// See LICENSE for license details.

#ifndef XFILES_DANA_LIBS_SRC_XFILES_SUPERVISOR_H_
#define XFILES_DANA_LIBS_SRC_XFILES_SUPERVISOR_H_

#include "src/include/xfiles.h"
#include "src/include/xfiles-supervisor-types.h"
#include "src/xfiles-supervisor.S"

// Set the ASID to a new value
xlen_t set_asid(asid_type asid);

// Set the ASID--NNID Table Poitner (ANTP)
xlen_t set_antp(ant_entry * antp, size_t size);

// Read a csr from XFiles
xlen_t xf_read_csr(xfiles_csr_t csr);

#endif  // XFILES_DANA_LIBS_SRC_XFILES_SUPERVISOR_H_
