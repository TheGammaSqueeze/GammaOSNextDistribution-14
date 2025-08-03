/*
 * Copyright (C) 2013 The Android Open Source Project
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

#include <stdio.h>
#include <string.h>

#include "android-base/macros.h"

#include "jni.h"
#include "jvmti.h"

// Test infrastructure
#include "jvmti_helper.h"
#include "test_env.h"

namespace art {
namespace Test908GcStartFinish {

static size_t starts = 0;
static size_t finishes = 0;

static void JNICALL GarbageCollectionFinish([[maybe_unused]] jvmtiEnv* ti_env) {
  finishes++;
}

static void JNICALL GarbageCollectionStart([[maybe_unused]] jvmtiEnv* ti_env) {
  starts++;
}

extern "C" JNIEXPORT void JNICALL Java_art_Test908_setupGcCallback(
    JNIEnv* env, [[maybe_unused]] jclass klass) {
  jvmtiEventCallbacks callbacks;
  memset(&callbacks, 0, sizeof(jvmtiEventCallbacks));
  callbacks.GarbageCollectionFinish = GarbageCollectionFinish;
  callbacks.GarbageCollectionStart = GarbageCollectionStart;

  jvmtiError ret = jvmti_env->SetEventCallbacks(&callbacks, sizeof(callbacks));
  JvmtiErrorToException(env, jvmti_env, ret);
}

extern "C" JNIEXPORT void JNICALL Java_art_Test908_enableGcTracking(JNIEnv* env,
                                                                    [[maybe_unused]] jclass klass,
                                                                    jboolean enable) {
  jvmtiError ret = jvmti_env->SetEventNotificationMode(
      enable ? JVMTI_ENABLE : JVMTI_DISABLE,
      JVMTI_EVENT_GARBAGE_COLLECTION_START,
      nullptr);
  if (JvmtiErrorToException(env, jvmti_env, ret)) {
    return;
  }
  ret = jvmti_env->SetEventNotificationMode(
      enable ? JVMTI_ENABLE : JVMTI_DISABLE,
      JVMTI_EVENT_GARBAGE_COLLECTION_FINISH,
      nullptr);
  if (JvmtiErrorToException(env, jvmti_env, ret)) {
    return;
  }
}

extern "C" JNIEXPORT jint JNICALL Java_art_Test908_getGcStarts([[maybe_unused]] JNIEnv* env,
                                                               [[maybe_unused]] jclass klass) {
  jint result = static_cast<jint>(starts);
  starts = 0;
  return result;
}

extern "C" JNIEXPORT jint JNICALL Java_art_Test908_getGcFinishes([[maybe_unused]] JNIEnv* env,
                                                                 [[maybe_unused]] jclass klass) {
  jint result = static_cast<jint>(finishes);
  finishes = 0;
  return result;
}

}  // namespace Test908GcStartFinish
}  // namespace art
