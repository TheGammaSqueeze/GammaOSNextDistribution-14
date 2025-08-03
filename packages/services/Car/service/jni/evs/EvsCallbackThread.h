/**
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
#ifndef ANDROID_CARSERVICE_EVSCALLBACKTHREAD_H
#define ANDROID_CARSERVICE_EVSCALLBACKTHREAD_H

#include <android-base/macros.h>
#include <android-base/thread_annotations.h>

#include <jni.h>

#include <queue>
#include <thread>

namespace android::automotive::evs {

/*
 * This class defines the thread that handles callbacks from the native Extended
 * View System service.
 */
class EvsCallbackThread final {
    using Task = std::function<void(JNIEnv*)>;

    JavaVM* mVm;

    std::atomic<bool> mRunning;
    std::thread mThread;
    std::mutex mLock;
    std::condition_variable mCondition;
    std::queue<Task> mTaskQueue GUARDED_BY(mLock);

    // Main loop to handle enqueued tasks continuously
    void threadLoop() EXCLUDES(mLock);

    DISALLOW_COPY_AND_ASSIGN(EvsCallbackThread);

public:
    explicit EvsCallbackThread(JavaVM* vm);
    virtual ~EvsCallbackThread();

    // Adds a new task to the queue
    void enqueue(const Task& task) EXCLUDES(mLock);

    // Stops a task handler thread
    void stop() EXCLUDES(mLock);
};

}  // namespace android::automotive::evs

#endif  // ANDROID_CARSERVICE_EVSCALLBACKTHREAD_H
