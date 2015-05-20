package dana

import Chisel._

class PECacheInterfaceResp extends DanaBundle()() {
  val field = UInt(width = log2Up(3))
  val data = UInt(width = bitsPerBlock)
  val peIndex = UInt(width = log2Up(peTableNumEntries))
  val indexIntoData = UInt(width = elementsPerBlock) // [TODO] too big width?
}

class PECacheInterface extends DanaBundle()() {
  // Outbound request / inbound responses. These are roughly
  // equivalent to:
  //   * pe_types::pe2storage_struct
  //   * pe_types::storage2pe_struct
  val req = Decoupled(new DanaBundle()() {
    val field = UInt(width = log2Up(3)) // [TODO] fragile on Cache Req Enum
    val peIndex = UInt(width = log2Up(peTableNumEntries))
    val cacheIndex = UInt(width = log2Up(cacheNumEntries))
    val cacheAddr = UInt(width =
      log2Up(cacheNumBlocks * elementsPerBlock * elementWidth))
  })
  val resp = Decoupled(new PECacheInterfaceResp).flip
}

class PERegisterFileInterface extends DanaBundle()() {
  // Outbound reguest / inbound response. nnsim-hdl equivalents:
  //   * pe_types::pe2reg_file_read_struct
  //   * pe_types::pe2reg_file_write_struct
  //   * pe_types::reg_file2pe_struct
  val req = Decoupled(new DanaBundle()() {
    // The register index should go down to the element level
    val isWrite = Bool()
    val regIndex = UInt(width = log2Up(regFileNumElements))
    val peIndex = UInt(width = log2Up(peTableNumEntries))
    val tIdx = UInt(width = log2Up(transactionTableNumEntries))
    val location = UInt(width = 1)
    val data = UInt(width = elementWidth)
  })
  val resp = Decoupled(new DanaBundle()() {
    // [TODO] I'm excluding valid_reg_mask as I think this is
    // unnecessary for the current asynchronous model.
    val peIndex = UInt(width = log2Up(peTableNumEntries))
    val data = UInt(width = bitsPerBlock)
  }).flip
}

class PETransactionTableInterfaceResp extends DanaBundle()() {
  val peIndex = UInt(width = log2Up(peTableNumEntries))
  val data = UInt(width = bitsPerBlock)
}

class PETransactionTableInterface extends DanaBundle()() {
  // Communication with the Transaction Table for some data from an IO
  // memory. SV equivalents:
  //   * pe2nn_table_read_struct
  //   * pe2nn_table_write_sruct
  //   * nntable2pe_struct
  val req = Decoupled(new DanaBundle()() {
    val isWrite = Bool()
    val peIndex = UInt(width = log2Up(peTableNumEntries))
    val tableIndex = UInt(width = log2Up(transactionTableNumEntries))
    val addr = UInt(width = log2Up(transactionTableSramElements))
    val data = UInt(width = elementWidth)
  })
  val resp = Decoupled(new PETransactionTableInterfaceResp).flip
}

class PETableInterface extends DanaBundle()() {
  val control = (new ControlPETableInterface).flip
  val cache = new PECacheInterface
  val regFile = new PERegisterFileInterface
  val tTable = new PETransactionTableInterface
}

class ProcessingElementState extends DanaBundle()() {
  val infoValid = Bool()
  val weightValid = Bool()
  val inValid = Bool() // input_valid
  val tid = UInt(width = tidWidth) // pid
  val tIdx = UInt(width = log2Up(transactionTableNumEntries)) // nn_index
  val cIdx = UInt(width = log2Up(cacheNumEntries)) // cache_index
  val nnNode = UInt(width = 10) // nn_node [TODO] fragile
  val outIdx = UInt(width = ioIdxWidth) // output_index
  val inIdx = UInt(width = ioIdxWidth) // input_index
  val neuronPtr = UInt(width = // neuron_pointer
    log2Up(elementWidth * elementsPerBlock * cacheNumBlocks))
  val weightPtr = UInt(width = // weight_pointer
    log2Up(elementWidth * elementsPerBlock * cacheNumBlocks))
  val decimalPoint = UInt(width = decimalPointWidth) // decimal_point
  val inLoc = UInt(width = 2) // input_location [TODO] fragile
  val outLoc = UInt(width = 2) // output_location [TODO] fragile
  val lastInLayer = Bool() // last_in_layer
  val inBlock = UInt(width = bitsPerBlock) // input_block
  val weightBlock = UInt(width = bitsPerBlock) //weight_block
  val numWeights = UInt(width = 8) // [TODO] fragile
  val activationFunction = UInt(width = activationFunctionWidth)
  val steepness = UInt(width = steepnessWidth)
  val bias = UInt(width = elementWidth)
}

