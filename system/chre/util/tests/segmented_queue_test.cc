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

#include "chre/util/segmented_queue.h"

#include <cstdlib>
#include <deque>
#include <vector>

#include "chre/util/enum.h"
#include "chre/util/non_copyable.h"
#include "gtest/gtest.h"

using chre::SegmentedQueue;
using std::deque;
using std::vector;

namespace {

class ConstructorCount {
 public:
  ConstructorCount() = default;
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

constexpr int kConstructedMagic = 0xdeadbeef;
class CopyableButNonMovable {
 public:
  CopyableButNonMovable(int value) : mValue(value) {}

  CopyableButNonMovable(const CopyableButNonMovable &other) {
    mValue = other.mValue;
  }

  CopyableButNonMovable &operator=(const CopyableButNonMovable &other) {
    CHRE_ASSERT(mMagic == kConstructedMagic);
    mValue = other.mValue;
    return *this;
  }

  CopyableButNonMovable(CopyableButNonMovable &&other) = delete;
  CopyableButNonMovable &operator=(CopyableButNonMovable &&other) = delete;

  int getValue() const {
    return mValue;
  }

 private:
  int mMagic = kConstructedMagic;
  int mValue;
};

class MovableButNonCopyable : public chre::NonCopyable {
 public:
  MovableButNonCopyable(int value) : mValue(value) {}

  MovableButNonCopyable(MovableButNonCopyable &&other) {
    mValue = other.mValue;
    other.mValue = -1;
  }

  MovableButNonCopyable &operator=(MovableButNonCopyable &&other) {
    CHRE_ASSERT(mMagic == kConstructedMagic);
    mValue = other.mValue;
    other.mValue = -1;
    return *this;
  }

  int getValue() const {
    return mValue;
  }

 private:
  int mMagic = kConstructedMagic;
  int mValue;
};

enum class OperationType : uint8_t {
  EMPLACE_BACK = 0,
  PUSH_BACK,
  POP_FRONT,
  REMOVE,
  BATCH_REMOVE,

