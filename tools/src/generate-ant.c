// See LICENSE for license details.

#include <getopt.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <assert.h>
#include "src/main/c/xfiles-asid-nnid-table.h"

void usage() {
  printf("Usage: generate-ant -a [asid],[nn-config] [OPTIONS]"
         "\n"
         "Options:\n"
         ""
         );
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

int ant_dump(ant * table, FILE * file, int verbose) {
  void * offset = table->entry_v;
  void * last = offset;
  if (verbose) printf("[INFO] Offset: %p\n", offset);
  for (ant_entry * e = table->entry_v; e < &table->entry_v[table->size]; e++) {
    fwrite(e, sizeof(ant_entry), 1, file);
    last += sizeof(ant_entry);
    for (nn_config * n = e->asid_nnid_v; n < &e->asid_nnid_v[e->num_valid]; n++) {
      long int padding = (long int) n - (long int) last;
      char zero = 0;
      for (int i = 0; i < padding; i++)
        fwrite(&zero, 1, 1, file);
      fwrite(n, sizeof(nn_config), 1, file);
      last += padding + sizeof(nn_config);
      // [TODO] Write the contents of the NN configuration
    }
  }
  return 0;
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
    c = getopt_long(argc, argv, "a:hv", long_test, &option_index);
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
      case 'h': usage(); return 0;
    }
  };
  // Ensure that at least one asid,network tuple was given.
  if (s_af.i == 0) {
    fprintf(stderr, "[ERROR] Specify at least one `asid,network` tuple\n");
    usage();
    exit_code = 1;
    goto bail;
  }

  // Create and populate the ASID-NNID Table
  ant * table;
  asid_nnid_table_create(&table, s_af.max_asid + 1, 10); // [TODO] Magic number
  for (t_asid_file * x = s_af.af; x < s_af.af + s_af.i; x++) {
    if (attach_nn_configuration(&table, x->asid, x->file) == -1) {
      fprintf(stderr, "[ERROR] Failed to attached to ASID %d file %s\n",
              x->asid, x->file);
      exit_code = 2;
      goto bail;
    }
    if (opt_verbose) printf("[INFO] asid: %d\n"
                            "[INFO] file: %s\n", x->asid, x->file);
  }
  if (opt_verbose) printf("[INFO] max asid: %d\n", s_af.max_asid);
  if (opt_verbose) asid_nnid_table_info(table);

  // Dump the raw bits
  FILE * file = NULL;
  if ((file = fopen("dump.bin", "w")) == 0) {
    fprintf(stderr, "[ERROR] Unable to open dump file\n");
    exit_code = 3;
    goto bail;
  }
  ant_dump(table, file, opt_verbose);

bail:
  if (s_af.af != NULL) free(s_af.af);
  if (file != NULL) fclose(file);
  return exit_code;
}
