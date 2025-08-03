/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "gtest/gtest.h"

#include <cmath>
#include <cstdint>
#include <initializer_list>
#include <limits>

#include "utility.h"

namespace {

TEST(Arm64InsnTest, UnsignedBitfieldMoveNoShift) {
  uint64_t arg = 0x3952247371907021ULL;
  uint64_t res;

  asm("ubfm %0, %1, #0, #63" : "=r"(res) : "r"(arg));

  ASSERT_EQ(res, 0x3952247371907021ULL);
}

TEST(Arm64InsnTest, BitfieldLeftInsertion) {
  uint64_t arg = 0x389522868478abcdULL;
  uint64_t res = 0x1101044682325271ULL;

  asm("bfm %0, %1, #40, #15" : "=r"(res) : "r"(arg), "0"(res));

  ASSERT_EQ(res, 0x110104abcd325271ULL);
}

TEST(Arm64InsnTest, BitfieldRightInsertion) {
  uint64_t arg = 0x3276561809377344ULL;
  uint64_t res = 0x1668039626579787ULL;

  asm("bfm %0, %1, #4, #39" : "=r"(res) : "r"(arg), "0"(res));

  ASSERT_EQ(res, 0x1668039180937734ULL);
}

TEST(Arm64InsnTest, MoveImmToFp32) {
  // The tests below verify that fmov works with various immediates.
  // Specifically, the instruction has an 8-bit immediate field consisting of
  // the following four subfields:
  //
  // - sign (one bit)
  // - upper exponent (one bit)
  // - lower exponent (two bits)
  // - mantisa (four bits)
  //
  // For example, we decompose imm8 = 0b01001111 into:
  //
  // - sign = 0 (positive)
  // - upper exponent = 1
  // - lower exponent = 00
  // - mantisa = 1111
  //
  // This immediate corresponds to 32-bit floating point value:
  //
  // 0 011111 00 1111 0000000000000000000
  // | |      |  |    |
  // | |      |  |    +- 19 zeros
  // | |      |  +------ mantisa
  // | |      +--------- lower exponent
  // | +---------------- upper exponent (custom extended to 6 bits)
  // +------------------ sign
  //
  // Thus we have:
  //
  //   1.11110000... * 2^(124-127) = 0.2421875
  //
  // where 1.11110000... is in binary.
  //
  // See VFPExpandImm in the ARM Architecture Manual for details.
  //
  // We enumerate all possible 8-bit immediate encodings of the form:
  //
  //   {0,1}{0,1}{00,11}{0000,1111}
  //
  // to verify that the decoder correctly splits the immediate into the
  // subfields and reconstructs the intended floating-point value.

  // imm8 = 0b00000000
  __uint128_t res1 = ASM_INSN_WRAP_FUNC_W_RES("fmov s0, #2.0e+00")();
  ASSERT_EQ(res1, MakeUInt128(0x40000000U, 0U));

  // imm8 = 0b00001111
  __uint128_t res2 = ASM_INSN_WRAP_FUNC_W_RES("fmov s0, #3.8750e+00")();
  ASSERT_EQ(res2, MakeUInt128(0x40780000U, 0U));

  // imm8 = 0b00110000
  __uint128_t res3 = ASM_INSN_WRAP_FUNC_W_RES("fmov s0, #1.60e+01")();
  ASSERT_EQ(res3, MakeUInt128(0x41800000U, 0U));

  // imm8 = 0b00111111
  __uint128_t res4 = ASM_INSN_WRAP_FUNC_W_RES("fmov s0, #3.10e+01")();
  ASSERT_EQ(res4, MakeUInt128(0x41f80000U, 0U));

  // imm8 = 0b01000000
  __uint128_t res5 = ASM_INSN_WRAP_FUNC_W_RES("fmov s0, #1.250e-01")();
  ASSERT_EQ(res5, MakeUInt128(0x3e000000U, 0U));

  // imm8 = 0b01001111
  __uint128_t res6 = ASM_INSN_WRAP_FUNC_W_RES("fmov s0, #2.4218750e-01")();
  ASSERT_EQ(res6, MakeUInt128(0x3e780000U, 0U));

  // imm8 = 0b01110000
  __uint128_t res7 = ASM_INSN_WRAP_FUNC_W_RES("fmov s0, #1.0e+00")();
  ASSERT_EQ(res7, MakeUInt128(0x3f800000U, 0U));

  // imm8 = 0b01111111
  __uint128_t res8 = ASM_INSN_WRAP_FUNC_W_RES("fmov s0, #1.93750e+00")();
  ASSERT_EQ(res8, MakeUInt128(0x3ff80000U, 0U));

  // imm8 = 0b10000000
  __uint128_t res9 = ASM_INSN_WRAP_FUNC_W_RES("fmov s0, #-2.0e+00")();
  ASSERT_EQ(res9, MakeUInt128(0xc0000000U, 0U));

  // imm8 = 0b10001111
  __uint128_t res10 = ASM_INSN_WRAP_FUNC_W_RES("fmov s0, #-3.8750e+00")();
  ASSERT_EQ(res10, MakeUInt128(0xc0780000U, 0U));

  // imm8 = 0b10110000
  __uint128_t res11 = ASM_INSN_WRAP_FUNC_W_RES("fmov s0, #-1.60e+01")();
  ASSERT_EQ(res11, MakeUInt128(0xc1800000U, 0U));

  // imm8 = 0b10111111
  __uint128_t res12 = ASM_INSN_WRAP_FUNC_W_RES("fmov s0, #-3.10e+01")();
  ASSERT_EQ(res12, MakeUInt128(0xc1f80000U, 0U));

  // imm8 = 0b11000000
  __uint128_t res13 = ASM_INSN_WRAP_FUNC_W_RES("fmov s0, #-1.250e-01")();
  ASSERT_EQ(res13, MakeUInt128(0xbe000000U, 0U));

  // imm8 = 0b11001111
  __uint128_t res14 = ASM_INSN_WRAP_FUNC_W_RES("fmov s0, #-2.4218750e-01")();
  ASSERT_EQ(res14, MakeUInt128(0xbe780000U, 0U));

  // imm8 = 0b11110000
  __uint128_t res15 = ASM_INSN_WRAP_FUNC_W_RES("fmov s0, #-1.0e+00")();
  ASSERT_EQ(res15, MakeUInt128(0xbf800000U, 0U));

  // imm8 = 0b11111111
  __uint128_t res16 = ASM_INSN_WRAP_FUNC_W_RES("fmov s0, #-1.93750e+00")();
  ASSERT_EQ(res16, MakeUInt128(0xbff80000U, 0U));
}

TEST(Arm64InsnTest, MoveImmToFp64) {
  // The tests below verify that fmov works with various immediates.
  // Specifically, the instruction has an 8-bit immediate field consisting of
  // the following four subfields:
  //
  // - sign (one bit)
  // - upper exponent (one bit)
  // - lower exponent (two bits)
  // - mantisa (four bits)
  //
  // For example, we decompose imm8 = 0b01001111 into:
  //
  // - sign = 0 (positive)
  // - upper exponent = 1
  // - lower exponent = 00
  // - mantisa = 1111
  //
  // This immediate corresponds to 64-bit floating point value:
  //
  // 0 011111111 00 1111 000000000000000000000000000000000000000000000000
  // | |         |  |    |
  // | |         |  |    +- 48 zeros
  // | |         |  +------ mantisa
  // | |         +--------- lower exponent
  // | +------------------- upper exponent (custom extended to 9 bits)
  // +--------------------- sign
  //
  // Thus we have:
  //
  //   1.11110000... * 2^(1020-1023) = 0.2421875
  //
  // where 1.11110000... is in binary.
  //
  // See VFPExpandImm in the ARM Architecture Manual for details.
  //
  // We enumerate all possible 8-bit immediate encodings of the form:
  //
  //   {0,1}{0,1}{00,11}{0000,1111}
  //
  // to verify that the decoder correctly splits the immediate into the
  // subfields and reconstructs the intended floating-point value.

  // imm8 = 0b00000000
  __uint128_t res1 = ASM_INSN_WRAP_FUNC_W_RES("fmov %d0, #2.0e+00")();
  ASSERT_EQ(res1, MakeUInt128(0x4000000000000000ULL, 0U));

  // imm8 = 0b00001111
  __uint128_t res2 = ASM_INSN_WRAP_FUNC_W_RES("fmov %d0, #3.8750e+00")();
  ASSERT_EQ(res2, MakeUInt128(0x400f000000000000ULL, 0U));

  // imm8 = 0b00110000
  __uint128_t res3 = ASM_INSN_WRAP_FUNC_W_RES("fmov %d0, #1.60e+01")();
  ASSERT_EQ(res3, MakeUInt128(0x4030000000000000ULL, 0U));

  // imm8 = 0b00111111
  __uint128_t res4 = ASM_INSN_WRAP_FUNC_W_RES("fmov %d0, #3.10e+01")();
  ASSERT_EQ(res4, MakeUInt128(0x403f000000000000ULL, 0U));

  // imm8 = 0b01000000
  __uint128_t res5 = ASM_INSN_WRAP_FUNC_W_RES("fmov %d0, #1.250e-01")();
  ASSERT_EQ(res5, MakeUInt128(0x3fc0000000000000ULL, 0U));

  // imm8 = 0b01001111
  __uint128_t res6 = ASM_INSN_WRAP_FUNC_W_RES("fmov %d0, #2.4218750e-01")();
  ASSERT_EQ(res6, MakeUInt128(0x3fcf000000000000ULL, 0U));

  // imm8 = 0b01110000
  __uint128_t res7 = ASM_INSN_WRAP_FUNC_W_RES("fmov %d0, #1.0e+00")();
  ASSERT_EQ(res7, MakeUInt128(0x3ff0000000000000ULL, 0U));

  // imm8 = 0b01111111
  __uint128_t res8 = ASM_INSN_WRAP_FUNC_W_RES("fmov %d0, #1.93750e+00")();
  ASSERT_EQ(res8, MakeUInt128(0x3fff000000000000ULL, 0U));

  // imm8 = 0b10000000
  __uint128_t res9 = ASM_INSN_WRAP_FUNC_W_RES("fmov %d0, #-2.0e+00")();
  ASSERT_EQ(res9, MakeUInt128(0xc000000000000000ULL, 0U));

  // imm8 = 0b10001111
  __uint128_t res10 = ASM_INSN_WRAP_FUNC_W_RES("fmov %d0, #-3.8750e+00")();
  ASSERT_EQ(res10, MakeUInt128(0xc00f000000000000ULL, 0U));

  // imm8 = 0b10110000
  __uint128_t res11 = ASM_INSN_WRAP_FUNC_W_RES("fmov %d0, #-1.60e+01")();
  ASSERT_EQ(res11, MakeUInt128(0xc030000000000000ULL, 0U));

  // imm8 = 0b10111111
  __uint128_t res12 = ASM_INSN_WRAP_FUNC_W_RES("fmov %d0, #-3.10e+01")();
  ASSERT_EQ(res12, MakeUInt128(0xc03f000000000000ULL, 0U));

  // imm8 = 0b11000000
  __uint128_t res13 = ASM_INSN_WRAP_FUNC_W_RES("fmov %d0, #-1.250e-01")();
  ASSERT_EQ(res13, MakeUInt128(0xbfc0000000000000ULL, 0U));

  // imm8 = 0b11001111
  __uint128_t res14 = ASM_INSN_WRAP_FUNC_W_RES("fmov %d0, #-2.4218750e-01")();
  ASSERT_EQ(res14, MakeUInt128(0xbfcf000000000000ULL, 0U));

  // imm8 = 0b11110000
  __uint128_t res15 = ASM_INSN_WRAP_FUNC_W_RES("fmov %d0, #-1.0e+00")();
  ASSERT_EQ(res15, MakeUInt128(0xbff0000000000000ULL, 0U));

  // imm8 = 0b11111111
  __uint128_t res16 = ASM_INSN_WRAP_FUNC_W_RES("fmov %d0, #-1.93750e+00")();
  ASSERT_EQ(res16, MakeUInt128(0xbfff000000000000ULL, 0U));
}

TEST(Arm64InsnTest, MoveImmToF32x4) {
  // The tests below verify that fmov works with various immediates.
  // Specifically, the instruction has an 8-bit immediate field consisting of
  // the following four subfields:
  //
  // - sign (one bit)
  // - upper exponent (one bit)
  // - lower exponent (two bits)
  // - mantisa (four bits)
  //
  // We enumerate all possible 8-bit immediate encodings of the form:
  //
  //   {0,1}{0,1}{00,11}{0000,1111}
  //
  // to verify that the decoder correctly splits the immediate into the
  // subfields and reconstructs the intended floating-point value.

  // imm8 = 0b00000000
  __uint128_t res1 = ASM_INSN_WRAP_FUNC_W_RES("fmov %0.4s, #2.0e+00")();
  ASSERT_EQ(res1, MakeUInt128(0x4000000040000000ULL, 0x4000000040000000ULL));

  // imm8 = 0b00001111
  __uint128_t res2 = ASM_INSN_WRAP_FUNC_W_RES("fmov %0.4s, #3.8750e+00")();
  ASSERT_EQ(res2, MakeUInt128(0x4078000040780000ULL, 0x4078000040780000ULL));

  // imm8 = 0b00110000
  __uint128_t res3 = ASM_INSN_WRAP_FUNC_W_RES("fmov %0.4s, #1.60e+01")();
  ASSERT_EQ(res3, MakeUInt128(0x4180000041800000ULL, 0x4180000041800000ULL));

  // imm8 = 0b00111111
  __uint128_t res4 = ASM_INSN_WRAP_FUNC_W_RES("fmov %0.4s, #3.10e+01")();
  ASSERT_EQ(res4, MakeUInt128(0x41f8000041f80000ULL, 0x41f8000041f80000ULL));

  // imm8 = 0b01000000
  __uint128_t res5 = ASM_INSN_WRAP_FUNC_W_RES("fmov %0.4s, #1.250e-01")();
  ASSERT_EQ(res5, MakeUInt128(0x3e0000003e000000ULL, 0x3e0000003e000000ULL));

  // imm8 = 0b01001111
  __uint128_t res6 = ASM_INSN_WRAP_FUNC_W_RES("fmov %0.4s, #2.4218750e-01")();
  ASSERT_EQ(res6, MakeUInt128(0x3e7800003e780000ULL, 0x3e7800003e780000ULL));

  // imm8 = 0b01110000
  __uint128_t res7 = ASM_INSN_WRAP_FUNC_W_RES("fmov %0.4s, #1.0e+00")();
  ASSERT_EQ(res7, MakeUInt128(0x3f8000003f800000ULL, 0x3f8000003f800000ULL));

  // imm8 = 0b01111111
  __uint128_t res8 = ASM_INSN_WRAP_FUNC_W_RES("fmov %0.4s, #1.93750e+00")();
  ASSERT_EQ(res8, MakeUInt128(0x3ff800003ff80000ULL, 0x3ff800003ff80000ULL));

  // imm8 = 0b10000000
  __uint128_t res9 = ASM_INSN_WRAP_FUNC_W_RES("fmov %0.4s, #-2.0e+00")();
  ASSERT_EQ(res9, MakeUInt128(0xc0000000c0000000ULL, 0xc0000000c0000000ULL));

  // imm8 = 0b10001111
  __uint128_t res10 = ASM_INSN_WRAP_FUNC_W_RES("fmov %0.4s, #-3.8750e+00")();
  ASSERT_EQ(res10, MakeUInt128(0xc0780000c0780000ULL, 0xc0780000c0780000ULL));

  // imm8 = 0b10110000
  __uint128_t res11 = ASM_INSN_WRAP_FUNC_W_RES("fmov %0.4s, #-1.60e+01")();
  ASSERT_EQ(res11, MakeUInt128(0xc1800000c1800000ULL, 0xc1800000c1800000ULL));

  // imm8 = 0b10111111
  __uint128_t res12 = ASM_INSN_WRAP_FUNC_W_RES("fmov %0.4s, #-3.10e+01")();
  ASSERT_EQ(res12, MakeUInt128(0xc1f80000c1f80000ULL, 0xc1f80000c1f80000ULL));

  // imm8 = 0b11000000
  __uint128_t res13 = ASM_INSN_WRAP_FUNC_W_RES("fmov %0.4s, #-1.250e-01")();
  ASSERT_EQ(res13, MakeUInt128(0xbe000000be000000ULL, 0xbe000000be000000ULL));

  // imm8 = 0b11001111
  __uint128_t res14 = ASM_INSN_WRAP_FUNC_W_RES("fmov %0.4s, #-2.4218750e-01")();
  ASSERT_EQ(res14, MakeUInt128(0xbe780000be780000ULL, 0xbe780000be780000ULL));

  // imm8 = 0b11110000
  __uint128_t res15 = ASM_INSN_WRAP_FUNC_W_RES("fmov %0.4s, #-1.0e+00")();
  ASSERT_EQ(res15, MakeUInt128(0xbf800000bf800000ULL, 0xbf800000bf800000ULL));

  // imm8 = 0b11111111
  __uint128_t res16 = ASM_INSN_WRAP_FUNC_W_RES("fmov %0.4s, #-1.93750e+00")();
  ASSERT_EQ(res16, MakeUInt128(0xbff80000bff80000ULL, 0xbff80000bff80000ULL));
}

TEST(Arm64InsnTest, MoveImmToF64x2) {
  // The tests below verify that fmov works with various immediates.
  // Specifically, the instruction has an 8-bit immediate field consisting of
  // the following four subfields:
  //
  // - sign (one bit)
  // - upper exponent (one bit)
  // - lower exponent (two bits)
  // - mantisa (four bits)
  //
  // We enumerate all possible 8-bit immediate encodings of the form:
  //
  //   {0,1}{0,1}{00,11}{0000,1111}
  //
  // to verify that the decoder correctly splits the immediate into the
  // subfields and reconstructs the intended floating-point value.

  // imm8 = 0b00000000
  __uint128_t res1 = ASM_INSN_WRAP_FUNC_W_RES("fmov %0.2d, #2.0e+00")();
  ASSERT_EQ(res1, MakeUInt128(0x4000000000000000ULL, 0x4000000000000000ULL));

  // imm8 = 0b00001111
  __uint128_t res2 = ASM_INSN_WRAP_FUNC_W_RES("fmov %0.2d, #3.8750e+00")();
  ASSERT_EQ(res2, MakeUInt128(0x400f000000000000ULL, 0x400f000000000000ULL));

  // imm8 = 0b00110000
  __uint128_t res3 = ASM_INSN_WRAP_FUNC_W_RES("fmov %0.2d, #1.60e+01")();
  ASSERT_EQ(res3, MakeUInt128(0x4030000000000000ULL, 0x4030000000000000ULL));

  // imm8 = 0b00111111
  __uint128_t res4 = ASM_INSN_WRAP_FUNC_W_RES("fmov %0.2d, #3.10e+01")();
  ASSERT_EQ(res4, MakeUInt128(0x403f000000000000ULL, 0x403f000000000000ULL));

  // imm8 = 0b01000000
  __uint128_t res5 = ASM_INSN_WRAP_FUNC_W_RES("fmov %0.2d, #1.250e-01")();
  ASSERT_EQ(res5, MakeUInt128(0x3fc0000000000000ULL, 0x3fc0000000000000ULL));

  // imm8 = 0b01001111
  __uint128_t res6 = ASM_INSN_WRAP_FUNC_W_RES("fmov %0.2d, #2.4218750e-01")();
  ASSERT_EQ(res6, MakeUInt128(0x3fcf000000000000ULL, 0x3fcf000000000000ULL));

  // imm8 = 0b01110000
  __uint128_t res7 = ASM_INSN_WRAP_FUNC_W_RES("fmov %0.2d, #1.0e+00")();
  ASSERT_EQ(res7, MakeUInt128(0x3ff0000000000000ULL, 0x3ff0000000000000ULL));

  // imm8 = 0b01111111
  __uint128_t res8 = ASM_INSN_WRAP_FUNC_W_RES("fmov %0.2d, #1.93750e+00")();
  ASSERT_EQ(res8, MakeUInt128(0x3fff000000000000ULL, 0x3fff000000000000ULL));

  // imm8 = 0b10000000
  __uint128_t res9 = ASM_INSN_WRAP_FUNC_W_RES("fmov %0.2d, #-2.0e+00")();
  ASSERT_EQ(res9, MakeUInt128(0xc000000000000000ULL, 0xc000000000000000ULL));

  // imm8 = 0b10001111
  __uint128_t res10 = ASM_INSN_WRAP_FUNC_W_RES("fmov %0.2d, #-3.8750e+00")();
  ASSERT_EQ(res10, MakeUInt128(0xc00f000000000000ULL, 0xc00f000000000000ULL));

  // imm8 = 0b10110000
  __uint128_t res11 = ASM_INSN_WRAP_FUNC_W_RES("fmov %0.2d, #-1.60e+01")();
  ASSERT_EQ(res11, MakeUInt128(0xc030000000000000ULL, 0xc030000000000000ULL));

  // imm8 = 0b10111111
  __uint128_t res12 = ASM_INSN_WRAP_FUNC_W_RES("fmov %0.2d, #-3.10e+01")();
  ASSERT_EQ(res12, MakeUInt128(0xc03f000000000000ULL, 0xc03f000000000000ULL));

  // imm8 = 0b11000000
  __uint128_t res13 = ASM_INSN_WRAP_FUNC_W_RES("fmov %0.2d, #-1.250e-01")();
  ASSERT_EQ(res13, MakeUInt128(0xbfc0000000000000ULL, 0xbfc0000000000000ULL));

  // imm8 = 0b11001111
  __uint128_t res14 = ASM_INSN_WRAP_FUNC_W_RES("fmov %0.2d, #-2.4218750e-01")();
  ASSERT_EQ(res14, MakeUInt128(0xbfcf000000000000ULL, 0xbfcf000000000000ULL));

  // imm8 = 0b11110000
  __uint128_t res15 = ASM_INSN_WRAP_FUNC_W_RES("fmov %0.2d, #-1.0e+00")();
  ASSERT_EQ(res15, MakeUInt128(0xbff0000000000000ULL, 0xbff0000000000000ULL));

  // imm8 = 0b11111111
  __uint128_t res16 = ASM_INSN_WRAP_FUNC_W_RES("fmov %0.2d, #-1.93750e+00")();
  ASSERT_EQ(res16, MakeUInt128(0xbfff000000000000ULL, 0xbfff000000000000ULL));
}

TEST(Arm64InsnTest, MoveFpRegToReg) {
  __uint128_t arg = MakeUInt128(0x1111aaaa2222bbbbULL, 0x3333cccc4444ddddULL);
  uint64_t res = 0xffffeeeeddddccccULL;

  // Move from high double.
  asm("fmov %0, %1.d[1]" : "=r"(res) : "w"(arg));
  ASSERT_EQ(res, 0x3333cccc4444ddddULL);

  // Move from low double.
  asm("fmov %0, %d1" : "=r"(res) : "w"(arg));
  ASSERT_EQ(res, 0x1111aaaa2222bbbbULL);

  // Move from single.
  asm("fmov %w0, %s1" : "=r"(res) : "w"(arg));
  ASSERT_EQ(res, 0x2222bbbbULL);
}

TEST(Arm64InsnTest, MoveRegToFpReg) {
  uint64_t arg = 0xffffeeeeddddccccULL;
  __uint128_t res = MakeUInt128(0x1111aaaa2222bbbbULL, 0x3333cccc4444ddddULL);

  // Move to high double.
  asm("fmov %0.d[1], %1" : "=w"(res) : "r"(arg), "0"(res));
  ASSERT_EQ(res, MakeUInt128(0x1111aaaa2222bbbbULL, 0xffffeeeeddddccccULL));

  // Move to low double.
  asm("fmov %d0, %1" : "=w"(res) : "r"(arg));
  ASSERT_EQ(res, MakeUInt128(0xffffeeeeddddccccULL, 0x0));

  // Move to single.
  asm("fmov %s0, %w1" : "=w"(res) : "r"(arg));
  ASSERT_EQ(res, MakeUInt128(0xddddccccULL, 0x0));
}

TEST(Arm64InsnTest, MoveFpRegToFpReg) {
  __uint128_t res;

  __uint128_t fp64_arg =
      MakeUInt128(0x402e9eb851eb851fULL, 0xdeadbeefaabbccddULL);  // 15.31 in double
  asm("fmov %d0, %d1" : "=w"(res) : "w"(fp64_arg));
  ASSERT_EQ(res, MakeUInt128(0x402e9eb851eb851fULL, 0ULL));

  __uint128_t fp32_arg =
      MakeUInt128(0xaabbccdd40e51eb8ULL, 0x0011223344556677ULL);  // 7.16 in float
  asm("fmov %s0, %s1" : "=w"(res) : "w"(fp32_arg));
  ASSERT_EQ(res, MakeUInt128(0x40e51eb8ULL, 0ULL));
}

TEST(Arm64InsnTest, InsertRegPartIntoSimd128) {
  uint64_t arg = 0xffffeeeeddddccccULL;
  __uint128_t res = MakeUInt128(0x1111aaaa2222bbbbULL, 0x3333cccc4444ddddULL);

  // Byte.
  asm("mov %0.b[3], %w1" : "=w"(res) : "r"(arg), "0"(res));
  ASSERT_EQ(res, MakeUInt128(0x1111aaaacc22bbbbULL, 0x3333cccc4444ddddULL));

  // Double word.
  asm("mov %0.d[1], %1" : "=w"(res) : "r"(arg), "0"(res));
  ASSERT_EQ(res, MakeUInt128(0x1111aaaacc22bbbbULL, 0xffffeeeeddddccccULL));
}

TEST(Arm64InsnTest, DuplicateRegIntoSimd128) {
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_R_ARG("dup %0.16b, %w1")(0xabU);
  ASSERT_EQ(res, MakeUInt128(0xababababababababULL, 0xababababababababULL));
}

TEST(Arm64InsnTest, MoveSimd128ElemToRegSigned) {
  uint64_t res = 0;
  __uint128_t arg = MakeUInt128(0x9796959493929190ULL, 0x9f9e9d9c9b9a99ULL);

  // Single word.
  asm("smov %0, %1.s[0]" : "=r"(res) : "w"(arg));
  ASSERT_EQ(res, 0xffffffff93929190ULL);

  asm("smov %0, %1.s[2]" : "=r"(res) : "w"(arg));
  ASSERT_EQ(res, 0xffffffff9c9b9a99ULL);

  // Half word.
  asm("smov %w0, %1.h[0]" : "=r"(res) : "w"(arg));
  ASSERT_EQ(res, 0x00000000ffff9190ULL);

  asm("smov %w0, %1.h[2]" : "=r"(res) : "w"(arg));
  ASSERT_EQ(res, 0x00000000ffff9594ULL);

  // Byte.
  asm("smov %w0, %1.b[0]" : "=r"(res) : "w"(arg));
  ASSERT_EQ(res, 0x00000000ffffff90ULL);

  asm("smov %w0, %1.b[2]" : "=r"(res) : "w"(arg));
  ASSERT_EQ(res, 0x00000000ffffff92ULL);
}

TEST(Arm64InsnTest, MoveSimd128ElemToRegUnsigned) {
  uint64_t res = 0;
  __uint128_t arg = MakeUInt128(0xaaaabbbbcccceeeeULL, 0xffff000011112222ULL);

  // Double word.
  asm("umov %0, %1.d[0]" : "=r"(res) : "w"(arg));
  ASSERT_EQ(res, 0xaaaabbbbcccceeeeULL);

  asm("umov %0, %1.d[1]" : "=r"(res) : "w"(arg));
  ASSERT_EQ(res, 0xffff000011112222ULL);

  // Single word.
  asm("umov %w0, %1.s[0]" : "=r"(res) : "w"(arg));
  ASSERT_EQ(res, 0xcccceeeeULL);

  asm("umov %w0, %1.s[2]" : "=r"(res) : "w"(arg));
  ASSERT_EQ(res, 0x11112222ULL);

  // Half word.
  asm("umov %w0, %1.h[0]" : "=r"(res) : "w"(arg));
  ASSERT_EQ(res, 0xeeeeULL);

  asm("umov %w0, %1.h[2]" : "=r"(res) : "w"(arg));
  ASSERT_EQ(res, 0xbbbbULL);

  // Byte.
  asm("umov %w0, %1.b[0]" : "=r"(res) : "w"(arg));
  ASSERT_EQ(res, 0xeeULL);

  asm("umov %w0, %1.b[2]" : "=r"(res) : "w"(arg));
  ASSERT_EQ(res, 0xccULL);
}

TEST(Arm64InsnTest, SignedMultiplyAddLongElemI16x4) {
  __uint128_t arg1 = MakeUInt128(0x9463229563989898ULL, 0x9358211674562701ULL);
  __uint128_t arg2 = MakeUInt128(0x0218356462201349ULL, 0x6715188190973038ULL);
  __uint128_t arg3 = MakeUInt128(0x1198004973407239ULL, 0x6103685406643193ULL);
  __uint128_t res =
      ASM_INSN_WRAP_FUNC_W_RES_WW0_ARG("smlal %0.4s, %1.4h, %2.h[1]")(arg1, arg2, arg3);
  ASSERT_EQ(res, MakeUInt128(0x37c4a3494b9db539ULL, 0x37c3dab413a58e33ULL));
}

TEST(Arm64InsnTest, SignedMultiplyAddLongElemI16x4Upper) {
  __uint128_t arg1 = MakeUInt128(0x9478221818528624ULL, 0x0851400666044332ULL);
  __uint128_t arg2 = MakeUInt128(0x5888569867054315ULL, 0x4706965747458550ULL);
  __uint128_t arg3 = MakeUInt128(0x3323233421073015ULL, 0x4594051655379068ULL);
  __uint128_t res =
      ASM_INSN_WRAP_FUNC_W_RES_WW0_ARG("smlal2 %0.4s, %1.8h, %2.h[1]")(arg1, arg2, arg3);
  ASSERT_EQ(res, MakeUInt128(0x5c30bd483c119e0fULL, 0x48ecc5ab6efb3a86ULL));
}

TEST(Arm64InsnTest, SignedMultiplyAddLongElemI16x4Upper2) {
  __uint128_t arg1 = MakeUInt128(0x9968262824727064ULL, 0x1336222178923903ULL);
  __uint128_t arg2 = MakeUInt128(0x1760854289437339ULL, 0x3561889165125042ULL);
  __uint128_t arg3 = MakeUInt128(0x4404008952719837ULL, 0x8738648058472689ULL);
  __uint128_t res =
      ASM_INSN_WRAP_FUNC_W_RES_WW0_ARG("smlal2 %0.4s, %1.8h, %2.h[7]")(arg1, arg2, arg3);
  ASSERT_EQ(res, MakeUInt128(0x5d27e9db5e54d15aULL, 0x8b39d9f65f64ea0aULL));
}

TEST(Arm64InsnTest, SignedMultiplySubtractLongElemI16x4) {
  __uint128_t arg1 = MakeUInt128(0x9143447886360410ULL, 0x3182350736502778ULL);
  __uint128_t arg2 = MakeUInt128(0x5908975782727313ULL, 0x0504889398900992ULL);
  __uint128_t arg3 = MakeUInt128(0x3913503373250855ULL, 0x9826558670892426ULL);
  __uint128_t res =
      ASM_INSN_WRAP_FUNC_W_RES_WW0_ARG("smlsl %0.4s, %1.4h, %2.h[1]")(arg1, arg2, arg3);
  ASSERT_EQ(res, MakeUInt128(0xfd58202775231935ULL, 0x61d69fb0921db6b6ULL));
}

TEST(Arm64InsnTest, SignedMultiplySubtractLongElemI16x4Upper) {
  __uint128_t arg1 = MakeUInt128(0x9320199199688285ULL, 0x1718395366913452ULL);
  __uint128_t arg2 = MakeUInt128(0x2244470804592396ULL, 0x6028171565515656ULL);
  __uint128_t arg3 = MakeUInt128(0x6611135982311225ULL, 0x0628905854914509ULL);
  __uint128_t res =
      ASM_INSN_WRAP_FUNC_W_RES_WW0_ARG("smlsl2 %0.4s, %1.8h, %2.h[1]")(arg1, arg2, arg3);
  ASSERT_EQ(res, MakeUInt128(0x645326f0814d99a3ULL, 0x05c4290053980b2eULL));
}

TEST(Arm64InsnTest, UnsignedMultiplyAddLongElemI16x4) {
  __uint128_t arg1 = MakeUInt128(0x9027601834840306ULL, 0x8113818551059797ULL);
  __uint128_t arg2 = MakeUInt128(0x0566400750942608ULL, 0x7885735796037324ULL);
  __uint128_t arg3 = MakeUInt128(0x5141467867036880ULL, 0x9880609716425849ULL);
  __uint128_t res =
      ASM_INSN_WRAP_FUNC_W_RES_WW0_ARG("umlal %0.4s, %1.4h, %2.h[1]")(arg1, arg2, arg3);
  ASSERT_EQ(res, MakeUInt128(0x61c8e2c867f707f8ULL, 0xc5dfe72334816629ULL));
}

TEST(Arm64InsnTest, UnsignedMultiplyAddLongElemI16x4Upper) {
  __uint128_t arg1 = MakeUInt128(0x9454236828860613ULL, 0x4084148637767009ULL);
  __uint128_t arg2 = MakeUInt128(0x6120715124914043ULL, 0x0272538607648236ULL);
  __uint128_t arg3 = MakeUInt128(0x3414334623518975ULL, 0x7664521641376796ULL);
  __uint128_t res =
      ASM_INSN_WRAP_FUNC_W_RES_WW0_ARG("umlal2 %0.4s, %1.8h, %2.h[1]")(arg1, arg2, arg3);
  ASSERT_EQ(res, MakeUInt128(0x3c00351c3352428eULL, 0x7f9b6cda4425df7cULL));
}

TEST(Arm64InsnTest, UnsignedMultiplySubtractLongElemI16x4) {
  __uint128_t arg1 = MakeUInt128(0x9128009282525619ULL, 0x0205263016391147ULL);
  __uint128_t arg2 = MakeUInt128(0x7247331485739107ULL, 0x7758744253876117ULL);
  __uint128_t arg3 = MakeUInt128(0x4657867116941477ULL, 0x6421441111263583ULL);
  __uint128_t res =
      ASM_INSN_WRAP_FUNC_W_RES_WW0_ARG("umlsl %0.4s, %1.4h, %2.h[1]")(arg1, arg2, arg3);
  ASSERT_EQ(res, MakeUInt128(0x0268619be9b26a3cULL, 0x1876471910da19edULL));
}

TEST(Arm64InsnTest, UnsignedMultiplySubtractLongElemI16x4Upper) {
  __uint128_t arg1 = MakeUInt128(0x9420757136275167ULL, 0x4573189189456283ULL);
  __uint128_t arg2 = MakeUInt128(0x5257044133543758ULL, 0x5753426986994725ULL);
  __uint128_t arg3 = MakeUInt128(0x4703165661399199ULL, 0x9682628247270641ULL);
  __uint128_t res =
      ASM_INSN_WRAP_FUNC_W_RES_WW0_ARG("umlsl2 %0.4s, %1.8h, %2.h[1]")(arg1, arg2, arg3);
  ASSERT_EQ(res, MakeUInt128(0x2b7d4cb24d79259dULL, 0x8895afc6423a13adULL));
}

TEST(Arm64InsnTest, AsmConvertI32F32) {
  constexpr auto AsmConvertI32F32 = ASM_INSN_WRAP_FUNC_W_RES_R_ARG("scvtf %s0, %w1");
  ASSERT_EQ(AsmConvertI32F32(21), MakeUInt128(0x41a80000U, 0U));
}

TEST(Arm64InsnTest, AsmConvertU32F32) {
  constexpr auto AsmConvertU32F32 = ASM_INSN_WRAP_FUNC_W_RES_R_ARG("ucvtf %s0, %w1");

  ASSERT_EQ(AsmConvertU32F32(29), MakeUInt128(0x41e80000U, 0U));

  // Test that the topmost bit isn't treated as the sign.
  ASSERT_EQ(AsmConvertU32F32(1U << 31), MakeUInt128(0x4f000000U, 0U));
}

TEST(Arm64InsnTest, AsmConvertU32F32FromSimdReg) {
  constexpr auto AsmUcvtf = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("ucvtf %s0, %s1");

  ASSERT_EQ(AsmUcvtf(28), MakeUInt128(0x41e00000U, 0U));

  // Test that the topmost bit isn't treated as the sign.
  ASSERT_EQ(AsmUcvtf(1U << 31), MakeUInt128(0x4f000000U, 0U));
}

TEST(Arm64InsnTest, AsmConvertI32F64) {
  constexpr auto AsmConvertI32F64 = ASM_INSN_WRAP_FUNC_W_RES_R_ARG("scvtf %d0, %w1");
  ASSERT_EQ(AsmConvertI32F64(21), MakeUInt128(0x4035000000000000ULL, 0U));
}

TEST(Arm64InsnTest, AsmConvertU32F64) {
  constexpr auto AsmConvertU32F64 = ASM_INSN_WRAP_FUNC_W_RES_R_ARG("ucvtf %d0, %w1");

  ASSERT_EQ(AsmConvertU32F64(18), MakeUInt128(0x4032000000000000ULL, 0U));

  // Test that the topmost bit isn't treated as the sign.
  ASSERT_EQ(AsmConvertU32F64(1U << 31), MakeUInt128(0x41e0000000000000ULL, 0U));
}

TEST(Arm64InsnTest, AsmConvertI64F32) {
  constexpr auto AsmConvertI64F32 = ASM_INSN_WRAP_FUNC_W_RES_R_ARG("scvtf %s0, %x1");
  ASSERT_EQ(AsmConvertI64F32(11), MakeUInt128(0x41300000U, 0U));
}

TEST(Arm64InsnTest, AsmConvertU64F32) {
  constexpr auto AsmConvertU64F32 = ASM_INSN_WRAP_FUNC_W_RES_R_ARG("ucvtf %s0, %x1");

  ASSERT_EQ(AsmConvertU64F32(3), MakeUInt128(0x40400000U, 0U));

  // Test that the topmost bit isn't treated as the sign.
  ASSERT_EQ(AsmConvertU64F32(1ULL << 63), MakeUInt128(0x5f000000U, 0U));
}

TEST(Arm64InsnTest, AsmConvertI64F64) {
  constexpr auto AsmConvertI64F64 = ASM_INSN_WRAP_FUNC_W_RES_R_ARG("scvtf %d0, %x1");
  ASSERT_EQ(AsmConvertI64F64(137), MakeUInt128(0x4061200000000000ULL, 0U));
}

TEST(Arm64InsnTest, AsmConvertI32F32FromSimdReg) {
  constexpr auto AsmConvertI32F32 = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("scvtf %s0, %s1");
  ASSERT_EQ(AsmConvertI32F32(1109), MakeUInt128(0x448aa000ULL, 0U));
}

TEST(Arm64InsnTest, AsmConvertI64F64FromSimdReg) {
  constexpr auto AsmConvertI64F64 = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("scvtf %d0, %d1");
  ASSERT_EQ(AsmConvertI64F64(123), MakeUInt128(0x405ec00000000000ULL, 0U));
}

TEST(Arm64InsnTest, AsmConvertI32x4F32x4) {
  constexpr auto AsmConvertI32F32 = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("scvtf %0.4s, %1.4s");
  __uint128_t arg = MakeUInt128(0x0000003500000014ULL, 0x0000005400000009ULL);
  ASSERT_EQ(AsmConvertI32F32(arg), MakeUInt128(0x4254000041a00000ULL, 0x42a8000041100000ULL));
}

TEST(Arm64InsnTest, AsmConvertI64x2F64x2) {
  constexpr auto AsmConvertI64F64 = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("scvtf %0.2d, %1.2d");
  __uint128_t arg = MakeUInt128(static_cast<int64_t>(-9), 17U);
  ASSERT_EQ(AsmConvertI64F64(arg), MakeUInt128(0xc022000000000000ULL, 0x4031000000000000ULL));
}

TEST(Arm64InsnTest, AsmConvertU32x4F32x4) {
  constexpr auto AsmConvertU32F32 = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("ucvtf %0.4s, %1.4s");
  __uint128_t arg = MakeUInt128(0x8000000000000019ULL, 0x0000005800000010ULL);
  ASSERT_EQ(AsmConvertU32F32(arg), MakeUInt128(0x4f00000041c80000ULL, 0x42b0000041800000ULL));
}

TEST(Arm64InsnTest, AsmConvertU64x2F64x2) {
  constexpr auto AsmConvertU64F64 = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("ucvtf %0.2d, %1.2d");
  __uint128_t arg = MakeUInt128(1ULL << 63, 29U);
  ASSERT_EQ(AsmConvertU64F64(arg), MakeUInt128(0x43e0000000000000ULL, 0x403d000000000000ULL));
}

TEST(Arm64InsnTest, AsmConvertU64F64) {
  constexpr auto AsmConvertU64F64 = ASM_INSN_WRAP_FUNC_W_RES_R_ARG("ucvtf %d0, %x1");

  ASSERT_EQ(AsmConvertU64F64(49), MakeUInt128(0x4048800000000000ULL, 0U));

  // Test that the topmost bit isn't treated as the sign.
  ASSERT_EQ(AsmConvertU64F64(1ULL << 63), MakeUInt128(0x43e0000000000000ULL, 0U));
}

TEST(Arm64InsnTest, AsmConvertU64F64FromSimdReg) {
  constexpr auto AsmUcvtf = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("ucvtf %d0, %d1");

  ASSERT_EQ(AsmUcvtf(47), MakeUInt128(0x4047800000000000ULL, 0U));

  // Test that the topmost bit isn't treated as the sign.
  ASSERT_EQ(AsmUcvtf(1ULL << 63), MakeUInt128(0x43e0000000000000ULL, 0U));
}

TEST(Arm64InsnTest, AsmConvertLiterals) {
  // Verify that the compiler encodes the floating-point literals used in the
  // conversion tests below exactly as expected.
  ASSERT_EQ(bit_cast<uint32_t>(-7.50f), 0xc0f00000U);
  ASSERT_EQ(bit_cast<uint32_t>(-6.75f), 0xc0d80000U);
  ASSERT_EQ(bit_cast<uint32_t>(-6.50f), 0xc0d00000U);
  ASSERT_EQ(bit_cast<uint32_t>(-6.25f), 0xc0c80000U);
  ASSERT_EQ(bit_cast<uint32_t>(6.25f), 0x40c80000U);
  ASSERT_EQ(bit_cast<uint32_t>(6.50f), 0x40d00000U);
  ASSERT_EQ(bit_cast<uint32_t>(6.75f), 0x40d80000U);
  ASSERT_EQ(bit_cast<uint32_t>(7.50f), 0x40f00000U);

  ASSERT_EQ(bit_cast<uint64_t>(-7.50), 0xc01e000000000000ULL);
  ASSERT_EQ(bit_cast<uint64_t>(-6.75), 0xc01b000000000000ULL);
  ASSERT_EQ(bit_cast<uint64_t>(-6.50), 0xc01a000000000000ULL);
  ASSERT_EQ(bit_cast<uint64_t>(-6.25), 0xc019000000000000ULL);
  ASSERT_EQ(bit_cast<uint64_t>(6.25), 0x4019000000000000ULL);
  ASSERT_EQ(bit_cast<uint64_t>(6.50), 0x401a000000000000ULL);
  ASSERT_EQ(bit_cast<uint64_t>(6.75), 0x401b000000000000ULL);
  ASSERT_EQ(bit_cast<uint64_t>(7.50), 0x401e000000000000ULL);
}

template <typename IntType, typename FuncType>
void TestConvertF32ToInt(FuncType AsmFunc, std::initializer_list<int> expected) {
  // Note that bit_cast isn't a constexpr.
  static const uint32_t kConvertF32ToIntInputs[] = {
      bit_cast<uint32_t>(-7.50f),
      bit_cast<uint32_t>(-6.75f),
      bit_cast<uint32_t>(-6.50f),
      bit_cast<uint32_t>(-6.25f),
      bit_cast<uint32_t>(6.25f),
      bit_cast<uint32_t>(6.50f),
      bit_cast<uint32_t>(6.75f),
      bit_cast<uint32_t>(7.50f),
  };

  const size_t kConvertF32ToIntInputsSize = sizeof(kConvertF32ToIntInputs) / sizeof(uint32_t);
  ASSERT_EQ(kConvertF32ToIntInputsSize, expected.size());

  auto expected_it = expected.begin();
  for (size_t input_it = 0; input_it < kConvertF32ToIntInputsSize; input_it++) {
    ASSERT_EQ(AsmFunc(kConvertF32ToIntInputs[input_it]), static_cast<IntType>(*expected_it++));
  }
}

TEST(Arm64InsnTest, AsmConvertF32I32TieAway) {
  constexpr auto AsmFcvtas = ASM_INSN_WRAP_FUNC_R_RES_W_ARG("fcvtas %w0, %s1");
  TestConvertF32ToInt<uint32_t>(AsmFcvtas, {-8, -7, -7, -6, 6U, 7U, 7U, 8U});
}

TEST(Arm64InsnTest, AsmConvertF32U32TieAway) {
  constexpr auto AsmFcvtau = ASM_INSN_WRAP_FUNC_R_RES_W_ARG("fcvtau %w0, %s1");
  TestConvertF32ToInt<uint32_t>(AsmFcvtau, {0U, 0U, 0U, 0U, 6U, 7U, 7U, 8U});
}

TEST(Arm64InsnTest, AsmConvertF32I32NegInf) {
  constexpr auto AsmFcvtms = ASM_INSN_WRAP_FUNC_R_RES_W_ARG("fcvtms %w0, %s1");
  TestConvertF32ToInt<uint32_t>(AsmFcvtms, {-8, -7, -7, -7, 6U, 6U, 6U, 7U});
}

TEST(Arm64InsnTest, AsmConvertF32U32NegInf) {
  constexpr auto AsmFcvtmu = ASM_INSN_WRAP_FUNC_R_RES_W_ARG("fcvtmu %w0, %s1");
  TestConvertF32ToInt<uint32_t>(AsmFcvtmu, {0U, 0U, 0U, 0U, 6U, 6U, 6U, 7U});
}

TEST(Arm64InsnTest, AsmConvertF32I32TieEven) {
  constexpr auto AsmFcvtns = ASM_INSN_WRAP_FUNC_R_RES_W_ARG("fcvtns %w0, %s1");
  TestConvertF32ToInt<uint32_t>(AsmFcvtns, {-8, -7, -6, -6, 6U, 6U, 7U, 8U});
}

TEST(Arm64InsnTest, AsmConvertF32U32TieEven) {
  constexpr auto AsmFcvtnu = ASM_INSN_WRAP_FUNC_R_RES_W_ARG("fcvtnu %w0, %s1");
  TestConvertF32ToInt<uint32_t>(AsmFcvtnu, {0U, 0U, 0U, 0U, 6U, 6U, 7U, 8U});
}

TEST(Arm64InsnTest, AsmConvertF32I32PosInf) {
  constexpr auto AsmFcvtps = ASM_INSN_WRAP_FUNC_R_RES_W_ARG("fcvtps %w0, %s1");
  TestConvertF32ToInt<uint32_t>(AsmFcvtps, {-7, -6, -6, -6, 7U, 7U, 7U, 8U});
}

TEST(Arm64InsnTest, AsmConvertF32U32PosInf) {
  constexpr auto AsmFcvtpu = ASM_INSN_WRAP_FUNC_R_RES_W_ARG("fcvtpu %w0, %s1");
  TestConvertF32ToInt<uint32_t>(AsmFcvtpu, {0U, 0U, 0U, 0U, 7U, 7U, 7U, 8U});
}

TEST(Arm64InsnTest, AsmConvertF32I32Truncate) {
  constexpr auto AsmFcvtzs = ASM_INSN_WRAP_FUNC_R_RES_W_ARG("fcvtzs %w0, %s1");
  TestConvertF32ToInt<uint32_t>(AsmFcvtzs, {-7, -6, -6, -6, 6U, 6U, 6U, 7U});
}

TEST(Arm64InsnTest, AsmConvertF32U32Truncate) {
  constexpr auto AsmFcvtzu = ASM_INSN_WRAP_FUNC_R_RES_W_ARG("fcvtzu %w0, %s1");
  TestConvertF32ToInt<uint32_t>(AsmFcvtzu, {0U, 0U, 0U, 0U, 6U, 6U, 6U, 7U});
}

TEST(Arm64InsnTest, AsmConvertF32I64TieAway) {
  constexpr auto AsmFcvtas = ASM_INSN_WRAP_FUNC_R_RES_W_ARG("fcvtas %x0, %s1");
  TestConvertF32ToInt<uint64_t>(AsmFcvtas, {-8, -7, -7, -6, 6U, 7U, 7U, 8U});
}

TEST(Arm64InsnTest, AsmConvertF32U64TieAway) {
  constexpr auto AsmFcvtau = ASM_INSN_WRAP_FUNC_R_RES_W_ARG("fcvtau %x0, %s1");
  TestConvertF32ToInt<uint64_t>(AsmFcvtau, {0U, 0U, 0U, 0U, 6U, 7U, 7U, 8U});
}

TEST(Arm64InsnTest, AsmConvertF32I64NegInf) {
  constexpr auto AsmFcvtms = ASM_INSN_WRAP_FUNC_R_RES_W_ARG("fcvtms %x0, %s1");
  TestConvertF32ToInt<uint64_t>(AsmFcvtms, {-8, -7, -7, -7, 6U, 6U, 6U, 7U});
}

TEST(Arm64InsnTest, AsmConvertF32U64NegInf) {
  constexpr auto AsmFcvtmu = ASM_INSN_WRAP_FUNC_R_RES_W_ARG("fcvtmu %x0, %s1");
  TestConvertF32ToInt<uint64_t>(AsmFcvtmu, {0U, 0U, 0U, 0U, 6U, 6U, 6U, 7U});
}

TEST(Arm64InsnTest, AsmConvertF32I64TieEven) {
  constexpr auto AsmFcvtns = ASM_INSN_WRAP_FUNC_R_RES_W_ARG("fcvtns %x0, %s1");
  TestConvertF32ToInt<uint64_t>(AsmFcvtns, {-8, -7, -6, -6, 6U, 6U, 7U, 8U});
}

TEST(Arm64InsnTest, AsmConvertF32U64TieEven) {
  constexpr auto AsmFcvtnu = ASM_INSN_WRAP_FUNC_R_RES_W_ARG("fcvtnu %x0, %s1");
  TestConvertF32ToInt<uint64_t>(AsmFcvtnu, {0U, 0U, 0U, 0U, 6U, 6U, 7U, 8U});
}

TEST(Arm64InsnTest, AsmConvertF32I64PosInf) {
  constexpr auto AsmFcvtps = ASM_INSN_WRAP_FUNC_R_RES_W_ARG("fcvtps %x0, %s1");
  TestConvertF32ToInt<uint64_t>(AsmFcvtps, {-7, -6, -6, -6, 7U, 7U, 7U, 8U});
}

TEST(Arm64InsnTest, AsmConvertF32U64PosInf) {
  constexpr auto AsmFcvtpu = ASM_INSN_WRAP_FUNC_R_RES_W_ARG("fcvtpu %x0, %s1");
  TestConvertF32ToInt<uint64_t>(AsmFcvtpu, {0U, 0U, 0U, 0U, 7U, 7U, 7U, 8U});
}

TEST(Arm64InsnTest, AsmConvertF32I64Truncate) {
  constexpr auto AsmFcvtzs = ASM_INSN_WRAP_FUNC_R_RES_W_ARG("fcvtzs %x0, %s1");
  TestConvertF32ToInt<uint64_t>(AsmFcvtzs, {-7, -6, -6, -6, 6U, 6U, 6U, 7U});
}

TEST(Arm64InsnTest, AsmConvertF32U64Truncate) {
  constexpr auto AsmFcvtzu = ASM_INSN_WRAP_FUNC_R_RES_W_ARG("fcvtzu %x0, %s1");
  TestConvertF32ToInt<uint64_t>(AsmFcvtzu, {0U, 0U, 0U, 0U, 6U, 6U, 6U, 7U});
}

template <typename IntType, typename FuncType>
void TestConvertF64ToInt(FuncType AsmFunc, std::initializer_list<int> expected) {
  // Note that bit_cast isn't a constexpr.
  static const uint64_t kConvertF64ToIntInputs[] = {
      bit_cast<uint64_t>(-7.50),
      bit_cast<uint64_t>(-6.75),
      bit_cast<uint64_t>(-6.50),
      bit_cast<uint64_t>(-6.25),
      bit_cast<uint64_t>(6.25),
      bit_cast<uint64_t>(6.50),
      bit_cast<uint64_t>(6.75),
      bit_cast<uint64_t>(7.50),
  };

  const size_t kConvertF64ToIntInputsSize = sizeof(kConvertF64ToIntInputs) / sizeof(uint64_t);
  ASSERT_EQ(kConvertF64ToIntInputsSize, expected.size());

  auto expected_it = expected.begin();
  for (size_t input_it = 0; input_it < kConvertF64ToIntInputsSize; input_it++) {
    ASSERT_EQ(AsmFunc(kConvertF64ToIntInputs[input_it]), static_cast<IntType>(*expected_it++));
  }
}

TEST(Arm64InsnTest, AsmConvertF64I32TieAway) {
  constexpr auto AsmFcvtas = ASM_INSN_WRAP_FUNC_R_RES_W_ARG("fcvtas %w0, %d1");
  TestConvertF64ToInt<uint32_t>(AsmFcvtas, {-8, -7, -7, -6, 6U, 7U, 7U, 8U});
}

TEST(Arm64InsnTest, AsmConvertF64U32TieAway) {
  constexpr auto AsmFcvtau = ASM_INSN_WRAP_FUNC_R_RES_W_ARG("fcvtau %w0, %d1");
  TestConvertF64ToInt<uint32_t>(AsmFcvtau, {0U, 0U, 0U, 0U, 6U, 7U, 7U, 8U});
}

TEST(Arm64InsnTest, AsmConvertF64I32NegInf) {
  constexpr auto AsmFcvtms = ASM_INSN_WRAP_FUNC_R_RES_W_ARG("fcvtms %w0, %d1");
  TestConvertF64ToInt<uint32_t>(AsmFcvtms, {-8, -7, -7, -7, 6U, 6U, 6U, 7U});
}

TEST(Arm64InsnTest, AsmConvertF64U32NegInf) {
  constexpr auto AsmFcvtmu = ASM_INSN_WRAP_FUNC_R_RES_W_ARG("fcvtmu %w0, %d1");
  TestConvertF64ToInt<uint32_t>(AsmFcvtmu, {0U, 0U, 0U, 0U, 6U, 6U, 6U, 7U});
}

TEST(Arm64InsnTest, AsmConvertF64I32TieEven) {
  constexpr auto AsmFcvtns = ASM_INSN_WRAP_FUNC_R_RES_W_ARG("fcvtns %w0, %d1");
  TestConvertF64ToInt<uint32_t>(AsmFcvtns, {-8, -7, -6, -6, 6U, 6U, 7U, 8U});
}

TEST(Arm64InsnTest, AsmConvertF64U32TieEven) {
  constexpr auto AsmFcvtnu = ASM_INSN_WRAP_FUNC_R_RES_W_ARG("fcvtnu %w0, %d1");
  TestConvertF64ToInt<uint32_t>(AsmFcvtnu, {0U, 0U, 0U, 0U, 6U, 6U, 7U, 8U});
}

TEST(Arm64InsnTest, AsmConvertF64I32PosInf) {
  constexpr auto AsmFcvtps = ASM_INSN_WRAP_FUNC_R_RES_W_ARG("fcvtps %w0, %d1");
  TestConvertF64ToInt<uint32_t>(AsmFcvtps, {-7, -6, -6, -6, 7U, 7U, 7U, 8U});
}

TEST(Arm64InsnTest, AsmConvertF64U32PosInf) {
  constexpr auto AsmFcvtpu = ASM_INSN_WRAP_FUNC_R_RES_W_ARG("fcvtpu %w0, %d1");
  TestConvertF64ToInt<uint32_t>(AsmFcvtpu, {0U, 0U, 0U, 0U, 7U, 7U, 7U, 8U});
}

TEST(Arm64InsnTest, AsmConvertF64I32Truncate) {
  constexpr auto AsmFcvtzs = ASM_INSN_WRAP_FUNC_R_RES_W_ARG("fcvtzs %w0, %d1");
  TestConvertF64ToInt<uint32_t>(AsmFcvtzs, {-7, -6, -6, -6, 6U, 6U, 6U, 7U});
}

TEST(Arm64InsnTest, AsmConvertF64U32Truncate) {
  constexpr auto AsmFcvtzu = ASM_INSN_WRAP_FUNC_R_RES_W_ARG("fcvtzu %w0, %d1");
  TestConvertF64ToInt<uint32_t>(AsmFcvtzu, {0U, 0U, 0U, 0U, 6U, 6U, 6U, 7U});
}

TEST(Arm64InsnTest, AsmConvertF64I64TieAway) {
  constexpr auto AsmFcvtas = ASM_INSN_WRAP_FUNC_R_RES_W_ARG("fcvtas %x0, %d1");
  TestConvertF64ToInt<uint64_t>(AsmFcvtas, {-8, -7, -7, -6, 6U, 7U, 7U, 8U});
}

TEST(Arm64InsnTest, AsmConvertF64U64TieAway) {
  constexpr auto AsmFcvtau = ASM_INSN_WRAP_FUNC_R_RES_W_ARG("fcvtau %x0, %d1");
  TestConvertF64ToInt<uint64_t>(AsmFcvtau, {0U, 0U, 0U, 0U, 6U, 7U, 7U, 8U});
}

TEST(Arm64InsnTest, AsmConvertF64I64NegInf) {
  constexpr auto AsmFcvtms = ASM_INSN_WRAP_FUNC_R_RES_W_ARG("fcvtms %x0, %d1");
  TestConvertF64ToInt<uint64_t>(AsmFcvtms, {-8, -7, -7, -7, 6U, 6U, 6U, 7U});
}

TEST(Arm64InsnTest, AsmConvertF64U64NegInf) {
  constexpr auto AsmFcvtmu = ASM_INSN_WRAP_FUNC_R_RES_W_ARG("fcvtmu %x0, %d1");
  TestConvertF64ToInt<uint64_t>(AsmFcvtmu, {0U, 0U, 0U, 0U, 6U, 6U, 6U, 7U});
}

TEST(Arm64InsnTest, AsmConvertF64I64TieEven) {
  constexpr auto AsmFcvtns = ASM_INSN_WRAP_FUNC_R_RES_W_ARG("fcvtns %x0, %d1");
  TestConvertF64ToInt<uint64_t>(AsmFcvtns, {-8, -7, -6, -6, 6U, 6U, 7U, 8U});
}

TEST(Arm64InsnTest, AsmConvertF64U64TieEven) {
  constexpr auto AsmFcvtnu = ASM_INSN_WRAP_FUNC_R_RES_W_ARG("fcvtnu %x0, %d1");
  TestConvertF64ToInt<uint64_t>(AsmFcvtnu, {0U, 0U, 0U, 0U, 6U, 6U, 7U, 8U});
}

TEST(Arm64InsnTest, AsmConvertF64I64PosInf) {
  constexpr auto AsmFcvtps = ASM_INSN_WRAP_FUNC_R_RES_W_ARG("fcvtps %x0, %d1");
  TestConvertF64ToInt<uint64_t>(AsmFcvtps, {-7, -6, -6, -6, 7U, 7U, 7U, 8U});
}

TEST(Arm64InsnTest, AsmConvertF64U64PosInf) {
  constexpr auto AsmFcvtpu = ASM_INSN_WRAP_FUNC_R_RES_W_ARG("fcvtpu %x0, %d1");
  TestConvertF64ToInt<uint64_t>(AsmFcvtpu, {0U, 0U, 0U, 0U, 7U, 7U, 7U, 8U});
}

TEST(Arm64InsnTest, AsmConvertF64I64Truncate) {
  constexpr auto AsmFcvtzs = ASM_INSN_WRAP_FUNC_R_RES_W_ARG("fcvtzs %x0, %d1");
  TestConvertF64ToInt<uint64_t>(AsmFcvtzs, {-7, -6, -6, -6, 6U, 6U, 6U, 7U});
}

TEST(Arm64InsnTest, AsmConvertF64U64Truncate) {
  constexpr auto AsmFcvtzu = ASM_INSN_WRAP_FUNC_R_RES_W_ARG("fcvtzu %x0, %d1");
  TestConvertF64ToInt<uint64_t>(AsmFcvtzu, {0U, 0U, 0U, 0U, 6U, 6U, 6U, 7U});
}

TEST(Arm64InsnTest, AsmConvertF32I32ScalarTieAway) {
  constexpr auto AsmFcvtas = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcvtas %s0, %s1");
  TestConvertF32ToInt<uint32_t>(AsmFcvtas, {-8, -7, -7, -6, 6U, 7U, 7U, 8U});
}

TEST(Arm64InsnTest, AsmConvertF32U32ScalarTieAway) {
  constexpr auto AsmFcvtau = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcvtau %s0, %s1");
  TestConvertF32ToInt<uint32_t>(AsmFcvtau, {0U, 0U, 0U, 0U, 6U, 7U, 7U, 8U});
}

TEST(Arm64InsnTest, AsmConvertF32I32ScalarNegInf) {
  constexpr auto AsmFcvtms = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcvtms %s0, %s1");
  TestConvertF32ToInt<uint32_t>(AsmFcvtms, {-8, -7, -7, -7, 6U, 6U, 6U, 7U});
}

TEST(Arm64InsnTest, AsmConvertF32U32ScalarNegInf) {
  constexpr auto AsmFcvtmu = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcvtmu %s0, %s1");
  TestConvertF32ToInt<uint32_t>(AsmFcvtmu, {0U, 0U, 0U, 0U, 6U, 6U, 6U, 7U});
}

TEST(Arm64InsnTest, AsmConvertF32I32ScalarTieEven) {
  constexpr auto AsmFcvtns = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcvtns %s0, %s1");
  TestConvertF32ToInt<uint32_t>(AsmFcvtns, {-8, -7, -6, -6, 6U, 6U, 7U, 8U});
}

TEST(Arm64InsnTest, AsmConvertF32U32ScalarTieEven) {
  constexpr auto AsmFcvtnu = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcvtnu %s0, %s1");
  TestConvertF32ToInt<uint32_t>(AsmFcvtnu, {0U, 0U, 0U, 0U, 6U, 6U, 7U, 8U});
}

TEST(Arm64InsnTest, AsmConvertF32I32ScalarPosInf) {
  constexpr auto AsmFcvtps = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcvtps %s0, %s1");
  TestConvertF32ToInt<uint32_t>(AsmFcvtps, {-7, -6, -6, -6, 7U, 7U, 7U, 8U});
}

TEST(Arm64InsnTest, AsmConvertF32U32ScalarPosInf) {
  constexpr auto AsmFcvtpu = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcvtpu %s0, %s1");
  TestConvertF32ToInt<uint32_t>(AsmFcvtpu, {0U, 0U, 0U, 0U, 7U, 7U, 7U, 8U});
}

TEST(Arm64InsnTest, AsmConvertF32I32ScalarTruncate) {
  constexpr auto AsmFcvtzs = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcvtzs %s0, %s1");
  TestConvertF32ToInt<uint32_t>(AsmFcvtzs, {-7, -6, -6, -6, 6U, 6U, 6U, 7U});
}

TEST(Arm64InsnTest, AsmConvertF32U32ScalarTruncate) {
  constexpr auto AsmFcvtzu = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcvtzu %s0, %s1");
  TestConvertF32ToInt<uint32_t>(AsmFcvtzu, {0U, 0U, 0U, 0U, 6U, 6U, 6U, 7U});
}

TEST(Arm64InsnTest, AsmConvertF64I64ScalarTieAway) {
  constexpr auto AsmFcvtas = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcvtas %d0, %d1");
  TestConvertF64ToInt<uint64_t>(AsmFcvtas, {-8, -7, -7, -6, 6U, 7U, 7U, 8U});
}

TEST(Arm64InsnTest, AsmConvertF64U64ScalarTieAway) {
  constexpr auto AsmFcvtau = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcvtau %d0, %d1");
  TestConvertF64ToInt<uint64_t>(AsmFcvtau, {0U, 0U, 0U, 0U, 6U, 7U, 7U, 8U});
}

TEST(Arm64InsnTest, AsmConvertF64I64ScalarNegInf) {
  constexpr auto AsmFcvtms = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcvtms %d0, %d1");
  TestConvertF64ToInt<uint64_t>(AsmFcvtms, {-8, -7, -7, -7, 6U, 6U, 6U, 7U});
}

TEST(Arm64InsnTest, AsmConvertF64U64ScalarNegInf) {
  constexpr auto AsmFcvtmu = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcvtmu %d0, %d1");
  TestConvertF64ToInt<uint64_t>(AsmFcvtmu, {0U, 0U, 0U, 0U, 6U, 6U, 6U, 7U});
}

TEST(Arm64InsnTest, AsmConvertF64I64ScalarTieEven) {
  constexpr auto AsmFcvtns = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcvtns %d0, %d1");
  TestConvertF64ToInt<uint64_t>(AsmFcvtns, {-8, -7, -6, -6, 6U, 6U, 7U, 8U});
}

TEST(Arm64InsnTest, AsmConvertF64U64ScalarTieEven) {
  constexpr auto AsmFcvtnu = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcvtnu %d0, %d1");
  TestConvertF64ToInt<uint64_t>(AsmFcvtnu, {0U, 0U, 0U, 0U, 6U, 6U, 7U, 8U});
}

TEST(Arm64InsnTest, AsmConvertF64I64ScalarPosInf) {
  constexpr auto AsmFcvtps = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcvtps %d0, %d1");
  TestConvertF64ToInt<uint64_t>(AsmFcvtps, {-7, -6, -6, -6, 7U, 7U, 7U, 8U});
}

TEST(Arm64InsnTest, AsmConvertF64U64ScalarPosInf) {
  constexpr auto AsmFcvtpu = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcvtpu %d0, %d1");
  TestConvertF64ToInt<uint64_t>(AsmFcvtpu, {0U, 0U, 0U, 0U, 7U, 7U, 7U, 8U});
}

TEST(Arm64InsnTest, AsmConvertF64I64ScalarTruncate) {
  constexpr auto AsmFcvtzs = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcvtzs %d0, %d1");
  TestConvertF64ToInt<uint64_t>(AsmFcvtzs, {-7, -6, -6, -6, 6U, 6U, 6U, 7U});
}

TEST(Arm64InsnTest, AsmConvertF64U64ScalarTruncate) {
  constexpr auto AsmFcvtzu = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcvtzu %d0, %d1");
  TestConvertF64ToInt<uint64_t>(AsmFcvtzu, {0U, 0U, 0U, 0U, 6U, 6U, 6U, 7U});
}

TEST(Arm64InsnTest, AsmConvertF32I32x4TieAway) {
  constexpr auto AsmFcvtas = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcvtas %0.4s, %1.4s");
  __uint128_t arg1 = MakeF32x4(-7.50f, -6.75f, -6.50f, -6.25f);
  ASSERT_EQ(AsmFcvtas(arg1), MakeUInt128(0xfffffff9fffffff8ULL, 0xfffffffafffffff9ULL));
  __uint128_t arg2 = MakeF32x4(6.25f, 6.50f, 6.75f, 7.50f);
  ASSERT_EQ(AsmFcvtas(arg2), MakeUInt128(0x0000000700000006ULL, 0x0000000800000007ULL));
}

TEST(Arm64InsnTest, AsmConvertF32U32x4TieAway) {
  constexpr auto AsmFcvtau = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcvtau %0.4s, %1.4s");
  __uint128_t arg1 = MakeF32x4(-7.50f, -6.75f, -6.50f, -6.25f);
  ASSERT_EQ(AsmFcvtau(arg1), MakeUInt128(0x0000000000000000ULL, 0x0000000000000000ULL));
  __uint128_t arg2 = MakeF32x4(6.25f, 6.50f, 6.75f, 7.50f);
  ASSERT_EQ(AsmFcvtau(arg2), MakeUInt128(0x0000000700000006ULL, 0x0000000800000007ULL));
}

TEST(Arm64InsnTest, AsmConvertF32I32x4NegInf) {
  constexpr auto AsmFcvtms = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcvtms %0.4s, %1.4s");
  __uint128_t arg1 = MakeF32x4(-7.50f, -6.75f, -6.50f, -6.25f);
  ASSERT_EQ(AsmFcvtms(arg1), MakeUInt128(0xfffffff9fffffff8ULL, 0xfffffff9fffffff9ULL));
  __uint128_t arg2 = MakeF32x4(6.25f, 6.50f, 6.75f, 7.50f);
  ASSERT_EQ(AsmFcvtms(arg2), MakeUInt128(0x0000000600000006ULL, 0x0000000700000006ULL));
}

TEST(Arm64InsnTest, AsmConvertF32U32x4NegInf) {
  constexpr auto AsmFcvtmu = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcvtmu %0.4s, %1.4s");
  __uint128_t arg1 = MakeF32x4(-7.50f, -6.75f, -6.50f, -6.25f);
  ASSERT_EQ(AsmFcvtmu(arg1), MakeUInt128(0x0000000000000000ULL, 0x0000000000000000ULL));
  __uint128_t arg2 = MakeF32x4(6.25f, 6.50f, 6.75f, 7.50f);
  ASSERT_EQ(AsmFcvtmu(arg2), MakeUInt128(0x0000000600000006ULL, 0x0000000700000006ULL));
}

TEST(Arm64InsnTest, AsmConvertF32I32x4TieEven) {
  constexpr auto AsmFcvtns = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcvtns %0.4s, %1.4s");
  __uint128_t arg1 = MakeF32x4(-7.50f, -6.75f, -6.50f, -6.25f);
  ASSERT_EQ(AsmFcvtns(arg1), MakeUInt128(0xfffffff9fffffff8ULL, 0xfffffffafffffffaULL));
  __uint128_t arg2 = MakeF32x4(6.25f, 6.50f, 6.75f, 7.50f);
  ASSERT_EQ(AsmFcvtns(arg2), MakeUInt128(0x0000000600000006ULL, 0x0000000800000007ULL));
}

TEST(Arm64InsnTest, AsmConvertF32U32x4TieEven) {
  constexpr auto AsmFcvtnu = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcvtnu %0.4s, %1.4s");
  __uint128_t arg1 = MakeF32x4(-7.50f, -6.75f, -6.50f, -6.25f);
  ASSERT_EQ(AsmFcvtnu(arg1), MakeUInt128(0x0000000000000000ULL, 0x0000000000000000ULL));
  __uint128_t arg2 = MakeF32x4(6.25f, 6.50f, 6.75f, 7.50f);
  ASSERT_EQ(AsmFcvtnu(arg2), MakeUInt128(0x0000000600000006ULL, 0x0000000800000007ULL));
}

TEST(Arm64InsnTest, AsmConvertF32I32x4PosInf) {
  constexpr auto AsmFcvtps = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcvtps %0.4s, %1.4s");
  __uint128_t arg1 = MakeF32x4(-7.50f, -6.75f, -6.50f, -6.25f);
  ASSERT_EQ(AsmFcvtps(arg1), MakeUInt128(0xfffffffafffffff9ULL, 0xfffffffafffffffaULL));
  __uint128_t arg2 = MakeF32x4(6.25f, 6.50f, 6.75f, 7.50f);
  ASSERT_EQ(AsmFcvtps(arg2), MakeUInt128(0x0000000700000007ULL, 0x0000000800000007ULL));
}

TEST(Arm64InsnTest, AsmConvertF32U32x4PosInf) {
  constexpr auto AsmFcvtpu = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcvtpu %0.4s, %1.4s");
  __uint128_t arg1 = MakeF32x4(-7.50f, -6.75f, -6.50f, -6.25f);
  ASSERT_EQ(AsmFcvtpu(arg1), MakeUInt128(0x0000000000000000ULL, 0x0000000000000000ULL));
  __uint128_t arg2 = MakeF32x4(6.25f, 6.50f, 6.75f, 7.50f);
  ASSERT_EQ(AsmFcvtpu(arg2), MakeUInt128(0x0000000700000007ULL, 0x0000000800000007ULL));
}

TEST(Arm64InsnTest, AsmConvertF32I32x4Truncate) {
  constexpr auto AsmFcvtzs = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcvtzs %0.4s, %1.4s");
  __uint128_t arg1 = MakeF32x4(-7.50f, -6.75f, -6.50f, -6.25f);
  ASSERT_EQ(AsmFcvtzs(arg1), MakeUInt128(0xfffffffafffffff9ULL, 0xfffffffafffffffaULL));
  __uint128_t arg2 = MakeF32x4(6.25f, 6.50f, 6.75f, 7.50f);
  ASSERT_EQ(AsmFcvtzs(arg2), MakeUInt128(0x0000000600000006ULL, 0x0000000700000006ULL));
}

TEST(Arm64InsnTest, AsmConvertF32U32x4Truncate) {
  constexpr auto AsmFcvtzu = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcvtzu %0.4s, %1.4s");
  __uint128_t arg1 = MakeF32x4(-7.50f, -6.75f, -6.50f, -6.25f);
  ASSERT_EQ(AsmFcvtzu(arg1), MakeUInt128(0x0000000000000000ULL, 0x0000000000000000ULL));
  __uint128_t arg2 = MakeF32x4(6.25f, 6.50f, 6.75f, 7.50f);
  ASSERT_EQ(AsmFcvtzu(arg2), MakeUInt128(0x0000000600000006ULL, 0x0000000700000006ULL));
}

TEST(Arm64InsnTest, AsmConvertF64I64x4TieAway) {
  constexpr auto AsmFcvtas = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcvtas %0.2d, %1.2d");
  __uint128_t arg1 = MakeF64x2(-7.50, -6.75);
  ASSERT_EQ(AsmFcvtas(arg1), MakeUInt128(0xfffffffffffffff8ULL, 0xfffffffffffffff9ULL));
  __uint128_t arg2 = MakeF64x2(-6.50, -6.25);
  ASSERT_EQ(AsmFcvtas(arg2), MakeUInt128(0xfffffffffffffff9ULL, 0xfffffffffffffffaULL));
  __uint128_t arg3 = MakeF64x2(6.25, 6.50);
  ASSERT_EQ(AsmFcvtas(arg3), MakeUInt128(0x0000000000000006ULL, 0x0000000000000007ULL));
  __uint128_t arg4 = MakeF64x2(6.75, 7.50);
  ASSERT_EQ(AsmFcvtas(arg4), MakeUInt128(0x0000000000000007ULL, 0x0000000000000008ULL));
}

TEST(Arm64InsnTest, AsmConvertF64U64x4TieAway) {
  constexpr auto AsmFcvtau = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcvtau %0.2d, %1.2d");
  __uint128_t arg1 = MakeF64x2(-7.50, -6.75);
  ASSERT_EQ(AsmFcvtau(arg1), MakeUInt128(0x0000000000000000ULL, 0x0000000000000000ULL));
  __uint128_t arg2 = MakeF64x2(-6.50, -6.25);
  ASSERT_EQ(AsmFcvtau(arg2), MakeUInt128(0x0000000000000000ULL, 0x0000000000000000ULL));
  __uint128_t arg3 = MakeF64x2(6.25, 6.50);
  ASSERT_EQ(AsmFcvtau(arg3), MakeUInt128(0x0000000000000006ULL, 0x0000000000000007ULL));
  __uint128_t arg4 = MakeF64x2(6.75, 7.50);
  ASSERT_EQ(AsmFcvtau(arg4), MakeUInt128(0x0000000000000007ULL, 0x0000000000000008ULL));
}

TEST(Arm64InsnTest, AsmConvertF64I64x4NegInf) {
  constexpr auto AsmFcvtms = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcvtms %0.2d, %1.2d");
  __uint128_t arg1 = MakeF64x2(-7.50, -6.75);
  ASSERT_EQ(AsmFcvtms(arg1), MakeUInt128(0xfffffffffffffff8ULL, 0xfffffffffffffff9ULL));
  __uint128_t arg2 = MakeF64x2(-6.50, -6.25);
  ASSERT_EQ(AsmFcvtms(arg2), MakeUInt128(0xfffffffffffffff9ULL, 0xfffffffffffffff9ULL));
  __uint128_t arg3 = MakeF64x2(6.25, 6.50);
  ASSERT_EQ(AsmFcvtms(arg3), MakeUInt128(0x0000000000000006ULL, 0x0000000000000006ULL));
  __uint128_t arg4 = MakeF64x2(6.75, 7.50);
  ASSERT_EQ(AsmFcvtms(arg4), MakeUInt128(0x0000000000000006ULL, 0x0000000000000007ULL));
}

TEST(Arm64InsnTest, AsmConvertF64U64x4NegInf) {
  constexpr auto AsmFcvtmu = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcvtmu %0.2d, %1.2d");
  __uint128_t arg1 = MakeF64x2(-7.50, -6.75);
  ASSERT_EQ(AsmFcvtmu(arg1), MakeUInt128(0x0000000000000000ULL, 0x0000000000000000ULL));
  __uint128_t arg2 = MakeF64x2(-6.50, -6.25);
  ASSERT_EQ(AsmFcvtmu(arg2), MakeUInt128(0x0000000000000000ULL, 0x0000000000000000ULL));
  __uint128_t arg3 = MakeF64x2(6.25, 6.50);
  ASSERT_EQ(AsmFcvtmu(arg3), MakeUInt128(0x0000000000000006ULL, 0x0000000000000006ULL));
  __uint128_t arg4 = MakeF64x2(6.75, 7.50);
  ASSERT_EQ(AsmFcvtmu(arg4), MakeUInt128(0x0000000000000006ULL, 0x0000000000000007ULL));
}

TEST(Arm64InsnTest, AsmConvertF64I64x4TieEven) {
  constexpr auto AsmFcvtns = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcvtns %0.2d, %1.2d");
  __uint128_t arg1 = MakeF64x2(-7.50, -6.75);
  ASSERT_EQ(AsmFcvtns(arg1), MakeUInt128(0xfffffffffffffff8ULL, 0xfffffffffffffff9ULL));
  __uint128_t arg2 = MakeF64x2(-6.50, -6.25);
  ASSERT_EQ(AsmFcvtns(arg2), MakeUInt128(0xfffffffffffffffaULL, 0xfffffffffffffffaULL));
  __uint128_t arg3 = MakeF64x2(6.25, 6.50);
  ASSERT_EQ(AsmFcvtns(arg3), MakeUInt128(0x0000000000000006ULL, 0x0000000000000006ULL));
  __uint128_t arg4 = MakeF64x2(6.75, 7.50);
  ASSERT_EQ(AsmFcvtns(arg4), MakeUInt128(0x0000000000000007ULL, 0x0000000000000008ULL));
}

TEST(Arm64InsnTest, AsmConvertF64U64x4TieEven) {
  constexpr auto AsmFcvtnu = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcvtnu %0.2d, %1.2d");
  __uint128_t arg1 = MakeF64x2(-7.50, -6.75);
  ASSERT_EQ(AsmFcvtnu(arg1), MakeUInt128(0x0000000000000000ULL, 0x0000000000000000ULL));
  __uint128_t arg2 = MakeF64x2(-6.50, -6.25);
  ASSERT_EQ(AsmFcvtnu(arg2), MakeUInt128(0x0000000000000000ULL, 0x0000000000000000ULL));
  __uint128_t arg3 = MakeF64x2(6.25, 6.50);
  ASSERT_EQ(AsmFcvtnu(arg3), MakeUInt128(0x0000000000000006ULL, 0x0000000000000006ULL));
  __uint128_t arg4 = MakeF64x2(6.75, 7.50);
  ASSERT_EQ(AsmFcvtnu(arg4), MakeUInt128(0x0000000000000007ULL, 0x0000000000000008ULL));
}

TEST(Arm64InsnTest, AsmConvertF64I64x4PosInf) {
  constexpr auto AsmFcvtps = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcvtps %0.2d, %1.2d");
  __uint128_t arg1 = MakeF64x2(-7.50, -6.75);
  ASSERT_EQ(AsmFcvtps(arg1), MakeUInt128(0xfffffffffffffff9ULL, 0xfffffffffffffffaULL));
  __uint128_t arg2 = MakeF64x2(-6.50, -6.25);
  ASSERT_EQ(AsmFcvtps(arg2), MakeUInt128(0xfffffffffffffffaULL, 0xfffffffffffffffaULL));
  __uint128_t arg3 = MakeF64x2(6.25, 6.50);
  ASSERT_EQ(AsmFcvtps(arg3), MakeUInt128(0x0000000000000007ULL, 0x0000000000000007ULL));
  __uint128_t arg4 = MakeF64x2(6.75, 7.50);
  ASSERT_EQ(AsmFcvtps(arg4), MakeUInt128(0x0000000000000007ULL, 0x0000000000000008ULL));
}

TEST(Arm64InsnTest, AsmConvertF64U64x4PosInf) {
  constexpr auto AsmFcvtpu = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcvtpu %0.2d, %1.2d");
  __uint128_t arg1 = MakeF64x2(-7.50, -6.75);
  ASSERT_EQ(AsmFcvtpu(arg1), MakeUInt128(0x0000000000000000ULL, 0x0000000000000000ULL));
  __uint128_t arg2 = MakeF64x2(-6.50, -6.25);
  ASSERT_EQ(AsmFcvtpu(arg2), MakeUInt128(0x0000000000000000ULL, 0x0000000000000000ULL));
  __uint128_t arg3 = MakeF64x2(6.25, 6.50);
  ASSERT_EQ(AsmFcvtpu(arg3), MakeUInt128(0x0000000000000007ULL, 0x0000000000000007ULL));
  __uint128_t arg4 = MakeF64x2(6.75, 7.50);
  ASSERT_EQ(AsmFcvtpu(arg4), MakeUInt128(0x0000000000000007ULL, 0x0000000000000008ULL));
}

TEST(Arm64InsnTest, AsmConvertF64I64x4Truncate) {
  constexpr auto AsmFcvtzs = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcvtzs %0.2d, %1.2d");
  __uint128_t arg1 = MakeF64x2(-7.50, -6.75);
  ASSERT_EQ(AsmFcvtzs(arg1), MakeUInt128(0xfffffffffffffff9ULL, 0xfffffffffffffffaULL));
  __uint128_t arg2 = MakeF64x2(-6.50, -6.25);
  ASSERT_EQ(AsmFcvtzs(arg2), MakeUInt128(0xfffffffffffffffaULL, 0xfffffffffffffffaULL));
  __uint128_t arg3 = MakeF64x2(6.25, 6.50);
  ASSERT_EQ(AsmFcvtzs(arg3), MakeUInt128(0x0000000000000006ULL, 0x0000000000000006ULL));
  __uint128_t arg4 = MakeF64x2(6.75, 7.50);
  ASSERT_EQ(AsmFcvtzs(arg4), MakeUInt128(0x0000000000000006ULL, 0x0000000000000007ULL));
}

TEST(Arm64InsnTest, AsmConvertF64U64x4Truncate) {
  constexpr auto AsmFcvtzu = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcvtzu %0.2d, %1.2d");
  __uint128_t arg1 = MakeF64x2(-7.50, -6.75);
  ASSERT_EQ(AsmFcvtzu(arg1), MakeUInt128(0x0000000000000000ULL, 0x0000000000000000ULL));
  __uint128_t arg2 = MakeF64x2(-6.50, -6.25);
  ASSERT_EQ(AsmFcvtzu(arg2), MakeUInt128(0x0000000000000000ULL, 0x0000000000000000ULL));
  __uint128_t arg3 = MakeF64x2(6.25, 6.50);
  ASSERT_EQ(AsmFcvtzu(arg3), MakeUInt128(0x0000000000000006ULL, 0x0000000000000006ULL));
  __uint128_t arg4 = MakeF64x2(6.75, 7.50);
  ASSERT_EQ(AsmFcvtzu(arg4), MakeUInt128(0x0000000000000006ULL, 0x0000000000000007ULL));
}

TEST(Arm64InsnTest, AsmConvertX32F32Scalar) {
  constexpr auto AsmConvertX32F32 = ASM_INSN_WRAP_FUNC_W_RES_R_ARG("scvtf %s0, %w1, #7");

  ASSERT_EQ(AsmConvertX32F32(0x610), MakeUInt128(0x41420000ULL, 0U));

  ASSERT_EQ(AsmConvertX32F32(1U << 31), MakeUInt128(0xcb800000ULL, 0U));
}

TEST(Arm64InsnTest, AsmConvertX32F64Scalar) {
  constexpr auto AsmConvertX32F64 = ASM_INSN_WRAP_FUNC_W_RES_R_ARG("scvtf %d0, %w1, #8");

  ASSERT_EQ(AsmConvertX32F64(0x487), MakeUInt128(0x40121c0000000000ULL, 0U));

  ASSERT_EQ(AsmConvertX32F64(1 << 31), MakeUInt128(0xc160000000000000ULL, 0U));
}

TEST(Arm64InsnTest, AsmConvertX32F32) {
  constexpr auto AsmConvertX32F32 = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("scvtf %s0, %s1, #7");

  ASSERT_EQ(AsmConvertX32F32(0x123), MakeUInt128(0x40118000ULL, 0U));

  ASSERT_EQ(AsmConvertX32F32(1U << 31), MakeUInt128(0xcb800000ULL, 0U));
}

TEST(Arm64InsnTest, AsmConvertX32x4F32x4) {
  constexpr auto AsmConvertX32F32 = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("scvtf %0.4s, %1.4s, #11");
  __uint128_t arg = MakeUInt128(0x80000000ffff9852ULL, 0x0000110200001254ULL);
  ASSERT_EQ(AsmConvertX32F32(arg), MakeUInt128(0xc9800000c14f5c00ULL, 0x400810004012a000ULL));
}

TEST(Arm64InsnTest, AsmConvertUX32F32Scalar) {
  constexpr auto AsmConvertUX32F32 = ASM_INSN_WRAP_FUNC_W_RES_R_ARG("ucvtf %s0, %w1, #7");

  ASSERT_EQ(AsmConvertUX32F32(0x857), MakeUInt128(0x41857000ULL, 0U));

  ASSERT_EQ(AsmConvertUX32F32(1U << 31), MakeUInt128(0x4b800000ULL, 0U));

  // Test the default rounding behavior (FPRounding_TIEEVEN).
  ASSERT_EQ(AsmConvertUX32F32(0x80000080), MakeUInt128(0x4b800000ULL, 0U));
  ASSERT_EQ(AsmConvertUX32F32(0x800000c0), MakeUInt128(0x4b800001ULL, 0U));
  ASSERT_EQ(AsmConvertUX32F32(0x80000140), MakeUInt128(0x4b800001ULL, 0U));
  ASSERT_EQ(AsmConvertUX32F32(0x80000180), MakeUInt128(0x4b800002ULL, 0U));
}

TEST(Arm64InsnTest, AsmConvertUX32F64Scalar) {
  constexpr auto AsmConvertUX32F64 = ASM_INSN_WRAP_FUNC_W_RES_R_ARG("ucvtf %d0, %w1, #8");

  ASSERT_EQ(AsmConvertUX32F64(0x361), MakeUInt128(0x400b080000000000ULL, 0U));

  ASSERT_EQ(AsmConvertUX32F64(1U << 31), MakeUInt128(0x4160000000000000ULL, 0U));
}

TEST(Arm64InsnTest, AsmConvertUX32F32) {
  constexpr auto AsmConvertUX32F32 = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("ucvtf %s0, %s1, #7");

  ASSERT_EQ(AsmConvertUX32F32(0x456), MakeUInt128(0x410ac000ULL, 0U));

  ASSERT_EQ(AsmConvertUX32F32(1U << 31), MakeUInt128(0x4b800000ULL, 0U));
}

TEST(Arm64InsnTest, AsmConvertUX32x4F32x4) {
  constexpr auto AsmConvertUX32F32 = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("ucvtf %0.4s, %1.4s, #11");
  __uint128_t arg = MakeUInt128(0x8000000000008023ULL, 0x0000201800001956ULL);
  ASSERT_EQ(AsmConvertUX32F32(arg), MakeUInt128(0x4980000041802300ULL, 0x40806000404ab000ULL));
}

TEST(Arm64InsnTest, AsmConvertX64F32Scalar) {
  constexpr auto AsmConvertX64F32 = ASM_INSN_WRAP_FUNC_W_RES_R_ARG("scvtf %s0, %x1, #10");

  ASSERT_EQ(AsmConvertX64F32(0x2234), MakeUInt128(0x4108d000ULL, 0U));
}

TEST(Arm64InsnTest, AsmConvertX64F64Scalar) {
  constexpr auto AsmConvertX64F64 = ASM_INSN_WRAP_FUNC_W_RES_R_ARG("scvtf %d0, %x1, #10");

  ASSERT_EQ(AsmConvertX64F64(0x1324), MakeUInt128(0x4013240000000000ULL, 0U));
}

TEST(Arm64InsnTest, AsmConvertUX64F32Scalar) {
  constexpr auto AsmConvertUX64F32 = ASM_INSN_WRAP_FUNC_W_RES_R_ARG("ucvtf %s0, %x1, #10");

  ASSERT_EQ(AsmConvertUX64F32(0x5763), MakeUInt128(0x41aec600ULL, 0U));
}

TEST(Arm64InsnTest, AsmConvertUX64F64Scalar) {
  constexpr auto AsmConvertUX64F64 = ASM_INSN_WRAP_FUNC_W_RES_R_ARG("ucvtf %d0, %x1, #10");

  ASSERT_EQ(AsmConvertUX64F64(0x2217), MakeUInt128(0x40210b8000000000ULL, 0U));
}

TEST(Arm64InsnTest, AsmConvertX64F64) {
  constexpr auto AsmConvertX64F64 = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("scvtf %d0, %d1, #12");

  ASSERT_EQ(AsmConvertX64F64(0x723), MakeUInt128(0x3fdc8c0000000000ULL, 0U));

  ASSERT_EQ(AsmConvertX64F64(1ULL << 63), MakeUInt128(0xc320000000000000ULL, 0U));
}

TEST(Arm64InsnTest, AsmConvertUX64F64) {
  constexpr auto AsmConvertUX64F64 = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("ucvtf %d0, %d1, #12");

  ASSERT_EQ(AsmConvertUX64F64(0x416), MakeUInt128(0x3fd0580000000000ULL, 0U));

  ASSERT_EQ(AsmConvertUX64F64(1ULL << 63), MakeUInt128(0x4320000000000000ULL, 0U));
}

TEST(Arm64InsnTest, AsmConvertX64x2F64x2) {
  constexpr auto AsmConvertX64F64 = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("scvtf %0.2d, %1.2d, #12");
  __uint128_t arg = MakeUInt128(1ULL << 63, 0x8086U);
  ASSERT_EQ(AsmConvertX64F64(arg), MakeUInt128(0xc320000000000000ULL, 0x402010c000000000ULL));
}

TEST(Arm64InsnTest, AsmConvertUX64x2F64x2) {
  constexpr auto AsmConvertUX64F64 = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("ucvtf %0.2d, %1.2d, #12");
  __uint128_t arg = MakeUInt128(1ULL << 63, 0x6809U);
  ASSERT_EQ(AsmConvertUX64F64(arg), MakeUInt128(0x4320000000000000ULL, 0x401a024000000000ULL));
}

TEST(Arm64InsnTest, AsmConvertF32X32Scalar) {
  constexpr auto AsmConvertF32X32 = ASM_INSN_WRAP_FUNC_R_RES_W_ARG("fcvtzs %w0, %s1, #16");
  uint32_t arg1 = 0x4091eb85U;  // 4.56 in float
  ASSERT_EQ(AsmConvertF32X32(arg1), MakeUInt128(0x00048f5cU, 0U));

  uint32_t arg2 = 0xc0d80000U;  // -6.75 in float
  ASSERT_EQ(AsmConvertF32X32(arg2), MakeUInt128(0xfff94000U, 0U));

  ASSERT_EQ(AsmConvertF32X32(kDefaultNaN32), MakeUInt128(bit_cast<uint32_t>(0.0f), 0U));
}

TEST(Arm64InsnTest, AsmConvertF32UX32Scalar) {
  constexpr auto AsmConvertF32UX32 = ASM_INSN_WRAP_FUNC_R_RES_W_ARG("fcvtzs %w0, %s1, #16");
  uint32_t arg1 = 0x41223d71U;  // 10.14 in float
  ASSERT_EQ(AsmConvertF32UX32(arg1), MakeUInt128(0x000a23d7U, 0U));

  uint32_t arg2 = 0xc1540000U;  // -13.25 in float
  ASSERT_EQ(AsmConvertF32UX32(arg2), MakeUInt128(0xfff2c000U, 0U));

  ASSERT_EQ(AsmConvertF32UX32(kDefaultNaN32), MakeUInt128(bit_cast<uint32_t>(0.0f), 0U));
}

TEST(Arm64InsnTest, AsmConvertF32UX32With31FractionalBits) {
  constexpr auto AsmConvertF32UX32 = ASM_INSN_WRAP_FUNC_R_RES_W_ARG("fcvtzs %w0, %s1, #31");
  uint32_t arg1 = bit_cast<uint32_t>(0.25f);
  ASSERT_EQ(AsmConvertF32UX32(arg1), MakeUInt128(0x20000000U, 0U));
}

TEST(Arm64InsnTest, AsmConvertF64X32Scalar) {
  constexpr auto AsmConvertF64X32 = ASM_INSN_WRAP_FUNC_R_RES_W_ARG("fcvtzs %w0, %d1, #16");
  uint64_t arg1 = 0x401e8f5c28f5c28fULL;  // 7.46 in double
  ASSERT_EQ(AsmConvertF64X32(arg1), MakeUInt128(0x0007a3d7U, 0U));

  uint64_t arg2 = 0xc040200000000000ULL;  // -32.44 in double
  ASSERT_EQ(AsmConvertF64X32(arg2), MakeUInt128(0xffdfc000U, 0U));
}

TEST(Arm64InsnTest, AsmConvertF32X64Scalar) {
  constexpr auto AsmFcvtzs = ASM_INSN_WRAP_FUNC_R_RES_W_ARG("fcvtzs %x0, %s1, #16");
  uint64_t arg1 = bit_cast<uint32_t>(7.50f);
  ASSERT_EQ(AsmFcvtzs(arg1), MakeUInt128(0x0000000000078000ULL, 0ULL));

  uint64_t arg2 = bit_cast<uint32_t>(-6.50f);
  ASSERT_EQ(AsmFcvtzs(arg2), MakeUInt128(0xfffffffffff98000ULL, 0ULL));
}

TEST(Arm64InsnTest, AsmConvertF32UX64With63FractionalBits) {
  constexpr auto AsmConvertF32UX64 = ASM_INSN_WRAP_FUNC_R_RES_W_ARG("fcvtzs %x0, %s1, #63");
  uint32_t arg1 = bit_cast<uint32_t>(0.25f);
  ASSERT_EQ(AsmConvertF32UX64(arg1), MakeUInt128(0x20000000'00000000ULL, 0U));
}

TEST(Arm64InsnTest, AsmConvertF64X64Scalar) {
  constexpr auto AsmFcvtzs = ASM_INSN_WRAP_FUNC_R_RES_W_ARG("fcvtzs %x0, %d1, #16");
  uint64_t arg1 = bit_cast<uint64_t>(7.50);
  ASSERT_EQ(AsmFcvtzs(arg1), MakeUInt128(0x0000000000078000ULL, 0ULL));

  uint64_t arg2 = bit_cast<uint64_t>(-6.50);
  ASSERT_EQ(AsmFcvtzs(arg2), MakeUInt128(0xfffffffffff98000ULL, 0ULL));
}

TEST(Arm64InsnTest, AsmConvertF32X32x4) {
  constexpr auto AsmFcvtzs = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcvtzs %0.4s, %1.4s, #2");
  __uint128_t res = AsmFcvtzs(MakeF32x4(-5.5f, -0.0f, 0.0f, 6.5f));
  ASSERT_EQ(res, MakeUInt128(0x00000000ffffffeaULL, 0x0000001a00000000ULL));
}

TEST(Arm64InsnTest, AsmConvertF64UX32Scalar) {
  constexpr auto AsmConvertF64UX32 = ASM_INSN_WRAP_FUNC_R_RES_W_ARG("fcvtzu %w0, %d1, #16");
  uint64_t arg1 = 0x4020947ae147ae14ULL;  // 8.29 in double
  ASSERT_EQ(AsmConvertF64UX32(arg1), MakeUInt128(0x00084a3dU, 0U));

  uint64_t arg2 = 0xc023666666666666ULL;  // -9.70 in double
  ASSERT_EQ(AsmConvertF64UX32(arg2), MakeUInt128(0U, 0U));
}

TEST(Arm64InsnTest, AsmConvertF32UX64Scalar) {
  constexpr auto AsmFcvtzu = ASM_INSN_WRAP_FUNC_R_RES_W_ARG("fcvtzu %x0, %s1, #16");
  uint64_t arg1 = bit_cast<uint32_t>(7.50f);
  ASSERT_EQ(AsmFcvtzu(arg1), MakeUInt128(0x0000000000078000ULL, 0ULL));
  uint64_t arg2 = bit_cast<uint32_t>(-6.50f);
  ASSERT_EQ(AsmFcvtzu(arg2), 0ULL);
}

TEST(Arm64InsnTest, AsmConvertF64UX64Scalar) {
  constexpr auto AsmFcvtzu = ASM_INSN_WRAP_FUNC_R_RES_W_ARG("fcvtzu %x0, %d1, #16");
  uint64_t arg1 = bit_cast<uint64_t>(7.50);
  ASSERT_EQ(AsmFcvtzu(arg1), MakeUInt128(0x0000000000078000ULL, 0ULL));

  uint64_t arg2 = bit_cast<uint64_t>(-6.50);
  ASSERT_EQ(AsmFcvtzu(arg2), MakeUInt128(0ULL, 0ULL));
}

TEST(Arm64InsnTest, AsmConvertF32UX32x4) {
  constexpr auto AsmFcvtzs = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcvtzu %0.4s, %1.4s, #2");
  __uint128_t res = AsmFcvtzs(MakeF32x4(-5.5f, -0.0f, 0.0f, 6.5f));
  ASSERT_EQ(res, MakeUInt128(0x0000000000000000ULL, 0x0000001a00000000ULL));
}

TEST(Arm64InsnTest, Fp32ConditionalSelect) {
  uint64_t int_arg1 = 3;
  uint64_t int_arg2 = 7;
  uint64_t fp_arg1 = 0xfedcba9876543210ULL;
  uint64_t fp_arg2 = 0x0123456789abcdefULL;
  __uint128_t res;

  asm("cmp %x1,%x2\n\t"
      "fcsel %s0, %s3, %s4, eq"
      : "=w"(res)
      : "r"(int_arg1), "r"(int_arg2), "w"(fp_arg1), "w"(fp_arg2));
  ASSERT_EQ(res, MakeUInt128(0x89abcdefULL, 0U));

  asm("cmp %x1,%x2\n\t"
      "fcsel %s0, %s3, %s4, ne"
      : "=w"(res)
      : "r"(int_arg1), "r"(int_arg2), "w"(fp_arg1), "w"(fp_arg2));
  ASSERT_EQ(res, MakeUInt128(0x76543210ULL, 0U));
}

TEST(Arm64InsnTest, Fp64ConditionalSelect) {
  uint64_t int_arg1 = 8;
  uint64_t int_arg2 = 3;
  uint64_t fp_arg1 = 0xfedcba9876543210ULL;
  uint64_t fp_arg2 = 0x0123456789abcdefULL;
  __uint128_t res;

  asm("cmp %x1,%x2\n\t"
      "fcsel %d0, %d3, %d4, eq"
      : "=w"(res)
      : "r"(int_arg1), "r"(int_arg2), "w"(fp_arg1), "w"(fp_arg2));
  ASSERT_EQ(res, MakeUInt128(0x0123456789abcdefULL, 0U));

  asm("cmp %x1,%x2\n\t"
      "fcsel %d0, %d3, %d4, ne"
      : "=w"(res)
      : "r"(int_arg1), "r"(int_arg2), "w"(fp_arg1), "w"(fp_arg2));
  ASSERT_EQ(res, MakeUInt128(0xfedcba9876543210ULL, 0U));
}

TEST(Arm64InsnTest, RoundUpFp32) {
  // The lower 32-bit represents 2.7182817 in float.
  uint64_t fp_arg = 0xdeadbeef402df854ULL;
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("frintp %s0, %s1")(fp_arg);
  ASSERT_EQ(res, MakeUInt128(0x40400000ULL, 0U));  // 3.0 in float
}

TEST(Arm64InsnTest, RoundUpFp64) {
  // 2.7182817 in double.
  uint64_t fp_arg = 0x4005BF0A8B145769ULL;
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("frintp %d0, %d1")(fp_arg);
  ASSERT_EQ(res, MakeUInt128(0x4008000000000000ULL, 0U));  // 3.0 in double
}

TEST(Arm64InsnTest, RoundToIntNearestTiesAwayFp64) {
  constexpr auto AsmFrinta = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("frinta %d0, %d1");

  // -7.50 -> -8.00 (ties away from zero as opposted to even)
  ASSERT_EQ(AsmFrinta(0xc01E000000000000ULL), MakeUInt128(0xc020000000000000ULL, 0U));

  // -6.75 -> -7.00
  ASSERT_EQ(AsmFrinta(0xc01B000000000000ULL), MakeUInt128(0xc01c000000000000ULL, 0U));

  // -6.50 -> -7.00 (ties away from zero as opposted to even)
  ASSERT_EQ(AsmFrinta(0xc01A000000000000ULL), MakeUInt128(0xc01c000000000000ULL, 0U));

  // -6.25 -> -6.00
  ASSERT_EQ(AsmFrinta(0xc019000000000000ULL), MakeUInt128(0xc018000000000000ULL, 0U));

  // 6.25 -> 6.00
  ASSERT_EQ(AsmFrinta(0x4019000000000000ULL), MakeUInt128(0x4018000000000000ULL, 0U));

  // 6.50 -> 7.00 (ties away from zero as opposted to even)
  ASSERT_EQ(AsmFrinta(0x401A000000000000ULL), MakeUInt128(0x401c000000000000ULL, 0U));

  // 6.75 -> 7.00
  ASSERT_EQ(AsmFrinta(0x401B000000000000ULL), MakeUInt128(0x401c000000000000ULL, 0U));

  // 7.50 -> 8.00 (ties away from zero as opposted to even)
  ASSERT_EQ(AsmFrinta(0x401E000000000000ULL), MakeUInt128(0x4020000000000000ULL, 0U));

  // -0.49999999999999994 -> -0.0 (should not "tie away" since -0.4999... != -0.5)
  ASSERT_EQ(AsmFrinta(0xBFDFFFFFFFFFFFFF), MakeUInt128(0x8000000000000000U, 0U));

  // A number too large to have fractional precision, should not change upon rounding with tie-away
  ASSERT_EQ(AsmFrinta(bit_cast<uint64_t>(1.0e100)), MakeUInt128(bit_cast<uint64_t>(1.0e100), 0U));
  ASSERT_EQ(AsmFrinta(bit_cast<uint64_t>(-1.0e100)), MakeUInt128(bit_cast<uint64_t>(-1.0e100), 0U));
}

TEST(Arm64InsnTest, RoundToIntNearestTiesAwayFp32) {
  constexpr auto AsmFrinta = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("frinta %s0, %s1");

  // -7.50 -> -8.00 (ties away from zero as opposted to even)
  ASSERT_EQ(AsmFrinta(0xc0f00000U), MakeUInt128(0xc1000000U, 0U));

  // -6.75 -> -7.00
  ASSERT_EQ(AsmFrinta(0xc0d80000U), MakeUInt128(0xc0e00000U, 0U));

  // -6.50 -> -7.00 (ties away from zero as opposted to even)
  ASSERT_EQ(AsmFrinta(0xc0d00000U), MakeUInt128(0xc0e00000U, 0U));

  // -6.25 -> -6.00
  ASSERT_EQ(AsmFrinta(0xc0c80000U), MakeUInt128(0xc0c00000U, 0U));

  // 6.25 -> 6.00
  ASSERT_EQ(AsmFrinta(0x40c80000U), MakeUInt128(0x40c00000U, 0U));

  // 6.50 -> 7.00 (ties away from zero as opposted to even)
  ASSERT_EQ(AsmFrinta(0x40d00000U), MakeUInt128(0x40e00000U, 0U));

  // 6.75 -> 7.00
  ASSERT_EQ(AsmFrinta(0x40d80000U), MakeUInt128(0x40e00000U, 0U));

  // 7.50 -> 8.00 (ties away from zero as opposted to even)
  ASSERT_EQ(AsmFrinta(0x40f00000U), MakeUInt128(0x41000000U, 0U));

  // -0.49999997019767761 -> -0.0 (should not "tie away" since -0.4999... != -0.5)
  ASSERT_EQ(AsmFrinta(0xbeffffff), MakeUInt128(0x80000000U, 0U));
}

TEST(Arm64InsnTest, RoundToIntDownwardFp64) {
  constexpr auto AsmFrintm = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("frintm %d0, %d1");

  // 7.7 -> 7.00
  ASSERT_EQ(AsmFrintm(0x401ecccccccccccdULL), MakeUInt128(0x401c000000000000, 0U));

  // 7.1 -> 7.00
  ASSERT_EQ(AsmFrintm(0x401c666666666666ULL), MakeUInt128(0x401c000000000000, 0U));

  // -7.10 -> -8.00
  ASSERT_EQ(AsmFrintm(0xc01c666666666666ULL), MakeUInt128(0xc020000000000000, 0U));

  // -7.90 -> -8.00
  ASSERT_EQ(AsmFrintm(0xc01f99999999999aULL), MakeUInt128(0xc020000000000000, 0U));

  // 0 -> 0
  ASSERT_EQ(AsmFrintm(0x0000000000000000ULL), MakeUInt128(0x0000000000000000, 0U));

  // -0 -> -0
  ASSERT_EQ(AsmFrintm(0x8000000000000000ULL), MakeUInt128(0x8000000000000000, 0U));
}

TEST(Arm64InsnTest, RoundToIntDownwardFp32) {
  constexpr auto AsmFrintm = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("frintm %s0, %s1");

  // 7.7 -> 7.00
  ASSERT_EQ(AsmFrintm(0x40f66666), 0x40e00000);

  // 7.1 -> 7.00
  ASSERT_EQ(AsmFrintm(0x40e33333), 0x40e00000);

  // -7.10 -> -8.00
  ASSERT_EQ(AsmFrintm(0xc0e33333), 0xc1000000);

  // -7.90 -> -8.00
  ASSERT_EQ(AsmFrintm(0xc0fccccd), 0xc1000000);

  // 0 -> 0
  ASSERT_EQ(AsmFrintm(0x00000000), 0x00000000);

  // -0 -> -0
  ASSERT_EQ(AsmFrintm(0x80000000), 0x80000000);
}

TEST(Arm64InsnTest, RoundToIntNearestFp64) {
  constexpr auto AsmFrintn = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("frintn %d0, %d1");

  // 7.5 -> 8.00 (ties to even)
  ASSERT_EQ(AsmFrintn(0x401e000000000000ULL), MakeUInt128(0x4020000000000000, 0U));

  // 8.5 -> 8.00 (ties to even)
  ASSERT_EQ(AsmFrintn(0x4021000000000000), MakeUInt128(0x4020000000000000, 0U));

  // 7.10 -> 7.00
  ASSERT_EQ(AsmFrintn(0x401c666666666666), MakeUInt128(0x401c000000000000, 0U));

  // 7.90 -> 8.00
  ASSERT_EQ(AsmFrintn(0x401f99999999999a), MakeUInt128(0x4020000000000000, 0U));

  // -7.5 -> -8.00 (ties to even)
  ASSERT_EQ(AsmFrintn(0xc01e000000000000), MakeUInt128(0xc020000000000000, 0U));

  // // -8.5 -> -8.00 (ties to even)
  ASSERT_EQ(AsmFrintn(0xc021000000000000), MakeUInt128(0xc020000000000000, 0U));

  // -7.10 -> -7.00
  ASSERT_EQ(AsmFrintn(0xc01c666666666666), MakeUInt128(0xc01c000000000000, 0U));

  // -7.90 -> -8.00
  ASSERT_EQ(AsmFrintn(0xc01f99999999999a), MakeUInt128(0xc020000000000000, 0U));

  // 0 -> 0
  ASSERT_EQ(AsmFrintn(0x0000000000000000ULL), MakeUInt128(0x0000000000000000, 0U));

  // -0 -> -0
  ASSERT_EQ(AsmFrintn(0x8000000000000000ULL), MakeUInt128(0x8000000000000000, 0U));
}

TEST(Arm64InsnTest, RoundToIntToNearestFp32) {
  constexpr auto AsmFrintn = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("frintn %s0, %s1");

  // 7.5 -> 8.00 (ties to even)
  ASSERT_EQ(AsmFrintn(0x40f00000), 0x41000000);

  // 8.5 -> 8.00 (ties to even)
  ASSERT_EQ(AsmFrintn(0x41080000), 0x41000000);

  // 7.10 -> 7.00
  ASSERT_EQ(AsmFrintn(0x40e33333), 0x40e00000);

  // 7.90 -> 8.00
  ASSERT_EQ(AsmFrintn(0x40fccccd), 0x41000000);

  // -7.5 -> -8.00 (ties to even)
  ASSERT_EQ(AsmFrintn(0xc0f00000), 0xc1000000);

  // -8.5 -> -8.00 (ties to even)
  ASSERT_EQ(AsmFrintn(0xc1080000), 0xc1000000);

  // -7.10 -> -7.00
  ASSERT_EQ(AsmFrintn(0xc0e33333), 0xc0e00000);

  // -7.90 -> -8.00
  ASSERT_EQ(AsmFrintn(0xc0fccccd), 0xc1000000);

  // 0 -> 0
  ASSERT_EQ(AsmFrintn(0x00000000), 0x00000000);

  // -0 -> -0
  ASSERT_EQ(AsmFrintn(0x80000000), 0x80000000);
}

TEST(Arm64InsnTest, RoundToIntTowardZeroFp64) {
  constexpr auto AsmFrintz = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("frintz %d0, %d1");

  // 7.7 -> 7.00
  ASSERT_EQ(AsmFrintz(0x401ecccccccccccdULL), MakeUInt128(0x401c000000000000, 0U));

  // 7.1 -> 7.00
  ASSERT_EQ(AsmFrintz(0x401c666666666666ULL), MakeUInt128(0x401c000000000000, 0U));

  // -7.10 -> -7.00
  ASSERT_EQ(AsmFrintz(0xc01c666666666666ULL), MakeUInt128(0xc01c000000000000, 0U));

  // -7.90 -> -7.00
  ASSERT_EQ(AsmFrintz(0xc01f99999999999aULL), MakeUInt128(0xc01c000000000000, 0U));

  // 0 -> 0
  ASSERT_EQ(AsmFrintz(0x0000000000000000ULL), MakeUInt128(0x0000000000000000, 0U));

  // -0 -> -0
  ASSERT_EQ(AsmFrintz(0x8000000000000000ULL), MakeUInt128(0x8000000000000000, 0U));
}

TEST(Arm64InsnTest, RoundToIntTowardZeroFp32) {
  constexpr auto AsmFrintz = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("frintz %s0, %s1");

  // 7.7 -> 7.00
  ASSERT_EQ(AsmFrintz(0x40f66666), 0x40e00000);

  // 7.1 -> 7.00
  ASSERT_EQ(AsmFrintz(0x40e33333), 0x40e00000);

  // -7.10 -> -7.00
  ASSERT_EQ(AsmFrintz(0xc0e33333), 0xc0e00000);

  // -7.90 -> -7.00
  ASSERT_EQ(AsmFrintz(0xc0fccccd), 0xc0e00000);

  // 0 -> 0
  ASSERT_EQ(AsmFrintz(0x00000000), 0x00000000);

  // -0 -> -0
  ASSERT_EQ(AsmFrintz(0x80000000), 0x80000000);
}

TEST(Arm64InsnTest, AsmConvertF32x4TieAway) {
  constexpr auto AsmFcvta = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("frinta %0.4s, %1.4s");
  __uint128_t arg1 = MakeF32x4(-7.50f, -6.75f, -6.50f, -6.25f);
  ASSERT_EQ(AsmFcvta(arg1), MakeF32x4(-8.00f, -7.00f, -7.00f, -6.00f));
  __uint128_t arg2 = MakeF32x4(6.25f, 6.50f, 6.75f, 7.50f);
  ASSERT_EQ(AsmFcvta(arg2), MakeF32x4(6.00f, 7.00f, 7.00f, 8.00f));
}

TEST(Arm64InsnTest, AsmConvertF32x4NegInf) {
  constexpr auto AsmFcvtm = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("frintm %0.4s, %1.4s");
  __uint128_t arg1 = MakeF32x4(-7.50f, -6.75f, -6.50f, -6.25f);
  ASSERT_EQ(AsmFcvtm(arg1), MakeF32x4(-8.00f, -7.00f, -7.00f, -7.00f));
  __uint128_t arg2 = MakeF32x4(6.25f, 6.50f, 6.75f, 7.50f);
  ASSERT_EQ(AsmFcvtm(arg2), MakeF32x4(6.00f, 6.00f, 6.00f, 7.00f));
}

TEST(Arm64InsnTest, AsmConvertF32x4TieEven) {
  constexpr auto AsmFcvtn = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("frintn %0.4s, %1.4s");
  __uint128_t arg1 = MakeF32x4(-7.50f, -6.75f, -6.50f, -6.25f);
  ASSERT_EQ(AsmFcvtn(arg1), MakeF32x4(-8.00f, -7.00f, -6.00f, -6.00f));
  __uint128_t arg2 = MakeF32x4(6.25f, 6.50f, 6.75f, 7.50f);
  ASSERT_EQ(AsmFcvtn(arg2), MakeF32x4(6.00f, 6.00f, 7.00f, 8.00f));
}

TEST(Arm64InsnTest, AsmConvertF32x4PosInf) {
  constexpr auto AsmFcvtp = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("frintp %0.4s, %1.4s");
  __uint128_t arg1 = MakeF32x4(-7.50f, -6.75f, -6.50f, -6.25f);
  ASSERT_EQ(AsmFcvtp(arg1), MakeF32x4(-7.00f, -6.00f, -6.00f, -6.00f));
  __uint128_t arg2 = MakeF32x4(6.25f, 6.50f, 6.75f, 7.50f);
  ASSERT_EQ(AsmFcvtp(arg2), MakeF32x4(7.00f, 7.00f, 7.00f, 8.00f));
}

TEST(Arm64InsnTest, AsmConvertF32x4Truncate) {
  constexpr auto AsmFcvtz = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("frintz %0.4s, %1.4s");
  __uint128_t arg1 = MakeF32x4(-7.50f, -6.75f, -6.50f, -6.25f);
  ASSERT_EQ(AsmFcvtz(arg1), MakeF32x4(-7.00f, -6.00f, -6.00f, -6.00f));
  __uint128_t arg2 = MakeF32x4(6.25f, 6.50f, 6.75f, 7.50f);
  ASSERT_EQ(AsmFcvtz(arg2), MakeF32x4(6.00f, 6.00f, 6.00f, 7.00f));
}

TEST(Arm64InsnTest, AsmConvertF64x4TieAway) {
  constexpr auto AsmFcvta = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("frinta %0.2d, %1.2d");
  __uint128_t arg1 = MakeF64x2(-7.50, -6.75);
  ASSERT_EQ(AsmFcvta(arg1), MakeF64x2(-8.00, -7.00));
  __uint128_t arg2 = MakeF64x2(-6.50, -6.25);
  ASSERT_EQ(AsmFcvta(arg2), MakeF64x2(-7.00, -6.00));
  __uint128_t arg3 = MakeF64x2(6.25, 6.50);
  ASSERT_EQ(AsmFcvta(arg3), MakeF64x2(6.00, 7.00));
  __uint128_t arg4 = MakeF64x2(6.75, 7.50);
  ASSERT_EQ(AsmFcvta(arg4), MakeF64x2(7.00, 8.00));
}

TEST(Arm64InsnTest, AsmConvertF64x4NegInf) {
  constexpr auto AsmFcvtm = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("frintm %0.2d, %1.2d");
  __uint128_t arg1 = MakeF64x2(-7.50, -6.75);
  ASSERT_EQ(AsmFcvtm(arg1), MakeF64x2(-8.00, -7.00));
  __uint128_t arg2 = MakeF64x2(-6.50, -6.25);
  ASSERT_EQ(AsmFcvtm(arg2), MakeF64x2(-7.00, -7.00));
  __uint128_t arg3 = MakeF64x2(6.25, 6.50);
  ASSERT_EQ(AsmFcvtm(arg3), MakeF64x2(6.00, 6.00));
  __uint128_t arg4 = MakeF64x2(6.75, 7.50);
  ASSERT_EQ(AsmFcvtm(arg4), MakeF64x2(6.00, 7.00));
}

TEST(Arm64InsnTest, AsmConvertF64x4TieEven) {
  constexpr auto AsmFcvtn = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("frintn %0.2d, %1.2d");
  __uint128_t arg1 = MakeF64x2(-7.50, -6.75);
  ASSERT_EQ(AsmFcvtn(arg1), MakeF64x2(-8.00, -7.00));
  __uint128_t arg2 = MakeF64x2(-6.50, -6.25);
  ASSERT_EQ(AsmFcvtn(arg2), MakeF64x2(-6.00, -6.00));
  __uint128_t arg3 = MakeF64x2(6.25, 6.50);
  ASSERT_EQ(AsmFcvtn(arg3), MakeF64x2(6.00, 6.00));
  __uint128_t arg4 = MakeF64x2(6.75, 7.50);
  ASSERT_EQ(AsmFcvtn(arg4), MakeF64x2(7.00, 8.00));
}

TEST(Arm64InsnTest, AsmConvertF64x4PosInf) {
  constexpr auto AsmFcvtp = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("frintp %0.2d, %1.2d");
  __uint128_t arg1 = MakeF64x2(-7.50, -6.75);
  ASSERT_EQ(AsmFcvtp(arg1), MakeF64x2(-7.00, -6.00));
  __uint128_t arg2 = MakeF64x2(-6.50, -6.25);
  ASSERT_EQ(AsmFcvtp(arg2), MakeF64x2(-6.00, -6.00));
  __uint128_t arg3 = MakeF64x2(6.25, 6.50);
  ASSERT_EQ(AsmFcvtp(arg3), MakeF64x2(7.00, 7.00));
  __uint128_t arg4 = MakeF64x2(6.75, 7.50);
  ASSERT_EQ(AsmFcvtp(arg4), MakeF64x2(7.00, 8.00));
}

TEST(Arm64InsnTest, AsmConvertF64x4Truncate) {
  constexpr auto AsmFcvtz = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("frintz %0.2d, %1.2d");
  __uint128_t arg1 = MakeF64x2(-7.50, -6.75);
  ASSERT_EQ(AsmFcvtz(arg1), MakeF64x2(-7.00, -6.00));
  __uint128_t arg2 = MakeF64x2(-6.50, -6.25);
  ASSERT_EQ(AsmFcvtz(arg2), MakeF64x2(-6.00, -6.00));
  __uint128_t arg3 = MakeF64x2(6.25, 6.50);
  ASSERT_EQ(AsmFcvtz(arg3), MakeF64x2(6.00, 6.00));
  __uint128_t arg4 = MakeF64x2(6.75, 7.50);
  ASSERT_EQ(AsmFcvtz(arg4), MakeF64x2(6.00, 7.00));
}

TEST(Arm64InsnTest, AsmRoundCurrentModeF32) {
  constexpr auto AsmFrinti = ASM_INSN_WRAP_FUNC_W_RES_WC_ARG("frinti %s0, %s1");
  ASSERT_EQ(AsmFrinti(bit_cast<uint32_t>(-7.50f), kFpcrRModeTieEven), bit_cast<uint32_t>(-8.00f));
  ASSERT_EQ(AsmFrinti(bit_cast<uint32_t>(-6.75f), kFpcrRModeTieEven), bit_cast<uint32_t>(-7.00f));
  ASSERT_EQ(AsmFrinti(bit_cast<uint32_t>(-6.50f), kFpcrRModeTieEven), bit_cast<uint32_t>(-6.00f));
  ASSERT_EQ(AsmFrinti(bit_cast<uint32_t>(-6.25f), kFpcrRModeTieEven), bit_cast<uint32_t>(-6.00f));
  ASSERT_EQ(AsmFrinti(bit_cast<uint32_t>(6.25f), kFpcrRModeTieEven), bit_cast<uint32_t>(6.00f));
  ASSERT_EQ(AsmFrinti(bit_cast<uint32_t>(6.50f), kFpcrRModeTieEven), bit_cast<uint32_t>(6.00f));
  ASSERT_EQ(AsmFrinti(bit_cast<uint32_t>(6.75f), kFpcrRModeTieEven), bit_cast<uint32_t>(7.00f));
  ASSERT_EQ(AsmFrinti(bit_cast<uint32_t>(7.50f), kFpcrRModeTieEven), bit_cast<uint32_t>(8.00f));
  ASSERT_EQ(AsmFrinti(bit_cast<uint32_t>(-7.50f), kFpcrRModeNegInf), bit_cast<uint32_t>(-8.00f));
  ASSERT_EQ(AsmFrinti(bit_cast<uint32_t>(-6.75f), kFpcrRModeNegInf), bit_cast<uint32_t>(-7.00f));
  ASSERT_EQ(AsmFrinti(bit_cast<uint32_t>(-6.50f), kFpcrRModeNegInf), bit_cast<uint32_t>(-7.00f));
  ASSERT_EQ(AsmFrinti(bit_cast<uint32_t>(-6.25f), kFpcrRModeNegInf), bit_cast<uint32_t>(-7.00f));
  ASSERT_EQ(AsmFrinti(bit_cast<uint32_t>(6.25f), kFpcrRModeNegInf), bit_cast<uint32_t>(6.00f));
  ASSERT_EQ(AsmFrinti(bit_cast<uint32_t>(6.50f), kFpcrRModeNegInf), bit_cast<uint32_t>(6.00f));
  ASSERT_EQ(AsmFrinti(bit_cast<uint32_t>(6.75f), kFpcrRModeNegInf), bit_cast<uint32_t>(6.00f));
  ASSERT_EQ(AsmFrinti(bit_cast<uint32_t>(7.50f), kFpcrRModeNegInf), bit_cast<uint32_t>(7.00f));
  ASSERT_EQ(AsmFrinti(bit_cast<uint32_t>(-7.50f), kFpcrRModePosInf), bit_cast<uint32_t>(-7.00f));
  ASSERT_EQ(AsmFrinti(bit_cast<uint32_t>(-6.75f), kFpcrRModePosInf), bit_cast<uint32_t>(-6.00f));
  ASSERT_EQ(AsmFrinti(bit_cast<uint32_t>(-6.50f), kFpcrRModePosInf), bit_cast<uint32_t>(-6.00f));
  ASSERT_EQ(AsmFrinti(bit_cast<uint32_t>(-6.25f), kFpcrRModePosInf), bit_cast<uint32_t>(-6.00f));
  ASSERT_EQ(AsmFrinti(bit_cast<uint32_t>(6.25f), kFpcrRModePosInf), bit_cast<uint32_t>(7.00f));
  ASSERT_EQ(AsmFrinti(bit_cast<uint32_t>(6.50f), kFpcrRModePosInf), bit_cast<uint32_t>(7.00f));
  ASSERT_EQ(AsmFrinti(bit_cast<uint32_t>(6.75f), kFpcrRModePosInf), bit_cast<uint32_t>(7.00f));
  ASSERT_EQ(AsmFrinti(bit_cast<uint32_t>(7.50f), kFpcrRModePosInf), bit_cast<uint32_t>(8.00f));
  ASSERT_EQ(AsmFrinti(bit_cast<uint32_t>(-7.50f), kFpcrRModeZero), bit_cast<uint32_t>(-7.00f));
  ASSERT_EQ(AsmFrinti(bit_cast<uint32_t>(-6.75f), kFpcrRModeZero), bit_cast<uint32_t>(-6.00f));
  ASSERT_EQ(AsmFrinti(bit_cast<uint32_t>(-6.50f), kFpcrRModeZero), bit_cast<uint32_t>(-6.00f));
  ASSERT_EQ(AsmFrinti(bit_cast<uint32_t>(-6.25f), kFpcrRModeZero), bit_cast<uint32_t>(-6.00f));
  ASSERT_EQ(AsmFrinti(bit_cast<uint32_t>(6.25f), kFpcrRModeZero), bit_cast<uint32_t>(6.00f));
  ASSERT_EQ(AsmFrinti(bit_cast<uint32_t>(6.50f), kFpcrRModeZero), bit_cast<uint32_t>(6.00f));
  ASSERT_EQ(AsmFrinti(bit_cast<uint32_t>(6.75f), kFpcrRModeZero), bit_cast<uint32_t>(6.00f));
  ASSERT_EQ(AsmFrinti(bit_cast<uint32_t>(7.50f), kFpcrRModeZero), bit_cast<uint32_t>(7.00f));
}

TEST(Arm64InsnTest, AsmRoundCurrentModeF64) {
  constexpr auto AsmFrinti = ASM_INSN_WRAP_FUNC_W_RES_WC_ARG("frinti %d0, %d1");
  ASSERT_EQ(AsmFrinti(bit_cast<uint64_t>(-7.50), kFpcrRModeTieEven), bit_cast<uint64_t>(-8.00));
  ASSERT_EQ(AsmFrinti(bit_cast<uint64_t>(-6.75), kFpcrRModeTieEven), bit_cast<uint64_t>(-7.00));
  ASSERT_EQ(AsmFrinti(bit_cast<uint64_t>(-6.50), kFpcrRModeTieEven), bit_cast<uint64_t>(-6.00));
  ASSERT_EQ(AsmFrinti(bit_cast<uint64_t>(-6.25), kFpcrRModeTieEven), bit_cast<uint64_t>(-6.00));
  ASSERT_EQ(AsmFrinti(bit_cast<uint64_t>(6.25), kFpcrRModeTieEven), bit_cast<uint64_t>(6.00));
  ASSERT_EQ(AsmFrinti(bit_cast<uint64_t>(6.50), kFpcrRModeTieEven), bit_cast<uint64_t>(6.00));
  ASSERT_EQ(AsmFrinti(bit_cast<uint64_t>(6.75), kFpcrRModeTieEven), bit_cast<uint64_t>(7.00));
  ASSERT_EQ(AsmFrinti(bit_cast<uint64_t>(7.50), kFpcrRModeTieEven), bit_cast<uint64_t>(8.00));
  ASSERT_EQ(AsmFrinti(bit_cast<uint64_t>(-7.50), kFpcrRModeNegInf), bit_cast<uint64_t>(-8.00));
  ASSERT_EQ(AsmFrinti(bit_cast<uint64_t>(-6.75), kFpcrRModeNegInf), bit_cast<uint64_t>(-7.00));
  ASSERT_EQ(AsmFrinti(bit_cast<uint64_t>(-6.50), kFpcrRModeNegInf), bit_cast<uint64_t>(-7.00));
  ASSERT_EQ(AsmFrinti(bit_cast<uint64_t>(-6.25), kFpcrRModeNegInf), bit_cast<uint64_t>(-7.00));
  ASSERT_EQ(AsmFrinti(bit_cast<uint64_t>(6.25), kFpcrRModeNegInf), bit_cast<uint64_t>(6.00));
  ASSERT_EQ(AsmFrinti(bit_cast<uint64_t>(6.50), kFpcrRModeNegInf), bit_cast<uint64_t>(6.00));
  ASSERT_EQ(AsmFrinti(bit_cast<uint64_t>(6.75), kFpcrRModeNegInf), bit_cast<uint64_t>(6.00));
  ASSERT_EQ(AsmFrinti(bit_cast<uint64_t>(7.50), kFpcrRModeNegInf), bit_cast<uint64_t>(7.00));
  ASSERT_EQ(AsmFrinti(bit_cast<uint64_t>(-7.50), kFpcrRModePosInf), bit_cast<uint64_t>(-7.00));
  ASSERT_EQ(AsmFrinti(bit_cast<uint64_t>(-6.75), kFpcrRModePosInf), bit_cast<uint64_t>(-6.00));
  ASSERT_EQ(AsmFrinti(bit_cast<uint64_t>(-6.50), kFpcrRModePosInf), bit_cast<uint64_t>(-6.00));
  ASSERT_EQ(AsmFrinti(bit_cast<uint64_t>(-6.25), kFpcrRModePosInf), bit_cast<uint64_t>(-6.00));
  ASSERT_EQ(AsmFrinti(bit_cast<uint64_t>(6.25), kFpcrRModePosInf), bit_cast<uint64_t>(7.00));
  ASSERT_EQ(AsmFrinti(bit_cast<uint64_t>(6.50), kFpcrRModePosInf), bit_cast<uint64_t>(7.00));
  ASSERT_EQ(AsmFrinti(bit_cast<uint64_t>(6.75), kFpcrRModePosInf), bit_cast<uint64_t>(7.00));
  ASSERT_EQ(AsmFrinti(bit_cast<uint64_t>(7.50), kFpcrRModePosInf), bit_cast<uint64_t>(8.00));
  ASSERT_EQ(AsmFrinti(bit_cast<uint64_t>(-7.50), kFpcrRModeZero), bit_cast<uint64_t>(-7.00));
  ASSERT_EQ(AsmFrinti(bit_cast<uint64_t>(-6.75), kFpcrRModeZero), bit_cast<uint64_t>(-6.00));
  ASSERT_EQ(AsmFrinti(bit_cast<uint64_t>(-6.50), kFpcrRModeZero), bit_cast<uint64_t>(-6.00));
  ASSERT_EQ(AsmFrinti(bit_cast<uint64_t>(-6.25), kFpcrRModeZero), bit_cast<uint64_t>(-6.00));
  ASSERT_EQ(AsmFrinti(bit_cast<uint64_t>(6.25), kFpcrRModeZero), bit_cast<uint64_t>(6.00));
  ASSERT_EQ(AsmFrinti(bit_cast<uint64_t>(6.50), kFpcrRModeZero), bit_cast<uint64_t>(6.00));
  ASSERT_EQ(AsmFrinti(bit_cast<uint64_t>(6.75), kFpcrRModeZero), bit_cast<uint64_t>(6.00));
  ASSERT_EQ(AsmFrinti(bit_cast<uint64_t>(7.50), kFpcrRModeZero), bit_cast<uint64_t>(7.00));
}

TEST(Arm64InsnTest, AsmRoundCurrentModeF32x4) {
  constexpr auto AsmFrinti = ASM_INSN_WRAP_FUNC_W_RES_WC_ARG("frinti %0.4s, %1.4s");
  __uint128_t arg1 = MakeF32x4(-7.50f, -6.75f, -6.50f, -6.25f);
  ASSERT_EQ(AsmFrinti(arg1, kFpcrRModeTieEven), MakeF32x4(-8.00f, -7.00f, -6.00f, -6.00f));
  __uint128_t arg2 = MakeF32x4(6.25f, 6.50f, 6.75f, 7.50f);
  ASSERT_EQ(AsmFrinti(arg2, kFpcrRModeTieEven), MakeF32x4(6.00f, 6.00f, 7.00f, 8.00f));
  __uint128_t arg3 = MakeF32x4(-7.50f, -6.75f, -6.50f, -6.25f);
  ASSERT_EQ(AsmFrinti(arg3, kFpcrRModeNegInf), MakeF32x4(-8.00f, -7.00f, -7.00f, -7.00f));
  __uint128_t arg4 = MakeF32x4(6.25f, 6.50f, 6.75f, 7.50f);
  ASSERT_EQ(AsmFrinti(arg4, kFpcrRModeNegInf), MakeF32x4(6.00f, 6.00f, 6.00f, 7.00f));
  __uint128_t arg5 = MakeF32x4(-7.50f, -6.75f, -6.50f, -6.25f);
  ASSERT_EQ(AsmFrinti(arg5, kFpcrRModePosInf), MakeF32x4(-7.00f, -6.00f, -6.00f, -6.00f));
  __uint128_t arg6 = MakeF32x4(6.25f, 6.50f, 6.75f, 7.50f);
  ASSERT_EQ(AsmFrinti(arg6, kFpcrRModePosInf), MakeF32x4(7.00f, 7.00f, 7.00f, 8.00f));
  __uint128_t arg7 = MakeF32x4(-7.50f, -6.75f, -6.50f, -6.25f);
  ASSERT_EQ(AsmFrinti(arg7, kFpcrRModeZero), MakeF32x4(-7.00f, -6.00f, -6.00f, -6.00f));
  __uint128_t arg8 = MakeF32x4(6.25f, 6.50f, 6.75f, 7.50f);
  ASSERT_EQ(AsmFrinti(arg8, kFpcrRModeZero), MakeF32x4(6.00f, 6.00f, 6.00f, 7.00f));
}

TEST(Arm64InsnTest, AsmRoundCurrentModeF64x2) {
  constexpr auto AsmFrinti = ASM_INSN_WRAP_FUNC_W_RES_WC_ARG("frinti %0.2d, %1.2d");
  __uint128_t arg1 = MakeF64x2(-7.50, -6.75);
  ASSERT_EQ(AsmFrinti(arg1, kFpcrRModeTieEven), MakeF64x2(-8.00, -7.00));
  __uint128_t arg2 = MakeF64x2(-6.50, -6.25);
  ASSERT_EQ(AsmFrinti(arg2, kFpcrRModeTieEven), MakeF64x2(-6.00, -6.00));
  __uint128_t arg3 = MakeF64x2(6.25, 6.50);
  ASSERT_EQ(AsmFrinti(arg3, kFpcrRModeTieEven), MakeF64x2(6.00, 6.00));
  __uint128_t arg4 = MakeF64x2(6.75, 7.50);
  ASSERT_EQ(AsmFrinti(arg4, kFpcrRModeTieEven), MakeF64x2(7.00, 8.00));
  __uint128_t arg5 = MakeF64x2(-7.50, -6.75);
  ASSERT_EQ(AsmFrinti(arg5, kFpcrRModeNegInf), MakeF64x2(-8.00, -7.00));
  __uint128_t arg6 = MakeF64x2(-6.50, -6.25);
  ASSERT_EQ(AsmFrinti(arg6, kFpcrRModeNegInf), MakeF64x2(-7.00, -7.00));
  __uint128_t arg7 = MakeF64x2(6.25, 6.50);
  ASSERT_EQ(AsmFrinti(arg7, kFpcrRModeNegInf), MakeF64x2(6.00, 6.00));
  __uint128_t arg8 = MakeF64x2(6.75, 7.50);
  ASSERT_EQ(AsmFrinti(arg8, kFpcrRModeNegInf), MakeF64x2(6.00, 7.00));
  __uint128_t arg9 = MakeF64x2(-7.50, -6.75);
  ASSERT_EQ(AsmFrinti(arg9, kFpcrRModePosInf), MakeF64x2(-7.00, -6.00));
  __uint128_t arg10 = MakeF64x2(-6.50, -6.25);
  ASSERT_EQ(AsmFrinti(arg10, kFpcrRModePosInf), MakeF64x2(-6.00, -6.00));
  __uint128_t arg11 = MakeF64x2(6.25, 6.50);
  ASSERT_EQ(AsmFrinti(arg11, kFpcrRModePosInf), MakeF64x2(7.00, 7.00));
  __uint128_t arg12 = MakeF64x2(6.75, 7.50);
  ASSERT_EQ(AsmFrinti(arg12, kFpcrRModePosInf), MakeF64x2(7.00, 8.00));
  __uint128_t arg13 = MakeF64x2(-7.50, -6.75);
  ASSERT_EQ(AsmFrinti(arg13, kFpcrRModeZero), MakeF64x2(-7.00, -6.00));
  __uint128_t arg14 = MakeF64x2(-6.50, -6.25);
  ASSERT_EQ(AsmFrinti(arg14, kFpcrRModeZero), MakeF64x2(-6.00, -6.00));
  __uint128_t arg15 = MakeF64x2(6.25, 6.50);
  ASSERT_EQ(AsmFrinti(arg15, kFpcrRModeZero), MakeF64x2(6.00, 6.00));
  __uint128_t arg16 = MakeF64x2(6.75, 7.50);
  ASSERT_EQ(AsmFrinti(arg16, kFpcrRModeZero), MakeF64x2(6.00, 7.00));
}

TEST(Arm64InsnTest, AsmRoundExactF32) {
  constexpr auto AsmFrintx = ASM_INSN_WRAP_FUNC_W_RES_WC_ARG("frintx %s0, %s1");
  ASSERT_EQ(AsmFrintx(bit_cast<uint32_t>(-7.50f), kFpcrRModeTieEven), bit_cast<uint32_t>(-8.00f));
  ASSERT_EQ(AsmFrintx(bit_cast<uint32_t>(-6.75f), kFpcrRModeTieEven), bit_cast<uint32_t>(-7.00f));
  ASSERT_EQ(AsmFrintx(bit_cast<uint32_t>(-6.50f), kFpcrRModeTieEven), bit_cast<uint32_t>(-6.00f));
  ASSERT_EQ(AsmFrintx(bit_cast<uint32_t>(-6.25f), kFpcrRModeTieEven), bit_cast<uint32_t>(-6.00f));
  ASSERT_EQ(AsmFrintx(bit_cast<uint32_t>(6.25f), kFpcrRModeTieEven), bit_cast<uint32_t>(6.00f));
  ASSERT_EQ(AsmFrintx(bit_cast<uint32_t>(6.50f), kFpcrRModeTieEven), bit_cast<uint32_t>(6.00f));
  ASSERT_EQ(AsmFrintx(bit_cast<uint32_t>(6.75f), kFpcrRModeTieEven), bit_cast<uint32_t>(7.00f));
  ASSERT_EQ(AsmFrintx(bit_cast<uint32_t>(7.50f), kFpcrRModeTieEven), bit_cast<uint32_t>(8.00f));
  ASSERT_EQ(AsmFrintx(bit_cast<uint32_t>(-7.50f), kFpcrRModeNegInf), bit_cast<uint32_t>(-8.00f));
  ASSERT_EQ(AsmFrintx(bit_cast<uint32_t>(-6.75f), kFpcrRModeNegInf), bit_cast<uint32_t>(-7.00f));
  ASSERT_EQ(AsmFrintx(bit_cast<uint32_t>(-6.50f), kFpcrRModeNegInf), bit_cast<uint32_t>(-7.00f));
  ASSERT_EQ(AsmFrintx(bit_cast<uint32_t>(-6.25f), kFpcrRModeNegInf), bit_cast<uint32_t>(-7.00f));
  ASSERT_EQ(AsmFrintx(bit_cast<uint32_t>(6.25f), kFpcrRModeNegInf), bit_cast<uint32_t>(6.00f));
  ASSERT_EQ(AsmFrintx(bit_cast<uint32_t>(6.50f), kFpcrRModeNegInf), bit_cast<uint32_t>(6.00f));
  ASSERT_EQ(AsmFrintx(bit_cast<uint32_t>(6.75f), kFpcrRModeNegInf), bit_cast<uint32_t>(6.00f));
  ASSERT_EQ(AsmFrintx(bit_cast<uint32_t>(7.50f), kFpcrRModeNegInf), bit_cast<uint32_t>(7.00f));
  ASSERT_EQ(AsmFrintx(bit_cast<uint32_t>(-7.50f), kFpcrRModePosInf), bit_cast<uint32_t>(-7.00f));
  ASSERT_EQ(AsmFrintx(bit_cast<uint32_t>(-6.75f), kFpcrRModePosInf), bit_cast<uint32_t>(-6.00f));
  ASSERT_EQ(AsmFrintx(bit_cast<uint32_t>(-6.50f), kFpcrRModePosInf), bit_cast<uint32_t>(-6.00f));
  ASSERT_EQ(AsmFrintx(bit_cast<uint32_t>(-6.25f), kFpcrRModePosInf), bit_cast<uint32_t>(-6.00f));
  ASSERT_EQ(AsmFrintx(bit_cast<uint32_t>(6.25f), kFpcrRModePosInf), bit_cast<uint32_t>(7.00f));
  ASSERT_EQ(AsmFrintx(bit_cast<uint32_t>(6.50f), kFpcrRModePosInf), bit_cast<uint32_t>(7.00f));
  ASSERT_EQ(AsmFrintx(bit_cast<uint32_t>(6.75f), kFpcrRModePosInf), bit_cast<uint32_t>(7.00f));
  ASSERT_EQ(AsmFrintx(bit_cast<uint32_t>(7.50f), kFpcrRModePosInf), bit_cast<uint32_t>(8.00f));
  ASSERT_EQ(AsmFrintx(bit_cast<uint32_t>(-7.50f), kFpcrRModeZero), bit_cast<uint32_t>(-7.00f));
  ASSERT_EQ(AsmFrintx(bit_cast<uint32_t>(-6.75f), kFpcrRModeZero), bit_cast<uint32_t>(-6.00f));
  ASSERT_EQ(AsmFrintx(bit_cast<uint32_t>(-6.50f), kFpcrRModeZero), bit_cast<uint32_t>(-6.00f));
  ASSERT_EQ(AsmFrintx(bit_cast<uint32_t>(-6.25f), kFpcrRModeZero), bit_cast<uint32_t>(-6.00f));
  ASSERT_EQ(AsmFrintx(bit_cast<uint32_t>(6.25f), kFpcrRModeZero), bit_cast<uint32_t>(6.00f));
  ASSERT_EQ(AsmFrintx(bit_cast<uint32_t>(6.50f), kFpcrRModeZero), bit_cast<uint32_t>(6.00f));
  ASSERT_EQ(AsmFrintx(bit_cast<uint32_t>(6.75f), kFpcrRModeZero), bit_cast<uint32_t>(6.00f));
  ASSERT_EQ(AsmFrintx(bit_cast<uint32_t>(7.50f), kFpcrRModeZero), bit_cast<uint32_t>(7.00f));
}

TEST(Arm64InsnTest, AsmRoundExactF64) {
  constexpr auto AsmFrintx = ASM_INSN_WRAP_FUNC_W_RES_WC_ARG("frintx %d0, %d1");
  ASSERT_EQ(AsmFrintx(bit_cast<uint64_t>(-7.50), kFpcrRModeTieEven), bit_cast<uint64_t>(-8.00));
  ASSERT_EQ(AsmFrintx(bit_cast<uint64_t>(-6.75), kFpcrRModeTieEven), bit_cast<uint64_t>(-7.00));
  ASSERT_EQ(AsmFrintx(bit_cast<uint64_t>(-6.50), kFpcrRModeTieEven), bit_cast<uint64_t>(-6.00));
  ASSERT_EQ(AsmFrintx(bit_cast<uint64_t>(-6.25), kFpcrRModeTieEven), bit_cast<uint64_t>(-6.00));
  ASSERT_EQ(AsmFrintx(bit_cast<uint64_t>(6.25), kFpcrRModeTieEven), bit_cast<uint64_t>(6.00));
  ASSERT_EQ(AsmFrintx(bit_cast<uint64_t>(6.50), kFpcrRModeTieEven), bit_cast<uint64_t>(6.00));
  ASSERT_EQ(AsmFrintx(bit_cast<uint64_t>(6.75), kFpcrRModeTieEven), bit_cast<uint64_t>(7.00));
  ASSERT_EQ(AsmFrintx(bit_cast<uint64_t>(7.50), kFpcrRModeTieEven), bit_cast<uint64_t>(8.00));
  ASSERT_EQ(AsmFrintx(bit_cast<uint64_t>(-7.50), kFpcrRModeNegInf), bit_cast<uint64_t>(-8.00));
  ASSERT_EQ(AsmFrintx(bit_cast<uint64_t>(-6.75), kFpcrRModeNegInf), bit_cast<uint64_t>(-7.00));
  ASSERT_EQ(AsmFrintx(bit_cast<uint64_t>(-6.50), kFpcrRModeNegInf), bit_cast<uint64_t>(-7.00));
  ASSERT_EQ(AsmFrintx(bit_cast<uint64_t>(-6.25), kFpcrRModeNegInf), bit_cast<uint64_t>(-7.00));
  ASSERT_EQ(AsmFrintx(bit_cast<uint64_t>(6.25), kFpcrRModeNegInf), bit_cast<uint64_t>(6.00));
  ASSERT_EQ(AsmFrintx(bit_cast<uint64_t>(6.50), kFpcrRModeNegInf), bit_cast<uint64_t>(6.00));
  ASSERT_EQ(AsmFrintx(bit_cast<uint64_t>(6.75), kFpcrRModeNegInf), bit_cast<uint64_t>(6.00));
  ASSERT_EQ(AsmFrintx(bit_cast<uint64_t>(7.50), kFpcrRModeNegInf), bit_cast<uint64_t>(7.00));
  ASSERT_EQ(AsmFrintx(bit_cast<uint64_t>(-7.50), kFpcrRModePosInf), bit_cast<uint64_t>(-7.00));
  ASSERT_EQ(AsmFrintx(bit_cast<uint64_t>(-6.75), kFpcrRModePosInf), bit_cast<uint64_t>(-6.00));
  ASSERT_EQ(AsmFrintx(bit_cast<uint64_t>(-6.50), kFpcrRModePosInf), bit_cast<uint64_t>(-6.00));
  ASSERT_EQ(AsmFrintx(bit_cast<uint64_t>(-6.25), kFpcrRModePosInf), bit_cast<uint64_t>(-6.00));
  ASSERT_EQ(AsmFrintx(bit_cast<uint64_t>(6.25), kFpcrRModePosInf), bit_cast<uint64_t>(7.00));
  ASSERT_EQ(AsmFrintx(bit_cast<uint64_t>(6.50), kFpcrRModePosInf), bit_cast<uint64_t>(7.00));
  ASSERT_EQ(AsmFrintx(bit_cast<uint64_t>(6.75), kFpcrRModePosInf), bit_cast<uint64_t>(7.00));
  ASSERT_EQ(AsmFrintx(bit_cast<uint64_t>(7.50), kFpcrRModePosInf), bit_cast<uint64_t>(8.00));
  ASSERT_EQ(AsmFrintx(bit_cast<uint64_t>(-7.50), kFpcrRModeZero), bit_cast<uint64_t>(-7.00));
  ASSERT_EQ(AsmFrintx(bit_cast<uint64_t>(-6.75), kFpcrRModeZero), bit_cast<uint64_t>(-6.00));
  ASSERT_EQ(AsmFrintx(bit_cast<uint64_t>(-6.50), kFpcrRModeZero), bit_cast<uint64_t>(-6.00));
  ASSERT_EQ(AsmFrintx(bit_cast<uint64_t>(-6.25), kFpcrRModeZero), bit_cast<uint64_t>(-6.00));
  ASSERT_EQ(AsmFrintx(bit_cast<uint64_t>(6.25), kFpcrRModeZero), bit_cast<uint64_t>(6.00));
  ASSERT_EQ(AsmFrintx(bit_cast<uint64_t>(6.50), kFpcrRModeZero), bit_cast<uint64_t>(6.00));
  ASSERT_EQ(AsmFrintx(bit_cast<uint64_t>(6.75), kFpcrRModeZero), bit_cast<uint64_t>(6.00));
  ASSERT_EQ(AsmFrintx(bit_cast<uint64_t>(7.50), kFpcrRModeZero), bit_cast<uint64_t>(7.00));
}

TEST(Arm64InsnTest, AsmRoundExactF32x4) {
  constexpr auto AsmFrintx = ASM_INSN_WRAP_FUNC_W_RES_WC_ARG("frintx %0.4s, %1.4s");
  __uint128_t arg1 = MakeF32x4(-7.50f, -6.75f, -6.50f, -6.25f);
  ASSERT_EQ(AsmFrintx(arg1, kFpcrRModeTieEven), MakeF32x4(-8.00f, -7.00f, -6.00f, -6.00f));
  __uint128_t arg2 = MakeF32x4(6.25f, 6.50f, 6.75f, 7.50f);
  ASSERT_EQ(AsmFrintx(arg2, kFpcrRModeTieEven), MakeF32x4(6.00f, 6.00f, 7.00f, 8.00f));
  __uint128_t arg3 = MakeF32x4(-7.50f, -6.75f, -6.50f, -6.25f);
  ASSERT_EQ(AsmFrintx(arg3, kFpcrRModeNegInf), MakeF32x4(-8.00f, -7.00f, -7.00f, -7.00f));
  __uint128_t arg4 = MakeF32x4(6.25f, 6.50f, 6.75f, 7.50f);
  ASSERT_EQ(AsmFrintx(arg4, kFpcrRModeNegInf), MakeF32x4(6.00f, 6.00f, 6.00f, 7.00f));
  __uint128_t arg5 = MakeF32x4(-7.50f, -6.75f, -6.50f, -6.25f);
  ASSERT_EQ(AsmFrintx(arg5, kFpcrRModePosInf), MakeF32x4(-7.00f, -6.00f, -6.00f, -6.00f));
  __uint128_t arg6 = MakeF32x4(6.25f, 6.50f, 6.75f, 7.50f);
  ASSERT_EQ(AsmFrintx(arg6, kFpcrRModePosInf), MakeF32x4(7.00f, 7.00f, 7.00f, 8.00f));
  __uint128_t arg7 = MakeF32x4(-7.50f, -6.75f, -6.50f, -6.25f);
  ASSERT_EQ(AsmFrintx(arg7, kFpcrRModeZero), MakeF32x4(-7.00f, -6.00f, -6.00f, -6.00f));
  __uint128_t arg8 = MakeF32x4(6.25f, 6.50f, 6.75f, 7.50f);
  ASSERT_EQ(AsmFrintx(arg8, kFpcrRModeZero), MakeF32x4(6.00f, 6.00f, 6.00f, 7.00f));
}

TEST(Arm64InsnTest, AsmRoundExactF64x2) {
  constexpr auto AsmFrintx = ASM_INSN_WRAP_FUNC_W_RES_WC_ARG("frintx %0.2d, %1.2d");
  __uint128_t arg1 = MakeF64x2(-7.50, -6.75);
  ASSERT_EQ(AsmFrintx(arg1, kFpcrRModeTieEven), MakeF64x2(-8.00, -7.00));
  __uint128_t arg2 = MakeF64x2(-6.50, -6.25);
  ASSERT_EQ(AsmFrintx(arg2, kFpcrRModeTieEven), MakeF64x2(-6.00, -6.00));
  __uint128_t arg3 = MakeF64x2(6.25, 6.50);
  ASSERT_EQ(AsmFrintx(arg3, kFpcrRModeTieEven), MakeF64x2(6.00, 6.00));
  __uint128_t arg4 = MakeF64x2(6.75, 7.50);
  ASSERT_EQ(AsmFrintx(arg4, kFpcrRModeTieEven), MakeF64x2(7.00, 8.00));
  __uint128_t arg5 = MakeF64x2(-7.50, -6.75);
  ASSERT_EQ(AsmFrintx(arg5, kFpcrRModeNegInf), MakeF64x2(-8.00, -7.00));
  __uint128_t arg6 = MakeF64x2(-6.50, -6.25);
  ASSERT_EQ(AsmFrintx(arg6, kFpcrRModeNegInf), MakeF64x2(-7.00, -7.00));
  __uint128_t arg7 = MakeF64x2(6.25, 6.50);
  ASSERT_EQ(AsmFrintx(arg7, kFpcrRModeNegInf), MakeF64x2(6.00, 6.00));
  __uint128_t arg8 = MakeF64x2(6.75, 7.50);
  ASSERT_EQ(AsmFrintx(arg8, kFpcrRModeNegInf), MakeF64x2(6.00, 7.00));
  __uint128_t arg9 = MakeF64x2(-7.50, -6.75);
  ASSERT_EQ(AsmFrintx(arg9, kFpcrRModePosInf), MakeF64x2(-7.00, -6.00));
  __uint128_t arg10 = MakeF64x2(-6.50, -6.25);
  ASSERT_EQ(AsmFrintx(arg10, kFpcrRModePosInf), MakeF64x2(-6.00, -6.00));
  __uint128_t arg11 = MakeF64x2(6.25, 6.50);
  ASSERT_EQ(AsmFrintx(arg11, kFpcrRModePosInf), MakeF64x2(7.00, 7.00));
  __uint128_t arg12 = MakeF64x2(6.75, 7.50);
  ASSERT_EQ(AsmFrintx(arg12, kFpcrRModePosInf), MakeF64x2(7.00, 8.00));
  __uint128_t arg13 = MakeF64x2(-7.50, -6.75);
  ASSERT_EQ(AsmFrintx(arg13, kFpcrRModeZero), MakeF64x2(-7.00, -6.00));
  __uint128_t arg14 = MakeF64x2(-6.50, -6.25);
  ASSERT_EQ(AsmFrintx(arg14, kFpcrRModeZero), MakeF64x2(-6.00, -6.00));
  __uint128_t arg15 = MakeF64x2(6.25, 6.50);
  ASSERT_EQ(AsmFrintx(arg15, kFpcrRModeZero), MakeF64x2(6.00, 6.00));
  __uint128_t arg16 = MakeF64x2(6.75, 7.50);
  ASSERT_EQ(AsmFrintx(arg16, kFpcrRModeZero), MakeF64x2(6.00, 7.00));
}

uint64_t Fp32Compare(uint64_t arg1, uint64_t arg2) {
  uint64_t res;
  asm("fcmp %s1, %s2\n\t"
      "mrs %x0, nzcv"
      : "=r"(res)
      : "w"(arg1), "w"(arg2));
  return res;
}

uint64_t Fp64Compare(uint64_t arg1, uint64_t arg2) {
  uint64_t res;
  asm("fcmp %d1, %d2\n\t"
      "mrs %x0, nzcv"
      : "=r"(res)
      : "w"(arg1), "w"(arg2));
  return res;
}

constexpr uint64_t MakeNZCV(uint64_t nzcv) {
  return nzcv << 28;
}

TEST(Arm64InsnTest, Fp32Compare) {
  // NaN and 1.83
  ASSERT_EQ(Fp32Compare(0x7fc00000ULL, 0x3fea3d71ULL), MakeNZCV(0b0011));

  // 6.31 == 6.31
  ASSERT_EQ(Fp32Compare(0x40c9eb85ULL, 0x40c9eb85ULL), MakeNZCV(0b0110));

  // 1.23 < 2.34
  ASSERT_EQ(Fp32Compare(0x3f9d70a4ULL, 0x4015c28fULL), MakeNZCV(0b1000));

  // 5.25 > 2.94
  ASSERT_EQ(Fp32Compare(0x40a80000ULL, 0x403c28f6ULL), MakeNZCV(0b0010));
}

TEST(Arm64InsnTest, Fp32CompareZero) {
  constexpr auto Fp32CompareZero = ASM_INSN_WRAP_FUNC_R_RES_W_ARG(
      "fcmp %s1, #0.0\n\t"
      "mrs %x0, nzcv");

  // NaN and 0.00
  ASSERT_EQ(Fp32CompareZero(0x7fa00000ULL), MakeNZCV(0b0011));

  // 0.00 == 0.00
  ASSERT_EQ(Fp32CompareZero(0x00000000ULL), MakeNZCV(0b0110));

  // -2.67 < 0.00
  ASSERT_EQ(Fp32CompareZero(0xc02ae148ULL), MakeNZCV(0b1000));

  // 1.56 > 0.00
  ASSERT_EQ(Fp32CompareZero(0x3fc7ae14ULL), MakeNZCV(0b0010));
}

TEST(Arm64InsnTest, Fp64Compare) {
  // NaN and 1.19
  ASSERT_EQ(Fp64Compare(0x7ff8000000000000ULL, 0x3ff30a3d70a3d70aULL), MakeNZCV(0b0011));

  // 8.42 == 8.42
  ASSERT_EQ(Fp64Compare(0x4020d70a3d70a3d7ULL, 0x4020d70a3d70a3d7ULL), MakeNZCV(0b0110));

  // 0.50 < 1.00
  ASSERT_EQ(Fp64Compare(0x3fe0000000000000ULL, 0x3ff0000000000000ULL), MakeNZCV(0b1000));

  // 7.38 > 1.54
  ASSERT_EQ(Fp64Compare(0x401d851eb851eb85ULL, 0x3ff8a3d70a3d70a4ULL), MakeNZCV(0b0010));
}

TEST(Arm64InsnTest, Fp64CompareZero) {
  constexpr auto Fp64CompareZero = ASM_INSN_WRAP_FUNC_R_RES_W_ARG(
      "fcmp %d1, #0.0\n\t"
      "mrs %x0, nzcv");

  // NaN and 0.00
  ASSERT_EQ(Fp64CompareZero(0x7ff4000000000000ULL), MakeNZCV(0b0011));

  // 0.00 == 0.00
  ASSERT_EQ(Fp64CompareZero(0x0000000000000000ULL), MakeNZCV(0b0110));

  // -7.23 < 0.00
  ASSERT_EQ(Fp64CompareZero(0xc01ceb851eb851ecULL), MakeNZCV(0b1000));

  // 5.39 > 0.00
  ASSERT_EQ(Fp64CompareZero(0x40158f5c28f5c28fULL), MakeNZCV(0b0010));
}

uint64_t Fp32CompareIfEqualOrSetAllFlags(float arg1, float arg2, uint64_t nzcv) {
  asm("msr nzcv, %x0\n\t"
      "fccmp %s2, %s3, #15, eq\n\t"
      "mrs %x0, nzcv\n\t"
      : "=r"(nzcv)
      : "0"(nzcv), "w"(arg1), "w"(arg2));
  return nzcv;
}

TEST(Arm64InsnTest, Fp32ConditionalCompare) {
  // Comparison is performed.
  constexpr uint64_t kEqual = MakeNZCV(0b0100);
  constexpr float kNan = std::numeric_limits<float>::quiet_NaN();
  ASSERT_EQ(Fp32CompareIfEqualOrSetAllFlags(1.0f, 1.0f, kEqual), MakeNZCV(0b0110));
  ASSERT_EQ(Fp32CompareIfEqualOrSetAllFlags(1.0f, 2.0f, kEqual), MakeNZCV(0b1000));
  ASSERT_EQ(Fp32CompareIfEqualOrSetAllFlags(2.0f, 1.0f, kEqual), MakeNZCV(0b0010));
  ASSERT_EQ(Fp32CompareIfEqualOrSetAllFlags(kNan, 1.0f, kEqual), MakeNZCV(0b0011));
  // Comparison is not performed; alt-nzcv is returned.
  constexpr uint64_t kNotEqual = MakeNZCV(0b0000);
  ASSERT_EQ(Fp32CompareIfEqualOrSetAllFlags(1.0f, 1.0f, kNotEqual), MakeNZCV(0b1111));
  ASSERT_EQ(Fp32CompareIfEqualOrSetAllFlags(1.0f, 2.0f, kNotEqual), MakeNZCV(0b1111));
  ASSERT_EQ(Fp32CompareIfEqualOrSetAllFlags(2.0f, 1.0f, kNotEqual), MakeNZCV(0b1111));
  ASSERT_EQ(Fp32CompareIfEqualOrSetAllFlags(kNan, 1.0f, kNotEqual), MakeNZCV(0b1111));
}

uint64_t Fp64CompareIfEqualOrSetAllFlags(double arg1, double arg2, uint64_t nzcv) {
  asm("msr nzcv, %x0\n\t"
      "fccmp %d2, %d3, #15, eq\n\t"
      "mrs %x0, nzcv\n\t"
      : "=r"(nzcv)
      : "0"(nzcv), "w"(arg1), "w"(arg2));
  return nzcv;
}

TEST(Arm64InsnTest, Fp64ConditionalCompare) {
  // Comparison is performed.
  constexpr uint64_t kEqual = MakeNZCV(0b0100);
  constexpr double kNan = std::numeric_limits<double>::quiet_NaN();
  ASSERT_EQ(Fp64CompareIfEqualOrSetAllFlags(1.0, 1.0, kEqual), MakeNZCV(0b0110));
  ASSERT_EQ(Fp64CompareIfEqualOrSetAllFlags(1.0, 2.0, kEqual), MakeNZCV(0b1000));
  ASSERT_EQ(Fp64CompareIfEqualOrSetAllFlags(2.0, 1.0, kEqual), MakeNZCV(0b0010));
  ASSERT_EQ(Fp64CompareIfEqualOrSetAllFlags(kNan, 1.0, kEqual), MakeNZCV(0b0011));
  // Comparison is not performed; alt-nzcv is returned.
  constexpr uint64_t kNotEqual = MakeNZCV(0b0000);
  ASSERT_EQ(Fp64CompareIfEqualOrSetAllFlags(1.0, 1.0, kNotEqual), MakeNZCV(0b1111));
  ASSERT_EQ(Fp64CompareIfEqualOrSetAllFlags(1.0, 2.0, kNotEqual), MakeNZCV(0b1111));
  ASSERT_EQ(Fp64CompareIfEqualOrSetAllFlags(2.0, 1.0, kNotEqual), MakeNZCV(0b1111));
  ASSERT_EQ(Fp64CompareIfEqualOrSetAllFlags(kNan, 1.0f, kNotEqual), MakeNZCV(0b1111));
}

TEST(Arm64InsnTest, ConvertFp32ToFp64) {
  uint64_t arg = 0x40cd70a4ULL;  // 6.42 in float
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcvt %d0, %s1")(arg);
  ASSERT_EQ(res, MakeUInt128(0x4019ae1480000000ULL, 0U));
}

TEST(Arm64InsnTest, ConvertFp64ToFp32) {
  uint64_t arg = 0x401a0a3d70a3d70aULL;  // 6.51 in double
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcvt %s0, %d1")(arg);
  ASSERT_EQ(res, MakeUInt128(0x40d051ecULL, 0U));
}

TEST(Arm64InsnTest, ConvertFp32ToFp16) {
  constexpr auto AsmFcvt = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcvt %h0, %s1");
  EXPECT_EQ(AsmFcvt(bit_cast<uint32_t>(2.5f)), MakeUInt128(0x4100U, 0U));
  EXPECT_EQ(AsmFcvt(bit_cast<uint32_t>(4.5f)), MakeUInt128(0x4480U, 0U));
  EXPECT_EQ(AsmFcvt(bit_cast<uint32_t>(8.5f)), MakeUInt128(0x4840U, 0U));
  EXPECT_EQ(AsmFcvt(bit_cast<uint32_t>(16.5f)), MakeUInt128(0x4c20U, 0U));
}

TEST(Arm64InsnTest, ConvertFp16ToFp32) {
  uint64_t arg = 0x4100U;
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcvt %s0, %h1")(arg);
  ASSERT_EQ(res, bit_cast<uint32_t>(2.5f));
}

TEST(Arm64InsnTest, ConvertFp64ToFp16) {
  uint64_t arg = bit_cast<uint64_t>(2.5);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcvt %h0, %d1")(arg);
  ASSERT_EQ(res, MakeUInt128(0x4100U, 0U));
}

TEST(Arm64InsnTest, ConvertFp16ToFp64) {
  uint64_t arg = 0x4100U;
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcvt %d0, %h1")(arg);
  ASSERT_EQ(res, bit_cast<uint64_t>(2.5));
}

TEST(Arm64InsnTest, ConvertToNarrowF64F32x2) {
  constexpr auto AsmFcvtn = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcvtn %0.2s, %1.2d");
  __uint128_t res = AsmFcvtn(MakeF64x2(2.0, 3.0));
  ASSERT_EQ(res, MakeF32x4(2.0f, 3.0f, 0.0f, 0.0f));
}

TEST(Arm64InsnTest, ConvertToNarrowF64F32x2Upper) {
  constexpr auto AsmFcvtn = ASM_INSN_WRAP_FUNC_W_RES_W0_ARG("fcvtn2 %0.4s, %1.2d");
  __uint128_t arg1 = MakeF64x2(2.0, 3.0);
  __uint128_t arg2 = MakeF32x4(4.0f, 5.0f, 6.0f, 7.0f);
  ASSERT_EQ(AsmFcvtn(arg1, arg2), MakeF32x4(4.0f, 5.0f, 2.0f, 3.0f));
}

TEST(Arm64InsnTest, ConvertToNarrowRoundToOddF64F32) {
  constexpr auto AsmFcvtxn = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcvtxn %s0, %d1");
  __uint128_t res = AsmFcvtxn(bit_cast<uint64_t>(2.0));
  ASSERT_EQ(res, bit_cast<uint32_t>(2.0f));
}

TEST(Arm64InsnTest, ConvertToNarrowRoundToOddF64F32x2) {
  constexpr auto AsmFcvtxn = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcvtxn %0.2s, %1.2d");
  __uint128_t res = AsmFcvtxn(MakeF64x2(2.0, 3.0));
  ASSERT_EQ(res, MakeF32x4(2.0f, 3.0f, 0.0f, 0.0f));
}

TEST(Arm64InsnTest, ConvertToNarrowRoundToOddF64F32x2Upper) {
  constexpr auto AsmFcvtxn = ASM_INSN_WRAP_FUNC_W_RES_W0_ARG("fcvtxn2 %0.4s, %1.2d");
  __uint128_t arg1 = MakeF64x2(2.0, 3.0);
  __uint128_t arg2 = MakeF32x4(4.0f, 5.0f, 6.0f, 7.0f);
  ASSERT_EQ(AsmFcvtxn(arg1, arg2), MakeF32x4(4.0f, 5.0f, 2.0f, 3.0f));
}

TEST(Arm64InsnTest, ConvertToWiderF32F64x2Lower) {
  constexpr auto AsmFcvtl = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcvtl %0.2d, %1.2s");
  __uint128_t arg = MakeF32x4(2.0f, 3.0f, 4.0f, 5.0f);
  ASSERT_EQ(AsmFcvtl(arg), MakeF64x2(2.0, 3.0));
}

TEST(Arm64InsnTest, ConvertToWiderF32F64x2Upper) {
  constexpr auto AsmFcvtl2 = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcvtl2 %0.2d, %1.4s");
  __uint128_t arg = MakeF32x4(2.0f, 3.0f, 4.0f, 5.0f);
  ASSERT_EQ(AsmFcvtl2(arg), MakeF64x2(4.0, 5.0));
}

TEST(Arm64InsnTest, ConvertToWiderF16F32x4Lower) {
  constexpr auto AsmFcvtl = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcvtl %0.4s, %1.4h");
  // 4xF16 in the lower half.
  __uint128_t arg = MakeUInt128(0x4c20'4840'4480'4100ULL, 0);
  ASSERT_EQ(AsmFcvtl(arg), MakeF32x4(2.5f, 4.5f, 8.5f, 16.5f));
}

TEST(Arm64InsnTest, ConvertToWiderF16F32x4Upper) {
  constexpr auto AsmFcvtl = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcvtl2 %0.4s, %1.8h");
  // 4xF16 in the upper half.
  __uint128_t arg = MakeUInt128(0, 0x4c20'4840'4480'4100ULL);
  ASSERT_EQ(AsmFcvtl(arg), MakeF32x4(2.5f, 4.5f, 8.5f, 16.5f));
}

TEST(Arm64InsnTest, ConvertToNarrowF32F16x4Lower) {
  constexpr auto AsmFcvtn = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcvtn %0.4h, %1.4s");
  __uint128_t arg = MakeF32x4(2.5f, 4.5f, 8.5f, 16.5f);
  // 4xF16 in the lower half.
  ASSERT_EQ(AsmFcvtn(arg), MakeUInt128(0x4c20'4840'4480'4100ULL, 0));
}

TEST(Arm64InsnTest, ConvertToNarrowF32F16x4Upper) {
  constexpr auto AsmFcvtn = ASM_INSN_WRAP_FUNC_W_RES_W0_ARG("fcvtn2 %0.8h, %1.4s");
  __uint128_t arg1 = MakeF32x4(2.5f, 4.5f, 8.5f, 16.5f);
  __uint128_t arg2 = MakeF32x4(3.0f, 5.0f, 7.0f, 11.0f);
  // 4xF16 in the upper half, lower half preserved.
  ASSERT_EQ(AsmFcvtn(arg1, arg2), MakeUInt128(uint64_t(arg2), 0x4c20'4840'4480'4100ULL));
}

TEST(Arm64InsnTest, AbsF32) {
  uint32_t arg = 0xc1273333U;  // -10.45 in float
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fabs %s0, %s1")(arg);
  ASSERT_EQ(res, MakeUInt128(0x41273333ULL, 0U));  // 10.45 in float
}

TEST(Arm64InsnTest, AbsF64) {
  uint64_t arg = 0xc03de8f5c28f5c29ULL;  // -29.91 in double
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fabs %d0, %d1")(arg);
  ASSERT_EQ(res, MakeUInt128(0x403de8f5c28f5c29ULL, 0U));  // 29.91 in double
}

TEST(Arm64InsnTest, AbsF32x4) {
  constexpr auto AsmFabs = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fabs %0.4s, %1.4s");
  __uint128_t arg = MakeF32x4(-0.0f, 0.0f, 3.0f, -7.0f);
  ASSERT_EQ(AsmFabs(arg), MakeF32x4(0.0f, 0.0f, 3.0f, 7.0f));
}

TEST(Arm64InsnTest, AbsF64x2) {
  constexpr auto AsmFabs = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fabs %0.2d, %1.2d");
  __uint128_t arg = MakeF64x2(-0.0, 3.0);
  ASSERT_EQ(AsmFabs(arg), MakeF64x2(0.0, 3.0));
}

TEST(Arm64InsnTest, AbdF32) {
  uint32_t arg1 = 0x4181851fU;  // 16.19 in float
  uint32_t arg2 = 0x41211eb8U;  // 10.06 in float
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fabd %s0, %s1, %s2")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x40c3d70cULL, 0U));  // 6.12 in float
}

TEST(Arm64InsnTest, AbdF64) {
  constexpr auto AsmFabd = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fabd %d0, %d1, %d2");
  uint64_t arg1 = 0x403828f5c28f5c29U;  // 24.16 in double
  uint64_t arg2 = 0x4027d70a3d70a3d7U;  // 11.92 in double
  __uint128_t res = AsmFabd(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x40287ae147ae147bULL, 0U));  // 12.24 in double
}

TEST(Arm64InsnTest, AbdF32x4) {
  constexpr auto AsmFabd = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fabd %0.4s, %1.4s, %2.4s");
  __uint128_t arg1 = MakeF32x4(1.0f, 5.0f, -3.0f, -2.0f);
  __uint128_t arg2 = MakeF32x4(-1.0f, 2.0f, -5.0f, 3.0f);
  __uint128_t res = AsmFabd(arg1, arg2);
  ASSERT_EQ(res, MakeF32x4(2.0f, 3.0f, 2.0f, 5.0f));
}

TEST(Arm64InsnTest, AbdF64x2) {
  constexpr auto AsmFabd = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fabd %0.2d, %1.2d, %2.2d");
  __uint128_t arg1 = MakeF64x2(5.0, -2.0);
  __uint128_t arg2 = MakeF64x2(4.0, 3.0);
  __uint128_t res = AsmFabd(arg1, arg2);
  ASSERT_EQ(res, MakeF64x2(1.0, 5.0));
}

TEST(Arm64InsnTest, NegF32) {
  uint32_t arg = 0x40eeb852U;  // 7.46 in float
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fneg %s0, %s1")(arg);
  ASSERT_EQ(res, MakeUInt128(0xc0eeb852ULL, 0U));  // -7.46 in float
}

TEST(Arm64InsnTest, NegF64) {
  uint64_t arg = 0x4054b28f5c28f5c3ULL;  // 82.79 in double
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fneg %d0, %d1")(arg);
  ASSERT_EQ(res, MakeUInt128(0xc054b28f5c28f5c3ULL, 0U));  // -82.79 in double
}

TEST(Arm64InsnTest, NegF32x4) {
  constexpr auto AsmFneg = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fneg %0.4s, %1.4s");
  __uint128_t arg = MakeF32x4(-0.0f, 0.0f, 1.0f, -3.0f);
  ASSERT_EQ(AsmFneg(arg), MakeF32x4(0.0f, -0.0f, -1.0f, 3.0f));
}

TEST(Arm64InsnTest, NegF64x2) {
  constexpr auto AsmFneg = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fneg %0.2d, %1.2d");
  __uint128_t arg = MakeF64x2(0.0, 3.0);
  ASSERT_EQ(AsmFneg(arg), MakeF64x2(-0.0, -3.0));
}

TEST(Arm64InsnTest, SqrtF32) {
  uint32_t arg = 0x41f3cac1U;  // 30.474 in float
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fsqrt %s0, %s1")(arg);
  ASSERT_EQ(res, MakeUInt128(0x40b0a683ULL, 0U));  // 5.5203261 in float
}

TEST(Arm64InsnTest, SqrtF64) {
  uint64_t arg = 0x403d466666666666ULL;  // 29.275 in double
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fsqrt %d0, %d1")(arg);
  ASSERT_EQ(res, MakeUInt128(0x4015a47e3392efb8ULL, 0U));  // 5.41... in double
}

TEST(Arm64InsnTest, SqrtF32x4) {
  constexpr auto AsmSqrt = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fsqrt %0.4s, %1.4s");
  __uint128_t arg = MakeF32x4(0.0f, 1.0f, 4.0f, 9.0f);
  ASSERT_EQ(AsmSqrt(arg), MakeF32x4(0.0f, 1.0f, 2.0f, 3.0f));
}

TEST(Arm64InsnTest, RecipEstimateF32) {
  constexpr auto AsmFrecpe = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("frecpe %s0, %s1");
  ASSERT_EQ(AsmFrecpe(bit_cast<uint32_t>(0.25f)), bit_cast<uint32_t>(3.9921875f));
  ASSERT_EQ(AsmFrecpe(bit_cast<uint32_t>(0.50f)), bit_cast<uint32_t>(1.99609375f));
  ASSERT_EQ(AsmFrecpe(bit_cast<uint32_t>(2.00f)), bit_cast<uint32_t>(0.4990234375f));
  ASSERT_EQ(AsmFrecpe(bit_cast<uint32_t>(4.00f)), bit_cast<uint32_t>(0.24951171875f));
}

TEST(Arm64InsnTest, RecipEstimateF32x4) {
  constexpr auto AsmFrecpe = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("frecpe %0.4s, %1.4s");
  __uint128_t res = AsmFrecpe(MakeF32x4(0.25f, 0.50f, 2.00f, 4.00f));
  ASSERT_EQ(res, MakeF32x4(3.9921875f, 1.99609375f, 0.4990234375f, 0.24951171875f));
}

TEST(Arm64InsnTest, RecipStepF32) {
  constexpr auto AsmFrecps = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("frecps %s0, %s1, %s2");
  __uint128_t res1 = AsmFrecps(bit_cast<uint32_t>(1.50f), bit_cast<uint32_t>(0.50f));
  ASSERT_EQ(res1, bit_cast<uint32_t>(1.25f));
  __uint128_t res2 = AsmFrecps(bit_cast<uint32_t>(2.00f), bit_cast<uint32_t>(0.50f));
  ASSERT_EQ(res2, bit_cast<uint32_t>(1.00f));
  __uint128_t res3 = AsmFrecps(bit_cast<uint32_t>(3.00f), bit_cast<uint32_t>(0.25f));
  ASSERT_EQ(res3, bit_cast<uint32_t>(1.25f));
  __uint128_t res4 = AsmFrecps(bit_cast<uint32_t>(3.00f), bit_cast<uint32_t>(0.50f));
  ASSERT_EQ(res4, bit_cast<uint32_t>(0.50f));
}

TEST(Arm64InsnTest, RecipStepF64) {
  constexpr auto AsmFrecps = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("frecps %d0, %d1, %d2");
  __uint128_t res1 = AsmFrecps(bit_cast<uint64_t>(1.50), bit_cast<uint64_t>(0.50));
  ASSERT_EQ(res1, bit_cast<uint64_t>(1.25));
  __uint128_t res2 = AsmFrecps(bit_cast<uint64_t>(2.00), bit_cast<uint64_t>(0.50));
  ASSERT_EQ(res2, bit_cast<uint64_t>(1.00));
  __uint128_t res3 = AsmFrecps(bit_cast<uint64_t>(3.00), bit_cast<uint64_t>(0.25));
  ASSERT_EQ(res3, bit_cast<uint64_t>(1.25));
  __uint128_t res4 = AsmFrecps(bit_cast<uint64_t>(3.00), bit_cast<uint64_t>(0.50));
  ASSERT_EQ(res4, bit_cast<uint64_t>(0.50));
}

TEST(Arm64InsnTest, RecipStepF32x4) {
  constexpr auto AsmFrecps = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("frecps %0.4s, %1.4s, %2.4s");
  __uint128_t arg1 = MakeF32x4(1.50f, 2.00f, 3.00f, 3.00f);
  __uint128_t arg2 = MakeF32x4(0.50f, 0.50f, 0.25f, 0.50f);
  __uint128_t res = AsmFrecps(arg1, arg2);
  ASSERT_EQ(res, MakeF32x4(1.25f, 1.00f, 1.25f, 0.50f));
}

TEST(Arm64InsnTest, RecipStepF64x2) {
  constexpr auto AsmFrecps = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("frecps %0.2d, %1.2d, %2.2d");
  __uint128_t arg1 = MakeF64x2(1.50, 2.00);
  __uint128_t arg2 = MakeF64x2(0.50, 0.50);
  ASSERT_EQ(AsmFrecps(arg1, arg2), MakeF64x2(1.25, 1.00));
  __uint128_t arg3 = MakeF64x2(3.00, 3.00);
  __uint128_t arg4 = MakeF64x2(0.25, 0.50);
  ASSERT_EQ(AsmFrecps(arg3, arg4), MakeF64x2(1.25, 0.50));
}

TEST(Arm64InsnTest, RecipSqrtEstimateF32) {
  constexpr auto AsmFrsqrte = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("frsqrte %s0, %s1");
  ASSERT_EQ(AsmFrsqrte(bit_cast<uint32_t>(2.0f)), bit_cast<uint32_t>(0.705078125f));
  ASSERT_EQ(AsmFrsqrte(bit_cast<uint32_t>(3.0f)), bit_cast<uint32_t>(0.576171875f));
  ASSERT_EQ(AsmFrsqrte(bit_cast<uint32_t>(4.0f)), bit_cast<uint32_t>(0.4990234375f));
  ASSERT_EQ(AsmFrsqrte(bit_cast<uint32_t>(5.0f)), bit_cast<uint32_t>(0.4462890625f));
}

TEST(Arm64InsnTest, RecipSqrtEstimateF32x4) {
  constexpr auto AsmFrsqrte = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("frsqrte %0.4s, %1.4s");
  __uint128_t arg = MakeF32x4(2.0f, 3.0f, 4.0f, 5.0f);
  __uint128_t res = AsmFrsqrte(arg);
  ASSERT_EQ(res, MakeF32x4(0.705078125f, 0.576171875f, 0.4990234375f, 0.4462890625f));
}

TEST(Arm64InsnTest, RecipSqrtEstimateF64) {
  constexpr auto AsmFrsqrte = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("frsqrte %d0, %d1");
  ASSERT_EQ(AsmFrsqrte(bit_cast<uint64_t>(2.0)), bit_cast<uint64_t>(0.705078125));
  ASSERT_EQ(AsmFrsqrte(bit_cast<uint64_t>(3.0)), bit_cast<uint64_t>(0.576171875));
  ASSERT_EQ(AsmFrsqrte(bit_cast<uint64_t>(4.0)), bit_cast<uint64_t>(0.4990234375));
  ASSERT_EQ(AsmFrsqrte(bit_cast<uint64_t>(5.0)), bit_cast<uint64_t>(0.4462890625));
}

TEST(Arm64InsnTest, RecipSqrtEstimateF64x2) {
  constexpr auto AsmFrsqrte = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("frsqrte %0.2d, %1.2d");
  __uint128_t arg = MakeF64x2(2.0, 3.0);
  __uint128_t res = AsmFrsqrte(arg);
  ASSERT_EQ(res, MakeUInt128(bit_cast<uint64_t>(0.705078125), bit_cast<uint64_t>(0.576171875)));
}

TEST(Arm64InsnTest, RecipSqrtStepF32) {
  constexpr auto AsmFrsqrts = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("frsqrts %s0, %s1, %s2");
  __uint128_t res1 = AsmFrsqrts(bit_cast<uint32_t>(1.50f), bit_cast<uint32_t>(0.50f));
  ASSERT_EQ(res1, bit_cast<uint32_t>(1.125f));
  __uint128_t res2 = AsmFrsqrts(bit_cast<uint32_t>(2.00f), bit_cast<uint32_t>(0.50f));
  ASSERT_EQ(res2, bit_cast<uint32_t>(1.000f));
  __uint128_t res3 = AsmFrsqrts(bit_cast<uint32_t>(3.00f), bit_cast<uint32_t>(0.25f));
  ASSERT_EQ(res3, bit_cast<uint32_t>(1.125f));
  __uint128_t res4 = AsmFrsqrts(bit_cast<uint32_t>(3.00f), bit_cast<uint32_t>(0.50f));
  ASSERT_EQ(res4, bit_cast<uint32_t>(0.750f));
}

TEST(Arm64InsnTest, RecipSqrtStepF64) {
  constexpr auto AsmFrsqrts = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("frsqrts %d0, %d1, %d2");
  __uint128_t res1 = AsmFrsqrts(bit_cast<uint64_t>(1.50), bit_cast<uint64_t>(0.50));
  ASSERT_EQ(res1, bit_cast<uint64_t>(1.125));
  __uint128_t res2 = AsmFrsqrts(bit_cast<uint64_t>(2.00), bit_cast<uint64_t>(0.50));
  ASSERT_EQ(res2, bit_cast<uint64_t>(1.000));
  __uint128_t res3 = AsmFrsqrts(bit_cast<uint64_t>(3.00), bit_cast<uint64_t>(0.25));
  ASSERT_EQ(res3, bit_cast<uint64_t>(1.125));
  __uint128_t res4 = AsmFrsqrts(bit_cast<uint64_t>(3.00), bit_cast<uint64_t>(0.50));
  ASSERT_EQ(res4, bit_cast<uint64_t>(0.750));
}

TEST(Arm64InsnTest, RecipSqrtStepF32x4) {
  constexpr auto AsmFrsqrts = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("frsqrts %0.4s, %1.4s, %2.4s");
  __uint128_t arg1 = MakeF32x4(1.50f, 2.00f, 3.00f, 3.00f);
  __uint128_t arg2 = MakeF32x4(0.50f, 0.50f, 0.25f, 0.50f);
  __uint128_t res = AsmFrsqrts(arg1, arg2);
  ASSERT_EQ(res, MakeF32x4(1.125f, 1.000f, 1.125f, 0.750f));
}

TEST(Arm64InsnTest, RecipSqrtStepF64x2) {
  constexpr auto AsmFrsqrts = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("frsqrts %0.2d, %1.2d, %2.2d");
  __uint128_t arg1 = MakeF64x2(1.50, 2.00);
  __uint128_t arg2 = MakeF64x2(0.50, 0.50);
  ASSERT_EQ(AsmFrsqrts(arg1, arg2), MakeF64x2(1.125, 1.000));
  __uint128_t arg3 = MakeF64x2(3.00, 3.00);
  __uint128_t arg4 = MakeF64x2(0.25, 0.50);
  ASSERT_EQ(AsmFrsqrts(arg3, arg4), MakeF64x2(1.125, 0.750));
}

TEST(Arm64InsnTest, AddFp32) {
  uint64_t fp_arg1 = 0x40d5c28fULL;  // 6.68 in float
  uint64_t fp_arg2 = 0x409f5c29ULL;  // 4.98 in float
  __uint128_t rd = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fadd %s0, %s1, %s2")(fp_arg1, fp_arg2);
  ASSERT_EQ(rd, MakeUInt128(0x413a8f5cULL, 0U));  // 11.66 in float
}

TEST(Arm64InsnTest, AddFp64) {
  uint64_t fp_arg1 = 0x402099999999999aULL;  // 8.30 in double
  uint64_t fp_arg2 = 0x4010ae147ae147aeULL;  // 4.17 in double
  __uint128_t rd = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fadd %d0, %d1, %d2")(fp_arg1, fp_arg2);
  ASSERT_EQ(rd, MakeUInt128(0x4028f0a3d70a3d71ULL, 0U));  // 12.47 in double
}

TEST(Arm64InsnTest, AddF32x4) {
  constexpr auto AsmFadd = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fadd %0.4s, %1.4s, %2.4s");
  __uint128_t arg1 = MakeF32x4(-3.0f, 2.0f, 7.0f, -0.0f);
  __uint128_t arg2 = MakeF32x4(6.0f, 1.0f, -8.0f, 5.0f);
  ASSERT_EQ(AsmFadd(arg1, arg2), MakeF32x4(3.0f, 3.0f, -1.0f, 5.0f));
}

TEST(Arm64InsnTest, AddF64x2) {
  constexpr auto AsmFadd = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fadd %0.2d, %1.2d, %2.2d");
  __uint128_t arg1 = MakeF64x2(3.0, 5.0);
  __uint128_t arg2 = MakeF64x2(-4.0, 2.0);
  ASSERT_EQ(AsmFadd(arg1, arg2), MakeF64x2(-1.0, 7.0));
}

TEST(Arm64InsnTest, AddPairwiseF32x2) {
  constexpr auto AsmFaddp = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("faddp %s0, %1.2s");
  __uint128_t arg1 = MakeF32x4(1.0f, 2.0f, 4.0f, 8.0f);
  ASSERT_EQ(AsmFaddp(arg1), bit_cast<uint32_t>(3.0f));
}

TEST(Arm64InsnTest, AddPairwiseF32x4) {
  constexpr auto AsmFaddp = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("faddp %0.4s, %1.4s, %2.4s");
  __uint128_t arg1 = MakeF32x4(-3.0f, 2.0f, 7.0f, -0.0f);
  __uint128_t arg2 = MakeF32x4(6.0f, 1.0f, -8.0f, 5.0f);
  ASSERT_EQ(AsmFaddp(arg1, arg2), MakeF32x4(-1.0f, 7.0f, 7.0f, -3.0f));
}

TEST(Arm64InsnTest, SubFp32) {
  uint64_t fp_arg1 = 0x411f5c29ULL;  // 9.96 in float
  uint64_t fp_arg2 = 0x404851ecULL;  // 3.13 in float
  __uint128_t rd = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fsub %s0, %s1, %s2")(fp_arg1, fp_arg2);
  ASSERT_EQ(rd, MakeUInt128(0x40da8f5cULL, 0U));  // 6.83 in float
}

TEST(Arm64InsnTest, SubFp64) {
  uint64_t fp_arg1 = 0x401ee147ae147ae1ULL;  // 7.72 in double
  uint64_t fp_arg2 = 0x4015666666666666ULL;  // 5.35 in double
  __uint128_t rd = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fsub %d0, %d1, %d2")(fp_arg1, fp_arg2);
  ASSERT_EQ(rd, MakeUInt128(0x4002f5c28f5c28f6ULL, 0U));  // 2.37 in double
}

TEST(Arm64InsnTest, SubF32x4) {
  constexpr auto AsmFsub = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fsub %0.4s, %1.4s, %2.4s");
  __uint128_t arg1 = MakeF32x4(-3.0f, 2.0f, 7.0f, -0.0f);
  __uint128_t arg2 = MakeF32x4(6.0f, 1.0f, -8.0f, 5.0f);
  ASSERT_EQ(AsmFsub(arg1, arg2), MakeF32x4(-9.0f, 1.0f, 15.0f, -5.0f));
}

TEST(Arm64InsnTest, SubF64x2) {
  constexpr auto AsmFsub = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fsub %0.2d, %1.2d, %2.2d");
  __uint128_t arg1 = MakeF64x2(3.0, 5.0);
  __uint128_t arg2 = MakeF64x2(-4.0, 2.0);
  ASSERT_EQ(AsmFsub(arg1, arg2), MakeF64x2(7.0, 3.0));
}

TEST(Arm64InsnTest, MaxFp32) {
  constexpr auto AsmFmax = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fmax %s0, %s1, %s2");
  uint32_t fp_arg_two = bit_cast<uint32_t>(2.0f);
  uint32_t fp_arg_three = bit_cast<uint32_t>(3.0f);

  ASSERT_EQ(AsmFmax(fp_arg_two, fp_arg_three), MakeU32x4(fp_arg_three, 0, 0, 0));
  ASSERT_EQ(AsmFmax(kDefaultNaN32, fp_arg_three), kDefaultNaN32);
  ASSERT_EQ(AsmFmax(fp_arg_three, kDefaultNaN32), kDefaultNaN32);
}

TEST(Arm64InsnTest, MaxFp64) {
  constexpr auto AsmFmax = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fmax %d0, %d1, %d2");
  uint64_t fp_arg_two = bit_cast<uint64_t>(2.0);
  uint64_t fp_arg_three = bit_cast<uint64_t>(3.0);

  ASSERT_EQ(AsmFmax(fp_arg_two, fp_arg_three), MakeUInt128(fp_arg_three, 0U));
  ASSERT_EQ(AsmFmax(kDefaultNaN64, fp_arg_three), kDefaultNaN64);
  ASSERT_EQ(AsmFmax(fp_arg_three, kDefaultNaN64), kDefaultNaN64);
}

TEST(Arm64InsnTest, MaxF32x4) {
  constexpr auto AsmFmax = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fmax %0.4s, %1.4s, %2.4s");
  __uint128_t arg1 = MakeF32x4(-0.0f, 2.0f, 3.0f, -4.0f);
  __uint128_t arg2 = MakeF32x4(0.0f, 1.0f, -3.0f, -3.0f);
  ASSERT_EQ(AsmFmax(arg1, arg2), MakeF32x4(0.0f, 2.0f, 3.0f, -3.0f));

  __uint128_t arg3 = MakeF32x4(-0.0f, bit_cast<float>(kDefaultNaN32), 3.0f, -4.0f);
  __uint128_t arg4 = MakeF32x4(0.0f, 1.0f, -3.0f, bit_cast<float>(kDefaultNaN32));
  ASSERT_EQ(AsmFmax(arg3, arg4),
            MakeF32x4(0.0f, bit_cast<float>(kDefaultNaN32), 3.0f, bit_cast<float>(kDefaultNaN32)));
}

TEST(Arm64InsnTest, MaxF64x2) {
  constexpr auto AsmFmax = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fmax %0.2d, %1.2d, %2.2d");
  __uint128_t arg1 = MakeF64x2(-0.0, 3.0);
  __uint128_t arg2 = MakeF64x2(0.0, -3.0);
  ASSERT_EQ(AsmFmax(arg1, arg2), MakeF64x2(0.0, 3.0));

  __uint128_t arg3 = MakeF64x2(bit_cast<double>(kDefaultNaN64), 3.0);
  __uint128_t arg4 = MakeF64x2(1.0, bit_cast<double>(kDefaultNaN64));
  ASSERT_EQ(AsmFmax(arg3, arg4),
            MakeF64x2(bit_cast<double>(kDefaultNaN64), bit_cast<double>(kDefaultNaN64)));
}

TEST(Arm64InsnTest, MaxNumberFp32) {
  constexpr auto AsmFmaxnm = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fmaxnm %s0, %s1, %s2");
  uint32_t fp_arg_two = bit_cast<uint32_t>(2.0f);
  uint32_t fp_arg_three = bit_cast<uint32_t>(3.0f);
  uint64_t fp_arg_minus_two = bit_cast<uint64_t>(-2.0);

  ASSERT_EQ(AsmFmaxnm(fp_arg_two, fp_arg_three), MakeU32x4(fp_arg_three, 0, 0, 0));

  ASSERT_EQ(AsmFmaxnm(fp_arg_two, kQuietNaN32), MakeU32x4(fp_arg_two, 0, 0, 0));
  ASSERT_EQ(AsmFmaxnm(fp_arg_minus_two, kQuietNaN32), MakeU32x4(fp_arg_minus_two, 0, 0, 0));
  ASSERT_EQ(AsmFmaxnm(kQuietNaN32, fp_arg_two), MakeU32x4(fp_arg_two, 0, 0, 0));
  ASSERT_EQ(AsmFmaxnm(kQuietNaN32, fp_arg_minus_two), MakeU32x4(fp_arg_minus_two, 0, 0, 0));
}

TEST(Arm64InsnTest, MaxNumberFp64) {
  constexpr auto AsmFmaxnm = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fmaxnm %d0, %d1, %d2");
  uint64_t fp_arg_two = bit_cast<uint64_t>(2.0);
  uint64_t fp_arg_three = bit_cast<uint64_t>(3.0);
  uint64_t fp_arg_minus_two = bit_cast<uint64_t>(-2.0);

  ASSERT_EQ(AsmFmaxnm(fp_arg_two, fp_arg_three), MakeUInt128(fp_arg_three, 0U));

  ASSERT_EQ(AsmFmaxnm(fp_arg_two, kQuietNaN64), MakeUInt128(fp_arg_two, 0U));
  ASSERT_EQ(AsmFmaxnm(fp_arg_minus_two, kQuietNaN64), MakeUInt128(fp_arg_minus_two, 0));
  ASSERT_EQ(AsmFmaxnm(kQuietNaN64, fp_arg_two), MakeUInt128(fp_arg_two, 0));
  ASSERT_EQ(AsmFmaxnm(kQuietNaN64, fp_arg_minus_two), MakeUInt128(fp_arg_minus_two, 0));
}

TEST(Arm64InsnTest, MinNumberFp32) {
  constexpr auto AsmFminnm = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fminnm %s0, %s1, %s2");
  uint32_t fp_arg_two = bit_cast<uint32_t>(2.0f);
  uint32_t fp_arg_three = bit_cast<uint32_t>(3.0f);
  uint32_t fp_arg_minus_two = bit_cast<uint32_t>(-2.0f);

  ASSERT_EQ(AsmFminnm(fp_arg_two, fp_arg_three), MakeU32x4(fp_arg_two, 0, 0, 0));

  ASSERT_EQ(AsmFminnm(fp_arg_two, kQuietNaN32), MakeU32x4(fp_arg_two, 0, 0, 0));
  ASSERT_EQ(AsmFminnm(fp_arg_minus_two, kQuietNaN32), MakeU32x4(fp_arg_minus_two, 0, 0, 0));
  ASSERT_EQ(AsmFminnm(kQuietNaN32, fp_arg_two), MakeU32x4(fp_arg_two, 0, 0, 0));
  ASSERT_EQ(AsmFminnm(kQuietNaN32, fp_arg_minus_two), MakeU32x4(fp_arg_minus_two, 0, 0, 0));
}

TEST(Arm64InsnTest, MinNumberFp64) {
  constexpr auto AsmFminnm = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fminnm %d0, %d1, %d2");
  uint64_t fp_arg_two = bit_cast<uint64_t>(2.0);
  uint64_t fp_arg_three = bit_cast<uint64_t>(3.0);
  uint64_t fp_arg_minus_two = bit_cast<uint64_t>(-2.0);

  ASSERT_EQ(AsmFminnm(fp_arg_two, fp_arg_three), MakeUInt128(fp_arg_two, 0U));

  ASSERT_EQ(AsmFminnm(fp_arg_two, kQuietNaN64), MakeUInt128(fp_arg_two, 0U));
  ASSERT_EQ(AsmFminnm(fp_arg_minus_two, kQuietNaN64), MakeUInt128(fp_arg_minus_two, 0));
  ASSERT_EQ(AsmFminnm(kQuietNaN64, fp_arg_two), MakeUInt128(fp_arg_two, 0));
  ASSERT_EQ(AsmFminnm(kQuietNaN64, fp_arg_minus_two), MakeUInt128(fp_arg_minus_two, 0));
}

TEST(Arm64InsnTest, MaxNumberF32x4) {
  constexpr auto AsmFmaxnm = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fmaxnm %0.4s, %1.4s, %2.4s");
  __uint128_t arg1 = MakeF32x4(-1.0f, 2.0f, 3.0f, -4.0f);
  __uint128_t arg2 = MakeF32x4(2.0f, 1.0f, -3.0f, -3.0f);
  ASSERT_EQ(AsmFmaxnm(arg1, arg2), MakeF32x4(2.0f, 2.0f, 3.0f, -3.0f));

  __uint128_t arg3 =
      MakeU32x4(bit_cast<uint32_t>(1.0f), bit_cast<uint32_t>(-1.0f), kQuietNaN32, kQuietNaN32);
  __uint128_t arg4 =
      MakeU32x4(kQuietNaN32, kQuietNaN32, bit_cast<uint32_t>(1.0f), bit_cast<uint32_t>(-1.0f));
  ASSERT_EQ(AsmFmaxnm(arg3, arg4), MakeF32x4(1.0f, -1.0f, 1.0f, -1.0f));

  __uint128_t arg5 = MakeU32x4(
      bit_cast<uint32_t>(1.0f), bit_cast<uint32_t>(-1.0f), kSignalingNaN32_1, kQuietNaN32);
  __uint128_t arg6 = MakeU32x4(
      kSignalingNaN32_1, kQuietNaN32, bit_cast<uint32_t>(1.0f), bit_cast<uint32_t>(-1.0f));
}

TEST(Arm64InsnTest, MaxNumberF64x2) {
  constexpr auto AsmFmaxnm = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fmaxnm %0.2d, %1.2d, %2.2d");
  __uint128_t arg1 = MakeF64x2(-1.0, -4.0);
  __uint128_t arg2 = MakeF64x2(2.0, -3.0);
  ASSERT_EQ(AsmFmaxnm(arg1, arg2), MakeF64x2(2.0, -3.0));

  __uint128_t arg3 = MakeUInt128(bit_cast<uint64_t>(1.0), kQuietNaN64);
  __uint128_t arg4 = MakeUInt128(kQuietNaN64, bit_cast<uint64_t>(-1.0));
  ASSERT_EQ(AsmFmaxnm(arg3, arg4), MakeF64x2(1.0, -1.0));
}

TEST(Arm64InsnTest, MinNumberF32x4) {
  constexpr auto AsmFminnm = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fminnm %0.4s, %1.4s, %2.4s");
  __uint128_t arg1 = MakeF32x4(0.0f, 2.0f, 3.0f, -4.0f);
  __uint128_t arg2 = MakeF32x4(-0.0f, 1.0f, -3.0f, -3.0f);
  ASSERT_EQ(AsmFminnm(arg1, arg2), MakeF32x4(-0.0f, 1.0f, -3.0f, -4.0f));

  __uint128_t arg3 =
      MakeU32x4(bit_cast<uint32_t>(1.0f), bit_cast<uint32_t>(-1.0f), kQuietNaN32, kQuietNaN32);
  __uint128_t arg4 =
      MakeU32x4(kQuietNaN32, kQuietNaN32, bit_cast<uint32_t>(1.0f), bit_cast<uint32_t>(-1.0f));
  __uint128_t res = AsmFminnm(arg3, arg4);
  ASSERT_EQ(res, MakeF32x4(1.0f, -1.0f, 1.0f, -1.0f));
}

TEST(Arm64InsnTest, MinNumberF64x2) {
  constexpr auto AsmFminnm = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fminnm %0.2d, %1.2d, %2.2d");
  __uint128_t arg1 = MakeF64x2(0.0, 3.0);
  __uint128_t arg2 = MakeF64x2(-0.0, -3.0);
  ASSERT_EQ(AsmFminnm(arg1, arg2), MakeF64x2(-0.0, -3.0));

  __uint128_t arg3 = MakeUInt128(bit_cast<uint64_t>(1.0), kQuietNaN64);
  __uint128_t arg4 = MakeUInt128(kQuietNaN64, bit_cast<uint64_t>(-1.0));
  __uint128_t res = AsmFminnm(arg3, arg4);
  ASSERT_EQ(res, MakeF64x2(1.0, -1.0));
}

TEST(Arm64InsnTest, MinFp32) {
  constexpr auto AsmFmin = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fmin %s0, %s1, %s2");
  uint32_t fp_arg_two = bit_cast<uint32_t>(2.0f);
  uint32_t fp_arg_three = bit_cast<uint32_t>(3.0f);

  ASSERT_EQ(AsmFmin(fp_arg_two, fp_arg_three), MakeU32x4(fp_arg_two, 0, 0, 0));
  ASSERT_EQ(AsmFmin(kDefaultNaN32, fp_arg_three), kDefaultNaN32);
  ASSERT_EQ(AsmFmin(fp_arg_three, kDefaultNaN32), kDefaultNaN32);
}

TEST(Arm64InsnTest, MinFp64) {
  constexpr auto AsmFmin = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fmin %d0, %d1, %d2");
  uint64_t fp_arg_two = bit_cast<uint64_t>(2.0);
  uint64_t fp_arg_three = bit_cast<uint64_t>(3.0);

  ASSERT_EQ(AsmFmin(fp_arg_two, fp_arg_three), MakeUInt128(fp_arg_two, 0U));
  ASSERT_EQ(AsmFmin(kDefaultNaN64, fp_arg_three), kDefaultNaN64);
  ASSERT_EQ(AsmFmin(fp_arg_three, kDefaultNaN64), kDefaultNaN64);
}

TEST(Arm64InsnTest, MinF32x4) {
  constexpr auto AsmFmin = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fmin %0.4s, %1.4s, %2.4s");
  __uint128_t arg1 = MakeF32x4(0.0f, 2.0f, 3.0f, -4.0f);
  __uint128_t arg2 = MakeF32x4(-0.0f, 1.0f, -3.0f, -3.0f);
  ASSERT_EQ(AsmFmin(arg1, arg2), MakeF32x4(-0.0f, 1.0f, -3.0f, -4.0f));

  __uint128_t arg3 = MakeF32x4(-0.0f, bit_cast<float>(kDefaultNaN32), 3.0f, -4.0f);
  __uint128_t arg4 = MakeF32x4(0.0f, 1.0f, -3.0f, bit_cast<float>(kDefaultNaN32));
  ASSERT_EQ(
      AsmFmin(arg3, arg4),
      MakeF32x4(-0.0f, bit_cast<float>(kDefaultNaN32), -3.0f, bit_cast<float>(kDefaultNaN32)));
}

TEST(Arm64InsnTest, MinF64x2) {
  constexpr auto AsmFmin = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fmin %0.2d, %1.2d, %2.2d");
  __uint128_t arg1 = MakeF64x2(0.0, 3.0);
  __uint128_t arg2 = MakeF64x2(-0.0, -3.0);
  ASSERT_EQ(AsmFmin(arg1, arg2), MakeF64x2(-0.0, -3.0));

  __uint128_t arg3 = MakeF64x2(bit_cast<double>(kDefaultNaN64), 3.0);
  __uint128_t arg4 = MakeF64x2(1.0, bit_cast<double>(kDefaultNaN64));
  ASSERT_EQ(AsmFmin(arg3, arg4),
            MakeF64x2(bit_cast<double>(kDefaultNaN64), bit_cast<double>(kDefaultNaN64)));
}

TEST(Arm64InsnTest, MaxPairwiseF32Scalar) {
  constexpr auto AsmFmaxp = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fmaxp %s0, %1.2s");
  __uint128_t arg1 = MakeF32x4(-3.0f, 2.0f, 7.0f, -0.0f);
  ASSERT_EQ(AsmFmaxp(arg1), bit_cast<uint32_t>(2.0f));

  __uint128_t arg2 = MakeF32x4(bit_cast<float>(kDefaultNaN32), 2.0f, 7.0f, -0.0f);
  ASSERT_EQ(AsmFmaxp(arg2), kDefaultNaN32);
}

TEST(Arm64InsnTest, MaxPairwiseF32x4) {
  constexpr auto AsmFmaxp = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fmaxp %0.4s, %1.4s, %2.4s");
  __uint128_t arg1 = MakeF32x4(-3.0f, 2.0f, 7.0f, -0.0f);
  __uint128_t arg2 = MakeF32x4(6.0f, 1.0f, -8.0f, 5.0f);
  ASSERT_EQ(AsmFmaxp(arg1, arg2), MakeF32x4(2.0f, 7.0f, 6.0f, 5.0f));

  __uint128_t arg3 =
      MakeF32x4(bit_cast<float>(kDefaultNaN32), 2.0f, 7.0f, bit_cast<float>(kDefaultNaN32));
  __uint128_t arg4 = MakeF32x4(6.0f, 1.0f, -8.0f, 5.0f);
  ASSERT_EQ(AsmFmaxp(arg3, arg4),
            MakeF32x4(bit_cast<float>(kDefaultNaN32), bit_cast<float>(kDefaultNaN32), 6.0f, 5.0f));
}

TEST(Arm64InsnTest, MinPairwiseF32Scalar) {
  constexpr auto AsmFminp = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fminp %s0, %1.2s");
  __uint128_t arg1 = MakeF32x4(-3.0f, 2.0f, 7.0f, -0.0f);
  ASSERT_EQ(AsmFminp(arg1), bit_cast<uint32_t>(-3.0f));

  __uint128_t arg2 = MakeF32x4(bit_cast<float>(kDefaultNaN32), 2.0f, 7.0f, -0.0f);
  ASSERT_EQ(AsmFminp(arg2), kDefaultNaN32);
}

TEST(Arm64InsnTest, MinPairwiseF32x4) {
  constexpr auto AsmFminp = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fminp %0.4s, %1.4s, %2.4s");
  __uint128_t arg1 = MakeF32x4(-3.0f, 2.0f, 7.0f, -0.0f);
  __uint128_t arg2 = MakeF32x4(6.0f, 1.0f, -8.0f, 5.0f);
  ASSERT_EQ(AsmFminp(arg1, arg2), MakeF32x4(-3.0f, -0.0f, 1.0f, -8.0f));

  __uint128_t arg3 =
      MakeF32x4(bit_cast<float>(kDefaultNaN32), 2.0f, 7.0f, bit_cast<float>(kDefaultNaN32));
  __uint128_t arg4 = MakeF32x4(6.0f, 1.0f, -8.0f, 5.0f);
  ASSERT_EQ(AsmFminp(arg3, arg4),
            MakeF32x4(bit_cast<float>(kDefaultNaN32), bit_cast<float>(kDefaultNaN32), 1.0f, -8.0f));
}

TEST(Arm64InsnTest, MaxPairwiseNumberF32Scalar) {
  constexpr auto AsmFmaxnmp = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fmaxnmp %s0, %1.2s");
  __uint128_t arg1 = MakeF32x4(-3.0f, 2.0f, 7.0f, -0.0f);
  ASSERT_EQ(AsmFmaxnmp(arg1), bit_cast<uint32_t>(2.0f));

  __uint128_t arg2 = MakeF32x4(bit_cast<float>(kQuietNaN32), 2.0f, 7.0f, -0.0f);
  ASSERT_EQ(AsmFmaxnmp(arg2), bit_cast<uint32_t>(2.0f));
}

TEST(Arm64InsnTest, MaxPairwiseNumberF32x4) {
  constexpr auto AsmFmaxnmp = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fmaxnmp %0.4s, %1.4s, %2.4s");
  __uint128_t arg1 = MakeF32x4(-3.0f, 2.0f, 7.0f, -0.0f);
  __uint128_t arg2 = MakeF32x4(6.0f, 1.0f, -8.0f, 5.0f);
  ASSERT_EQ(AsmFmaxnmp(arg1, arg2), MakeF32x4(2.0f, 7.0f, 6.0f, 5.0f));

  __uint128_t arg3 =
      MakeF32x4(bit_cast<float>(kQuietNaN32), 2.0f, 7.0f, bit_cast<float>(kQuietNaN32));
  __uint128_t arg4 = MakeF32x4(6.0f, 1.0f, -8.0f, 5.0f);
  ASSERT_EQ(AsmFmaxnmp(arg3, arg4), MakeF32x4(2.0f, 7.0f, 6.0f, 5.0f));
}

TEST(Arm64InsnTest, MinPairwiseNumberF32Scalar) {
  constexpr auto AsmFminnmp = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fminnmp %s0, %1.2s");
  __uint128_t arg1 = MakeF32x4(-3.0f, 2.0f, 7.0f, -0.0f);
  ASSERT_EQ(AsmFminnmp(arg1), bit_cast<uint32_t>(-3.0f));

  __uint128_t arg2 = MakeF32x4(bit_cast<float>(kQuietNaN32), 2.0f, 7.0f, -0.0f);
  ASSERT_EQ(AsmFminnmp(arg2), bit_cast<uint32_t>(2.0f));
}

TEST(Arm64InsnTest, MinPairwiseNumberF32x4) {
  constexpr auto AsmFminnmp = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fminnmp %0.4s, %1.4s, %2.4s");
  __uint128_t arg1 = MakeF32x4(-3.0f, 2.0f, 7.0f, -0.0f);
  __uint128_t arg2 = MakeF32x4(6.0f, 1.0f, -8.0f, 5.0f);
  ASSERT_EQ(AsmFminnmp(arg1, arg2), MakeF32x4(-3.0f, -0.0f, 1.0f, -8.0f));

  __uint128_t arg3 =
      MakeF32x4(bit_cast<float>(kQuietNaN32), 2.0f, 7.0f, bit_cast<float>(kQuietNaN32));
  __uint128_t arg4 = MakeF32x4(6.0f, 1.0f, -8.0f, 5.0f);
  ASSERT_EQ(AsmFminnmp(arg3, arg4), MakeF32x4(2.0f, 7.0f, 1.0f, -8.0f));
}

TEST(Arm64InsnTest, MaxAcrossF32x4) {
  constexpr auto AsmFmaxv = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fmaxv %s0, %1.4s");
  __uint128_t arg1 = MakeF32x4(0.0f, 2.0f, 3.0f, -4.0f);
  ASSERT_EQ(AsmFmaxv(arg1), bit_cast<uint32_t>(3.0f));

  __uint128_t arg2 = MakeF32x4(0.0f, 2.0f, bit_cast<float>(kDefaultNaN32), -4.0f);
  ASSERT_EQ(AsmFmaxv(arg2), kDefaultNaN32);
}

TEST(Arm64InsnTest, MinAcrossF32x4) {
  constexpr auto AsmFminv = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fminv %s0, %1.4s");
  __uint128_t arg1 = MakeF32x4(0.0f, 2.0f, 3.0f, -4.0f);
  ASSERT_EQ(AsmFminv(arg1), bit_cast<uint32_t>(-4.0f));

  __uint128_t arg2 = MakeF32x4(0.0f, 2.0f, bit_cast<float>(kDefaultNaN32), -4.0f);
  ASSERT_EQ(AsmFminv(arg2), kDefaultNaN32);
}

TEST(Arm64InsnTest, MaxNumberAcrossF32x4) {
  constexpr auto AsmFmaxnmv = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fmaxnmv %s0, %1.4s");
  __uint128_t arg1 = MakeF32x4(0.0f, 2.0f, 3.0f, -4.0f);
  ASSERT_EQ(AsmFmaxnmv(arg1), bit_cast<uint32_t>(3.0f));

  __uint128_t arg2 = MakeF32x4(0.0f, bit_cast<float>(kQuietNaN32), 3.0f, -4.0f);
  ASSERT_EQ(AsmFmaxnmv(arg2), bit_cast<uint32_t>(3.0f));
}

TEST(Arm64InsnTest, MinNumberAcrossF32x4) {
  constexpr auto AsmFminnmv = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fminnmv %s0, %1.4s");
  __uint128_t arg1 = MakeF32x4(0.0f, 2.0f, 3.0f, -4.0f);
  ASSERT_EQ(AsmFminnmv(arg1), bit_cast<uint32_t>(-4.0f));

  __uint128_t arg2 = MakeF32x4(0.0f, bit_cast<float>(kQuietNaN32), 3.0f, -4.0f);
  ASSERT_EQ(AsmFminnmv(arg2), bit_cast<uint32_t>(-4.0f));
}

TEST(Arm64InsnTest, MulFp32) {
  uint64_t fp_arg1 = 0x40a1999aULL;  // 5.05 in float
  uint64_t fp_arg2 = 0x40dae148ULL;  // 6.84 in float
  __uint128_t rd = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fmul %s0, %s1, %s2")(fp_arg1, fp_arg2);
  ASSERT_EQ(rd, MakeUInt128(0x420a2b03ULL, 0U));  // 34.5420 in float
}

TEST(Arm64InsnTest, MulFp64) {
  uint64_t fp_arg1 = 0x40226b851eb851ecULL;  // 9.21 in double
  uint64_t fp_arg2 = 0x4020c7ae147ae148ULL;  // 8.39 in double
  __uint128_t rd = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fmul %d0, %d1, %d2")(fp_arg1, fp_arg2);
  ASSERT_EQ(rd, MakeUInt128(0x40535166cf41f214ULL, 0U));  // 77.2719 in double
}

TEST(Arm64InsnTest, MulF32x4) {
  constexpr auto AsmFmul = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fmul %0.4s, %1.4s, %2.4s");
  __uint128_t arg1 = MakeF32x4(1.0f, -2.0f, 3.0f, -4.0f);
  __uint128_t arg2 = MakeF32x4(-3.0f, -1.0f, 4.0f, 1.0f);
  ASSERT_EQ(AsmFmul(arg1, arg2), MakeF32x4(-3.0f, 2.0f, 12.0f, -4.0f));
}

TEST(Arm64InsnTest, MulF64x2) {
  constexpr auto AsmFmul = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fmul %0.2d, %1.2d, %2.2d");
  __uint128_t arg1 = MakeF64x2(-4.0, 2.0);
  __uint128_t arg2 = MakeF64x2(2.0, 3.0);
  ASSERT_EQ(AsmFmul(arg1, arg2), MakeF64x2(-8.0, 6.0));
}

TEST(Arm64InsnTest, MulF32x4ByScalar) {
  __uint128_t arg1 = MakeF32x4(2.0f, 3.0f, 4.0f, 5.0f);
  __uint128_t arg2 = MakeF32x4(6.0f, 7.0f, 8.0f, 9.0f);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fmul %0.4s, %1.4s, %2.s[3]")(arg1, arg2);
  ASSERT_EQ(res, MakeF32x4(18.0f, 27.0f, 36.0f, 45.0f));
}

TEST(Arm64InsnTest, MulF64x2ByScalar) {
  __uint128_t arg1 = MakeF64x2(2.0, 3.0);
  __uint128_t arg2 = MakeF64x2(5.0, 4.0);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fmul %0.2d, %1.2d, %2.d[1]")(arg1, arg2);
  ASSERT_EQ(res, MakeF64x2(8.0, 12.0));
}

TEST(Arm64InsnTest, MulF32IndexedElem) {
  constexpr auto AsmFmul = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fmul %s0, %s1, %2.s[2]");
  __uint128_t arg1 = MakeF32x4(2.0f, 3.0f, 5.0f, 7.0f);
  __uint128_t arg2 = MakeF32x4(11.0f, 13.0f, 17.0f, 19.0f);
  ASSERT_EQ(AsmFmul(arg1, arg2), bit_cast<uint32_t>(34.0f));
}

TEST(Arm64InsnTest, MulF64IndexedElem) {
  constexpr auto AsmFmul = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fmul %d0, %d1, %2.d[1]");
  __uint128_t arg1 = MakeF64x2(2.0, 3.0);
  __uint128_t arg2 = MakeF64x2(5.0, 4.0);
  ASSERT_EQ(AsmFmul(arg1, arg2), bit_cast<uint64_t>(8.0));
}

TEST(Arm64InsnTest, MulExtendedF32) {
  constexpr auto AsmFmulx = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fmulx %s0, %s1, %s2");
  __uint128_t arg1 = MakeF32x4(2.0f, 3.0f, 5.0f, 7.0f);
  __uint128_t arg2 = MakeF32x4(11.0f, 13.0f, 17.0f, 19.0f);
  ASSERT_EQ(AsmFmulx(arg1, arg2), bit_cast<uint32_t>(22.0f));
}

TEST(Arm64InsnTest, MulExtendedF32x4) {
  constexpr auto AsmFmulx = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fmulx %0.4s, %1.4s, %2.4s");
  __uint128_t arg1 = MakeF32x4(2.0f, 3.0f, 5.0f, 7.0f);
  __uint128_t arg2 = MakeF32x4(11.0f, 13.0f, 17.0f, 19.0f);
  ASSERT_EQ(AsmFmulx(arg1, arg2), MakeF32x4(22.0f, 39.0f, 85.0f, 133.0f));
}

TEST(Arm64InsnTest, MulExtendedF32IndexedElem) {
  constexpr auto AsmFmulx = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fmulx %s0, %s1, %2.s[2]");
  __uint128_t arg1 = MakeF32x4(2.0f, 3.0f, 5.0f, 7.0f);
  __uint128_t arg2 = MakeF32x4(11.0f, 13.0f, 17.0f, 19.0f);
  ASSERT_EQ(AsmFmulx(arg1, arg2), bit_cast<uint32_t>(34.0f));
}

TEST(Arm64InsnTest, MulExtendedF64IndexedElem) {
  constexpr auto AsmFmulx = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fmulx %d0, %d1, %2.d[1]");
  __uint128_t arg1 = MakeF64x2(2.0, 3.0);
  __uint128_t arg2 = MakeF64x2(5.0, 4.0);
  ASSERT_EQ(AsmFmulx(arg1, arg2), bit_cast<uint64_t>(8.0));
}

TEST(Arm64InsnTest, MulExtendedF32x4IndexedElem) {
  constexpr auto AsmFmulx = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fmulx %0.4s, %1.4s, %2.s[2]");
  __uint128_t arg1 = MakeF32x4(2.0f, 3.0f, 5.0f, 7.0f);
  __uint128_t arg2 = MakeF32x4(11.0f, 13.0f, 17.0f, 19.0f);
  ASSERT_EQ(AsmFmulx(arg1, arg2), MakeF32x4(34.0f, 51.0f, 85.0f, 119.0f));
}

TEST(Arm64InsnTest, MulNegFp32) {
  uint64_t fp_arg1 = bit_cast<uint32_t>(2.0f);
  uint64_t fp_arg2 = bit_cast<uint32_t>(3.0f);
  __uint128_t rd = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fnmul %s0, %s1, %s2")(fp_arg1, fp_arg2);
  ASSERT_EQ(rd, MakeUInt128(bit_cast<uint32_t>(-6.0f), 0U));
}

TEST(Arm64InsnTest, MulNegFp64) {
  uint64_t fp_arg1 = bit_cast<uint64_t>(2.0);
  uint64_t fp_arg2 = bit_cast<uint64_t>(3.0);
  __uint128_t rd = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fnmul %d0, %d1, %d2")(fp_arg1, fp_arg2);
  ASSERT_EQ(rd, MakeUInt128(bit_cast<uint64_t>(-6.0), 0U));
}

TEST(Arm64InsnTest, DivFp32) {
  constexpr auto AsmFdiv = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fdiv %s0, %s1, %s2");

  uint32_t arg1 = 0x40c23d71U;                                     // 6.07 in float
  uint32_t arg2 = 0x401a3d71U;                                     // 2.41 in float
  ASSERT_EQ(AsmFdiv(arg1, arg2), MakeUInt128(0x402131edULL, 0U));  // 2.5186722 in float

  // Make sure that FDIV can produce a denormal result under the default FPCR,
  // where the FZ bit (flush-to-zero) is off.
  uint32_t arg3 = 0xa876eff9U;  // exponent (without offset) = -47
  uint32_t arg4 = 0xe7d86b60U;  // exponent (without offset) = 80
  ASSERT_EQ(AsmFdiv(arg3, arg4), MakeUInt128(0x0049065cULL, 0U));  // denormal
}

TEST(Arm64InsnTest, DivFp64) {
  uint64_t fp_arg1 = 0x401e5c28f5c28f5cULL;  // 7.59 in double
  uint64_t fp_arg2 = 0x3ff28f5c28f5c28fULL;  // 1.16 in double
  __uint128_t rd = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fdiv %d0, %d1, %d2")(fp_arg1, fp_arg2);
  ASSERT_EQ(rd, MakeUInt128(0x401a2c234f72c235ULL, 0U));  // 6.5431034482758620995923593 in double
}

TEST(Arm64InsnTest, DivFp32_FlagsWhenDivByZero) {
  uint64_t fpsr;
  volatile float dividend = 123.0f;
  volatile float divisor = 0.0f;
  float res;
  asm volatile(
      "msr fpsr, xzr\n\t"
      "fdiv %s1, %s2, %s3\n\t"
      "mrs %0, fpsr"
      : "=r"(fpsr), "=w"(res)
      : "w"(dividend), "w"(divisor));
  ASSERT_TRUE((fpsr & kFpsrDzcBit) == (kFpsrDzcBit));

  // Previous bug caused IOC to be set upon scalar div by zero.
  ASSERT_TRUE((fpsr & kFpsrIocBit) == 0);
}

TEST(Arm64InsnTest, DivFp64_FlagsWhenDivByZero) {
  uint64_t fpsr;
  double res;
  asm volatile(
      "msr fpsr, xzr\n\t"
      "fdiv %d1, %d2, %d3\n\t"
      "mrs %0, fpsr"
      : "=r"(fpsr), "=w"(res)
      : "w"(123.0), "w"(0.0));
  ASSERT_TRUE((fpsr & kFpsrDzcBit) == (kFpsrDzcBit));

  // Previous bug caused IOC to be set upon scalar div by zero.
  ASSERT_TRUE((fpsr & kFpsrIocBit) == 0);
}

TEST(Arm64InsnTest, DivFp32x4) {
  constexpr auto AsmFdiv = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fdiv %0.4s, %1.4s, %2.4s");

  // 16.39, 80.286, 41.16, 98.01
  __uint128_t arg1 = MakeUInt128(0x41831eb842a0926fULL, 0x4224a3d742c4051fULL);
  // 13.3, 45.45, 7.89, -2.63
  __uint128_t arg2 = MakeUInt128(0x4154cccd4235cccdULL, 0x40fc7ae1c02851ecULL);
  __uint128_t res1 = AsmFdiv(arg1, arg2);
  // 1.2323308, 1.7664686, 5.21673, -37.26616
  ASSERT_EQ(res1, MakeUInt128(0x3f9dbd043fe21ba5ULL, 0x40a6ef74c215108cULL));

  // Verify that fdiv produces a denormal result under the default FPCR.
  __uint128_t arg3 = MakeF32x4(1.0f, 1.0f, 1.0f, -0x1.eddff2p-47f);
  __uint128_t arg4 = MakeF32x4(1.0f, 1.0f, 1.0f, -0x1.b0d6c0p80f);
  __uint128_t res2 = AsmFdiv(arg3, arg4);
  __uint128_t expected2 = MakeF32x4(1.0f, 1.0f, 1.0f, 0x0.920cb8p-126f);
  ASSERT_EQ(res2, expected2);
}

TEST(Arm64InsnTest, DivFp64x2) {
  // 6.23, 65.02
  __uint128_t arg1 = MakeUInt128(0x4018EB851EB851ECULL, 0x40504147AE147AE1ULL);
  // -7.54, 11.92
  __uint128_t arg2 = MakeUInt128(0xC01E28F5C28F5C29ULL, 0x4027D70A3D70A3D7ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fdiv %0.2d, %1.2d, %2.2d")(arg1, arg2);
  // -0.82625994695, 5.45469798658
  ASSERT_EQ(res, MakeUInt128(0xbfea70b8b3449564ULL, 0x4015d19c59579fc9ULL));
}

TEST(Arm64InsnTest, MulAddFp32) {
  constexpr auto AsmFmadd = ASM_INSN_WRAP_FUNC_W_RES_WWW_ARG("fmadd %s0, %s1, %s2, %s3");

  __uint128_t res1 =
      AsmFmadd(bit_cast<uint32_t>(2.0f), bit_cast<uint32_t>(3.0f), bit_cast<uint32_t>(5.0f));
  ASSERT_EQ(res1, MakeF32x4(11.0f, 0, 0, 0));

  __uint128_t res2 =
      AsmFmadd(bit_cast<uint32_t>(2.5f), bit_cast<uint32_t>(2.0f), bit_cast<uint32_t>(-5.0f));
  ASSERT_EQ(res2, MakeF32x4(0, 0, 0, 0));

  // These tests verify that fmadd does not lose precision while doing the mult + add.
  __uint128_t res3 = AsmFmadd(bit_cast<uint32_t>(0x1.fffffep22f),
                              bit_cast<uint32_t>(0x1.000002p0f),
                              bit_cast<uint32_t>(-0x1.p23f));
  ASSERT_EQ(res3, MakeF32x4(0x1.fffffcp-2f, 0, 0, 0));

  __uint128_t res4 = AsmFmadd(bit_cast<uint32_t>(0x1.fffffep22f),
                              bit_cast<uint32_t>(0x1.000002p0f),
                              bit_cast<uint32_t>(-0x1.fffffep22f));
  ASSERT_EQ(res4, MakeF32x4(0x1.fffffep-1f, 0, 0, 0));

  __uint128_t res5 = AsmFmadd(bit_cast<uint32_t>(0x1.p23f),
                              bit_cast<uint32_t>(0x1.fffffep-1f),
                              bit_cast<uint32_t>(-0x1.000002p23f));
  ASSERT_EQ(res5, MakeF32x4(-0x1.80p0f, 0, 0, 0));
}

TEST(Arm64InsnTest, MulAddFp64) {
  uint64_t arg1 = 0x40323d70a3d70a3dULL;  // 18.24
  uint64_t arg2 = 0x40504147ae147ae1ULL;  // 65.02
  uint64_t arg3 = 0x4027d70a3d70a3d7ULL;  // 11.92
  __uint128_t res1 = ASM_INSN_WRAP_FUNC_W_RES_WWW_ARG("fmadd %d0, %d1, %d2, %d3")(arg1, arg2, arg3);
  ASSERT_EQ(res1, MakeUInt128(0x4092b78a0902de00ULL, 0U));  // 1197.8848
  __uint128_t res2 =
      ASM_INSN_WRAP_FUNC_W_RES_WWW_ARG("fnmadd %d0, %d1, %d2, %d3")(arg1, arg2, arg3);
  ASSERT_EQ(res2, MakeUInt128(0xc092b78a0902de00ULL, 0U));  // -1197.8848
}

TEST(Arm64InsnTest, MulAddFp64Precision) {
  uint64_t arg1 = bit_cast<uint64_t>(0x1.0p1023);
  uint64_t arg2 = bit_cast<uint64_t>(0x1.0p-1);
  uint64_t arg3 = bit_cast<uint64_t>(0x1.fffffffffffffp1022);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WWW_ARG("fmadd %d0, %d1, %d2, %d3")(arg1, arg2, arg3);
  ASSERT_EQ(res, bit_cast<uint64_t>(0x1.7ffffffffffff8p1023));
}

TEST(Arm64InsnTest, NegMulAddFp32) {
  constexpr auto AsmFnmadd = ASM_INSN_WRAP_FUNC_W_RES_WWW_ARG("fnmadd %s0, %s1, %s2, %s3");

  __uint128_t res1 =
      AsmFnmadd(bit_cast<uint32_t>(2.0f), bit_cast<uint32_t>(3.0f), bit_cast<uint32_t>(5.0f));
  ASSERT_EQ(res1, MakeF32x4(-11.0f, 0, 0, 0));

  // No -0 (proper negation)
  __uint128_t res2 =
      AsmFnmadd(bit_cast<uint32_t>(2.5f), bit_cast<uint32_t>(2.0f), bit_cast<uint32_t>(-5.0f));
  ASSERT_EQ(res2, MakeF32x4(0.0f, 0, 0, 0));

  // These tests verify that fmadd does not lose precision while doing the mult + add.
  __uint128_t res3 = AsmFnmadd(bit_cast<uint32_t>(0x1.fffffep22f),
                               bit_cast<uint32_t>(0x1.000002p0f),
                               bit_cast<uint32_t>(-0x1.p23f));
  ASSERT_EQ(res3, MakeF32x4(-0x1.fffffcp-2f, 0, 0, 0));

  __uint128_t res4 = AsmFnmadd(bit_cast<uint32_t>(0x1.fffffep22f),
                               bit_cast<uint32_t>(0x1.000002p0f),
                               bit_cast<uint32_t>(-0x1.fffffep22f));
  ASSERT_EQ(res4, MakeF32x4(-0x1.fffffep-1f, 0, 0, 0));

  __uint128_t res5 = AsmFnmadd(bit_cast<uint32_t>(0x1.p23f),
                               bit_cast<uint32_t>(0x1.fffffep-1f),
                               bit_cast<uint32_t>(-0x1.000002p23f));
  ASSERT_EQ(res5, MakeF32x4(0x1.80p0f, 0, 0, 0));
}

TEST(Arm64InsnTest, NegMulAddFp64) {
  constexpr auto AsmFnmadd = ASM_INSN_WRAP_FUNC_W_RES_WWW_ARG("fnmadd %d0, %d1, %d2, %d3");

  __uint128_t res1 =
      AsmFnmadd(bit_cast<uint64_t>(2.0), bit_cast<uint64_t>(3.0), bit_cast<uint64_t>(5.0));
  ASSERT_EQ(res1, MakeF64x2(-11.0, 0));

  // Proper negation (no -0 in this case)
  __uint128_t res2 =
      AsmFnmadd(bit_cast<uint64_t>(2.5), bit_cast<uint64_t>(2.0), bit_cast<uint64_t>(-5.0));
  ASSERT_EQ(res2, MakeF64x2(0.0, 0));
}

TEST(Arm64InsnTest, NegMulSubFp64) {
  constexpr auto AsmFnmsub = ASM_INSN_WRAP_FUNC_W_RES_WWW_ARG("fnmsub %d0, %d1, %d2, %d3");

  __uint128_t res1 =
      AsmFnmsub(bit_cast<uint64_t>(-2.0), bit_cast<uint64_t>(3.0), bit_cast<uint64_t>(5.0));
  ASSERT_EQ(res1, MakeF64x2(-11.0, 0));

  uint64_t arg1 = 0x40357ae147ae147bULL;  // 21.48
  uint64_t arg2 = 0x404ce3d70a3d70a4ull;  // 57.78
  uint64_t arg3 = 0x405e29999999999aULL;  // 120.65
  __uint128_t res2 = AsmFnmsub(arg1, arg2, arg3);
  ASSERT_EQ(res2, MakeUInt128(0x409181db8bac710dULL, 0U));  // 1120.4644

  // Assert no -0 in this case
  __uint128_t res3 =
      AsmFnmsub(bit_cast<uint64_t>(2.5), bit_cast<uint64_t>(2.0), bit_cast<uint64_t>(5.0));
  ASSERT_EQ(res3, MakeF64x2(0.0, 0));
}

TEST(Arm64InsnTest, NegMulSubFp64Precision) {
  constexpr auto AsmFnmsub = ASM_INSN_WRAP_FUNC_W_RES_WWW_ARG("fnmsub %d0, %d1, %d2, %d3");

  __uint128_t res = AsmFnmsub(bit_cast<uint64_t>(0x1.0p1023),
                              bit_cast<uint64_t>(0x1.0p-1),
                              bit_cast<uint64_t>(-0x1.fffffffffffffp1022));
  ASSERT_EQ(res, bit_cast<uint64_t>(0x1.7ffffffffffff8p1023));
}

TEST(Arm64InsnTest, MulAddF32x4) {
  constexpr auto AsmFmla = ASM_INSN_WRAP_FUNC_W_RES_WW0_ARG("fmla %0.4s, %1.4s, %2.4s");
  __uint128_t arg1 = MakeF32x4(1.0f, 2.0f, 4.0f, 3.0f);
  __uint128_t arg2 = MakeF32x4(3.0f, 1.0f, 2.0f, 4.0f);
  __uint128_t arg3 = MakeF32x4(2.0f, 3.0f, 1.0f, 2.0f);
  ASSERT_EQ(AsmFmla(arg1, arg2, arg3), MakeF32x4(5.0f, 5.0f, 9.0f, 14.0f));
}

TEST(Arm64InsnTest, MulAddF32IndexedElem) {
  constexpr auto AsmFmla = ASM_INSN_WRAP_FUNC_W_RES_WW0_ARG("fmla %s0, %s1, %2.s[2]");
  __uint128_t arg1 = MakeF32x4(1.0f, 2.0f, 4.0f, 3.0f);
  __uint128_t arg2 = MakeF32x4(3.0f, 1.0f, 2.0f, 4.0f);
  __uint128_t arg3 = MakeF32x4(2.0f, 3.0f, 1.0f, 2.0f);
  // 2 + (1 * 2)
  ASSERT_EQ(AsmFmla(arg1, arg2, arg3), bit_cast<uint32_t>(4.0f));
}

TEST(Arm64InsnTest, MulAddF64IndexedElem) {
  constexpr auto AsmFmla = ASM_INSN_WRAP_FUNC_W_RES_WW0_ARG("fmla %d0, %d1, %2.d[1]");
  __uint128_t arg1 = MakeF64x2(2.0, 3.0);
  __uint128_t arg2 = MakeF64x2(4.0, 5.0);
  __uint128_t arg3 = MakeF64x2(6.0, 7.0);
  // 6 + (2 * 5)
  ASSERT_EQ(AsmFmla(arg1, arg2, arg3), bit_cast<uint64_t>(16.0));
}

TEST(Arm64InsnTest, MulAddF32x4IndexedElem) {
  constexpr auto AsmFmla = ASM_INSN_WRAP_FUNC_W_RES_WW0_ARG("fmla %0.4s, %1.4s, %2.s[2]");
  __uint128_t arg1 = MakeF32x4(1.0f, 2.0f, 4.0f, 3.0f);
  __uint128_t arg2 = MakeF32x4(3.0f, 1.0f, 2.0f, 4.0f);
  __uint128_t arg3 = MakeF32x4(2.0f, 3.0f, 1.0f, 2.0f);
  ASSERT_EQ(AsmFmla(arg1, arg2, arg3), MakeF32x4(4.0f, 7.0f, 9.0f, 8.0f));
}

TEST(Arm64InsnTest, MulSubFp32) {
  uint32_t arg1 = bit_cast<uint32_t>(2.0f);
  uint32_t arg2 = bit_cast<uint32_t>(5.0f);
  uint32_t arg3 = bit_cast<uint32_t>(3.0f);
  __uint128_t res1 = ASM_INSN_WRAP_FUNC_W_RES_WWW_ARG("fmsub %s0, %s1, %s2, %s3")(arg1, arg2, arg3);
  ASSERT_EQ(res1, MakeUInt128(bit_cast<uint32_t>(-7.0f), 0U));
  __uint128_t res2 =
      ASM_INSN_WRAP_FUNC_W_RES_WWW_ARG("fnmsub %s0, %s1, %s2, %s3")(arg1, arg2, arg3);
  ASSERT_EQ(res2, MakeUInt128(bit_cast<uint32_t>(7.0f), 0U));
}

TEST(Arm64InsnTest, MulSubFp64) {
  constexpr auto AsmFmsub = ASM_INSN_WRAP_FUNC_W_RES_WWW_ARG("fmsub %d0, %d1, %d2, %d3");

  uint64_t arg1 = 0x40357ae147ae147bULL;  // 21.48
  uint64_t arg2 = 0x404ce3d70a3d70a4ull;  // 57.78
  uint64_t arg3 = 0x405e29999999999aULL;  // 120.65
  __uint128_t res1 = AsmFmsub(arg1, arg2, arg3);
  ASSERT_EQ(res1, MakeUInt128(0xc09181db8bac710dULL, 0U));  // -1120.4644

  // Basic case
  __uint128_t res3 =
      AsmFmsub(bit_cast<uint64_t>(2.0), bit_cast<uint64_t>(3.0), bit_cast<uint64_t>(-5.0));
  ASSERT_EQ(res3, MakeF64x2(-11.0, 0));

  // No -0 in this case (proper negation order)
  __uint128_t res4 =
      AsmFmsub(bit_cast<uint64_t>(2.5), bit_cast<uint64_t>(2.0), bit_cast<uint64_t>(5.0));
  ASSERT_EQ(res4, MakeF64x2(0.0, 0));
}

TEST(Arm64InsnTest, MulSubFp64Precision) {
  constexpr auto AsmFmsub = ASM_INSN_WRAP_FUNC_W_RES_WWW_ARG("fmsub %d0, %d1, %d2, %d3");
  __uint128_t res5 = AsmFmsub(bit_cast<uint64_t>(-0x1.0p1023),
                              bit_cast<uint64_t>(0x1.0p-1),
                              bit_cast<uint64_t>(0x1.fffffffffffffp1022));
  ASSERT_EQ(res5, bit_cast<uint64_t>(0x1.7ffffffffffff8p1023));
}

TEST(Arm64InsnTest, MulSubF32x4) {
  constexpr auto AsmFmls = ASM_INSN_WRAP_FUNC_W_RES_WW0_ARG("fmls %0.4s, %1.4s, %2.4s");
  __uint128_t arg1 = MakeF32x4(1.0f, 2.0f, 4.0f, 3.0f);
  __uint128_t arg2 = MakeF32x4(3.0f, 1.0f, 2.0f, 4.0f);
  __uint128_t arg3 = MakeF32x4(2.0f, 3.0f, 1.0f, 2.0f);
  ASSERT_EQ(AsmFmls(arg1, arg2, arg3), MakeF32x4(-1.0f, 1.0f, -7.0f, -10.0f));
}

TEST(Arm64InsnTest, MulSubF32IndexedElem) {
  constexpr auto AsmFmls = ASM_INSN_WRAP_FUNC_W_RES_WW0_ARG("fmls %s0, %s1, %2.s[2]");
  __uint128_t arg1 = MakeF32x4(2.0f, 1.0f, 4.0f, 3.0f);
  __uint128_t arg2 = MakeF32x4(4.0f, 3.0f, 2.0f, 1.0f);
  __uint128_t arg3 = MakeF32x4(8.0f, 3.0f, 1.0f, 2.0f);
  // 8 - (2 * 2)
  ASSERT_EQ(AsmFmls(arg1, arg2, arg3), bit_cast<uint32_t>(4.0f));
}

TEST(Arm64InsnTest, MulSubF64IndexedElem) {
  constexpr auto AsmFmls = ASM_INSN_WRAP_FUNC_W_RES_WW0_ARG("fmls %d0, %d1, %2.d[1]");
  __uint128_t arg1 = MakeF64x2(2.0, 5.0);
  __uint128_t arg2 = MakeF64x2(4.0, 1.0);
  __uint128_t arg3 = MakeF64x2(6.0, 7.0f);
  // 6 - (2 * 1)
  ASSERT_EQ(AsmFmls(arg1, arg2, arg3), bit_cast<uint64_t>(4.0));
}

TEST(Arm64InsnTest, MulSubF32x4IndexedElem) {
  constexpr auto AsmFmls = ASM_INSN_WRAP_FUNC_W_RES_WW0_ARG("fmls %0.4s, %1.4s, %2.s[2]");
  __uint128_t arg1 = MakeF32x4(1.0f, 2.0f, 4.0f, 3.0f);
  __uint128_t arg2 = MakeF32x4(3.0f, 1.0f, 2.0f, 4.0f);
  __uint128_t arg3 = MakeF32x4(2.0f, 3.0f, 1.0f, 2.0f);
  ASSERT_EQ(AsmFmls(arg1, arg2, arg3), MakeF32x4(0.0f, -1.0f, -7.0f, -4.0f));
}

TEST(Arm64InsnTest, CompareEqualF32) {
  constexpr auto AsmFcmeq = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fcmeq %s0, %s1, %s2");
  uint32_t two = bit_cast<uint32_t>(2.0f);
  uint32_t six = bit_cast<uint32_t>(6.0f);
  ASSERT_EQ(AsmFcmeq(two, six), 0x00000000ULL);
  ASSERT_EQ(AsmFcmeq(two, two), 0xffffffffULL);
  ASSERT_EQ(AsmFcmeq(kDefaultNaN32, two), 0x00000000ULL);
  ASSERT_EQ(AsmFcmeq(two, kDefaultNaN32), 0x00000000ULL);
}

TEST(Arm64InsnTest, CompareEqualF32x4) {
  constexpr auto AsmFcmeq = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fcmeq %0.4s, %1.4s, %2.4s");
  __uint128_t arg1 = MakeF32x4(-3.0f, 2.0f, 7.0f, -0.0f);
  __uint128_t arg2 = MakeF32x4(6.0f, 2.0f, -8.0f, 5.0f);
  __uint128_t res = AsmFcmeq(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0xffffffff00000000ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, CompareGreaterEqualF32) {
  constexpr auto AsmFcmge = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fcmge %s0, %s1, %s2");
  uint32_t two = bit_cast<uint32_t>(2.0f);
  uint32_t six = bit_cast<uint32_t>(6.0f);
  ASSERT_EQ(AsmFcmge(two, six), 0x00000000ULL);
  ASSERT_EQ(AsmFcmge(two, two), 0xffffffffULL);
  ASSERT_EQ(AsmFcmge(six, two), 0xffffffffULL);
  ASSERT_EQ(AsmFcmge(kDefaultNaN32, two), 0x00000000ULL);
  ASSERT_EQ(AsmFcmge(two, kDefaultNaN32), 0x00000000ULL);
}

TEST(Arm64InsnTest, CompareGreaterEqualF32x4) {
  constexpr auto AsmFcmge = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fcmge %0.4s, %1.4s, %2.4s");
  __uint128_t arg1 = MakeF32x4(-3.0f, 2.0f, 7.0f, -0.0f);
  __uint128_t arg2 = MakeF32x4(6.0f, 2.0f, -8.0f, 5.0f);
  __uint128_t res = AsmFcmge(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0xffffffff00000000ULL, 0x00000000ffffffffULL));
}

TEST(Arm64InsnTest, CompareGreaterF32) {
  constexpr auto AsmFcmgt = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fcmgt %s0, %s1, %s2");
  uint32_t two = bit_cast<uint32_t>(2.0f);
  uint32_t six = bit_cast<uint32_t>(6.0f);
  ASSERT_EQ(AsmFcmgt(two, six), 0x00000000ULL);
  ASSERT_EQ(AsmFcmgt(two, two), 0x00000000ULL);
  ASSERT_EQ(AsmFcmgt(six, two), 0xffffffffULL);
  ASSERT_EQ(AsmFcmgt(kDefaultNaN32, two), 0x00000000ULL);
  ASSERT_EQ(AsmFcmgt(two, kDefaultNaN32), 0x00000000ULL);
}

TEST(Arm64InsnTest, CompareGreaterF32x4) {
  constexpr auto AsmFcmgt = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fcmgt %0.4s, %1.4s, %2.4s");
  __uint128_t arg1 = MakeF32x4(-3.0f, 2.0f, 7.0f, -0.0f);
  __uint128_t arg2 = MakeF32x4(6.0f, 2.0f, -8.0f, 5.0f);
  __uint128_t res = AsmFcmgt(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x0000000000000000ULL, 0x00000000ffffffffULL));
}

TEST(Arm64InsnTest, CompareEqualZeroF32) {
  constexpr auto AsmFcmeq = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcmeq %s0, %s1, #0");
  ASSERT_EQ(AsmFcmeq(bit_cast<uint32_t>(0.0f)), 0xffffffffULL);
  ASSERT_EQ(AsmFcmeq(bit_cast<uint32_t>(4.0f)), 0x00000000ULL);
}

TEST(Arm64InsnTest, CompareEqualZeroF32x4) {
  constexpr auto AsmFcmeq = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcmeq %0.4s, %1.4s, #0");
  __uint128_t arg = MakeF32x4(-3.0f, 0.0f, 7.0f, 1.0f);
  __uint128_t res = AsmFcmeq(arg);
  ASSERT_EQ(res, MakeUInt128(0xffffffff00000000ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, CompareGreaterThanZeroF32) {
  constexpr auto AsmFcmgt = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcmgt %s0, %s1, #0");
  ASSERT_EQ(AsmFcmgt(bit_cast<uint32_t>(-1.0f)), 0x00000000ULL);
  ASSERT_EQ(AsmFcmgt(bit_cast<uint32_t>(0.0f)), 0x00000000ULL);
  ASSERT_EQ(AsmFcmgt(bit_cast<uint32_t>(1.0f)), 0xffffffffULL);
}

TEST(Arm64InsnTest, CompareGreaterThanZeroF32x4) {
  constexpr auto AsmFcmgt = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcmgt %0.4s, %1.4s, #0");
  __uint128_t arg = MakeF32x4(-3.0f, 0.0f, 7.0f, 1.0f);
  __uint128_t res = AsmFcmgt(arg);
  ASSERT_EQ(res, MakeUInt128(0x0000000000000000ULL, 0xffffffffffffffffULL));
}

TEST(Arm64InsnTest, CompareGreaterThanOrEqualZeroF32) {
  constexpr auto AsmFcmge = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcmge %s0, %s1, #0");
  ASSERT_EQ(AsmFcmge(bit_cast<uint32_t>(-1.0f)), 0x00000000ULL);
  ASSERT_EQ(AsmFcmge(bit_cast<uint32_t>(0.0f)), 0xffffffffULL);
  ASSERT_EQ(AsmFcmge(bit_cast<uint32_t>(1.0f)), 0xffffffffULL);
}

TEST(Arm64InsnTest, CompareGreaterThanOrEqualZeroF32x4) {
  constexpr auto AsmFcmge = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcmge %0.4s, %1.4s, #0");
  __uint128_t arg = MakeF32x4(-3.0f, 0.0f, 7.0f, 1.0f);
  __uint128_t res = AsmFcmge(arg);
  ASSERT_EQ(res, MakeUInt128(0xffffffff00000000ULL, 0xffffffffffffffffULL));
}

TEST(Arm64InsnTest, CompareLessThanZeroF32) {
  constexpr auto AsmFcmlt = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcmlt %s0, %s1, #0");
  ASSERT_EQ(AsmFcmlt(bit_cast<uint32_t>(-1.0f)), 0xffffffffULL);
  ASSERT_EQ(AsmFcmlt(bit_cast<uint32_t>(0.0f)), 0x00000000ULL);
  ASSERT_EQ(AsmFcmlt(bit_cast<uint32_t>(1.0f)), 0x00000000ULL);
}

TEST(Arm64InsnTest, CompareLessThanZeroF32x4) {
  constexpr auto AsmFcmlt = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcmlt %0.4s, %1.4s, #0");
  __uint128_t arg = MakeF32x4(-3.0f, 0.0f, 7.0f, 1.0f);
  __uint128_t res = AsmFcmlt(arg);
  ASSERT_EQ(res, MakeUInt128(0x00000000ffffffffULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, CompareLessThanOrEqualZeroF32) {
  constexpr auto AsmFcmle = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcmle %s0, %s1, #0");
  ASSERT_EQ(AsmFcmle(bit_cast<uint32_t>(-1.0f)), 0xffffffffULL);
  ASSERT_EQ(AsmFcmle(bit_cast<uint32_t>(0.0f)), 0xffffffffULL);
  ASSERT_EQ(AsmFcmle(bit_cast<uint32_t>(1.0f)), 0x00000000ULL);
}

TEST(Arm64InsnTest, CompareLessThanOrEqualZeroF32x4) {
  constexpr auto AsmFcmle = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fcmle %0.4s, %1.4s, #0");
  __uint128_t arg = MakeF32x4(-3.0f, 0.0f, 7.0f, 1.0f);
  __uint128_t res = AsmFcmle(arg);
  ASSERT_EQ(res, MakeUInt128(0xffffffffffffffffULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, AbsoluteCompareGreaterThanF32) {
  constexpr auto AsmFacgt = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("facgt %s0, %s1, %s2");
  ASSERT_EQ(AsmFacgt(bit_cast<uint32_t>(-3.0f), bit_cast<uint32_t>(1.0f)), 0xffffffffULL);
  ASSERT_EQ(AsmFacgt(bit_cast<uint32_t>(1.0f), bit_cast<uint32_t>(-1.0f)), 0x00000000ULL);
  ASSERT_EQ(AsmFacgt(bit_cast<uint32_t>(3.0f), bit_cast<uint32_t>(-7.0f)), 0x00000000ULL);
}

TEST(Arm64InsnTest, AbsoluteCompareGreaterThanOrEqualF32) {
  constexpr auto AsmFacge = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("facge %s0, %s1, %s2");
  ASSERT_EQ(AsmFacge(bit_cast<uint32_t>(-3.0f), bit_cast<uint32_t>(1.0f)), 0xffffffffULL);
  ASSERT_EQ(AsmFacge(bit_cast<uint32_t>(1.0f), bit_cast<uint32_t>(-1.0f)), 0xffffffffULL);
  ASSERT_EQ(AsmFacge(bit_cast<uint32_t>(3.0f), bit_cast<uint32_t>(-7.0f)), 0x00000000ULL);
}

TEST(Arm64InsnTest, AbsoluteCompareGreaterThanF32x4) {
  constexpr auto AsmFacgt = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("facgt %0.4s, %1.4s, %2.4s");
  __uint128_t arg1 = MakeF32x4(-3.0f, 1.0f, 3.0f, 4.0f);
  __uint128_t arg2 = MakeF32x4(1.0f, -1.0f, -7.0f, 2.0f);
  ASSERT_EQ(AsmFacgt(arg1, arg2), MakeUInt128(0x00000000ffffffffULL, 0xffffffff00000000ULL));
}

TEST(Arm64InsnTest, AbsoluteCompareGreaterThanEqualF32x4) {
  constexpr auto AsmFacge = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("facge %0.4s, %1.4s, %2.4s");
  __uint128_t arg1 = MakeF32x4(-3.0f, 1.0f, 3.0f, 4.0f);
  __uint128_t arg2 = MakeF32x4(1.0f, -1.0f, -7.0f, 2.0f);
  ASSERT_EQ(AsmFacge(arg1, arg2), MakeUInt128(0xffffffffffffffffULL, 0xffffffff00000000ULL));
}

TEST(Arm64InsnTest, CompareEqualF64) {
  constexpr auto AsmFcmeq = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fcmeq %d0, %d1, %d2");
  uint64_t two = bit_cast<uint64_t>(2.0);
  uint64_t six = bit_cast<uint64_t>(6.0);
  ASSERT_EQ(AsmFcmeq(two, six), 0x0000000000000000ULL);
  ASSERT_EQ(AsmFcmeq(two, two), 0xffffffffffffffffULL);
  ASSERT_EQ(AsmFcmeq(kDefaultNaN64, two), 0x0000000000000000ULL);
  ASSERT_EQ(AsmFcmeq(two, kDefaultNaN64), 0x0000000000000000ULL);
}

TEST(Arm64InsnTest, CompareEqualF64x2) {
  constexpr auto AsmFcmeq = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fcmeq %0.2d, %1.2d, %2.2d");
  __uint128_t arg1 = MakeF64x2(-3.0, 2.0);
  __uint128_t arg2 = MakeF64x2(6.0, 2.0);
  __uint128_t res = AsmFcmeq(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x0000000000000000ULL, 0xffffffffffffffffULL));
  arg1 = MakeF64x2(7.0, -0.0);
  arg2 = MakeF64x2(-8.0, 5.0);
  res = AsmFcmeq(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x0000000000000000ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, CompareGreaterEqualF64) {
  constexpr auto AsmFcmge = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fcmge %d0, %d1, %d2");
  uint64_t two = bit_cast<uint64_t>(2.0);
  uint64_t six = bit_cast<uint64_t>(6.0);
  ASSERT_EQ(AsmFcmge(two, six), 0x0000000000000000ULL);
  ASSERT_EQ(AsmFcmge(two, two), 0xffffffffffffffffULL);
  ASSERT_EQ(AsmFcmge(six, two), 0xffffffffffffffffULL);
  ASSERT_EQ(AsmFcmge(kDefaultNaN64, two), 0x0000000000000000ULL);
  ASSERT_EQ(AsmFcmge(two, kDefaultNaN64), 0x0000000000000000ULL);
}

TEST(Arm64InsnTest, CompareGreaterEqualF64x2) {
  constexpr auto AsmFcmge = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fcmge %0.2d, %1.2d, %2.2d");
  __uint128_t arg1 = MakeF64x2(-3.0, 2.0);
  __uint128_t arg2 = MakeF64x2(6.0, 2.0);
  __uint128_t res = AsmFcmge(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x0000000000000000ULL, 0xffffffffffffffffULL));
  arg1 = MakeF64x2(7.0, -0.0);
  arg2 = MakeF64x2(-8.0, 5.0);
  res = AsmFcmge(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0xffffffffffffffffULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, CompareGreaterF64) {
  constexpr auto AsmFcmgt = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fcmgt %d0, %d1, %d2");
  uint64_t two = bit_cast<uint64_t>(2.0);
  uint64_t six = bit_cast<uint64_t>(6.0);
  ASSERT_EQ(AsmFcmgt(two, six), 0x0000000000000000ULL);
  ASSERT_EQ(AsmFcmgt(two, two), 0x0000000000000000ULL);
  ASSERT_EQ(AsmFcmgt(six, two), 0xffffffffffffffffULL);
  ASSERT_EQ(AsmFcmgt(kDefaultNaN64, two), 0x0000000000000000ULL);
  ASSERT_EQ(AsmFcmgt(two, kDefaultNaN64), 0x0000000000000000ULL);
}

TEST(Arm64InsnTest, CompareGreaterF64x2) {
  constexpr auto AsmFcmgt = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fcmgt %0.2d, %1.2d, %2.2d");
  __uint128_t arg1 = MakeF64x2(-3.0, 2.0);
  __uint128_t arg2 = MakeF64x2(6.0, 2.0);
  __uint128_t res = AsmFcmgt(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x0000000000000000ULL, 0x0000000000000000ULL));
  arg1 = MakeF64x2(7.0, -0.0);
  arg2 = MakeF64x2(-8.0, 5.0);
  res = AsmFcmgt(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0xffffffffffffffffULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, AndInt8x16) {
  __uint128_t op1 = MakeUInt128(0x7781857780532171ULL, 0x2268066130019278ULL);
  __uint128_t op2 = MakeUInt128(0x0498862723279178ULL, 0x6085784383827967ULL);
  __uint128_t rd = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("and %0.16b, %1.16b, %2.16b")(op1, op2);
  ASSERT_EQ(rd, MakeUInt128(0x0480842700030170ULL, 0x2000004100001060ULL));
}

TEST(Arm64InsnTest, AndInt8x8) {
  __uint128_t op1 = MakeUInt128(0x7781857780532171ULL, 0x2268066130019278ULL);
  __uint128_t op2 = MakeUInt128(0x0498862723279178ULL, 0x6085784383827967ULL);
  __uint128_t rd = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("and %0.8b, %1.8b, %2.8b")(op1, op2);
  ASSERT_EQ(rd, MakeUInt128(0x0480842700030170ULL, 0));
}

TEST(Arm64InsnTest, OrInt8x16) {
  __uint128_t op1 = MakeUInt128(0x00ffaa5500112244ULL, 0x1248124812481248ULL);
  __uint128_t op2 = MakeUInt128(0x44221100ffaa5500ULL, 0x1122448811224488ULL);
  __uint128_t rd = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("orr %0.16b, %1.16b, %2.16b")(op1, op2);
  ASSERT_EQ(rd, MakeUInt128(0x44ffbb55ffbb7744ULL, 0x136a56c8136a56c8ULL));
}

TEST(Arm64InsnTest, OrInt8x8) {
  __uint128_t op1 = MakeUInt128(0x00ffaa5500112244ULL, 0x1248124812481248ULL);
  __uint128_t op2 = MakeUInt128(0x44221100ffaa5500ULL, 0x1122448811224488ULL);
  __uint128_t rd = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("orr %0.8b, %1.8b, %2.8b")(op1, op2);
  ASSERT_EQ(rd, MakeUInt128(0x44ffbb55ffbb7744ULL, 0));
}

TEST(Arm64InsnTest, XorInt8x16) {
  __uint128_t op1 = MakeUInt128(0x1050792279689258ULL, 0x9235420199561121ULL);
  __uint128_t op2 = MakeUInt128(0x8239864565961163ULL, 0x5488623057745649ULL);
  __uint128_t rd = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("eor %0.16b, %1.16b, %2.16b")(op1, op2);
  ASSERT_EQ(rd, MakeUInt128(0x9269ff671cfe833bULL, 0xc6bd2031ce224768ULL));
}

TEST(Arm64InsnTest, XorInt8x8) {
  __uint128_t op1 = MakeUInt128(0x1050792279689258ULL, 0x9235420199561121ULL);
  __uint128_t op2 = MakeUInt128(0x8239864565961163ULL, 0x5488623057745649ULL);
  __uint128_t rd = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("eor %0.8b, %1.8b, %2.8b")(op1, op2);
  ASSERT_EQ(rd, MakeUInt128(0x9269ff671cfe833bULL, 0));
}

TEST(Arm64InsnTest, AndNotInt8x16) {
  __uint128_t op1 = MakeUInt128(0x0313783875288658ULL, 0x7533208381420617ULL);
  __uint128_t op2 = MakeUInt128(0x2327917860857843ULL, 0x8382796797668145ULL);
  __uint128_t rd = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("bic %0.16b, %1.16b, %2.16b")(op1, op2);
  ASSERT_EQ(rd, MakeUInt128(0x0010680015288618ULL, 0x7431008000000612ULL));
}

TEST(Arm64InsnTest, AndNotInt8x8) {
  __uint128_t op1 = MakeUInt128(0x4861045432664821ULL, 0x2590360011330530ULL);
  __uint128_t op2 = MakeUInt128(0x5420199561121290ULL, 0x8572424541506959ULL);
  __uint128_t rd = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("bic %0.8b, %1.8b, %2.8b")(op1, op2);
  ASSERT_EQ(rd, MakeUInt128(0x0841044012644821ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, AndNotInt16x4Imm) {
  __uint128_t res = MakeUInt128(0x9690314950191085ULL, 0x7598442391986291ULL);

  asm("bic %0.4h, #0x3" : "=w"(res) : "0"(res));

  ASSERT_EQ(res, MakeUInt128(0x9690314850181084ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, AndNotInt16x4ImmShiftedBy8) {
  __uint128_t res = MakeUInt128(0x8354056704038674ULL, 0x3513622224771589ULL);

  asm("bic %0.4h, #0xa8, lsl #8" : "=w"(res) : "0"(res));

  ASSERT_EQ(res, MakeUInt128(0x0354056704030674ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, AndNotInt32x2ImmShiftedBy8) {
  __uint128_t res = MakeUInt128(0x1842631298608099ULL, 0x8886874132604721ULL);

  asm("bic %0.2s, #0xd3, lsl #8" : "=w"(res) : "0"(res));

  ASSERT_EQ(res, MakeUInt128(0x1842201298600099ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, AndNotInt32x2ImmShiftedBy16) {
  __uint128_t res = MakeUInt128(0x2947867242292465ULL, 0x4366800980676928ULL);

  asm("bic %0.2s, #0x22, lsl #16" : "=w"(res) : "0"(res));

  ASSERT_EQ(res, MakeUInt128(0x2945867242092465ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, AndNotInt32x2ImmShiftedBy24) {
  __uint128_t res = MakeUInt128(0x0706977942236250ULL, 0x8221688957383798ULL);

  asm("bic %0.2s, #0x83, lsl #24" : "=w"(res) : "0"(res));

  ASSERT_EQ(res, MakeUInt128(0x0406977940236250ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, OrInt16x4Imm) {
  __uint128_t res = MakeUInt128(0x0841284886269456ULL, 0x0424196528502221ULL);

  asm("orr %0.4h, #0x5" : "=w"(res) : "0"(res));

  ASSERT_EQ(res, MakeUInt128(0x0845284d86279457ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, OrNotInt8x16) {
  __uint128_t op1 = MakeUInt128(0x5428584447952658ULL, 0x6782105114135473ULL);
  __uint128_t op2 = MakeUInt128(0x3558764024749647ULL, 0x3263914199272604ULL);
  __uint128_t rd = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("orn %0.16b, %1.16b, %2.16b")(op1, op2);
  ASSERT_EQ(rd, MakeUInt128(0xdeafd9ffdf9f6ff8ULL, 0xef9e7eff76dbddfbULL));
}

TEST(Arm64InsnTest, OrNotInt8x8) {
  __uint128_t op1 = MakeUInt128(0x3279178608578438ULL, 0x3827967976681454ULL);
  __uint128_t op2 = MakeUInt128(0x6838689427741559ULL, 0x9185592524595395ULL);
  __uint128_t rd = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("orn %0.8b, %1.8b, %2.8b")(op1, op2);
  ASSERT_EQ(rd, MakeUInt128(0xb7ff97efd8dfeebeULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, BitwiseSelectInt8x8) {
  __uint128_t op1 = MakeUInt128(0x2000568127145263ULL, 0x5608277857713427ULL);
  __uint128_t op2 = MakeUInt128(0x0792279689258923ULL, 0x5420199561121290ULL);
  __uint128_t op3 = MakeUInt128(0x8372978049951059ULL, 0x7317328160963185ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW0_ARG("bsl %0.8b, %1.8b, %2.8b")(op1, op2, op3);
  ASSERT_EQ(res, MakeUInt128(0x0480369681349963ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, BitwiseInsertIfTrueInt8x8) {
  __uint128_t op1 = MakeUInt128(0x3678925903600113ULL, 0x3053054882046652ULL);
  __uint128_t op2 = MakeUInt128(0x9326117931051185ULL, 0x4807446237996274ULL);
  __uint128_t op3 = MakeUInt128(0x6430860213949463ULL, 0x9522473719070217ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW0_ARG("bit %0.8b, %1.8b, %2.8b")(op1, op2, op3);
  ASSERT_EQ(res, MakeUInt128(0x7630965b03908563ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, BitwiseInsertIfFalseInt8x8) {
  __uint128_t op1 = MakeUInt128(0x7067982148086513ULL, 0x2823066470938446ULL);
  __uint128_t op2 = MakeUInt128(0x5964462294895493ULL, 0x0381964428810975ULL);
  __uint128_t op3 = MakeUInt128(0x0348610454326648ULL, 0x2133936072602491ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW0_ARG("bif %0.8b, %1.8b, %2.8b")(op1, op2, op3);
  ASSERT_EQ(res, MakeUInt128(0x2143d8015c006500ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, ArithmeticShiftRightInt64x1) {
  __uint128_t arg = MakeUInt128(0x9486015046652681ULL, 0x4398770516153170ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("sshr %d0, %d1, #39")(arg);
  ASSERT_EQ(res, MakeUInt128(0xffffffffff290c02ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, ArithmeticShiftRightBy64Int64x1) {
  __uint128_t arg = MakeUInt128(0x9176042601763387ULL, 0x0454990176143641ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("sshr %d0, %d1, #64")(arg);
  ASSERT_EQ(res, MakeUInt128(0xffffffffffffffffULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, ArithmeticShiftRightInt64x2) {
  __uint128_t arg = MakeUInt128(0x7501116498327856ULL, 0x3531614516845769ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("sshr %0.2d, %1.2d, #35")(arg);
  ASSERT_EQ(res, MakeUInt128(0x000000000ea0222cULL, 0x0000000006a62c28ULL));
}

TEST(Arm64InsnTest, ArithmeticShiftRightAccumulateInt64x1) {
  __uint128_t arg1 = MakeUInt128(0x9667179643468760ULL, 0x0770479995378833ULL);
  __uint128_t arg2 = MakeUInt128(0x2557176908196030ULL, 0x9201824018842705ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W0_ARG("ssra %d0, %d1, #40")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x2557176907afc747ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, ArithmeticShiftRightBy64AccumulateInt64x1) {
  __uint128_t arg1 = MakeUInt128(0x9223343657791601ULL, 0x2809317940171859ULL);
  __uint128_t arg2 = MakeUInt128(0x3498025249906698ULL, 0x4233017350358044ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W0_ARG("ssra %d0, %d1, #64")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x3498025249906697ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, ArithmeticShiftRightAccumulateInt16x8) {
  __uint128_t arg1 = MakeUInt128(0x9276457931065792ULL, 0x2955249887275846ULL);
  __uint128_t arg2 = MakeUInt128(0x0101655256375678ULL, 0x5667227966198857ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W0_ARG("ssra %0.8h, %1.8h, #12")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x00fa6556563a567dULL, 0x5669227b6611885cULL));
}

TEST(Arm64InsnTest, ArithmeticRoundingShiftRightAccumulateInt16x8) {
  __uint128_t arg1 = MakeUInt128(0x9894671543578468ULL, 0x7886144458123145ULL);
  __uint128_t arg2 = MakeUInt128(0x1412147805734551ULL, 0x0500801908699603ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W0_ARG("srsra %0.8h, %1.8h, #12")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x140c147e05774549ULL, 0x0508801a086f9606ULL));
}

TEST(Arm64InsnTest, LogicalShiftRightInt64x1) {
  __uint128_t arg = MakeUInt128(0x9859771921805158ULL, 0x5321473926532515ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("ushr %d0, %d1, #33")(arg);
  ASSERT_EQ(res, MakeUInt128(0x000000004c2cbb8cULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, LogicalShiftRightBy64Int64x1) {
  __uint128_t arg = MakeUInt128(0x9474696134360928ULL, 0x6148494178501718ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("ushr %d0, %d1, #64")(arg);
  ASSERT_EQ(res, MakeUInt128(0x0000000000000000ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, LogicalShiftRightInt64x2) {
  __uint128_t op = MakeUInt128(0x3962657978771855ULL, 0x6084552965412665ULL);
  __uint128_t rd = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("ushr %0.2d, %1.2d, #33")(op);
  ASSERT_EQ(rd, MakeUInt128(0x000000001cb132bcULL, 0x0000000030422a94ULL));
}

TEST(Arm64InsnTest, LogicalShiftRightAccumulateInt64x1) {
  __uint128_t arg1 = MakeUInt128(0x9004112453790153ULL, 0x3296615697052237ULL);
  __uint128_t arg2 = MakeUInt128(0x0499939532215362ULL, 0x2748476603613677ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W0_ARG("usra %d0, %d1, #40")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x0499939532b15773ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, LogicalShiftRightBy64AccumulateInt64x1) {
  __uint128_t arg1 = MakeUInt128(0x9886592578662856ULL, 0x1249665523533829ULL);
  __uint128_t arg2 = MakeUInt128(0x3559152534784459ULL, 0x8183134112900199ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W0_ARG("usra %d0, %d1, #64")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x3559152534784459ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, LogicalShiftRightAccumulateInt16x8) {
  __uint128_t arg1 = MakeUInt128(0x9984345225161050ULL, 0x7027056235266012ULL);
  __uint128_t arg2 = MakeUInt128(0x4628654036036745ULL, 0x3286510570658748ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W0_ARG("usra %0.8h, %1.8h, #12")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x4631654336056746ULL, 0x328d51057068874eULL));
}

TEST(Arm64InsnTest, LogicalRoundingShiftRightAccumulateInt16x8) {
  __uint128_t arg1 = MakeUInt128(0x9843452251610507ULL, 0x0270562352660127ULL);
  __uint128_t arg2 = MakeUInt128(0x6286540360367453ULL, 0x2865105706587488ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W0_ARG("srsra %0.8h, %1.8h, #12")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x62805407603b7453ULL, 0x2865105c065d7488ULL));
}

TEST(Arm64InsnTest, SignedRoundingShiftRightInt64x1) {
  __uint128_t arg = MakeUInt128(0x9323685785585581ULL, 0x9555604215625088ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("srshr %d0, %d1, #40")(arg);
  ASSERT_EQ(res, MakeUInt128(0xffffffffff932368ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, SignedRoundingShiftRightInt64x2) {
  __uint128_t arg = MakeUInt128(0x8714878398908107ULL, 0x4295309410605969ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("srshr %0.2d, %1.2d, #36")(arg);
  ASSERT_EQ(res, MakeUInt128(0xfffffffff8714878ULL, 0x0000000004295309ULL));
}

TEST(Arm64InsnTest, SignedRoundingShiftRightAccumulateInt64x1) {
  __uint128_t arg1 = MakeUInt128(0x9946016520577405ULL, 0x2942305360178031ULL);
  __uint128_t arg2 = MakeUInt128(0x3960188013782542ULL, 0x1927094767337191ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W0_ARG("srsra %d0, %d1, #33")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x3960187fe01b25f5ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, UnsignedRoundingShiftRightInt64x1) {
  __uint128_t arg = MakeUInt128(0x9713552208445285ULL, 0x2640081252027665ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("urshr %d0, %d1, #33")(arg);
  ASSERT_EQ(res, MakeUInt128(0x000000004b89aa91ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, UnsignedRoundingShiftRightInt64x2) {
  __uint128_t arg = MakeUInt128(0x6653398573888786ULL, 0x6147629443414010ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("urshr %0.2d, %1.2d, #34")(arg);
  ASSERT_EQ(res, MakeUInt128(0x000000001994ce61ULL, 0x000000001851d8a5ULL));
}

TEST(Arm64InsnTest, UnsignedRoundingShiftRightAccumulateInt64x1) {
  __uint128_t arg1 = MakeUInt128(0x9616143204006381ULL, 0x3224658411111577ULL);
  __uint128_t arg2 = MakeUInt128(0x7184728147519983ULL, 0x5050478129771859ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W0_ARG("ursra %d0, %d1, #33")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x71847281925ca39cULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, ShiftLeftInt64x1) {
  __uint128_t arg = MakeUInt128(0x3903594664691623ULL, 0x5396809201394578ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("shl %d0, %d1, #35")(arg);
  ASSERT_EQ(res, MakeUInt128(0x2348b11800000000ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, ShiftLeftInt64x2) {
  __uint128_t arg = MakeUInt128(0x0750111649832785ULL, 0x6353161451684576ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("shl %0.2d, %1.2d, #37")(arg);
  ASSERT_EQ(res, MakeUInt128(0x3064f0a000000000ULL, 0x2d08aec000000000ULL));
}

TEST(Arm64InsnTest, ShiftLeftInt8x8) {
  __uint128_t arg = MakeUInt128(0x0402956047346131ULL, 0x1382638788975517ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("shl %0.8b, %1.8b, #6")(arg);
  ASSERT_EQ(res, MakeUInt128(0x00804000c0004040ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, ShiftRightInsertInt64x1) {
  __uint128_t arg1 = MakeUInt128(0x9112232618794059ULL, 0x9415540632701319ULL);
  __uint128_t arg2 = MakeUInt128(0x1537675115830432ULL, 0x0849872092028092ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W0_ARG("sri %d0, %d1, #20")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x1537691122326187ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, ShiftRightInsertInt64x2) {
  __uint128_t arg1 = MakeUInt128(0x7332335603484653ULL, 0x1873029302665964ULL);
  __uint128_t arg2 = MakeUInt128(0x5013718375428897ULL, 0x5579714499246540ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W0_ARG("sri %0.2d, %1.2d, #21")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x50137399919ab01aULL, 0x557970c398149813ULL));
}

TEST(Arm64InsnTest, ShiftLeftInsertInt64x1) {
  __uint128_t arg1 = MakeUInt128(0x3763526969344354ULL, 0x4004730671988689ULL);
  __uint128_t arg2 = MakeUInt128(0x6369498567302175ULL, 0x2313252926537589ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W0_ARG("sli %d0, %d1, #23")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x34b49a21aa302175ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, ShiftLeftInsertInt64x2) {
  __uint128_t arg1 = MakeUInt128(0x3270206902872323ULL, 0x3005386216347988ULL);
  __uint128_t arg2 = MakeUInt128(0x5094695472004795ULL, 0x2311201504329322ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W0_ARG("sli %0.2d, %1.2d, #21")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x0d2050e464604795ULL, 0x0c42c68f31129322ULL));
}

TEST(Arm64InsnTest, ShiftLeftLongInt8x8) {
  __uint128_t arg = MakeUInt128(0x2650697620201995ULL, 0x5484126500053944ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("shll %0.8h, %1.8b, #8")(arg);
  ASSERT_EQ(res, MakeUInt128(0x2000200019009500ULL, 0x2600500069007600ULL));
}

TEST(Arm64InsnTest, ShiftLeftLongInt8x8Upper) {
  __uint128_t arg = MakeUInt128(0x9050429225978771ULL, 0x0667873840000616ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("shll2 %0.8h, %1.16b, #8")(arg);
  ASSERT_EQ(res, MakeUInt128(0x4000000006001600ULL, 0x0600670087003800ULL));
}

TEST(Arm64InsnTest, SignedShiftLeftLongInt32x2) {
  __uint128_t arg = MakeUInt128(0x9075407923424023ULL, 0x0092590070173196ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("sshll %0.2d, %1.2s, #9")(arg);
  ASSERT_EQ(res, MakeUInt128(0x0000004684804600ULL, 0xffffff20ea80f200ULL));
}

TEST(Arm64InsnTest, SignedShiftLeftLongInt32x2Upper) {
  __uint128_t arg = MakeUInt128(0x9382432227188515ULL, 0x9740547021482897ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("sshll2 %0.2d, %1.4s, #9")(arg);
  ASSERT_EQ(res, MakeUInt128(0x0000004290512e00ULL, 0xffffff2e80a8e000ULL));
}

TEST(Arm64InsnTest, SignedShiftLeftLongInt32x2By0) {
  __uint128_t arg = MakeUInt128(0x9008777697763127ULL, 0x9572267265556259ULL);
  // SXTL is an alias for SSHLL for the shift count being zero.
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("sxtl %0.2d, %1.2s")(arg);
  ASSERT_EQ(res, MakeUInt128(0xffffffff97763127ULL, 0xffffffff90087776ULL));
}

TEST(Arm64InsnTest, ShiftLeftLongInt32x2) {
  __uint128_t arg = MakeUInt128(0x9094334676851422ULL, 0x1447737939375170ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("ushll %0.2d, %1.2s, #9")(arg);
  ASSERT_EQ(res, MakeUInt128(0x000000ed0a284400ULL, 0x0000012128668c00ULL));
}

TEST(Arm64InsnTest, ShiftLeftLongInt32x2Upper) {
  __uint128_t arg = MakeUInt128(0x7096834080053559ULL, 0x8491754173818839ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("ushll2 %0.2d, %1.4s, #17")(arg);
  ASSERT_EQ(res, MakeUInt128(0x0000e70310720000ULL, 0x00010922ea820000ULL));
}

TEST(Arm64InsnTest, ShiftLeftLongInt32x2By0) {
  __uint128_t arg = MakeUInt128(0x9945681506526530ULL, 0x5371829412703369ULL);
  // UXTL is an alias for USHLL for the shift count being zero.
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("uxtl %0.2d, %1.2s")(arg);
  ASSERT_EQ(res, MakeUInt128(0x0000000006526530ULL, 0x0000000099456815ULL));
}

TEST(Arm64InsnTest, ShiftRightNarrowI16x8) {
  __uint128_t arg = MakeUInt128(0x9378541786109696ULL, 0x9202538865034577ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("shrn %0.8b, %1.8h, #2")(arg);
  ASSERT_EQ(res, MakeUInt128(0x80e2405dde0584a5ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, ShiftRightNarrowI16x8Upper) {
  __uint128_t arg1 = MakeUInt128(0x9779940012601642ULL, 0x2760926082349304ULL);
  __uint128_t arg2 = MakeUInt128(0x3879158299848645ULL, 0x9271734059225620ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W0_ARG("shrn2 %0.16b, %1.8h, #2")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x3879158299848645ULL, 0xd8988dc1de009890ULL));
}

TEST(Arm64InsnTest, RoundingShiftRightNarrowI16x8) {
  __uint128_t arg = MakeUInt128(0x9303774688099929ULL, 0x6877582441047878ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("rshrn %0.8b, %1.8h, #2")(arg);
  ASSERT_EQ(res, MakeUInt128(0x1e09411ec1d2024aULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, RoundingShiftRightNarrowI16x8Upper) {
  __uint128_t arg1 = MakeUInt128(0x9314507607167064ULL, 0x3556827437743965ULL);
  __uint128_t arg2 = MakeUInt128(0x2103098604092717ULL, 0x0909512808630902ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W0_ARG("rshrn2 %0.16b, %1.8h, #2")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x2103098604092717ULL, 0x569ddd59c51ec619ULL));
}

TEST(Arm64InsnTest, AddInt64x1) {
  __uint128_t arg1 = MakeUInt128(0x0080000000000003ULL, 0xdeadbeef01234567ULL);
  __uint128_t arg2 = MakeUInt128(0x0080000000000005ULL, 0x0123deadbeef4567ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("add %d0, %d1, %d2")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x0100000000000008ULL, 0x0ULL));
}

TEST(Arm64InsnTest, AddInt32x4) {
  // The "add" below adds two vectors, each with four 32-bit elements.  We set the sign
  // bit for each element to verify that the carry does not affect any lane.
  __uint128_t op1 = MakeUInt128(0x8000000380000001ULL, 0x8000000780000005ULL);
  __uint128_t op2 = MakeUInt128(0x8000000480000002ULL, 0x8000000880000006ULL);
  __uint128_t rd = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("add %0.4s, %1.4s, %2.4s")(op1, op2);
  ASSERT_EQ(rd, MakeUInt128(0x0000000700000003ULL, 0x0000000f0000000bULL));
}

TEST(Arm64InsnTest, AddInt32x2) {
  __uint128_t op1 = MakeUInt128(0x8000000380000001ULL, 0x8000000780000005ULL);
  __uint128_t op2 = MakeUInt128(0x8000000480000002ULL, 0x8000000880000006ULL);
  __uint128_t rd = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("add %0.2s, %1.2s, %2.2s")(op1, op2);
  ASSERT_EQ(rd, MakeUInt128(0x0000000700000003ULL, 0));
}

TEST(Arm64InsnTest, AddInt64x2) {
  __uint128_t op1 = MakeUInt128(0x8000000380000001ULL, 0x8000000780000005ULL);
  __uint128_t op2 = MakeUInt128(0x8000000480000002ULL, 0x8000000880000006ULL);
  __uint128_t rd = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("add %0.2d, %1.2d, %2.2d")(op1, op2);
  ASSERT_EQ(rd, MakeUInt128(0x0000000800000003ULL, 0x000000100000000bULL));
}

TEST(Arm64InsnTest, SubInt64x1) {
  __uint128_t arg1 = MakeUInt128(0x0000000000000002ULL, 0x0011223344556677ULL);
  __uint128_t arg2 = MakeUInt128(0x0000000000000003ULL, 0x0123456789abcdefULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("sub %d0, %d1, %d2")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0xffffffffffffffffULL, 0x0ULL));
}

TEST(Arm64InsnTest, SubInt64x2) {
  constexpr auto AsmSub = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("sub %0.2d, %1.2d, %2.2d");
  __uint128_t arg1 = MakeUInt128(0x6873115956286388ULL, 0x2353787593751957ULL);
  __uint128_t arg2 = MakeUInt128(0x7818577805321712ULL, 0x2680661300192787ULL);
  __uint128_t res = AsmSub(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0xf05ab9e150f64c76ULL, 0xfcd31262935bf1d0ULL));
}

TEST(Arm64InsnTest, SubInt16x4) {
  __uint128_t arg1 = MakeUInt128(0x8888777766665555ULL, 0);
  __uint128_t arg2 = MakeUInt128(0x1111222233334444ULL, 0);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("sub %0.4h, %1.4h, %2.4h")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x7777555533331111ULL, 0));
}

TEST(Arm64InsnTest, MultiplyI8x8) {
  __uint128_t arg1 = MakeUInt128(0x5261365549781893ULL, 0x1297848216829989ULL);
  __uint128_t arg2 = MakeUInt128(0x4542858444795265ULL, 0x8678210511413547ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("mul %0.8b, %1.8b, %2.8b")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x1a020ed464b8b0ffULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, MultiplyAndAccumulateI8x8) {
  __uint128_t arg1 = MakeUInt128(0x5848406353422072ULL, 0x2258284886481584ULL);
  __uint128_t arg2 = MakeUInt128(0x7823986456596116ULL, 0x3548862305774564ULL);
  __uint128_t arg3 = MakeUInt128(0x8797108931456691ULL, 0x3686722874894056ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW0_ARG("mla %0.8b, %1.8b, %2.8b")(arg1, arg2, arg3);
  ASSERT_EQ(res, MakeUInt128(0xc76f10351337865dULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, MultiplyAndAccumulateI8x8IndexedElem) {
  __uint128_t arg1 = MakeUInt128(0x4143334547762416ULL, 0x8625189835694855ULL);
  __uint128_t arg2 = MakeUInt128(0x5346462080466842ULL, 0x5906949129331367ULL);
  __uint128_t arg3 = MakeUInt128(0x0355876402474964ULL, 0x7326391419927260ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW0_ARG("mla %0.4h, %1.4h, %2.h[0]")(arg1, arg2, arg3);
  ASSERT_EQ(res, MakeUInt128(0x0e9bc72e5eb38710ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, MultiplyAndAccumulateI8x8IndexedElemPosition2) {
  __uint128_t arg1 = MakeUInt128(0x1431429809190659ULL, 0x2509372216964615ULL);
  __uint128_t arg2 = MakeUInt128(0x2686838689427741ULL, 0x5599185592524595ULL);
  __uint128_t arg3 = MakeUInt128(0x6099124608051243ULL, 0x8843904512441365ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW0_ARG("mla %0.2s, %1.2s, %2.s[2]")(arg1, arg2, arg3);
  ASSERT_EQ(res, MakeUInt128(0x6ce7ccbedccdc110ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, MultiplyAndSubtractI8x8IndexedElem) {
  __uint128_t arg1 = MakeUInt128(0x8297455570674983ULL, 0x8505494588586926ULL);
  __uint128_t arg2 = MakeUInt128(0x6549911988183479ULL, 0x7753566369807426ULL);
  __uint128_t arg3 = MakeUInt128(0x4524919217321721ULL, 0x4772350141441973ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW0_ARG("mls %0.4h, %1.4h, %2.h[1]")(arg1, arg2, arg3);
  ASSERT_EQ(res, MakeUInt128(0xcefce99ad58a9ad9ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, MultiplyAndSubtractI8x8) {
  __uint128_t arg1 = MakeUInt128(0x0635342207222582ULL, 0x8488648158456028ULL);
  __uint128_t arg2 = MakeUInt128(0x9864565961163548ULL, 0x8623057745649803ULL);
  __uint128_t arg3 = MakeUInt128(0x1089314566913686ULL, 0x7228748940560101ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW0_ARG("mls %0.8b, %1.8b, %2.8b")(arg1, arg2, arg3);
  ASSERT_EQ(res, MakeUInt128(0x80d5b973bfa58df6ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, MultiplyI32x4IndexedElem) {
  __uint128_t arg1 = MakeUInt128(0x143334547762416ULL, 0x8625189835694855ULL);
  __uint128_t arg2 = MakeUInt128(0x627232791786085ULL, 0x7843838279679766ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("mul %0.4s, %1.4s, %2.s[1]")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0xcec23e830d48815aULL, 0xd12b87288ae0a3f3ULL));
}

TEST(Arm64InsnTest, PolynomialMultiplyU8x8) {
  __uint128_t arg1 = MakeUInt128(0x1862056476931257ULL, 0x0586356620185581ULL);
  __uint128_t arg2 = MakeUInt128(0x1668039626579787ULL, 0x7185560845529654ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("pmul %0.8b, %1.8b, %2.8b")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0xd0d00f18f4095e25ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, PolynomialMultiplyLongU8x8) {
  __uint128_t arg1 = MakeUInt128(0x1327656180937734ULL, 0x4403070746921120ULL);
  __uint128_t arg2 = MakeUInt128(0x9838952286847831ULL, 0x2355265821314495ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("pmull %0.8h, %1.8b, %2.8b")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x43004bcc17e805f4ULL, 0x082807a835210ce2ULL));
}

TEST(Arm64InsnTest, PolynomialMultiplyLongU8x8Upper) {
  __uint128_t arg1 = MakeUInt128(0x4439658253375438ULL, 0x8569094113031509ULL);
  __uint128_t arg2 = MakeUInt128(0x1865619673378623ULL, 0x6256125216320862ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("pmull2 %0.8h, %1.16b, %2.16b")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x015a005600a80372ULL, 0x30ea1da6008214d2ULL));
}

TEST(Arm64InsnTest, PolynomialMultiplyLongU64x2) {
  __uint128_t arg1 = MakeUInt128(0x1000100010001000ULL, 0xffffeeeeffffeeeeULL);
  __uint128_t arg2 = MakeUInt128(0x10001ULL, 0xffffeeeeffffeeeeULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("pmull %0.1q, %1.1d, %2.1d")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x1000ULL, 0x1000ULL));
}

TEST(Arm64InsnTest, PolynomialMultiplyLongU64x2Upper) {
  __uint128_t arg1 = MakeUInt128(0xffffeeeeffffeeeeULL, 0x1000100010001000ULL);
  __uint128_t arg2 = MakeUInt128(0xffffeeeeffffeeeeULL, 0x10001ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("pmull2 %0.1q, %1.2d, %2.2d")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x1000ULL, 0x1000ULL));
}

TEST(Arm64InsnTest, PairwiseAddInt8x16) {
  __uint128_t op1 = MakeUInt128(0x7766554433221100ULL, 0xffeeddccbbaa9988ULL);
  __uint128_t op2 = MakeUInt128(0x0706050403020100ULL, 0x0f0e0d0c0b0a0908ULL);
  __uint128_t rd = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("addp %0.16b, %1.16b, %2.16b")(op1, op2);
  ASSERT_EQ(rd, MakeUInt128(0xeda96521dd995511ULL, 0x1d1915110d090501ULL));
}

TEST(Arm64InsnTest, PairwiseAddInt8x8) {
  __uint128_t op1 = MakeUInt128(0x7766554433221100ULL, 0xffeeddccbbaa9988ULL);
  __uint128_t op2 = MakeUInt128(0x0706050403020100ULL, 0x0f0e0d0c0b0a0908ULL);
  __uint128_t rd = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("addp %0.8b, %1.8b, %2.8b")(op1, op2);
  ASSERT_EQ(rd, MakeUInt128(0x0d090501dd995511ULL, 0));
}

TEST(Arm64InsnTest, PairwiseAddInt64x2) {
  __uint128_t op1 = MakeUInt128(1ULL, 2ULL);
  __uint128_t op2 = MakeUInt128(3ULL, 4ULL);
  __uint128_t rd = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("addp %0.2d, %1.2d, %2.2d")(op1, op2);
  ASSERT_EQ(rd, MakeUInt128(3ULL, 7ULL));
}

TEST(Arm64InsnTest, CompareEqualInt8x16) {
  __uint128_t op1 = MakeUInt128(0x9375195778185778ULL, 0x0532171226806613ULL);
  __uint128_t op2 = MakeUInt128(0x9371595778815787ULL, 0x0352172126068613ULL);
  __uint128_t rd = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("cmeq %0.16b, %1.16b, %2.16b")(op1, op2);
  ASSERT_EQ(rd, MakeUInt128(0xff0000ffff00ff00ULL, 0x0000ff00ff0000ffULL));
}

TEST(Arm64InsnTest, CompareEqualInt8x8) {
  __uint128_t op1 = MakeUInt128(0x9375195778185778ULL, 0x0532171226806613ULL);
  __uint128_t op2 = MakeUInt128(0x9371595778815787ULL, 0x0352172126068613ULL);
  __uint128_t rd = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("cmeq %0.8b, %1.8b, %2.8b")(op1, op2);
  ASSERT_EQ(rd, MakeUInt128(0xff0000ffff00ff00ULL, 0));
}

TEST(Arm64InsnTest, CompareEqualInt16x4) {
  __uint128_t op1 = MakeUInt128(0x4444333322221111ULL, 0);
  __uint128_t op2 = MakeUInt128(0x8888333300001111ULL, 0);
  __uint128_t rd = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("cmeq %0.4h, %1.4h, %2.4h")(op1, op2);
  ASSERT_EQ(rd, MakeUInt128(0x0000ffff0000ffffULL, 0));
}

TEST(Arm64InsnTest, CompareEqualInt64x1) {
  constexpr auto AsmCmeq = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("cmeq %d0, %d1, %d2");
  __uint128_t arg1 = MakeUInt128(0x8297455570674983ULL, 0x8505494588586926ULL);
  __uint128_t arg2 = MakeUInt128(0x0665499119881834ULL, 0x7977535663698074ULL);
  __uint128_t arg3 = MakeUInt128(0x8297455570674983ULL, 0x1452491921732172ULL);
  ASSERT_EQ(AsmCmeq(arg1, arg2), MakeUInt128(0x0000000000000000ULL, 0x0000000000000000ULL));
  ASSERT_EQ(AsmCmeq(arg1, arg3), MakeUInt128(0xffffffffffffffffULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, CompareEqualZeroInt64x1) {
  constexpr auto AsmCmeq = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("cmeq %d0, %d1, #0");
  __uint128_t arg1 = MakeUInt128(0x6517166776672793ULL, 0x0354851542040238ULL);
  __uint128_t arg2 = MakeUInt128(0x0000000000000000ULL, 0x1746089232839170ULL);
  ASSERT_EQ(AsmCmeq(arg1), MakeUInt128(0x0000000000000000ULL, 0x0000000000000000ULL));
  ASSERT_EQ(AsmCmeq(arg2), MakeUInt128(0xffffffffffffffffULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, CompareEqualZeroInt8x16) {
  __uint128_t op = MakeUInt128(0x0000555500332200ULL, 0x0000000077001100ULL);
  __uint128_t rd = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("cmeq %0.16b, %1.16b, #0")(op);
  ASSERT_EQ(rd, MakeUInt128(0xffff0000ff0000ffULL, 0xffffffff00ff00ffULL));
}

TEST(Arm64InsnTest, CompareEqualZeroInt8x8) {
  __uint128_t op = MakeUInt128(0x001122330000aaaaULL, 0xdeadbeef0000cafeULL);
  __uint128_t rd = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("cmeq %0.8b, %1.8b, #0")(op);
  ASSERT_EQ(rd, MakeUInt128(0xff000000ffff0000ULL, 0));
}

TEST(Arm64InsnTest, CompareGreaterInt64x1) {
  constexpr auto AsmCmgt = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("cmgt %d0, %d1, %d2");
  __uint128_t arg1 = MakeUInt128(0x1976668559233565ULL, 0x4639138363185745ULL);
  __uint128_t arg2 = MakeUInt128(0x3474940784884423ULL, 0x7721751543342603ULL);
  __uint128_t arg3 = MakeUInt128(0x1976668559233565ULL, 0x8183196376370761ULL);
  __uint128_t arg4 = MakeUInt128(0x9243530136776310ULL, 0x8491351615642269ULL);
  ASSERT_EQ(AsmCmgt(arg1, arg2), MakeUInt128(0x0000000000000000ULL, 0x0000000000000000ULL));
  ASSERT_EQ(AsmCmgt(arg1, arg3), MakeUInt128(0x0000000000000000ULL, 0x0000000000000000ULL));
  ASSERT_EQ(AsmCmgt(arg1, arg4), MakeUInt128(0xffffffffffffffffULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, CompareGreaterZeroInt64x1) {
  constexpr auto AsmCmgt = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("cmgt %d0, %d1, #0");
  __uint128_t arg1 = MakeUInt128(0x6517166776672793ULL, 0x0354851542040238ULL);
  __uint128_t arg2 = MakeUInt128(0x0000000000000000ULL, 0x6174599705674507ULL);
  __uint128_t arg3 = MakeUInt128(0x9592057668278967ULL, 0x7644531840404185ULL);
  ASSERT_EQ(AsmCmgt(arg1), MakeUInt128(0xffffffffffffffffULL, 0x0000000000000000ULL));
  ASSERT_EQ(AsmCmgt(arg2), MakeUInt128(0x0000000000000000ULL, 0x0000000000000000ULL));
  ASSERT_EQ(AsmCmgt(arg3), MakeUInt128(0x0000000000000000ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, CompareGreaterThanZeroInt8x16) {
  __uint128_t op = MakeUInt128(0x807fff00017efe02ULL, 0xff7f80000102fe02ULL);
  __uint128_t rd = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("cmgt %0.16b, %1.16b, #0")(op);
  ASSERT_EQ(rd, MakeUInt128(0x00ff0000ffff00ffULL, 0x00ff0000ffff00ffULL));
}

TEST(Arm64InsnTest, CompareGreaterThanZeroInt8x8) {
  __uint128_t op = MakeUInt128(0x00ff7f80017efe00ULL, 0x0000cafedeadbeefULL);
  __uint128_t rd = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("cmgt %0.8b, %1.8b, #0")(op);
  ASSERT_EQ(rd, MakeUInt128(0x0000ff00ffff0000ULL, 0));
}

TEST(Arm64InsnTest, CompareGreaterThanInt16x8) {
  __uint128_t arg1 = MakeUInt128(0x9789389001852956ULL, 0x9196780455448285ULL);
  __uint128_t arg2 = MakeUInt128(0x7269389081795897ULL, 0x5469399264218285);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("cmgt %0.8h, %1.8h, %2.8h")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x00000000ffff0000ULL, 0x0000ffff00000000ULL));
}

TEST(Arm64InsnTest, CompareGreaterThanInt32x4) {
  __uint128_t arg1 = MakeUInt128(0x0000'0000'ffff'ffffULL, 0xffff'ffff'0000'0000ULL);
  __uint128_t arg2 = MakeUInt128(0xffff'ffff'0000'0000ULL, 0x0000'0000'ffff'ffffULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("cmgt %0.4s, %1.4s, %2.4s")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0xffff'ffff'0000'0000ULL, 0x0000'0000'ffff'ffffULL));
}

TEST(Arm64InsnTest, CompareLessZeroInt64x1) {
  constexpr auto AsmCmlt = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("cmlt %d0, %d1, #0");
  __uint128_t arg1 = MakeUInt128(0x4784264567633881ULL, 0x8807565612168960ULL);
  __uint128_t arg2 = MakeUInt128(0x0000000000000000ULL, 0x8955999911209916ULL);
  __uint128_t arg3 = MakeUInt128(0x9364610175685060ULL, 0x1671453543158148ULL);
  ASSERT_EQ(AsmCmlt(arg1), MakeUInt128(0x0000000000000000ULL, 0x0000000000000000ULL));
  ASSERT_EQ(AsmCmlt(arg2), MakeUInt128(0x0000000000000000ULL, 0x0000000000000000ULL));
  ASSERT_EQ(AsmCmlt(arg3), MakeUInt128(0xffffffffffffffffULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, CompareLessThanZeroInt8x16) {
  __uint128_t op = MakeUInt128(0xff00017ffe020180ULL, 0x0001027e7ffeff80ULL);
  __uint128_t rd = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("cmlt %0.16b, %1.16b, #0")(op);
  ASSERT_EQ(rd, MakeUInt128(0xff000000ff0000ffULL, 0x0000000000ffffffULL));
}

TEST(Arm64InsnTest, CompareLessThanZeroInt8x8) {
  __uint128_t op = MakeUInt128(0x0002017e7fff8000ULL, 0x001100220000ffffULL);
  __uint128_t rd = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("cmlt %0.8b, %1.8b, #0")(op);
  ASSERT_EQ(rd, MakeUInt128(0x0000000000ffff00ULL, 0));
}

TEST(Arm64InsnTest, CompareGreaterThanEqualInt64x1) {
  constexpr auto AsmCmge = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("cmge %d0, %d1, %d2");
  __uint128_t arg1 = MakeUInt128(0x1009391369138107ULL, 0x2581378135789400ULL);
  __uint128_t arg2 = MakeUInt128(0x5890939568814856ULL, 0x0263224393726562ULL);
  __uint128_t arg3 = MakeUInt128(0x1009391369138107ULL, 0x5511995818319637ULL);
  __uint128_t arg4 = MakeUInt128(0x9427141009391369ULL, 0x1381072581378135ULL);
  ASSERT_EQ(AsmCmge(arg1, arg2), MakeUInt128(0x0000000000000000ULL, 0x0000000000000000ULL));
  ASSERT_EQ(AsmCmge(arg1, arg3), MakeUInt128(0xffffffffffffffffULL, 0x0000000000000000ULL));
  ASSERT_EQ(AsmCmge(arg1, arg4), MakeUInt128(0xffffffffffffffffULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, CompareGreaterThanEqualZeroInt64x1) {
  constexpr auto AsmCmge = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("cmge %d0, %d1, #0");
  __uint128_t arg1 = MakeUInt128(0x5562116715468484ULL, 0x7780394475697980ULL);
  __uint128_t arg2 = MakeUInt128(0x0000000000000000ULL, 0x3548487562529875ULL);
  __uint128_t arg3 = MakeUInt128(0x9212366168902596ULL, 0x2730430679316531ULL);
  ASSERT_EQ(AsmCmge(arg1), MakeUInt128(0xffffffffffffffffULL, 0x0000000000000000ULL));
  ASSERT_EQ(AsmCmge(arg2), MakeUInt128(0xffffffffffffffffULL, 0x0000000000000000ULL));
  ASSERT_EQ(AsmCmge(arg3), MakeUInt128(0x0000000000000000ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, CompareGreaterThanEqualZeroInt8x16) {
  __uint128_t op = MakeUInt128(0x00ff01027ffe8002ULL, 0x80fffe7f7e020100ULL);
  __uint128_t rd = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("cmge %0.16b, %1.16b, #0")(op);
  ASSERT_EQ(rd, MakeUInt128(0xff00ffffff0000ffULL, 0x000000ffffffffffULL));
}

TEST(Arm64InsnTest, CompareGreaterThanEqualZeroInt8x8) {
  __uint128_t op = MakeUInt128(0x0001027f80feff00ULL, 0x0011223344556677ULL);
  __uint128_t rd = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("cmge %0.8b, %1.8b, #0")(op);
  ASSERT_EQ(rd, MakeUInt128(0xffffffff000000ffULL, 0));
}

TEST(Arm64InsnTest, CompareGreaterEqualInt16x8) {
  __uint128_t arg1 = MakeUInt128(0x4391962838870543ULL, 0x6777432242768091ULL);
  __uint128_t arg2 = MakeUInt128(0x4391838548318875ULL, 0x0142432208995068ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("cmge %0.8h, %1.8h, %2.8h")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0xffffffff0000ffffULL, 0xffffffffffff0000ULL));
}

TEST(Arm64InsnTest, CompareLessThanEqualZeroInt64x1) {
  constexpr auto AsmCmle = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("cmle %d0, %d1, #0");
  __uint128_t arg1 = MakeUInt128(0x3643296406335728ULL, 0x1070788758164043ULL);
  __uint128_t arg2 = MakeUInt128(0x0000000000000000ULL, 0x5865720227637840ULL);
  __uint128_t arg3 = MakeUInt128(0x8694346828590066ULL, 0x6408063140777577ULL);
  ASSERT_EQ(AsmCmle(arg1), MakeUInt128(0x0000000000000000ULL, 0x0000000000000000ULL));
  ASSERT_EQ(AsmCmle(arg2), MakeUInt128(0xffffffffffffffffULL, 0x0000000000000000ULL));
  ASSERT_EQ(AsmCmle(arg3), MakeUInt128(0xffffffffffffffffULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, CompareLessThanEqualZeroInt8x16) {
  __uint128_t op = MakeUInt128(0x80fffe7f7e020100ULL, 0x00ff01027ffe8002ULL);
  __uint128_t rd = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("cmle %0.16b, %1.16b, #0")(op);
  ASSERT_EQ(rd, MakeUInt128(0xffffff00000000ffULL, 0xffff000000ffff00ULL));
}

TEST(Arm64InsnTest, CompareHigherInt64x1) {
  constexpr auto AsmCmhi = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("cmhi %d0, %d1, %d2");
  __uint128_t arg1 = MakeUInt128(0x1009391369138107ULL, 0x2581378135789400ULL);
  __uint128_t arg2 = MakeUInt128(0x0759167297007850ULL, 0x5807171863810549ULL);
  __uint128_t arg3 = MakeUInt128(0x1009391369138107ULL, 0x6026322439372656ULL);
  __uint128_t arg4 = MakeUInt128(0x9087839523245323ULL, 0x7896029841669225ULL);
  ASSERT_EQ(AsmCmhi(arg1, arg2), MakeUInt128(0xffffffffffffffffULL, 0x0000000000000000ULL));
  ASSERT_EQ(AsmCmhi(arg1, arg3), MakeUInt128(0x0000000000000000ULL, 0x0000000000000000ULL));
  ASSERT_EQ(AsmCmhi(arg1, arg4), MakeUInt128(0x0000000000000000ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, CompareHigherInt16x8) {
  __uint128_t arg1 = MakeUInt128(0x6517166776672793ULL, 0x0354851542040238ULL);
  __uint128_t arg2 = MakeUInt128(0x2057166778967764ULL, 0x4531840442045540ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("cmhi %0.8h, %1.8h, %2.8h")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0xffff000000000000ULL, 0x0000ffff00000000ULL));
}

TEST(Arm64InsnTest, CompareHigherInt32x4) {
  __uint128_t arg1 = MakeUInt128(0x0000'0000'ffff'ffffULL, 0xffff'ffff'0000'0000ULL);
  __uint128_t arg2 = MakeUInt128(0xffff'ffff'0000'0000ULL, 0x0000'0000'ffff'ffffULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("cmhi %0.4s, %1.4s, %2.4s")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x0000'0000'ffff'ffffULL, 0xffff'ffff'0000'0000ULL));
}

TEST(Arm64InsnTest, CompareHigherSameInt64x1) {
  constexpr auto AsmCmhs = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("cmhs %d0, %d1, %d2");
  __uint128_t arg1 = MakeUInt128(0x3529566139788848ULL, 0x6050978608595701ULL);
  __uint128_t arg2 = MakeUInt128(0x1769845875810446ULL, 0x6283998806006162ULL);
  __uint128_t arg3 = MakeUInt128(0x3529566139788848ULL, 0x9001852956919678ULL);
  __uint128_t arg4 = MakeUInt128(0x9628388705436777ULL, 0x4322427680913236ULL);
  ASSERT_EQ(AsmCmhs(arg1, arg2), MakeUInt128(0xffffffffffffffffULL, 0x0000000000000000ULL));
  ASSERT_EQ(AsmCmhs(arg1, arg3), MakeUInt128(0xffffffffffffffffULL, 0x0000000000000000ULL));
  ASSERT_EQ(AsmCmhs(arg1, arg4), MakeUInt128(0x0000000000000000ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, CompareHigherSameInt16x8) {
  __uint128_t arg1 = MakeUInt128(0x4599705674507183ULL, 0x3206503455664403ULL);
  __uint128_t arg2 = MakeUInt128(0x4264705633881880ULL, 0x3206612168960504ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("cmhs %0.8h, %1.8h, %2.8h")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0xffffffffffffffffULL, 0xffff00000000ffffULL));
}

TEST(Arm64InsnTest, CompareLessThanEqualZeroInt8x8) {
  __uint128_t op = MakeUInt128(0x00fffe807f020100ULL, 0x00aabbccddeeff00ULL);
  __uint128_t rd = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("cmle %0.8b, %1.8b, #0")(op);
  ASSERT_EQ(rd, MakeUInt128(0xffffffff000000ffULL, 0));
}

TEST(Arm64InsnTest, TestInt64x1) {
  constexpr auto AsmCmtst = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("cmtst %d0, %d1, %d2");
  __uint128_t arg1 = MakeUInt128(0xaaaaaaaa55555555ULL, 0x7698385483188750ULL);
  __uint128_t arg2 = MakeUInt128(0x55555555aaaaaaaaULL, 0x1429389089950685ULL);
  __uint128_t arg3 = MakeUInt128(0xaa00aa0055005500ULL, 0x4530765116803337ULL);
  ASSERT_EQ(AsmCmtst(arg1, arg2), MakeUInt128(0x0000000000000000ULL, 0x0000000000000000ULL));
  ASSERT_EQ(AsmCmtst(arg1, arg3), MakeUInt128(0xffffffffffffffffULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, TestInt16x8) {
  __uint128_t arg1 = MakeUInt128(0x5999911209916464ULL, 0x6441191856827700ULL);
  __uint128_t arg2 = MakeUInt128(0x6101756850601671ULL, 0x4535431581480105ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("cmtst %0.8h, %1.8h, %2.8h")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0xffffffff0000ffffULL, 0xffffffff0000ffffULL));
}

TEST(Arm64InsnTest, ExtractVectorFromPair) {
  __uint128_t op1 = MakeUInt128(0x0011223344556677ULL, 0x8899aabbccddeeffULL);
  __uint128_t op2 = MakeUInt128(0x0001020304050607ULL, 0x08090a0b0c0d0e0fULL);
  __uint128_t rd = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("ext %0.16b, %1.16b, %2.16b, #8")(op1, op2);
  ASSERT_EQ(rd, MakeUInt128(0x8899aabbccddeeffULL, 0x0001020304050607ULL));
}

TEST(Arm64InsnTest, ExtractVectorFromPairHalfWidth) {
  __uint128_t op1 = MakeUInt128(0x8138268683868942ULL, 0x7741559918559252ULL);
  __uint128_t op2 = MakeUInt128(0x3622262609912460ULL, 0x8051243884390451ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("ext %0.8b, %1.8b, %2.8b, #3")(op1, op2);
  ASSERT_EQ(res, MakeUInt128(0x9124608138268683ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, ExtractVectorFromPairHalfWidthPosition1) {
  __uint128_t op1 = MakeUInt128(0x9471329621073404ULL, 0x3751895735961458ULL);
  __uint128_t op2 = MakeUInt128(0x9048010941214722ULL, 0x1317947647772622ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("ext %0.8b, %1.8b, %2.8b, #1")(op1, op2);
  ASSERT_EQ(res, MakeUInt128(0x2294713296210734ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, Load1OneI8x8) {
  static constexpr uint64_t arg = 0x8867915896904956ULL;
  __uint128_t res;
  asm("ld1 {%0.8b}, [%1]" : "=w"(res) : "r"(&arg) : "memory");
  ASSERT_EQ(res, arg);
}

TEST(Arm64InsnTest, Load1ThreeI8x8) {
  static constexpr uint64_t arg[3] = {
      0x3415354584283376ULL, 0x4378111988556318ULL, 0x7777925372011667ULL};
  __uint128_t res[3];
  asm("ld1 {v0.8b-v2.8b}, [%3]\n\t"
      "mov %0.16b, v0.16b\n\t"
      "mov %1.16b, v1.16b\n\t"
      "mov %2.16b, v2.16b"
      : "=w"(res[0]), "=w"(res[1]), "=w"(res[2])
      : "r"(arg)
      : "v0", "v1", "v2", "memory");
  ASSERT_EQ(res[0], static_cast<__uint128_t>(arg[0]));
  ASSERT_EQ(res[1], static_cast<__uint128_t>(arg[1]));
  ASSERT_EQ(res[2], static_cast<__uint128_t>(arg[2]));
}

TEST(Arm64InsnTest, Load1FourI8x8) {
  static constexpr uint64_t arg[4] = {
      0x9523688483099930ULL,
      0x2757419916463841ULL,
      0x4270779887088742ULL,
      0x2927705389122717ULL,
  };
  __uint128_t res[4];
  asm("ld1 {v0.8b-v3.8b}, [%4]\n\t"
      "mov %0.16b, v0.16b\n\t"
      "mov %1.16b, v1.16b\n\t"
      "mov %2.16b, v2.16b\n\t"
      "mov %3.16b, v3.16b"
      : "=w"(res[0]), "=w"(res[1]), "=w"(res[2]), "=w"(res[3])
      : "r"(arg)
      : "v0", "v1", "v2", "v3", "memory");
  ASSERT_EQ(res[0], static_cast<__uint128_t>(arg[0]));
  ASSERT_EQ(res[1], static_cast<__uint128_t>(arg[1]));
  ASSERT_EQ(res[2], static_cast<__uint128_t>(arg[2]));
  ASSERT_EQ(res[3], static_cast<__uint128_t>(arg[3]));
}

TEST(Arm64InsnTest, Store1OneI8x16) {
  static constexpr __uint128_t arg = MakeUInt128(0x7642291583425006ULL, 0x7361245384916067ULL);
  __uint128_t res;
  asm("st1 {%0.16b}, [%1]" : : "w"(arg), "r"(&res) : "memory");
  ASSERT_EQ(res, arg);
}

TEST(Arm64InsnTest, Store1ThreeI8x8) {
  static constexpr uint64_t arg[3] = {
      0x3086436111389069ULL, 0x4202790881431194ULL, 0x4879941715404210ULL};
  uint64_t res[3];
  asm("mov v0.16b, %0.16b\n\t"
      "mov v1.16b, %1.16b\n\t"
      "mov v2.16b, %2.16b\n\t"
      "st1 {v0.8b-v2.8b}, [%3]"
      :
      : "w"(arg[0]), "w"(arg[1]), "w"(arg[2]), "r"(res)
      : "v0", "v1", "v2", "memory");
  ASSERT_EQ(res[0], arg[0]);
  ASSERT_EQ(res[1], arg[1]);
  ASSERT_EQ(res[2], arg[2]);
}

TEST(Arm64InsnTest, Store1FourI8x8) {
  static constexpr uint64_t arg[4] = {
      0x8954750448339314ULL, 0x6896307633966572ULL, 0x2672704339321674ULL, 0x5421824557062524ULL};
  uint64_t res[4];
  asm("mov v0.16b, %0.16b\n\t"
      "mov v1.16b, %1.16b\n\t"
      "mov v2.16b, %2.16b\n\t"
      "mov v3.16b, %3.16b\n\t"
      "st1 {v0.8b-v3.8b}, [%4]"
      :
      : "w"(arg[0]), "w"(arg[1]), "w"(arg[2]), "w"(arg[3]), "r"(res)
      : "v0", "v1", "v2", "v3", "memory");
  ASSERT_EQ(res[0], arg[0]);
  ASSERT_EQ(res[1], arg[1]);
  ASSERT_EQ(res[2], arg[2]);
  ASSERT_EQ(res[3], arg[3]);
}

TEST(Arm64InsnTest, Load1TwoPostIndex) {
  __uint128_t op0 = MakeUInt128(0x5499119881834797ULL, 0x0507922796892589ULL);
  __uint128_t op1 = MakeUInt128(0x0511854807446237ULL, 0x6691368672287489ULL);
  __uint128_t array[] = {
      op0,
      op1,
  };
  __uint128_t* addr = &array[0];
  __uint128_t res0 = 0;
  __uint128_t res1 = 0;

  // The "memory" below ensures that the array contents are up to date.  Without it, the
  // compiler might decide to initialize the array after the asm statement.
  //
  // We hardcode SIMD registers v0 and v1 below because there is no other way to express
  // consecutive registers, which in turn requires the mov instructions to retrieve the
  // loaded values into res0 and res1.
  asm("ld1 {v0.16b, v1.16b}, [%2], #32\n\t"
      "mov %0.16b, v0.16b\n\t"
      "mov %1.16b, v1.16b"
      : "=w"(res0), "=w"(res1), "+r"(addr)
      :
      : "v0", "v1", "memory");

  ASSERT_EQ(res0, op0);
  ASSERT_EQ(res1, op1);
  ASSERT_EQ(addr, &array[2]);
}

TEST(Arm64InsnTest, Load1OnePostIndexReg) {
  static constexpr __uint128_t arg = MakeUInt128(0x4884761005564018ULL, 0x2423921926950620ULL);
  __uint128_t res_val;
  uint64_t res_addr;
  asm("ld1 {%0.16b}, [%1], %2"
      : "=w"(res_val), "=r"(res_addr)
      : "r"(static_cast<uint64_t>(32U)), "1"(&arg)
      : "memory");
  ASSERT_EQ(res_val, arg);
  ASSERT_EQ(res_addr, reinterpret_cast<uint64_t>(&arg) + 32);
}

TEST(Arm64InsnTest, LoadSingleInt8) {
  static constexpr __uint128_t reg_before =
      MakeUInt128(0x0011223344556677ULL, 0x8899aabbccddeeffULL);
  static constexpr __uint128_t mem_src = MakeUInt128(0x0102030405060708ULL, 0x090a0b0c0d0e0f10ULL);
  __uint128_t reg_after;
  asm("ld1 {%0.b}[3], [%1]" : "=w"(reg_after) : "r"(&mem_src), "0"(reg_before) : "memory");
  ASSERT_EQ(reg_after, MakeUInt128(0x00112233'08'556677ULL, 0x8899aabbccddeeffULL));
}

TEST(Arm64InsnTest, LoadSingleInt16) {
  static constexpr __uint128_t reg_before =
      MakeUInt128(0x0000111122223333ULL, 0x4444555566667777ULL);
  static constexpr __uint128_t mem_src = MakeUInt128(0x0102030405060708ULL, 0x090a0b0c0d0e0f10ULL);
  __uint128_t reg_after;
  asm("ld1 {%0.h}[2], [%1]" : "=w"(reg_after) : "r"(&mem_src), "0"(reg_before) : "memory");
  ASSERT_EQ(reg_after, MakeUInt128(0x0000'0708'22223333ULL, 0x4444555566667777ULL));
}

TEST(Arm64InsnTest, LoadSingleInt32) {
  static constexpr __uint128_t reg_before =
      MakeUInt128(0x0000000011111111ULL, 0x2222222233333333ULL);
  static constexpr __uint128_t mem_src = MakeUInt128(0x0102030405060708ULL, 0x090a0b0c0d0e0f10ULL);
  __uint128_t reg_after;
  asm("ld1 {%0.s}[1], [%1]" : "=w"(reg_after) : "r"(&mem_src), "0"(reg_before) : "memory");
  ASSERT_EQ(reg_after, MakeUInt128(0x0506070811111111ULL, 0x2222222233333333ULL));
}

TEST(Arm64InsnTest, LoadSingleInt64) {
  static constexpr __uint128_t reg_before =
      MakeUInt128(0x0000000000000000ULL, 0x1111111111111111ULL);
  static constexpr __uint128_t mem_src = MakeUInt128(0x0102030405060708ULL, 0x090a0b0c0d0e0f10ULL);
  __uint128_t reg_after;
  asm("ld1 {%0.d}[1], [%1]" : "=w"(reg_after) : "r"(&mem_src), "0"(reg_before) : "memory");
  ASSERT_EQ(reg_after, MakeUInt128(0x0000000000000000ULL, 0x0102030405060708ULL));
}

TEST(Arm64InsnTest, StoreSingleInt8) {
  static constexpr __uint128_t arg = MakeUInt128(0x0102030405060708ULL, 0x090a0b0c0d0e0f10ULL);
  __uint128_t mem_dest = MakeUInt128(0x0011223344556677ULL, 0x8899aabbccddeeffULL);
  asm("st1 {%1.b}[3], [%0]" : : "r"(&mem_dest), "w"(arg) : "memory");
  ASSERT_EQ(mem_dest, MakeUInt128(0x00112233445566'05ULL, 0x8899aabbccddeeffULL));
}

TEST(Arm64InsnTest, StoreSingleInt16) {
  static constexpr __uint128_t arg = MakeUInt128(0x0102030405060708ULL, 0x090a0b0c0d0e0f10ULL);
  __uint128_t mem_dest = MakeUInt128(0x0000111122223333ULL, 0x4444555566667777ULL);
  asm("st1 {%1.h}[5], [%0]" : : "r"(&mem_dest), "w"(arg) : "memory");
  ASSERT_EQ(mem_dest, MakeUInt128(0x000011112222'0d0eULL, 0x4444555566667777ULL));
}

TEST(Arm64InsnTest, StoreSingleInt32) {
  static constexpr __uint128_t arg = MakeUInt128(0x0102030405060708ULL, 0x090a0b0c0d0e0f10ULL);
  __uint128_t mem_dest = MakeUInt128(0x0000000011111111ULL, 0x2222222233333333ULL);
  asm("st1 {%1.s}[2], [%0]" : : "r"(&mem_dest), "w"(arg) : "memory");
  ASSERT_EQ(mem_dest, MakeUInt128(0x000000000'd0e0f10ULL, 0x2222222233333333ULL));
}

TEST(Arm64InsnTest, StoreSingleInt64) {
  static constexpr __uint128_t arg = MakeUInt128(0x0102030405060708ULL, 0x090a0b0c0d0e0f10ULL);
  __uint128_t mem_dest = MakeUInt128(0x0000000000000000ULL, 0x1111111111111111ULL);
  asm("st1 {%1.d}[1], [%0]" : : "r"(&mem_dest), "w"(arg) : "memory");
  ASSERT_EQ(mem_dest, MakeUInt128(0x090a0b0c0d0e0f10ULL, 0x1111111111111111ULL));
}

TEST(Arm64InsnTest, LoadSinglePostIndexImmInt8) {
  static constexpr __uint128_t arg1 = MakeUInt128(0x5494167594605487ULL, 0x1172359464291058ULL);
  static constexpr __uint128_t arg2 = MakeUInt128(0x5090995021495879ULL, 0x3112196135908315ULL);
  __uint128_t res;
  uint8_t* addr;
  asm("ld1 {%0.b}[3], [%1], #1" : "=w"(res), "=r"(addr) : "0"(arg1), "1"(&arg2) : "memory");
  ASSERT_EQ(res, MakeUInt128(0x5494167579605487ULL, 0x1172359464291058ULL));
  ASSERT_EQ(addr, reinterpret_cast<const uint8_t*>(&arg2) + 1);
}

TEST(Arm64InsnTest, LoadSinglePostIndexRegInt16) {
  static constexpr __uint128_t arg1 = MakeUInt128(0x0080587824107493ULL, 0x5751488997891173ULL);
  static constexpr __uint128_t arg2 = MakeUInt128(0x9746129320351081ULL, 0x4327032514090304ULL);
  __uint128_t res;
  uint8_t* addr;
  asm("ld1 {%0.h}[7], [%1], %2"
      : "=w"(res), "=r"(addr)
      : "r"(static_cast<uint64_t>(17U)), "0"(arg1), "1"(&arg2)
      : "memory");
  ASSERT_EQ(res, MakeUInt128(0x0080587824107493ULL, 0x1081488997891173ULL));
  ASSERT_EQ(addr, reinterpret_cast<const uint8_t*>(&arg2) + 17);
}

TEST(Arm64InsnTest, StoreSimdPostIndex) {
  __uint128_t old_val = MakeUInt128(0x4939965143142980ULL, 0x9190659250937221ULL);
  __uint128_t new_val = MakeUInt128(0x5985261365549781ULL, 0x8931297848216829ULL);
  __uint128_t* addr = &old_val;

  // Verify that the interpreter accepts "str q0, [x0], #8" where the register numbers are
  // the same, when the data register is one of the SIMD registers.
  asm("mov x0, %0\n\t"
      "mov v0.2D, %1.2D\n\t"
      "str q0, [x0], #8\n\t"
      "mov %0, x0"
      : "+r"(addr)
      : "w"(new_val)
      : "v0", "x0", "memory");

  ASSERT_EQ(old_val, MakeUInt128(0x5985261365549781ULL, 0x8931297848216829ULL));
  ASSERT_EQ(reinterpret_cast<uintptr_t>(addr), reinterpret_cast<uintptr_t>(&old_val) + 8);
}

TEST(Arm64InsnTest, StoreZeroPostIndex1) {
  uint64_t res;
  asm("str xzr, [sp, #-16]!\n\t"
      "ldr %0, [sp, #0]\n\t"
      "add sp, sp, #16"
      : "=r"(res));
  ASSERT_EQ(res, 0);
}

TEST(Arm64InsnTest, StoreZeroPostIndex2) {
  __uint128_t arg1 = MakeUInt128(0x9415573293820485ULL, 0x4212350817391254ULL);
  __uint128_t arg2 = MakeUInt128(0x9749819308714396ULL, 0x6151329420459193ULL);
  __uint128_t res1;
  __uint128_t res2;
  asm("mov v30.16b, %2.16b\n\t"
      "mov v31.16b, %3.16b\n\t"
      "stp q30, q31, [sp, #-32]!\n\t"
      "ldr %q0, [sp, #0]\n\t"
      "ldr %q1, [sp, #16]\n\t"
      "add sp, sp, #32"
      : "=w"(res1), "=w"(res2)
      : "w"(arg1), "w"(arg2)
      : "v30", "v31");

  ASSERT_EQ(res1, arg1);
  ASSERT_EQ(res2, arg2);
}

TEST(Arm64InsnTest, Load2MultipleInt8x8) {
  static constexpr uint8_t mem[] = {0x02,
                                    0x16,
                                    0x91,
                                    0x83,
                                    0x37,
                                    0x23,
                                    0x68,
                                    0x03,
                                    0x99,
                                    0x02,
                                    0x79,
                                    0x31,
                                    0x60,
                                    0x64,
                                    0x20,
                                    0x43};
  __uint128_t res[2];
  asm("ld2 {v0.8b, v1.8b}, [%2]\n\t"
      "mov %0.16b, v0.16b\n\t"
      "mov %1.16b, v1.16b"
      : "=w"(res[0]), "=w"(res[1])
      : "r"(mem)
      : "v0", "v1", "memory");
  ASSERT_EQ(res[0], MakeUInt128(0x2060799968379102ULL, 0U));
  ASSERT_EQ(res[1], MakeUInt128(0x4364310203238316ULL, 0U));
}

TEST(Arm64InsnTest, Load3MultipleInt8x8) {
  static constexpr uint8_t mem[] = {0x32, 0x87, 0x67, 0x03, 0x80, 0x92, 0x52, 0x16,
                                    0x79, 0x07, 0x57, 0x12, 0x04, 0x06, 0x12, 0x37,
                                    0x59, 0x63, 0x27, 0x68, 0x56, 0x74, 0x84, 0x50};
  __uint128_t res[3];
  asm("ld3 {v7.8b-v9.8b}, [%3]\n\t"
      "mov %0.16b, v7.16b\n\t"
      "mov %1.16b, v8.16b\n\t"
      "mov %2.16b, v9.16b"
      : "=w"(res[0]), "=w"(res[1]), "=w"(res[2])
      : "r"(mem)
      : "v7", "v8", "v9", "memory");
  ASSERT_EQ(res[0], MakeUInt128(0x7427370407520332ULL, 0U));
  ASSERT_EQ(res[1], MakeUInt128(0x8468590657168087ULL, 0U));
  ASSERT_EQ(res[2], MakeUInt128(0x5056631212799267ULL, 0U));
}

TEST(Arm64InsnTest, Load4MultipleInt16x8) {
  static constexpr uint16_t mem[] = {
      0x2069, 0x6535, 0x3863, 0x9644, 0x3225, 0x3883, 0x2752, 0x2499, 0x6059, 0x8697, 0x4759,
      0x8823, 0x2991, 0x6263, 0x5459, 0x7332, 0x4445, 0x1637, 0x5533, 0x4377, 0x4929, 0x2899,
      0x0581, 0x1757, 0x9881, 0x5078, 0x1468, 0x5262, 0x1332, 0x5247, 0x3837, 0x6511};
  __uint128_t res[4];
  asm("ld4 {v30.8h-v1.8h}, [%4]\n\t"
      "mov %0.16b, v30.16b\n\t"
      "mov %1.16b, v31.16b\n\t"
      "mov %2.16b, v0.16b\n\t"
      "mov %3.16b, v1.16b"
      : "=w"(res[0]), "=w"(res[1]), "=w"(res[2]), "=w"(res[3])
      : "r"(mem)
      : "v30", "v31", "v0", "v1", "memory");
  ASSERT_EQ(res[0], MakeUInt128(0x2991605932252069ULL, 0x1332988149294445ULL));
  ASSERT_EQ(res[1], MakeUInt128(0x6263869738836535ULL, 0x5247507828991637ULL));
  ASSERT_EQ(res[2], MakeUInt128(0x5459475927523863ULL, 0x3837146805815533ULL));
  ASSERT_EQ(res[3], MakeUInt128(0x7332882324999644ULL, 0x6511526217574377ULL));
}

TEST(Arm64InsnTest, Load1ReplicateInt8x8) {
  static constexpr uint8_t mem = 0x81U;
  __uint128_t res;
  asm("ld1r {%0.8b}, [%1]" : "=w"(res) : "r"(&mem) : "memory");
  ASSERT_EQ(res, MakeUInt128(0x8181818181818181ULL, 0U));
}

TEST(Arm64InsnTest, Load2ReplicateInt16x8) {
  static constexpr uint16_t mem[] = {0x7904, 0x8715};
  __uint128_t res[2];
  asm("ld2r {v6.8h, v7.8h}, [%2]\n\t"
      "mov %0.16b, v6.16b\n\t"
      "mov %1.16b, v7.16b"
      : "=w"(res[0]), "=w"(res[1])
      : "r"(mem)
      : "v6", "v7", "memory");
  ASSERT_EQ(res[0], MakeUInt128(0x7904790479047904ULL, 0x7904790479047904ULL));
  ASSERT_EQ(res[1], MakeUInt128(0x8715871587158715ULL, 0x8715871587158715ULL));
}

TEST(Arm64InsnTest, Load3ReplicateInt32x4) {
  static constexpr uint32_t mem[] = {0x78713710U, 0x60510637U, 0x95558588U};
  __uint128_t res[3];
  asm("ld3r {v30.4s-v0.4s}, [%3]\n\t"
      "mov %0.16b, v30.16b\n\t"
      "mov %1.16b, v31.16b\n\t"
      "mov %2.16b, v0.16b"
      : "=w"(res[0]), "=w"(res[1]), "=w"(res[2])
      : "r"(mem)
      : "v30", "v31", "v0", "memory");
  ASSERT_EQ(res[0], MakeUInt128(0x7871371078713710ULL, 0x7871371078713710ULL));
  ASSERT_EQ(res[1], MakeUInt128(0x6051063760510637ULL, 0x6051063760510637ULL));
  ASSERT_EQ(res[2], MakeUInt128(0x9555858895558588ULL, 0x9555858895558588ULL));
}

TEST(Arm64InsnTest, Load4ReplicateInt64x2) {
  static constexpr uint64_t mem[] = {
      0x8150781468526213ULL, 0x3252473837651192ULL, 0x9901561091897779ULL, 0x2200870579339646ULL};
  __uint128_t res[4];
  asm("ld4r {v29.2d-v0.2d}, [%4]\n\t"
      "mov %0.16b, v29.16b\n\t"
      "mov %1.16b, v30.16b\n\t"
      "mov %2.16b, v31.16b\n\t"
      "mov %3.16b, v0.16b"
      : "=w"(res[0]), "=w"(res[1]), "=w"(res[2]), "=w"(res[3])
      : "r"(mem)
      : "v29", "v30", "v31", "v0", "memory");
  ASSERT_EQ(res[0], MakeUInt128(mem[0], mem[0]));
  ASSERT_EQ(res[1], MakeUInt128(mem[1], mem[1]));
  ASSERT_EQ(res[2], MakeUInt128(mem[2], mem[2]));
  ASSERT_EQ(res[3], MakeUInt128(mem[3], mem[3]));
}

TEST(Arm64InsnTest, LoadPairNonTemporarlInt64) {
  static constexpr uint64_t mem[] = {0x3843601737474215ULL, 0x2476085152099016ULL};
  __uint128_t res[2];
  asm("ldnp %d0, %d1, [%2]" : "=w"(res[0]), "=w"(res[1]) : "r"(mem) : "memory");
  ASSERT_EQ(res[0], MakeUInt128(0x3843601737474215ULL, 0U));
  ASSERT_EQ(res[1], MakeUInt128(0x2476085152099016ULL, 0U));
}

TEST(Arm64InsnTest, MoviVector2S) {
  __uint128_t rd = ASM_INSN_WRAP_FUNC_W_RES("movi %0.2s, #0xe4")();
  ASSERT_EQ(rd, MakeUInt128(0x000000e4000000e4ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, MoviVector2D) {
  __uint128_t rd = ASM_INSN_WRAP_FUNC_W_RES("movi %0.2d, #0xff")();
  ASSERT_EQ(rd, MakeUInt128(0x00000000000000ffULL, 0x00000000000000ffULL));
}

TEST(Arm64InsnTest, MoviVector8B) {
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES("movi %0.8b, #0xda")();
  ASSERT_EQ(res, MakeUInt128(0xdadadadadadadadaULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, MoviVector4HShiftBy8) {
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES("movi %0.4h, #0xd1, lsl #8")();
  ASSERT_EQ(res, MakeUInt128(0xd100d100d100d100ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, MoviVector2SShiftBy16) {
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES("movi %0.2s, #0x37, msl #16")();
  ASSERT_EQ(res, MakeUInt128(0x0037ffff0037ffffULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, MvniVector4H) {
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES("mvni %0.4h, #0xbc")();
  ASSERT_EQ(res, MakeUInt128(0xff43ff43ff43ff43ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, MvniVector2SShiftBy8) {
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES("mvni %0.2s, #0x24, lsl #8")();
  ASSERT_EQ(res, MakeUInt128(0xffffdbffffffdbffULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, MvniVector2SShiftBy16) {
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES("mvni %0.2s, #0x25, msl #16")();
  ASSERT_EQ(res, MakeUInt128(0xffda0000ffda0000ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, LoadSimdRegPlusReg) {
  __uint128_t array[] = {
      MakeUInt128(0x6517980694113528ULL, 0x0131470130478164ULL),
      MakeUInt128(0x8672422924654366ULL, 0x8009806769282382ULL),
  };
  uint64_t offset = 16;
  __uint128_t rd;

  asm("ldr %q0, [%1, %2]" : "=w"(rd) : "r"(array), "r"(offset) : "memory");

  ASSERT_EQ(rd, MakeUInt128(0x8672422924654366ULL, 0x8009806769282382ULL));
}

TEST(Arm64InsnTest, ExtractNarrowI16x8ToI8x8) {
  __uint128_t arg = MakeUInt128(0x0123456789abcdefULL, 0x0011223344556677ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("xtn %0.8b, %1.8h")(arg);
  ASSERT_EQ(res, MakeUInt128(0x113355772367abefULL, 0x0ULL));
}

TEST(Arm64InsnTest, ExtractNarrowI32x4ToI16x4) {
  __uint128_t arg = MakeUInt128(0x0123456789abcdefULL, 0x0011223344556677ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("xtn %0.4h, %1.4s")(arg);
  ASSERT_EQ(res, MakeUInt128(0x223366774567cdefULL, 0x0ULL));
}

TEST(Arm64InsnTest, ExtractNarrowI64x2ToI32x2) {
  __uint128_t arg = MakeUInt128(0x0123456789abcdefULL, 0x0011223344556677ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("xtn %0.2s, %1.2d")(arg);
  ASSERT_EQ(res, MakeUInt128(0x4455667789abcdefULL, 0x0ULL));
}

TEST(Arm64InsnTest, ExtractNarrow2Int16x8ToInt8x16) {
  __uint128_t arg1 = MakeUInt128(0x1844396582533754ULL, 0x3885690941130315ULL);
  __uint128_t arg2 = MakeUInt128(0x6121865619673378ULL, 0x6236256125216320ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W0_ARG("xtn2 %0.16b, %1.8h")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x6121865619673378ULL, 0x8509131544655354ULL));
}

TEST(Arm64InsnTest, LoadLiteralSimd) {
  // We call an external assembly function to perform LDR literal because we
  // need to place the literal in .rodata.  The literal placed in .text would
  // trigger a segfault.
  ASSERT_EQ(get_fp64_literal(), 0x0123456789abcdefULL);
}

TEST(Arm64InsnTest, AbsInt64x1) {
  __uint128_t arg = MakeUInt128(0xfffffffffffffffdULL, 0xdeadbeef01234567ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("abs %d0, %d1")(arg);
  ASSERT_EQ(res, MakeUInt128(0x0000000000000003ULL, 0x0ULL));
}

TEST(Arm64InsnTest, AbsInt8x8) {
  __uint128_t arg = MakeUInt128(0x0001027e7f8081ffULL, 0x0123456789abcdefULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("abs %0.8b, %1.8b")(arg);
  ASSERT_EQ(res, MakeUInt128(0x0001027e7f807f01ULL, 0x0ULL));
}

TEST(Arm64InsnTest, UseV31) {
  __uint128_t res;

  asm("movi v31.2d, #0xffffffffffffffff\n\t"
      "mov %0.16b, v31.16b"
      : "=w"(res)
      :
      : "v31");

  ASSERT_EQ(res, MakeUInt128(~0ULL, ~0ULL));
}

TEST(Arm64InsnTest, AddHighNarrowInt16x8) {
  __uint128_t arg1 = MakeUInt128(0x2296617119637792ULL, 0x1337575114959501ULL);
  __uint128_t arg2 = MakeUInt128(0x0941214722131794ULL, 0x7647772622414254ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("addhn %0.8b, %1.8h, %2.8h")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x89ce36d72b823b8fULL, 0x0ULL));
}

TEST(Arm64InsnTest, AddHighNarrowUpperInt16x8) {
  __uint128_t arg1 = MakeUInt128(0x6561809377344403ULL, 0x0707469211201913ULL);
  __uint128_t arg2 = MakeUInt128(0x6095752706957220ULL, 0x9175671167229109ULL);
  __uint128_t arg3 = MakeUInt128(0x5797877185560845ULL, 0x5296541266540853ULL);
  __uint128_t res =
      ASM_INSN_WRAP_FUNC_W_RES_WW0_ARG("addhn2 %0.16b, %1.8h, %2.8h")(arg1, arg2, arg3);
  ASSERT_EQ(res, MakeUInt128(0x5797877185560845ULL, 0x98ad78aac5f57db6ULL));
}

TEST(Arm64InsnTest, SubHighNarrowInt16x8) {
  __uint128_t arg1 = MakeUInt128(0x4978189312978482ULL, 0x1682998948722658ULL);
  __uint128_t arg2 = MakeUInt128(0x1210835791513698ULL, 0x8209144421006751ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("subhn %0.8b, %1.8h, %2.8h")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x948527bf3795814dULL, 0x0ULL));
}

TEST(Arm64InsnTest, SubHighNarrowUpperInt16x8) {
  __uint128_t arg1 = MakeUInt128(0x5324944166803962ULL, 0x6579787718556084ULL);
  __uint128_t arg2 = MakeUInt128(0x1066587969981635ULL, 0x7473638405257145ULL);
  __uint128_t arg3 = MakeUInt128(0x3142980919065925ULL, 0x0937221696461515ULL);
  __uint128_t res =
      ASM_INSN_WRAP_FUNC_W_RES_WW0_ARG("subhn2 %0.16b, %1.8h, %2.8h")(arg1, arg2, arg3);
  ASSERT_EQ(res, MakeUInt128(0x3142980919065925ULL, 0xf11413ef423bfc23ULL));
}

TEST(Arm64InsnTest, RoundingAddHighNarrowInt16x8) {
  __uint128_t arg1 = MakeUInt128(0x8039626579787718ULL, 0x5560845529654126ULL);
  __uint128_t arg2 = MakeUInt128(0x3440171274947042ULL, 0x0562230538994561ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("raddhn %0.8b, %1.8h, %2.8h")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x5ba76287b479eee7ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, RoundingSubHighNarrowInt16x8) {
  __uint128_t arg1 = MakeUInt128(0x3063432858785698ULL, 0x3052358089330657ULL);
  __uint128_t arg2 = MakeUInt128(0x0216471550979259ULL, 0x2309907965473761ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("rsubhn %0.8b, %1.8h, %2.8h")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x0da524cf2efc08c4ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, ScalarPairwiseAddInt8x2) {
  __uint128_t arg = MakeUInt128(0x6257591633303910ULL, 0x7225383742182140ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("addp %d0, %1.2d")(arg);
  ASSERT_EQ(res, MakeUInt128(0xd47c914d75485a50ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, AddAcrossInt8x8) {
  __uint128_t arg = MakeUInt128(0x0681216028764962ULL, 0x8674460477464915ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("addv %b0, %1.8b")(arg);
  ASSERT_EQ(res, MakeUInt128(0x51ULL, 0x0ULL));
}

TEST(Arm64InsnTest, SignedAddLongAcrossInt16x8) {
  __uint128_t arg = MakeUInt128(0x9699557377273756ULL, 0x6761552711392258ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("saddlv %s0, %1.8h")(arg);
  ASSERT_EQ(res, MakeUInt128(0x0000000000018aa2ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, UnsignedAddLongAcrossInt16x8) {
  __uint128_t arg = MakeUInt128(0x7986396522961312ULL, 0x8017826797172898ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("uaddlv %s0, %1.8h")(arg);
  ASSERT_EQ(res, MakeUInt128(0x000000000002aac0ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, SignedMaximumAcrossInt16x8) {
  __uint128_t arg = MakeUInt128(0x8482065967379473ULL, 0x1680864156456505ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("smaxv %h0, %1.8h")(arg);
  ASSERT_EQ(res, MakeUInt128(0x0000000000006737ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, SignedMinimumAcrossInt16x8) {
  __uint128_t arg = MakeUInt128(0x6772530431825197ULL, 0x5791679296996504ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("sminv %h0, %1.8h")(arg);
  ASSERT_EQ(res, MakeUInt128(0x0000000000009699ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, UnsignedMaximumAcrossInt16x8) {
  __uint128_t arg = MakeUInt128(0x6500378070466126ULL, 0x4706021457505793ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("umaxv %h0, %1.8h")(arg);
  ASSERT_EQ(res, MakeUInt128(0x0000000000007046ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, UnsignedMinimumAcrossInt16x8) {
  __uint128_t arg = MakeUInt128(0x5223572397395128ULL, 0x8181640597859142ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("uminv %h0, %1.8h")(arg);
  ASSERT_EQ(res, MakeUInt128(0x0000000000005128ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, CountLeadingZerosI8x8) {
  __uint128_t arg = MakeUInt128(0x1452635608277857ULL, 0x7134275778960917ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("clz %0.8b, %1.8b")(arg);
  ASSERT_EQ(res, MakeUInt128(0x0301010104020101ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, CountLeadingSignBitsI8x8) {
  __uint128_t arg = MakeUInt128(0x8925892354201995ULL, 0x6112129021960864ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("cls %0.8b, %1.8b")(arg);
  ASSERT_EQ(res, MakeUInt128(0x0001000100010200ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, Cnt) {
  __uint128_t arg = MakeUInt128(0x9835484875625298ULL, 0x7524238730775595ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("cnt %0.16b, %1.16b")(arg);
  ASSERT_EQ(res, MakeUInt128(0x0304020205030303ULL, 0x0502030402060404ULL));
}

TEST(Arm64InsnTest, SimdScalarMove) {
  __uint128_t arg = MakeUInt128(0x1433345477624168ULL, 0x6251898356948556ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("mov %b0, %1.b[5]")(arg);
  ASSERT_EQ(res, MakeUInt128(0x0000000000000034ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, SimdVectorElemDuplicate) {
  __uint128_t arg = MakeUInt128(0x3021647155097925ULL, 0x9230990796547376ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("dup %0.8b, %1.b[5]")(arg);
  ASSERT_EQ(res, MakeUInt128(0x6464646464646464ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, SimdVectorElemDuplicateInt16AtIndex7) {
  __uint128_t arg = MakeUInt128(0x2582262052248940ULL, 0x7726719478268482ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("dup %0.4h, %1.h[7]")(arg);
  ASSERT_EQ(res, MakeUInt128(0x7726772677267726ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, SimdVectorElemInsert) {
  __uint128_t arg1 = MakeUInt128(0x7120844335732654ULL, 0x8938239119325974ULL);
  __uint128_t arg2 = MakeUInt128(0x7656180937734440ULL, 0x3070746921120191ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W0_ARG("mov %0.s[2], %1.s[1]")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x7656180937734440ULL, 0x3070746971208443ULL));
}

TEST(Arm64InsnTest, NegateInt64x1) {
  constexpr auto AsmNeg = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("neg %d0, %d1");
  __uint128_t arg1 = MakeUInt128(0x8389522868478312ULL, 0x3552658213144957ULL);
  ASSERT_EQ(AsmNeg(arg1), MakeUInt128(0x7c76add797b87ceeULL, 0x0000000000000000ULL));

  __uint128_t arg2 = MakeUInt128(1ULL << 63, 0U);
  ASSERT_EQ(AsmNeg(arg2), MakeUInt128(1ULL << 63, 0U));
}

TEST(Arm64InsnTest, NegateInt16x8) {
  __uint128_t arg = MakeUInt128(0x4411010446823252ULL, 0x7162010526522721ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("neg %0.8h, %1.8h")(arg);
  ASSERT_EQ(res, MakeUInt128(0xbbeffefcb97ecdaeULL, 0x8e9efefbd9aed8dfULL));
}

TEST(Arm64InsnTest, NotI8x8) {
  __uint128_t arg = MakeUInt128(0x6205647693125705ULL, 0x8635662018558100ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("not %0.8b, %1.8b")(arg);
  ASSERT_EQ(res, MakeUInt128(0x9dfa9b896ceda8faULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, RbitInt8x8) {
  __uint128_t arg = MakeUInt128(0x4713296210734043ULL, 0x7518957359614589ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("rbit %0.8b, %1.8b")(arg);
  ASSERT_EQ(res, MakeUInt128(0xe2c8944608ce02c2ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, Rev16Int8x16) {
  __uint128_t arg = MakeUInt128(0x9904801094121472ULL, 0x2131794764777262ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("rev16 %0.16b, %1.16b")(arg);
  ASSERT_EQ(res, MakeUInt128(0x0499108012947214ULL, 0x3121477977646272ULL));
}

TEST(Arm64InsnTest, Rev32Int16x8) {
  __uint128_t arg = MakeUInt128(0x8662237172159160ULL, 0x7716692547487389ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("rev32 %0.8h, %1.8h")(arg);
  ASSERT_EQ(res, MakeUInt128(0x2371866291607215ULL, 0x6925771673894748ULL));
}

TEST(Arm64InsnTest, Rev64Int32x4) {
  __uint128_t arg = MakeUInt128(0x5306736096571209ULL, 0x1807638327166416ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("rev64 %0.4s, %1.4s")(arg);
  ASSERT_EQ(res, MakeUInt128(0x9657120953067360ULL, 0x2716641618076383ULL));
}

TEST(Arm64InsnTest, TblInt8x8) {
  __uint128_t arg1 = MakeUInt128(0x7766554433221100ULL, 0xffeeddccbbaa9988ULL);
  __uint128_t arg2 = MakeUInt128(0x0104011509120605ULL, 0x0315080907091312ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("tbl %0.8b, {%1.16b}, %2.8b")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x1144110099006655ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, TblInt8x16) {
  __uint128_t arg1 = MakeUInt128(0x7766554433221100ULL, 0xffeeddccbbaa9988ULL);
  __uint128_t arg2 = MakeUInt128(0x0905060808010408ULL, 0x0506000206030202ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("tbl %0.16b, {%1.16b}, %2.16b")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x9955668888114488ULL, 0x5566002266332222ULL));
}

TEST(Arm64InsnTest, Tbl2Int8x16) {
  __uint128_t arg1 = MakeUInt128(0x7766554433221100ULL, 0xffeeddccbbaa9988ULL);
  __uint128_t arg2 = MakeUInt128(0x76655443322110ffULL, 0xfeeddccbbaa99887ULL);
  __uint128_t arg3 = MakeUInt128(0x0224052800020910ULL, 0x1807280319002203ULL);
  __uint128_t res;

  // Hardcode v30 and v0 so that the TBL instruction gets consecutive registers.
  asm("mov v31.16b, %1.16b\n\t"
      "mov v0.16b, %2.16b\n\t"
      "tbl %0.16b, {v31.16b, v0.16b}, %3.16b"
      : "=w"(res)
      : "w"(arg1), "w"(arg2), "w"(arg3)
      : "v31", "v0");

  ASSERT_EQ(res, MakeUInt128(0x22005500002299ffULL, 0x8777003398000033ULL));
}

TEST(Arm64InsnTest, Tbl3Int8x16) {
  __uint128_t arg1 = MakeUInt128(0x7766554433221100ULL, 0xffeeddccbbaa9988ULL);
  __uint128_t arg2 = MakeUInt128(0x76655443322110ffULL, 0xfeeddccbbaa99887ULL);
  __uint128_t arg3 = MakeUInt128(0x7060504030201000ULL, 0xf0e0d0c0b0a09080ULL);
  __uint128_t arg4 = MakeUInt128(0x0718264039291035ULL, 0x3526190040211304ULL);
  __uint128_t res;

  // Hardcode v0, v1, and v2 so that the TBL instruction gets consecutive registers.
  asm("mov v30.16b, %1.16b\n\t"
      "mov v31.16b, %2.16b\n\t"
      "mov v0.16b, %3.16b\n\t"
      "tbl %0.16b, {v30.16b-v0.16b}, %4.16b"
      : "=w"(res)
      : "w"(arg1), "w"(arg2), "w"(arg3), "w"(arg4)
      : "v0", "v1", "v2");

  ASSERT_EQ(res, MakeUInt128(0x778760000090ff00ULL, 0x0060980000103244ULL));
}

TEST(Arm64InsnTest, Tbl4Int8x16) {
  __uint128_t arg1 = MakeUInt128(0x7766554433221100ULL, 0xffeeddccbbaa9988ULL);
  __uint128_t arg2 = MakeUInt128(0x76655443322110ffULL, 0xfeeddccbbaa99887ULL);
  __uint128_t arg3 = MakeUInt128(0x7060504030201000ULL, 0xf0e0d0c0b0a09080ULL);
  __uint128_t arg4 = MakeUInt128(0x7f6f5f4f3f2f1fffULL, 0xffefdfcfbfaf9f8fULL);
  __uint128_t arg5 = MakeUInt128(0x0718264039291035ULL, 0x3526190040211304ULL);
  __uint128_t res;

  // Hardcode v30, v31, v0, and v1 so that the TBX instruction gets consecutive registers.
  asm("mov v30.16b, %1.16b\n\t"
      "mov v31.16b, %2.16b\n\t"
      "mov v0.16b, %3.16b\n\t"
      "mov v1.16b, %4.16b\n\t"
      "tbl %0.16b, {v30.16b-v1.16b}, %5.16b"
      : "=w"(res)
      : "w"(arg1), "w"(arg2), "w"(arg3), "w"(arg4), "w"(arg5)
      : "v30", "v31", "v0", "v1");

  ASSERT_EQ(res, MakeUInt128(0x778760009f90ff5fULL, 0x5f60980000103244ULL));
}

TEST(Arm64InsnTest, TbxInt8x16) {
  __uint128_t arg1 = MakeUInt128(0x7766554433221100ULL, 0xffeeddccbbaa9988ULL);
  __uint128_t arg2 = MakeUInt128(0x0915061808010408ULL, 0x0516000206031202ULL);
  __uint128_t arg3 = MakeUInt128(0x6668559233565463ULL, 0x9138363185745698ULL);
  __uint128_t res =
      ASM_INSN_WRAP_FUNC_W_RES_WW0_ARG("tbx %0.16b, {%1.16b}, %2.16b")(arg1, arg2, arg3);
  ASSERT_EQ(res, MakeUInt128(0x9968669288114488ULL, 0x5538002266335622ULL));
}

TEST(Arm64InsnTest, Tbx2Int8x16) {
  __uint128_t arg1 = MakeUInt128(0x7766554433221100ULL, 0xffeeddccbbaa9988ULL);
  __uint128_t arg2 = MakeUInt128(0x76655443322110ffULL, 0xfeeddccbbaa99887ULL);
  __uint128_t arg3 = MakeUInt128(0x0224052800020910ULL, 0x1807280319002203ULL);
  __uint128_t res = MakeUInt128(0x7494078488442377ULL, 0x2175154334260306ULL);

  // Hardcode v0 and v1 so that the TBX instruction gets consecutive registers.
  asm("mov v0.16b, %1.16b\n\t"
      "mov v1.16b, %2.16b\n\t"
      "tbx %0.16b, {v0.16b, v1.16b}, %3.16b"
      : "=w"(res)
      : "w"(arg1), "w"(arg2), "w"(arg3), "0"(res)
      : "v0", "v1");

  ASSERT_EQ(res, MakeUInt128(0x22945584002299ffULL, 0x8777153398000333ULL));
}

TEST(Arm64InsnTest, Tbx3Int8x16) {
  __uint128_t arg1 = MakeUInt128(0x7766554433221100ULL, 0xffeeddccbbaa9988ULL);
  __uint128_t arg2 = MakeUInt128(0x76655443322110ffULL, 0xfeeddccbbaa99887ULL);
  __uint128_t arg3 = MakeUInt128(0x7060504030201000ULL, 0xf0e0d0c0b0a09080ULL);
  __uint128_t arg4 = MakeUInt128(0x0718264039291035ULL, 0x3526190040211304ULL);
  __uint128_t res = MakeUInt128(0x0136776310849135ULL, 0x1615642269847507ULL);

  // Hardcode v0, v1, and v2 so that the TBX instruction gets consecutive registers.
  asm("mov v0.16b, %1.16b\n\t"
      "mov v1.16b, %2.16b\n\t"
      "mov v2.16b, %3.16b\n\t"
      "tbx %0.16b, {v0.16b, v1.16b, v2.16b}, %4.16b"
      : "=w"(res)
      : "w"(arg1), "w"(arg2), "w"(arg3), "w"(arg4), "0"(res)
      : "v0", "v1", "v2");

  ASSERT_EQ(res, MakeUInt128(0x778760631090ff35ULL, 0x1660980069103244ULL));
}

TEST(Arm64InsnTest, Tbx4Int8x16) {
  __uint128_t arg1 = MakeUInt128(0x7766554433221100ULL, 0xffeeddccbbaa9988ULL);
  __uint128_t arg2 = MakeUInt128(0x76655443322110ffULL, 0xfeeddccbbaa99887ULL);
  __uint128_t arg3 = MakeUInt128(0x7060504030201000ULL, 0xf0e0d0c0b0a09080ULL);
  __uint128_t arg4 = MakeUInt128(0x7f6f5f4f3f2f1fffULL, 0xffefdfcfbfaf9f8fULL);
  __uint128_t arg5 = MakeUInt128(0x0718264039291035ULL, 0x3526190040211304ULL);
  __uint128_t res = MakeUInt128(0x5818319637637076ULL, 0x1799191920357958ULL);

  // Hardcode v0, v1, v2, and v3 so that the TBX instruction gets consecutive registers.
  asm("mov v0.16b, %1.16b\n\t"
      "mov v1.16b, %2.16b\n\t"
      "mov v2.16b, %3.16b\n\t"
      "mov v3.16b, %4.16b\n\t"
      "tbx %0.16b, {v0.16b-v3.16b}, %5.16b"
      : "=w"(res)
      : "w"(arg1), "w"(arg2), "w"(arg3), "w"(arg4), "w"(arg5), "0"(res)
      : "v0", "v1", "v2", "v3");

  ASSERT_EQ(res, MakeUInt128(0x778760969f90ff5fULL, 0x5f60980020103244ULL));
}

TEST(Arm64InsnTest, Trn1Int8x8) {
  __uint128_t arg1 = MakeUInt128(0x2075916729700785ULL, 0x0580717186381054ULL);
  __uint128_t arg2 = MakeUInt128(0x2786099055690013ULL, 0x4137182368370991ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("trn1 %0.8b, %1.8b, %2.8b")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x8675906769701385ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, Trn2Int16x8) {
  __uint128_t arg1 = MakeUInt128(0x6685592335654639ULL, 0x1383631857456981ULL);
  __uint128_t arg2 = MakeUInt128(0x7494078488442377ULL, 0x2175154334260306ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("trn2 %0.8h, %1.8h, %2.8h")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x7494668588443565ULL, 0x2175138334265745ULL));
}

TEST(Arm64InsnTest, Uzp1Int8x8) {
  __uint128_t arg1 = MakeUInt128(0x4954893139394489ULL, 0x9216125525597701ULL);
  __uint128_t arg2 = MakeUInt128(0x2783467926101995ULL, 0x5852247172201777ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("uzp1 %0.8b, %1.8b, %2.8b")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x8379109554313989ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, Uzp2Int16x8) {
  __uint128_t arg1 = MakeUInt128(0x6745642390585850ULL, 0x2167190313952629ULL);
  __uint128_t arg2 = MakeUInt128(0x3620129476918749ULL, 0x7519101147231528ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("uzp2 %0.8h, %1.8h, %2.8h")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x2167139567459058ULL, 0x7519472336207691ULL));
}

TEST(Arm64InsnTest, Zip2Int64x2) {
  __uint128_t arg1 = MakeUInt128(0x1494271410093913ULL, 0x6913810725813781ULL);
  __uint128_t arg2 = MakeUInt128(0x3578940055995001ULL, 0x8354251184172136ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("uzp2 %0.2d, %1.2d, %2.2d")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x6913810725813781ULL, 0x8354251184172136ULL));
}

TEST(Arm64InsnTest, Zip1Int8x8) {
  __uint128_t arg1 = MakeUInt128(0x7499235630254947ULL, 0x8024901141952123ULL);
  __uint128_t arg2 = MakeUInt128(0x3331239480494707ULL, 0x9119153267343028ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("zip1 %0.8b, %1.8b, %2.8b")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x8030492547490747ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, Zip1Int64x2) {
  __uint128_t arg1 = MakeUInt128(0x9243530136776310ULL, 0x8491351615642269ULL);
  __uint128_t arg2 = MakeUInt128(0x0551199581831963ULL, 0x7637076179919192ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("zip1 %0.2d, %1.2d, %2.2d")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x9243530136776310ULL, 0x0551199581831963ULL));
}

TEST(Arm64InsnTest, Zip2Int16x8) {
  __uint128_t arg1 = MakeUInt128(0x5831832713142517ULL, 0x0296923488962766ULL);
  __uint128_t arg2 = MakeUInt128(0x2934595889706953ULL, 0x6534940603402166ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("zip2 %0.8h, %1.8h, %2.8h")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x0340889621662766ULL, 0x6534029694069234ULL));
}

TEST(Arm64InsnTest, SignedMaxInt16x8) {
  __uint128_t arg1 = MakeUInt128(0x9901573466102371ULL, 0x2235478911292547ULL);
  __uint128_t arg2 = MakeUInt128(0x4922157650450812ULL, 0x0677173571202718ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("smax %0.8h, %1.8h, %2.8h")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x4922573466102371ULL, 0x2235478971202718ULL));
}

TEST(Arm64InsnTest, SignedMinInt16x8) {
  __uint128_t arg1 = MakeUInt128(0x7820385653909910ULL, 0x4775941413215432ULL);
  __uint128_t arg2 = MakeUInt128(0x0084531214065935ULL, 0x8090412711359200ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("smin %0.8h, %1.8h, %2.8h")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x0084385614069910ULL, 0x8090941411359200ULL));
}

TEST(Arm64InsnTest, SignedMaxPairwiseInt16x8) {
  __uint128_t arg1 = MakeUInt128(0x6998469884770232ULL, 0x3823840055655517ULL);
  __uint128_t arg2 = MakeUInt128(0x3272867600724817ULL, 0x2987637569816335ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("smaxp %0.8h, %1.8h, %2.8h")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x3823556569980232ULL, 0x6375698132724817ULL));
}

TEST(Arm64InsnTest, SignedMinPairwiseInt16x8) {
  __uint128_t arg1 = MakeUInt128(0x8865701568501691ULL, 0x8647488541679154ULL);
  __uint128_t arg2 = MakeUInt128(0x1821553559732353ULL, 0x0686043010675760ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("sminp %0.8h, %1.8h, %2.8h")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x8647915488651691ULL, 0x0430106718212353ULL));
}

TEST(Arm64InsnTest, UnsignedMaxInt16x8) {
  __uint128_t arg1 = MakeUInt128(0x7639975974619383ULL, 0x5845749159880976ULL);
  __uint128_t arg2 = MakeUInt128(0x5928493695941434ULL, 0x0814685298150539ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("umax %0.8h, %1.8h, %2.8h")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x7639975995949383ULL, 0x5845749198150976ULL));
}

TEST(Arm64InsnTest, UnsignedMinInt16x8) {
  __uint128_t arg1 = MakeUInt128(0x2888773717663748ULL, 0x6027660634960353ULL);
  __uint128_t arg2 = MakeUInt128(0x6983349515101986ULL, 0x4269887847171939ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("umin %0.8h, %1.8h, %2.8h")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x2888349515101986ULL, 0x4269660634960353ULL));
}

TEST(Arm64InsnTest, UnsignedMaxPairwiseInt16x8) {
  __uint128_t arg1 = MakeUInt128(0x1318583584066747ULL, 0x2370297149785084ULL);
  __uint128_t arg2 = MakeUInt128(0x4570249413983163ULL, 0x4332378975955680ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("umaxp %0.8h, %1.8h, %2.8h")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x2971508458358406ULL, 0x4332759545703163ULL));
}

TEST(Arm64InsnTest, UnsignedMinPairwiseInt16x8) {
  __uint128_t arg1 = MakeUInt128(0x9538121791319145ULL, 0x1350099384631177ULL);
  __uint128_t arg2 = MakeUInt128(0x7769055481028850ULL, 0x2080858008781157ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("uminp %0.8h, %1.8h, %2.8h")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x0993117712179131ULL, 0x2080087805548102ULL));
}

TEST(Arm64InsnTest, SignedHalvingAddInt16x8) {
  __uint128_t arg1 = MakeUInt128(0x1021944719713869ULL, 0x2560841624511239ULL);
  __uint128_t arg2 = MakeUInt128(0x8062011318454124ULL, 0x4782050110798760ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("shadd %0.8h, %1.8h, %2.8h")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0xc841caad18db3cc6ULL, 0x3671c48b1a65ccccULL));
}

TEST(Arm64InsnTest, SignedHalvingSubInt16x8) {
  __uint128_t arg1 = MakeUInt128(0x9041210873032402ULL, 0x0106853419472304ULL);
  __uint128_t arg2 = MakeUInt128(0x7666672174986986ULL, 0x8547076781205124ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("shsub %0.8h, %1.8h, %2.8h")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x8ceddcf3ff35dd3eULL, 0x3ddfbee64c13e8f0ULL));
}

TEST(Arm64InsnTest, SignedRoundingHalvingAddInt16x8) {
  __uint128_t arg1 = MakeUInt128(0x5871487839890810ULL, 0x7429530941060596ULL);
  __uint128_t arg2 = MakeUInt128(0x9443158477539700ULL, 0x9439883949144323ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("srhadd %0.8h, %1.8h, %2.8h")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0xf65a2efe586ecf88ULL, 0x0431eda1450d245dULL));
}

TEST(Arm64InsnTest, SignedAbsoluteDifferenceInt16x8) {
  __uint128_t arg1 = MakeUInt128(0x1349607501116498ULL, 0x3278563531614516ULL);
  __uint128_t arg2 = MakeUInt128(0x8457695687109002ULL, 0x9997698412632665ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("sabd %0.8h, %1.8h, %2.8h")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x8ef208e17a01d496ULL, 0x98e1134f1efe1eb1ULL));
}

TEST(Arm64InsnTest, SignedAbsoluteDifferenceLongInt16x8) {
  __uint128_t arg1 = MakeUInt128(0x7419850973346267ULL, 0x9332107268687076ULL);
  __uint128_t arg2 = MakeUInt128(0x8062639919361965ULL, 0x0440995421676278ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("sabdl %0.4s, %1.4h, %2.4h")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x000059fe00004902ULL, 0x0000f3b70000de90ULL));
}

TEST(Arm64InsnTest, SignedAbsoluteDifferenceLongUpperInt16x8) {
  __uint128_t arg1 = MakeUInt128(0x4980559610330799ULL, 0x4145347784574699ULL);
  __uint128_t arg2 = MakeUInt128(0x9921285999993996ULL, 0x1228161521931488ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("sabdl2 %0.4s, %1.8h, %2.8h")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x00009d3c00003211ULL, 0x00002f1d00001e62ULL));
}

TEST(Arm64InsnTest, SignedAbsoluteDifferenceAccumulateInt16x8) {
  __uint128_t arg1 = MakeUInt128(0x8967003192586625ULL, 0x9410510533584384ULL);
  __uint128_t arg2 = MakeUInt128(0x6560233917967492ULL, 0x6784476370847497ULL);
  __uint128_t arg3 = MakeUInt128(0x8333655579007384ULL, 0x1914731988627135ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW0_ARG("saba %0.8h, %1.8h, %2.8h")(arg1, arg2, arg3);
  ASSERT_EQ(res, MakeUInt128(0x5f2c885dfe3e81f1ULL, 0xec887cbbc58ea248ULL));
}

TEST(Arm64InsnTest, SignedAbsoluteDifferenceAccumulateLongInt16x4) {
  __uint128_t arg1 = MakeUInt128(0x078464167452167ULL, 0x719048310967671ULL);
  __uint128_t arg2 = MakeUInt128(0x344349481926268ULL, 0x110739948250607ULL);
  __uint128_t arg3 = MakeUInt128(0x949507350316901ULL, 0x731852119552635ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW0_ARG("sabal %0.4s, %1.4h, %2.4h")(arg1, arg2, arg3);
  ASSERT_EQ(res, MakeUInt128(0x094a36265031aa02ULL, 0x073187ed195537e2ULL));
}

TEST(Arm64InsnTest, SignedAbsoluteDifferenceLongInt32x2) {
  __uint128_t arg1 = MakeUInt128(0x000000007fffffffULL, 0x0000000000000000ULL);
  __uint128_t arg2 = MakeUInt128(0x0000000080000000ULL, 0x0000000000000000ULL);
  __uint128_t arg3 = MakeUInt128(0x0000000000000000ULL, 0x0000000000000000ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW0_ARG("sabal %0.2d, %1.2s, %2.2s")(arg1, arg2, arg3);
  ASSERT_EQ(res, MakeUInt128(0x00000000ffffffffULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, SignedAbsoluteDifferenceAccumulateLongUpperInt16x8) {
  __uint128_t arg1 = MakeUInt128(0x690943470482932ULL, 0x414041114654092ULL);
  __uint128_t arg2 = MakeUInt128(0x988344435159133ULL, 0x010773944111840ULL);
  __uint128_t arg3 = MakeUInt128(0x410768498106634ULL, 0x241048239358274ULL);
  __uint128_t res =
      ASM_INSN_WRAP_FUNC_W_RES_WW0_ARG("sabal2 %0.4s, %1.8h, %2.8h")(arg1, arg2, arg3);
  ASSERT_EQ(res, MakeUInt128(0x0410a63098108e86ULL, 0x024108863935f59cULL));
}

TEST(Arm64InsnTest, UnsignedHalvingAddInt16x8) {
  __uint128_t arg1 = MakeUInt128(0x4775379853799732ULL, 0x2344561227858432ULL);
  __uint128_t arg2 = MakeUInt128(0x9684664751333657ULL, 0x3692387201464723ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("uhadd %0.8h, %1.8h, %2.8h")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x6efc4eef525666c4ULL, 0x2ceb4742146565aaULL));
}

TEST(Arm64InsnTest, UnsignedHalvingSubInt16x8) {
  __uint128_t arg1 = MakeUInt128(0x9926884349592876ULL, 0x1240075587569464ULL);
  __uint128_t arg2 = MakeUInt128(0x1370562514001179ULL, 0x7133166207153715ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("uhsub %0.8h, %1.8h, %2.8h")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x42db190f1aac0b7eULL, 0xd086f87940202ea7ULL));
}

TEST(Arm64InsnTest, UnsignedRoundingHalvingAddInt16x8) {
  __uint128_t arg1 = MakeUInt128(0x5066533985738887ULL, 0x8661476294434140ULL);
  __uint128_t arg2 = MakeUInt128(0x1049888993160051ULL, 0x2076781035886116ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("urhadd %0.8h, %1.8h, %2.8h")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x30586de18c45446cULL, 0x536c5fb964e6512bULL));
}

TEST(Arm64InsnTest, UnsignedAbsoluteDifferenceInt16x8) {
  __uint128_t arg1 = MakeUInt128(0x8574664607722834ULL, 0x1540311441529418ULL);
  __uint128_t arg2 = MakeUInt128(0x8047825438761770ULL, 0x7904300015669867ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("uabd %0.8h, %1.8h, %2.8h")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x052d1c0e310410c4ULL, 0x63c401142bec044fULL));
}

TEST(Arm64InsnTest, UnsignedAbsoluteDifferenceLongInt16x8) {
  __uint128_t arg1 = MakeUInt128(0x1614585505839727ULL, 0x4209809097817293ULL);
  __uint128_t arg2 = MakeUInt128(0x2393010676638682ULL, 0x4040111304024700ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("uabdl %0.4s, %1.4h, %2.4h")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x000070e0000010a5ULL, 0x00000d7f0000574fULL));
}

TEST(Arm64InsnTest, UnsignedAbsoluteDifferenceLongUpperInt16x8) {
  __uint128_t arg1 = MakeUInt128(0x0347999588867695ULL, 0x0161249722820403ULL);
  __uint128_t arg2 = MakeUInt128(0x0399546327883069ULL, 0x5976249361510102ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("uabdl2 %0.4s, %1.8h, %2.8h")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x00003ecf00000301ULL, 0x0000581500000004ULL));
}

TEST(Arm64InsnTest, UnsignedAbsoluteDifferenceAccumulateInt16x8) {
  __uint128_t arg1 = MakeUInt128(0x0857466460772283ULL, 0x4154031144152941ULL);
  __uint128_t arg2 = MakeUInt128(0x8804782543876177ULL, 0x0790430001566986ULL);
  __uint128_t arg3 = MakeUInt128(0x7767957609099669ULL, 0x3607559496515273ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW0_ARG("uaba %0.8h, %1.8h, %2.8h")(arg1, arg2, arg3);
  ASSERT_EQ(res, MakeUInt128(0xf714c73725f9d55dULL, 0x6fcb9583d91092b8ULL));
}

TEST(Arm64InsnTest, UnsignedAbsoluteDifferenceAccumulateLongInt16x4) {
  __uint128_t arg1 = MakeUInt128(0x8343417044157348ULL, 0x2481833301640566ULL);
  __uint128_t arg2 = MakeUInt128(0x9596688667695634ULL, 0x9141632842641497ULL);
  __uint128_t arg3 = MakeUInt128(0x4533349999480002ULL, 0x6699875888159350ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW0_ARG("uabal %0.4s, %1.4h, %2.4h")(arg1, arg2, arg3);
  ASSERT_EQ(res, MakeUInt128(0x453357ed99481d16ULL, 0x669999ab8815ba66ULL));
}

TEST(Arm64InsnTest, UnsignedAbsoluteDifferenceAccumulateLongUpperInt16x8) {
  __uint128_t arg1 = MakeUInt128(0x998685541703188ULL, 0x778867592902607ULL);
  __uint128_t arg2 = MakeUInt128(0x043212666179192ULL, 0x352093822787888ULL);
  __uint128_t arg3 = MakeUInt128(0x988633599116081ULL, 0x235355570464634ULL);
  __uint128_t res =
      ASM_INSN_WRAP_FUNC_W_RES_WW0_ARG("uabal2 %0.4s, %1.8h, %2.8h")(arg1, arg2, arg3);
  ASSERT_EQ(res, MakeUInt128(0x0988d34d9911b302ULL, 0x0235397b7046c371ULL));
}

TEST(Arm64InsnTest, SignedAddLongPairwiseInt8x16) {
  __uint128_t arg = MakeUInt128(0x6164411096256633ULL, 0x7305409219519675ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("saddlp %0.8h, %1.16b")(arg);
  ASSERT_EQ(res, MakeUInt128(0x00c50051ffbb0099ULL, 0x0078ffd2006a000bULL));
}

TEST(Arm64InsnTest, SignedAddAccumulateLongPairwiseInt8x16) {
  __uint128_t arg1 = MakeUInt128(0x1991646384142707ULL, 0x7988708874229277ULL);
  __uint128_t arg2 = MakeUInt128(0x7217826030500994ULL, 0x5108247835729056ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W0_ARG("sadalp %0.8h, %1.16b")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x71c183272fe809c2ULL, 0x510924703608905fULL));
}

TEST(Arm64InsnTest, SignedAddAccumulateLongPairwiseInt16x8) {
  __uint128_t arg1 = MakeUInt128(0x1991646384142707ULL, 0x7988708874229277ULL);
  __uint128_t arg2 = MakeUInt128(0x7217826030500994ULL, 0x5108247835729056ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W0_ARG("sadalp %0.4s, %1.8h")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x72180054304fb4afULL, 0x51090e88357296efULL));
}

TEST(Arm64InsnTest, UnsignedAddLongPairwiseInt8x16) {
  __uint128_t arg = MakeUInt128(0x1483287348089574ULL, 0x7777527834422109ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("uaddlp %0.8h, %1.16b")(arg);
  ASSERT_EQ(res, MakeUInt128(0x0097009b00500109ULL, 0x00ee00ca0076002aULL));
}

TEST(Arm64InsnTest, UnsignedAddAccumulateLongPairwiseInt8x16) {
  __uint128_t arg1 = MakeUInt128(0x9348154691631162ULL, 0x4928873574718824ULL);
  __uint128_t arg2 = MakeUInt128(0x5207665738825139ULL, 0x6391635767231510ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W0_ARG("uadalp %0.8h, %1.16b")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x52e266b2397651acULL, 0x64026413680815bcULL));
}

TEST(Arm64InsnTest, SignedAddLong) {
  __uint128_t arg1 = MakeUInt128(0x3478074585067606ULL, 0x3048229409653041ULL);
  __uint128_t arg2 = MakeUInt128(0x1183066710818930ULL, 0x3110887172816751ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("saddl %0.4s, %1.4h, %2.4h")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0xffff9587ffffff36ULL, 0x000045fb00000dacULL));
}

TEST(Arm64InsnTest, SignedAddLongUpper) {
  __uint128_t arg1 = MakeUInt128(0x3160683158679946ULL, 0x0165205774052942ULL);
  __uint128_t arg2 = MakeUInt128(0x3053601780313357ULL, 0x2632670547903384ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("saddl2 %0.4s, %1.8h, %2.8h")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x0000bb9500005cc6ULL, 0x000027970000875cULL));
}

TEST(Arm64InsnTest, SignedSubLong) {
  __uint128_t arg1 = MakeUInt128(0x8566746260879482ULL, 0x0186474876727272ULL);
  __uint128_t arg2 = MakeUInt128(0x2206267646533809ULL, 0x9801966883680994ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("ssubl %0.4s, %1.4h, %2.4h")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x00001a34ffff5c79ULL, 0xffff636000004decULL));
}

TEST(Arm64InsnTest, SignedSubLongUpper) {
  __uint128_t arg1 = MakeUInt128(0x3011331753305329ULL, 0x8020166888174813ULL);
  __uint128_t arg2 = MakeUInt128(0x4298868158557781ULL, 0x0343231753064784ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("ssubl2 %0.4s, %1.8h, %2.8h")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0xffff35110000008fULL, 0xffff7cddfffff351ULL));
}

TEST(Arm64InsnTest, UnsignedAddLong) {
  __uint128_t arg1 = MakeUInt128(0x3126059505777727ULL, 0x5424712416483128ULL);
  __uint128_t arg2 = MakeUInt128(0x3298207236175057ULL, 0x4673870128209575ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("uaddl %0.4s, %1.4h, %2.4h")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x00003b8e0000c77eULL, 0x000063be00002607ULL));
}

TEST(Arm64InsnTest, UnsignedAddLongUpper) {
  __uint128_t arg1 = MakeUInt128(0x3384698499778726ULL, 0x7065551918544686ULL);
  __uint128_t arg2 = MakeUInt128(0x9846947849573462ULL, 0x2606294219624557ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("uaddl2 %0.4s, %1.8h, %2.8h")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x000031b600008bddULL, 0x0000966b00007e5bULL));
}

TEST(Arm64InsnTest, UnsignedSubLong) {
  __uint128_t arg1 = MakeUInt128(0x4378111988556318ULL, 0x7777925372011667ULL);
  __uint128_t arg2 = MakeUInt128(0x1853954183598443ULL, 0x8305203762819440ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("usubl %0.4s, %1.4h, %2.4h")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x000004fcffffded5ULL, 0x00002b25ffff7bd8ULL));
}

TEST(Arm64InsnTest, UnsignedSubLongUpper) {
  __uint128_t arg1 = MakeUInt128(0x5228717440266638ULL, 0x9148817173086436ULL);
  __uint128_t arg2 = MakeUInt128(0x1113890694202790ULL, 0x8814311944879941ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("usubl2 %0.4s, %1.8h, %2.8h")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x00002e81ffffcaf5ULL, 0x0000093400005058ULL));
}

TEST(Arm64InsnTest, SignedAddWide) {
  __uint128_t arg1 = MakeUInt128(0x7844598183134112ULL, 0x9001999205981352ULL);
  __uint128_t arg2 = MakeUInt128(0x2051173365856407ULL, 0x8264849427644113ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("saddw %0.4s, %1.4s, %2.4h")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x7844bf068313a519ULL, 0x9001b9e305982a85ULL));
}

TEST(Arm64InsnTest, SignedAddWideUpper) {
  __uint128_t arg1 = MakeUInt128(0x3407092233436577ULL, 0x9160128093179401ULL);
  __uint128_t arg2 = MakeUInt128(0x7185985999338492ULL, 0x3549564005709955ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("saddw2 %0.4s, %1.4s, %2.8h")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x34070e923342feccULL, 0x916047c99317ea41ULL));
}

TEST(Arm64InsnTest, SignedSubWide) {
  __uint128_t arg1 = MakeUInt128(0x2302847007312065ULL, 0x8032626417116165ULL);
  __uint128_t arg2 = MakeUInt128(0x9576132723515666ULL, 0x6253667271899853ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("ssubw %0.4s, %1.4s, %2.4h")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x2302611f0730c9ffULL, 0x8032ccee17114e3eULL));
}

TEST(Arm64InsnTest, SignedSubWideUpper) {
  __uint128_t arg1 = MakeUInt128(0x4510824783572905ULL, 0x6919885554678860ULL);
  __uint128_t arg2 = MakeUInt128(0x7946280537122704ULL, 0x2466543192145281ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("ssubw2 %0.4s, %1.4s, %2.8h")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x4510f0338356d684ULL, 0x691963ef5467342fULL));
}

TEST(Arm64InsnTest, UnsignedAddWide) {
  __uint128_t arg1 = MakeUInt128(0x5870785951298344ULL, 0x1729535195378855ULL);
  __uint128_t arg2 = MakeUInt128(0x3457374260859029ULL, 0x0817651557803905ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("uaddw %0.4s, %1.4s, %2.4h")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x5870d8de512a136dULL, 0x172987a89537bf97ULL));
}

TEST(Arm64InsnTest, UnsignedAddWideUpper) {
  __uint128_t arg1 = MakeUInt128(0x7516493270950493ULL, 0x4639382432227188ULL);
  __uint128_t arg2 = MakeUInt128(0x5159740547021482ULL, 0x8971117779237612ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("uaddw2 %0.4s, %1.4s, %2.8h")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x7516c25570957aa5ULL, 0x4639c195322282ffULL));
}

TEST(Arm64InsnTest, UnsignedSubWide) {
  __uint128_t arg1 = MakeUInt128(0x0625247972199786ULL, 0x6854279897799233ULL);
  __uint128_t arg2 = MakeUInt128(0x9579057581890622ULL, 0x5254735822052364ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("usubw %0.4s, %1.4s, %2.4h")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x0624a2f072199164ULL, 0x6853921f97798cbeULL));
}

TEST(Arm64InsnTest, UnsignedSubWideUpper) {
  __uint128_t arg1 = MakeUInt128(0x8242392192695062ULL, 0x0831838145469839ULL);
  __uint128_t arg2 = MakeUInt128(0x2366461363989101ULL, 0x2102177095976704ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("usubw2 %0.4s, %1.4s, %2.8h")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x8241a38a9268e95eULL, 0x0831627f454680c9ULL));
}

TEST(Arm64InsnTest, SignedMultiplyLongInt8x8) {
  __uint128_t arg1 = MakeUInt128(0x9191791552241718ULL, 0x9585361680594741ULL);
  __uint128_t arg2 = MakeUInt128(0x2341933984202187ULL, 0x4564925644346239ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("smull %0.8h, %1.8b, %2.8b")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0xd848048002f7f4a8ULL, 0xf0d3e3d1cc7b04adULL));
}

TEST(Arm64InsnTest, SignedMultiplyLongInt8x8Upper) {
  __uint128_t arg1 = MakeUInt128(0x9314052976347574ULL, 0x8119356709110137ULL);
  __uint128_t arg2 = MakeUInt128(0x7517210080315590ULL, 0x2485309066920376ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("smull2 %0.8h, %1.16b, %2.16b")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x0396f8b20003195aULL, 0xee24f3fd09f0d2f0ULL));
}

TEST(Arm64InsnTest, UnsignedMultiplyLongInt8x8) {
  __uint128_t arg1 = MakeUInt128(0x9149055628425039ULL, 0x1275771028402799ULL);
  __uint128_t arg2 = MakeUInt128(0x8066365825488926ULL, 0x4880254566101729ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("umull %0.8h, %1.8b, %2.8b")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x05c812902ad00876ULL, 0x48801d16010e1d90ULL));
}

TEST(Arm64InsnTest, UnsignedMultiplyLongInt8x8Upper) {
  __uint128_t arg1 = MakeUInt128(0x9709683408005355ULL, 0x9849175417381883ULL);
  __uint128_t arg2 = MakeUInt128(0x9994469748676265ULL, 0x5165827658483588ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("umull2 %0.8h, %1.16b, %2.16b")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x07e80fc004f84598ULL, 0x30181ccd0bae26b8ULL));
}

TEST(Arm64InsnTest, SignedMultiplyLongInt8x8IndexedElem) {
  __uint128_t arg1 = MakeUInt128(0x9293459588970695ULL, 0x3653494060340216ULL);
  __uint128_t arg2 = MakeUInt128(0x6544375589004563ULL, 0x2882250545255640ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("smull %0.4s, %1.4h, %2.h[2]")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0xe630cb23016c3279ULL, 0xe8593fcf0f0a1d79ULL));
}

TEST(Arm64InsnTest, SignedMultiplyLongInt8x8IndexedElemUpper) {
  __uint128_t arg1 = MakeUInt128(0x9279068212073883ULL, 0x7781423356282360ULL);
  __uint128_t arg2 = MakeUInt128(0x8963208068222468ULL, 0x0122482611771858ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("smull2 %0.4s, %1.8h, %2.h[2]")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x0af01400047db000ULL, 0x0f2be08008677980ULL));
}

TEST(Arm64InsnTest, UnsignedMultiplyLongInt8x8IndexedElem) {
  __uint128_t arg1 = MakeUInt128(0x9086996033027634ULL, 0x7870810817545011ULL);
  __uint128_t arg2 = MakeUInt128(0x9307141223390866ULL, 0x3938339529425786ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("umull %0.4s, %1.4h, %2.h[2]")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x03ffbe2409445fa8ULL, 0x0b54a16c0c0648c0ULL));
}

TEST(Arm64InsnTest, UnsignedMultiplyLongInt8x8IndexedElem2) {
  __uint128_t arg1 = MakeUInt128(0x9132710495478599ULL, 0x1801969678353214ULL);
  __uint128_t arg2 = MakeUInt128(0x6444118926063152ULL, 0x6618167443193550ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("umull %0.4s, %1.4h, %2.h[4]")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x1f1659301bd26cd0ULL, 0x1e3cb9a017892540ULL));
}

TEST(Arm64InsnTest, UnsignedMultiplyLongInt8x8IndexedElemUpper) {
  __uint128_t arg1 = MakeUInt128(0x9815793678976697ULL, 0x4220575059683440ULL);
  __uint128_t arg2 = MakeUInt128(0x8697350201410206ULL, 0x7235850200724522ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("umull2 %0.4s, %1.8h, %2.h[2]")(arg1, arg2);
  ASSERT_EQ(res, MakeUInt128(0x12833ad00ad1a880ULL, 0x0db1244012143ea0ULL));
}

TEST(Arm64InsnTest, SignedMultiplyAddLongInt8x8) {
  __uint128_t arg1 = MakeUInt128(0x9779940012601642ULL, 0x2760926082349304ULL);
  __uint128_t arg2 = MakeUInt128(0x1180643829138347ULL, 0x3546797253992623ULL);
  __uint128_t arg3 = MakeUInt128(0x3879158299848645ULL, 0x9271734059225620ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW0_ARG("smlal %0.8h, %1.8b, %2.8b")(arg1, arg2, arg3);
  ASSERT_EQ(res, MakeUInt128(0x3b5b1ca28ec69893ULL, 0x8b7836c02ef25620ULL));
}

TEST(Arm64InsnTest, SignedMultiplyAddLongInt8x8Upper) {
  __uint128_t arg1 = MakeUInt128(0x5514435021828702ULL, 0x6685610665003531ULL);
  __uint128_t arg2 = MakeUInt128(0x0502163182060176ULL, 0x0921798468493686ULL);
  __uint128_t arg3 = MakeUInt128(0x3161293727951873ULL, 0x0789726373537171ULL);
  __uint128_t res =
      ASM_INSN_WRAP_FUNC_W_RES_WW0_ARG("smlal2 %0.8h, %1.16b, %2.16b")(arg1, arg2, arg3);
  ASSERT_EQ(res, MakeUInt128(0x5a69293732c30119ULL, 0x0b1f6288a12c6e89ULL));
}

TEST(Arm64InsnTest, SignedMultiplySubtractLongInt8x8) {
  __uint128_t arg1 = MakeUInt128(0x9662539339538092ULL, 0x2195591918188552ULL);
  __uint128_t arg2 = MakeUInt128(0x6780621499231727ULL, 0x6316321833989693ULL);
  __uint128_t arg3 = MakeUInt128(0x8075616855911752ULL, 0x9984501320671293ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW0_ARG("smlsl %0.8h, %1.8b, %2.8b")(arg1, arg2, arg3);
  ASSERT_EQ(res, MakeUInt128(0x9764560f61112814ULL, 0xc42a811300a11b17ULL));
}

TEST(Arm64InsnTest, SignedMultiplySubtractLongInt8x8Upper) {
  __uint128_t arg1 = MakeUInt128(0x9826903089111856ULL, 0x8798692947051352ULL);
  __uint128_t arg2 = MakeUInt128(0x4816091743243015ULL, 0x3836847072928989ULL);
  __uint128_t arg3 = MakeUInt128(0x8284602223730145ULL, 0x2655679898627767ULL);
  __uint128_t res =
      ASM_INSN_WRAP_FUNC_W_RES_WW0_ARG("smlsl2 %0.8h, %1.16b, %2.16b")(arg1, arg2, arg3);
  ASSERT_EQ(res, MakeUInt128(0x62e662482c482763ULL, 0x40cd7d88cb3e6577ULL));
}

TEST(Arm64InsnTest, SignedMultiplyAddLongInt16x4) {
  __uint128_t arg1 = MakeUInt128(0x9779940012601642ULL, 0x2760926082349304ULL);
  __uint128_t arg2 = MakeUInt128(0x1180643829138347ULL, 0x3546797253992623ULL);
  __uint128_t arg3 = MakeUInt128(0x3879158299848645ULL, 0x9271734059225620ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW0_ARG("smlal %0.4s, %1.4h, %2.4h")(arg1, arg2, arg3);
  ASSERT_EQ(res, MakeUInt128(0x3b6bd2a28eac7893ULL, 0x8b4c38c02edab620ULL));
}

TEST(Arm64InsnTest, UnsignedMultiplyAddLongInt8x8) {
  __uint128_t arg1 = MakeUInt128(0x9696920253886503ULL, 0x4577183176686885ULL);
  __uint128_t arg2 = MakeUInt128(0x9236814884752764ULL, 0x9846882194973972ULL);
  __uint128_t arg3 = MakeUInt128(0x9707737187188400ULL, 0x4143231276365048ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW0_ARG("umlal %0.8h, %1.8b, %2.8b")(arg1, arg2, arg3);
  ASSERT_EQ(res, MakeUInt128(0xc1d3b199967b852cULL, 0x96cf42b6bfc850d8ULL));
}

TEST(Arm64InsnTest, UnsignedMultiplyAddLongInt8x8Upper) {
  __uint128_t arg1 = MakeUInt128(0x9055637695252326ULL, 0x5361442478023082ULL);
  __uint128_t arg2 = MakeUInt128(0x6811831037735887ULL, 0x0892406130313364ULL);
  __uint128_t arg3 = MakeUInt128(0x7737101162821461ULL, 0x4661679404090518ULL);
  __uint128_t res =
      ASM_INSN_WRAP_FUNC_W_RES_WW0_ARG("umlal2 %0.8h, %1.16b, %2.16b")(arg1, arg2, arg3);
  ASSERT_EQ(res, MakeUInt128(0x8db710736c124729ULL, 0x48f99ee6150912bcULL));
}

TEST(Arm64InsnTest, UnsignedMultiplySubtractLongInt8x8) {
  __uint128_t arg1 = MakeUInt128(0x4577772457520386ULL, 0x5437542828256714ULL);
  __uint128_t arg2 = MakeUInt128(0x1288583454443513ULL, 0x2562054464241011ULL);
  __uint128_t arg3 = MakeUInt128(0x0379554641905811ULL, 0x6862305964476958ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_WW0_ARG("umlsl %0.8h, %1.8b, %2.8b")(arg1, arg2, arg3);
  ASSERT_EQ(res, MakeUInt128(0xe6ed3f7e40f14e1fULL, 0x6388f1213b5f6208ULL));
}

TEST(Arm64InsnTest, UnsignedMultiplySubtractLongInt8x8Upper) {
  __uint128_t arg1 = MakeUInt128(0x4739376564336319ULL, 0x7978680367187307ULL);
  __uint128_t arg2 = MakeUInt128(0x9693924236321448ULL, 0x4503547763156702ULL);
  __uint128_t arg3 = MakeUInt128(0x5539006542311792ULL, 0x0153464977929066ULL);
  __uint128_t res =
      ASM_INSN_WRAP_FUNC_W_RES_WW0_ARG("umlsl2 %0.8h, %1.16b, %2.16b")(arg1, arg2, arg3);
  ASSERT_EQ(res, MakeUInt128(0x2d64fe6d13ec1784ULL, 0xe0b644e155728f01ULL));
}

TEST(Arm64InsnTest, SignedShiftLeftInt64x1) {
  constexpr auto AsmSshl = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("sshl %d0, %d1, %d2");
  __uint128_t arg = MakeUInt128(0x9007497297363549ULL, 0x6453328886984406ULL);
  ASSERT_EQ(AsmSshl(arg, -65), MakeUInt128(0xffffffffffffffffULL, 0x0000000000000000ULL));
  ASSERT_EQ(AsmSshl(arg, -64), MakeUInt128(0xffffffffffffffffULL, 0x0000000000000000ULL));
  ASSERT_EQ(AsmSshl(arg, -63), MakeUInt128(0xffffffffffffffffULL, 0x0000000000000000ULL));
  ASSERT_EQ(AsmSshl(arg, -1), MakeUInt128(0xc803a4b94b9b1aa4ULL, 0x0000000000000000ULL));
  ASSERT_EQ(AsmSshl(arg, 0), MakeUInt128(0x9007497297363549ULL, 0x0000000000000000ULL));
  ASSERT_EQ(AsmSshl(arg, 1), MakeUInt128(0x200e92e52e6c6a92ULL, 0x0000000000000000ULL));
  ASSERT_EQ(AsmSshl(arg, 63), MakeUInt128(0x8000000000000000ULL, 0x0000000000000000ULL));
  ASSERT_EQ(AsmSshl(arg, 64), MakeUInt128(0x0000000000000000ULL, 0x0000000000000000ULL));
  ASSERT_EQ(AsmSshl(arg, 65), MakeUInt128(0x0000000000000000ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, SignedRoundingShiftLeftInt64x1) {
  constexpr auto AsmSrshl = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("srshl %d0, %d1, %d2");
  __uint128_t arg = MakeUInt128(0x9276457931065792ULL, 0x2955249887275846ULL);
  ASSERT_EQ(AsmSrshl(arg, -65), MakeUInt128(0x0000000000000000ULL, 0x0000000000000000ULL));
  ASSERT_EQ(AsmSrshl(arg, -64), MakeUInt128(0x0000000000000000ULL, 0x0000000000000000ULL));
  ASSERT_EQ(AsmSrshl(arg, -63), MakeUInt128(0xffffffffffffffffULL, 0x0000000000000000ULL));
  ASSERT_EQ(AsmSrshl(arg, -1), MakeUInt128(0xc93b22bc98832bc9ULL, 0x0000000000000000ULL));
  ASSERT_EQ(AsmSrshl(arg, 0), MakeUInt128(0x9276457931065792ULL, 0x0000000000000000ULL));
  ASSERT_EQ(AsmSrshl(arg, 1), MakeUInt128(0x24ec8af2620caf24ULL, 0x0000000000000000ULL));
  ASSERT_EQ(AsmSrshl(arg, 63), MakeUInt128(0x0000000000000000ULL, 0x0000000000000000ULL));
  ASSERT_EQ(AsmSrshl(arg, 64), MakeUInt128(0x0000000000000000ULL, 0x0000000000000000ULL));
  ASSERT_EQ(AsmSrshl(arg, 65), MakeUInt128(0x0000000000000000ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, UnsignedShiftLeftInt64x1) {
  constexpr auto AsmUshl = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("ushl %d0, %d1, %d2");
  __uint128_t arg = MakeUInt128(0x9138296682468185ULL, 0x7103188790652870ULL);
  ASSERT_EQ(AsmUshl(arg, -65), MakeUInt128(0x0000000000000000ULL, 0x0000000000000000ULL));
  ASSERT_EQ(AsmUshl(arg, -64), MakeUInt128(0x0000000000000000ULL, 0x0000000000000000ULL));
  ASSERT_EQ(AsmUshl(arg, -63), MakeUInt128(0x0000000000000001ULL, 0x0000000000000000ULL));
  ASSERT_EQ(AsmUshl(arg, -1), MakeUInt128(0x489c14b3412340c2ULL, 0x0000000000000000ULL));
  ASSERT_EQ(AsmUshl(arg, 0), MakeUInt128(0x9138296682468185ULL, 0x0000000000000000ULL));
  ASSERT_EQ(AsmUshl(arg, 1), MakeUInt128(0x227052cd048d030aULL, 0x0000000000000000ULL));
  ASSERT_EQ(AsmUshl(arg, 63), MakeUInt128(0x8000000000000000ULL, 0x0000000000000000ULL));
  ASSERT_EQ(AsmUshl(arg, 64), MakeUInt128(0x0000000000000000ULL, 0x0000000000000000ULL));
  ASSERT_EQ(AsmUshl(arg, 65), MakeUInt128(0x0000000000000000ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, UnsignedRoundingShiftLeftInt64x1) {
  constexpr auto AsmUrshl = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("urshl %d0, %d1, %d2");
  __uint128_t arg = MakeUInt128(0x9023452924407736ULL, 0x5949563051007421ULL);
  ASSERT_EQ(AsmUrshl(arg, -65), MakeUInt128(0x0000000000000000ULL, 0x0000000000000000ULL));
  ASSERT_EQ(AsmUrshl(arg, -64), MakeUInt128(0x0000000000000001ULL, 0x0000000000000000ULL));
  ASSERT_EQ(AsmUrshl(arg, -63), MakeUInt128(0x0000000000000001ULL, 0x0000000000000000ULL));
  ASSERT_EQ(AsmUrshl(arg, -1), MakeUInt128(0x4811a29492203b9bULL, 0x0000000000000000ULL));
  ASSERT_EQ(AsmUrshl(arg, 0), MakeUInt128(0x9023452924407736ULL, 0x0000000000000000ULL));
  ASSERT_EQ(AsmUrshl(arg, 1), MakeUInt128(0x20468a524880ee6cULL, 0x0000000000000000ULL));
  ASSERT_EQ(AsmUrshl(arg, 63), MakeUInt128(0x0000000000000000ULL, 0x0000000000000000ULL));
  ASSERT_EQ(AsmUrshl(arg, 64), MakeUInt128(0x0000000000000000ULL, 0x0000000000000000ULL));
  ASSERT_EQ(AsmUrshl(arg, 65), MakeUInt128(0x0000000000000000ULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, SignedShiftLeftInt16x8) {
  constexpr auto AsmSshl = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("sshl %0.8h, %1.8h, %2.8h");
  __uint128_t arg1 = MakeUInt128(0x9999999999999999ULL, 0x9999999999999999ULL);
  __uint128_t arg2 = MakeUInt128(0x0010000f00020001ULL, 0xfffffff1fff0ffefULL);
  ASSERT_EQ(AsmSshl(arg1, arg2), MakeUInt128(0x0000800066643332ULL, 0xccccffffffffffffULL));
  ASSERT_EQ(AsmSshl(arg1, 0), MakeUInt128(0x9999999999999999ULL, 0x9999999999999999ULL));
}

TEST(Arm64InsnTest, SignedRoundingShiftLeftInt16x8) {
  constexpr auto AsmSrshl = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("srshl %0.8h, %1.8h, %2.8h");
  __uint128_t arg1 = MakeUInt128(0x9999999999999999ULL, 0x9999999999999999ULL);
  __uint128_t arg2 = MakeUInt128(0x0010000f00020001ULL, 0xfffffff1fff0ffefULL);
  ASSERT_EQ(AsmSrshl(arg1, arg2), MakeUInt128(0x0000800066643332ULL, 0xcccdffff00000000ULL));
  ASSERT_EQ(AsmSrshl(arg1, 0), MakeUInt128(0x9999999999999999ULL, 0x9999999999999999ULL));
}

TEST(Arm64InsnTest, UnsignedShiftLeftInt16x8) {
  constexpr auto AsmUshl = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("ushl %0.8h, %1.8h, %2.8h");
  __uint128_t arg1 = MakeUInt128(0x9999999999999999ULL, 0x9999999999999999ULL);
  __uint128_t arg2 = MakeUInt128(0x0010000f00020001ULL, 0xfffffff1fff0ffefULL);
  ASSERT_EQ(AsmUshl(arg1, arg2), MakeUInt128(0x0000800066643332ULL, 0x4ccc000100000000ULL));
  ASSERT_EQ(AsmUshl(arg1, 0), MakeUInt128(0x9999999999999999ULL, 0x9999999999999999ULL));
}

TEST(Arm64InsnTest, UnsignedRoundingShiftLeftInt16x8) {
  constexpr auto AsmUrshl = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("urshl %0.8h, %1.8h, %2.8h");
  __uint128_t arg1 = MakeUInt128(0x9999999999999999ULL, 0x9999999999999999ULL);
  __uint128_t arg2 = MakeUInt128(0x0010000f00020001ULL, 0xfffffff1fff0ffefULL);
  ASSERT_EQ(AsmUrshl(arg1, arg2), MakeUInt128(0x0000800066643332ULL, 0x4ccd000100010000ULL));
  ASSERT_EQ(AsmUrshl(arg1, 0), MakeUInt128(0x9999999999999999ULL, 0x9999999999999999ULL));
}

TEST(Arm64InsnTest, UnsignedReciprocalSquareRootEstimateInt32x4) {
  __uint128_t arg = MakeUInt128(0x9641122821407533ULL, 0x0265510042410489ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("ursqrte %0.4s, %1.4s")(arg);
  ASSERT_EQ(res, MakeUInt128(0xa7000000ffffffffULL, 0xfffffffffb800000ULL));
}

TEST(Arm64InsnTest, UnsignedReciprocalEstimateInt32x4) {
  __uint128_t arg = MakeUInt128(0x9714864899468611ULL, 0x2476054286734367ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("urecpe %0.4s, %1.4s")(arg);
  ASSERT_EQ(res, MakeUInt128(0xd8800000d6000000ULL, 0xfffffffff4000000ULL));
}

bool IsQcBitSet(uint32_t fpsr) {
  return (fpsr & kFpsrQcBit) != 0;
}

TEST(Arm64InsnTest, SignedSaturatingAddInt64x1) {
  constexpr auto AsmSqadd = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("sqadd %d0, %d2, %d3");

  __uint128_t arg1 = MakeUInt128(0x4342527753119724ULL, 0x7430873043619511ULL);
  __uint128_t arg2 = MakeUInt128(0x3961190800302558ULL, 0x7838764420608504ULL);
  auto [res1, fpsr1] = AsmSqadd(arg1, arg2);
  ASSERT_EQ(res1, MakeUInt128(0x7ca36b7f5341bc7cULL, 0x0000000000000000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeUInt128(0x2557185308919284ULL, 0x4038050710300647ULL);
  __uint128_t arg4 = MakeUInt128(0x7684786324319100ULL, 0x0223929785255372ULL);
  auto [res2, fpsr2] = AsmSqadd(arg3, arg4);
  ASSERT_EQ(res2, MakeUInt128(0x7fffffffffffffffULL, 0x0000000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingAddInt32x4) {
  constexpr auto AsmSqadd = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("sqadd %0.4s, %2.4s, %3.4s");

  __uint128_t arg1 = MakeUInt128(0x9883554445602495ULL, 0x5666843660292219ULL);
  __uint128_t arg2 = MakeUInt128(0x5124830910605377ULL, 0x2019802183101032ULL);
  auto [res1, fpsr1] = AsmSqadd(arg1, arg2);
  ASSERT_EQ(res1, MakeUInt128(0xe9a7d84d55c0780cULL, 0x76800457e339324bULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeUInt128(0x9713308844617410ULL, 0x7959162511714864ULL);
  __uint128_t arg4 = MakeUInt128(0x8744686112476054ULL, 0x2867343670904667ULL);
  auto [res2, fpsr2] = AsmSqadd(arg3, arg4);
  ASSERT_EQ(res2, MakeUInt128(0x8000000056a8d464ULL, 0x7fffffff7fffffffULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, UnsignedSaturatingAddInt8x1) {
  constexpr auto AsmUqadd = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("uqadd %b0, %b2, %b3");

  __uint128_t arg1 = MakeUInt128(0x6017174229960273ULL, 0x5310276871944944ULL);
  __uint128_t arg2 = MakeUInt128(0x4917939785144631ULL, 0x5973144353518504ULL);
  auto [res1, fpsr1] = AsmUqadd(arg1, arg2);
  ASSERT_EQ(res1, MakeUInt128(0x00000000000000a4ULL, 0x0000000000000000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeUInt128(0x3306263695626490ULL, 0x9108276271159038ULL);
  __uint128_t arg4 = MakeUInt128(0x5699505124652999ULL, 0x6062855443838330ULL);
  auto [res2, fpsr2] = AsmUqadd(arg3, arg4);
  ASSERT_EQ(res2, MakeUInt128(0x00000000000000ffULL, 0x0000000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, UnsignedSaturatingAddInt64x1) {
  constexpr auto AsmUqadd = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("uqadd %d0, %d2, %d3");

  __uint128_t arg1 = MakeUInt128(0x0606885137234627ULL, 0x0799732723313469ULL);
  __uint128_t arg2 = MakeUInt128(0x3971456285542615ULL, 0x4676506324656766ULL);
  auto [res1, fpsr1] = AsmUqadd(arg1, arg2);
  ASSERT_EQ(res1, MakeUInt128(0x3f77cdb3bc776c3cULL, 0x0000000000000000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeUInt128(0x9534957018600154ULL, 0x1262396228641389ULL);
  __uint128_t arg4 = MakeUInt128(0x7796733329070567ULL, 0x3769621564981845ULL);
  auto [res2, fpsr2] = AsmUqadd(arg3, arg4);
  ASSERT_EQ(res2, MakeUInt128(0xffffffffffffffffULL, 0x0000000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, UnsignedSaturatingAddInt32x4) {
  constexpr auto AsmUqadd = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("uqadd %0.4s, %2.4s, %3.4s");

  __uint128_t arg1 = MakeUInt128(0x9737425700735921ULL, 0x0031541508936793ULL);
  __uint128_t arg2 = MakeUInt128(0x0081699805365202ULL, 0x7600727749674584ULL);
  auto [res1, fpsr1] = AsmUqadd(arg1, arg2);
  ASSERT_EQ(res1, MakeUInt128(0x97b8abef05a9ab23ULL, 0x7631c68c51faad17ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeUInt128(0x9727856471983963ULL, 0x0878154322116691ULL);
  __uint128_t arg4 = MakeUInt128(0x8654522268126887ULL, 0x2684459684424161ULL);
  auto [res2, fpsr2] = AsmUqadd(arg3, arg4);
  ASSERT_EQ(res2, MakeUInt128(0xffffffffd9aaa1eaULL, 0x2efc5ad9a653a7f2ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingSubtractInt32x1) {
  constexpr auto AsmSqsub = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("sqsub %s0, %s2, %s3");

  __uint128_t arg1 = MakeUInt128(0x3178534870760322ULL, 0x1982970579751191ULL);
  __uint128_t arg2 = MakeUInt128(0x4405109942358830ULL, 0x3454635349234982ULL);
  auto [res1, fpsr1] = AsmSqsub(arg1, arg2);
  ASSERT_EQ(res1, MakeUInt128(0x2e407af2ULL, 0U));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeUInt128(0x1423696483086410ULL, 0x2592887457999322ULL);
  __uint128_t arg4 = MakeUInt128(0x3749551912219519ULL, 0x0342445230753513ULL);
  auto [res2, fpsr2] = AsmSqsub(arg3, arg4);
  ASSERT_EQ(res2, MakeUInt128(0x80000000ULL, 0U));
  ASSERT_TRUE(IsQcBitSet(fpsr2));

  __uint128_t arg5 = MakeUInt128(0x3083508879584152ULL, 0x1489912761065137ULL);
  __uint128_t arg6 = MakeUInt128(0x4153943580721139ULL, 0x0328574918769094ULL);
  auto [res3, fpsr3] = AsmSqsub(arg5, arg6);
  ASSERT_EQ(res3, MakeUInt128(0x7fffffffULL, 0U));
  ASSERT_TRUE(IsQcBitSet(fpsr3));
}

TEST(Arm64InsnTest, SignedSaturatingSubtractInt64x1) {
  constexpr auto AsmSqsub = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("sqsub %d0, %d2, %d3");

  __uint128_t arg1 = MakeUInt128(0x4416125223196943ULL, 0x4712064173754912ULL);
  __uint128_t arg2 = MakeUInt128(0x1635700857369439ULL, 0x7305979709719726ULL);
  auto [res1, fpsr1] = AsmSqsub(arg1, arg2);
  ASSERT_EQ(res1, MakeUInt128(0x2de0a249cbe2d50aULL, 0x0000000000000000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeUInt128(0x7862766490242516ULL, 0x1990277471090335ULL);
  __uint128_t arg4 = MakeUInt128(0x9333093049483805ULL, 0x9785662884478744ULL);
  auto [res2, fpsr2] = AsmSqsub(arg3, arg4);
  ASSERT_EQ(res2, MakeUInt128(0x7fffffffffffffffULL, 0x0000000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingSubtractInt32x4) {
  constexpr auto AsmSqsub = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("sqsub %0.4s, %2.4s, %3.4s");

  __uint128_t arg1 = MakeUInt128(0x4485680977569630ULL, 0x3129588719161129ULL);
  __uint128_t arg2 = MakeUInt128(0x2946818849363386ULL, 0x4739274760122696ULL);
  auto [res1, fpsr1] = AsmSqsub(arg1, arg2);
  ASSERT_EQ(res1, MakeUInt128(0x1b3ee6812e2062aaULL, 0xe9f03140b903ea93ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeUInt128(0x9304127100727784ULL, 0x9301555038895360ULL);
  __uint128_t arg4 = MakeUInt128(0x3382619293437970ULL, 0x8187432094991415ULL);
  auto [res2, fpsr2] = AsmSqsub(arg3, arg4);
  ASSERT_EQ(res2, MakeUInt128(0x800000006d2efe14ULL, 0x117a12307fffffffULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, UnsignedSaturatingSubtractInt32x1) {
  constexpr auto AsmUqsub = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("uqsub %s0, %s2, %s3");

  __uint128_t arg1 = MakeUInt128(0x2548156091372812ULL, 0x8406333039373562ULL);
  __uint128_t arg2 = MakeUInt128(0x4200160456645574ULL, 0x1458816605216660ULL);
  auto [res1, fpsr1] = AsmUqsub(arg1, arg2);
  ASSERT_EQ(res1, MakeUInt128(0x3ad2d29eULL, 0U));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeUInt128(0x1259960281839309ULL, 0x5487090590738613ULL);
  __uint128_t arg4 = MakeUInt128(0x5191459181951029ULL, 0x7327875571049729ULL);
  auto [res2, fpsr2] = AsmUqsub(arg3, arg4);
  ASSERT_EQ(res2, MakeUInt128(0U, 0U));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, UnsignedSaturatingSubtractInt64x1) {
  constexpr auto AsmUqsub = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("uqsub %d0, %d2, %d3");

  __uint128_t arg1 = MakeUInt128(0x9691077542576474ULL, 0x8832534141213280ULL);
  __uint128_t arg2 = MakeUInt128(0x0626717094009098ULL, 0x2235296579579978ULL);
  auto [res1, fpsr1] = AsmUqsub(arg1, arg2);
  ASSERT_EQ(res1, MakeUInt128(0x906a9604ae56d3dcULL, 0x0000000000000000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeUInt128(0x7752929106925043ULL, 0x2614469501098610ULL);
  __uint128_t arg4 = MakeUInt128(0x8889991465855188ULL, 0x1873582528164302ULL);
  auto [res2, fpsr2] = AsmUqsub(arg3, arg4);
  ASSERT_EQ(res2, MakeUInt128(0x0000000000000000ULL, 0x0000000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, UnsignedSaturatingSubtractInt32x4) {
  constexpr auto AsmUqsub = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("uqsub %0.4s, %2.4s, %3.4s");

  __uint128_t arg1 = MakeUInt128(0x6884962578665885ULL, 0x9991798675205545ULL);
  __uint128_t arg2 = MakeUInt128(0x5809900455646117ULL, 0x8755249370124553ULL);
  auto [res1, fpsr1] = AsmUqsub(arg1, arg2);
  ASSERT_EQ(res1, MakeUInt128(0x107b06212301f76eULL, 0x123c54f3050e0ff2ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeUInt128(0x5032678340586301ULL, 0x9301932429963972ULL);
  __uint128_t arg4 = MakeUInt128(0x0444517928812285ULL, 0x4478211953530898ULL);
  auto [res2, fpsr2] = AsmUqsub(arg3, arg4);
  ASSERT_EQ(res2, MakeUInt128(0x4bee160a17d7407cULL, 0x4e89720b00000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingAbsoluteInt8x1) {
  constexpr auto AsmSqabs = ASM_INSN_WRAP_FUNC_WQ_RES_W_ARG("sqabs %b0, %b2");

  __uint128_t arg1 = MakeUInt128(0x8918016855727981ULL, 0x5642185819119749ULL);
  auto [res1, fpsr1] = AsmSqabs(arg1);
  ASSERT_EQ(res1, MakeUInt128(0x000000000000007fULL, 0x0000000000000000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg2 = MakeUInt128(0x0000000000000080ULL, 0x6464607287574305ULL);
  auto [res2, fpsr2] = AsmSqabs(arg2);
  ASSERT_EQ(res2, MakeUInt128(0x000000000000007fULL, 0x0000000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingAbsoluteInt64x1) {
  constexpr auto AsmSqabs = ASM_INSN_WRAP_FUNC_WQ_RES_W_ARG("sqabs %d0, %d2");

  __uint128_t arg1 = MakeUInt128(0x9717317281315179ULL, 0x3290443112181587ULL);
  auto [res1, fpsr1] = AsmSqabs(arg1);
  ASSERT_EQ(res1, MakeUInt128(0x68e8ce8d7eceae87ULL, 0x0000000000000000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg2 = MakeUInt128(0x8000000000000000ULL, 0x1001237687219447ULL);
  auto [res2, fpsr2] = AsmSqabs(arg2);
  ASSERT_EQ(res2, MakeUInt128(0x7fffffffffffffffULL, 0x0000000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingAbsoluteInt32x4) {
  constexpr auto AsmSqabs = ASM_INSN_WRAP_FUNC_WQ_RES_W_ARG("sqabs %0.4s, %2.4s");

  __uint128_t arg1 = MakeUInt128(0x9133820578492800ULL, 0x6982551957402018ULL);
  auto [res1, fpsr1] = AsmSqabs(arg1);
  ASSERT_EQ(res1, MakeUInt128(0x6ecc7dfb78492800ULL, 0x6982551957402018ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg2 = MakeUInt128(0x1810564129725083ULL, 0x6070356880000000ULL);
  auto [res2, fpsr2] = AsmSqabs(arg2);
  ASSERT_EQ(res2, MakeUInt128(0x1810564129725083ULL, 0x607035687fffffffULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingNegateInt32x1) {
  constexpr auto AsmSqneg = ASM_INSN_WRAP_FUNC_WQ_RES_W_ARG("sqneg %s0, %s2");

  __uint128_t arg1 = MakeUInt128(0x6461582694563802ULL, 0x3950283712168644ULL);
  auto [res1, fpsr1] = AsmSqneg(arg1);
  ASSERT_EQ(res1, MakeUInt128(0x000000006ba9c7feULL, 0x0000000000000000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg2 = MakeUInt128(0x6561785280000000ULL, 0x1277128269186886ULL);
  auto [res2, fpsr2] = AsmSqneg(arg2);
  ASSERT_EQ(res2, MakeUInt128(0x000000007fffffffULL, 0x0000000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingNegateInt64x1) {
  constexpr auto AsmSqneg = ASM_INSN_WRAP_FUNC_WQ_RES_W_ARG("sqneg %d0, %d2");

  __uint128_t arg1 = MakeUInt128(0x9703600795698276ULL, 0x2639234410714658ULL);
  auto [res1, fpsr1] = AsmSqneg(arg1);
  ASSERT_EQ(res1, MakeUInt128(0x68fc9ff86a967d8aULL, 0x0000000000000000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg2 = MakeUInt128(0x8000000000000000ULL, 0x4052295369374997ULL);
  auto [res2, fpsr2] = AsmSqneg(arg2);
  ASSERT_EQ(res2, MakeUInt128(0x7fffffffffffffffULL, 0x0000000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingNegateInt32x4) {
  constexpr auto AsmSqneg = ASM_INSN_WRAP_FUNC_WQ_RES_W_ARG("sqneg %0.4s, %2.4s");

  __uint128_t arg1 = MakeUInt128(0x9172320202822291ULL, 0x4886959399729974ULL);
  auto [res1, fpsr1] = AsmSqneg(arg1);
  ASSERT_EQ(res1, MakeUInt128(0x6e8dcdfefd7ddd6fULL, 0xb7796a6d668d668cULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg2 = MakeUInt128(0x2974711553718589ULL, 0x2423849380000000ULL);
  auto [res2, fpsr2] = AsmSqneg(arg2);
  ASSERT_EQ(res2, MakeUInt128(0xd68b8eebac8e7a77ULL, 0xdbdc7b6d7fffffffULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingShiftLeftImmInt32x1) {
  constexpr auto AsmSqshl = ASM_INSN_WRAP_FUNC_WQ_RES_W_ARG("sqshl %s0, %s2, #20");

  __uint128_t arg1 = MakeUInt128(0x9724611600000181ULL, 0x0003509892864120ULL);
  auto [res1, fpsr1] = AsmSqshl(arg1);
  ASSERT_EQ(res1, MakeUInt128(0x0000000018100000ULL, 0x0000000000000000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg2 = MakeUInt128(0x4195163551108763ULL, 0x2042676129798265ULL);
  auto [res2, fpsr2] = AsmSqshl(arg2);
  ASSERT_EQ(res2, MakeUInt128(0x000000007fffffffULL, 0x0000000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingShiftLeftImmInt64x1) {
  constexpr auto AsmSqshl = ASM_INSN_WRAP_FUNC_WQ_RES_W_ARG("sqshl %d0, %d2, #28");

  __uint128_t arg1 = MakeUInt128(0x0000000774000539ULL, 0x2622760323659751ULL);
  auto [res1, fpsr1] = AsmSqshl(arg1);
  ASSERT_EQ(res1, MakeUInt128(0x7740005390000000ULL, 0x0000000000000000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg2 = MakeUInt128(0x9938714995449137ULL, 0x3020518436690767ULL);
  auto [res2, fpsr2] = AsmSqshl(arg2);
  ASSERT_EQ(res2, MakeUInt128(0x8000000000000000ULL, 0x0000000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingShiftLeftImmInt32x4) {
  constexpr auto AsmSqshl = ASM_INSN_WRAP_FUNC_WQ_RES_W_ARG("sqshl %0.4s, %2.4s, #12");

  __uint128_t arg1 = MakeUInt128(0x0007256800042011ULL, 0x0000313500033555ULL);
  auto [res1, fpsr1] = AsmSqshl(arg1);
  ASSERT_EQ(res1, MakeUInt128(0x7256800042011000ULL, 0x0313500033555000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg2 = MakeUInt128(0x0944031900072034ULL, 0x8651010561049872ULL);
  auto [res2, fpsr2] = AsmSqshl(arg2);
  ASSERT_EQ(res2, MakeUInt128(0x7fffffff72034000ULL, 0x800000007fffffffULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingShiftLeftByRegisterImmInt32x1) {
  constexpr auto AsmSqshl = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("sqshl %s0, %s2, %s3");

  __uint128_t res;
  uint32_t fpsr;
  __uint128_t arg1 = MakeUInt128(0x7480771811555330ULL, 0x9098870255052076ULL);

  std::tie(res, fpsr) = AsmSqshl(arg1, -33);
  ASSERT_EQ(res, MakeUInt128(0U, 0U));
  ASSERT_FALSE(IsQcBitSet(fpsr));

  std::tie(res, fpsr) = AsmSqshl(arg1, -32);
  ASSERT_EQ(res, MakeUInt128(0U, 0U));
  ASSERT_FALSE(IsQcBitSet(fpsr));

  std::tie(res, fpsr) = AsmSqshl(arg1, -31);
  ASSERT_EQ(res, MakeUInt128(0U, 0U));
  ASSERT_FALSE(IsQcBitSet(fpsr));

  std::tie(res, fpsr) = AsmSqshl(arg1, -1);
  ASSERT_EQ(res, MakeUInt128(0x08aaa998ULL, 0U));
  ASSERT_FALSE(IsQcBitSet(fpsr));

  std::tie(res, fpsr) = AsmSqshl(arg1, 0);
  ASSERT_EQ(res, MakeUInt128(0x11555330ULL, 0U));
  ASSERT_FALSE(IsQcBitSet(fpsr));

  std::tie(res, fpsr) = AsmSqshl(arg1, 1);
  ASSERT_EQ(res, MakeUInt128(0x22aaa660ULL, 0U));
  ASSERT_FALSE(IsQcBitSet(fpsr));

  std::tie(res, fpsr) = AsmSqshl(arg1, 31);
  ASSERT_EQ(res, MakeUInt128(0x7fffffffULL, 0U));
  ASSERT_TRUE(IsQcBitSet(fpsr));

  std::tie(res, fpsr) = AsmSqshl(arg1, 32);
  ASSERT_EQ(res, MakeUInt128(0x7fffffffULL, 0U));
  ASSERT_TRUE(IsQcBitSet(fpsr));

  std::tie(res, fpsr) = AsmSqshl(arg1, 33);
  ASSERT_EQ(res, MakeUInt128(0x7fffffffULL, 0U));
  ASSERT_TRUE(IsQcBitSet(fpsr));
}

TEST(Arm64InsnTest, UnsignedSaturatingShiftLeftImmInt64x1) {
  constexpr auto AsmUqshl = ASM_INSN_WRAP_FUNC_WQ_RES_W_ARG("uqshl %d0, %d2, #28");

  __uint128_t arg1 = MakeUInt128(0x0000000961573564ULL, 0x8883443185280853ULL);
  auto [res1, fpsr1] = AsmUqshl(arg1);
  ASSERT_EQ(res1, MakeUInt128(0x9615735640000000ULL, 0x0000000000000000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg2 = MakeUInt128(0x9759277344336553ULL, 0x8418834030351782ULL);
  auto [res2, fpsr2] = AsmUqshl(arg2);
  ASSERT_EQ(res2, MakeUInt128(0xffffffffffffffffULL, 0x0000000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, UnsignedSaturatingShiftLeftImmInt32x4) {
  constexpr auto AsmUqshl = ASM_INSN_WRAP_FUNC_WQ_RES_W_ARG("uqshl %0.4s, %2.4s, #12");

  __uint128_t arg1 = MakeUInt128(0x0000326300096218ULL, 0x0004565900066853ULL);
  auto [res1, fpsr1] = AsmUqshl(arg1);
  ASSERT_EQ(res1, MakeUInt128(0x0326300096218000ULL, 0x4565900066853000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg2 = MakeUInt128(0x0009911314010804ULL, 0x0009732335449090ULL);
  auto [res2, fpsr2] = AsmUqshl(arg2);
  ASSERT_EQ(res2, MakeUInt128(0x99113000ffffffffULL, 0x97323000ffffffffULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, UnsignedSaturatingShiftLeftByRegisterImmInt32x1) {
  constexpr auto AsmUqshl = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("uqshl %s0, %s2, %s3");

  __uint128_t res;
  uint32_t fpsr;
  __uint128_t arg1 = MakeUInt128(0x9714978507414585ULL, 0x3085781339156270ULL);

  std::tie(res, fpsr) = AsmUqshl(arg1, -33);
  ASSERT_EQ(res, MakeUInt128(0U, 0U));
  ASSERT_FALSE(IsQcBitSet(fpsr));

  std::tie(res, fpsr) = AsmUqshl(arg1, -32);
  ASSERT_EQ(res, MakeUInt128(0U, 0U));
  ASSERT_FALSE(IsQcBitSet(fpsr));

  std::tie(res, fpsr) = AsmUqshl(arg1, -31);
  ASSERT_EQ(res, MakeUInt128(0U, 0U));
  ASSERT_FALSE(IsQcBitSet(fpsr));

  std::tie(res, fpsr) = AsmUqshl(arg1, -1);
  ASSERT_EQ(res, MakeUInt128(0x03a0a2c2ULL, 0U));
  ASSERT_FALSE(IsQcBitSet(fpsr));

  std::tie(res, fpsr) = AsmUqshl(arg1, 0);
  ASSERT_EQ(res, MakeUInt128(0x07414585ULL, 0U));
  ASSERT_FALSE(IsQcBitSet(fpsr));

  std::tie(res, fpsr) = AsmUqshl(arg1, 1);
  ASSERT_EQ(res, MakeUInt128(0x0e828b0aULL, 0U));
  ASSERT_FALSE(IsQcBitSet(fpsr));

  std::tie(res, fpsr) = AsmUqshl(arg1, 31);
  ASSERT_EQ(res, MakeUInt128(0xffffffffULL, 0U));
  ASSERT_TRUE(IsQcBitSet(fpsr));

  std::tie(res, fpsr) = AsmUqshl(arg1, 32);
  ASSERT_EQ(res, MakeUInt128(0xffffffffULL, 0U));
  ASSERT_TRUE(IsQcBitSet(fpsr));

  std::tie(res, fpsr) = AsmUqshl(arg1, 33);
  ASSERT_EQ(res, MakeUInt128(0xffffffffULL, 0U));
  ASSERT_TRUE(IsQcBitSet(fpsr));
}

TEST(Arm64InsnTest, SignedSaturatingShiftLeftByRegisterImmInt16x8) {
  constexpr auto AsmSqshl = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("sqshl %0.8h, %2.8h, %3.8h");

  __uint128_t arg1 = 0U;
  __uint128_t arg2 = MakeUInt128(0xffdfffe0ffe1ffffULL, 0x0001001f00200021ULL);
  auto [res1, fpsr1] = AsmSqshl(arg1, arg2);
  ASSERT_EQ(res1, MakeUInt128(0U, 0U));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeUInt128(0x3333333333333333ULL, 0x3333333333333333ULL);
  auto [res2, fpsr2] = AsmSqshl(arg3, arg2);
  ASSERT_EQ(res2, MakeUInt128(0x0000000000001999ULL, 0x66667fff7fff7fffULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, UnsignedSaturatingShiftLeftByRegisterImmInt16x8) {
  constexpr auto AsmUqshl = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("uqshl %0.8h, %2.8h, %3.8h");

  __uint128_t arg1 = 0U;
  __uint128_t arg2 = MakeUInt128(0xffdfffe0ffe1ffffULL, 0x0001001f00200021ULL);
  auto [res1, fpsr1] = AsmUqshl(arg1, arg2);
  ASSERT_EQ(res1, MakeUInt128(0U, 0U));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeUInt128(0x7777777777777777ULL, 0x7777777777777777ULL);
  auto [res2, fpsr2] = AsmUqshl(arg3, arg2);
  ASSERT_EQ(res2, MakeUInt128(0x0000000000003bbbULL, 0xeeeeffffffffffffULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingExtractNarrowInt64x2ToInt32x2) {
  constexpr auto AsmSqxtn = ASM_INSN_WRAP_FUNC_WQ_RES_W_ARG("sqxtn %0.2s, %2.2d");

  __uint128_t arg1 = MakeUInt128(0x0123456789abcdefULL, 0xfedcba9876543210ULL);
  auto [res1, fpsr1] = AsmSqxtn(arg1);
  ASSERT_EQ(res1, MakeUInt128(0x800000007fffffffULL, 0x0000000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr1));

  __uint128_t arg2 = MakeUInt128(0x0000000001234567ULL, 0x000000007ecdba98LL);
  auto [res2, fpsr2] = AsmSqxtn(arg2);
  ASSERT_EQ(res2, MakeUInt128(0x7ecdba9801234567ULL, 0x0000000000000000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingExtractNarrowInt64x1ToInt32x1) {
  constexpr auto AsmSqxtn = ASM_INSN_WRAP_FUNC_WQ_RES_W_ARG("sqxtn %s0, %d2");

  __uint128_t arg1 = MakeUInt128(0x1234567812345678ULL, 0x0ULL);
  auto [res1, fpsr1] = AsmSqxtn(arg1);
  ASSERT_EQ(res1, MakeUInt128(0x000000007fffffffULL, 0x0000000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr1));

  __uint128_t arg2 = MakeUInt128(0x0000000012345678ULL, 0x0ULL);
  auto [res2, fpsr2] = AsmSqxtn(arg2);
  ASSERT_EQ(res2, MakeUInt128(0x0000000012345678ULL, 0x0000000000000000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, UnsignedSaturatingExtractNarrowInt64x2ToInt32x2) {
  constexpr auto AsmUqstn = ASM_INSN_WRAP_FUNC_WQ_RES_W_ARG("uqxtn %0.2s, %2.2d");

  __uint128_t arg1 = MakeUInt128(0x0123456789abcdefULL, 0xfedcba9876543210ULL);
  auto [res1, fpsr1] = AsmUqstn(arg1);
  ASSERT_EQ(res1, MakeUInt128(0xffffffffffffffffULL, 0x0000000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr1));

  __uint128_t arg2 = MakeUInt128(0x0000000001234567ULL, 0x00000000fecdba98LL);
  auto [res2, fpsr2] = AsmUqstn(arg2);
  ASSERT_EQ(res2, MakeUInt128(0xfecdba9801234567ULL, 0x0000000000000000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, UnsignedSaturatingExtractNarrowInt64x1ToInt32x1) {
  constexpr auto AsmUqxtn = ASM_INSN_WRAP_FUNC_WQ_RES_W_ARG("uqxtn %s0, %d2");

  __uint128_t arg1 = MakeUInt128(0x1234567812345678ULL, 0x0ULL);
  auto [res1, fpsr1] = AsmUqxtn(arg1);
  ASSERT_EQ(res1, MakeUInt128(0x00000000ffffffffULL, 0x0000000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr1));

  __uint128_t arg2 = MakeUInt128(0x0000000087654321ULL, 0x0ULL);
  auto [res2, fpsr2] = AsmUqxtn(arg2);
  ASSERT_EQ(res2, MakeUInt128(0x0000000087654321ULL, 0x0000000000000000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingExtractNarrow2Int64x2ToInt32x2) {
  constexpr auto AsmSqxtn2 = ASM_INSN_WRAP_FUNC_WQ_RES_W0_ARG("sqxtn2 %0.4s, %2.2d");

  __uint128_t arg1 = MakeUInt128(0x0123456789abcdefULL, 0xfedcba9876543210ULL);
  __uint128_t arg2 = MakeUInt128(0x6121865619673378ULL, 0x6236256125216320ULL);
  auto [res1, fpsr1] = AsmSqxtn2(arg1, arg2);
  ASSERT_EQ(res1, MakeUInt128(0x6121865619673378ULL, 0x800000007fffffffULL));
  ASSERT_TRUE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeUInt128(0x0000000001234567ULL, 0x000000007ecdba98LL);
  __uint128_t arg4 = MakeUInt128(0x6121865619673378ULL, 0x6236256125216320ULL);
  auto [res2, fpsr2] = AsmSqxtn2(arg3, arg4);
  ASSERT_EQ(res2, MakeUInt128(0x6121865619673378ULL, 0x7ecdba9801234567ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, UnsignedSaturatingExtractNarrow2Int64x2ToInt32x4) {
  constexpr auto AsmUqxtn2 = ASM_INSN_WRAP_FUNC_WQ_RES_W0_ARG("uqxtn2 %0.4s, %2.2d");

  __uint128_t arg1 = MakeUInt128(0x0123456789abcdefULL, 0xfedcba9876543210ULL);
  __uint128_t arg2 = MakeUInt128(0x6121865619673378ULL, 0x6236256125216320ULL);
  auto [res1, fpsr1] = AsmUqxtn2(arg1, arg2);
  ASSERT_EQ(res1, MakeUInt128(0x6121865619673378ULL, 0xffffffffffffffffULL));
  ASSERT_TRUE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeUInt128(0x0000000001234567ULL, 0x00000000fecdba98LL);
  __uint128_t arg4 = MakeUInt128(0x6121865619673378ULL, 0x6236256125216320ULL);
  auto [res2, fpsr2] = AsmUqxtn2(arg3, arg4);
  ASSERT_EQ(res2, MakeUInt128(0x6121865619673378ULL, 0xfecdba9801234567ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingExtractUnsignedNarrowInt64x2ToInt32x2) {
  constexpr auto AsmSqxtun = ASM_INSN_WRAP_FUNC_WQ_RES_W_ARG("sqxtun %0.2s, %2.2d");

  __uint128_t arg1 = MakeUInt128(0x0000000044332211ULL, 0x00000001aabbccddULL);
  auto [res1, fpsr1] = AsmSqxtun(arg1);
  ASSERT_EQ(res1, MakeUInt128(0xffffffff44332211ULL, 0x0000000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr1));

  __uint128_t arg2 = MakeUInt128(0x0000000001234567ULL, 0x00000000fecdba98LL);
  auto [res2, fpsr2] = AsmSqxtun(arg2);
  ASSERT_EQ(res2, MakeUInt128(0xfecdba9801234567ULL, 0x0000000000000000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingExtractUnsignedNarrowInt64x1ToInt32x1) {
  constexpr auto AsmSqxtun = ASM_INSN_WRAP_FUNC_WQ_RES_W_ARG("sqxtun %s0, %d2");

  __uint128_t arg1 = MakeUInt128(0x00000001ff332211ULL, 0x0ULL);
  auto [res1, fpsr1] = AsmSqxtun(arg1);
  ASSERT_EQ(res1, MakeUInt128(0x00000000ffffffffULL, 0x0000000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr1));

  __uint128_t arg2 = MakeUInt128(0x00000000ff332211ULL, 0x0ULL);
  auto [res2, fpsr2] = AsmSqxtun(arg2);
  ASSERT_EQ(res2, MakeUInt128(0x00000000ff332211ULL, 0x0000000000000000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingExtractUnsignedNarrow2Int64x2ToInt32x4) {
  constexpr auto AsmSqxtun2 = ASM_INSN_WRAP_FUNC_WQ_RES_W0_ARG("sqxtun2 %0.4s, %2.2d");

  __uint128_t arg1 = MakeUInt128(0x0000000089abcdefULL, 0xfedcba9876543210ULL);
  __uint128_t arg2 = MakeUInt128(0x0123456789abcdefULL, 0xfedcba9876543210ULL);
  auto [res1, fpsr1] = AsmSqxtun2(arg1, arg2);
  ASSERT_EQ(res1, MakeUInt128(0x0123456789abcdefULL, 0x0000000089abcdefULL));
  ASSERT_TRUE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeUInt128(0x0000000001234567ULL, 0x00000000fecdba98LL);
  __uint128_t arg4 = MakeUInt128(0x0123456789abcdefULL, 0xfedcba9876543210ULL);
  auto [res2, fpsr2] = AsmSqxtun2(arg3, arg4);
  ASSERT_EQ(res2, MakeUInt128(0x0123456789abcdefULL, 0xfecdba9801234567ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingAccumulateOfUnsignedValueInt32x1) {
  constexpr auto AsmSuqadd = ASM_INSN_WRAP_FUNC_WQ_RES_W0_ARG("suqadd %s0, %s2");

  __uint128_t arg1 = MakeUInt128(0x9392023115638719ULL, 0x5080502467972579ULL);
  __uint128_t arg2 = MakeUInt128(0x2497605762625913ULL, 0x3285597263712112ULL);
  auto [res1, fpsr1] = AsmSuqadd(arg1, arg2);
  ASSERT_EQ(res1, MakeUInt128(0x0000000077c5e02cULL, 0x0000000000000000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeUInt128(0x9099791776687477ULL, 0x4481882870632315ULL);
  __uint128_t arg4 = MakeUInt128(0x5158650328981642ULL, 0x2828823274686610ULL);
  auto [res2, fpsr2] = AsmSuqadd(arg3, arg4);
  ASSERT_EQ(res2, MakeUInt128(0x000000007fffffffULL, 0x0000000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingAccumulateOfUnsignedValueInt32x4) {
  constexpr auto AsmSuqadd = ASM_INSN_WRAP_FUNC_WQ_RES_W0_ARG("suqadd %0.4s, %2.4s");

  __uint128_t arg1 = MakeUInt128(0x2590181000350989ULL, 0x2864120419516355ULL);
  __uint128_t arg2 = MakeUInt128(0x1108763204267612ULL, 0x9798265294258829ULL);
  auto [res1, fpsr1] = AsmSuqadd(arg1, arg2);
  ASSERT_EQ(res1, MakeUInt128(0x36988e42045b7f9bULL, 0xbffc3856ad76eb7eULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeUInt128(0x9082888934938376ULL, 0x4393992569006040ULL);
  __uint128_t arg4 = MakeUInt128(0x6731142209331219ULL, 0x5936202982972351ULL);
  auto [res2, fpsr2] = AsmSuqadd(arg3, arg4);
  ASSERT_EQ(res2, MakeUInt128(0x7fffffff3dc6958fULL, 0x7fffffffeb978391ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, UnsignedSaturatingAccumulateOfSignedValueInt32x1) {
  constexpr auto AsmUsqadd = ASM_INSN_WRAP_FUNC_WQ_RES_W0_ARG("usqadd %s0, %s2");

  __uint128_t arg1 = MakeUInt128(0x9052523242348615ULL, 0x3152097693846104ULL);
  __uint128_t arg2 = MakeUInt128(0x2582849714963475ULL, 0x3418375620030149ULL);
  auto [res1, fpsr1] = AsmUsqadd(arg1, arg2);
  ASSERT_EQ(res1, MakeUInt128(0x0000000056caba8aULL, 0x0000000000000000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeUInt128(0x9887125387801719ULL, 0x6071816407812484ULL);
  __uint128_t arg4 = MakeUInt128(0x7847257912407824ULL, 0x5443616823452395ULL);
  auto [res2, fpsr2] = AsmUsqadd(arg3, arg4);
  ASSERT_EQ(res2, MakeUInt128(0x0000000000000000ULL, 0x0000000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));

  __uint128_t arg5 = MakeUInt128(0x9708583970761645ULL, 0x8229630324424328ULL);
  __uint128_t arg6 = MakeUInt128(0x2377374595170285ULL, 0x6069806788952176ULL);
  auto [res3, fpsr3] = AsmUsqadd(arg5, arg6);
  ASSERT_EQ(res3, MakeUInt128(0x00000000ffffffffULL, 0x0000000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr3));
}

TEST(Arm64InsnTest, UnsignedSaturatingAccumulateOfSignedValueInt32x4) {
  constexpr auto AsmUsqadd = ASM_INSN_WRAP_FUNC_WQ_RES_W0_ARG("usqadd %0.4s, %2.4s");

  __uint128_t arg1 = MakeUInt128(0x4129137074982305ULL, 0x7592909166293919ULL);
  __uint128_t arg2 = MakeUInt128(0x5014721157586067ULL, 0x2700925477180257ULL);
  auto [res1, fpsr1] = AsmUsqadd(arg1, arg2);
  ASSERT_EQ(res1, MakeUInt128(0x913d8581cbf0836cULL, 0x9c9322e5dd413b70ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeUInt128(0x7816422828823274ULL, 0x6866106592732197ULL);
  __uint128_t arg4 = MakeUInt128(0x9071623846421534ULL, 0x8985247621678905ULL);
  auto [res2, fpsr2] = AsmUsqadd(arg3, arg4);
  ASSERT_EQ(res2, MakeUInt128(0xffffffff6ec447a8ULL, 0xf1eb34db00000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingRoundingShiftLeftInt32x1) {
  constexpr auto AsmSqrshl = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("sqrshl %s0, %s2, %s3");

  __uint128_t res;
  uint32_t fpsr;

  __uint128_t arg = MakeUInt128(0x9736705435580445ULL, 0x8657202276378404ULL);
  std::tie(res, fpsr) = AsmSqrshl(arg, -33);
  ASSERT_EQ(res, MakeUInt128(0x0000000000000000ULL, 0x0000000000000000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr));

  std::tie(res, fpsr) = AsmSqrshl(arg, -32);
  ASSERT_EQ(res, MakeUInt128(0x0000000000000000ULL, 0x0000000000000000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr));

  std::tie(res, fpsr) = AsmSqrshl(arg, -31);
  ASSERT_EQ(res, MakeUInt128(0x0000000000000000ULL, 0x0000000000000000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr));

  std::tie(res, fpsr) = AsmSqrshl(arg, -1);
  ASSERT_EQ(res, MakeUInt128(0x000000001aac0223ULL, 0x0000000000000000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr));

  std::tie(res, fpsr) = AsmSqrshl(arg, 0);
  ASSERT_EQ(res, MakeUInt128(0x0000000035580445ULL, 0x0000000000000000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr));

  std::tie(res, fpsr) = AsmSqrshl(arg, 1);
  ASSERT_EQ(res, MakeUInt128(0x000000006ab0088aULL, 0x0000000000000000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr));

  std::tie(res, fpsr) = AsmSqrshl(arg, 31);
  ASSERT_EQ(res, MakeUInt128(0x000000007fffffffULL, 0x0000000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr));

  std::tie(res, fpsr) = AsmSqrshl(arg, 32);
  ASSERT_EQ(res, MakeUInt128(0x000000007fffffffULL, 0x0000000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr));

  std::tie(res, fpsr) = AsmSqrshl(arg, 33);
  ASSERT_EQ(res, MakeUInt128(0x000000007fffffffULL, 0x0000000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr));
}

TEST(Arm64InsnTest, SignedSaturatingRoundingShiftLeftInt16x8) {
  constexpr auto AsmSqrshl = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("sqrshl %0.8h, %2.8h, %3.8h");

  __uint128_t arg1 = MakeUInt128(0x0000000000000099ULL, 0x9999099999999999ULL);
  __uint128_t arg2 = MakeUInt128(0x00110010000f0001ULL, 0xfffffff1fff0ffefULL);
  auto [res1, fpsr1] = AsmSqrshl(arg1, arg2);
  ASSERT_EQ(res1, MakeUInt128(0x0000000000000132ULL, 0xcccd000000000000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeUInt128(0x0099009900990099ULL, 0x0099009900990099ULL);
  auto [res2, fpsr2] = AsmSqrshl(arg3, arg2);
  ASSERT_EQ(res2, MakeUInt128(0x7fff7fff7fff0132ULL, 0x004d000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, UnsignedSaturatingRoundingShiftLeftInt32x1) {
  constexpr auto AsmUqrshl = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("uqrshl %s0, %s2, %s3");

  __uint128_t res;
  uint32_t fpsr;

  __uint128_t arg = MakeUInt128(0x9984124848262367ULL, 0x3771467226061633ULL);
  std::tie(res, fpsr) = AsmUqrshl(arg, -33);
  ASSERT_EQ(res, MakeUInt128(0x0000000000000000ULL, 0x0000000000000000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr));

  std::tie(res, fpsr) = AsmUqrshl(arg, -32);
  ASSERT_EQ(res, MakeUInt128(0x0000000000000000ULL, 0x0000000000000000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr));

  std::tie(res, fpsr) = AsmUqrshl(arg, -31);
  ASSERT_EQ(res, MakeUInt128(0x0000000000000001ULL, 0x0000000000000000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr));

  std::tie(res, fpsr) = AsmUqrshl(arg, -1);
  ASSERT_EQ(res, MakeUInt128(0x00000000241311b4ULL, 0x0000000000000000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr));

  std::tie(res, fpsr) = AsmUqrshl(arg, 0);
  ASSERT_EQ(res, MakeUInt128(0x0000000048262367ULL, 0x0000000000000000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr));

  std::tie(res, fpsr) = AsmUqrshl(arg, 1);
  ASSERT_EQ(res, MakeUInt128(0x00000000904c46ceULL, 0x0000000000000000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr));

  std::tie(res, fpsr) = AsmUqrshl(arg, 31);
  ASSERT_EQ(res, MakeUInt128(0x00000000ffffffffULL, 0x0000000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr));

  std::tie(res, fpsr) = AsmUqrshl(arg, 32);
  ASSERT_EQ(res, MakeUInt128(0x00000000ffffffffULL, 0x0000000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr));

  std::tie(res, fpsr) = AsmUqrshl(arg, 33);
  ASSERT_EQ(res, MakeUInt128(0x00000000ffffffffULL, 0x0000000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr));
}

TEST(Arm64InsnTest, UnsignedSaturatingRoundingShiftLeftInt16x8) {
  constexpr auto AsmUqrshl = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("uqrshl %0.8h, %2.8h, %3.8h");

  __uint128_t arg1 = MakeUInt128(0x0000000000000099ULL, 0x9999099999999999ULL);
  __uint128_t arg2 = MakeUInt128(0x00110010000f0001ULL, 0xfffffff1fff0ffefULL);
  auto [res1, fpsr1] = AsmUqrshl(arg1, arg2);
  ASSERT_EQ(res1, MakeUInt128(0x0000000000000132ULL, 0x4ccd000000010000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeUInt128(0x0099009900990099ULL, 0x0099009900990099ULL);
  auto [res2, fpsr2] = AsmUqrshl(arg3, arg2);
  ASSERT_EQ(res2, MakeUInt128(0xffffffffffff0132ULL, 0x004d000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingShiftRightNarrowInt16x1) {
  constexpr auto AsmSqshrn = ASM_INSN_WRAP_FUNC_WQ_RES_W_ARG("sqshrn %b0, %h2, #4");

  __uint128_t arg1 = MakeUInt128(0x888786614762f943ULL, 0x4140104988899316ULL);
  auto [res1, fpsr1] = AsmSqshrn(arg1);
  ASSERT_EQ(res1, MakeUInt128(0x94U, 0U));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg2 = MakeUInt128(0x0051207678103588ULL, 0x6116602029611936ULL);
  auto [res2, fpsr2] = AsmSqshrn(arg2);
  ASSERT_EQ(res2, MakeUInt128(0x7fU, 0U));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingShiftRightNarrowInt16x8) {
  constexpr auto AsmSqshrn = ASM_INSN_WRAP_FUNC_WQ_RES_W_ARG("sqshrn %0.8b, %2.8h, #4");

  __uint128_t arg1 = MakeUInt128(0x0625051604340253ULL, 0x0299028602670568ULL);
  auto [res1, fpsr1] = AsmSqshrn(arg1);
  ASSERT_EQ(res1, MakeUInt128(0x2928265662514325ULL, 0U));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg2 = MakeUInt128(0x2405806005642114ULL, 0x9386436864224724ULL);
  auto [res2, fpsr2] = AsmSqshrn(arg2);
  ASSERT_EQ(res2, MakeUInt128(0x807f7f7f7f80567fULL, 0U));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingShiftRightNarrowInt16x8Upper) {
  constexpr auto AsmSqshrn2 = ASM_INSN_WRAP_FUNC_WQ_RES_W0_ARG("sqshrn2 %0.16b, %2.8h, #4");

  __uint128_t arg1 = MakeUInt128(0x0367034704100536ULL, 0x0175064803000078ULL);
  __uint128_t arg2 = MakeUInt128(0x3494819262681110ULL, 0x7399482506073949ULL);
  auto [res1, fpsr1] = AsmSqshrn2(arg1, arg2);
  ASSERT_EQ(res1, MakeUInt128(0x3494819262681110ULL, 0x1764300736344153ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeUInt128(0x4641074501673719ULL, 0x0483109676711344ULL);
  auto [res2, fpsr2] = AsmSqshrn2(arg3, arg2);
  ASSERT_EQ(res2, MakeUInt128(0x3494819262681110ULL, 0x487f7f7f7f74167fULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, UnsignedSaturatingShiftRightNarrowInt16x1) {
  constexpr auto AsmUqshrn = ASM_INSN_WRAP_FUNC_WQ_RES_W_ARG("uqshrn %b0, %h2, #4");

  __uint128_t arg1 = MakeUInt128(0x6797172898220360ULL, 0x7028806908776866ULL);
  auto [res1, fpsr1] = AsmUqshrn(arg1);
  ASSERT_EQ(res1, MakeUInt128(0x36U, 0U));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg2 = MakeUInt128(0x0593252746378405ULL, 0x3976918480820410ULL);
  auto [res2, fpsr2] = AsmUqshrn(arg2);
  ASSERT_EQ(res2, MakeUInt128(0xffU, 0U));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, UnsignedSaturatingShiftRightNarrowInt16x8) {
  constexpr auto AsmUqshrn = ASM_INSN_WRAP_FUNC_WQ_RES_W_ARG("uqshrn %0.8b, %2.8h, #4");

  __uint128_t arg1 = MakeUInt128(0x0867067907600099ULL, 0x0693007509490515ULL);
  auto [res1, fpsr1] = AsmUqshrn(arg1);
  ASSERT_EQ(res1, MakeUInt128(0x6907945186677609ULL, 0U));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg2 = MakeUInt128(0x2736049811890413ULL, 0x0433116627747123ULL);
  auto [res2, fpsr2] = AsmUqshrn(arg2);
  ASSERT_EQ(res2, MakeUInt128(0x43ffffffff49ff41ULL, 0U));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, UnignedSaturatingShiftRightNarrowInt16x8Upper) {
  constexpr auto AsmUqshrn2 = ASM_INSN_WRAP_FUNC_WQ_RES_W0_ARG("uqshrn2 %0.16b, %2.8h, #4");

  __uint128_t arg1 = MakeUInt128(0x0441018407410768ULL, 0x0981066307240048ULL);
  __uint128_t arg2 = MakeUInt128(0x2393582740194493ULL, 0x5665161088463125ULL);
  auto [res1, fpsr1] = AsmUqshrn2(arg1, arg2);
  ASSERT_EQ(res1, MakeUInt128(0x2393582740194493ULL, 0x9866720444187476ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeUInt128(0x0785297709734684ULL, 0x3030614624180358ULL);
  auto [res2, fpsr2] = AsmUqshrn2(arg3, arg2);
  ASSERT_EQ(res2, MakeUInt128(0x2393582740194493ULL, 0xffffff3578ff97ffULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingRoundingShiftRightNarrowInt16x1) {
  constexpr auto AsmSqrshrn = ASM_INSN_WRAP_FUNC_WQ_RES_W_ARG("sqrshrn %b0, %h2, #4");

  __uint128_t arg1 = MakeUInt128(0x9610330799410534ULL, 0x7784574699992128ULL);
  auto [res1, fpsr1] = AsmSqrshrn(arg1);
  ASSERT_EQ(res1, MakeUInt128(0x0000000000000053ULL, 0x0000000000000000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg2 = MakeUInt128(0x5999993996122816ULL, 0x1521931488876938ULL);
  auto [res2, fpsr2] = AsmSqrshrn(arg2);
  ASSERT_EQ(res2, MakeUInt128(0x000000000000007fULL, 0x0000000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));

  __uint128_t arg3 = MakeUInt128(0x8022281083009986ULL, 0x0165494165426169ULL);
  auto [res3, fpsr3] = AsmSqrshrn(arg3);
  ASSERT_EQ(res3, MakeUInt128(0x0000000000000080ULL, 0x0000000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr3));
}

TEST(Arm64InsnTest, SignedSaturatingRoundingShiftRightNarrowInt16x8) {
  constexpr auto AsmSqrshrn = ASM_INSN_WRAP_FUNC_WQ_RES_W_ARG("sqrshrn %0.8b, %2.8h, #4");

  __uint128_t arg1 = MakeUInt128(0x0666070401700260ULL, 0x0520059204930759ULL);
  auto [res1, fpsr1] = AsmSqrshrn(arg1);
  ASSERT_EQ(res1, MakeUInt128(0x5259497666701726ULL, 0x0000000000000000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg2 = MakeUInt128(0x4143408146852981ULL, 0x5053947178900451ULL);
  auto [res2, fpsr2] = AsmSqrshrn(arg2);
  ASSERT_EQ(res2, MakeUInt128(0x7f807f457f7f7f7fULL, 0x0000000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingRoundingShiftRightNarrowInt16x8Upper) {
  constexpr auto AsmSqrshrn2 = ASM_INSN_WRAP_FUNC_WQ_RES_W0_ARG("sqrshrn2 %0.16b, %2.8h, #4");

  __uint128_t arg1 = MakeUInt128(0x0784017103960497ULL, 0x0707072501740336ULL);
  __uint128_t arg2 = MakeUInt128(0x5662725928440620ULL, 0x4302141137199227ULL);
  auto [res1, fpsr1] = AsmSqrshrn2(arg1, arg2);
  ASSERT_EQ(res1, MakeUInt128(0x5662725928440620ULL, 0x7072173378173949ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeUInt128(0x2066886512756882ULL, 0x6614973078865701ULL);
  __uint128_t arg4 = MakeUInt128(0x5685016918647488ULL, 0x5416791545965072ULL);
  auto [res2, fpsr2] = AsmSqrshrn2(arg3, arg4);
  ASSERT_EQ(res2, MakeUInt128(0x5685016918647488ULL, 0x7f807f7f7f807f7fULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, UnsignedSaturatingRoundingShiftRightNarrowInt16x1) {
  constexpr auto AsmUqrshrn = ASM_INSN_WRAP_FUNC_WQ_RES_W_ARG("uqrshrn %b0, %h2, #4");

  __uint128_t arg1 = MakeUInt128(0x9614236585950920ULL, 0x9083073323356034ULL);
  auto [res1, fpsr1] = AsmUqrshrn(arg1);
  ASSERT_EQ(res1, MakeUInt128(0x0000000000000092ULL, 0x0000000000000000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg2 = MakeUInt128(0x8465318730299026ULL, 0x6596450137183754ULL);
  auto [res2, fpsr2] = AsmUqrshrn(arg2);
  ASSERT_EQ(res2, MakeUInt128(0x00000000000000ffULL, 0x0000000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, UnsignedSaturatingRoundingShiftRightNarrowInt16x8) {
  constexpr auto AsmUqrshrn = ASM_INSN_WRAP_FUNC_WQ_RES_W_ARG("uqrshrn %0.8b, %2.8h, #4");

  __uint128_t arg1 = MakeUInt128(0x0301067603860240ULL, 0x0011030402470073ULL);
  auto [res1, fpsr1] = AsmUqrshrn(arg1);
  ASSERT_EQ(res1, MakeUInt128(0x0130240730673824ULL, 0x0000000000000000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg2 = MakeUInt128(0x5085082872462713ULL, 0x4946368501815469ULL);
  auto [res2, fpsr2] = AsmUqrshrn(arg2);
  ASSERT_EQ(res2, MakeUInt128(0xffff18ffff83ffffULL, 0x0000000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, UnsignedSaturatingRoundingShiftRightNarrowInt16x8Upper) {
  constexpr auto AsmUqrshrn = ASM_INSN_WRAP_FUNC_WQ_RES_W0_ARG("uqrshrn2 %0.16b, %2.8h, #4");

  __uint128_t arg1 = MakeUInt128(0x0388099005730661ULL, 0x0237022304780112ULL);
  __uint128_t arg2 = MakeUInt128(0x0392269110277722ULL, 0x6102544149221576ULL);
  auto [res1, fpsr1] = AsmUqrshrn(arg1, arg2);
  ASSERT_EQ(res1, MakeUInt128(0x0392269110277722ULL, 0x2322481139995766ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeUInt128(0x9254069617600504ULL, 0x7974928060721268ULL);
  __uint128_t arg4 = MakeUInt128(0x8414695726397884ULL, 0x2560084531214065ULL);
  auto [res2, fpsr2] = AsmUqrshrn(arg3, arg4);
  ASSERT_EQ(res2, MakeUInt128(0x8414695726397884ULL, 0xffffffffff69ff50ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingShiftRightUnsignedNarrowInt16x1) {
  constexpr auto AsmSqshrun = ASM_INSN_WRAP_FUNC_WQ_RES_W_ARG("sqshrun %b0, %h2, #4");

  __uint128_t arg1 = MakeUInt128(0x9143611439920063ULL, 0x8005083214098760ULL);
  auto [res1, fpsr1] = AsmSqshrun(arg1);
  ASSERT_EQ(res1, MakeUInt128(0x06U, 0U));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg2 = MakeUInt128(0x3815174571259975ULL, 0x4953580239983146ULL);
  auto [res2, fpsr2] = AsmSqshrun(arg2);
  ASSERT_EQ(res2, MakeUInt128(0x00U, 0U));
  ASSERT_TRUE(IsQcBitSet(fpsr2));

  __uint128_t arg3 = MakeUInt128(0x4599309324851025ULL, 0x1682944672606661ULL);
  auto [res3, fpsr3] = AsmSqshrun(arg3);
  ASSERT_EQ(res3, MakeUInt128(0xffU, 0U));
  ASSERT_TRUE(IsQcBitSet(fpsr3));
}

TEST(Arm64InsnTest, SignedSaturatingShiftRightUnsignedNarrowInt16x8) {
  constexpr auto AsmSqshrun = ASM_INSN_WRAP_FUNC_WQ_RES_W_ARG("sqshrun %0.8b, %2.8h, #4");

  __uint128_t arg1 = MakeUInt128(0x0911066408340874ULL, 0x0800074107250670ULL);
  auto [res1, fpsr1] = AsmSqshrun(arg1);
  ASSERT_EQ(res1, MakeUInt128(0x8074726791668387ULL, 0U));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg2 = MakeUInt128(0x4792258319129415ULL, 0x7390809143831384ULL);
  auto [res2, fpsr2] = AsmSqshrun(arg2);
  ASSERT_EQ(res2, MakeUInt128(0xff00ffffffffff00ULL, 0U));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingShiftRightUnsignedNarrowInt16x8Upper) {
  constexpr auto AsmSqshrun2 = ASM_INSN_WRAP_FUNC_WQ_RES_W0_ARG("sqshrun2 %0.16b, %2.8h, #4");

  __uint128_t arg1 = MakeUInt128(0x0625082101740415ULL, 0x0233074903960353ULL);
  __uint128_t arg2 = MakeUInt128(0x0136178653673760ULL, 0x6421667781377399ULL);
  auto [res1, fpsr1] = AsmSqshrun2(arg1, arg2);
  ASSERT_EQ(res1, MakeUInt128(0x0136178653673760ULL, 0x2374393562821741ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeUInt128(0x4295810545651083ULL, 0x1046297282937584ULL);
  __uint128_t arg4 = MakeUInt128(0x1611625325625165ULL, 0x7249807849209989ULL);
  auto [res2, fpsr2] = AsmSqshrun2(arg3, arg4);
  ASSERT_EQ(res2, MakeUInt128(0x1611625325625165ULL, 0xffff00ffff00ffffULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingRoundingShiftRightUnsignedNarrowInt16x1) {
  constexpr auto AsmSqrshrun = ASM_INSN_WRAP_FUNC_WQ_RES_W_ARG("sqrshrun %b0, %h2, #4");

  __uint128_t arg1 = MakeUInt128(0x5760186946490886ULL, 0x8154528562134698ULL);
  auto [res1, fpsr1] = AsmSqrshrun(arg1);
  ASSERT_EQ(res1, MakeUInt128(0x88ULL, 0U));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg2 = MakeUInt128(0x8355444560249556ULL, 0x6684366029221951ULL);
  auto [res2, fpsr2] = AsmSqrshrun(arg2);
  ASSERT_EQ(res2, MakeUInt128(0x00ULL, 0U));
  ASSERT_TRUE(IsQcBitSet(fpsr2));

  __uint128_t arg3 = MakeUInt128(0x2483091060537720ULL, 0x1980218310103270ULL);
  auto [res3, fpsr3] = AsmSqrshrun(arg3);
  ASSERT_EQ(res3, MakeUInt128(0xffULL, 0U));
  ASSERT_TRUE(IsQcBitSet(fpsr3));
}

TEST(Arm64InsnTest, SignedSaturatingRoundingShiftRightUnsignedNarrowInt16x8) {
  constexpr auto AsmSqrshrun = ASM_INSN_WRAP_FUNC_WQ_RES_W_ARG("sqrshrun %0.8b, %2.8h, #4");

  __uint128_t arg1 = MakeUInt128(0x0150069001490702ULL, 0x0673033808340550ULL);
  auto [res1, fpsr1] = AsmSqrshrun(arg1);
  ASSERT_EQ(res1, MakeUInt128(0x6734835515691570ULL, 0U));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg2 = MakeUInt128(0x8363660178487710ULL, 0x6080980426924713ULL);
  auto [res2, fpsr2] = AsmSqrshrun(arg2);
  ASSERT_EQ(res2, MakeUInt128(0xff00ffff00ffffffULL, 0U));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingRoundingShiftRightUnsignedNarrowInt16x8Upper) {
  constexpr auto AsmSqrshrun2 = ASM_INSN_WRAP_FUNC_WQ_RES_W0_ARG("sqrshrun2 %0.16b, %2.8h, #4");

  __uint128_t arg1 = MakeUInt128(0x0733049502080757ULL, 0x0651018705990498ULL);
  __uint128_t arg2 = MakeUInt128(0x5693795623875551ULL, 0x6175754380917805ULL);
  auto [res1, fpsr1] = AsmSqrshrun2(arg1, arg2);
  ASSERT_EQ(res1, MakeUInt128(0x5693795623875551ULL, 0x65185a4a73492175ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeUInt128(0x1444671298615527ULL, 0x5982014514102756ULL);
  __uint128_t arg4 = MakeUInt128(0x0068929750246304ULL, 0x0173514891945763ULL);
  auto [res2, fpsr2] = AsmSqrshrun2(arg3, arg4);
  ASSERT_EQ(res2, MakeUInt128(0x0068929750246304ULL, 0xff14ffffffff00ffULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingShiftLeftUnsignedImmInt32x1) {
  constexpr auto AsmSqshlu = ASM_INSN_WRAP_FUNC_WQ_RES_W_ARG("sqshlu %s0, %s2, #4");

  __uint128_t arg1 = MakeUInt128(0x9704033001862556ULL, 0x1473321177711744ULL);
  auto [res1, fpsr1] = AsmSqshlu(arg1);
  ASSERT_EQ(res1, MakeUInt128(0x18625560ULL, 0U));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg2 = MakeUInt128(0x3095760196946490ULL, 0x8868154528562134ULL);
  auto [res2, fpsr2] = AsmSqshlu(arg2);
  ASSERT_EQ(res2, MakeUInt128(0x00000000ULL, 0U));
  ASSERT_TRUE(IsQcBitSet(fpsr2));

  __uint128_t arg3 = MakeUInt128(0x1335028160884035ULL, 0x1781452541964320ULL);
  auto [res3, fpsr3] = AsmSqshlu(arg3);
  ASSERT_EQ(res3, MakeUInt128(0xffffffffULL, 0U));
  ASSERT_TRUE(IsQcBitSet(fpsr3));
}

TEST(Arm64InsnTest, SignedSaturatingShiftLeftUnsignedImmInt32x4) {
  constexpr auto AsmSqshlu = ASM_INSN_WRAP_FUNC_WQ_RES_W_ARG("sqshlu %0.4s, %2.4s, #4");

  __uint128_t arg1 = MakeUInt128(0x0865174507877133ULL, 0x0813875205980941ULL);
  auto [res1, fpsr1] = AsmSqshlu(arg1);
  ASSERT_EQ(res1, MakeUInt128(0x8651745078771330ULL, 0x8138752059809410ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg2 = MakeUInt128(0x2174227300352296ULL, 0x0080891797050682ULL);
  auto [res2, fpsr2] = AsmSqshlu(arg2);
  ASSERT_EQ(res2, MakeUInt128(0xffffffff03522960ULL, 0x0808917000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingDoublingMultiplyLong32x2) {
  constexpr auto AsmSqdmull = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("sqdmull %0.2d, %2.2s, %3.2s");

  __uint128_t arg1 = MakeUInt128(0x0000000200000004ULL, 0xfeed000300000010ULL);
  __uint128_t arg2 = MakeUInt128(0x0000000300000002ULL, 0xfeed00040000002ULL);
  auto [res1, fpsr1] = AsmSqdmull(arg1, arg2);
  ASSERT_EQ(res1, MakeUInt128(0x0000000000000010ULL, 0x000000000000000cULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeUInt128(0x8000000000000004ULL, 0xfeed000300000010ULL);
  __uint128_t arg4 = MakeUInt128(0x8000000000000002ULL, 0xfeed00040000002ULL);
  auto [res2, fpsr2] = AsmSqdmull(arg3, arg4);
  ASSERT_EQ(res2, MakeUInt128(0x0000000000000010ULL, 0x7fffffffffffffffULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingDoublingMultiplyLong16x4) {
  constexpr auto AsmSqdmull = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("sqdmull %0.4s, %2.4h, %3.4h");

  __uint128_t arg1 = MakeUInt128(0x0004000200f00004ULL, 0xfeedfeedfeedfeedULL);
  __uint128_t arg2 = MakeUInt128(0x0008000300800002ULL, 0xabcd0123ffff4567ULL);
  auto [res1, fpsr1] = AsmSqdmull(arg1, arg2);
  ASSERT_EQ(res1, MakeUInt128(0x0000f00000000010ULL, 0x000000400000000cULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeUInt128(0x8000000200f00004ULL, 0xfeedfeedfeedfeedULL);
  __uint128_t arg4 = MakeUInt128(0x8000000300800002ULL, 0xabcd0123ffff4567ULL);
  auto [res2, fpsr2] = AsmSqdmull(arg3, arg4);
  ASSERT_EQ(res2, MakeUInt128(0x0000f00000000010ULL, 0x7fffffff0000000cULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingDoublingMultiplyLongUpper32x2) {
  constexpr auto AsmSqdmull = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("sqdmull2 %0.2d, %2.4s, %3.4s");

  __uint128_t arg1 = MakeUInt128(0x0000000200000004ULL, 0xfeed000300000010ULL);
  __uint128_t arg2 = MakeUInt128(0x0000000300000002ULL, 0xfeed00040000002ULL);
  auto [res1, fpsr1] = AsmSqdmull(arg1, arg2);
  ASSERT_EQ(res1, MakeUInt128(0x0000000800000040ULL, 0xffddc4ed7f98e000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeUInt128(0x8000000000000004ULL, 0x8000000000000010ULL);
  __uint128_t arg4 = MakeUInt128(0x8000000000000002ULL, 0x8000000000000002ULL);
  auto [res2, fpsr2] = AsmSqdmull(arg3, arg4);
  ASSERT_EQ(res2, MakeUInt128(0x0000000000000040ULL, 0x7fffffffffffffffULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingDoublingMultiplyLongUpper16x4) {
  constexpr auto AsmSqdmull = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("sqdmull2 %0.4s, %2.8h, %3.8h");

  __uint128_t arg1 = MakeUInt128(0x0004000200f00004ULL, 0xfeedfeedfeedfeedULL);
  __uint128_t arg2 = MakeUInt128(0x0008000300800002ULL, 0xabcd0123ffff4567ULL);
  auto [res1, fpsr1] = AsmSqdmull(arg1, arg2);
  ASSERT_EQ(res1, MakeUInt128(0x00000226ff6ae4b6ULL, 0x00b4e592fffd8eceULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeUInt128(0x8000000000000004ULL, 0x8000000000000010ULL);
  __uint128_t arg4 = MakeUInt128(0x8000000000000002ULL, 0x8000000000000002ULL);
  auto [res2, fpsr2] = AsmSqdmull(arg3, arg4);
  ASSERT_EQ(res2, MakeUInt128(0x0000000000000040ULL, 0x7fffffff00000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingDoublingMultiplyLong64x2IndexedElem) {
  constexpr auto AsmSqdmull = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("sqdmull %0.2d, %2.2s, %3.s[1]");

  __uint128_t arg1 = MakeUInt128(0x0022002211223344ULL, 0x1122334400110011LL);
  __uint128_t arg2 = MakeUInt128(0x0000000200000000ULL, 0x000000000000000ULL);
  auto [res1, fpsr1] = AsmSqdmull(arg1, arg2);
  ASSERT_EQ(res1, MakeUInt128(0x000000004488cd10ULL, 0x0000000000880088ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeUInt128(0x0022002280000000ULL, 0x1122334400110011LL);
  __uint128_t arg4 = MakeUInt128(0x8000000000000000ULL, 0x000000000000000ULL);
  auto [res2, fpsr2] = AsmSqdmull(arg3, arg4);
  ASSERT_EQ(res2, MakeUInt128(0x7fffffffffffffffULL, 0xffddffde00000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingDoublingMultiplyLong32x4IndexedElem) {
  constexpr auto AsmSqdmull = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("sqdmull %0.4s, %2.4h, %3.h[4]");

  __uint128_t arg1 = MakeUInt128(0x0022002211223344ULL, 0x1122334400110011LL);
  __uint128_t arg2 = MakeUInt128(0x000f000f000f000fULL, 0x000f000f000f0002ULL);
  auto [res1, fpsr1] = AsmSqdmull(arg1, arg2);
  ASSERT_EQ(res1, MakeUInt128(0x000044880000cd10ULL, 0x0000008800000088ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeUInt128(0x0022002280000000ULL, 0x1122334400118000ULL);
  __uint128_t arg4 = MakeUInt128(0x1111111122222222ULL, 0x1122334411228000ULL);
  auto [res2, fpsr2] = AsmSqdmull(arg3, arg4);
  ASSERT_EQ(res2, MakeUInt128(0x7fffffff00000000ULL, 0xffde0000ffde0000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingDoublingMultiplyLongUpper64x2IndexedElem) {
  constexpr auto AsmSqdmull = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("sqdmull2 %0.2d, %2.4s, %3.s[3]");

  __uint128_t arg1 = MakeUInt128(0x0022002211223344ULL, 0x1122334400110011ULL);
  __uint128_t arg2 = MakeUInt128(0xffffffffffffffffULL, 0x00000002ffffffffULL);
  auto [res1, fpsr1] = AsmSqdmull(arg1, arg2);
  ASSERT_EQ(res1, MakeUInt128(0x0000000000440044ULL, 0x000000004488cd10ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeUInt128(0x80000000ffffffffULL, 0x1122334480000000ULL);
  __uint128_t arg4 = MakeUInt128(0x1122334411223344ULL, 0x80000000ffffffffULL);
  auto [res2, fpsr2] = AsmSqdmull(arg3, arg4);
  ASSERT_EQ(res2, MakeUInt128(0x7fffffffffffffffULL, 0xeeddccbc00000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingDoublingMultiplyLongUpper32x4IndexedElem) {
  constexpr auto AsmSqdmull = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("sqdmull2 %0.4s, %2.8h, %3.h[7]");

  __uint128_t arg1 = MakeUInt128(0x0022002211223344ULL, 0x1122334400110011ULL);
  __uint128_t arg2 = MakeUInt128(0xffffffffffffffffULL, 0x0002ffffffffffffULL);
  auto [res1, fpsr1] = AsmSqdmull(arg1, arg2);
  ASSERT_EQ(res1, MakeUInt128(0x0000004400000044ULL, 0x000044880000cd10ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeUInt128(0x80000000ffffffffULL, 0x112233448000ffffULL);
  __uint128_t arg4 = MakeUInt128(0x1122334411223344ULL, 0x8000ffffffffffffULL);
  auto [res2, fpsr2] = AsmSqdmull(arg3, arg4);
  ASSERT_EQ(res2, MakeUInt128(0x7fffffff00010000ULL, 0xeede0000ccbc0000ULL));
}

TEST(Arm64InsnTest, SignedSaturatingDoublingMultiplyLong64x1) {
  constexpr auto AsmSqdmull = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("sqdmull %d0, %s2, %s3");
  __uint128_t arg1 = MakeUInt128(0x0000000811112222ULL, 0x0000000700000006ULL);
  __uint128_t arg2 = MakeUInt128(0x0000000510000000ULL, 0x0000000300000002ULL);
  auto [res1, fpsr1] = AsmSqdmull(arg1, arg2);
  ASSERT_EQ(res1, MakeUInt128(0x0222244440000000ULL, 0x0000000000000000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeUInt128(0xaabbccdd80000000ULL, 0x1122334400110011ULL);
  __uint128_t arg4 = MakeUInt128(0xff11ff1180000000ULL, 0xffffffff11223344ULL);
  auto [res2, fpsr2] = AsmSqdmull(arg3, arg4);
  ASSERT_EQ(res2, MakeUInt128(0x7fffffffffffffffULL, 0x0000000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingDoublingMultiplyLong32x1) {
  constexpr auto AsmSqdmull = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("sqdmull %s0, %h2, %h3");
  __uint128_t arg1 = MakeUInt128(0x1111111811112222ULL, 0xf000000700080006ULL);
  __uint128_t arg2 = MakeUInt128(0x0000000510004444ULL, 0xf000000300080002ULL);
  auto [res1, fpsr1] = AsmSqdmull(arg1, arg2);
  ASSERT_EQ(res1, MakeUInt128(0x0000000012343210ULL, 0x0000000000000000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeUInt128(0xaabbccdd00008000ULL, 0x1122334400110011ULL);
  __uint128_t arg4 = MakeUInt128(0xff11ff1100008000ULL, 0xffffffff11223344ULL);
  auto [res2, fpsr2] = AsmSqdmull(arg3, arg4);
  ASSERT_EQ(res2, MakeUInt128(0x000000007fffffffULL, 0x0000000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingDoublingMultiplyLong32x1IndexedElem) {
  constexpr auto AsmSqdmull = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("sqdmull %s0, %h2, %3.h[7]");
  __uint128_t arg1 = MakeUInt128(0x0000000811112222ULL, 0x0000000700000006ULL);
  __uint128_t arg2 = MakeUInt128(0x0000000510000000ULL, 0x1111000300000002ULL);
  auto [res1, fpsr1] = AsmSqdmull(arg1, arg2);
  ASSERT_EQ(res1, MakeUInt128(0x00000000048d0c84ULL, 0x0000000000000000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeUInt128(0xaabbccddaabb8000ULL, 0x1122334400110011ULL);
  __uint128_t arg4 = MakeUInt128(0xff11ff11ff000ff0ULL, 0x8000aabb11223344ULL);
  auto [res2, fpsr2] = AsmSqdmull(arg3, arg4);
  ASSERT_EQ(res2, MakeUInt128(0x000000007fffffffULL, 0x0000000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingDoublingMultiplyLong64x1IndexedElem) {
  constexpr auto AsmSqdmull = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("sqdmull %d0, %s2, %3.s[3]");
  __uint128_t arg1 = MakeUInt128(0x0000000811112222ULL, 0x0000000700000006ULL);
  __uint128_t arg2 = MakeUInt128(0x0000000510000000ULL, 0x0000000300000002ULL);
  auto [res1, fpsr1] = AsmSqdmull(arg1, arg2);
  ASSERT_EQ(res1, MakeUInt128(0x000000006666ccccULL, 0x0000000000000000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeUInt128(0xaabbccdd80000000ULL, 0x1122334400110011ULL);
  __uint128_t arg4 = MakeUInt128(0xff11ff11ff000ff0ULL, 0x8000000011223344ULL);
  auto [res2, fpsr2] = AsmSqdmull(arg3, arg4);
  ASSERT_EQ(res2, MakeUInt128(0x7fffffffffffffffULL, 0x0000000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingDoublingMultiplyAddLong32x2) {
  constexpr auto AsmSqdmlal = ASM_INSN_WRAP_FUNC_WQ_RES_WW0_ARG("sqdmlal %0.2d, %2.2s, %3.2s");

  // No saturation.
  __uint128_t arg1 = MakeUInt128(0x1100110022002200ULL, 0x7654321076543210ULL);
  __uint128_t arg2 = MakeUInt128(0x0000000400000004ULL, 0x0123456701234567ULL);
  __uint128_t arg3 = MakeUInt128(0x0100010001000100ULL, 0x0400040004000400ULL);
  auto [res1, fpsr1] = AsmSqdmlal(arg1, arg2, arg3);
  ASSERT_EQ(res1, MakeUInt128(0x0100010111011100ULL, 0x040004008c008c00ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  // Saturates in the multiplication.
  __uint128_t arg4 = MakeUInt128(0x8000000000000004ULL, 0xfeed000300000010ULL);
  __uint128_t arg5 = MakeUInt128(0x8000000000000002ULL, 0xfeed000400000020ULL);
  __uint128_t arg6 = MakeUInt128(0x0000080000000900ULL, 0x00000a000000b000ULL);
  auto [res2, fpsr2] = AsmSqdmlal(arg4, arg5, arg6);
  ASSERT_EQ(res2, MakeUInt128(0x0000080000000910ULL, 0x7fffffffffffffffULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));

  // Saturates in the addition.
  __uint128_t arg7 = MakeUInt128(0x1100110022002200ULL, 0x7654321076543210ULL);
  __uint128_t arg8 = MakeUInt128(0x0000000400000004ULL, 0x0123456701234567ULL);
  __uint128_t arg9 = MakeUInt128(0x7fffffffffffffffULL, 0x00000a000000b000ULL);
  auto [res3, fpsr3] = AsmSqdmlal(arg7, arg8, arg9);
  ASSERT_EQ(res3, MakeUInt128(0x7fffffffffffffffULL, 0x00000a0088013800ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr3));
}

TEST(Arm64InsnTest, SignedSaturatingDoublingMultiplyAddLong16x4) {
  constexpr auto AsmSqdmlal = ASM_INSN_WRAP_FUNC_WQ_RES_WW0_ARG("sqdmlal %0.4s, %2.4h, %3.4h");

  // No saturation.
  __uint128_t arg1 = MakeUInt128(0x8000110022002200ULL, 0x7654321076543210ULL);
  __uint128_t arg2 = MakeUInt128(0x0010001100000004ULL, 0x0123456701234567ULL);
  __uint128_t arg3 = MakeUInt128(0x0100010001000100ULL, 0x0400040004000400ULL);
  auto [res1, fpsr1] = AsmSqdmlal(arg1, arg2, arg3);
  ASSERT_EQ(res1, MakeUInt128(0x0100010001011100ULL, 0x03f0040004024600ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  // Saturates in the multiplication.
  __uint128_t arg4 = MakeUInt128(0x8000111111111111ULL, 0x1234123412341234ULL);
  __uint128_t arg5 = MakeUInt128(0x8000111111111111ULL, 0x1234123412341234ULL);
  __uint128_t arg6 = MakeUInt128(0x0123456701234567ULL, 0x0123456701234567ULL);
  auto [res2, fpsr2] = AsmSqdmlal(arg4, arg5, arg6);
  ASSERT_EQ(res2, MakeUInt128(0x0369cba90369cba9ULL, 0x7fffffff0369cba9ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));

  // Saturates in the addition.
  __uint128_t arg7 = MakeUInt128(0x1100110022002200ULL, 0x7654321076543210ULL);
  __uint128_t arg8 = MakeUInt128(0x0000000400010004ULL, 0x0123456701234567ULL);
  __uint128_t arg9 = MakeUInt128(0x7fffffff12345678ULL, 0x00000a000000b000ULL);
  auto [res3, fpsr3] = AsmSqdmlal(arg7, arg8, arg9);
  ASSERT_EQ(res3, MakeUInt128(0x7fffffff12356678ULL, 0x00000a0000013800ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr3));
}

TEST(Arm64InsnTest, SignedSaturatingDoublingMultiplyAddLongUpper32x2) {
  constexpr auto AsmSqdmlal = ASM_INSN_WRAP_FUNC_WQ_RES_WW0_ARG("sqdmlal2 %0.2d, %2.4s, %3.4s");

  // No saturation.
  __uint128_t arg1 = MakeUInt128(0x1100110022002200ULL, 0x7654321076543210ULL);
  __uint128_t arg2 = MakeUInt128(0x0000000400000004ULL, 0x0123456701234567ULL);
  __uint128_t arg3 = MakeUInt128(0x0100010001000100ULL, 0x0400040004000400ULL);
  auto [res1, fpsr1] = AsmSqdmlal(arg1, arg2, arg3);
  ASSERT_EQ(res1, MakeUInt128(0x020d44926c1ce9e0ULL, 0x050d47926f1cece0ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  // Saturates in the multiplication.
  __uint128_t arg4 = MakeUInt128(0x1234567800000004ULL, 0x8000000001100010ULL);
  __uint128_t arg5 = MakeUInt128(0x1234567800000002ULL, 0x8000000001100020ULL);
  __uint128_t arg6 = MakeUInt128(0x0000080000000900ULL, 0x00000a000000b000ULL);
  auto [res2, fpsr2] = AsmSqdmlal(arg4, arg5, arg6);
  ASSERT_EQ(res2, MakeUInt128(0x00024a0066000d00ULL, 0x7fffffffffffffffULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));

  // Saturates in the addition.
  __uint128_t arg7 = MakeUInt128(0x1100110022002200ULL, 0x7654321076543210ULL);
  __uint128_t arg8 = MakeUInt128(0x0000000400000004ULL, 0x0123456701234567ULL);
  __uint128_t arg9 = MakeUInt128(0x1234567812345678ULL, 0x7fffffffffffffffULL);
  auto [res3, fpsr3] = AsmSqdmlal(arg7, arg8, arg9);
  ASSERT_EQ(res3, MakeUInt128(0x13419a0a7d513f58ULL, 0x7fffffffffffffffULL));
  ASSERT_TRUE(IsQcBitSet(fpsr3));
}

TEST(Arm64InsnTest, SignedSaturatingDoublingMultiplyAddLongUpper16x4) {
  constexpr auto AsmSqdmlal = ASM_INSN_WRAP_FUNC_WQ_RES_WW0_ARG("sqdmlal2 %0.4s, %2.8h, %3.8h");

  // No saturation.
  __uint128_t arg1 = MakeUInt128(0x8000110022002200ULL, 0x7654321076543210ULL);
  __uint128_t arg2 = MakeUInt128(0x0010001100000004ULL, 0x0123456701234567ULL);
  __uint128_t arg3 = MakeUInt128(0x0100010001000100ULL, 0x0400040004000400ULL);
  auto [res1, fpsr1] = AsmSqdmlal(arg1, arg2, arg3);
  ASSERT_EQ(res1, MakeUInt128(0x020d03f81c24e9e0ULL, 0x050d06f81f24ece0ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  // Saturates in the multiplication.
  __uint128_t arg4 = MakeUInt128(0x1111111111111111ULL, 0x8000123412341234ULL);
  __uint128_t arg5 = MakeUInt128(0x1111111111111111ULL, 0x8000123412341234ULL);
  __uint128_t arg6 = MakeUInt128(0x0123456701234567ULL, 0x0123456701234567ULL);
  auto [res2, fpsr2] = AsmSqdmlal(arg4, arg5, arg6);
  ASSERT_EQ(res2, MakeUInt128(0x03b9fa8703b9fa87ULL, 0x7fffffff03b9fa87ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));

  // Saturates in the addition.
  __uint128_t arg7 = MakeUInt128(0x1100110022002200ULL, 0x7654321076543210ULL);
  __uint128_t arg8 = MakeUInt128(0x0000000400010004ULL, 0x0123456701234567ULL);
  __uint128_t arg9 = MakeUInt128(0x1234567812345678ULL, 0x7fffffff0000b000ULL);
  auto [res3, fpsr3] = AsmSqdmlal(arg7, arg8, arg9);
  ASSERT_EQ(res3, MakeUInt128(0x134159702d593f58ULL, 0x7fffffff1b2598e0ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr3));
}

TEST(Arm64InsnTest, SignedSaturatingDoublingMultiplyAddLong64x1) {
  constexpr auto AsmSqdmlal = ASM_INSN_WRAP_FUNC_WQ_RES_WW0_ARG("sqdmlal %d0, %s2, %s3");

  // No saturation.
  __uint128_t arg1 = MakeUInt128(0x1100110011223344ULL, 0x7654321076543210ULL);
  __uint128_t arg2 = MakeUInt128(0x0000000020000000ULL, 0x0123456701234567ULL);
  __uint128_t arg3 = MakeUInt128(0x12345678000000FFULL, 0x0400040004000400ULL);
  auto [res1, fpsr1] = AsmSqdmlal(arg1, arg2, arg3);
  ASSERT_EQ(res1, MakeUInt128(0x167ce349000000ffULL, 0x0000000000000000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  // Saturates in the multiplication.
  __uint128_t arg4 = MakeUInt128(0x1122334480000000ULL, 0xfeed000300000010ULL);
  __uint128_t arg5 = MakeUInt128(0xaabbccdd80000000ULL, 0xfeed000400000020ULL);
  __uint128_t arg6 = MakeUInt128(0x1122334411111111ULL, 0x00000a000000b000ULL);
  auto [res2, fpsr2] = AsmSqdmlal(arg4, arg5, arg6);
  ASSERT_EQ(res2, MakeUInt128(0x7fffffffffffffffULL, 0x0000000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));

  // Saturates in the addition.
  __uint128_t arg7 = MakeUInt128(0x1122334400111111ULL, 0x7654321076543210ULL);
  __uint128_t arg8 = MakeUInt128(0xaabbccdd00222222ULL, 0x0123456701234567ULL);
  __uint128_t arg9 = MakeUInt128(0x7fffffffffffffffULL, 0x00000a000000b000ULL);
  auto [res3, fpsr3] = AsmSqdmlal(arg7, arg8, arg9);
  ASSERT_EQ(res3, MakeUInt128(0x7fffffffffffffffULL, 0x0000000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr3));
}

TEST(Arm64InsnTest, SignedSaturatingDoublingMultiplyAddLong32x1) {
  constexpr auto AsmSqdmlal = ASM_INSN_WRAP_FUNC_WQ_RES_WW0_ARG("sqdmlal %s0, %h2, %h3");

  // No saturation.
  __uint128_t arg1 = MakeUInt128(0x1100110022002200ULL, 0x7654321076543210ULL);
  __uint128_t arg2 = MakeUInt128(0x0000000000000004ULL, 0x0123456701234567ULL);
  __uint128_t arg3 = MakeUInt128(0x0100010001000100ULL, 0x0400040004000400ULL);
  auto [res1, fpsr1] = AsmSqdmlal(arg1, arg2, arg3);
  ASSERT_EQ(res1, MakeUInt128(0x0000000001011100ULL, 0x0000000000000000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  // Saturates in the multiplication.
  __uint128_t arg4 = MakeUInt128(0x1122334411228000ULL, 0xfeed000300000010ULL);
  __uint128_t arg5 = MakeUInt128(0xaabbccddaabb8000ULL, 0xfeed000400000020ULL);
  __uint128_t arg6 = MakeUInt128(0x1122334411111111ULL, 0x00000a000000b000ULL);
  auto [res2, fpsr2] = AsmSqdmlal(arg4, arg5, arg6);
  ASSERT_EQ(res2, MakeUInt128(0x000000007fffffffULL, 0x0000000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));

  // Saturates in the addition.
  __uint128_t arg7 = MakeUInt128(0x1122334411220123ULL, 0x7654321076543210ULL);
  __uint128_t arg8 = MakeUInt128(0xaabbccddaabb0044ULL, 0x0123456701234567ULL);
  __uint128_t arg9 = MakeUInt128(0xaabbccdd7fffffffULL, 0x00000a000000b000ULL);
  auto [res3, fpsr3] = AsmSqdmlal(arg7, arg8, arg9);
  ASSERT_EQ(res3, MakeUInt128(0x000000007fffffffULL, 0x0000000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr3));
}

TEST(Arm64InsnTest, SignedSaturatingDoublingMultiplyAddLong64x2IndexedElem) {
  constexpr auto AsmSqdmlal = ASM_INSN_WRAP_FUNC_WQ_RES_WW0_ARG("sqdmlal %0.2d, %2.2s, %3.s[1]");

  // No saturation.
  __uint128_t arg1 = MakeUInt128(0x1100110022002200ULL, 0x7654321076543210ULL);
  __uint128_t arg2 = MakeUInt128(0x0000000400000004ULL, 0x0123456701234567ULL);
  __uint128_t arg3 = MakeUInt128(0x0100010001000100ULL, 0x0400040004000400ULL);
  auto [res1, fpsr1] = AsmSqdmlal(arg1, arg2, arg3);
  ASSERT_EQ(res1, MakeUInt128(0x0100010111011100ULL, 0x040004008c008c00ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  // Saturates in the multiplication.
  __uint128_t arg4 = MakeUInt128(0x8000000000000004ULL, 0xfeed000300000010ULL);
  __uint128_t arg5 = MakeUInt128(0x8000000000000002ULL, 0xfeed000400000020ULL);
  __uint128_t arg6 = MakeUInt128(0x0000080000000900ULL, 0x00000a000000b000ULL);
  auto [res2, fpsr2] = AsmSqdmlal(arg4, arg5, arg6);
  ASSERT_EQ(res2, MakeUInt128(0x000007fc00000900ULL, 0x7fffffffffffffffULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));

  // Saturates in the addition.
  __uint128_t arg7 = MakeUInt128(0x1100110022002200ULL, 0x7654321076543210ULL);
  __uint128_t arg8 = MakeUInt128(0x0000000400000004ULL, 0x0123456701234567ULL);
  __uint128_t arg9 = MakeUInt128(0x7fffffffffffffffULL, 0x00000a000000b000ULL);
  auto [res3, fpsr3] = AsmSqdmlal(arg7, arg8, arg9);
  ASSERT_EQ(res3, MakeUInt128(0x7fffffffffffffffULL, 0x00000a0088013800ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr3));
}

TEST(Arm64InsnTest, SignedSaturatingDoublingMultiplyAddLong32x4IndexedElem) {
  constexpr auto AsmSqdmlal = ASM_INSN_WRAP_FUNC_WQ_RES_WW0_ARG("sqdmlal %0.4s, %2.4h, %3.h[7]");

  // No saturation.
  __uint128_t arg1 = MakeUInt128(0x0102030405060708ULL, 0x7654321076543210ULL);
  __uint128_t arg2 = MakeUInt128(0x1122334488776655ULL, 0x0123456701234567ULL);
  __uint128_t arg3 = MakeUInt128(0x0123456789abcdefULL, 0xfedcba9876543210ULL);
  auto [res1, fpsr1] = AsmSqdmlal(arg1, arg2, arg3);
  ASSERT_EQ(res1, MakeUInt128(0x012eb10b89bbca1fULL, 0xfedf0524765b0d28ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  // Saturates in the multiplication.
  __uint128_t arg4 = MakeUInt128(0x80000123456789a4ULL, 0xfeed000300000010ULL);
  __uint128_t arg5 = MakeUInt128(0x0123456789abcdefULL, 0x8000fedcba123456ULL);
  __uint128_t arg6 = MakeUInt128(0x0123456701234567ULL, 0x0123456701234567ULL);
  auto [res2, fpsr2] = AsmSqdmlal(arg4, arg5, arg6);
  ASSERT_EQ(res2, MakeUInt128(0xbbbc4567777f4567ULL, 0x7fffffff00004567ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));

  // Saturates in the addition.
  __uint128_t arg7 = MakeUInt128(0x1100110022002200ULL, 0x7654321076543210ULL);
  __uint128_t arg8 = MakeUInt128(0x8888111122223333ULL, 0x01234567ffffeeeeULL);
  __uint128_t arg9 = MakeUInt128(0x7fffffffffffffffULL, 0x00000a000000b000ULL);
  auto [res3, fpsr3] = AsmSqdmlal(arg7, arg8, arg9);
  ASSERT_EQ(res3, MakeUInt128(0x7fffffff004d4bffULL, 0x0026b00000275600ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr3));
}

TEST(Arm64InsnTest, SignedSaturatingDoublingMultiplyAddLongUpper64x2IndexedElem) {
  constexpr auto AsmSqdmlal = ASM_INSN_WRAP_FUNC_WQ_RES_WW0_ARG("sqdmlal2 %0.2d, %2.4s, %3.s[3]");

  // No saturation.
  __uint128_t arg1 = MakeUInt128(0x1100110022002200ULL, 0x7654321076543210ULL);
  __uint128_t arg2 = MakeUInt128(0x0000000400000004ULL, 0x0123456701234567ULL);
  __uint128_t arg3 = MakeUInt128(0x0100010001000100ULL, 0x0400040004000400ULL);
  auto [res1, fpsr1] = AsmSqdmlal(arg1, arg2, arg3);
  ASSERT_EQ(res1, MakeUInt128(0x020d44926c1ce9e0ULL, 0x050d47926f1cece0ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  // Saturates in the multiplication.
  __uint128_t arg4 = MakeUInt128(0x0123456789abcdefULL, 0x1122334480000000ULL);
  __uint128_t arg5 = MakeUInt128(0x0123456789abcdefULL, 0x8000000011223344ULL);
  __uint128_t arg6 = MakeUInt128(0x0101010102020202ULL, 0x0303030304040404ULL);
  auto [res2, fpsr2] = AsmSqdmlal(arg4, arg5, arg6);
  ASSERT_EQ(res2, MakeUInt128(0x7fffffffffffffffULL, 0xf1e0cfbf04040404ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));

  // Saturates in the addition.
  __uint128_t arg7 = MakeUInt128(0x1100110022002200ULL, 0x7654321076543210ULL);
  __uint128_t arg8 = MakeUInt128(0x1122334444332211ULL, 0x0123456701234567ULL);
  __uint128_t arg9 = MakeUInt128(0x7fffffffffffffffULL, 0x00000a000000b000ULL);
  auto [res3, fpsr3] = AsmSqdmlal(arg7, arg8, arg9);
  ASSERT_EQ(res3, MakeUInt128(0x7fffffffffffffffULL, 0x010d4d926b1d98e0ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr3));
}

TEST(Arm64InsnTest, SignedSaturatingDoublingMultiplyAddLongUpper32x4IndexedElem) {
  constexpr auto AsmSqdmlal = ASM_INSN_WRAP_FUNC_WQ_RES_WW0_ARG("sqdmlal2 %0.4s, %2.8h, %3.h[7]");

  // No saturation.
  __uint128_t arg1 = MakeUInt128(0x0102030405060708ULL, 0x7654321076543210ULL);
  __uint128_t arg2 = MakeUInt128(0x1122334488776655ULL, 0x0123456701234567ULL);
  __uint128_t arg3 = MakeUInt128(0x0123456789abcdefULL, 0xfedcba9876543210ULL);
  auto [res1, fpsr1] = AsmSqdmlal(arg1, arg2, arg3);
  ASSERT_EQ(res1, MakeUInt128(0x0230485f8a1d9e4fULL, 0xffe9bd9076c60270ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  // Saturates in the multiplication.
  __uint128_t arg4 = MakeUInt128(0x0011223344556677ULL, 0xfeedfeedfeed8000ULL);
  __uint128_t arg5 = MakeUInt128(0x0123456789abcdefULL, 0x8000fedcba123456ULL);
  __uint128_t arg6 = MakeUInt128(0x0123456701234567ULL, 0x0123456701234567ULL);
  auto [res2, fpsr2] = AsmSqdmlal(arg4, arg5, arg6);
  ASSERT_EQ(res2, MakeUInt128(0x023645677fffffffULL, 0x0236456702364567ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));

  // Saturates in the addition.
  __uint128_t arg7 = MakeUInt128(0x1100110022002200ULL, 0x7654321076543210ULL);
  __uint128_t arg8 = MakeUInt128(0x8888111122223333ULL, 0x01234567ffffeeeeULL);
  __uint128_t arg9 = MakeUInt128(0x7fffffffffffffffULL, 0x00000a000000b000ULL);
  auto [res3, fpsr3] = AsmSqdmlal(arg7, arg8, arg9);
  ASSERT_EQ(res3, MakeUInt128(0x7fffffff0071d05fULL, 0x010d0cf800728060ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr3));
}

TEST(Arm64InsnTest, SignedSaturatingDoublingMultiplyAddLong64x1IndexedElem) {
  constexpr auto AsmSqdmlal = ASM_INSN_WRAP_FUNC_WQ_RES_WW0_ARG("sqdmlal %d0, %s2, %3.s[3]");

  // No saturation.
  __uint128_t arg1 = MakeUInt128(0x0102030405060708ULL, 0x7654321076543210ULL);
  __uint128_t arg2 = MakeUInt128(0x1122334488776655ULL, 0x0123456701234567ULL);
  __uint128_t arg3 = MakeUInt128(0x0123456789abcdefULL, 0xfedcba9876543210ULL);
  auto [res1, fpsr1] = AsmSqdmlal(arg1, arg2, arg3);
  ASSERT_EQ(res1, MakeUInt128(0x012eb3d4d07fc65fULL, 0x0000000000000000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  // Saturates in the multiplication.
  __uint128_t arg4 = MakeUInt128(0x0011223380000000ULL, 0xfeedfeedfeed8000ULL);
  __uint128_t arg5 = MakeUInt128(0x0123456789abcdefULL, 0x80000000ba123456ULL);
  __uint128_t arg6 = MakeUInt128(0x0123456701234567ULL, 0x0123456701234567ULL);
  auto [res2, fpsr2] = AsmSqdmlal(arg4, arg5, arg6);
  ASSERT_EQ(res2, MakeUInt128(0x7fffffffffffffffULL, 0x0000000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));

  // Saturates in the addition.
  __uint128_t arg7 = MakeUInt128(0x1100110022002200ULL, 0x7654321076543210ULL);
  __uint128_t arg8 = MakeUInt128(0x8888111122223333ULL, 0x01234567ffffeeeeULL);
  __uint128_t arg9 = MakeUInt128(0x7fffffffffffffffULL, 0x00000a000000b000ULL);
  auto [res3, fpsr3] = AsmSqdmlal(arg7, arg8, arg9);
  ASSERT_EQ(res3, MakeUInt128(0x7fffffffffffffffULL, 0x0000000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr3));
}

TEST(Arm64InsnTest, SignedSaturatingDoublingMultiplyAddLong32x1IndexedElem) {
  constexpr auto AsmSqdmlal = ASM_INSN_WRAP_FUNC_WQ_RES_WW0_ARG("sqdmlal %s0, %h2, %3.h[7]");

  // No saturation.
  __uint128_t arg1 = MakeUInt128(0x0102030405060708ULL, 0x7654321076543210ULL);
  __uint128_t arg2 = MakeUInt128(0x1122334488776655ULL, 0x0123456701234567ULL);
  __uint128_t arg3 = MakeUInt128(0x0123456789abcdefULL, 0xfedcba9876543210ULL);
  auto [res1, fpsr1] = AsmSqdmlal(arg1, arg2, arg3);
  ASSERT_EQ(res1, MakeUInt128(0x0000000089bbca1fULL, 0x0000000000000000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  // Saturates in the multiplication.
  __uint128_t arg4 = MakeUInt128(0x0011223344558000ULL, 0xfeedfeedfeed1234ULL);
  __uint128_t arg5 = MakeUInt128(0x0123456789abcdefULL, 0x8000fedcba123456ULL);
  __uint128_t arg6 = MakeUInt128(0x0123456701234567ULL, 0x0123456701234567ULL);
  auto [res2, fpsr2] = AsmSqdmlal(arg4, arg5, arg6);
  ASSERT_EQ(res2, MakeUInt128(0x000000007fffffffULL, 0x0000000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));

  // Saturates in the addition.
  __uint128_t arg7 = MakeUInt128(0xaabbccddeeff2200ULL, 0x7654321076543210ULL);
  __uint128_t arg8 = MakeUInt128(0x8888111122223333ULL, 0x0123aabbccddeeffULL);
  __uint128_t arg9 = MakeUInt128(0xaabbccdd7fffffffULL, 0x0011223344556677ULL);
  auto [res3, fpsr3] = AsmSqdmlal(arg7, arg8, arg9);
  ASSERT_EQ(res3, MakeUInt128(0x000000007fffffffULL, 0x0000000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr3));
}

TEST(Arm64InsnTest, SignedSaturatingDoublingMultiplySubtractLong32x2) {
  constexpr auto AsmSqdmlsl = ASM_INSN_WRAP_FUNC_WQ_RES_WW0_ARG("sqdmlsl %0.2d, %2.2s, %3.2s");

  // No saturation.
  __uint128_t arg1 = MakeUInt128(0x0000000080000001ULL, 0x7654321076543210ULL);
  __uint128_t arg2 = MakeUInt128(0x0000000100000004ULL, 0x0123456701234567ULL);
  __uint128_t arg3 = MakeUInt128(0x0000100000000001ULL, 0x0400040004000400ULL);
  auto [res1, fpsr1] = AsmSqdmlsl(arg1, arg2, arg3);
  ASSERT_EQ(res1, MakeUInt128(0x00001003fffffff9ULL, 0x0400040004000400ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  // Saturates in the multiplication.
  __uint128_t arg4 = MakeUInt128(0x8000000000000004ULL, 0xfeed000300000010ULL);
  __uint128_t arg5 = MakeUInt128(0x8000000000000002ULL, 0xfeed000400000020ULL);
  __uint128_t arg6 = MakeUInt128(0x0000000000000900ULL, 0x00000a000000b000ULL);
  auto [res2, fpsr2] = AsmSqdmlsl(arg4, arg5, arg6);
  ASSERT_EQ(res2, MakeUInt128(0x00000000000008f0ULL, 0x80000a000000b001ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));

  // Saturates in the subtraction.
  __uint128_t arg7 = MakeUInt128(0x1100110022002200ULL, 0x7654321076543210ULL);
  __uint128_t arg8 = MakeUInt128(0x0000000400000004ULL, 0x0123456701234567ULL);
  __uint128_t arg9 = MakeUInt128(0x8000000000000000ULL, 0x00000a000000b000ULL);
  auto [res3, fpsr3] = AsmSqdmlsl(arg7, arg8, arg9);
  ASSERT_EQ(res3, MakeUInt128(0x8000000000000000ULL, 0x000009ff78002800ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr3));
}

TEST(Arm64InsnTest, SignedSaturatingDoublingMultiplySubtractLong16x4) {
  constexpr auto AsmSqdmlsl = ASM_INSN_WRAP_FUNC_WQ_RES_WW0_ARG("sqdmlsl %0.4s, %2.4h, %3.4h");

  // No saturation.
  __uint128_t arg1 = MakeUInt128(0x8000110022002200ULL, 0x7654321076543210ULL);
  __uint128_t arg2 = MakeUInt128(0x0010001100000004ULL, 0x0123456701234567ULL);
  __uint128_t arg3 = MakeUInt128(0x0100010001000100ULL, 0x0400040004000400ULL);
  auto [res1, fpsr1] = AsmSqdmlsl(arg1, arg2, arg3);
  ASSERT_EQ(res1, MakeUInt128(0x0100010000fef100ULL, 0x0410040003fdc200ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  // Saturates in the multiplication.
  __uint128_t arg4 = MakeUInt128(0x8000111111111111ULL, 0x1234123412341234ULL);
  __uint128_t arg5 = MakeUInt128(0x8000111111111111ULL, 0x1234123412341234ULL);
  __uint128_t arg6 = MakeUInt128(0x0123456701234567ULL, 0x0123456701234567ULL);
  auto [res2, fpsr2] = AsmSqdmlsl(arg4, arg5, arg6);
  ASSERT_EQ(res2, MakeUInt128(0xfedcbf25fedcbf25ULL, 0x81234568fedcbf25ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));

  // Saturates in the subtraction.
  __uint128_t arg7 = MakeUInt128(0x1100110022002200ULL, 0x7654321076543210ULL);
  __uint128_t arg8 = MakeUInt128(0x0000000400010004ULL, 0x0123456701234567ULL);
  __uint128_t arg9 = MakeUInt128(0x8000000012345678ULL, 0x00000a000000b000ULL);
  auto [res3, fpsr3] = AsmSqdmlsl(arg7, arg8, arg9);
  ASSERT_EQ(res3, MakeUInt128(0x8000000012334678ULL, 0x00000a0000002800ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr3));
}

TEST(Arm64InsnTest, SignedSaturatingDoublingMultiplySubtractLongUpper32x2) {
  constexpr auto AsmSqdmlsl = ASM_INSN_WRAP_FUNC_WQ_RES_WW0_ARG("sqdmlsl2 %0.2d, %2.4s, %3.4s");

  // No saturation.
  __uint128_t arg1 = MakeUInt128(0x1100110022002200ULL, 0x7654321076543210ULL);
  __uint128_t arg2 = MakeUInt128(0x0000000400000004ULL, 0x0123456701234567ULL);
  __uint128_t arg3 = MakeUInt128(0x0100010001000100ULL, 0x0400040004000400ULL);
  auto [res1, fpsr1] = AsmSqdmlsl(arg1, arg2, arg3);
  ASSERT_EQ(res1, MakeUInt128(0xfff2bd6d95e31820ULL, 0x02f2c06d98e31b20ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  // Saturates in the multiplication.
  __uint128_t arg4 = MakeUInt128(0x1234567800000004ULL, 0x8000000001100010ULL);
  __uint128_t arg5 = MakeUInt128(0x1234567800000002ULL, 0x8000000001100020ULL);
  __uint128_t arg6 = MakeUInt128(0x0000080000000900ULL, 0x00000a000000b000ULL);
  auto [res2, fpsr2] = AsmSqdmlsl(arg4, arg5, arg6);
  ASSERT_EQ(res2, MakeUInt128(0xfffdc5ff9a000500ULL, 0x80000a000000b001ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));

  // Saturates in the subtraction.
  __uint128_t arg7 = MakeUInt128(0x1100110022002200ULL, 0x7654321076543210ULL);
  __uint128_t arg8 = MakeUInt128(0x0000000400000004ULL, 0x0123456701234567ULL);
  __uint128_t arg9 = MakeUInt128(0x1234567812345678ULL, 0x8000000000000000ULL);
  auto [res3, fpsr3] = AsmSqdmlsl(arg7, arg8, arg9);
  ASSERT_EQ(res3, MakeUInt128(0x112712e5a7176d98ULL, 0x8000000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr3));
}

TEST(Arm64InsnTest, SignedSaturatingDoublingMultiplySubtractLongUpper16x4) {
  constexpr auto AsmSqdmlsl = ASM_INSN_WRAP_FUNC_WQ_RES_WW0_ARG("sqdmlsl2 %0.4s, %2.8h, %3.8h");

  // No saturation.
  __uint128_t arg1 = MakeUInt128(0x8000110022002200ULL, 0x7654321076543210ULL);
  __uint128_t arg2 = MakeUInt128(0x0010001100000004ULL, 0x0123456701234567ULL);
  __uint128_t arg3 = MakeUInt128(0x0100010001000100ULL, 0x0400040004000400ULL);
  auto [res1, fpsr1] = AsmSqdmlsl(arg1, arg2, arg3);
  ASSERT_EQ(res1, MakeUInt128(0xfff2fe08e5db1820ULL, 0x02f30108e8db1b20ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  // Saturates in the multiplication.
  __uint128_t arg4 = MakeUInt128(0x1111111111111111ULL, 0x8000123412341234ULL);
  __uint128_t arg5 = MakeUInt128(0x1111111111111111ULL, 0x8000123412341234ULL);
  __uint128_t arg6 = MakeUInt128(0x0123456701234567ULL, 0x0123456701234567ULL);
  auto [res2, fpsr2] = AsmSqdmlsl(arg4, arg5, arg6);
  ASSERT_EQ(res2, MakeUInt128(0xfe8c9047fe8c9047ULL, 0x81234568fe8c9047ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));

  // Saturates in the subtraction.
  __uint128_t arg7 = MakeUInt128(0x1100110022002200ULL, 0x7654321076543210ULL);
  __uint128_t arg8 = MakeUInt128(0x0000000400010004ULL, 0x0123456701234567ULL);
  __uint128_t arg9 = MakeUInt128(0x1234567812345678ULL, 0x800000000000b000ULL);
  auto [res3, fpsr3] = AsmSqdmlsl(arg7, arg8, arg9);
  ASSERT_EQ(res3, MakeUInt128(0x11275380f70f6d98ULL, 0x80000000e4dbc720ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr3));
}

TEST(Arm64InsnTest, SignedSaturatingDoublingMultiplySubtractLong64x1) {
  constexpr auto AsmSqdmlsl = ASM_INSN_WRAP_FUNC_WQ_RES_WW0_ARG("sqdmlsl %d0, %s2, %s3");

  // No saturation.
  __uint128_t arg1 = MakeUInt128(0x1100110011223344ULL, 0x7654321076543210ULL);
  __uint128_t arg2 = MakeUInt128(0x0000000020000000ULL, 0x0123456701234567ULL);
  __uint128_t arg3 = MakeUInt128(0x12345678000000FFULL, 0x0400040004000400ULL);
  auto [res1, fpsr1] = AsmSqdmlsl(arg1, arg2, arg3);
  ASSERT_EQ(res1, MakeUInt128(0x0debc9a7000000ffULL, 0x0000000000000000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  // Saturates in the multiplication.
  __uint128_t arg4 = MakeUInt128(0x1122334480000000ULL, 0xfeed000300000010ULL);
  __uint128_t arg5 = MakeUInt128(0xaabbccdd80000000ULL, 0xfeed000400000020ULL);
  __uint128_t arg6 = MakeUInt128(0x1122334411111111ULL, 0x00000a000000b000ULL);
  auto [res2, fpsr2] = AsmSqdmlsl(arg4, arg5, arg6);
  ASSERT_EQ(res2, MakeUInt128(0x9122334411111112ULL, 0x0000000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));

  // Saturates in the subtraction.
  __uint128_t arg7 = MakeUInt128(0x1122334400111111ULL, 0x7654321076543210ULL);
  __uint128_t arg8 = MakeUInt128(0xaabbccdd00222222ULL, 0x0123456701234567ULL);
  __uint128_t arg9 = MakeUInt128(0x8000000000000000ULL, 0x00000a000000b000ULL);
  auto [res3, fpsr3] = AsmSqdmlsl(arg7, arg8, arg9);
  ASSERT_EQ(res3, MakeUInt128(0x8000000000000000ULL, 0x0000000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr3));
}

TEST(Arm64InsnTest, SignedSaturatingDoublingMultiplySubtractLong32x1) {
  constexpr auto AsmSqdmlsl = ASM_INSN_WRAP_FUNC_WQ_RES_WW0_ARG("sqdmlsl %s0, %h2, %h3");

  // No saturation.
  __uint128_t arg1 = MakeUInt128(0x1100110022002200ULL, 0x7654321076543210ULL);
  __uint128_t arg2 = MakeUInt128(0x0000000000000004ULL, 0x0123456701234567ULL);
  __uint128_t arg3 = MakeUInt128(0x0100010001000100ULL, 0x0400040004000400ULL);
  auto [res1, fpsr1] = AsmSqdmlsl(arg1, arg2, arg3);
  ASSERT_EQ(res1, MakeUInt128(0x0000000000fef100ULL, 0x0000000000000000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  // Saturates in the multiplication.
  __uint128_t arg4 = MakeUInt128(0x1122334411228000ULL, 0xfeed000300000010ULL);
  __uint128_t arg5 = MakeUInt128(0xaabbccddaabb8000ULL, 0xfeed000400000020ULL);
  __uint128_t arg6 = MakeUInt128(0x1122334411111111ULL, 0x00000a000000b000ULL);
  auto [res2, fpsr2] = AsmSqdmlsl(arg4, arg5, arg6);
  ASSERT_EQ(res2, MakeUInt128(0x0000000091111112ULL, 0x0000000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));

  // Saturates in the subtraction.
  __uint128_t arg7 = MakeUInt128(0x1122334411220123ULL, 0x7654321076543210ULL);
  __uint128_t arg8 = MakeUInt128(0xaabbccddaabb0044ULL, 0x0123456701234567ULL);
  __uint128_t arg9 = MakeUInt128(0xaabbccdd80000000ULL, 0x00000a000000b000ULL);
  auto [res3, fpsr3] = AsmSqdmlsl(arg7, arg8, arg9);
  ASSERT_EQ(res3, MakeUInt128(0x0000000080000000ULL, 0x0000000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr3));
}

TEST(Arm64InsnTest, SignedSaturatingDoublingMultiplySubtractLong64x2IndexedElem) {
  constexpr auto AsmSqdmlsl = ASM_INSN_WRAP_FUNC_WQ_RES_WW0_ARG("sqdmlsl %0.2d, %2.2s, %3.s[1]");

  // No saturation.
  __uint128_t arg1 = MakeUInt128(0x1100110022002200ULL, 0x7654321076543210ULL);
  __uint128_t arg2 = MakeUInt128(0x0000000400000004ULL, 0x0123456701234567ULL);
  __uint128_t arg3 = MakeUInt128(0x0100010001000100ULL, 0x0400040004000400ULL);
  auto [res1, fpsr1] = AsmSqdmlsl(arg1, arg2, arg3);
  ASSERT_EQ(res1, MakeUInt128(0x010000fef0fef100ULL, 0x040003ff7bff7c00ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  // Saturates in the multiplication.
  __uint128_t arg4 = MakeUInt128(0x8000000000000004ULL, 0xfeed000300000010ULL);
  __uint128_t arg5 = MakeUInt128(0x8000000000000002ULL, 0xfeed000400000020ULL);
  __uint128_t arg6 = MakeUInt128(0x0000080000000900ULL, 0x00000a000000b000ULL);
  auto [res2, fpsr2] = AsmSqdmlsl(arg4, arg5, arg6);
  ASSERT_EQ(res2, MakeUInt128(0x0000080400000900ULL, 0x80000a000000b001ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));

  // Saturates in the subtraction.
  __uint128_t arg7 = MakeUInt128(0x1100110022002200ULL, 0x7654321076543210ULL);
  __uint128_t arg8 = MakeUInt128(0x0000000400000004ULL, 0x0123456701234567ULL);
  __uint128_t arg9 = MakeUInt128(0x8000000000000000ULL, 0x00000a000000b000ULL);
  auto [res3, fpsr3] = AsmSqdmlsl(arg7, arg8, arg9);
  ASSERT_EQ(res3, MakeUInt128(0x8000000000000000ULL, 0x000009ff78002800ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr3));
}

TEST(Arm64InsnTest, SignedSaturatingDoublingMultiplySubtractLong32x4IndexedElem) {
  constexpr auto AsmSqdmlsl = ASM_INSN_WRAP_FUNC_WQ_RES_WW0_ARG("sqdmlsl %0.4s, %2.4h, %3.h[7]");

  // No saturation.
  __uint128_t arg1 = MakeUInt128(0x0102030405060708ULL, 0x7654321076543210ULL);
  __uint128_t arg2 = MakeUInt128(0x1122334488776655ULL, 0x0123456701234567ULL);
  __uint128_t arg3 = MakeUInt128(0x0123456789abcdefULL, 0xfedcba9876543210ULL);
  auto [res1, fpsr1] = AsmSqdmlsl(arg1, arg2, arg3);
  ASSERT_EQ(res1, MakeUInt128(0x0117d9c3899bd1bfULL, 0xfeda700c764d56f8ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  // Saturates in the multiplication.
  __uint128_t arg4 = MakeUInt128(0x80000123456789a4ULL, 0xfeed000300000010ULL);
  __uint128_t arg5 = MakeUInt128(0x0123456789abcdefULL, 0x8000fedcba123456ULL);
  __uint128_t arg6 = MakeUInt128(0x0123456701234567ULL, 0x0123456701234567ULL);
  auto [res2, fpsr2] = AsmSqdmlsl(arg4, arg5, arg6);
  ASSERT_EQ(res2, MakeUInt128(0x468a45678ac74567ULL, 0x8123456802464567ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));

  // Saturates in the subtraction.
  __uint128_t arg7 = MakeUInt128(0x1100110022002200ULL, 0x7654321076543210ULL);
  __uint128_t arg8 = MakeUInt128(0x8888111122223333ULL, 0x01234567ffffeeeeULL);
  __uint128_t arg9 = MakeUInt128(0x8000000000000000ULL, 0x00000a000000b000ULL);
  auto [res3, fpsr3] = AsmSqdmlsl(arg7, arg8, arg9);
  ASSERT_EQ(res3, MakeUInt128(0x80000000ffb2b400ULL, 0xffd96400ffda0a00ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr3));
}

TEST(Arm64InsnTest, SignedSaturatingDoublingMultiplySubtractLongUpper64x2IndexedElem) {
  constexpr auto AsmSqdmlsl = ASM_INSN_WRAP_FUNC_WQ_RES_WW0_ARG("sqdmlsl2 %0.2d, %2.4s, %3.s[3]");

  // No saturation.
  __uint128_t arg1 = MakeUInt128(0x1100110022002200ULL, 0x7654321076543210ULL);
  __uint128_t arg2 = MakeUInt128(0x0000000400000004ULL, 0x0123456701234567ULL);
  __uint128_t arg3 = MakeUInt128(0x0100010001000100ULL, 0x0400040004000400ULL);
  auto [res1, fpsr1] = AsmSqdmlsl(arg1, arg2, arg3);
  ASSERT_EQ(res1, MakeUInt128(0xfff2bd6d95e31820ULL, 0x02f2c06d98e31b20ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  // Saturates in the multiplication.
  __uint128_t arg4 = MakeUInt128(0x0123456789abcdefULL, 0x1122334480000000ULL);
  __uint128_t arg5 = MakeUInt128(0x0123456789abcdefULL, 0x8000000011223344ULL);
  __uint128_t arg6 = MakeUInt128(0x0101010102020202ULL, 0x0303030304040404ULL);
  auto [res2, fpsr2] = AsmSqdmlsl(arg4, arg5, arg6);
  ASSERT_EQ(res2, MakeUInt128(0x8101010102020203ULL, 0x1425364704040404ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));

  // Saturates in the subtraction.
  __uint128_t arg7 = MakeUInt128(0x1100110022002200ULL, 0x7654321076543210ULL);
  __uint128_t arg8 = MakeUInt128(0x1122334444332211ULL, 0x0123456701234567ULL);
  __uint128_t arg9 = MakeUInt128(0x8000000000000000ULL, 0x00000a000000b000ULL);
  auto [res3, fpsr3] = AsmSqdmlsl(arg7, arg8, arg9);
  ASSERT_EQ(res3, MakeUInt128(0x8000000000000000ULL, 0xfef2c66d94e3c720ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr3));
}

TEST(Arm64InsnTest, SignedSaturatingDoublingMultiplySubtractLongUpper32x4IndexedElem) {
  constexpr auto AsmSqdmlsl = ASM_INSN_WRAP_FUNC_WQ_RES_WW0_ARG("sqdmlsl2 %0.4s, %2.8h, %3.h[7]");

  // No saturation.
  __uint128_t arg1 = MakeUInt128(0x0102030405060708ULL, 0x7654321076543210ULL);
  __uint128_t arg2 = MakeUInt128(0x1122334488776655ULL, 0x0123456701234567ULL);
  __uint128_t arg3 = MakeUInt128(0x0123456789abcdefULL, 0xfedcba9876543210ULL);
  auto [res1, fpsr1] = AsmSqdmlsl(arg1, arg2, arg3);
  ASSERT_EQ(res1, MakeUInt128(0x0016426f8939fd8fULL, 0xfdcfb7a075e261b0ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  // Saturates in the multiplication.
  __uint128_t arg4 = MakeUInt128(0x0011223344556677ULL, 0xfeedfeedfeed8000ULL);
  __uint128_t arg5 = MakeUInt128(0x0123456789abcdefULL, 0x8000fedcba123456ULL);
  __uint128_t arg6 = MakeUInt128(0x0123456701234567ULL, 0x0123456701234567ULL);
  auto [res2, fpsr2] = AsmSqdmlsl(arg4, arg5, arg6);
  ASSERT_EQ(res2, MakeUInt128(0x0010456781234568ULL, 0x0010456700104567ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));

  // Saturates in the subtraction.
  __uint128_t arg7 = MakeUInt128(0x1100110022002200ULL, 0x7654321076543210ULL);
  __uint128_t arg8 = MakeUInt128(0x8888111122223333ULL, 0x01234567ffffeeeeULL);
  __uint128_t arg9 = MakeUInt128(0x8000000000000000ULL, 0x00000a000000b000ULL);
  auto [res3, fpsr3] = AsmSqdmlsl(arg7, arg8, arg9);
  ASSERT_EQ(res3, MakeUInt128(0x80000000ff8e2fa0ULL, 0xfef30708ff8edfa0ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr3));
}

TEST(Arm64InsnTest, SignedSaturatingDoublingMultiplySubtractLong64x1IndexedElem) {
  constexpr auto AsmSqdmlsl = ASM_INSN_WRAP_FUNC_WQ_RES_WW0_ARG("sqdmlsl %d0, %s2, %3.s[3]");

  // No saturation.
  __uint128_t arg1 = MakeUInt128(0x0102030405060708ULL, 0x7654321076543210ULL);
  __uint128_t arg2 = MakeUInt128(0x1122334488776655ULL, 0x0123456701234567ULL);
  __uint128_t arg3 = MakeUInt128(0x0123456789abcdefULL, 0xfedcba9876543210ULL);
  auto [res1, fpsr1] = AsmSqdmlsl(arg1, arg2, arg3);
  ASSERT_EQ(res1, MakeUInt128(0x0117d6fa42d7d57fULL, 0x0ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  // Saturates in the multiplication.
  __uint128_t arg4 = MakeUInt128(0x0011223380000000ULL, 0xfeedfeedfeed8000ULL);
  __uint128_t arg5 = MakeUInt128(0x0123456789abcdefULL, 0x80000000ba123456ULL);
  __uint128_t arg6 = MakeUInt128(0x0123456701234567ULL, 0x0123456701234567ULL);
  auto [res2, fpsr2] = AsmSqdmlsl(arg4, arg5, arg6);
  ASSERT_EQ(res2, MakeUInt128(0x8123456701234568ULL, 0x0ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));

  // Saturates in the subtraction.
  __uint128_t arg7 = MakeUInt128(0x1100110022002200ULL, 0x7654321076543210ULL);
  __uint128_t arg8 = MakeUInt128(0x8888111122223333ULL, 0x01234567ffffeeeeULL);
  __uint128_t arg9 = MakeUInt128(0x8000000000000000ULL, 0x00000a000000b000ULL);
  auto [res3, fpsr3] = AsmSqdmlsl(arg7, arg8, arg9);
  ASSERT_EQ(res3, MakeUInt128(0x8000000000000000ULL, 0x0ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr3));
}

TEST(Arm64InsnTest, SignedSaturatingDoublingMultiplySubtractLong32x1IndexedElem) {
  constexpr auto AsmSqdmlsl = ASM_INSN_WRAP_FUNC_WQ_RES_WW0_ARG("sqdmlsl %s0, %h2, %3.h[7]");

  // No saturation.
  __uint128_t arg1 = MakeUInt128(0x0102030405060708ULL, 0x7654321076543210ULL);
  __uint128_t arg2 = MakeUInt128(0x1122334488776655ULL, 0x0123456701234567ULL);
  __uint128_t arg3 = MakeUInt128(0x0123456789abcdefULL, 0xfedcba9876543210ULL);
  auto [res1, fpsr1] = AsmSqdmlsl(arg1, arg2, arg3);
  ASSERT_EQ(res1, MakeUInt128(0x00000000899bd1bfULL, 0x0ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  // Saturates in the multiplication.
  __uint128_t arg4 = MakeUInt128(0x0011223344558000ULL, 0xfeedfeedfeed1234ULL);
  __uint128_t arg5 = MakeUInt128(0x0123456789abcdefULL, 0x8000fedcba123456ULL);
  __uint128_t arg6 = MakeUInt128(0x0123456701234567ULL, 0x0123456701234567ULL);
  auto [res2, fpsr2] = AsmSqdmlsl(arg4, arg5, arg6);
  ASSERT_EQ(res2, MakeUInt128(0x0000000081234568ULL, 0x0ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));

  // Saturates in the subtraction.
  __uint128_t arg7 = MakeUInt128(0xaabbccddeeff2200ULL, 0x7654321076543210ULL);
  __uint128_t arg8 = MakeUInt128(0x8888111122223333ULL, 0x0123aabbccddeeffULL);
  __uint128_t arg9 = MakeUInt128(0xaabbccdd80000000ULL, 0x0011223344556677ULL);
  auto [res3, fpsr3] = AsmSqdmlsl(arg7, arg8, arg9);
  ASSERT_EQ(res3, MakeUInt128(0x0000000080000000ULL, 0x0ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr3));
}

TEST(Arm64InsnTest, SignedSaturatingRoundingDoublingMultiplyHighHalf32x4) {
  constexpr auto AsmSqrdmulh = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("sqrdmulh %0.4s, %2.4s, %3.4s");

  __uint128_t arg1 = MakeU32x4(0x20000001UL, 0x00000004UL, 0x7eed0003UL, 0x00000010UL);
  __uint128_t arg2 = MakeU32x4(0x00000008UL, 0x00000002UL, 0x7eed0004UL, 0x00000002UL);
  auto [res1, fpsr1] = AsmSqrdmulh(arg1, arg2);
  ASSERT_EQ(res1, MakeU32x4(0x2UL, 0x0UL, 0x7ddc4ed9UL, 0x0UL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeU32x4(0x80000000UL, 0x00000004UL, 0xfeed0003UL, 0x00000010UL);
  __uint128_t arg4 = MakeU32x4(0x80000000UL, 0x00000002UL, 0xfeed0004UL, 0x00000002UL);
  auto [res2, fpsr2] = AsmSqrdmulh(arg3, arg4);
  ASSERT_EQ(res2, MakeU32x4(0x7fffffffUL, 0x0UL, 0x00024ed2UL, 0x0UL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingRoundingDoublingMultiplyHighHalf32x2) {
  constexpr auto AsmSqrdmulh = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("sqrdmulh %0.2s, %2.2s, %3.2s");

  __uint128_t arg1 = MakeU32x4(0x55555555UL, 0x00000004UL, 0xdeadc0deUL, 0xdeadc0deUL);
  __uint128_t arg2 = MakeU32x4(0x00000004UL, 0x00000002UL, 0xdeadc0deUL, 0xdeadc0deUL);
  auto [res1, fpsr1] = AsmSqrdmulh(arg1, arg2);
  ASSERT_EQ(res1, MakeU32x4(0x3, 0x0UL, 0x0UL, 0x0UL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeU32x4(0x80000000UL, 0x00000004UL, 0xdeadc0deUL, 0xdeadc0deUL);
  __uint128_t arg4 = MakeU32x4(0x80000000UL, 0x00000002UL, 0xdeadc0deUL, 0xdeadc0deUL);
  auto [res2, fpsr2] = AsmSqrdmulh(arg3, arg4);
  ASSERT_EQ(res2, MakeU32x4(0x7fffffffUL, 0x0UL, 0x0UL, 0x0UL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingRoundingDoublingMultiplyHighHalf16x8) {
  constexpr auto AsmSqrdmulh = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("sqrdmulh %0.8h, %2.8h, %3.8h");

  __uint128_t arg1 = MakeUInt128(0x200000017fff1111ULL, 0x7eed000300000010ULL);
  __uint128_t arg2 = MakeUInt128(0x0008000840000000ULL, 0x7eed000400000002ULL);
  auto [res1, fpsr1] = AsmSqrdmulh(arg1, arg2);
  ASSERT_EQ(res1, MakeUInt128(0x0002000040000000ULL, 0x7ddc000000000000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeUInt128(0x8000700040010000ULL, 0xfeed0003ffff0010ULL);
  __uint128_t arg4 = MakeUInt128(0x8000000100040000ULL, 0xfeed0004ffff0002ULL);
  auto [res2, fpsr2] = AsmSqrdmulh(arg3, arg4);
  ASSERT_EQ(res2, MakeUInt128(0x7fff000100020000ULL, 0x0002000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingRoundingDoublingMultiplyHighHalf16x4) {
  constexpr auto AsmSqrdmulh = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("sqrdmulh %0.4h, %2.4h, %3.4h");

  __uint128_t arg1 = MakeUInt128(0x555500017fff1111ULL, 0xdeadc0dedeadc0deULL);
  __uint128_t arg2 = MakeUInt128(0x0004000840000000ULL, 0xdeadc0dedeadc0deULL);
  auto [res1, fpsr1] = AsmSqrdmulh(arg1, arg2);
  ASSERT_EQ(res1, MakeUInt128(0x0003000040000000ULL, 0x0000000000000000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeUInt128(0x8000700040010000ULL, 0xdeadc0dedeadc0deULL);
  __uint128_t arg4 = MakeUInt128(0x8000000100040000ULL, 0xdeadc0dedeadc0deULL);
  auto [res2, fpsr2] = AsmSqrdmulh(arg3, arg4);
  ASSERT_EQ(res2, MakeUInt128(0x7fff000100020000ULL, 0x0000000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingRoundingDoublingMultiplyHighHalf32x4IndexedElem) {
  constexpr auto AsmSqrdmulh = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("sqrdmulh %0.4s, %2.4s, %3.s[0]");

  __uint128_t arg1 = MakeU32x4(0x20000001UL, 0x00000004UL, 0x7eed0003, 0x00000010UL);
  __uint128_t arg2 = MakeU32x4(0x00000008UL, 0xfeedfeedUL, 0xfeedfeed, 0xfeedfeedUL);
  auto [res1, fpsr1] = AsmSqrdmulh(arg1, arg2);
  // Without rounding, result should be 7 instead of 8.
  ASSERT_EQ(res1, MakeU32x4(0x2UL, 0x0UL, 0x8UL, 0x0UL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeU32x4(0x80000000UL, 0x00000004UL, 0xfeed0003UL, 0x00000010UL);
  __uint128_t arg4 = MakeU32x4(0x80000000UL, 0xfeedfeedUL, 0xfeedfeedUL, 0xfeedfeedUL);
  auto [res2, fpsr2] = AsmSqrdmulh(arg3, arg4);
  ASSERT_EQ(res2, MakeU32x4(0x7fffffffUL, 0xfffffffcUL, 0x0112fffdUL, 0xfffffff0UL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingRoundingDoublingMultiplyHighHalf32x2IndexedElem) {
  constexpr auto AsmSqrdmulh = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("sqrdmulh %0.2s, %2.2s, %3.s[0]");

  __uint128_t arg1 = MakeU32x4(0x55555555UL, 0x00000004UL, 0xdeadc0deUL, 0xdeadc0deUL);
  __uint128_t arg2 = MakeU32x4(0x00000004UL, 0xdeadc0deUL, 0xdeadc0deUL, 0xdeadc0deUL);
  auto [res1, fpsr1] = AsmSqrdmulh(arg1, arg2);
  ASSERT_EQ(res1, MakeU32x4(0x3UL, 0x0UL, 0x0UL, 0x0UL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeU32x4(0x80000000UL, 0x00000004UL, 0xdeadc0deUL, 0xdeadc0deUL);
  __uint128_t arg4 = MakeU32x4(0x80000000UL, 0xdeadc0deUL, 0xdeadc0deUL, 0xdeadc0deUL);
  auto [res2, fpsr2] = AsmSqrdmulh(arg3, arg4);
  ASSERT_EQ(res2, MakeU32x4(0x7fffffffUL, 0xfffffffcUL, 0x0UL, 0x0UL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingRoundingDoublingMultiplyHighHalf16x8IndexedElem) {
  constexpr auto AsmSqrdmulh = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("sqrdmulh %0.8h, %2.8h, %3.h[7]");

  __uint128_t arg1 = MakeUInt128(0x7fff800045670000ULL, 0xfe00780020004001ULL);
  __uint128_t arg2 = MakeUInt128(0xfeedfeedfeedfeedULL, 0x0008feedfeedfeedULL);
  auto [res1, fpsr1] = AsmSqrdmulh(arg1, arg2);
  ASSERT_EQ(res1, MakeUInt128(0x0008fff800040000ULL, 0x0000000800020004ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeUInt128(0x7fff800045670000ULL, 0xfe00780020004001ULL);
  __uint128_t arg4 = MakeUInt128(0xfeedfeedfeedfeedULL, 0x8000feedfeedfeedULL);
  auto [res2, fpsr2] = AsmSqrdmulh(arg3, arg4);
  ASSERT_EQ(res2, MakeUInt128(0x80017fffba990000ULL, 0x02008800e000bfffULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingRoundingDoublingMultiplyHighHalf16x4IndexedElem) {
  constexpr auto AsmSqrdmulh = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("sqrdmulh %0.4h, %2.4h, %3.h[7]");

  __uint128_t arg1 = MakeUInt128(0x7fff800055550000ULL, 0xdeadc0dedeadc0deULL);
  __uint128_t arg2 = MakeUInt128(0xdeadc0dedeadc0deULL, 0x0004c0dedeadc0deULL);
  auto [res1, fpsr1] = AsmSqrdmulh(arg1, arg2);
  ASSERT_EQ(res1, MakeUInt128(0x0004fffc00030000ULL, 0x0000000000000000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeUInt128(0x7fff800045670000ULL, 0xdeadc0dedeadc0deULL);
  __uint128_t arg4 = MakeUInt128(0xdeadc0dedeadc0deULL, 0x8000c0dedeadc0deULL);
  auto [res2, fpsr2] = AsmSqrdmulh(arg3, arg4);
  ASSERT_EQ(res2, MakeUInt128(0x80017fffba990000ULL, 0x0000000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingRoundingDoublingMultiplyHighHalf32x1) {
  constexpr auto AsmSqrdmulh = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("sqrdmulh %s0, %s2, %s3");

  __uint128_t arg1 = MakeU32x4(0x556789abUL, 0xfeedfeedUL, 0xfeedfeedUL, 0xfeedfeedUL);
  __uint128_t arg2 = MakeU32x4(0x00000004UL, 0xfeedfeedUL, 0xfeedfeedUL, 0xfeedfeedUL);
  auto [res1, fpsr1] = AsmSqrdmulh(arg1, arg2);
  // Without roundings, result should be 2 instead of 3.
  ASSERT_EQ(res1, MakeU32x4(0x3UL, 0x0UL, 0x0UL, 0x0UL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeU32x4(0x80000000UL, 0xfeedfeedUL, 0xfeedfeedUL, 0xfeedfeedUL);
  __uint128_t arg4 = MakeU32x4(0x80000000UL, 0xfeedfeedUL, 0xfeedfeedUL, 0xfeedfeedUL);
  auto [res2, fpsr2] = AsmSqrdmulh(arg3, arg4);
  ASSERT_EQ(res2, MakeU32x4(0x7fffffffUL, 0x0UL, 0x0UL, 0x0UL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingRoundingDoublingMultiplyHighHalf16x1) {
  constexpr auto AsmSqrdmulh = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("sqrdmulh %h0, %h2, %h3");

  __uint128_t arg1 = MakeUInt128(0xfeedfeedfeed5567ULL, 0xfeedfeedfeedfeedULL);
  __uint128_t arg2 = MakeUInt128(0xfeedfeedfeed0004ULL, 0xfeedfeedfeedfeedULL);
  auto [res1, fpsr1] = AsmSqrdmulh(arg1, arg2);
  ASSERT_EQ(res1, MakeUInt128(0x0000000000000003ULL, 0x0ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeUInt128(0xfeedfeedfeed8000ULL, 0xfeedfeedfeedfeedULL);
  __uint128_t arg4 = MakeUInt128(0xfeedfeedfeed8000ULL, 0xfeedfeedfeedfeedULL);
  auto [res2, fpsr2] = AsmSqrdmulh(arg3, arg4);
  ASSERT_EQ(res2, MakeUInt128(0x0000000000007fffULL, 0x0ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingRoundingDoublingMultiplyHighHalf32x1IndexedElem) {
  constexpr auto AsmSqrdmulh = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("sqrdmulh %s0, %s2, %3.s[2]");

  __uint128_t arg1 = MakeU32x4(0x556789abUL, 0xfeedfeedUL, 0xfeedfeedUL, 0xfeedfeedUL);
  __uint128_t arg2 = MakeU32x4(0xfeedfeedUL, 0xfeedfeedUL, 0x00000004UL, 0xfeedfeedUL);
  auto [res1, fpsr1] = AsmSqrdmulh(arg1, arg2);
  // Without rounding, result should be 2 instead of 3.
  ASSERT_EQ(res1, MakeU32x4(0x3UL, 0x0UL, 0x0UL, 0x0UL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeU32x4(0x80000000UL, 0xfeedfeedUL, 0xfeedfeedUL, 0xfeedfeedUL);
  __uint128_t arg4 = MakeU32x4(0xfeedfeedUL, 0xfeedfeedUL, 0x80000000UL, 0xfeedfeedUL);
  auto [res2, fpsr2] = AsmSqrdmulh(arg3, arg4);
  ASSERT_EQ(res2, MakeU32x4(0x7fffffffUL, 0x0UL, 0x0UL, 0x0UL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingRoundingDoublingMultiplyHighHalf16x1IndexedElem) {
  constexpr auto AsmSqrdmulh = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("sqrdmulh %h0, %h2, %3.h[7]");

  __uint128_t arg1 = MakeUInt128(0xfeedfeedfeed5567ULL, 0xfeedfeedfeedfeedULL);
  __uint128_t arg2 = MakeUInt128(0xfeedfeedfeedfeedULL, 0x0004feedfeedfeedULL);
  auto [res1, fpsr1] = AsmSqrdmulh(arg1, arg2);
  // Without rounding, result should be 2 instead of 3.
  ASSERT_EQ(res1, MakeUInt128(0x0000000000000003ULL, 0x0ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeUInt128(0xfeedfeedfeed8000ULL, 0xfeedfeedfeedfeedULL);
  __uint128_t arg4 = MakeUInt128(0xfeedfeedfeedfeedULL, 0x8000feedfeedfeedULL);
  auto [res2, fpsr2] = AsmSqrdmulh(arg3, arg4);
  ASSERT_EQ(res2, MakeUInt128(0x0000000000007fffULL, 0x0ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingDoublingMultiplyHighHalf32x4) {
  constexpr auto AsmSqdmulh = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("sqdmulh %0.4s, %2.4s, %3.4s");

  __uint128_t arg1 = MakeU32x4(0x20000001UL, 0x00000004UL, 0x7eed0003UL, 0x00000010UL);
  __uint128_t arg2 = MakeU32x4(0x00000008UL, 0x00000002UL, 0x7eed0004UL, 0x00000002UL);
  auto [res1, fpsr1] = AsmSqdmulh(arg1, arg2);
  ASSERT_EQ(res1, MakeU32x4(0x2UL, 0x0UL, 0x7ddc4ed8UL, 0x0UL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeU32x4(0x80000000UL, 0x00000004UL, 0xfeed0003UL, 0x00000010UL);
  __uint128_t arg4 = MakeU32x4(0x80000000UL, 0x00000002UL, 0xfeed0004UL, 0x00000002UL);
  auto [res2, fpsr2] = AsmSqdmulh(arg3, arg4);
  ASSERT_EQ(res2, MakeU32x4(0x7fffffffUL, 0x0UL, 0x00024ed1UL, 0x0UL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingDoublingMultiplyHighHalf32x2) {
  constexpr auto AsmSqdmulh = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("sqdmulh %0.2s, %2.2s, %3.2s");

  __uint128_t arg1 = MakeU32x4(0x55555555UL, 0x00000004UL, 0xdeadc0deUL, 0xdeadc0deUL);
  __uint128_t arg2 = MakeU32x4(0x00000004UL, 0x00000002UL, 0xdeadc0deUL, 0xdeadc0deUL);
  auto [res1, fpsr1] = AsmSqdmulh(arg1, arg2);
  ASSERT_EQ(res1, MakeU32x4(0x2UL, 0x0UL, 0x0UL, 0x0UL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeU32x4(0x80000000UL, 0x00000004UL, 0xdeadc0deUL, 0xdeadc0deUL);
  __uint128_t arg4 = MakeU32x4(0x80000000UL, 0x00000002UL, 0xdeadc0deUL, 0xdeadc0deUL);
  auto [res2, fpsr2] = AsmSqdmulh(arg3, arg4);
  ASSERT_EQ(res2, MakeU32x4(0x7fffffff, 0x0UL, 0x0UL, 0x0UL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingDoublingMultiplyHighHalf16x8) {
  constexpr auto AsmSqdmulh = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("sqdmulh %0.8h, %2.8h, %3.8h");

  __uint128_t arg1 = MakeUInt128(0x200000017fff1111ULL, 0x7eed000300000010ULL);
  __uint128_t arg2 = MakeUInt128(0x0008000840000000ULL, 0x7eed000400000002ULL);
  auto [res1, fpsr1] = AsmSqdmulh(arg1, arg2);
  ASSERT_EQ(res1, MakeUInt128(0x000200003fff0000ULL, 0x7ddc000000000000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeUInt128(0x8000700040010000ULL, 0xfeed0003ffff0010ULL);
  __uint128_t arg4 = MakeUInt128(0x8000000100040000ULL, 0xfeed0004ffff0002ULL);
  auto [res2, fpsr2] = AsmSqdmulh(arg3, arg4);
  ASSERT_EQ(res2, MakeUInt128(0x7fff000000020000ULL, 0x0002000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingDoublingMultiplyHighHalf16x4) {
  constexpr auto AsmSqdmulh = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("sqdmulh %0.4h, %2.4h, %3.4h");

  __uint128_t arg1 = MakeUInt128(0x555500017fff1111ULL, 0xdeadc0dedeadc0deULL);
  __uint128_t arg2 = MakeUInt128(0x0004000840000000ULL, 0xdeadc0dedeadc0deULL);
  auto [res1, fpsr1] = AsmSqdmulh(arg1, arg2);
  ASSERT_EQ(res1, MakeUInt128(0x000200003fff0000ULL, 0x0000000000000000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeUInt128(0x8000700040010000ULL, 0xdeadc0dedeadc0deULL);
  __uint128_t arg4 = MakeUInt128(0x8000000100040000ULL, 0xdeadc0dedeadc0deULL);
  auto [res2, fpsr2] = AsmSqdmulh(arg3, arg4);
  ASSERT_EQ(res2, MakeUInt128(0x7fff000000020000ULL, 0x0000000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingDoublingMultiplyHighHalf32x4IndexedElem) {
  constexpr auto AsmSqdmulh = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("sqdmulh %0.4s, %2.4s, %3.s[0]");

  __uint128_t arg1 = MakeU32x4(0x20000001UL, 0x00000004UL, 0x7eed0003UL, 0x00000010UL);
  __uint128_t arg2 = MakeU32x4(0x00000008UL, 0xfeedfeedUL, 0xfeedfeedUL, 0xfeedfeedUL);
  auto [res1, fpsr1] = AsmSqdmulh(arg1, arg2);
  ASSERT_EQ(res1, MakeU32x4(0x2UL, 0x0UL, 0x7UL, 0x0UL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeU32x4(0x80000000UL, 0x00000004UL, 0xfeed0003UL, 0x00000010UL);
  __uint128_t arg4 = MakeU32x4(0x80000000UL, 0xfeedfeedUL, 0xfeedfeedUL, 0xfeedfeedUL);
  auto [res2, fpsr2] = AsmSqdmulh(arg3, arg4);
  ASSERT_EQ(res2, MakeU32x4(0x7fffffffUL, 0xfffffffcUL, 0x0112fffdUL, 0xfffffff0UL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingDoublingMultiplyHighHalf32x2IndexedElem) {
  constexpr auto AsmSqdmulh = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("sqdmulh %0.2s, %2.2s, %3.s[0]");

  __uint128_t arg1 = MakeU32x4(0x55555555UL, 0x00000004UL, 0xdeadc0deUL, 0xdeadc0deUL);
  __uint128_t arg2 = MakeU32x4(0x00000004UL, 0xdeadc0deUL, 0xdeadc0deUL, 0xdeadc0deUL);
  auto [res1, fpsr1] = AsmSqdmulh(arg1, arg2);
  ASSERT_EQ(res1, MakeU32x4(0x2UL, 0x0UL, 0x0UL, 0x0UL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeU32x4(0x80000000UL, 0x00000004UL, 0xdeadc0deUL, 0xdeadc0deUL);
  __uint128_t arg4 = MakeU32x4(0x80000000UL, 0xdeadc0deUL, 0xdeadc0deUL, 0xdeadc0deUL);
  auto [res2, fpsr2] = AsmSqdmulh(arg3, arg4);
  ASSERT_EQ(res2, MakeU32x4(0x7fffffffUL, 0xfffffffcUL, 0x0UL, 0x0UL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingDoublingMultiplyHighHalf16x8IndexedElem) {
  constexpr auto AsmSqdmulh = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("sqdmulh %0.8h, %2.8h, %3.h[7]");

  __uint128_t arg1 = MakeUInt128(0x7fff800045670000ULL, 0xfe00780020004001ULL);
  __uint128_t arg2 = MakeUInt128(0xfeedfeedfeedfeedULL, 0x0008feedfeedfeedULL);
  auto [res1, fpsr1] = AsmSqdmulh(arg1, arg2);
  ASSERT_EQ(res1, MakeUInt128(0x0007fff800040000ULL, 0xffff000700020004ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeUInt128(0x7fff800045670000ULL, 0xfe00780020004001ULL);
  __uint128_t arg4 = MakeUInt128(0xfeedfeedfeedfeedULL, 0x8000feedfeedfeedULL);
  auto [res2, fpsr2] = AsmSqdmulh(arg3, arg4);
  ASSERT_EQ(res2, MakeUInt128(0x80017fffba990000ULL, 0x02008800e000bfffULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingDoublingMultiplyHighHalf16x4IndexedElem) {
  constexpr auto AsmSqdmulh = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("sqdmulh %0.4h, %2.4h, %3.h[7]");

  __uint128_t arg1 = MakeUInt128(0x7fff800055550000ULL, 0xdeadc0dedeadc0deULL);
  __uint128_t arg2 = MakeUInt128(0xdeadc0dedeadc0deULL, 0x0004c0dedeadc0deULL);
  auto [res1, fpsr1] = AsmSqdmulh(arg1, arg2);
  ASSERT_EQ(res1, MakeUInt128(0x0003fffc00020000ULL, 0x0000000000000000ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeUInt128(0x7fff800045670000ULL, 0xdeadc0dedeadc0deULL);
  __uint128_t arg4 = MakeUInt128(0xdeadc0dedeadc0deULL, 0x8000c0dedeadc0deULL);
  auto [res2, fpsr2] = AsmSqdmulh(arg3, arg4);
  ASSERT_EQ(res2, MakeUInt128(0x80017fffba990000ULL, 0x0000000000000000ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingDoublingMultiplyHighHalf32x1) {
  constexpr auto AsmSqdmulh = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("sqdmulh %s0, %s2, %s3");

  __uint128_t arg1 = MakeU32x4(0x556789abUL, 0xfeedfeedUL, 0xfeedfeedUL, 0xfeedfeedUL);
  __uint128_t arg2 = MakeU32x4(0x00000004UL, 0xfeedfeedUL, 0xfeedfeedUL, 0xfeedfeedUL);
  auto [res1, fpsr1] = AsmSqdmulh(arg1, arg2);
  ASSERT_EQ(res1, MakeU32x4(0x2UL, 0x0UL, 0x0UL, 0x0ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeU32x4(0x80000000UL, 0xfeedfeedUL, 0xfeedfeedUL, 0xfeedfeedUL);
  __uint128_t arg4 = MakeU32x4(0x80000000UL, 0xfeedfeedUL, 0xfeedfeedUL, 0xfeedfeedUL);
  auto [res2, fpsr2] = AsmSqdmulh(arg3, arg4);
  ASSERT_EQ(res2, MakeU32x4(0x7fffffffUL, 0x0UL, 0x0UL, 0x0UL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingDoublingMultiplyHighHalf16x1) {
  constexpr auto AsmSqdmulh = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("sqdmulh %h0, %h2, %h3");

  __uint128_t arg1 = MakeUInt128(0xfeedfeedfeed5567ULL, 0xfeedfeedfeedfeedULL);
  __uint128_t arg2 = MakeUInt128(0xfeedfeedfeed0004ULL, 0xfeedfeedfeedfeedULL);
  auto [res1, fpsr1] = AsmSqdmulh(arg1, arg2);
  ASSERT_EQ(res1, MakeUInt128(0x0000000000000002ULL, 0x0ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeUInt128(0xfeedfeedfeed8000ULL, 0xfeedfeedfeedfeedULL);
  __uint128_t arg4 = MakeUInt128(0xfeedfeedfeed8000ULL, 0xfeedfeedfeedfeedULL);
  auto [res2, fpsr2] = AsmSqdmulh(arg3, arg4);
  ASSERT_EQ(res2, MakeUInt128(0x0000000000007fffULL, 0x0ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingDoublingMultiplyHighHalf32x1IndexedElem) {
  constexpr auto AsmSqdmulh = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("sqdmulh %s0, %s2, %3.s[2]");

  __uint128_t arg1 = MakeU32x4(0x556789abUL, 0xfeedfeedUL, 0xfeedfeedUL, 0xfeedfeedUL);
  __uint128_t arg2 = MakeU32x4(0xfeedfeedUL, 0xfeedfeedUL, 0x00000004UL, 0xfeedfeedUL);
  auto [res1, fpsr1] = AsmSqdmulh(arg1, arg2);
  ASSERT_EQ(res1, MakeU32x4(0x2UL, 0x0UL, 0x0UL, 0x0UL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeU32x4(0x80000000UL, 0xfeedfeedUL, 0xfeedfeedUL, 0xfeedfeedUL);
  __uint128_t arg4 = MakeU32x4(0xfeedfeedUL, 0xfeedfeedUL, 0x80000000UL, 0xfeedfeedUL);
  auto [res2, fpsr2] = AsmSqdmulh(arg3, arg4);
  ASSERT_EQ(res2, MakeU32x4(0x7fffffffUL, 0x0UL, 0x0UL, 0x0UL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

TEST(Arm64InsnTest, SignedSaturatingDoublingMultiplyHighHalf16x1IndexedElem) {
  constexpr auto AsmSqdmulh = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("sqdmulh %h0, %h2, %3.h[7]");

  __uint128_t arg1 = MakeUInt128(0xfeedfeedfeed5567ULL, 0xfeedfeedfeedfeedULL);
  __uint128_t arg2 = MakeUInt128(0xfeedfeedfeedfeedULL, 0x0004feedfeedfeedULL);
  auto [res1, fpsr1] = AsmSqdmulh(arg1, arg2);
  ASSERT_EQ(res1, MakeUInt128(0x0000000000000002ULL, 0x0ULL));
  ASSERT_FALSE(IsQcBitSet(fpsr1));

  __uint128_t arg3 = MakeUInt128(0xfeedfeedfeed8000ULL, 0xfeedfeedfeedfeedULL);
  __uint128_t arg4 = MakeUInt128(0xfeedfeedfeedfeedULL, 0x8000feedfeedfeedULL);
  auto [res2, fpsr2] = AsmSqdmulh(arg3, arg4);
  ASSERT_EQ(res2, MakeUInt128(0x0000000000007fffULL, 0x0ULL));
  ASSERT_TRUE(IsQcBitSet(fpsr2));
}

class FpcrBitSupport : public testing::TestWithParam<uint64_t> {};

TEST_P(FpcrBitSupport, SupportsBit) {
  uint64_t fpcr1;
  asm("msr fpcr, %x1\n\t"
      "mrs %x0, fpcr"
      : "=r"(fpcr1)
      : "r"(static_cast<uint64_t>(GetParam())));
  ASSERT_EQ(fpcr1, GetParam()) << "Should be able to set then get FPCR bit: " << GetParam();
};

// Note: The exception enablement flags (such as IOE) are not checked, because when tested on actual
// ARM64 device we find that the tests fail either because they cannot be written or are RAZ (read
// as zero).
INSTANTIATE_TEST_SUITE_P(Arm64InsnTest,
                         FpcrBitSupport,
                         testing::Values(kFpcrRModeTieEven,
                                         kFpcrRModeZero,
                                         kFpcrRModeNegInf,
                                         kFpcrRModePosInf,
                                         kFpcrFzBit,
                                         kFpcrDnBit,
                                         0));

class FpsrBitSupport : public testing::TestWithParam<uint64_t> {};

TEST_P(FpsrBitSupport, SupportsBit) {
  uint64_t fpsr1;
  asm("msr fpsr, %1\n\t"
      "mrs %0, fpsr"
      : "=r"(fpsr1)
      : "r"(static_cast<uint64_t>(GetParam())));
  ASSERT_EQ(fpsr1, GetParam()) << "Should be able to set then get FPSR bit";
};

INSTANTIATE_TEST_SUITE_P(Arm64InsnTest,
                         FpsrBitSupport,
                         testing::Values(kFpsrIocBit,
                                         kFpsrDzcBit,
                                         kFpsrOfcBit,
                                         kFpsrUfcBit,
                                         kFpsrIxcBit,
                                         kFpsrIdcBit,
                                         kFpsrQcBit));

TEST(Arm64InsnTest, UnsignedDivide64) {
  auto udiv64 = [](uint64_t num, uint64_t den) {
    uint64_t result;
    asm("udiv %0, %1, %2" : "=r"(result) : "r"(num), "r"(den));
    return result;
  };
  ASSERT_EQ(udiv64(0x8'0000'0000ULL, 2ULL), 0x4'0000'0000ULL) << "Division is 64-bit.";
  ASSERT_EQ(udiv64(123ULL, 0ULL), 0ULL) << "Div by 0 results in 0.";
}

TEST(Arm64InsnTest, AesEncode) {
  __uint128_t arg = MakeUInt128(0x1111'2222'3333'4444ULL, 0x5555'6666'7777'8888ULL);
  __uint128_t key = MakeUInt128(0xaaaa'bbbb'cccc'ddddULL, 0xeeee'ffff'0000'9999ULL);
  __uint128_t res;
  asm("aese %0.16b, %2.16b" : "=w"(res) : "0"(arg), "w"(key));
  ASSERT_EQ(res, MakeUInt128(0x16ea'82ee'eaf5'eeeeULL, 0xf5ea'eeee'ea16'ee82ULL));
}

TEST(Arm64InsnTest, AesMixColumns) {
  __uint128_t arg = MakeUInt128(0x1111'2222'3333'4444ULL, 0x5555'6666'7777'8888ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("aesmc %0.16b, %1.16b")(arg);
  ASSERT_EQ(res, MakeUInt128(0x77114422dd33aa44ULL, 0x3355006692776d88ULL));
}

TEST(Arm64InsnTest, AesDecode) {
  // Check that it's opposite to AesEncode with extra XORs.
  __uint128_t arg = MakeUInt128(0x16ea'82ee'eaf5'eeeeULL, 0xf5ea'eeee'ea16'ee82ULL);
  __uint128_t key = MakeUInt128(0xaaaa'bbbb'cccc'ddddULL, 0xeeee'ffff'0000'9999ULL);
  arg ^= key;
  __uint128_t res;
  asm("aesd %0.16b, %2.16b" : "=w"(res) : "0"(arg), "w"(key));
  ASSERT_EQ(res ^ key, MakeUInt128(0x1111'2222'3333'4444ULL, 0x5555'6666'7777'8888ULL));
}

TEST(Arm64InsnTest, AesInverseMixColumns) {
  __uint128_t arg = MakeUInt128(0x77114422dd33aa44ULL, 0x3355006692776d88ULL);
  __uint128_t res = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("aesimc %0.16b, %1.16b")(arg);
  ASSERT_EQ(res, MakeUInt128(0x1111'2222'3333'4444ULL, 0x5555'6666'7777'8888ULL));
}

}  // namespace
