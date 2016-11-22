// See LICENSE for license details.

package dana

import chisel3._
import chisel3.util._
import scala.math.pow
import xfiles.{TransactionTableNumEntries}
import cde.{Parameters}

class RegisterFileInterface(implicit p: Parameters) extends DanaBundle()(p) {
  lazy val pe = (new PERegisterFileInterface).flip
  val control = (new ControlRegisterFileInterface).flip
  val tTable  = (new TTableRegisterFileInterface).flip
}

class RegisterFileInterfaceLearn(implicit p: Parameters)
    extends RegisterFileInterface()(p) {
  override lazy val pe = (new PERegisterFileInterfaceLearn).flip
}

class RegisterFileState(implicit p: Parameters) extends DanaBundle()(p) {
  val valid = Bool()
  val totalWrites = UInt(width = 16) // [TODO] fragile
  val countWrites = UInt(width = 16) // [TODO] fragile
}

class RegisterFileBase[SramIf <: SRAMElementInterface](
  genSram: => Vec[SramIf])(implicit p: Parameters)
    extends DanaModule()(p) {
  lazy val io = IO(new RegisterFileInterface)
  val mem = genSram

  val state = Reg(Vec(transactionTableNumEntries * 2, new RegisterFileState))
  val stateToggle = Reg(Vec(transactionTableNumEntries, UInt(width=1)))
  val tTableRespValid = Reg(Bool())
  val tTableRespTIdx = Reg(UInt(width=log2Up(transactionTableNumEntries)))
  val tTableRespAddr = Reg(UInt(width=log2Up(regFileNumElements)))

  // Default values for SRAMs
  for (transaction <- 0 until transactionTableNumEntries) {
    for (port <- 0 until mem(transaction).numPorts) {
      mem(transaction).we(port) := Bool(false)
      mem(transaction).din(port) := UInt(0)
      mem(transaction).dinElement(port) := UInt(0)
      mem(transaction).addr(port) := UInt(0)
    }
  }
  // Default Control interface values
  io.control.req.ready := Bool(true)
  io.control.resp.valid := Bool(false)
  io.control.resp.bits.tIdx := UInt(0)
  // Default Transaction Table interface values
  io.tTable.resp.valid := Bool(false)
  io.tTable.resp.bits.data := UInt(0)

  // Requests from the Processing Element Table
  when (io.pe.req.valid) {
    // Take action based on whether this is a write or a read
    val tIdx = io.pe.req.bits.tIdx
    val sIdx = io.pe.req.bits.tIdx ## io.pe.req.bits.location
    when (io.pe.req.bits.isWrite) { // This is a Write
      mem(tIdx).we(0) := Bool(true)
      mem(tIdx).addr(0) := io.pe.req.bits.addr
      mem(tIdx).dinElement(0) := io.pe.req.bits.data.asUInt
      printfInfo("RegFile: PE write element tIdx/Addr/Data 0x%x/0x%x/0x%x\n",
        tIdx, io.pe.req.bits.addr, io.pe.req.bits.data)
      // Increment the write count and generate a response to the
      // control module if this puts us at the write count
      when (io.pe.req.bits.incWriteCount) {
        state(sIdx).countWrites := state(sIdx).countWrites + UInt(1)
        printfInfo("RegFile: write count loc/seen/expected 0x%x/0x%x/0x%x\n",
          sIdx, state(sIdx).countWrites + UInt(1), state(sIdx).totalWrites)
        when (state(sIdx).countWrites === state(sIdx).totalWrites - UInt(1)) {
          io.control.resp.valid := Bool(true)
          io.control.resp.bits.tIdx := tIdx
        }
      }
    } .otherwise {                  // This is a read
      mem(tIdx).we(0) := Bool(false)
      mem(tIdx).addr(0) := io.pe.req.bits.addr
      printfInfo("RegFile: PE read tIdx/Addr 0x%x/0x%x\n", tIdx,
        io.pe.req.bits.addr)
    }
  }

  // Requests from the Control module
  when (io.control.req.valid) {
    val tIdx = io.control.req.bits.tIdx
    val location = io.control.req.bits.location
    state(tIdx << UInt(1) | location).valid := Bool(true)
    state(tIdx << UInt(1) | location).totalWrites :=
      io.control.req.bits.totalWrites
    state(tIdx << UInt(1) | location).countWrites := UInt(0)
    printfInfo("RegFile: Control req tIdx/location/totalWrites 0x%x/0x%x/0x%x\n",
      tIdx, location, io.control.req.bits.totalWrites)
  }

  // Requests form the Transaction Table
  when (io.tTable.req.valid) {
    val tIdx = io.tTable.req.bits.tidIdx
    switch (io.tTable.req.bits.reqType) {
      is (e_TTABLE_REGFILE_WRITE) {
        printfInfo("RegFile: Saw TTable write idx/Addr/Data 0x%x/0x%x/0x%x\n",
          tIdx, io.tTable.req.bits.addr, io.tTable.req.bits.data)
        mem(tIdx).we(0) := Bool(true)
        mem(tIdx).dinElement(0) := io.tTable.req.bits.data
        mem(tIdx).addr(0) := io.tTable.req.bits.addr
      }
      is (e_TTABLE_REGFILE_READ) {
        printfInfo("RegFile: Saw TTable read Addr 0x%x\n",
          io.tTable.req.bits.addr)
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
    printfInfo("RegFile: PE resp PE/Data 0x%x/0x%x\n",
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
    io.tTable.resp.valid := Bool(true)
    io.tTable.resp.bits.data :=
      memDataVec(tTableRespAddr(log2Up(elementsPerBlock)-1,0))
    printfInfo("RegFile: Returning data to TTable 0x%x\n",
      io.tTable.resp.bits.data)
  }

  // Reset
  when (reset) {for (i <- 0 until transactionTableNumEntries * 2) {
    state(i).valid := Bool(false)}}

  // Assertions

  // The number of writes that we've seen should never be greater than
  // the number of expected writes.
  assert(!Vec((0 until transactionTableNumEntries * 2).map(
    i => (state(i).valid &&
      state(i).countWrites > state(i).totalWrites))).contains(Bool(true)),
    "The total writes to a Regsiter File entry exceeded the number expected")

  // A request to change the total number of writes should only happen
  // to a state entry marked as valid if it's countWrites is equal to
  // the totalWrites.
  assert(!(io.control.req.valid &&
    state(io.control.req.bits.tIdx << UInt(1) |
      io.control.req.bits.location).valid &&
    (state(io.control.req.bits.tIdx << UInt(1) |
      io.control.req.bits.location).countWrites =/=
      state(io.control.req.bits.tIdx << UInt(1) |
        io.control.req.bits.location).totalWrites)),
    "RegFile totalWrites being changed when valid && (countWrites != totalWrites)")

  // We shouldn't be trying to write data outside of the bounds of the
  // memory
  (0 until transactionTableNumEntries).map(i =>
    assert(!(mem(i).addr(0) >= UInt(regFileNumElements)),
      "RegFile address (read or write) is out of bounds"))

}

class RegisterFile(implicit p: Parameters) extends RegisterFileBase (
  Vec.fill(p(TransactionTableNumEntries))(Module(new SRAMElement(
    dataWidth = p(BitsPerBlock),
    sramDepth = pow(2, log2Up(p(RegFileNumBlocks))).toInt,
    numPorts = 1,
    elementWidth = p(ElementWidth))).io))(p)

class RegisterFileLearn(implicit p: Parameters) extends RegisterFileBase (
  Vec.fill(p(TransactionTableNumEntries))(Module(new SRAMElementIncrement(
    dataWidth = p(BitsPerBlock),
    sramDepth = pow(2, log2Up(p(RegFileNumBlocks))).toInt,
    numPorts = 1,
    elementWidth = p(ElementWidth))).io))(p) {
  override lazy val io = IO(new RegisterFileInterfaceLearn)

  val readReqType_d0 = Reg(next = io.pe.req.bits.reqType)
  io.pe.resp.bits.reqType := readReqType_d0

  for (transaction <- 0 until transactionTableNumEntries) {
    for (port <- 0 until mem(transaction).numPorts) {
      mem(transaction).wType(port) := UInt(0)
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
          mem(tIdx).wType(0) := UInt(0)
          mem(tIdx).dinElement(0) := io.pe.req.bits.data.asUInt
          printfInfo("RegFile: PE write element tIdx/Addr/Data 0x%x/0x%x/0x%x\n",
            tIdx, io.pe.req.bits.addr, io.pe.req.bits.data)
        }
        is (e_PE_WRITE_BLOCK_NEW) {
          mem(tIdx).wType(0) := UInt(1)
          mem(tIdx).din(0) := io.pe.req.bits.dataBlock
          printfInfo("RegFile: PE write block new tIdx/Addr/Data 0x%x/0x%x/0x%x\n",
            tIdx, io.pe.req.bits.addr, io.pe.req.bits.dataBlock)
        }
        is (e_PE_WRITE_BLOCK_ACC) {
          mem(tIdx).wType(0) := UInt(2)
          mem(tIdx).din(0) := io.pe.req.bits.dataBlock
          printfInfo("RegFile: PE write block inc tIdx/Addr/Data 0x%x/0x%x/0x%x\n",
            tIdx, io.pe.req.bits.addr, io.pe.req.bits.dataBlock)
        }
        // Kludge to kill the write _if_ we're just incrementing the
        // write count
        is (e_PE_INCREMENT_WRITE_COUNT) {
          printfInfo("RegFile: PE increment write count\n")
          mem(tIdx).we(0) := Bool(false)
        }
      }
    }
  }
}
