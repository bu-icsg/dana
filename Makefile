# Common configuration
DIR_SRC_SCALA	= src/main/scala
DIR_SRC_V	= src/main/verilog
DIR_SRC_CPP	= src/main/cpp
DIR_TEST_SCALA	= src/test/scala
DIR_TEST_V	= src/test/verilog
DIR_TEST_CPP	= src/test/cpp
DIR_TEST_RV     = src/test/rv
DIR_BUILD	= build

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
XFILES_LIBRARIES_OBJECTS = $(DIR_BUILD)/xfiles-user.o

vpath %.scala $(DIR_SRC_SCALA)
vpath %.cpp $(DIR_TEST_CPP)
vpath %.cpp $(DIR_BUILD)
vpath %.c $(DIR_TEST_RV) src/main/c
vpath %.h src/main/c
vpath %.v $(DIR_TEST_V)
vpath %.v $(DIR_SRC_V)
vpath %.v $(DIR_BUILD)

.PHONY: all clean cpp debug dot libraries run run-verilog vcd vcd-verilog verilog

default: all

all: $(TEST_EXECUTABLES)
# all: $(BACKEND_CPP)

cpp: $(BACKEND_CPP)

dot: $(BACKEND_DOT)

libraries: $(XFILES_LIBRARIES)

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

rv: libraries $(RV_TESTS_EXECUTABLES) $(RV_TESTS_DISASM)

#------------------- Library Targets
$(DIR_BUILD)/xfiles-user.o: xfiles-user.c
	$(RV_GCC) -Wall -Werror -march=RV64IMAFDXcustom -c $< -o $@

$(DIR_BUILD)/libxfiles.a: $(XFILES_LIBRARIES_OBJECTS) xfiles.h
	$(RV_AR) rcs $@ $<

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
$(DIR_BUILD)/%.rv: %.c
	$(RV_GCC) -Wall -Werror -static -march=RV64IMAFDXcustom -Isrc/main/c $< -o $@ -L$(DIR_BUILD) -lxfiles

$(DIR_BUILD)/%.rvS: $(DIR_BUILD)/%.rv
	$(RV_OBJDUMP) -S $< > $@

#------------------- Utility Targets
clean:
	rm -rf $(DIR_BUILD)/*
	rm -rf target
