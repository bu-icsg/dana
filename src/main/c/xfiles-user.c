// See LICENSE for license details.

#include "src/main/c/xfiles-user.h"
#include "src/main/c/xfiles-debug.h"

// All RoCC communication occurs using the "custom0" RISC-V
// instruction of the following format:
//
//   custom0 <output register> <input reg 1> <input reg 2> <funct>
//
// The X-Files arbiter uses the bits of the 7-bit "funct" field to
// decode the instruction and figure out what it should do with it.
// The input registers are referred to as rs1 and rs2 in the
// rocket-chip repo and by available RoCC documentation so we stick
// with that convention below.
//
// The bits of "funct" are as follows:
//
//   |       [6:3]|       2|      1|            0|
//   | **unused** | isLast | isNew | readOrWrite |

xlen_t xfiles_dana_id(int flag_print) {
  xlen_t out;

  XFILES_INSTRUCTION_R_I_I(out, 0, 0, t_USR_XFILES_DANA_ID);

  if (flag_print) {
    uint64_t transaction_table_num_entries = (out >> 48) & ~((~0) << 4);

    uint32_t elements_per_block = (out >> 10) & ~((~0) << 6);
    uint32_t pe_table_num_entries = (out >> 4) & ~((~0) << 6);
    uint32_t cache_num_entries = out & ~((~0) << 4);
    printf("X-FILES/DANA Info:\n"
           "  Transaction Table Entries: %ld\n"
           "  Elements per Block:        %d\n"
           "  PEs:                       %d\n"
           "  Cache Entries:             %d\n",
           transaction_table_num_entries, elements_per_block,
           pe_table_num_entries, cache_num_entries);
  }

  return out;
}

tid_type new_write_request(nnid_type nnid, learning_type_t learning_type,
                           element_type num_train_outputs) {
  uint64_t out, rs2;

  rs2 = (uint64_t) nnid |
    ((uint64_t) num_train_outputs << 32) |
    ((uint64_t) learning_type << 48);

  // Initiate a new transaction by setting the "readOrWrite" (bit 0,
  // read == 0 / write == 1) and "isNew" (bit 1) flags of "funct",
  // i.e., funct == 3. The nnid goes in rs2. The output will show up
  // in the varaible "out".
  XFILES_INSTRUCTION(out, 0, rs2, t_USR_NEW_REQUEST);

  // The TID is in bits [47:32] of what we get back. Pull out this
  // portion and return it. [TODO] This is fragile on tid and element
  // sizing.
  const size_t shift = sizeof(xlen_t)*8 - sizeof(tid_type)*8 - RESP_CODE_WIDTH;
  const xlen_t mask = (~((~(xlen_t)0) << 16)) << shift;
  return (out & mask) >> shift;
}

xlen_t write_register(tid_type tid, xfiles_reg reg, uint32_t value) {

  xlen_t rs2, out;
  rs2 = (uint64_t) value | ((uint64_t) reg << 32);
  XFILES_INSTRUCTION(out, tid, rs2, t_USR_WRITE_REGISTER);
  return out;
}

xlen_t write_data(tid_type tid, element_type * data, size_t count) {
  const size_t shift = sizeof(xlen_t) * 8 - RESP_CODE_WIDTH;
  xlen_t out;

  // There are two types of writes available to users determined by
  // whether or not "isLast" (bit 2) is set. We write all but the last
  // data value with "isLast" deasserted (funct == 1). The tid goes in
  // rs1 and data goes in rs2.
  int write_index = 0;
  while (write_index != count - 1) {
    XFILES_INSTRUCTION(out, tid, data[write_index], t_USR_WRITE_DATA);
    int exit_code = out >> shift;
    switch (exit_code) {
      case resp_OK: write_index++; continue;
      case resp_QUEUE_ERR: continue;
      default: return exit_code;
    }
  }

  // Finally, we write the last data value with "isLast" set (funct ==
  // 5). When the X-Files Arbiter sees this "isLast" bit, it enables
  // execution of the transaction.
  while (1) {
    XFILES_INSTRUCTION(out, tid, data[write_index], t_USR_WRITE_DATA_LAST);
    int exit_code = out >> shift;
    switch (exit_code) {
      case resp_OK: return 0;
      case resp_QUEUE_ERR: continue;
      default: return exit_code;
    }
  }
}

