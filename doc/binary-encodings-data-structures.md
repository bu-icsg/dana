# Binary Encodings and Data Structures
This documentation covers the binary encodings (for things like decimal point and steepness) as well as the NN configuration data structure.

## NN Configuration Encoding
RAM Organization. This is a modified layout of fann_small and its associated abbreviated structs compacted to fit in block-sized chunks. Certain fields are aligned on block boundaries while others are not. The format below is shown for a network with a topology of 5x2x1x[something] and a block size of 16 bytes (128 bits). The start of the layers and neurons and every weight are aligned on block boundaries. The steepness must be a power of 2 and is encoded using 3 bits (see table below this one). The table structure will differ slightly for different block sizes, i.e., 32, 64, or 128 bytes.
```
|---------+----------+--------+------+-------------+---------------------------|
| Class   | Address  | Offset | Size | Attribute   | Sub Attribute * == Unused |
|---------+----------+--------+------+-------------+---------------------------|
| Info    | 0        |      0 |    3 | net info    | decimal point             |
|         |          |      3 |    1 |             | error function            |
|         |          |      4 |   12 |             | *** unused ***            |
|         |          |     16 |   16 |             | total weight blocks       |
|         |          |     32 |   16 |             | total neurons             |
|         |          |     48 |   16 |             | total layers [N]          |
|         |          |     64 |   16 |             | * first layer ptr [LF*]   |
|         |          |     80 |   16 |             | * weights ptr [W*]        |
|         |          |     96 |   16 |             | * learning rate           |
|         |          |    112 |   16 |             | * lambda (weight decay)   |
|---------+----------+--------+------+-------------+---------------------------|
| Layers  | [LF*]    |      0 |   12 | layer 0     | first neuron ptr [L0Nf]   |
|         |          |     12 |   10 |             | neurons in layer          |
|         |          |     22 |   10 |             | neurons in next layer     |
|         |          |     32 |   12 | layer 1     | first neuron ptr [L1Nf]   |
|         |          |     44 |   10 |             | neurons in layer          |
|         |          |     54 |   10 |             | neurons in next layer     |
|         |          |     64 |   12 | layer 2     | first neuron ptr [L2Nf]   |
|         |          |     76 |   10 |             | neurons in layer          |
|         |          |     86 |   10 |             | neurons in next layer     |
|         |          |     96 |   12 | layer 3     | last neurons ptr [L3Nl]   |
|         |          |    108 |   10 |             | neurons in layer          |
|         |          |    118 |   10 |             | neurons in next layer     |
|---------+----------+--------+------+-------------+---------------------------|
|         | ...      |    ... |  ... | ...         | ...                       |
|---------+----------+--------+------+-------------+---------------------------|
|         |          |      0 |   12 | layer N-2   | first neuron ptr [LpNf]   |
|         |          |     12 |   10 |             | neurons in layer          |
|         |          |     22 |   10 |             | neurons in next layer     |
|         |          |     32 |   12 | layer N-1   | first neuron ptr [LlNf]   |
|         |          |     44 |   10 |             | neurons in layer          |
|         |          |     54 |   10 |             | *** unused ***            |
|         |          |     64 |   64 |             | *** unused ***            |
|---------+----------+--------+------+-------------+---------------------------|
| Neurons | [L0Nf]   |      0 |   16 | L0 neuron 0 | weight offset [L0N0w]     |
|         |          |     16 |    8 |             | number of weights         |
|         |          |     24 |    5 |             | activation function       |
|         |          |     29 |    3 |             | steepness                 |
|         |          |     32 |   32 |             | bias                      |
|         |          |     64 |   16 | L0 neuron 1 | weight offset [L0N1w]     |
|         |          |     80 |    8 |             | number of weights         |
|         |          |     88 |    5 |             | activation function       |
|         |          |     93 |    3 |             | steepness                 |
|         |          |     96 |   32 |             | bias                      |
|---------+----------+--------+------+-------------+---------------------------|
|         |          |      0 |   16 | L0 neuron 2 | weight offset [L0N2w]     |
|         |          |     16 |    8 |             | number of weights         |
|         |          |     24 |    5 |             | activation function       |
|         |          |     29 |    3 |             | steepness                 |
|         |          |     32 |   32 |             | bias                      |
|         | [L1Nf]   |     64 |   16 | L1 neuron 0 | weight offset [L1N0w]     |
|         |          |     80 |    8 |             | number of weights         |
|         |          |     88 |    5 |             | activation function       |
|         |          |     93 |    3 |             | steepness                 |
|         |          |     96 |   32 |             | bias                      |
|---------+----------+--------+------+-------------+---------------------------|
|         |          |      0 |   16 | L0 neuron 1 | weight offset [L1N1w]     |
|         |          |     16 |    8 |             | number of weights         |
|         |          |     24 |    5 |             | activation function       |
|         |          |     29 |    3 |             | steepness                 |
|         |          |     32 |   32 |             | bias                      |
|         | [L2Nf]   |     64 |   16 |             | weight offset [L2N0w]     |
|         |          |     80 |    8 |             | number of weights         |
|         |          |     88 |    5 |             | activation function       |
|         |          |     93 |    3 |             | steepness                 |
|         |          |     96 |   32 |             | bias                      |
|---------+----------+--------+------+-------------+---------------------------|
| ...     | ...      |    ... |  ... | ...         | ...                       |
|---------+----------+--------+------+-------------+---------------------------|
| Weights | [W*] and |      0 |   32 | Weight 0    | first weight              |
|         | [W* +    |     32 |   32 | Weight 1    | second weight             |
|         | L0N0w]   |     64 |   32 | Weight 2    | third weight              |
|         |          |     96 |   32 | Weight 3    | fourth weight             |
|---------+----------+--------+------+-------------+---------------------------|
|         |          |      0 |   32 | Weight 4    | last weight               |
|         |          |     32 |   96 | Bias        | *** unused ***            |
|---------+----------+--------+------+-------------+---------------------------|
| Weights | [W* +    |      0 |   32 | Weight 0    | first weight              |
|         | L0N1w]   |     32 |   32 | Weight 1    | second weight             |
|         |          |     64 |   32 | Weight 2    | third weight              |
|         |          |     96 |   32 | Weight 3    | fourth weight             |
|---------+----------+--------+------+-------------+---------------------------|
|         |          |      0 |   32 | Weight 4    | last weight               |
|         |          |     32 |   96 | Bias        | *** unused ***            |
|---------+----------+--------+------+-------------+---------------------------|
|         | ...      |    ... |  ... | ...         | ...                       |
|---------+----------+--------+------+-------------+---------------------------|
```

