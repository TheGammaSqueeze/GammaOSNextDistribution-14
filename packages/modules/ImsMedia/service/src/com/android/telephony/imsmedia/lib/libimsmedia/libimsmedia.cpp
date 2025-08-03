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

#define LOG_TAG "libimsmediajni"

#include <stdio.h>
#include <assert.h>
#include <utils/Log.h>
#include <nativehelper/JNIHelp.h>

#define IMS_MEDIA_JNI_VERSION JNI_VERSION_1_4

extern jint ImsMediaServiceJni_OnLoad(JavaVM* vm, JNIEnv* env);

jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    (void)reserved;

    ALOGD("JNI_OnLoad::JNI_OnLoad");

    JNIEnv* env = nullptr;

    if (vm->GetEnv((void**)&env, IMS_MEDIA_JNI_VERSION) != JNI_OK)
    {
        ALOGE("JNI_OnLoad::GetEnv failed");
        return (-1);
    }

    assert(env != nullptr);

    if (ImsMediaServiceJni_OnLoad(vm, env) < 0)
    {
        ALOGE("JNI_OnLoad::ImsMediaServiceJni_OnLoad failed");
        return (-1);
    }

    return IMS_MEDIA_JNI_VERSION;
}
