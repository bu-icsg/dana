#include <stdint.h>
#include <stdio.h>
#include <math.h>
#include <getopt.h>
#include <time.h>

#include "fixedfann.h"
#include "xfiles.h"

#define read_csr(reg) ({ unsigned long __tmp; \
  asm volatile ("csrr %0, " #reg : "=r"(__tmp)); \
  __tmp; })

static char * usage_message =
  "fann-batch -n[config] -t[train file] [options]\n"
  "Run batch training on a specific neural network and training file.\n"
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
  "  -i, --id                   string id to use for printing data (default 0)\n"
  "  -j, --set-asid             use a specific asic (default 0)\n"
  "  -k, --set-nnid             use a specific nnid (default 0)\n"
  "  -l, --stat-last            print last epoch number statistic\n"
  "  -m, --stat-mse             print mse statistics (optional arg: MSE period)\n"
  "  -n, --nn-config            the binary NN configuration to use\n"
  "  -o, --stat-bit-fail        print bit fail percent (optional arg: period)\n"
  "  -p, --performance-mode     runs until an epoch limit, all checks disabled\n"
  "  -q, --stat-percent-correct print the percent correct (optional arg: period)\n"
  "  -r, --learning-rate        set the learning rate (default 0.7)\n"
  "  -t, --train-file           the fixed point FANN training file to use\n"
  "  -v, --verbose              turn on per-item inputs/output printfs\n"
  "  -w, --watch-for-errors     turn on some checks for sane outputs\n"
  "  -x, --incremental          run incremental updates instead of batch updates\n"
  "  -y, --weight-decay-lambda  set the weight decay parameter, lambda (default 0)\n"
  "  -z, --ignore-limits        continue blindly ignoring bit fail/mse limits"
  "\n"
  "Flags -n and -t are required.\n"
  "When gathering data related to connection updates per second, -p\n"
  "should always be used as this diables all unnecessary control statements.\n";

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



