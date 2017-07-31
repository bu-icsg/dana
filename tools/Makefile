DIR_TOP = $(abspath ..)
TARGET  = host

include ../Makefrag

DIR_SRC  = $(DIR_TOP)/tools/src
DIR_BIN  = $(DIR_TOP)/tools/bin
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
	fann-eval-fixed \
	fann-image
BINS     = $(addprefix $(DIR_BIN)/, $(TOOLS))

vpath %.c src

.PHONY: all clean

all: $(BINS)

include common/Makefrag-rv
include common/Makefrag-submodule

INCLUDE_PATHS  = $(DIR_TOP)

LIB_PATHS = \
	$(DIR_BUILD)/$(TARGET) \
	$(DIR_BUILD)/fann/$(TARGET) \
	$(DIR_TOP)/tests/libs/build/$(TARGET)
LDIRS = \
	$(addprefix -Wl$(COMMA)-R, $(shell echo $(LIB_PATHS) | xargs -n1 readlink -f)) \
	$(addprefix -L, $(LIB_PATHS))

.INTERMEDIATE: $(DIR_BIN)/fann-train-to-c-header.o \
	$(DIR_BIN)/fann-eval-fixed.o \
	$(DIR_BIN)/write-fann-config-for-accelerator.o \
	$(DIR_BIN)/fann-train-to-c-header.o \
	$(DIR_BIN)/fann-eval.o \
	$(DIR_BIN)/fann-train.o \
	$(DIR_BIN)/fann-random.o \
	$(DIR_BIN)/fann-float-to-fixed.o \
	$(DIR_BIN)/generate-ant.o \
	$(DIR_BIN)/fann-image.o

$(DIR_BIN)/generate-ant: $(DIR_BIN)/generate-ant.o $(DIR_TOP)/tests/libs/build/$(TARGET)/libxfiles-ant.a $(libfann_dep)
	$(CC) $(CFLAGS) $< $(LDIRS) -lxfiles-ant -o $@

# No pattern rules as I need to be explicit about what is linking
# against FANN since it's LGPLv2

# Fixed FANN
$(DIR_BIN)/fann-train-to-c-header-fixed: $(DIR_BIN)/fann-train-to-c-header.o $(libfann_dep)
	$(CC) $(CFLAGS) $< $(LDIRS) -lm -lfixedfann -fopenmp -o $@
$(DIR_BIN)/fann-eval-fixed: $(DIR_BIN)/fann-eval-fixed.o $(libfann_dep)
	$(CC) $(CFLAGS) $< $(LDIRS) -lm -lfixedfann -fopenmp -o $@
$(DIR_BIN)/write-fann-config-for-accelerator: $(DIR_BIN)/write-fann-config-for-accelerator.o $(libfann_dep)
	$(CC) $(CFLAGS) $< $(LDIRS) -lm -lfixedfann -fopenmp -o $@

# FANN
$(DIR_BIN)/fann-train-to-c-header: $(DIR_BIN)/fann-train-to-c-header.o $(libfann_dep)
	$(CC) $(CFLAGS) $< $(LDIRS) -lm -lfann -fopenmp -o $@
$(DIR_BIN)/fann-eval: $(DIR_BIN)/fann-eval.o $(libfann_dep)
	$(CC) $(CFLAGS) $< $(LDIRS) -lm -lfann -fopenmp -o $@
$(DIR_BIN)/fann-image: $(DIR_BIN)/fann-image.o $(libfann_dep)
	$(CC) $(CFLAGS) $< $(LDIRS) -lm -lfann -lpng -fopenmp -o $@
$(DIR_BIN)/fann-train: $(DIR_BIN)/fann-train.o $(libfann_dep)
	$(CC) $(CFLAGS) $< $(LDIRS) -lm -lfann -fopenmp -o $@
$(DIR_BIN)/fann-random: $(DIR_BIN)/fann-random.o $(libfann_dep)
	$(CC) $(CFLAGS) $< $(LDIRS) -lm -lfann -fopenmp -o $@
$(DIR_BIN)/fann-float-to-fixed: $(DIR_BIN)/fann-float-to-fixed.o $(libfann_dep)
	$(CC) $(CFLAGS) $< $(LDIRS) -lfann -o $@

$(DIR_BIN)/fann-eval-fixed.o: fann-eval.c | $(DIR_BIN)
	$(CC) $(CFLAGS) -DFIXEDFANN $< -c -o $@
$(DIR_BIN)/%.o: %.c | $(DIR_BIN)
	$(CC) $(CFLAGS) $< -c -o $@

$(DIR_BIN):
	mkdir -p $@

clean:
	rm -rf $(DIR_BIN)
