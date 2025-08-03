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

#ifndef ART_RUNTIME_LINEAR_ALLOC_INL_H_
#define ART_RUNTIME_LINEAR_ALLOC_INL_H_

#include "linear_alloc.h"

#include "base/gc_visited_arena_pool.h"
#include "thread-current-inl.h"

namespace art HIDDEN {

inline void LinearAlloc::SetFirstObject(void* begin, size_t bytes) const {
  DCHECK(track_allocations_);
  if (ArenaAllocator::IsRunningOnMemoryTool()) {
    bytes += ArenaAllocator::kMemoryToolRedZoneBytes;
  }
  uint8_t* end = static_cast<uint8_t*>(begin) + bytes;
  Arena* arena = allocator_.GetHeadArena();
  DCHECK_NE(arena, nullptr);
  // The object would either be in the head arena or the next one.
  if (UNLIKELY(begin < arena->Begin() || begin >= arena->End())) {
    arena = arena->Next();
  }
  DCHECK(begin >= arena->Begin() && end <= arena->End());
  down_cast<TrackedArena*>(arena)->SetFirstObject(static_cast<uint8_t*>(begin), end);
}

inline void LinearAlloc::ConvertToNoGcRoots(void* ptr, LinearAllocKind orig_kind) {
  if (track_allocations_ && ptr != nullptr) {
    TrackingHeader* header = static_cast<TrackingHeader*>(ptr);
    header--;
    DCHECK_EQ(header->GetKind(), orig_kind);
    DCHECK_GT(header->GetSize(), 0u);
    // 16-byte allocations are not supported yet.
    DCHECK(!header->Is16Aligned());
    header->SetKind(LinearAllocKind::kNoGCRoots);
  }
}

inline void LinearAlloc::SetupForPostZygoteFork(Thread* self) {
  MutexLock mu(self, lock_);
  DCHECK(track_allocations_);
  allocator_.ResetCurrentArena();
}

inline void* LinearAlloc::Realloc(Thread* self,
                                  void* ptr,
                                  size_t old_size,
                                  size_t new_size,
                                  LinearAllocKind kind) {
  MutexLock mu(self, lock_);
  if (track_allocations_) {
    if (ptr != nullptr) {
      // Realloc cannot be called on 16-byte aligned as Realloc doesn't guarantee
      // that. So the header must be immediately prior to ptr.
      TrackingHeader* header = reinterpret_cast<TrackingHeader*>(ptr) - 1;
      DCHECK_EQ(header->GetKind(), kind);
      old_size += sizeof(TrackingHeader);
      DCHECK_EQ(header->GetSize(), old_size);
      ptr = header;
    } else {
      DCHECK_EQ(old_size, 0u);
    }
    new_size += sizeof(TrackingHeader);
    void* ret = allocator_.Realloc(ptr, old_size, new_size);
    new (ret) TrackingHeader(new_size, kind);
    SetFirstObject(ret, new_size);
    return static_cast<TrackingHeader*>(ret) + 1;
  } else {
    return allocator_.Realloc(ptr, old_size, new_size);
  }
}

inline void* LinearAlloc::Alloc(Thread* self, size_t size, LinearAllocKind kind) {
  MutexLock mu(self, lock_);
  if (track_allocations_) {
    size += sizeof(TrackingHeader);
    TrackingHeader* storage = new (allocator_.Alloc(size)) TrackingHeader(size, kind);
    SetFirstObject(storage, size);
    return storage + 1;
  } else {
    return allocator_.Alloc(size);
  }
}

inline void* LinearAlloc::AllocAlign16(Thread* self, size_t size, LinearAllocKind kind) {
  MutexLock mu(self, lock_);
  DCHECK_ALIGNED(size, 16);
  if (track_allocations_) {
    size_t mem_tool_bytes = ArenaAllocator::IsRunningOnMemoryTool()
                            ? ArenaAllocator::kMemoryToolRedZoneBytes : 0;
    uint8_t* ptr = allocator_.CurrentPtr() + sizeof(TrackingHeader);
    uintptr_t padding =
        RoundUp(reinterpret_cast<uintptr_t>(ptr), 16) - reinterpret_cast<uintptr_t>(ptr);
    DCHECK_LT(padding, 16u);
    size_t required_size = size + sizeof(TrackingHeader) + padding;

    if (allocator_.CurrentArenaUnusedBytes() < required_size + mem_tool_bytes) {
      // The allocator will require a new arena, which is expected to be
      // 16-byte aligned.
      static_assert(ArenaAllocator::kArenaAlignment >= 16,
                    "Expecting sufficient alignment for new Arena.");
      required_size = size + RoundUp(sizeof(TrackingHeader), 16);
    }
    // Using ArenaAllocator's AllocAlign16 now would disturb the alignment by
    // trying to make header 16-byte aligned. The alignment requirements are
    // already addressed here. Now we want allocator to just bump the pointer.
    ptr = static_cast<uint8_t*>(allocator_.Alloc(required_size));
    new (ptr) TrackingHeader(required_size, kind, /*is_16_aligned=*/true);
    SetFirstObject(ptr, required_size);
    return AlignUp(ptr + sizeof(TrackingHeader), 16);
  } else {
    return allocator_.AllocAlign16(size);
  }
}

inline size_t LinearAlloc::GetUsedMemory() const {
  MutexLock mu(Thread::Current(), lock_);
  return allocator_.BytesUsed();
}

inline ArenaPool* LinearAlloc::GetArenaPool() {
  MutexLock mu(Thread::Current(), lock_);
  return allocator_.GetArenaPool();
}

inline bool LinearAlloc::Contains(void* ptr) const {
  MutexLock mu(Thread::Current(), lock_);
  return allocator_.Contains(ptr);
}

}  // namespace art

#endif  // ART_RUNTIME_LINEAR_ALLOC_INL_H_
