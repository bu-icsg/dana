package dana

import Chisel._
import cde.{Parameters, Field}

// The steepness is currently

class ActivationFunctionReq(implicit p: Parameters) extends DanaBundle()(p) {
  val decimal = UInt(width = decimalPointWidth)
  val steepness = UInt(width = steepnessWidth)
  // Differentiate activation function requests vs. error function
  // requests
  val afType = UInt(width = log2Up(2)) // [TODO] fragile
  val activationFunction = UInt(width = log2Up(18)) // [TODO] fragile
  val errorFunction = UInt(width = log2Up(2)) // [TODO] fragile
  val in = SInt(INPUT, elementWidth)
}

class ActivationFunctionResp(implicit p: Parameters) extends DanaBundle()(p) {
  val out = SInt(OUTPUT, elementWidth)
}

class ActivationFunctionInterface(implicit p: Parameters) extends DanaBundle()(p) {
  val req = Valid(new ActivationFunctionReq).flip
  val resp = Valid(new ActivationFunctionResp)
}

class ActivationFunction(implicit p: Parameters) extends DanaModule()(p) {
  val io = new ActivationFunctionInterface

  // Temporary values
  val inD0 = Wire(SInt(width = elementWidth))
  val decimal = Wire(UInt())
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

  // atanh specific
  // Binary Point: 31
  val _atanh_x0 = SInt(-2147483433) // -0.9999999
  val _atanh_x1 = SInt(-2138482164) // -0.9958083573500538
  val _atanh_x2 = SInt(-1876440389) // -0.8737856469351458
  val _atanh_x3 = SInt( 1876440379) // 0.873785642278533
  val _atanh_x4 = SInt( 2138482162) // 0.9958083564187312
  val _atanh_x5 = SInt( 2147483433) // 0.9999999
  // Binary Point: 26
  val _atanh_y0 = SInt(-1128183406) // -16.81124278204462
  val _atanh_y1 = SInt( -413773911) // -6.165711741243977
  val _atanh_y2 = SInt( -181041891) // -2.6977343972924133
  val _atanh_y3 = SInt(  181041888) // 2.697734357912798
  val _atanh_y4 = SInt(  413773896) // 6.165711518591778
  val _atanh_y5 = SInt( 1128183406) // 16.81124278204462
  // Binary Point: 19
  val _atanh_s1 = UInt( 1331568027) // 2539.7644566343943
  val _atanh_s2 = UInt(   14900660) // 28.42075325289503
  val _atanh_s3 = UInt(    1618692) // 3.087409817560523
  val _atanh_s4 = UInt(   14900659) // 28.420750883269488
  val _atanh_s5 = UInt( 1331567759) // 2539.763945441386
  // Binary Point: 31
  val atanh_x0 = _atanh_x0 >> (UInt(31)-io.req.bits.decimal-UInt(decimalPointOffset))
  val atanh_x1 = _atanh_x1 >> (UInt(31)-io.req.bits.decimal-UInt(decimalPointOffset))
  val atanh_x2 = _atanh_x2 >> (UInt(31)-io.req.bits.decimal-UInt(decimalPointOffset))
  val atanh_x3 = _atanh_x3 >> (UInt(31)-io.req.bits.decimal-UInt(decimalPointOffset))
  val atanh_x4 = _atanh_x4 >> (UInt(31)-io.req.bits.decimal-UInt(decimalPointOffset))
  val atanh_x5 = _atanh_x5 >> (UInt(31)-io.req.bits.decimal-UInt(decimalPointOffset))
  // when (io.req.valid) {
  //   printf("[INFO] AF: atanh_x0 is 0x%x\n", atanh_x0)
  //   printf("[INFO] AF: atanh_x1 is 0x%x\n", atanh_x1)
  //   printf("[INFO] AF: atanh_x2 is 0x%x\n", atanh_x2)
  //   printf("[INFO] AF: atanh_x3 is 0x%x\n", atanh_x3)
  //   printf("[INFO] AF: atanh_x4 is 0x%x\n", atanh_x4)
  //   printf("[INFO] AF: atanh_x5 is 0x%x\n", atanh_x5)
  // }
  // Binary Point: 26
  val atanh_y0 = _atanh_y0 >> (UInt(26)-io.req.bits.decimal-UInt(decimalPointOffset))
  val atanh_y1 = _atanh_y1 >> (UInt(26)-io.req.bits.decimal-UInt(decimalPointOffset))
  val atanh_y2 = _atanh_y2 >> (UInt(26)-io.req.bits.decimal-UInt(decimalPointOffset))
  val atanh_y3 = _atanh_y3 >> (UInt(26)-io.req.bits.decimal-UInt(decimalPointOffset))
  val atanh_y4 = _atanh_y4 >> (UInt(26)-io.req.bits.decimal-UInt(decimalPointOffset))
  val atanh_y5 = _atanh_y5 >> (UInt(26)-io.req.bits.decimal-UInt(decimalPointOffset))
  // when (io.req.valid) {
  //   printf("[INFO] AF: atanh_y0 is 0x%x\n", atanh_y0)
  //   printf("[INFO] AF: atanh_y1 is 0x%x\n", atanh_y1)
  //   printf("[INFO] AF: atanh_y2 is 0x%x\n", atanh_y2)
  //   printf("[INFO] AF: atanh_y3 is 0x%x\n", atanh_y3)
  //   printf("[INFO] AF: atanh_y4 is 0x%x\n", atanh_y4)
  //   printf("[INFO] AF: atanh_y5 is 0x%x\n", atanh_y5)
  // }
  // Binary Point: 19
  val atanh_s1 = _atanh_s1 >> (UInt(19)-io.req.bits.decimal-UInt(decimalPointOffset))
  val atanh_s2 = _atanh_s2 >> (UInt(19)-io.req.bits.decimal-UInt(decimalPointOffset))
  val atanh_s3 = _atanh_s3 >> (UInt(19)-io.req.bits.decimal-UInt(decimalPointOffset))
  val atanh_s4 = _atanh_s4 >> (UInt(19)-io.req.bits.decimal-UInt(decimalPointOffset))
  val atanh_s5 = _atanh_s5 >> (UInt(19)-io.req.bits.decimal-UInt(decimalPointOffset))
  // when (io.req.valid) {
  //   printf("[INFO] AF: atanh_s1 is 0x%x\n", atanh_s1)
  //   printf("[INFO] AF: atanh_s2 is 0x%x\n", atanh_s2)
  //   printf("[INFO] AF: atanh_s3 is 0x%x\n", atanh_s3)
  //   printf("[INFO] AF: atanh_s4 is 0x%x\n", atanh_s4)
  //   printf("[INFO] AF: atanh_s5 is 0x%x\n", atanh_s5)
  // }

