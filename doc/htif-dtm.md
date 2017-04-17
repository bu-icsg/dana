HTIF/DTM Notes
====================

## Top-level Design

``` scala
class Top(implicit val p: Parameters) extends Module {
  val io = IO(new Bundle {
    val ps_axi_slave = Flipped(new NastiIO()(AdapterParams(p))) // NASTI === Berkeley AXI4
    val mem_axi      = new NastiIO
  })

  val target = LazyModule(new FPGAZynqTop(p)).module         // Rocket + DANA + PRIME
  val slave  = Module(new ZynqAXISlave(1)(AdapterParams(p))) // AXI slave state machine

  io.mem_axi          <> target.io.mem_axi.head              // Rocket (master) -> AXI -> DDR
  slave.io.nasti.head <> io.ps_axi_slave                     // ARM (master) -> AXI -> Rocket

  slave.io.serial     <> target.io.serial                    // Debug Serial Port
  target.reset        := slave.io.sys_reset                  // Reset logic
}
```

``` verilog
module rocketchip_wrapper
   (DDR_... , FIXED_IO_ddr_..., clk);

system system_i
       (.DDR_... ,
        .FIXED_IO_ddr_...(FIXED_IO_ddr_...),
        // master AXI interface (zynq = master, fpga = slave)
        .M_AXI_... ,
        // slave AXI interface (fpga = master, zynq = slave)
        // connected directly to DDR controller to handle test chip mem
        .S_AXI_... ,
        .ext_clk_in(host_clk)
        );
  // Memory given to Rocket is the upper 256 MB of the 512 MB DRAM
  assign S_AXI_araddr = {4'd1, mem_araddr[27:0]};
  assign S_AXI_awaddr = {4'd1, mem_awaddr[27:0]};

  Top top(
   .clock(host_clk),
   .reset(reset),
   .io_ps_axi_...(M_AXI_...),
   .io_mem_axi_...(S_AXI_...),
  );
endmodule
```

## Whole System Emulation

This description is specific to the emulator. I'll try to make notes of what differs when this goes on the FPGA with a [<sup>fpga</sup>](https://github.com/ucb-bar/fpga-zynq) tag to link to a different file.

