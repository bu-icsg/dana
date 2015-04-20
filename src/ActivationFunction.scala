package dana

import Chisel._

// The steepness is currently

class ActivationFunctionReq(
  val elementWidth: Int,
  val decimalPointWidth: Int,
  val steepnessWidth: Int
) extends Bundle {
  override def clone = new ActivationFunctionReq(
    elementWidth = elementWidth,
    decimalPointWidth = decimalPointWidth,
    steepnessWidth = steepnessWidth).asInstanceOf[this.type]
  val decimal = UInt(width = decimalPointWidth)
  val steepness = UInt(width = steepnessWidth)
  val activationFunction = UInt(width = log2Up(18)) // [TODO] fragile
  val in = SInt(INPUT, elementWidth)
}

class ActivationFunctionResp(
  val elementWidth: Int
) extends Bundle {
  override def clone = new ActivationFunctionResp(
    elementWidth = elementWidth).asInstanceOf[this.type]
  val out = SInt(OUTPUT, elementWidth)
}

class ActivationFunctionInterface(
  val elementWidth: Int,
  val decimalPointWidth: Int,
  val steepnessWidth: Int
) extends Bundle {
  val req = Decoupled(new
    ActivationFunctionReq(elementWidth, decimalPointWidth, steepnessWidth)).flip
  val resp = Valid(new
    ActivationFunctionResp(elementWidth))
}

class ActivationFunction extends DanaModule()() {
  val io = new ActivationFunctionInterface(
    elementWidth = elementWidth,
    decimalPointWidth = decimalPointWidth,
    steepnessWidth = steepnessWidth)

  // Temporary values
  val inD0 = SInt(width = elementWidth)
  val decimal = UInt()
  val out = Reg(init = SInt(0, width = elementWidth))

  val _xmin      = SInt(-1420910720) // -54B16080
  val _x1        = SInt( -790391808) // -2F1C6C00
  val _x2        = SInt( -294906496) // -1193EA80
  val _x3        = SInt(  294906496) //  1193EA80
  val _x4        = SInt(  790391808) //  2F1C6C00
  val _xmax      = SInt( 1420910720) //  54b16080
  val _sigy0     = SInt(   10737418) //  00A3D70A
  val _sigy1     = SInt(  107374184) //  06666668
  val _sigy2     = SInt(  536870912) //  20000000
  val _sigy3     = SInt( 1610612736) //  60000000
  val _sigy4     = SInt( 2040109440) //  79999980
  val _sigy5     = SInt( 2136746240) //  7F5C2900
  val _slope1    = SInt(  164567525) //  09CF19E5
  val _slope2    = SInt(  930741212) //  3779FBDC
  val _slope3    = SInt( 1954723819) //  7482B7EB
  val _slope4    = SInt(  930741280) //  3779FC20
  val _slope5    = SInt(  164567500) //  09CF19CC
  val _symy0     = SInt(-2126008831) // -7EB851FF
  val _symy1     = SInt(-1932735232) // -73333300
  val _symy2     = SInt(-1073741824) // -40000000
  val _symy3     = SInt( 1073741824) //  40000000
  val _symy4     = SInt( 1932735232) //  73333300
  val _symy5     = SInt( 2126008831) //  7EB851FF
  // Chisel (or Scala) has a problem with creating UInts that use the
  // full bit width when specifying the value as an integer, e.g., the
  // string assignment that will be interpreted as hex works, but the
  // below assignment to the full width sslope3 does not work
  val _sslope1 = UInt("h139E343F")   // 139E343F
  val _sslope2 = UInt("h6EF3F751")  // 6EF3F751
  val _sslope3 = UInt("hE9056FD7")  // E9056FD7
  val _sslope4 = UInt("h6EF3F751")  // 6EF3F751
  val _sslope5 = UInt("h139E343F")   // 139E343F
  // val _sslope1 = UInt(329135167)   // 139E343F
  // val _sslope2 = UInt(1861482321)  // 6EF3F751
  // val _sslope3 = UInt(3909447639)  // E9056FD7
  // val _sslope4 = UInt(1861482321)  // 6EF3F751
  // val _sslope5 = UInt(329135167)   // 139E343F

