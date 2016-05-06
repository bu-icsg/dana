// See LICENSE for license details.

#include "src/main/c/xfiles-user.h"

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

  asm volatile ("custom0 %[out], 0, 0, %[type]"
                : [out] "=r" (out)
                : [type] "i" (XFILES_DANA_ID));

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
  asm volatile ("custom0 %[out], %[rs1], %[rs2], %[type]"
                : [out] "=r" (out)
                : [rs1] "r" (0), [rs2] "r" (rs2), [type] "i" (NEW_REQUEST));

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

  asm volatile ("custom0 %[out], %[rs1], %[rs2], %[type]"
                : [out] "=r" (out)
                : [rs1] "r" (tid), [rs2] "r" (rs2),
                 [type] "i" (WRITE_REGISTER));
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
    asm volatile ("custom0 %[out], %[rs1], %[rs2], %[type]"
                  : [out] "=r" (out)
                  : [rs1] "r" (tid), [rs2] "r" (data[write_index]),
                   [type] "i" (WRITE_DATA));
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
    asm volatile ("custom0 %[out], %[rs1], %[rs2], %[type]"
                  : [out] "=r" (out)
                  : [rs1] "r" (tid), [rs2] "r" (data[write_index]),
                   [type] "i" (WRITE_DATA_LAST));
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
    asm volatile ("custom0 %[out], %[rs1], %[rs2], %[type]"
                  : [out] "=r" (out)
                  : [rs1] "r" (tid), [rs2] "r" (data[write_index]),
                   [type] "i" (WRITE_DATA));
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
    asm volatile ("custom0 %[out], %[rs1], %[rs2], %[type]"
                  : [out] "=r" (out)
                  : [rs1] "r" (tid), [rs2] "r" (data[count - 1]),
                   [type] "i" (WRITE_DATA_LAST));
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
    asm volatile ("custom0 %[out], %[rs1], 0, %[type]"
                  : [out] "=r" (out)
                  : [rs1] "r" (tid), [type] "i" (READ_DATA));
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
                "li a7, 512\n\t"
                "ecall\n\t"
                "mv %[old_asid], a0"
                : [old_asid] "=r" (old_asid)
                : [asid] "r" (asid)
                : "a0", "a7");
  return old_asid;
}

xlen_t pk_syscall_set_antp(asid_nnid_table * os_antp) {
  // As with `set_asid`, this relies on the Proxy Kernel to handle
  // this system call. This passes a pointer to the first ASID--NNID
  // table entry and the size (i.e., the number of ASIDs).
  xlen_t old_antp;
  asm volatile ("mv a0, %[antp]\n\t"
                "mv a1, %[size]\n\t"
                "li a7, 513\n\t"
                "ecall\n\t"
                "mv %[old_antp], a0"
                : [old_antp] "=r" (old_antp)
                : [antp] "r" (os_antp->entry), [size] "r" (os_antp->size)
                : "a0", "a7");
  return old_antp;
}

xlen_t kill_transaction(tid_type tid) {
  return -1;
}

