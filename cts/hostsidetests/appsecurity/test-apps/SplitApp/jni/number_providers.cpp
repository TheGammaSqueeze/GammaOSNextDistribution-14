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

#ifdef __cplusplus
extern "C" {
#endif

/**
 * The content of libsplitapp_number_provider_proxy.so
 */
#ifdef ANDROID_SPLIT_APP_NUMBER_PROXY_SO
#include <dlfcn.h>

const char* kFunctionName = "get_number";

typedef int (*pFuncGetNumber)();

int get_number(const char* libraryFileName) {
    void *handle;
    char *error;
    handle = dlopen (libraryFileName, RTLD_LAZY);
    if (!handle) {
        return -1;
    }
    pFuncGetNumber functionGetNumber = (pFuncGetNumber) dlsym(handle, kFunctionName);
    if ((error = dlerror()) != NULL)  {
        dlclose(handle);
        return -2;
    }
    int ret = functionGetNumber();
    dlclose(handle);

    return ret;
}

int get_number_a() {
    return get_number("libsplitapp_number_provider_a.so");
}

int get_number_b() {
    return get_number("libsplitapp_number_provider_b.so");
}

#endif // ANDROID_SPLIT_APP_NUMBER_PROXY_SO

/**
 * The content of libsplitapp_number_provider_a.so
 */
#ifdef ANDROID_SPLIT_APP_NUMBER_PROVIDER_A_SO
int get_number() {
    return 7;
}
#endif // ANDROID_SPLIT_APP_NUMBER_PROVIDER_A_SO


/**
 * The content of libsplitapp_number_provider_b.so
 */
#ifdef ANDROID_SPLIT_APP_NUMBER_PROVIDER_B_SO
int get_number() {
    return 11;
}
#endif // ANDROID_SPLIT_APP_NUMBER_PROVIDER_B_SO

#ifdef __cplusplus
}
#endif
