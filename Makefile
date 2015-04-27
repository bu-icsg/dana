SBT          ?= sbt
_SBT_FLAGS   ?= -Dsbt.log.noformat=true
SBT_FLAGS    ?=
DIR_SRC = src
DIR_BUILD = build

CHISEL_FLAGS :=
GPP_FLAGS = -I $(DIR_BUILD)
GPP = g++

# EXECUTABLES = $(notdir $(basename $(wildcard $(DIR_SRC/*.scala))))
EXECUTABLES = Dana
TESTS = t_Dana.cpp
OUTS = $(EXECUTABLES:%=$(DIR_BUILD)/%.out)
TEST_EXECUTABLES = $(TESTS:%.cpp=$(DIR_BUILD)/%)
TEST_OBJECTS = $(TESTS:%.cpp=$(DIR_BUILD)/%.o)
OBJECTS = $(TEST_OBJECTS) $(EXECUTABLES:%=$(DIR_BUILD)/%.o)
HDLS = $(EXECUTABLES:%=$(DIR_BUILD)/%.v)
DOTS = $(EXECUTABLES:%=$(DIR_BUILD)/%.dot)

vpath %.scala $(DIR_SRC)
vpath %.cpp $(DIR_SRC)
vpath %.cpp $(DIR_BUILD)

.PHONY: all phony clean test verilog

default: all

#------------------- Build targets depending on scala sources
$(DIR_BUILD)/%.out: %.scala
	set -e -o pipefail; \
	$(SBT) $(SBT_FLAGS) "run $(basename $(notdir $<)) --genHarness --compile --test --backend c --debug --vcd --targetDir $(DIR_BUILD)" | tee $@

$(DIR_BUILD)/%.v: %.scala
	set -e -o pipefail; \
	$(SBT) $(SBT_FLAGS) "run $(basename $(notdir $<)) --genHarness --compile --backend v --targetDir $(DIR_BUILD)"

$(DIR_BUILD)/%.dot: %.scala
	set -e -o pipefail; \
	$(SBT) $(SBT_FLAGS) "run $(basename $(notdir $<)) --compile --backend dot --targetDir $(DIR_BUILD)"

#------------------- Other build targets
$(DIR_BUILD)/%.o: %.cpp
	$(GPP) -c $(GPP_FLAGS) $< -o $@

build/t_Dana: $(OUTS) $(TEST_OBJECTS)
	$(GPP) $(GPP_FLAGS) $(OBJECTS) $(EMULATOR_OBJECTS) -o $@

verilog: $(HDLS)

all: $(TEST_EXECUTABLES)

clean:
	rm -f $(DIR_BUILD)/*
	rm -rf target
