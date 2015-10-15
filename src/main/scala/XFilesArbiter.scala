package dana

import Chisel._

import rocket._

case object NumCores extends Field[Int]
case object AntwRobEntries extends Field[Int]

abstract trait XFilesParameters extends UsesParameters with DanaParameters {
  implicit val p: Parameters
  val numCores = p(NumCores)
  val antwRobEntries = p(AntwRobEntries)
}

abstract class XFilesModule(implicit p: Parameters) extends DanaModule()(p)
    with XFilesParameters
abstract class XFilesBundle(implicit p: Parameters) extends DanaBundle()(p)
    with XFilesParameters

class XFilesDanaInterface(implicit p: Parameters) extends XFilesBundle()(p) {
  val control = new TTableControlInterface
  // val peTable = (new PETransactionTableInterface).flip
  val regFile = new TTableRegisterFileInterface
  val cache = (new CacheMemInterface).flip
}

class XFilesInterface(implicit p: Parameters) extends XFilesBundle()(p) {
  val core = Vec.fill(numCores){ new RoCCInterface }
  val dana = new XFilesDanaInterface
}

class XFilesArbiter(implicit p: Parameters) extends XFilesModule()(p) {
  val io = new XFilesInterface

  // Module instatiation
  val tTable = Module(new TransactionTable)
  val antw = Module(new AsidNnidTableWalker)
  val asidRegs = Vec.fill(numCores){ Module(new AsidUnit()(p)).io }
  val coreQueue = Vec.fill(numCores){ Module(new Queue(new RoCCCommand, 4)).io }

  // Default values
  for (i <- 0 until numCores) {
    io.core(i).resp.valid := Bool(false)
    io.core(i).resp.bits.rd := UInt(0)
    io.core(i).resp.bits.data := UInt(0)
    io.core(i).interrupt := Bool(false)
  }
  tTable.io.arbiter.rocc.resp.ready := Bool(true)

  // Non-supervisory requests from cores are fed into a round robin
  // arbiter.
  val coreArbiter = Module(new RRArbiter(new RoCCCommand,
    numCores))
  for (i <- 0 until numCores) {
    // Core to core-specific queue connections. The ASID/TID are setup
    // here if needed.
    coreQueue(i).enq.valid := io.core(i).cmd.valid & !io.core(i).s
    // If this is a write and is new, then we need to add the TID
    // specified by the ASID unit. Otherwise, we only need to stamp
    // the ASID as the core provided the TID. We also need to respond
    // to the specific core that initiated this request telling it
    // what the TID is.
    when (io.core(i).cmd.bits.inst.funct(0) && io.core(i).cmd.bits.inst.funct(1) &&
      !io.core(i).cmd.bits.inst.funct(2)) {
      coreQueue(i).enq.bits := io.core(i).cmd.bits
      coreQueue(i).enq.bits.rs1 :=
        io.core(i).cmd.bits.rs1(feedbackWidth - 1, 0) ##
        asidRegs(i).asid ##
        asidRegs(i).tid
      // Respond to the core with the TID
      // io.core(i).resp.valid := Bool(false)
      // io.core(i).resp.bits.rd := io.core(i).cmd.bits.inst.rd
      // io.core(i).resp.bits.data := asidRegs(i).tid << UInt(elementWidth)
      // tTable.io.arbiter.rocc.resp.ready := Bool(false)
    } .otherwise {
      coreQueue(i).enq.bits := io.core(i).cmd.bits
      coreQueue(i).enq.bits.rs1 :=
        asidRegs(i).asid ##
        io.core(i).cmd.bits.rs1(tidWidth - 1, 0)
    }
    io.core(i).cmd.ready := coreQueue(i).enq.ready
    // Queue to RRArbiter connections
    coreQueue(i).deq.ready := coreArbiter.io.in(i).ready
    coreArbiter.io.in(i).valid := coreQueue(i).deq.valid
    coreArbiter.io.in(i).bits := coreQueue(i).deq.bits
    // coreArbiter.io.in(i).bits.rs1 :=
    //   Cat(io.core(i).cmd.bits.rs1(feedbackWidth + tidWidth - 1, tidWidth),
    //     asidRegs(i).asid,
    //     io.core(i).cmd.bits.rs1(tidWidth - 1, 0))
    io.core(i).cmd.ready := coreArbiter.io.in(i).ready
    // Inbound reqeusts are also fed into the ASID registers
    asidRegs(i).core.cmd.valid := io.core(i).cmd.valid
    asidRegs(i).core.cmd.bits := io.core(i).cmd.bits
    asidRegs(i).core.s := io.core(i).s
    // [TODO] Attach the ASID Units to the ANTW
    asidRegs(i).antw <> antw.io.asidUnit(i)
  }

  // When we see a valid response from the Transaction Table, we let
  // it through. [TODO] This needs to include the assignment of output
  // values to the correct core.
  when (tTable.io.arbiter.rocc.resp.valid) {
    io.core(tTable.io.arbiter.indexOut).resp.valid := tTable.io.arbiter.rocc.resp.valid
    io.core(tTable.io.arbiter.indexOut).resp.bits := tTable.io.arbiter.rocc.resp.bits
  }

  // Interface connections
  coreArbiter.io.out <> tTable.io.arbiter.rocc.cmd
  tTable.io.arbiter.coreIdx := coreArbiter.io.chosen
  io.dana.control <> tTable.io.control
  // io.dana.peTable <> tTable.io.peTable
  io.dana.regFile <> tTable.io.regFile
  io.dana.cache <> antw.io.cache
  (0 until numCores).map(i => io.core(i).mem <> antw.io.mem(i))

  // Assertions
}
