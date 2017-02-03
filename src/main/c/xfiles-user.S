// See LICENSE for license details.

#ifndef SRC_MAIN_C_XFILES_USER_S_
#define SRC_MAIN_C_XFILES_USER_S_

#include "src/main/c/xfiles.S"

#define XFILES_DANA_ID(rd)                                              \
  ROCC_INSTRUCTION_RAW_R_R_R(CUSTOM_X, rd, 0, 0, t_USR_XFILES_DANA_ID);

#define NEW_WRITE_REQUEST(rd, nnid, learning_type, num_train_outputs)   \
  li x1, learning_type << 48;                                           \
  ori x1, x1, learning_type << 32;                                      \
  ori x1, x1, nnid;                                                     \
  ROCC_INSTRUCTION_RAW_R_R_R(CUSTOM_X, 2, 0, 1, t_USR_NEW_REQUEST);     \
  srli x ## rd, x2,

#endif  // SRC_MAIN_C_XFILES_USER_S_
