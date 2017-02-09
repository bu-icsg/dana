DIR_TOP = $(abspath ..)
TARGET  = host

include ../Makefrag

DIR_SRC  = src
DIR_BIN  = bin
DIR_INC  = $(DIR_SRC)/include
COMMA    = ,

TOOLS = \
	fann-float-to-fixed \
	write-fann-config-for-accelerator \
	bin-config-to-c-header \
	fann-train-to-c-header \
	fann-random \
	fann-train \
	generate-ant \
	fann-eval
BINARIES = $(TOOLS)
BINS     = $(addprefix $(DIR_BIN)/, $(BINARIES))
SOURCES  = fann-float-to-fixed.c
OBJECTS  = $(SOURCES:%.c=%.o)

vpath %.c src

.PHONY: all default clean

default: all

all: $(BINS)

include common/Makefrag-rv
include common/Makefrag-submodule

INCLUDE_PATHS  = $(DIR_TOP)

LIB_PATHS = \
	$(DIR_BUILD)/$(TARGET) \
	$(DIR_BUILD)/fann/$(TARGET) \
	$(DIR_TOP)/tests/libs/build/$(TARGET)
LIBS      = fann m
LIBS_F    = fixedfann m
LFLAGS    = $(addprefix -Wl$(COMMA)-R, $(shell readlink -f $(LIB_PATHS))) \
            $(addprefix -L, $(LIB_PATHS)) \
            $(addprefix -l, $(LIBS))
LFLAGS_F    = $(addprefix -Wl$(COMMA)-R, $(shell readlink -f $(LIB_PATHS))) \
            $(addprefix -L, $(LIB_PATHS)) \
            $(addprefix -l, $(LIBS_F))

$(DIR_BIN)/generate-ant: $(DIR_BIN)/generate-ant.o $(DIR_TOP)/tests/libs/build/$(TARGET)/libxfiles-ant.a $(DIR_BUILD)/fann/$(TARGET)/libfann.a
	$(CC) $(CFLAGS) $^ $(LFLAGS) -lxfiles-ant -o $@

$(DIR_BIN)/write-fann-config-for-accelerator: $(DIR_BIN)/write-fann-config-for-accelerator.o $(DIR_BUILD)/fann/$(TARGET)/libfann.a
	$(CC) $(CFLAGS) $^ $(LFLAGS_F) -fopenmp -o $@

$(DIR_BIN)/%: $(DIR_BIN)/%.o $(DIR_BUILD)/fann/$(TARGET)/libfann.a
	$(CC) $(CFLAGS) $^ $(LFLAGS) -fopenmp -o $@

$(DIR_BIN)/%.o: %.c | $(DIR_BIN)
	$(CC) $(CFLAGS) $< -c -o $@

$(DIR_BIN):
	mkdir -p $@

clean:
	rm -rf $(DIR_BIN)
