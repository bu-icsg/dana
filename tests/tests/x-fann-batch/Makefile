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

include ../../common/Makefrag

vpath %.train $(DIR_TOP)/src/main/resources
vpath %.train $(DIR_TOP)/submodules/fann/datasets

.PHONY: $(PHONY) run compare

.PRECIOUS: %-fixed.net %-float.net %-fixed.train %-fixed.16bin

all: $(RUNS)

xorSigmoidSymmetric-float.net:
	@ $(FANN_RANDOM) -s$(SEED) \
	-n$(DIR_TOP)/src/main/resources/xorSigmoidSymmetric.train \
	-l2 -l3 -l1 -a5 -o5 $@ 2>&1 > /dev/null

%-fixed.net: %-float.net
	@ $(FLOAT_TO_FIXED) $< $@ 2>&1 > /dev/null

%-fixed.16bin: %-fixed.net
	@ $(WRITE_FANN_CONFIG) 16 $< $@ $(DECIMAL_POINT_OFFSET) 2>&1 > /dev/null

%-fixed.train: %.train %-fixed.net
	@ $(FANN_TRAIN_TO_FIXED) $< $@ `grep decimal_point $(word 2,$^) | sed 's/^.\+=//'` 2>&1 > /dev/null

%.emulator: %-fixed.16bin %-fixed.train %-fixed.net $(TEST).rv %.good
	@ $(EMULATOR) +maxcycles=$(MAX_CYCLES) pk $(TEST).rv \
	-n $(basename $@)-fixed.16bin \
	-t $(basename $@)-fixed.train \
	-b `grep decimal_point $(word 3,$^) | sed 's/^.\+=//'` \
	-m -e100 > $(basename $@).emulator
	@ $(TOOL_COMPARE) $(basename $@).good $(basename $@).emulator $(basename $@)

JUNK += *.net *.*bin *.train
