// See LICENSE.BU for license details.
// See LICENSE.IBM for license details.

package dana

import chisel3._
import chisel3.util._
import rocket.{RoCCCommand, RoCCResponse, MStatus}
import cde._
import xfiles.{TransactionTableNumEntries, TableEntry, HasTable,
  XFilesResponseCodes, XFilesBackendReq, XFilesBackendResp,
  XFilesQueueInterface}
import _root_.util.ParameterizedBundle

class TransactionState(implicit p: Parameters) extends TableEntry()(p)
    with DanaParameters {
  //-------- Base class
  val validIO             = Bool()
  val cacheValid          = Bool()
  val waiting             = Bool()
  val needsLayerInfo      = Bool()
  val decInUse            = Bool()
  val needsAsidNnid       = Bool()
  val needsInputs         = Bool()
  // There are two "in the last layer" bits. The first, "inLast",
  // asserts when all PEs in the previous layer are done. The latter,
  // "inLastEarly", asserts as soon as all PEs in the previous layer
  // have been assigned.
  val inLast              = Bool()
  val inFirst             = Bool()
  val cacheIndex          = UInt(log2Up(cacheNumEntries).W)
  val nnid                = UInt(nnidWidth.W)
  val decimalPoint        = UInt(decimalPointWidth.W)
  val numLayers           = UInt(16.W) // [TODO] fragile
  val numNodes            = UInt(16.W) // [TODO] fragile
  val currentNode         = UInt(16.W) // [TODO] fragile
  val currentNodeInLayer  = UInt(16.W) // [TODO] fragile
  val currentLayer        = UInt(16.W) // [TODO] fragile
  val nodesInCurrentLayer = UInt(16.W) // [TODO] fragile
  val neuronPointer       = UInt(11.W) // [TODO] fragile
  val regFileLocationBit  = UInt(1.W)
  val regFileAddrIn       = UInt(log2Up(regFileNumElements).W)
  val regFileAddrOut      = UInt(log2Up(regFileNumElements).W)
  val readIdx             = UInt(log2Up(regFileNumElements).W)
  val indexElement        = UInt(log2Up(regFileNumElements).W)
  //-------- Can be possibly moved over to a learning-only config
  val regFileAddrOutFixed = UInt(log2Up(regFileNumElements).W)

  aliasList += ( "valid"  -> "V",
    "reserved"            -> "R",
    "cacheValid"          -> "C",
    "waiting"             -> "W",
    "needsLayerInfo"      -> "NLI",
    "done"                -> "D",
    "decInUse"            -> "-",
    "inLast"              -> "L?",
    "inFirst"             -> "F?",
    "cacheIndex"          -> "C#",
    "decimalPoint"        -> "DP",
    "numLayers"           -> "#L",
    "numNodes"            -> "#N",
    "currentNode"         -> "cN",
    "currentNodeInLayer"  -> "cNiL",
    "currentLayer"        -> "cL",
    "nodesInCurrentLayer" -> "#NcL",
    "neuronPointer"       -> "N*",
    "regFileLocationBit"  -> "LB",
    "regFileAddrIn"       -> "AIn",
    "regFileAddrOut"      -> "AOut",
    "readIdx"             -> "R#",
    "indexElement"        -> "#E",
    "regFileAddrOutFixed" -> "AOutF"
  )
  override def reset() {
    super.reset()
  }
  def reserve() {
    this.flags.valid         := false.B
    this.flags.reserved      := true.B
    this.flags.done          := false.B
    this.needsAsidNnid       := true.B
    this.needsInputs         := false.B
    this.indexElement        := 0.U
    this.validIO             := true.B
    this.cacheValid          := false.B
    this.waiting             := false.B
    this.needsLayerInfo      := true.B
    this.currentLayer        := 0.U
    this.decInUse            := false.B
    this.indexElement        := 0.U
    this.regFileAddrOut      := 0.U
    this.nodesInCurrentLayer := 0.U
    this.currentNode         := 0.U
    this.readIdx             := 0.U
    this.inFirst             := true.B
    this.inLast              := false.B
    this.needsLayerInfo      := true.B
    this.regFileLocationBit  := 0.U
  }
  def enable() {
    this.flags.valid         := true.B
    this.currentNode         := 0.U
    this.readIdx             := 0.U
    this.inFirst             := true.B
    this.inLast              := false.B
    this.needsLayerInfo      := true.B
    this.flags.done          := false.B
    this.waiting             := false.B
    this.regFileLocationBit  := 0.U
  }
  def cacheValid(resp: ControlResp) {
    val info = (new NnConfigHeader).fromBits(resp.data)

    this.cacheValid   := true.B
    this.numLayers    := info.totalLayers
    this.numNodes     := info.totalNeurons
    this.decimalPoint := info.decimalPoint
    this.cacheIndex   := resp.cacheIndex
    // Once we know the cache is valid, this entry is no longer waiting
    this.waiting := false.B
    printfInfo("DANA TTable: Updating global info from Cache...\n")
    printfInfo("  total layers:            0x%x\n", info.totalLayers)
    printfInfo("  total nodes:             0x%x\n", info.totalNeurons)
    printfInfo("  decimal point:           0x%x\n", info.decimalPoint)
    printfInfo("  cache index:             0x%x\n", resp.cacheIndex)
  }
  def newLayer(resp: ControlResp) {
    val info = (new NnConfigLayer).fromBits(resp.data)

    this.needsLayerInfo := false.B
    this.currentNodeInLayer := 0.U
    this.nodesInCurrentLayer := info.neuronsInLayer
    this.neuronPointer := info.neuronPointer

    // Once we have layer information, we can update the
    // previous and current layer addresses. These are adjusted
    // to be on block boundaries, so there's an optional round
    // term.
    val nicl = info.neuronsInLayer
    val niclMSBs = // Nodes in previous layer MSBs [TODO] fragile
      nicl(15, log2Up(elementsPerBlock)) ##
    0.U(log2Up(elementsPerBlock).W)
    val niclLSBs = // Nodes in previous layer LSBs
      nicl(log2Up(elementsPerBlock)-1, 0)
    val round = Mux(niclLSBs =/= 0.U, elementsPerBlock.U, 0.U)
    val niclOffset = niclMSBs + round

    val niplMSBs =
      this.nodesInCurrentLayer(15, log2Up(elementsPerBlock)) ##
    0.U(log2Up(elementsPerBlock).W)
    val niplLSBs = this.nodesInCurrentLayer(log2Up(elementsPerBlock)-1,0)
    val niplOffset = niplMSBs + Mux(niplLSBs =/= 0.U,
      elementsPerBlock.U, 0.U)

    printfInfo("DANA TTable: Updating cache layer...\n")
    printfInfo("  total layers:               0x%x\n",
      this.numLayers)
    printfInfo("  layer is:                   0x%x\n",
      this.currentLayer)
    printfInfo("  neuron pointer:             0x%x\n", info.neuronPointer)
    printfInfo("  nodes in current layer:     0x%x\n", info.neuronsInLayer)
    printfInfo("  nodes in previous layer:    0x%x\n", info.neuronsInPreviousLayer)
    printfInfo("  nicl:                       0x%x\n", nicl)
    printfInfo("  niclMSBs:                   0x%x\n", niclMSBs)
    printfInfo("  niclLSBs:                   0x%x\n", niclLSBs)
    printfInfo("  round:                      0x%x\n", round)
    printfInfo("  niplMSBs:                   0x%x\n", niplMSBs)
    printfInfo("  niplLSBs:                   0x%x\n", niplLSBs)
    printfInfo("  niplOffset:                 0x%x\n", niplOffset)
    printfInfo("  regFileAddrIn:              0x%x\n",
      this.regFileAddrOut)
    printfInfo("  regFileAddrOut:             0x%x\n",
      this.regFileAddrOut +  niplOffset)
  }
}

