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

#include "reference_processor.h"

#include "art_field-inl.h"
#include "base/mutex.h"
#include "base/time_utils.h"
#include "base/utils.h"
#include "base/systrace.h"
#include "class_root-inl.h"
#include "collector/garbage_collector.h"
#include "jni/java_vm_ext.h"
#include "mirror/class-inl.h"
#include "mirror/object-inl.h"
#include "mirror/reference-inl.h"
#include "nativehelper/scoped_local_ref.h"
#include "object_callbacks.h"
#include "reflection.h"
#include "scoped_thread_state_change-inl.h"
#include "task_processor.h"
#include "thread-inl.h"
#include "thread_pool.h"
#include "well_known_classes.h"

namespace art HIDDEN {
namespace gc {

static constexpr bool kAsyncReferenceQueueAdd = false;

ReferenceProcessor::ReferenceProcessor()
    : collector_(nullptr),
      condition_("reference processor condition", *Locks::reference_processor_lock_) ,
      soft_reference_queue_(Locks::reference_queue_soft_references_lock_),
      weak_reference_queue_(Locks::reference_queue_weak_references_lock_),
      finalizer_reference_queue_(Locks::reference_queue_finalizer_references_lock_),
      phantom_reference_queue_(Locks::reference_queue_phantom_references_lock_),
      cleared_references_(Locks::reference_queue_cleared_references_lock_) {
}

static inline MemberOffset GetSlowPathFlagOffset(ObjPtr<mirror::Class> reference_class)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  DCHECK(reference_class == GetClassRoot<mirror::Reference>());
  // Second static field
  ArtField* field = reference_class->GetStaticField(1);
  DCHECK_STREQ(field->GetName(), "slowPathEnabled");
  return field->GetOffset();
}

static inline void SetSlowPathFlag(bool enabled) REQUIRES_SHARED(Locks::mutator_lock_) {
  ObjPtr<mirror::Class> reference_class = GetClassRoot<mirror::Reference>();
  MemberOffset slow_path_offset = GetSlowPathFlagOffset(reference_class);
  reference_class->SetFieldBoolean</* kTransactionActive= */ false, /* kCheckTransaction= */ false>(
      slow_path_offset, enabled ? 1 : 0);
}

void ReferenceProcessor::EnableSlowPath() {
  SetSlowPathFlag(/* enabled= */ true);
}

void ReferenceProcessor::DisableSlowPath(Thread* self) {
  SetSlowPathFlag(/* enabled= */ false);
  condition_.Broadcast(self);
}

bool ReferenceProcessor::SlowPathEnabled() {
  ObjPtr<mirror::Class> reference_class = GetClassRoot<mirror::Reference>();
  MemberOffset slow_path_offset = GetSlowPathFlagOffset(reference_class);
  return reference_class->GetFieldBoolean(slow_path_offset);
}

void ReferenceProcessor::BroadcastForSlowPath(Thread* self) {
  MutexLock mu(self, *Locks::reference_processor_lock_);
  condition_.Broadcast(self);
}

ObjPtr<mirror::Object> ReferenceProcessor::GetReferent(Thread* self,
                                                       ObjPtr<mirror::Reference> reference) {
  auto slow_path_required = [this, self]() REQUIRES_SHARED(Locks::mutator_lock_) {
    return gUseReadBarrier ? !self->GetWeakRefAccessEnabled() : SlowPathEnabled();
  };
  if (!slow_path_required()) {
    return reference->GetReferent();
  }
  // If the referent is null then it is already cleared, we can just return null since there is no
  // scenario where it becomes non-null during the reference processing phase.
  // A read barrier may be unsafe here, and we use the result only when it's null or marked.
  ObjPtr<mirror::Object> referent = reference->template GetReferent<kWithoutReadBarrier>();
  if (referent.IsNull()) {
    return referent;
  }

  bool started_trace = false;
  uint64_t start_millis;
  auto finish_trace = [](uint64_t start_millis) {
    ATraceEnd();
    uint64_t millis = MilliTime() - start_millis;
    static constexpr uint64_t kReportMillis = 10;  // Long enough to risk dropped frames.
    if (millis > kReportMillis) {
      LOG(WARNING) << "Weak pointer dereference blocked for " << millis << " milliseconds.";
    }
  };

  MutexLock mu(self, *Locks::reference_processor_lock_);
  // Keeping reference_processor_lock_ blocks the broadcast when we try to reenable the fast path.
  while (slow_path_required()) {
    DCHECK(collector_ != nullptr);
    const bool other_read_barrier = !kUseBakerReadBarrier && gUseReadBarrier;
    if (UNLIKELY(reference->IsFinalizerReferenceInstance()
                 || rp_state_ == RpState::kStarting /* too early to determine mark state */
                 || (other_read_barrier && reference->IsPhantomReferenceInstance()))) {
      // Odd cases in which it doesn't hurt to just wait, or the wait is likely to be very brief.

      // Check and run the empty checkpoint before blocking so the empty checkpoint will work in the
      // presence of threads blocking for weak ref access.
      self->CheckEmptyCheckpointFromWeakRefAccess(Locks::reference_processor_lock_);
      if (!started_trace) {
        ATraceBegin("GetReferent blocked");
        started_trace = true;
        start_millis = MilliTime();
      }
      condition_.WaitHoldingLocks(self);
      continue;
    }
    DCHECK(!reference->IsPhantomReferenceInstance());

    if (rp_state_ == RpState::kInitClearingDone) {
      // Reachable references have their final referent values.
      break;
    }
    // Although reference processing is not done, we can always predict the correct return value
    // based on the current mark state. No additional marking from finalizers has been done, since
    // we hold reference_processor_lock_, which is required to advance to kInitClearingDone.
    DCHECK(rp_state_ == RpState::kInitMarkingDone);
    // Re-load and re-check referent, since the current one may have been read before we acquired
    // reference_lock. In particular a Reference.clear() call may have intervened. (b/33569625)
    referent = reference->GetReferent<kWithoutReadBarrier>();
    ObjPtr<mirror::Object> forwarded_ref =
        referent.IsNull() ? nullptr : collector_->IsMarked(referent.Ptr());
    // Either the referent was marked, and forwarded_ref is the correct return value, or it
    // was not, and forwarded_ref == null, which is again the correct return value.
    if (started_trace) {
      finish_trace(start_millis);
    }
    return forwarded_ref;
  }
  if (started_trace) {
    finish_trace(start_millis);
  }
  return reference->GetReferent();
}

// Forward SoftReferences. Can be done before we disable Reference access. Only
// invoked if we are not clearing SoftReferences.
uint32_t ReferenceProcessor::ForwardSoftReferences(TimingLogger* timings) {
  TimingLogger::ScopedTiming split(
      concurrent_ ? "ForwardSoftReferences" : "(Paused)ForwardSoftReferences", timings);
  // We used to argue that we should be smarter about doing this conditionally, but it's unclear
  // that's actually better than the more predictable strategy of basically only clearing
  // SoftReferences just before we would otherwise run out of memory.
  uint32_t non_null_refs = soft_reference_queue_.ForwardSoftReferences(collector_);
  if (ATraceEnabled()) {
    static constexpr size_t kBufSize = 80;
    char buf[kBufSize];
    snprintf(buf, kBufSize, "Marking for %" PRIu32 " SoftReferences", non_null_refs);
    ATraceBegin(buf);
    collector_->ProcessMarkStack();
    ATraceEnd();
  } else {
    collector_->ProcessMarkStack();
  }
  return non_null_refs;
}

void ReferenceProcessor::Setup(Thread* self,
                               collector::GarbageCollector* collector,
                               bool concurrent,
                               bool clear_soft_references) {
  DCHECK(collector != nullptr);
  MutexLock mu(self, *Locks::reference_processor_lock_);
  collector_ = collector;
  rp_state_ = RpState::kStarting;
  concurrent_ = concurrent;
  clear_soft_references_ = clear_soft_references;
}

// Process reference class instances and schedule finalizations.
// We advance rp_state_ to signal partial completion for the benefit of GetReferent.
void ReferenceProcessor::ProcessReferences(Thread* self, TimingLogger* timings) {
  TimingLogger::ScopedTiming t(concurrent_ ? __FUNCTION__ : "(Paused)ProcessReferences", timings);
  if (!clear_soft_references_) {
    // Forward any additional SoftReferences we discovered late, now that reference access has been
    // inhibited.
    while (!soft_reference_queue_.IsEmpty()) {
      ForwardSoftReferences(timings);
    }
  }
  {
    MutexLock mu(self, *Locks::reference_processor_lock_);
    if (!gUseReadBarrier) {
      CHECK_EQ(SlowPathEnabled(), concurrent_) << "Slow path must be enabled iff concurrent";
    } else {
      // Weak ref access is enabled at Zygote compaction by SemiSpace (concurrent_ == false).
      CHECK_EQ(!self->GetWeakRefAccessEnabled(), concurrent_);
    }
    DCHECK(rp_state_ == RpState::kStarting);
    rp_state_ = RpState::kInitMarkingDone;
    condition_.Broadcast(self);
  }
  if (kIsDebugBuild && collector_->IsTransactionActive()) {
    // In transaction mode, we shouldn't enqueue any Reference to the queues.
    // See DelayReferenceReferent().
    DCHECK(soft_reference_queue_.IsEmpty());
    DCHECK(weak_reference_queue_.IsEmpty());
    DCHECK(finalizer_reference_queue_.IsEmpty());
    DCHECK(phantom_reference_queue_.IsEmpty());
  }
  // Clear all remaining soft and weak references with white referents.
  // This misses references only reachable through finalizers.
  soft_reference_queue_.ClearWhiteReferences(&cleared_references_, collector_);
  weak_reference_queue_.ClearWhiteReferences(&cleared_references_, collector_);
  // Defer PhantomReference processing until we've finished marking through finalizers.
  {
    // TODO: Capture mark state of some system weaks here. If the referent was marked here,
    // then it is now safe to return, since it can only refer to marked objects. If it becomes
    // marked below, that is no longer guaranteed.
    MutexLock mu(self, *Locks::reference_processor_lock_);
    rp_state_ = RpState::kInitClearingDone;
    // At this point, all mutator-accessible data is marked (black). Objects enqueued for
    // finalization will only be made available to the mutator via CollectClearedReferences after
    // we're fully done marking. Soft and WeakReferences accessible to the mutator have been
    // processed and refer only to black objects.  Thus there is no danger of the mutator getting
    // access to non-black objects.  Weak reference processing is still nominally suspended,
    // But many kinds of references, including all java.lang.ref ones, are handled normally from
    // here on. See GetReferent().
  }
  {
    TimingLogger::ScopedTiming t2(
        concurrent_ ? "EnqueueFinalizerReferences" : "(Paused)EnqueueFinalizerReferences", timings);
    // Preserve all white objects with finalize methods and schedule them for finalization.
    FinalizerStats finalizer_stats =
        finalizer_reference_queue_.EnqueueFinalizerReferences(&cleared_references_, collector_);
    if (ATraceEnabled()) {
      static constexpr size_t kBufSize = 80;
      char buf[kBufSize];
      snprintf(buf, kBufSize, "Marking from %" PRIu32 " / %" PRIu32 " finalizers",
               finalizer_stats.num_enqueued_, finalizer_stats.num_refs_);
      ATraceBegin(buf);
      collector_->ProcessMarkStack();
      ATraceEnd();
    } else {
      collector_->ProcessMarkStack();
    }
  }

  // Process all soft and weak references with white referents, where the references are reachable
  // only from finalizers. It is unclear that there is any way to do this without slightly
  // violating some language spec. We choose to apply normal Reference processing rules for these.
  // This exposes the following issues:
  // 1) In the case of an unmarked referent, we may end up enqueuing an "unreachable" reference.
  //    This appears unavoidable, since we need to clear the reference for safety, unless we
  //    mark the referent and undo finalization decisions for objects we encounter during marking.
  //    (Some versions of the RI seem to do something along these lines.)
  //    Or we could clear the reference without enqueuing it, which also seems strange and
  //    unhelpful.
  // 2) In the case of a marked referent, we will preserve a reference to objects that may have
  //    been enqueued for finalization. Again fixing this would seem to involve at least undoing
  //    previous finalization / reference clearing decisions. (This would also mean than an object
  //    containing both a strong and a WeakReference to the same referent could see the
  //    WeakReference cleared.)
  // The treatment in (2) is potentially quite dangerous, since Reference.get() can e.g. return a
  // finalized object containing pointers to native objects that have already been deallocated.
  // But it can be argued that this is just an instance of the broader rule that it is not safe
  // for finalizers to access otherwise inaccessible finalizable objects.
  soft_reference_queue_.ClearWhiteReferences(&cleared_references_, collector_,
                                             /*report_cleared=*/ true);
  weak_reference_queue_.ClearWhiteReferences(&cleared_references_, collector_,
                                             /*report_cleared=*/ true);

  // Clear all phantom references with white referents. It's fine to do this just once here.
  phantom_reference_queue_.ClearWhiteReferences(&cleared_references_, collector_);

  // At this point all reference queues other than the cleared references should be empty.
  DCHECK(soft_reference_queue_.IsEmpty());
  DCHECK(weak_reference_queue_.IsEmpty());
  DCHECK(finalizer_reference_queue_.IsEmpty());
  DCHECK(phantom_reference_queue_.IsEmpty());

  {
    MutexLock mu(self, *Locks::reference_processor_lock_);
    // Need to always do this since the next GC may be concurrent. Doing this for only concurrent
    // could result in a stale is_marked_callback_ being called before the reference processing
    // starts since there is a small window of time where slow_path_enabled_ is enabled but the
    // callback isn't yet set.
    if (!gUseReadBarrier && concurrent_) {
      // Done processing, disable the slow path and broadcast to the waiters.
      DisableSlowPath(self);
    }
  }
}

// Process the "referent" field in a java.lang.ref.Reference.  If the referent has not yet been
// marked, put it on the appropriate list in the heap for later processing.
void ReferenceProcessor::DelayReferenceReferent(ObjPtr<mirror::Class> klass,
                                                ObjPtr<mirror::Reference> ref,
                                                collector::GarbageCollector* collector) {
  // klass can be the class of the old object if the visitor already updated the class of ref.
  DCHECK(klass != nullptr);
  DCHECK(klass->IsTypeOfReferenceClass());
  mirror::HeapReference<mirror::Object>* referent = ref->GetReferentReferenceAddr();
  // do_atomic_update needs to be true because this happens outside of the reference processing
  // phase.
  if (!collector->IsNullOrMarkedHeapReference(referent, /*do_atomic_update=*/true)) {
    if (UNLIKELY(collector->IsTransactionActive())) {
      // In transaction mode, keep the referent alive and avoid any reference processing to avoid the
      // issue of rolling back reference processing.  do_atomic_update needs to be true because this
      // happens outside of the reference processing phase.
      if (!referent->IsNull()) {
        collector->MarkHeapReference(referent, /*do_atomic_update=*/ true);
      }
      return;
    }
    Thread* self = Thread::Current();
    // TODO: Remove these locks, and use atomic stacks for storing references?
    // We need to check that the references haven't already been enqueued since we can end up
    // scanning the same reference multiple times due to dirty cards.
    if (klass->IsSoftReferenceClass()) {
      soft_reference_queue_.AtomicEnqueueIfNotEnqueued(self, ref);
    } else if (klass->IsWeakReferenceClass()) {
      weak_reference_queue_.AtomicEnqueueIfNotEnqueued(self, ref);
    } else if (klass->IsFinalizerReferenceClass()) {
      finalizer_reference_queue_.AtomicEnqueueIfNotEnqueued(self, ref);
    } else if (klass->IsPhantomReferenceClass()) {
      phantom_reference_queue_.AtomicEnqueueIfNotEnqueued(self, ref);
    } else {
      LOG(FATAL) << "Invalid reference type " << klass->PrettyClass() << " " << std::hex
                 << klass->GetAccessFlags();
    }
  }
}

void ReferenceProcessor::UpdateRoots(IsMarkedVisitor* visitor) {
  cleared_references_.UpdateRoots(visitor);
}

class ClearedReferenceTask : public HeapTask {
 public:
  explicit ClearedReferenceTask(jobject cleared_references)
      : HeapTask(NanoTime()), cleared_references_(cleared_references) {
  }
  void Run(Thread* thread) override {
    ScopedObjectAccess soa(thread);
    WellKnownClasses::java_lang_ref_ReferenceQueue_add->InvokeStatic<'V', 'L'>(
        thread, soa.Decode<mirror::Object>(cleared_references_));
    soa.Env()->DeleteGlobalRef(cleared_references_);
  }

