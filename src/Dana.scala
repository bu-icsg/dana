package dana

import Chisel._

class DanaInterface extends DanaBundle()() {
  val arbiter = new XFilesArbiterInterface
}

class Dana extends DanaModule()() {
  val io = new DanaInterface

  val tTable = Module(new TransactionTable)
  val control = Module(new DanaControl)

  io.arbiter <> tTable.io.arbiter
  tTable.io.dana <> control.io.tTable
}

class DanaTests(uut: Dana, isTrace: Boolean = true)
    extends DanaTester(uut, isTrace) {
  for (t <- 0 until 3) {
    val tid = t
    val nnid = t + 15 * 16
    newWriteRequest(uut.io.arbiter, tid, nnid)
    writeRndData(uut.io.arbiter, tid, nnid, 5, 10)
    info(uut.tTable)
    step(3)
  }
}