  val xmin   = _xmin >>  (UInt(29)-io.req.bits.decimal-UInt(decimalPointOffset))
  val x1     = _x1 >>    (UInt(29)-io.req.bits.decimal-UInt(decimalPointOffset))
  val x2     = _x2 >>    (UInt(29)-io.req.bits.decimal-UInt(decimalPointOffset))
  val x3     = _x3 >>    (UInt(29)-io.req.bits.decimal-UInt(decimalPointOffset))
  val x4     = _x4 >>    (UInt(29)-io.req.bits.decimal-UInt(decimalPointOffset))
  val xmax   = _xmax >>  (UInt(29)-io.req.bits.decimal-UInt(decimalPointOffset))
  val sigy0  = _sigy0 >> (UInt(31)-io.req.bits.decimal-UInt(decimalPointOffset))
  val sigy1  = _sigy1 >> (UInt(31)-io.req.bits.decimal-UInt(decimalPointOffset))
  val sigy2  = _sigy2 >> (UInt(31)-io.req.bits.decimal-UInt(decimalPointOffset))
  val sigy3  = _sigy3 >> (UInt(31)-io.req.bits.decimal-UInt(decimalPointOffset))
  val sigy4  = _sigy4 >> (UInt(31)-io.req.bits.decimal-UInt(decimalPointOffset))
  val sigy5  = _sigy5 >> (UInt(31)-io.req.bits.decimal-UInt(decimalPointOffset))
  val slope1 = _slope1 >>(UInt(32)-io.req.bits.decimal-UInt(decimalPointOffset))
  val slope2 = _slope2 >>(UInt(32)-io.req.bits.decimal-UInt(decimalPointOffset))
  val slope3 = _slope3 >>(UInt(32)-io.req.bits.decimal-UInt(decimalPointOffset))
  val slope4 = _slope4 >>(UInt(32)-io.req.bits.decimal-UInt(decimalPointOffset))
  val slope5 = _slope5 >>(UInt(32)-io.req.bits.decimal-UInt(decimalPointOffset))
  val symy0  = _symy0 >> (UInt(31)-io.req.bits.decimal-UInt(decimalPointOffset))
  val symy1  = _symy1 >> (UInt(31)-io.req.bits.decimal-UInt(decimalPointOffset))
  val symy2  = _symy2 >> (UInt(31)-io.req.bits.decimal-UInt(decimalPointOffset))
  val symy3  = _symy3 >> (UInt(31)-io.req.bits.decimal-UInt(decimalPointOffset))
  val symy4  = _symy4 >> (UInt(31)-io.req.bits.decimal-UInt(decimalPointOffset))
  val symy5  = _symy5 >> (UInt(31)-io.req.bits.decimal-UInt(decimalPointOffset))
  val sslope1= _sslope1>>(UInt(32)-io.req.bits.decimal-UInt(decimalPointOffset))
  val sslope2= _sslope2>>(UInt(32)-io.req.bits.decimal-UInt(decimalPointOffset))
  val sslope3= _sslope3>>(UInt(32)-io.req.bits.decimal-UInt(decimalPointOffset))
  val sslope4= _sslope4>>(UInt(32)-io.req.bits.decimal-UInt(decimalPointOffset))
  val sslope5= _sslope5>>(UInt(32)-io.req.bits.decimal-UInt(decimalPointOffset))

  decimal := UInt(decimalPointOffset,
    width = decimalPointWidth + log2Up(decimalPointOffset)) + io.req.bits.decimal
  inD0 := SInt(0)

