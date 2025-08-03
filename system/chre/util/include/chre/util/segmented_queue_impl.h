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

#ifndef CHRE_UTIL_SEGMENTED_QUEUE_IMPL_H
#define CHRE_UTIL_SEGMENTED_QUEUE_IMPL_H

#include <type_traits>
#include <utility>

#include "chre/util/container_support.h"
#include "chre/util/segmented_queue.h"

namespace chre {

template <typename ElementType, size_t kBlockSize>
SegmentedQueue<ElementType, kBlockSize>::SegmentedQueue(size_t maxBlockCount,
                                                        size_t staticBlockCount)
    : kMaxBlockCount(maxBlockCount), kStaticBlockCount(staticBlockCount) {
  CHRE_ASSERT(kMaxBlockCount >= kStaticBlockCount);
  CHRE_ASSERT(kStaticBlockCount > 0);
  CHRE_ASSERT(kMaxBlockCount * kBlockSize < SIZE_MAX);
  mRawStoragePtrs.reserve(kMaxBlockCount);
  for (size_t i = 0; i < kStaticBlockCount; i++) {
    pushOneBlock();
  }
}

template <typename ElementType, size_t kBlockSize>
SegmentedQueue<ElementType, kBlockSize>::~SegmentedQueue() {
  clear();
}

template <typename ElementType, size_t kBlockSize>
bool SegmentedQueue<ElementType, kBlockSize>::push_back(
    const ElementType &element) {
  if (!prepareForPush()) {
    return false;
  }
  new (&locateDataAddress(mTail)) ElementType(element);
  mSize++;

  return true;
}

template <typename ElementType, size_t kBlockSize>
bool SegmentedQueue<ElementType, kBlockSize>::push_back(ElementType &&element) {
  if (!prepareForPush()) {
    return false;
  }
  new (&locateDataAddress(mTail)) ElementType(std::move(element));
  mSize++;

  return true;
}

template <typename ElementType, size_t kBlockSize>
bool SegmentedQueue<ElementType, kBlockSize>::push(const ElementType &element) {
  return push_back(element);
}

template <typename ElementType, size_t kBlockSize>
bool SegmentedQueue<ElementType, kBlockSize>::push(ElementType &&element) {
  return push_back(std::move(element));
}

template <typename ElementType, size_t kBlockSize>
template <typename... Args>
bool SegmentedQueue<ElementType, kBlockSize>::emplace_back(Args &&...args) {
  if (!prepareForPush()) {
    return false;
  }
  new (&locateDataAddress(mTail)) ElementType(std::forward<Args>(args)...);
  mSize++;

  return true;
}

template <typename ElementType, size_t kBlockSize>
ElementType &SegmentedQueue<ElementType, kBlockSize>::operator[](size_t index) {
  CHRE_ASSERT(index < mSize);

  return locateDataAddress(relativeIndexToAbsolute(index));
}

template <typename ElementType, size_t kBlockSize>
const ElementType &SegmentedQueue<ElementType, kBlockSize>::operator[](
    size_t index) const {
  CHRE_ASSERT(index < mSize);

  return locateDataAddress(relativeIndexToAbsolute(index));
}

template <typename ElementType, size_t kBlockSize>
ElementType &SegmentedQueue<ElementType, kBlockSize>::back() {
  CHRE_ASSERT(!empty());

  return locateDataAddress(mTail);
}

template <typename ElementType, size_t kBlockSize>
const ElementType &SegmentedQueue<ElementType, kBlockSize>::back() const {
  CHRE_ASSERT(!empty());

  return locateDataAddress(mTail);
}

template <typename ElementType, size_t kBlockSize>
ElementType &SegmentedQueue<ElementType, kBlockSize>::front() {
  CHRE_ASSERT(!empty());

  return locateDataAddress(mHead);
}

template <typename ElementType, size_t kBlockSize>
const ElementType &SegmentedQueue<ElementType, kBlockSize>::front() const {
  CHRE_ASSERT(!empty());

  return locateDataAddress(mHead);
}

template <typename ElementType, size_t kBlockSize>
void SegmentedQueue<ElementType, kBlockSize>::pop_front() {
  CHRE_ASSERT(!empty());

  doRemove(mHead);

  if (mSize == 0) {
    // TODO(b/258860898), Define a more proactive way to deallocate unused
    // block.
    resetEmptyQueue();
  } else {
    mHead = advanceOrWrapAround(mHead);
  }
}

template <typename ElementType, size_t kBlockSize>
void SegmentedQueue<ElementType, kBlockSize>::pop() {
  pop_front();
}

template <typename ElementType, size_t kBlockSize>
bool SegmentedQueue<ElementType, kBlockSize>::remove(size_t index) {
  if (index >= mSize) {
    return false;
  }

  if (index < mSize / 2) {
    pullBackward(index);
  } else {
    pullForward(index);
  }

  if (mSize == 0) {
    resetEmptyQueue();
  }
  return true;
}

template <typename ElementType, size_t kBlockSize>
size_t SegmentedQueue<ElementType, kBlockSize>::searchMatches(
    MatchingFunction *matchFunc, size_t foundIndicesLen,
    size_t foundIndices[]) {
  size_t foundCount = 0;
  size_t searchIndex = advanceOrWrapAround(mTail);
  bool firstRound = true;

  if (size() == 0) {
    return 0;
  }

  // firstRound need to be checked since if the queue is full, the index after
  // mTail will be mHead, leading to the loop falsely terminate in the first
  // round.
  while ((searchIndex != mHead || firstRound) &&
         foundCount != foundIndicesLen) {
    searchIndex = subtractOrWrapAround(searchIndex, 1 /* steps */);
    firstRound = false;
    if (matchFunc(locateDataAddress(searchIndex))) {
      foundIndices[foundCount] = searchIndex;
      ++foundCount;
    }
  }
  return foundCount;
}

template <typename ElementType, size_t kBlockSize>
void SegmentedQueue<ElementType, kBlockSize>::fillGaps(
    size_t gapCount, const size_t gapIndices[]) {
  if (gapCount == 0) {
    return;
  }

  // Move the elements between each gap indices section by section from the
  // section that is closest to the head. The destination index = the gap index
  // - how many gaps has been filled.
  //
  // For instance, assuming we have elements that we want to remove (gaps) at
  // these indices = [8, 7, 5, 2] and the last element is at index 10.
  //
  // The first iteration will move the items at index 3, 4, which is the first
  // section, to index 2, 3 and overwrite the original item at index 2, making
  // the queue: [0, 1, 3, 4, x, 5, 6, ...., 10] where x means empty slot.
  //
  // The second iteration will do a similar thing, move item 6 to the empty
  // slot, which could be calculated by using the index of the last gap and how
  // many gaps has been filled. So the queue turns into:
  // [0, 1, 3, 4, 6, x, x, 7, 8, 9, 10], note that there are now two empty slots
  // since there are two gaps filled.
  //
  // The third iteration does not move anything since there are no items between
  // 7 and 8.
  //
  // The final iteration is a special case to close the final gap. After the
  // final iteration, the queue will become: [1, 3, 4, 6, 9, 10].

  for (size_t i = gapCount - 1; i > 0; --i) {
    moveElements(advanceOrWrapAround(gapIndices[i]),
                 subtractOrWrapAround(gapIndices[i], gapCount - 1 - i),
                 absoluteIndexToRelative(gapIndices[i - 1]) -
                     absoluteIndexToRelative(gapIndices[i]) - 1);
  }

  // Since mTail is not guaranteed to be a gap, we need to make a special case
  // for moving the last section.
  moveElements(
      advanceOrWrapAround(gapIndices[0]),
      subtractOrWrapAround(gapIndices[0], gapCount - 1),
      absoluteIndexToRelative(mTail) - absoluteIndexToRelative(gapIndices[0]));
  mTail = subtractOrWrapAround(mTail, gapCount);
}

template <typename ElementType, size_t kBlockSize>
size_t SegmentedQueue<ElementType, kBlockSize>::removeMatchedFromBack(
    MatchingFunction *matchFunc, size_t maxNumOfElementsRemoved,
    FreeFunction *freeFunction, void *extraDataForFreeFunction) {
  size_t removeIndex[maxNumOfElementsRemoved];
  size_t removedItemCount =
      searchMatches(matchFunc, maxNumOfElementsRemoved, removeIndex);

  if (removedItemCount != 0) {
    for (size_t i = 0; i < removedItemCount; ++i) {
      if (freeFunction == nullptr) {
        doRemove(removeIndex[i]);
      } else {
        --mSize;
        freeFunction(locateDataAddress(removeIndex[i]),
                     extraDataForFreeFunction);
      }
    }

    if (mSize == 0) {
      resetEmptyQueue();
    } else {
      fillGaps(removedItemCount, removeIndex);
    }
  }

  return removedItemCount;
}

template <typename ElementType, size_t kBlockSize>
bool SegmentedQueue<ElementType, kBlockSize>::pushOneBlock() {
  return insertBlock(mRawStoragePtrs.size());
}

template <typename ElementType, size_t kBlockSize>
bool SegmentedQueue<ElementType, kBlockSize>::insertBlock(size_t blockIndex) {
  // Supporting inserting at any index since we started this data structure as
  // std::deque and would like to support push_front() in the future. This
  // function should not be needed once b/258771255 is implemented.
  CHRE_ASSERT(mRawStoragePtrs.size() != kMaxBlockCount);
  bool success = false;

  Block *newBlockPtr = static_cast<Block *>(memoryAlloc(sizeof(Block)));
  if (newBlockPtr != nullptr) {
    success = mRawStoragePtrs.insert(blockIndex, UniquePtr(newBlockPtr));
    if (success) {
      if (!empty() && mHead >= blockIndex * kBlockSize) {
        // If we are inserting a block before the current mHead, we need to
        // increase the offset since we now have a new gap from mHead to the
        // head of the container.
        mHead += kBlockSize;
      }

      // If mTail is after the new block, we want to move mTail to make sure
      // that the queue is continuous.
      if (mTail >= blockIndex * kBlockSize) {
        moveElements((blockIndex + 1) * kBlockSize, blockIndex * kBlockSize,
                     (mTail + 1) % kBlockSize);
      }
    }
  }
  if (!success) {
    LOG_OOM();
  }
  return success;
}

template <typename ElementType, size_t kBlockSize>
void SegmentedQueue<ElementType, kBlockSize>::moveElements(size_t srcIndex,
                                                           size_t destIndex,
                                                           size_t count) {
  CHRE_ASSERT(count <= mSize);
  CHRE_ASSERT(absoluteIndexToRelative(srcIndex) >
              absoluteIndexToRelative(destIndex));

  while (count--) {
    doMove(srcIndex, destIndex,
           typename std::is_move_constructible<ElementType>::type());
    srcIndex = advanceOrWrapAround(srcIndex);
    destIndex = advanceOrWrapAround(destIndex);
  }
}

template <typename ElementType, size_t kBlockSize>
void SegmentedQueue<ElementType, kBlockSize>::pullForward(size_t gapIndex) {
  CHRE_ASSERT(gapIndex < mSize);

  if (gapIndex < mSize / 2) {
    LOGD("Consider using pullBackward for better performance");
  }

  size_t gapAbsolute = relativeIndexToAbsolute(gapIndex);
  size_t tailSize = absoluteIndexToRelative(mTail) - gapIndex;
  size_t nextAbsolute = advanceOrWrapAround(gapAbsolute);
  doRemove(gapAbsolute);
  for (size_t i = 0; i < tailSize; ++i) {
    doMove(nextAbsolute, gapAbsolute,
           typename std::is_move_constructible<ElementType>::type());
    gapAbsolute = nextAbsolute;
    nextAbsolute = advanceOrWrapAround(nextAbsolute);
  }
  mTail = subtractOrWrapAround(mTail, /* steps= */ 1);
}

template <typename ElementType, size_t kBlockSize>
void SegmentedQueue<ElementType, kBlockSize>::pullBackward(size_t gapIndex) {
  CHRE_ASSERT(gapIndex < mSize);

  if (gapIndex > mSize / 2) {
    LOGD("Consider using pullForward for better performance");
  }

  size_t headSize = gapIndex;
  size_t gapAbsolute = relativeIndexToAbsolute(gapIndex);
  size_t prevAbsolute = subtractOrWrapAround(gapAbsolute, /* steps= */ 1);
  doRemove(gapAbsolute);
  for (size_t i = 0; i < headSize; ++i) {
    doMove(prevAbsolute, gapAbsolute,
           typename std::is_move_constructible<ElementType>::type());
    gapAbsolute = prevAbsolute;
    prevAbsolute = subtractOrWrapAround(prevAbsolute, /* steps= */ 1);
  }
  mHead = advanceOrWrapAround(mHead);
}

template <typename ElementType, size_t kBlockSize>
void SegmentedQueue<ElementType, kBlockSize>::doMove(size_t srcIndex,
                                                     size_t destIndex,
                                                     std::true_type) {
  new (&locateDataAddress(destIndex))
      ElementType(std::move(locateDataAddress(srcIndex)));
}

template <typename ElementType, size_t kBlockSize>
void SegmentedQueue<ElementType, kBlockSize>::doMove(size_t srcIndex,
                                                     size_t destIndex,
                                                     std::false_type) {
  new (&locateDataAddress(destIndex)) ElementType(locateDataAddress(srcIndex));
}

template <typename ElementType, size_t kBlockSize>
size_t SegmentedQueue<ElementType, kBlockSize>::relativeIndexToAbsolute(
    size_t index) {
  size_t absoluteIndex = mHead + index;
  if (absoluteIndex >= capacity()) {
    absoluteIndex -= capacity();
  }
  return absoluteIndex;
}

template <typename ElementType, size_t kBlockSize>
size_t SegmentedQueue<ElementType, kBlockSize>::absoluteIndexToRelative(
    size_t index) {
  if (mHead > index) {
    index += capacity();
  }
  return index - mHead;
}

template <typename ElementType, size_t kBlockSize>
bool SegmentedQueue<ElementType, kBlockSize>::prepareForPush() {
  bool success = false;
  if (full()) {
    LOG_OOM();
  } else {
    if (mSize == capacity()) {
      // TODO(b/258771255): index-based insert block should go away when we
      // have a ArrayQueue based container.
      size_t insertBlockIndex = (mTail + 1) / kBlockSize;
      success = insertBlock(insertBlockIndex);
    } else {
      success = true;
    }
    if (success) {
      mTail = advanceOrWrapAround(mTail);
    }
  }
  return success;
}

template <typename ElementType, size_t kBlockSize>
void SegmentedQueue<ElementType, kBlockSize>::clear() {
  if (!std::is_trivially_destructible<ElementType>::value) {
    while (!empty()) {
      pop_front();
    }
  } else {
    mSize = 0;
    mHead = 0;
    mTail = capacity() - 1;
  }
}

template <typename ElementType, size_t kBlockSize>
ElementType &SegmentedQueue<ElementType, kBlockSize>::locateDataAddress(
    size_t index) {
  return mRawStoragePtrs[index / kBlockSize].get()->data()[index % kBlockSize];
}

template <typename ElementType, size_t kBlockSize>
size_t SegmentedQueue<ElementType, kBlockSize>::advanceOrWrapAround(
    size_t index) {
  return index >= capacity() - 1 ? 0 : index + 1;
}

template <typename ElementType, size_t kBlockSize>
size_t SegmentedQueue<ElementType, kBlockSize>::subtractOrWrapAround(
    size_t index, size_t steps) {
  return index < steps ? capacity() + index - steps : index - steps;
}

template <typename ElementType, size_t kBlockSize>
void SegmentedQueue<ElementType, kBlockSize>::doRemove(size_t index) {
  mSize--;
  locateDataAddress(index).~ElementType();
}

template <typename ElementType, size_t kBlockSize>
void SegmentedQueue<ElementType, kBlockSize>::resetEmptyQueue() {
  CHRE_ASSERT(empty());

  while (mRawStoragePtrs.size() != kStaticBlockCount) {
    mRawStoragePtrs.pop_back();
  }
  mHead = 0;
  mTail = capacity() - 1;
}

}  // namespace chre

#endif  // CHRE_UTIL_SEGMENTED_QUEUE_IMPL_H