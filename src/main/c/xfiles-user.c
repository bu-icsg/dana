// See LICENSE for license details.

#include "src/main/c/xfiles-user.h"
#include "src/main/c/xfiles-debug.h"

// All RoCC communication occurs using the "custom0" RISC-V
// instruction of the following format:
//
//   custom0 <output register> <input reg 1> <input reg 2> <funct>
//
// The X-Files arbiter uses the bits of the 7-bit "funct" field to
// decode the instruction and figure out what it should do with it.
// The input registers are referred to as rs1 and rs2 in the
// rocket-chip repo and by available RoCC documentation so we stick
// with that convention below.
//
// The bits of "funct" are as follows:
//
//   |       [6:3]|       2|      1|            0|
//   | **unused** | isLast | isNew | readOrWrite |

xlen_t xfiles_dana_id(int flag_print) {
  xlen_t out;

  XFILES_INSTRUCTION_R_I_I(out, 0, 0, t_USR_XFILES_DANA_ID);

  if (flag_print) {
    uint64_t transaction_table_num_entries = (out >> 48) & ~((~0) << 4);

    uint32_t elements_per_block = (out >> 10) & ~((~0) << 6);
    uint32_t pe_table_num_entries = (out >> 4) & ~((~0) << 6);
    uint32_t cache_num_entries = out & ~((~0) << 4);
    printf("X-FILES/DANA Info:\n"
           "  Transaction Table Entries: %ld\n"
           "  Elements per Block:        %d\n"
           "  PEs:                       %d\n"
           "  Cache Entries:             %d\n",
           transaction_table_num_entries, elements_per_block,
           pe_table_num_entries, cache_num_entries);
  }

  return out;
}

tid_type new_write_request(nnid_type nnid, learning_type_t learning_type,
                           element_type num_train_outputs) {
  uint64_t out, rs2;

  rs2 = (uint64_t) nnid |
    ((uint64_t) num_train_outputs << 32) |
    ((uint64_t) learning_type << 48);

  // Initiate a new transaction by setting the "readOrWrite" (bit 0,
  // read == 0 / write == 1) and "isNew" (bit 1) flags of "funct",
  // i.e., funct == 3. The nnid goes in rs2. The output will show up
  // in the varaible "out".
  XFILES_INSTRUCTION(out, 0, rs2, t_USR_NEW_REQUEST);

  // The TID is in bits [47:32] of what we get back. Pull out this
  // portion and return it. [TODO] This is fragile on tid and element
  // sizing.
  const size_t shift = sizeof(xlen_t)*8 - sizeof(tid_type)*8 - RESP_CODE_WIDTH;
  const xlen_t mask = (~((~(xlen_t)0) << 16)) << shift;
  return (out & mask) >> shift;
}

xlen_t write_register(tid_type tid, xfiles_reg reg, uint32_t value) {

  xlen_t rs2, out;
  rs2 = (uint64_t) value | ((uint64_t) reg << 32);
  XFILES_INSTRUCTION(out, tid, rs2, t_USR_WRITE_REGISTER);
  return out;
}

xlen_t write_data(tid_type tid, element_type * data, size_t count) {
  const size_t shift = sizeof(xlen_t) * 8 - RESP_CODE_WIDTH;
  xlen_t out;

  // There are two types of writes available to users determined by
  // whether or not "isLast" (bit 2) is set. We write all but the last
  // data value with "isLast" deasserted (funct == 1). The tid goes in
  // rs1 and data goes in rs2.
  int write_index = 0;
  while (write_index != count - 1) {
    XFILES_INSTRUCTION(out, tid, data[write_index], t_USR_WRITE_DATA);
    int exit_code = out >> shift;
    switch (exit_code) {
      case resp_OK: write_index++; continue;
      case resp_QUEUE_ERR: continue;
      default: return exit_code;
    }
  }

  // Finally, we write the last data value with "isLast" set (funct ==
  // 5). When the X-Files Arbiter sees this "isLast" bit, it enables
  // execution of the transaction.
  while (1) {
    XFILES_INSTRUCTION(out, tid, data[write_index], t_USR_WRITE_DATA_LAST);
    int exit_code = out >> shift;
    switch (exit_code) {
      case resp_OK: return 0;
      case resp_QUEUE_ERR: continue;
      default: return exit_code;
    }
  }
}

xlen_t write_data_except_last(tid_type tid, element_type * data, size_t count) {
  const size_t shift = sizeof(xlen_t) * 8 - RESP_CODE_WIDTH;
  xlen_t out;

  int write_index = 0;
  while (write_index != count - 1) {
    XFILES_INSTRUCTION(out, tid, data[write_index], t_USR_WRITE_DATA);
    int exit_code = out >> shift;
    switch (exit_code) {
      case resp_OK: write_index++; continue;
      case resp_QUEUE_ERR: continue;
      default: return exit_code;
    }
  }
  return 0;
}

xlen_t write_data_last(tid_type tid, element_type * data, size_t count) {
  const size_t shift = sizeof(xlen_t) * 8 - RESP_CODE_WIDTH;
  xlen_t out;

  while (1) {
    XFILES_INSTRUCTION(out, tid, data[count - 1], t_USR_WRITE_DATA_LAST);
    int exit_code = out >> shift;
    switch (exit_code) {
      case resp_OK: return 0;
      case resp_QUEUE_ERR: continue;
      default: return exit_code;
    }
  }
}

xlen_t write_data_train_incremental(tid_type tid, element_type * input,
                                    element_type * output, size_t count_input,
                                    size_t count_output) {
  // Simply write the exepcted outputs followed by the inputs.
  xlen_t out = 0;
  if ((out = write_data(tid, output, count_output))) return out;
  if ((out = write_data(tid, input, count_input))) return out;
  return 0;
}

xlen_t read_data_spinlock(tid_type tid, element_type * data, size_t count) {
  uint64_t out;

  // Poll via READ_DATA requests until we've gotten enough OK
  // responses equal to the count that we're looking for.
  int read_index = 0;
  while (read_index != count) {
    XFILES_INSTRUCTION_R_R_I(out, tid, 0, t_USR_READ_DATA);
    int exit_code = out >> (32 + 16 + 16 - RESP_CODE_WIDTH);
    switch (exit_code) {
      case resp_NOT_DONE: continue;
      case resp_OK: data[read_index++] = out; continue;
      default: return exit_code;
    }
  }

  return 0;
}

xlen_t kill_transaction(tid_type tid) {
  return -1;
}
