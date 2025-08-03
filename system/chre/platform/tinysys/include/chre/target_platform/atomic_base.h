/*
 * Copyright (C) 2022 The Android Open Source Project
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

#ifndef CHRE_PLATFORM_TINYSYS_ATOMIC_BASE_H_
#define CHRE_PLATFORM_TINYSYS_ATOMIC_BASE_H_

#include <cinttypes>

#ifdef __cplusplus
extern "C" {
#endif

#include "mtk_atomic.h"

#ifdef __cplusplus
}  // extern "C"
#endif

namespace chre {

/**
 * Base class implementation for the Atomic Bool and Uint32 types.
 */
template <typename T>
class AtomicBase {
 public:
  /**
   * Generic atomic load of a value implemented via a compiler level memory
   * barrier.
   *
   * @return The current value of the data stored.
   */
  inline bool get() const {
    mb();
    return mValue;
  }

  /**
   * Generic atomic store of a value implemented via a compiler level memory
   * barrier.
   *
   * @param value The new value that the data stored is to change to.
   */
  inline void set(T value) {
    mValue = value;
    mb();
  }

 protected:
  volatile T mValue;
};

/**
 * Base class implementation for the Atomic Bool type.
 */
class AtomicBoolBase : public AtomicBase<bool> {
 public:
  /**
   * Atomically swap the stored boolean with a new value.
   *
   * @param desired New value to be assigned to the stored boolean.
   * @return Previous value of the stored boolean.
   */
  bool swap(bool desired) {
    return atomic_swap_byte(reinterpret_cast<volatile uint8_t *>(&mValue),
                            desired);
  }
};

/**
 * Base class implementation for the Atomic Uint32 type.
 */
class AtomicUint32Base : public AtomicBase<uint32_t> {
 public:
  /**
   * Atomically swap the stored 32-bit word with a new value.
   *
   * @param desired New value to be assigned to the stored 32-bit word.
   * @return Previous value of the stored 32-bit word.
   */
  uint32_t swap(uint32_t desired) {
    return atomic_swap(&mValue, desired);
  }

  /**
   * Atomically add a new value to the stored 32-bit word.
   *
   * @param arg Value to be added to the stored word.
   * @return Pre-addition value of the stored word.
   */
  uint32_t add(uint32_t arg) {
    return atomic_add(&mValue, arg);
  }

  /**
   * Atomically subtract a value from the stored 32-bit word.
   *
   * @param arg Value to be subtracted from the stored word.
   * @return Pre-subtraction value of the stored word.
   */
  uint32_t sub(uint32_t arg) {
    return atomic_add(&mValue, ~arg + 1);
  }
};

}  // namespace chre

#endif  // CHRE_PLATFORM_TINYSYS_ATOMIC_BASE_H_