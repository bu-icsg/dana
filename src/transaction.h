#include "fann.h"

// Transaction class that encapsulates a single transaction, i.e., a
// request by a thread to compute the output of neural network
// (specified by an NNID) for a given input vector.

class transaction {
private:
  fann * ann;
  fann_type * data;
  uint32_t nnid;
  uint16_t tid;
  unsigned int count_in, count_out, num_input, num_output, decimal_point;

public:
  std::vector<int32_t> inputs;
  std::vector<int32_t> outputs;

  transaction(fann *, fann_type *, uint32_t, unsigned int);
  int32_t next_in();
  void next_out(int32_t);
  bool done_in();
  bool done_out();
};
