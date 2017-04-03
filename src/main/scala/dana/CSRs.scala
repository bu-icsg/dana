// See LICENSE.IBM for license details.

package dana

import chisel3._
import chisel3.util._
import cde._

object Causes {
  val unknown       = 0x80
  // ASID--NNID Table Walker
  val no_antp       = 0x90
  val invalid_asid  = 0x91
  val invalid_nnid  = 0x92
  val null_read     = 0x93
  val zero_size     = 0x94
  val invalid_epb   = 0x95
  val misaligned    = 0x96
  // Cache
  val fence_context = 0xa0
}

object CSRs {
  // User read/write
  val fence        = 0x080 // saved
  val learn_rate   = 0x081
  val weight_decay = 0x082

  // Supervisor read/write
  val pe_size      = 0x180
  val cache_size   = 0x181
  val pe_cooldown  = 0x182
  val antp         = 0x183
  val num_asids    = 0x184
  val pe_governor  = 0x185

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
  val fence = new CSRs.FenceCSR
  val learn_rate = UInt(elementWidth.W)
  val weight_decay = UInt(elementWidth.W)
}

class DanaProbes(implicit p: Parameters) extends xfiles.BackendProbes()(p)
    with DanaParameters {
  val cache = new Bundle {
    val fence_done = Bool()
    val fence_asid = UInt(asidWidth.W)
  }
}

class CSRFileIO(implicit p: Parameters) extends xfiles.CSRFileIO()(p) {
  override val status = Output(new DanaStatus)
  override val probes_backend = Input(new DanaProbes)
}

class CSRFile(implicit p: Parameters) extends xfiles.CSRFile()(p) with DanaParameters {
  override lazy val io = new dana.CSRFileIO

  lazy val reg_pe_size = Reg(init = p(PeTableNumEntries).U(log2Up(p(PeTableNumEntries)+1).W))
  lazy val reg_cache_size = Reg(init = p(CacheNumEntries).U(log2Up(p(CacheNumEntries)+1).W))
  lazy val reg_pe_cooldown = Reg(init = 0.U(p(PeCooldownWidth).W))
  lazy val reg_antp = Reg(init = ~(0.U(xLen.W)))
  lazy val reg_num_asids = Reg(init = 0.U(p(xfiles.AsidWidth).W))
  lazy val reg_pe_governor = Reg(init = 0.U(1.W))
  lazy val reg_fence = Reg(new CSRs.FenceCSR)
  lazy val reg_learn_rate =   Reg(init = "b00000000000000000010011001100110".U) // 0.7
  lazy val reg_weight_decay = Reg(init = "b00000000000000000000000000000000".U) // 1.0

  def backendId = ( p(ElementsPerBlock).U ## reg_pe_size.pad(6) ##
    reg_cache_size.pad(4) ).pad(48)

  def backend_csrs = collection.immutable.ListMap[Int, Data](
    CSRs.pe_size      -> reg_pe_size,
    CSRs.cache_size   -> reg_cache_size,
    CSRs.pe_cooldown  -> reg_pe_cooldown,
    CSRs.antp         -> reg_antp,
    CSRs.num_asids    -> reg_num_asids,
    CSRs.pe_governor  -> reg_pe_governor,
    CSRs.fence        -> reg_fence,
    CSRs.learn_rate   -> reg_learn_rate,
    CSRs.weight_decay -> reg_weight_decay
  )

  def backend_writes = {
    val d = io.wdata
    when (addrIs(CSRs.pe_size))     { reg_pe_size          := d }
    when (addrIs(CSRs.cache_size))  { reg_cache_size       := d }
    when (addrIs(CSRs.pe_cooldown)) { reg_pe_cooldown      := d }
    when (addrIs(CSRs.antp))        { reg_antp             := d }
    when (addrIs(CSRs.num_asids))   { reg_num_asids        := d }
    when (addrIs(CSRs.pe_governor)) { reg_pe_governor      := d }
    when (addrIs(CSRs.fence)) {
      when (!reg_fence.valid) {
        reg_fence.valid      := true.B
        reg_fence.fence_type := d(p(NnidWidth))
        reg_fence.nnid       := d(p(NnidWidth) - 1, 0)
        io.rdata := 0.U
      } .otherwise {
        io.rdata := 1.U }}
    when (addrIs(CSRs.learn_rate))  { reg_learn_rate       := d }
    when (addrIs(CSRs.weight_decay)){ reg_weight_decay     := d }
  }

  io.status.pes_active     := reg_pe_size
  io.status.caches_active  := reg_cache_size
  io.status.pe_cooldown    := reg_pe_cooldown
  io.status.antp           := reg_antp
  io.status.num_asids      := reg_num_asids
  io.status.pe_governor    := reg_pe_governor
  io.status.fence          := reg_fence
  io.status.stall_response := ( (reg_fence.valid && reg_fence.fence_type) ||
    (wen && addrIs(CSRs.fence) && io.wdata(p(NnidWidth))) )
  io.status.learn_rate     := reg_learn_rate
  io.status.weight_decay   := reg_weight_decay

  when (io.probes_backend.cache.fence_done) {
    val p = io.probes_backend
    when (p.cache.fence_asid === reg_asid) {
      reg_fence.valid := false.B
    } .otherwise {
      reg_mip := reg_mip | 1.U
      reg_cause := Causes.fence_context.U }
    printfInfo("Saw fence done for 0x%x\n", p.cache.fence_asid)
  }

  when (reset) { reg_fence.valid := false.B }
}