The RISC-V is going to boot from address `0x1000`. This is mapped to a BootROM described in [`bootrom.S`](https://github.com/ucb-bar/rocket-chip/blob/master/bootrom/bootrom.S)[<sup>fpga</sup>](https://github.com/ucb-bar/fpga-zynq/blob/master/common/bootrom/bootrom.S) with the proper placement handled by the linker script [`linker.ld`](https://github.com/ucb-bar/rocket-chip/blob/master/bootrom/linked.ld)[<sup>fpga</sup>](https://github.com/ucb-bar/fpga-zynq/blob/master/common/bootrom/linker.ld). This is then waiting for the debug module to interrupt it.

A simplified view of the entire _emulator_ is below:

* [`emulator.cc`](https://github.com/ucb-bar/rocket-chip/blob/master/csrc/emulator.cc)
    * [`fesvr/dtm.cc`](https://github.com/riscv/riscv-fesvr/blob/master/fesvr/dtm.cc), [`fesvr/dtm.h`](https://github.com/riscv/riscv-fesvr/blob/master/fesvr/dtm.h)
        * [`fesvr/htif.cc`](https://github.com/riscv/riscv-fesvr/blob/master/fesvr/htif.cc), [`fesvr/htif.h`](https://github.com/riscv/riscv-fesvr/blob/master/fesvr/htif.h)
    * [`TestHarness.scala`](https://github.com/ucb-bar/rocket-chip/blob/master/src/main/scala/rocketchip/TestHarness.scala)
       * [`ExampleRocketTop`](https://github.com/ucb-bar/rocket-chip/blob/master/src/main/scala/rocketchip/ExampleTop.scala#L31)
           * [`TLROM_bootrom`](https://github.com/ucb-bar/rocket-chip/blob/master/src/main/scala/rocketchip/Periphery.scala#L304)
           * [`DefaultCoreplex`](https://github.com/ucb-bar/rocket-chip/blob/master/src/main/scala/coreplex/Coreplex.scala#L12)
       * [`RocketTile`](https://github.com/ucb-bar/rocket-chip/blob/33ffb623261d13422c141b0402f4e1d1f90a4dca/src/main/scala/rocket/Tile.scala#L26)
       * [`SimAXIMem (Memory)`](https://github.com/ucb-bar/rocket-chip/blob/master/src/main/scala/rocketchip/TestHarness.scala#L40), instance of [`AXI4RAM`](https://github.com/ucb-bar/rocket-chip/blob/dfa61bc48709a7733b73d24eb81acc2da227cfd4/src/main/scala/uncore/axi4/SRAM.scala#L10)
       * [`SimAXIMem (MMIO)`](https://github.com/ucb-bar/rocket-chip/blob/master/src/main/scala/rocketchip/TestHarness.scala#L40), instance of [`AXI4RAM`](https://github.com/ucb-bar/rocket-chip/blob/dfa61bc48709a7733b73d24eb81acc2da227cfd4/src/main/scala/uncore/axi4/SRAM.scala#L10)
       * [`SimDTM.v`](https://github.com/ucb-bar/rocket-chip/blob/master/csrc/SimDTM.cc)

## What is the emulator (technically the `fesvr` library) doing?

It may help to view what a call to the emulator looks like:
```
./emulator-rocketChip-VelvetBonus [emulator args...] [host args...] [target args...]
```

The emulator processes all options that it understands up until it hits an argument it doesn't. The rest of this is passed to the HTIF DTM (`to_dtm`). The HTIF DTM then (through it's parent HTIF constructor) processes options that it understands and stops when it hits a non-option.

Options exist to attach devices, create a signature file (?), and `chroot` (into what?). Default devices are a syscall proxy and `bcd` (?).

At a high level, the emulator resets rocket-chip and then continually toggles it's clock back and forth until the HTIF DTM says that it's done or some other exit condition is reached. I'm unclear on how tightly coupled the DTM and Rocket execution are.

## Emulation Overview

The HTIF/DTM then runs `start_host_thread`. This spawns off two threads: `target` and `host`. Target is the parent thread, host is a created thread that is provided with the function `host_thread_main` from `dtm.cc` by the `init` method. During initialization, a thread is spawned that runs `context_t::wrapper` to create a new context (using the current context). This causes it to launch `dtm_t::producer_thread` which does the following:

```c
  // dtm_t::producer_thread()
  dminfo = -1U;
  xlen = 32;
  set_csr(0x7b0, 8);  // This is the debug CSR (dcsr)
                      // '8' is the `step` register (enter halt mode)
  // query hart
  dminfo = read(0x11); // Not sure what this is reading?
  xlen = get_xlen();   // read the value of xlen (overriding 32 above)

  htif_t::run();       // start -> read_config_string
                       //       -> load_program
                       // poll `tohost`
                       // idle
                       // repeat
                       // stop
  while (true)
    nop();
```

## More Detail (`set_csr`)

Diving into this in a bit more detail, `set_csr` is defined in the following way:

```c
uint64_t dtm_t::read_csr(unsigned which) { return set_csr(which, 0); }

uint64_t dtm_t::modify_csr(unsigned which, uint64_t data, uint32_t type)
{
  int data_word = 4;
  int prog_words = data_word + xlen/32;
  uint32_t prog[] = {
    LOAD(xlen, S0, X0, ram_base() + data_word * 4),
    CSRRx(type, S0, which, S0),
    STORE(xlen, S0, X0, ram_base() + data_word * 4),
    JUMP(rom_ret(), ram_base() + 12),
    (uint32_t)data,
    (uint32_t)(data >> 32)
  };

  uint64_t res = run_program(prog, prog_words, data_word);
  if (xlen == 64)
    res |= read(data_word + 1) << 32;
  return res;
}
```

The act of modifying a CSR involves loading a small program that is going to be force fed to rocket. This uses macros (`LOAD` etc.) to emit RISC-V instructions.

``` c
#define LOAD(xlen, dst, base, imm) \
  (((xlen) == 64 ? 0x00003003 : 0x00002003) \
   | ((dst) << 7) | ((base) << 15) | (uint32_t)ENCODE_ITYPE_IMM(imm))
```

And is it a load?
```
> echo -e "DASM(0x00003003)\nDASM(0x00002003)" | spike-dasm
ld      zero, 0(zero)
lw      zero, 0(zero)
```

Yes!

## Program Loading (`start` in `htif_t::run`)

First the config string is read in.

Then the actual program is loaded using `load_elf`. __This is using the debug interface to load a small program that slowly loads the ELF into the memory of the microprocessor.__

After the program is loaded, the program needs to have found the following symbols:

* `<tohost>`
* `<fromhost>`

The addresses of these symbols are stored in `tohost_addr` and `fromhost_add`.

To concretize this you can examine any of the RISC-V tests or the proxy kernel/bbl:


```
> riscv64-unknown-elf-objdump -D -j .tohost $FOO/riscv-tools/riscv-tests/build/isa/rv64ui-p-add
Disassembly of section .tohost:

0000000080001000 <tohost>:
	...

0000000080001040 <fromhost>:
	...

```

The `...` just indicates regions of zeros.

## Polling the Target (body of `htif_t::run`)

The polling action looks like the following:
```c
  // htif::run()
  // HTIF polling
  while (!signal_exit && exitcode == 0)
  {
    if (auto tohost = mem.read_uint64(tohost_addr)) {
      mem.write_uint64(tohost_addr, 0);
      command_t cmd(this, tohost, fromhost_callback);
      device_list.handle_command(cmd);
    } else {
      idle();
    }

    device_list.tick();

    if (!fromhost_queue.empty() && mem.read_uint64(fromhost_addr) == 0) {
      mem.write_uint64(fromhost_addr, fromhost_queue.front());
      fromhost_queue.pop();
    }
  }
```

While you're not in some bad state, stop the microprocessor, read from `<tohost>`. See if there is anything here (some non-zero value) that needs to be dealt with. __This results in slow operation of the microprocessor as you are repeatedly halting it, seeing if it has anything going on, and then restarting it.__

## Cleaning up

Eventually the program finishes (or you hit some exit condition in `emulator.cc` like a maximum number of cycles). This will then return the exit code if one exists. This will generally only happen if you wind up in a situation trying to service a system call that is not understood or there's some odd exit condition (illegal instruction).
