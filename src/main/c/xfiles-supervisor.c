// See LICENSE for license details.

#include "src/main/c/xfiles-supervisor.h"

uint64_t set_asid (asid_t asid) {
  asm volatile ("custom0 0, %[rs1], 0, 0"
                :: [rs1] "r" (asid));
  return 0;
}

uint64_t set_antp (asid_nnid_table_t * os_antp) {
  asm volatile ("custom0 0, %[rs1], %[rs2], 1"
                :: [rs1] "r" (os_antp->entry), [rs2] "r" (os_antp->size));
  return 0;
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

void asid_nnid_table_create(asid_nnid_table_t ** new_table, size_t table_size,
                            size_t configs_per_entry) {
  int i;

  // Allocate space for the table
  *new_table = (asid_nnid_table_t *) malloc(sizeof(asid_nnid_table_t));
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

void asid_nnid_table_destroy(asid_nnid_table_t ** old_table) {
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

void asid_nnid_table_info(asid_nnid_table_t * table) {
  int i, j;
  printk("[INFO] 0x%lx <- Table Head\n", (uint64_t) table);
  printk("[INFO]   |-> 0x%lx: size:                     0x%lx\n",
         (uint64_t) &table->size,
         (uint64_t) table->size);
  printk("[INFO]       0x%lx: * entry:                  0x%lx\n",
         (uint64_t) &table->entry,
         (uint64_t) table->entry);
  for (i = 0; i < table->size; i++) {
    printk("[INFO]         |-> [%0d] 0x%lx: num_configs:    0x%lx\n", i,
           (uint64_t) &table->entry[i].num_configs,
           (uint64_t) table->entry[i].num_configs);
    printk("[INFO]         |       0x%lx: num_valid:      0x%lx\n",
           (uint64_t) &table->entry[i].num_valid,
           (uint64_t) table->entry[i].num_valid);
    printk("[INFO]         |       0x%lx: asid_nnid:      0x%lx\n",
           (uint64_t) &table->entry[i].asid_nnid,
           (uint64_t) table->entry[i].asid_nnid);
    // Dump the `nn_configuration`
    for (j = 0; j < table->entry[i].num_valid; j++) {
      printk("[INFO]         |         |-> [%0d] 0x%lx: size:             0x%lx\n", j,
             (uint64_t) &table->entry[i].asid_nnid[j].size,
             (uint64_t) table->entry[i].asid_nnid[j].size);
      printk("[INFO]         |         |       0x%lx: elements_per_block: 0d%ld\n",
             (uint64_t) &table->entry[i].asid_nnid[j].elements_per_block,
             (uint64_t) table->entry[i].asid_nnid[j].elements_per_block);
      printk("[INFO]         |         |       0x%lx: * config:           0x%lx\n",
             (uint64_t) &table->entry[i].asid_nnid[j].config,
             (uint64_t) table->entry[i].asid_nnid[j].config);
    }
    // Back to `asid_nnid_table_entry`
    printk("[INFO]         |       0x%lx: transaction_io: 0x%lx\n",
           (uint64_t) &table->entry[i].transaction_io,
           (uint64_t) table->entry[i].transaction_io);
    // Dump the `io`
    printk("[INFO]         |         |-> 0x%lx: header:   0x%lx\n",
           (uint64_t) &table->entry[i].transaction_io->header,
           (uint64_t) table->entry[i].transaction_io->header);
    printk("[INFO]         |         |   0x%lx: * input:  0x%lx\n",
           (uint64_t) &table->entry[i].transaction_io->input,
           (uint64_t) table->entry[i].transaction_io->input);
    printk("[INFO]         |         |   0x%lx: * output: 0x%lx\n",
           (uint64_t) &table->entry[i].transaction_io->output,
           (uint64_t) table->entry[i].transaction_io->output);
  }
}

// int attach_nn_configuration(asid_nnid_table_t ** table, asid_t asid,
//                             const char * file_name) {

//   if (asid >= (*table)->size) {
//     printk("[ERROR] Cannot append NN because ASID is too large\n");
//     return -1;
//   }
//   if ((*table)->entry[asid].num_valid == (*table)->entry[asid].num_configs) {
//     printk("[ERROR] Cannot append configuration because all slots allocated\n");
//     return -1;
//   }

//   nnid = (*table)->entry[asid].num_valid;

//   // Kludge until fseek works again
//   // fseek(fp, 0L, SEEK_END);
//   char c = 'a';
//   while (fread(&c, 1, 1, fp)) {}

//   file_size = ftell(fp) / sizeof(x_len);
//   file_size += (ftell(fp) % sizeof(x_len)) ? 1 : 0;
//   fseek(fp, 0L, SEEK_SET);

//   if (file_size <= 0) {
//     printk("[ERROR] Found nonsensical file size (%d) for file %s\n",
//            file_size, file_name);
//     return -1;
//   }

//   (*table)->entry[asid].asid_nnid[nnid].size = file_size;

//   // Compute the elements per block as set in the neural network
//   // configuration and write this into the ASID--NNID Table Entry
//   uint64_t block_64;
//   fread(&block_64, sizeof(block_64), 1, fp);
//   fseek(fp, 0L, SEEK_SET);
//   block_64 = (block_64 >> 4) & 3;
//   (*table)->entry[asid].asid_nnid[nnid].elements_per_block = 1 << (block_64 + 2);

//   // Allocate space for this configuraiton
//   (*table)->entry[asid].asid_nnid[nnid].config =
//       (x_len *) malloc(file_size * sizeof(x_len));
//   // Write the configuration
//   fread((*table)->entry[asid].asid_nnid[nnid].config, sizeof(x_len),
//         file_size, fp);

//   fclose(fp);
//   return ++(*table)->entry[asid].num_valid;
// }

// int attach_garbage(asid_nnid_table_t ** table, uint16_t asid) {

//   int nnid;

//   if (asid >= (*table)->size) {
//     printk("[ERROR] Cannot append NN because ASID is too large\n");
//     return -1;
//   }
//   if ((*table)->entry[asid].num_valid == (*table)->entry[asid].num_configs) {
//     printk("[ERROR] Cannot append configuration because all slots allocated\n");
//     return -1;
//   }

//   nnid = (*table)->entry[asid].num_valid;
//   (*table)->entry[asid].asid_nnid[nnid].size = 0;
//   (*table)->entry[asid].asid_nnid[nnid].config = NULL;

//   return ++(*table)->entry[asid].num_valid;
// }

// int attach_nn_configuration_array(uint16_t asid, const x_len * array,
//                                   size_t size) {
//   int nnid;

//   // Fail if we've run out of space
//   if ((*table)->entry[asid].num_valid == (*table)->entry[asid].num_configs) {
//     printk("[ERROR] Cannot append configuration because all slots allocated\n");
//     return -1;
//   }

//   // The NNID is the index into the ASID--NNID array. The NNID is
//   // implict here as it is the _next_ unallocated entry in the
//   // ASID--NNID array.
//   nnid = (*table)->entry[asid].num_valid;

//   // Allocate memory for the array and copy in the input array. Update
//   // the size following.
//   (*table)->entry[asid].asid_nnid[nnid].config =
//       (x_len *) malloc(size * sizeof(x_len));
//   memcpy((*table)->entry[asid].asid_nnid[nnid].config, array,
//          size * sizeof(x_len));
//   (*table)->entry[asid].asid_nnid[nnid].size = size;

//   // Return the new number of valid entries
//   return ++(*table)->entry[asid].num_valid;
// }
