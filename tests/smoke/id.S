# See LICENSE.IBM for license details.

#*****************************************************************************
# id.S
#-----------------------------------------------------------------------------
#
# Check that the ID matches what we expect
#

#include "riscv_test.h"
#include "../riscv-tools/riscv-tests/isa/macros/scalar/test_macros.h"
#include "tests/rocc-software/src/xcustom.h"
#include "tests/rocc-software/src/riscv_test_rocc.h"
#include "tests/libs/src/xfiles-user.S"

#define CUSTOM_X 0

// ID string parameters
#ifndef ID_STRING
#define ID_STRING 0x0001000000001012
// * Transaction Table Entries: 1 [63:48]
// * Elements Per Block:        4 [13:10]
// * Processing Elements:       1 [ 9: 4]
// * Cache Entries:             2 [ 3: 0]
#endif

RVTEST_WITH_ROCC

RVTEST_CODE_BEGIN
  TEST_CASE( 1, a0, ID_STRING, XFILES_DANA_ID )

  TEST_PASSFAIL

RVTEST_CODE_END

  .data
RVTEST_DATA_BEGIN

  TEST_DATA

RVTEST_DATA_END
