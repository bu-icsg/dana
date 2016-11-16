// See LICENSE for license details.

#include "src/main/resources/rocc.h"

Rocc::Rocc() {
  io.cmd.valid = Attach((char *) "TOP.XFilesTester.io_cmd_valid");
  io.cmd.ready = Attach((char *) "TOP.XFilesTester.io_cmd_ready");
  io.cmd.bits.rs1 = Attach((char *) "TOP.XFilesTester.io_cmd_bits_rs1");
  io.cmd.bits.rs2 = Attach((char *) "TOP.XFilesTester.io_cmd_bits_rs2");
  io.cmd.bits.inst.funct = Attach((char *) "TOP.XFilesTester.io_cmd_bits_inst_funct");
  io.cmd.bits.inst.rs1 = Attach((char *) "TOP.XFilesTester.io_cmd_bits_inst_rs1");
  io.cmd.bits.inst.rs2 = Attach((char *) "TOP.XFilesTester.io_cmd_bits_inst_rs2");
  io.cmd.bits.inst.xd = Attach((char *) "TOP.XFilesTester.io_cmd_bits_inst_xd");
  io.cmd.bits.inst.xs1 = Attach((char *) "TOP.XFilesTester.io_cmd_bits_inst_xs1");
  io.cmd.bits.inst.xs2 = Attach((char *) "TOP.XFilesTester.io_cmd_bits_inst_xs2");
  io.cmd.bits.inst.rd = Attach((char *) "TOP.XFilesTester.io_cmd_bits_inst_rd");
  io.cmd.bits.inst.opcode = Attach((char *) "TOP.XFilesTester.io_cmd_bits_inst_opcode");
}

vpiHandle Rocc::Attach(const char * s) {
  vpiHandle vh = vpi_handle_by_name((PLI_BYTE8*) s, NULL);
  if (!vh) throw std::runtime_error("Failed to attach signal");
  return vh;
}

void Rocc::Drive(vpiHandle vh, uint64_t value) {
  s_vpi_value x;
  x.format = vpiIntVal;
  x.value.integer = value;
  vpi_put_value(vh, &x, NULL, vpiForceFlag);
}

void Rocc::Cmd(roccCmd cmd) {
  Drive(io.cmd.valid, 1);
  Drive(io.cmd.bits.rs1, cmd.rs1);
  Drive(io.cmd.bits.rs2, cmd.rs2);
  Drive(io.cmd.bits.inst.funct, cmd.insn.rocc.funct);
  Drive(io.cmd.bits.inst.rs1, cmd.insn.rocc.rs1);
  Drive(io.cmd.bits.inst.rs2, cmd.insn.rocc.rs2);
  Drive(io.cmd.bits.inst.xd, cmd.insn.rocc.xd);
  Drive(io.cmd.bits.inst.xs1, cmd.insn.rocc.xs1);
  Drive(io.cmd.bits.inst.xs2, cmd.insn.rocc.xs2);
  Drive(io.cmd.bits.inst.rd, cmd.insn.rocc.rd);
  Drive(io.cmd.bits.inst.opcode, cmd.insn.rocc.opcode);
}

roccResponse Rocc::Resp() {
}
