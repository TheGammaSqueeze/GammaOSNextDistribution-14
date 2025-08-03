/*
 * Copyright (C) 2009 The Android Open Source Project
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

#include "helper.h"

#include <android/log.h>

#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>

#define LOG_TAG "cts"

/* See helper.h for docs. */
char *failure(const char *format, ...) {
    va_list args;
    char *result;

    va_start(args, format);
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, format, args);
    va_end(args);

    va_start(args, format);
    int count = vasprintf(&result, format, args);
    va_end(args);

    if (count < 0) {
        return NULL;
    }

    return result;
}

/* See helper.h for docs. */
char *runJniTests(JNIEnv *env, ...) {
    va_list args;
    char *result = NULL;

    va_start(args, env);

    for (;;) {
        const char *name = va_arg(args, const char *);
        if (name == NULL) {
            break;
        }

        JniTestFunction *function = va_arg(args, JniTestFunction *);

        __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "running %s", name);

        char *oneResult = function(env);
        if (oneResult != NULL) {
            char *newResult;
            asprintf(&newResult, "%s%s: %s\n",
                    (result == NULL) ? "" : result,
                    name, oneResult);
            free(result);
            if (newResult == NULL) {
                // Shouldn't happen, but deal as gracefully as possible.
                va_end(args);
                return NULL;
            }
            result = newResult;
        }

        jthrowable oneException = (*env)->ExceptionOccurred(env);
        if (oneException != NULL) {
            (*env)->ExceptionDescribe(env);
            (*env)->ExceptionClear(env);
        }
    }

    va_end(args);

    return result;
}

int registerJniMethods(JNIEnv* env, const char* className, const JNINativeMethod* methods,
                       int numMethods)
{
    __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG,
                        "Registering %s's %d native methods...", className, numMethods);
    jclass clazz = (*env)->FindClass(env, className);
    int result = (*env)->RegisterNatives(env, clazz, methods, numMethods);
    (*env)->DeleteLocalRef(env, clazz);
    if (result == 0) {
        return 0;
    }

    __android_log_print(ANDROID_LOG_FATAL, LOG_TAG,
                        "Failed to register JNI methods for %s: %d\n", className, result);
    return result;
}

void throwException(JNIEnv* env, const char* className, const char* message) {
    jclass clazz = (*env)->FindClass(env, className);
    int result = (*env)->ThrowNew(env, clazz, message);
    if (result != 0) {
        __android_log_print(ANDROID_LOG_FATAL, LOG_TAG,
                            "Failed to throw %s: d\n", className, result);

    }
}
