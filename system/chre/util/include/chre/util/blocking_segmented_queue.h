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

#ifndef CHRE_UTIL_BLOCKING_SEGMENTED_QUEUE_H_
#define CHRE_UTIL_BLOCKING_SEGMENTED_QUEUE_H_

#include "chre/util/fixed_size_blocking_queue.h"
#include "chre/util/segmented_queue.h"
namespace chre {
/**
 * Similar but memory efficient version of chre::FixedSizeBlockingQueue.
 * This data structure achieves memory efficiency by using chre::SegmentedQueue
 * as the data storage. @see BlockingQueueCore and FixedSizeBlockingQueue for
 * more information.
 *
 * @tparam ElementType Type of element that will be stored.
 * @tparam kBlockSize number of elements that a block can contain.
 */
template <typename ElementType, size_t kBlockSize>
class BlockingSegmentedQueue
    : public blocking_queue_internal::BlockingQueueCore<
          ElementType, SegmentedQueue<ElementType, kBlockSize>> {
  using Container = ::chre::SegmentedQueue<ElementType, kBlockSize>;
  using BlockingQueue =
      ::chre::blocking_queue_internal::BlockingQueueCore<ElementType,
                                                         Container>;

 public:
  typedef ElementType value_type;

  /**
   * Create the blocking segmented queue object
   *
   * @param maxBlockCount the maximum number of block that the queue can hold.
   * @param staticBlockCount the number of block that will be construct by
   * constructor and will only be removed by destructor.
   */
  BlockingSegmentedQueue(size_t maxBlockCount, size_t staticBlockCount = 1)
      : BlockingQueue(maxBlockCount, staticBlockCount) {}
  /**
   * @return size_t the number of block that the queue is holding.
   */
  size_t block_count() {
    return Container::block_count();
  }

  size_t removeMatchedFromBack(typename Container::MatchingFunction *matchFunc,
                               size_t maxNumOfElementsRemoved,
                               typename Container::FreeFunction *freeFunction,
                               void *extraDataForFreeFunction) {
    LockGuard<Mutex> lock(BlockingQueue::mMutex);
    return Container::removeMatchedFromBack(matchFunc, maxNumOfElementsRemoved,
                                            freeFunction,
                                            extraDataForFreeFunction);
  }
};
}  // namespace chre

#endif  // CHRE_UTIL_BLOCKING_SEGMENTED_QUEUE_H_
