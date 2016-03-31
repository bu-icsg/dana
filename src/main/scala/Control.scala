// See LICENSE for license details.

package dana

import Chisel._
import cde.Parameters

class ControlCacheInterfaceResp(implicit p: Parameters) extends DanaBundle()(p) {
  val fetch = Bool()
  val tableIndex = UInt(width = log2Up(transactionTableNumEntries))
  val tableMask = UInt(width = transactionTableNumEntries)
  val cacheIndex = UInt(width = log2Up(cacheNumEntries))
  val data = Vec.fill(6){UInt(width = 16)} // [TODO] possibly fragile
  val decimalPoint = UInt(INPUT, decimalPointWidth)
  val field = UInt(width = log2Up(7)) // [TODO] fragile on Constants.scala
  val regFileLocationBit = UInt(width = 1)
}

class ControlCacheInterfaceRespLearn(implicit p: Parameters)
    extends ControlCacheInterfaceResp()(p) {
  val totalWritesMul = UInt(width = 2)
  val globalWtptr = UInt(INPUT, 16) //[TODO] possibly fragile
}

class ControlCacheInterfaceReq(implicit p: Parameters) extends DanaBundle()(p) {
  val request = UInt(width = log2Up(3)) // [TODO] fragile on Constants.scala
  val asid = UInt(width = asidWidth)
  val nnid = UInt(width = nnidWidth)
  val tableIndex = UInt(width = log2Up(transactionTableNumEntries))
  val currentLayer = UInt(width = 16) // [TODO] fragile
  val regFileLocationBit = UInt(width = 1) // [TODO] fragile
  val coreIdx = UInt(width = log2Up(numCores))
}

class ControlCacheInterfaceReqLearn(implicit p: Parameters)
    extends ControlCacheInterfaceReq()(p) {
  val totalWritesMul = UInt(width = 2)
}

class ControlCacheInterface(implicit p: Parameters) extends DanaBundle()(p) {
  lazy val req = Decoupled(new ControlCacheInterfaceReq)
  lazy val resp = Decoupled(new ControlCacheInterfaceResp).flip
}

class ControlCacheInterfaceLearn(implicit p: Parameters)
    extends ControlCacheInterface()(p) {
  override lazy val req = Decoupled(new ControlCacheInterfaceReqLearn)
  override lazy val resp = Decoupled(new ControlCacheInterfaceRespLearn).flip
}

class ControlPETableInterfaceReq(implicit p: Parameters) extends DanaBundle()(p) {
  val cacheIndex = UInt(width = log2Up(cacheNumEntries))
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
  val dwAddr = UInt(width = ioIdxWidth)
  val slopeAddr = UInt(width = ioIdxWidth)
  val biasAddr = UInt(width = ioIdxWidth)
  val auxAddr = UInt(width = ioIdxWidth)
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
  // Transaction Table connections
  io.tTable.req.ready := Bool(true)

  io.tTable.resp.valid := io.cache.resp.valid || io.regFile.resp.valid
  io.tTable.resp.bits.readyCache := io.cache.req.ready
  io.tTable.resp.bits.readyPeTable := io.peTable.req.ready
  io.tTable.resp.bits.field := UInt(0)
  io.tTable.resp.bits.data := io.cache.resp.bits.data
  io.tTable.resp.bits.tableIndex := io.cache.resp.bits.tableIndex
  io.tTable.resp.bits.decimalPoint := io.cache.resp.bits.decimalPoint
  io.tTable.resp.bits.cacheValid := io.cache.resp.valid
  io.tTable.resp.bits.layerValidIndex := io.regFile.resp.bits.tIdx
  io.tTable.resp.bits.layerValid := io.regFile.resp.valid

  // Register File connections
  io.regFile.req.valid := Bool(false)
  io.regFile.resp.ready := Bool(false) // [TODO] not correct
  io.regFile.req.bits.tIdx := io.cache.resp.bits.tableIndex
  io.regFile.req.bits.totalWrites := io.cache.resp.bits.data(0)
  io.regFile.req.bits.location := io.cache.resp.bits.regFileLocationBit

  // Handling of Cache responses
  when (io.cache.resp.valid) {
    switch (io.cache.resp.bits.field) {
      is (e_CACHE_INFO) {
        io.tTable.resp.bits.field := e_TTABLE_CACHE_VALID
        io.tTable.resp.bits.data(2) := io.cache.resp.bits.cacheIndex ##
          io.cache.resp.bits.data(2)(errorFunctionWidth - 1,0) }
      is (e_CACHE_LAYER) {
        io.tTable.resp.bits.field := e_TTABLE_LAYER
        io.regFile.req.valid := Bool(true) }}}

  // Cache connections
  io.cache.req.bits.request := UInt(0)
  io.cache.resp.ready := Bool(true)
  io.cache.req.valid := Bool(false)
  io.cache.req.bits := io.tTable.req.bits

