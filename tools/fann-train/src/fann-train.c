#include <stdio.h>
#include <getopt.h>
#include <string.h>
#include <time.h>
#include "fann.h"

static char * usage_message =
  "fann-train -n[config] -t[train file] [options]\n"
  "Run batch training on a specific neural network and training file.\n"
  "\n"
  "Options:\n"
  "  -a, --test-train-file      the floating point FANN training file for test to use\n"
  "  -b, --video-data           generate a trace of execution over time\n"
  "  -c, --stat-cups            print information about the # of connectsion\n"
  "  -d, --num-batch-items      number of batch items to use\n"
  "  -e, --max-epochs           the epoch limit (default 10k)\n"
  "  -f, --bit-fail-limit       sets the bit fail limit (default 0.05)\n"
  "  -g, --mse-fail-limit       sets the maximum MSE (default -1, i.e., off)\n"
  "  -h, --help                 print this help and exit\n"
  "  -i, --id                   numeric id to use for printing data (default 0)\n"
  "  -l, --stat-last            print last epoch number statistic\n"
  "  -m, --stat-mse             print mse statistics (optional arg: MSE period)\n"
  "  -n, --nn-config            the binary NN configuration to use\n"
  "  -o, --stat-bit-fail        print bit fail percent (optional arg: period)\n"
  "  -p, --mse-data             generates the values of mse for training\n"
  "  -q, --stat-percent-correct print the percent correct (optional arg: period)\n"
  "  -r, --learning-rate        set the learning rate (default 0.7)\n"
  "  -t, --train-file           the fixed point FANN training file to use\n"
  "  -s, --mse-threshold        set the mse validation threshold for cross validation\n"
  "  -u, --set-size-factor      set the size factor for validation and test sets\n"
  "  -v, --verbose              turn on per-item inputs/output printfs\n"
  "  -x, --training-type        no arg: incremental, arg: use specific enum\n"
  "  -z, --ignore-limits        continue blindly ignoring bit fail/mse limits"
  "\n"
  "-n and -t are required\n";

void usage () {
  printf("Usage: %s", usage_message);
}



void select_test_set(struct fann_train_data * data, struct fann_train_data * data_test, int test_set_size, int data_size, int num_input, int num_output, char * file_train) {
  
  int i, j, index_random;
  char * temp;
  char * value = "_test";
  char * file_output_test_string = NULL;
  FILE * file_output_test;
  unsigned long int seed = 1000;
  
  /*struct timeval t1;
  gettimeofday(&t1, NULL);
  srand(t1.tv_usec * t1.tv_sec);*/
  srand(seed);

  temp = malloc(strlen(file_train) + strlen(value) + 1);
    
  strcpy(temp, file_train);
  strcat(temp, value);
  file_output_test_string = temp;
  file_output_test = fopen(file_output_test_string, "w");

  fprintf(file_output_test, "%d %d %d\n", test_set_size, num_input, num_output);
  for (i = 0; i < test_set_size; i++) {
    index_random = rand() % (test_set_size - i);
    printf("%d\n", index_random);
    for (j = 0; j < num_input; j++) {
      data_test->input[i][j] = data->input[index_random][j];
      fprintf(file_output_test, "%f ", data_test->input[i][j]);
      data->input[index_random][j] = data->input[data_size - i -1][j];
      data->input[data_size - i - 1][j] = data_test->input[i][j];
    }
    fprintf(file_output_test, "\n");
    for (j = 0; j < num_output; j++) {
      data_test->output[i][j] = data->output[index_random][j];
      fprintf(file_output_test, "%f ", data_test->output[i][j]);
      data->output[index_random][j] = data->output[data_size - i -1][j];
      data->output[data_size - i - 1][j] = data_test->output[i][j];
    }
    fprintf(file_output_test, "\n");
  }

  if (file_output_test != NULL)
    fclose(file_output_test);

}


