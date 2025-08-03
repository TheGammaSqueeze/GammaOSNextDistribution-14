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

#include "thread.h"

#include <limits.h>  // for INT_MAX
#include <pthread.h>
#include <signal.h>
#include <stdlib.h>
#include <sys/resource.h>
#include <sys/time.h>

#include <algorithm>
#include <atomic>
#include <bitset>
#include <cerrno>
#include <iostream>
#include <list>
#include <optional>
#include <sstream>

#include "android-base/file.h"
#include "android-base/stringprintf.h"
#include "android-base/strings.h"

#include "unwindstack/AndroidUnwinder.h"

#include "arch/context-inl.h"
#include "arch/context.h"
#include "art_field-inl.h"
#include "art_method-inl.h"
#include "base/atomic.h"
#include "base/bit_utils.h"
#include "base/casts.h"
#include "base/file_utils.h"
#include "base/memory_tool.h"
#include "base/mutex.h"
#include "base/stl_util.h"
#include "base/systrace.h"
#include "base/time_utils.h"
#include "base/timing_logger.h"
#include "base/to_str.h"
#include "base/utils.h"
#include "class_linker-inl.h"
#include "class_root-inl.h"
#include "debugger.h"
#include "dex/descriptors_names.h"
#include "dex/dex_file-inl.h"
#include "dex/dex_file_annotations.h"
#include "dex/dex_file_types.h"
#include "entrypoints/entrypoint_utils.h"
#include "entrypoints/quick/quick_alloc_entrypoints.h"
#include "gc/accounting/card_table-inl.h"
#include "gc/accounting/heap_bitmap-inl.h"
#include "gc/allocator/rosalloc.h"
#include "gc/heap.h"
#include "gc/space/space-inl.h"
#include "gc_root.h"
#include "handle_scope-inl.h"
#include "indirect_reference_table-inl.h"
#include "instrumentation.h"
#include "intern_table.h"
#include "interpreter/interpreter.h"
#include "interpreter/shadow_frame-inl.h"
#include "java_frame_root_info.h"
#include "jni/java_vm_ext.h"
#include "jni/jni_internal.h"
#include "mirror/class-alloc-inl.h"
#include "mirror/class_loader.h"
#include "mirror/object_array-alloc-inl.h"
#include "mirror/object_array-inl.h"
#include "mirror/stack_frame_info.h"
#include "mirror/stack_trace_element.h"
#include "monitor.h"
#include "monitor_objects_stack_visitor.h"
#include "native_stack_dump.h"
#include "nativehelper/scoped_local_ref.h"
#include "nativehelper/scoped_utf_chars.h"
#include "nterp_helpers.h"
#include "nth_caller_visitor.h"
#include "oat/oat_quick_method_header.h"
#include "oat/stack_map.h"
#include "obj_ptr-inl.h"
#include "object_lock.h"
#include "palette/palette.h"
#include "quick/quick_method_frame_info.h"
#include "quick_exception_handler.h"
#include "read_barrier-inl.h"
#include "reflection.h"
#include "reflective_handle_scope-inl.h"
#include "runtime-inl.h"
#include "runtime.h"
#include "runtime_callbacks.h"
#include "scoped_thread_state_change-inl.h"
#include "scoped_disable_public_sdk_checker.h"
#include "stack.h"
#include "thread-inl.h"
#include "thread_list.h"
#include "trace.h"
#include "verify_object.h"
#include "well_known_classes-inl.h"

#ifdef ART_TARGET_ANDROID
#include <android/set_abort_message.h>
#endif

#if ART_USE_FUTEXES
#include "linux/futex.h"
#include "sys/syscall.h"
#ifndef SYS_futex
#define SYS_futex __NR_futex
#endif
#endif  // ART_USE_FUTEXES

#pragma clang diagnostic push
#pragma clang diagnostic error "-Wconversion"

extern "C" __attribute__((weak)) void* __hwasan_tag_pointer(const volatile void* p,
                                                            unsigned char tag);

namespace art HIDDEN {

using android::base::StringAppendV;
using android::base::StringPrintf;

extern "C" NO_RETURN void artDeoptimize(Thread* self, bool skip_method_exit_callbacks);

bool Thread::is_started_ = false;
pthread_key_t Thread::pthread_key_self_;
ConditionVariable* Thread::resume_cond_ = nullptr;
const size_t Thread::kStackOverflowImplicitCheckSize = GetStackOverflowReservedBytes(kRuntimeISA);
bool (*Thread::is_sensitive_thread_hook_)() = nullptr;
Thread* Thread::jit_sensitive_thread_ = nullptr;
std::atomic<Mutex*> Thread::cp_placeholder_mutex_(nullptr);
#ifndef __BIONIC__
thread_local Thread* Thread::self_tls_ = nullptr;
#endif

static constexpr bool kVerifyImageObjectsMarked = kIsDebugBuild;

static const char* kThreadNameDuringStartup = "<native thread without managed peer>";

void Thread::InitCardTable() {
  tlsPtr_.card_table = Runtime::Current()->GetHeap()->GetCardTable()->GetBiasedBegin();
}

static void UnimplementedEntryPoint() {
  UNIMPLEMENTED(FATAL);
}

void InitEntryPoints(JniEntryPoints* jpoints,
                     QuickEntryPoints* qpoints,
                     bool monitor_jni_entry_exit);
void UpdateReadBarrierEntrypoints(QuickEntryPoints* qpoints, bool is_active);

void Thread::SetIsGcMarkingAndUpdateEntrypoints(bool is_marking) {
  CHECK(gUseReadBarrier);
  tls32_.is_gc_marking = is_marking;
  UpdateReadBarrierEntrypoints(&tlsPtr_.quick_entrypoints, /* is_active= */ is_marking);
}

void Thread::InitTlsEntryPoints() {
  ScopedTrace trace("InitTlsEntryPoints");
  // Insert a placeholder so we can easily tell if we call an unimplemented entry point.
  uintptr_t* begin = reinterpret_cast<uintptr_t*>(&tlsPtr_.jni_entrypoints);
  uintptr_t* end = reinterpret_cast<uintptr_t*>(
      reinterpret_cast<uint8_t*>(&tlsPtr_.quick_entrypoints) + sizeof(tlsPtr_.quick_entrypoints));
  for (uintptr_t* it = begin; it != end; ++it) {
    *it = reinterpret_cast<uintptr_t>(UnimplementedEntryPoint);
  }
  bool monitor_jni_entry_exit = false;
  PaletteShouldReportJniInvocations(&monitor_jni_entry_exit);
  if (monitor_jni_entry_exit) {
    AtomicSetFlag(ThreadFlag::kMonitorJniEntryExit);
  }
  InitEntryPoints(&tlsPtr_.jni_entrypoints, &tlsPtr_.quick_entrypoints, monitor_jni_entry_exit);
}

void Thread::ResetQuickAllocEntryPointsForThread() {
  ResetQuickAllocEntryPoints(&tlsPtr_.quick_entrypoints);
}

class DeoptimizationContextRecord {
 public:
  DeoptimizationContextRecord(const JValue& ret_val,
                              bool is_reference,
                              bool from_code,
                              ObjPtr<mirror::Throwable> pending_exception,
                              DeoptimizationMethodType method_type,
                              DeoptimizationContextRecord* link)
      : ret_val_(ret_val),
        is_reference_(is_reference),
        from_code_(from_code),
        pending_exception_(pending_exception.Ptr()),
        deopt_method_type_(method_type),
        link_(link) {}

  JValue GetReturnValue() const { return ret_val_; }
  bool IsReference() const { return is_reference_; }
  bool GetFromCode() const { return from_code_; }
  ObjPtr<mirror::Throwable> GetPendingException() const REQUIRES_SHARED(Locks::mutator_lock_) {
    return pending_exception_;
  }
  DeoptimizationContextRecord* GetLink() const { return link_; }
  mirror::Object** GetReturnValueAsGCRoot() {
    DCHECK(is_reference_);
    return ret_val_.GetGCRoot();
  }
  mirror::Object** GetPendingExceptionAsGCRoot() {
    return reinterpret_cast<mirror::Object**>(&pending_exception_);
  }
  DeoptimizationMethodType GetDeoptimizationMethodType() const {
    return deopt_method_type_;
  }

 private:
  // The value returned by the method at the top of the stack before deoptimization.
  JValue ret_val_;

  // Indicates whether the returned value is a reference. If so, the GC will visit it.
  const bool is_reference_;

  // Whether the context was created from an explicit deoptimization in the code.
  const bool from_code_;

  // The exception that was pending before deoptimization (or null if there was no pending
  // exception).
  mirror::Throwable* pending_exception_;

  // Whether the context was created for an (idempotent) runtime method.
  const DeoptimizationMethodType deopt_method_type_;

  // A link to the previous DeoptimizationContextRecord.
  DeoptimizationContextRecord* const link_;

  DISALLOW_COPY_AND_ASSIGN(DeoptimizationContextRecord);
};

class StackedShadowFrameRecord {
 public:
  StackedShadowFrameRecord(ShadowFrame* shadow_frame,
                           StackedShadowFrameType type,
                           StackedShadowFrameRecord* link)
      : shadow_frame_(shadow_frame),
        type_(type),
        link_(link) {}

  ShadowFrame* GetShadowFrame() const { return shadow_frame_; }
  StackedShadowFrameType GetType() const { return type_; }
  StackedShadowFrameRecord* GetLink() const { return link_; }

 private:
  ShadowFrame* const shadow_frame_;
  const StackedShadowFrameType type_;
  StackedShadowFrameRecord* const link_;

  DISALLOW_COPY_AND_ASSIGN(StackedShadowFrameRecord);
};

void Thread::PushDeoptimizationContext(const JValue& return_value,
                                       bool is_reference,
                                       ObjPtr<mirror::Throwable> exception,
                                       bool from_code,
                                       DeoptimizationMethodType method_type) {
  DCHECK(exception != Thread::GetDeoptimizationException());
  DeoptimizationContextRecord* record = new DeoptimizationContextRecord(
      return_value,
      is_reference,
      from_code,
      exception,
      method_type,
      tlsPtr_.deoptimization_context_stack);
  tlsPtr_.deoptimization_context_stack = record;
}

void Thread::PopDeoptimizationContext(JValue* result,
                                      ObjPtr<mirror::Throwable>* exception,
                                      bool* from_code,
                                      DeoptimizationMethodType* method_type) {
  AssertHasDeoptimizationContext();
  DeoptimizationContextRecord* record = tlsPtr_.deoptimization_context_stack;
  tlsPtr_.deoptimization_context_stack = record->GetLink();
  result->SetJ(record->GetReturnValue().GetJ());
  *exception = record->GetPendingException();
  *from_code = record->GetFromCode();
  *method_type = record->GetDeoptimizationMethodType();
  delete record;
}

void Thread::AssertHasDeoptimizationContext() {
  CHECK(tlsPtr_.deoptimization_context_stack != nullptr)
      << "No deoptimization context for thread " << *this;
}

enum {
  kPermitAvailable = 0,  // Incrementing consumes the permit
  kNoPermit = 1,  // Incrementing marks as waiter waiting
  kNoPermitWaiterWaiting = 2
};

void Thread::Park(bool is_absolute, int64_t time) {
  DCHECK(this == Thread::Current());
#if ART_USE_FUTEXES
  // Consume the permit, or mark as waiting. This cannot cause park_state to go
  // outside of its valid range (0, 1, 2), because in all cases where 2 is
  // assigned it is set back to 1 before returning, and this method cannot run
  // concurrently with itself since it operates on the current thread.
  int old_state = tls32_.park_state_.fetch_add(1, std::memory_order_relaxed);
  if (old_state == kNoPermit) {
    // no permit was available. block thread until later.
    Runtime::Current()->GetRuntimeCallbacks()->ThreadParkStart(is_absolute, time);
    bool timed_out = false;
    if (!is_absolute && time == 0) {
      // Thread.getState() is documented to return waiting for untimed parks.
      ScopedThreadSuspension sts(this, ThreadState::kWaiting);
      DCHECK_EQ(NumberOfHeldMutexes(), 0u);
      int result = futex(tls32_.park_state_.Address(),
                     FUTEX_WAIT_PRIVATE,
                     /* sleep if val = */ kNoPermitWaiterWaiting,
                     /* timeout */ nullptr,
                     nullptr,
                     0);
      // This errno check must happen before the scope is closed, to ensure that
      // no destructors (such as ScopedThreadSuspension) overwrite errno.
      if (result == -1) {
        switch (errno) {
          case EAGAIN:
            FALLTHROUGH_INTENDED;
          case EINTR: break;  // park() is allowed to spuriously return
          default: PLOG(FATAL) << "Failed to park";
        }
      }
    } else if (time > 0) {
      // Only actually suspend and futex_wait if we're going to wait for some
      // positive amount of time - the kernel will reject negative times with
      // EINVAL, and a zero time will just noop.

      // Thread.getState() is documented to return timed wait for timed parks.
      ScopedThreadSuspension sts(this, ThreadState::kTimedWaiting);
      DCHECK_EQ(NumberOfHeldMutexes(), 0u);
      timespec timespec;
      int result = 0;
      if (is_absolute) {
        // Time is millis when scheduled for an absolute time
        timespec.tv_nsec = (time % 1000) * 1000000;
        timespec.tv_sec = SaturatedTimeT(time / 1000);
        // This odd looking pattern is recommended by futex documentation to
        // wait until an absolute deadline, with otherwise identical behavior to
        // FUTEX_WAIT_PRIVATE. This also allows parkUntil() to return at the
        // correct time when the system clock changes.
        result = futex(tls32_.park_state_.Address(),
                       FUTEX_WAIT_BITSET_PRIVATE | FUTEX_CLOCK_REALTIME,
                       /* sleep if val = */ kNoPermitWaiterWaiting,
                       &timespec,
                       nullptr,
                       static_cast<int>(FUTEX_BITSET_MATCH_ANY));
      } else {
        // Time is nanos when scheduled for a relative time
        timespec.tv_sec = SaturatedTimeT(time / 1000000000);
        timespec.tv_nsec = time % 1000000000;
        result = futex(tls32_.park_state_.Address(),
                       FUTEX_WAIT_PRIVATE,
                       /* sleep if val = */ kNoPermitWaiterWaiting,
                       &timespec,
                       nullptr,
                       0);
      }
      // This errno check must happen before the scope is closed, to ensure that
      // no destructors (such as ScopedThreadSuspension) overwrite errno.
      if (result == -1) {
        switch (errno) {
          case ETIMEDOUT:
            timed_out = true;
            FALLTHROUGH_INTENDED;
          case EAGAIN:
          case EINTR: break;  // park() is allowed to spuriously return
          default: PLOG(FATAL) << "Failed to park";
        }
      }
    }
    // Mark as no longer waiting, and consume permit if there is one.
    tls32_.park_state_.store(kNoPermit, std::memory_order_relaxed);
    // TODO: Call to signal jvmti here
    Runtime::Current()->GetRuntimeCallbacks()->ThreadParkFinished(timed_out);
  } else {
    // the fetch_add has consumed the permit. immediately return.
    DCHECK_EQ(old_state, kPermitAvailable);
  }
#else
  #pragma clang diagnostic push
  #pragma clang diagnostic warning "-W#warnings"
  #warning "LockSupport.park/unpark implemented as noops without FUTEX support."
  #pragma clang diagnostic pop
  UNUSED(is_absolute, time);
  UNIMPLEMENTED(WARNING);
  sched_yield();
#endif
}

void Thread::Unpark() {
#if ART_USE_FUTEXES
  // Set permit available; will be consumed either by fetch_add (when the thread
  // tries to park) or store (when the parked thread is woken up)
  if (tls32_.park_state_.exchange(kPermitAvailable, std::memory_order_relaxed)
      == kNoPermitWaiterWaiting) {
    int result = futex(tls32_.park_state_.Address(),
                       FUTEX_WAKE_PRIVATE,
                       /* number of waiters = */ 1,
                       nullptr,
                       nullptr,
                       0);
    if (result == -1) {
      PLOG(FATAL) << "Failed to unpark";
    }
  }
#else
  UNIMPLEMENTED(WARNING);
#endif
}

void Thread::PushStackedShadowFrame(ShadowFrame* sf, StackedShadowFrameType type) {
  StackedShadowFrameRecord* record = new StackedShadowFrameRecord(
      sf, type, tlsPtr_.stacked_shadow_frame_record);
  tlsPtr_.stacked_shadow_frame_record = record;
}

ShadowFrame* Thread::MaybePopDeoptimizedStackedShadowFrame() {
  StackedShadowFrameRecord* record = tlsPtr_.stacked_shadow_frame_record;
  if (record == nullptr ||
      record->GetType() != StackedShadowFrameType::kDeoptimizationShadowFrame) {
    return nullptr;
  }
  return PopStackedShadowFrame();
}

ShadowFrame* Thread::PopStackedShadowFrame() {
  StackedShadowFrameRecord* record = tlsPtr_.stacked_shadow_frame_record;
  DCHECK_NE(record, nullptr);
  tlsPtr_.stacked_shadow_frame_record = record->GetLink();
  ShadowFrame* shadow_frame = record->GetShadowFrame();
  delete record;
  return shadow_frame;
}

class FrameIdToShadowFrame {
 public:
  static FrameIdToShadowFrame* Create(size_t frame_id,
                                      ShadowFrame* shadow_frame,
                                      FrameIdToShadowFrame* next,
                                      size_t num_vregs) {
    // Append a bool array at the end to keep track of what vregs are updated by the debugger.
    uint8_t* memory = new uint8_t[sizeof(FrameIdToShadowFrame) + sizeof(bool) * num_vregs];
    return new (memory) FrameIdToShadowFrame(frame_id, shadow_frame, next);
  }

  static void Delete(FrameIdToShadowFrame* f) {
    uint8_t* memory = reinterpret_cast<uint8_t*>(f);
    delete[] memory;
  }

  size_t GetFrameId() const { return frame_id_; }
  ShadowFrame* GetShadowFrame() const { return shadow_frame_; }
  FrameIdToShadowFrame* GetNext() const { return next_; }
  void SetNext(FrameIdToShadowFrame* next) { next_ = next; }
  bool* GetUpdatedVRegFlags() {
    return updated_vreg_flags_;
  }

 private:
  FrameIdToShadowFrame(size_t frame_id,
                       ShadowFrame* shadow_frame,
                       FrameIdToShadowFrame* next)
      : frame_id_(frame_id),
        shadow_frame_(shadow_frame),
        next_(next) {}

  const size_t frame_id_;
  ShadowFrame* const shadow_frame_;
  FrameIdToShadowFrame* next_;
  bool updated_vreg_flags_[0];

  DISALLOW_COPY_AND_ASSIGN(FrameIdToShadowFrame);
};

static FrameIdToShadowFrame* FindFrameIdToShadowFrame(FrameIdToShadowFrame* head,
                                                      size_t frame_id) {
  FrameIdToShadowFrame* found = nullptr;
  for (FrameIdToShadowFrame* record = head; record != nullptr; record = record->GetNext()) {
    if (record->GetFrameId() == frame_id) {
      if (kIsDebugBuild) {
        // Check we have at most one record for this frame.
        CHECK(found == nullptr) << "Multiple records for the frame " << frame_id;
        found = record;
      } else {
        return record;
      }
    }
  }
  return found;
}

ShadowFrame* Thread::FindDebuggerShadowFrame(size_t frame_id) {
  FrameIdToShadowFrame* record = FindFrameIdToShadowFrame(
      tlsPtr_.frame_id_to_shadow_frame, frame_id);
  if (record != nullptr) {
    return record->GetShadowFrame();
  }
  return nullptr;
}

// Must only be called when FindDebuggerShadowFrame(frame_id) returns non-nullptr.
bool* Thread::GetUpdatedVRegFlags(size_t frame_id) {
  FrameIdToShadowFrame* record = FindFrameIdToShadowFrame(
      tlsPtr_.frame_id_to_shadow_frame, frame_id);
  CHECK(record != nullptr);
  return record->GetUpdatedVRegFlags();
}

ShadowFrame* Thread::FindOrCreateDebuggerShadowFrame(size_t frame_id,
                                                     uint32_t num_vregs,
                                                     ArtMethod* method,
                                                     uint32_t dex_pc) {
  ShadowFrame* shadow_frame = FindDebuggerShadowFrame(frame_id);
  if (shadow_frame != nullptr) {
    return shadow_frame;
  }
  VLOG(deopt) << "Create pre-deopted ShadowFrame for " << ArtMethod::PrettyMethod(method);
  shadow_frame = ShadowFrame::CreateDeoptimizedFrame(num_vregs, method, dex_pc);
  FrameIdToShadowFrame* record = FrameIdToShadowFrame::Create(frame_id,
                                                              shadow_frame,
                                                              tlsPtr_.frame_id_to_shadow_frame,
                                                              num_vregs);
  for (uint32_t i = 0; i < num_vregs; i++) {
    // Do this to clear all references for root visitors.
    shadow_frame->SetVRegReference(i, nullptr);
    // This flag will be changed to true if the debugger modifies the value.
    record->GetUpdatedVRegFlags()[i] = false;
  }
  tlsPtr_.frame_id_to_shadow_frame = record;
  return shadow_frame;
}

TLSData* Thread::GetCustomTLS(const char* key) {
  MutexLock mu(Thread::Current(), *Locks::custom_tls_lock_);
  auto it = custom_tls_.find(key);
  return (it != custom_tls_.end()) ? it->second.get() : nullptr;
}

void Thread::SetCustomTLS(const char* key, TLSData* data) {
  // We will swap the old data (which might be nullptr) with this and then delete it outside of the
  // custom_tls_lock_.
  std::unique_ptr<TLSData> old_data(data);
  {
    MutexLock mu(Thread::Current(), *Locks::custom_tls_lock_);
    custom_tls_.GetOrCreate(key, []() { return std::unique_ptr<TLSData>(); }).swap(old_data);
  }
}

void Thread::RemoveDebuggerShadowFrameMapping(size_t frame_id) {
  FrameIdToShadowFrame* head = tlsPtr_.frame_id_to_shadow_frame;
  if (head->GetFrameId() == frame_id) {
    tlsPtr_.frame_id_to_shadow_frame = head->GetNext();
    FrameIdToShadowFrame::Delete(head);
    return;
  }
  FrameIdToShadowFrame* prev = head;
  for (FrameIdToShadowFrame* record = head->GetNext();
       record != nullptr;
       prev = record, record = record->GetNext()) {
    if (record->GetFrameId() == frame_id) {
      prev->SetNext(record->GetNext());
      FrameIdToShadowFrame::Delete(record);
      return;
    }
  }
  LOG(FATAL) << "No shadow frame for frame " << frame_id;
  UNREACHABLE();
}

void Thread::InitTid() {
  tls32_.tid = ::art::GetTid();
}

void Thread::InitAfterFork() {
  // One thread (us) survived the fork, but we have a new tid so we need to
  // update the value stashed in this Thread*.
  InitTid();
}

void Thread::DeleteJPeer(JNIEnv* env) {
  // Make sure nothing can observe both opeer and jpeer set at the same time.
  jobject old_jpeer = tlsPtr_.jpeer;
  CHECK(old_jpeer != nullptr);
  tlsPtr_.jpeer = nullptr;
  env->DeleteGlobalRef(old_jpeer);
}

void* Thread::CreateCallbackWithUffdGc(void* arg) {
  return Thread::CreateCallback(arg);
}

void* Thread::CreateCallback(void* arg) {
  Thread* self = reinterpret_cast<Thread*>(arg);
  Runtime* runtime = Runtime::Current();
  if (runtime == nullptr) {
    LOG(ERROR) << "Thread attaching to non-existent runtime: " << *self;
    return nullptr;
  }
  {
    // TODO: pass self to MutexLock - requires self to equal Thread::Current(), which is only true
    //       after self->Init().
    MutexLock mu(nullptr, *Locks::runtime_shutdown_lock_);
    // Check that if we got here we cannot be shutting down (as shutdown should never have started
    // while threads are being born).
    CHECK(!runtime->IsShuttingDownLocked());
    // Note: given that the JNIEnv is created in the parent thread, the only failure point here is
    //       a mess in InitStackHwm. We do not have a reasonable way to recover from that, so abort
    //       the runtime in such a case. In case this ever changes, we need to make sure here to
    //       delete the tmp_jni_env, as we own it at this point.
    CHECK(self->Init(runtime->GetThreadList(), runtime->GetJavaVM(), self->tlsPtr_.tmp_jni_env));
    self->tlsPtr_.tmp_jni_env = nullptr;
    Runtime::Current()->EndThreadBirth();
  }
  {
    ScopedObjectAccess soa(self);
    self->InitStringEntryPoints();

    // Copy peer into self, deleting global reference when done.
    CHECK(self->tlsPtr_.jpeer != nullptr);
    self->tlsPtr_.opeer = soa.Decode<mirror::Object>(self->tlsPtr_.jpeer).Ptr();
    // Make sure nothing can observe both opeer and jpeer set at the same time.
    self->DeleteJPeer(self->GetJniEnv());
    self->SetThreadName(self->GetThreadName()->ToModifiedUtf8().c_str());

    ArtField* priorityField = WellKnownClasses::java_lang_Thread_priority;
    self->SetNativePriority(priorityField->GetInt(self->tlsPtr_.opeer));

    runtime->GetRuntimeCallbacks()->ThreadStart(self);

    // Unpark ourselves if the java peer was unparked before it started (see
    // b/28845097#comment49 for more information)

    ArtField* unparkedField = WellKnownClasses::java_lang_Thread_unparkedBeforeStart;
    bool should_unpark = false;
    {
      // Hold the lock here, so that if another thread calls unpark before the thread starts
      // we don't observe the unparkedBeforeStart field before the unparker writes to it,
      // which could cause a lost unpark.
      art::MutexLock mu(soa.Self(), *art::Locks::thread_list_lock_);
      should_unpark = unparkedField->GetBoolean(self->tlsPtr_.opeer) == JNI_TRUE;
    }
    if (should_unpark) {
      self->Unpark();
    }
    // Invoke the 'run' method of our java.lang.Thread.
    ObjPtr<mirror::Object> receiver = self->tlsPtr_.opeer;
    WellKnownClasses::java_lang_Thread_run->InvokeVirtual<'V'>(self, receiver);
  }
  // Detach and delete self.
  Runtime::Current()->GetThreadList()->Unregister(self, /* should_run_callbacks= */ true);

  return nullptr;
}

Thread* Thread::FromManagedThread(Thread* self, ObjPtr<mirror::Object> thread_peer) {
  ArtField* f = WellKnownClasses::java_lang_Thread_nativePeer;
  Thread* result = reinterpret_cast64<Thread*>(f->GetLong(thread_peer));
  // Check that if we have a result it is either suspended or we hold the thread_list_lock_
  // to stop it from going away.
  if (kIsDebugBuild) {
    MutexLock mu(self, *Locks::thread_suspend_count_lock_);
    if (result != nullptr && !result->IsSuspended()) {
      Locks::thread_list_lock_->AssertHeld(self);
    }
  }
  return result;
}

Thread* Thread::FromManagedThread(const ScopedObjectAccessAlreadyRunnable& soa,
                                  jobject java_thread) {
  return FromManagedThread(soa.Self(), soa.Decode<mirror::Object>(java_thread));
}

static size_t FixStackSize(size_t stack_size) {
  // A stack size of zero means "use the default".
  if (stack_size == 0) {
    stack_size = Runtime::Current()->GetDefaultStackSize();
  }

  // Dalvik used the bionic pthread default stack size for native threads,
  // so include that here to support apps that expect large native stacks.
  stack_size += 1 * MB;

  // Under sanitization, frames of the interpreter may become bigger, both for C code as
  // well as the ShadowFrame. Ensure a larger minimum size. Otherwise initialization
  // of all core classes cannot be done in all test circumstances.
  if (kMemoryToolIsAvailable) {
    stack_size = std::max(2 * MB, stack_size);
  }

  // It's not possible to request a stack smaller than the system-defined PTHREAD_STACK_MIN.
  if (stack_size < PTHREAD_STACK_MIN) {
    stack_size = PTHREAD_STACK_MIN;
  }

  if (Runtime::Current()->GetImplicitStackOverflowChecks()) {
    // If we are going to use implicit stack checks, allocate space for the protected
    // region at the bottom of the stack.
    stack_size += Thread::kStackOverflowImplicitCheckSize +
        GetStackOverflowReservedBytes(kRuntimeISA);
  } else {
    // It's likely that callers are trying to ensure they have at least a certain amount of
    // stack space, so we should add our reserved space on top of what they requested, rather
    // than implicitly take it away from them.
    stack_size += GetStackOverflowReservedBytes(kRuntimeISA);
  }

  // Some systems require the stack size to be a multiple of the system page size, so round up.
  stack_size = RoundUp(stack_size, gPageSize);

  return stack_size;
}

// Return the nearest page-aligned address below the current stack top.
NO_INLINE
static uint8_t* FindStackTop() {
  return reinterpret_cast<uint8_t*>(
      AlignDown(__builtin_frame_address(0), gPageSize));
}

// Install a protected region in the stack.  This is used to trigger a SIGSEGV if a stack
// overflow is detected.  It is located right below the stack_begin_.
ATTRIBUTE_NO_SANITIZE_ADDRESS
void Thread::InstallImplicitProtection() {
  uint8_t* pregion = tlsPtr_.stack_begin - GetStackOverflowProtectedSize();
  // Page containing current top of stack.
  uint8_t* stack_top = FindStackTop();

  // Try to directly protect the stack.
  VLOG(threads) << "installing stack protected region at " << std::hex <<
        static_cast<void*>(pregion) << " to " <<
        static_cast<void*>(pregion + GetStackOverflowProtectedSize() - 1);
  if (ProtectStack(/* fatal_on_error= */ false)) {
    // Tell the kernel that we won't be needing these pages any more.
    // NB. madvise will probably write zeroes into the memory (on linux it does).
    size_t unwanted_size =
        reinterpret_cast<uintptr_t>(stack_top) - reinterpret_cast<uintptr_t>(pregion) - gPageSize;
    madvise(pregion, unwanted_size, MADV_DONTNEED);
    return;
  }

  // There is a little complexity here that deserves a special mention.  On some
  // architectures, the stack is created using a VM_GROWSDOWN flag
  // to prevent memory being allocated when it's not needed.  This flag makes the
  // kernel only allocate memory for the stack by growing down in memory.  Because we
  // want to put an mprotected region far away from that at the stack top, we need
  // to make sure the pages for the stack are mapped in before we call mprotect.
  //
  // The failed mprotect in UnprotectStack is an indication of a thread with VM_GROWSDOWN
  // with a non-mapped stack (usually only the main thread).
  //
  // We map in the stack by reading every page from the stack bottom (highest address)
  // to the stack top. (We then madvise this away.) This must be done by reading from the
  // current stack pointer downwards.
  //
  // Accesses too far below the current machine register corresponding to the stack pointer (e.g.,
  // ESP on x86[-32], SP on ARM) might cause a SIGSEGV (at least on x86 with newer kernels). We
  // thus have to move the stack pointer. We do this portably by using a recursive function with a
  // large stack frame size.

  // (Defensively) first remove the protection on the protected region as we'll want to read
  // and write it. Ignore errors.
  UnprotectStack();

  VLOG(threads) << "Need to map in stack for thread at " << std::hex <<
      static_cast<void*>(pregion);

  struct RecurseDownStack {
    // This function has an intentionally large stack size.
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wframe-larger-than="
    NO_INLINE
    __attribute__((no_sanitize("memtag"))) static void Touch(uintptr_t target) {
      volatile size_t zero = 0;
      // Use a large local volatile array to ensure a large frame size. Do not use anything close
      // to a full page for ASAN. It would be nice to ensure the frame size is at most a page, but
      // there is no pragma support for this.
      // Note: for ASAN we need to shrink the array a bit, as there's other overhead.
      constexpr size_t kAsanMultiplier =
#ifdef ADDRESS_SANITIZER
          2u;
#else
          1u;
#endif
      // Keep space uninitialized as it can overflow the stack otherwise (should Clang actually
      // auto-initialize this local variable).
      volatile char space[gPageSize - (kAsanMultiplier * 256)] __attribute__((uninitialized));
      [[maybe_unused]] char sink = space[zero];
      // Remove tag from the pointer. Nop in non-hwasan builds.
      uintptr_t addr = reinterpret_cast<uintptr_t>(
          __hwasan_tag_pointer != nullptr ? __hwasan_tag_pointer(space, 0) : space);
      if (addr >= target + gPageSize) {
        Touch(target);
      }
      zero *= 2;  // Try to avoid tail recursion.
    }
#pragma GCC diagnostic pop
  };
  RecurseDownStack::Touch(reinterpret_cast<uintptr_t>(pregion));

  VLOG(threads) << "(again) installing stack protected region at " << std::hex <<
      static_cast<void*>(pregion) << " to " <<
      static_cast<void*>(pregion + GetStackOverflowProtectedSize() - 1);

  // Protect the bottom of the stack to prevent read/write to it.
  ProtectStack(/* fatal_on_error= */ true);

  // Tell the kernel that we won't be needing these pages any more.
  // NB. madvise will probably write zeroes into the memory (on linux it does).
  size_t unwanted_size =
      reinterpret_cast<uintptr_t>(stack_top) - reinterpret_cast<uintptr_t>(pregion) - gPageSize;
  madvise(pregion, unwanted_size, MADV_DONTNEED);
}

template <bool kSupportTransaction>
static void SetNativePeer(ObjPtr<mirror::Object> java_peer, Thread* thread)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  ArtField* field = WellKnownClasses::java_lang_Thread_nativePeer;
  if (kSupportTransaction && Runtime::Current()->IsActiveTransaction()) {
    field->SetLong</*kTransactionActive=*/ true>(java_peer, reinterpret_cast<jlong>(thread));
  } else {
    field->SetLong</*kTransactionActive=*/ false>(java_peer, reinterpret_cast<jlong>(thread));
  }
}

