#include <stdio.h>
#include <assert.h>

#include "src/main/c/xfiles-user.h"

int main(int argc, char **argv) {
  printf("[TEST] Testing pk debug echo...\n");
  xlen_t out = pk_syscall_debug_echo(0xdead);
  printf("[TEST]   out: 0x%lx\n", out);
  assert(out == 0xdead);
}
