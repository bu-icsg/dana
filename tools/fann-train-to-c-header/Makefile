DIR_SRC		= src
DIR_BIN		= bin
COMMA		= ,

BINARIES	= fann-train-to-c-header fann-train-to-c-header-fixed
BINS		= $(addprefix $(DIR_BIN)/, $(BINARIES))
SOURCES		= fann-train-to-c-header.c fann-train-to-c-header-fixed.c
OBJECTS		= $(addprefix $(DIR_BIN)/, $(SOURCES))

GCC		= gcc
INCLUDE_PATHS	= ../../usr/include
CFLAGS		= -Wall -Werror $(addprefix -I, $(INCLUDE_PATHS))

LIB_PATHS	= ../../usr/lib
LIBS		= fann m
LIBS_FIXED      = fixedfann m
LFLAGS		= $(addprefix -Wl$(COMMA)-R, $(shell readlink -f $(LIB_PATHS))) \
            $(addprefix -L, $(LIB_PATHS)) \
            $(addprefix -l, $(LIBS))
LFLAGS_FIXED    = $(addprefix -Wl$(COMMA)-R, $(shell readlink -f $(LIB_PATHS))) \
            $(addprefix -L, $(LIB_PATHS)) \
            $(addprefix -l, $(LIBS_FIXED))


vpath %.c src

.PHONY: all default clean

default: all

all: $(BINS)

$(DIR_BIN)/fann-train-to-c-header: $(DIR_BIN)/fann-train-to-c-header.o
	$(GCC) $(CFLAGS) $^ $(LFLAGS) -o $@

$(DIR_BIN)/fann-train-to-c-header-fixed: $(DIR_BIN)/fann-train-to-c-header-fixed.o
	$(GCC) $(CFLAGS) $^ $(LFLAGS_FIXED) -o $@

$(DIR_BIN)/%.o: %.c
	$(GCC) $(CFLAGS) $< -c -o $@

clean:
	rm -f $(DIR_BIN)/*
