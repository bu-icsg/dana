// See LICENSE for license details.

#ifndef SRC_MAIN_RESOURCES_XFILES_DEBUG_H_
#define SRC_MAIN_RESOURCES_XFILES_DEBUG_H_

#include "src/main/resources/xcustom.h"
#include "src/main/c/xfiles-debug.h"

class XFilesDebug : public XCustom {
 public:
  XFilesDebug(int x = 0);
  roccCmd DebugEchoViaReg(int data);
  roccCmd DebugReadMem(uint64_t address);
  roccCmd DebugWriteMem(int data, uint64_t address);
  roccCmd DebugVirtToPhys(uint64_t address);
  roccCmd DebugReadUtl(uint64_t address);
  roccCmd DebugWriteUtl(int d, uint64_t a);
 private:
  roccCmd DebugTest(xfiles_debug_action_t action, int data = 0, int rd = 1,
                    uint64_t addr = 0);
};

#endif  // SRC_MAIN_RESOURCES_XFILES_DEBUG_H_
