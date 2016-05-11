#include <stdio.h>
#include <unistd.h>
#include <assert.h>

#include "src/main/c/xfiles-user.h"

int main(int argc, char **argv) {

  xlen_t data = 0xdead, copy = 0;

  xlen_t out;
  printf("[TEST] Testing register interface (action 0x%x)...\n", a_REG);
  out = debug_test(a_REG, data, 0);
  assert(out == data);

  printf("[TEST] Testing L1 read (action 0x%x)...\n", a_MEM_READ);
  out = debug_test(a_MEM_READ, 0, &data);
  assert(data == out);

  printf("[TEST] Testing L1 write (action 0x%x)...\n", a_MEM_WRITE);
  out = debug_test(a_MEM_WRITE, data, &copy);
  assert(out == 0);
  assert(data == copy);
}
