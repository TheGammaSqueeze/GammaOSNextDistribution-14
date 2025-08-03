/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include "chre/util/synchronized_memory_pool.h"

#include "gtest/gtest.h"

using chre::SynchronizedMemoryPool;

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

TEST(SynchronizedMemoryPool, FreeBlockCheck) {
  constexpr uint8_t maxSize = 12;
  constexpr uint8_t blankSpace = 2;

  SynchronizedMemoryPool<int, maxSize> testMemoryPool;
  int *tempDataPtrs[maxSize];

  for (int i = 0; i < maxSize - blankSpace; ++i) {
    tempDataPtrs[i] = testMemoryPool.allocate(i);
  }

  EXPECT_EQ(testMemoryPool.getFreeBlockCount(), blankSpace);

  for (int i = 0; i < maxSize - blankSpace; ++i) {
    testMemoryPool.deallocate(tempDataPtrs[i]);
  }
}
