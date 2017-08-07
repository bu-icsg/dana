// See LICENSE.BU for license details.

#include "tests/libs/src/include/xfiles-user-pk.h"

int main() {
  pk_syscall_set_asid(1);
  tid_type tid = new_write_request(0, 0, 0);
  element_type junk = 0;
  write_data(tid, &junk, 1);
  while(1) {};
}
