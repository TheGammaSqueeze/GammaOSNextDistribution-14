/*
 * Copyright (C) 2011 The Android Open Source Project
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

#ifndef ART_RUNTIME_THREAD_INL_H_
#define ART_RUNTIME_THREAD_INL_H_

#include "arch/instruction_set.h"
#include "base/aborting.h"
#include "base/casts.h"
#include "base/mutex-inl.h"
#include "base/time_utils.h"
#include "indirect_reference_table.h"
#include "jni/jni_env_ext.h"
#include "managed_stack-inl.h"
#include "obj_ptr-inl.h"
#include "runtime.h"
#include "thread-current-inl.h"
#include "thread.h"
#include "thread_list.h"
#include "thread_pool.h"

namespace art HIDDEN {

// Quickly access the current thread from a JNIEnv.
inline Thread* Thread::ForEnv(JNIEnv* env) {
  JNIEnvExt* full_env(down_cast<JNIEnvExt*>(env));
  return full_env->GetSelf();
}

inline size_t Thread::GetStackOverflowProtectedSize() {
  // The kMemoryToolStackGuardSizeScale is expected to be 1 when ASan is not enabled.
  // As the function is always inlined, in those cases each function call should turn
  // into a simple reference to gPageSize.
  return kMemoryToolStackGuardSizeScale * gPageSize;
}

inline ObjPtr<mirror::Object> Thread::DecodeJObject(jobject obj) const {
  if (obj == nullptr) {
    return nullptr;
  }
  IndirectRef ref = reinterpret_cast<IndirectRef>(obj);
  if (LIKELY(IndirectReferenceTable::IsJniTransitionOrLocalReference(ref))) {
    // For JNI transitions, the `jclass` for a static method points to the
    // `CompressedReference<>` in the `ArtMethod::declaring_class_` and other `jobject`
    // arguments point to spilled stack references but a `StackReference<>` is just
    // a subclass of `CompressedReference<>`. Local references also point to
    // a `CompressedReference<>` encapsulated in a `GcRoot<>`.
    if (kIsDebugBuild && IndirectReferenceTable::GetIndirectRefKind(ref) == kJniTransition) {
      CHECK(IsJniTransitionReference(obj));
    }
    auto* cref = IndirectReferenceTable::ClearIndirectRefKind<
        mirror::CompressedReference<mirror::Object>*>(ref);
    ObjPtr<mirror::Object> result = cref->AsMirrorPtr();
    if (kIsDebugBuild && IndirectReferenceTable::GetIndirectRefKind(ref) != kJniTransition) {
      CHECK_EQ(result, tlsPtr_.jni_env->locals_.Get(ref));
    }
    return result;
  } else {
    return DecodeGlobalJObject(obj);
  }
}

inline void Thread::AllowThreadSuspension() {
  CheckSuspend();
  // Invalidate the current thread's object pointers (ObjPtr) to catch possible moving GC bugs due
  // to missing handles.
  PoisonObjectPointers();
}

inline void Thread::CheckSuspend(bool implicit) {
  DCHECK_EQ(Thread::Current(), this);
  while (true) {
    StateAndFlags state_and_flags = GetStateAndFlags(std::memory_order_relaxed);
    if (LIKELY(!state_and_flags.IsAnyOfFlagsSet(SuspendOrCheckpointRequestFlags()))) {
      break;
    } else if (state_and_flags.IsFlagSet(ThreadFlag::kCheckpointRequest)) {
      RunCheckpointFunction();
    } else if (state_and_flags.IsFlagSet(ThreadFlag::kSuspendRequest) &&
               !state_and_flags.IsFlagSet(ThreadFlag::kSuspensionImmune)) {
      FullSuspendCheck(implicit);
      implicit = false;  // We do not need to `MadviseAwayAlternateSignalStack()` anymore.
    } else if (state_and_flags.IsFlagSet(ThreadFlag::kEmptyCheckpointRequest)) {
      RunEmptyCheckpoint();
    } else {
      DCHECK(state_and_flags.IsFlagSet(ThreadFlag::kSuspensionImmune));
      break;
    }
  }
  if (implicit) {
    // For implicit suspend check we want to `madvise()` away
    // the alternate signal stack to avoid wasting memory.
    MadviseAwayAlternateSignalStack();
  }
}

inline void Thread::CheckEmptyCheckpointFromWeakRefAccess(BaseMutex* cond_var_mutex) {
  Thread* self = Thread::Current();
  DCHECK_EQ(self, this);
  for (;;) {
    if (ReadFlag(ThreadFlag::kEmptyCheckpointRequest)) {
      RunEmptyCheckpoint();
      // Check we hold only an expected mutex when accessing weak ref.
      if (kIsDebugBuild) {
        for (int i = kLockLevelCount - 1; i >= 0; --i) {
          BaseMutex* held_mutex = self->GetHeldMutex(static_cast<LockLevel>(i));
          if (held_mutex != nullptr && held_mutex != GetMutatorLock() &&
              held_mutex != cond_var_mutex &&
              held_mutex != cp_placeholder_mutex_.load(std::memory_order_relaxed)) {
            // placeholder_mutex may still be nullptr. That's OK.
            CHECK(Locks::IsExpectedOnWeakRefAccess(held_mutex))
                << "Holding unexpected mutex " << held_mutex->GetName()
                << " when accessing weak ref";
          }
        }
      }
    } else {
      break;
    }
  }
}

inline void Thread::CheckEmptyCheckpointFromMutex() {
  DCHECK_EQ(Thread::Current(), this);
  for (;;) {
    if (ReadFlag(ThreadFlag::kEmptyCheckpointRequest)) {
      RunEmptyCheckpoint();
    } else {
      break;
    }
  }
}

inline ThreadState Thread::SetState(ThreadState new_state) {
  // Should only be used to change between suspended states.
  // Cannot use this code to change into or from Runnable as changing to Runnable should
  // fail if the `ThreadFlag::kSuspendRequest` is set and changing from Runnable might
  // miss passing an active suspend barrier.
  DCHECK_NE(new_state, ThreadState::kRunnable);
  if (kIsDebugBuild && this != Thread::Current()) {
    std::string name;
    GetThreadName(name);
    LOG(FATAL) << "Thread \"" << name << "\"(" << this << " != Thread::Current()="
               << Thread::Current() << ") changing state to " << new_state;
  }

  while (true) {
    StateAndFlags old_state_and_flags = GetStateAndFlags(std::memory_order_relaxed);
    CHECK_NE(old_state_and_flags.GetState(), ThreadState::kRunnable)
        << new_state << " " << *this << " " << *Thread::Current();
    StateAndFlags new_state_and_flags = old_state_and_flags.WithState(new_state);
    bool done =
        tls32_.state_and_flags.CompareAndSetWeakRelaxed(old_state_and_flags.GetValue(),
                                                        new_state_and_flags.GetValue());
    if (done) {
      return static_cast<ThreadState>(old_state_and_flags.GetState());
    }
  }
}

inline bool Thread::IsThreadSuspensionAllowable() const {
  if (tls32_.no_thread_suspension != 0) {
    return false;
  }
  for (int i = kLockLevelCount - 1; i >= 0; --i) {
    if (i != kMutatorLock &&
        i != kUserCodeSuspensionLock &&
        GetHeldMutex(static_cast<LockLevel>(i)) != nullptr) {
      return false;
    }
  }
  // Thread autoanalysis isn't able to understand that the GetHeldMutex(...) or AssertHeld means we
  // have the mutex meaning we need to do this hack.
  auto is_suspending_for_user_code = [this]() NO_THREAD_SAFETY_ANALYSIS {
    return tls32_.user_code_suspend_count != 0;
  };
  if (GetHeldMutex(kUserCodeSuspensionLock) != nullptr && is_suspending_for_user_code()) {
    return false;
  }
  return true;
}

inline void Thread::AssertThreadSuspensionIsAllowable(bool check_locks) const {
  if (kIsDebugBuild) {
    if (gAborting == 0) {
      CHECK_EQ(0u, tls32_.no_thread_suspension) << tlsPtr_.last_no_thread_suspension_cause;
    }
    if (check_locks) {
      bool bad_mutexes_held = false;
      for (int i = kLockLevelCount - 1; i >= 0; --i) {
        // We expect no locks except the mutator lock. User code suspension lock is OK as long as
        // we aren't going to be held suspended due to SuspendReason::kForUserCode.
        if (i != kMutatorLock && i != kUserCodeSuspensionLock) {
          BaseMutex* held_mutex = GetHeldMutex(static_cast<LockLevel>(i));
          if (held_mutex != nullptr) {
            LOG(ERROR) << "holding \"" << held_mutex->GetName()
                      << "\" at point where thread suspension is expected";
            bad_mutexes_held = true;
          }
        }
      }
      // Make sure that if we hold the user_code_suspension_lock_ we aren't suspending due to
      // user_code_suspend_count which would prevent the thread from ever waking up.  Thread
      // autoanalysis isn't able to understand that the GetHeldMutex(...) or AssertHeld means we
      // have the mutex meaning we need to do this hack.
      auto is_suspending_for_user_code = [this]() NO_THREAD_SAFETY_ANALYSIS {
        return tls32_.user_code_suspend_count != 0;
      };
      if (GetHeldMutex(kUserCodeSuspensionLock) != nullptr && is_suspending_for_user_code()) {
        LOG(ERROR) << "suspending due to user-code while holding \""
                   << Locks::user_code_suspension_lock_->GetName() << "\"! Thread would never "
                   << "wake up.";
        bad_mutexes_held = true;
      }
      if (gAborting == 0) {
        CHECK(!bad_mutexes_held);
      }
    }
  }
}

inline void Thread::TransitionToSuspendedAndRunCheckpoints(ThreadState new_state) {
  DCHECK_NE(new_state, ThreadState::kRunnable);
  while (true) {
    StateAndFlags old_state_and_flags = GetStateAndFlags(std::memory_order_relaxed);
    DCHECK_EQ(old_state_and_flags.GetState(), ThreadState::kRunnable);
    if (UNLIKELY(old_state_and_flags.IsFlagSet(ThreadFlag::kCheckpointRequest))) {
      IncrementStatsCounter(&checkpoint_count_);
      RunCheckpointFunction();
      continue;
    }
    if (UNLIKELY(old_state_and_flags.IsFlagSet(ThreadFlag::kEmptyCheckpointRequest))) {
      RunEmptyCheckpoint();
      continue;
    }
    // Change the state but keep the current flags (kCheckpointRequest is clear).
    DCHECK(!old_state_and_flags.IsFlagSet(ThreadFlag::kCheckpointRequest));
    DCHECK(!old_state_and_flags.IsFlagSet(ThreadFlag::kEmptyCheckpointRequest));
    StateAndFlags new_state_and_flags = old_state_and_flags.WithState(new_state);

    // CAS the value, ensuring that prior memory operations are visible to any thread
    // that observes that we are suspended.
    bool done =
        tls32_.state_and_flags.CompareAndSetWeakRelease(old_state_and_flags.GetValue(),
                                                        new_state_and_flags.GetValue());
    if (LIKELY(done)) {
      IncrementStatsCounter(&suspended_count_);
      break;
    }
  }
}

inline void Thread::CheckActiveSuspendBarriers() {
  DCHECK_NE(GetState(), ThreadState::kRunnable);
  while (true) {
    StateAndFlags state_and_flags = GetStateAndFlags(std::memory_order_relaxed);
    if (LIKELY(!state_and_flags.IsFlagSet(ThreadFlag::kCheckpointRequest) &&
               !state_and_flags.IsFlagSet(ThreadFlag::kEmptyCheckpointRequest) &&
               !state_and_flags.IsFlagSet(ThreadFlag::kActiveSuspendBarrier))) {
      break;
    } else if (state_and_flags.IsFlagSet(ThreadFlag::kActiveSuspendBarrier)) {
      PassActiveSuspendBarriers();
    } else {
      // Impossible
      LOG(FATAL) << "Fatal, thread transitioned into suspended without running the checkpoint";
    }
  }
}

inline void Thread::CheckBarrierInactive(WrappedSuspend1Barrier* suspend1_barrier) {
  for (WrappedSuspend1Barrier* w = tlsPtr_.active_suspend1_barriers; w != nullptr; w = w->next_) {
    CHECK_EQ(w->magic_, WrappedSuspend1Barrier::kMagic)
        << "first = " << tlsPtr_.active_suspend1_barriers << " current = " << w
        << " next = " << w->next_;
    CHECK_NE(w, suspend1_barrier);
  }
}

inline void Thread::AddSuspend1Barrier(WrappedSuspend1Barrier* suspend1_barrier) {
  if (tlsPtr_.active_suspend1_barriers != nullptr) {
    CHECK_EQ(tlsPtr_.active_suspend1_barriers->magic_, WrappedSuspend1Barrier::kMagic)
        << "first = " << tlsPtr_.active_suspend1_barriers;
  }
  CHECK_EQ(suspend1_barrier->magic_, WrappedSuspend1Barrier::kMagic);
  suspend1_barrier->next_ = tlsPtr_.active_suspend1_barriers;
  tlsPtr_.active_suspend1_barriers = suspend1_barrier;
}

inline void Thread::RemoveFirstSuspend1Barrier(WrappedSuspend1Barrier* suspend1_barrier) {
  DCHECK_EQ(tlsPtr_.active_suspend1_barriers, suspend1_barrier);
  tlsPtr_.active_suspend1_barriers = tlsPtr_.active_suspend1_barriers->next_;
}

inline void Thread::RemoveSuspend1Barrier(WrappedSuspend1Barrier* barrier) {
  // 'barrier' should be in the list. If not, we will get a SIGSEGV with fault address of 4 or 8.
  WrappedSuspend1Barrier** last = &tlsPtr_.active_suspend1_barriers;
  while (*last != barrier) {
    last = &((*last)->next_);
  }
  *last = (*last)->next_;
}

inline bool Thread::HasActiveSuspendBarrier() {
  return tlsPtr_.active_suspend1_barriers != nullptr ||
         tlsPtr_.active_suspendall_barrier != nullptr;
}

inline void Thread::TransitionFromRunnableToSuspended(ThreadState new_state) {
  // Note: JNI stubs inline a fast path of this method that transitions to suspended if
  // there are no flags set and then clears the `held_mutexes[kMutatorLock]` (this comes
  // from a specialized `BaseMutex::RegisterAsLockedImpl(., kMutatorLock)` inlined from
  // the `GetMutatorLock()->TransitionFromRunnableToSuspended(this)` below).
  // Therefore any code added here (other than debug build assertions) should be gated
  // on some flag being set, so that the JNI stub can take the slow path to get here.
  AssertThreadSuspensionIsAllowable();
  PoisonObjectPointersIfDebug();
  DCHECK_EQ(this, Thread::Current());
  // Change to non-runnable state, thereby appearing suspended to the system.
  TransitionToSuspendedAndRunCheckpoints(new_state);
  // Mark the release of the share of the mutator lock.
  GetMutatorLock()->TransitionFromRunnableToSuspended(this);
  // Once suspended - check the active suspend barrier flag
  CheckActiveSuspendBarriers();
}

inline ThreadState Thread::TransitionFromSuspendedToRunnable() {
  // Note: JNI stubs inline a fast path of this method that transitions to Runnable if
  // there are no flags set and then stores the mutator lock to `held_mutexes[kMutatorLock]`
  // (this comes from a specialized `BaseMutex::RegisterAsUnlockedImpl(., kMutatorLock)`
  // inlined from the `GetMutatorLock()->TransitionFromSuspendedToRunnable(this)` below).
  // Therefore any code added here (other than debug build assertions) should be gated
  // on some flag being set, so that the JNI stub can take the slow path to get here.
  DCHECK(this == Current());
  StateAndFlags old_state_and_flags = GetStateAndFlags(std::memory_order_relaxed);
  ThreadState old_state = old_state_and_flags.GetState();
  DCHECK_NE(old_state, ThreadState::kRunnable);
  while (true) {
    GetMutatorLock()->AssertNotHeld(this);  // Otherwise we starve GC.
    // Optimize for the return from native code case - this is the fast path.
    // Atomically change from suspended to runnable if no suspend request pending.
    constexpr uint32_t kCheckedFlags =
        SuspendOrCheckpointRequestFlags() |
        enum_cast<uint32_t>(ThreadFlag::kActiveSuspendBarrier) |
        FlipFunctionFlags();
    if (LIKELY(!old_state_and_flags.IsAnyOfFlagsSet(kCheckedFlags))) {
      // CAS the value with a memory barrier.
      StateAndFlags new_state_and_flags = old_state_and_flags.WithState(ThreadState::kRunnable);
      if (LIKELY(tls32_.state_and_flags.CompareAndSetWeakAcquire(old_state_and_flags.GetValue(),
                                                                 new_state_and_flags.GetValue()))) {
        // Mark the acquisition of a share of the mutator lock.
        GetMutatorLock()->TransitionFromSuspendedToRunnable(this);
        break;
      }
    } else if (old_state_and_flags.IsFlagSet(ThreadFlag::kActiveSuspendBarrier)) {
      PassActiveSuspendBarriers();
    } else if (UNLIKELY(old_state_and_flags.IsFlagSet(ThreadFlag::kCheckpointRequest) ||
                        old_state_and_flags.IsFlagSet(ThreadFlag::kEmptyCheckpointRequest))) {
      // Checkpoint flags should not be set while in suspended state.
      static_assert(static_cast<std::underlying_type_t<ThreadState>>(ThreadState::kRunnable) == 0u);
      LOG(FATAL) << "Transitioning to Runnable with checkpoint flag,"
                 // Note: Keeping unused flags. If they are set, it points to memory corruption.
                 << " flags=" << old_state_and_flags.WithState(ThreadState::kRunnable).GetValue()
                 << " state=" << old_state_and_flags.GetState();
    } else if (old_state_and_flags.IsFlagSet(ThreadFlag::kSuspendRequest)) {
      // Wait while our suspend count is non-zero.

      // We pass null to the MutexLock as we may be in a situation where the
      // runtime is shutting down. Guarding ourselves from that situation
      // requires to take the shutdown lock, which is undesirable here.
      Thread* thread_to_pass = nullptr;
      if (kIsDebugBuild && !IsDaemon()) {
        // We know we can make our debug locking checks on non-daemon threads,
        // so re-enable them on debug builds.
        thread_to_pass = this;
      }
      MutexLock mu(thread_to_pass, *Locks::thread_suspend_count_lock_);
      // Reload state and flags after locking the mutex.
      old_state_and_flags = GetStateAndFlags(std::memory_order_relaxed);
      DCHECK_EQ(old_state, old_state_and_flags.GetState());
      while (old_state_and_flags.IsFlagSet(ThreadFlag::kSuspendRequest)) {
        // Re-check when Thread::resume_cond_ is notified.
        Thread::resume_cond_->Wait(thread_to_pass);
        // Reload state and flags after waiting.
        old_state_and_flags = GetStateAndFlags(std::memory_order_relaxed);
        DCHECK_EQ(old_state, old_state_and_flags.GetState());
      }
      DCHECK_EQ(GetSuspendCount(), 0);
    } else if (UNLIKELY(old_state_and_flags.IsFlagSet(ThreadFlag::kRunningFlipFunction))) {
      DCHECK(!old_state_and_flags.IsFlagSet(ThreadFlag::kPendingFlipFunction));
      // Do this before transitioning to runnable, both because we shouldn't wait in a runnable
      // state, and so that the thread running the flip function can DCHECK we're not runnable.
      WaitForFlipFunction(this);
    } else if (old_state_and_flags.IsFlagSet(ThreadFlag::kPendingFlipFunction)) {
      // Logically acquire mutator lock in shared mode.
      DCHECK(!old_state_and_flags.IsFlagSet(ThreadFlag::kRunningFlipFunction));
      if (EnsureFlipFunctionStarted(this, this, old_state_and_flags)) {
        break;
      }
    }
    // Reload state and flags.
    old_state_and_flags = GetStateAndFlags(std::memory_order_relaxed);
    DCHECK_EQ(old_state, old_state_and_flags.GetState());
  }
  DCHECK_EQ(this->GetState(), ThreadState::kRunnable);
  return static_cast<ThreadState>(old_state);
}

inline mirror::Object* Thread::AllocTlab(size_t bytes) {
  DCHECK_GE(TlabSize(), bytes);
  ++tlsPtr_.thread_local_objects;
  mirror::Object* ret = reinterpret_cast<mirror::Object*>(tlsPtr_.thread_local_pos);
  tlsPtr_.thread_local_pos += bytes;
  return ret;
}

inline bool Thread::PushOnThreadLocalAllocationStack(mirror::Object* obj) {
  DCHECK_LE(tlsPtr_.thread_local_alloc_stack_top, tlsPtr_.thread_local_alloc_stack_end);
  if (tlsPtr_.thread_local_alloc_stack_top < tlsPtr_.thread_local_alloc_stack_end) {
    // There's room.
    DCHECK_LE(reinterpret_cast<uint8_t*>(tlsPtr_.thread_local_alloc_stack_top) +
              sizeof(StackReference<mirror::Object>),
              reinterpret_cast<uint8_t*>(tlsPtr_.thread_local_alloc_stack_end));
    DCHECK(tlsPtr_.thread_local_alloc_stack_top->AsMirrorPtr() == nullptr);
    tlsPtr_.thread_local_alloc_stack_top->Assign(obj);
    ++tlsPtr_.thread_local_alloc_stack_top;
    return true;
  }
  return false;
}

inline bool Thread::GetWeakRefAccessEnabled() const {
  DCHECK(gUseReadBarrier);
  DCHECK(this == Thread::Current());
  WeakRefAccessState s = tls32_.weak_ref_access_enabled.load(std::memory_order_relaxed);
  if (LIKELY(s == WeakRefAccessState::kVisiblyEnabled)) {
    return true;
  }
  s = tls32_.weak_ref_access_enabled.load(std::memory_order_acquire);
  if (s == WeakRefAccessState::kVisiblyEnabled) {
    return true;
  } else if (s == WeakRefAccessState::kDisabled) {
    return false;
  }
  DCHECK(s == WeakRefAccessState::kEnabled)
      << "state = " << static_cast<std::underlying_type_t<WeakRefAccessState>>(s);
  // The state is only changed back to DISABLED during a checkpoint. Thus no other thread can
  // change the value concurrently here. No other thread reads the value we store here, so there
  // is no need for a release store.
  tls32_.weak_ref_access_enabled.store(WeakRefAccessState::kVisiblyEnabled,
                                       std::memory_order_relaxed);
  return true;
}

inline void Thread::SetThreadLocalAllocationStack(StackReference<mirror::Object>* start,
                                                  StackReference<mirror::Object>* end) {
  DCHECK(Thread::Current() == this) << "Should be called by self";
  DCHECK(start != nullptr);
  DCHECK(end != nullptr);
  DCHECK_ALIGNED(start, sizeof(StackReference<mirror::Object>));
  DCHECK_ALIGNED(end, sizeof(StackReference<mirror::Object>));
  DCHECK_LT(start, end);
  tlsPtr_.thread_local_alloc_stack_end = end;
  tlsPtr_.thread_local_alloc_stack_top = start;
}

inline void Thread::RevokeThreadLocalAllocationStack() {
  if (kIsDebugBuild) {
    // Note: self is not necessarily equal to this thread since thread may be suspended.
    Thread* self = Thread::Current();
    DCHECK(this == self || GetState() != ThreadState::kRunnable)
        << GetState() << " thread " << this << " self " << self;
  }
  tlsPtr_.thread_local_alloc_stack_end = nullptr;
  tlsPtr_.thread_local_alloc_stack_top = nullptr;
}

inline void Thread::PoisonObjectPointersIfDebug() {
  if (kObjPtrPoisoning) {
    Thread::Current()->PoisonObjectPointers();
  }
}

inline void Thread::IncrementSuspendCount(Thread* self,
                                          AtomicInteger* suspendall_barrier,
                                          WrappedSuspend1Barrier* suspend1_barrier,
                                          SuspendReason reason) {
  if (kIsDebugBuild) {
    Locks::thread_suspend_count_lock_->AssertHeld(self);
    if (this != self) {
      Locks::thread_list_lock_->AssertHeld(self);
    }
  }
  if (UNLIKELY(reason == SuspendReason::kForUserCode)) {
    Locks::user_code_suspension_lock_->AssertHeld(self);
  }

  uint32_t flags = enum_cast<uint32_t>(ThreadFlag::kSuspendRequest);
  if (suspendall_barrier != nullptr) {
    DCHECK(suspend1_barrier == nullptr);
    DCHECK(tlsPtr_.active_suspendall_barrier == nullptr);
    tlsPtr_.active_suspendall_barrier = suspendall_barrier;
    flags |= enum_cast<uint32_t>(ThreadFlag::kActiveSuspendBarrier);
  } else if (suspend1_barrier != nullptr) {
    AddSuspend1Barrier(suspend1_barrier);
    flags |= enum_cast<uint32_t>(ThreadFlag::kActiveSuspendBarrier);
  }

  ++tls32_.suspend_count;
  if (reason == SuspendReason::kForUserCode) {
    ++tls32_.user_code_suspend_count;
  }

  // Two bits might be set simultaneously.
  tls32_.state_and_flags.fetch_or(flags, std::memory_order_release);
  TriggerSuspend();
}

inline void Thread::IncrementSuspendCount(Thread* self) {
  IncrementSuspendCount(self, nullptr, nullptr, SuspendReason::kInternal);
}

inline void Thread::DecrementSuspendCount(Thread* self, bool for_user_code) {
  DCHECK(ReadFlag(ThreadFlag::kSuspendRequest));
  Locks::thread_suspend_count_lock_->AssertHeld(self);
  if (UNLIKELY(tls32_.suspend_count <= 0)) {
    UnsafeLogFatalForSuspendCount(self, this);
    UNREACHABLE();
  }
  if (for_user_code) {
    Locks::user_code_suspension_lock_->AssertHeld(self);
    if (UNLIKELY(tls32_.user_code_suspend_count <= 0)) {
      LOG(ERROR) << "user_code_suspend_count incorrect";
      UnsafeLogFatalForSuspendCount(self, this);
      UNREACHABLE();
    }
    --tls32_.user_code_suspend_count;
  }

  --tls32_.suspend_count;

  if (tls32_.suspend_count == 0) {
    AtomicClearFlag(ThreadFlag::kSuspendRequest, std::memory_order_release);
  }
}

inline ShadowFrame* Thread::PushShadowFrame(ShadowFrame* new_top_frame) {
  new_top_frame->CheckConsistentVRegs();
  return tlsPtr_.managed_stack.PushShadowFrame(new_top_frame);
}

inline ShadowFrame* Thread::PopShadowFrame() {
  return tlsPtr_.managed_stack.PopShadowFrame();
}

inline uint8_t* Thread::GetStackEndForInterpreter(bool implicit_overflow_check) const {
  uint8_t* end = tlsPtr_.stack_end + (implicit_overflow_check
      ? GetStackOverflowReservedBytes(kRuntimeISA)
          : 0);
  if (kIsDebugBuild) {
    // In a debuggable build, but especially under ASAN, the access-checks interpreter has a
    // potentially humongous stack size. We don't want to take too much of the stack regularly,
    // so do not increase the regular reserved size (for compiled code etc) and only report the
    // virtually smaller stack to the interpreter here.
    end += GetStackOverflowReservedBytes(kRuntimeISA);
  }
  return end;
}

inline void Thread::ResetDefaultStackEnd() {
  // Our stacks grow down, so we want stack_end_ to be near there, but reserving enough room
  // to throw a StackOverflowError.
  tlsPtr_.stack_end = tlsPtr_.stack_begin + GetStackOverflowReservedBytes(kRuntimeISA);
}

inline void Thread::NotifyOnThreadExit(ThreadExitFlag* tef) {
  DCHECK_EQ(tef->exited_, false);
  DCHECK(tlsPtr_.thread_exit_flags == nullptr || !tlsPtr_.thread_exit_flags->exited_);
  tef->next_ = tlsPtr_.thread_exit_flags;
  tlsPtr_.thread_exit_flags = tef;
  if (tef->next_ != nullptr) {
    DCHECK(!tef->next_->HasExited());
    tef->next_->prev_ = tef;
  }
  tef->prev_ = nullptr;
}

inline void Thread::UnregisterThreadExitFlag(ThreadExitFlag* tef) {
  if (tef->HasExited()) {
    // List is no longer used; each client will deallocate its own ThreadExitFlag.
    return;
  }
  DCHECK(IsRegistered(tef));
  // Remove tef from the list.
  if (tef->next_ != nullptr) {
    tef->next_->prev_ = tef->prev_;
  }
  if (tef->prev_ == nullptr) {
    DCHECK_EQ(tlsPtr_.thread_exit_flags, tef);
    tlsPtr_.thread_exit_flags = tef->next_;
  } else {
    DCHECK_NE(tlsPtr_.thread_exit_flags, tef);
    tef->prev_->next_ = tef->next_;
  }
  DCHECK(tlsPtr_.thread_exit_flags == nullptr || tlsPtr_.thread_exit_flags->prev_ == nullptr);
}

inline void Thread::DCheckUnregisteredEverywhere(ThreadExitFlag* first, ThreadExitFlag* last) {
  if (!kIsDebugBuild) {
    return;
  }
  Thread* self = Thread::Current();
  MutexLock mu(self, *Locks::thread_list_lock_);
  Runtime::Current()->GetThreadList()->ForEach([&](Thread* t) REQUIRES(Locks::thread_list_lock_) {
    for (ThreadExitFlag* tef = t->tlsPtr_.thread_exit_flags; tef != nullptr; tef = tef->next_) {
      CHECK(tef < first || tef > last)
          << "tef = " << std::hex << tef << " first = " << first << std::dec;
    }
    // Also perform a minimal consistency check on each list.
    ThreadExitFlag* flags = t->tlsPtr_.thread_exit_flags;
    CHECK(flags == nullptr || flags->prev_ == nullptr);
  });
}

inline bool Thread::IsRegistered(ThreadExitFlag* query_tef) {
  for (ThreadExitFlag* tef = tlsPtr_.thread_exit_flags; tef != nullptr; tef = tef->next_) {
    if (tef == query_tef) {
      return true;
    }
  }
  return false;
}

inline void Thread::DisallowPreMonitorMutexes() {
  if (kIsDebugBuild) {
    CHECK(this == Thread::Current());
    CHECK(GetHeldMutex(kMonitorLock) == nullptr);
    // Pretend we hold a kMonitorLock level mutex to detect disallowed mutex
    // acquisitions by checkpoint Run() methods.  We don't normally register or thus check
    // kMonitorLock level mutexes, but this is an exception.
    Mutex* ph = cp_placeholder_mutex_.load(std::memory_order_acquire);
    if (UNLIKELY(ph == nullptr)) {
      Mutex* new_ph = new Mutex("checkpoint placeholder mutex", kMonitorLock);
      if (LIKELY(cp_placeholder_mutex_.compare_exchange_strong(ph, new_ph))) {
        ph = new_ph;
      } else {
        // ph now has the value set by another thread.
        delete new_ph;
      }
    }
    SetHeldMutex(kMonitorLock, ph);
  }
}

// Undo the effect of the previous call. Again only invoked by the thread itself.
inline void Thread::AllowPreMonitorMutexes() {
  if (kIsDebugBuild) {
    CHECK_EQ(GetHeldMutex(kMonitorLock), cp_placeholder_mutex_.load(std::memory_order_relaxed));
    SetHeldMutex(kMonitorLock, nullptr);
  }
}

}  // namespace art

#endif  // ART_RUNTIME_THREAD_INL_H_
