package dana

import Chisel._

// [TODO]
//   * The notifyMask/tableMask isn't being set in the response, but
//     this needs to be used
//   * I don't think that the location bit is used at all. Remove this
//     if it isn't

class CacheState extends DanaBundle {
  // nnsim-hdl equivalent:
  //   cache_types::cache_config_entry_struct
  val valid = Bool()
  val notifyFlag = Bool()
  val fetch = Bool()
  val notifyIndex = UInt(width = log2Up(transactionTableNumEntries))
  val notifyMask = UInt(width = transactionTableNumEntries)
  val asid = UInt(width = asidWidth)
  val nnid = UInt(width = nnidWidth)
  val inUseCount = UInt(width = log2Up(transactionTableNumEntries) + 1)
}

class CacheMemReq extends DanaBundle {
  val asid = UInt(width = asidWidth)
  val nnid = UInt(width = nnidWidth)
  val cacheIndex = UInt(width = log2Up(cacheNumEntries))
  val coreIndex = UInt(width = log2Up(numCores))
}

class CacheMemResp extends DanaBundle {
  val done = Bool()
  val data = UInt(width = elementsPerBlock * elementWidth)
  val cacheIndex = UInt(width = log2Up(cacheNumEntries))
  val addr = UInt(width = log2Up(cacheNumBlocks))
}

class CacheMemInterface extends DanaBundle {
  // Outbound request. nnsim-hdl equivalent:
  //   cache_types::cache2mem_struct
  val req = Decoupled(new CacheMemReq)
  // Response from memory. nnsim-hdl equivalent:
  //   cache_types::mem2cache_struct
  val resp = Decoupled(new CacheMemResp).flip
}

class CacheInterface extends Bundle {
  // The cache is connected to memory (technically via the arbiter
  // when this gets added), the control unit, and to the processing
  // elements
  val mem = new CacheMemInterface
  val control = (new ControlCacheInterface).flip
  val pe = (new PECacheInterface).flip
}


class CompressedNeuron extends DanaBundle {
  val weightPtr = UInt(width = 16)
  val numWeights = UInt(width = 8)
  val activationFunction = UInt(width = activationFunctionWidth)
  val steepness = UInt(width = steepnessWidth)
  val bias = UInt(width = elementWidth)
  def populate(data: UInt, out: CompressedNeuron) {
    out.weightPtr := data(15, 0)
    out.numWeights := data(23, 16)
    out.activationFunction := data(28, 24)
    out.steepness := data(31, 29)
    out.bias := data(63, 32)
  }
}

class Cache extends DanaModule {
  val io = new CacheInterface

  // Create the table of cache entries
  val table = Vec.fill(cacheNumEntries){Reg(new CacheState)}
  val mem = Vec((0 until cacheNumEntries).map(i => Module(new SRAM(
    dataWidth = elementWidth * elementsPerBlock,
    numReadPorts = 0,
    numWritePorts = 0,
    numReadWritePorts = 2,
    sramDepth = cacheNumBlocks, // [TODO] I think this is the correct parameter
    initSwitch = if (preloadCache) i else -1,
    elementsPerBlock = elementsPerBlock
    )).io))

  // Response Pipelines for Control module and PEs. Responses take multiple
  // cycles to generate due to the fact that data needs to be read out
  // of the individual cache SRAMs so we construct a pipeline that
  // builds up the responses.
  val controlRespPipe =
    Vec.fill(2){Reg(Valid(new ControlCacheInterfaceResp))}
  val peRespPipe =
    Vec.fill(2){Reg(Valid(new PECacheInterfaceResp))}
  val cacheRead = Vec.fill(cacheNumEntries){
    (Reg(UInt(width=log2Up(cacheNumBlocks)))) }
  // We also need to store the cache index of an inbound request by a
  // PE so that we can dereference it one cycle later when the cache
  // line SRAM output is valid. [TODO] Should this be gated by the PE
  // request being valid?
  val peCacheIndex_d0 = Reg(UInt(), next = io.pe.req.bits.cacheIndex)

  // Helper functions for examing the cache entries
  def isFree(x: CacheState): Bool = {!x.valid}
  def isUnused(x: CacheState): Bool = {x.inUseCount === UInt(0)}
  def derefNnid(x: CacheState, y: UInt): Bool = {x.valid && x.nnid === y}
  def isDoneFetching(x: CacheState): Bool = {x.notifyFlag}

