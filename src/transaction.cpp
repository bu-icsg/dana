#include "transaction.h"

transaction::transaction(fann * _ann, fann_type * _data, uint32_t _nnid,
                         unsigned int _decimal_point) {
  ann = _ann;
  data = _data;
  nnid = _nnid;
  num_input = fann_get_num_input(ann);
  num_output = fann_get_num_output(ann);
  count_in = 0;
  count_out = 0;
  decimal_point = _decimal_point;
  inputs.resize(num_input);
  for (int i = 0; i < num_input; i++)
    inputs[i] = (int32_t) data[i] << decimal_point;
};

int32_t transaction::get_input() {
  return inputs[count_in++];
};

bool transaction::done_in() {
  return count_in == num_input - 1;
};

bool transaction::done_out() {
  return count_out == num_output;
};
