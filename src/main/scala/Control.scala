package dana

import Chisel._
import cde.{Parameters, Field}

class ControlCacheInterfaceResp(implicit p: Parameters) extends DanaBundle()(p) {
  val fetch = Bool()
  val tableIndex = UInt(width = log2Up(transactionTableNumEntries))
  val tableMask = UInt(width = transactionTableNumEntries)
  val cacheIndex = UInt(width = log2Up(cacheNumEntries))
  val data = Vec.fill(6){UInt(width = 16)} // [TODO] possibly fragile
  val decimalPoint = UInt(INPUT, decimalPointWidth)
  val field = UInt(width = log2Up(7)) // [TODO] fragile on Constants.scala
  val location = UInt(width = 1)
}

class ControlCacheInterfaceRespLearn(implicit p: Parameters)
    extends ControlCacheInterfaceResp()(p) {
  val totalWritesMul = UInt(width = 2)
  val globalWtptr = UInt(INPUT, 16) //[TODO] possibly fragile
}

class ControlCacheInterfaceReq(implicit p: Parameters) extends XFilesBundle()(p) {
  val request = UInt(width = log2Up(3)) // [TODO] fragile on Constants.scala
  val asid = UInt(width = asidWidth)
  val nnid = UInt(width = nnidWidth)
  val tableIndex = UInt(width = log2Up(transactionTableNumEntries))
  val layer = UInt(width = 16) // [TODO] fragile
  val location = UInt(width = 1) // [TODO] fragile
  val coreIdx = UInt(width = log2Up(numCores))
}

class ControlCacheInterfaceReqLearn(implicit p: Parameters)
    extends ControlCacheInterfaceReq()(p) {
  val totalWritesMul = UInt(width = 2)
}

class ControlCacheInterface(implicit p: Parameters) extends DanaBundle()(p) {
  // Outbound request. nnsim-hdl equivalent:
  //   cache_types::ctl2storage_struct
  lazy val req = Decoupled(new ControlCacheInterfaceReq)
  // Inbound response. nnsim-hdl equivalent:
  //   cache_types::cache2ctl_struct
  lazy val resp = Decoupled(new ControlCacheInterfaceResp).flip
}

class ControlCacheInterfaceLearn(implicit p: Parameters)
    extends ControlCacheInterface()(p) {
  override lazy val req = Decoupled(new ControlCacheInterfaceReqLearn)
  override lazy val resp = Decoupled(new ControlCacheInterfaceRespLearn).flip
}

class ControlPETableInterfaceReq(implicit p: Parameters) extends DanaBundle()(p) {
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
  val location = UInt(width = 1)
  val neuronPointer = UInt(width = 12) // [TODO] fragile
  val decimalPoint = UInt(width = decimalPointWidth)
}

class ControlPETableInterfaceReqLearn(implicit p: Parameters)
    extends ControlPETableInterfaceReq()(p) {
  val learnAddr = UInt(width = ioIdxWidth)
  val deltaAddr = UInt(width = ioIdxWidth)
  val dwAddr = UInt(width = ioIdxWidth)
  val slopeAddr = UInt(width = ioIdxWidth)
  val biasAddr = UInt(width = ioIdxWidth)
  val errorFunction = UInt(width = log2Up(2)) // [TODO] fragile
  val stateLearn = UInt(width = log2Up(7)) // [TODO] fragile
  val inLast = Bool()
  val resetWB = Bool()
  val inFirst = Bool()
  val batchFirst = Bool()
  val learningRate = UInt(width = 16) // [TODO] fragile
  val lambda = UInt(width = 16) // [TODO] fragile
  val numWeightBlocks = UInt(width = 16) // [TODO] fragile
  val tType = UInt(width = log2Up(3)) // [TODO] fragile
  val globalWtptr = UInt(width = 16) // [TODO] fragile
}

class ControlPETableInterface(implicit p: Parameters) extends DanaBundle()(p) {
  lazy val req = Decoupled(new ControlPETableInterfaceReqLearn)
  // No response is necessary as the Control module needs to know is
  // if the PE Table has a free entry. This is communicated by means
  // of the Decoupled `ready` signal.
}

