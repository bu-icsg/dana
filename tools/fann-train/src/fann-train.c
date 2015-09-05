#include <stdio.h>

#include "fann.h"

int main (int argc, char * argv[]) {
  int i, j, k, max_epochs, num_bits_failing;
  struct fann * ann;
  struct fann_train_data * data;
  fann_type * calc_out, bit_fail_limit;

  if (argc != 5) {
    printf("[ERROR] Wrong number of input arguments\n");
    printf("Usage: fann-train <net> <training file> <bit fail limit> <max epochs>\n");
    return -1;
  }

  ann = fann_create_from_file(argv[1]);
  data = fann_read_train_from_file(argv[2]);
  bit_fail_limit = atof(argv[3]);
  max_epochs = atoi(argv[4]);

  ann->training_algorithm = FANN_TRAIN_BATCH;

  for (j = 0; j < max_epochs; j++) {
    fann_train_epoch(ann, data);
    num_bits_failing = 0;
    for (i = 0; i < fann_length_train_data(data); i++) {
      fann_reset_MSE(ann);
      calc_out = fann_test(ann, data->input[i], data->output[i]);
      printf("[INFO] ");
      for (k = 0; k < data->num_input; k++) {
        printf("%8.5f ", data->input[i][k]);
      }
      for (k = 0; k < data->num_output; k++) {
        printf("%8.5f ", calc_out[k]);
        num_bits_failing += fabs(calc_out[k] - data->output[i][k]) > bit_fail_limit;
      }
      if (i < fann_length_train_data(data) - 1)
        printf("\n");
    }
    printf("%5d\n\n", j);
    if (num_bits_failing == 0)
      goto done;
    // printf("%8.5f\n\n", fann_get_MSE(ann));
  }

 done:
  fann_destroy(ann);
  fann_destroy_train(data);

  return 0;
}
