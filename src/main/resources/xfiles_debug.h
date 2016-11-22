// See LICENSE for license details.

#ifndef SRC_MAIN_RESOURCES_XFILES_DEBUG_H_
#define SRC_MAIN_RESOURCES_XFILES_DEBUG_H_

#include "src/main/resources/xcustom.h"
#include "src/main/c/xfiles-debug.h"

class XFilesDebug : public XCustom {
 public:
  XFilesDebug(int x = 0);
  RoccCmd * DebugEchoViaReg(int data);
  RoccCmd * DebugReadMem(uint64_t address);
  RoccCmd * DebugWriteMem(int data, uint64_t address);
  RoccCmd * DebugVirtToPhys(uint64_t address);
  RoccCmd * DebugReadUtl(uint64_t address);
  RoccCmd * DebugWriteUtl(int d, uint64_t a);
  RoccResp * RespVal(int d);
 private:
  RoccCmd * DebugTest(xfiles_debug_action_t action, int data = 0, int rd = 1,
                    uint64_t addr = 0);
};

#endif  // SRC_MAIN_RESOURCES_XFILES_DEBUG_H_
