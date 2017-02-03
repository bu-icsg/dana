// See LICENSE for license details.

#include <getopt.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <assert.h>
#include "src/main/c/xfiles-asid-nnid-table.h"

void usage(char * argv) {
  printf("Usage: %s -a [asid],[nn-config] [OPTIONS] [output_file]\n"
         "Generate a standalone ASID--NNID Table with specific binary neural network\n"
         "configurations attached to specific ASIDs.\n"
         "\n"
         "Options:\n"
         "  -a, --attach [asid],[nn_config_file]\n"
         "                             attach binary (e.g., *.16bin) [nn_config_file]\n"
         "                             to [asid]\n"
         "  -h, -?, --help             display this help and exit\n"
         "  --verbose                  print debugging information\n"
         "\n"
         "The [output_file] is optional. If unspecified, this will print to stdout.\n"
         "\n"
         "Example:\n"
         "  %s -a 2,xorSigmoidSymmetric-fixed.16bin myconfig.bin\n",
         argv, argv);
}

typedef struct {
  int asid;
  char file[1024];
} t_asid_file;

int parse_asid_file(char * t, t_asid_file * af) {
  char string_asid [10];

  int idx = 0, on_asid = 1, i;
  for (i = 0; i < strlen(t); i++) {
    if (t[i] == ',') {
      idx = 0;
      on_asid = 0;
      string_asid[idx-1] = '\0';
      af->asid = atoi(string_asid);
      if (af->asid < 0)
        return 2;
      continue;
    }
    if (on_asid) {
      string_asid[idx++] = t[i];
      assert(idx < 10);
    }
    else {
      af->file[idx++] = t[i];
      assert(idx < 1024);
    }
  }
  af->file[idx] = '\0';

  if (on_asid)
    return 1;
  return 0;
}

int pad_dump(FILE * file, int amount) {
  char zero = 0;
  int written = 0;
  for (int i = 0; i < amount; i++)
    written += fwrite(&zero, 1, 1, file);
  return written;
}

typedef struct {
  char ** x;
  size_t size;
  int used;
  int total;
} t_array;
int array_init(t_array * a, size_t size, int count) {
  (*a).x = (char **) malloc(count * sizeof(char *));
  (*a).used = 0;
  (*a).total = count;
  (*a).size = size;
  return 0;
}
void array_destroy(t_array * a) {
  free((*a).x);
}
int array_push(t_array * a, void * new_x, int verbose) {
  if ((*a).used == (*a).total) {
    if (verbose) fprintf(stderr, "[INFO] Realloc'ing\n");
    (*a).x = (char **) realloc((*a).x, (*a).total * 2 * sizeof(char *));
  }
  (*a).x[(*a).used++] = new_x;
  if (verbose) fprintf(stderr, "[INFO] Pushing 0x%p onto array\n", new_x);
  return 0;
}

int ant_dump(ant * table, FILE * file, int verbose) {
  int exit_code = 0;
  char * offset = (char *) table;
  char * last = offset;
  if (verbose) fprintf(stderr, "[INFO] Writing Header:\n");
  if (verbose) fprintf(stderr, "[INFO]   0x%lx: header (0x%lx B)\n", last - offset,
                      sizeof(ant));
  last += sizeof(ant) * fwrite(table, sizeof(ant), 1, file);

  // Flatten the ASID--NNID table into ASID and NNID arrays
  t_array asids;
  t_array nnids;
  array_init(&asids, sizeof(ant_entry), 1);
  array_init(&nnids, sizeof(nn_config), 1);
  for (ant_entry * e = table->entry_v; e < &table->entry_v[table->size]; e++) {
    array_push(&asids, e, verbose);
    for (nn_config * n = e->asid_nnid_v; n < &e->asid_nnid_v[e->num_valid]; n++) {
      array_push(&nnids, n, verbose);
    }
  }

  // Loop over the ASID array and write the binary output
  long int padding;
  if (verbose) fprintf(stderr, "[INFO] Writing ASIDs:\n");
  for (ant_entry * a = (ant_entry *) (*asids.x);
       a < (ant_entry *) (*asids.x) + asids.used; a++) {
    padding = (char *) a - last;
    if (padding < 0) {
      fprintf(stderr, "[ERROR] ASID would require negative padding of 0d%ld\n",
              padding);
      exit_code = 1;
      goto bail;
    }
    if (verbose) fprintf(stderr, "[INFO]   0x%lx: Padding (0x%ld B)\n",
                        last - offset, padding);
    last += pad_dump(file, padding);
    if (verbose) fprintf(stderr, "[INFO]   0x%lx: VAddr 0x%p (0x%lx B)\n",
                        last - offset, a, sizeof(ant_entry));
    last += sizeof(ant_entry) * fwrite(a, sizeof(ant_entry), 1, file);
  }

  // Loop over the NNID array twice, writing NNID info followed by the configs
  if (verbose) fprintf(stderr, "[INFO] Writing NNIDs:\n");
  for (char ** x = nnids.x; x < nnids.x + nnids.used; x++) {
    nn_config * n = (nn_config *) (*x);
    padding = (char *) n - last;
    if (padding < 0) {
      fprintf(stderr, "[ERROR] NNID would require negative padding of 0d%ld\n",
              padding);
      exit_code = 1;
      goto bail;
    }
    if (verbose) fprintf(stderr, "[INFO]   0x%lx: Padding (0x%ld B)\n",
                        last - offset, padding);
    last += pad_dump(file, padding);
    if (verbose) fprintf(stderr, "[INFO]   0x%lx: NNID VAddr 0x%p (0x%lx B)\n",
                        last - offset, n, sizeof(nn_config));
    last += sizeof(nn_config) * fwrite(n, sizeof(nn_config), 1, file);
  }
  if (verbose) fprintf(stderr, "[INFO] Writing NN Configs:\n");
  for (char ** x = nnids.x; x < nnids.x + nnids.used; x++) {
    nn_config * n = (nn_config *) (*x);
    padding = (char *) n->config_v - last;
    if (padding < 0) {
      fprintf(stderr, "[ERROR] NN Config would require negative padding of 0d%ld\n",
              padding);
      exit_code = 1;
      goto bail;
    }
    if (verbose) fprintf(stderr, "[INFO]   0x%lx: Padding (0x%ld B)\n",
                        last - offset, padding);
    last += pad_dump(file, padding);
    if (verbose) fprintf(stderr, "[INFO]   0x%lx: Config (VAddr 0x%p, 0x%lx B)\n",
                        last - offset, n->config_v, n->size * sizeof(xlen_t));
    last += n->size * sizeof(xlen_t) *
            fwrite(n->config_v, n->size * sizeof(xlen_t), 1, file);
  }

bail:
  if (asids.x != NULL) array_destroy(&asids);
  if (nnids.x != NULL) array_destroy(&nnids);
  return exit_code;
}

