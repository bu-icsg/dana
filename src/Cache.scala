package dana

import Chisel._

class CacheState extends DanaBundle()() {
  // nnsim-hdl equivalent:
  //   cache_types::cache_config_entry_struct
  val valid = Reg(Bool(), init = Bool(false))
  val notifyFlag = Reg(Bool())
  val fetch = Reg(Bool())
  val notifyIndex = Reg(UInt(width = log2Up(transactionTableNumEntries)))
  val notifyMask = Reg(UInt(width = transactionTableNumEntries))
  val nnid = Reg(UInt(width = nnidWidth))
  val inUseCount = Reg(UInt(width = log2Up(transactionTableNumEntries)))
}

class CacheMemInterface extends DanaBundle()() {
  // Outbound request. nnsim-hdl equivalent:
  //   cache_types::cache2mem_struct
  val req = Decoupled(new DanaBundle()() {
    val nnid = UInt(width = nnidWidth)
    // [TODO] I'm not sure if the following is needed
    val tTableIndex = UInt(width = log2Up(transactionTableNumEntries))
    val cacheIndex = UInt(width = log2Up(cacheNumEntries))
  })
  // Response from memory. nnsim-hdl equivalent:
  //   cache_types::mem2cache_struct
  val resp = Decoupled(new DanaBundle()() {
    val done = Bool()
    val data = UInt(width = elementsPerBlock * elementWidth)
    val cacheIndex = UInt(width = log2Up(cacheNumEntries))
    val addr = UInt(width = log2Up(cacheNumBlocks))
    val inUse = Bool()
  }).flip
}

class CacheInterface extends Bundle {
  // The cache is connected to memory (technically via the arbiter
  // when this gets added), the control unit, and to the processing
  // elements
  val mem = new CacheMemInterface
  val control = (new ControlCacheInterface).flip
  val pe = (new PECacheInterface).flip
}

// Helper bundles that we can use for typecasting (I think)
class CompressedInfo extends DanaBundle()() {
  val decimalPoint      = UInt(width = decimalPointWidth)
  val unused_0          = Bits(width = 16 - decimalPointWidth)
  val totalEdges        = UInt(width = 16) // [TODO] Wrong name?
  val totalNeurons      = UInt(width = 16)
  val totalLayers       = UInt(width = 16)
  val firstLayerPointer = UInt(width = 16)
  val weightsPointer    = UInt(width = 16)
  val unused_1          = Bits(width = bitsPerBlock - 16 * 6)
}

class Cache extends DanaModule()() {
  val io = new CacheInterface

  // Create the table of cache entries
  val table = Vec.fill(cacheNumEntries){new CacheState}
  // Each cache entry gets one two-ported SRAM
  val mem = Vec.fill(cacheNumEntries){
    Module(new SRAM(
      dataWidth = elementWidth * elementsPerBlock,
      numReadPorts = 0,
      numWritePorts = 0,
      numReadWritePorts = 2,
      sramDepth = cacheNumBlocks // [TODO] I think this is the correct parameter
    )).io}

