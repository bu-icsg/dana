// See LICENSE.BU for license details.
// See LICENSE.IBM for license details.

package dana

import chisel3._
import chisel3.util._
import config._

// [TODO]
//   * I don't think that the location bit is used at all. Remove this
//     if it isn't

class CacheState(implicit p: Parameters) extends DanaBundle()(p) {
  val valid       = Bool()
  val notifyFlag  = Bool()
  val fetch       = Bool()
  val notifyIndex = UInt(log2Up(transactionTableNumEntries).W)
  val notifyMask  = UInt(transactionTableNumEntries.W)
  val asid        = UInt(asidWidth.W)
  val nnid        = UInt(nnidWidth.W)
  val inUseCount  = UInt((log2Up(transactionTableNumEntries) + 1).W)
}

class CacheMemReq(implicit p: Parameters) extends DanaBundle()(p) {
  val asid        = UInt(asidWidth.W)
  val nnid        = UInt(nnidWidth.W)
  val cacheIndex  = UInt(log2Up(cacheNumEntries).W)
}

class CacheMemResp(implicit p: Parameters) extends DanaBundle()(p) {
  val done        = Bool()
  val data        = UInt((elementsPerBlock * elementWidth).W)
  val cacheIndex  = UInt(log2Up(cacheNumEntries).W)
  val addr        = UInt(log2Up(cacheNumBlocks).W)
}

class CacheMemInterface(implicit p: Parameters) extends DanaBundle()(p) {
  val req  = Decoupled(new CacheMemReq)
  val resp = Valid(new CacheMemResp).flip
}

class CacheInterface(implicit p: Parameters) extends DanaStatusIO()(p) {
  val mem          = new CacheMemInterface
  lazy val control = (new ControlCacheInterface).flip
  lazy val pe      = (new PECacheInterface).flip
}

class CacheInterfaceLearn(implicit p: Parameters)
    extends CacheInterface()(p) {
  override lazy val control = (new ControlCacheInterfaceLearn).flip
  override lazy val pe      = (new PECacheInterfaceLearn).flip
}

class CompressedNeuron(implicit p: Parameters) extends DanaBundle()(p) {
  val weightPtr          = UInt(16.W)
  val numWeights         = UInt(8.W)
  val activationFunction = UInt(activationFunctionWidth.W)
  val steepness          = UInt(steepnessWidth.W)
  val bias               = SInt(elementWidth.W)
  def populate(data: UInt, out: CompressedNeuron) {
    out.weightPtr := data(15, 0)
    out.numWeights := data(23, 16)
    out.activationFunction := data(28, 24)
    out.steepness := data(31, 29)
    out.bias := data(63, 32).asSInt
  }
}

