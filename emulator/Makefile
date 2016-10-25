default: all

base_dir = $(abspath ../..)
generated_dir = $(abspath ./generated-src)
generated_dir_debug = $(abspath ./generated-src-debug)
sim_dir = $(abspath .)
output_dir = $(sim_dir)/output

ROCKETCHIP_ADDONS ?= xfiles-dana
CONFIG ?= XFilesDanaCppPe1Epb4StandaloneConfig

include $(base_dir)/Makefrag

CXXSRCS := emulator SimDTM
CXXFLAGS := $(CXXFLAGS) -std=c++11 -I$(RISCV)/include -I$(base_dir)/csrc
LDFLAGS := $(LDFLAGS) -L$(RISCV)/lib -Wl,-rpath,$(RISCV)/lib -L$(abspath $(sim_dir)) -lfesvr -lpthread

emu = emulator-$(PROJECT)-$(CONFIG)
emu_debug = emulator-$(PROJECT)-$(CONFIG)-debug

.PHONY: firrtl go-for-it verilog

$(generated_dir)/%.fir: $(chisel_srcs) | $(generated_dir)
	cd $(base_dir) && env ROCKETCHIP_ADDONS=$(ROCKETCHIP_ADDONS) $(SBT) "run-main xfiles.standalone.Standalone $(generated_dir) $(PROJECT) Standalone $(CFG_PROJECT) $(CONFIG)"

%.v: %.fir $(FIRRTL_JAR)
	$(FIRRTL) $(patsubst %,-i %,$(filter %.fir,$^)) -o $@ -X verilog

firrtl: $(generated_dir)/$(PROJECT).$(CONFIG).fir go-for-it
verilog: $(generated_dir)/$(PROJECT).$(CONFIG).v go-for-it

$(generated_dir):
	mkdir $@
