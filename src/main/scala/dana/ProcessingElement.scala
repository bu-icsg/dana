// See LICENSE for license details.

package dana

import Chisel._
import config._

class ProcessingElementReq(implicit p: Parameters) extends DanaBundle()(p) {
  val numWeights         = UInt(8.W)         // [TODO] fragile
  val index              = UInt(log2Up(peTableNumEntries).W)
  val decimalPoint       = UInt(decimalPointWidth.W)
  val steepness          = UInt(steepnessWidth.W)
  val activationFunction = UInt(activationFunctionWidth.W)
  val iBlock             = Vec(elementsPerBlock, SInt(elementWidth.W))
  val wBlock             = Vec(elementsPerBlock, SInt(elementWidth.W))
  val bias               = SInt(elementWidth.W)
}

class ProcessingElementReqLearn(implicit p: Parameters)
    extends ProcessingElementReq()(p) {
  val errorFunction      = UInt(log2Up(2).W) // [TODO] fragile
  val learningRate       = UInt(16.W)        // [TODO] fragile
  val lambda             = SInt(16.W)        // [TODO] fragile
  val learnReg           = SInt(elementWidth.W)
  val stateLearn         = UInt(log2Up(8).W) // [TODO] fragile
  val inLast             = Bool()
  val inFirst            = Bool()
  val dw_in              = SInt(elementWidth.W)
  val tType              = UInt(log2Up(3).W)
}

class ProcessingElementResp(implicit p: Parameters) extends DanaBundle()(p) {
  val data               = SInt(elementWidth.W)
  val state              = UInt(log2Up(PE_states.size).W)
  val index              = UInt(log2Up(peTableNumEntries).W)
  val incWriteCount      = Bool()
}

class ProcessingElementRespLearn(implicit p: Parameters)
    extends ProcessingElementResp()(p) {
  val dataBlock          = Vec(elementsPerBlock, SInt(elementWidth.W))
  val error              = SInt(elementWidth.W)
  val resetWeightPtr     = Bool()
}

class ProcessingElementInterface(implicit p: Parameters) extends DanaBundle()(p) {
  // The Processing Element Interface consists of three main
  // components: requests from the PE Table (really kicks to do
  // something), responses to the PE Table, and semi-static data which
  // th PE Table manages and is used by the PEs for computation.
  val req                = Decoupled(new ProcessingElementReq).flip
  val resp               = Decoupled(new ProcessingElementResp)
}

class ProcessingElementInterfaceLearn(implicit p: Parameters)
    extends ProcessingElementInterface()(p) {
  override val req       = Decoupled(new ProcessingElementReqLearn).flip
  override val resp      = Decoupled(new ProcessingElementRespLearn)
}

class ProcessingElement(id: Int = 0)(implicit p: Parameters) extends DanaModule()(p) {
  override val printfSigil = "PE[" + id + "]: "

  // Interface to the PE Table
  lazy val io = IO(new ProcessingElementInterface)

  // Activation Function module
  lazy val af = Module(new ActivationFunction)

  val index    = Reg(UInt(8.W)) // [TODO] fragile, should match numWeights
  val acc      = Reg(SInt(elementWidth.W))
  val dataOut  = Reg(SInt(elementWidth.W))
  val reqSent  = Reg(Bool())
  val eleIndex = index(log2Up(elementsPerBlock) - 1, 0)

