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
 *
 */

#include <android/input.h>
#include <jni.h>
#include <nativehelper/ScopedLocalFrame.h>
#include <nativehelper/ScopedLocalRef.h>
#include <nativehelper/ScopedUtfChars.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <map>
#include <sstream>
#include <unordered_map>
#include <vector>

#define LOG_TAG "InputDeviceKeyLayoutMapTest"

namespace android {

// Loads Generic.kl file and returns it as a std::map from scancode to key code.
std::map<int, std::string> loadGenericKl(std::string genericKl) {
    std::map<int, std::string> result;
    std::istringstream ssFile(genericKl);

    for (std::string line; std::getline(ssFile, line);) {
        if (line.empty() || line[0] == '#') {
            // Skip the comment lines
            continue;
        }

        std::string type, code, label, flags;
        std::istringstream ssLine(line);
        ssLine >> type >> code >> label >> flags;

        // Skip non-key mappings.
        if (type != "key") {
            continue;
        }

        // Skip HID usage keys.
        if (code == "usage") {
            continue;
        }

        // Skip keys with flags like "FUNCTION"
        if (!flags.empty()) {
            continue;
        }

        result.emplace(std::stoi(code), label);
    }

    return result;
}

static jobject android_view_cts_nativeLoadKeyLayout(JNIEnv* env, jclass, jstring genericKl) {
    ScopedUtfChars keyLayout(env, genericKl);
    if (keyLayout.c_str() == nullptr) {
        return nullptr;
    }
    std::map<int, std::string> map = loadGenericKl(keyLayout.c_str());

    ScopedLocalRef<jclass> hashMapClazz(env, env->FindClass("java/util/HashMap"));

    jmethodID hashMapConstructID = env->GetMethodID(hashMapClazz.get(), "<init>", "()V");

    jmethodID hashMapPutID =
            env->GetMethodID(hashMapClazz.get(), "put",
                             "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");

    ScopedLocalRef<jclass> integerClazz(env, env->FindClass("java/lang/Integer"));

    jmethodID integerConstructID = env->GetMethodID(integerClazz.get(), "<init>", "(I)V");

    jobject keyLayoutMap = env->NewObject(hashMapClazz.get(), hashMapConstructID);

    {
        ScopedLocalFrame localFrame(env);
        for (const auto& [key, label] : map) {
            env->CallObjectMethod(keyLayoutMap, hashMapPutID, env->NewStringUTF(label.c_str()),
                                  env->NewObject(integerClazz.get(), integerConstructID, key));
        }
    }
    return keyLayoutMap;
}

} // namespace android

static JNINativeMethod gMethods[] = {
        {"nativeLoadKeyLayout", "(Ljava/lang/String;)Ljava/util/Map;",
         (void*)android::android_view_cts_nativeLoadKeyLayout},
};

int register_android_view_cts_InputDeviceKeyLayoutMapTest(JNIEnv* env) {
    jclass clazz = env->FindClass("android/view/cts/input/InputDeviceKeyLayoutMapTest");
    return env->RegisterNatives(clazz, gMethods, sizeof(gMethods) / sizeof(JNINativeMethod));
}
