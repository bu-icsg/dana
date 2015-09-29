DIR_TESTS=tests

TESTS=$(shell ls $(DIR_TESTS))

.PHONY: default go-for-it

default: $(addprefix $(DIR_TESTS)/,$(TESTS))

$(DIR_TESTS)/t-%: go-for-it
	make -B -C $@
