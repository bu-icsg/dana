package dana

import Chisel._

class TransactionState(
  val elementsPerBlock: Int,
  val cacheNumEntries: Int,
  val tidWidth: Int,
  val nnidWidth: Int,
  val decimalPointWidth: Int,
  val feedbackWidth: Int,
  val regFileNumElements: Int,
  val transactionTableSramElements: Int
)(
  val regFileNumBlocks: Int = regFileNumElements / elementsPerBlock
) extends Bundle {
  override def clone = new TransactionState(
    elementsPerBlock = elementsPerBlock,
    cacheNumEntries = cacheNumEntries,
    tidWidth = tidWidth,
    nnidWidth = nnidWidth,
    decimalPointWidth = decimalPointWidth,
    feedbackWidth = feedbackWidth,
    regFileNumElements = regFileNumElements,
    transactionTableSramElements = transactionTableSramElements
  )().asInstanceOf[this.type]
  val valid = Reg(Bool(), init = Bool(false))
  val reserved = Reg(Bool(), init = Bool(false))
  val cacheValid = Reg(Bool())
  val waitingForCache = Reg(Bool())
  val needsLayerInfo = Reg(Bool())
  val needsRegisters = Reg(Bool())
  val needsNextRegister = Reg(Bool())
  val done = Reg(Bool())
  val request = Reg(Bool())
  val workToDo = Reg(Bool())
  val inLast = Reg(Bool())
  // output_layer should be unused according to types.vh
  val cacheIndex = Reg(UInt(width = log2Up(cacheNumEntries)))
  val tid = Reg(UInt(width = tidWidth)) // formerly pid
  val nnid = Reg(UInt(width = nnidWidth)) // formerly nn_hash
  val decimalPoint = Reg(UInt(width = decimalPointWidth))
  val numLayers = Reg(UInt(width = 16)) // [TODO] fragile
  val numNodes = Reg(UInt(width = 16)) // [TODO] fragile
  val currentNode = Reg(SInt(width = 16)) // [TODO] fragile
  val currentNodeInLayer = Reg(UInt(width = 16)) // [TODO] fragile
  val currentLayer = Reg(UInt(width = 16)) // [TODO] fragile
  val nodesInCurrentLayer = Reg(UInt(width = 16)) // [TODO] fragile
  val nodesInNextLayer = Reg(UInt(width = 16)) // [TODO] fragile
  val neuronPointer = Reg(UInt(width = 11)) // [TODO] fragile
  val regBlockIndexOut = Reg(UInt(width=log2Up(regFileNumElements)))
  val regBlockIndexIn = Reg(UInt(width=log2Up(regFileNumElements)))
  val regBlockInNext = Reg(UInt(width = log2Up(regFileNumBlocks)))
  val countUsedRegisters = Reg(UInt(width = log2Up(elementsPerBlock)))
  val countFeedback = Reg(UInt(width = feedbackWidth))
  // Additional crap which may be redundant
  val indexElement = Reg(UInt(width = log2Up(transactionTableSramElements)))
}

class DanaReq(
  val elementsPerBlock: Int,
  val cacheNumEntries: Int,
  val nnidWidth: Int,
  val tidWidth: Int,
  val regFileNumElements: Int,
  val decimalPointWidth: Int
)(
  val regFileNumBlocks: Int = regFileNumElements / elementsPerBlock
)extends Bundle {
  override def clone = new DanaReq(
    elementsPerBlock = elementsPerBlock,
    cacheNumEntries = cacheNumEntries,
    nnidWidth = nnidWidth,
    tidWidth = tidWidth,
    regFileNumElements = regFileNumElements,
    decimalPointWidth = decimalPointWidth
  )().asInstanceOf[this.type]
  // Bools
  val cacheValid = Bool()
  val waitingForCache = Bool()
  val needsLayerInfo = Bool()
  val needsRegisters = Bool()
  val needsNextRegister = Bool()
  val request = Bool()
  val inLast = Bool()
  // Global info
  val cacheIndex = UInt(width = log2Up(cacheNumEntries))
  val nnid = UInt(width = nnidWidth) // formerly nn_hash
  val tid = UInt(width = tidWidth) // formerly pid
  // State info
  val currentNode = SInt(width = 16) // [TODO] fragile
  val currentNodeInLayer = UInt(width = 16) // [TODO] fragile
  val currentLayer = UInt(width = 16) // [TODO] fragile
  val nodesInCurrentLayer = UInt(width = 16) // [TODO] fragile
  val nodesInNextLayer = UInt(width = 16) // [TODO] fragile
  val regBlockIndexIn = UInt(width=log2Up(regFileNumElements))
  val regBlockIndexOut = UInt(width=log2Up(regFileNumElements))
  val regBlockInNext = UInt(width = log2Up(regFileNumBlocks))
  val neuronPointer = UInt(width = 11) // [TODO] fragile
  val decimalPoint = UInt(width = decimalPointWidth)
}

