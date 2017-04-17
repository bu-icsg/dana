Dynamically Allocated Neural Network (DANA) Accelerator
========================================

A [Chisel3](https://github.com/ucb-bar/chisel3) implementation of a neural network accelerator, DANA, capable of supporting simultaneous inferences or learning _transactions_ originating from the same or different contexts [[1]](#cite-pact2015).
DANA integrates with the [RISC-V Rocket microprocessor](https://github.com/ucb-bar/rocket-chip) as a Rocket Custom Coprocessor (RoCC).

This is currently compatibile with [rocket-chip:f3299ae9](https://github.com/ucb-bar/rocket-chip/commit/f3299ae91d3f01d0349eb4746886e303e8fb1b41) -- an older rocket-chip version used by fpga-zynq.

### <a name="toc"></a> Table of Contents
- [Setup](#setup)
    - [1 - Clone the Rocket Chip Repository](#clone-the-rocket-chip-repo)
    - [2 - Build a RISC-V Toolchain](#riscv-toolchain)
- [Software Emulation](#emulation)
    - [Standalone Emulation](#emulation-standalone)
    - [Rocket + DANA Emulation](#emulation-rocket-chip)
    - [Debugging](#emulation-debugging)
        - [Printf Debugging](#printf-debugging)
        - [Waveform Debugging](#waveform-debugging)
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
- [Additional Documentation](#documentation)
    - [Attribution](#attribution)
    - [Doc Directory](#doc-directory)
    - [Publications](#publications)
    - [Workshop Presentations and Posters](#presentations-posters)
- [Contributors and Acknowledgments](#contributors-acknowledgments)

### <a name="setup"></a> Setup

Requirements:
* `python 3.X`
* `numpy`
* `scipy`
* All dependencies needed for the [RISC-V toolchain](https://www.github.com/riscv/riscv-tools)

1) <a name="clone-the-rocket-chip-repo"></a> Clone the Rocket Chip Repository
----------------------------------------
This is not, at present, a standalone repository and must be cloned inside of an existing Rocket Chip clone. The following will grab a supported version of rocket-chip and clone DANA inside of it:

```
git clone https://github.com/ucb-bar/rocket-chip $ROCKETCHIP_DIR
cd $ROCKETCHIP_DIR
git reset --hard f3299ae91d3f01d0349eb4746886e303e8fb1b41
git clone https://github.com/bu-icsg/dana
cd dana
git submodule update --init --recursive
```

2) <a name="riscv-toolchain"></a> Build the RISC-V Toolchain
----------------------------------------
This requires a supported version of the RISC-V toolchain. Go ahead and build the version of the toolchain pointed at by the rocket-chip repository. This requires setting the `RISCV` environment variable and satisfying any dependencies required to build the toolchain.

```
cd $ROCKETCHIP_DIR/riscv-tools
./build.sh
```

### <a name="emulation"></a> Emulation (Functional Verification)
This project uses [Chisel3](https://github.com/ucb-bar/chisel3) and [FIRRTL](https://github.com/ucb-barc/firrtl) for hardware design and Verilog generation.
The Verilog emitted by FIRRTL can then be tested either as a standalone accelerator or integrated with Rocket Chip.

#### <a name="emulation-standalone"></a> Standalone Emulation
This is currently WIP.

#### <a name="emulation-rocket-chip"></a> Rocket Chip Emulation
You can build a complete version of Rocket Chip that includes DANA in a RoCC socket.

You can build an emulator of Rocket + DANA using the rocket-chip make target inside the rocket-chip/emulator directory. The Makefile just needs to know what configuration we're using and that we have additional Chisel code located in the `dana` directory. Below we build a Rocket + DANA configuration with a DANA unit having 4 processing elements and using a block width of 4 32-bit elements:
```bash
cd $ROCKETCHIP/emulator
make CONFIG=XFilesDanaCppPe4Epb4Config ROCKETCHIP_ADDONS=xfiles-dana
```

We provide bare-metal test programs inside the [tests](tests) directory.

#### <a name="emulation-debugging"></a> Emulation Debugging

For debugging or running the emulator more verbosely, you have the option of either relying on Chisel's `printf` or building a version of the emulator that supports full VCD dumping.

##### <a name="printf-debugging"></a> Printf Debugging

Chisel's `printf` writes to STDERR, all `printf` statements are disabled by default. You can enable all Chisel-included `printf` commands with the `+verbose` option:

```
cd $ROCKETCHIP/emulator
./emulator-Top-XFilesDanaCppPe4Epb4Config +verbose [binary] 2>&1 | tee run.log
```

Note: Rocket Chip dumps information every cycle and it is often useful to grep for the exact `printf` that you're looking for.

##### <a name="waveform-debugging"></a> Waveform Debugging

You can build a "debug" version of the emulator (which provides full support for generating vcd traces  with:
```
cd $ROCKETCHIP/emulator
make debug
```

This creates a `*-debug` emulator which supports a `-v[FILE]` option for generating a VCD file, a `+start` option for starting VCD dumping at a specific cycle.

To further reduce the size of the VCD file we provide a tool that prunes a VCD file to only include signals in a specific module and it's children, [`vcd-prune`](https://github.com/IBM/hdl-tools/blob/master/scripts/vcd-prune). Example usage to only emit DANA signals:

```
cd $ROCKETCHIP_DIR/emulator
./emulator-Top-XFilesDanaCppPe4Epb4Config -v- [binary] 2>&1 | ../dana/util/hdl-tools/scripts/vcd-prune -m Dana > run.vcd
```

This waveform can then be viewed using GTKWave by building GTKWave locally and using a helper script to pre-populate the waveform window:

```
cd $ROCKETCHIP/emulator
make -C ../dana/util/hdl-tools gtkwave
../dana/util/hdl-tools/scripts/gtkwave-helper run.vcd
```

### <a name="hardware"></a> Hardware Evaluation

Rocket + DANA can be evaluated on a Zynq FPGA using the Berkeley-provided [`fpga-zynq`](https://github.com/ucb-bar/fpga-zynq) repository.

### <a name="known-issues"></a> Known Issues and WIP Features
There are a few remaining things that we're working on closing out which limit the set of available features.

#### <a name="configuration-size"></a> Configuration Size
Currently, the neural network configuration must fit completely in one of DANA's configuration cache memories. We plan to enable the ability for weight data to be loaded as needed for large configurations that do not wholly fit in a cache memory.

#### <a name="linux-support"></a> Linux Support
We're working on a full integration of the X-FILES supervisor library with the Linux kernel. Supervisor features are currently supported via system calls added to the [RISC-V Proxy Kernel](https://www.github.com/riscv/riscv-pk) via an included [patch](patches/riscv-pk-xfiles-syscalls.patch).

#### <a name="io-queues"></a> IO Queues
While neural network configurations are loaded from the memory of the microprocessor, all input and output data is transferred from Rocket to DANA hardware through the Rocket Custom Coprocessor (RoCC) register interface. We have plans to enable asynchronous transfer through in-memory queues.

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
The following people, while not mentioned in the commit log, have contributed directly or indirectly to the development of this work:
* [Jonathan Appavoo](http://www.cs.bu.edu/~jappavoo/jappavoo.github.com/index.html)
* [Amos Waterland](http://people.seas.harvard.edu/~apw/)
* [Tommy Unger](http://www.cs.bu.edu/~jappavoo/jappavoo.github.com/index.html)
* [Han Dong](http://cs-people.bu.edu/handong/)
* [Leila Delshad Tehrani](http:/www.bu.edu/icsg)

IBM contributions were funded by DARPA. Original work was funded by a NASA Space Technology Research Fellowship.
