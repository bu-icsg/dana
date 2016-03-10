// See LICENSE for license details.

package dana

import Chisel._

import rocket._
import cde.{Parameters, Field}

class AsidTid(implicit p: Parameters) extends XFilesBundle()(p) {
  val asid = UInt(width = asidWidth)
  val tid = UInt(width = tidWidth)
}

class AsidUnit(id: Int)(implicit p: Parameters) extends XFilesModule()(p) {
  val io = new XFilesBundle {
    val cmd = Decoupled(new RoCCCommand).flip
    val s = Bool(INPUT)
    val resp = Decoupled(new RoCCResponse)
    val data = Valid(new AsidTid)
    // In the event that we can't do anything with this request, we
    // just forward it along. This is implicitly a supervisor command.
    val cmdFwd = Valid(new RoCCCommand)
  }

  val asidReg = Reg(Valid(new AsidTid))

  val funct = io.cmd.bits.inst.funct
  val updateAsid = io.s & funct === t_UPDATE_ASID
  val newRequest = !io.s & funct === t_NEW_REQUEST

  // Snoop on the input RoCCInterface. When you see a new supervisory
  // ASID-update request, set the ASID and reset the TID counter.
  io.cmd.ready := Bool(true)
  when (io.cmd.fire() & updateAsid) {
    asidReg.valid := Bool(true)
    asidReg.bits.asid := io.cmd.bits.rs1(asidWidth - 1, 0)
    asidReg.bits.tid := UInt(0)
    printfInfo("ASID Unit[%d]: supervisor request to update ASID to 0x%x\n",
      UInt(id), io.cmd.bits.rs1(asidWidth - 1, 0));
    // [TODO] This needs to respond to the core with the ASID and TID
    // so that the OS can save the ASID/TID for reloading later.
  }

  // Forward the command on if we're aren't supposed to touch it. The
  // ready line is ignored as this request has to go through.
  io.cmdFwd.bits := io.cmd.bits
  io.cmdFwd.valid := (io.cmd.fire() & io.s & !updateAsid)

  when (io.cmdFwd.valid) {
    printfInfo("ASID Unit[%d] is forwarding request with funct code 0x%x\n",
      UInt(id), io.cmdFwd.bits.inst.funct) }

  // Respond with the ASID and TID if we have a vliad ASID, otherwise
  // respond with a generic -1
  io.resp.bits.rd := io.cmd.bits.inst.rd
  io.resp.bits.data := Mux(asidReg.valid,
    asidReg.bits.asid(asidWidth - 1, 0) ## asidReg.bits.tid(tidWidth - 1, 0),
    SInt(-err_XFILES_NOASID, width = xLen).toUInt)
  io.resp.valid := io.cmd.fire() & updateAsid

  when (io.resp.valid) {
    printfInfo("AsidUnit[%d]: responding to R%d with data 0x%x\n",
      UInt(id), io.resp.bits.rd, io.resp.bits.data)}

  // Increment the TID when a new request shows up
  when (io.cmd.fire() & newRequest & asidReg.valid) {
    asidReg.bits.tid := asidReg.bits.tid + UInt(1)
  }

  io.data := asidReg

  // Reset
  when (reset) { asidReg.valid := Bool(false) }

  // Assertions
  assert(!(io.resp.valid && !io.resp.ready),
    "AsidUnit tried to respond when core was not ready")
}
