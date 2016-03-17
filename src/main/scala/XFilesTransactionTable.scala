// See LICENSE for license details

package xfiles

import Chisel._
import rocket.{RoCCCommand, RoCCResponse}
import cde.{Parameters, Field}

trait TableVRDAT extends XFilesParameters {
  val valid = Bool()
  val reserved = Bool()
  val done = Bool()
  val asid = UInt(width = asidWidth)
  val tid = UInt(width = tidWidth)

  def reset() {
    this.valid := Bool(false)
    this.reserved := Bool(false)
  }
}

class TableEntry(implicit p: Parameters) extends XFilesBundle()(p)
    with TableVRDAT

trait HasTable {
  def isFree[T <: TableEntry](x: T): Bool = { !x.valid & !x.reserved }
  def findAsidTid[T <: TableEntry](x: T, asid: UInt, tid: UInt): Bool = {
    (x.asid === asid) & (x.tid === tid) & (x.valid | x.reserved) }
}

class XFilesTransactionTableCmdResp(implicit p: Parameters) extends
    XFilesBundle()(p) {
  val cmd = Decoupled(new RoCCCommand).flip
  val resp = Decoupled(new RoCCResponse)
  val busy = Bool(OUTPUT)
  val regIdx = (new CoreIdx).flip
}

class XFilesTransactionTable(implicit p: Parameters) extends XFilesModule()(p)
    with HasTable {
  val io = new Bundle { // The portion of the RoCCInterface this uses
    val xfiles = new XFilesTransactionTableCmdResp
    val backend = (new XFilesTransactionTableCmdResp).flip
  }

  val numEntries = transactionTableNumEntries

  val table = Reg(Vec(numEntries, new TableEntry))

  val hasFree = table.exists(isFree(_: TableEntry))
  val idxFree = table.indexWhere(isFree(_: TableEntry))

  val cmd = io.xfiles.cmd
  val funct = cmd.bits.inst.funct
  val newRequest = cmd.fire() & funct === t_NEW_REQUEST
  val readDataPoll = cmd.fire() & funct === t_READ_DATA

  when (newRequest) {
  }

  when (readDataPoll) {
  }

  // Temporary pass-through
  io.xfiles <> io.backend

  when (reset) { (0 until numEntries).map(i => { table(i).reset() })}
}
