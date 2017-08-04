// See LICENSE.BU for license details.

package dana

import chisel3._
import chisel3.util._
import scala.Array
import scala.math.min

case class Dimension(width: Int, height: Int)

class SRAMVariantInterface(
  val dataWidth: Int,
  val sramDepth: Int,
  val numPorts: Int
) extends Bundle {
  override def cloneType = new SRAMVariantInterface(
    dataWidth = dataWidth,
    sramDepth = sramDepth,
    numPorts = numPorts).asInstanceOf[this.type]
  val we   = Input(Vec(numPorts, Bool()))
  val re   = Input(Vec(numPorts, Bool()))
  val din  = Input(Vec(numPorts, UInt(dataWidth.W)))
  val addr = Input(Vec(numPorts, UInt(log2Up(sramDepth).W)))
  val dout = Output(Vec(numPorts, UInt(dataWidth.W)))
}

class SRAMVariant(
  val id: Int = 0,
  val dataWidth: Int = 32,
  val sramDepth: Int = 64,
  val numPorts: Int = 1
) extends Module {

  def writeElement(a: Vec[UInt], index: UInt, b: UInt) { a(index) := b }

  def divUp (dividend: Int, divisor: Int): Int = {
    (dividend + divisor - 1) / divisor}

  lazy val io = IO(new SRAMVariantInterface(
    dataWidth = dataWidth,
    sramDepth = sramDepth,
    numPorts = numPorts))

  val blockSize = Dimension(min(32, dataWidth), min(1024, sramDepth))
  val rows = divUp(sramDepth, blockSize.height)
  val cols = divUp(dataWidth, blockSize.width)
  require(dataWidth % blockSize.width == 0)

  val blockRows = Seq.fill(rows)(Wire(new SRAMInterface(dataWidth = dataWidth,
                                                        numReadPorts = numPorts,
                                                        numWritePorts = numPorts,
                                                        numReadWritePorts = 0,
                                                        sramDepth = blockSize.height)))
  for (r <- 0 until rows) {
    val srams = Seq.fill(cols)(Module(new SRAM(
      id = id,
      dataWidth = blockSize.width,
      sramDepth = blockSize.height,
      numReadPorts = numPorts,
      numWritePorts = numPorts,
      numReadWritePorts = 0)))
    for (i <- 0 until numPorts) {
      for (c <- 0 until cols) {
        srams(c).io.weW(i) := blockRows(r).weW(i)
        srams(c).io.dinW(i) := blockRows(r).dinW(i)((c + 1) * blockSize.width - 1, c * blockSize.width)
        srams(c).io.addrW(i) := blockRows(r).addrW(i)
        srams(c).io.reR(i) := blockRows(r).reR(i)
        srams(c).io.addrR(i) := blockRows(r).addrR(i)
      }
      blockRows(r).doutR(i) := srams.map(a => a.io.doutR(i)).reverse.reduce((a, b) => a ## b)
    }
  }
  val sram = Wire(new SRAMInterface(dataWidth = dataWidth,
                                    numReadPorts = numPorts,
                                    numWritePorts = numPorts,
                                    numReadWritePorts = 0,
                                    sramDepth = sramDepth))
  for (i <- 0 until numPorts) {
    sram.weW(i) := io.we(i)
    sram.addrW(i) := io.addr(i)
    sram.dinW(i) := io.din(i)
    sram.reR(i) := io.re(i)
    sram.addrR(i) := io.addr(i)
    io.dout(i) := sram.doutR(i)

    val (bankR, bankW) = rows compare 1 match {
      case 0 => (0.U, 0.U)
      case 1 => (
        sram.addrR(i)(log2Up(sramDepth) - 1, log2Up(blockSize.height)),
        sram.addrW(i)(log2Up(sramDepth) - 1, log2Up(blockSize.height)))
      case _ =>
        throw new Exception(s"Unable to determine banking for rows: ${rows}")
    }
    blockRows.zipWithIndex.map { case(row, rowIdx) => {
      row.weW(i)   := sram.weW(i) && bankW === rowIdx.U;
      row.addrW(i) := sram.addrW(i)(log2Up(blockSize.height) - 1, 0)
      row.dinW(i)  := sram.dinW(i)

      row.reR(i)   := sram.reR(i) && bankR === rowIdx.U;
      row.addrR(i) := sram.addrR(i)(log2Up(blockSize.height) - 1, 0)
    }}
    sram.doutR(i) := MuxLookup(RegNext(bankR), blockRows(0).doutR(i), (0 until rows).
      map (r => (r.U -> blockRows(r).doutR(i))))
  }
}
