// See LICENSE for license details.

package dana

import Chisel._

import rocket.{RoCC, RoccNMemChannels}
import cde.{Parameters}

class XFilesArbiterReq(implicit p: Parameters) extends DanaBundle()(p) {
  val tid = UInt(width = tidWidth)
  val readOrWrite = Bool()
  val countFeedback = UInt(width = feedbackWidth)
  val isNew = Bool()
  val isLast = Bool()
  val data = UInt(width = elementWidth)
}

class XFilesArbiterResp(implicit p: Parameters) extends DanaBundle()(p) {
  val tid = UInt(width = tidWidth)
  val data = UInt(width = elementWidth)
}

class XFilesArbiterInterface(implicit p: Parameters) extends DanaBundle()(p) {
  val req = Decoupled(new XFilesArbiterReq)
  val resp = Decoupled(new XFilesArbiterResp).flip
}

class XFilesDana(implicit p: Parameters) extends RoCC()(p) {
  // val io = new CoreXFilesInterface

  val xFilesArbiter = Module(new XFilesArbiter()(p))
  val dana = Module(new Dana)

  // io.arbiter <> xFilesArbiter.io.core
  io.cmd <> xFilesArbiter.io.core(0).cmd
  io.resp <> xFilesArbiter.io.core(0).resp

  // io.mem.req.valid := Bool(false)
  // io.mem.invalidate_lr := Bool(false)
  // io.mem <> xFilesArbiter.io.core(0).mem
  io.mem.req.valid := xFilesArbiter.io.core(0).mem.req.valid
  xFilesArbiter.io.core(0).mem.req.ready := io.mem.req.ready
  io.mem.req.bits.kill := xFilesArbiter.io.core(0).mem.req.bits.kill
  // io.mem.req.bits.phys := xFilesArbiter.io.core(0).mem.req.bits.phys
  io.mem.req.bits.data := xFilesArbiter.io.core(0).mem.req.bits.data
  io.mem.req.bits.addr := xFilesArbiter.io.core(0).mem.req.bits.addr
  io.mem.req.bits.tag := xFilesArbiter.io.core(0).mem.req.bits.tag
  io.mem.req.bits.cmd := xFilesArbiter.io.core(0).mem.req.bits.cmd
  io.mem.req.bits.typ := xFilesArbiter.io.core(0).mem.req.bits.typ
  io.mem.invalidate_lr := xFilesArbiter.io.core(0).mem.invalidate_lr

  io.mem.resp <> xFilesArbiter.io.core(0).mem.resp

  // io.mem.xcpt.ma := Bool(false)
  // io.mem.xcpt.pf := Bool(false)
  // io.mem.ptw.req.ready := Bool(false)
  // io.mem.ptw.invalidate := Bool(false)
  // io.mem.ptw.sret := Bool(false)

  io.busy := xFilesArbiter.io.core(0).busy
  xFilesArbiter.io.core(0).s := io.s
  io.interrupt := xFilesArbiter.io.core(0).interrupt

  io.autl.acquire.valid := Bool(false)
  io.autl.grant.ready := Bool(true)

  for (i <- 0 until p(RoccNMemChannels)) {
    io.utl(i).acquire.valid := Bool(false)
    io.utl(i).grant.ready := Bool(true)}

  xFilesArbiter.io.backend <> dana.io

  // Assertions

  // If there are no valid transactions, then all cache entries should
  // have an in use count of zero. [TODO] This violates a cross module
  // reference, but there should be a way to do this.
  // def isValid(x: TransactionState): Bool = { x.valid === Bool(true) }
  // def isZero(x: CacheState): Bool = { x.inUseCount === UInt(0) }
  // assert(!(Vec((0 until transactionTableNumEntries).map(i =>
  //   xFilesArbiter.tTable.table(i))).forall(isValid(_)) &&
  //   Vec((0 until cacheNumEntries).map(i =>
  //     dana.cache.table(i))).forall(isZero(_))),
  //   "TTable has no valid entries, but Cache has > 1 non-zero in use count")
}
