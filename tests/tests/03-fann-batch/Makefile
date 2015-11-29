# This test deviates slightly from the more basic ones. Specifically,
# this needs to generate reproducible NNs with specific seeds and make
# sure that the MSE behaves exactly as we expect. Any modifications
# which improve X-Files/Dana will require regenerating the good data
# file.

TEST=fann-batch
NETS_GEN=xorSigmoid xorSigmoidSymmetric xorSigmoidSymmetricPair
NETS_SKIP=xorSigmoidSymmetricPairThreeLayer
NETS_FLOAT=$(addsuffix -float.net, $(NETS_GEN))
NETS_TRAIN=$(addsuffix -fixed.train, $(NETS_GEN))
SOURCES=$(TEST).c
TARGETS=$(SOURCES:%.c=%.rv)
RUNS=$(addsuffix .msePattern, $(NETS_GEN)) \
	$(addsuffix .converge, $(NETS_GEN))
SEED=0
ASID=`shuf -i0-9 -n1`
NNID=`shuf -i0-9 -n1`
BIT_FAIL_LIMIT=0.05
MSE_CONVERGE=0.002
MAX_CONVERGE_EPOCHS=10000

# Shared, wickedly fragile things
DIR_BUILD_NETS=.

include ../../common/Makefrag
include ../../../scripts/Makefrag

vpath %.train $(DIR_TOP)/src/main/resources
vpath %.train $(DIR_TOP)/submodules/fann/datasets

.PHONY: $(PHONY) run compare

.PRECIOUS: %-fixed.net %-float.net %-fixed.train %-fixed.16bin %.rv

all: $(RUNS)

#--------- NN build rules are in xfiles-dana/scripts/Makefrag

# MSE tests -- These check that the MSE is following _the exact_
# pattern that it was previously. The output of a new run "*.mse" is
# compared to a known good example "*.mse-good".
%.msePattern: %-fixed.16bin %-fixed.train %-fixed.net $(TEST).rv %.mse-good
	@ echo [INFO] $(TEST_NAME)-$@ seed/asid/nnid $(SEED)/$(ASID)/$(NNID)
	@ $(EMULATOR) +maxcycles=$(MAX_CYCLES) pk $(TEST).rv \
	-n $*-fixed.16bin \
	-t $*-fixed.train \
	-m -e100 -j$(ASID) -k$(NNID) > $*.mse
	@ $(TOOL_COMPARE) $*.mse-good \
	$*.mse \
	$(TEST_NAME)-$@

# Convergence tests -- These check that the NNs converge to be under a
# specific bit fail limit or MSE after a long number of training
# epochs.
%.converge: %-fixed.16bin %-fixed.train %-fixed.net $(TEST).rv
	@ echo [INFO] $(TEST_NAME)-$@ seed/asid/nnid $(SEED)/$(ASID)/$(NNID)
	@ $(EMULATOR) +maxcycles=$(MAX_CYCLES) pk $(TEST).rv \
	-n $*-fixed.16bin \
	-t $*-fixed.train \
	-e$(MAX_CONVERGE_EPOCHS) -j$(ASID) -k$(NNID) \
	-f$(BIT_FAIL_LIMIT) -g$(MSE_CONVERGE) -l -m > $@
	@ tail -n 1 $@ | awk '{print $$7}' | \
	xargs -IX \
	sh -c "$(TOOL_HIT_MAX_EPOCHS) X $(MAX_CONVERGE_EPOCHS) $(TEST_NAME)-$@"

JUNK += *.net *.*bin *.train *.mse *.converge
