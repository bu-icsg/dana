#include <stdio.h>
#include <getopt.h>
#include <string.h>

#include "fann.h"

static char * usage_message =
  "fann-train -n[config] -t[train file] [options]\n"
  "Run batch training on a specific neural network and training file.\n"
  "\n"
  "Options:\n"
  "  -b, --video-data           generate a trace of execution over time"
  "  -c, --stat-cups            print information about the # of connectsion\n"
  "  -d, --num-batch-items      number of batch items to use\n"
  "  -e, --max-epochs           the epoch limit (default 10k)\n"
  "  -f, --bit-fail-limit       sets the bit fail limit (default 0.05)\n"
  "  -g, --mse-fail-limit       sets the maximum MSE (default -1, i.e., off)\n"
  "  -h, --help                 print this help and exit\n"
  "  -i, --id                   numeric id to use for printing data (default 0)\n"
  "  -l, --stat-last            print last epoch number statistic\n"
  "  -m, --stat-mse             print mse statistics (optional arg: MSE period)\n"
  "  -n, --nn-config            the binary NN configuration to use\n"
  "  -o, --stat-bit-fail        print bit fail percent (optional arg: period)\n"
  "  -q, --stat-percent-correct print the percent correct (optional arg: period)\n"
  "  -r, --learning-rate        set the learning rate (default 0.7)\n"
  "  -t, --train-file           the fixed point FANN training file to use\n"
  "  -v, --verbose              turn on per-item inputs/output printfs\n"
  "  -z, --ignore-limits        continue blindly ignoring bit fail/mse limits"
  "\n"
  "-n and -t are required\n";

void usage () {
  printf("Usage: %s", usage_message);
}

