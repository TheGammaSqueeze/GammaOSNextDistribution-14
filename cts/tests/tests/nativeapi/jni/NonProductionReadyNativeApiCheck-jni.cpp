/*
 * Copyright (C) 2024 The Android Open Source Project
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

#include <dlfcn.h>
#include <jni.h>
#include <string.h>

extern "C" {

JNIEXPORT jobjectArray JNICALL Java_android_nativeapi_cts_NonProductionReadyNativeApiCheck_checkNonProductionReadyNativeApis(
    JNIEnv* env, jobject obj, jobjectArray nonProductionReadyApis)
{
    int size = env->GetArrayLength(nonProductionReadyApis);
    jobjectArray ret = (jobjectArray)env->NewObjectArray(
        size,
        env->FindClass("java/lang/String"),
        env->NewStringUTF(""));

    for (int i = 0; i < size; i++) {
        jstring string = (jstring) env->GetObjectArrayElement(nonProductionReadyApis, i);
        const char* api = env->GetStringUTFChars(string, 0);
        if (dlsym(RTLD_DEFAULT, api) != nullptr) {
            env->SetObjectArrayElement(
                ret, i, env->NewStringUTF(api));
        } else {
            env->SetObjectArrayElement(
                ret, i, env->NewStringUTF(""));
        }
        env->ReleaseStringUTFChars(string, api);
        env->DeleteLocalRef(string);
    }

    return ret;
}

}
