// See LICENSE for license details.

package xfiles
import Chisel._
import uncore.constants.MemoryOpConstants._
import uncore.tilelink.{HasTileLinkParameters, Get, Put, GetBlock}
import rocket.{RoCCInterface, PTE}
import cde.Parameters

class DebugUnitInterface(implicit p: Parameters) extends RoCCInterface

class DebugUnit(id: Int = 0)(implicit p: Parameters) extends XFilesModule()(p)
  with HasTileLinkParameters {
  val io = new DebugUnitInterface

  val a_ = Enum(UInt(), List('REG, 'MEM_READ, 'MEM_WRITE, 'VIRT_TO_PHYS,
    'UTL_READ, 'UTL_WRITE))
  val s_ = Enum(UInt(), List('IDLE, 'REG, 'MEM_REQ, 'MEM_WAIT, 'TRANSLATE_REQ,
    'TRANSLATE_WAIT, 'TRANSLATE_RESP, 'UTL_ACQ, 'UTL_GRANT, 'UTL_RESP))

  val cmd = io.cmd
  val inst = cmd.bits.inst
  val funct = inst.funct
  val rs1 = cmd.bits.rs1
  val rs2 = cmd.bits.rs2
  val state = Reg(UInt(width=log2Up(s_.size)), init = s_('IDLE))

  val pte = Reg(new PTE)

  val action = rs1(xLen - 1, xLen/2)
  val data = rs1(xLen/2 - 1, 0)
  val isDebug = cmd.fire() & state === s_('IDLE) & funct === UInt(t_USR_XFILES_DEBUG)
  val actionReg = isDebug & action === a_('REG)
  val actionMem = isDebug & (action === a_('MEM_READ) | action === a_('MEM_WRITE))
  val actionVToP = isDebug & (action === a_('VIRT_TO_PHYS))
  val actionUtl = isDebug & (action === a_('UTL_READ) | action === a_('UTL_WRITE))

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

  cmd.ready := state === s_('IDLE)

  when (actionReg)  { state := s_('REG) }
  when (actionMem)  { state := s_('MEM_REQ) }
  when (actionVToP) { state := s_('TRANSLATE_REQ) }
  when (actionUtl)  { state := s_('UTL_ACQ) }

  io.resp.bits.rd := rd_d
  io.resp.valid := state === s_('REG) | (
    state === s_('MEM_WAIT) & io.mem.resp.valid) | (
    state === s_('TRANSLATE_RESP)) | (
    state === s_('MEM_WAIT) & action_d === a_('MEM_WRITE)) | (
    state === s_('UTL_RESP))
  io.resp.bits.data := data_d
  when (state === s_('REG)) { state := s_('IDLE) }

  io.mem.req.valid := state === s_('MEM_REQ)
  io.mem.req.bits.phys := Bool(true)
  io.mem.req.bits.addr := addr_d
  io.mem.req.bits.tag := addr_d
  io.mem.req.bits.data := data_d
  io.mem.req.bits.typ := MT_D
  io.mem.req.bits.cmd := Mux(action_d === a_('MEM_READ), M_XRD, M_XWR)
  io.mem.invalidate_lr := Bool(false)
  when (io.mem.req.fire()) { state := s_('MEM_WAIT) }

  when (state === s_('MEM_WAIT) & io.mem.resp.fire()) {
    state := s_('IDLE)
    io.resp.bits.data := io.mem.resp.bits.data
  }

  when (state === s_('MEM_WAIT) & action_d === a_('MEM_WRITE)) {
    state := s_('IDLE)
    io.resp.bits.data := UInt(0)
  }

  val ptw = io.ptw(0)
  ptw.req.valid := state === s_('TRANSLATE_REQ)
  ptw.req.bits.addr := addr_d(coreMaxAddrBits - 1, pgIdxBits)
  ptw.req.bits.store := Bool(false)
  ptw.req.bits.fetch := Bool(false)
  when (ptw.req.fire()) { state := s_('TRANSLATE_WAIT) }

  when (state === s_('TRANSLATE_WAIT) & ptw.resp.fire()) {
    pte := ptw.resp.bits.pte
    state := s_('TRANSLATE_RESP)
  }

  when (state === s_('TRANSLATE_RESP)) {
    val offset = addr_d(pgIdxBits - 1, 0)
    io.resp.bits.data := Mux(pte.leaf(), Cat(pte.ppn, offset), ~UInt(0, xLen))
    state := s_('IDLE)
  }

  io.autl.acquire.valid := state === s_('UTL_ACQ)
  private val utlBlockOffset = tlBeatAddrBits + tlByteAddrBits
  val utlDataPutVec = Wire(Vec(tlDataBits / xLen, UInt(width = xLen)))
  val addr_block = addr_d(coreMaxAddrBits - 1, utlBlockOffset)
  val addr_beat = addr_d(utlBlockOffset - 1, tlByteAddrBits)
  val addr_byte = addr_d(tlByteAddrBits - 1, 0)
  val addr_word = tlDataBits compare xLen match {
    case 1 => addr_d(tlByteAddrBits - 1, log2Up(xLen/8))
    case 0 => UInt(0)
    case -1 => throwException("XLen > tlByteAddrBits (this doesn't make sense!)")
  }

  (0 until tlDataBits/xLen).map(i => utlDataPutVec(i) := UInt(0))
  utlDataPutVec(addr_word) := data_d
  io.autl.acquire.bits := Mux(action_d === a_('UTL_READ),
    Get(client_xact_id = UInt(0),
      addr_block = addr_block,
      addr_beat = addr_beat,
      addr_byte = addr_byte,
      operand_size = MT_D,
      alloc = Bool(false)),
    Put(client_xact_id = UInt(0),
      addr_block = addr_block,
      addr_beat = addr_beat,
      data = utlDataPutVec.toBits,
      wmask = Option(Fill(xLen/8, UInt(1, 1)) << addr_byte),
      alloc = Bool(false)))
  io.autl.grant.ready := state === s_('UTL_GRANT)
  when (io.autl.acquire.fire()) { state := s_('UTL_GRANT) }

  val utlData = Reg(UInt(width = tlDataBits))
  when (state === s_('UTL_GRANT) & io.autl.grant.fire()) {
    utlData := io.autl.grant.bits.data
    state := s_('UTL_RESP)
  }

  val utlDataGetVec = Wire(Vec(tlDataBits / xLen, UInt(width = xLen)))

  (0 until tlDataBits/xLen).map(i =>
    utlDataGetVec(i) := utlData((i+1) * xLen-1, i * xLen))
  when (state === s_('UTL_RESP)) {
    io.resp.bits.data := Mux(action_d === a_('UTL_READ),
      utlDataGetVec(addr_word), UInt(0))
    state := s_('IDLE)
    printfDebug("DUnit[%d]: tlBeatAddrBits: 0d%d\n", UInt(id), UInt(tlBeatAddrBits))
    printfDebug("DUnit[%d]: tlByteAddrBits: 0d%d\n", UInt(id), UInt(tlByteAddrBits))
    printfDebug("DUnit[%d]: tlDataBytes:    0d%d\n", UInt(id), UInt(log2Up(tlDataBytes)))
    printfDebug("DUnit[%d]: addr_word       0d%d\n", UInt(id), addr_word)
    printfDebug("DUnit[%d]: log2Up(xLen):   0d%d\n", UInt(id), UInt(log2Up(xLen/8)))
  }

  when (io.autl.acquire.fire()) {
    val data = io.autl.acquire.bits
    printfDebug("DUnit[%d]: autl.acquire.fire | addr_d 0x%x, addr_block 0x%x, addr_beat 0x%x, addr_byte 0x%x, data 0x%x, wmask 0x%x\n",
      UInt(id), addr_d, data.addr_block, data.addr_beat, data.addr_byte(),
      data.data, data.wmask())

    (0 until tlDataBits/xLen).map(i =>
      printfDebug("DUnit[%d]:                 | utlDataPutVec(%d): 0x%x\n",
        UInt(id), UInt(i), utlDataPutVec(i)))
  }

  when (state === s_('UTL_GRANT) & io.autl.grant.fire()) {
    printfDebug("DUnit[%d]: autl.grant.fire | data 0x%x, beat 0x%x\n",
      UInt(id), io.autl.grant.bits.data, io.autl.grant.bits.addr_beat) }

  when (ptw.req.fire()) {
    printfDebug("DUnit[%d]: ptw.req.fire | addr_v 0x%x\n", UInt(id),
      ptw.req.bits.addr) }

  when (state === s_('TRANSLATE_WAIT) & ptw.resp.fire()) {
    printfDebug("DUnit[%d]: ptw.resp.fire\n", UInt(id)) }

  when (io.mem.req.fire()) {
    printfDebug("DUnit[%d]: mem.req.fire | addr 0x%x, cmd 0x%x, data0x%x\n",
      UInt(id), io.mem.req.bits.addr, io.mem.req.bits.cmd, io.mem.req.bits.data) }

  when (state === s_('MEM_WAIT) & io.mem.resp.fire()) {
    printfDebug("DUnit[%d]: mem.resp.fire | addr 0x%x, data 0x%x\n", UInt(id),
      io.mem.resp.bits.addr, io.mem.resp.bits.data) }

  when (io.resp.fire()) {
    printfDebug("DUnit[%d]: io.resp.fire | rd 0x%x, data 0x%x\n", UInt(id),
      io.resp.bits.rd, io.resp.bits.data) }

  when (isDebug) {
    printfDebug("DUnit[%d]: isDebug | funct 0x%x, rs1 0x%x, rs2 0x%x, action 0x%x\n",
      UInt(id), funct, rs1, rs2, action)
    printfDebug("                  | rd_d 0x%x, action_d 0x%x, data_d 0x%x, addr_d 0x%x\n",
    inst.rd, action, data, rs2)
  }

  val state_d = Reg(UInt(width=log2Up(s_.size)))
  state_d := state
  when (state =/= state_d) {
    printfDebug("DUnit[%d]: state transtion %d -> %d\n", UInt(id), state_d,
      state)
  }

  val mem_req_d = RegNext(io.mem.req.valid)
  when (io.mem.req.valid & !mem_req_d) {
    val req = io.mem.req.bits
    printfDebug("DUnit[%d]: Trying req Cmd %d, Typ %d, addr 0x%x, data 0x%x\n",
      UInt(id), req.cmd, req.typ, req.addr, req.data)
  }
  when (!io.mem.req.valid & mem_req_d) {
    val req = io.mem.req.bits
    printfDebug("DUnit[%d]:   No longer requesting\n", UInt(id))
  }

  // assert(!(state > s_.last._2), "DUnit: State out of bounds")
}
