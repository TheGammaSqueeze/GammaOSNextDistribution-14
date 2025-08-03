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

#ifndef BERBERIS_CALLING_CONVENTIONS_CALLING_CONVENTIONS_X86_64_H_
#define BERBERIS_CALLING_CONVENTIONS_CALLING_CONVENTIONS_X86_64_H_

#include "berberis/base/bit_util.h"
#include "berberis/base/logging.h"

namespace berberis::x86_64 {

enum ArgLocationKind {
  kArgLocationNone = 0,
  kArgLocationStack,
  kArgLocationInt,     // rdi, rsi, rdx, rcx, r8, r9
  kArgLocationIntOut,  // rax, rdx
  kArgLocationSimd,    // xmm0, xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7
  kArgLocationFp,      // st0, st1
};

struct ArgLocation {
  ArgLocationKind kind;
  unsigned offset;  // meaning of offset depends on kind!
};

class CallingConventions {
 public:
  // ATTENTION: if passing __m256 (__m512) on stack, alignment should be 32 (64)!
  static constexpr unsigned kStackAlignmentBeforeCall = 16;

  constexpr ArgLocation GetNextIntArgLoc(unsigned size, unsigned alignment) {
    // Fundamental integer type - 1/1, 2/2, 4/4, 8/8, 16/16.
    CHECK_LE(size, 16u);
    CHECK_EQ(size, alignment);

    unsigned size_in_regs = size > 8 ? 2 : 1;

    if (int_offset_ + size_in_regs <= kMaxIntOffset) {
      ArgLocation loc{kArgLocationInt, int_offset_};
      int_offset_ += size_in_regs;
      return loc;
    }

    return GetNextStackArgLoc(size, alignment);
  }

  constexpr ArgLocation GetNextFpArgLoc(unsigned size, unsigned alignment) {
    // Fundamental floating-point type - 4/4, 8/8, 16/16.
    // TODO: Handle 16/16 if used in a public Android API. Is it SSE or FP?
    CHECK_LE(size, 8u);
    CHECK_EQ(size, alignment);

    if (simd_offset_ < kMaxSimdOffset) {
      // Use next available xmm.
      ArgLocation loc{kArgLocationSimd, simd_offset_};
      ++simd_offset_;
      return loc;
    }

    return GetNextStackArgLoc(size, alignment);
  }

  constexpr ArgLocation GetIntResLoc(unsigned size) {
    // Fundamental integer type - 1/1, 2/2, 4/4, 8/8, 16/16.
    CHECK_LE(size, 16u);

    return {kArgLocationIntOut, 0u};
  }

  constexpr ArgLocation GetFpResLoc(unsigned size) {
    // Fundamental floating-point type - 4/4, 8/8, 16/16.
    // TODO: Handle 16/16 if used in a public Android API. Is it SSE or FP?
    CHECK_LE(size, 8u);

    // Use xmm0.
    return {kArgLocationSimd, 0u};
  }

 private:
  constexpr ArgLocation GetNextStackArgLoc(unsigned size, unsigned /*alignment*/) {
    // TODO(b/136170145): even for 16-byte aligned types, clang aligns on 8???
    // unsigned alignment_in_stack = alignment > 8 ? alignment : 8;
    unsigned alignment_in_stack = 8;
    unsigned size_in_stack = AlignUp(size, alignment_in_stack);

    unsigned aligned_stack_offset = AlignUp(stack_offset_, alignment_in_stack);

    ArgLocation loc{kArgLocationStack, aligned_stack_offset};
    stack_offset_ = aligned_stack_offset + size_in_stack;
    return loc;
  }

  static constexpr unsigned kMaxIntOffset = 6u;
  static constexpr unsigned kMaxSimdOffset = 8u;

  unsigned int_offset_ = 0;
  unsigned simd_offset_ = 0;
  unsigned stack_offset_ = 0;
};

}  // namespace berberis::x86_64

#endif  // BERBERIS_CALLING_CONVENTIONS_CALLING_CONVENTIONS_X86_64_H_
