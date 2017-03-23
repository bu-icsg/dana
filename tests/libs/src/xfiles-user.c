// See LICENSE.IBM for license details.

#include "src/include/xfiles-user.h"
#include "src/include/xfiles-debug.h"
#include "src/include/xfiles-supervisor.h"

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

xlen_t xfiles_dana_id() {
  return xf_read_csr(csr_XFID_CURRENT);
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

xlen_t transaction_feedforward(nnid_type nnid, element_type * addr_i,
                               element_type * addr_o, int num_inputs,
                               int num_outputs) {
  tid_type tid = new_write_request(nnid, 0, 0);
  write_data(tid, addr_i, num_inputs);
  return read_data_spinlock(tid, addr_o, num_outputs);
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

xlen_t transaction_learn(nnid_type nnid, element_type * addr_i, element_type * addr_o,
                                    element_type * addr_e, size_t num_inputs,
                                    size_t num_outputs) {
  tid_type tid = new_write_request(nnid, 1, 0);
  xlen_t out = 0;
  write_data(tid, addr_e, num_outputs);
  write_data(tid, addr_i, num_inputs);
  if ((out = read_data_spinlock(tid, addr_o, num_outputs))) return out;
  return 0;
}

xlen_t xfiles_fann_learn(nnid_type nnid,
                        element_type * addr_i,
                        element_type * addr_o,
                        element_type * addr_e,
                        int num_inputs, 
                        int num_outputs,
                        int num_data) {
     
    element_type * last = addr_i + num_inputs * num_data;
    for(; addr_i < last;
            addr_i += num_inputs, addr_o += num_outputs, addr_e += num_outputs) {
        if (transaction_learn(nnid, addr_i, addr_o, addr_e, num_inputs, num_outputs))
            return -1;
    }
    return 0;
}

xlen_t xfiles_fann_run_no_compare(nnid_type nnid,
                               element_type * addr_i,
                               element_type * addr_o,
                               element_type * addr_e,
                               int num_inputs,
                               int num_outputs,
                               int num_data) {
  element_type * last = addr_i + num_inputs * num_data;
  for (; addr_i < last;
       addr_i += num_inputs, addr_o += num_outputs, addr_e += num_outputs) {
    if (transaction_feedforward(nnid, addr_i, addr_o, num_inputs, num_outputs))
      return -1;
  }
  return 0;
}

xlen_t xfiles_fann_run_compare(nnid_type nnid,
                               element_type * addr_i,
                               element_type * addr_o,
                               element_type * addr_e,
                               int num_inputs,
                               int num_outputs,
                               int num_data) {
  int failures = 0;
  element_type * last = addr_i + num_inputs * num_data;
  element_type * first = addr_i;
  for (; addr_i < last;
       addr_i += num_inputs, addr_o += num_outputs, addr_e += num_outputs) {
    if (transaction_feedforward(nnid, addr_i, addr_o, num_inputs, num_outputs))
      return -1;
    for (int j = 0; j < num_outputs; j++)
      failures += addr_o[j] != addr_e[j];
    if (failures)
      return ((addr_i - first) / num_inputs + 1) << 32 | failures;
  }
  return 0;
}

xlen_t xfiles_fann_run_smp_compare(nnid_type nnid,
                                   element_type * addr_i,
                                   element_type * addr_o,
                                   element_type * addr_e,
                                   int num_inputs,
                                   int num_outputs,
                                   int num_data) {
  // Read the info block to figure out how many simultaneous
  // transactions we can support
  int failures = 0;
  xlen_t id = xf_read_csr(csr_XFID_CURRENT);
  int entries = id >> (64 - 16);
  element_type * last = addr_i + num_inputs * num_data;
  element_type * first = addr_i;
  // element_type * first = addr_i;
  for (; addr_i < last; addr_i += num_inputs, addr_o += num_outputs,
                        addr_e += num_outputs) {
    tid_type tid = -1;
    // Prime all transactions
    for (int i = 0; i < entries; i++) {
      tid = new_write_request(nnid, 0, 0);
      write_data_except_last(tid, addr_i, num_inputs);
    }
    // Start all transactions
    for (int i = 0; i < entries; i++)
      write_data_last(tid - (entries - 1) + i, addr_i, num_inputs);
    // Reap transactions
    for (int i = 0; i < entries; i++) {
      read_data_spinlock(tid - (entries - 1) + i, addr_o, num_outputs);
      for (int j = 0; j < num_outputs; j++) {
        failures += addr_o[j] != addr_e[j];
        addr_o[j] = 0;
      }
      if (failures)
        return ((addr_i - first) / num_inputs + 1) << 32 | failures;
    }
  }
  return 0;
}

xlen_t read_data_spinlock(tid_type tid, element_type * data, size_t count) {
  volatile uint64_t out;

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
