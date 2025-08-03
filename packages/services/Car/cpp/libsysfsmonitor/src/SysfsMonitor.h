/**
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

#ifndef CPP_LIBSYSFSMONITOR_SRC_SYSFSMONITOR_H_
#define CPP_LIBSYSFSMONITOR_SRC_SYSFSMONITOR_H_

#include <android-base/result.h>
#include <android-base/unique_fd.h>
#include <utils/RefBase.h>

#include <functional>
#include <string>
#include <thread>  // NOLINT(build/c++11)
#include <unordered_set>

namespace android {
namespace automotive {

using CallbackFunc = ::std::function<void(const std::vector<int32_t>&)>;

/**
 * SysfsMonitor monitors sysfs file changes and invokes the registered callback when there is a
 * change at the sysfs files to monitor.
 */
class SysfsMonitor final : public RefBase {
public:
    // Initializes SysfsMonitor instance.
    android::base::Result<void> init(CallbackFunc callback);
    // Releases resources used for monitoring.
    android::base::Result<void> release();
    // Registers a sysfs file to monitor.
    android::base::Result<void> registerFd(int32_t fd);
    // Unregisters a sysfs file to monitor.
    // Some events may be in process, so events may
    // continue to be reported even after this method completes.
    android::base::Result<void> unregisterFd(int32_t fd);
    // Starts observing sysfs file changes.
    android::base::Result<void> observe();

private:
    android::base::unique_fd mEpollFd;
    std::unordered_set<int32_t> mMonitoringFds;
    CallbackFunc mCallback;
    std::thread mMonitoringThread;
    int mPipefd[2];
};

}  // namespace automotive
}  // namespace android

#endif  // CPP_LIBSYSFSMONITOR_SRC_SYSFSMONITOR_H_
