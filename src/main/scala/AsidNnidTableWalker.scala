// See LICENSE for license details.

package dana

import Chisel._

import rocket.{RoCCCommand, RoCCResponse, HellaCacheReq, HellaCacheIO}
import uncore.{CacheName}
import uncore.constants.MemoryOpConstants._
import cde.{Parameters}

class ANTWXFilesInterface(implicit p: Parameters) extends XFilesBundle()(p) {
  val rocc = new Bundle {
    val cmd = Decoupled(new RoCCCommand).flip
    val resp = Decoupled(new RoCCResponse)
    val s = Bool(INPUT)
    val coreIdxCmd = UInt(INPUT, width = log2Up(numCores))
    val coreIdxResp = UInt(OUTPUT, width = log2Up(numCores))
  }
  val dcache = new Bundle {
    val mem = new HellaCacheIO()(p.alterPartial({ case CacheName => "L1D" }))
    val coreIdxReq = UInt(OUTPUT, width = log2Up(numCores))
    val coreIdxResp = UInt(INPUT, width = log2Up(numCores))
  }
}

class AsidNnidTableWalkerInterface(implicit p: Parameters) extends XFilesBundle()(p) {
  val cache = (new CacheMemInterface).flip
  val xfiles = new ANTWXFilesInterface
}

class ConfigRobEntry(implicit p: Parameters) extends XFilesBundle()(p) {
  val valid = UInt(width = bitsPerBlock / xLen)
  val cacheAddr = UInt(width = log2Up(cacheDataSize * 8 / bitsPerBlock))
  val data = Vec.fill(bitsPerBlock / xLen){UInt(width = xLen)}
}

class HellaCacheReqWithCore(implicit p: Parameters) extends XFilesBundle()(p) {
  val req = new HellaCacheReq()(p)
  val core = UInt(width = log2Up(numCores))
}

class antp(implicit p: Parameters) extends XFilesBundle()(p) {
  val valid = Bool()
  val antp = UInt(width = xLen)
  val size = UInt(width = xLen)
}

class AsidNnidTableWalker(implicit p: Parameters) extends XFilesModule()(p) {
  val io = new AsidNnidTableWalkerInterface
  val antpReg = Reg(new antp)

  val (s_IDLE :: s_CHECK_ASID :: s_GET_VALID_NNIDS :: s_GET_NN_POINTER ::
    s_GET_NN_SIZE :: s_GET_NN_EPB :: s_GET_CONFIG_POINTER :: s_GET_NN_CONFIG ::
    s_GET_NN_CONFIG_CLEANUP :: s_EXCEPTION :: s_ERROR :: Nil) = Enum(UInt(), 11)

  val state = Reg(UInt(), init = s_IDLE)

  // State used to read a configuration
  val configReqCount = Reg(UInt(width = log2Up(cacheDataSize * 8 / xLen)))
  val configBufSize = bitsPerBlock / xLen
  val configWb = Reg(Bool())
  val configWbCount = Reg(UInt(width = log2Up(cacheDataSize * 8 / bitsPerBlock)))

  // Cache WB reorder cache
  val configRob = Vec.fill(antwRobEntries){ Reg(new ConfigRobEntry) }

  // Queue requests from the cache
  // [TODO] Add parameters for these cache depths
  val cacheReqQueue = Module(new Queue(new CacheMemReq, 2))
  val cacheReqCurrent = Reg(new CacheMemReq)

