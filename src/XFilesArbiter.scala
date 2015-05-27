package dana

import Chisel._

class XFilesDanaInterface extends DanaBundle{
  val control = new TTableControlInterface
  val peTable = (new PETransactionTableInterface).flip
}

class XFilesInterface extends DanaBundle {
  val core = (new XFilesArbiterInterface).flip
  val dana = new XFilesDanaInterface
}

class XFilesArbiter extends DanaModule {
  val io = new XFilesInterface

  // Module instatiation
  val tTable = Module(new TransactionTable)

  // Interface connections
  io.core <> tTable.io.arbiter
  io.dana.control <> tTable.io.control
  io.dana.peTable <> tTable.io.peTable
}
