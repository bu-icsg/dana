// See LICENSE for license details.

package dana

import Chisel._

import rocket.{RoCCCommand, RoCCResponse, HellaCacheReq, HellaCacheIO, MStatus}
import uncore.{CacheName, ClientUncachedTileLinkIO}
import uncore.constants.MemoryOpConstants._
import cde.{Parameters}
import xfiles.{InterruptBundle, XFilesSupervisorRequests}

class ANTWXFilesInterface(implicit p: Parameters) extends DanaBundle()(p) {
  val rocc = new Bundle {
    val cmd = Decoupled(new RoCCCommand).flip
    val resp = Decoupled(new RoCCResponse)
    val status = new MStatus().asInput
  }
  val autl = new ClientUncachedTileLinkIO
  val dcache = new Bundle {
    val mem = new HellaCacheIO()(p.alterPartial({ case CacheName => "L1D" }))
  }
  val interrupt = Valid(new InterruptBundle)
}

class AsidNnidTableWalkerInterface(implicit p: Parameters) extends DanaBundle()(p) {
  val cache = (new CacheMemInterface).flip
  val xfiles = new ANTWXFilesInterface
}

class ConfigRobEntry(implicit p: Parameters) extends DanaBundle()(p) {
  val valid = UInt(width = bitsPerBlock / xLen)
  val cacheAddr = UInt(width = log2Up(cacheDataSize * 8 / bitsPerBlock))
  val data = Vec.fill(bitsPerBlock / xLen){UInt(width = xLen)}
}

class HellaCacheReqWithCore(implicit p: Parameters) extends DanaBundle()(p) {
  val req = new HellaCacheReq()(p)
}

class antp(implicit p: Parameters) extends DanaBundle()(p) {
  val valid = Bool()
  val antp = UInt(width = xLen)
  val size = UInt(width = xLen)
}

