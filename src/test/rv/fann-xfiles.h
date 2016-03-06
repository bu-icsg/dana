#ifndef SRC_TEST_RV_FANN_XFILES_H
#define SRC_TEST_RV_FANN_XFILES_H

#include "usr/include/fixedfann.h"
#include "src/main/c/xfiles.h"

#define read_csr(reg) ({ unsigned long __tmp; \
  asm volatile ("csrr %0, " #reg : "=r"(__tmp)); \
  __tmp; })

static char * usage_message =
  "fann-xfiles -n[config] -t[train file] [options]\n"
  "Run training on a specific neural network and training file.\n"
  "\n"
  "Options:\n"
  "  -a, --print-ant            print information about the asid--nnit table\n"
  "  -b, --video-data           generate a trace of execution over time\n"
  "  -c, --stat-cycles          print the total number of cycles in the ROI\n"
  "  -d, --num-batch-items      specify the number of batch items to use\n"
  "  -e, --max-epochs           the epoch limit (default 10k)\n"
  "  -f, --bit-fail-limit       sets the bit fail limit (default 0.05)\n"
  "  -g, --mse-fail-limit       sets the maximum MSE (default -1, i.e., off)\n"
  "  -h, --help                 print this help and exit\n"
  "  -i, --id                   string id for printing data (default 0)\n"
  "  -j, --set-asid             use a specific asic (default 0)\n"
  "  -k, --set-nnid             use a specific nnid (default 0)\n"
  "  -l, --stat-last            print last epoch number statistic\n"
  "  -m, --stat-mse             print mse stats (optional arg: MSE period)\n"
  "  -n, --nn-config            the binary NN configuration to use\n"
  "  -o, --stat-bit-fail        print bit fail % (optional arg: period)\n"
  "  -p, --performance-mode     runs until epoch limit, limited branches\n"
  "  -q, --stat-percent-correct print the % correct (optional arg: period)\n"
  "  -r, --learning-rate        set the learning rate (default 0.7)\n"
  "  -t, --train-file           the fixed point FANN training file to use\n"
  "  -u, --fake-incremental     run incremental learning using batch\n"
  "  -v, --verbose              turn on per-item inputs/output printfs\n"
  "  -w, --watch-for-errors     turn on some checks for sane outputs\n"
  "  -x, --incremental          run incremental learning instead of batch\n"
  "  -y, --weight-decay-lambda  set the weight decay parameter (default 0)\n"
  "  -z, --ignore-limits        continue blindly ignoring bit fail/mse limits"
  "\n"
  "Flags -n and -t are required.\n"
  "When gathering data related to connection updates per second, -p\n"
  "should always be used as this diables all unnecessary control statements.\n"
  "Flags -u and -x are mutually exclusive.\n";

void usage() {
  printf("Usage: %s", usage_message);
}

// Read the binary configuration format and return the number of
// connections
uint64_t binary_config_num_connections(char * file_nn) {
  FILE * fp;
  int i;

  fp = fopen(file_nn, "rb");
  if (fp == NULL) {
    fprintf(stderr, "[ERROR] Unable to opn %s\n\n", file_nn);
    return 0;
  }

  uint64_t connections = 0;
  uint16_t total_layers, layer_offset, ptr;
  uint32_t tmp;
  uint16_t layer_0, layer_1;
  fseek(fp, 6, SEEK_SET);
  fread(&total_layers, sizeof(uint16_t), 1, fp);
  fread(&layer_offset, sizeof(uint16_t), 1, fp);
  fseek(fp, layer_offset, SEEK_SET);
  fread(&ptr, sizeof(uint16_t), 1, fp);
  ptr &= ~((~0)<<12);
  fseek(fp, ptr + 2, SEEK_SET);
  fread(&layer_0, sizeof(uint16_t), 1, fp);
  layer_0 &= ~((~0)<<8);
  fseek(fp, layer_offset, SEEK_SET);
  fread(&tmp, sizeof(uint32_t), 1, fp);
  layer_1 = (tmp & (~((~0)<<10))<<12)>>12;
  connections += (layer_0 + 1) * layer_1;

  for (i = 1; i < total_layers; i++) {
    layer_0 = layer_1;
    fseek(fp, layer_offset + 4 * i, SEEK_SET);
    fread(&tmp, sizeof(uint32_t), 1, fp);
    layer_1 = (tmp & (~((~0)<<10))<<12)>>12;
    connections += (layer_0 + 1) * layer_1;
  }

  fclose(fp);
  return connections;
}

int binary_config_read_binary_point(char * file_nn, int binary_point_width) {
  FILE * fp;

  fp = fopen(file_nn, "rb");
  if (fp == NULL) {
    fprintf(stderr, "[ERROR] Unable to opn %s\n\n", file_nn);
    return -1;
  }

  int8_t tmp;
  fread(&tmp, sizeof(uint8_t), 1, fp);
  tmp &= ~(~0 << binary_point_width);

  fclose(fp);
  return tmp;
}

