// See LICENSE for license details.

package dana

import Chisel._

import rocket.{RoCCCommand, RoCCResponse, MStatus}
import cde.Parameters
import xfiles.{TransactionTableNumEntries, TableEntry, HasTable,
  XFilesResponseCodes, XFilesBackendReq, XFilesBackendResp,
  XFilesQueueInterface}
import util.ParameterizedBundle

class TransactionState(implicit p: Parameters) extends TableEntry()(p)
    with DanaParameters {
  //-------- Base class
  val validIO = Bool()
  val cacheValid = Bool()
  val waiting = Bool()
  val needsLayerInfo = Bool()
  val decInUse = Bool()
  val needsAsidNnid = Bool()
  val needsInputs = Bool()
  // There are two "in the last layer" bits. The first, "inLast",
  // asserts when all PEs in the previous layer are done. The latter,
  // "inLastEarly", asserts as soon as all PEs in the previous layer
  // have been assigned.
  val inLast = Bool()
  val inFirst = Bool()
  val cacheIndex = UInt(width = log2Up(cacheNumEntries))
  val nnid = UInt(width = nnidWidth)
  val decimalPoint = UInt(width = decimalPointWidth)
  val numLayers = UInt(width = 16)           // [TODO] fragile
  val numNodes = UInt(width = 16)            // [TODO] fragile
  val currentNode = UInt(width = 16)         // [TODO] fragile
  val currentNodeInLayer = UInt(width = 16)  // [TODO] fragile
  val currentLayer = UInt(width = 16)        // [TODO] fragile
  val nodesInCurrentLayer = UInt(width = 16) // [TODO] fragile
  val neuronPointer = UInt(width = 11)       // [TODO] fragile
  val regFileLocationBit = UInt(width = 1)
  val regFileAddrIn = UInt(width = log2Up(regFileNumElements))
  val regFileAddrOut = UInt(width = log2Up(regFileNumElements))
  val readIdx = UInt(width = log2Up(regFileNumElements))
  val indexElement = UInt(width = log2Up(regFileNumElements))
  //-------- Can be possibly moved over to a learning-only config
  val regFileAddrOutFixed = UInt(width = log2Up(regFileNumElements))

  aliasList += ( "valid" -> "V",
    "reserved" -> "R",
    "cacheValid" -> "C",
    "waiting" -> "W",
    "needsLayerInfo" -> "NLI",
    "done" -> "D",
    "decInUse" -> "-",
    "inLast" -> "L?",
    "inFirst" -> "F?",
    "cacheIndex" -> "C#",
    "decimalPoint" -> "DP",
    "numLayers" -> "#L",
    "numNodes" -> "#N",
    "currentNode" -> "cN",
    "currentNodeInLayer" -> "cNiL",
    "currentLayer" -> "cL",
    "nodesInCurrentLayer" -> "#NcL",
    "neuronPointer" -> "N*",
    "regFileLocationBit" -> "LB",
    "regFileAddrIn" -> "AIn",
    "regFileAddrOut" -> "AOut",
    "readIdx" -> "R#",
    "indexElement" -> "#E",
    "regFileAddrOutFixed" -> "AOutF"
  )
  override def reset() {
    super.reset()
  }
  def reserve() {
    this.flags.valid := Bool(false)
    this.flags.reserved := Bool(true)
    this.flags.done := Bool(false)
    this.needsAsidNnid := Bool(true)
    this.needsInputs := Bool(false)
    this.indexElement := UInt(0)
    this.validIO := Bool(true)
    this.cacheValid := Bool(false)
    this.waiting := Bool(false)
    this.needsLayerInfo := Bool(true)
    this.currentLayer := UInt(0)
    this.decInUse := Bool(false)
    this.indexElement := UInt(0)
    this.regFileAddrOut := UInt(0)
    this.nodesInCurrentLayer := UInt(0)
    this.currentNode := UInt(0)
    this.readIdx := UInt(0)
    this.inFirst := Bool(true)
    this.inLast := Bool(false)
    this.needsLayerInfo := Bool(true)
    this.regFileLocationBit := UInt(0)
  }
  def enable() {
    this.flags.valid := Bool(true)
    this.currentNode := UInt(0)
    this.readIdx := UInt(0)
    this.inFirst := Bool(true)
    this.inLast := Bool(false)
    this.needsLayerInfo := Bool(true)
    this.flags.done := Bool(false)
    this.waiting := Bool(false)
    this.regFileLocationBit := UInt(0)
  }
}

class TransactionStateLearn(implicit p: Parameters)
    extends TransactionState()(p) {
  // flags
  val needsOutputs = Bool()
  //
  val globalWtptr = UInt(width = 16)            //[TODO] fragile
  val inLastEarly = Bool()
  val transactionType = UInt(width = log2Up(3)) // [TODO] fragile
  val numTrainOutputs = UInt(width = 16)        // [TODO] fragile
  val stateLearn = UInt(width = log2Up(8))      // [TODO] fragile
  val errorFunction = UInt(width = log2Up(2))   // [TODO] fragile
  val learningRate = UInt(width = 16)           // [TODO] fragile
  val lambda = UInt(width = 16)                 // [TODO] fragile
  val numWeightBlocks = UInt(width = 16)        // [TODO] fragile
  val mse = UInt(width = elementWidth)          // unused
  // Batch training information
  val numBatchItems = UInt(width = 16)          // [TODO] fragile
  val curBatchItem = UInt(width = 16)           // [TODO] fragile
  val biasAddr = UInt(width = 16)               // [TODO] fragile
  val offsetBias = UInt(width = 16)             // [TODO] fragile
  val offsetDW = UInt(width = 16)               // [TODO] fragile
  val numOutputs = UInt(width = 16)             // [TODO] fragile
  // We need to keep track of where inputs and outputs should be
  // written to in the Register File.
  val regFileAddrInFixed = UInt(width = log2Up(regFileNumElements))
  // val regFileAddrDelta = UInt(width = log2Up(regFileNumElements))
  val regFileAddrDW = UInt(width = log2Up(regFileNumElements))
  val regFileAddrSlope = UInt(width = log2Up(regFileNumElements))
  val regFileAddrAux = UInt(width = log2Up(regFileNumElements))
  val nodesInPreviousLayer = UInt(width = 16) // [TODO] fragile
  val nodesInLast = UInt(width = 16) // [TODO] fragile

  aliasList += (
    "globalWtptr" -> "GW*",
    "inLastEarly" -> "L?e",
    "transactionType" -> "T?",
    "numTrainOutputs" -> "#TO",
    "stateLearn" -> "state",
    "errorFunction" -> "ef",
    "learningRate" -> "lr",
    "lambda" -> "Y",
    "numWeightBlocks" -> "#WB",
    "numBatchItems" -> "#BI",
    "curBatchItem" -> "cB",
    "biasAddr" -> "AB",
    "offsetBias" -> "oB",
    "offsetDW" -> "oDW",
    "regFileAddrInFixed" -> "AInF",
    "regFileAddrDW" -> "ADW",
    "regFileAddrSlope" -> "AS",
    "regFileAddrAux" -> "AAux",
    "nodesInPreviousLayer" -> "nipl",
    "nodesInLast" -> "nil"
  )

  override def reserve() {
    super.reserve()
    this.needsOutputs := Bool(false)
    this.inLastEarly := Bool(false)
    this.regFileAddrIn := UInt(0)
    this.regFileAddrDW := UInt(0)
    this.regFileAddrSlope := UInt(0)
    this.offsetBias := UInt(0)
    this.offsetDW := UInt(0)
    this.regFileAddrInFixed := UInt(0)
    // [TODO] Temporary value for number of batch items
    this.numBatchItems := UInt(1)
    this.curBatchItem := UInt(0)
  }

  override def enable() {
    super.enable()
    this.inLastEarly := Bool(false)
    this.regFileAddrIn := UInt(0)
    this.regFileAddrDW := UInt(0)
    this.regFileAddrSlope := UInt(0)
    this.offsetBias := UInt(0)
    this.offsetDW := UInt(0)
  }
}

