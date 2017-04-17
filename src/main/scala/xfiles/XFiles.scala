// See LICENSE.BU for license details.
// See LICENSE.IBM for license details.

package xfiles

import chisel3._
import chisel3.util._
import rocket.{RoCC, HasCoreParameters, CoreModule, CoreBundle}
import cde._
import math.pow
import _root_.util.ParameterizedBundle
import perfect.util.UniformPrintfs

case object TidWidth extends Field[Int]
case object AsidWidth extends Field[Int]
case object TableDebug extends Field[Boolean]
case object TransactionTableNumEntries extends Field[Int]
case object EnablePrintfs extends Field[Boolean]
case object EnableAsserts extends Field[Boolean]

trait XFilesErrorCodes {
  val err_XFILES_UNKNOWN = 0
  val err_XFILES_NOASID = 1
  val err_XFILES_TTABLEFULL = 2
  val err_XFILES_INVALIDTID = 3

  val int_INVREQ = 0
}

trait XFilesSupervisorRequests {
  // Supervisor requests are < 4
  val t_SUP_UPDATE_ASID = 0
  val t_SUP_WRITE_REG = 1
  val t_SUP_READ_CSR = 2
  val t_SUP_WRITE_CSR = 3
}

trait XFilesUserRequests {
  // User requests are >= 4
  val t_USR_READ_DATA = 4
  val t_USR_WRITE_DATA = 5
  val t_USR_NEW_REQUEST = 6
  val t_USR_WRITE_DATA_LAST = 7
  val t_USR_WRITE_REGISTER = 8
  val t_USR_XFILES_DEBUG = 9
}

trait XFilesParameters {
  implicit val p: Parameters

  val tidWidth = p(TidWidth)
  val asidWidth = p(AsidWidth)
  val transactionTableNumEntries = p(TransactionTableNumEntries)

  val tableDebug = p(TableDebug)

  val k_NULL_ASID = pow(2, asidWidth) - 1
}

trait XFilesResponseCodes extends HasCoreParameters with XFilesParameters
    with UniformPrintfs {
  val respCodeWidth = 3

  val (resp_OK :: resp_TID :: resp_READ :: resp_NOT_DONE :: resp_QUEUE_ERR ::
    resp_XFILES :: Nil) =  Enum(6)

  def genResp[T <: Bits](resp: T, respCode: T, tid: T, data: T = 0.U(xLen.W)) {
    val tmp = Wire(new Bundle {
      val respCode = UInt(respCodeWidth.W)
      val tid = UInt(tidWidth.W)
      val data = UInt((xLen - respCodeWidth - tidWidth).W)
    })
    tmp.respCode := respCode.asUInt
    tmp.tid := tid.asUInt
    tmp.data := data
    resp := tmp.asUInt
    printfInfo("Packing response 0x%x from (respCode: 0x%x, tid: 0x%x, data: 0x%x)\n",
      tmp.asUInt, tmp.respCode, tmp.tid, tmp.data)
  }
}

abstract class XFilesBundle(implicit val p: Parameters)
    extends ParameterizedBundle()(p) with HasCoreParameters
    with XFilesParameters with XFilesErrorCodes
    with XFilesUserRequests with UniformPrintfs {

  val aliasList = scala.collection.mutable.Map[String, String]()
  def alias (name: String): String = {
    if (aliasList.contains(name)) {
      return aliasList(name)
    } else {
      return name
    }
  }

  // Return a CSV list of all the elements in this bundle
  def printElements(prepend: String = ""): String = {
    var res = "[DEBUG]" + prepend
    var sep = ""
    for ((n, i) <- elements) {
      res += sep + alias(n)
      sep = ","
    }
    res += "\n"
    res
  }

  // Return a (String, Seq[Bits]) tuple suitable for passing to printf
  // that contains the values of all the elements in the bundle
  def printAll(prepend: String = ""): (String, Seq[Bits]) = {
    var format = "[DEBUG]" + prepend
    var sep = ""
    var argsIn = Seq[Bits]()
    for ((n, i) <- elements) {
      format += sep + "%x"
      sep = ","
      argsIn = argsIn :+ i.asUInt
    }
    format += "\n"
    (format, argsIn)
  }
}

abstract class XFilesModule(implicit p: Parameters) extends CoreModule()(p)
    with XFilesParameters with XFilesErrorCodes with XFilesUserRequests
    with UniformPrintfs {

  // Create a tupled version of printf
  def printfe(s: String, d: Seq[Bits]) = {
    printf(s, d:_*)
  }
  val printft = (printfe _).tupled

  // Info method that will dump the state of a table
  def info[T <: XFilesBundle](x: Vec[T], prepend: String = "") = {
    if (tableDebug) {
      printf(x(0).printElements(prepend))
        (0 until x.length).map(i => printft(x(i).printAll(","))) }}
}

trait HasXFilesBackend {
  implicit val p: Parameters
  val buildBackend = p(BuildXFilesBackend)
}

class XFiles(implicit p: Parameters) extends RoCC()(p)
    with HasCoreParameters with HasXFilesBackend {
  val backend = buildBackend.generator(p)
  val xFilesArbiter = Module(new XFilesArbiter)

  // Core -> Arbiter connections
  xFilesArbiter.io.core.cmd <> io.cmd
  io.resp <> xFilesArbiter.io.core.resp
  xFilesArbiter.io.core.mem.resp <> io.mem.resp
  io.ptw <> xFilesArbiter.io.core.ptw
  io.autl <> xFilesArbiter.io.core.autl
  io.utl <> xFilesArbiter.io.core.utl
  xFilesArbiter.io.core.cmd.bits.status := io.cmd.bits.status
  io.interrupt := xFilesArbiter.io.core.interrupt
  io.busy := xFilesArbiter.io.core.busy

  // The mem (L1 dcache) connection must be done manually due to the
  // explicit setting of mem.req.phys in rocc.scala.
  io.mem.req.valid := xFilesArbiter.io.core.mem.req.valid
  xFilesArbiter.io.core.mem.req.ready := io.mem.req.ready
  io.mem.req.bits := xFilesArbiter.io.core.mem.req.bits
  io.mem.invalidate_lr := xFilesArbiter.io.core.mem.invalidate_lr

  // Connect the backend to the arbiter
  xFilesArbiter.io.backend <> backend.io
}
