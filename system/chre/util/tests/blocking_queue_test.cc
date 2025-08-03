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

#include "chre/util/blocking_segmented_queue.h"
#include "chre/util/fixed_size_blocking_queue.h"
#include "chre/util/unique_ptr.h"

using chre::BlockingSegmentedQueue;
using chre::FixedSizeBlockingQueue;
using chre::MakeUnique;
using chre::UniquePtr;

namespace {
class ConstructorCount {
 public:
  ConstructorCount(int value_, ssize_t *constructedCount)
      : sConstructedCounter(constructedCount), value(value_) {
    (*sConstructedCounter)++;
  }
  ~ConstructorCount() {
    (*sConstructedCounter)--;
  }
  int getValue() {
    return value;
  }

  ssize_t *sConstructedCounter;

 private:
  int value;
};

}  // namespace

TEST(BlockingQueue, IsEmptyByDefault) {
  FixedSizeBlockingQueue<int, 16> blockingQueue;
  ASSERT_TRUE(blockingQueue.empty());
}

TEST(BlockingQueue, PushPopVerifyOrder) {
  FixedSizeBlockingQueue<int, 16> blockingQueue;

  ASSERT_TRUE(blockingQueue.push(0x1337));
  ASSERT_TRUE(blockingQueue.push(0xcafe));

  ASSERT_EQ(blockingQueue.pop(), 0x1337);
  ASSERT_EQ(blockingQueue.pop(), 0xcafe);
}

TEST(BlockingQueue, PushPopMove) {
  static constexpr int kVal = 0xbeef;
  UniquePtr<int> ptr = MakeUnique<int>();
  *ptr = kVal;

  FixedSizeBlockingQueue<UniquePtr<int>, 16> blockingQueue;

  ASSERT_TRUE(blockingQueue.push(std::move(ptr)));
  ASSERT_TRUE(ptr.isNull());
  ASSERT_EQ(*(blockingQueue.pop()), kVal);
}

TEST(BlockingSegmentedQueue, InitState) {
  constexpr uint8_t blockSize = 16;
  constexpr uint8_t maxBlockCount = 3;
  constexpr uint8_t staticBlockCount = 2;
  BlockingSegmentedQueue<int, blockSize> blockingQueue(maxBlockCount,
                                                       staticBlockCount);
  ASSERT_TRUE(blockingQueue.empty());
  ASSERT_EQ(blockingQueue.block_count(), staticBlockCount);
}
