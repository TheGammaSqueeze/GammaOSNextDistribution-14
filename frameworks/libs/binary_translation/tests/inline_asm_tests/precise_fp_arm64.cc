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

#include "utility.h"

namespace {

TEST(Arm64InsnTest, AddFp32PreciseNaN) {
  // Verify that FADD canonicalizes a qNaN to the default NaN.
  constexpr auto AsmFadd = ASM_INSN_WRAP_FUNC_W_RES_WWC_ARG("fadd %s0, %s1, %s2");
  ASSERT_EQ(AsmFadd(kQuietNaN32, kOneF32, kFpcrDnBit), kDefaultNaN32);
}

TEST(Arm64InsnTest, AddFp64PreciseNaN) {
  // Verify that FADD canonicalizes a qNaN to the default NaN.
  constexpr auto AsmFadd = ASM_INSN_WRAP_FUNC_W_RES_WWC_ARG("fadd %d0, %d1, %d2");
  ASSERT_EQ(AsmFadd(kQuietNaN64, kOneF64, kFpcrDnBit), kDefaultNaN64);
}

TEST(Arm64InsnTest, SubFp32PreciseNaN) {
  // Verify that FSUB canonicalizes a qNaN to the default NaN.
  constexpr auto AsmFsub = ASM_INSN_WRAP_FUNC_W_RES_WWC_ARG("fsub %s0, %s1, %s2");
  ASSERT_EQ(AsmFsub(kQuietNaN32, kOneF32, kFpcrDnBit), kDefaultNaN32);
}

TEST(Arm64InsnTest, SubFp64PreciseNaN) {
  // Verify that FSUB canonicalizes a qNaN to the default NaN.
  constexpr auto AsmFsub = ASM_INSN_WRAP_FUNC_W_RES_WWC_ARG("fsub %d0, %d1, %d2");
  ASSERT_EQ(AsmFsub(kQuietNaN64, kOneF64, kFpcrDnBit), kDefaultNaN64);
}

TEST(Arm64InsnTest, MulFp32PreciseNaN) {
  // Verify that FMUL canonicalizes a qNaN to the default NaN.
  constexpr auto AsmFmul = ASM_INSN_WRAP_FUNC_W_RES_WWC_ARG("fmul %s0, %s1, %s2");
  ASSERT_EQ(AsmFmul(kQuietNaN32, kOneF32, kFpcrDnBit), kDefaultNaN32);
}

TEST(Arm64InsnTest, MulFp64PreciseNaN) {
  // Verify that FMUL canonicalizes a qNaN to the default NaN.
  constexpr auto AsmFmul = ASM_INSN_WRAP_FUNC_W_RES_WWC_ARG("fmul %d0, %d1, %d2");
  ASSERT_EQ(AsmFmul(kQuietNaN64, kOneF64, kFpcrDnBit), kDefaultNaN64);
}

TEST(Arm64InsnTest, DivFp32PreciseNaN) {
  constexpr auto AsmFdiv = ASM_INSN_WRAP_FUNC_W_RES_WWC_ARG("fdiv %s0, %s1, %s2");

  // Verify that FDIV canonicalizes a qNaN to the default NaN.
  __uint128_t arg1 = kDefaultNaN32 | (1U << 31);  // A qNaN
  __uint128_t arg2 = bit_cast<uint32_t>(1.0f);
  ASSERT_EQ(AsmFdiv(arg1, arg2, kFpcrDnBit), kDefaultNaN32);
}

TEST(Arm64InsnTest, DivFp64PreciseNaN) {
  constexpr auto AsmFdiv = ASM_INSN_WRAP_FUNC_W_RES_WWC_ARG("fdiv %d0, %d1, %d2");

  // Verify that FDIV canonicalizes a qNaN to the default NaN.
  __uint128_t arg1 = kDefaultNaN64 | (1ULL << 63);  // A qNaN
  __uint128_t arg2 = bit_cast<uint64_t>(1.0);
  ASSERT_EQ(AsmFdiv(arg1, arg2, kFpcrDnBit), kDefaultNaN64);
}

TEST(Arm64InsnTest, DivFp64x2PreciseNaN) {
  constexpr auto AsmFdiv = ASM_INSN_WRAP_FUNC_W_RES_WWC_ARG("fdiv %0.2d, %1.2d, %2.2d");

  // Verify that FDIV canonicalizes a qNaN to the default NaN.
  __uint128_t arg1 = MakeUInt128(bit_cast<uint64_t>(2.0), kDefaultNaN64 | (1ULL << 63));
  __uint128_t arg2 = MakeF64x2(1.0, 1.0);
  __uint128_t res = AsmFdiv(arg1, arg2, kFpcrDnBit);
  ASSERT_EQ(res, MakeUInt128(bit_cast<uint64_t>(2.0), kDefaultNaN64));
}

TEST(Arm64InsnTest, MaxFp32PreciseNaN) {
  constexpr auto AsmFmax = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fmax %s0, %s1, %s2");
  uint32_t fp_arg_two = bit_cast<uint32_t>(2.0f);
  uint32_t fp_arg_minus_two = bit_cast<uint32_t>(-2.0f);

  ASSERT_EQ(AsmFmax(fp_arg_two, kQuietNaN32), MakeU32x4(kQuietNaN32, 0, 0, 0));
  ASSERT_EQ(AsmFmax(fp_arg_minus_two, kQuietNaN32), MakeU32x4(kQuietNaN32, 0, 0, 0));
  ASSERT_EQ(AsmFmax(kQuietNaN32, fp_arg_two), MakeU32x4(kQuietNaN32, 0, 0, 0));
  ASSERT_EQ(AsmFmax(kQuietNaN32, fp_arg_minus_two), MakeU32x4(kQuietNaN32, 0, 0, 0));
  ASSERT_EQ(AsmFmax(kSignalingNaN32_1, fp_arg_two), MakeU32x4(kQuietNaN32_1, 0, 0, 0));
  ASSERT_EQ(AsmFmax(kSignalingNaN32_1, fp_arg_minus_two), MakeU32x4(kQuietNaN32_1, 0, 0, 0));
  ASSERT_EQ(AsmFmax(kQuietNaN32, kSignalingNaN32_1), MakeU32x4(kQuietNaN32_1, 0, 0, 0));
}

TEST(Arm64InsnTest, MaxFp64PreciseNaN) {
  constexpr auto AsmFmax = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fmax %d0, %d1, %d2");
  uint64_t fp_arg_two = bit_cast<uint64_t>(2.0);
  uint64_t fp_arg_minus_two = bit_cast<uint64_t>(-2.0);

  ASSERT_EQ(AsmFmax(fp_arg_two, kQuietNaN64), MakeUInt128(kQuietNaN64, 0U));
  ASSERT_EQ(AsmFmax(fp_arg_minus_two, kQuietNaN64), MakeUInt128(kQuietNaN64, 0));
  ASSERT_EQ(AsmFmax(kQuietNaN64, fp_arg_two), MakeUInt128(kQuietNaN64, 0));
  ASSERT_EQ(AsmFmax(kQuietNaN64, fp_arg_minus_two), MakeUInt128(kQuietNaN64, 0));
  ASSERT_EQ(AsmFmax(kSignalingNaN64_1, fp_arg_two), MakeUInt128(kQuietNaN64_1, 0));
  ASSERT_EQ(AsmFmax(kSignalingNaN64_1, fp_arg_minus_two), MakeUInt128(kQuietNaN64_1, 0));
  ASSERT_EQ(AsmFmax(kQuietNaN64, kSignalingNaN64_1), MakeUInt128(kQuietNaN64_1, 0));
}

TEST(Arm64InsnTest, MaxNumberFp32PreciseNaN) {
  constexpr auto AsmFmaxnm = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fmaxnm %s0, %s1, %s2");
  uint32_t fp_arg_two = bit_cast<uint32_t>(2.0f);
  uint64_t fp_arg_minus_two = bit_cast<uint64_t>(-2.0);

  ASSERT_EQ(AsmFmaxnm(kSignalingNaN32_1, fp_arg_two), MakeU32x4(kQuietNaN32_1, 0, 0, 0));
  ASSERT_EQ(AsmFmaxnm(fp_arg_two, kSignalingNaN32_1), MakeU32x4(kQuietNaN32_1, 0, 0, 0));
  ASSERT_EQ(AsmFmaxnm(kSignalingNaN32_1, fp_arg_minus_two), MakeU32x4(kQuietNaN32_1, 0, 0, 0));
  ASSERT_EQ(AsmFmaxnm(kQuietNaN32, kSignalingNaN32_1), MakeU32x4(kQuietNaN32_1, 0, 0, 0));
}

TEST(Arm64InsnTest, MaxNumberFp64PreciseNaN) {
  constexpr auto AsmFmaxnm = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fmaxnm %d0, %d1, %d2");
  uint64_t fp_arg_two = bit_cast<uint64_t>(2.0);
  uint64_t fp_arg_minus_two = bit_cast<uint64_t>(-2.0);

  ASSERT_EQ(AsmFmaxnm(kSignalingNaN64_1, fp_arg_two), MakeUInt128(kQuietNaN64_1, 0));
  ASSERT_EQ(AsmFmaxnm(fp_arg_two, kSignalingNaN64_1), MakeUInt128(kQuietNaN64_1, 0));
  ASSERT_EQ(AsmFmaxnm(kSignalingNaN64_1, fp_arg_minus_two), MakeUInt128(kQuietNaN64_1, 0));
  ASSERT_EQ(AsmFmaxnm(kQuietNaN64, kSignalingNaN64_1), MakeUInt128(kQuietNaN64_1, 0));
}

TEST(Arm64InsnTest, MinFp32PreciseNaN) {
  constexpr auto AsmFmin = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fmin %s0, %s1, %s2");
  uint32_t fp_arg_two = bit_cast<uint32_t>(2.0f);
  uint32_t fp_arg_minus_two = bit_cast<uint32_t>(-2.0f);

  ASSERT_EQ(AsmFmin(fp_arg_two, kQuietNaN32), MakeU32x4(kQuietNaN32, 0, 0, 0));
  ASSERT_EQ(AsmFmin(fp_arg_minus_two, kQuietNaN32), MakeU32x4(kQuietNaN32, 0, 0, 0));
  ASSERT_EQ(AsmFmin(kQuietNaN32, fp_arg_two), MakeU32x4(kQuietNaN32, 0, 0, 0));
  ASSERT_EQ(AsmFmin(kQuietNaN32, fp_arg_minus_two), MakeU32x4(kQuietNaN32, 0, 0, 0));
  ASSERT_EQ(AsmFmin(kSignalingNaN32_1, fp_arg_two), MakeU32x4(kQuietNaN32_1, 0, 0, 0));
  ASSERT_EQ(AsmFmin(kSignalingNaN32_1, fp_arg_minus_two), MakeU32x4(kQuietNaN32_1, 0, 0, 0));
  ASSERT_EQ(AsmFmin(kQuietNaN32, kSignalingNaN32_1), MakeU32x4(kQuietNaN32_1, 0, 0, 0));
}

TEST(Arm64InsnTest, MinFp64PreciseNaN) {
  constexpr auto AsmFmin = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fmin %d0, %d1, %d2");
  uint64_t fp_arg_two = bit_cast<uint64_t>(2.0);
  uint64_t fp_arg_minus_two = bit_cast<uint64_t>(-2.0);

  ASSERT_EQ(AsmFmin(fp_arg_two, kQuietNaN64), MakeUInt128(kQuietNaN64, 0U));
  ASSERT_EQ(AsmFmin(fp_arg_minus_two, kQuietNaN64), MakeUInt128(kQuietNaN64, 0));
  ASSERT_EQ(AsmFmin(kQuietNaN64, fp_arg_two), MakeUInt128(kQuietNaN64, 0));
  ASSERT_EQ(AsmFmin(kQuietNaN64, fp_arg_minus_two), MakeUInt128(kQuietNaN64, 0));
  ASSERT_EQ(AsmFmin(kSignalingNaN64_1, fp_arg_two), MakeUInt128(kQuietNaN64_1, 0));
  ASSERT_EQ(AsmFmin(kSignalingNaN64_1, fp_arg_minus_two), MakeUInt128(kQuietNaN64_1, 0));
  ASSERT_EQ(AsmFmin(kQuietNaN64, kSignalingNaN64_1), MakeUInt128(kQuietNaN64_1, 0));
}

TEST(Arm64InsnTest, MinNumberFp32PreciseNaN) {
  constexpr auto AsmFminnm = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fminnm %s0, %s1, %s2");
  uint32_t fp_arg_two = bit_cast<uint32_t>(2.0f);
  uint32_t fp_arg_minus_two = bit_cast<uint32_t>(-2.0f);

  ASSERT_EQ(AsmFminnm(kSignalingNaN32_1, fp_arg_two), MakeU32x4(kQuietNaN32_1, 0, 0, 0));
  ASSERT_EQ(AsmFminnm(fp_arg_two, kSignalingNaN32_1), MakeU32x4(kQuietNaN32_1, 0, 0, 0));
  ASSERT_EQ(AsmFminnm(kSignalingNaN32_1, fp_arg_minus_two), MakeU32x4(kQuietNaN32_1, 0, 0, 0));
  ASSERT_EQ(AsmFminnm(kQuietNaN32, kSignalingNaN32_1), MakeU32x4(kQuietNaN32_1, 0, 0, 0));
}

TEST(Arm64InsnTest, MinNumberFp64PreciseNaN) {
  constexpr auto AsmFminnm = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fminnm %d0, %d1, %d2");
  uint64_t fp_arg_two = bit_cast<uint64_t>(2.0);
  uint64_t fp_arg_minus_two = bit_cast<uint64_t>(-2.0);

  ASSERT_EQ(AsmFminnm(kSignalingNaN64_1, fp_arg_two), MakeUInt128(kQuietNaN64_1, 0));
  ASSERT_EQ(AsmFminnm(fp_arg_two, kSignalingNaN64_1), MakeUInt128(kQuietNaN64_1, 0));
  ASSERT_EQ(AsmFminnm(kSignalingNaN64_1, fp_arg_minus_two), MakeUInt128(kQuietNaN64_1, 0));
  ASSERT_EQ(AsmFminnm(kQuietNaN64, kSignalingNaN64_1), MakeUInt128(kQuietNaN64_1, 0));
}

TEST(Arm64InsnTest, MaxNumberF32x4PreciseNaN) {
  constexpr auto AsmFmaxnm = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fmaxnm %0.4s, %1.4s, %2.4s");
  __uint128_t arg1 = MakeU32x4(
      bit_cast<uint32_t>(1.0f), bit_cast<uint32_t>(-1.0f), kSignalingNaN32_1, kQuietNaN32);
  __uint128_t arg2 = MakeU32x4(
      kSignalingNaN32_1, kQuietNaN32, bit_cast<uint32_t>(1.0f), bit_cast<uint32_t>(-1.0f));
  ASSERT_EQ(
      AsmFmaxnm(arg1, arg2),
      MakeU32x4(
          kQuietNaN32_1, bit_cast<uint32_t>(-1.0f), kQuietNaN32_1, bit_cast<uint32_t>(-1.0f)));
}

TEST(Arm64InsnTest, MaxNumberF64x2PreciseNaN) {
  constexpr auto AsmFmaxnm = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fmaxnm %0.2d, %1.2d, %2.2d");
  __uint128_t arg1 = MakeUInt128(bit_cast<uint64_t>(1.0), kSignalingNaN64_1);
  __uint128_t arg2 = MakeUInt128(kSignalingNaN64_1, bit_cast<uint64_t>(-1.0));
  ASSERT_EQ(AsmFmaxnm(arg1, arg2), MakeUInt128(kQuietNaN64_1, kQuietNaN64_1));
}

TEST(Arm64InsnTest, MinNumberF32x4PreciseNaN) {
  constexpr auto AsmFminnm = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fminnm %0.4s, %1.4s, %2.4s");
  __uint128_t arg1 = MakeU32x4(
      bit_cast<uint32_t>(1.0f), bit_cast<uint32_t>(-1.0f), kSignalingNaN32_1, kQuietNaN32);
  __uint128_t arg2 = MakeU32x4(
      kSignalingNaN32_1, kQuietNaN32, bit_cast<uint32_t>(1.0f), bit_cast<uint32_t>(-1.0f));
  ASSERT_EQ(
      AsmFminnm(arg1, arg2),
      MakeU32x4(
          kQuietNaN32_1, bit_cast<uint32_t>(-1.0f), kQuietNaN32_1, bit_cast<uint32_t>(-1.0f)));
}

TEST(Arm64InsnTest, MinNumberF64x2PreciseNaN) {
  constexpr auto AsmFminnm = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fminnm %0.2d, %1.2d, %2.2d");
  __uint128_t arg1 = MakeUInt128(bit_cast<uint64_t>(1.0), kSignalingNaN64_1);
  __uint128_t arg2 = MakeUInt128(kSignalingNaN64_1, bit_cast<uint64_t>(-1.0));
  ASSERT_EQ(AsmFminnm(arg1, arg2), MakeUInt128(kQuietNaN64_1, kQuietNaN64_1));
}

TEST(Arm64InsnTest, MaxPairwiseNumberF32ScalarPreciseNaN) {
  constexpr auto AsmFmaxnmp = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fmaxnmp %s0, %1.2s");
  __uint128_t arg = MakeF32x4(bit_cast<float>(kSignalingNaN32_1), 2.0f, 7.0f, -0.0f);
  ASSERT_EQ(AsmFmaxnmp(arg), kQuietNaN32_1);
}

TEST(Arm64InsnTest, MaxPairwiseNumberF32x4PreciseNaN) {
  constexpr auto AsmFmaxnmp = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fmaxnmp %0.4s, %1.4s, %2.4s");
  __uint128_t arg1 =
      MakeF32x4(bit_cast<float>(kSignalingNaN32_1), 2.0f, 7.0f, bit_cast<float>(kSignalingNaN32_1));
  __uint128_t arg2 = MakeF32x4(6.0f, 1.0f, -8.0f, 5.0f);
  ASSERT_EQ(AsmFmaxnmp(arg1, arg2),
            MakeF32x4(bit_cast<float>(kQuietNaN32_1), bit_cast<float>(kQuietNaN32_1), 6.0f, 5.0f));
}

TEST(Arm64InsnTest, MinPairwiseNumberF32ScalarPreciseNaN) {
  constexpr auto AsmFminnmp = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fminnmp %s0, %1.2s");
  __uint128_t arg = MakeF32x4(bit_cast<float>(kSignalingNaN32_1), 2.0f, 7.0f, -0.0f);
  ASSERT_EQ(AsmFminnmp(arg), kQuietNaN32_1);
}

TEST(Arm64InsnTest, MinPairwiseNumberF32x4PreciseNaN) {
  constexpr auto AsmFminnmp = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fminnmp %0.4s, %1.4s, %2.4s");
  __uint128_t arg1 =
      MakeF32x4(bit_cast<float>(kSignalingNaN32_1), 2.0f, 7.0f, bit_cast<float>(kSignalingNaN32_1));
  __uint128_t arg2 = MakeF32x4(6.0f, 1.0f, -8.0f, 5.0f);
  ASSERT_EQ(AsmFminnmp(arg1, arg2),
            MakeF32x4(bit_cast<float>(kQuietNaN32_1), bit_cast<float>(kQuietNaN32_1), 1.0f, -8.0f));
}

TEST(Arm64InsnTest, MaxNumberAcrossF32x4PreciseNaN) {
  constexpr auto AsmFmaxnmv = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fmaxnmv %s0, %1.4s");
  __uint128_t arg = MakeF32x4(0.0f, 2.0f, 3.0f, bit_cast<float>(kSignalingNaN32_1));
  ASSERT_EQ(AsmFmaxnmv(arg), bit_cast<uint32_t>(2.0f));
}

TEST(Arm64InsnTest, MinNumberAcrossF32x4PreciseNaN) {
  constexpr auto AsmFminnmv = ASM_INSN_WRAP_FUNC_W_RES_W_ARG("fminnmv %s0, %1.4s");
  __uint128_t arg = MakeF32x4(0.0f, 2.0f, 3.0f, bit_cast<float>(kSignalingNaN32_1));
  ASSERT_EQ(AsmFminnmv(arg), bit_cast<uint32_t>(0.0f));
}

TEST(Arm64InsnTest, AbdF64PreciseNaN) {
  constexpr auto AsmFabd = ASM_INSN_WRAP_FUNC_W_RES_WW_ARG("fabd %d0, %d1, %d2");
  // FABD computes the difference while propagating NaNs and then drops the sign
  // bit.  This means that if the difference is a "negative" NaN, then FABD
  // produces the positive one.  That is, a NaN input doesn't necessarily
  // propagate to the result as is even with the Default NaN mode turned off.
  uint64_t arg1 = kDefaultNaN64 | (1ULL << 63);  // A "negative" qNaN
  uint64_t arg2 = bit_cast<uint32_t>(1.0f);
  ASSERT_EQ(AsmFabd(arg1, arg2), kDefaultNaN64);
}

TEST(Arm64InsnTest, DivFp32FlushToZero) {
  constexpr auto AsmFdiv = ASM_INSN_WRAP_FUNC_W_RES_WWC_ARG("fdiv %s0, %s1, %s2");

  // Verify that 0.0 / denormal yields a NaN.
  __uint128_t arg1 = bit_cast<uint32_t>(0.0f);
  __uint128_t arg2 = 0x80008000ULL;  // denormal
  __uint128_t res = AsmFdiv(arg1, arg2, kFpcrFzBit);
  ASSERT_TRUE(isnan(bit_cast<float>(static_cast<uint32_t>(res))));
  ASSERT_EQ(res >> 32, 0ULL);
}

TEST(Arm64InsnTest, DivFp64FlushToZero) {
  constexpr auto AsmFdiv = ASM_INSN_WRAP_FUNC_W_RES_WWC_ARG("fdiv %d0, %d1, %d2");

  // Verify that 0.0 / denormal yields a NaN.
  __uint128_t arg1 = bit_cast<uint64_t>(0.0);
  __uint128_t arg2 = 0x8000000080000000ULL;  // denormal
  __uint128_t res = AsmFdiv(arg1, arg2, kFpcrFzBit);
  ASSERT_TRUE(isnan(bit_cast<double>(static_cast<uint64_t>(res))));
  ASSERT_EQ(res >> 64, 0ULL);
}

TEST(Arm64InsnTest, AddFp64FpStatusIdcWhenFzOn) {
  __uint128_t arg1 = 0x8000000080000000ULL;  // Denormal
  __uint128_t arg2 = bit_cast<uint64_t>(0.0);

  uint64_t fpcr = kFpcrFzBit;
  uint64_t fpsr;
  __uint128_t res;
  asm("msr fpsr, xzr\n\t"
      "msr fpcr, %x2\n\t"
      "fadd %d0, %d3, %d4\n\t"
      "mrs %1, fpsr"
      : "=w"(res), "=r"(fpsr)
      : "r"(fpcr), "w"(arg1), "w"(arg2));
  ASSERT_EQ(fpsr, kFpsrIdcBit);
}

TEST(Arm64InsnTest, AddFp64FpStatusIoc) {
  constexpr auto AsmFadd = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("fadd %d0, %d2, %d3");

  uint64_t fp_arg1 = 0x7ff4000000000000ULL;  // Nan
  uint64_t fp_arg2 = kOneF64;
  auto [res, fpsr] = AsmFadd(fp_arg1, fp_arg2);
  ASSERT_EQ(res, MakeUInt128(0x7ffc000000000000ULL, 0x0000000000000000ULL));
  ASSERT_EQ(fpsr, kFpsrIocBit);
}

TEST(Arm64InsnTest, AddFp64FpStatusIxc) {
  constexpr auto AsmFadd = ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG("fadd %s0, %s2, %s3");

  uint32_t fp_arg1 = 0x97876b0f;  // 8.7511959e-25
  uint32_t fp_arg2 = 0x904e5f47;  // -4.0699736e-29

  auto [res, fpsr] = AsmFadd(fp_arg1, fp_arg2);
  ASSERT_EQ(fpsr, kFpsrIxcBit);
  ASSERT_EQ(res, MakeUInt128(0x0000000097876cacULL, 0x0000000000000000ULL));
}

TEST(Arm64InsnTest, AddFp64FpStatusDzc) {
  constexpr auto AsmFDiv = ASM_INSN_WRAP_FUNC_WQ_RES_WW0_ARG("fdiv %d0, %d2, %d3");
  auto num = MakeUInt128(bit_cast<uint64_t>(2.0), 0ULL);
  auto den = MakeUInt128(bit_cast<uint64_t>(0.0), 0ULL);

  auto [res, fpsr] = AsmFDiv(num, den, 0);
  ASSERT_EQ(fpsr, kFpsrDzcBit);
}

TEST(Arm64InsnTest, AddFp64FpStatusOfe) {
  __uint128_t res;
  uint64_t fpsr;
  asm("msr fpsr, xzr\n\t"
      "msr fpcr, xzr\n\t"
      "fmul %d0, %d2, %d2\n\t"
      "mrs %1, fpsr"
      : "=w"(res), "=r"(fpsr)
      : "w"(std::numeric_limits<double>::max()));
  ASSERT_EQ(fpsr, kFpsrOfcBit | kFpsrIxcBit) << "OFE should be set upon overflow (as well as IXC).";
}

TEST(Arm64InsnTest, AddFp64FpStatusUfe) {
  __uint128_t res;
  uint64_t fpsr;
  asm("msr fpsr, xzr\n\t"
      "msr fpcr, xzr\n\t"
      "fdiv %d0, %d2, %d3\n\t"
      "mrs %1, fpsr"
      : "=w"(res), "=r"(fpsr)
      : "w"(std::numeric_limits<double>::min()), "w"(std::numeric_limits<double>::max()));
  ASSERT_EQ(fpsr, kFpsrUfcBit | kFpsrIxcBit)
      << "UFE should be set upon underflow (as well as IXC).";
}

}  // namespace