static void SetNativePeer(JNIEnv* env, jobject java_peer, Thread* thread) {
  ScopedObjectAccess soa(env);
  SetNativePeer</*kSupportTransaction=*/ false>(soa.Decode<mirror::Object>(java_peer), thread);
}

void Thread::CreateNativeThread(JNIEnv* env, jobject java_peer, size_t stack_size, bool is_daemon) {
  CHECK(java_peer != nullptr);
  Thread* self = static_cast<JNIEnvExt*>(env)->GetSelf();

  if (VLOG_IS_ON(threads)) {
    ScopedObjectAccess soa(env);

    ArtField* f = WellKnownClasses::java_lang_Thread_name;
    ObjPtr<mirror::String> java_name =
        f->GetObject(soa.Decode<mirror::Object>(java_peer))->AsString();
    std::string thread_name;
    if (java_name != nullptr) {
      thread_name = java_name->ToModifiedUtf8();
    } else {
      thread_name = "(Unnamed)";
    }

    VLOG(threads) << "Creating native thread for " << thread_name;
    self->Dump(LOG_STREAM(INFO));
  }

  Runtime* runtime = Runtime::Current();

  // Atomically start the birth of the thread ensuring the runtime isn't shutting down.
  bool thread_start_during_shutdown = false;
  {
    MutexLock mu(self, *Locks::runtime_shutdown_lock_);
    if (runtime->IsShuttingDownLocked()) {
      thread_start_during_shutdown = true;
    } else {
      runtime->StartThreadBirth();
    }
  }
  if (thread_start_during_shutdown) {
    ScopedLocalRef<jclass> error_class(env, env->FindClass("java/lang/InternalError"));
    env->ThrowNew(error_class.get(), "Thread starting during runtime shutdown");
    return;
  }

  Thread* child_thread = new Thread(is_daemon);
  // Use global JNI ref to hold peer live while child thread starts.
  child_thread->tlsPtr_.jpeer = env->NewGlobalRef(java_peer);
  stack_size = FixStackSize(stack_size);

  // Thread.start is synchronized, so we know that nativePeer is 0, and know that we're not racing
  // to assign it.
  SetNativePeer(env, java_peer, child_thread);

  // Try to allocate a JNIEnvExt for the thread. We do this here as we might be out of memory and
  // do not have a good way to report this on the child's side.
  std::string error_msg;
  std::unique_ptr<JNIEnvExt> child_jni_env_ext(
      JNIEnvExt::Create(child_thread, Runtime::Current()->GetJavaVM(), &error_msg));

  int pthread_create_result = 0;
  if (child_jni_env_ext.get() != nullptr) {
    pthread_t new_pthread;
    pthread_attr_t attr;
    child_thread->tlsPtr_.tmp_jni_env = child_jni_env_ext.get();
    CHECK_PTHREAD_CALL(pthread_attr_init, (&attr), "new thread");
    CHECK_PTHREAD_CALL(pthread_attr_setdetachstate, (&attr, PTHREAD_CREATE_DETACHED),
                       "PTHREAD_CREATE_DETACHED");
    CHECK_PTHREAD_CALL(pthread_attr_setstacksize, (&attr, stack_size), stack_size);
    pthread_create_result = pthread_create(&new_pthread,
                                           &attr,
                                           gUseUserfaultfd ? Thread::CreateCallbackWithUffdGc
                                                           : Thread::CreateCallback,
                                           child_thread);
    CHECK_PTHREAD_CALL(pthread_attr_destroy, (&attr), "new thread");

    if (pthread_create_result == 0) {
      // pthread_create started the new thread. The child is now responsible for managing the
      // JNIEnvExt we created.
      // Note: we can't check for tmp_jni_env == nullptr, as that would require synchronization
      //       between the threads.
      child_jni_env_ext.release();  // NOLINT pthreads API.
      return;
    }
  }

  // Either JNIEnvExt::Create or pthread_create(3) failed, so clean up.
  {
    MutexLock mu(self, *Locks::runtime_shutdown_lock_);
    runtime->EndThreadBirth();
  }
  // Manually delete the global reference since Thread::Init will not have been run. Make sure
  // nothing can observe both opeer and jpeer set at the same time.
  child_thread->DeleteJPeer(env);
  delete child_thread;
  child_thread = nullptr;
  // TODO: remove from thread group?
  SetNativePeer(env, java_peer, nullptr);
  {
    std::string msg(child_jni_env_ext.get() == nullptr ?
        StringPrintf("Could not allocate JNI Env: %s", error_msg.c_str()) :
        StringPrintf("pthread_create (%s stack) failed: %s",
                                 PrettySize(stack_size).c_str(), strerror(pthread_create_result)));
    ScopedObjectAccess soa(env);
    soa.Self()->ThrowOutOfMemoryError(msg.c_str());
  }
}

bool Thread::Init(ThreadList* thread_list, JavaVMExt* java_vm, JNIEnvExt* jni_env_ext) {
  // This function does all the initialization that must be run by the native thread it applies to.
  // (When we create a new thread from managed code, we allocate the Thread* in Thread::Create so
  // we can handshake with the corresponding native thread when it's ready.) Check this native
  // thread hasn't been through here already...
  CHECK(Thread::Current() == nullptr);

  // Set pthread_self_ ahead of pthread_setspecific, that makes Thread::Current function, this
  // avoids pthread_self_ ever being invalid when discovered from Thread::Current().
  tlsPtr_.pthread_self = pthread_self();
  CHECK(is_started_);

  ScopedTrace trace("Thread::Init");

  SetUpAlternateSignalStack();
  if (!InitStackHwm()) {
    return false;
  }
  InitCpu();
  InitTlsEntryPoints();
  RemoveSuspendTrigger();
  InitCardTable();
  InitTid();

#ifdef __BIONIC__
  __get_tls()[TLS_SLOT_ART_THREAD_SELF] = this;
#else
  CHECK_PTHREAD_CALL(pthread_setspecific, (Thread::pthread_key_self_, this), "attach self");
  Thread::self_tls_ = this;
#endif
  DCHECK_EQ(Thread::Current(), this);

  tls32_.thin_lock_thread_id = thread_list->AllocThreadId(this);

  if (jni_env_ext != nullptr) {
    DCHECK_EQ(jni_env_ext->GetVm(), java_vm);
    DCHECK_EQ(jni_env_ext->GetSelf(), this);
    tlsPtr_.jni_env = jni_env_ext;
  } else {
    std::string error_msg;
    tlsPtr_.jni_env = JNIEnvExt::Create(this, java_vm, &error_msg);
    if (tlsPtr_.jni_env == nullptr) {
      LOG(ERROR) << "Failed to create JNIEnvExt: " << error_msg;
      return false;
    }
  }

  ScopedTrace trace3("ThreadList::Register");
  thread_list->Register(this);
  return true;
}

template <typename PeerAction>
Thread* Thread::Attach(const char* thread_name,
                       bool as_daemon,
                       PeerAction peer_action,
                       bool should_run_callbacks) {
  Runtime* runtime = Runtime::Current();
  ScopedTrace trace("Thread::Attach");
  if (runtime == nullptr) {
    LOG(ERROR) << "Thread attaching to non-existent runtime: " <<
        ((thread_name != nullptr) ? thread_name : "(Unnamed)");
    return nullptr;
  }
  Thread* self;
  {
    ScopedTrace trace2("Thread birth");
    MutexLock mu(nullptr, *Locks::runtime_shutdown_lock_);
    if (runtime->IsShuttingDownLocked()) {
      LOG(WARNING) << "Thread attaching while runtime is shutting down: " <<
          ((thread_name != nullptr) ? thread_name : "(Unnamed)");
      return nullptr;
    } else {
      Runtime::Current()->StartThreadBirth();
      self = new Thread(as_daemon);
      bool init_success = self->Init(runtime->GetThreadList(), runtime->GetJavaVM());
      Runtime::Current()->EndThreadBirth();
      if (!init_success) {
        delete self;
        return nullptr;
      }
    }
  }

  self->InitStringEntryPoints();

  CHECK_NE(self->GetState(), ThreadState::kRunnable);
  self->SetState(ThreadState::kNative);

  // Run the action that is acting on the peer.
  if (!peer_action(self)) {
    runtime->GetThreadList()->Unregister(self, should_run_callbacks);
    // Unregister deletes self, no need to do this here.
    return nullptr;
  }

  if (VLOG_IS_ON(threads)) {
    if (thread_name != nullptr) {
      VLOG(threads) << "Attaching thread " << thread_name;
    } else {
      VLOG(threads) << "Attaching unnamed thread.";
    }
    ScopedObjectAccess soa(self);
    self->Dump(LOG_STREAM(INFO));
  }

  if (should_run_callbacks) {
    ScopedObjectAccess soa(self);
    runtime->GetRuntimeCallbacks()->ThreadStart(self);
  }

  return self;
}

Thread* Thread::Attach(const char* thread_name,
                       bool as_daemon,
                       jobject thread_group,
                       bool create_peer,
                       bool should_run_callbacks) {
  auto create_peer_action = [&](Thread* self) {
    // If we're the main thread, ClassLinker won't be created until after we're attached,
    // so that thread needs a two-stage attach. Regular threads don't need this hack.
    // In the compiler, all threads need this hack, because no-one's going to be getting
    // a native peer!
    if (create_peer) {
      self->CreatePeer(thread_name, as_daemon, thread_group);
      if (self->IsExceptionPending()) {
        // We cannot keep the exception around, as we're deleting self. Try to be helpful and log
        // the failure but do not dump the exception details. If we fail to allocate the peer, we
        // usually also fail to allocate an exception object and throw a pre-allocated OOME without
        // any useful information. If we do manage to allocate the exception object, the memory
        // information in the message could have been collected too late and therefore misleading.
        {
          ScopedObjectAccess soa(self);
          LOG(ERROR) << "Exception creating thread peer: "
                     << ((thread_name != nullptr) ? thread_name : "<null>");
          self->ClearException();
        }
        return false;
      }
    } else {
      // These aren't necessary, but they improve diagnostics for unit tests & command-line tools.
      if (thread_name != nullptr) {
        self->SetCachedThreadName(thread_name);
        ::art::SetThreadName(thread_name);
      } else if (self->GetJniEnv()->IsCheckJniEnabled()) {
        LOG(WARNING) << *Thread::Current() << " attached without supplying a name";
      }
    }
    return true;
  };
  return Attach(thread_name, as_daemon, create_peer_action, should_run_callbacks);
}

Thread* Thread::Attach(const char* thread_name, bool as_daemon, jobject thread_peer) {
  auto set_peer_action = [&](Thread* self) {
    // Install the given peer.
    DCHECK(self == Thread::Current());
    ScopedObjectAccess soa(self);
    ObjPtr<mirror::Object> peer = soa.Decode<mirror::Object>(thread_peer);
    self->tlsPtr_.opeer = peer.Ptr();
    SetNativePeer</*kSupportTransaction=*/ false>(peer, self);
    return true;
  };
  return Attach(thread_name, as_daemon, set_peer_action, /* should_run_callbacks= */ true);
}

void Thread::CreatePeer(const char* name, bool as_daemon, jobject thread_group) {
  Runtime* runtime = Runtime::Current();
  CHECK(runtime->IsStarted());
  Thread* self = this;
  DCHECK_EQ(self, Thread::Current());

  ScopedObjectAccess soa(self);
  StackHandleScope<4u> hs(self);
  DCHECK(WellKnownClasses::java_lang_ThreadGroup->IsInitialized());
  Handle<mirror::Object> thr_group = hs.NewHandle(soa.Decode<mirror::Object>(
      thread_group != nullptr ? thread_group : runtime->GetMainThreadGroup()));
  Handle<mirror::String> thread_name = hs.NewHandle(
      name != nullptr ? mirror::String::AllocFromModifiedUtf8(self, name) : nullptr);
  // Add missing null check in case of OOM b/18297817
  if (name != nullptr && UNLIKELY(thread_name == nullptr)) {
    CHECK(self->IsExceptionPending());
    return;
  }
  jint thread_priority = GetNativePriority();

  DCHECK(WellKnownClasses::java_lang_Thread->IsInitialized());
  Handle<mirror::Object> peer =
      hs.NewHandle(WellKnownClasses::java_lang_Thread->AllocObject(self));
  if (UNLIKELY(peer == nullptr)) {
    CHECK(IsExceptionPending());
    return;
  }
  tlsPtr_.opeer = peer.Get();
  WellKnownClasses::java_lang_Thread_init->InvokeInstance<'V', 'L', 'L', 'I', 'Z'>(
      self, peer.Get(), thr_group.Get(), thread_name.Get(), thread_priority, as_daemon);
  if (self->IsExceptionPending()) {
    return;
  }

  SetNativePeer</*kSupportTransaction=*/ false>(peer.Get(), self);

  MutableHandle<mirror::String> peer_thread_name(hs.NewHandle(GetThreadName()));
  if (peer_thread_name == nullptr) {
    // The Thread constructor should have set the Thread.name to a
    // non-null value. However, because we can run without code
    // available (in the compiler, in tests), we manually assign the
    // fields the constructor should have set.
    if (runtime->IsActiveTransaction()) {
      InitPeer<true>(tlsPtr_.opeer,
                     as_daemon,
                     thr_group.Get(),
                     thread_name.Get(),
                     thread_priority);
    } else {
      InitPeer<false>(tlsPtr_.opeer,
                      as_daemon,
                      thr_group.Get(),
                      thread_name.Get(),
                      thread_priority);
    }
    peer_thread_name.Assign(GetThreadName());
  }
  // 'thread_name' may have been null, so don't trust 'peer_thread_name' to be non-null.
  if (peer_thread_name != nullptr) {
    SetThreadName(peer_thread_name->ToModifiedUtf8().c_str());
  }
}

ObjPtr<mirror::Object> Thread::CreateCompileTimePeer(const char* name,
                                                     bool as_daemon,
                                                     jobject thread_group) {
  Runtime* runtime = Runtime::Current();
  CHECK(!runtime->IsStarted());
  Thread* self = this;
  DCHECK_EQ(self, Thread::Current());

  ScopedObjectAccessUnchecked soa(self);
  StackHandleScope<3u> hs(self);
  DCHECK(WellKnownClasses::java_lang_ThreadGroup->IsInitialized());
  Handle<mirror::Object> thr_group = hs.NewHandle(soa.Decode<mirror::Object>(
      thread_group != nullptr ? thread_group : runtime->GetMainThreadGroup()));
  Handle<mirror::String> thread_name = hs.NewHandle(
      name != nullptr ? mirror::String::AllocFromModifiedUtf8(self, name) : nullptr);
  // Add missing null check in case of OOM b/18297817
  if (name != nullptr && UNLIKELY(thread_name == nullptr)) {
    CHECK(self->IsExceptionPending());
    return nullptr;
  }
  jint thread_priority = kNormThreadPriority;  // Always normalize to NORM priority.

  DCHECK(WellKnownClasses::java_lang_Thread->IsInitialized());
  Handle<mirror::Object> peer = hs.NewHandle(
      WellKnownClasses::java_lang_Thread->AllocObject(self));
  if (peer == nullptr) {
    CHECK(Thread::Current()->IsExceptionPending());
    return nullptr;
  }

  // We cannot call Thread.init, as it will recursively ask for currentThread.

  // The Thread constructor should have set the Thread.name to a
  // non-null value. However, because we can run without code
  // available (in the compiler, in tests), we manually assign the
  // fields the constructor should have set.
  if (runtime->IsActiveTransaction()) {
    InitPeer<true>(peer.Get(),
                   as_daemon,
                   thr_group.Get(),
                   thread_name.Get(),
                   thread_priority);
  } else {
    InitPeer<false>(peer.Get(),
                    as_daemon,
                    thr_group.Get(),
                    thread_name.Get(),
                    thread_priority);
  }

  return peer.Get();
}

template<bool kTransactionActive>
void Thread::InitPeer(ObjPtr<mirror::Object> peer,
                      bool as_daemon,
                      ObjPtr<mirror::Object> thread_group,
                      ObjPtr<mirror::String> thread_name,
                      jint thread_priority) {
  WellKnownClasses::java_lang_Thread_daemon->SetBoolean<kTransactionActive>(peer,
      static_cast<uint8_t>(as_daemon ? 1u : 0u));
  WellKnownClasses::java_lang_Thread_group->SetObject<kTransactionActive>(peer, thread_group);
  WellKnownClasses::java_lang_Thread_name->SetObject<kTransactionActive>(peer, thread_name);
  WellKnownClasses::java_lang_Thread_priority->SetInt<kTransactionActive>(peer, thread_priority);
}

void Thread::SetCachedThreadName(const char* name) {
  DCHECK(name != kThreadNameDuringStartup);
  const char* old_name = tlsPtr_.name.exchange(name == nullptr ? nullptr : strdup(name));
  if (old_name != nullptr && old_name !=  kThreadNameDuringStartup) {
    // Deallocate it, carefully. Note that the load has to be ordered wrt the store of the xchg.
    for (uint32_t i = 0; UNLIKELY(tls32_.num_name_readers.load(std::memory_order_seq_cst) != 0);
         ++i) {
      static constexpr uint32_t kNumSpins = 1000;
      // Ugly, but keeps us from having to do anything on the reader side.
      if (i > kNumSpins) {
        usleep(500);
      }
    }
    // We saw the reader count drop to zero since we replaced the name; old one is now safe to
    // deallocate.
    free(const_cast<char *>(old_name));
  }
}

void Thread::SetThreadName(const char* name) {
  SetCachedThreadName(name);
  ::art::SetThreadName(name);
  Dbg::DdmSendThreadNotification(this, CHUNK_TYPE("THNM"));
}

static void GetThreadStack(pthread_t thread,
                           void** stack_base,
                           size_t* stack_size,
                           size_t* guard_size) {
#if defined(__APPLE__)
  *stack_size = pthread_get_stacksize_np(thread);
  void* stack_addr = pthread_get_stackaddr_np(thread);

  // Check whether stack_addr is the base or end of the stack.
  // (On Mac OS 10.7, it's the end.)
  int stack_variable;
  if (stack_addr > &stack_variable) {
    *stack_base = reinterpret_cast<uint8_t*>(stack_addr) - *stack_size;
  } else {
    *stack_base = stack_addr;
  }

  // This is wrong, but there doesn't seem to be a way to get the actual value on the Mac.
  pthread_attr_t attributes;
  CHECK_PTHREAD_CALL(pthread_attr_init, (&attributes), __FUNCTION__);
  CHECK_PTHREAD_CALL(pthread_attr_getguardsize, (&attributes, guard_size), __FUNCTION__);
  CHECK_PTHREAD_CALL(pthread_attr_destroy, (&attributes), __FUNCTION__);
#else
  pthread_attr_t attributes;
  CHECK_PTHREAD_CALL(pthread_getattr_np, (thread, &attributes), __FUNCTION__);
  CHECK_PTHREAD_CALL(pthread_attr_getstack, (&attributes, stack_base, stack_size), __FUNCTION__);
  CHECK_PTHREAD_CALL(pthread_attr_getguardsize, (&attributes, guard_size), __FUNCTION__);
  CHECK_PTHREAD_CALL(pthread_attr_destroy, (&attributes), __FUNCTION__);

#if defined(__GLIBC__)
  // If we're the main thread, check whether we were run with an unlimited stack. In that case,
  // glibc will have reported a 2GB stack for our 32-bit process, and our stack overflow detection
  // will be broken because we'll die long before we get close to 2GB.
  bool is_main_thread = (::art::GetTid() == static_cast<uint32_t>(getpid()));
  if (is_main_thread) {
    rlimit stack_limit;
    if (getrlimit(RLIMIT_STACK, &stack_limit) == -1) {
      PLOG(FATAL) << "getrlimit(RLIMIT_STACK) failed";
    }
    if (stack_limit.rlim_cur == RLIM_INFINITY) {
      size_t old_stack_size = *stack_size;

      // Use the kernel default limit as our size, and adjust the base to match.
      *stack_size = 8 * MB;
      *stack_base = reinterpret_cast<uint8_t*>(*stack_base) + (old_stack_size - *stack_size);

      VLOG(threads) << "Limiting unlimited stack (reported as " << PrettySize(old_stack_size) << ")"
                    << " to " << PrettySize(*stack_size)
                    << " with base " << *stack_base;
    }
  }
#endif

#endif
}

