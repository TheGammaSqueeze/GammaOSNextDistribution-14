/*
 * Copyright (C) 2013 The Android Open Source Project
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

#ifndef ART_RUNTIME_GC_SPACE_BUMP_POINTER_SPACE_H_
#define ART_RUNTIME_GC_SPACE_BUMP_POINTER_SPACE_H_

#include "base/mutex.h"
#include "space.h"

#include <deque>

namespace art HIDDEN {

namespace mirror {
class Object;
}

namespace gc {

namespace collector {
class MarkCompact;
class MarkSweep;
}  // namespace collector

namespace space {

// A bump pointer space allocates by incrementing a pointer, it doesn't provide a free
// implementation as its intended to be evacuated.
class EXPORT BumpPointerSpace final : public ContinuousMemMapAllocSpace {
 public:
  using WalkCallback = void (*)(void *, void *, int, void *);

  SpaceType GetType() const override {
    return kSpaceTypeBumpPointerSpace;
  }

  // Create a bump pointer space with the requested sizes. The requested base address is not
  // guaranteed to be granted, if it is required, the caller should call Begin on the returned
  // space to confirm the request was granted.
  static BumpPointerSpace* Create(const std::string& name, size_t capacity);
  static BumpPointerSpace* CreateFromMemMap(const std::string& name, MemMap&& mem_map);

  // Allocate num_bytes, returns null if the space is full.
  mirror::Object* Alloc(Thread* self, size_t num_bytes, size_t* bytes_allocated,
                        size_t* usable_size, size_t* bytes_tl_bulk_allocated) override;
  // Thread-unsafe allocation for when mutators are suspended, used by the semispace collector.
  mirror::Object* AllocThreadUnsafe(Thread* self, size_t num_bytes, size_t* bytes_allocated,
                                    size_t* usable_size, size_t* bytes_tl_bulk_allocated)
      override REQUIRES(Locks::mutator_lock_);

  mirror::Object* AllocNonvirtual(size_t num_bytes);
  mirror::Object* AllocNonvirtualWithoutAccounting(size_t num_bytes);

  // Return the storage space required by obj.
  size_t AllocationSize(mirror::Object* obj, size_t* usable_size) override
      REQUIRES_SHARED(Locks::mutator_lock_) {
    return AllocationSizeNonvirtual(obj, usable_size);
  }

  // NOPS unless we support free lists.
  size_t Free(Thread*, mirror::Object*) override {
    return 0;
  }

  size_t FreeList(Thread*, size_t, mirror::Object**) override {
    return 0;
  }

  size_t AllocationSizeNonvirtual(mirror::Object* obj, size_t* usable_size)
      REQUIRES_SHARED(Locks::mutator_lock_);

  // Removes the fork time growth limit on capacity, allowing the application to allocate up to the
  // maximum reserved size of the heap.
  void ClearGrowthLimit() {
    growth_end_ = Limit();
  }

  // Attempts to clamp the space limit to 'new_capacity'. If not possible, then
  // clamps to whatever possible. Returns the new capacity. 'lock_' is used to
  // ensure that TLAB allocations, which are the only ones which may be happening
  // concurrently with this function are synchronized. The other Alloc* functions
  // are either used in single-threaded mode, or when used in multi-threaded mode,
  // then the space is used by GCs (like SS)  which don't have clamping implemented.
  size_t ClampGrowthLimit(size_t new_capacity) REQUIRES(!lock_);

  // Override capacity so that we only return the possibly limited capacity
  size_t Capacity() const override {
    return growth_end_ - begin_;
  }

  // The total amount of memory reserved for the space.
  size_t NonGrowthLimitCapacity() const override {
    return GetMemMap()->Size();
  }

  accounting::ContinuousSpaceBitmap* GetLiveBitmap() override {
    return nullptr;
  }

  // Reset the space to empty.
  void Clear() override REQUIRES(!lock_);

  void Dump(std::ostream& os) const override;

  size_t RevokeThreadLocalBuffers(Thread* thread) override REQUIRES(!lock_);
  size_t RevokeAllThreadLocalBuffers() override
      REQUIRES(!Locks::runtime_shutdown_lock_, !Locks::thread_list_lock_, !lock_);
  void AssertThreadLocalBuffersAreRevoked(Thread* thread) REQUIRES(!lock_);
  void AssertAllThreadLocalBuffersAreRevoked()
      REQUIRES(!Locks::runtime_shutdown_lock_, !Locks::thread_list_lock_, !lock_);

  uint64_t GetBytesAllocated() override REQUIRES_SHARED(Locks::mutator_lock_)
      REQUIRES(!*Locks::runtime_shutdown_lock_, !*Locks::thread_list_lock_, !lock_);
  uint64_t GetObjectsAllocated() override REQUIRES_SHARED(Locks::mutator_lock_)
      REQUIRES(!*Locks::runtime_shutdown_lock_, !*Locks::thread_list_lock_, !lock_);
  // Return the pre-determined allocated object count. This could be beneficial
  // when we know that all the TLABs are revoked.
  int32_t GetAccumulatedObjectsAllocated() REQUIRES_SHARED(Locks::mutator_lock_) {
    return objects_allocated_.load(std::memory_order_relaxed);
  }
  bool IsEmpty() const {
    return Begin() == End();
  }

  bool CanMoveObjects() const override {
    return true;
  }

  // TODO: Change this? Mainly used for compacting to a particular region of memory.
  BumpPointerSpace(const std::string& name, uint8_t* begin, uint8_t* limit);

  // Allocate a new TLAB and updates bytes_tl_bulk_allocated with the
  // allocation-size, returns false if the allocation failed.
  bool AllocNewTlab(Thread* self, size_t bytes, size_t* bytes_tl_bulk_allocated) REQUIRES(!lock_);

  BumpPointerSpace* AsBumpPointerSpace() override {
    return this;
  }

  // Go through all of the blocks and visit the continuous objects.
  template <typename Visitor>
  ALWAYS_INLINE void Walk(Visitor&& visitor) REQUIRES_SHARED(Locks::mutator_lock_) REQUIRES(!lock_);

  accounting::ContinuousSpaceBitmap::SweepCallback* GetSweepCallback() override;

  // Record objects / bytes freed.
  void RecordFree(int32_t objects, int32_t bytes) {
    objects_allocated_.fetch_sub(objects, std::memory_order_relaxed);
    bytes_allocated_.fetch_sub(bytes, std::memory_order_relaxed);
  }

  bool LogFragmentationAllocFailure(std::ostream& os, size_t failed_alloc_bytes) override
      REQUIRES_SHARED(Locks::mutator_lock_);

  // Object alignment within the space.
  static constexpr size_t kAlignment = kObjectAlignment;

 protected:
  BumpPointerSpace(const std::string& name, MemMap&& mem_map);

  // Allocate a raw block of bytes.
  uint8_t* AllocBlock(size_t bytes) REQUIRES(lock_);
  void RevokeThreadLocalBuffersLocked(Thread* thread) REQUIRES(lock_);

  // The main block is an unbounded block where objects go when there are no other blocks. This
  // enables us to maintain tightly packed objects when you are not using thread local buffers for
  // allocation. The main block starts at the space Begin().
  void UpdateMainBlock() REQUIRES(lock_);

  uint8_t* growth_end_;
  AtomicInteger objects_allocated_;  // Accumulated from revoked thread local regions.
  AtomicInteger bytes_allocated_;  // Accumulated from revoked thread local regions.
  Mutex lock_ DEFAULT_MUTEX_ACQUIRED_AFTER;
  // The objects at the start of the space are stored in the main block.
  size_t main_block_size_ GUARDED_BY(lock_);
  // List of block sizes (in bytes) after the main-block. Needed for Walk().
  // If empty then the space has only one long continuous block. Each TLAB
  // allocation has one entry in this deque.
  // Keeping block-sizes off-heap simplifies sliding compaction algorithms.
  // The compaction algorithm should ideally compact all objects into the main
  // block, thereby enabling erasing corresponding entries from here.
  std::deque<size_t> block_sizes_ GUARDED_BY(lock_);

 private:
  // Return the object which comes after obj, while ensuring alignment.
  static mirror::Object* GetNextObject(mirror::Object* obj)
      REQUIRES_SHARED(Locks::mutator_lock_);

  // Return a vector of block sizes on the space. Required by MarkCompact GC for
  // walking black objects allocated after marking phase.
  std::vector<size_t>* GetBlockSizes(Thread* self, size_t* main_block_size) REQUIRES(!lock_);

  // Once the MarkCompact decides the post-compact layout of the space in the
  // pre-compaction pause, it calls this function to update the block sizes. It is
  // done by passing the new main-block size, which consumes a bunch of blocks
  // into itself, and the index of first unconsumed block. This works as all the
  // block sizes are ordered. Also updates 'end_' to reflect the change.
  void SetBlockSizes(Thread* self, const size_t main_block_size, const size_t first_valid_idx)
      REQUIRES(!lock_, Locks::mutator_lock_);

  // Align end to the given alignment. This is done in MarkCompact GC when
  // mutators are suspended so that upcoming TLAB allocations start with a new
  // page. Adjust's heap's bytes_allocated accordingly. Returns the aligned end.
  uint8_t* AlignEnd(Thread* self, size_t alignment, Heap* heap) REQUIRES(Locks::mutator_lock_);

  friend class collector::MarkSweep;
  friend class collector::MarkCompact;
  DISALLOW_COPY_AND_ASSIGN(BumpPointerSpace);
};

}  // namespace space
}  // namespace gc
}  // namespace art

#endif  // ART_RUNTIME_GC_SPACE_BUMP_POINTER_SPACE_H_
