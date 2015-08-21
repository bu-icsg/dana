package dana

import Chisel._

abstract trait ControlParameters extends DanaParameters {
}

class ControlCacheInterfaceResp extends DanaBundle with ControlParameters {
  val fetch = Bool()
  val tableIndex = UInt(width = log2Up(transactionTableNumEntries))
  val tableMask = UInt(width = transactionTableNumEntries)
  val cacheIndex = UInt(width = log2Up(cacheNumEntries))
  val data = Vec.fill(3){UInt(width = 16)} // [TODO] possibly fragile
  val decimalPoint = UInt(INPUT, decimalPointWidth)
  val field = UInt(width = log2Up(7)) // [TODO] fragile on Constants.scala
  val location = UInt(width = 1)
  val totalWritesMul = UInt(width = 2)
}

class ControlCacheInterfaceReq extends DanaBundle with ControlParameters {
  val request = UInt(width = log2Up(3)) // [TODO] fragile on Constants.scala
  val asid = UInt(width = asidWidth)
  val nnid = UInt(width = nnidWidth)
  val tableIndex = UInt(width = log2Up(transactionTableNumEntries))
  val layer = UInt(width = 16) // [TODO] fragile
  val location = UInt(width = 1) // [TODO] fragile
  val coreIdx = UInt(width = log2Up(numCores))
  val totalWritesMul = UInt(width = 2)
}

class ControlCacheInterface extends DanaBundle with ControlParameters {
  // Outbound request. nnsim-hdl equivalent:
  //   cache_types::ctl2storage_struct
  val req = Decoupled(new ControlCacheInterfaceReq)
  // Inbound response. nnsim-hdl equivalent:
  //   cache_types::cache2ctl_struct
  val resp = Decoupled(new ControlCacheInterfaceResp).flip
}

class ControlPETableInterface extends DanaBundle with ControlParameters {
  // Outbound request. nnsim-hdl equivalent:
  //   control_types::ctl2pe_table_struct
  val req = Decoupled(new DanaBundle {
    // The PE Index shouldn't be needed if the PE Table is allocating PEs
    // val peIndex = UInt(width = log2Up(peTableNumEntries))
    val cacheIndex = UInt(width = log2Up(cacheNumEntries))
    // new_state -- this should be unnecessary as all we need to do is
    // give the PE a kick, which should be accomplished with the
    // decoupled valid signal
    val tIdx = UInt(width = log2Up(transactionTableNumEntries))
    // [TODO] Change ioIdxWidth to regFileNumElements?
    val inAddr = UInt(width = ioIdxWidth)
    val outAddr = UInt(width = ioIdxWidth)
    val learnAddr = UInt(width = ioIdxWidth)
    val deltaAddr = UInt(width = ioIdxWidth)
    val indwAddr = UInt(width = ioIdxWidth)
    val outdwAddr = UInt(width = ioIdxWidth)
    val location = UInt(width = 1)
    val neuronPointer = UInt(width = 12) // [TODO] fragile
    val decimalPoint = UInt(width = decimalPointWidth)
    val errorFunction = UInt(width = log2Up(2)) // [TODO] fragile
    val stateLearn = UInt(width = log2Up(7)) // [TODO] fragile
    val inLast = Bool()
    val resetWB = Bool()
    val inFirst = Bool()
  })
  // No response is necessary as the Control module needs to know is
  // if the PE Table has a free entry. This is communicated by means
  // of the Decoupled `ready` signal.
}

class ControlRegisterFileInterface extends DanaBundle with ControlParameters {
  // Outbound request/inbound response. No nnsim-hdl equivalent.
  val req = Decoupled(new DanaBundle {
    val tIdx = UInt(width = transactionTableNumEntries)
    val totalWrites = UInt(width = 16) // [TODO] fragile
    val location = UInt(width = 1) // [TODO] fragile
  })
  val resp = Decoupled(new DanaBundle {
    val tIdx = UInt(width = transactionTableNumEntries)
  }).flip
}

