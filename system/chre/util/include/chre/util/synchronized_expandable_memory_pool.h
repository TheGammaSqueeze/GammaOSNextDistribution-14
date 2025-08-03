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

#ifndef CHRE_UTIL_SYNCHRONIZED_EXPANDABLE_MEMORY_POOL_H_
#define CHRE_UTIL_SYNCHRONIZED_EXPANDABLE_MEMORY_POOL_H_

#include "chre/platform/mutex.h"
#include "chre/util/fixed_size_vector.h"
#include "chre/util/memory_pool.h"
#include "chre/util/unique_ptr.h"

namespace chre {

/**
 * This is a similar data structure to chre::SynchronizedMemoryPool with a more
 * optimized memory usage. This data structure will allocate new storage as
 * needed and in segments as appose to SynchronizedMemoryPool that allocates the
 * entire storage once and as a continuous block. It will also deallocates
 * storage once it's not used for a long time to balance memory usage and
 * thrashing. These properties lead to a lower memory usage in average time and
 * also prevents heap fragmentation.
 *
 * @tparam ElementType the element to store in ths expandable memory pool.
 * @tparam kMemoryPoolSize the size of each element pool (each block).
 * @tparam kMaxMemoryPoolCount the maximum number of memory blocks.
 */
template <typename ElementType, size_t kMemoryPoolSize,
          size_t kMaxMemoryPoolCount>
class SynchronizedExpandableMemoryPool : public NonCopyable {
  using Block = ::chre::MemoryPool<ElementType, kMemoryPoolSize>;
  static_assert(kMemoryPoolSize > 0);

 public:
  /**
   * Construct a new Synchronized Expandable Memory Pool object.
   * The maximum memory that this pool will allocate is maxMemoryPoolCount
   * kMemoryPoolSize * sizeof(ElementType).
   *
   * @param staticBlockCount the number of blocks that will be allocate in the
   * constructor and will only be deallocate by the destructor. Needs to be
   * bigger than zero to avoid thrashing.
   */
  SynchronizedExpandableMemoryPool(size_t staticBlockCount = 1);

  /**
   * Allocates space for an object, constructs it and returns the pointer to
   * that object. This method is thread-safe and a lock will be acquired
   * upon entry to this method.
   *
   * @param  The arguments to be forwarded to the constructor of the object.
   * @return A pointer to a constructed object or nullptr if the allocation
   *         fails.
   */
  template <typename... Args>
  ElementType *allocate(Args &&...args);

  /**
   * Releases the memory of a previously allocated element. The pointer provided
   * here must be one that was produced by a previous call to the allocate()
   * function. The destructor is invoked on the object. This method is
   * thread-safe and a lock will be acquired upon entry to this method.
   *
   * @param A pointer to an element that was previously allocated by the
   *        allocate() function.
   */
  void deallocate(ElementType *element);

  /**
   * @return the number of new element that this memory pool can add.
   */
  size_t getFreeSpaceCount();

  /**
   * @return size_t Return the number of blocks currently in the memory pool.
   */
  size_t getBlockCount();

  /**
   * @return if the memory pool is full.
   */
  inline bool full();

 private:
  //! Number of blocks that will be allocate in the beginning and will only be
  //! deallocate by the destructor.
  const size_t kStaticBlockCount;

  //! Number of elements that this memory pool currently hold.
  size_t mSize = 0;

  //! The mutex used to guard access to this memory pool.
  Mutex mMutex;

  //! A fixed sized container that wraps around non-synchronized
  //! MemoryPools used to implement this thread-safe and expandable version
  //! version.
  FixedSizeVector<UniquePtr<Block>, kMaxMemoryPoolCount> mMemoryPoolPtrs;

  /**
   * Push one memory pool to the end of the vector.
   *
   * @return true if a new memory pool can be allocated.
   */
  bool pushOneBlock();

  /**
   * @return true if this block is more than half full.
   */
  bool isHalfFullBlock(Block *block);
};

}  // namespace chre

#include "chre/util/synchronized_expandable_memory_pool_impl.h"

#endif  // CHRE_UTIL_SYNCHRONIZED_EXPANDABLE_MEMORY_POOL_H_
