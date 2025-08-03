/*
 * Copyright (C) 2017 The Android Open Source Project
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

#include <android/binder_parcel.h>
#include <android/binder_parcel_jni.h>
#include <android/hardware_buffer.h>
#include <android/hardware_buffer_jni.h>
#include <android/hardware_buffer_aidl.h>

#include <nativehelper/JNIHelp.h>

#define LOG_TAG "HardwareBufferTest"

static jobject android_hardware_HardwareBuffer_nativeCreateHardwareBuffer(JNIEnv* env, jclass,
        jint width, jint height, jint format, jint layers, jlong usage) {
    AHardwareBuffer* buffer = NULL;
    AHardwareBuffer_Desc desc = {};

    desc.width = width;
    desc.height = height;
    desc.layers = layers;
    desc.usage = usage;
    desc.format = format;
    int res = AHardwareBuffer_allocate(&desc, &buffer);

    // TODO: Change this to res == NO_ERROR after b/77153085 is fixed
    if (res == 0) {
        jobject ret = AHardwareBuffer_toHardwareBuffer(env, buffer);
        AHardwareBuffer_release(buffer);
        return ret;
    } else {
        return 0;
    }
}

static jobject android_hardware_HardwareBuffer_nativeReadHardwareBuffer(JNIEnv* env, jclass,
        jobject parcelObj) {
    AParcel* parcel = AParcel_fromJavaParcel(env, parcelObj);
    if (!parcel) {
        jniThrowExceptionFmt(env, "android/os/BadParcelableException",
                                "null parcel");
        return nullptr;
    }
    AHardwareBuffer* buffer;
    binder_status_t status = AHardwareBuffer_readFromParcel(parcel, &buffer);
    AParcel_delete(parcel);

    if (status != STATUS_OK) {
        jniThrowExceptionFmt(env, "android/os/BadParcelableException",
                        "Failed to readFromParcel, status %d (%s)", status, strerror(-status));
        return nullptr;
    }
    jobject ret = AHardwareBuffer_toHardwareBuffer(env, buffer);
    AHardwareBuffer_release(buffer);
    return ret;
}

static void android_hardware_HardwareBuffer_nativeWriteHardwareBuffer(JNIEnv* env, jclass,
        jobject hardwareBufferObj, jobject parcelObj) {
    AHardwareBuffer* buffer = AHardwareBuffer_fromHardwareBuffer(env, hardwareBufferObj);
    AParcel* parcel = AParcel_fromJavaParcel(env, parcelObj);
    binder_status_t status = AHardwareBuffer_writeToParcel(buffer, parcel);
    AParcel_delete(parcel);
    if (status != STATUS_OK) {
        jniThrowExceptionFmt(env, "android/os/BadParcelableException",
                        "Failed to writeToParcel, status %d (%s)", status, strerror(-status));
    }
}

static JNINativeMethod gMethods[] = {
    { "nativeCreateHardwareBuffer", "(IIIIJ)Landroid/hardware/HardwareBuffer;",
            (void *) android_hardware_HardwareBuffer_nativeCreateHardwareBuffer },
    { "nativeReadHardwareBuffer", "(Landroid/os/Parcel;)Landroid/hardware/HardwareBuffer;",
           (void *) android_hardware_HardwareBuffer_nativeReadHardwareBuffer },
    { "nativeWriteHardwareBuffer", "(Landroid/hardware/HardwareBuffer;Landroid/os/Parcel;)V",
           (void *) android_hardware_HardwareBuffer_nativeWriteHardwareBuffer },
};

int register_android_hardware_cts_HardwareBufferTest(JNIEnv* env)
{
    jclass clazz = env->FindClass("android/hardware/cts/HardwareBufferTest");
    return env->RegisterNatives(clazz, gMethods,
            sizeof(gMethods) / sizeof(JNINativeMethod));
}
