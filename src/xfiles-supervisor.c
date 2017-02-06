// See LICENSE for license details.

#include "src/include/xfiles-supervisor.h"

xlen_t set_asid(asid_type asid) {
  int old_asid;
  XFILES_INSTRUCTION_R_R_I(old_asid, asid, 0, t_SUP_UPDATE_ASID);
  return old_asid;
}

xlen_t set_antp(ant_entry * antp, size_t size) {
  int old_antp;
  XFILES_INSTRUCTION(old_antp, antp, size, t_SUP_WRITE_REG);
  return old_antp;
}

xlen_t xf_read_csr(xfiles_csr_t csr) {
  xlen_t csr_value;
  XFILES_INSTRUCTION_R_R_I(csr_value, csr, 0, t_SUP_READ_CSR);
  return csr_value;
}