typedef struct {
  int cycles;
  int last;
  int mse;
  int performance;
  int ant_info;
  int incremental;
  int bit_fail;
  int ignore_limits;
  int percent_correct;
  int watch_for_errors;
  int incremental_fake;
  int verbose;
} flags_t;

typedef struct {
  flags_t flags;
  uint64_t cycles, connections_per_epoch;
  int32_t learn_rate, weight_decay;
  asid_type asid;
  nnid_type nnid;
  int max_epochs, exit_code, num_bits_failing, batch_items, num_correct,
    binary_point_width, binary_point_offset, binary_point,
    mse_reporting_period, bit_fail_reporting_period,
    percent_correct_reporting_period, epoch;
  double bit_fail_limit, mse_fail_limit, learning_rate, weight_decay_lambda,
    mse, error;
  char * file_nn, * file_train, * file_video_string, id[100];
  FILE * file_video;
  asid_nnid_table * table;
  element_type * outputs, * outputs_old;
  struct fann_train_data * data;
  size_t num_input, num_output;
  double multiplier;
  // Train on the provided data
  tid_type tid;
} test_t;

void test_init(test_t * test, int bp_w, int bp_o) {
  // Zero all the flags
  for (int i = 0; i < sizeof(flags_t) / sizeof(int); ++i)
    ((int *) &(test)->flags)[i] = 0;
  test->asid = 0;
  test->nnid = 0;
  test->binary_point_width = bp_w;
  test->binary_point_offset = bp_o;
  test->exit_code = 0;
  test->max_epochs = 10000;
  test->num_bits_failing = -1;
  test->batch_items = -1;
  test->binary_point = -1;
  test->mse_reporting_period = 1;
  test->bit_fail_reporting_period = 1;
  test->percent_correct_reporting_period = 1;
  // Double inits
  test->bit_fail_limit = 0.05;
  test->mse_fail_limit = -1.0;
  test->learning_rate = 0.7;
  test->weight_decay_lambda = 0.0;
  test->mse = 0.0;
  // Files
  test->file_video_string = NULL;
  // Strings
  test->file_nn = NULL;
  test->file_train = NULL;
  test->file_video = NULL;
  test->id[0] = '0';
  test->id[1] = 0;
  // Other
  test->table = NULL;
  test->outputs = NULL;
  test->outputs_old = NULL;
  test->data = NULL;
}