bool Thread::InitStackHwm() {
  ScopedTrace trace("InitStackHwm");
  void* read_stack_base;
  size_t read_stack_size;
  size_t read_guard_size;
  GetThreadStack(tlsPtr_.pthread_self, &read_stack_base, &read_stack_size, &read_guard_size);

  tlsPtr_.stack_begin = reinterpret_cast<uint8_t*>(read_stack_base);
  tlsPtr_.stack_size = read_stack_size;

  // The minimum stack size we can cope with is the protected region size + stack overflow check
  // region size + some memory for normal stack usage.
  //
  // The protected region is located at the beginning (lowest address) of the stack region.
  // Therefore, it starts at a page-aligned address. Its size should be a multiple of page sizes.
  // Typically, it is one page in size, however this varies in some configurations.
  //
  // The overflow reserved bytes is size of the stack overflow check region, located right after
  // the protected region, so also starts at a page-aligned address. The size is discretionary.
  // Typically it is 8K, but this varies in some configurations.
  //
  // The rest of the stack memory is available for normal stack usage. It is located right after
  // the stack overflow check region, so its starting address isn't necessarily page-aligned. The
  // size of the region is discretionary, however should be chosen in a way that the overall stack
  // size is a multiple of page sizes. Historically, it is chosen to be at least 4 KB.
  //
  // On systems with 4K page size, typically the minimum stack size will be 4+8+4 = 16K.
  // The thread won't be able to do much with this stack: even the GC takes between 8K and 12K.
  DCHECK_ALIGNED_PARAM(static_cast<size_t>(GetStackOverflowProtectedSize()),
                       static_cast<int32_t>(gPageSize));
  size_t min_stack = GetStackOverflowProtectedSize() +
      RoundUp(GetStackOverflowReservedBytes(kRuntimeISA) + 4 * KB, gPageSize);
  if (read_stack_size <= min_stack) {
    // Note, as we know the stack is small, avoid operations that could use a lot of stack.
    LogHelper::LogLineLowStack(__PRETTY_FUNCTION__,
                               __LINE__,
                               ::android::base::ERROR,
                               "Attempt to attach a thread with a too-small stack");
    return false;
  }

  // This is included in the SIGQUIT output, but it's useful here for thread debugging.
  VLOG(threads) << StringPrintf("Native stack is at %p (%s with %s guard)",
                                read_stack_base,
                                PrettySize(read_stack_size).c_str(),
                                PrettySize(read_guard_size).c_str());

  // Set stack_end_ to the bottom of the stack saving space of stack overflows

  Runtime* runtime = Runtime::Current();
  bool implicit_stack_check =
      runtime->GetImplicitStackOverflowChecks() && !runtime->IsAotCompiler();

  ResetDefaultStackEnd();

  // Install the protected region if we are doing implicit overflow checks.
  if (implicit_stack_check) {
    // The thread might have protected region at the bottom.  We need
    // to install our own region so we need to move the limits
    // of the stack to make room for it.

    tlsPtr_.stack_begin += read_guard_size + GetStackOverflowProtectedSize();
    tlsPtr_.stack_end += read_guard_size + GetStackOverflowProtectedSize();
    tlsPtr_.stack_size -= read_guard_size + GetStackOverflowProtectedSize();

    InstallImplicitProtection();
  }

  // Consistency check.
  CHECK_GT(FindStackTop(), reinterpret_cast<void*>(tlsPtr_.stack_end));

  return true;
}

void Thread::ShortDump(std::ostream& os) const {
  os << "Thread[";
  if (GetThreadId() != 0) {
    // If we're in kStarting, we won't have a thin lock id or tid yet.
    os << GetThreadId()
       << ",tid=" << GetTid() << ',';
  }
  tls32_.num_name_readers.fetch_add(1, std::memory_order_seq_cst);
  const char* name = tlsPtr_.name.load();
  os << GetState()
     << ",Thread*=" << this
     << ",peer=" << tlsPtr_.opeer
     << ",\"" << (name == nullptr ? "null" : name) << "\""
     << "]";
  tls32_.num_name_readers.fetch_sub(1 /* at least memory_order_release */);
}

Thread::DumpOrder Thread::Dump(std::ostream& os,
                               bool dump_native_stack,
                               bool force_dump_stack) const {
  DumpState(os);
  return DumpStack(os, dump_native_stack, force_dump_stack);
}

Thread::DumpOrder Thread::Dump(std::ostream& os,
                               unwindstack::AndroidLocalUnwinder& unwinder,
                               bool dump_native_stack,
                               bool force_dump_stack) const {
  DumpState(os);
  return DumpStack(os, unwinder, dump_native_stack, force_dump_stack);
}

ObjPtr<mirror::String> Thread::GetThreadName() const {
  if (tlsPtr_.opeer == nullptr) {
    return nullptr;
  }
  ObjPtr<mirror::Object> name = WellKnownClasses::java_lang_Thread_name->GetObject(tlsPtr_.opeer);
  return name == nullptr ? nullptr : name->AsString();
}

void Thread::GetThreadName(std::string& name) const {
  tls32_.num_name_readers.fetch_add(1, std::memory_order_seq_cst);
  // The store part of the increment has to be ordered with respect to the following load.
  const char* c_name = tlsPtr_.name.load(std::memory_order_seq_cst);
  name.assign(c_name == nullptr ? "<no name>" : c_name);
  tls32_.num_name_readers.fetch_sub(1 /* at least memory_order_release */);
}

uint64_t Thread::GetCpuMicroTime() const {
#if defined(__linux__)
  clockid_t cpu_clock_id;
  pthread_getcpuclockid(tlsPtr_.pthread_self, &cpu_clock_id);
  timespec now;
  clock_gettime(cpu_clock_id, &now);
  return static_cast<uint64_t>(now.tv_sec) * UINT64_C(1000000) +
         static_cast<uint64_t>(now.tv_nsec) / UINT64_C(1000);
#else  // __APPLE__
  UNIMPLEMENTED(WARNING);
  return -1;
#endif
}

// Attempt to rectify locks so that we dump thread list with required locks before exiting.
void Thread::UnsafeLogFatalForSuspendCount(Thread* self, Thread* thread) NO_THREAD_SAFETY_ANALYSIS {
  LOG(ERROR) << *thread << " suspend count already zero.";
  Locks::thread_suspend_count_lock_->Unlock(self);
  if (!Locks::mutator_lock_->IsSharedHeld(self)) {
    Locks::mutator_lock_->SharedTryLock(self);
    if (!Locks::mutator_lock_->IsSharedHeld(self)) {
      LOG(WARNING) << "Dumping thread list without holding mutator_lock_";
    }
  }
  if (!Locks::thread_list_lock_->IsExclusiveHeld(self)) {
    Locks::thread_list_lock_->TryLock(self);
    if (!Locks::thread_list_lock_->IsExclusiveHeld(self)) {
      LOG(WARNING) << "Dumping thread list without holding thread_list_lock_";
    }
  }
  std::ostringstream ss;
  Runtime::Current()->GetThreadList()->Dump(ss);
  LOG(FATAL) << ss.str();
  UNREACHABLE();
}

bool Thread::PassActiveSuspendBarriers() {
  DCHECK_EQ(this, Thread::Current());
  DCHECK_NE(GetState(), ThreadState::kRunnable);
  // Grab the suspend_count lock and copy the current set of barriers. Then clear the list and the
  // flag. The IncrementSuspendCount function requires the lock so we prevent a race between setting
  // the kActiveSuspendBarrier flag and clearing it.
  // TODO: Consider doing this without the temporary vector. That code will be a bit
  // tricky, since the WrappedSuspend1Barrier may disappear once the barrier is decremented.
  std::vector<AtomicInteger*> pass_barriers{};
  {
    MutexLock mu(this, *Locks::thread_suspend_count_lock_);
    if (!ReadFlag(ThreadFlag::kActiveSuspendBarrier)) {
      // Quick exit test: The barriers have already been claimed - this is possible as there may
      // be a race to claim and it doesn't matter who wins.  All of the callers of this function
      // (except SuspendAllInternal) will first test the kActiveSuspendBarrier flag without the
      // lock. Here we double-check whether the barrier has been passed with the
      // suspend_count_lock_.
      return false;
    }
    if (tlsPtr_.active_suspendall_barrier != nullptr) {
      // We have at most one active active_suspendall_barrier. See thread.h comment.
      pass_barriers.push_back(tlsPtr_.active_suspendall_barrier);
      tlsPtr_.active_suspendall_barrier = nullptr;
    }
    for (WrappedSuspend1Barrier* w = tlsPtr_.active_suspend1_barriers; w != nullptr; w = w->next_) {
      CHECK_EQ(w->magic_, WrappedSuspend1Barrier::kMagic)
          << "first = " << tlsPtr_.active_suspend1_barriers << " current = " << w
          << " next = " << w->next_;
      pass_barriers.push_back(&(w->barrier_));
    }
    tlsPtr_.active_suspend1_barriers = nullptr;
    AtomicClearFlag(ThreadFlag::kActiveSuspendBarrier);
    CHECK_GT(pass_barriers.size(), 0U);  // Since kActiveSuspendBarrier was set.
    // Decrement suspend barrier(s) while we still hold the lock, since SuspendThread may
    // remove and deallocate suspend barriers while holding suspend_count_lock_ .
    // There will typically only be a single barrier to pass here.
    for (AtomicInteger*& barrier : pass_barriers) {
      int32_t old_val = barrier->fetch_sub(1, std::memory_order_release);
      CHECK_GT(old_val, 0) << "Unexpected value for PassActiveSuspendBarriers(): " << old_val;
      if (old_val != 1) {
        // We're done with it.
        barrier = nullptr;
      }
    }
  }
  // Finally do futex_wakes after releasing the lock.
  for (AtomicInteger* barrier : pass_barriers) {
#if ART_USE_FUTEXES
    if (barrier != nullptr) {
      futex(barrier->Address(), FUTEX_WAKE_PRIVATE, INT_MAX, nullptr, nullptr, 0);
    }
#endif
  }
  return true;
}

void Thread::RunCheckpointFunction() {
  DCHECK_EQ(Thread::Current(), this);
  CHECK(!GetStateAndFlags(std::memory_order_relaxed).IsAnyOfFlagsSet(FlipFunctionFlags()));
  // Grab the suspend_count lock, get the next checkpoint and update all the checkpoint fields. If
  // there are no more checkpoints we will also clear the kCheckpointRequest flag.
  Closure* checkpoint;
  {
    MutexLock mu(this, *Locks::thread_suspend_count_lock_);
    checkpoint = tlsPtr_.checkpoint_function;
    if (!checkpoint_overflow_.empty()) {
      // Overflow list not empty, copy the first one out and continue.
      tlsPtr_.checkpoint_function = checkpoint_overflow_.front();
      checkpoint_overflow_.pop_front();
    } else {
      // No overflow checkpoints. Clear the kCheckpointRequest flag
      tlsPtr_.checkpoint_function = nullptr;
      AtomicClearFlag(ThreadFlag::kCheckpointRequest);
    }
  }
  // Outside the lock, run the checkpoint function.
  ScopedTrace trace("Run checkpoint function");
  CHECK(checkpoint != nullptr) << "Checkpoint flag set without pending checkpoint";
  checkpoint->Run(this);
}

void Thread::RunEmptyCheckpoint() {
  // Note: Empty checkpoint does not access the thread's stack,
  // so we do not need to check for the flip function.
  DCHECK_EQ(Thread::Current(), this);
  AtomicClearFlag(ThreadFlag::kEmptyCheckpointRequest);
  Runtime::Current()->GetThreadList()->EmptyCheckpointBarrier()->Pass(this);
}

bool Thread::RequestCheckpoint(Closure* function) {
  bool success;
  do {
    StateAndFlags old_state_and_flags = GetStateAndFlags(std::memory_order_relaxed);
    if (old_state_and_flags.GetState() != ThreadState::kRunnable) {
      return false;  // Fail, thread is suspended and so can't run a checkpoint.
    }
    StateAndFlags new_state_and_flags = old_state_and_flags;
    new_state_and_flags.SetFlag(ThreadFlag::kCheckpointRequest);
    success = tls32_.state_and_flags.CompareAndSetWeakSequentiallyConsistent(
        old_state_and_flags.GetValue(), new_state_and_flags.GetValue());
  } while (!success);
  // Succeeded setting checkpoint flag, now insert the actual checkpoint.
  if (tlsPtr_.checkpoint_function == nullptr) {
    tlsPtr_.checkpoint_function = function;
  } else {
    checkpoint_overflow_.push_back(function);
  }
  DCHECK(ReadFlag(ThreadFlag::kCheckpointRequest));
  TriggerSuspend();
  return true;
}

bool Thread::RequestEmptyCheckpoint() {
  StateAndFlags old_state_and_flags = GetStateAndFlags(std::memory_order_relaxed);
  if (old_state_and_flags.GetState() != ThreadState::kRunnable) {
    // If it's not runnable, we don't need to do anything because it won't be in the middle of a
    // heap access (eg. the read barrier).
    return false;
  }

  // We must be runnable to request a checkpoint.
  DCHECK_EQ(old_state_and_flags.GetState(), ThreadState::kRunnable);
  StateAndFlags new_state_and_flags = old_state_and_flags;
  new_state_and_flags.SetFlag(ThreadFlag::kEmptyCheckpointRequest);
  bool success = tls32_.state_and_flags.CompareAndSetStrongSequentiallyConsistent(
      old_state_and_flags.GetValue(), new_state_and_flags.GetValue());
  if (success) {
    TriggerSuspend();
  }
  return success;
}

class BarrierClosure : public Closure {
 public:
  explicit BarrierClosure(Closure* wrapped) : wrapped_(wrapped), barrier_(0) {}

  void Run(Thread* self) override {
    wrapped_->Run(self);
    barrier_.Pass(self);
  }

  void Wait(Thread* self, ThreadState wait_state) {
    if (wait_state != ThreadState::kRunnable) {
      barrier_.Increment<Barrier::kDisallowHoldingLocks>(self, 1);
    } else {
      barrier_.Increment<Barrier::kAllowHoldingLocks>(self, 1);
    }
  }

 private:
  Closure* wrapped_;
  Barrier barrier_;
};

// RequestSynchronousCheckpoint releases the thread_list_lock_ as a part of its execution.
bool Thread::RequestSynchronousCheckpoint(Closure* function, ThreadState wait_state) {
  Thread* self = Thread::Current();
  if (this == self) {
    Locks::thread_list_lock_->AssertExclusiveHeld(self);
    // Unlock the tll before running so that the state is the same regardless of thread.
    Locks::thread_list_lock_->ExclusiveUnlock(self);
    // Asked to run on this thread. Just run.
    function->Run(this);
    return true;
  }

  // The current thread is not this thread.

  VerifyState();

  Locks::thread_list_lock_->AssertExclusiveHeld(self);
  // If target "this" thread is runnable, try to schedule a checkpoint. Do some gymnastics to not
  // hold the suspend-count lock for too long.
  if (GetState() == ThreadState::kRunnable) {
    BarrierClosure barrier_closure(function);
    bool installed = false;
    {
      MutexLock mu(self, *Locks::thread_suspend_count_lock_);
      installed = RequestCheckpoint(&barrier_closure);
    }
    if (installed) {
      // Relinquish the thread-list lock. We should not wait holding any locks. We cannot
      // reacquire it since we don't know if 'this' hasn't been deleted yet.
      Locks::thread_list_lock_->ExclusiveUnlock(self);
      ScopedThreadStateChange sts(self, wait_state);
      // Wait state can be kRunnable, in which case, for lock ordering purposes, it's as if we ran
      // the closure ourselves. This means that the target thread should not acquire a pre-mutator
      // lock without running the checkpoint, and the closure should not acquire a pre-mutator
      // lock or suspend.
      barrier_closure.Wait(self, wait_state);
      return true;
    }
    // No longer runnable. Fall-through.
  }

  // Target "this" thread was not runnable. Suspend it, hopefully redundantly,
  // but it might have become runnable in the meantime.
  // Although this is a thread suspension, the target thread only blocks while we run the
  // checkpoint, which is presumed to terminate quickly even if other threads are blocked.
  // Note: IncrementSuspendCount also expects the thread_list_lock to be held unless this == self.
  WrappedSuspend1Barrier wrapped_barrier{};
  {
    bool is_suspended = false;

    {
      MutexLock suspend_count_mu(self, *Locks::thread_suspend_count_lock_);
      // If wait_state is kRunnable, function may not suspend. We thus never block because
      // we ourselves are being asked to suspend.
      if (UNLIKELY(wait_state != ThreadState::kRunnable && self->GetSuspendCount() != 0)) {
        // We are being asked to suspend while we are suspending another thread that may be
        // responsible for our suspension. This is likely to result in deadlock if we each
        // block on the suspension request. Instead we wait for the situation to change.
        ThreadExitFlag target_status;
        NotifyOnThreadExit(&target_status);
        for (int iter_count = 1; self->GetSuspendCount() != 0; ++iter_count) {
          Locks::thread_suspend_count_lock_->ExclusiveUnlock(self);
          Locks::thread_list_lock_->ExclusiveUnlock(self);
          {
            ScopedThreadStateChange sts(self, wait_state);
            usleep(ThreadList::kThreadSuspendSleepUs);
          }
          CHECK_LT(iter_count, ThreadList::kMaxSuspendRetries);
          Locks::thread_list_lock_->ExclusiveLock(self);
          if (target_status.HasExited()) {
            Locks::thread_list_lock_->ExclusiveUnlock(self);
            DCheckUnregisteredEverywhere(&target_status, &target_status);
            return false;
          }
          Locks::thread_suspend_count_lock_->ExclusiveLock(self);
        }
        UnregisterThreadExitFlag(&target_status);
      }
      IncrementSuspendCount(self, nullptr, &wrapped_barrier, SuspendReason::kInternal);
      VerifyState();
      DCHECK_GT(GetSuspendCount(), 0);
      if (wait_state != ThreadState::kRunnable) {
        DCHECK_EQ(self->GetSuspendCount(), 0);
      }
      // Since we've incremented the suspend count, "this" thread can no longer disappear.
      Locks::thread_list_lock_->ExclusiveUnlock(self);
      if (IsSuspended()) {
        // See the discussion in mutator_gc_coord.md and SuspendAllInternal for the race here.
        RemoveFirstSuspend1Barrier(&wrapped_barrier);
        if (!HasActiveSuspendBarrier()) {
          AtomicClearFlag(ThreadFlag::kActiveSuspendBarrier);
        }
        is_suspended = true;
      }
    }
    if (!is_suspended) {
      // This waits while holding the mutator lock. Effectively `self` becomes
      // impossible to suspend until `this` responds to the suspend request.
      // Arguably that's not making anything qualitatively worse.
      bool success = !Runtime::Current()
                          ->GetThreadList()
                          ->WaitForSuspendBarrier(&wrapped_barrier.barrier_)
                          .has_value();
      CHECK(success);
    }

    // Ensure that the flip function for this thread, if pending, is finished *before*
    // the checkpoint function is run. Otherwise, we may end up with both `to' and 'from'
    // space references on the stack, confusing the GC's thread-flip logic. The caller is
    // runnable so can't have a pending flip function.
    DCHECK_EQ(self->GetState(), ThreadState::kRunnable);
    DCHECK(IsSuspended());
    DCHECK(!self->GetStateAndFlags(std::memory_order_relaxed).IsAnyOfFlagsSet(FlipFunctionFlags()));
    EnsureFlipFunctionStarted(self, this);
    // Since we're runnable, and kPendingFlipFunction is set with all threads suspended, it
    // cannot be set again here. Thus kRunningFlipFunction is either already set after the
    // EnsureFlipFunctionStarted call, or will not be set before we call Run().
    if (ReadFlag(ThreadFlag::kRunningFlipFunction)) {
      WaitForFlipFunction(self);
    }
    function->Run(this);
  }

  {
    MutexLock mu2(self, *Locks::thread_suspend_count_lock_);
    DCHECK_NE(GetState(), ThreadState::kRunnable);
    DCHECK_GT(GetSuspendCount(), 0);
    DecrementSuspendCount(self);
    if (kIsDebugBuild) {
      CheckBarrierInactive(&wrapped_barrier);
    }
    resume_cond_->Broadcast(self);
  }

  Locks::thread_list_lock_->AssertNotHeld(self);
  return true;
}

void Thread::SetFlipFunction(Closure* function) {
  // This is called with all threads suspended, except for the calling thread.
  DCHECK(IsSuspended() || Thread::Current() == this);
  DCHECK(function != nullptr);
  DCHECK(GetFlipFunction() == nullptr);
  tlsPtr_.flip_function.store(function, std::memory_order_relaxed);
  DCHECK(!GetStateAndFlags(std::memory_order_relaxed).IsAnyOfFlagsSet(FlipFunctionFlags()));
  AtomicSetFlag(ThreadFlag::kPendingFlipFunction, std::memory_order_release);
}

bool Thread::EnsureFlipFunctionStarted(Thread* self,
                                       Thread* target,
                                       StateAndFlags old_state_and_flags,
                                       ThreadExitFlag* tef,
                                       bool* finished) {
  //  Note: If tef is non-null, *target may have been destroyed. We have to be careful about
  //  accessing it. That is the reason this is static and not a member function.
  DCHECK(self == Current());
  bool check_exited = (tef != nullptr);
  // Check that the thread can't unexpectedly exit while we are running.
  DCHECK(self == target || check_exited || target->ReadFlag(ThreadFlag::kSuspendRequest) ||
         Locks::thread_list_lock_->IsExclusiveHeld(self))
      << *target;
  bool become_runnable;
  auto maybe_release = [=]() NO_THREAD_SAFETY_ANALYSIS /* conditionally unlocks */ {
    if (check_exited) {
      Locks::thread_list_lock_->Unlock(self);
    }
  };
  auto set_finished = [=](bool value) {
    if (finished != nullptr) {
      *finished = value;
    }
  };

  if (check_exited) {
    Locks::thread_list_lock_->Lock(self);
    if (tef->HasExited()) {
      Locks::thread_list_lock_->Unlock(self);
      set_finished(true);
      return false;
    }
  }
  target->VerifyState();
  if (old_state_and_flags.GetValue() == 0) {
    become_runnable = false;
    old_state_and_flags = target->GetStateAndFlags(std::memory_order_relaxed);
  } else {
    become_runnable = true;
    DCHECK(!check_exited);
    DCHECK(target == self);
    DCHECK(old_state_and_flags.IsFlagSet(ThreadFlag::kPendingFlipFunction));
    DCHECK(!old_state_and_flags.IsFlagSet(ThreadFlag::kSuspendRequest));
  }
  while (true) {
    DCHECK(!check_exited || (Locks::thread_list_lock_->IsExclusiveHeld(self) && !tef->HasExited()));
    if (!old_state_and_flags.IsFlagSet(ThreadFlag::kPendingFlipFunction)) {
      maybe_release();
      set_finished(!old_state_and_flags.IsFlagSet(ThreadFlag::kRunningFlipFunction));
      return false;
    }
    DCHECK(!old_state_and_flags.IsFlagSet(ThreadFlag::kRunningFlipFunction));
    StateAndFlags new_state_and_flags =
        old_state_and_flags.WithFlag(ThreadFlag::kRunningFlipFunction)
                           .WithoutFlag(ThreadFlag::kPendingFlipFunction);
    if (become_runnable) {
      DCHECK_EQ(self, target);
      DCHECK_NE(self->GetState(), ThreadState::kRunnable);
      new_state_and_flags = new_state_and_flags.WithState(ThreadState::kRunnable);
    }
    if (target->tls32_.state_and_flags.CompareAndSetWeakAcquire(old_state_and_flags.GetValue(),
                                                                new_state_and_flags.GetValue())) {
      if (become_runnable) {
        self->GetMutatorLock()->TransitionFromSuspendedToRunnable(self);
      }
      art::Locks::mutator_lock_->AssertSharedHeld(self);
      maybe_release();
      // Thread will not go away while kRunningFlipFunction is set.
      target->RunFlipFunction(self);
      // At this point, no flip function flags should be set. It's unsafe to DCHECK that, since
      // the thread may now have exited.
      set_finished(true);
      return become_runnable;
    }
    if (become_runnable) {
      DCHECK(!check_exited);  // We didn't acquire thread_list_lock_ .
      // Let caller retry.
      return false;
    }
    old_state_and_flags = target->GetStateAndFlags(std::memory_order_acquire);
  }
  // Unreachable.
}

void Thread::RunFlipFunction(Thread* self) {
  // This function is called either by the thread running `ThreadList::FlipThreadRoots()` or when
  // a thread becomes runnable, after we've successfully set the kRunningFlipFunction ThreadFlag.
  DCHECK(ReadFlag(ThreadFlag::kRunningFlipFunction));

  Closure* flip_function = GetFlipFunction();
  tlsPtr_.flip_function.store(nullptr, std::memory_order_relaxed);
  DCHECK(flip_function != nullptr);
  VerifyState();
  flip_function->Run(this);
  DCHECK(!ReadFlag(ThreadFlag::kPendingFlipFunction));
  VerifyState();
  AtomicClearFlag(ThreadFlag::kRunningFlipFunction, std::memory_order_release);
  // From here on this thread may go away, and it is no longer safe to access.

  // Notify all threads that are waiting for completion.
  // TODO: Should we create a separate mutex and condition variable instead
  // of piggy-backing on the `thread_suspend_count_lock_` and `resume_cond_`?
  MutexLock mu(self, *Locks::thread_suspend_count_lock_);
  resume_cond_->Broadcast(self);
}

void Thread::WaitForFlipFunction(Thread* self) const {
  // Another thread is running the flip function. Wait for it to complete.
  // Check the flag while holding the mutex so that we do not miss the broadcast.
  // Repeat the check after waiting to guard against spurious wakeups (and because
  // we share the `thread_suspend_count_lock_` and `resume_cond_` with other code).
  // Check that the thread can't unexpectedly exit while we are running.
  DCHECK(self == this || ReadFlag(ThreadFlag::kSuspendRequest) ||
         Locks::thread_list_lock_->IsExclusiveHeld(self));
  MutexLock mu(self, *Locks::thread_suspend_count_lock_);
  while (true) {
    StateAndFlags old_state_and_flags = GetStateAndFlags(std::memory_order_acquire);
    if (!old_state_and_flags.IsFlagSet(ThreadFlag::kRunningFlipFunction)) {
      return;
    }
    // We sometimes hold mutator lock here. OK since the flip function must complete quickly.
    resume_cond_->WaitHoldingLocks(self);
  }
}

