// See LICENSE for license details.

package dana

import chisel3._
import chisel3.util._
import config._

class ControlCacheInterfaceResp(implicit p: Parameters) extends DanaBundle()(p) {
  val fetch              = Bool()
  val tableIndex         = UInt(log2Up(transactionTableNumEntries).W)
  val tableMask          = UInt(transactionTableNumEntries.W)
  val cacheIndex         = UInt(log2Up(cacheNumEntries).W)
  val data               = Vec(6, UInt(16.W)) // [TODO] fragile
  val decimalPoint       = UInt(decimalPointWidth.W)
  val field              = UInt(log2Up(7).W) // [TODO] fragile on Constants.scala
  val regFileLocationBit = UInt(1.W)
}

class ControlCacheInterfaceRespLearn(implicit p: Parameters)
    extends ControlCacheInterfaceResp()(p) {
  val totalWritesMul     = UInt(2.W)
  val globalWtptr        = UInt(16.W) //[TODO] possibly fragile
}

class ControlCacheInterfaceReq(implicit p: Parameters) extends DanaBundle()(p) {
  val request            = UInt(log2Up(3).W) // [TODO] fragile Constants.scala
  val asid               = UInt(asidWidth.W)
  val nnid               = UInt(nnidWidth.W)
  val tableIndex         = UInt(log2Up(transactionTableNumEntries).W)
  val currentLayer       = UInt(16.W) // [TODO] fragile
  val regFileLocationBit = UInt(1.W) // [TODO] fragile
}

class ControlCacheInterfaceReqLearn(implicit p: Parameters)
    extends ControlCacheInterfaceReq()(p) {
  val totalWritesMul     = UInt(2.W)
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
  val cacheIndex      = UInt(log2Up(cacheNumEntries).W)
  val tIdx            = UInt(log2Up(transactionTableNumEntries).W)
  // [TODO] Change ioIdxWidth to regFileNumElements?
  val inAddr          = UInt(ioIdxWidth.W)
  val outAddr         = UInt(ioIdxWidth.W)
  val location        = UInt(1.W)
  val neuronPointer   = UInt(12.W) // [TODO] fragile
  val decimalPoint    = UInt(decimalPointWidth.W)
}

class ControlPETableInterfaceReqLearn(implicit p: Parameters)
    extends ControlPETableInterfaceReq()(p) {
  val learnAddr       = UInt(ioIdxWidth.W)
  val dwAddr          = UInt(ioIdxWidth.W)
  val slopeAddr       = UInt(ioIdxWidth.W)
  val biasAddr        = UInt(ioIdxWidth.W)
  val auxAddr         = UInt(ioIdxWidth.W)
  val errorFunction   = UInt(log2Up(2).W) // [TODO] fragile
  val stateLearn      = UInt(log2Up(7).W) // [TODO] fragile
  val inLast          = Bool()
  val resetWB         = Bool()
  val inFirst         = Bool()
  val batchFirst      = Bool()
  val learningRate    = UInt(16.W) // [TODO] fragile
  val lambda          = UInt(16.W) // [TODO] fragile
  val numWeightBlocks = UInt(16.W) // [TODO] fragile
  val tType           = UInt(log2Up(3).W) // [TODO] fragile
  val globalWtptr     = UInt(16.W) // [TODO] fragile
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
  val tIdx        = UInt(transactionTableNumEntries.W)
  val totalWrites = UInt(16.W) // [TODO] fragile
  val location    = UInt(1.W)     // [TODO] fragile
}

class ControlRegisterFileInterfaceResp(implicit p: Parameters) extends DanaBundle()(p) {
  val tIdx        = UInt(transactionTableNumEntries.W)
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
  lazy val io = IO(new ControlInterface)

  override val printfSigil = "Dana.Control: "

  // Transaction Table connections
  io.tTable.req.ready := true.B

  io.tTable.resp.valid := io.cache.resp.valid || io.regFile.resp.valid
  io.tTable.resp.bits.readyCache := io.cache.req.ready
  io.tTable.resp.bits.readyPeTable := io.peTable.req.ready
  io.tTable.resp.bits.field := 0.U
  io.tTable.resp.bits.data := io.cache.resp.bits.data
  io.tTable.resp.bits.tableIndex := io.cache.resp.bits.tableIndex
  io.tTable.resp.bits.tableMask := io.cache.resp.bits.tableMask
  io.tTable.resp.bits.decimalPoint := io.cache.resp.bits.decimalPoint
  io.tTable.resp.bits.cacheValid := io.cache.resp.valid
  io.tTable.resp.bits.layerValidIndex := io.regFile.resp.bits.tIdx
  io.tTable.resp.bits.layerValid := io.regFile.resp.valid

  // Register File connections
  io.regFile.req.valid := false.B
  io.regFile.resp.ready := false.B // [TODO] not correct
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
        io.regFile.req.valid := true.B }}}

  // Cache connections
  io.cache.req.bits.request := 0.U
  io.cache.resp.ready := true.B
  io.cache.req.valid := false.B
  // These connections need to happen explicitly
  io.cache.req.bits.asid := io.tTable.req.bits.asid
  io.cache.req.bits.nnid := io.tTable.req.bits.nnid
  io.cache.req.bits.tableIndex := io.tTable.req.bits.tableIndex
  io.cache.req.bits.currentLayer := io.tTable.req.bits.currentLayer
  io.cache.req.bits.regFileLocationBit := io.tTable.req.bits.regFileLocationBit

  // PE Table connections
  io.peTable.req.valid := false.B
  io.peTable.req.bits.cacheIndex := io.tTable.req.bits.cacheIndex
  io.peTable.req.bits.tIdx := io.tTable.req.bits.tableIndex
  io.peTable.req.bits.inAddr := io.tTable.req.bits.regFileAddrIn
  io.peTable.req.bits.outAddr := io.tTable.req.bits.regFileAddrOut +
    io.tTable.req.bits.currentNodeInLayer
  io.peTable.req.bits.neuronPointer := io.tTable.req.bits.neuronPointer +
    (io.tTable.req.bits.currentNodeInLayer << 3.U)
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
  override lazy val io = IO(new ControlInterfaceLearn)

  io.tTable.resp.bits.globalWtptr := io.cache.resp.bits.globalWtptr
  io.regFile.req.bits.totalWrites := io.cache.resp.bits.totalWritesMul *
    io.cache.resp.bits.data(0)

  io.peTable.req.bits.inAddr :=
    Mux((io.tTable.req.bits.stateLearn === e_TTABLE_STATE_LEARN_ERROR_BACKPROP) ||
    (io.tTable.req.bits.stateLearn === e_TTABLE_STATE_LEARN_WEIGHT_UPDATE),
    io.tTable.req.bits.regFileAddrIn + io.tTable.req.bits.currentNodeInLayer,
    io.tTable.req.bits.regFileAddrIn)
  io.peTable.req.bits.resetWB := io.tTable.req.bits.inLast &&
    (io.tTable.req.bits.currentNodeInLayer === 0.U) &&
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
    2.U, 1.U)
}
