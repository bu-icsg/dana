// See LICENSE for license details.

#include "src/main/resources/rocc_test.h"
#include "src/main/resources/xfiles_debug.h"

int main(int argc, char** argv) {
  Verilated::commandArgs(argc, argv);

  RoccTest test = RoccTest(new TOP_TYPE);
  if (test.parseOptions(argc, argv)) return test.finish();
  if (test.isVerbose()) std::cout << "[INFO] Starting simulation!\n";

  // Apply reset
  test.reset(1);
  done_reset = true;

  // Load the L2 RAM
  test.loadMemory(true);

  // Run the simulation
  XFilesDebug inst(0);
  test.inst(*inst.DebugEchoViaReg(0xdead));
  test.inst(*inst.DebugEchoViaReg(0xbeef));

  // test.inst(*inst.DebugReadMem(0));
  // test.inst(*inst.DebugWriteMem(0, 0));
  // test.inst(*inst.DebugVirtToPhys(0));

  test.inst(*inst.DebugWriteUtl(0xf00d, 0x20));
  test.inst(*inst.DebugReadUtl(0x20));

  std::cout << "[INFO] Dumping all responses\n";
  while (test.numResp() != 0) {
    roccResp resp = test.popResp();
    std::cout << "[INFO] [" << resp.rd << "]: "
              << std::hex << resp.data << "\n" << std::dec;
  }

  // Let the simulation run for a few more cycles
  return test.finish();
}