void Thread::WaitForFlipFunctionTestingExited(Thread* self, ThreadExitFlag* tef) {
  Locks::thread_list_lock_->Lock(self);
  if (tef->HasExited()) {
    Locks::thread_list_lock_->Unlock(self);
    return;
  }
  // We need to hold suspend_count_lock_ to avoid missed wakeups when the flip function finishes.
  // We need to hold thread_list_lock_ because the tef test result is only valid while we hold the
  // lock, and once kRunningFlipFunction is no longer set, "this" may be deallocated. Hence the
  // complicated locking dance.
  MutexLock mu(self, *Locks::thread_suspend_count_lock_);
  while (true) {
    StateAndFlags old_state_and_flags = GetStateAndFlags(std::memory_order_acquire);
    Locks::thread_list_lock_->Unlock(self);  // So we can wait or return.
    if (!old_state_and_flags.IsFlagSet(ThreadFlag::kRunningFlipFunction)) {
      return;
    }
    resume_cond_->WaitHoldingLocks(self);
    Locks::thread_suspend_count_lock_->Unlock(self);  // To re-lock thread_list_lock.
    Locks::thread_list_lock_->Lock(self);
    Locks::thread_suspend_count_lock_->Lock(self);
    if (tef->HasExited()) {
      Locks::thread_list_lock_->Unlock(self);
      return;
    }
  }
}

void Thread::FullSuspendCheck(bool implicit) {
  ScopedTrace trace(__FUNCTION__);
  DCHECK(!ReadFlag(ThreadFlag::kSuspensionImmune));
  DCHECK(this == Thread::Current());
  VLOG(threads) << this << " self-suspending";
  // Make thread appear suspended to other threads, release mutator_lock_.
  // Transition to suspended and back to runnable, re-acquire share on mutator_lock_.
  ScopedThreadSuspension(this, ThreadState::kSuspended);  // NOLINT
  if (implicit) {
    // For implicit suspend check we want to `madvise()` away
    // the alternate signal stack to avoid wasting memory.
    MadviseAwayAlternateSignalStack();
  }
  VLOG(threads) << this << " self-reviving";
}

static std::string GetSchedulerGroupName(pid_t tid) {
  // /proc/<pid>/cgroup looks like this:
  // 2:devices:/
  // 1:cpuacct,cpu:/
  // We want the third field from the line whose second field contains the "cpu" token.
  std::string cgroup_file;
  if (!android::base::ReadFileToString(StringPrintf("/proc/self/task/%d/cgroup", tid),
                                       &cgroup_file)) {
    return "";
  }
  std::vector<std::string> cgroup_lines;
  Split(cgroup_file, '\n', &cgroup_lines);
  for (size_t i = 0; i < cgroup_lines.size(); ++i) {
    std::vector<std::string> cgroup_fields;
    Split(cgroup_lines[i], ':', &cgroup_fields);
    std::vector<std::string> cgroups;
    Split(cgroup_fields[1], ',', &cgroups);
    for (size_t j = 0; j < cgroups.size(); ++j) {
      if (cgroups[j] == "cpu") {
        return cgroup_fields[2].substr(1);  // Skip the leading slash.
      }
    }
  }
  return "";
}

void Thread::DumpState(std::ostream& os, const Thread* thread, pid_t tid) {
  std::string group_name;
  int priority;
  bool is_daemon = false;
  Thread* self = Thread::Current();

  // Don't do this if we are aborting since the GC may have all the threads suspended. This will
  // cause ScopedObjectAccessUnchecked to deadlock.
  if (gAborting == 0 && self != nullptr && thread != nullptr && thread->tlsPtr_.opeer != nullptr) {
    ScopedObjectAccessUnchecked soa(self);
    priority = WellKnownClasses::java_lang_Thread_priority->GetInt(thread->tlsPtr_.opeer);
    is_daemon = WellKnownClasses::java_lang_Thread_daemon->GetBoolean(thread->tlsPtr_.opeer);

    ObjPtr<mirror::Object> thread_group =
        WellKnownClasses::java_lang_Thread_group->GetObject(thread->tlsPtr_.opeer);

    if (thread_group != nullptr) {
      ObjPtr<mirror::Object> group_name_object =
          WellKnownClasses::java_lang_ThreadGroup_name->GetObject(thread_group);
      group_name = (group_name_object != nullptr)
          ? group_name_object->AsString()->ToModifiedUtf8()
          : "<null>";
    }
  } else if (thread != nullptr) {
    priority = thread->GetNativePriority();
  } else {
    palette_status_t status = PaletteSchedGetPriority(tid, &priority);
    CHECK(status == PALETTE_STATUS_OK || status == PALETTE_STATUS_CHECK_ERRNO);
  }

  std::string scheduler_group_name(GetSchedulerGroupName(tid));
  if (scheduler_group_name.empty()) {
    scheduler_group_name = "default";
  }

  if (thread != nullptr) {
    thread->tls32_.num_name_readers.fetch_add(1, std::memory_order_seq_cst);
    os << '"' << thread->tlsPtr_.name.load() << '"';
    thread->tls32_.num_name_readers.fetch_sub(1 /* at least memory_order_release */);
    if (is_daemon) {
      os << " daemon";
    }
    os << " prio=" << priority
       << " tid=" << thread->GetThreadId()
       << " " << thread->GetState();
    if (thread->IsStillStarting()) {
      os << " (still starting up)";
    }
    if (thread->tls32_.disable_thread_flip_count != 0) {
      os << " DisableFlipCount = " << thread->tls32_.disable_thread_flip_count;
    }
    os << "\n";
  } else {
    os << '"' << ::art::GetThreadName(tid) << '"'
       << " prio=" << priority
       << " (not attached)\n";
  }

  if (thread != nullptr) {
    auto suspend_log_fn = [&]() REQUIRES(Locks::thread_suspend_count_lock_) {
      StateAndFlags state_and_flags = thread->GetStateAndFlags(std::memory_order_relaxed);
      static_assert(
          static_cast<std::underlying_type_t<ThreadState>>(ThreadState::kRunnable) == 0u);
      state_and_flags.SetState(ThreadState::kRunnable);  // Clear state bits.
      os << "  | group=\"" << group_name << "\""
         << " sCount=" << thread->tls32_.suspend_count
         << " ucsCount=" << thread->tls32_.user_code_suspend_count
         << " flags=" << state_and_flags.GetValue()
         << " obj=" << reinterpret_cast<void*>(thread->tlsPtr_.opeer)
         << " self=" << reinterpret_cast<const void*>(thread) << "\n";
    };
    if (Locks::thread_suspend_count_lock_->IsExclusiveHeld(self)) {
      Locks::thread_suspend_count_lock_->AssertExclusiveHeld(self);  // For annotalysis.
      suspend_log_fn();
    } else {
      MutexLock mu(self, *Locks::thread_suspend_count_lock_);
      suspend_log_fn();
    }
  }

  os << "  | sysTid=" << tid
     << " nice=" << getpriority(PRIO_PROCESS, static_cast<id_t>(tid))
     << " cgrp=" << scheduler_group_name;
  if (thread != nullptr) {
    int policy;
    sched_param sp;
#if !defined(__APPLE__)
    // b/36445592 Don't use pthread_getschedparam since pthread may have exited.
    policy = sched_getscheduler(tid);
    if (policy == -1) {
      PLOG(WARNING) << "sched_getscheduler(" << tid << ")";
    }
    int sched_getparam_result = sched_getparam(tid, &sp);
    if (sched_getparam_result == -1) {
      PLOG(WARNING) << "sched_getparam(" << tid << ", &sp)";
      sp.sched_priority = -1;
    }
#else
    CHECK_PTHREAD_CALL(pthread_getschedparam, (thread->tlsPtr_.pthread_self, &policy, &sp),
                       __FUNCTION__);
#endif
    os << " sched=" << policy << "/" << sp.sched_priority
       << " handle=" << reinterpret_cast<void*>(thread->tlsPtr_.pthread_self);
  }
  os << "\n";

  // Grab the scheduler stats for this thread.
  std::string scheduler_stats;
  if (android::base::ReadFileToString(StringPrintf("/proc/self/task/%d/schedstat", tid),
                                      &scheduler_stats)
      && !scheduler_stats.empty()) {
    scheduler_stats = android::base::Trim(scheduler_stats);  // Lose the trailing '\n'.
  } else {
    scheduler_stats = "0 0 0";
  }

  char native_thread_state = '?';
  int utime = 0;
  int stime = 0;
  int task_cpu = 0;
  GetTaskStats(tid, &native_thread_state, &utime, &stime, &task_cpu);

  os << "  | state=" << native_thread_state
     << " schedstat=( " << scheduler_stats << " )"
     << " utm=" << utime
     << " stm=" << stime
     << " core=" << task_cpu
     << " HZ=" << sysconf(_SC_CLK_TCK) << "\n";
  if (thread != nullptr) {
    os << "  | stack=" << reinterpret_cast<void*>(thread->tlsPtr_.stack_begin) << "-"
        << reinterpret_cast<void*>(thread->tlsPtr_.stack_end) << " stackSize="
        << PrettySize(thread->tlsPtr_.stack_size) << "\n";
    // Dump the held mutexes.
    os << "  | held mutexes=";
    for (size_t i = 0; i < kLockLevelCount; ++i) {
      if (i != kMonitorLock) {
        BaseMutex* mutex = thread->GetHeldMutex(static_cast<LockLevel>(i));
        if (mutex != nullptr) {
          os << " \"" << mutex->GetName() << "\"";
          if (mutex->IsReaderWriterMutex()) {
            ReaderWriterMutex* rw_mutex = down_cast<ReaderWriterMutex*>(mutex);
            if (rw_mutex->GetExclusiveOwnerTid() == tid) {
              os << "(exclusive held)";
            } else {
              os << "(shared held)";
            }
          }
        }
      }
    }
    os << "\n";
  }
}

void Thread::DumpState(std::ostream& os) const {
  Thread::DumpState(os, this, GetTid());
}

struct StackDumpVisitor : public MonitorObjectsStackVisitor {
  StackDumpVisitor(std::ostream& os_in,
                   Thread* thread_in,
                   Context* context,
                   bool can_allocate,
                   bool check_suspended = true,
                   bool dump_locks = true)
      REQUIRES_SHARED(Locks::mutator_lock_)
      : MonitorObjectsStackVisitor(thread_in,
                                   context,
                                   check_suspended,
                                   can_allocate && dump_locks),
        os(os_in),
        last_method(nullptr),
        last_line_number(0),
        repetition_count(0) {}

  virtual ~StackDumpVisitor() {
    if (frame_count == 0) {
      os << "  (no managed stack frames)\n";
    }
  }

  static constexpr size_t kMaxRepetition = 3u;

  VisitMethodResult StartMethod(ArtMethod* m, [[maybe_unused]] size_t frame_nr) override
      REQUIRES_SHARED(Locks::mutator_lock_) {
    m = m->GetInterfaceMethodIfProxy(kRuntimePointerSize);
    ObjPtr<mirror::DexCache> dex_cache = m->GetDexCache();
    int line_number = -1;
    uint32_t dex_pc = GetDexPc(false);
    if (dex_cache != nullptr) {  // be tolerant of bad input
      const DexFile* dex_file = dex_cache->GetDexFile();
      line_number = annotations::GetLineNumFromPC(dex_file, m, dex_pc);
    }
    if (line_number == last_line_number && last_method == m) {
      ++repetition_count;
    } else {
      if (repetition_count >= kMaxRepetition) {
        os << "  ... repeated " << (repetition_count - kMaxRepetition) << " times\n";
      }
      repetition_count = 0;
      last_line_number = line_number;
      last_method = m;
    }

    if (repetition_count >= kMaxRepetition) {
      // Skip visiting=printing anything.
      return VisitMethodResult::kSkipMethod;
    }

    os << "  at " << m->PrettyMethod(false);
    if (m->IsNative()) {
      os << "(Native method)";
    } else {
      const char* source_file(m->GetDeclaringClassSourceFile());
      if (line_number == -1) {
        // If we failed to map to a line number, use
        // the dex pc as the line number and leave source file null
        source_file = nullptr;
        line_number = static_cast<int32_t>(dex_pc);
      }
      os << "(" << (source_file != nullptr ? source_file : "unavailable")
                       << ":" << line_number << ")";
    }
    os << "\n";
    // Go and visit locks.
    return VisitMethodResult::kContinueMethod;
  }

  VisitMethodResult EndMethod([[maybe_unused]] ArtMethod* m) override {
    return VisitMethodResult::kContinueMethod;
  }

  void VisitWaitingObject(ObjPtr<mirror::Object> obj, [[maybe_unused]] ThreadState state) override
      REQUIRES_SHARED(Locks::mutator_lock_) {
    PrintObject(obj, "  - waiting on ", ThreadList::kInvalidThreadId);
  }
  void VisitSleepingObject(ObjPtr<mirror::Object> obj)
      override
      REQUIRES_SHARED(Locks::mutator_lock_) {
    PrintObject(obj, "  - sleeping on ", ThreadList::kInvalidThreadId);
  }
  void VisitBlockedOnObject(ObjPtr<mirror::Object> obj,
                            ThreadState state,
                            uint32_t owner_tid)
      override
      REQUIRES_SHARED(Locks::mutator_lock_) {
    const char* msg;
    switch (state) {
      case ThreadState::kBlocked:
        msg = "  - waiting to lock ";
        break;

      case ThreadState::kWaitingForLockInflation:
        msg = "  - waiting for lock inflation of ";
        break;

      default:
        LOG(FATAL) << "Unreachable";
        UNREACHABLE();
    }
    PrintObject(obj, msg, owner_tid);
    num_blocked++;
  }
  void VisitLockedObject(ObjPtr<mirror::Object> obj)
      override
      REQUIRES_SHARED(Locks::mutator_lock_) {
    PrintObject(obj, "  - locked ", ThreadList::kInvalidThreadId);
    num_locked++;
  }

  void PrintObject(ObjPtr<mirror::Object> obj,
                   const char* msg,
                   uint32_t owner_tid) REQUIRES_SHARED(Locks::mutator_lock_) {
    if (obj == nullptr) {
      os << msg << "an unknown object";
    } else {
      const std::string pretty_type(obj->PrettyTypeOf());
      // It's often unsafe to allow lock inflation here. We may be the only runnable thread, or
      // this may be called from a checkpoint. We get the hashcode on a best effort basis.
      static constexpr int kNumRetries = 3;
      static constexpr int kSleepMicros = 10;
      int32_t hash_code;
      for (int i = 0;; ++i) {
        hash_code = obj->IdentityHashCodeNoInflation();
        if (hash_code != 0 || i == kNumRetries) {
          break;
        }
        usleep(kSleepMicros);
      }
      if (hash_code == 0) {
        os << msg
           << StringPrintf("<@addr=0x%" PRIxPTR "> (a %s)",
                           reinterpret_cast<intptr_t>(obj.Ptr()),
                           pretty_type.c_str());
      } else {
        // - waiting on <0x608c468> (a java.lang.Class<java.lang.ref.ReferenceQueue>)
        os << msg << StringPrintf("<0x%08x> (a %s)", hash_code, pretty_type.c_str());
      }
    }
    if (owner_tid != ThreadList::kInvalidThreadId) {
      os << " held by thread " << owner_tid;
    }
    os << "\n";
  }

  std::ostream& os;
  ArtMethod* last_method;
  int last_line_number;
  size_t repetition_count;
  size_t num_blocked = 0;
  size_t num_locked = 0;
};

static bool ShouldShowNativeStack(const Thread* thread)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  ThreadState state = thread->GetState();

  // In native code somewhere in the VM (one of the kWaitingFor* states)? That's interesting.
  if (state > ThreadState::kWaiting && state < ThreadState::kStarting) {
    return true;
  }

  // In an Object.wait variant or Thread.sleep? That's not interesting.
  if (state == ThreadState::kTimedWaiting ||
      state == ThreadState::kSleeping ||
      state == ThreadState::kWaiting) {
    return false;
  }

  // Threads with no managed stack frames should be shown.
  if (!thread->HasManagedStack()) {
    return true;
  }

  // In some other native method? That's interesting.
  // We don't just check kNative because native methods will be in state kSuspended if they're
  // calling back into the VM, or kBlocked if they're blocked on a monitor, or one of the
  // thread-startup states if it's early enough in their life cycle (http://b/7432159).
  ArtMethod* current_method = thread->GetCurrentMethod(nullptr);
  return current_method != nullptr && current_method->IsNative();
}

Thread::DumpOrder Thread::DumpJavaStack(std::ostream& os,
                                        bool check_suspended,
                                        bool dump_locks) const {
  // Dumping the Java stack involves the verifier for locks. The verifier operates under the
  // assumption that there is no exception pending on entry. Thus, stash any pending exception.
  // Thread::Current() instead of this in case a thread is dumping the stack of another suspended
  // thread.
  ScopedExceptionStorage ses(Thread::Current());

  std::unique_ptr<Context> context(Context::Create());
  StackDumpVisitor dumper(os, const_cast<Thread*>(this), context.get(),
                          !tls32_.throwing_OutOfMemoryError, check_suspended, dump_locks);
  dumper.WalkStack();
  if (IsJitSensitiveThread()) {
    return DumpOrder::kMain;
  } else if (dumper.num_blocked > 0) {
    return DumpOrder::kBlocked;
  } else if (dumper.num_locked > 0) {
    return DumpOrder::kLocked;
  } else {
    return DumpOrder::kDefault;
  }
}

Thread::DumpOrder Thread::DumpStack(std::ostream& os,
                                    bool dump_native_stack,
                                    bool force_dump_stack) const {
  unwindstack::AndroidLocalUnwinder unwinder;
  return DumpStack(os, unwinder, dump_native_stack, force_dump_stack);
}

Thread::DumpOrder Thread::DumpStack(std::ostream& os,
                                    unwindstack::AndroidLocalUnwinder& unwinder,
                                    bool dump_native_stack,
                                    bool force_dump_stack) const {
  // TODO: we call this code when dying but may not have suspended the thread ourself. The
  //       IsSuspended check is therefore racy with the use for dumping (normally we inhibit
  //       the race with the thread_suspend_count_lock_).
  bool dump_for_abort = (gAborting > 0);
  bool safe_to_dump = (this == Thread::Current() || IsSuspended());
  if (!kIsDebugBuild) {
    // We always want to dump the stack for an abort, however, there is no point dumping another
    // thread's stack in debug builds where we'll hit the not suspended check in the stack walk.
    safe_to_dump = (safe_to_dump || dump_for_abort);
  }
  DumpOrder dump_order = DumpOrder::kDefault;
  if (safe_to_dump || force_dump_stack) {
    uint64_t nanotime = NanoTime();
    // If we're currently in native code, dump that stack before dumping the managed stack.
    if (dump_native_stack && (dump_for_abort || force_dump_stack || ShouldShowNativeStack(this))) {
      ArtMethod* method =
          GetCurrentMethod(nullptr,
                           /*check_suspended=*/ !force_dump_stack,
                           /*abort_on_error=*/ !(dump_for_abort || force_dump_stack));
      DumpNativeStack(os, unwinder, GetTid(), "  native: ", method);
    }
    dump_order = DumpJavaStack(os,
                               /*check_suspended=*/ !force_dump_stack,
                               /*dump_locks=*/ !force_dump_stack);
    Runtime* runtime = Runtime::Current();
    std::optional<uint64_t> start = runtime != nullptr ? runtime->SiqQuitNanoTime() : std::nullopt;
    if (start.has_value()) {
      os << "DumpLatencyMs: " << static_cast<float>(nanotime - start.value()) / 1000000.0 << "\n";
    }
  } else {
    os << "Not able to dump stack of thread that isn't suspended";
  }
  return dump_order;
}

void Thread::ThreadExitCallback(void* arg) {
  Thread* self = reinterpret_cast<Thread*>(arg);
  if (self->tls32_.thread_exit_check_count == 0) {
    LOG(WARNING) << "Native thread exiting without having called DetachCurrentThread (maybe it's "
        "going to use a pthread_key_create destructor?): " << *self;
    CHECK(is_started_);
#ifdef __BIONIC__
    __get_tls()[TLS_SLOT_ART_THREAD_SELF] = self;
#else
    CHECK_PTHREAD_CALL(pthread_setspecific, (Thread::pthread_key_self_, self), "reattach self");
    Thread::self_tls_ = self;
#endif
    self->tls32_.thread_exit_check_count = 1;
  } else {
    LOG(FATAL) << "Native thread exited without calling DetachCurrentThread: " << *self;
  }
}

void Thread::Startup() {
  CHECK(!is_started_);
  is_started_ = true;
  {
    // MutexLock to keep annotalysis happy.
    //
    // Note we use null for the thread because Thread::Current can
    // return garbage since (is_started_ == true) and
    // Thread::pthread_key_self_ is not yet initialized.
    // This was seen on glibc.
    MutexLock mu(nullptr, *Locks::thread_suspend_count_lock_);
    resume_cond_ = new ConditionVariable("Thread resumption condition variable",
                                         *Locks::thread_suspend_count_lock_);
  }

  // Allocate a TLS slot.
  CHECK_PTHREAD_CALL(pthread_key_create, (&Thread::pthread_key_self_, Thread::ThreadExitCallback),
                     "self key");

  // Double-check the TLS slot allocation.
  if (pthread_getspecific(pthread_key_self_) != nullptr) {
    LOG(FATAL) << "Newly-created pthread TLS slot is not nullptr";
  }
#ifndef __BIONIC__
  CHECK(Thread::self_tls_ == nullptr);
#endif
}

void Thread::FinishStartup() {
  Runtime* runtime = Runtime::Current();
  CHECK(runtime->IsStarted());

  // Finish attaching the main thread.
  ScopedObjectAccess soa(Thread::Current());
  soa.Self()->CreatePeer("main", false, runtime->GetMainThreadGroup());
  soa.Self()->AssertNoPendingException();

  runtime->RunRootClinits(soa.Self());

  // The thread counts as started from now on. We need to add it to the ThreadGroup. For regular
  // threads, this is done in Thread.start() on the Java side.
  soa.Self()->NotifyThreadGroup(soa, runtime->GetMainThreadGroup());
  soa.Self()->AssertNoPendingException();
}

void Thread::Shutdown() {
  CHECK(is_started_);
  is_started_ = false;
  CHECK_PTHREAD_CALL(pthread_key_delete, (Thread::pthread_key_self_), "self key");
  MutexLock mu(Thread::Current(), *Locks::thread_suspend_count_lock_);
  if (resume_cond_ != nullptr) {
    delete resume_cond_;
    resume_cond_ = nullptr;
  }
}

void Thread::NotifyThreadGroup(ScopedObjectAccessAlreadyRunnable& soa, jobject thread_group) {
  ObjPtr<mirror::Object> thread_object = soa.Self()->GetPeer();
  ObjPtr<mirror::Object> thread_group_object = soa.Decode<mirror::Object>(thread_group);
  if (thread_group == nullptr || kIsDebugBuild) {
    // There is always a group set. Retrieve it.
    thread_group_object = WellKnownClasses::java_lang_Thread_group->GetObject(thread_object);
    if (kIsDebugBuild && thread_group != nullptr) {
      CHECK(thread_group_object == soa.Decode<mirror::Object>(thread_group));
    }
  }
  WellKnownClasses::java_lang_ThreadGroup_add->InvokeVirtual<'V', 'L'>(
      soa.Self(), thread_group_object, thread_object);
}

void Thread::SignalExitFlags() {
  ThreadExitFlag* next;
  for (ThreadExitFlag* tef = tlsPtr_.thread_exit_flags; tef != nullptr; tef = next) {
    DCHECK(!tef->exited_);
    tef->exited_ = true;
    next = tef->next_;
    if (kIsDebugBuild) {
      ThreadExitFlag* const garbage_tef = reinterpret_cast<ThreadExitFlag*>(1);
      // Link fields should no longer be used.
      tef->prev_ = tef->next_ = garbage_tef;
    }
  }
  tlsPtr_.thread_exit_flags = nullptr;  // Now unused.
}

Thread::Thread(bool daemon)
    : tls32_(daemon),
      wait_monitor_(nullptr),
      is_runtime_thread_(false) {
  wait_mutex_ = new Mutex("a thread wait mutex", LockLevel::kThreadWaitLock);
  wait_cond_ = new ConditionVariable("a thread wait condition variable", *wait_mutex_);
  tlsPtr_.mutator_lock = Locks::mutator_lock_;
  DCHECK(tlsPtr_.mutator_lock != nullptr);
  tlsPtr_.name.store(kThreadNameDuringStartup, std::memory_order_relaxed);
  CHECK_NE(GetStackOverflowProtectedSize(), 0u);

  static_assert((sizeof(Thread) % 4) == 0U,
                "art::Thread has a size which is not a multiple of 4.");
  DCHECK_EQ(GetStateAndFlags(std::memory_order_relaxed).GetValue(), 0u);
  StateAndFlags state_and_flags = StateAndFlags(0u).WithState(ThreadState::kNative);
  tls32_.state_and_flags.store(state_and_flags.GetValue(), std::memory_order_relaxed);
  tls32_.interrupted.store(false, std::memory_order_relaxed);
  // Initialize with no permit; if the java Thread was unparked before being
  // started, it will unpark itself before calling into java code.
  tls32_.park_state_.store(kNoPermit, std::memory_order_relaxed);
  memset(&tlsPtr_.held_mutexes[0], 0, sizeof(tlsPtr_.held_mutexes));
  std::fill(tlsPtr_.rosalloc_runs,
            tlsPtr_.rosalloc_runs + kNumRosAllocThreadLocalSizeBracketsInThread,
            gc::allocator::RosAlloc::GetDedicatedFullRun());
  tlsPtr_.checkpoint_function = nullptr;
  tlsPtr_.active_suspendall_barrier = nullptr;
  tlsPtr_.active_suspend1_barriers = nullptr;
  tlsPtr_.flip_function.store(nullptr, std::memory_order_relaxed);
  tlsPtr_.thread_local_mark_stack = nullptr;
  ResetTlab();
}

bool Thread::CanLoadClasses() const {
  return !IsRuntimeThread() || !Runtime::Current()->IsJavaDebuggable();
}

bool Thread::IsStillStarting() const {
  // You might think you can check whether the state is kStarting, but for much of thread startup,
  // the thread is in kNative; it might also be in kVmWait.
  // You might think you can check whether the peer is null, but the peer is actually created and
  // assigned fairly early on, and needs to be.
  // It turns out that the last thing to change is the thread name; that's a good proxy for "has
  // this thread _ever_ entered kRunnable".
  return (tlsPtr_.jpeer == nullptr && tlsPtr_.opeer == nullptr) ||
      (tlsPtr_.name.load() == kThreadNameDuringStartup);
}

void Thread::AssertPendingException() const {
  CHECK(IsExceptionPending()) << "Pending exception expected.";
}

void Thread::AssertPendingOOMException() const {
  AssertPendingException();
  auto* e = GetException();
  CHECK_EQ(e->GetClass(), WellKnownClasses::java_lang_OutOfMemoryError.Get()) << e->Dump();
}

