// See LICENSE for license details.

package xfiles

import Chisel._
import junctions.ParameterizedBundle

import rocket.{RoCCInterface, RoCCCommand, HellaCacheResp, HasCoreParameters}
import cde.{Parameters, Field}
import math.pow

case object NumCores extends Field[Int]
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

  val int_NOASID = 0
}

trait XFilesSupervisorRequests {
  val t_UPDATE_ASID = 0
  val t_SUP_WRITE_REG = 1
  val t_READ_CSR = 2
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

trait XFilesParameters extends HasCoreParameters with XFilesErrorCodes
    with XFilesSupervisorRequests {
  val numCores = p(NumCores)
  val tidWidth = p(TidWidth)
  val asidWidth = p(AsidWidth)
  val transactionTableNumEntries = p(TransactionTableNumEntries)

  val debugEnabled = p(DebugEnabled)
  val tableDebug = p(TableDebug)

  val k_NULL_ASID = pow(2, asidWidth) - 1
}

abstract class XFilesModule(implicit val p: Parameters) extends Module
    with XFilesParameters {
  val (t_READ_DATA :: t_WRITE_DATA :: t_UNUSED_2 :: t_NEW_REQUEST ::
    t_UNUSED_4 :: t_WRITE_DATA_LAST :: t_UNUSED_6 :: t_WRITE_REGISTER ::
    t_UNUSED_8 :: t_UNUSED_9 :: t_UNUSED_10 :: t_UNUSED_11 :: t_UNUSED_12 ::
    t_UNUSED_13 :: t_UNUSED_14 :: t_WRITE_REG :: t_XFILES_ID ::
    Nil) = Enum(UInt(), 17)

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
  val core = Vec(p(NumCores), new RoCCInterface)
  val backend = (new XFilesBackendInterface).flip
}

class XFilesArbiter(backendInfo: UInt)(implicit p: Parameters)
    extends XFilesModule()(p) {
  val io = new XFilesInterface

  val asidUnits = Vec.tabulate(numCores)(id => Module(new AsidUnit(id)).io)
  val transactionTable = Module(new XFilesTransactionTable).io

  // Each user request from a core gets entered into a queue. This is
  // not used for any supervisory requests which are routed through
  // the ASID Unit.
  val coreQueue = Vec.fill(numCores){ Module(new Queue(new RoCCCommand, 2)).io }
  val memQueue = Vec.fill(numCores){ Module(new Queue(new HellaCacheResp, 16)).io }

  // Use round robin arbitration for valid requests sitting in the
  // core queues
  val coreArbiter = Module(new RRArbiter(new RoCCCommand, numCores)).io
  val memArbiter = Module(new RRArbiter(new HellaCacheResp, numCores)).io

  val exception = Reg(Vec(numCores, Valid(UInt(width = xLen))))

  (0 until numCores).map(i => {
    // Alias out some commonly used signals
    val cmd = io.core(i).cmd
    val funct = cmd.bits.inst.funct

    // Handle direct, short-circuit responses to the core of the
    // following types:
    //   * reqInfo -- This is an informational request about X-FILES
    //     or the backend. This is allowed to go through
    //     uncoditionally, i.e., the ASID does not have to be set for
    //     this to succeed.
    //   * badRequest -- This covers receipt of a request when the
    //     ASID isn't set
    //   * readCsr -- read a CSR, like the exception cause register
    //   * writeReg -- Write a backend register
    //   * writeRegS -- Write a backend supervisor register
    // Either of these two types of requests means that we "squash"
    // the requst and prevent it from getting entered in the Core
    // Queue.
    val asidValid = asidUnits(i).data.valid
    val badRequest = cmd.fire() & !asidValid & !io.core(i).status.prv.orR &
      funct =/= t_XFILES_ID

    val reqInfo = cmd.fire() & !io.core(i).status.prv.orR & funct === t_XFILES_ID
    val readCsr = cmd.fire() & io.core(i).status.prv.orR & funct === UInt(t_READ_CSR)
    val writeReg = cmd.fire() & io.core(i).status.prv.orR
    val newRequest = cmd.fire() & !io.core(i).status.prv.orR & funct === t_NEW_REQUEST
    // Anything that is a short circuit response or involves a
    // supervisor request gets squashed.
    val squashSup = reqInfo | badRequest | readCsr
    val squashUser = squashSup | io.core(i).status.prv.orR | !asidValid

    io.core(i).resp.valid := reqInfo | badRequest | readCsr |
      asidUnits(i).resp.valid | (transactionTable.xfiles.resp.valid &
        transactionTable.regIdx.resp === UInt(i))

    io.core(i).resp.bits.rd := cmd.bits.inst.rd
    // [TODO] The info returned should be about X-FILES or the
    // backend. There shouldn't be anything DANA-specific here.
    io.core(i).resp.bits.data := SInt(-err_XFILES_NOASID, width = xLen).toUInt
    when (reqInfo) { io.core(i).resp.bits.data := backendInfo }
    when (readCsr) { io.core(i).resp.bits.data := exception(i).bits }

    // The ASID Units are provided with the full command, barring that
    // a short-circuit response hasn't been generated
    asidUnits(i).cmd.valid := cmd.fire() & !squashSup
    asidUnits(i).cmd.bits := cmd.bits
    asidUnits(i).status := io.core(i).status
    asidUnits(i).resp.ready := Bool(true)

    // Core queue connections. We enqueue any user requests, i.e.,
    // anything that hasn't been squashed. The ASID and TID are
    // supplied by this core's ASID unit if this is a new request.
    coreQueue(i).enq.valid := cmd.fire() & !squashUser
    io.core(i).cmd.ready := coreQueue(i).enq.ready
    coreQueue(i).enq.bits := cmd.bits
    when (newRequest) {
      // Grab the LSBs of rs1, but get the ASID/TID from the ASID Unit
      coreQueue(i).enq.bits.rs1 := cmd.bits.rs1(xLen-asidWidth-tidWidth-1, 0) ##
        asidUnits(i).data.bits.asid ## asidUnits(i).data.bits.tid
    } .otherwise {
      coreQueue(i).enq.bits.rs1 := asidUnits(i).data.bits.asid ##
        cmd.bits.rs1(tidWidth - 1, 0)
    }

    // Entries in the Core Queue are pulled out by the Core Arbiter
    coreQueue(i).deq <> coreArbiter.in(i)

    // Deal with responses in priority order
    when (asidUnits(i).resp.valid) {
      io.core(i).resp.bits := asidUnits(i).resp.bits
    } .elsewhen (transactionTable.xfiles.resp.valid &
      transactionTable.regIdx.resp === UInt(i)) {
      io.core(i).resp.bits := transactionTable.xfiles.resp.bits
    }

    // Deal with exceptional cases
    val backendException = io.backend.interrupt.fire() &
      (io.backend.interrupt.bits.coreIdx === UInt(i))
    exception(i).valid := exception(i).valid | badRequest | backendException
    when (badRequest) { exception(i).bits := UInt(int_NOASID)
      printfWarn("XF Arbiter: Saw badRequest\n") }
    when (backendException) { exception(i).bits := io.backend.interrupt.bits.code }

    // Other connections
    // io.core(i).interrupt := exception(i).valid
    io.core(i).interrupt := exception(i).valid
  })

  when (io.backend.rocc.interrupt) {
    printfError("XF Arbiter: RoCC Exception asserted w/ regIdx 0d%d\n",
      io.backend.regIdx.resp)
  }

  when (io.backend.interrupt.fire()) {
    printfError("XF Arbiter: Backend interrupt asserted on core 0d%d w/ code 0d%d\n",
      io.backend.interrupt.bits.coreIdx, io.backend.interrupt.bits.code) }

  // Connections to the backend. [TODO] Clean these up such that the
  // backend gets a single RoCC interface and some special lines for
  // dealing with Transactions.

  // The supervisor requests forwarded from the ASID Units get
  // processed in reverse order (i.e., Core 0 > Core 1 > ... Core N).
  // Aliasing is consequently possible and the OS has to be
  // intelligent about throwing around supervisor requests.
  val supReqToBackend = Vec.tabulate(numCores)(
    i => asidUnits(i).cmdFwd.valid).exists((x: Bool) => x)

  // We can pull things out of the Core Arbiter if the backend is
  // telling us it's okay and if the Core Arbiter isn't getting
  // superseded by a forwarded supervisor request.
  coreArbiter.out.ready := transactionTable.xfiles.cmd.ready & !supReqToBackend

  val userReqToBackend = coreArbiter.out.valid

  transactionTable.xfiles.cmd.valid := userReqToBackend
  transactionTable.xfiles.cmd.bits := coreArbiter.out.bits
  transactionTable.regIdx.cmd := coreArbiter.chosen
  transactionTable.xfiles.resp.ready := Bool(true)
  transactionTable.backend.rocc.resp <> io.backend.rocc.resp
  transactionTable.backend.rocc.cmd.ready := io.backend.rocc.cmd.ready

  io.backend.rocc.cmd.valid := transactionTable.backend.rocc.cmd.valid |
    supReqToBackend
  io.backend.rocc.cmd.bits := transactionTable.backend.rocc.cmd.bits

  // [TODO] Kludge that zeros all the status fields of MStatus except
  // for setting the privilege bits (prv) to ONE if we're sending a
  // supervisor request.
  io.backend.rocc.status.prv := supReqToBackend
  io.backend.regIdx.cmd := transactionTable.backend.regIdx.cmd
  transactionTable.backend.regIdx.resp := io.backend.regIdx.resp

  (numCores -1 to 0 by -1).map(i => {
    when (asidUnits(i).cmdFwd.valid) {
      printfInfo("XFiles Arbiter: cmdFwd asserted\n")
      io.backend.rocc.cmd.bits := asidUnits(i).cmdFwd.bits
      io.backend.regIdx.cmd := UInt(i)
      // [TODO] All the status bits other than prv are left unconnected
      io.backend.rocc.status.prv := io.core(i).status.prv
    }})

  // Handle memory request routing
  val allMemReady = io.core.forall((rocc: RoCCInterface) => rocc.mem.req.ready)
  (0 until numCores).map(i => {
    io.core(i).mem.req.valid := transactionTable.xfiles.mem.req.valid &&
      transactionTable.memIdx.cmd === UInt(i)
    io.core(i).mem.req.bits := transactionTable.xfiles.mem.req.bits
    io.core(i).mem.invalidate_lr := transactionTable.xfiles.mem.invalidate_lr
    when (io.core(i).mem.req.fire()) {
      printfInfo("XFilesArbiter: Mem request core %d with tag 0x%x for addr 0x%x\n",
        UInt(i), io.core(i).mem.req.bits.tag, io.core(i).mem.req.bits.addr) }
    when (io.core(i).mem.resp.fire()) {
      printfInfo("""XFilesArbiter: Mem response core %d with tag 0x%x for addr 0x%x and data 0x%x
""",
        UInt(i), io.core(i).mem.resp.bits.tag, io.core(i).mem.resp.bits.addr,
        io.core(i).mem.resp.bits.data_word_bypass) }})
  transactionTable.xfiles.mem.req.ready := allMemReady

  // Handle memory responses. These are sent into a per-core memory
  // response queue and then arbitrated out and passed to the backend.
  // Note that the memory responses come back on a Valid
  // interface, i.e., the memory queue cannot put backpressure on the
  // data cache to get it to stop sending responses. We currently
  // catch this missed write with an assertion. This is not a
  // candidate for an exception (#4) as we can just make sure that the
  // memory response queue is large enough to handle the maximum
  // number of outstanding transactions.

  (0 until numCores).map(i => {
    memQueue(i).enq.valid := io.core(i).mem.resp.valid
    memQueue(i).enq.bits := io.core(i).mem.resp.bits
    memQueue(i).deq <> memArbiter.in(i)
    assert(!(io.core(i).mem.resp.valid & !memQueue(i).enq.ready),
      "XFilesArbiter memory queue missed a memory response")})

  // The backend uses a Valid interface, but the arbiter wants a
  // Decoupled interface. We hack this in with a manual assignment and
  // telling the arbiter that the backend can always accept data (we
  // then just have guarantee that this is true on the backend).

  transactionTable.xfiles.mem.resp.valid := memArbiter.out.valid
  transactionTable.xfiles.mem.resp.bits := memArbiter.out.bits

  io.backend.rocc.mem.resp.valid := transactionTable.backend.rocc.mem.resp.valid
  io.backend.rocc.mem.resp.bits := transactionTable.backend.rocc.mem.resp.bits

  memArbiter.out.ready := Bool(true)

  transactionTable.memIdx.resp := memArbiter.chosen

  io.backend.memIdx <> transactionTable.backend.memIdx
  transactionTable.backend.rocc.mem.req <> io.backend.rocc.mem.req

  io.backend.xfReq <> transactionTable.backend.xfReq
  io.backend.xfResp <> transactionTable.backend.xfResp
  io.backend.queueIO <> transactionTable.backend.queueIO

  when (reset) { (0 until numCores).map(i => exception(i).valid := Bool(false)) }

  // Assertions
  (0 until numCores).map(i => {
    val totalResponses = Vec(asidUnits(i).resp.valid,
      transactionTable.xfiles.resp.valid &
        transactionTable.regIdx.resp === UInt(i))
    assert(!(totalResponses.count((x: Bool) => x) > UInt(1)),
      "X-FILES Arbiter: AsidUnit just aliased a TTable response")})

  when (io.core(0).resp.valid) {
    printf("XF Arbiter: Responding to core 0 with data 0x%x\n",
    io.core(0).resp.bits.data) }

  val newRequestToTransactionTable = RegNext(transactionTable.xfiles.cmd.fire()&
    transactionTable.xfiles.cmd.bits.inst.funct === t_NEW_REQUEST)
  assert(!(newRequestToTransactionTable & !io.core(0).resp.valid),
    "XF Arbiter: TTable failed to generate resposne after newRequest")
}