class ProcessingElementTable extends DanaModule()() {
  val io = new PETableInterface

  // Create the table with the specified top-level parameters. Derived
  // parameters should not be touched.
  val table = Vec.fill(peTableNumEntries){Reg(new ProcessingElementState)}
  // Create the processing elements
  val pe = Vec.fill(peTableNumEntries){Module (new ProcessingElement()).io}

  // Wire up the PE data connections
  for (i <- 0 until peTableNumEntries) {
    pe(i).req.bits.index := UInt(i)
    pe(i).req.bits.decimalPoint := table(i).decimalPoint
    pe(i).req.bits.steepness := table(i).steepness
    pe(i).req.bits.activationFunction := table(i).activationFunction
    pe(i).req.bits.numWeights := table(i).numWeights
    pe(i).req.bits.bias := table(i).bias
    // pe(i).validIn
    for (j <- 0 until elementsPerBlock) {
      pe(i).req.bits.iBlock(j) :=
        table(i).inBlock(elementWidth * (j + 1) - 1, elementWidth * j)
      pe(i).req.bits.wBlock(j) :=
        table(i).weightBlock(elementWidth * (j + 1) - 1, elementWidth * j)
    }
  }

  def isFree(x: ProcessingElementInterface): Bool = { x.req.ready }
  val hasFree = Bool()
  val nextFree = UInt()
  hasFree := pe.exists(isFree)
  nextFree := pe.indexWhere(isFree)

  io.control.req.ready := hasFree

  // Default values for Cache interface
  io.cache.req.valid := Bool(false)
  io.cache.req.bits.field := UInt(0)
  io.cache.req.bits.peIndex := UInt(0)
  io.cache.req.bits.cacheIndex := UInt(0)
  io.cache.req.bits.cacheAddr := UInt(0)
  io.cache.resp.ready := Bool(true) // [TODO] placeholder
  // Default values for TTable interface
  io.tTable.req.valid := Bool(false)
  io.tTable.req.bits.isWrite := Bool(false)
  io.tTable.req.bits.peIndex := UInt(0)
  io.tTable.req.bits.tableIndex := UInt(0)
  io.tTable.req.bits.addr := UInt(0)
  io.tTable.req.bits.data := UInt(0)
  io.tTable.resp.ready := Bool(true) // [TODO] placeholder
  // Default values for PE connections
  for (i <- 0 until peTableNumEntries) {
    pe(i).req.valid := Bool(false)
  }
  // Default values for Register File interface
  io.regFile.req.valid := Bool(false)
  io.regFile.req.bits.isWrite := Bool(false)
  io.regFile.req.bits.regIndex := UInt(0)
  io.regFile.req.bits.peIndex := UInt(0)
  io.regFile.req.bits.tIdx := UInt(0)
  io.regFile.req.bits.location := UInt(0)
  io.regFile.req.bits.data := UInt(0)
  io.regFile.resp.ready := Bool(true) // [TOOD] placeholder

  // Wire up all the processing elements
  for (i <- 0 until peTableNumEntries) {
  }

  // Deal with inbound requests from the Control module. If we see a
  // request, it can only mean one thing---we need to allocate a PE.
  when (io.control.req.valid) {
    table(nextFree).tid := io.control.req.bits.tid
    table(nextFree).cIdx := io.control.req.bits.cacheIndex
    table(nextFree).nnNode := io.control.req.bits.neuronIndex
    table(nextFree).inLoc := io.control.req.bits.locationInput
    table(nextFree).outLoc := io.control.req.bits.locationOutput
    table(nextFree).neuronPtr := io.control.req.bits.neuronPointer
    table(nextFree).weightPtr := SInt(-1)
    table(nextFree).decimalPoint := io.control.req.bits.decimalPoint
    table(nextFree).inIdx := io.control.req.bits.inputIndex
    table(nextFree).outIdx := io.control.req.bits.outputIndex
    table(nextFree).lastInLayer := Bool(false) // [TODO] not sure about this
    table(nextFree).inBlock := SInt(-1)
    table(nextFree).weightBlock := SInt(-1)
    table(nextFree).numWeights := SInt(-1)
    table(nextFree).activationFunction := SInt(-1)
    table(nextFree).steepness := SInt(-1)
    table(nextFree).bias := UInt(0)
    table(nextFree).weightValid := Bool(false)
    table(nextFree).inValid := Bool(false)
    // [TODO] Kick the PE
    pe(nextFree).req.valid := Bool(true)
  }

