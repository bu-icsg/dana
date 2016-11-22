// See LICENSE for license details.

#include "src/main/resources/rocc_test.h"

vluint64_t main_time = 0;
double sc_time_stamp () {
  return main_time;
}

bool done_reset;
bool verbose = false;

extern "C" void dpi_dummy() {};

RoccTest::RoccTest(TOP_TYPE * top) {
  t_ = top;
  main_time_ = &main_time;

  opts_.timeout = 100000000L;
  opts_.exit_code = 0;
  opts_.filename_vcd = NULL;
  opts_.filename_mem = NULL;
  opts_.verbose = false;
  opts_.nofail = false;

#if VM_TRACE
  tfp_ = NULL;
#endif
}

RoccTest::~RoccTest() {
  while (resp_.size()) {
    delete(resp_.front());
    resp_.pop();
  }
  delete t_;
#if VM_TRACE
  if (tfp_) delete tfp_;
#endif
}

void RoccTest::usage(const char * name, const char * extra) {
  printf("Usage: %s [OPTIONS]\n"
         "\n"
         "Options: \n"
         "  -c, --trace=[VCD FILE]     dump a waveform to [VCD FILE]\n"
#if VM_TRACE
#else
         "                               *** Unsupported *** as this emulator was\n"
         "                               not built with Verilator's `--trace` option.\n"
         "                               Rerun with `make debug` to enable this.\n"
#endif
         "  -d, --debug                enable Verilog (Chisel) printfs\n"
         "  -h, --help                 print this help and exit\n"
         "  -m, --memory=[MEM FILE]    initialize physical memory with [MEM FILE]\n"
         "  --no-fail                  all exit codes are zero\n"
         "  -t, --timeout=[TIMEOUT]    exit if you hit [TIMEOUT] cycles\n"
         "  -v, --verbose              enable C++ printfs\n"
         "%s",
         name, extra);
}

int RoccTest::parseOptions(int argc, char** argv) {
  opts_.argv0 = argv[0];
  int c;
  while (1) {
    static struct option long_options[] = {
      {"trace",      required_argument, 0,                'c'},
      {"debug",      no_argument,       0,                'd'},
      {"help",       no_argument,       0,                'h'},
      {"memory",     required_argument, 0,                'm'},
      {"no-fail",    no_argument,       &opts_.nofail,     1},
      {"timeout",    required_argument, 0,                't'},
      {"verbose",    no_argument,       0,                'v'},
      {0, 0, 0, 0}
    };
    int option_index = 0;
    c = getopt_long (argc, argv, "c:dhm:t:v", long_options, &option_index);
    if (c == -1)
      break;
    switch (c) {
      case 0:
        break;
      case 'c':
#if VM_TRACE
        opts_.filename_vcd = optarg;
        break;
#else
        std::cerr <<
            "[ERROR] Trace unsupported. Verilator needs the `--trace` arg to build an\n"
            "[ERROR]   executable that can emit VCD files (use `make debug`).\n";
        opts_.exit_code = -3;
        return opts_.exit_code;
#endif
      case 'd':
        verbose = true;
        break;
      case 'h':
        usage(argv[0]);
        opts_.exit_code = 0;
        return opts_.exit_code;
      case 'm':
        opts_.filename_mem = optarg;
        break;
      case 't':
        opts_.timeout = atoi(optarg) * 2;
        break;
      case 'v':
        opts_.verbose = true;
        break;
      default:
        printf("[ERROR] Bad command line option %d (%c)\n", c, c);
        opts_.exit_code = -2;
        return opts_.exit_code;
    }
  }

#if VM_TRACE
  Verilated::traceEverOn(true);
  VL_PRINTF("Enabling waves...\n");
  tfp_ = new VerilatedVcdC;
  t_->trace (tfp_, 99);
  if (opts_.filename_vcd) {
    tfp_->open(opts_.filename_vcd);
    if (verbose)
      std::cout << "[INFO] Writing VCD file" << opts_.filename_vcd << "\n";
  }
#endif

  return opts_.exit_code;
}

