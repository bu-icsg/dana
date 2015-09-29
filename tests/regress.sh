#!/usr/bin/bash

PARALLEL_JOBS=`common/max-processors.sh`

(make -j$PARALLEL_JOBS 2>&1 && \
    echo "[PASS]" && exit 0 || \
        echo "[FAIL]" && exit 1) | tee run.log
