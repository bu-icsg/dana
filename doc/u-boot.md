# U Boot Documentation

## Accessing U-Boot
The FPGAs are setup to boot from the uramdisk.image.gz automatically. To access U-Boot, you need to login over the serial console and manually reboot the ARM core. You will then have the opportunity to interrupt the boot process and access U-Boot. After logging in over the serial console, run the `reboot` command:
```
root@fpga3:~# reboot

Broadcast message from root@fpga3 ng down for reboot NOW!
INIT: Switching to runlevel: 6
INIT: Sending processes the TERM signal
INIT: Stopping Dropbear SSH server: stopped /usr/sbin/dropbear (pid 818)
dropbear.
Stopping tcf-agent: OK
not deconfiguring network interfaces: network file systems still mounted.
Sending all processes the TERM signal...
Sending all processes the KILL signal...
Unmounting remote filesystems...
Deactivating swap...
Unmounting local filesystems...
�ebooting... reboot: Restarting system

U-Boot 2014.07-01982-gf634657-dirty (Sep 24 2014 - 07:54:13)

Board:  Xilinx Zynq
I2C:   ready
DRAM:  ECC disabled 256 MiB
MMC:   zynq_sdhci: 0
SF: Detected S25FL128S_64K with page size 512 Bytes, erase size 128 KiB, total 32 MiB
In:    serial
Out:   serial
Err:   serial
Net:   Gem.e000b000
Hit any key to stop autoboot:  0
zynq-uboot>
```

## Manually Loading the SD Card
It is possible that the uramdisk.image.gz will get FUBARed during a transfer to a remote FPGA and all you can get to is U-Boot. If this is the case, you can reload whatever you need via U-Boot. I've only done this with minicom, however.

Connect to the FPGA (using /dev/ttyACM0 as an example):
```
minicom -D /dev/ttyACM0
```

Get U-Boot into a mode to receive files via the y-modem protocol
```
zynq-uboot> loady
```

Use an escape sequence to do a serial transfer through minicom, `C-a s`, selecting y-modem as the protocol. You can then navigate to the local file that you want to transfer to the RAM of the board. The file transfer will go through at the baud rate of the connection, so this can take some time. Take note of what this shows when it finishes as this indicates the start address of the loaded file and the size of the transfer:
```
## Ready for binary (ymodem) download to 0x00000000 at 115200 bps...
CxyzModem - CRC mode, 48072(SOH)/0(STX)/0(CAN) packets, 6 retries
## Total Size      = 0x005de2ce = 6152910 Bytes
```

Above, the download went to start address 0x0 and was of size 6152910. This file happened to be uramdisk.image.gz, which will be referenced below. Now we need to copy this from RAM to the SD card. The SD card is an mmc device which you can get some information about with `mmc info` or `mmc list` to find out what the device number is.
```
zynq-uboot> mmc info
Device: zynq_sdhci
Manufacturer ID: 27
OEM: 5048
Name: SD04G
Tran Speed: 50000000
Rd Block Len: 512
SD version 3.0
High Capacity: Yes
Capacity: 3.7 GiB
Bus Width: 4-bit
zynq-uboot> mmc list
zynq_sdhci: 0
```

Here, the device is 0. We can also look at the contents of the SD card with:
```
zynq-uboot> fatls mmc 0
            riscv/
 102050064   uramdisk.image.gz
  3396232   uimage
     9243   devicetree.dtb
  4517524   boot.bin
            .trash-1000/
   862920   simple_train_arm
       36   xor.data
     1969   xor_float.net
  4015544   tst-ieee754-riscv
   557056   zed.out
  4322320   simple_train_riscv
        0   boot.bif
  3701208   cmath_test_riscv
   557055   #zed.out#
  4517524   boot.bin.orig

15 file(s), 2 dir(s)
```

Let's go ahead and overwrite the bad uramdisk.image.gz:
```
zynq-uboot> fatwrite mmc 0 0x0 uramdisk.image.gz 6152910
writing uramdisk.image.gz
102050064 bytes written
```

Following that, everything should work, i.e., you can boot the board:
```
zynq-uboot> boot
```

Using this method you can recover the system from anything so long as U-Boot is intact.
