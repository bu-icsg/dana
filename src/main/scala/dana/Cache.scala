// See LICENSE.BU for license details.
// See LICENSE.IBM for license details.

package dana

import chisel3._
import chisel3.util._
import cde._

import dana.abi._

// [TODO]
//   * I don't think that the location bit is used at all. Remove this
//     if it isn't

case object CacheInit extends Field[Seq[CacheInitParameters]]

object CacheTypes {
  object Mem {
    val read = 0x0
    val write = 0x1
  }
}

class CacheState(implicit p: Parameters) extends DanaBundle()(p) {
  val valid       = Bool()
  val dirty       = Bool()
  val wbPending   = Bool()
  val notifyFlag  = Bool()
  val fetch       = Bool()
  val notifyIndex = UInt(log2Up(transactionTableNumEntries).W)
  val notifyMask  = UInt(transactionTableNumEntries.W)
  val asid        = UInt(asidWidth.W)
  val nnid        = UInt(nnidWidth.W)
  val inUseCount  = UInt((log2Up(transactionTableNumEntries) + 1).W)
}

class CacheAntwReq(implicit p: Parameters) extends DanaBundle()(p) {
  val asid        = UInt(asidWidth.W)
  val nnid        = UInt(nnidWidth.W)
  val cacheIndex  = UInt(log2Up(cacheNumEntries).W)
  val action      = UInt(1.W)
}

class CacheAntwResp(implicit p: Parameters) extends DanaBundle()(p) {
  val done        = Bool()
  val data        = UInt(bitsPerBlock.W)
  val cacheIndex  = UInt(log2Up(cacheNumEntries).W)
  val addr        = UInt(log2Up(cacheNumBlocks).W)
}

class CacheAntwStoreReq(implicit p: Parameters) extends DanaBundle()(p) {
  val addr  = UInt(log2Up(cacheNumBlocks).W)
  val index = UInt(log2Up(cacheNumBlocks).W)
  val done  = Bool()
}

class CacheAntwStore(implicit p: Parameters) extends DanaBundle()(p) {
  val req = Flipped(Decoupled(new CacheAntwStoreReq))
  val resp = Output(Valid(UInt(bitsPerBlock.W)))
}

class CacheAntwInterface(implicit p: Parameters) extends DanaBundle()(p) {
  val cmd       = Decoupled(new CacheAntwReq)
  val load      = Flipped(Valid(new CacheAntwResp))
  val store     = new CacheAntwStore
}

class CacheInterface(implicit p: Parameters) extends DanaStatusIO()(p) {
  val antw         = new CacheAntwInterface
  lazy val control = Flipped(new ControlCacheInterface)
  lazy val pe      = Flipped(new PECacheInterface)
}

class CacheInterfaceLearn(implicit p: Parameters)
    extends CacheInterface()(p) {
  override lazy val control = Flipped(new ControlCacheInterfaceLearn)
  override lazy val pe      = Flipped(new PECacheInterfaceLearn)
}

