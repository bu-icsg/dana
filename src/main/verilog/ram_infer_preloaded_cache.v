// See LICENSE.BU for license details.

module ram_infer_preloaded_cache
  #(
    parameter
    WIDTH = 8,
    DEPTH = 64,
    LG_DEPTH = 6,
    INIT_SWITCH = 0,
    ELEMENTS_PER_BLOCK = 4
    )
  (
   input                  clka, clkb, wea, web, ena, enb,
   input [LG_DEPTH-1:0]   addra, addrb,
   input [WIDTH-1:0]      dina, dinb,
   output reg [WIDTH-1:0] douta, doutb
   );

  reg [WIDTH-1:0]         ram [DEPTH-1:0];
  reg [WIDTH-1:0]         doa, dob;

  // Initialize the cache based on the the available initial blocks
  generate
    case (ELEMENTS_PER_BLOCK)
`ifndef RAM_INFER_OVERRIDE
      4: begin
        case (INIT_SWITCH)
          0:
`include "initial/net0-16.v"
          1:
`include "initial/net1-16.v"
          2:
`include "initial/net2-16.v"
          3:
`include "initial/net3-16.v"
          4:
`include "initial/net4-16.v"
	  5:
`include "initial/net5-16.v"
        endcase
      end
      8: begin
        case (INIT_SWITCH)
          0:
`include "initial/net0-32.v"
          1:
`include "initial/net1-32.v"
          2:
`include "initial/net2-32.v"
          3:
`include "initial/net3-32.v"
          4:
`include "initial/net4-32.v"
	  5:
`include "initial/net5-32.v"
        endcase
      end
      16: begin
        case (INIT_SWITCH)
          0:
`include "initial/net0-64.v"
          1:
`include "initial/net1-64.v"
          2:
`include "initial/net2-64.v"
          3:
`include "initial/net3-64.v"
          4:
`include "initial/net4-64.v"
	  5:
`include "initial/net5-64.v"
        endcase
      end
      32: begin
        case (INIT_SWITCH)
          0:
`include "initial/net0-128.v"
          1:
`include "initial/net1-128.v"
          2:
`include "initial/net2-128.v"
          3:
`include "initial/net3-128.v"
          4:
`include "initial/net4-128.v"
	  5:
`include "initial/net5-128.v"
        endcase
      end
`else // !`ifndef RAM_INFER_OVERRIDE
      4: begin
        case (INIT_SWITCH)
	  0:
`include "entry_0-16.v"
          1:
`include "entry_1-16.v"
          2:
`include "entry_2-16.v"
          3:
`include "entry_3-16.v"
          4:
`include "entry_4-16.v"
	  5:
`include "entry_5-16.v"
        endcase
      end
      8: begin
        case (INIT_SWITCH)
	  0:
`include "entry_0-32.v"
          1:
`include "entry_1-32.v"
          2:
`include "entry_2-32.v"
          3:
`include "entry_3-32.v"
          4:
`include "entry_4-32.v"
	  5:
`include "entry_5-32.v"
        endcase
      end
      16: begin
        case (INIT_SWITCH)
	  0:
`include "entry_0-64.v"
          1:
`include "entry_1-64.v"
          2:
`include "entry_2-64.v"
          3:
`include "entry_3-64.v"
          4:
`include "entry_4-64.v"
	  5:
`include "entry_5-64.v"
        endcase
      end
      32: begin
        case (INIT_SWITCH)
	  0:
`include "entry_0-128.v"
          1:
`include "entry_1-128.v"
          2:
`include "entry_2-128.v"
          3:
`include "entry_3-128.v"
          4:
`include "entry_4-128.v"
	  5:
`include "entry_5-128.v"
	endcase
      end
`endif // !`ifndef RAM_INFER_OVERRIDE
    endcase
  endgenerate

  always @(posedge clka) begin
    if (ena) begin
      if (wea)
        ram[addra] <= dina;
      douta <= ram[addra];
    end
  end

  always @(posedge clkb) begin
    if (enb) begin
      if (web)
        ram[addrb] <= dinb;
      doutb <= ram[addrb];
    end
  end

endmodule
