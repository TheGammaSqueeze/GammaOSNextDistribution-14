/*
 * Copyright (C) 2018 The Android Open Source Project
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

#include "android-base/macros.h"
#include "gc/scoped_gc_critical_section.h"
#include "instrumentation.h"
#include "runtime.h"
#include "runtime_callbacks.h"
#include "scoped_thread_state_change-inl.h"
#include "thread-inl.h"
#include "thread_list.h"

namespace tracefast {

#if ((!defined(TRACEFAST_INTERPRETER) && !defined(TRACEFAST_TRAMPOLINE)) || \
     (defined(TRACEFAST_INTERPRETER) && defined(TRACEFAST_TRAMPOLINE)))
#error Must set one of TRACEFAST_TRAMPOLINE or TRACEFAST_INTERPRETER during build
#endif


#ifdef TRACEFAST_INTERPRETER
static constexpr const char* kTracerInstrumentationKey = "tracefast_INTERPRETER";
static constexpr bool kNeedsInterpreter = true;
#else  // defined(TRACEFAST_TRAMPOLINE)
static constexpr const char* kTracerInstrumentationKey = "tracefast_TRAMPOLINE";
static constexpr bool kNeedsInterpreter = false;
#endif  // TRACEFAST_INITERPRETER

class Tracer final : public art::instrumentation::InstrumentationListener {
 public:
  Tracer() {}

  void MethodEntered([[maybe_unused]] art::Thread* thread,
                     [[maybe_unused]] art::ArtMethod* method) override
      REQUIRES_SHARED(art::Locks::mutator_lock_) {}

  void MethodExited([[maybe_unused]] art::Thread* thread,
                    [[maybe_unused]] art::ArtMethod* method,
                    [[maybe_unused]] art::instrumentation::OptionalFrame frame,
                    [[maybe_unused]] art::MutableHandle<art::mirror::Object>& return_value) override
      REQUIRES_SHARED(art::Locks::mutator_lock_) {}

  void MethodExited([[maybe_unused]] art::Thread* thread,
                    [[maybe_unused]] art::ArtMethod* method,
                    [[maybe_unused]] art::instrumentation::OptionalFrame frame,
                    [[maybe_unused]] art::JValue& return_value) override
      REQUIRES_SHARED(art::Locks::mutator_lock_) {}

  void MethodUnwind([[maybe_unused]] art::Thread* thread,
                    [[maybe_unused]] art::ArtMethod* method,
                    [[maybe_unused]] uint32_t dex_pc) override
      REQUIRES_SHARED(art::Locks::mutator_lock_) {}

  void DexPcMoved([[maybe_unused]] art::Thread* thread,
                  [[maybe_unused]] art::Handle<art::mirror::Object> this_object,
                  [[maybe_unused]] art::ArtMethod* method,
                  [[maybe_unused]] uint32_t new_dex_pc) override
      REQUIRES_SHARED(art::Locks::mutator_lock_) {}

  void FieldRead([[maybe_unused]] art::Thread* thread,
                 [[maybe_unused]] art::Handle<art::mirror::Object> this_object,
                 [[maybe_unused]] art::ArtMethod* method,
                 [[maybe_unused]] uint32_t dex_pc,
                 [[maybe_unused]] art::ArtField* field) override
      REQUIRES_SHARED(art::Locks::mutator_lock_) {}

  void FieldWritten([[maybe_unused]] art::Thread* thread,
                    [[maybe_unused]] art::Handle<art::mirror::Object> this_object,
                    [[maybe_unused]] art::ArtMethod* method,
                    [[maybe_unused]] uint32_t dex_pc,
                    [[maybe_unused]] art::ArtField* field,
                    [[maybe_unused]] art::Handle<art::mirror::Object> field_value) override
      REQUIRES_SHARED(art::Locks::mutator_lock_) {}

  void FieldWritten([[maybe_unused]] art::Thread* thread,
                    [[maybe_unused]] art::Handle<art::mirror::Object> this_object,
                    [[maybe_unused]] art::ArtMethod* method,
                    [[maybe_unused]] uint32_t dex_pc,
                    [[maybe_unused]] art::ArtField* field,
                    [[maybe_unused]] const art::JValue& field_value) override
      REQUIRES_SHARED(art::Locks::mutator_lock_) {}

  void ExceptionThrown([[maybe_unused]] art::Thread* thread,
                       [[maybe_unused]] art::Handle<art::mirror::Throwable> exception_object)
      override REQUIRES_SHARED(art::Locks::mutator_lock_) {}

  void ExceptionHandled([[maybe_unused]] art::Thread* self,
                        [[maybe_unused]] art::Handle<art::mirror::Throwable> throwable) override
      REQUIRES_SHARED(art::Locks::mutator_lock_) {}

  void Branch([[maybe_unused]] art::Thread* thread,
              [[maybe_unused]] art::ArtMethod* method,
              [[maybe_unused]] uint32_t dex_pc,
              [[maybe_unused]] int32_t dex_pc_offset) override
      REQUIRES_SHARED(art::Locks::mutator_lock_) {}

  void WatchedFramePop([[maybe_unused]] art::Thread* thread,
                       [[maybe_unused]] const art::ShadowFrame& frame) override
      REQUIRES_SHARED(art::Locks::mutator_lock_) {}

 private:
  DISALLOW_COPY_AND_ASSIGN(Tracer);
};

Tracer gEmptyTracer;

static void StartTracing() REQUIRES(!art::Locks::mutator_lock_,
                                    !art::Locks::thread_list_lock_,
                                    !art::Locks::thread_suspend_count_lock_) {
  art::Thread* self = art::Thread::Current();
  art::Runtime* runtime = art::Runtime::Current();
  art::gc::ScopedGCCriticalSection gcs(self,
                                       art::gc::kGcCauseInstrumentation,
                                       art::gc::kCollectorTypeInstrumentation);
  art::ScopedSuspendAll ssa("starting fast tracing");
  runtime->GetInstrumentation()->AddListener(&gEmptyTracer,
                                             art::instrumentation::Instrumentation::kMethodEntered |
                                             art::instrumentation::Instrumentation::kMethodExited |
                                             art::instrumentation::Instrumentation::kMethodUnwind);
  runtime->GetInstrumentation()->EnableMethodTracing(
      kTracerInstrumentationKey, &gEmptyTracer, kNeedsInterpreter);
}

class TraceFastPhaseCB : public art::RuntimePhaseCallback {
 public:
  TraceFastPhaseCB() {}

  void NextRuntimePhase(art::RuntimePhaseCallback::RuntimePhase phase)
      override REQUIRES_SHARED(art::Locks::mutator_lock_) {
    if (phase == art::RuntimePhaseCallback::RuntimePhase::kInit) {
      art::ScopedThreadSuspension sts(art::Thread::Current(),
                                      art::ThreadState::kWaitingForMethodTracingStart);
      StartTracing();
    }
  }
};
TraceFastPhaseCB gPhaseCallback;

// The plugin initialization function.
extern "C" bool ArtPlugin_Initialize() {
  art::Runtime* runtime = art::Runtime::Current();
  art::ScopedThreadStateChange stsc(art::Thread::Current(),
                                    art::ThreadState::kWaitingForMethodTracingStart);
  art::ScopedSuspendAll ssa("Add phase callback");
  runtime->GetRuntimeCallbacks()->AddRuntimePhaseCallback(&gPhaseCallback);
  return true;
}

extern "C" bool ArtPlugin_Deinitialize() {
  // Don't need to bother doing anything.
  return true;
}

}  // namespace tracefast
