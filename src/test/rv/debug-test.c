#include <stdio.h>
#include <unistd.h>
#include <assert.h>

#include "src/main/c/xfiles-user.h"


int main(int argc, char **argv) {

  uint64_t data[8];
  xlen_t copy = 0;
  data[0] = 0xaaaa;
  data[1] = 0xbbbb;
  data[2] = 0xcccc;
  data[3] = 0xdddd;

  xlen_t out;
  printf("[TEST] Testing register interface (action 0x%x)...\n", a_REG);
  out = debug_echo_via_reg(data[0]);
  assert(out == data[0]);

  printf("[TEST] Testing L1 read (action 0x%x)...\n", a_MEM_READ);
  for (size_t i = 0; i < 4; ++i) {
    out = debug_read_mem(&(data[i]));
    assert(data[i] == out);
  }

  printf("[TEST] Testing L1 write (action 0x%x)...\n", a_MEM_WRITE);
  out = debug_write_mem(data[0], &copy);
  assert(out == 0);
  assert(data[0] == copy);

  printf("[TEST] Testing translation (action 0x%x)...\n", a_VIRT_TO_PHYS);
  out = debug_virt_to_phys(&data);
  assert(out == (uint64_t) &data);

  printf("[TEST] Testing L2 read (action 0x%x)...\n", a_UTL_READ);
  for (size_t i = 0; i < 4; ++i) {
    out = debug_read_utl(&(data[i]));
    assert(out == data[i]);
  }
}
