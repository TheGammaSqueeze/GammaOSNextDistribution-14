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

#include "base/gc_visited_arena_pool.h"

#include <sys/mman.h>
#include <sys/types.h>
#include <unistd.h>

#include "base/arena_allocator-inl.h"
#include "base/memfd.h"
#include "base/utils.h"
#include "gc/collector/mark_compact-inl.h"

namespace art HIDDEN {

TrackedArena::TrackedArena(uint8_t* start, size_t size, bool pre_zygote_fork, bool single_obj_arena)
    : Arena(),
      first_obj_array_(nullptr),
      pre_zygote_fork_(pre_zygote_fork),
      waiting_for_deletion_(false) {
  static_assert(ArenaAllocator::kArenaAlignment <= kMinPageSize,
                "Arena should not need stronger alignment than kMinPageSize.");
  memory_ = start;
  size_ = size;
  if (single_obj_arena) {
    // We have only one object in this arena and it is expected to consume the
    // entire arena.
    bytes_allocated_ = size;
  } else {
    DCHECK_ALIGNED_PARAM(size, gPageSize);
    DCHECK_ALIGNED_PARAM(start, gPageSize);
    size_t arr_size = DivideByPageSize(size);
    first_obj_array_.reset(new uint8_t*[arr_size]);
    std::fill_n(first_obj_array_.get(), arr_size, nullptr);
  }
}

void TrackedArena::ReleasePages(uint8_t* begin, size_t size, bool pre_zygote_fork) {
  DCHECK_ALIGNED_PARAM(begin, gPageSize);
  // Userfaultfd GC uses MAP_SHARED mappings for linear-alloc and therefore
  // MADV_DONTNEED will not free the pages from page cache. Therefore use
  // MADV_REMOVE instead, which is meant for this purpose.
  // Arenas allocated pre-zygote fork are private anonymous and hence must be
  // released using MADV_DONTNEED.
  if (!gUseUserfaultfd || pre_zygote_fork ||
      (madvise(begin, size, MADV_REMOVE) == -1 && errno == EINVAL)) {
    // MADV_REMOVE fails if invoked on anonymous mapping, which could happen
    // if the arena is released before userfaultfd-GC starts using memfd. So
    // use MADV_DONTNEED.
    ZeroAndReleaseMemory(begin, size);
  }
}

void TrackedArena::Release() {
  if (bytes_allocated_ > 0) {
    ReleasePages(Begin(), Size(), pre_zygote_fork_);
    if (first_obj_array_.get() != nullptr) {
      std::fill_n(first_obj_array_.get(), DivideByPageSize(Size()), nullptr);
    }
    bytes_allocated_ = 0;
  }
}

void TrackedArena::SetFirstObject(uint8_t* obj_begin, uint8_t* obj_end) {
  DCHECK(first_obj_array_.get() != nullptr);
  DCHECK_LE(static_cast<void*>(Begin()), static_cast<void*>(obj_end));
  DCHECK_LT(static_cast<void*>(obj_begin), static_cast<void*>(obj_end));
  GcVisitedArenaPool* arena_pool =
      static_cast<GcVisitedArenaPool*>(Runtime::Current()->GetLinearAllocArenaPool());
  size_t idx = DivideByPageSize(static_cast<size_t>(obj_begin - Begin()));
  size_t last_byte_idx = DivideByPageSize(static_cast<size_t>(obj_end - 1 - Begin()));
  // Do the update below with arena-pool's lock in shared-mode to serialize with
  // the compaction-pause wherein we acquire it exclusively. This is to ensure
  // that last-byte read there doesn't change after reading it and before
  // userfaultfd registration.
  ReaderMutexLock rmu(Thread::Current(), arena_pool->GetLock());
  // If the addr is at the beginning of a page, then we set it for that page too.
  if (IsAlignedParam(obj_begin, gPageSize)) {
    first_obj_array_[idx] = obj_begin;
  }
  while (idx < last_byte_idx) {
    first_obj_array_[++idx] = obj_begin;
  }
}

uint8_t* GcVisitedArenaPool::AddMap(size_t min_size) {
  size_t size = std::max(min_size, kLinearAllocPoolSize);
#if defined(__LP64__)
  // This is true only when we are running a 64-bit dex2oat to compile a 32-bit image.
  if (low_4gb_) {
    size = std::max(min_size, kLow4GBLinearAllocPoolSize);
  }
#endif
  size_t alignment = gc::Heap::BestPageTableAlignment(size);
  DCHECK_GE(size, gc::Heap::GetPMDSize());
  std::string err_msg;
  maps_.emplace_back(MemMap::MapAnonymousAligned(
      name_, size, PROT_READ | PROT_WRITE, low_4gb_, alignment, &err_msg));
  MemMap& map = maps_.back();
  if (!map.IsValid()) {
    LOG(FATAL) << "Failed to allocate " << name_ << ": " << err_msg;
    UNREACHABLE();
  }

  if (gUseUserfaultfd) {
    // Create a shadow-map for the map being added for userfaultfd GC
    gc::collector::MarkCompact* mark_compact =
        Runtime::Current()->GetHeap()->MarkCompactCollector();
    DCHECK_NE(mark_compact, nullptr);
    mark_compact->AddLinearAllocSpaceData(map.Begin(), map.Size());
  }
  Chunk* chunk = new Chunk(map.Begin(), map.Size());
  best_fit_allocs_.insert(chunk);
  free_chunks_.insert(chunk);
  return map.Begin();
}

GcVisitedArenaPool::GcVisitedArenaPool(bool low_4gb, bool is_zygote, const char* name)
    : lock_("gc-visited arena-pool", kGenericBottomLock),
      bytes_allocated_(0),
      unused_arenas_(nullptr),
      name_(name),
      defer_arena_freeing_(false),
      low_4gb_(low_4gb),
      pre_zygote_fork_(is_zygote) {}

GcVisitedArenaPool::~GcVisitedArenaPool() {
  for (Chunk* chunk : free_chunks_) {
    delete chunk;
  }
  // Must not delete chunks from best_fit_allocs_ as they are shared with
  // free_chunks_.
}

size_t GcVisitedArenaPool::GetBytesAllocated() const {
  ReaderMutexLock rmu(Thread::Current(), lock_);
  return bytes_allocated_;
}

uint8_t* GcVisitedArenaPool::AddPreZygoteForkMap(size_t size) {
  DCHECK(pre_zygote_fork_);
  std::string pre_fork_name = "Pre-zygote-";
  pre_fork_name += name_;
  std::string err_msg;
  maps_.emplace_back(MemMap::MapAnonymous(
      pre_fork_name.c_str(), size, PROT_READ | PROT_WRITE, low_4gb_, &err_msg));
  MemMap& map = maps_.back();
  if (!map.IsValid()) {
    LOG(FATAL) << "Failed to allocate " << pre_fork_name << ": " << err_msg;
    UNREACHABLE();
  }
  return map.Begin();
}

uint8_t* GcVisitedArenaPool::AllocSingleObjArena(size_t size) {
  WriterMutexLock wmu(Thread::Current(), lock_);
  Arena* arena;
  DCHECK(gUseUserfaultfd);
  // To minimize private dirty, all class and intern table allocations are
  // done outside LinearAlloc range so they are untouched during GC.
  if (pre_zygote_fork_) {
    uint8_t* begin = static_cast<uint8_t*>(malloc(size));
    auto insert_result = allocated_arenas_.insert(
        new TrackedArena(begin, size, /*pre_zygote_fork=*/true, /*single_obj_arena=*/true));
    arena = *insert_result.first;
  } else {
    arena = AllocArena(size, /*need_first_obj_arr=*/true);
  }
  return arena->Begin();
}

void GcVisitedArenaPool::FreeSingleObjArena(uint8_t* addr) {
  Thread* self = Thread::Current();
  size_t size;
  bool zygote_arena;
  {
    TrackedArena temp_arena(addr);
    WriterMutexLock wmu(self, lock_);
    auto iter = allocated_arenas_.find(&temp_arena);
    DCHECK(iter != allocated_arenas_.end());
    TrackedArena* arena = *iter;
    size = arena->Size();
    zygote_arena = arena->IsPreZygoteForkArena();
    DCHECK_EQ(arena->Begin(), addr);
    DCHECK(arena->IsSingleObjectArena());
    allocated_arenas_.erase(iter);
    if (defer_arena_freeing_) {
      arena->SetupForDeferredDeletion(unused_arenas_);
      unused_arenas_ = arena;
    } else {
      delete arena;
    }
  }
  // Refer to the comment in FreeArenaChain() for why the pages are released
  // after deleting the arena.
  if (zygote_arena) {
    free(addr);
  } else {
    TrackedArena::ReleasePages(addr, size, /*pre_zygote_fork=*/false);
    WriterMutexLock wmu(self, lock_);
    FreeRangeLocked(addr, size);
  }
}

Arena* GcVisitedArenaPool::AllocArena(size_t size, bool single_obj_arena) {
  // Return only page aligned sizes so that madvise can be leveraged.
  size = RoundUp(size, gPageSize);
  if (pre_zygote_fork_) {
    // The first fork out of zygote hasn't happened yet. Allocate arena in a
    // private-anonymous mapping to retain clean pages across fork.
    uint8_t* addr = AddPreZygoteForkMap(size);
    auto insert_result = allocated_arenas_.insert(
        new TrackedArena(addr, size, /*pre_zygote_fork=*/true, single_obj_arena));
    DCHECK(insert_result.second);
    return *insert_result.first;
  }

  Chunk temp_chunk(nullptr, size);
  auto best_fit_iter = best_fit_allocs_.lower_bound(&temp_chunk);
  if (UNLIKELY(best_fit_iter == best_fit_allocs_.end())) {
    AddMap(size);
    best_fit_iter = best_fit_allocs_.lower_bound(&temp_chunk);
    CHECK(best_fit_iter != best_fit_allocs_.end());
  }
  auto free_chunks_iter = free_chunks_.find(*best_fit_iter);
  DCHECK(free_chunks_iter != free_chunks_.end());
  Chunk* chunk = *best_fit_iter;
  DCHECK_EQ(chunk, *free_chunks_iter);
  // if the best-fit chunk < 2x the requested size, then give the whole chunk.
  if (chunk->size_ < 2 * size) {
    DCHECK_GE(chunk->size_, size);
    auto insert_result = allocated_arenas_.insert(new TrackedArena(chunk->addr_,
                                                                   chunk->size_,
                                                                   /*pre_zygote_fork=*/false,
                                                                   single_obj_arena));
    DCHECK(insert_result.second);
    free_chunks_.erase(free_chunks_iter);
    best_fit_allocs_.erase(best_fit_iter);
    delete chunk;
    return *insert_result.first;
  } else {
    auto insert_result = allocated_arenas_.insert(new TrackedArena(chunk->addr_,
                                                                   size,
                                                                   /*pre_zygote_fork=*/false,
                                                                   single_obj_arena));
    DCHECK(insert_result.second);
    // Compute next iterators for faster insert later.
    auto next_best_fit_iter = best_fit_iter;
    next_best_fit_iter++;
    auto next_free_chunks_iter = free_chunks_iter;
    next_free_chunks_iter++;
    auto best_fit_nh = best_fit_allocs_.extract(best_fit_iter);
    auto free_chunks_nh = free_chunks_.extract(free_chunks_iter);
    best_fit_nh.value()->addr_ += size;
    best_fit_nh.value()->size_ -= size;
    DCHECK_EQ(free_chunks_nh.value()->addr_, chunk->addr_);
    best_fit_allocs_.insert(next_best_fit_iter, std::move(best_fit_nh));
    free_chunks_.insert(next_free_chunks_iter, std::move(free_chunks_nh));
    return *insert_result.first;
  }
}

void GcVisitedArenaPool::FreeRangeLocked(uint8_t* range_begin, size_t range_size) {
  Chunk temp_chunk(range_begin, range_size);
  bool merge_with_next = false;
  bool merge_with_prev = false;
  auto next_iter = free_chunks_.lower_bound(&temp_chunk);
  auto iter_for_extract = free_chunks_.end();
  // Can we merge with the previous chunk?
  if (next_iter != free_chunks_.begin()) {
    auto prev_iter = next_iter;
    prev_iter--;
    merge_with_prev = (*prev_iter)->addr_ + (*prev_iter)->size_ == range_begin;
    if (merge_with_prev) {
      range_begin = (*prev_iter)->addr_;
      range_size += (*prev_iter)->size_;
      // Hold on to the iterator for faster extract later
      iter_for_extract = prev_iter;
    }
  }
  // Can we merge with the next chunk?
  if (next_iter != free_chunks_.end()) {
    merge_with_next = range_begin + range_size == (*next_iter)->addr_;
    if (merge_with_next) {
      range_size += (*next_iter)->size_;
      if (merge_with_prev) {
        auto iter = next_iter;
        next_iter++;
        // Keep only one of the two chunks to be expanded.
        Chunk* chunk = *iter;
        size_t erase_res = best_fit_allocs_.erase(chunk);
        DCHECK_EQ(erase_res, 1u);
        free_chunks_.erase(iter);
        delete chunk;
      } else {
        iter_for_extract = next_iter;
        next_iter++;
      }
    }
  }

  // Extract-insert avoids 2/4 destroys and 2/2 creations
  // as compared to erase-insert, so use that when merging.
  if (merge_with_prev || merge_with_next) {
    auto free_chunks_nh = free_chunks_.extract(iter_for_extract);
    auto best_fit_allocs_nh = best_fit_allocs_.extract(*iter_for_extract);

    free_chunks_nh.value()->addr_ = range_begin;
    DCHECK_EQ(best_fit_allocs_nh.value()->addr_, range_begin);
    free_chunks_nh.value()->size_ = range_size;
    DCHECK_EQ(best_fit_allocs_nh.value()->size_, range_size);

    free_chunks_.insert(next_iter, std::move(free_chunks_nh));
    // Since the chunk's size has expanded, the hint won't be useful
    // for best-fit set.
    best_fit_allocs_.insert(std::move(best_fit_allocs_nh));
  } else {
    DCHECK(iter_for_extract == free_chunks_.end());
    Chunk* chunk = new Chunk(range_begin, range_size);
    free_chunks_.insert(next_iter, chunk);
    best_fit_allocs_.insert(chunk);
  }
}

void GcVisitedArenaPool::FreeArenaChain(Arena* first) {
  if (kRunningOnMemoryTool) {
    for (Arena* arena = first; arena != nullptr; arena = arena->Next()) {
      MEMORY_TOOL_MAKE_UNDEFINED(arena->Begin(), arena->GetBytesAllocated());
    }
  }

  // TODO: Handle the case when arena_allocator::kArenaAllocatorPreciseTracking
  // is true. See MemMapArenaPool::FreeArenaChain() for example.
  CHECK(!arena_allocator::kArenaAllocatorPreciseTracking);
  Thread* self = Thread::Current();
  // vector of arena ranges to be freed and whether they are pre-zygote-fork.
  std::vector<std::tuple<uint8_t*, size_t, bool>> free_ranges;

  {
    WriterMutexLock wmu(self, lock_);
    while (first != nullptr) {
      TrackedArena* temp = down_cast<TrackedArena*>(first);
      DCHECK(!temp->IsSingleObjectArena());
      first = first->Next();
      free_ranges.emplace_back(temp->Begin(), temp->Size(), temp->IsPreZygoteForkArena());
      // In other implementations of ArenaPool this is calculated when asked for,
      // thanks to the list of free arenas that is kept around. But in this case,
      // we release the freed arena back to the pool and therefore need to
      // calculate here.
      bytes_allocated_ += temp->GetBytesAllocated();
      auto iter = allocated_arenas_.find(temp);
      DCHECK(iter != allocated_arenas_.end());
      allocated_arenas_.erase(iter);
      if (defer_arena_freeing_) {
        temp->SetupForDeferredDeletion(unused_arenas_);
        unused_arenas_ = temp;
      } else {
        delete temp;
      }
    }
  }

  // madvise of arenas must be done after the above loop which serializes with
  // MarkCompact::ProcessLinearAlloc() so that if it finds an arena to be not
  // 'waiting-for-deletion' then it finishes the arena's processing before
  // clearing here. Otherwise, we could have a situation wherein arena-pool
  // assumes the memory range of the arena(s) to be zero'ed (by madvise),
  // whereas GC maps stale arena pages.
  for (auto& iter : free_ranges) {
    // No need to madvise pre-zygote-fork arenas as they will munmapped below.
    if (!std::get<2>(iter)) {
      TrackedArena::ReleasePages(std::get<0>(iter), std::get<1>(iter), /*pre_zygote_fork=*/false);
    }
  }

  WriterMutexLock wmu(self, lock_);
  for (auto& iter : free_ranges) {
    if (UNLIKELY(std::get<2>(iter))) {
      bool found = false;
      for (auto map_iter = maps_.begin(); map_iter != maps_.end(); map_iter++) {
        if (map_iter->Begin() == std::get<0>(iter)) {
          // erase will destruct the MemMap and thereby munmap. But this happens
          // very rarely so it's ok to do it with lock acquired.
          maps_.erase(map_iter);
          found = true;
          break;
        }
      }
      CHECK(found);
    } else {
      FreeRangeLocked(std::get<0>(iter), std::get<1>(iter));
    }
  }
}

void GcVisitedArenaPool::DeleteUnusedArenas() {
  TrackedArena* arena;
  {
    WriterMutexLock wmu(Thread::Current(), lock_);
    defer_arena_freeing_ = false;
    arena = unused_arenas_;
    unused_arenas_ = nullptr;
  }
  while (arena != nullptr) {
    TrackedArena* temp = down_cast<TrackedArena*>(arena->Next());
    delete arena;
    arena = temp;
  }
}

}  // namespace art
