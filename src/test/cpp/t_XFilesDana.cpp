#include <iomanip>
#include <unistd.h>
#include <algorithm>
#include <unordered_map>
#include <queue>
#include "time.h"

#include "fann.h"
#include "transaction.h"


typedef enum {
  e_SINGLE = 0,
  e_SMP
} test_type;

typedef struct {
  uint64_t unused;
  uint64_t tid;
  uint64_t data;
} response;

class t_XFilesDana : public XFilesDana_api_t {
private:
  uint64_t cycle;
  bool vcd_flag;
  FILE * vcd;
  XFilesDana_t * xfiles_dana;
  unsigned int seed;
  struct {
    uint64_t num_pes;
    uint64_t cache_num_entries;
    uint64_t elements_per_block;
    uint64_t transaction_table_num_entries;
    uint64_t transaction_table_sram_elements;
    uint64_t register_file_num_elements;
    uint64_t asid_width;
    uint64_t tid_width;
    uint64_t nnid_width;
    uint64_t feedback_width;
    uint64_t element_width;
    uint64_t num_cores;
    uint64_t decimal_point_offset;
    uint64_t decimal_point_width;
  } parameters;

public:
  // Constructors
  t_XFilesDana();
  t_XFilesDana(const string);
  // Destructor
  ~t_XFilesDana();

  // Drive the clock low
  void tick_lo(int);

  // Drive the clock high and update the vcd file if the vcd flag is
  // set
  void tick_hi(int);

  // Tick the clock for a specified number of cycles without changing
  // the inputs. Outputs will be pushed onto a specified output vector
  // (if an output vector is, in fact, specified)
  int tick(int, int, std::vector<response> *, bool);

  // Apply the reset for a specified number of cycles
  void reset(int);

  // Initiate a new write request with the accelerator
  int new_write_request(uint32_t, std::vector<response> *, bool);

  // Write one unit of data
  void write_data(int, int32_t, int, std::vector<response> *, bool);

  // Finalize a write request by sending random data to the
  // accelerator
  void write_rnd_data(int, int, int);

  // Read one unit of data out of Dana for a specific TID
  int new_read_request(int, std::vector<response> *, bool);

  // Set the ASID of the first core's input line to a new value
  // [TODO] Make generic so you can set any core's input line.
  void set_asid(uint16_t);

  // Print out information about the state of all modules in the
  // system
  void info();

  // Print out information about the Transaction Table
  void info_ttable();

  // Print out information about the Cache Table
  void info_cache_table();

  // Print out PE Table info
  void info_petable();

  // Print out Register File info
  void info_reg_file();

  // Print out ASID info
  void info_asids();

  // Load the cache so that memory requests aren't necessary
  void cache_load(int, uint32_t, const char *, bool);

  // Check to see if any entries in the Transaction Table are done
  // [TODO] This method is possibly broken.
  int any_done();

  // Check for valid entries in the Transaction Table
  int any_valid();

  // Check to see if a specific ASID/TID transaction is done
  int is_done(uint16_t, uint16_t);

  // Return the count of the number of cycles
  uint64_t get_cycles();

  // Generic method that compares the output of a FANN neural network
  // for a specific training file to DANA. The inputs in the training
  // file are run in order and one at a time on DANA.
  int testbench_fann(const char *,
                     const char *,
                     const char *, test_type, bool, uint64_t,
                     double);

  // Generic method that throw multiple FANN networks at DANA.
  int testbench_fann(std::vector<const char *> *,
                     std::vector<const char *> *,
                     std::vector<const char *> *, test_type, bool, uint64_t,
                     double);

  // Run a single transaction to completion
  int run_single(transaction *, bool, uint64_t);

  // Run a collection of transactions to completion
  int run_smp(std::vector<transaction *> *, bool, uint64_t);

  // Read a parameter file and populate the local parameters
  int read_parameters(const string);
};

int t_XFilesDana::read_parameters(const string file_string_parameters) {
  std::string line, key, value;
  int pos_del, pos_eol;
  std::ifstream file_params(file_string_parameters, std::ifstream::in);
  std::cout << "[INFO] Reading parameters from file:\n[INFO]   "
            << file_string_parameters << std::endl;

  while (std::getline(file_params, line)) {
    pos_del = line.find(",");
    pos_eol = line.find(")");
    key = line.substr(1, pos_del - 1);
    value = line.substr(pos_del + 1, pos_eol - pos_del - 1);
    if (key.compare("NUM_PES") == 0)
      parameters.num_pes = stoll(value, NULL, 10);
    else if (key.compare("CACHE_NUM_ENTRIES") == 0)
      parameters.cache_num_entries = stoll(value, NULL, 10);
    else if (key.compare("ELEMENTS_PER_BLOCK") == 0)
      parameters.elements_per_block = stoll(value, NULL, 10);
    else if (key.compare("TRANSACTION_TABLE_NUM_ENTRIES") == 0)
      parameters.transaction_table_num_entries = stoll(value, NULL, 10);
    else if (key.compare("TRANSACTION_TABLE_SRAM_ELEMENTS") == 0)
      parameters.transaction_table_sram_elements = stoll(value, NULL, 10);
    else if (key.compare("REGISTER_FILE_NUM_ELEMENTS") == 0)
      parameters.register_file_num_elements = stoll(value, NULL, 10);
    else if (key.compare("TID_WIDTH") == 0)
      parameters.tid_width = stoll(value, NULL, 10);
    else if (key.compare("NNID_WIDTH") == 0)
      parameters.nnid_width = stoll(value, NULL, 10);
    else if (key.compare("FEEDBACK_WIDTH") == 0)
      parameters.feedback_width = stoll(value, NULL, 10);
    else if (key.compare("ELEMENT_WIDTH") == 0)
      parameters.element_width = stoll(value, NULL, 10);
    else if (key.compare("ASID_WIDTH") == 0)
      parameters.asid_width = stoll(value, NULL, 10);
    else if (key.compare("NUM_CORES") == 0)
      parameters.num_cores = stoll(value, NULL, 10);
    else if (key.compare("DECIMAL_POINT_OFFSET") == 0)
      parameters.decimal_point_offset = stoll(value, NULL, 10);
    else if (key.compare("DECIMAL_POINT_WIDTH") == 0)
      parameters.decimal_point_width = stoll(value, NULL, 10);
    else
      std::cout << "[ERROR] Unknown parameter key (" << key << ") found" << std::endl;
    std::cout << "[INFO]     " << key << " -> " << value << std::endl;
  }

  file_params.close();
  return 0;
}

t_XFilesDana::t_XFilesDana() {
  seed = time(NULL);
  srand(seed);
  xfiles_dana = new XFilesDana_t();
  xfiles_dana->init(seed);
  init(xfiles_dana);
  cycle = 0;
  vcd_flag = false;
  std::cout << "[INFO] Using seed: " << std::hex << seed << std::endl;
  std::cout << "[INFO] No vcd file output specified" << std::endl;
}

