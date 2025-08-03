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

#include "EvsCallbackThread.h"

#include <android-base/logging.h>

namespace {

constexpr const char kCallbackThreadName[] = "EvsCallbackThread";

}  // namespace

namespace android::automotive::evs {

EvsCallbackThread::EvsCallbackThread(JavaVM* vm) : mVm(vm), mRunning(true) {
    mThread = std::thread(&EvsCallbackThread::threadLoop, this);
    LOG(DEBUG) << "Started the native callback handler thread = " << this;
}

EvsCallbackThread::~EvsCallbackThread() {
    // Stops the loop
    stop();
}

void EvsCallbackThread::threadLoop() {
    // Attaches the current thread to the Java VM.
    JNIEnv* env = nullptr;
    JavaVMAttachArgs args = {JNI_VERSION_1_4, kCallbackThreadName, nullptr};
    if (mVm->AttachCurrentThread(&env, &args) != JNI_OK || env == nullptr) {
        LOG(ERROR) << "Failed to be attached to the VM.";
        mRunning = false;
        return;
    }

    while (true) {
        Task task;
        {
            std::unique_lock<std::mutex> lock(mLock);
            android::base::ScopedLockAssertion lock_assertion(mLock);
            if (!mRunning) {
                break;
            }

            if (mTaskQueue.empty()) {
                mCondition.wait(lock);
                // The conditional variable is signalled when either a new task
                // is enqueued or we are requested to stop.  If we wake up
                // spuriously, the task queue must be empty so go back to sleep.
                if (!mRunning) {
                    break;
                } else if (mTaskQueue.empty()) {
                    LOG(DEBUG) << "No pending tasks; continue.";
                    continue;
                }
            }

            task = mTaskQueue.front();
            mTaskQueue.pop();
        }

        // Executes the task and check the exception
        task(env);
        if (env->ExceptionCheck()) {
            LOG(ERROR) << "Exception happens while handling a task:";
            env->ExceptionDescribe();
            env->ExceptionClear();
        }
    }

    {
        std::lock_guard lock(mLock);
        auto res = mVm->DetachCurrentThread();
        if (res != JNI_OK) {
            LOG(WARNING) << "Failed to be detached from the VM.";
        }

        if (!mTaskQueue.empty()) {
            LOG(WARNING) << mTaskQueue.size() << " tasks are ignored.";
        }
    }

    LOG(DEBUG) << "Exiting a callback handler thread.";
}

void EvsCallbackThread::enqueue(const Task& task) {
    std::lock_guard<std::mutex> lock(mLock);
    if (!mRunning) {
        LOG(WARNING) << "A callback handler thread is not running.";
        return;
    }

    mTaskQueue.push(task);
    mCondition.notify_one();
}

void EvsCallbackThread::stop() {
    {
        std::lock_guard<std::mutex> lock(mLock);
        if (!mRunning) {
            // Nothing to do if the handler thread is not running.
            return;
        }

        mRunning = false;
        mCondition.notify_all();
    }

    if (mThread.get_id() == std::this_thread::get_id()) {
        // Should not join by myself
        mThread.detach();
    } else if (mThread.joinable()) {
        mThread.join();
    }
}

}  // namespace android::automotive::evs
