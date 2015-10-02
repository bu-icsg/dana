#!/usr/bin/bash -xe

# Script to handle a Jenkins build

# Directory of things pulled in from the rocket-chip Jenkins build
# relative to the xfiles-dana workspace directory
# DIR_ROCKET_CHIP=../../rocket-chip/workspace/rocket-chip
DIR_RISCV=../../riscv
# DIR_XFILES=$DIR_ROCKET_CHIP/xfiles-dana

# Jump up one level and get a copy of rocket chip HEAD
# cd ../
# git init .
# if [[ -z `git remote | grep upstream` ]]; then
#     git remote add upstream https://github.com/ucb-bar/rocket-chip
# fi
# git pull upstream master

# Build the RISC-V tools from scratch
# mkdir -p ../riscv
export RISCV=`readlink -f $DIR_RISCV`
echo RISCV ENV VAR is $RISCV
export PATH=$PATH:$RISCV/bin
echo PATH is $PATH
# git submodule update --init --recursive riscv-tools
# cd riscv-tools
# ../xfiles-dana/usr/bin/max-processors.sh | \
#     xargs -IX sh -c "sed -i 's/JOBS=\([0-9]\+\)/JOBS=X/' build.common"
# cd riscv-pk
# git checkout .
# git apply ../../xfiles-dana/patches/riscv-pk-xfiles-syscalls.patch
# cd ..
# ./build.sh;
# cd riscv-pk
# git apply -R ../../xfiles-dana/patches/riscv-pk-xfiles-syscalls.patch
# cd ../..
# git submodule update --init
# git submodule status --recursive

# Now jump into xfiles-dana and run the regression tests
# cd $DIR_XFILES
# git submodule update --init
./install-symlinks
make rv
cd tests
./regress.sh
