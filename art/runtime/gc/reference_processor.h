/*
 * Copyright (C) 2014 The Android Open Source Project
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

#ifndef ART_RUNTIME_GC_REFERENCE_PROCESSOR_H_
#define ART_RUNTIME_GC_REFERENCE_PROCESSOR_H_

#include "base/macros.h"
#include "base/locks.h"
#include "jni.h"
#include "reference_queue.h"
#include "runtime_globals.h"

namespace art HIDDEN {

class IsMarkedVisitor;
class TimingLogger;

namespace mirror {
class Class;
class FinalizerReference;
class Object;
class Reference;
}  // namespace mirror

namespace gc {

namespace collector {
class GarbageCollector;
}  // namespace collector

class Heap;

// Used to process java.lang.ref.Reference instances concurrently or paused.
class ReferenceProcessor {
 public:
  ReferenceProcessor();

  // Initialize for a reference processing pass. Called before suspending weak
  // access.
  void Setup(Thread* self,
             collector::GarbageCollector* collector,
             bool concurrent,
             bool clear_soft_references)
      REQUIRES(!Locks::reference_processor_lock_);
  // Enqueue all types of java.lang.ref.References, and mark through finalizers.
  // Assumes there is no concurrent mutator-driven marking, i.e. all potentially
  // mutator-accessible objects should be marked before this.
  void ProcessReferences(Thread* self, TimingLogger* timings)
      REQUIRES_SHARED(Locks::mutator_lock_)
      REQUIRES(Locks::heap_bitmap_lock_)
      REQUIRES(!Locks::reference_processor_lock_);

  // The slow path bool is contained in the reference class object, can only be set once
  // Only allow setting this with mutators suspended so that we can avoid using a lock in the
  // GetReferent fast path as an optimization.
  void EnableSlowPath() REQUIRES_SHARED(Locks::mutator_lock_);
  void BroadcastForSlowPath(Thread* self);
  // Decode the referent, may block if references are being processed. In the normal
  // no-read-barrier or Baker-read-barrier cases, we assume reference is not a PhantomReference.
  ObjPtr<mirror::Object> GetReferent(Thread* self, ObjPtr<mirror::Reference> reference)
      REQUIRES_SHARED(Locks::mutator_lock_) REQUIRES(!Locks::reference_processor_lock_);
  // Collects the cleared references and returns a task, to be executed after FinishGC, that will
  // enqueue all of them.
  SelfDeletingTask* CollectClearedReferences(Thread* self) REQUIRES(!Locks::mutator_lock_);
  void DelayReferenceReferent(ObjPtr<mirror::Class> klass,
                              ObjPtr<mirror::Reference> ref,
                              collector::GarbageCollector* collector)
      REQUIRES_SHARED(Locks::mutator_lock_);
  void UpdateRoots(IsMarkedVisitor* visitor)
      REQUIRES_SHARED(Locks::mutator_lock_, Locks::heap_bitmap_lock_);
  // Make a circular list with reference if it is not enqueued. Uses the finalizer queue lock.
  bool MakeCircularListIfUnenqueued(ObjPtr<mirror::FinalizerReference> reference)
      REQUIRES_SHARED(Locks::mutator_lock_)
      REQUIRES(!Locks::reference_processor_lock_,
               !Locks::reference_queue_finalizer_references_lock_);
  void ClearReferent(ObjPtr<mirror::Reference> ref)
      REQUIRES_SHARED(Locks::mutator_lock_)
      REQUIRES(!Locks::reference_processor_lock_);
  uint32_t ForwardSoftReferences(TimingLogger* timings)
      REQUIRES_SHARED(Locks::mutator_lock_);

 private:
  bool SlowPathEnabled() REQUIRES_SHARED(Locks::mutator_lock_);
  // Called by ProcessReferences.
  void DisableSlowPath(Thread* self) REQUIRES(Locks::reference_processor_lock_)
      REQUIRES_SHARED(Locks::mutator_lock_);
  // Wait until reference processing is done.
  void WaitUntilDoneProcessingReferences(Thread* self)
      REQUIRES_SHARED(Locks::mutator_lock_)
      REQUIRES(Locks::reference_processor_lock_);
  // Collector which is clearing references, used by the GetReferent to return referents which are
  // already marked. Only updated by thread currently running GC.
  // Guarded by reference_processor_lock_ when not read by collector. Only the collector changes
  // it.
  collector::GarbageCollector* collector_;
  // Reference processor state. Only valid while weak reference processing is suspended.
  // Used by GetReferent and friends to return early.
  enum class RpState : uint8_t { kStarting, kInitMarkingDone, kInitClearingDone };
  RpState rp_state_ GUARDED_BY(Locks::reference_processor_lock_);
  bool concurrent_;  // Running concurrently with mutator? Only used by GC thread.
  bool clear_soft_references_;  // Only used by GC thread.

  // Condition that people wait on if they attempt to get the referent of a reference while
  // processing is in progress. Broadcast when an empty checkpoint is requested, but not for other
  // checkpoints or thread suspensions. See mutator_gc_coord.md.
  ConditionVariable condition_ GUARDED_BY(Locks::reference_processor_lock_);
  // Reference queues used by the GC.
  ReferenceQueue soft_reference_queue_;
  ReferenceQueue weak_reference_queue_;
  ReferenceQueue finalizer_reference_queue_;
  ReferenceQueue phantom_reference_queue_;
  ReferenceQueue cleared_references_;

  DISALLOW_COPY_AND_ASSIGN(ReferenceProcessor);
};

}  // namespace gc
}  // namespace art

#endif  // ART_RUNTIME_GC_REFERENCE_PROCESSOR_H_
