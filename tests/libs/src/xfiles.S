// See LICENSE.IBM for license details.

#ifndef XFILES_DANA_LIBS_SRC_XFILES_S_
#define XFILES_DANA_LIBS_SRC_XFILES_S_

#define t_USR_READ_DATA 4
#define t_USR_WRITE_DATA 5
#define t_USR_NEW_REQUEST 6
#define t_USR_WRITE_DATA_LAST 7
#define t_USR_WRITE_REGISTER 8
#define t_USR_XFILES_DEBUG 9

#define csr_EXCEPTION 0x0
#define csr_TTABLE_SIZE 0x1
#define csr_XFID 0x2
#define csr_XFID_CURRENT 0x3
#define csr_ASID 0x4
#define csr_TID 0x5
#define csr_DEBUG 0x6

#define csr_PE_SIZE 0x10
#define csr_CACHE_SIZE 0x11
#define csr_PE_COOLDOWN 0x12
#define csr_ANTP 0x13
#define csr_NUM_ASIDS 0x14
#define csr_PE_GOVERNOR 0x15

#define RESP_CODE_WIDTH 3

#endif  // XFILES_DANA_LIBS_SRC_XFILES_S_
