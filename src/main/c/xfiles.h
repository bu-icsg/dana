// See LICENSE for license details

#ifndef SRC_MAIN_C_XFILES_H
#define SRC_MAIN_C_XFILES_H

#include <stdint.h>
#include <stdlib.h>
#include <string.h>

// [TODO] Any changes to these types need to occur in conjunction with
// the Chisel code and with the TID extraction part of
// new_write_request.
typedef uint32_t nnid_t;
typedef uint16_t tid_t;
typedef int32_t element_t;
typedef uint64_t x_len;
typedef uint16_t asid_t;

#endif  // SRC_MAIN_C_XFILES_H
