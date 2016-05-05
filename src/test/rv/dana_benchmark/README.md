Benchmark Tests for Dana
======

## Inputs to DANA_BENCHMARK

DANA_BENCHMARK takes pairs of net files and train files and a directive. The directive indicates how many threads to be run at a time.

The net files and associated train files are seperated by a comma.

## Example Run

./DANA_BENCHMARK NET0,TRAIN0 NET1,TRAIN1 2

This will create two net/train file pairs [NET0, TRAIN0] and [NET1, TRAIN1] and run two threads at a time. Therefore, both the file pairs will be run concurrently.

## NOTE

Currently, the program only takes the first neural network net file that is passed to it and creates its own input for the net.