// See LICENSE for license details.

#include <stdio.h>
#include <stdlib.h>
#include <assert.h>
#include <string.h>
#include "src/main/c/xfiles-asid-nnid-table.h"

void asid_nnid_table_info(ant * table) {
  int i = 0;
  printf("[INFO] 0x%p <- Table Head\n", table);
  printf("[INFO]   |-> 0x%p: size:                     0x%lx\n"
         "[INFO]       0x%p: * entry_p:                0x%p\n"
         "[INFO]       0x%p: * entry_v:                0x%p\n",
         &table->size,  table->size,
         &table->entry_p, table->entry_p,
         &table->entry_v, table->entry_v);
  for (ant_entry * e = table->entry_v; e < &table->entry_v[table->size]; e++, i++) {
    printf("[INFO]         |-> [%0d] 0x%p: num_configs:    0x%x\n"
           "[INFO]         |       0x%p: num_valid:      0x%x\n"
           "[INFO]         |       0x%p: asid_nnid_p:    0x%p\n"
           "[INFO]         |       0x%p: asid_nnid_v:    0x%p\n",
           i,
           &e->num_configs, e->num_configs,
           &e->num_valid, e->num_valid,
           &e->asid_nnid_p, e->asid_nnid_p,
           &e->asid_nnid_v, e->asid_nnid_v);
    // Dump the `nn_configuration`
    int j = 0;
    for (nn_config * n = e->asid_nnid_v; n < &e->asid_nnid_v[e->num_valid]; n++, j++) {
      printf("[INFO]         |         |-> [%0d] 0x%p: size:             0x%lx\n"
             "[INFO]         |         |       0x%p: elements_per_block: 0d%lx\n"
             "[INFO]         |         |       0x%p: * config_raw:       0x%p\n"
             "[INFO]         |         |       0x%p: * config_p:         0x%p\n"
             "[INFO]         |         |       0x%p: * config_v:         0x%p\n",
             j,
             &n->size, n->size,
             &n->elements_per_block, n->elements_per_block,
             &n->config_raw, n->config_raw,
             &n->config_p, n->config_p,
             &n->config_v, n->config_v);
    }
    // Back to `asid_nnid_table_entry`
    printf("[INFO]         |       0x%p: transaction_io: 0x%p\n"
           "[INFO]         |         |-> 0x%p: header:   0x%lx\n"
           "[INFO]         |         |   0x%p: * input:  0x%p\n"
           "[INFO]         |         |   0x%p: * output: 0x%p\n",
           &e->transaction_io, e->transaction_io,
           &e->transaction_io->header, e->transaction_io->header,
           &e->transaction_io->input, e->transaction_io->input,
           &e->transaction_io->output, e->transaction_io->output);
  }
}

void construct_queue(queue ** new_queue, int size) {
  (*new_queue)->data = (uint64_t *) malloc(size * sizeof(uint64_t));
  (*new_queue)->size = size;
  (*new_queue)->head = (*new_queue)->data;
  (*new_queue)->tail = (*new_queue)->data;
}

void destroy_queue(queue ** old_queue) {
  free((*old_queue)->data);
  free(*old_queue);
}

void asid_nnid_table_create(ant ** t, size_t size,
                            size_t configs_per_entry) {
  // Allocate space for the table
  *t = (ant *) malloc(sizeof(ant));
  (*t)->entry_v = (ant_entry *) malloc(sizeof(ant_entry) * size);
  // [TODO] This assumes physical contiguity
#ifdef NO_VM
  (*t)->entry_p = (*t)->entry_v;
#else
  (*t)->entry_p = debug_virt_to_phys((*t)->entry_v);
#endif
  (*t)->size = size;

  // Allocate space for ant_entry
  for (ant_entry * e = (*t)->entry_v; e < &(*t)->entry_v[size]; e++) {
    e->asid_nnid_v = (nn_config *) malloc(configs_per_entry * sizeof(nn_config));
    // [TODO] This assumes physical contiguity
#ifdef NO_VM
    e->asid_nnid_p = e->asid_nnid_v;
#else
    e->asid_nnid_p = debug_virt_to_phys(e->asid_nnid_v);
#endif
    e->asid_nnid_v->config_v = e->asid_nnid_v->config_p = NULL;
    e->num_configs = configs_per_entry;
    e->num_valid = 0;

    // Create the io region
    e->transaction_io = (io *) malloc(sizeof(io));
    e->transaction_io->header = 0;
    e->transaction_io->input = (queue *) malloc(sizeof(queue));
    e->transaction_io->output = (queue *) malloc(sizeof(queue));
    construct_queue(&e->transaction_io->input, 16);
    construct_queue(&e->transaction_io->output, 16);
  }
}

