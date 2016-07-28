// See LICENSE for license details.

package xfiles

import Chisel._

import rocket.{RoCCInterface, RoccNMemChannels, RoCCCommand, HellaCacheResp}
import cde.{Parameters}

class XFilesArbiterInterface(implicit p: Parameters) extends Bundle {
  val core = new RoCCInterface
  val backend = (new XFilesBackendInterface).flip
}

class XFilesArbiter(genInfo: => UInt)(implicit p: Parameters)
    extends XFilesModule()(p) with XFilesSupervisorRequests {
  val io = new XFilesArbiterInterface

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

  // Alias out some commonly used signals
  val cmd = io.core.cmd
  val sup = io.core.cmd.bits.status.prv.orR
  val funct = cmd.bits.inst.funct

  val asidValid = asidUnit.data.valid
  // Handle direct, short-circuit responses to the core of the
  // following types:
  //   * reqInfo -- information request that always succeeds
  //     regardless of ASID state
  //   * badRequest -- some invalid action (ASID is unset, user trying
  //     to initiate a supervisor request)
  //   * readCsr -- read a CSR, like the exception cause register
  //   * isDebug -- an access to the Debug Unit
  val reqInfo = cmd.fire() & !sup & funct === UInt(t_USR_XFILES_ID)
  when (reqInfo) { printfInfo("XF Arbiter: Received reqInfo\n") }
  val badRequest = cmd.fire() & ((!asidValid & !sup &
    (funct =/= UInt(t_USR_XFILES_ID)) & (funct =/= UInt(t_USR_XFILES_DEBUG))) |
    (!sup & funct < UInt(4)))
  val readCsr = cmd.fire() & sup & funct === UInt(t_SUP_READ_CSR)
  val isDebug = cmd.fire() & funct === UInt(t_USR_XFILES_DEBUG)
  // Anything that is a short circuit response or involves a
  // supervisor request gets squashed.
  val squashSup = reqInfo | badRequest | readCsr | isDebug
  val squashUser = squashSup | (sup & funct < UInt(4)) | !asidValid

  // Alternatively, the request
  val newRequest = cmd.fire() & !sup & funct === UInt(t_USR_NEW_REQUEST)

  io.core.resp.valid := reqInfo | badRequest | readCsr |
    asidUnit.resp.valid | debugUnit.resp.valid | tTable.xfiles.resp.valid

  io.core.resp.bits.rd := cmd.bits.inst.rd
  io.core.resp.bits.data := SInt(-err_XFILES_NOASID, width = xLen).toUInt
  val infoBits = genInfo
  when (reqInfo) { io.core.resp.bits.data := infoBits }
  when (readCsr) { io.core.resp.bits.data := exception.bits
    exception.valid := Bool(false) }

  // The ASID Units are provided with the full command, barring that
  // a short-circuit response hasn't been generated
  asidUnit.cmd.valid := cmd.fire() & !squashSup
  asidUnit.cmd.bits := cmd.bits
  asidUnit.status := io.core.cmd.bits.status
  asidUnit.resp.ready := Bool(true)

  // See if the ASID Unit is forwarding a supervisor request to the
  // backend
  val supReqToBackend = asidUnit.cmdFwd.valid

  when (cmd.fire()) {
    printfDebug("XF Arbiter: funct 0x%x, rs1 0x%x, rs2 0x%x\n",
      funct, cmd.bits.rs1, cmd.bits.rs2)
  }

  // The Debug Units get the full command, but are expected to
  // behave...
  debugUnit.cmd.valid := cmd.fire()
  debugUnit.cmd.bits := cmd.bits
  debugUnit.cmd.bits.status := io.core.cmd.bits.status
  debugUnit.resp.ready := Bool(true)

  // PTW connections for the Deubg Units
  debugUnit.ptw <> io.core.ptw

  // Core queue connections. We enqueue any user requests, i.e.,
  // anything that hasn't been squashed. The ASID and TID are
  // supplied by this core's ASID unit if this is a new request.
  coreQueue.enq.valid := cmd.fire() & !squashUser
  io.core.cmd.ready := coreQueue.enq.ready
  coreQueue.enq.bits := cmd.bits
  val asid = asidUnit.data.bits.asid
  val newTid = asidUnit.data.bits.tid
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
  when (asidUnit.resp.valid)  { io.core.resp.bits := asidUnit.resp.bits  }
  when (debugUnit.resp.valid) { io.core.resp.bits := debugUnit.resp.bits }

  // Deal with exceptional cases
  val backendException = io.backend.interrupt.fire()
  exception.valid := exception.valid | badRequest | backendException
  when (badRequest) { exception.bits := UInt(int_INVREQ)
    printfWarn("XF Arbiter: Saw badRequest\n") }
  when (backendException) { exception.bits := io.backend.interrupt.bits.code }

  // Other connections
  io.core.interrupt := exception.valid

  when (backendException) {
    printfError("XF Arbiter: RoCC Exception asserted\n")
  }

  when (io.backend.interrupt.fire()) {
    printfError("XF Arbiter: Backend interrupt asserted w/ code 0d%d\n",
      io.backend.interrupt.bits.code) }

  // Connections to the backend. [TODO] Clean these up such that the
  // backend gets a single RoCC interface and some special lines for
  // dealing with Transactions.
  tTable.xfiles.cmd.valid := coreQueue.deq.fire()
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
  io.backend.rocc.cmd.bits.status.prv := supReqToBackend

  when (asidUnit.cmdFwd.valid) {
    printfInfo("XFiles Arbiter: cmdFwd asserted\n")
    io.backend.rocc.cmd.bits := asidUnit.cmdFwd.bits
    io.backend.rocc.cmd.bits.status.prv := io.core.cmd.bits.status.prv
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
    io.core.utl(i).acquire.valid := Bool(false)
    io.core.utl(i).grant.ready := Bool(true) }

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
  io.backend.xfQueue <> tTable.backend.xfQueue

  when (reset) { exception.valid := Bool(false) }

  when (io.core.resp.valid) {
    printfInfo("XF Arbiter: Responding to core rd 0d%d with data 0x%x\n",
      io.core.resp.bits.rd, io.core.resp.bits.data) }

  // Assertions
  val totalResponses = Vec(asidUnit.resp.valid, debugUnit.resp.valid,
    tTable.xfiles.resp.valid)
  assert(!(totalResponses.count((x: Bool) => x) > UInt(1)),
    "X-FILES Arbiter: A response to the core was aliased")

  val newRequestToTransactionTable = RegNext(tTable.xfiles.cmd.fire()&
    tTable.xfiles.cmd.bits.inst.funct === UInt(t_USR_NEW_REQUEST))
  assert(!(newRequestToTransactionTable & !io.core.resp.valid),
    "XF Arbiter: TTable failed to generate resposne after newRequest")
}
