#include "fann.h"

int main(int argc, char *argv[])
{
    struct fann * ann;
    unsigned int decimal_point;

    if (argc != 3) {
      fprintf(stderr, "usage:\n  %s <floating point net> <fixed point net>\n",
              argv[0]);
      return 1;
    }

    printf("[INFO] Reading floating point net:\n[INFO]   %s\n", argv[1]);
    ann = fann_create_from_file(argv[1]);
    if (!ann) {
      fprintf(stderr, "ERROR: in fann_create_from_file\n");
      return -1;
    }
    printf("[INFO] Writing fixed point net:\n[INFO]   %s\n", argv[2]);
    decimal_point = fann_save_to_fixed(ann, argv[2]);
    printf("[INFO] Decimal point is: %d\n", decimal_point);

    fann_destroy(ann);
    return 0;
}
