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

#ifndef CHRE_UTIL_FIXED_SIZE_BLOCKING_QUEUE_H_
#define CHRE_UTIL_FIXED_SIZE_BLOCKING_QUEUE_H_

#include <deque>

#include "chre/platform/condition_variable.h"
#include "chre/platform/mutex.h"
#include "chre/util/array_queue.h"
#include "chre/util/non_copyable.h"
#include "chre/util/segmented_queue.h"

namespace chre {

namespace blocking_queue_internal {

/**
 * The wrapper around queue storage (ArraryQueue or SegmentedQueue) that
 * provide thread safety.
 *
 * The queue storage must provide the following APIs:
 *    empty(), size(), push(), front(), pop(), remove(), operator[].
 */
template <typename ElementType, class QueueStorageType>
class BlockingQueueCore : public QueueStorageType {
 public:
  // Inherit constructors from QueueStorageType
  using QueueStorageType::QueueStorageType;

  /**
   * Determines whether or not the BlockingQueue is empty.
   */
  bool empty();

  /**
   * Determines the current size of the BlockingQueue.
   */
  size_t size();

  /**
   * Pushes an element into the queue and notifies any waiting threads that an
   * element is available.
   *
   * @param The element to be pushed.
   * @return true if the element is pushed successfully.
   */
  bool push(const ElementType &element);
  bool push(ElementType &&element);

  /**
   * Pops one element from the queue. If the queue is empty, the thread will
   * block until an element has been pushed.
   *
   * @return The element that was popped.
   */
  ElementType pop();

  /**
   * Removes an element from the array queue given an index. It returns false if
   * the index is out of bounds of the underlying array queue.
   *
   * @param index Requested index in range [0,size()-1]
   * @return true if the indexed element has been removed successfully.
   */
  bool remove(size_t index);

  /**
   * Obtains an element of the array queue given an index. It is illegal to
   * index this array queue out of bounds and the user of the API must check
   * the size() function prior to indexing this array queue to ensure that they
   * will not read out of bounds.
   *
   * @param index Requested index in range [0,size()-1]
   * @return The element.
   */
  ElementType &operator[](size_t index);
  const ElementType &operator[](size_t index) const;

 protected:
  //! The mutex used to ensure thread-safety. Mutable to allow const
  //! operator[].
  mutable Mutex mMutex;

 private:
  //! The condition variable used to implement the blocking behavior of the
  //! queue.
  ConditionVariable mConditionVariable;
};
}  // namespace blocking_queue_internal

/**
 * Wrapper for the blocking queue implementation that uses chre::ArrayQueue as
 * the data container.
 *
 * @tparam ElementType type of the item that will be stored in the queue.
 * @tparam kSize maximum item count that this queue can hold
 */
template <typename ElementType, size_t kSize>
class FixedSizeBlockingQueue
    : public blocking_queue_internal::BlockingQueueCore<
          ElementType, ArrayQueue<ElementType, kSize>> {
 public:
  typedef ElementType value_type;
};

}  // namespace chre

#include "chre/util/fixed_size_blocking_queue_impl.h"

#endif  // CHRE_UTIL_FIXED_SIZE_BLOCKING_QUEUE_H_
