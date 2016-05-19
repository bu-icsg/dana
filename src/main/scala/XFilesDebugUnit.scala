// See LICENSE for license details.

package xfiles
import Chisel._
import uncore.constants.MemoryOpConstants._
import uncore.{HasTileLinkParameters, Get, GetBlock}
import rocket.{RoCCInterface, PTE}
import cde.Parameters

class DebugUnitInterface(implicit p: Parameters) extends RoCCInterface

class DebugUnit(id: Int)(implicit p: Parameters) extends XFilesModule()(p)
  with HasTileLinkParameters {
  val io = new DebugUnitInterface

  val (t_REG :: t_MEM_READ :: t_MEM_WRITE :: t_VIRT_TO_PHYS :: t_UTL_READ ::
    t_UTL_WRITE :: Nil) = Enum(UInt(), 6)

  val (s_IDLE :: s_REG :: s_MEM_REQ :: s_MEM_WAIT :: s_TRANSLATE_REQ ::
    s_TRANSLATE_WAIT :: s_TRANSLATE_RESP :: s_UTL_ACQ :: s_UTL_GRANT ::
    s_UTL_RESP :: Nil) = Enum(UInt(), 10)

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

  when (actionReg)  { state := s_REG }
  when (actionMem)  { state := s_MEM_REQ }
  when (actionVToP) { state := s_TRANSLATE_REQ }
  when (actionUtl)  { state := s_UTL_ACQ }

  io.resp.bits.rd := rd_d
  io.resp.valid := state === s_REG | (
    state === s_MEM_WAIT & io.mem.resp.valid) | (
    state === s_TRANSLATE_RESP) | (
    state === s_MEM_WAIT & action_d === t_MEM_WRITE) | (
    state === s_UTL_RESP)
  io.resp.bits.data := data_d
  when (state === s_REG) { state := s_IDLE }

  io.mem.req.valid := state === s_MEM_REQ
  io.mem.req.bits.kill := Bool(false)
  io.mem.req.bits.phys := Bool(false)
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
    val offset = addr_d(pgIdxBits - 1, 0)
    io.resp.bits.data := Mux(pte.leaf(), Cat(pte.ppn, offset), ~UInt(0, xLen))
    state := s_IDLE
  }

  io.autl.acquire.valid := state === s_UTL_ACQ
  private val utlBlockOffset = tlBeatAddrBits + tlByteAddrBits
  // io.autl.acquire.bits := GetBlock(
  //   addr_block = addr_d(coreMaxAddrBits - 1, utlBlockOffset))
  io.autl.acquire.bits := Get(
    client_xact_id = UInt(0),
    addr_block = addr_d(coreMaxAddrBits - 1, utlBlockOffset),
    addr_beat = addr_d(utlBlockOffset - 1, tlByteAddrBits),
    addr_byte = addr_d(tlByteAddrBits - 1, 0),
    operand_size = MT_D,
    alloc = Bool(false))
  io.autl.grant.ready := state === s_UTL_GRANT
  when (io.autl.acquire.fire()) { state := s_UTL_GRANT }

  val utlData = Reg(UInt(width = tlDataBits))
  val utlBeat = Reg(UInt(width = tlBeatAddrBits))
  when (io.autl.grant.fire()) {
    utlData := io.autl.grant.bits.data
    utlBeat := io.autl.grant.bits.addr_beat
    state := s_UTL_RESP
  }

  val utlDataVec = Wire(Vec.fill(tlDataBits / xLen)(UInt(width = xLen)))

  (0 until tlDataBits/xLen).map(i =>
    utlDataVec(i) := utlData((i+1) * xLen-1, i * xLen))
  when (state === s_UTL_RESP) {
    val addr_word = addr_d(tlByteAddrBits - 1, log2Up(xLen/8))
    io.resp.bits.data := utlDataVec(addr_word)
    state := s_IDLE
    // printfDebug("DUnit[%d]: utlOffset 0d%d\n", UInt(id), utlOffset)
    printfDebug("DUnit[%d]: tlBeatAddrBits: 0d%d\n", UInt(id), UInt(tlBeatAddrBits))
    printfDebug("DUnit[%d]: tlByteAddrBits: 0d%d\n", UInt(id), UInt(tlByteAddrBits))
    printfDebug("DUnit[%d]: tlDataBytes:    0d%d\n", UInt(id), UInt(log2Up(tlDataBytes)))
    printfDebug("DUnit[%d]: addr_word       0d%d\n", UInt(id), addr_word)
    printfDebug("DUnit[%d]: log2Up(xLen):   0d%d\n", UInt(id), UInt(log2Up(xLen/8)))
  }

  when (io.autl.acquire.fire()) {
    val data = io.autl.acquire.bits
    printfDebug("DUnit[%d]: autl.acquire.valid | addr_d 0x%x, addr_block 0x%x, addr_beat 0x%x, addr_byte 0x%x\n",
      UInt(id), addr_d, data.addr_block, data.addr_beat,
      addr_d(tlByteAddrBits - 1, 0)) }

  when (io.autl.grant.fire()) {
    printfDebug("DUnit[%d]: autl.grant.valid | data 0x%x, beat 0x%x\n",
      UInt(id), io.autl.grant.bits.data, io.autl.grant.bits.addr_beat) }

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
