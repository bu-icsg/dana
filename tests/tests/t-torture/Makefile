TEST=torture
SOURCES=$(TEST).c
TARGETS=$(SOURCES:%.c=%.rv)

include ../../common/Makefrag

.PHONY: $(PHONY) run compare

all: compare

run: $(TARGETS)
	@ $(EMULATOR) +maxcycles=$(MAX_CYCLES) pk $(TEST).rv 2>&1 > \
	$(TEST).emulator

compare: run
	@ $(TOOL_COMPARE) $(TEST).good $(TEST).emulator $(TEST)
