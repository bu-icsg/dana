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

Then clone a copy of this repository (xfiles-dana) inside the rocket-chip repo. We then symlink in the files that rocket-chip needs to know about (`./install-symlinks`) and build some RISC-V test programs (`make rv`).
```bash
git clone git@github.com:seldridge/xfiles-dana
cd xfiles-dana
git submodule update --init
./install-symlinks
make rv
```
The `install-symlinks` script adds a symlink into `src/main/scala` which defines Rocket Chip configurations that include X-Files/Dana. These configurations, listed below, are used at various stages in the build process:
* XFilesDanaCPPConfig -- Used for emulation
* XFilesDanaFPGAConfig -- Used for "larger" development boards, like the Zedboard. This configuration is based on DefaultFPGAConfig
* XFilesDanaFPGASmallConfig -- A smaller FPGA configuration based on DefaultFPGASmallConfig

If you haven't already installed the RISC-V toolchain, you should go ahead and do that now. Instead of maintaining a separate toolchain, I usually just build whatever version is shipped with the rocket-chip repo. This build process relies on the existence of a `RISCV` environment variable, i.e., the location where the RISC-V tools will be installed. Some of the tests currently use special system calls added to the proxy kernel. The xfiles-dana repo includes a patch for the proxy kernel which will set this up for you. Note, if you already have a valid copy of the riscv-tools you can optionally just patch the proxy kernel, as done in the block following this.
```bash
git submodule update --init
cd riscv-pk
git apply ../../xfiles-dana/patches/riscv-pk-xfiles-syscalls.patch
cd ..
export RISCV=/home/se/research_local/riscv
./build.sh
```

If you just want to patch the proxy kernel, you can do the following.
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

Using the proxy kernel that we just built, we can then run test programs built by running `make rv` in the xfiles-dana repo. The two test programs are a hello-world program (`hello.rv`) which does not exercise X-Files/Dana and a one-off test of an NN Transaction (`rsa-rocc-supervisor.rv`). If you didn't patch the proxy kernel globally, you'll need to specify a full path to it here:
```bash
./emulator-Top-XFilesDanaCPPConfig pk ../xfiles-dana/build/hello.rv
./emulator-Top-XFilesDanaCPPConfig pk ../xfiles-dana/build/rsa-rocc-supervisor.rv
```
