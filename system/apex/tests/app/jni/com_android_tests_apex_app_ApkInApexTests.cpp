/*
 * Copyright 2022 The Android Open Source Project
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
 *
 */

#include <jni.h>

namespace {

bool fakeMethod() { return true; }

jint com_android_tests_apex_app_nativeFakeMethod(JNIEnv * /*env*/,
                                                 jclass /*clazz*/) {
  return (jboolean)fakeMethod();
}

static JNINativeMethod gMethods[] = {
    {"nativeFakeMethod", "()Z",
     (void *)com_android_tests_apex_app_nativeFakeMethod}};

} // anonymous namespace

int register_android_native_code_test_data(JNIEnv *env) {
  jclass clazz = env->FindClass("com/android/tests/apex/app/ApkInApexTests");
  return env->RegisterNatives(clazz, gMethods,
                              sizeof(gMethods) / sizeof(JNINativeMethod));
}

jint JNI_OnLoad(JavaVM *vm, void * /*reserved*/) {
  JNIEnv *env = nullptr;
  if (vm->GetEnv((void **)&env, JNI_VERSION_1_6) != JNI_OK)
    return JNI_ERR;
  if (register_android_native_code_test_data(env))
    return JNI_ERR;
  return JNI_VERSION_1_6;
}
