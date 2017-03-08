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
	fann-train-to-c-header-fixed \
	fann-random \
	fann-train \
	generate-ant \
	fann-eval \
	fann-eval-fixed
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
LFLAGS    = $(addprefix -Wl$(COMMA)-R, $(shell echo $(LIB_PATHS) | xargs -n1 readlink -f)) \
            $(addprefix -L, $(LIB_PATHS)) \
            $(addprefix -l, $(LIBS))
LFLAGS_F    = $(addprefix -Wl$(COMMA)-R, $(shell echo $(LIB_PATHS) | xargs -n1 readlink -f)) \
            $(addprefix -L, $(LIB_PATHS)) \
            $(addprefix -l, $(LIBS_F))

$(DIR_BIN)/generate-ant: $(DIR_BIN)/generate-ant.o $(DIR_TOP)/tests/libs/build/$(TARGET)/libxfiles-ant.a $(libfann_dep)
	$(CC) $(CFLAGS) $< $(LFLAGS) -lxfiles-ant -o $@

$(DIR_BIN)/write-fann-config-for-accelerator: $(DIR_BIN)/write-fann-config-for-accelerator.o $(libfann_dep)
	$(CC) $(CFLAGS) $< $(LFLAGS_F) -fopenmp -o $@

$(DIR_BIN)/%-fixed: $(DIR_BIN)/%-fixed.o $(libfann_dep)
	$(CC) $(CFLAGS) $< $(LFLAGS_F) -o $@

$(DIR_BIN)/fann-float-to-fixed: $(DIR_BIN)/fann-float-to-fixed.o $(libfann_dep)
	$(CC) $(CFLAGS) $< $(LFLAGS) -o $@

$(DIR_BIN)/%: $(DIR_BIN)/%.o $(libfann_dep)
	$(CC) $(CFLAGS) $< $(LFLAGS) -fopenmp -o $@

$(DIR_BIN)/fann-eval-fixed.o: fann-eval.c | $(DIR_BIN)
	$(CC) $(CFLAGS) -DFIXEDFANN $< -c -o $@

$(DIR_BIN)/%.o: %.c | $(DIR_BIN)
	$(CC) $(CFLAGS) $< -c -o $@

$(DIR_BIN):
	mkdir -p $@

clean:
	rm -rf $(DIR_BIN)
