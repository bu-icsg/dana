// See LICENSE.IBM for license details.

package xfiles

import chisel3._
import chisel3.util._
import config._

object Interrupts {
  val privilege = 0x00
}

object CSRs {
  val exception    = 0x100
  val ttable_size  = 0x101
  val asid         = 0x102 // saved
  val tid          = 0x103
  val debug        = 0x104

  val xfid         = 0xd00 // read-only
  val xfid_current = 0xd01
}

class XFStatus(implicit p: Parameters) extends XFilesBundle()(p) {
  val ttable_entries = UInt(log2Up(transactionTableNumEntries + 1).W)
  val exception = UInt(xLen.W)
  val asid = UInt(asidWidth.W)
  val tid = UInt(tidWidth.W)
  val asidValid = Bool()
  val debug = Bool()
}

class CSRFileIO(implicit p: Parameters) extends XFilesBundle()(p) {
  val action = Input(new Bundle {
    val newRequest = Bool()})
  val addr = Input(UInt(12.W))
  val cmd = Input(Bool())
  val prv = Input(UInt(rocket.PRV.SZ.W))
  val rdata = Output(UInt(xLen.W))
  val wdata = Input(UInt(xLen.W))
  val status = Output(new XFStatus)
}

abstract class CSRFile(implicit p: Parameters) extends XFilesModule()(p)
  with HasXFilesBackend {
  def backendId: UInt
  require(backendId.getWidth == 48, "XF backendId must be 48-bit")
  def backend_csrs: collection.immutable.ListMap[Int, Bits]
  def backend_writes: Unit

  lazy val io = IO(new CSRFileIO)
  override val printfSigil = "xfiles.CSRFile: "

  val reg_exception = Reg(init = 0.U(xLen.W))
  val reg_ttable_size = Reg(init = transactionTableNumEntries.U(log2Up(transactionTableNumEntries + 1).W))
  val reg_asid = Reg(UInt(asidWidth.W), init = ~(0.U(asidWidth.W)))
  val reg_tid = Reg(UInt(tidWidth.W))
  val reg_debug = Reg(init = false.B)

  lazy val read_mapping = collection.mutable.LinkedHashMap[Int, Bits] (
    CSRs.exception    -> reg_exception,
    CSRs.ttable_size  -> reg_ttable_size,
    CSRs.xfid         -> transactionTableNumEntries.U ##
                         buildBackend.info.U((xLen - 16).W),
    CSRs.xfid_current -> reg_ttable_size.pad(16)(15, 0) ## backendId,
    CSRs.asid         -> reg_asid,
    CSRs.tid          -> reg_tid,
    CSRs.debug        -> reg_debug )
  read_mapping ++= backend_csrs

  val addrIs = read_mapping map { case(k, v) => k -> (io.addr === k.U) }
  val addr_valid = addrIs.values.reduce(_||_)
  io.rdata := Mux1H(for ((k, v) <- read_mapping) yield addrIs(k) -> v)

  val (read_only, csr_addr_prv)  = (io.addr(11, 10), io.addr(9, 8))
  val prv_ok = (io.prv >= csr_addr_prv) || reg_debug
  val wen = io.cmd && addr_valid && prv_ok && !read_only
  when (wen) {
    val d = io.wdata
    when(addrIs(CSRs.exception))   { reg_exception   := d }
    when(addrIs(CSRs.ttable_size)) { reg_ttable_size := d }
    when(addrIs(CSRs.asid))        { reg_asid        := d }
    when(addrIs(CSRs.tid))         { reg_tid         := d }
    when(addrIs(CSRs.debug))       { reg_debug       := d }
    backend_writes
    printfInfo("Writing to CSR[0x%x] value 0x%x\n", io.addr, d) }

  when (io.cmd && addr_valid && !prv_ok) {
    reg_exception := Interrupts.privilege.U
    printfInfo("Privilege exception (prv: 0x%x) on write to CSR[0x%x]\n",
      io.prv, io.addr) }

  when (io.action.newRequest) { reg_tid := reg_tid + 1.U }

  io.status.ttable_entries := reg_ttable_size
  io.status.exception      := reg_exception
  io.status.asid           := reg_asid
  io.status.tid            := reg_tid
  io.status.asidValid      := reg_asid =/= ~(0.U(asidWidth.W))
  io.status.debug          := reg_debug
}
