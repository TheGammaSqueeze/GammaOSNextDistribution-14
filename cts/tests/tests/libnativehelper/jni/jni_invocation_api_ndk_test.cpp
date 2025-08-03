/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include "libnativehelper_test.h"

TEST_F(LibnativehelperTest, JNI_CreateJavaVM) {
    // Android only supports a single runtime, this is already running so no more can be created.
    JavaVMInitArgs vm_args;
    JavaVMOption options[] = {
        { .optionString = "-verbose:jni", .extraInfo = nullptr }
    };
    vm_args.version = JNI_VERSION_1_6;
    vm_args.nOptions = std::size(options);
    vm_args.options = options;
    vm_args.ignoreUnrecognized = JNI_TRUE;

    JavaVM* vm = nullptr;
    JNIEnv* env = nullptr;
    EXPECT_EQ(JNI_ERR, JNI_CreateJavaVM(&vm, &env, &vm_args));
    EXPECT_EQ(nullptr, vm);
    EXPECT_EQ(nullptr, env);
}

TEST_F(LibnativehelperTest, JNI_GetDefaultJavaVMInitArgs) {
    // JNI_GetDefaultJavaVMInitArgs is historically not supported by ART or Dalvik (at least as
    // far back as android-r2.2.3_r2).
    JavaVMInitArgs vm_args;
    vm_args.version = JNI_VERSION_1_6;
    vm_args.nOptions = 0;
    EXPECT_EQ(JNI_ERR, JNI_GetDefaultJavaVMInitArgs(&vm_args));
}

TEST_F(LibnativehelperTest, JNI_GetCreatedJavaVMs) {
    JavaVM* vmBuf[3];
    jsize nVMs;
    ASSERT_EQ(JNI_OK, JNI_GetCreatedJavaVMs(vmBuf, std::size(vmBuf), &nVMs));
    ASSERT_EQ(1, nVMs);
    ASSERT_NE(nullptr, vmBuf[0]);

    // Check caller can get the JNIEnv.
    jint versions[] = { JNI_VERSION_1_1, JNI_VERSION_1_2, JNI_VERSION_1_4, JNI_VERSION_1_6 };
    JNIEnv* env = nullptr;
    for (jint version : versions) {
        EXPECT_EQ(JNI_OK, vmBuf[0]->GetEnv(reinterpret_cast<void**>(&env), version))
            << " for JNI version " << std::hex << version;
        EXPECT_EQ(mEnv, env);
    }
}