  // State that we need to derive from the cache
  val hasFree = Bool()
  val hasUnused = Bool()
  val nextFree = UInt()
  val nextUnused = UInt()
  val foundNnid = Bool()
  val derefNnid = UInt()
  val hasNotify = Bool()
  val idxNotify = UInt()
  hasFree := table.exists(isFree)
  hasUnused := table.exists(isUnused)
  nextFree := table.indexWhere(isFree)
  nextUnused := table.indexWhere(isUnused)
  foundNnid := table.exists(derefNnid(_, io.control.req.bits.nnid))
  derefNnid := table.indexWhere(derefNnid(_, io.control.req.bits.nnid))
  hasNotify := table.exists(isDoneFetching)
  idxNotify := table.indexWhere(isDoneFetching)

  // Helper functions for setting the table
  def tableInit(index: UInt) {
    // This initializes a new cache entry
    table(index).valid := Bool(true)
    table(index).asid := io.control.req.bits.asid
    table(index).nnid := io.control.req.bits.nnid
    table(index).fetch := Bool(true)
    table(index).notifyFlag := Bool(false)
    table(index).notifyIndex := io.control.req.bits.tableIndex
    table(index).inUseCount := UInt(1)
  }

  // I think the cache is always ready. This should not be gated on
  // hasFree or hasUnused as this precludes cache hits on used entries
  // (a case which comes up when you have the Cache and Transaction
  // Table completely filled).
  io.control.req.ready := Bool(true)

  // Default values
  io.mem.req.valid := Bool(false)
  io.mem.req.bits.asid := UInt(0)
  io.mem.req.bits.nnid := UInt(0)
  io.mem.req.bits.cacheIndex := UInt(0)
  io.mem.req.bits.coreIndex := UInt(0)

  io.control.resp.valid := Bool(false)
  io.control.resp.bits.fetch := Bool(false)
  io.control.resp.bits.tableIndex := UInt(0)
  io.control.resp.bits.tableMask := UInt(0)
  io.control.resp.bits.cacheIndex := UInt(0)
  io.control.resp.bits.data := Vec.fill(3){UInt(0)}
  io.control.resp.bits.decimalPoint := UInt(0)
  io.control.resp.bits.field := UInt(0)

  controlRespPipe(0).valid := Bool(false)
  controlRespPipe(0).bits.fetch := Bool(false)
  controlRespPipe(0).bits.tableIndex := UInt(0)
  controlRespPipe(0).bits.tableMask := UInt(0)
  controlRespPipe(0).bits.cacheIndex := UInt(0)
  controlRespPipe(0).bits.data := Vec.fill(3){UInt(0)}
  controlRespPipe(0).bits.decimalPoint := UInt(0)
  controlRespPipe(0).bits.field := UInt(0)
  controlRespPipe(0).bits.location := UInt(0)
  controlRespPipe(0).bits.inLastLearn := Bool(false)

  peRespPipe(0).valid := Bool(false)
  peRespPipe(0).bits.field := UInt(0)
  peRespPipe(0).bits.data := UInt(0)
  peRespPipe(0).bits.peIndex := UInt(0)
  peRespPipe(0).bits.indexIntoData := UInt(0)

  // [TODO] This shouldn't always be true
  io.pe.req.ready := Bool(true)

  // Assignment to the output pipe
  controlRespPipe(1) := controlRespPipe(0)
  peRespPipe(1) := peRespPipe(0)