class DanaResp(
) extends Bundle {
}

class XFilesArbiterReq(
  val tidWidth: Int,
  val nnidWidth: Int,
  val bitsFeedback: Int
) extends Bundle {
  override def clone = new XFilesArbiterReq(
    tidWidth = tidWidth,
    nnidWidth = nnidWidth,
    bitsFeedback = bitsFeedback).asInstanceOf[this.type]
  val tid = UInt(width = tidWidth)
  val readOrWrite = Bool()
  val countFeedback = UInt(width = bitsFeedback)
  val isNew = Bool()
  val isLast = Bool()
  val data = UInt(width = 32) // [TODO] fragile
}

class XFilesArbiterInterface(
  val tidWidth: Int,
  val nnidWidth: Int,
  val bitsFeedback: Int
) extends Bundle {
  val req = Decoupled(new XFilesArbiterReq(
    tidWidth = tidWidth,
    nnidWidth = nnidWidth,
    bitsFeedback = bitsFeedback)).flip
}

class TTableDanaInterface(
  val elementsPerBlock: Int,
  val tidWidth: Int,
  val nnidWidth: Int,
  val cacheNumEntries: Int,
  val regFileNumElements: Int,
  val decimalPointWidth: Int
) extends Bundle {
  val danaReq = Decoupled(new DanaReq(
    elementsPerBlock = elementsPerBlock,
    tidWidth = tidWidth,
    nnidWidth = nnidWidth,
    cacheNumEntries = cacheNumEntries,
    regFileNumElements = regFileNumElements,
    decimalPointWidth = decimalPointWidth)())
}

class TransactionTableInterface(
  val elementsPerBlock: Int,
  val tidWidth: Int,
  val nnidWidth: Int,
  val bitsFeedback: Int,
  val cacheNumEntries: Int,
  val regFileNumElements: Int,
  val decimalPointWidth: Int
) extends Bundle {
  override def clone = new TransactionTableInterface(
    elementsPerBlock = elementsPerBlock,
    tidWidth = tidWidth,
    nnidWidth = nnidWidth,
    bitsFeedback = bitsFeedback,
    cacheNumEntries = cacheNumEntries,
    regFileNumElements = regFileNumElements,
    decimalPointWidth = decimalPointWidth
  ).asInstanceOf[this.type]
  val arbiter = new XFilesArbiterInterface(
    tidWidth = tidWidth,
    nnidWidth = nnidWidth,
    bitsFeedback = bitsFeedback)
  val dana = new TTableDanaInterface(
    elementsPerBlock = elementsPerBlock,
    tidWidth = tidWidth,
    nnidWidth = nnidWidth,
    cacheNumEntries = cacheNumEntries,
    regFileNumElements = regFileNumElements,
    decimalPointWidth = decimalPointWidth)
}

class TransactionTable extends DanaModule()() {
  // Communication with the X-FILES arbiter
  val io = new TransactionTableInterface(
    elementsPerBlock = elementsPerBlock,
    tidWidth = tidWidth,
    nnidWidth = nnidWidth,
    bitsFeedback = bitsFeedback,
    cacheNumEntries = cacheNumEntries,
    regFileNumElements = regFileNumElements,
    decimalPointWidth = decimalPointWidth
  )

  // Vector of all the table entries
  val table = Vec.fill(transactionTableNumEntries){new TransactionState(
    elementsPerBlock = elementsPerBlock,
    cacheNumEntries = cacheNumEntries,
    tidWidth = tidWidth,
    nnidWidth = nnidWidth,
    decimalPointWidth = decimalPointWidth,
    feedbackWidth = feedbackWidth,
    regFileNumElements = regFileNumElements,
    transactionTableSramElements = transactionTableSramElements
  )()}
  // Vector of the table entry memories
  val mem = Vec.fill(transactionTableNumEntries){
    Module(new SRAMElement(
      dataWidth = elementWidth * elementsPerBlock,
      sramDepth = transactionTableSramBlocks,
      numPorts = 1,
      elementWidth = elementWidth)).io}
  // An entry is free if it is not valid and not reserved
  def isFree(x: TransactionState): Bool = { !x.valid && !x.reserved }
  def derefTid(x: TransactionState, y: UInt): Bool = { x.tid === y }

