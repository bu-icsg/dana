// See LICENSE for license details.

#ifndef SRC_MAIN_C_XFILES_H_
#define SRC_MAIN_C_XFILES_H_

#include <stdint.h>
#include <stddef.h>
#include "src/main/c/xcustom.h"
#include "src/main/c/xfiles.S"

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

typedef enum {
  int_INVREQ      = 0,
  int_DANA_NOANTP = 1,
  int_INVASID     = 2,
  int_INVNNID     = 3,
  int_NULLREAD    = 4,
  int_ZEROSIZE    = 5,
  int_INVEPB      = 6,
  int_MISALIGNED  = 7,
  int_UNKNOWN     = -1
} xfiles_interrupt_t;

#define OPCODE 0
// Standard macro that passes rd_, rs1_, and rs2_ via registers
#define XFILES_INSTRUCTION(rd, rs1, rs2, funct) \
  XFILES_INSTRUCTION_R_R_R(rd, rs1, rs2, funct)
#define XFILES_INSTRUCTION_R_R_R(rd, rs1, rs2, funct)   \
  XCUSTOM_R_R_R(OPCODE, rd, rs1, rs2, funct)

// Macro to pass rs2_ as an immediate
#define XFILES_INSTRUCTION_R_R_I(rd, rs1, rs2, funct)   \
  XCUSTOM_R_R_R(OPCODE, rd, rs1, rs2, funct)

// Macro to pass rs1_ and rs2_ as immediates
#define XFILES_INSTRUCTION_R_I_I(rd, rs1, rs2, funct)   \
  XCUSTOM_R_R_R(OPCODE, rd, rs1, rs2, funct)

#endif  // SRC_MAIN_C_XFILES_H_
