# Chisel/Scala configuration

include Makefrag

DIR_FANN        = $(DIR_TOP)/submodules/fann

# Common configuration
SHELL           = /bin/bash
JOBS            = `$(DIR_TOP)/usr/bin/max-processors.sh`
DIR_SRC_SCALA	= $(DIR_TOP)/src/main/scala
DIR_SRC_V	= $(DIR_TOP)/src/main/verilog
DIR_SRC_C       = $(DIR_TOP)/src/main/c
DIR_SRC_CPP	= $(DIR_TOP)/src/main/cpp
DIR_TEST_V	= $(DIR_TOP)/src/test/verilog
DIR_TEST_CPP	= $(DIR_TOP)/src/test/cpp
DIR_TEST_RV     = $(DIR_TOP)/src/test/rv
DIR_MAIN_RES    = $(DIR_TOP)/src/main/resources
SEED            = $(shell echo "$$RANDOM")
SEED            = 0

# Miscellaneous crap
COMMA    = ,

# RISCV Tests Targets
RV_TESTS             = hello.c \
	fann-xfiles.c \
	fann-soft.c \
	read-xfiles-dana-id.c \
	trap-00-new-request-no-asid.c \
	trap-00-write-register-no-asid.c \
	trap-00-supervisor-req-as-user.c \
	trap-01-request-antp-not-set.c \
	trap-02-request-oob-asid.c \
	trap-03-request-oob-nnid.c \
	trap-05-request-nn-config-zero-size.c \
	trap-06-request-invalid-epb.c \
	dana-benchmark.c \
	debug-test.c \
	antw-config.c
RV_TESTS_EXECUTABLES = $(RV_TESTS:%.c=$(DIR_BUILD)/$(TARGET)/%.rv)
RV_TESTS_DISASM      = $(RV_TESTS:%.c=$(DIR_BUILD)/$(TARGET)/%.rvS)

# X-FILES libraries related
xfiles_libraries = \
	libxfiles-user.a \
	libxfiles-user-pk.a \
	libxfiles-supervisor.a \
	libxfiles-ant.a
XFILES_LIBRARIES = $(xfiles_libraries:%=$(DIR_TOP)/tests/libs/build/$(TARGET)/%)

DECIMAL_POINT_OFFSET=7
DECIMAL_POINT_BITS=3
MAX_DECIMAL_POINT=`echo "2 $(DECIMAL_POINT_BITS)^1-$(DECIMAL_POINT_OFFSET)+p"|dc`

vpath %.scala $(DIR_SRC_SCALA)
vpath %.cpp $(DIR_TEST_CPP)
vpath %.cpp $(DIR_BUILD)
vpath %.c $(DIR_SRC_C)
vpath %.c $(DIR_TEST_RV)
vpath %.h $(DIR_SRC_C)
vpath %.v $(DIR_TEST_V)
vpath %.v $(DIR_SRC_V)
vpath %.v $(DIR_BUILD)
vpath %-float.net $(DIR_MAIN_RES)

.PHONY: all clean checkstyle cpp debug doc dot lib mrproper nets run \
	run-verilog tags tools vcd vcd-verilog verilog

default: all

all: tests nets

checkstyle:
	env ROCKETCHIP_ADDONS=$(ROCKETCHIP_ADDONS) $(SBT) $(SBT_FLAGS) scalastyle test:scalastyle

cpp: $(BACKEND_CPP)

dot: $(BACKEND_DOT)

verilog: $(BACKEND_VERILOG)

vcd: $(DIR_BUILD)/t_XFilesDana$(CHISEL_CONFIG_DOT).vcd Makefile
	scripts/gtkwave $<

run: $(TEST_EXECUTABLES) Makefile
	$< $(<:$(DIR_BUILD)/t_%=$(DIR_BUILD)/%.prm)

run-verilog: $(TEST_V_EXECUTABLES) Makefile
	vvp $<

vcd-verilog: $(DIR_BUILD)/t_XFilesDana$(FPGA_CONFIG_DOT)-vcd.vvp Makefile
	vvp $<
	scripts/gtkwave $<.vcd

debug: $(TEST_EXECUTABLES) Makefile
	$< -d $(<:$(DIR_BUILD)/t_%=$(DIR_BUILD)/%.prm)

tests: $(RV_TESTS_EXECUTABLES)

disasm: $(RV_TESTS_DISASM)

host: $(XFILES_LIBRARIES_HOST)

include $(DIR_TOP)/tools/common/Makefrag-rv
include $(DIR_TOP)/tools/common/Makefrag-submodule
include $(DIR_TOP)/tools/common/Makefrag-tools
include $(DIR_TOP)/tools/common/Makefrag-nets
include $(DIR_TOP)/tools/common/Makefrag-video

nets: $(NETS_BIN) $(TRAIN_FIXED) $(NETS_TEST)
tools: $(NETS_TOOLS)

#--------- Generate ScalaDoc documentation
doc: | $(DIR_BUILD)/doc
	scaladoc src/main/scala/*.scala ../*/src/main/scala/*.scala ../src/main/scala/* -d $(DIR_BUILD)/doc

$(DIR_BUILD)/doc:
	mkdir -p $@

#------------------- Miscellaneous
TAGS_SCALA = \
	$(DIR_TOP)/../src/main/scala $(DIR_TOP)/../chisel3 \
	$(DIR_TOP)/src/main/scala
TAGS_C = \
	$(DIR_TOP)/src/main/c \
	$(DIR_TOP)/src/test/rv \
	$(DIR_TOP)/src/main/resources \
	$(DIR_TOP)/tests/smoke \
	$(DIR_TOP)/tests/rocc-software/src \
	$(DIR_TOP)/tests/env/
tags:
	find $(TAGS_SCALA) -name *.scala -exec ctags --output-format=etags {} +
	find $(TAGS_C) -exec ctags --append=yes --output-format=etags {} +
	find $(TAGS_SCALA) -name *.scala -exec ctags {} +
	find $(TAGS_C) -exec ctags --append=yes {} +

#------------------- Utility Targets
clean:
	rm -rf $(DIR_BUILD)/*
	rm -rf target
	rm -f TAGS

mrproper: clean
	$(MAKE) clean -C $(DIR_TOP)/tools