  // Fake signals that hack in typecasting onto the data read out from
  // the cache. CompressedInfo is currently not used because I can't
  // figure out a way to make it work.
  val compressedInfo = Vec.fill(cacheNumEntries){new CompressedInfo}
  val compressedLayers = Vec.fill(cacheNumEntries){
    Vec.fill(bitsPerBlock / 32){UInt(width = 32) }}
  val compressedNeurons = Vec.fill(cacheNumEntries){
    Vec.fill(bitsPerBlock / 64){UInt(width = 64) }}
  val compressedWeights = Vec.fill(cacheNumEntries){
    Vec.fill(bitsPerBlock / 32){UInt(width = 32)}}
  for (i <- 0 until cacheNumEntries) {
    compressedInfo(i).decimalPoint      := mem(i).dout(0)(decimalPointWidth-1,0)
    compressedInfo(i).unused_0          := mem(i).dout(0)(16-decimalPointWidth-1,
      decimalPointWidth)
    compressedInfo(i).totalEdges        := mem(i).dout(0)(32-1, 16)
    compressedInfo(i).totalNeurons      := mem(i).dout(0)(48-1, 32)
    compressedInfo(i).totalLayers       := mem(i).dout(0)(64-1, 48)
    compressedInfo(i).firstLayerPointer := mem(i).dout(0)(80-1, 64)
    compressedInfo(i).weightsPointer    := mem(i).dout(0)(96-1, 80)
    compressedInfo(i).unused_1          := mem(i).dout(0)(bitsPerBlock-1, 96)
    debug(compressedInfo(i))
    (0 until bitsPerBlock / 32).map(j => compressedLayers(i)(j) :=
      mem(i).dout(0)(32 * (j + 1) - 1, 32 * j))
    (0 until bitsPerBlock / 64).map(j => compressedNeurons(i)(j) :=
      mem(i).dout(0)(64 * (j + 1) - 1, 64 * j))
    (0 until bitsPerBlock / 32).map(j => compressedWeights(i)(j) :=
      mem(i).dout(0)(64 * (j + 1) - 1, 64 * j))
  }

  // Response Pipelines for Control module and PEs. Responses take multiple
  // cycles to generate due to the fact that data needs to be read out
  // of the individual cache SRAMs so we construct a pipeline that
  // builds up the responses.
  val controlRespPipe =
    Vec.fill(2){Reg(Valid(new ControlCacheInterfaceResp))}
  val peRespPipe =
    Vec.fill(2){Reg(Valid(new PECacheInterfaceResp))}
  val cacheRead = Vec.fill(cacheNumEntries){
    (Reg(UInt(width=log2Up(cacheNumBlocks)))) }
  // We also need to store the cache index of an inbound request by a
  // PE so that we can dereference it one cycle later when the cache
  // line SRAM output is valid. [TODO] Should this be gated by the PE
  // request being valid?
  val peCacheIndex_d0 = Reg(UInt(), next = io.pe.req.bits.cacheIndex)

  // Helper functions for examing the cache entries
  def isFree(x: CacheState): Bool = {!x.valid}
  def isUnused(x: CacheState): Bool = {x.inUseCount === UInt(0)}
  def derefNnid(x: CacheState, y: UInt): Bool = {x.nnid === y}

  // State that we need to derive from the cache
  val hasFree = Bool()
  val hasUnused = Bool()
  val nextFree = UInt()
  val foundNnid = Bool()
  val derefNnid = UInt()
  hasFree := table.exists(isFree)
  hasUnused := table.exists(isUnused)
  nextFree := table.indexWhere(isFree)
  foundNnid := table.exists(derefNnid(_, io.control.req.bits.nnid))
  derefNnid := table.indexWhere(derefNnid(_, io.control.req.bits.nnid))

  io.control.req.ready := hasFree | hasUnused

  // Default values
  io.mem.req.valid := Bool(false)
  io.mem.req.bits.nnid := UInt(0)
  io.mem.req.bits.tTableIndex := UInt(0)
  io.mem.req.bits.cacheIndex := UInt(0)

  io.control.resp.valid := Bool(false)
  io.control.resp.bits.fetch := Bool(false)
  io.control.resp.bits.tableIndex := UInt(0)
  io.control.resp.bits.tableMask := UInt(0)
  io.control.resp.bits.cacheIndex := UInt(0)
  io.control.resp.bits.data := Vec.fill(3){UInt(0)}
  io.control.resp.bits.decimalPoint := UInt(0)
  io.control.resp.bits.field := UInt(0)

  controlRespPipe(0).valid := Bool(false)
  controlRespPipe(0).bits.fetch := Bool(false)
  controlRespPipe(0).bits.tableIndex := UInt(0)
  controlRespPipe(0).bits.tableMask := UInt(0)
  controlRespPipe(0).bits.cacheIndex := UInt(0)
  controlRespPipe(0).bits.data := Vec.fill(3){UInt(0)}
  controlRespPipe(0).bits.decimalPoint := UInt(0)
  controlRespPipe(0).bits.field := UInt(0)

