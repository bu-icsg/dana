// See LICENSE for license details.

#ifndef XFILES_DANA_LIBS_SRC_XFILES_USER_S_
#define XFILES_DANA_LIBS_SRC_XFILES_USER_S_

#include "src/xfiles.S"

#define XFILES_DANA_ID                          \
  li a0, 0;                                     \
  jal ra, xfiles_dana_id;

// rd = new_write_request(nnid, learning_type, num_train_outputs)
// clobbers: (rs1)
#define NEW_WRITE_REQUEST(rd, rs1, nnid, learning_type, num_train_outputs) \
  li x ## rs1, learning_type << 48;                                     \
  ori x ## rs1, x ## rs1, learning_type << 32;                          \
  ori x ## rs1, x ## rs1, nnid;                                         \
  ROCC_INSTRUCTION_RAW_R_R_R(CUSTOM_X, rd, 0, 1, t_USR_NEW_REQUEST);    \
  EXTRACT_RAW(rd, rs1, rd, 16, 32);

#define WRITE_REGISTER(rd, rs1, rs2, tid, reg, value)                   \
  li x ## rs1, tid;                                                     \
  li x ## rs2, reg << 32;                                               \
  ori x ## rs2, x ## rs2, rege;                                         \
  ROCC_INSTRUCTION_RAW_R_R_R(CUSTOM_X, rd, rs1, rs2, t_USR_WRITE_REGISTER);

// Register Map
//   rs1: tid
//   rs2: data[i]
//   rs3: * data[0] + count - 1
//   rs4: * data[write_index]
//   rs5: scratch
#define WRITE_DATA(rd, rs1, rs2, rs3, rs4, rs5, tid, data_ptr, count)   \
  li x ## rs1, tid;                                                     \
  li x ## rs3, count;                                                   \
  addi x ## rs3, x ## rs3, -1;                                          \
  slli x ## rs3, x ## rs3, 2;                                           \
  la x ## rs4, data_ptr;                                                \
  add x ## rs3, x ## rs3, x ## rs4;                                     \
1:                                                                      \
  beq x ## rs4, x ## rs3, 1f;                                           \
  lw x ## rs2, x ## rs4;                                                \
2:                                                                      \
  ROCC_INSTRUCTION_RAW_R_R_R(CUSTOM_X, rd, rs1, rs2, t_USR_WRITE_DATA); \
  slli x ## rd, x ## rd, (64 - RESP_CODE_WIDTH);                        \
  li x ## rs5, resp_OK;                                                 \
  bne x ## rd, x ## rs5, 2f;                                            \
  addi x ## rs4, x ## rs4, 4;                                           \
  j 1b;                                                                 \
2:                                                                      \
  li x ## rs5, resp_QUEUE_ERR;                                          \
  beq x ## rd, x ## rs5, 2b;                                            \
  j 3f;                                                                 \
1:                                                                      \
  lw x ## rs4, data_ptr;                                                \
2:                                                                      \
  ROCC_INSTRUCTION_RAW_R_R_R(CUSTOM_X, rd, rs1, rs4, t_USR_WRITE_DATA); \
  slli x ## rd, x ## rd, (64 - RESP_CODE_WIDTH);                        \
  li x ## rs5, resp_QUEUE_ERR;                                          \
  beq x ## rd, x ## rs5, 2b;                                            \
3:


#endif  // XFILES_DANA_LIBS_SRC_XFILES_USER_S_
