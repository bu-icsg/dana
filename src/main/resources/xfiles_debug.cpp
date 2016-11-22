// See LICENSE for license details.

#include "src/main/resources/xfiles_debug.h"

XFilesDebug::XFilesDebug(int x) : XCustom(x) {}

roccCmd XFilesDebug::DebugTest(xfiles_debug_action_t action, int data, int rd,
                                uint64_t addr) {
  uint64_t action_and_data = ((uint64_t) action << 32) | data;
  return Instruction(t_USR_XFILES_DEBUG, action_and_data, addr, 1, 2, 1);
}

roccCmd XFilesDebug::DebugEchoViaReg(int d) {
  return DebugTest(a_REG, d);
}

roccCmd XFilesDebug::DebugReadMem(uint64_t a) {
  return DebugTest(a_MEM_READ, 0, a);
}

roccCmd XFilesDebug::DebugWriteMem(int d, uint64_t a) {
  return DebugTest(a_MEM_WRITE, d, a);
}

roccCmd XFilesDebug::DebugVirtToPhys(uint64_t a) {
  return DebugTest(a_VIRT_TO_PHYS, 0, a);
}

roccCmd XFilesDebug::DebugReadUtl(uint64_t a) {
  return DebugTest(a_UTL_READ, 0, a);
}

roccCmd XFilesDebug::DebugWriteUtl(int d, uint64_t a) {
  return DebugTest(a_UTL_WRITE, d, a);
}
