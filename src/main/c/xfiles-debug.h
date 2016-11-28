// See LICENSE for license details.

#ifndef SRC_MAIN_C_XFILES_DEBUG_H_
#define SRC_MAIN_C_XFILES_DEBUG_H_

#include "src/main/c/xfiles.h"
#include "src/main/c/xfiles-debug.S"

//-------------------------------------- Interactions with the Debug Unit

// Function that accesses the per-core Debug Unit. This can be used
// manually or the functions below act as aliases to this function.
xlen_t debug_test(unsigned action, uint32_t data, void * addr);

// Write data to the accelerator and have the accelerator return it:
//   data = data
xlen_t debug_echo_via_reg(uint32_t data);

// Read from a specific address using the L1 port:
//   data = [addr]
xlen_t debug_read_mem(void * addr);

// Write to a specific address using the L1 port:
//   [addr] = data
xlen_t debug_write_mem(uint32_t data, void * addr);

// Do virtual to physical address translation:
//   addr_phys = virt_to_phys(addr_virt)
void * debug_virt_to_phys(void * addr_v);

// Read a specific memory address using the L2 uncached tilelink port:
//   data = [addr]
xlen_t debug_read_utl(void * addr);

// Write to a specific memory address using the L2 uncached tilelink port:
//   [addr] = data
xlen_t debug_write_utl(uint32_t data, void * addr);

#endif  // SRC_MAIN_C_XFILES_DEBUG_H_
