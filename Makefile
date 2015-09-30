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
DIR_FANN        = submodules/fann
SEED            = $(shell echo "$$RANDOM")

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
RV_TESTS             = hello.c \
	rsa-rocc.c \
	rsa-rocc-supervisor.c \
	rsa-rocc-supervisor-incremental.c \
	rsa-rocc-supervisor-batch.c \
	rsa-rocc-supervisor-batch-fast.c \
	xorSigmoid-batch.c \
	xorSigmoidSymmetric-batch.c \
	torture.c \
	fann-batch.c \
	fann-soft.c
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
RV_TARGET     = riscv64-unknown-elf
RV_GCC        = $(RV_TARGET)-gcc
RV_GPP        = $(RV_TARGET)-g++
RV_AR         = $(RV_TARGET)-ar
RV_OBJDUMP    = $(RV_TARGET)-objdump

# Linker related options
LIB_PATHS     = ../usr/lib
LIB_LIBS      = m fann
LFLAGS        = $(addprefix -Wl$(COMMA)-R, $(shell readlink -f $(LIB_PATHS))) \
	$(LIB_PATHS:%=-L %) $(LIB_LIBS:%=-l %)

# X-FILES libraries related
XFILES_LIBRARIES = $(DIR_BUILD)/libxfiles.a
#$(DIR_BUILD)/libxfiles.so
XFILES_LIBRARIES_OBJECTS = $(DIR_BUILD)/xfiles-user.o $(DIR_BUILD)/xfiles-supervisor.o

# Network Configurations -- the convenction here is floating point
# nets look like "foo.net" while the fixed point equivalent of this is
# "foo-fixed.net". NETS and NETS_THRESHOLD have floating point
# configurations which live in src/main/resources. NETS_GEN are
# randomly initialized networks which have to be generated with
# `fann-random` and will have their floating point versions put in
# build/nets.
NETS=3sum collatz rsa ll edip blackscholes fft inversek2j jmeint jpeg kmeans sobel amos
NETS_THRESHOLD=3sum collatz ll rsa amos
NETS_GEN=xorSigmoid xorSigmoidSymmetric xorSigmoidSymmetricPair \
	xorSigmoidSymmetricPairThreeLayer
NETS_FANN=census-house mushroom diabetes gene kin32fm soybean thyroid two-spiral
NETS+=$(NETS_GEN)
NETS+=$(NETS_FANN)
NETS_FLOAT=$(addsuffix -float, $(NETS))
# Only certain networks have valid training files
NETS_TRAIN=blackscholes fft inversek2j jmeint jpeg kmeans rsa sobel \
	xorSigmoid xorSigmoidSymmetric xorSigmoidSymmetricPair \
	xorSigmoidSymmetricPairThreeLayer
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
TRAIN_H=$(addprefix $(DIR_BUILD)/nets/, $(addsuffix _train.h, $(NETS_TRAIN)))
TRAIN_FIXED=$(addprefix $(DIR_BUILD)/nets/, $(addsuffix -fixed.train, $(NETS_GEN)))
TRAIN_FIXED+=$(addprefix $(DIR_BUILD)/nets/, $(addsuffix -fixed.train, $(NETS_FANN)))
FLOAT_TO_FIXED=$(DIR_USR_BIN)/fann-float-to-fixed
WRITE_FANN_CONFIG=$(DIR_USR_BIN)/write-fann-config-for-accelerator
BIN_TO_C_HEADER=$(DIR_USR_BIN)/bin-config-to-c-header
TRAIN_TO_C_HEADER=$(DIR_USR_BIN)/fann-train-to-c-header
TRAIN_TO_C_HEADER_FIXED=$(DIR_USR_BIN)/fann-train-to-c-header-fixed
FANN_RANDOM=$(DIR_USR_BIN)/fann-random
FANN_CHANGE_FIXED_POINT=$(DIR_USR_BIN)/fann-change-fixed-point
FANN_TRAIN_TO_FIXED=$(DIR_USR_BIN)/fann-data-to-fixed
NETS_TOOLS = $(FLOAT_TO_FIXED) \
	$(WRITE_FANN_CONFIG) \
	$(BIN_TO_C_HEADER) \
	$(TRAIN_TO_C_HEADER) \
	$(FANN_RANDOM) \
	$(FANN_CHANGE_FIXED_POINT) \
	$(FANN_TRAIN_TO_FIXED)
