#include <stdio.h>

#include "fann.h"

int main (int argc, char * argv[]) {
  int i, j, k, num_epochs;
  struct fann * ann;
  struct fann_train_data * data;
  fann_type * calc_out;

  if (argc != 4) {
    printf("[ERROR] Wrong number of input arguments\n");
    printf("Usage: fann-train <net> <training file> <num epochs>\n");
    return -1;
  }

  ann = fann_create_from_file(argv[1]);
  data = fann_read_train_from_file(argv[2]);
  num_epochs = atoi(argv[3]);

  ann->training_algorithm = FANN_TRAIN_BATCH;

  for (j = 0; j < num_epochs; j++) {
    fann_train_epoch(ann, data);
    for (i = 0; i < fann_length_train_data(data); i++) {
      fann_reset_MSE(ann);
      calc_out = fann_test(ann, data->input[i], data->output[i]);
      printf("[INFO] ");
      for (k = 0; k < data->num_input; k++) {
        printf("%8.5f ", data->input[i][k]);
      }
      for (k = 0; k < data->num_output; k++) {
        printf("%8.5f ", calc_out[k]);
      }
      if (i < fann_length_train_data(data) - 1)
        printf("\n");
    }
    printf("%8.5f\n\n", fann_get_MSE(ann));
  }


  fann_destroy(ann);
  fann_destroy_train(data);

  return 0;
}
