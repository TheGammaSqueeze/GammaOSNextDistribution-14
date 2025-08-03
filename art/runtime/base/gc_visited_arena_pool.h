/*
 * Copyright 2022 The Android Open Source Project
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

#ifndef ART_RUNTIME_BASE_GC_VISITED_ARENA_POOL_H_
#define ART_RUNTIME_BASE_GC_VISITED_ARENA_POOL_H_

#include <set>

#include "base/allocator.h"
#include "base/arena_allocator.h"
#include "base/casts.h"
#include "base/hash_set.h"
#include "base/locks.h"
#include "base/mem_map.h"
#include "read_barrier_config.h"
#include "runtime.h"

namespace art HIDDEN {

// GcVisitedArenaPool can be used for tracking allocations so that they can
// be visited during GC to update the GC-roots inside them.

// An Arena which tracks its allocations.
class TrackedArena final : public Arena {
 public:
  // Used for searching in maps. Only arena's starting address is relevant.
  explicit TrackedArena(uint8_t* addr) : pre_zygote_fork_(false) { memory_ = addr; }
  TrackedArena(uint8_t* start, size_t size, bool pre_zygote_fork, bool single_obj_arena);

  template <typename PageVisitor>
  void VisitRoots(PageVisitor& visitor) const REQUIRES_SHARED(Locks::mutator_lock_) {
    uint8_t* page_begin = Begin();
    if (first_obj_array_.get() != nullptr) {
      DCHECK_ALIGNED_PARAM(Size(), gPageSize);
      DCHECK_ALIGNED_PARAM(Begin(), gPageSize);
      for (int i = 0, nr_pages = DivideByPageSize(Size());
           i < nr_pages;
           i++, page_begin += gPageSize) {
        uint8_t* first = first_obj_array_[i];
        if (first != nullptr) {
          visitor(page_begin, first, gPageSize);
        } else {
          break;
        }
      }
    } else {
      size_t page_size = Size();
      while (page_size > gPageSize) {
        visitor(page_begin, nullptr, gPageSize);
        page_begin += gPageSize;
        page_size -= gPageSize;
      }
      visitor(page_begin, nullptr, page_size);
    }
  }

  // Return the page addr of the first page with first_obj set to nullptr.
  uint8_t* GetLastUsedByte() const REQUIRES_SHARED(Locks::mutator_lock_) {
    // Jump past bytes-allocated for arenas which are not currently being used
    // by arena-allocator. This helps in reducing loop iterations below.
    uint8_t* last_byte = AlignUp(Begin() + GetBytesAllocated(), gPageSize);
    if (first_obj_array_.get() != nullptr) {
      DCHECK_ALIGNED_PARAM(Begin(), gPageSize);
      DCHECK_ALIGNED_PARAM(End(), gPageSize);
      DCHECK_LE(last_byte, End());
    } else {
      DCHECK_EQ(last_byte, End());
    }
    for (size_t i = DivideByPageSize(last_byte - Begin());
         last_byte < End() && first_obj_array_[i] != nullptr;
         last_byte += gPageSize, i++) {
      // No body.
    }
    return last_byte;
  }

  uint8_t* GetFirstObject(uint8_t* addr) const REQUIRES_SHARED(Locks::mutator_lock_) {
    DCHECK_LE(Begin(), addr);
    DCHECK_GT(End(), addr);
    if (first_obj_array_.get() != nullptr) {
      return first_obj_array_[DivideByPageSize(addr - Begin())];
    } else {
      // The pages of this arena contain array of GC-roots. So we don't need
      // first-object of any given page of the arena.
      // Returning null helps distinguish which visitor is to be called.
      return nullptr;
    }
  }

  // Set 'obj_begin' in first_obj_array_ in every element for which it's the
  // first object.
  EXPORT void SetFirstObject(uint8_t* obj_begin, uint8_t* obj_end);
  // Setup the arena for deferred deletion.
  void SetupForDeferredDeletion(TrackedArena* next_arena) {
    DCHECK(next_arena == nullptr || next_arena->waiting_for_deletion_);
    DCHECK(!waiting_for_deletion_);
    waiting_for_deletion_ = true;
    next_ = next_arena;
  }
  bool IsWaitingForDeletion() const { return waiting_for_deletion_; }

  // Madvise the pages in the given range. 'begin' is expected to be page
  // aligned.
  // TODO: Remove this once we remove the shmem (minor-fault) code in
  // userfaultfd GC and directly use ZeroAndReleaseMemory().
  static void ReleasePages(uint8_t* begin, size_t size, bool pre_zygote_fork);
  void Release() override;
  bool IsPreZygoteForkArena() const { return pre_zygote_fork_; }
  bool IsSingleObjectArena() const { return first_obj_array_.get() == nullptr; }

 private:
  // first_obj_array_[i] is the object that overlaps with the ith page's
  // beginning, i.e. first_obj_array_[i] <= ith page_begin.
  std::unique_ptr<uint8_t*[]> first_obj_array_;
  const bool pre_zygote_fork_;
  bool waiting_for_deletion_;
};

// An arena-pool wherein allocations can be tracked so that the GC can visit all
// the GC roots. All the arenas are allocated in one sufficiently large memory
// range to avoid multiple calls to mremapped/mprotected syscalls.
class GcVisitedArenaPool final : public ArenaPool {
 public:
#if defined(__LP64__)
  // Use a size in multiples of 1GB as that can utilize the optimized mremap
  // page-table move.
  static constexpr size_t kLinearAllocPoolSize = 1 * GB;
  static constexpr size_t kLow4GBLinearAllocPoolSize = 32 * MB;
#else
  static constexpr size_t kLinearAllocPoolSize = 32 * MB;
#endif

  explicit GcVisitedArenaPool(bool low_4gb = false,
                              bool is_zygote = false,
                              const char* name = "LinearAlloc");
  virtual ~GcVisitedArenaPool();

  Arena* AllocArena(size_t size, bool need_first_obj_arr) REQUIRES(lock_);
  // Use by arena allocator.
  Arena* AllocArena(size_t size) override REQUIRES(!lock_) {
    WriterMutexLock wmu(Thread::Current(), lock_);
    return AllocArena(size, /*need_first_obj_arr=*/false);
  }
  void FreeArenaChain(Arena* first) override REQUIRES(!lock_);
  size_t GetBytesAllocated() const override REQUIRES(!lock_);
  void ReclaimMemory() override {}
  void LockReclaimMemory() override {}
  void TrimMaps() override {}

  EXPORT uint8_t* AllocSingleObjArena(size_t size) REQUIRES(!lock_);
  EXPORT void FreeSingleObjArena(uint8_t* addr) REQUIRES(!lock_);

  bool Contains(void* ptr) REQUIRES(!lock_) {
    ReaderMutexLock rmu(Thread::Current(), lock_);
    for (auto& map : maps_) {
      if (map.HasAddress(ptr)) {
        return true;
      }
    }
    return false;
  }

  template <typename PageVisitor>
  void VisitRoots(PageVisitor& visitor) REQUIRES_SHARED(Locks::mutator_lock_, lock_) {
    for (auto& arena : allocated_arenas_) {
      arena->VisitRoots(visitor);
    }
  }

  template <typename Callback>
  void ForEachAllocatedArena(Callback cb) REQUIRES_SHARED(Locks::mutator_lock_, lock_) {
    // We should not have any unused arenas when calling this function.
    CHECK(unused_arenas_ == nullptr);
    for (auto& arena : allocated_arenas_) {
      cb(*arena);
    }
  }

  // Called in Heap::PreZygoteFork(). All allocations after this are done in
  // arena-pool which is visited by userfaultfd.
  void SetupPostZygoteMode() REQUIRES(!lock_) {
    WriterMutexLock wmu(Thread::Current(), lock_);
    DCHECK(pre_zygote_fork_);
    pre_zygote_fork_ = false;
  }

  // For userfaultfd GC to be able to acquire the lock to avoid concurrent
  // release of arenas when it is visiting them.
  ReaderWriterMutex& GetLock() const RETURN_CAPABILITY(lock_) { return lock_; }

  // Called in the compaction pause to indicate that all arenas that will be
  // freed until compaction is done shouldn't delete the TrackedArena object to
  // avoid ABA problem. Called with lock_ acquired.
  void DeferArenaFreeing() REQUIRES(lock_) {
    CHECK(unused_arenas_ == nullptr);
    defer_arena_freeing_ = true;
  }

  // Clear defer_arena_freeing_ and delete all unused arenas.
  void DeleteUnusedArenas() REQUIRES(!lock_);

 private:
  void FreeRangeLocked(uint8_t* range_begin, size_t range_size) REQUIRES(lock_);
  // Add a map (to be visited by userfaultfd) to the pool of at least min_size
  // and return its address.
  uint8_t* AddMap(size_t min_size) REQUIRES(lock_);
  // Add a private anonymous map prior to zygote fork to the pool and return its
  // address.
  uint8_t* AddPreZygoteForkMap(size_t size) REQUIRES(lock_);

  class Chunk {
   public:
    Chunk(uint8_t* addr, size_t size) : addr_(addr), size_(size) {}
    uint8_t* addr_;
    size_t size_;
  };

  class LessByChunkAddr {
   public:
    bool operator()(const Chunk* a, const Chunk* b) const {
      return std::less<uint8_t*>{}(a->addr_, b->addr_);
    }
  };

  class LessByChunkSize {
   public:
    // Since two chunks could have the same size, use addr when that happens.
    bool operator()(const Chunk* a, const Chunk* b) const {
      return a->size_ < b->size_ ||
             (a->size_ == b->size_ && std::less<uint8_t*>{}(a->addr_, b->addr_));
    }
  };

  class TrackedArenaEquals {
   public:
    bool operator()(const TrackedArena* a, const TrackedArena* b) const {
      return std::equal_to<uint8_t*>{}(a->Begin(), b->Begin());
    }
  };

  class TrackedArenaHash {
   public:
    size_t operator()(const TrackedArena* arena) const {
      return std::hash<size_t>{}(DivideByPageSize(reinterpret_cast<uintptr_t>(arena->Begin())));
    }
  };
  using AllocatedArenaSet =
      HashSet<TrackedArena*, DefaultEmptyFn<TrackedArena*>, TrackedArenaHash, TrackedArenaEquals>;

  mutable ReaderWriterMutex lock_;
  std::vector<MemMap> maps_ GUARDED_BY(lock_);
  std::set<Chunk*, LessByChunkSize> best_fit_allocs_ GUARDED_BY(lock_);
  std::set<Chunk*, LessByChunkAddr> free_chunks_ GUARDED_BY(lock_);
  // Set of allocated arenas. It's required to be able to find the arena
  // corresponding to a given address.
  AllocatedArenaSet allocated_arenas_ GUARDED_BY(lock_);
  // Number of bytes allocated so far.
  size_t bytes_allocated_ GUARDED_BY(lock_);
  // To hold arenas that are freed while GC is happening. These are kept until
  // the end of GC to avoid ABA problem.
  TrackedArena* unused_arenas_ GUARDED_BY(lock_);
  const char* name_;
  // Flag to indicate that some arenas have been freed. This flag is used as an
  // optimization by GC to know if it needs to find if the arena being visited
  // has been freed or not. The flag is cleared in the compaction pause and read
  // when linear-alloc space is concurrently visited updated to update GC roots.
  bool defer_arena_freeing_ GUARDED_BY(lock_);
  const bool low_4gb_;
  // Set to true in zygote process so that all linear-alloc allocations are in
  // private-anonymous mappings and not on userfaultfd visited pages. At
  // first zygote fork, it's set to false, after which all allocations are done
  // in userfaultfd visited space.
  bool pre_zygote_fork_ GUARDED_BY(lock_);

  DISALLOW_COPY_AND_ASSIGN(GcVisitedArenaPool);
};

