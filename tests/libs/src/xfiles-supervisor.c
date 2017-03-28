// See LICENSE.IBM for license details.

#include "tests/libs/src/include/xfiles-supervisor.h"

asid_type set_asid(asid_type * asid, tid_type * tid) {
  *asid = xf_write_csr(CSRs_asid, *asid);
  *tid = xf_write_csr(CSRs_tid, *tid);
  return *asid;
}

ant_entry * set_antp(ant_entry * antp, size_t * size) {
  antp = (ant_entry *) xf_write_csr(CSRs_antp, (xlen_t) antp);
  size = (size_t *) xf_write_csr(CSRs_num_asids, *size);
  return antp;
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
