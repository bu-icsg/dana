#!/bin/bash -xe

make -k 2>&1 | tee run.log

if [ ${PIPESTATUS[0]} -ne 0 ]; then
    grep "PASS\|FAIL" run.log | sort -n
    echo "[FAIL]" && exit 1
else
    grep "PASS\|FAIL" run.log | sort -n
    echo "[PASS]" && exit 0
fi
