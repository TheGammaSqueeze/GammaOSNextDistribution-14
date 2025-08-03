/*
 * Copyright 2021 The Android Open Source Project
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

#ifndef CTS_TESTS_TESTS_NEURALNETWORKS_JAVA_TEST_JNI_UTILS_H
#define CTS_TESTS_TESTS_NEURALNETWORKS_JAVA_TEST_JNI_UTILS_H

#include <android/log.h>
#include <jni.h>

#include <cstdlib>
#include <cstring>

// NOTE: All of the following macros requires a local variable `env` of type `JNIEnv*`.

// Check the condition, signal a test failure and return if the condition is not met.
#define ASSERT_OR_RETURN(condition)                                                       \
    if ((condition))                                                                      \
        ;                                                                                 \
    else if (!::android::nn::cts::fail(env, "assert failed on (%s) at %s:%d", #condition, \
                                       __FILE__, __LINE__))                               \
        ;                                                                                 \
    else                                                                                  \
        return

#define ASSERT_OR_RETURN_FALSE(condition) ASSERT_OR_RETURN((condition)) false
#define ASSERT_OR_RETURN_NULL(condition) ASSERT_OR_RETURN((condition)) nullptr
#define ASSERT_OR_RETURN_ZERO(condition) ASSERT_OR_RETURN((condition)) 0
#define ASSERT_OR_RETURN_DEFAULT(condition) \
    ASSERT_OR_RETURN((condition)) {}

// Check and return if there is already a test failure
#define RETURN_IF_FAILED                  \
    if (!::android::nn::cts::failed(env)) \
        ;                                 \
    else                                  \
        return

namespace android::nn::cts {

// Check if there is already a test failure
inline bool failed(JNIEnv* env) {
    return env->ExceptionCheck();
}

// Throw a java exception to signal a test failure
// Will always return true for ASSERT_OR_RETURN
inline bool fail(JNIEnv* env, const char* format, ...) {
    // Return if a test failure is already signaled
    if (failed(env)) return true;

    // Construct error message
    va_list args;
    va_start(args, format);
    char* msg;
    vasprintf(&msg, format, args);
    va_end(args);

    // Throw exception
    jclass testFailureClass = env->FindClass("android/neuralnetworks/cts/TestFailure");
    env->ThrowNew(testFailureClass, msg);
    free(msg);
    return true;
}

} // namespace android::nn::cts

#endif // CTS_TESTS_TESTS_NEURALNETWORKS_JAVA_TEST_JNI_UTILS_H
