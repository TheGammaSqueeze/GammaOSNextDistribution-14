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

#ifndef ART_RUNTIME_LINEAR_ALLOC_H_
#define ART_RUNTIME_LINEAR_ALLOC_H_

#include "base/arena_allocator.h"
#include "base/casts.h"
#include "base/macros.h"
#include "base/mutex.h"

namespace art HIDDEN {

class ArenaPool;

enum class LinearAllocKind : uint32_t {
  kNoGCRoots = 0,  // No GC-root kind should always be 0.
  kGCRootArray,
  kArtMethodArray,
  kArtFieldArray,
  kDexCacheArray,
  kArtMethod
};

// Header for every allocation in LinearAlloc. The header provides the type
// and size information to the GC for invoking the right visitor.
class TrackingHeader final {
 public:
  static constexpr uint32_t kIs16Aligned = 1;
  TrackingHeader(size_t size, LinearAllocKind kind, bool is_16_aligned = false)
      : kind_(kind), size_(dchecked_integral_cast<uint32_t>(size)) {
    // We need the last bit to store 16-byte alignment flag.
    CHECK_EQ(size_ & kIs16Aligned, 0u);
    if (is_16_aligned) {
      size_ |= kIs16Aligned;
    }
  }

  LinearAllocKind GetKind() const { return kind_; }
  // Since we are linearly allocating and hop from one object to the next during
  // visits, reading 'size_ == 0' indicates that there are no more objects to
  // visit in the given page. But ASAN detects it as use-after-poison access.
  ATTRIBUTE_NO_SANITIZE_ADDRESS size_t GetSize() const { return size_ & ~kIs16Aligned; }
  bool Is16Aligned() const { return size_ & kIs16Aligned; }

 private:
  void SetKind(LinearAllocKind kind) { kind_ = kind; }

  LinearAllocKind kind_;
  uint32_t size_;

  friend class LinearAlloc;  // For SetKind()

  DISALLOW_IMPLICIT_CONSTRUCTORS(TrackingHeader);
};

std::ostream& operator<<(std::ostream& os, LinearAllocKind value);

class LinearAlloc {
 public:
  static constexpr size_t kAlignment = 8u;
  static_assert(kAlignment >= ArenaAllocator::kAlignment);
  static_assert(sizeof(TrackingHeader) == ArenaAllocator::kAlignment);

  explicit LinearAlloc(ArenaPool* pool, bool track_allocs)
      : lock_("linear alloc"), allocator_(pool), track_allocations_(track_allocs) {}

  void* Alloc(Thread* self, size_t size, LinearAllocKind kind) REQUIRES(!lock_);
  void* AllocAlign16(Thread* self, size_t size, LinearAllocKind kind) REQUIRES(!lock_);

  // Realloc never frees the input pointer, it is the caller's job to do this if necessary.
  void* Realloc(Thread* self, void* ptr, size_t old_size, size_t new_size, LinearAllocKind kind)
      REQUIRES(!lock_);

  // Allocate an array of structs of type T.
  template<class T>
  T* AllocArray(Thread* self, size_t elements, LinearAllocKind kind) REQUIRES(!lock_) {
    return reinterpret_cast<T*>(Alloc(self, elements * sizeof(T), kind));
  }

  // Return the number of bytes used in the allocator.
  size_t GetUsedMemory() const REQUIRES(!lock_);

  ArenaPool* GetArenaPool() REQUIRES(!lock_);
  // Force arena allocator to ask for a new arena on next allocation. This
  // is to preserve private/shared clean pages across zygote fork.
  void SetupForPostZygoteFork(Thread* self) REQUIRES(!lock_);
  // Convert the given allocated object into a `no GC-root` so that compaction
  // skips it. Currently only used during class linking for ArtMethod array.
  void ConvertToNoGcRoots(void* ptr, LinearAllocKind orig_kind);

  // Return true if the linear alloc contains an address.
  bool Contains(void* ptr) const REQUIRES(!lock_);

  // Unsafe version of 'Contains' only to be used when the allocator is going
  // to be deleted.
  bool ContainsUnsafe(void* ptr) const NO_THREAD_SAFETY_ANALYSIS {
    return allocator_.Contains(ptr);
  }

  // Set the given object as the first object for all the pages where the
  // page-beginning overlaps with the object.
  void SetFirstObject(void* begin, size_t bytes) const REQUIRES(lock_);

 private:
  mutable Mutex lock_ DEFAULT_MUTEX_ACQUIRED_AFTER;
  ArenaAllocator allocator_ GUARDED_BY(lock_);
  const bool track_allocations_;

  DISALLOW_IMPLICIT_CONSTRUCTORS(LinearAlloc);
};

}  // namespace art

#endif  // ART_RUNTIME_LINEAR_ALLOC_H_
