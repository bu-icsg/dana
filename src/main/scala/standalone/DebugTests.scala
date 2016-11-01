package xfiles.standalone

import chisel3._
import chisel3.util._
import cde.Parameters
import xfiles.XFilesUserRequests

class DebugTester(implicit p: Parameters) extends XFilesTester {
  val s_INIT :: s_WRITE :: s_READ :: s_DONE :: Nil = Enum(UInt(), 4)

  val t_ECHO :: t_L1R :: Nil = Enum(UInt(), 2)

  val state = Reg(UInt(width = log2Up(4)), init = s_INIT)

  val test = Reg(UInt(width = 32), init = UInt(0))

  when (state === s_INIT) { state := s_WRITE }

  when (state === s_WRITE) {
    switch (test) {
      is (UInt(0)) { debug_echo_via_reg(0xdead) }
      is (UInt(1)) { debug_read_mem(0) }
    }
    state := s_READ
  }

  when (state === s_READ && dut.io.resp.fire()) {
    printf("[INFO] Saw response 0x%x\n", dut.io.resp.bits.data)
    state := Mux(test === UInt(1), s_DONE, s_READ)
    test := test + UInt(1)
    switch (test) {
      is (UInt(0)) { assert (dut.io.resp.bits.data === UInt("hdead"),
        "XFiles did not echo sent data") }
      is (UInt(1)) { assert (dut.io.resp.bits.data === UInt("hbeef"),
        "XFiles did not read correct L1 data") }
    }
  }

  when (state === s_DONE) { stop() }
}