  // [TODO] fragile on PE stateu enum (Common.scala)
  val state = Reg(UInt(log2Up(PE_states.size).W),
    init = PE_states('e_PE_UNALLOCATED))
  val nextState = Wire(UInt(log2Up(PE_states.size).W))
  nextState := state

  // Local state storage. Any and all of these are possible kludges
  // which could be implemented more cleanly.
  val hasBias = Reg(Bool())
  val steepness = steepnessOffset.U - io.req.bits.steepness
  val decimal = decimalPointOffset.U((decimalPointWidth + 1).W) +
    io.req.bits.decimalPoint
  val one = 1.S << decimal

  def applySteepness(x: SInt, steepness: UInt): SInt = {
    val tmp = Wire(SInt())
    when (steepness < (steepnessOffset).U) {
      tmp := x >> ((steepnessOffset).U - steepness)
    } .elsewhen (steepness === (steepnessOffset).U) {
      tmp := x
    } .otherwise {
      tmp := x << (steepness - (steepnessOffset).U)
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
  DSP(0.S, 0.S, 0.U)

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
  af.io.req.bits.in := (0).S
  af.io.req.bits.decimal := io.req.bits.decimalPoint
  af.io.req.bits.steepness := io.req.bits.steepness
  af.io.req.bits.activationFunction := io.req.bits.activationFunction

  state := state
  // Jump to nextState when we are able to get a request out
  def reqNoResp() = {
    when (io.req.valid) { state := nextState }
  }
  // Jump to nextState when we get a request out and we receive a
  // response
  def reqWaitForResp() = {
      when (!reqSent) {
        io.resp.valid := true.B
        reqSent := io.req.valid
      } .elsewhen (io.req.valid) {
        reqSent := false.B
        state := nextState }}
  def reqAf() = {
    when (!reqSent) {
      af.io.req.valid := true.B
      reqSent := true.B
    } .elsewhen (af.io.resp.valid) {
      reqSent := false.B
      state := nextState }}

  // State-driven logic
  switch (state) {
    is (PE_states('e_PE_UNALLOCATED)) {
      nextState := PE_states('e_PE_GET_INFO)
      reqNoResp()
      io.req.ready := true.B
      hasBias := false.B
      index := (0).U
      reqSent := false.B
    }
    is (PE_states('e_PE_GET_INFO)) {
      dataOut := (0).S
      index := (0).U
      nextState := PE_states('e_PE_REQUEST_INPUTS_AND_WEIGHTS)
      reqWaitForResp()
    }
    is (PE_states('e_PE_REQUEST_INPUTS_AND_WEIGHTS)) {
      // If hasBias is false, then this is the first time we're in this
      // state and we need to load the bias into the accumulator
      hasBias := true.B
      when (hasBias === false.B) {
        acc := io.req.bits.bias
      }
      nextState := PE_states('e_PE_RUN)
      reqWaitForResp()
    }
    is (PE_states('e_PE_RUN)) {
      when (index === (io.req.bits.numWeights - (1).U)) {
        state := PE_states('e_PE_ACTIVATION_FUNCTION)
      } .elsewhen (eleIndex === (elementsPerBlock - 1).U) {
        state := PE_states('e_PE_REQUEST_INPUTS_AND_WEIGHTS)
      }
      DSP(io.req.bits.iBlock(eleIndex), io.req.bits.wBlock(eleIndex), decimal)
      acc := acc + dsp.d
      index := index + (1).U
      printfInfo("run 0x%x + (0x%x * 0x%x) >> 0x%x = 0x%x\n",
        acc, io.req.bits.iBlock(eleIndex), io.req.bits.wBlock(eleIndex),
        decimal, acc + dsp.d)
    }
    is (PE_states('e_PE_ACTIVATION_FUNCTION)) {
      reqAf()
      nextState := PE_states('e_PE_DONE)
      af.io.req.bits.in := acc
      dataOut := Mux(af.io.resp.valid, af.io.resp.bits.out, dataOut)
    }
    is (PE_states('e_PE_DONE)) {
      nextState := PE_states('e_PE_UNALLOCATED)
      reqNoResp()
      io.resp.bits.incWriteCount := true.B
      io.resp.valid := true.B
    }
  }

  assert (!(state === PE_states('e_PE_ERROR)), "[ERROR] PE is in error state\n")
}

class ProcessingElementLearn(id: Int = 0)(implicit p: Parameters)
    extends ProcessingElement(id)(p) {
  override lazy val io = IO(new ProcessingElementInterfaceLearn)
  override lazy val af = Module(new ActivationFunctionLearn)

  val weightWB        = Reg(Vec(elementsPerBlock, SInt(elementWidth.W)))
  val derivative      = Reg(SInt(elementWidth.W)) //delta
  val errorOut        = Reg(SInt(elementWidth.W)) //ek
  val mse             = Reg(SInt(elementWidth.W))
  val dwWritebackDone = Reg(Bool())
  val stateLearn = io.req.bits.stateLearn

  // Defaults
  derivative := derivative
  io.resp.bits.dataBlock := weightWB
  io.resp.bits.error := errorOut
  io.resp.bits.resetWeightPtr := false.B
  af.io.req.bits.afType := e_AF_DO_ACTIVATION_FUNCTION
  af.io.req.bits.errorFunction := io.req.bits.errorFunction

  switch (state) {
    is (PE_states('e_PE_UNALLOCATED)) {
      dwWritebackDone := false.B
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
      when (index === (io.req.bits.numWeights - 1.U)) {
        state := PE_states('e_PE_ACTIVATION_FUNCTION)
      } .elsewhen (eleIndex === (elementsPerBlock - 1).U) {
        state := PE_states('e_PE_REQUEST_INPUTS_AND_WEIGHTS)
      }
      DSP(io.req.bits.iBlock(eleIndex), io.req.bits.wBlock(eleIndex), decimal)
      acc := acc + dsp.d
      index := index + 1.U
    }
    is (PE_states('e_PE_ACTIVATION_FUNCTION)) {
      af.io.req.bits.afType := e_AF_DO_ACTIVATION_FUNCTION
    }
    is (PE_states('e_PE_DONE)) {
      when (io.req.bits.inLast &&
        stateLearn === e_TTABLE_STATE_LEARN_FEEDFORWARD) {
        nextState := PE_states('e_PE_COMPUTE_DERIVATIVE)
      } .otherwise {
        nextState := PE_states('e_PE_UNALLOCATED)
      }
      reqNoResp()
      io.resp.bits.resetWeightPtr := true.B
    }
    is (PE_states('e_PE_COMPUTE_DERIVATIVE)){
      state := Mux((stateLearn === e_TTABLE_STATE_LEARN_ERROR_BACKPROP),
        PE_states('e_PE_COMPUTE_DELTA), PE_states('e_PE_REQUEST_EXPECTED_OUTPUT))

      val af = io.req.bits.activationFunction
      when (af === e_FANN_LINEAR) {
        printfWarn("Linear activation function untested\n")
        when (steepness < steepnessOffset.U) {
          derivative := one >> (steepnessOffset.U - steepness)
          printfInfo("derivative linear: 0x%x\n",
            one >> (steepnessOffset.U - steepness))
        } .elsewhen (steepness === steepnessOffset.U) {
          derivative := one
          printfInfo("derivative linear: 0x%x\n", one)
        } .otherwise {
          derivative := one << (steepness - steepnessOffset.U)
          printfInfo("derivative linear: 0x%x\n",
            one << (steepness - steepnessOffset.U))
        }
      } .elsewhen (af === e_FANN_SIGMOID || af === e_FANN_SIGMOID_STEPWISE) {
        DSP(dataOut, one - dataOut, decimal + steepness - 1.U)
        derivative := dsp.d
        printfInfo("derivative sigmoid: 0x%x\n", dsp.d)
      } .elsewhen (af === e_FANN_SIGMOID_SYMMETRIC ||
        af === e_FANN_SIGMOID_SYMMETRIC_STEPWISE) {
        val steepness = io.req.bits.steepness
        DSP(dataOut, dataOut, decimal)
        when (steepness < steepnessOffset.U) {
          derivative := (one - dsp.d) >> (steepnessOffset.U - steepness)
          printfInfo("derivative sigmoid symmetric: 0x%x\n",
            (one - dsp.d) >> (steepnessOffset.U - steepness))
        } .elsewhen (steepness === steepnessOffset.U) {
          derivative := one - dsp.d
          printfInfo("derivative sigmoid symmetric: 0x%x\n",
            one - dsp.d)
        } .otherwise {
          derivative := (one - dsp.d) << (steepness - steepnessOffset.U)
          printfInfo("derivative sigmoid symmetric: 0x%x\n",
            (one - dsp.d) << (steepness - steepnessOffset.U))
        }
      }
    }
    is (PE_states('e_PE_REQUEST_EXPECTED_OUTPUT)) {
      nextState := PE_states('e_PE_COMPUTE_ERROR)
      reqWaitForResp()
    }
    is (PE_states('e_PE_COMPUTE_ERROR)) {
      val error = io.req.bits.learnReg - dataOut
      val errorSym = error >> 1.U
      // Divide by 2 should be conditional on being in a symmetric
      when (io.req.bits.activationFunction === e_FANN_SIGMOID_SYMMETRIC ||
        io.req.bits.activationFunction === e_FANN_SIGMOID_SYMMETRIC_STEPWISE) {
        errorOut := errorSym
        DSP(errorSym, errorSym, decimal)
        mse := dsp.d
        // mse := (errorSym * errorSym) >> decimal
        printfInfo("errorOut and Error square set to 0x%x and  0x%x\n",
          errorSym, dsp.d)
      } .otherwise {
        errorOut := error
        DSP(error, error, decimal)
        mse := dsp.d
        // mse := (error * error) >> decimal
        printfInfo("errorOut and Error square set to 0x%x and  0x%x\n",
          error, dsp.d)
      }
      state := PE_states('e_PE_ERROR_FUNCTION)
    }
    is (PE_states('e_PE_ERROR_FUNCTION)) {
      reqAf()
      nextState := PE_states('e_PE_COMPUTE_DELTA)
      af.io.req.bits.in := errorOut
      af.io.req.bits.afType := e_AF_DO_ERROR_FUNCTION
      errorOut := Mux(af.io.resp.valid, af.io.resp.bits.out, errorOut)
      when (af.io.resp.valid) {
        printfInfo("errFn(0x%x) = 0x%x\n", errorOut, af.io.resp.bits.out)
      }
    }
    is (PE_states('e_PE_COMPUTE_DELTA)){
      // Reset the index in preparation for doing the deltas in the
      // previous layer
      val der = Mux(derivative === 0.S, 1.S, derivative)
      index := 0.U

      switch(stateLearn){
        is(e_TTABLE_STATE_LEARN_FEEDFORWARD){
          DSP(der, errorOut, decimal)
          errorOut := dsp.d
          // errorOut := (der * errorOut) >> decimal
          printfInfo("delta (output) 0x%x * 0x%x = 0x%x\n",
            der, errorOut, dsp.d)
          state := PE_states('e_PE_ERROR_BACKPROP_REQUEST_WEIGHTS)
        }
        is(e_TTABLE_STATE_LEARN_ERROR_BACKPROP){
          DSP(der, io.req.bits.dw_in, decimal)
          errorOut := dsp.d
          // errorOut := (der * io.req.bits.dw_in) >> decimal
          printfInfo("sees errFn*derivative 0x%x\n", dsp.d)
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
      when (index === (io.req.bits.numWeights - 1.U) ||
        eleIndex === (elementsPerBlock - 1).U) {
        state := PE_states('e_PE_ERROR_BACKPROP_WEIGHT_WB)
      }
      DSP(errorOut, io.req.bits.wBlock(eleIndex), decimal)
      weightWB(eleIndex) := dsp.d
      // weightWB(eleIndex) := (errorOut * io.req.bits.wBlock(eleIndex)) >>
      //   decimal
      printfInfo("d*weight (0x%x * 0x%x) >> 0x%x = 0x%x\n",
        errorOut, io.req.bits.wBlock(eleIndex), decimal,
        dsp.d)
      index := index + 1.U
    }
    is (PE_states('e_PE_ERROR_BACKPROP_WEIGHT_WB)) {
      when (io.req.valid) {
        when (index === io.req.bits.numWeights) {
          index := 0.U
          state := PE_states('e_PE_REQUEST_INPUTS_AND_WEIGHTS)
          io.resp.bits.resetWeightPtr := true.B
        } .otherwise {
          state := PE_states('e_PE_ERROR_BACKPROP_REQUEST_WEIGHTS)
        }
      }
      dwWritebackDone := true.B
      io.resp.valid := true.B
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
      val inLastElement = (index === (io.req.bits.numWeights - 1.U) ||
        eleIndex === (elementsPerBlock - 1).U)
      index := index + 1.U
      when (inLastElement) {
        state := PE_states('e_PE_SLOPE_WB)
        when (io.req.bits.tType === e_TTYPE_INCREMENTAL) {
          state := PE_states('e_PE_RUN_WEIGHT_UPDATE)
          // Reset any modifications that have been made to the index
          // as this will be reused during weight update
          // [TODO] #54: Does this actually work?
          index := index(index.getWidth - 1, log2Up(elementsPerBlock)) ##
            0.U(log2Up(elementsPerBlock).W)
        }
      }
      DSP(delta, io.req.bits.iBlock(eleIndex), decimal)
      weightWB(eleIndex) := dsp.d
      printfInfo("update slope 0x%x * 0x%x = 0x%x\n",
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
      io.resp.valid := true.B
      // Setup the bias to be written back (bias is in delta!)
      dataOut := delta
    }
    is (PE_states('e_PE_SLOPE_BIAS_WB)) {
      nextState := PE_states('e_PE_UNALLOCATED)
      reqNoResp()
      io.resp.valid := true.B
      printfInfo("bias wb: 0x%x\n", dataOut)
    }
    is (PE_states('e_PE_RUN_WEIGHT_UPDATE)){
      when (index === (io.req.bits.numWeights - 1.U) ||
        eleIndex === (elementsPerBlock - 1).U) {
        state := PE_states('e_PE_WEIGHT_UPDATE_WRITE_BACK)
      }
      val weightDecay = (-io.req.bits.wBlock(eleIndex) * io.req.bits.lambda) >>
        decimal
      printfInfo("weight decay %d: -1 * 0x%x * 0x%x = 0x%x\n",
        eleIndex,
        io.req.bits.wBlock(eleIndex), io.req.bits.lambda, weightDecay)

      when (io.req.bits.tType === e_TTYPE_BATCH) {
        DSP(io.req.bits.iBlock(eleIndex), io.req.bits.learningRate.asSInt,
          decimal)
        weightWB(eleIndex) := dsp.d + weightDecay
        printfInfo("weight update %d: 0x%x * 0x%x + 0x%x = 0x%x\n",
          eleIndex,
          io.req.bits.iBlock(eleIndex), io.req.bits.learningRate.asSInt,
          weightDecay, dsp.d + weightDecay)
      } .otherwise {
        // [TODO] #54: we're doing incremental learning here, so the
        // source is coming from weightWB
        DSP(weightWB(eleIndex), io.req.bits.learningRate.asSInt, decimal)
        weightWB(eleIndex) := dsp.d + weightDecay
        printfInfo("weight update %d: 0x%x * 0x%x + 0x%x = 0x%x\n",
          eleIndex,
          weightWB(eleIndex), io.req.bits.learningRate, weightDecay,
          dsp.d + weightDecay)
      }
      index := index + 1.U
    }
    is (PE_states('e_PE_WEIGHT_UPDATE_WRITE_BACK)){
      when (io.req.valid) {
        printfInfo("weight update writeback index/numWeights 0x%x/0x%x\n",
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
      io.resp.valid := true.B
    }
    is (PE_states('e_PE_WEIGHT_UPDATE_REQUEST_BIAS)) {
      when (!reqSent) {
        io.resp.valid := true.B
        reqSent := io.req.valid
      } .elsewhen (io.req.valid) {
        reqSent := false.B
        state := PE_states('e_PE_WEIGHT_UPDATE_COMPUTE_BIAS)
      }
    }
    is (PE_states('e_PE_WEIGHT_UPDATE_COMPUTE_BIAS)) {
      state := PE_states('e_PE_WEIGHT_UPDATE_WRITE_BIAS)
      val biasSlope = Mux(io.req.bits.tType === e_TTYPE_BATCH,
        io.req.bits.dw_in, errorOut)
      DSP(biasSlope, io.req.bits.learningRate.asSInt, decimal)
      dataOut := dsp.d
      printfInfo("biasSlope scale 0x%x * 0x%x = 0x%x\n",
        biasSlope, io.req.bits.learningRate.asSInt, dsp.d)
    }
    is (PE_states('e_PE_WEIGHT_UPDATE_WRITE_BIAS)) {
      when (io.req.valid) { state := PE_states('e_PE_UNALLOCATED) }
      io.resp.valid := true.B
    }
  }

  // Assertions

  // [TODO] #54: disallow specific states for certain transaction types
  assert(!(io.req.bits.tType === e_TTYPE_INCREMENTAL &&
    (state === PE_states('e_PE_SLOPE_WB) ||
      state === PE_states('e_PE_SLOPE_BIAS_WB))),
    "PE entered a disallowed state for incremental learning")
}
