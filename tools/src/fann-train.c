#include <stdio.h>
#include <getopt.h>
#include <string.h>

#include "submodules/fann/src/include/fann.h"

#define OPTARG_STAT_BIT_FAIL 1024

static char * usage_message =
    "fann-train -n[config] -t[train file] [options] [FILE]\n"
    "Run batch training on a specific neural network and training file.\n"
    "\n"
    "Options:\n"
    "  -b, --video-data           generate a trace of execution over time\n"
    "  --stat-cups                print information about the # of connectsion\n"
    "  -d, --num-batch-items      number of batch items to use\n"
    "  -e, --max-epochs           the epoch limit (default 10k)\n"
    "  -f, --bit-fail-limit       sets the bit fail limit (default 0.05)\n"
    "  -g, --mse-fail-limit       sets the maximum MSE (default -1, i.e., off)\n"
    "  -h, --help                 print this help and exit\n"
    "  -i, --id                   numeric id to use for printing data (default 0)\n"
    "  --stat-last                print last epoch number statistic\n"
    "  -m, --stat-mse             print mse statistics (optional arg: MSE period)\n"
    "  -n, --nn-config [FILE]     read FANN floating point network from FILE\n"
    "  -q, --stat-percent-correct print the percent correct (optional arg: period)\n"
    "  -r, --learning-rate        set the learning rate (default 0.7)\n"
    "  --stat-bit-fail            print bit fail percent (optional arg: period)\n"
    "  -t, --train-file [FILE]    read FANN training file FILE\n"
    "  --verbose                  turn on per-item inputs/output printfs\n"
    "  -x, --training-type        no arg: incremental, arg: use specific enum\n"
    "  --ignore-limits            continue blindly ignoring bit fail/mse limits"
    "\n"
    "Notes:\n"
    "  * The output FILE may be \"/dev/stdout\" or \"-\" to write to STDOUT\n";

void usage () {
  printf("Usage: %s", usage_message);
}

int main (int argc, char * argv[]) {
  int i, epoch, k, num_bits_failing, num_correct;
  int max_epochs = 10000, exit_code = 0, batch_items = -1;
  static int flag_cups, flag_last, flag_verbose, flag_ignore_limits;
  int flag_mse = 0, flag_bit_fail = 0, flag_percent_correct = 0;
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
  enum fann_train_enum type_training = FANN_TRAIN_BATCH;

  char * file_nn = NULL, * file_train = NULL, * file_out = NULL;
  int c;
  while (1) {
    static struct option long_options[] = {
      {"video-data",           required_argument, 0, 'b'},
      {"stat-cups",            no_argument,       &flag_cups, 1},
      {"num-batch-items",      required_argument, 0, 'd'},
      {"max-epochs",           required_argument, 0, 'e'},
      {"bit-fail-limit",       required_argument, 0, 'f'},
      {"mse-fail-limit",       required_argument, 0, 'g'},
      {"help",                 no_argument,       0, 'h'},
      {"id",                   required_argument, 0, 'i'},
      {"stat-last",            no_argument,       &flag_last, 1},
      {"stat-mse",             optional_argument, 0, 'm'},
      {"nn-config",            required_argument, 0, 'n'},
      {"stat-bit-fail",        optional_argument, 0, OPTARG_STAT_BIT_FAIL},
      {"stat-percent-correct", optional_argument, 0, 'q'},
      {"learning-rate",        required_argument, 0, 'r'},
      {"train-file",           required_argument, 0, 't'},
      {"verbose",              no_argument,       &flag_verbose, 1},
      {"incremental",          optional_argument, 0, 'x'},
      {"ignore-limits",        no_argument,       &flag_ignore_limits, 1}
    };
    int option_index = 0;
    c = getopt_long (argc, argv, "b:d:e:f:g:hi:m::n:q::r:t:x:",
                     long_options, &option_index);
    if (c == -1)
      break;
    switch (c) {
      case 'b': file_video_string = optarg; break;
      case 'd': batch_items = atoi(optarg); break;
      case 'e': max_epochs = atoi(optarg); break;
      case 'f': bit_fail_limit = atof(optarg); break;
      case 'g': mse_fail_limit = atof(optarg); break;
      case 'h': usage(); exit_code = 0; goto bail;
      case 'i': strcpy(id, optarg); break;
      case 'l': flag_last = 1; break;
      case 'm':
        if (optarg)
          mse_reporting_period = atoi(optarg);
        flag_mse = 1;
        break;
      case 'n': file_nn = optarg; break;
      case OPTARG_STAT_BIT_FAIL:
        if (optarg)
          bit_fail_reporting_period = atoi(optarg);
        flag_bit_fail = 1;
        break;
      case 'q':
        if (optarg)
          percent_correct_reporting_period = atoi(optarg);
        flag_percent_correct = 1;
        break;
      case 'r': learning_rate = atof(optarg); break;
      case 't': file_train = optarg; break;
      case 'x': type_training=(optarg)?atoi(optarg):FANN_TRAIN_INCREMENTAL; break;
    }
  };

  if (optind == argc - 1)
    file_out = argv[optind];
  else if (optind < argc - 1) {
    fprintf(stderr, "[ERROR] Expected only one file to output to\n\n");
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

  // The training type needs to make sense
  if (type_training > FANN_TRAIN_SARPROP) {
    fprintf(stderr, "[ERROR] Training type %d outside of enumerated range (max: %d)\n",
            type_training, FANN_TRAIN_SARPROP);
    exit_code = -1;
    goto bail;
  }

  ann = fann_create_from_file(file_nn);
  data = fann_read_train_from_file(file_train);
  if (batch_items != -1 && batch_items < data->num_data)
    data->num_data = batch_items;
  enum fann_activationfunc_enum af =
      fann_get_activation_function(ann, ann->last_layer - ann->first_layer -1, 0);

  ann->training_algorithm = type_training;
  ann->learning_rate = learning_rate;
  printf("[INFO] Using training type %d\n", type_training);

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
  if (file_out) {
    if (strcmp(file_out, "-") == 0)
      fann_save(ann, "/dev/stdout");
    else
      fann_save(ann, file_out);
  }

bail:
  if (ann != NULL)
    fann_destroy(ann);
  if (data != NULL)
    fann_destroy_train(data);
  if (file_video != NULL)
    fclose(file_video);

  return exit_code;
}
