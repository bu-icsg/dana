// See LICENSE.BU for license details.
// See LICENSE.IBM for license details.

package dana

import chisel3._
import chisel3.util._

// [TODO] This module is currently non-working. It was an initial
// solution to the problem of dealing with random writes from the
// Processing Elements and needing to maintain a record of which
// entries were valid. Knowledge of valid entries is necessary to
// ensure that subsequent PE reads are only reading valid data. This
// approach introduces metadata into each SRAM block that contains the
// number of valid entries in that block.

class SRAMElementCounterResp (
  val sramDepth: Int
) extends Bundle {
  override def cloneType = new SRAMElementCounterResp (
    sramDepth = sramDepth).asInstanceOf[this.type]
  val index = UInt(log2Up(sramDepth).W)
}

class SRAMElementCounterInterface (
  val dataWidth: Int,
  val sramDepth: Int,
  val numPorts: Int,
  val elementWidth: Int
) extends Bundle {
  override def cloneType = new SRAMElementInterface(
    dataWidth = dataWidth,
    sramDepth = sramDepth,
    numPorts = numPorts,
    elementWidth = elementWidth).asInstanceOf[this.type]
  val we = Output(Vec(numPorts, Bool()))
  val din = Output(Vec(numPorts, UInt(elementWidth.W)))
  val addr = Output(Vec(numPorts, UInt(log2Up(sramDepth * dataWidth / elementWidth).W)))
  val dout = Input(Vec(numPorts, UInt(dataWidth.W)))
  // lastBlock sets which is the last block in the SRAM
  val lastBlock = Input(Vec(numPorts, UInt(log2Up(sramDepth).W)))
  // lastCount sets the number of elements in the last block
  val lastCount = Input(Vec(numPorts, UInt((log2Up(dataWidth / elementWidth) + 1).W)))
  val resp = Vec(numPorts, Decoupled(new SRAMElementCounterResp (
    sramDepth = sramDepth)) )
}


// write (i.e., SRAMElement), but also includes a count of the number
// of valid elements in each block. When a block is determined to be
// completely valid, this module generates an output update signal
// indicating which block is now valid.
class SRAMElementCounter (
  val dataWidth: Int = 32,
  val sramDepth: Int = 64,
  val elementWidth: Int = 8,
  val numPorts: Int = 1
) extends Module {
  val io = IO(Flipped(new SRAMElementCounterInterface(
    dataWidth = dataWidth,
    sramDepth = sramDepth,
    numPorts = numPorts,
    elementWidth = elementWidth
  )))
  val sram = Module(new SRAM(
    dataWidth = dataWidth + log2Up(dataWidth / elementWidth) + 1,
    sramDepth = sramDepth,
    numReadPorts = numPorts,
    numWritePorts = numPorts,
    numReadWritePorts = 0
  ))

  val addr = Vec(numPorts, new Bundle{
    val addrHi = UInt(log2Up(sramDepth).W)
    val addrLo = UInt(log2Up(dataWidth / elementWidth).W)})

  val writePending = Reg(Vec(numPorts, new WritePendingBundle(
    elementWidth = elementWidth,
    dataWidth = dataWidth,
    sramDepth = sramDepth)))

  val tmp = Vec(numPorts, Vec(dataWidth/elementWidth, UInt(elementWidth.W)))
  val count = Vec(numPorts, UInt((log2Up(dataWidth / elementWidth) + 1).W))
  val forwarding = Vec(numPorts, Bool())

  // Combinational Logic
  for (i <- 0 until numPorts) {
    // Assign the addresses
    addr(i).addrHi := io.addr(i)(
      log2Up(sramDepth * dataWidth / elementWidth) - 1,
      log2Up(dataWidth / elementWidth))
    addr(i).addrLo := io.addr(i)(
      log2Up(dataWidth / elementWidth) - 1, 0)
    // Connections to the sram
    sram.io.weW(i) := writePending(i).valid
    // Explicit data and count assignments
    sram.io.dinW(i) := 0.U
    sram.io.dinW(i)(sramDepth - 1, 0) := tmp(i)
    sram.io.dinW(i)(sramDepth + log2Up(dataWidth/elementWidth) + 1 - 1) :=
      count(i) + 1.U + forwarding(i)
    sram.io.addrR(i) := addr(i).addrHi
    io.dout(i) := sram.io.doutR(i)(dataWidth - 1, 0)
    // Defaults
    io.resp(i).valid := false.B
    io.resp(i).bits.index := 0.U
    forwarding(i) := false.B
    tmp(i) := sram.io.doutR(i)(dataWidth - 1, 0)
    count(i) := sram.io.doutR(i)(
      dataWidth + log2Up(dataWidth/elementWidth) + 1 - 1, dataWidth)
    sram.io.addrW(i) := writePending(i).addrHi
    when (writePending(i).valid) {
      for (j <- 0 until dataWidth / elementWidth) {
        when (j.U === writePending(i).addrLo) {
          tmp(i)(j) := writePending(i).data
        } .elsewhen(addr(i).addrHi === writePending(i).addrHi &&
            io.we(i) &&
            j.U === addr(i).addrLo) {
          tmp(i)(j) := io.din(i)
          forwarding(i) := true.B
        } .otherwise {
          tmp(i)(j) := sram.io.doutR(i)((j+1) * elementWidth - 1, j * elementWidth)
        }
      }
      // Generate a response if we've filled up an entry. An entry is
      // full if it's count is equal to the number of elementsPerBlock
      // or if it's the last count in the last block (this covers the
      // case of a partially filled last block).
      when (count(i) + 1.U + forwarding(i) === (dataWidth / elementWidth).U ||
        (count(i) + 1.U + forwarding(i) === io.lastCount(i) &&
          writePending(i).addrHi === io.lastBlock(i))) {
        io.resp(i).valid := true.B
        io.resp(i).bits.index := writePending(i).addrHi
        sram.io.dinW(i)(sramDepth + log2Up(dataWidth/elementWidth) + 1 - 1) := 0.U
      }
    }
  }

  // Sequential Logic
  for (i <- 0 until numPorts) {
    // Assign the pending write data
    writePending(i).valid := false.B
    when ((io.we(i)) && (forwarding(i) === false.B)) {
      writePending(i).valid := true.B
      writePending(i).data := io.din(i)
      writePending(i).addrHi := addr(i).addrHi
      writePending(i).addrLo := addr(i).addrLo
    }
  }

  // Assertions
  assert(isPow2(dataWidth / elementWidth),
    "dataWidth/elementWidth must be a power of 2")
}
