// See LICENSE for license details.

package dana

import chisel3._
import chisel3.util._

// SRAMElement variant that allows for element _and_ block writes with
// the option of writing a block that is accumualted with the elements
// of the existing block. Forwarding is allowable for all cases.

// This uses both write enable (we) and write type (wType) input
// lines. The write type is defined as follows:
//   0: element write (like SRAMElement0
//   1: block write overwriting old block
//   2: block write accumulating element-wise with old block

class SRAMElementIncrementInterface (
  override val dataWidth: Int,
  override val sramDepth: Int,
  override val numPorts: Int,
  override val elementWidth: Int
) extends SRAMElementInterface(dataWidth, sramDepth, numPorts, elementWidth) {
  override def cloneType = new SRAMElementIncrementInterface(
    dataWidth = dataWidth,
    sramDepth = sramDepth,
    numPorts = numPorts,
    elementWidth = elementWidth).asInstanceOf[this.type]
  val wType = Vec(numPorts, UInt(INPUT, width = log2Up(3)))
}

class WritePendingIncrementBundle (
  val elementWidth: Int,
  val dataWidth: Int,
  val sramDepth: Int
) extends Bundle {
  override def cloneType = new WritePendingIncrementBundle (
    elementWidth = elementWidth,
    dataWidth = dataWidth,
    sramDepth = sramDepth).asInstanceOf[this.type]
  val valid       = Bool()
  val wType       = UInt(width = log2Up(3))
  val dataElement = UInt(width = elementWidth)
  val dataBlock   = UInt(width = dataWidth)
  val addrHi      = UInt(width = log2Up(sramDepth))
  val addrLo      = UInt(width = log2Up(dataWidth / elementWidth))
}

// A special instance of the generic SRAM that allows for masked
// writes to the SRAM. Reads happen normally, but writes happen using
// a 2-cyle read-modify-write operation. Due to the nature of this
// operation, each write port needs an associated read port.
// Consequently, this only has RW ports.
class SRAMElementIncrement (
  override val dataWidth: Int = 32,
  override val sramDepth: Int = 64,
  override val numPorts: Int = 1,
  val elementWidth: Int = 8
) extends SRAMVariant {
  override lazy val io = IO(new SRAMElementIncrementInterface(
    dataWidth = dataWidth,
    sramDepth = sramDepth,
    numPorts = numPorts,
    elementWidth = elementWidth
  ))

  val elementsPerBlock = divUp(dataWidth, elementWidth)

  def index(j: Int): (Int, Int) = (elementWidth*(j+1) - 1, elementWidth * j)
  def writeBlock(a: Vec[UInt], b: UInt) {
    val bTupled = (((x: Int, y: Int) => b.apply(x, y)) tupled)
    (0 until elementsPerBlock).map(j => a(j) := bTupled(index(j))) }
  def writeBlockIncrement(a: Vec[UInt], b: UInt, c: UInt) {
    val bTupled = (((x: Int, y: Int) => b.apply(x, y)) tupled)
    val cTupled = (((x: Int, y: Int) => c.apply(x, y)) tupled)
    (0 until elementsPerBlock).map(j => a(j) :=
      bTupled(index(j)) + cTupled(index(j))) }

  class AddrBundle(
    val sramDepth: Int,
    val elementsPerBlock: Int
  ) extends Bundle {
    val addrHi = UInt(width = log2Up(sramDepth))
    val addrLo = UInt(width = log2Up(elementsPerBlock))
    override def cloneType = new AddrBundle(
      sramDepth = sramDepth,
      elementsPerBlock = elementsPerBlock).asInstanceOf[this.type]
  }

  val addr = Wire(Vec(numPorts, new AddrBundle(sramDepth,elementsPerBlock)))

  val writePending = Reg(Vec(numPorts, new WritePendingIncrementBundle(
    elementWidth = elementWidth,
    dataWidth = dataWidth,
    sramDepth = sramDepth)))

  val tmp0 = Wire(Vec(numPorts,Vec(elementsPerBlock,UInt(width=elementWidth))))
  val tmp1 = Wire(Vec(numPorts,Vec(elementsPerBlock,UInt(width=elementWidth))))
  val forwarding = Wire(Vec(numPorts, Bool()))

  // Combinational Logic
  for (i <- 0 until numPorts) {
    // Assign the addresses: addrHi->block address, addrLo->element address
    addr(i).addrHi := io.addr(i).asUInt()(
      log2Up(sramDepth * elementsPerBlock) - 1, log2Up(elementsPerBlock))
    addr(i).addrLo := io.addr(i).asUInt()(log2Up(elementsPerBlock) - 1, 0)

    // Connections to the sram
    sram.io.weW(i) := writePending(i).valid
    sram.io.dinW(i) := tmp1(i).asUInt
    sram.io.addrW(i) := writePending(i).addrHi
    sram.io.addrR(i) := addr(i).addrHi
    io.dout(i) := sram.io.doutR(i)

    // Defaults
    val doutRTupled = (((x: Int, y: Int) => sram.io.doutR(i)(x, y)) tupled)
    (0 until elementsPerBlock).map(j => tmp0(i)(j) := doutRTupled(index(j)))
    tmp1(i) := tmp0(i)
    forwarding(i) := addr(i).addrHi === writePending(i).addrHi && io.we(i) &&
      writePending(i).valid

    // Deal with a pending write if one exists
    when (writePending(i).valid) {
      // Write pending data to tmp0
      switch (writePending(i).wType) {
        is (UInt(0)) {
          writeElement(tmp0(i), writePending(i).addrLo, writePending(i).dataElement) }
        is (UInt(1)) {
          writeBlock(tmp0(i), writePending(i).dataBlock) }
        is (UInt(2)) {
          writeBlockIncrement(tmp0(i), sram.io.doutR(i), writePending(i).dataBlock) }}

      // Handle forwarding by updating tmp1
      when (forwarding(i)) {
        switch (io.wType(i)) {
          is (UInt(0)) {
            writeElement(tmp1(i), addr(i).addrLo, io.dinElement(i)) }
          is (UInt(1)) {
            writeBlock(tmp1(i), io.din(i)) }
          is (UInt(2)) {
            writeBlockIncrement(tmp1(i), tmp0(i).asUInt, io.din(i)) }}}

      printf("[INFO] SramEleInc: WRITE port/type/fwd?/fwdType 0x%x/0x%x/0x%x/0x%x\n",
        UInt(i), writePending(i).wType, forwarding(i), io.wType(i))
      printf("[INFO]              DATA addr/dataOld/dataNew 0x%x/0x%x/0x%x\n",
        writePending(i).addrHi##writePending(i).addrLo, sram.io.doutR(i).asUInt,
        tmp1(i).asUInt)
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
      writePending(i).dataBlock := io.din(i)
      writePending(i).addrHi := addr(i).addrHi
      writePending(i).addrLo := addr(i).addrLo
    }
  }

  // Assertions

  // We only define write types up through 2
  assert(!Vec((0 until numPorts).map(i =>
    io.we(i) && io.wType(i) > UInt(2))).contains(Bool(true)),
    "SRAMElementIncrement saw unsupported wType > 2")
}
