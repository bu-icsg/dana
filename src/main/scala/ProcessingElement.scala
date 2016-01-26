package dana

import Chisel._
import cde.{Parameters, Field}

class ProcessingElementReq(implicit p: Parameters) extends DanaBundle()(p) {
  val numWeights = UInt(INPUT, width = 8)            // [TODO] fragile
  val index = UInt(INPUT)
  val decimalPoint = UInt(INPUT, decimalPointWidth)
  val steepness = UInt(INPUT, steepnessWidth)
  val activationFunction = UInt(INPUT, activationFunctionWidth)
  val iBlock = Vec.fill(elementsPerBlock){SInt(INPUT, elementWidth)}
  val wBlock = Vec.fill(elementsPerBlock){SInt(INPUT, elementWidth)}
  val bias = SInt(INPUT, elementWidth)
}

class ProcessingElementReqLearn(implicit p: Parameters)
    extends ProcessingElementReq()(p) {
  val errorFunction = UInt(INPUT, width = log2Up(2)) // [TODO] fragile
  val learningRate = UInt(INPUT, width = 16)         // [TODO] fragile
  val lambda = SInt(INPUT, width = 16)               // [TODO] fragile
  val learnReg = SInt(INPUT, elementWidth)
  val stateLearn = UInt(INPUT, width = log2Up(8))    // [TODO] fragile
  val inLast = Bool(INPUT)
  val inFirst = Bool(INPUT)
  val dw_in = SInt(INPUT, elementWidth)
  val tType = UInt(INPUT, width = log2Up(3))
}

class ProcessingElementResp(implicit p: Parameters) extends DanaBundle()(p) {
  val data = SInt(width = elementWidth)
  val state = UInt()
  val index = UInt()
  val incWriteCount = Bool()
}

class ProcessingElementRespLearn(implicit p: Parameters)
    extends ProcessingElementResp()(p) {
  val dataBlock = Vec.fill(elementsPerBlock){SInt(width = elementWidth)}
  val error = SInt(width = elementWidth)
}

class ProcessingElementInterface(implicit p: Parameters) extends DanaBundle()(p) {
  // The Processing Element Interface consists of three main
  // components: requests from the PE Table (really kicks to do
  // something), responses to the PE Table, and semi-static data which
  // th PE Table manages and is used by the PEs for computation.
  lazy val req = Decoupled(new ProcessingElementReq).flip
  lazy val resp = Decoupled(new ProcessingElementResp)
}

class ProcessingElementInterfaceLearn(implicit p: Parameters)
    extends ProcessingElementInterface()(p) {
  override lazy val req = Decoupled(new ProcessingElementReqLearn).flip
  override lazy val resp = Decoupled(new ProcessingElementRespLearn)
}

class ProcessingElement(implicit p: Parameters) extends DanaModule()(p) {
  // Interface to the PE Table
  lazy val io = new ProcessingElementInterface

  // Activation Function module
  lazy val af = Module(new ActivationFunction)

  val index = Reg(UInt(width = 8)) // [TODO] fragile, should match numWeights
  val acc = Reg(SInt(width = elementWidth))
  val dataOut = Reg(SInt(width = elementWidth))
  val reqSent = Reg(Bool())

