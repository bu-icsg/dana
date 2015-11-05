package dana

import Chisel._
import cde.{Parameters, Config, Dump, Knob}

class DefaultXFilesDanaConfig extends Config (
  topDefinitions = { (pname,site,here) =>
    def divUp (dividend: Int, divisor: Int): Int = {
      (dividend + divisor - 1) / divisor}
    pname match {
      // Core parameters
      case NumCores => Dump(Knob("NUM_CORES"))
      // ANTW Parameters
      case AntwRobEntries => 16
      // Field widths
      case ElementWidth => Dump("ELEMENT_WIDTH", 32)
      case ElementsPerBlock => Dump(Knob("ELEMENTS_PER_BLOCK"))
      case TidWidth => Dump("TID_WIDTH", 16)
      case AsidWidth => Dump("ASID_WIDTH", 16)
      case ActivationFunctionWidth => 5
      case NnidWidth => Dump("NNID_WIDTH", 16)
      case DecimalPointOffset => Dump("DECIMAL_POINT_OFFSET", 7)
      case DecimalPointWidth => Dump("DECIMAL_POINT_WIDTH", 3)
      case SteepnessWidth => 3
      // The steepness offset is the value you subtract from the
      // steepness to get the actual steepness
      case SteepnessOffset => 4
      case ErrorFunctionWidth => 1
      case FeedbackWidth => Dump("FEEDBACK_WIDTH", 12)
      // Processing Element Table
      case PeTableNumEntries => Dump(Knob("NUM_PES"))
      // Transaction Table
      case TransactionTableNumEntries => Dump(Knob("TRANSACTION_TABLE_NUM_ENTRIES"))
      // Configuration Cache
      case CacheNumEntries => Dump(Knob("CACHE_NUM_ENTRIES"))
      case CacheDataSize => 32 * 1024
      // Register File
      case RegisterFileNumElements => Dump(Knob("REGISTER_FILE_NUM_ELEMENTS"))
      // Enables support for in-hardware learning
      case LearningEnabled => true
      case BitsPerBlock => site(ElementsPerBlock) * site(ElementWidth)
      case RegFileNumBlocks => divUp(site(RegisterFileNumElements),
        site(ElementsPerBlock))
      case CacheNumBlocks => divUp(divUp((site(CacheDataSize) * 8),
        site(ElementWidth)), site(ElementsPerBlock))
    }},
  // [TODO] Add constraints
  // topConstraints = List(
  //   { ex => ex(ElementWidth) == 32 },
  //   { ex => ex(ActivationFunctionWidth) <= 5 }
  // ),
  knobValues = {
    case "NUM_CORES" => 1
    case "ELEMENTS_PER_BLOCK" => 4
    case "NUM_PES" => 1
    case "TRANSACTION_TABLE_NUM_ENTRIES" => 1
    case "CACHE_NUM_ENTRIES" => 2
    case "REGISTER_FILE_NUM_ELEMENTS" => 10240
  }
)

class DefaultXFilesDanaFPGAConfig extends Config(new DefaultXFilesDanaConfig)
