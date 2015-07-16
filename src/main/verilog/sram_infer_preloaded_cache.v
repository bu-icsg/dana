// A wrapper for ram_infer_preloaded_cache
`include "ram_infer_preloaded_cache.v"

module sram_infer_preloaded_cache
  #(parameter
    WIDTH = 8,
    DEPTH = 64,
    LG_DEPTH = 6,
    INIT_SWITCH = 0,
    ELEMENTS_PER_BLOCK = 4
    )
  (input clk,
   input [WIDTH - 1:0] io_din_1,
   input [WIDTH - 1:0] io_din_0,
   output [WIDTH - 1:0] io_dout_1,
   output [WIDTH - 1:0] io_dout_0,
   input [LG_DEPTH - 1:0] io_addr_1,
   input [LG_DEPTH - 1:0] io_addr_0,
   input io_we_1,
   input io_we_0
   );

  ram_infer_preloaded_cache
    #(.WIDTH(WIDTH),
      .DEPTH(DEPTH),
      .LG_DEPTH(LG_DEPTH),
      .INIT_SWITCH(INIT_SWITCH),
      .ELEMENTS_PER_BLOCK(ELEMENTS_PER_BLOCK)
      )
  u_ram_infer_preloaded_cache
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
