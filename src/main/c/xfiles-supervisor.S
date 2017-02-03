// See LICENSE for license details.

#ifndef SRC_MAIN_C_XFILES_SUPERVISOR_S_
#define SRC_MAIN_C_XFILES_SUPERVISOR_S_

#include "src/main/c/xfiles.S"

#define t_SUP_UPDATE_ASID 0
#define t_SUP_WRITE_REG 1
#define t_SUP_READ_CSR 2

#define SET_ASID(rd, rs1, asid)                                         \
  li x ## rs1, asid;                                                    \
  ROCC_INSTRUCTION_RAW_R_R_R(CUSTOM_X, rd, rs1, 0, t_SUP_UPDATE_ASID);

#define SET_ANTP(rd, rs1, rs2, antp)                                    \
  la x ## rs1, antp;                                                    \
  lw x ## rs2, antp;                                                    \
  ROCC_INSTRUCTION_RAW_R_R_R(CUSTOM_X, rd, rs1, rs2, t_SUP_WRITE_REG);

#define XF_READ_CSR(csr)

#endif  // SRC_MAIN_C_XFILES_SUPERVISOR_S_
