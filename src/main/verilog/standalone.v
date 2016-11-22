module top
  (input clock,
   input reset);

  wire io_cmd_ready;
  wire io_cmd_valid;
  wire [6:0] io_cmd_bits_inst_funct;
  wire [4:0] io_cmd_bits_inst_rs2;
  wire [4:0] io_cmd_bits_inst_rs1;
  wire io_cmd_bits_inst_xd;
  wire io_cmd_bits_inst_xs1;
  wire io_cmd_bits_inst_xs2;
  wire [4:0] io_cmd_bits_inst_rd;
  wire [6:0] io_cmd_bits_inst_opcode;
  wire [63:0] io_cmd_bits_rs1;
  wire [63:0] io_cmd_bits_rs2;
  wire io_cmd_bits_status_debug;
  wire [31:0] io_cmd_bits_status_isa;
  wire [1:0] io_cmd_bits_status_prv;
  wire io_cmd_bits_status_sd;
  wire [30:0] io_cmd_bits_status_zero3;
  wire io_cmd_bits_status_sd_rv32;
  wire [1:0] io_cmd_bits_status_zero2;
  wire [4:0] io_cmd_bits_status_vm;
  wire [3:0] io_cmd_bits_status_zero1;
  wire io_cmd_bits_status_mxr;
  wire io_cmd_bits_status_pum;
  wire io_cmd_bits_status_mprv;
  wire [1:0] io_cmd_bits_status_xs;
  wire [1:0] io_cmd_bits_status_fs;
  wire [1:0] io_cmd_bits_status_mpp;
  wire [1:0] io_cmd_bits_status_hpp;
  wire io_cmd_bits_status_spp;
  wire io_cmd_bits_status_mpie;
  wire io_cmd_bits_status_hpie;
  wire io_cmd_bits_status_spie;
  wire io_cmd_bits_status_upie;
  wire io_cmd_bits_status_mie;
  wire io_cmd_bits_status_hie;
  wire io_cmd_bits_status_sie;
  wire io_cmd_bits_status_uie;
  wire io_resp_ready;
  wire io_resp_valid;
  wire [4:0] io_resp_bits_rd;
  wire [63:0] io_resp_bits_dat;

  ROCC rocc
    (.clock(clock),
     .reset(reset),
     .io_cmd_ready(io_cmd_ready),
     .io_cmd_valid(io_cmd_valid),
     .io_cmd_bits_inst_funct(io_cmd_bits_inst_funct),
     .io_cmd_bits_inst_rs2(io_cmd_bits_inst_rs2),
     .io_cmd_bits_inst_rs1(io_cmd_bits_inst_rs1),
     .io_cmd_bits_inst_xd(io_cmd_bits_inst_xd),
     .io_cmd_bits_inst_xs1(io_cmd_bits_inst_xs1),
     .io_cmd_bits_inst_xs2(io_cmd_bits_inst_xs2),
     .io_cmd_bits_inst_rd(io_cmd_bits_inst_rd),
     .io_cmd_bits_inst_opcode(io_cmd_bits_inst_opcode),
     .io_cmd_bits_rs1(io_cmd_bits_rs1),
     .io_cmd_bits_rs2(io_cmd_bits_rs2),
     .io_cmd_bits_status_debug(io_cmd_bits_status_debug),
     .io_cmd_bits_status_isa(io_cmd_bits_status_isa),
     .io_cmd_bits_status_prv(io_cmd_bits_status_prv),
     .io_cmd_bits_status_sd(io_cmd_bits_status_sd),
     .io_cmd_bits_status_zero3(io_cmd_bits_status_zero3),
<     .io_cmd_bits_status_sd_rv32(io_cmd_bits_status_sd_rv32),
     .io_cmd_bits_status_zero2(io_cmd_bits_status_zero2),
     .io_cmd_bits_status_vm(io_cmd_bits_status_vm),
     .io_cmd_bits_status_zero1(io_cmd_bits_status_zero1),
     .io_cmd_bits_status_mxr(io_cmd_bits_status_mxr),
     .io_cmd_bits_status_pum(io_cmd_bits_status_pum),
     .io_cmd_bits_status_mprv(io_cmd_bits_status_mprv),
     .io_cmd_bits_status_xs(io_cmd_bits_status_xs),
     .io_cmd_bits_status_fs(io_cmd_bits_status_fs),
     .io_cmd_bits_status_mpp(io_cmd_bits_status_mpp),
     .io_cmd_bits_status_hpp(io_cmd_bits_status_hpp),
     .io_cmd_bits_status_spp(io_cmd_bits_status_spp),
     .io_cmd_bits_status_mpie(io_cmd_bits_status_mpie),
     .io_cmd_bits_status_hpie(io_cmd_bits_status_hpie),
     .io_cmd_bits_status_spie(io_cmd_bits_status_spie),
     .io_cmd_bits_status_upie(io_cmd_bits_status_upie),
     .io_cmd_bits_status_mie(io_cmd_bits_status_mie),
     .io_cmd_bits_status_hie(io_cmd_bits_status_hie),
     .io_cmd_bits_status_sie(io_cmd_bits_status_sie),
     .io_cmd_bits_status_uie(io_cmd_bits_status_uie),
     .io_resp_ready(io_resp_ready),
     .io_resp_valid(io_resp_valid),
     .io_resp_bits_rd(io_resp_bits_rd),
     .funct(funct),
     .rd(rd),
     .rs1(rs1),
     .rs2(rs2));

  function xcustom
    (input int funct,
     input int rd,
     input longint rs1,
     input longint rs2
     );
    $display("[INFO] hello_from_verilog");
    case (funct)
      // 0:;
      // 1:;
      // 2:;
      // 3:;
    endcase
    assert(funct >=0 && funct <= 4);

  endfunction
  export "DPI-C" function hello_from_verilog;
  import "DPI-C" context function void hello_from_c();
  initial begin
    $display("[INFO] In initial block");
    hello_from_c();
  end

endmodule
