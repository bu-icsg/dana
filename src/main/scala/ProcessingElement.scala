// See LICENSE for license details.

package dana

import Chisel._
import cde.Parameters

class ProcessingElementReq(implicit p: Parameters) extends DanaBundle()(p) {
  val numWeights = UInt(width = 8)            // [TODO] fragile
  val index = UInt(width = log2Up(peTableNumEntries))
  val decimalPoint = UInt(decimalPointWidth)
  val steepness = UInt(steepnessWidth)
  val activationFunction = UInt(activationFunctionWidth)
  val iBlock = Vec(elementsPerBlock, SInt(elementWidth))
  val wBlock = Vec(elementsPerBlock, SInt(elementWidth))
  val bias = SInt(elementWidth)
}

class ProcessingElementReqLearn(implicit p: Parameters)
    extends ProcessingElementReq()(p) {
  val errorFunction = UInt(width = log2Up(2)) // [TODO] fragile
  val learningRate = UInt(width = 16)         // [TODO] fragile
  val lambda = SInt(width = 16)               // [TODO] fragile
  val learnReg = SInt(elementWidth)
  val stateLearn = UInt(width = log2Up(8))    // [TODO] fragile
  val inLast = Bool()
  val inFirst = Bool()
  val dw_in = SInt(elementWidth)
  val tType = UInt(width = log2Up(3))
}

class ProcessingElementResp(implicit p: Parameters) extends DanaBundle()(p) {
  val data = SInt(width = elementWidth)
  val state = UInt(width = log2Up(PE_states.size))
  val index = UInt(width = log2Up(peTableNumEntries))
  val incWriteCount = Bool()
}

class ProcessingElementRespLearn(implicit p: Parameters)
    extends ProcessingElementResp()(p) {
  val dataBlock = Vec(elementsPerBlock, SInt(width = elementWidth))
  val error = SInt(width = elementWidth)
  val resetWeightPtr = Bool()
}

class ProcessingElementInterface(implicit p: Parameters) extends DanaBundle()(p) {
  // The Processing Element Interface consists of three main
  // components: requests from the PE Table (really kicks to do
  // something), responses to the PE Table, and semi-static data which
  // th PE Table manages and is used by the PEs for computation.
  val req = Decoupled(new ProcessingElementReq).flip
  val resp = Decoupled(new ProcessingElementResp)
}