t_XFilesDana::t_XFilesDana(const string file_string_vcd) {
  seed = time(NULL);
  srand(seed);
  xfiles_dana = new XFilesDana_t();
  xfiles_dana->init(seed);
  init(xfiles_dana);
  cycle = 0;
  vcd_flag = true;
  vcd = fopen(file_string_vcd.c_str(), "w");
  assert(vcd);
  std::cout << "[INFO] Using seed: " << std::hex << seed << std::endl;
  std::cout << "[INFO] Using vcd file:\n[INFO]   " << file_string_vcd << std::endl;
  xfiles_dana->set_dumpfile(vcd);
}

t_XFilesDana::~t_XFilesDana() {
  if (vcd_flag)
    fclose(vcd);
}

void t_XFilesDana::tick_lo(int reset) {
  xfiles_dana->clock_lo(dat_t<1>(reset));
}

void t_XFilesDana::tick_hi(int reset) {
  if (vcd_flag)
    xfiles_dana->dump(vcd, cycle);
  xfiles_dana->clock_hi(dat_t<1>(reset));
  cycle++;
}

int t_XFilesDana::tick(int num_cycles = 1, int reset = 0,
                std::vector<response> * output = NULL, bool debug = false) {
  // [TODO] This needs to support reads from all core lines.
  int responses_seen = 0;
  response r;
  uint64_t data;
  std::string string_full, string_asid, string_tid, string_data;
  for (int i = 0; i < num_cycles; i++) {
    tick_lo(reset);
    tick_hi(reset);
    if (debug) info();
    if (xfiles_dana->XFilesDana__io_arbiter_0_resp_valid == 1) {
      string_full = get_dat_by_name("XFilesDana.io_arbiter_0_resp_bits_data")->get_value().erase(0,2);
      // If any of the following parameters are not divisible by 4
      // (and consequently aligned on nibble boundaries), the lazy
      // logic below to split up the string into asid, tid, and data
      // regions won't work. [TODO] Add a more intelligent way of
      // doing this that works for parameters of any size.
      assert((parameters.asid_width / 4) * 4 == parameters.asid_width);
      assert((parameters.tid_width / 4) * 4 == parameters.tid_width);
      assert((parameters.element_width / 4) * 4 == parameters.element_width);
      // Break up the string into its constituent regions
      string_asid = string_full.substr(0, parameters.asid_width / 4);
      string_tid = string_full.substr(parameters.asid_width / 4, parameters.tid_width / 4);
      string_data = string_full.substr(parameters.asid_width / 4 + parameters.tid_width / 4,
                                       parameters.element_width / 4);
      r.unused = std::stoi(string_asid, NULL, 16);
      r.tid = std::stoi(string_tid, NULL, 16);
      r.data = std::stoll(string_data, NULL, 16);
      if (output != NULL) {
        output->push_back(r);
      }
      else {
        std::cout << "[INFO] Saw response... [UNUSED]+TID: "
                  << r.unused << " + " << r.tid
                  << " Output:"
                  << r.data
                  << std::endl;
      }
      responses_seen++;
    }
  }
  return responses_seen;
}

void t_XFilesDana::reset(int num_cycles) {
  for(int i = 0; i < num_cycles; i++) {
    tick(1,1);
  }
}

int t_XFilesDana::new_write_request(uint32_t nnid, std::vector<response> * outputs = NULL,
                              bool debug = false) {
  uint64_t funct, rs1, rs2;
  // Compute the underlying fields
  funct = 1 | (1 << 1) & ~(1 << 2);
  rs1 = 0 & ~(~(~0 << parameters.feedback_width) << parameters.tid_width);
  rs2 = nnid;
  // Assign the fields to the input wires for core 0
  xfiles_dana->XFilesDana__io_arbiter_0_cmd_valid = 1;
  xfiles_dana->XFilesDana__io_arbiter_0_cmd_bits_inst_funct = funct;
  xfiles_dana->XFilesDana__io_arbiter_0_cmd_bits_rs1 = rs1;
  xfiles_dana->XFilesDana__io_arbiter_0_cmd_bits_rs2 = rs2;
  int responses_seen = tick(1,0, outputs);
  if (debug) info();
  xfiles_dana->XFilesDana__io_arbiter_0_cmd_valid = 0;
  xfiles_dana->XFilesDana__io_arbiter_0_cmd_bits_inst_funct = 0;
  xfiles_dana->XFilesDana__io_arbiter_0_cmd_bits_rs1 = 0;
  xfiles_dana->XFilesDana__io_arbiter_0_cmd_bits_rs2 = 0;
  return responses_seen;
}

void t_XFilesDana::write_data(int tid, int32_t data, int is_last,
                       std::vector<response> * outputs = NULL,
                       bool debug = false) {
  uint64_t funct, rs1, rs2;
  // Compute the fields
  funct = 1 & ~(1 << 2);
  if (is_last) funct |= 1 << 2;
  else funct &= ~(1 << 2);
  rs1 = tid;
  rs2 = data;
  // Assign the fields to the input wires of core 0
  xfiles_dana->XFilesDana__io_arbiter_0_cmd_valid = 1;
  xfiles_dana->XFilesDana__io_arbiter_0_cmd_bits_inst_funct = funct;
  xfiles_dana->XFilesDana__io_arbiter_0_cmd_bits_rs1 = rs1;
  xfiles_dana->XFilesDana__io_arbiter_0_cmd_bits_rs2 = rs2;
  tick(1, 0, outputs);
  if (debug) info();
  xfiles_dana->XFilesDana__io_arbiter_0_cmd_valid = 0;
  xfiles_dana->XFilesDana__io_arbiter_0_cmd_bits_inst_funct = 0;
  xfiles_dana->XFilesDana__io_arbiter_0_cmd_bits_rs1 = 0;
  xfiles_dana->XFilesDana__io_arbiter_0_cmd_bits_rs2 = 0;
}

void t_XFilesDana::write_rnd_data(int tid, int num, int decimal) {
  for (int i = 0; i < num; i++)
    write_data(tid, 256, (i == num - 1));
}

int t_XFilesDana::new_read_request(int tid, std::vector<response> * outputs = NULL,
                            bool debug = false) {
  uint64_t funct, rs1, rs2;
  // Compute the fields;
  funct = 0 & ~(1 << 1) & ~(1 << 2);
  rs1 = tid;
  rs2 = 0;
  // Assign the fields
  xfiles_dana->XFilesDana__io_arbiter_0_cmd_valid = 1;
  xfiles_dana->XFilesDana__io_arbiter_0_cmd_bits_inst_funct = funct;
  xfiles_dana->XFilesDana__io_arbiter_0_cmd_bits_rs1 = rs1;
  xfiles_dana->XFilesDana__io_arbiter_0_cmd_bits_rs2 = rs2;
  int responses_seen = tick(1, 0, outputs, debug);
  xfiles_dana->XFilesDana__io_arbiter_0_cmd_valid = 0;
  xfiles_dana->XFilesDana__io_arbiter_0_cmd_bits_inst_funct = 0;
  xfiles_dana->XFilesDana__io_arbiter_0_cmd_bits_rs1 = 0;
  xfiles_dana->XFilesDana__io_arbiter_0_cmd_bits_rs2 = 0;
  return responses_seen;
}

