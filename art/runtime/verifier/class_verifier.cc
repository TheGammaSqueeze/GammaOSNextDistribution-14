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

#include "class_verifier.h"

#include <android-base/logging.h>
#include <android-base/stringprintf.h>

#include "art_method-inl.h"
#include "base/enums.h"
#include "base/locks.h"
#include "base/logging.h"
#include "base/systrace.h"
#include "base/utils.h"
#include "class_linker.h"
#include "compiler_callbacks.h"
#include "dex/class_accessor-inl.h"
#include "dex/class_reference.h"
#include "dex/descriptors_names.h"
#include "dex/dex_file-inl.h"
#include "handle.h"
#include "handle_scope-inl.h"
#include "method_verifier-inl.h"
#include "mirror/class-inl.h"
#include "mirror/dex_cache.h"
#include "runtime.h"
#include "thread.h"
#include "verifier_compiler_binding.h"
#include "verifier/method_verifier.h"
#include "verifier/reg_type_cache.h"

namespace art HIDDEN {
namespace verifier {

using android::base::StringPrintf;

// We print a warning blurb about "dx --no-optimize" when we find monitor-locking issues. Make
// sure we only print this once.
static bool gPrintedDxMonitorText = false;

static void UpdateMethodFlags(uint32_t method_index,
                              Handle<mirror::Class> klass,
                              Handle<mirror::DexCache> dex_cache,
                              CompilerCallbacks* callbacks,
                              int error_types)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  if (callbacks != nullptr && !CanCompilerHandleVerificationFailure(error_types)) {
    MethodReference ref(dex_cache->GetDexFile(), method_index);
    callbacks->AddUncompilableMethod(ref);
  }
  if (klass == nullptr) {
    DCHECK(Runtime::Current()->IsAotCompiler());
    // Flags will be set at runtime.
    return;
  }

  // Mark methods with DontCompile/MustCountLocks flags.
  ClassLinker* const linker = Runtime::Current()->GetClassLinker();
  ArtMethod* method =
      klass->FindClassMethod(dex_cache.Get(), method_index, linker->GetImagePointerSize());
  DCHECK(method != nullptr);
  DCHECK(method->GetDeclaringClass() == klass.Get());
  if (!CanCompilerHandleVerificationFailure(error_types)) {
    method->SetDontCompile();
  }
  if ((error_types & VerifyError::VERIFY_ERROR_LOCKING) != 0) {
    method->SetMustCountLocks();
  }
}

FailureKind ClassVerifier::VerifyClass(Thread* self,
                                       VerifierDeps* verifier_deps,
                                       const DexFile* dex_file,
                                       Handle<mirror::Class> klass,
                                       Handle<mirror::DexCache> dex_cache,
                                       Handle<mirror::ClassLoader> class_loader,
                                       const dex::ClassDef& class_def,
                                       CompilerCallbacks* callbacks,
                                       HardFailLogMode log_level,
                                       uint32_t api_level,
                                       std::string* error) {
  // A class must not be abstract and final.
  if ((class_def.access_flags_ & (kAccAbstract | kAccFinal)) == (kAccAbstract | kAccFinal)) {
    *error = "Verifier rejected class ";
    *error += PrettyDescriptor(dex_file->GetClassDescriptor(class_def));
    *error += ": class is abstract and final.";
    return FailureKind::kHardFailure;
  }

  // Note that `klass` can be a redefined class, not in the loader's table yet.
  // Therefore, we do not use it for class resolution, but only when needing to
  // update its methods' flags.
  ClassAccessor accessor(*dex_file, class_def);
  SCOPED_TRACE << "VerifyClass " << PrettyDescriptor(accessor.GetDescriptor());
  metrics::AutoTimer timer{GetMetrics()->ClassVerificationTotalTime()};

  int64_t previous_method_idx[2] = { -1, -1 };
  MethodVerifier::FailureData failure_data;
  ClassLinker* const linker = Runtime::Current()->GetClassLinker();

  for (const ClassAccessor::Method& method : accessor.GetMethods()) {
    int64_t* previous_idx = &previous_method_idx[method.IsStaticOrDirect() ? 0u : 1u];
    self->AllowThreadSuspension();
    const uint32_t method_idx = method.GetIndex();
    if (method_idx == *previous_idx) {
      // smali can create dex files with two encoded_methods sharing the same method_idx
      // http://code.google.com/p/smali/issues/detail?id=119
      continue;
    }
    *previous_idx = method_idx;
    std::string hard_failure_msg;
    MethodVerifier::FailureData result =
        MethodVerifier::VerifyMethod(self,
                                     linker,
                                     Runtime::Current()->GetArenaPool(),
                                     verifier_deps,
                                     method_idx,
                                     dex_file,
                                     dex_cache,
                                     class_loader,
                                     class_def,
                                     method.GetCodeItem(),
                                     method.GetAccessFlags(),
                                     log_level,
                                     api_level,
                                     Runtime::Current()->IsAotCompiler(),
                                     &hard_failure_msg);
    if (result.kind == FailureKind::kHardFailure) {
      if (failure_data.kind == FailureKind::kHardFailure) {
        // If we logged an error before, we need a newline.
        *error += "\n";
      } else {
        // If we didn't log a hard failure before, print the header of the message.
        *error += "Verifier rejected class ";
        *error += PrettyDescriptor(dex_file->GetClassDescriptor(class_def));
        *error += ":";
      }
      *error += " ";
      *error += hard_failure_msg;
    } else if (result.kind != FailureKind::kNoFailure) {
      UpdateMethodFlags(method.GetIndex(), klass, dex_cache, callbacks, result.types);
      if ((result.types & VerifyError::VERIFY_ERROR_LOCKING) != 0) {
        // Print a warning about expected slow-down.
        // Use a string temporary to print one contiguous warning.
        std::string tmp =
            StringPrintf("Method %s failed lock verification and will run slower.",
                         dex_file->PrettyMethod(method.GetIndex()).c_str());
        if (!gPrintedDxMonitorText) {
          tmp +=
              "\nCommon causes for lock verification issues are non-optimized dex code\n"
              "and incorrect proguard optimizations.";
          gPrintedDxMonitorText = true;
        }
        LOG(WARNING) << tmp;
      }
    }

    // Merge the result for the method into the global state for the class.
    failure_data.Merge(result);
  }
  uint64_t elapsed_time_microseconds = timer.Stop();
  VLOG(verifier) << "VerifyClass took " << PrettyDuration(UsToNs(elapsed_time_microseconds))
                 << ", class: " << PrettyDescriptor(dex_file->GetClassDescriptor(class_def));

  GetMetrics()->ClassVerificationCount()->AddOne();

  GetMetrics()->ClassVerificationTotalTimeDelta()->Add(elapsed_time_microseconds);
  GetMetrics()->ClassVerificationCountDelta()->AddOne();

  if (failure_data.kind == verifier::FailureKind::kHardFailure && callbacks != nullptr) {
    ClassReference ref(dex_file, dex_file->GetIndexForClassDef(class_def));
    callbacks->ClassRejected(ref);
  }

  return failure_data.kind;
}

}  // namespace verifier
}  // namespace art
