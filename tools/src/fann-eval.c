// See LICENSE.IBM for license details.
// See LICENSE.BU for license details.

#include <stdio.h>
#include <getopt.h>
#include <string.h>

#include "tools/src/copyright.h"
#ifndef FIXEDFANN
#include "fann/src/include/fann.h"
#else
#include "fann/src/include/fixedfann.h"
#undef FANNPRINTF
#define FANNPRINTF "%08x"
#endif

static char * usage_message =
    "Usage: fann-eval -n[CONFIG] -t[TRAIN_FILE]\n"
    "Fun feedforward inference for a given FANN configuration (CONFIG) and testing\n"
    "file (TEST FILE).\n"
    "\n"
    "Options:\n"
    "  -n, --nn-config [CONFIG]   read FANN floating point network from FILE\n"
    "  -t, --test-file [TRAIN FILE]\n"
    "                             read FANN testing file FILE\n"
    "  --verbose                  print information while running\n"
    "\n";

void usage () {
  printf("Usage: %s", usage_message);
}

int main (int argc, char * argv[]) {
  PRINT_NOTICES(COPYRIGHT_FANN);
  int exit_code = 0;

  struct fann * ann = NULL;
  struct fann_train_data * data = NULL;

  int c;
  static int opt_verbose = 0;
  while (1) {
    static struct option long_options[] = {
      {"nn-config",            required_argument, 0, 'n'},
      {"train-file",           required_argument, 0, 't'},
      {"verbose",              no_argument,       &opt_verbose, 1},
      {0, 0, 0, 0}
    };
    int option_index = 0;
    c = getopt_long (argc, argv, "n:t:",
                     long_options, &option_index);
    if (c == -1)
      break;
    switch (c) {
      case 'n': ann = fann_create_from_file(optarg); break;
      case 't': data = fann_read_train_from_file(optarg); break;
    }
  }

  if (ann == NULL || data == NULL) {
    fprintf(stderr, "[ERROR] Missing required input argument\n\n");
    usage();
    exit_code = -1;
    goto bail;
  }

  // double mse;
  fann_type * calc_out;
  for (int i = 0; i < fann_length_train_data(data); i++) {
    calc_out = fann_test(ann, data->input[i], data->output[i]);
    if (opt_verbose) {
      printf("[INFO] ");
      for (int k = 0; k < data->num_input; k++) {
        printf(FANNPRINTF " ", data->input[i][k]);
      }
      printf("-> ");
      for (int k = 0; k < data->num_output; k++) {
        printf(FANNPRINTF " ", calc_out[k]);
      }
      printf("\n");
    }
  }

bail:
  if (ann != NULL)
    fann_destroy(ann);
  if (data != NULL)
    fann_destroy_train(data);

  return exit_code;
}
