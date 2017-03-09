// See LICENSE.BU for license details.
// See LICENSE.IBM for license details.

package dana

import chisel3._
import chisel3.util._
import config._

class ActivationFunctionReq(implicit p: Parameters) extends DanaBundle()(p) {
  val decimal            = UInt(decimalPointWidth.W)
  val steepness          = UInt(steepnessWidth.W)
  // Differentiate activation function requests vs. error function
  // requests
  val activationFunction = UInt(log2Up(18).W) // [TODO] fragile
  val in                 = SInt(elementWidth.W)
}

class ActivationFunctionReqLearn(implicit p: Parameters)
    extends ActivationFunctionReq()(p) {
  val afType             = UInt(log2Up(2).W) // [TODO] fragile
  val errorFunction      = UInt(log2Up(2).W)
}

class ActivationFunctionResp(implicit p: Parameters) extends DanaBundle()(p) {
  val out                = SInt(elementWidth.W)
}

class ActivationFunctionInterface(implicit p: Parameters) extends DanaBundle()(p) {
  val req = Valid(new ActivationFunctionReq).flip
  val resp = Valid(new ActivationFunctionResp)
}

class ActivationFunctionInterfaceLearn(implicit p: Parameters)
    extends ActivationFunctionInterface()(p) {
  override val req = Valid(new ActivationFunctionReqLearn).flip
}

class DSP(implicit p: Parameters) extends DanaModule()(p) {
  val io = IO(new Bundle {
    val a = Input(SInt(elementWidth.W))
    val b = Input(SInt(elementWidth.W))
    val c = Input(UInt(elementWidth.W))
    val d = Output(SInt(elementWidth.W))
  })
  io.d := (((io.a * io.b) >> io.c)(elementWidth - 1, 0)).asSInt
}

class ActivationFunction(id: Int = 0)(implicit p: Parameters) extends DanaModule()(p) {
  lazy val io = (new ActivationFunctionInterface)
  override val printfSigil = "dana.PE[" + id + "]: "

  // Temporary values
  val inD0 = Wire(SInt(elementWidth.W))
  val decimal = Wire(UInt())
  val out = Reg(init = 0.S(elementWidth.W))

  val _xmin      = -1420910720.S // -54B16080
  val _x1        =  -790391808.S // -2F1C6C00
  val _x2        =  -294906496.S // -1193EA80
  val _x3        =   294906496.S //  1193EA80
  val _x4        =   790391808.S //  2F1C6C00
  val _xmax      =  1420910720.S //  54b16080
  val _sigy0     =    10737418.S //  00A3D70A
  val _sigy1     =   107374184.S //  06666668
  val _sigy2     =   536870912.S //  20000000
  val _sigy3     =  1610612736.S //  60000000
  val _sigy4     =  2040109440.S //  79999980
  val _sigy5     =  2136746240.S //  7F5C2900
  val _slope1    =   164567525.S //  09CF19E5
  val _slope2    =   930741212.S //  3779FBDC
  val _slope3    =  1954723819.S //  7482B7EB
  val _slope4    =   930741280.S //  3779FC20
  val _slope5    =   164567500.S //  09CF19CC
  val _symy0     = -2126008831.S // -7EB851FF
  val _symy1     = -1932735232.S // -73333300
  val _symy2     = -1073741824.S // -40000000
  val _symy3     =  1073741824.S //  40000000
  val _symy4     =  1932735232.S //  73333300
  val _symy5     =  2126008831.S //  7EB851FF
  // Chisel (or Scala) has a problem with creating UInts that use the
  // full bit width when specifying the value as an integer, e.g., the
  // string assignment that will be interpreted as hex works, but the
  // below assignment to the full width sslope3 does not work
  val _sslope1 = "h139E343F".U   // 139E343F
  val _sslope2 = "h6EF3F751".U  // 6EF3F751
  val _sslope3 = "hE9056FD7".U  // E9056FD7
  val _sslope4 = "h6EF3F751".U  // 6EF3F751
  val _sslope5 = "h139E343F".U   // 139E343F

