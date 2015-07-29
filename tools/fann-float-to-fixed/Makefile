DIR_SRC  = src
DIR_BIN  = bin
DIR_INC  = $(DIR_SRC)/include
COMMA    = ,

BINARIES = fann-float-to-fixed
BINS     = $(addprefix $(DIR_BIN)/, $(BINARIES))
SOURCES  = fann-float-to-fixed.c
OBJECTS  = $(SOURCES:%.c=%.o)

INCLUDE_PATHS  = $(DIR_INC) ../../usr/include

GCC      = gcc
CFLAGS   = -Wall -Werror $(addprefix -I, $(INCLUDE_PATHS))

LIB_PATHS = ../../usr/lib
LIBS      = fann m
LFLAGS    = $(addprefix -Wl$(COMMA)-R, $(shell readlink -f $(LIB_PATHS))) \
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