void make_relative(ant * table) {
  void * offset = table;
  table->entry_p = (void *) ((size_t) table->entry_p - (size_t) offset);
  for (ant_entry * e = table->entry_v; e < &table->entry_v[table->size]; e++) {
    e->asid_nnid_p = (void *) ((size_t) e->asid_nnid_p - (size_t) offset);
    for (nn_config * n = e->asid_nnid_v; n < &e->asid_nnid_v[e->num_valid]; n++)
      n->config_p = (void *) ((size_t) n->config_p - (size_t) offset);
  }
}

int main(int argc, char ** argv) {
  int exit_code = 0;

  // Track the ASID->file mappings
  struct {
    t_asid_file * af;
    int i;
    int n;
    asid_type max_asid;
  } s_af;
  s_af.af = (t_asid_file*) malloc(4 * sizeof(t_asid_file));
  s_af.i = 0;
  s_af.n = 4;
  s_af.max_asid = 0;

  FILE * file = stdout;
  // Parse command line options
  int c;
  static int opt_verbose;
  while (1) {
    static struct option long_test[] = {
      {"attach",               required_argument, 0,           'a'},
      {"help",                 no_argument,       0,           'h'},
      {"verbose",              no_argument,       &opt_verbose, 1},
      {0, 0, 0, 0}
    };
    int option_index = 0;
    c = getopt_long(argc, argv, "a:h?", long_test, &option_index);
    if (c == -1)
      break;
    switch (c) {
      // Add to s_af, realloc'ing if needed
      case 'a': {
        if (s_af.i == s_af.n - 1) {
          s_af.af = (t_asid_file*) realloc(s_af.af, s_af.n * 2);
          s_af.n = s_af.n * 2;
        }
        if (parse_asid_file(optarg, &s_af.af[s_af.i++])) {
          fprintf(stderr, "[ERROR] Bad asid,file pair: %s\n", optarg);
          exit_code = 1;
          goto bail;
        }
        if (s_af.af[s_af.i-1].asid > s_af.max_asid)
          s_af.max_asid = s_af.af[s_af.i-1].asid;
        break;
      }
      case '?':
      case 'h': usage(argv[0]); return 0;
    }
  };
  // Ensure that at least one asid,network tuple was given.
  if (s_af.i == 0) {
    fprintf(stderr, "[ERROR] Specify at least one `asid,network` tuple\n");
    usage(argv[0]);
    exit_code = 1;
    goto bail;
  }
  if (optind == argc - 1) {
    if ((file = fopen(argv[optind], "w")) == 0) {
      fprintf(stderr, "[ERROR] Unable to open dump file\n");
      exit_code = 3;
      goto bail;
    }
  }

  // Create and populate the ASID-NNID Table
  ant * table;
  asid_nnid_table_create(&table, s_af.max_asid + 1, 10); // [TODO] Magic number
  for (t_asid_file * x = s_af.af; x < s_af.af + s_af.i; x++) {
    if (attach_nn_configuration(&table, x->asid, x->file) == -1) {
      fprintf(stderr, "[ERROR] Failed to attach to ASID %d file %s\n",
              x->asid, x->file);
      exit_code = 2;
      goto bail;
    }
    if (opt_verbose) fprintf(stderr, "[INFO] asid: %d\n"
                             "[INFO] file: %s\n", x->asid, x->file);
  }
  if (opt_verbose) fprintf(stderr, "[INFO] max asid: %d\n", s_af.max_asid);
  make_relative(table);
  if (opt_verbose) asid_nnid_table_info(table);

  // Dump the raw bits
  ant_dump(table, file, opt_verbose);

bail:
  if (s_af.af != NULL) free(s_af.af);
  if (file != stdout) fclose(file);
  return exit_code;
}