void t_XFilesDana::set_asid(uint16_t asid) {
  std::cout << "[INFO] Changing ASID to: 0x" << std::hex << asid << std::endl;
  xfiles_dana->XFilesDana__io_arbiter_0_s = 1;
  xfiles_dana->XFilesDana__io_arbiter_0_cmd_valid = 1;
  xfiles_dana->XFilesDana__io_arbiter_0_cmd_bits_rs1 = asid;
  tick(1,0);
  xfiles_dana->XFilesDana__io_arbiter_0_cmd_valid = 0;
  xfiles_dana->XFilesDana__io_arbiter_0_s = 0;
}

void t_XFilesDana::info() {
  std::cout << "[INFO] Dumping tables at cycle " << std::dec << cycle
            << std::endl;
  info_ttable();
  info_cache_table();
  info_petable();
  info_reg_file();
  info_asids();
}

void t_XFilesDana::info_ttable() {
  std::cout << "-------------------------------------------------------------------------------------------------------\n";
  std::cout << "|V|R|W|CV|F?|L?|NL|-C|D|ASID| Tid|Nnid|  #L|  #N|  CL|  CN|CNinL|#NcL|#NnL|idxE|#PeW|RidX| &N|Cache|DP| <- TTable\n";
  std::cout << "-------------------------------------------------------------------------------------------------------\n";
  std::string string_table("XFilesDana.xFilesArbiter.tTable.table_");
  std::stringstream string_field("");
  for (int i = 0; i < parameters.transaction_table_num_entries; i++) {
    // Valid
    string_field.str("");
    string_field << string_table << i << "_valid";
    std::cout << "|" << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // Reserved
    string_field.str("");
    string_field << string_table << i << "_reserved";
    std::cout << "|" << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // Waiting for a response
    string_field.str("");
    string_field << string_table << i << "_waiting";
    std::cout << "|" << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // Cache Valid
    string_field.str("");
    string_field << string_table << i << "_cacheValid";
    std::cout << "| " << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // In the first layer
    string_field.str("");
    string_field << string_table << i << "_inFirst";
    std::cout << "| " << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // In the last layer
    string_field.str("");
    string_field << string_table << i << "_inLast";
    std::cout << "| " << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // Needs Layer Info
    string_field.str("");
    string_field << string_table << i << "_needsLayerInfo";
    std::cout << "| " << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // Decrement Caceh in use (almost done)
    string_field.str("");
    string_field << string_table << i << "_decInUse";
    std::cout << "| " << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // Done
    string_field.str("");
    string_field << string_table << i << "_done";
    std::cout << "|" << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // ASID
    string_field.str("");
    string_field << string_table << i << "_asid";
    std::cout << "|" << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // TID
    string_field.str("");
    string_field << string_table << i << "_tid";
    std::cout << "|" << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // NNID
    string_field.str("");
    string_field << string_table << i << "_nnid";
    std::cout << "|" << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // Number of Layers
    string_field.str("");
    string_field << string_table << i << "_numLayers";
    std::cout << "|" << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // Number of Neurons (referred to as nodes)
    string_field.str("");
    string_field << string_table << i << "_numNodes";
    std::cout << "|" << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // The current layer
    string_field.str("");
    string_field << string_table << i << "_currentLayer";
    std::cout << "|" << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // The current node
    string_field.str("");
    string_field << string_table << i << "_currentNode";
    std::cout << "|" << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // The current node in the current layer
    string_field.str("");
    string_field << string_table << i << "_currentNodeInLayer";
    std::cout << "| " << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // The total nodes in the current layer
    string_field.str("");
    string_field << string_table << i << "_nodesInCurrentLayer";
    std::cout << "|" << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // Number of nodes in the next layer
    string_field.str("");
    string_field << string_table << i << "_nodesInNextLayer";
    std::cout << "|" << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // Element Index (updated when this receives write data)
    string_field.str("");
    string_field << string_table << i << "_indexElement";
    std::cout << "|  " << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // Number of PE Writes
    string_field.str("");
    string_field << string_table << i << "_countPeWrites";
    std::cout << "|" << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // Read index
    string_field.str("");
    string_field << string_table << i << "_readIdx";
    std::cout << "|  " << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // Neuron Pointer
    string_field.str("");
    string_field << string_table << i << "_neuronPointer";
    std::cout << "|" << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // Cache Index
    string_field.str("");
    string_field << string_table << i << "_cacheIndex";
    std::cout << "|" << std::setw(5) << std::setfill(' ')
              << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // Decimal Point
    string_field.str("");
    string_field << string_table << i << "_decimalPoint";
    std::cout << "|" << std::setw(2) << std::setfill(' ')
              << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    std::cout << "|" << std::endl;
  }
  std::cout << std::endl;
}

void t_XFilesDana::info_cache_table() {
  std::cout << "---------------------------\n";
  std::cout << "|V|N|F|NIdx|NMask|Nnid|IUC| <- Cache Table\n";
  std::cout << "---------------------------\n";
  std::string string_table("XFilesDana.dana.cache.table_");
  std::stringstream string_field("");
  for (int i = 0; i < parameters.cache_num_entries; i++) {
    // Valid
    string_field.str("");
    string_field << string_table << i << "_valid";
    std::cout << "|" << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // Notify Flag
    string_field.str("");
    string_field << string_table << i << "_notifyFlag";
    // std::cout << "|" << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    std::cout << "| ";
    // Fetch
    string_field.str("");
    string_field << string_table << i << "_fetch";
    std::cout << "|" << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // std::cout << "| ";
    // Notify Index
    string_field.str("");
    string_field << string_table << i << "_notifyIndex";
    // std::cout << "|" << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    std::cout << "|    ";
    // Notify Mask
    string_field.str("");
    string_field << string_table << i << "_notifyMask";
    // std::cout << "|" << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    std::cout << "|     ";
    // NNID
    string_field.str("");
    string_field << string_table << i << "_nnid";
    std::cout << "|" << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // In Use Count
    string_field.str("");
    string_field << string_table << i << "_inUseCount";
    std::cout << "|  " << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    std::cout << "|" << std::endl;
  }
  std::cout << std::endl;
}

