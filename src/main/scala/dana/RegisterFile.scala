// See LICENSE.BU for license details.
// See LICENSE.IBM for license details.

package dana

import chisel3._
import chisel3.util._
import scala.math.pow
import xfiles.{TransactionTableNumEntries}
import cde._

class RegisterFileInterface(implicit p: Parameters) extends DanaStatusIO()(p) {
  lazy val pe = Flipped(new PERegisterFileInterface)
  val control = Flipped(new ControlRegisterFileInterface)
  val tTable  = Flipped(new TTableRegisterFileInterface)
}

class RegisterFileInterfaceLearn(implicit p: Parameters)
    extends RegisterFileInterface()(p) {
  override lazy val pe = Flipped(new PERegisterFileInterfaceLearn)
}

class RegisterFileState(implicit p: Parameters) extends DanaBundle()(p) {
  val valid = Bool()
  val totalWrites = UInt(16.W) // [TODO] fragile
  val countWrites = UInt(16.W) // [TODO] fragile
}

class RegisterFileBase[SramIf <: SRAMElementInterface](
  genSram: => Vec[SramIf])(implicit p: Parameters)
    extends DanaModule()(p) {
  lazy val io = IO(new RegisterFileInterface)
  override val printfSigil = "dana.RegFile: "
  val mem = genSram

  val state = Reg(Vec(transactionTableNumEntries * 2, new RegisterFileState))
  val stateToggle = Reg(Vec(transactionTableNumEntries, UInt(1.W)))
  val tTableRespValid = Reg(Bool())
  val tTableRespTIdx = Reg(UInt(log2Up(transactionTableNumEntries).W))
  val tTableRespAddr = Reg(UInt(log2Up(regFileNumElements).W))

  // Default values for SRAMs
  for (transaction <- 0 until transactionTableNumEntries) {
    for (port <- 0 until mem(transaction).numPorts) {
      mem(transaction).we(port) := false.B
      mem(transaction).re(port) := false.B
      mem(transaction).din(port) := 0.U
      mem(transaction).dinElement(port) := 0.U
      mem(transaction).addr(port) := 0.U
    }
  }
  // Default Control interface values
  io.control.req.ready := true.B
  io.control.resp.valid := false.B
  io.control.resp.bits.tIdx := 0.U
  // Default Transaction Table interface values
  io.tTable.resp.valid := false.B
  io.tTable.resp.bits.data := 0.U

  // Requests from the Processing Element Table
  when (io.pe.req.valid) {
    // Take action based on whether this is a write or a read
    val tIdx = io.pe.req.bits.tIdx
    val sIdx = io.pe.req.bits.tIdx ## io.pe.req.bits.location
    when (io.pe.req.bits.isWrite) { // This is a Write
      mem(tIdx).we(0) := true.B
      mem(tIdx).addr(0) := io.pe.req.bits.addr
      mem(tIdx).dinElement(0) := io.pe.req.bits.data.asUInt
      printfInfo("PE write element tIdx/Addr/Data 0x%x/0x%x/0x%x\n",
        tIdx, io.pe.req.bits.addr, io.pe.req.bits.data)
      // Increment the write count and generate a response to the
      // control module if this puts us at the write count
      when (io.pe.req.bits.incWriteCount) {
        state(sIdx).countWrites := state(sIdx).countWrites + 1.U
        printfInfo("write count loc/seen/expected 0x%x/0x%x/0x%x\n",
          sIdx, state(sIdx).countWrites + 1.U, state(sIdx).totalWrites)
        when (state(sIdx).countWrites === state(sIdx).totalWrites - 1.U) {
          io.control.resp.valid := true.B
          io.control.resp.bits.tIdx := tIdx
        }
      }
    } .otherwise {                  // This is a read
      mem(tIdx).re(0) := true.B
      mem(tIdx).addr(0) := io.pe.req.bits.addr
      printfInfo("PE read tIdx/Addr 0x%x/0x%x\n", tIdx,
        io.pe.req.bits.addr)
    }
  }

  // Requests from the Control module
  when (io.control.req.valid) {
    val tIdx = io.control.req.bits.tIdx
    val location = io.control.req.bits.location
    state(tIdx << 1.U | location).valid := true.B
    state(tIdx << 1.U | location).totalWrites :=
      io.control.req.bits.totalWrites
    state(tIdx << 1.U | location).countWrites := 0.U
    printfInfo("Control req tIdx/location/totalWrites 0x%x/0x%x/0x%x\n",
      tIdx, location, io.control.req.bits.totalWrites)
  }

  // Requests form the Transaction Table
  when (io.tTable.req.valid) {
    val tIdx = io.tTable.req.bits.tidIdx
    switch (io.tTable.req.bits.reqType) {
      is (e_TTABLE_REGFILE_WRITE) {
        printfInfo("Saw TTable write idx/Addr/Data 0x%x/0x%x/0x%x\n",
          tIdx, io.tTable.req.bits.addr, io.tTable.req.bits.data)
        mem(tIdx).we(0) := true.B
        mem(tIdx).dinElement(0) := io.tTable.req.bits.data
        mem(tIdx).addr(0) := io.tTable.req.bits.addr
      }
      is (e_TTABLE_REGFILE_READ) {
        printfInfo("Saw TTable read Addr 0x%x\n",
          io.tTable.req.bits.addr)
        mem(tIdx).re(0) := true.B
        mem(tIdx).dinElement(0) := io.tTable.req.bits.data
        mem(tIdx).addr(0) := io.tTable.req.bits.addr
      }
    }
  }

  val readReqValid_d0 = Reg(next = io.pe.req.valid && !io.pe.req.bits.isWrite)
  val peIndex_d0 = Reg(next = io.pe.req.bits.peIndex)
  val tIndex_d0 = Reg(next = io.pe.req.bits.tIdx)

  io.pe.resp.valid := readReqValid_d0
  io.pe.resp.bits.peIndex := peIndex_d0
  io.pe.resp.bits.data := mem(tIndex_d0).dout(0)
  when (io.pe.resp.valid) {
    printfInfo("PE resp PE/Data 0x%x/0x%x\n",
      io.pe.resp.bits.peIndex, io.pe.resp.bits.data);
  }
  // Transaction Table Response
  tTableRespValid := io.tTable.req.valid &&
    io.tTable.req.bits.reqType === e_TTABLE_REGFILE_READ
  tTableRespTIdx := io.tTable.req.bits.tidIdx
  tTableRespAddr := io.tTable.req.bits.addr
  when (tTableRespValid) {
  val memDataVec = Vec((0 until elementsPerBlock).map(i =>
    (mem(tTableRespTIdx).dout(0))(elementWidth * (i + 1) - 1, elementWidth * i)))
    io.tTable.resp.valid := true.B
    io.tTable.resp.bits.data :=
      memDataVec(tTableRespAddr(log2Up(elementsPerBlock)-1,0))
    printfInfo("Returning data to TTable 0x%x\n",
      io.tTable.resp.bits.data)
  }

  // Reset
  when (reset) {for (i <- 0 until transactionTableNumEntries * 2) {
    state(i).valid := false.B}}

  // Assertions

  // The number of writes that we've seen should never be greater than
  // the number of expected writes.
  assert(!Vec((0 until transactionTableNumEntries * 2).map(
    i => (state(i).valid &&
      state(i).countWrites > state(i).totalWrites))).contains(true.B),
    printfSigil ++
    "The total writes to a Regsiter File entry exceeded the number expected")

  // A request to change the total number of writes should only happen
  // to a state entry marked as valid if it's countWrites is equal to
  // the totalWrites.
  assert(!(io.control.req.valid &&
    state(io.control.req.bits.tIdx << 1.U |
      io.control.req.bits.location).valid &&
    (state(io.control.req.bits.tIdx << 1.U |
      io.control.req.bits.location).countWrites =/=
      state(io.control.req.bits.tIdx << 1.U |
        io.control.req.bits.location).totalWrites)), printfSigil ++
    "RegFile totalWrites being changed when valid && (countWrites != totalWrites)")

  // We shouldn't be trying to write data outside of the bounds of the
  // memory
  (0 until transactionTableNumEntries).map(i =>
    assert(!(mem(i).addr(0) >= regFileNumElements.U), printfSigil ++
      "RegFile address (read or write) is out of bounds"))

}