DECIMAL_POINT_OFFSET=7
DECIMAL_POINT_BITS=3
MAX_DECIMAL_POINT=`echo "2 $(DECIMAL_POINT_BITS)^1-$(DECIMAL_POINT_OFFSET)+p"|dc`

vpath %.scala $(DIR_SRC_SCALA)
vpath %.cpp $(DIR_TEST_CPP)
vpath %.cpp $(DIR_BUILD)
vpath %.c $(DIR_TEST_RV) src/main/c
vpath %.h src/main/c
vpath %.v $(DIR_TEST_V)
vpath %.v $(DIR_SRC_V)
vpath %.v $(DIR_BUILD)
vpath %-float.net $(DIR_MAIN_RES)
vpath %.train $(DIR_MAIN_RES) # This is missing *.train.X, e.g., *.train.100
vpath %.train $(DIR_FANN)/datasets
vpath %bin $(DIR_BUILD)/nets

.PHONY: all clean cpp debug dot fann libraries mrproper nets run run-verilog \
	tools vcd vcd-verilog verilog

.PRECIOUS: $(DIR_BUILD)/nets/%-fixed.net \
	$(DIR_BUILD)/nets/%-float.net \
	$(DIR_BUILD)/nets/%.net

default: all

all: $(TEST_EXECUTABLES)
# all: $(BACKEND_CPP)

cpp: $(BACKEND_CPP)

dot: $(BACKEND_DOT)

fann: $(DIR_BUILD)/fann
	cd $(DIR_BUILD)/fann && \
	cmake -DCMAKE_LIBRARY_OUTPUT_DIRECTORY=\
	$(shell readlink -f $(DIR_BUILD)/fann) \
	-DCMAKE_C_FLAGS="-DFANN_NO_SEED" \
	-DCMAKE_CXX_FLAGS="-DFANN_NO_SEED" \
	../../submodules/fann && \
	make

fann-rv: $(DIR_BUILD)/fann-rv
	cd $(DIR_BUILD)/fann-rv && \
	cmake -DCMAKE_ARCHIVE_OUTPUT_DIRECTORY=\
	$(shell readlink -f $(DIR_BUILD)/fann-rv) \
	-DPKGCONFIG_INSTALL_DIR=$(shell readlink -f $(DIR_BUILD)/fann-rv) \
	-DINCLUDE_INSTALL_DIR=$(shell readlink -f $(DIR_BUILD)/fann-rv) \
	-DLIB_INSTALL_DIR=$(shell readlink -f $(DIR_BUILD)/fann-rv) \
	-DCMAKE_CONFIG_DIR=$(shell readlink -f $(DIR_BUILD)/fann-rv) \
	-DCMAKE_CURRENT_BINARY_DIR=$(shell readlink -f $(DIR_BUILD)/fann-rv) \
	-DCMAKE_C_COMPILER=$$RISCV/bin/$(RV_GCC) \
	-DCMAKE_CXX_COMPILER=$$RISCV/bin/$(RV_GPP) \
	-DCMAKE_SYSTEM_NAME=Generic \
	-DBUILD_SHARED_LIBS=OFF \
	-DCMAKE_C_FLAGS="-DFANN_NO_SEED" \
	-DCMAKE_CXX_FLAGS="-DFANN_NO_SEED" \
	../../submodules/fann && \
	make

nets: tools $(DIR_BUILD)/nets $(NETS_BIN) $(NETS_H) $(TRAIN_H) $(TRAIN_FIXED)

libraries: $(XFILES_LIBRARIES)

verilog: $(BACKEND_VERILOG)

vcd: $(DIR_BUILD)/t_XFilesDana$(CHISEL_CONFIG_DOT).vcd Makefile
	scripts/gtkwave $<

run: $(TEST_EXECUTABLES) Makefile
	$< $(<:$(DIR_BUILD)/t_%=$(DIR_BUILD)/%.prm)

