// See LICENSE for license details.

package xfiles

import chisel3._
import config._
import dana.DefaultDanaConfig

class DefaultXFilesConfig extends Config (
  (pname,site,here) =>
  pname match {
    case TidWidth                   => 16
    case AsidWidth                  => 16
    case DebugEnabled               => false
    case TableDebug                 => true
    case TransactionTableNumEntries => 1
    case TransactionTableQueueSize  => 32
    case _ => throw new CDEMatchError
  }
)

class XFilesDebugConfig extends Config (
  (pname,site,here) =>
  pname match {
    case DebugEnabled => true
    case _ => throw new CDEMatchError
  }
)
