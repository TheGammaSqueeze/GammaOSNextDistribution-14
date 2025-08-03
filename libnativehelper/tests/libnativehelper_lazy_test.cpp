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

#include "../libnativehelper_lazy.h"

#include <gtest/gtest.h>
#include "jni.h"

#include "nativehelper/JniInvocation.h"
#include "nativehelper/JNIHelp.h"
#include "nativehelper/JNIPlatformHelp.h"

// The tests here are just for the case when libnativehelper.so cannot be loaded by
// libnativehelper_lazy.
class LibnativehelperLazyTest : public ::testing::Test {
  protected:
    virtual void SetUp() {
      ::testing::Test::SetUp();
      PreventLibnativehelperLazyLoadingForTests();
    }
};

static const char* kLoadFailed = "Failed to load libnativehelper.so";

TEST_F(LibnativehelperLazyTest, NoLibnativehelperIsForJNIPlatformHelp) {
  C_JNIEnv* env = NULL;
  EXPECT_DEATH(jniGetNioBufferBaseArray(env, NULL), kLoadFailed);
  EXPECT_DEATH(jniGetNioBufferBaseArrayOffset(env, NULL), kLoadFailed);
  EXPECT_DEATH(jniGetNioBufferFields(env, NULL, NULL, NULL, NULL), kLoadFailed);
  EXPECT_DEATH(jniGetNioBufferPointer(env, NULL), kLoadFailed);
  EXPECT_DEATH(jniUninitializeConstants(), kLoadFailed);
}

TEST_F(LibnativehelperLazyTest, NoLibnativehelperIsForJniInvocation) {
  EXPECT_DEATH(JniInvocationCreate(), kLoadFailed);
  EXPECT_DEATH(JniInvocationDestroy(NULL), kLoadFailed);
  EXPECT_DEATH(JniInvocationGetLibrary("a", NULL), kLoadFailed);
  EXPECT_DEATH(JniInvocationInit(NULL, "a"), kLoadFailed);
}

TEST_F(LibnativehelperLazyTest, NoLibnativehelperIsForJniApi) {
  PreventLibnativehelperLazyLoadingForTests();

  JavaVM* vm = NULL;
  JNIEnv* env = NULL;
  jsize count = 0;

  EXPECT_DEATH(JNI_GetDefaultJavaVMInitArgs(NULL), kLoadFailed);
  EXPECT_DEATH(JNI_CreateJavaVM(&vm, &env, NULL), kLoadFailed);
  EXPECT_EQ(JNI_OK, JNI_GetCreatedJavaVMs(&vm, 1, &count));
  EXPECT_EQ(0, count);
}
