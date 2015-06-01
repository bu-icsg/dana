#include <iomanip>
#include <unistd.h>
#include "fann.h"
#include "transaction.h"

class t_Top : public Top_api_t {
private:
  uint64_t cycle;
  bool vcd_flag;
  FILE * vcd;
  Top_t * top;
  struct {
    uint64_t num_pes;
    uint64_t cache_num_entries;
    uint64_t elements_per_block;
    uint64_t transaction_table_num_entries;
    uint64_t asid_width;
    uint64_t tid_width;
    uint64_t nnid_width;
    uint64_t feedback_width;
    uint64_t element_width;
    uint64_t num_cores;
  } parameters;

public:
  // Constructors
  t_Top();
  t_Top(const string);
  // Destructor
  ~t_Top();

  // Drive the clock low
  void tick_lo(int);

  // Drive the clock high and update the vcd file if the vcd flag is
  // set
  void tick_hi(int);

  // Tick the clock for a specified number of cycles without changing
  // the inputs. Outputs will be pushed onto a specified output vector
  // (if an output vector is, in fact, specified)
  int tick(int, int, std::vector<int32_t> *);

  // Apply the reset for a specified number of cycles
  void reset(int);

  // Initiate a new write request with the accelerator
  void new_write_request(uint32_t, std::vector<int32_t> *, bool);

  // Write one unit of data
  void write_data(int, int32_t, int, bool);

  // Finalize a write request by sending random data to the
  // accelerator
  void write_rnd_data(int, int, int);

  // Read one unit of data out of Dana for a specific TID
  void new_read_request(int, std::vector<int32_t> *);

  // Set the ASID of the first core's input line to a new value
  void set_asid(int);

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
  int any_done();

  // Check for valid entries in the Transaction Table
  int any_valid();

  // Return the count of the number of cycles
  int get_cycles();

  // Generic method that compares the output of a FANN neural network
  // for a specific training file to DANA. The cache must be
  // preloaded.
  int testbench_fann(uint16_t, uint32_t, const char *,
                     const char *, bool);

  // Run a set of transactions on the DANA object
  int testbench(std::vector<transaction *> *);

  // Run a single transaction to completion
  int run_single(transaction *, bool, int);

  // Read a parameter file and populate the local parameters
  int read_parameters(const string);
};

int t_Top::read_parameters(const string file_string_parameters) {
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
      parameters.num_pes = stoi(value);
    else if (key.compare("CACHE_NUM_ENTRIES") == 0)
      parameters.cache_num_entries = stoi(value);
    else if (key.compare("ELEMENTS_PER_BLOCK") == 0)
      parameters.elements_per_block = stoi(value);
    else if (key.compare("TRANSACTION_TABLE_NUM_ENTRIES") == 0)
      parameters.transaction_table_num_entries = stoi(value);
    else if (key.compare("TID_WIDTH") == 0)
      parameters.tid_width = stoi(value);
    else if (key.compare("NNID_WIDTH") == 0)
      parameters.nnid_width = stoi(value);
    else if (key.compare("FEEDBACK_WIDTH") == 0)
      parameters.feedback_width = stoi(value);
    else if (key.compare("ELEMENT_WIDTH") == 0)
      parameters.element_width = stoi(value);
    else if (key.compare("ASID_WIDTH") == 0)
      parameters.asid_width = stoi(value);
    else if (key.compare("NUM_CORES") == 0)
      parameters.num_cores = stoi(value);
    else
      std::cout << "[ERROR] Unknown parameter key (" << key << ") found" << std::endl;
    std::cout << "[INFO]     " << key << " -> " << value << std::endl;
  }

  file_params.close();
  return 0;
}

t_Top::t_Top() {
  top = new Top_t();
  top->init();
  init(top);
  cycle = 0;
  vcd_flag = false;
  std::cout << "[INFO] No vcd file output specified" << std::endl;
}

t_Top::t_Top(const string file_string_vcd) {
  top = new Top_t();
  top->init();
  init(top);
  cycle = 0;
  vcd_flag = true;
  vcd = fopen(file_string_vcd.c_str(), "w");
  assert(vcd);
  std::cout << "[INFO] Using vcd file:\n[INFO]   " << file_string_vcd << std::endl;
  top->set_dumpfile(vcd);
}

