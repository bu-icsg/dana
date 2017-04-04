#include <stdio.h>
#include <stdint.h>
#include <stddef.h>
#include <stdbool.h>

typedef struct {
    int8_t decimal_point : 3;
    bool error_function : 1;
    int8_t binary_format : 3;
    uint16_t unused : 9;
    uint16_t num_weight_blocks : 16;
    uint16_t num_neurons : 16;
    uint16_t num_layers : 16;
    uint16_t first_layer_ptr : 16;
    uint16_t weights_ptr : 16;
    int16_t learning_rate : 16;
    int16_t lambda : 16;
} packed_info_t;

typedef struct {
    ptrdiff_t weight_offset : 16;
    uint8_t num_weights : 8;
    int8_t activation_function : 5;
    int8_t steepness : 3;
    int32_t bias : 32;
} __attribute__((packed, aligned(1))) packed_neuron_t;

typedef struct {
    uint16_t first_neuron_ptr : 12;
    uint16_t num_neurons : 10;
    uint16_t num_neurons_prev : 10;
} __attribute__((packed, aligned(1))) packed_layer_t;

int main(int argc, char **argv) {
    if (argc != 2)
        printf("Usage: %s filename\n", argv[0]);
    else {
        FILE *file = fopen(argv[1], "rb");
        if (file == 0) {
            printf("Could not open file\n");
        }
        else {
            packed_info_t info;
            packed_layer_t layer;
            printf("sizeof(packed_info_t): %lu\n", sizeof(packed_info_t));
            printf("sizeof(packed_layer_t): %lu\n", sizeof(packed_layer_t));
            packed_neuron_t neuron;
            int32_t weight;
            fread(&info, sizeof(packed_info_t), 1, file);
            printf("decimal point: %d\n", info.decimal_point);
            printf("error function: %d\n", info.error_function);
            printf("binary format: %d\n", info.binary_format);
            printf("num. weight blocks: %u\n", info.num_weight_blocks);
            printf("num. neurons: %u\n", info.num_neurons);
            printf("num. layers: %u\n", info.num_layers);
            printf("first layer ptr: %d\n", info.first_layer_ptr);
            printf("weights ptr: %d\n", info.weights_ptr);
            printf("learning rate: %d\n", info.learning_rate);
            printf("lambda: %d\n", info.lambda);
            fseek(file, info.first_layer_ptr, SEEK_SET);
            int32_t layer_ptr, neuron_ptr;
            for (uint16_t i = 0; i < info.num_layers; i++) {
                fread(&layer, sizeof(packed_layer_t), 1, file);
                layer_ptr = ftell(file);
                printf("layer %u\n", i);
                printf("first neuron ptr: %u\n", layer.first_neuron_ptr);
                printf("neurons in layer: %u\n", layer.num_neurons);
                printf("neurons in previous layer: %u\n", layer.num_neurons_prev);
                fseek(file, layer.first_neuron_ptr, SEEK_SET);
                for (uint16_t j = 0; j < layer.num_neurons; j++) {
                    fread(&neuron, sizeof(packed_neuron_t), 1, file);
                    neuron_ptr = ftell(file);
                    printf("\tneuron %u\n", j);
                    printf("\tweight offset: %d\n", neuron.weight_offset);
                    printf("\tnum. weights: %u\n", neuron.num_weights);
                    printf("\tactivation function: %u\n", neuron.activation_function);
                    printf("\tsteepness: %d\n", neuron.steepness);
                    printf("\tbias: %d\n", neuron.bias);
                    fseek(file, neuron.weight_offset, SEEK_SET);
                    for (uint16_t k = 0; k < neuron.num_weights; k++) {
                        fread(&weight, sizeof(int32_t), 1, file);
                        printf("\t\tweight %u: %d\n", k, weight);
                    }
                    fseek(file, neuron_ptr, SEEK_SET);
                }
                fseek(file, layer_ptr, SEEK_SET);
            }
            return 0;
            fseek(file, 6, SEEK_SET);
            /*fread(&num_layers, sizeof(uint16_t), 1, file);*/
            /*fread(&first_layer_ptr, sizeof(uint16_t), 1, file);*/
            /*printf("num layers: %u\n", num_layers);*/
            /*printf("first layer ptr: %d\n", first_layer_ptr);*/
            /*packed_layer_t layer;*/
            /*fseek(file, first_layer_ptr, SEEK_SET);*/
            /*printf("file pos: %lu\n", ftell(file));*/
            /*for (uint32_t i = 0; i < num_layers; i++) {*/
                /*fread(&layer, sizeof(packed_layer_t), 1, file);*/
                /*printf("layer: %u, num. neurons: %u\n", i, layer.num_neurons);*/
                /*for(uint32_t j = 0; j < num_neurons; j++) {*/
                    
                /*}*/
            /*}*/
        
            fclose(file);
        }
    }
    return 0;
}
