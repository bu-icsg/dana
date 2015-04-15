package dana

import Chisel._

object Testbench {
  def main(args: Array[String]): Unit = {
    val cliArgs = args.slice(1, args.length)
    val res =
      args(0) match {
        case "ProcessingElement" =>
          chiselMainTest(cliArgs, () => Module(new ProcessingElement())) {
            c => new ProcessingElementTests(c, false)}
        case "ActivationFunction" =>
          chiselMainTest(cliArgs, () => Module(new ActivationFunction())){
            c => new ActivationFunctionTests(c, false)}
        case "TransactionTable" =>
          chiselMainTest(cliArgs, () => Module(new TransactionTable())){
            c => new TransactionTableTests(c, false)}
        case "SRAM" =>
          chiselMainTest(cliArgs, () => Module(new SRAM(
            numReadPorts = 0,
            numWritePorts = 0,
            numReadWritePorts = 2,
            dataWidth = 8,
            sramDepth = 8))){
            c => new SRAMTests(c, false)}
      }
  }
}

// Contains things common to all DANA modules
abstract class DanaModule extends Module {
}

// Contains things common to all DANA testbenches
abstract class DanaTester[+T <: Module](c: T, isTrace: Boolean = true)
    extends Tester(c, isTrace) {
}
