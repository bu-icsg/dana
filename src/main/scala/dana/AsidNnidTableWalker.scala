// See LICENSE.BU for license details.
// See LICENSE.IBM for license details.

package dana

import chisel3._
import chisel3.util._
import rocket.{HellaCacheReq, HellaCacheIO, MStatus, MT_D}
import uncore.tilelink.{HasTileLinkParameters, ClientUncachedTileLinkIO, Get,
  GetBlock}
import uncore.util.{CacheName, CacheBlockBytes}
import config._
import xfiles._

trait AntParameters {
  // Parameters that must match xfiles-supervisor.h. These can be
  // generated with usr/bin/antw-config.
  val sizeAsidStruct = 32  // sizeof(asid_nnid_table_entry)
  val sizeNnidStruct = 40  // sizeof(nn_configuration)
  val offsetNnidPtr  = 8
  val offsetEpb      = 8
  val offsetConfig   = 24
}

class ANTWXFilesInterface(implicit p: Parameters) extends DanaBundle()(p) {
  val autl      = new ClientUncachedTileLinkIO
  val interrupt = Valid(new InterruptBundle)
}

class AsidNnidTableWalkerInterface(implicit p: Parameters) extends DanaStatusIO()(p) {
  val cache     = (new CacheMemInterface).flip
  val xfiles    = new ANTWXFilesInterface
}

class HellaCacheReqWithCore(implicit p: Parameters) extends DanaBundle()(p) {
  val req       = new HellaCacheReq()(p)
}

class ConfigRobEntry(implicit p: Parameters) extends DanaBundle()(p)
    with HasTileLinkParameters {
  val valid     = Vec(bitsPerBlock / tlDataBits, Bool())
  val data      = Vec(bitsPerBlock / tlDataBits, UInt(tlDataBits.W))
}