int main (int argc, char * argv[]) {
  int i, epoch, k, num_bits_failing, num_correct;
  int max_epochs = 10000, exit_code = 0, batch_items = -1;
  int flag_cups = 0, flag_last = 0, flag_mse = 0, flag_verbose = 0,
    flag_bit_fail = 0, flag_ignore_limits = 0, flag_percent_correct = 0;
  int mse_reporting_period = 1, bit_fail_reporting_period = 1,
    percent_correct_reporting_period = 1;
  float bit_fail_limit = 0.05, mse_fail_limit = -1.0;
  double learning_rate = 0.7;
  char id[100] = "0";
  char * file_video_string = NULL;
  FILE * file_video = NULL;
  struct fann * ann = NULL;
  struct fann_train_data * data = NULL;
  fann_type * calc_out;

  char * file_nn = NULL, * file_train = NULL;
  int c;
  while (1) {
    static struct option long_options[] = {
      {"video-data",           required_argument, 0, 'b'},
      {"stat-cups",            no_argument,       0, 'c'},
      {"num-batch-items",      required_argument, 0, 'd'},
      {"max-epochs",           required_argument, 0, 'e'},
      {"bit-fail-limit",       required_argument, 0, 'f'},
      {"mse-fail-limit",       required_argument, 0, 'g'},
      {"help",                 no_argument,       0, 'h'},
      {"id",                   required_argument, 0, 'i'},
      {"stat-last",            no_argument,       0, 'l'},
      {"stat-mse",             optional_argument, 0, 'm'},
      {"nn-config",            required_argument, 0, 'n'},
      {"stat-bit-fail",        optional_argument, 0, 'o'},
      {"stat-percent-correct", optional_argument, 0, 'q'},
      {"learning-rate",        required_argument, 0, 'r'},
      {"train-file",           required_argument, 0, 't'},
      {"verbose",              no_argument,       0, 'v'},
      {"ignore-limits",        no_argument,       0, 'z'}
    };
    int option_index = 0;
     c = getopt_long (argc, argv, "b:cd:e:f:g:hi:lm::n:o::q::r:t:vz",
                     long_options, &option_index);
    if (c == -1)
      break;
    switch (c) {
    case 'b':
      file_video_string = optarg;
      break;
    case 'c':
      flag_cups = 1;
      break;
    case 'd':
      batch_items = atoi(optarg);
      break;
    case 'e':
      max_epochs = atoi(optarg);
      break;
    case 'f':
      bit_fail_limit = atof(optarg);
      break;
    case 'g':
      mse_fail_limit = atof(optarg);
      break;
    case 'h':
      usage();
      exit_code = 0;
      goto bail;
      break;
    case 'i':
      strcpy(id, optarg);
      break;
    case 'l':
      flag_last = 1;
      break;
    case 'm':
      if (optarg)
        mse_reporting_period = atoi(optarg);
      flag_mse = 1;
      break;
    case 'n':
      file_nn = optarg;
      break;
    case 'o':
      if (optarg)
        bit_fail_reporting_period = atoi(optarg);
      flag_bit_fail = 1;
      break;
    case 'q':
      if (optarg)
        percent_correct_reporting_period = atoi(optarg);
      flag_percent_correct = 1;
      break;
    case 'r':
      learning_rate = atof(optarg);
      break;
    case 't':
      file_train = optarg;
      break;
    case 'v':
      flag_verbose = 1;
      break;
    case 'z':
      flag_ignore_limits = 1;
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
  if (batch_items != -1 && batch_items < data->num_data)
    data->num_data = batch_items;
  enum fann_activationfunc_enum af =
    fann_get_activation_function(ann, ann->last_layer - ann->first_layer -1, 0);

  ann->training_algorithm = FANN_TRAIN_BATCH;
  ann->learning_rate = learning_rate;

  if (file_video_string != NULL)
    file_video = fopen(file_video_string, "w");

  double mse;
  for (epoch = 0; epoch < max_epochs; epoch++) {
    fann_train_epoch(ann, data);
    num_bits_failing = 0;
    num_correct = 0;
    fann_reset_MSE(ann);
    for (i = 0; i < fann_length_train_data(data); i++) {
      calc_out = fann_test(ann, data->input[i], data->output[i]);
      if (flag_verbose) {
        printf("[INFO] ");
        for (k = 0; k < data->num_input; k++) {
          printf("%8.5f ", data->input[i][k]);
        }
      }
      int correct = 1;
      for (k = 0; k < data->num_output; k++) {
        if (flag_verbose)
          printf("%8.5f ", calc_out[k]);
        num_bits_failing +=
          fabs(calc_out[k] - data->output[i][k]) > bit_fail_limit;
        if (fabs(calc_out[k] - data->output[i][k]) > bit_fail_limit)
          correct = 0;
        if (file_video)
          fprintf(file_video, "%f ", calc_out[k]);
      }
      if (file_video)
        fprintf(file_video, "\n");
      num_correct += correct;
      if (flag_verbose) {
        if (i < fann_length_train_data(data) - 1)
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
      printf("[STAT] epoch %d id %s mse %8.8f\n", epoch, id, mse);
    }
    if (flag_bit_fail && (epoch % bit_fail_reporting_period == 0))
      printf("[STAT] epoch %d id %s bfp %8.8f\n", epoch, id,
             1 - (double) num_bits_failing / data->num_output /
             fann_length_train_data(data));
    if (flag_percent_correct && (epoch % percent_correct_reporting_period == 0))
      printf("[STAT] epoch %d id %s perc %8.8f\n", epoch, id,
             (double) num_correct / fann_length_train_data(data));
    if (!flag_ignore_limits && (num_bits_failing == 0 || mse < mse_fail_limit))
      goto finish;
    // printf("%8.5f\n\n", fann_get_MSE(ann));
  }

 finish:
  if (flag_last)
    printf("[STAT] x 0 id %s epoch %d\n", id, epoch);
  if (flag_cups)
    printf("[STAT] x 0 id %s cups %d / ?\n", id,
           epoch * fann_get_total_connections(ann));

 bail:
  if (ann != NULL)
    fann_destroy(ann);
  if (data != NULL)
    fann_destroy_train(data);
  if (file_video != NULL)
    fclose(file_video);

  return exit_code;
}
