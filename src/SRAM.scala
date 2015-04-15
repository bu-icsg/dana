package dana

import Chisel._

class SRAMInterface(
  val dataWidth: Int,
  val numReadPorts: Int,
  val numWritePorts: Int,
  val numReadWritePorts: Int,
  val sramDepth: Int
) extends Bundle {
  override def clone = new SRAMInterface(
    dataWidth = dataWidth,
    numReadPorts = numReadPorts,
    numWritePorts = numWritePorts,
    numReadWritePorts = numReadWritePorts,
    sramDepth = sramDepth
  ).asInstanceOf[this.type]
  // Data Input
  val dinRW = Vec.fill(numReadWritePorts){UInt(OUTPUT, width = dataWidth)}
  val dinW = Vec.fill(numWritePorts){UInt(OUTPUT, width = dataWidth)}
  // Data Output
  val doutRW = Vec.fill(numReadWritePorts){UInt(INPUT, width = dataWidth)}
  val doutR = Vec.fill(numReadPorts){UInt(INPUT, width = dataWidth)}
  // Addresses
  val addrRW = Vec.fill(numReadWritePorts){UInt(OUTPUT, width = log2Up(sramDepth))}
  val addrR = Vec.fill(numReadPorts){UInt(OUTPUT, width = log2Up(sramDepth))}
  val addrW = Vec.fill(numWritePorts){UInt(OUTPUT, width = log2Up(sramDepth))}
  // Write enable
  val weRW = Vec.fill(numReadWritePorts){Bool(OUTPUT)}
  val weW = Vec.fill(numWritePorts){Bool(OUTPUT)}
}

class SRAM (
  // The default is an SRAM with two RW ports
  val numReadPorts: Int = 0,
  val numWritePorts: Int = 0,
  val numReadWritePorts: Int = 2,
  val dataWidth: Int = 8,
  val sramDepth: Int = 64
) extends Module {
  val io = new SRAMInterface(
    numReadPorts = numReadPorts,
    numWritePorts = numWritePorts,
    numReadWritePorts = numReadWritePorts,
    dataWidth = dataWidth,
    sramDepth = sramDepth
  ).flip
  val mem = Mem(UInt(width = dataWidth), sramDepth)

  for (i <- 0 until numReadPorts) {
    io.doutR(i) := mem(io.addrR(i))
  }

  for (i <- 0 until numWritePorts) {
    when (io.weW(i)) {
      mem(io.addrW(i)) := io.dinW(i)
    }
  }

  for (i <- 0 until numReadWritePorts) {
    when (io.weRW(i)) {
      mem(io.addrRW(i)) := io.dinRW(i)
    }
    io.doutRW(i) := mem(io.addrRW(i))
  }
}

class SRAMTests(uut: SRAM, isTrace: Boolean = true)
    extends Tester(uut, isTrace) {
  // Generate a local copy of the memory in a vector
  val copy = Array.fill(uut.sramDepth){0}
  for (i <- 0 until uut.sramDepth) {
    copy(i) = rnd.nextInt((Math.pow(2, uut.dataWidth) - 1).toInt)
  }
  // Write all the data into the memory
  for (i <- 0 until uut.sramDepth) {
    poke(uut.io.weRW(0), 1)
    poke(uut.io.addrRW(0), i)
    poke(uut.io.dinRW(0), copy(i))
    step(1)
    poke(uut.io.weRW(0), 0)
  }
  // Verify that all the data is correct
  for (i <- 0 until uut.sramDepth) {
    poke(uut.io.addrRW(1), i)
    step(1)
    expect(uut.io.doutRW(1), copy(i))
  }
}
