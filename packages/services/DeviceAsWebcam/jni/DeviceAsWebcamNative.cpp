/*
 * Copyright (C) 2023 The Android Open Source Project
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

#define LOG_TAG "DeviceAsWebcamNative"

#include <DeviceAsWebcamServiceManager.h>
#include <log/log.h>
#include <nativehelper/JNIHelp.h>

#include "DeviceAsWebcamNative.h"

/**
 * Called by the JVM when JNI is loaded. This does not imply that the service is actually running,
 * only that the jni library is loaded.
 */
jint JNI_OnLoad(JavaVM* jvm, void*) {
    using namespace android::webcam;
    JNIEnv* e = NULL;
    if (jvm->GetEnv((void**)&e, JNI_VERSION_1_6)) {
        return JNI_ERR;
    }
    if (DeviceAsWebcamNative::registerJNIMethods(e, jvm)) {
        return JNI_ERR;
    }
    return JNI_VERSION_1_6;
}

namespace android {
namespace webcam {

// These will be initialized by registerJNIMethods
JavaMethods DeviceAsWebcamNative::kJavaMethods = {};
JavaVM* DeviceAsWebcamNative::kJVM = nullptr;

static inline jclass FindClassOrDie(JNIEnv* env, const char* class_name) {
    jclass clazz = env->FindClass(class_name);
    LOG_ALWAYS_FATAL_IF(clazz == nullptr, "Unable to find class %s", class_name);
    return clazz;
}

static inline jmethodID GetMethodIdOrDie(JNIEnv* env, jclass clazz, const char* method_name,
                                         const char* method_signature) {
    jmethodID res = env->GetMethodID(clazz, method_name, method_signature);
    LOG_ALWAYS_FATAL_IF(res == nullptr, "Unable to find method %s with signature %s", method_name,
                        method_signature);
    return res;
}

const JNINativeMethod DeviceAsWebcamNative::sMethods[] = {
        {"setupServicesAndStartListeningNative", "([Ljava/lang/String;)I",
         (void*)com_android_DeviceAsWebcam_setupServicesAndStartListening},
        {"nativeOnDestroy", "()V", (void*)com_android_DeviceAsWebcam_onDestroy},
        {"shouldStartServiceNative", "([Ljava/lang/String;)Z",
         (void*)com_android_DeviceAsWebcam_shouldStartService},
        {"nativeEncodeImage", "(Landroid/hardware/HardwareBuffer;JI)I",
         (void*)com_android_DeviceAsWebcam_encodeImage},
};

int DeviceAsWebcamNative::registerJNIMethods(JNIEnv* e, JavaVM* jvm) {
    char clsName[] = "com/android/DeviceAsWebcam/DeviceAsWebcamFgService";
    int ret = jniRegisterNativeMethods(e, clsName, sMethods, NELEM(sMethods));
    if (ret) {
        return JNI_ERR;
    }

    jclass clazz = FindClassOrDie(e, clsName);
    kJavaMethods.setStreamConfig = GetMethodIdOrDie(e, clazz, "setStreamConfig", "(ZIII)V");
    kJavaMethods.startStreaming = GetMethodIdOrDie(e, clazz, "startStreaming", "()V");
    kJavaMethods.stopStreaming = GetMethodIdOrDie(e, clazz, "stopStreaming", "()V");
    kJavaMethods.stopService = GetMethodIdOrDie(e, clazz, "stopService", "()V");
    kJavaMethods.returnImage = GetMethodIdOrDie(e, clazz, "returnImage", "(J)V");

    kJVM = jvm;
    return 0;
}

jint DeviceAsWebcamNative::com_android_DeviceAsWebcam_encodeImage(JNIEnv* env, jobject,
                                                                  jobject hardwareBuffer,
                                                                  jlong timestamp,
                                                                  jint rotation) {
    return DeviceAsWebcamServiceManager::kInstance->encodeImage(env, hardwareBuffer, timestamp,
                                                                rotation);
}

jint DeviceAsWebcamNative::com_android_DeviceAsWebcam_setupServicesAndStartListening(
        JNIEnv* env, jobject thiz, jobjectArray jIgnoredNodes) {
    return DeviceAsWebcamServiceManager::kInstance->setupServicesAndStartListening(env, thiz,
                                                                                   jIgnoredNodes);
}

jboolean DeviceAsWebcamNative::com_android_DeviceAsWebcam_shouldStartService(
        JNIEnv*, jclass, jobjectArray jIgnoredNodes) {
    return DeviceAsWebcamServiceManager::kInstance->shouldStartService(jIgnoredNodes);
}

void DeviceAsWebcamNative::com_android_DeviceAsWebcam_onDestroy(JNIEnv*, jobject) {
    DeviceAsWebcamServiceManager::kInstance->onDestroy();
}

void DeviceAsWebcamNative::setStreamConfig(jobject thiz, bool mjpeg, uint32_t width,
                                           uint32_t height, uint32_t fps) {
    JNIEnv* env = getJNIEnvOrAbort();
    jboolean jMjpeg = mjpeg ? JNI_TRUE : JNI_FALSE;
    jint jWidth = static_cast<jint>(width);
    jint jHeight = static_cast<jint>(height);
    jint jFps = static_cast<jint>(fps);
    env->CallVoidMethod(thiz, kJavaMethods.setStreamConfig, jMjpeg, jWidth, jHeight, jFps);
}

void DeviceAsWebcamNative::startStreaming(jobject thiz) {
    JNIEnv* env = getJNIEnvOrAbort();
    env->CallVoidMethod(thiz, kJavaMethods.startStreaming);
}
void DeviceAsWebcamNative::stopStreaming(jobject thiz) {
    JNIEnv* env = getJNIEnvOrAbort();
    env->CallVoidMethod(thiz, kJavaMethods.stopStreaming);
}

void DeviceAsWebcamNative::returnImage(jobject thiz, long timestamp) {
    JNIEnv* env = getJNIEnvOrAbort();
    jlong jTimestamp = static_cast<jlong>(timestamp);
    env->CallVoidMethod(thiz, kJavaMethods.returnImage, jTimestamp);
}

void DeviceAsWebcamNative::stopService(jobject thiz) {
    JNIEnv* env = getJNIEnvOrAbort();
    env->CallVoidMethod(thiz, kJavaMethods.stopService);
}

JNIEnv* DeviceAsWebcamNative::getJNIEnvOrAbort() {
    JNIEnv* env = nullptr;
    kJVM->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    if (env == nullptr) {
        ALOGE("%s: Called from a thread not bound to the JVM", __FUNCTION__);
        // Call abort() to force creation of a tombstone for easier debugging.
        abort();  // :(
    }
    return env;
}

}  // namespace webcam
}  // namespace android