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
  val din   = Input(Vec(numReadWritePorts, UInt(dataWidth.W)))
  val dinW  = Input(Vec(numWritePorts,     UInt(dataWidth.W)))
  // Data Output
  val dout  = Output(Vec(numReadWritePorts, UInt(dataWidth.W)))
  val doutR = Output(Vec(numReadPorts,      UInt(dataWidth.W)))
  // Addresses
  val addr  = Input(Vec(numReadWritePorts, UInt(log2Up(sramDepth).W)))
  val addrR = Input(Vec(numReadPorts,      UInt(log2Up(sramDepth).W)))
  val addrW = Input(Vec(numWritePorts,     UInt(log2Up(sramDepth).W)))
  // Write enable
  val we    = Input(Vec(numReadWritePorts, Bool()))
  val weW   = Input(Vec(numWritePorts,     Bool()))
  // Read enable
  val re    = Input(Vec(numReadWritePorts, Bool()))
  val reR   = Input(Vec(numReadPorts, Bool()))
}

class SRAM (
  val id: Int                = 0,
  val dataWidth: Int         = 8,
  val sramDepth: Int         = 64,
  val numReadPorts: Int      = 0,
  val numWritePorts: Int     = 0,
  val numReadWritePorts: Int = 2,
  val initSwitch: Int        = -1,
  val elementsPerBlock: Int  = -1
) extends Module {
  val io = IO(new SRAMInterface(
    numReadPorts = numReadPorts,
    numWritePorts = numWritePorts,
    numReadWritePorts = numReadWritePorts,
    dataWidth = dataWidth,
    sramDepth = sramDepth))

  val mem = SeqMem(sramDepth, UInt(dataWidth.W))

  for (i <- 0 until numReadWritePorts) {
    when (io.we(i))                    { mem(io.addr(i))  := io.din(i)        }
    when (io.re(i))                    { io.dout(i)       := mem(io.addr(i))  }}

  for (i <- 0 until numReadPorts)      {
    when (io.reR(i))                   { io.doutR(i)      := mem(io.addrR(i)) }}

  for (i <- 0 until numWritePorts)     {
    when (io.weW(i))                   { mem(io.addrW(i)) := io.dinW(i)       }}
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
  val we = Output(Vec(2, Bool()))
  val din = Output(Vec(2, UInt(dataWidth.W)))
  val addr = Output(Vec(2, UInt(log2Up(sramDepth).W)))
  val dout = Input(Vec(2, UInt(dataWidth.W)))
}

class SRAMDualPort(
  val dataWidth: Int,
  val sramDepth: Int
) extends Module {
  val io = Flipped(new SRAMDualPortInterface(
    dataWidth = dataWidth,
    sramDepth = sramDepth))
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
