# Chisel/Scala configuration

# Shared parameters (be careful messing with these)
SHELL            = /bin/bash
DIR_TOP         ?= .
DIR_BUILD	?= $(DIR_TOP)/build

DIR_FANN        = $(DIR_TOP)/submodules/fann

# Common configuration
SHELL           = /bin/bash
JOBS            = `$(DIR_TOP)/usr/bin/max-processors.sh`
DIR_SRC_SCALA	= $(DIR_TOP)/src/main/scala
DIR_SRC_V	= $(DIR_TOP)/src/main/verilog
DIR_SRC_C       = $(DIR_TOP)/src/main/c
DIR_SRC_CPP	= $(DIR_TOP)/src/main/cpp
DIR_TEST_V	= $(DIR_TOP)/src/test/verilog
DIR_TEST_CPP	= $(DIR_TOP)/src/test/cpp
DIR_TEST_RV     = $(DIR_TOP)/src/test/rv
DIR_MAIN_RES    = $(DIR_TOP)/src/main/resources
SEED            = $(shell echo "$$RANDOM")
SEED            = 0

SBT			?= sbt
# Unused sbt flags
_SBT_FLAGS		?= -Dsbt.log.noformat=true
SBT_FLAGS		?=
CHISEL_TOP		= dana
CHISEL_CONFIG		= DefaultXFilesDanaConfig
CHISEL_CONFIG_DOT 	= .$(CHISEL_CONFIG)
FPGA_CONFIG             = DefaultXFilesDanaFPGAConfig
FPGA_CONFIG_DOT 	= .$(FPGA_CONFIG)
CHISEL_FLAGS		= --targetDir $(DIR_BUILD) \
	--configDump \
	--compile \
	--debug \
	--vcd
CHISEL_FLAGS_CPP	= --backend c --genHarness --compile $(CHISEL_FLAGS) \
	--configInstance $(CHISEL_TOP)$(CHISEL_CONFIG_DOT)
CHISEL_FLAGS_V		= --backend v $(CHISEL_FLAGS) \
	--configInstance $(CHISEL_TOP)$(FPGA_CONFIG_DOT)
CHISEL_FLAGS_DOT	= --backend dot $(CHISEL_FLAGS) \
	--configInstance $(CHISEL_TOP)$(CHISEL_CONFIG_DOT)
# Unused Chisel Flags
_CHISEL_FLAGS		= --genHarness \
	--compile \
	--test \
	--debug \
	--vcd \
	--targetDir $(DIR_BUILD) \
	--configInstance $(CHISEL_TOP)$(CHISEL_CONFIG_DOT)

# Miscellaneous crap
COMMA    = ,

