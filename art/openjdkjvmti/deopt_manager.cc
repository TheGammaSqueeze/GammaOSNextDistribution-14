/* Copyright (C) 2017 The Android Open Source Project
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This file implements interfaces from the file jvmti.h. This implementation
 * is licensed under the same terms as the file jvmti.h.  The
 * copyright and license information for the file jvmti.h follows.
 *
 * Copyright (c) 2003, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

#include <functional>
#include <iosfwd>
#include <mutex>

#include "deopt_manager.h"

#include "art_jvmti.h"
#include "art_method-inl.h"
#include "base/enums.h"
#include "base/mutex-inl.h"
#include "dex/dex_file_annotations.h"
#include "dex/modifiers.h"
#include "events-inl.h"
#include "gc/collector_type.h"
#include "gc/heap.h"
#include "gc/scoped_gc_critical_section.h"
#include "instrumentation.h"
#include "jit/jit.h"
#include "jit/jit_code_cache.h"
#include "jni/jni_internal.h"
#include "mirror/class-inl.h"
#include "mirror/object_array-inl.h"
#include "nativehelper/scoped_local_ref.h"
#include "oat/oat_file_manager.h"
#include "read_barrier_config.h"
#include "runtime_callbacks.h"
#include "scoped_thread_state_change-inl.h"
#include "scoped_thread_state_change.h"
#include "thread-current-inl.h"
#include "thread_list.h"
#include "ti_phase.h"

namespace openjdkjvmti {

static constexpr const char* kInstrumentationKey = "JVMTI_DeoptRequester";

// We could make this much more selective in the future so we only return true when we
// actually care about the method at this time (ie active frames had locals changed). For now we
// just assume that if anything has changed any frame's locals we care about all methods. This only
// impacts whether we are able to OSR or not so maybe not really important to maintain frame
// specific information.
bool JvmtiMethodInspectionCallback::HaveLocalsChanged() {
  return manager_->HaveLocalsChanged();
}

DeoptManager::DeoptManager()
  : deoptimization_status_lock_("JVMTI_DeoptimizationStatusLock",
                                static_cast<art::LockLevel>(
                                    art::LockLevel::kClassLinkerClassesLock + 1)),
    deoptimization_condition_("JVMTI_DeoptimizationCondition", deoptimization_status_lock_),
    performing_deoptimization_(false),
    global_deopt_count_(0),
    deopter_count_(0),
    breakpoint_status_lock_("JVMTI_BreakpointStatusLock",
                            static_cast<art::LockLevel>(art::LockLevel::kAbortLock + 1)),
    inspection_callback_(this),
    set_local_variable_called_(false) { }

void DeoptManager::Setup() {
  art::ScopedThreadStateChange stsc(art::Thread::Current(),
                                    art::ThreadState::kWaitingForDebuggerToAttach);
  art::ScopedSuspendAll ssa("Add method Inspection Callback");
  art::RuntimeCallbacks* callbacks = art::Runtime::Current()->GetRuntimeCallbacks();
  callbacks->AddMethodInspectionCallback(&inspection_callback_);
}

void DeoptManager::DumpDeoptInfo(art::Thread* self, std::ostream& stream) {
  art::ScopedObjectAccess soa(self);
  art::MutexLock mutll(self, *art::Locks::thread_list_lock_);
  art::MutexLock mudsl(self, deoptimization_status_lock_);
  art::MutexLock mubsl(self, breakpoint_status_lock_);
  stream << "Deoptimizer count: " << deopter_count_ << "\n";
  stream << "Global deopt count: " << global_deopt_count_ << "\n";
  stream << "Can perform OSR: " << !set_local_variable_called_.load() << "\n";
  for (const auto& [bp, loc] : this->breakpoint_status_) {
    stream << "Breakpoint: " << bp->PrettyMethod() << " @ 0x" << std::hex << loc << "\n";
  }
  struct DumpThreadDeoptCount : public art::Closure {
   public:
    DumpThreadDeoptCount(std::ostream& stream, std::mutex& mu)
        : cnt_(0), stream_(stream), mu_(mu) {}
    void Run(art::Thread* self) override {
      {
        std::lock_guard<std::mutex> lg(mu_);
        std::string name;
        self->GetThreadName(name);
        stream_ << "Thread " << name << " (id: " << std::dec << self->GetThreadId()
                << ") force interpreter count " << self->ForceInterpreterCount() << "\n";
      }
      // Increment this after unlocking the mutex so we won't race its destructor.
      cnt_++;
    }

    void WaitForCount(size_t threads) {
      while (cnt_.load() != threads) {
        sched_yield();
      }
    }

   private:
    std::atomic<size_t> cnt_;
    std::ostream& stream_;
    std::mutex& mu_;
  };

  std::mutex mu;
  DumpThreadDeoptCount dtdc(stream, mu);
  auto func = [](art::Thread* thread, void* ctx) {
    reinterpret_cast<DumpThreadDeoptCount*>(ctx)->Run(thread);
  };
  art::Runtime::Current()->GetThreadList()->ForEach(func, &dtdc);
}

void DeoptManager::FinishSetup() {
  art::Thread* self = art::Thread::Current();
  art::Runtime* runtime = art::Runtime::Current();
  if (runtime->IsJavaDebuggable()) {
    return;
  }

  // See if we can enable all JVMTI functions.
  if (PhaseUtil::GetPhaseUnchecked() == JVMTI_PHASE_ONLOAD) {
    // We are still early enough to change the compiler options and get full JVMTI support.
    LOG(INFO) << "Openjdkjvmti plugin loaded on a non-debuggable runtime. Changing runtime to "
              << "debuggable state. Please pass '--debuggable' to dex2oat and "
              << "'-Xcompiler-option --debuggable' to dalvikvm in the future.";
    DCHECK(runtime->GetJit() == nullptr) << "Jit should not be running yet!";
    art::ScopedSuspendAll ssa(__FUNCTION__);
    // TODO check if we need to hold deoptimization_status_lock_ here.
    art::MutexLock mu(self, deoptimization_status_lock_);
    runtime->AddCompilerOption("--debuggable");
    runtime->SetRuntimeDebugState(art::Runtime::RuntimeDebugState::kJavaDebuggableAtInit);
    runtime->DeoptimizeBootImage();
    return;
  }

  // Runtime has already started in non-debuggable mode. Only kArtTiVersion agents can be
  // retrieved and they will all be best-effort.
  LOG(WARNING) << "Openjdkjvmti plugin was loaded on a non-debuggable Runtime. Plugin was "
               << "loaded too late to change runtime state to support all capabilities. Only "
               << "kArtTiVersion (0x" << std::hex << kArtTiVersion << ") environments are "
               << "available. Some functionality might not work properly.";

  // Transition the runtime to debuggable:
  // 1. Wait for any background verification tasks to finish. We don't support
  // background verification after moving to debuggable state.
  runtime->GetOatFileManager().WaitForBackgroundVerificationTasksToFinish();

  // Do the transition in ScopedJITSuspend, so we don't start any JIT compilations
  // before the transition to debuggable is finished.
  art::jit::ScopedJitSuspend suspend_jit;
  art::ScopedSuspendAll ssa(__FUNCTION__);

  // 2. Discard any JITed code that was generated before, since they would be
  // compiled without debug support.
  art::jit::Jit* jit = runtime->GetJit();
  if (jit != nullptr) {
    jit->GetCodeCache()->InvalidateAllCompiledCode();
    jit->GetCodeCache()->TransitionToDebuggable();
    jit->GetJitCompiler()->SetDebuggableCompilerOption(true);
  }

  // 3. Change the state to JavaDebuggable, so that debug features can be
  // enabled from now on.
  runtime->SetRuntimeDebugState(art::Runtime::RuntimeDebugState::kJavaDebuggable);

  // 4. Update all entrypoints to avoid using any AOT code.
  runtime->GetInstrumentation()->UpdateEntrypointsForDebuggable();
}

bool DeoptManager::MethodHasBreakpoints(art::ArtMethod* method) {
  art::MutexLock lk(art::Thread::Current(), breakpoint_status_lock_);
  return MethodHasBreakpointsLocked(method);
}

bool DeoptManager::MethodHasBreakpointsLocked(art::ArtMethod* method) {
  auto elem = breakpoint_status_.find(method);
  return elem != breakpoint_status_.end() && elem->second != 0;
}

void DeoptManager::RemoveDeoptimizeAllMethods() {
  art::Thread* self = art::Thread::Current();
  art::ScopedThreadSuspension sts(self, art::ThreadState::kSuspended);
  deoptimization_status_lock_.ExclusiveLock(self);
  RemoveDeoptimizeAllMethodsLocked(self);
}

void DeoptManager::AddDeoptimizeAllMethods() {
  art::Thread* self = art::Thread::Current();
  art::ScopedThreadSuspension sts(self, art::ThreadState::kSuspended);
  deoptimization_status_lock_.ExclusiveLock(self);
  AddDeoptimizeAllMethodsLocked(self);
}

void DeoptManager::AddMethodBreakpoint(art::ArtMethod* method) {
  DCHECK(method->IsInvokable());
  DCHECK(!method->IsProxyMethod()) << method->PrettyMethod();
  DCHECK(!method->IsNative()) << method->PrettyMethod();

  art::Thread* self = art::Thread::Current();
  method = method->GetCanonicalMethod();
  bool is_default = method->IsDefault();

  art::ScopedThreadSuspension sts(self, art::ThreadState::kSuspended);
  deoptimization_status_lock_.ExclusiveLock(self);
  {
    breakpoint_status_lock_.ExclusiveLock(self);

    DCHECK_GT(deopter_count_, 0u) << "unexpected deotpimization request";

    if (MethodHasBreakpointsLocked(method)) {
      // Don't need to do anything extra.
      breakpoint_status_[method]++;
      // Another thread might be deoptimizing the very method we just added new breakpoints for.
      // Wait for any deopts to finish before moving on.
      breakpoint_status_lock_.ExclusiveUnlock(self);
      WaitForDeoptimizationToFinish(self);
      return;
    }
    breakpoint_status_[method] = 1;
    breakpoint_status_lock_.ExclusiveUnlock(self);
  }
  auto instrumentation = art::Runtime::Current()->GetInstrumentation();
  if (instrumentation->IsForcedInterpretOnly()) {
    // We are already interpreting everything so no need to do anything.
    deoptimization_status_lock_.ExclusiveUnlock(self);
    return;
  } else if (is_default) {
    AddDeoptimizeAllMethodsLocked(self);
  } else {
    PerformLimitedDeoptimization(self, method);
  }
}

void DeoptManager::RemoveMethodBreakpoint(art::ArtMethod* method) {
  DCHECK(method->IsInvokable()) << method->PrettyMethod();
  DCHECK(!method->IsProxyMethod()) << method->PrettyMethod();
  DCHECK(!method->IsNative()) << method->PrettyMethod();

  art::Thread* self = art::Thread::Current();
  method = method->GetCanonicalMethod();
  bool is_default = method->IsDefault();

  art::ScopedThreadSuspension sts(self, art::ThreadState::kSuspended);
  // Ideally we should do a ScopedSuspendAll right here to get the full mutator_lock_ that we might
  // need but since that is very heavy we will instead just use a condition variable to make sure we
  // don't race with ourselves.
  deoptimization_status_lock_.ExclusiveLock(self);
  bool is_last_breakpoint;
  {
    art::MutexLock mu(self, breakpoint_status_lock_);

    DCHECK_GT(deopter_count_, 0u) << "unexpected deotpimization request";
    DCHECK(MethodHasBreakpointsLocked(method)) << "Breakpoint on a method was removed without "
                                              << "breakpoints present!";
    breakpoint_status_[method] -= 1;
    is_last_breakpoint = (breakpoint_status_[method] == 0);
  }
  auto instrumentation = art::Runtime::Current()->GetInstrumentation();
  if (UNLIKELY(instrumentation->IsForcedInterpretOnly())) {
    // We don't need to do anything since we are interpreting everything anyway.
    deoptimization_status_lock_.ExclusiveUnlock(self);
    return;
  } else if (is_last_breakpoint) {
    if (UNLIKELY(is_default)) {
      RemoveDeoptimizeAllMethodsLocked(self);
    } else {
      PerformLimitedUndeoptimization(self, method);
    }
  } else {
    // Another thread might be deoptimizing the very methods we just removed breakpoints from. Wait
    // for any deopts to finish before moving on.
    WaitForDeoptimizationToFinish(self);
  }
}

void DeoptManager::WaitForDeoptimizationToFinishLocked(art::Thread* self) {
  while (performing_deoptimization_) {
    deoptimization_condition_.Wait(self);
  }
}

void DeoptManager::WaitForDeoptimizationToFinish(art::Thread* self) {
  WaitForDeoptimizationToFinishLocked(self);
  deoptimization_status_lock_.ExclusiveUnlock(self);
}

// Users should make sure that only gc-critical-section safe code is used while a
// ScopedDeoptimizationContext exists.
class ScopedDeoptimizationContext : public art::ValueObject {
 public:
  ScopedDeoptimizationContext(art::Thread* self, DeoptManager* deopt)
      RELEASE(deopt->deoptimization_status_lock_)
      ACQUIRE(art::Locks::mutator_lock_)
      ACQUIRE(art::Roles::uninterruptible_)
      : self_(self),
        deopt_(deopt),
        critical_section_(self_, "JVMTI Deoptimizing methods"),
        uninterruptible_cause_(nullptr) {
    deopt_->WaitForDeoptimizationToFinishLocked(self_);
    DCHECK(!deopt->performing_deoptimization_)
        << "Already performing deoptimization on another thread!";
    // Use performing_deoptimization_ to keep track of the lock.
    deopt_->performing_deoptimization_ = true;
    deopt_->deoptimization_status_lock_.Unlock(self_);
    uninterruptible_cause_ = critical_section_.Enter(art::gc::kGcCauseInstrumentation,
                                                     art::gc::kCollectorTypeCriticalSection);
    art::Runtime::Current()->GetThreadList()->SuspendAll("JMVTI Deoptimizing methods",
                                                         /*long_suspend=*/ false);
  }

  ~ScopedDeoptimizationContext()
      RELEASE(art::Locks::mutator_lock_)
      RELEASE(art::Roles::uninterruptible_) {
    // Can be suspended again.
    critical_section_.Exit(uninterruptible_cause_);
    // Release the mutator lock.
    art::Runtime::Current()->GetThreadList()->ResumeAll();
    // Let other threads know it's fine to proceed.
    art::MutexLock lk(self_, deopt_->deoptimization_status_lock_);
    deopt_->performing_deoptimization_ = false;
    deopt_->deoptimization_condition_.Broadcast(self_);
  }

 private:
  art::Thread* self_;
  DeoptManager* deopt_;
  art::gc::GCCriticalSection critical_section_;
  const char* uninterruptible_cause_;
};

