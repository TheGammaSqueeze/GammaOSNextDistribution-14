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

#ifndef ART_RUNTIME_BASE_ATOMIC_PAIR_H_
#define ART_RUNTIME_BASE_ATOMIC_PAIR_H_

#include <android-base/logging.h>

#include <atomic>
#include <type_traits>

#include "base/macros.h"

namespace art HIDDEN {

// Implement 16-byte atomic pair using the seq-lock synchronization algorithm.
// This is currently only used for DexCache.
//
// This uses top 4-bytes of the key as version counter and lock bit,
// which means the stored pair key can not use those bytes.
//
// This allows us to read the cache without exclusive access to the cache line.
//
// The 8-byte atomic pair uses the normal single-instruction implementation.
//
static constexpr uint64_t kSeqMask = (0xFFFFFFFFull << 32);
static constexpr uint64_t kSeqLock = (0x80000000ull << 32);
static constexpr uint64_t kSeqIncr = (0x00000001ull << 32);

// std::pair<> is not trivially copyable and as such it is unsuitable for atomic operations.
template <typename IntType>
struct PACKED(2 * sizeof(IntType)) AtomicPair {
  static_assert(std::is_integral_v<IntType>);

  AtomicPair(IntType f, IntType s) : key(f), val(s) {}

  IntType key;
  IntType val;
};

template <typename IntType>
ALWAYS_INLINE static inline AtomicPair<IntType> AtomicPairLoadAcquire(AtomicPair<IntType>* pair) {
  static_assert(std::is_trivially_copyable<AtomicPair<IntType>>::value);
  auto* target = reinterpret_cast<std::atomic<AtomicPair<IntType>>*>(pair);
  return target->load(std::memory_order_acquire);
}

template <typename IntType>
ALWAYS_INLINE static inline void AtomicPairStoreRelease(AtomicPair<IntType>* pair,
                                                        AtomicPair<IntType> value) {
  static_assert(std::is_trivially_copyable<AtomicPair<IntType>>::value);
  auto* target = reinterpret_cast<std::atomic<AtomicPair<IntType>>*>(pair);
  target->store(value, std::memory_order_release);
}

ALWAYS_INLINE static inline AtomicPair<uint64_t> AtomicPairLoadAcquire(AtomicPair<uint64_t>* pair) {
  auto* key_ptr = reinterpret_cast<std::atomic_uint64_t*>(&pair->key);
  auto* val_ptr = reinterpret_cast<std::atomic_uint64_t*>(&pair->val);
  while (true) {
    uint64_t key0 = key_ptr->load(std::memory_order_acquire);
    uint64_t val = val_ptr->load(std::memory_order_acquire);
    uint64_t key1 = key_ptr->load(std::memory_order_relaxed);
    uint64_t key = key0 & ~kSeqMask;
    if (LIKELY((key0 & kSeqLock) == 0 && key0 == key1)) {
      return {key, val};
    }
  }
}

ALWAYS_INLINE static inline void AtomicPairStoreRelease(AtomicPair<uint64_t>* pair,
                                                        AtomicPair<uint64_t> value) {
  DCHECK((value.key & kSeqMask) == 0) << "Key=0x" << std::hex << value.key;
  auto* key_ptr = reinterpret_cast<std::atomic_uint64_t*>(&pair->key);
  auto* val_ptr = reinterpret_cast<std::atomic_uint64_t*>(&pair->val);
  uint64_t key = key_ptr->load(std::memory_order_relaxed);
  do {
    key &= ~kSeqLock;  // Ensure that the CAS below fails if the lock bit is already set.
  } while (!key_ptr->compare_exchange_weak(key, key | kSeqLock));
  key = (((key & kSeqMask) + kSeqIncr) & ~kSeqLock) | (value.key & ~kSeqMask);
  val_ptr->store(value.val, std::memory_order_release);
  key_ptr->store(key, std::memory_order_release);
}

}  // namespace art

#endif  // ART_RUNTIME_BASE_ATOMIC_PAIR_H_