class TransactionStateLearn(implicit p: Parameters)
    extends TransactionState()(p) {
  // flags
  val needsOutputs         = Bool()
  //
  val globalWtptr          = UInt(16.W)           //[TODO] fragile
  val inLastEarly          = Bool()
  val transactionType      = UInt(log2Up(3).W)    // [TODO] fragile
  val numTrainOutputs      = UInt(16.W)           // [TODO] fragile
  val stateLearn           = UInt(log2Up(8).W)    // [TODO] fragile
  val errorFunction        = UInt(log2Up(2).W)    // [TODO] fragile
  val learningRate         = UInt(elementWidth.W)
  val weightDecay          = UInt(elementWidth.W)
  val numWeightBlocks      = UInt(16.W)           // [TODO] fragile
  val mse                  = UInt(elementWidth.W) // unused
  // Batch training information
  val numBatchItems        = UInt(16.W)           // [TODO] fragile
  val curBatchItem         = UInt(16.W)           // [TODO] fragile
  val biasAddr             = UInt(16.W)           // [TODO] fragile
  val offsetBias           = UInt(16.W)           // [TODO] fragile
  val offsetDW             = UInt(16.W)           // [TODO] fragile
  val numOutputs           = UInt(16.W)           // [TODO] fragile
  // We need to keep track of where inputs and outputs should be
  // written to in the Register File.
  val regFileAddrInFixed   = UInt(log2Up(regFileNumElements).W)
  // val regFileAddrDelta  = UInt(log2Up(regFileNumElements).W)
  val regFileAddrDW        = UInt(log2Up(regFileNumElements).W)
  val regFileAddrSlope     = UInt(log2Up(regFileNumElements).W)
  val regFileAddrAux       = UInt(log2Up(regFileNumElements).W)
  val nodesInPreviousLayer = UInt(16.W)           // [TODO] fragile
  val nodesInLast          = UInt(16.W)           // [TODO] fragile

  aliasList += (
    "globalWtptr"          -> "GW*",
    "inLastEarly"          -> "L?e",
    "transactionType"      -> "T?",
    "numTrainOutputs"      -> "#TO",
    "stateLearn"           -> "state",
    "errorFunction"        -> "ef",
    "learningRate"         -> "lr",
    "weightDecay"          -> "Y",
    "numWeightBlocks"      -> "#WB",
    "numBatchItems"        -> "#BI",
    "curBatchItem"         -> "cB",
    "biasAddr"             -> "AB",
    "offsetBias"           -> "oB",
    "offsetDW"             -> "oDW",
    "regFileAddrInFixed"   -> "AInF",
    "regFileAddrDW"        -> "ADW",
    "regFileAddrSlope"     -> "AS",
    "regFileAddrAux"       -> "AAux",
    "nodesInPreviousLayer" -> "nipl",
    "nodesInLast"          -> "nil"
  )

  override def reserve() {
    super.reserve()
    this.needsOutputs       := false.B
    this.inLastEarly        := false.B
    this.regFileAddrIn      := 0.U
    this.regFileAddrDW      := 0.U
    this.regFileAddrSlope   := 0.U
    this.offsetBias         := 0.U
    this.offsetDW           := 0.U
    this.regFileAddrInFixed := 0.U
    // [TODO] Temporary value for number of batch items
    this.numBatchItems      := 1.U
    this.curBatchItem       := 0.U
  }

  override def enable() {
    super.enable()
    this.inLastEarly        := false.B
    this.regFileAddrIn      := 0.U
    this.regFileAddrDW      := 0.U
    this.regFileAddrSlope   := 0.U
    this.offsetBias         := 0.U
    this.offsetDW           := 0.U
  }

  override def cacheValid(resp: ControlResp) {
    super.cacheValid(resp)
    val info = (new NnConfigHeader).fromBits(resp.data)

    this.globalWtptr := info.weightsPointer
    this.errorFunction := info.errorFunction
    this.numWeightBlocks := info.totalWeightBlocks
    printfInfo("  global weight pointer:   0x%x\n", info.weightsPointer)
    printfInfo("  error function:          0x%x\n", info.errorFunction)
    printfInfo("  total weight blocsk:     0x%x\n", info.totalWeightBlocks)
  }
}

class ControlReq(implicit p: Parameters) extends DanaBundle()(p) {
  // Bools
  val cacheValid         = Bool()
  val waiting            = Bool()
  val needsLayerInfo     = Bool()
  val isDone             = Bool()
  val inFirst            = Bool()
  val inLast             = Bool()
  // Global info
  val tableIndex         = UInt(log2Up(transactionTableNumEntries).W)
  val cacheIndex         = UInt(log2Up(cacheNumEntries).W)
  val asid               = UInt(asidWidth.W)
  val nnid               = UInt(nnidWidth.W) // formerly nn_hash
  // State info
  val currentNodeInLayer = UInt(16.W) // [TODO] fragile
  val currentLayer       = UInt(16.W) // [TODO] fragile
  val neuronPointer      = UInt(log2Up(elementWidth * elementsPerBlock * cacheNumBlocks).W)
  val decimalPoint       = UInt(decimalPointWidth.W)
  val regFileAddrIn      = UInt(log2Up(regFileNumElements).W)
  val regFileAddrOut     = UInt(log2Up(regFileNumElements).W)
  val regFileLocationBit = UInt(1.W) // [TODO] fragile on definition above
}

class ControlReqLearn(implicit p: Parameters) extends ControlReq()(p) {
  val globalWtptr         = UInt(16.W) // [TODO] fragile
  val inLastEarly         = Bool()
  val transactionType     = UInt(log2Up(3).W) // [TODO] fragile
  val stateLearn          = UInt(log2Up(8).W) // [TODO] fragile
  val errorFunction       = UInt(log2Up(2).W) // [TODO] fragile
  val learningRate        = UInt(elementWidth.W)
  val weightDecay         = UInt(elementWidth.W)
  val numWeightBlocks     = UInt(16.W) // [TODO] fragile
  // val regFileAddrDelta = UInt(log2Up(regFileNumElements).W)
  val regFileAddrDW       = UInt(log2Up(regFileNumElements).W)
  val regFileAddrSlope    = UInt(log2Up(regFileNumElements).W)
  val regFileAddrBias     = UInt(log2Up(regFileNumElements).W)
  val regFileAddrAux      = UInt(log2Up(regFileNumElements).W)
  val batchFirst          = Bool()
}

class ControlResp(implicit p: Parameters) extends DanaBundle()(p) {
  val readyCache      = Bool()
  val readyPeTable    = Bool()
  val cacheValid      = Bool()
  val cacheIndex      = UInt(log2Up(cacheNumEntries).W)
  val tableIndex      = UInt(log2Up(transactionTableNumEntries).W)
  val tableMask       = UInt(transactionTableNumEntries.W)
  val field           = UInt(4.W) // [TODO] fragile on Constants.scala
  val data            = UInt((new NnConfigHeader).getWidth.W)
  val layerValid      = Bool()
  val layerValidIndex = UInt(log2Up(transactionTableNumEntries).W)
}