class ControlPETableInterfaceLearn(implicit p: Parameters)
    extends ControlPETableInterface()(p) {
  override lazy val req = Decoupled(new ControlPETableInterfaceReqLearn)
}

class ControlRegisterFileInterfaceReq(implicit p: Parameters) extends DanaBundle()(p) {
  val tIdx = UInt(width = transactionTableNumEntries)
  val totalWrites = UInt(width = 16) // [TODO] fragile
  val location = UInt(width = 1)     // [TODO] fragile
}

class ControlRegisterFileInterfaceResp(implicit p: Parameters) extends DanaBundle()(p) {
  val tIdx = UInt(width = transactionTableNumEntries)
}

class ControlRegisterFileInterface(implicit p: Parameters) extends DanaBundle()(p) {
  // Outbound request/inbound response. No nnsim-hdl equivalent.
  val req = Decoupled(new ControlRegisterFileInterfaceReq)
  val resp = Decoupled(new ControlRegisterFileInterfaceResp).flip
}

class ControlInterface(implicit p: Parameters) extends DanaBundle()(p) {
  lazy val tTable = (new TTableControlInterface).flip
  lazy val cache = new ControlCacheInterface
  lazy val peTable = new ControlPETableInterface
  val regFile = new ControlRegisterFileInterface
}

class ControlInterfaceLearn(implicit p: Parameters)
    extends ControlInterface()(p) {
  override lazy val tTable = (new TTableControlInterfaceLearn).flip
  override lazy val cache = new ControlCacheInterfaceLearn
  override lazy val peTable = new ControlPETableInterfaceLearn
}

