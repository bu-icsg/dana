# Chisel/Scala configuration

include Makefrag

DIR_FANN        = $(DIR_TOP)/submodules/fann

# Common configuration
DIR_SRC_SCALA	= $(DIR_TOP)/src/main/scala
DIR_SRC_V	= $(DIR_TOP)/src/main/verilog
DIR_SRC_C       = $(DIR_TOP)/src/main/c
DIR_SRC_CPP	= $(DIR_TOP)/src/main/cpp
DIR_TEST_CPP	= $(DIR_TOP)/src/test/cpp
DIR_TEST_RV     = $(DIR_TOP)/src/test/rv
DIR_MAIN_RES    = $(DIR_TOP)/src/main/resources
SEED            = $(shell echo "$$RANDOM")
SEED            = 0

# Miscellaneous crap
COMMA    = ,

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

.PHONY: all clean checkstyle debug doc mrproper nets tags tools vcd

default: all

all: nets rv-tests

SBT ?= sbt
SBT_FLAGS ?=
checkstyle:
	env ROCKETCHIP_ADDONS=$(ROCKETCHIP_ADDONS) $(SBT) $(SBT_FLAGS) scalastyle test:scalastyle

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
	$(rocketchip_dir)/src/main/scala \
	$(rocketchip_dir)/chisel3 \
	$(DIR_TOP)/src/main/scala
TAGS_C = \
	$(rocketchip_dir)/csrc \
	$(rocketchip_dir)/xfiles-dana/tests \
	$(rocketchip_dir)/riscv-tools/riscv-fesvr/fesvr \
	$(rocketchip_dir)/riscv-tools/riscv-pk/pk \
	$(rocketchip_dir)/riscv-tools/riscv-pk/machine \
	$(rocketchip_dir)/riscv-tools/riscv-pk/bbl
TAGS_V = \
	$(DIR_TOP)/../vsrc
tags:
	find $(TAGS_SCALA) -name *.scala -exec ctags --output-format=etags {} +
	find $(TAGS_C) -exec ctags --append=yes --output-format=etags {} +
	find $(TAGS_V) -exec ctags --append=yes --output-format=etags {} +
	find $(TAGS_SCALA) -name *.scala -exec ctags {} +
	find $(TAGS_C) -exec ctags --append=yes {} +
	find $(TAGS_V) -exec ctags --append=yes {} +

#------------------- Utility Targets
clean:
	rm -rf $(DIR_BUILD)/*
	rm -rf target
	rm -f TAGS

mrproper: clean
	$(MAKE) clean -C $(DIR_TOP)/tools