xlen_t write_data_except_last(tid_type tid, element_type * data, size_t count) {
  const size_t shift = sizeof(xlen_t) * 8 - RESP_CODE_WIDTH;
  xlen_t out;

  int write_index = 0;
  while (write_index != count - 1) {
    XFILES_INSTRUCTION(out, tid, data[write_index], t_USR_WRITE_DATA);
    int exit_code = out >> shift;
    switch (exit_code) {
      case resp_OK: write_index++; continue;
      case resp_QUEUE_ERR: continue;
      default: return exit_code;
    }
  }
  return 0;
}

xlen_t write_data_last(tid_type tid, element_type * data, size_t count) {
  const size_t shift = sizeof(xlen_t) * 8 - RESP_CODE_WIDTH;
  xlen_t out;

  while (1) {
    XFILES_INSTRUCTION(out, tid, data[count - 1], t_USR_WRITE_DATA_LAST);
    int exit_code = out >> shift;
    switch (exit_code) {
      case resp_OK: return 0;
      case resp_QUEUE_ERR: continue;
      default: return exit_code;
    }
  }
}

xlen_t write_data_train_incremental(tid_type tid, element_type * input,
                                    element_type * output, size_t count_input,
                                    size_t count_output) {
  // Simply write the exepcted outputs followed by the inputs.
  xlen_t out = 0;
  if ((out = write_data(tid, output, count_output))) return out;
  if ((out = write_data(tid, input, count_input))) return out;
  return 0;
}

xlen_t read_data_spinlock(tid_type tid, element_type * data, size_t count) {
  uint64_t out;

  // Poll via READ_DATA requests until we've gotten enough OK
  // responses equal to the count that we're looking for.
  int read_index = 0;
  while (read_index != count) {
    XFILES_INSTRUCTION_R_R_I(out, tid, 0, t_USR_READ_DATA);
    int exit_code = out >> (32 + 16 + 16 - RESP_CODE_WIDTH);
    switch (exit_code) {
      case resp_NOT_DONE: continue;
      case resp_OK: data[read_index++] = out; continue;
      default: return exit_code;
    }
  }

  return 0;
}

xlen_t pk_syscall_set_asid(asid_type asid) {
  // This currently depends on a backing OS system call supported by
  // the Proxy Kernel (a basic RISC-V OS). Using the RISC-V function
  // calling convention, the asid is placed into register a0, the
  // syscall ID (#512) in register a7, and we generate a syscall. The
  // Proxy Kernel will then generate a special custom0 instruction
  // that sets the ASID. No output is expected, so we just return
  // whenever the OS returns control.
  xlen_t old_asid;
  asm volatile ("mv a0, %[asid]\n\t"
                "li a7, %[syscall]\n\t"
                "ecall\n\t"
                "mv %[old_asid], a0"
                : [old_asid] "=r" (old_asid)
                : [asid] "r" (asid), [syscall] "i" (SYSCALL_SET_ASID)
                : "a0", "a7");
  return old_asid;
}

xlen_t pk_syscall_set_antp(ant * os_antp) {
  // As with `set_asid`, this relies on the Proxy Kernel to handle
  // this system call. This passes a pointer to the first ASID--NNID
  // table entry and the size (i.e., the number of ASIDs).
  xlen_t old_antp;
  asm volatile ("mv a0, %[antp]\n\t"
                "mv a1, %[size]\n\t"
                "li a7, %[syscall]\n\t"
                "ecall\n\t"
                "mv %[old_antp], a0"
                : [old_antp] "=r" (old_antp)
                : [antp] "r" (os_antp->entry_p), [size] "r" (os_antp->size),
                  [syscall] "i" (SYSCALL_SET_ANTP)
                : "a0", "a7");
  return old_antp;
}

xlen_t pk_syscall_debug_echo(uint32_t data) {
  xlen_t out;
  asm volatile ("mv a0, %[data]\n\t"
                "li a7, %[syscall]\n\t"
                "ecall\n\t"
                "mv %[out], a0"
                : [out] "=r" (out)
                : [data] "r" (data), [syscall] "i" (SYSCALL_DEBUG_ECHO)
                : "a0", "a7");
  return out;
}

