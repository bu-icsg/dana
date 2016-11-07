package xfiles.standalone

import chisel3._
import chisel3.util._
import cde.Parameters
import xfiles.XFilesUserRequests

class DebugTester(implicit p: Parameters) extends XFilesTester {
  val s_INIT :: s_WRITE :: s_READ :: s_DONE :: Nil = Enum(UInt(), 4)
  val t_ECHO :: t_UTLW :: t_UTLR :: t_L1R :: t_L1W :: t_V2P :: Nil = Enum(UInt(), 6)
  val lastTest = t_UTLR

  val state = Reg(init = s_INIT)
  val test = Reg(init = t_ECHO)

  when (state === s_INIT) { state := s_WRITE }

  val data = Seq(0xaaaa, 0xbbbb, 0xcccc, 0xdddd)
  when (state === s_WRITE) {
    switch (test) {
      is (t_ECHO) { debug_echo_via_reg(data(0)) }
      is (t_L1W)  { debug_write_mem(0, data(1)) }
      is (t_L1R)  { debug_read_mem(0) }
      is (t_V2P)  { debug_virt_to_phys(0) }
      is (t_UTLW) { debug_write_utl(0, data(2)) }
      is (t_UTLR) { debug_read_utl(0) }
    }
    state := s_READ
  }

  when (state === s_READ && dut.io.resp.fire()) {
    printf("[INFO] Saw response 0x%x\n", dut.io.resp.bits.data)
    state := Mux(test === lastTest, s_DONE, s_WRITE)
    test := test + UInt(1)
    val r = dut.io.resp.bits.data
    switch (test) {
      is (t_ECHO) { assert (r === UInt(data(0)), "XFiles did not echo sent data") }
      is (t_L1W)  { }
      is (t_L1R)  { }
      is (t_V2P)  { }
      is (t_UTLW) { }
      is (t_UTLR) { assert (r === UInt(data(2)), "XFiles read wrong data over AUTL") }
    }
  }

  when (state === s_DONE) { stop() }

  when (state =/= RegNext(state)) {
    printf("[INFO] State change in DebugTester %d->%d\n", RegNext(state), state)
  }
}
