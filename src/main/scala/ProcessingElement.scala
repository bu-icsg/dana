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
  val stateLearn = UInt(INPUT, width = log2Up(7)) // [TODO] fragile
  val inLast = Bool(INPUT)
  val inFirst = Bool(INPUT)
  val dw_in = SInt(INPUT, elementWidth)
}

class ProcessingElementResp extends DanaBundle {
  // Not included:
  //   * next_state
  //   * invalidate_inputs
  val data = UInt(width = elementWidth)
  val dataBlock = Vec.fill(elementsPerBlock){SInt(width = elementWidth)}
  val state = UInt() // [TODO] fragile on PE state enum
  val index = UInt()
  val error = SInt(width = elementWidth)
  val incWriteCount = Bool()
  // val uwBlock = Vec.fill(elementsPerBlock){SInt(elementWidth)}
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

  val index = Reg(UInt(width = 8)) // [TODO] fragile, should match numWeights
  val acc = Reg(SInt(width = elementWidth))
  val weightWB = Vec.fill(elementsPerBlock){Reg(SInt(width=elementWidth))}
  val dataOut = Reg(SInt(width = elementWidth))
  val derivative = Reg(SInt(width = elementWidth)) //delta
  val errorOut = Reg(SInt(width = elementWidth)) //ek
  val mse = Reg(UInt(width = elementWidth))
  // val updated_weight = Vec.fill(elementsPerBlock){Reg(SInt(INPUT, elementWidth))}
  val derivativeSigmoidSymmetric = SInt(width = elementWidth)

