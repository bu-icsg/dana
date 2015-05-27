#include <iomanip>
#include "fann.h"

class t_Dana : public Dana_api_t {
private:
  uint64_t cycle;
  bool vcd_flag;
  FILE * vcd;
  Dana_t * dana;
  struct {
    uint64_t num_pes;
    uint64_t cache_num_entries;
    uint64_t elements_per_block;
    uint64_t transaction_table_num_entries;
  } parameters;

public:
  // Constructors
  t_Dana();
  t_Dana(const string);
  // Destructor
  ~t_Dana();

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
  void new_write_request(uint16_t, uint32_t, bool);

  // Write one unit of data
  void write_data(int, int32_t, int, bool);

  // Finalize a write request by sending random data to the
  // accelerator
  void write_rnd_data(int, int, int);

  // Read one unit of data out of Dana for a specific TID
  void new_read_request(int);

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
  int testbench_fann(uint16_t, uint32_t, uint16_t, const char *,
                     const char *, bool);

  // Run the C++ Chisel model on a set of inputs
  int run(uint16_t, uint32_t, uint16_t, std::vector<int32_t> *,
          std::vector<int32_t> *, bool, int);

  // Read a parameter file and populate the local parameters
  int read_parameters(const string);
};

int t_Dana::read_parameters(const string file_string_parameters) {
  std::string line, key, value;
  int pos_del, pos_eol;
  std::ifstream file_params(file_string_parameters, std::ifstream::in);

  while (std::getline(file_params, line)) {
    pos_del = line.find(",");
    pos_eol = line.find(")");
    key = line.substr(1, pos_del - 1);
    value = line.substr(pos_del + 1, pos_eol - pos_del - 1);
    if (key.compare("NUM_PES") == 0) {
      parameters.num_pes = stoi(value);
    }
    else if (key.compare("CACHE_NUM_ENTRIES") == 0) {
      parameters.cache_num_entries = stoi(value);
    }
    else if (key.compare("ELEMENTS_PER_BLOCK") == 0) {
      parameters.elements_per_block = stoi(value);
    }
    else if (key.compare("TRANSACTION_TABLE_NUM_ENTRIES") == 0) {
      parameters.transaction_table_num_entries = stoi(value);
    }
    else {
      std::cout << "[ERROR] Unknown parameter key (" << key << ") found" << std::endl;
    }
    std::cout << "[INFO] Found parameter: " << key << " -> " << value << std::endl;
  }

  file_params.close();
  return 0;
}

t_Dana::t_Dana() {
  dana = new Dana_t();
  dana->init();
  init(dana);
  cycle = 0;
  vcd_flag = false;
  std::cout << "[INFO] No vcd file output specified" << std::endl;
}

t_Dana::t_Dana(const string file_string_vcd) {
  dana = new Dana_t();
  dana->init();
  init(dana);
  cycle = 0;
  vcd_flag = true;
  vcd = fopen(file_string_vcd.c_str(), "w");
  assert(vcd);
  std::cout << "[INFO] Using vcd file:\n[INFO]   " << file_string_vcd << std::endl;
  dana->set_dumpfile(vcd);
}

t_Dana::~t_Dana() {
  if (vcd_flag)
    fclose(vcd);
}

void t_Dana::tick_lo(int reset) {
  dana->clock_lo(dat_t<1>(reset));
}

void t_Dana::tick_hi(int reset) {
  if (vcd_flag)
    dana->dump(vcd, cycle);
  dana->clock_hi(dat_t<1>(reset));
  cycle++;
}

int t_Dana::tick(int num_cycles = 1, int reset = 0,
                 std::vector<int32_t> * output = NULL) {
  int responses_seen = 0;
  for (int i = 0; i < num_cycles; i++) {
    tick_lo(reset);
    tick_hi(reset);
    if (dana->Dana__io_arbiter_resp_valid == 1) {
      if (output != NULL) {
        output->push_back(std::stoi(get_dat_by_name("Dana.io_arbiter_resp_bits_data")->get_value().erase(0,2), NULL, 16));
      }
      else {
        std::cout << "[INFO] Saw response... Tid:";
        std::cout << std::stoi(get_dat_by_name("Dana.io_arbiter_resp_bits_tid")->get_value().erase(0,2), NULL, 16);
        std::cout << " Output:";
        std::cout << std::stoi(get_dat_by_name("Dana.io_arbiter_resp_bits_data")->get_value().erase(0,2), NULL, 16);
        std::cout << std::endl;
      }
      responses_seen++;
    }
  }
  return responses_seen;
}