  // [TODO] fragile on PE stateu enum (Common.scala)
  val state = Reg(UInt(), init = PE_states('e_PE_UNALLOCATED))

  // Local state storage. Any and all of these are possible kludges
  // which could be implemented more cleanly.
  val hasBias = Reg(Bool())
  val steepness = UInt(steepnessOffset) - io.req.bits.steepness
  val decimal = UInt(decimalPointOffset, width = decimalPointWidth + 1) +
    io.req.bits.decimalPoint
  val one = SInt(1) << decimal

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
  DSP(SInt(0), SInt(0), UInt(0))
  dsp.d := ((dsp.a * dsp.b) >> dsp.c)(elementWidth - 1, 0)

  // Default values
  acc := acc
  reqSent := reqSent
  io.req.ready := Bool(false)
  io.resp.valid := Bool(false)
  io.resp.bits.state := state
  io.resp.bits.index := io.req.bits.index
  io.resp.bits.data := dataOut
  io.resp.bits.incWriteCount := Bool(false)
  index := index
  // Activation function unit default values
  af.io.req.valid := Bool(false)
  af.io.req.bits.in := UInt(0)
  af.io.req.bits.decimal := io.req.bits.decimalPoint
  af.io.req.bits.steepness := io.req.bits.steepness
  af.io.req.bits.activationFunction := io.req.bits.activationFunction

  // State-driven logic
  switch (state) {
    is (PE_states('e_PE_UNALLOCATED)) {
      state := Mux(io.req.valid, PE_states('e_PE_GET_INFO), state)
      io.req.ready := Bool(true)
      index := UInt(0)
      reqSent := Bool(false)
    }
    is (PE_states('e_PE_GET_INFO)) {
      dataOut := UInt(0)
      index := UInt(0)
      when (!reqSent) {
        io.resp.valid := Bool(true)
        reqSent := io.req.valid
      } .elsewhen (io.req.valid) {
        reqSent := Bool(false)
        state := PE_states('e_PE_REQUEST_INPUTS_AND_WEIGHTS)
      }
    }
    is (PE_states('e_PE_REQUEST_INPUTS_AND_WEIGHTS)) {
      when (!reqSent) {
        io.resp.valid := Bool(true)
        reqSent := io.req.valid
      } .elsewhen (io.req.valid) {
        reqSent := Bool(false)
        state := PE_states('e_PE_RUN)
      }
    }
    is (PE_states('e_PE_RUN)) {
      val blockIndex = index(log2Up(elementsPerBlock) - 1, 0)
      when (index === (io.req.bits.numWeights - UInt(1))) {
        state := PE_states('e_PE_ACTIVATION_FUNCTION)
      } .elsewhen (blockIndex === UInt(elementsPerBlock - 1)) {
        state := PE_states('e_PE_REQUEST_INPUTS_AND_WEIGHTS)
      }
      DSP(io.req.bits.iBlock(blockIndex), io.req.bits.wBlock(blockIndex), decimal)
      acc := acc + dsp.d
      index := index + UInt(1)
      printf("[INFO] PE: run 0x%x + (0x%x * 0x%x) >> 0x%x = 0x%x\n",
        acc, io.req.bits.iBlock(blockIndex), io.req.bits.wBlock(blockIndex),
        decimal, acc + dsp.d)
    }
    is (PE_states('e_PE_ACTIVATION_FUNCTION)) {
      af.io.req.valid := Bool(true)
      af.io.req.bits.in := acc
      when (af.io.resp.valid) {
        state := PE_states('e_PE_DONE)
      }
      dataOut := Mux(af.io.resp.valid, af.io.resp.bits.out, dataOut)
    }
    is (PE_states('e_PE_DONE)) {
      when (io.req.valid) {
        state := PE_states('e_PE_UNALLOCATED)
      }
      io.resp.bits.incWriteCount := Bool(true)
      io.resp.valid := Bool(true)
    }
  }

  assert (!(state === PE_states('e_PE_ERROR)), "[ERROR] PE is in error state\n")
}

class ProcessingElementLearn(implicit p: Parameters)
    extends ProcessingElement()(p) {
  override lazy val io = new ProcessingElementInterfaceLearn
  override lazy val af = Module(new ActivationFunctionLearn)

  val weightWB = Reg(Vec.fill(elementsPerBlock){SInt(width=elementWidth)})
  val derivative = Reg(SInt(width = elementWidth)) //delta
  val errorOut = Reg(SInt(width = elementWidth)) //ek
  val mse = Reg(UInt(width = elementWidth))

  // Defaults
  derivative := derivative
  io.resp.bits.dataBlock := weightWB
  io.resp.bits.error := errorOut
  af.io.req.bits.afType := e_AF_DO_ACTIVATION_FUNCTION
  af.io.req.bits.errorFunction := io.req.bits.errorFunction

  switch (state) {
    is (PE_states('e_PE_UNALLOCATED)) {
      state := Mux(io.req.valid, PE_states('e_PE_GET_INFO), state)
      io.req.ready := Bool(true)
      hasBias := Bool(false)
      index := UInt(0)
      reqSent := Bool(false)
    }
    is (PE_states('e_PE_GET_INFO)) {
      dataOut := UInt(0)
      index := UInt(0)
      when (!reqSent) {
        io.resp.valid := Bool(true)
        reqSent := io.req.valid
      } .elsewhen (io.req.valid) {
        reqSent := Bool(false)
        when ((io.req.bits.stateLearn === e_TTABLE_STATE_FEEDFORWARD ||
          io.req.bits.stateLearn === e_TTABLE_STATE_LEARN_FEEDFORWARD ||
          ((io.req.bits.stateLearn === e_TTABLE_STATE_LEARN_WEIGHT_UPDATE) &&
            (io.req.bits.tType === e_TTYPE_BATCH)))) {
          state := PE_states('e_PE_REQUEST_INPUTS_AND_WEIGHTS)
        } .elsewhen ((io.req.bits.stateLearn === e_TTABLE_STATE_LEARN_ERROR_BACKPROP)) {
          state := PE_states('e_PE_REQUEST_OUTPUTS_ERROR_BACKPROP)
        } .elsewhen ((io.req.bits.stateLearn === e_TTABLE_STATE_LEARN_WEIGHT_UPDATE ||
            io.req.bits.stateLearn === e_TTABLE_STATE_LEARN_UPDATE_SLOPE)) {
          state := PE_states('e_PE_WEIGHT_UPDATE_REQUEST_DELTA)
        }
      }
    }
    is (PE_states('e_PE_REQUEST_INPUTS_AND_WEIGHTS)) {
      // If hasBias is false, then this is the first time we're in this
      // state and we need to load the bias into the accumulator
      hasBias := Bool(true)
      when (hasBias === Bool(false)) {
        acc := io.req.bits.bias
      }

      when (!reqSent) {
        io.resp.valid := Bool(true)
        reqSent := io.req.valid
      } .elsewhen (io.req.valid) {
        reqSent := Bool(false)
        when(io.req.bits.tType === e_TTYPE_BATCH &&
          (io.req.bits.stateLearn === e_TTABLE_STATE_LEARN_ERROR_BACKPROP ||
            io.req.bits.stateLearn === e_TTABLE_STATE_LEARN_UPDATE_SLOPE)){
          state := PE_states('e_PE_RUN_UPDATE_SLOPE)
        } .elsewhen(io.req.bits.stateLearn === e_TTABLE_STATE_LEARN_WEIGHT_UPDATE ||
          (io.req.bits.stateLearn === e_TTABLE_STATE_LEARN_ERROR_BACKPROP)){
          state := PE_states('e_PE_RUN_WEIGHT_UPDATE)
        } .otherwise{
          state := PE_states('e_PE_RUN)
        }
      }
    }
    is (PE_states('e_PE_RUN)) {
      val blockIndex = index(log2Up(elementsPerBlock) - 1, 0)
      when (index === (io.req.bits.numWeights - UInt(1))) {
        state := PE_states('e_PE_ACTIVATION_FUNCTION)
      } .elsewhen (blockIndex === UInt(elementsPerBlock - 1)) {
        state := PE_states('e_PE_REQUEST_INPUTS_AND_WEIGHTS)
      }
      DSP(io.req.bits.iBlock(blockIndex), io.req.bits.wBlock(blockIndex), decimal)
      acc := acc + dsp.d
      // acc := acc + ((io.req.bits.iBlock(blockIndex) *
      //        io.req.bits.wBlock(blockIndex)) >>
      //   decimal)(elementWidth-1,0)
      index := index + UInt(1)
      printf("[INFO] PE: run 0x%x + (0x%x * 0x%x) >> 0x%x = 0x%x\n",
        acc, io.req.bits.iBlock(blockIndex), io.req.bits.wBlock(blockIndex),
        decimal, acc + dsp.d)
    }
    is (PE_states('e_PE_ACTIVATION_FUNCTION)) {
      af.io.req.valid := Bool(true)
      af.io.req.bits.in := acc
      af.io.req.bits.afType := e_AF_DO_ACTIVATION_FUNCTION
      when (af.io.resp.valid) {
        state := PE_states('e_PE_DONE)
      }
      dataOut := Mux(af.io.resp.valid, af.io.resp.bits.out, dataOut)
    }
    is (PE_states('e_PE_DONE)) {
      when (io.req.valid) {
        when (io.req.bits.inLast &&
          io.req.bits.stateLearn === e_TTABLE_STATE_LEARN_FEEDFORWARD) {
          state := PE_states('e_PE_COMPUTE_DERIVATIVE)
        } .otherwise {
          state := PE_states('e_PE_UNALLOCATED)
        }
      }
      io.resp.bits.incWriteCount := Bool(true)
      io.resp.valid := Bool(true)
    }
    is (PE_states('e_PE_COMPUTE_DERIVATIVE)){
      state := Mux((io.req.bits.stateLearn === e_TTABLE_STATE_LEARN_ERROR_BACKPROP),
        PE_states('e_PE_COMPUTE_DELTA), PE_states('e_PE_REQUEST_EXPECTED_OUTPUT))

      val af = io.req.bits.activationFunction
      when (af === e_FANN_LINEAR) {
        printf("[WARN] Linear activation function untested\n")
        when (steepness < UInt(steepnessOffset)) {
          derivative := one >> (UInt(steepnessOffset) - steepness)
          printf("[INFO] PE: derivative linear: 0x%x\n",
            one >> (UInt(steepnessOffset) - steepness))
        } .elsewhen (steepness === UInt(steepnessOffset)) {
          derivative := one
          printf("[INFO] PE: derivative linear: 0x%x\n", one)
        } .otherwise {
          derivative := one << (steepness - UInt(steepnessOffset))
          printf("[INFO] PE: derivative linear: 0x%x\n",
            one << (steepness - UInt(steepnessOffset)))
        }
      } .elsewhen (af === e_FANN_SIGMOID || af === e_FANN_SIGMOID_STEPWISE) {
        DSP(dataOut, one - dataOut, decimal + steepness - UInt(1))
        derivative := dsp.d
        // derivative := (dataOut * (one - dataOut)) >>
        //   (decimal + steepness - UInt(1))
        printf("[INFO] PE: derivative sigmoid: 0x%x\n", dsp.d)
      } .elsewhen (af === e_FANN_SIGMOID_SYMMETRIC ||
        af === e_FANN_SIGMOID_SYMMETRIC_STEPWISE) {
        val steepness = io.req.bits.steepness
        DSP(dataOut, dataOut, decimal)
        when (steepness < UInt(steepnessOffset)) {
          derivative := (one - dsp.d) >> (UInt(steepnessOffset) - steepness)
          printf("[INFO] PE: derivative sigmoid symmetric: 0x%x\n",
            (one - dsp.d) >> (UInt(steepnessOffset) - steepness))
        } .elsewhen (steepness === UInt(steepnessOffset)) {
          derivative := one - dsp.d
          printf("[INFO] PE: derivative sigmoid symmetric: 0x%x\n",
            one - dsp.d)
        } .otherwise {
          derivative := (one - dsp.d) << (steepness - UInt(steepnessOffset))
          printf("[INFO] PE: derivative sigmoid symmetric: 0x%x\n",
            (one - dsp.d) << (steepness - UInt(steepnessOffset)))
        }
      }
    }
    is (PE_states('e_PE_REQUEST_EXPECTED_OUTPUT)) {
      when (!reqSent) {
        io.resp.valid := Bool(true)
        reqSent := io.req.valid
      } .elsewhen (io.req.valid) {
        reqSent := Bool(false)
        state := PE_states('e_PE_COMPUTE_ERROR)
      }
    }
    is (PE_states('e_PE_COMPUTE_ERROR)) {
      val error = io.req.bits.learnReg - dataOut
      val errorSym = error >> UInt(1)
      // Divide by 2 should be conditional on being in a symmetric
      when (io.req.bits.activationFunction === e_FANN_SIGMOID_SYMMETRIC ||
        io.req.bits.activationFunction === e_FANN_SIGMOID_SYMMETRIC_STEPWISE) {
        errorOut := errorSym
        DSP(errorSym, errorSym, decimal)
        mse := dsp.d
        // mse := (errorSym * errorSym) >> decimal
        printf("[INFO] PE: errorOut and Error square set to 0x%x and  0x%x\n",
          errorSym, dsp.d)
      } .otherwise {
        errorOut := error
        DSP(error, error, decimal)
        mse := dsp.d
        // mse := (error * error) >> decimal
        printf("[INFO] PE: errorOut and Error square set to 0x%x and  0x%x\n",
          error, dsp.d)
      }
      state := PE_states('e_PE_ERROR_FUNCTION)
    }
    is (PE_states('e_PE_ERROR_FUNCTION)) {
      af.io.req.valid := Bool(true)
      af.io.req.bits.in := errorOut
      af.io.req.bits.afType := e_AF_DO_ERROR_FUNCTION
      errorOut := Mux(af.io.resp.valid, af.io.resp.bits.out, errorOut)
      when (af.io.resp.valid) {
        state := PE_states('e_PE_COMPUTE_DELTA)
        printf("[INFO] PE: errFn(0x%x) = 0x%x\n", errorOut, af.io.resp.bits.out)
      }
    }
    is (PE_states('e_PE_COMPUTE_DELTA)){
      // Reset the index in preparation for doing the deltas in the
      // previous layer
      val der = Mux(derivative === SInt(0), SInt(1), derivative)
      index := UInt(0)

      switch(io.req.bits.stateLearn){
        is(e_TTABLE_STATE_LEARN_FEEDFORWARD){
          DSP(der, errorOut, decimal)
          errorOut := dsp.d
          // errorOut := (der * errorOut) >> decimal
          printf("[INFO] PE: delta (output) 0x%x * 0x%x = 0x%x\n",
            der, errorOut, dsp.d)
          state := PE_states('e_PE_DELTA_WRITE_BACK)
        }
        is(e_TTABLE_STATE_LEARN_ERROR_BACKPROP){
          DSP(der, io.req.bits.dw_in, decimal)
          errorOut := dsp.d
          // errorOut := (der * io.req.bits.dw_in) >> decimal
          printf("[INFO] PE sees errFn*derivative 0x%x\n", dsp.d)
          when(io.req.bits.inFirst) {
            state := PE_states('e_PE_REQUEST_INPUTS_AND_WEIGHTS)
          }.otherwise {
            state := PE_states('e_PE_DELTA_WRITE_BACK)
          }
        }
      }
    }
    is (PE_states('e_PE_DELTA_WRITE_BACK)){
      // This is the "last" writeback for a group, so we turn on the
      // `incWriteCount` flag to tell the Register File to increment its write
      // count
      when (io.req.valid) {
        when((io.req.bits.stateLearn === e_TTABLE_STATE_LEARN_ERROR_BACKPROP)){
          state := PE_states('e_PE_ERROR_BACKPROP_REQUEST_WEIGHTS)
        }.otherwise {
          state := Mux(io.req.bits.inLast &&
            io.req.bits.stateLearn === e_TTABLE_STATE_LEARN_FEEDFORWARD,
            PE_states('e_PE_ERROR_BACKPROP_REQUEST_WEIGHTS),
            PE_states('e_PE_UNALLOCATED))
        }
      }
      io.resp.bits.incWriteCount := Bool(true)
      io.resp.valid := Bool(true)
    }
    is (PE_states('e_PE_ERROR_BACKPROP_REQUEST_WEIGHTS)) {
      when (!reqSent) {
        io.resp.valid := Bool(true)
        reqSent := io.req.valid
      } .elsewhen (io.req.valid) {
        reqSent := Bool(false)
        state := PE_states('e_PE_ERROR_BACKPROP_DELTA_WEIGHT_MUL)
      }
    }
    is (PE_states('e_PE_ERROR_BACKPROP_DELTA_WEIGHT_MUL)) {
      val blockIndex = index(log2Up(elementsPerBlock) - 1, 0)
      // Loop over all the weights in the weight buffer, multiplying
      // these by their delta
      when (index === (io.req.bits.numWeights - UInt(1)) ||
        blockIndex === UInt(elementsPerBlock - 1)) {
        state := PE_states('e_PE_ERROR_BACKPROP_WEIGHT_WB)
      }
      DSP(errorOut, io.req.bits.wBlock(blockIndex), decimal)
      weightWB(blockIndex) := dsp.d
      // weightWB(blockIndex) := (errorOut * io.req.bits.wBlock(blockIndex)) >>
      //   decimal
      printf("[INFO] PE: d*weight (0x%x * 0x%x) >> 0x%x = 0x%x\n",
        errorOut, io.req.bits.wBlock(blockIndex), decimal,
        dsp.d)
      index := index + UInt(1)
    }
    is (PE_states('e_PE_ERROR_BACKPROP_WEIGHT_WB)) {
      val blockIndex = index(log2Up(elementsPerBlock) - 1, 0)
      io.resp.bits.incWriteCount := index === io.req.bits.numWeights
      when (io.req.valid) {
        when (index === io.req.bits.numWeights) {
          state := PE_states('e_PE_UNALLOCATED)
        } .otherwise {
          state := PE_states('e_PE_ERROR_BACKPROP_REQUEST_WEIGHTS)
        }
      }

      io.resp.valid := Bool(true)
    }
    is (PE_states('e_PE_REQUEST_OUTPUTS_ERROR_BACKPROP)) {
      when (!reqSent) {
        io.resp.valid := Bool(true)
        reqSent := io.req.valid
      } .elsewhen (io.req.valid) {
        reqSent := Bool(false)
        state := PE_states('e_PE_REQUEST_DELTA_WEIGHT_PRODUCT_ERROR_BACKPROP)
      }
    }
    is (PE_states('e_PE_REQUEST_DELTA_WEIGHT_PRODUCT_ERROR_BACKPROP)) {
      dataOut := io.req.bits.learnReg

      when (!reqSent) {
        io.resp.valid := Bool(true)
        reqSent := io.req.valid
      } .elsewhen (io.req.valid) {
        reqSent := Bool(false)
        state := PE_states('e_PE_COMPUTE_DERIVATIVE)
      }
    }
    is(PE_states('e_PE_RUN_UPDATE_SLOPE)){
      val delta = Mux(io.req.bits.inFirst, errorOut, io.req.bits.learnReg)
      val blockIndex = index(log2Up(elementsPerBlock) - 1, 0)
      when (index === (io.req.bits.numWeights - UInt(1)) ||
        blockIndex === UInt(elementsPerBlock - 1)) {
        state := PE_states('e_PE_SLOPE_WB)
      }
      DSP(delta, io.req.bits.iBlock(blockIndex), decimal)
      weightWB(blockIndex) := dsp.d
      printf("[INFO] PE: update slope 0x%x * 0x%x = 0x%x\n",
        delta, io.req.bits.iBlock(blockIndex),
        dsp.d)
      index := index + UInt(1)
    }
    is(PE_states('e_PE_SLOPE_WB)){
      val delta = Mux(io.req.bits.inFirst, errorOut, io.req.bits.learnReg)
      val nextState = Mux(index === io.req.bits.numWeights,
        PE_states('e_PE_SLOPE_BIAS_WB),
        PE_states('e_PE_REQUEST_INPUTS_AND_WEIGHTS))
      when (io.req.valid) { state := nextState }
      io.resp.valid := Bool(true)
      // Setup the bias to be written back
      printf("[INFO] PE: bias wb: 0x%x\n", delta)
      dataOut := delta
    }
    is (PE_states('e_PE_SLOPE_BIAS_WB)) {
      when (io.req.valid) { state := PE_states('e_PE_UNALLOCATED) }
      io.resp.valid := Bool(true)
    }
    is (PE_states('e_PE_WEIGHT_UPDATE_REQUEST_DELTA)){
      when (!reqSent) {
        io.resp.valid := Bool(true)
        reqSent := io.req.valid
      } .elsewhen (io.req.valid) {
        reqSent := Bool(false)
        state := PE_states('e_PE_REQUEST_INPUTS_AND_WEIGHTS)
      }
    }
    is (PE_states('e_PE_RUN_WEIGHT_UPDATE)){
      val blockIndex = index(log2Up(elementsPerBlock) - 1, 0)
      when (index === (io.req.bits.numWeights - UInt(1)) ||
        blockIndex === UInt(elementsPerBlock - 1)) {
        state := PE_states('e_PE_WEIGHT_UPDATE_WRITE_BACK)
      }
      val delta = (Mux(io.req.bits.inFirst, errorOut, io.req.bits.learnReg) *
        io.req.bits.learningRate) >> decimal
      val weightDecay = (-io.req.bits.wBlock(blockIndex) * io.req.bits.lambda) >>
        decimal
      when (io.req.bits.tType === e_TTYPE_BATCH) {
        DSP(io.req.bits.iBlock(blockIndex), io.req.bits.learningRate.toSInt,
          decimal)
        weightWB(blockIndex) := dsp.d + weightDecay
        printf("[INFO] PE: weight update %d: 0x%x * 0x%x + 0x%x = 0x%x\n",
          blockIndex,
          io.req.bits.iBlock(blockIndex), io.req.bits.learningRate, weightDecay,
          dsp.d + weightDecay)
      } .otherwise {
        DSP(delta, io.req.bits.iBlock(blockIndex), decimal)
        weightWB(blockIndex) := dsp.d + weightDecay
        printf("[INFO] PE: weight update %d: 0x%x * 0x%x + 0x%x = 0x%x\n",
          blockIndex,
          io.req.bits.iBlock(blockIndex), delta, weightDecay,
          dsp.d + weightDecay)
      }
      index := index + UInt(1)
      dataOut := delta
    }
    is (PE_states('e_PE_WEIGHT_UPDATE_WRITE_BACK)){
      when (io.req.valid) {
        printf("[INFO] PE: weight update writeback index/numWeights 0x%x/0x%x\n",
          index, io.req.bits.numWeights)
      }
      val nextState = Mux(index === io.req.bits.numWeights,
        Mux(io.req.bits.tType === e_TTYPE_BATCH,
          PE_states('e_PE_WEIGHT_UPDATE_REQUEST_BIAS),
          PE_states('e_PE_WEIGHT_UPDATE_WRITE_BIAS)),
        PE_states('e_PE_REQUEST_INPUTS_AND_WEIGHTS))
      when (io.req.valid) { state := nextState }
      io.resp.valid := Bool(true)
    }
    is (PE_states('e_PE_WEIGHT_UPDATE_REQUEST_BIAS)) {
      when (!reqSent) {
        io.resp.valid := Bool(true)
        reqSent := io.req.valid
      } .elsewhen (io.req.valid) {
        reqSent := Bool(false)
        state := PE_states('e_PE_WEIGHT_UPDATE_WAIT_FOR_BIAS_d0)
      }
    }
    is (PE_states('e_PE_WEIGHT_UPDATE_WAIT_FOR_BIAS_d0)) {
      state := PE_states('e_PE_WEIGHT_UPDATE_WRITE_BIAS)
      DSP(io.req.bits.dw_in, io.req.bits.learningRate.toSInt, decimal)
      dataOut := dsp.d
    }
    is (PE_states('e_PE_WEIGHT_UPDATE_WRITE_BIAS)) {
      when (io.req.valid) { state := PE_states('e_PE_UNALLOCATED) }
      io.resp.valid := Bool(true)
    }
  }
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