  val xmin   = _xmin >>  (29.U-io.req.bits.decimal-decimalPointOffset.U)
  val x1     = _x1 >>    (29.U-io.req.bits.decimal-decimalPointOffset.U)
  val x2     = _x2 >>    (29.U-io.req.bits.decimal-decimalPointOffset.U)
  val x3     = _x3 >>    (29.U-io.req.bits.decimal-decimalPointOffset.U)
  val x4     = _x4 >>    (29.U-io.req.bits.decimal-decimalPointOffset.U)
  val xmax   = _xmax >>  (29.U-io.req.bits.decimal-decimalPointOffset.U)
  val sigy0  = _sigy0 >> (31.U-io.req.bits.decimal-decimalPointOffset.U)
  val sigy1  = _sigy1 >> (31.U-io.req.bits.decimal-decimalPointOffset.U)
  val sigy2  = _sigy2 >> (31.U-io.req.bits.decimal-decimalPointOffset.U)
  val sigy3  = _sigy3 >> (31.U-io.req.bits.decimal-decimalPointOffset.U)
  val sigy4  = _sigy4 >> (31.U-io.req.bits.decimal-decimalPointOffset.U)
  val sigy5  = _sigy5 >> (31.U-io.req.bits.decimal-decimalPointOffset.U)
  val slope1 = _slope1 >>(32.U-io.req.bits.decimal-decimalPointOffset.U)
  val slope2 = _slope2 >>(32.U-io.req.bits.decimal-decimalPointOffset.U)
  val slope3 = _slope3 >>(32.U-io.req.bits.decimal-decimalPointOffset.U)
  val slope4 = _slope4 >>(32.U-io.req.bits.decimal-decimalPointOffset.U)
  val slope5 = _slope5 >>(32.U-io.req.bits.decimal-decimalPointOffset.U)
  val symy0  = _symy0 >> (31.U-io.req.bits.decimal-decimalPointOffset.U)
  val symy1  = _symy1 >> (31.U-io.req.bits.decimal-decimalPointOffset.U)
  val symy2  = _symy2 >> (31.U-io.req.bits.decimal-decimalPointOffset.U)
  val symy3  = _symy3 >> (31.U-io.req.bits.decimal-decimalPointOffset.U)
  val symy4  = _symy4 >> (31.U-io.req.bits.decimal-decimalPointOffset.U)
  val symy5  = _symy5 >> (31.U-io.req.bits.decimal-decimalPointOffset.U)
  val sslope1= _sslope1>>(32.U-io.req.bits.decimal-decimalPointOffset.U)
  val sslope2= _sslope2>>(32.U-io.req.bits.decimal-decimalPointOffset.U)
  val sslope3= _sslope3>>(32.U-io.req.bits.decimal-decimalPointOffset.U)
  val sslope4= _sslope4>>(32.U-io.req.bits.decimal-decimalPointOffset.U)
  val sslope5= _sslope5>>(32.U-io.req.bits.decimal-decimalPointOffset.U)

  decimal := decimalPointOffset.U((decimalPointWidth + log2Up(decimalPointOffset)).W) + io.req.bits.decimal

  // DSP Unit
  val dsp = Module(new DSP).io
  def DSP(a: SInt, b: SInt, c: UInt) {
    dsp.a := a
    dsp.b := b
    dsp.c := c
  }

  // All activation functions currently take two cycles, so the output
  // valid signal is delayed by two cycles.
  val ioVal_d0 = Reg(next = io.req.valid)
  val ioVal_d1 = Reg(next = ioVal_d0)
  io.resp.bits.out := out
  io.resp.valid := ioVal_d1
  val dataIn = RegNext(io.req.bits.in)

  when (ioVal_d1) {
    printfInfo("af(0x%x) = 0x%x\n", dataIn, out)
  }

  def applySteepness(x: SInt, steepness: UInt): SInt = {
    val tmp = Wire(SInt())
    when (steepness < steepnessOffset.U) {
      tmp := x >> (steepnessOffset.U - steepness)
    } .elsewhen (steepness === steepnessOffset.U) {
      tmp := x
    } .otherwise {
      tmp := x << (steepness - steepnessOffset.U)
    }
    tmp
  }

