// See LICENSE for license details.

#ifndef SRC_MAIN_C_XFILES_USER_PK_H_
#define SRC_MAIN_C_XFILES_USER_PK_H_

#include "src/main/c/xfiles.h"
#include "src/main/c/xfiles-supervisor.h"
#include "src/main/c/xfiles-user.h"

// Set the ASID to a new value
xlen_t pk_syscall_set_asid(asid_type asid);

// Set the ASID--NNID Table Poitner (ANTP)
xlen_t pk_syscall_set_antp(ant * os_antp);

// Do a debug echo using a systemcall
xlen_t pk_syscall_debug_echo(uint32_t data);

#endif  // SRC_MAIN_C_XFILES_USER_PK_H_
