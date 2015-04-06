package dana

import Chisel._

class ActivationFunctionInterface(
  val elementWidth: Int,
  val decimalPointWidth: Int,
  val steepnessWidth: Int
)extends Bundle {
  val interim = SInt(INPUT, elementWidth)
  val decimalPoint = UInt(INPUT, decimalPointWidth)
  val steepness = UInt(INPUT, steepnessWidth)
  val activationFunction = UInt(INPUT, log2Up(18))
  val out = SInt(OUTPUT, elementWidth)
}

class ActivationFunction(
  val elementWidth: Int,
  val decimalPointOffset: Int,
  val decimalPointWidth: Int,
  val steepnessWidth: Int
) extends Module {
  val io = new ActivationFunctionInterface(elementWidth, decimalPointWidth,
    steepnessWidth)

  // Temporary values
  val _interim = SInt()
  val decimal = UInt()
  val out = Reg(init = SInt(0, width = elementWidth))

  val _minx      = SInt(-1420910720) // -54B16080
  val _x1        = SInt(-790391808)  // -2F1C6C00
  val _x2        = SInt(-294906496)  // -1193EA80
  val _x3        = SInt(294906496)   // 1193EA80
  val _x4        = SInt(790391808)   // 2F1C6C00
  val _maxx      = SInt(1420910720)  // 54b16080
  val _sigy0     = SInt(10737418)    // 00A3D70A
  val _sigy1     = SInt(107374184)   // 06666668
  val _sigy2     = SInt(536870912)   // 20000000
  val _sigy3     = SInt(1610612736)  // 60000000
  val _sigy4     = SInt(2040109440)  // 79999980
  val _sigy5     = SInt(2136746240)  // 7F5C2900
  val _slope1    = SInt(164567525)   // 09CF19E5
  val _slope2    = SInt(930741212)   // 3779FBDC
  val _slope3    = SInt(1954723819)  // 7482B7EB
  val _slope4    = SInt(930741280)   // 3779FC20
  val _slope5    = SInt(164567500)   // 09CF19CC
  val _symy0     = SInt(-2126008831) // -7EB851FF
  val _symy1     = SInt(-1932735232) // -73333300
  val _symy2     = SInt(-1073741824) // -40000000
  val _symy3     = SInt(1073741824)  // 40000000
  val _symy4     = SInt(1932735232)  // 73333300
  val _symy5     = SInt(2126008831)  // 7EB851FF
  // Chisel (or Scala) has a problem with creating UInts that use the
  // full bit width when specifying the value as an integer, e.g., the
  // string assignment that will be interpreted as hex works, but the
  // below assignment to the full width symslope3 does not work
  val _symslope1 = UInt("h139E343F")   // 139E343F
  val _symslope2 = UInt("h6EF3F751")  // 6EF3F751
  val _symslope3 = UInt("hE9056FD7")  // E9056FD7
  val _symslope4 = UInt("h6EF3F751")  // 6EF3F751
  val _symslope5 = UInt("h139E343F")   // 139E343F
  // val _symslope1 = UInt(329135167)   // 139E343F
  // val _symslope2 = UInt(1861482321)  // 6EF3F751
  // val _symslope3 = UInt(3909447639)  // E9056FD7
  // val _symslope4 = UInt(1861482321)  // 6EF3F751
  // val _symslope5 = UInt(329135167)   // 139E343F

  val minx = _minx >> (UInt(29)-io.decimalPoint-UInt(decimalPointOffset))
  val x1 = _x1 >> (UInt(29)-io.decimalPoint-UInt(decimalPointOffset))
  val x2 = _x2 >> (UInt(29)-io.decimalPoint-UInt(decimalPointOffset))
  val x3 = _x3 >> (UInt(29)-io.decimalPoint-UInt(decimalPointOffset))
  val x4 = _x4 >> (UInt(29)-io.decimalPoint-UInt(decimalPointOffset))
  val maxx = _maxx >> (UInt(29)-io.decimalPoint-UInt(decimalPointOffset))
  val sigy0 = _sigy0 >> (UInt(31)-io.decimalPoint-UInt(decimalPointOffset))
  val sigy1 = _sigy1 >> (UInt(31)-io.decimalPoint-UInt(decimalPointOffset))
  val sigy2 = _sigy2 >> (UInt(31)-io.decimalPoint-UInt(decimalPointOffset))
  val sigy3 = _sigy3 >> (UInt(31)-io.decimalPoint-UInt(decimalPointOffset))
  val sigy4 = _sigy4 >> (UInt(31)-io.decimalPoint-UInt(decimalPointOffset))
  val sigy5 = _sigy5 >> (UInt(31)-io.decimalPoint-UInt(decimalPointOffset))
  val slope1 = _slope1 >> (UInt(32)-io.decimalPoint-UInt(decimalPointOffset))
  val slope2 = _slope2 >> (UInt(32)-io.decimalPoint-UInt(decimalPointOffset))
  val slope3 = _slope3 >> (UInt(32)-io.decimalPoint-UInt(decimalPointOffset))
  val slope4 = _slope4 >> (UInt(32)-io.decimalPoint-UInt(decimalPointOffset))
  val slope5 = _slope5 >> (UInt(32)-io.decimalPoint-UInt(decimalPointOffset))
  val symy0 = _symy0 >> (UInt(31)-io.decimalPoint-UInt(decimalPointOffset))
  val symy1 = _symy1 >> (UInt(31)-io.decimalPoint-UInt(decimalPointOffset))
  val symy2 = _symy2 >> (UInt(31)-io.decimalPoint-UInt(decimalPointOffset))
  val symy3 = _symy3 >> (UInt(31)-io.decimalPoint-UInt(decimalPointOffset))
  val symy4 = _symy4 >> (UInt(31)-io.decimalPoint-UInt(decimalPointOffset))
  val symy5 = _symy5 >> (UInt(31)-io.decimalPoint-UInt(decimalPointOffset))
  val symslope1 = _symslope1>>(UInt(32)-io.decimalPoint-UInt(decimalPointOffset))
  val symslope2 = _symslope2>>(UInt(32)-io.decimalPoint-UInt(decimalPointOffset))
  val symslope3 = _symslope3>>(UInt(32)-io.decimalPoint-UInt(decimalPointOffset))
  val symslope4 = _symslope4>>(UInt(32)-io.decimalPoint-UInt(decimalPointOffset))
  val symslope5 = _symslope5>>(UInt(32)-io.decimalPoint-UInt(decimalPointOffset))

  decimal := UInt(decimalPointOffset,
    width = decimalPointWidth + log2Up(decimalPointOffset)) + io.decimalPoint
  _interim := SInt(0)

  when(io.activationFunction === UInt(1)) {        // FANN_THRESHOLD
    when (io.interim < SInt(0)) { out := SInt(0)
    } .otherwise { out := SInt(1) << decimal }
  } .elsewhen(io.activationFunction === UInt(2)) { // FANN_THRESHOLD_SYMMETRIC
    when (io.interim < SInt(0)) { out := SInt(-1, width=elementWidth) << decimal
    } .elsewhen(io.interim === SInt(0)) { out := SInt(0)
    } .otherwise { out := SInt(1) << decimal }
  } .elsewhen(io.activationFunction === UInt(3) || // FANN_SIGMOID
    io.activationFunction === UInt(4)) {           //   |-> ..._STEPWISE
    switch(io.steepness) {
      is(UInt(0)) { _interim := io.interim >> UInt(4) }
      is(UInt(1)) { _interim := io.interim >> UInt(3) }
      is(UInt(2)) { _interim := io.interim >> UInt(2) }
      is(UInt(3)) { _interim := io.interim >> UInt(1) }
      is(UInt(4)) { _interim := io.interim }
      is(UInt(5)) { _interim := io.interim << UInt(1) }
      is(UInt(6)) { _interim := io.interim << UInt(2) }
      is(UInt(7)) { _interim := io.interim << UInt(3) }
    }
    when(_interim < minx) {
      out := SInt(0)
    } .elsewhen((minx < _interim) && (_interim < x1)) {
      out := ((slope1*(_interim-minx) >> decimal) + sigy0)
    } .elsewhen((x1 < _interim) && (_interim < x2)) {
      out := ((slope2*(_interim-x1) >> decimal) + sigy1)
    } .elsewhen((x2 < _interim) && (_interim < x3)) {
      out := ((slope3*(_interim-x2) >> decimal) + sigy2)
    } .elsewhen((x3 < _interim) && (_interim < x4)) {
      out := ((slope4*(_interim-x3) >> decimal) + sigy3)
    } .elsewhen((x4 < _interim) && (_interim < maxx)) {
      out := ((slope5*(_interim-x4) >> decimal) + sigy4)
    } .otherwise {
      out := SInt(1) << (UInt(decimalPointOffset) + io.decimalPoint)
    }
  } .elsewhen(io.activationFunction === UInt(5) ||  // FANN_SIGMOID_SYMMETRIC
    io.activationFunction === UInt(6)) {            //   |-> ..._STEPWISE
    switch(io.steepness) {
      is(UInt(0)) { _interim := io.interim >> UInt(4) }
      is(UInt(1)) { _interim := io.interim >> UInt(3) }
      is(UInt(2)) { _interim := io.interim >> UInt(2) }
      is(UInt(3)) { _interim := io.interim >> UInt(1) }
      is(UInt(4)) { _interim := io.interim }
      is(UInt(5)) { _interim := io.interim << UInt(1) }
      is(UInt(6)) { _interim := io.interim << UInt(2) }
      is(UInt(7)) { _interim := io.interim << UInt(3) }
    }
    when(_interim < minx) {
      out := SInt(-1) << (UInt(decimalPointOffset) + io.decimalPoint)
    } .elsewhen((minx < _interim) && (_interim < x1)) {
      out := ((slope1*(_interim-minx) >> decimal) + symy0)
    } .elsewhen((x1 < _interim) && (_interim < x2)) {
      out := ((slope2*(_interim-x1) >> decimal) + symy1)
    } .elsewhen((x2 < _interim) && (_interim < x3)) {
      out := ((slope3*(_interim-x2) >> decimal) + symy2)
    } .elsewhen((x3 < _interim) && (_interim < x4)) {
      out := ((slope4*(_interim-x3) >> decimal) + symy3)
    } .elsewhen((x4 < _interim) && (_interim < maxx)) {
      out := ((slope5*(_interim-x4) >> decimal) + symy4)
    } .otherwise {
      out := SInt(1) << (UInt(decimalPointOffset) + io.decimalPoint)
    }
  } .otherwise {
    out := SInt(429496792) // The largest possible 32-bit integer
  }

  io.out := out
  // assert(io.activationFunction >= UInt(1) && io.activationFunction <= UInt(6),
  //   "Undefined activation function")
}

