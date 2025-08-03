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

#include <jni.h>
#include <nativehelper/JNIHelp.h>

#include "offload.h"

namespace android {

static jobjectArray getBpfCounterNames(JNIEnv *env) {
    size_t size = BPF_TETHER_ERR__MAX;
    jobjectArray ret = env->NewObjectArray(size, env->FindClass("java/lang/String"), nullptr);
    for (int i = 0; i < size; i++) {
        env->SetObjectArrayElement(ret, i, env->NewStringUTF(bpf_tether_errors[i]));
    }
    return ret;
}

/*
 * JNI registration.
 */
static const JNINativeMethod gMethods[] = {
    /* name, signature, funcPtr */
    { "getBpfCounterNames", "()[Ljava/lang/String;", (void*) getBpfCounterNames },
};

int register_com_android_networkstack_tethering_BpfCoordinator(JNIEnv* env) {
    return jniRegisterNativeMethods(env,
            "com/android/networkstack/tethering/BpfCoordinator",
            gMethods, NELEM(gMethods));
}

}; // namespace android
