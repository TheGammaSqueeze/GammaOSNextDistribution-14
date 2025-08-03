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

#include <android/binder_ibinder_jni.h>
#include <android/binder_manager.h>
#include <jni.h>
#include "nativehelper/JNIHelp.h"
#include <nativehelper/ScopedUtfChars.h>
#include <private/android_filesystem_config.h>

namespace android {
static jobject com_android_server_ServiceManagerWrapper_waitForService(
        JNIEnv* env, jobject clazz, jstring serviceName) {
    ScopedUtfChars name(env, serviceName);

// AServiceManager_waitForService is available on only 31+, but it's still safe for Thread
// service because it's enabled on only 34+
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wunguarded-availability"
    return AIBinder_toJavaBinder(env, AServiceManager_waitForService(name.c_str()));
#pragma clang diagnostic pop
}

/*
 * JNI registration.
 */

static const JNINativeMethod gMethods[] = {
        /* name, signature, funcPtr */
        {"nativeWaitForService",
         "(Ljava/lang/String;)Landroid/os/IBinder;",
         (void*)com_android_server_ServiceManagerWrapper_waitForService},
};

int register_com_android_server_ServiceManagerWrapper(JNIEnv* env) {
    return jniRegisterNativeMethods(env,
            "android/net/connectivity/com/android/server/ServiceManagerWrapper",
            gMethods, NELEM(gMethods));
}

};  // namespace android
