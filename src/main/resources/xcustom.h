// See LICENSE for license details.

#ifndef SRC_MAIN_RESOURCES_XCUSTOM_H_
#define SRC_MAIN_RESOURCES_XCUSTOM_H_

#include <stdint.h>

enum privilegeMode {kUser, kSupervisor, kHypervisor, kMachine};

struct roccInsn {
  unsigned opcode : 7;
  unsigned rd     : 5;
  unsigned xs2    : 1;
  unsigned xs1    : 1;
  unsigned xd     : 1;
  unsigned rs1    : 5;
  unsigned rs2    : 5;
  unsigned funct  : 7;
};

union roccInsnUnion {
  roccInsn rocc;
  uint32_t raw;
};

class RoccCmd {
 public:
  RoccCmd(roccInsnUnion inst, uint64_t rs1, uint64_t rs2) {
    inst_ = inst;
    rs1_ = rs1;
    rs2_ = rs2;
  }
 public:
  roccInsnUnion inst_;
  uint64_t rs1_;
  uint64_t rs2_;
};

class RoccResp {
 public:
  RoccResp(unsigned rd, uint64_t data) {
    rd_ = rd;
    data_ = data;
  }
  bool operator== (const RoccResp &b) const {
    bool same = this->rd_ == b.rd_;
    same &= (this->data_ == b.data_);
    return same;
  }
  bool operator!= (const RoccResp&b) const {
    bool same = this->rd_ != b.rd_;
    same |= (this->data_ != b.data_);
    return same;
  }
 public:
  unsigned rd_;
  uint64_t data_;
};

class XCustom {
 private:
  int x_;
  privilegeMode prv_;

 public:
  XCustom(int x, privilegeMode prv = kUser);
  privilegeMode ChangePrv(privilegeMode prv);
  RoccCmd * Instruction(int funct, uint64_t rs1, uint64_t rs2, int rs1_d = 1,
                        int rs2_d = 2, int rd = 0);
  RoccCmd * Unimplemented();
};

#endif  // SRC_MAIN_RESOURCES_XCUSTOM_H_
