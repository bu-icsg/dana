# Rough Guide to Debugging

Within the rocket-chip repository there are directories for Chisel backends:
* emulator -- C++ model
* fsim -- FPGA-syntehsizable Verilog
* vsim -- VLSI-compatible / VCS simulated Verilog

For most of our debugging we use the C++ backend.

## Git Setup for Clearing Issues
I create a new branch named after the issue and then switch to that:

```
git branch issue-54-incremental-learning
git checkout issue-54-incremental-learning
```

## Building the C++ Model

There are two Chisel sources used to configure a build of the C++ model:
* src/main/scala/Configs.scala
* configs/XFilesDanaConfigs.scala

The XFilesDanaConfigs.scala is known to the rocket-chip repository by symlinking this into rocket-chip/src/main/scala.

Looking in XFilesDanaConfigs.scala, we can select one of the C++ configurations to build, e.g., `XFilesDanaCppPe1Epb4Config`, and build it:

```
cd rocket-chip/emulator
make CONFIG=XFilesDanaCppPe1Epb4Config ROCKETCHIP_ADDONS=xfiles-dana
```

In the event that you have more than one addon, you can specify these with a comma delimited list.

The name of the emulator will include the configuration. For the code above we should now have a `emulator-Top-XFilesDanaCppPe1Epb4Config` in the rocket-chip/emulator directory. Once built, you can run a test program with:

```
./emulator-Top-XFilesDanaCppPe1Epb4Config pk ../xfiles-dana/build/hello.rv
```

Similarly, you can run any program that works on the FPGA, like `fann-xfiles`:

```
./emulator-Top-XFilesDanaCppPe1Epb4Config pk ../xfiles-dana/build/fann-xfiles.rv \
    -n../xfiles-dana/build/nets/xorSigmoidSymmetric-fixed.16bin \
    -t ../xfiles-dana/build/nets/xorSigmoidSymmetric-fixed.train -m -e10
```

However, this does not provide any more information than you get from the FPGA, so we need to dump additional debug information. Any `printf` that you put inside of Chisel code translates to statement that will print when you run an emulator with the `+verbose` option. Unfortunately, this dumps a ton of information from rocket-chip, so I use a standard convention for my own `printf` statements that can be selected using `grep`. Furthermore, all the `+verbose` output prints on STDERR, so you need to redirect this to STDOUT in order to actually run in through `grep`. My specific convention is to prepend every `printf` with one of the following:
* `[INFO]`
* `[WARN]`
* `[ERROR]`

To catch assertions also look for `Assert`. To do all this, the following command works:

```
./emulator-Top-XFilesDanaCppPe1Epb4Config +verbose pk \
    ../xfiles-dana/build/fann-xfiles.rv
    -n ../xfiles-dana/build/nets/xorSigmoidSymmetric-fixed.16bin \
    -t ../xfiles-dana/build/nets/xorSigmoidSymmetric-fixed.train \
    -m -e2 -x 2>&1 | grep "INFO\|WARN\|ERROR\|Assert"
```

We can also send this to a file for better analysis:

```
./emulator-Top-XFilesDanaCppPe1Epb4Config +verbose pk \
    ../xfiles-dana/build/fann-xfiles.rv
    -n ../xfiles-dana/build/nets/xorSigmoidSymmetric-fixed.16bin \
    -t ../xfiles-dana/build/nets/xorSigmoidSymmetric-fixed.train \
    -m -e2 -x 2>&1 | grep "INFO\|WARN\|ERROR\|Assert" > issue-54.log
```

Most of the enumerated types are stored in src/main/scala/Dana.scala.

### Table Based Debugging
Debugging with straight Chisel `printfs` of events is very difficult as it requires complete knowledge of the system. We're in the process of moving to a debugging style where the state of X-FILES and DANA tables are dumped whenever they see an event which updates their state. Chisel, however, does not provide a `printf` that accepts field widths (field widths are inferred from signal widths). Each module includes a dedicated `info` method which will dump the state of the module into a CSV format prepended with `^[DEBUG] *`. The included etc/debug-table.awk can be used to convert the raw output from the C++ emulator to pretty, formatted tables. As an example, you can run this with:

```
./emulator-Top-XFilesDanaCppPe1Epb4Config +verbose pk \
    ../xfiles-dana/build/fann-xfiles.rv
    -n ../xfiles-dana/build/nets/xorSigmoidSymmetric-fixed.16bin \
    -t ../xfiles-dana/build/nets/xorSigmoidSymmetric-fixed.train \
    -m -e2 -x 2>&1 | awk -f ../xfiles-dana/usr/etc/debug-table.awk
```

### Memory Tool for Debugging Help
One of the common issues that comes up when debugging is checking that the address computations for the Register File/Intermediate Storage area are working correctly. I have a tool which helps with this that will dump out a CSV formatted table of what the Register File should look like internally. This can then be pushed through `column` to get something reasonable to look at. You can do this with a call like the following:
```
$ ./usr/bin/dana-memory-tool -b8 \
    -n build/nets/xor-sigmoid-4i-fixed.net \
    -l | column -s, -t -o" "
E[out]        0x0  0
In            0x8  8  9  a  b
H[0]          0x10 10 11 12 13
Out           0x18 18
DW            0x20 20 21 22 23
Bias H[0]     0x28 28 29 2a 2b
Bias Out      0x30 30
Slope H[0][0] 0x38 38 39 3a 3b
Slope H[0][1] 0x40 40 41 42 43
Slope H[0][2] 0x48 48 49 4a 4b
Slope H[0][3] 0x50 50 51 52 53
Slope Out[0]  0x58 58 59 5a 5b
```