class ControlReq(implicit p: Parameters) extends DanaBundle()(p) {
  // Bools
  val cacheValid = Bool()
  val waiting = Bool()
  val needsLayerInfo = Bool()
  val isDone = Bool()
  val inFirst = Bool()
  val inLast = Bool()
  // Global info
  val tableIndex = UInt(width = log2Up(transactionTableNumEntries))
  val cacheIndex = UInt(width = log2Up(cacheNumEntries))
  val asid = UInt(width = asidWidth)
  val nnid = UInt(width = nnidWidth) // formerly nn_hash
  // State info
  val currentNodeInLayer = UInt(width = 16) // [TODO] fragile
  val currentLayer = UInt(width = 16) // [TODO] fragile
  val neuronPointer = UInt(width = 11) // [TODO] fragile
  val decimalPoint = UInt(width = decimalPointWidth)
  val regFileAddrIn = UInt(width = log2Up(regFileNumElements))
  val regFileAddrOut = UInt(width = log2Up(regFileNumElements))
  val regFileLocationBit = UInt(width = 1) // [TODO] fragile on definition above
}

class ControlReqLearn(implicit p: Parameters) extends ControlReq()(p) {
  val globalWtptr = UInt(width = 16) // [TODO] fragile
  val inLastEarly = Bool()
  val transactionType = UInt(width = log2Up(3)) // [TODO] fragile
  val stateLearn = UInt(width = log2Up(8)) // [TODO] fragile
  val errorFunction = UInt(width = log2Up(2)) // [TODO] fragile
  val learningRate = UInt(width = 16) // [TODO] fragile
  val lambda = UInt(width = 16) // [TODO] fragile
  val numWeightBlocks = UInt(width = 16) // [TODO] fragile
  // val regFileAddrDelta = UInt(width = log2Up(regFileNumElements))
  val regFileAddrDW = UInt(width = log2Up(regFileNumElements))
  val regFileAddrSlope = UInt(width = log2Up(regFileNumElements))
  val regFileAddrBias = UInt(width = log2Up(regFileNumElements))
  val regFileAddrAux = UInt(width = log2Up(regFileNumElements))
  val batchFirst = Bool()
}

class ControlResp(implicit p: Parameters) extends DanaBundle()(p) {
  val readyCache = Bool()
  val readyPeTable = Bool()
  val cacheValid = Bool()
  val tableIndex = UInt(width = log2Up(transactionTableNumEntries))
  val field = UInt(width = 4) // [TODO] fragile on Constants.scala
  val data = Vec(6, UInt(width = 16)) // [TODO] fragile
  val decimalPoint = UInt(width = decimalPointWidth)
  val layerValid = Bool()
  val layerValidIndex = UInt(width = log2Up(transactionTableNumEntries))
}

class ControlRespLearn(implicit p: Parameters) extends ControlResp()(p) {
  val globalWtptr = UInt(width = 16) //[TODO] fragile
}

class TTableControlInterface(implicit val p: Parameters)
    extends ParameterizedBundle()(p) {
  lazy val req = Decoupled(new ControlReq)
  lazy val resp = Decoupled(new ControlResp).flip
}

class TTableControlInterfaceLearn(implicit p: Parameters)
    extends TTableControlInterface()(p) {
  override lazy val req = Decoupled(new ControlReqLearn)
  override lazy val resp = Decoupled(new ControlRespLearn).flip
}

class TTableRegisterFileReq(implicit p: Parameters) extends DanaBundle()(p) {
  val reqType = UInt(width = log2Up(2)) // [TODO] Frgaile on Dana enum
  val tidIdx = UInt(width = log2Up(transactionTableNumEntries))
  val addr = UInt(width = log2Up(regFileNumElements))
  val data = UInt(width = elementWidth)
}

class TTableRegisterFileResp(implicit p: Parameters) extends DanaBundle()(p) {
  val data = UInt(width = elementWidth)
}

class TTableRegisterFileInterface(implicit p: Parameters) extends DanaBundle()(p) {
  val req = Valid(new TTableRegisterFileReq)
  val resp = Valid(new TTableRegisterFileResp).flip
}

class TTableArbiter(implicit p: Parameters) extends DanaBundle()(p) {
  val rocc = new Bundle {
    val cmd = Decoupled(new RoCCCommand).flip
    val resp = Decoupled(new RoCCResponse)
    val status = new MStatus().asInput
  }
  val xfReq = (new XFilesBackendReq).flip
  val xfResp = new XFilesBackendResp
  val xfQueue = new XFilesQueueInterface
}

class DanaTransactionTableInterface(implicit p: Parameters) extends DanaBundle()(p) {
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
  lazy val io = new DanaTransactionTableInterface

  // IO aliases
  // val cmd = new DanaBundle {
  //   val asid = io.arbiter.rocc.cmd.bits.rs1(asidWidth + tidWidth - 1, tidWidth)
  //   val tid = io.arbiter.rocc.cmd.bits.rs1(tidWidth - 1, 0)
  //   val countFeedback =
  //     io.arbiter.rocc.cmd.bits.rs1(feedbackWidth + asidWidth + tidWidth - 1,
  //       asidWidth + tidWidth)
  //   val nnid = io.arbiter.rocc.cmd.bits.rs2(nnidWidth - 1, 0)
  //   val data = io.arbiter.rocc.cmd.bits.rs2
  //   val rd = io.arbiter.rocc.cmd.bits.inst.rd
  //   val regId = io.arbiter.rocc.cmd.bits.rs2(63,32)
  //   val regValue = io.arbiter.rocc.cmd.bits.rs2(31,0)
  //   // Only used with learning, but maintained for assertion checking
  //   val transactionType = io.arbiter.rocc.cmd.bits.rs2(49,48)
  //   val numTrainOutputs = io.arbiter.rocc.cmd.bits.rs2(47,32)
  //   val raw = io.arbiter.rocc.cmd }