void parse_options(test_t * t, int argc, char ** argv) {
  int c;
  while (1) {
    static struct option long_test[] = {
      {"ant-info",             no_argument,       0, 'a'},
      {"video-data",           required_argument, 0, 'b'},
      {"stat-cycles",          no_argument,       0, 'c'},
      {"num-batch-items",      required_argument, 0, 'd'},
      {"max-epochs",           required_argument, 0, 'e'},
      {"bit-fail-limit",       required_argument, 0, 'f'},
      {"mse-fail-limit",       required_argument, 0, 'g'},
      {"help",                 no_argument,       0, 'h'},
      {"id",                   required_argument, 0, 'i'},
      {"set-asid",             required_argument, 0, 'j'},
      {"set-nnid",             required_argument, 0, 'k'},
      {"stat-last",            no_argument,       0, 'l'},
      {"stat-mse",             optional_argument, 0, 'm'},
      {"nn-config",            required_argument, 0, 'n'},
      {"stat-bit-fail",        optional_argument, 0, 'o'},
      {"performance-mode",     no_argument,       0, 'p'},
      {"stat-percent-correct", optional_argument, 0, 'q'},
      {"train-file",           required_argument, 0, 't'},
      {"verbose",              no_argument,       0, 'v'},
      {"watch-for-errors",     no_argument,       0, 'w'},
      {"incremental",          no_argument,       0, 'x'},
      {"weight-decay-lamba,",  required_argument, 0, 'y'},
      {"ignore-limits",        no_argument,       0, 'z'}
    };
    int option_index = 0;
    c = getopt_long (argc, argv, "ab:cd:e:f:g:hi:j:k:lm::n:o::pq::r:t:uvwxy:z",
                     long_test, &option_index);
    if (c == -1)
      break;
    switch (c) {
    case 'a': t->flags.ant_info = 1; break;
    case 'b': t->file_video_string = optarg; break;
    case 'c': t->flags.cycles = 1; break;
    case 'd': t->batch_items = atoi(optarg); break;
    case 'e': t->max_epochs = atoi(optarg); break;
    case 'f': t->bit_fail_limit = atof(optarg); break;
    case 'g': t->mse_fail_limit = atof(optarg); break;
    case 'h': usage(); t->exit_code = 1; return;
    case 'i': strcpy(t->id, optarg); break;
    case 'j': t->asid = atoi(optarg); break;
    case 'k': t->nnid = atoi(optarg); break;
    case 'l': t->flags.last = 1; break;
    case 'm': t->flags.mse = 1;
      if (optarg)
        t->mse_reporting_period = atoi(optarg);
      break;
    case 'n': t->file_nn = optarg; break;
    case 'o': t->flags.bit_fail = 1;
      if (optarg)
        t->bit_fail_reporting_period = atoi(optarg);
      break;
    case 'p': t->flags.performance = 1; break;
    case 'r': t->learning_rate = atof(optarg); break;
    case 'q': t->flags.percent_correct = 1;
      if (optarg)
        t->percent_correct_reporting_period = atoi(optarg);
      break;
    case 't': t->file_train = optarg; break;
    case 'u': t->flags.incremental_fake = 1; break;
    case 'v': t->flags.verbose = 1; break;
    case 'w': t->flags.watch_for_errors = 1; break;
    case 'x': t->flags.incremental = 1; break;
    case 'y': t->weight_decay_lambda = atof(optarg); break;
    case 'z': t->flags.ignore_limits = 1; break;
    }
  };

  // Make sure there aren't any arguments left over
  if (optind != argc) {
    fprintf(stderr, "[ERROR] Bad argument\n\n");
    usage();
    t->exit_code = -1;
    return;
  }

  // Make sure we have all required inputs
  if (t->file_nn == NULL || t->file_train == NULL) {
    fprintf(stderr, "[ERROR] Missing required input argument\n\n");
    usage();
    t->exit_code = -1;
    return;
  }

  // Incremental and fake incremental are conflicting test
  if (t->flags.incremental && t->flags.incremental_fake) {
    fprintf(stderr, "[ERROR] Only one of '-x' or '-u' may be specified\n\n");
    usage();
    t->exit_code = -1;
    return;
  }

  // Read the binary point and make sure its sane
  t->binary_point =
    binary_config_read_binary_point(t->file_nn, t->binary_point_width) +
    t->binary_point_offset;
  if (t->binary_point < t->binary_point_offset) {
    fprintf(stderr, "[ERROR] Binary point (%d) looks bad, exiting\n\n",
            t->binary_point);
    t->exit_code = -1;
    return;
  }
  printf("[INFO] Found binary point %d\n", t->binary_point);
}

// Run batch training on a specified set of items in the dataset
int xfiles_train_batch(test_t * t, int item_start, int item_stop) {
  if (item_start < 0 || item_stop > t->batch_items)
    return -1;

  t->tid = new_write_request(t->nnid, TRAIN_BATCH, 0);
  write_register(t->tid, xfiles_reg_batch_items, t->batch_items);
  write_register(t->tid, xfiles_reg_learning_rate, t->learn_rate);
  write_register(t->tid, xfiles_reg_weight_decay_lambda, t->weight_decay);

  for (int i = item_start; i < item_stop; ++i) {
    // Write the output and input data
    write_data_train_incremental(t->tid,
                                 (element_type *) t->data->input[i],
                                 (element_type *) t->data->output[i],
                                 t->num_input, t->num_output);
    // Blocking read
    read_data_spinlock(t->tid, t->outputs, t->num_output);
  }
  return 0;
}

// Run incremental training over some set of items
int xfiles_train_incremental(test_t * t, int item_start, int item_stop) {
  if (item_start < 0 || item_stop > t->batch_items)
    return -1;

  for (int i = 0; i < t->batch_items; ++i) {
    // Run one training t->epoch
    t->tid = new_write_request(t->nnid, TRAIN_INCREMENTAL, 0);
    write_register(t->tid, xfiles_reg_learning_rate, t->learn_rate);
    write_register(t->tid, xfiles_reg_weight_decay_lambda, t->weight_decay);
    // Write the output and input data
    write_data_train_incremental
      (t->tid, (element_type *) t->data->input[i],
       (element_type *) t->data->output[i], t->num_input, t->num_output);

    // Blocking read
    read_data_spinlock(t->tid, t->outputs, t->num_output);
  }
  return 0;
}

double to_float(test_t * t, element_type value) {
  return value / t->multiplier;
}

int bit_fail(test_t * t, size_t item, size_t idx) {
  double diff = to_float(t, t->outputs[idx] - t->data->output[item][idx]);
  return fabs(diff > t->bit_fail_limit);
}

double compute_err(test_t * t, size_t item, size_t idx) {
  return to_float(t, t->outputs[idx] - t->data->output[item][idx]);
}

double compute_mse_change(test_t * t, size_t item, size_t idx) {
  double new_value = to_float(t, t->outputs[idx]);
  double old_value = to_float(t, t->outputs[item * t->num_output + idx]);
  return fabs(new_value - old_value);
}

