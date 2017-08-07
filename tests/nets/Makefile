include $(abs_top_srcdir)/Makefrag

_tests = $(notdir $(wildcard $(src_dir)/../../build/nets/*-fixed.ant.h))
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
vpath %.h $(src_dir)/../../build/nets

$(PREFIX)-p-%: genericNetTest.S $(top_build_dir)/%-fixed.ant.h $(HEADERS_P) $(abs_top_srcdir)/libs/build/$(TARGET)/libxfiles-user.a $(abs_top_srcdir)/libs/build/$(TARGET)/libxfiles-supervisor.a
	$(CC) $(CFLAGS) -I$(ENV_P) -include $(top_build_dir)/$*-fixed.ant.h $< $(LFLAGS) -T$(ENV_P)/link.ld -o $@ $(LIBS)

$(PREFIX)-p-%-smp: genericNetTest.S $(top_build_dir)/%-fixed.ant.h $(HEADERS_P) $(abs_top_srcdir)/libs/build/$(TARGET)/libxfiles-user.a $(abs_top_srcdir)/libs/build/$(TARGET)/libxfiles-supervisor.a
	$(CC) $(CFLAGS) -DSMP -I$(ENV_P) -include $(top_build_dir)/$*-fixed.ant.h $< $(LFLAGS) -T$(ENV_P)/link.ld -o $@ $(LIBS)

$(PREFIX)-p-%-learn: genericLearnTest.S $(top_build_dir)/%-fixed.ant.h $(HEADERS_P) $(abs_top_srcdir)/libs/build/$(TARGET)/libxfiles-user.a $(abs_top_srcdir)/libs/build/$(TARGET)/libxfiles-supervisor.a
	$(CC) $(CFLAGS) -I$(ENV_P) -include $(top_build_dir)/$*-fixed.ant.h $< $(LFLAGS) -T$(ENV_P)/link.ld -o $@ $(LIBS)

$(PREFIX)-v-%: %.S $(HEADERS_V) $(abs_top_srcdir)/libs/build/$(TARGET)/libxfiles-user.a $(abs_top_srcdir)/libs/build/$(TARGET)/libxfiles-supervisor.a
	$(CC) $(CFLAGS) -I$(ENV_V) $(RISCV_LD_OPTS) -T$(ENV_V)/link.ld $(LFLAGS) $< -o $@ $(LIBS)

junk += $(tests_p)