  // Create the actual Transaction Table
  val table = Reg(genStateVec)

  // Temporary signal tie-offs
  io.arbiter.xfReq.tidx.ready := Bool(true)
  io.arbiter.xfResp.tidx.valid := Bool(false)
  io.arbiter.xfQueue.in.ready := Bool(false)
  io.arbiter.xfQueue.out.valid := Bool(false)

  // Control is broken up into X-FILES orchestrated updates and
  // independent actions
  when (io.arbiter.xfReq.tidx.fire()) {
    val entry = table(io.arbiter.xfReq.tidx.bits)
    entry.validIO := Bool(true)
    when (!entry.flags.reserved) {
      entry.reserve()
    }
  }


  // Determine if there exits a free entry in the table and the index
  // of the next availble free entry
  io.arbiter.rocc.cmd.ready := Bool(true)
  io.arbiter.rocc.resp.bits.rd := UInt(0)
  io.arbiter.rocc.resp.bits.data := UInt(0)
  // Default register file connections
  io.regFile.req.valid := Bool(false)
  io.regFile.req.bits.reqType := UInt(0)
  io.regFile.req.bits.tidIdx := UInt(0)
  io.regFile.req.bits.addr := UInt(0)
  io.regFile.req.bits.data := UInt(0)

  io.arbiter.rocc.resp.valid := Bool(false)

  val roccCmd = io.arbiter.rocc.cmd
  val newRoccCmd = roccCmd.fire() && !io.arbiter.rocc.status.prv.orR
  val regWrite = newRoccCmd & roccCmd.bits.inst.funct === UInt(t_USR_WRITE_REGISTER)

  // Update the table when we get a request from DANA
  when (io.control.resp.valid) {
    val tIdx = io.control.resp.bits.tableIndex
    // table(tIdx).waiting := Bool(true)
    when (io.control.resp.bits.cacheValid) {
      switch(io.control.resp.bits.field) {
        is(e_TTABLE_CACHE_VALID) {
          table(tIdx).cacheValid := Bool(true)
          table(tIdx).numLayers := io.control.resp.bits.data(0)
          table(tIdx).numNodes := io.control.resp.bits.data(1)
          table(tIdx).cacheIndex := io.control.resp.bits.data(2)(
            log2Up(cacheNumEntries) + errorFunctionWidth - 1, errorFunctionWidth)
          table(tIdx).decimalPoint := io.control.resp.bits.decimalPoint
          // table(tIdx).learningRate := io.control.resp.bits.data(3)
          // table(tIdx).lambda := io.control.resp.bits.data(4)
          // Once we know the cache is valid, this entry is no longer waiting
          table(tIdx).waiting := Bool(false)
          printfInfo("DANA TTable: Updating global info from Cache...\n")
          printfInfo("  total layers:            0x%x\n",
            io.control.resp.bits.data(0))
          printfInfo("  total nodes:             0x%x\n",
            io.control.resp.bits.data(1))
          printfInfo("  cache index:             0x%x\n",
            io.control.resp.bits.data(2)(
            log2Up(cacheNumEntries) + errorFunctionWidth - 1, errorFunctionWidth))
        }
        is(e_TTABLE_LAYER) {
          table(tIdx).needsLayerInfo := Bool(false)
          table(tIdx).currentNodeInLayer := UInt(0)
          table(tIdx).nodesInCurrentLayer := io.control.resp.bits.data(0)
          table(tIdx).neuronPointer := io.control.resp.bits.data(2)

          // Once we have layer information, we can update the
          // previous and current layer addresses. These are adjusted
          // to be on block boundaries, so there's an optional round
          // term.
          val nicl = io.control.resp.bits.data(0)
          val niclMSBs = // Nodes in previous layer MSBs [TODO] fragile
            nicl(15, log2Up(elementsPerBlock)) ##
              UInt(0, width=log2Up(elementsPerBlock))
          val niclLSBs = // Nodes in previous layer LSBs
            nicl(log2Up(elementsPerBlock)-1, 0)
          val round = Mux(niclLSBs =/= UInt(0), UInt(elementsPerBlock), UInt(0))
          val niclOffset = niclMSBs + round

          val niplMSBs =
            table(tIdx).nodesInCurrentLayer(15, log2Up(elementsPerBlock)) ##
              UInt(0, width=log2Up(elementsPerBlock))
          val niplLSBs = table(tIdx).nodesInCurrentLayer(log2Up(elementsPerBlock)-1,0)
          val niplOffset = niplMSBs + Mux(niplLSBs =/= UInt(0),
            UInt(elementsPerBlock), UInt(0))

          printfInfo("DANA TTable: Updating cache layer...\n")
          printfInfo("  total layers:               0x%x\n",
            table(tIdx).numLayers)
          printfInfo("  layer is:                   0x%x\n",
            table(tIdx).currentLayer)
          printfInfo("  neuron pointer:             0x%x\n",
            io.control.resp.bits.data(2))
          printfInfo("  nodes in current layer:     0x%x\n",
            io.control.resp.bits.data(0))
          printfInfo("  nodes in previous layer:    0x%x\n",
            io.control.resp.bits.data(1))
          printfInfo("  nicl:                       0x%x\n", nicl)
          printfInfo("  niclMSBs:                   0x%x\n", niclMSBs)
          printfInfo("  niclLSBs:                   0x%x\n", niclLSBs)
          printfInfo("  round:                      0x%x\n", round)
          printfInfo("  niplMSBs:                   0x%x\n", niplMSBs)
          printfInfo("  niplLSBs:                   0x%x\n", niplLSBs)
          printfInfo("  niplOffset:                 0x%x\n", niplOffset)
          printfInfo("  regFileAddrIn:              0x%x\n",
            table(tIdx).regFileAddrOut)
          printfInfo("  regFileAddrOut:             0x%x\n",
            table(tIdx).regFileAddrOut +  niplOffset)
        }
      }
    }
    // If the register file has all valid entries, then this specific
    // entry should stop waiting. Note, that this logic will correctly
    // overwrite that of the e_TTABLE_LAYER.
    when (io.control.resp.bits.layerValid) {
      val tIdx = io.control.resp.bits.layerValidIndex
      val inLastOld = table(tIdx).inLast
      val inLastNew = table(tIdx).currentLayer === (table(tIdx).numLayers - UInt(1))
      table(tIdx).inLast := inLastNew
      printfInfo("DANA TTable: RegFile has all outputs of tIdx 0x%x\n",
        io.control.resp.bits.layerValidIndex)
    }
  }

