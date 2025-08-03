/**
 * Copyright (c) 2022, The Android Open Source Project
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

#ifndef CPP_WATCHDOG_SERVER_SRC_THREADPRIORITYCONTROLLER_H_
#define CPP_WATCHDOG_SERVER_SRC_THREADPRIORITYCONTROLLER_H_

#include <aidl/android/automotive/watchdog/internal/ThreadPolicyWithPriority.h>
#include <android-base/result.h>

#include <sched.h>

namespace android {
namespace automotive {
namespace watchdog {

class ThreadPriorityControllerInterface {
public:
    virtual ~ThreadPriorityControllerInterface() = default;
    virtual android::base::Result<void> setThreadPriority(int pid, int tid, int uid, int policy,
                                                          int priority) = 0;
    virtual android::base::Result<void> getThreadPriority(
            int pid, int tid, int uid,
            aidl::android::automotive::watchdog::internal::ThreadPolicyWithPriority* result) = 0;
};

class ThreadPriorityController final : public ThreadPriorityControllerInterface {
public:
    // An interface for stubbing system calls in unit testing.
    class SystemCallsInterface {
    public:
        virtual int setScheduler(pid_t tid, int policy, const sched_param* param) = 0;
        virtual int getScheduler(pid_t tid) = 0;
        virtual int getParam(pid_t tid, sched_param* param) = 0;
        virtual android::base::Result<std::tuple<uid_t, pid_t>> readPidStatusFileForPid(
                pid_t pid) = 0;

        virtual ~SystemCallsInterface() = default;
    };

    ThreadPriorityController() : mSystemCallsInterface(std::make_unique<SystemCalls>()) {}

    explicit ThreadPriorityController(std::unique_ptr<SystemCallsInterface> s) :
          mSystemCallsInterface(std::move(s)) {}

    android::base::Result<void> setThreadPriority(int pid, int tid, int uid, int policy,
                                                  int priority) override;
    android::base::Result<void> getThreadPriority(
            int pid, int tid, int uid,
            aidl::android::automotive::watchdog::internal::ThreadPolicyWithPriority* result)
            override;

private:
    class SystemCalls final : public SystemCallsInterface {
        int setScheduler(pid_t tid, int policy, const sched_param* param) override;
        int getScheduler(pid_t tid) override;
        int getParam(pid_t tid, sched_param* param) override;
        android::base::Result<std::tuple<uid_t, pid_t>> readPidStatusFileForPid(pid_t pid) override;
    };

    std::unique_ptr<SystemCallsInterface> mSystemCallsInterface;

    android::base::Result<void> checkPidTidUid(pid_t pid, pid_t tid, uid_t uid);
};

}  // namespace watchdog
}  // namespace automotive
}  // namespace android

#endif  // CPP_WATCHDOG_SERVER_SRC_THREADPRIORITYCONTROLLER_H_
