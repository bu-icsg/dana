// See LICENSE for license details.

package xfiles

import chisel3._
import chisel3.util._
import rocket.{RoCCCommand, RoCCResponse, MStatus}
import cde.Parameters

class AsidTid(implicit p: Parameters) extends XFilesBundle()(p) {
  val asid = UInt(width = p(AsidWidth))
  val tid  = UInt(width = p(TidWidth))
}

class AsidUnit(id: Int = 0)(implicit p: Parameters) extends XFilesModule()(p)
    with XFilesSupervisorRequests{
  val io = new XFilesBundle {
    val cmd    = Decoupled(new RoCCCommand).flip
    val status = new MStatus().asInput
    val resp   = Decoupled(new RoCCResponse)
    val data   = Valid(new AsidTid()(p))
    // In the event that we can't do anything with this request, we
    // just forward it along. This is implicitly a supervisor command.
    val cmdFwd = Valid(new RoCCCommand)
  }

  val asidReg = Reg(Valid(new AsidTid))
  val tid = asidReg.bits.tid
  val asid = asidReg.bits.asid

  val funct = io.cmd.bits.inst.funct
  val sup = io.status.prv.orR
  val updateAsid = io.cmd.fire() & sup & funct === UInt(t_SUP_UPDATE_ASID)
  val newRequest = io.cmd.fire() & funct === UInt(t_USR_NEW_REQUEST)

  // Snoop on the input RoCCInterface. When you see a new supervisory
  // ASID-update request, set the ASID and reset the TID counter.
  io.cmd.ready := Bool(true)
  when (updateAsid) {
    asidReg.valid := Bool(true)
    asid := io.cmd.bits.rs1(asidWidth - 1, 0)
    tid := UInt(0)
    printfInfo("ASID Unit[%d]: supervisor request to update ASID to 0x%x\n",
      UInt(id), io.cmd.bits.rs1(asidWidth - 1, 0));
    // [TODO] This needs to respond to the core with the ASID and TID
    // so that the OS can save the ASID/TID for reloading later.
  }

  // Forward the command on if we're aren't supposed to touch it. The
  // ready line is ignored as this request has to go through.
  io.cmdFwd.bits := io.cmd.bits
  io.cmdFwd.valid := (io.cmd.fire() & sup & funct < UInt(4))

  when (io.cmdFwd.valid) {
    printfInfo("ASID Unit[%d] is forwarding request with funct code 0x%x\n",
      UInt(id), io.cmdFwd.bits.inst.funct) }

  // Respond with the ASID and TID if we have a vliad ASID, otherwise
  // respond with a generic -1
  io.resp.bits.rd := io.cmd.bits.inst.rd
  io.resp.bits.data := Mux(asidReg.valid,
    asid(asidWidth - 1, 0) ## tid(tidWidth - 1, 0),
    SInt(-err_XFILES_NOASID, width = xLen).asUInt)
  io.resp.valid := updateAsid

  when (io.resp.valid) {
    printfInfo("AsidUnit[%d]: responding to R%d with data 0x%x\n",
      UInt(id), io.resp.bits.rd, io.resp.bits.data)}

  // Increment the TID when a new request shows up. Negative TIDs are
  // reserved for error codes, so the valid ranges of TIDs is then:
  //   [0, 2^(tidWidth - 1) - 1]
  when (newRequest & asidReg.valid) { tid := (tid + UInt(1))(tidWidth - 2, 0) }

  io.data := asidReg

  // Reset
  when (reset) { asidReg.valid := Bool(false) }

  // Assertions
  assert(!(io.resp.valid && !io.resp.ready),
    "AsidUnit tried to respond when core was not ready")
}
