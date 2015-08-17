package dana

import Chisel._

class PECacheInterfaceResp extends DanaBundle {
  val field = UInt(width = log2Up(3))
  val data = UInt(width = bitsPerBlock)
  val peIndex = UInt(width = log2Up(peTableNumEntries))
  val indexIntoData = UInt(width = elementsPerBlock) // [TODO] too big width?
}

class PECacheInterface extends DanaBundle {
  // Outbound request / inbound responses. These are roughly
  // equivalent to:
  //   * pe_types::pe2storage_struct
  //   * pe_types::storage2pe_struct
  val req = Decoupled(new DanaBundle {
    val field = UInt(width = log2Up(3)) // [TODO] fragile on Cache Req Enum
    val peIndex = UInt(width = log2Up(peTableNumEntries))
    val cacheIndex = UInt(width = log2Up(cacheNumEntries))
    val cacheAddr = UInt(width =
      log2Up(cacheNumBlocks * elementsPerBlock * elementWidth))
  })
  val resp = Decoupled(new PECacheInterfaceResp).flip
}

class PERegisterFileInterface extends DanaBundle {
  // Outbound reguest / inbound response. nnsim-hdl equivalents:
  //   * pe_types::pe2reg_file_read_struct
  //   * pe_types::pe2reg_file_write_struct
  //   * pe_types::reg_file2pe_struct
  val req = Decoupled(new DanaBundle {
    // The register index should go down to the element level
    val isWrite = Bool()
    val addr = UInt(width = log2Up(regFileNumElements))
    val peIndex = UInt(width = log2Up(peTableNumEntries))
    val tIdx = UInt(width = log2Up(transactionTableNumEntries))
    val data = UInt(width = elementWidth)
    val location = UInt(width = 1)
    val reqType = UInt(width = log2Up(2)) // [TODO] Fragile on Dana.scala
  })
  val resp = Decoupled(new DanaBundle {
    // [TODO] I'm excluding valid_reg_mask as I think this is
    // unnecessary for the current asynchronous model.
    val peIndex = UInt(width = log2Up(peTableNumEntries))
    val data = UInt(width = bitsPerBlock)
    val reqType = UInt(width = log2Up(2)) // [TODO] Fragile on Dana.scala
  }).flip
}

class PETransactionTableInterfaceResp extends DanaBundle {
  val peIndex = UInt(width = log2Up(peTableNumEntries))
  val data = UInt(width = bitsPerBlock)
}

class PETableInterface extends DanaBundle {
  val control = (new ControlPETableInterface).flip
  val cache = new PECacheInterface
  val regFile = new PERegisterFileInterface
}

class ProcessingElementState extends DanaBundle {
  val infoValid = Bool()
  val weightValid = Bool()
  val inValid = Bool() // input_valid
  val tIdx = UInt(width = log2Up(transactionTableNumEntries)) // nn_index
  val cIdx = UInt(width = log2Up(cacheNumEntries)) // cache_index
  val inAddr = UInt(width = log2Up(regFileNumElements))
  val outAddr = UInt(width = log2Up(regFileNumElements))
  // [TODO] learn address may have multiple meanings: 1) this is the
  // current node in the layer and will be used to generate an
  // expected output request to the Register File
  val learnAddr = UInt(width = log2Up(regFileNumElements))
  val errAddr = UInt(width = log2Up(regFileNumElements))
  val location = UInt(width = 1)
  val neuronPtr = UInt(width = // neuron_pointer
    log2Up(elementWidth * elementsPerBlock * cacheNumBlocks))
  val weightPtr = UInt(width = // weight_pointer
    log2Up(elementWidth * elementsPerBlock * cacheNumBlocks))
  val weightPtrPermanent = UInt(width = // weight_pointer
    log2Up(elementWidth * elementsPerBlock * cacheNumBlocks))
  val decimalPoint = UInt(width = decimalPointWidth) // decimal_point
  val inBlock = UInt(width = bitsPerBlock) // input_block
  val weightBlock = UInt(width = bitsPerBlock) //weight_block
  val learnReg = UInt(width = elementWidth) // "learning register", multiuse
  val numWeights = UInt(width = 8) // [TODO] fragile
  val activationFunction = UInt(width = activationFunctionWidth)
  val errorFunction = UInt(width = log2Up(2)) // [TODO] fragile
  val steepness = UInt(width = steepnessWidth)
  val bias = UInt(width = elementWidth)
  val stateLearn = UInt(width = log2Up(7)) // [TODO] fragile
  val inLast = Bool()
  val inFirst = Bool()
}

