package dana

import Chisel._

class DefaultConfig extends ChiselConfig (
  topDefinitions = { (pname,site,here) =>
    pname match {
      // Field widths
      case ElementWidth => 32
      case ElementsPerBlock => Dump(Knob("ELEMENTS_PER_BLOCK"))
      case TidWidth => 16
      case ActivationFunctionWidth => 5
      case NnidWidth => 16
      case DecimalPointOffset => 7
      case DecimalPointWidth => 3
      case SteepnessWidth => 3
      case FeedbackWidth => 12
      // Processing Element Table
      case PeTableNumEntries => Dump(Knob("NUM_PES"))
      // Transaction Table
      case TransactionTableNumEntries => Dump(Knob("TRANSACTION_TABLE_NUM_ENTRIES"))
      case TransactionTableSramElements => 32
      // Configuration Cache
      case CacheNumEntries => Dump(Knob("CACHE_NUM_ENTRIES"))
      case CacheDataSize => 32 * 1024
      // Register File
      case RegisterFileNumElements => Knob("REGISTER_FILE_NUM_ELEMENTS")
    }},
  // [TODO] Add constraints
  // topConstraints = List(
  //   { ex => ex(ElementWidth) == 32 },
  //   { ex => ex(ActivationFunctionWidth) <= 5 }
  // ),
  knobValues = {
    case "ELEMENTS_PER_BLOCK" => 4
    case "NUM_PES" => 1
    case "TRANSACTION_TABLE_NUM_ENTRIES" => 4
    case "CACHE_NUM_ENTRIES" => 4
    case "REGISTER_FILE_NUM_ELEMENTS" => 80
  }
)
