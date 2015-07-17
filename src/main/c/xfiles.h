#ifndef __XFILES_H__
#define __XFILES_H__

#include <stdint.h>
#include <stdlib.h>
#include <stdio.h>

typedef uint32_t nnid_type;
typedef uint16_t tid_type;
typedef int32_t element_type;

//-------------------------------------- Userland

tid_type new_write_request(nnid_type);

void write_data(tid_type, element_type *, size_t);

uint64_t spin_read_data(tid_type, element_type *, size_t);

//-------------------------------------- Supervisor

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
