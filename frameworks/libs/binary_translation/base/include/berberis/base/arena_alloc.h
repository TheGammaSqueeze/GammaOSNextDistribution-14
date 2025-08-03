/*
 * Copyright (C) 2015 The Android Open Source Project
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

#ifndef BERBERIS_BASE_ARENA_ALLOC_H_
#define BERBERIS_BASE_ARENA_ALLOC_H_

#include <cstddef>
#include <cstdint>
#include <new>

#include "berberis/base/bit_util.h"
#include "berberis/base/logging.h"
#include "berberis/base/mmap.h"
#include "berberis/base/mmap_pool.h"
#include "berberis/base/page_size.h"

namespace berberis {

namespace arena_internal {

// TODO(eaeltsin): tune for each guest arch?
inline constexpr size_t kDefaultArenaBlockSize = 32 * 4096;
inline constexpr size_t kMmapPoolSizeLimit = kDefaultArenaBlockSize * 16;
inline constexpr size_t kMaxAllocSizeInDefaultArenaBlock = 16 * 4096;

using MmapPoolForArena = MmapPool<kDefaultArenaBlockSize, kMmapPoolSizeLimit>;

struct ArenaBlock {
  const size_t size;
  ArenaBlock* next;

  uint8_t* data() { return reinterpret_cast<uint8_t*>(this) + sizeof(ArenaBlock); }
  uint8_t* data_end() { return reinterpret_cast<uint8_t*>(this) + size; }
};

inline ArenaBlock* AllocArenaBlock(size_t size, size_t align, ArenaBlock* blocks) {
  // Add header size.
  size += AlignUp(sizeof(ArenaBlock), align);

  // Pick between default and dedicated block sizes.
  if (size < kMaxAllocSizeInDefaultArenaBlock) {
    return new (MmapPoolForArena::Alloc()) ArenaBlock{kDefaultArenaBlockSize, blocks};
  } else {
    return new (MmapOrDie(size)) ArenaBlock{AlignUpPageSize(size), blocks};
  }
}

inline void FreeArenaBlocks(ArenaBlock* blocks) {
  while (blocks) {
    auto next = blocks->next;
    // It may happen that a big block was allocated with kDefaultArenaBlockSize.
    // It's still okay to push it to MmapPool.
    if (blocks->size == kDefaultArenaBlockSize) {
      MmapPoolForArena::Free(blocks);
    } else {
      MunmapOrDie(blocks, blocks->size);
    }
    blocks = next;
  }
}

}  // namespace arena_internal

// Arena is for placement of small objects with same lifetime (such as IR nodes in translation).
// Arena is NOT thread-safe!
class Arena {
 public:
  Arena() {}

  ~Arena() { arena_internal::FreeArenaBlocks(blocks_); }

  void* Alloc(size_t size, size_t align) {
    if (size == 0) {
      // STL allocators shall return distinct non-NULL values for
      // 0-sized allocations.
      size = 1;
    }

    // Allocate in current block.
    auto res = AlignUp(current_, align);
    if (res + size <= end_) {
      // Fits in the current block.
      current_ = res + size;
    } else {
      // Doesn't fit in the current block, allocate new block of sufficient size.
      blocks_ = arena_internal::AllocArenaBlock(size, align, blocks_);

      // Allocate in the new block.
      res = AlignUp(blocks_->data(), align);
      auto new_current = res + size;

      if (end_ - current_ < blocks_->data_end() - new_current) {
        // Current block has less space left than the new one.
        current_ = new_current;
        end_ = blocks_->data_end();
      }
    }

    return res;
  }

 private:
  arena_internal::ArenaBlock* blocks_ = nullptr;
  uint8_t* current_ = nullptr;
  uint8_t* end_ = nullptr;

  friend class ArenaTest;
};

template <typename T, typename... Args>
T* NewInArena(Arena* arena, Args... args) {
  void* ptr = arena->Alloc(sizeof(T), alignof(T));
  return new (ptr) T(args...);
}

template <typename T>
T* NewArrayInArena(Arena* arena, size_t size) {
  void* ptr = arena->Alloc(sizeof(T) * size, alignof(T));
  return new (ptr) T[size];
}

// ArenaAllocator is used for faster placement of STL containers.
template <class T>
class ArenaAllocator {
 public:
  using value_type = T;

  // Allow passing arena as allocator arg of STL container ctor.
  ArenaAllocator(Arena* arena) : arena_(arena) {}  // NOLINT(runtime/explicit)

  template <typename U>
  ArenaAllocator(const ArenaAllocator<U>& other) : arena_(other.arena()) {}

  T* allocate(size_t n) {
    size_t size = n * sizeof(T);
    return reinterpret_cast<T*>(arena()->Alloc(size, alignof(T)));
  }

  void deallocate(T*, size_t) {}

  bool operator==(const ArenaAllocator<T>& other) const { return arena() == other.arena(); }

  bool operator!=(const ArenaAllocator<T>& other) const { return arena() != other.arena(); }

  Arena* arena() const { return arena_; }

 private:
  Arena* arena_;
};

}  // namespace berberis

#endif  // BERBERIS_BASE_ARENA_ALLOC_H_
