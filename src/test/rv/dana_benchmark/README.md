Benchmark Tests for Dana
======

## Overview

The dana_benchmark repo contains a testing program that can be used with the XFILES/DANA system. The DANA_BENCHMARK.c program creates a series of transactions which query the system and acquire the output. Currently, multiple transactions can be run on a single thread.

## Running Tests

#### Command Line Inputs

The dana_benchmark tests are defined by command line input. The command line arguments include:

* Transactions
	* [NEURAL_NET],[NUMBER_OF_INPUTS],[NUMBER_OF_OUTPUTS]
* Number of concurrent transactions to run
	* Will always be second to last command line argument
	* If this exceeds the total number of transactions it will be set to the total number of transactions
* Debug Option
	* Will always be last command line argument
	* If set to 1, debug information will be printed

The transaction arguments are seperated by a comma with no space, multiple transactions are seperated with a space.

#### Compilation

The dana_benchmark program is compiled with 

`riscv64-unknown-elf-gcc-5.3.0`

The following command is used to compile the program:

`riscv64-unknown-elf-gcc-5.3.0 DANA_BENCHMARK.c -o danabench`

This will produce the binary 'danabench' which can then be run on an fpga.

#### Example Run

The following command is an example of how to run the dana_benchmark program. This is running on an fpga using fesver-zynq and the proxy kernel.

`./fesvr-zynq pk danabench net0,3,2 net1,5,1 1 1`

The above command will create two transactions:

* Transaction 1
	* Neural Network = net0
	* Input array length = 3
	* Output array length = 2
* Transaction 2
	* Neural Network = net1
	* Input array length = 5
	* Output array length = 1

The command will run each transaction separately (indicated by the 1 as the second to last command line argument) and the debugging will be turned on (indicated by the 1 as the last command line arguement)

The following is an example of running the program with concurrent transactions without debugging:

`./fesvr-zynq pk danabench net0,3,2 net1,5,1 2 0`

The 2 as the second to last command line argument indicates that both transactions will be run at the same time. The zero as the last command line argument indicates that debugging is off.

### Further Documentation and Questions

For information on specific functions in the DANA_BENCHMARK.c program, the DANA_BENCHMARK.h file can be consulted which is located in the dana_benchmark/c directory.

For information on the functions that are used to interact with the XFILES/DANA system, the xfiles* files in the dana_benchmark/c directory can be consulted.


If there are any further questions, please email einstein@bu.edu!






