#include "xfiles.h"

int main (int argv, char * argc[]) {
  printf("%s: START\n", argc[0]);

  tid_type tid = new_write_request(0, 0, 0);
  printf("TID on write with invalid ASID: %d\n", tid);

  printf("%s: END\n", argc[0]);
  return 0;
}
