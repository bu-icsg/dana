// See LICENSE for license details.

package dana

import rocket._
import xfiles._
import Chisel._
import cde.{Parameters, Field}

case object ElementWidth extends Field[Int]
case object ElementsPerBlock extends Field[Int]
case object ActivationFunctionWidth extends Field[Int]
case object NnidWidth extends Field[Int]
case object DecimalPointOffset extends Field[Int]
case object DecimalPointWidth extends Field[Int]
case object SteepnessWidth extends Field[Int]
case object SteepnessOffset extends Field[Int]
case object ErrorFunctionWidth extends Field[Int]
case object FeedbackWidth extends Field[Int]
case object PeTableNumEntries extends Field[Int]
case object CacheNumEntries extends Field[Int]
case object CacheDataSize extends Field[Int]
case object RegisterFileNumElements extends Field[Int]
case object LearningEnabled extends Field[Boolean]
case object BitsPerBlock extends Field[Int]
case object RegFileNumBlocks extends Field[Int]
case object CacheNumBlocks extends Field[Int]
case object NNConfigNeuronWidth extends Field[Int]
case object AntwRobEntries extends Field[Int]

trait DanaParameters extends HasCoreParameters with XFilesParameters {
  val elementWidth = p(ElementWidth)
  val elementsPerBlock = p(ElementsPerBlock)
  // Activation Function width increases will break:
  //   * ProcessingElementTable logic for indexing into cache data
  val activationFunctionWidth = p(ActivationFunctionWidth)
  val nnidWidth = p(NnidWidth)
  val decimalPointOffset = p(DecimalPointOffset)
  val decimalPointWidth = p(DecimalPointWidth)
  // Steepness width increases will break:
  //   * ProcessingElementTable logic for indexing into cache data
  val steepnessWidth = p(SteepnessWidth)
  val steepnessOffset = p(SteepnessOffset)
  val errorFunctionWidth = p(ErrorFunctionWidth)
  val feedbackWidth = p(FeedbackWidth)
  val antwRobEntries = p(AntwRobEntries)

  // Processing Element Table
  val peTableNumEntries = p(PeTableNumEntries)
  // Configuration Cache
  val cacheNumEntries = p(CacheNumEntries)
  val cacheDataSize = p(CacheDataSize)
  // Register File
  val regFileNumElements = p(RegisterFileNumElements)

  // Derived parameters
  val regFileNumBlocks = p(RegFileNumBlocks)
  val cacheNumBlocks = p(CacheNumBlocks)
  // [TODO] This ioIdxWidth looks wrong?
  val ioIdxWidth = log2Up(p(RegisterFileNumElements) * p(ElementWidth))
  val bitsPerBlock = p(BitsPerBlock)
  val learningEnabled = p(LearningEnabled)

  // Related to the neural network configuration format
  val nnConfigNeuronWidth = p(NNConfigNeuronWidth)

  def divUp (dividend: Int, divisor: Int): Int = {
    (dividend + divisor - 1) / divisor}

  val err_UNKNOWN     = 0
  val err_DANA_NOANTP = 1
  val err_INVASID     = 2
  val err_INVNNID     = 3
  val err_ZEROSIZE    = 4
  val err_INVEPB      = 5
}

