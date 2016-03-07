# Chisel/Scala configuration

# Shared parameters (be careful messing with these)
SHELL            = /bin/bash
DIR_TOP         ?= .
DIR_BUILD	?= $(DIR_TOP)/build

DIR_BUILD_NETS  = $(DIR_BUILD)/nets
DIR_FANN        = $(DIR_TOP)/submodules/fann

# Common configuration
SHELL           = /bin/bash
JOBS            = `$(DIR_TOP)/usr/bin/max-processors.sh`
DIR_SRC_SCALA	= $(DIR_TOP)/src/main/scala
DIR_SRC_V	= $(DIR_TOP)/src/main/verilog
DIR_SRC_C       = $(DIR_TOP)/src/main/c
DIR_SRC_CPP	= $(DIR_TOP)/src/main/cpp
DIR_TEST_SCALA	= $(DIR_TOP)/src/test/scala
DIR_TEST_V	= $(DIR_TOP)/src/test/verilog
DIR_TEST_CPP	= $(DIR_TOP)/src/test/cpp
DIR_TEST_RV     = $(DIR_TOP)/src/test/rv
DIR_MAIN_RES    = $(DIR_TOP)/src/main/resources
DIR_USR         = $(DIR_TOP)/usr
DIR_USR_BIN     = $(DIR_TOP)/usr/bin
DIR_USR_LIB     = $(DIR_TOP)/usr/lib
DIR_USR_LIB_RV  = $(DIR_TOP)/usr/lib-rv-newlib
DIR_USR_LIB_RV_LINUX = $(DIR_TOP)/usr/lib-rv-linux
DIR_USR_INCLUDE = $(DIR_TOP)/usr/include
DIR_TOOLS       = $(DIR_TOP)/tools
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
	$(shell readlink -f ../submodules/verilog/src/) \
	$(shell readlink -f ../nnsim-hdl/src)