void DeoptManager::AddDeoptimizeAllMethodsLocked(art::Thread* self) {
  global_deopt_count_++;
  if (global_deopt_count_ == 1) {
    PerformGlobalDeoptimization(self);
  } else {
    WaitForDeoptimizationToFinish(self);
  }
}

void DeoptManager::Shutdown() {
  art::Thread* self = art::Thread::Current();
  art::Runtime* runtime = art::Runtime::Current();

  // Do the transition in ScopedJITSuspend, so we don't start any JIT compilations
  // before the transition to debuggable is finished.
  art::jit::ScopedJitSuspend suspend_jit;

  art::ScopedThreadStateChange sts(self, art::ThreadState::kSuspended);
  deoptimization_status_lock_.ExclusiveLock(self);
  ScopedDeoptimizationContext sdc(self, this);

  art::RuntimeCallbacks* callbacks = runtime->GetRuntimeCallbacks();
  callbacks->RemoveMethodInspectionCallback(&inspection_callback_);

  if (runtime->IsShuttingDown(self)) {
    return;
  }

  // If we attach a debugger to a non-debuggable runtime, we switch the runtime to debuggable to
  // provide a consistent (though still best effort) support. Since we are detaching the debugger
  // now, switch it back to non-debuggable if there are no other debugger / profiling tools are
  // active.
  runtime->GetInstrumentation()->DisableDeoptimization(kInstrumentationKey,
                                                       /*try_switch_to_non_debuggable=*/true);
  runtime->GetInstrumentation()->DisableDeoptimization(kDeoptManagerInstrumentationKey,
                                                       /*try_switch_to_non_debuggable=*/true);
}

