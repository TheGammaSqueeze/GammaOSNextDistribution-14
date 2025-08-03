/*
 * Copyright (C) 2012 The Android Open Source Project
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

#include <android-base/logging.h>

#include "art_method-inl.h"
#include "base/casts.h"
#include "entrypoints/entrypoint_utils-inl.h"
#include "indirect_reference_table.h"
#include "mirror/object-inl.h"
#include "palette/palette.h"
#include "thread-inl.h"
#include "verify_object.h"

// For methods that monitor JNI invocations and report their begin/end to
// palette hooks.
#define MONITOR_JNI(kind)                                \
  {                                                      \
    bool should_report = false;                          \
    PaletteShouldReportJniInvocations(&should_report);   \
    if (should_report) {                                 \
      kind(self->GetJniEnv());                           \
    }                                                    \
  }

namespace art HIDDEN {

extern "C" int artMethodExitHook(Thread* self,
                                 ArtMethod* method,
                                 uint64_t* gpr_result,
                                 uint64_t* fpr_result);

static_assert(sizeof(jni::LRTSegmentState) == sizeof(uint32_t), "LRTSegmentState size unexpected");
static_assert(std::is_trivial<jni::LRTSegmentState>::value, "LRTSegmentState not trivial");

extern "C" void artJniReadBarrier(ArtMethod* method) {
  DCHECK(gUseReadBarrier);
  mirror::CompressedReference<mirror::Object>* declaring_class =
      method->GetDeclaringClassAddressWithoutBarrier();
  if (kUseBakerReadBarrier) {
    DCHECK(declaring_class->AsMirrorPtr() != nullptr)
        << "The class of a static jni call must not be null";
    // Check the mark bit and return early if it's already marked.
    if (LIKELY(declaring_class->AsMirrorPtr()->GetMarkBit() != 0)) {
      return;
    }
  }
  // Call the read barrier and update the handle.
  mirror::Object* to_ref = ReadBarrier::BarrierForRoot(declaring_class);
  declaring_class->Assign(to_ref);
}

// Called on entry to JNI, transition out of Runnable and release share of mutator_lock_.
extern "C" void artJniMethodStart(Thread* self) {
  if (kIsDebugBuild) {
    ArtMethod* native_method = *self->GetManagedStack()->GetTopQuickFrame();
    CHECK(!native_method->IsFastNative()) << native_method->PrettyMethod();
    CHECK(!native_method->IsCriticalNative()) << native_method->PrettyMethod();
  }

  // Transition out of runnable.
  self->TransitionFromRunnableToSuspended(ThreadState::kNative);
}

static void PopLocalReferences(uint32_t saved_local_ref_cookie, Thread* self)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  JNIEnvExt* env = self->GetJniEnv();
  if (UNLIKELY(env->IsCheckJniEnabled())) {
    env->CheckNoHeldMonitors();
  }
  env->PopLocalReferenceFrame(bit_cast<jni::LRTSegmentState>(saved_local_ref_cookie));
}

// TODO: annotalysis disabled as monitor semantics are maintained in Java code.
__attribute__((no_sanitize("memtag")))  // TODO(b/305919664)
extern "C" void
artJniUnlockObject(mirror::Object* locked, Thread* self) NO_THREAD_SAFETY_ANALYSIS
    REQUIRES(!Roles::uninterruptible_) {
  // Note: No thread suspension is allowed for successful unlocking, otherwise plain
  // `mirror::Object*` return value saved by the assembly stub would need to be updated.
  uintptr_t old_poison_object_cookie = kIsDebugBuild ? self->GetPoisonObjectCookie() : 0u;
  // Save any pending exception over monitor exit call.
  ObjPtr<mirror::Throwable> saved_exception = nullptr;
  if (UNLIKELY(self->IsExceptionPending())) {
    saved_exception = self->GetException();
    self->ClearException();
  }
  // Decode locked object and unlock, before popping local references.
  locked->MonitorExit(self);
  if (UNLIKELY(self->IsExceptionPending())) {
    LOG(FATAL) << "Exception during implicit MonitorExit for synchronized native method:\n"
        << self->GetException()->Dump()
        << (saved_exception != nullptr
               ? "\nAn exception was already pending:\n" + saved_exception->Dump()
               : "");
    UNREACHABLE();
  }
  // Restore pending exception.
  if (saved_exception != nullptr) {
    self->SetException(saved_exception);
  }
  if (kIsDebugBuild) {
    DCHECK_EQ(old_poison_object_cookie, self->GetPoisonObjectCookie());
  }
}

// TODO: These should probably be templatized or macro-ized.
// Otherwise there's just too much repetitive boilerplate.

extern "C" void artJniMethodEnd(Thread* self) {
  self->TransitionFromSuspendedToRunnable();

  if (kIsDebugBuild) {
    ArtMethod* native_method = *self->GetManagedStack()->GetTopQuickFrame();
    CHECK(!native_method->IsFastNative()) << native_method->PrettyMethod();
    CHECK(!native_method->IsCriticalNative()) << native_method->PrettyMethod();
  }
}

extern mirror::Object* JniDecodeReferenceResult(jobject result, Thread* self)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  DCHECK(!self->IsExceptionPending());
  ObjPtr<mirror::Object> o = self->DecodeJObject(result);
  // Process result.
  if (UNLIKELY(self->GetJniEnv()->IsCheckJniEnabled())) {
    // CheckReferenceResult can resolve types.
    StackHandleScope<1> hs(self);
    HandleWrapperObjPtr<mirror::Object> h_obj(hs.NewHandleWrapper(&o));
    CheckReferenceResult(h_obj, self);
  }
  VerifyObject(o);
  return o.Ptr();
}

extern uint64_t GenericJniMethodEnd(Thread* self,
                                    uint32_t saved_local_ref_cookie,
                                    jvalue result,
                                    uint64_t result_f,
                                    ArtMethod* called)
    // NO_THREAD_SAFETY_ANALYSIS because we can enter this function with the mutator lock
    // unlocked for normal JNI, or locked for @FastNative and @CriticalNative.
    NO_THREAD_SAFETY_ANALYSIS {
  bool critical_native = called->IsCriticalNative();
  bool fast_native = called->IsFastNative();
  bool normal_native = !critical_native && !fast_native;

  // @CriticalNative does not do a state transition. @FastNative usually does not do a state
  // transition either but it performs a suspend check that may do state transitions.
  if (LIKELY(normal_native)) {
    if (UNLIKELY(self->ReadFlag(ThreadFlag::kMonitorJniEntryExit))) {
      artJniMonitoredMethodEnd(self);
    } else {
      artJniMethodEnd(self);
    }
  } else if (fast_native) {
    // When we are in @FastNative, we are already Runnable.
    DCHECK(Locks::mutator_lock_->IsSharedHeld(self));
    // Only do a suspend check on the way out of JNI just like compiled stubs.
    self->CheckSuspend();
  }
  // We need the mutator lock (i.e., calling `artJniMethodEnd()`) before accessing
  // the shorty or the locked object.
  if (called->IsSynchronized()) {
    DCHECK(normal_native) << "@FastNative/@CriticalNative and synchronize is not supported";
    ObjPtr<mirror::Object> lock = GetGenericJniSynchronizationObject(self, called);
    DCHECK(lock != nullptr);
    artJniUnlockObject(lock.Ptr(), self);
  }
  char return_shorty_char = called->GetShorty()[0];
  uint64_t ret;
  if (return_shorty_char == 'L') {
    ret = reinterpret_cast<uint64_t>(
        UNLIKELY(self->IsExceptionPending()) ? nullptr : JniDecodeReferenceResult(result.l, self));
    PopLocalReferences(saved_local_ref_cookie, self);
  } else {
    if (LIKELY(!critical_native)) {
      PopLocalReferences(saved_local_ref_cookie, self);
    }
    switch (return_shorty_char) {
      case 'F': {
        if (kRuntimeISA == InstructionSet::kX86) {
          // Convert back the result to float.
          double d = bit_cast<double, uint64_t>(result_f);
          ret = bit_cast<uint32_t, float>(static_cast<float>(d));
        } else {
          ret = result_f;
        }
      }
      break;
      case 'D':
        ret = result_f;
        break;
      case 'Z':
        ret = result.z;
        break;
      case 'B':
        ret = result.b;
        break;
      case 'C':
        ret = result.c;
        break;
      case 'S':
        ret = result.s;
        break;
      case 'I':
        ret = result.i;
        break;
      case 'J':
        ret = result.j;
        break;
      case 'V':
        ret = 0;
        break;
      default:
        LOG(FATAL) << "Unexpected return shorty character " << return_shorty_char;
        UNREACHABLE();
    }
  }

  return ret;
}

extern "C" void artJniMonitoredMethodStart(Thread* self) {
  artJniMethodStart(self);
  MONITOR_JNI(PaletteNotifyBeginJniInvocation);
}

extern "C" void artJniMonitoredMethodEnd(Thread* self) {
  MONITOR_JNI(PaletteNotifyEndJniInvocation);
  artJniMethodEnd(self);
}

}  // namespace art
