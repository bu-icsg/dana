// See LICENSE for license details.

package dana

import Chisel._

import rocket.{RoCCInterface, RoCCCommand}
import cde.{Parameters, Field}

case object NumCores extends Field[Int]
case object AntwRobEntries extends Field[Int]

abstract trait XFilesParameters extends DanaParameters {
  val antwRobEntries = p(AntwRobEntries)
}

abstract class XFilesModule(implicit p: Parameters) extends DanaModule()(p)
    with XFilesParameters {
  val (t_READ_DATA :: t_WRITE_DATA :: t_UNUSED_2 :: t_NEW_REQUEST ::
    t_UNUSED_4 :: t_WRITE_DATA_LAST :: t_UNUSED_6 :: t_WRITE_REGISTER ::
    t_UNUSED_8 :: t_UNUSED_9 :: t_UNUSED_10 :: t_UNUSED_11 :: t_UNUSED_12 ::
    t_UNUSED_13 :: t_UNUSED_14 :: t_WRITE_REG :: t_XFILES_ID ::
    Nil) = Enum(UInt(), 17)
  val (t_UPDATE_ASID :: t_SUP_WRITE_REG :: Nil) = Enum(UInt(), 2)
  val err_XFILES_BADREQ = 1
  val err_XFILES_NOASID = 2
}

abstract class XFilesBundle(implicit p: Parameters) extends DanaBundle()(p)
    with XFilesParameters

class XFilesDanaInterface(implicit p: Parameters) extends XFilesBundle()(p) {
  val antw = new ANTWXFilesInterface
  val tTable = new TTableArbiter
}

class XFilesInterface(implicit p: Parameters) extends Bundle {
  val core = Vec(p(NumCores), new RoCCInterface)
  val dana = (new XFilesDanaInterface).flip
}

class XFilesArbiter(implicit p: Parameters) extends XFilesModule()(p) {
  val io = new XFilesInterface

  // Each core gets its own ASID Unit
  val asidUnits = Vec.tabulate(numCores)(id => Module(new AsidUnit(id)).io)

  // Each user request from a core gets entered into a queue. This is
  // not used for any supervisory requests which are routed through
  // the ASID Unit.
  val coreQueue = Vec.fill(numCores){ Module(new Queue(new RoCCCommand, 2)).io }

  // Use round robin arbitration for valid requests sitting in the
  // core queues
  val coreArbiter = Module(new RRArbiter(new RoCCCommand, numCores)).io

  (0 until numCores).map(i => {
    // Alias out some commonly used signals
    val cmd = io.core(i).cmd
    val funct = cmd.bits.inst.funct

    // Handle direct, short-circuit responses to the core of the
    // following types:
    //   * reqInfo -- This is an informational request about X-FILES
    //     or the backend
    //   * badRequest -- This covers receipt of a request when the
    //     ASID isn't set
    //   * writeReg -- Write a backend register
    //   * writeRegS -- Write a backend supervisor register
    // Either of these two types of requests means that we "squash"
    // the requst and prevent it from getting entered in the Core
    // Queue.
    val reqInfo = cmd.fire() & !io.core(i).s & funct === t_XFILES_ID
    val badRequest = cmd.fire() & !io.core(i).s & !asidUnits(i).data.valid
    val writeReg = cmd.fire() & io.core(i).s
    val newRequest = cmd.fire() & !io.core(i).s & funct === t_NEW_REQUEST
    // Anything that is a short circuit response or involves a
    // supervisor request gets squashed.
    val squashSup = reqInfo | badRequest
    val squashUser = squashSup | io.core(i).s
    val info = UInt(elementsPerBlock, width = 6) ##
      UInt(peTableNumEntries, width = 6) ##
      UInt(transactionTableNumEntries, width = 4) ##
    UInt(cacheNumEntries, width = 4)

    io.core(i).resp.valid := reqInfo | badRequest | asidUnits(i).resp.valid | (
      io.dana.tTable.rocc.resp.valid && io.dana.tTable.indexOut === UInt(i)) | (
      io.dana.antw.rocc.resp.valid && io.dana.antw.rocc.coreIdxResp === UInt(i))

    io.core(i).resp.bits.rd := cmd.bits.inst.rd
    // [TODO] The info returned should be about X-FILES or the
    // backend. There shouldn't be anything DANA-specific here.
    io.core(i).resp.bits.data := Mux(reqInfo, info,
      SInt(-err_XFILES_BADREQ, width = xLen).toUInt)

    // The ASID Units are provided with the full command, barring that
    // a short-circuit response hasn't been generated
    asidUnits(i).cmd.valid := cmd.fire() & !squashSup
    asidUnits(i).cmd.bits := cmd.bits
    asidUnits(i).s := io.core(i).s
    asidUnits(i).resp.ready := Bool(true)

    // Core queue connections. We enqueue any user requests, i.e.,
    // anything that hasn't been squashed. The ASID and TID are
    // supplied by this core's ASID unit if this is a new request.
    coreQueue(i).enq.valid := cmd.fire() & !squashUser
    io.core(i).cmd.ready := coreQueue(i).enq.ready
    coreQueue(i).enq.bits := cmd.bits
    when (newRequest) {
      coreQueue(i).enq.bits.rs1 := cmd.bits.rs1(feedbackWidth - 1, 0) ##
        asidUnits(i).data.bits.asid ## asidUnits(i).data.bits.tid
    } .otherwise {
      coreQueue(i).enq.bits.rs1 := asidUnits(i).data.bits.asid ##
        cmd.bits.rs1(tidWidth - 1, 0)
    }

    // Entries in the Core Queue are pulled out by the Core Arbiter
    coreQueue(i).deq <> coreArbiter.in(i)

    // Deal with responses in priority order
    when (asidUnits(i).resp.fire()) {
      io.core(i).resp.bits := asidUnits(i).resp.bits
    } .elsewhen (io.dana.antw.rocc.resp.fire() &&
      io.dana.antw.rocc.coreIdxResp === UInt(i)) {
      io.core(i).resp.bits := io.dana.antw.rocc.resp.bits
    } .elsewhen (io.dana.tTable.rocc.resp.fire() &&
      io.dana.tTable.indexOut === UInt(i)) {
      io.core(i).resp.bits := io.dana.tTable.rocc.resp.bits
    }

    // Other connections
    io.core(i).interrupt := Bool(false)
  })

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
  coreArbiter.out.ready := io.dana.antw.rocc.cmd.ready &
    io.dana.tTable.rocc.cmd.ready & !supReqToBackend

