// See LICENSE.BU for license details.

#include "fann.h"

// Transaction class that encapsulates a single transaction, i.e., a
// request by a thread to compute the output of neural network
// (specified by an NNID) for a given input vector.

class transaction {
private:
  unsigned int count_in, count_out, count_reads, decimal_point;
  int output_fann_th, output_dana_th;

public:
  struct fann * ann;
  unsigned int num_input, num_output;
  std::vector<int32_t> inputs;
  std::vector<int32_t> outputs;
  std::vector<fann_type> outputs_fann;
  uint16_t asid;
  uint16_t tid;
  uint16_t num_rounds;
  uint32_t nnid;
  double error, error_squared;
  int bound_failures;
  int bit_failures;

  transaction(fann *, fann_type *, uint16_t, uint32_t, unsigned int);
  int32_t get_input();
  bool done_in();
  bool done_out();
  bool new_read();
  void update_error(double);
};