class AsidNnidTableWalker(implicit p: Parameters) extends DanaModule()(p)
  with XFilesSupervisorRequests {
  val io = new AsidNnidTableWalkerInterface
  val antpReg = Reg(new antp)

  val (s_IDLE :: s_CHECK_ASID :: s_GET_VALID_NNIDS :: s_GET_NN_POINTER ::
    s_GET_NN_SIZE :: s_GET_NN_EPB :: s_GET_CONFIG_POINTER :: s_GET_NN_CONFIG ::
    s_GET_NN_CONFIG_CLEANUP :: s_INTERRUPT :: s_ERROR :: Nil) = Enum(UInt(), 11)

  val state = Reg(UInt(), init = s_IDLE)

  // State used to read a configuration
  val configReqCount = Reg(UInt(width = log2Up(cacheDataSize * 8 / xLen)))
  val configBufSize = bitsPerBlock / xLen
  val configWbCount = Reg(UInt(width = log2Up(cacheDataSize * 8 / bitsPerBlock)))

  // Queue for cache requests. At maximum, every entry in the
  // Configuration Cache. could have an outstanding request, so we
  // size this queue accordingly. The head of the queue can then be
  // operated on directly or the data in the head can be dequeued into
  // a set of "current" registers. The latter approach is used here.
  val cacheReqQueue = Module(new Queue(new CacheMemReq, cacheNumEntries))
  cacheReqQueue.io.enq <> io.cache.req
  val cacheReqCurrent = Reg(new CacheMemReq)

  // Default values
  io.xfiles.rocc.cmd.ready := Bool(true)

  io.cache.resp.valid := Bool(false)
  io.cache.resp.bits.done := Bool(false)
  io.cache.resp.bits.data := UInt(0)
  io.cache.resp.bits.cacheIndex := UInt(0)
  io.cache.resp.bits.addr := UInt(0)

  io.xfiles.autl.acquire.valid := Bool(false)
  io.xfiles.autl.grant.ready := Bool(true)

  io.xfiles.dcache.mem.req.valid := Bool(false)
  io.xfiles.dcache.mem.req.bits.kill := Bool(false) // testing
  io.xfiles.dcache.mem.req.bits.phys := Bool(false) // testing
  io.xfiles.dcache.mem.req.bits.data := Bool(false) // testing
  io.xfiles.dcache.mem.req.bits.addr := UInt(0)
  io.xfiles.dcache.mem.req.bits.tag := UInt(0)
  io.xfiles.dcache.mem.req.bits.cmd := M_XRD
  io.xfiles.dcache.mem.req.bits.typ := MT_D
  io.xfiles.dcache.mem.invalidate_lr := Bool(false)
  def memRead(addr: UInt) {
    io.xfiles.dcache.mem.req.bits.addr := addr
    io.xfiles.dcache.mem.req.bits.tag := addr(coreDCacheReqTagBits - 1, 0) }

  val respData = io.xfiles.dcache.mem.resp.bits.data_word_bypass

  // Entries can come back in any order, so we use a Config Reorder
  // Buffer (ROB) to pack the xLen-sized responses into blocks before
  // sending them back to the configuration cache.
  val configPointer = Reg(UInt())
  val configRob = Vec.fill(antwRobEntries){ Reg(new ConfigRobEntry) }
  def feedConfigRob() {
    // Compute the response index in terms of a logical index into
    // the array that we're reading
    val respIdx = (io.xfiles.dcache.mem.resp.bits.addr - configPointer) >>
      UInt(log2Up(xLen/8))
    // Based on this response index, compute the slot and offset
    // in the Config ROB buffer
    val configRobSlot = respIdx(log2Up(antwRobEntries) +
      log2Up(configBufSize) - 1, log2Up(configBufSize))
    val configRobOffset = respIdx(log2Up(configBufSize) - 1, 0)

    // Write the data to the appropriate slot and offset in the
    // Config ROB setting the valid flags appropriately
    configRob(configRobSlot).valid := configRob(configRobSlot).valid |
      UInt(1, width = configBufSize) << configRobOffset
    val cacheAddr = respIdx >> UInt(log2Up(configBufSize))
    configRob(configRobSlot).cacheAddr := cacheAddr
    configRob(configRobSlot).data(configRobOffset) := respData

    // Check that we aren't overwriting valid data
    val overwrite = (configRob(configRobSlot).valid &
      UInt(1, width = configBufSize) << configRobOffset) =/= UInt(0)
    when (overwrite) {
      printfWarn("ANTW: overWr (old/new) addr 0x%x/0x%x, data 0x%x/0x%x\n",
        configRob(configRobSlot).cacheAddr, cacheAddr,
        configRob(configRobSlot).data(configRobOffset), respData) }
    when (overwrite & (configRob(configRobSlot).cacheAddr === cacheAddr) &
      (configRob(configRobSlot).data(configRobOffset) === respData)) {
      printfWarn("ANTW: Overwriting existing entry with the same addr/data\n") }
    assert(!(overwrite & (configRob(configRobSlot).cacheAddr =/= cacheAddr)),
      "ANTW about to overwrite a valid Config ROB entry with different addr")
    assert(!(overwrite &
      (configRob(configRobSlot).data(configRobOffset) =/= respData)),
      "ANTW about to overwrite a valid Config ROB entry with different data") }

  // RoCC requests that come in for changing the ANTP are handled
  // here. The old ASID value will be returned to the operating
  // system. In the event of an invalid ASID, a value
  // of -int_DANA_NOANTP (defined in src/main/scala/Dana.scala) is
  // returned.
  val funct = io.xfiles.rocc.cmd.bits.inst.funct
  val updateAntp = io.xfiles.rocc.status.prv.orR && funct === UInt(t_SUP_WRITE_REG)
  when (io.xfiles.rocc.cmd.fire() && updateAntp) {
    antpReg.valid := Bool(true)
    antpReg.antp := io.xfiles.rocc.cmd.bits.rs1
    antpReg.size := io.xfiles.rocc.cmd.bits.rs2
    printfInfo("ANTW changing ANTP to 0x%x with size 0x%x\n",
      io.xfiles.rocc.cmd.bits.rs1, io.xfiles.rocc.cmd.bits.rs2) }

  io.xfiles.rocc.resp.bits.rd := io.xfiles.rocc.cmd.bits.inst.rd
  io.xfiles.rocc.resp.bits.data := Mux(antpReg.valid, antpReg.antp,
    SInt(-int_DANA_NOANTP, width = xLen).toUInt)
  io.xfiles.rocc.resp.valid := io.xfiles.rocc.cmd.fire() && updateAntp

  when (io.xfiles.rocc.resp.valid) {
    printfInfo("ANTW: Responding to core R%d with data 0x%x\n",
      io.xfiles.rocc.resp.bits.rd, io.xfiles.rocc.resp.bits.data) }

  // New cache requests get entered on the queue
  when (io.cache.req.fire()) {
    printfInfo("ANTW: Enqueue mem req ASID/NNID/Idx 0x%x/0x%x/0x%x\n",
      io.cache.req.bits.asid, io.cache.req.bits.nnid,
      io.cache.req.bits.cacheIndex) }

  val interruptCode = Reg(Valid(UInt()))
  def setInterrupt(code: Int) {
    interruptCode.valid := Bool(true);
    if (code >= 0) interruptCode.bits := UInt(code)
    else interruptCode.bits := SInt(code).toUInt }
  def clearInterrupt() { interruptCode.valid := Bool(false) }

  // Many of the state updates are gated by waiting for a response.
  // This leverages a similar structure from
  // src/main/scala/ProcessingElement.scala with `reqWaitForResp`.
  // This wraps up all the logic of generating a request, waiting for
  // a response, and using a function (`cond`) to determine of things
  // are okay to proceed.
  val reqSent = Reg(Bool())
  reqSent := reqSent
  def reqWaitForResp(nextState: UInt, cond: => Bool = Bool(true),
    code: Int = int_UNKNOWN) = {
    when (!reqSent) {
      io.xfiles.dcache.mem.req.valid := Bool(true)
      reqSent := io.xfiles.dcache.mem.req.ready
    } .elsewhen (io.xfiles.dcache.mem.resp.valid) {
      io.xfiles.dcache.mem.req.valid := Bool(false)
      reqSent := Bool(false)
      state := Mux(cond, nextState, s_INTERRUPT)
      setInterrupt(code) }}

  val hasCacheRequests = cacheReqQueue.io.count > UInt(0)
  cacheReqQueue.io.deq.ready := state === s_IDLE & hasCacheRequests
  when (state === s_IDLE & hasCacheRequests) {
    // Pull data out of the cache request queue and save it in the
    // "current" buffer
    val deq = cacheReqQueue.io.deq.bits
    cacheReqCurrent := deq
    state := s_CHECK_ASID
    when (!antpReg.valid) {
      state := s_INTERRUPT
      setInterrupt(int_DANA_NOANTP)
    }
    reqSent := Bool(false)
    printfInfo("ANTW: Dequeue mem req ASID/NNID/Idx 0x%x/0x%x/0x%x\n",
      deq.asid, deq.nnid, deq.cacheIndex)
  }

  val asid = cacheReqCurrent.asid
  val nnid = cacheReqCurrent.nnid
  when (state === s_CHECK_ASID) {
    state := s_GET_VALID_NNIDS
    when (asid >= antpReg.size) {
      state := s_INTERRUPT
      setInterrupt(int_INVASID)
    }
  }

  when (state === s_GET_VALID_NNIDS) {
    val reqAddr = antpReg.antp + asid * UInt(24)
    val numValidNnids = respData(63, 32)
    memRead(reqAddr)
    reqWaitForResp(s_GET_NN_POINTER, nnid < numValidNnids, int_INVNNID)
  }

  val nnidPointer = Reg(UInt())
  nnidPointer := nnidPointer
  when (state === s_GET_NN_POINTER) {
    val reqAddr = antpReg.antp + asid * UInt(24) + UInt(8)
    nnidPointer := respData + nnid * UInt(24)
    memRead(reqAddr)
    reqWaitForResp(s_GET_NN_SIZE, respData =/= UInt(0), int_NULLREAD)
  }

  val configSize = Reg(UInt())
  configSize := configSize
  when (state === s_GET_NN_SIZE) {
    val reqAddr = nnidPointer
    memRead(reqAddr)
    configSize := respData
    reqWaitForResp(s_GET_NN_EPB, respData =/= UInt(0), int_ZEROSIZE)
  }

  when (state === s_GET_NN_EPB) {
    val reqAddr = nnidPointer + UInt(8)
    memRead(reqAddr)
    reqWaitForResp(s_GET_CONFIG_POINTER, respData === UInt(elementsPerBlock),
      int_INVEPB)
  }

  configPointer := configPointer
  when (state === s_GET_CONFIG_POINTER) {
    val reqAddr = nnidPointer + UInt(16)
    configReqCount := UInt(0)
    configWbCount := UInt(0)
    memRead(reqAddr)
    configPointer := respData
    reqWaitForResp(s_GET_NN_CONFIG)
  }

  when (state === s_GET_NN_CONFIG) {
    io.xfiles.dcache.mem.req.valid := Bool(true)
    memRead(configPointer + configReqCount * UInt(xLen / 8))
    when (io.xfiles.dcache.mem.req.fire()) {
      configReqCount := configReqCount + UInt(1)
      when (configReqCount === configSize - UInt(1)) {
        state := s_GET_NN_CONFIG_CLEANUP
      }
    }
    when (io.xfiles.dcache.mem.resp.valid) { feedConfigRob() }
  }

  when (state === s_GET_NN_CONFIG_CLEANUP) {
    when (configWbCount === (configSize >> UInt(log2Up(configBufSize)))) {
      state := s_IDLE
      printfInfo("ANTW: Cache access finished\n")
    }
    when (io.xfiles.dcache.mem.resp.valid) { feedConfigRob() }
  }

  io.xfiles.interrupt.valid := state === s_INTERRUPT
  io.xfiles.interrupt.bits.code := interruptCode.bits
  when (state === s_INTERRUPT) {
    // Add interrupt/exception support (#4)

    // [TODO] #4: The transition back to idle makese sense. However,
    // this also needs to respond to the cache to kill that waiting
    // entry. This should then propagate back up to the Transaction
    // Table and kill whatever is there?

    state := s_IDLE
    printfError("ANTW: Exception code 0d%d\n", interruptCode.bits)
  }

  assert(!(state === s_INTERRUPT & interruptCode.bits > UInt(int_INVEPB)),
    "ANTW: hit interrupt")

  when (io.xfiles.dcache.mem.req.fire()) {
    printfInfo("ANTW: Mem req to core with tag 0x%x for addr 0x%x\n",
      io.xfiles.dcache.mem.req.bits.tag, io.xfiles.dcache.mem.req.bits.addr) }

  when (io.xfiles.dcache.mem.resp.fire()) {
    printfInfo("ANTW: Mem resp from Core with tag 0x%x data 0x%x\n",
      io.xfiles.dcache.mem.resp.bits.tag,
      io.xfiles.dcache.mem.resp.bits.data_word_bypass) }

  // We need to look at the Config ROB and determine if anything is
  // valid to write back to the cache. A slot is valid if all its
  // valid bits are asserted.
  def configRobEntryValid(x: ConfigRobEntry): Bool = {
    x.valid === ~UInt(0, width = configBufSize)}
  val configRobHasValidEntries = configRob.exists(configRobEntryValid(_))
  val configRobValidIdx = configRob.indexWhere(configRobEntryValid(_))

  io.cache.resp.valid := configRobHasValidEntries
  when (configRobHasValidEntries) {
    val done = configWbCount === (configSize >> UInt(log2Up(configBufSize))) - UInt(1)
    val data = configRob(configRobValidIdx).data.toBits
    val cacheIdx = cacheReqCurrent.cacheIndex
    val cacheAddr = configRob(configRobValidIdx).cacheAddr
    io.cache.resp.bits.done := done
    io.cache.resp.bits.data := data
    io.cache.resp.bits.cacheIndex := cacheIdx
    io.cache.resp.bits.addr := cacheAddr

    configRob(configRobValidIdx).valid := UInt(0)
    configWbCount := configWbCount + UInt(1)

    printfInfo("ANTW: configWbCount: 0x%x of 0x%x\n", configWbCount,
      configSize >> UInt(log2Up(configBufSize)))
  }

  // Reset conditions
  when (reset) {
    antpReg.valid := Bool(false)
    (0 until antwRobEntries).map(i => configRob(i).valid := UInt(0))
  }

  // Assertions
  assert(!(io.cache.req.fire() && !io.cache.req.ready),
    "ANTW saw a cache request, but it's cache queue is full")
  assert(!(state === s_ERROR),
    "ANTW is in an error state")
  assert(Bool(isPow2(configBufSize)),
    "ANTW derived parameter configBufSize must be a power of 2")
  // Outbound memory requests shouldn't try to read NULL
  assert(!(io.xfiles.dcache.mem.req.valid &&
    io.xfiles.dcache.mem.req.bits.addr === UInt(0)),
    "INTERRUPT: ANTW tried to read from NULL")
}
