SBT          ?= sbt
_SBT_FLAGS   ?= -Dsbt.log.noformat=true
SBT_FLAGS    ?=
DIR_SRC = src
DIR_BUILD = build

CHISEL_FLAGS :=
GPP_FLAGS = -I $(DIR_BUILD) -g
GPP = g++

# EXECUTABLES = $(notdir $(basename $(wildcard $(DIR_SRC)/*.scala)))
EXECUTABLES = Dana
ALL_MODULES = $(notdir $(wildcard $(DIR_SRC)/*.scala))
TESTS = t_Dana.cpp
OUTS = $(EXECUTABLES:%=$(DIR_BUILD)/%.out)
TEST_EXECUTABLES = $(TESTS:%.cpp=$(DIR_BUILD)/%)
TEST_OBJECTS = $(TESTS:%.cpp=$(DIR_BUILD)/%.o)
VCDS = $(TESTS:%.cpp=$(DIR_BUILD)/%.vcd)
OBJECTS = $(TEST_OBJECTS) $(EXECUTABLES:%=$(DIR_BUILD)/%.o)
HDLS = $(EXECUTABLES:%=$(DIR_BUILD)/%.v)
DOTS = $(EXECUTABLES:%=$(DIR_BUILD)/%.dot)

vpath %.scala $(DIR_SRC)
vpath %.cpp $(DIR_SRC)
vpath %.cpp $(DIR_BUILD)

.PHONY: all phony clean test verilog vcd

default: all

#------------------- Build targets depending on scala sources
$(DIR_BUILD)/%.out: %.scala $(ALL_MODULES)
	set -e -o pipefail; \
	$(SBT) $(SBT_FLAGS) "run $(basename $(notdir $<)) --genHarness --compile --test --backend c --debug --vcd --targetDir $(DIR_BUILD)" | tee $@

$(DIR_BUILD)/%.v: %.scala $(ALL_MODULES)
	set -e -o pipefail; \
	$(SBT) $(SBT_FLAGS) "run $(basename $(notdir $<)) --genHarness --compile --backend v --targetDir $(DIR_BUILD)"

$(DIR_BUILD)/%.dot: %.scala $(ALL_MODULES)
	set -e -o pipefail; \
	$(SBT) $(SBT_FLAGS) "run $(basename $(notdir $<)) --compile --backend dot --targetDir $(DIR_BUILD)"

#------------------- Other build targets
$(DIR_BUILD)/%.o: %.cpp Makefile
	$(GPP) -c $(GPP_FLAGS) $< -o $@

$(DIR_BUILD)/%.vcd: $(DIR_BUILD)/% Makefile
	$<

build/t_Dana: $(OUTS) $(TEST_OBJECTS)
	$(GPP) $(GPP_FLAGS) $(OBJECTS) $(EMULATOR_OBJECTS) -o $@

vcd: $(DIR_BUILD)/t_Dana.vcd Makefile
	scripts/gtkwave $<

verilog: $(HDLS)

all: $(TEST_EXECUTABLES)

clean:
	rm -f $(DIR_BUILD)/*
	rm -rf target
