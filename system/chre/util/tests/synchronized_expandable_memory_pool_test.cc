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

#include "chre/util/synchronized_expandable_memory_pool.h"

#include "gtest/gtest.h"

using chre::SynchronizedExpandableMemoryPool;

namespace {

class ConstructorCount {
 public:
  ConstructorCount(int value_) : value(value_) {
    sConstructedCounter++;
  }
  ~ConstructorCount() {
    sConstructedCounter--;
  }
  int getValue() {
    return value;
  }

  static ssize_t sConstructedCounter;

 private:
  const int value;
};

ssize_t ConstructorCount::sConstructedCounter = 0;

}  // namespace

TEST(SynchronizedExpandAbleMemoryPool, InitStateTest) {
  constexpr uint8_t blockSize = 3;
  constexpr uint8_t maxBlockCount = 5;
  constexpr uint8_t staticBlockCount = 3;
  SynchronizedExpandableMemoryPool<int, blockSize, maxBlockCount>
      testMemoryPool(staticBlockCount);

  ASSERT_EQ(testMemoryPool.getFreeSpaceCount(), blockSize * maxBlockCount);
  ASSERT_EQ(testMemoryPool.getBlockCount(), staticBlockCount);
}

TEST(SynchronizedExpandAbleMemoryPool, OneAllocateAndDeallocate) {
  constexpr uint8_t blockSize = 3;
  constexpr uint8_t maxBlockCount = 5;

  SynchronizedExpandableMemoryPool<ConstructorCount, blockSize, maxBlockCount>
      testMemoryPool;
  ASSERT_EQ(testMemoryPool.getBlockCount(), 1);

  ConstructorCount *temp = testMemoryPool.allocate(10);
  ASSERT_NE(temp, nullptr);
  ASSERT_EQ(ConstructorCount::sConstructedCounter, 1);
  ASSERT_EQ(testMemoryPool.getFreeSpaceCount(), blockSize * maxBlockCount - 1);
  testMemoryPool.deallocate(temp);
  ASSERT_EQ(ConstructorCount::sConstructedCounter, 0);
  ASSERT_EQ(testMemoryPool.getFreeSpaceCount(), blockSize * maxBlockCount);
}

TEST(SynchronizedExpandAbleMemoryPool, HysteresisDeallocation) {
  constexpr uint8_t blockSize = 3;
  constexpr uint8_t maxBlockCount = 4;
  constexpr uint8_t staticBlockCount = 2;

  SynchronizedExpandableMemoryPool<int, blockSize, maxBlockCount>
      testMemoryPool(staticBlockCount);
  int *tempDataPtrs[blockSize * maxBlockCount];

  for (int i = 0; i < blockSize * maxBlockCount; i++) {
    tempDataPtrs[i] = testMemoryPool.allocate(i);
  }
  EXPECT_EQ(testMemoryPool.getBlockCount(), maxBlockCount);

  for (int i = blockSize * maxBlockCount - 1;
       i >= (maxBlockCount - 1) * blockSize; i--) {
    testMemoryPool.deallocate(tempDataPtrs[i]);
  }

  // Should not remove the last block if it just got empty.
  EXPECT_EQ(testMemoryPool.getBlockCount(), maxBlockCount);

  for (int i = 0; i < (maxBlockCount - 1) * blockSize; i++) {
    testMemoryPool.deallocate(tempDataPtrs[i]);
  }

  // Once it is empty, it should not still hold maxBlockCount as before.
  EXPECT_EQ(testMemoryPool.getFreeSpaceCount(), blockSize * maxBlockCount);
  EXPECT_EQ(testMemoryPool.getBlockCount(), staticBlockCount);
}
