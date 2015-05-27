package dana

import Chisel._

class TransactionState extends DanaBundle {
  val valid = Bool()
  val reserved = Bool()
  val cacheValid = Bool()
  val waiting = Bool()
  val needsLayerInfo = Bool()
  val needsRegisters = Bool()
  val needsNextRegister = Bool()
  val done = Bool()
  val request = Bool()
  val inFirst = Bool()
  val inLast = Bool()
  // output_layer should be unused according to types.vh
  val cacheIndex = UInt(width = log2Up(cacheNumEntries))
  val tid = UInt(width = tidWidth) // formerly pid
  val nnid = UInt(width = nnidWidth) // formerly nn_hash
  val decimalPoint = UInt(width = decimalPointWidth)
  val numLayers = UInt(width = 16) // [TODO] fragile
  val numNodes = UInt(width = 16) // [TODO] fragile
  val currentNode = UInt(width = 16) // [TODO] fragile
  val currentNodeInLayer = UInt(width = 16) // [TODO] fragile
  val currentLayer = UInt(width = 16) // [TODO] fragile
  val nodesInCurrentLayer = UInt(width = 16) // [TODO] fragile
  val nodesInNextLayer = UInt(width = 16) // [TODO] fragile
  val neuronPointer = UInt(width = 11) // [TODO] fragile
  val regBlockIndexOut = UInt(width=log2Up(regFileNumElements))
  val regBlockIndexIn = UInt(width=log2Up(regFileNumElements))
  val regBlockInNext = UInt(width = log2Up(regFileNumBlocks))
  val countUsedRegisters = UInt(width = log2Up(elementsPerBlock))
  val countFeedback = UInt(width = feedbackWidth)
  val countPeWrites = UInt(width = 16) // [TODO] fragile
  val readIdx = UInt(width = log2Up(transactionTableSramElements))
  // Additional crap which may be redundant
  val indexElement = UInt(width = log2Up(transactionTableSramElements))
}

class ControlReq extends DanaBundle {
  // Bools
  val cacheValid = Bool()
  val waiting = Bool()
  val needsLayerInfo = Bool()
  val needsRegisters = Bool()
  val needsNextRegister = Bool()
  val request = Bool()
  val inFirst = Bool()
  val inLast = Bool()
  // Global info
  val tableIndex = UInt(width = log2Up(transactionTableNumEntries))
  val cacheIndex = UInt(width = log2Up(cacheNumEntries))
  val nnid = UInt(width = nnidWidth) // formerly nn_hash
  val tid = UInt(width = tidWidth) // formerly pid
  // State info
  val currentNode = UInt(width = 16) // [TODO] fragile
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

class ControlResp extends DanaBundle {
  val tableIndex = UInt(width = log2Up(transactionTableNumEntries))
  val field = UInt(width = 4) // [TODO] fragile on Constants.scala
  val data = Vec.fill(3){UInt(width = 16)} // [TODO] fragile
  val decimalPoint = UInt(width = decimalPointWidth)
}

class XFilesArbiterRespPipe extends DanaBundle {
  val tid = UInt(width = tidWidth)
  val tidIdx = UInt(width = log2Up(transactionTableNumEntries))
  val readIdx = UInt(width = log2Up(transactionTableSramElements))
}

class TTableControlInterface extends DanaBundle {
  val req = Decoupled(new ControlReq)
  val resp = Decoupled(new ControlResp).flip
}

class TransactionTableInterface extends DanaBundle {
  val arbiter = (new XFilesArbiterInterface).flip
  val control = new TTableControlInterface
  val peTable = (new PETransactionTableInterface).flip
}

class TransactionTable extends DanaModule {
  // Communication with the X-FILES arbiter
  val io = new TransactionTableInterface

