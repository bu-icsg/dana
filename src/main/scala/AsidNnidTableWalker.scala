package dana

import Chisel._

import rocket._
import uncore._
import Util._
import cde.{Parameters, Field}

class AsidNnidTableWalkerInterface(implicit p: Parameters) extends XFilesBundle()(p) {
  val asidUnit = Vec.fill(numCores){ (new AsidUnitANTWInterface).flip }
  val cache = (new CacheMemInterface).flip
  val mem = Vec.fill(numCores){
    new HellaCacheIO()(p.alterPartial({ case CacheName => "L1D" }))
  }
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

  val (s_IDLE ::           // 0
    s_CHECK_NNID_WAIT ::   // 1
    s_READ_NNID_POINTER :: // 2
    s_READ_CONFIGSIZE ::   // 3
    s_READ_CONFIGPTR ::    // 4
    s_READ_CONFIG ::       // 5
    s_READ_CONFIG_WAIT ::  // 6
    s_ERROR ::             // 7
    Nil) = Enum(UInt(), 8)
  val state = Reg(UInt(), init = s_IDLE)

  // State used to read a configuration
  val configSize = Reg(UInt(width = log2Up(cacheDataSize * 8 / xLen)))
  val configReqCount = Reg(UInt(width = log2Up(cacheDataSize * 8 / xLen)))
  val configRespCount = Reg(UInt(width = log2Up(cacheDataSize * 8 / xLen)))
  val configPtr = Reg(UInt(width = xLen))
  val configBufSize = bitsPerBlock / xLen
  val configWb = Reg(Bool())
  val configWbCount = Reg(UInt(width = log2Up(cacheDataSize * 8 / bitsPerBlock)))

  // Cache WB reorder cache
  val configRob = Vec.fill(antwRobEntries){ Reg(new ConfigRobEntry) }

  // Queue requests from the cache
  // [TODO] Add parameters for these cache depths
  val cacheReqQueue = Module(new Queue(new CacheMemReq, 2))
  val memReqQueue = Module(new Queue(new HellaCacheReqWithCore()(p), 4))
  val cacheReqCurrent = Reg(new CacheMemReq)

  // Default values
  (0 until numCores).map(i => io.asidUnit(i).req.ready := Bool(true))
  // We can accept new cache requests only if the Cache Request Queue
  // is ready, i.e., the queue isn't full
  io.cache.req.ready := cacheReqQueue.io.enq.ready
  io.cache.resp.valid := Bool(false)
  io.cache.resp.bits.done := Bool(false)
  io.cache.resp.bits.data := UInt(0)
  io.cache.resp.bits.cacheIndex := UInt(0)
  io.cache.resp.bits.addr := UInt(0)
  for (i <- 0 until numCores) {
    io.mem(i).req.valid := Bool(false)
    io.mem(i).req.bits.kill := Bool(false) // testing
    io.mem(i).req.bits.phys := Bool(true) // testing
    io.mem(i).req.bits.data := Bool(false) // testing
    io.mem(i).req.bits.addr := UInt(0)
    io.mem(i).req.bits.tag := UInt(0)
    io.mem(i).req.bits.cmd := UInt(0)
    io.mem(i).req.bits.typ := UInt(0)
    io.mem(i).invalidate_lr := Bool(false)
  }
  cacheReqQueue.io.enq.valid := Bool(false)
  cacheReqQueue.io.enq.bits.asid := UInt(0)
  cacheReqQueue.io.enq.bits.nnid := UInt(0)
  cacheReqQueue.io.enq.bits.cacheIndex := UInt(0)
  cacheReqQueue.io.enq.bits.coreIndex := UInt(0)
  cacheReqQueue.io.deq.ready := Bool(false)
  configWb := Bool(false)
  // Memory Request Queue
  memReqQueue.io.enq.valid := Bool(false)
  memReqQueue.io.enq.bits.req.addr := UInt(0)
  memReqQueue.io.enq.bits.req.tag := UInt(0)
  memReqQueue.io.enq.bits.req.cmd := UInt(0)
  memReqQueue.io.enq.bits.req.typ := UInt(0)
  // memReqQueue.io.enq.bits.req.toBits := UInt(0)
  memReqQueue.io.enq.bits.core := UInt(0)
  memReqQueue.io.deq.ready := Bool(false)

