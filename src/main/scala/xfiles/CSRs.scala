// See LICENSE.IBM for license details.

package xfiles

import chisel3._
import chisel3.util._
import config._

object Causes {
  val illegal_instruction = 0x2
}

object CSRs {
  val cause        = 0x100
  val ttable_size  = 0x101
  val asid         = 0x102 // saved
  val tid          = 0x103

  val xfid         = 0xd00 // read-only
  val xfid_current = 0xd01

  val size = 2
  val N = 0.U(size.W)
  val R = 1.U(size.W)
  val W = 2.U(size.W)
}

class XFStatus(implicit p: Parameters) extends XFilesBundle()(p) {
  val ttable_entries = UInt(log2Up(transactionTableNumEntries + 1).W)
  val asid = UInt(asidWidth.W)
  val tid = UInt(tidWidth.W)
  val asidValid = Bool()
}

class XFProbes(implicit p: Parameters) extends XFilesBundle()(p) {
  val interrupt = Bool()
  val cause = UInt(xLen.W)
}

class CSRFileIO(implicit p: Parameters) extends XFilesBundle()(p) {
  val action = Input(new Bundle {
    val newRequest = Bool()})
  val addr = Input(UInt(12.W))
  val cmd = Input(Bool())
  val prv = Input(UInt(rocket.PRV.SZ.W))
  val rdata = Output(UInt(xLen.W))
  val wdata = Input(UInt(xLen.W))
  val interrupt = Output(Bool())
  val status = Output(new XFStatus)
  val probes = Input(new XFProbes)
}

abstract class CSRFile(implicit p: Parameters) extends XFilesModule()(p)
  with HasXFilesBackend {
  def backendId: UInt
  require(backendId.getWidth == 48, "XF backendId must be 48-bit")
  def backend_csrs: collection.immutable.ListMap[Int, Bits]
  def backend_writes: Unit

  lazy val io = IO(new CSRFileIO)
  override val printfSigil = "xfiles.CSRFile: "

  val reg_cause = Reg(init = 0.U(xLen.W))
  val reg_ttable_size = Reg(init = transactionTableNumEntries.U(log2Up(transactionTableNumEntries + 1).W))
  val reg_asid = Reg(UInt(asidWidth.W), init = ~(0.U(asidWidth.W)))
  val reg_tid = Reg(UInt(tidWidth.W))

  lazy val read_mapping = collection.mutable.LinkedHashMap[Int, Bits] (
    CSRs.cause        -> reg_cause,
    CSRs.ttable_size  -> reg_ttable_size,
    CSRs.xfid         -> transactionTableNumEntries.U ##
                         buildBackend.info.U((xLen - 16).W),
    CSRs.xfid_current -> reg_ttable_size.pad(16)(15, 0) ## backendId,
    CSRs.asid         -> reg_asid,
    CSRs.tid          -> reg_tid )
  read_mapping ++= backend_csrs

  val addrIs = read_mapping map { case(k, v) => k -> (io.addr === k.U) }
  val addr_valid = addrIs.values.reduce(_ || _)
  io.rdata := Mux1H(read_mapping map {case(k, v) => addrIs(k) -> v})

  val (read_only, csr_addr_prv)  = (io.addr(11, 10).andR, io.addr(9, 8))
  val prv_ok = (io.prv >= csr_addr_prv)
  val ren = io.cmd === CSRs.R
  val wen = io.cmd === CSRs.W && addr_valid && prv_ok && !read_only
  when (wen) {
    val d = io.wdata
    when(addrIs(CSRs.cause))       { reg_cause   := d }
    when(addrIs(CSRs.ttable_size)) { reg_ttable_size := d }
    when(addrIs(CSRs.asid))        { reg_asid        := d }
    when(addrIs(CSRs.tid))         { reg_tid         := d }
    backend_writes
    printfInfo("Writing to CSR[0x%x] value 0x%x\n", io.addr, d) }

  val cause = Mux(io.status.interrupt, io.status.cause, Causes.illegal_instruction.U)
  val exception = ((wen && read_only) || (ren && (!addr_valid || !prv_ok)) ||
    io.status.interrupt)

  when (exception) { reg_cause := cause
    printfInfo("Exception with cause 0x%x\n", cause)
  }

  when (io.action.newRequest) { reg_tid := reg_tid + 1.U }

  io.interrupt             := reg_cause.orR
  io.status.ttable_entries := reg_ttable_size
  io.status.asid           := reg_asid
  io.status.tid            := reg_tid
  io.status.asidValid      := reg_asid =/= ~(0.U(asidWidth.W))
}
