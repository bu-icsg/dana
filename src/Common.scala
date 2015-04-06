package dana

import Chisel._

object Testbench {
  def main(args: Array[String]): Unit = {
    val cliArgs = args.slice(1, args.length)
    val res =
      args(0) match {
        case "ProcessingElement" =>
          chiselMainTest(cliArgs, () => Module(new
            ProcessingElement(32,4,7,3,3))){
            c => new ProcessingElementTests(c)}
        case "ActivationFunction" =>
          chiselMainTest(cliArgs, () => Module(new
            ActivationFunction(32,7,3,3))){
            c => new ActivationFunctionTests(c)}
      }
  }
}
