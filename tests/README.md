## Software Testing Environment for Dana
This contains a Rocket-attached suite of tests for verifying DANA's functionality based off of [`riscv-tests`](https://github.com/riscv/riscv-tests).

### Organization
Tests are organized into the following categories:

* [Smoke Tests](smoke) -- Low-level verification of all instructions. These are not intended to be comprehensive.
* [Neural Network Tests](nets) -- Inferences, learning, and simultaneously multi-processed (SMP) inference tests

Like with `riscv-tests`, the tests are intended to be built in varieties that use physical (`-p`) or virtual memory (`-v`). Currently, only the `-p` variants are built.

### Usage

```
mkdir build
cd build
../configure
make
```

You can then run one of these tests if you have the emulator:

```
$ROCKET_CHIP/emulator/emulator-rocketchip-XFilesDanaCppPe1Epb4Config smoke/<test>-p
```

You can run these through `spike`, but these will naturally fail as `spike` does not have an attached accelerator.
