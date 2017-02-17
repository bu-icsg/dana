// See LICENSE for license details.

#ifndef XFILES_DANA_LIBS_SRC_XFILES_USER_H_
#define XFILES_DANA_LIBS_SRC_XFILES_USER_H_

#include <stdio.h>
#include "src/include/xfiles.h"

//-------------------------------------- Userland

// Request information about the specific X-FILES/DANA configuration
// and return it in an XLen sized packed representation.
xlen_t xfiles_dana_id();

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

// Writes an input array to the X-Files Arbiter, but does not write
// the last array element. This, coupled with `write_data_last` can be
// used to start transactions nearly simultaneously.
xlen_t write_data_except_last(tid_type tid,
                              element_type * input_data_array,
                              size_t count);

// Writes the last element of an input array to the X-Files Arbiter.
// This will implicitly start a transaction.
xlen_t write_data_last(tid_type tid,
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

// Run feedforward inference on one input--output pair
xlen_t transaction_feedforward(nnid_type nnid,
                               element_type * addr_i,
                               element_type * addr_o,
                               int num_inputs,
                               int num_outputs);

// Run over an input--output dataset for a given NNID, returning the
// number of differences with the expected output
xlen_t xfiles_fann_run_compare(nnid_type nnid,
                               element_type * addr_i,
                               element_type * addr_o,
                               element_type * addr_e,
                               int num_inputs,
                               int num_outputs,
                               int num_data);

#endif  // XFILES_DANA_LIBS_SRC_XFILES_USER_H_