  // Default values
  io.xfiles.rocc.cmd.ready := Bool(true)
  // We can accept new cache requests only if the Cache Request Queue
  // is ready, i.e., the queue isn't full
  io.cache.req.ready := cacheReqQueue.io.enq.ready
  io.cache.resp.valid := Bool(false)
  io.cache.resp.bits.done := Bool(false)
  io.cache.resp.bits.data := UInt(0)
  io.cache.resp.bits.cacheIndex := UInt(0)
  io.cache.resp.bits.addr := UInt(0)
  for (i <- 0 until numCores) {
    io.xfiles.dcache.mem.req.valid := Bool(false)
    io.xfiles.dcache.mem.req.bits.kill := Bool(false) // testing
    io.xfiles.dcache.mem.req.bits.phys := Bool(true) // testing
    io.xfiles.dcache.mem.req.bits.data := Bool(false) // testing
    io.xfiles.dcache.mem.req.bits.addr := UInt(0)
    io.xfiles.dcache.mem.req.bits.tag := UInt(0)
    io.xfiles.dcache.mem.req.bits.cmd := UInt(0)
    io.xfiles.dcache.mem.req.bits.typ := UInt(0)
    io.xfiles.dcache.mem.invalidate_lr := Bool(false)
  }
  cacheReqQueue.io.enq.valid := Bool(false)
  cacheReqQueue.io.enq.bits.asid := UInt(0)
  cacheReqQueue.io.enq.bits.nnid := UInt(0)
  cacheReqQueue.io.enq.bits.cacheIndex := UInt(0)
  cacheReqQueue.io.enq.bits.coreIndex := UInt(0)
  cacheReqQueue.io.deq.ready := Bool(false)
  configWb := Bool(false)

  val indexResp = io.xfiles.dcache.coreIdxResp
  val respData = io.xfiles.dcache.mem.resp.bits.data_word_bypass

  val configPointer = Reg(UInt())
  def memRead(addr: UInt) {
    io.xfiles.dcache.mem.req.bits.addr := addr
    io.xfiles.dcache.mem.req.bits.tag := addr(coreDCacheReqTagBits - 1, 0)
    io.xfiles.dcache.mem.req.bits.cmd := M_XRD
    io.xfiles.dcache.mem.req.bits.typ := MT_D
  }
  def cacheResp(done: Bool, data: UInt, cacheIndex: UInt, addr: UInt) {
    io.cache.resp.valid := Bool(true)
    io.cache.resp.bits.done := done
    io.cache.resp.bits.data := data
    io.cache.resp.bits.cacheIndex := cacheIndex
    io.cache.resp.bits.addr := addr
  }
  def feedCacheRob() {
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

    // Assertions

    val overwrite = (configRob(configRobSlot).valid &
      UInt(1, width = configBufSize) << configRobOffset) =/= UInt(0)
    // The Config ROB bit that we're setting valid should not already
    // be valid. This indicates that we're overwritting some valid
    // data that has not been written back, likely due to a dropped
    // cache request.
    // assert(!(overwrite),
    //   "ANTW about to overwrite valid Config ROB entry. Possible dropped request?")

    when (overwrite) {
      printfWarn("ANTW: saw overwrite on oldAddr/newAddr 0x%x 0x%x of oldData/newData 0x%x/0x%x\n",
        configRob(configRobSlot).cacheAddr, cacheAddr,
        configRob(configRobSlot).data(configRobOffset), respData) }

    when (overwrite & (configRob(configRobSlot).cacheAddr === cacheAddr) &
      (configRob(configRobSlot).data(configRobOffset) === respData)) {
      printfWarn("ANTW: Overwriting existing entry with the same addr/data\n") }

    assert(!(overwrite & (configRob(configRobSlot).cacheAddr =/= cacheAddr)),
      "ANTW about to overwrite a valid Config ROB entry with different addr")

    assert(!(overwrite &
      (configRob(configRobSlot).data(configRobOffset) =/= respData)),
      "ANTW about to overwrite a valid Config ROB entry with different data")
  }

  // Communication with the ASID Unit
  val funct = io.xfiles.rocc.cmd.bits.inst.funct
  val updateAntp = io.xfiles.rocc.s && funct === t_SUP_WRITE_REG
  when (io.xfiles.rocc.cmd.fire() && updateAntp) {
    antpReg.valid := Bool(true)
    antpReg.antp := io.xfiles.rocc.cmd.bits.rs1
    antpReg.size := io.xfiles.rocc.cmd.bits.rs2
    printfInfo("ANTW changing ANTP to 0x%x with size 0x%x\n",
      io.xfiles.rocc.cmd.bits.rs1, io.xfiles.rocc.cmd.bits.rs2)
  }

  io.xfiles.rocc.resp.bits.rd := io.xfiles.rocc.cmd.bits.inst.rd
  io.xfiles.rocc.resp.bits.data := Mux(antpReg.valid, antpReg.antp,
    SInt(-err_DANA_NOANTP, width = xLen).toUInt)
  io.xfiles.rocc.coreIdxResp := io.xfiles.rocc.coreIdxCmd
  io.xfiles.rocc.resp.valid := io.xfiles.rocc.cmd.fire() && updateAntp

