#!/usr/bin/env python3

import click
import sys
import re
import subprocess
import os
import ipdb

re_in_val = re.compile(r'queueIn\[0\]\sdeq\s\[data:(0x[\da-f]+)')
re_out_val = re.compile(r'queueOut\[0\]\sdeq\s\[data:(0x[\da-f]+)')
path_fann_eval_fixed = 'xfiles-dana/tools/bin/fann-eval-fixed'
#path_emulator_bin = 'emulator/emulator-rocketchip-XFilesDanaCppPe1Epb4Config'
path_emulator_bin = 'emulator/emulator-rocketchip-XFilesDanaCppPe4Epb4Config'

def generate_baremetal_trace(emulator_bin_file_path, baremetal_bin_file_path):
    assert emulator_bin_file_path, "Must provide path to emulator bin or to baremetal trace file"
    assert baremetal_bin_file_path, "Must provide path to baremetal benchamrk bin or to baremetal trace file"
    baremetal_bin_base_name = os.path.basename(baremetal_bin_file_path).split('.')[0]
    baremetal_trace_file_name = baremetal_bin_base_name + ".baremetal.trace"
    args = [emulator_bin_file_path, '+verbose', baremetal_bin_file_path]
    with open(baremetal_trace_file_name, 'wb') as baremetal_trace_file:
        print(' '.join(args))
        subprocess.run(args, stderr=baremetal_trace_file)
    return baremetal_trace_file_name


def parse_baremetal_trace(trace_file_path, write_file=False):
    trace_base_name = os.path.basename(trace_file_path).split('.')[0]
    trace_dir_name = os.path.dirname(trace_file_path)
    baremetal_outputs_file_name = trace_base_name + ".baremetal.outs"
    baremetal_outputs_file_path = baremetal_outputs_file_name
    # baremetal_outputs_file_path = os.path.join(trace_dir_name, baremetal_outputs_file_name)
    baremetal_outputs = []
    with open(trace_file_path, 'r') as trace_file:
        for line in trace_file.readlines():
            groups = re_out_val.search(line)
            if groups:
                baremetal_outputs.append(groups.group(1))
    if write_file:
        with open(baremetal_outputs_file_path, 'w') as baremetal_outputs_file:
            for item in baremetal_outputs:
                baremetal_outputs_file.write(item + '\n')
    return baremetal_outputs


def fann_eval_fixed(net_file_path, train_file_path):
    assert net_file_path, "Must provide path to net file or to FANN trace file"
    assert train_file_path, "Must provide path to train file or to FANN trace file"
    net_base_name = os.path.basename(net_file_path).split('.')[0]
    net_dir_name = os.path.dirname(net_file_path)
    trace_file_name = net_base_name + ".fann_eval_fixed.trace"
    trace_file_path = trace_file_name
    # trace_file_path = os.path.join(net_dir_name, trace_file_name)
    args = [path_fann_eval_fixed, '--verbose', '-n', net_file_path, '-t', train_file_path]
    with open(trace_file_path, 'wb') as trace_file:
        print(' '.join(args))
        subprocess.run(args, stdout=trace_file)
    assert os.path.isfile(trace_file_path), "FANN trace file doesn't exist"
    assert os.path.getsize(trace_file_path) > 0, "FANN trace file is empty"
    return trace_file_path
    

def parse_fann_eval_fixed_trace(trace_file_path, write_file=False):
    fann_fixed_outputs = []
    with open(trace_file_path, 'r') as trace_file:
        for line in trace_file.readlines():
            fann_fixed_outputs.extend(["0x00000000{}".format(b) for b in line.split(" -> ")[1].split(" ")[:-1]])
    if write_file:
        trace_base_name = os.path.basename(trace_file_path).split('.')[0]
        trace_dir_name = os.path.dirname(trace_file_path)
        fann_fixed_outputs_file_name = trace_base_name + ".fann_eval_fixed.outs"
        fann_fixed_outputs_file_path = fann_fixed_outputs_file_name
        # fann_fixed_outputs_file_path = os.path.join(trace_dir_name, fann_fixed_outputs_file_name)
        with open(fann_fixed_outputs_file_path, 'w') as fann_fixed_outputs_file:
            for item in fann_fixed_outputs:
                fann_fixed_outputs_file.write(item + '\n')
    return fann_fixed_outputs