  def reqValid(x: AsidUnitANTWInterface): Bool = { x.req.valid }
  def respValid(x: HellaCacheIO): Bool = { x.resp.valid }
  val indexReq = io.asidUnit.indexWhere(reqValid(_))
  val indexResp = io.mem.indexWhere(respValid(_))

  def memRead(core: UInt, addr: UInt) {
    memReqQueue.io.enq.valid := Bool(true)
    memReqQueue.io.enq.bits.req.addr := addr
    memReqQueue.io.enq.bits.req.tag := addr(coreDCacheReqTagBits - 1, 0)
    memReqQueue.io.enq.bits.req.cmd := M_XRD
    memReqQueue.io.enq.bits.req.typ := MT_D
    memReqQueue.io.enq.bits.core := core
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
    val respIdx = (io.mem(indexResp).resp.bits.addr - configPtr) >>
    UInt(log2Up(xLen/8))
    // Based on this response index, compute the slot and offset
    // in the Config ROB buffer
    val configRobSlot = respIdx(log2Up(antwRobEntries) +
      log2Up(configBufSize) - 1, log2Up(configBufSize))
    val configRobOffset = respIdx(log2Up(configBufSize) - 1, 0)
    // The configRespCount just keeps track of how many responses
    // we've seen. This is used to determine when we've seen all
    // the reads we expecte.
    configRespCount := configRespCount + UInt(1)

    // Write the data to the appropriate slot and offset in the
    // Config ROB setting the valid flags appropriately
    configRob(configRobSlot).valid :=
      configRob(configRobSlot).valid |
      UInt(1, width = configBufSize) << configRobOffset
    configRob(configRobSlot).cacheAddr :=
      respIdx >> UInt(log2Up(configBufSize))
    configRob(configRobSlot).data(configRobOffset) :=
      io.mem(indexResp).resp.bits.data_word_bypass

    // Assertions

    // The Config ROB bit that we're setting valid should not already
    // be valid. This indicates that we're overwritting some valid
    // data that has not been written back, likely due to a dropped
    // cache request.
    assert(!((configRob(configRobSlot).valid &
      UInt(1, width = configBufSize) << configRobOffset) >> configRobOffset),
      "ANTW about to overwrite valid Config ROB entry. Possible dropped request?")
  }

  // Communication with the ASID Unit
  when (io.asidUnit.exists(reqValid)) {
    antpReg.valid := Bool(true)
    antpReg.antp := io.asidUnit(indexReq).req.bits.antp
    antpReg.size := io.asidUnit(indexReq).req.bits.size
    printfInfo("ANTW changing ANTP to 0x%x with size 0x%x\n",
      io.asidUnit(indexReq).req.bits.antp,
      io.asidUnit(indexReq).req.bits.size)
  }

  // New cache requests get entered on the queue
  when (io.cache.req.fire()) {
    printfInfo("ANTW: Enqueing new mem request for Core/ASID/NNID/Idx 0x%x/0x%x/0x%x/0x%x\n",
      io.cache.req.bits.coreIndex, io.cache.req.bits.asid,
      io.cache.req.bits.nnid, io.cache.req.bits.cacheIndex)
    cacheReqQueue.io.enq.valid := Bool(true)
    cacheReqQueue.io.enq.bits := io.cache.req.bits
  }

