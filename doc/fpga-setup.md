# FPGA Setup and Management
The general strategy here is to setup a bunch of FPGAs (e.g., Zedboards) behind a NAT connected to a HOST. Users with access to the HOST are then able to connect using the `xfiles-dana/scripts/rv*` scripts to grab an FPGA and do work on it. The HOST is running an NFS file server that makes the home directories of the HOST available to specific clients.

## HOST Configuration
On the HOST side, several files need to be properly maintained. This is Ubuntu specific, but should be roughly general in terms of effect but for different files:
* `/etc/hosts` -- setup the HOST to know about the IPs of the FPGAs
* `/etc/hosts.allow` -- enable access to the HOST system from the FPGAs
* `/etc/dhcp/dhcpd.conf` -- create entries in the subnet for each of the FPGAs that will give assign a specific IP address to a specific MAC address. Each FPGA must have a unique MAC
* `/etc/exports` -- enable access to specific directories via NFS, e.g., `/home`

## FPGA Configuration
On the FPGA side, certain files need to be configured inside of the ramdisk:
* `/etc/hosts` -- setup the hostname mappings for the FPGAs and the HOST
* `/etc/network/interfaces` -- give the FPGA a static IP and set the network/gateway
* `/etc/hostname` -- give the FPGA a hostname

Presently, all of these files need to be changed manually whenever you are pushing changes to the ramdisk.

### Set the FPGA MAC address
Each of the FPGAs comes with a built in MAC address of `00:0A:35:00:01:22`. If you run more than one of these on the same network then network traffic will get lost. Consequently, the FPGAs need to be given unique MAC addresses. This can be accomplished from configuring this in Vivado (I think), but it's easier (and persistent!) to just handle this from u-boot. The procedure here is as follows:
1. Login to u-boot using the serial console (i.e., use `rvcon`)
2. Set the `ethaddr` environment variable and save it:
```
setenv ethaddr 00:0A:35:00:01:[XX]
saveenv
```
