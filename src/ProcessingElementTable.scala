package dana

import Chisel._

class PERegisterFileInterface extends DanaBundle()() {
  val req = Decoupled(new DanaBundle()() {
    val foo = Bool()
  })
  val resp = Decoupled(new DanaBundle()() {
    val bar = Bool()
  }).flip
}

class PETableInterface extends DanaBundle()() {
  val control = (new ControlPETableInterface).flip
}

class ProcessingElementState extends DanaBundle()() {
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
  val state = Reg(UInt(), init = UInt(0)) // [TODO] fragile init
  val inLoc = Reg(UInt(width = 2)) // input_location [TODO] fragile
  val outLoc = Reg(UInt(width = 2)) // output_location [TODO] fragile
  val lastInLayer = Reg(Bool()) // last_in_layer
  val inBlock = Reg(UInt(width = elementWidth * elementsPerBlock)) // input_block
  val weightBlock = Reg(UInt(width = elementWidth * elementsPerBlock)) //weight_block
  val numWeights = Reg(UInt(width = 8)) // [TODO] fragile
  val activationFunction = Reg(UInt(width = activationFunctionWidth))
  val steepness = Reg(UInt(width = steepnessWidth))
  val bias = Reg(UInt(width = elementWidth))
  val weightValid = Reg(Bool())
  val inValid = Reg(Bool()) // input_valid
}

class ProcessingElementTable extends DanaModule()() {
  val io = new PETableInterface

  // Create the table with the specified top-level parameters. Derived
  // parameters should not be touched.
  val table = Vec.fill(peTableNumEntries){new ProcessingElementState}
  // Create the processing elements
  val pes = Range(0, peTableNumEntries).map(i => Module(new ProcessingElement))

  def isFree(x: ProcessingElementState): Bool = { x.state === e_PE_UNALLOCATED }
  def derefTid(x: ProcessingElementState, y: UInt): Bool = { x.tid === y}
  val hasFree = Bool()
  val nextFree = UInt()
  val derefTidIndex = UInt()
  hasFree := table.exists(isFree)
  nextFree := table.indexWhere(isFree)
  derefTidIndex := table.indexWhere(derefTid(_, io.control.req.bits.tid))

  io.control.req.ready := hasFree

  // Temporary debug shit
  // (0 until peTableNumEntries).map(i => debug(table(i).state))
  for (i <- 0 until peTableNumEntries) {
    debug(table(i).state)
    // debug(table(i).tid)
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
    table(derefTidIndex).state := e_PE_GET_INFO
    table(derefTidIndex).tid := io.control.req.bits.tid
    table(derefTidIndex).cIdx := io.control.req.bits.cacheIndex
    table(derefTidIndex).nnNode := io.control.req.bits.neuronIndex
    table(derefTidIndex).inIdx := io.control.req.bits.locationInput
    table(derefTidIndex).outIdx := io.control.req.bits.locationOutput
    table(derefTidIndex).neuronPtr := io.control.req.bits.neuronPointer
    table(derefTidIndex).weightPtr := SInt(-1)
    table(derefTidIndex).decimalPoint := io.control.req.bits.decimalPoint
    table(derefTidIndex).inLoc := io.control.req.bits.inputIndex
    table(derefTidIndex).outLoc := io.control.req.bits.outputIndex
    table(derefTidIndex).lastInLayer := Bool(false) // [TODO] not sure about this
    table(derefTidIndex).inBlock := SInt(-1)
    table(derefTidIndex).weightBlock := SInt(-1)
    table(derefTidIndex).numWeights := SInt(-1)
    table(derefTidIndex).activationFunction := SInt(-1)
    table(derefTidIndex).steepness := SInt(-1)
    table(derefTidIndex).bias := UInt(0)
    table(derefTidIndex).weightValid := Bool(false)
    table(derefTidIndex).inValid := Bool(false)
  }

}