class TTableControlInterface(implicit val p: Parameters)
    extends ParameterizedBundle()(p) {
  lazy val req = Decoupled(new ControlReq)
  lazy val resp = Flipped(Decoupled(new ControlResp))
}

class TTableControlInterfaceLearn(implicit p: Parameters)
    extends TTableControlInterface()(p) {
  override lazy val req = Decoupled(new ControlReqLearn)
}

class TTableRegisterFileReq(implicit p: Parameters) extends DanaBundle()(p) {
  val reqType = UInt(log2Up(2).W) // [TODO] Frgaile on Dana enum
  val tidIdx  = UInt(log2Up(transactionTableNumEntries).W)
  val addr    = UInt(log2Up(regFileNumElements).W)
  val data    = UInt(elementWidth.W)
}

class TTableRegisterFileResp(implicit p: Parameters) extends DanaBundle()(p) {
  val data = UInt(elementWidth.W)
}

class TTableRegisterFileInterface(implicit p: Parameters) extends DanaBundle()(p) {
  val req = Valid(new TTableRegisterFileReq)
  val resp = Flipped(Valid(new TTableRegisterFileResp))
}

class TTableArbiter(implicit p: Parameters) extends DanaBundle()(p) {
  val rocc = new Bundle {
    val cmd = Flipped(Decoupled(new RoCCCommand))
    val resp = Decoupled(new RoCCResponse)
    val status = Input(new MStatus())
  }
  val xfReq = Flipped(new XFilesBackendReq)
  val xfResp = new XFilesBackendResp
  val xfQueue = new XFilesQueueInterface
}

class DanaTransactionTableInterface(implicit p: Parameters) extends DanaStatusIO()(p) {
  val arbiter = new TTableArbiter
  lazy val control = new TTableControlInterface
  val regFile = new TTableRegisterFileInterface
}

class DanaTransactionTableInterfaceLearn(implicit p: Parameters)
    extends DanaTransactionTableInterface()(p) {
  override lazy val control = new TTableControlInterfaceLearn
}

