// See LICENSE for license details.

#include "src/main/resources/rocc_test.h"

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
  test.run();

  // Let the simulation run for a few more cycles
  return test.finish();
}
