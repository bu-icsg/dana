package dana

import Chisel._

case object NumCores extends Field[Int]

abstract trait XFilesParameters extends UsesParameters {
  val numCores = params(NumCores)
}

class XFilesDanaInterface extends DanaBundle {
  val control = new TTableControlInterface
  val peTable = (new PETransactionTableInterface).flip
}

class XFilesInterface extends DanaBundle with XFilesParameters {
  val core = Vec.fill(numCores){(new XFilesArbiterInterface).flip}
  val dana = new XFilesDanaInterface
}

class XFilesArbiter extends DanaModule with XFilesParameters {
  val io = new XFilesInterface

  // Module instatiation
  val tTable = Module(new TransactionTable)

  // Requests from cores are fed into a round robin arbiter
  val coreArbiter = Module(new RRArbiter(new XFilesArbiterReq,
    numCores))
  (0 until numCores).map(i => io.core(i).req <> coreArbiter.io.in(i))

  // Interface connections
  // io.core <> tTable.io.arbiter
  coreArbiter.io.out <> tTable.io.arbiter.req
  // [TODO] Kludge until ASID routing is in place. The response from
  // the Transaction Table is just routed to the first core.
  io.core(0).resp <> tTable.io.arbiter.resp
  io.dana.control <> tTable.io.control
  io.dana.peTable <> tTable.io.peTable
}
