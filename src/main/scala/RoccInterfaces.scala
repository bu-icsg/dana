package dana

import Chisel._

case object XLen extends Field[Int]

abstract trait CoreParameters extends UsesParameters {
  val xLen = params(XLen)
}

abstract class CoreBundle extends Bundle with CoreParameters

class RoCCInstruction extends Bundle
{
  val funct = Bits(width = 7)
  val rs2 = Bits(width = 5)
  val rs1 = Bits(width = 5)
  val xd = Bool()
  val xs1 = Bool()
  val xs2 = Bool()
  val rd = Bits(width = 5)
  val opcode = Bits(width = 7)
}

class RoCCCommand extends CoreBundle
{
  val inst = new RoCCInstruction
  val rs1 = Bits(width = xLen)
  val rs2 = Bits(width = xLen)
}

class RoCCResponse extends CoreBundle
{
  val rd = Bits(width = 5)
  val data = Bits(width = xLen)
}

class RoCCInterface extends Bundle
{
  val cmd = Decoupled(new RoCCCommand).flip
  val resp = Decoupled(new RoCCResponse)
  // val mem = new HellaCacheIO
  val busy = Bool(OUTPUT)
  val s = Bool(INPUT)
  val interrupt = Bool(OUTPUT)

  // I'm purposefully ignoring evrything that I'm not using in the
  // RoCCInterface.
  // val imem = new HeaderlessUncachedTileLinkIO
  // val dmem = new HeaderlessTileLinkIO
  // val iptw = new TLBPTWIO
  // val dptw = new TLBPTWIO
  // val pptw = new TLBPTWIO
  // val exception = Bool(INPUT)
}
