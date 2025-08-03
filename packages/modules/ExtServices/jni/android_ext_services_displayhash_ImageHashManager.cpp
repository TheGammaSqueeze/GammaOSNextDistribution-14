/*
 * Copyright 2020 The Android Open Source Project
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

//#define LOG_NDEBUG 0
#define LOG_TAG "ImageHashManager"

#include <android/hardware_buffer_jni.h>
#include <log/log_main.h>
#include <nativehelper/JNIHelp.h>
#include <nativehelper/scoped_utf_chars.h>
#include <array>
#include <string>

#include <ImageHashManager.h>

namespace android {

class BufferWrapper {
public:
    BufferWrapper(AHardwareBuffer* buffer) : mBuffer(buffer) {}

    int lock(uint8_t** buf) {
        if (mIsLocked) {
            return -1;
        }

        int status = AHardwareBuffer_lock(mBuffer,
                                          AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN |
                                                  AHARDWAREBUFFER_USAGE_CPU_WRITE_NEVER,
                                          -1 /* fence */, nullptr /* rect */,
                                          reinterpret_cast<void**>(buf));
        if (!status) {
            mIsLocked = true;
        }
        return status;
    }

    void unlock() {
        if (!mIsLocked) {
            return;
        }
        mIsLocked = false;
        AHardwareBuffer_unlock(mBuffer, nullptr);
    }

    ~BufferWrapper() { unlock(); }

private:
    AHardwareBuffer* mBuffer = nullptr;
    bool mIsLocked = false;
};

static jbyteArray nativeGenerateHash(JNIEnv* env, jobject clazz, jobject hardwareBufferObj,
                                     jstring hashAlgorithmString) {
    ScopedUtfChars hashAlgorithmChars(env, hashAlgorithmString);
    std::string hashAlgorithm(hashAlgorithmChars.c_str());

    transform(hashAlgorithm.begin(), hashAlgorithm.end(), hashAlgorithm.begin(), ::tolower);

    AHardwareBuffer* hardwareBuffer = AHardwareBuffer_fromHardwareBuffer(env, hardwareBufferObj);

    AHardwareBuffer_Desc bufferDesc;
    AHardwareBuffer_describe(hardwareBuffer, &bufferDesc);

    BufferWrapper bufferWrapper(hardwareBuffer);
    uint8_t* buf = nullptr;
    int32_t status = bufferWrapper.lock(&buf);

    if (status) {
        ALOGE("Failed to lock buffer status=%d", status);
        return nullptr;
    }

    std::array<uint8_t, 8> imageHash;
    status = ImageHashManager::generateHash(hashAlgorithm, buf, bufferDesc, &imageHash);

    if (status) {
        ALOGE("Failed to generate hash status=%d", status);
        return nullptr;
    }

    jbyteArray ret = env->NewByteArray(8);
    if (ret) {
        jbyte* bytes = (jbyte*)env->GetPrimitiveArrayCritical(ret, 0);
        if (bytes) {
            memcpy(bytes, imageHash.data(), 8);
            env->ReleasePrimitiveArrayCritical(ret, bytes, 0);
        }
    }
    return ret;
}

// clang-format off
static const JNINativeMethod gMethods[] = {
    {"nativeGenerateHash", "(Landroid/hardware/HardwareBuffer;Ljava/lang/String;)[B",
                (void *)nativeGenerateHash},
};
// clang-format on

int register_android_ext_services_displayhash_DisplayHashAlgorithm(JNIEnv* env) {
    int res = jniRegisterNativeMethods(env, "android/ext/services/displayhash/ImageHashManager",
                                       gMethods, NELEM(gMethods));
    LOG_ALWAYS_FATAL_IF(res < 0, "Unable to register native methods.");

    return res;
}

} /* namespace android */
