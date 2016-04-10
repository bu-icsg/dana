// See LICENSE for license details.

#ifndef SRC_MAIN_C_XFILES_USER_H_
#define SRC_MAIN_C_XFILES_USER_H_

#include "src/main/c/xfiles.h"

// Temporarily include supervisor data structures to support proxy
// kernel systemcalls
#include "src/main/c/xfiles-supervisor.h"

//-------------------------------------- Userland

// Request information about the specific X-FILES/DANA configuration
// and return it in an XLen sized packed representation. Optionally,
// this will print the output directly to stdout.
xlen_t xfiles_dana_id(int flag_print);

// Initiate a new Transaction for a specific NNID. The X-Files Arbiter
// will then assign and return a TID necessary for other userland
// functions. The second parameter, "num_train_outputs", when set to
// zero indicates that this is a feedforward computation. If non-zero,
// this is a learning request.
tid_type new_write_request(nnid_type nnid, learning_type_t learning_type,
                           element_type num_train_outputs);

// Function to write a specific register inside of the X-Files
// Arbiter. The value is passed as a 32-bit unsigned, but only the
// LSBs will be used if the destination register has fewer than 32
// bits.
xlen_t write_register(tid_type tid, xfiles_reg reg, uint32_t value);

// Write the contents of an input array of some size to the X-Files
// Arbiter. After completing this function, the transaction is deemed
// valid and will start executing on Dana.
xlen_t write_data(tid_type tid,
                  element_type * input_data_array,
                  size_t count);

// A special write data request used for incremental training. Here,
// an input and an expected output vector are passed. The
// configuration cache is updated inside the Configuration Cache.
xlen_t write_data_train_incremental(tid_type tid,
                                    element_type * input_data_array,
                                    element_type * output_data_array,
                                    size_t count_input,
                                    size_t count_output);

// Read all the output data for a specific transaction. This throws
// the CPU into a spinlock repeatedly checking the validity of the
// X-Files response.
uint64_t read_data_spinlock(tid_type tid,
                            element_type * output_data_array,
                            size_t count);

// Forcibly kill a running transaction
xlen_t kill_transaction(tid_type tid);

//-------------------------------------- Userland Proxy Kernel Syscalls

// Set the ASID to a new value
xlen_t pk_syscall_set_asid(asid_type asid);

// Set the ASID--NNID Table Poitner (ANTP)
xlen_t pk_syscall_set_antp(asid_nnid_table * os_antp);

// Print a visual organization of a specific ASID--NNIT Table
void asid_nnid_table_info(asid_nnid_table * table);

// Constructor and destructor for the ASID--NNID Table data structure
void asid_nnid_table_create(asid_nnid_table ** table, size_t num_asids,
                            size_t nn_configurations_per_asid);
void asid_nnid_table_destroy(asid_nnid_table **);

// Constructor and destructor for the Queue structure
void construct_queue(queue **, int);
void destroy_queue(queue **);

// Append the NN configuration contained in a binary file to the ASID
// of the specified ASID--NNID table. **NOTE** This is currently
// unsupported with the proxy kernel as it doesn't supported file
// operation system calls.
int attach_nn_configuration(asid_nnid_table ** table, asid_type asid,
                            const char * nn_configuration_binary_file);

// Attach an NN configuration that points to NULL. This is useful for
// testing purposes to place a specific NN configuration in a specific
// location and generate traps that will cause us to fail fast on an
// invalid read.
int attach_garbage(asid_nnid_table ** table, asid_type asid);

// Append the NN configuration contained in an XLen-sized (64-bit or
// 32-bit depending on RISC-V architecture) array and of a certain
// size to the ASID of a specific ASID--NNID Table.
int attach_nn_configuration_array(asid_nnid_table ** table, uint16_t asid,
                                  const xlen_t * nn_configuration_array,
                                  size_t size);

#endif  // SRC_MAIN_C_XFILES_USER_H_
