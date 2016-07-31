// See LICENSE for license details.

package dana

import Chisel._

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
  val dinElement = Vec(numPorts, UInt(INPUT, width = elementWidth))
  override val addr = Vec(numPorts, UInt(INPUT,
    width = log2Up(sramDepth) + log2Up(dataWidth / elementWidth)))
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
  val data = UInt(width = elementWidth)
  val addrHi = UInt(width = log2Up(sramDepth))
  val addrLo = UInt(width = log2Up(dataWidth / elementWidth))
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
  override lazy val io = new SRAMElementInterface(
    dataWidth = dataWidth,
    sramDepth = sramDepth,
    numPorts = numPorts,
    elementWidth = elementWidth
  )

  val elementsPerBlock = divUp(dataWidth, elementWidth)

  def index(j: Int): (Int, Int) = (elementWidth*(j+1) - 1, elementWidth * j)

  val addr = Vec(numPorts, new Bundle {
    val addrHi = Wire(UInt(width = log2Up(sramDepth)))
    val addrLo = Wire(UInt(width = log2Up(elementsPerBlock)))})

  val writePending = Reg(Vec(numPorts, new WritePendingBundle(
    elementWidth = elementWidth,
    dataWidth = dataWidth,
    sramDepth = sramDepth)))

  val tmp = Wire(Vec(numPorts, Vec(elementsPerBlock, UInt(width = elementWidth) )))
  val forwarding = Wire(Vec(numPorts, Bool()))

  // Combinational Logic
  for (i <- 0 until numPorts) {
    // Assign the addresses
    addr(i).addrHi := io.addr(i).toBits()(
      log2Up(sramDepth * elementsPerBlock) - 1, log2Up(elementsPerBlock))
    addr(i).addrLo := io.addr(i).toBits()(log2Up(elementsPerBlock) - 1, 0)

    // Connections to the sram
    sram.io.weW(i) := writePending(i).valid
    sram.io.dinW(i) := tmp(i).toBits()
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
    writePending(i).valid := Bool(false)
    when ((io.we(i)) && (forwarding(i) === Bool(false))) {
      writePending(i).valid := Bool(true)
      writePending(i).data := io.dinElement(i)
      writePending(i).addrHi := addr(i).addrHi
      writePending(i).addrLo := addr(i).addrLo }}
}

// class SRAMElementTests(uut: SRAMElement, isTrace: Boolean = true)
//     extends Tester(uut, isTrace) {
//   // Extracts an element from a block
//   def extractElement(block: Int, element: Int): Int = {
//     if (element == 0)
//       block & ~((~0) << uut.elementWidth)
//     else
//       (block >> (element * uut.elementWidth)) & ~((~0) << uut.elementWidth)
//   }

//   // Generate some random local data
//   val copy = Array.fill(uut.sramDepth){0}
//   for (i <- 0 until uut.sramDepth) {
//     copy(i) = rnd.nextInt((Math.pow(2, uut.dataWidth) - 1).toInt)
//   }

//   // No forwarding test
//   println("[INFO] Sequential writes, no forwarding")
//   for (i <- 0 until uut.sramDepth) {
//     for (j <- 0 until (uut.divUp(uut.dataWidth, uut.elementWidth))) {
//       poke(uut.io.we(0), 1)
//       poke(uut.io.addr(0), ((i << log2Up(uut.divUp(uut.dataWidth, uut.elementWidth))) + j))
//       poke(uut.io.dinElement(0), extractElement(copy(i), j))
//       step(1)
//       poke(uut.io.we(0), 0)
//       step(1)
//     }
//   }

//   step(1)
//   for (i <- 0 until uut.sramDepth) {
//     poke(uut.io.addr(0), (i << log2Up(uut.divUp(uut.dataWidth, uut.elementWidth))))
//     step(1)
//     printf("%08x =? %08x", copy(i), peek(uut.io.dout(0)))
//     if (!expect(uut.io.dout(0), copy(i)))
//       printf(" X\n")
//     else
//       printf("\n")
//   }

//   // Forwarding Test
//   println("[INFO] Sequential writes, all forwards")
//   for (i <- 0 until uut.sramDepth) {
//     copy(i) = rnd.nextInt((Math.pow(2, uut.dataWidth) - 1).toInt)
//   }
//   for (i <- 0 until uut.sramDepth) {
//     for (j <- 0 until (uut.divUp(uut.dataWidth, uut.elementWidth))) {
//       poke(uut.io.we(0), 1)
//       poke(uut.io.addr(0), ((i << log2Up(uut.divUp(uut.dataWidth, uut.elementWidth))) + j))
//       poke(uut.io.dinElement(0), extractElement(copy(i), j))
//       step(1)
//     }
//   }
//   poke(uut.io.we(0), 0)

//   step(1)
//   for (i <- 0 until uut.sramDepth) {
//     poke(uut.io.addr(0), (i << log2Up(uut.divUp(uut.dataWidth, uut.elementWidth))))
//     step(1)
//     printf("%08x =? %08x", copy(i), peek(uut.io.dout(0)))
//     if (!expect(uut.io.dout(0), copy(i)))
//       printf(" X\n")
//     else
//       printf("\n")
//   }

//   // Random Forwarding Test
//   println("[INFO] Sequential writes, random forwards")
//   for (i <- 0 until uut.sramDepth) {
//     copy(i) = rnd.nextInt((Math.pow(2, uut.dataWidth) - 1).toInt)
//   }
//   for (i <- 0 until uut.sramDepth) {
//     for (j <- 0 until (uut.divUp(uut.dataWidth, uut.elementWidth))) {
//       poke(uut.io.we(0), 1)
//       poke(uut.io.addr(0), ((i << log2Up(uut.divUp(uut.dataWidth, uut.elementWidth))) + j))
//       poke(uut.io.dinElement(0), extractElement(copy(i), j))
//       step(1)
//       if (rnd.nextInt(2) == 1) {
//         poke(uut.io.we(0), 0)
//         step(1)
//       }
//     }
//   }
//   poke(uut.io.we(0), 0)

//   step(1)
//   for (i <- 0 until uut.sramDepth) {
//     poke(uut.io.addr(0), (i << log2Up(uut.divUp(uut.dataWidth, uut.elementWidth))))
//     step(1)
//     printf("%08x =? %08x", copy(i), peek(uut.io.dout(0)))
//     if (!expect(uut.io.dout(0), copy(i)))
//       printf(" X\n")
//     else
//       printf("\n")
//   }
// }
