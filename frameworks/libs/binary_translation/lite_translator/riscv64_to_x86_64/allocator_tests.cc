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

#include <optional>

#include "allocator.h"

namespace berberis {

namespace {

TEST(AllocatorTest, Allocator) {
  Allocator<x86_64::Assembler::Register> allocator;
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::rbx);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::rsi);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::rdi);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::r8);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::r9);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::r10);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::r11);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::r12);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::r13);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::r14);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::r15);
  EXPECT_EQ(allocator.Alloc(), std::nullopt);
}

TEST(AllocatorTest, AllocTemp) {
  Allocator<x86_64::Assembler::Register> allocator;
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::rbx);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::rsi);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::rdi);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::r8);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::r9);
  EXPECT_EQ(allocator.AllocTemp().value(), x86_64::Assembler::r15);
  EXPECT_EQ(allocator.AllocTemp().value(), x86_64::Assembler::r14);
  EXPECT_EQ(allocator.AllocTemp().value(), x86_64::Assembler::r13);
  EXPECT_EQ(allocator.AllocTemp().value(), x86_64::Assembler::r12);
  EXPECT_EQ(allocator.AllocTemp().value(), x86_64::Assembler::r11);
  EXPECT_EQ(allocator.AllocTemp().value(), x86_64::Assembler::r10);
  EXPECT_EQ(allocator.AllocTemp(), std::nullopt);
  allocator.FreeTemps();
  EXPECT_EQ(allocator.AllocTemp().value(), x86_64::Assembler::r15);
}

TEST(AllocatorTest, SeparateMappedRegsAndTempRegs) {
  Allocator<x86_64::Assembler::Register> allocator;
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::rbx);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::rsi);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::rdi);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::r8);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::r9);
  EXPECT_EQ(allocator.AllocTemp().value(), x86_64::Assembler::r15);
  EXPECT_EQ(allocator.AllocTemp().value(), x86_64::Assembler::r14);
  EXPECT_EQ(allocator.AllocTemp().value(), x86_64::Assembler::r13);
  EXPECT_EQ(allocator.AllocTemp().value(), x86_64::Assembler::r12);
  EXPECT_EQ(allocator.AllocTemp().value(), x86_64::Assembler::r11);
  EXPECT_EQ(allocator.AllocTemp().value(), x86_64::Assembler::r10);
}

TEST(AllocatorTest, SimdAllocator) {
  Allocator<x86_64::Assembler::XMMRegister> allocator;
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::xmm0);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::xmm1);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::xmm2);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::xmm3);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::xmm4);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::xmm5);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::xmm6);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::xmm7);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::xmm8);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::xmm9);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::xmm10);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::xmm11);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::xmm12);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::xmm13);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::xmm14);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::xmm15);
  EXPECT_EQ(allocator.Alloc(), std::nullopt);
}

TEST(AllocatorTest, AllocSimdTemp) {
  Allocator<x86_64::Assembler::XMMRegister> allocator;
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::xmm0);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::xmm1);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::xmm2);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::xmm3);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::xmm4);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::xmm5);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::xmm6);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::xmm7);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::xmm8);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::xmm9);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::xmm10);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::xmm11);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::xmm12);
  EXPECT_EQ(allocator.AllocTemp().value(), x86_64::Assembler::xmm15);
  EXPECT_EQ(allocator.AllocTemp().value(), x86_64::Assembler::xmm14);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::xmm13);
  EXPECT_EQ(allocator.Alloc(), std::nullopt);
  allocator.FreeTemps();
  EXPECT_EQ(allocator.AllocTemp().value(), x86_64::Assembler::xmm15);
}

TEST(ALlocatorTest, SeparateMappedSimdRegsAndTempSimdRegs) {
  Allocator<x86_64::Assembler::XMMRegister> allocator;
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::xmm0);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::xmm1);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::xmm2);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::xmm3);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::xmm4);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::xmm5);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::xmm6);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::xmm7);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::xmm8);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::xmm9);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::xmm10);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::xmm11);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::xmm12);
  EXPECT_EQ(allocator.AllocTemp().value(), x86_64::Assembler::xmm15);
  EXPECT_EQ(allocator.AllocTemp().value(), x86_64::Assembler::xmm14);
  EXPECT_EQ(allocator.Alloc().value(), x86_64::Assembler::xmm13);
  allocator.FreeTemps();
  EXPECT_EQ(allocator.Alloc(), std::nullopt);
}

}  // namespace

}  // namespace berberis