void t_XFilesDana::info_petable() {
  std::cout << "---------------------------------------------------------------------------------------------------------\n";
  std::cout << "|S|IV|WV|ASID| TID|tIdx|CIdx|inLoc|outLoc|InIdx|OutIdx|   &N|   &W|DP|#W|AF|S|    Bias|     Acc| DataOut| <- PE Table\n";
  std::cout << "---------------------------------------------------------------------------------------------------------\n";
  std::string string_table("XFilesDana.dana.peTable.table_");
  std::string string_pe("XFilesDana.dana.peTable.ProcessingElement");
  std::string string_transaction_table("XFilesDana.xFilesArbiter.tTable.table_");
  std::string tIdx;
  bool valid;
  std::stringstream string_field("");
  for (int i = 0; i < parameters.num_pes; i++) {
    // State
    string_field.str("");
    string_field << string_pe;
    if (i > 0) string_field << "_" << i;
    string_field << ".state";
    std::cout << "|" << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    valid = get_dat_by_name(string_field.str())->get_value().erase(0,2) != "0";
    // [TODO] This should read out the state of the PE managed by this
    // PE Table entry
    // Input Valid
    string_field.str("");
    string_field << string_table << i << "_inValid";
    std::cout << "| " << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // Weight Valid
    string_field.str("");
    string_field << string_table << i << "_weightValid";
    std::cout << "| " << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // The TID and ASID are technically not stored in the PE Table (as
    // these are superfluous to its operation). However, these are
    // useful to view when debugging, so we dereference the
    // Transaction Index (tIdx) into the Transaction Table to figure
    // out what the ASID and TID are.
    if (valid) {
      string_field.str("");
      string_field << string_table << i << "_tIdx";
      tIdx = get_dat_by_name(string_field.str())->get_value().erase(0,2);
      // ASID
      string_field.str("");
      string_field << string_transaction_table << tIdx << "_asid";
      std::cout << "|" << get_dat_by_name(string_field.str())->get_value().erase(0,2);
      // TID
      string_field.str("");
      string_field << string_transaction_table << tIdx << "_tid";
      std::cout << "|" << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    }
    else {
      std::cout << "|----|----";
    }
    // // ASID
    // string_field.str("");
    // string_field << string_table << i << "_asid";
    // std::cout << "|" << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // // TID
    // string_field.str("");
    // string_field << string_table << i << "_tid";
    // std::cout << "|" << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // Transaction Index
    string_field.str("");
    string_field << string_table << i << "_tIdx";
    std::cout << "|   " << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // Cache Index
    string_field.str("");
    string_field << string_table << i << "_cIdx";
    std::cout << "|   " << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // Input Location (IO Storage or first/second Register File Partition)
    string_field.str("");
    string_field << string_table << i << "_inLoc";
    std::cout << "|    " << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // Output Location
    string_field.str("");
    string_field << string_table << i << "_outLoc";
    std::cout << "|     " << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // Input Index
    string_field.str("");
    // if (i < 3)
    //   string_field << string_table << i << "_inIdx_1_1";
    // else
      string_field << string_table << i << "_inIdx";
    std::cout << "|  " << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // Output Index
    string_field.str("");
    string_field << string_table << i << "_outIdx";
    std::cout << "|   " << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // Neuron Pointer
    string_field.str("");
    string_field << string_table << i << "_neuronPtr";
    std::cout << "|" << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // Weight Pointer
    string_field.str("");
    // if (i < 3)
    //   string_field << string_table << i << "_weightPtr_1_1";
    // else
      string_field << string_table << i << "_weightPtr";
    std::cout << "|" << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // Decimal Point
    string_field.str("");
    string_field << string_table << i << "_decimalPoint";
    std::cout << "| " << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // Num Weights
    string_field.str("");
    // if (i < 3)
    //   string_field << string_table << i << "_numWeights_1_1";
    // else
      string_field << string_table << i << "_numWeights";
    std::cout << "|" << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // Activation Function
    string_field.str("");
    string_field << string_table << i << "_activationFunction";
    std::cout << "|" << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // Steepness
    string_field.str("");
    string_field << string_table << i << "_steepness";
    std::cout << "|" << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // Bias
    string_field.str("");
    string_field << string_table << i << "_bias";
    std::cout << "|" << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // Accumulator
    string_field.str("");
    string_field << string_pe;
    if (i > 0) string_field << "_" << i;
    string_field << ".acc";
    std::cout << "|" << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // Activation Function Output
    string_field.str("");
    string_field << string_pe;
    if (i > 0) string_field << "_" << i;
    string_field << ".dataOut";
    std::cout << "|" << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // Inputs for this PE
    string_field.str("");
    string_field << string_table << i << "_inBlock";
    std::cout << "|" << get_dat_by_name(string_field.str())->get_value().erase(0,2)
              << "|" << std::endl
              << "                                                                                                        ";
    // Weights for this PE
    string_field.str("");
    string_field << string_table << i << "_weightBlock";
    std::cout << "|" << get_dat_by_name(string_field.str())->get_value().erase(0,2)
              << "|" << std::endl;
  }
  std::cout << std::endl;
}

void t_XFilesDana::info_reg_file() {
  std::cout << "-----------------------------------\n"
            << "|V|E[Wr](0)|#Wr(0)|E[Wr](1)|#Wr(1)| <- Register File\n"
            << "-----------------------------------\n";
  std::string string_table("XFilesDana.dana.regFile.state_");
  std::stringstream string_field("");
  for (int i = 0; i < parameters.transaction_table_num_entries; i++) {
    // Valid
    string_field.str("");
    string_field << string_table << i * 2 << "_valid";
    std::cout << "|" << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // Total number of expected writes
    string_field.str("");
    string_field << string_table << i * 2 << "_totalWrites";
    std::cout << "|    " << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // Number of writes that have been seen
    string_field.str("");
    string_field << string_table << i * 2 << "_countWrites";
    std::cout << "|  " << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // Total number of expected writes
    string_field.str("");
    string_field << string_table << (i + 1) * 2 - 1 << "_totalWrites";
    std::cout << "|    " << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // Number of writes that have been seen
    string_field.str("");
    string_field << string_table << (i + 1) * 2 - 1 << "_countWrites";
    std::cout << "|  " << get_dat_by_name(string_field.str())->get_value().erase(0,2);

    std::cout << "|" << std::endl;
  }
  std::cout << std::endl;
}

