include $(abs_top_srcdir)/Makefrag

tests = \
	hello \
	dana-benchmark \
	debug-test \
	id \
	trap-00-new-request-no-asid \
	trap-00-supervisor-req-as-user \
	trap-00-write-register-no-asid \
	trap-01-request-antp-not-set \
	trap-02-request-oob-asid \
	trap-03-request-oob-nnid \
	trap-05-request-nn-config-zero-size \
	trap-06-request-invalid-epb

CFLAGS := $(CFLAGS) \
	-Wall \
	-Werror \
	-static \
	-I$(abs_top_srcdir)/..

tests_pk = $(addprefix $(PREFIX)-, $(tests))

all: $(tests_pk)

vpath %.c $(src_dir)
vpath %.h $(src_dir)

$(PREFIX)-trap-%: trap-%.c $(abs_top_srcdir)/libs/build/$(TARGET)/libxfiles-user-pk.a
	$(CC) $(CFLAGS) $< -o $@ $(LFLAGS) -lxfiles-user-pk
$(PREFIX)-dana-benchmark: dana-benchmark.c $(abs_top_srcdir)/libs/build/$(TARGET)/libxfiles-user-pk.a
	$(CC) $(CFLAGS) $< -o $@ $(LFLAGS) -lxfiles-user-pk
$(PREFIX)-id: id.c $(abs_top_srcdir)/libs/build/$(TARGET)/libxfiles-user-pk.a $(abs_top_srcdir)/libs/build/$(TARGET)/libxfiles-supervisor.a
	$(CC) $(CFLAGS) $< -o $@ $(LFLAGS) -lxfiles-user-pk -lxfiles-supervisor
$(PREFIX)-%: %.c $(XFILES_LIBRARIES) $(libfann_dep) $(abs_top_srcdir)/libs/build/$(TARGET)/libxfiles-user.a
	$(CC) $(CFLAGS) $< -o $@ $(LFLAGS) -lxfiles-user
$(PREFIX)-%.S: $(dir_build)/%.rv
	$(OBJDUMP) -S $< > $@

junk += $(tests_pk)
