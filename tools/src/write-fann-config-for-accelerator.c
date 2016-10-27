// Writes a binary configuration file suitable for use with the
// accelerator. The format can be found in
// `nnsim-hdl/src/include/types.vh`

#include <stdio.h>
#include <stdint.h>
#include <math.h>
#include <getopt.h>

#include "fixedfann.h"

void usage()
{
  printf("Usage: write-fann-config-for-accelerator [OPTIONS] <block width (bytes)> <FANN config> <bin out> <decimal point offset>\n"
         "\n"
         "Options:\n"
         "  -h, --help                 print this help and exit\n"
         "  -v, --verbose              print exhaustive debug info\n"
         );
}

int main(int argc, char *argv[])
{
  int i;
  char * null = NULL;
  struct fann * ann = NULL;
  struct fann_neuron * neuron;
  struct fann_layer * layer;
  int flag_verbose = 0;
  int write_count, exit_code = 0;

  int c;
  while ((c = getopt (argc, argv, "hv")) != -1)
    switch (c) {
      case 'h':
        usage();
        goto bail;
        break;
      case 'v':
        flag_verbose = 1;
        break;
      default:
        abort ();
      }

  FILE * file = NULL;
  int size_of_block = -1;
  int decimal_point_offset = -1024;
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
        exit_code = 1;
        goto bail;
      }
      break;
    case 3:
      if ((file = fopen(argv[index_optind], "w")) == 0) {
        fprintf(stderr, "[ERROR] Failed to open bin out %s\n", argv[index_optind]);
        usage();
        exit_code = 1;
        goto bail;
      }
      break;
    case 4:
      decimal_point_offset = strtol(argv[index_optind], (char **) NULL, 10);
      break;
    default:
      fprintf(stderr, "[ERROR] Too many arguments\n\n");
      usage();
      exit_code = 1;
      goto bail;
    }
  }

  if (file == NULL || size_of_block == -1 || decimal_point_offset == -1024) {
    fprintf(stderr, "[ERROR] Missing command line arguments\n");
    usage();
    exit_code = 1;
    goto bail;
  }

  // This is a bunch of zeros equal to the size of a block. This is
  // used for writing free space into the binary.
  null = calloc(size_of_block, sizeof(char));

  // All pointers are 2 bytes (16 bits)
  int size_of_pointer = 2;
  // Each layer is composed of two pointers
  int size_of_layer = 2 * size_of_pointer;
  // Each neuron is composed of a weight pointer, the number of
  // weights, config (5-bit activation function and 3-bits unused),
  // and an activation steepness.
  int size_of_num_weights = 1;
  int size_of_config = 1;
  int size_of_steepness = 4;
  int size_of_node =
    size_of_pointer + size_of_num_weights + size_of_config + size_of_steepness;
  // Each weight is just a 4-byte (32-bit) value
  int size_of_weight = 4;

  int layers_per_block = size_of_block / size_of_layer;
  int nodes_per_block = size_of_block / size_of_node;
  int weights_per_block = size_of_block / size_of_weight;

  if (flag_verbose)
    printf("Sizes (#/block)\n  Block: %d\n  Layer: %d (%d)\n"
           "Neuron: %d (%d)\n  Weight: %d (%d)\n",
           size_of_block, size_of_layer, layers_per_block, size_of_node,
           nodes_per_block, size_of_weight, weights_per_block);

  // The decimal point can be up to three bits and has an minimum
  // value of decimal point offset. The maximum value is decimal point
  // offset + 2^3 - 1.
  unsigned int decimal_point_encoded = ann->decimal_point - decimal_point_offset;
  if (flag_verbose)
    printf("Decimal point: 0x%x (%d), encoded: 0x%x\n",
           ann->decimal_point, ann->decimal_point, decimal_point_encoded);
  if (decimal_point_encoded > 7) {
    fprintf(stderr, "[ERROR] Decimal point (%d) is not in range [%d, %d]\n",
            ann->decimal_point, decimal_point_offset, decimal_point_offset + 7);
    exit_code = 3;
    goto bail;
  }

  // The error function needs to be stored. This is a 1-bit value in
  // FANN. This value is stored in the the 4th bit, just after the
  // encoded decimal point.
  unsigned int error_function = ann->train_error_function;
  decimal_point_encoded |= error_function << 3;
  if (flag_verbose) {
    printf("Error function: 0x%x (%d)\n", error_function, error_function);
    printf("Encoded decimal point is now: 0x%x\n", decimal_point_encoded);
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
      exit_code = 2;
      goto bail;
  }
  decimal_point_encoded |= block_width_encoded << 4;
  if (flag_verbose) {
    printf("Block width encoded: 0x%x (%d)\n", block_width_encoded,
           block_width_encoded);
    printf("Encoded decimal point is now: 0x%x\n", decimal_point_encoded);
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
  if (flag_verbose) {
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
    for (neuron = layer->first_neuron; neuron != layer->last_neuron - 1; neuron++) {
      weights += size_of_node;
    }
    if (weights % size_of_block != 0)
      weights += size_of_block - (weights % size_of_block);
  }
  if (flag_verbose)
    printf("Weights *: 0x%x (%d)\n", weights, weights);

  // Write the learning rate. Use a default value if the learning rate
  // is set to zero.
  uint16_t learning_rate = ann->learning_rate;
  if (learning_rate == 0)
    learning_rate = 0.25 * pow(2, ann->decimal_point);

  // Weight decay labmda
  // uint16_t weight_decay = (uint16_t)((double) 0.01 * pow(2, ann->decimal_point));
  uint16_t weight_decay = (uint16_t)((double)0.008 * pow(2.0, ann->decimal_point));
  if (weight_decay == 0) {
    fprintf(stderr, "[ERROR] Weight decay would be zero (found 0x%x), exiting!",
            weight_decay);
    exit_code = 4;
    goto bail;
  }

  // Write the Info Block
  fwrite(&decimal_point_encoded, 2, 1, file);
  fwrite(&num_weight_blocks, 2, 1, file);
  fwrite(&num_nodes, 2, 1, file);
  fwrite(&num_layers, 2, 1, file);
  fwrite(&first_layer, 2, 1, file);
  fwrite(&weights, 2, 1, file);
  fwrite(&learning_rate, 2, 1, file);
  fwrite(&weight_decay, 2, 1, file);
  // Write the free space following the Info Block if needed
  fwrite(null, size_of_block-16, 1, file);

  // Write the Layer Blocks
  // int neuron_pointer = first_node;
  int nodes_per_layer, nodes_per_previous_layer, next_node;
  int packed_layer_data;
  write_count = size_of_block;
  i = 0;
  next_node = first_node;
  for (layer = ann->first_layer + 1; layer != ann->last_layer; layer++) {
    nodes_per_layer = layer->last_neuron - layer->first_neuron - 1;
    nodes_per_previous_layer = (layer-1)->last_neuron-(layer-1)->first_neuron-1;
    packed_layer_data = (next_node & 0xfff) |
      ((nodes_per_layer & 0x3ff) << 12) |
      ((nodes_per_previous_layer & 0x3ff) << (12 + 10));
    fwrite(&packed_layer_data, 4, 1, file);
    // fwrite(&next_node, 2, 1, file);
    // fwrite(&nodes_per_layer, 2, 1, file);
    write_count -= size_of_layer;
    if (write_count == 0)
      write_count = size_of_block;
    if (flag_verbose) {
      printf("Layer %d: 0x%x is first node, 0x%x (%d) nodes/layer, "
             "0x%x (%d) nodes/previous layer\n", i,
           next_node, nodes_per_layer, nodes_per_layer,
           nodes_per_previous_layer, nodes_per_previous_layer);
      printf("  Packed: 0x%08x\n", packed_layer_data);
    }
    next_node += nodes_per_layer * size_of_node;
    if (next_node % size_of_block)
      next_node += size_of_block - (next_node % size_of_block);
    i++;
  }
  // Write the remainder of the block if needed
  if (write_count % size_of_block)
    fwrite(null, write_count, 1, file);

  // Write the Neuron Blocks
  int connections;
  int weight_offset = weights;
  int config;
  int node_count, layer_count, weight_count;
  double steepness;
  layer_count = 0;
  weight_count = 0;
  for (layer = ann->first_layer + 1; layer != ann->last_layer; layer++) {
    node_count = 0;
    write_count = size_of_block;
    for (neuron = layer->first_neuron; neuron != layer->last_neuron - 1; neuron++) {
      weight_count += neuron->last_con - neuron->first_con;
      fwrite(&weight_offset, 2, 1, file);
      connections = neuron->last_con - neuron->first_con - 1;
      fwrite(&connections, 1, 1, file);
      // The neuron activation steepness is assumed to be a power of
      // two between 1/16 and 8. This is encoded using the 3 remaining
      // bits we have in the config block.
      steepness = log((double)neuron->activation_steepness /
                      pow(2, ann->decimal_point)) / log(2) + 4;
      // Check to make sure that the steepness is a power of 2 and its
      // in the correct range.
      if ((steepness != round(steepness)) ||
          (steepness < 0) ||
          (steepness > 7)) {
        fprintf(stderr, "[ERROR] Steepness %d is not of the correct format\n",
                neuron->activation_steepness);
        exit_code = 5;
        goto bail;
      }
      config = neuron->activation_function | (int) steepness << 5;
      fwrite(&config, 1, 1, file);
      // Write bias
      fwrite(&ann->weights[weight_count - 1], 4, 1, file);
      write_count -= size_of_node;
      if (write_count == 0)
        write_count = size_of_block;
      weight_offset += size_of_weight * (neuron->last_con - neuron->first_con - 1);
      if (flag_verbose) {
        printf("L%dN%d: 0x%x is the weight ptr, 0x%x (%d) total weights, ",
               layer_count, node_count,
               weight_offset, connections, connections);
        printf("0x%x (%d) config, 0x%x (%d) bias\n", config, config,
               ann->weights[weight_count - 1], ann->weights[weight_count - 1]);
        printf("  Computed weight offset pre-round: 0x%x", weight_offset);
      }
      if (weight_offset % size_of_block != 0)
        weight_offset += size_of_block - (weight_offset % size_of_block);
      if (flag_verbose)
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
  for (layer = ann->first_layer + 1; layer != ann->last_layer; layer++) {
    node_count = 0;
    for (neuron = layer->first_neuron; neuron != layer->last_neuron - 1; neuron++) {
      write_count = size_of_block;
      if (flag_verbose)
        printf("L%dN%d: ", layer_count, node_count);
      for (connection = neuron->first_con; connection != neuron->last_con - 1; connection++) {
        if (flag_verbose)
          printf("0x%08x (%d) ", ann->weights[i], ann->weights[i]);
        fwrite(&ann->weights[i], 4, 1, file);
        write_count -= size_of_weight;
        if (write_count == 0)
          write_count = size_of_block;
        i++;
      }
      if (flag_verbose)
        printf("\n");
      // Align everything
      if (write_count % size_of_block)
        fwrite(null, write_count, 1, file);
      node_count++;
      // Need to increment the counter to compensate for the bias node
      i++;
    }
    layer_count++;
  }

 bail:
  if (file != NULL)
    fclose(file);
  if (ann != NULL)
    fann_destroy(ann);
  if (null != NULL) // Try this on for size...
    free(null);

  return exit_code;
}
