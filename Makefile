TARGET            ?= riscv64-unknown-elf
DIR_TOP           ?= $(abspath .)
DIR_BUILD         ?= $(DIR_TOP)/build/$(TARGET)

# RISC-V related options
ifeq "$(TARGET)" "host"
CFLAGS = -DNO_VM=1
else
TARGET_DASH = $(TARGET)-
endif
CC            = $(TARGET_DASH)gcc
CXX           = $(TARGET_DASH)g++
AR            = $(TARGET_DASH)ar
OBJDUMP       = $(TARGET_DASH)objdump

libs = \
	xfiles-user \
	xfiles-supervisor \
	xfiles-debug \
	xfiles-user-pk \
	xfiles-ant

CFLAGS += \
	-Wall \
	-Werror \
	-O3 \
	-static \
	--std=gnu11 \
	-I$(DIR_TOP) \
	-I$(DIR_BUILD)/nets
LFLAGS = -L$(DIR_BUILD)/$(TARGET) -L$(DIR_BUILD)/fann/$(TARGET)

vpath %.h $(DIR_TOP)/src/include
vpath %.c $(DIR_TOP)/src

.PHONY: all clean
.SUFFIXES:

all: $(libs:%=$(DIR_BUILD)/lib%.a)

LIBS = \
	$(DIR_BUILD)/libxfiles-user.a \
	$(DIR_BUILD)/libxfiles-supervisor.a \
	$(DIR_BUILD)/libxfiles-user-pk.a
$(DIR_BUILD)/libxfiles-user.a: \
	$(DIR_BUILD)/xfiles-user.o \
	$(DIR_BUILD)/xfiles-debug.o
$(DIR_BUILD)/libxfiles-supervisor.a: \
	$(DIR_BUILD)/xfiles-supervisor.o \
	$(DIR_BUILD)/xfiles-asid-nnid-table.o
$(DIR_BUILD)/libxfiles-user-pk.a: \
	$(DIR_BUILD)/xfiles-user.o \
	$(DIR_BUILD)/xfiles-user-pk.o \
	$(DIR_BUILD)/xfiles-supervisor.o \
	$(DIR_BUILD)/xfiles-asid-nnid-table.o \
	$(DIR_BUILD)/xfiles-debug.o
$(DIR_BUILD)/libxfiles-debug.a: \
	$(DIR_BUILD)/xfiles-debug.o
$(DIR_BUILD)/libxfiles-ant.a: \
	$(DIR_BUILD)/xfiles-asid-nnid-table.o

$(LIBS): | $(DIR_BUILD)
	$(AR) rcs $@ $^

$(DIR_BUILD)/%.o: %.c %.h | $(DIR_BUILD)
	$(CC) $(CFLAGS) -c $< -o $@

$(DIR_BUILD):
	mkdir -p $@

clean:
	rm -rf $(DIR_BUILD)
