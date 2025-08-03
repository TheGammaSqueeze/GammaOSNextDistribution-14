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
 *
 */

#include <jni.h>

#include <android/binder_ibinder_jni.h>
#include <android/log.h>
#include <android/hardware_buffer.h>

#include <aidl/cts/android/hardware/BnHardwareBufferTestService.h>

#define LOG_TAG "HardwareBufferTest"

using ::ndk::SharedRefBase;
using ::ndk::SpAIBinder;
using ::aidl::android::hardware::HardwareBuffer;

class ServiceImpl : public ::aidl::cts::android::hardware::BnHardwareBufferTestService {
    static auto ok() {
        return ::ndk::ScopedAStatus(AStatus_newOk());
    }
    static auto error() {
        return ::ndk::ScopedAStatus(AStatus_fromStatus(STATUS_UNKNOWN_ERROR));
    }
public:
    ::ndk::ScopedAStatus getId(const HardwareBuffer& buffer, int64_t* outId) override {
        int err = AHardwareBuffer_getId(buffer.get(), reinterpret_cast<uint64_t*>(outId));
        if (err) {
            return error();
        }
        return ok();
    }
    ::ndk::ScopedAStatus createBuffer(int32_t width, int32_t height,
            HardwareBuffer* outBuffer) override {
        AHardwareBuffer* buffer = NULL;
        AHardwareBuffer_Desc desc = {};

        desc.width = width;
        desc.height = height;
        desc.layers = 1;
        desc.usage = AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN |  AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN;
        desc.format = AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM;
        int res = AHardwareBuffer_allocate(&desc, &buffer);

        if (res == 0) {
            outBuffer->reset(buffer);
            return ok();
        } else {
            return error();
        }
    }
};

static jobject makeNativeService(JNIEnv* env, jclass) {
  // The ref owns the MyTest, and the binder owns the ref.
  SpAIBinder binder = SharedRefBase::make<ServiceImpl>()->asBinder();
  // And the Java object owns the binder
  return AIBinder_toJavaBinder(env, binder.get());
}

static JNINativeMethod gMethods[] = {
    { "makeNativeService", "()Landroid/os/IBinder;", (void *) makeNativeService },
};

int register_android_hardware_cts_HardwareBufferTestService(JNIEnv* env)
{
    jclass clazz = env->FindClass("android/hardware/cts/HardwareBufferTestService");
    return env->RegisterNatives(clazz, gMethods,
            sizeof(gMethods) / sizeof(JNINativeMethod));
}
