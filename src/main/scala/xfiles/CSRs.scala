// See LICENSE.IBM for license details.

package xfiles

import chisel3._
import chisel3.util._
import cde._

object Causes {
  val xfiles_generic = 0x0
  val backend_generic = 0x1
  val illegal_instruction = 0x2
}

object CSRs {
  // Supervisor read/write
  val cause        = 0x100
  val ttable_size  = 0x101
  val asid         = 0x102 // saved
  val tid          = 0x103

  // [TODO] Register with interrupt pending bits for all transactions
  val tx_ip        = 0x160

  // Supervisor read-only
  val xfid         = 0xd00
  val xfid_current = 0xd01

  // [TODO] Base address of per-transaction information read-only CSR
  val tx_info      = 0xd20

  // User read-only
  val u_xfid       = 0xC00

  // N = no action, R = read, W = write
  val cmdSize = 2
  val N = 0.U(cmdSize.W)
  val R = 1.U(cmdSize.W)
  val W = 2.U(cmdSize.W)
}

class XFMIP extends Bundle {
  val backend = Bool()
  val xf = Bool()
  val illegal_instruction = Bool()
}

class XFStatus(implicit p: Parameters) extends XFilesBundle()(p) {
  val ttable_entries = UInt(log2Up(transactionTableNumEntries + 1).W)
  val asid = UInt(asidWidth.W)
  val tid = UInt(tidWidth.W)
  val asidValid = Bool()
  val stall_response = Bool()
}

class XFProbes(implicit p: Parameters) extends XFilesBundle()(p) {
  val newRequest = Bool()
  val interrupt = Bool()
  val cause = UInt(xLen.W)
  // val ttable = Vec(transactionTableNumEntries, new XFilesBundle()(p) with TableRVDIO)
}

class BackendProbes(implicit p: Parameters) extends XFilesBundle()(p) {
  val interrupt = Bool()
  val cause = UInt(xLen.W)
}

class CSRFileIO(implicit p: Parameters) extends XFilesBundle()(p) {
  val addr = Input(UInt(12.W))
  val cmd = Input(UInt(CSRs.cmdSize.W))
  val prv = Input(UInt(rocket.PRV.SZ.W))
  val rdata = Output(UInt(xLen.W))
  val wdata = Input(UInt(xLen.W))
  val interrupt = Output(Bool())
  val status = Output(new XFStatus)
  val probes = Input(new XFProbes)
  val probes_backend = Input(new BackendProbes)
}

abstract class CSRFile(implicit p: Parameters) extends XFilesModule()(p)
  with HasXFilesBackend {
  def backendId: UInt
  require(backendId.getWidth == 48, "XF backendId must be 48-bit")
  def backend_csrs: collection.immutable.ListMap[Int, Data]
  def backend_writes: Unit

  lazy val io = new CSRFileIO
  override val printfSigil = "xfiles.CSRFile: "

  val reg_mip = Reg(init = 0.U(new XFMIP().getWidth.W))
  val reg_cause = Reg(init = 0.U(xLen.W))
  val reg_ttable_size = Reg(init = transactionTableNumEntries.U(log2Up(transactionTableNumEntries + 1).W))
  val reg_asid = Reg(UInt(asidWidth.W), init = ~(0.U(asidWidth.W)))
  val reg_tid = Reg(UInt(tidWidth.W))

  val reg_tx_ip = Reg(init = Vec(transactionTableNumEntries, Bool()).fromBits(0.U))
  require(transactionTableNumEntries <= xLen, "TTable entries must be less than XLen")

  val xfid = transactionTableNumEntries.U ## buildBackend.info.U((xLen - 16).W)
  val xfid_current = reg_ttable_size.pad(16)(15, 0) ## backendId

  lazy val read_mapping = collection.mutable.LinkedHashMap[Int, Data] (
    CSRs.cause        -> reg_cause,
    CSRs.ttable_size  -> reg_ttable_size,
    CSRs.xfid         -> xfid,
    CSRs.xfid_current -> xfid_current,
    CSRs.asid         -> reg_asid,
    CSRs.tid          -> reg_tid,
    CSRs.tx_ip        -> reg_tx_ip.asUInt,
    // [TODO] Properly read from any of the transaction status fields
    CSRs.tx_info      -> reg_tx_ip(0),
    CSRs.u_xfid       -> xfid_current
  )
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
    when(addrIs(CSRs.cause))       { reg_cause       := d }
    when(addrIs(CSRs.ttable_size)) { reg_ttable_size := d }
    when(addrIs(CSRs.asid))        { reg_asid        := d }
    when(addrIs(CSRs.tid))         { reg_tid         := d }
    backend_writes
    printfInfo("Write CSR[0x%x] value 0x%x\n", io.addr, d) }
  when (ren) { printfInfo("Read CSR[0x%x] value 0x%x\n", io.addr, io.rdata) }

  val cause = Mux(io.probes.interrupt, Causes.xfiles_generic.U,
    Mux(io.probes_backend.interrupt, Causes.backend_generic.U,
      Causes.illegal_instruction.U))
  val illegal_instruction = (ren && (!addr_valid || !prv_ok))
  val exception = (wen && read_only) || illegal_instruction || (
    io.probes.interrupt || io.probes_backend.interrupt )

  when (exception) {
    reg_cause := reg_cause | cause
    reg_mip := reg_mip | (io.probes_backend.interrupt ## io.probes.interrupt ##
      illegal_instruction)

    printfInfo("Exception with cause 0x%x, reg_cause <- 0x%x\n", cause,
      reg_cause | cause)
  }

  when (io.probes.newRequest) { reg_tid := reg_tid + 1.U }

  io.interrupt             := reg_mip.orR
  io.status.ttable_entries := reg_ttable_size
  io.status.asid           := reg_asid
  io.status.tid            := reg_tid
  io.status.asidValid      := reg_asid =/= ~(0.U(asidWidth.W))
  io.status.stall_response := false.B
}
