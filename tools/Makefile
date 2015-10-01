TOOLS = fann-float-to-fixed \
write-fann-config-for-accelerator \
bin-config-to-c-header \
fann-train-to-c-header \
fann-random \
fann-train

.PHONY: all default

default: all

all: $(TOOLS)
	$(foreach tool, $(TOOLS), $(MAKE) -C ./$(tool);)

clean:
	$(foreach tool, $(TOOLS), $(MAKE) clean -C ./$(tool);)
