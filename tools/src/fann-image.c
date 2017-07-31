// See LICENSE.IBM for license details.

// Preprocessing:
//   convert -trim -negate -resize '20x20' -gravity center -extent '28x28' -background black [image] -

#include <stdio.h>
#include <getopt.h>
#include <string.h>
#include <png.h>

#include "tools/src/copyright.h"
#ifndef FIXEDFANN
#include "fann/src/include/fann.h"
#else
#include "fann/src/include/fixedfann.h"
#undef FANNPRINTF
#define FANNPRINTF "%08x"
#endif

static char * usage_message =
    "Usage: fann-eval [OPTION]... -n[CONFIG] IMAGE...\n"
    "Classify an input IMAGE using a specific FANN network CONFIG.\n"
    "\n"
    "Mandatory options to long options are mandatory for short options, too.\n"
    "  -n, --nn-config [CONFIG]   read FANN floating point network from FILE\n"
    "  --verbose                  print information while running\n"
    "\n";

void usage () {
  printf("%s", usage_message);
}

void console_print(png_bytep * img, int width, int height) {
  fprintf(stderr, "[info] +");
  for (int i = 0; i < width; i++)
    fprintf(stderr, "-");
  fprintf(stderr, "+\n");
  for (int i = 0; i < height; i++) {
    fprintf(stderr, "[info] |");
    for (int j = 0; j < width; j++) {
      int x = (int)((float)img[i][j] / 255 * 10 - 1);
      if (x <= 0) fprintf(stderr, " ");
      else        fprintf(stderr, "%d", x);
    }
    fprintf(stderr, "|\n");
  }
  fprintf(stderr, "[info] +");
  for (int i = 0; i < width; i++)
    fprintf(stderr, "-");
  fprintf(stderr, "+\n");
}

int main (int argc, char * argv[]) {
  PRINT_NOTICES(COPYRIGHT_FANN);
  int exit_code = 0;

  struct fann * ann = NULL;
  FILE * fp = NULL;
  png_bytep header = NULL;
  png_structp png = NULL;
  png_infop info = NULL;
  png_bytepp img = NULL;
  fann_type * input = NULL;
  fann_type * logits = NULL;

  int c;
  static int opt_verbose = 0;
  while (1) {
    static struct option long_options[] = {
      {"--help",               no_argument,       0, 'h'},
      {"nn-config",            required_argument, 0, 'n'},
      {"verbose",              no_argument,       &opt_verbose, 1},
      {0, 0, 0, 0}
    };
    int option_index = 0;
    c = getopt_long (argc, argv, "hn:",
                     long_options, &option_index);
    if (c == -1)
      break;
    switch (c) {
      case 'h': usage(); goto bail; break;
      case 'n': ann = fann_create_from_file(optarg); break;
    }
  }

  if (ann == NULL || optind == argc) {
    fprintf(stderr, "[error] Missing required input argument\n\n");
    usage();
    exit_code = 1;
    goto bail;
  }

  for (int image_index = optind; image_index < argc; image_index++) {
    char * image_name = argv[image_index];
    if (opt_verbose) { fprintf(stderr, "[info] processing %s\n", image_name); }
    fp = fopen(image_name, "rb");

    // Check that the file exists
    if (!fp) {
      fprintf(stderr, "[error] Unable to find file %s\n", image_name);
      exit_code = 2;
      goto bail;
    }

    // Check that the file is a PNG
    header = (png_bytep) malloc(sizeof(png_byte) * 8);
    fread(header, 1, 8, fp);
    if (png_sig_cmp(header, 0, 8)) {
      fprintf(stderr, "[error] File %s is not a PNG\n", image_name);
      exit_code = 3;
      goto bail;
    }
    free(header); header = NULL;
    fseek(fp, 0, SEEK_SET);

    png = png_create_read_struct(PNG_LIBPNG_VER_STRING, NULL, NULL, NULL);
    if (!png) {
      fprintf(stderr, "[error] Unable to create png struct\n");
      exit_code = 4;
      goto bail;
    }

    info = png_create_info_struct(png);
    if (!info) {
      fprintf(stderr, "[error] Unable to create png info struct\n");
      exit_code = 5;
      goto bail;
    }

    if(setjmp(png_jmpbuf(png))) abort();

    png_init_io(png, fp);
    png_read_info(png, info);

    png_uint_32 width = png_get_image_width(png, info);
    png_uint_32 height = png_get_image_height(png, info);
    png_byte color_type = png_get_color_type(png, info);
    png_byte bit_depth  = png_get_bit_depth(png, info);

    if (opt_verbose)
      fprintf(stderr, "[info] image info: \n"
             "[info]   - size: [%lu, %lu]\n"
             "[info]     color: %d\n"
             "[info]     depth: %d\n", width, height, color_type, bit_depth);

    img = (png_bytepp) malloc(sizeof(png_bytep) * height);
    for (int i = 0; i < height; i++)
      img[i] = (png_bytep) malloc(sizeof(png_byte) * width);

    png_read_image(png, img);

    if (opt_verbose)
      console_print(img, width, height);

    input = (fann_type *) malloc(sizeof(fann_type) * width * height);
    for (int i = 0; i < height; i++) {
      for (int j = 0; j < width; j++) {
        input[i * height + j] = (fann_type) img[i][j] / 255;
      }
    }

    unsigned int num_output = fann_get_num_output(ann);
    logits = fann_run(ann, input);

    fann_type max = -8192;
    int max_logit = -1;
    for (int i = 0; i < num_output; i++) {
      if (logits[i] > max) {
        max_logit = i;
        max = logits[i];
      }
      if (opt_verbose)
        fprintf(stderr, "[info] %d -> " FANNPRINTF "\n", i, logits[i]);
    }
    printf("%d\n", max_logit);

    // Clean things up for the next iteration
    for (int i = 0; i < height; i++)
      free(img[i]);
    free(img); img = NULL;
    free(input); input = NULL;
    png_destroy_read_struct(&png, &info, NULL);
    fclose(fp); fp = NULL;
  }

bail:
  if (ann)
    fann_destroy(ann);
  if (fp)
    fclose(fp);
  if (header)
    free(header);
  if (input)
    free(input);
  if (png && info)
    png_destroy_read_struct(&png, &info, NULL);

  return exit_code;
}
