# See LICENSE.IBM for license details.

#include "riscv_test.h"
#include "../riscv-tools/riscv-tests/isa/macros/scalar/test_macros.h"
#include "tests/rocc-software/src/xcustom.h"
#include "tests/rocc-software/src/riscv_test_rocc.h"
#include "tests/libs/src/xfiles-supervisor.S"
#include "tests/libs/src/xfiles-user.S"

#define CUSTOM_X 0

#define STACK_TOP (_end + 4096)

#undef EXTRA_INIT
#define EXTRA_INIT                              \
  SET_ASID(1);                                  \
  SET_ANTP(antp_dana, antp_os);                 \
  la sp, _end + 1024;

RVTEST_WITH_ROCC

start:

RVTEST_CODE_BEGIN

  FANN_TEST_INFER_CC(0, NUM_INPUTS, NUM_OUTPUTS, data_in, data_out)

  TEST_PASSFAIL

RVTEST_CODE_END
