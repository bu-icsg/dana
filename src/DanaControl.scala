package dana

import Chisel._

class ControlCacheInterfaceResp extends DanaBundle()() {
  val fetch = Bool()
  val tableIndex = UInt(width = log2Up(transactionTableNumEntries))
  val tableMask = UInt(width = transactionTableNumEntries)
  val cacheIndex = UInt(width = log2Up(cacheNumEntries))
  val data = Vec.fill(3){UInt(width = 16)} // [TODO] possibly fragile
  val decimalPoint = UInt(INPUT, decimalPointWidth)
  val field = UInt(width = log2Up(4)) // [TODO] fragile on Constants.scala
}

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
  val resp = Decoupled(new ControlCacheInterfaceResp).flip
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
  io.tTable.resp.bits.data := Vec.fill(3){UInt(0)}
  io.tTable.resp.bits.decimalPoint := UInt(0)
  io.cache.resp.ready := Bool(true) // [TODO] not correct
  // io.cache defaults
  reqCache(Bool(false), UInt(0), UInt(0), UInt(0), UInt(0))

  // This is where we handle responses
  when (io.cache.resp.valid) {
    switch (io.cache.resp.bits.field) {
      is (e_CACHE_INFO) {
        io.tTable.resp.valid := Bool(true)
        io.tTable.resp.bits.field := e_TTABLE_CACHE_VALID
        io.tTable.resp.bits.data := io.cache.resp.bits.data
        io.tTable.resp.bits.decimalPoint := io.cache.resp.bits.decimalPoint
      }
      is (e_CACHE_LAYER) {
        io.tTable.resp.valid := Bool(true)
        io.tTable.resp.bits.field := e_TTABLE_LAYER // [TODO] may be wrong
        io.tTable.resp.bits.data := io.cache.resp.bits.data
      }
      // is (e_CACHE_NEURON) {
      //   io.tTable.resp.valid := Bool(true)
      //   io.tTable.resp.bits.field := e_CACHE_NEURON // [TODO] wrong
      // }
      // is (e_CACHE_WEIGHT) {
      //   io.tTable.resp.valid := Bool(true)
      //   io.tTable.resp.bits.field := e_CACHE_WEIGHT // [TODO] wrong
      // }
    }
  }

  // No inbound requests, so we just handle whatever is valid coming
  // from the Transaction Table
  when (io.tTable.req.valid) {
    // Cache state is unknown and we're not waiting for the cache to
    // respond
    when (!io.tTable.req.bits.cacheValid &&
      !io.tTable.req.bits.waiting) {
      // Send a request to the cache
      reqCache(Bool(true), e_CACHE_LOAD, io.tTable.req.bits.nnid,
        io.tTable.req.bits.tableIndex, UInt(0))

      // Send a response to the tTable
      io.tTable.resp.valid := Bool(true)
      io.tTable.resp.bits.tableIndex := io.tTable.req.bits.tableIndex
      io.tTable.resp.bits.field := e_TTABLE_WAITING
    }
    when (io.tTable.req.bits.cacheValid &&
      io.tTable.req.bits.needsLayerInfo) {
      // Send a request to the storage module
      reqCache(Bool(true), e_CACHE_LAYER_INFO, io.tTable.req.bits.nnid,
        io.tTable.req.bits.tableIndex, io.tTable.req.bits.currentLayer)

      // Tell the tTable to wait
      io.tTable.resp.valid := Bool(true)
      io.tTable.resp.bits.tableIndex := io.tTable.req.bits.tableIndex
      io.tTable.resp.bits.field := e_TTABLE_WAITING
      // io.tTable.resp.valid := Bool(true)
      // io.tTable.resp.bits.tableIndex := io.tTable.req.bits.tableIndex
      // io.tTable.resp.bits.field := e_TTABLE_
    }
    when (io.tTable.req.bits.cacheValid &&
      io.tTable.req.bits.needsRegisters) {

      // Tell the tTable to wait
      io.tTable.resp.valid := Bool(true)
      io.tTable.resp.bits.tableIndex := io.tTable.req.bits.tableIndex
      io.tTable.resp.bits.field := e_TTABLE_WAITING
      // Send a request to the register file
    }
    when (io.tTable.req.bits.cacheValid &&
      io.tTable.req.bits.needsNextRegister) {

      // Tell the tTable to wait
      io.tTable.resp.valid := Bool(true)
      io.tTable.resp.bits.tableIndex := io.tTable.req.bits.tableIndex
      io.tTable.resp.bits.field := e_TTABLE_WAITING
      // Send a request to the register file
      // io.tTable.resp.valid := Bool(true)
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
