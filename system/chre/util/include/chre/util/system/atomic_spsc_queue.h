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

#ifndef CHRE_UTIL_ATOMIC_SPSC_QUEUE_H_
#define CHRE_UTIL_ATOMIC_SPSC_QUEUE_H_

#include <algorithm>
#include <cstddef>
#include <cstdint>
#include <new>
#include <type_traits>

#include "chre/platform/atomic.h"
#include "chre/util/fixed_size_vector.h"
#include "chre/util/memory.h"
#include "chre/util/non_copyable.h"

/**
 * @file
 * AtomicSpscArrayQueue is a templated fixed-size FIFO queue implemented around
 * a contiguous array supporting atomic single-producer, single-consumer (SPSC)
 * usage. In other words, one thread of execution can safely add to the
 * queue while a different thread of execution can can pull from the queue,
 * without the use of locking. To ensure safe concurrency, the user of this
 * class must ensure that producer methods do not interleave with other producer
 * methods, and likewise for consumer methods. To help ensure this contract is
 * upheld, producer-only methods are grouped under the Producer subclass
 * (accessed via AtomicSpscArrayQueue::producer()), and likewise for Consumer.
 *
 * To accomplish concurrency without the use of locks, the head and tail
 * pointers are allowed to increment past the size of the container. They are
 * reset when new elements are pushed into an empty container, therefore the
 * usage model must involve relatively frequent emptying of the container to
 * prevent overflow of the indices. The nearingOverflow() method can be used to
 * detect when this condition is imminent, and enable flow control or some other
 * mechanism to ensure the queue is fully emptied before proceeding (though
 * triggering an assert/fatal error could also be considered, since the set of
 * conditions required to trigger this condition organically are expected to be
 * so rare as to be effectively impossible, so a bug is a more likely cause).
 *
 * Since modulo operations are common in the internals of this container, it's
 * recommended to use powers of 2 for the capacity where possible.
 */

namespace chre {

template <typename ElementType, size_t kCapacity>
class AtomicSpscQueue : public NonCopyable {
  // Since we rely on being able to increment mHead and mTail beyond kCapacity,
  // this provides some level of guarantee that we'll be able to do that a few
  // times before things are reset (when the queue is emptied).
  static_assert(kCapacity <= UINT32_MAX / 8,
                "Large capacity usage of AtomicSpscQueue is not advised");

 public:
  /**
   * Destroying the queue must only be done when it is guaranteed that the
   * producer and consumer execution contexts are both stopped.
   */
  ~AtomicSpscQueue() {
    size_t sz = size();
    auto c = consumer();
    for (size_t i = 0; i < sz; i++) {
      c.pop();
    }
  }

  size_t capacity() const {
    return kCapacity;
  }

  /**
   * Checks whether the queue has not been fully emptied in a long time, and
   * internal counters are nearing overflow, which would cause significant data
   * loss if it occurs (consumer sees queue as empty when it actually isn't,
   * until tail catches up to head). If this possibility is a concern, the
   * producer should check this and if it returns true, enable flow control to
   * stop adding new data to the queue until after the queue has been fully
   * emptied.
   *
   * @return true internal counters/indices are nearing overflow
   */
  bool nearingOverflow() const {
    return (mTail.load() > UINT32_MAX - kCapacity);
  }

  /**
   * Gets a snapshot of the number of elements currently stored in the queue.
   * Safe to call from any context.
   */
  size_t size() const {
    uint32_t head = mHead.load();
    uint32_t tail = mTail.load();

    // Note that head and tail are normally monotonically increasing with
    // head <= tail, *except* when we are resetting both head and tail to 0
    // (done only when adding new elements), in which case we reset tail first.
    // If our reads happened between resetting tail and resetting head, then
    // tail < head, and we can safely assume the queue is empty.
    if (head == tail || tail < head) {
      return 0;
    } else {
      return (tail - head);
    }
  }

  /**
   * Non-const methods within this class must ONLY be invoked from the producer
   * execution context.
   */
  class Producer {
   public:
    size_t capacity() const {
      return kCapacity;
    }
    bool full() const {
      return (size() == kCapacity);
    }
    size_t size() const {
      return mQueue.size();
    }

    /**
     * Constructs a new item at the end of the queue in-place.
     *
     * WARNING: Undefined behavior if the array queue is currently full.
     */
    template <typename... Args>
    void emplace(Args &&...args) {
      uint32_t newTail;
      new (nextStorage(&newTail)) ElementType(std::forward<Args>(args)...);
      mQueue.mTail = newTail;
    }

    /**
     * Pushes an element onto the back of the array queue.
     *
     * WARNING: Undefined behavior if the array queue is currently full.
     */
    void push(const ElementType &element) {
      uint32_t newTail;
      new (nextStorage(&newTail)) ElementType(element);
      mQueue.mTail = newTail;
    }

    //! Move construction version of push(const ElementType&)
    void push(ElementType &&element) {
      uint32_t newTail;
      new (nextStorage(&newTail)) ElementType(std::move(element));
      mQueue.mTail = newTail;
    }

   private:
    friend class AtomicSpscQueue;
    Producer(AtomicSpscQueue<ElementType, kCapacity> &q) : mQueue(q) {}

    AtomicSpscQueue<ElementType, kCapacity> &mQueue;

