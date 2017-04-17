# See LICENSE.IBM for license details.

#*****************************************************************************
# xorSigmoidSymmetric.S
#-----------------------------------------------------------------------------
#
# Single transaction test of xorSigmoidSymmetric
#

#include "riscv_test.h"
#include "../riscv-tools/riscv-tests/isa/macros/scalar/test_macros.h"
#include "tests/rocc-software/src/xcustom.h"
#include "tests/rocc-software/src/riscv_test_rocc.h"
#include "tests/libs/src/xfiles-supervisor.S"
#include "tests/libs/src/xfiles-user.S"

#define CUSTOM_X 0

#define STACK_TOP (_end + 4096)

// RVTEST_CODE_BEGIN includes the EXTRA_INIT macro before its final
// `mret` and the resulting drop to user mode. We use this to setup
// the ASID and ANTP for a single transaction test.
#undef EXTRA_INIT
#define EXTRA_INIT                              \
  SET_ASID(1);                                  \
  SET_ANTP(antp_dana, antp_os);                 \
  la sp, _end + 1024;

RVTEST_WITH_ROCC

start:

RVTEST_CODE_BEGIN

  // Decrease the learning rate to 0.125
  XF_WRITE_CSR(CSRs_learn_rate,  0b00000000000000000000100000000000);
  FANN_TEST_LEARN(0, NUM_INPUTS, NUM_OUTPUTS, NUM_DATAPOINTS, data_in, data_expected, data_out);
  DANA_FENCE(0)
  FANN_TEST_NO_COMPARE_CC(0, NUM_INPUTS, NUM_OUTPUTS, NUM_DATAPOINTS, data_in, data_out);

  TEST_PASSFAIL

RVTEST_CODE_END
