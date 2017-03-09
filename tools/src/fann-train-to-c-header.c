// See LICENSE.BU for license details.

#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include "submodules/fann/src/include/fann.h"

int main (int argc, char * argv[]) {
  FILE * fp;
  struct fann * ann;
  int i, j, decimal_point, multiplier;
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

  // Figure out what the decimal point should be
  decimal_point = fann_save_to_fixed(ann, "/dev/null");
  multiplier = pow(2, decimal_point);

  // Read the header
  fscanf(fp, "%d %d %d", &num_data, &num_input, &num_output);
  printf("// Automatically generated using:\n//   %s %s %s %s\n",
         argv[0], argv[1], argv[2], argv[3]);
  printf("static int %s_decimal_point __attribute__((unused)) = %d;\n", argv[3],
         decimal_point);
  printf("static int %s_num_data __attribute__((unused)) = %d;\n", argv[3],
         num_data);
  printf("static int %s_num_input __attribute__((unused)) = %d;\n", argv[3],
         num_input);
  printf("static int %s_num_output __attribute__((unused)) = %d;\n", argv[3],
         num_output);
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
      fscanf(fp, "%f", &inputs[i][j]);
    for (j = 0; j < num_output; j++)
      fscanf(fp, "%f", &outputs_expected[i][j]);
    memcpy(outputs_fann[i], fann_run(ann, inputs[i]),
           num_output * sizeof(fann_type));
  }

  // Print out the inputs, expected, and actual outputs (what FANN produced)
  printf("static int %s_inputs[%d][%d] __attribute__((unused)) = {\n", argv[3],
         num_data, num_input);
  for (i = 0; i < num_data; i++) {
    printf("  {");
    for (j = 0; j < num_input - 1; j++)
      printf("%d,", (int) (inputs[i][j] * multiplier));
    printf("%d},\n", (int) (inputs[i][j] * multiplier));
  }
  printf("};\n");

  printf("static int %s_outputs_expected[%d][%d] __attribute__((unused)) = {\n",
         argv[3], num_data,
         num_output);
  for (i = 0; i < num_data; i++) {
    printf("  {");
    for (j = 0; j < num_output - 1; j++)
      printf("%d,", (int) (outputs_expected[i][j] * multiplier));
    printf("%d},\n", (int) (outputs_expected[i][j] * multiplier));
  }
  printf("};\n");

  printf("static int %s_outputs_fann[%d][%d] __attribute__((unused)) = {\n",
         argv[3], num_data,
         num_output);
  for (i = 0; i < num_data; i++) {
    printf("  {");
    for (j = 0; j < num_output - 1; j++)
      printf("%d,", (int) (outputs_fann[i][j] * multiplier));
    printf("%d},\n", (int) (outputs_fann[i][j] * multiplier));
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
