#include <verilated.h>
#include <iostream>
#include <getopt.h>
#if VM_TRACE
# include <verilated_vcd_c.h>	// Trace file format header
#endif
#include "src/main/resources/xfiles_debug.h"
#include "src/main/resources/rocc.h"

extern "C" void dpi_dummy() {};

//VGCDTester *top;
TOP_TYPE *top;
bool done_reset;
bool verbose = false;

vluint64_t main_time = 0;
double sc_time_stamp () {
  return main_time;
}

void usage(char * name) {
  printf("Usage: %s [OPTIONS]\n"
         "\n"
         "Options: \n"
         "  -c, --trace=[VCD FILE]     dump a waveform to [VCD FILE]\n"
         "  -d, --debug                enable Verilog (Chisel) printfs\n"
         "  -h, --help                 print this help and exit\n"
         "  -m, --memory=[MEM FILE]    initialize physical memory with [MEM FILE]\n"
         "  -t, --timeout=[TIMEOUT]    exit if you hit [TIMEOUT] cycles\n"
         "  -v, --verbose              enable C++ printfs\n",
         name);
}

typedef struct {
  bool verbose;
  char * filename_vcd;
  char * filename_mem;
  long timeout;
  int exit_code;
} t_options;

t_options parse_options(int argc, char** argv) {
  t_options opts;
  opts.timeout = 100000000L;
  opts.exit_code = 0;
  opts.filename_vcd = NULL;
  opts.filename_mem = NULL;
  opts.verbose = false;

  int c;
  while (1) {
    static struct option long_options[] = {
      {"trace",     required_argument, 0, 'c'},
      {"debug",     no_argument,       0, 'd'},
      {"help",      no_argument,       0, 'h'},
      {"memory",    required_argument, 0, 'm'},
      {"timeout",   required_argument, 0, 't'},
      {"verbose",   no_argument,       0, 'v'}
    };
    int option_index = 0;
    c = getopt_long (argc, argv, "c:dhm:t:v", long_options, &option_index);
    if (c == -1)
      break;
    switch (c) {
      case 'c':
        opts.filename_vcd = optarg;
        break;
      case 'd':
        verbose = true;
        break;
      case 'h':
        usage(argv[0]);
        opts.exit_code = 1;
        return opts;
      case 'm':
        opts.filename_mem = optarg;
        break;
      case 'v':
        opts.verbose = true;
        break;
      case 't':
        opts.timeout = atoi(optarg) * 10;
        break;
      default:
        printf("Shouldn't be here\n");
        abort ();
    }
  }

  return opts;
}

uint64_t roccCommunicate(roccCmd& cmd, Rocc& interface, bool verbose = false) {
  // Send the cmd
  if (verbose) std::cout << "[INFO] Insn: 0x" << std::hex << cmd.insn.raw << "\n";
  interface.Cmd(cmd);

  // Wait for a response if xd is set
  if (cmd.insn.rocc.xd) {
    // assert(false);
  } else {
    return 0;
  }
}

int main(int argc, char** argv) {
  Verilated::commandArgs(argc, argv);   // Remember args
  top = new TOP_TYPE;
  int exit_code = 0;

  t_options opts = parse_options(argc, argv);
  if (opts.exit_code > 0) return exit_code;

#if VM_TRACE			// If verilator was invoked with --trace
  Verilated::traceEverOn(true);	// Verilator must compute traced signals
  VL_PRINTF("Enabling waves...\n");
  VerilatedVcdC* tfp = new VerilatedVcdC;
  top->trace (tfp, 99);	// Trace 99 levels of hierarchy
  if (opts.filename_vcd) tfp->open(opts.filename_vcd);
#endif

  top->reset = 1;
  done_reset = 1;

  if (opts.verbose) cout << "[INFO] Starting simulation!\n";

  // Instruction emitter
  XFilesDebug instructions(0);
  // RoCC interface layer
  Rocc rocc;

  while (!Verilated::gotFinish() && main_time < opts.timeout) {
    if (main_time > 10) {
      top->reset = 0;
    }
    if (main_time == 11 && opts.filename_mem) {
      dpi_readmemh(opts.filename_mem);
      roccCmd r = instructions.DebugEchoViaReg(0);
      roccCommunicate(r, rocc, opts.verbose);
    }
    if ((main_time % 10) == 1) {
      top->clock = 1;
    }
    if ((main_time % 10) == 6) {
      top->clock = 0;
    }
    top->eval();
#if VM_TRACE
    if (tfp) tfp->dump (main_time);
#endif
    main_time++;
  }

  if (main_time >= opts.timeout) {
    cout << "[ERROR] Simulation terminated by timeout at time " << main_time <<
        " (cycle " << main_time / 10 << ")"<< endl;
    exit_code = -1;
    return exit_code;
  } else {
    cout << "[INFO] Simulation completed at time " << main_time <<
        " (cycle " << main_time / 10 << ")"<< endl;
  }

  // Run for 10 more clocks
  vluint64_t end_time = main_time + 100;
  while (main_time < end_time) {
    if ((main_time % 10) == 1) {
      top->clock = 1;
    }
    if ((main_time % 10) == 6) {
      top->clock = 0;
    }
    top->eval();
#if VM_TRACE
    if (tfp) tfp->dump (main_time);
#endif
    main_time++;
  }

#if VM_TRACE
  if (tfp) tfp->close();
#endif
  return exit_code;
}
