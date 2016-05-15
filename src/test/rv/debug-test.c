#include <stdio.h>
#include <unistd.h>
#include <assert.h>

#include "src/main/c/xfiles-user.h"

int main(int argc, char **argv) {

  xlen_t data = 0xdead, copy = 0;

  xlen_t out;
  printf("[TEST] Testing register interface (action 0x%x)...\n", a_REG);
  out = debug_echo_via_reg(data);
  assert(out == data);

  printf("[TEST] Testing L1 read (action 0x%x)...\n", a_MEM_READ);
  out = debug_read_mem(&data);
  assert(data == out);

  printf("[TEST] Testing L1 write (action 0x%x)...\n", a_MEM_WRITE);
  out = debug_write_mem(data, &copy);
  assert(out == 0);
  assert(data == copy);

  printf("[TEST] Testing translation (action 0x%x)...\n", a_VIRT_TO_PHYS);
  out = debug_virt_to_phys(&data);
  printf("[INFO] &data: 0x%lx_v -> 0x%lx_p\n", (xlen_t) &data, out);
}