  decimal := UInt(decimalPointOffset,
    width = decimalPointWidth + log2Up(decimalPointOffset)) + io.req.bits.decimal

  // DSP Unit
  val dsp = new Bundle {
    // [TODO] These internal wires **may** be completely wrong.
    // Constructors are generally forbidden inside of Bundle!
    val a = Wire(SInt(width = elementWidth))
    val b = Wire(SInt(width = elementWidth))
    val c = Wire(UInt(width = elementWidth))
    val d = Wire(SInt(width = elementWidth))
  }
  def DSP(a: SInt, b: SInt, c: UInt) {
    dsp.a := a
    dsp.b := b
    dsp.c := c
  }
  dsp.d := ((dsp.a * dsp.b) >> dsp.c)(elementWidth - 1, 0)

  // All activation functions currently take two cycles, so the output
  // valid signal is delayed by two cycles.
  val ioVal_d0 = Reg(next = io.req.valid)
  val ioVal_d1 = Reg(next = ioVal_d0)
  io.resp.bits.out := out
  io.resp.valid := ioVal_d1
  val dataIn = Reg(SInt(INPUT, elementWidth))
  when (io.req.valid) {
    dataIn := io.req.bits.in
  } .otherwise {
    dataIn := SInt(0)
  }

  when (ioVal_d1) {
    printf("[INFO] PE: AF(0x%x) = 0x%x\n", dataIn, out)
  }

  def applySteepness(x: SInt, steepness: UInt): SInt = {
    val tmp = Wire(SInt())
    when (steepness < UInt(steepnessOffset)) {
      tmp := x >> (UInt(steepnessOffset) - steepness)
    } .elsewhen (steepness === UInt(steepnessOffset)) {
      tmp := x
    } .otherwise {
      tmp := x << (steepness - UInt(steepnessOffset))
    }
    tmp
  }

