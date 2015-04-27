package dana

import Chisel._

class ControlCacheInterface extends DanaBundle()() {
  // Outbound request. nnsim-hdl equivalent:
  //   cach_types::ctl2storage_struct
  val req = Decoupled(new DanaBundle()() {
    val request = UInt(width = log2Up(3)) // [TODO] fragile on Constants.scala
    val nnid = UInt(width = nnidWidth)
    val tableIndex = UInt(width = log2Up(transactionTableNumEntries))
    val layer = UInt(width = 16) // [TODO] fragile
  })
  // Inbound response. nnsim-hdl equivalent:
  //   cache_types::cache2ctl_struct
  val resp = Decoupled(new DanaBundle()() {
    val fetch = Bool()
    val tableIndex = UInt(width = log2Up(transactionTableNumEntries))
    val tableMask = UInt(width = transactionTableNumEntries)
    val cacheIndex = UInt(width = log2Up(cacheNumEntries))
    val data = Vec.fill(3){UInt(width = 16)} // [TODO] possibly fragile
    val decimalPoint = UInt(INPUT, decimalPointWidth)
    val field = UInt(width = log2Up(4)) // [TODO] fragile on Constants.scala
  }).flip
}

class DanaControlInterface extends DanaBundle()() {
  val tTable = (new TTableDanaInterface).flip
  val cache = new ControlCacheInterface
}

class DanaControl extends DanaModule()() {
  val io = new DanaControlInterface

  // IO Driver Functions
  def reqCache(valid: Bool, request: UInt, nnid: UInt, tableIndex: UInt,
    layer: UInt) {
    io.cache.req.valid := valid
    io.cache.req.bits.request := request
    io.cache.req.bits.nnid := nnid
    io.cache.req.bits.tableIndex := tableIndex
    io.cache.req.bits.layer := layer
  }

  // io.tTable defaults
  io.tTable.req.ready := Bool(true)   // [TODO] Not correct
  io.tTable.resp.valid := Bool(false)
  io.tTable.resp.bits.tableIndex := UInt(0)
  io.tTable.resp.bits.field := UInt(0)
  io.cache.resp.ready := Bool(true) // [TODO] not correct
  // io.cache defaults
  reqCache(Bool(false), UInt(0), UInt(0), UInt(0), UInt(0))

  // No inbound requests, so we just handle whatever is valid coming
  // from the Transaction Table
  when (io.tTable.req.valid) {
    // Cache state is unknown and we're not waiting for the cache to
    // respond
    when (!io.tTable.req.bits.cacheValid &&
      !io.tTable.req.bits.waitingForCache) {
      // Send a request to the cache
      reqCache(Bool(true), CACHE_LOAD, io.tTable.req.bits.nnid,
        io.tTable.req.bits.tableIndex, UInt(0))

      // Send a response to the tTable
      io.tTable.resp.valid := Bool(true)
      io.tTable.resp.bits.tableIndex := io.tTable.req.bits.tableIndex
      io.tTable.resp.bits.field := TTABLE_WAITING_FOR_CACHE
    }
    when (io.tTable.req.bits.needsLayerInfo) {
      // Send a request to the storage module
    }
    when (io.tTable.req.bits.cacheValid &&
      io.tTable.req.bits.needsRegisters) {
      // Send a request to the register file
    }
    when (io.tTable.req.bits.cacheValid &&
      io.tTable.req.bits.needsNextRegister) {
      // Send a request to the register file
      io.tTable.resp.valid := Bool(true)
    }
    // when (io.tTable.req.bits.cacheValid &&
    //   !io.tTable.req.bits.needsRegisters &&
    //   // The PE Table has at least one free entry:
    //   // io.peTable.req.ready) {
    //   // Send a request to the PE Table for an assignment
    //   // Respond to the tTable
    // }
  }
}