  // [TODO] fragile on PE stateu enum (Common.scala)
  val state = Reg(UInt(), init = PE_states('e_PE_UNALLOCATED))

  // Local state storage. Any and all of these are possible kludges
  // which could be implemented more cleanly.
  val hasBias = Reg(Bool())
  val steepness = UInt(width = elementWidth)
  val decimal = UInt(decimalPointOffset, width = decimalPointWidth + 1) + io.req.bits.decimalPoint

  def applySteepness(x: SInt, steepness: UInt): SInt = {
    val tmp = SInt()
    when (steepness < UInt(steepnessOffset)) {
      tmp := x >> (UInt(steepnessOffset) - steepness)
    } .elsewhen (steepness === UInt(steepnessOffset)) {
      tmp := x
    } .otherwise {
      tmp := x << (steepness - UInt(steepnessOffset))
    }
    tmp
  }

  // Default values
  acc := acc
  derivative := derivative
  derivativeSigmoidSymmetric := applySteepness(
    (SInt(1) << decimal) - (dataOut * dataOut >> decimal)(elementWidth-1,0),
    io.req.bits.steepness)
  io.req.ready := Bool(false)
  io.resp.valid := Bool(false)
  io.resp.bits.state := state
  io.resp.bits.index := io.req.bits.index
  io.resp.bits.data := dataOut
  io.resp.bits.dataBlock := weightWB
  io.resp.bits.error := errorOut
  io.resp.bits.incWriteCount := Bool(false)
  // io.resp.bits.uwBlock := updated_weight
  index := index
  // Activation function unit default values
  af.io.req.valid := Bool(false)
  af.io.req.bits.in := UInt(0)
  af.io.req.bits.decimal := io.req.bits.decimalPoint
  af.io.req.bits.steepness := io.req.bits.steepness
  af.io.req.bits.afType := e_AF_DO_ACTIVATION_FUNCTION
  af.io.req.bits.activationFunction := io.req.bits.activationFunction
  af.io.req.bits.errorFunction := io.req.bits.errorFunction

  // State-driven logic
  switch (state) {
    is (PE_states('e_PE_UNALLOCATED)) {
      state := Mux(io.req.valid, PE_states('e_PE_GET_INFO), state)
      io.req.ready := Bool(true)
      hasBias := Bool(false)
    }
    is (PE_states('e_PE_GET_INFO)) {
      dataOut := UInt(0)
      state := Mux(io.req.valid, PE_states('e_PE_WAIT_FOR_INFO), state)
      io.resp.valid := Bool(true)
      index := UInt(0)
    }
    is (PE_states('e_PE_WAIT_FOR_INFO)) {
      //state := Mux(io.req.valid, PE_states('e_PE_REQUEST_INPUTS_AND_WEIGHTS), state)
      when (io.req.valid && (io.req.bits.stateLearn === e_TTABLE_STATE_FEEDFORWARD ||
      io.req.bits.stateLearn === e_TTABLE_STATE_LEARN_FEEDFORWARD)) {
        state := PE_states('e_PE_REQUEST_INPUTS_AND_WEIGHTS)
      } .elsewhen (io.req.valid && (io.req.bits.stateLearn === e_TTABLE_STATE_LEARN_ERROR_BACKPROP)) {
        state := PE_states('e_PE_REQUEST_OUTPUTS_ERROR_BACKPROP)
      } .elsewhen (io.req.valid &&
        (io.req.bits.stateLearn === e_TTABLE_STATE_LEARN_WEIGHT_UPDATE)) {
        state := PE_states('e_PE_WEIGHT_UPDATE_REQUEST_DELTA)
      } .otherwise{
        state := state
      }
    }
    is (PE_states('e_PE_REQUEST_INPUTS_AND_WEIGHTS)) {
      state := Mux(io.req.valid, PE_states('e_PE_WAIT_FOR_INPUTS_AND_WEIGHTS), state)
      io.resp.valid := Bool(true)
      // If hasBias is false, then this is the first time we're in this
      // state and we need to load the bias into the accumulator
      when (hasBias === Bool(false)) {
        hasBias := Bool(true)
        acc := io.req.bits.bias
      }
      // index := UInt(0)
    }
    is (PE_states('e_PE_WAIT_FOR_INPUTS_AND_WEIGHTS)) {
      state := Mux(io.req.valid, Mux(io.req.bits.stateLearn === e_TTABLE_STATE_LEARN_WEIGHT_UPDATE ||
        (io.req.bits.stateLearn === e_TTABLE_STATE_LEARN_ERROR_BACKPROP),
       PE_states('e_PE_RUN_WEIGHT_UPDATE), PE_states('e_PE_RUN)), state)
      //state := Mux(io.req.valid, PE_states('e_PE_RUN), state)
    }
    is (PE_states('e_PE_RUN)) {
      val blockIndex = index(log2Up(elementsPerBlock) - 1, 0)
      // [TOOD] This logic is broken for some reason
      when (index === (io.req.bits.numWeights - UInt(1))) {
        state := PE_states('e_PE_ACTIVATION_FUNCTION)
      } .elsewhen (blockIndex === UInt(elementsPerBlock - 1)) {
        state := PE_states('e_PE_REQUEST_INPUTS_AND_WEIGHTS)
      } .otherwise {
        state := state
      }
      acc := acc + ((io.req.bits.iBlock(blockIndex) * io.req.bits.wBlock(blockIndex)) >>
        (io.req.bits.decimalPoint + UInt(decimalPointOffset, width = decimalPointWidth + 1)))(elementWidth-1,0)
      index := index + UInt(1)
    }
    is (PE_states('e_PE_ACTIVATION_FUNCTION)) {
      af.io.req.valid := Bool(true)
      af.io.req.bits.in := acc
      af.io.req.bits.afType := e_AF_DO_ACTIVATION_FUNCTION
      state := Mux(af.io.resp.valid, Mux(io.req.bits.inLast &&
        io.req.bits.stateLearn === e_TTABLE_STATE_LEARN_FEEDFORWARD,
        PE_states('e_PE_COMPUTE_DERIVATIVE), PE_states('e_PE_DONE)), state)
      dataOut := Mux(af.io.resp.valid, af.io.resp.bits.out, dataOut)
    }
    is (PE_states('e_PE_COMPUTE_DERIVATIVE)){
      state := Mux((io.req.bits.stateLearn === e_TTABLE_STATE_LEARN_ERROR_BACKPROP),
        PE_states('e_PE_COMPUTE_DELTA), PE_states('e_PE_REQUEST_EXPECTED_OUTPUT))

      switch (io.req.bits.activationFunction) {
        // [TODO] negative shifts, probably broken!
        printf("[INFO] PE: decimal point is 0x%x\n", decimal)
        is(e_FANN_LINEAR) {
          derivative := SInt(1) >> (decimal + UInt(steepnessOffset) - io.req.bits.steepness)
          printf("[INFO] PE: derivative and steepness is set to 0x%x and 0x%x\n", steepness, steepness)
        }
        is(e_FANN_SIGMOID) {
          derivative := (dataOut * ((SInt(1) << decimal) - dataOut)) >> (decimal + UInt(steepnessOffset) - io.req.bits.steepness - UInt(1))
          printf("[INFO] PE: derivative is set to 0x%x\n",
            ((dataOut * ((SInt(1) << decimal) - dataOut)) >> (decimal + UInt(steepnessOffset) - io.req.bits.steepness - UInt(1))))
        }
        is(e_FANN_SIGMOID_STEPWISE) {
          derivative := (dataOut * ((SInt(1) << decimal) - dataOut)) >> (decimal + UInt(steepnessOffset) - io.req.bits.steepness - UInt(1))
          printf("[INFO] PE: derivative is set to 0x%x\n",
            (((dataOut * ((SInt(1) << decimal) - dataOut)) >> (decimal + UInt(steepnessOffset) - io.req.bits.steepness - UInt(1)))))
        }
        is(e_FANN_SIGMOID_SYMMETRIC) {
          // [TODO] shift by steepness possibly broken if "steepness" is negative
          derivative := derivativeSigmoidSymmetric
          printf("[INFO] PE: derivative is set to 0x%x\n",
            derivativeSigmoidSymmetric)
        }
        is(e_FANN_SIGMOID_SYMMETRIC_STEPWISE) {
          derivative := derivativeSigmoidSymmetric
          printf("[INFO] PE: derivative is set to 0x%x\n",
            derivativeSigmoidSymmetric)
        }
      }
    }
    is (PE_states('e_PE_REQUEST_EXPECTED_OUTPUT)) {
      state := Mux(io.req.valid, PE_states('e_PE_WAIT_FOR_EXPECTED_OUTPUT), state)
      io.resp.valid := Bool(true)
    }
    is (PE_states('e_PE_WAIT_FOR_EXPECTED_OUTPUT)) {
      state := Mux(io.req.valid, PE_states('e_PE_COMPUTE_ERROR), state)
    }
    is (PE_states('e_PE_COMPUTE_ERROR)) {
      // Divide by 2 should be conditional on being in a symmetric
      when (io.req.bits.activationFunction === e_FANN_SIGMOID_SYMMETRIC ||
        io.req.bits.activationFunction === e_FANN_SIGMOID_SYMMETRIC_STEPWISE) {
        errorOut := (dataOut - io.req.bits.learnReg) >> UInt(1)
        mse := (((dataOut - io.req.bits.learnReg) >> UInt(1)) * ((dataOut - io.req.bits.learnReg) >> UInt(1))) >> decimal
        printf("[INFO] PE: errorOut and Error square set to 0x%x and  0x%x\n",
          (dataOut - io.req.bits.learnReg) >> UInt(1),
          (((dataOut - io.req.bits.learnReg) >> UInt(1)) * ((dataOut - io.req.bits.learnReg) >> UInt(1))) >> decimal)
      } .otherwise {
        errorOut := dataOut - io.req.bits.learnReg
        mse := (((dataOut - io.req.bits.learnReg)) * ((dataOut - io.req.bits.learnReg))) >> decimal
        printf("[INFO] PE: errorOut and Error square set to 0x%x and  0x%x\n",
          dataOut - io.req.bits.learnReg,
          (((dataOut - io.req.bits.learnReg)) * ((dataOut - io.req.bits.learnReg))) >> decimal)
      }
      state := PE_states('e_PE_ERROR_FUNCTION)
    }
    is (PE_states('e_PE_ERROR_FUNCTION)) {
      af.io.req.valid := Bool(true)
      af.io.req.bits.in := errorOut
      af.io.req.bits.afType := e_AF_DO_ERROR_FUNCTION
      state := Mux(af.io.resp.valid, PE_states('e_PE_COMPUTE_DELTA), state)
      errorOut := Mux(af.io.resp.valid, af.io.resp.bits.out, errorOut)
    }
    is (PE_states('e_PE_COMPUTE_DELTA)){
      // Reset the index in preparation for doing the deltas in the
      // previous layer
      switch(io.req.bits.stateLearn){
        is(e_TTABLE_STATE_LEARN_FEEDFORWARD){
          errorOut := (derivative * errorOut) >> decimal
          printf("[INFO] PE sees errFn/(errFn*derivative) 0x%x/0x%x\n", errorOut,
            (derivative * errorOut) >> decimal)
          state := PE_states('e_PE_DELTA_WRITE_BACK)
        }
        is(e_TTABLE_STATE_LEARN_ERROR_BACKPROP){
          errorOut := (derivative * io.req.bits.dw_in) >> decimal
          printf("[INFO] PE sees delta 0x%x\n",
            (derivative * io.req.bits.dw_in) >> decimal)
          when (io.req.bits.inFirst) {
            state := PE_states('e_PE_WEIGHT_UPDATE_REQUEST_INPUTS)
          } .otherwise {
            state := PE_states('e_PE_DELTA_WRITE_BACK)
          }
        }
      }
      // [TODO] Check if this is in the first layer and we're doing
      // error backprop. If we aren't, then write back the delta to
      // the register file.
      index := UInt(0)
    }
    is(PE_states('e_PE_DELTA_WRITE_BACK)){
      // This is the "last" writeback for a group, so we turn on the
      // `incWriteCount` flag to tell the Register File to increment its write
      // count
      when((io.req.bits.stateLearn === e_TTABLE_STATE_LEARN_ERROR_BACKPROP)){
        state := Mux(io.req.valid,PE_states('e_PE_ERROR_BACKPROP_REQUEST_WEIGHTS), state)
      }.otherwise {
        state := Mux(io.req.valid, Mux(io.req.bits.inLast &&
          io.req.bits.stateLearn === e_TTABLE_STATE_LEARN_FEEDFORWARD,
          PE_states('e_PE_ERROR_BACKPROP_REQUEST_WEIGHTS), PE_states('e_PE_DONE)), state)
      }
      io.resp.bits.incWriteCount := Bool(true)
      io.resp.valid := Bool(true)
    }
    is (PE_states('e_PE_ERROR_BACKPROP_REQUEST_WEIGHTS)) {
      // index := UInt(0)
      state := Mux(io.req.valid, PE_states('e_PE_ERROR_BACKPROP_WAIT_FOR_WEIGHTS),
        state)
      io.resp.valid := Bool(true)
    }
    is (PE_states('e_PE_ERROR_BACKPROP_WAIT_FOR_WEIGHTS)) {
      state := Mux(io.req.valid, Mux(io.req.bits.inFirst &&
        (io.req.bits.stateLearn === e_TTABLE_STATE_LEARN_ERROR_BACKPROP),
        PE_states('e_PE_WEIGHT_UPDATE_REQUEST_INPUTS),
        PE_states('e_PE_ERROR_BACKPROP_DELTA_WEIGHT_MUL)), state)
    }
    is (PE_states('e_PE_ERROR_BACKPROP_DELTA_WEIGHT_MUL)) {
      val blockIndex = index(log2Up(elementsPerBlock) - 1, 0)
      // Loop over all the weights in the weight buffer, multiplying
      // these by their delta
      when (index === (io.req.bits.numWeights - UInt(1)) ||
        blockIndex === UInt(elementsPerBlock - 1)) {
        state := PE_states('e_PE_ERROR_BACKPROP_WEIGHT_WB)
      }

      weightWB(blockIndex) := (errorOut * io.req.bits.wBlock(blockIndex)) >>
        (io.req.bits.decimalPoint +
          UInt(decimalPointOffset, width=decimalPointWidth +1))
      index := index + UInt(1)
    }
    is (PE_states('e_PE_ERROR_BACKPROP_WEIGHT_WB)) {
      val blockIndex = index(log2Up(elementsPerBlock) - 1, 0)
      io.resp.bits.incWriteCount := index === io.req.bits.numWeights
      when (io.req.valid) {
        when (index === io.req.bits.numWeights) {
          state := Mux((io.req.bits.stateLearn === e_TTABLE_STATE_LEARN_ERROR_BACKPROP),
            PE_states('e_PE_UNALLOCATED),PE_states('e_PE_DONE))
        } .otherwise {
          state := PE_states('e_PE_ERROR_BACKPROP_REQUEST_WEIGHTS)
        }
      }

      io.resp.valid := Bool(true)
    }
    is (PE_states('e_PE_REQUEST_OUTPUTS_ERROR_BACKPROP)) {
      state := Mux(io.req.valid, PE_states('e_PE_WAIT_FOR_OUTPUTS_ERROR_BACKPROP), state)
      io.resp.valid := Bool(true)
    }
    is (PE_states('e_PE_WAIT_FOR_OUTPUTS_ERROR_BACKPROP)) {
      state := Mux(io.req.valid, PE_states('e_PE_REQUEST_DELTA_WEIGHT_PRODUCT_ERROR_BACKPROP), state)
    }
    is (PE_states('e_PE_REQUEST_DELTA_WEIGHT_PRODUCT_ERROR_BACKPROP)) {
      dataOut := io.req.bits.learnReg
      state := Mux(io.req.valid, PE_states('e_PE_WAIT_FOR_DELTA_WEIGHT_PRODUCT_ERROR_BACKPROP), state)
      io.resp.valid := Bool(true)
    }
    is (PE_states('e_PE_WAIT_FOR_DELTA_WEIGHT_PRODUCT_ERROR_BACKPROP))
    {
      state := Mux(io.req.valid, PE_states('e_PE_COMPUTE_DERIVATIVE), state)
    }

    is (PE_states('e_PE_DONE)) {
      state := Mux(io.req.valid, PE_states('e_PE_UNALLOCATED), state)
      io.resp.bits.incWriteCount := Bool(true)
      io.resp.valid := Bool(true)
    }
    //not sure if we need a seperate state for this
    is (PE_states('e_PE_GET_INFO_WEIGHT_UPDATE)){
      state := Mux(io.req.valid, PE_states('e_PE_WAIT_FOR_INFO_WEIGHT_UPDATE), state)
      io.resp.valid := Bool(true)
    }
    //not sure if we need a seperate state for this
    is (PE_states('e_PE_WAIT_FOR_INFO_WEIGHT_UPDATE)){
      state := Mux(io.req.valid, PE_states('e_PE_WEIGHT_UPDATE_REQUEST_DELTA), state)
    }
    is (PE_states('e_PE_WEIGHT_UPDATE_REQUEST_DELTA)){
      state := Mux(io.req.valid, PE_states('e_PE_WEIGHT_UPDATE_WAIT_FOR_DELTA), state)
      io.resp.valid := Bool(true)
    }
    is (PE_states('e_PE_WEIGHT_UPDATE_WAIT_FOR_DELTA)){
      state := Mux(io.req.valid, PE_states('e_PE_WEIGHT_UPDATE_REQUEST_INPUTS), state)
    }

    is (PE_states('e_PE_WEIGHT_UPDATE_REQUEST_INPUTS)) {
      state := Mux(io.req.valid, PE_states('e_PE_WEIGHT_UPDATE_WAIT_FOR_INPUTS), state)
      io.resp.valid := Bool(true)
    }
    is (PE_states('e_PE_WEIGHT_UPDATE_WAIT_FOR_INPUTS)) {
      state := Mux(io.req.valid, PE_states('e_PE_RUN_WEIGHT_UPDATE), state)
    }

    is (PE_states('e_PE_RUN_WEIGHT_UPDATE)){
      val blockIndex = index(log2Up(elementsPerBlock) - 1, 0)
      when (index === (io.req.bits.numWeights - UInt(1)) ||
        blockIndex === UInt(elementsPerBlock - 1)) {
        state := PE_states('e_PE_WEIGHT_UPDATE_WRITE_BACK)
      } .otherwise {
        state := state
      }
      val delta = Mux(io.req.bits.inFirst, errorOut, io.req.bits.learnReg)
      weightWB(blockIndex):=
        ((delta * io.req.bits.iBlock(blockIndex)) >> decimal)(elementWidth-1, 0)
      index := index + UInt(1)
    }
    is (PE_states('e_PE_WEIGHT_UPDATE_WRITE_BACK)){
      val nextState = Mux(index === io.req.bits.numWeights,
        PE_states('e_PE_UNALLOCATED),
        PE_states('e_PE_WEIGHT_UPDATE_REQUEST_INPUTS))
      io.resp.bits.incWriteCount := index === io.req.bits.numWeights
      state := Mux(io.req.valid, nextState, state)
      io.resp.valid := Bool(true)
    }
  }

  assert (!(state === PE_states('e_PE_ERROR)), "[ERROR] PE is in error state\n")
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
