// See LICENSE.IBM for license details.

#include "tests/libs/src/include/xfiles-user-pk.h"

xlen_t pk_syscall_set_asid(asid_type asid) {
  // This currently depends on a backing OS system call supported by
  // the Proxy Kernel (a basic RISC-V OS). Using the RISC-V function
  // calling convention, the asid is placed into register a0, the
  // syscall ID (#512) in register a7, and we generate a syscall. The
  // Proxy Kernel will then generate a special custom0 instruction
  // that sets the ASID. No output is expected, so we just return
  // whenever the OS returns control.
  xlen_t old_asid;
  asm volatile ("mv a0, %[asid]\n\t"
                "li a7, %[syscall]\n\t"
                "ecall\n\t"
                "mv %[old_asid], a0"
                : [old_asid] "=r" (old_asid)
                : [asid] "r" (asid), [syscall] "i" (SYSCALL_SET_ASID)
                : "a0", "a7");
  return old_asid;
}

xlen_t pk_syscall_set_antp(ant * os_antp) {
  // As with `set_asid`, this relies on the Proxy Kernel to handle
  // this system call. This passes a pointer to the first ASID--NNID
  // table entry and the size (i.e., the number of ASIDs).
  xlen_t old_antp;
  asm volatile ("mv a0, %[antp]\n\t"
                "mv a1, %[size]\n\t"
                "li a7, %[syscall]\n\t"
                "ecall\n\t"
                "mv %[old_antp], a0"
                : [old_antp] "=r" (old_antp)
                : [antp] "r" (os_antp->entry_p), [size] "r" (os_antp->size),
                  [syscall] "i" (SYSCALL_SET_ANTP)
                : "a0", "a7");
  return old_antp;
}

xlen_t pk_syscall_debug_echo(uint32_t data) {
  xlen_t out;
  asm volatile ("mv a0, %[data]\n\t"
                "li a7, %[syscall]\n\t"
                "ecall\n\t"
                "mv %[out], a0"
                : [out] "=r" (out)
                : [data] "r" (data), [syscall] "i" (SYSCALL_DEBUG_ECHO)
                : "a0", "a7");
  return out;
}
