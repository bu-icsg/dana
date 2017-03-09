// See LICENSE.IBM for license details.

package xfiles

import chisel3._
import config._
import dana.DefaultDanaConfig

class DefaultXFilesConfig extends Config (
  (pname,site,here) =>
  pname match {
    case TidWidth                   => 16
    case AsidWidth                  => 16
    case TableDebug                 => true
    case TransactionTableNumEntries => 2
    case TransactionTableQueueSize  => 32
    case _ => throw new CDEMatchError
  }
)
