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

#endif  // SRC_MAIN_C_XFILES_DEBUG_S_
