// See LICENSE for license details.

#include "src/main/c/xfiles-supervisor.h"

xlen_t set_asid(asid_type asid) {
  int old_asid;
  asm volatile ("custom0 %[old_asid], %[rs1], 0, 0"
                : [old_asid] "=r" (old_asid)
                : [rs1] "r" (asid));
  return old_asid;
}

xlen_t set_antp(asid_nnid_table_entry * antp, size_t size) {
  int old_antp;
  asm volatile ("custom0 %[old_antp], %[rs1], %[rs2], 1"
                : [old_antp] "=r" (old_antp)
                : [rs1] "r" (antp), [rs2] "r" (size));
  return old_antp;
}

xlen_t xf_read_csr(xfiles_csr_t csr) {
  xlen_t csr_value;
  asm volatile ("custom0 %[csr_value], %[rs1], 0, 2"
                : [csr_value] "=r" (csr_value)
                : [rs1] "r" (csr));
  return csr_value;
}
