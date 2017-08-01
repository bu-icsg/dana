// See LICENSE.IBM for license details.

#ifndef __TOOLS_SRC_ENCODING_H_
#define __TOOLS_SRC_ENCODING_H_

#define W_NEURON_FIRST_NEURON_POINTER 12
#define W_NEURON_NEURONS_IN_LAYER 10
#define W_NEURON_NEURONS_IN_PREVIOUS_LAYER W_NEURON_NEURONS_IN_LAYER

typedef uint32_t dana_ptr_t;  // internal configuration pointer
typedef uint32_t dana_data_t; // DANA arithmetic unit

struct global_info_t {
  uint16_t decimal_point  : 3;
  uint16_t error_function : 1;
  uint16_t binary_format  : 3;
  uint16_t _unused_0      : 9;
  uint16_t total_weight_blocks; // ???
  uint16_t total_neurons;
  uint16_t total_layers;
  dana_ptr_t ptr_first_layer;
  dana_ptr_t ptr_weights;
};

struct layer_info_t {
  dana_ptr_t ptr_neuron;
  uint32_t num_neurons          : 16;
  uint32_t num_neurons_previous : 16;
};

struct neuron_info_t {
  dana_ptr_t ptr_weight_offset;
  uint16_t num_weights;
  uint8_t activation_function : 5;
  uint8_t steepness           : 3;
  uint8_t _unused_0;
  uint32_t _unused_1;
  dana_data_t bias;
};

enum encoding_error_t {
  NO_ERROR = 0,
  FAILED_TO_READ_ANN_FROM_FILE,
  FAILED_TO_OPEN_BIN_OUT,
  BAD_ARGUMENTS,
  UNSUPPORTED_BLOCK_WIDTH,
  VERIFY_GLOBAL_FAILED,
  VERIFY_NEURON_FAILED,
  STRUCT_LARGER_THAN_16B
};


#endif  // __TOOLS_SRC_ENCODING_H_
