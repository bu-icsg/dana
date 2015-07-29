#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>

int main (int argc, char * argv[]) {
  FILE * fp;
  void * data;
  long int file_size;
  int i;

  // Check that we have two input arguments
  if (argc != 4) {
    printf("Usage: %s <binary config> <array name> <XLen (64, likely)?>\n", argv[0]);
    return -1;
  }

  // Read the complete original binary file
  fp = fopen(argv[1], "rb");
  fseek(fp, 0, SEEK_END);
  file_size = ftell(fp);
  fseek(fp, 0, SEEK_SET);
  switch (atoi(argv[3])) {
  case (32):
    file_size /= sizeof(uint32_t);
    data = (uint32_t *) malloc(file_size * sizeof(uint32_t));
    fread((uint32_t *) data, sizeof(uint32_t), file_size, fp);
    printf("static uint32_t %s[%ld] = \n{", argv[2], file_size);
    for (i = 0; i < file_size - 1; i++) {
      printf("0x%08x,", ((uint32_t *)data)[i]);
      if ((i + 1) %4 == 0)
        printf("\n ");
    }
    printf("0x%08x};\n", ((uint32_t *)data)[i]);
    break;
  case (64):
    file_size /= sizeof(uint64_t);
    data = (uint64_t *) malloc(file_size * sizeof(uint64_t));
    fread((uint64_t *) data, sizeof(uint64_t), file_size, fp);
    printf("static uint64_t %s[%ld] = \n{", argv[2], file_size);
    for (i = 0; i < file_size - 1; i++) {
      printf("0x%016lx,", ((uint64_t *)data)[i]);
      if ((i + 1) %2 == 0)
        printf("\n ");
    }
    printf("0x%016lx};\n", ((uint64_t *)data)[i]);
    break;
  case (128):
    // [TODO] Add support for this at some point
  default:
    printf("Only XLens of 32 or 64 are supported\n");
  }

  fclose(fp);
  free(data);
  return 0;
}
