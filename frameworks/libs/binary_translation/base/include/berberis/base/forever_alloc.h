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

#ifndef BERBERIS_BASE_FOREVER_ALLOC_H_
#define BERBERIS_BASE_FOREVER_ALLOC_H_

#include <atomic>
#include <cstdint>

#include "berberis/base/checks.h"
#include "berberis/base/macros.h"
#include "berberis/base/mmap.h"
#include "berberis/base/page_size.h"

namespace berberis {

// Lock-free allocator for memory that is never freed.
// Can be linker-initialized.
// Should only be used for object smaller than memory page.
class ForeverAllocator {
 public:
  constexpr ForeverAllocator() : curr_(0) {}

  template <typename T>
  T* Allocate() {
    static_assert(sizeof(T) < kPageSize, "ForeverAllocator: bad type");
    return reinterpret_cast<T*>(AllocateImpl(sizeof(T), alignof(T)));
  }

  void* Allocate(size_t size, size_t align) {
    CHECK_GT(size, 0);
    CHECK_LT(size, static_cast<size_t>(kPageSize));
    CHECK(IsPowerOf2(align));
    return reinterpret_cast<void*>(AllocateImpl(size, align));
  }

 private:
  uintptr_t AllocatePage() {
    void* ptr = MmapOrDie(kPageSize);
    uintptr_t page = reinterpret_cast<uintptr_t>(ptr);
    CHECK(IsAlignedPageSize(page));

    uintptr_t curr = 0;
    if (!curr_.compare_exchange_strong(curr, page, std::memory_order_acq_rel)) {
      MunmapOrDie(ptr, kPageSize);
      return curr;
    }
    return page;
  }

  uintptr_t AllocateImpl(size_t size, size_t align) {
    uintptr_t curr = curr_.load(std::memory_order_acquire);
    for (;;) {
      if (!curr) {
        curr = AllocatePage();
      }

      uintptr_t res = AlignUp(curr, align);
      uintptr_t next = res + size;
      uintptr_t end = AlignDownPageSize(curr) + kPageSize;

      if (end < next) {
        curr_.compare_exchange_weak(curr, 0, std::memory_order_acquire);
        continue;
      }
      if (end == next) {
        next = 0;
      }
      if (curr_.compare_exchange_weak(curr, next, std::memory_order_acquire)) {
        return res;
      }
    }
  }

  std::atomic_uintptr_t curr_;

  DISALLOW_COPY_AND_ASSIGN(ForeverAllocator);
};

// Allocate from common ForeverAllocator.
// Thread-safe, signal-safe, reentrant.
inline void* AllocateForever(size_t size, size_t align) {
  static ForeverAllocator g_forever_allocator;
  return g_forever_allocator.Allocate(size, align);
}

}  // namespace berberis

#endif  // BERBERIS_BASE_FOREVER_ALLOC_H_
