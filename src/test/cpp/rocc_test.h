// See LICENSE.IBM for license details.

#ifndef SRC_TEST_CPP_ROCC_TEST_H_
#define SRC_TEST_CPP_ROCC_TEST_H_

#include <iostream>
#include <vector>
#include <queue>
#include <getopt.h>
#include <verilated.h>
#if VM_TRACE
#include <verilated_vcd_c.h>
#endif

#include "src/test/cpp/xcustom.h"

typedef struct {
  bool verbose;
  char * filename_vcd;
  char * filename_mem;
  long timeout;
  int exit_code;
  int nofail;
  int resolution;
  char * argv0;
} t_options;

class RoccTest {
 private:
  TOP_TYPE * t_;
  vluint64_t * main_time_;
  std::queue<RoccResp*> resp_;
  unsigned int half_;
  t_options opts_;
#if VM_TRACE
  VerilatedVcdC * tfp_;
#endif

 public:
  RoccTest(TOP_TYPE * top);
  ~RoccTest();

  // Utility functions
  int parseOptions(int argc, char ** argv);

  // Low-level operations
  int tick(unsigned int num_cycles = 1, bool reset = false, bool debug = false);
  int reset(unsigned int num_cycles = 1);
  int finish(unsigned int drain_cycles = 1);
  int loadMemory(bool safe = false);
  RoccResp * popResp();

  // RoCC Command-level functions
  int inst(const RoccCmd & cmd);
  bool instAndCheck(const RoccCmd & cmd, const RoccResp & resp);
  bool instAndCheck(const std::tuple<RoccCmd*, RoccResp*> & t);
  bool instAndCheck(const std::vector<std::tuple<RoccCmd*, RoccResp*>> & t);

  // Testcase functions
  int run(std::vector<RoccCmd> &);
  int run(void *);

  // Blindly run until the end
  int run(unsigned int num_cycles = -1);

  // Accessor functions
  bool isVerbose()     { return opts_.verbose;   }
  int numResp()        { return resp_.size();    }
  vluint64_t getTime() { return *main_time_;     }
  int exit_code()      { return opts_.exit_code; }

 private:
  void usage(const char * name, const char * extra = NULL);
};

#endif  // SRC_TEST_CPP_ROCC_TEST_H_
