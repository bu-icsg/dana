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

To get started, clone this repository inside of an existing rocket-chip clone (either directly or as a submodule) and then run `install_symlinks` to create the necessary links within the rocket-chip repo:
```bash
cd $ROCKET_CHIP_REPO
git clone git@github.com:seldridge/xfiles-dana
cd xfiles-dana
./install-symlinks
```

You can then build a C++ of FPGA rocket-chip that includes DANA by referencing one of the following configurations:
* XFilesDanaCPPConfig -- used for C++ simulation
* XFilesDanaFPGAConfig -- FPGA build using rocket-chip's default configuration
* XFilesDanaFPGASmallConfig -- FPGA build using rocket-ship's small configuration (no FPU and smaller caches)

```
THE  TRUTH  IS  OUT  THERE
```
