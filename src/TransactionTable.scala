package dana

import Chisel._

class TransactionState(
  val elementsPerBlock: Int = 4,
  val cacheNumEntries: Int = 4,
  val tidWidth: Int = 16,
  val nnidWidth: Int = 16,
  val decimalPointWidth: Int = 3,
  val feedbackWidth: Int = 12,
  val regFileNumElements: Int = 256
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
    regFileNumElements = regFileNumElements)().asInstanceOf[this.type]
  val valid = Reg(Bool(), init = Bool(false))
  val reserved = Reg(Bool(), init = Bool(false))
  val cacheValid = Reg(Bool())
  val waitingForCache = Reg(Bool())
  val needsLayerInfo = Reg(Bool())
  val needsRegisters = Reg(Bool())
  val done = Reg(Bool())
  val request = Reg(Bool())
  val noWorkToDo = Reg(Bool())
  val inLast = Reg(Bool())
  // output_layer should be unused according to types.vh
  val cacheIndex = Reg(UInt(width = log2Up(cacheNumEntries)))
  val tid = Reg(UInt(width = tidWidth)) // formerly pid
  val nnid = Reg(UInt(width = nnidWidth)) // formerly nn_hash
  val decimalPoint = Reg(UInt(width = decimalPointWidth))
  val numLayers = Reg(UInt(width = 16)) // [TODO] fragile
  val numNodes = Reg(UInt(width = 16)) // [TODO] fragile
  val currentNode = Reg(UInt(width = 16)) // [TODO] fragile
  val currentNodeInLayer = Reg(UInt(width = 16)) // [TODO] fragile
  val nodesInCurrentLayer = Reg(UInt(width = 16)) // [TODO] fragile
  val nodesInNextLayer = Reg(UInt(width = 16)) // [TODO] fragile
  val neuronPointer = Reg(UInt(width = 11)) // [TODO] fragile
  val regBlockIndexOut = Reg(UInt(width=log2Up(regFileNumElements)))
  val regBlockIndexIn = Reg(UInt(width=log2Up(regFileNumElements)))
  val regBlockInNext = Reg(UInt(width = log2Up(regFileNumBlocks)))
  val countUsedRegisters = Reg(UInt(width = log2Up(elementsPerBlock)))
  val countFeedback = Reg(UInt(width = feedbackWidth))
}

class XFilesArbiterReq(
  val tidWidth: Int = 16,
  val nnidWidth: Int = 16
) extends Bundle {
  override def clone = new XFilesArbiterReq(
    tidWidth = tidWidth,
    nnidWidth = nnidWidth).asInstanceOf[this.type]
  val tid = UInt(width = tidWidth)
  val nnid = UInt(width = nnidWidth)
}

class XFilesArbiterInterface(
  val tidWidth: Int = 16,
  val nnidWidth: Int = 16
) extends Bundle {
  val req = Decoupled(new XFilesArbiterReq(
    tidWidth = tidWidth,
    nnidWidth = nnidWidth)).flip
}

class TransactionTableInterface(
  val tidWidth: Int = 16,
  val nnidWidth: Int = 16
) extends Bundle {
  override def clone = new TransactionTableInterface(
    tidWidth = tidWidth,
    nnidWidth = nnidWidth).asInstanceOf[this.type]
  val arbiter = new XFilesArbiterInterface(
    tidWidth = tidWidth,
    nnidWidth = nnidWidth)
}

class TransactionTable(
  val elementWidth: Int = 32,
  val elementsPerBlock: Int = 4,
  val tidWidth: Int = 16,
  val transactionTableNumEntries: Int = 4,
  val cacheNumEntries: Int = 4,
  val nnidWidth: Int = 16,
  val decimalPointWidth: Int = 3,
  val feedbackWidth: Int = 12,
  val regFileNumBlocks: Int = 256
) extends DanaModule {
  // Communication with the X-FILES arbiter
  val io = new TransactionTableInterface(
    tidWidth = tidWidth,
    nnidWidth = nnidWidth)

  // Vector of all the table entries
  val table = Vec.fill(transactionTableNumEntries){new TransactionState(
    cacheNumEntries = cacheNumEntries,
    tidWidth = tidWidth,
    nnidWidth = nnidWidth,
    decimalPointWidth = decimalPointWidth,
    feedbackWidth = feedbackWidth)()}

  // Compute the next available free entry
  val nextFree = Reg(UInt(0, width = transactionTableNumEntries))
  // val hasFree = Reg(Bool())
  val hasFree = Bool()
  // hasFree := Bool(false)
  // nextFree := UInt(0)
  // for (i <- 0 until table.length)
  //   when (!table(i).valid && !table(i).reserved) {
  //     nextFree := UInt(i)
  //     hasFree := Bool(true)
  //   }
  table.indexWhere(_.valid)
  io.arbiter.req.ready := hasFree

  when (io.arbiter.req.valid) {
    table(nextFree).reserved := Bool(true)
  }

  // Assertions
  assert(!io.arbiter.req.valid || io.arbiter.req.ready,
    "Inbound arbiter request when Transaction Table not ready")
}

class TransactionTableTests(uut: TransactionTable, isTrace: Boolean = true)
    extends DanaTester(uut, isTrace) {
  for (t <- 0 until 4) {
    peek(uut.hasFree)
    peek(uut.nextFree)
    val tid = rnd.nextInt(16)
    val nnid = rnd.nextInt(16)
    step (1)
    poke(uut.io.arbiter.req.valid, 1)
    poke(uut.io.arbiter.req.bits.tid, tid)
    poke(uut.io.arbiter.req.bits.nnid, nnid)
  }
}
