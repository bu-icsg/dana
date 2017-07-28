// See LICENSE.BU for license details.
// See LICENSE.IBM for license details.

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
  val wType = Input(Vec(numPorts, UInt(log2Up(3).W)))
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
  val wType       = UInt(log2Up(3).W)
  val dataElement = UInt(elementWidth.W)
  val dataBlock   = UInt(dataWidth.W)
  val addrHi      = UInt(log2Up(sramDepth).W)
  val addrLo      = UInt(log2Up(dataWidth / elementWidth).W)
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
    val addrHi = UInt(log2Up(sramDepth).W)
    val addrLo = UInt(log2Up(elementsPerBlock).W)
    override def cloneType = new AddrBundle(
      sramDepth = sramDepth,
      elementsPerBlock = elementsPerBlock).asInstanceOf[this.type]
  }

  val addr = Wire(Vec(numPorts, new AddrBundle(sramDepth,elementsPerBlock)))

  val writePending = Reg(Vec(numPorts, new WritePendingIncrementBundle(
    elementWidth = elementWidth,
    dataWidth = dataWidth,
    sramDepth = sramDepth)))

  val tmp0 = Wire(Vec(numPorts,Vec(elementsPerBlock,UInt(elementWidth.W))))
  val tmp1 = Wire(Vec(numPorts,Vec(elementsPerBlock,UInt(elementWidth.W))))
  val forwarding = Wire(Vec(numPorts, Bool()))

  // Combinational Logic
  for (i <- 0 until numPorts) {
    // Assign the addresses: addrHi->block address, addrLo->element address
    addr(i).addrHi := io.addr(i)(
      log2Up(sramDepth * elementsPerBlock) - 1, log2Up(elementsPerBlock))
    addr(i).addrLo := io.addr(i)(log2Up(elementsPerBlock) - 1, 0)

    val fwd = (io.we(i) && writePending(i).valid &&
      addr(i).addrHi === writePending(i).addrHi)

    // Connections to the sram
    sram.weW(i) := writePending(i).valid
    sram.reR(i) := io.re(i) || (io.we(i) && !fwd)
    sram.dinW(i) := tmp1(i).asUInt
    sram.addrW(i) := writePending(i).addrHi
    sram.addrR(i) := addr(i).addrHi
    io.dout(i) := sram.doutR(i)

    // Defaults
    val doutRTupled = (((x: Int, y: Int) => sram.doutR(i)(x, y)) tupled)
    (0 until elementsPerBlock).map(j => tmp0(i)(j) := doutRTupled(index(j)))
    tmp1(i) := tmp0(i)
    forwarding(i) := fwd

    // Deal with a pending write if one exists
    when (writePending(i).valid) {
      // Write pending data to tmp0
      switch (writePending(i).wType) {
        is (0.U) {
          writeElement(tmp0(i), writePending(i).addrLo, writePending(i).dataElement) }
        is (1.U) {
          writeBlock(tmp0(i), writePending(i).dataBlock) }
        is (2.U) {
          writeBlockIncrement(tmp0(i), sram.doutR(i), writePending(i).dataBlock) }}

      // Handle forwarding by updating tmp1
      when (forwarding(i)) {
        switch (io.wType(i)) {
          is (0.U) {
            writeElement(tmp1(i), addr(i).addrLo, io.dinElement(i)) }
          is (1.U) {
            writeBlock(tmp1(i), io.din(i)) }
          is (2.U) {
            writeBlockIncrement(tmp1(i), tmp0(i).asUInt, io.din(i)) }}}

      printf("[INFO] SramEleInc: WRITE port/type/fwd?/fwdType 0x%x/0x%x/0x%x/0x%x\n",
        i.U, writePending(i).wType, forwarding(i), io.wType(i))
      printf("[INFO]              DATA addr/dataOld/dataNew 0x%x/0x%x/0x%x\n",
        writePending(i).addrHi##writePending(i).addrLo, sram.doutR(i),
        tmp1(i).asUInt)
    }
  }

  // Sequential Logic
  for (i <- 0 until numPorts) {
    // Assign the pending write data
    writePending(i).valid := false.B
    when ((io.we(i)) && (forwarding(i) === false.B)) {
      writePending(i).valid := true.B
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
    io.we(i) && io.wType(i) > 2.U)).contains(true.B),
    "SRAMElementIncrement saw unsupported wType > 2")
}
