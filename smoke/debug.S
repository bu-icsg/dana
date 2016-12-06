# See LICENSE.ibm for license details.

#*****************************************************************************
# debug.S
#-----------------------------------------------------------------------------
#
# Exercise the debug unit
#

#include "riscv_test.h"
#include "include/test_macros.h"
#include "include/xfiles-dana.h"
#include "rocc-software/src/xcustom.h"
#include "rocc-software/src/riscv_test_rocc.h"
#include "src/main/c/xfiles-debug.S"

#define CUSTOM_X 0
#define DEBUG_TEST(action, data, addr, rd, rs1, rs2) \
  li x ## rs1, action; \
  slli x ## rs1, x ## rs1, 32; \
  li x ## rs2, data; \
  or x ## rs1, x ## rs1, x ## rs2; \
  la x ## rs2, addr; \
  ROCC_INSTRUCTION_RAW_R_R_R(CUSTOM_X, rd, rs1, rs2, t_USR_XFILES_DEBUG);

#define DEBUG_ECHO_VIA_REG(data) DEBUG_TEST(a_REG, data, tdat, 3, 1, 2)
#define DEBUG_READ_MEM(addr) DEBUG_TEST(a_MEM_READ, 0, addr, 3, 1, 2);
#define DEBUG_WRITE_MEM(data, addr) DEBUG_TEST(a_MEM_WRITE, data, addr, 3, 1, 2);
#define DEBUG_VIRT_TO_PHYS(vaddr, paddr) DEBUG_TEST(a_VIRT_TO_PHYS, 0, vaddr, paddr, 1, 2);
#define DEBUG_READ_UTL(addr) DEBUG_TEST(a_UTL_READ, 0, addr, 3, 1, 2);
#define DEBUG_WRITE_UTL(data, addr) DEBUG_TEST(a_UTL_WRITE, data, addr, 3, 1, 2);

RVTEST_WITH_ROCC

RVTEST_CODE_BEGIN
  TEST_CASE( 1, x3, 0xaaaa, DEBUG_ECHO_VIA_REG(0xaaaa) )
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
