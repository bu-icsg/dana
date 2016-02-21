# Overview
A set of user and supervisor extensions (X-FILES software), hardware management of neural network "transactions" (X-FILES Hardware Arbiter), and a backend multi-transaction accelerator (DANA) to accelerate neural network computation. This system is intended to be used as an accelerator for a RISC-V microprocessor like [Rocket](https://www.github.com/ucb-bar/rocket-chip).

## Publications
S. Eldridge, A. Waterland, M. Seltzer, J. Appavoo, and Joshi, A., _Towards General Purpose Neural Network Computing_, in Proceedings of the International Conference on Parallel Architectures and Compilation Techniques (PACT), 2015.
* [Paper](http://people.bu.edu/schuye/files/pact2015-eldridge-paper.pdf)
* [Presentation](http://people.bu.edu/schuye/files/pact2015-eldridge-presentation.pdf)

### Workshop Presentations and Posters
S. Eldridge., T. Unger, M. Sahaya Louis, A. Waterland, M. Seltzer, J. Appavoo, and A. Joshi, _Neural Networks as Function Primitives: Software/Hardware Support with X-FILES/DANA_, Boston Area Architecture Workshop (BARC) 2016.
* [Paper](http://people.bu.edu/schuye/files/barc2016-eldridge-paper.pdf)
* [Presentation](http://people.bu.edu/schuye/files/barc2016-eldridge-presentation.pdf)
* [Poster](http://people.bu.edu/schuye/files/barc2016-eldridge-poster.pdf)

# Setup

## Grab a copy of rocket-chip
This is not, at present, a standalone repository due to dependencies on classes and parameters defined in [rocket](https://www.github.com/ucb-bar/rocket), [uncore](https://www.github.com/ucb-bar/uncore), [rocket-chip](https://www.github.com/ucb-bar/rocket-chip), and possibly others. Consequently, X-FILES/DANA cannot be tested outside of a rocket-chip environment.

First, you need to grab a copy of rocket-chip. I'm going to use the variable `$ROCKETCHIP` as the directory where you've cloned rocket-chip:
```bash
git clone https://github.com/ucb-bar/rocket-chip $ROCKETCHIP
cd $ROCKETCHIP
git submodule update --init
```

## Build the RISC-V toolchain
You then need to build a copy of the RISC-V toolchain (if you don't already have one available). We currently track whatever version of the toolchain that the rocket-chip repo points to. You may experience difficulties if you track something else, e.g., HEAD.

We deviate from a vanilla toolchain by defining new systemcalls inside the RISC-V [Proxy Kernel](https://www.github.com/riscv/riscv-pk) that allow user access to supervisor functions while we're in the process of finishing integration of X-FILES software with the Linux kernel. The `xfiles-dana` repo includes patches to enable these features in the Proxy Kernel.

So, to build a complete copy of the toolchain with a patched Proxy Kernel, you need to:
* Grab the RISC-V toolchain submodule and all its submodules
* Set the mandatory `RISCV` environment variable
* Patch the Proxy Kernel
* Build everything (this may take around 30 minutes)
```bash
cd $ROCKETCHIP/riscv-tools
git submodule update --init
cd riscv-pk
git apply ../../xfiles-dana/patches/riscv-pk-xfiles-syscalls.patch
cd ..
export RISCV=<PATH TO WHERE YOU WILL INSTALL THE TOOLCHAIN>
./build.sh
```

If you already have a toolchain and want to just build a patched Proxy Kernel, you can do the following:
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

Then clone a copy of this repository (`xfiles-dana`) inside the rocket-chip repo. We then symlink in the files that `rocket-chip` needs to know about (`./install-symlinks`) and build some RISC-V test programs (`make rv`).
```bash
git clone git@github.com:bu-icsg/xfiles-dana $ROCKETCHIP/xfiles-dana
cd $ROCKETCHIP/xfiles-dana
git submodule update --init
./install-symlinks
make rv
```

The `install-symlinks` script adds a symlink into `$ROCKETCHIP/src/main/scala` which defines Rocket Chip configurations that include X-FILES/DANA. These configurations live in `xfiles-dana/config`. Some of these configurations are listed below:
* XFilesDANACPPConfig -- Used for C++ emulation/testing
* XFilesDANAFPGAConfig -- Builds X-FILES/DANA alongside a "larger" Rocket core (includes an FPU and larger caches)

# C++ Emulation
All our functional verification and testing occurs using C++ models of Rocket + X-FILES/DANA. Steps to build and run the C++ model as well as some debugging help follows.

You can build a C++ emulator of Rocket + X-FILES/DANA using the rocket-chip make target inside the rocket-chip/emulator directory. The Makefile just needs to know what configuration we're using and that we have additional Chisel code located in the `xfiles-dana` directory:
```bash
cd $ROCKETCHIP/emulator
make CONFIG=XFilesDANACPPConfig ROCKETCHIP_ADDONS=xfiles-dana
```

With the patched Proxy Kernel, we can then run the test programs (built by running `make rv` in the `xfiles-dana` repo). The two test programs are a hello-world program (`hello.rv`) which does not exercise X-FILES/DANA and a one-off test running gradient descent/stochatic gradient descent (`fann-xfiles.rv`) which offloads FANN computation to X-FILES/DANA. If your patched Proxy Kernel is not in its "normal" place in the `RISCV` directory, you'll need to specify a full path to it.

You can run the hello world program with the following:
```bash
./emulator-Top-XFilesDANACPPConfig pk ../xfiles-dana/build/hello.rv
```

You should see the following logo:
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

The other program can take a lot of input options, but requires:
* A packed binary representation of a neural network
* A fixed point training file

Some of these are automatically generated by the top-level `Makefile` (with network configurations described in `tools/common/Makefrag`). An example run of `fann-xfiles` training using gradient descent for 100 epochs showing MSE every 10 epochs is as follows:

```bash
cd $ROCKETCHIP/emulator
./emulator-Top-XFilesDanaCppPe1Epb8Config pk ../xfiles-dana/build/fann-xfiles.rv \
  -n ../xfiles-dana/build/nets/xorSigmoidSymmetric-fixed.32bin \
  -t ../xfiles-dana/build/nets/xorSigmoidSymmetric-fixed.train \
  -e100 \
  -m10
./emulator-Top-XFilesDanaCPPConfig pk ../xfiles-dana/build/
```

You'll then get an output like the following showing the MSE decreasing as a function of the number of epochs:
``` bash
[INFO] Found binary point 14
[INFO] Done reading input file
[INFO] Computed learning rate is 0xb33
[STAT] epoch 0 id 0 bp 14 mse 1.03157693
[STAT] epoch 10 id 0 bp 14 mse 0.98926378
[STAT] epoch 20 id 0 bp 14 mse 0.96295700
[STAT] epoch 30 id 0 bp 14 mse 0.88830202
[STAT] epoch 40 id 0 bp 14 mse 0.71772405
[STAT] epoch 50 id 0 bp 14 mse 0.37129650
[STAT] epoch 60 id 0 bp 14 mse 0.19943481
[STAT] epoch 70 id 0 bp 14 mse 0.12536569
[STAT] epoch 80 id 0 bp 14 mse 0.07838210
[STAT] epoch 90 id 0 bp 14 mse 0.04855352
```

## C++ emulation debugging
At present, we don't have a good way to do full VCD debugging (i.e., dump every signal) due to the size of the combined Rocket + X-FILES/DANA system and the use of the Proxy Kernel (this takes some time to start up). As a result, the best way that we've found to do debugging of X-FILES/DANA is to use Chisel's builtin `printf()` function. You can use this to get the value of a signal on some condition or write whole "info" functions which show you the state of a module at every clock cycle. There are some gotchas here, though.

Chisel's `printf` writes to STDERR, all `printf` statements are disabled by default, and enabling your statements also causes rocket-chip to dump state information every cycle. To get around this, we use a standard convention of prepending any `printf` with "[INFO]" or "[ERROR]" while assertions will always start with "Assertion". We can then grep for these in the output and ignore everything from rocket-chip.

So, pipe STDERR to STDOUT (`2>&1`), enable printfs (`+verbose`), and grep for specific printfs `| grep "INFO\|WARE\|ERROR\|Assert"`. Using the example above, to dump `xfiles-dana` debug information:
```
cd $ROCKETCHIP/emulator
./emulator-Top-XFilesDanaCppPe1Epb8Config +verbose pk \
  ../xfiles-dana/build/fann-xfiles.rv \
  -n ../xfiles-dana/build/nets/xorSigmoidSymmetric-fixed.32bin \
  -t ../xfiles-dana/build/nets/xorSigmoidSymmetric-fixed.train \
  -e100 \
  -m10 2>&1 | grep "INFO\|ERROR\|WARN\|Assert"
```

## Regression testing
The tests directory contains infrastructure for running regression tests. This includes two new tests not discussed above: a "torture" test and a suite of tests that run XOR training using `fann-xfiles`.

The set of tests that use `fann-xfiles` run batch training for networks of different topology learning an XOR function. These tests check execution for correctness in two ways:
* Exact replication of MSE over 100 training epochs when compared against a known "good" MSE output
* Convergence (as defined by hitting either a bit fail limit or an MSE target)

# FPGA target
An FPGA is one possible target for the Verilog generated by Chisel. What follows is an abbreviated and modified version of what is described in `rocket-chip/fpga-zynq/README.md`.

## Verilog generation
Our FPGA target relies on some modifications to the fsim memory generator script. You first need to apply this patch:
```bash
cd $ROCKETCHIP
git apply xfiles-dana/patches/fpga-mem-gen-add-arbitrary-verilog.patch
```

You can then build the FPGA-based Verilog in a similar fashion to how you built the C++ emulator:
```baqsh
cd $ROCKETCHIP/fpga-zynq/zedboard
make rocket CONFIG=XFilesDANAFPGAConfig ROCKETCHIP_ADDONS=xfiles-dana
```

Note that this is equivalent to running `make verilog` in $ROCKETCHIP/fsim and copying the generated Verilog into src/verilog. Also, beware that the `Makefile` in `fpga-zynq/zedboard` does not seem to properly handle certain options, like "unconditionally remake all targets"/`-B`, and that the dependency tracking seems broken to me. Consequently, I've found that I may need to explicitly blow away specific files to get this to build in changes.

## Create a Vivado project
This requires that you have an install of Vivado (e.g., a node-locked license that comes with a Zedboard or access to a license server). You should only have to do this once for each configuration that you care about.

Note that Vivado requires ncurses-5. If you're running a rolling distribution (e.g., Arch), then you'll need to have ncurses-5 installed for Vivado to actually work. There's some discussion of this problem as it relates to Matlab here: [https://bbs.archlinux.org/viewtopic.php?id=203039](https://bbs.archlinux.org/viewtopic.php?id=203039). To get around this, there's an available Arch AUR package:
[https://aur.archlinux.org/packages/ncurses5-compat-libs](https://aur.archlinux.org/packages/ncurses5-compat-libs).

To generate a new Vivado project for your specific configuration, you can use:
```bash
cd $ROCKETCHIP/fpga-zynq/zedboard
make project CONFIG=XFilesDANAFPGAConfig
```

## Generate a Zynq project configuration
With the Verilog source available and a valid project, you can then generate a configuration (boot.bin) which can be used to get the Zynq FPGA configured and the ARM core booted.

First, however, I've found that it's good to prevent Vivado from flattening the design hierarchy. Normally, Vivado will synthesize the whole design, flatten everything (basically, put the entire design in a single module), and then optimize it. While this should produce better performance and a more compact design, I've had problems with Vivado taking a very long time to finish and often failing to place the design due to space constraints when flattening is enabled. I have a patch which will disable all flattening that you can optionally apply with:
```bash
cd $ROCKETCHIP/fpga-zynq
git apply ../xfiles-dana/patches/fpga-zynq-dont-flatten-hierarchy.patch
```

You can then generate a boot.bin with:
```bash
make fpga-images-zybo/boot.bin CONFIG=XFilesDANAFPGAConfig
```

## Load the SD card
With a new boot.bin in place, you can then copy this onto the SD card. The general procedure here is mount the SD card and then use the provided Makefile target to copy everything over, and unmeant the SD card. Your SD card device will vary:
```bash
sudo mount /dev/mmcblk0p1 /mnt
cd $ROCKETCHIP/fpga-zynq/zedboard
make load-sd SD=/mnt
sudo umount /mnt
```

If you need to make any modifications to the actual Linux filesystem that the ARM core will see, you can use the following:
```bash
cd $ROCKETCHIP/fpga-zynq/zedboard
make ramdisk-open
```
This creates a directory $ROCKETCHIP/fpga-zynq/zedboard/ramdisk where you can put whatever files you want. When finished, you copy all these changes into boot.bin and remove the ramdisk directory:

```bash
cd $ROCKETCHIP/fpga-zynq/zedboard
make ramdisk-close
sudo rm -rf ramdisk
```