void DeoptManager::RemoveDeoptimizeAllMethodsLocked(art::Thread* self) {
  DCHECK_GT(global_deopt_count_, 0u) << "Request to remove non-existent global deoptimization!";
  global_deopt_count_--;
  if (global_deopt_count_ == 0) {
    PerformGlobalUndeoptimization(self);
  } else {
    WaitForDeoptimizationToFinish(self);
  }
}

void DeoptManager::PerformLimitedDeoptimization(art::Thread* self, art::ArtMethod* method) {
  ScopedDeoptimizationContext sdc(self, this);
  art::Runtime::Current()->GetInstrumentation()->Deoptimize(method);
}

void DeoptManager::PerformLimitedUndeoptimization(art::Thread* self, art::ArtMethod* method) {
  ScopedDeoptimizationContext sdc(self, this);
  art::Runtime::Current()->GetInstrumentation()->Undeoptimize(method);
}

void DeoptManager::PerformGlobalDeoptimization(art::Thread* self) {
  ScopedDeoptimizationContext sdc(self, this);
  art::Runtime::Current()->GetInstrumentation()->DeoptimizeEverything(
      kDeoptManagerInstrumentationKey);
}

void DeoptManager::PerformGlobalUndeoptimization(art::Thread* self) {
  ScopedDeoptimizationContext sdc(self, this);
  art::Runtime::Current()->GetInstrumentation()->UndeoptimizeEverything(
      kDeoptManagerInstrumentationKey);
}