class AsidNnidTableWalker(implicit p: Parameters) extends DanaModule()(p)
    with XFilesSupervisorRequests with HasTileLinkParameters
    with AntParameters {
  override val printfSigil = "xfiles.ANTW: "
  val io = IO(new AsidNnidTableWalkerInterface)
  val (s_IDLE :: s_CHECK_ASID :: s_GET_VALID_NNIDS :: s_GET_NN_POINTER ::
    s_GET_NN_SIZE :: s_GET_NN_EPB :: s_GET_CONFIG_POINTER :: s_GET_NN_CONFIG ::
    s_GET_NN_CONFIG_CLEANUP :: s_INTERRUPT :: s_ERROR :: Nil) = Enum(UInt(), 11)

  val state = Reg(UInt(), init = s_IDLE)

  // State used to read a configuration
  val configReqCount = Reg(UInt((log2Up(cacheDataSize * 8 / xLen)).W))
  val configBufSize = bitsPerBlock / xLen

  // Queue for cache requests. At maximum, every entry in the
  // Configuration Cache. could have an outstanding request, so we
  // size this queue accordingly. The head of the queue can then be
  // operated on directly or the data in the head can be dequeued into
  // a set of "current" registers. The latter approach is used here.
  val cacheReqQueue = Module(new Queue(new CacheMemReq, cacheNumEntries))
  cacheReqQueue.io.enq <> io.cache.req
  val cacheReqCurrent = Reg(new CacheMemReq)

  io.cache.resp.valid := false.B
  io.cache.resp.bits.done := false.B
  io.cache.resp.bits.data := 0.U
  io.cache.resp.bits.cacheIndex := 0.U
  io.cache.resp.bits.addr := 0.U

  val acq = io.xfiles.autl.acquire
  val gnt = io.xfiles.autl.grant
  acq.valid := false.B
  gnt.ready := true.B

  val interruptCode = Reg(Valid(UInt()))
  def setInterrupt(code: Int) {
    interruptCode.valid := true.B;
    if (code >= 0) interruptCode.bits := code.U
    else interruptCode.bits := (code.S).asUInt }
  def clearInterrupt() { interruptCode.valid := false.B }

  val autlBlockOffset = tlBeatAddrBits + tlByteAddrBits
  val autlAddr = Wire(UInt(xLen.W))
  val addr_block = autlAddr(coreMaxAddrBits - 1, autlBlockOffset)
  val addr_beat = autlAddr(autlBlockOffset - 1, tlByteAddrBits)
  val addr_byte = autlAddr(tlByteAddrBits - 1, 0)
  val get = Get(client_xact_id = 0.U,
      addr_block = addr_block,
      addr_beat = addr_beat,
      addr_byte = addr_byte,
      operand_size = MT_D,
      alloc = false.B)
  val getBlock = GetBlock(addr_block = addr_block, alloc = false.B)

  acq.bits := Mux(state <= s_GET_CONFIG_POINTER, get, getBlock)

  val autlAddr_d = Reg(UInt(xLen.W))
  val autlAddrWord_d = tlDataBits compare xLen match {
    case 1 => autlAddr_d(tlByteAddrBits - 1, log2Up(xLen/8))
    case 0 => autlAddr_d(tlByteAddrBits, log2Up(xLen/8))
    case -1 => throw new Exception("XLen > tlByteAddrBits (this doesn't make sense!)")
  }
  val autlDataGetVec = Wire(Vec(tlDataBits / xLen, UInt(xLen.W)))
  autlDataGetVec.zipWithIndex.map({
    case(x,i) => x := gnt.bits.data((i+1) * xLen-1, i * xLen) })
  val autlDataWord = autlDataGetVec(autlAddrWord_d)
  // Many of the state updates are gated by waiting for a response.
  // This leverages a similar structure from
  // src/main/scala/ProcessingElement.scala with `reqWaitForResp`.
  // This wraps up all the logic of generating a request, waiting for
  // a response, and using a function (`cond`) to determine of things
  // are okay to proceed.
  val reqSent = Reg(Bool())
  def autlAcqGrant(nextState: UInt, cond: => Bool = true.B,
    code: Int = int_UNKNOWN) = {
    when (!reqSent) {
      acq.valid := true.B
      reqSent := acq.fire()
      autlAddr_d := autlAddr
    }

    when (gnt.fire()) {
      reqSent := false.B
      state := Mux(cond, nextState, s_INTERRUPT)
      setInterrupt(code)
    }
  }

  val hasCacheRequests = cacheReqQueue.io.count > 0.U
  val configRob = Reg(new ConfigRobEntry)
  cacheReqQueue.io.deq.ready := state === s_IDLE & hasCacheRequests
  val antp = io.status.antp
  val antpValid = antp =/= ~(0.U(xLen.W))
  val deq = cacheReqQueue.io.deq.bits
  when (state === s_IDLE & hasCacheRequests) {
    // Pull data out of the cache request queue and save it in the
    // "current" buffer
    cacheReqCurrent := deq
    state := s_CHECK_ASID
    when (!antpValid) {
      state := s_INTERRUPT
      setInterrupt(int_DANA_NOANTP)
    }
    reqSent := false.B

    configRob.valid map (x => x := false.B)
  }

  val asid = cacheReqCurrent.asid
  val nnid = cacheReqCurrent.nnid
  when (state === s_CHECK_ASID) {
    state := s_GET_VALID_NNIDS
    when (asid >= io.status.num_asids) {
      state := s_INTERRUPT
      setInterrupt(int_INVASID)
    }
  }

  autlAddr := antp + asid * sizeAsidStruct.U
  when (state === s_GET_VALID_NNIDS) {
    val reqAddr = antp + asid * sizeAsidStruct.U
    val numValidNnids = autlDataWord(63, 32)
    autlAcqGrant(s_GET_NN_POINTER, nnid < numValidNnids, int_INVNNID)
  }

  val nnidPointer = Reg(UInt())
  nnidPointer := nnidPointer
  when (state === s_GET_NN_POINTER) {
    autlAddr := antp + asid * sizeAsidStruct.U + offsetNnidPtr.U
    nnidPointer := autlDataWord + nnid * sizeNnidStruct.U
    autlAcqGrant(s_GET_NN_SIZE, autlDataWord =/= 0.U, int_NULLREAD)
  }

  val configSize = Reg(UInt())
  configSize := configSize
  when (state === s_GET_NN_SIZE) {
    autlAddr := nnidPointer
    configSize := autlDataWord
    autlAcqGrant(s_GET_NN_EPB, autlDataWord =/= 0.U, int_ZEROSIZE)
  }

  when (state === s_GET_NN_EPB) {
    autlAddr := nnidPointer + offsetEpb.U
    autlAcqGrant(s_GET_CONFIG_POINTER, autlDataWord === elementsPerBlock.U,
      int_INVEPB)
  }

  val configPointer = Reg(UInt())
  val cacheAddr = Reg(UInt(log2Up(cacheNumBlocks).W))
  configPointer := configPointer
  when (state === s_GET_CONFIG_POINTER) {
    autlAddr := nnidPointer + offsetConfig.U
    configReqCount := 0.U
    configPointer := autlDataWord
    def aligned(addr: UInt): Bool = {
      addr(log2Up(p(CacheBlockBytes)) - 1, 0) === 0.U }

    autlAcqGrant(s_GET_NN_CONFIG, aligned(autlDataWord) & autlDataWord =/= 0.U,
      int_MISALIGNED)
    cacheAddr := 0.U
  }

  def autlAcqGrantBlock(nextState: UInt, cond: => Bool = true.B,
    code: Int = int_UNKNOWN) = {
    when (!reqSent) {
      acq.valid := true.B
      reqSent := acq.fire()
      autlAddr_d := autlAddr
    }

    when (gnt.fire() & gnt.bits.addr_beat === (tlDataBeats - 1).U) {
      reqSent := false.B
      state := Mux(cond, nextState, s_INTERRUPT)
      setInterrupt(code)
    }
  }

  // Entries can come back in any order, so we use a Config Reorder
  // Buffer (ROB) to pack the beat-sized responses into blocks before
  // sending them back to the configuration cache.
  def feedConfigRob(addr_beat: UInt) {
    val autlAddrWithBeat_d = autlAddr_d | (addr_beat << tlByteAddrBits)
    val beatsPerResp = bitsPerBlock/tlDataBits
    // The index of the block that we're writing back to the cache
    val respIdx = (autlAddrWithBeat_d - configPointer) >> (log2Up(xLen/8)).U
    // The beatOffset is the index into the configRob.data vector.
    val beatOffset = bitsPerBlock compare tlDataBits match {
      case 0 => 0.U
      case 1 => autlAddrWithBeat_d(tlByteAddrBits + log2Up(beatsPerResp) - 1,
        tlByteAddrBits)
      case -1 => throw new Exception("bits per DANA block < ANTW L2 bits per beat")
    }
    configRob.data(beatOffset) := gnt.bits.data
    configRob.valid(beatOffset) := true.B

    if (p(EnablePrintfs)) {
      printfInfo("feedConfigRob[%d] data 0x%x\n", beatOffset, gnt.bits.data)
      printfInfo("  autlAddrWithBeat_d 0x%x, (%d, %d) 0x%x\n",
        autlAddrWithBeat_d,
        (tlByteAddrBits + log2Up(beatsPerResp) - 1).U, tlByteAddrBits.U,
        autlAddrWithBeat_d(tlByteAddrBits + log2Up(beatsPerResp) - 1, tlByteAddrBits)) }
    if (p(EnableAsserts)) {
      assert(!(configRob.valid(beatOffset) & !io.cache.resp.valid),
        printfSigil ++ "overwrite occurred in configRob" ) }
  }

  // We need to look at the Config ROB and determine if anything is
  // valid to write back to the cache. A slot is valid if all its
  // valid bits are asserted. Note: This block (and it's reset of the
  // configRob valid bits) comes before the next block (and it's
  // setting of the configRob valid bits) to use last connect
  // semantics. A write to the configRob and a write back can occur on
  // the same cycle!
  val done = cacheAddr >= (configSize >> log2Up(configBufSize).U) - 1.U
  val cacheIdx = cacheReqCurrent.cacheIndex
  when (configRob.valid.asUInt.andR) {
    io.cache.resp.valid := true.B
    io.cache.resp.bits.done := done
    io.cache.resp.bits.data := configRob.data.asUInt
    io.cache.resp.bits.addr := cacheAddr
    cacheAddr := cacheAddr + 1.U
    when (done) { state := s_IDLE }
    configRob.valid.map(_ := false.B)
  }

  when (state === s_GET_NN_CONFIG) {
    autlAddr := configPointer
    val finishedAcq = configReqCount >= configSize - 1.U
    val nextState = Mux(finishedAcq, s_GET_NN_CONFIG_CLEANUP, s_GET_NN_CONFIG)
    when (!finishedAcq) { autlAcqGrantBlock(nextState) }

    when (acq.fire()) {
      configReqCount := configReqCount + (tlDataBits / xLen * tlDataBeats).U
    }

    when (gnt.fire()) { feedConfigRob(gnt.bits.addr_beat) }

    when (gnt.fire() & gnt.bits.addr_beat === (tlDataBeats - 1).U) {
      configPointer := configPointer + (1.U << autlBlockOffset) }
  }

  when (state === s_GET_NN_CONFIG_CLEANUP) {
    when (gnt.fire()) {
      feedConfigRob(gnt.bits.addr_beat)
    }
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
  }

  val interruptDelay = Module(new Pipe(UInt(1.W), 500))
  interruptDelay.io.enq.valid := state === s_INTERRUPT

  // Reset conditions
  when (reset) { configRob.valid map (_ := false.B) }
}

