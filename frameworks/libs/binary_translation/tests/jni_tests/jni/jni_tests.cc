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

#include <jni.h>

namespace {

bool g_jni_onload_called = false;

jint add42(JNIEnv* /* env */, jclass /* clazz */, jint x) {
  return x + 42;
}

}  // namespace

extern "C" {

jint JNI_OnLoad(JavaVM* /* vm */, void* /* reserved */) {
  g_jni_onload_called = true;
  return JNI_VERSION_1_6;
}

JNIEXPORT jint JNICALL Java_com_berberis_jnitests_JniTests_intFromJNI(JNIEnv* /* env */,
                                                                      jclass /* clazz */) {
  return 42;
}

JNIEXPORT jboolean JNICALL
Java_com_berberis_jnitests_JniTests_isJNIOnLoadCalled(JNIEnv* /* env */, jclass /* clazz */) {
  return g_jni_onload_called;
}

JNIEXPORT jboolean JNICALL Java_com_berberis_jnitests_JniTests_checkGetVersion(JNIEnv* env,
                                                                               jclass /* clazz */) {
  return env->GetVersion() == JNI_VERSION_1_6;
}

JNIEXPORT jboolean JNICALL
Java_com_berberis_jnitests_JniTests_checkJavaVMCorrespondsToJNIEnv(JNIEnv* env,
                                                                   jclass /* clazz */) {
  JavaVM* vm;
  if (env->GetJavaVM(&vm) != JNI_OK) {
    return false;
  }

  void* env_copy;
  if (vm->GetEnv(&env_copy, JNI_VERSION_1_6) != JNI_OK) {
    return false;
  }

  return env == env_copy;
}

JNIEXPORT jboolean JNICALL Java_com_berberis_jnitests_JniTests_callRegisterNatives(JNIEnv* env,
                                                                                   jclass clazz) {
  JNINativeMethod methods[] = {{"add42", "(I)I", reinterpret_cast<void*>(&add42)}};
  return env->RegisterNatives(clazz, methods, sizeof(methods) / sizeof(methods[1])) == JNI_OK;
}

JNIEXPORT jint JNICALL Java_com_berberis_jnitests_JniTests_callAdd(JNIEnv* env,
                                                                   jclass clazz,
                                                                   jint x,
                                                                   jint y) {
  jmethodID method_id = env->GetStaticMethodID(clazz, "add", "(II)I");
  // ATTENTION: JNIEnv converts Call*Method(...) to Call*MethodV(va_list)!
  return env->CallStaticIntMethod(clazz, method_id, x, y);
}

JNIEXPORT jint JNICALL Java_com_berberis_jnitests_JniTests_callAddA(JNIEnv* env,
                                                                    jclass clazz,
                                                                    jint x,
                                                                    jint y) {
  jmethodID method_id = env->GetStaticMethodID(clazz, "add", "(II)I");
  jvalue args[2];
  args[0].i = x;
  args[1].i = y;
  return env->CallStaticIntMethodA(clazz, method_id, args);
}

JNIEXPORT jint JNICALL Java_com_berberis_jnitests_JniTests_callCallIntFromJNI(JNIEnv* env,
                                                                              jclass clazz) {
  jmethodID method_id = env->GetStaticMethodID(clazz, "callIntFromJNI", "()I");
  return env->CallStaticIntMethod(clazz, method_id);
}

}  // extern "C"
