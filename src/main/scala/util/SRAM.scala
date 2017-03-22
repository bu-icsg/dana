// See LICENSE.BU for license details.
// See LICENSE.IBM for license details.

package dana

import chisel3._
import chisel3.util._

class SRAMInterface(
  val dataWidth: Int,
  val numReadPorts: Int,
  val numWritePorts: Int,
  val numReadWritePorts: Int,
  val sramDepth: Int
) extends Bundle {
  override def cloneType = new SRAMInterface(
    dataWidth = dataWidth,
    numReadPorts = numReadPorts,
    numWritePorts = numWritePorts,
    numReadWritePorts = numReadWritePorts,
    sramDepth = sramDepth
  ).asInstanceOf[this.type]
  // Data Input
  val din   = Vec(numReadWritePorts, UInt(dataWidth.W)).asInput
  val dinW  = Vec(numWritePorts,     UInt(dataWidth.W)).asInput
  // Data Output
  val dout  = Vec(numReadWritePorts, UInt(dataWidth.W)).asOutput
  val doutR = Vec(numReadPorts,      UInt(dataWidth.W)).asOutput
  // Addresses
  val addr  = Vec(numReadWritePorts, UInt(log2Up(sramDepth).W)).asInput
  val addrR = Vec(numReadPorts,      UInt(log2Up(sramDepth).W)).asInput
  val addrW = Vec(numWritePorts,     UInt(log2Up(sramDepth).W)).asInput
  // Write enable
  val we    = Vec(numReadWritePorts, Bool()).asInput
  val weW   = Vec(numWritePorts,     Bool()).asInput
  // Dump signal
  val dump  = Bool().asInput
}

class SRAM (
  val id: Int                = 0,
  val dataWidth: Int         = 8,
  val sramDepth: Int         = 64,
  val numReadPorts: Int      = 0,
  val numWritePorts: Int     = 0,
  val numReadWritePorts: Int = 2,
  val initSwitch: Int        = -1,
  val elementsPerBlock: Int  = -1,
  val enableDump: Boolean    = false
) extends Module {
  val io = IO(new SRAMInterface(
    numReadPorts = numReadPorts,
    numWritePorts = numWritePorts,
    numReadWritePorts = numReadWritePorts,
    dataWidth = dataWidth,
    sramDepth = sramDepth))

  val mem = Mem(sramDepth, UInt(dataWidth.W))

  if (numReadWritePorts > 0) {
    val buf = Reg(Vec(numReadWritePorts, UInt(dataWidth.W)))
    for (i <- 0 until numReadWritePorts) {
      when (io.we(i)) {
        mem(io.addr(i)) := io.din(i)
      }
      buf(i) := mem(io.addr(i))
      io.dout(i) := buf(i)
    }
  }

  if (numReadPorts > 0) {
    val bufR = Reg(Vec(numReadPorts, UInt(dataWidth.W)))
    for (i <- 0 until numReadPorts) {
      bufR(i) := mem(io.addrR(i))
      io.doutR(i) := bufR(i)
    }
  }

  if (numWritePorts > 0) {
    for (i <- 0 until numWritePorts) {
      when (io.weW(i)) {
        mem(io.addrW(i)) := io.dinW(i)
      }
    }
  }

  if (enableDump) { when(io.dump) { mem.zipWithIndex.map { case(m, i) =>
    printf("SRAM[0x%x]: 0x%x -> 0x%x\n", id.U, i.U, m) }} }
}

class SRAMSinglePortInterface(
  val dataWidth: Int,
  val sramDepth: Int
) extends Bundle {
  override def cloneType = new SRAMDualPortInterface(
    dataWidth = dataWidth,
    sramDepth = sramDepth).asInstanceOf[this.type]
  val we = Output(Bool())
  val din = Output(UInt(dataWidth.W))
  val addr = Output(UInt(log2Up(sramDepth).W))
  val dout = Input(UInt(dataWidth.W))
}

class SRAMDualPortInterface(
  val dataWidth: Int,
  val sramDepth: Int
) extends Bundle {
  override def cloneType = new SRAMDualPortInterface(
    dataWidth = dataWidth,
    sramDepth = sramDepth).asInstanceOf[this.type]
  val we = Vec(2, Bool()).asOutput
  val din = Vec(2, UInt(dataWidth.W)).asOutput
  val addr = Vec(2, UInt(log2Up(sramDepth).W)).asOutput
  val dout = Vec(2, UInt(dataWidth.W)).asInput
}

class SRAMDualPort(
  val dataWidth: Int,
  val sramDepth: Int
) extends Module {
  val io = new SRAMDualPortInterface(
    dataWidth = dataWidth,
    sramDepth = sramDepth).flip
  val sram = Module(new SRAM(
    dataWidth = dataWidth,
    numReadPorts = 0,
    numWritePorts = 0,
    numReadWritePorts = 2,
    initSwitch = -1,
    elementsPerBlock = -1,
    sramDepth = sramDepth)).io

  for (i <- 0 until 2) {
    sram.we(i) := io.we(i)
    sram.din(i) := io.din(i)
    sram.addr(i) := io.addr(i)
    io.dout(i) := sram.dout(i)
  }
}