run-verilog: $(TEST_V_EXECUTABLES) Makefile
	vvp $<

tools: fann fann-rv
	make -j$(JOBS) -C tools

vcd-verilog: $(DIR_BUILD)/t_XFilesDana$(FPGA_CONFIG_DOT)-vcd.vvp Makefile
	vvp $<
	scripts/gtkwave $<.vcd

debug: $(TEST_EXECUTABLES) Makefile
	$< -d $(<:$(DIR_BUILD)/t_%=$(DIR_BUILD)/%.prm)

rv: libraries nets $(RV_TESTS_EXECUTABLES) $(RV_TESTS_DISASM)

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
$(DIR_BUILD)/fann-soft.rv: fann-soft.c $(XFILES_LIBRARIES)
	$(RV_GCC) -Wall -Werror -static -march=RV64IMAFDXcustom -Isrc/main/c -I$(DIR_BUILD)/nets -I$(DIR_USR_INCLUDE) $< -o $@ -Lusr/lib-rv -lxfiles -lfann -lm

$(DIR_BUILD)/%.rv: %.c $(XFILES_LIBRARIES)
	$(RV_GCC) -Wall -Werror -static -march=RV64IMAFDXcustom -Isrc/main/c -I$(DIR_BUILD)/nets -I$(DIR_USR_INCLUDE) $< -o $@ -Lusr/lib-rv -lxfiles -lfixedfann -lm

$(DIR_BUILD)/%.rvS: $(DIR_BUILD)/%.rv
	$(RV_OBJDUMP) -S $< > $@

#------------------- Tools

#------------------- Nets
$(DIR_BUILD)/nets/%-fixed.net: $(DIR_BUILD)/nets/%-float.net $(NETS_TOOLS)
	$(FLOAT_TO_FIXED) $< $@
	if [[ `grep decimal $@ | sed 's/.\+=//'` -gt $(MAX_DECIMAL_POINT) ]]; then \
	echo "[WARN] Fixed point precision unexpectedly high, attempting to fix..."; \
	mv $@ $@.tooBig; \
	$(FANN_CHANGE_FIXED_POINT) $@.tooBig $(MAX_DECIMAL_POINT) > $@; \
	rm $@.tooBig; \
	elif [[ `grep decimal $@ | sed 's/.\+=//'` -lt $(DECIMAL_POINT_OFFSET) ]]; \
	then echo "[WARN] Fixed point precision too low, attempting to fix..."; \
	mv $@ $@.tooSmall; \
	$(FANN_CHANGE_FIXED_POINT) $@.tooSmall $(DECIMAL_POINT_OFFSET) > $@; \
	rm $@.tooSmall; fi

$(DIR_BUILD)/nets/%-fixed.net: %-float.net $(NETS_TOOLS)
	$(FLOAT_TO_FIXED) $< $@
	if [[ `grep decimal $@ | sed 's/.\+=//'` -gt $(MAX_DECIMAL_POINT) ]]; then \
	echo "[WARN] Fixed point precision unexpectedly high, attempting to fix..."; \
	mv $@ $@.tooBig; \
	$(FANN_CHANGE_FIXED_POINT) $@.tooBig $(MAX_DECIMAL_POINT) > $@; \
	rm $@.tooBig; \
	elif [[ `grep decimal $@ | sed 's/.\+=//'` -lt $(DECIMAL_POINT_OFFSET) ]]; \
	then echo "[WARN] Fixed point precision too low, attempting to fix..."; \
	mv $@ $@.tooSmall; \
	$(FANN_CHANGE_FIXED_POINT) $@.tooSmall $(DECIMAL_POINT_OFFSET) > $@; \
	rm $@.tooSmall; fi

#--------- Randomly generated nets based on some training data
$(DIR_BUILD)/nets/xorSigmoid-float.net: $(NETS_TOOLS)
	$(FANN_RANDOM) -s$(SEED) -r0.7 -l2 -l3 -l1 -a5 -o3 $@

