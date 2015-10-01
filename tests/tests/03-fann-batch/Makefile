# This test deviates slightly from the more basic ones. Specifically,
# this needs to generate reproducible NNs with specific seeds and make
# sure that the MSE behaves exactly as we expect. Any modifications
# which improve X-Files/Dana will require regenerating the good data
# file.

TEST=fann-batch
# NETS_GEN=xorSigmoid xorSigmoidSymmetric xorSigmoidSymmetricPair \
# 	xorSigmoidSymmetricPairThreeLayer
NETS_GEN=xorSigmoidSymmetric
NETS_FLOAT=$(addsuffix -float.net, $(NETS_GEN))
NETS_TRAIN=$(addsuffix -fixed.train, $(NETS_GEN))
SOURCES=$(TEST).c
TARGETS=$(SOURCES:%.c=%.rv)
RUNS=$(addsuffix .emulator, $(NETS_GEN))
SEED=0
ASID=`shuf -i0-9 -n1`
NNID=`shuf -i0-9 -n1`

# Shared, wickedly fragile things
DIR_BUILD_NETS=.

include ../../common/Makefrag
include ../../../scripts/Makefrag

vpath %.train $(DIR_TOP)/src/main/resources
vpath %.train $(DIR_TOP)/submodules/fann/datasets

.PHONY: $(PHONY) run compare

.PRECIOUS: %-fixed.net %-float.net %-fixed.train %-fixed.16bin

all: $(RUNS)

#--------- NN build rules are in xfiles-dana/scripts/Makefrag

%.emulator: %-fixed.16bin %-fixed.train %-fixed.net $(TEST).rv %.good
	@ echo [INFO] $(TEST_NAME) seed/asid/nnid $(SEED)/$(ASID)/$(NNID)
	@ $(EMULATOR) +maxcycles=$(MAX_CYCLES) pk $(TEST).rv \
	-n $(basename $@)-fixed.16bin \
	-t $(basename $@)-fixed.train \
	-b `grep decimal_point $(word 3,$^) | sed 's/^.\+=//'` \
	-m -e100 -j$(ASID) -k$(NNID) > $(basename $@).emulator
	@ $(TOOL_COMPARE) $(basename $@).good \
	$(basename $@).emulator \
	$(TEST_NAME)-$(basename $@)

JUNK += *.net *.*bin *.train
