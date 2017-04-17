#!/usr/bin/env python3

import argparse
import re

def parse_arguments():
    parser = argparse.ArgumentParser(
        description='Tool to generate CSV that shows the memory layout of DANA',
        epilog='''
The CSV output can be formatted with `column`::
    dana-memory-tool [ARGS] | column -s, -t -n''',
        formatter_class=argparse.RawTextHelpFormatter)
    parser.add_argument(
        '-b', '--elements-per-block', dest='elementsPerBlock', type=int,
        required=True, help='The number of elements per block')
    parser.add_argument(
        '-l', '--learning-transaction', dest='learning_f', action='store_true',
        default=False, help='Print memory for a learning transaction')
    parser.add_argument(
        '-n', '--nn-config', dest='nnConfig', type=str,
        required=True, help='FANN neural network file to use for topology')
    return parser.parse_args()

def get_layers(args):
    with open(args.nnConfig, 'r') as f:
        reLayerSizes = re.compile('layer_sizes=([\d ]+)')
        for line in f:
            layerSearch = reLayerSizes.match(line)
            if layerSearch:
                layers = re.split(' ',layerSearch.group(1))
                break

    # Remove the trailing whitespace on the layer_sizes FANN line
    del(layers[len(layers) - 1])

    for i in range(len(layers)):
        layers[i] = int(layers[i])

    return layers

class Memory:

    def __init__(self, layerArray, epb, learning):
        self.layerArray = layerArray
        self.totalLayers = len(layerArray)
        self.epb = epb
        self.learning = learning

    def __alignAddr(self):
        if (self.addr % self.epb != 0):
            self.addr = self.addr + int(self.epb - self.addr % self.epb)
            print("")

    def __printExpectedOut(self):
        print("E[out],", end='')
        self.__printNeurons(self.layerArray[len(self.layerArray)-1])
        self.__alignAddr()

    def __printNeurons(self, layer):
        print("0x%x," % self.addr, end='')
        for neuron in range(layer - 1):
            print("%x," % self.addr, end='')
            if (self.addr % self.epb == self.epb - 1):
                print("")
                if (neuron != layer - 2):
                    print(",,", end='')
            self.addr += 1
        self.__alignAddr()

    def __printDW(self):
        layerNumber = self.totalLayers - 3
        for layer in self.layerArray[len(self.layerArray)-2:0:-1]:
            print("DW H[%d]," % layerNumber, end='')
            self.__printNeurons(layer)
            layerNumber -= 1

    def __printBias(self):
        layerNumber = 1
        for layer in self.layerArray[1:len(self.layerArray)]:
            if (layerNumber == len(self.layerArray)-1):
                print("Bias Out,", end='')
            else:
                print("Bias H[%d]," % (layerNumber - 1), end='')
            layerNumber += 1
            self.__printNeurons(layer)

    def __printSlopes(self):
        for layerIndex in range(1, len(self.layerArray)):
            for neuron in range(self.layerArray[layerIndex] - 1):
                if (layerIndex == len(self.layerArray)-1):
                    print("Slope Out[%d]," % neuron, end='')
                else:
                    print("Slope H[%d][%d]," % ((layerIndex - 1), neuron), end='')
                print("0x%x," % self.addr, end='')
                for neuronPrevious in range(self.layerArray[layerIndex - 1] - 1):
                    print("%x," % self.addr, end='')
                    if (self.addr % self.epb == self.epb - 1):
                        print("")
                        if (neuronPrevious != self.layerArray[layerIndex - 1] - 2):
                            print(",,", end='')
                    self.addr += 1
                self.__alignAddr()

    def __printLearning(self):
        self.__printExpectedOut()
        self.__printFeedforward()
        self.__printDW()
        self.__printBias()
        self.__printSlopes()

    def __printFeedforward(self):
        layerNumber = 0
        for layer in self.layerArray:
            if (layerNumber == 0):
                print("In,", end='')
            elif (layerNumber < len(self.layerArray) - 1):
                print("H[%d]," % (layerNumber - 1), end='')
            else:
                print("Out,", end='')
            layerNumber += 1
            self.__printNeurons(layer)

    def printMemory(self):
        self.addr = 0
        if (self.learning):
            self.__printLearning()
        else:
            self.__printFeedforward()

def main():
    args = parse_arguments()

    layers = get_layers(args)

    memory = Memory(layers, args.elementsPerBlock, args.learning_f)
    memory.printMemory()

if __name__ == '__main__':
    main()
