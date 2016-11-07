#include <verilated.h>
#include <iostream>
#include <getopt.h>

#if VM_TRACE
# include <verilated_vcd_c.h>	// Trace file format header
#endif

using namespace std;

//VGCDTester *top;
TOP_TYPE *top;
bool verbose;
bool done_reset;

vluint64_t main_time = 0;       // Current simulation time
// This is a 64-bit integer to reduce wrap over issues and
// allow modulus.  You can also use a double, if you wish.

double sc_time_stamp () { // Called by $time in Verilog
  return main_time;       // converts to double, to match
  // what SystemC does
}

void usage(char * name) {
  printf("Usage: %s [OPTIONS]\n"
         "\n"
         "Options: \n"
         "  -c, --trace=[VCD FILE]     dump a waveform to [VCD FILE]\n"
         "  -h, --help                 print this help and exit\n"
         "  -t, --timeout=[TIMEOUT]    exit if you hit [TIMEOUT] cycles\n"
         "  -v, --verbose              enable Chisel printfs\n",
         name);
}

int main(int argc, char** argv) {
  Verilated::commandArgs(argc, argv);   // Remember args
  top = new TOP_TYPE;

  long timeout = 100000000L;

  int c;
  char * filename_vcd = NULL;
  while (1) {
    static struct option long_options[] = {
      {"help",      no_argument,       0, 'h'},
      {"timeout",   required_argument, 0, 't'},
      {"trace",     required_argument, 0, 'c'},
      {"verbose",   no_argument,       0, 'v'}
    };
    int option_index = 0;
    c = getopt_long (argc, argv, "c:ht:v", long_options, &option_index);
    if (c == -1)
      break;
    switch (c) {
      case 'c':
        filename_vcd = optarg;
        break;
      case 'h':
        usage(argv[0]);
        return 0;
        break;
      case 'v':
        verbose = 1;
        break;
      case 't':
        timeout = atoi(optarg) * 10;
        break;
      default:
        printf("Shouldn't be here\n");
        abort ();
    }
  }

#if VM_TRACE			// If verilator was invoked with --trace
  Verilated::traceEverOn(true);	// Verilator must compute traced signals
  VL_PRINTF("Enabling waves...\n");
  VerilatedVcdC* tfp = new VerilatedVcdC;
  top->trace (tfp, 99);	// Trace 99 levels of hierarchy
  if (filename_vcd) tfp->open(filename_vcd);
#endif

  top->reset = 1;
  done_reset = 1;

  if (verbose) cout << "[INFO] Starting simulation!\n";

  while (!Verilated::gotFinish() && main_time < timeout) {
    if (main_time > 10) {
      top->reset = 0;   // Deassert reset
    }
    if ((main_time % 10) == 1) {
      top->clock = 1;       // Toggle clock
    }
    if ((main_time % 10) == 6) {
      top->clock = 0;
    }
    top->eval();               // Evaluate model
#if VM_TRACE
    if (tfp) tfp->dump (main_time);	// Create waveform trace for this timestamp
#endif
    main_time++;               // Time passes...
  }

  if (main_time >= timeout) {
    cout << "[ERROR] Simulation terminated by timeout at time " << main_time <<
        " (cycle " << main_time / 10 << ")"<< endl;
    return -1;
  } else {
    cout << "[INFO] Simulation completed at time " << main_time <<
        " (cycle " << main_time / 10 << ")"<< endl;
  }

  // Run for 10 more clocks
  vluint64_t end_time = main_time + 100;
  while (main_time < end_time) {
    if ((main_time % 10) == 1) {
      top->clock = 1;       // Toggle clock
    }
    if ((main_time % 10) == 6) {
      top->clock = 0;
    }
    top->eval();               // Evaluate model
#if VM_TRACE
    if (tfp) tfp->dump (main_time);	// Create waveform trace for this timestamp
#endif
    main_time++;               // Time passes...
  }

#if VM_TRACE
  if (tfp) tfp->close();
#endif
}
