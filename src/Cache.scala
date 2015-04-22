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

// [TODO] This needs to be moved to the PE or PE Table once those are
// ready
class PECacheInterface extends DanaBundle()() {
  // Inbound request from the PEs. nnsim-hdl equivalent:
  //   pe_types::pe2storage_struct
  val req = Decoupled(new DanaBundle()() {
    val accessType = UInt(width = 1) // [TODO] fragile on Constants.scala
    val peIndex = UInt(width = log2Up(peTableNumEntries))
    val cacheIndex = UInt(width = log2Up(cacheNumEntries))
    val cacheAddr = UInt(width =
      log2Up(cacheNumBlocks * elementsPerBlock * elementWidth))
  }).flip
  // Outbound response to PEs. nnsim-hdl equivalent:
  //   pe_types::storage2pe_struct
  val resp = Decoupled(new DanaBundle()() {
    val accessType = UInt(width = 1) // [TODO] fragile on Constants.scala
    val peIndex = UInt(width = log2Up(peTableNumEntries))
    val data = UInt(width = elementWidth * elementsPerBlock)
    val indexIntoData = UInt(width = elementsPerBlock)
  })
}

class CacheInterface extends Bundle {
  // The cache is connected to memory (technically via the arbiter
  // when this gets added), the control unit, and to the processing
  // elements
  val mem = new CacheMemInterface
  val control = (new ControlCacheInterface).flip
  val pe = new PECacheInterface
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

  // Helper functions for examing the cache entries
  def isFree(x: CacheState): Bool = {!x.valid}
  def isUnused(x: CacheState): Bool = {x.inUseCount === UInt(0)}
  def derefNnid(x: CacheState, y: UInt): Bool = {x.nnid === y}

  // State that we need to derive from the cache
  val hasFree = Bool()
  val hasUnused = Bool()
  val nextFree = UInt()
  val foundNnid = Bool()
  hasFree := table.exists(isFree)
  hasUnused := table.exists(isUnused)
  nextFree := table.indexWhere(isFree)
  foundNnid := table.exists(derefNnid(_, io.control.req.bits.nnid))

  io.control.req.ready := hasFree | hasUnused

  // Default values
  io.mem.req.valid := Bool(false)
  io.mem.req.bits.nnid := UInt(0)
  io.mem.req.bits.tTableIndex := UInt(0)
  io.mem.req.bits.cacheIndex := UInt(0)

  // Handle requests from the control module
  when (io.control.req.valid) {
    switch (io.control.req.bits.request) {
      is (CACHE_LOAD) {
        when (!foundNnid) {
          // Reserve the new cache entry
          table(nextFree).valid := Bool(true)
          table(nextFree).nnid := io.control.req.bits.nnid
          table(nextFree).notifyIndex := io.control.req.bits.tableIndex

          // Generate a request to memory
          io.mem.req.valid := Bool(true)
          io.mem.req.bits.nnid := io.control.req.bits.nnid
          io.mem.req.bits.tTableIndex := io.control.req.bits.tableIndex
          io.mem.req.bits.cacheIndex := nextFree
        }
          .otherwise {
        }
      }
      is (CACHE_LAYER_INFO) {

      }
      is (CACHE_DECREMENT_IN_USE_COUNT) {

      }
    }
  }
}