  OPERATION_TYPE_COUNT,  // Must be at the end.
};

}  // namespace

TEST(SegmentedQueue, InitialzedState) {
  constexpr uint8_t blockSize = 5;
  constexpr uint8_t maxBlockCount = 3;
  constexpr uint8_t staticBlockCount = 2;
  SegmentedQueue<int, blockSize> segmentedQueue(maxBlockCount,
                                                staticBlockCount);

  EXPECT_EQ(segmentedQueue.block_count(), staticBlockCount);
  EXPECT_EQ(segmentedQueue.capacity(), staticBlockCount * blockSize);
  EXPECT_EQ(segmentedQueue.size(), 0);
}

TEST(SegmentedQueue, PushAndRead) {
  constexpr uint8_t blockSize = 5;
  constexpr uint8_t maxBlockCount = 3;
  SegmentedQueue<int, blockSize> segmentedQueue(maxBlockCount);

  for (uint32_t queueSize = 0; queueSize < blockSize * maxBlockCount;
       queueSize++) {
    EXPECT_TRUE(segmentedQueue.push_back(queueSize));
    EXPECT_EQ(segmentedQueue.size(), queueSize + 1);
    EXPECT_EQ(segmentedQueue[queueSize], queueSize);
    EXPECT_EQ(segmentedQueue.back(), queueSize);
  }

  EXPECT_FALSE(segmentedQueue.push_back(10000));
  EXPECT_EQ(segmentedQueue.size(), maxBlockCount * blockSize);
  EXPECT_TRUE(segmentedQueue.full());
}

TEST(SegmentedQueue, EmplaceAndRead) {
  constexpr uint8_t blockSize = 5;
  constexpr uint8_t maxBlockCount = 3;
  ssize_t constructorCount = 0;
  SegmentedQueue<ConstructorCount, blockSize> segmentedQueue(maxBlockCount);

  for (uint32_t queueSize = 0; queueSize < blockSize * maxBlockCount;
       queueSize++) {
    ssize_t oldConstructedCounter = constructorCount;
    EXPECT_TRUE(segmentedQueue.emplace_back(queueSize, &constructorCount));
    EXPECT_EQ(segmentedQueue.size(), queueSize + 1);
    EXPECT_EQ(segmentedQueue[queueSize].getValue(), queueSize);
    EXPECT_EQ(segmentedQueue.back().getValue(), queueSize);
    EXPECT_EQ(constructorCount, oldConstructedCounter + 1);
  }

  EXPECT_FALSE(segmentedQueue.emplace_back(10000, &constructorCount));
  EXPECT_EQ(segmentedQueue.size(), maxBlockCount * blockSize);
  EXPECT_TRUE(segmentedQueue.full());
}

TEST(SegmentedQueue, PushAndReadCopyableButNonMovable) {
  constexpr uint8_t blockSize = 5;
  constexpr uint8_t maxBlockCount = 3;
  SegmentedQueue<CopyableButNonMovable, blockSize> segmentedQueue(
      maxBlockCount);

  for (uint32_t queueSize = 0; queueSize < blockSize * maxBlockCount;
       queueSize++) {
    CopyableButNonMovable cbnm(queueSize);
    EXPECT_TRUE(segmentedQueue.push_back(cbnm));
    EXPECT_EQ(segmentedQueue.size(), queueSize + 1);
    EXPECT_EQ(segmentedQueue[queueSize].getValue(), queueSize);
    EXPECT_EQ(segmentedQueue.back().getValue(), queueSize);
  }
}

TEST(SegmentedQueue, PushAndReadMovableButNonCopyable) {
  constexpr uint8_t blockSize = 5;
  constexpr uint8_t maxBlockCount = 3;
  SegmentedQueue<MovableButNonCopyable, blockSize> segmentedQueue(
      maxBlockCount);

  for (uint8_t blockIndex = 0; blockIndex < maxBlockCount; blockIndex++) {
    for (uint8_t index = 0; index < blockSize; index++) {
      int value = segmentedQueue.size();
      EXPECT_TRUE(segmentedQueue.emplace_back(value));
      EXPECT_EQ(segmentedQueue.size(), value + 1);
      EXPECT_EQ(segmentedQueue[value].getValue(), value);
      EXPECT_EQ(segmentedQueue.back().getValue(), value);
    }
  }
}

TEST(SegmentedQueue, ReadAndPop) {
  constexpr uint8_t blockSize = 5;
  constexpr uint8_t maxBlockCount = 3;
  SegmentedQueue<ConstructorCount, blockSize> segmentedQueue(maxBlockCount);
  ssize_t constructedCounter = 0;

  for (uint32_t index = 0; index < blockSize * maxBlockCount; index++) {
    EXPECT_TRUE(segmentedQueue.emplace_back(index, &constructedCounter));
  }

  uint8_t originalQueueSize = segmentedQueue.size();
  for (uint8_t index = 0; index < originalQueueSize; index++) {
    EXPECT_EQ(segmentedQueue[index].getValue(), index);
  }

  size_t capacityBeforePop = segmentedQueue.capacity();
  while (!segmentedQueue.empty()) {
    ASSERT_EQ(segmentedQueue.front().getValue(),
              originalQueueSize - segmentedQueue.size());
    ssize_t oldConstructedCounter = constructedCounter;
    segmentedQueue.pop_front();
    EXPECT_EQ(oldConstructedCounter - 1, constructedCounter);
  }

  EXPECT_EQ(segmentedQueue.size(), 0);
  EXPECT_TRUE(segmentedQueue.empty());
  EXPECT_LT(segmentedQueue.capacity(), capacityBeforePop);
  EXPECT_GT(segmentedQueue.capacity(), 0);
}

TEST(SegmentedQueue, RemoveTest) {
  constexpr uint8_t blockSize = 2;
  constexpr uint8_t maxBlockCount = 3;
  SegmentedQueue<int, blockSize> segmentedQueue(maxBlockCount);

  for (uint32_t index = 0; index < blockSize * maxBlockCount; index++) {
    EXPECT_TRUE(segmentedQueue.push_back(index));
  }

  // segmentedQueue = [[0, 1], [2, 3], [4, 5]]
  EXPECT_FALSE(segmentedQueue.remove(segmentedQueue.size()));

  EXPECT_TRUE(segmentedQueue.remove(4));
  EXPECT_EQ(segmentedQueue[4], 5);
  EXPECT_EQ(segmentedQueue[3], 3);
  EXPECT_EQ(segmentedQueue.size(), 5);

  EXPECT_TRUE(segmentedQueue.remove(1));
  EXPECT_EQ(segmentedQueue[3], 5);
  EXPECT_EQ(segmentedQueue[1], 2);
  EXPECT_EQ(segmentedQueue[0], 0);
  EXPECT_EQ(segmentedQueue.size(), 4);

  size_t currentSize = segmentedQueue.size();
  size_t capacityBeforeRemove = segmentedQueue.capacity();
  while (currentSize--) {
    EXPECT_TRUE(segmentedQueue.remove(0));
  }

  EXPECT_EQ(segmentedQueue.size(), 0);
  EXPECT_TRUE(segmentedQueue.empty());
  EXPECT_LT(segmentedQueue.capacity(), capacityBeforeRemove);
  EXPECT_GT(segmentedQueue.capacity(), 0);
}

TEST(SegmentedQueue, MiddleBlockTest) {
  // This test tests that the SegmentedQueue will behave correctly when
  // the reference of front() and back() are not aligned to the head/back
  // of a block.

  constexpr uint8_t blockSize = 3;
  constexpr uint8_t maxBlockCount = 3;
  SegmentedQueue<int, blockSize> segmentedQueue(maxBlockCount);

  for (uint32_t index = 0; index < blockSize * (maxBlockCount - 1); index++) {
    EXPECT_TRUE(segmentedQueue.push_back(index));
  }

  segmentedQueue.pop_front();
  segmentedQueue.pop_front();
  EXPECT_TRUE(segmentedQueue.push_back(6));
  EXPECT_TRUE(segmentedQueue.push_back(7));

  // segmentedQueue = [[6, 7, 2], [3, 4, 5], [X]]
  EXPECT_EQ(segmentedQueue.front(), 2);
  EXPECT_EQ(segmentedQueue.back(), 7);

  EXPECT_TRUE(segmentedQueue.push_back(8));
  EXPECT_EQ(segmentedQueue.back(), 8);

  // segmentedQueue = [[x, x, 2], [3, 4, 5], [6, 7, 8]]
  EXPECT_TRUE(segmentedQueue.push_back(9));
  EXPECT_TRUE(segmentedQueue.push_back(10));

  for (int i = 0; i < segmentedQueue.size(); i++) {
    EXPECT_EQ(segmentedQueue[i], i + 2);
  }
}

TEST(SegmentedQueue, RemoveMatchesEnoughItem) {
  constexpr uint8_t blockSize = 3;
  constexpr uint8_t maxBlockCount = 2;
  ssize_t constCounter = 0;
  SegmentedQueue<ConstructorCount, blockSize> segmentedQueue(maxBlockCount);

  for (uint32_t index = 0; index < blockSize * maxBlockCount; index++) {
    EXPECT_TRUE(segmentedQueue.emplace_back(index, &constCounter));
  }

  EXPECT_EQ(
      3, segmentedQueue.removeMatchedFromBack(
             [](ConstructorCount &element) { return element.getValue() <= 4; },
             3));

  EXPECT_EQ(segmentedQueue[0].getValue(), 0);
  EXPECT_EQ(segmentedQueue[1].getValue(), 1);
  EXPECT_EQ(segmentedQueue[2].getValue(), 5);
  EXPECT_EQ(segmentedQueue.size(), blockSize * maxBlockCount - 3);
  EXPECT_EQ(segmentedQueue.front().getValue(), 0);
  EXPECT_EQ(segmentedQueue.back().getValue(), 5);
  EXPECT_EQ(constCounter, 3);
}

TEST(SegmentedQueue, RemoveMatchesEmptyQueue) {
  constexpr uint8_t blockSize = 5;
  constexpr uint8_t maxBlockCount = 2;
  SegmentedQueue<int, blockSize> segmentedQueue(maxBlockCount);

  EXPECT_EQ(0, segmentedQueue.removeMatchedFromBack(
                   [](int element) { return element >= 5; }, 3));
  EXPECT_EQ(segmentedQueue.size(), 0);
}

TEST(SegmentedQueue, RemoveMatchesSingleElementQueue) {
  constexpr uint8_t blockSize = 5;
  constexpr uint8_t maxBlockCount = 2;
  SegmentedQueue<int, blockSize> segmentedQueue(maxBlockCount);

  EXPECT_TRUE(segmentedQueue.push_back(1));

  EXPECT_EQ(1, segmentedQueue.removeMatchedFromBack(
                   [](int element) { return element == 1; }, 3));
  EXPECT_EQ(segmentedQueue.size(), 0);
}

TEST(SegmentedQueue, RemoveMatchesTemp) {
  constexpr uint8_t blockSize = 5;
  constexpr uint8_t maxBlockCount = 2;
  SegmentedQueue<int, blockSize> segmentedQueue(maxBlockCount);

  EXPECT_TRUE(segmentedQueue.push_back(1));

  EXPECT_EQ(1, segmentedQueue.removeMatchedFromBack(
                   [](int element) { return element == 1; }, 3));
  EXPECT_EQ(segmentedQueue.size(), 0);
}

TEST(SegmentedQueue, RemoveMatchesTailInMiddle) {
  constexpr uint8_t blockSize = 5;
  constexpr uint8_t maxBlockCount = 2;
  SegmentedQueue<int, blockSize> segmentedQueue(maxBlockCount);

  for (uint32_t index = 0; index < blockSize * maxBlockCount; index++) {
    EXPECT_TRUE(segmentedQueue.emplace_back(index));
  }

  segmentedQueue.pop();
  segmentedQueue.pop();
  segmentedQueue.push_back(blockSize * maxBlockCount);
  segmentedQueue.push_back(blockSize * maxBlockCount + 1);

  EXPECT_EQ(5, segmentedQueue.removeMatchedFromBack(
                   [](int item) { return item % 2 == 0; }, 10));
  EXPECT_EQ(segmentedQueue.size(), 5);

  EXPECT_EQ(segmentedQueue[0], 3);
  EXPECT_EQ(segmentedQueue[1], 5);
  EXPECT_EQ(segmentedQueue[2], 7);
  EXPECT_EQ(segmentedQueue[3], 9);
  EXPECT_EQ(segmentedQueue[4], 11);

  EXPECT_EQ(segmentedQueue.front(), 3);
  EXPECT_EQ(segmentedQueue.back(), 11);
}

TEST(SegmentedQueue, RemoveMatchesWithFreeCallback) {
  constexpr uint8_t blockSize = 3;
  constexpr uint8_t maxBlockCount = 2;
  int8_t counter = 0;
  SegmentedQueue<uint8_t, blockSize> segmentedQueue(maxBlockCount);

  for (uint8_t index = 0; index < blockSize * maxBlockCount; ++index) {
    EXPECT_TRUE(segmentedQueue.push_back(index));
  }

  EXPECT_EQ(3, segmentedQueue.removeMatchedFromBack(
                   [](uint8_t item) { return item % 2 == 0; }, 3,
                   [](uint8_t item, void *counter) {
                     *static_cast<int8_t *>(counter) -= item;
                   },
                   &counter));

  EXPECT_EQ(counter, -6);  // item 0, 2, 4 is removed.
  EXPECT_EQ(segmentedQueue.size(), 3);
  EXPECT_EQ(segmentedQueue.back(), 5);
  EXPECT_EQ(segmentedQueue.front(), 1);
}

TEST(SegmentedQueue, PseudoRandomStressTest) {
  // This test uses std::deque as reference implementation to make sure
  // that chre::SegmentedQueue is functioning correctly.

  constexpr uint32_t maxIteration = 200;

  constexpr uint32_t totalSize = 1024;
  constexpr uint32_t blockSize = 16;

  ssize_t referenceQueueConstructedCounter = 0;
  ssize_t segmentedQueueConstructedCounter = 0;

  std::srand(0xbeef);

  deque<ConstructorCount> referenceDeque;
  SegmentedQueue<ConstructorCount, blockSize> testSegmentedQueue(totalSize /
                                                                 blockSize);

  for (uint32_t currentIteration = 0; currentIteration < maxIteration;
       currentIteration++) {
    OperationType operationType = static_cast<OperationType>(
        std::rand() % chre::asBaseType(OperationType::OPERATION_TYPE_COUNT));
    int temp = std::rand();
    switch (operationType) {
      case OperationType::PUSH_BACK:
        if (referenceDeque.size() < totalSize) {
          ASSERT_TRUE(testSegmentedQueue.push_back(
              ConstructorCount(temp, &segmentedQueueConstructedCounter)));
          referenceDeque.push_back(
              ConstructorCount(temp, &referenceQueueConstructedCounter));
        } else {
          ASSERT_FALSE(testSegmentedQueue.push_back(
              ConstructorCount(temp, &segmentedQueueConstructedCounter)));
        }
        break;

      case OperationType::EMPLACE_BACK:
        if (referenceDeque.size() < totalSize) {
          ASSERT_TRUE(testSegmentedQueue.emplace_back(
              temp, &segmentedQueueConstructedCounter));
          referenceDeque.emplace_back(temp, &referenceQueueConstructedCounter);
        } else {
          ASSERT_FALSE(testSegmentedQueue.emplace_back(
              temp, &segmentedQueueConstructedCounter));
        }
        break;

      case OperationType::POP_FRONT:
        ASSERT_EQ(testSegmentedQueue.empty(), referenceDeque.empty());
        if (!testSegmentedQueue.empty()) {
          testSegmentedQueue.pop_front();
          referenceDeque.pop_front();
        }
        break;

      case OperationType::REMOVE: {
        ASSERT_EQ(testSegmentedQueue.size(), referenceDeque.size());
        if (!testSegmentedQueue.empty()) {
          // Creates 50% chance for removing index that is out of bound
          size_t index = std::rand() % (testSegmentedQueue.size() * 2);
          if (index >= referenceDeque.size()) {
            ASSERT_FALSE(testSegmentedQueue.remove(index));
          } else {
            ASSERT_TRUE(testSegmentedQueue.remove(index));
            referenceDeque.erase(referenceDeque.begin() + index);
          }
        }
      } break;

      case OperationType::BATCH_REMOVE: {
        ASSERT_EQ(testSegmentedQueue.size(), referenceDeque.size());
        // Always try to remove a quarter of elements
        size_t targetRemoveElement = referenceDeque.size() * 0.25;
        vector<size_t> removedIndex;
        for (int i = referenceDeque.size() - 1; i >= 0; i--) {
          if (removedIndex.size() == targetRemoveElement) {
            break;
          } else if (referenceDeque[i].getValue() % 2 == 0) {
            removedIndex.push_back(i);
          }
        }
        for (auto idx : removedIndex) {
          referenceDeque.erase(referenceDeque.begin() + idx);
        }

        ASSERT_EQ(removedIndex.size(), testSegmentedQueue.removeMatchedFromBack(
                                           [](ConstructorCount &item) {
                                             return item.getValue() % 2 == 0;
                                           },
                                           targetRemoveElement));
      } break;

      case OperationType::OPERATION_TYPE_COUNT:
        // Should not be here, create this to prevent compiler error from
        // -Wswitch.
        FAIL();
        break;
    }

    // Complete check
    ASSERT_EQ(segmentedQueueConstructedCounter,
              referenceQueueConstructedCounter);
    ASSERT_EQ(testSegmentedQueue.size(), referenceDeque.size());
    ASSERT_EQ(testSegmentedQueue.empty(), referenceDeque.empty());
    if (!testSegmentedQueue.empty()) {
      ASSERT_EQ(testSegmentedQueue.back().getValue(),
                referenceDeque.back().getValue());
      ASSERT_EQ(testSegmentedQueue.front().getValue(),
                referenceDeque.front().getValue());
    }
    for (size_t idx = 0; idx < testSegmentedQueue.size(); idx++) {
      ASSERT_EQ(testSegmentedQueue[idx].getValue(),
                referenceDeque[idx].getValue());
    }
  }
}