void select_training_validation_sets(struct fann_train_data * data, struct fann_train_data * data_training, struct fann_train_data * data_validation, int validation_set_size, int data_size, int iteration, int num_input, int num_output, char * file_train) {
  
  int i, j, index_training, index_validation;
  char * temp;
  char * value = "_validation";
  char * file_output_training_string = NULL, * file_output_validation_string = NULL;
  FILE * file_output_training, * file_output_validation;
  temp = malloc(strlen(file_train) + strlen(value) + 1);
    
  strcpy(temp, file_train);
  strcat(temp, value);
  file_output_validation_string = temp;
  file_output_validation = fopen(file_output_validation_string, "w");

  value = "_training";
  strcpy(temp, file_train);
  strcat(temp, value);
  file_output_training_string = temp;
  file_output_training = fopen(file_output_training_string, "w");  

  fprintf(file_output_validation, "%d %d %d\n", validation_set_size, num_input, num_output);
  fprintf(file_output_training, "%d %d %d\n", data_size - validation_set_size, num_input, num_output);
  index_training = 0;
  for (i = 0; i < validation_set_size * iteration; i++) {
    for(j = 0; j < num_input; j++) {
	data_training->input[index_training][j] = data->input[i][j];
	fprintf(file_output_training, "%f ", data->input[i][j]);
    }
    fprintf(file_output_training, "\n");
    for(j = 0; j < num_output; j++) {
	data_training->output[index_training][j] = data->output[i][j];
	fprintf(file_output_training, "%f ", data->output[i][j]);
    }
    fprintf(file_output_training, "\n");
    index_training++;
  }

  index_validation = 0;
  for (i = validation_set_size * iteration; i < validation_set_size * (iteration + 1); i++) {
    for(j = 0; j < num_input; j++) {
	data_validation->input[index_validation][j] = data->input[i][j];
	fprintf(file_output_validation, "%f ", data->input[i][j]);
    }
    fprintf(file_output_validation, "\n");
    for(j = 0; j < num_output; j++) {
	data_validation->output[index_validation][j] = data->output[i][j];
	fprintf(file_output_validation, "%f ", data->output[i][j]);
    }
    fprintf(file_output_validation, "\n");
    index_validation++;
  }

  for (i = validation_set_size * (iteration + 1); i < data_size; i++) {
    for(j = 0; j < num_input; j++) {
	data_training->input[index_training][j] = data->input[i][j];
	fprintf(file_output_training, "%f ", data->input[i][j]);
    }
    fprintf(file_output_training, "\n");
    for(j = 0; j < num_output; j++) {
	data_training->output[index_training][j] = data->output[i][j];
	fprintf(file_output_training, "%f ", data->output[i][j]);
    }
    fprintf(file_output_training, "\n");
    index_training++;
  }
  if (file_output_training != NULL)
    fclose(file_output_training);
  if (file_output_validation != NULL)
    fclose(file_output_validation);
}



