// See LICENSE.BU for license details.

#include "src/include/xfiles-user-pk.h"

int main() {
  asid_type asid = 2;
  tid_type tid = 0;
  pk_syscall_set_asid(1);
  set_asid(&asid, &tid);
  while(1) {};
}