class ProcessingElementTable extends DanaModule {
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
    pe(i).req.bits.errorFunction := table(i).errorFunction
    pe(i).req.bits.numWeights := table(i).numWeights
    pe(i).req.bits.bias := table(i).bias
    pe(i).req.bits.stateLearn := table(i).stateLearn
    pe(i).req.bits.inLast := table(i).inLast
    pe(i).req.bits.inFirst := table(i).inFirst
    // pe(i).validIn
    for (j <- 0 until elementsPerBlock) {
      pe(i).req.bits.iBlock(j) :=
        table(i).inBlock(elementWidth * (j + 1) - 1, elementWidth * j)
      pe(i).req.bits.wBlock(j) :=
        table(i).weightBlock(elementWidth * (j + 1) - 1, elementWidth * j)
    }
    pe(i).req.bits.learnReg := table(i).learnReg
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
  // Default values for PE connections
  for (i <- 0 until peTableNumEntries) {
    pe(i).req.valid := Bool(false)
  }
  // Default values for Register File interface
  io.regFile.req.valid := Bool(false)
  io.regFile.req.bits.isWrite := Bool(false)
  io.regFile.req.bits.addr := UInt(0)
  io.regFile.req.bits.peIndex := UInt(0)
  io.regFile.req.bits.tIdx := UInt(0)
  io.regFile.req.bits.data := UInt(0)
  io.regFile.req.bits.location := UInt(0)
  io.regFile.req.bits.reqType := UInt(0)
  io.regFile.resp.ready := Bool(true) // [TOOD] placeholder

  // Wire up all the processing elements
  for (i <- 0 until peTableNumEntries) {
  }

  // Deal with inbound requests from the Control module. If we see a
  // request, it can only mean one thing---we need to allocate a PE.
  when (io.control.req.valid) {
    table(nextFree).tIdx := io.control.req.bits.tIdx
    table(nextFree).cIdx := io.control.req.bits.cacheIndex
    table(nextFree).neuronPtr := io.control.req.bits.neuronPointer
    table(nextFree).decimalPoint := io.control.req.bits.decimalPoint
    table(nextFree).errorFunction := io.control.req.bits.errorFunction
    table(nextFree).stateLearn := io.control.req.bits.stateLearn
    table(nextFree).inLast := io.control.req.bits.inLast
    table(nextFree).inFirst := io.control.req.bits.inFirst
    table(nextFree).inAddr := io.control.req.bits.inAddr
    table(nextFree).outAddr := io.control.req.bits.outAddr
    table(nextFree).learnAddr := io.control.req.bits.learnAddr
    table(nextFree).errAddr := io.control.req.bits.errAddr
    table(nextFree).location := io.control.req.bits.location
    table(nextFree).numWeights := SInt(-1)
    table(nextFree).weightValid := Bool(false)
    table(nextFree).inValid := Bool(false)
    // [TODO] Kick the PE
    pe(nextFree).req.valid := Bool(true)
    printf("[INFO] PETable: Received control request...\n")
    printf("[INFO]   next free:  0x%x\n", nextFree);
    printf("[INFO]   tid idx:    0x%x\n", io.control.req.bits.tIdx)
    printf("[INFO]   cache idx:  0x%x\n", io.control.req.bits.cacheIndex)
    printf("[INFO]   neuron ptr: 0x%x\n", io.control.req.bits.neuronPointer)
    printf("[INFO]   decimal:    0x%x\n", io.control.req.bits.decimalPoint)
    printf("[INFO]   error func: 0x%x\n", io.control.req.bits.errorFunction)
    printf("[INFO]   in addr:    0x%x\n", io.control.req.bits.inAddr)
    printf("[INFO]   out addr:   0x%x\n", io.control.req.bits.outAddr)
    printf("[INFO]   learn addr: 0x%x\n", io.control.req.bits.learnAddr)
    when(io.control.req.bits.inLast === Bool(true) &&io.control.req.bits.stateLearn === e_TTABLE_STATE_LEARN_FEEDFORWARD){
      printf("[INFO]   Error addr: 0x%x\n", io.control.req.bits.errAddr)
    }
    printf("[INFO]   stateLearn: 0x%x\n", io.control.req.bits.stateLearn)
    printf("[INFO]   inLast:     0x%x\n", io.control.req.bits.inLast)
    printf("[INFO]   inFirst:     0x%x\n", io.control.req.bits.inFirst)
  }

