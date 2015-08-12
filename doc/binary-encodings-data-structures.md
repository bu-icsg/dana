# Binary Encodings and Data Structures
This documentation covers the binary encodings (for things like decimal point and steepness) as well as the NN configuration data structure.

## NN Configuration Encoding
RAM Organization. This is a modified layout of fann_small and its associated abbreviated structs compacted to fit in block-sized chunks. Certain fields are aligned on block boundaries while others are not. The format below is shown for a network with a topology of 5x2x1x[something] and a block size of 16 bytes (128 bits). The start of the layers and neurons and every weight are aligned on block boundaries. The steepness must be a power of 2 and is encoded using 3 bits (see table below this one). The table structure will differ slightly for different block sizes, i.e., 32, 64, or 128 bytes.
```
|---------+----------+--------+------+-------------+-------------------------|
| Class   | Address  | Offset | Size | Attribute   | Sub Attribute           |
|---------+----------+--------+------+-------------+-------------------------|
| Info    | 0        |      0 |    3 | net info    | decimal point           |
|         |          |      4 |    1 |             | error function          |
|         |          |      5 |   12 |             | *** unused ***          |
|         |          |     16 |   16 |             | total edges             |
|         |          |     32 |   16 |             | total neurons           |
|         |          |     48 |   16 |             | total layers [N]        |
|         |          |     64 |   16 |             | first layer ptr [LF*]   |
|         |          |     80 |   16 |             | weights ptr [W*]        |
|         |          |     96 |   32 |             | *** unused ***          |
|---------+----------+--------+------+-------------+-------------------------|
| Layers  | [LF*]    |      0 |   12 | layer 0     | first neuron ptr [L0Nf] |
|         |          |     12 |   10 |             | neurons in layer        |
|         |          |     22 |   10 |             | neurons in next layer   |
|         |          |     32 |   12 | layer 1     | first neuron ptr [L1Nf] |
|         |          |     44 |   10 |             | neurons in layer        |
|         |          |     54 |   10 |             | neurons in next layer   |
|         |          |     64 |   12 | layer 2     | first neuron ptr [L2Nf] |
|         |          |     76 |   10 |             | neurons in layer        |
|         |          |     86 |   10 |             | neurons in next layer   |
|         |          |     96 |   12 | layer 3     | last neurons ptr [L3Nl] |
|         |          |    108 |   10 |             | neurons in layer        |
|         |          |    118 |   10 |             | neurons in next layer   |
|---------+----------+--------+------+-------------+-------------------------|
|         | ...      |    ... |  ... | ...         | ...                     |
|---------+----------+--------+------+-------------+-------------------------|
|         |          |      0 |   12 | layer N-2   | first neuron ptr [LpNf] |
|         |          |     12 |   10 |             | neurons in layer        |
|         |          |     22 |   10 |             | neurons in next layer   |
|         |          |     32 |   12 | layer N-1   | first neuron ptr [LlNf] |
|         |          |     44 |   10 |             | neurons in layer        |
|         |          |     54 |   10 |             | *** unused ***          |
|         |          |     64 |   64 |             | *** unused ***          |
|---------+----------+--------+------+-------------+-------------------------|
| Neurons | [L0Nf]   |      0 |   16 | L0 neuron 0 | weight offset [L0N0w]   |
|         |          |     16 |    8 |             | number of weights       |
|         |          |     24 |    5 |             | activation function     |
|         |          |     29 |    3 |             | steepness               |
|         |          |     32 |   32 |             | bias                    |
|         |          |     64 |   16 | L0 neuron 1 | weight offset [L0N1w]   |
|         |          |     80 |    8 |             | number of weights       |
|         |          |     88 |    5 |             | activation function     |
|         |          |     93 |    3 |             | steepness               |
|         |          |     96 |   32 |             | bias                    |
|---------+----------+--------+------+-------------+-------------------------|
|         |          |      0 |   16 | L0 neuron 2 | weight offset [L0N2w]   |
|         |          |     16 |    8 |             | number of weights       |
|         |          |     24 |    5 |             | activation function     |
|         |          |     29 |    3 |             | steepness               |
|         |          |     32 |   32 |             | bias                    |
|         | [L1Nf]   |     64 |   16 | L1 neuron 0 | weight offset [L1N0w]   |
|         |          |     80 |    8 |             | number of weights       |
|         |          |     88 |    5 |             | activation function     |
|         |          |     93 |    3 |             | steepness               |
|         |          |     96 |   32 |             | bias                    |
|---------+----------+--------+------+-------------+-------------------------|
|         |          |      0 |   16 | L0 neuron 1 | weight offset [L1N1w]   |
|         |          |     16 |    8 |             | number of weights       |
|         |          |     24 |    5 |             | activation function     |
|         |          |     29 |    3 |             | steepness               |
|         |          |     32 |   32 |             | bias                    |
|         | [L2Nf]   |     64 |   16 |             | weight offset [L2N0w]   |
|         |          |     80 |    8 |             | number of weights       |
|         |          |     88 |    5 |             | activation function     |
|         |          |     93 |    3 |             | steepness               |
|         |          |     96 |   32 |             | bias                    |
|---------+----------+--------+------+-------------+-------------------------|
| ...     | ...      |    ... |  ... | ...         | ...                     |
|---------+----------+--------+------+-------------+-------------------------|
| Weights | [W*] and |      0 |   32 | Weight 0    | first weight            |
|         | [W* +    |     32 |   32 | Weight 1    | second weight           |
|         | L0N0w]   |     64 |   32 | Weight 2    | third weight            |
|         |          |     96 |   32 | Weight 3    | fourth weight           |
|---------+----------+--------+------+-------------+-------------------------|
|         |          |      0 |   32 | Weight 4    | last weight             |
|         |          |     32 |   96 | Bias        | *** unused ***          |
|---------+----------+--------+------+-------------+-------------------------|
| Weights | [W* +    |      0 |   32 | Weight 0    | first weight            |
|         | L0N1w]   |     32 |   32 | Weight 1    | second weight           |
|         |          |     64 |   32 | Weight 2    | third weight            |
|         |          |     96 |   32 | Weight 3    | fourth weight           |
|---------+----------+--------+------+-------------+-------------------------|
|         |          |      0 |   32 | Weight 4    | last weight             |
|         |          |     32 |   96 | Bias        | *** unused ***          |
|---------+----------+--------+------+-------------+-------------------------|
|         | ...      |    ... |  ... | ...         | ...                     |
|---------+----------+--------+------+-------------+-------------------------|
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
|-------------------+------------------|
| Encoded Steepness | Actual Steepness |
|-------------------+------------------|
|               000 | 1/16             |
|               001 | 1/8              |
|               010 | 1/4              |
|               011 | 1/2              |
|               100 | 1                |
|               101 | 2                |
|               110 | 4                |
|               111 | 8                |
|-------------------+------------------|
```
