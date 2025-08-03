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

#ifndef CPP_POWERPOLICY_SERVER_SRC_SILENTMODEHANDLER_H_
#define CPP_POWERPOLICY_SERVER_SRC_SILENTMODEHANDLER_H_

#include <android-base/result.h>
#include <android-base/unique_fd.h>
#include <utils/Mutex.h>
#include <utils/String16.h>
#include <utils/StrongPointer.h>
#include <utils/Vector.h>

#include <SysfsMonitor.h>

#include <atomic>
#include <thread>  // NOLINT(build/c++11)

namespace android {
namespace frameworks {
namespace automotive {
namespace powerpolicy {

inline constexpr const char* kBootReasonForcedNonSilent = "reboot,forcednonsilent";
inline constexpr const char* kBootReasonForcedSilent = "reboot,forcedsilent";
inline constexpr const char* kValueNonSilentMode = "0";
inline constexpr const char* kValueSilentMode = "1";

class ISilentModeChangeHandler;

// Forward declaration for testing use only.
namespace internal {

class SilentModeHandlerPeer;

}  // namespace internal

/**
 * SilentModeHandler monitors {@code /sys/kernel/silent_boot/pm_silentmode_hw_state} in sysfs to
 * detect Silent Mode change by a vehicle processor. Also, it updates
 * {@code /sys/kernel/silent_boot/pm_silentmode_kernel_state} in sysfs to tell kernel the current
 * Silent Mode.
 */
class SilentModeHandler final {
public:
    explicit SilentModeHandler(ISilentModeChangeHandler* server);

    // Initialize SilentModeHandler instance.
    void init();
    // Release the resource to prepare termination.
    void release();
    // Returns the current Silent Mode.
    bool isSilentMode();
    // Stops monitoring the change on pm_silentmode_hw_state.
    void stopMonitoringSilentModeHwState();
    // Dumps the internal state.
    android::base::Result<void> dump(int fd, const Vector<String16>& args);

private:
    android::base::Result<void> updateKernelSilentMode(bool silent);
    void startMonitoringSilentModeHwState();
    void handleSilentModeHwStateChange();
    void handleSilentModeChange(bool silent);
    android::base::Result<void> enableBootAnimation(bool enabled);

    android::Mutex mMutex;
    bool mSilentModeByHwState GUARDED_BY(mMutex);
    bool mForcedMode = false;
    std::string mBootReason;
    std::string mSilentModeHwStateFilename;
    std::string mKernelSilentModeFilename;
    ISilentModeChangeHandler* mSilentModeChangeHandler;
    std::atomic_bool mIsMonitoring = false;
    android::sp<android::automotive::SysfsMonitor> mSysfsMonitor;
    android::base::unique_fd mFdSilentModeHwState;

    // For unit tests.
    friend class internal::SilentModeHandlerPeer;
};

}  // namespace powerpolicy
}  // namespace automotive
}  // namespace frameworks
}  // namespace android

#endif  // CPP_POWERPOLICY_SERVER_SRC_SILENTMODEHANDLER_H_
