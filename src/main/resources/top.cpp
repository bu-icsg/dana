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
  XFilesDebug * inst = new XFilesDebug(0);
  roccCmd x = inst->DebugEchoViaReg(0xdead);
  test.inst(x);
  x = inst->DebugEchoViaReg(0xbeef);
  test.inst(x);

  std::cout << "[INFO] Dumping all responses\n";
  while (test.numResp() != 0) {
    roccResp resp = test.popResp();
    std::cout << "[INFO] [" << resp.rd << "]: "
              << std::hex << resp.data << "\n" << std::dec;
  }

  // Let the simulation run for a few more cycles
  return test.finish();
}
