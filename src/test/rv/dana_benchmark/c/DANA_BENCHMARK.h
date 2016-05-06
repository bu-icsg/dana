/**********
 * Author: Craig Einstein
 * 
 * File: DANA_BENCHMARK.h
 *
 * Description: Header file for DANA_BENCHMARK
 *		DANA_BENCHMARK runs transactional testing on the XFILES/DANA System
 *
 **********/
#ifndef DANA_BENCHMARK
#define DANA_BENCHMARK

/*Struct that contains transactional information
  Each struct contains:
  - A neural network
  - The number of inputs
  - The number of outputs
  - The input array
  - The output array
*/
typedef struct Transaction{
	char net[100];
	int input;
	int output;
	element_type * input_array;
	element_type * output_array;
} Transaction;

/*
	The main method of DANA_BENCHMARK.c does the following:
	- Parses the command line input (format defined in the README)
	- Creates the ASID_NNID Table, ASID, and ANTP
	- Queries the XFILES/DANA system with the desired number of concurrent transactions
	- Waits for the output of the desired number of concurrent outputs.
*/

/*Transaction Function
  This function completes the following:
	- Attaches the transaction's neural network to the asid_nnid table
	- Creates write request (and gets the tid)
	- Creates an input array
	- Creates an output array
	- Writes the input array to the transaction
	- Returns the tid
  Takes an asid_nnid table, an asid, and a transaction as input
*/
tid_type create_transaction(asid_nnid_table * table, asid_type asid, Transaction * transaction);


//Auxillary Functions

//Function that prints out debug messages if the user chooses
void debug(const char * output, ...);

//Copies a character array into another character array
void strcopy(char *file, char str[]);

//Nicely prints an array
void printArr(int * arr, int size);

//Nicely prints a transaction (if debugging is on)
void printTransaction(Transaction * transaction);



#endif