  // Inbound requests from the cache. I setup some helper nodes here
  // that interpret the data coming from the cache.
  val cacheRespVec = Vec.fill(bitsPerBlock / 64){new CompressedNeuron}
  val indexIntoData = UInt()
  indexIntoData := UInt(0)
  (0 until cacheRespVec.length).map(i =>
    cacheRespVec(i).populate(io.cache.resp.bits.data((i+1) * cacheRespVec(i).getWidth - 1,
      i * cacheRespVec(i).getWidth), cacheRespVec(i)))
  // Deal with the cache response if one exists.
  when (io.cache.resp.valid) {
    val peIndex = io.cache.resp.bits.peIndex
    switch (io.cache.resp.bits.field) {
      is (e_CACHE_NEURON) {
        // [TODO] Fragile on increases to widthActivationFunction or
        // widthSteepness.
        indexIntoData := io.cache.resp.bits.indexIntoData
        table(peIndex).weightPtr := cacheRespVec(indexIntoData).weightPtr
        table(peIndex).weightPtrPermanent := cacheRespVec(indexIntoData).weightPtr
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
        when (table(peIndex).inValid) {
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

  // Deal with responses from the register file (should be register
  // file reads)
  when (io.regFile.resp.valid) {
    val peIndex = io.regFile.resp.bits.peIndex
    // [TODO] FixMe
    switch (io.regFile.resp.bits.reqType) {
      is (e_PE_REQ_INPUT) {
        table(peIndex).inBlock := io.regFile.resp.bits.data
        table(peIndex).inAddr := table(peIndex).inAddr + UInt(elementsPerBlock)
        when (table(peIndex).weightValid) {
          pe(peIndex).req.valid := Bool(true)
          table(peIndex).weightValid := Bool(false)
          table(peIndex).inValid := Bool(false)
        } .otherwise {
          table(peIndex).inValid := Bool(true)
        }
        printf("[INFO] PETable: Valid RegFile input resp PE/data 0x%x/0x%x\n",
          peIndex, io.regFile.resp.bits.data)
      }
      is (e_PE_REQ_EXPECTED_OUTPUT) {
        val addr = table(peIndex).learnAddr(log2Up(elementsPerBlock)-1,0)
        val dataVec = Vec((0 until elementsPerBlock).map(i =>
          (io.regFile.resp.bits.data)(elementWidth * (i + 1) - 1, elementWidth * i)))
        table(peIndex).learnReg := dataVec(addr)
        pe(peIndex).req.valid := Bool(true)
        printf("[INFO] PETable: Valid RegFile E[out] resp PE/data 0x%x/0x%x\n",
          peIndex, io.regFile.resp.bits.data)
        printf("[INFO]          learnReg -> dataVec(0x%x): 0x%x\n", addr,
          dataVec(addr))
      }
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
    peArbiter.io.in(i).bits.error := pe(i).resp.bits.error
    // block write
    //peArbiter.io.in(i).bits.uwBlock := pe(i).resp.bits.uwBlock
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
        // All requests are now routed through the Register File (the
        // intermediate storage area for all computation)
        io.regFile.req.valid := Bool(true)
        io.regFile.req.bits.isWrite := Bool(false) // unecessary to specify
        io.regFile.req.bits.addr := table(peArbiter.io.out.bits.index).inAddr
        io.regFile.req.bits.peIndex := peArbiter.io.out.bits.index
        io.regFile.req.bits.tIdx := table(peArbiter.io.out.bits.index).tIdx
        io.regFile.req.bits.location := table(peArbiter.io.out.bits.index).location
        io.regFile.req.bits.reqType := e_PE_REQ_INPUT

        // Send a request to the cache for weights
        io.cache.req.valid := Bool(true)
        io.cache.req.bits.field := e_CACHE_WEIGHT
        io.cache.req.bits.peIndex := peArbiter.io.out.bits.index
        io.cache.req.bits.cacheIndex := table(peArbiter.io.out.bits.index).cIdx
        io.cache.req.bits.cacheAddr := table(peArbiter.io.out.bits.index).weightPtr

        pe(peArbiter.io.out.bits.index).req.valid := Bool(true)
      }
      is (e_PE_REQUEST_EXPECTED_OUTPUT) {
        io.regFile.req.valid := Bool(true)
        io.regFile.req.bits.isWrite := Bool(false) // unecessary to specify
        io.regFile.req.bits.addr := table(peArbiter.io.out.bits.index).learnAddr
        io.regFile.req.bits.peIndex := peArbiter.io.out.bits.index
        io.regFile.req.bits.tIdx := table(peArbiter.io.out.bits.index).tIdx
        io.regFile.req.bits.location := table(peArbiter.io.out.bits.index).location
        io.regFile.req.bits.reqType := e_PE_REQ_EXPECTED_OUTPUT

        pe(peArbiter.io.out.bits.index).req.valid := Bool(true)
      }
      is(e_PE_COMPUTE_ERROR_WRITE_BACK){
        // Outputs are always written to the Register File
        io.regFile.req.valid := Bool(true)
        io.regFile.req.bits.isWrite := Bool(true)
        io.regFile.req.bits.addr := table(peArbiter.io.out.bits.index).errAddr
        io.regFile.req.bits.tIdx := table(peArbiter.io.out.bits.index).tIdx
        io.regFile.req.bits.data := peArbiter.io.out.bits.error
        io.regFile.req.bits.location := table(peArbiter.io.out.bits.index).location

        pe(peArbiter.io.out.bits.index).req.valid := Bool(true)
      }
      /*is (e_PE_REQUEST_DELTA_WEIGHT_UPDATE) {
        io.regFile.req.valid := Bool(true)
        io.regFile.req.bits.isWrite := Bool(false) // unecessary to specify
        io.regFile.req.bits.addr := table(peArbiter.io.out.bits.index).learnAddr
        io.regFile.req.bits.peIndex := peArbiter.io.out.bits.index
        io.regFile.req.bits.tIdx := table(peArbiter.io.out.bits.index).tIdx
        io.regFile.req.bits.location := table(peArbiter.io.out.bits.index).location
        io.regFile.req.bits.reqType := e_PE_REQ_EXPECTED_OUTPUT

        pe(peArbiter.io.out.bits.index).req.valid := Bool(true)
      }
      is(e_PE_WEIGHT_UPDATE_WRITE_BACK) {
        io.regFile.req.valid := Bool(true)
        io.regFile.req.bits.isWrite := Bool(true)
        io.regFile.req.bits.addr := table(peArbiter.io.out.bits.index).learnAddr
        io.regFile.req.bits.tIdx := table(peArbiter.io.out.bits.index).tIdx
        //[block write]
        io.regFile.req.bits.data := peArbiter.io.out.bits.uwBlock 
        io.regFile.req.bits.location := table(peArbiter.io.out.bits.index).location

        pe(peArbiter.io.out.bits.index).req.valid := Bool(true)
      } */
      is (e_PE_DONE) {
        // Outputs are always written to the Register File
        io.regFile.req.valid := Bool(true)
        io.regFile.req.bits.isWrite := Bool(true)
        io.regFile.req.bits.addr := table(peArbiter.io.out.bits.index).outAddr
        io.regFile.req.bits.tIdx := table(peArbiter.io.out.bits.index).tIdx
        io.regFile.req.bits.data := peArbiter.io.out.bits.data
        io.regFile.req.bits.location := table(peArbiter.io.out.bits.index).location

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
  assert(!(!io.control.req.ready && io.control.req.valid),
    "Cache received valid control request when not ready")
  // I'm using some kludgy logic spread across two when blocks to
  // handle kicking the PE when the inputs and weights are valid. The
  // way this is written, any PE Table entry should never be in a
  // state where its input and weight are valid, but it hasn't jumped
  // to state 5. Likewise, it shouldn't be in state 5

}
