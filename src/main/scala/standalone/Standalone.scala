package xfiles.standalone

import chisel3._
import chisel3.util._
import chisel3.testers.BasicTester
import cde.{Field, Parameters}
import rocket._
import xfiles._
import dana._

class HoneyPot[T <: Bundle](name: String = "") extends Module {
  val io = IO(new Bundle {
    val req = Decoupled(new Bundle{}).flip
    val resp = Valid(new Bundle{})
  })

  io.req.ready := Bool(true)
  io.resp.valid := Bool(false)
  assert(!(io.req.valid), s"Module tried to access HoneyPot $name")
}

abstract class RoccTester[T <: RoCC](implicit p: Parameters)
    extends BasicTester {
  def dut: T
  def xCustomType: Int

  private val status = Reg(new MStatus)
  status.elements map { case (s: String, d: Data) => d := UInt(0) }

  def xcustom(funct: Int, rd: Int = 0, rs1: Int = 0, rs2: Int = 0,
    rs1_d: Long = 0, rs2_d: Long = 0): RoCCCommand = {
    val tmp = Wire(new RoCCCommand)
    tmp.inst.rd := UInt(rd)
    tmp.inst.xd := UInt(rd) =/= UInt(0)
    tmp.inst.rs1 := UInt(rs1)
    tmp.inst.rs2 := UInt(rs2)
    tmp.inst.funct := UInt(funct)
    tmp.inst.opcode := { xCustomType match {
      case 0 => UInt("b0001011")
      case 1 => UInt("b0101011")
      case 2 => UInt("b1011011")
      case 3 => UInt("b1111011")
      case _ => throw new Exception("Undefined XCustom type (should be [0,3])")
    }}
    tmp.rs1 := UInt(rs1_d)
    tmp.rs2 := UInt(rs2_d)
    tmp.status := status
    tmp
  }

  def setPrv(prv: Int) {
    prv match {
      case prv if (prv >= 0 && prv <= 3) => status.prv := UInt(prv)
      case _ => throw new Exception("Invalid privilege change request")}
  }
}

class XFilesTester(implicit p: Parameters) extends RoccTester[XFiles]
    with XFilesUserRequests with XFilesDebugActions {
  val dut = Module(new XFiles)
  val xCustomType = 0

  // Memory Honeypot
  val mem = Module(new HoneyPot(name="Memory"))
  mem.io.req.valid := dut.io.mem.req.valid
  dut.io.mem.req.ready := mem.io.req.ready
  dut.io.mem.resp.valid := mem.io.resp.valid

  dut.io.resp.ready := Bool(true)

  private def _debug_test(action: Int, data: Int = 0, rd: Int = 1, addr: Int = 0) {
    val action_and_data: Long = (action.asInstanceOf[Long] << 32) | (data & ~(~0L << 32))
    dut.io.cmd.valid := Bool(true)
    dut.io.cmd.bits := xcustom(t_USR_XFILES_DEBUG, rd, rs1_d=action_and_data, rs2_d=addr)
  }

  def debug_echo_via_reg(d: Int) { _debug_test(a_REG, data=d) }
  def debug_read_mem(a: Int) { _debug_test(a_MEM_READ, addr=a) }
  def debug_write_mem(a: Int, d: Int) { _debug_test(a_MEM_WRITE, addr=a, data=d) }

}
