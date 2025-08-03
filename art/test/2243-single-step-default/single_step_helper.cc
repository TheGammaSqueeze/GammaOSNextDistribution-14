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
#include "jni.h"
#include "jvmti.h"
#include "jvmti_helper.h"
#include "test_env.h"

namespace art {
namespace Test2243SingleStepDefault {

jmethodID interface_default_method;

static void singleStepCB(jvmtiEnv* jvmti,
                         JNIEnv* env,
                         jthread thr,
                         jmethodID method,
                         [[maybe_unused]] jlocation location) {
  // We haven't reached the default method yet. Continue single stepping
  if (method != interface_default_method) {
    return;
  }

  // Disable single stepping
  jvmtiError err = jvmti_env->SetEventNotificationMode(JVMTI_DISABLE, JVMTI_EVENT_SINGLE_STEP, thr);
  if (JvmtiErrorToException(env, jvmti_env, err)) {
    return;
  }

  // Inspect the frame.
  jint frame_count;
  if (JvmtiErrorToException(env, jvmti_env, jvmti_env->GetFrameCount(thr, &frame_count))) {
    return;
  }
  CHECK_GT(frame_count, 0);

  // Check that the method id from the stack frame is same as the one returned
  // by single step callback
  jmethodID m = nullptr;
  jlong loc = -1;
  if (JvmtiErrorToException(env, jvmti_env, jvmti_env->GetFrameLocation(thr, 0, &m, &loc))) {
    return;
  }
  CHECK_EQ(m, method) << "Method id from stack walk doesn't match id from single step callback";

  // Check that the method id is also present in the declaring class
  jclass klass = nullptr;
  if (JvmtiErrorToException(env, jvmti_env, jvmti_env->GetMethodDeclaringClass(m, &klass))) {
    return;
  }
  jint count = 0;
  jmethodID* methods = nullptr;
  jvmtiError result = jvmti_env->GetClassMethods(klass, &count, &methods);
  if (JvmtiErrorToException(env, jvmti_env, result)) {
    return;
  }

  bool found_method_id = false;
  for (int i = 0; i < count; i++) {
    if (methods[i] == method) {
      found_method_id = true;
      break;
    }
  }
  CHECK(found_method_id) << "Couldn't find the method id in the declaring class";

  // Check it isn't copied method.
  jint access_flags = 0;
  if (JvmtiErrorToException(env, jvmti, jvmti->GetMethodModifiers(m, &access_flags))) {
    return;
  }
  static constexpr uint32_t kAccCopied = 0x01000000;
  static constexpr uint32_t kAccIntrinsic = 0x80000000;
  bool is_copied = ((access_flags & (kAccIntrinsic | kAccCopied)) == kAccCopied);
  CHECK(!is_copied) << "Got copied methodID. Missed canonicalizing?\n";
}

extern "C" JNIEXPORT void JNICALL Java_art_Test2243_setSingleStepCallback(JNIEnv* env) {
  jvmtiEventCallbacks callbacks;
  memset(&callbacks, 0, sizeof(jvmtiEventCallbacks));
  callbacks.SingleStep = singleStepCB;

  jvmtiError ret = jvmti_env->SetEventCallbacks(&callbacks, sizeof(callbacks));
  JvmtiErrorToException(env, jvmti_env, ret);
}

extern "C" JNIEXPORT void JNICALL Java_art_Test2243_enableSingleStep(JNIEnv* env,
                                                                     [[maybe_unused]] jclass cl,
                                                                     jthread thr) {
  jvmtiError err = jvmti_env->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_SINGLE_STEP, thr);
  JvmtiErrorToException(env, jvmti_env, err);
}

extern "C" JNIEXPORT void JNICALL Java_art_Test2243_setSingleStepUntil(JNIEnv* env,
                                                                       [[maybe_unused]] jclass cl,
                                                                       jobject method) {
  interface_default_method = env->FromReflectedMethod(method);
}

}  // namespace Test2243SingleStepDefault
}  // namespace art