  for (i <- 0 until transactionTableNumEntries) {
  }

  // Determine if there exits a free entry in the table and the index
  // of the next availble free entry
  val hasFree = Bool()
  val nextFree = UInt()
  val foundTid = Bool()
  val derefTidIndex = UInt()
  hasFree := table.exists(isFree)
  nextFree := table.indexWhere(isFree)
  foundTid := table.exists(derefTid(_, io.arbiter.req.bits.tid))
  derefTidIndex := table.indexWhere(derefTid(_, io.arbiter.req.bits.tid))
  io.arbiter.req.ready := hasFree

  // Default value assignment
  for (i <- 0 until transactionTableNumEntries) {
    mem(i).we(0) := Bool(false)
    mem(i).din(0) := UInt(0)
    mem(i).addr(0) := UInt(0)
  }
  when (io.arbiter.req.valid) {
    // This is a new packet
    when (io.arbiter.req.bits.readOrWrite) { // Write == True
      when (io.arbiter.req.bits.isNew) {
        table(nextFree).reserved := Bool(true)
        table(nextFree).cacheValid := Bool(false)
        table(nextFree).waitingForCache := Bool(false)
        table(nextFree).needsLayerInfo := Bool(true)
        table(nextFree).needsRegisters := Bool(true)
        table(nextFree).needsNextRegister := Bool(false)
        table(nextFree).workToDo := Bool(false)
        table(nextFree).tid := io.arbiter.req.bits.tid
        table(nextFree).nnid := io.arbiter.req.bits.data
        table(nextFree).currentNode := SInt(-1)
        table(nextFree).currentLayer := UInt(0)
        table(nextFree).request := Bool(false)
        table(nextFree).countFeedback := io.arbiter.req.bits.countFeedback
        table(nextFree).done := Bool(false)
        table(nextFree).indexElement := UInt(0)
      }
        .elsewhen(io.arbiter.req.bits.isLast) {
        mem(derefTidIndex).we(0) := Bool(true)
        mem(derefTidIndex).din(0) := io.arbiter.req.bits.data
        mem(derefTidIndex).addr(0) := table(derefTidIndex).indexElement
        table(derefTidIndex).indexElement :=
          table(derefTidIndex).indexElement + UInt(1)
        table(derefTidIndex).valid := Bool(true)
        table(derefTidIndex).workToDo := Bool(true)
      }
        // This is an input packet
        .otherwise {
        mem(derefTidIndex).we(0) := Bool(true)
        mem(derefTidIndex).din(0) := io.arbiter.req.bits.data
        mem(derefTidIndex).addr(0) := table(derefTidIndex).indexElement
        table(derefTidIndex).indexElement :=
          table(derefTidIndex).indexElement + UInt(1)
        // table(derefTidIndex).data() :=
      }
    }
  }

  // Round Robin Arbitration of Transaction Table entries. One of
  // these is passed out over an interface to DANA's control module.
  val entryArbiter = Module(new RRArbiter( new DanaReq(
    elementsPerBlock = elementsPerBlock,
    tidWidth = tidWidth,
    nnidWidth = nnidWidth,
    cacheNumEntries = cacheNumEntries,
    regFileNumElements = regFileNumElements,
    decimalPointWidth = decimalPointWidth)(),
    transactionTableNumEntries))
  // All of these need to be wired up manually as the internal
  // connections aren't IO
  for (i <- 0 until transactionTableNumEntries) {
    // A request is valid if it has work to do
    entryArbiter.io.in(i).valid := table(i).workToDo
    // The other data connections are just aliases to the contents of
    // the specific table entry
    entryArbiter.io.in(i).bits.cacheValid := table(i).cacheValid
    entryArbiter.io.in(i).bits.waitingForCache := table(i).waitingForCache
    entryArbiter.io.in(i).bits.needsLayerInfo := table(i).needsLayerInfo
    entryArbiter.io.in(i).bits.needsRegisters := table(i).needsRegisters
    entryArbiter.io.in(i).bits.needsNextRegister := table(i).needsNextRegister
    entryArbiter.io.in(i).bits.request := table(i).request
    entryArbiter.io.in(i).bits.inLast := table(i).inLast
    // Global info
    entryArbiter.io.in(i).bits.cacheIndex := table(i).cacheIndex
    entryArbiter.io.in(i).bits.nnid := table(i).nnid
    entryArbiter.io.in(i).bits.tid := table(i).tid
    // State info
    entryArbiter.io.in(i).bits.currentNode := table(i).currentNode
    entryArbiter.io.in(i).bits.currentNodeInLayer := table(i).currentNodeInLayer
    entryArbiter.io.in(i).bits.currentLayer := table(i).currentLayer
    entryArbiter.io.in(i).bits.nodesInCurrentLayer :=table(i).nodesInCurrentLayer
    entryArbiter.io.in(i).bits.nodesInNextLayer := table(i).nodesInNextLayer
    entryArbiter.io.in(i).bits.regBlockIndexIn := table(i).regBlockIndexIn
    entryArbiter.io.in(i).bits.regBlockIndexOut := table(i).regBlockIndexOut
    entryArbiter.io.in(i).bits.regBlockInNext := table(i).regBlockInNext
    entryArbiter.io.in(i).bits.neuronPointer := table(i).neuronPointer
    entryArbiter.io.in(i).bits.decimalPoint := table(i).decimalPoint
  }
  io.dana.danaReq <> entryArbiter.io.out

