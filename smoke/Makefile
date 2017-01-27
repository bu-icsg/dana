XLEN ?= 64

# Compute the ID String
ID_TTABLE ?= 1
EPB ?= 4
PES ?= 1
ID_CACHE ?= 2
ID_STRING ?= 0x$(shell echo "obase=16; $(ID_CACHE) + ($(PES) * (2 ^ 4)) + ($(EPB) * (2 ^ 10)) + ($(ID_TTABLE) * (2 ^ 48))" | bc)

default: all
src_dir = .

tests = \
	debug \
	id

tests_p = $(addprefix $(ROCC)-p-, $(tests))

RISCV_PREFIX ?= riscv$(XLEN)-unknown-elf-
RISCV_GCC ?= $(RISCV_PREFIX)gcc
RISCV_GCC_OPTS ?= -static -mcmodel=medany -fvisibility=hidden -nostdlib -nostartfiles -I$(abs_top_srcdir)/include -I$(abs_top_srcdir) -I$(abs_top_srcdir)/.. -DID_STRING=$(ID_STRING)
RISCV_OBJDUMP ?= $(RISCV_PREFIX)objdump --disassemble-all --disassemble-zeroes --section=.text --section=.text.startup --section=.text.init --section=.data
RISCV_SIM ?= spike

all: $(tests_p)

vpath %.S $(src_dir)

$(ROCC)-p-%: %.S
	$(RISCV_GCC) $(RISCV_GCC_OPTS) -I$(abs_top_srcdir)/env/p -T$(abs_top_srcdir)/env/p/link.ld $< -o $@

$(ROCC)-v-%: %.S
	$(RISCV_GCC) $(RISCV_GCC_OPTS) -I$(abs_top_srcdir)/env/v -T$(abs_top_srcdir)/env/v/link.ld $< -o $@

junk += $(tests_p)

clean:
	rm -rf $(junk)
