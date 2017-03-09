// See LICENSE.IBM for license details.

#ifndef XFILES_DANA_LIBS_SRC_XFILES_ASID_NNID_TABLE_H_
#define XFILES_DANA_LIBS_SRC_XFILES_ASID_NNID_TABLE_H_

#include "src/include/xfiles-supervisor-types.h"
#ifndef NO_VM
#include "src/include/xfiles-debug.h"
#endif

// Print a visual organization of a specific ASID--NNIT Table
void asid_nnid_table_info(ant * table);

// Constructor and destructor for the ASID--NNID Table data structure
void asid_nnid_table_create(ant ** table, size_t num_asids,
                            size_t nn_configurations_per_asid);
void asid_nnid_table_destroy(ant **);

// Constructor and destructor for the Queue structure
void construct_queue(queue **, int);
void destroy_queue(queue **);

// Append the NN configuration contained in a binary file to the ASID
// of the specified ASID--NNID table. **NOTE** This is currently
// unsupported with the proxy kernel as it doesn't supported file
// operation system calls.
int attach_nn_configuration(ant ** table, asid_type asid,
                            const char * nn_configuration_binary_file);

// Attach an NN configuration that points to NULL. This is useful for
// testing purposes to place a specific NN configuration in a specific
// location and generate traps that will cause us to fail fast on an
// invalid read.
int attach_garbage(ant ** table, asid_type asid);

// Append the NN configuration contained in an XLen-sized (64-bit or
// 32-bit depending on RISC-V architecture) array and of a certain
// size to the ASID of a specific ASID--NNID Table.
int attach_nn_configuration_array(ant ** table, uint16_t asid,
                                  const xlen_t * nn_configuration_array,
                                  size_t size);

// Bytes of data per beat of Tilelink L2 response. This is the value
// of tlDataBeats in uncore/src/main/scala/tilelink.scala.
#define TILELINK_BYTES_PER_BEAT 16
#define TILELINK_LG_BYTES_PER_BEAT 4
#define TILELINK_L2_BYTES 128
#define TILELINK_L2_ADDR_BITS 7
// Do an allocation that is aligned on an L2 cache line
int alloc_config_aligned(xlen_t ** raw, xlen_t ** aligned, size_t size);

#endif  // XFILES_DANA_LIBS_SRC_XFILES_ASID_NNID_TABLE_H_
