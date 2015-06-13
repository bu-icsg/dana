`timescale 1ns/1ps
`include "Top.DefaultConfig.v"

module t_top;
`define PERIOD_TARGET 3.0
`define SLACK 0
`define PERIOD (`PERIOD_TARGET-(`SLACK))
`define HALF_PERIOD (`PERIOD/2)
`define CQ_DELAY 0.100

  logic clk, rst;
  int clk_count;

  // cmd
  wire io_0_cmd_ready;
  logic io_0_cmd_valid;
  logic [6:0] io_0_cmd_bits_inst_funct;
  logic [4:0] io_0_cmd_bits_inst_rs2;
  logic [4:0] io_0_cmd_bits_inst_rs1;
  logic io_0_cmd_bits_inst_xd;
  logic io_0_cmd_bits_inst_xs1;
  logic io_0_cmd_bits_inst_xs2;
  logic [4:0] io_0_cmd_bits_inst_rd;
  logic [6:0] io_0_cmd_bits_inst_opcode;
  logic [63:0] io_0_cmd_bits_rs1;
  logic [63:0] io_0_cmd_bits_rs2;

  // resp
  logic io_0_resp_ready;
  logic io_0_resp_valid;
  logic [4:0] io_0_resp_bits_rd;
  logic [63:0] io_0_resp_bits_data;
  logic io_0_busy;
  logic io_0_s;
  logic io_0_interrupt;

  // rocc io_0, io_1;

  task info;
    info_ttable();
  endtask

  task info_ttable;
    $display("-----");
    $display("|V|R|W|CV|F?|L?|NL|-C|D|ASID| Tid|Nnid|  #L|  #N|  CL|  CN|CNinL|#NcL|#NnL|idxE|#PeW|RidX| &N|Cache|DP| <- TTable");
    $display("|%d|%d|%d|%2d|%2d|%2d|%2d|%2d|%d|%4x|",
             u_top.xFilesArbiter.tTable.table__0_valid,
             u_top.xFilesArbiter.tTable.table__0_reserved,
             u_top.xFilesArbiter.tTable.table__0_waiting,
             u_top.xFilesArbiter.tTable.table__0_cacheValid,
             u_top.xFilesArbiter.tTable.table__0_inFirst,
             u_top.xFilesArbiter.tTable.table__0_inLast,
             u_top.xFilesArbiter.tTable.table__0_needsLayerInfo,
             u_top.xFilesArbiter.tTable.table__0_decInUse,
             u_top.xFilesArbiter.tTable.table__0_done,
             u_top.xFilesArbiter.tTable.table__0_asid);
    $display("|%d|%d|",
             u_top.xFilesArbiter.tTable.table__1_valid,
             u_top.xFilesArbiter.tTable.table__1_reserved);
    $display("|%d|%d|",
             u_top.xFilesArbiter.tTable.table__2_valid,
             u_top.xFilesArbiter.tTable.table__2_reserved);
    $display("|%d|%d|",
             u_top.xFilesArbiter.tTable.table__3_valid,
             u_top.xFilesArbiter.tTable.table__3_reserved);
    $display("-----");
  endtask

  initial begin
    clk = 0;
    rst = 0;
    clk_count = 0;
    io_0_cmd_valid = 0;
    io_0_cmd_bits_inst_funct = 0;
    io_0_cmd_bits_inst_rs2 = 0;
    io_0_cmd_bits_inst_rs1 = 0;
    io_0_cmd_bits_inst_xd = 0;
    io_0_cmd_bits_inst_xs1 = 0;
    io_0_cmd_bits_inst_xs2 = 0;
    io_0_cmd_bits_inst_rd = 0;
    io_0_cmd_bits_inst_opcode = 0;
    io_0_cmd_bits_rs1 = 0;
    io_0_cmd_bits_rs2 = 0;
    io_0_resp_ready = 0;
    io_0_s = 0;
    #(`HALF_PERIOD * 100) rst = 1; $display("[INFO] Reset de-asserted");
    // Set the ASID
    // New Write Request

    // Write Data

    // Wait until done

    // Read Data
  end

  always #`HALF_PERIOD begin
    if (clk) begin
      info();
      clk_count++;
    end
    clk = ~clk;
  end

  Top u_top
    (.clk(clk),
     .reset(!rst),
     // Core 1
     .io_arbiter_1_cmd_ready(), // output
     .io_arbiter_1_cmd_valid('0), // input
     .io_arbiter_1_cmd_bits_inst_funct('0), // input [6:0]
     .io_arbiter_1_cmd_bits_inst_rs2('0), // input [4:0]
     .io_arbiter_1_cmd_bits_inst_rs1('0), // input [4:0]
     .io_arbiter_1_cmd_bits_inst_xd('0), // input
     .io_arbiter_1_cmd_bits_inst_xs1('0), // input
     .io_arbiter_1_cmd_bits_inst_xs2('0), // input
     .io_arbiter_1_cmd_bits_inst_rd('0), // input [4:0]
     .io_arbiter_1_cmd_bits_inst_opcode('0), // input [6:0]
     .io_arbiter_1_cmd_bits_rs1('0), // input [63:0]
     .io_arbiter_1_cmd_bits_rs2('0), // input [63:0]
     .io_arbiter_1_resp_ready('0), // input
     .io_arbiter_1_resp_valid(), // output
     .io_arbiter_1_resp_bits_rd(), // output[4:0]
     .io_arbiter_1_resp_bits_data(), // output[63:0]
     .io_arbiter_1_busy(), // output
     .io_arbiter_1_s('0), // input
     .io_arbiter_1_interrupt(), // output
     // Core 0
     .io_arbiter_0_cmd_ready(io_0_cmd_ready), // output
     .io_arbiter_0_cmd_valid(io_0_cmd_valid), // input
     .io_arbiter_0_cmd_bits_inst_funct(io_0_cmd_bits_inst_funct), // input [6:0]
     .io_arbiter_0_cmd_bits_inst_rs2(io_0_cmd_bits_inst_rs2), // input [4:0]
     .io_arbiter_0_cmd_bits_inst_rs1(io_0_cmd_bits_inst_rs1), // input [4:0]
     .io_arbiter_0_cmd_bits_inst_xd(io_0_cmd_bits_inst_xd), // input
     .io_arbiter_0_cmd_bits_inst_xs1(io_0_cmd_bits_inst_xs1), // input
     .io_arbiter_0_cmd_bits_inst_xs2(io_0_cmd_bits_inst_xs2), // input
     .io_arbiter_0_cmd_bits_inst_rd(io_0_cmd_bits_inst_rd), // input [4:0]
     .io_arbiter_0_cmd_bits_inst_opcode(io_0_cmd_bits_inst_opcode), // input [6:0]
     .io_arbiter_0_cmd_bits_rs1(io_0_cmd_bits_rs1), // input [63:0]
     .io_arbiter_0_cmd_bits_rs2(io_0_cmd_bits_rs2), // input [63:0]
     .io_arbiter_0_resp_ready(io_0_resp_ready), // input
     .io_arbiter_0_resp_valid(io_0_resp_valid), // output
     .io_arbiter_0_resp_bits_rd(io_0_resp_bits_rd), // output[4:0]
     .io_arbiter_0_resp_bits_data(io_0_resp_bits_data), // output[63:0]
     .io_arbiter_0_busy(io_0_busy), // output
     .io_arbiter_0_s(io_0_s), // input
     .io_arbiter_0_interrupt(io_0_interrupt) // output
     );

endmodule
