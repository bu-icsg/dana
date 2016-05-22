// See LICENSE for license details.

package xfiles

import Chisel._
import junctions.ParameterizedBundle

import rocket.{RoCCInterface, RoCCCommand, HellaCacheResp, HasCoreParameters}
import cde.{Parameters, Field}
import math.pow

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

trait XFilesParameters extends HasCoreParameters with XFilesErrorCodes
    with XFilesUserRequests {
  val tidWidth = p(TidWidth)
  val asidWidth = p(AsidWidth)
  val transactionTableNumEntries = p(TransactionTableNumEntries)

  val debugEnabled = p(DebugEnabled)
  val tableDebug = p(TableDebug)

  val k_NULL_ASID = pow(2, asidWidth) - 1
}

trait XFilesResponseCodes extends XFilesParameters {
  val respCodeWidth = 3

  val (resp_OK :: resp_TID :: resp_READ :: resp_NOT_DONE :: resp_QUEUE_ERR ::
    resp_XFILES :: Nil) =  Enum(UInt(), 6)

  def genResp[T <: Bits](resp: T, respCode: T, tid: T,
    data: T = Bits(0, width = xLen)) {
    resp := data.toBits
    resp(xLen - 1, xLen - respCodeWidth) := UInt(respCode)
    resp(xLen - respCodeWidth - 1, xLen - respCodeWidth - tidWidth) := tid
  }
}

abstract class XFilesModule(implicit val p: Parameters) extends Module
    with XFilesParameters {

  // Create a tupled version of printf
  val printff = printf _
  val printft = printff.tupled

  // Info method that will dump the state of a table
  def info[T <: XFilesBundle](x: Vec[T], prepend: String = "") = {
    if (tableDebug) {
      printf(x(0).printElements(prepend))
        (0 until x.length).map(i => printft(x(i).printAll(","))) }}

  def printfInfo(message: String, args: Node*): Unit = {
    if (debugEnabled) { printff("[INFO] " + message, args) }}

  def printfWarn(message: String, args: Node*) {
    if (debugEnabled) { printff("[WARN] " + message, args) }}

  def printfError(message: String, args: Node*) {
    if (debugEnabled) { printff("[ERROR] " + message, args) }}

  def printfDebug(message: String, args: Node*) {
    if (debugEnabled) { printff("[DEBUG] " + message, args) }}

  def printfTodo(message: String, args: Node*) {
    if (debugEnabled) { printff("[TODO] " + message, args) }}
}

abstract class XFilesBundle(implicit val p: Parameters)
    extends ParameterizedBundle()(p) with XFilesParameters {

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

  // Return a (String, Seq[Node]) tuple suitable for passing to printf
  // that contains the values of all the elements in the bundle
  def printAll(prepend: String = ""): (String, Seq[Node]) = {
    var format = "[DEBUG]" + prepend
    var sep = ""
    var argsIn = Seq[Node]()
    for ((n, i) <- elements) {
      format += sep + "%x"
      sep = ","
      argsIn = argsIn :+ i.toNode
    }
    format += "\n"
    (format, argsIn)
  }
}

class XFilesInterface(implicit p: Parameters) extends Bundle {
  val core = new RoCCInterface
  val backend = (new XFilesBackendInterface).flip
}