class DanaTransactionTableBase[StateType <: TransactionState,
  ControlReqType <: ControlReq](
  genStateVec: => Vec[StateType], genControlReq: => ControlReqType)(
  implicit p: Parameters) extends DanaModule()(p) with HasTable
    with XFilesResponseCodes {
  lazy val io = IO(new DanaTransactionTableInterface)
  override val printfSigil = "dana.TTable: "

  // Create the actual Transaction Table
  val table = Reg(genStateVec)

  // Temporary signal tie-offs
  io.arbiter.xfReq.tidx.ready := true.B
  io.arbiter.xfResp.tidx.valid := false.B
  io.arbiter.xfQueue.in.ready := false.B
  io.arbiter.xfQueue.out.valid := false.B

  // Control is broken up into X-FILES orchestrated updates and
  // independent actions
  when (io.arbiter.xfReq.tidx.fire()) {
    val entry = table(io.arbiter.xfReq.tidx.bits)
    entry.validIO := true.B
    when (!entry.flags.reserved) {
      entry.reserve()
    }
  }


  // Determine if there exits a free entry in the table and the index
  // of the next availble free entry
  io.arbiter.rocc.cmd.ready := true.B
  io.arbiter.rocc.resp.bits.rd := 0.U
  io.arbiter.rocc.resp.bits.data := 0.U
  // Default register file connections
  io.regFile.req.valid := false.B
  io.regFile.req.bits.reqType := 0.U
  io.regFile.req.bits.tidIdx := 0.U
  io.regFile.req.bits.addr := 0.U
  io.regFile.req.bits.data := 0.U

  io.arbiter.rocc.resp.valid := false.B

  val roccCmd = io.arbiter.rocc.cmd
  val newRoccCmd = roccCmd.fire() && !io.arbiter.rocc.status.prv.orR
  val regWrite = newRoccCmd & roccCmd.bits.inst.funct === t_USR_WRITE_REGISTER.U

  // Update the table when we get a request from DANA
  when (io.control.resp.valid) {
    val resp = io.control.resp.bits
    val tIdx = resp.tableIndex
    // table(tIdx).waiting := true.B
    when (resp.cacheValid) {
      switch(resp.field) {
        is(e_TTABLE_CACHE_VALID) { table.zipWithIndex.map({case(t, i) =>
          when (resp.tableMask(i)) { table(i).cacheValid(resp) }})}
        is(e_TTABLE_LAYER)       { table(tIdx).newLayer(resp)   }
      }
    }
    // If the register file has all valid entries, then this specific
    // entry should stop waiting. Note, that this logic will correctly
    // overwrite that of the e_TTABLE_LAYER.
    when (resp.layerValid) {
      val tIdx = resp.layerValidIndex
      val inLastNew = table(tIdx).currentLayer === (table(tIdx).numLayers - 1.U)
      table(tIdx).inLast := inLastNew
      printfInfo("RegFile has all outputs of tIdx 0x%x\n",
        resp.layerValidIndex)
    }
  }

  val readyCache = Reg(next = io.control.resp.bits.readyCache)
  val readyPeTable = io.control.resp.bits.readyPeTable
  // Round Robin Arbitration of Transaction Table entries. One of
  // these is passed out over an interface to DANA's control module.
  val entryArbiter = Module(new RRArbiter(genControlReq,
    transactionTableNumEntries))
  for (i <- 0 until transactionTableNumEntries) {
    val isValid = table(i).flags.valid
    val isNotWaiting = !table(i).waiting
    val cacheWorkToDo = (table(i).decInUse || !table(i).cacheValid ||
      table(i).needsLayerInfo)
    val peWorkToDo = (table(i).currentNode =/= table(i).numNodes)
    // The entryArbiter has a valid request if that TTable entry is
    // valid, it is not waiting, a request was not generated last
    // cycle, and either there is cache or PE table work to do and the
    // backend can support one of these.
    entryArbiter.io.in(i).valid := isValid && isNotWaiting &&
      ((readyCache && cacheWorkToDo) || (readyPeTable && peWorkToDo))
    // All connections here are explicit as these are not bundles of
    // the same type
    entryArbiter.io.in(i).bits.cacheValid := table(i).cacheValid
    entryArbiter.io.in(i).bits.waiting := table(i).waiting
    entryArbiter.io.in(i).bits.needsLayerInfo := table(i).needsLayerInfo
    entryArbiter.io.in(i).bits.isDone := table(i).decInUse // TODO: Different
    entryArbiter.io.in(i).bits.inFirst := table(i).inFirst
    entryArbiter.io.in(i).bits.inLast := table(i).inLast
    entryArbiter.io.in(i).bits.tableIndex := i.U // TODO: Different
    entryArbiter.io.in(i).bits.cacheIndex := table(i).cacheIndex
    entryArbiter.io.in(i).bits.asid := table(i).asid
    entryArbiter.io.in(i).bits.nnid := table(i).nnid
    entryArbiter.io.in(i).bits.currentNodeInLayer := table(i).currentNodeInLayer
    entryArbiter.io.in(i).bits.currentLayer := table(i).currentLayer
    entryArbiter.io.in(i).bits.neuronPointer := table(i).neuronPointer
    entryArbiter.io.in(i).bits.decimalPoint := table(i).decimalPoint
    entryArbiter.io.in(i).bits.regFileAddrIn := table(i).regFileAddrIn
    entryArbiter.io.in(i).bits.regFileAddrOut := table(i).regFileAddrOut
    entryArbiter.io.in(i).bits.regFileLocationBit := table(i).regFileLocationBit
  }

  // Input/Output arbitration happens separately from arbitration
  // related to communication with the Cache and the PEs. Note that
  // this UInt is technically not used. The RRArbiter is just used to
  // get an index. [TODO] Perhaps a better solution could be achieved
  // here?
  val ioArbiter = Module(new RRArbiter(Bool(), transactionTableNumEntries)).io

  (0 until transactionTableNumEntries).map(i => {
    val entry = table(i)
    val flags = entry.flags
    ioArbiter.in(i).valid := flags.reserved & entry.validIO & (
      entry.needsAsidNnid | entry.needsInputs)
  })
  io.arbiter.xfQueue.tidxIn := ioArbiter.chosen
  io.arbiter.xfResp.tidx.bits := ioArbiter.chosen
  io.arbiter.xfResp.flags.reset("vdio")

  val queueOutTidx_d = Reg(Valid(UInt(log2Up(transactionTableNumEntries).W)))
  queueOutTidx_d.valid := false.B
  queueOutTidx_d.bits := ioArbiter.chosen
  when (ioArbiter.out.valid) {
    val tidIdx = ioArbiter.chosen
    val entry = table(tidIdx)
    when (entry.needsAsidNnid) {
      when (!io.arbiter.xfQueue.in.valid) {
        io.arbiter.xfResp.tidx.valid := true.B
        io.arbiter.xfResp.flags.set("vi")
        entry.validIO := false.B
        printfInfo("Entry 0d%d needs INFO, but In Queue not ready\n",
          ioArbiter.chosen)
      } .otherwise {
        // io.arbiter.xfResp.tidx.valid := true.B
        // io.arbiter.xfResp.flags.set("v")
        io.arbiter.xfQueue.in.ready := true.B
        val asid = io.arbiter.xfQueue.in.bits.rs1(asidWidth + tidWidth - 1, tidWidth)
        val tid = io.arbiter.xfQueue.in.bits.rs1(tidWidth - 1, 0)
        val nnid = io.arbiter.xfQueue.in.bits.rs2(nnidWidth - 1, 0)
        entry.asid := asid
        entry.tid := tid
        entry.nnid := nnid
        entry.needsAsidNnid := false.B
        printfInfo("T0d%d got (ASID:0x%x/TID:0x%x/NNID:0x%x) from queue\n",
          ioArbiter.chosen, asid, tid, nnid)
        entry.needsInputs := true.B
      }
    }

    when (entry.needsInputs) {
      when (!io.arbiter.xfQueue.in.valid) {
        io.arbiter.xfResp.tidx.valid := true.B
        io.arbiter.xfResp.flags.set("vi")
        entry.validIO := false.B
        printfInfo("Entry 0d%d needs INPUTS, but In Queue not ready\n",
          ioArbiter.chosen)
      } .otherwise {
        // io.arbiter.xfResp.tidx.valid := true.B
        // io.arbiter.xfResp.flags.set("v")
        io.arbiter.xfQueue.in.ready := true.B
        io.regFile.req.valid := true.B
        io.regFile.req.bits.reqType := e_TTABLE_REGFILE_WRITE
        io.regFile.req.bits.tidIdx := tidIdx
        io.regFile.req.bits.addr := entry.indexElement
        val data = io.arbiter.xfQueue.in.bits.rs2
        io.regFile.req.bits.data := data
        printfInfo("T0d%d got (INPUT:0x%x) from queue\n",
          ioArbiter.chosen, data)

        val isLast = io.arbiter.xfQueue.in.bits.funct === t_USR_WRITE_DATA_LAST.U
        when (isLast) {
          val nextIndexBlock = (table(tidIdx).indexElement(
            log2Up(regFileNumElements)-1,log2Up(elementsPerBlock)) ##
            0.U(log2Up(elementsPerBlock).W)) + elementsPerBlock.U
          entry.indexElement := nextIndexBlock
          entry.needsInputs := false.B
          entry.enable()
        } .otherwise {
          entry.indexElement := entry.indexElement + 1.U
        }
      }
    }

    // [TODO] This needs a response pipe..
    when (entry.flags.done) {
      when (!io.arbiter.xfQueue.out.ready) {
        io.arbiter.xfResp.tidx.valid := true.B
        io.arbiter.xfResp.flags.set("vo")
        entry.validIO := false.B
        printfInfo("Entry 0d%d has OUTPUTS, but Out Queue not ready\n",
          ioArbiter.chosen)
      } .otherwise {
        // [TODO] Kludge to slow down the output rate so that we don't
        // overwrite the FIFO.
        io.regFile.req.valid := true.B
        io.regFile.req.bits.reqType := e_TTABLE_REGFILE_READ
        io.regFile.req.bits.tidIdx := tidIdx
        io.regFile.req.bits.addr := entry.readIdx + entry.regFileAddrOutFixed

        entry.readIdx := entry.readIdx + 1.U

        queueOutTidx_d.valid := true.B

        printfInfo("Req output 0x%x from Reg File sent\n", entry.readIdx)
      }
    }
  }

  io.arbiter.xfQueue.out.valid := queueOutTidx_d.valid
  io.arbiter.xfQueue.out.bits := io.regFile.resp.bits.data
  io.arbiter.xfQueue.tidxOut := queueOutTidx_d.bits

  io.control.req <> entryArbiter.io.out

  // Do a special table update if the arbiter is allowing a request to
  // go through. This decreases the necessary bandwidth between the
  // Control module and the Transaction Table as the Control module
  // doesn't have to generate responses. [TODO] This is somewhat kludgy
  // and may result in excess combinational logic depth. It may be
  // necessary to pipeline this.
  val isCacheReq = entryArbiter.io.out.valid &&
    (!entryArbiter.io.out.bits.cacheValid ||
      entryArbiter.io.out.bits.needsLayerInfo ||
      entryArbiter.io.out.bits.isDone)
  val isPeReq = entryArbiter.io.out.valid &&
    (entryArbiter.io.out.bits.cacheValid &&
      !entryArbiter.io.out.bits.needsLayerInfo) &&
    !entryArbiter.io.out.bits.isDone
  // If this is a transition into a layer which is not the first
  // layer, then the Transaction Table requests need to block
  // until the Register File has all valid data. [TODO] This is
  // a sub-optimal design choice as PEs should be allowed to
  // start before the Register File has _all_ of its valid data,
  // but I'm leaving this the way it is due to the lack of a
  // non-trivial path to add this functionality.
  when (isCacheReq) {
    val tIdx = entryArbiter.io.out.bits.tableIndex
    table(tIdx).waiting := true.B
    when (entryArbiter.io.out.bits.isDone) {
      printfInfo("entry for ASID/TID %x/%x is done\n",
        table(tIdx).asid, table(tIdx).tid)
      table(tIdx).flags.done := true.B }}
  when (isPeReq) {
    val tIdx = entryArbiter.io.out.bits.tableIndex
    val inLastNode = table(tIdx).currentNodeInLayer ===
      table(tIdx).nodesInCurrentLayer - 1.U
    val notInLastLayer = table(tIdx).currentLayer <
      (table(tIdx).numLayers - 1.U)
    table(tIdx).currentNode := table(tIdx).currentNode + 1.U
    table(tIdx).currentNodeInLayer := table(tIdx).currentNodeInLayer + 1.U }

  // Dump table information
  when (isPeReq || io.control.resp.valid) {
    info(table, "ttable,") }

  // Reset Condition
  when (reset) {(0 until transactionTableNumEntries).map(i => table(i).reset)}

  // Assertions
  // Valid should never be true if reserved is not true
  for (i <- 0 until transactionTableNumEntries)
    assert(!table(i).flags.valid || table(i).flags.reserved,
      "Valid asserted with reserved de-asserted on TTable " + i)

  // A Control response should never have a cacheValid or layerValid
  // asserted when the decoupled valid is deasserted
  assert(!(!io.control.resp.valid &&
    (io.control.resp.bits.cacheValid || io.control.resp.bits.layerValid)),
    "DANA TTable control response deasserted, but cacheValid or layerValid asserted")

  // The current node should never be greater than the total number of nodes
  assert(!Vec((0 until transactionTableNumEntries).map(i =>
    table(i).flags.valid && (table(i).currentNode >
      table(i).numNodes))).contains(true.B),
    "A TTable entry has a currentNode count greater than the total numNodes")

  // Don't send a response to the core unless it's ready
  assert(!(io.arbiter.rocc.resp.valid && !io.arbiter.rocc.resp.ready),
    "DANA TTable tried to send a valid response when core was not ready")

  // No writes should show up if the transaction is already valid
  // assert(!(newRoccCmd && cmd.readOrWrite && table(derefTidIndex).flags.valid),
  //   "DANA TTable saw write requests on valid TID")

  // Temporary printfs and assertions
  when (io.arbiter.xfReq.tidx.fire()) {
    val idx = io.arbiter.xfReq.tidx.bits
    printfInfo("XF scheduled tidx 0d%d (ASID:0x%x/TID:0x%x)\n",
      idx, table(idx).asid, table(idx).tid)
  }

  when (io.arbiter.xfResp.tidx.fire()) {
    val flags = io.arbiter.xfResp.flags
    when (flags.input | flags.output) {
      printfInfo("Deschedule T0d%d with flags VDIO/%b%b%b%b\n",
        io.arbiter.xfResp.tidx.bits, flags.valid, flags.done, flags.input,
        flags.output)
    } .otherwise {
      printfInfo("Reschedule T0d%d with flags VDIO/%b%b%b%b\n",
        io.arbiter.xfResp.tidx.bits, flags.valid, flags.done, flags.input,
        flags.output)
    }
  }
}

