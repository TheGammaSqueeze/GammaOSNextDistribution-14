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

#ifndef CHRE_UTIL_SYNCHRONIZED_EXPANDABLE_MEMORY_POOL_IMPL_H_
#define CHRE_UTIL_SYNCHRONIZED_EXPANDABLE_MEMORY_POOL_IMPL_H_

#include <algorithm>

#include "chre/util/lock_guard.h"
#include "chre/util/memory_pool.h"
#include "chre/util/synchronized_expandable_memory_pool.h"

namespace chre {

template <typename ElementType, size_t kMemoryPoolSize,
          size_t kMaxMemoryPoolCount>
SynchronizedExpandableMemoryPool<ElementType, kMemoryPoolSize,
                                 kMaxMemoryPoolCount>::
    SynchronizedExpandableMemoryPool(size_t staticBlockCount)
    : kStaticBlockCount(staticBlockCount) {
  CHRE_ASSERT(staticBlockCount > 0);
  CHRE_ASSERT(kMaxMemoryPoolCount >= staticBlockCount);
  for (uint8_t i = 0; i < kStaticBlockCount; i++) {
    pushOneBlock();
  }
}

template <typename ElementType, size_t kMemoryPoolSize,
          size_t kMaxMemoryPoolCount>
template <typename... Args>
ElementType *SynchronizedExpandableMemoryPool<
    ElementType, kMemoryPoolSize,
    kMaxMemoryPoolCount>::allocate(Args &&...args) {
  LockGuard<Mutex> lock(mMutex);
  ElementType *result = nullptr;

  // TODO(b/259286151): Optimizing using pointer to a non-full block
  for (UniquePtr<Block> &memoryPool : mMemoryPoolPtrs) {
    result = memoryPool->allocate(args...);
    if (result != nullptr) {
      break;
    }
  }

  if (result == nullptr && pushOneBlock()) {
    result = mMemoryPoolPtrs.back()->allocate(args...);
  }

  if (result != nullptr) {
    ++mSize;
  }

  return result;
}

template <typename ElementType, size_t kMemoryPoolSize,
          size_t kMaxMemoryPoolCount>
void SynchronizedExpandableMemoryPool<
    ElementType, kMemoryPoolSize,
    kMaxMemoryPoolCount>::deallocate(ElementType *element) {
  bool success = false;
  LockGuard<Mutex> lock(mMutex);
  for (UniquePtr<Block> &memoryPool : mMemoryPoolPtrs) {
    if (memoryPool->containsAddress(element)) {
      success = true;
      memoryPool->deallocate(element);
      break;
    }
  }
  if (!success) {
    CHRE_ASSERT(false);
  } else {
    --mSize;
    while (
        mMemoryPoolPtrs.size() > std::max(kStaticBlockCount, size_t(1)) &&
        mMemoryPoolPtrs.back()->empty() &&
        !isHalfFullBlock(mMemoryPoolPtrs[mMemoryPoolPtrs.size() - 2].get())) {
      mMemoryPoolPtrs.pop_back();
    }
  }
}

template <typename ElementType, size_t kMemoryPoolSize,
          size_t kMaxMemoryPoolCount>
size_t SynchronizedExpandableMemoryPool<
    ElementType, kMemoryPoolSize, kMaxMemoryPoolCount>::getFreeSpaceCount() {
  LockGuard<Mutex> lock(mMutex);
  return kMaxMemoryPoolCount * kMemoryPoolSize - mSize;
}

template <typename ElementType, size_t kMemoryPoolSize,
          size_t kMaxMemoryPoolCount>
bool SynchronizedExpandableMemoryPool<ElementType, kMemoryPoolSize,
                                      kMaxMemoryPoolCount>::full() {
  LockGuard<Mutex> lock(mMutex);
  return kMaxMemoryPoolCount * kMemoryPoolSize == mSize;
}

template <typename ElementType, size_t kMemoryPoolSize,
          size_t kMaxMemoryPoolCount>
size_t SynchronizedExpandableMemoryPool<ElementType, kMemoryPoolSize,
                                        kMaxMemoryPoolCount>::getBlockCount() {
  LockGuard<Mutex> lock(mMutex);
  return mMemoryPoolPtrs.size();
}

template <typename ElementType, size_t kMemoryPoolSize,
          size_t kMaxMemoryPoolCount>
bool SynchronizedExpandableMemoryPool<ElementType, kMemoryPoolSize,
                                      kMaxMemoryPoolCount>::pushOneBlock() {
  bool success = false;
  if (mMemoryPoolPtrs.size() < kMaxMemoryPoolCount) {
    auto newBlock = MakeUnique<Block>();
    if (!newBlock.isNull()) {
      success = true;
      mMemoryPoolPtrs.push_back(std::move(newBlock));
    }
  }

  if (!success) {
    LOG_OOM();
  }

  return success;
}

template <typename ElementType, size_t kMemoryPoolSize,
          size_t kMaxMemoryPoolCount>
bool SynchronizedExpandableMemoryPool<
    ElementType, kMemoryPoolSize,
    kMaxMemoryPoolCount>::isHalfFullBlock(Block *block) {
  return block->getFreeBlockCount() < kMemoryPoolSize / 2;
}
}  // namespace chre

#endif  // CHRE_UTIL_SYNCHRONIZED_EXPANDABLE_MEMORY_POOL_IMPL_H_