abstract class CacheBase[SramIfType <: SRAMVariantInterface,
  ControlCacheIfReqType <: ControlCacheInterfaceReq,
  ControlCacheIfRespType <: ControlCacheInterfaceResp,
  PECacheIfRespType <: PECacheInterfaceResp](
  genSram: => Vec[SramIfType], genControlReq: => ControlCacheIfReqType,
    genControlResp: => ControlCacheIfRespType,
    genPEResp: => PECacheIfRespType)(implicit p: Parameters)
    extends DanaModule()(p) {
  val mem = genSram

  override val printfSigil = "dana.Cache"

  lazy val io = IO(new CacheInterface)

  // Create the table of cache entries
  val table = Reg(Vec(cacheNumEntries, new CacheState))

  when (io.control.req.valid || io.control.resp.valid) {
    info(table, "cache,") }

  // The Transaction Table Queue needs to be big enough to hold one
  // inbound request from every entry in the Transaction Table
  val tTableReqQueue = Module(new Queue(genControlReq,
    transactionTableNumEntries)).io
  tTableReqQueue.enq.valid := io.control.req.valid
  tTableReqQueue.enq.bits := io.control.req.bits
  io.control.req.ready := tTableReqQueue.enq.ready

  // Response Pipelines for Control module and PEs. Responses take multiple
  // cycles to generate due to the fact that data needs to be read out
  // of the individual cache SRAMs so we construct a pipeline that
  // builds up the responses.
  val controlRespPipe = Reg(Vec(2, Valid(genControlResp)))
  val peRespPipe = Reg(Vec(2, Valid(genPEResp)))
  val cacheRead = Reg(Vec(cacheNumEntries, UInt(log2Up(cacheNumBlocks).W)))
  // We also need to store the cache index of an inbound request by a
  // PE so that we can dereference it one cycle later when the cache
  // line SRAM output is valid. [TODO] Should this be gated by the PE
  // request being valid?
  val peCacheIndex_d0 = Reg(UInt(), next = io.pe.req.bits.cacheIndex)

  // Helper functions for examing the cache entries
  def fIsFree(x: CacheState): Bool = {!x.valid}
  def fIsUnused(x: CacheState): Bool = {x.inUseCount === 0.U}
  def fDerefNnid(x: CacheState, y: UInt): Bool = {x.valid && x.nnid === y}

  // The check on whether or not an entry is done fetching depends, in
  // the absolute worst case, on the number of cycles it takes for
  // data to be written back to the call-by-name parameter
  // genSram, i.e., the Cache memory. The worst case here means that
  // the last entry to get written to the cache is the first block.
  // Thus, to be totally safe here, the check needs to be defined
  // based on the genSram used.
  def fIsDoneFetching(x: CacheState): Bool

  // State that we need to derive from the cache
  val hasUnused = table.exists(fIsUnused(_))
  val nextFree = table.indexWhere(fIsFree(_))
  val hasFree = table.exists(fIsFree(_)) && nextFree < io.status.caches_active
  val nextUnused = table.indexWhere(fIsUnused(_))
  val foundNnid = table.exists(fDerefNnid(_: CacheState,
    tTableReqQueue.deq.bits.nnid))
  val derefNnid = table.indexWhere(fDerefNnid(_: CacheState,
    tTableReqQueue.deq.bits.nnid))
  val hasNotify = table.exists(fIsDoneFetching(_))
  val idxNotify = table.indexWhere(fIsDoneFetching(_))

  // This initializes a new cache entry
  def tableInit(index: UInt) {
    table(index).valid := true.B
    table(index).asid := tTableReqQueue.deq.bits.asid
    table(index).nnid := tTableReqQueue.deq.bits.nnid
    table(index).fetch := true.B
    table(index).notifyFlag := false.B
    table(index).notifyIndex := tTableReqQueue.deq.bits.tableIndex
    table(index).notifyMask := UIntToOH(tTableReqQueue.deq.bits.tableIndex)
    table(index).inUseCount := 1.U
  }

  // Default values
  io.mem.req.valid := false.B
  io.mem.req.bits.asid := 0.U
  io.mem.req.bits.nnid := 0.U
  io.mem.req.bits.cacheIndex := 0.U

  controlRespPipe(0).valid := false.B
  controlRespPipe(0).bits.fetch := false.B
  controlRespPipe(0).bits.tableIndex := 0.U
  controlRespPipe(0).bits.tableMask := 0.U
  controlRespPipe(0).bits.cacheIndex := 0.U
  controlRespPipe(0).bits.data := Vec.fill(6)(0.U)
  controlRespPipe(0).bits.decimalPoint := 0.U
  controlRespPipe(0).bits.field := 0.U
  controlRespPipe(0).bits.regFileLocationBit := 0.U

  peRespPipe(0).valid := false.B
  peRespPipe(0).bits.data := 0.U

  // [TODO] This shouldn't always be true
  io.pe.req.ready := true.B

  // Assignment to the output pipe
  controlRespPipe(1) := controlRespPipe(0)
  peRespPipe(1) := peRespPipe(0)

  // Default values for the memory input wires
  for (i <- 0 until cacheNumEntries) {
    mem(i).din(0) := 0.U
    mem(i).addr(0) := 0.U
    mem(i).we(0) := false.B
  }

  // The cache can see requests from three locations:
  //   * Control (reads for layer information)
  //   * Memory (writes to load a configuration)
  //   * PE Table (reads for neuron information or weights)
  // Control reads and memory writes should never occur at the same
  // time. Consequently, these types of accesses share an SRAM port.
  // PE Table requests are frequent and will coflict with
  // control/memory requests for any non-trivial configuration and
  // network, so these get their own port.

  tTableReqQueue.deq.ready := false.B
  // Handle requests from the control module
  val request = tTableReqQueue.deq.bits.request
  val asid = tTableReqQueue.deq.bits.asid
  val nnid = tTableReqQueue.deq.bits.nnid
  val tableIndex = tTableReqQueue.deq.bits.tableIndex
  val layer = tTableReqQueue.deq.bits.currentLayer
  val location = tTableReqQueue.deq.bits.regFileLocationBit
  // Blind assignments
  controlRespPipe(0).bits.tableIndex := tableIndex
  controlRespPipe(0).bits.regFileLocationBit := location
  controlRespPipe(0).bits.data(0) := layer(log2Up(elementsPerBlock) - 1, 0)
  when (hasNotify) {
    controlRespPipe(0).bits.tableIndex := table(idxNotify).notifyIndex
    controlRespPipe(0).bits.tableMask := table(idxNotify).notifyMask
    controlRespPipe(0).bits.cacheIndex := idxNotify
  } .otherwise {
    controlRespPipe(0).bits.tableIndex := tableIndex
    controlRespPipe(0).bits.tableMask := UIntToOH(tableIndex)
    controlRespPipe(0).bits.cacheIndex := derefNnid
  }
  when (tTableReqQueue.deq.valid && !io.pe.req.valid) {
    tTableReqQueue.deq.ready := true.B
    switch (request) {
      is (e_CACHE_LOAD) {
        when (!foundNnid) {
          // The NNID was not found, so we need to initialize the
          // cache line and ask memory (ANTW or similar) to start
          // loading this specific configuration. However, this can
          // only happen if there is a free entry or an unused entry
          // in the cache. However, if the cache is always sized
          // larger than the Transcation Table, the case where there
          // isn't a free cache entry should never occur.
          val cacheIdx = Mux(hasFree, nextFree, nextUnused)
          when (hasFree | hasUnused) {
            tableInit(cacheIdx)
            // Generate a request to memory
            io.mem.req.valid := true.B
            io.mem.req.bits.asid := asid
            io.mem.req.bits.nnid := nnid
            io.mem.req.bits.cacheIndex := cacheIdx
          } .otherwise {
          }

        } .elsewhen (table(derefNnid).fetch) {
          // The nnid was found, but the data is currently being
          // loaded from memory. This happens if a second request for
          // the same data shows up while this guy is being fetched.
          table(derefNnid).inUseCount := table(derefNnid).inUseCount + 1.U
          table(derefNnid).notifyMask := table(derefNnid).notifyMask |
            UIntToOH(tableIndex)
        } .otherwise {
          // The NNID was found and the data has already been loaded
          table(derefNnid).inUseCount := table(derefNnid).inUseCount + 1.U
          // Start a response to the control unit
          controlRespPipe(0).valid := true.B
          controlRespPipe(0).bits.field := e_CACHE_INFO
        }
      }
      is (e_CACHE_LAYER_INFO) {
        controlRespPipe(0).valid := true.B
        controlRespPipe(0).bits.field := e_CACHE_LAYER
        // The layer sub-index is temporarily stored in data(0)

        // Read the layer information from the correct block. A layer
        // occupies one block, so we need to pull the block address
        // out of the layer number.
        mem(derefNnid).addr(0) := 1.U + // Offset from info region
          layer(layer.getWidth-1, log2Up(elementsPerBlock))
      }
      is (e_CACHE_DECREMENT_IN_USE_COUNT) {
          table(derefNnid).inUseCount := table(derefNnid).inUseCount - 1.U
      }
    }
  } .elsewhen (hasNotify) {
    // Start a response to the control unit
    controlRespPipe(0).valid := true.B
    controlRespPipe(0).bits.field := e_CACHE_INFO
    // Now that this is away, we can deassert some table bits
    table(idxNotify).fetch := false.B
    table(idxNotify).notifyFlag := false.B
  }

  // Pipeline second stage (SRAM read)
  switch (controlRespPipe(0).bits.field) {
    is (e_CACHE_INFO) {
      val thisCache = mem(controlRespPipe(0).bits.cacheIndex).dout(0)
      val dataDecode = (new NnConfigHeader).fromBits(thisCache)

      controlRespPipe(1).bits.decimalPoint := dataDecode.decimalPoint
      controlRespPipe(1).bits.data(0) := dataDecode.totalLayers
      controlRespPipe(1).bits.data(1) := dataDecode.totalNeurons
     // Pass back the error function in LSBs of data(2)
      controlRespPipe(1).bits.data(2) :=
        0.U((16-errorFunctionWidth).W) ## dataDecode.errorFunction
      controlRespPipe(1).bits.data(3) := dataDecode.learningRate
      controlRespPipe(1).bits.data(4) := dataDecode.lambda
      controlRespPipe(1).bits.data(5) := dataDecode.totalWeightBlocks
    }
    is (e_CACHE_LAYER_INFO) {
      val thisCache = mem(controlRespPipe(0).bits.cacheIndex).dout(0)
      // [TODO] fragile
      val dataDecode = Vec(bitsPerBlock/32, new NnConfigNeuron).fromBits(thisCache)
      val neuronIdx = controlRespPipe(0).bits.data(0)

      controlRespPipe(1).bits.data(0) := dataDecode(neuronIdx).neuronsInLayer
      controlRespPipe(1).bits.data(1) := dataDecode(neuronIdx).neuronsInPreviousLayer
      controlRespPipe(1).bits.data(2) := dataDecode(neuronIdx).neuronPointer
    }
  }

  // Handle requests from the Processing Element Table
  peRespPipe(0).bits.field := io.pe.req.bits.field
  peRespPipe(0).bits.peIndex := io.pe.req.bits.peIndex
  peRespPipe(0).bits.neuronIndex :=
    io.pe.req.bits.cacheAddr(2 + log2Up(elementsPerBlock) - 1, 3)
  when (io.pe.req.valid) {
    // Generate a request to the cache-specific SRAM. We need to
    // generate a block address from the input cache byte address.
    // [TODO] This shift may be a source of bugs. Check to make sure
    // that it's being passed correctly.
    mem(io.pe.req.bits.cacheIndex).addr(0) :=
      io.pe.req.bits.cacheAddr >> ((2 + log2Up(elementsPerBlock)).U)
    printfInfo("block address from byte address 0x%x/0x%x\n",
      io.pe.req.bits.cacheAddr,
      io.pe.req.bits.cacheAddr >> ((2 + log2Up(elementsPerBlock)).U) )
    // Fill the first stage of the PE pipeline
    switch (io.pe.req.bits.field) {
      is (e_CACHE_NEURON) {
        peRespPipe(0).valid := true.B
      }
      is (e_CACHE_WEIGHT) {
        printfInfo("PE 0x%x req for weight @ addr 0x%x\n",
          io.pe.req.bits.peIndex, io.pe.req.bits.cacheAddr)
        peRespPipe(0).valid := true.B
      }
    }
  }

  // The actual data that comes out of the memory is not interpreted
  // here, i.e., we just pass the full bitsPerBlock-sized data packet
  // to the PE Table and it deals with the internal indices.
  peRespPipe(1).bits.data := mem(peCacheIndex_d0).dout(0)

  // Set the response to the Control module
  io.control.resp.valid := controlRespPipe(1).valid
  io.control.resp.bits := controlRespPipe(1).bits
  // Set the response to the Processing Element Table
  io.pe.resp.valid := peRespPipe(1).valid
  io.pe.resp.bits := peRespPipe(1).bits

  // Handle responses from memory (ANTW or similar)
  when (io.mem.resp.valid) {
    printfInfo("saw write to SRAM_%x(%x) <= %x\n",
      io.mem.resp.bits.cacheIndex,
      io.mem.resp.bits.addr,
      io.mem.resp.bits.data)
    mem(io.mem.resp.bits.cacheIndex).we(0) := true.B
    mem(io.mem.resp.bits.cacheIndex).addr(0) := io.mem.resp.bits.addr
    mem(io.mem.resp.bits.cacheIndex).din(0) := io.mem.resp.bits.data
    // If this is done, then set the notify flag which will cause the
    // when block above to generate a response when the cache isn't
    // dealing with other requests
    when (io.mem.resp.bits.done) {
      printfInfo("SRAM_%x received DONE response\n",
        io.mem.resp.bits.cacheIndex)
      table(io.mem.resp.bits.cacheIndex).notifyFlag := true.B
    }
  }

  // Reset
  when (reset) {for (i <- 0 until cacheNumEntries) {
    table(i).valid := false.B }}

  // Assertions

  // The control module shouldn't be sending requests if the cache is
  // not ready.
  assert(!(!io.control.req.ready && io.control.req.valid),
    "Cache received valid control request when not ready")

  // The cache shouldn't be sending responses to the control module
  // when the control module isn't ready.
  assert(!(io.control.resp.valid && !io.control.resp.ready),
    "Cache trying to send response to Control when Control not ready")

  // We currently have no way of handling simultaneous requests.
  assert(!(io.control.req.valid && io.mem.req.valid),
    "Multiple simultaneous requests on the cache (dropped requests possible)")

  // The in use count should never be decremented below zero.
  assert(!(tTableReqQueue.deq.ready &&
    tTableReqQueue.deq.bits.request === e_CACHE_DECREMENT_IN_USE_COUNT &&
    table(derefNnid).inUseCount === 0.U),
    "Cache received control request to decrement count of zero-valued inUseCount")

  // There is no way of handling a request from the Transaction Table
  // that shows up when the cache has no free or unused entries.
  // However, this should never happen if the Transaction Table is
  // always smaller or equal to the number of cache entries.
  assert(!(tTableReqQueue.deq.ready &&
    tTableReqQueue.deq.bits.request === e_CACHE_LOAD &&
    !foundNnid &&
    (!hasFree && !hasUnused)),
    "Cache missed on ASID/NNID req, but has no free/unused entries")
}

