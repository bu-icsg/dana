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
#define CSRs_fence        0x080 // Dana

// Supervisor CSRs read/write
#define CSRs_cause        0x100 // X-Files
#define CSRs_ttable_size  0x101
#define CSRs_asid         0x102
#define CSRs_tid          0x103

#define CSRs_pe_size      0x180 // Dana
#define CSRs_cache_size   0x181
#define CSRs_pe_cooldown  0x182
#define CSRs_antp         0x183
#define CSRs_num_asids    0x184
#define CSRs_pe_governor  0x185

// Supervisor CSRS read-only
#define CSRs_xfid         0xD00 // X-Files
#define CSRs_xfid_current 0xD01

#define RESP_CODE_WIDTH 3

#endif  // XFILES_DANA_LIBS_SRC_XFILES_S_
