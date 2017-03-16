// See LICENSE.IBM for license details.

#ifndef XFILES_DANA_LIBS_SRC_XFILES_S_
#define XFILES_DANA_LIBS_SRC_XFILES_S_

#define t_USR_READ_DATA 4
#define t_USR_WRITE_DATA 5
#define t_USR_NEW_REQUEST 6
#define t_USR_WRITE_DATA_LAST 7
#define t_USR_WRITE_REGISTER 8
#define t_USR_XFILES_DEBUG 9

// User CSRs
#define csr_FENCE        0x080 // Dana

// Supervisor CSRs read/write
#define csr_CAUSE        0x100 // X-Files
#define csr_TTABLE_SIZE  0x101
#define csr_ASID         0x102
#define csr_TID          0x103

#define csr_PE_SIZE      0x180 // Dana
#define csr_CACHE_SIZE   0x181
#define csr_PE_COOLDOWN  0x182
#define csr_ANTP         0x183
#define csr_NUM_ASIDS    0x184
#define csr_PE_GOVERNOR  0x185

// Supervisor CSRs read-only
#define csr_XFID         0xD00 // X-Files
#define csr_XFID_CURRENT 0xD01

#define RESP_CODE_WIDTH 3

#endif  // XFILES_DANA_LIBS_SRC_XFILES_S_
