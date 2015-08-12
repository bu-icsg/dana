package dana

import Chisel._

class ProcessingElementReq extends DanaBundle {
  // I'm excluding, potentially temporarily:
  //   * state
  //   * pe_selected
  //   * is_first
  //   * reg_file_ready
  val numWeights = UInt(INPUT, width = 8) // [TODO] fragile
  val index = UInt(INPUT)
  val decimalPoint = UInt(INPUT, decimalPointWidth)
  val steepness = UInt(INPUT, steepnessWidth)
  val activationFunction = UInt(INPUT, activationFunctionWidth)
  val errorFunction = UInt(INPUT, width = log2Up(2)) // [TODO] fragile
  val bias = SInt(INPUT, elementWidth)
  val iBlock = Vec.fill(elementsPerBlock){SInt(INPUT, elementWidth)}
  val wBlock = Vec.fill(elementsPerBlock){SInt(INPUT, elementWidth)}
  val learnReg = SInt(INPUT, elementWidth)
  val stateLearn = UInt(width = log2Up(7)) // [TODO] fragile
  val inLast = Bool()
}

class ProcessingElementResp extends DanaBundle {
  // Not included:
  //   * next_state
  //   * invalidate_inputs
  val data = UInt(width = elementWidth)
  val state = UInt() // [TODO] fragile on PE state enum
  val index = UInt()
  val delta = SInt(width = elementWidth)
  val error = SInt(width = elementWidth)
}

class ProcessingElementInterface extends DanaBundle {
  // The Processing Element Interface consists of three main
  // components: requests from the PE Table (really kicks to do
  // something), responses to the PE Table, and semi-static data which
  // th PE Table manages and is used by the PEs for computation.
  val req = Decoupled(new ProcessingElementReq).flip
  val resp = Decoupled(new ProcessingElementResp)
}

class ProcessingElement extends DanaModule {
  // Interface to the PE Table
  val io = new ProcessingElementInterface

  // Activation Function module
  val af = Module(new ActivationFunction)

  val index = Reg(UInt(width = log2Up(elementsPerBlock)))
  val acc = Reg(SInt(width = elementWidth))
  val dataOut = Reg(SInt(width = elementWidth))
  val derivative = Reg(SInt(width = elementWidth)) //delta
  val errorOut = Reg(SInt(width = elementWidth)) //ek
  val mse = Reg(UInt(width = elementWidth))

  // [TODO] fragile on PE stateu enum (Common.scala)
  val state = Reg(UInt(), init = e_PE_UNALLOCATED)

  // Local state storage. Any and all of these are possible kludges
  // which could be implemented more cleanly.
  val hasBias = Reg(Bool())


  // Default values
  acc := acc
  io.req.ready := Bool(false)
  io.resp.valid := Bool(false)
  io.resp.bits.state := state
  io.resp.bits.index := io.req.bits.index
  io.resp.bits.data := dataOut
  io.resp.bits.delta := derivative
  io.resp.bits.error := errorOut
  index := index
  af.io.req.valid := Bool(false)

