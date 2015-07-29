# Common configuration
JOBS            = 4
DIR_SRC_SCALA	= src/main/scala
DIR_SRC_V	= src/main/verilog
DIR_SRC_CPP	= src/main/cpp
DIR_TEST_SCALA	= src/test/scala
DIR_TEST_V	= src/test/verilog
DIR_TEST_CPP	= src/test/cpp
DIR_TEST_RV     = src/test/rv
DIR_BUILD	= build
DIR_MAIN_RES    = src/main/resources
DIR_USR         = usr
DIR_USR_BIN     = usr/bin
DIR_USR_LIB     = usr/lib
DIR_USR_INCLUDE = usr/include

# Chisel/Scala configuration
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
	$(shell readlink -f ../submodules/verilog/src/) \
	$(shell readlink -f ../nnsim-hdl/src)
FLAGS_V            = -g2012 $(addprefix -I, $(INCLUDE_V))
HEADERS_V          = ../nnsim-hdl/src/ram_infer_preloaded_cache.v \
	../submodules/verilog/src/ram_infer.v \
	$(wildcard ../nnsim-hdl/src/initial/*.v)

# RISCV Tests Targets
RV_TESTS             = $(notdir $(wildcard $(DIR_TEST_RV)/*.c))
RV_TESTS_EXECUTABLES = $(RV_TESTS:%.c=$(DIR_BUILD)/%.rv)
RV_TESTS_DISASM      = $(RV_TESTS:%.c=$(DIR_BUILD)/%.rvS)

# Compiler related options
GPP           = g++
GCC           = gcc
INCLUDE_PATHS = $(DIR_BUILD) ../usr/include
INCLUDES      = $(addprefix -include $(DIR_BUILD)/, \
	$(EXECUTABLES:%=%$(CHISEL_CONFIG_DOT).h))
GPP_FLAGS     = $(INCLUDES) $(INCLUDE_PATHS:%=-I %) -g -std=c++11

# RISC-V related options
RV_GCC        = riscv64-unknown-elf-gcc
RV_AR         = riscv64-unknown-elf-ar
RV_OBJDUMP    = riscv64-unknown-elf-objdump

# Linker related options
LIB_PATHS     = ../usr/lib
LIB_LIBS      = m fann
LFLAGS        = $(addprefix -Wl$(COMMA)-R, $(shell readlink -f $(LIB_PATHS))) \
	$(LIB_PATHS:%=-L %) $(LIB_LIBS:%=-l %)

# X-FILES libraries related
XFILES_LIBRARIES = $(DIR_BUILD)/libxfiles.a
#$(DIR_BUILD)/libxfiles.so
XFILES_LIBRARIES_OBJECTS = $(DIR_BUILD)/xfiles-user.o $(DIR_BUILD)/xfiles-supervisor.o

# Network Configurations
NETS=3sum collatz rsa ll edip blackscholes fft inversek2j jmeint jpeg kmeans sobel amos
NETS_THRESHOLD=3sum collatz ll rsa amos
NETS_BIN=$(addprefix $(DIR_BUILD)/nets/, $(addsuffix -fixed.16bin, $(NETS)) \
	$(addsuffix -fixed.32bin, $(NETS)) \
	$(addsuffix -fixed.64bin, $(NETS)) \
	$(addsuffix -fixed.128bin, $(NETS)))
# [TODO] Skip threshold nets as I don't have floating point *.net sources
# NETS_BIN+=$(addprefix $(DIR_BUILD)/nets/, \
# 	$(addsuffix -threshold-fixed.16bin, $(NETS_THRESHOLD)) \
# 	$(addsuffix -threshold-fixed.32bin, $(NETS_THRESHOLD)) \
# 	$(addsuffix -threshold-fixed.64bin, $(NETS_THRESHOLD)) \
# 	$(addsuffix -threshold-fixed.128bin, $(NETS_THRESHOLD)))
NETS_H+=$(addprefix $(DIR_BUILD)/nets/, $(addsuffix -fixed-16bin-32.h, $(NETS)) \
	$(addsuffix -fixed-32bin-32.h, $(NETS)) \
	$(addsuffix -fixed-64bin-32.h, $(NETS)) \
	$(addsuffix -fixed-128bin-32.h, $(NETS)))
NETS_H+=$(addprefix $(DIR_BUILD)/nets/, $(addsuffix -fixed-16bin-64.h, $(NETS)) \
	$(addsuffix -fixed-32bin-64.h, $(NETS)) \
	$(addsuffix -fixed-64bin-64.h, $(NETS)) \
	$(addsuffix -fixed-128bin-64.h, $(NETS)))
FLOAT_TO_FIXED=$(DIR_USR_BIN)/fann-float-to-fixed
WRITE_FANN_CONFIG=$(DIR_USR_BIN)/write-fann-config-for-accelerator
BIN_TO_C_HEADER=$(DIR_USR_BIN)/bin-config-to-c-header
NETS_TOOLS = $(FLOAT_TO_FIXED) $(WRITE_FANN_CONFIG) $(BIN_TO_C_HEADER)
DECIMAL_POINT_OFFSET=7

vpath %.scala $(DIR_SRC_SCALA)
vpath %.cpp $(DIR_TEST_CPP)
vpath %.cpp $(DIR_BUILD)
vpath %.c $(DIR_TEST_RV) src/main/c
vpath %.h src/main/c
vpath %.v $(DIR_TEST_V)
vpath %.v $(DIR_SRC_V)
vpath %.v $(DIR_BUILD)
vpath %.net $(DIR_MAIN_RES) $(DIR_BUILD)/nets
vpath %.train $(DIR_MAIN_RES) # This is missing *.train.X, e.g., *.train.100
vpath %bin $(DIR_BUILD)/nets

.PHONY: all clean cpp debug dot fann libraries mrproper nets run run-verilog \
	tools vcd vcd-verilog verilog

default: all

all: $(TEST_EXECUTABLES)
# all: $(BACKEND_CPP)

cpp: $(BACKEND_CPP)

dot: $(BACKEND_DOT)

fann:
	cd submodules/fann && cmake . && make -j$(JOBS)

nets: build/nets $(NETS_BIN) $(NETS_H)

libraries: $(XFILES_LIBRARIES)

verilog: $(BACKEND_VERILOG)

vcd: $(DIR_BUILD)/t_XFilesDana$(CHISEL_CONFIG_DOT).vcd Makefile
	scripts/gtkwave $<

run: $(TEST_EXECUTABLES) Makefile
	$< $(<:$(DIR_BUILD)/t_%=$(DIR_BUILD)/%.prm)

run-verilog: $(TEST_V_EXECUTABLES) Makefile
	vvp $<

tools: fann
	make -j$(JOBS) -C tools

vcd-verilog: $(DIR_BUILD)/t_XFilesDana$(FPGA_CONFIG_DOT)-vcd.vvp Makefile
	vvp $<
	scripts/gtkwave $<.vcd

debug: $(TEST_EXECUTABLES) Makefile
	$< -d $(<:$(DIR_BUILD)/t_%=$(DIR_BUILD)/%.prm)

rv: libraries nets $(DIR_BUILD)/cache $(RV_TESTS_EXECUTABLES) $(RV_TESTS_DISASM)

#------------------- Dependent Programs
$(FLOAT_TO_FIXED): tools
$(WRITE_FANN_CONFIG): tools
$(BIN_TO_C_HEADER): tools

#------------------- Library Targets
$(DIR_BUILD)/xfiles-user.o: xfiles-user.c
	$(RV_GCC) -Wall -Werror -march=RV64IMAFDXcustom -c $< -o $@

$(DIR_BUILD)/xfiles-supervisor.o: xfiles-supervisor.c
	$(RV_GCC) -Wall -Werror -march=RV64IMAFDXcustom -c $< -o $@

$(DIR_BUILD)/libxfiles.a: $(XFILES_LIBRARIES_OBJECTS) xfiles.h
	$(RV_AR) rcs $@ $(XFILES_LIBRARIES_OBJECTS)

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
	$(GPP) -c $(GPP_FLAGS) $< -o $@

$(DIR_BUILD)/%.vcd: $(DIR_BUILD)/% Makefile
	$< -v $<.vcd $(<:$(DIR_BUILD)/t_%=$(DIR_BUILD)/%.prm)

$(DIR_BUILD)/t_XFilesDana$(CHISEL_CONFIG_DOT): $(OBJECTS)
	$(GPP) $(GPP_FLAGS) $(OBJECTS) $(EMULATOR_OBJECTS) $(LFLAGS) -o $@

#------------------- Verilog Backend Targets
$(DIR_BUILD)/%$(FPGA_CONFIG_DOT).vvp: %.v $(BACKEND_VERILOG) $(HEADERS_V)
	iverilog $(FLAGS_V) -o $@ $<

$(DIR_BUILD)/%$(FPGA_CONFIG_DOT)-vcd.vvp: %.v $(BACKEND_VERILOG) $(HEADERS_V)
	iverilog $(FLAGS_V) -D DUMP_VCD=\"$@.vcd\" -o $@ $<

#------------------- RISC-V Tests
$(DIR_BUILD)/%.rv: %.c $(XFILES_LIBRARIES)
	$(RV_GCC) -Wall -Werror -static -march=RV64IMAFDXcustom -Isrc/main/c -Ibuild/nets $< -o $@ -L$(DIR_BUILD) -lxfiles

$(DIR_BUILD)/%.rvS: $(DIR_BUILD)/%.rv
	$(RV_OBJDUMP) -S $< > $@

#------------------- Tools

#------------------- Nets
$(DIR_BUILD)/nets/%-fixed.net: %.net $(NETS_TOOLS)
	$(FLOAT_TO_FIXED) $< $@

$(DIR_BUILD)/nets/%.16bin: $(DIR_BUILD)/nets/%.net $(NETS_TOOLS)
	$(WRITE_FANN_CONFIG) 16 $< $@ $(DECIMAL_POINT_OFFSET)

$(DIR_BUILD)/nets/%.32bin: $(DIR_BUILD)/nets/%.net $(NETS_TOOLS)
	$(WRITE_FANN_CONFIG) 32 $< $@ $(DECIMAL_POINT_OFFSET)

$(DIR_BUILD)/nets/%.64bin: $(DIR_BUILD)/nets/%.net $(NETS_TOOLS)
	$(WRITE_FANN_CONFIG) 64 $< $@ $(DECIMAL_POINT_OFFSET)

$(DIR_BUILD)/nets/%.128bin: $(DIR_BUILD)/nets/%.net $(NETS_TOOLS)
	$(WRITE_FANN_CONFIG) 128 $< $@ $(DECIMAL_POINT_OFFSET)

$(DIR_BUILD)/nets/%-16bin-32.h: $(DIR_BUILD)/nets/%.16bin $(NETS_TOOLS)
	$(BIN_TO_C_HEADER) $< $(subst -,_,init-$(basename $(notdir $<))-16bin-32) 32 > $@

$(DIR_BUILD)/nets/%-16bin-64.h: $(DIR_BUILD)/nets/%.16bin $(NETS_TOOLS)
	$(BIN_TO_C_HEADER) $< $(subst -,_,init-$(basename $(notdir $<))-16bin-64) 64 > $@

$(DIR_BUILD)/nets/%-32bin-32.h: $(DIR_BUILD)/nets/%.32bin $(NETS_TOOLS)
	$(BIN_TO_C_HEADER) $< $(subst -,_,init-$(basename $(notdir $<))-32bin-32) 32 > $@

$(DIR_BUILD)/nets/%-32bin-64.h: $(DIR_BUILD)/nets/%.32bin $(NETS_TOOLS)
	$(BIN_TO_C_HEADER) $< $(subst -,_,init-$(basename $(notdir $<))-32bin-64) 64 > $@

$(DIR_BUILD)/nets/%-64bin-32.h: $(DIR_BUILD)/nets/%.64bin $(NETS_TOOLS)
	$(BIN_TO_C_HEADER) $< $(subst -,_,init-$(basename $(notdir $<))-64bin-32) 32 > $@

$(DIR_BUILD)/nets/%-64bin-64.h: $(DIR_BUILD)/nets/%.64bin $(NETS_TOOLS)
	$(BIN_TO_C_HEADER) $< $(subst -,_,init-$(basename $(notdir $<))-64bin-64) 64 > $@

$(DIR_BUILD)/nets/%-128bin-32.h: $(DIR_BUILD)/nets/%.128bin $(NETS_TOOLS)
	$(BIN_TO_C_HEADER) $< $(subst -,_,init-$(basename $(notdir $<))-128bin-32) 32 > $@

$(DIR_BUILD)/nets/%-128bin-64.h: $(DIR_BUILD)/nets/%.128bin $(NETS_TOOLS)
	$(BIN_TO_C_HEADER) $< $(subst -,_,init-$(basename $(notdir $<))-128bin-64) 64 > $@

$(DIR_BUILD)/nets:
	mkdir $@

#------------------- Populate a dummy cache (shouldn't be needed!)
$(DIR_BUILD)/cache:
	$(DIR_USR_BIN)/danaCache $@ src/main/resources/fft.net

#------------------- Utility Targets
clean:
	rm -rf $(DIR_BUILD)/*
	rm -rf target

mrproper: clean
	make clean -C tools
	make clean -C submodules/fann
