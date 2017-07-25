// See LICENSE.BU for license details.
// See LICENSE.IBM for license details.

package xfiles

import chisel3._
import chisel3.util._
import rocket.{RoCCCommand, RoCCResponse, RoCCInterface}
import cde._

case object TransactionTableQueueSize extends Field[Int]

trait FlagsVDIO {
  val valid  = Bool() // Eligible for scheduling on the backend
  val done   = Bool() // Entry is finished
  val input  = Bool() // Asserted when waiting for input queue to be not empty
  val output = Bool() // Asserted when waiting for output queue to be not full
  def set(x: String) {x.map((a: Char) => {
      a match {
        case 'v' => this.valid  := true.B
        case 'd' => this.done   := true.B
        case 'i' => this.input  := true.B
        case 'o' => this.output := true.B
        case _ => throw new Exception("FlagsVDIO.set cannot match " + a) }})
  }
  def reset(x: String) {x.map((a: Char) => {
      a match {
        case 'v' => this.valid  := false.B
        case 'd' => this.done   := false.B
        case 'i' => this.input  := false.B
        case 'o' => this.output := false.B
        case _ => throw new Exception("FlagsVDIO.reset cannot match " + a) }})
  }
}

trait TableRVDIO extends XFilesParameters {
  val flags = new Bundle with FlagsVDIO {
    val reserved = Bool() // Entry is in use
  }
  val asid = UInt(asidWidth.W)
  val tid  = UInt(tidWidth.W)

  def reset() {
    this.flags.valid    := false.B
    this.flags.reserved := false.B
  }
  def reserve(asid: UInt, tid: UInt) {
    this.flags.reserved := true.B
    this.flags.valid    := true.B
    this.flags.done     := false.B
    this.flags.input    := false.B
    this.flags.output   := false.B
    this.asid           := asid
    this.tid            := tid
  }
}

class TableEntry(implicit p: Parameters) extends XFilesBundle()(p)
    with TableRVDIO {
  aliasList += ( "flags.reserved" -> "R", "flags.valid" -> "V",
    "flags.done" -> "D", "flags.input" -> "I", "flags.output" -> "O",
    "asid" -> "ASID", "tid" -> "TID")
}

trait HasTable {
  def isFree[T <: TableEntry](x: T): Bool = { !x.flags.reserved }
  def findAsidTid[T <: TableEntry](x: T, asid: UInt, tid: UInt): Bool = {
    (x.asid === asid) & (x.tid === tid) & (x.flags.valid | x.flags.reserved) }
}

class XFilesTransactionTableCmdResp(implicit p: Parameters) extends
    XFilesBundle()(p) {
  val cmd = Flipped(Decoupled(new RoCCCommand))
  val resp = Decoupled(new RoCCResponse)
  val busy = Output(Bool())
}

class RespBundle(implicit p: Parameters) extends XFilesBundle()(p) {
  val rocc = new RoCCResponse
}

