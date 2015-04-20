package dana

import Chisel._

object Testbench {
  def main(args: Array[String]): Unit = {
    val cliArgs = args.slice(1, args.length)
    val res =
      args(0) match {
        case "ProcessingElement" =>
          chiselMainTest(cliArgs, () => Module(new ProcessingElement)) {
            c => new ProcessingElementTests(c, false)}
        case "ActivationFunction" =>
          chiselMainTest(cliArgs, () => Module(new ActivationFunction)){
            c => new ActivationFunctionTests(c, false)}
        case "TransactionTable" =>
          chiselMainTest(cliArgs, () => Module(new TransactionTable)){
            c => new TransactionTableTests(c, false)}
        case "SRAM" =>
          chiselMainTest(cliArgs, () => Module(new SRAM(
            numReadPorts = 0,
            numWritePorts = 0,
            numReadWritePorts = 2,
            dataWidth = 8,
            sramDepth = 8))){
            c => new SRAMTests(c, false)}
        case "SRAMElement" =>
          chiselMainTest(cliArgs, () => Module(new SRAMElement(
            elementWidth = 16,
            dataWidth = 32,
            numPorts = 1,
            sramDepth = 8))){
            c => new SRAMElementTests(c, false)}
      }
  }
}

// An abstract base class for anything associated with DANA (and the
// X-FILES framework?)
abstract class DanaModule(
  val elementWidth: Int = 32,
  val elementsPerBlock: Int = 4,
  val tidWidth: Int = 16,
  val activationFunctionWidth: Int = 5,
  val nnidWidth: Int = 16,
  val decimalPointOffset: Int = 7,
  val decimalPointWidth: Int = 3,
  val steepnessWidth: Int = 3,
  val feedbackWidth: Int = 12,
  val bitsFeedback: Int = 12,
  // Processing Element Table
  val peTableNumEntries: Int = 2,
  // Transaction Table
  val transactionTableNumEntries: Int = 4,
  val transactionTableSramElements: Int = 32,
  // Register File
  val regFileNumElements: Int = 80,
  // Cache
  val cacheNumEntries: Int = 4,
  val cacheDataSize: Int = 32 * 1024
)(
  // Derived parameters. These should not be overwritten unless you
  // have a good reason!
  val transactionTableSramBlocks: Int = transactionTableSramElements /
    elementsPerBlock,
  val regFileNumBlocks: Int = regFileNumElements / elementsPerBlock,
  val cacheNumBlocks: Int = cacheDataSize / elementsPerBlock / elementWidth * 8,
  val ioIdxWidth: Int = if (transactionTableSramElements > regFileNumElements)
    log2Up(transactionTableSramElements * elementWidth) else
      log2Up(regFileNumElements * elementWidth)
) extends Module {
}

// Contains things common to all DANA testbenches
abstract class DanaTester[+T <: Module](c: T, isTrace: Boolean = true)
    extends Tester(c, isTrace) {
}
