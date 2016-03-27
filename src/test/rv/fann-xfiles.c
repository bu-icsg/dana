// See LICENSE for license details.

#include <stdint.h>
#include <stdio.h>
#include <math.h>
#include <getopt.h>
#include <time.h>

#include "src/test/rv/fann-xfiles.h"

int main (int argc, char * argv[]) {
  test_t t;
  test_init(&t, 3, 7);

#ifdef VERBOSE_DEFAULT
  t.flags.verbose = 1;
#else
  t.flags.verbose = 0;
#endif

  parse_options(&t, argc, argv);
  if (t.exit_code)
    goto bail;

  // Create the ASID--NNID Table
  asid_nnid_table_create(&t.table, t.asid * 2 + 1, t.nnid * 2 + 1);
  int old_antp = set_antp(t.table);
  if (-old_antp != err_DANA_NOANTP) {
    printf("[ERROR] Found unexpected ANTP response 0d%d\n", -old_antp);
    t.exit_code = -old_antp;
    goto bail;
  }

  // Populate the ASID--NNID Table
  int i;
  for (i = 0; i < t.nnid * 2 + 1; i++) {
    if (i == t.nnid) {
      if (attach_nn_configuration(&t.table, t.asid, t.file_nn) != t.nnid + 1) {
        printf("[ERROR] Failed to attach NN configuration 0x%x\n", t.nnid);
        t.exit_code = -1;
        goto bail;
      }
    }
    else
      attach_garbage(&t.table, t.asid);
  }
  int old_asid = set_asid(t.asid);
  if (-old_asid != err_XFILES_NOASID) {
    printf("[ERROR] Found unexpected ASID response 0d%d\n", -old_asid);
    t.exit_code = -old_asid;
    goto bail;
  }

  if (t.flags.ant_info)
    asid_nnid_table_info(t.table);

  if (t.file_video_string != NULL)
    t.file_video = fopen(t.file_video_string, "w");

  t.connections_per_epoch = binary_config_num_connections(t.file_nn);

  // Read in data from the training file
  t.data = fann_read_train_from_file(t.file_train);
  if (t.data == NULL) {
    t.exit_code = -2;
    goto bail;
  }
  t.num_input = t.data->num_input;
  t.num_output = t.data->num_output;
  printf("[INFO] Done reading input file\n");

  t.multiplier = pow(2, t.binary_point);

  t.outputs = (element_type *) malloc(t.num_output * sizeof(element_type));
  if (t.batch_items == -1)
    t.batch_items = fann_length_train_data(t.data);
  if (t.batch_items > fann_length_train_data(t.data))
    t.batch_items = fann_length_train_data(t.data);
  t.outputs_old = (element_type *)
    malloc(t.num_output * t.batch_items *
                                        sizeof(element_type));
  t.learn_rate = (int32_t) (t.learning_rate * t.multiplier);
  t.weight_decay = (int32_t) (t.weight_decay_lambda * t.multiplier);
  if (!t.flags.incremental) {
    t.learn_rate /= t.batch_items;
    t.weight_decay /= t.batch_items;
  }
  // weight_decay = 1;
  if (t.learn_rate == 0) {
    printf("[ERROR] Number of batch items forces learning rate increase\n");
    printf("[ERROR]   learning_rate: 0d%f\n", t.learning_rate);
    printf("[ERROR]   multiplier:    0f%f\n", t.multiplier);
    printf("[ERROR]   learn_rate:    0d%d\n", t.learn_rate);
    t.exit_code = -3;
    goto bail;
  }
  printf("[INFO] Computed learning rate is 0x%x\n", t.learn_rate);

  // Execution is broken down into two different modes "performance"
  // and "verbose". "Verbose" allows for early training exits based on
  // MSE, etc. However, this requires additional control statements,
  // possible printfs and things which hide the actual performance of
  // the accelerator. "Performance" turns all this off and just runs
  // for a hard number of epochs. This mode is intended to be used
  // with the '-c' option to get an accurate number of connection
  // updates per cycle.
  switch((t.flags.performance << 1) | t.flags.incremental) {
  case 0: xfiles_batch_verbose(&t);           break;
  case 1: xfiles_incremental_verbose(&t);     break;
  case 2: xfiles_batch_performance(&t);       break;
  case 3: xfiles_incremental_performance(&t); break;
  }

  if (t.exit_code) {
    printf("[ERROR] Saw exit code %d\n", t.exit_code);
    goto bail;
  }

  // Print overall statistics in a parser-friendly way
  if (t.flags.last)
    printf("[STAT] bp %d id %s t.epoch %d\n", t.binary_point, t.id, t.epoch);
  if (t.flags.cycles) {
    printf("[STAT] x 0 id %s bp %d cycles %ld\n", t.id, t.binary_point,
           t.cycles);
    printf("[STAT] x 0 id %s bp %d CUPC %0.8f\n", t.id, t.binary_point,
           (t.connections_per_epoch * t.epoch * t.batch_items) /
           (double) t.cycles);
  }

  // Free memory
 bail:
  if (t.data != NULL)        fann_destroy_train(t.data);
  if (t.table != NULL)       asid_nnid_table_destroy(&t.table);
  if (t.outputs != NULL)     free(t.outputs);
  if (t.outputs_old != NULL) free(t.outputs_old);
  if (t.file_video != NULL)  fclose(t.file_video);
  return t.exit_code;
}