void Thread::AssertNoPendingException() const {
  if (UNLIKELY(IsExceptionPending())) {
    ScopedObjectAccess soa(Thread::Current());
    LOG(FATAL) << "No pending exception expected: " << GetException()->Dump();
  }
}

void Thread::AssertNoPendingExceptionForNewException(const char* msg) const {
  if (UNLIKELY(IsExceptionPending())) {
    ScopedObjectAccess soa(Thread::Current());
    LOG(FATAL) << "Throwing new exception '" << msg << "' with unexpected pending exception: "
        << GetException()->Dump();
  }
}

class MonitorExitVisitor : public SingleRootVisitor {
 public:
  explicit MonitorExitVisitor(Thread* self) : self_(self) { }

  // NO_THREAD_SAFETY_ANALYSIS due to MonitorExit.
  void VisitRoot(mirror::Object* entered_monitor,
                 [[maybe_unused]] const RootInfo& info) override NO_THREAD_SAFETY_ANALYSIS {
    if (self_->HoldsLock(entered_monitor)) {
      LOG(WARNING) << "Calling MonitorExit on object "
                   << entered_monitor << " (" << entered_monitor->PrettyTypeOf() << ")"
                   << " left locked by native thread "
                   << *Thread::Current() << " which is detaching";
      entered_monitor->MonitorExit(self_);
    }
  }

 private:
  Thread* const self_;
};

void Thread::Destroy(bool should_run_callbacks) {
  Thread* self = this;
  DCHECK_EQ(self, Thread::Current());

  if (tlsPtr_.jni_env != nullptr) {
    {
      ScopedObjectAccess soa(self);
      MonitorExitVisitor visitor(self);
      // On thread detach, all monitors entered with JNI MonitorEnter are automatically exited.
      tlsPtr_.jni_env->monitors_.VisitRoots(&visitor, RootInfo(kRootVMInternal));
    }
    // Release locally held global references which releasing may require the mutator lock.
    if (tlsPtr_.jpeer != nullptr) {
      // If pthread_create fails we don't have a jni env here.
      tlsPtr_.jni_env->DeleteGlobalRef(tlsPtr_.jpeer);
      tlsPtr_.jpeer = nullptr;
    }
    if (tlsPtr_.class_loader_override != nullptr) {
      tlsPtr_.jni_env->DeleteGlobalRef(tlsPtr_.class_loader_override);
      tlsPtr_.class_loader_override = nullptr;
    }
  }

  if (tlsPtr_.opeer != nullptr) {
    ScopedObjectAccess soa(self);
    // We may need to call user-supplied managed code, do this before final clean-up.
    HandleUncaughtExceptions();
    RemoveFromThreadGroup();
    Runtime* runtime = Runtime::Current();
    if (runtime != nullptr && should_run_callbacks) {
      runtime->GetRuntimeCallbacks()->ThreadDeath(self);
    }

    // this.nativePeer = 0;
    SetNativePeer</*kSupportTransaction=*/ true>(tlsPtr_.opeer, nullptr);

    // Thread.join() is implemented as an Object.wait() on the Thread.lock object. Signal anyone
    // who is waiting.
    ObjPtr<mirror::Object> lock =
        WellKnownClasses::java_lang_Thread_lock->GetObject(tlsPtr_.opeer);
    // (This conditional is only needed for tests, where Thread.lock won't have been set.)
    if (lock != nullptr) {
      StackHandleScope<1> hs(self);
      Handle<mirror::Object> h_obj(hs.NewHandle(lock));
      ObjectLock<mirror::Object> locker(self, h_obj);
      locker.NotifyAll();
    }

    tlsPtr_.opeer = nullptr;
  }

  {
    ScopedObjectAccess soa(self);
    Runtime::Current()->GetHeap()->RevokeThreadLocalBuffers(this);

    if (UNLIKELY(self->GetMethodTraceBuffer() != nullptr)) {
      Trace::FlushThreadBuffer(self);
    }
  }
  // Mark-stack revocation must be performed at the very end. No
  // checkpoint/flip-function or read-barrier should be called after this.
  if (gUseReadBarrier) {
    Runtime::Current()->GetHeap()->ConcurrentCopyingCollector()->RevokeThreadLocalMarkStack(this);
  }
}

Thread::~Thread() {
  CHECK(tlsPtr_.class_loader_override == nullptr);
  CHECK(tlsPtr_.jpeer == nullptr);
  CHECK(tlsPtr_.opeer == nullptr);
  bool initialized = (tlsPtr_.jni_env != nullptr);  // Did Thread::Init run?
  if (initialized) {
    delete tlsPtr_.jni_env;
    tlsPtr_.jni_env = nullptr;
  }
  CHECK_NE(GetState(), ThreadState::kRunnable);
  CHECK(!ReadFlag(ThreadFlag::kCheckpointRequest));
  CHECK(!ReadFlag(ThreadFlag::kEmptyCheckpointRequest));
  CHECK(!ReadFlag(ThreadFlag::kSuspensionImmune));
  CHECK(tlsPtr_.checkpoint_function == nullptr);
  CHECK_EQ(checkpoint_overflow_.size(), 0u);
  // A pending flip function request is OK. FlipThreadRoots will have been notified that we
  // exited, and nobody will attempt to process the request.

  // Make sure we processed all deoptimization requests.
  CHECK(tlsPtr_.deoptimization_context_stack == nullptr) << "Missed deoptimization";
  CHECK(tlsPtr_.frame_id_to_shadow_frame == nullptr) <<
      "Not all deoptimized frames have been consumed by the debugger.";

  // We may be deleting a still born thread.
  SetStateUnsafe(ThreadState::kTerminated);

  delete wait_cond_;
  delete wait_mutex_;

  if (tlsPtr_.long_jump_context != nullptr) {
    delete tlsPtr_.long_jump_context;
  }

  if (initialized) {
    CleanupCpu();
  }

  SetCachedThreadName(nullptr);  // Deallocate name.
  delete tlsPtr_.deps_or_stack_trace_sample.stack_trace_sample;

  CHECK_EQ(tlsPtr_.method_trace_buffer, nullptr);

  Runtime::Current()->GetHeap()->AssertThreadLocalBuffersAreRevoked(this);

  TearDownAlternateSignalStack();
}

void Thread::HandleUncaughtExceptions() {
  Thread* self = this;
  DCHECK_EQ(self, Thread::Current());
  if (!self->IsExceptionPending()) {
    return;
  }

  // Get and clear the exception.
  ObjPtr<mirror::Object> exception = self->GetException();
  self->ClearException();

  // Call the Thread instance's dispatchUncaughtException(Throwable)
  WellKnownClasses::java_lang_Thread_dispatchUncaughtException->InvokeFinal<'V', 'L'>(
      self, tlsPtr_.opeer, exception);

  // If the dispatchUncaughtException threw, clear that exception too.
  self->ClearException();
}

void Thread::RemoveFromThreadGroup() {
  Thread* self = this;
  DCHECK_EQ(self, Thread::Current());
  // this.group.threadTerminated(this);
  // group can be null if we're in the compiler or a test.
  ObjPtr<mirror::Object> group =
      WellKnownClasses::java_lang_Thread_group->GetObject(tlsPtr_.opeer);
  if (group != nullptr) {
    WellKnownClasses::java_lang_ThreadGroup_threadTerminated->InvokeVirtual<'V', 'L'>(
        self, group, tlsPtr_.opeer);
  }
}

template <bool kPointsToStack>
class JniTransitionReferenceVisitor : public StackVisitor {
 public:
  JniTransitionReferenceVisitor(Thread* thread, void* obj) REQUIRES_SHARED(Locks::mutator_lock_)
      : StackVisitor(thread, /*context=*/ nullptr, StackVisitor::StackWalkKind::kSkipInlinedFrames),
        obj_(obj),
        found_(false) {}

  bool VisitFrame() override REQUIRES_SHARED(Locks::mutator_lock_) {
    ArtMethod* m = GetMethod();
    if (!m->IsNative() || m->IsCriticalNative()) {
      return true;
    }
    if (kPointsToStack) {
      uint8_t* sp = reinterpret_cast<uint8_t*>(GetCurrentQuickFrame());
      size_t frame_size = GetCurrentQuickFrameInfo().FrameSizeInBytes();
      uint32_t* current_vreg = reinterpret_cast<uint32_t*>(sp + frame_size + sizeof(ArtMethod*));
      if (!m->IsStatic()) {
        if (current_vreg == obj_) {
          found_ = true;
          return false;
        }
        current_vreg += 1u;
      }
      uint32_t shorty_length;
      const char* shorty = m->GetShorty(&shorty_length);
      for (size_t i = 1; i != shorty_length; ++i) {
        switch (shorty[i]) {
          case 'D':
          case 'J':
            current_vreg += 2u;
            break;
          case 'L':
            if (current_vreg == obj_) {
              found_ = true;
              return false;
            }
            FALLTHROUGH_INTENDED;
          default:
            current_vreg += 1u;
            break;
        }
      }
      // Continue only if the object is somewhere higher on the stack.
      return obj_ >= current_vreg;
    } else {  // if (kPointsToStack)
      if (m->IsStatic() && obj_ == m->GetDeclaringClassAddressWithoutBarrier()) {
        found_ = true;
        return false;
      }
      return true;
    }
  }

  bool Found() const {
    return found_;
  }

 private:
  void* obj_;
  bool found_;
};

bool Thread::IsJniTransitionReference(jobject obj) const {
  DCHECK(obj != nullptr);
  // We need a non-const pointer for stack walk even if we're not modifying the thread state.
  Thread* thread = const_cast<Thread*>(this);
  uint8_t* raw_obj = reinterpret_cast<uint8_t*>(obj);
  if (static_cast<size_t>(raw_obj - tlsPtr_.stack_begin) < tlsPtr_.stack_size) {
    JniTransitionReferenceVisitor</*kPointsToStack=*/ true> visitor(thread, raw_obj);
    visitor.WalkStack();
    return visitor.Found();
  } else {
    JniTransitionReferenceVisitor</*kPointsToStack=*/ false> visitor(thread, raw_obj);
    visitor.WalkStack();
    return visitor.Found();
  }
}

void Thread::HandleScopeVisitRoots(RootVisitor* visitor, uint32_t thread_id) {
  BufferedRootVisitor<kDefaultBufferedRootCount> buffered_visitor(
      visitor, RootInfo(kRootNativeStack, thread_id));
  for (BaseHandleScope* cur = tlsPtr_.top_handle_scope; cur; cur = cur->GetLink()) {
    cur->VisitRoots(buffered_visitor);
  }
}

ObjPtr<mirror::Object> Thread::DecodeGlobalJObject(jobject obj) const {
  DCHECK(obj != nullptr);
  IndirectRef ref = reinterpret_cast<IndirectRef>(obj);
  IndirectRefKind kind = IndirectReferenceTable::GetIndirectRefKind(ref);
  DCHECK_NE(kind, kJniTransition);
  DCHECK_NE(kind, kLocal);
  ObjPtr<mirror::Object> result;
  bool expect_null = false;
  if (kind == kGlobal) {
    result = tlsPtr_.jni_env->vm_->DecodeGlobal(ref);
  } else {
    DCHECK_EQ(kind, kWeakGlobal);
    result = tlsPtr_.jni_env->vm_->DecodeWeakGlobal(const_cast<Thread*>(this), ref);
    if (Runtime::Current()->IsClearedJniWeakGlobal(result)) {
      // This is a special case where it's okay to return null.
      expect_null = true;
      result = nullptr;
    }
  }

  DCHECK(expect_null || result != nullptr)
      << "use of deleted " << ToStr<IndirectRefKind>(kind).c_str()
      << " " << static_cast<const void*>(obj);
  return result;
}

bool Thread::IsJWeakCleared(jweak obj) const {
  CHECK(obj != nullptr);
  IndirectRef ref = reinterpret_cast<IndirectRef>(obj);
  IndirectRefKind kind = IndirectReferenceTable::GetIndirectRefKind(ref);
  CHECK_EQ(kind, kWeakGlobal);
  return tlsPtr_.jni_env->vm_->IsWeakGlobalCleared(const_cast<Thread*>(this), ref);
}

// Implements java.lang.Thread.interrupted.
bool Thread::Interrupted() {
  DCHECK_EQ(Thread::Current(), this);
  // No other thread can concurrently reset the interrupted flag.
  bool interrupted = tls32_.interrupted.load(std::memory_order_seq_cst);
  if (interrupted) {
    tls32_.interrupted.store(false, std::memory_order_seq_cst);
  }
  return interrupted;
}

// Implements java.lang.Thread.isInterrupted.
bool Thread::IsInterrupted() {
  return tls32_.interrupted.load(std::memory_order_seq_cst);
}

void Thread::Interrupt(Thread* self) {
  {
    MutexLock mu(self, *wait_mutex_);
    if (tls32_.interrupted.load(std::memory_order_seq_cst)) {
      return;
    }
    tls32_.interrupted.store(true, std::memory_order_seq_cst);
    NotifyLocked(self);
  }
  Unpark();
}

void Thread::Notify() {
  Thread* self = Thread::Current();
  MutexLock mu(self, *wait_mutex_);
  NotifyLocked(self);
}

void Thread::NotifyLocked(Thread* self) {
  if (wait_monitor_ != nullptr) {
    wait_cond_->Signal(self);
  }
}

void Thread::SetClassLoaderOverride(jobject class_loader_override) {
  if (tlsPtr_.class_loader_override != nullptr) {
    GetJniEnv()->DeleteGlobalRef(tlsPtr_.class_loader_override);
  }
  tlsPtr_.class_loader_override = GetJniEnv()->NewGlobalRef(class_loader_override);
}

using ArtMethodDexPcPair = std::pair<ArtMethod*, uint32_t>;

// Counts the stack trace depth and also fetches the first max_saved_frames frames.
class FetchStackTraceVisitor : public StackVisitor {
 public:
  explicit FetchStackTraceVisitor(Thread* thread,
                                  ArtMethodDexPcPair* saved_frames = nullptr,
                                  size_t max_saved_frames = 0)
      REQUIRES_SHARED(Locks::mutator_lock_)
      : StackVisitor(thread, nullptr, StackVisitor::StackWalkKind::kIncludeInlinedFrames),
        saved_frames_(saved_frames),
        max_saved_frames_(max_saved_frames) {}

  bool VisitFrame() override REQUIRES_SHARED(Locks::mutator_lock_) {
    // We want to skip frames up to and including the exception's constructor.
    // Note we also skip the frame if it doesn't have a method (namely the callee
    // save frame)
    ArtMethod* m = GetMethod();
    if (skipping_ && !m->IsRuntimeMethod() &&
        !GetClassRoot<mirror::Throwable>()->IsAssignableFrom(m->GetDeclaringClass())) {
      skipping_ = false;
    }
    if (!skipping_) {
      if (!m->IsRuntimeMethod()) {  // Ignore runtime frames (in particular callee save).
        if (depth_ < max_saved_frames_) {
          saved_frames_[depth_].first = m;
          saved_frames_[depth_].second = m->IsProxyMethod() ? dex::kDexNoIndex : GetDexPc();
        }
        ++depth_;
      }
    } else {
      ++skip_depth_;
    }
    return true;
  }

  uint32_t GetDepth() const {
    return depth_;
  }

  uint32_t GetSkipDepth() const {
    return skip_depth_;
  }

 private:
  uint32_t depth_ = 0;
  uint32_t skip_depth_ = 0;
  bool skipping_ = true;
  ArtMethodDexPcPair* saved_frames_;
  const size_t max_saved_frames_;

  DISALLOW_COPY_AND_ASSIGN(FetchStackTraceVisitor);
};

class BuildInternalStackTraceVisitor : public StackVisitor {
 public:
  BuildInternalStackTraceVisitor(Thread* self, Thread* thread, uint32_t skip_depth)
      : StackVisitor(thread, nullptr, StackVisitor::StackWalkKind::kIncludeInlinedFrames),
        self_(self),
        skip_depth_(skip_depth),
        pointer_size_(Runtime::Current()->GetClassLinker()->GetImagePointerSize()) {}

  bool Init(uint32_t depth) REQUIRES_SHARED(Locks::mutator_lock_) ACQUIRE(Roles::uninterruptible_) {
    // Allocate method trace as an object array where the first element is a pointer array that
    // contains the ArtMethod pointers and dex PCs. The rest of the elements are the declaring
    // class of the ArtMethod pointers.
    ClassLinker* class_linker = Runtime::Current()->GetClassLinker();
    StackHandleScope<1> hs(self_);
    ObjPtr<mirror::Class> array_class =
        GetClassRoot<mirror::ObjectArray<mirror::Object>>(class_linker);
    // The first element is the methods and dex pc array, the other elements are declaring classes
    // for the methods to ensure classes in the stack trace don't get unloaded.
    Handle<mirror::ObjectArray<mirror::Object>> trace(
        hs.NewHandle(mirror::ObjectArray<mirror::Object>::Alloc(
            hs.Self(), array_class, static_cast<int32_t>(depth) + 1)));
    if (trace == nullptr) {
      // Acquire uninterruptible_ in all paths.
      self_->StartAssertNoThreadSuspension("Building internal stack trace");
      self_->AssertPendingOOMException();
      return false;
    }
    ObjPtr<mirror::PointerArray> methods_and_pcs =
        class_linker->AllocPointerArray(self_, depth * 2);
    const char* last_no_suspend_cause =
        self_->StartAssertNoThreadSuspension("Building internal stack trace");
    if (methods_and_pcs == nullptr) {
      self_->AssertPendingOOMException();
      return false;
    }
    trace->Set</*kTransactionActive=*/ false, /*kCheckTransaction=*/ false>(0, methods_and_pcs);
    trace_ = trace.Get();
    // If We are called from native, use non-transactional mode.
    CHECK(last_no_suspend_cause == nullptr) << last_no_suspend_cause;
    return true;
  }

  virtual ~BuildInternalStackTraceVisitor() RELEASE(Roles::uninterruptible_) {
    self_->EndAssertNoThreadSuspension(nullptr);
  }

  bool VisitFrame() override REQUIRES_SHARED(Locks::mutator_lock_) {
    if (trace_ == nullptr) {
      return true;  // We're probably trying to fillInStackTrace for an OutOfMemoryError.
    }
    if (skip_depth_ > 0) {
      skip_depth_--;
      return true;
    }
    ArtMethod* m = GetMethod();
    if (m->IsRuntimeMethod()) {
      return true;  // Ignore runtime frames (in particular callee save).
    }
    AddFrame(m, m->IsProxyMethod() ? dex::kDexNoIndex : GetDexPc());
    return true;
  }

  void AddFrame(ArtMethod* method, uint32_t dex_pc) REQUIRES_SHARED(Locks::mutator_lock_) {
    ObjPtr<mirror::PointerArray> methods_and_pcs = GetTraceMethodsAndPCs();
    methods_and_pcs->SetElementPtrSize</*kTransactionActive=*/ false, /*kCheckTransaction=*/ false>(
        count_, method, pointer_size_);
    methods_and_pcs->SetElementPtrSize</*kTransactionActive=*/ false, /*kCheckTransaction=*/ false>(
        static_cast<uint32_t>(methods_and_pcs->GetLength()) / 2 + count_, dex_pc, pointer_size_);
    // Save the declaring class of the method to ensure that the declaring classes of the methods
    // do not get unloaded while the stack trace is live. However, this does not work for copied
    // methods because the declaring class of a copied method points to an interface class which
    // may be in a different class loader. Instead, retrieve the class loader associated with the
    // allocator that holds the copied method. This is much cheaper than finding the actual class.
    ObjPtr<mirror::Object> keep_alive;
    if (UNLIKELY(method->IsCopied())) {
      ClassLinker* class_linker = Runtime::Current()->GetClassLinker();
      keep_alive = class_linker->GetHoldingClassLoaderOfCopiedMethod(self_, method);
    } else {
      keep_alive = method->GetDeclaringClass();
    }
    trace_->Set</*kTransactionActive=*/ false, /*kCheckTransaction=*/ false>(
        static_cast<int32_t>(count_) + 1, keep_alive);
    ++count_;
  }

  ObjPtr<mirror::PointerArray> GetTraceMethodsAndPCs() const REQUIRES_SHARED(Locks::mutator_lock_) {
    return ObjPtr<mirror::PointerArray>::DownCast(trace_->Get(0));
  }

  mirror::ObjectArray<mirror::Object>* GetInternalStackTrace() const {
    return trace_;
  }

 private:
  Thread* const self_;
  // How many more frames to skip.
  uint32_t skip_depth_;
  // Current position down stack trace.
  uint32_t count_ = 0;
  // An object array where the first element is a pointer array that contains the `ArtMethod`
  // pointers on the stack and dex PCs. The rest of the elements are referencing objects
  // that shall keep the methods alive, namely the declaring class of the `ArtMethod` for
  // declared methods and the class loader for copied methods (because it's faster to find
  // the class loader than the actual class that holds the copied method). The `trace_[i+1]`
  // contains the declaring class or class loader of the `ArtMethod` of the i'th frame.
  // We're initializing a newly allocated trace, so we do not need to record that under
  // a transaction. If the transaction is aborted, the whole trace shall be unreachable.
  mirror::ObjectArray<mirror::Object>* trace_ = nullptr;
  // For cross compilation.
  const PointerSize pointer_size_;

  DISALLOW_COPY_AND_ASSIGN(BuildInternalStackTraceVisitor);
};

jobject Thread::CreateInternalStackTrace(const ScopedObjectAccessAlreadyRunnable& soa) const {
  // Compute depth of stack, save frames if possible to avoid needing to recompute many.
  constexpr size_t kMaxSavedFrames = 256;
  std::unique_ptr<ArtMethodDexPcPair[]> saved_frames(new ArtMethodDexPcPair[kMaxSavedFrames]);
  FetchStackTraceVisitor count_visitor(const_cast<Thread*>(this),
                                       &saved_frames[0],
                                       kMaxSavedFrames);
  count_visitor.WalkStack();
  const uint32_t depth = count_visitor.GetDepth();
  const uint32_t skip_depth = count_visitor.GetSkipDepth();

  // Build internal stack trace.
  BuildInternalStackTraceVisitor build_trace_visitor(
      soa.Self(), const_cast<Thread*>(this), skip_depth);
  if (!build_trace_visitor.Init(depth)) {
    return nullptr;  // Allocation failed.
  }
  // If we saved all of the frames we don't even need to do the actual stack walk. This is faster
  // than doing the stack walk twice.
  if (depth < kMaxSavedFrames) {
    for (size_t i = 0; i < depth; ++i) {
      build_trace_visitor.AddFrame(saved_frames[i].first, saved_frames[i].second);
    }
  } else {
    build_trace_visitor.WalkStack();
  }

  mirror::ObjectArray<mirror::Object>* trace = build_trace_visitor.GetInternalStackTrace();
  if (kIsDebugBuild) {
    ObjPtr<mirror::PointerArray> trace_methods = build_trace_visitor.GetTraceMethodsAndPCs();
    // Second half of trace_methods is dex PCs.
    for (uint32_t i = 0; i < static_cast<uint32_t>(trace_methods->GetLength() / 2); ++i) {
      auto* method = trace_methods->GetElementPtrSize<ArtMethod*>(
          i, Runtime::Current()->GetClassLinker()->GetImagePointerSize());
      CHECK(method != nullptr);
    }
  }
  return soa.AddLocalReference<jobject>(trace);
}

bool Thread::IsExceptionThrownByCurrentMethod(ObjPtr<mirror::Throwable> exception) const {
  // Only count the depth since we do not pass a stack frame array as an argument.
  FetchStackTraceVisitor count_visitor(const_cast<Thread*>(this));
  count_visitor.WalkStack();
  return count_visitor.GetDepth() == static_cast<uint32_t>(exception->GetStackDepth());
}

static ObjPtr<mirror::StackTraceElement> CreateStackTraceElement(
    const ScopedObjectAccessAlreadyRunnable& soa,
    ArtMethod* method,
    uint32_t dex_pc) REQUIRES_SHARED(Locks::mutator_lock_) {
  int32_t line_number;
  StackHandleScope<3> hs(soa.Self());
  auto class_name_object(hs.NewHandle<mirror::String>(nullptr));
  auto source_name_object(hs.NewHandle<mirror::String>(nullptr));
  if (method->IsProxyMethod()) {
    line_number = -1;
    class_name_object.Assign(method->GetDeclaringClass()->GetName());
    // source_name_object intentionally left null for proxy methods
  } else {
    line_number = method->GetLineNumFromDexPC(dex_pc);
    // Allocate element, potentially triggering GC
    // TODO: reuse class_name_object via Class::name_?
    const char* descriptor = method->GetDeclaringClassDescriptor();
    CHECK(descriptor != nullptr);
    std::string class_name(PrettyDescriptor(descriptor));
    class_name_object.Assign(
        mirror::String::AllocFromModifiedUtf8(soa.Self(), class_name.c_str()));
    if (class_name_object == nullptr) {
      soa.Self()->AssertPendingOOMException();
      return nullptr;
    }
    const char* source_file = method->GetDeclaringClassSourceFile();
    if (line_number == -1) {
      // Make the line_number field of StackTraceElement hold the dex pc.
      // source_name_object is intentionally left null if we failed to map the dex pc to
      // a line number (most probably because there is no debug info). See b/30183883.
      line_number = static_cast<int32_t>(dex_pc);
    } else {
      if (source_file != nullptr) {
        source_name_object.Assign(mirror::String::AllocFromModifiedUtf8(soa.Self(), source_file));
        if (source_name_object == nullptr) {
          soa.Self()->AssertPendingOOMException();
          return nullptr;
        }
      }
    }
  }
  const char* method_name = method->GetInterfaceMethodIfProxy(kRuntimePointerSize)->GetName();
  CHECK(method_name != nullptr);
  Handle<mirror::String> method_name_object(
      hs.NewHandle(mirror::String::AllocFromModifiedUtf8(soa.Self(), method_name)));
  if (method_name_object == nullptr) {
    return nullptr;
  }
  return mirror::StackTraceElement::Alloc(soa.Self(),
                                          class_name_object,
                                          method_name_object,
                                          source_name_object,
                                          line_number);
}

