package dana

import Chisel._

class ProcessingElementReq(
  val activationFunctionWidth: Int,
  val steepnessWidth: Int,
  val decimalPointWidth: Int,
  val elementWidth: Int,
  val elementsPerBlock: Int
) extends Bundle {
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

class ProcessingElementResp(
  val elementWidth: Int
) extends Bundle {
  // Not included:
  //   * next_state
  //   * invalidate_inputs
  val data = UInt(OUTPUT, elementWidth)
}

class ProcessingElementInterface(
  val elementWidth: Int,
  val elementsPerBlock: Int,
  val decimalPointWidth: Int,
  val steepnessWidth: Int,
  val activationFunctionWidth: Int
) extends Bundle {
  // Parameters
  val decimalPoint = UInt(INPUT, decimalPointWidth)
  val steepness = UInt(INPUT, steepnessWidth)
  val activationFunction = UInt(INPUT, activationFunctionWidth)
  val validIn = Bool(INPUT)
  val dataIn = Vec.fill(elementsPerBlock){SInt(INPUT, elementWidth)}
  val weight = Vec.fill(elementsPerBlock){SInt(INPUT, elementWidth)}
  val dataOut = SInt(OUTPUT, elementWidth)
  val validOut = Bool(OUTPUT)
}

class ProcessingElement(
  val elementWidth: Int = 32,
  val elementsPerBlock: Int = 4,
  val decimalPointOffset: Int = 7,
  val decimalPointWidth: Int = 3,
  val steepnessWidth: Int = 3,
  val activationFunctionWidth: Int = 5
) extends DanaModule {
  val io = new ProcessingElementInterface(
    elementWidth = elementWidth,
    elementsPerBlock = elementsPerBlock,
    decimalPointWidth = decimalPointWidth,
    steepnessWidth = steepnessWidth,
    activationFunctionWidth = activationFunctionWidth)
  val index = Reg(init = UInt(10))
  val acc = Reg(init = SInt(0, width = elementWidth))
  val dataOut = Reg(init = SInt(0, width = elementWidth))

  // Initial version, just a multiplier
  val s_unallocated :: s_mul :: s_af :: s_af_wait :: s_done :: Nil =
    Enum(UInt(), 5)

  val state = Reg(init = s_unallocated)

  // Default values
  io.dataOut := UInt(0, width = elementWidth)
  io.validOut := Bool(false)
  acc := acc

  // State transitions
  when (state === s_unallocated) {
    when (io.validIn) {
      state := s_mul
    }
  }
  when (state === s_mul) {
    when (index === UInt(elementsPerBlock - 1)) {
      state := s_af
    }
  }
  when (state === s_af) {
    state := s_af_wait
  }
  // [TODO] Kludge: The activation function is currently a 1-cycle
  // operation, but this is intended to use a decoupled interface.
  // When this switches to the decoupled interface, this will need to
  // be modified.
  when (state === s_af_wait) {
    state := s_done
  }
  when (state === s_done) {
    state := s_unallocated
  }

  // Non-state sequential logic
  when (state === s_unallocated) {
    acc := SInt(0, width = elementWidth)
    index := UInt(0)
  }
  when (state === s_mul) {
    acc := acc + ((io.dataIn(index) * io.weight(index)) >>
      (io.decimalPoint) >> UInt(decimalPointOffset))
    index := index + UInt(1)
  }
  when (state === s_af) {
    acc := acc
  }
  // when (state === s_af_wait) {
  //   acc :=
  // }
  when (state === s_done) {
    io.dataOut := dataOut
    io.validOut := Bool(true)
  }

  // Submodule instantiation
  val af = Module(new ActivationFunction(
    elementWidth = elementWidth,
    decimalPointOffset = decimalPointOffset,
    decimalPointWidth = decimalPointWidth,
    steepnessWidth = steepnessWidth))
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
