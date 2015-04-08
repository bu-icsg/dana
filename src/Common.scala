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
      }
  }
}
