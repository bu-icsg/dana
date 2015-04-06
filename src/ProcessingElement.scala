package dana

import Chisel._

class ProcessingElementInterface(
  val elementWidth: Int,
  val elementsPerBlock: Int,
  val decimalPointWidth: Int,
  val steepnessWidth: Int
) extends Bundle {
  // Parameters
  val decimalPoint = UInt(INPUT, decimalPointWidth)
  val steepness = UInt(INPUT, steepnessWidth)
  val validIn = Bool(INPUT)
  val dataIn = Vec.fill(elementsPerBlock){UInt(INPUT, elementWidth)}
  val weight = Vec.fill(elementsPerBlock){UInt(INPUT, elementWidth)}
  val dataOut = SInt(OUTPUT, elementWidth)
  val validOut = Bool(OUTPUT)
}

class ProcessingElement(
  val elementWidth: Int,
  val elementsPerBlock: Int,
  val decimalPointOffset: Int,
  val decimalPointWidth: Int,
  val steepnessWidth: Int
) extends Module {
  val io = new ProcessingElementInterface(elementWidth, elementsPerBlock,
  decimalPointWidth, steepnessWidth)
  val index = Reg(init = UInt(10))
  val dataOut = Reg(init = SInt(0))

  // Initial version, just a multiplier
  val s_unallocated :: s_mul :: s_activation_function :: s_done :: Nil =
    Enum(UInt(), 4)
  val state = Reg(init = s_unallocated)

  // Default values
  io.dataOut := UInt(0)
  io.validOut := Bool(false)
  dataOut := dataOut

  // State transitions
  when (state === s_unallocated) {
    when (io.validIn) {
      state := s_mul
    }
  }
  when (state === s_mul) {
    when (index === UInt(elementsPerBlock - 1)) {
      state := s_activation_function
    }
  }
  when (state === s_activation_function) {
    state := s_done
  }
  when (state === s_done) {
    state := s_unallocated
  }

  // Non-state sequential logic
  when (state === s_unallocated) {
    dataOut := UInt(0)
    index := UInt(0)
  }
  when (state === s_mul) {
    dataOut := dataOut + io.dataIn(index) * io.weight(index)
    index := index + UInt(1)
  }
  when (state === s_activation_function) {
    dataOut := dataOut
    // Sigmoid activation function
    // dataOut := sigmoid(
    //   elementWidth,
    //   decimalPointOffset,
    //   SInt(0), minx, maxx,
    //   slope1, slope2, slope3, slope4, slope5,
    //   sigy0, sigy1, sigy2, sigy3, sigy4,
    //   x1, x2, x3, x4,
    //   io.decimalPoint,
    //   SInt(0),
    //   SInt(1) << UInt(decimalPointOffset) + io.decimalPoint,
    //   io.steepness)
    // Symmetric sigmoid
    // dataOut := sigmoid(
    //   decimalPointOffset,
    //   dataOut, minx, maxx,
    //   slope1, slope2, slope3, slope4, slope5,
    //   sigy0, sigy1, sigy2, sigy3, sigy4,
    //   x1, x2, x3, x4,
    //   io.decimalPoint,
    //   SInt(-1) << UInt(decimalPointOffset) + io.decimalPoint,
    //   SInt(1) << UInt(decimalPointOffset) + io.decimalPoint,
    //   io.steepness)
  }
  when (state === s_done) {
    io.dataOut := dataOut
    io.validOut := Bool(true)
  }

}

class ProcessingElementTests(uut: ProcessingElement) extends Tester(uut) {
  val dataIn = Array.fill(uut.elementsPerBlock){0}
  val weight = Array.fill(uut.elementsPerBlock){0}
  var correct = 0
  for (t <- 0 until 4) {
    correct = 0
    for (i <- 0 until uut.elementsPerBlock) {
      dataIn(i) = rnd.nextInt(Math.pow(2, uut.elementWidth).toInt - 1)
      weight(i) = rnd.nextInt(Math.pow(2, uut.elementWidth).toInt - 1)
      // dataIn(i) = 2 * i
      // weight(i) = 2 * i
      correct = correct + dataIn(i) * weight(i)
    }
    println(s"Correct: $correct")
    for (i <- 0 until uut.elementsPerBlock) {
      poke(uut.io.dataIn(i), dataIn(i))
      poke(uut.io.weight(i), weight(i))
    }
    poke(uut.io.validIn, 1)
    step(1)
    poke(uut.io.validIn, 0)
    while(peek(uut.io.validOut) == 0) {
      step(1)
    }
    expect(uut.io.dataOut, correct)
    step(1)
    // expect(uut.io.dataOut, dataIn * weight)
  }
}
