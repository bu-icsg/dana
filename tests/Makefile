DIR_TESTS=tests

TESTS=$(shell ls $(DIR_TESTS))
EMULATOR=../../emulator/emulator-Top-XFilesDanaCPPConfig

.PHONY: default go-for-it

default: $(addprefix $(DIR_TESTS)/,$(TESTS))

$(EMULATOR):
	@ (cd ../../emulator && $(MAKE) CONFIG=XFilesDanaCPPConfig \
	ROCKETCHIP_ADDONS=xfiles-dana)

$(DIR_TESTS)/t-%: $(EMULATOR) go-for-it
	@ $(MAKE) -C $@ clean && $(MAKE) -C $@
