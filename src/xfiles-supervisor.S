// See LICENSE for license details.

#ifndef XFILES_DANA_LIBS_SRC_XFILES_SUPERVISOR_S_
#define XFILES_DANA_LIBS_SRC_XFILES_SUPERVISOR_S_

#define t_SUP_UPDATE_ASID 0
#define t_SUP_WRITE_REG 1
#define t_SUP_READ_CSR 2

#define SET_ASID(asid)                          \
  li a0, asid;                                  \
  jal set_asid;

#define SET_ANTP(antp, size)                    \
  la a0, antp;                                  \
  lw a1, size;                                  \
  jal set_antp;

// #define XF_READ_CSR(csr)

#endif  // XFILES_DANA_LIBS_SRC_XFILES_SUPERVISOR_S_
