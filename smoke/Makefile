XLEN ?= 64

default: all
src_dir = .

tests = \
	debug

tests_p = $(addprefix $(ROCC)-p-, $(tests))

RISCV_PREFIX ?= riscv$(XLEN)-unknown-elf-
RISCV_GCC ?= $(RISCV_PREFIX)gcc
RISCV_GCC_OPTS ?= -static -mcmodel=medany -fvisibility=hidden -nostdlib -nostartfiles -I$(abs_top_srcdir)/include -I$(abs_top_srcdir) -I$(abs_top_srcdir)/..
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