# Chisel Target Backends
EXECUTABLES	= XFilesDana
ALL_MODULES	= $(notdir $(wildcard $(DIR_SRC_SCALA)/*.scala))
BACKEND_CPP	= $(EXECUTABLES:%=$(DIR_BUILD)/%$(CHISEL_CONFIG_DOT).cpp)
BACKEND_VERILOG = $(EXECUTABLES:%=$(DIR_BUILD)/%$(FPGA_CONFIG_DOT).v)
BACKEND_DOT	= $(EXECUTABLES:%=$(DIR_BUILD)/%$(CHISEL_CONFIG_DOT).dot)

# C++ Backend Specific Targets
TESTS            = t_XFilesDana.cpp
TEST_EXECUTABLES = $(TESTS:%.cpp=$(DIR_BUILD)/%$(CHISEL_CONFIG_DOT))
SOURCES          = transaction.cpp
OBJECTS          = $(BACKEND_CPP:%.cpp=%.o) $(TESTS:%.cpp=$(DIR_BUILD)/%.o) \
	$(SOURCES:%.cpp=$(DIR_BUILD)/%.o)
VCDS             = $(TESTS:%.cpp=$(DIR_BUILD)/%.vcd)
STRIPPED         = $(EXECUTABLES:%=$(DIR_BUILD)/%-emulator-nomain.o)

# Verilog Backend Specific Targets
TESTS_V            = t_XFilesDana.v
TEST_V_EXECUTABLES = $(TESTS_V:%.v=$(DIR_BUILD)/%$(FPGA_CONFIG_DOT).vvp)
VCDS_V             = $(TESTS_V:%.v=$(DIR_BUILD)/%$(FPGA_CONFIG_DOT)-v.vcd)
INCLUDE_V          = $(DIR_BUILD) $(DIR_BUILD)/cache \
	$(DIR_TEST_V) \
	$(DIR_SRC_V) \
	$(abspath ../submodules/verilog/src/) \
	$(abspath ../nnsim-hdl/src)
FLAGS_V            = -g2012 $(addprefix -I, $(INCLUDE_V))
HEADERS_V          = ../nnsim-hdl/src/ram_infer_preloaded_cache.v \
	../submodules/verilog/src/ram_infer.v \
	$(wildcard ../nnsim-hdl/src/initial/*.v)

# RISCV Tests Targets
RV_TESTS             = hello.c \
	fann-xfiles.c \
	fann-soft.c \
	read-xfiles-dana-id.c \
	trap-00-new-request-no-asid.c \
	trap-00-write-register-no-asid.c \
	trap-00-supervisor-req-as-user.c \
	trap-01-request-antp-not-set.c \
	trap-02-request-oob-asid.c \
	trap-03-request-oob-nnid.c \
	trap-05-request-nn-config-zero-size.c \
	trap-06-request-invalid-epb.c \
	dana-benchmark.c \
	debug-test.c \
	test-pk-debug.c
RV_TESTS_EXECUTABLES_NEWLIB	= $(RV_TESTS:%.c=$(DIR_BUILD)/newlib/%.rv)
RV_TESTS_EXECUTABLES_LINUX	= $(RV_TESTS:%.c=$(DIR_BUILD)/linux/%.rv)
RV_TESTS_DISASM_NEWLIB		= $(RV_TESTS:%.c=$(DIR_BUILD)/newlib/%.rvS)
RV_TESTS_DISASM_LINUX		= $(RV_TESTS:%.c=$(DIR_BUILD)/linux/%.rvS)

# Compiler related options
CXX           ?= g++
CC            ?= gcc
INCLUDE_PATHS = $(DIR_BUILD) $(DIR_TOP)/usr/include
INCLUDES      = $(addprefix -include $(DIR_BUILD)/, \
	$(EXECUTABLES:%=%$(CHISEL_CONFIG_DOT).h))
CXX_FLAGS     = $(INCLUDES) $(INCLUDE_PATHS:%=-I %) -g -std=c++11

# Linker related options
LIB_PATHS     = $(DIR_TOP)/usr/lib
LIB_LIBS      = m fann
LFLAGS        = $(addprefix -Wl$(COMMA)-R, $(abspath $(LIB_PATHS))) \
	$(LIB_PATHS:%=-L %) $(LIB_LIBS:%=-l %)

# X-FILES libraries related
XFILES_LIBRARIES_NEWLIB = $(DIR_BUILD)/newlib/libxfiles-user.a \
	$(DIR_BUILD)/newlib/libxfiles-supervisor.a
XFILES_LIBRARIES_LINUX = $(DIR_BUILD)/linux/libxfiles-user.a \
	$(DIR_BUILD)/linux/libxfiles-supervisor.a

DECIMAL_POINT_OFFSET=7
DECIMAL_POINT_BITS=3
MAX_DECIMAL_POINT=`echo "2 $(DECIMAL_POINT_BITS)^1-$(DECIMAL_POINT_OFFSET)+p"|dc`

vpath %.scala $(DIR_SRC_SCALA)
vpath %.cpp $(DIR_TEST_CPP)
vpath %.cpp $(DIR_BUILD)
vpath %.c $(DIR_SRC_C)
vpath %.c $(DIR_TEST_RV)
vpath %.h $(DIR_SRC_C)
vpath %.v $(DIR_TEST_V)
vpath %.v $(DIR_SRC_V)
vpath %.v $(DIR_BUILD)
vpath %-float.net $(DIR_MAIN_RES)

.PHONY: all clean cpp debug doc dot libraries mrproper nets run \
	run-verilog tags tools vcd vcd-verilog verilog

default: all

all: nets newlib

cpp: $(BACKEND_CPP)

dot: $(BACKEND_DOT)

verilog: $(BACKEND_VERILOG)

vcd: $(DIR_BUILD)/t_XFilesDana$(CHISEL_CONFIG_DOT).vcd Makefile
	scripts/gtkwave $<

run: $(TEST_EXECUTABLES) Makefile
	$< $(<:$(DIR_BUILD)/t_%=$(DIR_BUILD)/%.prm)

run-verilog: $(TEST_V_EXECUTABLES) Makefile
	vvp $<

vcd-verilog: $(DIR_BUILD)/t_XFilesDana$(FPGA_CONFIG_DOT)-vcd.vvp Makefile
	vvp $<
	scripts/gtkwave $<.vcd

debug: $(TEST_EXECUTABLES) Makefile
	$< -d $(<:$(DIR_BUILD)/t_%=$(DIR_BUILD)/%.prm)

newlib: $(XFILES_LIBRARIES_NEWLIB)  \
	$(RV_TESTS_EXECUTABLES_NEWLIB)


linux: $(XFILES_LIBRARIES_LINUX) \
	$(RV_TESTS_EXECUTABLES_LINUX)

disasm-newlib: $(RV_TESTS_DISASM_NEWLIB)
disasm-linux: $(RV_TESTS_DISASM_LINUX)

include $(DIR_TOP)/tools/common/Makefrag-rv
include $(DIR_TOP)/tools/common/Makefrag-fann
include $(DIR_TOP)/tools/common/Makefrag-tools
include $(DIR_TOP)/tools/common/Makefrag-nets
include $(DIR_TOP)/tools/common/Makefrag-video

nets: $(NETS_BIN) $(TRAIN_FIXED)
tools: $(NETS_TOOLS)

#------------------- Chisel Build Targets
$(DIR_BUILD)/%$(CHISEL_CONFIG_DOT).cpp: %.scala $(ALL_MODULES)
	set -e -o pipefail; \
	$(SBT) $(SBT_FLAGS) "run $(basename $(notdir $<)) $(CHISEL_FLAGS_CPP)" \
	| tee $@.out

$(DIR_BUILD)/%$(FPGA_CONFIG_DOT).v: %.scala $(ALL_MODULES)
	set -e -o pipefail; \
	$(SBT) $(SBT_FLAGS) "run $(basename $(notdir $<)) $(CHISEL_FLAGS_V)" \
	| tee $@.out

$(DIR_BUILD)/%$(CHISEL_CONFIG_DOT).dot: %.scala $(ALL_MODULES)
	set -e -o pipefail; \
	$(SBT) $(SBT_FLAGS) "run $(basename $(notdir $<)) $(CHISEL_FLAGS_DOT)" \
	| tee $@.out

#------------------- C++ Backend Targets
$(DIR_BUILD)/%-nomain.o: $(DIR_BUILD)/%.o Makefile
	strip -N main $< -o $@

$(DIR_BUILD)/%.o: %.cpp Makefile
	$(CXX) -c $(CXX_FLAGS) $< -o $@

$(DIR_BUILD)/%.vcd: $(DIR_BUILD)/% Makefile
	$< -v $<.vcd $(<:$(DIR_BUILD)/t_%=$(DIR_BUILD)/%.prm)

$(DIR_BUILD)/t_XFilesDana$(CHISEL_CONFIG_DOT): $(OBJECTS)
	$(CXX) $(CXX_FLAGS) $(OBJECTS) $(EMULATOR_OBJECTS) $(LFLAGS) -o $@

#------------------- Verilog Backend Targets
$(DIR_BUILD)/%$(FPGA_CONFIG_DOT).vvp: %.v $(BACKEND_VERILOG) $(HEADERS_V)
	iverilog $(FLAGS_V) -o $@ $<

$(DIR_BUILD)/%$(FPGA_CONFIG_DOT)-vcd.vvp: %.v $(BACKEND_VERILOG) $(HEADERS_V)
	iverilog $(FLAGS_V) -D DUMP_VCD=\"$@.vcd\" -o $@ $<

#--------- Generate ScalaDoc documentation
doc: | $(DIR_BUILD)/doc
	scaladoc src/main/scala/*.scala -d $(DIR_BUILD)/doc

$(DIR_BUILD)/doc:
	mkdir -p $@

#------------------- Populate a dummy cache (shouldn't be needed!)
$(DIR_BUILD)/cache:
	$(DIR_TOP)/usr/bin/danaCache $@ src/main/resources/fft.net

#------------------- Miscellaneous
tags: $(shell find $(DIR_TOP)/src -regex ".+\.[^~#]+\$$")
	ctags -e -R $?

#------------------- Utility Targets
clean:
	rm -rf $(DIR_BUILD)/*
	rm -rf target

mrproper: clean
	$(MAKE) clean -C $(DIR_TOP)/tools
