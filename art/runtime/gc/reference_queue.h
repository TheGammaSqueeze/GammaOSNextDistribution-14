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

#ifndef ART_RUNTIME_GC_REFERENCE_QUEUE_H_
#define ART_RUNTIME_GC_REFERENCE_QUEUE_H_

#include <iosfwd>
#include <string>
#include <vector>

#include "base/atomic.h"
#include "base/locks.h"
#include "base/macros.h"
#include "base/timing_logger.h"
#include "jni.h"
#include "obj_ptr.h"
#include "offsets.h"
#include "runtime_globals.h"
#include "thread_pool.h"

namespace art HIDDEN {

class Mutex;

namespace mirror {
class Reference;
}  // namespace mirror

class IsMarkedVisitor;
class MarkObjectVisitor;

namespace gc {

namespace collector {
class GarbageCollector;
}  // namespace collector

class Heap;

struct FinalizerStats {
  FinalizerStats(size_t num_refs, size_t num_enqueued)
      : num_refs_(num_refs), num_enqueued_(num_enqueued) {}
  const uint32_t num_refs_;
  const uint32_t num_enqueued_;
};

// Used to temporarily store java.lang.ref.Reference(s) during GC and prior to queueing on the
// appropriate java.lang.ref.ReferenceQueue. The linked list is maintained as an unordered,
// circular, and singly-linked list using the pendingNext fields of the java.lang.ref.Reference
// objects.
class ReferenceQueue {
 public:
  explicit ReferenceQueue(Mutex* lock);

  // Enqueue a reference if it is unprocessed. Thread safe to call from multiple
  // threads since it uses a lock to avoid a race between checking for the references presence and
  // adding it.
  void AtomicEnqueueIfNotEnqueued(Thread* self, ObjPtr<mirror::Reference> ref)
      REQUIRES_SHARED(Locks::mutator_lock_) REQUIRES(!*lock_);

  // Enqueue a reference. The reference must be unprocessed.
  // Not thread safe, used when mutators are paused to minimize lock overhead.
  void EnqueueReference(ObjPtr<mirror::Reference> ref) REQUIRES_SHARED(Locks::mutator_lock_);

  // Dequeue a reference from the queue and return that dequeued reference.
  // Call DisableReadBarrierForReference for the reference that's returned from this function.
  ObjPtr<mirror::Reference> DequeuePendingReference() REQUIRES_SHARED(Locks::mutator_lock_);

  // If applicable, disable the read barrier for the reference after its referent is handled (see
  // ConcurrentCopying::ProcessMarkStackRef.) This must be called for a reference that's dequeued
  // from pending queue (DequeuePendingReference). 'order' is expected to be
  // 'release' if called outside 'weak-ref access disabled' critical section.
  // Otherwise 'relaxed' order will suffice.
  void DisableReadBarrierForReference(ObjPtr<mirror::Reference> ref, std::memory_order order)
      REQUIRES_SHARED(Locks::mutator_lock_);

  // Enqueues finalizer references with white referents.  White referents are blackened, moved to
  // the zombie field, and the referent field is cleared.
  FinalizerStats EnqueueFinalizerReferences(ReferenceQueue* cleared_references,
                                  collector::GarbageCollector* collector)
      REQUIRES_SHARED(Locks::mutator_lock_);

  // Walks the reference list marking and dequeuing any references subject to the reference
  // clearing policy.  References with a black referent are removed from the list.  References
  // with white referents biased toward saving are blackened and also removed from the list.
  // Returns the number of non-null soft references. May be called concurrently with
  // AtomicEnqueueIfNotEnqueued().
  uint32_t ForwardSoftReferences(MarkObjectVisitor* visitor)
      REQUIRES(!*lock_)
      REQUIRES_SHARED(Locks::mutator_lock_);

  // Unlink the reference list clearing references objects with white referents. Cleared references
  // registered to a reference queue are scheduled for appending by the heap worker thread.
  void ClearWhiteReferences(ReferenceQueue* cleared_references,
                            collector::GarbageCollector* collector,
                            bool report_cleared = false)
      REQUIRES_SHARED(Locks::mutator_lock_);

  void Dump(std::ostream& os) const REQUIRES_SHARED(Locks::mutator_lock_);
  size_t GetLength() const REQUIRES_SHARED(Locks::mutator_lock_);

  bool IsEmpty() const {
    return list_ == nullptr;
  }

  // Clear this queue. Only safe after handing off the contents elsewhere for further processing.
  void Clear() {
    list_ = nullptr;
  }

  mirror::Reference* GetList() REQUIRES_SHARED(Locks::mutator_lock_) {
    return list_;
  }

  // Visits list_, currently only used for the mark compact GC.
  void UpdateRoots(IsMarkedVisitor* visitor)
      REQUIRES_SHARED(Locks::mutator_lock_);

 private:
  // Lock, used for parallel GC reference enqueuing. It allows for multiple threads simultaneously
  // calling AtomicEnqueueIfNotEnqueued.
  Mutex* const lock_;
  // The actual reference list. Only a root for the mark compact GC since it
  // will be null during root marking for other GC types. Not an ObjPtr since it
  // is accessed from multiple threads.  Points to a singly-linked circular list
  // using the pendingNext field.
  mirror::Reference* list_;

  DISALLOW_IMPLICIT_CONSTRUCTORS(ReferenceQueue);
};

}  // namespace gc
}  // namespace art

#endif  // ART_RUNTIME_GC_REFERENCE_QUEUE_H_
