#include <stdint.h>
#include <stdio.h>
#include <math.h>
#include <getopt.h>

#include "fixedfann.h"
#include "xfiles.h"

static char * usage_message =
  "fann-batch -n[config] -t[train file] -b[binary point] [options]\n"
  "Run batch training on a specific neural network and training file.\n"
  "\n"
  "Options:\n"
  "  -b, --binary-point         the binary point (number of fractional bits)\n"
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
  "-n, -t, and -b are required\n";

void usage() {
  printf("Usage: %s", usage_message);
}

int main (int argc, char * argv[]) {
  int exit_code = 0, max_epochs = 10000, bits_failing = -1, id = 0;
  int flag_last = 0, flag_mse = 0, flag_verbose = 0;
  float bit_fail_limit = 0.05;
  struct fann_train_data * data = NULL;

  char * file_nn = NULL, * file_train = NULL;
  int binary_point = 0, c;
  while (1) {
    static struct option long_options[] = {
      {"binary-point",   required_argument, 0, 'b'},
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
    c = getopt_long (argc, argv, "b:e:f:hi:mn:t:v", long_options, &option_index);
    if (c == -1)
      break;
    switch (c) {
    case 'b':
      binary_point = atoi(optarg);
      break;
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
  if (file_nn == NULL || file_train == NULL || binary_point == -1) {
    fprintf(stderr, "[ERROR] Missing required input argument\n\n");
    usage();
    exit_code = -1;
    goto bail;
  }

  // Create the ASID--NNID Table
  asid_nnid_table * table = NULL;
  asid_nnid_table_create(&table, 4, 17);
  set_antp(table);

  // Populate the ASID--NNID Table
  asid_type asid = 1;
  nnid_type nnid = 0;
  attach_nn_configuration(&table, asid, file_nn);
  set_asid(asid);

  // Read in data from the training file
  data = fann_read_train_from_file(file_train);
  if (data == NULL) {
    exit_code = -2;
    goto bail;
  }

  float multiplier = pow(2, binary_point);

  // Train on the provided data
  int epoch, item, i;
  element_type * outputs = NULL;
  tid_type tid;
  float mse;

  outputs = (element_type *) malloc(data->num_output * sizeof(element_type));
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
    mse = 0.0;
    float error;
    for (item = 0; item < fann_length_train_data(data); item++) {
      tid = new_write_request(nnid, 0, 0);
      write_data(tid, (element_type *) data->input[item], data->num_input);
      read_data_spinlock(tid, outputs, data->num_output);

      if (flag_verbose) {
        printf("[INFO] ");
        for (i = 0; i < data->num_input; i++) {
          printf("%8.5f ", ((float)data->input[item][i]) / multiplier);
        }
      }

      for (i = 0; i < data->num_output; i++) {
        if (flag_verbose)
          printf("%8.5f ", ((float)outputs[i])/multiplier);
        bits_failing +=
          fabs((float)(outputs[i] - data->output[item][i]) / multiplier) >
          bit_fail_limit;
        error = (float)(outputs[i] - data->output[item][i]) / multiplier;
        mse += error * error;
      }

      if (flag_verbose) {
        if (item < fann_length_train_data(data) - 1)
          printf("\n");
      }
    }

    mse /= fann_length_train_data(data);

    if (flag_verbose)
      printf("%5d\n\n", epoch);
    if (flag_mse)
      printf("[STAT] epoch %d id %d bp %d mse %8.8f\n", epoch, id, binary_point, mse);
    if (bits_failing == 0)
      goto finish;
  }

  // Print overall statistics in a parser-friendly way
 finish:
  printf("# [STAT] fann-batch-id%d-bit-fail %d\n", id, bits_failing);
  printf("# [STAT] fann-batch-id%d-final-epoch %d\n", id, epoch);
  if (flag_last)
    printf("[STAT] epoch %d id %d\n", epoch, id);

  // Free memory
 bail:
  if (data != NULL)
    fann_destroy_train(data);
  if (table != NULL)
    asid_nnid_table_destroy(&table);
  if (outputs != NULL)
    free(outputs);
  return exit_code;
}
