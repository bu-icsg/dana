// See LICENSE.BU for license details.
// See LICENSE.IBM for license details.

package dana

import chisel3._
import chisel3.util._

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
  val inc = Vec(numPorts, Bool()).asInput
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
  val data = UInt(dataWidth.W)
  val addr = UInt(log2Up(sramDepth).W)
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
  override lazy val io = IO(new SRAMBlockIncrementInterface(
    dataWidth = dataWidth,
    sramDepth = sramDepth,
    numPorts = numPorts,
    elementWidth = elementWidth))

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

  val writePending = Reg(Vec(numPorts, new WritePendingBlockIncrementBundle(
    elementWidth = elementWidth,
    dataWidth = dataWidth,
    sramDepth = sramDepth)))

  val tmp0 = Wire(Vec(numPorts, Vec(elementsPerBlock, UInt(elementWidth.W))))
  val tmp1 = Wire(Vec(numPorts, Vec(elementsPerBlock, UInt(elementWidth.W))))
  val forwarding = Wire(Vec(numPorts, Bool()))

  // Combinational Logic
  for (i <- 0 until numPorts) {
    // Connections to the sram
    sram.io.weW(i) := writePending(i).valid
    sram.io.dinW(i) := tmp1(i).asUInt
    sram.io.addrW(i) := writePending(i).addr
    sram.io.addrR(i) := io.addr(i)
    io.dout(i) := sram.io.doutR(i)

    // Defaults
    val doutRTupled = (((x: Int, y: Int) => sram.io.doutR(i)(x, y)) tupled)
    (0 until elementsPerBlock).map(j => tmp0(i)(j) := doutRTupled(index(j)))
    tmp1(i) := tmp0(i)
    forwarding(i) := writePending(i).valid && io.we(i) &&
      io.addr(i) === writePending(i).addr

    // Handle a pending write if one exists
    when (writePending(i).valid) {
      when (!writePending(i).inc) {
        writeBlock(tmp0(i), writePending(i).data)
      } .otherwise {
        writeBlockIncrement(tmp0(i), writePending(i).data, sram.io.doutR(i)) }

      // Deal with forwarding
      when (forwarding(i)) {
        when (!io.inc(i)) {
          writeBlock(tmp0(i), io.din(i))
        } .otherwise {
          writeBlockIncrement(tmp1(i), tmp0(i).asUInt, io.din(i)) }}

      printf("[INFO] SramBloInc: WRITE port/inc?/fwd?/fwdInc? 0x%x/0x%x/0x%x/0x%x\n",
        i.U, writePending(i).inc, forwarding(i), io.inc(i))
      printf("[INFO]              DATA addr/dataOld/dataNew 0x%x/0x%x/0x%x\n",
        writePending(i).addr, sram.io.doutR(i), tmp1(i).asUInt) }}

  // Sequential Logic
  for (i <- 0 until numPorts) {
    // Assign the pending write data
    writePending(i).valid := false.B
    when ((io.we(i)) && (forwarding(i) === false.B)) {
      writePending(i).valid := true.B
      writePending(i).inc := io.inc(i)
      writePending(i).data := io.din(i)
      writePending(i).addr := io.addr(i) }}
}