class XFilesArbiter(backendInfo: UInt)(implicit p: Parameters)
    extends XFilesModule()(p) with XFilesSupervisorRequests {
  val io = new XFilesInterface

  val asidUnit = Module(new AsidUnit).io
  val tTable = Module(new XFilesTransactionTable).io

  // Each user request from a core gets entered into a queue. This is
  // not used for any supervisory requests which are routed through
  // the ASID Unit.
  val coreQueue = Module(new Queue(new RoCCCommand, 2)).io
  val memQueue = Module(new Queue(new HellaCacheResp, 16)).io

  val exception = Reg(Valid(UInt(width = xLen)))

  // Include a debug unit for some debugging operations
  val debugUnit = Module(new DebugUnit).io

  // The supervisor requests forwarded from the ASID Units get
  // processed in reverse order (i.e., Core 0 > Core 1 > ... Core N).
  // Aliasing is consequently possible and the OS has to be
  // intelligent about throwing around supervisor requests.
  val supReqToBackend = asidUnit.cmdFwd.valid

  // Alias out some commonly used signals
  val cmd = io.core.cmd
  val sup = io.core.status.prv.orR
  val funct = cmd.bits.inst.funct

  // Handle direct, short-circuit responses to the core of the
  // following types:
  //   * reqInfo -- This is an informational request about X-FILES
  //     or the backend. This is allowed to go through
  //     uncoditionally, i.e., the ASID does not have to be set for
  //     this to succeed.
  //   * badRequest -- This covers all bad requests (ASID is unset,
  //     user trying to initiate a supervisor request)
  //   * readCsr -- read a CSR, like the exception cause register
  //   * writeReg -- Write a backend register
  //   * writeRegS -- Write a backend supervisor register
  // Either of these two types of requests means that we "squash"
  // the requst and prevent it from getting entered in the Core
  // Queue.
  val asidValid = asidUnit.data.valid
  val badRequest = cmd.fire() & ((!asidValid & !sup &
    funct =/= UInt(t_USR_XFILES_ID) & funct =/= UInt(t_USR_XFILES_DEBUG)) |
    (!sup & funct < UInt(4)))

  val reqInfo = cmd.fire() & !sup & funct === UInt(t_USR_XFILES_ID)
  val readCsr = cmd.fire() & sup & funct === UInt(t_SUP_READ_CSR)
  // val writeReg = cmd.fire() & sup
  val newRequest = cmd.fire() & !sup & funct === UInt(t_USR_NEW_REQUEST)
  val isDebug = cmd.fire() & funct === UInt(t_USR_XFILES_DEBUG)
  // Anything that is a short circuit response or involves a
  // supervisor request gets squashed.
  val squashSup = reqInfo | badRequest | readCsr | isDebug
  val squashUser = squashSup | (sup & funct < UInt(4)) | !asidValid

  io.core.resp.valid := (reqInfo | badRequest | readCsr |
    asidUnit.resp.valid | debugUnit.resp.valid | tTable.xfiles.resp.valid)

  io.core.resp.bits.rd := cmd.bits.inst.rd
  // [TODO] The info returned should be about X-FILES or the
  // backend. There shouldn't be anything DANA-specific here.
  io.core.resp.bits.data := SInt(-err_XFILES_NOASID, width = xLen).toUInt
  when (reqInfo) { io.core.resp.bits.data := backendInfo }
  when (readCsr) { io.core.resp.bits.data := exception.bits
    exception.valid := Bool(false) }

  // The ASID Units are provided with the full command, barring that
  // a short-circuit response hasn't been generated
  asidUnit.cmd.valid := cmd.fire() & !squashSup
  asidUnit.cmd.bits := cmd.bits
  asidUnit.status := io.core.status
  asidUnit.resp.ready := Bool(true)

  when (cmd.fire()) {
    printfDebug("XF Arbiter: funct 0x%x, rs1 0x%x, rs2 0x%x\n",
      funct, cmd.bits.rs1, cmd.bits.rs2)
  }

  // The Debug Units get the full command, but are expected to
  // behave...
  debugUnit.cmd.valid := cmd.fire()
  debugUnit.cmd.bits := cmd.bits
  debugUnit.status := io.core.status
  debugUnit.resp.ready := Bool(true)

  // PTW connectsion for the Deubg Units
  debugUnit.ptw <> io.core.ptw

  // Core queue connections. We enqueue any user requests, i.e.,
  // anything that hasn't been squashed. The ASID and TID are
  // supplied by this core's ASID unit if this is a new request.
  coreQueue.enq.valid := cmd.fire() & !squashUser
  io.core.cmd.ready := coreQueue.enq.ready
  coreQueue.enq.bits := cmd.bits
  when (newRequest) {
    // Grab the LSBs of rs1, but get the ASID/TID from the ASID Unit
    coreQueue.enq.bits.rs1 := cmd.bits.rs1(xLen-asidWidth-tidWidth-1, 0) ##
    asidUnit.data.bits.asid ## asidUnit.data.bits.tid
  } .otherwise {
    coreQueue.enq.bits.rs1 := asidUnit.data.bits.asid ##
    cmd.bits.rs1(tidWidth - 1, 0)
  }

  // Entries in the Core Queue are pulled out by the Core Arbiter
  coreQueue.deq.ready := tTable.xfiles.cmd.ready & !supReqToBackend

  // Deal with responses in priority order
  when (debugUnit.resp.valid) {
    io.core.resp.bits := debugUnit.resp.bits
  } .elsewhen (asidUnit.resp.valid) {
    io.core.resp.bits := asidUnit.resp.bits
  } .elsewhen (tTable.xfiles.resp.valid) {
    io.core.resp.bits := tTable.xfiles.resp.bits
  }

  // Deal with exceptional cases
  val backendException = io.backend.interrupt.fire()
  exception.valid := exception.valid | badRequest | backendException
  when (badRequest) { exception.bits := UInt(int_INVREQ)
    printfWarn("XF Arbiter: Saw badRequest\n") }
  when (backendException) { exception.bits := io.backend.interrupt.bits.code }

  // Other connections
  io.core.interrupt := exception.valid

  when (io.backend.rocc.interrupt) {
    printfError("XF Arbiter: RoCC Exception asserted\n")
  }

  when (io.backend.interrupt.fire()) {
    printfError("XF Arbiter: Backend interrupt asserted w/ code 0d%d\n",
      io.backend.interrupt.bits.code) }

  // Connections to the backend. [TODO] Clean these up such that the
  // backend gets a single RoCC interface and some special lines for
  // dealing with Transactions.

  val userReqToBackend = coreQueue.deq.fire()

  tTable.xfiles.cmd.valid := userReqToBackend
  tTable.xfiles.cmd.bits := coreQueue.deq.bits
  tTable.xfiles.resp.ready := Bool(true)
  tTable.backend.rocc.resp <> io.backend.rocc.resp
  tTable.backend.rocc.cmd.ready := io.backend.rocc.cmd.ready

  io.backend.rocc.cmd.valid := tTable.backend.rocc.cmd.valid |
  supReqToBackend
  io.backend.rocc.cmd.bits := tTable.backend.rocc.cmd.bits

  // [TODO] Kludge that zeros all the status fields of MStatus except
  // for setting the privilege bits (prv) to ONE if we're sending a
  // supervisor request.
  io.backend.rocc.status.prv := supReqToBackend

  when (asidUnit.cmdFwd.valid) {
    printfInfo("XFiles Arbiter: cmdFwd asserted\n")
    io.backend.rocc.cmd.bits := asidUnit.cmdFwd.bits
    // [TODO] All the status bits other than prv are left unconnected
    io.backend.rocc.status.prv := io.core.status.prv
  }

  io.core.mem.req.valid := (tTable.xfiles.mem.req.valid) | (
    debugUnit.mem.req.valid)

  io.core.mem.req.bits := tTable.xfiles.mem.req.bits
  io.core.mem.invalidate_lr := tTable.xfiles.mem.invalidate_lr

  when (debugUnit.mem.req.valid) {
    io.core.mem.req.bits := debugUnit.mem.req.bits
  }
  debugUnit.mem.req.ready := io.core.mem.req.ready

  when (io.core.mem.req.fire()) {
    printfInfo("XFilesArbiter: Mem request from core with tag 0x%x for addr 0x%x\n",
      io.core.mem.req.bits.tag, io.core.mem.req.bits.addr) }
  when (io.core.mem.resp.fire()) {
    printfInfo("""XFilesArbiter: Mem response from core with tag 0x%x for addr 0x%x and data 0x%x
""",
      io.core.mem.resp.bits.tag, io.core.mem.resp.bits.addr,
      io.core.mem.resp.bits.data_word_bypass) }

  tTable.xfiles.mem.req.ready := io.core.mem.req.ready

  // Just attach the AUTL lines to the X-FILES Arbiter
  io.core.autl <> debugUnit.autl

  // Handle memory responses. These are sent into a per-core memory
  // response queue and then arbitrated out and passed to the backend.
  // Note that the memory responses come back on a Valid
  // interface, i.e., the memory queue cannot put backpressure on the
  // data cache to get it to stop sending responses. We currently
  // catch this missed write with an assertion. This is not a
  // candidate for an exception (#4) as we can just make sure that the
  // memory response queue is large enough to handle the maximum
  // number of outstanding transactions.

  memQueue.enq.valid := io.core.mem.resp.valid
  memQueue.enq.bits := io.core.mem.resp.bits
  memQueue.deq.ready := Bool(true)
  assert(!(io.core.mem.resp.valid & !memQueue.enq.ready),
    "XFilesArbiter memory queue missed a memory response")

  // The backend uses a Valid interface, but the arbiter wants a
  // Decoupled interface. We hack this in with a manual assignment and
  // telling the arbiter that the backend can always accept data (we
  // then just have guarantee that this is true on the backend).

  tTable.xfiles.mem.resp.valid := memQueue.deq.valid
  tTable.xfiles.mem.resp.bits := memQueue.deq.bits

  debugUnit.mem.resp := memQueue.deq

  io.backend.rocc.mem.resp.valid := tTable.backend.rocc.mem.resp.valid
  io.backend.rocc.mem.resp.bits := tTable.backend.rocc.mem.resp.bits

  tTable.backend.rocc.mem.req <> io.backend.rocc.mem.req

  io.backend.xfReq <> tTable.backend.xfReq
  io.backend.xfResp <> tTable.backend.xfResp
  io.backend.queueIO <> tTable.backend.queueIO

  when (reset) { exception.valid := Bool(false) }

  // Assertions
  val totalResponses = Vec(asidUnit.resp.valid, tTable.xfiles.resp.valid)
  assert(!(totalResponses.count((x: Bool) => x) > UInt(1)),
    "X-FILES Arbiter: AsidUnit just aliased a TTable response")

  when (io.core.resp.valid) {
    printf("XF Arbiter: Responding to core 0 with data 0x%x\n",
      io.core.resp.bits.data) }

  val newRequestToTransactionTable = RegNext(tTable.xfiles.cmd.fire()&
    tTable.xfiles.cmd.bits.inst.funct === UInt(t_USR_NEW_REQUEST))
  assert(!(newRequestToTransactionTable & !io.core.resp.valid),
    "XF Arbiter: TTable failed to generate resposne after newRequest")
}