  // Assertions
  assert(!io.arbiter.req.valid || io.arbiter.req.ready,
    "Inbound arbiter request when Transaction Table not ready")
}

class TransactionTableTests(uut: TransactionTable, isTrace: Boolean = true)
    extends DanaTester(uut, isTrace) {
  // Functions
  def info() {
    printf("|V|R|CV|WC|NL|NR|D|Tid|Nnid| <- TTable\n")
    printf("----------------------------\n")
    for (i <- 0 until uut.table.length) {
      printf("|%d|%d|%2d|%2d|%2d|%2d|%s|%3d|%4x|",
        peek(uut.table(i).valid),
        peek(uut.table(i).reserved),
        peek(uut.table(i).cacheValid),
        peek(uut.table(i).waitingForCache),
        peek(uut.table(i).needsLayerInfo),
        peek(uut.table(i).needsRegisters),
        // peek(uut.table(i).done),
        "X",
        peek(uut.table(i).tid),
        peek(uut.table(i).nnid)
      )
      // for (j <- 0 until uut.transactionTableSramElements) {
      //   poke(uut.mem(i).we(0), j)
      //   poke(uut.mem(i).addr(0), j)
      //   step(1)
      //   printf("%6d|", peek(uut.mem(i).dout(0)).toInt)
      // }
      printf("\n")
    }
    printf("| hasFree: %d | nextFree: %d |\n",
      peek(uut.hasFree), peek(uut.nextFree))
    printf("\n");
  }
  def newWriteRequest(tid: Int, nnid: Int) {
    // Initiate a new request to DANA with the specified `tid` and
    // `nnid`
    poke(uut.io.arbiter.req.valid, 1)
    poke(uut.io.arbiter.req.bits.isNew, 1)
    poke(uut.io.arbiter.req.bits.readOrWrite, 1)
    poke(uut.io.arbiter.req.bits.isLast, 0)
    poke(uut.io.arbiter.req.bits.tid, tid)
    poke(uut.io.arbiter.req.bits.data, nnid)
    step(1)
    poke(uut.io.arbiter.req.valid, 0)
  }
  def writeRndData(tid: Int, nnid: Int, num: Int, decimal: Int) {
    // Send `num` data elements to DANA
    for (i <- 0 until num) {
      poke(uut.io.arbiter.req.valid, 1)
      poke(uut.io.arbiter.req.bits.isNew, 0)
      poke(uut.io.arbiter.req.bits.readOrWrite, 1)
      if (i == num - 1)
        poke(uut.io.arbiter.req.bits.isLast, 1)
      else
        poke(uut.io.arbiter.req.bits.isLast, 0)
      val data = rnd.nextInt(Math.pow(2, decimal + 2).toInt) -
        Math.pow(2, decimal + 1).toInt
      printf("[INFO] Input data: %d\n", data)
      poke(uut.io.arbiter.req.bits.data, data)
      step(1)
    }
  }

  for (t <- 0 until 3) {
    peek(uut.hasFree)
    peek(uut.nextFree)
    val tid = t
    val nnid = t + 15 * 16
    newWriteRequest(tid, nnid)
    writeRndData(tid, nnid, 5, 10)
    info()
    poke(uut.io.dana.danaReq.ready, 1)
    // uut.info()
    // printf("%s", uut.info())
  }
}
