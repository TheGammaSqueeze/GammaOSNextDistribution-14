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

#ifndef CHRE_UTIL_ARRAY_QUEUE_IMPL_H_
#define CHRE_UTIL_ARRAY_QUEUE_IMPL_H_

#include <new>
#include <utility>

#include "chre/util/array_queue.h"
#include "chre/util/container_support.h"

namespace chre {
namespace internal {

template <typename ElementType, typename StorageType>
ArrayQueueCore<ElementType, StorageType>::~ArrayQueueCore() {
  clear();
}

template <typename ElementType, typename StorageType>
bool ArrayQueueCore<ElementType, StorageType>::empty() const {
  return (mSize == 0);
}

template <typename ElementType, typename StorageType>
bool ArrayQueueCore<ElementType, StorageType>::full() const {
  return (mSize == StorageType::capacity());
}

template <typename ElementType, typename StorageType>
size_t ArrayQueueCore<ElementType, StorageType>::size() const {
  return mSize;
}

template <typename ElementType, typename StorageType>
ElementType &ArrayQueueCore<ElementType, StorageType>::front() {
  CHRE_ASSERT(mSize > 0);
  return StorageType::data()[mHead];
}

template <typename ElementType, typename StorageType>
const ElementType &ArrayQueueCore<ElementType, StorageType>::front() const {
  CHRE_ASSERT(mSize > 0);
  return StorageType::data()[mHead];
}

template <typename ElementType, typename StorageType>
ElementType &ArrayQueueCore<ElementType, StorageType>::back() {
  CHRE_ASSERT(mSize > 0);
  return StorageType::data()[mTail];
}

template <typename ElementType, typename StorageType>
const ElementType &ArrayQueueCore<ElementType, StorageType>::back() const {
  CHRE_ASSERT(mSize > 0);
  return StorageType::data()[mTail];
}

template <typename ElementType, typename StorageType>
ElementType &ArrayQueueCore<ElementType, StorageType>::operator[](
    size_t index) {
  CHRE_ASSERT(index < mSize);
  return StorageType::data()[relativeIndexToAbsolute(index)];
}

template <typename ElementType, typename StorageType>
const ElementType &ArrayQueueCore<ElementType, StorageType>::operator[](
    size_t index) const {
  CHRE_ASSERT(index < mSize);
  return StorageType::data()[relativeIndexToAbsolute(index)];
}

template <typename ElementType, typename StorageType>
bool ArrayQueueCore<ElementType, StorageType>::push(
    const ElementType &element) {
  bool success = pushTail();
  if (success) {
    new (&StorageType::data()[mTail]) ElementType(element);
  }
  return success;
}

template <typename ElementType, typename StorageType>
bool ArrayQueueCore<ElementType, StorageType>::push(ElementType &&element) {
  bool success = pushTail();
  if (success) {
    new (&StorageType::data()[mTail]) ElementType(std::move(element));
  }
  return success;
}

template <typename ElementType, typename StorageType>
void ArrayQueueCore<ElementType, StorageType>::kick_push(
    const ElementType &element) {
  if (full()) {
    pop();
  }
  push(element);
}

template <typename ElementType, typename StorageType>
void ArrayQueueCore<ElementType, StorageType>::kick_push(
    ElementType &&element) {
  if (full()) {
    pop();
  }
  push(std::move(element));
}

template <typename ElementType, typename StorageType>
void ArrayQueueCore<ElementType, StorageType>::pop() {
  if (mSize > 0) {
    StorageType::data()[mHead].~ElementType();
    pullHead();
  }
}

template <typename ElementType, typename StorageType>
void ArrayQueueCore<ElementType, StorageType>::pop_back() {
  if (mSize > 0) {
    StorageType::data()[mTail].~ElementType();
    pullTail();
  }
}

// Assuming popping from the middle of the queue is rare, part of the
// array is copied over.
template <typename ElementType, typename StorageType>
bool ArrayQueueCore<ElementType, StorageType>::remove(size_t index) {
  // If we used memmove to shift the array down when removing an element in the
  // middle of the queue, then we'd need to add this somewhere:
  // static_assert(std::is_trivially_copyable<ElementType>::value,
  //               "Elements within ArrayQueue must be trivially copyable");

  bool success = true;
  if (index >= mSize) {
    success = false;
  } else if (index > mSize / 2) {
    removeAndPullTail(index);
  } else {
    removeAndPullHead(index);
  }
  return success;
}

template <typename ElementType, typename StorageType>
template <typename... Args>
bool ArrayQueueCore<ElementType, StorageType>::emplace(Args &&...args) {
  bool success = pushTail();
  if (success) {
    new (&StorageType::data()[mTail]) ElementType(std::forward<Args>(args)...);
  }
  return success;
}

template <typename ElementType, typename StorageType>
void ArrayQueueCore<ElementType, StorageType>::clear() {
  if (!std::is_trivially_destructible<ElementType>::value) {
    while (!empty()) {
      pop();
    }
  } else {
    mSize = 0;
    mHead = 0;
    mTail = StorageType::capacity() - 1;
  }
}

template <typename ElementType, typename StorageType>
typename ArrayQueueCore<ElementType, StorageType>::iterator
ArrayQueueCore<ElementType, StorageType>::begin() {
  // Align begin() and end() outside of the memory block when empty.
  return empty() ? end()
                 : iterator(StorageType::data() + mHead, StorageType::data(),
                            mTail, StorageType::capacity());
}

template <typename ElementType, typename StorageType>
typename ArrayQueueCore<ElementType, StorageType>::iterator
ArrayQueueCore<ElementType, StorageType>::end() {
  return iterator(StorageType::data() + StorageType::capacity(),
                  StorageType::data(), mTail, StorageType::capacity());
}

template <typename ElementType, typename StorageType>
typename ArrayQueueCore<ElementType, StorageType>::const_iterator
ArrayQueueCore<ElementType, StorageType>::begin() const {
  return cbegin();
}

template <typename ElementType, typename StorageType>
typename ArrayQueueCore<ElementType, StorageType>::const_iterator
ArrayQueueCore<ElementType, StorageType>::end() const {
  return cend();
}

template <typename ElementType, typename StorageType>
typename ArrayQueueCore<ElementType, StorageType>::const_iterator
ArrayQueueCore<ElementType, StorageType>::cbegin() const {
  // Align begin() and end() outside of the memory block when empty.
  return empty()
             ? cend()
             : const_iterator(StorageType::data() + mHead, StorageType::data(),
                              mTail, StorageType::capacity());
}

template <typename ElementType, typename StorageType>
typename ArrayQueueCore<ElementType, StorageType>::const_iterator
ArrayQueueCore<ElementType, StorageType>::cend() const {
  return const_iterator(StorageType::data() + StorageType::capacity(),
                        StorageType::data(), mTail, StorageType::capacity());
}

template <typename ElementType, typename StorageType>
size_t ArrayQueueCore<ElementType, StorageType>::relativeIndexToAbsolute(
    size_t index) const {
  size_t absoluteIndex = mHead + index;
  if (absoluteIndex >= StorageType::capacity()) {
    absoluteIndex -= StorageType::capacity();
  }
  return absoluteIndex;
}

template <typename ElementType, typename StorageType>
size_t ArrayQueueCore<ElementType, StorageType>::absoluteIndexToRelative(
    size_t index) const {
  if (mHead > index) {
    index += StorageType::capacity();
  }
  return index - mHead;
}

template <typename ElementType, typename StorageType>
void ArrayQueueCore<ElementType, StorageType>::removeAndPullTail(size_t index) {
  CHRE_ASSERT(index < mSize);

  size_t gapAbsolute = relativeIndexToAbsolute(index);
  size_t tailSize = absoluteIndexToRelative(mTail) - index;
  size_t nextAbsolute = advanceOrWrapAround(gapAbsolute);
  StorageType::data()[gapAbsolute].~ElementType();
  for (size_t i = 0; i < tailSize; ++i) {
    StorageType::data()[gapAbsolute] = StorageType::data()[nextAbsolute];
    gapAbsolute = nextAbsolute;
    nextAbsolute = advanceOrWrapAround(nextAbsolute);
  }
  mTail = subtractOrWrapAround(mTail);
  --mSize;
}

template <typename ElementType, typename StorageType>
void ArrayQueueCore<ElementType, StorageType>::removeAndPullHead(size_t index) {
  CHRE_ASSERT(index < mSize);

  size_t gapAbsolute = relativeIndexToAbsolute(index);
  size_t headSize = index;
  size_t prevAbsolute = subtractOrWrapAround(gapAbsolute);
  StorageType::data()[gapAbsolute].~ElementType();
  for (size_t i = 0; i < headSize; ++i) {
    StorageType::data()[gapAbsolute] = StorageType::data()[prevAbsolute];
    gapAbsolute = prevAbsolute;
    prevAbsolute = subtractOrWrapAround(prevAbsolute);
  }
  mHead = advanceOrWrapAround(mHead);
  --mSize;
}

template <typename ElementType, typename StorageType>
void ArrayQueueCore<ElementType, StorageType>::pullHead() {
  CHRE_ASSERT(mSize > 0);
  if (++mHead == StorageType::capacity()) {
    mHead = 0;
  }
  mSize--;
}

template <typename ElementType, typename StorageType>
void ArrayQueueCore<ElementType, StorageType>::pullTail() {
  CHRE_ASSERT(mSize > 0);
  if (mTail == 0) {
    mTail = StorageType::capacity() - 1;
  } else {
    mTail--;
  }
  mSize--;
}

template <typename ElementType, typename StorageType>
bool ArrayQueueCore<ElementType, StorageType>::pushTail() {
  bool success;
  if (mSize >= StorageType::capacity()) {
    success = false;
  } else {
    if (++mTail == StorageType::capacity()) {
      mTail = 0;
    }
    mSize++;
    success = true;
  }
  return success;
}

}  // namespace internal
}  // namespace chre

#endif  // CHRE_UTIL_ARRAY_QUEUE_IMPL_H_
