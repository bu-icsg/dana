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
        case "Dana" =>
          chiselMainTest(cliArgs, () => Module(new Dana)){
            c => new DanaTests(c, false)}
      }
  }
}

// An abstract base class for anything associated with DANA (and the
// X-FILES framework?). This defines all shared DANA parameters.
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
      log2Up(regFileNumElements * elementWidth),
  val bitsPerBlock: Int = elementsPerBlock * elementWidth
) extends Module {
  // Transaction Table State Entries. nnsim-hdl equivalent:
  //   controL_types::field_enum
  val (e_TTABLE_VALID ::       // 0
    e_TTABLE_RESERVED ::       // 1
    e_TTABLE_CACHE_VALID ::    // 2
    e_TTABLE_LAYER ::          // 3
    e_TTABLE_WAITING ::        // 4
    e_TTABLE_DONE ::           // 5
    e_TTABLE_OUTPUT_LAYER ::   // 6
    e_TTABLE_INCREMENT_NODE :: // 7
    e_TTABLE_REGISTER_INFO ::  // 8
    e_TTABLE_REGISTER_NEXT ::  // 9
    Nil) = Enum(UInt(), 10)
  // Cache Request Type
  val (e_CACHE_LOAD ::                // 0
    e_CACHE_LAYER_INFO ::             // 1
    e_CACHE_DECREMENT_IN_USE_COUNT :: // 2
    Nil) = Enum(UInt(), 3)
  // Cache to control field enum. nnsim-hdl equivalent:
  //   cache_types::field_enum
  val (e_CACHE_INFO :: // 0
    e_CACHE_LAYER ::   // 1
    e_CACHE_NEURON ::  // 2
    e_CACHE_WEIGHT ::  // 3
    Nil) = Enum(UInt(), 4)
  // Cache / PE access type enum. nnsim-hdl equivalent:
  //   pe_types::pe2storage_enum
  val (e_PE_NEURON :: // 0
    e_PE_WEIGHT ::    // 1
    Nil) = Enum(UInt(), 2)
  // PE State
  val (e_PE_UNALLOCATED ::              // 0
    e_PE_GET_INFO ::                    // 1
    e_PE_WAIT_FOR_INFO ::               // 2
    e_PE_REQUEST_INPUTS_AND_WEIGHTS ::  // 4
    e_PE_WAIT_FOR_INPUTS_AND_WEIGHTS :: // 5
    e_PE_RUN ::                         // 6
    e_PE_DONE ::                        // 7
    Nil) = Enum(UInt(), 8)
  // Location of inputs and outputs
  val (e_LOCATION_REG_0 :: // 0
    e_LOCATION_REG_1 ::    // 1
    e_LOCATION_IO ::       // 2
    Nil) = Enum(UInt(), 3)
}

// Base class for all Bundle classes used in DANA. This sets all the
// parameters that should be shared. All parameters defined here
// should be the same as in DanaModule.
abstract class DanaBundle(
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
      log2Up(regFileNumElements * elementWidth),
  val bitsPerBlock: Int = elementsPerBlock * elementWidth
) extends Bundle{
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
          printf("|%d|%d|%2d|%2d|%2d|%2d|%s|%3d|%4x|",
            peek(c.table(i).valid),
            peek(c.table(i).reserved),
            peek(c.table(i).cacheValid),
            peek(c.table(i).waiting),
            peek(c.table(i).needsLayerInfo),
            peek(c.table(i).needsRegisters),
            // peek(c.table(i).done),
            "X",
            peek(c.table(i).tid),
            peek(c.table(i).nnid)
          )
          // for (j <- 0 until c.transactionTableSramElements) {
          //   poke(c.mem(i).we(0), j)
          //   poke(c.mem(i).addr(0), j)
          //   step(1)
          //   printf("%6d|", peek(c.mem(i).dout(0)).toInt)
          // }
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
