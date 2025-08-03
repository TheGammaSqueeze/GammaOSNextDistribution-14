/*
 * Copyright (C) 2016 The Android Open Source Project
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

#ifndef BERBERIS_BASE_LOCK_FREE_STACK_H_
#define BERBERIS_BASE_LOCK_FREE_STACK_H_

#include <atomic>
#include <cstdint>

#include "berberis/base/macros.h"  // DISALLOW_COPY_AND_ASSIGN
#include "berberis/base/pointer_and_counter.h"

namespace berberis {

// Lock-free stack.
// Can be linker-initialized.
// Uses 32/16 bits ABA counter on 32/64-bit platforms.
// Memory passed to Push should never be reused.
// Type T should have T* next field.
template <typename T>
class LockFreeStack {
 public:
  constexpr LockFreeStack() : head_(0) {}

  bool Empty() const {
    return PointerAndCounter<T>::UnpackPointer(head_.load(std::memory_order_relaxed)) == nullptr;
  }

  void PushRange(T* p, T* l) {
    // TODO(b/232598137): CHECK p, l, p(->next)* == l?
    uint64_t cmp = head_.load(std::memory_order_relaxed);
    while (true) {
      uint64_t cnt = PointerAndCounter<T>::UnpackCounter(cmp) + 1;
      uint64_t xch = PointerAndCounter<T>::PackUnsafe(p, cnt);
      l->next = PointerAndCounter<T>::UnpackPointer(cmp);
      // Updates cmp!
      if (head_.compare_exchange_weak(cmp, xch, std::memory_order_release)) {
        break;
      }
    }
  }

  void Push(T* p) { PushRange(p, p); }

  T* Pop() {
    uint64_t cmp = head_.load(std::memory_order_acquire);
    while (true) {
      T* curr = PointerAndCounter<T>::UnpackPointer(cmp);
      if (!curr) {
        return nullptr;
      }
      T* next = curr->next;
      uint64_t cnt = PointerAndCounter<T>::UnpackCounter(cmp);
      uint64_t xch = PointerAndCounter<T>::PackUnsafe(next, cnt);
      // Updates cmp!
      if (head_.compare_exchange_weak(cmp, xch, std::memory_order_acquire)) {
        return curr;
      }
    }
  }

  T* TopForTesting() { return PointerAndCounter<T>::UnpackPointer(head_); }

 private:
  std::atomic_uint_fast64_t head_;

  DISALLOW_COPY_AND_ASSIGN(LockFreeStack);
};

}  // namespace berberis

#endif  // BERBERIS_BASE_LOCK_FREE_STACK_H_