FLAGS_V            = -g2012 $(addprefix -I, $(INCLUDE_V))
HEADERS_V          = ../nnsim-hdl/src/ram_infer_preloaded_cache.v \
	../submodules/verilog/src/ram_infer.v \
	$(wildcard ../nnsim-hdl/src/initial/*.v)

# RISCV Tests Targets
RV_TESTS             = hello.c \
	fann-xfiles.c \
	fann-soft.c \
	write-on-invalid-asid.c \
	read-xfiles-dana-id.c
RV_TESTS_EXECUTABLES = $(RV_TESTS:%.c=$(DIR_BUILD)/newlib/%.rv) \
	$(RV_TESTS:%.c=$(DIR_BUILD)/linux/%.rv)
RV_TESTS_DISASM      = $(RV_TESTS:%.c=$(DIR_BUILD)/newlib/%.rvS) \
	$(RV_TESTS:%.c=$(DIR_BUILD)/linux/%.rvS)

# Compiler related options
CXX           ?= g++
CC            ?= gcc
INCLUDE_PATHS = $(DIR_BUILD) $(DIR_USR_INCLUDE)
INCLUDES      = $(addprefix -include $(DIR_BUILD)/, \
	$(EXECUTABLES:%=%$(CHISEL_CONFIG_DOT).h))
CXX_FLAGS     = $(INCLUDES) $(INCLUDE_PATHS:%=-I %) -g -std=c++11

# RISC-V related options
RV_NEWLIB        = riscv64-unknown-elf
RV_LINUX         = riscv64-unknown-linux-gnu
RV_CC            = $(RV_NEWLIB)-gcc
RV_CXX           = $(RV_NEWLIB)-g++
RV_AR            = $(RV_NEWLIB)-ar
RV_OBJDUMP       = $(RV_NEWLIB)-objdump
RV_CC_LINUX      = $(RV_LINUX)-gcc
RV_CXX_LINUX     = $(RV_LINUX)-g++
RV_AR_LINUX      = $(RV_LINUX)-ar
RV_OBJDUMP_LINUX = $(RV_LINUX)-objdump

# Linker related options
LIB_PATHS     = $(DIR_USR_LIB)
LIB_LIBS      = m fann
LFLAGS        = $(addprefix -Wl$(COMMA)-R, $(shell readlink -f $(LIB_PATHS))) \
	$(LIB_PATHS:%=-L %) $(LIB_LIBS:%=-l %)

# X-FILES libraries related
XFILES_LIBRARIES_NEWLIB = $(DIR_BUILD)/newlib/libxfiles-user.a \
	$(DIR_BUILD)/newlib/libxfiles-supervisor.a
XFILES_LIBRARIES_LINUX = $(DIR_BUILD)/linux/libxfiles-user.a \
	$(DIR_BUILD)/linux/libxfiles-supervisor.a
#$(DIR_BUILD)/libxfiles.so
XFILES_LIBRARIES_OBJECTS_NEWLIB = $(DIR_BUILD)/newlib/xfiles-user.o \
	$(DIR_BUILD)/newlib/xfiles-supervisor.o

XFILES_LIBRARIES_OBJECTS_LINUX = $(DIR_BUILD)/linux/xfiles-user.o \
	$(DIR_BUILD)/linux/xfiles-supervisor.o

# Network Configurations -- the convenction here is floating point
# nets look like "foo.net" while the fixed point equivalent of this is
# "foo-fixed.net". NETS and NETS_THRESHOLD have floating point
# configurations which live in src/main/resources. NETS_GEN are
# randomly initialized networks which have to be generated with
# `fann-random` and will have their floating point versions put in
# build/nets.
NETS_GEN=xorSigmoid xorSigmoidSymmetric xorSigmoidSymmetricPair \
	xorSigmoidSymmetricThreeLayer
NETS_FANN=census-house mushroom diabetes gene kin32fm soybean thyroid \
	two-spiral abelone bank32fm bank32nh building census-house \
	pumadyn-32fm robot soybean
NETS_PARITY=parity-1 parity-2 parity-3 parity-4 parity-5 parity-6 parity-7 \
	parity-8 parity-9
NETS_PARITY_SAME=parity-same-1 parity-same-2 parity-same-3 parity-same-4 \
	parity-same-5 parity-same-6 parity-same-7 parity-same-8 parity-same-9
NETS_XOR=xor-sigmoid-4i xor-sigmoid-8i xor-sigmoid-16i xor-sigmoid-32i \
	xor-sigmoid-64i xor-sigmoid-128i xor-sigmoid-256i xor-sigmoid-512i \
	xor-sigmoid-1024i \
	xor-sigmoid-4o xor-sigmoid-8o xor-sigmoid-16o xor-sigmoid-32o \
	xor-sigmoid-64o xor-sigmoid-128o xor-sigmoid-256o xor-sigmoid-512o \
	xor-sigmoid-1024o
NETS_SIN=sin
NETS+=$(NETS_GEN) $(NETS_FANN) $(NETS_PARITY) $(NETS_PARITY_SAME) $(NETS_XOR) \
	$(NETS_SIN)
NETS_FLOAT=$(addsuffix -float, $(NETS))
# Only certain networks have valid training files
NETS_TRAIN=xorSigmoid xorSigmoidSymmetric xorSigmoidSymmetricPair \
	xorSigmoidSymmetricThreeLayer
NETS_BIN=$(addprefix $(DIR_BUILD_NETS)/, $(addsuffix -fixed.16bin, $(NETS)) \
	$(addsuffix -fixed.32bin, $(NETS)) \
	$(addsuffix -fixed.64bin, $(NETS)) \
	$(addsuffix -fixed.128bin, $(NETS)))
TRAIN_SIN=sin-scale-0.25 sin-scale-0.50 sin-scale-0.75 sin-scale-1.00 \
	sin-scale-1.25 sin-scale-1.50 sin-scale-1.75 sin-scale-2.00 \
	sin-scale-2.25 sin-scale-2.50 sin-scale-2.75 sin-scale-3.00 \
	sin-scale-3.25 sin-scale-3.50 sin-scale-3.75 sin-scale-4.00
# [TODO] Skip threshold nets as I don't have floating point *.net sources
# NETS_BIN+=$(addprefix $(DIR_BUILD_NETS)/, \
# 	$(addsuffix -threshold-fixed.16bin, $(NETS_THRESHOLD)) \
# 	$(addsuffix -threshold-fixed.32bin, $(NETS_THRESHOLD)) \
# 	$(addsuffix -threshold-fixed.64bin, $(NETS_THRESHOLD)) \
# 	$(addsuffix -threshold-fixed.128bin, $(NETS_THRESHOLD)))
NETS_H+=$(addprefix $(DIR_BUILD_NETS)/, $(addsuffix -fixed-16bin-32.h, $(NETS)) \
	$(addsuffix -fixed-32bin-32.h, $(NETS)) \
	$(addsuffix -fixed-64bin-32.h, $(NETS)) \
	$(addsuffix -fixed-128bin-32.h, $(NETS)))
NETS_H+=$(addprefix $(DIR_BUILD_NETS)/, $(addsuffix -fixed-16bin-64.h, $(NETS)) \
	$(addsuffix -fixed-32bin-64.h, $(NETS)) \
	$(addsuffix -fixed-64bin-64.h, $(NETS)) \
	$(addsuffix -fixed-128bin-64.h, $(NETS)))
TRAIN_H=$(addprefix $(DIR_BUILD_NETS)/, $(addsuffix _train.h, $(NETS_TRAIN)))
TRAIN_FIXED=$(addprefix $(DIR_BUILD_NETS)/, $(addsuffix -fixed.train, $(NETS_GEN)))
TRAIN_FIXED+=$(addprefix $(DIR_BUILD_NETS)/, $(addsuffix -fixed.train, $(NETS_FANN)))
TRAIN_FIXED+=$(addprefix $(DIR_BUILD_NETS)/, $(addsuffix -fixed.train, $(NETS_PARITY)))
TRAIN_FIXED+=$(addprefix $(DIR_BUILD_NETS)/, $(addsuffix -fixed.train, $(NETS_XOR)))
TRAIN_FIXED+=$(addprefix $(DIR_BUILD_NETS)/, $(addsuffix -fixed.train, $(TRAIN_SIN)))
FLOAT_TO_FIXED=$(DIR_USR_BIN)/fann-float-to-fixed
WRITE_FANN_CONFIG=$(DIR_USR_BIN)/write-fann-config-for-accelerator
BIN_TO_C_HEADER=$(DIR_USR_BIN)/bin-config-to-c-header
TRAIN_TO_C_HEADER=$(DIR_USR_BIN)/fann-train-to-c-header
TRAIN_TO_C_HEADER_FIXED=$(DIR_USR_BIN)/fann-train-to-c-header-fixed
FANN_RANDOM=$(DIR_USR_BIN)/fann-random
FANN_CHANGE_FIXED_POINT=$(DIR_USR_BIN)/fann-change-fixed-point
FANN_TRAIN_TO_FIXED=$(DIR_USR_BIN)/fann-data-to-fixed
GEN_BOOLEAN_DATA=$(DIR_USR_BIN)/gen-boolean-data
GEN_MATH_DATA=$(DIR_USR_BIN)/gen-math-data
FANN_TRAIN=$(DIR_USR_BIN)/fann-train
GEN_VIDEO=$(DIR_USR_BIN)/gen-trace-video
NETS_TOOLS = $(FLOAT_TO_FIXED) \
	$(WRITE_FANN_CONFIG) \
	$(BIN_TO_C_HEADER) \
	$(TRAIN_TO_C_HEADER) \
	$(FANN_RANDOM) \
	$(FANN_TRAIN)
DECIMAL_POINT_OFFSET=7
DECIMAL_POINT_BITS=3
MAX_DECIMAL_POINT=`echo "2 $(DECIMAL_POINT_BITS)^1-$(DECIMAL_POINT_OFFSET)+p"|dc`

FANN_LIBS = $(DIR_BUILD)/fann/libfann.so \
	$(DIR_BUILD)/fann-rv-newlib/libfann.a \
	$(DIR_BUILD)/fann-rv-linux/libfann.a

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
vpath %bin $(DIR_BUILD_NETS)

.PHONY: all clean cpp debug doc dot libraries mrproper nets run \
	run-verilog tools vcd vcd-verilog verilog

default: all

# all: $(TEST_EXECUTABLES)
# all: $(BACKEND_CPP)
all:

cpp: $(BACKEND_CPP)

dot: $(BACKEND_DOT)

$(DIR_BUILD)/fann/libfann.so:
	if [ ! -d $(DIR_BUILD)/fann ]; then mkdir -p $(DIR_BUILD)/fann; fi
	cd $(DIR_BUILD)/fann && \
	cmake -DCMAKE_LIBRARY_OUTPUT_DIRECTORY=\
	$(shell readlink -f $(DIR_BUILD)/fann) \
	-DCMAKE_C_FLAGS="-DFANN_NO_SEED" \
	-DCMAKE_CXX_FLAGS="-DFANN_NO_SEED" \
	../../submodules/fann && \
	$(MAKE)

# Newer versions of FANN includes additional tests which newlib cannot
# build. Hence, this target is set to ignore all build errors. This is
# dangerous, but I don't see a way around it without me putting a fix
# in FANN.
$(DIR_BUILD)/fann-rv-newlib/libfann.a:
	if [ ! -d $(DIR_BUILD)/fann-rv-newlib ]; \
	  then mkdir -p $(DIR_BUILD)/fann-rv-newlib; fi
	cd $(DIR_BUILD)/fann-rv-newlib && \
	cmake -DCMAKE_ARCHIVE_OUTPUT_DIRECTORY=\
	$(shell readlink -f $(DIR_BUILD)/fann-rv-newlib) \
	-DPKGCONFIG_INSTALL_DIR=$(shell readlink \
	  -f $(DIR_BUILD)/fann-rv-newlib) \
	-DINCLUDE_INSTALL_DIR=$(shell readlink \
	  -f $(DIR_BUILD)/fann-rv-newlib) \
	-DLIB_INSTALL_DIR=$(shell readlink \
	  -f $(DIR_BUILD)/fann-rv-newlib) \
	-DCMAKE_CONFIG_DIR=$(shell readlink -f $(DIR_BUILD)/fann-rv-newlib) \
	-DCMAKE_CURRENT_BINARY_DIR=$(shell readlink \
	  -f $(DIR_BUILD)/fann-rv-newlib) \
	-DCMAKE_C_COMPILER=$(RV_CC) \
	-DCMAKE_CXX_COMPILER=$(RV_CXX) \
	-DCMAKE_SYSTEM_NAME=Generic \
	-DDISABLE_PARALLEL_FANN=1 \
	-DBUILD_SHARED_LIBS=OFF \
	../../submodules/fann && \
	$(MAKE) -k || true

$(DIR_BUILD)/fann-rv-linux/libfann.a:
	if [ ! -d $(DIR_BUILD)/fann-rv-linux ]; \
	  then mkdir -p $(DIR_BUILD)/fann-rv-linux; fi
	cd $(DIR_BUILD)/fann-rv-linux && \
	cmake -DCMAKE_ARCHIVE_OUTPUT_DIRECTORY=\
	$(shell readlink -f $(DIR_BUILD)/fann-rv-linux) \
	-DPKGCONFIG_INSTALL_DIR=$(shell readlink -f \
	  $(DIR_BUILD)/fann-rv-linux) \
	-DINCLUDE_INSTALL_DIR=$(shell readlink -f \
	  $(DIR_BUILD)/fann-rv-linux) \
	-DLIB_INSTALL_DIR=$(shell readlink -f $(DIR_BUILD)/fann-rv-linux) \
	-DCMAKE_CONFIG_DIR=$(shell readlink -f $(DIR_BUILD)/fann-rv-linux) \
	-DCMAKE_CURRENT_BINARY_DIR=$(shell readlink -f \
	  $(DIR_BUILD)/fann-rv-linux) \
	-DCMAKE_C_COMPILER=$(RV_CC_LINUX) \
	-DCMAKE_CXX_COMPILER=$(RV_CXX_LINUX) \
	-DDISABLE_PARALLEL_FANN=1 \
	../../submodules/fann && \
	$(MAKE)

verilog: $(BACKEND_VERILOG)

vcd: $(DIR_BUILD)/t_XFilesDana$(CHISEL_CONFIG_DOT).vcd Makefile
	scripts/gtkwave $<

run: $(TEST_EXECUTABLES) Makefile
	$< $(<:$(DIR_BUILD)/t_%=$(DIR_BUILD)/%.prm)

run-verilog: $(TEST_V_EXECUTABLES) Makefile
	vvp $<

tools: $(FANN_LIBS)
	$(MAKE) -j$(JOBS) -C $(DIR_TOOLS)

vcd-verilog: $(DIR_BUILD)/t_XFilesDana$(FPGA_CONFIG_DOT)-vcd.vvp Makefile
	vvp $<
	scripts/gtkwave $<.vcd

debug: $(TEST_EXECUTABLES) Makefile
	$< -d $(<:$(DIR_BUILD)/t_%=$(DIR_BUILD)/%.prm)

rv: $(XFILES_LIBRARIES_NEWLIB) $(XFILES_LIBRARIES_LINUX) \
	$(NETS_BIN) \
	$(TRAIN_FIXED) \
	$(RV_TESTS_EXECUTABLES) \
	$(RV_TESTS_DISASM)

#------------------- Library Targets
$(DIR_BUILD)/newlib/%.o: %.c $(DIR_BUILD)/newlib
	$(RV_CC) -Wall -Werror -march=RV64IMAFDXcustom -I. -c $< -o $@

$(DIR_BUILD)/newlib/lib%.a: $(DIR_BUILD)/newlib/%.o %.h
	$(RV_AR) rcs $@ $<

$(DIR_BUILD)/linux/%.o: %.c $(DIR_BUILD)/linux
	$(RV_CC_LINUX) -Wall -Werror -march=RV64IMAFDXcustom -I. -c $< -o $@

$(DIR_BUILD)/linux/lib%.a: $(DIR_BUILD)/linux/%.o %.h
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

#------------------- RISC-V Tests
$(DIR_BUILD)/newlib/fann-soft.rv: fann-soft.c $(DIR_BUILD_NETS) $(XFILES_LIBRARIES_NEWLIB) $(DIR_BUILD)/newlib $(DIR_BUILD)/fann-rv-newlib/libfann.a
	$(RV_CC) -Wall -Werror -static -march=RV64IMAFDXcustom -I$(DIR_SRC_C) -I$(DIR_BUILD_NETS) -I$(DIR_USR_INCLUDE) -I. $< -o $@ -L$(DIR_USR)/lib-rv-newlib -lxfiles-user -lfann -lm

$(DIR_BUILD)/linux/fann-soft.rv: fann-soft.c $(DIR_BUILD_NETS) $(XFILES_LIBRARIES_LINUX) $(DIR_BUILD)/linux $(DIR_BUILD)/fann-rv-linux/libfann.a
	$(RV_CC_LINUX) -Wall -Werror -static -march=RV64IMAFDXcustom -I$(DIR_SRC_C) -I$(DIR_BUILD_NETS) -I$(DIR_USR_INCLUDE) -I. $< -o $@ -L$(DIR_USR)/lib-rv-linux -lxfiles-user -lfann -lm

$(DIR_BUILD)/newlib/%.rv: %.c $(DIR_BUILD_NETS) $(XFILES_LIBRARIES_NEWLIB) $(DIR_BUILD)/newlib $(DIR_BUILD)/fann-rv-newlib/libfann.a
	$(RV_CC) -Wall -Werror -static -march=RV64IMAFDXcustom -I$(DIR_SRC_C) -I$(DIR_BUILD_NETS) -I$(DIR_USR_INCLUDE) -I. $< -o $@ -L$(DIR_USR)/lib-rv-newlib -lxfiles-user -lfixedfann -lm

$(DIR_BUILD)/linux/%.rv: %.c $(DIR_BUILD_NETS) $(XFILES_LIBRARIES_LINUX) $(DIR_BUILD)/linux $(DIR_BUILD)/fann-rv-linux/libfann.a
	$(RV_CC_LINUX) -Wall -Werror -static -march=RV64IMAFDXcustom -I$(DIR_SRC_C) -I$(DIR_BUILD_NETS) -I$(DIR_USR_INCLUDE) -I. $< -o $@ -L$(DIR_USR)/lib-rv-linux -lxfiles-user -lfixedfann -lm

$(DIR_BUILD)/newlib/%.rvS: $(DIR_BUILD)/newlib/%.rv
	$(RV_OBJDUMP) -S $< > $@

$(DIR_BUILD)/linux/%.rvS: $(DIR_BUILD)/linux/%.rv
	$(RV_OBJDUMP) -S $< > $@

#------------------- Nets
include $(DIR_TOOLS)/common/Makefrag-nets
include $(DIR_TOOLS)/common/Makefrag-tools

$(DIR_BUILD_NETS)/%-16bin-32.h: $(DIR_BUILD_NETS)/%.16bin $(NETS_TOOLS)
	$(BIN_TO_C_HEADER) $< \
	$(subst -,_,init-$(basename $(notdir $<))-16bin-32) 32 > $@

$(DIR_BUILD_NETS)/%-16bin-64.h: $(DIR_BUILD_NETS)/%.16bin $(NETS_TOOLS)
	$(BIN_TO_C_HEADER) $< \
	$(subst -,_,init-$(basename $(notdir $<))-16bin-64) 64 > $@

$(DIR_BUILD_NETS)/%-32bin-32.h: $(DIR_BUILD_NETS)/%.32bin $(NETS_TOOLS)
	$(BIN_TO_C_HEADER) $< \
	$(subst -,_,init-$(basename $(notdir $<))-32bin-32) 32 > $@

$(DIR_BUILD_NETS)/%-32bin-64.h: $(DIR_BUILD_NETS)/%.32bin $(NETS_TOOLS)
	$(BIN_TO_C_HEADER) $< \
	$(subst -,_,init-$(basename $(notdir $<))-32bin-64) 64 > $@

$(DIR_BUILD_NETS)/%-64bin-32.h: $(DIR_BUILD_NETS)/%.64bin $(NETS_TOOLS)
	$(BIN_TO_C_HEADER) $< \
	$(subst -,_,init-$(basename $(notdir $<))-64bin-32) 32 > $@

$(DIR_BUILD_NETS)/%-64bin-64.h: $(DIR_BUILD_NETS)/%.64bin $(NETS_TOOLS)
	$(BIN_TO_C_HEADER) $< \
	$(subst -,_,init-$(basename $(notdir $<))-64bin-64) 64 > $@

$(DIR_BUILD_NETS)/%-128bin-32.h: $(DIR_BUILD_NETS)/%.128bin $(NETS_TOOLS)
	$(BIN_TO_C_HEADER) $< \
	$(subst -,_,init-$(basename $(notdir $<))-128bin-32) 32 > $@

$(DIR_BUILD_NETS)/%-128bin-64.h: $(DIR_BUILD_NETS)/%.128bin $(NETS_TOOLS)
	$(BIN_TO_C_HEADER) $< \
	$(subst -,_,init-$(basename $(notdir $<))-128bin-64) 64 > $@

#--------- Fixed point training files
$(DIR_BUILD_NETS)/%_train.h: %.train $(NETS_TOOLS)
	@ if [[ -e $(DIR_MAIN_RES)/$(notdir $(basename $<)-float.net) ]]; \
	then $(TRAIN_TO_C_HEADER) \
	$(basename $<)-float.net $< $(basename $(notdir $<)) > $@;\
	else $(TRAIN_TO_C_HEADER) $(DIR_BUILD_NETS)/$(notdir \
	$(basename $<)-float.net) $< $(basename $(notdir $<)) > $@; \
	fi

$(DIR_BUILD_NETS):
	if [ ! -d $@ ]; then mkdir -p $@; fi

$(DIR_BUILD)/linux:
	if [ ! -d $@ ]; then mkdir -p $@; fi

$(DIR_BUILD)/newlib:
	if [ ! -d $@ ]; then mkdir -p $@; fi

#--------- Generate videos
include $(DIR_TOOLS)/common/Makefrag-video

#--------- Generate ScalaDoc documentation
doc:
	scaladoc src/main/scala/*.scala -d $(DIR_BUILD)/doc

$(DIR_BUILD)/doc:
	if [ ! -d $@ ]; then mkdir -p $@; fi

#------------------- Populate a dummy cache (shouldn't be needed!)
$(DIR_BUILD)/cache:
	$(DIR_USR_BIN)/danaCache $@ src/main/resources/fft.net

#------------------- Utility Targets
clean:
	rm -rf $(DIR_BUILD)/*
	rm -rf target

mrproper: clean
	$(MAKE) clean -C $(DIR_TOOLS)
	$(MAKE) clean -C $(DIR_FANN)
