#!/usr/bin/bash

if [[ `cmp $1 $2` ]]; then
    echo "[FAIL] $3 test failed"
    exit 1
else
    echo "[PASS] $3 ok"
    exit 0
fi

exit 1
