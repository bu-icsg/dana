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

First, you need to grab a copy of rocket-chip. I'm going to use the variable `$ROCKETCHIP` as the directory where you've cloned rocket-chip:
```bash
git clone https://github.com/ucb-bar/rocket-chip $ROCKETCHIP
cd $ROCKETCHIP
git submodule update --init
```

Then clone a copy of this repository (xfiles-dana) inside the rocket-chip repo. We then symlink in the files that rocket-chip needs to know about (`./install-symlinks`) and build some RISC-V test programs (`make rv`).
```bash
git clone git@github.com:seldridge/xfiles-dana $ROCKETCHIP/xfiles-dana
cd $ROCKETCHIP/xfiles-dana
git submodule update --init
./install-symlinks
make rv
```
The `install-symlinks` script adds a symlink into `$ROCKETCHIP/src/main/scala` which defines Rocket Chip configurations that include X-Files/Dana. These configurations, listed below, are used at various stages in the build process:
* XFilesDanaCPPConfig -- Used for emulation
* XFilesDanaFPGAConfig -- Used for "larger" development boards, like the Zedboard. This configuration is based on DefaultFPGAConfig
* XFilesDanaFPGASmallConfig -- A smaller FPGA configuration based on DefaultFPGASmallConfig

If you haven't already installed the RISC-V toolchain, you should go ahead and do that now. Instead of maintaining a separate toolchain, I usually just build whatever version is shipped with the rocket-chip repo. This build process relies on the existence of a `RISCV` environment variable, i.e., the location where the RISC-V tools will be installed. Some of the tests currently use special system calls added to the proxy kernel. The xfiles-dana repo includes a patch for the proxy kernel which will set this up for you. Note, if you already have a valid copy of the riscv-tools you can optionally just patch the proxy kernel, as done in the block following this.
```bash
cd $ROCKETCHIP/riscv-tools
git submodule update --init
cd riscv-pk
git apply ../../xfiles-dana/patches/riscv-pk-xfiles-syscalls.patch
cd ..
export RISCV=/home/se/research_local/riscv
./build.sh
```

If you just want to patch the proxy kernel, you can do the following. Note, it's important that you build the proxy kernel _in it's own build directory_.
```bash
cd $ROCKETCHIP/riscv-tools
git submodule update --init riscv-pk
cd riscv-pk
git apply ../../xfiles-dana/patches/riscv-pk-xfiles-syscalls.patch
mkdir build
cd build
../configure --prefix=$RISCV/riscv64-unknown-elf --host=riscv64-unknown-elf
make
make install
```

## C++ Emulation

We can then go back into the emulator directory and build a C++ emulator of a Rocket Chip with one Rocket Core and one X-Files/Dana instance. We have to tell the build process that we want to use the `XFilesDanaCPPConfig` configuration (defined in the symlink `src/main/scala/XFilesDanaConfigs.scala`) and that we have an "add-on" project called "xfiles-dana":
```bash
cd $ROCKETCHIP/emulator
make CONFIG=XFilesDanaCPPConfig ROCKETCHIP_ADDONS=xfiles-dana
```

Using the patched proxy kernel, we can then run the test programs (built by running `make rv` in the xfiles-dana repo). The two test programs are a hello-world program (`hello.rv`) which does not exercise X-Files/Dana and a one-off test of an NN Transaction (`rsa-rocc-supervisor.rv`) which does exercise X-Files/Dana. If you didn't patch the proxy kernel globally, you'll need to specify a full path to it here:
```bash
./emulator-Top-XFilesDanaCPPConfig pk ../xfiles-dana/build/hello.rv
./emulator-Top-XFilesDanaCPPConfig pk ../xfiles-dana/build/rsa-rocc-supervisor.rv
```

