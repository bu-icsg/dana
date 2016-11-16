// See LICENSE for license details.

#ifndef SRC_MAIN_C_XCUSTOM_H_
#define SRC_MAIN_C_XCUSTOM_H_

#define STR1(x) #x
#ifndef STR
#define STR(x) STR1(x)
#endif
#define EXTRACT(a, size, offset) (((~(~0 << size) << offset) & a) >> offset)

#define XCUSTOM_OPCODE(x) CUSTOM_##x
#define CUSTOM_0 0b0001011
#define CUSTOM_1 0b0101011
#define CUSTOM_2 0b1011011
#define CUSTOM_3 0b1111011

#define XCUSTOM(X, rd, rs1, rs2, funct) \
  XCUSTOM_OPCODE(X)                   | \
  (rd                   << (7))       | \
  (0x7                  << (7+5))     | \
  (rs1                  << (7+5+3))   | \
  (rs2                  << (7+5+3+5)) | \
  (EXTRACT(funct, 7, 0) << (7+5+3+5+5))

#define XCUSTOM_R_R_R(X, rd, rs1, rs2, funct)             \
  asm ("mv a4, %[_rs1]\n\t"                               \
       "mv a5, %[_rs2]\n\t"                               \
       ".word " STR(XCUSTOM(X, 15, 14, 15, funct)) "\n\t" \
       "mv %[_rd], a5"                                    \
       : [_rd] "=r" (rd)                                  \
       : [_rs1] "r" (rs1), [_rs2] "r" (rs2)               \
       : "a4", "a5")

#endif  // SRC_MAIN_C_XCUSTOM_H_