  // Pull memory requests out of the Memory Request Queue
  when (memReqQueue.io.deq.valid &&
    io.mem(memReqQueue.io.deq.bits.core).req.ready
    // [TODO] This _shouldn't_ be necessary, here, but I'm having
    // problems with dropped requests without it.
    && Reg(next = !io.mem(memReqQueue.io.deq.bits.core).req.valid)
  ) {
    val core = memReqQueue.io.deq.bits.core
    memReqQueue.io.deq.ready := Bool(true)
    io.mem(core).req.valid := Bool(true)
    io.mem(core).req.bits.addr := memReqQueue.io.deq.bits.req.addr
    io.mem(core).req.bits.tag :=
      memReqQueue.io.deq.bits.req.addr(coreDCacheReqTagBits - 1, 0)
    io.mem(core).req.bits.cmd := memReqQueue.io.deq.bits.req.cmd
    io.mem(core).req.bits.typ := memReqQueue.io.deq.bits.req.typ
    printfInfo("ANTW: Mem read req core/addr/tag(%x,%x) 0x%x/0x%x/0x%x\n",
      UInt(coreDCacheReqTagBits - 1), UInt(0),
      UInt(core), memReqQueue.io.deq.bits.req.addr,
      memReqQueue.io.deq.bits.req.addr(coreDCacheReqTagBits - 1, 0))
  }

  // [TODO] Need a small controller that determines what to do next.
  // This should support servicing a request on the queue or dealing
  // with a "one-off" request from a PE. I think this should be
  // written as request and response logic.
  val hasCacheRequests = cacheReqQueue.io.count > UInt(0) &&
    antpReg.valid