jvmtiError DeoptManager::AddDeoptimizeThreadMethods(art::ScopedObjectAccessUnchecked& soa, jthread jtarget) {
  art::Locks::thread_list_lock_->ExclusiveLock(soa.Self());
  art::Thread* target = nullptr;
  jvmtiError err = OK;
  if (!ThreadUtil::GetNativeThread(jtarget, soa, &target, &err)) {
    art::Locks::thread_list_lock_->ExclusiveUnlock(soa.Self());
    return err;
  }
  // We don't need additional locking here because we hold the Thread_list_lock_.
  if (target->IncrementForceInterpreterCount() == 1) {
    struct DeoptClosure : public art::Closure {
     public:
      explicit DeoptClosure(DeoptManager* manager) : manager_(manager) {}
      void Run(art::Thread* self) override REQUIRES_SHARED(art::Locks::mutator_lock_) {
        manager_->DeoptimizeThread(self);
      }

     private:
      DeoptManager* manager_;
    };
    DeoptClosure c(this);
    target->RequestSynchronousCheckpoint(&c);
  } else {
    art::Locks::thread_list_lock_->ExclusiveUnlock(soa.Self());
  }
  return OK;
}

jvmtiError DeoptManager::RemoveDeoptimizeThreadMethods(art::ScopedObjectAccessUnchecked& soa, jthread jtarget) {
  art::MutexLock mu(soa.Self(), *art::Locks::thread_list_lock_);
  art::Thread* target = nullptr;
  jvmtiError err = OK;
  if (!ThreadUtil::GetNativeThread(jtarget, soa, &target, &err)) {
    return err;
  }
  // We don't need additional locking here because we hold the Thread_list_lock_.
  DCHECK_GT(target->ForceInterpreterCount(), 0u);
  target->DecrementForceInterpreterCount();
  return OK;
}