jobjectArray Thread::InternalStackTraceToStackTraceElementArray(
    const ScopedObjectAccessAlreadyRunnable& soa,
    jobject internal,
    jobjectArray output_array,
    int* stack_depth) {
  // Decode the internal stack trace into the depth, method trace and PC trace.
  // Subtract one for the methods and PC trace.
  int32_t depth = soa.Decode<mirror::Array>(internal)->GetLength() - 1;
  DCHECK_GE(depth, 0);

  ClassLinker* const class_linker = Runtime::Current()->GetClassLinker();

  jobjectArray result;

  if (output_array != nullptr) {
    // Reuse the array we were given.
    result = output_array;
    // ...adjusting the number of frames we'll write to not exceed the array length.
    const int32_t traces_length =
        soa.Decode<mirror::ObjectArray<mirror::StackTraceElement>>(result)->GetLength();
    depth = std::min(depth, traces_length);
  } else {
    // Create java_trace array and place in local reference table
    ObjPtr<mirror::ObjectArray<mirror::StackTraceElement>> java_traces =
        class_linker->AllocStackTraceElementArray(soa.Self(), static_cast<size_t>(depth));
    if (java_traces == nullptr) {
      return nullptr;
    }
    result = soa.AddLocalReference<jobjectArray>(java_traces);
  }

  if (stack_depth != nullptr) {
    *stack_depth = depth;
  }

  for (uint32_t i = 0; i < static_cast<uint32_t>(depth); ++i) {
    ObjPtr<mirror::ObjectArray<mirror::Object>> decoded_traces =
        soa.Decode<mirror::Object>(internal)->AsObjectArray<mirror::Object>();
    // Methods and dex PC trace is element 0.
    DCHECK(decoded_traces->Get(0)->IsIntArray() || decoded_traces->Get(0)->IsLongArray());
    const ObjPtr<mirror::PointerArray> method_trace =
        ObjPtr<mirror::PointerArray>::DownCast(decoded_traces->Get(0));
    // Prepare parameters for StackTraceElement(String cls, String method, String file, int line)
    ArtMethod* method = method_trace->GetElementPtrSize<ArtMethod*>(i, kRuntimePointerSize);
    uint32_t dex_pc = method_trace->GetElementPtrSize<uint32_t>(
        i + static_cast<uint32_t>(method_trace->GetLength()) / 2, kRuntimePointerSize);
    const ObjPtr<mirror::StackTraceElement> obj = CreateStackTraceElement(soa, method, dex_pc);
    if (obj == nullptr) {
      return nullptr;
    }
    // We are called from native: use non-transactional mode.
    soa.Decode<mirror::ObjectArray<mirror::StackTraceElement>>(result)->Set<false>(
        static_cast<int32_t>(i), obj);
  }
  return result;
}

[[nodiscard]] static ObjPtr<mirror::StackFrameInfo> InitStackFrameInfo(
    const ScopedObjectAccessAlreadyRunnable& soa,
    ClassLinker* class_linker,
    Handle<mirror::StackFrameInfo> stackFrameInfo,
    ArtMethod* method,
    uint32_t dex_pc) REQUIRES_SHARED(Locks::mutator_lock_) {
  StackHandleScope<4> hs(soa.Self());
  int32_t line_number;
  auto source_name_object(hs.NewHandle<mirror::String>(nullptr));
  if (method->IsProxyMethod()) {
    line_number = -1;
    // source_name_object intentionally left null for proxy methods
  } else {
    line_number = method->GetLineNumFromDexPC(dex_pc);
    if (line_number == -1) {
      // Make the line_number field of StackFrameInfo hold the dex pc.
      // source_name_object is intentionally left null if we failed to map the dex pc to
      // a line number (most probably because there is no debug info). See b/30183883.
      line_number = static_cast<int32_t>(dex_pc);
    } else {
      const char* source_file = method->GetDeclaringClassSourceFile();
      if (source_file != nullptr) {
        source_name_object.Assign(mirror::String::AllocFromModifiedUtf8(soa.Self(), source_file));
        if (source_name_object == nullptr) {
          soa.Self()->AssertPendingOOMException();
          return nullptr;
        }
      }
    }
  }

  Handle<mirror::Class> declaring_class_object(
      hs.NewHandle<mirror::Class>(method->GetDeclaringClass()));

  ArtMethod* interface_method = method->GetInterfaceMethodIfProxy(kRuntimePointerSize);
  const char* method_name = interface_method->GetName();
  CHECK(method_name != nullptr);
  Handle<mirror::String> method_name_object(
      hs.NewHandle(mirror::String::AllocFromModifiedUtf8(soa.Self(), method_name)));
  if (method_name_object == nullptr) {
    soa.Self()->AssertPendingOOMException();
    return nullptr;
  }

  dex::ProtoIndex proto_idx =
      method->GetDexFile()->GetIndexForProtoId(interface_method->GetPrototype());
  Handle<mirror::MethodType> method_type_object(hs.NewHandle<mirror::MethodType>(
      class_linker->ResolveMethodType(soa.Self(), proto_idx, interface_method)));
  if (method_type_object == nullptr) {
    soa.Self()->AssertPendingOOMException();
    return nullptr;
  }

  stackFrameInfo->AssignFields(declaring_class_object,
                               method_type_object,
                               method_name_object,
                               source_name_object,
                               line_number,
                               static_cast<int32_t>(dex_pc));
  return stackFrameInfo.Get();
}

constexpr jlong FILL_CLASS_REFS_ONLY = 0x2;  // StackStreamFactory.FILL_CLASS_REFS_ONLY

jint Thread::InternalStackTraceToStackFrameInfoArray(
    const ScopedObjectAccessAlreadyRunnable& soa,
    jlong mode,  // See java.lang.StackStreamFactory for the mode flags
    jobject internal,
    jint startLevel,
    jint batchSize,
    jint startBufferIndex,
    jobjectArray output_array) {
  // Decode the internal stack trace into the depth, method trace and PC trace.
  // Subtract one for the methods and PC trace.
  int32_t depth = soa.Decode<mirror::Array>(internal)->GetLength() - 1;
  DCHECK_GE(depth, 0);

  StackHandleScope<6> hs(soa.Self());
  Handle<mirror::ObjectArray<mirror::Object>> framesOrClasses =
      hs.NewHandle(soa.Decode<mirror::ObjectArray<mirror::Object>>(output_array));

  jint endBufferIndex = startBufferIndex;

  if (startLevel < 0 || startLevel >= depth) {
    return endBufferIndex;
  }

  int32_t bufferSize = framesOrClasses->GetLength();
  if (startBufferIndex < 0 || startBufferIndex >= bufferSize) {
    return endBufferIndex;
  }

  // The FILL_CLASS_REFS_ONLY flag is defined in AbstractStackWalker.fetchStackFrames() javadoc.
  bool isClassArray = (mode & FILL_CLASS_REFS_ONLY) != 0;

  Handle<mirror::ObjectArray<mirror::Object>> decoded_traces =
      hs.NewHandle(soa.Decode<mirror::Object>(internal)->AsObjectArray<mirror::Object>());
  // Methods and dex PC trace is element 0.
  DCHECK(decoded_traces->Get(0)->IsIntArray() || decoded_traces->Get(0)->IsLongArray());
  Handle<mirror::PointerArray> method_trace =
      hs.NewHandle(ObjPtr<mirror::PointerArray>::DownCast(decoded_traces->Get(0)));

  ClassLinker* const class_linker = Runtime::Current()->GetClassLinker();
  Handle<mirror::Class> sfi_class =
      hs.NewHandle(class_linker->FindSystemClass(soa.Self(), "Ljava/lang/StackFrameInfo;"));
  DCHECK(sfi_class != nullptr);

  MutableHandle<mirror::StackFrameInfo> frame = hs.NewHandle<mirror::StackFrameInfo>(nullptr);
  MutableHandle<mirror::Class> clazz = hs.NewHandle<mirror::Class>(nullptr);
  for (uint32_t i = static_cast<uint32_t>(startLevel); i < static_cast<uint32_t>(depth); ++i) {
    if (endBufferIndex >= startBufferIndex + batchSize || endBufferIndex >= bufferSize) {
      break;
    }

    ArtMethod* method = method_trace->GetElementPtrSize<ArtMethod*>(i, kRuntimePointerSize);
    if (isClassArray) {
      clazz.Assign(method->GetDeclaringClass());
      framesOrClasses->Set(endBufferIndex, clazz.Get());
    } else {
      // Prepare parameters for fields in StackFrameInfo
      uint32_t dex_pc = method_trace->GetElementPtrSize<uint32_t>(
          i + static_cast<uint32_t>(method_trace->GetLength()) / 2, kRuntimePointerSize);

      ObjPtr<mirror::Object> frameObject = framesOrClasses->Get(endBufferIndex);
      // If libcore didn't allocate the object, we just stop here, but it's unlikely.
      if (frameObject == nullptr || !frameObject->InstanceOf(sfi_class.Get())) {
        break;
      }
      frame.Assign(ObjPtr<mirror::StackFrameInfo>::DownCast(frameObject));
      frame.Assign(InitStackFrameInfo(soa, class_linker, frame, method, dex_pc));
      // Break if InitStackFrameInfo fails to allocate objects or assign the fields.
      if (frame == nullptr) {
        break;
      }
    }

    ++endBufferIndex;
  }

  return endBufferIndex;
}

jobjectArray Thread::CreateAnnotatedStackTrace(const ScopedObjectAccessAlreadyRunnable& soa) const {
  // This code allocates. Do not allow it to operate with a pending exception.
  if (IsExceptionPending()) {
    return nullptr;
  }

  class CollectFramesAndLocksStackVisitor : public MonitorObjectsStackVisitor {
   public:
    CollectFramesAndLocksStackVisitor(const ScopedObjectAccessAlreadyRunnable& soaa_in,
                                      Thread* self,
                                      Context* context)
        : MonitorObjectsStackVisitor(self, context),
          wait_jobject_(soaa_in.Env(), nullptr),
          block_jobject_(soaa_in.Env(), nullptr),
          soaa_(soaa_in) {}

   protected:
    VisitMethodResult StartMethod(ArtMethod* m, [[maybe_unused]] size_t frame_nr) override
        REQUIRES_SHARED(Locks::mutator_lock_) {
      ObjPtr<mirror::StackTraceElement> obj = CreateStackTraceElement(
          soaa_, m, GetDexPc(/* abort on error */ false));
      if (obj == nullptr) {
        return VisitMethodResult::kEndStackWalk;
      }
      stack_trace_elements_.emplace_back(soaa_.Env(), soaa_.AddLocalReference<jobject>(obj.Ptr()));
      return VisitMethodResult::kContinueMethod;
    }

    VisitMethodResult EndMethod([[maybe_unused]] ArtMethod* m) override {
      lock_objects_.push_back({});
      lock_objects_[lock_objects_.size() - 1].swap(frame_lock_objects_);

      DCHECK_EQ(lock_objects_.size(), stack_trace_elements_.size());

      return VisitMethodResult::kContinueMethod;
    }

    void VisitWaitingObject(ObjPtr<mirror::Object> obj, [[maybe_unused]] ThreadState state) override
        REQUIRES_SHARED(Locks::mutator_lock_) {
      wait_jobject_.reset(soaa_.AddLocalReference<jobject>(obj));
    }
    void VisitSleepingObject(ObjPtr<mirror::Object> obj)
        override
        REQUIRES_SHARED(Locks::mutator_lock_) {
      wait_jobject_.reset(soaa_.AddLocalReference<jobject>(obj));
    }
    void VisitBlockedOnObject(ObjPtr<mirror::Object> obj,
                              [[maybe_unused]] ThreadState state,
                              [[maybe_unused]] uint32_t owner_tid) override
        REQUIRES_SHARED(Locks::mutator_lock_) {
      block_jobject_.reset(soaa_.AddLocalReference<jobject>(obj));
    }
    void VisitLockedObject(ObjPtr<mirror::Object> obj)
        override
        REQUIRES_SHARED(Locks::mutator_lock_) {
      frame_lock_objects_.emplace_back(soaa_.Env(), soaa_.AddLocalReference<jobject>(obj));
    }

   public:
    std::vector<ScopedLocalRef<jobject>> stack_trace_elements_;
    ScopedLocalRef<jobject> wait_jobject_;
    ScopedLocalRef<jobject> block_jobject_;
    std::vector<std::vector<ScopedLocalRef<jobject>>> lock_objects_;

   private:
    const ScopedObjectAccessAlreadyRunnable& soaa_;

    std::vector<ScopedLocalRef<jobject>> frame_lock_objects_;
  };

  std::unique_ptr<Context> context(Context::Create());
  CollectFramesAndLocksStackVisitor dumper(soa, const_cast<Thread*>(this), context.get());
  dumper.WalkStack();

  // There should not be a pending exception. Otherwise, return with it pending.
  if (IsExceptionPending()) {
    return nullptr;
  }

  // Now go and create Java arrays.

  ClassLinker* class_linker = Runtime::Current()->GetClassLinker();

  StackHandleScope<6> hs(soa.Self());
  Handle<mirror::Class> h_aste_array_class = hs.NewHandle(class_linker->FindSystemClass(
      soa.Self(),
      "[Ldalvik/system/AnnotatedStackTraceElement;"));
  if (h_aste_array_class == nullptr) {
    return nullptr;
  }
  Handle<mirror::Class> h_aste_class = hs.NewHandle(h_aste_array_class->GetComponentType());

  Handle<mirror::Class> h_o_array_class =
      hs.NewHandle(GetClassRoot<mirror::ObjectArray<mirror::Object>>(class_linker));
  DCHECK(h_o_array_class != nullptr);  // Class roots must be already initialized.


  // Make sure the AnnotatedStackTraceElement.class is initialized, b/76208924 .
  class_linker->EnsureInitialized(soa.Self(),
                                  h_aste_class,
                                  /* can_init_fields= */ true,
                                  /* can_init_parents= */ true);
  if (soa.Self()->IsExceptionPending()) {
    // This should not fail in a healthy runtime.
    return nullptr;
  }

  ArtField* stack_trace_element_field =
      h_aste_class->FindDeclaredInstanceField("stackTraceElement", "Ljava/lang/StackTraceElement;");
  DCHECK(stack_trace_element_field != nullptr);
  ArtField* held_locks_field =
      h_aste_class->FindDeclaredInstanceField("heldLocks", "[Ljava/lang/Object;");
  DCHECK(held_locks_field != nullptr);
  ArtField* blocked_on_field =
      h_aste_class->FindDeclaredInstanceField("blockedOn", "Ljava/lang/Object;");
  DCHECK(blocked_on_field != nullptr);

  int32_t length = static_cast<int32_t>(dumper.stack_trace_elements_.size());
  ObjPtr<mirror::ObjectArray<mirror::Object>> array =
      mirror::ObjectArray<mirror::Object>::Alloc(soa.Self(), h_aste_array_class.Get(), length);
  if (array == nullptr) {
    soa.Self()->AssertPendingOOMException();
    return nullptr;
  }

  ScopedLocalRef<jobjectArray> result(soa.Env(), soa.Env()->AddLocalReference<jobjectArray>(array));

  MutableHandle<mirror::Object> handle(hs.NewHandle<mirror::Object>(nullptr));
  MutableHandle<mirror::ObjectArray<mirror::Object>> handle2(
      hs.NewHandle<mirror::ObjectArray<mirror::Object>>(nullptr));
  for (size_t i = 0; i != static_cast<size_t>(length); ++i) {
    handle.Assign(h_aste_class->AllocObject(soa.Self()));
    if (handle == nullptr) {
      soa.Self()->AssertPendingOOMException();
      return nullptr;
    }

    // Set stack trace element.
    stack_trace_element_field->SetObject<false>(
        handle.Get(), soa.Decode<mirror::Object>(dumper.stack_trace_elements_[i].get()));

    // Create locked-on array.
    if (!dumper.lock_objects_[i].empty()) {
      handle2.Assign(mirror::ObjectArray<mirror::Object>::Alloc(
          soa.Self(), h_o_array_class.Get(), static_cast<int32_t>(dumper.lock_objects_[i].size())));
      if (handle2 == nullptr) {
        soa.Self()->AssertPendingOOMException();
        return nullptr;
      }
      int32_t j = 0;
      for (auto& scoped_local : dumper.lock_objects_[i]) {
        if (scoped_local == nullptr) {
          continue;
        }
        handle2->Set(j, soa.Decode<mirror::Object>(scoped_local.get()));
        DCHECK(!soa.Self()->IsExceptionPending());
        j++;
      }
      held_locks_field->SetObject<false>(handle.Get(), handle2.Get());
    }

    // Set blocked-on object.
    if (i == 0) {
      if (dumper.block_jobject_ != nullptr) {
        blocked_on_field->SetObject<false>(
            handle.Get(), soa.Decode<mirror::Object>(dumper.block_jobject_.get()));
      }
    }

    ScopedLocalRef<jobject> elem(soa.Env(), soa.AddLocalReference<jobject>(handle.Get()));
    soa.Env()->SetObjectArrayElement(result.get(), static_cast<jsize>(i), elem.get());
    DCHECK(!soa.Self()->IsExceptionPending());
  }

  return result.release();
}

void Thread::ThrowNewExceptionF(const char* exception_class_descriptor, const char* fmt, ...) {
  va_list args;
  va_start(args, fmt);
  ThrowNewExceptionV(exception_class_descriptor, fmt, args);
  va_end(args);
}

void Thread::ThrowNewExceptionV(const char* exception_class_descriptor,
                                const char* fmt, va_list ap) {
  std::string msg;
  StringAppendV(&msg, fmt, ap);
  ThrowNewException(exception_class_descriptor, msg.c_str());
}

void Thread::ThrowNewException(const char* exception_class_descriptor,
                               const char* msg) {
  // Callers should either clear or call ThrowNewWrappedException.
  AssertNoPendingExceptionForNewException(msg);
  ThrowNewWrappedException(exception_class_descriptor, msg);
}

static ObjPtr<mirror::ClassLoader> GetCurrentClassLoader(Thread* self)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  ArtMethod* method = self->GetCurrentMethod(nullptr);
  return method != nullptr
      ? method->GetDeclaringClass()->GetClassLoader()
      : nullptr;
}

void Thread::ThrowNewWrappedException(const char* exception_class_descriptor,
                                      const char* msg) {
  DCHECK_EQ(this, Thread::Current());
  ScopedObjectAccessUnchecked soa(this);
  StackHandleScope<3> hs(soa.Self());

  // Disable public sdk checks if we need to throw exceptions.
  // The checks are only used in AOT compilation and may block (exception) class
  // initialization if it needs access to private fields (e.g. serialVersionUID).
  //
  // Since throwing an exception will EnsureInitialization and the public sdk may
  // block that, disable the checks. It's ok to do so, because the thrown exceptions
  // are not part of the application code that needs to verified.
  ScopedDisablePublicSdkChecker sdpsc;

  Handle<mirror::ClassLoader> class_loader(hs.NewHandle(GetCurrentClassLoader(soa.Self())));
  ScopedLocalRef<jobject> cause(GetJniEnv(), soa.AddLocalReference<jobject>(GetException()));
  ClearException();
  Runtime* runtime = Runtime::Current();
  auto* cl = runtime->GetClassLinker();
  Handle<mirror::Class> exception_class(
      hs.NewHandle(cl->FindClass(this, exception_class_descriptor, class_loader)));
  if (UNLIKELY(exception_class == nullptr)) {
    CHECK(IsExceptionPending());
    LOG(ERROR) << "No exception class " << PrettyDescriptor(exception_class_descriptor);
    return;
  }

  if (UNLIKELY(!runtime->GetClassLinker()->EnsureInitialized(soa.Self(), exception_class, true,
                                                             true))) {
    DCHECK(IsExceptionPending());
    return;
  }
  DCHECK_IMPLIES(runtime->IsStarted(), exception_class->IsThrowableClass());
  Handle<mirror::Throwable> exception(
      hs.NewHandle(ObjPtr<mirror::Throwable>::DownCast(exception_class->AllocObject(this))));

  // If we couldn't allocate the exception, throw the pre-allocated out of memory exception.
  if (exception == nullptr) {
    Dump(LOG_STREAM(WARNING));  // The pre-allocated OOME has no stack, so help out and log one.
    SetException(Runtime::Current()->GetPreAllocatedOutOfMemoryErrorWhenThrowingException());
    return;
  }

  // Choose an appropriate constructor and set up the arguments.
  const char* signature;
  ScopedLocalRef<jstring> msg_string(GetJniEnv(), nullptr);
  if (msg != nullptr) {
    // Ensure we remember this and the method over the String allocation.
    msg_string.reset(
        soa.AddLocalReference<jstring>(mirror::String::AllocFromModifiedUtf8(this, msg)));
    if (UNLIKELY(msg_string.get() == nullptr)) {
      CHECK(IsExceptionPending());  // OOME.
      return;
    }
    if (cause.get() == nullptr) {
      signature = "(Ljava/lang/String;)V";
    } else {
      signature = "(Ljava/lang/String;Ljava/lang/Throwable;)V";
    }
  } else {
    if (cause.get() == nullptr) {
      signature = "()V";
    } else {
      signature = "(Ljava/lang/Throwable;)V";
    }
  }
  ArtMethod* exception_init_method =
      exception_class->FindConstructor(signature, cl->GetImagePointerSize());

  CHECK(exception_init_method != nullptr) << "No <init>" << signature << " in "
      << PrettyDescriptor(exception_class_descriptor);

  if (UNLIKELY(!runtime->IsStarted())) {
    // Something is trying to throw an exception without a started runtime, which is the common
    // case in the compiler. We won't be able to invoke the constructor of the exception, so set
    // the exception fields directly.
    if (msg != nullptr) {
      exception->SetDetailMessage(DecodeJObject(msg_string.get())->AsString());
    }
    if (cause.get() != nullptr) {
      exception->SetCause(DecodeJObject(cause.get())->AsThrowable());
    }
    ScopedLocalRef<jobject> trace(GetJniEnv(), CreateInternalStackTrace(soa));
    if (trace.get() != nullptr) {
      exception->SetStackState(DecodeJObject(trace.get()).Ptr());
    }
    SetException(exception.Get());
  } else {
    jvalue jv_args[2];
    size_t i = 0;

    if (msg != nullptr) {
      jv_args[i].l = msg_string.get();
      ++i;
    }
    if (cause.get() != nullptr) {
      jv_args[i].l = cause.get();
      ++i;
    }
    ScopedLocalRef<jobject> ref(soa.Env(), soa.AddLocalReference<jobject>(exception.Get()));
    InvokeWithJValues(soa, ref.get(), exception_init_method, jv_args);
    if (LIKELY(!IsExceptionPending())) {
      SetException(exception.Get());
    }
  }
}

void Thread::ThrowOutOfMemoryError(const char* msg) {
  LOG(WARNING) << "Throwing OutOfMemoryError "
               << '"' << msg << '"'
               << " (VmSize " << GetProcessStatus("VmSize")
               << (tls32_.throwing_OutOfMemoryError ? ", recursive case)" : ")");
  ScopedTrace trace("OutOfMemoryError");
  if (!tls32_.throwing_OutOfMemoryError) {
    tls32_.throwing_OutOfMemoryError = true;
    ThrowNewException("Ljava/lang/OutOfMemoryError;", msg);
    tls32_.throwing_OutOfMemoryError = false;
  } else {
    Dump(LOG_STREAM(WARNING));  // The pre-allocated OOME has no stack, so help out and log one.
    SetException(Runtime::Current()->GetPreAllocatedOutOfMemoryErrorWhenThrowingOOME());
  }
}

Thread* Thread::CurrentFromGdb() {
  return Thread::Current();
}

void Thread::DumpFromGdb() const {
  std::ostringstream ss;
  Dump(ss);
  std::string str(ss.str());
  // log to stderr for debugging command line processes
  std::cerr << str;
#ifdef ART_TARGET_ANDROID
  // log to logcat for debugging frameworks processes
  LOG(INFO) << str;
#endif
}

// Explicitly instantiate 32 and 64bit thread offset dumping support.
template
void Thread::DumpThreadOffset<PointerSize::k32>(std::ostream& os, uint32_t offset);
template
void Thread::DumpThreadOffset<PointerSize::k64>(std::ostream& os, uint32_t offset);

