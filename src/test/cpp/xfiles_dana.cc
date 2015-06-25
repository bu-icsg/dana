#include "xfiles_dana.h"

xfiles_dana_helper::xfiles_dana_helper() {
}

int xfiles_dana_helper::read_parameters(const string file_string_parameters) {
  std::string line, key, value;
  int pos_del, pos_eol;
  std::ifstream file_params(file_string_parameters, std::ifstream::in);
  if (!file_params.is_open()) {
    std::cout << "[ERROR] Unable to read parameter file:\n[ERROR]   "
              << file_string_parameters << std::endl;
    return -1;
  };
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
      std::cout << "[INFO] Ignoring unknown parameter key (" << key << ")" << std::endl;
    std::cout << "[INFO]     " << key << " -> " << value << std::endl;
  }

  file_params.close();
  return 0;
}

int xfiles_dana_helper::cache_load(int index, uint32_t nnid,
                                   const char * file, bool debug = false) {

  std::string cache("Top.RocketTile.XFilesDana.dana.cache");
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
  ss << cache << ".table_" << index << "_valid";
  get_dat_by_name(ss.str())->set_value("1");
  ss.str("");
  ss << cache << ".table_" << index << "_nnid";
  val << nnid;
  get_dat_by_name(ss.str())->set_value(val.str());
  ss.str("");
  ss << cache << ".table_" << index << "_inUseCount";
  get_dat_by_name(ss.str())->set_value("0");
  ss.str("");
  ss << cache << ".table_" << index << "_fetch";
  get_dat_by_name(ss.str())->set_value("0");

  // Set the cache SRAM
  ifstream config;
  config.open(file + file_extension, ios::in | ios::binary);
  if (!config.is_open()) {
    std::cout << "[ERROR] Failed to read cache file " << file << file_extension << std::endl;
    return -1;
  }
  ss.str("");
  ss << cache << ".SRAM";
  // if (index > 0) ss << "_" << index;
  ss << "_" << index;
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

  return 0;
}