    // FANN_THRESHOLD
  when(io.req.bits.activationFunction === UInt(1)) {        // FANN_THRESHOLD
    when (io.req.bits.in <= SInt(0)) { out := SInt(0)
    } .otherwise { out := SInt(1) << decimal }
  } // FANN_THRESHOLD_SYMMETRIC
    .elsewhen(io.req.bits.activationFunction === UInt(2)) {
    when (io.req.bits.in < SInt(0)) {
      out := SInt(-1, width=elementWidth) << decimal
    } .elsewhen(io.req.bits.in === SInt(0)) {
      out := SInt(0)
    } .otherwise {
      out := SInt(1) << decimal }
  } // FANN_SIGMOID and STEPWISE
    .elsewhen(io.req.bits.activationFunction === UInt(3) ||
    io.req.bits.activationFunction === UInt(4)) {
    // Adjust for the steepness
    when (io.req.bits.steepness < UInt(4)) {
      inD0 := io.req.bits.in >> (UInt(4) - io.req.bits.steepness)
    } .elsewhen (io.req.bits.steepness === UInt(4)) {
      inD0 := io.req.bits.in
    } .otherwise {
      inD0 := io.req.bits.in << (io.req.bits.steepness - UInt(4))
    }
    // Compute the output
    when(inD0 < xmin) {
      out := SInt(0)
    } .elsewhen((xmin <= inD0) && (inD0 < x1)) {
      out := ((slope1*(inD0-xmin) >> decimal) + sigy0)
    } .elsewhen((x1 <= inD0) && (inD0 < x2)) {
      out := ((slope2*(inD0-x1) >> decimal) + sigy1)
    } .elsewhen((x2 <= inD0) && (inD0 < x3)) {
      out := ((slope3*(inD0-x2) >> decimal) + sigy2)
    } .elsewhen((x3 <= inD0) && (inD0 < x4)) {
      out := ((slope4*(inD0-x3) >> decimal) + sigy3)
    } .elsewhen((x4 <= inD0) && (inD0 < xmax)) {
      out := ((slope5*(inD0-x4) >> decimal) + sigy4)
    } .otherwise {
      out := SInt(1) << decimal
    }
  } // FANN_SIGMOID_SYMMETRIC and STEPWISE
    .elsewhen(io.req.bits.activationFunction === UInt(5) ||
    io.req.bits.activationFunction === UInt(6)) {
    // Adjust for the steepness
    when (io.req.bits.steepness < UInt(4)) {
      inD0 := io.req.bits.in >> (UInt(4) - io.req.bits.steepness)
    } .elsewhen (io.req.bits.steepness === UInt(4)) {
      inD0 := io.req.bits.in
    } .otherwise {
      inD0 := io.req.bits.in << (io.req.bits.steepness - UInt(4))
    }
    // Compute the output
    when(inD0 < xmin) {
      out := SInt(-1, width = elementWidth) << decimal
    } .elsewhen((xmin <= inD0) && (inD0 < x1)) {
      out := ((sslope1*(inD0-xmin) >> decimal) + symy0)
    } .elsewhen((x1 <= inD0) && (inD0 < x2)) {
      out := ((sslope2*(inD0-x1) >> decimal) + symy1)
    } .elsewhen((x2 <= inD0) && (inD0 < x3)) {
      out := ((sslope3*(inD0-x2) >> decimal) + symy2)
    } .elsewhen((x3 <= inD0) && (inD0 < x4)) {
      out := ((sslope4*(inD0-x3) >> decimal) + symy3)
    } .elsewhen((x4 <= inD0) && (inD0 < xmax)) {
      out := ((sslope5*(inD0-x4) >> decimal) + symy4)
    } .otherwise {
      out := SInt(1) << decimal
    }
  } // Dump out some garbage (the largest 32-bit integer)
    .otherwise {
    out := SInt(429496792)
  }

  io.resp.bits.out := out

}

