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
  val control = Module(new Control)
  val cache = Module(new Cache)
  val mem = Module(new Memory)
  val peTable = Module(new ProcessingElementTable)
  val regFile = Module(new RegisterFile)

  // Wire everything up. Ordering shouldn't matter here.
  io.arbiter <> tTable.io.arbiter
  tTable.io.control <> control.io.tTable
  cache.io.control <> control.io.cache
  cache.io.mem <> mem.io.cache
  control.io.peTable <> peTable.io.control
  control.io.regFile <> regFile.io.control
  peTable.io.cache <> cache.io.pe
  peTable.io.tTable <> tTable.io.peTable
  peTable.io.regFile <> regFile.io.pe
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
