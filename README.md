Dynamically Allocated Neural Network (DANA) Accelerator
========================================

A [Chisel3](https://github.com/ucb-bar/chisel3) implementation of a neural network accelerator, DANA, capable of supporting simultaneous inferences or learning _transactions_ originating from the same or different contexts [[1]](#cite-pact2015).
DANA integrates with the [RISC-V Rocket microprocessor](https://github.com/ucb-bar/rocket-chip) as a Rocket Custom Coprocessor (RoCC).

### <a name="toc"></a> Table of Contents
- [Setup](#setup)
    - [1 - Clone the Rocket Chip Repository](#clone-the-rocket-chip-repo)
    - [2 - Build a RISC-V Toolchain](#riscv-toolchain)
- [Software Emulation](#emulation)
    - [Standalone Emulation](#emulation-standalone)
    - [Rocket + DANA Emulation](#emulation-rocket-chip)
    - [Debugging](#emulation-debugging)
    - [Regression Testing](#regression-testing)
- [Hardware Evaluation](#hardware)
    - [FPGA Target](#fpga-target)
        - [1 - Verilog Generation](#verilog-generation)
        - [2 - Create a Vivado Project](#vivado-project)
        - [3 - Generate Zynq Configuration](#boot-bin)
        - [4 - Load the SD Card](#load-sd-card)
        - [5 - Test on the FPGA](#test-on-the-fpga)
- [Known Issues, WIP Features](#known-issues)
    - [Configuration Size](#configuration-size)
    - [Linux Support](#linux-support)
    - [IO Queues](#io-queues)
    - [Ability to Offload Configurations](#configuration-offload)
- [Additional Documentation](#documentation)
    - [Attribution](#attribution)
    - [Doc Directory](#doc-directory)
    - [Publications](#publications)
    - [Workshop Presentations and Posters](#presentations-posters)
- [Contributors and Acknowledgments](#contributors-acknowledgments)

### <a name="setup"></a> Setup

Requirements:
* `python 2.7 or python 3.X`
* `numpy`
* `scipy`
* All dependencies needed for the [RISC-V toolchain](https://www.github.com/riscv/riscv-tools)

1) <a name="clone-the-rocket-chip-repo"></a> Clone the Rocket Chip Repository
----------------------------------------
This is not, at present, a standalone repository due to dependencies on classes and parameters defined in [rocket](https://www.github.com/ucb-bar/rocket), [uncore](https://www.github.com/ucb-bar/uncore), [rocket-chip](https://www.github.com/ucb-bar/rocket-chip), and possibly others. Consequently, DANA cannot be tested outside of a rocket-chip environment.

First, you need to grab a copy of rocket-chip.
While you can use a bleeding edge rocket-chip from [Berkeley](https://github.com/ucb-bar/rocket-chip), we provide a stable version guaranteed to work with DANA in [our own rocket-chip repo](https://gitlab.com/the-lone-gunmen/rocket-chip).
Here, the variable `$ROCKETCHIP` is directory where you've cloned rocket-chip:
```bash
git clone https://gitlab.com/the-lone-gunmen/rocket-chip --branch=xfiles-dana $ROCKETCHIP
cd $ROCKETCHIP
git submodule update --init
```

2) <a name="riscv-toolchain"></a> Build the RISC-V Toolchain
----------------------------------------
You then need to build a copy of the RISC-V toolchain (if you don't already have one available). We currently track whatever version of the toolchain that the rocket-chip repo points to. You may experience difficulties if you track something else, e.g., HEAD.

We deviate from a vanilla toolchain by defining new systemcalls inside the RISC-V [Proxy Kernel](https://www.github.com/riscv/riscv-pk) that allow user access to supervisor functions while we're in the process of finishing integration of software with the Linux kernel. The `dana` repo includes patches to enable these features in the Proxy Kernel.

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
_Note_: Any failures resulting from running `./build.sh` related to the riscv-tests repository can be safely ignored.

Ensure that you have the toolchain available on your path, i.e., append `$RISCV/bin` to your path with the following command (or add this to your `~/.bashrc`:
``` bash
export PATH=$PATH:$RISCV/bin
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

### <a name="emulation"></a> Emulation (Functional Verification)

This project uses [Chisel3](https://github.com/ucb-bar/chisel3) and [FIRRTL](https://github.com/ucb-barc/firrtl) for hardware design and Verilog generation.
The Verilog emitted by FIRRTL can then be tested either as a standalone accelerator or integrated with Rocket Chip.

#### <a name="emulation-standalone"></a> Standalone Emulation
The standalone version of DANA uses the complete Rocket Chip environment, but treats [`XFilesTester`](src/main/scala/standalone/Standalone.scala#L64) as the top module.

To build a standalone version of DANA:
```
cd $ROCKETCHIP/xfiles-dana/emulator
make
```

You can optionally specify a test to build (e.g., `make TEST=t_myTest`) which will need to define a `main` function in `src/test/cpp/t_myTest.cpp`.

#### <a name="emulation-rocket-chip"></a> Rocket Chip Emulation
Alternatively, you can build a complete version of Rocket Chip that includes DANA in a RoCC socket.

You can build an emulator of Rocket + DANA using the rocket-chip make target inside the rocket-chip/emulator directory. The Makefile just needs to know what configuration we're using and that we have additional Chisel code located in the `xfiles-dana` directory. Below we build a Rocket + DANA configuration with a DANA unit having 4 processing elements and using a block width of 4 32-bit elements:
```bash
cd $ROCKETCHIP/emulator
make CONFIG=XFilesDanaCppPe4Epb4Config ROCKETCHIP_ADDONS=xfiles-dana
```

With the patched Proxy Kernel, we can then run the test programs. These can be built inside of the xfiles-dana repo:
``` bash
cd $ROCKETCHIP/xfiles-dana
make
```

This will build the test programs using the newlib toolchain (specified by the `TARGET=riscv64-unknown-elf` Makefile variable). If you want to build using the Linux-GNU toolchain (you must first have this installed) and can run `make TARGET=riscv64-unknown-linux-gnu`.

#### <a name="emulation-debugging"></a> Emulation Debugging
VCD debugging is currently only realistic for the Standalone Emulator (due to the overhead of booting an operating system). You can build a "debug" version of the standalone emulator with:
```
cd $ROCKETCHIP/xfiles-dana/emulator
make debug
```

This creates a `*-debug` emulator with a working `--trace` command line argument.

The standalone build environment supports additional build targets that helps to automate the process of running and inspecting waveforms with the following targets:
* `make vcd` -- This will generate a VCD file in `generated-src/<emulator>/dump.vcd`.
* `make gtkwave` -- This will generate a VCD file and open it using GTKWave. The first time that this is run, GTKWave will be built from `svn` sources.

At present, we don't have a good way to do VCD debugging using the Full System emulator (i.e., dump every signal) due to the size of the combined Rocket + DANA system and the use of the Proxy Kernel (this takes some time to start up). As a result, the best way that we've found to do debugging of DANA is to use Chisel's builtin `printf` function. You can use this to get the value of a signal on some condition or write whole "info" functions which show you the state of a module at every clock cycle. There are some gotchas here, though.

Chisel's `printf` writes to STDERR, all `printf` statements are disabled by default, and enabling your statements also causes rocket-chip to dump state information every cycle.
To pare this down, pipe STDERR to STDOUT (`2>&1`), enable printfs (`+verbose`), and grep to remove all rocket-chip per-cycle information:
```
cd $ROCKETCHIP/emulator
./emulator-Top-XFilesDanaCppPe4Epb4Config +verbose [binary] 2>&1 | grep -v ^C "INFO\|ERROR\|WARN\|Assert"
```

### <a name="hardware"></a> Hardware Evaluation

#### <a name="fpga-target"></a> FPGA Target
An FPGA is one possible target for the Verilog generated by Chisel. What follows is an abbreviated and modified version of what is described in `rocket-chip/fpga-zynq/README.md`.

1) <a name="verilog-generation"></a> Verilog generation
----------------------------------------
Our FPGA target relies on some modifications to the fsim memory generator script. You first need to apply this patch:
```bash
cd $ROCKETCHIP
git apply xfiles-dana/patches/fpga-mem-gen-add-arbitrary-verilog.patch
git apply xfiles-dana/patches/fpga-vsim-verilog-kludge.patch
```

You can then build the FPGA-based Verilog in a similar fashion to how you built the C++ emulator:
```baqsh
cd $ROCKETCHIP/fpga-zynq/zedboard
make rocket CONFIG=XFilesDanaFPGAConfig ROCKETCHIP_ADDONS=xfiles-dana
```

Note that this is equivalent to running `make verilog` in $ROCKETCHIP/fsim and copying the generated Verilog into src/verilog. Also, beware that the `Makefile` in `fpga-zynq/zedboard` does not seem to properly handle certain options, like "unconditionally remake all targets"/`-B`, and that the dependency tracking seems broken to me. Consequently, I've found that I may need to explicitly blow away specific files to get this to build in changes.

2) <a name="vivado-project"></a> Create a Vivado project
----------------------------------------
This requires that you have an install of Vivado (e.g., a node-locked license that comes with a Zedboard or access to a license server). You should only have to do this once for each configuration that you care about.

Note that Vivado requires ncurses-5. If you're running a rolling distribution (e.g., Arch), then you'll need to have ncurses-5 installed for Vivado to actually work. There's some discussion of this problem as it relates to Matlab here: [https://bbs.archlinux.org/viewtopic.php?id=203039](https://bbs.archlinux.org/viewtopic.php?id=203039). To get around this, there's an available Arch AUR package:
[https://aur.archlinux.org/packages/ncurses5-compat-libs](https://aur.archlinux.org/packages/ncurses5-compat-libs).

To generate a new Vivado project for your specific configuration, you can use:
```bash
cd $ROCKETCHIP/fpga-zynq/zedboard
make project CONFIG=XFilesDanaFPGAConfig
```

3) <a name="boot-bin"></a> Generate a Zynq project configuration
----------------------------------------
With the Verilog source available and a valid project, you can then generate a configuration (boot.bin) which can be used to get the Zynq FPGA configured and the ARM core booted.

First, however, I've found that it's good to prevent Vivado from flattening the design hierarchy. Normally, Vivado will synthesize the whole design, flatten everything (basically, put the entire design in a single module), and then optimize it. While this should produce better performance and a more compact design, I've had problems with Vivado taking a very long time to finish and often failing to place the design due to space constraints when flattening is enabled. I have a patch which will disable all flattening that you can optionally apply with:
```bash
cd $ROCKETCHIP/fpga-zynq
git apply ../xfiles-dana/patches/fpga-zynq-dont-flatten-hierarchy.patch
```

You can then generate a boot.bin with:
```bash
make fpga-images-zybo/boot.bin CONFIG=XFilesDanaFPGAConfig
```

4) <a name="load-sd-card"></a> Load the SD Card
----------------------------------------
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

5) <a name="test-on-the-fpga"></a> Test on the FPGA
----------------------------------------
This assumes that you are using the default configuration that the Berkeley [fpga-zynq repository](https://www.github.com/ucb-bar/fpga-zynq) provides. We currently use a more complicated implementation with three FPGAs attached to a server that we are in the process of documenting (see:
[fpga-setup.md](doc/fpga-setup.md) and [toolflow.md](doc/toolflow.md#fpga-tools)).

The ARM core running Linux has a default IP address of 192.168.1.5. Use `scp` to copy over whatever you need. You will likely need to update the front end server and the front end server shared library. Additionally, you'll need to replace to old proxy kernel with out patched version.

From there, you can run your strict software or DANA accelerated neural network workloads, e.g., you can run the same C++ emulation example from above, just a whole lot faster:
```
$ ./fesvr-zynq [pk (optional)] [binary]
```

### <a name="known-issues"></a> Known Issues and WIP Features
There are a few remaining things that we're working on closing out which limit the set of available features.

#### <a name="configuration-size"></a> Configuration Size
Currently, the neural network configuration must fit completely in one of DANA's configuration cache memories. We plan to enable the ability for weight data to be loaded as needed for large configurations that do not wholly fit in a cache memory.

#### <a name="linux-support"></a> Linux Support
We're working on a full integration of the X-FILES supervisor library with the Linux kernel. Supervisor features are currently supported via system calls added to the [RISC-V Proxy Kernel](https://www.github.com/riscv/riscv-pk) via an included [patch](patches/riscv-pk-xfiles-syscalls.patch).

#### <a name="io-queues"></a> IO Queues
While neural network configurations are loaded from the memory of the microprocessor, all input and output data is transferred from Rocket to DANA hardware through the Rocket Custom Coprocessor (RoCC) register interface. We have plans to enable asynchronous transfer through in-memory queues.

#### <a name="configuration-offload"></a> Ability to Offload Configurations
We don't currently support writeback of trained neural network configurations from DANA back to the memory of the microprocessor. Repeated user calls to use the same neural network configuration will, however, use the cached (and trained) neural network configuration on DANA.

### <a name="documentation"></a> Additional Documentation

Additional documentation can be found in the `xfiles-dana/doc` directory or in some of our publications.

#### <a name="attribution"></a> Attribution
If you use this for research, please cite the original PACT paper:
```
@inproceedings{eldridge2015,
  author    = {Schuyler Eldridge and
               Amos Waterland and
               Margo Seltzer and
               Jonathan Appavoo and
               Ajay Joshi},
  title     = {Towards General-Purpose Neural Network Computing},
  booktitle = {2015 International Conference on Parallel Architecture and Compilation,
               {PACT} 2015, San Francisco, CA, USA, October 18-21, 2015},
  pages     = {99--112},
  year      = {2015},
  url       = {http://dx.doi.org/10.1109/PACT.2015.21},
  doi       = {10.1109/PACT.2015.21},
  timestamp = {Wed, 04 May 2016 14:25:23 +0200},
  biburl    = {http://dblp.uni-trier.de/rec/bib/conf/IEEEpact/EldridgeWSAJ15},
  bibsource = {dblp computer science bibliography, http://dblp.org}
}
```

#### <a name="doc-directory"></a> Doc Directory
Specific documentation includes:
* [Binary Encodings and Data Structures](doc/binary-encodings-data-structures.md)
* [Debugging](doc/debugging.md)
* [FPGA Setup](doc/fpga-setup.md)
* [Timing](doc/timing.md)
* [Toolflow](doc/toolflow.md)
* [U-Boot](doc/u-boot.md)

#### <a name="publications"></a> Publications
<a name="cite-pact2015"></a> [1] S. Eldridge, A. Waterland, M. Seltzer, J. Appavoo, and A. Joshi, "Towards General Purpose Neural Network Computing", _in Proceedings of the International Conference on Parallel Architectures and Compilation Techniques (PACT)_. 2015.
* [Paper](http://people.bu.edu/schuye/files/pact2015-eldridge-paper.pdf)
* [Presentation](http://people.bu.edu/schuye/files/pact2015-eldridge-presentation.pdf)

<a name="cite-thesis"></a> [2] S. Eldridge, "Neural Network Computing Using On-Chip Accelerators", Boston University. 2016.
* [Thesis](http://people.bu.edu/joshi/files/thesis-eldridge.pdf)

#### <a name="presentations-posters"></a> Workshop Presentations and Posters
[3] S. Eldridge., T. Unger, M. Sahaya Louis, A. Waterland, M. Seltzer, J. Appavoo, and A. Joshi, "Neural Networks as Function Primitives: Software/Hardware Support with X-FILES/DANA", _Boston Area Architecture Workshop (BARC)_. 2016.
* [Paper](http://people.bu.edu/schuye/files/barc2016-eldridge-paper.pdf)
* [Presentation](http://people.bu.edu/schuye/files/barc2016-eldridge-presentation.pdf)
* [Poster](http://people.bu.edu/schuye/files/barc2016-eldridge-poster.pdf)

### <a name="contributors-acknowledgments"></a> Contributors and Acknowledgments
* [Mateja Putic](https://github.com/matejaputic)
The following people, while not mentioned in the commit log, have contributed directly or indirectly to the development of this work:
* [Jonathan Appavoo](http://www.cs.bu.edu/~jappavoo/jappavoo.github.com/index.html)
* [Amos Waterland](http://people.seas.harvard.edu/~apw/)
* [Tommy Unger](http://www.cs.bu.edu/~jappavoo/jappavoo.github.com/index.html)
* [Han Dong](http://cs-people.bu.edu/handong/)
* [Leila Delshad Tehrani](http:/www.bu.edu/icsg)

IBM contributions were funded by DARPA. Original work was funded by a NASA Space Technology Research Fellowship.