t_Top::~t_Top() {
  if (vcd_flag)
    fclose(vcd);
}

void t_Top::tick_lo(int reset) {
  top->clock_lo(dat_t<1>(reset));
}

void t_Top::tick_hi(int reset) {
  if (vcd_flag)
    top->dump(vcd, cycle);
  top->clock_hi(dat_t<1>(reset));
  cycle++;
}

int t_Top::tick(int num_cycles = 1, int reset = 0,
                 std::vector<int32_t> * output = NULL) {
  int responses_seen = 0;
  uint64_t data;
  int asid, tid;
  std::string string_full, string_asid, string_tid, string_data;
  for (int i = 0; i < num_cycles; i++) {
    tick_lo(reset);
    tick_hi(reset);
    if (top->Top__io_arbiter_0_resp_valid == 1) {
      string_full = get_dat_by_name("Top.io_arbiter_0_resp_bits_data")->get_value().erase(0,2);
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
      asid = std::stoi(string_asid, 0, 16);
      tid = std::stoi(string_tid, 0, 16);
      data = std::stoi(string_data, 0, 16);
      // data = std::stoll(get_dat_by_name("Top.io_arbiter_0_resp_bits_data")->get_value().erase(0,2), 0, 16);
      if (output != NULL) {
        output->push_back(data);
      }
      else {
        std::cout << "[INFO] Saw response... ASID+TID: "
                  << asid << " + " << tid
                  << " Output:"
                  << (data & ~(~(uint64_t)0 << parameters.element_width))
                  << std::endl;
      }
      responses_seen++;
    }
  }
  return responses_seen;
}

void t_Top::reset(int num_cycles) {
  for(int i = 0; i < num_cycles; i++) {
    tick(1,1);
  }
}

void t_Top::new_write_request(uint32_t nnid, std::vector<int32_t> * outputs = NULL,
                              bool debug = false) {
  uint64_t funct, rs1, rs2;
  // Compute the underlying fields
  funct = 1 | (1 << 1) & ~(1 << 2);
  rs1 = 0 & ~(~(~0 << parameters.feedback_width) << parameters.tid_width);
  rs2 = nnid;
  // Assign the fields to the input wires for core 0
  top->Top__io_arbiter_0_cmd_valid = 1;
  top->Top__io_arbiter_0_cmd_bits_inst_funct = funct;
  top->Top__io_arbiter_0_cmd_bits_rs1 = rs1;
  top->Top__io_arbiter_0_cmd_bits_rs2 = rs2;
  tick(1,0, outputs);
  if (debug) info();
  top->Top__io_arbiter_0_cmd_valid = 0;
  top->Top__io_arbiter_0_cmd_bits_inst_funct = 0;
  top->Top__io_arbiter_0_cmd_bits_rs1 = 0;
  top->Top__io_arbiter_0_cmd_bits_rs2 = 0;
}

void t_Top::write_data(int tid, int32_t data, int is_last, bool debug = false) {
  uint64_t funct, rs1, rs2;
  // Compute the fields
  funct = 1 & ~(1 << 2);
  if (is_last) funct |= 1 << 2;
  else funct &= ~(1 << 2);
  rs1 = tid;
  rs2 = data;
  // Assign the fields to the input wires of core 0
  top->Top__io_arbiter_0_cmd_valid = 1;
  top->Top__io_arbiter_0_cmd_bits_inst_funct = funct;
  top->Top__io_arbiter_0_cmd_bits_rs1 = rs1;
  top->Top__io_arbiter_0_cmd_bits_rs2 = rs2;
  tick(1,0);
  if (debug) info();
  top->Top__io_arbiter_0_cmd_valid = 0;
  top->Top__io_arbiter_0_cmd_bits_inst_funct = 0;
  top->Top__io_arbiter_0_cmd_bits_rs1 = 0;
  top->Top__io_arbiter_0_cmd_bits_rs2 = 0;
}

void t_Top::write_rnd_data(int tid, int num, int decimal) {
  for (int i = 0; i < num; i++)
    write_data(tid, 256, (i == num - 1));
}