void t_Dana::reset(int num_cycles) {
  for(int i = 0; i < num_cycles; i++) {
    tick(1,1);
  }
}

void t_Dana::new_write_request(uint16_t tid, uint32_t nnid, bool debug = false) {
  dana->Dana__io_arbiter_req_valid = 1;
  dana->Dana__io_arbiter_req_bits_isNew = 1;
  dana->Dana__io_arbiter_req_bits_readOrWrite = 1;
  dana->Dana__io_arbiter_req_bits_isLast = 0;
  dana->Dana__io_arbiter_req_bits_tid = tid;
  dana->Dana__io_arbiter_req_bits_data = nnid;
  tick(1,0);
  if (debug) info();
  dana->Dana__io_arbiter_req_valid = 0;
  dana->Dana__io_arbiter_req_bits_isNew = 0;
  dana->Dana__io_arbiter_req_bits_readOrWrite = 0;
  dana->Dana__io_arbiter_req_bits_isLast = 0;
  dana->Dana__io_arbiter_req_bits_tid = 0;
  dana->Dana__io_arbiter_req_bits_data = 0;
}

void t_Dana::write_data(int tid, int32_t data, int is_last, bool debug = false) {
  dana->Dana__io_arbiter_req_valid = 1;
  dana->Dana__io_arbiter_req_bits_isNew = 0;
  dana->Dana__io_arbiter_req_bits_tid = tid;
  dana->Dana__io_arbiter_req_bits_readOrWrite = 1;
  dana->Dana__io_arbiter_req_bits_isLast = is_last;
  dana->Dana__io_arbiter_req_bits_data = data;
  tick(1,0);
  if (debug) info();
  dana->Dana__io_arbiter_req_valid = 0;
  dana->Dana__io_arbiter_req_bits_tid = tid;
  dana->Dana__io_arbiter_req_bits_isLast = 0;
  dana->Dana__io_arbiter_req_bits_readOrWrite = 0;
  dana->Dana__io_arbiter_req_bits_data = 0;
}

void t_Dana::write_rnd_data(int tid, int num, int decimal) {
  for (int i = 0; i < num; i++)
    write_data(tid, 256, (i == num - 1));
}

void t_Dana::new_read_request(int tid) {
  dana->Dana__io_arbiter_req_valid = 1;
  dana->Dana__io_arbiter_req_bits_isNew = 0;
  dana->Dana__io_arbiter_req_bits_readOrWrite = 0;
  dana->Dana__io_arbiter_req_bits_isLast = 0;
  dana->Dana__io_arbiter_req_bits_tid = tid;
  dana->Dana__io_arbiter_req_bits_data = 0;
  tick(1,0);
  dana->Dana__io_arbiter_req_valid = 0;
  dana->Dana__io_arbiter_req_bits_isLast = 0;
}

void t_Dana::info() {
  std::cout << "[INFO] Dumping tables at cycle " << cycle << std::endl;
  info_ttable();
  info_cache_table();
  info_petable();
  info_reg_file();
}

