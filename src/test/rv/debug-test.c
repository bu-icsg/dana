#include <stdio.h>
#include <unistd.h>

#include "src/main/c/xfiles-user.h"

static char * usage_message =
    "debug-test -a[ACTION] -d[DATA]\n"
    "Access the Debug Unit of the X-FILES Arbiter\n"
    "\n"
    "  -a                         the action to perform (see below)\n"
    "  -d                         the data to send along with the action\n"

    "\n"
    "Actions:\n"
    "  * 0 -- Send DATA via register transfer\n"
    "  * 1 -- Send DATA via the L1 Data cache\n"
    "  * 2 -- Send DATA via the L2 Data cache\n";

void usage() {
  printf("Usage: %s", usage_message);
}

int main(int argc, char **argv) {

  xlen_t action, data;
  int action_set = 0, data_set = 0;
  int opt;
  while ((opt = getopt(argc, argv, "a:d:h")) != -1 ) {
    switch (opt) {
      case 'a': action = atoi(optarg); action_set = 1; break;
      case 'd': data = atoi (optarg); data_set = 1; break;
      case 'h': usage(); return 0;
    }
  }

  if (optind != argc) {
    fprintf(stderr, "[ERROR] Bad command line argument\n\n");
    usage();
    return 1;
  }

  if (!(action_set & data_set)) {
    fprintf(stderr, "[ERROR] Missing required command line argument\n\n");
    usage();
    return 1;
  }

  printf("[INFO] action: 0x%8lx\n", action);
  printf("[INFO] data:   0x%8lx\n", data);
  printf("[INFO] &data:  0x%8lx\n", (uint64_t) &data);

  xlen_t out = debug_test(action, data);
  printf("[INFO] Saw output 0x%lx\n", out);

}
