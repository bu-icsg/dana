#include "fann.h"

// Transaction class that encapsulates a single transaction, i.e., a
// request by a thread to compute the output of neural network
// (specified by an NNID) for a given input vector.

class transaction {
private:
  fann * ann;
  fann_type * data;
  unsigned int count_in, count_out, num_input, num_output, decimal_point;

public:
  std::vector<int32_t> inputs;
  std::vector<int32_t> outputs;
  uint16_t tid;
  uint16_t num_rounds;
  uint32_t nnid;

  transaction(fann *, fann_type *, uint32_t, unsigned int);
  int32_t get_input();
  bool done_in();
  bool done_out();
};
