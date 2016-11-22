// See LICENSE for license details.

#include "src/main/resources/xcustom.h"

XCustom::XCustom(int x, privilegeMode prv) {
  if (x < 0 || x > 3)
    throw std::domain_error("XCustom x must be on range [0, 3]");
  x_ = x;
  prv_ = prv;
}

privilegeMode XCustom::ChangePrv(privilegeMode prv) {
  privilegeMode prv_old = prv_;
  prv_ = prv;
  return prv_old;
}

roccCmd XCustom::Instruction(int funct, uint64_t rs1, uint64_t rs2, int rs1_d,
                             int rs2_d, int rd) {
  roccCmd cmd;
  roccInsn * r = &cmd.inst.rocc;
  r->funct = funct;
  r->rs1 = rs1_d;
  r->rs2 = rs2_d;
  r->rd = rd;

  r->xs1 = 1;
  r-> xs2 = 1;
  r->xd = rd != 0;
  switch (x_) {
    case (0): r->opcode = 0b0001011; break;
    case (1): r->opcode = 0b0101011; break;
    case (2): r->opcode = 0b1011011; break;
    case (3): r->opcode = 0b1111011; break;
  }

  cmd.rs1 = rs1;
  cmd.rs2 = rs2;
  return cmd;
}

roccCmd XCustom::Unimplemented() {
  throw std::logic_error("Unimplemented function");
}
