#include "src/main/c/xfiles-user.h"
#include "src/main/c/xfiles-supervisor.h"

int main() {
  pk_syscall_set_asid(1);
  set_asid(2);
  while(1) {};
}
