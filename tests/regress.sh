#!/bin/bash -xe

PARALLEL_JOBS=`../usr/bin/max-processors.sh`

make -j$PARALLEL_JOBS -k 2>&1 | tee run.log

if [[ ${PIPESTATUS[0]} -ne 0 ]]; then
    grep "PASS\|FAIL" run.log | sort -n
    echo "[FAIL]" && exit 1
else
    grep "PASS\|FAIL" run.log | sort -n
    echo "[PASS]" && exit 0
fi
