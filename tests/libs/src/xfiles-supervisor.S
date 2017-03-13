// See LICENSE.IBM for license details.

#ifndef XFILES_DANA_LIBS_SRC_XFILES_SUPERVISOR_S_
#define XFILES_DANA_LIBS_SRC_XFILES_SUPERVISOR_S_

#define t_SUP_UPDATE_ASID 0
#define t_SUP_WRITE_REG 1
#define t_SUP_READ_CSR 2
#define t_SUP_WRITE_CSR 3

#define XF_READ_CSR(csr)                        \
  li a0, csr;                                   \
  jal xf_read_csr;

#define XF_WRITE_CSR_A(csr, addr)               \
  li a0, csr;                                   \
  la a1, addr;                                  \
  jal xf_write_csr;

#define XF_WRITE_CSR_I(csr, imm)                \
  li a0, csr;                                   \
  li a1, imm;                                   \
  jal xf_write_csr;

#define XF_WRITE_CSR_R(csr, reg)                \
  li a0, csr;                                   \
  lw a1, reg;                                   \
  jal xf_write_csr;

#define XF_WRITE_CSR(csr, val) XF_WRITE_CSR_I(csr, val);

#define SET_ASID(asid)                          \
  XF_WRITE_CSR_I(csr_ASID, asid);               \
  XF_WRITE_CSR_I(csr_TID, 0);
#define SET_ANTP(antp, size)                    \
  XF_WRITE_CSR_A(csr_ANTP, antp);               \
  XF_WRITE_CSR_R(csr_NUM_ASIDS, size);

// #define XF_READ_CSR(csr)

#endif  // XFILES_DANA_LIBS_SRC_XFILES_SUPERVISOR_S_
