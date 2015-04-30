package dana

import Chisel._

class DanaInterface extends DanaBundle()() {
  val arbiter = new XFilesArbiterInterface
}

class Dana extends DanaModule()() {
  val io = new DanaInterface

  // Half clock hack
  // val halfClock = new Clock(reset) / 2
  // val clockInternal = Reg(init=UInt(0), clock = halfClock)
  // debug(clockInternal)
  // clockInternal := ~clockInternal

  // Module instantiation
  val tTable = Module(new TransactionTable)
  val control = Module(new DanaControl)
  val cache = Module(new Cache)
  val mem = Module(new Memory)

  // Wire everything up. Ordering shouldn't matter here.
  io.arbiter <> tTable.io.arbiter
  tTable.io.dana <> control.io.tTable
  cache.io.control <> control.io.cache
  cache.io.mem <> mem.io.cache
}

class DanaTests(uut: Dana, isTrace: Boolean = true)
    extends DanaTester(uut, isTrace) {
  for (t <- 0 until 3) {
    val tid = t
    val nnid = t + 15 * 16
    // newWriteRequest(uut.io.arbiter, tid, nnid)
    // writeRndData(uut.io.arbiter, tid, nnid, 5, 10)
    // info(uut.tTable)
    step(3)
  }
}