    //! Fetches a pointer to the next location where we should push an element,
    //! and updates bookkeeping for the next next location
    ElementType *nextStorage(uint32_t *newTail) {
      uint32_t tail = mQueue.mTail.load();
      if (tail != 0 && tail == mQueue.mHead.load()) {
        // We're empty, so reset both head and tail to 0 so it doesn't continue
        // to grow (and possibly overflow). Only do this when pushing, as this
        // is the only place we can guarantee that mHead is stable (there's
        // nothing for the consumer to retrieve, and attempting to pull from an
        // empty queue is UB) and mTail is too (we're in the producer context).
        // Note that we need to reset tail *first* to ensure size() is safe to
        // call from both contexts.
        mQueue.mTail = 0;
        mQueue.mHead = 0;
        tail = 0;
      } else {
        // If tail overflows (only possible if the producer *always* pushes a
        // new element while the consumer is reading, meaning that the queue
        // never gets fully emptied, and this continues until the tail pointer
        // reaches the max here), then size() will consider the queue empty and
        // things will get very broken.
        CHRE_ASSERT(tail < UINT32_MAX);
      }

      *newTail = tail + 1;
      return &mQueue.data()[tail % kCapacity];
    }
  };

  Producer producer() {
    return Producer(*this);
  }

  /**
   * Non-const methods within this class must ONLY be invoked from the consumer
   * execution context.
   */
  class Consumer {
   public:
    size_t capacity() const {
      return kCapacity;
    }
    bool empty() const {
      return (size() == 0);
    }
    size_t size() const {
      return mQueue.size();
    }

    /**
     * Retrieves a reference to the oldest element in the queue.
     *
     * WARNING: Undefined behavior if the queue is currently empty
     */
    ElementType &front() {
      return mQueue.data()[mQueue.mHead.load() % kCapacity];
    }
    const ElementType &front() const {
      return mQueue.data()[mQueue.mHead.load() % kCapacity];
    }

    /**
     * Removes the oldest element in the queue.
     *
     * WARNING: Undefined behavior if the queue is currently empty
     */
    void pop() {
      // Destructing prior to moving the head pointer is safe as long as this
      // doesn't interleave with other Producer methods
      uint32_t headRaw = mQueue.mHead;
      uint32_t headIndex = headRaw % kCapacity;
      mQueue.data()[headIndex].~ElementType();
      mQueue.mHead = headRaw + 1;
    }

    /**
     * Moves or copies a block of elements into the provided (possibly
     * uninitialized) destination storage.
     *
     * Safe to call if the queue is currently empty (includes an internal
     * check).
     *
     * @param dest Pointer to destination array
     * @param count Maximum number of elements to extract
     *
     * @return Number of elements actually pulled out of the queue.
     */
    size_t extract(ElementType *dest, size_t count) {
      size_t elementsToCopy = std::min(mQueue.size(), count);
      return extractInternal(dest, elementsToCopy);
    }

    //! Equivalent to extract(ElementType*, size_t) but appends to the provided
    //! FixedSizeVector up to its capacity
    template <size_t kDestCapacity>
    size_t extract(FixedSizeVector<ElementType, kDestCapacity> *dest) {
      size_t destIndex = dest->size();
      size_t elementsToCopy =
          std::min(mQueue.size(), dest->capacity() - destIndex);

      dest->resize(destIndex + elementsToCopy);
      return extractInternal(&dest->data()[destIndex], elementsToCopy);
    }

   private:
    friend class AtomicSpscQueue;
    Consumer(AtomicSpscQueue<ElementType, kCapacity> &q) : mQueue(q) {}

    AtomicSpscQueue<ElementType, kCapacity> &mQueue;

    size_t extractInternal(ElementType *dest, size_t elementsToCopy) {
      if (elementsToCopy > 0) {
        uint32_t headRaw = mQueue.mHead;
        size_t headIndex = headRaw % kCapacity;

        size_t firstCopy = std::min(elementsToCopy, kCapacity - headIndex);
        uninitializedMoveOrCopy(&mQueue.data()[headIndex], firstCopy, dest);
        destroy(&mQueue.data()[headIndex], firstCopy);

        if (firstCopy != elementsToCopy) {
          size_t secondCopy = elementsToCopy - firstCopy;
          uninitializedMoveOrCopy(&mQueue.data()[0], secondCopy,
                                  &dest[firstCopy]);
          destroy(&mQueue.data()[0], secondCopy);
        }

        mQueue.mHead = headRaw + elementsToCopy;
      }

      return elementsToCopy;
    }
  };

  Consumer consumer() {
    return Consumer(*this);
  }

 protected:
  //! Index of the oldest element on the queue (first to be popped). If the
  //! queue is empty, this is equal to mTail (modulo kCapacity) *or* for a very
  //! brief time it may be greater than mTail (when we're resetting both to 0).
  chre::AtomicUint32 mHead{0};

  //! Indicator of where we will push the next element -- to provide atomic
  //! behavior, this may exceed kCapacity, so modulo kCapacity is needed to
  //! convert this into an array index.
  chre::AtomicUint32 mTail{0};

  typename std::aligned_storage<sizeof(ElementType), alignof(ElementType)>::type
      mData[kCapacity];

  ElementType *data() {
    return reinterpret_cast<ElementType *>(mData);
  }
};

}  // namespace chre

#endif  // CHRE_UTIL_ATOMIC_SPSC_QUEUE_H_
