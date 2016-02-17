// See LICENSE for license details.

#include "transaction.h"

transaction::transaction(fann * _ann, fann_type * _inputs,
                         uint16_t _asid, uint32_t _nnid,
                         unsigned int _decimal_point) {
  ann = _ann;
  asid = _asid;
  nnid = _nnid;
  num_input = fann_get_num_input(ann);
  num_output = fann_get_num_output(ann);
  count_in = 0;
  count_out = 0;
  count_reads = 0;
  decimal_point = _decimal_point;
  inputs.resize(num_input);
  outputs_fann.resize(num_output);
  for (int i = 0; i < num_input; i++)
    inputs[i] = (int32_t) (_inputs[i] * pow(2, decimal_point));
  fann_type * tmp = fann_run(ann, _inputs);
  for (int i = 0; i < num_output; i++)
    outputs_fann[i] = tmp[i];
};

bool transaction::new_read() {
  count_reads++;
  return count_reads == num_output;
};

int32_t transaction::get_input() {
  return inputs[count_in++];
};

bool transaction::done_in() {
  return count_in == num_input - 1;
};

bool transaction::done_out() {
  return outputs.size() == num_output;
};

void transaction::update_error(double bound) {
  error = 0;
  error_squared = 0;
  bound_failures = 0;
  bit_failures = 0;
  assert(outputs.size() == num_output);
  double err;
  for (int i = 0; i < num_output; i++) {
    err = outputs_fann[i] - (double) outputs[i] / pow(2.0, decimal_point);
    error += err;
    error_squared += err * err;
    // Check to see if we're violating an error bound
    if (fabs(err) > bound) {
      printf("[ERROR] ABS Err (%f) > %0.5f on [TID: 0x%x, %d], found %d (%f), should be %f\n",
             fabs(err), bound, tid, i,
             outputs[i],
             (double) outputs[i] / pow(2.0, decimal_point),
             outputs_fann[i]);
      bound_failures++;
    }
    // Check to see if this results in a bit flip
    output_fann_th = outputs_fann[i] > 0.5 ? 1 : 0;
    output_dana_th = outputs[i] > (1 << (decimal_point - 1)) ? 1 : 0;
    if (output_fann_th != output_dana_th) {
      printf("[ERROR] Bit flip on [TID: 0x%x, %d], found %d (%f), should be %f\n",
             tid, i, outputs[i],
             (double) outputs[i] / pow(2.0, decimal_point),
             outputs_fann[i]);
      bit_failures++;
    }
  }
};
