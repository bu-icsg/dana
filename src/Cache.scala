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
    val transactionTableIndex = UInt(width = log2Up(transactionTableNumEntries))
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

class ControlCacheInterface extends DanaBundle()() {
  // Inbound request. nnsim-hdl equivalent:
  //   cach_types::ctl2storage_struct
  val req = Decoupled(new DanaBundle()() {
    val request = UInt(width = log2Up(3)) // [TODO] fragile on Constants.scala
    val nnid = UInt(width = nnidWidth)
    val tableIndex = UInt(width = log2Up(transactionTableNumEntries))
    val layer = UInt(width = 16) // [TODO] fragile
  }).flip
  // Outbound response. nnsim-hdl equivalent:
  //   cache_types::cache2ctl_struct
  val resp = Decoupled(new DanaBundle()() {
    val fetch = Bool()
    val tableIndex = UInt(width = log2Up(transactionTableNumEntries))
    val tableMask = UInt(width = transactionTableNumEntries)
    val cacheIndex = UInt(width = log2Up(cacheNumEntries))
    val data = Vec.fill(3){UInt(width = 16)} // [TODO] possibly fragile
    val decimalPoint = UInt(INPUT, decimalPointWidth)
    val field = UInt(width = log2Up(4)) // [TODO] fragile on Constants.scala
  })
}

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
  val control = new ControlCacheInterface
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
}
