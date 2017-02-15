// See LICENSE for license details.

package xfiles

import chisel3._
import chisel3.util._
import rocket.{RoCCCommand, RoCCResponse, MStatus}
import config._

class AsidTid(implicit p: Parameters) extends XFilesBundle()(p) {
  val asid = UInt(p(AsidWidth).W)
  val tid  = UInt(p(TidWidth).W)
}

class AsidUnit(id: Int = 0)(implicit p: Parameters) extends XFilesModule()(p)
    with XFilesSupervisorRequests{
  val io = IO(new XFilesBundle {
    val cmd    = Decoupled(new RoCCCommand).flip
    val status = new MStatus().asInput
    val resp   = Decoupled(new RoCCResponse)
    val data   = Valid(new AsidTid()(p))
    // In the event that we can't do anything with this request, we
    // just forward it along. This is implicitly a supervisor command.
    val cmdFwd = Valid(new RoCCCommand)
  })

  override val printfSigil = "xfiles.ASIDUnit[" + id + "]: "

  val asidReg = Reg(Valid(new AsidTid))
  val tid = asidReg.bits.tid
  val asid = asidReg.bits.asid

  val funct = io.cmd.bits.inst.funct
  val sup = io.status.prv.orR
  val updateAsid = io.cmd.fire() & sup & funct === t_SUP_UPDATE_ASID.U
  val newRequest = io.cmd.fire() & funct === t_USR_NEW_REQUEST.U

  // Snoop on the input RoCCInterface. When you see a new supervisory
  // ASID-update request, set the ASID and reset the TID counter.
  io.cmd.ready := true.B
  when (updateAsid) {
    asidReg.valid := true.B
    asid := io.cmd.bits.rs1(asidWidth - 1, 0)
    tid := 0.U
    printfInfo("supervisor request to update ASID to 0x%x\n",
      io.cmd.bits.rs1(asidWidth - 1, 0));
    // [TODO] This needs to respond to the core with the ASID and TID
    // so that the OS can save the ASID/TID for reloading later.
  }

  // Forward the command on if we're aren't supposed to touch it. The
  // ready line is ignored as this request has to go through.
  io.cmdFwd.bits := io.cmd.bits
  io.cmdFwd.valid := (io.cmd.fire() & sup & funct < 4.U)

  when (io.cmdFwd.valid) {
    printfInfo("forwarding request with funct code 0x%x\n",
      io.cmdFwd.bits.inst.funct) }

  // Respond with the ASID and TID if we have a vliad ASID, otherwise
  // respond with a generic -1
  io.resp.bits.rd := io.cmd.bits.inst.rd
  io.resp.bits.data := Mux(asidReg.valid,
    asid(asidWidth - 1, 0) ## tid(tidWidth - 1, 0),
    (-(err_XFILES_NOASID.S(xLen.W))).asUInt)
  io.resp.valid := updateAsid

  when (io.resp.valid) {
    printfInfo("responding to R%d with data 0x%x\n", io.resp.bits.rd,
      io.resp.bits.data)}

  // Increment the TID when a new request shows up. Negative TIDs are
  // reserved for error codes, so the valid ranges of TIDs is then:
  //   [0, 2^(tidWidth - 1) - 1]
  when (newRequest & asidReg.valid) { tid := (tid + 1.U)(tidWidth - 2, 0) }

  io.data := asidReg

  // Reset
  when (reset) { asidReg.valid := false.B }

  // Assertions
  assert(!(io.resp.valid && !io.resp.ready),
    printfSigil ++ "tried to respond when core was not ready")
}
