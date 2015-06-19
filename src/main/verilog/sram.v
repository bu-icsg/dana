// A wrapper for ram_infer
`include "ram_infer.v"

module sram
  #(parameter
    WIDTH = 8,
    DEPTH = 64,
    LG_DEPTH = 6,
    INIT_VAL = '0
    )
  (input clk,
   input [WIDTH - 1:0] io_din_1,
   input [WIDTH - 1:0] io_din_0,
   output [WIDTH - 1:0] io_dout_1,
   output [WIDTH - 1:0] io_dout_0,
   output [LG_DEPTH - 1:0] io_addr_1,
   output [LG_DEPTH - 1:0] io_addr_0,
   input io_we_1,
   input io_we_0
   );

  ram_infer
    #(.WIDTH(WIDTH),
      .DEPTH(DEPTH),
      .LG_DEPTH(LG_DEPTH),
      .INIT_VAL(INIT_VAL)
      )
  u_ram_infer
    (.clka(clk),
     .clkb(clk),
     .wea(io_we_0),
     .web(io_we_1),
     .ena(1'b1),
     .enb(1'b1),
     .addra(io_addr_0),
     .addrb(io_addr_1),
     .dina(io_din_0),
     .dinb(io_din_1),
     .douta(io_dout_0),
     .doutb(io_dout_1)
     );

endmodule
