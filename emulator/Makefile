default: all

ROCKETCHIP_ADDONS ?= xfiles-dana
CONFIG ?= XFilesDanaCppPe1Epb4StandaloneConfig
MODEL ?= Standalone

base_dir = $(abspath ../..)
generated_dir = $(abspath ./generated-src)
generated_dir_debug = $(abspath ./generated-src-debug)
sim_dir = $(abspath .)
output_dir = $(sim_dir)/output

firrtl = $(generated_dir)/$(long_name).fir
firrtl_debug = $(generated_dir_debug)/$(long_name).fir
verilog = $(generated_dir)/$(long_name).v
verilog_debug = $(generated_dir_debug)/$(long_name).v

chisel3test = $(base_dir)/chisel3/src/main/resources/top.cpp

include $(base_dir)/Makefrag

CXXSRCS := emulator SimDTM
CXXFLAGS := $(CXXFLAGS) -std=c++11 -I$(RISCV)/include -I$(base_dir)/csrc
LDFLAGS := $(LDFLAGS) -L$(RISCV)/lib -Wl,-rpath,$(RISCV)/lib -L$(abspath $(sim_dir)) -lfesvr -lpthread

emu = emulator-$(PROJECT)-$(CONFIG)
emu_debug = emulator-$(PROJECT)-$(CONFIG)-debug

all: $(emu)
debug: $(emu_debug)

.PHONY: firrtl go-for-it verilog

$(generated_dir)/%.fir: $(chisel_srcs) | $(generated_dir)
	cd $(base_dir) && env ROCKETCHIP_ADDONS=$(ROCKETCHIP_ADDONS) $(SBT) "run-main xfiles.standalone.$(MODEL) $(generated_dir) $(PROJECT) $(MODEL) $(CFG_PROJECT) $(CONFIG)"

%.v: %.fir $(FIRRTL_JAR)
	$(FIRRTL) $(patsubst %,-i %,$(filter %.fir,$^)) -o $@ -X verilog

firrtl: $(generated_dir)/$(long_name).fir go-for-it
verilog: $(generated_dir)/$(long_name).v go-for-it

# Verilator -- Just build this once in the Rocket Chip repo
INSTALLED_VERILATOR=$(abspath $(base_dir)/emulator/verilator/install/bin/verilator)
verilator $(INSTALLED_VERILATOR):
	$(MAKE) -C $(base_dir)/emulator verilator/install/bin/verilator

CXXFLAGS := $(CXXFLAGS) -std=c++11
LDFLAGS := $(LDFLAGS)
VERILATOR := $(INSTALLED_VERILATOR) --cc --exe
VERILATOR_FLAGS := --top-module $(MODEL) \
  +define+PRINTF_COND="!$(MODEL).reset" \
  +define+STOP_COND="!$(MODEL).reset" \
  --output-split 20000 \
  -Wno-STMTDLY --x-assign unique \
  -O3 -CFLAGS "$(CXXFLAGS) -DVERILATOR -DTOP_TYPE=V$(MODEL) -include $(generated_dir)/$(long_name)/V$(MODEL).h"

emulator $(emu): $(verilog) $(chisel3test) | $(generated_dir)/$(long_name)
	$(VERILATOR) $(VERILATOR_FLAGS) -Mdir $(generated_dir)/$(long_name) \
	-o $(sim_dir)/$@ $< $(chisel3test)
	$(MAKE) -C $(generated_dir)/$(long_name) -j -f V$(MODEL).mk

emulator-debug $(emu_debug): $(verilog) $(chisel3test) | $(generated_dir)/$(long_name)
	$(VERILATOR) $(VERILATOR_FLAGS) -Mdir $(generated_dir)/$(long_name) --trace \
	-o $(sim_dir)/$@ $< $(chisel3test)
	$(MAKE) -C $(generated_dir)/$(long_name) -j -f V$(MODEL).mk

$(generated_dir)/$(long_name) $(generated_dir):
	mkdir $@

clean:
	rm -rf $(generated_dir)