class ActivationFunctionTests(uut: ActivationFunction, isTrace: Boolean = true)
    extends DanaTester(uut, isTrace) {
  def printOutputs() {
    printf("%d %d %d %d\n", peek(uut.io.req.bits.steepness),
      peek(uut.io.req.bits.activationFunction),
      peek(uut.io.req.bits.in),
      peek(uut.io.resp.bits.out))
  }

  printf("[INFO] Threshold Activation Function Test\n")
  // Threshold Test
  val numRuns = 128
  printf("steepness activationFunction acc out\n")
  for (s <- 0 until 8) {
    for (t <- 0 until numRuns) {
      // val decimalEncoded = rnd.nextInt(8)
      val decimalEncoded = 4
      val decimal = uut.decimalPointOffset + decimalEncoded
      // val in = rnd.nextInt(Math.pow(2, decimal+3).toInt) -
      // Math.pow(2, decimal+2).toInt
      val in = ( - Math.pow(2, decimal + 1) + Math.pow(2, decimal + 2) / numRuns.toFloat * t).toInt
      val steepness = s
      val out = if (in > 0) 1 << decimal else 0
      poke(uut.io.req.bits.in, in)
      poke(uut.io.req.bits.decimal, decimalEncoded)
      poke(uut.io.req.bits.steepness, s)
      poke(uut.io.req.bits.activationFunction, 1)
      step(1)
      printOutputs()
      assert(expect(uut.io.resp.bits.out, out), "Failed Threshold Test")
    }
  }
  // Symmetric Threshold Test
  printf("[INFO] Threshold Symmetric Activation Function Test\n")
  for (s <- 0 until 8) {
    for (t <- 0 until numRuns) {
      // val decimalEncoded = rnd.nextInt(8)
      val decimalEncoded = 4
      val decimal = uut.decimalPointOffset + decimalEncoded
      // val in = rnd.nextInt(Math.pow(2, decimal+3).toInt) -
      val in = ( - Math.pow(2, decimal + 1) + Math.pow(2, decimal + 2) / numRuns.toFloat * t).toInt
      Math.pow(2, decimal+2).toInt
      val steepness = s
      var out = 0
      if (in > 0) out = 1 << decimal
      else if (in == 0) out = 0
      else out = -1 << decimal
      poke(uut.io.req.bits.in, in)
      poke(uut.io.req.bits.decimal, decimalEncoded)
      poke(uut.io.req.bits.steepness, steepness)
      poke(uut.io.req.bits.activationFunction, 2)
      step(1)
      printOutputs()
      assert(expect(uut.io.resp.bits.out, out), "Failed Threshold Sym. Test")
    }
  }
  // Sigmoid Test
  printf("[INFO] Sigmoid Activation Function Test\n")
  // printf("[INFO]   decimalEnc steepness aF in inSteep out exact\n")
  for (s <- 0 until 8) {
    for (t <- 0 until numRuns) {
      // val decimalEncoded = rnd.nextInt(8)
      val decimalEncoded = 4
      // val decimalEncoded = 3
      val decimal = uut.decimalPointOffset + decimalEncoded
      // val in = rnd.nextInt(Math.pow(2, decimal+3).toInt) -
      val in = ( - Math.pow(2, decimal + 1) + Math.pow(2, decimal + 2) / numRuns.toFloat * t).toInt
      Math.pow(2, decimal+2).toInt
      // val in = ( -Math.pow(2, decimal + 2) +
      //   Math.pow(2, decimal + 3) / 100 * t).toInt
      val steepness = s
      // val steepness = 4
      poke(uut.io.req.bits.in, in)
      poke(uut.io.req.bits.decimal, decimalEncoded)
      poke(uut.io.req.bits.steepness, steepness)
      poke(uut.io.req.bits.activationFunction, 3)
      step(1)
      // Print out all the internally derived parameters
      printOutputs()
      val x: Double = peek(uut.inD0).floatValue() / Math.pow(2,decimal)
      // printf("[INFO]   %d %d %d %f %f %f %f\n",
      //   peek(uut.io.req.bits.decimal),
      //   peek(uut.io.req.bits.steepness),
      //   peek(uut.io.req.bits.activationFunction),
      //   peek(uut.io.req.bits.in).floatValue() / Math.pow(2,decimal),
      //   x,
      //   peek(uut.out).floatValue() / Math.pow(2, decimal),
      //   1 / (1 + Math.exp(-x/0.5)))
      // Weak check to ensure the output is close to what it should be:
      assert(expect(Math.abs(peek(uut.out).floatValue() / Math.pow(2, decimal) -
        1 / (1 + Math.exp(-x/0.5))) < 0.1, "Simgoid within 0.1 of correct?"))
    }
  }
  // Symmetric Sigmoid Test
  printf("[INFO] Sigmoid Symmetric Activation Function Test\n")
  // printf("[INFO]   decimalEnc steepness aF in inSteep out exact\n")
  for (s <- 0 until 8) {
    for (t <- 0 until numRuns) {
      // val decimalEncoded = rnd.nextInt(8)
      val decimalEncoded = 4
      // val decimalEncoded = 0
      val decimal = uut.decimalPointOffset + decimalEncoded
      // val in = rnd.nextInt(Math.pow(2, decimal+3).toInt) -
      val in = ( - Math.pow(2, decimal + 1) + Math.pow(2, decimal + 2) / numRuns.toFloat * t).toInt
      Math.pow(2, decimal+2).toInt
      // val in = ( -Math.pow(2, decimal + 2) +
      //   Math.pow(2, decimal + 3) / numRuns * t).toInt
      val steepness = s
      // val steepness = 2
      poke(uut.io.req.bits.in, in)
      poke(uut.io.req.bits.decimal, decimalEncoded)
      poke(uut.io.req.bits.steepness, steepness)
      poke(uut.io.req.bits.activationFunction, 5)
      printOutputs()
      step(1)
      // Print out all the internally derived parameters
      val x: Double = peek(uut.inD0).floatValue() / Math.pow(2,decimal)
      // printf("[INFO]   %d %d %d %f %f %f %f\n",
      //   peek(uut.io.req.bits.decimal),
      //   peek(uut.io.req.bits.steepness),
      //   peek(uut.io.req.bits.activationFunction),
      //   peek(uut.io.req.bits.in).floatValue() / Math.pow(2,decimal),
      //   x,
      //   peek(uut.out).floatValue() / Math.pow(2, decimal),
      //   2 / (1 + Math.exp(-x/0.5)) - 1)
      // Weak check to ensure the output is close to what it should be:
      assert(expect(Math.abs((peek(uut.out).floatValue() / Math.pow(2,decimal))-
        (2 / (1 + Math.exp(-x/0.5)) -1)) <0.1,
        "Symmetric simgoid within 0.1 of correct?"))
    }
  }
}
