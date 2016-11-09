default: all

ROCKETCHIP_ADDONS ?= xfiles-dana
CONFIG ?= XFilesDanaCppPe1Epb4StandaloneConfig
MODEL ?= DebugTester

base_dir = $(abspath ../..)
DIR_TOP = $(base_dir)/xfiles-dana
generated_dir = $(abspath ./generated-src)
generated_dir_debug = $(abspath ./generated-src-debug)
sim_dir = $(abspath .)
output_dir = $(sim_dir)/output

firrtl = $(generated_dir)/$(long_name).fir
firrtl_debug = $(generated_dir_debug)/$(long_name).fir
verilog = $(generated_dir)/$(long_name).v
verilog_debug = $(generated_dir_debug)/$(long_name).v
instrumented = $(generated_dir)/$(long_name)-instrumented.v
instrumented_debug = $(generated_dir_debug)/$(long_name)-instrumented.v

chisel3test = $(DIR_TOP)/src/main/resources/top.cpp

include $(base_dir)/Makefrag

PROJECT = xfiles.standalone
CFG_PROJECT = rocketchip
CXXSRCS := emulator SimDTM
CXXFLAGS := $(CXXFLAGS) -std=c++11 -I$(RISCV)/include -I$(base_dir)/csrc
LDFLAGS := $(LDFLAGS) -L$(RISCV)/lib -Wl,-rpath,$(RISCV)/lib -L$(abspath $(sim_dir)) -lfesvr -lpthread

emu = emulator-$(PROJECT)-$(CONFIG)
emu_debug = emulator-$(PROJECT)-$(CONFIG)-debug

.PHONY: all emulator emulator-debug firrtl go-for-it run vcd verilator verilog

all emulator: $(emu)
debug: $(emu_debug)
firrtl: $(firrtl) go-for-it
verilog: $(verilog) go-for-it
instrumented: $(instrumented) go-for-it

$(generated_dir)/%.fir: $(chisel_srcs) | $(generated_dir)
	cd $(base_dir) && env ROCKETCHIP_ADDONS=$(ROCKETCHIP_ADDONS) $(SBT) "run-main xfiles.standalone.Standalone $(generated_dir) $(PROJECT) $(MODEL) $(CFG_PROJECT) $(CONFIG)"

%.v: %.fir $(FIRRTL_JAR)
	$(FIRRTL) $(patsubst %,-i %,$(filter %.fir,$^)) -o $@ -X verilog

# Verilator -- Just build this once in the Rocket Chip repo
INSTALLED_VERILATOR=$(abspath $(base_dir)/emulator/verilator/install/bin/verilator)
verilator $(INSTALLED_VERILATOR):
	$(MAKE) -C $(base_dir)/emulator verilator/install/bin/verilator
CXXFLAGS := $(CXXFLAGS) -std=c++11
LDFLAGS := $(LDFLAGS)
VERILATOR := $(INSTALLED_VERILATOR) --cc --exe
VERILATOR_FLAGS := --top-module $(MODEL) \
  +define+PRINTF_COND=\$$c\(\"verbose\",\"\&\&\"\,\"done_reset\"\) \
  +define+STOP_COND="!$(MODEL).reset" \
  --output-split 20000 \
  -Wno-STMTDLY --x-assign unique \
  -O3 -CFLAGS "$(CXXFLAGS) -DVERILATOR -DTOP_TYPE=V$(MODEL) -include $(generated_dir)/$(long_name)/V$(MODEL).h -include $(base_dir)/csrc/verilator.h"

# GTKWAVE -- Build this if we need it
GTKWAVE = $(sim_dir)/gtkwave/bin/gtkwave
JOBS = 4
$(GTKWAVE): | $(sim_dir)/gtkwave
	(cd $(sim_dir)/gtkwave/src && \
	./configure --prefix=$(sim_dir)/gtkwave --disable-xz && \
	make -j$(JOBS) && \
	make install || true)
$(sim_dir)/gtkwave:
	svn co "svn://svn.code.sf.net/p/gtkwave/code/gtkwave3" $@/src

TOOL_INSTRUMENT_DPI = $(DIR_TOP)/usr/bin/instrument_dpi
$(instrumented): $(verilog)
	cp $< $<.bak
	$(TOOL_INSTRUMENT_DPI) \
	-m TileLinkTestRAM \
	-s ram \
	$<.bak > $@
$(instrumented_debug): $(verilog_debug)
	cp $< $<.bak
	$(TOOL_INSTRUMENT_DPI) \
	-m TileLinkTestRAM \
	-s ram \
	$<.bak > $@

$(emu): $(instrumented) $(chisel3test) | $(generated_dir)/$(long_name)
	$(VERILATOR) $(VERILATOR_FLAGS) -Mdir $(generated_dir)/$(long_name) \
	-o $(sim_dir)/$@ $< $(chisel3test)
	$(MAKE) VM_PARALLEL_BUILDS=1 -C $(generated_dir)/$(long_name) -j -f V$(MODEL).mk

$(emu_debug): $(verilog) $(chisel3test) | $(generated_dir)/$(long_name)
	$(VERILATOR) $(VERILATOR_FLAGS) -Mdir $(generated_dir)/$(long_name) --trace \
	-o $(sim_dir)/$@ $< $(chisel3test)
	$(MAKE) VM_PARALLEL_BUILDS=1 -C $(generated_dir)/$(long_name) -j -f V$(MODEL).mk

run: $(emu)
	./$(emu)

vcd $(generated_dir)/$(long_name)/dump.vcd: $(emu_debug)
	./$< --trace=$(generated_dir)/$(long_name)/dump.vcd

include $(DIR_TOP)/tools/common/Makefrag-submodule
WAVES = $(DIR_TOP)/submodules/hdl-scripts/addWavesRecursive.tcl
gtkwave: $(generated_dir)/$(long_name)/dump.vcd $(GTKWAVE) $(SUBMODULE_HDL_SCRIPTS)
	$(GTKWAVE) -S $(WAVES) $< > $(generated_dir)/$(long_name)/dump.gtkw
	$(GTKWAVE) $< $(generated_dir)/$(long_name)/dump.gtkw

$(generated_dir)/$(long_name) $(generated_dir):
	mkdir $@

clean:
	rm -rf $(generated_dir) emulator-*
