// See LICENSE for license details.

#ifndef SRC_MAIN_C_XFILES_DEBUG_H_
#define SRC_MAIN_C_XFILES_DEBUG_H_

#include "src/main/c/xfiles.h"

//-------------------------------------- Interactions with the Debug Unit

// Enumerated type that defines the action taken by the Debug Unit
typedef enum {
  a_REG,          // Return a value written using the cmd interface
  a_MEM_READ,     // Read data from the L1 cache and return it
  a_MEM_WRITE,    // Write data to the L1 cache
  a_VIRT_TO_PHYS, // Do address translation via the PTW port
  a_UTL_READ,     // Read data from the L2 cache and return it
  a_UTL_WRITE     // Write data to the L2 cache
} xfiles_debug_action_t;

// Function that accesses the per-core Debug Unit. This can be used
// manually or the functions below act as aliases to this function.
xlen_t debug_test(xfiles_debug_action_t action, uint32_t data, void * addr);

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
xlen_t debug_virt_to_phys(void * addr_v);

// Read a specific memory address using the L2 uncached tilelink port:
//   data = [addr]
xlen_t debug_read_utl(void * addr);

// Write to a specific memory address using the L2 uncached tilelink port:
//   [addr] = data
xlen_t debug_write_utl(uint32_t data, void * addr);

#endif  // SRC_MAIN_C_XFILES_DEBUG_H_