  val one          = 1.S(elementWidth.W) << decimal
  val negOne       = -1.S(elementWidth.W) << decimal
  val seventeen    = 17.S(elementWidth.W) << decimal
  val negSeventeen = -17.S(elementWidth.W) << decimal
  val offsetX      = Wire(SInt(elementWidth.W))
  val offsetSigY   = Wire(SInt(elementWidth.W))
  val offsetSymY   = Wire(SInt(elementWidth.W))
  val slopeSig     = Wire(SInt(elementWidth.W))
  val slopeSym     = Wire(SInt(elementWidth.W))
  when(inD0 < xmin) {
    offsetX    := 0.S
    offsetSigY := 0.S
    offsetSymY := negOne
    slopeSig   := 0.S
    slopeSym   := 0.S
  } .elsewhen((xmin <= inD0) && (inD0 < x1)) {
    offsetX    := xmin
    offsetSigY := sigy0
    offsetSymY := symy0
    slopeSig   := slope1
    slopeSym   := sslope1.asSInt
  } .elsewhen((x1 <= inD0) && (inD0 < x2)) {
    offsetX    := x1
    offsetSigY := sigy1
    offsetSymY := symy1
    slopeSig   := slope2
    slopeSym   := sslope2.asSInt
  } .elsewhen((x2 <= inD0) && (inD0 < x3)) {
    offsetX    := x2
    offsetSigY := sigy2
    offsetSymY := symy2
    slopeSig   := slope3
    slopeSym   := sslope3.asSInt
  } .elsewhen((x3 <= inD0) && (inD0 < x4)) {
    offsetX    := x3
    offsetSigY := sigy3
    offsetSymY := symy3
    slopeSig   := slope4
    slopeSym   := sslope4.asSInt
  } .elsewhen((xmin <= inD0) && (inD0 < xmax)) {
    offsetX    := x4
    offsetSigY := sigy4
    offsetSymY := symy4
    slopeSig   := slope5
    slopeSym   := sslope5.asSInt
  } .otherwise {
    offsetX    := 0.S
    offsetSigY := one
    offsetSymY := one
    slopeSig   := 0.S
    slopeSym   := 0.S
  }