If everything works correctly here, you should see output like the following. The program `rsa-rocc-supervisor.rv` creates an ASID--NNID Table (in userspace), sets the ASID to 0x1, passes a pointer to the ASID--NNID Table to the X-Files Arbiter, and then generates a request to compute the output for neural network with NNID 0x4. A lot of the slowdown here that you initially experience is just the proxy kernel starting up. However, once the contents of the ASID--NNID Table start to print, everything should print out quickly. If there are any stops in the middle of this, X-Files/Dana is probably stuck. This could be a problem with your toolchain or it could be X-Files/Dana off in the weeds (which is likely as I haven't done testing other than this example).
```bash
se@se-lenovo:[schuyler=]~/research_local/rocket-chip/emulator$
> ./emulator-Top-XFilesDanaCPPConfig pk ../xfiles-dana/build/rsa-rocc-supervisor.rv
[INFO] After init, user sees ANTP as 0x2dbc0
[INFO] 0x2dbc0 <- Table Head
[INFO]   |-> 0x2dbc0: size:                     0x4
[INFO]       0x2dbc8: * entry:                  0x2dbe0
[INFO]         |-> [0] 0x2dbe0: num_configs:    0x11
[INFO]         |       0x2dbe4: num_valid:      0x0
[INFO]         |       0x2dbe8: asid_nnid:      0x2dc50
[INFO]         |       0x2dbf0: transaction_io: 0x2dce0
[INFO]         |         |-> 0x2dce0: header:   0x0
[INFO]         |         |   0x2dce8: * input:  0x2dd00
[INFO]         |         |   0x2dcf0: * output: 0x2dd30
[INFO]         |-> [1] 0x2dbf8: num_configs:    0x11
[INFO]         |       0x2dbfc: num_valid:      0x7
[INFO]         |       0x2dc00: asid_nnid:      0x2de80
[INFO]         |         |-> [0] 0x2de80: size:     0x5d2
[INFO]         |         |       0x2de88: * config: 0x2e920
[INFO]         |         |-> [1] 0x2de90: size:     0x2bc
[INFO]         |         |       0x2de98: * config: 0x317c0
[INFO]         |         |-> [2] 0x2dea0: size:     0x61e
[INFO]         |         |       0x2dea8: * config: 0x32db0
[INFO]         |         |-> [3] 0x2deb0: size:     0x9a4
[INFO]         |         |       0x2deb8: * config: 0x35eb0
[INFO]         |         |-> [4] 0x2dec0: size:     0x400
[INFO]         |         |       0x2dec8: * config: 0x3abe0
[INFO]         |         |-> [5] 0x2ded0: size:     0x42
[INFO]         |         |       0x2ded8: * config: 0x3cbf0
[INFO]         |         |-> [6] 0x2dee0: size:     0x5a
[INFO]         |         |       0x2dee8: * config: 0x3ce10
[INFO]         |       0x2dc08: transaction_io: 0x2df10
[INFO]         |         |-> 0x2df10: header:   0x0
[INFO]         |         |   0x2df18: * input:  0x2df30
[INFO]         |         |   0x2df20: * output: 0x2df60
[INFO]         |-> [2] 0x2dc10: num_configs:    0x11
[INFO]         |       0x2dc14: num_valid:      0x0
[INFO]         |       0x2dc18: asid_nnid:      0x2e0b0
[INFO]         |       0x2dc20: transaction_io: 0x2e140
[INFO]         |         |-> 0x2e140: header:   0x0
[INFO]         |         |   0x2e148: * input:  0x2e160
[INFO]         |         |   0x2e150: * output: 0x2e190
[INFO]         |-> [3] 0x2dc28: num_configs:    0x11
[INFO]         |       0x2dc2c: num_valid:      0x0
[INFO]         |       0x2dc30: asid_nnid:      0x2e2e0
[INFO]         |       0x2dc38: transaction_io: 0x2e370
[INFO]         |         |-> 0x2e370: header:   0x0
[INFO]         |         |   0x2e378: * input:  0x2e390
[INFO]         |         |   0x2e380: * output: 0x2e3c0
[INFO] Initiating new request with NNID 0x4
[INFO] X-Files Arbiter responded with TID 0x0
[INFO] TID 0x0 done!
[INFO] output[ 0]: 880
[INFO] output[ 1]: 377
[INFO] output[ 2]: 0
[INFO] output[ 3]: 1024
[INFO] output[ 4]: 1024
[INFO] output[ 5]: 1024
[INFO] output[ 6]: 1024
[INFO] output[ 7]: 0
[INFO] output[ 8]: 1024
[INFO] output[ 9]: 0
[INFO] output[10]: 1024
[INFO] output[11]: 1024
[INFO] output[12]: 0
[INFO] output[13]: 1024
[INFO] output[14]: 0
[INFO] output[15]: 1024
[INFO] output[16]: 1024
[INFO] output[17]: 0
[INFO] output[18]: 1024
[INFO] output[19]: 0
[INFO] output[20]: 1024
[INFO] output[21]: 1024
[INFO] output[22]: 0
[INFO] output[23]: 0
[INFO] output[24]: 0
[INFO] output[25]: 0
[INFO] output[26]: 0
[INFO] output[27]: 0
[INFO] output[28]: 0
[INFO] output[29]: 0
[INFO] Destroying ASID--NNID Table
```