## Decimal Point Encoding
The actual decimal (really "binary") point is defined:

    decimal point = [decimal point encoded] + [decimal point offset]

The decimal point offset, currently 7, is controlled by a parameter in Configs.scala. Using this parameter, the encoded decimal points are then:
```
|-----------------------+----------------------|
| Encoded Decimal Point | Actual Decimal Point |
|-----------------------+----------------------|
|                   000 |                    7 |
|                   001 |                    8 |
|                   010 |                    9 |
|                   011 |                   10 |
|                   100 |                   11 |
|                   101 |                   12 |
|                   110 |                   13 |
|                   111 |                   14 |
|-----------------------+----------------------|
```

## Steepness Encoding
The steepness is defined such that:

    actual steepness = 2 ** ([encoded steepness] - [steepness offset])

The steepness offset, currently 4, is also controlled by a Configs.scala parameter. When tabulated, the steepness mappings are then:
```
|-------------------+------------------+-------|
| Encoded Steepness | Actual Steepness | Shift |
|-------------------+------------------+-------|
|               000 | 1/16             | >> 4  |
|               001 | 1/8              | >> 3  |
|               010 | 1/4              | >> 2  |
|               011 | 1/2              | >> 1  |
|               100 | 1                | =     |
|               101 | 2                | << 1  |
|               110 | 4                | << 2  |
|               111 | 8                | << 3  |
|-------------------+------------------+-------|
```

## Register File Organization
All "segments" are aligned on block boundaries. To indicate this below, I define a function `align()` which serves to align an index on a block boundary.

### Feedforward Transaction
```
|---------------------------+------------------------+---------------|
| Data                      | Address                |        Offset |
|---------------------------+------------------------+---------------|
| Input                     | 0                      |             0 |
|---------------------------+------------------------+---------------|
| Hidden Layer 0 Output     | align(#in)             |             0 |
| Hidden Layer 1 Output     |                        |   align(#h_0) |
| ...                       |                        |               |
| Hidden Layer [H]-1 Output |                        | align(#h_H-2) |
|---------------------------+------------------------+---------------|
| Output                    | align(#in + sum(#h_h)) |             0 |
|---------------------------+------------------------+---------------|
```

### Learning Transaction
Definitions:
```
[H]: Number of Hidden Layers
```