abstract class CacheBase[
  A <: SRAMVariant, B <: SRAMVariantInterface, C <: ControlCacheInterfaceReq,
  D <: ControlCacheInterfaceResp](implicit p: Parameters)
    extends DanaModule()(p) {
  def mem: Seq[A]
  def memIo: Vec[B]
  def genControlReq: C
  def genControlResp: D
  mem zip memIo map {case(m, io) => m.io <> io}

  override val printfSigil = "dana.Cache: "

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
  val peRespPipe = Reg(Vec(2, Valid(new PECacheInterfaceResp)))
  val cacheRead = Reg(Vec(cacheNumEntries, UInt(log2Up(cacheNumBlocks).W)))
  // We also need to store the cache index of an inbound request by a
  // PE so that we can dereference it one cycle later when the cache
  // line SRAM output is valid. [TODO] Should this be gated by the PE
  // request being valid?
  val peCacheIndex_d0 = Reg(UInt(), next = io.pe.req.bits.cacheIndex)

  // Helper functions for examing the cache entries
  def fIsFree(x: CacheState): Bool = { !x.valid }
  def fIsUnused(x: CacheState): Bool = { (x.inUseCount ## x.notifyMask) === 0.U }
  def fDerefNnid(x: CacheState, y: UInt, z: UInt): Bool = {
    x.valid && x.nnid === y && x.asid === z }

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
    tTableReqQueue.deq.bits.nnid, tTableReqQueue.deq.bits.asid))
  val derefNnid = table.indexWhere(fDerefNnid(_: CacheState,
    tTableReqQueue.deq.bits.nnid, tTableReqQueue.deq.bits.asid))
  val hasNotify = table.exists(fIsDoneFetching(_))
  val idxNotify = table.indexWhere(fIsDoneFetching(_))

  // This initializes a new cache entry
  def tableInit(index: UInt) {
    table(index).valid := true.B
    table(index).dirty := false.B
    table(index).wbPending := false.B
    table(index).asid := tTableReqQueue.deq.bits.asid
    table(index).nnid := tTableReqQueue.deq.bits.nnid
    table(index).fetch := true.B
    table(index).notifyFlag := false.B
    table(index).notifyIndex := tTableReqQueue.deq.bits.tableIndex
    table(index).notifyMask := UIntToOH(tTableReqQueue.deq.bits.tableIndex)
  }

  // Default values
  controlRespPipe(0).valid := false.B
  controlRespPipe(0).bits.elements map { case (_, x) => x := 0.U }

  peRespPipe(0).valid := false.B
  peRespPipe(0).bits.data := 0.U

  // [TODO] This shouldn't always be true
  io.pe.req.ready := true.B

  // Assignment to the output pipe
  controlRespPipe(1) := controlRespPipe(0)
  peRespPipe(1) := peRespPipe(0)

  // Default values for the memory input wires
  for (i <- 0 until cacheNumEntries) {
    memIo(i).din(0) := 0.U
    memIo(i).addr(0) := 0.U
    memIo(i).we(0) := false.B
    memIo(i).re(0) := false.B
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
  val tableIndex = tTableReqQueue.deq.bits.tableIndex
  val layer = tTableReqQueue.deq.bits.currentLayer
  val location = tTableReqQueue.deq.bits.regFileLocationBit
  // Blind assignments
  controlRespPipe(0).bits.tableIndex := tableIndex
  controlRespPipe(0).bits.regFileLocationBit := location
  val layer_d = RegNext(layer(log2Up(elementsPerBlock) - 1, 0))
  when (hasNotify) {
    controlRespPipe(0).bits.tableIndex := table(idxNotify).notifyIndex
    controlRespPipe(0).bits.tableMask := table(idxNotify).notifyMask
    controlRespPipe(0).bits.cacheIndex := idxNotify
  } .otherwise {
    controlRespPipe(0).bits.tableIndex := tableIndex
    controlRespPipe(0).bits.tableMask := UIntToOH(tableIndex)
    controlRespPipe(0).bits.cacheIndex := derefNnid
  }

  io.antw.cmd.valid := false.B
  io.antw.cmd.bits.asid := tTableReqQueue.deq.bits.asid
  io.antw.cmd.bits.nnid := tTableReqQueue.deq.bits.nnid
  io.antw.cmd.bits.action := CacheTypes.Mem.read.U
  val cacheIdx = Mux(hasFree, nextFree, nextUnused)
  io.antw.cmd.bits.cacheIndex := cacheIdx
  when (tTableReqQueue.deq.valid && !io.pe.req.valid) {
    tTableReqQueue.deq.ready := true.B
    // The entry isn't ready if it's being fetched or if a writeback
    // is in progress and the transaction wants non-dirty data (e.g.,
    // the default case for learning transactions)
    val notReady = table(derefNnid).fetch || (
      table(derefNnid).wbPending && tTableReqQueue.deq.bits.notDirty)
    switch (request) {
      is (e_CACHE_LOAD) {
        when (!foundNnid) {
          // Free/unused entry found. Fetch via ANTW.
          when (hasFree | hasUnused) {
            tableInit(cacheIdx)
            io.antw.cmd.valid := true.B
          }
          // [TODO] Handle the situation where there isn't a free
          // entry
        } .elsewhen (notReady) {
          // ASID/NNID exists, but isn't ready. Update notify mask.
          table(derefNnid).notifyMask := (table(derefNnid).notifyMask |
            UIntToOH(tableIndex) )
        } .otherwise {
          // ASID/NNID found and ready
          table(derefNnid).inUseCount := table(derefNnid).inUseCount + 1.U
          controlRespPipe(0).valid := true.B
          memIo(idxNotify).re(0) := true.B
          controlRespPipe(0).bits.field := e_CACHE_INFO
        }
      }
      is (e_CACHE_LAYER_INFO) {
        controlRespPipe(0).valid := true.B
        controlRespPipe(0).bits.field := e_CACHE_LAYER

        // Read the layer information from the correct block. A layer
        // occupies one block, so we need to pull the block address
        // out of the layer number.
        memIo(derefNnid).addr(0) := (1.U + // Offset from info region
          layer(layer.getWidth-1, log2Up(elementsPerBlock)) )
        memIo(derefNnid).re(0) := true.B
      }
      is (e_CACHE_DECREMENT_IN_USE_COUNT) {
        table(derefNnid).inUseCount := table(derefNnid).inUseCount - 1.U
      }
    }
  } .elsewhen (hasNotify) {
    // Start a response to the control unit
    controlRespPipe(0).valid := true.B
    controlRespPipe(0).bits.field := e_CACHE_INFO
    memIo(idxNotify).re(0) := true.B
    // Now that this is away, we can deassert some table bits, and
    // properly set the inUse count
    table(idxNotify).fetch := false.B
    table(idxNotify).notifyFlag := false.B
    table(idxNotify).inUseCount := PopCount(table(idxNotify).notifyMask)
    table(idxNotify).notifyMask := 0.U
    printfInfo("Entry 0x%x gets inUseCount of 0x%x\n", idxNotify,
      PopCount(table(idxNotify).notifyMask))
  }

  // Pipeline second stage (SRAM read)
  val thisCache = memIo(controlRespPipe(0).bits.cacheIndex).dout(0)
  when (controlRespPipe(0).bits.field === e_CACHE_INFO) {
    controlRespPipe(1).bits.data := thisCache }
  when (controlRespPipe(0).bits.field === e_CACHE_LAYER_INFO) {
    val size = (new NnConfigLayer).getWidth
    val dataDecode = Vec(bitsPerBlock/size, UInt(size.W)).fromBits(thisCache)
    controlRespPipe(1).bits.data := 0.U ## dataDecode(layer_d) }

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
    memIo(io.pe.req.bits.cacheIndex).addr(0) :=
      io.pe.req.bits.cacheAddr >> ((2 + log2Up(elementsPerBlock)).U)
    memIo(io.pe.req.bits.cacheIndex).re(0) := true.B
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
  peRespPipe(1).bits.data := memIo(peCacheIndex_d0).dout(0)

  // Set the response to the Control module
  io.control.resp.valid := controlRespPipe(1).valid
  io.control.resp.bits := controlRespPipe(1).bits
  // Set the response to the Processing Element Table
  io.pe.resp.valid := peRespPipe(1).valid
  io.pe.resp.bits := peRespPipe(1).bits

  // Handle responses from memory (ANTW or similar)
  when (io.antw.load.valid) {
    printfInfo("saw write to SRAM_%x(%x) <= %x\n",
      io.antw.load.bits.cacheIndex,
      io.antw.load.bits.addr,
      io.antw.load.bits.data)
    memIo(io.antw.load.bits.cacheIndex).we(0) := true.B
    memIo(io.antw.load.bits.cacheIndex).addr(0) := io.antw.load.bits.addr
    memIo(io.antw.load.bits.cacheIndex).din(0) := io.antw.load.bits.data
    // If this is done, then set the notify flag which will cause the
    // when block above to generate a response when the cache isn't
    // dealing with other requests
    when (io.antw.load.bits.done) {
      printfInfo("SRAM_%x received DONE response\n",
        io.antw.load.bits.cacheIndex)
      table(io.antw.load.bits.cacheIndex).notifyFlag := true.B
    }
  }

  // Reset
  when (reset) {
    for (i <- 0 until cacheNumEntries) { table(i).valid := false.B }
    // Optional Cache initialization
    if (!p(CacheInit).isEmpty) {
      p(CacheInit).zipWithIndex.map{ case (c, i) => {
        when (reset) {
          table(i).valid       := true.B
          table(i).asid        := c.asid.U
          table(i).nnid        := c.nnid.U
          table(i).wbPending   := false.B
          table(i).notifyFlag  := false.B
          table(i).fetch       := false.B
          table(i).notifyIndex := 0.U
          table(i).notifyMask  := 0.U
          table(i).inUseCount  := 0.U } }}}
  }

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
  assert(!(io.control.req.valid && io.antw.cmd.valid),
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

class Cache(implicit p: Parameters) extends CacheBase[
  SRAMVariant, SRAMVariantInterface, ControlCacheInterfaceReq,
  ControlCacheInterfaceResp]()(p) {
  lazy val mem = Seq.fill(p(CacheNumEntries))(
    Module(new SRAMVariant(
      dataWidth = p(BitsPerBlock),
      sramDepth = p(CacheNumBlocks),
      numPorts = 1)))
  lazy val memIo = Wire(Vec(cacheNumEntries, mem(0).io.cloneType))

  def genControlReq = new ControlCacheInterfaceReq
  def genControlResp = new ControlCacheInterfaceResp

  // SRAMVariant writes back in one cycle and no waiting is necessary.
  override def fIsDoneFetching(x: CacheState): Bool = {x.notifyFlag}
}

class CacheLearn(implicit p: Parameters) extends CacheBase[SRAMBlockIncrement,
  SRAMBlockIncrementInterface, ControlCacheInterfaceReqLearn,
  ControlCacheInterfaceRespLearn]()(p) {
  override lazy val io = IO(new CacheInterfaceLearn)

  lazy val mem = Seq.tabulate(p(CacheNumEntries))(id =>
    Module(new SRAMBlockIncrement(
      id,
      dataWidth = p(BitsPerBlock),
      sramDepth = p(CacheNumBlocks),
      numPorts = 1,
      elementWidth = p(DanaDataBits) )))
  lazy val memIo = Wire(Vec(p(CacheNumEntries), mem(0).io.cloneType))

  def genControlReq = new ControlCacheInterfaceReqLearn
  def genControlResp = new ControlCacheInterfaceRespLearn

  // SRAMBlockIncrement takes two cycles to writeback, so we're done
  // fetching one cycle after notifyFlag asserts.
  override def fIsDoneFetching(x: CacheState): Bool = {
    val notifyFlagOld = Reg(next = x.notifyFlag)
    x.notifyFlag & notifyFlagOld }

  for (i <- 0 until cacheNumEntries) {
    memIo(i).inc(0) := false.B
  }

  controlRespPipe(0).bits.totalWritesMul := 0.U
  controlRespPipe(0).bits.totalWritesMul := tTableReqQueue.deq.bits.totalWritesMul

  val dataDecode = (new NnConfigHeader).fromBits(thisCache)

  when (io.pe.req.valid) {
    val cacheIdx = io.pe.req.bits.cacheIndex
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
        memIo(cacheIdx).we(0) := true.B
        memIo(cacheIdx).inc(0) := true.B
        memIo(cacheIdx).din(0) := io.pe.req.bits.data.asUInt
        table(cacheIdx).dirty := true.B
      }
    }
  }

  // Status/Probe handling (fence/sync)
  val doFence = io.status.fence.valid && io.status.fence.fence_type
  val doSync = io.status.fence.valid && !io.status.fence.fence_type
  io.probes.cache.fence_done := false.B
  // [TODO] Handle the situation where the configuration is still in
  // use
  when ((doFence || doSync)) {
    val foundNnid = table.exists(fDerefNnid(_: CacheState,
      io.status.fence.nnid, io.status.asid))
    val nnidIdx = table.indexWhere(fDerefNnid(_: CacheState,
      io.status.fence.nnid, io.status.asid))

    val pending = table(nnidIdx).wbPending
    val noWriteback = !pending && (
      !foundNnid || (foundNnid && !table(nnidIdx).dirty))
    val writeback = !pending && foundNnid && table(nnidIdx).dirty
    when (noWriteback) {
      io.probes.cache.fence_done := true.B
      io.probes.cache.fence_asid := io.status.asid
      printfInfo("No writeback for fence/sync (%x/%x) for asid/nnid (0x%x/0x%x)\n",
        doFence, doSync, io.status.fence.nnid, io.status.asid)
    }

    when (writeback) {
      io.antw.cmd.valid := true.B
      io.antw.cmd.bits.cacheIndex := nnidIdx
      io.antw.cmd.bits.asid := io.status.asid
      io.antw.cmd.bits.nnid := io.status.fence.nnid
      io.antw.cmd.bits.action := CacheTypes.Mem.write.U
      printfInfo("Writeback needed for fence/sync (%x/%x) for asid/nnid (0x%x/0x%x)\n",
        doFence, doSync, io.status.fence.nnid, io.status.asid)

      table(nnidIdx).wbPending := true.B
    }
  }

  io.antw.store.req.ready := true.B
  io.antw.store.resp.bits := memIo(RegNext(io.antw.store.req.bits.index)).dout(0)
  when (io.antw.store.req.fire()) {
    val (req, resp) = (io.antw.store.req, io.antw.store.resp)
    val (m, t)      = (memIo(req.bits.index), table(req.bits.index))
    val p           = io.probes.cache

    when (req.bits.done) {
      t.dirty := false.B
      t.wbPending := false.B
      p.fence_done := true.B
      p.fence_asid := t.asid
      printfInfo("Writeback done for asid/nnid (0x%x/0x%x)\n", t.asid, t.nnid)
    } .otherwise {
      m.re(0) := true.B
      m.we(0) := false.B
      m.addr(0) := req.bits.addr
      resp.valid := RegNext(req.fire())
    }
  }
}
