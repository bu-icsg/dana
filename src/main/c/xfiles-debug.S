// See LICENSE for license details.

#ifndef SRC_MAIN_C_XFILES_DEBUG_S_
#define SRC_MAIN_C_XFILES_DEBUG_S_

#include "src/main/c/xfiles.S"

// Enumerated type that defines the action taken by the Debug Unit
#define a_REG 0          // Return a value written using the cmd interface
#define a_MEM_READ 1     // Read data from the L1 cache and return it
#define a_MEM_WRITE 2    // Write data to the L1 cache
#define a_VIRT_TO_PHYS 3 // Do address translation via the PTW port
#define a_UTL_READ 4     // Read data from the L2 cache and return it
#define a_UTL_WRITE 5    // Write data to the L2 cache

#define DEBUG_TEST(action, data, addr, rd, rs1, rs2)                    \
  li x ## rs1, action;                                                  \
  slli x ## rs1, x ## rs1, 32;                                          \
  li x ## rs2, data;                                                    \
  or x ## rs1, x ## rs1, x ## rs2;                                      \
  la x ## rs2, addr;                                                    \
  ROCC_INSTRUCTION_RAW_R_R_R(CUSTOM_X, rd, rs1, rs2, t_USR_XFILES_DEBUG);

#define DEBUG_ECHO_VIA_REG(data) DEBUG_TEST(a_REG, data, tdat, 3, 1, 2)
#define DEBUG_READ_MEM(addr) DEBUG_TEST(a_MEM_READ, 0, addr, 3, 1, 2);
#define DEBUG_WRITE_MEM(data, addr) DEBUG_TEST(a_MEM_WRITE, data, addr, 3, 1, 2);
#define DEBUG_VIRT_TO_PHYS(vaddr, paddr) DEBUG_TEST(a_VIRT_TO_PHYS, 0, vaddr, paddr, 1, 2);
#define DEBUG_READ_UTL(addr) DEBUG_TEST(a_UTL_READ, 0, addr, 3, 1, 2);
#define DEBUG_WRITE_UTL(data, addr) DEBUG_TEST(a_UTL_WRITE, data, addr, 3, 1, 2);

#endif  // SRC_MAIN_C_XFILES_DEBUG_S_
