package dana

import Chisel._

class RegisterFileInterface extends DanaBundle()() {
  val pe = new (PERegisterFileInterface).flip
}

class RegisterFile extends DanaModule()() {
  val io = new RegisterFileInterface

  // Instantiate an element-write SRAM with regFileNumBlocks reserved
  // for each Transaction Table entry
  val mem = Module( new SRAMElement(
    dataWidth = bitsPerBlock,
    sramDepth = regFileNumBlocks * transactionTableNumEntries * 2,
    numPorts = 1,
    elementWidth = elementWidth))

  // Default values for the memory
  for (i <- 0 until mem.numPorts) {
    mem.io.we(i) := Bool(false)
    mem.io.din(i) := UInt(0)
    mem.io.addr(i) := UInt(0)
  }

  when (io.pe.req.valid) {
    // Take action based on whether this is a write or a read
    when (io.pe.req.bits.isWrite) { // This is a Write
      mem.io.we(0) := Bool(true)
      mem.io.din(0) := io.pe.req.bits.data
      mem.io.addr(0) := io.pe.req.bits.regIndex +
        io.pe.req.bits.tIdx * UInt(regFileNumBlocks * 2) +
        io.pe.req.bits.location * UInt(regFileNumBlocks)
    } .otherwise {                  // This is a read
      mem.io.we(0) := Bool(false)
      mem.io.addr(0) := io.pe.req.bits.regIndex +
        io.pe.req.bits.tIdx * UInt(regFileNumBlocks * 2) +
        io.pe.req.bits.location * UInt(regFileNumBlocks)
    }
  }

  val readReqValid_d0 = Reg(next = io.pe.req.valid && !io.pe.req.bits.isWrite)
  val peIndex_d0 = Reg(next = io.pe.req.bits.peIndex)

  io.pe.resp.valid := readReqValid_d0
  io.pe.resp.bits.peIndex := peIndex_d0
  io.pe.resp.bits.data := mem.io.dout(0)

}
