`include "Top.DefaultConfig.v"

module t_top;
`define PERIOD_TARGET 3.0
`define SLACK 0
`define PERIOD (`PERIOD_TARGET-(`SLACK))
`define HALF_PERIOD (`PERIOD/2)
`define CQ_DELAY 0.100

  logic clk, rst;

  struct packed {
    // cmd
    logic cmd_ready;
    logic cmd_valid;
    logic [6:0] cmd_bits_inst_funct;
    logic [4:0] cmd_bits_inst_rs2;
    logic [4:0] cmd_bits_inst_rs1;
    logic cmd_bits_inst_xd;
    logic cmd_bits_inst_xs1;
    logic cmd_bits_inst_xs2;
    logic [4:0] cmd_bits_inst_rd;
    logic [6:0] cmd_bits_inst_opcode;
    logic [63:0] cmd_bits_rs1;
    logic [63:0] cmd_bits_rs2;

    // resp
    logic resp_ready;
    logic resp_valid;
    logic [4:0] resp_bits_rd;
    logic [63:0] resp_bits_data;
    logic busy;
    logic s;
    logic interrupt;
    } io_0, io_1;

  // rocc io_0, io_1;

  // function info_ttable;
  // endfunction

  initial begin
    clk = 0;
    rst = 0;
    io_0.cmd_valid = 0;
    io_0.cmd_bits_inst_funct = 0;
    io_0.cmd_bits_inst_rs2 = 0;
    io_0.cmd_bits_inst_rs1 = 0;
    io_0.cmd_bits_inst_xd = 0;
    io_0.cmd_bits_inst_xs1 = 0;
    io_0.cmd_bits_inst_xs2 = 0;
    io_0.cmd_bits_inst_rd = 0;
    io_0.cmd_bits_inst_opcode = 0;
    io_0.cmd_bits_rs1 = 0;
    io_0.cmd_bits_rs2 = 0;
    io_0.resp_ready = 0;
    io_0.s = 0;
    #(`PERIOD * 5) rst = 1; $display("[INFO] Reset asserted");
    #(`HALF_PERIOD * 4) rst = 0; $display("[INFO] Reset de-asserted");
    // Set the ASID

    // New Write Request

    // Write Data

    // Wait until done

    // Read Data
  end

  always #`HALF_PERIOD begin
    if (clk)
      $fwrite(32'h80000001, "Tick\n");
    else
      $fwrite(32'h80000001, "Tock\n");
    clk = ~clk;
  end

  Top u_top
    (.clk(clk),
     .reset(rst),
     // Core 1
     .io_arbiter_1_cmd_ready(io_1.cmd_ready), // output
     .io_arbiter_1_cmd_valid(io_1.cmd_valid), // input
     .io_arbiter_1_cmd_bits_inst_funct(io_1.cmd_bits_inst_funct), // input [6:0]
     .io_arbiter_1_cmd_bits_inst_rs2(io_1.cmd_bits_inst_rs2), // input [4:0]
     .io_arbiter_1_cmd_bits_inst_rs1(io_1.cmd_bits_inst_rs1), // input [4:0]
     .io_arbiter_1_cmd_bits_inst_xd(io_1.cmd_bits_inst_xd), // input
     .io_arbiter_1_cmd_bits_inst_xs1(io_1.cmd_bits_inst_xs1), // input
     .io_arbiter_1_cmd_bits_inst_xs2(io_1.cmd_bits_inst_xs2), // input
     .io_arbiter_1_cmd_bits_inst_rd(io_1.cmd_bits_inst_rd), // input [4:0]
     .io_arbiter_1_cmd_bits_inst_opcode(io_1.cmd_bits_inst_opcode), // input [6:0]
     .io_arbiter_1_cmd_bits_rs1(io_1.cmd_bits_rs1), // input [63:0]
     .io_arbiter_1_cmd_bits_rs2(io_1.cmd_bits_rs2), // input [63:0]
     .io_arbiter_1_resp_ready(io_1.resp_ready), // input
     .io_arbiter_1_resp_valid(io_1.resp_valid), // output
     .io_arbiter_1_resp_bits_rd(io_1.resp_bits_rd), // output[4:0]
     .io_arbiter_1_resp_bits_data(io_1.resp_bits_data), // output[63:0]
     .io_arbiter_1_busy(io_1.busy), // output
     .io_arbiter_1_s(io_1.s), // input
     .io_arbiter_1_interrupt(io_1.interrupt), // output
     // Core 0
     .io_arbiter_0_cmd_ready(io_0.cmd_ready), // output
     .io_arbiter_0_cmd_valid(io_0.cmd_valid), // input
     .io_arbiter_0_cmd_bits_inst_funct(io_0.cmd_bits_inst_funct), // input [6:0]
     .io_arbiter_0_cmd_bits_inst_rs2(io_0.cmd_bits_inst_rs2), // input [4:0]
     .io_arbiter_0_cmd_bits_inst_rs1(io_0.cmd_bits_inst_rs1), // input [4:0]
     .io_arbiter_0_cmd_bits_inst_xd(io_0.cmd_bits_inst_xd), // input
     .io_arbiter_0_cmd_bits_inst_xs1(io_0.cmd_bits_inst_xs1), // input
     .io_arbiter_0_cmd_bits_inst_xs2(io_0.cmd_bits_inst_xs2), // input
     .io_arbiter_0_cmd_bits_inst_rd(io_0.cmd_bits_inst_rd), // input [4:0]
     .io_arbiter_0_cmd_bits_inst_opcode(io_0.cmd_bits_inst_opcode), // input [6:0]
     .io_arbiter_0_cmd_bits_rs1(io_0.cmd_bits_rs1), // input [63:0]
     .io_arbiter_0_cmd_bits_rs2(io_0.cmd_bits_rs2), // input [63:0]
     .io_arbiter_0_resp_ready(io_0.resp_ready), // input
     .io_arbiter_0_resp_valid(io_0.resp_valid), // output
     .io_arbiter_0_resp_bits_rd(io_0.resp_bits_rd), // output[4:0]
     .io_arbiter_0_resp_bits_data(io_0.resp_bits_data), // output[63:0]
     .io_arbiter_0_busy(io_0.busy), // output
     .io_arbiter_0_s(io_0.s), // input
     .io_arbiter_0_interrupt(io_0.interrupt) // output
     );

endmodule