template<PointerSize ptr_size>
void Thread::DumpThreadOffset(std::ostream& os, uint32_t offset) {
#define DO_THREAD_OFFSET(x, y) \
    if (offset == (x).Uint32Value()) { \
      os << (y); \
      return; \
    }
  DO_THREAD_OFFSET(ThreadFlagsOffset<ptr_size>(), "state_and_flags")
  DO_THREAD_OFFSET(CardTableOffset<ptr_size>(), "card_table")
  DO_THREAD_OFFSET(ExceptionOffset<ptr_size>(), "exception")
  DO_THREAD_OFFSET(PeerOffset<ptr_size>(), "peer");
  DO_THREAD_OFFSET(JniEnvOffset<ptr_size>(), "jni_env")
  DO_THREAD_OFFSET(SelfOffset<ptr_size>(), "self")
  DO_THREAD_OFFSET(StackEndOffset<ptr_size>(), "stack_end")
  DO_THREAD_OFFSET(ThinLockIdOffset<ptr_size>(), "thin_lock_thread_id")
  DO_THREAD_OFFSET(IsGcMarkingOffset<ptr_size>(), "is_gc_marking")
  DO_THREAD_OFFSET(TopOfManagedStackOffset<ptr_size>(), "top_quick_frame_method")
  DO_THREAD_OFFSET(TopShadowFrameOffset<ptr_size>(), "top_shadow_frame")
  DO_THREAD_OFFSET(TopHandleScopeOffset<ptr_size>(), "top_handle_scope")
  DO_THREAD_OFFSET(ThreadSuspendTriggerOffset<ptr_size>(), "suspend_trigger")
#undef DO_THREAD_OFFSET

#define JNI_ENTRY_POINT_INFO(x) \
    if (JNI_ENTRYPOINT_OFFSET(ptr_size, x).Uint32Value() == offset) { \
      os << #x; \
      return; \
    }
  JNI_ENTRY_POINT_INFO(pDlsymLookup)
  JNI_ENTRY_POINT_INFO(pDlsymLookupCritical)
#undef JNI_ENTRY_POINT_INFO

#define QUICK_ENTRY_POINT_INFO(x) \
    if (QUICK_ENTRYPOINT_OFFSET(ptr_size, x).Uint32Value() == offset) { \
      os << #x; \
      return; \
    }
  QUICK_ENTRY_POINT_INFO(pAllocArrayResolved)
  QUICK_ENTRY_POINT_INFO(pAllocArrayResolved8)
  QUICK_ENTRY_POINT_INFO(pAllocArrayResolved16)
  QUICK_ENTRY_POINT_INFO(pAllocArrayResolved32)
  QUICK_ENTRY_POINT_INFO(pAllocArrayResolved64)
  QUICK_ENTRY_POINT_INFO(pAllocObjectResolved)
  QUICK_ENTRY_POINT_INFO(pAllocObjectInitialized)
  QUICK_ENTRY_POINT_INFO(pAllocObjectWithChecks)
  QUICK_ENTRY_POINT_INFO(pAllocStringObject)
  QUICK_ENTRY_POINT_INFO(pAllocStringFromBytes)
  QUICK_ENTRY_POINT_INFO(pAllocStringFromChars)
  QUICK_ENTRY_POINT_INFO(pAllocStringFromString)
  QUICK_ENTRY_POINT_INFO(pInstanceofNonTrivial)
  QUICK_ENTRY_POINT_INFO(pCheckInstanceOf)
  QUICK_ENTRY_POINT_INFO(pInitializeStaticStorage)
  QUICK_ENTRY_POINT_INFO(pResolveTypeAndVerifyAccess)
  QUICK_ENTRY_POINT_INFO(pResolveType)
  QUICK_ENTRY_POINT_INFO(pResolveString)
  QUICK_ENTRY_POINT_INFO(pSet8Instance)
  QUICK_ENTRY_POINT_INFO(pSet8Static)
  QUICK_ENTRY_POINT_INFO(pSet16Instance)
  QUICK_ENTRY_POINT_INFO(pSet16Static)
  QUICK_ENTRY_POINT_INFO(pSet32Instance)
  QUICK_ENTRY_POINT_INFO(pSet32Static)
  QUICK_ENTRY_POINT_INFO(pSet64Instance)
  QUICK_ENTRY_POINT_INFO(pSet64Static)
  QUICK_ENTRY_POINT_INFO(pSetObjInstance)
  QUICK_ENTRY_POINT_INFO(pSetObjStatic)
  QUICK_ENTRY_POINT_INFO(pGetByteInstance)
  QUICK_ENTRY_POINT_INFO(pGetBooleanInstance)
  QUICK_ENTRY_POINT_INFO(pGetByteStatic)
  QUICK_ENTRY_POINT_INFO(pGetBooleanStatic)
  QUICK_ENTRY_POINT_INFO(pGetShortInstance)
  QUICK_ENTRY_POINT_INFO(pGetCharInstance)
  QUICK_ENTRY_POINT_INFO(pGetShortStatic)
  QUICK_ENTRY_POINT_INFO(pGetCharStatic)
  QUICK_ENTRY_POINT_INFO(pGet32Instance)
  QUICK_ENTRY_POINT_INFO(pGet32Static)
  QUICK_ENTRY_POINT_INFO(pGet64Instance)
  QUICK_ENTRY_POINT_INFO(pGet64Static)
  QUICK_ENTRY_POINT_INFO(pGetObjInstance)
  QUICK_ENTRY_POINT_INFO(pGetObjStatic)
  QUICK_ENTRY_POINT_INFO(pAputObject)
  QUICK_ENTRY_POINT_INFO(pJniMethodStart)
  QUICK_ENTRY_POINT_INFO(pJniMethodEnd)
  QUICK_ENTRY_POINT_INFO(pJniMethodEntryHook)
  QUICK_ENTRY_POINT_INFO(pJniDecodeReferenceResult)
  QUICK_ENTRY_POINT_INFO(pJniLockObject)
  QUICK_ENTRY_POINT_INFO(pJniUnlockObject)
  QUICK_ENTRY_POINT_INFO(pQuickGenericJniTrampoline)
  QUICK_ENTRY_POINT_INFO(pLockObject)
  QUICK_ENTRY_POINT_INFO(pUnlockObject)
  QUICK_ENTRY_POINT_INFO(pCmpgDouble)
  QUICK_ENTRY_POINT_INFO(pCmpgFloat)
  QUICK_ENTRY_POINT_INFO(pCmplDouble)
  QUICK_ENTRY_POINT_INFO(pCmplFloat)
  QUICK_ENTRY_POINT_INFO(pCos)
  QUICK_ENTRY_POINT_INFO(pSin)
  QUICK_ENTRY_POINT_INFO(pAcos)
  QUICK_ENTRY_POINT_INFO(pAsin)
  QUICK_ENTRY_POINT_INFO(pAtan)
  QUICK_ENTRY_POINT_INFO(pAtan2)
  QUICK_ENTRY_POINT_INFO(pCbrt)
  QUICK_ENTRY_POINT_INFO(pCosh)
  QUICK_ENTRY_POINT_INFO(pExp)
  QUICK_ENTRY_POINT_INFO(pExpm1)
  QUICK_ENTRY_POINT_INFO(pHypot)
  QUICK_ENTRY_POINT_INFO(pLog)
  QUICK_ENTRY_POINT_INFO(pLog10)
  QUICK_ENTRY_POINT_INFO(pNextAfter)
  QUICK_ENTRY_POINT_INFO(pSinh)
  QUICK_ENTRY_POINT_INFO(pTan)
  QUICK_ENTRY_POINT_INFO(pTanh)
  QUICK_ENTRY_POINT_INFO(pFmod)
  QUICK_ENTRY_POINT_INFO(pL2d)
  QUICK_ENTRY_POINT_INFO(pFmodf)
  QUICK_ENTRY_POINT_INFO(pL2f)
  QUICK_ENTRY_POINT_INFO(pD2iz)
  QUICK_ENTRY_POINT_INFO(pF2iz)
  QUICK_ENTRY_POINT_INFO(pIdivmod)
  QUICK_ENTRY_POINT_INFO(pD2l)
  QUICK_ENTRY_POINT_INFO(pF2l)
  QUICK_ENTRY_POINT_INFO(pLdiv)
  QUICK_ENTRY_POINT_INFO(pLmod)
  QUICK_ENTRY_POINT_INFO(pLmul)
  QUICK_ENTRY_POINT_INFO(pShlLong)
  QUICK_ENTRY_POINT_INFO(pShrLong)
  QUICK_ENTRY_POINT_INFO(pUshrLong)
  QUICK_ENTRY_POINT_INFO(pIndexOf)
  QUICK_ENTRY_POINT_INFO(pStringCompareTo)
  QUICK_ENTRY_POINT_INFO(pMemcpy)
  QUICK_ENTRY_POINT_INFO(pQuickImtConflictTrampoline)
  QUICK_ENTRY_POINT_INFO(pQuickResolutionTrampoline)
  QUICK_ENTRY_POINT_INFO(pQuickToInterpreterBridge)
  QUICK_ENTRY_POINT_INFO(pInvokeDirectTrampolineWithAccessCheck)
  QUICK_ENTRY_POINT_INFO(pInvokeInterfaceTrampolineWithAccessCheck)
  QUICK_ENTRY_POINT_INFO(pInvokeStaticTrampolineWithAccessCheck)
  QUICK_ENTRY_POINT_INFO(pInvokeSuperTrampolineWithAccessCheck)
  QUICK_ENTRY_POINT_INFO(pInvokeVirtualTrampolineWithAccessCheck)
  QUICK_ENTRY_POINT_INFO(pInvokePolymorphic)
  QUICK_ENTRY_POINT_INFO(pTestSuspend)
  QUICK_ENTRY_POINT_INFO(pDeliverException)
  QUICK_ENTRY_POINT_INFO(pThrowArrayBounds)
  QUICK_ENTRY_POINT_INFO(pThrowDivZero)
  QUICK_ENTRY_POINT_INFO(pThrowNullPointer)
  QUICK_ENTRY_POINT_INFO(pThrowStackOverflow)
  QUICK_ENTRY_POINT_INFO(pDeoptimize)
  QUICK_ENTRY_POINT_INFO(pA64Load)
  QUICK_ENTRY_POINT_INFO(pA64Store)
  QUICK_ENTRY_POINT_INFO(pNewEmptyString)
  QUICK_ENTRY_POINT_INFO(pNewStringFromBytes_B)
  QUICK_ENTRY_POINT_INFO(pNewStringFromBytes_BB)
  QUICK_ENTRY_POINT_INFO(pNewStringFromBytes_BI)
  QUICK_ENTRY_POINT_INFO(pNewStringFromBytes_BII)
  QUICK_ENTRY_POINT_INFO(pNewStringFromBytes_BIII)
  QUICK_ENTRY_POINT_INFO(pNewStringFromBytes_BIIString)
  QUICK_ENTRY_POINT_INFO(pNewStringFromBytes_BString)
  QUICK_ENTRY_POINT_INFO(pNewStringFromBytes_BIICharset)
  QUICK_ENTRY_POINT_INFO(pNewStringFromBytes_BCharset)
  QUICK_ENTRY_POINT_INFO(pNewStringFromChars_C)
  QUICK_ENTRY_POINT_INFO(pNewStringFromChars_CII)
  QUICK_ENTRY_POINT_INFO(pNewStringFromChars_IIC)
  QUICK_ENTRY_POINT_INFO(pNewStringFromCodePoints)
  QUICK_ENTRY_POINT_INFO(pNewStringFromString)
  QUICK_ENTRY_POINT_INFO(pNewStringFromStringBuffer)
  QUICK_ENTRY_POINT_INFO(pNewStringFromStringBuilder)
  QUICK_ENTRY_POINT_INFO(pNewStringFromUtf16Bytes_BII)
  QUICK_ENTRY_POINT_INFO(pJniReadBarrier)
  QUICK_ENTRY_POINT_INFO(pReadBarrierMarkReg00)
  QUICK_ENTRY_POINT_INFO(pReadBarrierMarkReg01)
  QUICK_ENTRY_POINT_INFO(pReadBarrierMarkReg02)
  QUICK_ENTRY_POINT_INFO(pReadBarrierMarkReg03)
  QUICK_ENTRY_POINT_INFO(pReadBarrierMarkReg04)
  QUICK_ENTRY_POINT_INFO(pReadBarrierMarkReg05)
  QUICK_ENTRY_POINT_INFO(pReadBarrierMarkReg06)
  QUICK_ENTRY_POINT_INFO(pReadBarrierMarkReg07)
  QUICK_ENTRY_POINT_INFO(pReadBarrierMarkReg08)
  QUICK_ENTRY_POINT_INFO(pReadBarrierMarkReg09)
  QUICK_ENTRY_POINT_INFO(pReadBarrierMarkReg10)
  QUICK_ENTRY_POINT_INFO(pReadBarrierMarkReg11)
  QUICK_ENTRY_POINT_INFO(pReadBarrierMarkReg12)
  QUICK_ENTRY_POINT_INFO(pReadBarrierMarkReg13)
  QUICK_ENTRY_POINT_INFO(pReadBarrierMarkReg14)
  QUICK_ENTRY_POINT_INFO(pReadBarrierMarkReg15)
  QUICK_ENTRY_POINT_INFO(pReadBarrierMarkReg16)
  QUICK_ENTRY_POINT_INFO(pReadBarrierMarkReg17)
  QUICK_ENTRY_POINT_INFO(pReadBarrierMarkReg18)
  QUICK_ENTRY_POINT_INFO(pReadBarrierMarkReg19)
  QUICK_ENTRY_POINT_INFO(pReadBarrierMarkReg20)
  QUICK_ENTRY_POINT_INFO(pReadBarrierMarkReg21)
  QUICK_ENTRY_POINT_INFO(pReadBarrierMarkReg22)
  QUICK_ENTRY_POINT_INFO(pReadBarrierMarkReg23)
  QUICK_ENTRY_POINT_INFO(pReadBarrierMarkReg24)
  QUICK_ENTRY_POINT_INFO(pReadBarrierMarkReg25)
  QUICK_ENTRY_POINT_INFO(pReadBarrierMarkReg26)
  QUICK_ENTRY_POINT_INFO(pReadBarrierMarkReg27)
  QUICK_ENTRY_POINT_INFO(pReadBarrierMarkReg28)
  QUICK_ENTRY_POINT_INFO(pReadBarrierMarkReg29)
  QUICK_ENTRY_POINT_INFO(pReadBarrierSlow)
  QUICK_ENTRY_POINT_INFO(pReadBarrierForRootSlow)
#undef QUICK_ENTRY_POINT_INFO

  os << offset;
}

void Thread::QuickDeliverException(bool skip_method_exit_callbacks) {
  // Get exception from thread.
  ObjPtr<mirror::Throwable> exception = GetException();
  CHECK(exception != nullptr);
  if (exception == GetDeoptimizationException()) {
    // This wasn't a real exception, so just clear it here. If there was an actual exception it
    // will be recorded in the DeoptimizationContext and it will be restored later.
    ClearException();
    artDeoptimize(this, skip_method_exit_callbacks);
    UNREACHABLE();
  }

  ReadBarrier::MaybeAssertToSpaceInvariant(exception.Ptr());

  // This is a real exception: let the instrumentation know about it. Exception throw listener
  // could set a breakpoint or install listeners that might require a deoptimization. Hence the
  // deoptimization check needs to happen after calling the listener.
  instrumentation::Instrumentation* instrumentation = Runtime::Current()->GetInstrumentation();
  if (instrumentation->HasExceptionThrownListeners() &&
      IsExceptionThrownByCurrentMethod(exception)) {
    // Instrumentation may cause GC so keep the exception object safe.
    StackHandleScope<1> hs(this);
    HandleWrapperObjPtr<mirror::Throwable> h_exception(hs.NewHandleWrapper(&exception));
    instrumentation->ExceptionThrownEvent(this, exception);
  }
  // Does instrumentation need to deoptimize the stack or otherwise go to interpreter for something?
  // Note: we do this *after* reporting the exception to instrumentation in case it now requires
  // deoptimization. It may happen if a debugger is attached and requests new events (single-step,
  // breakpoint, ...) when the exception is reported.
  // Frame pop can be requested on a method unwind callback which requires a deopt. We could
  // potentially check after each unwind callback to see if a frame pop was requested and deopt if
  // needed. Since this is a debug only feature and this path is only taken when an exception is
  // thrown, it is not performance critical and we keep it simple by just deopting if method exit
  // listeners are installed and frame pop feature is supported.
  bool needs_deopt =
      instrumentation->HasMethodExitListeners() && Runtime::Current()->AreNonStandardExitsEnabled();
  if (Dbg::IsForcedInterpreterNeededForException(this) || IsForceInterpreter() || needs_deopt) {
    NthCallerVisitor visitor(this, 0, false);
    visitor.WalkStack();
    if (visitor.GetCurrentQuickFrame() != nullptr) {
      if (Runtime::Current()->IsAsyncDeoptimizeable(visitor.GetOuterMethod(), visitor.caller_pc)) {
        // method_type shouldn't matter due to exception handling.
        const DeoptimizationMethodType method_type = DeoptimizationMethodType::kDefault;
        // Save the exception into the deoptimization context so it can be restored
        // before entering the interpreter.
        PushDeoptimizationContext(
            JValue(),
            /* is_reference= */ false,
            exception,
            /* from_code= */ false,
            method_type);
        artDeoptimize(this, skip_method_exit_callbacks);
        UNREACHABLE();
      } else {
        LOG(WARNING) << "Got a deoptimization request on un-deoptimizable method "
                     << visitor.caller->PrettyMethod();
      }
    } else {
      // This is either top of call stack, or shadow frame.
      DCHECK(visitor.caller == nullptr || visitor.IsShadowFrame());
    }
  }

  // Don't leave exception visible while we try to find the handler, which may cause class
  // resolution.
  ClearException();
  QuickExceptionHandler exception_handler(this, false);
  exception_handler.FindCatch(exception, skip_method_exit_callbacks);
  if (exception_handler.GetClearException()) {
    // Exception was cleared as part of delivery.
    DCHECK(!IsExceptionPending());
  } else {
    // Exception was put back with a throw location.
    DCHECK(IsExceptionPending());
    // Check the to-space invariant on the re-installed exception (if applicable).
    ReadBarrier::MaybeAssertToSpaceInvariant(GetException());
  }
  exception_handler.DoLongJump();
}

Context* Thread::GetLongJumpContext() {
  Context* result = tlsPtr_.long_jump_context;
  if (result == nullptr) {
    result = Context::Create();
  } else {
    tlsPtr_.long_jump_context = nullptr;  // Avoid context being shared.
    result->Reset();
  }
  return result;
}

ArtMethod* Thread::GetCurrentMethod(uint32_t* dex_pc_out,
                                    bool check_suspended,
                                    bool abort_on_error) const {
  // Note: this visitor may return with a method set, but dex_pc_ being DexFile:kDexNoIndex. This is
  //       so we don't abort in a special situation (thinlocked monitor) when dumping the Java
  //       stack.
  ArtMethod* method = nullptr;
  uint32_t dex_pc = dex::kDexNoIndex;
  StackVisitor::WalkStack(
      [&](const StackVisitor* visitor) REQUIRES_SHARED(Locks::mutator_lock_) {
        ArtMethod* m = visitor->GetMethod();
        if (m->IsRuntimeMethod()) {
          // Continue if this is a runtime method.
          return true;
        }
        method = m;
        dex_pc = visitor->GetDexPc(abort_on_error);
        return false;
      },
      const_cast<Thread*>(this),
      /* context= */ nullptr,
      StackVisitor::StackWalkKind::kIncludeInlinedFrames,
      check_suspended);

  if (dex_pc_out != nullptr) {
    *dex_pc_out = dex_pc;
  }
  return method;
}

bool Thread::HoldsLock(ObjPtr<mirror::Object> object) const {
  return object != nullptr && object->GetLockOwnerThreadId() == GetThreadId();
}

extern std::vector<StackReference<mirror::Object>*> GetProxyReferenceArguments(ArtMethod** sp)
    REQUIRES_SHARED(Locks::mutator_lock_);

// RootVisitor parameters are: (const Object* obj, size_t vreg, const StackVisitor* visitor).
template <typename RootVisitor, bool kPrecise = false>
class ReferenceMapVisitor : public StackVisitor {
 public:
  ReferenceMapVisitor(Thread* thread, Context* context, RootVisitor& visitor)
      REQUIRES_SHARED(Locks::mutator_lock_)
      // We are visiting the references in compiled frames, so we do not need
      // to know the inlined frames.
      : StackVisitor(thread, context, StackVisitor::StackWalkKind::kSkipInlinedFrames),
        visitor_(visitor),
        visit_declaring_class_(!Runtime::Current()->GetHeap()->IsPerformingUffdCompaction()) {}

  bool VisitFrame() override REQUIRES_SHARED(Locks::mutator_lock_) {
    if (false) {
      LOG(INFO) << "Visiting stack roots in " << ArtMethod::PrettyMethod(GetMethod())
                << StringPrintf("@ PC:%04x", GetDexPc());
    }
    ShadowFrame* shadow_frame = GetCurrentShadowFrame();
    if (shadow_frame != nullptr) {
      VisitShadowFrame(shadow_frame);
    } else if (GetCurrentOatQuickMethodHeader()->IsNterpMethodHeader()) {
      VisitNterpFrame();
    } else {
      VisitQuickFrame();
    }
    return true;
  }

  void VisitShadowFrame(ShadowFrame* shadow_frame) REQUIRES_SHARED(Locks::mutator_lock_) {
    ArtMethod* m = shadow_frame->GetMethod();
    VisitDeclaringClass(m);
    DCHECK(m != nullptr);
    size_t num_regs = shadow_frame->NumberOfVRegs();
    // handle scope for JNI or References for interpreter.
    for (size_t reg = 0; reg < num_regs; ++reg) {
      mirror::Object* ref = shadow_frame->GetVRegReference(reg);
      if (ref != nullptr) {
        mirror::Object* new_ref = ref;
        visitor_(&new_ref, reg, this);
        if (new_ref != ref) {
          shadow_frame->SetVRegReference(reg, new_ref);
        }
      }
    }
    // Mark lock count map required for structured locking checks.
    shadow_frame->GetLockCountData().VisitMonitors(visitor_, /* vreg= */ -1, this);
  }

 private:
  // Visiting the declaring class is necessary so that we don't unload the class of a method that
  // is executing. We need to ensure that the code stays mapped. NO_THREAD_SAFETY_ANALYSIS since
  // the threads do not all hold the heap bitmap lock for parallel GC.
  void VisitDeclaringClass(ArtMethod* method)
      REQUIRES_SHARED(Locks::mutator_lock_)
      NO_THREAD_SAFETY_ANALYSIS {
    if (!visit_declaring_class_) {
      return;
    }
    ObjPtr<mirror::Class> klass = method->GetDeclaringClassUnchecked<kWithoutReadBarrier>();
    // klass can be null for runtime methods.
    if (klass != nullptr) {
      if (kVerifyImageObjectsMarked) {
        gc::Heap* const heap = Runtime::Current()->GetHeap();
        gc::space::ContinuousSpace* space = heap->FindContinuousSpaceFromObject(klass,
                                                                                /*fail_ok=*/true);
        if (space != nullptr && space->IsImageSpace()) {
          bool failed = false;
          if (!space->GetLiveBitmap()->Test(klass.Ptr())) {
            failed = true;
            LOG(FATAL_WITHOUT_ABORT) << "Unmarked object in image " << *space;
          } else if (!heap->GetLiveBitmap()->Test(klass.Ptr())) {
            failed = true;
            LOG(FATAL_WITHOUT_ABORT) << "Unmarked object in image through live bitmap " << *space;
          }
          if (failed) {
            GetThread()->Dump(LOG_STREAM(FATAL_WITHOUT_ABORT));
            space->AsImageSpace()->DumpSections(LOG_STREAM(FATAL_WITHOUT_ABORT));
            LOG(FATAL_WITHOUT_ABORT) << "Method@" << method->GetDexMethodIndex() << ":" << method
                                     << " klass@" << klass.Ptr();
            // Pretty info last in case it crashes.
            LOG(FATAL) << "Method " << method->PrettyMethod() << " klass "
                       << klass->PrettyClass();
          }
        }
      }
      mirror::Object* new_ref = klass.Ptr();
      visitor_(&new_ref, /* vreg= */ JavaFrameRootInfo::kMethodDeclaringClass, this);
      if (new_ref != klass) {
        method->CASDeclaringClass(klass.Ptr(), new_ref->AsClass());
      }
    }
  }

  void VisitNterpFrame() REQUIRES_SHARED(Locks::mutator_lock_) {
    ArtMethod** cur_quick_frame = GetCurrentQuickFrame();
    StackReference<mirror::Object>* vreg_ref_base =
        reinterpret_cast<StackReference<mirror::Object>*>(NterpGetReferenceArray(cur_quick_frame));
    StackReference<mirror::Object>* vreg_int_base =
        reinterpret_cast<StackReference<mirror::Object>*>(NterpGetRegistersArray(cur_quick_frame));
    CodeItemDataAccessor accessor((*cur_quick_frame)->DexInstructionData());
    const uint16_t num_regs = accessor.RegistersSize();
    // An nterp frame has two arrays: a dex register array and a reference array
    // that shadows the dex register array but only containing references
    // (non-reference dex registers have nulls). See nterp_helpers.cc.
    for (size_t reg = 0; reg < num_regs; ++reg) {
      StackReference<mirror::Object>* ref_addr = vreg_ref_base + reg;
      mirror::Object* ref = ref_addr->AsMirrorPtr();
      if (ref != nullptr) {
        mirror::Object* new_ref = ref;
        visitor_(&new_ref, reg, this);
        if (new_ref != ref) {
          ref_addr->Assign(new_ref);
          StackReference<mirror::Object>* int_addr = vreg_int_base + reg;
          int_addr->Assign(new_ref);
        }
      }
    }
  }

  template <typename T>
  ALWAYS_INLINE
  inline void VisitQuickFrameWithVregCallback() REQUIRES_SHARED(Locks::mutator_lock_) {
    ArtMethod** cur_quick_frame = GetCurrentQuickFrame();
    DCHECK(cur_quick_frame != nullptr);
    ArtMethod* m = *cur_quick_frame;
    VisitDeclaringClass(m);

    if (m->IsNative()) {
      // TODO: Spill the `this` reference in the AOT-compiled String.charAt()
      // slow-path for throwing SIOOBE, so that we can remove this carve-out.
      if (UNLIKELY(m->IsIntrinsic()) &&
          m->GetIntrinsic() == enum_cast<uint32_t>(Intrinsics::kStringCharAt)) {
        // The String.charAt() method is AOT-compiled with an intrinsic implementation
        // instead of a JNI stub. It has a slow path that constructs a runtime frame
        // for throwing SIOOBE and in that path we do not get the `this` pointer
        // spilled on the stack, so there is nothing to visit. We can distinguish
        // this from the GenericJni path by checking that the PC is in the boot image
        // (PC shall be known thanks to the runtime frame for throwing SIOOBE).
        // Note that JIT does not emit that intrinic implementation.
        const void* pc = reinterpret_cast<const void*>(GetCurrentQuickFramePc());
        if (pc != nullptr && Runtime::Current()->GetHeap()->IsInBootImageOatFile(pc)) {
          return;
        }
      }
      // Native methods spill their arguments to the reserved vregs in the caller's frame
      // and use pointers to these stack references as jobject, jclass, jarray, etc.
      // Note: We can come here for a @CriticalNative method when it needs to resolve the
      // target native function but there would be no references to visit below.
      const size_t frame_size = GetCurrentQuickFrameInfo().FrameSizeInBytes();
      const size_t method_pointer_size = static_cast<size_t>(kRuntimePointerSize);
      uint32_t* current_vreg = reinterpret_cast<uint32_t*>(
          reinterpret_cast<uint8_t*>(cur_quick_frame) + frame_size + method_pointer_size);
      auto visit = [&]() REQUIRES_SHARED(Locks::mutator_lock_) {
        auto* ref_addr = reinterpret_cast<StackReference<mirror::Object>*>(current_vreg);
        mirror::Object* ref = ref_addr->AsMirrorPtr();
        if (ref != nullptr) {
          mirror::Object* new_ref = ref;
          visitor_(&new_ref, /* vreg= */ JavaFrameRootInfo::kNativeReferenceArgument, this);
          if (ref != new_ref) {
            ref_addr->Assign(new_ref);
          }
        }
      };
      const char* shorty = m->GetShorty();
      if (!m->IsStatic()) {
        visit();
        current_vreg += 1u;
      }
      for (shorty += 1u; *shorty != 0; ++shorty) {
        switch (*shorty) {
          case 'D':
          case 'J':
            current_vreg += 2u;
            break;
          case 'L':
            visit();
            FALLTHROUGH_INTENDED;
          default:
            current_vreg += 1u;
            break;
        }
      }
    } else if (!m->IsRuntimeMethod() && (!m->IsProxyMethod() || m->IsConstructor())) {
      // Process register map (which native, runtime and proxy methods don't have)
      const OatQuickMethodHeader* method_header = GetCurrentOatQuickMethodHeader();
      DCHECK(method_header->IsOptimized());
      StackReference<mirror::Object>* vreg_base =
          reinterpret_cast<StackReference<mirror::Object>*>(cur_quick_frame);
      uintptr_t native_pc_offset = method_header->NativeQuickPcOffset(GetCurrentQuickFramePc());
      CodeInfo code_info = kPrecise
          ? CodeInfo(method_header)  // We will need dex register maps.
          : CodeInfo::DecodeGcMasksOnly(method_header);
      StackMap map = code_info.GetStackMapForNativePcOffset(native_pc_offset);
      DCHECK(map.IsValid());

      T vreg_info(m, code_info, map, visitor_);

      // Visit stack entries that hold pointers.
      BitMemoryRegion stack_mask = code_info.GetStackMaskOf(map);
      for (size_t i = 0; i < stack_mask.size_in_bits(); ++i) {
        if (stack_mask.LoadBit(i)) {
          StackReference<mirror::Object>* ref_addr = vreg_base + i;
          mirror::Object* ref = ref_addr->AsMirrorPtr();
          if (ref != nullptr) {
            mirror::Object* new_ref = ref;
            vreg_info.VisitStack(&new_ref, i, this);
            if (ref != new_ref) {
              ref_addr->Assign(new_ref);
            }
          }
        }
      }
      // Visit callee-save registers that hold pointers.
      uint32_t register_mask = code_info.GetRegisterMaskOf(map);
      for (uint32_t i = 0; i < BitSizeOf<uint32_t>(); ++i) {
        if (register_mask & (1 << i)) {
          mirror::Object** ref_addr = reinterpret_cast<mirror::Object**>(GetGPRAddress(i));
          if (kIsDebugBuild && ref_addr == nullptr) {
            std::string thread_name;
            GetThread()->GetThreadName(thread_name);
            LOG(FATAL_WITHOUT_ABORT) << "On thread " << thread_name;
            DescribeStack(GetThread());
            LOG(FATAL) << "Found an unsaved callee-save register " << i << " (null GPRAddress) "
                       << "set in register_mask=" << register_mask << " at " << DescribeLocation();
          }
          if (*ref_addr != nullptr) {
            vreg_info.VisitRegister(ref_addr, i, this);
          }
        }
      }
    } else if (!m->IsRuntimeMethod() && m->IsProxyMethod()) {
      // If this is a proxy method, visit its reference arguments.
      DCHECK(!m->IsStatic());
      DCHECK(!m->IsNative());
      std::vector<StackReference<mirror::Object>*> ref_addrs =
          GetProxyReferenceArguments(cur_quick_frame);
      for (StackReference<mirror::Object>* ref_addr : ref_addrs) {
        mirror::Object* ref = ref_addr->AsMirrorPtr();
        if (ref != nullptr) {
          mirror::Object* new_ref = ref;
          visitor_(&new_ref, /* vreg= */ JavaFrameRootInfo::kProxyReferenceArgument, this);
          if (ref != new_ref) {
            ref_addr->Assign(new_ref);
          }
        }
      }
    }
  }

