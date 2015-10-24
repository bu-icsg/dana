package dana

import Chisel._
import cde.{Parameters, Field}

object Testbench {
  def main(args: Array[String]): Unit = {
    val cliArgs = args.slice(1, args.length)
    val res =
      args(0) match {
        // case "ProcessingElement" =>
        //   chiselMainTest(cliArgs, () => Module(new ProcessingElement)) {
        //     c => new ProcessingElementTests(c, false)}
        // case "ActivationFunction" =>
        //   chiselMain.run(cliArgs, () => new ActivationFunction)
        //   // chiselMainTest(cliArgs, () => Module(new ActivationFunction)){
        //   //   c => new ActivationFunctionTests(c, false)}
        // case "TransactionTable" =>
        //   chiselMainTest(cliArgs, () => Module(new TransactionTable)){
        //     c => new TransactionTableTests(c, false)}
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
        // case "XFilesDana" =>
        //   chiselMain.run(cliArgs, () => new XFilesDana)
      }
  }
}

// Contains things common to all DANA testbenches
abstract class DanaTester[+T <: Module](c: T, isTrace: Boolean = true)
    extends Tester(c, isTrace) {
  // Generate a new request to a Transaction Table module for a
  // specified TID and NNID.
  def newWriteRequest(x: XFilesArbiterInterface, tid: Int, nnid: Int) {
    poke(x.req.valid, 1)
    poke(x.req.bits.isNew, 1)
    poke(x.req.bits.readOrWrite, 1)
    poke(x.req.bits.isLast, 0)
    poke(x.req.bits.tid, tid)
    poke(x.req.bits.data, nnid)
    step(1)
    poke(x.req.valid, 0)
  }

  // Send `num` amount of random data to a specific Transaction Table
  // instance with the specified TID and NNID.
  def writeRndData(x: XFilesArbiterInterface, tid: Int, nnid: Int, num: Int,
    decimal: Int) {
    // Send `num` data elements to DANA
    for (i <- 0 until num) {
      poke(x.req.valid, 1)
      poke(x.req.bits.isNew, 0)
      poke(x.req.bits.readOrWrite, 1)
      if (i == num - 1)
        poke(x.req.bits.isLast, 1)
      else
        poke(x.req.bits.isLast, 0)
      val data = rnd.nextInt(Math.pow(2, decimal + 2).toInt) -
        Math.pow(2, decimal + 1).toInt
      printf("[INFO] Input data: %d\n", data)
      poke(x.req.bits.data, data)
      step(1)
      poke(x.req.valid, 0)
    }
  }

  // Generic info function. This prints out module-specific
  // information based on the type of module that it sees. The intent
  // here is that all modules will be defined as cases in this case
  // statement and the function will print something different based
  // on the type.
  def info(dut : Any) {
    dut match {
      case _: TransactionTable =>
        val c = dut.asInstanceOf[TransactionTable]
        printf("|V|R|CV|WC|NL|NR|D|Tid|Nnid| <- TTable\n")
        printf("----------------------------\n")
        for (i <- 0 until c.table.length) {
          printf("|%d|%d|%2d|%2d|%2d|%s|%3d|%4x|",
            peek(c.table(i).valid),
            peek(c.table(i).reserved),
            peek(c.table(i).cacheValid),
            peek(c.table(i).waiting),
            peek(c.table(i).needsLayerInfo),
            peek(c.table(i).done),
            peek(c.table(i).tid),
            peek(c.table(i).nnid)
          )
          printf("\n")
        }
        printf("| hasFree: %d | nextFree: %d |\n",
          peek(c.hasFree),
          peek(c.nextFree))
        printf("\n");
      case _ => printf("No info() function for specified DUT\n")
    }
  }
}
