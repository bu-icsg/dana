SOURCES=hello.c
TARGETS=$(SOURCES:%.c=%.rv)

include ../../common/Makefrag

.PHONY: $(PHONY) run compare

all: compare

run: $(TARGETS)
	@ $(EMULATOR) +maxcycles=$(MAX_CYCLES) pk hello.rv 2>&1 > hello.emulator
	@ spike pk hello.rv 2>&1 > hello.spike

compare: run
	@ $(TOOL_COMPARE) hello.spike hello.emulator $(TEST_NAME)

JUNK += *.spike
