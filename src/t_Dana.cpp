#include <iomanip>
#include "Dana.h"

class t_Dana : public Dana_api_t {
private:
  uint64_t cycle;
  bool vcd_flag;
  FILE * vcd;
  Dana_t * dana;

public:
  // Constructors
  t_Dana();
  t_Dana(const string);
  // Destructor
  ~t_Dana();

  // Drive the clock low
  int tick_lo(int reset);

  // Drive the clock high and update the vcd file if the vcd flag is
  // set
  int tick_hi(int reset);

  // Tick the clock for a specified number of cycles without changing
  // the inputs
  int tick(int num_cycles, int reset);

  // Apply the reset for a specified number of cycles
  int reset(int num_cycles);

  // Initiate a new write request with the accelerator
  int new_write_request(int tid, int nnid);

  // Finalize a write request by sending random data to the
  // accelerator
  int write_rnd_data(int tid, int nnid, int num, int decimal);

  // Print out information about the state of all modules in the
  // system
  int info();

  // Print out information about the Transaction Table
  int info_ttable();

  // Print out information about the Cache Table
  int info_cache_table();

  // Load the cache so that memory requests aren't necessary
  int cache_load(int index, int nnid, const char *);
};

t_Dana::t_Dana() {
  dana = new Dana_t();
  dana->init();
  init(dana);
  cycle = 0;
  vcd_flag = false;
}

t_Dana::t_Dana(const string file_string_vcd) {
  dana = new Dana_t();
  dana->init();
  init(dana);
  cycle = 0;
  vcd_flag = true;
  vcd = fopen(file_string_vcd.c_str(), "w");
  assert(vcd);
  dana->set_dumpfile(vcd);
}

t_Dana::~t_Dana() {
  if (vcd_flag)
    fclose(vcd);
}

int t_Dana::tick_lo(int reset) {
  dana->clock_lo(dat_t<1>(reset));
}

int t_Dana::tick_hi(int reset) {
  if (vcd_flag)
    dana->dump(vcd, cycle);
  dana->clock_hi(dat_t<1>(reset));
  cycle++;
}

int t_Dana::tick(int num_cycles, int reset) {
  for (int i = 0; i < num_cycles; i++) {
    tick_lo(reset);
    tick_hi(reset);
  }
}

int t_Dana::reset(int num_cycles) {
  for(int i = 0; i < num_cycles; i++) {
    tick_lo(1);
    tick_hi(1);
    tick_lo(0);
  }
}

int t_Dana::new_write_request(int tid, int nnid) {
  tick_lo(0);
  dana->Dana__io_arbiter_req_valid = 1;
  dana->Dana__io_arbiter_req_bits_isNew = 1;
  dana->Dana__io_arbiter_req_bits_readOrWrite = 1;
  dana->Dana__io_arbiter_req_bits_isLast = 0;
  dana->Dana__io_arbiter_req_bits_tid = tid;
  dana->Dana__io_arbiter_req_bits_data = nnid;
  tick_hi(0);
  return 1;
}

int t_Dana::write_rnd_data(int tid, int nnid, int num, int decimal) {
  for (int i = 0; i < num; i++) {
    tick_lo(0);
    dana->Dana__io_arbiter_req_valid = 1;
    dana->Dana__io_arbiter_req_bits_isNew = 0;
    dana->Dana__io_arbiter_req_bits_readOrWrite = 1;
    dana->Dana__io_arbiter_req_bits_isLast = (i == num - 1);
    dana->Dana__io_arbiter_req_bits_data = i;
    tick_hi(0);
  }
  return 1;
}

int t_Dana::info() {
  std::cout << "[INFO] Dumping tables at cycle " << cycle << std::endl;
  info_ttable();
  info_cache_table();
}

int t_Dana::info_ttable() {
  std::cout << "-----------------------------\n";
  std::cout << "|V|R|CV|WC|NL|NR|D| Tid|Nnid| <- TTable\n";
  std::cout << "-----------------------------\n";
  std::string string_table("Dana.tTable.table_");
  std::stringstream string_field("");
  for (int i = 0; i < 4; i++) { // [TODO] fragile, should be transactionTableNumEntries
    // Valid
    string_field.str("");
    string_field << string_table << i << "_valid";
    std::cout << "|" << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // Reserved
    string_field.str("");
    string_field << string_table << i << "_reserved";
    std::cout << "|" << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // Cache Valid
    string_field.str("");
    string_field << string_table << i << "_cacheValid";
    std::cout << "| " << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // Waiting For Cache
    string_field.str("");
    string_field << string_table << i << "_waitingForCache";
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
    std::cout << "| ";
    // TID
    string_field.str("");
    string_field << string_table << i << "_tid";
    std::cout << "|" << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    // NNID
    string_field.str("");
    string_field << string_table << i << "_nnid";
    std::cout << "|" << get_dat_by_name(string_field.str())->get_value().erase(0,2);
    std::cout << "|" << std::endl;
  }
  std::cout << std::endl;
}

int t_Dana::info_cache_table() {
  std::cout << "---------------------------\n";
  std::cout << "|V|N|F|NIdx|NMask|Nnid|IUC| <- Cache Table\n";
  std::cout << "---------------------------\n";
  std::string string_table("Dana.cache.table_");
  std::stringstream string_field("");
  for (int i = 0; i < 4; i++) { // [TODO] fragile, should be transactionTableNumEntries
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

int t_Dana::cache_load(int index, int nnid, const char * file) {
  std::stringstream ss("");
  std::stringstream val("");
  char buf [16];
  int i;

  // Set the cache table
  ss << "Dana.cache.table_" << index << "_valid";
  get_dat_by_name(ss.str())->set_value("1");
  ss.str("");
  ss << "Dana.cache.table_" << index << "_nnid";
  val << nnid;
  get_dat_by_name(ss.str())->set_value(val.str());

  // Set the cache SRAM
  ifstream config;
  config.open(file, ios::in | ios::binary);
  ss.str("");
  ss << "Dana.cache.SRAM";
  if (index > 0) ss << "_" << index;
  ss << ".mem";
  i = 0;
  // Go through the whole file and dump the data into the SRAM
  while (!config.eof()) {
    // The number of characters to read
    config.read(buf, 16);
    // std::cout << i << ":" << std::hex << std::setfill('0') << std::setw(2) << buf << std::endl;
    std::cout << i << ":";
    for (int j = 0; j < 16; j++)
      printf("%02x", (const unsigned char) buf[j]);
    printf("\n");
    i += 16;
  }
  // get_mem_by_name(ss)->set_value(,);
  config.close();
}

int main (int argc, char* argv[]) {
  t_Dana* api = new t_Dana("build/t_Dana.vcd");
  FILE *tee = NULL;
  api->set_teefile(tee);

  // Reset
  api->reset(8);

  // Preload the cache
  api->cache_load(0, 17, "../workloads/data/sobel-fixed.16bin");

  // Run the actual tests
  api->info();
  api->new_write_request(1, 17);
  api->info();
  api->write_rnd_data(1, 17, 10, 6);
  api->info();
  api->tick(1, 0);
  api->info();
  api->tick(1, 0);
  api->info();
  api->tick(10, 0);
  // api->new_write_request(2, 18);
  // api->write_rnd_data(2, 18, 2, 6);
  // api->tick(10, 0);
  // api->new_write_request(3, 19);
  // api->write_rnd_data(3, 19, 4, 6);
  // api->tick(10, 0);
  // api->new_write_request(4, 20);
  // api->write_rnd_data(4, 20, 6, 6);
  // api->tick(10, 0);
  if (tee) fclose(tee);
}