class XFilesTransactionTable(implicit p: Parameters) extends XFilesModule()(p)
    with HasTable with XFilesResponseCodes {
  val io = IO( new Bundle {
    val xfiles = new RoCCInterface
    val backend = Flipped(new XFilesBackendInterface)
    val status = Input(p(BuildXFilesBackend).csrStatus_gen(p))
    val probes = Output(new XFProbes)
    val probes_backend = Output(p(BuildXFilesBackend).csrProbes_gen(p))
  })

  override val printfSigil = "xfiles.TTable: "

  val queueSize = p(TransactionTableQueueSize)

  val cmd = io.xfiles.cmd
  val funct = cmd.bits.inst.funct

  def getCmdAsid() = { cmd.bits.rs1(asidWidth + tidWidth - 1, tidWidth) }
  def getCmdTid() = { cmd.bits.rs1(tidWidth - 1, 0) }

  val roccRespBits = io.backend.rocc.resp.bits.data
  def getRespCode() = { roccRespBits(xLen - 1, xLen - respCodeWidth) }
  def getRespTid() = {
    val offset = xLen - respCodeWidth
    roccRespBits(offset - 1, offset - tidWidth) }
  def getRespAsid() = {
    val offset = xLen - respCodeWidth - tidWidth
    roccRespBits(offset - 1, offset - asidWidth) }

  val numEntries = transactionTableNumEntries

  val table = Reg(Vec(numEntries, new TableEntry))
  val testQueue = Module(new Queue(new XFilesRs1Rs2Funct, queueSize))
  val queueInput = Vec.tabulate(numEntries)(
    x => Module(new Queue(new XFilesRs1Rs2Funct, queueSize)).io)
  // val queueInput = Vec(numEntries,
  //   Module(new Queue(new XFilesRs1Rs2Funct, queueSize)).io)
  // We use a Queue with an "almost full" high water mark to provide
  // flow control for the backend.
  val queueOutput = Vec.fill(numEntries)(
    Module(new QueueAf(UInt(xLen.W), queueSize,
      almostFullEntries = queueSize - 1)).io)
  // The RRArbiter is not communicating data, but is only used to
  // provide arbitration to generate an index
  val arbiter = Module(new RRArbiter(Bool(), numEntries)).io

  val idxFree = table.indexWhere(isFree(_: TableEntry))
  val hasFree = (
    table.exists(isFree(_: TableEntry)) && idxFree < io.status.ttable_entries )

  val newRequest = cmd.fire() & funct === t_USR_NEW_REQUEST.U
  val writeData = cmd.fire() & funct === t_USR_WRITE_DATA.U
  val writeDataLast = cmd.fire() & funct === t_USR_WRITE_DATA_LAST.U
  val readDataPoll = cmd.fire() & funct === t_USR_READ_DATA.U
  val unknownCmd = cmd.fire() & !(
    newRequest | writeData | writeDataLast | readDataPoll )
  val asid =  getCmdAsid()
  val tid = getCmdTid()

  val hitAsidTid = table.exists(findAsidTid(_: TableEntry, asid, tid))
  val idxAsidTid = table.indexWhere(findAsidTid(_: TableEntry, asid, tid))

  // Temporary pass-through
  io.backend.rocc.cmd.valid := io.xfiles.cmd.valid & unknownCmd & hitAsidTid
  io.backend.rocc.cmd.bits := io.xfiles.cmd.bits
  io.xfiles.cmd.ready := io.backend.rocc.cmd.ready
  io.backend.status := io.status
  io.probes_backend := io.backend.probes_backend

  // [TODO] What interrupts can be generated here?
  io.probes.interrupt := false.B

  io.xfiles.busy := io.backend.rocc.busy

  // memory connections
  io.xfiles.mem <> io.backend.rocc.mem

  // resp
  val resp_d = Reg(Valid(new RespBundle))
  io.xfiles.resp.valid := io.backend.rocc.resp.valid | resp_d.valid
  io.xfiles.resp.bits := io.backend.rocc.resp.bits
  io.backend.rocc.resp.ready := io.xfiles.resp.ready

  resp_d.valid := cmd.fire()
  resp_d.bits.rocc.rd := cmd.bits.inst.rd

  when (resp_d.valid) {
    io.xfiles.resp.bits.rd := resp_d.bits.rocc.rd
    io.xfiles.resp.bits.data := resp_d.bits.rocc.data
  }

  // Queue connections
  (0 until numEntries).map(i => {
    val hitNew = newRequest & hasFree & (idxFree === i.U)
    val hitOld = (writeData|writeDataLast) & hitAsidTid & idxAsidTid===i.U
    val enq = (hitNew | hitOld) & queueInput(i).enq.ready
    val deq = io.backend.xfQueue.in.ready & io.backend.xfQueue.tidxIn === i.U
    queueInput(i).enq.valid := enq
    queueInput(i).enq.bits.rs1 := cmd.bits.rs1
    queueInput(i).enq.bits.rs2 := cmd.bits.rs2
    queueInput(i).enq.bits.funct := cmd.bits.inst.funct

    queueInput(i).deq.ready := deq
  })
  io.backend.xfQueue.in.bits := queueInput(io.backend.xfQueue.tidxIn).deq.bits
  io.backend.xfQueue.in.valid := queueInput(io.backend.xfQueue.tidxIn).deq.valid

  (0 until numEntries).map(i => {
    val enq = io.backend.xfQueue.out.valid & io.backend.xfQueue.tidxOut===i.U
    val deq = readDataPoll & hitAsidTid & (idxAsidTid === i.U)
    queueOutput(i).enq.valid := enq
    queueOutput(i).enq.bits := io.backend.xfQueue.out.bits

    queueOutput(i).deq.ready := deq
  })
  io.backend.xfQueue.out.ready := !queueOutput(io.backend.xfQueue.tidxOut).almostFull

  // Hook up the arbiter to the table
  (0 until numEntries).map(i => {
    val flags = table(i.U).flags
    arbiter.in(i).valid := flags.valid & !(flags.input | flags.output)
  })
  io.backend.xfReq.tidx.bits := arbiter.chosen
  io.backend.xfReq.tidx.valid := arbiter.out.valid
  arbiter.out.ready := io.backend.xfReq.tidx.ready
  // Transaction goes invalid whenever it gets scheduled on the
  // backend
  when (arbiter.out.fire()) {
    table(arbiter.chosen).flags.valid := false.B
  }

  // Deal with response on the xfResp line
  when (io.backend.xfResp.tidx.fire()) {
    val tidx = io.backend.xfResp.tidx.bits
    val flags = table(tidx).flags
    flags.valid := flags.valid | io.backend.xfResp.flags.valid
    flags.done := flags.done | io.backend.xfResp.flags.done
    flags.input := flags.input | io.backend.xfResp.flags.input
    flags.output := flags.output | io.backend.xfResp.flags.output
  }

  // State updates. These are structured with the error response first
  // followed by a check on the correct condition, table updates (if
  // applicable), and the correct response.
  when (newRequest) {
    val queue = queueInput(idxFree)
    genResp(resp_d.bits.rocc.data, resp_TID, (-err_XFILES_TTABLEFULL).S(tidWidth.W))
    printfInfo("newRequest: idxFree 0x%x, ttable_entries 0x%x\n", idxFree,
      io.status.ttable_entries)
    when (hasFree & queue.enq.ready) {
      genResp(resp_d.bits.rocc.data, resp_TID, tid)
      table(idxFree).reserve(asid, tid)
    }
  }

  val entry = table(idxAsidTid)
  when (writeData | writeDataLast) {
    val queue = queueInput(idxAsidTid)
    genResp(resp_d.bits.rocc.data, resp_QUEUE_ERR, tid)
    when (queue.enq.ready) {
      genResp(resp_d.bits.rocc.data, resp_OK, tid)
      entry.flags.input := false.B
    }
  }

  val finished = ( entry.flags.done &&
    queueOutput(idxAsidTid).count === 1.U &&
    !queueOutput(idxAsidTid).enq.fire() )
  when (readDataPoll) {
    // val queueOut = queueOutput(idxAsidTid)
    genResp(resp_d.bits.rocc.data, resp_NOT_DONE, tid)
    when (queueOutput(idxAsidTid).deq.fire()) {
      genResp(resp_d.bits.rocc.data, resp_OK, tid, queueOutput(idxAsidTid).deq.bits)
      entry.flags.output := false.B
      when (finished) { entry.reset() }
    }
  }

  when (unknownCmd) {
    genResp(resp_d.bits.rocc.data, resp_OK, (-err_XFILES_INVALIDTID).S(tidWidth.W))
    when (hitAsidTid) {
      genResp(resp_d.bits.rocc.data, resp_OK, tid)
    }
  }

  when (reset) { (0 until numEntries).map(i => { table(i).reset() })}

  //------------------------------------ Printfs, asserts
  val error = Reg(Bool(), init = false.B)

  // Check for too many reads without a response
  val readDataPollCount = Reg(init = 0.U(32.W))

  when (newRequest)    { printfInfo("newRequest(ASID 0x%x, TID 0x%x)\n", asid, tid)    }
  when (unknownCmd)    { printfInfo("unknownCmd(ASID 0x%x, TID 0x%x)\n", asid, tid)    }
  when (writeData)     { printfInfo("writeData(ASID 0x%x, TID 0x%x)\n", asid, tid)     }
  when (writeDataLast) { printfInfo("writeDataLast(ASID 0x%x, TID 0x%x)\n", asid, tid) }
  when (readDataPoll)  { printfInfo("readDataPoll(ASID 0x%x, TID 0x%x)\n", asid, tid)
    readDataPollCount := readDataPollCount + 1.U
    // error := readDataPollCount > 512.U
    when (queueOutput(idxAsidTid).deq.fire() & finished) {
      printfInfo("T0d%d is done via queue, evicting...\n", idxAsidTid)
    }
  }

  when (arbiter.out.fire()) {
    val tidx = arbiter.chosen
    printfInfo("Arbiter scheduled tidx 0d%d (ASID:0x%x/TID:0x%x)\n",
      tidx, table(tidx).asid, table(tidx).tid)
  }

  when (io.xfiles.resp.fire()) {
    printfInfo("XF TTable response to R0d%d with data 0x%x\n",
      io.xfiles.resp.bits.rd, io.xfiles.resp.bits.data)
  }

  (0 until numEntries).map(i => {
    // Input Queue
    assert(!(queueInput(i).enq.valid & !queueInput(i).enq.ready),
      printfSigil ++ "Input queue overflowed\n")
    when (queueInput(i).enq.fire()) {
      printfInfo("queueIn[%d] enq [f:0x%x, rs1:0x%x, rs2:0x%x], #:0d%d\n",
        i.U, queueInput(i).enq.bits.funct, queueInput(i).enq.bits.rs1,
        queueInput(i).enq.bits.rs2, queueInput(i).count) }
    when (queueInput(i).deq.fire()) {
      printfInfo("queueIn[%d] deq [f:0x%x, rs1:0x%x, rs2:0x%x], #:0d%d\n",
        i.U, queueInput(i).deq.bits.funct, queueInput(i).deq.bits.rs1,
        queueInput(i).deq.bits.rs2, queueInput(i).count) }
    when (queueOutput(i).enq.fire()) {
      printfInfo("queueOut[%d] enq [data:0x%x], #:0d%d\n",
        i.U, queueOutput(i).enq.bits, queueOutput(i).count) }
    when (queueOutput(i).deq.fire()) {
      printfInfo("queueOut[%d] deq [data:0x%x], #:0d%d\n",
        i.U, queueOutput(i).deq.bits, queueOutput(i).count) }
  })

  when (RegNext(newRequest | writeData | writeDataLast | readDataPoll |
    io.backend.xfResp.tidx.fire())) {
    info(table, "xfttable,") }

  // Explicit assertions
  assert(!(resp_d.valid & io.backend.rocc.resp.valid),
    printfSigil ++ "newRequest resp just aliased backend resp")
  assert(!((writeData | writeDataLast) & !hitAsidTid),
    printfSigil ++ "writeData or writeDataLast without TTable ASID/TID hit")
  assert(!(error),
    printfSigil ++ "error asserted")
}
