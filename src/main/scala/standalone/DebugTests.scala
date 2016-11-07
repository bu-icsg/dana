package xfiles.standalone

import chisel3._
import chisel3.util._
import cde.Parameters
import xfiles.XFilesUserRequests

class DebugTester(implicit p: Parameters) extends XFilesTester {
  val s_INIT :: s_WRITE :: s_READ :: s_DONE :: Nil = Enum(UInt(), 4)
  val t_ECHO :: t_L1R :: t_L1W :: t_V2P :: t_UTLR :: t_UTLW :: Nil = Enum(UInt(), 6)
  val lastTest = t_UTLW

  val state = Reg(UInt(width = log2Up(16)), init = s_INIT)
  val test = Reg(UInt(width = 32), init = t_ECHO)

  when (state === s_INIT) { state := s_WRITE }

  val data = Seq(0xaaaa, 0xbbbb, 0xcccc, 0xdddd)
  when (state === s_WRITE) {
    switch (test) {
      is (t_ECHO) { debug_echo_via_reg(data(0)) }
      is (t_L1R)  { debug_read_mem(0) }
      is (t_L1W)  { debug_write_mem(0, data(1)) }
      is (t_V2P)  { debug_virt_to_phys(0) }
      is (t_UTLR) { debug_read_utl(0) }
      is (t_UTLW) { debug_write_utl(0, data(2)) }
    }
    state := s_READ
  }

  when (state === s_READ && dut.io.resp.fire()) {
    printf("[INFO] Saw response 0x%x\n", dut.io.resp.bits.data)
    state := Mux(test === lastTest, s_DONE, s_READ)
    test := test + UInt(1)
    val r = dut.io.resp.bits.data
    switch (test) {
      is (t_ECHO) { assert (r === UInt(data(0)), "XFiles did not echo sent data") }
      is (t_L1R)  { }
      is (t_L1W)  { }
      is (t_V2P)  { }
      is (t_UTLR) { }
      is (t_UTLW) { }
    }
  }

  when (state === s_DONE) { stop() }
}
