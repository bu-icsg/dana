# This test deviates slightly from the more basic ones. Specifically,
# this needs to generate reproducible NNs with specific seeds and make
# sure that the MSE behaves exactly as we expect. Any modifications
# which improve X-Files/Dana will require regenerating the good data
# file.

TEST=fann-xfiles
NETS_PATTERN=xorSigmoid \
	xorSigmoidSymmetric \
	xorSigmoidSymmetricPair \
	xorSigmoidSymmetricThreeLayer
NETS_CONVERGE=xor-sigmoid-4i \
	xor-sigmoid-8i \
	xor-sigmoid-4o \
	xor-sigmoid-8o
NETS_ALL=$(NETS_PATTERN) $(NETS_CONVERGE)
NETS_SKIP=xor-sigmoid-16i \
	xor-sigmoid-32i \
	xor-sigmoid-64i \
	xor-sigmoid-128i \
	xor-sigmoid-256i \
	xor-sigmoid-256o \
	xor-sigmoid-16o \
	xor-sigmoid-32o \
	xor-sigmoid-64o \
	xor-sigmoid-128o
NETS_FLOAT=$(addsuffix -float.net, $(NETS_ALL))
NETS_TRAIN=$(addsuffix -fixed.train, $(NETS_ALL))
SOURCES=$(TEST).c
TARGETS=$(SOURCES:%.c=%.rv)
RUNS=$(addsuffix .msePattern, $(NETS_PATTERN)) \
	$(addsuffix .converge, $(NETS_CONVERGE))
SEED=0
ASID=`shuf -i0-9 -n1`
NNID=`shuf -i0-9 -n1`
BIT_FAIL_LIMIT=0.1
MSE_CONVERGE=0.002
MAX_CONVERGE_EPOCHS=10000

include ../../common/Makefrag
include ../../../tools/common/Makefrag-nets

vpath %.train $(DIR_TOP)/src/main/resources
vpath %.train $(DIR_TOP)/submodules/fann/datasets

.PHONY: $(PHONY)

.PRECIOUS: %-fixed.net %-float.net %-fixed.train %-fixed.$(BIN_SUFFIX) %.rv

all: $(RUNS)

#--------- NN build rules are in xfiles-dana/scripts/Makefrag

# MSE tests -- These check that the MSE is following _the exact_
# pattern that it was previously. The output of a new run "*.mse" is
# compared to a known good example "*.mse-good".
%.msePattern: $(DIR_BUILD_NETS)/%-fixed.$(BIN_SUFFIX) $(DIR_BUILD_NETS)/%-fixed.train $(DIR_BUILD_NETS)/%-fixed.net $(TEST).rv %.mse-good
	@ echo [INFO] $(TEST_NAME)-$@ seed/asid/nnid $(SEED)/$(ASID)/$(NNID)
	@ $(EMULATOR) +maxcycles=$(MAX_CYCLES) pk $(TEST).rv \
	-n $(DIR_BUILD_NETS)/$*-fixed.$(BIN_SUFFIX) \
	-t $(DIR_BUILD_NETS)/$*-fixed.train \
	-m -e100 -j$(ASID) -k$(NNID) > $*.mse
	@ $(TOOL_COMPARE) $*.mse-good \
	$*.mse \
	$(TEST_NAME)-$@

# Convergence tests -- These check that the NNs converge to be under a
# specific bit fail limit or MSE after a long number of training
# epochs.
%.converge: $(DIR_BUILD_NETS)/%-fixed.$(BIN_SUFFIX) $(DIR_BUILD_NETS)/%-fixed.train $(DIR_BUILD_NETS)/%-fixed.net $(TEST).rv
	@ echo [INFO] $(TEST_NAME)-$@ seed/asid/nnid $(SEED)/$(ASID)/$(NNID)
	@ $(EMULATOR) +maxcycles=$(MAX_CYCLES) pk $(TEST).rv \
	-n $(DIR_BUILD_NETS)/$*-fixed.$(BIN_SUFFIX) \
	-t $(DIR_BUILD_NETS)/$*-fixed.train \
	-e$(MAX_CONVERGE_EPOCHS) -j$(ASID) -k$(NNID) \
	-f$(BIT_FAIL_LIMIT) -g$(MSE_CONVERGE) -l -m > $@
	@ tail -n 1 $@ | awk '{print $$7}' | \
	xargs -IX \
	sh -c "$(TOOL_HIT_MAX_EPOCHS) X $(MAX_CONVERGE_EPOCHS) $(TEST_NAME)-$@"

JUNK += *.net *.*bin *.train *.mse *.converge