class ControlBase(implicit p: Parameters) extends DanaModule()(p) {
  lazy val io = new ControlInterface
  // IO Driver Functions
  def reqCache(valid: Bool, request: UInt, asid: UInt, nnid: UInt,
    tableIndex: UInt, coreIdx: UInt, layer: UInt, location: UInt) {
    io.cache.req.valid := valid
    io.cache.req.bits.request := request
    io.cache.req.bits.asid := asid
    io.cache.req.bits.nnid := nnid
    io.cache.req.bits.tableIndex := tableIndex
    io.cache.req.bits.coreIdx := coreIdx
    io.cache.req.bits.layer := layer
    io.cache.req.bits.location := location
  }
  def reqPETable(valid: Bool, cacheIndex: UInt, tIdx: UInt, inAddr: UInt,
    outAddr: UInt, neuronPointer: UInt, decimalPoint: UInt, location: UInt) {
    io.peTable.req.valid := valid
    io.peTable.req.bits.cacheIndex := cacheIndex
    io.peTable.req.bits.tIdx := tIdx
    io.peTable.req.bits.inAddr := inAddr
    io.peTable.req.bits.outAddr := outAddr
    io.peTable.req.bits.neuronPointer := neuronPointer
    io.peTable.req.bits.decimalPoint := decimalPoint
    io.peTable.req.bits.location := location
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
  io.tTable.resp.bits.data := Vec.fill(6){UInt(0)}
  io.tTable.resp.bits.decimalPoint := UInt(0)
  io.tTable.resp.bits.layerValid := Bool(false)
  io.tTable.resp.bits.layerValidIndex := UInt(0)
  // io.cache defaults
  io.cache.resp.ready := Bool(true) // [TODO] not correct
  reqCache(Bool(false), UInt(0), UInt(0), UInt(0), UInt(0), UInt(0), UInt(0),
    UInt(0))
  // io.petable defaults
  reqPETable(Bool(false),
    UInt(0), UInt(0), UInt(0), UInt(0), UInt(0), UInt(0), UInt(0))
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
        io.tTable.resp.bits.data(3) := io.cache.resp.bits.data(3)
        io.tTable.resp.bits.data(4) := io.cache.resp.bits.data(4)
        io.tTable.resp.bits.data(5) := io.cache.resp.bits.data(5)
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
        io.regFile.req.bits.totalWrites := io.cache.resp.bits.data(0)
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
      reqCache(valid = Bool(true), request = e_CACHE_LOAD,
        asid = io.tTable.req.bits.asid,
        nnid = io.tTable.req.bits.nnid,
        tableIndex = io.tTable.req.bits.tableIndex,
        coreIdx = io.tTable.req.bits.coreIdx,
        layer = UInt(0),
        location = UInt(0))
    }
      .elsewhen (io.tTable.req.bits.cacheValid && io.tTable.req.bits.needsLayerInfo) {
      // Send a request to the storage module
      // val inLastLearn = (io.tTable.req.bits.inLastEarly) &&
      //   (io.tTable.req.bits.stateLearn === e_TTABLE_STATE_LEARN_FEEDFORWARD)
      printf("[INFO] Control: TTable layer req inFirst 0x%x\n",
        io.tTable.req.bits.inFirst)
      reqCache(valid = Bool(true), request = e_CACHE_LAYER_INFO,
        asid = io.tTable.req.bits.asid,
        nnid = io.tTable.req.bits.nnid,
        tableIndex = io.tTable.req.bits.tableIndex,
        coreIdx = io.tTable.req.bits.coreIdx,
        layer = io.tTable.req.bits.currentLayer,
        location = io.tTable.req.bits.regFileLocationBit)
    }
    // If this entry is done, then its cache entry needs to be invalidated
      .elsewhen (io.tTable.req.bits.isDone) {
      // [TODO] This passes no information about the core index which
      // _may_ be needed to close out any final cache updates.
      reqCache(valid = Bool(true), request = e_CACHE_DECREMENT_IN_USE_COUNT,
        asid = io.tTable.req.bits.asid,
        nnid = io.tTable.req.bits.nnid,
        tableIndex = UInt(0),
        coreIdx = UInt(0),
        layer = UInt(0),
        location = UInt(0))
    }
      .elsewhen (io.tTable.req.bits.cacheValid && !io.tTable.req.bits.needsLayerInfo &&
      io.peTable.req.ready) {
      // Go ahead and allocate an entry in the Processing Element
      reqPETable(valid = Bool(true),
        cacheIndex = io.tTable.req.bits.cacheIndex,
        tIdx = io.tTable.req.bits.tableIndex,
        inAddr = io.tTable.req.bits.regFileAddrIn,
        outAddr = io.tTable.req.bits.regFileAddrOut +
          io.tTable.req.bits.currentNodeInLayer,
        neuronPointer = io.tTable.req.bits.neuronPointer +
          (io.tTable.req.bits.currentNodeInLayer << UInt(3)),
        decimalPoint = io.tTable.req.bits.decimalPoint,
        location = io.tTable.req.bits.regFileLocationBit)
    }
  }
  when (io.regFile.resp.valid) {
    // The register file for the next layer is 100% ready so we make
    // the specific Transaction Table entry stop waiting
    io.tTable.resp.valid := Bool(true)
    io.tTable.resp.bits.layerValid := Bool(true)
    io.tTable.resp.bits.layerValidIndex := io.regFile.resp.bits.tIdx
  }

  // Assertions
}

class Control(implicit p: Parameters)
    extends ControlBase()(p)

