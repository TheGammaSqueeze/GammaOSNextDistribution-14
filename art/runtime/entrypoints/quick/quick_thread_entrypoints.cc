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

#include "callee_save_frame.h"
#include "jit/jit.h"
#include "runtime.h"
#include "thread-inl.h"

namespace art HIDDEN {

extern "C" void artDeoptimizeIfNeeded(Thread* self, uintptr_t result, bool is_ref)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  instrumentation::Instrumentation* instr = Runtime::Current()->GetInstrumentation();
  DCHECK(!self->IsExceptionPending());

  ArtMethod** sp = self->GetManagedStack()->GetTopQuickFrame();
  DCHECK(sp != nullptr && (*sp)->IsRuntimeMethod());

  DeoptimizationMethodType type = instr->GetDeoptimizationMethodType(*sp);
  JValue jvalue;
  jvalue.SetJ(result);
  instr->DeoptimizeIfNeeded(self, sp, type, jvalue, is_ref);
}

extern "C" void artTestSuspendFromCode(Thread* self) REQUIRES_SHARED(Locks::mutator_lock_) {
  // Called when there is a pending checkpoint or suspend request.
  ScopedQuickEntrypointChecks sqec(self);
  self->CheckSuspend();

  // We could have other dex instructions at the same dex pc as suspend and we need to execute
  // those instructions. So we should start executing from the current dex pc.
  ArtMethod** sp = self->GetManagedStack()->GetTopQuickFrame();
  JValue result;
  result.SetJ(0);
  Runtime::Current()->GetInstrumentation()->DeoptimizeIfNeeded(
      self, sp, DeoptimizationMethodType::kKeepDexPc, result, /* is_ref= */ false);
}

extern "C" void artImplicitSuspendFromCode(Thread* self) REQUIRES_SHARED(Locks::mutator_lock_) {
  // Called when there is a pending checkpoint or suspend request.
  ScopedQuickEntrypointChecks sqec(self);
  self->CheckSuspend(/*implicit=*/ true);

  // We could have other dex instructions at the same dex pc as suspend and we need to execute
  // those instructions. So we should start executing from the current dex pc.
  ArtMethod** sp = self->GetManagedStack()->GetTopQuickFrame();
  JValue result;
  result.SetJ(0);
  Runtime::Current()->GetInstrumentation()->DeoptimizeIfNeeded(
      self, sp, DeoptimizationMethodType::kKeepDexPc, result, /* is_ref= */ false);
}

extern "C" void artCompileOptimized(ArtMethod* method, Thread* self)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  ScopedQuickEntrypointChecks sqec(self);
  // It is important this method is not suspended due to:
  // * It is called on entry, and object parameters are in locations that are
  //   not marked in the stack map.
  // * Async deoptimization does not expect runtime methods other than the
  //   suspend entrypoint before executing the first instruction of a Java
  //   method.
  ScopedAssertNoThreadSuspension sants("Enqueuing optimized compilation");
  Runtime::Current()->GetJit()->EnqueueOptimizedCompilation(method, self);
}

}  // namespace art
