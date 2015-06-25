package dana

import Chisel._

class AsidUnit extends DanaModule with XFilesParameters {
  val io = new XFilesBundle {
    val core = new XFilesBundle {
      val cmd = Valid(new RoCCCommand).flip
      val s = Bool(INPUT)
    }
    val asid = UInt(OUTPUT, width = asidWidth)
    val tid = UInt(OUTPUT, width = tidWidth)
  }

  val asidReg = Reg(new XFilesBundle {
    val valid = Bool()
    val asid = UInt(width = asidWidth)
    val tid = UInt(width = tidWidth)
  })

  val updateAsid = io.core.s && io.core.cmd.bits.inst.funct === UInt(0)
  val newRequest = !io.core.s && io.core.cmd.bits.inst.funct(0) &&
    io.core.cmd.bits.inst.funct(1)

  // Snoop on the input RoCCInterface. When you see a new supervisory
  // ASID-update request, set the ASID and reset the TID counter.
  when (io.core.cmd.fire() && updateAsid) {
    asidReg.valid := Bool(true)
    asidReg.asid := io.core.cmd.bits.rs1(asidWidth - 1, 0)
    asidReg.tid := UInt(0)
  }
  when (io.core.cmd.fire() && newRequest) {
    asidReg.tid := asidReg.tid + UInt(1)
  }
  io.asid := asidReg.asid
  io.tid := asidReg.tid

  // Reset
  when (reset) {
    asidReg.valid := Bool(true)
    asidReg.tid := UInt(5)
  }

  // Assertions
  // There shouldn't be a new request on an invalid TID
  assert(!(newRequest && !asidReg.valid),
    "New request on invalid ASID (a clean build may be needed)");
}
