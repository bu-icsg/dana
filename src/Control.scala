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
    // The PE Index shouldn't be needed if the PE Table is allocating PEs
    // val peIndex = UInt(width = log2Up(peTableNumEntries))
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
  val tTable = (new TTableControlInterface).flip
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
  def reqPETable(valid: Bool, cacheIndex: UInt, tid: UInt,
    neuronIndex: UInt, locationInput: UInt, locationOutput: UInt,
    inputIndex: UInt, outputIndex: UInt, neuronPointer: UInt,
    decimalPoint: UInt) {
    io.peTable.req.valid := valid
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
  reqPETable(Bool(false), UInt(0), UInt(0), UInt(0), UInt(0), UInt(0),
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
  .elsewhen (io.tTable.req.valid) {
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
      !io.tTable.req.bits.needsLayerInfo &&
      io.peTable.req.ready) {
      // Go ahead and allocate an entry in the Processing Element
      reqPETable(Bool(true), // valid
        // The specific cache entry where the NN configuration for
        // this PE is located
        io.tTable.req.bits.cacheIndex, // cacheIndex
        // The transaction ID
        io.tTable.req.bits.tid, // tid
        // The neuron index is just the current neuron in the layer
        // that's being processed. This information is redundant with
        // the output index
        io.tTable.req.bits.currentNodeInLayer, // neuronIndex
        // Clever input/output location determination
        Mux(io.tTable.req.bits.inFirst, e_LOCATION_IO,
          !io.tTable.req.bits.currentLayer(0)), // locationInput
        Mux(io.tTable.req.bits.inLast, e_LOCATION_IO,
          io.tTable.req.bits.currentLayer(0)), // locationOutput
        // The input index is always zero as we need to start reading
        // from the initial position of the IO Storage / Register File
        UInt(0), // inputIndex is always zero
        // The output index is simply the index of the current node
        // being processed
        io.tTable.req.bits.currentNodeInLayer, // outputIndex
        // The neuron pointer is going to be the base pointer that
        // lives in the Transaction Table plus an offset based on the
        // current node that we're processing. [TODO] I'm unsure why
        // I'm using the left shift by 3 and need to check how this is
        // being used for the cache lookup by the PE.
        io.tTable.req.bits.neuronPointer + // neuronPointer
          (io.tTable.req.bits.currentNodeInLayer << UInt(3)),
        // Pass along the decimal point
        io.tTable.req.bits.decimalPoint // decimalPoint
      )

      // Increment the Transaction Table Node as we've just initiated*
      // an assignment
      io.tTable.resp.valid := Bool(true)
      io.tTable.resp.bits.tableIndex := io.tTable.req.bits.tableIndex
      io.tTable.resp.bits.field := e_TTABLE_INCREMENT_NODE
    }
  }
}
