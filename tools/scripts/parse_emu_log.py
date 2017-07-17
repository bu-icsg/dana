#!/usr/bin/env python3

import click
import sys
import re
import subprocess
import os
import mmap
import ipdb
import struct
from collections import defaultdict

re_in_out_mapping = re.compile(r'RegFile: (?:Saw TTable write|PE write element).+(?P<tidx>0x[0-9a-fA-F]+)\/(?P<addr>0x[0-9a-fA-F]+)\/(?P<data>0x[0-9a-fA-F]+)')
re_sram_blo_inc = re.compile(r'SramBloInc[\D\s]+0x[01]\/0x1.+\n[\D\s]+(?P<addr>0x[a-f0-9]+)\/(?P<dataOld>0x[a-f0-9]+)\/(?P<dataNew>0x[a-f0-9]+)')
re_in_val = re.compile(r'queueIn\[0\]\sdeq\s\[data:(0x[\da-f]+)')
re_out_val = re.compile(r'queueOut\[0\]\sdeq\s\[data:(0x[\da-f]+)')
re_in_out_val = re.compile(r"(?P<inputs>(?:[a-f0-9]+\s)+)->(?P<outputs>(?:\s[a-f0-9]+)+)")
re_store_data = re.compile(r"Received store data 0x([\da-f]+)")
path_fann_eval_fixed = 'tools/bin/fann-eval-fixed'
#path_emulator_bin = 'emulator/emulator-rocketchip-XFilesDanaCppPe1Epb4Config'
path_emulator_bin = 'emulator/emulator-rocketchip-XFilesDanaCppPe4Epb4Config'

def twos_comp(val, bits):
    """compute the 2's compliment of int value val"""
    if (val & (1 << (bits - 1))) != 0: # if sign bit is set e.g., 8bit: 128-255
        val = val - (1 << bits)        # compute negative value
    return val                         # return positive value as is

def twos_complement(val, width):
    format_string = "0x{0:0" + str(width >> 2) + "x}"
    if (val & (1 << (width - 1))) != 0:
        return format_string.format(int(((-1 * val) ^ 0xffffffff) + 1))
    else:
        return format_string.format(val)

def parse_regex_from_log(log_file_path, regex):
    mfd = os.open(log_file_path, os.O_RDONLY)
    mfile = mmap.mmap(mfd, 0, prot=mmap.PROT_READ)
    bar = []
    for m in regex.finditer(mfile.read().decode('utf-8')):
        # ipdb.set_trace()
        bar.append(m.groupdict())
    # bar = [m.groupdict() for m in regex.finditer(mfile.read().decode('utf-8'))]
    return bar

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
    with open(trace_file_path, 'r') as trace_file:
        foo = [m.groupdict() for m in re_in_out_val.finditer(trace_file.read())]
        bar = []
        for d in foo:
            baz = {}
            for k, v in d.items():
                baz[k] = v.split()
            bar.append(baz)
    return bar
        # fann_fixed_outputs = [{k.split(): v.split()} for m in re_in_out_val.finditer(trace_file.read()) for k, v in m.groupdict().iteritems()] 
        # fann_fixed_outputs.extend(["0x00000000{}".format(b) for b in line.split(" -> ")[1].split(" ")[:-1]])
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



@click.group()
@click.option('--net_file_path', '-n', type=click.Path(exists=True), required=False)
@click.option('--train_file_path', '-t', type=click.Path(exists=True), required=False)
@click.option('--fann_trace_file_path', '-ft', type=click.Path(exists=True), required=False)
@click.option('--baremetal_bin_file_path', '-bb', type=click.Path(exists=True), required=False)
@click.option('--baremetal_trace_file_path', '-bt', type=click.Path(exists=True), required=False)
@click.option('--test_list_file_path', '-tel', type=click.Path(exists=True), required=False)
@click.option('--trace_list_file_path', '-trl', type=click.Path(exists=True), required=False)
@click.option('--debug', is_flag=True, required=False)
@click.option('--learn', is_flag=True, required=False)
def cli(net_file_path, train_file_path, fann_trace_file_path, baremetal_bin_file_path, baremetal_trace_file_path, test_list_file_path, trace_list_file_path, debug, learn):
    pass

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
            
    if learn:
        assert net_file_path, "Must provide path to net file"
        assert train_file_path, "Must provide path to train file"

        print("Generating FANN trace")
        fann_trace_file_path = fann_eval_fixed(net_file_path, train_file_path)
        fann_fixed_outputs = parse_fann_eval_fixed_trace(fann_trace_file_path, write_file=debug)

        print("Parsing learn trace")
        # Figure out the address of the output neuron(s)
        args = ["./tools/scripts/dana_memory_tool.py -b 4 -l -n {} | perl -ne 'print $1.\"\n\" if /^Out,(.+?),/'".format(net_file_path)]
        ps = subprocess.Popen(args, shell=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        output = ps.communicate()[0]
        output_addr = int(output.decode('utf-8'), 16)
        foo = [m.groupdict() for m in re_in_out_mapping.finditer(output.decode('utf-8'))]
        print("Output addr: {}".format(output_addr))

        # for item in parse_regex_from_log(baremetal_trace_file_path, re_sram_blo_inc):
            # print(item)
        ins_and_outs = parse_regex_from_log(baremetal_trace_file_path, re_in_out_mapping)
        sram_blo_results = parse_regex_from_log(baremetal_trace_file_path, re_sram_blo_inc)
        for item in sram_blo_results:
            print(item)
        # Algorithm 1
        sram_mem = ["0" * 32] * 1024
        for entry in sram_blo_results:
            addr = int(entry['addr'], 16)
            sram_mem[addr] = entry['dataNew'][2:]
        sram_mem = [s[i:i+8] for i in range(0, 32, 8) for s in sram_mem]
        for num, s in enumerate(sram_mem):
            print("{}: {}, {}".format(num, s, twos_comp(int(s, 16), 32)))
        sys.exit(0)

        for item in ins_and_outs:
            if int(item['addr'], 16) == output_addr:
                print(item['data'])
        ipdb.set_trace()
    else:
        parse_learn_trace_new(baremetal_trace_file_path)
        sys.exit(0)
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
    
@cli.command()
@click.option('--baremetal_trace_file_path', '-bt', type=click.Path(exists=True), required=False)
def parse_learn_trace_new(baremetal_trace_file_path):
    with open(baremetal_trace_file_path) as learn_trace_file:
        with open('a.out', 'wb') as out_file:
            for line in learn_trace_file:
                res = re_store_data.search(line)
                if res:
                    foo = res.groups()[0]
                    print(foo)
                    bar = []
                    for i in range(0, len(foo), 4):
                        baz = foo[i+2:i+4] + foo[i:i+2]
                        bar.append(baz)
                    bar.reverse()
                    baz = ''.join(bar)
                    print(baz)
                    print()
                    out_file.write(bytes.fromhex(baz))

if __name__ == '__main__':
    cli()
