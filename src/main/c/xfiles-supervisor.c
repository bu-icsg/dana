#include "xfiles.h"

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

void asid_nnid_table_create(asid_nnid_table ** new_table, int table_size,
                            int configs_per_entry) {
  int i;

  // Allocate space for the table
  *new_table = (asid_nnid_table *) malloc(sizeof(asid_nnid_table));
  (*new_table)->entry =
    (asid_nnid_table_entry *) malloc(sizeof(asid_nnid_table_entry) * table_size);
  (*new_table)->size = table_size;

  for (i = 0; i < table_size; i++) {
    // Create the configuration region
    (*new_table)->entry[i].asid_nnid =
      (nn_configuration *) malloc(configs_per_entry * sizeof(nn_configuration*));
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
      free((*old_table)->entry[i].asid_nnid->config);
    free((*old_table)->entry[i].asid_nnid);

    // Destroy the IO region
    destroy_queue(&(*old_table)->entry[i].transaction_io->input);
    destroy_queue(&(*old_table)->entry[i].transaction_io->output);
    free((*old_table)->entry[i].transaction_io);
  }

  free((*old_table)->entry);
  free(*old_table);
}

int attach_nn_configuration(asid_nnid_table ** table, uint16_t asid,
                            const char * file_name) {
  int file_size;
  FILE *fp;

  if (asid >= (*table)->size) {
    printf("[ERROR] Cannot append NN because ASID is too large\n");
    return -1;
  }
  if ((*table)->entry[asid].num_valid == (*table)->entry[asid].num_configs) {
    printf("[ERROR] Cannot append configuration because all slots allocated\n");
    return -1;
  }

  // Open the file and find out how big it is so that we can allocate
  // the correct amount of space
  fp = fopen(file_name, "rb");
  fseek(fp, 0L, SEEK_END);
  file_size = ftell(fp);
  fseek(fp, 0L, SEEK_SET);
  (*table)->entry[asid].asid_nnid->size = file_size;

  // Allocate space for this configuraiton
  (*table)->entry[asid].asid_nnid->config =
    (uint8_t *) malloc(file_size * sizeof(uint8_t));
  // Write the configuration
  fread((*table)->entry[asid].asid_nnid->config, sizeof(uint8_t),
        file_size, fp);

  fclose(fp);
  return ++(*table)->entry[asid].num_valid;
}

int main() {
  asid_nnid_table * table;

  // Create a table
  asid_nnid_table_create(&table, 4, 16);

  // Attach some NN configurations
  if (attach_nn_configuration(&table, 0, "/home/se/research_local/nn-hardware/workloads/data/rsa-fixed.16bin")<0) {
    printf("Failed to allocate\n");
    return -1;
  };
  if (attach_nn_configuration(&table, 1, "/home/se/research_local/nn-hardware/workloads/data/sobel-fixed.16bin")<0) {
    printf("Failed to allocate\n");
    return -1;
  };

  // Destroy everything
  asid_nnid_table_destroy(&table);

  return 0;
}
