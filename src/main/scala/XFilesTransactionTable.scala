// See LICENSE for license details

package xfiles

import Chisel._
import rocket.{RoCCCommand, RoCCResponse}
import cde.{Parameters, Field}

class XFilesTransactionTableState(implicit p: Parameters)
    extends XFilesBundle()(p) {
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

class XFilesTransactionTableCmdResp(implicit p: Parameters) extends
    XFilesBundle()(p) {
  val cmd = Decoupled(new RoCCCommand).flip
  val resp = Decoupled(new RoCCResponse)
  val busy = Bool(OUTPUT)
  val s = Bool(INPUT)
  val regIdx = (new CoreIdx).flip
}

class XFilesTransactionTable(implicit p: Parameters) extends XFilesModule()(p) {
  val io = new Bundle { // The portion of the RoCCInterface this uses
    val xfiles = new XFilesTransactionTableCmdResp
    val backend = (new XFilesTransactionTableCmdResp).flip
  }

  val numEntries = transactionTableNumEntries

  val table = Reg(Vec(numEntries, new XFilesTransactionTableState))

  def isFree(x: XFilesTransactionTableState): Bool = { !x.valid & !x.reserved}

  val cmd = io.xfiles.cmd
  val funct = cmd.bits.inst.funct
  val sup = io.xfiles.s
  val newRequest = cmd.fire() & funct === t_NEW_REQUEST & !sup
  val readDataPoll = cmd.fire() & funct === t_READ_DATA & !sup

  when (newRequest) {
  }

  when (readDataPoll) {
  }

  // Temporary pass-through
  io.xfiles <> io.backend

  when (reset) { (0 until numEntries).map(i => { table(i).reset() })}
}
