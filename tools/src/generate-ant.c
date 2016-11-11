// See LICENSE for license details.

#include <getopt.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <assert.h>
#include "src/main/c/xfiles-asid-nnid-table.h"

void usage() {
  printf("Usage: generate-ant [OPTIONS]"
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
  while (1) {
    static struct option long_test[] = {
      {"attach",               required_argument, 0, 'a'},
      {"help",                 no_argument,       0, 'h'},
      {0, 0, 0, 0}
    };
    int option_index = 0;
    c = getopt_long(argc, argv, "a:h", long_test, &option_index);
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
          printf("[ERROR] Bad asid,file pair: %s\n", optarg);
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

  for (int i = 0; i < s_af.i; i++) {
    printf("[INFO] asid: %d\n", s_af.af[i].asid);
    printf("[INFO] file: %s\n", s_af.af[i].file);
  }

  if (s_af.i > 0) printf("[INFO] max asid: %d\n", s_af.max_asid);

  ant * table;
  asid_nnid_table_create(&table, s_af.max_asid, 10); // [TODO] Magic number

bail:
  free(s_af.af);
  return exit_code;
}
