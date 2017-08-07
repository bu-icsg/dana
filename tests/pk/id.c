// See LICENSE.BU for license details.

#include "tests/libs/src/include/xfiles-user.h"

int main (int argc, char ** argv) {
  xlen_t id = xfiles_dana_id(1);
  printf("[info] got id: 0x%lx\n", id);
  return 0;
}
