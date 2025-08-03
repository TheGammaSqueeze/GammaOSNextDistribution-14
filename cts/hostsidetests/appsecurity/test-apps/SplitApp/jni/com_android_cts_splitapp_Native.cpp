/*
 * Copyright (C) 2014 The Android Open Source Project
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

#define LOG_TAG "SplitApp"

#include <android/log.h>
#include <stdio.h>
#include <dlfcn.h>

#include "jni.h"

#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)

typedef int (*pFuncGetNumber)();

static jint get_number_from_other_library(
        const char* library_file_name, const char* function_name) {
    void *handle;
    char *error;
    handle = dlopen (library_file_name, RTLD_LAZY);
    if (!handle) {
        LOGE("Can't load %s: %s\n", library_file_name, dlerror());
        return -1;
    }
    pFuncGetNumber functionGetNumber = (pFuncGetNumber) dlsym(handle, function_name);
    if ((error = dlerror()) != NULL)  {
        LOGE("Can't load function %s: %s\n", function_name, error);
        dlclose(handle);
        return -2;
    }
    int ret = functionGetNumber();
    dlclose(handle);

    return ret;
}

static jint get_number_a_via_proxy(JNIEnv *env, jobject thiz) {
    return get_number_from_other_library("libsplitapp_number_proxy.so", "get_number_a");
}

static jint get_number_b_via_proxy(JNIEnv *env, jobject thiz) {
    return get_number_from_other_library("libsplitapp_number_proxy.so", "get_number_b");
}

static jint get_number_a_from_provider(JNIEnv *env, jobject thiz) {
    return get_number_from_other_library("libsplitapp_number_provider_a.so", "get_number");
}

static jint get_number_b_from_provider(JNIEnv *env, jobject thiz) {
    return get_number_from_other_library("libsplitapp_number_provider_b.so", "get_number");
}

#ifdef __LIVE_ONLY_32BIT__
#define ABI_BITNESS 32
#else // __LIVE_ONLY_32BIT__
#define ABI_BITNESS 64
#endif // __LIVE_ONLY_32BIT__

static jint get_abi_bitness(JNIEnv* env, jobject thiz) {
    return ABI_BITNESS;
}

static jint add(JNIEnv *env, jobject thiz, jint a, jint b) {
    int result = a + b;
    LOGI("%d + %d = %d", a, b, result);
    return result;
}

static jstring arch(JNIEnv *env, jobject thiz) {
    return env->NewStringUTF(__ANDROID_ARCH__);
}

static jint sub(JNIEnv* env, jobject thiz, jint a, jint b) {
#ifdef __REVISION_HAVE_SUB__
    int result = a - b;
    LOGI("%d - %d = %d", a, b, result);
    return result;
#else  // __REVISION_HAVE_SUB__
    LOGI("Implement sub badly, just return 0");
    return 0;
#endif // __REVISION_HAVE_SUB__
}

static const char *classPathName = "com/android/cts/splitapp/Native";

static JNINativeMethod methods[] = {
        {"getAbiBitness", "()I", (void*)get_abi_bitness},
        {"add", "(II)I", (void*)add},
        {"arch", "()Ljava/lang/String;", (void*)arch},
        {"sub", "(II)I", (void*)sub},
        {"getNumberAViaProxy", "()I", (void*) get_number_a_via_proxy},
        {"getNumberBViaProxy", "()I", (void*) get_number_b_via_proxy},
        {"getNumberADirectly", "()I", (void*) get_number_a_from_provider},
        {"getNumberBDirectly", "()I", (void*) get_number_b_from_provider},
};

static int registerNativeMethods(JNIEnv* env, const char* className, JNINativeMethod* gMethods, int numMethods) {
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

typedef union {
    JNIEnv* env;
    void* venv;
} UnionJNIEnvToVoid;

jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    UnionJNIEnvToVoid uenv;
    uenv.venv = NULL;
    jint result = -1;
    JNIEnv* env = NULL;

#ifdef __REVISION_HAVE_SUB__
    LOGI("JNI_OnLoad revision %d bits", ABI_BITNESS);
#else  // __REVISION_HAVE_SUB__
    LOGI("JNI_OnLoad %d bits", ABI_BITNESS);
#endif // __REVISION_HAVE_SUB__

    if (vm->GetEnv(&uenv.venv, JNI_VERSION_1_4) != JNI_OK) {
        LOGE("ERROR: GetEnv failed");
        goto bail;
    }
    env = uenv.env;

    if (registerNatives(env) != JNI_TRUE) {
        LOGE("ERROR: registerNatives failed");
        goto bail;
    }

    result = JNI_VERSION_1_4;

bail:
    return result;
}