  when (io.xfiles.rocc.resp.valid) {
    printfInfo("ANTW: Responding to core %d, R%d with data 0x%x\n",
      io.xfiles.rocc.coreIdxResp, io.xfiles.rocc.resp.bits.rd,
      io.xfiles.rocc.resp.bits.data) }

  // New cache requests get entered on the queue
  when (io.cache.req.fire()) {
    printfInfo("ANTW: Enqueing new mem request for Core/ASID/NNID/Idx 0x%x/0x%x/0x%x/0x%x\n",
      io.cache.req.bits.coreIndex, io.cache.req.bits.asid,
      io.cache.req.bits.nnid, io.cache.req.bits.cacheIndex)
    cacheReqQueue.io.enq.valid := Bool(true)
    cacheReqQueue.io.enq.bits := io.cache.req.bits
  }

  // [TODO] Need a small controller that determines what to do next.
  // This should support servicing a request on the queue or dealing
  // with a "one-off" request from a PE. I think this should be
  // written as request and response logic.
  val hasCacheRequests = cacheReqQueue.io.count > UInt(0) &&
    antpReg.valid

  val exceptionCode = Reg(Valid(UInt()))
  def setException(code: Int) {
    exceptionCode.valid := Bool(true);
    exceptionCode.bits := UInt(code) }
  def clearException() {
    exceptionCode.valid := Bool(false) }

  val reqSent = Reg(Bool())
  reqSent := reqSent
  def reqNoResp(nextState: UInt) = {
    io.xfiles.dcache.mem.req.valid := Bool(true)
    when (io.xfiles.dcache.mem.req.ready) {
      io.xfiles.dcache.mem.req.valid := Bool(false)
      state := nextState }}
  def reqWaitForResp(nextState: UInt, cond: => Bool = Bool(true),
    code: Int = err_UNKNOWN) = {
    when (!reqSent) {
      io.xfiles.dcache.mem.req.valid := Bool(true)
      reqSent := io.xfiles.dcache.mem.req.ready
    } .elsewhen (io.xfiles.dcache.mem.resp.valid) {
      io.xfiles.dcache.mem.req.valid := Bool(false)
      reqSent := Bool(false)
      state := Mux(cond, nextState, s_EXCEPTION)
      setException(code) }}

  when (state === s_IDLE & hasCacheRequests) {
    // Pull data out of the cache request queue and save it in the
    // "current" buffer
    cacheReqCurrent := cacheReqQueue.io.deq.bits
    cacheReqQueue.io.deq.ready := Bool(true)
    state := s_CHECK_ASID
    reqSent := Bool(false)
  }

  val asid = cacheReqCurrent.asid
  val nnid = cacheReqCurrent.nnid
  when (state === s_CHECK_ASID) {
    state := s_GET_VALID_NNIDS
    when (asid >= antpReg.size) {
      state := s_EXCEPTION
      setException(err_INVASID)
    }
  }

  io.xfiles.dcache.coreIdxReq := cacheReqCurrent.coreIndex
  when (state === s_GET_VALID_NNIDS) {
    val reqAddr = antpReg.antp + cacheReqCurrent.asid * UInt(24)
    val numValidNnids = respData(63, 32)
    memRead(reqAddr)
    reqWaitForResp(s_GET_NN_POINTER, nnid < numValidNnids, err_INVNNID)
  }

  val nnidPointer = Reg(UInt())
  nnidPointer := nnidPointer
  when (state === s_GET_NN_POINTER) {
    val reqAddr = antpReg.antp + asid * UInt(24) + UInt(8)
    nnidPointer := respData + nnid * UInt(24)
    memRead(reqAddr)
    reqWaitForResp(s_GET_NN_SIZE, respData =/= UInt(0), err_ZEROSIZE)
  }

  val configSize = Reg(UInt())
  configSize := configSize
  when (state === s_GET_NN_SIZE) {
    val reqAddr = nnidPointer
    memRead(reqAddr)
    configSize := respData
    reqWaitForResp(s_GET_NN_EPB)
  }

