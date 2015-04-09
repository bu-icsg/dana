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
            c => new ActivationFunctionTests(c)}
        case "TransactionTable" =>
          chiselMainTest(cliArgs, () => Module(new TransactionTable())){
            c => new TransactionTableTests(c)}
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
