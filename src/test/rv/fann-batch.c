#include <stdint.h>
#include <stdio.h>
#include <math.h>

#include "fixedfann.h"
#include "xfiles.h"

void usage() {
  printf("Usage: fann-batch [binary config] [FANN training file] [binary point]");
}

int main (int argc, char * argv[]) {
  int exit_code = 0, max_epochs = 10000;
  struct fann_train_data * data = NULL;

  // Check that we have the correct number of inputs
  if (argc != 4) {
    fprintf(stderr, "[ERROR] Wrong number of inputs\n");
    usage();
    exit_code = -1;
    goto done;
  }

  // Create the ASID--NNID Table
  asid_nnid_table * table = NULL;
  asid_nnid_table_create(&table, 4, 17);
  set_antp(table);

  // Populate the ASID--NNID Table
  asid_type asid = 1;
  nnid_type nnid = 0;
  attach_nn_configuration(&table, asid, argv[1]);
  set_asid(asid);
  asid_nnid_table_info(table);

  // Read in data from the training file
  data = fann_read_train_from_file(argv[2]);
  if (data == NULL) {
    exit_code = -2;
    goto done;
  }

  float multiplier = pow(2, atoi(argv[3]));

  // Train on the provided data
  int epoch, item, i, bits_failing;
  float bit_fail_limit = 0.05;
  element_type * outputs = NULL;
  tid_type tid;

  outputs = (element_type *) malloc(data->num_output * sizeof(element_type));
  printf("[INFO] fann_length_train_data(data) = %d\n",
         fann_length_train_data(data));
  for (epoch = 0; epoch < max_epochs; epoch++) {
    // Run one training epoch
    for (item = 0; item < fann_length_train_data(data); item++) {
      // First item in a batch. Handle setup.
      if (item == 0) {
        tid = new_write_request(nnid, 2, 0);
        write_register(tid, xfiles_reg_batch_items, fann_length_train_data(data));
        write_register(tid, xfiles_reg_learning_rate,
                       (int32_t)((0.7 / fann_length_train_data(data)) * multiplier));
        write_register(tid, xfiles_reg_weight_decay_lambda, 0);
      }

      // Write the output and input data
      write_data_train_incremental(tid, (element_type *) data->input[item],
                                   (element_type *) data->output[item],
                                   data->num_input, data->num_output);

      // Blocking read
      read_data_spinlock(tid, outputs, data->num_output);
    }

    // Check the outputs
    bits_failing = 0;
    for (item = 0; item < fann_length_train_data(data); item++) {
      tid = new_write_request(nnid, 0, 0);
      write_data(tid, (element_type *) data->input[item], data->num_input);
      read_data_spinlock(tid, outputs, data->num_output);

      printf("[INFO] ");
      for (i = 0; i < data->num_input; i++) {
        printf("%8.5f ", ((float)data->input[item][i]) / multiplier);
      }

      for (i = 0; i < data->num_output; i++) {
        printf("%8.5f ", ((float)outputs[i])/multiplier);
        bits_failing +=
          fabs((float)(outputs[i] - data->output[item][i]) / multiplier) >
          bit_fail_limit;
      }

      if (item < fann_length_train_data(data) - 1)
        printf("\n");
    }

    printf("%5d\n\n", epoch);
    if (bits_failing == 0)
      goto done;
  }

  // Free memory and finish
 done:
  if (data != NULL)
    fann_destroy_train(data);
  if (table != NULL)
    asid_nnid_table_destroy(&table);
  if (outputs != NULL)
    free(outputs);
  return exit_code;
}
