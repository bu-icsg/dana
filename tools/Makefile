TOOLS = fann-float-to-fixed \
write-fann-config-for-accelerator \
bin-config-to-c-header \
fann-train-to-c-header \
fann-random \
fann-train

DIR_TOP = $(abspath ..)

DIR_SRC  = src
DIR_BIN  = bin
DIR_INC  = $(DIR_SRC)/include
COMMA    = ,

BINARIES = $(TOOLS)
BINS     = $(addprefix $(DIR_BIN)/, $(BINARIES))
SOURCES  = fann-float-to-fixed.c
OBJECTS  = $(SOURCES:%.c=%.o)

INCLUDE_PATHS  = $(DIR_INC) $(DIR_TOP)/usr/include

GCC      = gcc
CFLAGS   = -Wall -Werror $(addprefix -I, $(INCLUDE_PATHS))

LIB_PATHS = $(DIR_TOP)/usr/lib
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

$(DIR_BIN)/write-fann-config-for-accelerator: $(DIR_BIN)/write-fann-config-for-accelerator.o
	$(GCC) $(CFLAGS) $^ $(LFLAGS_F) -o $@

$(DIR_BIN)/%: $(DIR_BIN)/%.o
	$(GCC) $(CFLAGS) $^ $(LFLAGS) -o $@

$(DIR_BIN)/%.o: %.c | $(DIR_BIN)
	$(GCC) $(CFLAGS) $< -c -o $@

$(DIR_BIN):
	mkdir -p $@

clean:
	rm -rf $(DIR_BIN)
