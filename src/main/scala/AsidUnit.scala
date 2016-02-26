// See LICENSE for license details.

package dana

import Chisel._

import rocket._
import cde.{Parameters, Field}

class ANTWRequest(implicit p: Parameters) extends XFilesBundle()(p) {
  val antp = UInt(width = xLen)
  val size = UInt(width = xLen)
}

class AsidUnitANTWInterface(implicit p: Parameters) extends XFilesBundle()(p) {
  val req = Decoupled(new ANTWRequest)
}

class AsidTid(implicit p: Parameters) extends XFilesBundle()(p) {
  val asid = UInt(width = asidWidth)
  val tid = UInt(width = tidWidth)
}

class AsidUnit(implicit p: Parameters) extends XFilesModule()(p) {
  val io = new XFilesBundle {
    val core = new XFilesBundle {
      val cmd = Valid(new RoCCCommand()(p)).flip
      val s = Bool(INPUT)
    }
    val antw = new AsidUnitANTWInterface
    val data = Valid(new AsidTid)
  }

  val asidReg = Reg(Valid(new AsidTid))

  val funct = io.core.cmd.bits.inst.funct
  val updateAntp = io.core.s && funct === t_UPDATE_ANTP
  val updateAsid = io.core.s && funct === t_UPDATE_ASID
  val newRequest = !io.core.s && funct === t_NEW_REQUEST

  // Defaults
  io.antw.req.valid := Bool(false)
  io.antw.req.bits.antp := UInt(0)
  io.antw.req.bits.size := UInt(0)

  // Snoop on the input RoCCInterface. When you see a new supervisory
  // ASID-update request, set the ASID and reset the TID counter.
  when (io.core.cmd.fire() && updateAsid) {
    asidReg.valid := Bool(true)
    asidReg.bits.asid := io.core.cmd.bits.rs1(asidWidth - 1, 0)
    asidReg.bits.tid := UInt(0)
    printfInfo("Saw supervisor request to update ASID to 0x%x\n",
      io.core.cmd.bits.rs1(asidWidth - 1, 0));
    // [TODO] This needs to respond to the core with the ASID and TID
    // so that the OS can save the ASID/TID for reloading later.
  }

  // Generate a request that updates the ASID--NNID Table Pointer if
  // we see this request on the RoCCInterface
  when (io.core.cmd.fire() && updateAntp) {
    io.antw.req.valid := Bool(true);
    io.antw.req.bits.antp := io.core.cmd.bits.rs1;
    io.antw.req.bits.size := io.core.cmd.bits.rs2;
    printfInfo("Saw supervisor request to change ANTP to 0x%x of size 0x%x\n",
      io.core.cmd.bits.rs1,
      io.core.cmd.bits.rs2);
  }

  when (io.core.cmd.fire() & newRequest & asidReg.valid) {
    asidReg.bits.tid := asidReg.bits.tid + UInt(1)
  }

  io.data := asidReg

  // Reset
  when (reset) { asidReg.valid := Bool(false) }
}
