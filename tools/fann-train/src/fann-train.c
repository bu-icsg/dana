#include <stdio.h>
#include <getopt.h>

#include "fann.h"

static char * usage_message =
  "fann-train -n[config] -t[train file] [options]\n"
  "Run batch training on a specific neural network and training file.\n"
  "\n"
  "Options:\n"
  "  -e, --max-epochs           the epoch limit (default 10k)\n"
  "  -f, --bit-fail-limit       sets the bit fail limit (default 0.05)\n"
  "  -h, --help                 print this help and exit\n"
  "  -i, --id                   numeric id to use for printing data (default 0)\n"
  "  -l, --stat-last            print last epoch number statistic\n"
  "  -m, --stat-mse             print mse statistics\n"
  "  -n, --nn-config            the binary NN configuration to use\n"
  "  -t, --train-file           the fixed point FANN training file to use\n"
  "  -v, --verbose              turn on per-item inputs/output printfs\n"
  "\n"
  "-n and -t are required\n";

void usage () {
  printf("Usage: %s", usage_message);
}

int main (int argc, char * argv[]) {
  int i, epoch, k, num_bits_failing;
  int max_epochs = 10000, exit_code = 0, id = 0;
  int flag_last = 0, flag_mse = 0, flag_verbose = 0;
  float bit_fail_limit = 0.05;
  struct fann * ann = NULL;
  struct fann_train_data * data = NULL;
  fann_type * calc_out;

  char * file_nn = NULL, * file_train = NULL;
  int c;
  while (1) {
    static struct option long_options[] = {
      {"max-epochs",     required_argument, 0, 'e'},
      {"bit-fail-limit", required_argument, 0, 'f'},
      {"help",           no_argument,       0, 'h'},
      {"id",             required_argument, 0, 'i'},
      {"stat-last",      no_argument,       0, 'l'},
      {"stat-mse",       no_argument,       0, 'm'},
      {"nn-config",      required_argument, 0, 'n'},
      {"train-file",     required_argument, 0, 't'},
      {"verbose",        no_argument,       0, 'v'}
    };
    int option_index = 0;
    c = getopt_long (argc, argv, "e:f:hi:lmn:t:v", long_options, &option_index);
    if (c == -1)
      break;
    switch (c) {
    case 'e':
      max_epochs = atoi(optarg);
      break;
    case 'f':
      bit_fail_limit = atof(optarg);
      break;
    case 'h':
      usage();
      exit_code = 0;
      goto bail;
      break;
    case 'i':
      id = atoi(optarg);
      break;
    case 'l':
      flag_last = 1;
      break;
    case 'm':
      flag_mse = 1;
      break;
    case 'n':
      file_nn = optarg;
      break;
    case 't':
      file_train = optarg;
      break;
    case 'v':
      flag_verbose = 1;
      break;
    }
  };

  // Make sure there aren't any arguments left over
  if (optind != argc) {
    fprintf(stderr, "[ERROR] Bad argument\n\n");
    usage();
    exit_code = -1;
    goto bail;
  }

  // Make sure we have all required inputs
  if (file_nn == NULL || file_train == NULL) {
    fprintf(stderr, "[ERROR] Missing required input argument\n\n");
    usage();
    exit_code = -1;
    goto bail;
  }

  ann = fann_create_from_file(file_nn);
  data = fann_read_train_from_file(file_train);

  ann->training_algorithm = FANN_TRAIN_BATCH;

  for (epoch = 0; epoch < max_epochs; epoch++) {
    fann_train_epoch(ann, data);
    num_bits_failing = 0;
    for (i = 0; i < fann_length_train_data(data); i++) {
      fann_reset_MSE(ann);
      calc_out = fann_test(ann, data->input[i], data->output[i]);
      if (flag_verbose) {
        printf("[INFO] ");
        for (k = 0; k < data->num_input; k++) {
          printf("%8.5f ", data->input[i][k]);
        }
      }
      for (k = 0; k < data->num_output; k++) {
        if (flag_verbose)
          printf("%8.5f ", calc_out[k]);
        num_bits_failing += fabs(calc_out[k] - data->output[i][k]) > bit_fail_limit;
      }
      if (flag_verbose) {
        if (i < fann_length_train_data(data) - 1)
          printf("\n");
      }
    }
    if (flag_verbose)
      printf("%5d\n\n", epoch);
    if (flag_mse)
      printf("[STAT] epoch %d id %d mse %8.8f\n", epoch, id, fann_get_MSE(ann));
    if (num_bits_failing == 0)
      goto finish;
    // printf("%8.5f\n\n", fann_get_MSE(ann));
  }

 finish:
  if (flag_last)
    printf("[STAT] x 0 id %d epoch %d\n", id, epoch);

 bail:
  if (ann != NULL)
    fann_destroy(ann);
  if (data != NULL)
    fann_destroy_train(data);

  return exit_code;
}