  peRespPipe(0).valid := Bool(false)
  peRespPipe(0).bits.field := UInt(0)
  peRespPipe(0).bits.data := UInt(0)
  peRespPipe(0).bits.peIndex := UInt(0)
  peRespPipe(0).bits.indexIntoData := UInt(0)

  // [TODO] This shouldn't always be true
  io.pe.req.ready := Bool(true)

  // Assignment to the output pipe
  controlRespPipe(1) := controlRespPipe(0)
  peRespPipe(1) := peRespPipe(0)

  // debug(controlRespPipe)

  for (i <- 0 until cacheNumEntries) {
    mem(i).din(0) := UInt(0)
    mem(i).addr(0) := UInt(0)
    // mem(i).addr(0) := cacheRead(i)
    mem(i).we(0) := Bool(false)
    // cacheRead(i) := UInt(0)
  }

  // Handle requests from the control module
  when (io.control.req.valid) {
    switch (io.control.req.bits.request) {
      is (e_CACHE_LOAD) {
        when (!foundNnid) {
          // The NNID was not found, so we need to generate a memory
          // request to load it in.

          // Reserve the new cache entry
          table(nextFree).valid := Bool(true)
          table(nextFree).nnid := io.control.req.bits.nnid
          table(nextFree).notifyIndex := io.control.req.bits.tableIndex
          table(nextFree).inUseCount := UInt(1)

          // Generate a request to memory
          io.mem.req.valid := Bool(true)
          io.mem.req.bits.nnid := io.control.req.bits.nnid
          io.mem.req.bits.tTableIndex := io.control.req.bits.tableIndex
          io.mem.req.bits.cacheIndex := nextFree
        } .elsewhen (table(derefNnid).fetch) {
          // The nnid was found, but the data is currently being
          // loaded from memory. This happens if a second request for
          // the same data shows up while this guy is being fetched.
          table(derefNnid).inUseCount := table(derefNnid).inUseCount + UInt(1)
          table(derefNnid).notifyMask := table(derefNnid).notifyMask |
          UIntToOH(io.control.req.bits.tableIndex)
        } .otherwise {
          // The NNID was found and the data has already been loaded
          table(derefNnid).inUseCount := table(derefNnid).inUseCount + UInt(1)

          // Start a response to the control unit
          controlRespPipe(0).valid := Bool(true)
          controlRespPipe(0).bits.tableIndex := io.control.req.bits.tableIndex
          controlRespPipe(0).bits.tableMask :=
            UIntToOH(io.control.req.bits.tableIndex)
          controlRespPipe(0).bits.cacheIndex := derefNnid
          controlRespPipe(0).bits.field := e_CACHE_INFO

          // Read the requested information from the cache
          // mem(derefNnid).addr(0) := UInt(0) // Info is in first blocks
          // cacheRead(derefNnid) := UInt(0)
        }
      }
      is (e_CACHE_LAYER_INFO) {
        controlRespPipe(0).valid := Bool(true)
        controlRespPipe(0).bits.tableIndex := io.control.req.bits.tableIndex
        controlRespPipe(0).bits.field := e_CACHE_LAYER_INFO
        controlRespPipe(0).bits.data(0) := io.control.req.bits.layer

        // Read the layer information from the correct block. A layer
        // occupies one block, so we need to pull the block address
        // out of the layer number.
        mem(derefNnid).addr(0) := UInt(1) + // Offset from info region
          io.control.req.bits.layer(io.control.req.bits.layer.getWidth-1,
            log2Up(elementsPerBlock))
        // cacheRead(derefNnid) := UInt(1) + // Offset from info region
        //   io.control.req.bits.layer(io.control.req.bits.layer.getWidth-1,
        //     log2Up(elementsPerBlock))
      }
      is (e_CACHE_DECREMENT_IN_USE_COUNT) {

      }
    }
  }

