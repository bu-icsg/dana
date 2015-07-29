DIR_OUT = out

TARGETS = ${DIR_OUT}/rsa-stsp.dat \
	${DIR_OUT}/rsa-stsp-norm.dat

.PHONY: default

default: ${TARGETS}

${DIR_OUT}/rsa-stsp.dat: dana-asc-power-performance
	./dana-asc-power-performance-sweep > $@

${DIR_OUT}/rsa-stsp-norm.dat: dana-asc-power-performance normalize
	./dana-asc-power-performance-sweep | ./normalize > $@

clean:
	rm -f out/*
