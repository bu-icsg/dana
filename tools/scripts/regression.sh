#!/bin/bash -xe

# Script to handle regression testing of xfiles-dana. This script is
# intended to work in concert with `rocket-chip-setup.sh` which deals
# with grabbing all the submodules of the current rocket-chip master
# and building the RISC-V toolchain. The directory structure will look
# like:
#
#   /home/jenkins/
#            |--> jobs/
#                 |--> rocket-chip/workspace/
#                 |                     |--> riscv/
#                 |                     |--> rocket-chip/
#                 |                                 |--> xfiles-dana
#                 |--> xfiles-dana/workspace/
#                                       |--> [EMPTY]
#
# The workspace of this build is _technically_ in
# xfiles-dana/workspace, but all the actual work will be done in the
# workspace of the rocket-chip build, rocket-chip/workspace. Hence, we
# need to deal with everything being relative to that directory. This
# script, however, will be called _after_ a Jenkins moves us to the
# rocket-chip/workspace/xfiles-dana directory.

# Define a relative path to the RISC-V Toolchain
DIR_RISCV=../../riscv

# Setup the RISCV environment variable and add its binary directory to
# the path.
export RISCV=`readlink -f $DIR_RISCV`
echo RISCV ENV VAR is $RISCV
export PATH=$PATH:$RISCV/bin
echo PATH is $PATH

# Jenkins will recursively update xfiles-dana submodules, so we
# shouldn't have to do any setup there. Just create the symlinks
# inside rocket-chip (assuming they don't already exist), run the
# normal `make rv` target to see if anything related to generating NNs
# or libraries is broken, and then run the regression tests.
./install-symlinks
make rv
cd tests
./regress.sh
