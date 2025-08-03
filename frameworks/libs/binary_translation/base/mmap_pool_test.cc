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

#include "gtest/gtest.h"

#include <cstddef>
#include <thread>

#include "berberis/base/mmap.h"
#include "berberis/base/mmap_pool.h"
#include "berberis/base/page_size.h"

namespace berberis {

class MmapPoolTest : public ::testing::Test {
 protected:
  static constexpr size_t kBlockSize = kMaxPageSize;
  static constexpr size_t kSizeLimit = 2 * kBlockSize;
  using Pool = MmapPool<kBlockSize, kSizeLimit>;

  MmapPoolTest() = default;

  size_t ListLength() {
    size_t length = 0;
    Pool::Node* node = Pool::g_nodes_with_available_blocks_.TopForTesting();
    while (node) {
      length++;
      node = node->next;
    }
    return length;
  }

  virtual void SetUp() {
    // Empty the global pool, possibly populated by other tests.
    while (ListLength() > 0) {
      MunmapOrDie(Pool::Alloc(), kBlockSize);
    }
  }
};

namespace {

TEST_F(MmapPoolTest, Smoke) {
  char* p1 = static_cast<char*>(Pool::Alloc());
  ASSERT_TRUE(p1);
  p1[kBlockSize - 1] = 'a';
  EXPECT_EQ(ListLength(), 0u);

  char* p2 = static_cast<char*>(Pool::Alloc());
  ASSERT_TRUE(p2);
  p2[kBlockSize - 1] = 'b';
  EXPECT_EQ(ListLength(), 0u);

  EXPECT_NE(p1, p2);

  char* p3 = static_cast<char*>(Pool::Alloc());
  ASSERT_TRUE(p3);
  p3[kBlockSize - 1] = 'c';
  EXPECT_EQ(ListLength(), 0u);

  Pool::Free(p1);
  EXPECT_EQ(ListLength(), 1u);
  p1[kBlockSize - 1] = 'A';

  Pool::Free(p2);
  EXPECT_EQ(ListLength(), 2u);
  p2[kBlockSize - 1] = 'B';

  Pool::Free(p3);
  // Length doesn't change!
  EXPECT_EQ(ListLength(), 2u);
  // The block is unmapped.
  EXPECT_DEATH(p3[kBlockSize - 1] = 'C', "");
}

TEST_F(MmapPoolTest, Stress) {
  constexpr size_t kNumThreads = 4;
  std::thread threads[kNumThreads];

  for (size_t i = 0; i < kNumThreads; i++) {
    threads[i] = std::thread([]() {
      for (int c = 0; c < 1024 * 1024; c++) {
        char* block = static_cast<char*>(Pool::Alloc());
        block[kBlockSize - 1] = 'a';
        Pool::Free(block);
        // ListLength isn't thread-safe, so we cannot use it here. So we just expect that
        // Alloc/Free cycles do not result in a crash.
      }
    });
  }

  for (auto& thread : threads) {
    thread.join();
  }
  EXPECT_EQ(ListLength(), 2u);
}

}  // namespace

}  // namespace berberis