  // [TODO] You can probably remove this---by default `out` gets a
  // garbage value (a very big integer) which should be visibile in
  // the output
  DSP(slopeSym, inD0-offsetX, decimal)
  out := dsp.d + offsetSymY
  inD0 := applySteepness(dataIn, io.req.bits.steepness)
  // FANN_LINEAR
  switch (io.req.bits.activationFunction) {
    is (e_FANN_LINEAR) {
      out := inD0
    } // FANN_THRESHOLD
    is (e_FANN_THRESHOLD) {
      when (inD0 <= 0.S) { out := 0.S
      } .otherwise { out := one }
    } // FANN_THRESHOLD_SYMMETRIC
    is (e_FANN_THRESHOLD_SYMMETRIC) {
      when (inD0 < 0.S) {
        out := negOne
      } .elsewhen(inD0 === 0.S) {
        out := 0.S
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

class ActivationFunctionLearn(id: Int = 0)(implicit p: Parameters)
    extends ActivationFunction(id)(p) {
  override lazy val io = (new ActivationFunctionInterfaceLearn)

  // atanh specific
  // Binary Point: 31
  val _atanh_x0 = -2147483433.S // -0.9999999
  val _atanh_x1 = -2138482164.S // -0.9958083573500538
  val _atanh_x2 = -1876440389.S // -0.8737856469351458
  val _atanh_x3 =  1876440379.S // 0.873785642278533
  val _atanh_x4 =  2138482162.S // 0.9958083564187312
  val _atanh_x5 =  2147483433.S // 0.9999999
  // Binary Point: 26
  val _atanh_y0 = -1128183406.S // -16.81124278204462
  val _atanh_y1 =  -413773911.S // -6.165711741243977
  val _atanh_y2 =  -181041891.S // -2.6977343972924133
  val _atanh_y3 =   181041888.S // 2.697734357912798
  val _atanh_y4 =   413773896.S // 6.165711518591778
  val _atanh_y5 =  1128183406.S // 16.81124278204462
  // Binary Point: 19
  val _atanh_s1 =  1331568027.U // 2539.7644566343943
  val _atanh_s2 =    14900660.U // 28.42075325289503
  val _atanh_s3 =     1618692.U // 3.087409817560523
  val _atanh_s4 =    14900659.U // 28.420750883269488
  val _atanh_s5 =  1331567759.U // 2539.763945441386
  // Binary Point: 31
  val atanh_x0 = _atanh_x0 >> (31.U-io.req.bits.decimal-decimalPointOffset.U)
  val atanh_x1 = _atanh_x1 >> (31.U-io.req.bits.decimal-decimalPointOffset.U)
  val atanh_x2 = _atanh_x2 >> (31.U-io.req.bits.decimal-decimalPointOffset.U)
  val atanh_x3 = _atanh_x3 >> (31.U-io.req.bits.decimal-decimalPointOffset.U)
  val atanh_x4 = _atanh_x4 >> (31.U-io.req.bits.decimal-decimalPointOffset.U)
  val atanh_x5 = _atanh_x5 >> (31.U-io.req.bits.decimal-decimalPointOffset.U)
  // Binary Point: 26
  val atanh_y0 = _atanh_y0 >> (26.U-io.req.bits.decimal-decimalPointOffset.U)
  val atanh_y1 = _atanh_y1 >> (26.U-io.req.bits.decimal-decimalPointOffset.U)
  val atanh_y2 = _atanh_y2 >> (26.U-io.req.bits.decimal-decimalPointOffset.U)
  val atanh_y3 = _atanh_y3 >> (26.U-io.req.bits.decimal-decimalPointOffset.U)
  val atanh_y4 = _atanh_y4 >> (26.U-io.req.bits.decimal-decimalPointOffset.U)
  val atanh_y5 = _atanh_y5 >> (26.U-io.req.bits.decimal-decimalPointOffset.U)
  // Binary Point: 19
  val atanh_s1 = _atanh_s1 >> (19.U-io.req.bits.decimal-decimalPointOffset.U)
  val atanh_s2 = _atanh_s2 >> (19.U-io.req.bits.decimal-decimalPointOffset.U)
  val atanh_s3 = _atanh_s3 >> (19.U-io.req.bits.decimal-decimalPointOffset.U)
  val atanh_s4 = _atanh_s4 >> (19.U-io.req.bits.decimal-decimalPointOffset.U)
  val atanh_s5 = _atanh_s5 >> (19.U-io.req.bits.decimal-decimalPointOffset.U)

  // Atanh error function
  val atanhOffsetX = Wire(SInt(elementWidth.W))
  val atanhOffsetY = Wire(SInt(elementWidth.W))
  val atanhSlope   = Wire(SInt(elementWidth.W))
  when (dataIn < atanh_x0) {
    atanhOffsetX := 0.S
    atanhOffsetY := negSeventeen
    atanhSlope   := 0.S
  } .elsewhen ((atanh_x0 <= dataIn) && (dataIn < atanh_x1)) {
    atanhOffsetX := atanh_x0
    atanhOffsetY := atanh_y0
    atanhSlope   := atanh_s1.asSInt
  } .elsewhen ((atanh_x1 <= dataIn) && (dataIn < atanh_x2)) {
    atanhOffsetX := atanh_x1
    atanhOffsetY := atanh_y1
    atanhSlope   := atanh_s2.asSInt
  } .elsewhen ((atanh_x2 <= dataIn) && (dataIn < atanh_x3)) {
    atanhOffsetX := atanh_x2
    atanhOffsetY := atanh_y2
    atanhSlope   := atanh_s3.asSInt
  } .elsewhen ((atanh_x3 <= dataIn) && (dataIn < atanh_x4)) {
    atanhOffsetX := atanh_x3
    atanhOffsetY := atanh_y3
    atanhSlope   := atanh_s4.asSInt
  } .elsewhen ((atanh_x4 <= dataIn) && (dataIn < atanh_x5)) {
    atanhOffsetX := atanh_x4
    atanhOffsetY := atanh_y4
    atanhSlope   := atanh_s5.asSInt
  } .otherwise {
    atanhOffsetX := 0.S
    atanhOffsetY := seventeen
    atanhSlope   := 0.S
  }

  when (io.req.bits.afType === e_AF_DO_ERROR_FUNCTION) {
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
