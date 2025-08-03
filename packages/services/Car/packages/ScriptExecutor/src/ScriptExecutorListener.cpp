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

#include "ScriptExecutorListener.h"

#include "JniUtils.h"
#include "nativehelper/scoped_local_ref.h"

#include <android-base/logging.h>

namespace com {
namespace android {
namespace car {
namespace scriptexecutor {

ScriptExecutorListener::~ScriptExecutorListener() {
    JNIEnv* env = getCurrentJNIEnv();
    if (mScriptExecutorListener != nullptr) {
        env->DeleteGlobalRef(mScriptExecutorListener);
    }
}

ScriptExecutorListener::ScriptExecutorListener(JNIEnv* env, jobject scriptExecutorListener) {
    mScriptExecutorListener = env->NewGlobalRef(scriptExecutorListener);
    env->GetJavaVM(&mJavaVM);
}

void ScriptExecutorListener::onSuccess(jobject bundle) {
    JNIEnv* env = getCurrentJNIEnv();
    ScopedLocalRef<jclass> listenerClassRef(env, env->GetObjectClass(mScriptExecutorListener));
    jmethodID onSuccessMethod = env->GetMethodID(listenerClassRef.get(), "onSuccess",
                                                 "(Landroid/os/PersistableBundle;)V");
    env->CallVoidMethod(mScriptExecutorListener, onSuccessMethod, bundle);
}

void ScriptExecutorListener::onScriptFinished(jobject bundle) {
    JNIEnv* env = getCurrentJNIEnv();
    ScopedLocalRef<jclass> listenerClassRef(env, env->GetObjectClass(mScriptExecutorListener));
    jmethodID onScriptFinished = env->GetMethodID(listenerClassRef.get(), "onScriptFinished",
                                                  "(Landroid/os/PersistableBundle;)V");
    env->CallVoidMethod(mScriptExecutorListener, onScriptFinished, bundle);
}

void ScriptExecutorListener::onError(const ErrorType errorType, const char* message,
                                     const char* stackTrace) {
    JNIEnv* env = getCurrentJNIEnv();
    ScopedLocalRef<jclass> listenerClassRef(env, env->GetObjectClass(mScriptExecutorListener));
    jmethodID onErrorMethod = env->GetMethodID(listenerClassRef.get(), "onError",
                                               "(ILjava/lang/String;Ljava/lang/String;)V");

    ScopedLocalRef<jstring> messageStringRef(env, env->NewStringUTF(message));
    if (messageStringRef == nullptr) {
        LOG(ERROR) << "Failed to create a Java string for provided error message due to OOM error";
        return;
    }

    ScopedLocalRef<jstring> stackTraceRef(env, env->NewStringUTF(stackTrace));
    if (stackTraceRef == nullptr) {
        LOG(ERROR) << "Failed to create a Java string for stack trace due to OOM error";
        return;
    }

    env->CallVoidMethod(mScriptExecutorListener, onErrorMethod, static_cast<int>(errorType),
                        messageStringRef.get(), stackTraceRef.get());
}

void ScriptExecutorListener::onMetricsReport(jobject reportBundle, jobject stateBundle) {
    JNIEnv* env = getCurrentJNIEnv();
    ScopedLocalRef<jclass> listenerClassRef(env, env->GetObjectClass(mScriptExecutorListener));
    jmethodID onMetricsReport =
            env->GetMethodID(listenerClassRef.get(), "onMetricsReport",
                             "(Landroid/os/PersistableBundle;Landroid/os/PersistableBundle;)V");
    env->CallVoidMethod(mScriptExecutorListener, onMetricsReport, reportBundle, stateBundle);
}

JNIEnv* ScriptExecutorListener::getCurrentJNIEnv() {
    JNIEnv* env;
    if (mJavaVM->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        LOG(FATAL) << "Unable to return JNIEnv from JavaVM";
    }
    return env;
}

}  // namespace scriptexecutor
}  // namespace car
}  // namespace android
}  // namespace com