int RoccTest::inst(const RoccCmd & cmd) {
  t_->io_cmd_valid            = 1;
  t_->io_cmd_bits_rs1         = cmd.rs1_;
  t_->io_cmd_bits_rs2         = cmd.rs2_;
  t_->io_cmd_bits_inst_opcode = cmd.inst_.rocc.opcode;
  t_->io_cmd_bits_inst_rd     = cmd.inst_.rocc.rd;
  t_->io_cmd_bits_inst_xs2    = cmd.inst_.rocc.xs2;
  t_->io_cmd_bits_inst_xd     = cmd.inst_.rocc.xd;
  t_->io_cmd_bits_inst_rs1    = cmd.inst_.rocc.rs1;
  t_->io_cmd_bits_inst_rs2    = cmd.inst_.rocc.rs2;
  t_->io_cmd_bits_inst_funct  = cmd.inst_.rocc.funct;

  int response_cycles = 0, got_response;
  while ((cmd.inst_.rocc.xd && !(got_response = tick(1))) ||
         (!cmd.inst_.rocc.xd && response_cycles < 1)) {
    t_->io_cmd_valid = 0;
    response_cycles++;
  }

  if (opts_.verbose)
    std::cout << "[INFO] " << *main_time_ << ": Reponse latency "
              << response_cycles << " cycles\n";
  return got_response;
}

bool RoccTest::instAndCheck(const RoccCmd & cmd, const RoccResp & resp) {
  if (inst(cmd) != 1) return false;
  const RoccResp * r = popResp();
  opts_.exit_code += resp != *r;
  delete r;
  return opts_.exit_code;
}

bool RoccTest::instAndCheck(const std::tuple<RoccCmd*, RoccResp*> & t) {
  return instAndCheck(*std::get<0>(t), *std::get<1>(t));
}

bool RoccTest::instAndCheck(const std::vector<std::tuple<RoccCmd*, RoccResp*>> & t) {
  bool pass = true;
  for (int i = 0; i < t.size(); ++i)
    pass &= instAndCheck(t[i]);
  return pass;
}

int RoccTest::tick(unsigned int num_cycles, bool reset, bool debug) {
  t_->reset = reset;
  int responses = 0;

  for (int unit = 0; unit < num_cycles; ++unit) {
    // clock low
    t_->clock = 0;
    t_->eval();
#if VM_TRACE
    if (tfp_) tfp_->dump(*main_time_);
#endif
    (*main_time_)++;

    if (t_->io_resp_valid) {
      RoccResp * resp = new RoccResp(t_->io_resp_bits_rd, t_->io_resp_bits_data);
      resp_.push(resp);
      responses++;
    }
    if (t_->io_exception) {
      std::cerr << "[INFO] Saw exception\n";
    }

    // clock high
    t_->clock = 1;
    t_->eval();
#if VM_TRACE
    if (tfp_) tfp_->dump(*main_time_);
#endif
    (*main_time_)++;
    t_->io_cmd_valid = 0;
  }
  t_->reset = false;
  return responses;
}

int RoccTest::reset(unsigned int num_cycles) {
  tick(num_cycles, true);
  return num_cycles;
}

int RoccTest::finish(unsigned int drain_cycles) {
  tick(drain_cycles);
#if VM_TRACE
  if (tfp_) tfp_->close();
#endif
  return opts_.nofail ? 0 : opts_.exit_code;
}

int RoccTest::loadMemory(bool safe) {
  if (!opts_.filename_mem) {
    if (safe) {
      std::cerr << "[WARN] Ignoring user request to load memory\n";
      return 0;
    }
    std::cerr << "[ERROR] User ran loadMemory without specifying memory\n";
    usage(opts_.argv0);
    throw std::invalid_argument("User did not specify memory");
  }
  dpi_readmemh(opts_.filename_mem);
  return 0;
}

RoccResp * RoccTest::popResp() {
  RoccResp * resp;
  if (resp_.size() == 0) {
    return NULL;
  }

  resp = resp_.front();
  resp_.pop();
  return resp;
}

int RoccTest::run(unsigned int num_cycles) {
  unsigned int start = *main_time_ / 2;
  unsigned int stop = num_cycles + start;
  int num_responses = 0;
  while (!Verilated::gotFinish() && *main_time_ < opts_.timeout &&
         start++ != stop) {
    num_responses += tick(1);
  }

  if (*main_time_ >= opts_.timeout) {
    std::cerr << "[ERROR] Simulation terminated by timeout at time " << *main_time_ <<
        " (cycle " << *main_time_ / 2 << ")"<< endl;
    opts_.exit_code = -1;
  } else {
    std::cout << "[INFO] Simulation completed at time " << *main_time_ <<
        " (cycle " << *main_time_ / 2 << ")"<< endl;
  }
  return num_responses;
}
