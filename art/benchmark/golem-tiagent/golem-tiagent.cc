/*
 * Copyright (C) 2022 The Android Open Source Project
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
#include "android-base/logging.h"

#include "jni.h"
#include "jvmti.h"

namespace art {

jvmtiEnv* jvmti_env = nullptr;

void CheckJvmtiError(jvmtiEnv* env, jvmtiError error) {
  if (error != JVMTI_ERROR_NONE) {
    char* error_name;
    jvmtiError name_error = env->GetErrorName(error, &error_name);
    if (name_error != JVMTI_ERROR_NONE) {
      LOG(FATAL) << "Unable to get error name for " << error;
    }
    LOG(FATAL) << "Unexpected error: " << error_name;
  }
}

static void JNICALL VMInitCallback([[maybe_unused]] jvmtiEnv* jenv,
                                   JNIEnv* jni_env,
                                   [[maybe_unused]] jthread thread) {
  // Set a breakpoint on a rare method that we won't expect to be hit.
  // java.lang.Thread.stop is deprecated and not expected to be used.
  jclass cl = jni_env->FindClass("java/lang/Thread");
  if (cl == nullptr) {
    LOG(FATAL) << "Cannot find class java/lang/Thread to set a breakpoint";
  }

  jmethodID method = jni_env->GetMethodID(cl, "stop", "()V");
  if (method == nullptr) {
    LOG(FATAL) << "Cannot find method to set a breapoint";
  }

  jlong start = 0;
  jlong end;
  CheckJvmtiError(jvmti_env, jvmti_env->GetMethodLocation(method, &start, &end));
  CheckJvmtiError(jvmti_env, jvmti_env->SetBreakpoint(method, start));
}

extern "C" JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM* vm,
                                               [[maybe_unused]] char* options,
                                               [[maybe_unused]] void* reserved) {
  // Setup jvmti_env
  if (vm->GetEnv(reinterpret_cast<void**>(&jvmti_env), JVMTI_VERSION_1_0) != 0) {
    LOG(ERROR) << "Unable to get jvmti env!";
    return 1;
  }

  // Enable breakpoint capability
  jvmtiCapabilities capabilities;
  memset(&capabilities, 0, sizeof(capabilities));
  capabilities.can_generate_breakpoint_events = 1;
  CheckJvmtiError(jvmti_env, jvmti_env->AddCapabilities(&capabilities));

  // Set a callback for VM_INIT phase so we can set a breakpoint. We cannot just
  // set a breakpoint here since vm isn't fully initialized here.
  jvmtiEventCallbacks callbacks;
  memset(&callbacks, 0, sizeof(jvmtiEventCallbacks));
  callbacks.VMInit = VMInitCallback;
  CheckJvmtiError(jvmti_env, jvmti_env->SetEventCallbacks(&callbacks, sizeof(callbacks)));
  CheckJvmtiError(jvmti_env,
                  jvmti_env->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_VM_INIT, nullptr));

  return 0;
}

}  // namespace art