  val readyCache = Reg(next = io.control.resp.bits.readyCache)
  val readyPeTable = Reg(next = io.control.resp.bits.readyPeTable)
  // Round Robin Arbitration of Transaction Table entries. One of
  // these is passed out over an interface to DANA's control module.
  val entryArbiter = Module(new RRArbiter(genControlReq,
    transactionTableNumEntries))
  for (i <- 0 until transactionTableNumEntries) {
    val isValid = table(i).flags.valid
    val isNotWaiting = !table(i).waiting
    val noRequestLastCycle = Reg(next = !entryArbiter.io.out.valid)
    val cacheWorkToDo = (table(i).decInUse || !table(i).cacheValid ||
      table(i).needsLayerInfo)
    val peWorkToDo = (table(i).currentNode =/= table(i).numNodes)
    // The entryArbiter has a valid request if that TTable entry is
    // valid, it is not waiting, a request was not generated last
    // cycle, and either there is cache or PE table work to do and the
    // backend can support one of these.
    entryArbiter.io.in(i).valid := isValid && isNotWaiting &&
      noRequestLastCycle &&
      ((readyCache && cacheWorkToDo) || (readyPeTable && peWorkToDo))
    // The other data connections are just aliases to the contents of
    // the specific table entry
    entryArbiter.io.in(i).bits := table(i)
    entryArbiter.io.in(i).bits.isDone := table(i).decInUse
    entryArbiter.io.in(i).bits.tableIndex := UInt(i)
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
  io.arbiter.xfQueue.tidxOut := Reg(next = ioArbiter.chosen)
  io.arbiter.xfResp.tidx.bits := ioArbiter.chosen
  io.arbiter.xfResp.flags.reset("vdio")

  val queueOutTidx_d = Reg(Valid(UInt(width = log2Up(transactionTableNumEntries))))
  queueOutTidx_d.valid := Bool(false)
  queueOutTidx_d.bits := ioArbiter.chosen
  when (ioArbiter.out.valid) {
    val entry = table(ioArbiter.chosen)
    when (entry.needsAsidNnid) {
      when (!io.arbiter.xfQueue.in.valid) {
        io.arbiter.xfResp.tidx.valid := Bool(true)
        io.arbiter.xfResp.flags.set("vi")
        entry.validIO := Bool(false)
        printfInfo("DANA TTable: Entry 0d%d needs INFO, but In Queue not ready\n",
          ioArbiter.chosen)
      } .otherwise {
        // io.arbiter.xfResp.tidx.valid := Bool(true)
        // io.arbiter.xfResp.flags.set("v")
        io.arbiter.xfQueue.in.ready := Bool(true)
        val asid = io.arbiter.xfQueue.in.bits.rs1(asidWidth + tidWidth - 1, tidWidth)
        val tid = io.arbiter.xfQueue.in.bits.rs1(tidWidth - 1, 0)
        val nnid = io.arbiter.xfQueue.in.bits.rs2(nnidWidth - 1, 0)
        entry.asid := asid
        entry.tid := tid
        entry.nnid := nnid
        entry.needsAsidNnid := Bool(false)
        printfInfo("DANA TTable: T0d%d got (ASID:0x%x/TID:0x%x/NNID:0x%x) from queue\n",
          ioArbiter.chosen, asid, tid, nnid)
        entry.needsInputs := Bool(true)
      }
    }

    when (entry.needsInputs) {
      val tidIdx = io.arbiter.xfResp.tidx.bits
      when (!io.arbiter.xfQueue.in.valid) {
        io.arbiter.xfResp.tidx.valid := Bool(true)
        io.arbiter.xfResp.flags.set("vi")
        entry.validIO := Bool(false)
        printfInfo("DANA TTable: Entry 0d%d needs INPUTS, but In Queue not ready\n",
          ioArbiter.chosen)
      } .otherwise {
        // io.arbiter.xfResp.tidx.valid := Bool(true)
        // io.arbiter.xfResp.flags.set("v")
        io.arbiter.xfQueue.in.ready := Bool(true)
        io.regFile.req.valid := Bool(true)
        io.regFile.req.bits.reqType := e_TTABLE_REGFILE_WRITE
        io.regFile.req.bits.tidIdx := tidIdx
        io.regFile.req.bits.addr := entry.indexElement
        val data = io.arbiter.xfQueue.in.bits.rs2
        io.regFile.req.bits.data := data
        printfInfo("DANA TTable: T0d%d got (INPUT:0x%x) from queue\n",
          ioArbiter.chosen, data)

        val isLast = io.arbiter.xfQueue.in.bits.funct === UInt(t_USR_WRITE_DATA_LAST)
        when (isLast) {
          val nextIndexBlock = (table(tidIdx).indexElement(
            log2Up(regFileNumElements)-1,log2Up(elementsPerBlock)) ##
            UInt(0, width=log2Up(elementsPerBlock))) + UInt(elementsPerBlock)
          entry.indexElement := nextIndexBlock
          entry.needsInputs := Bool(false)
          entry.enable()
        } .otherwise {
          entry.indexElement := entry.indexElement + UInt(1)
        }
      }
    }

    when (entry.flags.done) {
      when (!io.arbiter.xfQueue.out.ready) {
        io.arbiter.xfResp.tidx.valid := Bool(true)
        io.arbiter.xfResp.flags.set("vo")
        entry.validIO := Bool(false)
        printfInfo("DANA TTable: Entry 0d%d has OUTPUTS, but Out Queue not ready\n",
          ioArbiter.chosen)
      } .otherwise {
        // [TODO] Kludge to slow down the output rate so that we don't
        // overwrite the FIFO.
        io.regFile.req.valid := Bool(true)
        io.regFile.req.bits.reqType := e_TTABLE_REGFILE_READ
        io.regFile.req.bits.addr := entry.readIdx + entry.regFileAddrOutFixed

        entry.readIdx := entry.readIdx + UInt(1)

        queueOutTidx_d.valid := Bool(true)

        printfInfo("DANA TTable: Req output 0x%x from Reg File sent\n",
          entry.readIdx)
      }
    }
  }

  io.arbiter.xfQueue.out.valid := queueOutTidx_d.valid
  io.arbiter.xfQueue.out.bits := io.regFile.resp.bits.data

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
    table(tIdx).waiting := Bool(true)
    when (entryArbiter.io.out.bits.isDone) {
      printfInfo("DANA TTable entry for ASID/TID %x/%x is done\n",
        table(tIdx).asid, table(tIdx).tid)
      table(tIdx).flags.done := Bool(true) }}
  when (isPeReq) {
    val tIdx = entryArbiter.io.out.bits.tableIndex
    val inLastNode = table(tIdx).currentNodeInLayer ===
      table(tIdx).nodesInCurrentLayer - UInt(1)
    val notInLastLayer = table(tIdx).currentLayer <
      (table(tIdx).numLayers - UInt(1))
    table(tIdx).currentNode := table(tIdx).currentNode + UInt(1)
    table(tIdx).currentNodeInLayer := table(tIdx).currentNodeInLayer + UInt(1) }

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
      table(i).numNodes))).contains(Bool(true)),
    "A TTable entry has a currentNode count greater than the total numNodes")

  // Don't send a response to the core unless it's ready
  assert(!(io.arbiter.rocc.resp.valid && !io.arbiter.rocc.resp.ready),
    "DANA TTable tried to send a valid response when core was not ready")

  // No writes should show up if the transaction is already valid
  // assert(!(newRoccCmd && cmd.readOrWrite && table(derefTidIndex).flags.valid),
  //   "DANA TTable saw write requests on valid TID")

  // Temporary printfs and assertions
  when (io.arbiter.xfReq.tidx.fire()) {
    printfInfo("DANA TTable: XF scheduled tidx 0d%d\n",
      io.arbiter.xfReq.tidx.bits)
  }

  when (io.arbiter.xfResp.tidx.fire()) {
    val flags = io.arbiter.xfResp.flags
    when (flags.input | flags.output) {
      printfInfo("DANA TTable: Deschedule T0d%d with flags VDIO/%b%b%b%b\n",
        io.arbiter.xfResp.tidx.bits, flags.valid, flags.done, flags.input,
        flags.output)
    } .otherwise {
      printfInfo("DANA TTable: Reschedule T0d%d with flags VDIO/%b%b%b%b\n",
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
      (entry.readIdx === entry.nodesInCurrentLayer - UInt(1))
    when (finished) {
      io.arbiter.xfResp.tidx.valid := Bool(true)
      io.arbiter.xfResp.flags.set("d")
      entry.flags.valid := Bool(false)
      entry.flags.reserved := Bool(false)
      entry.flags.done := Bool(false)
    }
  }

  when (io.control.resp.valid) {
    val tIdx = io.control.resp.bits.tableIndex
    when (io.control.resp.bits.cacheValid) {
      switch (io.control.resp.bits.field) {
        is (e_TTABLE_LAYER) {
          val nicl = io.control.resp.bits.data(0)
          val niclMSBs = // Nodes in previous layer MSBs [TODO] fragile
            nicl(15, log2Up(elementsPerBlock)) ##
          UInt(0, width=log2Up(elementsPerBlock))
          val niclLSBs = // Nodes in previous layer LSBs
            nicl(log2Up(elementsPerBlock)-1, 0)
          val round = Mux(niclLSBs =/= UInt(0), UInt(elementsPerBlock), UInt(0))
          val niclOffset = niclMSBs + round

          val niplMSBs =
            table(tIdx).nodesInCurrentLayer(15, log2Up(elementsPerBlock)) ##
          UInt(0, width=log2Up(elementsPerBlock))
          val niplLSBs = table(tIdx).nodesInCurrentLayer(log2Up(elementsPerBlock)-1,0)
          val niplOffset = niplMSBs + Mux(niplLSBs =/= UInt(0),
            UInt(elementsPerBlock), UInt(0))
          table(tIdx).waiting := Bool(false)
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
      val inLastNew = table(tIdx).currentLayer === (table(tIdx).numLayers - UInt(1))
      when (!inLastOld) {
        table(tIdx).waiting := Bool(false)
      } .otherwise {
        table(tIdx).decInUse := Bool(true)
        table(tIdx).waiting := Bool(false)
      }
      printfInfo("  inFirst/inLast: 0x%x/0x%x->0x%x\n",
        table(tIdx).inFirst, inLastOld, inLastNew)
    }
  }

  when (isPeReq) {
    val tIdx = entryArbiter.io.out.bits.tableIndex
    val inLastNode = table(tIdx).currentNodeInLayer ===
      table(tIdx).nodesInCurrentLayer - UInt(1)
    val notInLastLayer = table(tIdx).currentLayer <
      (table(tIdx).numLayers - UInt(1))
    when(inLastNode && notInLastLayer) {
      table(tIdx).needsLayerInfo := Bool(true)
      table(tIdx).currentLayer := table(tIdx).currentLayer + UInt(1)
      table(tIdx).regFileLocationBit := !table(tIdx).regFileLocationBit
    } .otherwise {
      table(tIdx).needsLayerInfo := Bool(false)
      table(tIdx).currentLayer := table(tIdx).currentLayer
    }
    table(tIdx).inFirst := table(tIdx).currentLayer === UInt(0)
  }

}

class DanaTransactionTableLearn(implicit p: Parameters)
    extends DanaTransactionTableBase[TransactionStateLearn, ControlReqLearn](
  Vec(p(TransactionTableNumEntries), new TransactionStateLearn),
    new ControlReqLearn)(p) {
  override lazy val io = new DanaTransactionTableInterfaceLearn

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
      is (e_TTABLE_WRITE_REG_BATCH_ITEMS) {  e.numBatchItems := cmd.regValue }
      is (e_TTABLE_WRITE_REG_LEARNING_RATE) { e.learningRate := cmd.regValue }
      is (e_TTABLE_WRITE_REG_WEIGHT_DECAY_LAMBDA) { e.lambda := cmd.regValue }
    }
    printfInfo("DANA TTable: saw reg write TID/Reg/Value 0x%x/0x%x/0x%x\n",
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
        entry.needsInputs := Bool(false)
        entry.needsOutputs := Bool(true)
      }
      printfInfo("DANA TTable:     (transactionType:0x%x)\n",
        transactionType)
    }

    // The learning variant needs to set certain fields when it exits
    // the "needsInputs" state
    val isLast = io.arbiter.xfQueue.in.bits.funct === UInt(t_USR_WRITE_DATA_LAST)
    val numInputs = entry.indexElement
    val numInputsMSBs = numInputs(log2Up(regFileNumElements) - 1,
      log2Up(elementsPerBlock)) ## UInt(0, width=log2Up(elementsPerBlock))
    val numInputsOffset = numInputsMSBs + UInt(elementsPerBlock)
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
        io.arbiter.xfResp.tidx.valid := Bool(true)
        io.arbiter.xfResp.flags.set("vi")
        entry.validIO := Bool(false)
        printfInfo("DANA TTable: Entry 0d%d needs OUTPUTS, but In Queue not ready\n",
          ioArbiter.chosen)
      } .otherwise {
        // io.arbiter.xfResp.tidx.valid := Bool(true)
        // io.arbiter.xfResp.flags.set("v")
        io.arbiter.xfQueue.in.ready := Bool(true)
        io.regFile.req.valid := Bool(true)
        io.regFile.req.bits.reqType := e_TTABLE_REGFILE_WRITE
        io.regFile.req.bits.tidIdx := tidIdx
        io.regFile.req.bits.addr := entry.indexElement
        val data = io.arbiter.xfQueue.in.bits.rs2
        io.regFile.req.bits.data := data
        printfInfo("DANA TTable: T0d%d got (E[OUTPUT]:0x%x) from queue\n",
          ioArbiter.chosen, data)

        when (isLast) {
          val nextIndexBlock = (entry.indexElement(
            log2Up(regFileNumElements)-1,log2Up(elementsPerBlock)) ##
            UInt(0, width=log2Up(elementsPerBlock))) + UInt(elementsPerBlock)
          entry.indexElement := nextIndexBlock
          entry.needsInputs := Bool(true)
          entry.needsOutputs := Bool(false)
          entry.regFileAddrInFixed := nextIndexBlock
          entry.regFileAddrOut := nextIndexBlock
          entry.numOutputs := entry.indexElement + UInt(1)
          printfInfo("DANA TTable: Saving numOutputs 0d%d\n", entry.indexElement)
        } .otherwise {
          entry.indexElement := entry.indexElement + UInt(1)
        }
      }
    }

    when (entry.flags.done) {
      val entry = table(ioArbiter.chosen)
      val finished = io.arbiter.xfQueue.out.ready & Mux(
        entry.transactionType === e_TTYPE_FEEDFORWARD,
        entry.readIdx === (entry.nodesInCurrentLayer - UInt(1)),
        entry.readIdx === (entry.numOutputs - UInt(1)))
      when (finished & (entry.stateLearn =/= e_TTABLE_STATE_LOAD_OUTPUTS)) {
        // This is an "I'm done" response to XF which indicates that all
        // outputs have been read
        io.arbiter.xfResp.tidx.valid := Bool(true)
        io.arbiter.xfResp.flags.set("d")
        entry.flags.valid := Bool(false)
        entry.flags.reserved := Bool(false)
        entry.flags.done := Bool(false)
      }

      when (finished & entry.stateLearn === e_TTABLE_STATE_LOAD_OUTPUTS) {
        entry.flags.valid := Bool(false)
        entry.flags.done := Bool(false)
        entry.needsOutputs := Bool(true)
        printfInfo("DANA TTable: Learn TX batch done\n")
      }

      printfInfo("DANA TTable: entry.flags.done asserted\n")
      printfInfo("DANA TTable:   finished: %d\n", finished)
      printfInfo("DANA TTable:   entry.readIdx: %d\n", entry.readIdx)
      printfInfo("DANA TTable:   entry.numOutputs: %d\n", entry.numOutputs)
    }
  }

  // Update the table when we get a request from DANA
  when (io.control.resp.valid) {
    val tIdx = io.control.resp.bits.tableIndex
    when (io.control.resp.bits.cacheValid) {
      switch(io.control.resp.bits.field) {
        is(e_TTABLE_CACHE_VALID) {
          table(tIdx).globalWtptr := io.control.resp.bits.globalWtptr
          when (table(tIdx).transactionType === e_TTYPE_BATCH) {
            table(tIdx).numNodes := io.control.resp.bits.data(1) * UInt(2)
          }
          table(tIdx).errorFunction := io.control.resp.bits.data(2)(
            errorFunctionWidth - 1, 0)
          table(tIdx).numWeightBlocks := io.control.resp.bits.data(5)
          printfInfo("  error function:          0x%x\n",
            io.control.resp.bits.data(2)(
              errorFunctionWidth - 1, 0))
          printfInfo("  learning rate:           0x%x (NOT SET)\n",
            io.control.resp.bits.data(3))
          printfInfo("  lambda:                  0x%x (NOT SET)\n",
            io.control.resp.bits.data(4))
          printfInfo("  Totalweightblocks :      0x%x\n",
            io.control.resp.bits.data(5))
          printfInfo("  Global Weight Pointer :  0x%x\n",
            io.control.resp.bits.globalWtptr)
        }
        is(e_TTABLE_LAYER) {
          val nicl = io.control.resp.bits.data(0)
          val niclMSBs = // Nodes in previous layer MSBs [TODO] fragile
            nicl(15, log2Up(elementsPerBlock)) ##
              UInt(0, width=log2Up(elementsPerBlock))
          val niclLSBs = // Nodes in previous layer LSBs
            nicl(log2Up(elementsPerBlock)-1, 0)
          val round = Mux(niclLSBs =/= UInt(0), UInt(elementsPerBlock), UInt(0))
          val niclOffset = niclMSBs + round

          val nipl = io.control.resp.bits.data(1)
          val niplMSBs = nipl(15, log2Up(elementsPerBlock)) ##
              UInt(0, width=log2Up(elementsPerBlock))
          val niplLSBs = nipl(log2Up(elementsPerBlock)-1,0)
          val niplOffset = niplMSBs + Mux(niplLSBs =/= UInt(0),
            UInt(elementsPerBlock), UInt(0))

          printfInfo("  nicl:             0x%x\n", nicl)
          printfInfo("  niclOffset:       0x%x\n", niclOffset)
          printfInfo("  nipl:             0x%x\n", nipl)
          printfInfo("  niplOffset:       0x%x\n", niplOffset)
          when ((table(tIdx).currentLayer === UInt(0)) &&
            (table(tIdx).stateLearn === e_TTABLE_STATE_LEARN_FEEDFORWARD ||
              table(tIdx).stateLearn === e_TTABLE_STATE_FEEDFORWARD)) {
            table(tIdx).waiting := Bool(false)
          }
          switch(table(tIdx).stateLearn) {
            is(e_TTABLE_STATE_FEEDFORWARD){
              table(tIdx).regFileAddrIn := table(tIdx).regFileAddrOut
              table(tIdx).regFileAddrOut := table(tIdx).regFileAddrOut +
                niplOffset
              table(tIdx).regFileAddrOutFixed :=
                table(tIdx).regFileAddrOut + niplOffset
              // nodesInLast can be blindly set during non-learning
              // feedforward mode
              table(tIdx).nodesInLast := nicl
            }
            is(e_TTABLE_STATE_LEARN_FEEDFORWARD){
              val regFileAddrOut = table(tIdx).regFileAddrOut + niplOffset
              table(tIdx).regFileAddrIn := table(tIdx).regFileAddrOut
              table(tIdx).regFileAddrOut := regFileAddrOut
              table(tIdx).regFileAddrOutFixed := regFileAddrOut
              table(tIdx).regFileAddrDW := regFileAddrOut + niclOffset

              // Update the number of total nodes in the network
              when (table(tIdx).currentLayer === UInt(0)) { // In first layer
                table(tIdx).numNodes := table(tIdx).numNodes + nicl
              } .elsewhen (table(tIdx).inLastEarly) {        // in the last layer
                table(tIdx).numNodes := table(tIdx).numNodes + nicl
              } .otherwise {                                // not first or last
                table(tIdx).numNodes := table(tIdx).numNodes + nicl * UInt(2)
              }

              // The bias offset is the size of the bias region
              val offsetBias = table(tIdx).offsetBias + niclOffset
              table(tIdx).offsetBias := offsetBias
              val biasAddr = regFileAddrOut + table(tIdx).offsetBias +
                table(tIdx).offsetDW + niclOffset
              table(tIdx).biasAddr := biasAddr
              table(tIdx).regFileAddrSlope := biasAddr + niclOffset
              printfInfo("  offsetBias:       0x%x\n",
                table(tIdx).offsetBias + niclOffset)
              // The DW offset is the size of the DW region
              when (!table(tIdx).inLastEarly) {
                table(tIdx).offsetDW := table(tIdx).offsetDW + niclOffset
                printfInfo("  offsetDW:         0x%x\n",
                  table(tIdx).offsetDW + niclOffset)
              }

              // Store the number of nodes in the output layer for future use
              when (table(tIdx).inLastEarly) {
                table(tIdx).nodesInLast := nicl
              }

              printfInfo("  offsetDW:         0x%x\n", table(tIdx).offsetDW)
              printfInfo("  regFileAddrDw:    0x%x -> 0x%x\n",
                table(tIdx).regFileAddrDW, regFileAddrOut + niclOffset)
              printfInfo("  regFileAddrSlope: 0x%x\n", biasAddr + niclOffset)
              printfInfo("  biasAddr:         0x%x\n", biasAddr)
            }
            is(e_TTABLE_STATE_LEARN_ERROR_BACKPROP){
              table(tIdx).regFileAddrOut := table(tIdx).regFileAddrDW
              table(tIdx).regFileAddrDW := table(tIdx).regFileAddrDW +
                niclOffset

              // Handle special case of being in the last hidden
              // layer. Also, setup the Auxiliary address which, in
              // this state, is used to store the address of the
              // previous layer's inputs
              when (table(tIdx).currentLayer === table(tIdx).numLayers - UInt(2)) {
                val regFileAddrIn = table(tIdx).regFileAddrIn
                val regFileAddrAux = regFileAddrIn - niplOffset
                //address to read outputs to compute derivative
                table(tIdx).regFileAddrIn := regFileAddrIn
                table(tIdx).regFileAddrAux := regFileAddrAux
                printfInfo("  regFileAddrIn:    0x%x\n", regFileAddrIn)
                printfInfo("  regFileAddrAux:   0x%x\n", regFileAddrAux)
              } .otherwise {
                val regFileAddrIn = table(tIdx).regFileAddrIn - niclOffset
                val regFileAddrAux = regFileAddrIn - niplOffset
                table(tIdx).regFileAddrIn := regFileAddrIn
                table(tIdx).regFileAddrAux := regFileAddrAux
                printfInfo("  regFileAddrIn:    0x%x\n", regFileAddrIn)
                printfInfo("  regFileAddrAux:   0x%x\n", regFileAddrAux)
              }

              // [TODO] Check that this is working
              table(tIdx).biasAddr := table(tIdx).biasAddr - niclOffset
              printfInfo("  offsetBias:       0x%x\n", table(tIdx).offsetBias)
              printfInfo("  offsetDW:         0x%x\n", table(tIdx).offsetDW)
              printfInfo("  regFileAddrDw:    0x%x -> 0x%x\n",
                table(tIdx).regFileAddrDW, table(tIdx).regFileAddrDW + niclOffset)
              printfInfo("  regFileAddrSlope: 0x%x\n",
                table(tIdx).regFileAddrSlope)
              printfInfo("  biasAddr:         0x%x\n",
                table(tIdx).regFileAddrDW - niclOffset)

            }
            is(e_TTABLE_STATE_LEARN_WEIGHT_UPDATE){
              when(table(tIdx).transactionType === e_TTYPE_BATCH){
                printfInfo("DANA TTable Layer Update, state == LEARN_WEIGHT_UPDATE\n")
                when (table(tIdx).currentLayer === UInt(0)){
                  table(tIdx).regFileAddrDW := table(tIdx).regFileAddrInFixed
                  table(tIdx).regFileAddrIn := table(tIdx).regFileAddrInFixed +
                    niclOffset

                  // If we're in the first layer, then we need to go
                  // ahead and update the slope address. We can
                  // compute this because we know both the slope
                  // offset (which is the offset from the bias region
                  // to the start of the weight update region).
                  table(tIdx).biasAddr := table(tIdx).regFileAddrSlope -
                    table(tIdx).offsetBias
                  printfInfo("  regFileAddrDw:   0x%x\n",
                    table(tIdx).regFileAddrInFixed)
                  printfInfo("  regFileAddrIn:   0x%x\n",
                    table(tIdx).regFileAddrInFixed + niclOffset)
                }.otherwise{
                  table(tIdx).regFileAddrDW := table(tIdx).regFileAddrIn
                  table(tIdx).regFileAddrIn := table(tIdx).regFileAddrIn + niclOffset
                  table(tIdx).biasAddr := table(tIdx).biasAddr + niplOffset
                  printfInfo("  regFileAddrDw:   0x%x\n",
                    table(tIdx).regFileAddrIn)
                  printfInfo("  regFileAddrIn:   0x%x\n",
                    table(tIdx).regFileAddrInFixed + niclOffset)
                  printfInfo("  biasAddr:        0x%x\n",
                    table(tIdx).biasAddr + niplOffset)
                }
              }.otherwise{
                table(tIdx).regFileAddrDW := table(tIdx).regFileAddrIn
                table(tIdx).regFileAddrIn := table(tIdx).regFileAddrIn + niplOffset
              }
            }
          }
          printfInfo("  inFirst/inLast/inLastEarly: 0x%x/0x%x/0x%x\n",
            table(tIdx).inFirst, table(tIdx).inLast, table(tIdx).inLastEarly)
        }
      }
    }
    when (io.control.resp.bits.layerValid) {
      val tIdx = io.control.resp.bits.layerValidIndex
      val inLastOld = table(tIdx).inLast
      val inLastNew = table(tIdx).currentLayer === (table(tIdx).numLayers - UInt(1))
      val inFirst = table(tIdx).currentLayer === UInt(0)
      switch (table(tIdx).transactionType) {
        is (e_TTYPE_FEEDFORWARD) {
          when (!inLastOld) {
            table(tIdx).waiting := Bool(false)
          } .otherwise {
            table(tIdx).decInUse := Bool(true)
            table(tIdx).waiting := Bool(false)
          }
        }
        is (e_TTYPE_INCREMENTAL) {
          when (inFirst &&
            table(tIdx).stateLearn === e_TTABLE_STATE_LEARN_WEIGHT_UPDATE) {
            table(tIdx).decInUse := Bool(true)
            table(tIdx).waiting := Bool(false)
          } .otherwise {
            table(tIdx).waiting := Bool(false)
          }
        }
        is (e_TTYPE_BATCH) {
          when (inLastOld &&
            table(tIdx).currentLayer === (table(tIdx).numLayers - UInt(1)) &&
            table(tIdx).stateLearn === e_TTABLE_STATE_LEARN_WEIGHT_UPDATE) {
            table(tIdx).waiting := Bool(false)
            table(tIdx).decInUse := Bool(true)
          } .elsewhen (table(tIdx).stateLearn === e_TTABLE_STATE_LOAD_OUTPUTS) {
            table(tIdx).indexElement := UInt(0)
            table(tIdx).flags.done := Bool(true)
            table(tIdx).flags.valid := Bool(false)
          } .otherwise {
            table(tIdx).waiting := Bool(false)
          }
        }
      }
      printfInfo("  inFirst/inLast/inLastEarly/state: 0x%x/0x%x->0x%x/0x%x/0x%x\n",
        table(tIdx).inFirst, inLastOld, inLastNew, table(tIdx).inLastEarly,
        table(tIdx).stateLearn)
    }
  }

  for (i <- 0 until transactionTableNumEntries) {
    entryArbiter.io.in(i).bits.globalWtptr := table(i).globalWtptr
    entryArbiter.io.in(i).bits.transactionType := table(i).transactionType
    entryArbiter.io.in(i).bits.batchFirst := table(i).curBatchItem === UInt(0)
    entryArbiter.io.in(i).bits.errorFunction := table(i).errorFunction
    entryArbiter.io.in(i).bits.learningRate := table(i).learningRate
    entryArbiter.io.in(i).bits.lambda := table(i).lambda
    entryArbiter.io.in(i).bits.numWeightBlocks := table(i).numWeightBlocks
    entryArbiter.io.in(i).bits.regFileAddrDW := table(i).regFileAddrDW
    entryArbiter.io.in(i).bits.regFileAddrSlope := table(i).regFileAddrSlope
    entryArbiter.io.in(i).bits.regFileAddrBias := table(i).biasAddr
    entryArbiter.io.in(i).bits.regFileAddrAux := table(i).regFileAddrAux
    entryArbiter.io.in(i).bits.stateLearn := table(i).stateLearn
    entryArbiter.io.in(i).bits.inLastEarly := table(i).inLastEarly
  }

  when (isPeReq) {
    val tIdx = entryArbiter.io.out.bits.tableIndex
    val inLastNode = table(tIdx).currentNodeInLayer ===
      table(tIdx).nodesInCurrentLayer - UInt(1)
    val notInLastLayer = table(tIdx).currentLayer <
      (table(tIdx).numLayers - UInt(1))

    // If we're at the end of a layer, we need new layer information
    // The comparison here differs from how this is handled in
    // nn_instruction.v.
    switch(table(tIdx).stateLearn){
      is(e_TTABLE_STATE_FEEDFORWARD){
        when(inLastNode && notInLastLayer) {
          table(tIdx).needsLayerInfo := Bool(true)
          table(tIdx).currentLayer := table(tIdx).currentLayer + UInt(1)
          table(tIdx).regFileLocationBit := !table(tIdx).regFileLocationBit
        } .otherwise {
          table(tIdx).needsLayerInfo := Bool(false)
          table(tIdx).currentLayer := table(tIdx).currentLayer
        }
        table(tIdx).inFirst := table(tIdx).currentLayer === UInt(0)
      }
      is(e_TTABLE_STATE_LEARN_FEEDFORWARD){
        when(table(tIdx).inLast && inLastNode){
          table(tIdx).needsLayerInfo := Bool(true)
          table(tIdx).currentLayer := table(tIdx).currentLayer - UInt(1)
          table(tIdx).regFileLocationBit := !table(tIdx).regFileLocationBit
          table(tIdx).inFirst := table(tIdx).currentLayer === UInt(1)
          table(tIdx).inLastEarly := Bool(true)
          table(tIdx).stateLearn := e_TTABLE_STATE_LEARN_ERROR_BACKPROP
        } .elsewhen (inLastNode && notInLastLayer) {
          table(tIdx).needsLayerInfo := Bool(true)
          table(tIdx).currentLayer := table(tIdx).currentLayer + UInt(1)
          table(tIdx).regFileLocationBit := !table(tIdx).regFileLocationBit
          table(tIdx).inFirst := table(tIdx).currentLayer === UInt(0)

          // inLastEarly will assert as soon as the last PE Request goes
          // out. This is useful if you need something that goes high at
          // the earliest possible definition of "being in the last
          // layer", e.g., when generating a request for the next layer
          // information.
          table(tIdx).inLastEarly :=
            table(tIdx).currentLayer === (table(tIdx).numLayers - UInt(2))
        } .otherwise {
          table(tIdx).needsLayerInfo := Bool(false)
          table(tIdx).currentLayer := table(tIdx).currentLayer
          table(tIdx).inFirst := table(tIdx).currentLayer === UInt(0)
        }
      }
      is(e_TTABLE_STATE_LEARN_ERROR_BACKPROP){
        when(table(tIdx).inFirst && inLastNode) {
          // table(tIdx).needsLayerInfo := Bool(true)
          table(tIdx).needsLayerInfo := Bool(false)
          // table(tIdx).currentLayer := table(tIdx).currentLayer + UInt(1)
          table(tIdx).currentLayer := UInt(0)
          table(tIdx).regFileLocationBit := !table(tIdx).regFileLocationBit
          when(table(tIdx).transactionType === e_TTYPE_BATCH){
            when (table(tIdx).curBatchItem === (table(tIdx).numBatchItems - UInt(1))) {
              table(tIdx).needsLayerInfo := Bool(true)
              table(tIdx).stateLearn := e_TTABLE_STATE_LEARN_WEIGHT_UPDATE
            } .otherwise {
              table(tIdx).stateLearn := e_TTABLE_STATE_LOAD_OUTPUTS
              table(tIdx).curBatchItem := table(tIdx).curBatchItem + UInt(1)
            }
          }.otherwise{
            // [TODO] Related to a fix for #54
            table(tIdx).stateLearn := e_TTABLE_STATE_LEARN_WEIGHT_UPDATE
          }
          table(tIdx).inFirst := Bool(false)
          table(tIdx).inLastEarly :=
            table(tIdx).currentLayer === (table(tIdx).numLayers - UInt(2))
        } .elsewhen(inLastNode && (table(tIdx).currentLayer > UInt(0))) {
          table(tIdx).needsLayerInfo := Bool(true)
          table(tIdx).currentLayer := table(tIdx).currentLayer - UInt(1)
          table(tIdx).regFileLocationBit := !table(tIdx).regFileLocationBit
          table(tIdx).inFirst := table(tIdx).currentLayer === UInt(1)
        } .otherwise {
          table(tIdx).needsLayerInfo := Bool(false)
          table(tIdx).currentLayer := table(tIdx).currentLayer
        }
      }
      is (e_TTABLE_STATE_LEARN_WEIGHT_UPDATE) {
        // when(table(tIdx).transactionType === e_TTYPE_INCREMENTAL){
        when(table(tIdx).inLast && inLastNode){
            table(tIdx).waiting := Bool(true)
          table(tIdx).currentLayer := table(tIdx).currentLayer
        } .elsewhen(inLastNode && notInLastLayer) {
          table(tIdx).needsLayerInfo := Bool(true)
          table(tIdx).currentLayer := table(tIdx).currentLayer + UInt(1)
          table(tIdx).regFileLocationBit := !table(tIdx).regFileLocationBit
        } .otherwise {
          table(tIdx).needsLayerInfo := Bool(false)
          table(tIdx).currentLayer := table(tIdx).currentLayer
          table(tIdx).inLastEarly :=
          table(tIdx).currentLayer === (table(tIdx).numLayers - UInt(2))
        }
      }
    }
  }

  // Catch any jumps to an error state
  (0 until transactionTableNumEntries).map(i =>
    assert(!((table(i).flags.valid || table(i).flags.reserved) &&
      table(i).stateLearn === e_TTABLE_STATE_ERROR),
      "DANA TTable Transaction is in error state"))
}
