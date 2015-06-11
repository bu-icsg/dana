package dana

import Chisel._

class DefaultConfig extends ChiselConfig (
  topDefinitions = { (pname,site,here) =>
    pname match {
      // Core parameters
      case NumCores => Dump(Knob("NUM_CORES"))
      case XLen => 64
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
      case FeedbackWidth => Dump("FEEDBACK_WIDTH", 12)
      // Processing Element Table
      case PeTableNumEntries => Dump(Knob("NUM_PES"))
      // Transaction Table
      case TransactionTableNumEntries => Dump(Knob("TRANSACTION_TABLE_NUM_ENTRIES"))
      case TransactionTableSramElements => Dump(Knob("TRANSACTION_TABLE_SRAM_ELEMENTS"))
      // Configuration Cache
      case CacheNumEntries => Dump(Knob("CACHE_NUM_ENTRIES"))
      case CacheDataSize => 32 * 1024
      // Register File
      case RegisterFileNumElements => Dump(Knob("REGISTER_FILE_NUM_ELEMENTS"))
    }},
  // [TODO] Add constraints
  // topConstraints = List(
  //   { ex => ex(ElementWidth) == 32 },
  //   { ex => ex(ActivationFunctionWidth) <= 5 }
  // ),
  knobValues = {
    case "NUM_CORES" => 2
    case "ELEMENTS_PER_BLOCK" => 8
    case "NUM_PES" => 6
    case "TRANSACTION_TABLE_NUM_ENTRIES" => 4
    case "TRANSACTION_TABLE_SRAM_ELEMENTS" => 64
    case "CACHE_NUM_ENTRIES" => 4
    case "REGISTER_FILE_NUM_ELEMENTS" => 80
  }
)
