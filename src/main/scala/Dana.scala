package dana

// Grab junctions for the ParameterizedBundle class
import junctions._
import rocket._
import Chisel._
import cde.{Parameters, Field}

case object ElementWidth extends Field[Int]
case object ElementsPerBlock extends Field[Int]
case object TidWidth extends Field[Int]
case object AsidWidth extends Field[Int]
case object ActivationFunctionWidth extends Field[Int]
case object NnidWidth extends Field[Int]
case object DecimalPointOffset extends Field[Int]
case object DecimalPointWidth extends Field[Int]
case object SteepnessWidth extends Field[Int]
case object SteepnessOffset extends Field[Int]
case object ErrorFunctionWidth extends Field[Int]
case object FeedbackWidth extends Field[Int]
case object PeTableNumEntries extends Field[Int]
case object TransactionTableNumEntries extends Field[Int]
case object CacheNumEntries extends Field[Int]
case object CacheDataSize extends Field[Int]
case object RegisterFileNumElements extends Field[Int]
case object LearningEnabled extends Field[Boolean]

trait DanaParameters extends HasCoreParameters {
  val elementWidth = p(ElementWidth)
  val elementsPerBlock = p(ElementsPerBlock)
  val tidWidth = p(TidWidth)
  val asidWidth = p(AsidWidth)
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

  // Processing Element Table
  val peTableNumEntries = p(PeTableNumEntries)
  // Transaction Table
  val transactionTableNumEntries = p(TransactionTableNumEntries)
  // Configuration Cache
  val cacheNumEntries = p(CacheNumEntries)
  val cacheDataSize = p(CacheDataSize)
  // Register File
  val regFileNumElements = p(RegisterFileNumElements)

  // Derived parameters
  val regFileNumBlocks =
    divUp(p(RegisterFileNumElements), p(ElementsPerBlock))
  val cacheNumBlocks =
    divUp(divUp((p(CacheDataSize) * 8), p(ElementWidth)),
      p(ElementsPerBlock))
  // [TODO] This ioIdxWidth looks wrong?
  val ioIdxWidth = log2Up(p(RegisterFileNumElements) * p(ElementWidth))
  val bitsPerBlock = p(ElementsPerBlock) * p(ElementWidth)
  val learningEnabled = p(LearningEnabled)

  def divUp (dividend: Int, divisor: Int): Int = {
    (dividend + divisor - 1) / divisor}
}

// An abstract base class for anything associated with DANA (and the
// X-FILES framework?). This defines all shared DANA parameters.
abstract class DanaModule(implicit val p: Parameters) extends Module
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
  val (e_TTABLE_STATE_LOAD_OUTPUTS ::      // 0
    e_TTABLE_STATE_FEEDFORWARD ::          // 1
    e_TTABLE_STATE_LEARN_FEEDFORWARD ::    // 2
    e_TTABLE_STATE_LEARN_ERROR_BACKPROP :: // 3
    e_TTABLE_STATE_LEARN_WEIGHT_STORE ::   // 4
    e_TTABLE_STATE_LEARN_UPDATE_SLOPE ::   // 5
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
      'e_PE_DELTA_WRITE_BACK,                             // 10
      'e_PE_ERROR_BACKPROP_REQUEST_WEIGHTS,               // 11
      'e_PE_ERROR_BACKPROP_DELTA_WEIGHT_MUL,              // 12
      'e_PE_ERROR_BACKPROP_WEIGHT_WB,                     // 13
      'e_PE_REQUEST_OUTPUTS_ERROR_BACKPROP,               // 14
      'e_PE_REQUEST_DELTA_WEIGHT_PRODUCT_ERROR_BACKPROP,  // 15
      'e_PE_RUN_UPDATE_SLOPE,                             // 16
      'e_PE_SLOPE_WB,                                     // 17
      'e_PE_SLOPE_BIAS_WB,                                // 18
      'e_PE_WEIGHT_UPDATE_REQUEST_DELTA,                  // 19
      'e_PE_RUN_WEIGHT_UPDATE,                            // 20
      'e_PE_WEIGHT_UPDATE_WRITE_BACK,                     // 21
      'e_PE_WEIGHT_UPDATE_WRITE_BIAS,                     // 22
      'e_PE_WEIGHT_UPDATE_REQUEST_BIAS,                   // 23
      'e_PE_WEIGHT_UPDATE_WAIT_FOR_BIAS_d0,               // 24
      'e_PE_DONE,                                         // 25
      'e_PE_ERROR))                                       // 26
  val (e_PE_REQ_INPUT ::             // 0
    e_PE_REQ_EXPECTED_OUTPUT ::      // 1
    e_PE_REQ_OUTPUT ::               // 2
    e_PE_REQ_DELTA ::                // 3
    e_PE_REQ_BIAS ::                 // 4
    e_PE_REQ_DELTA_WEIGHT_PRODUCT :: // 5
    e_PE_WRITE_ELEMENT ::            // 6
    e_PE_WRITE_BLOCK_NEW ::          // 7
    e_PE_WRITE_BLOCK_ACC ::          // 8
    e_PE_INCREMENT_WRITE_COUNT ::    // 9
    Nil) = Enum(UInt(), 10)
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
  // Response type that will be sent to a core
  val (e_TID ::   // 0
    e_READ ::     // 1
    e_NOT_DONE :: // 2
    Nil) = Enum(UInt(), 3)
  // Transaction Table to Register File Types
  val (e_TTABLE_REGFILE_WRITE :: // 0
    e_TTABLE_REGFILE_READ ::     // 1
    Nil) = Enum(UInt(), 2)       // 2 Total
}

// Base class for all Bundle classes used in DANA. This sets all the
// parameters that should be shared. All parameters defined here
// should be the same as in DanaModule.
abstract class DanaBundle(implicit val p: Parameters)
    extends junctions.ParameterizedBundle()(p)
    with DanaParameters

class Dana(implicit p: Parameters) extends DanaModule {
  val io = (new XFilesDanaInterface).flip

  // Module instantiation
  // val tTable = Module(new TransactionTable)
  val control = Module(new Control)
  val cache = Module(new Cache)
  val peTable = if (learningEnabled) Module(new ProcessingElementTableLearn) else
    Module(new ProcessingElementTable)
  val regFile = Module(new RegisterFile)

  // Wire everything up. Ordering shouldn't matter here.
  // io.arbiter <> tTable.io.arbiter
  // tTable.io.control <> control.io.tTable
  io.control <> control.io.tTable
  cache.io.control <> control.io.cache
  control.io.peTable <> peTable.io.control
  control.io.regFile <> regFile.io.control
  peTable.io.cache <> cache.io.pe
  // peTable.io.tTable <> tTable.io.peTable
  // peTable.io.tTable <> io.peTable
  regFile.io.tTable <> io.regFile
  peTable.io.regFile <> regFile.io.pe
  cache.io.mem <> io.cache
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