  val one = SInt(1, width = elementWidth) << decimal
  val negOne = SInt(-1, width = elementWidth) << decimal
  val seventeen = SInt(17, width = elementWidth) << decimal
  val negSeventeen = SInt(-17, width = elementWidth) << decimal
  val offsetX = Wire(SInt(width = elementWidth))
  val offsetSigY = Wire(SInt(width = elementWidth))
  val offsetSymY = Wire(SInt(width = elementWidth))
  val slopeSig = Wire(SInt(width = elementWidth))
  val slopeSym = Wire(SInt(width = elementWidth))
  when(inD0 < xmin) {
    offsetX    := SInt(0)
    offsetSigY := SInt(0)
    offsetSymY := negOne
    slopeSig   := SInt(0)
    slopeSym   := SInt(0)
  } .elsewhen((xmin <= inD0) && (inD0 < x1)) {
    offsetX    := xmin
    offsetSigY := sigy0
    offsetSymY := symy0
    slopeSig   := slope1
    slopeSym   := sslope1
  } .elsewhen((x1 <= inD0) && (inD0 < x2)) {
    offsetX    := x1
    offsetSigY := sigy1
    offsetSymY := symy1
    slopeSig   := slope2
    slopeSym   := sslope2
  } .elsewhen((x2 <= inD0) && (inD0 < x3)) {
    offsetX    := x2
    offsetSigY := sigy2
    offsetSymY := symy2
    slopeSig   := slope3
    slopeSym   := sslope3
  } .elsewhen((x3 <= inD0) && (inD0 < x4)) {
    offsetX    := x3
    offsetSigY := sigy3
    offsetSymY := symy3
    slopeSig   := slope4
    slopeSym   := sslope4
  } .elsewhen((xmin <= inD0) && (inD0 < xmax)) {
    offsetX    := x4
    offsetSigY := sigy4
    offsetSymY := symy4
    slopeSig   := slope5
    slopeSym   := sslope5
  } .otherwise {
    offsetX    := SInt(0)
    offsetSigY := one
    offsetSymY := one
    slopeSig   := SInt(0)
    slopeSym   := SInt(0)
  }
  // Atanh error function
  val atanhOffsetX = Wire(SInt(width = elementWidth))
  val atanhOffsetY = Wire(SInt(width = elementWidth))
  val atanhSlope = Wire(SInt(width = elementWidth))
  when (dataIn < atanh_x0) {
    atanhOffsetX := SInt(0)
    atanhOffsetY := negSeventeen
    atanhSlope   := SInt(0)
  } .elsewhen ((atanh_x0 <= dataIn) && (dataIn < atanh_x1)) {
    atanhOffsetX := atanh_x0
    atanhOffsetY := atanh_y0
    atanhSlope   := atanh_s1
  } .elsewhen ((atanh_x1 <= dataIn) && (dataIn < atanh_x2)) {
    atanhOffsetX := atanh_x1
    atanhOffsetY := atanh_y1
    atanhSlope   := atanh_s2
  } .elsewhen ((atanh_x2 <= dataIn) && (dataIn < atanh_x3)) {
    atanhOffsetX := atanh_x2
    atanhOffsetY := atanh_y2
    atanhSlope   := atanh_s3
  } .elsewhen ((atanh_x3 <= dataIn) && (dataIn < atanh_x4)) {
    atanhOffsetX := atanh_x3
    atanhOffsetY := atanh_y3
    atanhSlope   := atanh_s4
  } .elsewhen ((atanh_x4 <= dataIn) && (dataIn < atanh_x5)) {
    atanhOffsetX := atanh_x4
    atanhOffsetY := atanh_y4
    atanhSlope   := atanh_s5
  } .otherwise {
    atanhOffsetX := SInt(0)
    atanhOffsetY := seventeen
    atanhSlope   := SInt(0)
  }

  // [TODO] You can probably remove this---by default `out` gets a
  // garbage value (a very big integer) which should be visibile in
  // the output
  DSP(slopeSym, inD0-offsetX, decimal)
  out := dsp.d + offsetSymY
  inD0 := applySteepness(dataIn, io.req.bits.steepness)
  // FANN_LINEAR
  switch (io.req.bits.afType) {
    is (e_AF_DO_ACTIVATION_FUNCTION) {
      switch (io.req.bits.activationFunction) {
        is (e_FANN_LINEAR) {
          out := inD0
        } // FANN_THRESHOLD
        is (e_FANN_THRESHOLD) {
          when (inD0 <= SInt(0)) { out := SInt(0)
          } .otherwise { out := one }
        } // FANN_THRESHOLD_SYMMETRIC
        is (e_FANN_THRESHOLD_SYMMETRIC) {
          when (inD0 < SInt(0)) {
            out := SInt(-1, width=elementWidth) << decimal
          } .elsewhen(inD0 === SInt(0)) {
            out := SInt(0)
          } .otherwise {
            out := one }
        } // FANN_SIGMOID and STEPWISE
        is (e_FANN_SIGMOID) {
          DSP(slopeSig, inD0-offsetX, decimal)
          out := dsp.d + offsetSigY
        }
        is (e_FANN_SIGMOID_STEPWISE) {
          DSP(slopeSig, inD0-offsetX, decimal)
          out := dsp.d + offsetSigY
        }
      }
    }
    is (e_AF_DO_ERROR_FUNCTION) {
      switch (io.req.bits.errorFunction) {
        is (e_FANN_ERRORFUNC_LINEAR) {
          out := dataIn
        }
        is (e_FANN_ERRORFUNC_TANH) {
          DSP(atanhSlope, dataIn-atanhOffsetX, decimal)
          out := dsp.d + atanhOffsetY
        }
      }
    }
  }
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
