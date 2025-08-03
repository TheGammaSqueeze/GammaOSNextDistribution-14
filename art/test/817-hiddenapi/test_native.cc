/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include <android-base/logging.h>

#include "nativehelper/ScopedUtfChars.h"
#include "runtime.h"

namespace art {

extern "C" JNIEXPORT jint JNICALL Java_TestCase_testNativeInternal(JNIEnv* env,
                                                                   jclass) {
  jclass cls = env->FindClass("InheritAbstract");
  CHECK(cls != nullptr);
  jmethodID constructor = env->GetMethodID(cls, "<init>", "()V");
  CHECK(constructor != nullptr);
  jmethodID method_id = env->GetMethodID(cls, "methodPublicSdkNotInAbstractParent", "()I");
  if (method_id == nullptr) {
    return -1;
  }
  jobject obj = env->NewObject(cls, constructor);
  return env->CallIntMethod(obj, method_id);
}

extern "C" JNIEXPORT jboolean JNICALL Java_TestCase_testAccessInternal(JNIEnv* env,
                                                                       jclass,
                                                                       jclass cls,
                                                                       jstring method_name,
                                                                       jstring signature) {
  ScopedUtfChars chars_method(env, method_name);
  ScopedUtfChars chars_signature(env, signature);
  if (env->GetMethodID(cls, chars_method.c_str(), chars_signature.c_str()) != nullptr) {
    return true;
  }
  env->ExceptionClear();
  return false;
}

extern "C" JNIEXPORT void JNICALL Java_TestCase_dedupeHiddenApiWarnings(JNIEnv*, jclass) {
  Runtime::Current()->SetDedupeHiddenApiWarnings(true);
}

}  // namespace art
