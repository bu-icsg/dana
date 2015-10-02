#!/usr/bin/bash

# Setup script, intended to be used by Jenkins, that will build the
# RISC-V toolchain specified by the commit ID of the riscv-tools
# submodule. The directory structure used is as follows:
#
#   /home/jenkins/
#            |--> jobs/
#                 |--> rocket-chip/workspace/
#                                       |--> riscv/
#                                       |--> rocket-chip/
#
# Jenkins uses a non-standard convention of cloning rocket-chip in a
# rocket-chip subdirectory of the workspace. This then allows us to
# define our own riscv directory where the toolchain will be built.

# Create the riscv directory if it doesn't already exist and setup the
# environment variables that we care about.
mkdir -p ../riscv
export RISCV=`readlink -f ../riscv`
echo RISCV ENV VAR is $RISCV
export PATH=$PATH:$RISCV/bin
echo PATH is $PATH

# Update the riscv-tools submodule. We're more careful about which
# submodules we update here (as opposed to getting Jenkins to do it)
# because we don't want to update everything (i.e., we don't need the
# fpga-zynq images which take a while to get). We then set the number
# of parllel jobs to something sane (the output of the
# `max-processors.sh` script) so that we don't wind up using the
# default value of 16.
git submodule update --init --recursive riscv-tools
cd riscv-tools
../xfiles-dana/usr/bin/max-processors.sh | \
    xargs -IX sh -c "sed -i 's/JOBS=\([0-9]\+\)/JOBS=X/' build.common"

# The proxy kernel needs to be patched to enable our special
# supervisor systemcalls that set the ASID and ASID--NNID Table
# pointer.
cd riscv-pk
git checkout .
git apply ../../xfiles-dana/patches/riscv-pk-xfiles-syscalls.patch
cd ..

# Build the toolchain.
./build.sh;

# The script will barf if we try to doubly patch the proxy kernel, so
# we remove the patch in prepartion for a subsequent build.
cd riscv-pk
git apply -R ../../xfiles-dana/patches/riscv-pk-xfiles-syscalls.patch
cd ../..

# Update only the submodules in the top level (e.g., uncore, rocket,
# hardfloat) and then dump out their status so that we can see if
# anything is acting weirdly in the logs.
git submodule update --init
git submodule status --recursive
