// See LICENSE for license details.

#ifndef XFILES_DANA_LIBS_SRC_XFILES_USER_S_
#define XFILES_DANA_LIBS_SRC_XFILES_USER_S_

#include "src/xfiles.S"

#define XFILES_DANA_ID                          \
  jal ra, xfiles_dana_id;

#define NEW_WRITE_REQUEST(nnid, learning_type, num_train_outputs)       \
  li a0, nnid;                                                          \
  li a1, learning_type;                                                 \
  li a2, num_train_outputs;                                             \
  jal new_write_request;

#define WRITE_REGISTER(tid, reg, value)         \
  li a0, tid;                                   \
  li a1, reg;                                   \
  li a2, value;                                 \
  jal write_register;

#define WRITE_DATA(tid, input_data_array, count) \
  li a0, tid;                                    \
  la a1, input_data_array;                       \
  li a2, count;                                  \
  jal write_data;                                \

#define READ_DATA_SPINLOCK(tid, data, count) \
  li a0, tid;                                \
  la a1, data;                               \
  li a2, count;                              \
  jal read_data_spinlock;

#endif  // XFILES_DANA_LIBS_SRC_XFILES_USER_S_
