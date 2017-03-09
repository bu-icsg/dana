XLEN ?= 64

# Compute the ID String
ID_TTABLE ?= 1
EPB ?= 4
PES ?= 1
ID_CACHE ?= 2
ID_STRING ?= 0x$(shell echo "obase=16; $(ID_CACHE) + ($(PES) * (2 ^ 4)) + ($(EPB) * (2 ^ 10)) + ($(ID_TTABLE) * (2 ^ 48))" | bc)

default: all
src_dir = .

tests = $(patsubst %-fixed.ant.h,%,$(notdir $(wildcard $(src_dir)/*-fixed.ant.h)))

tests_p = $(addprefix $(PREFIX)-p-, $(tests))

RISCV_PREFIX ?= riscv$(XLEN)-unknown-elf
RISCV_GCC ?= $(RISCV_PREFIX)-gcc
RISCV_GCC_OPTS ?= \
	-static \
	-mcmodel=medany \
	-fvisibility=hidden \
	-nostdlib \
	-nostartfiles \
	-I$(abs_top_srcdir)/include \
	-I$(abs_top_srcdir) \
	-I$(src_dir) \
	-I$(abs_top_srcdir)/libs \
	-DID_STRING=$(ID_STRING)
RISCV_LD_OPTS ?= \
	-lxfiles-user \
	-lxfiles-supervisor \
	-L$(abs_top_srcdir)/libs/build/$(RISCV_PREFIX)
RISCV_OBJDUMP ?= \
	$(RISCV_PREFIX)objdump \
	--disassemble-all \
	--disassemble-zeroes \
	--section=.text \
	--section=.text.startup \
	--section=.text.init \
	--section=.data
RISCV_SIM ?= spike

HEADERS = $(shell find \
	$(abs_top_srcdir)/rocc-software/src \
	$(abs_top_srcdir)/../src/main/c)
HEADERS_P = $(HEADERS) $(shell find $(abs_top_srcdir)/env/p)
HEADERS_V = $(HEADERS) $(shell find $(abs_top_srcdir)/env/v)

all: $(tests_p)

vpath %.S $(src_dir)
vpath %.h $(src_dir)

$(PREFIX)-p-%: genericNetTest.S %-fixed.ant.h $(HEADERS_P) $(abs_top_srcdir)/libs/build/libxfiles-user.a $(abs_top_srcdir)/libs/build/libxfiles-supervisor.a
	$(RISCV_GCC) $(RISCV_GCC_OPTS) -I$(abs_top_srcdir)/env/p -include $*-fixed.ant.h $< $(RISCV_LD_OPTS) -T$(abs_top_srcdir)/env/p/link.ld -o $@

$(PREFIX)-p-%-smp: %.S $(HEADERS_P) $(abs_top_srcdir)/libs/build/libxfiles-user.a $(abs_top_srcdir)/libs/build/libxfiles-supervisor.a
	$(RISCV_GCC) $(RISCV_GCC_OPTS) -DSMP -I$(abs_top_srcdir)/env/p $< $(RISCV_LD_OPTS) -T$(abs_top_srcdir)/env/p/link.ld -o $@

$(PREFIX)-v-%: %.S $(HEADERS_V) $(asb_top_srcdir)/libs/build/libxfiles-user.a $(abs_top_srcdir)/libs/build/libxfiles-supervisor.a
	$(RISCV_GCC) $(RISCV_GCC_OPTS) -I$(abs_top_srcdir)/env/v $(RISCV_LD_OPTS) -T$(abs_top_srcdir)/env/v/link.ld $< -o $@

$(abs_top_srcdir)/libs/build/lib%.a:
	make -C $(abs_top_srcdir)/libs

junk += $(tests_p)

clean:
	rm -rf $(junk)