void t_XFilesDana::info_asids() {
  std::cout << "------------------\n"
            << "|Core|V|ASID|nTID| <- ASIDs in X-FILES arbiter\n"
            << "------------------\n";
  std::string string_table("XFilesDana.xFilesArbiter.AsidUnit");
  std::stringstream string_field("");
  for (int i = 0; i < parameters.num_cores; i++) {
    // Core Index
    std::cout << "|" << std::setw(4) << std::setfill(' ') << i;
    // Valid
    string_field.str("");
    if (i == 0) string_field << string_table << ".asidReg_valid";
    else string_field << string_table << "_" << i << ".asidReg_valid";
    std::cout << "|" << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // ASID
    string_field.str("");
    if (i == 0) string_field << string_table << ".asidReg_asid";
    else string_field << string_table << "_" << i << ".asidReg_asid";
    std::cout << "|" << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // Next TID
    string_field.str("");
    if (i == 0) string_field << string_table << ".asidReg_tid";
    else string_field << string_table << "_" << i << ".asidReg_tid";
    std::cout << "|" << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    std::cout << "|" << std::endl;
  }
  std::cout << std::endl;
}

void t_XFilesDana::cache_load(int index, uint32_t nnid, const char * file,
                        bool debug = false) {
  std::stringstream ss("");
  std::stringstream val("");
  std::stringstream i_s("");
  std::string file_extension;
  char tmp [2];
  int i;
  int num_bytes;

  // Determine the file extension (.e.g., ".16bin") based on the
  // number of elements per block
  num_bytes = parameters.elements_per_block * 4;
  char buf [num_bytes];
  switch(parameters.elements_per_block) {
  case 4:
    file_extension = ".16bin";
    break;
  case 8:
    file_extension = ".32bin";
    break;
  case 16:
    file_extension = ".64bin";
    break;
  case 32:
    file_extension = ".128bin";
    break;
  }

  // Set the cache table
  ss << "XFilesDana.dana.cache.table_" << index << "_valid";
  get_dat_by_name(ss.str())->set_value("1");
  ss.str("");
  ss << "XFilesDana.dana.cache.table_" << index << "_nnid";
  val << nnid;
  get_dat_by_name(ss.str())->set_value(val.str());
  ss.str("");
  ss << "XFilesDana.dana.cache.table_" << index << "_inUseCount";
  get_dat_by_name(ss.str())->set_value("0");
  ss.str("");
  ss << "XFilesDana.dana.cache.table_" << index << "_fetch";
  get_dat_by_name(ss.str())->set_value("0");

  // Set the cache SRAM
  ifstream config;
  config.open(file + file_extension, ios::in | ios::binary);
  ss.str("");
  ss << "XFilesDana.dana.cache.SRAM";
  if (index > 0) ss << "_" << index;
  ss << ".mem";
  i = 0;
  // Go through the whole file and dump the data into the SRAM
  std::cout << "[INFO] Loading Cache SRAM " << index << " with NNID "
            << std::hex << nnid  << " and data from" << std::endl;
  std::cout << "[INFO]   " << file + file_extension << std::endl;
  while (!config.eof()) {
    // [TODO] The endinannes may need to be swapped here
    // The number of characters to read
    config.read(buf, num_bytes);
    if (debug) std::cout << "[INFO]   " << std::setw(5) << i << ":";
    val.str("");
    val << "0x";
    // This needs to go in reverse order to do an endianness
    // conversion
    for (int j = num_bytes - 1; j >= 0; j--) {
      sprintf(tmp, "%02x", (const unsigned char)buf[j]);
      val << tmp;
    }
    if (debug) std::cout << val.str() << std::endl;
    i_s.str("");
    i_s << i;
    get_mem_by_name(ss.str())->set_element(i_s.str(), val.str());
    i++;
  }
  config.close();
  if (debug) std::cout << "[INFO]   Done!" << std::endl;
}

// [TODO] any_done is possibly broken
int t_XFilesDana::any_done () {
  std::string string_table("XFilesDana.xFilesArbiter.tTable.table_");
  std::stringstream string_field("");
  for (int i = 0; i < parameters.transaction_table_num_entries; i++) {
    string_field.str("");
    string_field << string_table << i << "_done";
    if (std::stoi(get_dat_by_name(string_field.str())->get_value().erase(0,2), NULL, 16)) {
      return 1;
    }
  }
  return 0;
}

int t_XFilesDana::any_valid () {
  std::string string_table("XFilesDana.xFilesArbiter.tTable.table_");
  std::stringstream string_field("");
  for (int i = 0; i < parameters.transaction_table_num_entries; i++) {
    string_field.str("");
    string_field << string_table << i << "_valid";
    if (std::stoi(get_dat_by_name(string_field.str())->get_value().erase(0,2), NULL, 16)) {
      return 1;
    }
  }
  return 0;
}

int t_XFilesDana::is_done (uint16_t _asid, uint16_t _tid) {
  std::string string_table("XFilesDana.xFilesArbiter.tTable.table_");
  std::stringstream string_field("");
  for (int i = 0; i < parameters.transaction_table_num_entries; i++) {
    uint16_t asid, tid;
    int done;
    string_field.str("");
    string_field << string_table << i << "_asid";
    asid = std::stoi(get_dat_by_name(string_field.str())->get_value().erase(0,2), NULL, 16);
    string_field.str("");
    string_field << string_table << i << "_tid";
    tid = std::stoi(get_dat_by_name(string_field.str())->get_value().erase(0,2), NULL, 16);
    string_field.str("");
    string_field << string_table << i << "_done";
    done = std::stoi(get_dat_by_name(string_field.str())->get_value().erase(0,2), NULL, 16);
    // printf("[INFO] Doing is_done check. Found %d, %d, %d, %d\n",
    //        asid, tid, done, _asid == asid && _tid == tid && done);
    if (_asid == asid && _tid == tid && done) {
      return i;
    }
  }
  return -1;
}

uint64_t t_XFilesDana::get_cycles() {
  return cycle;
}

int t_XFilesDana::run_single(transaction * t, bool debug = false,
                      uint64_t cycle_limit = 0) {
  std::vector<response> responses;
  if (debug) info();
  // Initiate a new request. If we don't see a response in the same
  // cycle, then loop until we see a response.
  if (!new_write_request(t->nnid, &responses, debug))
    while (1) if (tick(1, 0, &responses, debug)) break;
  assert(responses.size() == 1);
  t->tid = responses[0].tid;
  std::cout << "[INFO] X-FILES responded with TID: 0x" << std::hex << t->tid
            << std::endl;
  responses.pop_back();
  // Once we have a TID, we can start writing data
  for (unsigned int i = 0; i < t->inputs.size(); i++)
    write_data(t->tid, t->get_input(), t->done_in(), &responses, debug);
  while (is_done(t->asid, t->tid) < 0) {
    tick(1, 0, NULL, debug);
    if (debug) info();
    if (cycle_limit && get_cycles() > cycle_limit) {
      std::cout << "[ERROR] Hit " << std::dec << cycle_limit
                << " cycle limit, bailing..." << std::endl;
      return -1;
    }
  }
  // Once a transaction is done, we can start reading data
  for (int i = 0; i < t->num_output; i++) {
    if (debug) info();
    new_read_request(t->tid, &responses);
  }
  tick(5, 0, &responses, debug);
  // Copy the data over to the output vector
  for (int i = 0; i < responses.size(); i++) {
    assert (t->tid == responses[i].tid);
    t->outputs.push_back(responses[i].data);
  }
  return 0;
}

