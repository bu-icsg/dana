package dana

import Chisel._

class DefaultConfig extends ChiselConfig (
  topDefinitions = { (pname,site,here) =>
    pname match {
      // Field widths
      case ElementWidth => 32
      case ElementsPerBlock => Knob("ELEMENTS_PER_BLOCK")
      case TidWidth => 16
      case ActivationFunctionWidth => 5
      case NnidWidth => 16
      case DecimalPointOffset => 7
      case DecimalPointWidth => 3
      case SteepnessWidth => 3
      case FeedbackWidth => 12
      // Processing Element Table
      case PeTableNumEntries => Knob("NUM_PES")
      // Transaction Table
      case TransactionTableNumEntries => Knob("TRANSACTION_TABLE_NUM_ENTRIES")
      case TransactionTableSramElements => 32
      // Configuration Cache
      case CacheNumEntries => Knob("CACHE_NUM_ENTRIES")
      case CacheDataSize => 32 * 1024
      // Register File
      case RegisterFileNumElements => Knob("REGISTER_FILE_NUM_ELEMENTS")
    }},
  // [TODO] Add in constraints at some point. The following is an
  // example from a repo on GitHub.
  // override val topConstraints:List[ViewSym=>Ex[Boolean]] = List(
  //   ex => isPowerOfTwo(ex[Int]("keywordsize"), 8, 64)
  // )
  knobValues = {
    case "ELEMENTS_PER_BLOCK" => 4
    case "NUM_PES" => 4
    case "TRANSACTION_TABLE_NUM_ENTRIES" => 4
    case "CACHE_NUM_ENTRIES" => 4
    case "REGISTER_FILE_NUM_ELEMENTS" => 80
  }
)
