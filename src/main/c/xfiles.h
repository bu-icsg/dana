// See LICENSE for license details.

#ifndef __XFILES_H__
#define __XFILES_H__

#include <stdint.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>

// [TODO] Any changes to these types need to occur in conjunction with
// the Chisel code and with the TID extraction part of
// new_write_request.
typedef int32_t nnid_type;
typedef int16_t tid_type;
typedef int32_t element_type;
typedef uint64_t xlen_t;

typedef enum {
  xfiles_reg_batch_items = 0,
  xfiles_reg_learning_rate,
  xfiles_reg_weight_decay_lambda
} xfiles_reg;

typedef enum {
  READ_DATA = 0,       // 0 0000
  WRITE_DATA = 1,      // 0 0001
  NEW_REQUEST = 3,     // 0 0011
  WRITE_DATA_LAST = 5, // 0 0101
  WRITE_REGISTER = 7,  // 0 0111
  XFILES_DEBUG = 8,    // 0 1000
  XFILES_DANA_ID = 16  // 1 0000
} request_t;

typedef enum {
  FEEDFORWARD = 0,
  TRAIN_INCREMENTAL = 1,
  TRAIN_BATCH = 2
} learning_type_t;

typedef enum {
  err_XFILES_UNKNOWN = 0,
  err_XFILES_NOASID,
  err_XFILES_TTABLEFULL,
  err_XFILES_INVALIDTID
} xfiles_err_t;

typedef enum {
  resp_OK = 0,
  resp_TID,
  resp_READ,
  resp_NOT_DONE,
  resp_QUEUE_ERR,
  resp_XFILES
} xfiles_resp_t;

typedef enum {
  err_UNKNOWN     = 0,
  err_DANA_NOANTP = 1,
  err_INVASID     = 2,
  err_INVNNID     = 3,
  err_ZEROSIZE    = 4,
  err_INVEPB      = 5
} dana_err_t;

#define RESP_CODE_WIDTH 3

#define XCUSTOM "custom0"
#define XFILES_INSTRUCTION(rd_, rs1_, rs2_, funct_)       \
  asm volatile (XCUSTOM" %[rd], %[rs1], %[rs2], %[funct]" \
                : [rd] "=r" (rd_) \
                : [rs1] "r" (rs1_), \
                  [rs2] "r" (rs2_), \
                  [funct] "i" (funct_))

#endif
