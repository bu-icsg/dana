#ifndef __XFILES_H__
#define __XFILES_H__

#include <stdint.h>
#include <stdlib.h>
#include <stdio.h>

// [TODO] Any changes to these types need to occur in conjunction with
// the Chisel code and with the TID extraction part of
// new_write_request.
typedef uint32_t nnid_type;
typedef uint16_t tid_type;
typedef int32_t element_type;

//-------------------------------------- Userland

// Initiate a new Transaction for a specific NNID. The X-Files Arbiter
// will then assign and return a TID necessary for other userland
// functions.
tid_type new_write_request(nnid_type nnid);

// Write the contents of an input array of some size to the X-Files
// Arbiter. After completing this function, the transaction is deemed
// valid and will start executing on Dana.
void write_data(tid_type tid,
                element_type * input_data_array,
                size_t count);

// Read all the output data for a specific transaction. This throws
// the CPU into a spinlock repeatedly checking the validity of the
// X-Files response.
uint64_t read_data_spinlock(tid_type tid,
                            element_type * output_data_array,
                            size_t count);

//-------------------------------------- Supervisor

// Incomplete and undocumented / here be dragons...

typedef struct {
  uint64_t * data;
  int size;
  uint64_t * head;
  uint64_t * tail;
} queue;

typedef struct {
  uint64_t header;
  queue * input;
  queue * output;
} io;

typedef struct {
  int size;
  uint8_t * config;
} nn_configuration;

typedef struct {
  int num_configs;
  int num_valid;
  nn_configuration * asid_nnid;
  io * transaction_io;
} asid_nnid_table_entry;

typedef struct {
  int size;
  asid_nnid_table_entry * entry;
} asid_nnid_table;

// Constructor and destructor for the ASID--NNID Table data structure
void asid_nnid_table_create(asid_nnid_table **, int, int);
void asid_nnid_table_destroy(asid_nnid_table **);

// Constructor and destructor for the Queue structure
void contstuct_queue(queue **, int);
void destroy_queue(queue **);

// Read an NN configuration from a binary file and append in to the
// the specified ASID
int attach_nn_configuration(asid_nnid_table **, uint16_t, const char *);

#endif
