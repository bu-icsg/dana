// See LICENSE for license details.

#ifndef __XFILES_H__
#define __XFILES_H__

#include <stdint.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>

// [TODO] Any changes to these types need to occur in conjunction with
// the Chisel code and with the TID extraction part of
// new_write_request.
typedef uint32_t nnid_type;
typedef uint16_t asid_type;
typedef uint16_t tid_type;
typedef int32_t element_type;
typedef uint64_t xlen_t;

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
  UPDATE_ASID = 0,
  UPDATE_ANTP = 1
} request_super_t;

typedef enum {
  FEEDFORWARD = 0,
  TRAIN_INCREMENTAL = 1,
  TRAIN_BATCH = 2
} learning_type_t;

typedef enum {
  err_XFILES_BADREQ = 1,
  err_XFILES_NOASID
} xfiles_err_t;

typedef enum {
  err_UNKNOWN     = 0,
  err_DANA_NOANTP = 1,
  err_INVASID     = 2,
  err_INVNNID     = 3,
  err_ZEROSIZE    = 4,
  err_INVEPB      = 5
} dana_err_t;

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
void write_register(tid_type tid, xfiles_reg reg, uint32_t value);

// Write the contents of an input array of some size to the X-Files
// Arbiter. After completing this function, the transaction is deemed
// valid and will start executing on Dana.
void write_data(tid_type tid,
                element_type * input_data_array,
                size_t count);

// A special write data request used for incremental training. Here,
// an input and an expected output vector are passed. The
// configuration cache is updated inside the Configuration Cache.
void write_data_train_incremental(tid_type tid,
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

//-------------------------------------- Supervisor

// Incomplete and undocumented / here be dragons...

// ASID--NNID Table Data Structure. To help clarify this, I'm
// including a figure which shows the inter-relationships between all
// the strucst which make up this data structure.
//
// This whole thing is indexed by an OS ASID--NNID Table Pointer
// (ANTP), shown at the bottom right. The ANTP points at an
// `asid_nnid_table` which is a top-level data structure that contains
// metadata (currently just the total number of ASIDs) and a pointer
// to the first `asid_nnid_table_entry`. The top-level
// `asid_nnid_table` also contains a `size` which indicates the size
// of the ASID--NNID Table or the number of `asid_nnid_table_entry`
// that can be index. While I initially thought that the X-Files
// Arbiter would have to deal with this OS ANTP pointer, I don't think
// that's actually the case. The X-Files Arbiter can deal with a
// direct pointer to the first `asid_nnid_table_entry` with the `size`
// parameter communicated by the OS. The reason for this is to avoid a
// dereference and the size and location of the ASID--NNID table entry
// are likely fixed, i.e., doing a `realloc` of the ASID--NNID table
// entry will change the pointer and the size. The X-Files Arbiter
// ANTP is communicated using the `set_asid` function below.
//
// An `asid_nnid_table_entry` contains all the information for one
// ASID. To facilitate indexing, the intended convention is that ASIDs
// are generated sequentially staring from 0. Therefore, the ASID can
// be used to index its `asid_nnid_table_entry`. Each
// `asid_nnid_table_entry` contains two pointers. The first,
// `asid_nnid`, points at a data structure, `nn_configuration`, that
// can be used to dereference NNIDs into raw NN Configurations. The
// second, `transaction_io`, points at `io`, a pair of memory queues
// (circular buffer/FIFO), which can be used to read and write
// input/output data for this specific ASID.
//
// An `nn_configuration` contains a pointer to a specific memory
// location containing an NN configuration as well as metadata
// indicating the size of this configuration (so that you know how
// much data to read).
//
// An `io` contains pointers to input and output queue data structures
// (`queue`).

typedef struct {                 // |------------|     <---- queue size ----->
  uint64_t * data;               // | * data     |---> [ | |0|1|2|3|4| | ... ]
  size_t size;                   // | queue size |          ^       ^
  uint64_t * head;               // | * head     |----------|       |
  uint64_t * tail;               // | * tail     |------------------|
} queue;                         // |------------| <-----| <--|
                                 //                      |    |
typedef struct {                 // |----------------|   |    |
  uint64_t header;               // | status bits    |   |    |
  queue * input;                 // | * input queue  |---|    |
  queue * output;                // | * output queue |--------|
} io;                            // |----------------| <-----------------|
                                 //                                      |
typedef struct {                 // |--------------------|               |
  size_t size;                   // | size of config     |               |
  size_t elements_per_block;     // | elements per block |               |
  xlen_t * config;               // | * config           |-> [NN Config] |
} nn_configuration;              // |--------------------| <---|         |
                                 //                            |         |
typedef struct {                 // |-------------------|      |         |
  int num_configs;               // | num configs       |      |         |
  int num_valid;                 // | num valid configs |      |         |
  nn_configuration * asid_nnid;  // | * ASID--NNID      |------|         |
  io * transaction_io;           // | * IO              |----------------|
} asid_nnid_table_entry;         // |-------------------| <-| <--[Hardware ANTP]
                                 //                         |
typedef struct {                 // |-----------|           |
  size_t size;                   // | num ASIDs |           |
  asid_nnid_table_entry * entry; // | * entry   |-----------|
} asid_nnid_table;               // |-----------| <--------------------[OS ANTP]

// Set the ASID to a new value
xlen_t set_asid (asid_type asid);

// Set the ASID--NNID Table Poitner (ANTP)
xlen_t set_antp (asid_nnid_table * os_antp);

// Constructor and destructor for the ASID--NNID Table data structure
void asid_nnid_table_create(asid_nnid_table ** table, size_t num_asids,
                            size_t nn_configurations_per_asid);
void asid_nnid_table_destroy(asid_nnid_table **);

// Constructor and destructor for the Queue structure
void contstuct_queue(queue **, int);
void destroy_queue(queue **);

// Print a visual organization of a specific ASID--NNIT Table
void asid_nnid_table_info(asid_nnid_table * table);

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

#endif