int t_XFilesDana::run_smp(std::vector<transaction *> * transactions,
                   bool debug = false, uint64_t cycle_limit =0) {
  typedef enum {UNUSED, NEW_WRITE, NEW_WRITE_WAIT,
                WRITE, EXECUTING, READ, READ_WAIT} action_type;

  typedef struct {
    action_type state;
    transaction * t;
  } action;

  std::unordered_map<uint16_t, action *> action_hash;
  std::queue<action *> action_queue;
  std::vector<action> action_pool;
  std::vector<action *> action_pool_work;
  action * a;
  int i, i_assigned = 0, done_in, unused_count;
  int32_t input_next;
  std::vector<response> responses;

  if ((*transactions).size() <= parameters.transaction_table_num_entries)
    action_pool.resize((*transactions).size());
  else
    action_pool.resize(parameters.transaction_table_num_entries);

  if (debug) info();

  // Initial population of transactions
  std::random_shuffle (transactions->begin(), transactions->end());
  for (i = 0; i < action_pool.size(); i++) {
    action_pool[i].t = (*transactions)[i];
    action_pool[i].state = NEW_WRITE;
    i_assigned++;
  }

  // for (int i_tmp = 0; i_tmp < 8000; i_tmp++) {
  while (1) {
    // Iteratre over the action pool populating a vector of actions
    // with core-side requests that need to be generated. Also,
    // compute the total number of unused action slots.
    action_pool_work.clear();
    unused_count = 0;
    for (i = 0; i < action_pool.size(); i++) {
      // Compute the number of unused action slots
      if (action_pool[i].state == UNUSED) unused_count++;
      // If an action slot is unused, then we need to fill it with a
      // new transaction if the action pool is not exhausted.
      if (action_pool[i].state == UNUSED && i_assigned < transactions->size()) {
        action_pool[i].t = (*transactions)[i_assigned++];
        action_pool[i].state = NEW_WRITE;
        unused_count--;
        action_pool_work.push_back(&action_pool[i]);
      }
      // Actionable work (to populated action_pool_work) exists if a
      // transaction is only in select states.
      else if (action_pool[i].state != EXECUTING &&
               action_pool[i].state != NEW_WRITE_WAIT &&
               action_pool[i].state != UNUSED &&
               action_pool[i].state != READ_WAIT)
        action_pool_work.push_back(&action_pool[i]);
    }

    // Run two checks to see if we're done or if we've hit the cycle
    // limit.
    if (unused_count == action_pool.size())
      break;
    if (cycle_limit && get_cycles() > cycle_limit) {
      printf("[ERROR] Hit %d cycle limit, bailing...\n", cycle_limit);
      goto failure;
    }

    // From the actionable transactions, choose a transaction that
    // will generate a request. If there are no actionable
    // transactions, just tick X-FILES/DANA.
    if (action_pool_work.size() > 0) {
      std::random_shuffle (action_pool_work.begin(), action_pool_work.end());
      a = action_pool_work[0];
      // Based on the state of that action, we generate a specific
      // transaction, updating the state as needed.
      switch (a->state) {
      case UNUSED:
        break;
      case NEW_WRITE:
        new_write_request(a->t->nnid, &responses, debug);
        a->state = NEW_WRITE_WAIT;
        // New writes will get a response some time later. The
        // response order is FIFO, so we add these transactions to a
        // FIFO queue that we'll read when we see a TID response.
        action_queue.push(a);
        break;
      case WRITE:
        done_in = a->t->done_in();
        write_data(a->t->tid, a->t->get_input(), done_in, &responses,
                   debug);
        a->state = (done_in) ? EXECUTING : a->state;
        break;
      case READ:
        new_read_request(a->t->tid, &responses, debug);
        a->state = (a->t->new_read()) ? READ_WAIT : a->state;
        break;
      default:
        printf("[ERROR] Unknown action pool state (%d)\n", a->state);
        goto failure;
      }
    } else {
      tick(1, 0, &responses, debug);
    }

    // Peek at the internal state of executing transactions to see if
    // any of these are done. If they are, move the transaction state
    // to READ so that we'll start reading data out during a later
    // cycle.
    for (i = 0; i < action_pool.size(); i++) {
      a = &action_pool[i];
      switch (a->state) {
      case EXECUTING:
        a->state = (is_done(a->t->asid, a->t->tid) > -1) ? READ : EXECUTING;
        break;
      }
    }

    // Look to see if X-FILES/DANA generated any responses and handle
    // them accordingly.
    while (responses.size() != 0) {
      switch (responses.back().unused) {
      case 0: // e_TID, a TID response.
        // The transaction that generated this TID request should be
        // sitting in the action queue waiting for a TID response.
        assert(action_queue.size() > 0);
        a = action_queue.front();
        assert(a->state == NEW_WRITE_WAIT);
        action_queue.pop();
        // New write request response providing a TID. We need to
        // create a new entry in the action hash (first checking to
        // make sure that this doesn't already exist) so that we can
        // find it later on when we get read responses.
        assert(action_hash.find(responses.back().tid) == action_hash.end());
        action_hash[responses.back().tid] = a;
        a->t->tid = responses.back().tid;
        std::cout << "[INFO] X-FILES responded with TID: 0x" << std::hex << a->t->tid
                  << std::endl;
        a->state = WRITE;
        break;
      case 1: // e_READ, a data response to a read request
        // Dereference the transaction from the response' TID and put
        // it in that transactions output vector.
        a = action_hash[responses.back().tid];
        a->t->outputs.push_back(responses.back().data);
        a->state = (a->t->done_out()) ? UNUSED : a->state;
        break;
      default:
        printf("[ERROR] Unknown response type %d found\n",
               responses.back().unused);
        goto failure;
      }
      responses.pop_back();
    }
  }

  printf("[INFO] All transactions finished executing\n");
  return 0;

 failure:
  return 1;
}

