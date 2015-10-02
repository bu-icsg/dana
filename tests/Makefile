DIR_TESTS=tests

TESTS=$(shell ls $(DIR_TESTS))
DIR_EMULATOR=../../emulator
EMULATOR=$(DIR_EMULATOR)/emulator-Top-XFilesDanaCPPConfig

.PHONY: default go-for-it

default: $(addprefix $(DIR_TESTS)/,$(TESTS))

$(DIR_EMULATOR)/emulator-Top-%:
	@ (cd ../../emulator && $(MAKE) CONFIG=$* \
	ROCKETCHIP_ADDONS=xfiles-dana)

$(DIR_TESTS)/t-%: $(EMULATOR) go-for-it
	@ $(MAKE) -C $@ clean && $(MAKE) -C $@
