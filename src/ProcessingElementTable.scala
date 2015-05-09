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

class PETable2PEInterface extends DanaBundle()() {
  val req = Valid(new DanaBundle()() {
    val decimalPoint = UInt(width = decimalPointWidth)
    val inBlock = UInt(width = bitsPerBlock)
    val weightBlock = UInt(width = bitsPerBlock)
    val numWeights = UInt(width = 8)
    val activationFunction = UInt(width = activationFunctionWidth)
    val steepness = UInt(width = steepnessWidth)
    val bias = UInt(width = elementWidth)
  })
  val resp = Decoupled(new DanaBundle()() {
    val output = UInt(width = elementWidth)
    val needsData = Bool()
    val done = Bool()
  }).flip
}

class ProcessingElementState extends DanaBundle()() {
  val valid = Reg(Bool(), init = Bool(false))
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

  // Create the table with the specified top-level parameters. Derived
  // parameters should not be touched.
  val table = Vec.fill(peTableNumEntries){new ProcessingElementState}
  // Create the processing elements
  val pes = Vec.fill(peTableNumEntries){Module (new ProcessingElement()).io}

  def isFree(x: ProcessingElementState): Bool = { !x.valid }
  val hasFree = Bool()
  val nextFree = UInt()
  hasFree := table.exists(isFree)
  nextFree := table.indexWhere(isFree)

  io.control.req.ready := hasFree

  // Temporary debug shit
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
    table(nextFree).valid := Bool(true)
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
  }

}