int t_XFilesDana::testbench_fann(const char * file_net,
                          const char * file_train,
                          const char * file_cache,
                          test_type type,
                          bool debug = false,
                          uint64_t cycle_limit = 0,
                          double error_bound = 0.1) {
  struct fann *ann = NULL;
  struct fann_train_data *data = NULL;
  fann_type * output_fann;
  fann_layer * layer_it;
  int i, j;
  int decimal_point, total_bound_failures, total_bit_failures, total_outputs;
  uint32_t nnid;
  uint16_t asid;
  double error, error_mean, error_mse;
  uint64_t cycle_start, cycle_stop, edges;
  std::vector<transaction*> transactions;

  // Preload the cache and set the ASID
  nnid = (uint32_t) rand();
  asid = (uint16_t) rand();
  cache_load(0, nnid, file_cache, debug);
  if (debug) info();
  set_asid(asid);

  if ((ann = fann_create_from_file(file_net)) == 0) goto failure;
  if ((data = fann_read_train_from_file(file_train)) == 0) goto failure;

  // Check that the sizing of X-FILES/DANA is okay for the selected NN
  // configuration
  if (ann->num_input > parameters.transaction_table_sram_elements) {
    printf("[ERROR] Num inputs (%d) in NN > TTable SRAM elements (%d)",
           ann->num_input, parameters.transaction_table_sram_elements);
    goto failure;
  }
  if (ann->num_output > parameters.transaction_table_sram_elements) {
    printf("[ERROR] Num outputs (%d) in NN > TTable SRAM elements (%d)",
           ann->num_input, parameters.transaction_table_sram_elements);
    goto failure;
  }
  for (layer_it = ann->first_layer + 1; layer_it != ann->last_layer - 1; layer_it++) {
    if (layer_it->last_neuron - layer_it->first_neuron >
        parameters.register_file_num_elements) {
      printf("[ERROR] Num internal layer outputs (%d) in NN > RegFile num elements (%d)",
             layer_it->last_neuron - layer_it->first_neuron,
             parameters.register_file_num_elements);
      goto failure;
    }
  }

  decimal_point = fann_save_to_fixed(ann, "/dev/null");
  printf("[INFO] Found decimal point: %d\n", decimal_point);
  if (decimal_point < parameters.decimal_point_offset ||
      decimal_point > parameters.decimal_point_offset +
      pow(2, parameters.decimal_point_width) - 1) {
    printf("[ERROR] Decimal point is outside of range");
    goto failure;
  }

  error_mean = 0.0;
  error_mse = 0.0;
  total_outputs = 0;
  total_bound_failures = 0;
  total_bit_failures = 0;
  edges = 0;
  cycle_start = cycle;
  cycle_limit = cycle_limit ? cycle_limit + cycle_start : 0;

  // Create an array of transactions from the input data
  for (i = 0; i < data->num_data; i++) {
    transactions.push_back(new transaction(ann, data->input[i], asid, nnid,
                                           decimal_point));
  }

  switch (type) {
  case e_SINGLE:
    // Execute the transactions
    for (i = 0; i < transactions.size(); i++)
      if (run_single(transactions[i], debug, cycle_limit))
        goto failure;
    break;
  case e_SMP:
    if (run_smp(&transactions, debug, cycle_limit))
      goto failure;
    break;
  default:
    printf("[ERROR] Unknown test type %d\n", type);
    goto failure;
  }
  cycle_stop = cycle;

  // Compute the error for all the executed transactions
  for (i = 0; i < transactions.size(); i++) {
    transactions[i]->update_error(error_bound);
    error_mean += transactions[i]->error;
    error_mse += transactions[i]->error_squared;
    total_outputs += transactions[i]->num_output;
    total_bound_failures += transactions[i]->bound_failures;
    total_bit_failures += transactions[i]->bit_failures;
    edges += transactions[i]->ann->total_connections;
  }

  printf("[INFO] Outputs tested: %d\n", total_outputs);
  printf("[INFO] Total cycles: %d\n", cycle_stop - cycle_start);
  printf("[INFO] Mean error: %0.10f\n",
         error_mean / (fann_type) total_outputs);
  printf("[INFO] Mean squared error: %0.10f\n",
         error_mse / (fann_type) total_outputs);
  printf("[INFO] Total bound failures: %d\n", total_bound_failures);
  printf("[INFO] Total bit failures: %d\n", total_bit_failures);
  printf("[INFO] Throughput: %0.4f edges/cycle (%0.0f%% of max)\n",
         (double) edges / (cycle_stop - cycle_start),
         (double) edges / (cycle_stop - cycle_start) / parameters.num_pes *100);

  for (i = 0; i < transactions.size(); i++)
    delete transactions[i];

  fann_destroy(ann);
  fann_destroy_train(data);
  return 0;

 failure:
  if (data != NULL) fann_destroy_train(data);
  if (ann != NULL) fann_destroy(ann);
  return 1;
}

