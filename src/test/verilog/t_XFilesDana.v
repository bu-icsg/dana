`timescale 1ns/1ps
`define RAM_INFER_OVERRIDE

`include "XFilesDana.DefaultFPGAConfig.v"
`include "sram_r1_w1_rw0.v"
`include "sram_infer_preloaded_cache.v"

module t_xfiles_dana;
`define PERIOD_TARGET 3.0
`define SLACK 0
`define PERIOD (`PERIOD_TARGET-(`SLACK))
`define HALF_PERIOD (`PERIOD/2)
`define CQ_DELAY 0.100

  logic clk, rst;
  int clk_count;
  int seed = 0;

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

  task info;
    $display("[INFO] Dumping tables at cycle %0dps", $time);
    info_ttable();
    //info_cache_table();
    //info_pe_table();
    //info_reg_file();
    info_asids();
  endtask

  task info_ttable;
    $display("------------------------------------------------------------------------------------------------------");
    $display("|V|R|W|CV|F?|L?|NL|-C|D|ASID| Tid|Nnid|  #L|  #N|  CL|  CN|CNinL|#NcL|#NnL|idxE|#PeW|RdX| &N|Cache|DP| <- TTable");
    $display("------------------------------------------------------------------------------------------------------");
    $display("|%d|%d|%d|%2d|%2d|%2d|%2d|%2d|%d|%4x|%4x|%4x|%4x|%4x|%4x|%4x|%5x|%4x|%4x|%4x|%4x|%3x|%3x|%5x|%2d|",
             u_xfiles_dana.xFilesArbiter.tTable.table__0_valid,
             u_xfiles_dana.xFilesArbiter.tTable.table__0_reserved,
             u_xfiles_dana.xFilesArbiter.tTable.table__0_waiting,
             u_xfiles_dana.xFilesArbiter.tTable.table__0_cacheValid,
             u_xfiles_dana.xFilesArbiter.tTable.table__0_inFirst,
             u_xfiles_dana.xFilesArbiter.tTable.table__0_inLast,
             u_xfiles_dana.xFilesArbiter.tTable.table__0_needsLayerInfo,
             u_xfiles_dana.xFilesArbiter.tTable.table__0_decInUse,
             u_xfiles_dana.xFilesArbiter.tTable.table__0_done,
             u_xfiles_dana.xFilesArbiter.tTable.table__0_asid,
             u_xfiles_dana.xFilesArbiter.tTable.table__0_tid,
             u_xfiles_dana.xFilesArbiter.tTable.table__0_nnid,
             u_xfiles_dana.xFilesArbiter.tTable.table__0_numLayers,
             u_xfiles_dana.xFilesArbiter.tTable.table__0_numNodes,
             u_xfiles_dana.xFilesArbiter.tTable.table__0_currentLayer,
             u_xfiles_dana.xFilesArbiter.tTable.table__0_currentNode,
             u_xfiles_dana.xFilesArbiter.tTable.table__0_currentNodeInLayer,
             u_xfiles_dana.xFilesArbiter.tTable.table__0_nodesInCurrentLayer,
             u_xfiles_dana.xFilesArbiter.tTable.table__0_nodesInNextLayer,
             u_xfiles_dana.xFilesArbiter.tTable.table__0_indexElement,
             u_xfiles_dana.xFilesArbiter.tTable.table__0_countPeWrites,
             u_xfiles_dana.xFilesArbiter.tTable.table__0_readIdx,
             u_xfiles_dana.xFilesArbiter.tTable.table__0_neuronPointer,
             u_xfiles_dana.xFilesArbiter.tTable.table__0_cacheIndex,
             u_xfiles_dana.xFilesArbiter.tTable.table__0_decimalPoint);
    $display("|%d|%d|%d|%2d|%2d|%2d|%2d|%2d|%d|%4x|",
             u_xfiles_dana.xFilesArbiter.tTable.table__1_valid,
             u_xfiles_dana.xFilesArbiter.tTable.table__1_reserved,
             u_xfiles_dana.xFilesArbiter.tTable.table__1_waiting,
             u_xfiles_dana.xFilesArbiter.tTable.table__1_cacheValid,
             u_xfiles_dana.xFilesArbiter.tTable.table__1_inFirst,
             u_xfiles_dana.xFilesArbiter.tTable.table__1_inLast,
             u_xfiles_dana.xFilesArbiter.tTable.table__1_needsLayerInfo,
             u_xfiles_dana.xFilesArbiter.tTable.table__1_decInUse,
             u_xfiles_dana.xFilesArbiter.tTable.table__1_done,
             u_xfiles_dana.xFilesArbiter.tTable.table__1_asid);
    $display("|%d|%d|%d|%2d|%2d|%2d|%2d|%2d|%d|%4x|",
             u_xfiles_dana.xFilesArbiter.tTable.table__2_valid,
             u_xfiles_dana.xFilesArbiter.tTable.table__2_reserved,
             u_xfiles_dana.xFilesArbiter.tTable.table__2_waiting,
             u_xfiles_dana.xFilesArbiter.tTable.table__2_cacheValid,
             u_xfiles_dana.xFilesArbiter.tTable.table__2_inFirst,
             u_xfiles_dana.xFilesArbiter.tTable.table__2_inLast,
             u_xfiles_dana.xFilesArbiter.tTable.table__2_needsLayerInfo,
             u_xfiles_dana.xFilesArbiter.tTable.table__2_decInUse,
             u_xfiles_dana.xFilesArbiter.tTable.table__2_done,
             u_xfiles_dana.xFilesArbiter.tTable.table__2_asid);
    $display("|%d|%d|%d|%2d|%2d|%2d|%2d|%2d|%d|%4x|",
             u_xfiles_dana.xFilesArbiter.tTable.table__3_valid,
             u_xfiles_dana.xFilesArbiter.tTable.table__3_reserved,
             u_xfiles_dana.xFilesArbiter.tTable.table__3_waiting,
             u_xfiles_dana.xFilesArbiter.tTable.table__3_cacheValid,
             u_xfiles_dana.xFilesArbiter.tTable.table__3_inFirst,
             u_xfiles_dana.xFilesArbiter.tTable.table__3_inLast,
             u_xfiles_dana.xFilesArbiter.tTable.table__3_needsLayerInfo,
             u_xfiles_dana.xFilesArbiter.tTable.table__3_decInUse,
             u_xfiles_dana.xFilesArbiter.tTable.table__3_done,
             u_xfiles_dana.xFilesArbiter.tTable.table__3_asid);
    $display("");
  endtask

  task info_asids();
    $display("------------------");
    $display("|Core|V|ASID|nTID| <- ASIDs in X-FILES arbiter");
    $display("------------------");
    $display("|%4d|%d|%4x|%4x|",
             0,
             u_xfiles_dana.xFilesArbiter.AsidUnit.asidReg_valid,
             u_xfiles_dana.xFilesArbiter.AsidUnit.asidReg_asid,
             u_xfiles_dana.xFilesArbiter.AsidUnit.asidReg_tid);
    $display("|%4d|%d|%4x|%4x|",
             1,
             u_xfiles_dana.xFilesArbiter.AsidUnit_1.asidReg_valid,
             u_xfiles_dana.xFilesArbiter.AsidUnit_1.asidReg_asid,
             u_xfiles_dana.xFilesArbiter.AsidUnit_1.asidReg_tid);
    $display("");
  endtask

  task set_asid(input [15:0] asid);
    io_0_s = 1;
    io_0_cmd_valid = 1;
    io_0_cmd_bits_rs1 = asid;
  endtask

  task new_write_request(input [31:0] nnid);
    io_0_cmd_valid = 1;
    io_0_cmd_bits_inst_funct = 1 | (1 << 1) & ~(1 << 2);
    io_0_cmd_bits_rs1 = 0;
    io_0_cmd_bits_rs2 = {'0, nnid};
  endtask

  task write_data(input [15:0] tid,
                  input [31:0] data,
                  input is_last);
    io_0_cmd_valid = 1;
    io_0_cmd_bits_inst_funct = 1 & ~(1 << 2);
    if (is_last)
      io_0_cmd_bits_inst_funct |= 1 << 2;
    io_0_cmd_bits_rs1 = tid;
    io_0_cmd_bits_rs2 = data;
  endtask

  task new_read_request(input [15:0] tid);
    io_0_cmd_valid = 1;
    io_0_cmd_bits_inst_funct = 0 & ~(1 << 1) & ~(1 << 2);
    io_0_cmd_bits_rs1 = tid;
    io_0_cmd_bits_rs2 = 0;
  endtask

  task no_inputs();
    io_0_cmd_valid = '0;
    io_0_cmd_bits_inst_funct = '0;
    io_0_cmd_bits_inst_rs2 = '0;
    io_0_cmd_bits_inst_rs1 = '0;
    io_0_cmd_bits_inst_xd = '0;
    io_0_cmd_bits_inst_xs1 = '0;
    io_0_cmd_bits_inst_xs2 = '0;
    io_0_cmd_bits_inst_rd = '0;
    io_0_cmd_bits_inst_opcode = '0;
    io_0_cmd_bits_rs1 = '0;
    io_0_cmd_bits_rs2 = '0;
    io_0_resp_ready = '0;
    io_0_s = '0;
  endtask

  task tick();
    wait(clk == 0);
    wait(clk == 1);
    no_inputs();
  endtask

  int tid, read_count, flag_read;
  logic [31:0] inputs [29:0];
  logic [31:0] outputs [29:0];

  initial begin
`ifdef DUMP_VCD
    $dumpfile(`DUMP_VCD);
    $dumpvars(0, u_xfiles_dana);
`endif

    inputs[0] = 1024;
    inputs[1] = 1024;
    inputs[2] = 1024;
    inputs[3] = 1024;
    inputs[4] = 0;
    inputs[5] = 0;
    inputs[6] = 1024;
    inputs[7] = 1024;
    inputs[8] = 1024;
    inputs[9] = 0;
    inputs[10] = 1024;
    inputs[11] = 0;
    inputs[12] = 0;
    inputs[13] = 0;
    inputs[14] = 1024;
    inputs[15] = 0;
    inputs[16] = 1024;
    inputs[17] = 1024;
    inputs[18] = 1024;
    inputs[19] = 0;
    inputs[20] = 1024;
    inputs[21] = 0;
    inputs[22] = 0;
    inputs[23] = 0;
    inputs[24] = 0;
    inputs[25] = 0;
    inputs[26] = 0;
    inputs[27] = 0;
    inputs[28] = 0;
    inputs[29] = 0;

    clk = 0;
    rst = 0;
    flag_read = 0;
    no_inputs();
    clk_count = 0;
    #(`HALF_PERIOD * 8) rst = 1; $display("[INFO] Reset de-asserted");
    // Set the ASID
    tick();
    set_asid($random(seed));
    tick();
    // New Write Request
    new_write_request(0);
    tick();
    wait(io_0_resp_valid);
    tid = io_0_resp_bits_data;
    $display("[INFO] X-FILES responded with TID: %x\n", tid);
    // Write Data
    for (int i = 0; i < 30; i++) begin
      write_data(tid, inputs[i], (i == 29));
      tick();
    end
    // Wait until done
    wait(u_xfiles_dana.xFilesArbiter.tTable.table__0_done);
    flag_read = 1;

    // Read Data
    for (int i = 0; i < 30; i++) begin
      new_read_request(tid);
      tick();
    end

    wait(read_count == 30);
    for (int i = 0; i < read_count; i++)
      $display("outputs[%2d]: %0d", i, outputs[i]);

    $finish;
  end

  always @ (posedge clk) begin
    if (flag_read && io_0_resp_valid) begin
      outputs[read_count] <= io_0_resp_bits_data[31:0];
      read_count <= read_count + 1;
    end
  end

  // initial
  //   #300 $finish;

  always #`HALF_PERIOD begin
    if (clk) begin
      info();
      clk_count++;
    end
    clk = ~clk;
  end

  XFilesDana u_xfiles_dana
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