  when (state === s_GET_NN_EPB) {
    val reqAddr = nnidPointer + UInt(8)
    memRead(reqAddr)
    reqWaitForResp(s_GET_CONFIG_POINTER, respData === UInt(elementsPerBlock),
      err_INVEPB)
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
    // [TODO] This computation may be better served as an update to a
    // register
    memRead(configPointer + configReqCount * UInt(xLen / 8))
    // Related to #5, if I don't slow down the rate of request
    // generation, the core tends to generate repeated responses.
    io.xfiles.dcache.mem.req.valid := Bool(true)
    when (io.xfiles.dcache.mem.req.fire()) {
      configReqCount := configReqCount + UInt(1)
      when (configReqCount === configSize - UInt(1)) {
        state := s_GET_NN_CONFIG_CLEANUP
      }
    }
    when (io.xfiles.dcache.mem.resp.valid) { feedCacheRob() }
  }

  when (state === s_GET_NN_CONFIG_CLEANUP) {
    when (configWbCount === configSize) { state := s_IDLE }
    when (io.xfiles.dcache.mem.resp.valid) { feedCacheRob() }
  }

  when (state === s_EXCEPTION) {
    printfError("ANTW: Excpetion code 0d%d\n", exceptionCode.bits);
    state := s_ERROR;
  }

  when (io.xfiles.dcache.mem.req.fire()) {
    printfInfo("ANTW: Mem req to Core %d with tag 0x%x for addr 0x%x\n",
      io.xfiles.dcache.coreIdxReq, io.xfiles.dcache.mem.req.bits.tag,
      io.xfiles.dcache.mem.req.bits.addr) }

  when (io.xfiles.dcache.mem.resp.fire()) {
    printfInfo("ANTW: Mem resp from Core %d with tag 0x%x data 0x%x\n",
      io.xfiles.dcache.coreIdxResp, io.xfiles.dcache.mem.resp.bits.tag,
      io.xfiles.dcache.mem.resp.bits.data_word_bypass) }

  // We need to look at the Config ROB and determine if anything is
  // valid to write back to the cache. A slot is valid if all its
  // valid bits are asserted.
  def configRobAllValid(x: ConfigRobEntry): Bool = {
    x.valid === ~UInt(0, width = configBufSize)}
  val configRobWb = configRob.exists(configRobAllValid(_))
  val configRobIdx = configRob.indexWhere(configRobAllValid(_))

  // Writeback data to the cache whenever the configWb flag tells us
  // that the configBuf has valid data
  when (configRobWb) {
    cacheResp(
      configWbCount === (configSize >> UInt(log2Up(configBufSize))) - UInt(1),
      configRob(configRobIdx).data.toBits,
      cacheReqCurrent.cacheIndex,
      configRob(configRobIdx).cacheAddr)
    printfInfo("ANTW: configWbCount: 0x%x of 0x%x\n", configWbCount,
      configSize >> UInt(log2Up(configBufSize)))
    configRob(configRobIdx).valid := UInt(0)
    configWbCount := configWbCount + UInt(1)
  }

  // Reset conditions
  when (reset) {
    antpReg.valid := Bool(false)
    (0 until antwRobEntries).map(i => configRob(i).valid := UInt(0))
  }

  // Assertions
  assert(!(io.cache.req.fire() && !io.cache.req.ready),
    "ANTW saw a cache request, but it's cache queue is full")
  // If the ASID is larger than the stored size, then this is an
  // invalid ASID for the stored ASID--NNID table pointer.
  assert(!(io.cache.req.fire() && antpReg.valid &&
    antpReg.size < io.cache.req.bits.asid),
    "ANTW saw cache request with out of bounds ASID")
  assert(!(io.cache.req.fire() && !antpReg.valid),
    "ANTW saw cache request with invalid ASID-NNID Table Pointer")
  assert(!(state === s_ERROR),
    "ANTW is in an error state")
  assert(Bool(isPow2(configBufSize)),
    "ANTW derived parameter configBufSize must be a power of 2")
  // Outbound memory requests shouldn't try to read NULL
  assert(!(io.xfiles.dcache.mem.req.valid &&
    io.xfiles.dcache.mem.req.bits.addr === UInt(0)),
    "ANTW tried to read from NULL")
}