$(DIR_BUILD)/nets/xorSigmoidSymmetric-float.net: $(NETS_TOOLS)
	$(FANN_RANDOM) -s$(SEED) \
	-nsrc/main/resources/xorSigmoidSymmetric.train \
	-l2 -l3 -l1 -a5 -o5 $@

$(DIR_BUILD)/nets/xorSigmoidSymmetricPair-float.net: $(NETS_TOOLS)
	$(FANN_RANDOM) -s$(SEED) \
	-nsrc/main/resources/xorSigmoidSymmetricPair.train \
	-l2 -l3 -l2 -a5 -o5 $@

$(DIR_BUILD)/nets/xorSigmoidSymmetricPairThreeLayer-float.net: $(NETS_TOOLS)
	$(FANN_RANDOM) -s$(SEED) \
	-nsrc/main/resources/xorSigmoidSymmetricPairThreeLayer.train \
	-l2 -l3 -l3 -l2 -a5 -o5 $@

$(DIR_BUILD)/nets/census-house-float.net: $(NETS_TOOLS)
	$(FANN_RANDOM) -s$(SEED) \
	-n$(DIR_FANN)/datasets/census-house.train \
	-l16 -l1 -l1 -a5 -o3 $@

$(DIR_BUILD)/nets/mushroom-float.net: $(NETS_TOOLS)
	$(FANN_RANDOM) -s$(SEED) -r0.05 \
	-l125 -l1 -l2 -a3 -o3 $@

$(DIR_BUILD)/nets/diabetes-float.net: $(NETS_TOOLS)
	$(FANN_RANDOM) -s$(SEED) \
	-n$(DIR_FANN)/datasets/diabetes.train \
	-l8 -l10 -l2 -a5 -o3 $@

$(DIR_BUILD)/nets/gene-float.net: $(NETS_TOOLS)
	$(FANN_RANDOM) -s$(SEED) \
	-n$(DIR_FANN)/datasets/gene.train \
	-l120 -l19 -l3 -a5 -o3 $@

$(DIR_BUILD)/nets/kin32fm-float.net: $(NETS_TOOLS)
	$(FANN_RANDOM) -s$(SEED) \
	-n$(DIR_FANN)/datasets/kin32fm.train \
	-l32 -l20 -l1 -a5 -o3 $@

$(DIR_BUILD)/nets/soybean-float.net: $(NETS_TOOLS)
	$(FANN_RANDOM) -s$(SEED) \
	-n$(DIR_FANN)/datasets/soybean.train \
	-l82 -l20 -l19 -a5 -o3 $@

$(DIR_BUILD)/nets/thyroid-float.net: $(NETS_TOOLS)
	$(FANN_RANDOM) -s$(SEED) \
	-n$(DIR_FANN)/datasets/thyroid.train \
	-l21 -l1 -l3 -a5 -o3 $@

$(DIR_BUILD)/nets/two-spiral-float.net: $(NETS_TOOLS)
	$(FANN_RANDOM) -s$(SEED) \
	-n$(DIR_FANN)/datasets/two-spiral.train \
	-l2 -l10 -l30 -l3 -l1 -a5 -o3 $@

#--------- Non-randomly generated networks
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

#--------- Fixed point training files
$(DIR_BUILD)/nets/%-fixed.train: %.train $(DIR_BUILD)/nets/%-fixed.net $(NETS_TOOLS)
	$(FANN_TRAIN_TO_FIXED) $< $@ `grep decimal_point $(word 2,$^) | sed 's/^.\+=//'`

$(DIR_BUILD)/nets/%_train.h: %.train $(NETS_TOOLS)
	if [[ -e $(DIR_MAIN_RES)/$(notdir $(basename $<)-float.net) ]]; \
	  then $(TRAIN_TO_C_HEADER) $(basename $<)-float.net $< $(basename $(notdir $<)) > $@;\
	  else $(TRAIN_TO_C_HEADER) $(DIR_BUILD)/nets/$(notdir $(basename $<)-float.net) $< $(basename $(notdir $<)) > $@; \
	fi

$(DIR_BUILD)/nets:
	mkdir -p $@

$(DIR_BUILD)/fann-rv:
	mkdir -p $@

$(DIR_BUILD)/fann:
	mkdir -p $@

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