// An abstract base class for anything associated with DANA (and the
// X-FILES framework?). This defines all shared DANA parameters.
abstract class DanaModule(implicit p: Parameters) extends XFilesModule()(p)
    with DanaParameters {
  // Transaction Table State Entries. nnsim-hdl equivalent:
  //   controL_types::field_enum
  val (e_TTABLE_VALID ::       // 0
    e_TTABLE_RESERVED ::       // 1
    e_TTABLE_CACHE_VALID ::    // 2
    e_TTABLE_LAYER ::          // 3
    e_TTABLE_OUTPUT_LAYER ::   // 4
    e_TTABLE_REGISTER_INFO ::  // 5
    e_TTABLE_REGISTER_NEXT ::  // 6
    Nil) = Enum(UInt(), 7)
  // Used to set "transactionType" in the Transaction Table state
  val (e_TTYPE_FEEDFORWARD :: // 0
    e_TTYPE_INCREMENTAL ::    // 1
    e_TTYPE_BATCH ::          // 2
    Nil) = Enum(UInt(), 3)
  // Used to define the state of the transaction
  val (e_TTABLE_STATE_READ_INFO ::         // 0
    e_TTABLE_STATE_LOAD_OUTPUTS ::         // 1
    e_TTABLE_STATE_LOAD_INPUTS ::          // 2
    e_TTABLE_STATE_FEEDFORWARD ::          // 3
    e_TTABLE_STATE_LEARN_FEEDFORWARD ::    // 4
    e_TTABLE_STATE_LEARN_ERROR_BACKPROP :: // 5
    e_TTABLE_STATE_LEARN_WEIGHT_UPDATE ::  // 6
    e_TTABLE_STATE_ERROR ::                // 7
    Nil) = Enum(UInt(), 8)
  // Transaction register IDs used for write register request. This
  // must match "typedef enum xfiles_reg" in "xfiles.h".
  val (e_TTABLE_WRITE_REG_BATCH_ITEMS ::      // 0
    e_TTABLE_WRITE_REG_LEARNING_RATE ::       // 1
    e_TTABLE_WRITE_REG_WEIGHT_DECAY_LAMBDA :: // 2
    Nil) = Enum(UInt(), 3)

  // Cache Request Type
  val (e_CACHE_LOAD ::                // 0
    e_CACHE_LAYER_INFO ::             // 1
    e_CACHE_DECREMENT_IN_USE_COUNT :: // 2
    Nil) = Enum(UInt(), 3)
  // Cache to control field enum. nnsim-hdl equivalent:
  //   cache_types::field_enum
  val (e_CACHE_INFO ::     // 0
    e_CACHE_LAYER ::       // 1
    e_CACHE_NEURON ::      // 2
    e_CACHE_WEIGHT ::      // 3
    e_CACHE_WEIGHT_ONLY :: // 4
    e_CACHE_WEIGHT_WB ::   // 5
    Nil) = Enum(UInt(), 6)
  // Cache / PE access type enum. nnsim-hdl equivalent:
  //   pe_types::pe2storage_enum
  val (e_PE_NEURON :: // 0
    e_PE_WEIGHT ::    // 1
    Nil) = Enum(UInt(), 2)
  // PE State
  val PE_states = Enum(UInt(),
    List('e_PE_UNALLOCATED,                               // 0
      'e_PE_GET_INFO,                                     // 1
      'e_PE_REQUEST_INPUTS_AND_WEIGHTS,                   // 2
      'e_PE_RUN,                                          // 3
      'e_PE_ACTIVATION_FUNCTION,                          // 4
      'e_PE_COMPUTE_DERIVATIVE,                           // 5
      'e_PE_REQUEST_EXPECTED_OUTPUT,                      // 6
      'e_PE_COMPUTE_ERROR,                                // 7
      'e_PE_ERROR_FUNCTION,                               // 8
      'e_PE_COMPUTE_DELTA,                                // 9
      'e_PE_ERROR_BACKPROP_REQUEST_WEIGHTS,               // 10
      'e_PE_ERROR_BACKPROP_DELTA_WEIGHT_MUL,              // 11
      'e_PE_ERROR_BACKPROP_WEIGHT_WB,                     // 12
      'e_PE_REQUEST_OUTPUTS_ERROR_BACKPROP,               // 13
      'e_PE_REQUEST_DELTA_WEIGHT_PRODUCT_ERROR_BACKPROP,  // 14
      'e_PE_RUN_UPDATE_SLOPE,                             // 15
      'e_PE_SLOPE_WB,                                     // 16
      'e_PE_SLOPE_BIAS_WB,                                // 17
      'e_PE_RUN_WEIGHT_UPDATE,                            // 19
      'e_PE_WEIGHT_UPDATE_WRITE_BACK,                     // 20
      'e_PE_WEIGHT_UPDATE_WRITE_BIAS,                     // 21
      'e_PE_WEIGHT_UPDATE_REQUEST_BIAS,                   // 22
      'e_PE_WEIGHT_UPDATE_COMPUTE_BIAS,                   // 23
      'e_PE_DONE,                                         // 24
      'e_PE_ERROR))                                       // 25
  val (e_PE_REQ_INPUT ::             // 0
    e_PE_REQ_EXPECTED_OUTPUT ::      // 1
    e_PE_REQ_OUTPUT ::               // 2
    e_PE_REQ_BIAS ::                 // 4
    e_PE_REQ_DELTA_WEIGHT_PRODUCT :: // 5
    e_PE_WRITE_ELEMENT ::            // 6
    e_PE_WRITE_BLOCK_NEW ::          // 7
    e_PE_WRITE_BLOCK_ACC ::          // 8
    e_PE_INCREMENT_WRITE_COUNT ::    // 9
    Nil) = Enum(UInt(), 9)
  // Location of inputs and outputs
  val (e_LOCATION_REG_0 :: // 0
    e_LOCATION_REG_1 ::    // 1
    e_LOCATION_IO ::       // 2
    Nil) = Enum(UInt(), 3)
  // FANN Activation Function enums. These should match FANN!
  val (e_FANN_LINEAR ::                  // 0
    e_FANN_THRESHOLD ::                  // 1
    e_FANN_THRESHOLD_SYMMETRIC ::        // 2
    e_FANN_SIGMOID ::                    // 3
    e_FANN_SIGMOID_STEPWISE ::           // 4
    e_FANN_SIGMOID_SYMMETRIC ::          // 5
    e_FANN_SIGMOID_SYMMETRIC_STEPWISE :: // 6
    e_FANN_GAUSSIAN ::                   // 7
    e_FANN_GAUSSIAN_SYMMETRIC ::         // 8
    e_FANN_GAUSSIAN_STEPWISE ::          // 9
    e_FANN_ELLIOT ::                     // 10
    e_FANN_ELLIOT_SYMMETRIC ::           // 11
    e_FANN_LINEAR_PIECE ::               // 12
    e_FANN_LINEAR_PIECE_SYMMETRIC ::     // 13
    e_FANN_SIN_SYMMETRIC ::              // 14
    e_FANN_COS_SYMMETRIC ::              // 15
    e_FANN_SIN ::                        // 16
    e_FANN_CO ::                         // 17
    Nil) = Enum(UInt(), 18)
  // FANN error functions
  val (e_FANN_ERRORFUNC_LINEAR :: // 0
    e_FANN_ERRORFUNC_TANH ::      // 1
    Nil) = Enum(UInt(), 2)
  // Determin what the actiavtion function unit is doing
  val (e_AF_DO_ACTIVATION_FUNCTION :: // 0
    e_AF_DO_ERROR_FUNCTION ::         // 1
    Nil) = Enum(UInt(), 2)
  // Transaction Table to Register File Types
  val (e_TTABLE_REGFILE_WRITE :: // 0
    e_TTABLE_REGFILE_READ ::     // 1
    Nil) = Enum(UInt(), 2)       // 2 Total
}

// Base class for all Bundle classes used in DANA. This sets all the
// parameters that should be shared. All parameters defined here
// should be the same as in DanaModule.
abstract class DanaBundle(implicit p: Parameters) extends XFilesBundle()(p)
    with DanaParameters

class Dana(implicit p: Parameters) extends XFilesBackend()(p)
    with DanaParameters {

  // Module instantiation
  val control = if (learningEnabled) Module(new ControlLearn) else
    Module(new Control)
  val cache = if (learningEnabled) Module(new CacheLearn) else
    Module(new Cache)
  val peTable = if (learningEnabled) Module(new ProcessingElementTableLearn) else
    Module(new ProcessingElementTable)
  val regFile = if (learningEnabled) Module(new RegisterFileLearn) else
    Module(new RegisterFile)
  val antw = Module(new AsidNnidTableWalker)

  val tTable = if (learningEnabled) Module(new TransactionTableLearn) else
    Module(new TransactionTable)

  // Wire everything up. Ordering shouldn't matter here.
  cache.io.control <> control.io.cache
  control.io.peTable <> peTable.io.control
  control.io.regFile <> regFile.io.control
  peTable.io.cache <> cache.io.pe
  peTable.io.regFile <> regFile.io.pe

  // ASID--NNID Table Walker
  antw.io.xfiles.rocc.cmd.valid := io.rocc.cmd.valid
  antw.io.xfiles.rocc.cmd.bits := io.rocc.cmd.bits
  antw.io.xfiles.rocc.resp.ready := io.rocc.resp.ready
  antw.io.xfiles.rocc.status := io.rocc.status
  antw.io.xfiles.rocc.coreIdxCmd := io.regIdx.cmd

  antw.io.cache <> cache.io.mem
  antw.io.xfiles.dcache.mem <> io.rocc.mem
  io.memIdx.cmd := antw.io.xfiles.dcache.coreIdxReq
  antw.io.xfiles.dcache.coreIdxResp := io.memIdx.resp

  // Arbitration between TTable and ANTW
  io.rocc.cmd.ready := antw.io.xfiles.rocc.cmd.ready &
    tTable.io.arbiter.rocc.cmd.ready
  io.rocc.resp.valid := tTable.io.arbiter.rocc.resp.valid
  io.rocc.resp.bits := tTable.io.arbiter.rocc.resp.bits
  tTable.io.arbiter.rocc.resp.ready := io.rocc.resp.ready
  io.regIdx.resp := tTable.io.arbiter.indexOut
  when (antw.io.xfiles.rocc.resp.valid) {
    io.rocc.resp.valid := antw.io.xfiles.rocc.resp.valid
    io.rocc.resp.bits := antw.io.xfiles.rocc.resp.bits
    io.regIdx.resp := antw.io.xfiles.rocc.coreIdxResp
  }
  assert(!(tTable.io.arbiter.rocc.resp.valid & antw.io.xfiles.rocc.resp.valid),
    "ANTW register response just aliased DANA's Transaction TAble")

  // Transaction Table
  tTable.io.arbiter.rocc.cmd.valid := io.rocc.cmd.valid
  tTable.io.arbiter.rocc.cmd.bits := io.rocc.cmd.bits
  tTable.io.arbiter.rocc.resp.ready := io.rocc.resp.ready
  tTable.io.arbiter.rocc.status := io.rocc.status
  tTable.io.arbiter.coreIdx := io.regIdx.cmd

  tTable.io.control <> control.io.tTable
  tTable.io.regFile <> regFile.io.tTable

  tTable.io.arbiter.xfReq <> io.xfReq
  tTable.io.arbiter.xfResp <> io.xfResp
  tTable.io.arbiter.queueIO <> io.queueIO

  when (io.rocc.cmd.valid) {
    printfInfo("Dana: io.tTable.rocc.cmd.valid asserted\n")}
}

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

// [TODO] These are all unused legacy interfaces that were originally
// used for testing. These need to be cleaned up.
class XFilesArbiterReq(implicit p: Parameters) extends DanaBundle()(p) {
  val tid = UInt(width = tidWidth)
  val readOrWrite = Bool()
  val isNew = Bool()
  val isLast = Bool()
  val data = UInt(width = elementWidth)
}

class XFilesArbiterResp(implicit p: Parameters) extends DanaBundle()(p) {
  val tid = UInt(width = tidWidth)
  val data = UInt(width = elementWidth)
}

class XFilesArbiterInterface(implicit p: Parameters) extends DanaBundle()(p) {
  val req = Decoupled(new XFilesArbiterReq)
  val resp = Decoupled(new XFilesArbiterResp).flip
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
            peek(c.table(i).flags.valid),
            peek(c.table(i).flags.reserved),
            peek(c.table(i).cacheValid),
            peek(c.table(i).waiting),
            peek(c.table(i).needsLayerInfo),
            peek(c.table(i).flags.done),
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

class DanaTests(uut: Dana, isTrace: Boolean = true)
    extends DanaTester(uut, isTrace) {
  for (t <- 0 until 3) {
    val tid = t
    val nnid = t + 15 * 16
    // newWriteRequest(uut.io.arbiter, tid, nnid)
    // writeRndData(uut.io.arbiter, tid, nnid, 5, 10)
    // info(uut.tTable)
    step(3)
  }
}
