#include <stdio.h>
#include <stdlib.h>
#include <getopt.h>

#include "fann.h"

static char * usage_message =
  "fann-random -l[1st hidden size] -l[2nd hidden size] -l... [options] file\n"
  "Generate a random floating point fann network with a specific topology\n"
  "and write it to the specified file. The network must have at least two layers.\n"
  "\n"
  "Options:\n"
  "  -a, --af-hidden            set the activation function of all hidden neurons\n"
  "  -h, --help                 print this help and exit\n"
  "  -l, --layers               adds a layer to the network of a specific size\n"
  "  -n, --randomize-nguyen     initialize weights using Nguyen-Widrow (needs data)\n"
  "  -o, --af-output            set the activation function of all output neurons\n"
  "  -r, --randomize-weights    randomize weights on a specified range\n"
  "\n"
  "Only of of -n or -r may be specified\n"
  ;

void usage () {
  printf("Usage: %s", usage_message);
}

typedef struct {
  int size;
  int valid;
  double weight_random;
  char * weight_nguyen;
  enum fann_activationfunc_enum af_hidden;
  enum fann_activationfunc_enum af_output;
  unsigned int * layer_array;
} layers_struct;

void add_layer (layers_struct ** layers, int new_layer) {
  static int realloc_size = 4;

  // Reallocate the layer array if needed
  if ((*layers)->valid == (*layers)->size) {
    (*layers)->layer_array =
    (unsigned int *) realloc ((*layers)->layer_array,
                              ((*layers)->size + realloc_size) * sizeof(int));
    (*layers)->size += realloc_size;
  }

  (*layers)->layer_array[(*layers)->valid] = new_layer;
  (*layers)->valid++;
}

int main (int argc, char * argv[]) {
  int c;
  layers_struct * layers;
  const char * file;

  layers = (layers_struct *) malloc (4 * sizeof(layers_struct));

  layers->size = 4;
  layers->valid = 0;
  layers->weight_random = 0.0;
  layers->weight_nguyen = NULL;
  layers->af_hidden = FANN_SIGMOID_STEPWISE;
  layers->af_output = FANN_SIGMOID_STEPWISE;
  layers->layer_array = (unsigned int *) malloc (layers->size * sizeof(int));

  while (1) {
    static struct option long_options[] = {
      {"af-hidden",          required_argument, 0, 'a'},
      {"help",               no_argument,       0, 'h'},
      {"add-layer",          required_argument, 0, 'l'},
      {"randomize-nguyen",   required_argument, 0, 'n'},
      {"af-ouput",           required_argument, 0, 'o'},
      {"randomize-weights",  required_argument, 0, 'r'}
    };
    int option_index = 0;
    c = getopt_long (argc, argv, "a:hl:n:o:r:", long_options, &option_index);
    if (c == -1)
      break;
    switch (c) {
    case 'a':
      layers->af_hidden = atoi(optarg);
      break;
    case 'h':
      usage();
      goto failure;
    case 'l':
      add_layer(&layers, atoi(optarg));
      break;
    case 'n':
      layers->weight_nguyen = optarg;
      break;
    case 'o':
      layers->af_output = atoi(optarg);
      break;
    case 'r':
      layers->weight_random = atof(optarg);
      break;
    default:
      abort ();
    }
  }

  if (optind != argc - 1) {
    fprintf(stderr, "[ERROR] Missing output file\n\n");
    usage();
    goto failure;
  }
  file = argv[optind];

  if (layers->valid < 2) {
    fprintf(stderr, "[ERROR] Network needs at least two layers\n\n");
    usage();
    goto failure;
  }

  if (layers->weight_random != 0.0 && layers->weight_nguyen) {
    fprintf(stderr,
            "[ERROR] Both regular (-r) and nguyen (-n) randomization specified\n\n");
    usage();
    goto failure;
  }

  // Create the network
  struct fann * ann;
  struct fann_train_data * data;
  ann = fann_create_standard_array(layers->valid, layers->layer_array);
  fann_set_activation_function_hidden(ann, layers->af_hidden);
  fann_set_activation_function_output(ann, layers->af_output);
  if (layers->weight_random != 0.0)
    fann_randomize_weights(ann, -layers->weight_random, layers->weight_random);
  else if (layers->weight_nguyen != NULL) {
    data = fann_read_train_from_file(layers->weight_nguyen);
    fann_init_weights(ann, data);
    fann_destroy_train(data);
  }
  fann_save(ann, file);
  fann_destroy(ann);

  // Destroy the layers array
 failure:
  free(layers->layer_array);
  free(layers);

  return 0;
}