void asid_nnid_table_info(asid_nnid_table * table) {
  int i, j;
  printf("[INFO] 0x%lx <- Table Head\n", (uint64_t) table);
  printf("[INFO]   |-> 0x%lx: size:                     0x%lx\n",
         (uint64_t) &table->size,
         (uint64_t) table->size);
  printf("[INFO]       0x%lx: * entry:                  0x%lx\n",
         (uint64_t) &table->entry,
         (uint64_t) table->entry);
  for (i = 0; i < table->size; i++) {
    printf("[INFO]         |-> [%0d] 0x%lx: num_configs:    0x%lx\n", i,
           (uint64_t) &table->entry[i].num_configs,
           (uint64_t) table->entry[i].num_configs);
    printf("[INFO]         |       0x%lx: num_valid:      0x%lx\n",
           (uint64_t) &table->entry[i].num_valid,
           (uint64_t) table->entry[i].num_valid);
    printf("[INFO]         |       0x%lx: asid_nnid:      0x%lx\n",
           (uint64_t) &table->entry[i].asid_nnid,
           (uint64_t) table->entry[i].asid_nnid);
    // Dump the `nn_configuration`
    for (j = 0; j < table->entry[i].num_valid; j++) {
      printf("[INFO]         |         |-> [%0d] 0x%lx: size:             0x%lx\n", j,
             (uint64_t) &table->entry[i].asid_nnid[j].size,
             (uint64_t) table->entry[i].asid_nnid[j].size);
      printf("[INFO]         |         |       0x%lx: elements_per_block: 0d%ld\n",
             (uint64_t) &table->entry[i].asid_nnid[j].elements_per_block,
             (uint64_t) table->entry[i].asid_nnid[j].elements_per_block);
      printf("[INFO]         |         |       0x%lx: * config:           0x%lx\n",
             (uint64_t) &table->entry[i].asid_nnid[j].config,
             (uint64_t) table->entry[i].asid_nnid[j].config);
    }
    // Back to `asid_nnid_table_entry`
    printf("[INFO]         |       0x%lx: transaction_io: 0x%lx\n",
           (uint64_t) &table->entry[i].transaction_io,
           (uint64_t) table->entry[i].transaction_io);
    // Dump the `io`
    printf("[INFO]         |         |-> 0x%lx: header:   0x%lx\n",
           (uint64_t) &table->entry[i].transaction_io->header,
           (uint64_t) table->entry[i].transaction_io->header);
    printf("[INFO]         |         |   0x%lx: * input:  0x%lx\n",
           (uint64_t) &table->entry[i].transaction_io->input,
           (uint64_t) table->entry[i].transaction_io->input);
    printf("[INFO]         |         |   0x%lx: * output: 0x%lx\n",
           (uint64_t) &table->entry[i].transaction_io->output,
           (uint64_t) table->entry[i].transaction_io->output);
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

void asid_nnid_table_create(asid_nnid_table ** new_table, size_t table_size,
                            size_t configs_per_entry) {
  int i;

  // Allocate space for the table
  *new_table = (asid_nnid_table *) malloc(sizeof(asid_nnid_table));
  (*new_table)->entry =
    (asid_nnid_table_entry *) malloc(sizeof(asid_nnid_table_entry) * table_size);
  (*new_table)->size = table_size;

  for (i = 0; i < table_size; i++) {
    // Create the configuration region
    (*new_table)->entry[i].asid_nnid =
      (nn_configuration *) malloc(configs_per_entry * sizeof(nn_configuration));
    (*new_table)->entry[i].asid_nnid->config = NULL;
    (*new_table)->entry[i].num_configs = configs_per_entry;
    (*new_table)->entry[i].num_valid = 0;

    // Create the io region
    (*new_table)->entry[i].transaction_io = (io *) malloc(sizeof(io));
    (*new_table)->entry[i].transaction_io->header = 0;
    (*new_table)->entry[i].transaction_io->input = (queue *) malloc(sizeof(queue));
    (*new_table)->entry[i].transaction_io->output = (queue *) malloc(sizeof(queue));
    construct_queue(&(*new_table)->entry[i].transaction_io->input, 16);
    construct_queue(&(*new_table)->entry[i].transaction_io->output, 16);
  }

}

void asid_nnid_table_destroy(asid_nnid_table ** old_table) {
  int i, j;
  for (i = 0; i < (*old_table)->size; i++) {
    // Destroy the configuration region
    for (j = 0; j < (*old_table)->entry[i].num_valid; j++)
      if ((*old_table)->entry[i].asid_nnid[j].config != NULL)
        free((*old_table)->entry[i].asid_nnid[j].config);
    free((*old_table)->entry[i].asid_nnid);

    // Destroy the IO region
    destroy_queue(&(*old_table)->entry[i].transaction_io->input);
    destroy_queue(&(*old_table)->entry[i].transaction_io->output);
    free((*old_table)->entry[i].transaction_io);
  }

  free((*old_table)->entry);
  free(*old_table);
}

int attach_nn_configuration(asid_nnid_table ** table, asid_type asid,
                            const char * file_name) {
  int file_size, nnid;
  FILE *fp;

  if (asid >= (*table)->size) {
    return -1;
  }

  if ((*table)->entry[asid].num_valid == (*table)->entry[asid].num_configs) {
    return -1;
  }

  // Open the file and find out how big it is so that we can allocate
  // the correct amount of space
  if (!(fp = fopen(file_name, "rb"))) {
    return -1;
  }

  nnid = (*table)->entry[asid].num_valid;

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

  (*table)->entry[asid].asid_nnid[nnid].size = file_size;

  // Compute the elements per block as set in the neural network
  // configuration and write this into the ASID--NNID Table Entry
  uint64_t block_64;
  fread(&block_64, sizeof(block_64), 1, fp);
  fseek(fp, 0L, SEEK_SET);
  block_64 = (block_64 >> 4) & 3;
  (*table)->entry[asid].asid_nnid[nnid].elements_per_block = 1 << (block_64 + 2);

  // Allocate space for this configuraiton
  (*table)->entry[asid].asid_nnid[nnid].config =
    (xlen_t *) malloc(file_size * sizeof(xlen_t));
  // Write the configuration
  fread((*table)->entry[asid].asid_nnid[nnid].config, sizeof(xlen_t),
        file_size, fp);

  fclose(fp);
  return ++(*table)->entry[asid].num_valid;
}

int attach_garbage(asid_nnid_table ** table, asid_type asid) {

  int nnid;

  if (asid >= (*table)->size) {
    return -1;
  }
  if ((*table)->entry[asid].num_valid == (*table)->entry[asid].num_configs) {
    return -1;
  }

  nnid = (*table)->entry[asid].num_valid;
  (*table)->entry[asid].asid_nnid[nnid].size = 0;
  (*table)->entry[asid].asid_nnid[nnid].config = NULL;

  return ++(*table)->entry[asid].num_valid;
}

int attach_nn_configuration_array(asid_nnid_table ** table, uint16_t asid,
                                  const xlen_t * array, size_t size) {
  int nnid;

  // Fail if we've run out of space
  if ((*table)->entry[asid].num_valid == (*table)->entry[asid].num_configs) {
    return -1;
  }

  // The NNID is the index into the ASID--NNID array. The NNID is
  // implict here as it is the _next_ unallocated entry in the
  // ASID--NNID array.
  nnid = (*table)->entry[asid].num_valid;

  // Allocate memory for the array and copy in the input array. Update
  // the size following.
  (*table)->entry[asid].asid_nnid[nnid].config =
    (xlen_t *) malloc(size * sizeof(xlen_t));
  memcpy((*table)->entry[asid].asid_nnid[nnid].config, array,
         size * sizeof(xlen_t));
  (*table)->entry[asid].asid_nnid[nnid].size = size;

  // Return the new number of valid entries
  return ++(*table)->entry[asid].num_valid;
}
