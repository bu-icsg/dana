// See LICENSE for license details.

#include "src/main/resources/xfiles_debug.h"

XFilesDebug::XFilesDebug(int x) : XCustom(x) {}

roccCmd XFilesDebug::DebugTest(xfiles_debug_action_t action, int data, int rd,
                                int addr) {
  uint64_t action_and_data = ((uint64_t) action << 32) | data;
  return Instruction(t_USR_XFILES_DEBUG, action_and_data, addr, 1, 2, 1);
}

roccCmd XFilesDebug::DebugEchoViaReg(int d) {
  return DebugTest(a_REG, d);
}

roccCmd XFilesDebug::DebugReadMem(int a) {
  return Unimplemented();
}
