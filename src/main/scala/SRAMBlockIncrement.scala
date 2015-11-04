package dana

import Chisel._

// SRAMBlock variant that allows for element _and_ block writes with
// the option of writing a block that is accumualted with the elements
// of the existing block. Forwarding is allowable for all cases.

// This uses both write enable (we) and write type (wType) input
// lines. The write type is defined as follows:
//   0: element write (like SRAMBlock0
//   1: block write overwriting old block
//   2: block write accumulating element-wise with old block

class SRAMBlockIncrementInterface (
  override val dataWidth: Int,
  override val sramDepth: Int,
  override val numPorts: Int,
  val elementWidth: Int
) extends SRAMVariantInterface(dataWidth, sramDepth, numPorts) {
  override def cloneType = new SRAMBlockIncrementInterface(
    dataWidth = dataWidth,
    sramDepth = sramDepth,
    numPorts = numPorts,
    elementWidth = elementWidth).asInstanceOf[this.type]
  val inc = Vec.fill(numPorts){ Bool(OUTPUT)}
}

class WritePendingBlockIncrementBundle (
  val elementWidth: Int,
  val dataWidth: Int,
  val sramDepth: Int
) extends Bundle {
  override def cloneType = new WritePendingBlockIncrementBundle (
    elementWidth = elementWidth,
    dataWidth = dataWidth,
    sramDepth = sramDepth).asInstanceOf[this.type]
  val valid = Bool()
  val inc = Bool()
  val data = UInt(width = dataWidth)
  val addr = UInt(width = log2Up(sramDepth))
}

// A special instance of the generic SRAM that allows for masked
// writes to the SRAM. Reads happen normally, but writes happen using
// a 2-cyle read-modify-write operation. Due to the nature of this
// operation, each write port needs an associated read port.
// Consequently, this only has RW ports.
class SRAMBlockIncrement (
  override val dataWidth: Int = 32,
  override val sramDepth: Int = 64,
  override val numPorts: Int = 1,
  val elementWidth: Int = 8
) extends SRAMVariant {
  override lazy val io = new SRAMBlockIncrementInterface(
    dataWidth = dataWidth,
    sramDepth = sramDepth,
    numPorts = numPorts,
    elementWidth = elementWidth).flip
  val elementsPerBlock = divUp(dataWidth, elementWidth)

  val writePending = Vec.fill(numPorts){Reg(new WritePendingBlockIncrementBundle(
    elementWidth = elementWidth,
    dataWidth = dataWidth,
    sramDepth = sramDepth))}

  val tmp = Wire(Vec.fill(numPorts){
    Vec.fill(elementsPerBlock){ UInt(width = elementWidth) }})
  val forwarding = Wire(Vec.fill(numPorts){ Bool() })

  // Combinational Logic
  for (i <- 0 until numPorts) {
    // Connections to the sram
    sram.io.weW(i) := writePending(i).valid
    sram.io.dinW(i) := tmp(i).toBits()
    sram.io.addrR(i) := io.addr(i)
    io.dout(i) := sram.io.doutR(i)
    // Defaults
    forwarding(i) := Bool(false)
    (0 until elementsPerBlock).map(j =>
      tmp(i)(j) := sram.io.doutR(i)(elementWidth*(j+1)-1,elementWidth*j))
    sram.io.addrW(i) := writePending(i).addr
    when (writePending(i).valid) {
      switch (writePending(i).inc) {
        // Block Write
        is (Bool(false)) {
          when (io.addr(i) === writePending(i).addr &&
            io.we(i) && io.inc(i) === Bool(false)) {
            (0 until elementsPerBlock).map(j =>
              tmp(i)(j) := io.din(i)(elementWidth*(j+1) - 1, elementWidth * j))
            forwarding(i) := Bool(true)
          } .otherwise {
            (0 until elementsPerBlock).map(j =>
              tmp(i)(j) := writePending(i).data(elementWidth*(j+1) - 1,
                elementWidth * j))
          }
        }
        // Block Write with Element-wise Increment
        is (Bool(true)) {
          when (io.addr(i) === writePending(i).addr &&
            io.we(i) && io.inc(i) === Bool(true)) {
            (0 until elementsPerBlock).map(j =>
              tmp(i)(j) := io.din(i)(elementWidth*(j+1) - 1,
                elementWidth * j) +
                writePending(i).data(elementWidth*(j+1) - 1,
                  elementWidth * j) +
                sram.io.doutR(i).toBits()((j+1) * elementWidth - 1,
                  j * elementWidth))
            forwarding(i) := Bool(true)
          } .otherwise {
            (0 until elementsPerBlock).map(j =>
              tmp(i)(j) := sram.io.doutR(i).toBits()((j+1) * elementWidth - 1,
                j * elementWidth) +
              writePending(i).data(elementWidth*(j+1) - 1,
                elementWidth * j))
          }

        }
      }
      printf("[INFO] SRAMBlockIncrement: PE write block Addr/Data_acc/Data_new/Data_old 0x%x/0x%x/0x%x/0x%x\n",
        writePending(i).addr##UInt(0, width=log2Up(elementsPerBlock)),
        tmp(i).toBits, writePending(i).data, sram.io.doutR(i).toBits)
    }
  }

  // Sequential Logic
  for (i <- 0 until numPorts) {
    // Assign the pending write data
    writePending(i).valid := Bool(false)
    when ((io.we(i)) && (forwarding(i) === Bool(false))) {
      writePending(i).valid := Bool(true)
      writePending(i).inc := io.inc(i)
      writePending(i).data := io.din(i)
      writePending(i).addr := io.addr(i)
    }
  }
}
