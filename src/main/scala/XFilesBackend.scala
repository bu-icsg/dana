// See LICENSE for license details

package dana

import Chisel._
import cde.{Parameters}

class XFilesBackendInterface(implicit p: Parameters)
    extends XFilesBundle()(p) {
  val antw = new ANTWXFilesInterface
  val tTable = new TTableArbiter
}

class XFilesBackend(implicit p: Parameters) extends XFilesModule()(p) {
  val io = new XFilesBackendInterface
}
