// See LICENSE.BU for license details.

#include "tests/libs/src/include/xfiles-user-pk.h"
#include "tests/libs/src/include/xfiles-asid-nnid-table.h"

int main() {
  pk_syscall_set_asid(1);

  ant * ant;
  asid_nnid_table_create(&ant, 2, 4);
  attach_garbage(&ant, 1);
  attach_garbage(&ant, 1);
  attach_garbage(&ant, 1);
  attach_garbage(&ant, 1);
  pk_syscall_set_antp(ant);

  tid_type tid = new_write_request(0, 0, 0);
  element_type junk = 0;
  write_data(tid, &junk, 1);
  while (1) {};
}
