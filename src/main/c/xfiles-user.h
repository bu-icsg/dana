// See LICENSE for license details.

#ifndef SRC_MAIN_C_XFILES_H
#define SRC_MAIN_C_XFILES_H

#include <stdio.h>
#include "src/main/c/xfiles.h"

typedef enum {
  xfiles_reg_batch_items = 0,
  xfiles_reg_learning_rate,
  xfiles_reg_weight_decay_lambda
} xfiles_reg;

typedef enum {
  READ_DATA = 0,
  WRITE_DATA = 1,
  NEW_REQUEST = 3,
  WRITE_DATA_LAST = 5,
  WRITE_REGISTER = 7,
  XFILES_DANA_ID = 16
} request_t;

typedef enum {
  FEEDFORWARD = 0,
  TRAIN_INCREMENTAL = 1,
  TRAIN_BATCH = 2
} learning_t_t;

// Request information about the specific X-FILES/DANA configuration
// and return it in an XLen sized packed representation. Optionally,
// this will print the output directly to stdout.
x_len xfiles_dana_id(int flag_print);

// Initiate a new Transaction for a specific NNID. The X-Files Arbiter
// will then assign and return a TID necessary for other userland
// functions. The second parameter, "num_train_outputs", when set to
// zero indicates that this is a feedforward computation. If non-zero,
// this is a learning request.
tid_t new_write_request(nnid_t nnid, learning_t_t learning_t,
                        element_t num_train_outputs);

// Function to write a specific register inside of the X-Files
// Arbiter. The value is passed as a 32-bit unsigned, but only the
// LSBs will be used if the destination register has fewer than 32
// bits.
void write_register(tid_t tid, xfiles_reg reg, uint32_t value);

// Write the contents of an input array of some size to the X-Files
// Arbiter. After completing this function, the transaction is deemed
// valid and will start executing on Dana.
void write_data(tid_t tid,
                element_t * input_data_array,
                size_t count);

// A special write data request used for incremental training. Here,
// an input and an expected output vector are passed. The
// configuration cache is updated inside the Configuration Cache.
void write_data_train_incremental(tid_t tid,
                                  element_t * input_data_array,
                                  element_t * output_data_array,
                                  size_t count_input,
                                  size_t count_output);

// Read all the output data for a specific transaction. This throws
// the CPU into a spinlock repeatedly checking the validity of the
// X-Files response.
uint64_t read_data_spinlock(tid_t tid,
                            element_t * output_data_array,
                            size_t count);

//-------------------------------------- Proxy Kernel Systemcalls
uint64_t syscall_set_asid(asid_t asid);
nnid_t syscall_attach_nn_configuration(asid_t asid,
                                       const char * file_name);

#endif  // SRC_MAIN_C_XFILES_H
