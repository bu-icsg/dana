DIR_TOP = $(abspath ..)
TARGET  = host

include ../Makefrag

DIR_SRC  = src
DIR_BIN  = bin
DIR_INC  = $(DIR_SRC)/include
COMMA    = ,

TOOLS = fann-float-to-fixed \
	write-fann-config-for-accelerator \
	bin-config-to-c-header \
	fann-train-to-c-header \
	fann-random \
	fann-train \
	generate-ant
BINARIES = $(TOOLS)
BINS     = $(addprefix $(DIR_BIN)/, $(BINARIES))
SOURCES  = fann-float-to-fixed.c
OBJECTS  = $(SOURCES:%.c=%.o)

INCLUDE_PATHS  = $(DIR_TOP)

CC      = gcc
CFLAGS   = -Wall -Werror --std=c99 $(addprefix -I, $(INCLUDE_PATHS))

LIB_PATHS = $(DIR_BUILD)/$(TARGET) $(DIR_BUILD)/fann/$(TARGET)
LIBS      = fann m
LIBS_F    = fixedfann m
LFLAGS    = $(addprefix -Wl$(COMMA)-R, $(shell readlink -f $(LIB_PATHS))) \
            $(addprefix -L, $(LIB_PATHS)) \
            $(addprefix -l, $(LIBS))
LFLAGS_F    = $(addprefix -Wl$(COMMA)-R, $(shell readlink -f $(LIB_PATHS))) \
            $(addprefix -L, $(LIB_PATHS)) \
            $(addprefix -l, $(LIBS_F))

vpath %.c src

.PHONY: all default clean

default: all

all: $(BINS)

$(DIR_BIN)/generate-ant: $(DIR_BIN)/generate_ant.o
	$(CC) $(CFLAGS) $^ $(LFLAGS) -lxfiles-supervisor -o $@

$(DIR_BIN)/write-fann-config-for-accelerator: $(DIR_BIN)/write-fann-config-for-accelerator.o
	$(CC) $(CFLAGS) $^ $(LFLAGS_F) -o $@

$(DIR_BIN)/%: $(DIR_BIN)/%.o
	$(CC) $(CFLAGS) $^ $(LFLAGS) -o $@

$(DIR_BIN)/%.o: %.c | $(DIR_BIN)
	$(CC) $(CFLAGS) $< -c -o $@

$(DIR_BIN):
	mkdir -p $@

clean:
	rm -rf $(DIR_BIN)