class DanaTransactionTable(implicit p: Parameters)
    extends DanaTransactionTableBase[TransactionState, ControlReq](
  Vec(p(TransactionTableNumEntries), new TransactionState),
      new ControlReq)(p) {

  // The finished condition (when the transaction becomes unreserved),
  // occurs differently for the non-learning and learning variants
  when (ioArbiter.out.valid) {
    val entry = table(ioArbiter.chosen)
    val finished = io.arbiter.xfQueue.out.ready &
      (entry.readIdx === entry.nodesInCurrentLayer - 1.U)
    when (finished) {
      io.arbiter.xfResp.tidx.valid := true.B
      io.arbiter.xfResp.flags.set("d")
      entry.flags.valid := false.B
      entry.flags.reserved := false.B
      entry.flags.done := false.B
    }
  }

  when (io.control.resp.valid) {
    val resp = io.control.resp.bits
    val tIdx = resp.tableIndex
    when (resp.cacheValid) {
      switch (resp.field) {
        is (e_TTABLE_LAYER) {
          val info = (new NnConfigLayer).fromBits(resp.data)

          val nicl = info.neuronsInLayer
          val niclMSBs = // Nodes in previous layer MSBs [TODO] fragile
            nicl(15, log2Up(elementsPerBlock)) ##
          0.U(log2Up(elementsPerBlock).W)
          val niclLSBs = // Nodes in previous layer LSBs
            nicl(log2Up(elementsPerBlock)-1, 0)
          val round = Mux(niclLSBs =/= 0.U, elementsPerBlock.U, 0.U)
          val niclOffset = niclMSBs + round

          val niplMSBs =
            table(tIdx).nodesInCurrentLayer(15, log2Up(elementsPerBlock)) ##
          0.U(width=log2Up(elementsPerBlock).W)
          val niplLSBs = table(tIdx).nodesInCurrentLayer(log2Up(elementsPerBlock)-1,0)
          val niplOffset = niplMSBs + Mux(niplLSBs =/= 0.U,
            elementsPerBlock.U, 0.U)
          table(tIdx).waiting := false.B
          table(tIdx).regFileAddrIn := table(tIdx).regFileAddrOut
          table(tIdx).regFileAddrOut := table(tIdx).regFileAddrOut + niplOffset
          table(tIdx).regFileAddrOutFixed := table(tIdx).regFileAddrOut +
            niplOffset
          printfInfo("  inFirst/inLast: 0x%x/0x%x\n", table(tIdx).inFirst,
            table(tIdx).inLast)
        }
      }
    }
    when (io.control.resp.bits.layerValid) {
      val tIdx = io.control.resp.bits.layerValidIndex
      val inLastOld = table(tIdx).inLast
      val inLastNew = table(tIdx).currentLayer === (table(tIdx).numLayers - 1.U)
      val inFirstNew = table(tIdx).currentLayer === 0.U
      table(tIdx).inFirst := inFirstNew
      table(tIdx).waiting := false.B
      when (inLastOld) {
        table(tIdx).decInUse := true.B }
      printfInfo("  inFirst/inLast: %x->%x/%x->%x\n", table(tIdx).inFirst,
        inFirstNew, inLastOld, inLastNew)
    }
  }

  when (isPeReq) {
    val tIdx = entryArbiter.io.out.bits.tableIndex
    val inLastNode = table(tIdx).currentNodeInLayer ===
      table(tIdx).nodesInCurrentLayer - 1.U
    val notInLastLayer = table(tIdx).currentLayer <
      (table(tIdx).numLayers - 1.U)
    when(inLastNode && notInLastLayer) {
      table(tIdx).needsLayerInfo := true.B
      table(tIdx).currentLayer := table(tIdx).currentLayer + 1.U
      table(tIdx).regFileLocationBit := !table(tIdx).regFileLocationBit
    } .otherwise {
      table(tIdx).needsLayerInfo := false.B
      table(tIdx).currentLayer := table(tIdx).currentLayer
    }
  }

}

