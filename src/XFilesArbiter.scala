package dana

import Chisel._

case object NumCores extends Field[Int]

abstract trait XFilesParameters extends UsesParameters {
  val numCores = params(NumCores)
}

abstract class XFilesModule extends DanaModule with XFilesParameters
abstract class XFilesBundle extends DanaBundle with XFilesParameters

class XFilesDanaInterface extends XFilesBundle {
  val control = new TTableControlInterface
  val peTable = (new PETransactionTableInterface).flip
}

class XFilesInterface extends XFilesBundle {
  val core = Vec.fill(numCores){ new RoCCInterface }
  val dana = new XFilesDanaInterface
}

class XFilesArbiter extends XFilesModule {
  val io = new XFilesInterface

  // Module instatiation
  val tTable = Module(new TransactionTable)

  // Requests from cores are fed into a round robin arbiter
  val coreArbiter = Module(new RRArbiter(new RoCCCommand,
    numCores))
  (0 until numCores).map(i => io.core(i).cmd <> coreArbiter.io.in(i))

  // Interface connections
  // io.core <> tTable.io.arbiter
  coreArbiter.io.out <> tTable.io.arbiter.cmd
  // [TODO] Kludge until ASID routing is in place. The response from
  // the Transaction Table is just routed to the first core.
  io.core(0).resp <> tTable.io.arbiter.resp
  io.dana.control <> tTable.io.control
  io.dana.peTable <> tTable.io.peTable
}
