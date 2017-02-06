#include "src/include/xfiles-user-pk.h"

int main() {
  pk_syscall_set_asid(1);
  set_asid(2);
  while(1) {};
}
