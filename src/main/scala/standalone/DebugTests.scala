package xfiles.standalone

import chisel3._
import chisel3.util._
import cde.Parameters
import xfiles.XFilesUserRequests

class DebugTester(implicit p: Parameters) extends XFilesTester {
  val s_INIT :: s_WRITE :: s_READ :: s: :: s_DONE :: Nil = Enum(UInt(), 4)

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
