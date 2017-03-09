// See LICENSE.BU for license details.
// See LICENSE.IBM for license details.

package xfiles

import chisel3._
import chisel3.util._
import rocket.{RoCCInterface, HasCoreParameters}
import config._
import _root_.util.ParameterizedBundle

case object BuildXFilesBackend extends Field[XFilesBackendParameters]
case class XFilesBackendParameters(
  generator: Parameters => XFilesBackend,
  csrFile_gen: Parameters => CSRFile,
  csrData_gen: Parameters => XFStatus,
  info: Long = 0
)

class InterruptBundle(implicit p: Parameters) extends XFilesBundle()(p) {
  val code = Output(UInt(xLen.W))
}

class XFilesBackendReq(implicit p: Parameters) extends XFilesBundle()(p) {
  val tidx = Decoupled(UInt(log2Up(transactionTableNumEntries).W))
}

class XFilesBackendResp(implicit p: Parameters) extends XFilesBundle()(p) {
  val tidx = Valid(UInt(log2Up(transactionTableNumEntries).W))
  val flags = Output(new Bundle with FlagsVDIO)
}

class XFilesRs1Rs2Funct(implicit val p: Parameters)
    extends ParameterizedBundle()(p) with HasCoreParameters {
  val rs1 = UInt(xLen.W)
  val rs2 = UInt(xLen.W)
  val funct = UInt(7.W)
}

class XFilesQueueInterface(implicit p: Parameters) extends XFilesBundle()(p) {
  val tidxIn = Output(UInt(log2Up(transactionTableNumEntries).W))
  val tidxOut = Output(UInt(log2Up(transactionTableNumEntries).W))
  // The naming here follows what is connected to the XF TTable Input
  // and Ouptut queues. Alternatively, this is from the perspective of
  // data flowing into (in) and out of (out) the backend
  val in = Flipped(Decoupled(new XFilesRs1Rs2Funct))
  val out = Decoupled(UInt(xLen.W))
}

class XFilesBackendInterface(implicit p: Parameters) extends XFilesBundle()(p) {
  val rocc = new RoCCInterface
  val xfReq = Flipped(new XFilesBackendReq)
  val xfResp = new XFilesBackendResp
  val xfQueue = new XFilesQueueInterface
  val status = Input(p(BuildXFilesBackend).csrData_gen(p))
  val interrupt = Valid(new InterruptBundle)
}

class XFilesBackend(implicit p: Parameters)
    extends XFilesModule()(p) {
  val io = IO(new XFilesBackendInterface)
}
