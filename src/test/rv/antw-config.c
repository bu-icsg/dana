#include <stdio.h>

#include "src/include/xfiles-supervisor.h"

int main() {
  ant_entry asidEntry;
  nn_config nnidEntry;

  printf("(sizeAntStruct,%ld)\n"
         "(sizeAsidStruct,%ld)\n"
         "(sizeNnidStruct,%ld)\n"
         "(sizeIoStruct,%ld)\n"
         "(sizeQueueStruct,%ld)\n"
         "(offsetNnidPtr,%ld)\n"
         "(offsetEpb,%ld)\n"
         "(offsetConfig,%ld)\n",
         sizeof(ant),
         sizeof(ant_entry),
         sizeof(nn_config),
         sizeof(io),
         sizeof(queue),
         (uint64_t) &asidEntry.asid_nnid_p - (uint64_t) &asidEntry,
         (uint64_t) &nnidEntry.elements_per_block - (uint64_t) &nnidEntry,
         (uint64_t) &nnidEntry.config_p - (uint64_t) &nnidEntry);
         // sizeof());

  return 0;
}
