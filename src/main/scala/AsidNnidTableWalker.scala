// See LICENSE for license details.

package dana

import Chisel._

import rocket.{RoCCCommand, RoCCResponse, HellaCacheReq, HellaCacheIO, MStatus}
import uncore.{HasTileLinkParameters, CacheName, ClientUncachedTileLinkIO, Get,
  GetBlock, CacheBlockBytes}
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

class HellaCacheReqWithCore(implicit p: Parameters) extends DanaBundle()(p) {
  val req = new HellaCacheReq()(p)
}

class antp(implicit p: Parameters) extends DanaBundle()(p) {
  val valid = Bool()
  val antp = UInt(width = xLen)
  val size = UInt(width = xLen)
}

class ConfigRobEntry(implicit p: Parameters) extends DanaBundle()(p)
    with HasTileLinkParameters {
  val valid = UInt(width = bitsPerBlock / tlDataBits)
  val data = Vec.fill(bitsPerBlock / tlDataBits){UInt(width = tlDataBits)}
}

class AsidNnidTableWalker(implicit p: Parameters) extends DanaModule()(p)
  with XFilesSupervisorRequests with HasTileLinkParameters {
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

  val cacheRespQueue = Module(new Queue(new CacheMemResp, cacheNumEntries))

  // Default values
  io.xfiles.rocc.cmd.ready := Bool(true)

  io.cache.resp.valid := Bool(false)
  io.cache.resp.bits.done := Bool(false)
  io.cache.resp.bits.data := UInt(0)
  io.cache.resp.bits.cacheIndex := UInt(0)
  io.cache.resp.bits.addr := UInt(0)

  val acq = io.xfiles.autl.acquire
  val gnt = io.xfiles.autl.grant
  acq.valid := Bool(false)
  gnt.ready := Bool(true)

  io.xfiles.dcache.mem.req.valid := Bool(false)

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

  val autlBlockOffset = tlBeatAddrBits + tlByteAddrBits
  val autlAddr = Wire(UInt(width = xLen))
  val addr_block = autlAddr(coreMaxAddrBits - 1, autlBlockOffset)
  val addr_beat = autlAddr(autlBlockOffset - 1, tlByteAddrBits)
  val addr_byte = autlAddr(tlByteAddrBits - 1, 0)
  val get = Get(client_xact_id = UInt(0),
      addr_block = addr_block,
      addr_beat = addr_beat,
      addr_byte = addr_byte,
      operand_size = MT_D,
      alloc = Bool(false))
  val getBlock = GetBlock(addr_block = addr_block, alloc = Bool(false))

  acq.bits := Mux(state <= s_GET_CONFIG_POINTER, get, getBlock)

  val autlAddr_d = Reg(UInt(width = xLen))
  val autlAddrWord_d = autlAddr_d(tlByteAddrBits - 1, log2Up(xLen/8))
  val autlDataGetVec = Wire(Vec.fill(tlDataBits / xLen)(UInt(width = xLen)))
  (0 until tlDataBits/xLen).map(i =>
    autlDataGetVec(i).toBits := gnt.bits.data((i+1) * xLen-1, i * xLen))
  val autlDataWord = autlDataGetVec(autlAddrWord_d)
  def autlAcqGrant(nextState: UInt, cond: => Bool = Bool(true),
    code: Int = int_UNKNOWN) = {
    when (!reqSent) {
      acq.valid := Bool(true)
      reqSent := acq.fire()
      autlAddr_d := autlAddr
    }

    when (gnt.fire()) {
      reqSent := Bool(false)
      state := Mux(cond, nextState, s_INTERRUPT)
      setInterrupt(code)
    }
  }

  when (acq.fire()) {
    printfInfo("ANTW: AUTL ACQ.%d | addr 0x%x, addr_block 0x%x, addr_beat 0x%x, addr_byte 0x%x\n",
      acq.bits.a_type, autlAddr, acq.bits.addr_block, acq.bits.addr_beat,
      acq.bits.addr_byte())
  }

  when (gnt.fire()) {
    printfInfo("ANTW: AUTL GNT | data 0x%x, addr_beat 0x%x, addr_word 0x%x, word 0x%x\n",
      gnt.bits.data, gnt.bits.addr_beat, autlAddrWord_d, autlDataWord)
  }

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

  autlAddr := antpReg.antp + asid * UInt(24)
  when (state === s_GET_VALID_NNIDS) {
    val reqAddr = antpReg.antp + asid * UInt(24)
    val numValidNnids = autlDataWord(63, 32)
    autlAcqGrant(s_GET_NN_POINTER, nnid < numValidNnids, int_INVNNID)
  }

  val nnidPointer = Reg(UInt())
  nnidPointer := nnidPointer
  when (state === s_GET_NN_POINTER) {
    val reqAddr = antpReg.antp + asid * UInt(24) + UInt(8)
    nnidPointer := autlDataWord + nnid * UInt(24)
    autlAddr := reqAddr
    autlAcqGrant(s_GET_NN_SIZE, autlDataWord =/= UInt(0), int_NULLREAD)
  }

  val configSize = Reg(UInt())
  configSize := configSize
  when (state === s_GET_NN_SIZE) {
    val reqAddr = nnidPointer
    autlAddr := reqAddr
    configSize := autlDataWord
    autlAcqGrant(s_GET_NN_EPB, autlDataWord =/= UInt(0), int_ZEROSIZE)
  }

  when (state === s_GET_NN_EPB) {
    val reqAddr = nnidPointer + UInt(8)
    autlAddr := reqAddr
    autlAcqGrant(s_GET_CONFIG_POINTER, autlDataWord === UInt(elementsPerBlock),
      int_INVEPB)
  }

  val configPointer = Reg(UInt())
  val cacheAddr = Reg(UInt(width = log2Up(cacheNumBlocks)))
  configPointer := configPointer
  when (state === s_GET_CONFIG_POINTER) {
    val reqAddr = nnidPointer + UInt(16)
    configReqCount := UInt(0)
    configWbCount := UInt(0)
    autlAddr := reqAddr
    configPointer := autlDataWord
    def misaligned(addr: UInt): Bool = {
      addr(log2Up(p(CacheBlockBytes)) - 1, 0) === UInt(0) }

    autlAcqGrant(s_GET_NN_CONFIG, misaligned(autlDataWord), int_MISALIGNED)
    cacheAddr := UInt(0)
  }

  def autlAcqGrantBlock(nextState: UInt, cond: => Bool = Bool(true),
    code: Int = int_UNKNOWN) = {
    when (!reqSent) {
      acq.valid := Bool(true)
      reqSent := acq.fire()
      autlAddr_d := autlAddr
    }

    when (gnt.fire() & gnt.bits.addr_beat === UInt(tlDataBeats - 1)) {
      reqSent := Bool(false)
      state := Mux(cond, nextState, s_INTERRUPT)
      setInterrupt(code)
    }
  }

  // Entries can come back in any order, so we use a Config Reorder
  // Buffer (ROB) to pack the beat-sized responses into blocks before
  // sending them back to the configuration cache.
  val configRob = Reg(new ConfigRobEntry)
  def feedConfigRob(addr_beat: UInt) {
    val autlAddrWithBeat_d = autlAddr_d | (addr_beat << tlByteAddrBits)
    val beatsPerResp = bitsPerBlock/tlDataBits
    // The index of the block that we're writing back to the cache
    val respIdx = (autlAddrWithBeat_d - configPointer) >> UInt(log2Up(xLen/8))
    // The beatOffset is the index into the configRob.data vector.
    val beatOffset = bitsPerBlock compare tlDataBits match {
      case 0 => UInt(0)
      case 1 => autlAddrWithBeat_d(tlByteAddrBits + log2Up(beatsPerResp) - 1,
        tlByteAddrBits)
      case -1 => throwException("bits per DANA block < ANTW L2 bits per beat")
    }
    configRob.data(beatOffset) := gnt.bits.data
    configRob.valid(beatOffset) := Bool(true)

    printfInfo("ANTW: feedConfigRob[%d] data 0x%x\n", beatOffset, gnt.bits.data)
    printfInfo("ANTW:   autlAddrWithBeat_d 0x%x, (%d, %d) 0x%x\n",
      autlAddrWithBeat_d,
      UInt(tlByteAddrBits + log2Up(beatsPerResp) - 1), UInt(tlByteAddrBits),
      autlAddrWithBeat_d(tlByteAddrBits + log2Up(beatsPerResp) - 1, tlByteAddrBits))
    assert(!(configRob.valid(beatOffset)), "ANTW: overwrite occurred in configRob" )
  }

  when (state === s_GET_NN_CONFIG) {
    autlAddr := configPointer
    val finished = configReqCount >= configSize - UInt(1)
    val nextState = Mux(finished, s_IDLE, s_GET_NN_CONFIG)
    autlAcqGrantBlock(nextState)

    when (gnt.fire() & !finished) {
      feedConfigRob(gnt.bits.addr_beat)
      configReqCount := configReqCount + UInt(tlDataBits / xLen)
      printfInfo("ANTW: configReqCount 0x%x\n",
        configReqCount + UInt(tlDataBits / xLen))
    }

    when (gnt.fire() & gnt.bits.addr_beat === UInt(tlDataBeats - 1)) {
      configPointer := configPointer + (UInt(1) << autlBlockOffset)
      printfInfo("ANTW: configPointer 0x%x\n",
        configPointer + (UInt(1) << autlBlockOffset)) }
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

  assert(!((state === s_INTERRUPT) & (interruptCode.bits > UInt(int_MISALIGNED))),
    "ANTW: hit interrupt")
  val interruptDelay = Module(new Pipe(UInt(width=1), 500))
  interruptDelay.io.enq.valid := state === s_INTERRUPT
  // assert(!(interruptDelay.io.deq.valid), "ANTW: hit interrupt")

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
  // val configRobHasValidEntries = configRob.exists(configRobEntryValid(_))
  // val configRobValidIdx = configRob.indexWhere(configRobEntryValid(_))

  when (configRob.valid.toBits.andR) {
    val done = cacheAddr >= (configSize >> UInt(log2Up(configBufSize))) - UInt(1)
    val cacheIdx = cacheReqCurrent.cacheIndex
    io.cache.resp.valid := Bool(true)
    io.cache.resp.bits.done := done
    io.cache.resp.bits.data := configRob.data.toBits
    io.cache.resp.bits.addr := cacheAddr
    cacheAddr := cacheAddr + UInt(1)

    configRob.valid.toBits := UInt(0)
    printfInfo("ANTW: Cache[%d] Resp: done 0x%x, addr 0x%x, data 0x%x\n",
      cacheIdx, done, cacheAddr, configRob.data.toBits)
    printfInfo("ANTW:   cacheAddr/configSize/cS>>cbs 0x%x/0x%x/0x%x\n",
      cacheAddr, configSize, (configSize >> UInt(log2Up(configBufSize))) - UInt(1))

  }

  // Reset conditions
  when (reset) {
    antpReg.valid := Bool(false)
    configRob.valid.toBits := UInt(0)
  }

  // Assertions
  assert(!(io.cache.req.fire() && !io.cache.req.ready),
    "ANTW saw a cache request, but it's cache queue is full")
  assert(!(RegNext(RegNext(RegNext(state === s_ERROR)))),
    "ANTW is in an error state")
  assert(Bool(isPow2(configBufSize)),
    "ANTW derived parameter configBufSize must be a power of 2")
  // Outbound memory requests shouldn't try to read NULL
  assert(!(io.xfiles.dcache.mem.req.valid &&
    io.xfiles.dcache.mem.req.bits.addr === UInt(0)),
    "INTERRUPT: ANTW tried to read from NULL")
}
