// See LICENSE for license details

package xfiles

import Chisel._
import rocket.RoCCInterface
import cde.{Parameters, Field}

case object BuildXFilesBackend extends Field[XFilesBackendParameters]
case class XFilesBackendParameters(
  generator: Parameters => XFilesBackend,
  info: Int = 0)

class InterruptBundle(implicit p: Parameters) extends XFilesBundle()(p) {
  val code = UInt(OUTPUT, width = xLen)
}

class XFilesBackendReq(implicit p: Parameters) extends XFilesBundle()(p) {
  val tidx = Decoupled(UInt(INPUT, width = log2Up(transactionTableNumEntries)))
}

class XFilesBackendResp(implicit p: Parameters) extends XFilesBundle()(p) {
  val tidx = Valid(UInt(OUTPUT, width = log2Up(transactionTableNumEntries)))
  val flags = (new Bundle with FlagsVDIO).asOutput
}

class XFilesRs1Rs2Funct(implicit p: Parameters) extends XFilesBundle()(p) {
  val rs1 = UInt(width = xLen)
  val rs2 = UInt(width = xLen)
  val funct = UInt(width = 7)
}

class XFilesQueueInterface(implicit p: Parameters) extends XFilesBundle()(p) {
  val tidxIn = UInt(OUTPUT, width = log2Up(transactionTableNumEntries))
  val tidxOut = UInt(OUTPUT, width = log2Up(transactionTableNumEntries))
  // The naming here follows what is connected to the XF TTable Input
  // and Ouptut queues. Alternatively, this is from the perspective of
  // data flowing into (in) and out of (out) the backend
  val in = Decoupled(new XFilesRs1Rs2Funct).flip
  val out = Decoupled(UInt(width = xLen))
}

class XFilesBackendInterface(implicit p: Parameters)
    extends XFilesBundle()(p) {
  val rocc = new RoCCInterface
  val xfReq = (new XFilesBackendReq).flip
  val xfResp = new XFilesBackendResp
  val queueIO = new XFilesQueueInterface
  val interrupt = Valid(new InterruptBundle)
}

class XFilesBackend(implicit p: Parameters) extends XFilesModule()(p) {
  val io = new XFilesBackendInterface
}
