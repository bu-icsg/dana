#include "src/main/c/xfiles-user-pk.h"
#include "src/main/c/xfiles-asid-nnid-table.h"

int main() {
  pk_syscall_set_asid(1);

  ant * ant;
  asid_nnid_table_create(&ant, 2, 4);
  attach_nn_configuration(&ant, 1, "../xfiles-dana/build/nets/xorSigmoidSymmetric-fixed.128bin");
  pk_syscall_set_antp(ant);

  tid_type tid = new_write_request(0, 0, 0);
  element_type junk = 0;
  write_data(tid, &junk, 1);
  while (1) {};
}
