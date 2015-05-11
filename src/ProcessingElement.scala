package dana

import Chisel._

class ProcessingElementReq extends DanaBundle()() {
  // I'm excluding, potentially temporarily:
  //   * state
  //   * pe_selected
  //   * is_first
  //   * reg_file_ready
  val numWeights = UInt(INPUT, 8) // [TODO] fragile
  val activationFunction = UInt(INPUT, activationFunctionWidth)
  val steepness = UInt(INPUT, steepnessWidth)
  val decimalPoint = UInt(INPUT, decimalPointWidth)
  val neuronPointer = UInt(INPUT, 12) // [TODO] fragile
  val bias = UInt(INPUT, elementWidth)
  val data = Vec.fill(elementsPerBlock){SInt(INPUT, elementWidth)}
  val weights = Vec.fill(elementsPerBlock){SInt(INPUT, elementWidth)}
}

class ProcessingElementResp extends DanaBundle()() {
  // Not included:
  //   * next_state
  //   * invalidate_inputs
  val data = UInt(OUTPUT, elementWidth)
  val state = UInt()
  val index = UInt()
}

class ProcessingElementData extends DanaBundle()() {
  val index = UInt()
  val decimalPoint = UInt(INPUT, decimalPointWidth)
  val steepness = UInt(INPUT, steepnessWidth)
  val activationFunction = UInt(INPUT, activationFunctionWidth)
  val iBlock = Vec.fill(elementsPerBlock){SInt(INPUT, elementWidth)}
  val wBlock = Vec.fill(elementsPerBlock){SInt(INPUT, elementWidth)}
}

class ProcessingElementInterface extends DanaBundle()() {
  // The Processing Element Interface consists of three main
  // components: requests from the PE Table (really kicks to do
  // something), responses to the PE Table, and semi-static data which
  // th PE Table manages and is used by the PEs for computation.
  val req = Decoupled(new ProcessingElementReq).flip
  val resp = Decoupled(new ProcessingElementResp)
  val data = new ProcessingElementData
}

class ProcessingElement extends DanaModule()() {
  val io = new ProcessingElementInterface
  val index = Reg(init = UInt(10))
  val acc = Reg(init = SInt(0, width = elementWidth))
  val dataOut = Reg(init = SInt(0, width = elementWidth))

  val state = Reg(init = e_PE_UNALLOCATED)

  // Default values
  io.dataOut := UInt(0, width = elementWidth)
  io.validOut := Bool(false)
  acc := acc
  io.req.ready := Bool(false)
  io.resp.valid := Bool(false)
  io.resp.bits.state := state
  io.resp.bits.index := io.data.index

  // State-driven logic
  when (state === e_PE_UNALLOCTED) {
    state := Mux(io.req.valid, e_PE_GET_INFO, state)
    io.req.ready := Bool(true)
    acc := UInt(0)
    index := UInt(0)
  } .elsewhen (state === e_PE_GET_INFO) {
    state := Mux(io.req.valid, e_PE_WAIT_FOR_INFO, state)
    io.resp.valid := Bool(true)
  } .elsewhen (state === e_PE_WAIT_FOR_INFO) {
    state := Mux(io.req.valid, e_PE_REQUEST_INPUTS_AND_WEIGHTS, state)
  } .elsewhen (state === e_PE_REQUEST_INPUTS_AND_WEIGHTS) {
    state := Mux(io.req.valid, e_PE_WAIT_FOR_INPUTS_AND_WEIGHTS, state)
    io.resp.valid := Bool(true)
  } .elsewhen (state === e_PE_WAIT_FOR_INPUTS_AND_WEIGHTS) {
    state := Mux(io.req.valid, e_PE_RUN, state)
  } .elsewhen (state === e_PE_RUN) {
    // [TODO] This needs state transition logic
    acc := acc + ((io.dataIn(index) * io.weight(index)) >>
      (io.decimalPoint) >> UInt(decimalPointOffset))
    index := index + UInt(1)
  } .elsewhen (state === e_PE_DONE) {
    state := Mux(io.req.valid, e_PE_UNALLOCATED, state)
  } .otherwise {
    // [TODO] currently unused, should this fire an assertion or
    // something?
  }

  // Submodule instantiation
  val af = Module(new ActivationFunction)
  af.io.req.bits.in := acc
  af.io.req.bits.decimal := io.decimalPoint
  af.io.req.bits.steepness := io.steepness
  af.io.req.bits.activationFunction := io.activationFunction
  dataOut := af.io.resp.bits.out

}

class ProcessingElementTests(uut: ProcessingElement, isTrace: Boolean = true)
    extends DanaTester(uut, isTrace) {
  // Helper functions
  def getDecimal(): Int = {
    peek(uut.io.decimalPoint).intValue + uut.decimalPointOffset }
  def getSteepness(): Int = {
    peek(uut.io.steepness).intValue - 4 }
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
    // poke(uut.io.decimalPoint, 3)
    poke(uut.io.decimalPoint, rnd.nextInt(8))
    // poke(uut.io.steepness, 4)
    poke(uut.io.steepness, rnd.nextInt(8))
    // poke(uut.io.activationFunction, 5)
    poke(uut.io.activationFunction, rnd.nextInt(5) + 1)
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
      poke(uut.io.dataIn(i), dataIn(i))
      poke(uut.io.weight(i), weight(i))
    }
    poke(uut.io.validIn, 1)
    step(1)
    poke(uut.io.validIn, 0)
    while(peek(uut.io.validOut) == 0) {
      step(1)
      // printf("[INFO]   acc: %f\n", fixedConvert(uut.acc))
    }
    // printf("[INFO] Acc:         %f\n", fixedConvert(uut.acc))
    // printf("[INFO] Output:      %f\n", fixedConvert(uut.io.dataOut))
    // printf("[INFO] AF Good:     %f\n",
    //   activationFunction(peek(uut.io.activationFunction).toInt, uut.acc,
    //     getSteepness))
    expect(uut.acc, correct)
    expect(Math.abs(fixedConvert(uut.io.dataOut) -
      activationFunction(peek(uut.io.activationFunction).toInt, uut.acc,
        getSteepness)) < 0.1, "Activation Function Check")
    step(1)
  }
}