@click.command()
@click.option('--net_file_path', '-n', type=click.Path(exists=True), required=False)
@click.option('--train_file_path', '-t', type=click.Path(exists=True), required=False)
@click.option('--fann_trace_file_path', '-ft', type=click.Path(exists=True), required=False)
@click.option('--baremetal_bin_file_path', '-bb', type=click.Path(exists=True), required=False)
@click.option('--baremetal_trace_file_path', '-bt', type=click.Path(exists=True), required=False)
@click.option('--test_list_file_path', '-tel', type=click.Path(exists=True), required=False)
@click.option('--trace_list_file_path', '-trl', type=click.Path(exists=True), required=False)
@click.option('--debug', is_flag=True, required=False)
def cli(net_file_path, train_file_path, fann_trace_file_path, baremetal_bin_file_path, baremetal_trace_file_path, test_list_file_path, trace_list_file_path, debug):
    if test_list_file_path:
        with open(test_list_file_path, 'r') as test_list_file:
            test_list = test_list_file.read()
        for test_name in test_list.splitlines():
            if test_name[0] == '#': continue
            print(test_name)
            net_file_name = "{}-fixed.net".format(test_name)
            train_file_name = "{}-fixed.train".format(test_name)
            baremetal_bin_file_name = "xfiles-dana-nets-p-{}".format(test_name)
            baremetal_bin_file_path = os.path.join('xfiles-dana/tests/build/nets', baremetal_bin_file_name)
            net_file_path = os.path.join('xfiles-dana/build/nets', net_file_name)
            train_file_path = os.path.join('xfiles-dana/build/nets', train_file_name)

            print("Generating baremetal trace")
            baremetal_trace_file_path = generate_baremetal_trace(path_emulator_bin, baremetal_bin_file_path)
            baremetal_outputs = parse_baremetal_trace(baremetal_trace_file_path, write_file=debug)

            print("Generating FANN trace")
            fann_trace_file_path = fann_eval_fixed(net_file_path, train_file_path)
            fann_fixed_outputs = parse_fann_eval_fixed_trace(fann_trace_file_path, write_file=debug)

            errors = [int(l, 16) - int(r, 16) for l, r in zip(baremetal_outputs, fann_fixed_outputs)]
            print("Max error: {}".format(max([(i, abs(i)) for i in errors], key=lambda x: x[1])[0]))
    
    if trace_list_file_path:
        with open(trace_list_file_path, 'r') as trace_list_file:
            trace_list = trace_list_file.read()
        for trace_file_path in trace_list.splitlines():
            if trace_file_path[0] == '#': continue
            print(trace_file_path)
            net_base_name = os.path.basename(trace_file_path).split('.')[0].replace("xfiles-dana-nets-p-","")
            net_file_path = os.path.join('xfiles-dana/build/nets', "{}-fixed.net".format(net_base_name))
            train_file_path = os.path.join('xfiles-dana/build/nets', "{}-fixed.train".format(net_base_name))
            print(net_file_path)
            print(train_file_path)
            print("Parsing baremetal trace")
            baremetal_trace_file_path = trace_file_path
            baremetal_outputs = parse_baremetal_trace(baremetal_trace_file_path, write_file=debug)
            if not baremetal_outputs: 
                print("No baremetal outputs!")
                continue

            print("Generating FANN trace")
            fann_trace_file_path = fann_eval_fixed(net_file_path, train_file_path)
            fann_fixed_outputs = parse_fann_eval_fixed_trace(fann_trace_file_path, write_file=debug)
            if not fann_fixed_outputs:
                print("No FANN outputs")
                continue

            errors = [int(l, 16) - int(r, 16) for l, r in zip(baremetal_outputs, fann_fixed_outputs)]
            print("Max error: {}".format(max([(i, abs(i)) for i in errors], key=lambda x: x[1])[0]))
            
    else:
        print("Generating baremetal trace")
        baremetal_trace_file_path = baremetal_trace_file_path or generate_baremetal_trace(path_emulator_bin, baremetal_bin_file_path)
        print("Parsing baremetal trace")
        baremetal_outputs = parse_baremetal_trace(baremetal_trace_file_path, write_file=debug)

        print("Generating FANN trace")
        fann_trace_file_path = fann_trace_file_path or fann_eval_fixed(net_file_path, train_file_path)
        print("Parsing FANN trace")
        fann_fixed_outputs = parse_fann_eval_fixed_trace(fann_trace_file_path, write_file=debug)

        errors = [int(l, 16) - int(r, 16) for l, r in zip(baremetal_outputs, fann_fixed_outputs)]
        print("Max error: {}".format(max([(i, abs(i)) for i in errors], key=lambda x: x[1])[0]))

    sys.exit(0)

    if baremetal_trace_file_path:
        baremetal_outputs_file_path = parse_baremetal_trace(baremetal_trace_file_path)
    else:
        # Generate baremetal trace
        pass
    if fann_trace_file_path:
        fann_fixed_outputs_file_path = parse_fann_eval_fixed_trace(fann_trace_file_path)
    else:
        # Generate fann trace
        fann_trace_file_path = fann_eval_fixed(net_file_path, train_file_path)
        assert(os.path.isfile(fann_trace_file_path))

    assert(os.path.isfile(baremetal_outputs_file_path))
    sys.exit(0)
    assert(os.path.isfile(baremetal_outputs_file_path))
    fann_fixed_outputs_file_path = parse_fann_eval_fixed_trace(fann_trace_file_path)
    assert(os.path.isfile(fann_fixed_outputs_file_path))

    #elif net_file_dir_path:
        # process the whole directory
    train_file = open(train_file_path, 'rb')
    num_datapoints, num_inputs, num_outputs = train_file.readline().split()
    num_outputs = int(num_outputs, 10)
    train_file.close()

    if not emu_log:
        emu_log = sys.stdin
    print("expected out, emulated out, delta")
    for i in range(0, len(expected_out_list), num_outputs):
        for j in expected_out_list[i:i + num_outputs]:
            for line in emu_log:
                res = re_out_val.search(line)
                if res:
                    out_val = int(res.groups()[0], 0)
                    break
            j = int(j.decode("utf-8"), 16)
            print("{}, {}, {}".format(j, out_val, abs(j - out_val)))
    
if __name__ == '__main__':
    cli()