// Allocator for class-table and intern-table hash-sets. It enables updating the
// roots concurrently page-by-page.
template <class T, AllocatorTag kTag>
class GcRootArenaAllocator : public TrackingAllocator<T, kTag> {
 public:
  using value_type = typename TrackingAllocator<T, kTag>::value_type;
  using size_type = typename TrackingAllocator<T, kTag>::size_type;
  using difference_type = typename TrackingAllocator<T, kTag>::difference_type;
  using pointer = typename TrackingAllocator<T, kTag>::pointer;
  using const_pointer = typename TrackingAllocator<T, kTag>::const_pointer;
  using reference = typename TrackingAllocator<T, kTag>::reference;
  using const_reference = typename TrackingAllocator<T, kTag>::const_reference;

  // Used internally by STL data structures.
  template <class U>
  explicit GcRootArenaAllocator(
      [[maybe_unused]] const GcRootArenaAllocator<U, kTag>& alloc) noexcept {}
  // Used internally by STL data structures.
  GcRootArenaAllocator() noexcept : TrackingAllocator<T, kTag>() {}

  // Enables an allocator for objects of one type to allocate storage for objects of another type.
  // Used internally by STL data structures.
  template <class U>
  struct rebind {
    using other = GcRootArenaAllocator<U, kTag>;
  };

  pointer allocate(size_type n, [[maybe_unused]] const_pointer hint = nullptr) {
    if (!gUseUserfaultfd) {
      return TrackingAllocator<T, kTag>::allocate(n);
    }
    size_t size = n * sizeof(T);
    GcVisitedArenaPool* pool =
        down_cast<GcVisitedArenaPool*>(Runtime::Current()->GetLinearAllocArenaPool());
    return reinterpret_cast<pointer>(pool->AllocSingleObjArena(size));
  }

  template <typename PT>
  void deallocate(PT p, size_type n) {
    if (!gUseUserfaultfd) {
      TrackingAllocator<T, kTag>::deallocate(p, n);
      return;
    }
    GcVisitedArenaPool* pool =
        down_cast<GcVisitedArenaPool*>(Runtime::Current()->GetLinearAllocArenaPool());
    pool->FreeSingleObjArena(reinterpret_cast<uint8_t*>(p));
  }
};

}  // namespace art

#endif  // ART_RUNTIME_BASE_GC_VISITED_ARENA_POOL_H_
