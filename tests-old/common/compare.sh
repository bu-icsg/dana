#!/bin/bash

COMPARE_BYTES=`ls -la $2 | awk '{print $5}'`
if [ $COMPARE_BYTES -lt 140 ]; then
    echo "[FAIL] File to compare looks too short:"
    cat $2
    exit 1
fi

cmp -n $COMPARE_BYTES -s $1 $2
if [ $? -eq 1 ]; then
    echo "[FAIL] $3 test failed:"
    echo "[INFO] -------------------- Expected:"
    cat $1
    echo "[INFO] -------------------- Saw:"
    cat $2
    echo "[INFO] -------------------- Diff:"
    diff $1 $2
    exit 1
else
    echo "[PASS] $3 ok"
    exit 0
fi

exit 1