  // Inbound requests from the cache. I setup some helper nodes here
  // that interpret the data coming from the cache.
  val cacheRespVec = Vec.fill(bitsPerBlock / 64){new CompressedNeuron}
  val peIndex = UInt()
  val indexIntoData = UInt()
  peIndex := UInt(0)
  indexIntoData := UInt(0)
  (0 until cacheRespVec.length).map(i =>
    cacheRespVec(i).populate(io.cache.resp.bits.data((i+1) * cacheRespVec(i).getWidth - 1,
      i * cacheRespVec(i).getWidth), cacheRespVec(i)))
  // Deal with the cache response if one exists.
  when (io.cache.resp.valid) {
    peIndex := io.cache.resp.bits.peIndex
    switch (io.cache.resp.bits.field) {
      is (e_CACHE_NEURON) {
        // [TODO] Fragile on increases to widthActivationFunction or
        // widthSteepness.
        indexIntoData := io.cache.resp.bits.indexIntoData
        table(peIndex).weightPtr := cacheRespVec(indexIntoData).weightPtr
        table(peIndex).numWeights :=
          cacheRespVec(indexIntoData).numWeights + UInt(elementsPerBlock)
        table(peIndex).activationFunction :=
          cacheRespVec(indexIntoData).activationFunction
        table(peIndex).steepness := cacheRespVec(indexIntoData).steepness
        table(peIndex).bias := cacheRespVec(indexIntoData).bias
        pe(peIndex).req.valid := Bool(true)
      }
      is (e_CACHE_WEIGHT) {
        table(peIndex).weightPtr :=
          table(peIndex).weightPtr + UInt(elementsPerBlock * elementWidth / 8)
        table(peIndex).weightBlock := io.cache.resp.bits.data
        table(peIndex).numWeights :=
          Mux(table(peIndex).numWeights < UInt(elementsPerBlock),
            UInt(0), table(peIndex).numWeights - UInt(elementsPerBlock))
        // Kick the PE if the input is valid or will be valid.
        when (table(peIndex).inValid || io.tTable.resp.valid) {
          pe(peIndex).req.valid := Bool(true)
          table(peIndex).weightValid := Bool(false)
          table(peIndex).inValid := Bool(false)
        } .otherwise {
          table(peIndex).weightValid := Bool(true)
        }
      }
    }
    // pe(io.cache.resp.bits.peIndex).req.valid := Bool(true)
  }

  // Deal with any responses from the Transaction Table
  when (io.tTable.resp.valid) {
    table(io.tTable.resp.bits.peIndex).inBlock := io.tTable.resp.bits.data
    table(io.tTable.resp.bits.peIndex).inIdx :=
      table(io.tTable.resp.bits.peIndex).inIdx + UInt(elementsPerBlock)
    when (table(io.tTable.resp.bits.peIndex).weightValid) {
      pe(io.tTable.resp.bits.peIndex).req.valid := Bool(true)
      table(io.tTable.resp.bits.peIndex).weightValid := Bool(false)
      table(io.tTable.resp.bits.peIndex).inValid := Bool(false)
    } .otherwise {
      table(io.tTable.resp.bits.peIndex).inValid := Bool(true)
    }
  }

  // Deal with responses from the register file (should be register
  // file reads)
  when (io.regFile.resp.valid) {
    table(io.regFile.resp.bits.peIndex).inBlock := io.regFile.resp.bits.data
    table(io.regFile.resp.bits.peIndex).inIdx :=
      table(io.regFile.resp.bits.peIndex).inIdx + UInt(elementsPerBlock)
    when (table(io.regFile.resp.bits.peIndex).weightValid) {
      pe(io.regFile.resp.bits.peIndex).req.valid := Bool(true)
      table(io.regFile.resp.bits.peIndex).weightValid := Bool(false)
      table(io.regFile.resp.bits.peIndex).inValid := Bool(false)
    } .otherwise {
      table(io.regFile.resp.bits.peIndex).inValid := Bool(true)
    }
  }

  // Round robin arbitration of PE Table entries
  val peArbiter = Module(new RRArbiter(new ProcessingElementResp,
    peTableNumEntries))
  // Wire up the arbiter
  for (i <- 0 until peTableNumEntries) {
    peArbiter.io.in(i).valid := pe(i).resp.valid
    peArbiter.io.in(i).bits.data := pe(i).resp.bits.data
    peArbiter.io.in(i).bits.state := pe(i).resp.bits.state
    peArbiter.io.in(i).bits.index := pe(i).resp.bits.index
  }

