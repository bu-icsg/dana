package dana

import Chisel._

class ProcessingElementState(
  // Top-level parameters
  val elementWidth: Int = 32,
  val elementsPerBlock: Int = 4,
  val tidWidth: Int = 16,
  val transactionTableNumEntries: Int = 2,
  val transactionTableSramElements: Int = 32,
  val regFileNumElements: Int = 80,
  val cacheNumEntries: Int = 4,
  val decimalPointWidth: Int = 3,
  val activationFunctionWidth: Int = 5,
  val steepnessWidth: Int = 3,
  val cacheDataSize: Int = 32 * 1024
) (
  // Derived parameters
  val transactionTableNumBlocks: Int = transactionTableSramElements /
    elementsPerBlock,
  val cacheNumBlocks: Int = cacheDataSize / elementsPerBlock / elementWidth * 8,
  val ioIdxWidth: Int = if (transactionTableSramElements > regFileNumElements)
    log2Up(transactionTableSramElements * elementWidth) else
      log2Up(regFileNumElements * elementWidth)
)extends Bundle {
  val tid = UInt(width = tidWidth) // pid
  val tIdx = UInt(width = log2Up(transactionTableNumEntries)) // nn_index
  val cIdx = UInt(width = log2Up(cacheNumEntries)) // cache_index
  val nnNode = UInt(width = 10) // nn_node [TODO] fragile
  val outIndx = UInt(width = ioIdxWidth) // output_index
  val inIndx = UInt(width = ioIdxWidth) // input_index
  val neuronPtr = UInt(width = // neuron_pointer
    log2Up(elementWidth * elementsPerBlock * cacheNumBlocks))
  val weightPtr = UInt(width = // weight_pointer
    log2Up(elementWidth * elementsPerBlock * cacheNumBlocks))
  val decimalPoint = UInt(width = decimalPointWidth) // decimal_point
  // state is not included here
  val inLoc = UInt(width = 2) // input_location [TODO] fragile
  val outLoc = UInt(width = 2) // output_location [TODO] fragile
  val lastInLayer = Bool() // last_in_layer
  val inBlock = UInt(width = elementWidth * elementsPerBlock) // input_block
  val weightBlock = UInt(width = elementWidth * elementsPerBlock) //weight_block
  val numWeights = UInt(width = 8) // [TODO] fragile
  val activationFunction = UInt(width = activationFunctionWidth)
  val steepness = UInt(width = steepnessWidth)
  val bias = UInt(width = elementWidth)
  val weightValid = Bool()
  val inValid = Bool() // input_valid
}

abstract class ProcessingElementTable extends DanaModule()() {
  // Create the table with the specified top-level parameters. Derived
  // parameters should not be touched.
  val table = Vec.fill(peTableNumEntries){new ProcessingElementState(
    elementWidth = elementWidth,
    elementsPerBlock = elementsPerBlock,
    tidWidth = tidWidth,
    transactionTableNumEntries = transactionTableNumEntries,
    transactionTableSramElements = transactionTableSramElements,
    regFileNumElements = regFileNumElements,
    cacheNumEntries = cacheNumEntries,
    decimalPointWidth = decimalPointWidth,
    activationFunctionWidth = activationFunctionWidth,
    steepnessWidth = steepnessWidth,
    cacheDataSize = cacheDataSize)()}
  // Create the processing elements
  val pes = Range(0, peTableNumEntries).map(i => Module(new ProcessingElement))

  // Wire up all the processing elements
  for (i <- 0 until peTableNumEntries) {
  }
}
