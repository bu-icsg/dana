# Documentation Related to Proxy Kernel Virtual and Physical Memory

This is heavily related to [Issue #17 -- Use UTL to load/store NN configurations](https://github.com/bu-icsg/xfiles-dana/issues/17).

The virtual memory of the proxy-kernel is handled in [vm.c](https://github.com/riscv/riscv-pk/blob/master/pk/vm.c). The following functions are of interest:
* `__handle_page_fault` -- This does the virtual to physical address translation with the `__walk` function
* `__walk` -- This calls `__walk_internal` to do the conversion, but should return the physical address of the actual memory object