class ProcessingElementInterfaceLearn(implicit p: Parameters)
    extends ProcessingElementInterface()(p) {
  override val req = Decoupled(new ProcessingElementReqLearn).flip
  override val resp = Decoupled(new ProcessingElementRespLearn)
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
  val eleIndex = index(log2Up(elementsPerBlock) - 1, 0)

  // [TODO] fragile on PE stateu enum (Common.scala)
  val state = Reg(UInt(width = log2Up(PE_states.size)),
    init = PE_states('e_PE_UNALLOCATED))
  val nextState = Wire(UInt(width = log2Up(PE_states.size)))
  nextState := state

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
  val dsp = Module(new DSP).io
  def DSP(a: SInt, b: SInt, c: UInt) {
    dsp.a := a
    dsp.b := b
    dsp.c := c
  }
  DSP(SInt(0), SInt(0), UInt(0))

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
  af.io.req.bits.in := SInt(0)
  af.io.req.bits.decimal := io.req.bits.decimalPoint
  af.io.req.bits.steepness := io.req.bits.steepness
  af.io.req.bits.activationFunction := io.req.bits.activationFunction

  state := state
  // Jump to nextState when we are able to get a request out
  def reqNoResp() = {
    when (io.req.valid) { state := nextState }}
  // Jump to nextState when we get a request out and we receive a
  // response
  def reqWaitForResp() = {
      when (!reqSent) {
        io.resp.valid := Bool(true)
        reqSent := io.req.valid
      } .elsewhen (io.req.valid) {
        reqSent := Bool(false)
        state := nextState }}

  // State-driven logic
  switch (state) {
    is (PE_states('e_PE_UNALLOCATED)) {
      nextState := PE_states('e_PE_GET_INFO)
      reqNoResp()
      io.req.ready := Bool(true)
      hasBias := Bool(false)
      index := UInt(0)
      reqSent := Bool(false)
    }
    is (PE_states('e_PE_GET_INFO)) {
      dataOut := SInt(0)
      index := UInt(0)
      nextState := PE_states('e_PE_REQUEST_INPUTS_AND_WEIGHTS)
      reqWaitForResp()
    }
    is (PE_states('e_PE_REQUEST_INPUTS_AND_WEIGHTS)) {
      // If hasBias is false, then this is the first time we're in this
      // state and we need to load the bias into the accumulator
      hasBias := Bool(true)
      when (hasBias === Bool(false)) {
        acc := io.req.bits.bias
      }
      nextState := PE_states('e_PE_RUN)
      reqWaitForResp()
    }
    is (PE_states('e_PE_RUN)) {
      when (index === (io.req.bits.numWeights - UInt(1))) {
        state := PE_states('e_PE_ACTIVATION_FUNCTION)
      } .elsewhen (eleIndex === UInt(elementsPerBlock - 1)) {
        state := PE_states('e_PE_REQUEST_INPUTS_AND_WEIGHTS)
      }
      DSP(io.req.bits.iBlock(eleIndex), io.req.bits.wBlock(eleIndex), decimal)
      acc := acc + dsp.d
      index := index + UInt(1)
      printfInfo("PE: run 0x%x + (0x%x * 0x%x) >> 0x%x = 0x%x\n",
        acc, io.req.bits.iBlock(eleIndex), io.req.bits.wBlock(eleIndex),
        decimal, acc + dsp.d)
    }
    is (PE_states('e_PE_ACTIVATION_FUNCTION)) {
      af.io.req.valid := Bool(true)
      af.io.req.bits.in := acc
      when (af.io.resp.valid) {
        state := PE_states('e_PE_DONE) }
      dataOut := Mux(af.io.resp.valid, af.io.resp.bits.out, dataOut)
    }
    is (PE_states('e_PE_DONE)) {
      nextState := PE_states('e_PE_UNALLOCATED)
      reqNoResp()
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

  val weightWB = Reg(Vec(elementsPerBlock, SInt(width=elementWidth)))
  val derivative = Reg(SInt(width = elementWidth)) //delta
  val errorOut = Reg(SInt(width = elementWidth)) //ek
  val mse = Reg(SInt(width = elementWidth))
  val dwWritebackDone = Reg(Bool())
  val stateLearn = io.req.bits.stateLearn

  // Defaults
  derivative := derivative
  io.resp.bits.dataBlock := weightWB
  io.resp.bits.error := errorOut
  io.resp.bits.resetWeightPtr := Bool(false)
  af.io.req.bits.afType := e_AF_DO_ACTIVATION_FUNCTION
  af.io.req.bits.errorFunction := io.req.bits.errorFunction

  switch (state) {
    is (PE_states('e_PE_UNALLOCATED)) {
      dwWritebackDone := Bool(false)
    }
    is (PE_states('e_PE_GET_INFO)) {
      nextState := PE_states('e_PE_REQUEST_OUTPUTS_ERROR_BACKPROP)
      when (stateLearn === e_TTABLE_STATE_FEEDFORWARD ||
        stateLearn === e_TTABLE_STATE_LEARN_FEEDFORWARD ||
        ((stateLearn === e_TTABLE_STATE_LEARN_WEIGHT_UPDATE) &&
          (io.req.bits.tType === e_TTYPE_BATCH))) {
        nextState := PE_states('e_PE_REQUEST_INPUTS_AND_WEIGHTS)
      }
      reqWaitForResp()
    }
    is (PE_states('e_PE_REQUEST_INPUTS_AND_WEIGHTS)) {
      when (io.req.bits.tType === e_TTYPE_BATCH) {
        when (stateLearn === e_TTABLE_STATE_LEARN_ERROR_BACKPROP ||
          dwWritebackDone) {
          nextState := PE_states('e_PE_RUN_UPDATE_SLOPE)
        } .elsewhen (stateLearn === e_TTABLE_STATE_LEARN_WEIGHT_UPDATE) {
          nextState := PE_states('e_PE_RUN_WEIGHT_UPDATE) }
      } .elsewhen (io.req.bits.tType === e_TTYPE_INCREMENTAL) {
        when (stateLearn === e_TTABLE_STATE_LEARN_ERROR_BACKPROP ||
          dwWritebackDone) {
          nextState := PE_states('e_PE_RUN_UPDATE_SLOPE) }}
      reqWaitForResp()
    }
    is (PE_states('e_PE_RUN)) {
      when (index === (io.req.bits.numWeights - UInt(1))) {
        state := PE_states('e_PE_ACTIVATION_FUNCTION)
      } .elsewhen (eleIndex === UInt(elementsPerBlock - 1)) {
        state := PE_states('e_PE_REQUEST_INPUTS_AND_WEIGHTS)
      }
      DSP(io.req.bits.iBlock(eleIndex), io.req.bits.wBlock(eleIndex), decimal)
      acc := acc + dsp.d
      index := index + UInt(1)
      printfInfo("PE: run 0x%x + (0x%x * 0x%x) >> 0x%x = 0x%x\n",
        acc, io.req.bits.iBlock(eleIndex), io.req.bits.wBlock(eleIndex),
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
      when (io.req.bits.inLast &&
        stateLearn === e_TTABLE_STATE_LEARN_FEEDFORWARD) {
        nextState := PE_states('e_PE_COMPUTE_DERIVATIVE)
      } .otherwise {
        nextState := PE_states('e_PE_UNALLOCATED)
      }
      reqNoResp()
      io.resp.bits.resetWeightPtr := Bool(true)
    }
    is (PE_states('e_PE_COMPUTE_DERIVATIVE)){
      state := Mux((stateLearn === e_TTABLE_STATE_LEARN_ERROR_BACKPROP),
        PE_states('e_PE_COMPUTE_DELTA), PE_states('e_PE_REQUEST_EXPECTED_OUTPUT))

      val af = io.req.bits.activationFunction
      when (af === e_FANN_LINEAR) {
        printfWarn("Linear activation function untested\n")
        when (steepness < UInt(steepnessOffset)) {
          derivative := one >> (UInt(steepnessOffset) - steepness)
          printfInfo("PE: derivative linear: 0x%x\n",
            one >> (UInt(steepnessOffset) - steepness))
        } .elsewhen (steepness === UInt(steepnessOffset)) {
          derivative := one
          printfInfo("PE: derivative linear: 0x%x\n", one)
        } .otherwise {
          derivative := one << (steepness - UInt(steepnessOffset))
          printfInfo("PE: derivative linear: 0x%x\n",
            one << (steepness - UInt(steepnessOffset)))
        }
      } .elsewhen (af === e_FANN_SIGMOID || af === e_FANN_SIGMOID_STEPWISE) {
        DSP(dataOut, one - dataOut, decimal + steepness - UInt(1))
        derivative := dsp.d
        // derivative := (dataOut * (one - dataOut)) >>
        //   (decimal + steepness - UInt(1))
        printfInfo("PE: derivative sigmoid: 0x%x\n", dsp.d)
      } .elsewhen (af === e_FANN_SIGMOID_SYMMETRIC ||
        af === e_FANN_SIGMOID_SYMMETRIC_STEPWISE) {
        val steepness = io.req.bits.steepness
        DSP(dataOut, dataOut, decimal)
        when (steepness < UInt(steepnessOffset)) {
          derivative := (one - dsp.d) >> (UInt(steepnessOffset) - steepness)
          printfInfo("PE: derivative sigmoid symmetric: 0x%x\n",
            (one - dsp.d) >> (UInt(steepnessOffset) - steepness))
        } .elsewhen (steepness === UInt(steepnessOffset)) {
          derivative := one - dsp.d
          printfInfo("PE: derivative sigmoid symmetric: 0x%x\n",
            one - dsp.d)
        } .otherwise {
          derivative := (one - dsp.d) << (steepness - UInt(steepnessOffset))
          printfInfo("PE: derivative sigmoid symmetric: 0x%x\n",
            (one - dsp.d) << (steepness - UInt(steepnessOffset)))
        }
      }
    }
    is (PE_states('e_PE_REQUEST_EXPECTED_OUTPUT)) {
      nextState := PE_states('e_PE_COMPUTE_ERROR)
      reqWaitForResp()
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
        printfInfo("PE: errorOut and Error square set to 0x%x and  0x%x\n",
          errorSym, dsp.d)
      } .otherwise {
        errorOut := error
        DSP(error, error, decimal)
        mse := dsp.d
        // mse := (error * error) >> decimal
        printfInfo("PE: errorOut and Error square set to 0x%x and  0x%x\n",
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
        printfInfo("PE: errFn(0x%x) = 0x%x\n", errorOut, af.io.resp.bits.out)
      }
    }
    is (PE_states('e_PE_COMPUTE_DELTA)){
      // Reset the index in preparation for doing the deltas in the
      // previous layer
      val der = Mux(derivative === SInt(0), SInt(1), derivative)
      index := UInt(0)

      switch(stateLearn){
        is(e_TTABLE_STATE_LEARN_FEEDFORWARD){
          DSP(der, errorOut, decimal)
          errorOut := dsp.d
          // errorOut := (der * errorOut) >> decimal
          printfInfo("PE: delta (output) 0x%x * 0x%x = 0x%x\n",
            der, errorOut, dsp.d)
          state := PE_states('e_PE_ERROR_BACKPROP_REQUEST_WEIGHTS)
        }
        is(e_TTABLE_STATE_LEARN_ERROR_BACKPROP){
          DSP(der, io.req.bits.dw_in, decimal)
          errorOut := dsp.d
          // errorOut := (der * io.req.bits.dw_in) >> decimal
          printfInfo("PE sees errFn*derivative 0x%x\n", dsp.d)
          when(io.req.bits.inFirst) {
            state := PE_states('e_PE_REQUEST_INPUTS_AND_WEIGHTS)
          }.otherwise {
            state := PE_states('e_PE_ERROR_BACKPROP_REQUEST_WEIGHTS)
          }
        }
      }
    }
    is (PE_states('e_PE_ERROR_BACKPROP_REQUEST_WEIGHTS)) {
      nextState := PE_states('e_PE_ERROR_BACKPROP_DELTA_WEIGHT_MUL)
      reqWaitForResp()
    }
    is (PE_states('e_PE_ERROR_BACKPROP_DELTA_WEIGHT_MUL)) {
      // Loop over all the weights in the weight buffer, multiplying
      // these by their delta
      when (index === (io.req.bits.numWeights - UInt(1)) ||
        eleIndex === UInt(elementsPerBlock - 1)) {
        state := PE_states('e_PE_ERROR_BACKPROP_WEIGHT_WB)
      }
      DSP(errorOut, io.req.bits.wBlock(eleIndex), decimal)
      weightWB(eleIndex) := dsp.d
      // weightWB(eleIndex) := (errorOut * io.req.bits.wBlock(eleIndex)) >>
      //   decimal
      printfInfo("PE: d*weight (0x%x * 0x%x) >> 0x%x = 0x%x\n",
        errorOut, io.req.bits.wBlock(eleIndex), decimal,
        dsp.d)
      index := index + UInt(1)
    }
    is (PE_states('e_PE_ERROR_BACKPROP_WEIGHT_WB)) {
      when (io.req.valid) {
        when (index === io.req.bits.numWeights) {
          index := UInt(0)
          state := PE_states('e_PE_REQUEST_INPUTS_AND_WEIGHTS)
          io.resp.bits.resetWeightPtr := Bool(true)
        } .otherwise {
          state := PE_states('e_PE_ERROR_BACKPROP_REQUEST_WEIGHTS)
        }
      }
      dwWritebackDone := Bool(true)
      io.resp.valid := Bool(true)
    }
    is (PE_states('e_PE_REQUEST_OUTPUTS_ERROR_BACKPROP)) {
      nextState := PE_states('e_PE_REQUEST_DELTA_WEIGHT_PRODUCT_ERROR_BACKPROP)
      reqWaitForResp()
    }
    is (PE_states('e_PE_REQUEST_DELTA_WEIGHT_PRODUCT_ERROR_BACKPROP)) {
      dataOut := io.req.bits.learnReg
      nextState := PE_states('e_PE_COMPUTE_DERIVATIVE)
      reqWaitForResp()
    }
    is(PE_states('e_PE_RUN_UPDATE_SLOPE)){
      // val delta = Mux(io.req.bits.inFirst, errorOut, io.req.bits.learnReg)
      val delta =  errorOut
      // [TODO] This is actually an element index!
      val inLastElement = (index === (io.req.bits.numWeights - UInt(1)) ||
        eleIndex === UInt(elementsPerBlock - 1))
      index := index + UInt(1)
      when (inLastElement) {
        state := PE_states('e_PE_SLOPE_WB)
        when (io.req.bits.tType === e_TTYPE_INCREMENTAL) {
          state := PE_states('e_PE_RUN_WEIGHT_UPDATE)
          // Reset any modifications that have been made to the index
          // as this will be reused during weight update
          // [TODO] #54: Does this actually work?
          index := index(index.getWidth - 1, log2Up(elementsPerBlock)) ##
            UInt(0, width = log2Up(elementsPerBlock))
        }
      }
      DSP(delta, io.req.bits.iBlock(eleIndex), decimal)
      weightWB(eleIndex) := dsp.d
      printfInfo("PE: update slope 0x%x * 0x%x = 0x%x\n",
        delta, io.req.bits.iBlock(eleIndex),
        dsp.d)
    }
    is(PE_states('e_PE_SLOPE_WB)){
      // val delta = Mux(io.req.bits.inFirst, errorOut, io.req.bits.learnReg)
      val delta = errorOut
      nextState := Mux(index === io.req.bits.numWeights,
        PE_states('e_PE_SLOPE_BIAS_WB),
        PE_states('e_PE_REQUEST_INPUTS_AND_WEIGHTS))
      reqNoResp()
      io.resp.valid := Bool(true)
      // Setup the bias to be written back
      printfInfo("PE: bias wb: 0x%x\n", delta)
      printfInfo("    index: 0x%x\n", index)
      dataOut := delta
    }
    is (PE_states('e_PE_SLOPE_BIAS_WB)) {
      nextState := PE_states('e_PE_UNALLOCATED)
      reqNoResp()
      io.resp.valid := Bool(true)
    }
    is (PE_states('e_PE_RUN_WEIGHT_UPDATE)){
      when (index === (io.req.bits.numWeights - UInt(1)) ||
        eleIndex === UInt(elementsPerBlock - 1)) {
        state := PE_states('e_PE_WEIGHT_UPDATE_WRITE_BACK)
      }
      val weightDecay = (-io.req.bits.wBlock(eleIndex) * io.req.bits.lambda) >>
        decimal
      printfInfo("PE: weight decay %d: -1 * 0x%x * 0x%x = 0x%x\n",
        eleIndex,
        io.req.bits.wBlock(eleIndex), io.req.bits.lambda, weightDecay)

      when (io.req.bits.tType === e_TTYPE_BATCH) {
        DSP(io.req.bits.iBlock(eleIndex), io.req.bits.learningRate.toSInt,
          decimal)
        weightWB(eleIndex) := dsp.d + weightDecay
        printfInfo("PE: weight update %d: 0x%x * 0x%x + 0x%x = 0x%x\n",
          eleIndex,
          io.req.bits.iBlock(eleIndex), io.req.bits.learningRate.toSInt,
          weightDecay, dsp.d + weightDecay)
      } .otherwise {
        // [TODO] #54: we're doing incremental learning here, so the
        // source is coming from weightWB
        DSP(weightWB(eleIndex), io.req.bits.learningRate.toSInt, decimal)
        weightWB(eleIndex) := dsp.d + weightDecay
        printfInfo("PE: weight update %d: 0x%x * 0x%x + 0x%x = 0x%x\n",
          eleIndex,
          weightWB(eleIndex), io.req.bits.learningRate, weightDecay,
          dsp.d + weightDecay)
      }
      index := index + UInt(1)
    }
    is (PE_states('e_PE_WEIGHT_UPDATE_WRITE_BACK)){
      when (io.req.valid) {
        printfInfo("PE: weight update writeback index/numWeights 0x%x/0x%x\n",
          index, io.req.bits.numWeights)
        // [TODO] #54: cleanup all this mux nonsense to make it
        // readable. Also, the transition here for the bias update is
        // likely incorrect when doing incremental learning. I think we
        // need to jump into the _d0 state and make sure that the bias
        // is loaded into dw_in prior to that.
        state := PE_states('e_PE_REQUEST_INPUTS_AND_WEIGHTS)
        when (index === io.req.bits.numWeights) {
          when (io.req.bits.tType === e_TTYPE_BATCH) {
            state := PE_states('e_PE_WEIGHT_UPDATE_REQUEST_BIAS)
          } .otherwise {
            state := PE_states('e_PE_WEIGHT_UPDATE_COMPUTE_BIAS)
          }
        }
      }
      io.resp.valid := Bool(true)
    }
    is (PE_states('e_PE_WEIGHT_UPDATE_REQUEST_BIAS)) {
      when (!reqSent) {
        io.resp.valid := Bool(true)
        reqSent := io.req.valid
      } .elsewhen (io.req.valid) {
        reqSent := Bool(false)
        state := PE_states('e_PE_WEIGHT_UPDATE_COMPUTE_BIAS)
      }
    }
    is (PE_states('e_PE_WEIGHT_UPDATE_COMPUTE_BIAS)) {
      state := PE_states('e_PE_WEIGHT_UPDATE_WRITE_BIAS)
      val biasSlope = Mux(io.req.bits.tType === e_TTYPE_BATCH,
        io.req.bits.dw_in, errorOut)
      DSP(biasSlope, io.req.bits.learningRate.toSInt, decimal)
      dataOut := dsp.d
      printfInfo("PE: biasSlope scale 0x%x * 0x%x = 0x%x\n",
        biasSlope, io.req.bits.learningRate.toSInt, dsp.d)
    }
    is (PE_states('e_PE_WEIGHT_UPDATE_WRITE_BIAS)) {
      when (io.req.valid) { state := PE_states('e_PE_UNALLOCATED) }
      io.resp.valid := Bool(true)
    }
  }

  // Assertions

  // [TODO] #54: disallow specific states for certain transaction types
  assert(!(io.req.bits.tType === e_TTYPE_INCREMENTAL &&
    (state === PE_states('e_PE_SLOPE_WB) ||
      state === PE_states('e_PE_SLOPE_BIAS_WB))),
    "PE entered a disallowed state for incremental learning")
}

// [TODO] This whole testbench is broken due to the integration with
// the PE Table
// class ProcessingElementTests(uut: ProcessingElement, isTrace: Boolean = true)
//     extends DanaTester(uut, isTrace) {
//   // Helper functions
//   def getDecimal(): Int = {
//     peek(uut.io.req.bits.decimalPoint).intValue + uut.decimalPointOffset }
//   def getSteepness(): Int = {
//     peek(uut.io.req.bits.steepness).intValue - 4 }
//   def fixedConvert(data: Bits): Double = {
//     peek(data).floatValue() / Math.pow(2, getDecimal) }
//   def fixedConvert(data: Int): Double = {
//     data.floatValue() / Math.pow(2, getDecimal) }
//   def activationFunction(af: Int, data: Bits, steepness: Int = 1): Double = {
//     val x = fixedConvert(data)
//     // printf("[INFO]   af:   %d\n", af)
//     // printf("[INFO]   data: %f\n", x)
//     // printf("[INFO]   stee: %d\n", steepness)
//     af match {
//       // FANN_THRESHOLD
//       case 1 => if (x > 0) 1 else 0
//       // FANN_THRESHOLD_SYMMETRIC
//       case 2 => if (x > 0) 1 else if (x == 0) 0 else -1
//       // FANN_SIGMOID
//       case 3 => 1 / (1 + Math.exp(-Math.pow(2, steepness.toFloat) * x / 0.5))
//       case 4 => 1 / (1 + Math.exp(-Math.pow(2, steepness.toFloat) * x / 0.5))
//       // FANN_SIGMOID_SYMMETRIC
//       case 5 => 2 / (1 + Math.exp(-Math.pow(2, steepness.toFloat) * x / 0.5)) - 1
//       case 6 => 2 / (1 + Math.exp(-Math.pow(2, steepness.toFloat) * x / 0.5)) - 1
//       case _ => 0
//     }
//   }

//   val dataIn = Array.fill(uut.elementsPerBlock){0}
//   val weight = Array.fill(uut.elementsPerBlock){0}
//   var correct = 0
//   printf("[INFO] Sigmoid Activation Function Test\n")
//   for (t <- 0 until 100) {
//     // poke(uut.io.req.bits.decimalPoint, 3)
//     poke(uut.io.req.bits.decimalPoint, rnd.nextInt(8))
//     // poke(uut.io.req.bits.steepness, 4)
//     poke(uut.io.req.bits.steepness, rnd.nextInt(8))
//     // poke(uut.io.req.bits.activationFunction, 5)
//     poke(uut.io.req.bits.activationFunction, rnd.nextInt(5) + 1)
//     correct = 0
//     for (i <- 0 until uut.elementsPerBlock) {
//       dataIn(i) = rnd.nextInt(Math.pow(2, getDecimal + 2).toInt) -
//         Math.pow(2, getDecimal + 1).toInt
//       weight(i) = rnd.nextInt(Math.pow(2, getDecimal + 2).toInt) -
//         Math.pow(2, getDecimal + 1).toInt
//       correct = correct + (dataIn(i) * weight(i) >> getDecimal)
//       // printf("[INFO] In(%d): %f, %f\n", i, fixedConvert(dataIn(i)),
//       //   fixedConvert(weight(i)))
//     }
//     // printf("[INFO] Correct Acc: %f\n", fixedConvert(correct))
//     for (i <- 0 until uut.elementsPerBlock) {
//       poke(uut.io.req.bits.iBlock(i), dataIn(i))
//       poke(uut.io.req.bits.wBlock(i), weight(i))
//     }
//     poke(uut.io.req.valid, 1)
//     step(1)
//     poke(uut.io.req.valid, 0)
//     while(peek(uut.io.resp.valid) == 0) {
//       step(1)
//       // printf("[INFO]   acc: %f\n", fixedConvert(uut.acc))
//     }
//     // printf("[INFO] Acc:         %f\n", fixedConvert(uut.acc))
//     // printf("[INFO] Output:      %f\n", fixedConvert(uut.io.req.bitsOut))
//     // printf("[INFO] AF Good:     %f\n",
//     //   activationFunction(peek(uut.io.activationFunction).toInt, uut.acc,
//     //     getSteepness))
//     expect(uut.acc, correct)
//     expect(Math.abs(fixedConvert(uut.io.resp.bits.data) -
//       activationFunction(peek(uut.io.req.bits.activationFunction).toInt, uut.acc,
//         getSteepness)) < 0.1, "Activation Function Check")
//     step(1)
//   }
// }
