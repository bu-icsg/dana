package dana

import Chisel._

// SRAMElement variant that allows for element _and_ block writes with
// the option of writing a block that is accumualted with the elements
// of the existing block. Forwarding is allowable for all cases.

// This uses both write enable (we) and write type (wType) input
// lines. The write type is defined as follows:
//   0: element write (like SRAMElement0
//   1: block write overwriting old block
//   2: block write accumulating element-wise with old block

class SRAMElementIncrementInterface (
  val dataWidth: Int,
  val sramDepth: Int,
  val numPorts: Int,
  val elementWidth: Int
) extends Bundle {
  override def clone = new SRAMElementIncrementInterface(
    dataWidth = dataWidth,
    sramDepth = sramDepth,
    numPorts = numPorts,
    elementWidth = elementWidth).asInstanceOf[this.type]
  val we = Vec.fill(numPorts){ Bool(OUTPUT) }
  val wType = Vec.fill(numPorts){ UInt(OUTPUT, width = log2Up(3)) }
  val dinElement = Vec.fill(numPorts){ UInt(OUTPUT, width = elementWidth)}
  val dinBlock = Vec.fill(numPorts){ UInt(OUTPUT, width = dataWidth)}
  val addr = Vec.fill(numPorts){ UInt(OUTPUT,
    width = log2Up(sramDepth) + log2Up(dataWidth / elementWidth))}
  val dout = Vec.fill(numPorts){ UInt(INPUT, width = dataWidth)}
}

class WritePendingIncrementBundle (
  val elementWidth: Int,
  val dataWidth: Int,
  val sramDepth: Int
) extends Bundle {
  override def clone = new WritePendingIncrementBundle (
    elementWidth = elementWidth,
    dataWidth = dataWidth,
    sramDepth = sramDepth).asInstanceOf[this.type]
  val valid = Bool()
  val wType = UInt(width = log2Up(3))
  val dataElement = UInt(width = elementWidth)
  val dataBlock = UInt(width = dataWidth)
  val addrHi = UInt(width = log2Up(sramDepth))
  val addrLo = UInt(width = log2Up(dataWidth / elementWidth))
}

