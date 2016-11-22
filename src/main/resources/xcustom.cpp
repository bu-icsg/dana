// See LICENSE for license details.

#include "src/main/resources/xcustom.h"

RoccCmd::RoccCmd(roccInsnUnion inst, uint64_t rs1, uint64_t rs2) {
  inst_ = inst;
  rs1_ = rs1;
  rs2_ = rs2;
}

// RoccCmd::RoccCmd(unsigned opcode, unsigned rd, unsigned xs2, unsigned xs1,
//                  unsigned xd, unsigned rs1_a, unsigned rs2_a, unsigned funct,
//                  uint64_t rs1, uint64_t rs2) {

// }

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

RoccCmd * XCustom::Instruction(int funct, uint64_t rs1, uint64_t rs2, int rs1_d,
                             int rs2_d, int rd) {
  roccInsnUnion r;
  r.rocc.funct = funct;
  r.rocc.rs1 = rs1_d;
  r.rocc.rs2 = rs2_d;
  r.rocc.rd = rd;

  r.rocc.xs1 = 1;
  r.rocc.xs2 = 1;
  r.rocc.xd = rd != 0;
  switch (x_) {
    case (0): r.rocc.opcode = 0b0001011; break;
    case (1): r.rocc.opcode = 0b0101011; break;
    case (2): r.rocc.opcode = 0b1011011; break;
    case (3): r.rocc.opcode = 0b1111011; break;
  }

  return new RoccCmd(r, rs1, rs2);
}

RoccCmd * XCustom::Unimplemented() {
  throw std::logic_error("Unimplemented function");
}