  // State-driven logic
  switch (state) {
    is (e_PE_UNALLOCATED) {
      when (io.req.bits.stateLearn === e_TTABLE_STATE_FEEDFORWARD ||
      io.req.bits.stateLearn === e_TTABLE_STATE_LEARN_FEEDFORWARD) {
        state := e_PE_GET_INFO
      } .elsewhen (io.req.bits.stateLearn === e_TTABLE_STATE_LEARN_ERROR_BACKPROP) {
        state := e_PE_GET_INFO_ERROR_BACKPROP
      }
      state := Mux(io.req.valid, e_PE_GET_INFO, state)
      io.req.ready := Bool(true)
      index := UInt(0)
      hasBias := Bool(false)
    }
    is (e_PE_GET_INFO) {
      state := Mux(io.req.valid, e_PE_WAIT_FOR_INFO, state)
      io.resp.valid := Bool(true)
    }
    is (e_PE_WAIT_FOR_INFO) {
      state := Mux(io.req.valid, e_PE_REQUEST_INPUTS_AND_WEIGHTS, state)
    }
    is (e_PE_REQUEST_INPUTS_AND_WEIGHTS) {
      state := Mux(io.req.valid, e_PE_WAIT_FOR_INPUTS_AND_WEIGHTS, state)
      io.resp.valid := Bool(true)
      // If hasBias is false, then this is the first time we're in this
      // state and we need to load the bias into the accumulator
      when (hasBias === Bool(false)) {
        hasBias := Bool(true)
        acc := io.req.bits.bias
      }
    }
    is (e_PE_WAIT_FOR_INPUTS_AND_WEIGHTS) {
      state := Mux(io.req.valid, e_PE_RUN, state)
    }
    is (e_PE_RUN) {
      // [TOOD] This logic is broken for some reason
      when (index === (io.req.bits.numWeights - UInt(1))) {
        state := e_PE_ACTIVATION_FUNCTION
      } .elsewhen (index === UInt(elementsPerBlock - 1)) {
        state := e_PE_REQUEST_INPUTS_AND_WEIGHTS
      } .otherwise {
        state := state
      }
      acc := acc + ((io.req.bits.iBlock(index) * io.req.bits.wBlock(index)) >>
        (io.req.bits.decimalPoint + UInt(decimalPointOffset, width = decimalPointWidth + 1)))(elementWidth,0)
      index := index + UInt(1)
    }
    is (e_PE_ACTIVATION_FUNCTION) {
      af.io.req.valid := Bool(true)
      state := Mux(af.io.resp.valid, Mux(io.req.bits.inLast &&
        io.req.bits.stateLearn === e_TTABLE_STATE_LEARN_FEEDFORWARD,
        e_PE_REQUEST_EXPECTED_OUTPUT, e_PE_DONE), state)
      af.io.req.bits.afType := e_AF_DO_ACTIVATION_FUNCTION
    }
    is (e_PE_REQUEST_EXPECTED_OUTPUT) {
      state := Mux(io.req.valid, e_PE_WAIT_FOR_EXPECTED_OUTPUT, state)
      io.resp.valid := Bool(true)
    }
    is (e_PE_WAIT_FOR_EXPECTED_OUTPUT) {
      state := Mux(io.req.valid, e_PE_COMPUTE_ERROR, state)
    }
    is (e_PE_COMPUTE_ERROR) {
      errorOut := (af.io.resp.bits.out - io.req.bits.learnReg)>>UInt(1)
      mse := ((af.io.resp.bits.out - io.req.bits.learnReg)>>UInt(1))*((af.io.resp.bits.out - io.req.bits.learnReg)>>UInt(1))
      printf("[INFO] PE: errorOut and Error square set to 0x%x and  0x%x\n",
        (af.io.resp.bits.out - io.req.bits.learnReg)>>UInt(1), ((af.io.resp.bits.out - io.req.bits.learnReg)>>UInt(1))*((af.io.resp.bits.out - io.req.bits.learnReg)>>UInt(1)))
      state := e_PE_COMPUTE_ERROR_WRITE_BACK
    }
    is(e_PE_COMPUTE_ERROR_WRITE_BACK){
      state := Mux(io.req.valid, e_PE_DONE, state)
      io.resp.valid := Bool(true)
    }
    is (e_PE_DONE) {
      state := Mux(io.req.valid, e_PE_UNALLOCATED, state)
      io.resp.valid := Bool(true)
    }

    is (e_PE_GET_INFO_ERROR_BACKPROP) {
      state := Mux(io.req.valid, e_PE_WAIT_FOR_INFO_ERROR_BACKPROP, state)
      io.resp.valid := Bool(true)
    }
    is (e_PE_WAIT_FOR_INFO_ERROR_BACKPROP) {
      state := Mux(io.req.valid, e_PE_REQUEST_INPUTS_AND_WEIGHTS_ERROR_BACKPROP, state)
    }
    is (e_PE_REQUEST_INPUTS_AND_WEIGHTS_ERROR_BACKPROP) {
      state := Mux(io.req.valid, e_PE_WAIT_FOR_INPUTS_AND_WEIGHTS_ERROR_BACKPROP, state)
      io.resp.valid := Bool(true)
    }
    is (e_PE_WAIT_FOR_INPUTS_AND_WEIGHTS_ERROR_BACKPROP) {
      state := Mux(io.req.valid, e_PE_RUN_ERROR_BACKPROP, state)
    }
    is (e_PE_RUN_ERROR_BACKPROP) {
      // [TOOD] This logic is broken for some reason
      when (index === (io.req.bits.numWeights - UInt(1))) {
        state := e_PE_ACTIVATION_FUNCTION_ERROR_BACKPROP
      } .elsewhen (index === UInt(elementsPerBlock - 1)) {
        state := e_PE_REQUEST_INPUTS_AND_WEIGHTS_ERROR_BACKPROP
      } .otherwise {
        state := state
      }
      //outer product of weight matrix and delta
      io.resp.bits.delta := SInt(0)
      errorOut := errorOut +
      ((io.req.bits.iBlock(index) * io.req.bits.wBlock(index)) >>
        (io.req.bits.decimalPoint +
          UInt(decimalPointOffset, width = decimalPointWidth + 1)))(elementWidth,0)
      index := index + UInt(1)
    }
  }

