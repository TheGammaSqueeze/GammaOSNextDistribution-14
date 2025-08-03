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

#include "reference_queue.h"

#include "accounting/card_table-inl.h"
#include "base/mutex.h"
#include "collector/concurrent_copying.h"
#include "heap.h"
#include "mirror/class-inl.h"
#include "mirror/object-inl.h"
#include "mirror/reference-inl.h"
#include "object_callbacks.h"

namespace art HIDDEN {
namespace gc {

ReferenceQueue::ReferenceQueue(Mutex* lock) : lock_(lock), list_(nullptr) {
}

void ReferenceQueue::AtomicEnqueueIfNotEnqueued(Thread* self, ObjPtr<mirror::Reference> ref) {
  DCHECK(ref != nullptr);
  MutexLock mu(self, *lock_);
  if (ref->IsUnprocessed()) {
    EnqueueReference(ref);
  }
}

void ReferenceQueue::EnqueueReference(ObjPtr<mirror::Reference> ref) {
  DCHECK(ref != nullptr);
  CHECK(ref->IsUnprocessed());
  if (IsEmpty()) {
    // 1 element cyclic queue, ie: Reference ref = ..; ref.pendingNext = ref;
    list_ = ref.Ptr();
  } else {
    // The list is owned by the GC, everything that has been inserted must already be at least
    // gray.
    ObjPtr<mirror::Reference> head = list_->GetPendingNext<kWithoutReadBarrier>();
    DCHECK(head != nullptr);
    ref->SetPendingNext(head);
  }
  // Add the reference in the middle to preserve the cycle.
  list_->SetPendingNext(ref);
}

ObjPtr<mirror::Reference> ReferenceQueue::DequeuePendingReference() {
  DCHECK(!IsEmpty());
  ObjPtr<mirror::Reference> ref = list_->GetPendingNext<kWithoutReadBarrier>();
  DCHECK(ref != nullptr);
  // Note: the following code is thread-safe because it is only called from ProcessReferences which
  // is single threaded.
  if (list_ == ref) {
    list_ = nullptr;
  } else {
    ObjPtr<mirror::Reference> next = ref->GetPendingNext<kWithoutReadBarrier>();
    list_->SetPendingNext(next);
  }
  ref->SetPendingNext(nullptr);
  return ref;
}

// This must be called whenever DequeuePendingReference is called.
void ReferenceQueue::DisableReadBarrierForReference(ObjPtr<mirror::Reference> ref,
                                                    std::memory_order order) {
  Heap* heap = Runtime::Current()->GetHeap();
  if (kUseBakerReadBarrier && heap->CurrentCollectorType() == kCollectorTypeCC &&
      heap->ConcurrentCopyingCollector()->IsActive()) {
    // Change the gray ptr we left in ConcurrentCopying::ProcessMarkStackRef() to non-gray.
    // We check IsActive() above because we don't want to do this when the zygote compaction
    // collector (SemiSpace) is running.
    CHECK(ref != nullptr);
    collector::ConcurrentCopying* concurrent_copying = heap->ConcurrentCopyingCollector();
    uint32_t rb_state = ref->GetReadBarrierState();
    if (rb_state == ReadBarrier::GrayState()) {
      ref->AtomicSetReadBarrierState(ReadBarrier::GrayState(), ReadBarrier::NonGrayState(), order);
      CHECK_EQ(ref->GetReadBarrierState(), ReadBarrier::NonGrayState());
    } else {
      // In ConcurrentCopying::ProcessMarkStackRef() we may leave a non-gray reference in the queue
      // and find it here, which is OK.
      CHECK_EQ(rb_state, ReadBarrier::NonGrayState()) << "ref=" << ref << " rb_state=" << rb_state;
      ObjPtr<mirror::Object> referent = ref->GetReferent<kWithoutReadBarrier>();
      // The referent could be null if it's cleared by a mutator (Reference.clear()).
      if (referent != nullptr) {
        CHECK(concurrent_copying->IsInToSpace(referent.Ptr()))
            << "ref=" << ref << " rb_state=" << ref->GetReadBarrierState()
            << " referent=" << referent;
      }
    }
  }
}

void ReferenceQueue::Dump(std::ostream& os) const {
  ObjPtr<mirror::Reference> cur = list_;
  os << "Reference starting at list_=" << list_ << "\n";
  if (cur == nullptr) {
    return;
  }
  do {
    ObjPtr<mirror::Reference> pending_next = cur->GetPendingNext();
    os << "Reference= " << cur << " PendingNext=" << pending_next;
    if (cur->IsFinalizerReferenceInstance()) {
      os << " Zombie=" << cur->AsFinalizerReference()->GetZombie();
    }
    os << "\n";
    cur = pending_next;
  } while (cur != list_);
}

size_t ReferenceQueue::GetLength() const {
  size_t count = 0;
  ObjPtr<mirror::Reference> cur = list_;
  if (cur != nullptr) {
    do {
      ++count;
      cur = cur->GetPendingNext();
    } while (cur != list_);
  }
  return count;
}

void ReferenceQueue::ClearWhiteReferences(ReferenceQueue* cleared_references,
                                          collector::GarbageCollector* collector,
                                          bool report_cleared) {
  while (!IsEmpty()) {
    ObjPtr<mirror::Reference> ref = DequeuePendingReference();
    mirror::HeapReference<mirror::Object>* referent_addr = ref->GetReferentReferenceAddr();
    // do_atomic_update is false because this happens during the reference processing phase where
    // Reference.clear() would block.
    if (!collector->IsNullOrMarkedHeapReference(referent_addr, /*do_atomic_update=*/false)) {
      // Referent is white, clear it.
      if (Runtime::Current()->IsActiveTransaction()) {
        ref->ClearReferent<true>();
      } else {
        ref->ClearReferent<false>();
      }
      cleared_references->EnqueueReference(ref);
      if (report_cleared) {
        static bool already_reported = false;
        if (!already_reported) {
          // TODO: Maybe do this only if the queue is non-null?
          LOG(WARNING)
              << "Cleared Reference was only reachable from finalizer (only reported once)";
          already_reported = true;
        }
      }
    }
    // Delay disabling the read barrier until here so that the ClearReferent call above in
    // transaction mode will trigger the read barrier.
    DisableReadBarrierForReference(ref, std::memory_order_relaxed);
  }
}

FinalizerStats ReferenceQueue::EnqueueFinalizerReferences(ReferenceQueue* cleared_references,
                                                collector::GarbageCollector* collector) {
  uint32_t num_refs(0), num_enqueued(0);
  while (!IsEmpty()) {
    ObjPtr<mirror::FinalizerReference> ref = DequeuePendingReference()->AsFinalizerReference();
    ++num_refs;
    mirror::HeapReference<mirror::Object>* referent_addr = ref->GetReferentReferenceAddr();
    // do_atomic_update is false because this happens during the reference processing phase where
    // Reference.clear() would block.
    if (!collector->IsNullOrMarkedHeapReference(referent_addr, /*do_atomic_update=*/false)) {
      ObjPtr<mirror::Object> forward_address = collector->MarkObject(referent_addr->AsMirrorPtr());
      // Move the updated referent to the zombie field.
      if (Runtime::Current()->IsActiveTransaction()) {
        ref->SetZombie<true>(forward_address);
        ref->ClearReferent<true>();
      } else {
        ref->SetZombie<false>(forward_address);
        ref->ClearReferent<false>();
      }
      cleared_references->EnqueueReference(ref);
      ++num_enqueued;
    }
    // Delay disabling the read barrier until here so that the ClearReferent call above in
    // transaction mode will trigger the read barrier.
    DisableReadBarrierForReference(ref->AsReference(), std::memory_order_relaxed);
  }
  return FinalizerStats(num_refs, num_enqueued);
}

uint32_t ReferenceQueue::ForwardSoftReferences(MarkObjectVisitor* visitor) {
  uint32_t num_refs(0);
  Thread* self = Thread::Current();
  static constexpr int SR_BUF_SIZE = 32;
  ObjPtr<mirror::Reference> buf[SR_BUF_SIZE];
  int n_entries;
  bool empty;
  do {
    {
      // Acquire lock only a few times and hold it as briefly as possible.
      MutexLock mu(self, *lock_);
      empty = IsEmpty();
      for (n_entries = 0; n_entries < SR_BUF_SIZE && !empty; ++n_entries) {
        // Dequeuing the Reference here means it could possibly be enqueued again during this GC.
        // That's unlikely and benign.
        buf[n_entries] = DequeuePendingReference();
        empty = IsEmpty();
      }
    }
    for (int i = 0; i < n_entries; ++i) {
      mirror::HeapReference<mirror::Object>* referent_addr = buf[i]->GetReferentReferenceAddr();
      if (referent_addr->AsMirrorPtr() != nullptr) {
        visitor->MarkHeapReference(referent_addr, /*do_atomic_update=*/ true);
        ++num_refs;
      }
      DisableReadBarrierForReference(buf[i]->AsReference(), std::memory_order_release);
    }
  } while (!empty);
  return num_refs;
}

void ReferenceQueue::UpdateRoots(IsMarkedVisitor* visitor) {
  if (list_ != nullptr) {
    list_ = down_cast<mirror::Reference*>(visitor->IsMarked(list_));
  }
}

}  // namespace gc
}  // namespace art
