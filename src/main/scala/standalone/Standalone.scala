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
  })
  val dut = gen

  // Memory Honeypot
  val mem = Module(new HoneyPot(name="Memory", fatal=false))
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

// abstract class RoccTester[T <: RoCC](implicit p: Parameters)
//     extends BasicTester {
//   def dut: T
//   def xCustomType: Int
//   val testName = this.getClass.getSimpleName

//   private val status = Reg(new MStatus)
//   status.elements map { case (s: String, d: Data) => d := UInt(0) }

//   def xcustom(funct: Int, rd: Int = 0, rs1: Int = 0, rs2: Int = 0,
//     rs1_d: Long = 0, rs2_d: Long = 0): RoCCCommand = {
//     val tmp = Wire(new RoCCCommand)
//     tmp.inst.rd := UInt(rd)
//     tmp.inst.xd := UInt(rd) =/= UInt(0)
//     tmp.inst.rs1 := UInt(rs1)
//     tmp.inst.rs2 := UInt(rs2)
//     tmp.inst.funct := UInt(funct)
//     tmp.inst.opcode := { xCustomType match {
//       case 0 => UInt("b0001011")
//       case 1 => UInt("b0101011")
//       case 2 => UInt("b1011011")
//       case 3 => UInt("b1111011")
//       case _ => throw new Exception("Undefined XCustom type (should be [0,3])")
//     }}
//     tmp.rs1 := UInt(rs1_d)
//     tmp.rs2 := UInt(rs2_d)
//     tmp.status := status
//     tmp
//   }

//   def setPrv(prv: Int) {
//     prv match {
//       case prv if (prv >= 0 && prv <= 3) => status.prv := UInt(prv)
//       case _ => throw new Exception("Invalid privilege change request")}
//   }
// }

// abstract class XFilesTester(implicit p: Parameters)
//     extends RoccTester[XFiles]()( with XFilesUserRequests with XFilesDebugActions
//     with HasTileLinkParameters {
//   val dut = Module(new XFiles)
//   val xCustomType = 0

//   // Memory Honeypot
//   val mem = Module(new HoneyPot(name="Memory", fatal=false))
//   mem.io.req.valid := dut.io.mem.req.valid
//   dut.io.mem.req.ready := mem.io.req.ready
//   dut.io.mem.resp.valid := mem.io.resp.valid

//   // Real AUTL
//   val autl = Module(new TileLinkTestRAM(p(TileLinkRAMSize)/tlDataBits)(p))
//   autl.io <> dut.io.autl

//   // PTW Honeypot
//   val ptw = Vec(Seq.fill(p(RoccNPTWPorts))(Module(new HoneyPot(name="PTW")).io))
//   ptw.zipWithIndex map { case (p, i) =>
//     p.req.valid := dut.io.ptw(i).req.valid
//     dut.io.ptw(i).req.ready := p.req.ready
//     dut.io.ptw(i).resp.valid := p.resp.valid
//   }

//   dut.io.resp.ready := Bool(true)
//   dut.io.cmd.valid := Bool(false)

//   private def _debug_test(action: Int, data: Int = 0, rd: Int = 1, addr: Int = 0) {
//     val action_and_data: Long = (action.asInstanceOf[Long] << 32) | (data & ~(~0L << 32))
//     dut.io.cmd.valid := Bool(true)
//     dut.io.cmd.bits := xcustom(t_USR_XFILES_DEBUG, rd, rs1_d=action_and_data, rs2_d=addr)
//   }

//   def debug_echo_via_reg (d: Int)         { _debug_test(a_REG, data=d)               }
//   def debug_read_mem     (a: Int)         { _debug_test(a_MEM_READ, addr=a)          }
//   def debug_write_mem    (a: Int, d: Int) { _debug_test(a_MEM_WRITE, addr=a, data=d) }
//   def debug_virt_to_phys (v: Int)         { _debug_test(a_VIRT_TO_PHYS, addr=v)      }
//   def debug_read_utl     (a: Int)         { _debug_test(a_UTL_READ, addr=a)          }
//   def debug_write_utl    (a: Int, d: Int) { _debug_test(a_UTL_WRITE, data=d, addr=a) }

//   def set_asid(asid: Int) {}
//   def set_antp(antp: Int, size: Long) {}
//   def xf_read_csr(csr: Int) {}

//   def xfiles_dana_id(flag_print: Boolean) {}
//   def new_write_request(nnid: Int, learning_type: Int, num_train_outputs: Int) {}
//   def write_register(tid: Int, reg: Int, value: Int) {}
//   def write_data(tid: Int, data: Seq[Int]) {}
//   def write_data_except_last(tid: Int, data: Seq[Int]) {}
//   def write_data_last(tid: Int, data: Seq[Int]) {}
//   def write_data_train_incremental(tid: Int, data_in: Seq[Int], data_out: Seq[Int]) {}
//   def read_data_spinlock(tid: Int, data_out: Seq[Int]) {}
//   def kill_transaction(tid: Int) {}
// }
