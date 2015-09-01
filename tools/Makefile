TOOLS = dana-asc-power-performance \
fann-float-to-fixed \
write-fann-config-for-accelerator \
bin-config-to-c-header \
fann-train-to-c-header \
fann-random

.PHONY: all default

default: all

all: $(TOOLS)
	$(foreach tool, $(TOOLS), make -C ./$(tool);)

clean:
	$(foreach tool, $(TOOLS), make clean -C ./$(tool);)
