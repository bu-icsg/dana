// See LICENSE.BU for license details.
// See LICENSE.IBM for license details.

// Writes a binary configuration file suitable for use with the
// accelerator. The format can be found in
// `nnsim-hdl/src/include/types.vh`

#include <stdio.h>
#include <stdint.h>
#include <math.h>
#include <getopt.h>

#include "fann/src/include/fixedfann.h"
#include "tools/src/copyright.h"
#include "tools/src/encoding.h"

struct opt_t {
  int verbose;
  int decimal_point_offset;
};

void usage()
{
  fprintf(stderr, "Usage: write-fann-config-for-accelerator [OPTIONS] <block width (bytes)> <FANN config> <bin out> <decimal point offset>\n"
         "\n"
         "Options:\n"
         "  -h, --help                 print this help and exit\n"
         "  -v, --verbose              print exhaustive debug info\n"
         );
}

void global_info_printf(const struct global_info_t * global_info,
                        const struct fann * ann) {
  // The decimal point can be up to three bits and has an minimum
  // value of decimal point offset. The maximum value is decimal point
  // offset + 2^3 - 1.
  printf("Decimal point: 0x%x (%d), encoded: 0x%x\n",
         ann->decimal_point, ann->decimal_point, global_info->decimal_point);

  printf("Error function: 0x%x (%d)\n", global_info->error_function,
         global_info->error_function);

  printf("Block width encoded: 0x%x (%d)\n", global_info->binary_format,
         global_info->binary_format);
}

int global_info_verify(const struct global_info_t * info,
                       const struct fann * ann, const struct opt_t * opt) {
  if (info->decimal_point > 7) {
    fprintf(stderr, "[ERROR] Decimal point (%d) is not in range [%d, %d]\n",
            ann->decimal_point, opt->decimal_point_offset,
            opt->decimal_point_offset + 7);
    return VERIFY_GLOBAL_FAILED;
  }

  return NO_ERROR;
}

int neuron_info_verify(const struct neuron_info_t * info,
                       const struct fann_neuron * neuron) {
  if ((info->steepness != round(info->steepness)) ||
      (info->steepness < 0) ||
      (info->steepness > 7)) {
    fprintf(stderr, "[ERROR] Steepness %d is not of the correct format\n",
            neuron->activation_steepness);
    return VERIFY_NEURON_FAILED;
  }

  return NO_ERROR;
}

void neuron_info_printf(const struct neuron_info_t * info,
                        const struct fann * ann,
                        int layer, int node) {
  printf("L%dN%d: 0x%x is the weight ptr, 0x%x (%d) total weights, ",
         layer, node, info->ptr_weight_offset, info->num_weights,
         info->num_weights);
  printf("0x%x activation_function, 0x%x steepness, 0x%x (%d) bias\n",
         info->activation_function, info->steepness, info->bias, info->bias);
  printf("  Computed weight offset pre-round: 0x%x", info->ptr_weight_offset);
}

