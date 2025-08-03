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

#include <android/file_descriptor_jni.h>

#include "libnativehelper_test.h"

TEST_F(LibnativehelperTest, FileDescriptorNdkApiUses) {
    static constexpr int kInvalidFd = -1;
    jobject jifd = AFileDescriptor_create(mEnv);
    ASSERT_NE(nullptr, jifd);
    ASSERT_EQ(JNI_FALSE, mEnv->ExceptionCheck());
    ASSERT_EQ(kInvalidFd, AFileDescriptor_getFd(mEnv, jifd));

    static const int kSubsequentUnixFds[] = { 0, -1, -999, -1, 0, 1812, -1, 1066 };
    for (int unixFd : kSubsequentUnixFds) {
        AFileDescriptor_setFd(mEnv, jifd, unixFd);
        ASSERT_EQ(JNI_FALSE, mEnv->ExceptionCheck());
        ASSERT_EQ(unixFd, AFileDescriptor_getFd(mEnv, jifd));
        ASSERT_EQ(JNI_FALSE, mEnv->ExceptionCheck());
    }
}
