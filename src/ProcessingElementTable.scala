package dana

import Chisel._

class PECacheInterfaceResp extends DanaBundle()() {
  val field = UInt(width = log2Up(3))
  val data = UInt(width = bitsPerBlock)
  val peIndex = UInt(width = log2Up(peTableNumEntries))
  val indexIntoData = UInt(width = elementsPerBlock) // [TODO] too big width?
}

class PECacheInterface extends DanaBundle()() {
  // Outbound request / inbound responses. These are roughly
  // equivalent to:
  //   * pe_types::pe2storage_struct
  //   * pe_types::storage2pe_struct
  val req = Decoupled(new DanaBundle()() {
    val field = UInt(width = log2Up(3)) // [TODO] fragile on Cache Req Enum
    val peIndex = UInt(width = log2Up(peTableNumEntries))
    val cacheIndex = UInt(width = log2Up(cacheNumEntries))
    val cacheAddr = UInt(width =
      log2Up(cacheNumBlocks * elementsPerBlock * elementWidth))
  })
  val resp = Decoupled(new PECacheInterfaceResp).flip
}

class PERegisterFileInterface extends DanaBundle()() {
  val req = Decoupled(new DanaBundle()() {
    val foo = Bool()
  })
  val resp = Decoupled(new DanaBundle()() {
    val bar = Bool()
  }).flip
}

class PETransactionTableInterface extends DanaBundle()() {
  val req = Decoupled(new DanaBundle()() {
    val foo = Bool()
  })
  val resp = Decoupled(new DanaBundle()() {
    val bar = Bool()
  }).flip
}

class PETableInterface extends DanaBundle()() {
  val control = (new ControlPETableInterface).flip
  val cache = new PECacheInterface
  val registerFile = new PERegisterFileInterface
  val tTable = new PETransactionTableInterface
}

class ProcessingElementState extends DanaBundle()() {
  val infoValid = Reg(Bool())
  val weightValid = Reg(Bool())
  val inValid = Reg(Bool()) // input_valid
  val tid = Reg(UInt(width = tidWidth), init = SInt(-1)) // pid
  val tIdx = Reg(UInt(width = log2Up(transactionTableNumEntries))) // nn_index
  val cIdx = Reg(UInt(width = log2Up(cacheNumEntries))) // cache_index
  val nnNode = Reg(UInt(width = 10)) // nn_node [TODO] fragile
  val outIdx = Reg(UInt(width = ioIdxWidth)) // output_index
  val inIdx = Reg(UInt(width = ioIdxWidth)) // input_index
  val neuronPtr = Reg(UInt(width = // neuron_pointer
    log2Up(elementWidth * elementsPerBlock * cacheNumBlocks)))
  val weightPtr = Reg(UInt(width = // weight_pointer
    log2Up(elementWidth * elementsPerBlock * cacheNumBlocks)))
  val decimalPoint = Reg(UInt(width = decimalPointWidth)) // decimal_point
  val inLoc = Reg(UInt(width = 2)) // input_location [TODO] fragile
  val outLoc = Reg(UInt(width = 2)) // output_location [TODO] fragile
  val lastInLayer = Reg(Bool()) // last_in_layer
  val inBlock = Reg(UInt(width = bitsPerBlock)) // input_block
  val weightBlock = Reg(UInt(width = bitsPerBlock)) //weight_block
  val numWeights = Reg(UInt(width = 8)) // [TODO] fragile
  val activationFunction = Reg(UInt(width = activationFunctionWidth))
  val steepness = Reg(UInt(width = steepnessWidth))
  val bias = Reg(UInt(width = elementWidth))
}

class ProcessingElementTable extends DanaModule()() {
  val io = new PETableInterface

  // [TODO] Placeholder values for Transaction Table interface
  io.tTable.req.valid := Bool(false)
  io.tTable.req.bits.foo := Bool(false)
  io.tTable.resp.ready := Bool(false)
  // [TODO] Placeholder values for Register File interface
  io.registerFile.req.valid := Bool(false)
  io.registerFile.req.bits.foo := Bool(false)
  io.registerFile.resp.ready := Bool(false)

  // Create the table with the specified top-level parameters. Derived
  // parameters should not be touched.
  val table = Vec.fill(peTableNumEntries){new ProcessingElementState}
  // Create the processing elements
  val pe = Vec.fill(peTableNumEntries){Module (new ProcessingElement()).io}

  // Wire up the PE data connections
  for (i <- 0 until peTableNumEntries) {
    pe(i).req.bits.index := UInt(i)
    pe(i).req.bits.decimalPoint := table(i).decimalPoint
    pe(i).req.bits.steepness := table(i).steepness
    pe(i).req.bits.activationFunction := table(i).activationFunction
    pe(i).req.bits.numElements := table(i).numWeights
    pe(i).req.bits.bias := table(i).bias
    // pe(i).validIn
    for (j <- 0 until elementsPerBlock) {
      pe(i).req.bits.iBlock(j) :=
        table(i).inBlock(elementWidth * (j + 1) - 1, elementWidth * j)
      pe(i).req.bits.wBlock(j) :=
        table(i).weightBlock(elementWidth * (j + 1) - 1, elementWidth * j)
    }
  }