object AsidNnidTableWalker {
  trait Printfs extends AsidNnidTableWalker {
    when (io.cache.req.fire()) {
      printfInfo("Enqueue mem req ASID/NNID/Idx 0x%x/0x%x/0x%x\n",
        io.cache.req.bits.asid, io.cache.req.bits.nnid,
        io.cache.req.bits.cacheIndex) }

    when (acq.fire()) {
      printfInfo("AUTL ACQ.%d | addr 0x%x, addr_block 0x%x, addr_beat 0x%x, addr_byte 0x%x\n",
        acq.bits.a_type, autlAddr, acq.bits.addr_block, acq.bits.addr_beat,
        acq.bits.addr_byte())
    }

    when (gnt.fire()) {
      printfInfo("AUTL GNT | data 0x%x, addr_beat 0x%x, addr_word 0x%x, word 0x%x\n",
        gnt.bits.data, gnt.bits.addr_beat, autlAddrWord_d, autlDataWord)
    }

    when (cacheReqQueue.io.deq.fire()) {
      printfInfo("Dequeue mem req ANTP/ASID/NNID/Idx 0x%x/0x%x/0x%x/0x%x\n",
        antp, deq.asid, deq.nnid, deq.cacheIndex)
    }

    when (configRob.valid.asUInt.andR) {
      printfInfo("Cache[%d] Resp: done 0x%x, addr 0x%x, data 0x%x\n",
        cacheIdx, done, cacheAddr, configRob.data.asUInt)
      printfInfo("  cacheAddr/configSize/cS>>cbs 0x%x/0x%x/0x%x\n",
        cacheAddr, configSize, (configSize >> log2Up(configBufSize).U) - 1.U)
    }

    when (state === s_INTERRUPT) {
      printfError("Exception code 0d%d\n", interruptCode.bits)
    }
  }

  trait Asserts extends AsidNnidTableWalker {
    assert(!RegNext((state === s_INTERRUPT) & (interruptCode.bits > int_MISALIGNED.U)),
      printfSigil ++ "hit interrupt")
    assert(!RegNext(io.cache.req.fire() && !io.cache.req.ready),
      printfSigil ++ "saw a cache request, but it's cache queue is full")
    assert(!(RegNext(RegNext(RegNext(state === s_ERROR)))),
      printfSigil ++ "is in an error state")
    assert((isPow2(configBufSize)).B,
      printfSigil ++ "derived parameter configBufSize must be a power of 2")
  }

  def apply()(implicit p: Parameters): AsidNnidTableWalker =
    (p(EnablePrintfs), p(EnableAsserts)) match {
      case (false, false) => new AsidNnidTableWalker
      case (false, true)  => new AsidNnidTableWalker with Asserts
      case (true,  false) => new AsidNnidTableWalker with Printfs
      case (true,  true)  => new AsidNnidTableWalker with Asserts with Printfs
    }
}
