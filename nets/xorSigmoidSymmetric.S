# See LICENSE.ibm for license details.

#*****************************************************************************
# id.S
#-----------------------------------------------------------------------------
#
# Check that the ID matches what we expect
#

#include "riscv_test.h"
#include "include/test_macros.h"
#include "include/xfiles-dana.h"
#include "rocc-software/src/xcustom.h"
#include "rocc-software/src/riscv_test_rocc.h"
#include "libs/src/xfiles-supervisor.S"
#include "libs/src/xfiles-user.S"

#define CUSTOM_X 0

#define STACK_TOP (_end + 4096)

// RVTEST_CODE_BEGIN includes the EXTRA_INIT macro before its final
// `mret` and the resulting drop to user mode. We use this to setup
// the ASID and ANTP for a single transaction test.
#undef EXTRA_INIT
#define EXTRA_INIT                              \
  SET_ASID(1);                                  \
  SET_ANTP(antp_dana, antp_os); \
  la sp, _end + 1024;

RVTEST_WITH_ROCC

start:

RVTEST_CODE_BEGIN
  TEST_CASE( 2, a0, 0, NEW_WRITE_REQUEST(0, 0, 0) );
  // I am dangerously _not_ saving the TID here, but we know it's '0'
  TEST_CASE( 3, a0, 0, WRITE_DATA(0, data_in, 2) );
  TEST_CASE( 4, a0, 0, READ_DATA_SPINLOCK(0, data_out, 1) );

  TEST_PASSFAIL

RVTEST_CODE_END

  .data
RVTEST_DATA_BEGIN

  TEST_DATA

data_in:
  .word -0x4000
  .word 0x4000
data_out:
  .word 0x0
// ./tools/scripts/generate-ant -a1,build/nets/xorSigmoidSymmetric-fixed.16bin
.align 4
antp_os:
.dword 0x0000000000000002 // size_t size
.dword asid_0 // char * entry_p
.dword 0x0000000000000000 // ant_entry * entry_v
.align 4
antp_dana:
asid_0:
.word 0x00000000 // int num_configs
.word 0x00000000 // int num_valid
.dword 0000000000000000 // char * asid_nnid_p
.dword 0x0000000000000000 // nn_config * asid_nnid_v
.dword 0x0000000000000000 // io * transaction_io
asid_1:
.word 0x00000001 // int num_configs
.word 0x00000001 // int num_valid
.dword asid_1_nnid_0 // char * asid_nnid_p
.dword 0x0000000000000000 // nn_config * asid_nnid_v
.dword 0x0000000000000000 // io * transaction_io
.align 4
asid_1_nnid_0: //
.dword 0x0000000000000022 // size_t size
.dword 0x0000000000000004 // size_t elements_per_block
.dword 0x0000000000000000 // xlen_t * config_raw
.dword asid_1_nnid_0_config // char * config_p
.dword 0x0000000000000000 // char * config_v
.align 6
asid_1_nnid_0_config: // build/nets/xorSigmoidSymmetric-fixed.16bin
.word 0x000a000f
.word 0x00020009
.word 0x00700010
.word 0x00831000
.word 0x00808020
.word 0x02001060
.word 0x00000000
.word 0x00000000
.word 0x65020070
.word 0x0000022d
.word 0x65020080
.word 0xffffed69
.word 0x65020090
.word 0x0000182f
.word 0x650200a0
.word 0xffffec96
.word 0x650200b0
.word 0x0000258b
.word 0x650200c0
.word 0x00000254
.word 0x650200d0
.word 0x00000ea0
.word 0x650200e0
.word 0xffffd8f3
.word 0x650800f0
.word 0x00000390
.word 0x00000000
.word 0x00000000
.word 0xffffedb9
.word 0x00000c53
.word 0x00000000
.word 0x00000000
.word 0xffffff6d
.word 0x00002a5c
.word 0x00000000
.word 0x00000000
.word 0x00001850
.word 0x00000265
.word 0x00000000
.word 0x00000000
.word 0xfffff70f
.word 0x00002315
.word 0x00000000
.word 0x00000000
.word 0xfffff2c8
.word 0x00001b92
.word 0x00000000
.word 0x00000000
.word 0xffffd973
.word 0x00002842
.word 0x00000000
.word 0x00000000
.word 0xffffdae9
.word 0xffffe46c
.word 0x00000000
.word 0x00000000
.word 0x000022f7
.word 0xfffff276
.word 0x00000000
.word 0x00000000
.word 0xffffd4fe
.word 0xfffffc36
.word 0xffffd8da
.word 0xffffe88d
.word 0x00002a2b
.word 0x0000240a
.word 0x00001f71
.word 0xffffeb18
RVTEST_DATA_END
