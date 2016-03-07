// See LICENSE for license details.

#ifndef SRC_MAIN_C_XFILES_SUPERVISOR_H
#define SRC_MAIN_C_XFILES_SUPERVISOR_H

#include "src/main/c/xfiles.h"
// #include "../riscv-tools/riscv-pk/pk/pk.h"

#define XFILES_SYSCALL_SET_ASID 512
#define XFILES_SYSCALL_ATTACH_CONFIGURATION 513

typedef enum {
  UPDATE_ASID = 0,
  UPDATE_ANTP = 1
} request_super_t;

// Incomplete and undocumented / here be dragons...

// ASID--NNID Table Data Structure. To help clarify this, I'm
// including a figure which shows the inter-relationships between all
// the strucst which make up this data structure.
//
// This whole thing is indexed by an OS ASID--NNID Table Pointer
// (ANTP), shown at the bottom right. The ANTP points at an
// `asid_nnid_table_t` which is a top-level data structure that
// contains metadata (currently just the total number of ASIDs) and a
// pointer to the first `asid_nnid_table_entry`. The top-level
// `asid_nnid_table_t` also contains a `size` which indicates the size
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
  x_len * config;                // | * config           |-> [NN Config] |
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
} asid_nnid_table_t;             // |-----------| <--------------------[OS ANTP]

// Set the ASID to a new value
uint64_t set_asid (asid_t asid);

// Set the ASID--NNID Table Poitner (ANTP)
uint64_t set_antp (asid_nnid_table_t * os_antp);

// Constructor and destructor for the ASID--NNID Table data structure
void asid_nnid_table_create(asid_nnid_table_t ** table, size_t num_asids,
                            size_t nn_configurations_per_asid);
void asid_nnid_table_destroy(asid_nnid_table_t **);

// Constructor and destructor for the Queue structure
void contstuct_queue(queue **, int);
void destroy_queue(queue **);

// Print a visual organization of a specific ASID--NNIT Table
void asid_nnid_table_info(asid_nnid_table_t * table);

// Append the NN configuration contained in a binary file to the ASID
// of the specified ASID--NNID table. **NOTE** This is currently
// unsupported with the proxy kernel as it doesn't supported file
// operation system calls.
int attach_nn_configuration(asid_nnid_table_t ** table, asid_t asid,
                            const char * nn_configuration_binary_file);

// Attach an NN configuration that points to NULL. This is useful for
// testing purposes to place a specific NN configuration in a specific
// location and generate traps that will cause us to fail fast on an
// invalid read.
int attach_garbage(asid_t asid);

// Append the NN configuration contained in an XLen-sized (64-bit or
// 32-bit depending on RISC-V architecture) array and of a certain
// size to the ASID of a specific ASID--NNID Table.
int attach_nn_configuration_array(uint16_t asid,
                                  const x_len * nn_configuration_array,
                                  size_t size);

#endif  // SRC_MAIN_C_XFILES_SUPERVISOR_H
