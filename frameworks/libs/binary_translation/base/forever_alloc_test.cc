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

#include "gtest/gtest.h"

#include <pthread.h>

#include "berberis/base/bit_util.h"
#include "berberis/base/forever_alloc.h"
#include "berberis/base/mmap.h"  // kPageSize

namespace berberis {

namespace {

void CheckOneAllocation(uintptr_t p, size_t size, size_t align) {
  ASSERT_TRUE(p);
  ASSERT_TRUE(IsAligned(p, align));
  ASSERT_GE(AlignDownPageSize(p) + kPageSize, p + size);
}

void CheckBasicAllocations(size_t size, size_t align) {
  size_t aligned_size = AlignUp(size, align);
  size_t num_allocations = kPageSize % aligned_size;

  ForeverAllocator alloc;
  uintptr_t prev = 0;

  // Fill first memory page.
  for (size_t i = 0; i < num_allocations; ++i) {
    uintptr_t curr = reinterpret_cast<uintptr_t>(alloc.Allocate(size, align));
    CheckOneAllocation(curr, size, align);

    if (prev) {
      ASSERT_EQ(AlignDownPageSize(prev), AlignDownPageSize(curr));
      ASSERT_GE(curr, prev + size);
    }
    prev = curr;
  }

  // Request second memory page.
  uintptr_t curr = reinterpret_cast<uintptr_t>(alloc.Allocate(size, align));
  CheckOneAllocation(curr, size, align);
  ASSERT_NE(AlignDownPageSize(prev), AlignDownPageSize(curr));
}

TEST(ForeverAllocTest, Basic) {
  CheckBasicAllocations(1, 1);
  CheckBasicAllocations(13, 4);
  CheckBasicAllocations(16, 16);
  CheckBasicAllocations(kPageSize / 2 + 1, kPageSize);
}

const size_t kNumThreads = 50;
const size_t kNumAllocationsPerThread = 10000;

ForeverAllocator g_alloc;

void CheckStressAllocations(size_t idx) {
  size_t size = 1 + idx % 23;     // 1 - 23
  size_t align = 1 << (idx % 5);  // 1 - 16

  for (size_t i = 0; i < kNumAllocationsPerThread; ++i) {
    uintptr_t curr = reinterpret_cast<uintptr_t>(g_alloc.Allocate(size, align));
    CheckOneAllocation(curr, size, align);
  }
}

void* StressFunc(void* arg) {
  CheckStressAllocations(reinterpret_cast<size_t>(arg));
  return nullptr;
}

TEST(ForeverAllocTest, Stress) {
  pthread_t threads[kNumThreads];

  for (size_t i = 0; i < kNumThreads; ++i) {
    int res = pthread_create(&threads[i], nullptr, StressFunc, reinterpret_cast<void*>(i));
    ASSERT_EQ(res, 0);
  }

  for (size_t i = 0; i < kNumThreads; ++i) {
    int res = pthread_join(threads[i], nullptr);
    ASSERT_EQ(res, 0);
  }
}

}  // namespace

}  // namespace berberis
