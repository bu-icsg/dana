/**********
 * Author: Craig Einstein
 *
 * File: DANA_BENCHMARK.c
 *
 * Description: Contains the definitions for the methods in DANA_BENCHMARK.h
 *		DANA_BENCHMARK runs transactional testing on the XFILES/DANA System
 *      The functions contained in this program are defined in xfiles-user.h,
 *		xfiles-user.c, and DANA_BENCHMARK. These files are located in the
 *      dana_benchmark/c directory
 *
 **********/
#include <stdio.h>
#include <pthread.h>
#include "src/main/c/xfiles-user.h"
#include "src/main/c/xfiles-user.c"
#include "src/test/rv/dana-benchmark.h"

#define DEBUG

int main(int argc, char *argv[]){
    printf("%s: STARTED\n", argv[0]);
    int amount = argc;
	if(amount < 4){
		printf("Not enough input\nExiting ...\n");
		exit(1);
	}

	int t_number = argc - 3;
	int concurrent = atoi(argv[argc - 2]); //Number of transactions to be run concurrently
	if(concurrent > t_number){
		printf("Concurrent amount is greater than the total transactions\n");
		printf("Setting concurrency to the total number of transactions...\n");
		concurrent = t_number;
	}

	debug ("%i, %i\n", t_number, concurrent);
	struct Transaction transactions[t_number];
	//COMMAND LINE PARSING
	//Creates the transaction objects
	int file = 0;
	int k = 0;
	int j = 0;
	char net[50];
	char in[50];
	char out[50];
	for(int i = 1; i <= t_number; i++){
		j = 0;
		k = 0;
		file = 0;
		while (argv[i][j] != '\0'){
			if(argv[i][j] == ','){
				file++;
				if(file == 1){
					net[k] = '\0';
				}else if(file == 2){
					in[k] = '\0';
				}
				k = 0;
			}else{
				if(file == 0){
					net[k] = argv[i][j];
				}else if(file == 1){
					in[k] = argv[i][j];
				}else{
					out[k] = argv[i][j];
				}
				k++;
			}
			j++;
		}
		out[k] = '\0';
		strcopy(transactions[i - 1].net, net);
		transactions[i - 1].input = atoi(in);
		transactions[i - 1].output = atoi(out);
	}


	debug("\n\n*****Starting XFILES-DANA interaction*****\n");

	debug("\n\nPrinting preliminary information\n");
#ifdef DEBUG
    	xlen_t trial = xfiles_dana_id(4);
        debug("Trial is: %i\n", trial);
#endif

	//Table Setup
	asid_nnid_table * table;
	asid_type asid = 1;

	//Setting the ASID
	debug("\n\nSetting the new asid\n");
    xlen_t old_asid = pk_syscall_set_asid(asid);
    debug("The old asid was: %i\n", old_asid);

	//Creating the new nnid table
	debug("\n\nCreating the new nnid_table...\n");
	asid_nnid_table_create(&table, t_number, t_number);

	//Setting the ANTP
	debug("\n\nSetting the ANTP...\n");
	xlen_t antp = pk_syscall_set_antp(table);
	debug("The old ANTP was: %i\n", antp);

	//Printing table information
	debug("\n\nPrinting information about the new nnid_table\n");
#ifdef DEBUG
		asid_nnid_table_info(table);
#endif

	tid_type tids[t_number];

	for(int i = 0; i < t_number; i += concurrent){ //Assmbles the set amount of transactions for execution
		for(int j = i; j < i + concurrent; j++){ //Sets up <Concurrent> amount of transactions
			if(j < t_number){
                          printf("Net: %s\n", transactions[j].net);
				tids[j] = create_transaction(table, asid, &transactions[j]); //Creates tid for transaction
			}
		}
                // Start the concurrent transactions
		for(int j = i; j < i + concurrent; j++){
			if(j < t_number){
                          printf("Net: %s\n", transactions[j].net);
                          write_data_last(tids[j], transactions[j].input_array,
                                          transactions[j].input);
			}
                }

		for(int j = i; j < i + concurrent; j++){ //Reads <Concurrent> amount of transactions
			if(j < t_number){
				printf("\nReceiving output for TID: %i\n", tids[j]);
				read_data_spinlock(tids[j], transactions[j].output_array, transactions[j].output);
				printArr(transactions[j].output_array, transactions[j].output);
				debug("read_data_spinlock returned for TID: %i\n", tids[j]);
			}
		}


	}
}

//Creates a transaction on the table
tid_type create_transaction(asid_nnid_table * table, asid_type asid, Transaction * transaction){
	//Attaching a neural network
	debug("\n\nAttaching the neural network file %s to the table...\n", transaction->net);
	int nn_config = attach_nn_configuration(&table, asid, transaction->net);
	debug("NN number = %i\n", nn_config);

	//Creating a new write request
	printf("\nCreating a new write request...\n");
	tid_type tid = new_write_request(nn_config - 1, 0, 0);
	printf("NEW TID: %i\n", tid);

	//Creates input array
	debug("Creating input and output arrays...\n");
	transaction->input_array = malloc(transaction->input * sizeof(element_type));
	for(element_type i = 0; i < transaction->input; i++){
		transaction->input_array[i] = i;
	}

	printf("Input Array is: ");
	printArr(transaction->input_array, transaction->input);

	//Creates output array and fills in indices with a constant number
	transaction->output_array = malloc(transaction->output * sizeof(element_type));
	for(element_type i = 0; i < transaction->output; i++){
		transaction->output_array[i] = 13;
	}
	printf("Output Array is: ");
	printArr(transaction->output_array, transaction->output);


	//Writes to the transaction
	debug("Writing to the transaction...\n");
	xlen_t write = write_data_except_last(tid, transaction->input_array,
                                              transaction->input);
	debug("Write number is: %i\n", write);

	return tid;
}

void printTransaction(Transaction * transaction){
	debug("\n\n*****Transaction*****\n");
	debug("Neural Network: %s\n", transaction->net);
	debug("Number of Inputs: %i\n", transaction->input);
	debug("Number of Outputs: %i\n", transaction->output);
	debug("Input Array: "); printArr(transaction->input_array, transaction->input);
	debug("Output Array: "); printArr(transaction->output_array, transaction->output);
	debug("*****End of Transaction*****\n");

}

void debug(const char * output, ...){
#ifdef DEBUG
		printf(output);
#endif
}

void strcopy(char *file, char str[]){
	int i = 0;
	while(str[i] != '\0'){
		file[i] = str[i];
		i++;
	}
	file[i] = '\0';
}

void printArr(int * arr, int size){
  printf("[ ");
  for(int i = 0; i < size; i++){
    printf("%i ", arr[i]);
  }
  printf("]\n");
}
