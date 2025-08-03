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

#ifndef CHRE_UTIL_ARRAY_QUEUE_H_
#define CHRE_UTIL_ARRAY_QUEUE_H_

#include <cstddef>
#include <iterator>
#include <type_traits>

#include "chre/util/non_copyable.h"
#include "chre/util/raw_storage.h"

/**
 * @file
 * ArrayQueue is a templated fixed-size FIFO queue implemented around a
 * contiguous array. Two variations on how the
 *
 *  1) ArrayQueue<ElementType, kCapacity> allocates the underlying array within
 *  the ArrayQueue object itself.
 *  2) ArrayQueueExt<ElementType> accepts a pointer to the storage at
 *  construction time. Since this variation maintains the capacity of the array
 *  as a member variable rather than template parameter, it can be useful in
 *  situations where it'd be inconvenient to include the array capacity in the
 *  type specification, for example when processing multiple array queues with
 *  different capacities in a loop or similar construct.
 *
 * This variability is accomplished through a base class which provides the
 * underlying storage, which is attached to the array queue implementation in
 * ArrayQueueCore via a template parameter, then the two storage options are
 * composed into public APIs as ArrayQueue and ArrayQueueExt. Users of this
 * container are not expected to reference ArrayQueueCore or ArrayQueue*Storage
 * directly, but developers should refer to ArrayQueueCore for API
 * documentation.
 */

namespace chre {

// Forward declaration to support declarations in ArrayQueueCore
template <typename ValueType>
class ArrayQueueIterator;

namespace internal {

/**
 * The core implementation of an array queue, from which the public interfaces
 * (ArrayQueue and ArrayQueueExt) are derived.
 *
 * The StorageType template parameter must be a class supplying data() and
 * capacity() methods used to access the underlying array storage.
 */
template <typename ElementType, class StorageType>
class ArrayQueueCore : public StorageType {
 public:
  // Inherit constructors from StorageType
  using StorageType::StorageType;

  /**
   * Calls the destructor of all the elements in the array queue.
   */
  ~ArrayQueueCore();

  // data() and capacity() functions are inherited from StorageType

  /**
   * @return true if the array queue is empty.
   */
  bool empty() const;

  /**
   * @return true if the array queue is full.
   */
  bool full() const;

  /**
   * @return The number of elements currently stored in the array queue.
   */
  size_t size() const;

  /**
   * Obtains the front element of the array queue. It is illegal to access the
   * front element when the array queue is empty. The user of the API must check
   * the size() or empty() function prior to accessing the front element to
   * ensure that they will not read out of bounds.
   *
   * @return The front element.
   */
  ElementType &front();
  const ElementType &front() const;

  /**
   * Obtains the last element in the queue. Illegal to call when empty() is
   * true.
   *
   * @return The last element in the queue.
   */
  ElementType &back();
  const ElementType &back() const;

  /**
   * Obtains an element of the array queue given an index. It is illegal to
   * index this array queue out of bounds and the user of the API must check the
   * size() function prior to indexing this array queue to ensure that they will
   * not read out of bounds.
   *
   * @param index Requested index in range [0,size()-1]
   * @return The element.
   */
  ElementType &operator[](size_t index);

  /**
   * Obtains an element of the array queue given an index. It is illegal to
   * index this array queue out of bounds and the user of the API must check the
   * size() function prior to indexing this array queue to ensure that they will
   * not read out of bounds.
   *
   * @param index Requested index in range [0,size()-1]
   * @return The element.
   */
  const ElementType &operator[](size_t index) const;

  /**
   * Pushes an element onto the back of the array queue via copy or move
   * construction. It returns false if the array queue is full already and there
   * is no room for the elements. All iterators and references are unaffected.
   *
   * @param element The element to push onto the array queue.
   * @return true if the element is pushed successfully.
   */
  bool push(const ElementType &element);
  bool push(ElementType &&element);

  /**
   * Pushes an element onto the back of the array queue via copy or move
   * construction. If the array queue is full the front element is removed
   * to make room for the new element.
   *
   * @param element The element to push onto the array queue.
   */
  void kick_push(const ElementType &element);
  void kick_push(ElementType &&element);

  /**
   * Removes the front element from the array queue if the array queue is not
   * empty. Only iterators and references to the front of the queue are
   * invalidated.
   */
  void pop();

  /**
   * Removes the back element from the array queue if the array queue is not
   * empty. Only iterators and references to the back of the queue are
   * invalidated.
   */
  void pop_back();

  /**
   * Removes an element from the array queue given an index. It returns false if
   * the array queue contains fewer items than the index. All iterators and
   * references to elements before the removed one are unaffected. Iterators
   * and references to the removed element or any elements after it are
   * invalidated.
   *
   * @param index Requested index in range [0,size()-1]
   * @return true if the indexed element has been removed successfully.
   */
  bool remove(size_t index);

  /**
   * Constructs an element onto the back of the array queue. All iterators and
   * references are unaffected.
   *
   * @param The arguments to the constructor
   * @return true if the element is constructed successfully.
   */
  template <typename... Args>
  bool emplace(Args &&... args);

  /**
   * Removes all the elements of the queue.
   */
  void clear();

  /**
   * Forward iterator that points to some element in the container.
   */
  typedef ArrayQueueIterator<ElementType> iterator;
  typedef ArrayQueueIterator<const ElementType> const_iterator;

  /**
   * @return A forward iterator to the beginning.
   */
  iterator begin();
  const_iterator begin() const;
  const_iterator cbegin() const;

  /**
   * @return A forward iterator to the end.
   */
  iterator end();
  const_iterator end() const;
  const_iterator cend() const;

