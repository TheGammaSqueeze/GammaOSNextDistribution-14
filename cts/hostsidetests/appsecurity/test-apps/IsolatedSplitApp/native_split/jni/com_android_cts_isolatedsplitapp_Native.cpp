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

#define LOG_TAG "IsolatedSplitApp"

#include <android/log.h>
#include <dlfcn.h>
#include <stdio.h>

#include "jni.h"

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

static jint add(JNIEnv* env, jobject thiz, jint numA, jint numB) {
    return numA + numB;
}

typedef int (*pFuncGetNumber)();

static jint get_number_from_other_library(const char* library_file_name,
                                          const char* function_name) {
    void* handle;
    char* error;
    handle = dlopen(library_file_name, RTLD_LAZY);
    if (!handle) {
        LOGE("Can't load %s: %s\n", library_file_name, dlerror());
        return -1;
    }
    pFuncGetNumber functionGetNumber = (pFuncGetNumber)dlsym(handle, function_name);
    if ((error = dlerror()) != NULL) {
        LOGE("Can't load function %s: %s\n", function_name, error);
        dlclose(handle);
        return -2;
    }
    int ret = functionGetNumber();
    dlclose(handle);

    return ret;
}

static jint get_number_a_via_proxy(JNIEnv* env, jobject thiz) {
    return get_number_from_other_library("libsplitapp_number_proxy.so", "get_number_a");
}

static jint get_number_b_via_proxy(JNIEnv* env, jobject thiz) {
    return get_number_from_other_library("libsplitapp_number_proxy.so", "get_number_b");
}

static jint get_number_a_from_provider(JNIEnv* env, jobject thiz) {
    return get_number_from_other_library("libsplitapp_number_provider_a.so", "get_number");
}

static jint get_number_b_from_provider(JNIEnv* env, jobject thiz) {
    return get_number_from_other_library("libsplitapp_number_provider_b.so", "get_number");
}

static const char* classPathName = "com/android/cts/isolatedsplitapp/Native";

static JNINativeMethod methods[] = {
        {"add", "(II)I", reinterpret_cast<void*>(add)},
        {"getNumberAViaProxy", "()I", reinterpret_cast<void**>(get_number_a_via_proxy)},
        {"getNumberBViaProxy", "()I", reinterpret_cast<void**>(get_number_b_via_proxy)},
        {"getNumberADirectly", "()I", reinterpret_cast<void**>(get_number_a_from_provider)},
        {"getNumberBDirectly", "()I", reinterpret_cast<void**>(get_number_b_from_provider)},
};

static int registerNativeMethods(JNIEnv* env, const char* className, JNINativeMethod* gMethods,
                                 int numMethods) {
    jclass clazz;

    clazz = env->FindClass(className);
    if (clazz == NULL) {
        LOGE("Native registration unable to find class '%s'", className);
        return JNI_FALSE;
    }
    if (env->RegisterNatives(clazz, gMethods, numMethods) < 0) {
        LOGE("RegisterNatives failed for '%s'", className);
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

static int registerNatives(JNIEnv* env) {
    if (!registerNativeMethods(env, classPathName, methods, sizeof(methods) / sizeof(methods[0]))) {
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    JNIEnv* env = NULL;

    LOGI("JNI_OnLoad %s", classPathName);

    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        LOGE("ERROR: GetEnv failed");
        return JNI_ERR;
    }

    if (registerNatives(env) != JNI_TRUE) {
        LOGE("ERROR: registerNatives failed");
        return JNI_ERR;
    }

    return JNI_VERSION_1_6;
}
