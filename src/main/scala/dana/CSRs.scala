// See LICENSE.IBM for license details.

package dana

import chisel3._
import chisel3.util._
import config._

object Interrupts {
  val fence = 0x10
}

object CSRs {
  val pe_size = 0x10
  val cache_size = 0x11
  val pe_cooldown = 0x12
  val antp = 0x13
  val num_asids = 0x14
  val pe_governor = 0x15
  val fence = 0x16 // saved

  class FenceCSR(implicit p: Parameters) extends DanaBundle()(p) {
    val valid = Bool()
    val fence_type = Bool()
    val nnid = UInt(nnidWidth.W)
  }
}

object PeGovernor {
  val cooldown = 0x0
  val backoff_linear = 0x1
}

class DanaStatus(implicit p: Parameters) extends xfiles.XFStatus()(p)
    with DanaParameters {
  val pes_active = UInt(log2Up(peTableNumEntries + 1).W)
  val caches_active = UInt(log2Up(cacheNumEntries + 1).W)
  val pe_cooldown = UInt(peCooldownWidth.W)
  val antp = UInt(xLen.W)
  val num_asids = UInt(p(xfiles.AsidWidth).W)
  val pe_governor = UInt(1.W)
  val fence = UInt((nnidWidth + 2).W)
}

class CSRFileIO(implicit p: Parameters) extends xfiles.CSRFileIO()(p) {
  override val status = Output(new DanaStatus)
  val fence_done = Input(Bool())
  val fence_asid = Input(UInt(asidWidth.W))
}

class CSRFile(implicit p: Parameters) extends xfiles.CSRFile()(p) with DanaParameters {
  override lazy val io = IO(new dana.CSRFileIO)

  lazy val reg_pe_size = Reg(init = p(PeTableNumEntries).U(log2Up(p(PeTableNumEntries)+1).W))
  lazy val reg_cache_size = Reg(init = p(CacheNumEntries).U(log2Up(p(CacheNumEntries)+1).W))
  lazy val reg_pe_cooldown = Reg(init = 0.U(p(PeCooldownWidth).W))
  lazy val reg_antp = Reg(init = ~(0.U(xLen.W)))
  lazy val reg_num_asids = Reg(init = 0.U(p(xfiles.AsidWidth).W))
  lazy val reg_pe_governor = Reg(init = 0.U(1.W))
  lazy val reg_fence = Reg(init = 0.U((nnidWidth + 2).W))

  def backendId = ( p(ElementsPerBlock).U ## reg_pe_size.pad(6) ##
    reg_cache_size.pad(4) ).pad(48)

  def backend_csrs = collection.immutable.ListMap(
    CSRs.pe_size     -> reg_pe_size,
    CSRs.cache_size  -> reg_cache_size,
    CSRs.pe_cooldown -> reg_pe_cooldown,
    CSRs.antp        -> reg_antp,
    CSRs.num_asids   -> reg_num_asids,
    CSRs.pe_governor -> reg_pe_governor,
    CSRs.fence       -> reg_fence
  )

  def backend_writes = {
    val d = io.wdata
    when (addrIs(CSRs.pe_size))     { reg_pe_size     := d }
    when (addrIs(CSRs.cache_size))  { reg_cache_size  := d }
    when (addrIs(CSRs.pe_cooldown)) { reg_pe_cooldown := d }
    when (addrIs(CSRs.antp))        { reg_antp        := d }
    when (addrIs(CSRs.num_asids))   { reg_num_asids   := d }
    when (addrIs(CSRs.pe_governor)) { reg_pe_governor := d }
    when (addrIs(CSRs.fence))       { reg_fence       := 1.U ## d(nnidWidth + 1, 0) }
  }

  io.status.pes_active    := reg_pe_size
  io.status.caches_active := reg_cache_size
  io.status.pe_cooldown   := reg_pe_cooldown
  io.status.antp          := reg_antp
  io.status.num_asids     := reg_num_asids
  io.status.pe_governor   := reg_pe_governor
  io.status.fence         := reg_fence

  when (io.fence_done) {
    when (io.fence_asid === reg_asid) { reg_fence := 0.U                    }
      .otherwise                      { reg_exception := Interrupts.fence.U }
  }
}
