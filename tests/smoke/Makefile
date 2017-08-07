include $(abs_top_srcdir)/Makefrag

tests = \
	debug \
	id \
	csr

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

$(PREFIX)-p-%: %.S $(HEADERS_P) $(abs_top_srcdir)/libs/build/$(TARGET)/libxfiles-user.a $(abs_top_srcdir)/libs/build/$(TARGET)/libxfiles-debug.a
	$(CC) $(CFLAGS) -I$(ENV_P) -T$(ENV_P)/link.ld $< $(LFLAGS) -o $@ $(LIBS)

$(PREFIX)-v-%: %.S $(HEADERS_V) $(abs_top_srcdir)/libs/build/$(TARGET)/libxfiles-user.a $(abs_top_srcdir)/libs/build/$(TARGET)/libxfiles-debug.a
	$(CC) $(CFLAGS) -I$(ENV_V) -T$(ENV_V)/link.ld $(LFLAGS) $< -o $@ $(LIBS)

junk += $(tests_p)
