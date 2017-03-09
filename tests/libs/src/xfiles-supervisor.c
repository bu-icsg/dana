// See LICENSE.IBM for license details.

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

xlen_t xf_read_csr(xlen_t csr) {
  xlen_t csr_value;
  XFILES_INSTRUCTION_R_R_I(csr_value, csr, 0, t_SUP_READ_CSR);
  return csr_value;
}

xlen_t xf_write_csr(xlen_t csr, xlen_t val) {
  xlen_t csr_value;
  XFILES_INSTRUCTION_R_R_R(csr_value, csr, val, t_SUP_WRITE_CSR);
  return csr_value;
}
