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

#include "bump_pointer_space.h"
#include "bump_pointer_space-inl.h"
#include "mirror/class-inl.h"
#include "mirror/object-inl.h"
#include "thread_list.h"

namespace art HIDDEN {
namespace gc {
namespace space {

BumpPointerSpace* BumpPointerSpace::Create(const std::string& name, size_t capacity) {
  capacity = RoundUp(capacity, gPageSize);
  std::string error_msg;
  MemMap mem_map = MemMap::MapAnonymous(name.c_str(),
                                        capacity,
                                        PROT_READ | PROT_WRITE,
                                        /*low_4gb=*/ true,
                                        &error_msg);
  if (!mem_map.IsValid()) {
    LOG(ERROR) << "Failed to allocate pages for alloc space (" << name << ") of size "
        << PrettySize(capacity) << " with message " << error_msg;
    return nullptr;
  }
  return new BumpPointerSpace(name, std::move(mem_map));
}

BumpPointerSpace* BumpPointerSpace::CreateFromMemMap(const std::string& name, MemMap&& mem_map) {
  return new BumpPointerSpace(name, std::move(mem_map));
}

BumpPointerSpace::BumpPointerSpace(const std::string& name, uint8_t* begin, uint8_t* limit)
    : ContinuousMemMapAllocSpace(
          name, MemMap::Invalid(), begin, begin, limit, kGcRetentionPolicyAlwaysCollect),
      growth_end_(limit),
      objects_allocated_(0),
      bytes_allocated_(0),
      lock_("Bump-pointer space lock"),
      main_block_size_(0) {
  // This constructor gets called only from Heap::PreZygoteFork(), which
  // doesn't require a mark_bitmap.
}

BumpPointerSpace::BumpPointerSpace(const std::string& name, MemMap&& mem_map)
    : ContinuousMemMapAllocSpace(name,
                                 std::move(mem_map),
                                 mem_map.Begin(),
                                 mem_map.Begin(),
                                 mem_map.End(),
                                 kGcRetentionPolicyAlwaysCollect),
      growth_end_(mem_map_.End()),
      objects_allocated_(0),
      bytes_allocated_(0),
      lock_("Bump-pointer space lock", kBumpPointerSpaceBlockLock),
      main_block_size_(0) {
  mark_bitmap_ =
      accounting::ContinuousSpaceBitmap::Create("bump-pointer space live bitmap",
                                                Begin(),
                                                Capacity());
}

void BumpPointerSpace::Clear() {
  // Release the pages back to the operating system.
  if (!kMadviseZeroes) {
    memset(Begin(), 0, Limit() - Begin());
  }
  CHECK_NE(madvise(Begin(), Limit() - Begin(), MADV_DONTNEED), -1) << "madvise failed";
  // Reset the end of the space back to the beginning, we move the end forward as we allocate
  // objects.
  SetEnd(Begin());
  objects_allocated_.store(0, std::memory_order_relaxed);
  bytes_allocated_.store(0, std::memory_order_relaxed);
  {
    MutexLock mu(Thread::Current(), lock_);
    growth_end_ = Limit();
    block_sizes_.clear();
    main_block_size_ = 0;
  }
}

size_t BumpPointerSpace::ClampGrowthLimit(size_t new_capacity) {
  CHECK(gUseUserfaultfd);
  MutexLock mu(Thread::Current(), lock_);
  CHECK_EQ(growth_end_, Limit());
  uint8_t* end = End();
  CHECK_LE(end, growth_end_);
  size_t free_capacity = growth_end_ - end;
  size_t clamp_size = Capacity() - new_capacity;
  if (clamp_size > free_capacity) {
    new_capacity += clamp_size - free_capacity;
  }
  SetLimit(Begin() + new_capacity);
  growth_end_ = Limit();
  GetMemMap()->SetSize(new_capacity);
  if (GetMarkBitmap()->HeapBegin() != 0) {
    GetMarkBitmap()->SetHeapSize(new_capacity);
  }
  return new_capacity;
}

void BumpPointerSpace::Dump(std::ostream& os) const {
  os << GetName() << " "
      << reinterpret_cast<void*>(Begin()) << "-" << reinterpret_cast<void*>(End()) << " - "
      << reinterpret_cast<void*>(Limit());
}

size_t BumpPointerSpace::RevokeThreadLocalBuffers(Thread* thread) {
  MutexLock mu(Thread::Current(), lock_);
  RevokeThreadLocalBuffersLocked(thread);
  return 0U;
}

size_t BumpPointerSpace::RevokeAllThreadLocalBuffers() {
  Thread* self = Thread::Current();
  MutexLock mu(self, *Locks::runtime_shutdown_lock_);
  MutexLock mu2(self, *Locks::thread_list_lock_);
  // TODO: Not do a copy of the thread list?
  std::list<Thread*> thread_list = Runtime::Current()->GetThreadList()->GetList();
  for (Thread* thread : thread_list) {
    RevokeThreadLocalBuffers(thread);
  }
  return 0U;
}

void BumpPointerSpace::AssertThreadLocalBuffersAreRevoked(Thread* thread) {
  if (kIsDebugBuild) {
    MutexLock mu(Thread::Current(), lock_);
    DCHECK(!thread->HasTlab());
  }
}

void BumpPointerSpace::AssertAllThreadLocalBuffersAreRevoked() {
  if (kIsDebugBuild) {
    Thread* self = Thread::Current();
    MutexLock mu(self, *Locks::runtime_shutdown_lock_);
    MutexLock mu2(self, *Locks::thread_list_lock_);
    // TODO: Not do a copy of the thread list?
    std::list<Thread*> thread_list = Runtime::Current()->GetThreadList()->GetList();
    for (Thread* thread : thread_list) {
      AssertThreadLocalBuffersAreRevoked(thread);
    }
  }
}

void BumpPointerSpace::UpdateMainBlock() {
  DCHECK(block_sizes_.empty());
  main_block_size_ = Size();
}

// Returns the start of the storage.
uint8_t* BumpPointerSpace::AllocBlock(size_t bytes) {
  if (block_sizes_.empty()) {
    UpdateMainBlock();
  }
  uint8_t* storage = reinterpret_cast<uint8_t*>(AllocNonvirtualWithoutAccounting(bytes));
  if (LIKELY(storage != nullptr)) {
    block_sizes_.push_back(bytes);
  }
  return storage;
}

accounting::ContinuousSpaceBitmap::SweepCallback* BumpPointerSpace::GetSweepCallback() {
  UNIMPLEMENTED(FATAL);
  UNREACHABLE();
}

uint64_t BumpPointerSpace::GetBytesAllocated() {
  // Start out pre-determined amount (blocks which are not being allocated into).
  uint64_t total = static_cast<uint64_t>(bytes_allocated_.load(std::memory_order_relaxed));
  Thread* self = Thread::Current();
  MutexLock mu(self, *Locks::runtime_shutdown_lock_);
  MutexLock mu2(self, *Locks::thread_list_lock_);
  std::list<Thread*> thread_list = Runtime::Current()->GetThreadList()->GetList();
  MutexLock mu3(Thread::Current(), lock_);
  // If we don't have any blocks, we don't have any thread local buffers. This check is required
  // since there can exist multiple bump pointer spaces which exist at the same time.
  if (!block_sizes_.empty()) {
    for (Thread* thread : thread_list) {
      total += thread->GetThreadLocalBytesAllocated();
    }
  }
  return total;
}

uint64_t BumpPointerSpace::GetObjectsAllocated() {
  // Start out pre-determined amount (blocks which are not being allocated into).
  uint64_t total = static_cast<uint64_t>(objects_allocated_.load(std::memory_order_relaxed));
  Thread* self = Thread::Current();
  MutexLock mu(self, *Locks::runtime_shutdown_lock_);
  MutexLock mu2(self, *Locks::thread_list_lock_);
  std::list<Thread*> thread_list = Runtime::Current()->GetThreadList()->GetList();
  MutexLock mu3(Thread::Current(), lock_);
  // If we don't have any blocks, we don't have any thread local buffers. This check is required
  // since there can exist multiple bump pointer spaces which exist at the same time.
  if (!block_sizes_.empty()) {
    for (Thread* thread : thread_list) {
      total += thread->GetThreadLocalObjectsAllocated();
    }
  }
  return total;
}

void BumpPointerSpace::RevokeThreadLocalBuffersLocked(Thread* thread) {
  objects_allocated_.fetch_add(thread->GetThreadLocalObjectsAllocated(), std::memory_order_relaxed);
  bytes_allocated_.fetch_add(thread->GetThreadLocalBytesAllocated(), std::memory_order_relaxed);
  thread->ResetTlab();
}

bool BumpPointerSpace::AllocNewTlab(Thread* self, size_t bytes, size_t* bytes_tl_bulk_allocated) {
  bytes = RoundUp(bytes, kAlignment);
  MutexLock mu(Thread::Current(), lock_);
  RevokeThreadLocalBuffersLocked(self);
  uint8_t* start = AllocBlock(bytes);
  if (start == nullptr) {
    return false;
  }
  self->SetTlab(start, start + bytes, start + bytes);
  if (bytes_tl_bulk_allocated != nullptr) {
    *bytes_tl_bulk_allocated = bytes;
  }
  return true;
}

bool BumpPointerSpace::LogFragmentationAllocFailure(std::ostream& os,
                                                    size_t failed_alloc_bytes) {
  size_t max_contiguous_allocation = Limit() - End();
  if (failed_alloc_bytes > max_contiguous_allocation) {
    os << "; failed due to fragmentation (largest possible contiguous allocation "
       <<  max_contiguous_allocation << " bytes)";
    return true;
  }
  // Caller's job to print failed_alloc_bytes.
  return false;
}

size_t BumpPointerSpace::AllocationSizeNonvirtual(mirror::Object* obj, size_t* usable_size) {
  size_t num_bytes = obj->SizeOf();
  if (usable_size != nullptr) {
    *usable_size = RoundUp(num_bytes, kAlignment);
  }
  return num_bytes;
}

uint8_t* BumpPointerSpace::AlignEnd(Thread* self, size_t alignment, Heap* heap) {
  Locks::mutator_lock_->AssertExclusiveHeld(self);
  DCHECK(IsAligned<kAlignment>(alignment));
  uint8_t* end = end_.load(std::memory_order_relaxed);
  uint8_t* aligned_end = AlignUp(end, alignment);
  ptrdiff_t diff = aligned_end - end;
  if (diff > 0) {
    end_.store(aligned_end, std::memory_order_relaxed);
    heap->AddBytesAllocated(diff);
    // If we have blocks after the main one. Then just add the diff to the last
    // block.
    MutexLock mu(self, lock_);
    if (!block_sizes_.empty()) {
      block_sizes_.back() += diff;
    }
  }
  return aligned_end;
}

std::vector<size_t>* BumpPointerSpace::GetBlockSizes(Thread* self, size_t* main_block_size) {
  std::vector<size_t>* block_sizes = nullptr;
  MutexLock mu(self, lock_);
  if (!block_sizes_.empty()) {
    block_sizes = new std::vector<size_t>(block_sizes_.begin(), block_sizes_.end());
  } else {
    UpdateMainBlock();
  }
  *main_block_size = main_block_size_;
  return block_sizes;
}

void BumpPointerSpace::SetBlockSizes(Thread* self,
                                     const size_t main_block_size,
                                     const size_t first_valid_idx) {
  MutexLock mu(self, lock_);
  main_block_size_ = main_block_size;
  if (!block_sizes_.empty()) {
    block_sizes_.erase(block_sizes_.begin(), block_sizes_.begin() + first_valid_idx);
  }
  size_t size = main_block_size;
  for (size_t block_size : block_sizes_) {
    size += block_size;
  }
  DCHECK(IsAligned<kAlignment>(size));
  end_.store(Begin() + size, std::memory_order_relaxed);
}

}  // namespace space
}  // namespace gc
}  // namespace art
