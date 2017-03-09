// See LICENSE.IBM for license details.

#include "src/include/xfiles-debug.h"
#include <stdio.h>

xlen_t debug_test(unsigned action, uint32_t data, void * addr) {
  xlen_t out, action_and_data = ((uint64_t)action << 32) | (uint32_t)data;
  XFILES_INSTRUCTION(out, action_and_data, addr, t_USR_XFILES_DEBUG);
  return out;
}

xlen_t debug_echo_via_reg(uint32_t data) {
  return debug_test(a_REG, data, 0);
}

xlen_t debug_read_mem(void * addr) {
  return debug_test(a_MEM_READ, 0, addr);
}

xlen_t debug_write_mem(uint32_t data, void * addr) {
  return debug_test(a_MEM_WRITE, data, addr);
}

void * debug_virt_to_phys(void * addr_v) {
  return (void *) debug_test(a_VIRT_TO_PHYS, 0, addr_v);
}

xlen_t debug_read_utl(void * addr) {
  return debug_test(a_UTL_READ, 0, addr);
}

xlen_t debug_write_utl(uint32_t data, void * addr) {
  return debug_test(a_UTL_WRITE, data, addr);
}
