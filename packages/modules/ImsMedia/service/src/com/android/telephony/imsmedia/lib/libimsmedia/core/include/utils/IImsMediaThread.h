/**
 * Copyright (C) 2022 The Android Open Source Project
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

#ifndef IIMS_MEDIA_THREAD
#define IIMS_MEDIA_THREAD

#include <mutex>

#define MAX_EVENTHANDLER_NAME 256

// Thread priority value used with SCH_FIFO scheduling policy to set Real-Time priority.
#define THREAD_PRIORITY_REALTIME 2

/**
 * @class IImsMediaThread
 * @brief Base class of thread
 *        - Child class should implement run() method.
 *        - Call StartThread() method to start thread.
 */
class IImsMediaThread
{
public:
    IImsMediaThread();
    virtual ~IImsMediaThread();

    /**
     * @brief Starts the new thread execution and detaches from the calling thread.
     *
     * @param name Optional param. If passed, used to set name for the thread.
     *               Helpful for debugging.
     */
    bool StartThread(const char* name = nullptr);

    /**
     * @brief Sets given thread priority for a given thread. Used to set realtime thread priority
     * for time critical thread in the audio graph.
     *
     * @param pid Id of the current process
     * @param tid Id of the thread whose priority should be modified.
     * @param priority Thread priority value used with SCH_FIFO scheduling policy.
     *                 THREAD_PRIORITY_REALTIME can be used to set realtime priority.
     */
    static void SetThreadPriority(pid_t pid, pid_t tid, int priority);

    /**
     * @brief Sets the stopped flag and the thread's run method should read and return.
     * Method is asynchronous and returns immediately without waiting for the thread's run
     * method to return.
     */
    void StopThread();

    /**
     * @brief Used to check if the thread is running.
     */
    bool IsThreadStopped();

    /**
     * @brief Should be implemented by the derived call. Called by new thread when StartThread is
     * invoked.
     */
    virtual void* run() = 0;

private:
    std::mutex mThreadMutex;
    bool mThreadStopped;
};

#endif