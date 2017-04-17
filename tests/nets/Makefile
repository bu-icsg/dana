include $(abs_top_srcdir)/Makefrag

_tests = $(notdir $(wildcard $(src_dir)/*-fixed.ant.h))
tests = \
	$(patsubst %-fixed.ant.h,%, $(_tests)) \
	$(patsubst %-fixed.ant.h,%-smp, $(_tests)) \
	$(patsubst %-fixed.ant.h,%-learn, $(_tests))

tests_p = $(addprefix $(PREFIX)-p-, $(tests))

CFLAGS := $(CFLAGS) \
	-static \
	-mcmodel=medany \
	-fvisibility=hidden \
	-nostdlib \
	-nostartfiles \
	-I$(abs_top_srcdir)/.. \
	-DID_STRING=$(ID_STRING)
LIBS := $(LIBS) \
	-lxfiles-user \
	-lxfiles-supervisor \

HEADERS = $(shell find \
	$(abs_top_srcdir)/rocc-software/src \
	$(abs_top_srcdir)/libs/src)

all: $(tests_p)

vpath %.S $(src_dir)
vpath %.h $(src_dir)

$(PREFIX)-p-%: genericNetTest.S %-fixed.ant.h $(HEADERS_P) $(abs_top_srcdir)/libs/build/libxfiles-user.a $(abs_top_srcdir)/libs/build/libxfiles-supervisor.a
	$(CC) $(CFLAGS) -I$(ENV_P) -include $(abs_top_srcdir)/nets/$*-fixed.ant.h $< $(LDFLAGS) -T$(ENV_P)/link.ld -o $@ $(LIBS)

$(PREFIX)-p-%-smp: genericNetTest.S %-fixed.ant.h $(HEADERS_P) $(abs_top_srcdir)/libs/build/libxfiles-user.a $(abs_top_srcdir)/libs/build/libxfiles-supervisor.a
	$(CC) $(CFLAGS) -DSMP -I$(ENV_P) -include $(abs_top_srcdir)/nets/$*-fixed.ant.h $< $(LDFLAGS) -T$(ENV_P)/link.ld -o $@ $(LIBS)

$(PREFIX)-p-%-learn: genericLearnTest.S %-fixed.ant.h $(HEADERS_P) $(abs_top_srcdir)/libs/build/libxfiles-user.a $(abs_top_srcdir)/libs/build/libxfiles-supervisor.a
	$(CC) $(CFLAGS) -I$(ENV_P) -include $(abs_top_srcdir)/nets/$*-fixed.ant.h $< $(LDFLAGS) -T$(ENV_P)/link.ld -o $@ $(LIBS)

$(PREFIX)-v-%: %.S $(HEADERS_V) $(asb_top_srcdir)/libs/build/libxfiles-user.a $(abs_top_srcdir)/libs/build/libxfiles-supervisor.a
	$(CC) $(CFLAGS) -I$(ENV_V) $(RISCV_LD_OPTS) -T$(ENV_V)/link.ld $(LDFLAGS) $< -o $@ $(LIBS)

junk += $(tests_p)
