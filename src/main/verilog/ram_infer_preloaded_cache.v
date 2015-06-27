//-----------------------------------------------------------------------------
// Title         : Preloaded SRAM for Cache
// Project       : nnsim-hdl
//-----------------------------------------------------------------------------
// File          : ram_infer_preloaded.v
// Author        : Eldridge  <schuye@celnode06.ad.bu.edu>
// Created       : 2015/02/16
// Last modified : 2015/02/16
//-----------------------------------------------------------------------------
// Description :
//
//-----------------------------------------------------------------------------
// Copyright (c) 2015 by Boston University This model is the confidential and
// proprietary property of Boston University and the possession or use of this
// file requires a written license from Boston University.
//------------------------------------------------------------------------------
// Modification history :
// 2015/02/16 : created
//-----------------------------------------------------------------------------
module ram_infer_preloaded_cache
  #(
    parameter
    WIDTH = 8,
    DEPTH = 64,
    LG_DEPTH = 6,
    INIT_SWITCH = 0,
    ELEMENT_WIDTH = 32,
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

  genvar                  i;

  // Initialize the cache based on the the available initial blocks
  generate
    case (ELEMENTS_PER_BLOCK)
`ifndef RAM_INFER_OVERRIDE
      4: begin
        case (INIT_SWITCH)
          0:
`include "initial/3sum-16.v"
          1:
`include "initial/collatz-16.v"
          2:
`include "initial/edip-16.v"
          3:
`include "initial/ll-16.v"
          4:
`include "initial/rsa-16.v"
	  5:
`include "initial/amos-threshold-16.v"
        endcase
      end
      8: begin
        case (INIT_SWITCH)
          0:
`include "initial/3sum-32.v"
          1:
`include "initial/collatz-32.v"
          2:
`include "initial/edip-32.v"
          3:
`include "initial/ll-32.v"
          4:
`include "initial/rsa-32.v"
	  5:
`include "initial/amos-threshold-32.v"
        endcase
      end
      16: begin
        case (INIT_SWITCH)
          0:
`include "initial/3sum-64.v"
          1:
`include "initial/collatz-64.v"
          2:
`include "initial/edip-64.v"
          3:
`include "initial/ll-64.v"
          4:
`include "initial/rsa-64.v"
	  5:
`include "initial/amos-threshold-64.v"
        endcase
      end
      32: begin
        case (INIT_SWITCH)
          0:
`include "initial/3sum-128.v"
          1:
`include "initial/collatz-128.v"
          2:
`include "initial/edip-128.v"
          3:
`include "initial/ll-128.v"
          4:
`include "initial/rsa-128.v"
	  5:
`include "initial/amos-threshold-128.v"
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