void asid_nnid_table_destroy(ant ** t) {
  for (ant_entry * e = (*t)->entry_v; e < &(*t)->entry_v[(*t)->size]; e++) {
    for (nn_config * n = e->asid_nnid_v; n < &(e)->asid_nnid_v[e->num_configs]; n++) {
      free(n->config_raw);
    }
    free(e->asid_nnid_v);
    destroy_queue(&e->transaction_io->input);
    destroy_queue(&e->transaction_io->output);
    free(e->transaction_io);
  }
  free((*t)->entry_v);
  free(*t);
}

int attach_nn_configuration(ant ** table, asid_type asid,
                            const char * file_name) {
  int file_size, nnid;
  FILE *fp;

  if (asid >= (*table)->size) {
    return -1;
  }

  ant_entry * e = &(*table)->entry_v[asid];
  if (e->num_valid == e->num_configs) {
    return -1;
  }

  // Open the file and find out how big it is so that we can allocate
  // the correct amount of space
  if (!(fp = fopen(file_name, "rb"))) {
    return -1;
  }

  nnid = e->num_valid;

  // Kludge until fseek works again
  // fseek(fp, 0L, SEEK_END);
  char c = 'a';
  while (fread(&c, 1, 1, fp)) {}

  file_size = ftell(fp) / sizeof(xlen_t);
  file_size += (ftell(fp) % sizeof(xlen_t)) ? 1 : 0;
  fseek(fp, 0L, SEEK_SET);

  if (file_size <= 0) {
    return -1;
  }

  nn_config * n = &e->asid_nnid_v[nnid];
  n->size = file_size;

  // Compute the elements per block as set in the neural network
  // configuration and write this into the ASID--NNID Table Entry
  uint64_t block_64;
  fread(&block_64, sizeof(block_64), 1, fp);
  fseek(fp, 0L, SEEK_SET);
  block_64 = (block_64 >> 4) & 3;
  n->elements_per_block = 1 << (block_64 + 2);

  // Allocate space for this configuraiton
  alloc_config_aligned(&n->config_raw, &n->config_v, file_size * sizeof(xlen_t));
  assert(n->config_raw != NULL);
  assert(n->config_v != NULL);

  // Write the configuration
  fread(n->config_v, sizeof(xlen_t), file_size, fp);
  // [TODO] Assumes memory contiguity
#ifdef NO_VM
  n->config_p = n->config_v;
#else
  n->config_p = debug_virt_to_phys(n->config_v);
#endif
  assert((size_t) n != -1);

  fclose(fp);
  return ++e->num_valid;
}

int attach_garbage(ant ** table, asid_type asid) {

  int nnid;

  if (asid >= (*table)->size) {
    return -1;
  }
  ant_entry * e = &(*table)->entry_v[asid];
  if (e->num_valid == e->num_configs) {
    return -1;
  }

  nnid = e->num_valid;
  e->asid_nnid_v[nnid].size = 0;
  e->asid_nnid_v[nnid].config_v = NULL;
  e->asid_nnid_v[nnid].config_p = NULL;

  return ++e->num_valid;
}

int attach_nn_configuration_array(ant ** table, uint16_t asid,
                                  const xlen_t * array, size_t size) {
  int nnid;

  // Fail if we've run out of space
  ant_entry * e = &(*table)->entry_v[asid];
  if (e->num_valid == e->num_configs) { return -1; }

  // The NNID is the index into the ASID--NNID array. The NNID is
  // implict here as it is the _next_ unallocated entry in the
  // ASID--NNID array.
  nnid = e->num_valid;

  // Allocate memory for the array and copy in the input array. Update
  // the size following.
  nn_config * n = &e->asid_nnid_v[nnid];
  alloc_config_aligned(&n->config_raw, &n->config_v, size * sizeof(xlen_t));
  assert(n->config_raw != NULL);
  assert(n->config_v != NULL);

  memcpy(n->config_v, array, size * sizeof(xlen_t));
  n->size = size;

  // [TODO] This assumes physical contiguity
#ifdef NO_VM
  n->config_p = n->config_v;
#else
  n->config_p = debug_virt_to_phys(n->config_v);
#endif
  assert((size_t) n != -1);

  // Return the new number of valid entries
  return ++e->num_valid;
}

int alloc_config_aligned(xlen_t ** raw, xlen_t ** aligned, size_t size) {
  *raw = (xlen_t *) malloc(size * sizeof(xlen_t) + TILELINK_L2_BYTES - 1);
  if (*raw == NULL) return -1;
  const size_t mask = ~(~0 << TILELINK_L2_ADDR_BITS);
  size_t offset = (-((size_t) *raw & mask) & mask);
  *aligned = (xlen_t *) ((char*) *raw + offset);
  return 0;
}
