// See LICENSE.BU for license details.
// See LICENSE.IBM for license details.

package xfiles

import chisel3._
import chisel3.util._
import rocket.{RoCCInterface, RoccNMemChannels, RoCCCommand, HellaCacheResp}
import config._

class XFilesArbiterInterface(implicit p: Parameters) extends Bundle {
  val core = new RoCCInterface
  val backend = Flipped(new XFilesBackendInterface)
}

class XFilesArbiter()(implicit p: Parameters)
    extends XFilesModule()(p) with XFilesSupervisorRequests with HasXFilesBackend {
  val io = IO(new XFilesArbiterInterface)

  override val printfSigil = "xfiles.Arbiter: "

  val tTable = Module(new XFilesTransactionTable).io
  val csrFile = buildBackend.csrFile_gen(p)

  // Each user request from a core gets entered into a queue. This is
  // not used for any supervisory requests which are routed through
  // the ASID Unit.
  val coreQueue = Module(new Queue(new RoCCCommand, 2)).io
  val memQueue = Module(new Queue(new HellaCacheResp, 16)).io

  val exception = Reg(Valid(UInt(xLen.W)))

  // Include a debug unit for some debugging operations
  val debugUnit = Module(new DebugUnit).io

  // Alias out some commonly used signals
  val cmd = io.core.cmd
  val sup = cmd.bits.status.prv.orR
  val funct = cmd.bits.inst.funct

  io.core.resp.bits.data := (-err_XFILES_NOASID.S(xLen.W)).asUInt

  // Handle direct, short-circuit responses to the core of the
  // following types:
  //   * reqInfo -- information request that always succeeds
  //     regardless of ASID state
  //   * badRequest -- some invalid action (ASID is unset, user trying
  //     to initiate a supervisor request)
  //   * readCsr -- read a CSR, like the exception cause register
  //   * isDebug -- an access to the Debug Unit
  val reqInfo = cmd.fire() & !sup & funct === t_USR_XFILES_ID.U
  when (reqInfo) { printfInfo("Received reqInfo (deprecated!)\n") }
  val asidValid = csrFile.io.status.asidValid
  val badRequest = cmd.fire() & (
    (!asidValid & !sup & (funct =/= t_USR_XFILES_DEBUG.U | funct =/= t_SUP_READ_CSR.U)) |
      (asidValid & !sup & (funct < t_USR_READ_DATA.U & funct =/= t_SUP_READ_CSR.U)) |
      (reqInfo)) & !csrFile.io.status.debug
  val readCsr = cmd.fire() & funct === t_SUP_READ_CSR.U
  val writeCsr = cmd.fire() & funct === t_SUP_WRITE_CSR.U
  val isDebug = cmd.fire() & funct === t_USR_XFILES_DEBUG.U
  // Anything that is a short circuit response or involves a
  // supervisor request gets squashed.
  val squashSup = badRequest | readCsr | writeCsr | isDebug
  val squashUser = squashSup | (sup & funct < 4.U) | !asidValid

  csrFile.io.cmd := writeCsr
  csrFile.io.addr := cmd.bits.rs1
  csrFile.io.wdata := cmd.bits.rs2
  csrFile.io.prv := cmd.bits.status.prv
  when (readCsr | writeCsr) { io.core.resp.bits.data := csrFile.io.rdata }
  tTable.status := csrFile.io.status

  // Alternatively, the request
  val newRequest = cmd.fire() & asidValid & funct === t_USR_NEW_REQUEST.U
  csrFile.io.action.newRequest := newRequest

  io.core.resp.valid := badRequest | readCsr | writeCsr |
    debugUnit.resp.valid | tTable.xfiles.resp.valid

  io.core.resp.bits.rd := cmd.bits.inst.rd

  // See if the ASID Unit is forwarding a supervisor request to the
  // backend
  val supReqToBackend = false.B

  when (cmd.fire()) {
    printfDebug("funct 0x%x, rs1 0x%x, rs2 0x%x\n",
      funct, cmd.bits.rs1, cmd.bits.rs2)
  }

  // The Debug Units get the full command, but are expected to
  // behave...
  debugUnit.cmd.valid := cmd.fire()
  debugUnit.cmd.bits := cmd.bits
  debugUnit.cmd.bits.status := cmd.bits.status
  debugUnit.resp.ready := true.B

  // PTW connections for the Deubg Units
  io.core.ptw <> debugUnit.ptw

  // Core queue connections. We enqueue any user requests, i.e.,
  // anything that hasn't been squashed. The ASID and TID are
  // supplied by this core's ASID unit if this is a new request.
  coreQueue.enq.valid := cmd.fire() & !squashUser
  cmd.ready := coreQueue.enq.ready & debugUnit.cmd.ready
  coreQueue.enq.bits := cmd.bits
  val asid = csrFile.io.status.asid
  val newTid = csrFile.io.status.tid
  when (newRequest) {
    // Grab the LSBs of rs1, but get the ASID/TID from the ASID Unit
    val rs1Data = cmd.bits.rs1(xLen-asidWidth-tidWidth-1, 0)
    coreQueue.enq.bits.rs1 := rs1Data ## asid ## newTid
  } .otherwise {
    coreQueue.enq.bits.rs1 := asid ## cmd.bits.rs1(tidWidth - 1, 0)
  }

  // Entries in the Core Queue are pulled out by the Core Arbiter
  coreQueue.deq.ready := tTable.xfiles.cmd.ready & !supReqToBackend

  // Deal with responses with an implied priority
  when (tTable.xfiles.resp.valid) { io.core.resp.bits := tTable.xfiles.resp.bits }
  when (debugUnit.resp.valid) { io.core.resp.bits := debugUnit.resp.bits }

  // Deal with exceptional cases
  val backendException = io.backend.interrupt.fire()
  exception.valid := exception.valid | badRequest | backendException
  when (badRequest) { exception.bits := int_INVREQ.U
    printfWarn("Saw badRequest\n") }
  when (backendException) { exception.bits := io.backend.interrupt.bits.code }

  // Other connections
  io.core.interrupt := exception.valid
  io.core.busy := io.backend.rocc.busy

  when (backendException) {
    printfError("RoCC Exception asserted\n")
  }

  when (io.backend.interrupt.fire()) {
    printfError("Backend interrupt asserted w/ code 0d%d\n",
      io.backend.interrupt.bits.code) }

  // Connections to the backend. [TODO] Clean these up such that the
  // backend gets a single RoCC interface and some special lines for
  // dealing with Transactions.
  tTable.xfiles.cmd.valid := coreQueue.deq.fire()
  tTable.xfiles.cmd.bits := coreQueue.deq.bits
  tTable.xfiles.resp.ready := true.B
  tTable.backend.rocc.resp <> io.backend.rocc.resp
  tTable.backend.rocc.cmd.ready := io.backend.rocc.cmd.ready

  io.backend.rocc.cmd.valid := (tTable.backend.rocc.cmd.valid |
    supReqToBackend)
  io.backend.rocc.cmd.bits := tTable.backend.rocc.cmd.bits

  // [TODO] Kludge that zeros all the status fields of MStatus except
  // for setting the privilege bits (prv) to ONE if we're sending a
  // supervisor request.
  io.backend.rocc.cmd.bits.status.prv := supReqToBackend

  io.core.mem.req.valid := tTable.xfiles.mem.req.valid | (
    debugUnit.mem.req.valid)

  io.core.mem.req.bits := tTable.xfiles.mem.req.bits
  io.core.mem.invalidate_lr := tTable.xfiles.mem.invalidate_lr
  when (debugUnit.mem.req.valid) {
    io.core.mem.req.bits := debugUnit.mem.req.bits
    io.core.mem.invalidate_lr := debugUnit.mem.invalidate_lr
  }
  debugUnit.mem.req.ready := io.core.mem.req.ready

  when (io.core.mem.req.fire()) {
    printfInfo("Mem request from core with tag 0x%x for addr 0x%x\n",
      io.core.mem.req.bits.tag, io.core.mem.req.bits.addr) }
  when (io.core.mem.resp.fire()) {
    printfInfo("""Mem response from core with tag 0x%x for addr 0x%x and data 0x%x
""",
      io.core.mem.resp.bits.tag, io.core.mem.resp.bits.addr,
      io.core.mem.resp.bits.data_word_bypass) }

  tTable.xfiles.mem.req.ready := io.core.mem.req.ready

  // Uncached TileLink connections to Debug Unit and Backend
  io.core.autl.acquire.valid := (debugUnit.autl.acquire.valid |
    io.backend.rocc.autl.acquire.valid)
  io.core.autl.acquire.bits := io.backend.rocc.autl.acquire.bits
  io.backend.rocc.autl.acquire.ready := io.core.autl.acquire.ready

  debugUnit.autl.acquire.ready := io.core.autl.acquire.ready
  when (debugUnit.autl.acquire.valid) {
    io.core.autl.acquire.bits := debugUnit.autl.acquire.bits }

  debugUnit.autl.grant.valid := io.core.autl.grant.valid
  debugUnit.autl.grant.bits := io.core.autl.grant.bits
  io.backend.rocc.autl.grant.valid := io.core.autl.grant.valid
  io.backend.rocc.autl.grant.bits := io.core.autl.grant.bits
  io.core.autl.grant.ready := (debugUnit.autl.grant.ready |
    io.backend.rocc.autl.grant.ready)

  for (i <- 0 until p(RoccNMemChannels)) {
    io.core.utl(i).acquire.valid := false.B
    io.core.utl(i).grant.ready := true.B }

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
  memQueue.deq.ready := true.B
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
  tTable.backend.rocc.mem.invalidate_lr := io.backend.rocc.mem.invalidate_lr

  io.backend.xfReq <> tTable.backend.xfReq
  tTable.backend.xfResp <> io.backend.xfResp
  tTable.backend.xfQueue <> io.backend.xfQueue
  io.backend.status := tTable.backend.status

  when (reset) { exception.valid := false.B }

  when (io.core.resp.valid) {
    printfInfo("Responding to core rd 0d%d with data 0x%x\n",
      io.core.resp.bits.rd, io.core.resp.bits.data) }

  // Assertions
  val totalResponses = Vec(debugUnit.resp.valid, tTable.xfiles.resp.valid)
  assert(!(totalResponses.count((x: Bool) => x) > 1.U),
    printfSigil ++ "response to the core was aliased")

  val newRequestToTransactionTable = RegNext(tTable.xfiles.cmd.fire()&
    tTable.xfiles.cmd.bits.inst.funct === t_USR_NEW_REQUEST.U)
  assert(!(newRequestToTransactionTable & !io.core.resp.valid),
    printfSigil ++ "TTable failed to generate resposne after newRequest")
}