class ControlInterface extends DanaBundle {
  val tTable = (new TTableControlInterface).flip
  val cache = new ControlCacheInterface
  val peTable = new ControlPETableInterface
  val regFile = new ControlRegisterFileInterface
}

class Control extends DanaModule {
  val io = new ControlInterface

  // IO Driver Functions
  def reqCache(valid: Bool, request: UInt, asid: UInt, nnid: UInt,
    tableIndex: UInt, coreIdx: UInt, layer: UInt, location: UInt,
    totalWritesMul: UInt) {
    io.cache.req.valid := valid
    io.cache.req.bits.request := request
    io.cache.req.bits.asid := asid
    io.cache.req.bits.nnid := nnid
    io.cache.req.bits.tableIndex := tableIndex
    io.cache.req.bits.coreIdx := coreIdx
    io.cache.req.bits.layer := layer
    io.cache.req.bits.location := location
    io.cache.req.bits.totalWritesMul := totalWritesMul
  }
  def reqPETable(valid: Bool, cacheIndex: UInt, tIdx: UInt,  inAddr: UInt,
    outAddr: UInt, learnAddr: UInt, deltaAddr: UInt, indwAddr: UInt, outdwAddr: UInt,
    neuronPointer: UInt, decimalPoint: UInt, errorFunction: UInt,
    location: UInt, stateLearn: UInt, inLast: UInt, resetWB: Bool, inFirst: UInt) {
    io.peTable.req.valid := valid
    io.peTable.req.bits.cacheIndex := cacheIndex
    io.peTable.req.bits.tIdx := tIdx
    io.peTable.req.bits.inAddr := inAddr
    io.peTable.req.bits.outAddr := outAddr
    io.peTable.req.bits.learnAddr := learnAddr
    io.peTable.req.bits.deltaAddr := deltaAddr
    io.peTable.req.bits.indwAddr := indwAddr
    io.peTable.req.bits.outdwAddr := outdwAddr
    io.peTable.req.bits.neuronPointer := neuronPointer
    io.peTable.req.bits.decimalPoint := decimalPoint
    io.peTable.req.bits.errorFunction := errorFunction
    io.peTable.req.bits.location := location
    io.peTable.req.bits.stateLearn := stateLearn
    io.peTable.req.bits.inLast := inLast
    io.peTable.req.bits.resetWB := resetWB
    io.peTable.req.bits.inFirst := inFirst
  }

  // io.tTable defaults
  // The actual req.ready signal serves no purpose. Readiness is
  // indicated using the readyCache and readyPeTable lines passed
  // using the response portion of the tTable bundle.
  io.tTable.req.ready := Bool(true)
  io.tTable.resp.valid := Bool(false)
  io.tTable.resp.bits.readyCache := io.cache.req.ready
  io.tTable.resp.bits.readyPeTable := io.peTable.req.ready
  io.tTable.resp.bits.cacheValid := Bool(false)
  io.tTable.resp.bits.tableIndex := UInt(0)
  io.tTable.resp.bits.field := UInt(0)
  io.tTable.resp.bits.data := Vec.fill(3){UInt(0)}
  io.tTable.resp.bits.decimalPoint := UInt(0)
  io.tTable.resp.bits.layerValid := Bool(false)
  io.tTable.resp.bits.layerValidIndex := UInt(0)
  // io.cache defaults
  io.cache.resp.ready := Bool(true) // [TODO] not correct
  reqCache(Bool(false), UInt(0), UInt(0), UInt(0), UInt(0), UInt(0), UInt(0),
    UInt(0), UInt(0))
  // io.petable defaults
  reqPETable(Bool(false), UInt(0), UInt(0), UInt(0), UInt(0), UInt(0), UInt(0), UInt(0),
    UInt(0), UInt(0), UInt(0), UInt(0), UInt(0), UInt(0), UInt(0), Bool(false),
    Bool(false))
  // io.regFile defaults
  io.regFile.req.valid := Bool(false)
  io.regFile.req.bits.tIdx := UInt(0)
  io.regFile.req.bits.totalWrites := UInt(0)
  io.regFile.req.bits.location := UInt(0)
  io.regFile.resp.ready := Bool(false) // [TODO] not correct