// Do feedforward passes through the network for a specific set of
// items
int xfiles_eval_batch(test_t * t, int item_start, int item_stop) {
  if (item_start < 0 || item_stop > t->batch_items)
    return -1;

  t->num_bits_failing = 0;
  t->num_correct = 0;
  t->mse = 0;

  for (int item = 0; item < t->batch_items; item++) {
    t->tid = new_write_request(t->nnid, FEEDFORWARD, 0);
    write_data(t->tid, (element_type *) t->data->input[item], t->num_input);
    read_data_spinlock(t->tid, t->outputs, t->num_output);

    if (t->flags.verbose) {
      printf("[INFO] ");
      for (int i = 0; i < t->num_input; i++)
        printf("%8.5f ", to_float(t, t->data->input[item][i]));
    }

    int correct = 1;
    for (int i = 0; i < t->num_output; i++) {
      double output_float = to_float(t, t->outputs[i]);
      if (t->flags.verbose)
        printf("%8.5f ", output_float);

      int bit_failure = bit_fail(t, item, i);
      t->num_bits_failing += bit_failure;
      if (bit_failure)
        correct = 0;

      if (t->flags.mse || t->mse_fail_limit != -1) {
        t->error = compute_err(t, item, i);
        t->mse += t->error * t->error;
      }

      if (t->flags.watch_for_errors && t->epoch > 0) {
        double change = compute_mse_change(t, item, i);
        if (change > 0.1)
          printf("\n[ERROR] T->Epoch %d: Output changed by > 0.1 (%0.5f)",
                 t->epoch, change);
      }
      if (t->file_video)
        fprintf(t->file_video, "%f ", output_float);
      t->outputs_old[item * t->num_output + i] = t->outputs[i];
    }
    t->num_correct += correct;
    if (t->file_video)
      fprintf(t->file_video, "\n");
    if (t->flags.verbose) {
      if (item < t->batch_items - 1)
        printf("\n");
    }
  }

  if (t->flags.verbose)
    printf("%5d\n\n", t->epoch);

  if (t->flags.mse || t->mse_fail_limit != -1) {
    t->mse /= t->batch_items * t->num_output;
    if (t->flags.mse && (t->epoch % t->mse_reporting_period == 0))
      printf("[STAT] epoch %d id %s bp %d mse %8.8f\n", t->epoch, t->id,
             t->binary_point, t->mse);
  }

  if (t->flags.bit_fail && (t->epoch % t->bit_fail_reporting_period == 0))
    printf("[STAT] epoch %d id %s bp %d bfp %8.8f\n", t->epoch, t->id,
           t->binary_point, 1 - (double) t->num_bits_failing /
           t->num_output / t->batch_items);

  if (t->flags.percent_correct &&
      (t->epoch % t->percent_correct_reporting_period == 0))
    printf("[STAT] epoch %d id %s bp %d perc %8.8f\n", t->epoch, t->id,
           t->binary_point,
           (double) t->num_correct / t->batch_items);

  if (!t->flags.ignore_limits &&
      (t->num_bits_failing == 0 || t->mse < t->mse_fail_limit))
    return 1;

  return 0;
}

int xfiles_batch_verbose(test_t * t) {
  int exit_code = 0;
  t->cycles = read_csr(0xc00);
  for (t->epoch = 0; t->epoch < t->max_epochs; t->epoch++) {
    if ((exit_code = xfiles_train_batch(t, 0, t->batch_items)))
      return exit_code;
    if ((exit_code = xfiles_eval_batch(t, 0, t->batch_items)))
      return exit_code;
  }
  t->cycles = read_csr(0xc00) - t->cycles;
  return exit_code;
}

int xfiles_incremental_verbose(test_t * t) {
  int exit_code = 0;
  t->cycles = read_csr(0xc00);
  for (t->epoch = 0; t->epoch < t->max_epochs; t->epoch++) {
    if ((exit_code = xfiles_train_incremental(t, 0, t->batch_items)))
      return exit_code;
    if ((exit_code = xfiles_eval_batch(t, 0, t->batch_items)))
      return exit_code;
  }
  t->cycles = read_csr(0xc00) - t->cycles;
  return exit_code;
}

void xfiles_batch_performance(test_t * t) {
  t->cycles = read_csr(0xc00);
  for (t->epoch = 0; t->epoch < t->max_epochs; t->epoch++)
    xfiles_train_batch(t, 0, t->batch_items);
  t->cycles = read_csr(0xc00) - t->cycles;
}

void xfiles_incremental_performance(test_t * t) {
  t->cycles = read_csr(0xc00);
  for (t->epoch = 0; t->epoch < t->max_epochs; t->epoch++)
    xfiles_train_incremental(t, 0, t->batch_items);
  t->cycles = read_csr(0xc00) - t->cycles;
}

#endif  // SRC_TEST_RV_FANN_XFILES_H
