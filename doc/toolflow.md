# Toolflow for Working with X-FILES/DANA

At its most basic, X-FILES/DANA enables hardware acceleration of a subset of networks generate by the Fast Artificial Neural Network Library (FANN) [[1]](#cite-fann). DANA, however, does not execute a "neural network program". Instead, DANA understands a binary data structure representing a neural network. This data format is described in more detail in the [binary encodings documentation](https://www.github.com/bu-icsg/xfiles-dana/tree/master/doc/binary-encodings-data-structures.md#nn-configuration-encoding) and in our PACT '15 paper [[2]](#cite-pact2015). This format roughly follows the FANN neural network data structure with an enforced all-to-all connectivity and ignoring all data that DANA does not use.

Requests to access neural network resources are defined using a transaction model. Each transaction needs an input (or set of inputs in the case of training) and a binary data structure describing a neural network. Tools are provided that facilitate the generation of these files. The workflow and associated tools for the common usage case are discussed below.

All tools live in a subdirectory of [`xfiles-dana/tools`](https://www.github.com/bu-icsg/xfiles-dana/tree/master/tools), but have symlinks provided in [`xfiles-dana/usr/bin`](https://www.github.com/bu-icsg/xfiles-dana/tree/master/usr/bin).

## Standard Workflow
The standard workflow (most of which happens automatically for selected neural networks when you run `make rv`) is shown graphically below:
```
Existing                                      Existing
Floating Point FANN--+                        Floating Point FANN---+
Neural Network       |                        Training File         |
Configuration        |                                              |
                     |                         +----------------+   |
+-----------+        |                         |gen-boolean-data|-->|
|fann-random|------->|                         +----------------+   |
+-----------+        |                            +-------------+   |
                     |                            |gen-math-data|-->|
                     |                            +-------------+   |
                     |                    +---------------------+   |
                     |                    |gen-random-fann-input|-->|
                     |                    +---------------------+   |
                     |                                              |
        +------------+                                       +------+
        |                                                    |
        v                                                    v
 Floating Point FANN                                Floating Point FANN
 Neural Network                                     Training File
 Configuration                                               |
   |    |                                                    |
   |    +-------------------+                                |
   |  +---------------------+--------------------------------+
   |  |                     |                                |
   |  |                     v                                |
   |  |          +-------------------+                       |
   |  |          |fann-float-to-fixed|      Specific         |
   |  |          +-------------------+      Binary Point     |
   |  |                     |                    |           |
   |  |    +----------------+-----+              |           |
   |  |    |                      v              |           |
   |  |    |     +-----------------------+       |           |
   |  |    |     |fann-change-fixed-point|<------+           |
   |  |    |     +-----------------------+                   |
   |  |    |                      |                          |
   |  |    +----------------+-----+                          |
   |  |                     |                                |
   |  |                     v                                v
   |  |           Fixed Point FANN   Binary Point     +------------------+
   |  |           Neural Network  ------------------->|fann-data-to-fixed|
   |  |           Configuration                       +------------------+
   |  |                     |                                  |
   |  |  Decimal            |                                  |
   |  |  Point Offset       |                                  |
   |  |         |           |                                  |
   |  |  Block  +-----+     |                                  |
   |  |  Width        v     v                                  v
   |  |    |     +---------------------------------+   Fixed Point FANN
   |  |    +---->|write-fann-config-for-accelerator|   Training File
   |  |          +---------------------------------+           |
   |  |                     |                                  |
   |  |                     v                                  |
   |  |           Binary Fixed Point                           |
   |  |           Neural Network                               |
   |  |           Configuration for DANA                       |
   |  |                     |                                  |
   |  |                     |    |-----------------------------+
   |  |                     |    |
   v  v                     v    v
+-------------+       +------------+
|FANN Software|       |X-FILES/DANA|
+-------------+       +------------+
  | |                     | |
  | |          +----------+ +-----------------+
  | |          |                              |
  | +----------+---------------------------+  |
  |            |                           |  |
  |            |                           |  |
  +--+         |                           |  |
     v         v                           v  v
  Trained Neural Network              Prediction/Inference
```

### Sourcing or Creating a Neural Network and Training File
First, you need to start with a floating point FANN neural network configuration. You can get this from a program using FANN to create a neural network or use the provided [`fann-random`](https://www.github.com/bu-icsg/xfiles-dana/tree/master/tools/fann-random/src/fann-random.c) to generate a randomly initialized neural network with a specific topology. In our convention, this file is usually named `*-float.net`.

Second, you need a floating point FANN training file. You can either generate this yourself, use one of the [datasets provided by FANN](https://www.github.com/libfann/fann/tree/master/datasets), or generate data with one of the following tools:
* [`gen-boolean-data`](https://www.github.com/bu-icsg/xfiles-dana/tree/master/tools/scripts/gen-boolean-data) -- generate a training file for some boolean function with some number of input bits
* [`gen-math-data`](https://www.github.com/bu-icsg/xfiles-dana/tree/master/tools/scripts/gen-math-data) -- generate a training file for a mathematical function
* [`gen-random-fann-input`](https://www.github.com/bu-icsg/xfiles-dana/tree/master/tools/scripts/gen-random-fann-input) -- generate random data for some number of inputs and outputs
In out convention, this file is usually named `*-float.train`.

A floating point network and training file is enough to run a software version of FANN on either the host machine or on Rocket. By default, two programs are built for this purpose:
* [`fann-train`](https://www.github.com/bu-icsg/xfiles-dana/tree/master/tools/fann-train/src/fann-train.c)
* [`fann-soft`](https://www.github.com/bu-icsg/xfiles-dana/tree/master/src/test/rv/fann-soft.c)

### Conversion to Fixed Point
DANA is a fixed point accelerator and we need to convert both the neural network configuration to something that DANA understands and convert the floating point training data to a fixed point format.

The program [`fann-float-to-fixed`](https://www.github.com/bu-icsg/xfiles-dana/tree/master/tools/fann-float-to-fixed/src/fann-float-to-fixed.c) will convert a floating point FANN network to a fixed point network using the FANN library. FANN will try to choose the largest binary point (which FANN refers to internally as the "decimal Point") that will not cause integer overflow in its internal fixed point type. Qualitatively, this means that the binary point is inversely proportional to the maximum number of connections to any neuron in the network. With our all-to-all provision, this is equivalent to the maximum number of neurons in a layer.

To manually change the fixed point for a given fixed point configuration to a specific use a specific binary point, the program [`fann-change-fixed-point`](https://www.github.com/bu-icsg/xfiles-dana/tree/master/tools/scripts/fann-change-fixed-point) is provided.

The floating point training file must then be converted to a fixed point representation using the binary point determined by `fann-float-to-fixed` or after applying `fann-change-fixed-point`. You can just grep for "decimal_point" in the fixed point configuration to figure this out (or script this out like [`xfiles-dana/tools/common/Makefrag`](https://www.github.com/bu-icsg/xfiles-dana/tree/master/tools/common/Makefrag) does with: `grep decimal $FIXED_POINT_NET | sed 's/.\+=//'`).

A fixed point FANN configuration can then be converted over to a binary data structure that DANA understands with [`write-fann-config-for-accelerator`](https://www.github.com/bu-icsg/xfiles-dana/tree/master/tools/write-fann-config-for-accelerator/src/write-fann-config-for-accelerator.c). This has two additional input parameters that are dependent on the specific build of DANA:
* Decimal Point Offset -- The decimal point is encoded using 3 bits (*cough* premature optimization *cough*). The actual decimal point is computed by adding this encoded value to the offset. See the [binary encodings documentation](https://www.github.com/bu-icsg/xfiles-dana/tree/master/doc/binary-encodings-data-structures.md#decimal-point-encoding) for more details. This is currently 7.
* Block Width -- DANA does all processing on wide blocks of elements. The element width is currently 32 bits, but will likely be reduced in a later version. DANA configurations support 4, 8, 16, and 32 element blocks. The block width is the size in 8-bit bytes used when generating the binary configuration data structure. So, the block width is `4 * elements-per-block`. __There is currently no checking by DANA to see if a format is valid, so an incorrect configuration will just hang with high probability.__

### Execution on X-FILES/DANA
Once you have a binary configuration and a fixed point training file, you can then run this in the C++ cycle-accurate model of Rocket + X-FILES/DANA generated by Chisel or on the FPGA. A provided program for running gradient descent or stochastic gradient descent learning is [`fann-xfiles`](https://www.github.com/bu-icsg/xfiles-dana/tree/master/src/test/rv/fann-xfiles.c). An example run of one of this for a neural network generated by the top-level Makefile on the C++ model would then be (where `$ROCKETCHIP` is your clone of the [rocket-chip](https://www.github.com/ucb-bar/rocket-chip) repo):
```
./emulator-Top-XFilesDanaCppPe4Epb4Config pk \
  ../xfiles-dana/build/fann-xfiles.rv \
  -n ../xfiles-dana/build/nets/xorSigmoidSymmetric-fixed.16bin \
  -t ../xfiles-dana/build/nets/xorSigmoidSymmetric-fixed.train \
  -m10 -e100
```

Alternatively, you can write your own software that uses the [X-FILES user/supervisor library](https://www.github.com/bu-icsg/xfiles-dana/tree/master/src/main/c/xfiles.h).

## Other Tools
There are a number of other tools that are currently provided but not used in the standard workflow.

### Tools to Process Outputs
* [`gen-trace-video`](https://www.github.com/bu-icsg/xfiles-dana/tree/master/tools/scripts/gen-trace-video) -- Both [`fann-train`](https://www.github.com/bu-icsg/xfiles-dana/tree/master/tools/fann-train/src/fann-train.c) and [`fann-xfiles`](https://www.github.com/bu-icsg/xfiles-dana/tree/master/src/test/rv/fann-xfiles.c) have a `-b,--video-data` option to produce a log of all the outputs. This Python script will coalesce this data, for a provided training file, into a video animation of how the neural network approximates the actual data. Note, really only supports functions with one input and one output.
* [`parse-data-generic`](https://www.github.com/bu-icsg/xfiles-dana/tree/master/tools/scripts/parse-data-generic) -- A Perl script used to coalesce a log of accuracies or run times into data suitable for plotting with LaTeX.

### Tools to Communicate with an FPGA Pool
Note: these tools will not work out of the box, but are provided as an example way of working with a pool of FPGA resources attached to a server.

We currently maintain a pool of Zedboards attached to a server. These can be power cycled with an APC power strip if needed. These tools all depend on the existence of a `/opt/etc/info.txt` file with the following format:
```
fpga0,/dev/ttyACM0,B,8,3
fpga1,/dev/ttyACM2,B,1,1
fpga2,/dev/ttyACM1,A,1,1
ip,B,192.168.1.33
ip,A,192.168.1.32
credentials-apc,USERNAME,PASSWORD
```

Each FPGA is declared as being on a specific device with some information about which power strip (A or B) and the plug that the FPGA is using. Credentials are also stored for accessing the APC.

For working on the FPGAs we use `screen` as a soft way of locking each FPGA for individual use. A user who wants to work on an FPGA runs [`rvcon`](https://www.github.com/bu-icsg/xfiles-dana/tree/master/tools/scripts/rvcon) which will look for a free FPGA and connect to it with `screen`. The user can then disconnect from that `screen` session and `ssh` to the board to work more easily. When the user is done with the FPGA, they are supposed to kill their `screen` session. Users can query that status of the boards (power state and who, if anyone, is using it) with [`rvstatus`](https://www.github.com/bu-icsg/xfiles-dana/tree/master/tools/scripts/rvstatus) or [`rvwho`](https://www.github.com/bu-icsg/xfiles-dana/tree/master/tools/scripts/rvwho). The boards can be power cycled with [`rvreboot`](https://www.github.com/bu-icsg/xfiles-dana/tree/master/tools/scripts/rvreboot) and a new FPGA configuration can be loaded with [`rv-load-fpga`](https://www.github.com/bu-icsg/xfiles-dana/tree/master/tools/scripts/rv-load-fpga).

### Rarely Used Tools
* [`atanh-lut`](https://www.github.com/bu-icsg/xfiles-dana/tree/master/tools/scripts/atanh-lut) -- Python script to figure out the optimal piecewise linear points for a hyperbolic tangent function
* [`binary-to-ram-init`](https://www.github.com/bu-icsg/xfiles-dana/tree/master/tools/scripts/binary-to-ram-init) -- Converts a binary neural network configuration to a Verilog initial block that can be used to preload DANA's cache. This is outdated as DANA's cache will by filled as needed from the memory of the Rocket's memory hierarchy.
* [`danaCache`](https://www.github.com/bu-icsg/xfiles-dana/tree/master/tools/scripts/danaCache) -- A tool for helping to populate to preload DANA's configuration cache
* [`dataset-tool.py`](https://www.github.com/bu-icsg/xfiles-dana/tree/master/tools/scripts/dataset-tool.py)
* [`fann-config-mr`](https://www.github.com/bu-icsg/xfiles-dana/tree/master/tools/scripts/fann-config-mr) -- Generates a neural network configuration with some amount of modular redundancy in its neurons. This was related to some preliminary work towards increasing the fault tolerance of a neural network [[3]](#cite-barc2015)
* [`fann-train-to-c-header`](https://www.github.com/bu-icsg/xfiles-dana/tree/master/tools/fann-train-to-c-header/src/fann-train-to-c-header.c) -- Convert a FANN training file to a C header. A fixed point variant (`fann-train-to-c-header`) of this will also be built from the same source.

## <a name="references"></a> References

<a name="cite-fann"></a>[1] S. Nissen. "Implementation of a fast artificial neural network library (fann)".
_Technical Report_, Department of Computer Science University of Copenhagen (DIKU), 31.

<a name="cite-pact2015"</a>[2] [S. Eldridge, A. Waterland, M. Seltzer, J. Appavoo, and Joshi, A., _Towards General Purpose Neural Network Computing_, in Proceedings of the International Conference on Parallel Architectures and Compilation Techniques (PACT), 2015.](http://people.bu.edu/schuye/files/pact2015-eldridge-paper.pdf)

<a name="cite-barc2015"></a>[3] [S. Eldridge and A. Joshi, Exploiting Hidden Layer Modular Redundancy for Fault-Tolerance in Neural Network Accelerators, Boston Area Architecture Workshop (BARC) 2015.](http://people.bu.edu/schuye/files/barc2015-eldridge-paper.pdf)
