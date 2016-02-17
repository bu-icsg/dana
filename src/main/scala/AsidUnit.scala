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

class asid(implicit p: Parameters) extends XFilesBundle()(p) {
  val valid = Bool()
  val asid = UInt(width = asidWidth)
  val tid = UInt(width = tidWidth)
}

class AsidUnit(implicit p: Parameters) extends DanaModule()(p) with XFilesParameters {
  val io = new XFilesBundle {
    val core = new XFilesBundle {
      val cmd = Valid(new RoCCCommand()(p)).flip
      val s = Bool(INPUT)
    }
    val antw = new AsidUnitANTWInterface
    val asid = UInt(OUTPUT, width = asidWidth)
    val tid = UInt(OUTPUT, width = tidWidth)
  }

  val asidReg = Reg(new asid)

  val updateAsid = io.core.s && io.core.cmd.bits.inst.funct === UInt(0)
  val updateANTP = io.core.s && io.core.cmd.bits.inst.funct === UInt(1)
  val newRequest = !io.core.s && io.core.cmd.bits.inst.funct(0) &&
    io.core.cmd.bits.inst.funct(1) && !io.core.cmd.bits.inst.funct(2)

  // Defaults
  io.antw.req.valid := Bool(false)
  io.antw.req.bits.antp := UInt(0)
  io.antw.req.bits.size := UInt(0)

  // Snoop on the input RoCCInterface. When you see a new supervisory
  // ASID-update request, set the ASID and reset the TID counter.
  when (io.core.cmd.fire() && updateAsid) {
    asidReg.valid := Bool(true)
    asidReg.asid := io.core.cmd.bits.rs1(asidWidth - 1, 0)
    asidReg.tid := UInt(0)
    printfInfo("Saw supervisor request to update ASID to 0x%x\n",
      io.core.cmd.bits.rs1(asidWidth - 1, 0));
    // [TODO] This needs to respond to the core with the ASID and TID
    // so that the OS can save the ASID/TID for reloading later.
  }
  // Generate a request that updates the ASID--NNID Table Pointer if
  // we see this request on the RoCCInterface
  when (io.core.cmd.fire() && updateANTP) {
    io.antw.req.valid := Bool(true);
    io.antw.req.bits.antp := io.core.cmd.bits.rs1;
    io.antw.req.bits.size := io.core.cmd.bits.rs2;
    printfInfo("Saw supervisor request to change ANTP to 0x%x of size 0x%x\n",
      io.core.cmd.bits.rs1,
      io.core.cmd.bits.rs2);
  }
  when (io.core.cmd.fire() && newRequest) {
    asidReg.tid := asidReg.tid + UInt(1)
    printfInfo("AsidUnit: Saw new request funct/rs1/rs2 0x%x/0x%x/0x%x\n",
      io.core.cmd.bits.inst.funct, io.core.cmd.bits.rs1, io.core.cmd.bits.rs2)
  }
  io.asid := asidReg.asid
  io.tid := asidReg.tid

  // Reset
  when (reset) {
    asidReg.valid := Bool(false)
  }

  // Assertions
  // There shouldn't be a new request on an invalid ASID
  assert(!(io.core.cmd.fire() && newRequest && !asidReg.valid),
    "New request on invalid ASID (a clean build may be needed)");
}