void DeoptManager::RemoveDeoptimizationRequester() {
  art::Thread* self = art::Thread::Current();
  art::ScopedThreadStateChange sts(self, art::ThreadState::kSuspended);
  deoptimization_status_lock_.ExclusiveLock(self);
  DCHECK_GT(deopter_count_, 0u) << "Removing deoptimization requester without any being present";
  deopter_count_--;
  if (deopter_count_ == 0) {
    ScopedDeoptimizationContext sdc(self, this);
    art::Runtime::Current()->GetInstrumentation()->DisableDeoptimization(
        kInstrumentationKey, /*try_switch_to_non_debuggable=*/false);
    return;
  } else {
    deoptimization_status_lock_.ExclusiveUnlock(self);
  }
}

void DeoptManager::AddDeoptimizationRequester() {
  art::Thread* self = art::Thread::Current();
  art::ScopedThreadStateChange stsc(self, art::ThreadState::kSuspended);
  deoptimization_status_lock_.ExclusiveLock(self);
  deopter_count_++;
  if (deopter_count_ == 1) {
    // When we add a deoptimization requester, we should enable entry / exit hooks. We only call
    // this in debuggable runtimes and hence it won't be necessary to update entrypoints but we
    // still need to inform instrumentation that we need to actually run entry / exit hooks. Though
    // entrypoints are capable of running entry / exit hooks they won't run them unless enabled.
    ScopedDeoptimizationContext sdc(self, this);
    art::Runtime::Current()->GetInstrumentation()->EnableEntryExitHooks(kInstrumentationKey);
    return;
  }
  deoptimization_status_lock_.ExclusiveUnlock(self);
}

void DeoptManager::DeoptimizeThread(art::Thread* target) {
  // We might or might not be running on the target thread (self) so get Thread::Current
  // directly.
  art::ScopedThreadSuspension sts(art::Thread::Current(), art::ThreadState::kSuspended);
  art::gc::ScopedGCCriticalSection sgccs(art::Thread::Current(),
                                         art::gc::GcCause::kGcCauseDebugger,
                                         art::gc::CollectorType::kCollectorTypeDebugger);
  art::ScopedSuspendAll ssa("Instrument thread stack");
  // Prepare the stack so methods can be deoptimized as and when required.
  // This by itself doesn't cause any methods to deoptimize but enables
  // deoptimization on demand.
  art::Runtime::Current()->GetInstrumentation()->InstrumentThreadStack(target,
                                                                       /* force_deopt= */ false);
}

extern DeoptManager* gDeoptManager;
DeoptManager* DeoptManager::Get() {
  return gDeoptManager;
}

}  // namespace openjdkjvmti