#### With Delta Writes
```
|---------------+--------------+----------------------+-----------------|
| Class         | Written by   | Size                 | State           |
|---------------+--------------+----------------------+-----------------|
| E[out]        | User         | output neurons       | _LOAD_OUTPUTS   |
|---------------+--------------+----------------------+-----------------|
| Input         | User         | input neurons        | *_FEEDFORWARD   |
| Output        | hidden 1     | hidden 1 neurons     |                 |
| Output        | hidden 2     | hidden 2 neurons     |                 |
| ...           | ...          |                      |                 |
| Output        | hidden [H]   | hidden [H] neurons   |                 |
| Output        | output       | output neurons       |                 |
|---------------+--------------+----------------------+-----------------|
| Delta         | output       | output neurons       | _ERROR_BACKPROP |
| Delta--Weight | output       | hidden [H] neurons   |                 |
| Delta         | hidden [H]   | hidden [H] neurons   |                 |
| Delta--Weight | hidden [H]   | hidden [H]-1 neurons |                 |
| Delta         | hidden [H]-1 | hidden [H]-1 neurons |                 |
| Delta--Weight | hidden [H]-1 | hidden [H]-2 neurons |                 |
| ...           | ...          |                      |                 |
| Delta         | hidden 2     | hidden 2 neurons     |                 |
| Delta--Weight | hidden 2     | hidden 1 neurons     |                 |
|---------------+--------------+----------------------+-----------------|
| Bias          | hidden 1     | hidden 1 neurons     | _UPDATE_SLOPE   |
| ...           | ...          |                      |                 |
| Bias          | hidden [H]   | hidden [H] neurons   |                 |
| Bias          | output       | output neurons       |                 |
|---------------+--------------+----------------------+-----------------|
| Slopes        | hidden 1     |                      | _UPDATE_SLOPE   |
| ...           | ...          |                      |                 |
| Slopes        | hidden [H]   |                      |                 |
| Slopes        | output       |                      |                 |
|---------------+--------------+----------------------+-----------------|
```

This results in the following number of writes to the register file based on state. Note that the number of writes may be more (in the case of writing delta--weight products), but only the last data written by a neuron will then be counted.
```
|-------------------------------+---------------------------------------------|
| state (e_TTABLE_STATE prefix) | # writes (multiplies of # neurons in layer) |
|-------------------------------+---------------------------------------------|
| _LOAD_OUTPUTS                 | 0                                           |
| _FEEDFORWARD                  | 1                                           |
| _LEARN_FEEDFORWARD            | 1 or 3 (when in last layer)                 |
| _LEARN_ERROR_BACKPROP         | 2 or 1 (when in first layer)                |
| _LEARN_UPDATE_SLOPE           | 1 (only bias counted)                       |
| _LEARN_WEIGHT_UPDATE          | 0                                           |
|-------------------------------+---------------------------------------------|

```

#### Without Delta Writes (#32 state merge)
```
|---------------+--------------+----------------------+-----------------|
| Class         | Written by   | Size                 | State           |
|---------------+--------------+----------------------+-----------------|
| E[out]        | User         | output neurons       | _LOAD_OUTPUTS   |
|---------------+--------------+----------------------+-----------------|
| Input         | User         | input neurons        | *_FEEDFORWARD   |
| Output        | hidden 1     | hidden 1 neurons     |                 |
| Output        | hidden 2     | hidden 2 neurons     |                 |
| ...           | ...          |                      |                 |
| Output        | hidden [H]   | hidden [H] neurons   |                 |
| Output        | output       | output neurons       |                 |
|---------------+--------------+----------------------+-----------------|
| Delta--Weight | output       | hidden [H] neurons   | _ERROR_BACKPROP |
| Delta--Weight | hidden [H]   | hidden [H]-1 neurons |                 |
| Delta--Weight | hidden [H]-1 | hidden [H]-2 neurons |                 |
| ...           | ...          |                      |                 |
| Delta--Weight | hidden 2     | hidden 1 neurons     |                 |
|---------------+--------------+----------------------+-----------------|
| Bias          | hidden 1     | hidden 1 neurons     | _ERROR_BACKPROP |
| ...           | ...          |                      |                 |
| Bias          | hidden [H]   | hidden [H] neurons   |                 |
| Bias          | output       | output neurons       |                 |
|---------------+--------------+----------------------+-----------------|
| Slopes        | hidden 1     |                      | _ERROR_BACKPROP |
| ...           | ...          |                      |                 |
| Slopes        | hidden [H]   |                      |                 |
| Slopes        | output       |                      |                 |
|---------------+--------------+----------------------+-----------------|
```

Number of writes to the register file.
```
|-------------------------------+---------------------------------------------|
| state (e_TTABLE_STATE prefix) | # writes (multiplies of # neurons in layer) |
|-------------------------------+---------------------------------------------|
| _LOAD_OUTPUTS                 | 0                                           |
| _FEEDFORWARD                  | 1                                           |
| _LEARN_FEEDFORWARD            | 1 or 2 (when in last layer)                 |
| _LEARN_ERROR_BACKPROP         | 1                                           |
| _LEARN_WEIGHT_UPDATE          | 0                                           |
|-------------------------------+---------------------------------------------|
