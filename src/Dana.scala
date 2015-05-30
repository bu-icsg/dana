package dana

import Chisel._

case object ElementWidth extends Field[Int]
case object ElementsPerBlock extends Field[Int]
case object TidWidth extends Field[Int]
case object AsidWidth extends Field[Int]
case object ActivationFunctionWidth extends Field[Int]
case object NnidWidth extends Field[Int]
case object DecimalPointOffset extends Field[Int]
case object DecimalPointWidth extends Field[Int]
case object SteepnessWidth extends Field[Int]
case object FeedbackWidth extends Field[Int]
case object PeTableNumEntries extends Field[Int]
case object TransactionTableNumEntries extends Field[Int]
case object CacheNumEntries extends Field[Int]
case object CacheDataSize extends Field[Int]
case object TransactionTableSramElements extends Field[Int]
case object RegisterFileNumElements extends Field[Int]

abstract trait DanaParameters extends UsesParameters {
  def divUp (dividend: Int, divisor: Int): Int = {
    (dividend + divisor - 1) / divisor}
  val elementWidth = params(ElementWidth)
  val elementsPerBlock = params(ElementsPerBlock)
  val tidWidth = params(TidWidth)
  val asidWidth = params(AsidWidth)
  // Activation Function width increases will break:
  //   * ProcessingElementTable logic for indexing into cache data
  val activationFunctionWidth = params(ActivationFunctionWidth)
  val nnidWidth = params(NnidWidth)
  val decimalPointOffset = params(DecimalPointOffset)
  val decimalPointWidth = params(DecimalPointWidth)
  // Steepness width increases will break:
  //   * ProcessingElementTable logic for indexing into cache data
  val steepnessWidth = params(SteepnessWidth)
  val feedbackWidth = params(FeedbackWidth)

  // Processing Element Table
  val peTableNumEntries = params(PeTableNumEntries)
  // Transaction Table
  val transactionTableSramElements = params(TransactionTableSramElements)
  val transactionTableNumEntries = params(TransactionTableNumEntries)
  // Configuration Cache
  val cacheNumEntries = params(CacheNumEntries)
  val cacheDataSize = params(CacheDataSize)
  // Register File
  val regFileNumElements = params(RegisterFileNumElements)

  // Derived parameters
  val transactionTableSramBlocks =
    divUp(params(TransactionTableSramElements),  params(ElementsPerBlock))
  val regFileNumBlocks =
    divUp(params(RegisterFileNumElements), params(ElementsPerBlock))
  val cacheNumBlocks =
    divUp(divUp(params(CacheDataSize), params(ElementsPerBlock)),
      params(ElementWidth)) * 8
  val ioIdxWidth = if (params(TransactionTableSramElements) > params(RegisterFileNumElements))
    log2Up(params(TransactionTableSramElements) * params(ElementWidth)) else
      log2Up(params(RegisterFileNumElements) * params(ElementWidth))
  val bitsPerBlock = params(ElementsPerBlock) * params(ElementWidth)
}

// An abstract base class for anything associated with DANA (and the
// X-FILES framework?). This defines all shared DANA parameters.
abstract class DanaModule extends Module with DanaParameters {
  // Transaction Table State Entries. nnsim-hdl equivalent:
  //   controL_types::field_enum
  val (e_TTABLE_VALID ::       // 0
    e_TTABLE_RESERVED ::       // 1
    e_TTABLE_CACHE_VALID ::    // 2
    e_TTABLE_LAYER ::          // 3
    e_TTABLE_WAITING ::        // 4
    e_TTABLE_STOP_WAITING ::   // 5
    e_TTABLE_DONE ::           // 6
    e_TTABLE_OUTPUT_LAYER ::   // 7
    e_TTABLE_INCREMENT_NODE :: // 8
    e_TTABLE_REGISTER_INFO ::  // 9
    e_TTABLE_REGISTER_NEXT ::  // 10
    Nil) = Enum(UInt(), 11)
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
    e_PE_REQUEST_INPUTS_AND_WEIGHTS ::  // 3
    e_PE_WAIT_FOR_INPUTS_AND_WEIGHTS :: // 4
    e_PE_RUN ::                         // 5
    e_PE_ACTIVATION_FUNCTION ::         // 6
    e_PE_DONE ::                        // 7
    Nil) = Enum(UInt(), 8)
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
}

// Base class for all Bundle classes used in DANA. This sets all the
// parameters that should be shared. All parameters defined here
// should be the same as in DanaModule.
abstract class DanaBundle extends Bundle with DanaParameters

class Dana extends DanaModule {
  val io = (new XFilesDanaInterface).flip

  // Half clock hack
  // val halfClock = new Clock(reset) / 2
  // val clockInternal = Reg(init=UInt(0), clock = halfClock)
  // debug(clockInternal)
  // clockInternal := ~clockInternal

  // Module instantiation
  // val tTable = Module(new TransactionTable)
  val control = Module(new Control)
  val cache = Module(new Cache)
  val mem = Module(new Memory)
  val peTable = Module(new ProcessingElementTable)
  val regFile = Module(new RegisterFile)

  // Wire everything up. Ordering shouldn't matter here.
  // io.arbiter <> tTable.io.arbiter
  // tTable.io.control <> control.io.tTable
  io.control <> control.io.tTable
  cache.io.control <> control.io.cache
  cache.io.mem <> mem.io.cache
  control.io.peTable <> peTable.io.control
  control.io.regFile <> regFile.io.control
  peTable.io.cache <> cache.io.pe
  // peTable.io.tTable <> tTable.io.peTable
  peTable.io.tTable <> io.peTable
  peTable.io.regFile <> regFile.io.pe
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
