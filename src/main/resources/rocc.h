// See LICENSE for license details.

#ifndef SRC_MAIN_RESOURCES_ROCC_H_
#define SRC_MAIN_RESOURCES_ROCC_H_

#include <vpi_user.h>
#include "src/main/resources/xcustom.h"

struct roccInstruction {
  vpiHandle funct;
  vpiHandle rs2;
  vpiHandle rs1;
  vpiHandle xd;
  vpiHandle xs1;
  vpiHandle xs2;
  vpiHandle rd;
  vpiHandle opcode;
};

struct roccCommand {
  vpiHandle valid;
  vpiHandle ready;
  struct {
    roccInstruction inst;
    vpiHandle rs1;
    vpiHandle rs2;
    vpiHandle status; // [TODO] Populate this
  } bits;
};

struct roccResponse {
  vpiHandle valid;
  vpiHandle ready;
  struct {
    vpiHandle rd;
    vpiHandle data;
  } bits;
};

struct roccInterface {
  roccCommand cmd;
  roccResponse resp;
};

class Rocc {
 private:
  roccInterface io;

 public:
  Rocc();
  vpiHandle Attach(const char * name);
  void Drive(vpiHandle vh, uint64_t value);
  void Cmd(roccCmd cmd);
  roccResponse Resp();
};

#endif  // SRC_MAIN_RESOURCES_ROCC_H_
