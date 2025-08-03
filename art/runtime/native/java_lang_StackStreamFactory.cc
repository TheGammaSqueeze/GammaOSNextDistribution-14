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

#include "java_lang_StackStreamFactory.h"

#include "nativehelper/jni_macros.h"

#include "jni/jni_internal.h"
#include "native_util.h"
#include "scoped_fast_native_object_access-inl.h"
#include "thread.h"

namespace art HIDDEN {

static jobject StackStreamFactory_nativeGetStackAnchor(JNIEnv* env, jclass) {
  ScopedFastNativeObjectAccess soa(env);
  return soa.Self()->CreateInternalStackTrace(soa);
}

static jint StackStreamFactory_nativeFetchStackFrameInfo(JNIEnv* env, jclass,
    jlong mode, jobject anchor, jint startLevel, jint batchSize, jint startBufferIndex,
    jobjectArray frameBuffer) {
  if (anchor == nullptr) {
      return startLevel;
  }
  ScopedFastNativeObjectAccess soa(env);
  return Thread::InternalStackTraceToStackFrameInfoArray(soa, mode, anchor,
    startLevel, batchSize, startBufferIndex, frameBuffer);
}

static JNINativeMethod gMethods[] = {
  FAST_NATIVE_METHOD(StackStreamFactory, nativeGetStackAnchor, "()Ljava/lang/Object;"),
  FAST_NATIVE_METHOD(StackStreamFactory, nativeFetchStackFrameInfo, "(JLjava/lang/Object;III[Ljava/lang/Object;)I"),
};

void register_java_lang_StackStreamFactory(JNIEnv* env) {
  REGISTER_NATIVE_METHODS("java/lang/StackStreamFactory");
}

}  // namespace art