int main(int argc, char *argv[])
{
  PRINT_NOTICES(COPYRIGHT_FANN);
  int i;
  char * null = NULL;
  struct fann * ann = NULL;
  struct fann_neuron * neuron;
  struct fann_layer * layer;
  int write_count, exit_code = 0;

  FILE * file = NULL;
  int c;
  struct opt_t opt = {
    .verbose = 0,
    .decimal_point_offset = -1024
  };
  while ((c = getopt (argc, argv, "hv")) != -1)
    switch (c) {
      case 'h': usage(); goto bail;
      case 'v': opt.verbose = 1; break;
      default:
        fprintf(stderr, "[error] unknown command line argument\n");
        usage();
        exit_code = BAD_ARGUMENTS;
        goto bail;
    }

  int size_of_block = -1;
  int index;
  for (index = 1; index < argc - optind + 1; index++) {
    int index_optind = optind + index - 1;
    switch (index) {
      case 1:
        size_of_block = strtol(argv[index_optind], (char **) NULL, 10);
        break;
      case 2:
        if ((ann = fann_create_from_file(argv[index_optind])) == 0) {
          fprintf(stderr, "[ERROR] Failed to read ANN from %s\n", argv[index_optind]);
          usage();
          exit_code = FAILED_TO_READ_ANN_FROM_FILE;
          goto bail;
        }
        break;
      case 3:
        if ((file = fopen(argv[index_optind], "w")) == 0) {
          fprintf(stderr, "[ERROR] Failed to open bin out %s\n", argv[index_optind]);
          usage();
          exit_code = FAILED_TO_OPEN_BIN_OUT;
          goto bail;
        }
        break;
      case 4:
        opt.decimal_point_offset = strtol(argv[index_optind], (char **) NULL, 10);
        break;
      default:
        fprintf(stderr, "[ERROR] Too many arguments\n\n");
        usage();
        exit_code = BAD_ARGUMENTS;
        goto bail;
    }
  }

  if (file == NULL || size_of_block == -1 || opt.decimal_point_offset == -1024) {
    fprintf(stderr, "[ERROR] Missing command line arguments\n");
    usage();
    exit_code = BAD_ARGUMENTS;
    goto bail;
  }

  // This is a bunch of zeros equal to the size of a block. This is
  // used for writing free space into the binary.
  null = calloc(size_of_block, sizeof(char));

  // Each neuron is composed of a weight pointer, the number of
  // weights, config (5-bit activation function and 3-bits unused),
  // and an activation steepness.
  int size_of_node = sizeof(struct neuron_info_t);
  // Each weight is just a 4-byte (32-bit) value
  int size_of_weight = sizeof(dana_data_t);

  int layers_per_block = size_of_block / sizeof(struct layer_info_t);
  int nodes_per_block = size_of_block / sizeof(struct neuron_info_t);
  int weights_per_block = size_of_block / size_of_weight;

  if (opt.verbose)
    printf("Sizes (#/block)\n  Block: %d\n  Layer: %ld (%d)\n"
           "Neuron: %d (%d)\n  Weight: %d (%d)\n",
           size_of_block, sizeof(struct layer_info_t), layers_per_block,
           size_of_node, nodes_per_block, size_of_weight, weights_per_block);

  if (layers_per_block == 0 || nodes_per_block == 0 || weights_per_block == 0) {
    fprintf(stderr, "[error] Choice of encoding results in struct > 16B");
    exit_code = STRUCT_LARGER_THAN_16B;
    goto bail;
  }

  // Encode the block width and append this to the encoded decimal
  // point. The block width must be [16, 32, 64, 128] which is encoded
  // as [0, 1, 2, 3].
  unsigned int block_width_encoded;
  switch (size_of_block) {
    case (16):  block_width_encoded = 0; break;
    case (32):  block_width_encoded = 1; break;
    case (64):  block_width_encoded = 2; break;
    case (128): block_width_encoded = 3; break;
    default:
      fprintf(stderr, "[ERROR] Unsupported block width %d\n", size_of_block);
      exit_code = UNSUPPORTED_BLOCK_WIDTH;
      goto bail;
  }

  // Compute the number of edges and nodes. This is the actual number
  // and not the FANN number. Consequently, I need to remove any bias
  // connections, input nodes, and hidden nodes.
  int num_edges = ann->total_connections;
  int num_nodes = 0;
  int num_weight_blocks = 0;
  int num_weight_blocks_tmp = 0;
  int num_connections;
  for (layer = ann->first_layer + 1; layer != ann->last_layer; layer++) {
    num_nodes += (int)(layer->last_neuron - layer->first_neuron - 1);
    for (neuron = layer->first_neuron;neuron !=layer->last_neuron-1;neuron++){
      num_connections = neuron->last_con - neuron->first_con - 1;
      num_weight_blocks_tmp = num_connections * size_of_weight / size_of_block;
      num_weight_blocks += ((num_connections * size_of_weight) % size_of_block) ?
                           num_weight_blocks_tmp + 1 : num_weight_blocks_tmp;
      num_edges--;
    }
  }

  int num_layers = ann->last_layer - ann->first_layer - 1;

  // The first layer is always at byte 16
  int first_layer = size_of_block * 1;

  // Each layer takes up 4 bytes. One block is 16 bytes. Any remaining
  // space due to the number of layers not being divisible by 4 is
  // left vacant. This is the location of the first neuron.
  int first_node = first_layer +
                   (num_layers / layers_per_block + (num_layers % layers_per_block != 0)) *
                   size_of_block;
  if (opt.verbose) {
    printf("Total Edges: 0x%x (%d)\n", num_edges, num_edges);
    printf("Total Weight Blocks: 0x%x (%d)\n", num_weight_blocks, num_weight_blocks);
    printf("Total Neurons: 0x%x (%d)\n", num_nodes, num_nodes);
    printf("Total Layers: 0x%x (%d)\n", num_layers, num_layers);
    printf("First Layer *: 0x%x\n", first_layer);
    printf("[NOT USED] First Neuron *: 0x%x (%d)\n", first_node, first_node);
  }

  // Pointer to the weights. This is the block immediately following
  // the layers and neurons. I need to do some math to figure out
  // where this actually is due to the special alignment constraints.
  int weights = first_node;
  for (layer = ann->first_layer + 1; layer != ann->last_layer; layer++) {
    for (neuron = layer->first_neuron; neuron != layer->last_neuron - 1; neuron++)
      weights += size_of_node;
    if (weights % size_of_block != 0)
      weights += size_of_block - (weights % size_of_block);
  }
  if (opt.verbose)
    printf("Weights *: 0x%x (%d)\n", weights, weights);

  struct global_info_t global_info = {
    .decimal_point       = ann->decimal_point - opt.decimal_point_offset,
    .error_function      = ann->train_error_function,
    .binary_format       = block_width_encoded,
    ._unused_0           = 0,
    .total_weight_blocks = num_weight_blocks,
    .total_neurons       = num_nodes,
    .total_layers        = num_layers,
    .ptr_first_layer     = first_layer,
    .ptr_weights         = weights
  };

  if ((exit_code = global_info_verify(&global_info, ann, &opt)) != 0)
    goto bail;
  if (opt.verbose)
    global_info_printf(&global_info, ann);

  fwrite(&global_info, sizeof(struct global_info_t), 1, file);
  fwrite(null, size_of_block - sizeof(struct global_info_t), 1, file);

  // Write the Layer Blocks
  // int neuron_pointer = first_node;
  int nodes_per_layer, nodes_per_previous_layer, next_node;
  write_count = size_of_block;
  next_node = first_node;
  for (layer = ann->first_layer + 1, i = 0; layer != ann->last_layer; layer++, i++) {
    nodes_per_layer = layer->last_neuron - layer->first_neuron - 1;
    nodes_per_previous_layer = (layer-1)->last_neuron-(layer-1)->first_neuron-1;

    struct layer_info_t layer_info = {
      .ptr_neuron           = next_node,
      .num_neurons          = nodes_per_layer,
      .num_neurons_previous = nodes_per_previous_layer
    };
    fwrite(&layer_info, sizeof(struct layer_info_t), 1, file);

    write_count -= sizeof(layer_info);
    if (write_count == 0)
      write_count = size_of_block;
    if (opt.verbose) {
      printf("Layer %d: 0x%x is first node, 0x%x (%d) nodes/layer, "
             "0x%x (%d) nodes/previous layer\n", i,
             next_node, nodes_per_layer, nodes_per_layer,
             nodes_per_previous_layer, nodes_per_previous_layer);
    }

    next_node += nodes_per_layer * size_of_node;
    if (next_node % size_of_block)
      next_node += size_of_block - (next_node % size_of_block);
  }
  // Write the remainder of the block if needed
  if (write_count % size_of_block)
    fwrite(null, write_count, 1, file);

  // Write the Neuron Blocks
  int connections;
  int weight_offset = weights;
  int node_count, layer_count, weight_count;
  double steepness;
  layer_count = 0;
  weight_count = 0;
  for (layer = ann->first_layer + 1; layer != ann->last_layer; layer++) {
    node_count = 0;
    write_count = size_of_block;
    for (neuron = layer->first_neuron; neuron != layer->last_neuron - 1; neuron++) {
      weight_count += neuron->last_con - neuron->first_con;
      connections = neuron->last_con - neuron->first_con - 1;
      steepness = log((double)neuron->activation_steepness /
                      pow(2, ann->decimal_point)) / log(2) + 4;

      struct neuron_info_t neuron_info = {
        .ptr_weight_offset   = weight_offset,
        .num_weights         = connections,
        .activation_function = neuron->activation_function,
        .steepness           = steepness,
        ._unused_0           = 0,
        ._unused_1           = 0,
        .bias                = ann->weights[weight_count - 1]
      };
      neuron_info_verify(&neuron_info, neuron);
      fwrite(&neuron_info, sizeof(struct neuron_info_t), 1, file);

      write_count -= size_of_node;
      if (write_count == 0)
        write_count = size_of_block;
      if (weight_offset + size_of_weight * (neuron->last_con - neuron->first_con - 1) <
          weight_offset) {
        fprintf(stderr, "[ERROR] Unable to encode weight offset (0x%x) in dana_ptr_t (%ld bits)\n",
                weight_offset, sizeof(dana_ptr_t) * 8);
      }
      weight_offset += size_of_weight * (neuron->last_con - neuron->first_con - 1);
      if (opt.verbose)
        neuron_info_printf(&neuron_info, ann, layer_count, node_count);

      if (weight_offset % size_of_block != 0)
        weight_offset += size_of_block - (weight_offset % size_of_block);
      if (opt.verbose)
        printf(" (post: 0x%x)\n", weight_offset);
      node_count++;
    }
    // Align the next write
    if (write_count % size_of_block)
      fwrite(null, write_count, 1, file);
    layer_count++;
  }

  // Write the Weight Blocks. Bias blocks are not written here as they
  // have already been included in each neuron block.
  int connection;
  i = 0;
  layer_count = 0;
  for (layer = ann->first_layer + 1; layer != ann->last_layer; layer++,
       layer_count++) {
    node_count = 0;
    for (neuron = layer->first_neuron; neuron != layer->last_neuron - 1; neuron++,
         i++) {
      write_count = size_of_block;
      if (opt.verbose)
        printf("L%dN%d: ", layer_count, node_count);
      for (connection = neuron->first_con; connection != neuron->last_con - 1;
           connection++, i++) {
        if (opt.verbose)
          printf("0x%08x (%d) ", ann->weights[i], ann->weights[i]);
        fwrite(&ann->weights[i], sizeof(dana_data_t), 1, file);
        write_count -= size_of_weight;
        if (write_count == 0)
          write_count = size_of_block;
      }
      if (opt.verbose)
        printf("\n");
      // Align everything
      if (write_count % size_of_block)
        fwrite(null, write_count, 1, file);
      node_count++;
    }
  }

bail:
  if (file != NULL)
    fclose(file);
  if (ann != NULL)
    fann_destroy(ann);
  if (null != NULL)
    free(null);

  return exit_code;
}