class Cache(implicit p: Parameters)
    extends CacheBase(Vec.fill(p(CacheNumEntries))(
      Module(new SRAMVariant(
        dataWidth = p(BitsPerBlock),
        sramDepth = p(CacheNumBlocks),
        numPorts = 1)).io),
      new ControlCacheInterfaceReq, new ControlCacheInterfaceResp,
      new PECacheInterfaceResp)(p) {

  // SRAMVariant writes back in one cycle and no waiting is necessary.
  override def fIsDoneFetching(x: CacheState): Bool = {x.notifyFlag}
}

class CacheLearn(implicit p: Parameters)
    extends CacheBase(Vec.fill(p(CacheNumEntries))(
      Module(new SRAMBlockIncrement(
        dataWidth = p(BitsPerBlock),
        sramDepth = p(CacheNumBlocks),
        numPorts = 1,
        elementWidth = p(ElementWidth))).io),
      new ControlCacheInterfaceReqLearn, new ControlCacheInterfaceRespLearn,
      new PECacheInterfaceResp)(p) {
  override lazy val io = IO(new CacheInterfaceLearn)

  // SRAMBlockIncrement takes two cycles to writeback, so we're done
  // fetching one cycle after notifyFlag asserts.
  override def fIsDoneFetching(x: CacheState): Bool = {
    val notifyFlagOld = Reg(next = x.notifyFlag)
    x.notifyFlag & notifyFlagOld }

  for (i <- 0 until cacheNumEntries) {
    mem(i).inc(0) := false.B
  }

  controlRespPipe(0).bits.totalWritesMul := 0.U
  controlRespPipe(0).bits.totalWritesMul :=
    tTableReqQueue.deq.bits.totalWritesMul

  // [TODO] Why is this always assigned?
  val thisCache = mem(controlRespPipe(0).bits.cacheIndex).dout(0)
  val dataDecode = (new NnConfigHeader).fromBits(thisCache)
  controlRespPipe(1).bits.globalWtptr := dataDecode.weightsPointer

  when (io.pe.req.valid) {
    switch (io.pe.req.bits.field) {
      is (e_CACHE_WEIGHT_ONLY) {
        printfInfo("PE 0x%x req for weight @ addr 0x%x\n",
          io.pe.req.bits.peIndex, io.pe.req.bits.cacheAddr)
        peRespPipe(0).valid := true.B
      }
      is (e_CACHE_WEIGHT_WB) {
        printfInfo("PE 0x%x req to inc weight @addr 0x%x\n",
          io.pe.req.bits.peIndex, io.pe.req.bits.cacheAddr)
        printfInfo("       block: 0x%x\n", io.pe.req.bits.data)
        mem(io.pe.req.bits.cacheIndex).we(0) := true.B
        mem(io.pe.req.bits.cacheIndex).inc(0) := true.B
        mem(io.pe.req.bits.cacheIndex).din(0) := io.pe.req.bits.data.asUInt
      }
    }
  }
}