  def isFree(x: ProcessingElementInterface): Bool = { x.req.ready }
  val hasFree = Bool()
  val nextFree = UInt()
  hasFree := pe.exists(isFree)
  nextFree := pe.indexWhere(isFree)

  io.control.req.ready := hasFree

  // Default values for Cache interface
  io.cache.req.valid := Bool(false)
  io.cache.req.bits.field := UInt(0)
  io.cache.req.bits.peIndex := UInt(0)
  io.cache.req.bits.cacheIndex := UInt(0)
  io.cache.req.bits.cacheAddr := UInt(0)
  // Default values for PE connections
  for (i <- 0 until peTableNumEntries) {
    pe(i).req.valid := Bool(false)
  }

  // Temporary debug shit [TODO] Remove this at some point
  for (i <- 0 until peTableNumEntries) {
    debug(table(i).tid)
    debug(table(i).cIdx)
    debug(table(i).nnNode)
    debug(table(i).inIdx)
    debug(table(i).outIdx)
    debug(table(i).neuronPtr)
    debug(table(i).weightPtr)
    debug(table(i).decimalPoint)
    debug(table(i).inLoc)
    debug(table(i).outLoc)
    debug(table(i).lastInLayer)
    debug(table(i).inBlock)
    debug(table(i).weightBlock)
    debug(table(i).numWeights)
    debug(table(i).activationFunction)
    debug(table(i).steepness)
    debug(table(i).bias)
    debug(table(i).weightValid)
    debug(table(i).inValid)
  }

  // Wire up all the processing elements
  for (i <- 0 until peTableNumEntries) {
  }

  // Deal with inbound requests from the Control module. If we see a
  // request, it can only mean one thing---we need to allocate a PE.
  when (io.control.req.valid) {
    table(nextFree).tid := io.control.req.bits.tid
    table(nextFree).cIdx := io.control.req.bits.cacheIndex
    table(nextFree).nnNode := io.control.req.bits.neuronIndex
    table(nextFree).inIdx := io.control.req.bits.locationInput
    table(nextFree).outIdx := io.control.req.bits.locationOutput
    table(nextFree).neuronPtr := io.control.req.bits.neuronPointer
    table(nextFree).weightPtr := SInt(-1)
    table(nextFree).decimalPoint := io.control.req.bits.decimalPoint
    table(nextFree).inLoc := io.control.req.bits.inputIndex
    table(nextFree).outLoc := io.control.req.bits.outputIndex
    table(nextFree).lastInLayer := Bool(false) // [TODO] not sure about this
    table(nextFree).inBlock := SInt(-1)
    table(nextFree).weightBlock := SInt(-1)
    table(nextFree).numWeights := SInt(-1)
    table(nextFree).activationFunction := SInt(-1)
    table(nextFree).steepness := SInt(-1)
    table(nextFree).bias := UInt(0)
    table(nextFree).weightValid := Bool(false)
    table(nextFree).inValid := Bool(false)
    // [TODO] Kick the PE
    pe(nextFree).req.valid := Bool(true)
  }

  // Round robin arbitration of PE Table entries
  val peArbiter = Module(new RRArbiter(new ProcessingElementResp,
    peTableNumEntries))
  // Wire up the arbiter
  for (i <- 0 until peTableNumEntries) {
    peArbiter.io.in(i).valid := pe(i).resp.valid
    peArbiter.io.in(i).bits.data := pe(i).resp.bits.data
    peArbiter.io.in(i).bits.state := pe(i).resp.bits.state
    peArbiter.io.in(i).bits.index := pe(i).resp.bits.index
  }

  // If the arbiter is showing a valid output, then we have to
  // generate some requests based on which PE the arbiter has
  // determined needs something. The action taken depends on the state
  // that we're seeing from the chosen PE.
  when (peArbiter.io.out.valid) {
    switch (peArbiter.io.out.bits.state) {
      is (e_PE_GET_INFO) {
        // Send a request to the cache for information.
        io.cache.req.valid := Bool(true)
        io.cache.req.bits.field := e_CACHE_NEURON
        io.cache.req.bits.peIndex := peArbiter.io.out.bits.index
        io.cache.req.bits.cacheIndex := table(peArbiter.io.out.bits.index).cIdx
        io.cache.req.bits.cacheAddr := table(peArbiter.io.out.bits.index).neuronPtr
      }
      // is (e_PE_REQUEST_INPUTS_AND_WEIGHTS) {
      //   // Send a request to the IO storage or the register file for
      //   // inputs

      //   // Send a request to the cache for weights
      // }
      // is (e_PE_DONE) {
      //   // Send the output value where it needs to go
      // }
    }
    // Kick the selected PE so that it jumps to a wait state
    pe(peArbiter.io.out.bits.index).req.valid := Bool(true)
  }

}
