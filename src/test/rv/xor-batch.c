#include <stdint.h>
#include <stdio.h>
#include <math.h>

#include "xfiles.h"
#include "dana-static-config.h"
#include "xor_train.h"

int main (int argc, char * argv[]) {
  int i, j, k;
  asid_type asid;
  tid_type tid;
  nnid_type nnid;
  uint32_t batch_items;
  // Inputs are sourced from xor_train.h
  element_type outputs[xor_num_output];

  // The NNID for this XOR application is hard-coded in DANA to 4. See
  // src/main/verilog/ram_infer_preloaded_cache.v for default
  // configurations.
  nnid = 7;
  asid = 1;
  batch_items = xor_num_data;

  set_asid(asid);

  // ASID--NNID Table Setup
  asid_nnid_table * table;
  asid_nnid_table_create(&table, 4, 17);
  printf("[INFO] After init, user sees ANTP as 0x%lx\n", (uint64_t) table);
  attach_nn_configuration_array(&table, asid, init_3sum_fixed_16bin_64,         // 0
                                sizeof(init_3sum_fixed_16bin_64)/sizeof(x_len));
  attach_nn_configuration_array(&table, asid, init_collatz_fixed_16bin_64,      // 1
                                sizeof(init_collatz_fixed_16bin_64)/sizeof(x_len));
  attach_nn_configuration_array(&table, asid, init_edip_fixed_16bin_64,         // 2
                                sizeof(init_edip_fixed_16bin_64)/sizeof(x_len));
  attach_nn_configuration_array(&table, asid, init_ll_fixed_16bin_64,           // 3
                                sizeof(init_ll_fixed_16bin_64)/sizeof(x_len));
  attach_nn_configuration_array(&table, asid, init_rsa_fixed_16bin_64,          // 4
                                sizeof(init_rsa_fixed_16bin_64)/sizeof(x_len));
  attach_nn_configuration_array(&table, asid, init_sobel_fixed_16bin_64,        // 5
                                sizeof(init_sobel_fixed_16bin_64)/sizeof(x_len));
  attach_nn_configuration_array(&table, asid, init_blackscholes_fixed_16bin_64, // 6
                                sizeof(init_blackscholes_fixed_16bin_64)/sizeof(x_len));
  attach_nn_configuration_array(&table, asid, init_xor_fixed_16bin_64,          // 7
                                sizeof(init_blackscholes_fixed_16bin_64)/sizeof(x_len));
  set_antp(table);

  // Run to get a baseline
  // Rerun to see if the outputs have changed after learning
  double error, mse_old, mse = 0.0;
  double multiplier = pow(2, xor_decimal_point);
  // for (j = 0; j < batch_items; j++) {
  //   tid = new_write_request(nnid, 0, 0);
  //   write_data(tid, xor_inputs[j], xor_num_input);
  //   read_data_spinlock(tid, outputs, xor_num_output);
  //   printf("[INFO] ");
  //   for (i = 0; i < xor_num_input; i++) {
  //     printf("%5d ", xor_inputs[j][i]);
  //   }
  //   for (i = 0; i < xor_num_output; i++) {
  //     printf("%5d", outputs[i]);
  //     error = (double)(outputs[i] - xor_outputs_expected[j][i]) / multiplier;
  //     mse += error * error;
  //   }
  //   printf("\n");
  // }
  mse_old = mse;

  for (k = 0; k < 100; k++) {
    for (j = 0; j < batch_items; j++) {
      // Generate a new transaction request for the XOR network by
      // referencing XOR's NNID. The X-Files arbiter returns a TID that we
      // can use to reference this transaction in the future.
      if (j == 0) {
        tid = new_write_request(nnid, 2, 0);
        write_register(tid, xfiles_reg_batch_items, batch_items);
        write_register(tid, xfiles_reg_learning_rate,
                       (uint32_t)((0.25 / batch_items) * multiplier));
      }

      // Write the output and input data
      write_data_train_incremental(tid, xor_inputs[j], xor_outputs_expected[j],
                                   xor_num_input, xor_num_output);
      // for (i = 0; i < 1000; i++) {
      //   asm volatile("nop");
      // }
      // printf("[INFO] Stop nopping...\n");
      // if (j > 0)
      //   return -1;

      // Read outputs
      read_data_spinlock(tid, outputs, xor_num_output);
      // Now that we have the outputs, we can print them
      // for (i = 0; i < xor_num_input; i++) {
      //   printf("%5d %5d ");
      // }
      // for (i = 0; i < xor_num_output; i++)
      //   printf("[INFO] output[%2d]: %0d\n", i, outputs[i]);
    }

    // Rerun to see if the outputs have changed after learning
    mse = 0.0;
    for (j = 0; j < batch_items; j++) {
      tid = new_write_request(nnid, 0, 0);
      write_data(tid, xor_inputs[j], xor_num_input);
      read_data_spinlock(tid, outputs, xor_num_output);
      printf("[INFO] ");
      for (i = 0; i < xor_num_input; i++) {
        printf("%5d ", xor_inputs[j][i]);
      }
      for (i = 0; i < xor_num_output; i++) {
        printf("%5d ", outputs[i]);
        error = (double)(outputs[i] - xor_outputs_expected[j][i]) / multiplier;
        mse += error * error;
      }
      if (j < batch_items - 1)
        printf("\n");
    }

    printf("%0.8f\n", mse / batch_items / xor_num_output);
  }
  printf("[INFO] mse_old: %0.8f\n", mse_old / batch_items / xor_num_output);


  printf("[INFO] Destroying ASID--NNID Table\n");
  asid_nnid_table_destroy(&table);
  return 0;

}
