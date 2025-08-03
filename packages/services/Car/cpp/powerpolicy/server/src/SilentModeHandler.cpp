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

#define LOG_TAG "carpowerpolicyd"

#include "SilentModeHandler.h"

#include "CarPowerPolicyServer.h"

#include <android-base/file.h>
#include <android-base/properties.h>
#include <android-base/stringprintf.h>
#include <android-base/strings.h>

#include <sys/epoll.h>
#include <sys/stat.h>
#include <unistd.h>

#include <filesystem>

namespace android {
namespace frameworks {
namespace automotive {
namespace powerpolicy {

using ::android::Mutex;
using ::android::automotive::SysfsMonitor;
using ::android::base::Error;
using ::android::base::GetProperty;
using ::android::base::ReadFileToString;
using ::android::base::Result;
using ::android::base::SetProperty;
using ::android::base::StringPrintf;
using ::android::base::Trim;
using ::android::base::unique_fd;
using ::android::base::WriteStringToFd;

namespace {

constexpr const char kPropertySystemBootReason[] = "sys.boot.reason";
constexpr const char kHwStateFilename[] = "pm_silentmode_hw_state";
constexpr const char kKernelStateFilename[] = "pm_silentmode_kernel_state";
// To prevent boot animation from being started.
constexpr const char kPropertyNoBootAnimation[] = "debug.sf.nobootanimation";
// To stop boot animation while it is being played.
constexpr const char kPropertyBootAnimationExit[] = "service.bootanim.exit";
constexpr const char* kSysfsDirForSilentMode[] = {"/sys/kernel/silent_boot", "/sys/power"};

bool fileExists(const char* filename) {
    struct stat buffer;
    return stat(filename, &buffer) == 0;
}

// We search for the folder where sysfs files for Silent Mode are located in the following order:
//   1. /sys/kernel/silent_boot
//   2. /sys/power (only for backward compatibility)
std::string searchForSysfsDir() {
    std::error_code ec;
    for (const char* dir : kSysfsDirForSilentMode) {
        if (std::filesystem::is_directory(dir, ec)) {
            return std::string(dir) + "/";
        }
    }
    return std::string(kSysfsDirForSilentMode[0]) + "/";
}

}  // namespace

SilentModeHandler::SilentModeHandler(ISilentModeChangeHandler* handler) :
      mSilentModeByHwState(false),
      mSilentModeChangeHandler(handler),
      mSysfsMonitor(sp<SysfsMonitor>::make()) {
    mBootReason = GetProperty(kPropertySystemBootReason, "");
}

void SilentModeHandler::init() {
    std::string sysfsDir = searchForSysfsDir();
    mSilentModeHwStateFilename = sysfsDir + kHwStateFilename;
    mKernelSilentModeFilename = sysfsDir + kKernelStateFilename;
    if (mBootReason == kBootReasonForcedSilent) {
        mForcedMode = true;
        mSilentModeByHwState = true;
    } else if (mBootReason == kBootReasonForcedNonSilent) {
        mForcedMode = true;
        mSilentModeByHwState = false;
    }
    if (mForcedMode) {
        handleSilentModeChange(mSilentModeByHwState);
        mSilentModeChangeHandler->notifySilentModeChange(mSilentModeByHwState);
        ALOGI("Now in forced mode: monitoring %s is disabled", mSilentModeHwStateFilename.c_str());
    } else {
        startMonitoringSilentModeHwState();
    }
}

void SilentModeHandler::release() {
    stopMonitoringSilentModeHwState();
}

bool SilentModeHandler::isSilentMode() {
    Mutex::Autolock lock(mMutex);
    return mSilentModeByHwState;
}

void SilentModeHandler::stopMonitoringSilentModeHwState() {
    if (mIsMonitoring.exchange(false)) {
        if (auto ret = mSysfsMonitor->unregisterFd(mFdSilentModeHwState.get()); !ret.ok()) {
            ALOGW("Unregistering %s from SysfsMonitor failed", mSilentModeHwStateFilename.c_str());
        }
    }
    mFdSilentModeHwState.reset();
    mSysfsMonitor->release();
}

Result<void> SilentModeHandler::dump(int fd, const Vector<String16>& /*args*/) {
    const char* indent = "  ";
    WriteStringToFd(StringPrintf("%sHW state filename: %s\n", indent,
                                 mSilentModeHwStateFilename.c_str()),
                    fd);
    WriteStringToFd(StringPrintf("%sKernel state filename: %s\n", indent,
                                 mKernelSilentModeFilename.c_str()),
                    fd);
    WriteStringToFd(StringPrintf("%sMonitoring HW state: %s\n", indent,
                                 mIsMonitoring ? "true" : "false"),
                    fd);
    WriteStringToFd(StringPrintf("%sForced silent mode: %s\n", indent,
                                 mForcedMode ? "true" : "false"),
                    fd);
    if (mIsMonitoring) {
        Mutex::Autolock lock(mMutex);
        WriteStringToFd(StringPrintf("%sSilent mode by HW state: %s\n", indent,
                                     mSilentModeByHwState ? "silent" : "non-silent"),
                        fd);
    }
    return {};
}

void SilentModeHandler::startMonitoringSilentModeHwState() {
    if (mIsMonitoring) {
        ALOGW("Silent Mode monitoring is already started");
        return;
    }
    const char* filename = mSilentModeHwStateFilename.c_str();
    if (!fileExists(filename)) {
        ALOGW("Failed to start monitoring Silent Mode HW state: %s doesn't exist", filename);
        return;
    }
    if (mFdSilentModeHwState == -1) {
        if (mFdSilentModeHwState.reset(open(filename, O_RDONLY | O_NONBLOCK | O_CLOEXEC));
            mFdSilentModeHwState == -1) {
            ALOGW("Failed to open %s for monitoring: errno = %d", filename, errno);
            return;
        }
    }
    auto ret = mSysfsMonitor->init([this](const std::vector<int32_t>& fileDescriptors) {
        // Only one sysfs file is registered.
        if (mFdSilentModeHwState != fileDescriptors[0]) {
            return;
        }
        handleSilentModeHwStateChange();
    });
    if (!ret.ok()) {
        ALOGW("Failed to initialize SysfsMonitor: %s", ret.error().message().c_str());
        return;
    }
    if (auto ret = mSysfsMonitor->registerFd(mFdSilentModeHwState.get()); !ret.ok()) {
        ALOGW("Failed to register %s to SysfsMonitor: %s", filename, ret.error().message().c_str());
        return;
    }
    if (mSysfsMonitor->observe(); !ret.ok()) {
        ALOGI("Failed to observe %s", filename);
        return;
    }
    mIsMonitoring = true;

    // Read the current silent mode HW state.
    handleSilentModeHwStateChange();
}

void SilentModeHandler::handleSilentModeHwStateChange() {
    if (!mIsMonitoring) {
        return;
    }
    std::string buf;
    if (!ReadFileToString(mSilentModeHwStateFilename.c_str(), &buf)) {
        ALOGW("Failed to read %s", mSilentModeHwStateFilename.c_str());
        return;
    }
    bool newSilentMode;
    bool oldSilentMode;
    {
        Mutex::Autolock lock(mMutex);
        oldSilentMode = std::exchange(mSilentModeByHwState, Trim(buf) == kValueSilentMode);
        newSilentMode = mSilentModeByHwState;
    }
    if (newSilentMode != oldSilentMode) {
        ALOGI("%s is set to %s", mSilentModeHwStateFilename.c_str(),
              newSilentMode ? "silent" : "non-silent");
        handleSilentModeChange(newSilentMode);
        mSilentModeChangeHandler->notifySilentModeChange(newSilentMode);
    }
}

void SilentModeHandler::handleSilentModeChange(bool silent) {
    if (auto ret = updateKernelSilentMode(silent); !ret.ok()) {
        ALOGW("Failed to update kernel silent mode: %s", ret.error().message().c_str());
    }
    if (auto ret = enableBootAnimation(!silent); !ret.ok()) {
        ALOGW("Failed to %s boot animation: %s", mSilentModeByHwState ? "disabling" : "enabling",
              ret.error().message().c_str());
    }
}

Result<void> SilentModeHandler::enableBootAnimation(bool enabled) {
    const std::string value = enabled ? "0" : "1";
    if (!SetProperty(kPropertyNoBootAnimation, value)) {
        return Error() << "Failed to set " << kPropertyNoBootAnimation << " property to " << value;
    }
    if (!enabled) {
        if (!SetProperty(kPropertyBootAnimationExit, value)) {
            return Error() << "Failed to set " << kPropertyBootAnimationExit << " property to "
                           << value;
        }
    }
    return {};
}

Result<void> SilentModeHandler::updateKernelSilentMode(bool silent) {
    int fd = open(mKernelSilentModeFilename.c_str(), O_WRONLY | O_NONBLOCK);
    if (fd < 0) {
        return Error() << "Failed to open " << mKernelSilentModeFilename;
    }
    Result<void> status = {};
    if (const auto& value = silent ? kValueSilentMode : kValueNonSilentMode;
        !WriteStringToFd(value, fd)) {
        status = Error() << "Failed to write " << value << " to fd " << fd;
    }
    close(fd);
    return status;
}

}  // namespace powerpolicy
}  // namespace automotive
}  // namespace frameworks
}  // namespace android
