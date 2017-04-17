// See LICENSE.BU for license details.
// See LICENSE.IBM for license details.

package dana

import chisel3._
import chisel3.util._
import rocket.{HellaCacheReq, MT_D}
import uncore.tilelink.{HasTileLinkParameters, ClientUncachedTileLinkIO, Get,
  GetBlock, PutBlock}
import uncore.util._
import uncore.agents.{CacheBlockBytes}
import cde._
import xfiles._

// TileLink Parameters:
//   * tlDataBits -- bits per beat
//   * tlDataBeats -- beats per cache line (2 ^ tlBeatAddrBits)
//   * tlDataBytes -- bytes per beat (2 ^ tlByteAddrBits)
// Dana Parameters:
//   * bitsPerBlock -- bits per one block (elementWidth * elementsPerblock)
//   * configSize is specified in Dana Blocks

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
  val autl      = new ClientUncachedTileLinkIO()(p)
}

class AsidNnidTableWalkerInterface(implicit p: Parameters) extends DanaStatusIO()(p) {
  val cache     = Flipped(new CacheAntwInterface)
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
    s_GET_NN_CONFIG_CLEANUP :: s_PUT_NN_CONFIG :: s_INTERRUPT :: s_ERROR ::
    Nil) = Enum(12)

  val state = Reg(UInt(), init = s_IDLE)

  // State used to read a configuration
  val configReqCount = Reg(UInt((log2Up(cacheDataSize * 8 / xLen)).W))
  val configBufSize = bitsPerBlock / xLen

  // Queue for cache requests. At maximum, every entry in the
  // Configuration Cache. could have an outstanding request, so we
  // size this queue accordingly. The head of the queue can then be
  // operated on directly or the data in the head can be dequeued into
  // a set of "current" registers. The latter approach is used here.
  val cacheReqQueue = Module(new Queue(new CacheAntwReq, cacheNumEntries))
  cacheReqQueue.io.enq <> io.cache.cmd
  val cacheReqCurrent = Reg(new CacheAntwReq)
  val cacheIdx = cacheReqCurrent.cacheIndex

  val cacheAddr = Reg(UInt(log2Up(cacheNumBlocks).W))
  io.cache.load.valid := false.B
  io.cache.load.bits.done := false.B
  io.cache.load.bits.data := 0.U
  io.cache.load.bits.cacheIndex := cacheIdx
  io.cache.load.bits.addr := cacheAddr

  io.cache.store.req.valid := false.B

  val acq = io.xfiles.autl.acquire
  val gnt = io.xfiles.autl.grant
  acq.valid := false.B
  gnt.ready := true.B

  val interruptCode = Reg(init = 0.U(xLen.W))
  def setInterrupt(code: Int) {
    if (code >= 0) interruptCode := code.U
    else interruptCode := (code.S).asUInt }

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
  val putData = Wire(UInt(tlDataBits.W))
  val putBlock = PutBlock(client_xact_id = 0.U,
    addr_block = addr_block,
    addr_beat = addr_beat,
    data = putData)

  acq.bits := Mux(state === s_GET_NN_CONFIG, getBlock,
    Mux(state === s_PUT_NN_CONFIG, putBlock, get))

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
    code: Int = Causes.unknown) = {
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
  val (loadRob, storeRob) = (Reg(new ConfigRobEntry), Reg(new ConfigRobEntry))
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
      setInterrupt(Causes.no_antp)
    }
    reqSent := false.B

    loadRob.valid map (x => x := false.B)
  }

  val asid = cacheReqCurrent.asid
  val nnid = cacheReqCurrent.nnid
  when (state === s_CHECK_ASID) {
    state := s_GET_VALID_NNIDS
    when (asid >= io.status.num_asids) {
      state := s_INTERRUPT
      setInterrupt(Causes.invalid_nnid)
    }
  }

  autlAddr := antp + asid * sizeAsidStruct.U
  when (state === s_GET_VALID_NNIDS) {
    val reqAddr = antp + asid * sizeAsidStruct.U
    val numValidNnids = autlDataWord(63, 32)
    autlAcqGrant(s_GET_NN_POINTER, nnid < numValidNnids, Causes.invalid_nnid)
  }

  val nnidPointer = Reg(UInt())
  nnidPointer := nnidPointer
  when (state === s_GET_NN_POINTER) {
    autlAddr := antp + asid * sizeAsidStruct.U + offsetNnidPtr.U
    nnidPointer := autlDataWord + nnid * sizeNnidStruct.U
    autlAcqGrant(s_GET_NN_SIZE, autlDataWord =/= 0.U, Causes.null_read)
  }

  val configSize = Reg(UInt())
  val configSizeBytes = configSize ## 0.U(log2Up(bytesPerBlock).W)
  configSize := configSize
  when (state === s_GET_NN_SIZE) {
    autlAddr := nnidPointer
    configSize := autlDataWord
    autlAcqGrant(s_GET_NN_EPB, autlDataWord =/= 0.U, Causes.zero_size)
  }

  when (state === s_GET_NN_EPB) {
    autlAddr := nnidPointer + offsetEpb.U
    autlAcqGrant(s_GET_CONFIG_POINTER, autlDataWord === elementsPerBlock.U,
      Causes.invalid_epb)
  }

  val configPointer = Reg(UInt())
  configPointer := configPointer
  when (state === s_GET_CONFIG_POINTER) {
    autlAddr := nnidPointer + offsetConfig.U
    configReqCount := 0.U
    configPointer := autlDataWord
    def aligned(addr: UInt): Bool = {
      addr(log2Up(p(CacheBlockBytes)) - 1, 0) === 0.U }

    val nextState = Mux(cacheReqCurrent.action === CacheTypes.Mem.read.U,
      s_GET_NN_CONFIG, s_PUT_NN_CONFIG)
    autlAcqGrant(nextState, aligned(autlDataWord) & autlDataWord =/= 0.U,
      Causes.misaligned)
    cacheAddr := 0.U
  }

  def autlAcqGrantBlock(nextState: UInt, cond: => Bool = true.B,
    code: Int = Causes.unknown) = {
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
  def robIndex(addr: UInt) = bitsPerBlock compare tlDataBits match {
    case 0 => 0.U
    case 1 => addr(tlByteAddrBits + log2Up(bitsPerBlock/tlDataBits) - 1,
      tlByteAddrBits)
    case -1 => throw new Exception("bits per DANA block < ANTW L2 bits per beat") }
  def feedConfigRob(addr_beat: UInt) {
    val autlAddrWithBeat_d = autlAddr_d | (addr_beat << tlByteAddrBits)
    val beatsPerResp = bitsPerBlock/tlDataBits
    // The index of the block that we're writing back to the cache
    val respIdx = (autlAddrWithBeat_d - configPointer) >> (log2Up(xLen/8)).U
    // The beatOffset is the index into the loadRob.data vector.
    val beatOffset = robIndex(autlAddrWithBeat_d)
    loadRob.data(beatOffset) := gnt.bits.data
    loadRob.valid(beatOffset) := true.B

    if (p(EnablePrintfs)) {
      printfInfo("feedConfigRob[%d] data 0x%x\n", beatOffset, gnt.bits.data)
      printfInfo("  autlAddrWithBeat_d 0x%x, (%d, %d) 0x%x\n",
        autlAddrWithBeat_d,
        (tlByteAddrBits + log2Up(beatsPerResp) - 1).U, tlByteAddrBits.U,
        autlAddrWithBeat_d(tlByteAddrBits + log2Up(beatsPerResp) - 1, tlByteAddrBits)) }
    if (p(EnableAsserts)) {
      assert(!(loadRob.valid(beatOffset) & !io.cache.load.valid),
        printfSigil ++ "overwrite occurred in loadRob" ) }
  }

  // We need to look at the Config ROB and determine if anything is
  // valid to write back to the cache. A slot is valid if all its
  // valid bits are asserted. Note: This block (and it's reset of the
  // loadRob valid bits) comes before the next block (and it's
  // setting of the loadRob valid bits) to use last connect
  // semantics. A write to the loadRob and a write back can occur on
  // the same cycle!
  val done = cacheAddr >= (configSize >> log2Up(configBufSize).U) - 1.U
  when (loadRob.valid.asUInt.andR) {
    io.cache.load.valid := true.B
    io.cache.load.bits.done := done
    io.cache.load.bits.data := loadRob.data.asUInt
    cacheAddr := cacheAddr + 1.U
    when (done) { state := s_IDLE }
    loadRob.valid.map(_ := false.B)
  }

  val autlFinished = configReqCount > configSize - 1.U
  when (state === s_GET_NN_CONFIG) {
    autlAddr := configPointer
    val nextState = Mux(autlFinished, s_GET_NN_CONFIG_CLEANUP, s_GET_NN_CONFIG)
    when (!autlFinished) { autlAcqGrantBlock(nextState) }

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

  val (rob_index, _) = Counter(acq.fire() && state === s_PUT_NN_CONFIG,
    storeRob.data.size)
  val (put_count, put_sent) = Counter(acq.fire() && state === s_PUT_NN_CONFIG,
    tlDataBeats)
  putData := Mux(autlFinished, 0.U, storeRob.data(rob_index))
  io.cache.store.req.bits.addr := cacheAddr
  io.cache.store.req.bits.index := cacheIdx
  io.cache.store.req.bits.done := autlFinished
  val gntWait = Reg(init = false.B)
  when (state === s_PUT_NN_CONFIG) {
    autlAddr := configPointer
    val dataReady = storeRob.valid.asUInt.orR

    def cacheReqBlock() = {
      when (!reqSent) {
        io.cache.store.req.valid := true.B
        reqSent := io.cache.store.req.fire()
      }

      when (io.cache.store.resp.fire()) {
        reqSent := false.B
        storeRob.valid map (_ := true.B)
        storeRob.data := (storeRob.data.cloneType).fromBits(io.cache.store.resp.bits)
        cacheAddr := cacheAddr + 1.U
      }
    }

    val blocksToPut = ( (configSizeBytes >> autlBlockOffset) +
      configSizeBytes(autlBlockOffset - 1, 0).orR )

    when (put_sent) { gntWait := true.B }
    .elsewhen (gnt.fire()) { gntWait := false.B }

    acq.valid := (dataReady || autlFinished) && !gntWait

    // When we don't have data, fetch it from cache
    when (!autlFinished && !dataReady) { cacheReqBlock() }

    // When we have data, send whatever is valid from the storeRob
    // until nothing is left
    when (acq.fire()) { storeRob.valid(rob_index) := false.B
      configReqCount := configReqCount + (tlDataBits / xLen).U
      configPointer := configPointer + (1.U << tlByteAddrBits)
    }

    when (gnt.fire()) {configPointer := configPointer + (1.U<<autlBlockOffset)}
    when (gnt.fire() && autlFinished) {
      io.cache.store.req.valid := true.B // Indicate fence/sync completion
      state := s_IDLE }
  }

  io.probes.interrupt := state === s_INTERRUPT
  io.probes.cause := interruptCode
  when (state === s_INTERRUPT) {
    // Add interrupt/exception support (#4)

    // [TODO] #4: The transition back to idle makese sense. However,
    // this also needs to respond to the cache to kill that waiting
    // entry. This should then propagate back up to the Transaction
    // Table and kill whatever is there?

    interruptCode := 0.U
    state := s_IDLE
  }

  val interruptDelay = Module(new Pipe(UInt(1.W), 500))
  interruptDelay.io.enq.valid := state === s_INTERRUPT

  // Reset conditions
  when (reset) {
    loadRob.valid map (_ := false.B)
    storeRob.valid map (_ := false.B) }
}

object AsidNnidTableWalker {
  trait Printfs extends AsidNnidTableWalker {
    when (io.cache.cmd.fire()) {
      printfInfo("Enqueue mem req ASID/NNID/Idx 0x%x/0x%x/0x%x\n",
        io.cache.cmd.bits.asid, io.cache.cmd.bits.nnid,
        io.cache.cmd.bits.cacheIndex) }

    when (acq.fire()) {
      printfInfo("AUTL ACQ.%d | addr 0x%x, addr_block 0x%x, addr_beat 0x%x, addr_byte 0x%x, data 0x%x\n",
        acq.bits.a_type, autlAddr, acq.bits.addr_block, acq.bits.addr_beat,
        acq.bits.addr_byte(), acq.bits.data)
    }

    when (gnt.fire()) {
      printfInfo("AUTL GNT | data 0x%x, addr_beat 0x%x, addr_word 0x%x, word 0x%x\n",
        gnt.bits.data, gnt.bits.addr_beat, autlAddrWord_d, autlDataWord)
    }

    when (cacheReqQueue.io.deq.fire()) {
      printfInfo("Dequeue mem req ANTP/ASID/NNID/Idx 0x%x/0x%x/0x%x/0x%x\n",
        antp, deq.asid, deq.nnid, deq.cacheIndex)
    }

    when (loadRob.valid.asUInt.andR) {
      printfInfo("Cache[%d] Resp: done 0x%x, addr 0x%x, data 0x%x\n",
        cacheIdx, done, cacheAddr, loadRob.data.asUInt)
      printfInfo("  cacheAddr/configSize/cS>>cbs 0x%x/0x%x/0x%x\n",
        cacheAddr, configSize, (configSize >> log2Up(configBufSize).U) - 1.U)
    }

    when (state === s_INTERRUPT) {
      printfError("Exception code 0d%d\n", interruptCode)
    }

    when (io.cache.store.req.fire()) {
      printfInfo("Request for store data from Cache(%d)[0x%x], done: %b\n",
        io.cache.store.req.bits.index, io.cache.store.req.bits.addr,
        io.cache.store.req.bits.done)
    }

    when (io.cache.store.resp.fire()) {
      printfInfo("Received store data 0x%x\n", io.cache.store.resp.bits)
    }
  }

  trait Asserts extends AsidNnidTableWalker {
    assert(!RegNext((state === s_INTERRUPT) & (interruptCode > Causes.misaligned.U)),
      printfSigil ++ "hit interrupt")
    assert(!RegNext(io.cache.cmd.fire() && !io.cache.cmd.ready),
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
