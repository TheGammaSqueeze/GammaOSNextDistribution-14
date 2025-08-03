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

#ifndef BERBERIS_BASE_FOREVER_POOL_H_
#define BERBERIS_BASE_FOREVER_POOL_H_

#include <cstddef>

#include "berberis/base/forever_alloc.h"
#include "berberis/base/lock_free_stack.h"

namespace berberis {

// Memory pool to be used by berberis runtime.
// To be used for small objects of few different types.
// Thread-safe, signal-safe, reentrant.
// Singleton interface (no non-static members).
template <typename T>
class ForeverPool {
 public:
  static T* Alloc() {
    Node* p = g_free_list_.Pop();
    if (p) {
      return reinterpret_cast<T*>(p);
    }
    return reinterpret_cast<T*>(AllocateForever(sizeof(Node), alignof(Node)));
  }

  static void Free(T* p) { g_free_list_.Push(reinterpret_cast<Node*>(p)); }

 private:
  // LockFreeStack requires 'next' data member.
  union Node {
    Node* next;
    T value;
  };

  static LockFreeStack<Node> g_free_list_;

  ForeverPool() = delete;
};

template <typename T>
LockFreeStack<typename ForeverPool<T>::Node> ForeverPool<T>::g_free_list_;

// Allocator for STL containers on top of berberis runtime's memory pool.
// ATTENTION: if allocate/deallocate more than 1 element at once, memory is NOT reused!
template <class T>
class ForeverPoolAllocator {
 public:
  using value_type = T;

  ForeverPoolAllocator() {}

  template <typename U>
  ForeverPoolAllocator(const ForeverPoolAllocator<U>&) {}

  T* allocate(size_t n) {
    if (n == 1) {
      return ForeverPool<T>::Alloc();
    } else {
      // ATTENTION: allocate directly from underlying ForeverAllocator!
      return static_cast<T*>(AllocateForever(n * sizeof(T), alignof(T)));
    }
  }

  void deallocate(T* p, size_t n) {
    if (n == 1) {
      ForeverPool<T>::Free(p);
    } else {
      // ATTENTION: waste!
    }
  }

  bool operator==(const ForeverPoolAllocator<T>&) const { return true; }

  bool operator!=(const ForeverPoolAllocator<T>&) const { return false; }
};

}  // namespace berberis

#endif  // BERBERIS_BASE_FOREVER_POOL_H_
