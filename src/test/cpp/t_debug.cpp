// See LICENSE.IBM for license details.

#include <tuple>
#include <vector>

#include "src/test/cpp/rocc_test.h"
#include "src/test/cpp/xfiles_debug.h"

int main(int argc, char** argv) {
  Verilated::commandArgs(argc, argv);

  RoccTest test = RoccTest(new TOP_TYPE);
  if (test.parseOptions(argc, argv)) return test.finish();
  if (test.isVerbose()) std::cout << "[INFO] Starting simulation!\n";

  // Apply reset
  test.reset(1);
  done_reset = true;

  // Create all the instructions
  XFilesDebug g(0);
  std::vector<std::tuple<RoccCmd*, RoccResp*>> tests = {
    std::make_tuple(g.DebugEchoViaReg(0xdead), g.RespVal(0xdead)),
    std::make_tuple(g.DebugWriteUtl(0xf00d, 0x20), g.RespVal(0x0)),
    std::make_tuple(g.DebugReadUtl(0x20), g.RespVal(0xf00d))
  };

  // Run the tests
  test.instAndCheck(tests);
  if (test.exit_code() != 0)
    std::cerr << "[ERROR] Tests failed (count: " << test.exit_code() << ")\n";
  else
    if(test.isVerbose()) std::cout << "[INFO] Test passed\n";

  // Let the simulation run for a few more cycles
  for (int i = 0; i < tests.size(); ++i) {
    delete std::get<0>(tests[i]);
    delete std::get<1>(tests[i]);
  }
  return test.finish();
}
