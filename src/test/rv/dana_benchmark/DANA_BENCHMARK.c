#include <stdio.h>
#include <pthread.h>
#include "c/xfiles-user.h"
#include "c/xfiles-user.c"
//Struct that holds a net file, a train file, and the thread info
struct Net{
	char net_file[100];
	char train_file[100];
	int thread_number;
  //	pthread_t thread;
};

//Copies a character array into another character array
void strcopy(char *file, char str[]){
	int i = 0;
	while(str[i] != '\0'){
		file[i] = str[i];
		i++;
	}
	file[i] = '\0';
}

//Prints the info contained in a thread
void *print_info(void *net_info){
	struct Net *nets;
	nets = (struct Net *) net_info;

	printf("%s, %s\n", nets->net_file, nets->train_file);
}

void printArr(int * arr, int size){
  printf("[ ");
  for(int i = 0; i < size; i++){
    printf("%i ", arr[i]);
  }
  printf("]\n");
}

//Converts a character into an integer
/*int atoi(char directive[]){
	int i = 0;
	int ret = 0;
	while(directive[i] != '\0'){
		ret = ret*10 +  directive[i] - '0';
		i++;
	}
	return ret;
	}*/

uint64_t * inputCreation(int size){

  uint64_t * input;
  input= malloc(size *sizeof(int)); //Allocate space for the array                                       
  for(uint64_t i = 0; i < size; i++){
    input[i] = i;
  }

  return input;

}

int main(int argc, char *argv[]){
    printf("%s: STARTED\n", argv[0]);
	if(argc < 3){
		printf("Not enough input\nExiting ...\n");
		return 0;
	}
	int directive = atoi(argv[argc - 1]);
	struct Net nets[argc - 2];
	//Parse input files
	int file = 0;
	int k = 0;
	int j = 0;
	char n_file[50];
	char t_file[50];
	for(int i = 1; i < argc  - 1; i++){
		j = 0;
		k = 0;
		file = 0;
		while (argv[i][j] != '\0'){
			if(argv[i][j] == ','){
				file++;
				n_file[k] = '\0';
				k = 0;
			}else{
				if(file == 0){
					n_file[k] = argv[i][j];
				}else{
					t_file[k] = argv[i][j];
				}
				k++;
			}			
			j++;
		}
		t_file[k] = '\0';
		strcopy(nets[i - 1].net_file, n_file);
		strcopy(nets[i - 1].train_file, t_file);

	}

	/*	//Thread creation and execution
	for(int i = 0; i < sizeof(nets)/sizeof(struct Net); i += directive){
		for(int j = i; j < directive + i && j < sizeof(nets)/sizeof(struct Net); j++){
			nets[j].thread_number = pthread_create(&nets[j].thread, NULL, &print_info, &nets[j]);
		}

		for(int j = i; j < directive + i && j < sizeof(nets)/sizeof(struct Net); j++){
			pthread_join(nets[j].thread, NULL);
		}
		}*/

	printf("\n\n*****Starting XFILES-DANA interaction*****\n");

	printf("\n\nPrinting preliminary information\n");
    xlen_t trial = xfiles_dana_id(4);
	
	//Table Setup
	asid_nnid_table * table;
	asid_type asid_number = 1;

	//Setting the ASID
	printf("\n\nSetting the new asid\n");
    xlen_t asid = pk_syscall_set_asid(asid_number);
    printf("The old asid was: %i\n", asid);

	//Creating the new nnid table
	printf("\n\nCreating the new nnid_table...\n");
	asid_nnid_table_create(&table, 2, 1);

	//Setting the ANTP
	printf("\n\nSetting the ANTP...\n");
	xlen_t antp = pk_syscall_set_antp(table);
	printf("The old ANTP was: %i\n", antp);
	
	//Printing table information
	printf("\n\nPrinting information about the new nnid_table\n");
	asid_nnid_table_info(table);

	//Attaching a neural network
	printf("\n\nAttaching the neural network file %s to the table...\n", nets[0].net_file);
	int nn_config = attach_nn_configuration(&table, asid_number, nets[0].net_file);	
	printf("NN number = %i\n", nn_config);

	//Creating a new write request
	printf("\n\nCreating a new write request...\n");
	tid_type tid = new_write_request(nn_config - 1, 0, 0);
	printf("The tid is: %i\n", tid);

	//Creates the input and output arrays
	printf("\n\nCreating input and output arrays...\n");
	int num_input = 10;
	element_type * inputs = malloc(num_input * sizeof(element_type));
	for(element_type i = 0; i < num_input; i++){
		inputs[i] = i;
	}
	printf("Input Array is: ");
	printArr(inputs, num_input);
	int num_output = 1;
	element_type * outputs = malloc(num_output * sizeof(element_type));
	for(element_type i = 0; i < num_output; i++){
		outputs[i] = 13;
	}
	printf("Output Array is: ");
	printArr(outputs, num_output);


	//Writes to the transaction
	printf("\n\nWriting to the transaction...\n");
	xlen_t write = write_data(tid, inputs, num_input);
	printf("Write number is: %i\n", write);


	printf("\n\nReading from the transaction\n");
	int i = 0;
	uint64_t output;
	while(1){
	  printf("Loop Cycle Number %i\n", i++);
	  output = read_data_spinlock(tid, outputs, num_output);
	  printArr(outputs, num_output);
	  printf("read_data_spinlock returning: %i, output array: %i\n", output, outputs[0]);
	  if(output){
	    break;
	  }

	}

	printf("YO!");
}