void t_Dana::info_ttable() {
  std::cout << "-----------------------------------------------------------------------------------\n";
  std::cout << "|V|R|W|CV|F?|L?|NL|NR|D| Tid|Nnid|  #L|  #N|  CL|  CN|CNinL|#NcL|#NnL| &N|Cache|DP| <- TTable\n";
  std::cout << "-----------------------------------------------------------------------------------\n";
  std::string string_table("Dana.tTable.table_");
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

void t_Dana::info_cache_table() {
  std::cout << "---------------------------\n";
  std::cout << "|V|N|F|NIdx|NMask|Nnid|IUC| <- Cache Table\n";
  std::cout << "---------------------------\n";
  std::string string_table("Dana.cache.table_");
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

void t_Dana::info_petable() {
  std::cout << "-------------------------------------------------------------------------------------------------------------\n";
  std::cout << "|S|IV|WV| TID|tIdx|CIdx|Node|inLoc|outLoc|InIdx|OutIdx|   &N|   &W|DP|LiL|#W|AF|S|    Bias|     Acc| DataOut| <- PE Table\n";
  std::cout << "-------------------------------------------------------------------------------------------------------------\n";
  std::string string_table("Dana.peTable.table_");
  std::string string_pe("Dana.peTable.ProcessingElement");
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
    string_field << "Dana.peTable.ProcessingElement";
    if (i > 0) string_field << "_" << i;
    string_field << ".acc";
    std::cout << "|" << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // Activation Function Output
    string_field.str("");
    string_field << "Dana.peTable.ProcessingElement";
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

void t_Dana::info_reg_file() {
  std::cout << "---------------------------------\n";
  std::cout << "|E[Wr](0)|#Wr(0)|E[Wr](1)|#Wr(1)| <- Register File\n";
  std::cout << "---------------------------------\n";
  std::string string_table("Dana.regFile.state_");
  std::stringstream string_field("");
  for (int i = 0; i < parameters.num_pes; i++) {
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

void t_Dana::cache_load(int index, uint32_t nnid, const char * file,
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
  ss << "Dana.cache.table_" << index << "_valid";
  get_dat_by_name(ss.str())->set_value("1");
  ss.str("");
  ss << "Dana.cache.table_" << index << "_nnid";
  val << nnid;
  get_dat_by_name(ss.str())->set_value(val.str());

  // Set the cache SRAM
  ifstream config;
  config.open(file + file_extension, ios::in | ios::binary);
  ss.str("");
  ss << "Dana.cache.SRAM";
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

int t_Dana::any_done () {
  std::string string_table("Dana.tTable.table_");
  std::stringstream string_field("");
  for (int i = 0; i < 4; i++) {
    string_field.str("");
    string_field << string_table << i << "_done";
    if (std::stoi(get_dat_by_name(string_field.str())->get_value().erase(0,2))) {
      return 1;
    }
  }
  return 0;
}

int t_Dana::any_valid () {
  std::string string_table("Dana.tTable.table_");
  std::stringstream string_field("");
  for (int i = 0; i < 4; i++) {
    string_field.str("");
    string_field << string_table << i << "_valid";
    if (std::stoi(get_dat_by_name(string_field.str())->get_value().erase(0,2))) {
      return 1;
    }
  }
  return 0;
}

int t_Dana::get_cycles() {
  return cycle;
}

int t_Dana::run(uint16_t tid, uint32_t nnid, uint16_t num_rounds,
                std::vector<int32_t> * inputs, std::vector<int32_t> * outputs,
                bool debug = false, int cycle_limit = 0) {
  if (debug) info();
  new_write_request(tid, nnid, debug);
  for (unsigned int i = 0; i < inputs->size(); i++)
    write_data(tid, (*inputs)[i], i == inputs->size() - 1, debug);
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
    new_read_request(tid);
    tick(1, 0, outputs);
  }
  return 0;
}

int t_Dana::testbench_fann(uint16_t tid, uint32_t nnid, uint16_t num_rounds,
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

  // [TODO] Add support for num_rounds into DANA-Chisel and into the
  // FANN checking below.
  if (num_rounds > 0)
    std::cout << "[WARN] NN Output->Input feedback not supported" << std::endl;

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

  for (i = 0; i < data->num_data; i++) {
    output_dana.clear();
    for (j = 0; j < data->num_input; j++) {
      input_dana[j] = ((int32_t) data->input[i][j] << decimal_point);
    }
    output_fann = fann_run(ann, data->input[i]);
    if (run(tid, nnid, num_rounds, &input_dana, &output_dana, debug))
      goto failure;
    // std::cout << "[INFO] FANN vs. DANA" << std::endl;
    for (j = 0; j < data->num_output; j++) {
      total_outputs++;
      error = output_fann[j] -
        ((fann_type) output_dana[j] / pow(2.0, decimal_point));
      error_mean += error;
      error_mse += error * error;
      if (fabs(error) > 0.1) {
        printf("[INFO] ABS Err > 0.1 (%f) on [%d, %d], found %d (%f), should be %f\n",
               fabs(error), i, j,
               output_dana[j], (float)output_dana[j] / pow(2.0, decimal_point),
               output_fann[j]);
        // Check to see if this results in a bit flip
        output_fann_th = output_fann[j] > 0.5 ? 1 : 0;
        output_dana_th = output_dana[j] > (1 << (decimal_point - 1)) ? 1 : 0;
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
  printf("[INFO] Mean error: %0.5f\n",
         error_mean / (fann_type) total_outputs);
  printf("[INFO] Mean squared error: %0.5f\n",
         error_mse / (fann_type) total_outputs);
  printf("[INFO] Total bit failures: %d\n", total_bit_failures);
  printf("[INFO] Throughput: %0.2f edges/cycle\n",
         (double) edges / (cycle_stop - cycle_start));

  fann_destroy(ann);
  fann_destroy_train(data);
  return 0;

 failure:
  if (data != NULL) fann_destroy_train(data);
  if (ann != NULL) fann_destroy(ann);
  return 1;
}

int t_sobel() {
  t_Dana* api = new t_Dana("build/t_Dana.vcd");
  FILE *tee = NULL;
  api->set_teefile(tee);
  // Reset
  api->reset(8);
  // Preload the cache
  api->cache_load(0, 17, "../workloads/data/sobel-fixed.16bin");
  // Run the actual tests
  api->new_write_request(1, 17);
  api->write_rnd_data(1, 10, 6);
  // Drop this into a loop until some TID in the Transaction Table is
  // done
  while (!api->any_done())
    api->tick(1, 0);
  api->tick(1,0);
  // Read out data until there aren't any valid entries
  while (api->any_valid()) {
    api->new_read_request(1);
    api->tick(1,0);
  }
  if (tee) fclose(tee);

  return 0;
}

int t_rsa() {
  t_Dana* api = new t_Dana("build/t_Dana.vcd");
  FILE *tee = NULL;
  api->set_teefile(tee);
  std::vector<int32_t> inputs;
  inputs.push_back(1024);
  inputs.push_back(1024);
  inputs.push_back(1024);
  inputs.push_back(1024);
  inputs.push_back(0);
  inputs.push_back(0);
  inputs.push_back(1024);
  inputs.push_back(1024);
  inputs.push_back(1024);
  inputs.push_back(0);
  inputs.push_back(1024);
  inputs.push_back(0);
  inputs.push_back(0);
  inputs.push_back(0);
  inputs.push_back(1024);
  inputs.push_back(0);
  inputs.push_back(1024);
  inputs.push_back(1024);
  inputs.push_back(1024);
  inputs.push_back(0);
  inputs.push_back(1024);
  inputs.push_back(0);
  inputs.push_back(0);
  inputs.push_back(0);
  inputs.push_back(0);
  inputs.push_back(0);
  inputs.push_back(0);
  inputs.push_back(0);
  inputs.push_back(0);
  inputs.push_back(0);

  // Reset
  api->reset(8);

  // Preload the cache
  api->cache_load(0, 17, "../workloads/data/sobel-fixed.16bin");
  api->cache_load(1, 18, "../workloads/data/rsa-fixed.16bin");

  // Run the actual tests
  api->info();
  api->new_write_request(1, 18);
  for (unsigned int i = 0; i < inputs.size(); i++)
    api->write_data(1, inputs[i], i == inputs.size() - 1);
  // Drop this into a loop until some TID in the Transaction Table is
  // done
  while (!api->any_done()) {
    api->tick(1, 0);
    api->info();
    if (api->get_cycles() > 10000) {
      std::cout << "[ERROR] Hit cycle count limit, bailing..." << std::endl;
      return -1;
    }
  }
  api->tick(1,0);
  // Read out data until there aren't any valid entries
  while (api->any_valid()) {
    api->new_read_request(1);
    api->tick(1,0);
  }

  if (tee) fclose(tee);
  return 0;
}

int main (int argc, char* argv[]) {
  // t_Dana* api = new t_Dana("build/t_Dana.vcd");
  t_Dana * api;

  if (argc == 3) {
    api = new t_Dana(argv[2]);
    api->read_parameters(argv[1]);
  }
  else if (argc == 2) {
    api = new t_Dana();
    api->read_parameters(argv[1]);
  }
  else
    api = new t_Dana();
  FILE *tee = NULL;
  api->set_teefile(tee);

  // Preload the cache
  api->cache_load(0, 17, "../workloads/data/sobel-fixed");
  api->cache_load(1, 18, "../workloads/data/rsa-fixed", true);

  api->testbench_fann(1, 18, 0, "../workloads/data/rsa.net",
                      "../workloads/data/rsa.train.1", true);

  if (tee) fclose(tee);
  return 0;
}