void t_Top::new_read_request(int tid, std::vector<int32_t> * outputs = NULL) {
  uint64_t funct, rs1, rs2;
  // Compute the fields;
  funct = 0 & ~(1 << 1) & ~(1 << 2);
  rs1 = tid;
  rs2 = 0;
  // Assign the fields
  top->Top__io_arbiter_0_cmd_valid = 1;
  top->Top__io_arbiter_0_cmd_bits_inst_funct = funct;
  top->Top__io_arbiter_0_cmd_bits_rs1 = rs1;
  top->Top__io_arbiter_0_cmd_bits_rs2 = rs2;
  tick(1, 0, outputs);
  top->Top__io_arbiter_0_cmd_valid = 0;
  top->Top__io_arbiter_0_cmd_bits_inst_funct = 0;
  top->Top__io_arbiter_0_cmd_bits_rs1 = 0;
  top->Top__io_arbiter_0_cmd_bits_rs2 = 0;
}

void t_Top::set_asid(int asid) {
  std::cout << "[INFO] Changing ASID to: 0x" << std::hex << asid << std::endl;
  top->Top__io_arbiter_0_s = 1;
  top->Top__io_arbiter_0_cmd_valid = 1;
  top->Top__io_arbiter_0_cmd_bits_rs1 = asid;
  tick(1,0);
  top->Top__io_arbiter_0_cmd_valid = 0;
  top->Top__io_arbiter_0_s = 0;
}

void t_Top::info() {
  std::cout << "[INFO] Dumping tables at cycle " << cycle << std::endl;
  info_ttable();
  info_cache_table();
  info_petable();
  info_reg_file();
  info_asids();
}

