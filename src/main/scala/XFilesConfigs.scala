// See LICENSE for license details.

package xfiles

import Chisel._
import cde.{Parameters, Config, Dump, Knob}
import dana.{DefaultDanaConfig}

class DefaultXFilesConfig extends Config (
  topDefinitions = { (pname,site,here) =>
    pname match {
      case NumCores => Dump(Knob("NUM_CORES"))
      case TidWidth => Dump("TID_WIDTH", 16)
      case AsidWidth => Dump("ASID_WIDTH", 16)
      case DebugEnabled => false
      case TableDebug => true
      case TransactionTableNumEntries => Dump(Knob("TRANSACTION_TABLE_NUM_ENTRIES"))
      case TransactionTableQueueSize => 2
    }},
  knobValues = {
    case "NUM_CORES" => 1
    case "TRANSACTION_TABLE_NUM_ENTRIES" => 1
  }
)

class XFilesDebugConfig extends Config (
  topDefinitions = { (pname,site,here) =>
    pname match {
      case DebugEnabled => true
    }}
)

class DefaultXFilesDanaFPGAConfig extends Config(new DefaultXFilesConfig ++
  new DefaultDanaConfig)
