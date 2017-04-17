#!/usr/bin/env python3
"""
For manipulating fann data files.
Assumes arg 1 is input data file.
Assume a well formed input file.
I don't really like the use of hstack / vstack, fix.
"""
import sys
import numpy as np
import getopt

def read_file(file_name):
    """
    Turns space separated data file into a dictionary of np arrays
    and some floats.
    """
    f = open(file_name)
    l = f.readline().split()

    num_pairs = l[0]
    num_in = l[1]
    num_out = l[2]

    in_vals = [ float(x) for x in np.array(f.readline().split()) ]
    out_vals = [ float(x) for x in np.array(f.readline().split()) ]
    while True:
        line1 = f.readline()
        line2 = f.readline()
        if not line2:
            break
        in_v = [ float(x) for x in np.asarray(line1.split())]
        out_v = [ float(x) for x in np.asarray(line2.split())]
        in_vals = np.vstack((in_vals, in_v))
        out_vals = np.vstack((out_vals, out_v))

    f.close()
    return {
        'num_pairs' : num_pairs,
        'num_in'    : num_in,
        'num_out'   : num_out,
        'in_vals'   : in_vals,
        'out_vals'  : out_vals
    }

def normalize_data(data, min_r, max_r):
    """
    Wrapper for normalization.
    """
    data['in_vals']  = normalize_cols_individually(data['in_vals'], min_r, max_r)
    data['out_vals'] = normalize_cols_individually(data['out_vals'], min_r, max_r)
    return data

def normalize_cols_individually(vals_2d, min_r, max_r):
    """
    Manages separating cols from 2d array passes them for normalization
    then returns the 2d array with each col normalized individually.
    """
    #Do this so we can hstack later
    tmp = np.empty( ( len(vals_2d), 0) )

    #Transverse allows us to iterate over cols, not rows .
    for col in vals_2d.T:
        col = normalize_vec(col, min_r, max_r)
        tmp = np.hstack((tmp, col[np.newaxis].T))
    return tmp

def normalize_vec(vec, min_r, max_r):
    """
    Data needs to be normalized on a per col basis.
    Would like to do this smarter ie 3 sigma.
    """
    min_r = min_r
    max_r = max_r
    diff_r = max_r-min_r

    min_data = np.amin(vec)
    max_data = np.amax(vec)
    assert min_r < max_r
    if not min_data < max_data:
        return np.zeros(len(vec))
    diff_data = max_data-min_data

    #rescales data to new range
    vec = vec * (diff_r / diff_data)

    min_data_prime = np.amin(vec)
    #shifts data to new min
    vec = vec + (min_r - min_data_prime)

    return vec

def simple_write_np_array(arr):
    res_str = ""
    for elem in arr:
        res_str += str(elem) + " "
    return res_str

def write_data_to_file(data, file_name):
    f = open(file_name, 'w')

    in_tmp = data['in_vals']
    out_tmp = data['out_vals']
    assert len(in_tmp) == len(out_tmp)
    print(data['num_pairs'], data['num_in'], data['num_out'], file=f)

    while len(in_tmp) > 0:
        print(simple_write_np_array(in_tmp[0]), file=f)
        print(simple_write_np_array(out_tmp[0]), file=f)
        in_tmp = in_tmp[1:]
        out_tmp = out_tmp[1:]
    f.close()


def add_noise_to_input(data, amt):
    """
    This should make the classification problem harder.
    Meant to only be applied to data that is already normalized.

    """
    data['in_vals'] = data['in_vals'] + (amt *(np.random.random((int(data['num_pairs']), int(data['num_in'])))))
    return data

def gen_rand_input_output_pairs(num_wav_ln, count):
    """
    adds a random sin input/output num_pair. The sin calculation
    expects radians, so we draw from -pi to pi.
    """
    # This draws equally likely domain values
    s = np.random.uniform(-np.pi, np.pi, count)
    # The sin output will be in range -1 to 1 for
    # Num_wav_ln >= .5 if the boundaries are hit.

    t = np.sin( s * num_wav_ln  )

    return s[np.newaxis].T, t[np.newaxis].T

def generate_sin_data(num_wav_ln, num_pairs=100, num_in=1, num_out=1):
    in_vals, out_vals = gen_rand_input_output_pairs(num_wav_ln, num_pairs)
    return {
        'num_pairs' : num_pairs,
        'num_in'    : num_in,
        'num_out'   : num_out,
        'in_vals'   : in_vals,
        'out_vals'  : out_vals
    }

def usage():
    """
    Yells at user when they mess up input.
    """
    print("operates on fann data files. ")
    print("dataset_tool hni:o:r:")
    print("i and o required")
    print("required -i input and -o ouput files")
    print("support for:")
    print("-n normalizing input ")
    print("-r: injecting random noise on inputs")
    print("python dataset_tool.py -i iris_train_orig.data -o iris_normal -n")

"""throw this in a data structure"""
def process_cl_args():
    in_file = False
    out_file = False
    norm = False
    rand = False
    amt = 0
    min_r = -1
    max_r = 1
    num_wav_ln = 0
    try:
        opts, args = getopt.getopt(sys.argv[1:], 'hni:o:r:s:', ["min=", "max="])
    except getopt.GetoptError:
        usage()
        sys.exit(2)

    for opt, arg in opts:
        if opt in '-h':
            usage()
            sys.exit()

        elif opt in '-i':
            in_file = arg
            print("in file = ", in_file)

        elif opt in '-o':
            out_file = arg
            print("out file = ", out_file)

        elif opt in '-s':
            #sin
            num_wav_ln = float(arg)

        elif opt in '-n':
            #normalization
            norm = True

        elif opt in '-r':
            #randomization
            rand = True
            amt = float(arg)
            if amt < 0:
                print("random amt must be > 0")
                sys.exit(2)

        elif opt in '--min':
            min_r = float(arg)

        elif opt in '--max':
            max_r = float(arg)

        else:
            usage()
            sys.exit(2)
    return {
    'in_file'    : in_file,
    'out_file'   : out_file,
    'norm'       : norm,
    'rand'       : rand,
    'amt'        : amt,
    'min_r'      : min_r,
    'max_r'      : max_r,
    'num_wav_ln' : num_wav_ln
    }

control = process_cl_args()
#data = {}
#If we have input and output files
#and
#we're only doing norm or rand.
if not(control['out_file']):
    print("specify -i input and -o output files")
    sys.exit()
assert control['min_r'] < control['max_r']

#we have input and output files, we are doing norm xor rand
if control['in_file']:
    data = read_file(control['in_file'])

if control['norm']:
    data = normalize_data(data, control['min_r'], control['max_r'])
#This only really makes sense if you do it to normalized files.
#Then there may be elements outside 0,1, so have to normalize again.
if control['rand']:
    #This operation may violate the normalization, so fix that.
    data = add_noise_to_input(data, control['amt'])
    data = normalize_data(data, control['min_r'], control['max_r'])

if control['num_wav_ln'] > 0:
    data = generate_sin_data(control['num_wav_ln'])

write_data_to_file(data, control['out_file'])
"""
data = read_file(sys.argv[1])
data = normalize_input_data(data)
data = add_noise_to_input(data, sys.argv[3])
print (data['in_vals'])
write_data_to_file(data, sys.argv[2]+'_.'+sys.argv[3])

"""
