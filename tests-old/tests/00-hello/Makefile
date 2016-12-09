SOURCES=hello.c
TARGETS=$(SOURCES:%.c=%.rv)

.PHONY: $(PHONY) test compare

all: compare

ROCKETCHIP ?= $(abspath ../../../..)
include ../../common/Makefrag

test: $(TARGETS) $(EMULATOR)
	@ $(EMULATOR) +maxcycles=$(MAX_CYCLES) pk hello.rv 2>&1 > \
	hello.emulator
	@ spike pk hello.rv 2>&1 > hello.spike

compare: test
	@ $(TOOL_COMPARE) hello.spike hello.emulator $(TEST_NAME)

JUNK += *.spike
