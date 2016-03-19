// See LICENSE for license details

package xfiles

import Chisel._
import rocket.{RoCCCommand, RoCCResponse}
import cde.{Parameters, Field}

trait TableRVDIO extends XFilesParameters {
  val flags = new Bundle{
    val reserved = Bool()  // Entry is in use
    val valid = Bool()     // Eligible for scheduling
    val done = Bool()      // Entry is finished
    val input = Bool()     // Entry is waiting for input queue to be not empty
    val output = Bool()    // Entry is waiting for output queue to be not full
  }
  val asid = UInt(width = asidWidth)
  val tid = UInt(width = tidWidth)

  def reset() {
    this.flags.valid := Bool(false)
    this.flags.reserved := Bool(false)
  }
  def reserve(asid: UInt, tid: UInt) {
    this.flags.valid := Bool(false)
    this.flags.reserved := Bool(true)
    this.flags.done := Bool(false)
  }
}

class TableEntry(implicit p: Parameters) extends XFilesBundle()(p)
    with TableRVDIO

trait HasTable {
  def isFree[T <: TableEntry](x: T): Bool = { !x.flags.reserved }
  def findAsidTid[T <: TableEntry](x: T, asid: UInt, tid: UInt): Bool = {
    (x.asid === asid) & (x.tid === tid) & (x.flags.valid | x.flags.reserved) }
}

class XFilesTransactionTableCmdResp(implicit p: Parameters) extends
    XFilesBundle()(p) {
  val cmd = Decoupled(new RoCCCommand).flip
  val resp = Decoupled(new RoCCResponse)
  val busy = Bool(OUTPUT)
  val regIdx = (new CoreIdx).flip
}

class RespBundle(implicit p: Parameters) extends XFilesBundle()(p) {
  val rocc = new RoCCResponse
  val idx = UInt(width = numCores)
}

class XFilesTransactionTable(implicit p: Parameters) extends XFilesModule()(p)
    with HasTable with XFilesResponseCodes {
  val io = new Bundle { // The portion of the RoCCInterface this uses
    val xfiles = new XFilesTransactionTableCmdResp
    val backend = (new XFilesTransactionTableCmdResp).flip
  }

  def getCmdAsid() = { io.xfiles.cmd.bits.rs1(asidWidth + tidWidth - 1, tidWidth) }
  def getCmdTid() = { io.xfiles.cmd.bits.rs1(tidWidth - 1, 0) }

  def getRespCode() = { io.backend.resp.bits.data(xLen - 1, xLen - respCodeWidth) }
  def getRespTid() = {
    val offset = xLen - respCodeWidth
    io.backend.resp.bits.data(offset - 1, offset - tidWidth) }
  def getRespAsid() = {
    val offset = xLen - respCodeWidth - tidWidth
    io.backend.resp.bits.data(offset - 1, offset - asidWidth) }

  val numEntries = transactionTableNumEntries

  val table = Reg(Vec(numEntries, new TableEntry))

  val hasFree = table.exists(isFree(_: TableEntry))
  val idxFree = table.indexWhere(isFree(_: TableEntry))

  val cmd = io.xfiles.cmd
  val funct = cmd.bits.inst.funct
  val newRequest = cmd.fire() & funct === t_NEW_REQUEST
  val writeData = cmd.fire() & funct === t_WRITE_DATA
  val writeDataLast = cmd.fire() & funct === t_WRITE_DATA_LAST
  val readDataPoll = cmd.fire() & funct === t_READ_DATA
  val asid =  getCmdAsid()
  val tid = getCmdTid()

  val error = Reg(Bool(), init = Bool(false))

  // Temporary pass-through
  io.xfiles.cmd <> io.backend.cmd
  io.xfiles.busy := io.backend.busy

  // resp
  val resp_d = Reg(Valid(new RespBundle))
  io.xfiles.resp.valid := io.backend.resp.valid | resp_d.valid
  io.xfiles.resp.bits := io.backend.resp.bits
  io.backend.resp.ready := io.xfiles.resp.ready

  resp_d.valid := newRequest
  resp_d.bits.rocc.rd := cmd.bits.inst.rd
  resp_d.bits.idx := io.xfiles.regIdx.cmd

  // regIdx
  io.xfiles.regIdx.resp := io.backend.regIdx.resp
  io.backend.regIdx.cmd := io.xfiles.regIdx.cmd

  when (resp_d.valid) {
    io.xfiles.resp.bits.rd := resp_d.bits.rocc.rd
    io.xfiles.resp.bits.data := resp_d.bits.rocc.data
    io.xfiles.regIdx.resp := resp_d.bits.idx
  }

  when (newRequest) {
    genResp(resp_d.bits.rocc.data, resp_TID, tid)
  }

  when (writeData) {
  }

  when (writeDataLast) {
  }

  when (readDataPoll) {
  }

  assert (!(error), "XF TTable error asserted")

  assert (!(resp_d.valid & io.backend.resp.valid),
    "XF TTable: newRequest resp just aliased backend resp")

  when (reset) { (0 until numEntries).map(i => { table(i).reset() })}
}