xlen_t kill_transaction(tid_type tid) {
  return -1;
}

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

void asid_nnid_table_create(ant ** new_table, size_t table_size,
                            size_t configs_per_entry) {
  int i;

  // Allocate space for the table
  *new_table = (ant *) malloc(sizeof(ant));
  (*new_table)->entry_v =
    (ant_entry *) malloc(sizeof(ant_entry) * table_size);
  // [TODO] This assumes physical contiguity
  printf("[INFO] Translate 0x%p\n", (*new_table)->entry_v);
  (*new_table)->entry_p = debug_virt_to_phys((*new_table)->entry_v);
  printf("[INFO] Got 0x%p\n", (*new_table)->entry_p);
  (*new_table)->size = table_size;

  for (i = 0; i < table_size; i++) {
    // Create the configuration region
    (*new_table)->entry_v[i].asid_nnid_v =
      (nn_config *) malloc(configs_per_entry * sizeof(nn_config));
    // [TODO] This assumes physical contiguity
    printf("[INFO] Translate 0x%p\n", (*new_table)->entry_v[i].asid_nnid_v);
    (*new_table)->entry_v[i].asid_nnid_p =
        debug_virt_to_phys((*new_table)->entry_v[i].asid_nnid_v);
    printf("[INFO] Got 0x%p\n", (*new_table)->entry_v[i].asid_nnid_p);
    (*new_table)->entry_v[i].asid_nnid_v->config_v = NULL;
    (*new_table)->entry_v[i].asid_nnid_v->config_p = NULL;
    (*new_table)->entry_v[i].num_configs = configs_per_entry;
    (*new_table)->entry_v[i].num_valid = 0;

    // Create the io region
    (*new_table)->entry_v[i].transaction_io = (io *) malloc(sizeof(io));
    (*new_table)->entry_v[i].transaction_io->header = 0;
    (*new_table)->entry_v[i].transaction_io->input = (queue *) malloc(sizeof(queue));
    (*new_table)->entry_v[i].transaction_io->output = (queue *) malloc(sizeof(queue));
    construct_queue(&(*new_table)->entry_v[i].transaction_io->input, 16);
    construct_queue(&(*new_table)->entry_v[i].transaction_io->output, 16);
  }

}

void asid_nnid_table_destroy(ant ** old_table) {
  int i, j;
  for (i = 0; i < (*old_table)->size; i++) {
    // Destroy the configuration region
    for (j = 0; j < (*old_table)->entry_v[i].num_valid; j++)
      if ((*old_table)->entry_v[i].asid_nnid_v[j].config_v != NULL)
        free((*old_table)->entry_v[i].asid_nnid_v[j].config_raw);
    free((*old_table)->entry_v[i].asid_nnid_v);

    // Destroy the IO region
    destroy_queue(&(*old_table)->entry_v[i].transaction_io->input);
    destroy_queue(&(*old_table)->entry_v[i].transaction_io->output);
    free((*old_table)->entry_v[i].transaction_io);
  }

  free((*old_table)->entry_v);
  free(*old_table);
}

