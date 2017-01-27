# See LICENSE.ibm for license details.

#*****************************************************************************
# feedforward.S
#-----------------------------------------------------------------------------
#
# Generic feedforward transaction
#

#include "riscv_test.h"
#include "include/test_macros.h"
#include "include/xfiles-dana.h"
#include "rocc-software/src/xcustom.h"
#include "rocc-software/src/riscv_test_rocc.h"
#include "src/main/c/xfiles-user.S"

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
  TEST_CASE( 1, x3, ID_STRING, XFILES_DANA_ID(3) )

  TEST_PASSFAIL

RVTEST_CODE_END

  .data
RVTEST_DATA_BEGIN

  TEST_DATA

RVTEST_DATA_END
