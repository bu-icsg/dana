// See LICENSE for license details.

package dana

import Chisel._

import junctions._
import uncore._
import rocket._
import cde.{Parameters, Field}

case object NumCores extends Field[Int]
case object AntwRobEntries extends Field[Int]

abstract trait XFilesParameters extends DanaParameters {
  val numCores = p(NumCores)
  val antwRobEntries = p(AntwRobEntries)
}

abstract class XFilesModule(implicit p: Parameters) extends DanaModule()(p)
    with XFilesParameters
abstract class XFilesBundle(implicit p: Parameters) extends DanaBundle()(p)
    with XFilesParameters

class XFilesDanaInterface(implicit p: Parameters) extends XFilesBundle()(p) {
  lazy val control = new TTableControlInterface
  // val peTable = (new PETransactionTableInterface).flip
  val regFile = new TTableRegisterFileInterface
  val cache = (new CacheMemInterface).flip
}

class XFilesDanaInterfaceLearn(implicit p: Parameters)
    extends XFilesDanaInterface()(p) {
  override lazy val control = new TTableControlInterfaceLearn
}

class XFilesInterface(implicit p: Parameters) extends Bundle {
  val core = Vec(p(NumCores), new RoCCInterface)
  lazy val dana = new XFilesDanaInterface
}

class XFilesInterfaceLearn(implicit p: Parameters)
    extends XFilesInterface()(p) {
  override lazy val dana = new XFilesDanaInterfaceLearn
}

class XFilesArbiter(implicit p: Parameters) extends XFilesModule()(p) {
  val io = if (learningEnabled) new XFilesInterfaceLearn else
    new XFilesInterface

  // Module instatiation
  val tTable = if (learningEnabled) Module(new TransactionTableLearn) else
    Module(new TransactionTable)
  val antw = Module(new AsidNnidTableWalker)
  val asidRegs = Vec.fill(numCores){ Module(new AsidUnit()(p)).io }
  val coreQueue = Vec.fill(numCores){ Module(new Queue(new RoCCCommand, 4)).io }

  // Default values
  for (i <- 0 until numCores) {
    io.core(i).interrupt := Bool(false)
    io.core(i).resp.valid := tTable.io.arbiter.rocc.resp.valid &&
      tTable.io.arbiter.indexOut === UInt(i)
    // When we see a valid response from the Transaction Table, we let
    // it through. [TODO] This needs to include the assignment of output
    // values to the correct core.
    when (tTable.io.arbiter.rocc.resp.valid &&
      tTable.io.arbiter.indexOut === UInt(i)) {
      io.core(i).resp.bits := tTable.io.arbiter.rocc.resp.bits
    } .otherwise {
      io.core(i).resp.bits.rd := UInt(0)
      io.core(i).resp.bits.data := UInt(0)
    }
  }
  tTable.io.arbiter.rocc.resp.ready := Bool(true)
  // when (tTable.io.arbiter.rocc.resp.valid) {
  //   io.core(tTable.io.arbiter.indexOut).resp.valid := tTable.io.arbiter.rocc.resp.valid
  //   io.core(tTable.io.arbiter.indexOut).resp.bits := tTable.io.arbiter.rocc.resp.bits
  // }

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
    io.core(i).cmd.ready := coreArbiter.io.in(i).ready
    // Inbound reqeusts are also fed into the ASID registers
    asidRegs(i).core.cmd.valid := io.core(i).cmd.valid
    asidRegs(i).core.cmd.bits := io.core(i).cmd.bits
    asidRegs(i).core.s := io.core(i).s
    asidRegs(i).antw <> antw.io.asidUnit(i)
  }

  // Interface connections
  coreArbiter.io.out <> tTable.io.arbiter.rocc.cmd
  tTable.io.arbiter.coreIdx := coreArbiter.io.chosen
  io.dana.control <> tTable.io.control
  // io.dana.peTable <> tTable.io.peTable
  io.dana.regFile <> tTable.io.regFile
  io.dana.cache <> antw.io.cache
  (0 until numCores).map(i => io.core(i).mem <> antw.io.mem(i))
}
