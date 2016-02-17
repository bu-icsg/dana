// See LICENSE for license details.

#ifndef __XFILES_DANA_H__
#define __XFILES_DANA_H__

#include <iomanip>
#include "emulator.h"

class xfiles_dana_helper : public Top_api_t {
private:
  struct {
    uint64_t num_pes;
    uint64_t cache_num_entries;
    uint64_t elements_per_block;
    uint64_t transaction_table_num_entries;
    uint64_t transaction_table_sram_elements;
    uint64_t register_file_num_elements;
    uint64_t asid_width;
    uint64_t tid_width;
    uint64_t nnid_width;
    uint64_t feedback_width;
    uint64_t element_width;
    uint64_t num_cores;
    uint64_t decimal_point_offset;
    uint64_t decimal_point_width;
  } parameters;

public:
  xfiles_dana_helper();
  ~xfiles_dana_helper();

  // Load the cache so that memory requests aren't necessary
  int cache_load(int, uint32_t, const char *, bool);

  // Read a parameter file and populate the local parameters
  int read_parameters(const string);
};

#endif
