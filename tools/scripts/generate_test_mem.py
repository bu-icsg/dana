#!/usr/bin/env python3

import subprocess
import argparse
import os
#from generate_ant import ant
import sys
import ipdb

path_generate_ant = './generate-ant'
path_fann_eval_fixed = '../bin/fann-eval-fixed'
path_bits_to_asm = '../../util/hdl-tools/scripts/bits-to-asm'
asid = 1


def twos_complement(val, width):
	format_string = "  .word 0x{0:0" + str(width >> 2) + "x}"
	if (val & (1 << (width - 1))) != 0:
		return format_string.format(int(((-1 * val) ^ 0xffffffff) + 1))
	else:
		return format_string.format(val)

def write_ant_file(net_name):
	net_file_path = os.path.join('../../build/nets/', net_name + '.net')
	train_file_path = os.path.join('../../build/nets/', net_name + '.train')
	ant_file_path = os.path.join('../../build/nets/', net_name + '.ant')
	data_in_file_path = os.path.join('../../build/nets/', net_name + '.train')
	sixteen_bin_file_path = os.path.join('../../build/nets/', net_name + '.16bin')
	# If the data_in file is not available or the ant file can't be written,
	# then abort
	try:
		data_in_file = open(data_in_file_path)	
		ant_file = open(ant_file_path, 'w')
	except FileNotFoundError:
		return	
	data_in_list = []
	for line in data_in_file:
		data_in_list.extend([int(x) for x in line.split()])
	
	# Append data_in
	ant_file.write('data_in:\n')
	for v in [twos_complement(i, 32) for i in data_in_list]:
		ant_file.write(v + '\n')
	fann_eval_fixed_output = subprocess.Popen([path_fann_eval_fixed, '--verbose', '-n', net_file_path, '-t', train_file_path], stdout=subprocess.PIPE, stderr=subprocess.PIPE).communicate()[0]
	expected_out_list = []
	for line in fann_eval_fixed_output.splitlines():
		expected_out_list.extend(line.split(b'->')[-1].split())
	
	# Append data_out
	ant_file.write('data_out:\n')
	for _ in range(len(expected_out_list)):
		ant_file.write('  .word 0x00000000\n') 
	
	# Append data_expected
	ant_file.write('data_expected:\n')
	for v in expected_out_list:
		ant_file.write("  .word 0x" + v.decode("utf-8") + '\n')
	
	# Append ANT region
	ant_region = subprocess.Popen([path_generate_ant, '-a', str(asid) + ',' + sixteen_bin_file_path], stdout=subprocess.PIPE, stderr=subprocess.PIPE).communicate()[0]
	for line in ant_region.splitlines():
		if line.decode("utf-8")[0] == '.':
			ant_file.write("  " + line.decode("utf-8") + '\n')
		else:
			ant_file.write(line.decode("utf-8") + '\n')
	ant_file.close()

if __name__ == "__main__":
	parser = argparse.ArgumentParser()
	parser.add_argument('-nd', '--nets-dir')
	parser.add_argument('-nn', '--net-name')
	args = parser.parse_args()
	foo = os.listdir(args.nets_dir)
	def is_16bin(name):
		return True if name.split('.')[-1] == '16bin' else False
	sixteen_bin_filenames = [name.split('.')[0] for name in filter(is_16bin, foo)]
	if args.net_name:
		write_ant_file(args.net_name)
	else:
		for net_name in sixteen_bin_net_names:
			write_ant_file(net_name)	
