CC=riscv64-unknown-linux-gnu-gcc
LDFLAGS=-static
LIBS=-lpthread
CFLAGS=${LDFLAGS}
DISK=root.bin
MYDIR=/mnt/kd/einstein/dana_benchmark
FPGA=root@fpga1
FESRV=./fesvr-zynq

.PHONY: domount doumount mnt install

danabench: DANA_BENCHMARK.c
	${CC} ${CFLAGS} -static $< -o $@ ${LIBS}

mnt:
	mkdir mnt

domount: 
	sudo mount -o loop ${DISK} mnt

doumount:
	sudo umount mnt

install: danabench domount
	sudo cp danabench mnt/home
	sudo umount mnt

run: install
	ssh -t ${FPGA}  ". /home/root/.profile; ${FESRV} --with-paylod=$(MYDIR)/linux-4.1.17/vmlinux $(MYDIR)/riscv-tests/riscv-pk/build/bbl"
