package dana

import Chisel._

class RegisterFileInterface extends DanaBundle {
  val pe = new (PERegisterFileInterface).flip
  val control = new (ControlRegisterFileInterface).flip
}

class RegisterFileState extends DanaBundle {
  val totalWrites = UInt(width = 16) // [TODO] fragile
  val countWrites = UInt(width = 16) // [TODO] fragile
}

class RegisterFile extends DanaModule {
  val io = new RegisterFileInterface

  // Instantiate an element-write SRAM with regFileNumBlocks reserved
  // for each Transaction Table entry
  val mem = Module( new SRAMElement(
    dataWidth = bitsPerBlock,
    sramDepth = regFileNumBlocks * transactionTableNumEntries * 2,
    numPorts = 1,
    elementWidth = elementWidth))
  val state = Vec.fill(transactionTableNumEntries * 2){Reg(new RegisterFileState)}

  // Default values for the memory
  for (i <- 0 until mem.numPorts) {
    mem.io.we(i) := Bool(false)
    mem.io.din(i) := UInt(0)
    mem.io.addr(i) := UInt(0)
  }
  // Default Control interface values
  io.control.req.ready := Bool(true)
  io.control.resp.valid := Bool(false)
  io.control.resp.bits.tIdx := UInt(0)

  // Requests from the Processing Element Table
  when (io.pe.req.valid) {
    // Take action based on whether this is a write or a read
    when (io.pe.req.bits.isWrite) { // This is a Write
      mem.io.we(0) := Bool(true)
      mem.io.din(0) := io.pe.req.bits.data
      mem.io.addr(0) := io.pe.req.bits.regIndex +
        io.pe.req.bits.tIdx * UInt(regFileNumBlocks * 2) +
        io.pe.req.bits.location * UInt(regFileNumBlocks)
      // Increment the write count and generate a response to the
      // control module if this puts us at the write count
      state(io.pe.req.bits.tIdx ## io.pe.req.bits.location).countWrites :=
        state(io.pe.req.bits.tIdx ## io.pe.req.bits.location).countWrites + UInt(1)
      when (state(io.pe.req.bits.tIdx ## io.pe.req.bits.location).countWrites ===
        state(io.pe.req.bits.tIdx ## io.pe.req.bits.location).totalWrites - UInt(1)) {
        io.control.resp.valid := Bool(true)
        io.control.resp.bits.tIdx := io.pe.req.bits.tIdx
      }
    } .otherwise {                  // This is a read
      mem.io.we(0) := Bool(false)
      mem.io.addr(0) := io.pe.req.bits.regIndex +
        io.pe.req.bits.tIdx * UInt(regFileNumBlocks * 2) +
        io.pe.req.bits.location * UInt(regFileNumBlocks)
    }
  }

  // Requests from the Control module
  when (io.control.req.valid) {
    state(io.control.req.bits.tIdx << UInt(1) |
      io.control.req.bits.location).totalWrites :=
      io.control.req.bits.totalWrites
    state(io.control.req.bits.tIdx << UInt(1) |
      io.control.req.bits.location).countWrites := UInt(0)
  }

  val readReqValid_d0 = Reg(next = io.pe.req.valid && !io.pe.req.bits.isWrite)
  val peIndex_d0 = Reg(next = io.pe.req.bits.peIndex)

  io.pe.resp.valid := readReqValid_d0
  io.pe.resp.bits.peIndex := peIndex_d0
  io.pe.resp.bits.data := mem.io.dout(0)

  // Assertions

  // The number of writes that we've seen should never be greater than
  // the number of expected writes.
  assert(!Vec((0 until transactionTableNumEntries * 2).map(
    i => (state(i).countWrites > state(i).totalWrites))).contains(Bool(true)),
    "The total writes to a Regsiter File entry exceeded the number expected")
}
