DIR_SRC		= src
DIR_BIN		= bin
COMMA		= ,

BINARIES	= fann-train-to-c-header
BINS		= $(addprefix $(DIR_BIN)/, $(BINARIES))
SOURCES		= fann-train-to-c-header.c
OBJECTS		= $(SOURCES:%.c=%.o)

GCC		= gcc
INCLUDE_PATHS	= ../../usr/include
CFLAGS		= -Wall -Werror $(addprefix -I, $(INCLUDE_PATHS))

LIB_PATHS	= ../../usr/lib
LIBS		= fann m
LFLAGS		= $(addprefix -Wl$(COMMA)-R, $(shell readlink -f $(LIB_PATHS))) \
            $(addprefix -L, $(LIB_PATHS)) \
            $(addprefix -l, $(LIBS))

vpath %.c src

.PHONY: all default clean

default: all

all: $(BINS)

$(BINS): $(addprefix $(DIR_BIN)/, $(OBJECTS))
	$(GCC) $(CFLAGS) $^ $(LFLAGS) -o $@

$(DIR_BIN)/%.o: %.c
	$(GCC) $(CFLAGS) $< -c -o $@

clean:
	rm -f $(DIR_BIN)/*
