// See LICENSE for license details.

#include <stdint.h>
#include <stdio.h>
#include <math.h>
#include <getopt.h>

#include "submodules/fann/src/include/fann.h"
#include "src/main/c/xfiles.h"

#define read_csr(reg) ({ unsigned long __tmp; \
  asm volatile ("csrr %0, " #reg : "=r"(__tmp)); \
  __tmp; })

static char * usage_message =
  "fann-soft -n[config] -t[train file] -b[binary point] [options]\n"
  "Run batch training on a specific neural network and training file.\n"
  "\n"
  "Options:\n"
  "  -c, --stat-cycles          print the total number of cycles in the ROI\n"
  "  -e, --max-epochs           the epoch limit (default 10k)\n"
  "  -f, --bit-fail-limit       sets the bit fail limit (default 0.05)\n"
  "  -g, --mse-fail-limit       sets the maximum MSE (default -1, i.e., off)\n"
  "  -h, --help                 print this help and exit\n"
  "  -i, --id                   numeric id to use for printing data (default 0)\n"
  "  -l, --stat-last            print last epoch number statistic\n"
  "  -m, --stat-mse             print mse statistics (optional arg: MSE period)\n"
  "  -n, --nn-config            the binary NN configuration to use\n"
  "  -p, --performance-mode     runs until an epoch limit, all checks disabled\n"
  "  -t, --train-file           the fixed point FANN training file to use\n"
  "  -v, --verbose              turn on per-item inputs/output printfs\n"
  "\n"
  "Flags -n and -t are required.\n"
  "When gathering data related to connection updates per second, -p\n"
  "should always be used as this diables all unnecessary control statements.\n";

void usage() {
  printf("Usage: %s", usage_message);
}

int main (int argc, char * argv[]) {
  int exit_code = 0, max_epochs = 10000, bits_failing = -1, id = 0;
  int flag_cycles = 0, flag_last = 0, flag_mse = 0, flag_performance = 0,
    flag_verbose = 0;
  int mse_reporting_period = 1;
  uint64_t cycles;
  double bit_fail_limit = 0.05, mse_fail_limit = -1.0;
  struct fann_train_data * data = NULL;
  struct fann * ann = NULL;

  char * file_nn = NULL, * file_train = NULL;
  int c;
  while (1) {
    static struct option long_options[] = {
      {"stat-cycles",      no_argument,       0, 'c'},
      {"max-epochs",       required_argument, 0, 'e'},
      {"bit-fail-limit",   required_argument, 0, 'f'},
      {"mse-fail-limit",   required_argument, 0, 'g'},
      {"help",             no_argument,       0, 'h'},
      {"id",               required_argument, 0, 'i'},
      {"stat-last",        no_argument,       0, 'l'},
      {"stat-mse",         optional_argument, 0, 'm'},
      {"nn-config",        required_argument, 0, 'n'},
      {"performance-mode", no_argument,       0, 'p'},
      {"train-file",       required_argument, 0, 't'},
      {"verbose",          no_argument,       0, 'v'}
    };
    int option_index = 0;
    c = getopt_long (argc, argv, "ce:f:g:hi:lm::n:pt:v", long_options, &option_index);
    if (c == -1)
      break;
    switch (c) {
    case 'c': flag_cycles = 1; break;
    case 'e': max_epochs = atoi(optarg); break;
    case 'f': bit_fail_limit = atof(optarg); break;
    case 'g': mse_fail_limit = atof(optarg); break;
    case 'h': usage(); exit_code = 0; goto bail;
    case 'i': id = atoi(optarg); break;
    case 'l': flag_last = 1; break;
    case 'm':
      if (optarg)
        mse_reporting_period = atoi(optarg);
      flag_mse = 1;
      break;
    case 'n': file_nn = optarg; break;
    case 'p': flag_performance = 1; break;
    case 't': file_train = optarg; break;
    case 'v': flag_verbose = 1; break;
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
  int batch_items = fann_length_train_data(data);
  enum fann_activationfunc_enum af =
    fann_get_activation_function(ann, ann->last_layer - ann->first_layer -1, 0);
  ann->training_algorithm = FANN_TRAIN_BATCH;

  int epoch, item, k;
  fann_type * calc_out;
  double mse;

  if (!flag_performance) {
    cycles = read_csr(0xc00);
    for (epoch = 0; epoch < max_epochs; epoch++) {
      fann_train_epoch(ann, data);
      fann_reset_MSE(ann);
      for (item = 0; item < fann_length_train_data(data); item++) {
        calc_out = fann_test(ann, data->input[item], data->output[item]);
        if (flag_verbose) {
          printf("[INFO] ");
          for (k = 0; k < data->num_input; k++) {
            printf("%8.5f ", data->input[item][k]);
          }
        }
        for (k = 0; k < data->num_output; k++) {
          if (flag_verbose)
            printf("%8.5f ", calc_out[k]);
          bits_failing += fabs(calc_out[k] - data->output[item][k]) > bit_fail_limit;
        }
        if (flag_verbose) {
          if (item < fann_length_train_data(data) - 1)
            printf("\n");
        }
      }
      if (flag_verbose)
        printf("%5d\n\n", epoch);
      if (flag_mse  && (epoch % mse_reporting_period == 0)) {
        mse = fann_get_MSE(ann);
        switch(af) {
          case FANN_LINEAR_PIECE_SYMMETRIC:
          case FANN_THRESHOLD_SYMMETRIC:
          case FANN_SIGMOID_SYMMETRIC:
          case FANN_SIGMOID_SYMMETRIC_STEPWISE:
          case FANN_ELLIOT_SYMMETRIC:
          case FANN_GAUSSIAN_SYMMETRIC:
          case FANN_SIN_SYMMETRIC:
          case FANN_COS_SYMMETRIC:
            mse *= 4.0;
          default:
            break;
        }
        printf("[STAT] epoch %d id %d mse %8.8f\n", epoch, id, mse);
      }
      if (bits_failing == 0 || mse < mse_fail_limit)
        break;
    }
  }
  else {
    cycles = read_csr(0xc00);
    for (epoch = 0; epoch < max_epochs; epoch++)
      fann_train_epoch(ann, data);
  }

  // Print overall statistics in a parser-friendly way
  cycles = read_csr(0xc00) - cycles;
  if (flag_last)
    printf("[STAT] id %d epoch %d\n", id, epoch);
  if (flag_cycles) {
    printf("[STAT] x 0 id %d cycles %ld\n", id, cycles);
    printf("[STAT] x 0 id %d CUPC %0.8f\n", id,
           (fann_get_total_connections(ann) * epoch * batch_items) /
           (double) cycles);
  }

  // Free memory
 bail:
  if (data != NULL)
    fann_destroy_train(data);
  if (ann != NULL)
    fann_destroy(ann);
  // if (outputs != NULL)
  //   free(outputs);
  return exit_code;

}