  // PE Table connections
  io.peTable.req.valid := Bool(false)
  io.peTable.req.bits.cacheIndex := io.tTable.req.bits.cacheIndex
  io.peTable.req.bits.tIdx := io.tTable.req.bits.tableIndex
  io.peTable.req.bits.inAddr := io.tTable.req.bits.regFileAddrIn
  io.peTable.req.bits.outAddr := io.tTable.req.bits.regFileAddrOut +
    io.tTable.req.bits.currentNodeInLayer
  io.peTable.req.bits.neuronPointer := io.tTable.req.bits.neuronPointer +
    (io.tTable.req.bits.currentNodeInLayer << UInt(3))
  io.peTable.req.bits.decimalPoint := io.tTable.req.bits.decimalPoint
  io.peTable.req.bits.location := io.tTable.req.bits.regFileLocationBit

  val tTableValid = io.tTable.req.valid
  val cacheLoad = tTableValid &&
    !io.tTable.req.bits.cacheValid && !io.tTable.req.bits.waiting
  val cacheLayer = tTableValid && io.tTable.req.bits.cacheValid &&
    io.tTable.req.bits.needsLayerInfo
  val cacheDone = tTableValid && io.tTable.req.bits.isDone
  val peAllocate = tTableValid && io.tTable.req.bits.cacheValid &&
    !io.tTable.req.bits.needsLayerInfo && io.peTable.req.ready

  io.cache.req.valid := cacheLoad || cacheLayer || cacheDone
  when (cacheLoad) {
    io.cache.req.bits.request := e_CACHE_LOAD } .elsewhen(cacheLayer) {
    io.cache.req.bits.request := e_CACHE_LAYER_INFO } .elsewhen(cacheDone) {
    io.cache.req.bits.request := e_CACHE_DECREMENT_IN_USE_COUNT } .otherwise {
    io.peTable.req.valid := peAllocate }
}

class Control(implicit p: Parameters)
    extends ControlBase()(p)

class ControlLearn(implicit p: Parameters)
    extends ControlBase()(p) {
  override lazy val io = new ControlInterfaceLearn

  io.tTable.resp.bits.globalWtptr := io.cache.resp.bits.globalWtptr
  io.regFile.req.bits.totalWrites := io.cache.resp.bits.totalWritesMul *
    io.cache.resp.bits.data(0)

  io.peTable.req.bits.inAddr :=
    Mux((io.tTable.req.bits.stateLearn === e_TTABLE_STATE_LEARN_ERROR_BACKPROP) ||
    (io.tTable.req.bits.stateLearn === e_TTABLE_STATE_LEARN_WEIGHT_UPDATE),
    io.tTable.req.bits.regFileAddrIn + io.tTable.req.bits.currentNodeInLayer,
    io.tTable.req.bits.regFileAddrIn)
  io.peTable.req.bits.resetWB := io.tTable.req.bits.inLast &&
    (io.tTable.req.bits.currentNodeInLayer === UInt(0)) &&
    io.tTable.req.bits.stateLearn === e_TTABLE_STATE_LEARN_FEEDFORWARD
  io.peTable.req.bits.inFirst := io.tTable.req.bits.inFirst
  io.peTable.req.bits.inLast := io.tTable.req.bits.inLast
  io.peTable.req.bits.batchFirst := io.tTable.req.bits.batchFirst
  io.peTable.req.bits.learnAddr := io.tTable.req.bits.currentNodeInLayer
  io.peTable.req.bits.dwAddr := io.tTable.req.bits.regFileAddrDW
  io.peTable.req.bits.slopeAddr := io.tTable.req.bits.regFileAddrSlope
  io.peTable.req.bits.biasAddr := io.tTable.req.bits.regFileAddrBias +
    io.tTable.req.bits.currentNodeInLayer
  io.peTable.req.bits.auxAddr := io.tTable.req.bits.regFileAddrAux
  io.peTable.req.bits.errorFunction := io.tTable.req.bits.errorFunction
  io.peTable.req.bits.stateLearn := io.tTable.req.bits.stateLearn
  io.peTable.req.bits.learningRate := io.tTable.req.bits.learningRate
  io.peTable.req.bits.lambda := io.tTable.req.bits.lambda
  io.peTable.req.bits.numWeightBlocks := io.tTable.req.bits.numWeightBlocks
  io.peTable.req.bits.tType := io.tTable.req.bits.transactionType
  io.peTable.req.bits.globalWtptr := io.tTable.req.bits.globalWtptr

  io.cache.req.bits.totalWritesMul := Mux(io.tTable.req.bits.inLastEarly &&
    (io.tTable.req.bits.stateLearn === e_TTABLE_STATE_LEARN_FEEDFORWARD),
    UInt(2), UInt(1))
}