  af.io.req.bits.in := acc
  af.io.req.bits.decimal := io.req.bits.decimalPoint
  af.io.req.bits.steepness := io.req.bits.steepness
  af.io.req.bits.afType := e_AF_DO_ACTIVATION_FUNCTION
  af.io.req.bits.activationFunction := io.req.bits.activationFunction
  af.io.req.bits.errorFunction := io.req.bits.errorFunction
  dataOut := af.io.resp.bits.out
}

// [TODO] This whole testbench is broken due to the integration with
// the PE Table
class ProcessingElementTests(uut: ProcessingElement, isTrace: Boolean = true)
    extends DanaTester(uut, isTrace) {
  // Helper functions
  def getDecimal(): Int = {
    peek(uut.io.req.bits.decimalPoint).intValue + uut.decimalPointOffset }
  def getSteepness(): Int = {
    peek(uut.io.req.bits.steepness).intValue - 4 }
  def fixedConvert(data: Bits): Double = {
    peek(data).floatValue() / Math.pow(2, getDecimal) }
  def fixedConvert(data: Int): Double = {
    data.floatValue() / Math.pow(2, getDecimal) }
  def activationFunction(af: Int, data: Bits, steepness: Int = 1): Double = {
    val x = fixedConvert(data)
    // printf("[INFO]   af:   %d\n", af)
    // printf("[INFO]   data: %f\n", x)
    // printf("[INFO]   stee: %d\n", steepness)
    af match {
      // FANN_THRESHOLD
      case 1 => if (x > 0) 1 else 0
      // FANN_THRESHOLD_SYMMETRIC
      case 2 => if (x > 0) 1 else if (x == 0) 0 else -1
      // FANN_SIGMOID
      case 3 => 1 / (1 + Math.exp(-Math.pow(2, steepness.toFloat) * x / 0.5))
      case 4 => 1 / (1 + Math.exp(-Math.pow(2, steepness.toFloat) * x / 0.5))
      // FANN_SIGMOID_SYMMETRIC
      case 5 => 2 / (1 + Math.exp(-Math.pow(2, steepness.toFloat) * x / 0.5)) - 1
      case 6 => 2 / (1 + Math.exp(-Math.pow(2, steepness.toFloat) * x / 0.5)) - 1
      case _ => 0
    }
  }

  val dataIn = Array.fill(uut.elementsPerBlock){0}
  val weight = Array.fill(uut.elementsPerBlock){0}
  var correct = 0
  printf("[INFO] Sigmoid Activation Function Test\n")
  for (t <- 0 until 100) {
    // poke(uut.io.req.bits.decimalPoint, 3)
    poke(uut.io.req.bits.decimalPoint, rnd.nextInt(8))
    // poke(uut.io.req.bits.steepness, 4)
    poke(uut.io.req.bits.steepness, rnd.nextInt(8))
    // poke(uut.io.req.bits.activationFunction, 5)
    poke(uut.io.req.bits.activationFunction, rnd.nextInt(5) + 1)
    correct = 0
    for (i <- 0 until uut.elementsPerBlock) {
      dataIn(i) = rnd.nextInt(Math.pow(2, getDecimal + 2).toInt) -
        Math.pow(2, getDecimal + 1).toInt
      weight(i) = rnd.nextInt(Math.pow(2, getDecimal + 2).toInt) -
        Math.pow(2, getDecimal + 1).toInt
      correct = correct + (dataIn(i) * weight(i) >> getDecimal)
      // printf("[INFO] In(%d): %f, %f\n", i, fixedConvert(dataIn(i)),
      //   fixedConvert(weight(i)))
    }
    // printf("[INFO] Correct Acc: %f\n", fixedConvert(correct))
    for (i <- 0 until uut.elementsPerBlock) {
      poke(uut.io.req.bits.iBlock(i), dataIn(i))
      poke(uut.io.req.bits.wBlock(i), weight(i))
    }
    poke(uut.io.req.valid, 1)
    step(1)
    poke(uut.io.req.valid, 0)
    while(peek(uut.io.resp.valid) == 0) {
      step(1)
      // printf("[INFO]   acc: %f\n", fixedConvert(uut.acc))
    }
    // printf("[INFO] Acc:         %f\n", fixedConvert(uut.acc))
    // printf("[INFO] Output:      %f\n", fixedConvert(uut.io.req.bitsOut))
    // printf("[INFO] AF Good:     %f\n",
    //   activationFunction(peek(uut.io.activationFunction).toInt, uut.acc,
    //     getSteepness))
    expect(uut.acc, correct)
    expect(Math.abs(fixedConvert(uut.io.resp.bits.data) -
      activationFunction(peek(uut.io.req.bits.activationFunction).toInt, uut.acc,
        getSteepness)) < 0.1, "Activation Function Check")
    step(1)
  }
}
