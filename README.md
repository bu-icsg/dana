```
               === ===
                \\ //
        ---      \ /
T H E  | X |  F I L E S
        ---      / \
                // \\
               === ===

  GILLIAN
  ANDERSON as DANA
```

## Overview

A neural network framework, hardware arbiter, and backend accelerator to accelerate neural network computation.

## Setup

This is not, at present, a standalone repository due to dependencies on classes and parameters defined in [rocket](https://www.github.com/ucb-bar/rocket), [uncore](https://www.github.com/ucb-bar/uncore), and [rocket-chip](https://www.github.com/ucb-bar/rocket-chip). Consequently, X-FILES/DANA cannot currently be tested outside of a rocket-chip environment. We intend to add this support eventually.

Consequently, you need to grab a copy of rocket-chip first:
```bash
git clone https://github.com/ucb-bar/rocket-chip
cd rocket-chip
git submodule update --init
```

Then clone a copy of this repository (xfiles-dana) inside the rocket-chip repo. We then symlink in the modules we care the files that rocket-chip needs to know about (`./install-symlinks`) and build some RISC-V test programs (`make rv`):
```bash
git clone git@github.com:seldridge/xfiles-dana
cd xfiles-dana
git submodule update --init
./install-symlinks
make rv
```

The test program `rsa-rocc-supervisor.rv` relies on special system calls to setup the X-Files arbiter. You need to patch and build a new proxy kernel to use these.
```bash
cd ../riscv-tools
git submodule update --init riscv-pk
git apply ../../xfiles-dana/patches/riscv-pk-xfiles-syscalls.patch
mkdir build
cd build
../configure --prefix=$RISCV/riscv-unknown-elf --host=riscv64-unknown-elf
make
```

We can then go back into the emulator directory and build a Rocket Chip with one Rocket Core and one X-Files/Dana instance:
```bash
cd ../../emulator
make CONFIG=XFilesDanaCPPConfig ROCKETCHIP_ADDONS=xfiles-dana
```

Using the proxy kernel that we just built, we can then run test programs built by running `make rv` in the xfiles-dana repo. The two test programs are a hello-world program (`hello.rv`) which does not exercise X-Files/Dana and a one-off test of an NN Transaction (`rsa-rocc-supervisor.rv`):
```bash
./emulator-Top-XFilesDanaCPPConfig ../riscv-tools/riscv-pk/build/pk ../xfiles-dana/build/hello.rv
./emulator-Top-XFilesDanaCPPConfig ../riscv-tools/riscv-pk/build/pk ../xfiles-dana/build/rsa-rocc-supervisor.rv
```
