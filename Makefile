# Common configuration
DIR_SRC		= src
DIR_BUILD	= build

# Chisel/Scala configuration
SBT			?= sbt
# Unused sbt flags
_SBT_FLAGS		?= -Dsbt.log.noformat=true
SBT_FLAGS		?=
CHISEL_TOP		= dana
CHISEL_CONFIG		= DefaultConfig
CHISEL_CONFIG_DOT 	= .$(CHISEL_CONFIG)
CHISEL_FLAGS		= --targetDir $(DIR_BUILD) \
	--configDump \
	--compile \
	--configInstance $(CHISEL_TOP)$(CHISEL_CONFIG_DOT) \
	--debug \
	--vcd
CHISEL_FLAGS_CPP	= --backend c --genHarness --compile $(CHISEL_FLAGS)
CHISEL_FLAGS_V		= --backend v $(CHISEL_FLAGS)
CHISEL_FLAGS_DOT	= --backend dot $(CHISEL_FLAGS)
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
EXECUTABLES	= Dana
ALL_MODULES	= $(notdir $(wildcard $(DIR_SRC)/*.scala))
BACKEND_CPP	= $(EXECUTABLES:%=$(DIR_BUILD)/%$(CHISEL_CONFIG_DOT).cpp)
BACKEND_VERILOG = $(EXECUTABLES:%=$(DIR_BUILD)/%$(CHISEL_CONFIG_DOT).v)
BACKEND_DOT	= $(EXECUTABLES:%=$(DIR_BUILD)/%$(CHISEL_CONFIG_DOT).dot)

# C++ Backend Specific Targets
TESTS            = t_Dana.cpp
TEST_EXECUTABLES = $(TESTS:%.cpp=$(DIR_BUILD)/%$(CHISEL_CONFIG_DOT))
OBJECTS          = $(BACKEND_CPP:%.cpp=%.o) $(TESTS:%.cpp=$(DIR_BUILD)/%.o)
VCDS             = $(TESTS:%.cpp=$(DIR_BUILD)/%.vcd)
STRIPPED         = $(EXECUTABLES:%=$(DIR_BUILD)/%-emulator-nomain.o)

# Compiler related options
GPP           = g++
INCLUDE_PATHS = $(DIR_BUILD) ../usr/include
INCLUDES      = $(addprefix -include $(DIR_BUILD)/, \
	$(EXECUTABLES:%=%$(CHISEL_CONFIG_DOT).h))
GPP_FLAGS     = $(INCLUDES) $(INCLUDE_PATHS:%=-I %) -g -std=c++11

# Linker related options
LIB_PATHS     = ../usr/lib
LIB_LIBS      = m fann
LFLAGS        = $(addprefix -Wl$(COMMA)-R, $(shell readlink -f $(LIB_PATHS))) \
	$(LIB_PATHS:%=-L %) $(LIB_LIBS:%=-l %)

vpath %.scala $(DIR_SRC)
vpath %.cpp $(DIR_SRC)
vpath %.cpp $(DIR_BUILD)

.PHONY: all clean cpp dot verilog vcd run

default: all

all: $(TEST_EXECUTABLES)
# all: $(BACKEND_CPP)

cpp: $(BACKEND_CPP)

dot: $(BACKEND_DOT)

verilog: $(BACKEND_VERILOG)

vcd: $(DIR_BUILD)/t_Dana$(CHISEL_CONFIG_DOT).vcd Makefile
	scripts/gtkwave $<

run: $(TEST_EXECUTABLES) Makefile
	$< $(<:$(DIR_BUILD)/t_%=$(DIR_BUILD)/%.prm)

#------------------- Chisel Build Targets
$(DIR_BUILD)/%$(CHISEL_CONFIG_DOT).cpp: %.scala $(ALL_MODULES) Makefile
	set -e -o pipefail; \
	$(SBT) $(SBT_FLAGS) "run $(basename $(notdir $<)) $(CHISEL_FLAGS_CPP)" \
	| tee $@.out

$(DIR_BUILD)/%$(CHISEL_CONFIG_DOT).v: %.scala $(ALL_MODULES) Makefile
	set -e -o pipefail; \
	$(SBT) $(SBT_FLAGS) "run $(basename $(notdir $<)) $(CHISEL_FLAGS_V)" \
	| tee $@.out

$(DIR_BUILD)/%$(CHISEL_CONFIG_DOT).dot: %.scala $(ALL_MODULES) Makefile
	set -e -o pipefail; \
	$(SBT) $(SBT_FLAGS) "run $(basename $(notdir $<)) $(CHISEL_FLAGS_DOT)" \
	| tee $@.out

#------------------- C++ Backend Targets
$(DIR_BUILD)/%-nomain.o: $(DIR_BUILD)/%.o Makefile
	strip -N main $< -o $@

$(DIR_BUILD)/%.o: %.cpp Makefile
	$(GPP) -c $(GPP_FLAGS) $< -o $@

$(DIR_BUILD)/%.vcd: $(DIR_BUILD)/% Makefile
	$< $(<:$(DIR_BUILD)/t_%=$(DIR_BUILD)/%.prm) $<.vcd

$(DIR_BUILD)/t_Dana$(CHISEL_CONFIG_DOT): $(OBJECTS)
	$(GPP) $(GPP_FLAGS) $(OBJECTS) $(EMULATOR_OBJECTS) $(LFLAGS) -o $@

clean:
	rm -f $(DIR_BUILD)/*
	rm -rf target
