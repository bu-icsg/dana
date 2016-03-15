// See LICENSE for license details

package dana

import Chisel._
import rocket.{RoCCInterface}
import cde.{Parameters}

class CoreIdx(implicit p: Parameters) extends XFilesBundle()(p) {
  val cmd = UInt(OUTPUT, width = log2Up(numCores))
  val resp = UInt(INPUT, width = log2Up(numCores))
}

class XFilesBackendInterface(implicit p: Parameters)
    extends XFilesBundle()(p) {
  val rocc = new RoCCInterface
  val regIdx = (new CoreIdx).flip
  val memIdx = new CoreIdx
}

class XFilesBackend(implicit p: Parameters) extends XFilesModule()(p) {
  val io = new XFilesBackendInterface
}
