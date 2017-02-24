// See LICENSE for license details.

package dana

import chisel3._
import chisel3.util._
import config._

object CSRs {
  val pe_size = 0x10
  val cache_size = 0x11
  val pe_cooldown = 0x12
  val antp = 0x13
  val num_asids = 0x14
  val pe_governor = 0x15
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
}

class CSRFileIO(implicit p: Parameters) extends xfiles.CSRFileIO()(p) {
  override val status = Output(new DanaStatus)
}

class CSRFile(implicit p: Parameters) extends xfiles.CSRFile()(p) with DanaParameters {
  override lazy val io = IO(new dana.CSRFileIO)

  lazy val reg_pe_size = Reg(init = p(PeTableNumEntries).U(log2Up(p(PeTableNumEntries)+1).W))
  lazy val reg_cache_size = Reg(init = p(CacheNumEntries).U(log2Up(p(CacheNumEntries)+1).W))
  lazy val reg_pe_cooldown = Reg(init = 0.U(p(PeCooldownWidth).W))
  lazy val reg_antp = Reg(init = ~(0.U(xLen.W)))
  lazy val reg_num_asids = Reg(init = 0.U(p(xfiles.AsidWidth).W))
  lazy val reg_pe_governor = Reg(init = 0.U(1.W))

  def backendId = ( p(ElementsPerBlock).U ## reg_pe_size.pad(6) ##
    reg_cache_size.pad(4) ).pad(48)

  def backend_csrs = collection.immutable.ListMap(
    CSRs.pe_size     -> reg_pe_size,
    CSRs.cache_size  -> reg_cache_size,
    CSRs.pe_cooldown -> reg_pe_cooldown,
    CSRs.antp        -> reg_antp,
    CSRs.num_asids   -> reg_num_asids,
    CSRs.pe_governor -> reg_pe_governor
  )

  def backend_writes = {
    when (decoded_addr(CSRs.pe_size))     { reg_pe_size := io.wdata     }
    when (decoded_addr(CSRs.cache_size))  { reg_cache_size := io.wdata  }
    when (decoded_addr(CSRs.pe_cooldown)) { reg_pe_cooldown := io.wdata }
    when (decoded_addr(CSRs.antp))        { reg_antp := io.wdata        }
    when (decoded_addr(CSRs.num_asids))   { reg_num_asids := io.wdata   }
    when (decoded_addr(CSRs.pe_governor)) { reg_pe_governor := io.wdata }
  }

  io.status.pes_active := reg_pe_size
  io.status.caches_active := reg_cache_size
  io.status.pe_cooldown := reg_pe_cooldown
  io.status.antp := reg_antp
  io.status.num_asids := reg_num_asids
  io.status.pe_governor := reg_pe_governor
}
