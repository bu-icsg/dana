// See LICENSE.BU for license details.

// A wrapper for ram_infer
`include "ram_infer.v"

module sram_r1_w1_rw0
  #(parameter
    WIDTH = 8,
    DEPTH = 64,
    LG_DEPTH = 6,
    INIT_VAL = 0
    )
  (input clk,
   input [WIDTH - 1:0] io_dinW_0,
   output [WIDTH - 1:0] io_doutR_0,
   input [LG_DEPTH - 1:0] io_addrR_0,
   input [LG_DEPTH - 1:0] io_addrW_0,
   input io_weW_0
   );

  // A is the write port, B is the read port
  ram_infer
    #(.WIDTH(WIDTH),
      .DEPTH(DEPTH),
      .LG_DEPTH(LG_DEPTH),
      .INIT_VAL(INIT_VAL)
      )
  u_ram_infer
    (.clka(clk),
     .clkb(clk),
     .wea(io_weW_0),
     .web(1'b0),
     .ena(1'b1),
     .enb(1'b1),
     .addra(io_addrW_0),
     .addrb(io_addrR_0),
     .dina(io_dinW_0),
     .dinb(),
     .douta(),
     .doutb(io_doutR_0)
     );

endmodule
