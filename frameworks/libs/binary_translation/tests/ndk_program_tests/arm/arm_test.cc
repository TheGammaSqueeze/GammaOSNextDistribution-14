/*
 * Copyright (C) 2014 The Android Open Source Project
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

#include <link.h>

#include <cstdint>
#include <cstdio>

// Dummy function for LinkerExidx test.
int arm_tests() {
  return 0;
}

uintptr_t read_exidx_func(int32_t* entry) {
  int32_t offset = *entry;
  // sign-extend from int31 to int32.
  if ((offset & 0x40000000) != 0) {
    offset += -0x7fffffff - 1;
  }
  return reinterpret_cast<uintptr_t>(entry) + offset;
}

TEST(ARM, LinkerExidx) {
  int count;
  int32_t* entries;
  entries = reinterpret_cast<int32_t*>(
      dl_unwind_find_exidx(reinterpret_cast<uintptr_t>(read_exidx_func), &count));
  ASSERT_TRUE(entries);
  ASSERT_GT(count, 0);
  // Sanity checks
  uintptr_t func = reinterpret_cast<uintptr_t>(arm_tests);
  bool found = false;
  for (int i = 0; i < count; i++) {
    // Entries must have 31 bit set to zero.
    ASSERT_GE(entries[2 * i], 0);
    uintptr_t exidx_func = read_exidx_func(&entries[2 * i]);
    // If our function is compiled to thumb, exception table contains our
    // address - 1.
    if (func == exidx_func || func == exidx_func + 1) {
      found = true;
    }
    // Entries must be sorted. Some addresses may appear twice if function
    // is compiled to arm.
    if (i > 0) {
      ASSERT_GE(exidx_func, read_exidx_func(&entries[2 * (i - 1)]));
    }
  }
  ASSERT_TRUE(found);
}

TEST(ARM, LSL0) {
  int result;

  asm volatile(
      "mov %0, #-2\n"
      "mov r1, #0\n"
      "lsl %0, r1\n"
      : "=r"(result)::"r1");
  EXPECT_EQ(result, -2);

  asm volatile(
      "mov r1, #-2\n"
      "mov r2, #0\n"
      "lsl %0, r1, r2\n"
      : "=r"(result)::"r1", "r2");
  EXPECT_EQ(result, -2);

  asm volatile(
      "mov r1, #-2\n"
      "lsl %0, r1, #0\n"
      : "=r"(result)::"r1");
  EXPECT_EQ(result, -2);
}

TEST(ARM, LSL32) {
  int result;
  int flag;

  asm volatile(
      "mov %0, #-1\n"
      "mov r1, #32\n"
      "lsls %0, r1\n"
      "sbc %1, r1, r1\n"
      : "=r"(result), "=r"(flag)::"r1");
  EXPECT_EQ(result, 0);
  EXPECT_EQ(flag, 0);

  asm volatile(
      "mov r1, #-1\n"
      "mov r2, #32\n"
      "lsls %0, r1, r2\n"
      "sbc %1, r1, r1\n"
      : "=r"(result), "=r"(flag)::"r1", "r2");
  EXPECT_EQ(result, 0);
  EXPECT_EQ(flag, 0);
}

TEST(ARM, LSL33) {
  int result;
  int flag;

  asm volatile(
      "mov %0, #-1\n"
      "mov r1, #33\n"
      "lsls %0, r1\n"
      "sbc %1, r1, r1\n"
      : "=r"(result), "=r"(flag)::"r1");
  EXPECT_EQ(result, 0);
  EXPECT_EQ(flag, -1);

  asm volatile(
      "mov r1, #-1\n"
      "mov r2, #33\n"
      "lsls %0, r1, r2\n"
      "sbc %1, r1, r1\n"
      : "=r"(result), "=r"(flag)::"r1", "r2");
  EXPECT_EQ(result, 0);
  EXPECT_EQ(flag, -1);
}

TEST(ARM, LSL256) {
  int result;

  asm volatile(
      "mov %0, #-2\n"
      "mov r1, #256\n"
      "lsl %0, r1\n"
      : "=r"(result)::"r1");
  EXPECT_EQ(result, -2);

  asm volatile(
      "mov r1, #-2\n"
      "mov r2, #256\n"
      "lsl %0, r1, r2\n"
      : "=r"(result)::"r1", "r2");
  EXPECT_EQ(result, -2);
}

TEST(ARM, LSR0) {
  int result;

  asm volatile(
      "mov %0, #-2\n"
      "mov r1, #0\n"
      "lsr %0, r1\n"
      : "=r"(result)::"r1");
  EXPECT_EQ(result, -2);

  asm volatile(
      "mov r1, #-2\n"
      "mov r2, #0\n"
      "lsr %0, r1, r2\n"
      : "=r"(result)::"r1", "r2");
  EXPECT_EQ(result, -2);
}

TEST(ARM, LSR32) {
  int result;
  int flag;

  asm volatile(
      "mov %0, #-1\n"
      "mov r1, #32\n"
      "lsrs %0, r1\n"
      "sbc %1, r1, r1\n"
      : "=r"(result), "=r"(flag)::"r1");
  EXPECT_EQ(result, 0);
  EXPECT_EQ(flag, 0);

  asm volatile(
      "mov r1, #-1\n"
      "mov r2, #32\n"
      "lsrs %0, r1, r2\n"
      "sbc %1, r1, r1\n"
      : "=r"(result), "=r"(flag)::"r1", "r2");
  EXPECT_EQ(result, 0);
  EXPECT_EQ(flag, 0);

  asm volatile(
      "mov r1, #-1\n"
      "lsrs %0, r1, #32\n"
      "sbc %1, r1, r1\n"
      : "=r"(result), "=r"(flag)::"r1");
  EXPECT_EQ(result, 0);
  EXPECT_EQ(flag, 0);
}

TEST(ARM, LSR33) {
  int result;
  int flag;

  asm volatile(
      "mov %0, #-1\n"
      "mov r1, #33\n"
      "lsrs %0, r1\n"
      "sbc %1, r1, r1\n"
      : "=r"(result), "=r"(flag)::"r1");
  EXPECT_EQ(result, 0);
  EXPECT_EQ(flag, -1);

  asm volatile(
      "mov r1, #-1\n"
      "mov r2, #33\n"
      "lsrs %0, r1, r2\n"
      "sbc %1, r1, r1\n"
      : "=r"(result), "=r"(flag)::"r1", "r2");
  EXPECT_EQ(result, 0);
  EXPECT_EQ(flag, -1);
}

TEST(ARM, LSR256) {
  int result;

  asm volatile(
      "mov %0, #-2\n"
      "mov r1, #256\n"
      "lsr %0, r1\n"
      : "=r"(result)::"r1");
  EXPECT_EQ(result, -2);

  asm volatile(
      "mov r1, #-2\n"
      "mov r2, #256\n"
      "lsr %0, r1, r2\n"
      : "=r"(result)::"r1", "r2");
  EXPECT_EQ(result, -2);
}

TEST(ARM, ASR0) {
  int result;

  asm volatile(
      "mov %0, #-2\n"
      "mov r1, #0\n"
      "asr %0, r1\n"
      : "=r"(result)::"r1");
  EXPECT_EQ(result, -2);

  asm volatile(
      "mov r1, #-2\n"
      "mov r2, #0\n"
      "asr %0, r1, r2\n"
      : "=r"(result)::"r1", "r2");
  EXPECT_EQ(result, -2);
}

TEST(ARM, ASR32) {
  int result;
  int flag;

  asm volatile(
      "mov %0, #-1\n"
      "mov r1, #32\n"
      "asrs %0, r1\n"
      "sbc %1, r1, r1\n"
      : "=r"(result), "=r"(flag)::"r1");
  EXPECT_EQ(result, -1);
  EXPECT_EQ(flag, 0);

  asm volatile(
      "mov r1, #-1\n"
      "mov r2, #32\n"
      "asrs %0, r1, r2\n"
      "sbc %1, r1, r1\n"
      : "=r"(result), "=r"(flag)::"r1", "r2");
  EXPECT_EQ(result, -1);
  EXPECT_EQ(flag, 0);

  asm volatile(
      "mov r1, #-1\n"
      "asrs %0, r1, #32\n"
      "sbc %1, r1, r1\n"
      : "=r"(result), "=r"(flag)::"r1");
  EXPECT_EQ(result, -1);
  EXPECT_EQ(flag, 0);
}

TEST(ARM, ASR33) {
  int result;
  int flag;

  asm volatile(
      "mov %0, #-1\n"
      "mov r1, #33\n"
      "asrs %0, r1\n"
      "sbc %1, r1, r1\n"
      : "=r"(result), "=r"(flag)::"r1");
  EXPECT_EQ(result, -1);
  EXPECT_EQ(flag, 0);

  asm volatile(
      "mov r1, #-1\n"
      "mov r2, #33\n"
      "asrs %0, r1, r2\n"
      "sbc %1, r1, r1\n"
      : "=r"(result), "=r"(flag)::"r1", "r2");
  EXPECT_EQ(result, -1);
  EXPECT_EQ(flag, 0);
}

TEST(ARM, ASR256) {
  int result;

  asm volatile(
      "mov %0, #-2\n"
      "mov r1, #256\n"
      "asr %0, r1\n"
      : "=r"(result)::"r1");
  EXPECT_EQ(result, -2);

  asm volatile(
      "mov r1, #-2\n"
      "mov r2, #256\n"
      "asr %0, r1, r2\n"
      : "=r"(result)::"r1", "r2");
  EXPECT_EQ(result, -2);
}
