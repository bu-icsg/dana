#include <getopt.h>

#include "fann.h"

void usage() {
  printf("usage: fann-float-to-fixed [OPTIONS] <floating point net> <fixed point net>\n"
         "\n"
         "Options:\n"
         "  -h, --help                 print this help and exit\n"
         "  -v, --verbose              print exhaustive debug info\n"
         );
}

int main(int argc, char *argv[])
{
  struct fann * ann = NULL;
  char * fixed_file_name = NULL;
  unsigned int decimal_point = -1;

  int c;
  int flag_verbose = 0, exit_code = 0;
  while ((c = getopt (argc, argv, "hv")) != -1)
    switch (c) {
    case 'h':
      usage();
      goto bail;
      break;
    case 'v':
      flag_verbose = 1;
      break;
    default:
      abort ();
    }

  int index;
  for (index = 1; index < argc - optind + 1; index++) {
    int index_optind = optind + index - 1;
    switch (index) {
    case 1:
      if ((ann = fann_create_from_file(argv[index_optind])) == 0) {
        fprintf(stderr, "[ERROR] Failed to read ANN %s\n", argv[index_optind]);
        usage();
        exit_code = 2;
        goto bail;
      }
      if (flag_verbose)
        printf("[INFO] Reading floating point net: %s\n", argv[index_optind]);
      break;
    case 2:
      fixed_file_name = argv[index_optind];
      if (flag_verbose)
        printf("[INFO] Will write to fixed point net: %s\n", argv[index_optind]);
      break;
    default:
      fprintf(stderr, "[ERROR] Too many arguments\n\n");
      usage();
      exit_code = 1;
      goto bail;
    }
  }

  if (ann == NULL || fixed_file_name == NULL) {
    fprintf(stderr, "[ERROR] Missing input arguments\n\n");
    usage();
    exit_code = 1;
    goto bail;
  }

  decimal_point = fann_save_to_fixed(ann, fixed_file_name);
  if (flag_verbose)
    printf("[INFO] Decimal point is: %d\n", decimal_point);

 bail:
  if (ann != NULL)
    fann_destroy(ann);
  return exit_code;
}
