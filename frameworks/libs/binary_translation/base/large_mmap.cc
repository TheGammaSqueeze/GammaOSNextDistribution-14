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

#include "berberis/base/large_mmap.h"

#include <atomic>

#include "berberis/base/mmap.h"

namespace berberis {

#if defined(__LP64__)

// Some apps have a bug of not supporting pointer difference >16gb. (http://b/167572400)
// Since translator allocates some additional address space it is more likely for app
// to get in the situation of having pointers more than 16g apart.
//
// The solution is to move large long-term translator allocations away from the current mmap
// area, leaving close addresses for the guest allocations, so that memory allocation footprint
// is closer to what app gets in the native environment.
//
// We implement this by allocating a huge buffer for translator allocations. To ensure enough space
// between the buffer and the current mmap area, we allocate an even larger buffer and then free a
// part of it. Since on modern Linux mmap moves top-to-down (https://lwn.net/Articles/91829) the
// spacing area (that needs to be freed) is at the higher addresses.
//
// ATTENTION: If guest allocation (in another thread) takes place while the buffer is allocated but
// the spacing is not yet freed, the allocation will go too far away from the current mmap area,
// manifesting the bug. To avoid that, we allocate the buffer on init and never reallocate it.
// When the buffer is exhausted, translator allocates with mmap directly.
//
// Note that we cannot simply allocate a huge buffer at init to achieve the same result for
// following reasons:
// 1. there can be small mapping gaps, that will later be taken by guest allocations.
// 2. Some mappings can be unmapped later also allowing new guest allocations in their place.

namespace {

// ATTENTION: buffer allocation is not atomic! To make it atomic, use PointerAndCounter!
std::atomic<uint8_t*> g_buffer = nullptr;
uint8_t* g_buffer_end = nullptr;

}  // namespace

void InitLargeMmap() {
  constexpr size_t kBufferSize = size_t(1) << 34;   // 16gb
  constexpr size_t kSpacingSize = size_t(1) << 35;  // 32gb

  // As explained above we expect mmap to work top-to-down, so spacing is at the higher addresses.
  auto* ptr = static_cast<uint8_t*>(MmapImplOrDie(
      {.size = kBufferSize + kSpacingSize, .flags = MAP_PRIVATE | MAP_ANONYMOUS | MAP_NORESERVE}));
  MunmapOrDie(ptr + kBufferSize, kSpacingSize);

  g_buffer.store(ptr);
  g_buffer_end = ptr + kBufferSize;
}

void* LargeMmapImplOrDie(MmapImplArgs args) {
  if (args.addr == nullptr) {
    CHECK_EQ(0, args.flags & MAP_FIXED);
    size_t size = AlignUpPageSize(args.size);

    uint8_t* curr = g_buffer.load(std::memory_order_relaxed);
    for (;;) {
      uint8_t* next = curr + size;
      if (next > g_buffer_end) {
        break;
      }

      // Updates curr!
      if (g_buffer.compare_exchange_weak(curr, next, std::memory_order_release)) {
        args.addr = curr;
        args.flags |= MAP_FIXED;
        break;
      }
    }
  }

  return MmapImplOrDie(args);
}

#else

void InitLargeMmap() {}

void* LargeMmapImplOrDie(MmapImplArgs args) {
  return MmapImplOrDie(args);
}

#endif

void* LargeMmapOrDie(size_t size) {
  return LargeMmapImplOrDie({.size = size});
}

}  // namespace berberis
