#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include "fixedfann.h"

int main (int argc, char * argv[]) {
  FILE * fp;
  struct fann * ann;
  int i, j;
  unsigned int num_data, num_input, num_output;
  fann_type ** inputs, ** outputs_expected, ** outputs_fann;

  // Check that we have two input arguments
  if (argc != 4) {
    printf("Usage: %s <net file> <train file> <array name>\n", argv[0]);
    return -1;
  }

  // Open the training file and create the network
  fp = fopen(argv[2], "r");
  if (fp == NULL) {
    fprintf(stderr, "Failed to open file %s\n", argv[2]);
    return -1;
  }
  ann = fann_create_from_file(argv[1]);
  if (ann == NULL) {
    fprintf(stderr, "Failed to open FANN config %s\n", argv[1]);
    return -2;
  }

  // Read the header
  fscanf(fp, "%d %d %d", &num_data, &num_input, &num_output);
  printf("// Automatically generated using:\n//   %s %s %s %s\n",
         argv[0], argv[1], argv[2], argv[3]);
  printf("int %s_num_data = %d;\n", argv[3], num_data);
  printf("int %s_num_input = %d;\n", argv[3], num_input);
  printf("int %s_num_output = %d;\n", argv[3], num_output);
  inputs = (fann_type **) malloc(num_data * sizeof(fann_type *));
  outputs_expected = (fann_type **) malloc(num_data * sizeof(fann_type *));
  outputs_fann = (fann_type **) malloc(num_data * sizeof(fann_type *));
  for (i = 0; i < num_data; i++) {
    inputs[i] = (fann_type *) malloc(num_input * sizeof(fann_type));
    outputs_expected[i] = (fann_type *) malloc(num_input * sizeof(fann_type));
    outputs_fann[i] = (fann_type *) malloc(num_input * sizeof(fann_type));
  }

  // Read all the input--output pairs
  for (i = 0; i < num_data; i++) {
    for (j = 0; j < num_input; j++)
      fscanf(fp, FANNPRINTF, &inputs[i][j]);
    for (j = 0; j < num_output; j++)
      fscanf(fp, FANNPRINTF, &outputs_expected[i][j]);
    memcpy(outputs_fann[i], fann_run(ann, inputs[i]), num_output);
  }

  // Print out the inputs, expected, and actual outputs (what FANN produced)
  printf("int %s_inputs[%d][%d] = {\n", argv[3], num_data, num_input);
  for (i = 0; i < num_data; i++) {
    printf("  {");
    for (j = 0; j < num_input - 1; j++)
      printf("%d,", inputs[i][j]);
    printf("%d},\n", inputs[i][j]);
  }
  printf("};\n");

  printf("int %s_outputs_expected[%d][%d] = {\n", argv[3], num_data, num_output);
  for (i = 0; i < num_data; i++) {
    printf("  {");
    for (j = 0; j < num_output - 1; j++)
      printf("%d,", outputs_expected[i][j]);
    printf("%d},\n", outputs_expected[i][j]);
  }
  printf("};\n");

  printf("int %s_outputs_fann[%d][%d] = {\n", argv[3], num_data, num_output);
  for (i = 0; i < num_data; i++) {
    printf("  {");
    for (j = 0; j < num_output - 1; j++)
      printf("%d,", outputs_fann[i][j]);
    printf("%d},\n", outputs_fann[i][j]);
  }
  printf("};\n");

  // Cleanup
  for (i = 0; i < num_data; i++) {
    free(inputs[i]);
    free(outputs_expected[i]);
    free(outputs_fann[i]);
  }
  free(inputs);
  free(outputs_expected);
  free(outputs_fann);
  fann_destroy(ann);
  fclose(fp);
  return 0;
}
