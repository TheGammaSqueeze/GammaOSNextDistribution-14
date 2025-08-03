/*
 * Copyright (C) 2021 The Android Open Source Project
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

#ifndef BERBERIS_BASE_POINTER_AND_COUNTER_H_
#define BERBERIS_BASE_POINTER_AND_COUNTER_H_

#include <cstdint>

#include "berberis/base/bit_util.h"
#include "berberis/base/checks.h"

namespace berberis {

// Pack aligned pointer and counter into uint64_t for atomic handling.
template <typename T, size_t kAlign = alignof(T)>
struct PointerAndCounter {
#if defined(__LP64__)
  // 64-bit pointers and size_t. Use 48 bits for address and save alignment bits.
  //     [counter][pointer-without-align-bits]
  // bit: 63                                0
  static_assert(sizeof(T*) == 8, "wrong pointer size");
  static const size_t kPointerBits = 48;
  static const size_t kAlignBits = BitUtilLog2(kAlign);
#else
  // 32-bit pointers and size_t. KISS.
  //     [counter][pointer]
  // bit: 63   32  31    0
  static_assert(sizeof(T*) == 4, "wrong pointer size");
  static const size_t kPointerBits = 32;
  static const size_t kAlignBits = 0;
#endif

  static const size_t kRealPointerBits = kPointerBits - kAlignBits;
  static const size_t kCounterBits = 64 - kRealPointerBits;

  static const uint64_t kRealPointerMask = uint64_t(-1) >> kCounterBits;

  static const uint64_t kMaxCounter = uint64_t(1) << kCounterBits;

  // ATTENTION: counter might get truncated!
  static uint64_t PackUnsafe(T* p, uint64_t cnt) {
    uintptr_t ptr = reinterpret_cast<uintptr_t>(p);
    return (static_cast<uint64_t>(ptr) >> kAlignBits) | (cnt << kRealPointerBits);
  }

  static uint64_t Pack(T* p, uint64_t cnt) {
    CHECK_GT(kMaxCounter, cnt);
    return PackUnsafe(p, cnt);
  }

  static T* UnpackPointer(uint64_t v) {
    uintptr_t ptr = static_cast<uintptr_t>((v & kRealPointerMask) << kAlignBits);
    return reinterpret_cast<T*>(ptr);
  }

  static uint64_t UnpackCounter(uint64_t v) { return v >> kRealPointerBits; }
};

}  // namespace berberis

#endif  // BERBERIS_BASE_POINTER_AND_COUNTER_H_
