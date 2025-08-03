/*
 * Copyright (C) 2016 The Android Open Source Project
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

#include "jni.h"

#include "gc/gc_cause.h"
#include "gc/scoped_gc_critical_section.h"
#include "mirror/class-inl.h"
#include "runtime.h"
#include "scoped_thread_state_change-inl.h"
#include "thread_list.h"
#include "thread_state.h"

namespace art {

extern "C" JNIEXPORT void JNICALL Java_Main_deoptimizeAll(
    JNIEnv* env,
    [[maybe_unused]] jclass cls) {
  ScopedObjectAccess soa(env);
  ScopedThreadSuspension sts(Thread::Current(), ThreadState::kWaitingForDeoptimization);
  gc::ScopedGCCriticalSection gcs(Thread::Current(),
                                  gc::kGcCauseInstrumentation,
                                  gc::kCollectorTypeInstrumentation);
  // We need to suspend mutator threads first.
  ScopedSuspendAll ssa(__FUNCTION__);
  Runtime::Current()->GetInstrumentation()->DeoptimizeEverything("test");
}

extern "C" JNIEXPORT void JNICALL Java_Main_undeoptimizeAll(
    JNIEnv* env,
    [[maybe_unused]] jclass cls) {
  ScopedObjectAccess soa(env);
  ScopedThreadSuspension sts(Thread::Current(), ThreadState::kWaitingForDeoptimization);
  gc::ScopedGCCriticalSection gcs(Thread::Current(),
                                  gc::kGcCauseInstrumentation,
                                  gc::kCollectorTypeInstrumentation);
  // We need to suspend mutator threads first.
  ScopedSuspendAll ssa(__FUNCTION__);
  Runtime::Current()->GetInstrumentation()->UndeoptimizeEverything("test");
}

}  // namespace art
