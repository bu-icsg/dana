package rocketchip

import chisel3._
import chisel3.util._
import chisel3.testers.BasicTester
import cde.{Field, Parameters}
import rocket._
import xfiles._
import dana._

class HoneyPot[T <: Bundle](req: => T, name: String = "") extends Module {
  val io = Decoupled(req)
  io.ready := Bool(true)
  assert(!(io.valid), s"Module tried to access HoneyPot $name")
}

abstract class RoccTester[T <: RoCC](x: Int = 0)(implicit p: Parameters)
    extends BasicTester {
  def dut: T

  val status = Reg(new MStatus)
  status.elements map { case (s: String, d: Data) => d := UInt(0) }

  def xcustom(funct: Int, rd: Int = 0, rs1: Int = 0, rs2: Int = 0,
    rs1_d: Int = 0, rs2_d: Int = 0) {
    dut.io.cmd.valid := Bool(true)
    dut.io.cmd.bits.inst.rd := UInt(rd)
    dut.io.cmd.bits.inst.xd := UInt(rd) =/= UInt(0)
    dut.io.cmd.bits.inst.rs1 := UInt(rs1)
    dut.io.cmd.bits.inst.rs2 := UInt(rs2)
    dut.io.cmd.bits.inst.funct := UInt(funct)
    dut.io.cmd.bits.inst.opcode := { x match {
      case 0 => UInt("b0001011")
      case 1 => UInt("b0101011")
      case 2 => UInt("b1011011")
      case 3 => UInt("b1111011")
      case _ => throw new Exception("Undefined XCustom type (should be [0,3])")
    }}
    dut.io.cmd.bits.rs1 := UInt(rs1_d)
    dut.io.cmd.bits.rs2 := UInt(rs2_d)
    dut.io.cmd.bits.status := status
  }

  def setPrv(prv: Int) {
    prv match {
      case prv if (prv >= 0 && prv <= 3) => status.prv := UInt(prv)
      case _ => throw new Exception("Invalid privilege change request")}
  }
}

class XFilesTester(implicit p: Parameters) extends RoccTester[XFiles]
    with XFilesUserRequests {
  val dut = Module(new XFiles)
  dut.io.resp.ready := Bool(true)

  def debug_echo_via_reg(data: Int) {
    xcustom(funct=t_USR_XFILES_DEBUG, rd=1, rs1_d=data)
  }
}

class Standalone(implicit p: Parameters) extends XFilesTester {
  val s_INIT :: s_WRITE :: s_READ :: s_DONE :: Nil = Enum(UInt(), 4)

  val state = Reg(UInt(width = log2Up(4)), init = s_INIT)

  when (state === s_INIT) { state := s_WRITE }

  when (state === s_WRITE) { debug_echo_via_reg(0xdead)
    state := s_READ }

  when (state === s_READ && dut.io.resp.fire()) {
    printf("[INFO] Saw response 0x%x\n", dut.io.resp.bits.data)
    state := s_DONE
    assert (dut.io.resp.bits.data === UInt("hdead"),
      "XFiles did not echo sent data") }

  when (state === s_DONE) { stop() }
}