  // Pipeline second stage (SRAM read)
  switch (controlRespPipe(0).bits.field) {
    is (e_CACHE_INFO) {
      // Decimal Point
      controlRespPipe(1).bits.decimalPoint :=
        compressedInfo(controlRespPipe(0).bits.cacheIndex).decimalPoint
      // Layers
      controlRespPipe(1).bits.data(0) :=
        compressedInfo(controlRespPipe(0).bits.cacheIndex).totalLayers
      // Neurons
      controlRespPipe(1).bits.data(1) :=
        compressedInfo(controlRespPipe(0).bits.cacheIndex).totalNeurons
      controlRespPipe(1).bits.data(2) := UInt(0) // Unused
    }
    is (e_CACHE_LAYER_INFO) {
      // Number of neurons in this layer
      controlRespPipe(1).bits.data(0) :=
        compressedLayers(controlRespPipe(0).bits.cacheIndex)(controlRespPipe(0).bits.data(0)(log2Up(elementsPerBlock)-1,0))(12 + 9, 12)
      // Number of neurons in the next layer
      controlRespPipe(1).bits.data(1) :=
        compressedLayers(controlRespPipe(0).bits.cacheIndex)(controlRespPipe(0).bits.data(0)(log2Up(elementsPerBlock)-1,0))(22 + 9, 22)
      // Pointer to the first neuron
      controlRespPipe(1).bits.data(2) :=
        compressedLayers(controlRespPipe(0).bits.cacheIndex)(controlRespPipe(0).bits.data(0)(log2Up(elementsPerBlock)-1,0))(12 - 1, 0)
    }
  }

  // Handle requests from the Processing Element Table
  when (io.pe.req.valid) {
    // Generate a request to the cache-specific SRAM. We need to
    // generate a block address from the input cache byte address.
    // [TODO] This shift may be a source of bugs. Check to make sure
    // that it's being passed correctly.
    mem(io.pe.req.bits.cacheIndex).addr(0) :=
      io.pe.req.bits.cacheAddr >> (UInt(2 + log2Up(elementsPerBlock)))
    // Fill the first stage of the PE pipeline
    switch (io.pe.req.bits.field) {
      is (e_CACHE_NEURON) {
        peRespPipe(0).valid := Bool(true)
        peRespPipe(0).bits.peIndex := io.pe.req.bits.peIndex
        peRespPipe(0).bits.field := e_CACHE_NEURON
        peRespPipe(0).bits.indexIntoData :=
          io.pe.req.bits.cacheAddr(2 + log2Up(elementsPerBlock) - 1, 3)
      }
      is (e_CACHE_WEIGHT) {
        peRespPipe(0).valid := Bool(true)
        peRespPipe(0).bits.peIndex := io.pe.req.bits.peIndex
        peRespPipe(0).bits.field := e_CACHE_WEIGHT
        peRespPipe(0).bits.indexIntoData :=
          io.pe.req.bits.cacheAddr(2 + log2Up(elementsPerBlock) - 1, 2)
      }
    }
  }

  // The actual data that comes out of the memory is not interpreted
  // here, i.e., we just pass the full bitsPerBlock-sized data packet
  // to the PE Table and it deals with the internal indices.
  peRespPipe(1).bits.data := mem(peCacheIndex_d0).dout(0)

  // Set the response to the Control module
  io.control.resp.valid := controlRespPipe(1).valid
  io.control.resp.bits := controlRespPipe(1).bits
  // Set the response to the Processing Element Table
  io.pe.resp.valid := peRespPipe(1).valid
  io.pe.resp.bits := peRespPipe(1).bits

  // Assertions
  assert(!io.control.resp.valid || io.control.resp.ready,
    "Cache trying to send response to Control when Control not ready")
  assert(!io.control.req.valid || !io.pe.req.valid || !io.mem.req.valid,
    "Multiple simultaneous requests on the cache (dropped requests possible)")
}
