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
    with XFilesParameters {
  val (t_READ_DATA :: t_WRITE_DATA :: t_UNUSED_2 :: t_NEW_REQUEST ::
    t_UNUSED_4 :: t_WRITE_DATA_LAST :: t_UNUSED_6 :: t_WRITE_REGISTER ::
    t_UNUSED_8 :: t_UNUSED_9 :: t_UNUSED_10 :: t_UNUSED_11 :: t_UNUSED_12 ::
    t_UNUSED_13 :: t_UNUSED_14 :: t_UNUSED_15 :: t_XFILES_ID ::
    Nil) = Enum(UInt(), 17)
  val (t_UPDATE_ASID :: t_UPDATE_ANTP :: Nil) = Enum(UInt(), 2)
}

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

  tTable.io.arbiter.rocc.resp.ready := Bool(true)

  // Non-supervisory requests from cores are fed into a round robin
  // arbiter.
  val coreArbiter = Module(new RRArbiter(new RoCCCommand, numCores))
  for (i <- 0 until numCores) {
    val cmd = io.core(i).cmd
    val funct = cmd.bits.inst.funct

    // Special requests
    val reqInfo = cmd.fire() & !io.core(i).s & funct === t_XFILES_ID
    val badRequest = cmd.fire() & !io.core(i).s & !asidRegs(i).data.valid
    val squash = !asidRegs(i).data.valid || reqInfo

    // Core to core-specific queue connections. The ASID/TID are setup
    // here if needed.
    val userRequest = cmd.fire() & !io.core(i).s & !squash
    val supervisorRequest = cmd.fire() & io.core(i).s
    coreQueue(i).enq.valid := userRequest
    // If this is a write and is new, then we need to add the TID
    // specified by the ASID unit. Otherwise, we only need to stamp
    // the ASID as the core provided the TID. We also need to respond
    // to the specific core that initiated this request telling it
    // what the TID is.
    val newWriteRequest = funct === t_NEW_REQUEST
    val asid = asidRegs(i).data.bits.asid
    val tid = asidRegs(i).data.bits.tid
    coreQueue(i).enq.bits := cmd.bits
    when (newWriteRequest) {
      coreQueue(i).enq.bits.rs1 :=
        cmd.bits.rs1(feedbackWidth - 1, 0) ## asid ## tid
    } .otherwise {
      coreQueue(i).enq.bits.rs1 := asid ## cmd.bits.rs1(tidWidth - 1, 0)
    }
    cmd.ready := coreQueue(i).enq.ready
    // Queue to RRArbiter connections
    coreQueue(i).deq.ready := coreArbiter.io.in(i).ready
    coreArbiter.io.in(i).valid := coreQueue(i).deq.valid
    coreArbiter.io.in(i).bits := coreQueue(i).deq.bits
    cmd.ready := coreArbiter.io.in(i).ready
    // Inbound reqeusts are also fed into the ASID registers
    asidRegs(i).core.cmd.valid := cmd.valid
    asidRegs(i).core.cmd.bits := cmd.bits
    asidRegs(i).core.s := io.core(i).s
    asidRegs(i).antw <> antw.io.asidUnit(i)

    // Connections back to the cores
    io.core(i).interrupt := Bool(false)
    io.core(i).resp.valid := tTable.io.arbiter.rocc.resp.valid &&
      tTable.io.arbiter.indexOut === UInt(i) || reqInfo || badRequest

    // Return -1 on a bad request
    io.core(i).resp.bits.rd := cmd.bits.inst.rd
    io.core(i).resp.bits.data := SInt(-1)

    // When we see a valid response from the Transaction Table, we let
    // it through.
    when (tTable.io.arbiter.rocc.resp.valid &&
      tTable.io.arbiter.indexOut === UInt(i)) {
      io.core(i).resp.bits := tTable.io.arbiter.rocc.resp.bits
    }

    when (reqInfo) {
      val info = UInt(elementsPerBlock, width = 6) ##
        UInt(peTableNumEntries, width = 6) ##
        UInt(transactionTableNumEntries, width = 4) ##
        UInt(cacheNumEntries, width = 4)
      io.core(i).resp.bits.rd := cmd.bits.inst.rd
      io.core(i).resp.bits.data := info
    }

    when (squash) { tTable.io.arbiter.rocc.resp.ready := Bool(false) }
  }

  // Interface connections
  coreArbiter.io.out <> tTable.io.arbiter.rocc.cmd
  tTable.io.arbiter.coreIdx := coreArbiter.io.chosen
  io.dana.control <> tTable.io.control
  // io.dana.peTable <> tTable.io.peTable
  io.dana.regFile <> tTable.io.regFile
  io.dana.cache <> antw.io.cache

  (0 until numCores).map(i => {
    io.core(i).mem <> antw.io.mem(i)
    io.core(i).autl <> antw.io.autl(i) })
}