class DanaTransactionTableLearn(implicit p: Parameters)
    extends DanaTransactionTableBase[TransactionStateLearn, ControlReqLearn](
  Vec(p(TransactionTableNumEntries), new TransactionStateLearn),
    new ControlReqLearn)(p) {
  override lazy val io = IO(new DanaTransactionTableInterfaceLearn)

  when (io.arbiter.xfReq.tidx.fire()) {
    val tidx = io.arbiter.xfReq.tidx.bits
    when (!table(tidx).flags.reserved) {
      table(tidx).stateLearn := e_TTABLE_STATE_READ_INFO
    }
  }

  (0 until transactionTableNumEntries).map(i => {
    val entry = table(i)
    val flags = entry.flags
    ioArbiter.in(i).valid := flags.reserved & entry.validIO & (
      entry.needsAsidNnid | entry.needsOutputs | entry.needsInputs |
        entry.flags.done)
  })

  when (regWrite) {
    val cmd = (new UsrCmdRegWrite).fromBits(io.arbiter.rocc.cmd.bits.rs2 ##
      io.arbiter.rocc.cmd.bits.rs1)
    val derefTidIndex = table.indexWhere(findAsidTid(_: TransactionStateLearn,
      cmd.asid, cmd.tid))
    val e = table(derefTidIndex)
    switch(cmd.regId) {
      is (e_TTABLE_WRITE_REG_BATCH_ITEMS) { e.numBatchItems := cmd.regValue }
    }
    assert(!(regWrite && cmd.regId =/= e_TTABLE_WRITE_REG_BATCH_ITEMS),
      "Deprecated regWrite register\n")
    printfInfo("saw reg write TID/Reg/Value 0x%x/0x%x/0x%x\n",
      cmd.tid, cmd.regId, cmd.regValue)
  }

  // IO Arbiter
  when (ioArbiter.out.valid) {
    val entry = table(ioArbiter.chosen)
    // As this extends the base Transaction Table, we need to overwite
    // the logic of how the needsAsidnnid -> needsInputs logic is
    // handled. If we're dealing with a learning transaction, then we
    // need to assert needsOutputs
    val transactionType = io.arbiter.xfQueue.in.bits.rs2(49,48)
    when (entry.needsAsidNnid & io.arbiter.xfQueue.in.valid) {
      entry.transactionType := transactionType
      when (transactionType =/= e_TTYPE_FEEDFORWARD) {
        entry.needsInputs := false.B
        entry.needsOutputs := true.B
      }
      printfInfo("    (transactionType:0x%x)\n",
        transactionType)
    }

    // The learning variant needs to set certain fields when it exits
    // the "needsInputs" state
    val isLast = io.arbiter.xfQueue.in.bits.funct === t_USR_WRITE_DATA_LAST.U
    val numInputs = entry.indexElement
    val numInputsMSBs = numInputs(log2Up(regFileNumElements) - 1,
      log2Up(elementsPerBlock)) ## 0.U(log2Up(elementsPerBlock).W)
    val numInputsOffset = numInputsMSBs + elementsPerBlock.U
    when (entry.needsInputs & io.arbiter.xfQueue.in.valid & isLast) {
      entry.stateLearn := e_TTABLE_STATE_FEEDFORWARD
      when (entry.transactionType =/= e_TTYPE_FEEDFORWARD) {
        entry.stateLearn := e_TTABLE_STATE_LEARN_FEEDFORWARD
        entry.nodesInCurrentLayer := numInputsOffset - entry.regFileAddrInFixed
      }
    }

    when (entry.needsOutputs) {
      val tidIdx = io.arbiter.xfResp.tidx.bits
      when (!io.arbiter.xfQueue.in.valid) {
        io.arbiter.xfResp.tidx.valid := true.B
        io.arbiter.xfResp.flags.set("vi")
        entry.validIO := false.B
        printfInfo("Entry 0d%d needs OUTPUTS, but In Queue not ready\n",
          ioArbiter.chosen)
      } .otherwise {
        // io.arbiter.xfResp.tidx.valid := true.B
        // io.arbiter.xfResp.flags.set("v")
        io.arbiter.xfQueue.in.ready := true.B
        io.regFile.req.valid := true.B
        io.regFile.req.bits.reqType := e_TTABLE_REGFILE_WRITE
        io.regFile.req.bits.tidIdx := tidIdx
        io.regFile.req.bits.addr := entry.indexElement
        val data = io.arbiter.xfQueue.in.bits.rs2
        io.regFile.req.bits.data := data
        printfInfo("T0d%d got (E[OUTPUT]:0x%x) from queue\n",
          ioArbiter.chosen, data)

        when (isLast) {
          val nextIndexBlock = (entry.indexElement(
            log2Up(regFileNumElements)-1,log2Up(elementsPerBlock)) ##
            0.U(log2Up(elementsPerBlock).W)) + elementsPerBlock.U
          entry.indexElement := nextIndexBlock
          entry.needsInputs := true.B
          entry.needsOutputs := false.B
          entry.regFileAddrInFixed := nextIndexBlock
          entry.regFileAddrOut := nextIndexBlock
          entry.numOutputs := entry.indexElement + 1.U
          printfInfo("Saving numOutputs 0d%d\n", entry.indexElement)
        } .otherwise {
          entry.indexElement := entry.indexElement + 1.U
        }
      }
    }

    when (entry.flags.done) {
      val entry = table(ioArbiter.chosen)
      val finished = io.arbiter.xfQueue.out.ready & Mux(
        entry.transactionType === e_TTYPE_FEEDFORWARD,
        entry.readIdx === (entry.nodesInCurrentLayer - 1.U),
        entry.readIdx === (entry.numOutputs - 1.U))
      when (finished & (entry.stateLearn =/= e_TTABLE_STATE_LOAD_OUTPUTS)) {
        // This is an "I'm done" response to XF which indicates that all
        // outputs have been read
        io.arbiter.xfResp.tidx.valid := true.B
        io.arbiter.xfResp.flags.set("d")
        entry.flags.valid := false.B
        entry.flags.reserved := false.B
        entry.flags.done := false.B
      }

      when (finished & entry.stateLearn === e_TTABLE_STATE_LOAD_OUTPUTS) {
        entry.flags.valid := false.B
        entry.flags.done := false.B
        entry.needsOutputs := true.B
        printfInfo("Learn TX batch done\n")
      }

      printfInfo("entry.flags.done asserted\n")
      printfInfo("  finished: %d\n", finished)
      printfInfo("  entry.readIdx: %d\n", entry.readIdx)
      printfInfo("  entry.numOutputs: %d\n", entry.numOutputs)
    }
  }

  // Update the table when we get a request from DANA
  val cacheValid = io.control.resp.valid && io.control.resp.bits.cacheValid
  val layerValid = io.control.resp.valid && io.control.resp.bits.layerValid

  when (cacheValid) {
    val resp = io.control.resp.bits
    val tIdx = resp.tableIndex
    val t = table(tIdx)
    switch(resp.field) {
      is(e_TTABLE_CACHE_VALID) {
        val info = (new NnConfigHeader).fromBits(resp.data)

        // t.globalWtptr := io.control.resp.bits.globalWtptr
        when (t.transactionType === e_TTYPE_BATCH) {
          t.numNodes := info.totalNeurons * 2.U
        }
        val learningRate = io.status.learn_rate >> (
          decimalPointOffset.U - info.decimalPoint)
        val weightDecay = io.status.weight_decay >> (
          decimalPointOffset.U - info.decimalPoint)
        t.learningRate := learningRate
        t.weightDecay := weightDecay

        printfInfo("  learning rate:           0x%x \n", learningRate)
        printfInfo("  weight decay:            0x%x \n", weightDecay)
      }
      is(e_TTABLE_LAYER) {
        val info = (new NnConfigLayer).fromBits(resp.data)

        val nicl = info.neuronsInLayer
        val niclMSBs = // Nodes in previous layer MSBs [TODO] fragile
          nicl(15, log2Up(elementsPerBlock)) ##
        0.U(log2Up(elementsPerBlock).W)
        val niclLSBs = // Nodes in previous layer LSBs
          nicl(log2Up(elementsPerBlock)-1, 0)
        val round = Mux(niclLSBs =/= 0.U, elementsPerBlock.U, 0.U)
        val niclOffset = niclMSBs + round

        val nipl = info.neuronsInPreviousLayer
        val niplMSBs = nipl(15, log2Up(elementsPerBlock)) ##
        0.U(log2Up(elementsPerBlock).W)
        val niplLSBs = nipl(log2Up(elementsPerBlock)-1,0)
        val niplOffset = niplMSBs + Mux(niplLSBs =/= 0.U,
          elementsPerBlock.U, 0.U)

        printfInfo("  nicl:             0x%x\n", nicl)
        printfInfo("  niclOffset:       0x%x\n", niclOffset)
        printfInfo("  nipl:             0x%x\n", nipl)
        printfInfo("  niplOffset:       0x%x\n", niplOffset)
        when ((t.currentLayer === 0.U) &&
          (t.stateLearn === e_TTABLE_STATE_LEARN_FEEDFORWARD ||
            t.stateLearn === e_TTABLE_STATE_FEEDFORWARD)) {
          t.waiting := false.B
        }
        switch(t.stateLearn) {
          is(e_TTABLE_STATE_FEEDFORWARD){
            t.regFileAddrIn := t.regFileAddrOut
            t.regFileAddrOut := t.regFileAddrOut +
            niplOffset
            t.regFileAddrOutFixed :=
            t.regFileAddrOut + niplOffset
            // nodesInLast can be blindly set during non-learning
            // feedforward mode
            t.nodesInLast := nicl
          }
          is(e_TTABLE_STATE_LEARN_FEEDFORWARD){
            val regFileAddrOut = t.regFileAddrOut + niplOffset
            t.regFileAddrIn := t.regFileAddrOut
            t.regFileAddrOut := regFileAddrOut
            t.regFileAddrOutFixed := regFileAddrOut
            t.regFileAddrDW := regFileAddrOut + niclOffset

            // Update the number of total nodes in the network
            when (t.currentLayer === 0.U) { // In first layer
              t.numNodes := t.numNodes + nicl
            } .elsewhen (t.inLastEarly) {        // in the last layer
              t.numNodes := t.numNodes + nicl
            } .otherwise {                                // not first or last
              t.numNodes := t.numNodes + nicl * 2.U
            }

            // The bias offset is the size of the bias region
            val offsetBias = t.offsetBias + niclOffset
            t.offsetBias := offsetBias
            val biasAddr = regFileAddrOut + t.offsetBias +
            t.offsetDW + niclOffset
            t.biasAddr := biasAddr
            t.regFileAddrSlope := biasAddr + niclOffset
            printfInfo("  offsetBias:       0x%x\n",
              t.offsetBias + niclOffset)
            // The DW offset is the size of the DW region
            when (!t.inLastEarly) {
              t.offsetDW := t.offsetDW + niclOffset
              printfInfo("  offsetDW:         0x%x\n",
                t.offsetDW + niclOffset)
            }

            // Store the number of nodes in the output layer for future use
            when (t.inLastEarly) {
              t.nodesInLast := nicl
            }

            printfInfo("  offsetDW:         0x%x\n", t.offsetDW)
            printfInfo("  regFileAddrDw:    0x%x -> 0x%x\n",
              t.regFileAddrDW, regFileAddrOut + niclOffset)
            printfInfo("  regFileAddrSlope: 0x%x\n", biasAddr + niclOffset)
            printfInfo("  biasAddr:         0x%x\n", biasAddr)
          }
          is(e_TTABLE_STATE_LEARN_ERROR_BACKPROP){
            t.regFileAddrOut := t.regFileAddrDW
            t.regFileAddrDW := t.regFileAddrDW +
            niclOffset

            // Handle special case of being in the last hidden
            // layer. Also, setup the Auxiliary address which, in
            // this state, is used to store the address of the
            // previous layer's inputs
            when (t.currentLayer === t.numLayers - 2.U) {
              val regFileAddrIn = t.regFileAddrIn
              val regFileAddrAux = regFileAddrIn - niplOffset
              //address to read outputs to compute derivative
              t.regFileAddrIn := regFileAddrIn
              t.regFileAddrAux := regFileAddrAux
              printfInfo("  regFileAddrIn:    0x%x\n", regFileAddrIn)
              printfInfo("  regFileAddrAux:   0x%x\n", regFileAddrAux)
            } .otherwise {
              val regFileAddrIn = t.regFileAddrIn - niclOffset
              val regFileAddrAux = regFileAddrIn - niplOffset
              t.regFileAddrIn := regFileAddrIn
              t.regFileAddrAux := regFileAddrAux
              printfInfo("  regFileAddrIn:    0x%x\n", regFileAddrIn)
              printfInfo("  regFileAddrAux:   0x%x\n", regFileAddrAux)
            }

            // [TODO] Check that this is working
            t.biasAddr := t.biasAddr - niclOffset
            printfInfo("  offsetBias:       0x%x\n", t.offsetBias)
            printfInfo("  offsetDW:         0x%x\n", t.offsetDW)
            printfInfo("  regFileAddrDw:    0x%x -> 0x%x\n",
              t.regFileAddrDW, t.regFileAddrDW + niclOffset)
            printfInfo("  regFileAddrSlope: 0x%x\n",
              t.regFileAddrSlope)
            printfInfo("  biasAddr:         0x%x\n",
              t.regFileAddrDW - niclOffset)

          }
          is(e_TTABLE_STATE_LEARN_WEIGHT_UPDATE){
            when(t.transactionType === e_TTYPE_BATCH){
              printfInfo("Layer Update, state == LEARN_WEIGHT_UPDATE\n")
              when (t.currentLayer === 0.U){
                t.regFileAddrDW := t.regFileAddrInFixed
                t.regFileAddrIn := t.regFileAddrInFixed +
                niclOffset

                // If we're in the first layer, then we need to go
                // ahead and update the slope address. We can
                // compute this because we know both the slope
                // offset (which is the offset from the bias region
                // to the start of the weight update region).
                t.biasAddr := t.regFileAddrSlope -
                t.offsetBias
                printfInfo("  regFileAddrDw:   0x%x\n",
                  t.regFileAddrInFixed)
                printfInfo("  regFileAddrIn:   0x%x\n",
                  t.regFileAddrInFixed + niclOffset)
              }.otherwise{
                t.regFileAddrDW := t.regFileAddrIn
                t.regFileAddrIn := t.regFileAddrIn + niclOffset
                t.biasAddr := t.biasAddr + niplOffset
                printfInfo("  regFileAddrDw:   0x%x\n",
                  t.regFileAddrIn)
                printfInfo("  regFileAddrIn:   0x%x\n",
                  t.regFileAddrInFixed + niclOffset)
                printfInfo("  biasAddr:        0x%x\n",
                  t.biasAddr + niplOffset)
              }
            }.otherwise{
              t.regFileAddrDW := t.regFileAddrIn
              t.regFileAddrIn := t.regFileAddrIn + niplOffset
            }
          }
        }
        printfInfo("  inFirst/inLast/inLastEarly: 0x%x/0x%x/0x%x\n",
          t.inFirst, t.inLast, t.inLastEarly)
      }
    }
  }

  when (layerValid) {
    val tIdx = io.control.resp.bits.layerValidIndex
    val t = table(tIdx)
    val inLastOld = t.inLast
    val inLastNew = t.currentLayer === (t.numLayers - 1.U)
    val inFirstNew = t.currentLayer === 0.U

    val isFeedforward = t.transactionType === e_TTYPE_FEEDFORWARD
    val isIncremental = t.transactionType === e_TTYPE_INCREMENTAL
    val isBatch       = t.transactionType === e_TTYPE_BATCH
    t.inFirst := inFirstNew
    when (isFeedforward) {
      t.waiting := false.B
      t.decInUse := inLastOld }
    when (isIncremental) {
      t.waiting := false.B
      t.decInUse := ( t.inFirst &&
        t.stateLearn === e_TTABLE_STATE_LEARN_ERROR_BACKPROP )}
    when (isBatch) {
      when (inLastOld &&
        t.currentLayer === (t.numLayers - 1.U) &&
        t.stateLearn === e_TTABLE_STATE_LEARN_WEIGHT_UPDATE) {
        t.waiting := false.B
        t.decInUse := true.B
      } .elsewhen (t.stateLearn === e_TTABLE_STATE_LOAD_OUTPUTS) {
        t.indexElement := 0.U
        t.flags.done := true.B
        t.flags.valid := false.B
      } .otherwise {
        t.waiting := false.B
      } }
    printfInfo("  inFirst/inLast/inLastEarly/state: %x->%x/%x->%x/%x/0x%x\n",
      t.inFirst, inFirstNew, inLastOld, inLastNew, t.inLastEarly, t.stateLearn)
  }

  entryArbiter.io.in zip table map { case(e, t) =>
    e.bits.globalWtptr      := t.globalWtptr
    e.bits.inLastEarly      := t.inLastEarly
    e.bits.transactionType  := t.transactionType
    e.bits.stateLearn       := t.stateLearn
    e.bits.errorFunction    := t.errorFunction
    e.bits.learningRate     := t.learningRate
    e.bits.weightDecay      := t.weightDecay
    e.bits.numWeightBlocks  := t.numWeightBlocks
    e.bits.regFileAddrDW    := t.regFileAddrDW
    e.bits.regFileAddrSlope := t.regFileAddrSlope
    e.bits.regFileAddrBias  := t.biasAddr
    e.bits.regFileAddrAux   := t.regFileAddrAux
    e.bits.batchFirst       := t.curBatchItem === 0.U}

  when (isPeReq) {
    val tIdx = entryArbiter.io.out.bits.tableIndex
    val inLastNode = table(tIdx).currentNodeInLayer ===
      table(tIdx).nodesInCurrentLayer - 1.U
    val notInLastLayer = table(tIdx).currentLayer <
      (table(tIdx).numLayers - 1.U)

    // If we're at the end of a layer, we need new layer information
    // The comparison here differs from how this is handled in
    // nn_instruction.v.
    switch(table(tIdx).stateLearn){
      is(e_TTABLE_STATE_FEEDFORWARD){
        when(inLastNode && notInLastLayer) {
          table(tIdx).needsLayerInfo := true.B
          table(tIdx).currentLayer := table(tIdx).currentLayer + 1.U
          table(tIdx).regFileLocationBit := !table(tIdx).regFileLocationBit
        } .otherwise {
          table(tIdx).needsLayerInfo := false.B
          table(tIdx).currentLayer := table(tIdx).currentLayer
        }
      }
      is(e_TTABLE_STATE_LEARN_FEEDFORWARD){
        when(table(tIdx).inLast && inLastNode){
          table(tIdx).needsLayerInfo := true.B
          table(tIdx).currentLayer := table(tIdx).currentLayer - 1.U
          table(tIdx).regFileLocationBit := !table(tIdx).regFileLocationBit
          table(tIdx).inLastEarly := true.B
          table(tIdx).stateLearn := e_TTABLE_STATE_LEARN_ERROR_BACKPROP
        } .elsewhen (inLastNode && notInLastLayer) {
          table(tIdx).needsLayerInfo := true.B
          table(tIdx).currentLayer := table(tIdx).currentLayer + 1.U
          table(tIdx).regFileLocationBit := !table(tIdx).regFileLocationBit

          // inLastEarly will assert as soon as the last PE Request goes
          // out. This is useful if you need something that goes high at
          // the earliest possible definition of "being in the last
          // layer", e.g., when generating a request for the next layer
          // information.
          table(tIdx).inLastEarly :=
            table(tIdx).currentLayer === (table(tIdx).numLayers - 2.U)
        } .otherwise {
          table(tIdx).needsLayerInfo := false.B
          table(tIdx).currentLayer := table(tIdx).currentLayer
        }
      }
      is(e_TTABLE_STATE_LEARN_ERROR_BACKPROP){
        when(table(tIdx).inFirst && inLastNode) {
          // table(tIdx).needsLayerInfo := true.B
          table(tIdx).needsLayerInfo := false.B
          // table(tIdx).currentLayer := table(tIdx).currentLayer + 1.U
          table(tIdx).currentLayer := 0.U
          table(tIdx).regFileLocationBit := !table(tIdx).regFileLocationBit
          table(tIdx).inLastEarly :=
            table(tIdx).currentLayer === (table(tIdx).numLayers - 2.U)
          when(table(tIdx).transactionType === e_TTYPE_BATCH){
            when (table(tIdx).curBatchItem === (table(tIdx).numBatchItems - 1.U)) {
              table(tIdx).needsLayerInfo := true.B
              table(tIdx).stateLearn := e_TTABLE_STATE_LEARN_WEIGHT_UPDATE
            } .otherwise {
              table(tIdx).stateLearn := e_TTABLE_STATE_LOAD_OUTPUTS
              table(tIdx).curBatchItem := table(tIdx).curBatchItem + 1.U
            }
          }
          when (table(tIdx).transactionType === e_TTYPE_INCREMENTAL) {
            // [TODO] What should happen here? The transaction is done
            // as the cache was updated during the error
            // backpropagation pass.
            table(tIdx).waiting := true.B
          }
        } .elsewhen(inLastNode && (table(tIdx).currentLayer > 0.U)) {
          table(tIdx).needsLayerInfo := true.B
          table(tIdx).currentLayer := table(tIdx).currentLayer - 1.U
          table(tIdx).regFileLocationBit := !table(tIdx).regFileLocationBit
        } .otherwise {
          table(tIdx).needsLayerInfo := false.B
          table(tIdx).currentLayer := table(tIdx).currentLayer
        }
      }
      is (e_TTABLE_STATE_LEARN_WEIGHT_UPDATE) {
        when(table(tIdx).inLast && inLastNode){
            table(tIdx).waiting := true.B
          table(tIdx).currentLayer := table(tIdx).currentLayer
        } .elsewhen(inLastNode && notInLastLayer) {
          table(tIdx).needsLayerInfo := true.B
          table(tIdx).currentLayer := table(tIdx).currentLayer + 1.U
          table(tIdx).regFileLocationBit := !table(tIdx).regFileLocationBit
        } .otherwise {
          table(tIdx).needsLayerInfo := false.B
          table(tIdx).currentLayer := table(tIdx).currentLayer
          table(tIdx).inLastEarly := (
            table(tIdx).currentLayer === (table(tIdx).numLayers - 2.U) )
        }
      }
    }
  }

  // Catch any jumps to an error state
  table map (t => assert(
    !((t.flags.valid || t.flags.reserved) && t.stateLearn === e_TTABLE_STATE_ERROR),
      "DANA TTable Transaction is in error state") )
}