int main (int argc, char * argv[]) {
  int i, epoch, k, num_bits_failing, num_correct, num_correct_validation, num_correct_test, num_correct_old, num_correct_validation_old, num_data, num_data_training, num_data_validation, num_data_test, num_training_iterations, iteration;
  int max_epochs = 10000, exit_code = 0, batch_items = -1;
  int flag_cups = 0, flag_last = 0, flag_mse = 0, flag_verbose = 0,
    flag_bit_fail = 0, flag_ignore_limits = 0, flag_percent_correct = 0;
  int mse_reporting_period = 1, bit_fail_reporting_period = 1,
    percent_correct_reporting_period = 1;
  float bit_fail_limit = 0.05, mse_fail_limit = -1.0;
  double learning_rate = 0.7;
  char id[100] = "0";
  double validation_size_factor = 0.1, mse_validation_threshold = 0.0, test_size_factor = 0.1;
  double mse_validation = 0.0, mse_test = 0.0, mse_old, mse_validation_old;
  char * file_video_string = NULL, * file_video_validation_string = NULL, * file_video_test_string = NULL, * file_mse_training_string = NULL, * file_mse_validation_string = NULL, * file_mse_test_string = NULL;
  FILE * file_video = NULL, * file_video_validation = NULL, * file_video_test = NULL;
  FILE * file_mse_training = NULL, * file_mse_validation = NULL, * file_mse_test = NULL;
  struct fann * ann = NULL;
  struct fann_train_data * data = NULL;
  struct fann_train_data * data_training = NULL;
  struct fann_train_data * data_validation = NULL;
  struct fann_train_data * data_test = NULL;
  fann_type * calc_out;
  enum fann_train_enum type_training = FANN_TRAIN_BATCH;

  char * file_nn = NULL, * file_train = NULL, * file_test_train = NULL;
  int c;
  while (1) {
    static struct option long_options[] = {
      {"test-train-file",           required_argument, 0, 'a'},
      {"video-data",                required_argument, 0, 'b'},
      {"stat-cups",                 no_argument,       0, 'c'},
      {"num-batch-items",           required_argument, 0, 'd'},
      {"max-epochs",                required_argument, 0, 'e'},
      {"bit-fail-limit",            required_argument, 0, 'f'},
      {"mse-fail-limit",            required_argument, 0, 'g'},
      {"help",                      no_argument,       0, 'h'},
      {"id",                        required_argument, 0, 'i'},
      {"stat-last",                 no_argument,       0, 'l'},
      {"stat-mse",                  optional_argument, 0, 'm'},
      {"nn-config",                 required_argument, 0, 'n'},
      {"stat-bit-fail",             optional_argument, 0, 'o'},
      {"mse-data",                  required_argument, 0, 'p'},
      {"stat-percent-correct",      optional_argument, 0, 'q'},
      {"learning-rate",             required_argument, 0, 'r'},
      {"mse-validation-threshold",  required_argument, 0, 's'},
      {"train-file",                required_argument, 0, 't'},
      {"set-size-factor",           required_argument, 0, 'u'},
      {"verbose",                   no_argument,       0, 'v'},
      {"incremental",               optional_argument, 0, 'x'},
      {"ignore-limits",             no_argument,       0, 'z'}
    };
    int option_index = 0;
     c = getopt_long (argc, argv, "a:b:cd:e:f:g:hi:lm::n:o::p:q::r:s:t:vu:x::z",
                     long_options, &option_index);
    if (c == -1)
      break;
    switch (c) {
    case 'a':
      file_test_train = optarg;
      test_size_factor = 0;
      break;
    case 'b':
      file_video_string = optarg;
      break;
    case 'c':
      flag_cups = 1;
      break;
    case 'd':
      batch_items = atoi(optarg);
      break;
    case 'e':
      max_epochs = atoi(optarg);
      break;
    case 'f':
      bit_fail_limit = atof(optarg);
      break;
    case 'g':
      mse_fail_limit = atof(optarg);
      break;
    case 'h':
      usage();
      exit_code = 0;
      goto bail;
      break;
    case 'i':
      strcpy(id, optarg);
      break;
    case 'l':
      flag_last = 1;
      break;
    case 'm':
      if (optarg)
        mse_reporting_period = atoi(optarg);
      flag_mse = 1;
      break;
    case 'n':
      file_nn = optarg;
      break;
    case 'o':
      if (optarg)
        bit_fail_reporting_period = atoi(optarg);
      flag_bit_fail = 1;
      break;
    case 'p':
      file_mse_training_string = optarg;
      break;
    case 'q':
      if (optarg)
        percent_correct_reporting_period = atoi(optarg);
      flag_percent_correct = 1;
      break;
    case 'r':
      learning_rate = atof(optarg);
      break;
    case 's':
      mse_validation_threshold = atof(optarg);
      break;
    case 't':
      file_train = optarg;
      break;
    case 'u':
      validation_size_factor = atof(optarg);
      if(test_size_factor != 0)
	test_size_factor = atof(optarg);
      break;
    case 'v':
      flag_verbose = 1;
      break;
    case 'x':
      type_training = (optarg) ? atoi(optarg) : FANN_TRAIN_INCREMENTAL;
      break;
    case 'z':
      flag_ignore_limits = 1;
      break;
    }
  };

  // Make sure there aren't any arguments left over
  if (optind != argc) {
    fprintf(stderr, "[ERROR] Bad argument\n\n");
    usage();
    exit_code = -1;
    goto bail;
  }

  // Make sure we have all required inputs
  if (file_nn == NULL || file_train == NULL) {
    fprintf(stderr, "[ERROR] Missing required input argument\n\n");
    usage();
    exit_code = -1;
    goto bail;
  }

  // The training type needs to make sense
  if (type_training > FANN_TRAIN_SARPROP) {
    fprintf(stderr, "[ERROR] Training type %d outside of enumerated range (max: %d)\n",
            type_training, FANN_TRAIN_SARPROP);
    exit_code = -1;
    goto bail;
  }

  ann = fann_create_from_file(file_nn);
  data = fann_read_train_from_file(file_train);
  if (batch_items != -1 && batch_items < data->num_data)
    data->num_data = batch_items;
  enum fann_activationfunc_enum af =
    fann_get_activation_function(ann, ann->last_layer - ann->first_layer -1, 0);

  ann->training_algorithm = type_training;
  ann->learning_rate = learning_rate;

  size_t num_input = data->num_input;
  size_t num_output = data->num_output;

  if(file_test_train != NULL) {
    data_test = fann_read_train_from_file(file_test_train);
    num_data_test = fann_length_train_data(data_test);
    num_data = fann_length_train_data(data);
  }
  else {
    num_data_test = (int) floor(fann_length_train_data(data) * test_size_factor);
    data_test = fann_create_train(num_data_test, num_input, num_output);
    select_test_set(data, data_test, num_data_test, fann_length_train_data(data), num_input, num_output, file_train);
    num_data = (int) ceil(fann_length_train_data(data) * (1 - test_size_factor));
  }
  
  num_data_validation = (int) floor(fann_length_train_data(data) * validation_size_factor);
  num_data_training =  num_data - num_data_validation;
  num_training_iterations = (int) ((1 - test_size_factor) / validation_size_factor);
  data_training = fann_create_train(num_data_training, num_input, num_output);
  data_validation = fann_create_train(num_data_validation, num_input, num_output);


  printf("[INFO] Using training type %d\n", type_training);

  if (file_video_string != NULL) {
    file_video = fopen(file_video_string, "w");

    char * temp;
    char * value = "_valdiation";
    temp = malloc(strlen(file_video_string) + strlen(value) + 1);
    
    strcpy(temp, file_video_string);
    strcat(temp, value);
    file_video_validation_string = temp;
    file_video_validation = fopen(file_video_validation_string, "w");

    value = "_test";
    strcpy(temp, file_video_string);
    strcat(temp, value);
    file_video_test_string = temp;
    file_video_test = fopen(file_video_test_string, "w");   
  }

  if (file_mse_training_string != NULL) {
    file_mse_training = fopen(file_mse_training_string, "w");

    char * temp;
    char * value = "_valdiation";
    temp = malloc(strlen(file_mse_training_string) + strlen(value) + 1);
    
    strcpy(temp, file_mse_training_string);
    strcat(temp, value);
    file_mse_validation_string = temp;
    file_mse_validation = fopen(file_mse_validation_string, "w");

    value = "_test";
    strcpy(temp, file_mse_training_string);
    strcat(temp, value);
    file_mse_test_string = temp;
    file_mse_test = fopen(file_mse_test_string, "w");   
  }

  double mse;
  mse_old = -1.0;
  mse_validation_old = -1.0;
  num_correct_old = 0;
  num_correct_validation_old = 0;
 
  // num_training_iterations is set to 1 to test one pass through epochs while considering the same validation set. This line should be commeted for a complete pass of validation set over training data
  num_training_iterations = 1;
  for (iteration = 0; iteration < num_training_iterations; iteration++) { 
    //This line should be uncommented for a complete pass of validation set over training data 
    //select_training_validation_sets(data, data_training, data_validation, num_data_validation, num_data, iteration, num_input, num_output);

    // For testing a well distributed part of training data as validation set, second iteration is considered for generating the validation set
    select_training_validation_sets(data, data_training, data_validation, num_data_validation, num_data, 1, num_input, num_output, file_train);
    
    for (epoch = 0; epoch < max_epochs; epoch++) {
      fann_train_epoch(ann, data_training);
      num_bits_failing = 0;
      num_correct = 0;
      fann_reset_MSE(ann);
      for (i = 0; i < num_data_training; i++) {
	calc_out = fann_test(ann, data_training->input[i], data_training->output[i]);
	if (flag_verbose) {
	  printf("[INFO] ");
	  for (k = 0; k < data_training->num_input; k++) {
	    printf("%8.5f ", data_training->input[i][k]);
	  }
       }
       int correct = 1;
       for (k = 0; k < data_training->num_output; k++) {
	  if (flag_verbose)
	    printf("%8.5f ", calc_out[k]);
	  num_bits_failing +=
	    fabs(calc_out[k] - data_training->output[i][k]) > bit_fail_limit;
	  if (fabs(calc_out[k] - data_training->output[i][k]) > bit_fail_limit)
	    correct = 0;
	  if (file_video)
	    fprintf(file_video, "%f ", calc_out[k]);
	}
	if (file_video)
	  fprintf(file_video, "\n");
	num_correct += correct;
	if (flag_verbose) {
	  if (i < num_data_training - 1)
	    printf("\n");
	}
      }

      if (flag_verbose)
	printf("%5d\n\n", epoch);
      if (flag_mse) {
	mse = fann_get_MSE(ann);
	switch(af) {
	case FANN_LINEAR_PIECE_SYMMETRIC:
	case FANN_THRESHOLD_SYMMETRIC:
	case FANN_SIGMOID_SYMMETRIC:
	case FANN_SIGMOID_SYMMETRIC_STEPWISE:
	case FANN_ELLIOT_SYMMETRIC:
	case FANN_GAUSSIAN_SYMMETRIC:
	case FANN_SIN_SYMMETRIC:
	case FANN_COS_SYMMETRIC:
	  mse *= 4.0;
	default:
	  break;
	}
      }

      num_correct_validation = 0;
      fann_reset_MSE(ann);
      for (i = 0; i < num_data_validation; i++) {
	calc_out = fann_test(ann, data_validation->input[i], data_validation->output[i]);
	if (flag_verbose) {
	  printf("[INFO] ");
	  for (k = 0; k < data_validation->num_input; k++) {
	    printf("%8.5f ", data_validation->input[i][k]);
	  }
	}
	int correct_validation = 1;
	for (k = 0; k < data_validation->num_output; k++) {
	  if (flag_verbose)
	    printf("%8.5f ", calc_out[k]);
	  num_bits_failing +=
	    fabs(calc_out[k] - data_validation->output[i][k]) > bit_fail_limit;
	  if (fabs(calc_out[k] - data_validation->output[i][k]) > bit_fail_limit)
	    correct_validation = 0;
	  if (file_video_validation)
	    fprintf(file_video_validation, "%f ", calc_out[k]);
	}
	if (file_video_validation)
	  fprintf(file_video_validation, "\n");
	num_correct_validation += correct_validation;
	if (flag_verbose) {
	  if (i < num_data_validation - 1)
	    printf("\n");
	}
      }

      if (flag_verbose)
	printf("%5d\n\n", epoch);
      if (flag_mse ) {
	mse_validation = fann_get_MSE(ann);
	switch(af) {
	case FANN_LINEAR_PIECE_SYMMETRIC:
	case FANN_THRESHOLD_SYMMETRIC:
	case FANN_SIGMOID_SYMMETRIC:
	case FANN_SIGMOID_SYMMETRIC_STEPWISE:
	case FANN_ELLIOT_SYMMETRIC:
	case FANN_GAUSSIAN_SYMMETRIC:
	case FANN_SIN_SYMMETRIC:
	case FANN_COS_SYMMETRIC:
	  mse_validation *= 4.0;
	default:
	  break;
	}
      }


    num_correct_test = 0;
    fann_reset_MSE(ann);
    for (i = 0; i < num_data_test; i++) {
      calc_out = fann_test(ann, data_test->input[i], data_test->output[i]);
      if (flag_verbose) {
	printf("[INFO] ");
      for (k = 0; k < data_test->num_input; k++) {
	    printf("%8.5f ", data_test->input[i][k]);
      }
    }
      int correct_test = 1;
      for (k = 0; k < data_test->num_output; k++) {
	if (flag_verbose)
	  printf("%8.5f ", calc_out[k]);
        num_bits_failing +=
	  fabs(calc_out[k] - data_test->output[i][k]) > bit_fail_limit;
	if (fabs(calc_out[k] - data_test->output[i][k]) > bit_fail_limit)
	  correct_test = 0;
	if (file_video_test)
	  fprintf(file_video_test, "%f ", calc_out[k]);
      }
      if (file_video_test)
	  fprintf(file_video_test, "\n");
	num_correct_test += correct_test;
	if (flag_verbose) {
	  if (i < num_data_test - 1)
	    printf("\n");
	}
     }

     if (flag_verbose)
       printf("%5d\n\n", epoch);
     if (flag_mse ) {
       mse_test = fann_get_MSE(ann);
       switch(af) {
       case FANN_LINEAR_PIECE_SYMMETRIC:
       case FANN_THRESHOLD_SYMMETRIC:
       case FANN_SIGMOID_SYMMETRIC:
       case FANN_SIGMOID_SYMMETRIC_STEPWISE:
       case FANN_ELLIOT_SYMMETRIC:
       case FANN_GAUSSIAN_SYMMETRIC:
       case FANN_SIN_SYMMETRIC:
       case FANN_COS_SYMMETRIC:
	 mse_test *= 4.0;
       default:
	 break;
      }
    }
      
  
    if (flag_mse && (epoch % mse_reporting_period == 0)) {
      printf("[STAT] epoch %d id %s mse %8.8f mse_validation %8.8f mse_test %8.8f\n", epoch, id, mse, mse_validation, mse_test);
      if (file_mse_training)
	  fprintf(file_mse_training, "%f\n", mse);
      if (file_mse_validation)
	  fprintf(file_mse_validation, "%f\n", mse_validation);
      if (file_mse_test)
	  fprintf(file_mse_test, "%f\n", mse_test);
    }
    if (flag_bit_fail && (epoch % bit_fail_reporting_period == 0))
      printf("[STAT] epoch %d id %s bfp %8.8f\n", epoch, id,
             1 - (double) num_bits_failing / data->num_output /
             num_data);
    if (flag_percent_correct && (epoch % percent_correct_reporting_period == 0))
      printf("[STAT] epoch %d id %s perc %8.8f perc_validation %8.8f perc_test %8.8f\n", epoch, id, (double) num_correct / num_data_training, (double) num_correct_validation / num_data_validation, (double) num_correct_test / num_data_test);
    if (!flag_ignore_limits && (num_bits_failing == 0 || mse < mse_fail_limit))
      goto finish;
    // printf("%8.5f\n\n", fann_get_MSE(ann));
    
    if (flag_mse) {
	if (((mse_validation - mse_validation_old) > mse_validation_threshold) && (mse - mse_old) < 0) {
	  //printf("[INFO] breakpoint\n");
	  //break;
	}
    }
    else if (flag_percent_correct) {
	double perc_correct, perc_correct_old, perc_correct_validation, perc_correct_validation_old;
	perc_correct = (double) num_correct / num_data_training;
	perc_correct_old = (double) num_correct_old / num_data_training;
	perc_correct_validation = (double) num_correct_validation / num_data_validation;
	perc_correct_validation_old = (double) num_correct_validation_old / num_data_validation;

        if ((perc_correct_validation - perc_correct_validation_old) < mse_validation_threshold && (perc_correct - perc_correct_old) > 0) {
	  //printf("[INFO] breakpoint\n");
	  //break;
	}
      }

      mse_validation_old = mse_validation;
      mse_old = mse;
      num_correct_old = num_correct;
      num_correct_validation_old = num_correct_validation;
    }
  }

 finish:
  if (flag_last)
    printf("[STAT] x 0 id %s epoch %d\n", id, epoch);
  if (flag_cups)
    printf("[STAT] x 0 id %s cups %d / ?\n", id,
           epoch * fann_get_total_connections(ann));

 bail:
  if (ann != NULL)
    fann_destroy(ann);
  if (data != NULL)
    fann_destroy_train(data);
  if (file_video != NULL)
    fclose(file_video);
  if (file_video_validation != NULL)
    fclose(file_video_validation);
  if (file_video_test != NULL)
    fclose(file_video_test);
  if (file_mse_training != NULL)
    fclose(file_mse_training);
  if (file_mse_validation != NULL)
    fclose(file_mse_validation);
  if (file_mse_test != NULL)
    fclose(file_mse_test);
  return exit_code;
}
