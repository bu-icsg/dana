#!/usr/bin/bash

mkdir -p ../riscv
export RISCV=`readlink -f ../riscv`
echo RISCV ENV VAR is $RISCV
export PATH=$PATH:$RISCV/bin
echo PATH is $PATH
git submodule update --init --recursive riscv-tools
cd riscv-tools
../xfiles-dana/usr/bin/max-processors.sh | \
    xargs -IX sh -c "sed -i 's/JOBS=\([0-9]\+\)/JOBS=X/' build.common"
cd riscv-pk
git checkout .
git apply ../../xfiles-dana/patches/riscv-pk-xfiles-syscalls.patch
cd ..
./build.sh;
cd riscv-pk
git apply -R ../../xfiles-dana/patches/riscv-pk-xfiles-syscalls.patch
cd ../..
git submodule update --init
git submodule status --recursive
