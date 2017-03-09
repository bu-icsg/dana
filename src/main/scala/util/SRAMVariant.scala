// See LICENSE.BU for license details.

package dana

import chisel3._
import chisel3.util._

class SRAMVariantInterface(
  val dataWidth: Int,
  val sramDepth: Int,
  val numPorts: Int
) extends Bundle {
  override def cloneType = new SRAMVariantInterface(
    dataWidth = dataWidth,
    sramDepth = sramDepth,
    numPorts = numPorts).asInstanceOf[this.type]
  val we   = Vec(numPorts, Bool() ).asInput
  val din  = Vec(numPorts, UInt(dataWidth.W)).asInput
  val addr = Vec(numPorts, UInt(log2Up(sramDepth).W)).asInput
  val dout = Vec(numPorts, UInt(dataWidth.W)).asOutput
}

class SRAMVariant(
  val dataWidth: Int = 32,
  val sramDepth: Int = 64,
  val numPorts: Int = 1
) extends Module {

  def writeElement(a: Vec[UInt], index: UInt, b: UInt) { a(index) := b }

  lazy val io = IO(new SRAMVariantInterface(
    dataWidth = dataWidth,
    sramDepth = sramDepth,
    numPorts = numPorts))

  val sram = Module(new SRAM(
    dataWidth = dataWidth,
    sramDepth = sramDepth,
    numReadPorts = numPorts,
    numWritePorts = numPorts,
    numReadWritePorts = 0))

  // Set the name of the verilog backend
  // [TODO] issue-37, these aren't supported in Chisel 3
  // if (numPorts == 1)
  //   sram.setModuleName("sram_r" + numPorts + "_w" + numPorts + "_rw" + 0);
  // else
  //   sram.setModuleName("UNDEFINED_SRAM_BACKEND_FOR_NUM_PORTS_" + numPorts);

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
