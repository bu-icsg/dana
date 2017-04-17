// See LICENSE.BU for license details.
// See LICENSE.IBM for license details.

package dana

import chisel3._
import chisel3.util._

// [TODO] Miscellaneous todos:
// * A read immediately following a write is going to result in screwy
//   behavior. It's reasonable that either this should be prohibited
//   via pushback on the asynchronous inteface or with an assertion.

class SRAMElementInterface (
  override val dataWidth: Int,
  override val sramDepth: Int,
  override val numPorts: Int,
  val elementWidth: Int
) extends SRAMVariantInterface(dataWidth, sramDepth, numPorts) {
  override def cloneType = new SRAMElementInterface(
    dataWidth = dataWidth,
    sramDepth = sramDepth,
    numPorts = numPorts,
    elementWidth = elementWidth).asInstanceOf[this.type]
  val dinElement = Input(Vec(numPorts, UInt(elementWidth.W)))
  override val addr = Input(Vec(numPorts,
    UInt((log2Up(sramDepth) + log2Up(dataWidth / elementWidth)).W)))
}

class WritePendingBundle (
  val elementWidth: Int,
  val dataWidth: Int,
  val sramDepth: Int
) extends Bundle {
  override def cloneType = new WritePendingBundle (
    elementWidth = elementWidth,
    dataWidth = dataWidth,
    sramDepth = sramDepth).asInstanceOf[this.type]
  val valid = Bool()
  val data = UInt(elementWidth.W)
  val addrHi = UInt(log2Up(sramDepth).W)
  val addrLo = UInt(log2Up(dataWidth / elementWidth).W)
}

// A special instance of the generic SRAM that allows for masked
// writes to the SRAM. Reads happen normally, but writes happen using
// a 2-cyle read-modify-write operation. Due to the nature of this
// operation, each write port needs an associated read port.
// Consequently, this only has RW ports.
class SRAMElement (
  override val dataWidth: Int = 32,
  override val sramDepth: Int = 64,
  override val numPorts: Int = 1,
  val elementWidth: Int = 8
) extends SRAMVariant(dataWidth, sramDepth, numPorts) {
  override lazy val io = IO(new SRAMElementInterface(
    dataWidth = dataWidth,
    sramDepth = sramDepth,
    numPorts = numPorts,
    elementWidth = elementWidth
  ))

  val elementsPerBlock = divUp(dataWidth, elementWidth)

  def index(j: Int): (Int, Int) = (elementWidth*(j+1) - 1, elementWidth * j)

  val addr = Vec(numPorts, new Bundle {
    val addrHi = Wire(UInt(log2Up(sramDepth).W))
    val addrLo = Wire(UInt(log2Up(elementsPerBlock).W))})

  val writePending = Reg(Vec(numPorts, new WritePendingBundle(
    elementWidth = elementWidth,
    dataWidth = dataWidth,
    sramDepth = sramDepth)))

  val tmp = Wire(Vec(numPorts, Vec(elementsPerBlock, UInt(elementWidth.W) )))
  val forwarding = Wire(Vec(numPorts, Bool()))

  // Combinational Logic
  for (i <- 0 until numPorts) {
    // Assign the addresses
    addr(i).addrHi := io.addr(i).asUInt()(
      log2Up(sramDepth * elementsPerBlock) - 1, log2Up(elementsPerBlock))
    addr(i).addrLo := io.addr(i)(log2Up(elementsPerBlock) - 1, 0)

    // Connections to the sram
    sram.io.weW(i) := writePending(i).valid
    sram.io.dinW(i) := tmp(i)
    sram.io.addrW(i) := writePending(i).addrHi
    sram.io.addrR(i) := addr(i).addrHi
    io.dout(i) := sram.io.doutR(i)

    // Defaults
    val doutRTupled = (((x: Int, y: Int) => sram.io.doutR(i)(x, y)) tupled)
    (0 until elementsPerBlock).map(j => tmp(i)(j) := doutRTupled(index(j)))
    forwarding(i) := addr(i).addrHi === writePending(i).addrHi && io.we(i) &&
      writePending(i).valid

    when (writePending(i).valid) {
      // Write the element
      tmp(i)(writePending(i).addrLo) := writePending(i).data
      // Write the forwarded element if needed
      when (forwarding(i)) {
        tmp(i)(writePending(i).addrLo) := io.dinElement(i) }}}

  // Sequential Logic
  for (i <- 0 until numPorts) {
    // Assign the pending write data
    writePending(i).valid := false.B
    when ((io.we(i)) && (forwarding(i) === false.B)) {
      writePending(i).valid := true.B
      writePending(i).data := io.dinElement(i)
      writePending(i).addrHi := addr(i).addrHi
      writePending(i).addrLo := addr(i).addrLo }}
}
