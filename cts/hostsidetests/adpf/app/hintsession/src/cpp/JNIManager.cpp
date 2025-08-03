/*
 * Copyright 2023 The Android Open Source Project
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

#include "JNIManager.h"

#include <mutex>
#include <vector>

#include "jni.h"

void JNIManager::sendResultsToJava(std::map<std::string, std::string> data) {
    JNIManager& manager = getInstance();
    JNIEnv* env = manager.AttachCurrentThread();
    jclass stringClass = env->FindClass("java/lang/String");
    jmethodID sendMethod = env->GetMethodID(manager.getMainActivityClass(), "sendResultsToJava",
                                            "([Ljava/lang/String;[Ljava/lang/String;)V");

    jobjectArray namesOut = env->NewObjectArray(data.size(), stringClass, 0);
    jobjectArray valuesOut = env->NewObjectArray(data.size(), stringClass, 0);

    int index = 0;
    for (auto&& item : data) {
        env->SetObjectArrayElement(namesOut, index, env->NewStringUTF(item.first.c_str()));
        env->SetObjectArrayElement(valuesOut, index, env->NewStringUTF(item.second.c_str()));
        ++index;
    }

    env->CallVoidMethod(manager.app_->activity->clazz, sendMethod, namesOut, valuesOut);
    manager.DetachCurrentThread();
}

void JNIManager::sendConfigToNative(JNIEnv* env, jobject, jobjectArray data) {
    std::lock_guard lock(getInstance().mutex_);
    size_t length = env->GetArrayLength(data);
    std::vector<std::string> out{};
    for (int i = 0; i < length; ++i) {
        jstring str = static_cast<jstring>(env->GetObjectArrayElement(data, i));
        const char* rawStr = env->GetStringUTFChars(str, 0);
        out.push_back({rawStr});
        env->ReleaseStringUTFChars(str, rawStr);
    }
    getInstance().testNames_.set_value(out);
}

std::vector<std::string> JNIManager::getTestNames() {
    return testNames_.get_future().get();
}

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void*) {
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    static jclass deviceActivityClass =
            env->FindClass("android/adpf/hintsession/app/ADPFHintSessionDeviceActivity");
    JNIManager::getInstance().setMainActivityClassGlobalRef(
            reinterpret_cast<jclass>(env->NewGlobalRef(deviceActivityClass)));

    if (deviceActivityClass == nullptr) return JNI_ERR;

    static const JNINativeMethod methods[] = {
            {"sendConfigToNative", "([Ljava/lang/String;)V",
             reinterpret_cast<void*>(JNIManager::sendConfigToNative)},

    };

    int rc = env->RegisterNatives(deviceActivityClass, methods,
                                  sizeof(methods) / sizeof(JNINativeMethod));
    if (rc != JNI_OK) return rc;

    return JNI_VERSION_1_6;
}
