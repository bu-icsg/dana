#!/usr/bin/bash -xe

PARALLEL_JOBS=`common/max-processors.sh`

make -j$PARALLEL_JOBS 2>&1 | tee run.log

if [[ ${PIPESTATUS[0]} -ne 0 ]]; then
    echo "[FAIL]" && exit 1
else
    echo "[PASS]" && exit 0
fi
