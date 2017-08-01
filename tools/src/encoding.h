// See LICENSE.IBM for license details.

#ifndef __TOOLS_SRC_ENCODING_H_
#define __TOOLS_SRC_ENCODING_H_

#define W_NEURON_FIRST_NEURON_POINTER 12
#define W_NEURON_NEURONS_IN_LAYER 10
#define W_NEURON_NEURONS_IN_PREVIOUS_LAYER W_NEURON_NEURONS_IN_LAYER

typedef uint16_t dana_ptr_t;
typedef uint32_t dana_data_t;

struct global_info_t {
  uint16_t decimal_point  : 3;
  uint16_t error_function : 1;
  uint16_t binary_format  : 3;
  uint16_t unused_0       : 9;
  uint16_t total_weight_blocks; // ???
  uint16_t total_neurons;
  uint16_t total_layers;
  dana_ptr_t ptr_first_layer;
  dana_ptr_t ptr_weights;
  uint16_t learning_rate; // [todo] remove
  uint16_t weight_decay;  // [todo] remove
};

struct layer_info_t {
  uint32_t ptr_neuron           : 12;
  uint32_t num_neurons          : 10;
  uint32_t num_neurons_previous : 10;
};

struct neuron_info_t {
  dana_ptr_t ptr_weight_offset;
  uint16_t num_weights         : 8;
  uint16_t activation_function : 5;
  uint16_t steepness           : 3;
  dana_data_t bias;
};

enum encoding_error_t {
  FAILED_TO_READ_ANN_FROM_FILE = 1,
  FAILED_TO_OPEN_BIN_OUT,
  BAD_ARGUMENTS,
  UNSUPPORTED_BLOCK_WIDTH
};


#endif  // __TOOLS_SRC_ENCODING_H_