  // Default values for the memory input wires
  for (i <- 0 until cacheNumEntries) {
    for (j <- 0 until mem(i).numReadWritePorts) {
      mem(i).din(j) := UInt(0)
      mem(i).addr(j) := UInt(0)
      // mem(i).addr(j) := cacheRead(i)
      mem(i).we(j) := Bool(false)
      // cacheRead(j) := UInt(0)
    }
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

  // Handle requests from the control module
  when (io.control.req.valid) {
    switch (io.control.req.bits.request) {
      is (e_CACHE_LOAD) {
        when (!foundNnid) {
          // The NNID was not found, so we need to initialize the
          // cache line and ask memory (ANTW or similar) to start
          // loading this specific configuration. However, this can
          // only happen if there is a free entry or an unused entry
          // in the cache. However, if the cache is always sized
          // larger than the Transcation Table, the case where there
          // isn't a free cache entry should never occur.
          when (hasFree | hasUnused) {
            tableInit(Mux(hasFree, nextFree, nextUnused))
            // Generate a request to memory
            io.mem.req.valid := Bool(true)
            io.mem.req.bits.asid := io.control.req.bits.asid
            io.mem.req.bits.nnid := io.control.req.bits.nnid
            io.mem.req.bits.cacheIndex := nextFree
            io.mem.req.bits.coreIndex := io.control.req.bits.coreIdx
            printf("[INFO] Cache: req Core/ASID/NNID %d/0x%x/0x%x miss\n",
              io.control.req.bits.coreIdx, io.control.req.bits.asid,
              io.control.req.bits.nnid);
          } .otherwise {
            printf("[ERROR] Cache: req ASID/NNID 0x%x/0x%x, no space\n",
            io.control.req.bits.asid, io.control.req.bits.nnid)
          }

        } .elsewhen (table(derefNnid).fetch) {
          // The nnid was found, but the data is currently being
          // loaded from memory. This happens if a second request for
          // the same data shows up while this guy is being fetched.
          table(derefNnid).inUseCount := table(derefNnid).inUseCount + UInt(1)
          table(derefNnid).notifyMask := table(derefNnid).notifyMask |
            UIntToOH(io.control.req.bits.tableIndex)
          printf("[INFO] Cache: req Core/ASID/NNID %d/0x%x/0x%x miss (loading)\n",
            io.control.req.bits.coreIdx, io.control.req.bits.asid,
            io.control.req.bits.nnid);
        } .otherwise {
          // The NNID was found and the data has already been loaded
          table(derefNnid).inUseCount := table(derefNnid).inUseCount + UInt(1)

          // Start a response to the control unit
          controlRespPipe(0).valid := Bool(true)
          controlRespPipe(0).bits.tableIndex := io.control.req.bits.tableIndex
          controlRespPipe(0).bits.tableMask :=
            UIntToOH(io.control.req.bits.tableIndex)
          controlRespPipe(0).bits.cacheIndex := derefNnid
          controlRespPipe(0).bits.field := e_CACHE_INFO
          controlRespPipe(0).bits.location := io.control.req.bits.location

          printf("[INFO] Cache: req Core/ASID/NNID %d/0x%x/0x%x hit\n",
            io.control.req.bits.coreIdx, io.control.req.bits.asid,
            io.control.req.bits.nnid);
        }
      }
      is (e_CACHE_LAYER_INFO) {
        controlRespPipe(0).valid := Bool(true)
        controlRespPipe(0).bits.tableIndex := io.control.req.bits.tableIndex
        controlRespPipe(0).bits.field := e_CACHE_LAYER_INFO
        // The layer sub-index is temporarily stored in data(0)
        controlRespPipe(0).bits.data(0) :=
          io.control.req.bits.layer(log2Up(elementsPerBlock)-1,0)
        controlRespPipe(0).bits.location := io.control.req.bits.location
        controlRespPipe(0).bits.cacheIndex := derefNnid
        controlRespPipe(0).bits.inLastLearn := io.control.req.bits.inLastLearn

        // Read the layer information from the correct block. A layer
        // occupies one block, so we need to pull the block address
        // out of the layer number.
        mem(derefNnid).addr(0) := UInt(1) + // Offset from info region
          io.control.req.bits.layer(io.control.req.bits.layer.getWidth-1,
            log2Up(elementsPerBlock))
        // cacheRead(derefNnid) := UInt(1) + // Offset from info region
        //   io.control.req.bits.layer(io.control.req.bits.layer.getWidth-1,
        //     log2Up(elementsPerBlock))
      }
      is (e_CACHE_DECREMENT_IN_USE_COUNT) {
          table(derefNnid).inUseCount := table(derefNnid).inUseCount - UInt(1)
      }
    }
  } .elsewhen (hasNotify) {
    // Start a response to the control unit
    controlRespPipe(0).valid := Bool(true)
    controlRespPipe(0).bits.tableIndex := table(idxNotify).notifyIndex
    controlRespPipe(0).bits.tableMask := table(idxNotify).notifyMask
    controlRespPipe(0).bits.cacheIndex := idxNotify
    controlRespPipe(0).bits.field := e_CACHE_INFO
    // [TODO] The location bit should isn't used? Remove this?
    controlRespPipe(0).bits.location := UInt(0)
    // Now that this is away, we can deassert some table bits
    table(idxNotify).fetch := Bool(false)
    table(idxNotify).notifyFlag := Bool(false)
  }

  // Pipeline second stage (SRAM read)
  switch (controlRespPipe(0).bits.field) {
    is (e_CACHE_INFO) {
      val compressedInfo = new Bundle{
        val decimalPoint = mem(controlRespPipe(0).bits.cacheIndex).dout(0)(
          decimalPointWidth - 1, 0)
        val errorFunction = mem(controlRespPipe(0).bits.cacheIndex).dout(0)(
          decimalPointWidth + errorFunctionWidth - 1, decimalPointWidth)
        val unused_0 = mem(controlRespPipe(0).bits.cacheIndex).dout(0)(
          16 - 1, decimalPointWidth + errorFunctionWidth)
        val totalEdges = mem(controlRespPipe(0).bits.cacheIndex).dout(0)(
          32 - 1, 16)
        val totalNeurons = mem(controlRespPipe(0).bits.cacheIndex).dout(0)(
          48 - 1, 32)
        val totalLayers = mem(controlRespPipe(0).bits.cacheIndex).dout(0)(
          64 - 1, 48)
        val firstLayerPointer = mem(controlRespPipe(0).bits.cacheIndex).dout(0)(
          80 - 1, 64)
        val weightsPointer = mem(controlRespPipe(0).bits.cacheIndex).dout(0)(
          96 - 1, 80)
        val unused_1 = mem(controlRespPipe(0).bits.cacheIndex).dout(0)(
          bitsPerBlock - 1, 96)
      }
      // Decimal Point
      controlRespPipe(1).bits.decimalPoint := compressedInfo.decimalPoint
      // Layers
      controlRespPipe(1).bits.data(0) := compressedInfo.totalLayers
      // Neurons
      controlRespPipe(1).bits.data(1) := compressedInfo.totalNeurons
      // Pass back the error function in LSBs of data(2)
      controlRespPipe(1).bits.data(2) := UInt(0, width=16-errorFunctionWidth) ##
        compressedInfo.errorFunction
    }
    is (e_CACHE_LAYER_INFO) {
      val compressedLayers = Vec((0 until bitsPerBlock / 32).map(i =>
        mem(controlRespPipe(0).bits.cacheIndex).dout(0)(32 * (i + 1) - 1, 32 * i)))
      // Number of neurons in this layer
      controlRespPipe(1).bits.data(0) :=
        compressedLayers(controlRespPipe(0).bits.data(0))(12 + 10 - 1, 12)
      // data(1) is currently unused
      // Pointer to the first neuron
      controlRespPipe(1).bits.data(2) :=
        compressedLayers(controlRespPipe(0).bits.data(0))(12 - 1, 0)
    }
  }

  // Handle requests from the Processing Element Table
  when (io.pe.req.valid) {
    // Generate a request to the cache-specific SRAM. We need to
    // generate a block address from the input cache byte address.
    // [TODO] This shift may be a source of bugs. Check to make sure
    // that it's being passed correctly.
    mem(io.pe.req.bits.cacheIndex).addr(1) :=
      io.pe.req.bits.cacheAddr >> (UInt(2 + log2Up(elementsPerBlock)))
    // Fill the first stage of the PE pipeline
    switch (io.pe.req.bits.field) {
      is (e_CACHE_NEURON) {
        peRespPipe(0).valid := Bool(true)
        peRespPipe(0).bits.peIndex := io.pe.req.bits.peIndex
        peRespPipe(0).bits.field := io.pe.req.bits.field
        peRespPipe(0).bits.indexIntoData :=
          io.pe.req.bits.cacheAddr(2 + log2Up(elementsPerBlock) - 1, 3)
      }
      is (e_CACHE_WEIGHT) {
        printf("[INFO] Cache: PE 0x%x req for weight @ addr 0x%x\n",
          io.pe.req.bits.peIndex, io.pe.req.bits.cacheAddr)
        peRespPipe(0).valid := Bool(true)
        peRespPipe(0).bits.peIndex := io.pe.req.bits.peIndex
        peRespPipe(0).bits.field := io.pe.req.bits.field
        peRespPipe(0).bits.indexIntoData :=
          io.pe.req.bits.cacheAddr(2 + log2Up(elementsPerBlock) - 1, 2)
      }
      is (e_CACHE_WEIGHT_ONLY) {
        printf("[INFO] Cache: PE 0x%x req for weight @ addr 0x%x\n",
          io.pe.req.bits.peIndex, io.pe.req.bits.cacheAddr)
        peRespPipe(0).valid := Bool(true)
        peRespPipe(0).bits.peIndex := io.pe.req.bits.peIndex
        peRespPipe(0).bits.field := io.pe.req.bits.field
        peRespPipe(0).bits.indexIntoData :=
          io.pe.req.bits.cacheAddr(2 + log2Up(elementsPerBlock) - 1, 2)
      }
    }
  }

  // The actual data that comes out of the memory is not interpreted
  // here, i.e., we just pass the full bitsPerBlock-sized data packet
  // to the PE Table and it deals with the internal indices.
  peRespPipe(1).bits.data := mem(peCacheIndex_d0).dout(1)

  // Set the response to the Control module
  io.control.resp.valid := controlRespPipe(1).valid
  io.control.resp.bits := controlRespPipe(1).bits
  // Set the response to the Processing Element Table
  io.pe.resp.valid := peRespPipe(1).valid
  io.pe.resp.bits := peRespPipe(1).bits

  // Handle responses from memory (ANTW or similar)
  when (io.mem.resp.valid) {
    printf("[INFO] Cache: saw write to SRAM_%x(%x) <= %x\n",
      io.mem.resp.bits.cacheIndex,
      io.mem.resp.bits.addr,
      io.mem.resp.bits.data)
    mem(io.mem.resp.bits.cacheIndex).we(0) := Bool(true)
    mem(io.mem.resp.bits.cacheIndex).addr(0) := io.mem.resp.bits.addr
    mem(io.mem.resp.bits.cacheIndex).din(0) := io.mem.resp.bits.data
    // If this is done, then set the notify flag which will cause the
    // when block above to generate a response when the cache isn't
    // dealing with other requests
    when (io.mem.resp.bits.done) {
      printf("[INFO] Cache: SRAM_%x received DONE response\n",
        io.mem.resp.bits.cacheIndex)
      table(io.mem.resp.bits.cacheIndex).notifyFlag := Bool(true)
    }
  }

  // Reset
  if (preloadCache) {
    when (reset) {for (i <- 0 until cacheNumEntries) {
      table(i).valid := Bool(true)
      table(i).notifyFlag := Bool(false)
      table(i).fetch := Bool(false)
      table(i).notifyIndex := UInt(0)
      table(i).notifyMask := UInt(0)
      table(i).nnid := UInt(i)
      table(i).inUseCount := UInt(0)
    }}}
    else {
    when (reset) {for (i <- 0 until cacheNumEntries) {
      table(i).valid := Bool(false)
    }}}

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
  assert(!(io.control.req.valid && io.pe.req.valid && io.mem.req.valid),
    "Multiple simultaneous requests on the cache (dropped requests possible)")

  // The in use count should never be decremented below zero.
  assert(!(io.control.req.valid &&
    io.control.req.bits.request === e_CACHE_DECREMENT_IN_USE_COUNT &&
    table(derefNnid).inUseCount === UInt(0)),
    "Cache received control request to decrement count of zero-valued inUseCount")

  // There is no way of handling a request from the Transaction Table
  // that shows up when the cache has no free or unused entries.
  // However, this should never happen if the Transaction Table is
  // always smaller or equal to the number of cache entries.
  assert(!(io.control.req.valid &&
    io.control.req.bits.request === e_CACHE_LOAD &&
    !foundNnid &&
    (!hasFree && !hasUnused)),
  "Cache missed on ASID/NNID req, but has no free/unused entries")
}
