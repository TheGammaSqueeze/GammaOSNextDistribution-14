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

#ifndef CHRE_UTIL_MEMORY_POOL_IMPL_H_
#define CHRE_UTIL_MEMORY_POOL_IMPL_H_

#include <cinttypes>
#include <utility>

#include "chre/util/container_support.h"
#include "chre/util/memory_pool.h"

namespace chre {
template <typename ElementType, size_t kSize>
MemoryPool<ElementType, kSize>::MemoryPool() {
  // Initialize the free block list. The initial condition is such that each
  // block points to the next as being empty. The mFreeBlockCount is used to
  // ensure that we never allocate out of bounds so we don't need to worry about
  // the last block referring to one that is non-existent.
  for (size_t i = 0; i < kSize; i++) {
    blocks()[i].mNextFreeBlockIndex = i + 1;
  }
}

template <typename ElementType, size_t kSize>
template <typename... Args>
ElementType *MemoryPool<ElementType, kSize>::allocate(Args &&... args) {
  if (mFreeBlockCount == 0) {
    return nullptr;
  }

  size_t blockIndex = mNextFreeBlockIndex;
  mNextFreeBlockIndex = blocks()[blockIndex].mNextFreeBlockIndex;
  mFreeBlockCount--;

  return new (&blocks()[blockIndex].mElement)
      ElementType(std::forward<Args>(args)...);
}

template <typename ElementType, size_t kSize>
void MemoryPool<ElementType, kSize>::deallocate(ElementType *element) {
  size_t blockIndex;
  CHRE_ASSERT(getBlockIndex(element, &blockIndex));

  blocks()[blockIndex].mElement.~ElementType();
  blocks()[blockIndex].mNextFreeBlockIndex = mNextFreeBlockIndex;
  mNextFreeBlockIndex = blockIndex;
  mFreeBlockCount++;
}

template <typename ElementType, size_t kSize>
bool MemoryPool<ElementType, kSize>::containsAddress(ElementType *element) {
  size_t temp;
  return getBlockIndex(element, &temp);
}

template <typename ElementType, size_t kSize>
bool MemoryPool<ElementType, kSize>::getBlockIndex(ElementType *element,
                                                   size_t *indexOutput) {
  uintptr_t elementAddress = reinterpret_cast<uintptr_t>(element);
  uintptr_t baseAddress = reinterpret_cast<uintptr_t>(&blocks()[0].mElement);
  *indexOutput = (elementAddress - baseAddress) / sizeof(MemoryPoolBlock);

  return elementAddress >= baseAddress &&
         elementAddress <=
             reinterpret_cast<uintptr_t>(&blocks()[kSize - 1].mElement) &&
         ((elementAddress - baseAddress) % sizeof(MemoryPoolBlock) == 0);
}

template <typename ElementType, size_t kSize>
size_t MemoryPool<ElementType, kSize>::getFreeBlockCount() const {
  return mFreeBlockCount;
}

template <typename ElementType, size_t kSize>
typename MemoryPool<ElementType, kSize>::MemoryPoolBlock *
MemoryPool<ElementType, kSize>::blocks() {
  return mBlocks.data();
}

}  // namespace chre

#endif  // CHRE_UTIL_MEMORY_POOL_IMPL_H_
