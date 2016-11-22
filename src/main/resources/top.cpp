// See LICENSE for license details.

#include <tuple>
#include <vector>

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

  // Create all the instructions
  XFilesDebug g(0);
  std::vector<std::tuple<RoccCmd*, RoccResp*>> tests;
  tests.push_back(std::make_tuple(g.DebugEchoViaReg(0xdead), g.RespVal(0xdead)));
  tests.push_back(std::make_tuple(g.DebugEchoViaReg(0xbeef), g.RespVal(0xbeef)));
  tests.push_back(std::make_tuple(g.DebugWriteUtl(0xf00d, 0x20), g.RespVal(0x0)));
  tests.push_back(std::make_tuple(g.DebugReadUtl(0x20), g.RespVal(0xf00d)));

  // Run the tests
  for (int i = 0; i < tests.size(); ++i) {
    test.inst(*std::get<0>(tests[i]));
  }

  // Print the responses
  std::cout << "[INFO] Dumping all responses\n";
  while (test.numResp() != 0) {
    RoccResp * resp = test.popResp();
    std::cout << "[INFO] [" << resp->rd_ << "]: "
              << std::hex << resp->data_ << "\n" << std::dec;
    delete resp;
  }

  // Let the simulation run for a few more cycles
  for (int i = 0; i < tests.size(); ++i) {
    delete std::get<0>(tests[i]);
    delete std::get<1>(tests[i]);
  }
  return test.finish();
}
