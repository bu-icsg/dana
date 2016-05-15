// See LICENSE for license details.

package xfiles
import Chisel._
import uncore.constants.MemoryOpConstants._
import rocket.{RoCCInterface, PTE}
import cde.Parameters

class DebugUnitInterface(implicit p: Parameters) extends RoCCInterface

class DebugUnit(id: Int)(implicit p: Parameters) extends XFilesModule()(p) {
  val io = new DebugUnitInterface

  val (t_REG :: t_MEM_READ :: t_MEM_WRITE :: t_VIRT_TO_PHYS :: t_UTL_READ ::
    t_UTL_WRITE :: Nil) = Enum(UInt(), 6)

  val (s_IDLE :: s_REG :: s_MEM_REQ :: s_MEM_WAIT :: s_TRANSLATE_REQ ::
    s_TRANSLATE_WAIT :: s_TRANSLATE_RESP :: s_UTL_REQ :: s_UTL_WAIT :: Nil) =
    Enum(UInt(), 9)

  val cmd = io.cmd
  val inst = cmd.bits.inst
  val funct = inst.funct
  val rs1 = cmd.bits.rs1
  val rs2 = cmd.bits.rs2
  val state = Reg(UInt(), init = s_IDLE)

  val pte = Reg(new PTE)

  val action = rs1(xLen - 1, xLen/2)
  val data = rs1(xLen/2 - 1, 0)
  val isDebug = cmd.fire() & state === s_IDLE & funct === t_XFILES_DEBUG
  val actionReg = isDebug & action === t_REG
  val actionMem = isDebug & (action === t_MEM_READ | action === t_MEM_WRITE)
  val actionVToP = isDebug & (action === t_VIRT_TO_PHYS)
  val actionUtl = isDebug & (action === t_UTL_READ | action === t_UTL_WRITE)

  val rd_d = Reg(UInt())
  val action_d = Reg(UInt())
  val data_d = Reg(UInt())
  val addr_d = Reg(UInt())

  when (isDebug) {
    rd_d := inst.rd
    action_d := action
    data_d := data
    addr_d := rs2
  }

  io.cmd.ready := Bool(true)

  when (actionReg) { state := s_REG }
  when (actionMem) { state := s_MEM_REQ }
  when (actionVToP) { state := s_TRANSLATE_REQ }
  when (actionUtl) { state := s_UTL_REQ }

  io.resp.bits.rd := rd_d
  io.resp.valid := state === s_REG | (
    state === s_MEM_WAIT & io.mem.resp.valid) | (
    state === s_TRANSLATE_RESP) | (
    state === s_MEM_WAIT & action_d === t_MEM_WRITE)
  io.resp.bits.data := data_d
  when (state === s_REG) { state := s_IDLE }

  io.mem.req.valid := state === s_MEM_REQ
  io.mem.req.bits.kill := Bool(false)
  io.mem.req.bits.phys := Bool(true)
  io.mem.req.bits.addr := addr_d
  io.mem.req.bits.data := data_d
  io.mem.req.bits.typ := MT_D
  io.mem.req.bits.cmd := Mux(action_d === t_MEM_READ, M_XRD, M_XWR)
  when (io.mem.req.fire()) { state := s_MEM_WAIT }

  when (state === s_MEM_WAIT & io.mem.resp.valid) {
    state := s_IDLE
    io.resp.bits.data := io.mem.resp.bits.data
  }

  when (state === s_MEM_WAIT & action_d === t_MEM_WRITE) {
    state := s_IDLE
    io.resp.bits.data := UInt(0)
  }

  val ptw = io.ptw(0)
  ptw.req.valid := state === s_TRANSLATE_REQ
  ptw.req.bits.addr := addr_d(coreMaxAddrBits - 1, pgIdxBits)
  ptw.req.bits.store := Bool(false)
  ptw.req.bits.fetch := Bool(false)
  when (ptw.req.fire()) { state := s_TRANSLATE_WAIT }

  when (state === s_TRANSLATE_WAIT & ptw.resp.valid) {
    pte := ptw.resp.bits.pte
    state := s_TRANSLATE_RESP
  }

  when (state === s_TRANSLATE_RESP) {
    state := s_IDLE
    val offset = addr_d(pgIdxBits - 1, 0)
    io.resp.bits.data := Mux(pte.leaf(), Cat(pte.ppn, offset), ~UInt(0, xLen))
  }

  when (ptw.req.fire()) {
    printfDebug("DUnit[%d]: ptw.req.valid | addr_v 0x%x\n", UInt(id),
      ptw.req.bits.addr) }

  when (ptw.resp.valid) {
    printfDebug("DUnit[%d]: ptw.resp.valid\n", UInt(id)) }

  when (io.mem.req.valid) {
    printfDebug("DUnit[%d]: mem.req.valid | addr 0x%x, cmd 0x%x, data0x%x\n",
      UInt(id), io.mem.req.bits.addr, io.mem.req.bits.cmd, io.mem.req.bits.data) }

  when (io.mem.resp.valid) {
    printfDebug("DUnit[%d]: mem.resp.valid | addr 0x%x, data 0x%x\n", UInt(id),
      io.mem.resp.bits.addr, io.mem.resp.bits.data) }

  when (io.resp.valid) {
    printfDebug("DUnit[%d]: io.resp.valid | rd 0x%x, data 0x%x\n", UInt(id),
      io.resp.bits.rd, io.resp.bits.data) }

  when (isDebug) {
    printfDebug("DUnit[%d]: isDebug | funct 0x%x, rs1 0x%x, rs2 0x%x\n", UInt(id),
    funct, rs1, rs2)
  }


}
