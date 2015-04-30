package dana

import Chisel._

class TransactionState extends DanaBundle()() {
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

class DanaReq extends DanaBundle()() {
  // Bools
  val cacheValid = Bool()
  val waitingForCache = Bool()
  val needsLayerInfo = Bool()
  val needsRegisters = Bool()
  val needsNextRegister = Bool()
  val request = Bool()
  val inLast = Bool()
  // Global info
  val tableIndex = UInt(width = log2Up(transactionTableNumEntries))
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

class DanaResp extends DanaBundle()() {
  val tableIndex = UInt(width = log2Up(transactionTableNumEntries))
  val field = UInt(width = 4) // [TODO] fragile on Constants.scala
  val data = Vec.fill(3){UInt(width = 16)} // [TODO] fragile
  val decimalPoint = UInt(width = decimalPointWidth)
}

class XFilesArbiterReq extends DanaBundle()() {
  val tid = UInt(width = tidWidth)
  val readOrWrite = Bool()
  val countFeedback = UInt(width = bitsFeedback)
  val isNew = Bool()
  val isLast = Bool()
  val data = UInt(width = 32) // [TODO] fragile
}

class XFilesArbiterInterface extends DanaBundle()() {
  val req = Decoupled(new XFilesArbiterReq).flip
}

class TTableDanaInterface extends DanaBundle()() {
  val req = Decoupled(new DanaReq)
  val resp = Decoupled(new DanaResp).flip
}

class TransactionTableInterface extends DanaBundle()() {
  val arbiter = new XFilesArbiterInterface
  val dana = new TTableDanaInterface
}

class TransactionTable extends DanaModule()() {
  // Communication with the X-FILES arbiter
  val io = new TransactionTableInterface

  // Vector of all the table entries
  val table = Vec.fill(transactionTableNumEntries){new TransactionState}
  // Temporary debug enforcement
  for (i <- 0 until transactionTableNumEntries) {
    debug(table(i).numLayers)
    debug(table(i).numNodes)
    debug(table(i).cacheIndex)
  }
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

  // Update the table when we get a request from DANA
  when (io.dana.resp.valid) {
    // table(io.dana.resp.bits.tableIndex).waitingForCache := Bool(true)
    switch(io.dana.resp.bits.field) {
      is(e_TTABLE_WAITING_FOR_CACHE) {
        table(io.dana.resp.bits.tableIndex).waitingForCache := Bool(true)
      }
      is(e_TTABLE_CACHE_VALID) {
        table(io.dana.resp.bits.tableIndex).cacheValid := Bool(true)
        table(io.dana.resp.bits.tableIndex).numLayers :=
          io.dana.resp.bits.data(0)
        table(io.dana.resp.bits.tableIndex).numNodes :=
          io.dana.resp.bits.data(1)
        table(io.dana.resp.bits.tableIndex).cacheIndex :=
          io.dana.resp.bits.data(2)
        table(io.dana.resp.bits.tableIndex).decimalPoint :=
          io.dana.resp.bits.decimalPoint
      }
      is(e_TTABLE_DONE) {
        table(io.dana.resp.bits.tableIndex).cacheValid := Bool(true)
      }
    }
  }

  // Round Robin Arbitration of Transaction Table entries. One of
  // these is passed out over an interface to DANA's control module.
  val entryArbiter = Module(new RRArbiter( new DanaReq,
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
    entryArbiter.io.in(i).bits.tableIndex := UInt(i)
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
  io.dana.req <> entryArbiter.io.out

  // Assertions

  // The arbiter should only receive a request if it is asserting its
  // ready signal.
  assert(!io.arbiter.req.valid || io.arbiter.req.ready,
    "Inbound arbiter request when Transaction Table not ready")
  // Only one inbound request or response can currently be handled
  assert(!io.arbiter.req.valid || !io.dana.resp.valid,
    "Received simultaneous requests on the TransactionTable")
}

class TransactionTableTests(uut: TransactionTable, isTrace: Boolean = true)
    extends DanaTester(uut, isTrace) {
  for (t <- 0 until 3) {
    peek(uut.hasFree)
    peek(uut.nextFree)
    val tid = t
    val nnid = t + 15 * 16
    newWriteRequest(uut.io.arbiter, tid, nnid)
    writeRndData(uut.io.arbiter, tid, nnid, 5, 10)
    info(uut)
    poke(uut.io.dana.req.ready, 1)
  }
}