int main (int argc, char * argv[]) {
  int exit_code = 0, max_epochs = 10000, num_bits_failing = -1, batch_items = -1,
    num_correct;
  int flag_cycles = 0, flag_last = 0, flag_mse = 0, flag_performance = 0,
    flag_ant_info = 0, flag_incremental = 0, flag_bit_fail = 0,
    flag_ignore_limits = 0, flag_percent_correct = 0, flag_watch_for_errors = 0;
  char id[100] = "0";
  asid_type asid = 0;
  nnid_type nnid = 0;
  int binary_point_width = 3, binary_point_offset = 7;
#ifdef VERBOSE_DEFAULT
  int flag_verbose = 1;
#else
  int flag_verbose = 0;
#endif
  int mse_reporting_period = 1, bit_fail_reporting_period = 1,
    percent_correct_reporting_period = 1;
  uint64_t cycles;
  double bit_fail_limit = 0.05, mse_fail_limit = -1.0,
    learning_rate = 0.7, weight_decay_lambda = 0.0;
  struct fann_train_data * data = NULL;

  char * file_nn = NULL, * file_train = NULL;
  char * file_video_string = NULL;
  FILE * file_video = NULL;
  asid_nnid_table * table = NULL;
  element_type * outputs = NULL, * outputs_old = NULL;
  int binary_point = -1, c;
  while (1) {
    static struct option long_options[] = {
      {"ant-info",             no_argument,       0, 'a'},
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
    c = getopt_long (argc, argv, "ab:cd:e:f:g:hi:j:k:lm::n:o::pq::r:t:vwxy:z",
                     long_options, &option_index);
    if (c == -1)
      break;
    switch (c) {
    case 'a':
      flag_ant_info = 1;
      break;
    case 'b':
      file_video_string = optarg;
      break;
    case 'c':
      flag_cycles = 1;
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
    case 'j':
      asid = atoi(optarg);
      break;
    case 'k':
      nnid = atoi(optarg);
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
    case 'p':
      flag_performance = 1;
      break;
    case 'r':
      learning_rate = atof(optarg);
      break;
    case 'q':
      if (optarg)
        percent_correct_reporting_period = atoi(optarg);
      flag_percent_correct = 1;
      break;
    case 't':
      file_train = optarg;
      break;
    case 'v':
      flag_verbose = 1;
      break;
    case 'w':
      flag_watch_for_errors = 1;
      break;
    case 'x':
      flag_incremental = 1;
      break;
    case 'y':
      weight_decay_lambda = atof(optarg);
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

  // Read the binary point and make sure its sane
  binary_point = binary_config_read_binary_point(file_nn, binary_point_width) +
    binary_point_offset;
  if (binary_point < binary_point_offset) {
    fprintf(stderr, "[ERROR] Binary point (%d) looks bad, exiting\n\n",
            binary_point);
    exit_code = -1;
    goto bail;
  }
  printf("[INFO] Found binary point %d\n", binary_point);

  // Create the ASID--NNID Table
  asid_nnid_table_create(&table, asid * 2 + 1, nnid * 2 + 1);
  set_antp(table);

  // Populate the ASID--NNID Table
  int i;
  for (i = 0; i < nnid * 2 + 1; i++) {
    if (i == nnid) {
      if (attach_nn_configuration(&table, asid, file_nn) != nnid + 1) {
        printf("[ERROR] Failed to attach NN configuration 0x%x\n", nnid);
        exit_code = -1;
        goto bail;
      }
    }
    else
      attach_garbage(&table, asid);
  }
  set_asid(asid);

  if (flag_ant_info)
    asid_nnid_table_info(table);

  if (file_video_string != NULL)
    file_video = fopen(file_video_string, "w");

  uint64_t connections_per_epoch = binary_config_num_connections(file_nn);

  // Read in data from the training file
  data = fann_read_train_from_file(file_train);
  if (data == NULL) {
    exit_code = -2;
    goto bail;
  }
  size_t num_input = data->num_input;
  size_t num_output = data->num_output;
  printf("[INFO] Done reading input file\n");

  double multiplier = pow(2, binary_point);

  // Train on the provided data
  int epoch, item;
  tid_type tid;
  double mse = 0.0, error;

  outputs = (element_type *) malloc(num_output * sizeof(element_type));
  if (batch_items == -1)
    batch_items = fann_length_train_data(data);
  if (batch_items > fann_length_train_data(data))
    batch_items = fann_length_train_data(data);
  outputs_old = (element_type *) malloc(num_output * batch_items *
                                        sizeof(element_type));
  int32_t learn_rate = (int32_t) (learning_rate * multiplier);
  int32_t weight_decay = (int32_t) (weight_decay_lambda * multiplier);
  if (!flag_incremental) {
    learn_rate /= batch_items;
    weight_decay /= batch_items;
  }
  // weight_decay = 1;
  if (learn_rate == 0) {
    printf("[ERROR] Number of batch items forces learning rate increase\n");
    exit_code = -3;
    goto bail;
  }
  printf("[INFO] Computed learning rate is 0x%x\n", learn_rate);

  // Execution is broken down into two different modes "performance"
  // and "verbose". "Verbose" allows for early training exits based on
  // MSE, etc. However, this requires additional control statements,
  // possible printfs and things which hide the actual performance of
  // the accelerator. "Performance" turns all this off and just runs
  // for a hard number of epochs. This mode is intended to be used
  // with the '-c' option to get an accurate number of connection
  // updates per cycle.
  switch((flag_performance << 1) | flag_incremental) {
  case 0: goto xfiles_batch_verbose;
  case 1: goto xfiles_incremental_verbose;
  case 2: goto xfiles_batch_performance;
  case 3: goto xfiles_incremental_performance;
  }
  if (flag_performance) goto xfiles_batch_performance;
  else goto xfiles_batch_verbose;

 xfiles_batch_verbose:
  cycles = read_csr(0xc00);
  for (epoch = 0; epoch < max_epochs; epoch++) {
    // Run one training epoch
    tid = new_write_request(nnid, 2, 0);
    write_register(tid, xfiles_reg_batch_items, batch_items);
    write_register(tid, xfiles_reg_learning_rate, learn_rate);
    write_register(tid, xfiles_reg_weight_decay_lambda, weight_decay);

    for (item = 0; item < batch_items; item++) {
      // Write the output and input data
      write_data_train_incremental(tid, (element_type *) data->input[item],
                                   (element_type *) data->output[item],
                                   num_input, num_output);

      // Blocking read
      read_data_spinlock(tid, outputs, num_output);
    }

    // Check the outputs
    num_bits_failing = 0;
    num_correct = 0;
    mse = 0;
    for (item = 0; item < batch_items; item++) {
      tid = new_write_request(nnid, 0, 0);
      write_data(tid, (element_type *) data->input[item], num_input);
      read_data_spinlock(tid, outputs, num_output);

      if (flag_verbose) {
        printf("[INFO] ");
        for (i = 0; i < num_input; i++) {
          printf("%8.5f ", ((double)data->input[item][i]) / multiplier);
        }
      }

      int correct = 1;
      for (i = 0; i < num_output; i++) {
        if (flag_verbose)
          printf("%8.5f ", ((double)outputs[i])/multiplier);
        num_bits_failing +=
          fabs((double)(outputs[i] - data->output[item][i]) / multiplier) >
          bit_fail_limit;
        if (fabs((double)(outputs[i] - data->output[item][i]) / multiplier) >
            bit_fail_limit)
          correct = 0;
        if (flag_mse || mse_fail_limit != -1) {
          error = (double)(outputs[i] - data->output[item][i]) / multiplier;
          mse += error * error;
        }
        if (flag_watch_for_errors && epoch > 0) {
          double change =
            fabs(((double) outputs[i] / multiplier) -
                 ((double) outputs_old[item * num_output + i] / multiplier));
          if (change > 0.1)
            printf("\n[ERROR] Epoch %d: Output changed by > 0.1 (%0.5f)",
                   epoch, change);
        }
        if (file_video)
          fprintf(file_video, "%f ", (double) outputs[i] / multiplier);
        outputs_old[item * num_output + i] = outputs[i];
      }
      num_correct += correct;
      if (file_video)
        fprintf(file_video, "\n");
      if (flag_verbose) {
        if (item < batch_items - 1)
          printf("\n");
      }
    }

    if (flag_verbose)
      printf("%5d\n\n", epoch);
    if (flag_mse || mse_fail_limit != -1) {
      mse /= batch_items * num_output;
      if (flag_mse && (epoch % mse_reporting_period == 0))
        printf("[STAT] epoch %d id %s bp %d mse %8.8f\n", epoch, id, binary_point, mse);
    }
    if (flag_bit_fail && (epoch % bit_fail_reporting_period == 0))
      printf("[STAT] epoch %d id %s bp %d bfp %8.8f\n", epoch, id,
             binary_point, 1 - (double) num_bits_failing / num_output /
             batch_items);
    if (flag_percent_correct && (epoch % percent_correct_reporting_period == 0))
      printf("[STAT] epoch %d id %s bp %d perc %8.8f\n", epoch, id,
             binary_point,
             (double) num_correct / batch_items);
    if (!flag_ignore_limits && (num_bits_failing == 0 || mse < mse_fail_limit))
      goto finish;
  }
  goto finish;

 xfiles_batch_performance:
  cycles = read_csr(0xc00);
  for (epoch = 0; epoch < max_epochs; epoch++) {
    // Run one training epoch
    tid = new_write_request(nnid, 2, 0);
    write_register(tid, xfiles_reg_batch_items, batch_items);
    write_register(tid, xfiles_reg_learning_rate, learn_rate);
    write_register(tid, xfiles_reg_weight_decay_lambda, weight_decay);

    for (item = 0; item < batch_items; item++) {
      // Write the output and input data
      write_data_train_incremental(tid, data->input[item], data->output[item],
                                   num_input, num_output);

      // Blocking read
      read_data_spinlock(tid, outputs, num_output);
    }
  }
  goto finish;

 xfiles_incremental_verbose:
  cycles = read_csr(0xc00);
  for (epoch = 0; epoch < max_epochs; epoch++) {
    for (item = 0; item < batch_items; item++) {
      // Run one training epoch
      tid = new_write_request(nnid, 1, 0);
      write_register(tid, xfiles_reg_learning_rate, learn_rate);
      write_register(tid, xfiles_reg_weight_decay_lambda, weight_decay);
      // Write the output and input data
      write_data_train_incremental(tid, (element_type *) data->input[item],
                                   (element_type *) data->output[item],
                                   num_input, num_output);

      // Blocking read
      read_data_spinlock(tid, outputs, num_output);
    }

    // Check the outputs
    num_bits_failing = 0;
    mse = 0;
    num_correct = 0;
    for (item = 0; item < batch_items; item++) {
      tid = new_write_request(nnid, 0, 0);
      write_data(tid, (element_type *) data->input[item], num_input);
      read_data_spinlock(tid, outputs, num_output);

      if (flag_verbose) {
        printf("[INFO] ");
        for (i = 0; i < num_input; i++) {
          printf("%8.5f ", ((double)data->input[item][i]) / multiplier);
        }
      }

      int correct = 1;
      for (i = 0; i < num_output; i++) {
        if (flag_verbose)
          printf("%8.5f ", ((double)outputs[i])/multiplier);
        num_bits_failing +=
          fabs((double)(outputs[i] - data->output[item][i]) / multiplier) >
          bit_fail_limit;
        if (fabs((double)(outputs[i] - data->output[item][i]) / multiplier) >
            bit_fail_limit)
          correct = 0;
        if (flag_mse || mse_fail_limit != -1) {
          error = (double)(outputs[i] - data->output[item][i]) / multiplier;
          mse += error * error;
        }
        if (file_video)
          fprintf(file_video, "%f ", (double) outputs[i] / multiplier);
      }
      num_correct += correct;
      if (file_video)
        fprintf(file_video, "\n");
      if (flag_verbose) {
        if (item < batch_items - 1)
          printf("\n");
      }
    }


    if (flag_verbose)
      printf("%5d\n\n", epoch);
    if (flag_mse || mse_fail_limit != -1) {
      mse /= batch_items * num_output;
      if (flag_mse && (epoch % mse_reporting_period == 0))
        printf("[STAT] epoch %d id %s bp %d mse %8.8f\n", epoch, id, binary_point, mse);
    }
    if (flag_bit_fail && (epoch % bit_fail_reporting_period == 0))
      printf("[STAT] epoch %d id %s bp %d bfp %8.8f\n", epoch, id,
             binary_point, 1 - (double) num_bits_failing / num_output /
             batch_items);
    if (flag_percent_correct && (epoch % percent_correct_reporting_period == 0))
      printf("[STAT] epoch %d id %s bp %d perc %8.8f\n", epoch, id,
             binary_point,
             (double) num_correct / batch_items);
    if (num_bits_failing == 0 || mse < mse_fail_limit)
      goto finish;
  }
  goto finish;

 xfiles_incremental_performance:
  cycles = read_csr(0xc00);
  for (epoch = 0; epoch < max_epochs; epoch++) {
    for (item = 0; item < batch_items; item++) {
      // Run one training epoch
      tid = new_write_request(nnid, 1, 0);
      write_register(tid, xfiles_reg_learning_rate, learn_rate);
      write_register(tid, xfiles_reg_weight_decay_lambda, weight_decay);
      // Write the output and input data
      write_data_train_incremental(tid, (element_type *) data->input[item],
                                   (element_type *) data->output[item],
                                   num_input, num_output);

      // Blocking read
      read_data_spinlock(tid, outputs, num_output);
    }
  }
  goto finish;

  // Print overall statistics in a parser-friendly way
 finish:
  cycles = read_csr(0xc00) - cycles;
  // printf("# [STAT] fann-batch-id%d-bit-fail %d\n", id, num_bits_failing);
  // printf("# [STAT] fann-batch-id%d-final-epoch %d\n", id, epoch);
  if (flag_last)
    printf("[STAT] bp %d id %s epoch %d\n", binary_point, id, epoch);
  if (flag_cycles) {
    printf("[STAT] x 0 id %s bp %d cycles %ld\n", id, binary_point, cycles);
    printf("[STAT] x 0 id %s bp %d CUPC %0.8f\n", id, binary_point,
           (connections_per_epoch * epoch * batch_items) / (double) cycles);
  }

  // Free memory
 bail:
  if (data != NULL)
    fann_destroy_train(data);
  if (table != NULL)
    asid_nnid_table_destroy(&table);
  if (outputs != NULL)
    free(outputs);
  if (outputs_old != NULL)
    free(outputs_old);
  if (file_video != NULL)
    fclose(file_video);
  return exit_code;
}
