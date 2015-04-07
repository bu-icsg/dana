package dana

import Chisel._

object Testbench {
  def main(args: Array[String]): Unit = {
    val cliArgs = args.slice(1, args.length)
    val elementWidth = 32
    val res =
      args(0) match {
        case "ProcessingElement" =>
          chiselMainTest(cliArgs, () => Module(new
            ProcessingElement(
            elementWidth = elementWidth,
              elementsPerBlock = 4,
              decimalPointOffset = 7,
              decimalPointWidth = 3,
              steepnessWidth = 3,
              activationFunctionWidth = 5))){
            c => new ProcessingElementTests(c)}
        case "ActivationFunction" =>
          chiselMainTest(cliArgs, () => Module(new
            ActivationFunction(
            elementWidth = elementWidth,
              decimalPointOffset = 7,
              decimalPointWidth = 3,
              steepnessWidth = 3))){
            c => new ActivationFunctionTests(c)}
      }
  }
}
