/*
 * Copyright (C) 2014 The Android Open Source Project
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

#include "gtest/gtest.h"

#include <jni.h>

// Simple testing framework for binary translation.
#include "jni_test_main.h"  // NOLINT

//------------------------------------------------------------------------------
// Test JNI_OnLoad call.

static bool gJNIOnLoadCalled = false;

jint JNI_OnLoad(JavaVM*, void*) {
  gJNIOnLoadCalled = true;
  return JNI_VERSION_1_2;
}

TEST(JNI, OnLoad) {
  EXPECT_TRUE(gJNIOnLoadCalled);
}

JNIEnv* gEnv;
jobject gObj;

bool test_Ellipsis_Real(JNIEnv* env, jobject obj) {
  jclass java_class = env->GetObjectClass(obj);
  jmethodID arg_test_method = env->GetMethodID(java_class, "jniArgTest", "(JIJIIJ)Z");
  jboolean res = env->CallBooleanMethod(obj, arg_test_method, 1LL, 2, 3LL, 4, 5, 6LL);

  jmethodID arg_float_test_method = env->GetMethodID(java_class, "jniFloatArgTest", "(FIFIIF)Z");
  res &= env->CallBooleanMethod(obj, arg_float_test_method, 1.0f, 2, 3.0f, 4, 5, 6.0f);

  res &= env->CallNonvirtualBooleanMethod(obj, java_class, arg_test_method, 1LL, 2, 3LL, 4, 5, 6LL);
  return res;
}

// We call test_Ellipsis_Real inside these 2 functions in order to test
// ellipsis calls when stack is aligned to 8 bytes and when it is not.

bool __attribute__((noinline)) test_Ellipsis_F1(JNIEnv* env, jobject obj) {
  return test_Ellipsis_Real(env, obj);
}

bool __attribute__((noinline)) test_Ellipsis_F2(JNIEnv* env, jobject obj, int /* arg1 */) {
  return test_Ellipsis_Real(env, obj);
}

TEST(JNI, Ellipsis) {
  EXPECT_TRUE(test_Ellipsis_F1(gEnv, gObj));
  EXPECT_TRUE(test_Ellipsis_F2(gEnv, gObj, 0));
}

static jint return42(JNIEnv*, jobject) {
  return 42;
}

jint callJavaIntReturningMethod(const char* method) {
  jclass clazz = gEnv->GetObjectClass(gObj);
  jmethodID caller_method = gEnv->GetMethodID(clazz, method, "()I");
  return gEnv->CallIntMethod(gObj, caller_method);
}

TEST(JNI, RegisterNatives) {
  JNINativeMethod methods[] = {{"return42", "()I", reinterpret_cast<void*>(&return42)}};
  jclass clazz = gEnv->GetObjectClass(gObj);
  gEnv->RegisterNatives(clazz, methods, sizeof(methods) / sizeof(methods[1]));
  EXPECT_EQ(42, callJavaIntReturningMethod("callReturn42"));
}

// See comment for NdkTests.wrappersABITest in java part.
TEST(JNI, WrappersABI) {
  jclass java_class = gEnv->GetObjectClass(gObj);
  jmethodID wrappers_abi_test_method = gEnv->GetMethodID(java_class, "wrappersABITest", "()Z");
  EXPECT_TRUE(gEnv->CallBooleanMethod(gObj, wrappers_abi_test_method));
}

//------------------------------------------------------------------------------

extern "C" jint Java_com_example_ndk_1tests_NdkTests_returnInt(JNIEnv*, jobject, jint arg) {
  return arg;
}

extern "C" jfloat Java_com_example_ndk_1tests_NdkTests_returnFloat(JNIEnv*, jobject, jfloat arg)
#ifdef __arm__
    // On ARM with 'softfp' these functions are binary interchangeable.
    __attribute__((alias("Java_com_example_ndk_1tests_NdkTests_returnInt")));
#else
{
  return arg;
}
#endif

extern "C" jint Java_com_example_ndk_1tests_NdkTests_runTests(JNIEnv* env,
                                                              jobject thiz,
                                                              jstring gtest_list,
                                                              jstring gtest_filter) {
  gEnv = env;
  gObj = thiz;

  int result = ndk_test::RunAllTests(env, thiz, gtest_list, gtest_filter);

  gEnv = NULL;
  gObj = NULL;
  return result;
}
