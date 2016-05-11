// See LICENSE for license details.

package xfiles
import Chisel._
import rocket.RoCCInterface
import cde.Parameters

class DebugUnitInterface(implicit p: Parameters) extends RoCCInterface

class DebugUnit(id: Int)(implicit p: Parameters) extends XFilesModule()(p) {
  val io = new DebugUnitInterface

  val (t_REG :: t_MEM :: t_UTL :: Nil) = Enum(UInt(), 3)

  val (s_IDLE :: s_REG :: s_MEM_REQ :: s_MEM_WAIT :: s_UTL_REQ :: s_UTL_WAIT ::
    Nil) = Enum(UInt(), 6)

  val cmd = io.cmd
  val inst = cmd.bits.inst
  val funct = inst.funct
  val rs1 = cmd.bits.rs1
  val rs2 = cmd.bits.rs2
  val state = Reg(UInt(), init = s_IDLE)

  val isDebug = cmd.fire() & state === s_IDLE & funct === t_XFILES_DEBUG
  val actionReg = isDebug & rs1 === t_REG
  val actionMem = isDebug & rs1 === t_MEM
  val actionUtl = isDebug & rs1 === t_UTL

  val rd_d = Reg(UInt())
  val rs2_d = Reg(UInt())

  when (isDebug) {
    rd_d := inst.rd
    rs2_d := rs2
  }

  io.cmd.ready := Bool(true)

  when (actionReg) { state := s_REG }
  when (actionMem) { state := s_MEM_REQ }
  when (actionUtl) { state := s_UTL_REQ }

  io.resp.valid := state === s_REG
  io.resp.bits.rd := rd_d
  io.resp.bits.data := rs2_d
  when (state === s_REG) { state := s_IDLE }

  io.mem.req.valid := state === s_MEM_REQ
  when (io.mem.req.valid & io.mem.req.ready) { state := s_MEM_WAIT }

  when (cmd.valid) {
    printfDebug("DUnit[%d]: cmd.valid | funct 0x%x, rs1 0x%x, rs2 0x%x\n", UInt(id),
    funct, rs1, rs2)
  }

  when (isDebug) {
    printfDebug("DUnit[%d]: isDebug | funct 0x%x, rs1 0x%x, rs2 0x%x\n", UInt(id),
    funct, rs1, rs2)
  }


}
