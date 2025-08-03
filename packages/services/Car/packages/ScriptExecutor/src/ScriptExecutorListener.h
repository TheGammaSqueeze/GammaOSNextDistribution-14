/*
 * Copyright (c) 2021, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef PACKAGES_SCRIPTEXECUTOR_SRC_SCRIPTEXECUTORLISTENER_H_
#define PACKAGES_SCRIPTEXECUTOR_SRC_SCRIPTEXECUTORLISTENER_H_

#include "jni.h"

#include <string>

namespace com {
namespace android {
namespace car {
namespace scriptexecutor {

// Changes in this enum must also be reflected in:
// p/s/C/service/src/com/android/car/telemetry/scriptexecutorinterface/IScriptExecutorListener.aidl
// p/s/C/car-lib/src/android/car/telemetry/telemetry.proto
enum ErrorType {
    /**
     * Default error type.
     */
    ERROR_TYPE_UNSPECIFIED = 0,

    /**
     * Used when an error occurs in the ScriptExecutor code.
     */
    ERROR_TYPE_SCRIPT_EXECUTOR_ERROR = 1,

    /**
     * Used when an error occurs while executing the Lua script (such as
     * errors returned by lua_pcall)
     */
    ERROR_TYPE_LUA_RUNTIME_ERROR = 2,

    /**
     * Used to log errors by a script itself, for instance, when a script received
     * inputs outside of expected range.
     */
    ERROR_TYPE_LUA_SCRIPT_ERROR = 3,

    /**
     * Used to log errors for publisher failures.
     */
    ERROR_TYPE_PUBLISHER_FAILED = 4,
};

//  Wrapper class for IScriptExecutorListener.aidl.
class ScriptExecutorListener {
public:
    ScriptExecutorListener(JNIEnv* jni, jobject scriptExecutorListener);

    virtual ~ScriptExecutorListener();

    void onScriptFinished(jobject bundle);

    void onSuccess(jobject bundle);

    void onError(const ErrorType errorType, const char* message, const char* stackTrace);

    void onMetricsReport(jobject reportBundle, jobject stateBundle);

    JNIEnv* getCurrentJNIEnv();

private:
    // Stores a jni global reference to Java Script Executor listener object.
    jobject mScriptExecutorListener;

    // Stores JavaVM pointer in order to be able to get JNIEnv pointer.
    // This is done because JNIEnv cannot be shared between threads.
    // https://developer.android.com/training/articles/perf-jni.html#javavm-and-jnienv
    JavaVM* mJavaVM;
};

}  // namespace scriptexecutor
}  // namespace car
}  // namespace android
}  // namespace com

#endif  // PACKAGES_SCRIPTEXECUTOR_SRC_SCRIPTEXECUTORLISTENER_H_