  // If the arbiter is showing a valid output, then we have to
  // generate some requests based on which PE the arbiter has
  // determined needs something. The action taken depends on the state
  // that we're seeing from the chosen PE.
  when (peArbiter.io.out.valid) {
    switch (peArbiter.io.out.bits.state) {
      is (e_PE_GET_INFO) {
        // Send a request to the cache for information.
        io.cache.req.valid := Bool(true)
        io.cache.req.bits.field := e_CACHE_NEURON
        io.cache.req.bits.peIndex := peArbiter.io.out.bits.index
        io.cache.req.bits.cacheIndex := table(peArbiter.io.out.bits.index).cIdx
        io.cache.req.bits.cacheAddr := table(peArbiter.io.out.bits.index).neuronPtr
        pe(peArbiter.io.out.bits.index).req.valid := Bool(true)
      }
      is (e_PE_REQUEST_INPUTS_AND_WEIGHTS) {
        // Send a request to the Transaction Table (IO Storage area)
        // or to the Register File to pull out this data
        when (table(peArbiter.io.out.bits.index).inLoc === e_LOCATION_IO) {
          io.tTable.req.valid := Bool(true)
          io.tTable.req.bits.isWrite := Bool(false)
          io.tTable.req.bits.peIndex := peArbiter.io.out.bits.index
          io.tTable.req.bits.tableIndex := table(peArbiter.io.out.bits.index).tIdx
          io.tTable.req.bits.addr := table(peArbiter.io.out.bits.index).inIdx
        } .otherwise {
          // [TODO] untested
          io.regFile.req.valid := Bool(true)
          io.regFile.req.bits.isWrite := Bool(false) // unecessary to specify
          io.regFile.req.bits.regIndex := table(peArbiter.io.out.bits.index).inIdx
          io.regFile.req.bits.peIndex := peArbiter.io.out.bits.index
          io.regFile.req.bits.tIdx := table(peArbiter.io.out.bits.index).tIdx
          io.regFile.req.bits.location := table(peArbiter.io.out.bits.index).inLoc
        }

        // Send a request to the cache for weights
        io.cache.req.valid := Bool(true)
        io.cache.req.bits.field := e_CACHE_WEIGHT
        io.cache.req.bits.peIndex := peArbiter.io.out.bits.index
        io.cache.req.bits.cacheIndex := table(peArbiter.io.out.bits.index).cIdx
        io.cache.req.bits.cacheAddr := table(peArbiter.io.out.bits.index).weightPtr
        pe(peArbiter.io.out.bits.index).req.valid := Bool(true)
      }
      is (e_PE_DONE) {
        // Send the output value where it needs to go, based on
        // location
        when (table(peArbiter.io.out.bits.index).outLoc === e_LOCATION_IO) {
          // [TODO] This is untested
          io.tTable.req.valid := Bool(true)
          io.tTable.req.bits.isWrite := Bool(true)
          io.tTable.req.bits.tableIndex := table(peArbiter.io.out.bits.index).tIdx
          io.tTable.req.bits.addr := table(peArbiter.io.out.bits.index).outIdx
          io.tTable.req.bits.data := peArbiter.io.out.bits.data
        } .otherwise { // The location is the register file
          io.regFile.req.valid := Bool(true)
          io.regFile.req.bits.isWrite := Bool(true)
          io.regFile.req.bits.regIndex := table(peArbiter.io.out.bits.index).outIdx
          io.regFile.req.bits.tIdx := table(peArbiter.io.out.bits.index).tIdx
          io.regFile.req.bits.location := table(peArbiter.io.out.bits.index).outLoc
          io.regFile.req.bits.data := peArbiter.io.out.bits.data
        }
        pe(peArbiter.io.out.bits.index).req.valid := Bool(true)
      }
    }
    // Kick the selected PE so that it jumps to a wait state
    // [TODO] This should be enabled once _all_ of the states are
    // being properly handled
    // pe(peArbiter.io.out.bits.index).req.valid := Bool(true)
  }

  // Assertions

  // Inbound control requests should only happen if there are free
  // entries in the PE Table
  assert( !io.control.req.valid || hasFree,
    "Control module trying to assign PE, but no free entries in PE Table")
  // I'm using some kludgy logic spread across two when blocks to
  // handle kicking the PE when the inputs and weights are valid. The
  // way this is written, any PE Table entry should never be in a
  // state where its input and weight are valid, but it hasn't jumped
  // to state 5. Likewise, it shouldn't be in state 5

  // [TODO] Debug info to be removed
  for (i <- 0 until peTableNumEntries) {
    debug(table(i).tid)
    debug(table(i).cIdx)
    debug(table(i).nnNode)
    debug(table(i).inIdx)
    debug(table(i).outIdx)
    debug(table(i).neuronPtr)
    debug(table(i).weightPtr)
    debug(table(i).decimalPoint)
    debug(table(i).inLoc)
    debug(table(i).outLoc)
    debug(table(i).lastInLayer)
    debug(table(i).inBlock)
    debug(table(i).weightBlock)
    debug(table(i).numWeights)
    debug(table(i).activationFunction)
    debug(table(i).steepness)
    debug(table(i).bias)
  }
}