class RegisterFile(implicit p: Parameters) extends RegisterFileBase (
  Vec.fill(p(TransactionTableNumEntries))(Module(new SRAMElement(
    dataWidth = p(BitsPerBlock),
    sramDepth = pow(2, log2Up(p(RegFileNumBlocks))).toInt,
    numPorts = 1,
    elementWidth = p(DanaDataBits))).io))(p)

class RegisterFileLearn(implicit p: Parameters) extends RegisterFileBase (
  Vec.fill(p(TransactionTableNumEntries))(Module(new SRAMElementIncrement(
    dataWidth = p(BitsPerBlock),
    sramDepth = pow(2, log2Up(p(RegFileNumBlocks))).toInt,
    numPorts = 1,
    elementWidth = p(DanaDataBits))).io))(p) {
  override lazy val io = IO(new RegisterFileInterfaceLearn)

  val readReqType_d0 = Reg(next = io.pe.req.bits.reqType)
  io.pe.resp.bits.reqType := readReqType_d0

  for (transaction <- 0 until transactionTableNumEntries) {
    for (port <- 0 until mem(transaction).numPorts) {
      mem(transaction).wType(port) := 0.U
    }
  }

  when (io.pe.req.valid) {
    // Take action based on whether this is a write or a read
    val tIdx = io.pe.req.bits.tIdx
    val sIdx = io.pe.req.bits.tIdx ## io.pe.req.bits.location
    when (io.pe.req.bits.isWrite) { // This is a Write
      mem(tIdx).addr(0) := io.pe.req.bits.addr
      switch (io.pe.req.bits.reqType) {
        is (e_PE_WRITE_ELEMENT) {
          mem(tIdx).wType(0) := 0.U
          mem(tIdx).dinElement(0) := io.pe.req.bits.data.asUInt
          printfInfo("PE write element tIdx/Addr/Data 0x%x/0x%x/0x%x\n",
            tIdx, io.pe.req.bits.addr, io.pe.req.bits.data)
        }
        is (e_PE_WRITE_BLOCK_NEW) {
          mem(tIdx).wType(0) := 1.U
          mem(tIdx).din(0) := io.pe.req.bits.dataBlock
          printfInfo("PE write block new tIdx/Addr/Data 0x%x/0x%x/0x%x\n",
            tIdx, io.pe.req.bits.addr, io.pe.req.bits.dataBlock)
        }
        is (e_PE_WRITE_BLOCK_ACC) {
          mem(tIdx).wType(0) := 2.U
          mem(tIdx).din(0) := io.pe.req.bits.dataBlock
          printfInfo("PE write block inc tIdx/Addr/Data 0x%x/0x%x/0x%x\n",
            tIdx, io.pe.req.bits.addr, io.pe.req.bits.dataBlock)
        }
        // Kludge to kill the write _if_ we're just incrementing the
        // write count
        is (e_PE_INCREMENT_WRITE_COUNT) {
          printfInfo("PE increment write count\n")
          mem(tIdx).we(0) := false.B
        }
      }
    }
  }
}
