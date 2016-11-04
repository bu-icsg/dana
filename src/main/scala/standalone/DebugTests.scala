package xfiles.standalone

import chisel3._
import chisel3.util._
import cde.Parameters
import xfiles.XFilesUserRequests

class DebugTester(implicit p: Parameters) extends XFilesTester {
  val s_INIT :: s_WRITE :: s_READ :: s_DONE :: Nil = Enum(UInt(), 4)

  val t_ECHO :: t_L1R :: t_L1W :: Nil = Enum(UInt(), 3)

  val state = Reg(UInt(width = log2Up(16)), init = s_INIT)

  val test = Reg(UInt(width = 32), init = UInt(0))

  when (state === s_INIT) { state := s_WRITE }

  val data = Seq(0xaaaa, 0xbbbb, 0xcccc, 0xdddd)
  when (state === s_WRITE) {
    switch (test) {
      is (t_ECHO) { debug_echo_via_reg(data(0)) }
    }
    state := s_READ
  }

  when (state === s_READ && dut.io.resp.fire()) {
    printf("[INFO] Saw response 0x%x\n", dut.io.resp.bits.data)
    state := Mux(test === t_ECHO, s_DONE, s_READ)
    test := test + UInt(1)
    switch (test) {
      is (t_ECHO) { assert (dut.io.resp.bits.data === UInt(data(0)),
        "XFiles did not echo sent data") }
    }
  }

  when (state === s_DONE) { stop() }
}