int t_XFilesDana::testbench_fann(std::vector<const char *> * files_net,
                          std::vector<const char *> * files_train,
                          std::vector<const char *> * files_cache,
                          test_type type,
                          bool debug = false,
                          uint64_t cycle_limit = 0,
                          double error_bound = 0.1) {
  std::vector<struct fann *> ann;
  std::vector<struct fann_train_data *> data;
  fann_type * output_fann;
  fann_layer * layer_it;
  int i, j;
  std::vector<int> decimal_point;
  int total_bound_failures, total_bit_failures, total_outputs;
  std::vector<uint32_t> nnid;
  uint16_t asid;
  double error, error_mean, error_mse;
  uint64_t cycle_start, cycle_stop, edges;
  std::vector<transaction*> transactions;

  // Preload the cache and set the ASID
  if (files_cache->size() > parameters.cache_num_entries) {
    printf("[ERROR] Specified %d cache files, but cache is of size %d\n",
           files_cache->size(), parameters.cache_num_entries);
    goto failure;
  }
  asid = (uint16_t) rand();
  for (i = 0; i < files_cache->size(); i++) {
    nnid.push_back((uint32_t) rand());
    cache_load(i, nnid[i], (*files_cache)[i], debug);
    if (debug) info();
  }
  set_asid(asid);

  ann.resize(files_net->size());
  data.resize(files_train->size());
  decimal_point.resize(files_net->size());
  for (i = 0; i < files_net->size(); i++) {
    if ((ann[i] = fann_create_from_file((*files_net)[i])) == 0) goto failure;
    if ((data[i] = fann_read_train_from_file((*files_train)[i])) == 0) goto failure;
    // Check that the sizing of X-FILES/DANA is okay for the selected NN
    // configuration
    if (ann[i]->num_input > parameters.transaction_table_sram_elements) {
      printf("[ERROR] Num inputs (%d) in NN > TTable SRAM elements (%d)",
             ann[i]->num_input, parameters.transaction_table_sram_elements);
      goto failure;
    }
    if (ann[i]->num_output > parameters.transaction_table_sram_elements) {
      printf("[ERROR] Num outputs (%d) in NN > TTable SRAM elements (%d)",
             ann[i]->num_input, parameters.transaction_table_sram_elements);
      goto failure;
    }
    for (layer_it = ann[i]->first_layer + 1; layer_it != ann[i]->last_layer - 1; layer_it++) {
      if (layer_it->last_neuron - layer_it->first_neuron >
          parameters.register_file_num_elements) {
        printf("[ERROR] Num internal layer outputs (%d) in NN > RegFile num elements (%d)",
               layer_it->last_neuron - layer_it->first_neuron,
               parameters.register_file_num_elements);
        goto failure;
      }
    }
    decimal_point[i] = fann_save_to_fixed(ann[i], "/dev/null");
    printf("[INFO] Found decimal point: %d\n", decimal_point[i]);
    if (decimal_point[i] < parameters.decimal_point_offset ||
        decimal_point[i] > parameters.decimal_point_offset +
        pow(2, parameters.decimal_point_width) - 1) {
      printf("[ERROR] Decimal point is outside of range");
      goto failure;
    }
  }

  error_mean = 0.0;
  error_mse = 0.0;
  total_outputs = 0;
  total_bound_failures = 0;
  total_bit_failures = 0;
  edges = 0;
  cycle_start = cycle;
  cycle_limit = cycle_limit ? cycle_limit + cycle_start : 0;

  // Create an array of transactions from the input data
  for (i = 0; i < ann.size(); i++) {
    for (j = 0; j < data[i]->num_data; j++) {
      transactions.push_back(new transaction(ann[i], data[i]->input[j], asid, nnid[i],
                                             decimal_point[i]));
    }
  }

  switch (type) {
  case e_SINGLE:
    // Execute the transactions
    for (i = 0; i < transactions.size(); i++)
      if (run_single(transactions[i], debug, cycle_limit))
        goto failure;
    break;
  case e_SMP:
    if (run_smp(&transactions, debug, cycle_limit))
      goto failure;
    break;
  default:
    printf("[ERROR] Unknown test type %d\n", type);
    goto failure;
  }
  cycle_stop = cycle;

  // Compute the error for all the executed transactions
  for (i = 0; i < transactions.size(); i++) {
    transactions[i]->update_error(error_bound);
    error_mean += transactions[i]->error;
    error_mse += transactions[i]->error_squared;
    total_outputs += transactions[i]->num_output;
    total_bound_failures += transactions[i]->bound_failures;
    total_bit_failures += transactions[i]->bit_failures;
    edges += transactions[i]->ann->total_connections;
  }

  printf("[INFO] Outputs tested: %d\n", total_outputs);
  printf("[INFO] Total cycles: %d\n", cycle_stop - cycle_start);
  printf("[INFO] Total edges: %d\n", edges);
  printf("[INFO] Mean error: %0.10f\n",
         error_mean / (fann_type) total_outputs);
  printf("[INFO] Mean squared error: %0.10f\n",
         error_mse / (fann_type) total_outputs);
  printf("[INFO] Total bound failures: %d\n", total_bound_failures);
  printf("[INFO] Total bit failures: %d\n", total_bit_failures);
  printf("[INFO] Throughput: %0.4f edges/cycle (%0.0f%% of max)\n",
         (double) edges / (cycle_stop - cycle_start),
         (double) edges / (cycle_stop - cycle_start) / parameters.num_pes *100);

  for (i = 0; i < transactions.size(); i++)
    delete transactions[i];

  for (i = 0; i < ann.size(); i++) {
    fann_destroy(ann[i]);
    fann_destroy_train(data[i]);
  }
  return 0;

 failure:
  for (i = 0; i < ann.size(); i++) {
    fann_destroy(ann[i]);
    fann_destroy_train(data[i]);
  }
  return 1;
};

void usage(const char * bin) {
  // Print a usage string and exit
  const char *string_usage =
    "[OPTION]... PARAMETER_FILE\n"
    "Simulate an X-FILES/DANA accelerator for a given paramter file.\n\n"
    "  -d                         print debug output from tables\n"
    "  -v                         output to the specified vcd file\n";
  printf("Usage: %s ", bin);
  printf("%s", string_usage);
}

int main(int argc, char* argv[]) {
  // t_XFilesDana* api = new t_XFilesDana("build/t_XFilesDana.vcd");
  t_XFilesDana * api;
  bool has_vcd = false, debug = false;
  std::string file_parameters, file_vcd;

  int c;
  while ((c = getopt (argc, argv, "dhv:")) != -1) {
    switch (c) {
    case 'd':
      debug = true;
      break;
    case 'h':
      usage(argv[0]);
      return 0;
    case 'v':
      file_vcd = optarg;
      has_vcd = true;
      break;
    }
  }

  // After parsing all the options, there should be one argument left
  // in argv which is the parameter file
  if (argc - optind != 1) {
    fprintf(stderr, "%s: missing parameter file\n", argv[0]);
    usage(argv[0]);
    return -1;
  }
  file_parameters = argv[optind];

  // Run the constructor for t_XFilesDana specifying a vcd file if we have
  // one
  if (has_vcd) api = new t_XFilesDana(file_vcd);
  else api = new t_XFilesDana();

  // Load the parameters
  api->read_parameters(file_parameters);

  // Apply a multi-cycle reset
  std::cout << "[INFO] Applying reset" << std::endl;
  api->reset(8);
  std::cout << "[INFO] De-asserting reset" << std::endl;

  // No tee file is used, currently
  FILE *tee = NULL;
  api->set_teefile(tee);

  std::vector<const char *> files_net;
  std::vector<const char *> files_train;
  std::vector<const char *> files_cache;
  files_net.push_back("../workloads/data/blackscholes.net");
  files_train.push_back("../workloads/data/blackscholes.train.10");
  files_cache.push_back("../workloads/data/blackscholes-fixed");

  files_net.push_back("../workloads/data/fft.net");
  files_train.push_back("../workloads/data/fft.train.10");
  files_cache.push_back("../workloads/data/fft-fixed");

  files_net.push_back("../workloads/data/inversek2j.net");
  files_train.push_back("../workloads/data/inversek2j.train.10");
  files_cache.push_back("../workloads/data/inversek2j-fixed");

  files_net.push_back("../workloads/data/jmeint.net");
  files_train.push_back("../workloads/data/jmeint.train.10");
  files_cache.push_back("../workloads/data/jmeint-fixed");

  files_net.push_back("../workloads/data/jpeg.net");
  files_train.push_back("../workloads/data/jpeg.train.10");
  files_cache.push_back("../workloads/data/jpeg-fixed");

  files_net.push_back("../workloads/data/kmeans.net");
  files_train.push_back("../workloads/data/kmeans.train.10");
  files_cache.push_back("../workloads/data/kmeans-fixed");

  files_net.push_back("../workloads/data/sobel.net");
  files_train.push_back("../workloads/data/sobel.train.10");
  files_cache.push_back("../workloads/data/sobel-fixed");

  files_net.push_back("../workloads/data/rsa.net");
  files_train.push_back("../workloads/data/rsa.train.10");
  files_cache.push_back("../workloads/data/rsa-fixed");

  if (api->testbench_fann(&files_net,
                          &files_train,
                          &files_cache,
                          e_SINGLE,
                          debug,
                          0,
                          0.1))
    return 1;

  if (api->testbench_fann(&files_net,
                          &files_train,
                          &files_cache,
                          e_SMP,
                          debug,
                          0,
                          0.1))
    return 1;

  if (tee) fclose(tee);
  return 0;
}
