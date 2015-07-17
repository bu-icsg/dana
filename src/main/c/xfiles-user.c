#include "xfiles.h"

tid_type new_write_request(nnid_type nnid) {
  uint64_t out;

  asm volatile ("custom0 %[out], %[rs1], %[rs2], 3"
                : [out] "=r" (out)
                : [rs1] "r" (0), [rs2] "r" (nnid));
  return (out >> 32) & ~((~0) << 16);
}

void write_data(tid_type tid, element_type * data, size_t count) {
  int i;

  for (i = 0; i < count - 1; i++)
    asm volatile ("custom0 0, %[rs1], %[rs2], 1"
                  :: [rs1] "r" (tid), [rs2] "r" (data[i]));

  asm volatile ("custom0 0, %[rs1], %[rs2], 5"
                :: [rs1] "r" (tid), [rs2] "r" (data[i]));
}

uint64_t spin_read_data(tid_type tid, element_type * data, size_t count) {
  int i;
  uint64_t out;

  while (1) {
    asm volatile ("custom0 %[out], %[rs1], 0, 0"
                  : [out] "=r" (out)
                  : [rs1] "r" (tid));
    switch (out >> (32 + 16 + 14)) {
    case 2:  continue;
    case 1:  goto success;
    default: return -1;
    }
  }

 success:
  data[0] = out;

  for (i = 1; i < count; i++)
    asm volatile ("custom0 %[out], %[rs1], 0, 0"
                  : [out] "=r" (data[i])
                  : [rs1] "r" (tid));
}
