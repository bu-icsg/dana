## Software Testing Environment for Dana
This contains a Rocket-attached suite of tests for verifying DANA's functionality based off of [`riscv-tests`](https://github.com/riscv/riscv-tests).

### Organization
Tests are organized into the following categories:

* [Smoke Tests](smoke) -- Bare metal verification of all instructions. These are not intended to be comprehensive.
* [Neural Network Tests](nets) -- Bare metal inference, learning, and simultaneously multi-processed (SMP) inference tests

Like with `riscv-tests`, the tests are intended to be built in varieties that use physical (`-p`) or virtual memory (`-v`). Currently, only the `-p` variants are built.

### Usage

This requires that certain submodules in the Rocket Chip hierarchy are provided:
```
cd $ROCKETCHIP_DIR
git submodule update --init --recursive
```

Build neural networks for DANA:
```
cd $DANA_DIR
make
```

Build all tests:
```
cd $DANA_DIR/tests
autoconf
mkdir build
cd build
../configure
make
```

You can then run one of these tests if you have the emulator:

```
$ROCKET_CHIP/emulator/emulator-rocketchip-XFilesDanaCppPe1Epb4Config smoke/<test>-p
```

Due to the fact that these are bare-metal, the output is not terrifically interesting (it's either pass/fail). Running with Chisel `printf` commands enabled or to generate a waveform produces a more verbose output.

You can run these through `spike`, but these will naturally fail as `spike` does not have an attached accelerator.