int attach_nn_configuration(ant ** table, asid_type asid,
                            const char * file_name) {
  int file_size, nnid;
  FILE *fp;

  if (asid >= (*table)->size) {
    return -1;
  }

  if ((*table)->entry_v[asid].num_valid == (*table)->entry_v[asid].num_configs) {
    return -1;
  }

  // Open the file and find out how big it is so that we can allocate
  // the correct amount of space
  if (!(fp = fopen(file_name, "rb"))) {
    return -1;
  }

  nnid = (*table)->entry_v[asid].num_valid;

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

  (*table)->entry_v[asid].asid_nnid_v[nnid].size = file_size;

  // Compute the elements per block as set in the neural network
  // configuration and write this into the ASID--NNID Table Entry
  uint64_t block_64;
  fread(&block_64, sizeof(block_64), 1, fp);
  fseek(fp, 0L, SEEK_SET);
  block_64 = (block_64 >> 4) & 3;
  (*table)->entry_v[asid].asid_nnid_v[nnid].elements_per_block = 1 << (block_64 + 2);

  // Allocate space for this configuraiton
  alloc_config_aligned(&(*table)->entry_v[asid].asid_nnid_v[nnid].config_raw,
                       &(*table)->entry_v[asid].asid_nnid_v[nnid].config_v,
                       file_size * sizeof(xlen_t));
  assert((*table)->entry_v[asid].asid_nnid_v[nnid].config_raw != NULL);
  assert((*table)->entry_v[asid].asid_nnid_v[nnid].config_v != NULL);
  // [TODO] Assumes memory contiguity
  printf("[INFO] Translate 0x%p\n", (*table)->entry_v[asid].asid_nnid_v[nnid].config_v);
  (*table)->entry_v[asid].asid_nnid_v[nnid].config_p =
      debug_virt_to_phys((*table)->entry_v[asid].asid_nnid_v[nnid].config_v);
  printf("[INFO] Translate 0x%p\n", (*table)->entry_v[asid].asid_nnid_v[nnid].config_p);

  // Write the configuration
  fread((*table)->entry_v[asid].asid_nnid_v[nnid].config_v, sizeof(xlen_t),
        file_size, fp);

  fclose(fp);
  return ++(*table)->entry_v[asid].num_valid;
}

int attach_garbage(ant ** table, asid_type asid) {

  int nnid;

  if (asid >= (*table)->size) {
    return -1;
  }
  if ((*table)->entry_v[asid].num_valid == (*table)->entry_v[asid].num_configs) {
    return -1;
  }

  nnid = (*table)->entry_v[asid].num_valid;
  (*table)->entry_v[asid].asid_nnid_v[nnid].size = 0;
  (*table)->entry_v[asid].asid_nnid_v[nnid].config_v = NULL;
  (*table)->entry_v[asid].asid_nnid_v[nnid].config_p = NULL;

  return ++(*table)->entry_v[asid].num_valid;
}

int attach_nn_configuration_array(ant ** table, uint16_t asid,
                                  const xlen_t * array, size_t size) {
  int nnid;

  // Fail if we've run out of space
  if ((*table)->entry_v[asid].num_valid == (*table)->entry_v[asid].num_configs) {
    return -1;
  }

  // The NNID is the index into the ASID--NNID array. The NNID is
  // implict here as it is the _next_ unallocated entry in the
  // ASID--NNID array.
  nnid = (*table)->entry_v[asid].num_valid;

  // Allocate memory for the array and copy in the input array. Update
  // the size following.
  alloc_config_aligned(&(*table)->entry_v[asid].asid_nnid_v[nnid].config_raw,
                       &(*table)->entry_v[asid].asid_nnid_v[nnid].config_v,
                       size * sizeof(xlen_t));
  assert((*table)->entry_v[asid].asid_nnid_v[nnid].config_raw != NULL);
  assert((*table)->entry_v[asid].asid_nnid_v[nnid].config_v != NULL);
  // [TODO] This assumes physical contiguity
  printf("[INFO] Translate 0x%p\n", (*table)->entry_v[asid].asid_nnid_v[nnid].config_v);
  (*table)->entry_v[asid].asid_nnid_v[nnid].config_p =
      debug_virt_to_phys((*table)->entry_v[asid].asid_nnid_v[nnid].config_v);
  printf("[INFO] Translate 0x%p\n", (*table)->entry_v[asid].asid_nnid_v[nnid].config_p);

  memcpy((*table)->entry_v[asid].asid_nnid_v[nnid].config_v, array,
         size * sizeof(xlen_t));
  (*table)->entry_v[asid].asid_nnid_v[nnid].size = size;

  // Return the new number of valid entries
  return ++(*table)->entry_v[asid].num_valid;
}

int alloc_config_aligned(xlen_t ** raw, xlen_t ** aligned, size_t size) {
  *raw = (xlen_t *) malloc(size * sizeof(xlen_t) + TILELINK_L2_BYTES - 1);
  if (*raw == NULL) return -1;
  const size_t mask = ~(~0 << TILELINK_L2_ADDR_BITS);
  size_t offset = (-((size_t) *raw & mask) & mask);
  *aligned = (xlen_t *) ((char*) *raw + offset);
  return 0;
}
