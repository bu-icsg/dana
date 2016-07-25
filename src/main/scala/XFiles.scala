// See LICENSE for license details.

package xfiles

import Chisel._

import rocket.{RoCC, HasCoreParameters, CoreModule, CoreBundle}
import cde.{Parameters, Field}
import math.pow
import junctions.{ParameterizedBundle, HasAddrMapParameters}

case object TidWidth extends Field[Int]
case object AsidWidth extends Field[Int]
case object DebugEnabled extends Field[Boolean]
case object TableDebug extends Field[Boolean]
case object TransactionTableNumEntries extends Field[Int]

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
}

trait XFilesUserRequests {
  // User requests are >= 4
  val t_USR_READ_DATA = 4
  val t_USR_WRITE_DATA = 5
  val t_USR_NEW_REQUEST = 6
  val t_USR_WRITE_DATA_LAST = 7
  val t_USR_WRITE_REGISTER = 8
  val t_USR_XFILES_DEBUG = 9
  val t_USR_XFILES_ID = 10
}

trait XFilesParameters {
  implicit val p: Parameters

  val tidWidth = p(TidWidth)
  val asidWidth = p(AsidWidth)
  val transactionTableNumEntries = p(TransactionTableNumEntries)

  val debugEnabled = p(DebugEnabled)
  val tableDebug = p(TableDebug)

  val k_NULL_ASID = pow(2, asidWidth) - 1
}

trait XFilesResponseCodes extends HasCoreParameters with XFilesParameters {
  val respCodeWidth = 3

  val (resp_OK :: resp_TID :: resp_READ :: resp_NOT_DONE :: resp_QUEUE_ERR ::
    resp_XFILES :: Nil) =  Enum(UInt(), 6)

  def genResp[T <: Bits](resp: T, respCode: T, tid: T,
    data: T = Bits(0, width = xLen)) {
    resp := data.toBits
    resp(xLen - 1, xLen - respCodeWidth) := respCode.toBits
    resp(xLen - respCodeWidth - 1, xLen - respCodeWidth - tidWidth) := tid.toBits
  }
}

abstract class XFilesBundle(implicit val p: Parameters)
    extends ParameterizedBundle()(p) with HasAddrMapParameters
    with HasCoreParameters with XFilesParameters with XFilesErrorCodes
    with XFilesUserRequests {

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
      argsIn = argsIn :+ i.toBits
    }
    format += "\n"
    (format, argsIn)
  }
}

abstract class XFilesModule(implicit p: Parameters) extends CoreModule()(p)
    with XFilesParameters with XFilesErrorCodes with XFilesUserRequests {

  // Create a tupled version of printf
  val printff = (printf.apply _)
  val printft = printff.tupled

  // Info method that will dump the state of a table
  def info[T <: XFilesBundle](x: Vec[T], prepend: String = "") = {
    if (tableDebug) {
      printf(x(0).printElements(prepend))
        (0 until x.length).map(i => printft(x(i).printAll(","))) }}

  def printfPrefix(prefix: String, message: String, args: Bits*): Unit = {
    if (debugEnabled) { printff(prefix + message, args) }}

  def printfInfo (m: String, a: Bits*) { printfPrefix("[INFO]",  m, a:_*) }
  def printfWarn (m: String, a: Bits*) { printfPrefix("[WARN]",  m, a:_*) }
  def printfError(m: String, a: Bits*) { printfPrefix("[ERROR]", m, a:_*) }
  def printfDebug(m: String, a: Bits*) { printfPrefix("[DEBUG]", m, a:_*) }
  def printfTodo (m: String, a: Bits*) { printfPrefix("[TODO]",  m, a:_*) }
}

class XFiles(implicit p: Parameters) extends RoCC()(p)
    with HasCoreParameters {
  val buildBackend = p(BuildXFilesBackend)
  val backend = buildBackend.generator(p)
  val backendInfo = UInt(p(TransactionTableNumEntries)) ##
    UInt(buildBackend.info, width = xLen - 16)
  val xFilesArbiter = Module(new XFilesArbiter(backendInfo)(p))

  // Core -> Arbiter connections
  io.cmd <> xFilesArbiter.io.core.cmd
  io.resp <> xFilesArbiter.io.core.resp
  io.mem.resp <> xFilesArbiter.io.core.mem.resp
  io.ptw <> xFilesArbiter.io.core.ptw
  io.autl <> xFilesArbiter.io.core.autl
  io.utl <> xFilesArbiter.io.core.utl
  io.busy := xFilesArbiter.io.core.busy
  xFilesArbiter.io.core.status := io.status
  io.interrupt := xFilesArbiter.io.core.interrupt

  // The mem (L1 dcache) connection must be done manually due to the
  // explicit setting of mem.req.phys in rocc.scala.
  io.mem.req.valid := xFilesArbiter.io.core.mem.req.valid
  xFilesArbiter.io.core.mem.req.ready := io.mem.req.ready
  io.mem.req.bits := xFilesArbiter.io.core.mem.req.bits
  io.mem.invalidate_lr := xFilesArbiter.io.core.mem.invalidate_lr

  // Connect the backend to the arbiter
  xFilesArbiter.io.backend <> backend.io
}
