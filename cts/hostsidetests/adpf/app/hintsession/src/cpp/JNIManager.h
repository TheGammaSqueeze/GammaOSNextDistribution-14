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

#pragma once

#include <jni.h>

#include <future>
#include <map>
#include <mutex>
#include <vector>

#include "external/android_native_app_glue.h"

class JNIManager {
public:
    // Used to send results from the native side to the Java app
    static void sendResultsToJava(std::map<std::string, std::string> data);
    // Used to receive data in native code about the test configuration from Java
    static void sendConfigToNative(JNIEnv* env, jobject thisObj, jobjectArray data);

    static JNIManager& getInstance() {
        static JNIManager instance;
        return instance;
    }

    ~JNIManager() {
        JNIEnv* env = AttachCurrentThread();
        env->DeleteGlobalRef(mainActivityClassGlobalRef_);
        DetachCurrentThread();
    }

    void setApp(android_app* app) { app_ = app; }

    JNIEnv* AttachCurrentThread() {
        JNIEnv* env;
        JavaVM* vm = app_->activity->vm;
        if (vm->GetEnv((void**)&env, JNI_VERSION_1_6) == JNI_OK) return env;
        vm->AttachCurrentThread(&env, NULL);
        return env;
    }

    bool useHintSession() {
        std::lock_guard lock(mutex_);
        return hintSessionEnabled_;
    }

    void setMainActivityClassGlobalRef(jclass c) { mainActivityClassGlobalRef_ = c; }

    jclass getMainActivityClass() { return mainActivityClassGlobalRef_; }

    void DetachCurrentThread() { app_->activity->vm->DetachCurrentThread(); }

    std::vector<std::string> getTestNames();

private:
    std::mutex mutex_;
    jclass mainActivityClassGlobalRef_;
    android_app* app_;
    bool hintSessionEnabled_;
    static JNIManager& instance;
    std::promise<std::vector<std::string>> testNames_;
};

// #endif