  switch (state) {
    is (s_IDLE) {
      when (hasCacheRequests &&
        io.mem(cacheReqQueue.io.deq.bits.coreIndex).req.ready) {
        // The base request offset is the ANTP plus the ASID *
        // size_of(asid_nnid_table_entry) which is 24 bytes
        val reqAddr = antpReg.antp + cacheReqQueue.io.deq.bits.asid * UInt(24)
        // Copy the request into the currently processing storage area
        cacheReqCurrent.asid := cacheReqQueue.io.deq.bits.asid
        cacheReqCurrent.nnid := cacheReqQueue.io.deq.bits.nnid
        cacheReqCurrent.cacheIndex := cacheReqQueue.io.deq.bits.cacheIndex
        cacheReqCurrent.coreIndex := cacheReqQueue.io.deq.bits.coreIndex
        cacheReqQueue.io.deq.ready := Bool(true)
        memRead(cacheReqQueue.io.deq.bits.coreIndex, reqAddr)
        state := s_CHECK_NNID_WAIT
        printfInfo("ANTW: Dequeuing mem request for Core/ASID/NNID/Idx 0x%x/0x%x/0x%x/0x%x\n",
          cacheReqQueue.io.deq.bits.coreIndex, cacheReqQueue.io.deq.bits.asid,
          cacheReqQueue.io.deq.bits.nnid, cacheReqQueue.io.deq.bits.cacheIndex)
      }
    }
    is (s_CHECK_NNID_WAIT) {
      when(io.mem.exists(respValid)) {
        // [TODO] Fragile on XLen
        val numConfigs = io.mem(indexResp).resp.bits.data_word_bypass(31, 0)
        val numValid = io.mem(indexResp).resp.bits.data_word_bypass(63, 32)
        printfInfo("ANTW: Saw CHECK_NNID resp w/ #configs 0x%x, #valid 0x%x\n",
          numConfigs, numValid)
        when (cacheReqCurrent.nnid < numValid) {
          val reqAddr = antpReg.antp + cacheReqCurrent.asid * UInt(24) + UInt(8)
          memRead(io.cache.req.bits.coreIndex, reqAddr)
          state := s_READ_NNID_POINTER
        } .otherwise {
          printf("[ERROR] ANTW: NNID reference would be invalid\n")
          state := s_ERROR
        }
      }
    }
    is (s_READ_NNID_POINTER) {
      when (io.mem.exists(respValid)) {
        val reqAddr = io.mem(indexResp).resp.bits.data_word_bypass +
          cacheReqCurrent.nnid * UInt(16)
        printfInfo("ANTW: Saw READ_NNID_POINTER resp w/ configPtr 0x%x\n",
          reqAddr + UInt(8))
        configPtr := reqAddr + UInt(8)
        memRead(io.cache.req.bits.coreIndex, reqAddr)
        state := s_READ_CONFIGSIZE
      }
    }
    is (s_READ_CONFIGSIZE) {
      when (io.mem.exists(respValid)) {
        printfInfo("ANTW: Saw READ_NNID_POINTER resp w/ configPtr 0x%x\n",
          io.mem(indexResp).resp.bits.data_word_bypass)
        configSize := io.mem(indexResp).resp.bits.data_word_bypass
        val reqAddr = configPtr
        memRead(io.cache.req.bits.coreIndex, reqAddr)
        state := s_READ_CONFIGPTR
      }
    }
    is (s_READ_CONFIGPTR) {
      when (io.mem.exists(respValid)) {
        configPtr := io.mem(indexResp).resp.bits.data_word_bypass
        configReqCount := UInt(1)
        configRespCount := UInt(0)
        configWbCount := UInt(0)
        val reqAddr = io.mem(indexResp).resp.bits.data_word_bypass
        memRead(io.cache.req.bits.coreIndex, reqAddr)
        state := s_READ_CONFIG
      }
    }
    is (s_READ_CONFIG) {
      // Whenever the cache can accept a new request, send one
      when (memReqQueue.io.enq.ready) {
        configReqCount := configReqCount + UInt(1)
        val reqAddr = configPtr + configReqCount * UInt(xLen / 8)
        memRead(io.cache.req.bits.coreIndex, reqAddr)
        when (configReqCount === configSize - UInt(1)) {
          state := s_READ_CONFIG_WAIT
        }
      }
      // If a new response shows up, write it into a buffer and send
      // it along to the Cache if we've filled a buffer
      when (io.mem.exists(respValid)) {
        feedCacheRob()
      }
    }
    is (s_READ_CONFIG_WAIT) {
      when (io.mem.exists(respValid)) {
        feedCacheRob()
      }
      when (configRespCount === configSize) {
        state := s_IDLE
      }
    }
  }

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
  // There should only be at most one valid ANTP update request from
  // all ASID Units
  assert(!(io.asidUnit.count(reqValid(_)) > UInt(1)),
    "Saw more than one simultaneous ANTP request")
  assert(!(io.mem.count(respValid(_)) > UInt(1)),
    "Saw more than one simultaneous ANTP response, dropping all but one...")
  assert(!(io.cache.req.fire() && !io.cache.req.ready),
    "ANTW saw a cache request, but it's cache queue is full")
  // If the ASID is larger than the stored size, then this is an
  // invalid ASID for the stored ASID--NNID table pointer.
  assert(!(io.cache.req.fire() && antpReg.valid &&
    antpReg.size < io.cache.req.bits.asid),
    "ANTW saw cache request with out of bounds ASID")
  assert(!(io.cache.req.fire() && !antpReg.valid),
    "ANTW saw cache request with invalid ASID")
  assert(!(state === s_ERROR),
    "ANTW is in an error state")
  assert(Bool(isPow2(configBufSize)),
    "ANTW derived parameter configBufSize must be a power of 2")
  // Outbound memory requests shouldn't happen when memory not ready
  (0 until numCores).map(core =>
    assert(!(io.mem(core).req.valid && !io.mem(core).req.ready),
      "ANTW just sent memory to a core when memory was not ready"))
  // Outbound memory requests should try to read NULL
  (0 until numCores).map(core =>
    assert(!(io.mem(core).req.valid && io.mem(core).req.bits.addr === UInt(0)),
      "ANTW tried to read from NULL"))
}
