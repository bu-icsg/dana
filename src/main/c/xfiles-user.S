// See LICENSE for license details.

#ifndef SRC_MAIN_C_XFILES_USERS_S_
#define SRC_MAIN_C_XFILES_USERS_S_

#include "src/main/c/xfiles.S"

#define XFILES_DANA_ID(rd)                                              \
  ROCC_INSTRUCTION_RAW_R_R_R(CUSTOM_X, rd, 0, 0, t_USR_XFILES_DANA_ID);

#endif  // SRC_MAIN_C_XFILES_USERS_S_
