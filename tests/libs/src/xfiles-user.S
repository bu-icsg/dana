// See LICENSE.IBM for license details.

#ifndef XFILES_DANA_LIBS_SRC_XFILES_USER_S_
#define XFILES_DANA_LIBS_SRC_XFILES_USER_S_

#include "tests/libs/src/xfiles.S"

#define XFILES_DANA_ID                          \
  li a0, CSRs_xfid_current;                     \
  jal ra, xf_read_csr;

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
  mv a0, tid;                                    \
  mv a1, input_data_array;                       \
  li a2, count;                                  \
  jal write_data;                                \

#define READ_DATA_SPINLOCK(tid, data, count) \
  mv a0, tid;                                \
  mv a1, data;                               \
  li a2, count;                              \
  jal read_data_spinlock;

// a0: number of failing comparisons
// a1: exit condition (a + size)
// a2: a + i
// a3: b + i
// a4: [a + i]
// a5: [b + i]
#define COMPARE(a, b, size)                     \
  xor a0, a0, a0;                               \
  mv a2, a;                                     \
  li a1, size;                                  \
  slli a1, a1, 2;                               \
  add a1, a1, a2;                               \
  mv a3, b;                                     \
2:                                              \
  lw a4, 0(a2);                                 \
  lw a5, 0(a3);                                 \
  addi a2, a2, 4;                               \
  addi a3, a3, 4;                               \
  beq a4, a5, 1f;                               \
  addi a0, a0, 1;                               \
1:                                              \
  bne a1, a2, 2b;

#define TRANSACTION_FF(nnid, learning_type, num_train_outputs, addr_i, addr_o, addr_e) \
  NEW_WRITE_REQUEST(nnid, 0, 0);                                       \
  addi sp, sp, -16;                                                    \
  sw a0, 12(sp);                                                       \
  WRITE_DATA(12(sp), addr_i, 2);                                       \
  READ_DATA_SPINLOCK(12(sp), addr_o, 1);                               \
  COMPARE(addr_o, addr_e, 1);

#define FANN_TEST(nnid, learning_type, num_input, num_output, num_data, addr_i, addr_o, addr_e) \
  li TESTNUM, 0;                   \
  la x27, addr_i;                  \
  la x26, addr_o;                  \
  la x25, addr_e;                  \
  li x24, num_data;                \
fann_test_top:                     \
  addi TESTNUM, TESTNUM, 1;        \
  NEW_WRITE_REQUEST(nnid, 0, 0);   \
  mv x23, a0;                      \
  WRITE_DATA(x23, x27, 2);         \
  READ_DATA_SPINLOCK(x23, x26, 1); \
  COMPARE(x26, x25, num_output);   \
  bne a0, x0, fail;                \
  li x23, num_input;               \
  slli x23, x23, 2;                \
  add x27, x27, x23;               \
  li x23, num_output;              \
  slli x23, x23, 2;                \
  add x26, x26, x23;               \
  add x25, x25, x23;               \
  blt TESTNUM, x24, fann_test_top;

#define FANN_TEST_LEARN(nnid, num_input, num_output, num_data, addr_i, addr_e, addr_o) \
  li a0, nnid;                  \
  la a1, addr_i;                \
  la a2, addr_e;                \
  la a3, addr_o;                \
  li a4, num_input;             \
  li a5, num_output;            \
  li a6, num_data;              \
  jal xfiles_fann_learn;

#define FANN_TEST_NO_COMPARE_CC(nnid, num_input, num_output, num_data, addr_i, addr_o) \
  li TESTNUM, 0;               \
  li a0, nnid;                 \
  la a1, addr_i;               \
  la a2, addr_o;               \
  li a3, num_input;            \
  li a4, num_output;           \
  li a5, num_data;             \
  jal xfiles_fann_run_no_compare; \
  beq a0, x0, pass;            \
  srli TESTNUM, a0, 32;        \
  j fail;

#define FANN_TEST_CC(nnid, num_input, num_output, num_data, addr_i, addr_o, addr_e) \
  li TESTNUM, 0;               \
  li a0, nnid;                 \
  la a1, addr_i;               \
  la a2, addr_o;               \
  la a3, addr_e;               \
  li a4, num_input;            \
  li a5, num_output;           \
  li a6, num_data;             \
  jal xfiles_fann_run_compare; \
  beq a0, x0, pass;            \
  srli TESTNUM, a0, 32;        \
  j fail;

#define FANN_TEST_SMP_NO_COMPARE_CC(nnid, num_input, num_output, num_data, addr_i, addr_o) \
  li TESTNUM, 0;               \
  li a0, nnid;                 \
  la a1, addr_i;               \
  la a2, addr_o;               \
  li a3, num_input;            \
  li a4, num_output;           \
  li a5, num_data;             \
  jal xfiles_fann_run_smp_no_compare; \
  beq a0, x0, pass;            \
  srli TESTNUM, a0, 32;        \
  j fail;

#define FANN_TEST_SMP_CC(nnid, num_input, num_output, num_data, addr_i, addr_o, addr_e) \
  li TESTNUM, 0;               \
  li a0, nnid;                 \
  la a1, addr_i;               \
  la a2, addr_o;               \
  la a3, addr_e;               \
  li a4, num_input;            \
  li a5, num_output;           \
  li a6, num_data;             \
  jal xfiles_fann_run_smp_compare; \
  beq a0, x0, pass;            \
  srli TESTNUM, a0, 32;        \
  j fail;

#endif  // XFILES_DANA_LIBS_SRC_XFILES_USER_S_