// A special instance of the generic SRAM that allows for masked
// writes to the SRAM. Reads happen normally, but writes happen using
// a 2-cyle read-modify-write operation. Due to the nature of this
// operation, each write port needs an associated read port.
// Consequently, this only has RW ports.
class SRAMElementIncrement (
  val dataWidth: Int = 32,
  val sramDepth: Int = 64,
  val elementWidth: Int = 8,
  val numPorts: Int = 1
) extends Module {
  val io = new SRAMElementIncrementInterface(
    dataWidth = dataWidth,
    sramDepth = sramDepth,
    numPorts = numPorts,
    elementWidth = elementWidth
  ).flip
  val sram = Module(new SRAM(
    dataWidth = dataWidth,
    sramDepth = sramDepth,
    numReadPorts = numPorts,
    numWritePorts = numPorts,
    numReadWritePorts = 0
  ))

  // Set the name of the verilog backend
  if (numPorts == 1)
    sram.setName("sram_r" + numPorts + "_w" + numPorts + "_rw" + 0);
  else
    sram.setName("UNDEFINED_SRAM_BACKEND_FOR_NUM_PORTS_" + numPorts);

  def divUp (dividend: Int, divisor: Int): Int = {
    (dividend + divisor - 1) / divisor}
  val elementsPerBlock = divUp(dataWidth, elementWidth)

  val addr = Vec.fill(numPorts){ new Bundle{
    val addrHi = UInt(width = log2Up(sramDepth))
    val addrLo = UInt(width = log2Up(elementsPerBlock))}}

  val writePending = Vec.fill(numPorts){Reg(new WritePendingIncrementBundle(
    elementWidth = elementWidth,
    dataWidth = dataWidth,
    sramDepth = sramDepth))}

  val tmp = Vec.fill(numPorts){
    Vec.fill(elementsPerBlock){ UInt(width = elementWidth) }}
  val forwarding = Vec.fill(numPorts){ Bool() }

  // Combinational Logic
  for (i <- 0 until numPorts) {
    // Assign the addresses
    addr(i).addrHi := io.addr(i).toBits()(
      log2Up(sramDepth * elementsPerBlock) - 1,
      log2Up(elementsPerBlock))
    addr(i).addrLo := io.addr(i).toBits()(
      log2Up(elementsPerBlock) - 1, 0)
    // Connections to the sram
    sram.io.weW(i) := writePending(i).valid
    sram.io.dinW(i) := tmp(i).toBits()
    sram.io.addrR(i) := addr(i).addrHi
    io.dout(i) := sram.io.doutR(i)
    // Defaults
    forwarding(i) := Bool(false)
    (0 until elementsPerBlock).map(j =>
      tmp(i)(j) := sram.io.doutR(i)(elementWidth*(j+1)-1,elementWidth*j))
    sram.io.addrW(i) := writePending(i).addrHi
    when (writePending(i).valid) {
      switch (writePending(i).wType) {
        // Element Write
        is (UInt(0)) {
          for (j <- 0 until elementsPerBlock) {
            when (UInt(j) === writePending(i).addrLo) {
              tmp(i)(j) := writePending(i).dataElement
            } .elsewhen(addr(i).addrHi === writePending(i).addrHi &&
              io.we(i) &&
              io.wType(i) === UInt(0) &&
              UInt(j) === addr(i).addrLo) {
              tmp(i)(j) := io.dinElement(i)
              forwarding(i) := Bool(true)
            } .otherwise {
              tmp(i)(j) := sram.io.doutR(i).toBits()((j+1) * elementWidth - 1, j * elementWidth)
            }
          }
        }
        // Block Write
        is (UInt(1)) {
          when (addr(i).addrHi === writePending(i).addrHi &&
            io.we(i) && io.wType(i) === UInt(1)) {
            (0 until elementsPerBlock).map(j =>
              tmp(i)(j) := io.dinBlock(i)(elementWidth*(j+1) - 1,
                elementWidth * j))
            forwarding(i) := Bool(true)
          } .otherwise {
            (0 until elementsPerBlock).map(j =>
              tmp(i)(j) := writePending(i).dataBlock(elementWidth*(j+1) - 1,
                elementWidth * j))
          }
        }
        // Block Write with Element-wise Increment
        is (UInt(2)) {
          when (addr(i).addrHi === writePending(i).addrHi &&
            io.we(i) && io.wType(i) === UInt(1)) {
            (0 until elementsPerBlock).map(j =>
              tmp(i)(j) := io.dinBlock(i)(elementWidth*(j+1) - 1,
                elementWidth * j) +
                writePending(i).dataBlock(elementWidth*(j+1) - 1,
                  elementWidth * j) +
                sram.io.doutR(i).toBits()((j+1) * elementWidth - 1,
                  j * elementWidth))
            forwarding(i) := Bool(true)
          } .otherwise {
            (0 until elementsPerBlock).map(j =>
              tmp(i)(j) := io.dinBlock(i)(elementWidth*(j+1) - 1,
                elementWidth * j) +
                writePending(i).dataBlock(elementWidth*(j+1) - 1,
                elementWidth * j))
          }
        }
      }
    }
  }

  // Sequential Logic
  for (i <- 0 until numPorts) {
    // Assign the pending write data
    writePending(i).valid := Bool(false)
    when ((io.we(i)) && (forwarding(i) === Bool(false))) {
      writePending(i).valid := Bool(true)
      writePending(i).wType := io.wType(i)
      writePending(i).dataElement := io.dinElement(i)
      writePending(i).dataBlock := io.dinBlock(i)
      writePending(i).addrHi := addr(i).addrHi
      writePending(i).addrLo := addr(i).addrLo
    }
  }
}