class ControlLearn(implicit p: Parameters)
    extends ControlBase()(p) {
  override lazy val io = new ControlInterfaceLearn

  def reqCache(valid: Bool, request: UInt, asid: UInt, nnid: UInt,
    tableIndex: UInt, coreIdx: UInt, layer: UInt, location: UInt,
    totalWritesMul: UInt) {
    reqCache(valid = valid, request = request, asid = asid, nnid = nnid,
      tableIndex = tableIndex, coreIdx = coreIdx, layer = layer,
      location = location)
    io.cache.req.bits.totalWritesMul := totalWritesMul
  }

  def reqPETable(valid: Bool, cacheIndex: UInt, tIdx: UInt, inAddr: UInt,
    outAddr: UInt, neuronPointer: UInt, decimalPoint: UInt, location: UInt,
    // learning-specific
    resetWB: Bool, inFirst: Bool, inLast: Bool, batchFirst: Bool,
    learnAddr: UInt, deltaAddr: UInt, dwAddr: UInt, slopeAddr: UInt,
    biasAddr: UInt, errorFunction: UInt, stateLearn: UInt, learningRate: UInt,
    lambda: UInt, numWeightBlocks: UInt, transactionType: UInt,
    globalWtptr: UInt) {
    reqPETable(valid = valid, cacheIndex = cacheIndex, tIdx = tIdx,
      inAddr = inAddr, outAddr = outAddr, neuronPointer = neuronPointer,
      decimalPoint = decimalPoint, location = location)
    io.peTable.req.bits.resetWB := resetWB
    io.peTable.req.bits.inFirst := inFirst
    io.peTable.req.bits.inLast := inLast
    io.peTable.req.bits.batchFirst := batchFirst
    io.peTable.req.bits.learnAddr := learnAddr
    io.peTable.req.bits.deltaAddr := deltaAddr
    io.peTable.req.bits.dwAddr := dwAddr
    io.peTable.req.bits.slopeAddr := slopeAddr
    io.peTable.req.bits.biasAddr := biasAddr
    io.peTable.req.bits.errorFunction := errorFunction
    io.peTable.req.bits.stateLearn := stateLearn
    io.peTable.req.bits.learningRate := learningRate
    io.peTable.req.bits.lambda := lambda
    io.peTable.req.bits.numWeightBlocks := numWeightBlocks
    io.peTable.req.bits.tType := transactionType
    io.peTable.req.bits.globalWtptr := globalWtptr
  }

  io.tTable.resp.bits.globalWtptr := UInt(0)

  reqCache(Bool(false), UInt(0), UInt(0), UInt(0), UInt(0), UInt(0), UInt(0),
    UInt(0), UInt(0))
  reqPETable(Bool(false),
    UInt(0), UInt(0), UInt(0), UInt(0), UInt(0), UInt(0), UInt(0),
    Bool(false), Bool(false), Bool(false), Bool(false),
    UInt(0), UInt(0), UInt(0), UInt(0), UInt(0), UInt(0), UInt(0), UInt(0),
    UInt(0), UInt(0), UInt(0), UInt(0))

  when (io.cache.resp.valid) {
    switch (io.cache.resp.bits.field) {
      is (e_CACHE_INFO) {
        io.tTable.resp.bits.globalWtptr := io.cache.resp.bits.globalWtptr
      }
      is (e_CACHE_LAYER) {
        io.regFile.req.bits.totalWrites := io.cache.resp.bits.totalWritesMul *
          io.cache.resp.bits.data(0)
      }
    }
  }

  when (io.tTable.req.valid) {
    when (!io.tTable.req.bits.cacheValid && !io.tTable.req.bits.waiting) {
      // Send a request to the cache
      reqCache(valid = Bool(true), request = e_CACHE_LOAD,
        asid = io.tTable.req.bits.asid,
        nnid = io.tTable.req.bits.nnid,
        tableIndex = io.tTable.req.bits.tableIndex,
        coreIdx = io.tTable.req.bits.coreIdx,
        layer = UInt(0), location = UInt(0),
        totalWritesMul = UInt(0))
    } .elsewhen (io.tTable.req.bits.cacheValid &&
      io.tTable.req.bits.needsLayerInfo) {
      // Send a request to the storage module
      val totalWritesMul = Mux(io.tTable.req.bits.inLastEarly &&
        (io.tTable.req.bits.stateLearn === e_TTABLE_STATE_LEARN_FEEDFORWARD),
        UInt(3), Mux(!io.tTable.req.bits.inFirst &&
          (io.tTable.req.bits.stateLearn === e_TTABLE_STATE_LEARN_ERROR_BACKPROP),
          UInt(2), UInt(1)))
      // val inLastLearn = (io.tTable.req.bits.inLastEarly) &&
      //   (io.tTable.req.bits.stateLearn === e_TTABLE_STATE_LEARN_FEEDFORWARD)
      printf("[INFO] Control: TTable layer req inFirst/inLastEarly/state/totalWritesMul 0x%x/0x%x/0x%x/0x%x\n",
        io.tTable.req.bits.inFirst,
        io.tTable.req.bits.inLastEarly, io.tTable.req.bits.stateLearn, totalWritesMul)
      reqCache(valid = Bool(true), request = e_CACHE_LAYER_INFO,
        asid = io.tTable.req.bits.asid,
        nnid = io.tTable.req.bits.nnid,
        tableIndex = io.tTable.req.bits.tableIndex,
        coreIdx = io.tTable.req.bits.coreIdx,
        layer = io.tTable.req.bits.currentLayer,
        location = io.tTable.req.bits.regFileLocationBit,
        totalWritesMul = totalWritesMul)
    } .elsewhen (io.tTable.req.bits.isDone) {
      // [TODO] This passes no information about the core index which
      // _may_ be needed to close out any final cache updates.
      reqCache(valid = Bool(true), request = e_CACHE_DECREMENT_IN_USE_COUNT,
        asid = io.tTable.req.bits.asid, nnid = io.tTable.req.bits.nnid,
        tableIndex = UInt(0), coreIdx = UInt(0), layer = UInt(0),
        location = UInt(0), totalWritesMul = Bool(false))
    } .elsewhen (io.tTable.req.bits.cacheValid &&
      !io.tTable.req.bits.needsLayerInfo && io.peTable.req.ready) {
      // Go ahead and allocate an entry in the Processing Element
      reqPETable(valid = Bool(true),
        cacheIndex = io.tTable.req.bits.cacheIndex,
        tIdx = io.tTable.req.bits.tableIndex,
        inAddr =
          Mux((io.tTable.req.bits.stateLearn === e_TTABLE_STATE_LEARN_ERROR_BACKPROP) ||
          (io.tTable.req.bits.stateLearn === e_TTABLE_STATE_LEARN_WEIGHT_UPDATE),
          io.tTable.req.bits.regFileAddrIn + io.tTable.req.bits.currentNodeInLayer,
          io.tTable.req.bits.regFileAddrIn),
        outAddr = io.tTable.req.bits.regFileAddrOut +
          io.tTable.req.bits.currentNodeInLayer,
        neuronPointer = io.tTable.req.bits.neuronPointer +
          (io.tTable.req.bits.currentNodeInLayer << UInt(3)),
        decimalPoint = io.tTable.req.bits.decimalPoint,
        location = io.tTable.req.bits.regFileLocationBit,
        resetWB = io.tTable.req.bits.inLast &&
          (io.tTable.req.bits.currentNodeInLayer === UInt(0)) &&
          io.tTable.req.bits.stateLearn === e_TTABLE_STATE_LEARN_FEEDFORWARD,
        inFirst = io.tTable.req.bits.inFirst,
        inLast = io.tTable.req.bits.inLast,
        batchFirst = io.tTable.req.bits.batchFirst,
        learnAddr = io.tTable.req.bits.currentNodeInLayer,
        deltaAddr = io.tTable.req.bits.regFileAddrDelta +
          io.tTable.req.bits.currentNodeInLayer,
        dwAddr = io.tTable.req.bits.regFileAddrDW,
        slopeAddr = io.tTable.req.bits.regFileAddrSlope,
        biasAddr = io.tTable.req.bits.regFileAddrBias +
          io.tTable.req.bits.currentNodeInLayer,
        errorFunction = io.tTable.req.bits.errorFunction,
        stateLearn = io.tTable.req.bits.stateLearn,
        learningRate = io.tTable.req.bits.learningRate,
        lambda = io.tTable.req.bits.lambda,
        numWeightBlocks = io.tTable.req.bits.numWeightBlocks,
        transactionType = io.tTable.req.bits.transactionType,
        globalWtptr = io.tTable.req.bits.globalWtptr
      )
    }
  }

}
