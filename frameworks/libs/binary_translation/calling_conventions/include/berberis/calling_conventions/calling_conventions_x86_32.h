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

#ifndef BERBERIS_CALLING_CONVENTIONS_CALLING_CONVENTIONS_X86_32_H_
#define BERBERIS_CALLING_CONVENTIONS_CALLING_CONVENTIONS_X86_32_H_

#include "berberis/base/bit_util.h"
#include "berberis/base/logging.h"

namespace berberis::x86_32 {

enum ArgLocationKind {
  kArgLocationNone = 0,
  kArgLocationStack,
  kArgLocationIntOut,  // eax, edx
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

  constexpr ArgLocation GetNextArgLoc(unsigned size, unsigned alignment) {
    // Arguments of all types except packed (__m64 - __m512) are passed on stack.
    // Stack is organized in fourbytes.
    unsigned alignment_in_stack = (alignment < 4u ? 4u : alignment);
    unsigned size_in_stack = AlignUp(size, alignment_in_stack);

    unsigned aligned_stack_offset = AlignUp(stack_offset_, alignment_in_stack);

    ArgLocation loc{kArgLocationStack, aligned_stack_offset};
    stack_offset_ = aligned_stack_offset + size_in_stack;
    return loc;
  }

  constexpr ArgLocation GetIntResLoc(unsigned size) {
    // Fundamental integer type - 1/1, 2/2, 4/4, 8/8.
    CHECK_LE(size, 8u);

    return {kArgLocationIntOut, 0u};
  }

  constexpr ArgLocation GetFpResLoc(unsigned size) {
    // Fundamental floating-point type - 4/4, 8/8, 16/16.
    CHECK_LE(size, 16u);

    return {kArgLocationFp, 0u};
  }

 private:
  unsigned stack_offset_ = 0;
};

}  // namespace berberis::x86_32

#endif  // BERBERIS_CALLING_CONVENTIONS_CALLING_CONVENTIONS_X86_32_H_
