#!/usr/bin/bash

# Figure out how many processors are in the system
NUM_CPUS=`cat /proc/cpuinfo | \
    grep processor | \
    tail -n1 | \
    awk '{print $3}' | \
    xargs -IX echo "X 1+p" | \
    dc`

# Find the worst load that we've seen over the past 3 reported load
# average intevals
WORST_LOAD=`cat /proc/loadavg | \
    awk '{print $1" "$2" "$3}' | \
    sort -nr | \
    awk '{print $1}'`

# Grab the number of idle CPUs that we see, and, if the machine is
# heavily loaded, just grab one
COMMANDEERED_CPUS=`echo "$NUM_CPUS $WORST_LOAD-p" | dc | sed 's/\..\+$//'`
if [[ $COMMANDEERED_CPUS = 0 ]]; then
   COMMANDEERED_CPUS=1;
fi
echo $COMMANDEERED_CPUS