void t_Top::info_ttable() {
  std::cout << "----------------------------------------------------------------------------------------\n";
  std::cout << "|V|R|W|CV|F?|L?|NL|NR|D|ASID| Tid|Nnid|  #L|  #N|  CL|  CN|CNinL|#NcL|#NnL| &N|Cache|DP| <- TTable\n";
  std::cout << "----------------------------------------------------------------------------------------\n";
  std::string string_table("Top.xFilesArbiter.tTable.table_");
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
    // Needs Registers
    string_field.str("");
    string_field << string_table << i << "_needsRegisters";
    std::cout << "| " << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // Done [TODO] this is a placeholder until done is actually set/used
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

void t_Top::info_cache_table() {
  std::cout << "---------------------------\n";
  std::cout << "|V|N|F|NIdx|NMask|Nnid|IUC| <- Cache Table\n";
  std::cout << "---------------------------\n";
  std::string string_table("Top.dana.cache.table_");
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
    // std::cout << "|" << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    std::cout << "| ";
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

void t_Top::info_petable() {
  std::cout << "------------------------------------------------------------------------------------------------------------------\n";
  std::cout << "|S|IV|WV|ASID| TID|tIdx|CIdx|Node|inLoc|outLoc|InIdx|OutIdx|   &N|   &W|DP|LiL|#W|AF|S|    Bias|     Acc| DataOut| <- PE Table\n";
  std::cout << "------------------------------------------------------------------------------------------------------------------\n";
  std::string string_table("Top.dana.peTable.table_");
  std::string string_pe("Top.dana.peTable.ProcessingElement");
  std::stringstream string_field("");
  for (int i = 0; i < parameters.num_pes; i++) {
    // State
    string_field.str("");
    string_field << string_pe;
    if (i > 0) string_field << "_" << i;
    string_field << ".state";
    std::cout << "|" << get_dat_by_name(string_field.str())->get_value().erase(0,2);
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
    // ASID
    string_field.str("");
    string_field << string_table << i << "_asid";
    std::cout << "|" << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // Transaction ID
    string_field.str("");
    string_field << string_table << i << "_tid";
    std::cout << "|" << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // Transaction Index
    string_field.str("");
    string_field << string_table << i << "_tIdx";
    std::cout << "|   " << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // Cache Index
    string_field.str("");
    string_field << string_table << i << "_cIdx";
    std::cout << "|   " << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // Current Neuron (in this layer)
    string_field.str("");
    string_field << string_table << i << "_nnNode";
    std::cout << "| " << get_dat_by_name(string_field.str())->get_value().erase(0,2);
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
    // Last in Layer
    string_field.str("");
    string_field << string_table << i << "_lastInLayer";
    std::cout << "|  " << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // // In Block
    // string_field.str("");
    // string_field << string_table << i << "_inBlock";
    // std::cout << "|  " << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // // Weight Block
    // string_field.str("");
    // string_field << string_table << i << "_weightBlock";
    // std::cout << "|  " << get_dat_by_name(string_field.str())->get_value().erase(0,2);
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
    std::cout << "|" << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // Weights for this PE
    string_field.str("");
    string_field << string_table << i << "_weightBlock";
    std::cout << "|" << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    std::cout << "|" << std::endl;
  }
  std::cout << std::endl;
}

void t_Top::info_reg_file() {
  std::cout << "---------------------------------\n"
            << "|E[Wr](0)|#Wr(0)|E[Wr](1)|#Wr(1)| <- Register File\n"
            << "---------------------------------\n";
  std::string string_table("Top.dana.regFile.state_");
  std::stringstream string_field("");
  for (int i = 0; i < parameters.transaction_table_num_entries; i++) {
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

void t_Top::info_asids() {
  std::cout << "------------------\n"
            << "|Core|V|ASID|nTID| <- ASIDs in X-FILES arbiter\n"
            << "------------------\n";
  std::string string_table("Top.xFilesArbiter.AsidUnit");
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

void t_Top::cache_load(int index, uint32_t nnid, const char * file,
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
  ss << "Top.dana.cache.table_" << index << "_valid";
  get_dat_by_name(ss.str())->set_value("1");
  ss.str("");
  ss << "Top.dana.cache.table_" << index << "_nnid";
  val << nnid;
  get_dat_by_name(ss.str())->set_value(val.str());

  // Set the cache SRAM
  ifstream config;
  config.open(file + file_extension, ios::in | ios::binary);
  ss.str("");
  ss << "Top.dana.cache.SRAM";
  if (index > 0) ss << "_" << index;
  ss << ".mem";
  i = 0;
  // Go through the whole file and dump the data into the SRAM
  std::cout << "[INFO] Loading Cache SRAM " << index << " with NNID "
            << nnid  << " and data from" << std::endl;
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

int t_Top::any_done () {
  std::string string_table("Top.xFilesArbiter.tTable.table_");
  std::stringstream string_field("");
  for (int i = 0; i < parameters.transaction_table_num_entries; i++) {
    string_field.str("");
    string_field << string_table << i << "_done";
    if (std::stoi(get_dat_by_name(string_field.str())->get_value().erase(0,2))) {
      return 1;
    }
  }
  return 0;
}

int t_Top::any_valid () {
  std::string string_table("Top.xFilesArbiter.tTable.table_");
  std::stringstream string_field("");
  for (int i = 0; i < parameters.transaction_table_num_entries; i++) {
    string_field.str("");
    string_field << string_table << i << "_valid";
    if (std::stoi(get_dat_by_name(string_field.str())->get_value().erase(0,2))) {
      return 1;
    }
  }
  return 0;
}

int t_Top::get_cycles() {
  return cycle;
}

int t_Top::run_single(transaction * t,
               bool debug = false, int cycle_limit = 0) {
  if (debug) info();
  // Initiate a new request. [TODO] The output is passed along to grab
  // the TID. Currently the TID response happens in the same cycle,
  // which may be too aggressive.
  new_write_request(t->nnid, &t->outputs, debug);
  t->tid = t->outputs[0];
  std::cout << "[INFO] X-FILES responded with TID: 0x" << std::hex << t->tid
            << std::endl;
  t->outputs.pop_back();
  // Once we have a TID, we can start writing data
  for (unsigned int i = 0; i < t->inputs.size(); i++)
    write_data(t->tid, t->get_input(), t->done_in(), debug);
  while (!any_done()) {
    tick();
    if (debug) info();
    if (cycle_limit && get_cycles() > cycle_limit) {
      std::cout << "[ERROR] Hit " << cycle_limit
                << " cycle count limit, bailing..." << std::endl;
      return -1;
    }
  }
  // We need to add an extra tick to allow the data to get into the IO
  // storage.
  tick();
  while (any_valid()) {
    if (debug) info();
    new_read_request(t->tid, &t->outputs);
    // [TODO] This is kludgy as I'm forcing a slower response rate
    // than *should* be allowable.
    tick(2, 0, &t->outputs);
  }
  return 0;
}

int t_Top::testbench_fann(uint16_t tid, uint32_t nnid,
                           const char * file_net, const char * file_train,
                           bool debug = false) {
  struct fann *ann = NULL;
  struct fann_train_data *data = NULL;
  fann_type * output_fann;
  std::vector<int32_t> input_dana, output_dana;
  int i, j;
  int decimal_point, total_bit_failures, total_outputs;
  int output_fann_th, output_dana_th;
  double error, error_mean, error_mse;
  uint64_t cycle_start, cycle_stop, edges;
  std::vector<transaction*> transactions;

  if ((ann = fann_create_from_file(file_net)) == 0) goto failure;
  if ((data = fann_read_train_from_file(file_train)) == 0) goto failure;

  input_dana.resize(data->num_input);

  decimal_point = fann_save_to_fixed(ann, "/dev/null");

  error_mean = 0.0;
  error_mse = 0.0;
  total_outputs = 0;
  total_bit_failures = 0;
  edges = 0;
  cycle_start = cycle;

  for (i = 0; i < data->num_data; i++)
    transactions.push_back(new transaction(ann, data->input[i], nnid, decimal_point));

  for (i = 0; i < data->num_data; i++) {
    output_dana.clear();
    for (j = 0; j < data->num_input; j++) {
      input_dana[j] = ((int32_t) data->input[i][j] << decimal_point);
    }
    output_fann = fann_run(ann, data->input[i]);
    if (run_single(transactions[i], debug))
      goto failure;
    // std::cout << "[INFO] FANN vs. DANA" << std::endl;
    for (j = 0; j < data->num_output; j++) {
      total_outputs++;
      error = output_fann[j] -
        ((fann_type) transactions[i]->outputs[j] / pow(2.0, decimal_point));
      error_mean += error;
      error_mse += error * error;
      if (fabs(error) > 0.1) {
        printf("[INFO] ABS Err > 0.1 (%f) on [%d, %d], found %d (%f), should be %f\n",
               fabs(error), i, j,
               transactions[i]->outputs[j],
               (float) transactions[i]->outputs[j] / pow(2.0, decimal_point),
               output_fann[j]);
        // Check to see if this results in a bit flip
        output_fann_th = output_fann[j] > 0.5 ? 1 : 0;
        output_dana_th = transactions[i]->outputs[j] > (1 << (decimal_point - 1)) ? 1 : 0;
        if (output_fann_th != output_dana_th) {
          std::cout << "[ERROR] This results in a bit flip!" << std::endl;
          total_bit_failures++;
        }
      }
    }
    edges += ann->total_connections;
  }
  cycle_stop = cycle;

  printf("[INFO] Outputs tested: %d\n", total_outputs);
  printf("[INFO] Total cycles: %d\n", cycle_stop - cycle_start);
  printf("[INFO] Mean error: %0.5f\n",
         error_mean / (fann_type) total_outputs);
  printf("[INFO] Mean squared error: %0.5f\n",
         error_mse / (fann_type) total_outputs);
  printf("[INFO] Total bit failures: %d\n", total_bit_failures);
  printf("[INFO] Throughput: %0.2f edges/cycle\n",
         (double) edges / (cycle_stop - cycle_start));

  for (i = 0; i < data->num_data; i++)
    delete transactions[i];

  fann_destroy(ann);
  fann_destroy_train(data);
  return 0;

 failure:
  if (data != NULL) fann_destroy_train(data);
  if (ann != NULL) fann_destroy(ann);
  return 1;
}

int t_Top::testbench(std::vector<transaction *> * transactions) {
  return 0;
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
  // t_Top* api = new t_Top("build/t_Top.vcd");
  t_Top * api;
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

  // Run the constructor for t_Top specifying a vcd file if we have
  // one
  if (has_vcd) api = new t_Top(file_vcd);
  else api = new t_Top();

  // Apply a multi-cycle reset
  api->reset(8);

  // Load the parameters
  api->read_parameters(file_parameters);
  // No tee file is used, currently
  FILE *tee = NULL;
  api->set_teefile(tee);

  // Preload the cache
  api->cache_load(0, 17, "../workloads/data/sobel-fixed", debug);
  api->cache_load(1, 18, "../workloads/data/rsa-fixed", debug);

  // Set the ASID
  api->set_asid(0xbeef);

  // Run the simulation
  api->testbench_fann(1, 18, "../workloads/data/rsa.net",
                      "../workloads/data/rsa.train.1", debug);

  if (tee) fclose(tee);
  return 0;
}
