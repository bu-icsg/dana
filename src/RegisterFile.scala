package dana

import Chisel._

class RegisterFileInterface extends DanaBundle()() {
  val pe = new (PERegisterFileInterface).flip
}

class RegisterFile extends DanaModule()() {
  val io = new RegisterFileInterface

  // Instantiate an element-write SRAM with regFileNumBlocks reserved
  // for each Transaction Table entry
  val sram = Module( new SRAMElement(
    dataWidth = bitsPerBlock,
    sramDepth = regFileNumBlocks * transactionTableNumEntries,
    numPorts = 2,
    elementWidth = elementWidth))
}