  void VisitQuickFrame() REQUIRES_SHARED(Locks::mutator_lock_) {
    if (kPrecise) {
      VisitQuickFramePrecise();
    } else {
      VisitQuickFrameNonPrecise();
    }
  }

  void VisitQuickFrameNonPrecise() REQUIRES_SHARED(Locks::mutator_lock_) {
    struct UndefinedVRegInfo {
      UndefinedVRegInfo([[maybe_unused]] ArtMethod* method,
                        [[maybe_unused]] const CodeInfo& code_info,
                        [[maybe_unused]] const StackMap& map,
                        RootVisitor& _visitor)
          : visitor(_visitor) {}

      ALWAYS_INLINE
      void VisitStack(mirror::Object** ref,
                      [[maybe_unused]] size_t stack_index,
                      const StackVisitor* stack_visitor) REQUIRES_SHARED(Locks::mutator_lock_) {
        visitor(ref, JavaFrameRootInfo::kImpreciseVreg, stack_visitor);
      }

      ALWAYS_INLINE
      void VisitRegister(mirror::Object** ref,
                         [[maybe_unused]] size_t register_index,
                         const StackVisitor* stack_visitor) REQUIRES_SHARED(Locks::mutator_lock_) {
        visitor(ref, JavaFrameRootInfo::kImpreciseVreg, stack_visitor);
      }

      RootVisitor& visitor;
    };
    VisitQuickFrameWithVregCallback<UndefinedVRegInfo>();
  }

  void VisitQuickFramePrecise() REQUIRES_SHARED(Locks::mutator_lock_) {
    struct StackMapVRegInfo {
      StackMapVRegInfo(ArtMethod* method,
                       const CodeInfo& _code_info,
                       const StackMap& map,
                       RootVisitor& _visitor)
          : number_of_dex_registers(method->DexInstructionData().RegistersSize()),
            code_info(_code_info),
            dex_register_map(code_info.GetDexRegisterMapOf(map)),
            visitor(_visitor) {
        DCHECK_EQ(dex_register_map.size(), number_of_dex_registers);
      }

      // TODO: If necessary, we should consider caching a reverse map instead of the linear
      //       lookups for each location.
      void FindWithType(const size_t index,
                        const DexRegisterLocation::Kind kind,
                        mirror::Object** ref,
                        const StackVisitor* stack_visitor)
          REQUIRES_SHARED(Locks::mutator_lock_) {
        bool found = false;
        for (size_t dex_reg = 0; dex_reg != number_of_dex_registers; ++dex_reg) {
          DexRegisterLocation location = dex_register_map[dex_reg];
          if (location.GetKind() == kind && static_cast<size_t>(location.GetValue()) == index) {
            visitor(ref, dex_reg, stack_visitor);
            found = true;
          }
        }

        if (!found) {
          // If nothing found, report with unknown.
          visitor(ref, JavaFrameRootInfo::kUnknownVreg, stack_visitor);
        }
      }

      void VisitStack(mirror::Object** ref, size_t stack_index, const StackVisitor* stack_visitor)
          REQUIRES_SHARED(Locks::mutator_lock_) {
        const size_t stack_offset = stack_index * kFrameSlotSize;
        FindWithType(stack_offset,
                     DexRegisterLocation::Kind::kInStack,
                     ref,
                     stack_visitor);
      }

      void VisitRegister(mirror::Object** ref,
                         size_t register_index,
                         const StackVisitor* stack_visitor)
          REQUIRES_SHARED(Locks::mutator_lock_) {
        FindWithType(register_index,
                     DexRegisterLocation::Kind::kInRegister,
                     ref,
                     stack_visitor);
      }

      size_t number_of_dex_registers;
      const CodeInfo& code_info;
      DexRegisterMap dex_register_map;
      RootVisitor& visitor;
    };
    VisitQuickFrameWithVregCallback<StackMapVRegInfo>();
  }

  // Visitor for when we visit a root.
  RootVisitor& visitor_;
  bool visit_declaring_class_;
};

class RootCallbackVisitor {
 public:
  RootCallbackVisitor(RootVisitor* visitor, uint32_t tid) : visitor_(visitor), tid_(tid) {}

  void operator()(mirror::Object** obj, size_t vreg, const StackVisitor* stack_visitor) const
      REQUIRES_SHARED(Locks::mutator_lock_) {
    visitor_->VisitRoot(obj, JavaFrameRootInfo(tid_, stack_visitor, vreg));
  }

 private:
  RootVisitor* const visitor_;
  const uint32_t tid_;
};

void Thread::VisitReflectiveTargets(ReflectiveValueVisitor* visitor) {
  for (BaseReflectiveHandleScope* brhs = GetTopReflectiveHandleScope();
       brhs != nullptr;
       brhs = brhs->GetLink()) {
    brhs->VisitTargets(visitor);
  }
}

// FIXME: clang-r433403 reports the below function exceeds frame size limit.
// http://b/197647048
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wframe-larger-than="
template <bool kPrecise>
void Thread::VisitRoots(RootVisitor* visitor) {
  const uint32_t thread_id = GetThreadId();
  visitor->VisitRootIfNonNull(&tlsPtr_.opeer, RootInfo(kRootThreadObject, thread_id));
  if (tlsPtr_.exception != nullptr && tlsPtr_.exception != GetDeoptimizationException()) {
    visitor->VisitRoot(reinterpret_cast<mirror::Object**>(&tlsPtr_.exception),
                       RootInfo(kRootNativeStack, thread_id));
  }
  if (tlsPtr_.async_exception != nullptr) {
    visitor->VisitRoot(reinterpret_cast<mirror::Object**>(&tlsPtr_.async_exception),
                       RootInfo(kRootNativeStack, thread_id));
  }
  visitor->VisitRootIfNonNull(&tlsPtr_.monitor_enter_object, RootInfo(kRootNativeStack, thread_id));
  tlsPtr_.jni_env->VisitJniLocalRoots(visitor, RootInfo(kRootJNILocal, thread_id));
  tlsPtr_.jni_env->VisitMonitorRoots(visitor, RootInfo(kRootJNIMonitor, thread_id));
  HandleScopeVisitRoots(visitor, thread_id);
  // Visit roots for deoptimization.
  if (tlsPtr_.stacked_shadow_frame_record != nullptr) {
    RootCallbackVisitor visitor_to_callback(visitor, thread_id);
    ReferenceMapVisitor<RootCallbackVisitor, kPrecise> mapper(this, nullptr, visitor_to_callback);
    for (StackedShadowFrameRecord* record = tlsPtr_.stacked_shadow_frame_record;
         record != nullptr;
         record = record->GetLink()) {
      for (ShadowFrame* shadow_frame = record->GetShadowFrame();
           shadow_frame != nullptr;
           shadow_frame = shadow_frame->GetLink()) {
        mapper.VisitShadowFrame(shadow_frame);
      }
    }
  }
  for (DeoptimizationContextRecord* record = tlsPtr_.deoptimization_context_stack;
       record != nullptr;
       record = record->GetLink()) {
    if (record->IsReference()) {
      visitor->VisitRootIfNonNull(record->GetReturnValueAsGCRoot(),
                                  RootInfo(kRootThreadObject, thread_id));
    }
    visitor->VisitRootIfNonNull(record->GetPendingExceptionAsGCRoot(),
                                RootInfo(kRootThreadObject, thread_id));
  }
  if (tlsPtr_.frame_id_to_shadow_frame != nullptr) {
    RootCallbackVisitor visitor_to_callback(visitor, thread_id);
    ReferenceMapVisitor<RootCallbackVisitor, kPrecise> mapper(this, nullptr, visitor_to_callback);
    for (FrameIdToShadowFrame* record = tlsPtr_.frame_id_to_shadow_frame;
         record != nullptr;
         record = record->GetNext()) {
      mapper.VisitShadowFrame(record->GetShadowFrame());
    }
  }
  // Visit roots on this thread's stack
  RuntimeContextType context;
  RootCallbackVisitor visitor_to_callback(visitor, thread_id);
  ReferenceMapVisitor<RootCallbackVisitor, kPrecise> mapper(this, &context, visitor_to_callback);
  mapper.template WalkStack<StackVisitor::CountTransitions::kNo>(false);
}
#pragma GCC diagnostic pop

static void SweepCacheEntry(IsMarkedVisitor* visitor, const Instruction* inst, size_t* value)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  if (inst == nullptr) {
    return;
  }
  using Opcode = Instruction::Code;
  Opcode opcode = inst->Opcode();
  switch (opcode) {
    case Opcode::NEW_INSTANCE:
    case Opcode::CHECK_CAST:
    case Opcode::INSTANCE_OF:
    case Opcode::NEW_ARRAY:
    case Opcode::CONST_CLASS: {
      mirror::Class* klass = reinterpret_cast<mirror::Class*>(*value);
      if (klass == nullptr || klass == Runtime::GetWeakClassSentinel()) {
        return;
      }
      mirror::Class* new_klass = down_cast<mirror::Class*>(visitor->IsMarked(klass));
      if (new_klass == nullptr) {
        *value = reinterpret_cast<size_t>(Runtime::GetWeakClassSentinel());
      } else if (new_klass != klass) {
        *value = reinterpret_cast<size_t>(new_klass);
      }
      return;
    }
    case Opcode::CONST_STRING:
    case Opcode::CONST_STRING_JUMBO: {
      mirror::Object* object = reinterpret_cast<mirror::Object*>(*value);
      if (object == nullptr) {
        return;
      }
      mirror::Object* new_object = visitor->IsMarked(object);
      // We know the string is marked because it's a strongly-interned string that
      // is always alive (see b/117621117 for trying to make those strings weak).
      if (kIsDebugBuild && new_object == nullptr) {
        // (b/275005060) Currently the problem is reported only on CC GC.
        // Therefore we log it with more information. But since the failure rate
        // is quite high, sampling it.
        if (gUseReadBarrier) {
          Runtime* runtime = Runtime::Current();
          gc::collector::ConcurrentCopying* cc = runtime->GetHeap()->ConcurrentCopyingCollector();
          CHECK_NE(cc, nullptr);
          LOG(FATAL) << cc->DumpReferenceInfo(object, "string")
                     << " string interned: " << std::boolalpha
                     << runtime->GetInternTable()->LookupStrong(Thread::Current(),
                                                                down_cast<mirror::String*>(object))
                     << std::noboolalpha;
        } else {
          // Other GCs
          LOG(FATAL) << __FUNCTION__
                     << ": IsMarked returned null for a strongly interned string: " << object;
        }
      } else if (new_object != object) {
        *value = reinterpret_cast<size_t>(new_object);
      }
      return;
    }
    default:
      // The following opcode ranges store non-reference values.
      if ((Opcode::IGET <= opcode && opcode <= Opcode::SPUT_SHORT) ||
          (Opcode::INVOKE_VIRTUAL <= opcode && opcode <= Opcode::INVOKE_INTERFACE_RANGE)) {
        return;  // Nothing to do for the GC.
      }
      // New opcode is using the cache. We need to explicitly handle it in this method.
      DCHECK(false) << "Unhandled opcode " << inst->Opcode();
  }
}

void Thread::SweepInterpreterCache(IsMarkedVisitor* visitor) {
  for (InterpreterCache::Entry& entry : GetInterpreterCache()->GetArray()) {
    SweepCacheEntry(visitor, reinterpret_cast<const Instruction*>(entry.first), &entry.second);
  }
}

// FIXME: clang-r433403 reports the below function exceeds frame size limit.
// http://b/197647048
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wframe-larger-than="
void Thread::VisitRoots(RootVisitor* visitor, VisitRootFlags flags) {
  if ((flags & VisitRootFlags::kVisitRootFlagPrecise) != 0) {
    VisitRoots</* kPrecise= */ true>(visitor);
  } else {
    VisitRoots</* kPrecise= */ false>(visitor);
  }
}
#pragma GCC diagnostic pop

class VerifyRootVisitor : public SingleRootVisitor {
 public:
  void VisitRoot(mirror::Object* root, [[maybe_unused]] const RootInfo& info) override
      REQUIRES_SHARED(Locks::mutator_lock_) {
    VerifyObject(root);
  }
};

void Thread::VerifyStackImpl() {
  if (Runtime::Current()->GetHeap()->IsObjectValidationEnabled()) {
    VerifyRootVisitor visitor;
    std::unique_ptr<Context> context(Context::Create());
    RootCallbackVisitor visitor_to_callback(&visitor, GetThreadId());
    ReferenceMapVisitor<RootCallbackVisitor> mapper(this, context.get(), visitor_to_callback);
    mapper.WalkStack();
  }
}

// Set the stack end to that to be used during a stack overflow
void Thread::SetStackEndForStackOverflow() {
  // During stack overflow we allow use of the full stack.
  if (tlsPtr_.stack_end == tlsPtr_.stack_begin) {
    // However, we seem to have already extended to use the full stack.
    LOG(ERROR) << "Need to increase kStackOverflowReservedBytes (currently "
               << GetStackOverflowReservedBytes(kRuntimeISA) << ")?";
    DumpStack(LOG_STREAM(ERROR));
    LOG(FATAL) << "Recursive stack overflow.";
  }

  tlsPtr_.stack_end = tlsPtr_.stack_begin;

  // Remove the stack overflow protection if is it set up.
  bool implicit_stack_check = Runtime::Current()->GetImplicitStackOverflowChecks();
  if (implicit_stack_check) {
    if (!UnprotectStack()) {
      LOG(ERROR) << "Unable to remove stack protection for stack overflow";
    }
  }
}

void Thread::SetTlab(uint8_t* start, uint8_t* end, uint8_t* limit) {
  DCHECK_LE(start, end);
  DCHECK_LE(end, limit);
  tlsPtr_.thread_local_start = start;
  tlsPtr_.thread_local_pos  = tlsPtr_.thread_local_start;
  tlsPtr_.thread_local_end = end;
  tlsPtr_.thread_local_limit = limit;
  tlsPtr_.thread_local_objects = 0;
}

void Thread::ResetTlab() {
  gc::Heap* const heap = Runtime::Current()->GetHeap();
  if (heap->GetHeapSampler().IsEnabled()) {
    // Note: We always ResetTlab before SetTlab, therefore we can do the sample
    // offset adjustment here.
    heap->AdjustSampleOffset(GetTlabPosOffset());
    VLOG(heap) << "JHP: ResetTlab, Tid: " << GetTid()
               << " adjustment = "
               << (tlsPtr_.thread_local_pos - tlsPtr_.thread_local_start);
  }
  SetTlab(nullptr, nullptr, nullptr);
}

bool Thread::HasTlab() const {
  const bool has_tlab = tlsPtr_.thread_local_pos != nullptr;
  if (has_tlab) {
    DCHECK(tlsPtr_.thread_local_start != nullptr && tlsPtr_.thread_local_end != nullptr);
  } else {
    DCHECK(tlsPtr_.thread_local_start == nullptr && tlsPtr_.thread_local_end == nullptr);
  }
  return has_tlab;
}

void Thread::AdjustTlab(size_t slide_bytes) {
  if (HasTlab()) {
    tlsPtr_.thread_local_start -= slide_bytes;
    tlsPtr_.thread_local_pos -= slide_bytes;
    tlsPtr_.thread_local_end -= slide_bytes;
    tlsPtr_.thread_local_limit -= slide_bytes;
  }
}

std::ostream& operator<<(std::ostream& os, const Thread& thread) {
  thread.ShortDump(os);
  return os;
}

bool Thread::ProtectStack(bool fatal_on_error) {
  void* pregion = tlsPtr_.stack_begin - GetStackOverflowProtectedSize();
  VLOG(threads) << "Protecting stack at " << pregion;
  if (mprotect(pregion, GetStackOverflowProtectedSize(), PROT_NONE) == -1) {
    if (fatal_on_error) {
      // b/249586057, LOG(FATAL) times out
      LOG(ERROR) << "Unable to create protected region in stack for implicit overflow check. "
          "Reason: "
          << strerror(errno) << " size:  " << GetStackOverflowProtectedSize();
      exit(1);
    }
    return false;
  }
  return true;
}

bool Thread::UnprotectStack() {
  void* pregion = tlsPtr_.stack_begin - GetStackOverflowProtectedSize();
  VLOG(threads) << "Unprotecting stack at " << pregion;
  return mprotect(pregion, GetStackOverflowProtectedSize(), PROT_READ|PROT_WRITE) == 0;
}

size_t Thread::NumberOfHeldMutexes() const {
  size_t count = 0;
  for (BaseMutex* mu : tlsPtr_.held_mutexes) {
    count += mu != nullptr ? 1 : 0;
  }
  return count;
}

void Thread::DeoptimizeWithDeoptimizationException(JValue* result) {
  DCHECK_EQ(GetException(), Thread::GetDeoptimizationException());
  ClearException();
  ObjPtr<mirror::Throwable> pending_exception;
  bool from_code = false;
  DeoptimizationMethodType method_type;
  PopDeoptimizationContext(result, &pending_exception, &from_code, &method_type);
  SetTopOfStack(nullptr);

  // Restore the exception that was pending before deoptimization then interpret the
  // deoptimized frames.
  if (pending_exception != nullptr) {
    SetException(pending_exception);
  }

  ShadowFrame* shadow_frame = MaybePopDeoptimizedStackedShadowFrame();
  // We may not have a shadow frame if we deoptimized at the return of the
  // quick_to_interpreter_bridge which got directly called by art_quick_invoke_stub.
  if (shadow_frame != nullptr) {
    SetTopOfShadowStack(shadow_frame);
    interpreter::EnterInterpreterFromDeoptimize(this,
                                                shadow_frame,
                                                result,
                                                from_code,
                                                method_type);
  }
}

void Thread::SetAsyncException(ObjPtr<mirror::Throwable> new_exception) {
  CHECK(new_exception != nullptr);
  Runtime::Current()->SetAsyncExceptionsThrown();
  if (kIsDebugBuild) {
    // Make sure we are in a checkpoint.
    MutexLock mu(Thread::Current(), *Locks::thread_suspend_count_lock_);
    CHECK(this == Thread::Current() || GetSuspendCount() >= 1)
        << "It doesn't look like this was called in a checkpoint! this: "
        << this << " count: " << GetSuspendCount();
  }
  tlsPtr_.async_exception = new_exception.Ptr();
}

bool Thread::ObserveAsyncException() {
  DCHECK(this == Thread::Current());
  if (tlsPtr_.async_exception != nullptr) {
    if (tlsPtr_.exception != nullptr) {
      LOG(WARNING) << "Overwriting pending exception with async exception. Pending exception is: "
                   << tlsPtr_.exception->Dump();
      LOG(WARNING) << "Async exception is " << tlsPtr_.async_exception->Dump();
    }
    tlsPtr_.exception = tlsPtr_.async_exception;
    tlsPtr_.async_exception = nullptr;
    return true;
  } else {
    return IsExceptionPending();
  }
}

void Thread::SetException(ObjPtr<mirror::Throwable> new_exception) {
  CHECK(new_exception != nullptr);
  // TODO: DCHECK(!IsExceptionPending());
  tlsPtr_.exception = new_exception.Ptr();
}

bool Thread::IsAotCompiler() {
  return Runtime::Current()->IsAotCompiler();
}

mirror::Object* Thread::GetPeerFromOtherThread() {
  Thread* self = Thread::Current();
  if (this == self) {
    // We often call this on every thread, including ourselves.
    return GetPeer();
  }
  // If "this" thread is not suspended, it could disappear.
  DCHECK(IsSuspended()) << *this;
  DCHECK(tlsPtr_.jpeer == nullptr);
  // Some JVMTI code may unfortunately hold thread_list_lock_, but if it does, it should hold the
  // mutator lock in exclusive mode, and we should not have a pending flip function.
  if (kIsDebugBuild && Locks::thread_list_lock_->IsExclusiveHeld(self)) {
    Locks::mutator_lock_->AssertExclusiveHeld(self);
    CHECK(!ReadFlag(ThreadFlag::kPendingFlipFunction));
  }
  // Ensure that opeer is not obsolete.
  EnsureFlipFunctionStarted(self, this);
  if (ReadFlag(ThreadFlag::kRunningFlipFunction)) {
    // Does not release mutator lock. Hence no new flip requests can be issued.
    WaitForFlipFunction(self);
  }
  return tlsPtr_.opeer;
}

mirror::Object* Thread::LockedGetPeerFromOtherThread(ThreadExitFlag* tef) {
  DCHECK(tlsPtr_.jpeer == nullptr);
  Thread* self = Thread::Current();
  Locks::thread_list_lock_->AssertHeld(self);
  if (ReadFlag(ThreadFlag::kPendingFlipFunction)) {
    // It is unsafe to call EnsureFlipFunctionStarted with thread_list_lock_. Thus we temporarily
    // release it, taking care to handle the case in which "this" thread disapppears while we no
    // longer hold it.
    Locks::thread_list_lock_->Unlock(self);
    EnsureFlipFunctionStarted(self, this, StateAndFlags(0), tef);
    Locks::thread_list_lock_->Lock(self);
    if (tef->HasExited()) {
      return nullptr;
    }
  }
  if (ReadFlag(ThreadFlag::kRunningFlipFunction)) {
    // Does not release mutator lock. Hence no new flip requests can be issued.
    WaitForFlipFunction(self);
  }
  return tlsPtr_.opeer;
}

void Thread::SetReadBarrierEntrypoints() {
  // Make sure entrypoints aren't null.
  UpdateReadBarrierEntrypoints(&tlsPtr_.quick_entrypoints, /* is_active=*/ true);
}

void Thread::ClearAllInterpreterCaches() {
  static struct ClearInterpreterCacheClosure : Closure {
    void Run(Thread* thread) override {
      thread->GetInterpreterCache()->Clear(thread);
    }
  } closure;
  Runtime::Current()->GetThreadList()->RunCheckpoint(&closure);
}


void Thread::ReleaseLongJumpContextInternal() {
  // Each QuickExceptionHandler gets a long jump context and uses
  // it for doing the long jump, after finding catch blocks/doing deoptimization.
  // Both finding catch blocks and deoptimization can trigger another
  // exception such as a result of class loading. So there can be nested
  // cases of exception handling and multiple contexts being used.
  // ReleaseLongJumpContext tries to save the context in tlsPtr_.long_jump_context
  // for reuse so there is no need to always allocate a new one each time when
  // getting a context. Since we only keep one context for reuse, delete the
  // existing one since the passed in context is yet to be used for longjump.
  delete tlsPtr_.long_jump_context;
}

void Thread::SetNativePriority(int new_priority) {
  palette_status_t status = PaletteSchedSetPriority(GetTid(), new_priority);
  CHECK(status == PALETTE_STATUS_OK || status == PALETTE_STATUS_CHECK_ERRNO);
}

int Thread::GetNativePriority() const {
  int priority = 0;
  palette_status_t status = PaletteSchedGetPriority(GetTid(), &priority);
  CHECK(status == PALETTE_STATUS_OK || status == PALETTE_STATUS_CHECK_ERRNO);
  return priority;
}

void Thread::AbortInThis(std::string message) {
  std::string thread_name;
  Thread::Current()->GetThreadName(thread_name);
  LOG(ERROR) << message;
  LOG(ERROR) << "Aborting culprit thread";
  Runtime::Current()->SetAbortMessage(("Caused " + thread_name + " failure : " + message).c_str());
  // Unlike Runtime::Abort() we do not fflush(nullptr), since we want to send the signal with as
  // little delay as possible.
  int res = pthread_kill(tlsPtr_.pthread_self, SIGABRT);
  if (res != 0) {
    LOG(ERROR) << "pthread_kill failed with " << res << " " << strerror(res) << " target was "
               << tls32_.tid;
  } else {
    // Wait for our process to be aborted.
    sleep(10 /* seconds */);
  }
  // The process should have died long before we got here. Never return.
  LOG(FATAL) << "Failed to abort in culprit thread: " << message;
  UNREACHABLE();
}

bool Thread::IsSystemDaemon() const {
  if (GetPeer() == nullptr) {
    return false;
  }
  return WellKnownClasses::java_lang_Thread_systemDaemon->GetBoolean(GetPeer());
}

std::string Thread::StateAndFlagsAsHexString() const {
  std::stringstream result_stream;
  result_stream << std::hex << GetStateAndFlags(std::memory_order_relaxed).GetValue();
  return result_stream.str();
}

ScopedExceptionStorage::ScopedExceptionStorage(art::Thread* self)
    : self_(self), hs_(self_), excp_(hs_.NewHandle<art::mirror::Throwable>(self_->GetException())) {
  self_->ClearException();
}

void ScopedExceptionStorage::SuppressOldException(const char* message) {
  CHECK(self_->IsExceptionPending()) << *self_;
  ObjPtr<mirror::Throwable> old_suppressed(excp_.Get());
  excp_.Assign(self_->GetException());
  if (old_suppressed != nullptr) {
    LOG(WARNING) << message << "Suppressing old exception: " << old_suppressed->Dump();
  }
  self_->ClearException();
}

ScopedExceptionStorage::~ScopedExceptionStorage() {
  CHECK(!self_->IsExceptionPending()) << *self_;
  if (!excp_.IsNull()) {
    self_->SetException(excp_.Get());
  }
}

}  // namespace art

#pragma clang diagnostic pop  // -Wconversion
