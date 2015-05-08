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
  //   cache_types::ctl2storage_struct
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

class ControlPETableInterface extends DanaBundle()() {
  // Outbound request. nnsim-hdl equivalent:
  //   control_types::ctl2pe_table_struct
  val req = Decoupled(new DanaBundle()() {
    val peIndex = UInt(width = log2Up(peTableNumEntries))
    val cacheIndex = UInt(width = log2Up(cacheNumEntries))
    // new_state -- this should be unnecessary as all we need to do is
    // give the PE a kick, which should be accomplished with the
    // decoupled valid signal
    val tid = UInt(width = tidWidth)
    val neuronIndex = UInt(width = 10) // [TODO] fragile
    val locationInput = UInt()
    val locationOutput = UInt()
    val inputIndex = UInt(width = ioIdxWidth)
    val outputIndex = UInt(width = ioIdxWidth)
    val neuronPointer = UInt(width = 12) // [TODO] fragile
    val decimalPoint = UInt(width = decimalPointWidth)
  })
  // No response is necessary as the Control module needs to know is
  // if the PE Table has a free entry. This is communicated by means
  // of the Decoupled `ready` signal.
}

class ControlInterface extends DanaBundle()() {
  val tTable = (new TTableDanaInterface).flip
  val cache = new ControlCacheInterface
  val peTable = new ControlPETableInterface
}

class Control extends DanaModule()() {
  val io = new ControlInterface

  // IO Driver Functions
  def reqCache(valid: Bool, request: UInt, nnid: UInt, tableIndex: UInt,
    layer: UInt) {
    io.cache.req.valid := valid
    io.cache.req.bits.request := request
    io.cache.req.bits.nnid := nnid
    io.cache.req.bits.tableIndex := tableIndex
    io.cache.req.bits.layer := layer
  }
  def reqPETable(valid: Bool, peIndex: UInt, cacheIndex: UInt, tid: UInt,
    neuronIndex: UInt, locationInput: UInt, locationOutput: UInt,
    inputIndex: UInt, outputIndex: UInt, neuronPointer: UInt,
    decimalPoint: UInt) {
    io.peTable.req.valid := valid
    io.peTable.req.bits.peIndex := peIndex
    io.peTable.req.bits.cacheIndex := cacheIndex
    io.peTable.req.bits.tid := tid
    io.peTable.req.bits.neuronIndex := neuronIndex
    io.peTable.req.bits.locationInput := locationInput
    io.peTable.req.bits.locationOutput := locationOutput
    io.peTable.req.bits.inputIndex := inputIndex
    io.peTable.req.bits.outputIndex := outputIndex
    io.peTable.req.bits.neuronPointer := neuronPointer
    io.peTable.req.bits.decimalPoint := decimalPoint
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
  // io.petable defaults
  reqPETable(Bool(false), UInt(0), UInt(0), UInt(0), UInt(0), UInt(0), UInt(0),
    UInt(0), UInt(0), UInt(0), UInt(0))

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
    // [TODO] The register file now has dedicated storage for each
    // transaction, hence the need of this check to allocate registers
    // in a shared register file is no longer needed
    // when (io.tTable.req.bits.cacheValid &&
    //   io.tTable.req.bits.needsNextRegister) {
    //   // Tell the tTable to wait
    //   io.tTable.resp.valid := Bool(true)
    //   io.tTable.resp.bits.tableIndex := io.tTable.req.bits.tableIndex
    //   io.tTable.resp.bits.field := e_TTABLE_WAITING
    //   // Send a request to the register file
    //   // io.tTable.resp.valid := Bool(true)
    // }
    when (io.tTable.req.bits.cacheValid &&
      io.peTable.req.ready) {
      // Go ahead and allocate an entry in the Processing Element
      // Table
      // reqPETable(valid: Bool, peIndex: UInt, cacheIndex: UInt, tid: UInt,
      //   neuronIndex: UInt, locationInput: UInt, locationOutput: UInt,
      //   inputIndex: UInt, outputIndex: UInt, neuronPointer: UInt,
      //   decimalPoint: UInt)
      // [TODO] pickup here....
    }
  }
}
