# See LICENSE.IBM for license details.

#*****************************************************************************
# debug.S
#-----------------------------------------------------------------------------
#
# Exercise the debug unit
#

#include "riscv_test.h"
#include "../riscv-tools/riscv-tests/isa/macros/scalar/test_macros.h"
#include "tests/rocc-software/src/xcustom.h"
#include "tests/rocc-software/src/riscv_test_rocc.h"
#include "tests/libs/src/xfiles-debug.S"


#define CUSTOM_X 0

RVTEST_WITH_ROCC

RVTEST_CODE_BEGIN
  TEST_CASE( 1, x3, 0xaaaa, DEBUG_ECHO_VIA_REG(0xaaaa) );
  TEST_CASE( 2, x3, 0x0,    DEBUG_WRITE_MEM(0xbbbb, tdat2) );
  TEST_CASE( 3, x3, 0xbbbb, DEBUG_READ_MEM(tdat3) );
  TEST_CASE( 4, x3, 0x0,    DEBUG_WRITE_UTL(0xcccc, tdat4) );
  TEST_CASE( 5, x3, 0xcccc, DEBUG_READ_UTL(tdat5) );

  TEST_PASSFAIL

RVTEST_CODE_END

  .data
RVTEST_DATA_BEGIN

  TEST_DATA

tdat:
tdat2:
tdat3:  .dword 0x0
tdat4:
tdat5:  .dword 0x0

RVTEST_DATA_END
