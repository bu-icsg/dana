ROCKETCHIP ?= $(abspath ../..)

BIN_SUFFIX = $(shell echo "$(EPB) 4*p" | dc)bin

TORTURE_CONFIG ?= TORTURE_IS_UNSPECIFIED
TOP = $(ROCKETCHIP)
base_dir = $(ROCKETCHIP)

DIR_TESTS = $(abspath tests)
TESTS=$(shell ls $(DIR_TESTS))
DIR_EMULATOR=$(abspath $(ROCKETCHIP)/emulator)
EMULATOR=$(DIR_EMULATOR)/emulator-Top-$(CONFIG)

# The directory that the tools get built into.
RISCV ?= $(abspath install/$(TOOLS_HASH))

.PHONY: default go-for-it

default: $(addprefix $(DIR_TESTS)/,$(TESTS))

# $(DIR_TESTS)/t-%:  go-for-it
$(DIR_TESTS)/t-%: stamps/$(CONFIG)/emulator-ndebug.stamp go-for-it
	$(MAKE) -j 8 EMULATOR=$(EMULATOR) PATH="$(abspath $(RISCV)/bin:$(PATH))" RISCV=$(RISCV) -C $@

SUITE = __XFilesDana
CONFIGS = XFilesDanaCppPe1Epb4Config \
	XFilesDanaCppPe4Epb4Config \
	XFilesDanaCppPe16Epb16Config \
	XFilesDanaCppPe32Epb32Config
include $(ROCKETCHIP)/regression/Makefile

# $(DIR_EMULATOR)/emulator-Top-%: go-for-it
# 	@ (cd ../../emulator && make clean && make clean -C ../dramsim2 && \
# 	$(MAKE) CONFIG=$* \ ROCKETCHIP_ADDONS=xfiles-dana)

# $(DIR_TESTS)/t-%: $(EMULATOR) go-for-it
# 	@ $(MAKE) -C $@ clean && $(MAKE) -C $@