  // This is where we handle responses
  when (io.cache.resp.valid) {
    io.tTable.resp.valid := Bool(true)
    io.tTable.resp.bits.cacheValid := Bool(true)
    io.tTable.resp.bits.tableIndex := io.cache.resp.bits.tableIndex
    switch (io.cache.resp.bits.field) {
      is (e_CACHE_INFO) {
        io.tTable.resp.bits.field := e_TTABLE_CACHE_VALID
        io.tTable.resp.bits.data(0) := io.cache.resp.bits.data(0)
        io.tTable.resp.bits.data(1) := io.cache.resp.bits.data(1)
        io.tTable.resp.bits.data(2) := io.cache.resp.bits.cacheIndex ##
          io.cache.resp.bits.data(2)(errorFunctionWidth - 1,0)
        io.tTable.resp.bits.decimalPoint := io.cache.resp.bits.decimalPoint
      }
      is (e_CACHE_LAYER) {
        io.tTable.resp.bits.field := e_TTABLE_LAYER // [TODO] may be wrong
        io.tTable.resp.bits.data := io.cache.resp.bits.data
        // Inform the Register File aobut the number of writes that it
        // is expected to see. The total writes is equal to the number
        // of nodes in the current layer. [TODO] This shouldn't
        // technically be allowed to go through when the current layer
        // is the last layer, but I don't think it's hurting anything.
        io.regFile.req.valid := Bool(true)
        io.regFile.req.bits.tIdx := io.cache.resp.bits.tableIndex
        // [TODO] This won't work as the tTable data is no longer
        // valid when the cache response comes back.
        io.regFile.req.bits.totalWrites := io.cache.resp.bits.totalWritesMul *
          io.cache.resp.bits.data(0)
        // This is the output location. This needs to match the
        // convention used for the Processing Elements
        io.regFile.req.bits.location := io.cache.resp.bits.location
      }
    }
  }
  // No inbound requests, so we just handle whatever is valid coming
  // from the Transaction Table
  when (io.tTable.req.valid) {
    printf("[INFO] Control sees core index: %d\n",
      io.tTable.req.bits.coreIdx)
    // Cache state is unknown and we're not waiting for the cache to
    // respond
    when (!io.tTable.req.bits.cacheValid && !io.tTable.req.bits.waiting) {
      // Send a request to the cache
      reqCache(Bool(true), e_CACHE_LOAD, io.tTable.req.bits.asid,
        io.tTable.req.bits.nnid, io.tTable.req.bits.tableIndex,
        io.tTable.req.bits.coreIdx, UInt(0), UInt(0), Bool(false))
    }
      .elsewhen (io.tTable.req.bits.cacheValid && io.tTable.req.bits.needsLayerInfo) {
      // Send a request to the storage module
      val totalWritesMul = Mux(io.tTable.req.bits.inLastEarly &&
        (io.tTable.req.bits.stateLearn === e_TTABLE_STATE_LEARN_FEEDFORWARD),
        UInt(3), Mux(!io.tTable.req.bits.inFirst &&
          (io.tTable.req.bits.stateLearn === e_TTABLE_STATE_LEARN_ERROR_BACKPROP),
        UInt(2), UInt(1)))
      // val inLastLearn = (io.tTable.req.bits.inLastEarly) &&
      //   (io.tTable.req.bits.stateLearn === e_TTABLE_STATE_LEARN_FEEDFORWARD)
      printf("[INFO] Control: TTable layer req inLastEarly/state/totalWritesMul 0x%x/0x%x/0x%x\n",
        io.tTable.req.bits.inLastEarly, io.tTable.req.bits.stateLearn, totalWritesMul)
      reqCache(Bool(true), e_CACHE_LAYER_INFO, io.tTable.req.bits.asid,
        io.tTable.req.bits.nnid, io.tTable.req.bits.tableIndex,
        io.tTable.req.bits.coreIdx, io.tTable.req.bits.currentLayer,
        io.tTable.req.bits.currentLayer(0), totalWritesMul)
    }
    // If this entry is done, then its cache entry needs to be invalidated
      .elsewhen (io.tTable.req.bits.isDone) {
      // [TODO] This passes no information about the core index which
      // _may_ be needed to close out any final cache updates.
      reqCache(Bool(true), e_CACHE_DECREMENT_IN_USE_COUNT,
        io.tTable.req.bits.asid, io.tTable.req.bits.nnid,
        UInt(0), UInt(0), UInt(0), UInt(0), Bool(false))
    }
      .elsewhen (io.tTable.req.bits.cacheValid && !io.tTable.req.bits.needsLayerInfo &&
      io.peTable.req.ready) {
      // Go ahead and allocate an entry in the Processing Element
      reqPETable(Bool(true), // valid
        // The specific cache entry where the NN configuration for
        // this PE is located
        io.tTable.req.bits.cacheIndex, // cacheIndex
        // Table Index, no ASID/TID are used
        io.tTable.req.bits.tableIndex,
        // The input address is contained in the TTable request
        io.tTable.req.bits.regFileAddrIn,
        // The output address is a base output (specified by the
        // TTable request) plus an offset (which neuron this is)
        io.tTable.req.bits.regFileAddrOut+io.tTable.req.bits.currentNodeInLayer,
        // The learn address could mean many things, but is generally
        // used to pass an _additional_ register file address used for
        // learning
        io.tTable.req.bits.currentNodeInLayer,
        //Error address in LERAN_FEEDFORWARD state means the address used to save
        //the calculated error values
        io.tTable.req.bits.regFileAddrDelta+io.tTable.req.bits.currentNodeInLayer,
        // The DWIn address is where the delta--weight products will be
        // read
        io.tTable.req.bits.regFileAddrDWIn,
        // The DWOut address is where the delta--weight products will be
        // written (and accumulated by the Register File)
        io.tTable.req.bits.regFileAddrDWOut,
        // The neuron pointer is going to be the base pointer that
        // lives in the Transaction Table plus an offset based on the
        // current node that we're processing. The shift by 3 is to
        // convert a neuron number into a memory address. This is
        // fragile on the neuron size of 64 bits.
        io.tTable.req.bits.neuronPointer + // neuronPointer
          (io.tTable.req.bits.currentNodeInLayer << UInt(3)),
        // Pass along the decimal point
        io.tTable.req.bits.decimalPoint, // decimalPoint
        // Pass along the error function
        io.tTable.req.bits.errorFunction,
        // Communicate the "location" which eventually the Register
        // File will use to determine which of two locations is the
        // "writeCount" for the current layer vs. the next layer
        io.tTable.req.bits.currentLayer(0),
        // Pass along the state (TTable "stateLearn") to the PE Table
        // so that the PE can handle this differently based on the
        // type of opeartion the PE needs to perform
        io.tTable.req.bits.stateLearn,
        // The state is also moderated by the "inLast" bit so this is
        // also passed along
        io.tTable.req.bits.inLast,
        // The condition under which the Register File Block WB Table
        // should be reset
        io.tTable.req.bits.inLast &&
          (io.tTable.req.bits.currentNodeInLayer === UInt(0)) &&
          io.tTable.req.bits.stateLearn === e_TTABLE_STATE_LEARN_FEEDFORWARD,
        //inFirst bit is passed along for to check for the corner case in weight
        //update state
        io.tTable.req.bits.inFirst
      )
    }
  }
  // Responses from the Register File specific to layer updates. These
  // use different lines than TTable responses in the above block so
  // that these won't cause aliasing conflicts.
  when (io.regFile.resp.valid) {
    // The register file for the next layer is 100% ready so we make
    // the specific Transaction Table entry stop waiting
    io.tTable.resp.valid := Bool(true)
    io.tTable.resp.bits.layerValid := Bool(true)
    io.tTable.resp.bits.layerValidIndex := io.regFile.resp.bits.tIdx
  }

  // Assertions
}
