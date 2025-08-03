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

#ifndef CHRE_PLATFORM_ARM_ATOMIC_BASE_H_
#define CHRE_PLATFORM_ARM_ATOMIC_BASE_H_

namespace chre {

namespace atomic {

/**
 * Atomically swap the value of a byte with a new value.
 *
 * @param byte Pointer to a byte whose value is to be swapped out.
 * @param newValue The value replacing the old value in the byte.
 * @return The bytes pre-swap value.
 */
inline bool swapByte(volatile uint8_t *byte, uint32_t newValue) {
  uint32_t prevValue;
  uint32_t storeFailed;

  do {
    asm volatile(
        "ldrexb %0,     [%3] \n"
        "strexb %1, %2, [%3] \n"
        : "=r"(prevValue), "=r"(storeFailed), "=r"(newValue), "=r"(byte)
        : "2"(newValue), "3"(byte)
        : "memory");
  } while (storeFailed);

  return prevValue;
}

/**
 * Atomically swap the value of a 32-bit word with a new value.
 *
 * @param word Pointer to a 32-bit word whose value is to be swapped out.
 * @param newValue The value replacing the old value in the 32-bit word.
 * @return The 32-bit word's pre-swap value.
 */
inline uint32_t swapWord(volatile uint32_t *word, uint32_t newValue) {
  uint32_t prevValue;
  uint32_t storeFailed;

  do {
    asm volatile(
        "ldrex %0,     [%3] \n"
        "strex %1, %2, [%3] \n"
        : "=r"(prevValue), "=r"(storeFailed), "=r"(newValue), "=r"(word)
        : "2"(newValue), "3"(word)
        : "memory");
  } while (storeFailed);

  return prevValue;
}

/**
 * Atomically add a value to a 32-bit word.
 *
 * @param word Pointer to a 32-bit word whose value is to be incremented.
 * @param addend Value that is to be added to the 32-bit word.
 * @return The 32-bit word's value before the addition was performed.
 */
inline uint32_t addToWord(volatile uint32_t *word, uint32_t addend) {
  uint32_t prevValue;
  uint32_t storeFailed;
  uint32_t tmp;

  do {
    asm volatile(
        "ldrex  %0,     [%4] \n"
        "add    %2, %0, %3   \n"
        "strex  %1, %2, [%4] \n"
        : "=r"(prevValue), "=r"(storeFailed), "=r"(tmp), "=r"(addend),
          "=r"(word)
        : "3"(addend), "4"(word)
        : "memory");
  } while (storeFailed);

  return prevValue;
}

/**
 * Atomically subtract a value from a 32-bit word.
 *
 * @param word Pointer to a 32-bit word which is to be decremented.
 * @param arg Value to subtract from the 32-bit word.
 * @return Value of the 32-bit word before the subtraction was performed.
 */
inline uint32_t subFromWord(volatile uint32_t *word, uint32_t arg) {
  uint32_t prevValue;
  uint32_t storeFailed;
  uint32_t tmp;

  do {
    asm volatile(
        "ldrex  %0,     [%4] \n"
        "sub    %2, %0, %3   \n"
        "strex  %1, %2, [%4] \n"
        : "=r"(prevValue), "=r"(storeFailed), "=r"(tmp), "=r"(arg), "=r"(word)
        : "3"(arg), "4"(word)
        : "memory");
  } while (storeFailed);

  return prevValue;
}

}  // namespace atomic

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
    barrier();
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
    barrier();
  }

 protected:
  volatile T mValue;

  /**
   * Forces the compiler to not optimize/re-order memory accesses around the
   * barrier.
   */
  inline void barrier() const {
    asm volatile("" ::: "memory");
  }
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
    return atomic::swapByte(reinterpret_cast<volatile uint8_t *>(&mValue),
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
    return atomic::swapWord(&mValue, desired);
  }

  /**
   * Atomically add a new value to the stored 32-bit word.
   *
   * @param arg Value to be added to the stored word.
   * @return Pre-addition value of the stored word.
   */
  uint32_t add(uint32_t arg) {
    return atomic::addToWord(&mValue, arg);
  }

  /**
   * Atomically subtract a value from the stored 32-bit word.
   *
   * @param arg Value to be subtracted from the stored word.
   * @return Pre-subtraction value of the stored word.
   */
  uint32_t sub(uint32_t arg) {
    return atomic::subFromWord(&mValue, arg);
  }
};

}  // namespace chre

#endif  // CHRE_PLATFORM_ARM_ATOMIC_BASE_H_