  // Vector of all the table entries
  val table = Vec.fill(transactionTableNumEntries){Reg(new TransactionState)}
  // Temporary debug enforcement
  for (i <- 0 until transactionTableNumEntries) {
    debug(table(i).done)
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
  def derefTid(x: TransactionState, y: UInt): Bool = { x.tid === y && (x.valid || x.reserved) }

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
  io.arbiter.resp.valid := Bool(false)
  io.arbiter.resp.bits.tid := UInt(0)
  io.arbiter.resp.bits.data := UInt(0)

  // Response pipeline to arbiter
  val arbiterRespPipe = Reg(Valid(new XFilesArbiterRespPipe))
  arbiterRespPipe.valid := Bool(false)
  arbiterRespPipe.bits.tid := UInt(0)
  arbiterRespPipe.bits.readIdx := UInt(0)
  arbiterRespPipe.bits.tidIdx := UInt(0)
  io.arbiter.resp.valid := arbiterRespPipe.valid
  io.arbiter.resp.bits.tid := arbiterRespPipe.bits.tid
  val memDataVec = Vec((0 until elementsPerBlock).map(i => (mem(arbiterRespPipe.bits.tidIdx).dout(0) >> (UInt(elementWidth) * UInt(i)))(elementWidth - 1, 0)))
  io.arbiter.resp.bits.data :=
    memDataVec(arbiterRespPipe.bits.readIdx(log2Up(elementsPerBlock)-1,0))

  // Default value assignment
  for (i <- 0 until transactionTableNumEntries) {
    for (j <- 0 until mem(i).numPorts) {
      mem(i).we(j) := Bool(false)
      mem(i).din(j) := UInt(0)
      mem(i).addr(j) := UInt(0)
    }
  }
  when (io.arbiter.req.valid) {
    // This is a new packet
    when (io.arbiter.req.bits.readOrWrite) { // Write == True
      when (io.arbiter.req.bits.isNew) {
        table(nextFree).reserved := Bool(true)
        table(nextFree).cacheValid := Bool(false)
        table(nextFree).waiting := Bool(false)
        table(nextFree).needsLayerInfo := Bool(true)
        table(nextFree).needsRegisters := Bool(true)
        table(nextFree).needsNextRegister := Bool(false)
        table(nextFree).inFirst := Bool(true)
        table(nextFree).inLast := Bool(false)
        table(nextFree).tid := io.arbiter.req.bits.tid
        table(nextFree).nnid := io.arbiter.req.bits.data
        table(nextFree).currentNode := UInt(0)
        table(nextFree).currentLayer := UInt(0)
        table(nextFree).request := Bool(false)
        table(nextFree).countFeedback := io.arbiter.req.bits.countFeedback
        table(nextFree).done := Bool(false)
        table(nextFree).indexElement := UInt(0)
        table(nextFree).countPeWrites := UInt(0)
        table(nextFree).readIdx := UInt(0)
      }
        .elsewhen(io.arbiter.req.bits.isLast) {
        mem(derefTidIndex).we(0) := Bool(true)
        mem(derefTidIndex).din(0) := io.arbiter.req.bits.data
        mem(derefTidIndex).addr(0) := table(derefTidIndex).indexElement
        table(derefTidIndex).indexElement :=
          table(derefTidIndex).indexElement + UInt(1)
        table(derefTidIndex).valid := Bool(true)
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
    } .otherwise { // Ths is a read packet
      mem(derefTidIndex).addr(0) := table(derefTidIndex).readIdx
      arbiterRespPipe.valid := Bool(true)
      arbiterRespPipe.bits.tid := io.arbiter.req.bits.tid
      arbiterRespPipe.bits.tidIdx := derefTidIndex
      arbiterRespPipe.bits.readIdx := table(derefTidIndex).readIdx
      // Check to see if all outputs have been read
      when (table(derefTidIndex).readIdx ===
        table(derefTidIndex).nodesInCurrentLayer - UInt(1)) {
        table(derefTidIndex).valid := Bool(false)
        table(derefTidIndex).reserved := Bool(false)
      }
      table(derefTidIndex).readIdx := table(derefTidIndex).readIdx + UInt(1)
    }
  }

  // Update the table when we get a request from DANA
  when (io.control.resp.valid) {
    // table(io.control.resp.bits.tableIndex).waiting := Bool(true)
    switch(io.control.resp.bits.field) {
      is(e_TTABLE_WAITING) {
        table(io.control.resp.bits.tableIndex).waiting := Bool(true)
      }
      is (e_TTABLE_STOP_WAITING) {
        table(io.control.resp.bits.tableIndex).waiting := Bool(false)
      }
      is(e_TTABLE_CACHE_VALID) {
        table(io.control.resp.bits.tableIndex).cacheValid := Bool(true)
        table(io.control.resp.bits.tableIndex).numLayers :=
          io.control.resp.bits.data(0)
        table(io.control.resp.bits.tableIndex).numNodes :=
          io.control.resp.bits.data(1)
        table(io.control.resp.bits.tableIndex).cacheIndex :=
          io.control.resp.bits.data(2)
        table(io.control.resp.bits.tableIndex).decimalPoint :=
          io.control.resp.bits.decimalPoint
        // Once we know the cache is valid, this entry is no longer waiting
        table(io.control.resp.bits.tableIndex).waiting := Bool(false)
      }
      is(e_TTABLE_LAYER) {
        table(io.control.resp.bits.tableIndex).needsLayerInfo := Bool(false)
        table(io.control.resp.bits.tableIndex).needsRegisters :=
          table(io.control.resp.bits.tableIndex).currentLayer !=
          table(io.control.resp.bits.tableIndex).numLayers - UInt(1)
        table(io.control.resp.bits.tableIndex).currentNodeInLayer := UInt(0)
        table(io.control.resp.bits.tableIndex).nodesInCurrentLayer := io.control.resp.bits.data(0)
        table(io.control.resp.bits.tableIndex).nodesInNextLayer := io.control.resp.bits.data(1)
        table(io.control.resp.bits.tableIndex).neuronPointer := io.control.resp.bits.data(2)
        // Update the inFirst and inLast Bools. The currentLayer
        // should have already been updated when the request went out.
        table(io.control.resp.bits.tableIndex).inFirst :=
          table(io.control.resp.bits.tableIndex).currentLayer === UInt(0)
          // table(io.control.resp.bits.tableIndex).numLayers - UInt(1)
        table(io.control.resp.bits.tableIndex).inLast :=
          table(io.control.resp.bits.tableIndex).currentLayer ===
          table(io.control.resp.bits.tableIndex).numLayers - UInt(1)
        // [TODO] This right shift is probably fucked
        table(io.control.resp.bits.tableIndex).regBlockIndexIn :=
          table(io.control.resp.bits.tableIndex).regBlockInNext << UInt(log2Up(elementsPerBlock))
        table(io.control.resp.bits.tableIndex).countUsedRegisters := UInt(0)
        // If this is a transition into a layer which is not the first
        // layer, then the Transaction Table requests need to block
        // until the Register File has all valid data. [TODO] This is
        // a sub-optimal design choice as PEs should be allowed to
        // start before the Register File has _all_ of its valid data,
        // but I'm leaving this the way it is due to the lack of a
        // non-trivial path to add this functionality.
        table(io.control.resp.bits.tableIndex).waiting :=
          table(io.control.resp.bits.tableIndex).currentLayer > UInt(0)
      }
      is(e_TTABLE_DONE) {
        table(io.control.resp.bits.tableIndex).cacheValid := Bool(true)
      }
      is (e_TTABLE_INCREMENT_NODE) {
        // [TODO] The waiting bit shouldn't always be set...
        table(io.control.resp.bits.tableIndex).currentNode :=
          table(io.control.resp.bits.tableIndex).currentNode + UInt(1)
        // [TODO] This currentNodeInLayer is always incremented and I
        // think this is okay as the value will be reset when a Layer
        // Info request gets serviced.
        table(io.control.resp.bits.tableIndex).currentNodeInLayer :=
          table(io.control.resp.bits.tableIndex).currentNodeInLayer + UInt(1)
        table(io.control.resp.bits.tableIndex).inFirst :=
          table(io.control.resp.bits.tableIndex).currentLayer === UInt(0)
        table(io.control.resp.bits.tableIndex).inLast :=
          table(io.control.resp.bits.tableIndex).currentLayer ===
          table(io.control.resp.bits.tableIndex).numLayers - UInt(1)
        // If we're at the end of a layer, we need new layer
        // information
        when(table(io.control.resp.bits.tableIndex).currentNodeInLayer ===
          // The comparison here differs from how this is handled in
          // nn_instruction.v.
          table(io.control.resp.bits.tableIndex).nodesInCurrentLayer - UInt(1) &&
          table(io.control.resp.bits.tableIndex).currentLayer <
          table(io.control.resp.bits.tableIndex).numLayers
        ) {
          table(io.control.resp.bits.tableIndex).needsLayerInfo := Bool(true)
          table(io.control.resp.bits.tableIndex).currentLayer :=
            table(io.control.resp.bits.tableIndex).currentLayer + UInt(1)
        } .otherwise {
          table(io.control.resp.bits.tableIndex).needsLayerInfo := Bool(false)
          table(io.control.resp.bits.tableIndex).currentLayer :=
            table(io.control.resp.bits.tableIndex).currentLayer
        }
      }
    }
  }

  // Deal with requests from the PE Table. [TODO] This is a somewhat
  // verbose implementation with a largely unused portion of this
  // response pipeline.
  val peRespPipe = Vec.fill(2){Reg(Valid(new PETransactionTableInterfaceResp))}
  val peRespIndex = Reg(next = io.peTable.req.bits.tableIndex)
  peRespPipe(0).valid := Bool(false)
  when (io.peTable.req.valid) {
    // This is either a read or a write request
    when (!io.peTable.req.bits.isWrite) { // This is a read req
      // [TODO] This is using the first address, which should be fine,
      // but there are technically two that we can play with if
      // needed. There may be unintended consequences if some read
      // happens to follow a read very closely.
      mem(io.peTable.req.bits.tableIndex).addr(0) := io.peTable.req.bits.addr
      peRespPipe(0).valid := Bool(true)
      peRespPipe(0).bits.peIndex := io.peTable.req.bits.peIndex
    } .otherwise { // This is a write req
      mem(io.peTable.req.bits.tableIndex).we(0) := Bool(true)
      mem(io.peTable.req.bits.tableIndex).addr(0) := io.peTable.req.bits.addr
      mem(io.peTable.req.bits.tableIndex).din(0) := io.peTable.req.bits.data
      table(io.peTable.req.bits.tableIndex).countPeWrites :=
        table(io.peTable.req.bits.tableIndex).countPeWrites + UInt(1)
      when (table(io.peTable.req.bits.tableIndex).countPeWrites ===
        table(io.peTable.req.bits.tableIndex).nodesInCurrentLayer - UInt(1)) {
        table(io.peTable.req.bits.tableIndex).done := Bool(true)
      }
    }
  }
  // Package up the memory response for the response to the PE Table
  peRespPipe(1) := peRespPipe(0)
  when (peRespPipe(0).valid) {
    peRespPipe(1).bits.data := mem(peRespIndex).dout(0)
  }
  io.peTable.resp.valid := peRespPipe(1).valid
  io.peTable.resp.bits.peIndex := peRespPipe(1).bits.peIndex
  io.peTable.resp.bits.data := peRespPipe(1).bits.data

  // Round Robin Arbitration of Transaction Table entries. One of
  // these is passed out over an interface to DANA's control module.
  val entryArbiter = Module(new RRArbiter( new ControlReq,
    transactionTableNumEntries))
  // All of these need to be wired up manually as the internal
  // connections aren't IO
  for (i <- 0 until transactionTableNumEntries) {
    // A request is valid if it is valid, is not waiting, and if all
    // the nodes haven't already been allocated (but, the cache must
    // already be valid, i.e., we need to have valid data sitting in
    // the currentNode and numNodes to actually do this comparison).
    entryArbiter.io.in(i).valid := table(i).valid && !table(i).waiting &&
      ((table(i).currentNode != table(i).numNodes) || !table(i).cacheValid)
    // The other data connections are just aliases to the contents of
    // the specific table entry
    entryArbiter.io.in(i).bits.cacheValid := table(i).cacheValid
    entryArbiter.io.in(i).bits.waiting := table(i).waiting
    entryArbiter.io.in(i).bits.needsLayerInfo := table(i).needsLayerInfo
    entryArbiter.io.in(i).bits.needsRegisters := table(i).needsRegisters
    entryArbiter.io.in(i).bits.needsNextRegister := table(i).needsNextRegister
    entryArbiter.io.in(i).bits.request := table(i).request
    entryArbiter.io.in(i).bits.inFirst := table(i).inFirst
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
  io.control.req <> entryArbiter.io.out

  // Assertions

  // The arbiter should only receive a request if it is asserting its
  // ready signal.
  assert(!io.arbiter.req.valid || io.arbiter.req.ready,
    "Inbound arbiter request when Transaction Table not ready")
  // Only one inbound request or response can currently be handled
  assert(!io.arbiter.req.valid || !io.control.resp.valid,
    "Received simultaneous requests on the TransactionTable")
  // Valid should never be true if reserved is not true
  for (i <- 0 until transactionTableNumEntries)
    assert(!table(i).valid || table(i).reserved,
      "Valid asserted with reserved de-asserted on TTable " + i)
  // A read request should hit a valid entry
  assert(foundTid || !io.arbiter.req.valid || io.arbiter.req.bits.readOrWrite,
    "X-FILES performed a read request on a non-existent TID")
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
    poke(uut.io.control.req.ready, 1)
  }
}