  val userReqToBackend = coreArbiter.out.valid | supReqToBackend
  io.dana.tTable.rocc.cmd.valid := userReqToBackend
  io.dana.tTable.rocc.cmd.bits := coreArbiter.out.bits
  io.dana.tTable.coreIdx := coreArbiter.chosen
  io.dana.tTable.rocc.s := supReqToBackend
  io.dana.tTable.rocc.resp.ready := Bool(true)

  io.dana.antw.rocc.cmd.valid := userReqToBackend
  io.dana.antw.rocc.cmd.bits := coreArbiter.out.bits
  io.dana.antw.rocc.coreIdxCmd := coreArbiter.chosen
  io.dana.antw.rocc.s := supReqToBackend
  io.dana.antw.rocc.resp.ready := Bool(true)

  when (coreArbiter.out.fire()) {
    printfInfo("""XFiles Arbiter: coreArbiter.out.valid asserted
[INFO] XFilesArbiter: Non-cmdFwd req sent to TTable:
[INFO] XFilesArbiter:   inst: 0x%x
[INFO] XFilesArbiter:   rs1:  0x%x
[INFO] XFilesArbiter:   rs2:  0x%x
""", coreArbiter.out.bits.inst.toBits, coreArbiter.out.bits.rs1,
      coreArbiter.out.bits.rs2) }

  (numCores -1 to 0 by -1).map(i => {
    when (asidUnits(i).cmdFwd.valid) {
      printfInfo("XFiles Arbiter: cmdFwd asserted\n")
      io.dana.tTable.rocc.cmd.bits := asidUnits(i).cmdFwd.bits
      io.dana.tTable.coreIdx := UInt(i)

      io.dana.antw.rocc.cmd.bits := asidUnits(i).cmdFwd.bits
      io.dana.antw.rocc.coreIdxCmd := UInt(i)
    }})


  (0 until numCores).map(i => {
    io.dana.antw.mem(i) <> io.core(i).mem})

  // Assertions
  (0 until numCores).map(i => {
    val totalResponses =
      Vec(io.dana.tTable.rocc.resp.valid && io.dana.tTable.indexOut === UInt(i),
        asidUnits(i).resp.valid,
        io.dana.antw.rocc.coreIdxResp === UInt(i) && io.dana.antw.rocc.resp.valid)
    assert(!(totalResponses.count((x: Bool) => x) > UInt(1)),
      "X-FILES Arbiter: AsidUnit just aliased a TTable response")
    })
}
