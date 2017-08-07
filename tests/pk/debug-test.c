// See LICENSE.BU for license details.

#include <stdio.h>
#include <unistd.h>
#include <assert.h>

#include "tests/libs/src/include/xfiles-debug.h"

int main(int argc, char **argv) {

  uint64_t data[4];
  data[0] = 0xaaaa;
  data[1] = 0xbbbb;
  data[2] = 0xcccc;
  data[3] = 0xdddd;

  xlen_t out;
  printf("[test] Testing register interface (action 0x%x)...\n", a_REG);
  printf("[test]   - sent: 0x%lx\n", data[0]);
  out = debug_echo_via_reg(data[0]);
  printf("[test]     received: 0x%lx\n", out);
  assert(out == data[0]);

  printf("[test] Testing L1 read (action 0x%x)...\n", a_MEM_READ);
  for (size_t i = 0; i < 4; ++i) {
    printf("[test]   - virtual address: 0x%p\n", &data[i]);
    xlen_t * data_p = debug_virt_to_phys(&data[i]);
    printf("[test]     physical address: 0x%p\n", data_p);
    out = debug_read_mem(data_p);
    printf("[test]     data: 0x%lx\n", out);
  }

  printf("[test] Testing L1 write (action 0x%x)...\n", a_MEM_WRITE);
  xlen_t copy_l1[4];
  for (size_t i = 0; i < 4; ++i) {
    xlen_t * copy_p = (xlen_t *) debug_virt_to_phys(&copy_l1[i]);
    printf("[test]   Write %p\n", copy_p);
    out = debug_write_mem(data[i], copy_p);
    assert(out == 0);
    assert(data[i] == copy_l1[i]);
  }

  printf("[test] Testing translation (action 0x%x)...\n", a_VIRT_TO_PHYS);
  out = (xlen_t) debug_virt_to_phys(&data);
  assert(out != -1);

  printf("[test] Testing L2 read (action 0x%x)...\n", a_UTL_READ);
  for (size_t i = 0; i < 4; ++i) {
    xlen_t * data_p = (xlen_t *) debug_virt_to_phys(&data[i]);
    printf("[test]   Read PHYS %p (VIRT: %p)\n", data_p, &data[i]);
    out = debug_read_utl(data_p);
    printf("[test]     Got 0x%lx\n", out);
    assert(out == data[i]);
  }

  printf("[test] Testing L2 write (action 0x%x)...\n", a_UTL_WRITE);
  xlen_t copy_l2[4];
  for (size_t i = 0; i < 4; ++i) {
    xlen_t * copy_p = (xlen_t *) debug_virt_to_phys(&copy_l2[i]);
    printf("[test]   Write 0x%lx to PHYS %p (VIRT: %p)\n",
           data[i], copy_p, &copy_l2[i]);
    out = debug_write_utl(data[i], copy_p);
    printf("[test]     Got 0x%lx\n", copy_l2[i]);
    assert(out == 0);
    assert(copy_l2[i] == data[i]);
  }
}
