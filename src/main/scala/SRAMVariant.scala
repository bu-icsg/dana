package dana

import Chisel._

class SRAMVariantInterface(
  val dataWidth: Int,
  val sramDepth: Int,
  val numPorts: Int
) extends Bundle {
  override def cloneType = new SRAMVariantInterface(
    dataWidth = dataWidth,
    sramDepth = sramDepth,
    numPorts = numPorts).asInstanceOf[this.type]
  val we = Vec.fill(numPorts){ Bool(OUTPUT) }
  val din = Vec.fill(numPorts){ UInt(OUTPUT, width = dataWidth)}
  val addr = Vec.fill(numPorts){ UInt(OUTPUT, width = log2Up(sramDepth))}
  val dout = Vec.fill(numPorts){ UInt(INPUT, width = dataWidth)}
}

class SRAMVariant(
  val dataWidth: Int = 32,
  val sramDepth: Int = 64,
  val numPorts: Int = 1
) extends Module {

  lazy val io = new SRAMVariantInterface(
    dataWidth = dataWidth,
    sramDepth = sramDepth,
    numPorts = numPorts).flip

  val sram = Module(new SRAM(
    dataWidth = dataWidth,
    sramDepth = sramDepth,
    numReadPorts = numPorts,
    numWritePorts = numPorts,
    numReadWritePorts = 0))

  // Set the name of the verilog backend
  if (numPorts == 1)
    sram.setName("sram_r" + numPorts + "_w" + numPorts + "_rw" + 0);
  else
    sram.setName("UNDEFINED_SRAM_BACKEND_FOR_NUM_PORTS_" + numPorts);

  def divUp (dividend: Int, divisor: Int): Int = {
    (dividend + divisor - 1) / divisor}

  // Basic block read and block write
  for (i <- 0 until numPorts) {
    sram.io.weW(i) := io.we(i)
    sram.io.dinW(i) := io.din(i)
    sram.io.addrR(i) := io.addr(i)
    sram.io.addrW(i) := io.addr(i)
    io.dout(i) := sram.io.doutR(i)
  }
}
