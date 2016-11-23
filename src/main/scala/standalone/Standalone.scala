package xfiles.standalone

import chisel3._
import chisel3.util._
import chisel3.testers.BasicTester
import cde.{Field, Parameters}
import rocket.{RoCCCommand, RoCCResponse, RoCC, RoccNPTWPorts}
import xfiles._
import dana._
import uncore.devices.TileLinkTestRAM
import uncore.tilelink.HasTileLinkParameters

case object TileLinkRAMSize extends Field[Int]

class HoneyPot[T <: Bundle](name: String = "", fatal: Boolean = true) extends Module {
  val io = IO(new Bundle {
    val req = Decoupled(new Bundle{}).flip
    val resp = Valid(new Bundle{})
  })

  io.req.ready := Bool(true)
  io.resp.valid := Bool(false)
  val i = s"Module tried to access HoneyPot $name"
  if (fatal)
    assert(!(io.req.valid), i)
  else
    when (io.req.valid) { printf(s"[WARN] HoneyPot: $i") }
}

class RoccTester[T <: RoCC](gen: => T)(implicit val p: Parameters)
    extends Module with HasTileLinkParameters {
  val io = IO(new Bundle {
    val cmd = Decoupled(new RoCCCommand).flip
    val resp = Decoupled(new RoCCResponse)
    val busy = Bool(OUTPUT)
    val interrupt = Bool(INPUT)
    val exception = Bool(OUTPUT)
  })
  val dut = gen

  // Memory Honeypot
  val mem = Module(new HoneyPot(name="Memory"))
  mem.io.req.valid := dut.io.mem.req.valid
  dut.io.mem.req.ready := mem.io.req.ready
  dut.io.mem.resp.valid := mem.io.resp.valid

  // Real AUTL
  val autl = Module(new TileLinkTestRAM(p(TileLinkRAMSize)/tlDataBits)(p))
  autl.io <> dut.io.autl

  // PTW Honeypot
  val ptw = Vec(Seq.fill(p(RoccNPTWPorts))(Module(new HoneyPot(name="PTW")).io))
  ptw.zipWithIndex map { case (p, i) =>
    p.req.valid := dut.io.ptw(i).req.valid
    dut.io.ptw(i).req.ready := p.req.ready
    dut.io.ptw(i).resp.valid := p.resp.valid
  }

  // Expose the internal Cmd/Resp bits
  io.cmd <> dut.io.cmd
  io.resp <> dut.io.resp
}

class XFilesTester(implicit p: Parameters) extends RoccTester(Module(new XFiles))(p)
