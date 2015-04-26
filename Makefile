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
SOURCES = Dana.cpp
TESTS = t_Dana.cpp
OBJECTS = $(SOURCES:%.cpp=$(DIR_BUILD)/%.o) $(TESTS:%.cpp=$(DIR_BUILD)/%.o)
EMULATORS = $(EXECUTABLES:%=$(DIR_BUILD)/%.out)
TESTBENCHES_CPP = $(TESTS:%.cpp=$(DIR_BUILD)/%)
HDLS = $(EXECUTABLES:%=$(DIR_BUILD)/%.v)
DOTS = $(EXECUTABLES:%=$(DIR_BUILD)/%.dot)

vpath %.scala $(DIR_SRC)
vpath %.cpp $(DIR_SRC)
vpath %.cpp $(DIR_BUILD)

.PHONY: all emulator phony clean test verilog

default: all

$(DIR_BUILD)/%.out: %.scala
	set -e -o pipefail; \
	$(SBT) $(SBT_FLAGS) "run $(basename $(notdir $<)) --genHarness --compile --test --backend c --debug --vcd --targetDir $(DIR_BUILD)"

$(DIR_BUILD)/%.v: %.scala
	set -e -o pipefail; \
	$(SBT) $(SBT_FLAGS) "run $(basename $(notdir $<)) --genHarness --compile --backend v --targetDir $(DIR_BUILD)"

$(DIR_BUILD)/%.dot: %.scala
	set -e -o pipefail; \
	$(SBT) $(SBT_FLAGS) "run $(basename $(notdir $<)) --compile --backend dot --targetDir $(DIR_BUILD)"

$(DIR_BUILD)/%.o: %.cpp test
	$(GPP) -c $(GPP_FLAGS) $< -o $@

$(DIR_BUILD)/%: $(OBJECTS) test
	$(GPP) $(GPP_FLAGS) $(OBJECTS) -o $@

test: $(EMULATORS)

verilog: $(HDLS)

all: test $(OBJECTS) $(TESTBENCHES_CPP)

clean:
	rm -f $(DIR_BUILD)/*
	rm -rf target

# To build and run C++
#   run ProcessingElement --genHarness --compile --test --backend c --vcd
