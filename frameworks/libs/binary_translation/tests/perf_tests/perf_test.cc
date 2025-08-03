/*
 * Copyright (C) 2023 The Android Open Source Project
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

extern "C" void foo();

namespace {

int f0() {
  return 1;
}

int f1() {
  return 2;
}

int f2() {
  return 3;
}

int f3() {
  return 4;
}

}  // namespace

TEST(BerberisPerf, XorLoop) {
  unsigned c = 0xdeadbeef;

  // c "wraps" every 32 iterations.  Since 1,000,000,000 is divisible
  // by 32, we expect to get the original value back.
  for (int i = 0; i < 1000 * 1000 * 1000; i++) {
    c ^= (c << 1);
  }

  EXPECT_EQ(c, 0xdeadbeef);
}

TEST(BerberisPerf, LoopWithCondition) {
  unsigned res = 0xf00dfeed;

  // We want to make sure the loop body is efficiently executed even when loop
  // body is split by an unconditional branch. E.g. this shouldn't result in two
  // translated regions.
  // Note that simple if-else won't suffice. First, it can be replaced by
  // a conditional MOV instruction. Second, one uncoditional branch can be merged
  // with the back branch of the loop. Thus we intentionally use if-else_if-else.
  for (int i = 0; i < 1000 * 1000 * 1000; i++) {
    int mod = i % 4;
    if (mod == 0) {
      res ^= res << 1;
    } else if (mod == 1) {
      res ^= res << 2;
    } else if (mod == 2) {
      res ^= res << 3;
    } else {
      res ^= res << 4;
    }
  }

  EXPECT_EQ(res, 0xf00dfeed);
}

TEST(BerberisPerf, Pi) {
  // Calculate the area of a circle with r = 10000 by checking to see
  // if each point in the 20000 x 20000 square lies within the circle.
  const int N = 10000;
  int c = 0;
  for (int i = -N; i < N; i++) {
    for (int j = -N; j < N; j++) {
      c += ((i * i + j * j) < N * N);
    }
  }
  EXPECT_EQ(c, 314159017);
}

TEST(BerberisPerf, FuncPtr) {
  using FuncPtr = int (*)(void);
  static const FuncPtr fptrs[4] = {f0, f1, f2, f3};

  // Call functions with their pointers 100 million times.
  int a = 0;
  for (int i = 0; i < 100 * 1000 * 1000; i++) {
    // The array index expression below has a period of length 16.
    a += fptrs[(i ^ (i >> 2)) & 3]();
  }
  EXPECT_EQ(a, 250000000);
}

TEST(BerberisPerf, StrlenFruits) {
  // Call strlen about 35 million times while incrementing the pointer
  // to the string.  This way, we get to test different alignments.
  //
  // Dropping "256" below seems to change the characteristics of the
  // test, and the execution time would collapse to 300ms from 4000ms.
  static const char str[256] =
      "banana apple orange strawberry pinapple grape lemon cherry pear melon watermelon peach";
  unsigned result = 0;
  int e = strlen(str);

  for (int i = 0; i < 300 * 1000; i++) {
    for (int j = 0; j != e; j++) {
      result ^= strlen(str + j);
    }
  }
  EXPECT_EQ(result, 0U);
}

TEST(BerberisPerf, StrlenEmpty) {
  // Call strlen with the empty string to measure the overhead of
  // trampoline.
  //
  // We keep assigning to and using "len" to prevent the compiler from
  // optimizing away calls to strlen.
  unsigned len = 0;
  int i;
  for (i = 0; i < 30 * 1000 * 1000; i++) {
    char str[1] = {static_cast<char>(len)};
    len = strlen(str);
  }
  EXPECT_EQ(len, 0U);
}

TEST(BerberisPerf, HighRegPres) {
  // High register pressure test.
  //
  // The generated code on ARM has no spill.  Twelve variables from v0
  // to vb, "i", SP, LR, and PC use up exactly 16 registers.
  unsigned v0 = 0;
  unsigned v1 = 1;
  unsigned v2 = 2;
  unsigned v3 = 3;
  unsigned v4 = 4;
  unsigned v5 = 5;
  unsigned v6 = 6;
  unsigned v7 = 7;
  unsigned v8 = 8;
  unsigned v9 = 9;
  unsigned va = 10;
  unsigned vb = 11;
  volatile unsigned vol = 0;
  for (size_t i = 0; i < 100 * 1000 * 1000; i++) {
    // Disable the auto vectorization by reading a volatile variable.
    i += vol;

    v0 += i ^ 3;
    v1 += i ^ 4;
    v2 += i ^ 5;
    v3 += i ^ 6;
    v4 += i ^ 7;
    v5 += i ^ 8;
    v6 += i ^ 9;
    v7 += i ^ 10;
    v8 += i ^ 11;
    v9 += i ^ 12;
    va += i ^ 13;
    vb += i ^ 14;
  }
  unsigned result = (v0 ^ v1 ^ v2 ^ v3 ^ v4 ^ v5 ^ v6 ^ v7 ^ v8 ^ v9 ^ va ^ vb);
  EXPECT_EQ(result, 0U);
}

TEST(BerberisPerf, EmptyFunc) {
  // Keep calling an empty function.
  for (size_t i = 0; i < 500 * 1000 * 1000; i++) {
    foo();
  }
  EXPECT_EQ(0, 0);
}

TEST(BerberisPerf, ConvertF32I32) {
  static const float vals[] = {0.5, 1.2};
  int sum = 0;
  for (int i = 0; i < 100 * 1000 * 1000; i++) {
    sum += static_cast<int>(vals[i & 1]);
  }
  EXPECT_EQ(sum, 50000000);
}

#if defined __arm__

TEST(BerberisPerf, ReadWriteFPSCR) {
  for (int i = 0; i < 0x1ffffff; i++) {
    // Filter-out bits which implementation does not support and exception bits.
    // If we set exception bits then we get FP-exception (correct behavior), but
    // it's handling dwarfs the execution time by huge margin thus we couldn't do
    // that in perf test.
    uint32_t fpscr_in = i & 0xc01f00;
    uint32_t fpscr_out;
    asm("vmsr fpscr, %1\n"
        "vmrs %0, fpscr\n"
        : "=r"(fpscr_out)
        : "r"(fpscr_in));
    EXPECT_EQ(fpscr_in, fpscr_out);
  }
}

#endif  // defined __arm__