 private:
  const jobject cleared_references_;
};

SelfDeletingTask* ReferenceProcessor::CollectClearedReferences(Thread* self) {
  Locks::mutator_lock_->AssertNotHeld(self);
  // By default we don't actually need to do anything. Just return this no-op task to avoid having
  // to put in ifs.
  std::unique_ptr<SelfDeletingTask> result(new FunctionTask([](Thread*) {}));
  // When a runtime isn't started there are no reference queues to care about so ignore.
  if (!cleared_references_.IsEmpty()) {
    if (LIKELY(Runtime::Current()->IsStarted())) {
      jobject cleared_references;
      {
        ReaderMutexLock mu(self, *Locks::mutator_lock_);
        cleared_references = self->GetJniEnv()->GetVm()->AddGlobalRef(
            self, cleared_references_.GetList());
      }
      if (kAsyncReferenceQueueAdd) {
        // TODO: This can cause RunFinalization to terminate before newly freed objects are
        // finalized since they may not be enqueued by the time RunFinalization starts.
        Runtime::Current()->GetHeap()->GetTaskProcessor()->AddTask(
            self, new ClearedReferenceTask(cleared_references));
      } else {
        result.reset(new ClearedReferenceTask(cleared_references));
      }
    }
    cleared_references_.Clear();
  }
  return result.release();
}

void ReferenceProcessor::ClearReferent(ObjPtr<mirror::Reference> ref) {
  Thread* self = Thread::Current();
  MutexLock mu(self, *Locks::reference_processor_lock_);
  // Need to wait until reference processing is done since IsMarkedHeapReference does not have a
  // CAS. If we do not wait, it can result in the GC un-clearing references due to race conditions.
  // This also handles the race where the referent gets cleared after a null check but before
  // IsMarkedHeapReference is called.
  WaitUntilDoneProcessingReferences(self);
  if (Runtime::Current()->IsActiveTransaction()) {
    ref->ClearReferent<true>();
  } else {
    ref->ClearReferent<false>();
  }
}

void ReferenceProcessor::WaitUntilDoneProcessingReferences(Thread* self) {
  // Wait until we are done processing reference.
  while ((!gUseReadBarrier && SlowPathEnabled()) ||
         (gUseReadBarrier && !self->GetWeakRefAccessEnabled())) {
    // Check and run the empty checkpoint before blocking so the empty checkpoint will work in the
    // presence of threads blocking for weak ref access.
    self->CheckEmptyCheckpointFromWeakRefAccess(Locks::reference_processor_lock_);
    condition_.WaitHoldingLocks(self);
  }
}

bool ReferenceProcessor::MakeCircularListIfUnenqueued(
    ObjPtr<mirror::FinalizerReference> reference) {
  Thread* self = Thread::Current();
  MutexLock mu(self, *Locks::reference_processor_lock_);
  WaitUntilDoneProcessingReferences(self);
  // At this point, since the sentinel of the reference is live, it is guaranteed to not be
  // enqueued if we just finished processing references. Otherwise, we may be doing the main GC
  // phase. Since we are holding the reference processor lock, it guarantees that reference
  // processing can't begin. The GC could have just enqueued the reference one one of the internal
  // GC queues, but since we hold the lock finalizer_reference_queue_ lock it also prevents this
  // race.
  MutexLock mu2(self, *Locks::reference_queue_finalizer_references_lock_);
  if (reference->IsUnprocessed()) {
    CHECK(reference->IsFinalizerReferenceInstance());
    reference->SetPendingNext(reference);
    return true;
  }
  return false;
}

}  // namespace gc
}  // namespace art