 private:
  /*
   * Initialize mTail to be (capacity-1). When an element is pushed in,
   * mHead and mTail will align. Also, this is consistent with
   * mSize = (mTail - mHead)%capacity + 1 for mSize > 0.
   */
  //! Index of the front element
  size_t mHead = 0;

  //! Index of the back element
  size_t mTail = StorageType::capacity() - 1;

  //! Number of elements in the array queue
  size_t mSize = 0;

  /**
   * Converts relative index with respect to mHead to absolute index in the
   * storage array.
   *
   * @param index Relative index in range [0,size()-1]
   * @return The index of the storage array in range [0,kCapacity-1]
   */
  size_t relativeIndexToAbsolute(size_t index) const;

  /**
   * Converts absolute index to relative index with respect to mHead.
   *
   * @param index absolute index showing the offset to the head of storage
   * array.
   * @return size_t relative index in range [0, size() - 1].
   */
  size_t absoluteIndexToRelative(size_t index) const;

  /**
   * Removes an item at index and pull the tail forward to fill gap.
   *
   * @param index the relative index of the item to be remove, must be smaller
   * than mSize.
   */
  void removeAndPullTail(size_t index);

  /**
   * Removes an item at index and pull the head forward to fill gap.
   *
   * @param index the relative index of the item to be remove, must be smaller
   * than mSize.
   */
  void removeAndPullHead(size_t index);

  /*
   * Pulls mHead to the next element in the array queue and decrements mSize
   * accordingly. It is illegal to call this function on an empty array queue.
   */
  void pullHead();

  /*
   * Pulls mTail to the previous element in the array queue and decrements mSize
   * accordingly. It is illegal to call this function on an empty array queue.
   */
  void pullTail();

  /*
   * Pushes mTail to the next available storage space and increments mSize
   * accordingly.
   *
   * @return true if the array queue is not full.
   */
  bool pushTail();

  /**
   * Advance an index or wrap it around to the head of it is out of bound.
   *
   * @param index current index.
   * @return the calculated result.
   */
  size_t advanceOrWrapAround(size_t index) {
    return index >= StorageType::capacity() - 1 ? 0 : index + 1;
  }

  /**
   * Subtract an index or wrap it around to the end of it is out of bound.
   *
   * @param index current index.
   * @return the calculated result.
   */
  size_t subtractOrWrapAround(size_t index) {
    return index == 0 ? StorageType::capacity() - 1 : index - 1;
  }
};

/**
 * Storage for ArrayQueue based on a pointer to an array allocated elsewhere.
 */
template <typename ElementType>
class ArrayQueueExternalStorage : public NonCopyable {
 public:
  ArrayQueueExternalStorage(ElementType *storage, size_t capacity)
      : mData(storage), kCapacity(capacity) {}

  ElementType *data() {
    return mData;
  }

  const ElementType *data() const {
    return mData;
  }

  size_t capacity() const {
    return kCapacity;
  }

 private:
  ElementType *mData;
  const size_t kCapacity;
};

}  // namespace internal

/**
 * Alias to the array queue implementation with storage allocated inside the
 * object. This is the interface that most code is expected to use.
 */
template <typename ElementType, size_t kCapacity>
class ArrayQueue
    : public internal::ArrayQueueCore<ElementType,
                                      RawStorage<ElementType, kCapacity>> {
 public:
  typedef ElementType value_type;
};

/**
 * Wrapper for the array queue implementation with storage allocated elsewhere.
 * This is useful in instances where it's inconvenient to have the array's
 * capacity form part of the type specification.
 */
template <typename ElementType>
class ArrayQueueExt
    : public internal::ArrayQueueCore<
          ElementType, internal::ArrayQueueExternalStorage<ElementType>> {
 public:
  ArrayQueueExt(ElementType *storage, size_t capacity)
      : internal::ArrayQueueCore<
            ElementType, internal::ArrayQueueExternalStorage<ElementType>>(
            storage, capacity) {}
};

/**
 * A template class that implements a forward iterator for the array queue.
 */
template <typename ValueType>
class ArrayQueueIterator {
 public:
  typedef ValueType value_type;
  typedef ValueType &reference;
  typedef ValueType *pointer;
  typedef std::ptrdiff_t difference_type;
  typedef std::forward_iterator_tag iterator_category;

  ArrayQueueIterator() = default;
  ArrayQueueIterator(ValueType *start, ValueType *base, size_t tail,
                     size_t capacity)
      : mPointer(start), mBase(base), mTail(tail), mCapacity(capacity) {}

  bool operator==(const ArrayQueueIterator &right) const {
    return (mPointer == right.mPointer);
  }

  bool operator!=(const ArrayQueueIterator &right) const {
    return (mPointer != right.mPointer);
  }

  ValueType &operator*() {
    return *mPointer;
  }

  ValueType *operator->() {
    return mPointer;
  }

  ArrayQueueIterator &operator++() {
    if (mPointer == (mBase + mTail)) {
      // Jump to end() if at tail
      mPointer = mBase + mCapacity;
    } else if (mPointer == (mBase + mCapacity - 1)) {
      // Wrap around in the memory
      mPointer = mBase;
    } else {
      mPointer++;
    }
    return *this;
  }

  ArrayQueueIterator operator++(int) {
    ArrayQueueIterator it(*this);
    operator++();
    return it;
  }

 private:
  //! Pointer of the iterator.
  ValueType *mPointer;

  //! The memory base address of this container.
  ValueType *mBase;

  //! The tail offset relative to the memory base address.
  size_t mTail;

  //! Number of elements the underlying ArrayQueue can hold
  size_t mCapacity;
};

}  // namespace chre

#include "chre/util/array_queue_impl.h"

#endif  // CHRE_UTIL_ARRAY_QUEUE_H_
