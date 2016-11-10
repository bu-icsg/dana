// See LICENSE for license details.

#ifndef SRC_MAIN_C_XFILES_SUPERVISOR_TYPES_H_
#define SRC_MAIN_C_XFILES_SUPERVISOR_TYPES_H_

#include <stdint.h>
#include "src/main/c/xfiles.h"

#define SYSCALL_SET_ASID 512
#define SYSCALL_SET_ANTP 513
#define SYSCALL_DEBUG_ECHO 514

#define t_SUP_UPDATE_ASID 0
#define t_SUP_WRITE_REG 1
#define t_SUP_READ_CSR 2

typedef enum {
  csr_CAUSE = 0
} xfiles_csr_t;

typedef int16_t asid_type;

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

typedef struct {             // |------------|     <---- queue size ----->
  uint64_t * data;           // | * data     |---> [ | |0|1|2|3|4| | ... ]
  size_t size;               // | queue size |          ^       ^
  uint64_t * head;           // | * head     |----------|       |
  uint64_t * tail;           // | * tail     |------------------|
} queue;                     // |------------| <-----| <--|
                             //                      |    |
typedef struct {             // |----------------|   |    |
  uint64_t header;           // | status bits    |   |    |
  queue * input;             // | * input queue  |---|    |
  queue * output;            // | * output queue |--------|
} io;                        // |----------------| <----------------------|
                             //                                           |
typedef struct {             // |-------------------------|               |
  size_t size;               // | size of config          |               |
  size_t elements_per_block; // | elements per block      |               |
  xlen_t * config_raw;       // | * config unaligned      |               |
  xlen_t * config_p;         // | * config aligned phys   |-> [NN Config] |
  xlen_t * config_v;         // | * config aligned virt   |-> [NN Config] |
} nn_config;                 // |-------------------------| <---|         |
                             //                                 |         |
typedef struct {             // |-------------------|           |         |
  int num_configs;           // | num configs       |           |         |
  int num_valid;             // | num valid configs |           |         |
  nn_config * asid_nnid_p;   // | * ASID--NNID phys |--<phys>---|         |
  nn_config * asid_nnid_v;   // | * ASID--NNID virt |--<virt>---|         |
  io * transaction_io;       // | * IO              |---------------------|
} ant_entry;                 // |-------------------| <-| <--[Hardware ANTP]
                             //                         |
typedef struct {             // |-----------|           |
  size_t size;               // | num ASIDs |           |
  ant_entry * entry_p;       // | * entry   |-----------|
  ant_entry * entry_v;       // | * entry   |-----------|
} ant;                       // |-----------| <--------------------[OS ANTP]

#endif  // SRC_MAIN_C_XFILES_SUPERVISOR_H_