class ActivationFunctionTests(uut: ActivationFunction) extends Tester(uut) {
  printf("[INFO] Threshold Activation Function Test\n")
  // Threshold Test
  for (t <- 0 until 100) {
    val decimalEncoded = rnd.nextInt(8)
    val decimal = uut.decimalPointOffset + decimalEncoded
    val in = rnd.nextInt(Math.pow(2, decimal+3).toInt) -
      Math.pow(2, decimal+2).toInt
    val steepness = rnd.nextInt(8)
    val out = if (in > 0) 1 << decimal else 0
    poke(uut.io.interim, in)
    poke(uut.io.decimalPoint, decimalEncoded)
    poke(uut.io.steepness, steepness)
    poke(uut.io.activationFunction, 1)
    step(1)
    expect(uut.io.out, out)
  }
  // Symmetric Threshold Test
  for (t <- 0 until 100) {
    val decimalEncoded = rnd.nextInt(8)
    val decimal = uut.decimalPointOffset + decimalEncoded
    val in = rnd.nextInt(Math.pow(2, decimal+3).toInt) -
      Math.pow(2, decimal+2).toInt
    val steepness = rnd.nextInt(8)
    val out = if (in > 0) 1 << decimal else -1 << decimal
    poke(uut.io.interim, in)
    poke(uut.io.decimalPoint, decimalEncoded)
    poke(uut.io.steepness, steepness)
    poke(uut.io.activationFunction, 2)
    step(1)
    expect(uut.io.out, out)
  }
}
