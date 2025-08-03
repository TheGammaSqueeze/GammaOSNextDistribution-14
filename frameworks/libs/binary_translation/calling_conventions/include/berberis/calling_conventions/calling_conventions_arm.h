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

#ifndef BERBERIS_CALLING_CONVENTIONS_CALLING_CONVENTIONS_ARM_H_
#define BERBERIS_CALLING_CONVENTIONS_CALLING_CONVENTIONS_ARM_H_

#include "berberis/base/bit_util.h"
#include "berberis/base/logging.h"

namespace berberis::arm {

enum ArgLocationKind {
  kArgLocationNone = 0,
  kArgLocationStack,
  kArgLocationInt,
  kArgLocationIntAndStack,
  kArgLocationSimd,
};

struct ArgLocation {
  ArgLocationKind kind;
  unsigned offset;  // meaning of offset depends on kind!
};

class CallingConventions {
 public:
  static constexpr unsigned kStackAlignmentBeforeCall = 8;

  CallingConventions() = default;
  CallingConventions(const CallingConventions&) = default;
  CallingConventions(CallingConventions&&) = default;
  // These are used for va_list initialization, where stack may not be aligned.
  CallingConventions(const CallingConventions& base, unsigned stack)
      : int_byte_offset_(base.int_byte_offset_),
        simd_mask_(base.simd_mask_),
        init_stack_offset_(base.init_stack_offset_ + stack),
        stack_offset_(base.stack_offset_ + stack) {}
  CallingConventions(unsigned stack) : init_stack_offset_(stack), stack_offset_(stack) {}
  static constexpr struct StackOnly {
  } kStackOnly;
  CallingConventions(StackOnly, unsigned stack)
      : int_byte_offset_(kMaxIntByteOffset),
        simd_mask_(0),
        init_stack_offset_(stack),
        stack_offset_(stack) {}

  constexpr ArgLocation GetNextIntArgLoc(unsigned size, unsigned alignment) {
    unsigned param_alignment = 0;
    unsigned param_size = 0;

    // Handle under- and over-aligned parameters.
    if (alignment < 4) {
      param_alignment = 4;
      param_size = AlignUp(size, 4);
    } else if (alignment > 8) {
      param_alignment = 8;
      param_size = size;
    } else {
      param_alignment = alignment;
      param_size = size;
    }

    unsigned param_offset = AlignUp(int_byte_offset_, param_alignment);

    if (param_offset + param_size <= kMaxIntByteOffset) {
      // Parameter on int register.
      int_byte_offset_ = param_offset + param_size;

      return {kArgLocationInt, param_offset / 4};
    }

    if (param_offset < kMaxIntByteOffset && stack_offset_ == init_stack_offset_) {
      // Parameter on int register and stack.
      int_byte_offset_ = kMaxIntByteOffset;
      stack_offset_ = param_offset + param_size - kMaxIntByteOffset;

      return {kArgLocationIntAndStack, param_offset / 4};
    }

    // Parameter on stack.
    int_byte_offset_ = kMaxIntByteOffset;

    param_offset = AlignUp(stack_offset_, param_alignment);
    stack_offset_ = param_offset + param_size;

    return {kArgLocationStack, param_offset};
  }

  constexpr ArgLocation GetNextFpArgLoc(unsigned size, unsigned alignment) {
    if (simd_mask_) {
      unsigned param_size_mask = (1u << (size / 4)) - 1;
      for (unsigned index = 0; index < kMaxSimdOffset; index += alignment / 4) {
        unsigned param_mask = (param_size_mask << index);
        if ((simd_mask_ & param_mask) == param_mask) {
          // Parameter is on simd registers.
          simd_mask_ &= ~param_mask;

          return {kArgLocationSimd, index};
        }
      }

      // No available simd registers, this and next params are on stack.
      simd_mask_ = 0;
    }

    // Parameter is on stack.
    unsigned param_offset = AlignUp(stack_offset_, alignment);
    stack_offset_ = param_offset + size;

    return {kArgLocationStack, param_offset};
  }

  constexpr ArgLocation GetIntResLoc(unsigned size) {
    // Fundamental integer or pointer type - 1/1, 2/2, 3/3, 4/4, 8/8, 16/16.
    CHECK_LE(size, 16u);

    // Use r0.
    return {kArgLocationInt, 0u};
  }

  constexpr ArgLocation GetFpResLoc(unsigned size) {
    // Fundamental floating-point type - 2/2, 4/4, 8/8, 16/16.
    CHECK_LE(size, 16u);

    // Use v0.
    return {kArgLocationSimd, 0u};
  }

 private:
  static constexpr unsigned kMaxIntByteOffset = 16u;
  static constexpr unsigned kMaxSimdOffset = 16u;

  unsigned int_byte_offset_ = 0;
  unsigned simd_mask_ = (1u << kMaxSimdOffset) - 1;
  unsigned init_stack_offset_ = 0;
  unsigned stack_offset_ = 0;
};

}  // namespace berberis::arm

#endif  // BERBERIS_CALLING_CONVENTIONS_CALLING_CONVENTIONS_ARM_H_
