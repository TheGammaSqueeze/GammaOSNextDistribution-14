/*
 * Copyright (C) 2012 The Android Open Source Project
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

#define ATRACE_TAG (ATRACE_TAG_GRAPHICS | ATRACE_TAG_HAL)
//#include <linux/fb.h>
#include "ExynosDisplay.h"

#include <aidl/android/hardware/graphics/common/Transform.h>
#include <aidl/android/hardware/graphics/composer3/ColorMode.h>
#include <aidl/android/hardware/power/IPower.h>
#include <aidl/google/hardware/power/extension/pixel/IPowerExt.h>
#include <android-base/parsebool.h>
#include <android-base/properties.h>
#include <android/binder_manager.h>
#include <cutils/properties.h>
#include <hardware/hwcomposer_defs.h>
#include <linux/fb.h>
#include <processgroup/processgroup.h>
#include <sync/sync.h>
#include <sys/ioctl.h>
#include <utils/CallStack.h>

#include <charconv>
#include <future>
#include <map>

#include "BrightnessController.h"
#include "ExynosExternalDisplay.h"
#include "ExynosLayer.h"
#include "HistogramController.h"
#include "VendorGraphicBuffer.h"
#include "exynos_format.h"
#include "utils/Timers.h"

/**
 * ExynosDisplay implementation
 */

using namespace android;
using namespace vendor::graphics;
using namespace std::chrono_literals;

using ::aidl::android::hardware::power::IPower;
using ::aidl::google::hardware::power::extension::pixel::IPowerExt;
namespace AidlComposer3 = ::aidl::android::hardware::graphics::composer3;
namespace AidlCommon = ::aidl::android::hardware::graphics::common;

extern struct exynos_hwc_control exynosHWCControl;
extern struct update_time_info updateTimeInfo;

constexpr const char* kBufferDumpPath = "/data/vendor/log/hwc";

constexpr float kDynamicRecompFpsThreshold = 1.0 / 5.0; // 1 frame update per 5 second

constexpr float nsecsPerSec = std::chrono::nanoseconds(1s).count();
constexpr int64_t nsecsIdleHintTimeout = std::chrono::nanoseconds(100ms).count();

ExynosDisplay::PowerHalHintWorker::PowerHalHintWorker(uint32_t displayId,
                                                      const String8& displayTraceName)
      : Worker("DisplayHints", HAL_PRIORITY_URGENT_DISPLAY),
        mNeedUpdateRefreshRateHint(false),
        mLastRefreshRateHint(0),
        mIdleHintIsEnabled(false),
        mForceUpdateIdleHint(false),
        mIdleHintDeadlineTime(0),
        mIdleHintSupportIsChecked(false),
        mIdleHintIsSupported(false),
        mDisplayTraceName(displayTraceName),
        mPowerModeState(HWC2_POWER_MODE_OFF),
        mRefreshRate(kDefaultRefreshRateFrequency),
        mConnectRetryCount(0),
        mDeathRecipient(AIBinder_DeathRecipient_new(BinderDiedCallback)),
        mPowerHalExtAidl(nullptr),
        mPowerHalAidl(nullptr),
        mPowerHintSession(nullptr) {
    if (property_get_bool("vendor.display.powerhal_hint_per_display", false)) {
        std::string displayIdStr = std::to_string(displayId);
        mIdleHintStr = "DISPLAY_" + displayIdStr + "_IDLE";
        mRefreshRateHintPrefixStr = "DISPLAY_" + displayIdStr + "_";
    } else {
        mIdleHintStr = "DISPLAY_IDLE";
        mRefreshRateHintPrefixStr = "REFRESH_";
    }
}

ExynosDisplay::PowerHalHintWorker::~PowerHalHintWorker() {
    Exit();
}

int ExynosDisplay::PowerHalHintWorker::Init() {
    return InitWorker();
}

void ExynosDisplay::PowerHalHintWorker::BinderDiedCallback(void *cookie) {
    ALOGE("PowerHal is died");
    auto powerHint = reinterpret_cast<PowerHalHintWorker *>(cookie);
    powerHint->forceUpdateHints();
}

int32_t ExynosDisplay::PowerHalHintWorker::connectPowerHal() {
    if (mPowerHalAidl && mPowerHalExtAidl) {
        return NO_ERROR;
    }

    const std::string kInstance = std::string(IPower::descriptor) + "/default";
    ndk::SpAIBinder pwBinder = ndk::SpAIBinder(AServiceManager_getService(kInstance.c_str()));

    mPowerHalAidl = IPower::fromBinder(pwBinder);

    if (!mPowerHalAidl) {
        ALOGE("failed to connect power HAL (retry %u)", mConnectRetryCount);
        mConnectRetryCount++;
        return -EINVAL;
    }

    ndk::SpAIBinder pwExtBinder;
    AIBinder_getExtension(pwBinder.get(), pwExtBinder.getR());

    mPowerHalExtAidl = IPowerExt::fromBinder(pwExtBinder);

    if (!mPowerHalExtAidl) {
        mPowerHalAidl = nullptr;
        ALOGE("failed to connect power HAL extension (retry %u)", mConnectRetryCount);
        mConnectRetryCount++;
        return -EINVAL;
    }

    mConnectRetryCount = 0;
    AIBinder_linkToDeath(pwExtBinder.get(), mDeathRecipient.get(), reinterpret_cast<void *>(this));
    // ensure the hint session is recreated every time powerhal is recreated
    mPowerHintSession = nullptr;
    forceUpdateHints();
    ALOGI("connected power HAL successfully");
    return NO_ERROR;
}

int32_t ExynosDisplay::PowerHalHintWorker::checkPowerHalExtHintSupport(const std::string &mode) {
    if (mode.empty() || connectPowerHal() != NO_ERROR) {
        return -EINVAL;
    }

    bool isSupported = false;
    auto ret = mPowerHalExtAidl->isModeSupported(mode.c_str(), &isSupported);
    if (!ret.isOk()) {
        ALOGE("failed to check power HAL extension hint: mode=%s", mode.c_str());
        if (ret.getExceptionCode() == EX_TRANSACTION_FAILED) {
            /*
             * PowerHAL service may crash due to some reasons, this could end up
             * binder transaction failure. Set nullptr here to trigger re-connection.
             */
            ALOGE("binder transaction failed for power HAL extension hint");
            mPowerHalExtAidl = nullptr;
            return -ENOTCONN;
        }
        return -EINVAL;
    }

    if (!isSupported) {
        ALOGW("power HAL extension hint is not supported: mode=%s", mode.c_str());
        return -EOPNOTSUPP;
    }

    ALOGI("power HAL extension hint is supported: mode=%s", mode.c_str());
    return NO_ERROR;
}

int32_t ExynosDisplay::PowerHalHintWorker::sendPowerHalExtHint(const std::string &mode,
                                                               bool enabled) {
    if (mode.empty() || connectPowerHal() != NO_ERROR) {
        return -EINVAL;
    }

    auto ret = mPowerHalExtAidl->setMode(mode.c_str(), enabled);
    if (!ret.isOk()) {
        ALOGE("failed to send power HAL extension hint: mode=%s, enabled=%d", mode.c_str(),
              enabled);
        if (ret.getExceptionCode() == EX_TRANSACTION_FAILED) {
            /*
             * PowerHAL service may crash due to some reasons, this could end up
             * binder transaction failure. Set nullptr here to trigger re-connection.
             */
            ALOGE("binder transaction failed for power HAL extension hint");
            mPowerHalExtAidl = nullptr;
            return -ENOTCONN;
        }
        return -EINVAL;
    }

    return NO_ERROR;
}

int32_t ExynosDisplay::PowerHalHintWorker::checkRefreshRateHintSupport(const int32_t refreshRate) {
    int32_t ret = NO_ERROR;

    if (!isPowerHalExist()) {
        return -EOPNOTSUPP;
    }
    const auto its = mRefreshRateHintSupportMap.find(refreshRate);
    if (its == mRefreshRateHintSupportMap.end()) {
        /* check new hint */
        std::string refreshRateHintStr =
                mRefreshRateHintPrefixStr + std::to_string(refreshRate) + "FPS";
        ret = checkPowerHalExtHintSupport(refreshRateHintStr);
        if (ret == NO_ERROR || ret == -EOPNOTSUPP) {
            mRefreshRateHintSupportMap[refreshRate] = (ret == NO_ERROR);
            ALOGI("cache refresh rate hint %s: %d", refreshRateHintStr.c_str(), !ret);
        } else {
            ALOGE("failed to check the support of refresh rate hint, ret %d", ret);
        }
    } else {
        /* check existing hint */
        if (!its->second) {
            ret = -EOPNOTSUPP;
        }
    }
    return ret;
}

int32_t ExynosDisplay::PowerHalHintWorker::sendRefreshRateHint(const int32_t refreshRate,
                                                               bool enabled) {
    std::string hintStr = mRefreshRateHintPrefixStr + std::to_string(refreshRate) + "FPS";
    int32_t ret = sendPowerHalExtHint(hintStr, enabled);
    if (ret == -ENOTCONN) {
        /* Reset the hints when binder failure occurs */
        mLastRefreshRateHint = 0;
    }
    return ret;
}

int32_t ExynosDisplay::PowerHalHintWorker::updateRefreshRateHintInternal(
        const hwc2_power_mode_t powerMode, const int32_t refreshRate) {
    int32_t ret = NO_ERROR;

    /* TODO: add refresh rate buckets, tracked in b/181100731 */
    // skip sending unnecessary hint if it's still the same.
    if (mLastRefreshRateHint == refreshRate && powerMode == HWC2_POWER_MODE_ON) {
        return NO_ERROR;
    }

    if (mLastRefreshRateHint) {
        ret = sendRefreshRateHint(mLastRefreshRateHint, false);
        if (ret == NO_ERROR) {
            mLastRefreshRateHint = 0;
        } else {
            return ret;
        }
    }

    // disable all refresh rate hints if power mode is not ON.
    if (powerMode != HWC2_POWER_MODE_ON) {
        return ret;
    }

    ret = checkRefreshRateHintSupport(refreshRate);
    if (ret != NO_ERROR) {
        return ret;
    }

    ret = sendRefreshRateHint(refreshRate, true);
    if (ret != NO_ERROR) {
        return ret;
    }

    mLastRefreshRateHint = refreshRate;
    return ret;
}

int32_t ExynosDisplay::PowerHalHintWorker::checkIdleHintSupport(void) {
    int32_t ret = NO_ERROR;

    if (!isPowerHalExist()) {
        return -EOPNOTSUPP;
    }

    Lock();
    if (mIdleHintSupportIsChecked) {
        ret = mIdleHintIsSupported ? NO_ERROR : -EOPNOTSUPP;
        Unlock();
        return ret;
    }
    Unlock();

    ret = checkPowerHalExtHintSupport(mIdleHintStr);
    Lock();
    if (ret == NO_ERROR) {
        mIdleHintIsSupported = true;
        mIdleHintSupportIsChecked = true;
        ALOGI("display idle hint is supported");
    } else if (ret == -EOPNOTSUPP) {
        mIdleHintSupportIsChecked = true;
        ALOGI("display idle hint is unsupported");
    } else {
        ALOGW("failed to check the support of display idle hint, ret %d", ret);
    }
    Unlock();
    return ret;
}

int32_t ExynosDisplay::PowerHalHintWorker::checkPowerHintSessionSupport() {
    std::scoped_lock lock(sSharedDisplayMutex);
    if (sSharedDisplayData.hintSessionSupported.has_value()) {
        mHintSessionSupportChecked = true;
        return *(sSharedDisplayData.hintSessionSupported);
    }

    if (!isPowerHalExist()) {
        return -EOPNOTSUPP;
    }

    if (connectPowerHal() != NO_ERROR) {
        ALOGW("Error connecting to the PowerHAL");
        return -EINVAL;
    }

    int64_t rate;
    // Try to get preferred rate to determine if it's supported
    auto ret = mPowerHalAidl->getHintSessionPreferredRate(&rate);

    int32_t out;
    if (ret.isOk()) {
        ALOGV("Power hint session is supported");
        out = NO_ERROR;
    } else if (ret.getExceptionCode() == EX_UNSUPPORTED_OPERATION) {
        ALOGW("Power hint session unsupported");
        out = -EOPNOTSUPP;
    } else {
        ALOGW("Error checking power hint status");
        out = -EINVAL;
    }

    mHintSessionSupportChecked = true;
    sSharedDisplayData.hintSessionSupported = out;
    return out;
}

int32_t ExynosDisplay::PowerHalHintWorker::updateIdleHint(const int64_t deadlineTime,
                                                          const bool forceUpdate) {
    int32_t ret = checkIdleHintSupport();
    if (ret != NO_ERROR) {
        return ret;
    }

    bool enableIdleHint =
            (deadlineTime < systemTime(SYSTEM_TIME_MONOTONIC) && CC_LIKELY(deadlineTime > 0));
    DISPLAY_ATRACE_INT("HWCIdleHintTimer", enableIdleHint);

    if (mIdleHintIsEnabled != enableIdleHint || forceUpdate) {
        ret = sendPowerHalExtHint(mIdleHintStr, enableIdleHint);
        if (ret == NO_ERROR) {
            mIdleHintIsEnabled = enableIdleHint;
        }
    }
    return ret;
}

void ExynosDisplay::PowerHalHintWorker::forceUpdateHints(void) {
    Lock();
    mLastRefreshRateHint = 0;
    mNeedUpdateRefreshRateHint = true;
    mLastErrorSent = std::nullopt;
    if (mIdleHintSupportIsChecked && mIdleHintIsSupported) {
        mForceUpdateIdleHint = true;
    }

    Unlock();

    Signal();
}

int32_t ExynosDisplay::PowerHalHintWorker::sendActualWorkDuration() {
    Lock();
    if (mPowerHintSession == nullptr) {
        Unlock();
        return -EINVAL;
    }

    if (!needSendActualWorkDurationLocked()) {
        Unlock();
        return NO_ERROR;
    }

    if (mActualWorkDuration.has_value()) {
        mLastErrorSent = *mActualWorkDuration - mTargetWorkDuration;
    }

    std::vector<WorkDuration> hintQueue(std::move(mPowerHintQueue));
    mPowerHintQueue.clear();
    Unlock();

    ALOGV("Sending hint update batch");
    mLastActualReportTimestamp = systemTime(SYSTEM_TIME_MONOTONIC);
    auto ret = mPowerHintSession->reportActualWorkDuration(hintQueue);
    if (!ret.isOk()) {
        ALOGW("Failed to report power hint session timing:  %s %s", ret.getMessage(),
              ret.getDescription().c_str());
        if (ret.getExceptionCode() == EX_TRANSACTION_FAILED) {
            Lock();
            mPowerHalExtAidl = nullptr;
            Unlock();
        }
    }
    return ret.isOk() ? NO_ERROR : -EINVAL;
}

int32_t ExynosDisplay::PowerHalHintWorker::updateTargetWorkDuration() {
    if (sNormalizeTarget) {
        return NO_ERROR;
    }

    if (mPowerHintSession == nullptr) {
        return -EINVAL;
    }

    Lock();

    if (!needUpdateTargetWorkDurationLocked()) {
        Unlock();
        return NO_ERROR;
    }

    nsecs_t targetWorkDuration = mTargetWorkDuration;
    mLastTargetDurationReported = targetWorkDuration;
    Unlock();

    ALOGV("Sending target time: %lld ns", static_cast<long long>(targetWorkDuration));
    auto ret = mPowerHintSession->updateTargetWorkDuration(targetWorkDuration);
    if (!ret.isOk()) {
        ALOGW("Failed to send power hint session target:  %s %s", ret.getMessage(),
              ret.getDescription().c_str());
        if (ret.getExceptionCode() == EX_TRANSACTION_FAILED) {
            Lock();
            mPowerHalExtAidl = nullptr;
            Unlock();
        }
    }
    return ret.isOk() ? NO_ERROR : -EINVAL;
}

void ExynosDisplay::PowerHalHintWorker::signalActualWorkDuration(nsecs_t actualDurationNanos) {
    ATRACE_CALL();

    if (!usePowerHintSession()) {
        return;
    }
    Lock();
    nsecs_t reportedDurationNs = actualDurationNanos;
    if (sNormalizeTarget) {
        reportedDurationNs += mLastTargetDurationReported - mTargetWorkDuration;
    } else {
        if (mLastTargetDurationReported != kDefaultTarget.count() && mTargetWorkDuration != 0) {
            reportedDurationNs =
                    static_cast<int64_t>(static_cast<long double>(mLastTargetDurationReported) /
                                         mTargetWorkDuration * actualDurationNanos);
        }
    }

    mActualWorkDuration = reportedDurationNs;
    WorkDuration duration = {.timeStampNanos = systemTime(), .durationNanos = reportedDurationNs};

    if (sTraceHintSessionData) {
        DISPLAY_ATRACE_INT64("Measured duration", actualDurationNanos);
        DISPLAY_ATRACE_INT64("Target error term", mTargetWorkDuration - actualDurationNanos);

        DISPLAY_ATRACE_INT64("Reported duration", reportedDurationNs);
        DISPLAY_ATRACE_INT64("Reported target", mLastTargetDurationReported);
        DISPLAY_ATRACE_INT64("Reported target error term",
                             mLastTargetDurationReported - reportedDurationNs);
    }
    ALOGV("Sending actual work duration of: %" PRId64 " on reported target: %" PRId64
          " with error: %" PRId64,
          reportedDurationNs, mLastTargetDurationReported,
          mLastTargetDurationReported - reportedDurationNs);

    mPowerHintQueue.push_back(duration);

    bool shouldSignal = needSendActualWorkDurationLocked();
    Unlock();
    if (shouldSignal) {
        Signal();
    }
}

void ExynosDisplay::PowerHalHintWorker::signalTargetWorkDuration(nsecs_t targetDurationNanos) {
    ATRACE_CALL();
    if (!usePowerHintSession()) {
        return;
    }
    Lock();
    mTargetWorkDuration = targetDurationNanos - kTargetSafetyMargin.count();

    if (sTraceHintSessionData) DISPLAY_ATRACE_INT64("Time target", mTargetWorkDuration);
    bool shouldSignal = false;
    if (!sNormalizeTarget) {
        shouldSignal = needUpdateTargetWorkDurationLocked();
        if (shouldSignal && mActualWorkDuration.has_value() && sTraceHintSessionData) {
            DISPLAY_ATRACE_INT64("Target error term", *mActualWorkDuration - mTargetWorkDuration);
        }
    }
    Unlock();
    if (shouldSignal) {
        Signal();
    }
}

void ExynosDisplay::PowerHalHintWorker::signalRefreshRate(hwc2_power_mode_t powerMode,
                                                          int32_t refreshRate) {
    Lock();
    mPowerModeState = powerMode;
    mRefreshRate = refreshRate;
    mNeedUpdateRefreshRateHint = true;
    Unlock();

    Signal();
}

void ExynosDisplay::PowerHalHintWorker::signalNonIdle() {
    ATRACE_CALL();

    Lock();
    if (mIdleHintSupportIsChecked && !mIdleHintIsSupported) {
        Unlock();
        return;
    }

    mIdleHintDeadlineTime = systemTime(SYSTEM_TIME_MONOTONIC) + nsecsIdleHintTimeout;
    Unlock();

    Signal();
}

bool ExynosDisplay::PowerHalHintWorker::needUpdateIdleHintLocked(int64_t &timeout) {
    if (!mIdleHintIsSupported) {
        return false;
    }

    int64_t currentTime = systemTime(SYSTEM_TIME_MONOTONIC);
    bool shouldEnableIdleHint =
            (mIdleHintDeadlineTime < currentTime) && CC_LIKELY(mIdleHintDeadlineTime > 0);
    if (mIdleHintIsEnabled != shouldEnableIdleHint || mForceUpdateIdleHint) {
        return true;
    }

    timeout = mIdleHintDeadlineTime - currentTime;
    return false;
}

void ExynosDisplay::PowerHalHintWorker::Routine() {
    Lock();
    bool useHintSession = usePowerHintSession();
    // if the tids have updated, we restart the session
    if (mTidsUpdated && useHintSession) mPowerHintSession = nullptr;
    bool needStartHintSession =
            (mPowerHintSession == nullptr) && useHintSession && !mBinderTids.empty();
    int ret = 0;
    int64_t timeout = -1;
    if (!mNeedUpdateRefreshRateHint && !needUpdateIdleHintLocked(timeout) &&
        !needSendActualWorkDurationLocked() && !needStartHintSession &&
        !needUpdateTargetWorkDurationLocked()) {
        ret = WaitForSignalOrExitLocked(timeout);
    }

    // exit() signal received
    if (ret == -EINTR) {
        Unlock();
        return;
    }

    // store internal values so they are consistent after Unlock()
    // some defined earlier also might have changed during the wait
    useHintSession = usePowerHintSession();
    needStartHintSession = (mPowerHintSession == nullptr) && useHintSession && !mBinderTids.empty();

    bool needUpdateRefreshRateHint = mNeedUpdateRefreshRateHint;
    int64_t deadlineTime = mIdleHintDeadlineTime;
    hwc2_power_mode_t powerMode = mPowerModeState;

    /*
     * Clear the flags here instead of clearing them after calling the hint
     * update functions. The flags may be set by signals after Unlock() and
     * before the hint update functions are done. Thus we may miss the newest
     * hints if we clear the flags after the hint update functions work without
     * errors.
     */
    mTidsUpdated = false;
    mNeedUpdateRefreshRateHint = false;

    bool forceUpdateIdleHint = mForceUpdateIdleHint;
    mForceUpdateIdleHint = false;
    Unlock();

    if (!mHintSessionSupportChecked) {
        checkPowerHintSessionSupport();
    }

    updateIdleHint(deadlineTime, forceUpdateIdleHint);

    if (needUpdateRefreshRateHint) {
        int32_t rc = updateRefreshRateHintInternal(powerMode, mRefreshRate);
        if (rc != NO_ERROR && rc != -EOPNOTSUPP) {
            Lock();
            if (mPowerModeState == HWC2_POWER_MODE_ON) {
                /* Set the flag to trigger update again for next loop */
                mNeedUpdateRefreshRateHint = true;
            }
            Unlock();
        }
    }

    if (useHintSession) {
        if (needStartHintSession) {
            startHintSession();
        }
        sendActualWorkDuration();
        updateTargetWorkDuration();
    }
}

void ExynosDisplay::PowerHalHintWorker::addBinderTid(pid_t tid) {
    Lock();
    if (mBinderTids.count(tid) != 0) {
        Unlock();
        return;
    }
    mTidsUpdated = true;
    mBinderTids.emplace(tid);
    Unlock();
    Signal();
}

void ExynosDisplay::PowerHalHintWorker::removeBinderTid(pid_t tid) {
    Lock();
    if (mBinderTids.erase(tid) == 0) {
        Unlock();
        return;
    }
    mTidsUpdated = true;
    Unlock();
    Signal();
}

int32_t ExynosDisplay::PowerHalHintWorker::startHintSession() {
    Lock();
    std::vector<int> tids(mBinderTids.begin(), mBinderTids.end());
    nsecs_t targetWorkDuration =
            sNormalizeTarget ? mLastTargetDurationReported : mTargetWorkDuration;
    // we want to stay locked during this one since it assigns "mPowerHintSession"
    auto ret = mPowerHalAidl->createHintSession(getpid(), static_cast<uid_t>(getuid()), tids,
                                                targetWorkDuration, &mPowerHintSession);
    if (!ret.isOk()) {
        ALOGW("Failed to start power hal hint session with error  %s %s", ret.getMessage(),
              ret.getDescription().c_str());
        if (ret.getExceptionCode() == EX_TRANSACTION_FAILED) {
            mPowerHalExtAidl = nullptr;
        }
        Unlock();
        return -EINVAL;
    } else {
        mLastTargetDurationReported = targetWorkDuration;
    }
    Unlock();
    return NO_ERROR;
}

bool ExynosDisplay::PowerHalHintWorker::checkPowerHintSessionReady() {
    static constexpr const std::chrono::milliseconds maxFlagWaitTime = 20s;
    static const std::string propName =
            "persist.device_config.surface_flinger_native_boot.AdpfFeature__adpf_cpu_hint";
    static std::once_flag hintSessionFlag;
    // wait once for 20 seconds in another thread for the value to become available, or give up
    std::call_once(hintSessionFlag, [&] {
        std::thread hintSessionChecker([&] {
            std::optional<std::string> flagValue =
                    waitForPropertyValue(propName, maxFlagWaitTime.count());
            bool enabled = flagValue.has_value() &&
                    (base::ParseBool(flagValue->c_str()) == base::ParseBoolResult::kTrue);
            std::scoped_lock lock(sSharedDisplayMutex);
            sSharedDisplayData.hintSessionEnabled = enabled;
        });
        hintSessionChecker.detach();
    });
    std::scoped_lock lock(sSharedDisplayMutex);
    return sSharedDisplayData.hintSessionEnabled.has_value() &&
            sSharedDisplayData.hintSessionSupported.has_value();
}

bool ExynosDisplay::PowerHalHintWorker::usePowerHintSession() {
    std::optional<bool> useSessionCached{mUsePowerHintSession.load()};
    if (useSessionCached.has_value()) {
        return *useSessionCached;
    }
    if (!checkPowerHintSessionReady()) return false;
    std::scoped_lock lock(sSharedDisplayMutex);
    bool out = *(sSharedDisplayData.hintSessionEnabled) &&
            (*(sSharedDisplayData.hintSessionSupported) == NO_ERROR);
    mUsePowerHintSession.store(out);
    return out;
}

bool ExynosDisplay::PowerHalHintWorker::needUpdateTargetWorkDurationLocked() {
    if (!usePowerHintSession() || sNormalizeTarget) return false;
    // to disable the rate limiter we just use a max deviation of 1
    nsecs_t maxDeviation = sUseRateLimiter ? kAllowedDeviation.count() : 1;
    // report if the change in target from our last submission to now exceeds the threshold
    return abs(mTargetWorkDuration - mLastTargetDurationReported) >= maxDeviation;
}

bool ExynosDisplay::PowerHalHintWorker::needSendActualWorkDurationLocked() {
    if (!usePowerHintSession() || mPowerHintQueue.size() == 0 || !mActualWorkDuration.has_value()) {
        return false;
    }
    if (!mLastErrorSent.has_value() ||
        (systemTime(SYSTEM_TIME_MONOTONIC) - mLastActualReportTimestamp) > kStaleTimeout.count()) {
        return true;
    }
    // to effectively disable the rate limiter we just use a max deviation of 1
    nsecs_t maxDeviation = sUseRateLimiter ? kAllowedDeviation.count() : 1;
    // report if the change in error term from our last submission to now exceeds the threshold
    return abs((*mActualWorkDuration - mTargetWorkDuration) - *mLastErrorSent) >= maxDeviation;
}

// track the tid of any thread that calls in and remove it on thread death
void ExynosDisplay::PowerHalHintWorker::trackThisThread() {
    thread_local struct TidTracker {
        TidTracker(PowerHalHintWorker *worker) : mWorker(worker) {
            mTid = gettid();
            mWorker->addBinderTid(mTid);
        }
        ~TidTracker() { mWorker->removeBinderTid(mTid); }
        pid_t mTid;
        PowerHalHintWorker *mWorker;
    } tracker(this);
}

const bool ExynosDisplay::PowerHalHintWorker::sTraceHintSessionData =
        base::GetBoolProperty(std::string("debug.hwc.trace_hint_sessions"), false);

const bool ExynosDisplay::PowerHalHintWorker::sNormalizeTarget =
        base::GetBoolProperty(std::string("debug.hwc.normalize_hint_session_durations"), false);

const bool ExynosDisplay::PowerHalHintWorker::sUseRateLimiter =
        base::GetBoolProperty(std::string("debug.hwc.use_rate_limiter"), true);

ExynosDisplay::PowerHalHintWorker::SharedDisplayData
        ExynosDisplay::PowerHalHintWorker::sSharedDisplayData;

std::mutex ExynosDisplay::PowerHalHintWorker::sSharedDisplayMutex;

int ExynosSortedLayer::compare(ExynosLayer * const *lhs, ExynosLayer *const *rhs)
{
    ExynosLayer *left = *((ExynosLayer**)(lhs));
    ExynosLayer *right = *((ExynosLayer**)(rhs));
    return left->mZOrder > right->mZOrder;
}

ssize_t ExynosSortedLayer::remove(const ExynosLayer *item)
{
    for (size_t i = 0; i < size(); i++)
    {
        if (array()[i] == item)
        {
            removeAt(i);
            return i;
        }
    }
    return -1;
}

status_t ExynosSortedLayer::vector_sort()
{
    return sort(compare);
}

ExynosLowFpsLayerInfo::ExynosLowFpsLayerInfo()
    : mHasLowFpsLayer(false),
    mFirstIndex(-1),
    mLastIndex(-1)
{
}

void ExynosLowFpsLayerInfo::initializeInfos()
{
    mHasLowFpsLayer = false;
    mFirstIndex = -1;
    mLastIndex = -1;
}

int32_t ExynosLowFpsLayerInfo::addLowFpsLayer(uint32_t layerIndex)
{
    if (mHasLowFpsLayer == false) {
        mFirstIndex = layerIndex;
        mLastIndex = layerIndex;
        mHasLowFpsLayer = true;
    } else {
        mFirstIndex = min(mFirstIndex, (int32_t)layerIndex);
        mLastIndex = max(mLastIndex, (int32_t)layerIndex);
    }
    return NO_ERROR;
}

ExynosCompositionInfo::ExynosCompositionInfo(uint32_t type)
    : ExynosMPPSource(MPP_SOURCE_COMPOSITION_TARGET, this),
    mType(type),
    mHasCompositionLayer(false),
    mFirstIndex(-1),
    mLastIndex(-1),
    mTargetBuffer(NULL),
    mDataSpace(HAL_DATASPACE_UNKNOWN),
    mAcquireFence(-1),
    mReleaseFence(-1),
    mEnableSkipStatic(false),
    mSkipStaticInitFlag(false),
    mSkipFlag(false),
    mWindowIndex(-1)
{
    /* If AFBC compression of mTargetBuffer is changed, */
    /* mCompressionInfo should be set properly before resource assigning */

    char value[256];
    int afbc_prop;
    property_get("ro.vendor.ddk.set.afbc", value, "0");
    afbc_prop = atoi(value);

    if (afbc_prop == 0)
        mCompressionInfo.type = COMP_TYPE_NONE;
    else
        mCompressionInfo.type = COMP_TYPE_AFBC;

    memset(&mSkipSrcInfo, 0, sizeof(mSkipSrcInfo));
    for (int i = 0; i < NUM_SKIP_STATIC_LAYER; i++) {
        mSkipSrcInfo.srcInfo[i].acquireFenceFd = -1;
        mSkipSrcInfo.srcInfo[i].releaseFenceFd = -1;
        mSkipSrcInfo.dstInfo[i].acquireFenceFd = -1;
        mSkipSrcInfo.dstInfo[i].releaseFenceFd = -1;
    }

    if(type == COMPOSITION_CLIENT)
        mEnableSkipStatic = true;

    memset(&mLastWinConfigData, 0x0, sizeof(mLastWinConfigData));
    mLastWinConfigData.acq_fence = -1;
    mLastWinConfigData.rel_fence = -1;
}

void ExynosCompositionInfo::initializeInfosComplete(ExynosDisplay *display)
{
    mTargetBuffer = NULL;
    mDataSpace = HAL_DATASPACE_UNKNOWN;
    if (mAcquireFence >= 0) {
        ALOGD("ExynosCompositionInfo(%d):: mAcquire is not initialized(%d)", mType, mAcquireFence);
        if (display != NULL)
            fence_close(mAcquireFence, display, FENCE_TYPE_UNDEFINED, FENCE_IP_UNDEFINED);
    }
    mAcquireFence = -1;
    initializeInfos(display);
}

void ExynosCompositionInfo::initializeInfos(ExynosDisplay *display)
{
    mHasCompositionLayer = false;
    mFirstIndex = -1;
    mLastIndex = -1;

    if (mType != COMPOSITION_CLIENT) {
        mTargetBuffer = NULL;
        mDataSpace = HAL_DATASPACE_UNKNOWN;
        if (mAcquireFence >= 0) {
            ALOGD("ExynosCompositionInfo(%d):: mAcquire is not initialized(%d)", mType, mAcquireFence);
            if (display != NULL)
                fence_close(mAcquireFence, display, FENCE_TYPE_UNDEFINED, FENCE_IP_UNDEFINED);
        }
        mAcquireFence = -1;
    }

    if (mReleaseFence >= 0) {
        ALOGD("ExynosCompositionInfo(%d):: mReleaseFence is not initialized(%d)", mType, mReleaseFence);
        if (display!= NULL)
            fence_close(mReleaseFence, display, FENCE_TYPE_UNDEFINED, FENCE_IP_UNDEFINED);
    }
    mReleaseFence = -1;

    mWindowIndex = -1;
    mOtfMPP = NULL;
    mM2mMPP = NULL;
    if ((display != NULL) &&
        (display->mType == HWC_DISPLAY_VIRTUAL) &&
        (mType == COMPOSITION_EXYNOS)) {
        mM2mMPP = display->mResourceManager->getExynosMPP(MPP_LOGICAL_G2D_COMBO);
    }
}

void ExynosCompositionInfo::setTargetBuffer(ExynosDisplay *display, buffer_handle_t handle,
        int32_t acquireFence, android_dataspace dataspace)
{
    mTargetBuffer = handle;
    if (mType == COMPOSITION_CLIENT) {
        if (display != NULL) {
            if (mAcquireFence >= 0) {
                mAcquireFence =
                        fence_close(mAcquireFence, display, FENCE_TYPE_SRC_ACQUIRE, FENCE_IP_FB);
            }
            mAcquireFence = hwcCheckFenceDebug(display, FENCE_TYPE_DST_ACQUIRE, FENCE_IP_FB, acquireFence);
        }
    } else {
        if (display != NULL) {
            if (mAcquireFence >= 0) {
                mAcquireFence =
                        fence_close(mAcquireFence, display, FENCE_TYPE_SRC_ACQUIRE, FENCE_IP_G2D);
            }
            mAcquireFence = hwcCheckFenceDebug(display, FENCE_TYPE_DST_ACQUIRE, FENCE_IP_G2D, acquireFence);
        }
    }
    if ((display != NULL) && (mDataSpace != dataspace))
        display->setGeometryChanged(GEOMETRY_DISPLAY_DATASPACE_CHANGED);
    mDataSpace = dataspace;
}

void ExynosCompositionInfo::setCompressionType(uint32_t compressionType) {
    mCompressionInfo.type = compressionType;
}

void ExynosCompositionInfo::dump(String8& result) const {
    result.appendFormat("CompositionInfo (%d)\n", mType);
    result.appendFormat("mHasCompositionLayer(%d)\n", mHasCompositionLayer);
    if (mHasCompositionLayer) {
        result.appendFormat("\tfirstIndex: %d, lastIndex: %d, dataSpace: 0x%8x, compression: %s, "
                            "windowIndex: %d\n",
                            mFirstIndex, mLastIndex, mDataSpace,
                            getCompressionStr(mCompressionInfo).c_str(), mWindowIndex);
        result.appendFormat("\thandle: %p, acquireFence: %d, releaseFence: %d, skipFlag: %d",
                mTargetBuffer, mAcquireFence, mReleaseFence, mSkipFlag);
        if ((mOtfMPP == NULL) && (mM2mMPP == NULL))
            result.appendFormat("\tresource is not assigned\n");
        if (mOtfMPP != NULL)
            result.appendFormat("\tassignedMPP: %s\n", mOtfMPP->mName.c_str());
        if (mM2mMPP != NULL)
            result.appendFormat("\t%s\n", mM2mMPP->mName.c_str());
    }
    if (mTargetBuffer != NULL) {
        uint64_t internal_format = 0;
        internal_format = VendorGraphicBufferMeta::get_internal_format(mTargetBuffer);
        result.appendFormat("\tinternal_format: 0x%" PRIx64 ", afbc: %d\n", internal_format,
                            isAFBCCompressed(mTargetBuffer));
    }
    uint32_t assignedSrcNum = 0;
    if ((mM2mMPP != NULL) &&
        ((assignedSrcNum = mM2mMPP->mAssignedSources.size()) > 0)) {
        result.appendFormat("\tAssigned source num: %d\n", assignedSrcNum);
        result.append("\t");
        for (uint32_t i = 0; i < assignedSrcNum; i++) {
            if (mM2mMPP->mAssignedSources[i]->mSourceType == MPP_SOURCE_LAYER) {
                ExynosLayer* layer = (ExynosLayer*)(mM2mMPP->mAssignedSources[i]);
                result.appendFormat("[%d]layer_%p ", i, layer->mLayerBuffer);
            } else {
                result.appendFormat("[%d]sourceType_%d ", i, mM2mMPP->mAssignedSources[i]->mSourceType);
            }
        }
        result.append("\n");
    }
    result.append("\n");
}

String8 ExynosCompositionInfo::getTypeStr()
{
    switch(mType) {
    case COMPOSITION_NONE:
        return String8("COMPOSITION_NONE");
    case COMPOSITION_CLIENT:
        return String8("COMPOSITION_CLIENT");
    case COMPOSITION_EXYNOS:
        return String8("COMPOSITION_EXYNOS");
    default:
        return String8("InvalidType");
    }
}

ExynosDisplay::ExynosDisplay(uint32_t type, uint32_t index, ExynosDevice* device,
                             const std::string& displayName)
      : mDisplayId(getDisplayId(type, index)),
        mType(type),
        mIndex(index),
        mDeconNodeName(""),
        mXres(1440),
        mYres(2960),
        mXdpi(25400),
        mYdpi(25400),
        mVsyncPeriod(kDefaultVsyncPeriodNanoSecond),
        mBtsFrameScanoutPeriod(kDefaultVsyncPeriodNanoSecond),
        mBtsPendingOperationRatePeriod(0),
        mDevice(device),
        mDisplayName(displayName.c_str()),
        mDisplayTraceName(String8::format("%s(%d)", displayName.c_str(), mDisplayId)),
        mPlugState(false),
        mHasSingleBuffer(false),
        mResourceManager(NULL),
        mClientCompositionInfo(COMPOSITION_CLIENT),
        mExynosCompositionInfo(COMPOSITION_EXYNOS),
        mGeometryChanged(0x0),
        mBufferUpdates(0),
        mRenderingState(RENDERING_STATE_NONE),
        mHWCRenderingState(RENDERING_STATE_NONE),
        mDisplayBW(0),
        mDynamicReCompMode(CLIENT_2_DEVICE),
        mDREnable(false),
        mDRDefault(false),
        mLastFpsTime(0),
        mFrameCount(0),
        mLastFrameCount(0),
        mErrorFrameCount(0),
        mUpdateEventCnt(0),
        mUpdateCallCnt(0),
        mDefaultDMA(MAX_DECON_DMA_TYPE),
        mLastRetireFence(-1),
        mWindowNumUsed(0),
        mBaseWindowIndex(0),
        mNumMaxPriorityAllowed(1),
        mCursorIndex(-1),
        mColorTransformHint(HAL_COLOR_TRANSFORM_IDENTITY),
        mMaxLuminance(0),
        mMaxAverageLuminance(0),
        mMinLuminance(0),
        mHWC1LayerList(NULL),
        /* Support DDI scalser */
        mOldScalerMode(0),
        mNewScaledWidth(0),
        mNewScaledHeight(0),
        mDeviceXres(0),
        mDeviceYres(0),
        mColorMode(HAL_COLOR_MODE_NATIVE),
        mSkipFrame(false),
        mVsyncPeriodChangeConstraints{systemTime(SYSTEM_TIME_MONOTONIC), 0},
        mVsyncAppliedTimeLine{false, 0, systemTime(SYSTEM_TIME_MONOTONIC)},
        mConfigRequestState(hwc_request_state_t::SET_CONFIG_STATE_DONE),
        mPowerHalHint(mDisplayId, mDisplayTraceName),
        mErrLogFileWriter(2, ERR_LOG_SIZE),
        mDebugDumpFileWriter(10, 1, ".dump"),
        mFenceFileWriter(2, FENCE_ERR_LOG_SIZE),
        mOperationRateManager(nullptr) {
    mDisplayControl.enableCompositionCrop = true;
    mDisplayControl.enableExynosCompositionOptimization = true;
    mDisplayControl.enableClientCompositionOptimization = true;
    mDisplayControl.useMaxG2DSrc = false;
    mDisplayControl.handleLowFpsLayers = false;
    mDisplayControl.earlyStartMPP = true;
    mDisplayControl.adjustDisplayFrame = false;
    mDisplayControl.cursorSupport = false;

    mDisplayConfigs.clear();

    mPowerModeState = std::nullopt;

    mVsyncState = HWC2_VSYNC_DISABLE;

    /* TODO : Exception handling here */

    if (device == NULL) {
        ALOGE("Display creation failed!");
        return;
    }

    mResourceManager = device->mResourceManager;

    /* The number of window is same with the number of otfMPP */
    mMaxWindowNum = mResourceManager->getOtfMPPs().size();

    mDpuData.init(mMaxWindowNum, 0);
    mLastDpuData.init(mMaxWindowNum, 0);
    ALOGI("window configs size(%zu)", mDpuData.configs.size());

    mLowFpsLayerInfo.initializeInfos();

    mPowerHalHint.Init();

    mUseDpu = true;
    mHpdStatus = false;

    return;
}

ExynosDisplay::~ExynosDisplay()
{
}

/**
 * Member function for Dynamic AFBC Control solution.
 */
bool ExynosDisplay::comparePreferedLayers() {
    return false;
}

int ExynosDisplay::getId() {
    return mDisplayId;
}

void ExynosDisplay::initDisplay() {
    mClientCompositionInfo.initializeInfos(this);
    mClientCompositionInfo.mEnableSkipStatic = true;
    mClientCompositionInfo.mSkipStaticInitFlag = false;
    mClientCompositionInfo.mSkipFlag = false;
    memset(&mClientCompositionInfo.mSkipSrcInfo, 0x0, sizeof(mClientCompositionInfo.mSkipSrcInfo));
    for (int i = 0; i < NUM_SKIP_STATIC_LAYER; i++) {
        mClientCompositionInfo.mSkipSrcInfo.srcInfo[i].acquireFenceFd = -1;
        mClientCompositionInfo.mSkipSrcInfo.srcInfo[i].releaseFenceFd = -1;
        mClientCompositionInfo.mSkipSrcInfo.dstInfo[i].acquireFenceFd = -1;
        mClientCompositionInfo.mSkipSrcInfo.dstInfo[i].releaseFenceFd = -1;
    }
    memset(&mClientCompositionInfo.mLastWinConfigData, 0x0, sizeof(mClientCompositionInfo.mLastWinConfigData));
    mClientCompositionInfo.mLastWinConfigData.acq_fence = -1;
    mClientCompositionInfo.mLastWinConfigData.rel_fence = -1;

    mExynosCompositionInfo.initializeInfos(this);
    mExynosCompositionInfo.mEnableSkipStatic = false;
    mExynosCompositionInfo.mSkipStaticInitFlag = false;
    mExynosCompositionInfo.mSkipFlag = false;
    memset(&mExynosCompositionInfo.mSkipSrcInfo, 0x0, sizeof(mExynosCompositionInfo.mSkipSrcInfo));
    for (int i = 0; i < NUM_SKIP_STATIC_LAYER; i++) {
        mExynosCompositionInfo.mSkipSrcInfo.srcInfo[i].acquireFenceFd = -1;
        mExynosCompositionInfo.mSkipSrcInfo.srcInfo[i].releaseFenceFd = -1;
        mExynosCompositionInfo.mSkipSrcInfo.dstInfo[i].acquireFenceFd = -1;
        mExynosCompositionInfo.mSkipSrcInfo.dstInfo[i].releaseFenceFd = -1;
    }

    memset(&mExynosCompositionInfo.mLastWinConfigData, 0x0, sizeof(mExynosCompositionInfo.mLastWinConfigData));
    mExynosCompositionInfo.mLastWinConfigData.acq_fence = -1;
    mExynosCompositionInfo.mLastWinConfigData.rel_fence = -1;

    mGeometryChanged = 0x0;
    mRenderingState = RENDERING_STATE_NONE;
    mDisplayBW = 0;
    mDynamicReCompMode = CLIENT_2_DEVICE;
    mCursorIndex = -1;

    mDpuData.reset();
    mLastDpuData.reset();

    if (mDisplayControl.earlyStartMPP == true) {
        for (size_t i = 0; i < mLayers.size(); i++) {
            exynos_image outImage;
            ExynosMPP* m2mMPP = mLayers[i]->mM2mMPP;

            /* Close release fence of dst buffer of last frame */
            if ((mLayers[i]->mValidateCompositionType == HWC2_COMPOSITION_DEVICE) &&
                (m2mMPP != NULL) &&
                (m2mMPP->mAssignedDisplay == this) &&
                (m2mMPP->getDstImageInfo(&outImage) == NO_ERROR)) {
                if (m2mMPP->mPhysicalType == MPP_MSC) {
                    fence_close(outImage.releaseFenceFd, this, FENCE_TYPE_DST_RELEASE, FENCE_IP_MSC);
                } else if (m2mMPP->mPhysicalType == MPP_G2D) {
                    fence_close(outImage.releaseFenceFd, this, FENCE_TYPE_DST_RELEASE, FENCE_IP_G2D);
                } else {
                    DISPLAY_LOGE("[%zu] layer has invalid mppType(%d)", i, m2mMPP->mPhysicalType);
                    fence_close(outImage.releaseFenceFd, this, FENCE_TYPE_DST_RELEASE, FENCE_IP_ALL);
                }
                m2mMPP->resetDstReleaseFence();
            }
        }
    }
}

/**
 * @param outLayer
 * @return int32_t
 */
int32_t ExynosDisplay::destroyLayer(hwc2_layer_t outLayer) {

    Mutex::Autolock lock(mDRMutex);
    ExynosLayer *layer = (ExynosLayer *)outLayer;

    if (layer == nullptr) {
        return HWC2_ERROR_BAD_LAYER;
    }

    if (mLayers.remove(layer) < 0) {
        auto it = std::find(mIgnoreLayers.begin(), mIgnoreLayers.end(), layer);
        if (it == mIgnoreLayers.end()) {
            ALOGE("%s:: There is no layer", __func__);
        } else {
            mIgnoreLayers.erase(it);
        }
    } else {
        setGeometryChanged(GEOMETRY_DISPLAY_LAYER_REMOVED);
    }

    mDisplayInterface->destroyLayer(layer);
    layer->resetAssignedResource();

    delete layer;

    if (mPlugState == false) {
        DISPLAY_LOGI("%s : destroyLayer is done. But display is already disconnected",
                __func__);
        return HWC2_ERROR_BAD_DISPLAY;
    }

    return HWC2_ERROR_NONE;
}

/**
 * @return void
 */
void ExynosDisplay::destroyLayers() {
    Mutex::Autolock lock(mDRMutex);
    for (uint32_t index = 0; index < mLayers.size();) {
        ExynosLayer *layer = mLayers[index];
        mLayers.removeAt(index);
        delete layer;
    }

    for (auto it = mIgnoreLayers.begin(); it != mIgnoreLayers.end();) {
        ExynosLayer *layer = *it;
        it = mIgnoreLayers.erase(it);
        delete layer;
    }
}

ExynosLayer *ExynosDisplay::checkLayer(hwc2_layer_t addr) {
    ExynosLayer *temp = (ExynosLayer *)addr;
    if (!mLayers.isEmpty()) {
        for (size_t i = 0; i < mLayers.size(); i++) {
            if (mLayers[i] == temp)
                return mLayers[i];
        }
    }

    if (mIgnoreLayers.size()) {
        auto it = std::find(mIgnoreLayers.begin(), mIgnoreLayers.end(), temp);
        return (it == mIgnoreLayers.end()) ? NULL : *it;
    }

    ALOGE("HWC2 : %s : %d, wrong layer request!", __func__, __LINE__);
    return NULL;
}

void ExynosDisplay::checkIgnoreLayers() {
    Mutex::Autolock lock(mDRMutex);
    for (auto it = mIgnoreLayers.begin(); it != mIgnoreLayers.end();) {
        ExynosLayer *layer = *it;
        if ((layer->mLayerFlag & EXYNOS_HWC_IGNORE_LAYER) == 0) {
            mLayers.push_back(layer);
            it = mIgnoreLayers.erase(it);
        } else {
            it++;
        }
    }

    for (uint32_t index = 0; index < mLayers.size();) {
        ExynosLayer *layer = mLayers[index];
        if (layer->mLayerFlag & EXYNOS_HWC_IGNORE_LAYER) {
            layer->resetValidateData();
            layer->mValidateCompositionType = HWC2_COMPOSITION_DEVICE;
            /*
             * Directly close without counting down
             * because it was not counted by validate
             */
            if (layer->mAcquireFence > 0) {
                close(layer->mAcquireFence);
            }
            layer->mAcquireFence = -1;

            layer->mReleaseFence = -1;
            mIgnoreLayers.push_back(layer);
            mLayers.removeAt(index);
        } else {
            index++;
        }
    }
}

/**
 * @return void
 */
void ExynosDisplay::doPreProcessing() {
    /* Low persistence setting */
    int ret = 0;
    bool hasSingleBuffer = false;
    bool hasClientLayer = false;

    for (size_t i=0; i < mLayers.size(); i++) {
        buffer_handle_t handle = mLayers[i]->mLayerBuffer;
        VendorGraphicBufferMeta gmeta(handle);
        if (mLayers[i]->mCompositionType == HWC2_COMPOSITION_CLIENT) {
            hasClientLayer = true;
        }

        exynos_image srcImg;
        exynos_image dstImg;
        mLayers[i]->setSrcExynosImage(&srcImg);
        mLayers[i]->setDstExynosImage(&dstImg);
        mLayers[i]->setExynosImage(srcImg, dstImg);
    }

    /*
     * Disable skip static layer feature if there is the layer that's
     * mCompositionType  is HWC2_COMPOSITION_CLIENT
     * HWC should not change compositionType if it is HWC2_COMPOSITION_CLIENT
     */
    if (mType != HWC_DISPLAY_VIRTUAL)
        mClientCompositionInfo.mEnableSkipStatic = (!hasClientLayer && !hasSingleBuffer);

    if (mHasSingleBuffer != hasSingleBuffer) {
        if ((ret = mDisplayInterface->disableSelfRefresh(uint32_t(hasSingleBuffer))) < 0)
            DISPLAY_LOGE("ioctl S3CFB_LOW_PERSISTENCE failed: %s ret(%d)", strerror(errno), ret);

        mDisplayControl.skipM2mProcessing = !hasSingleBuffer;
        mHasSingleBuffer = hasSingleBuffer;
        setGeometryChanged(GEOMETRY_DISPLAY_SINGLEBUF_CHANGED);
    }

    if ((exynosHWCControl.displayMode < DISPLAY_MODE_NUM) &&
        (mDevice->mDisplayMode != exynosHWCControl.displayMode))
        setGeometryChanged(GEOMETRY_DEVICE_DISP_MODE_CHAGED);

    if ((ret = mResourceManager->checkScenario(this)) != NO_ERROR)
        DISPLAY_LOGE("checkScenario error ret(%d)", ret);

    if (exynosHWCControl.skipResourceAssign == 0) {
        /* Set any flag to mGeometryChanged */
        setGeometryChanged(GEOMETRY_DEVICE_SCENARIO_CHANGED);
    }
#ifdef HWC_NO_SUPPORT_SKIP_VALIDATE
    if (mDevice->checkNonInternalConnection()) {
        /* Set any flag to mGeometryChanged */
        mDevice->mGeometryChanged = 0x10;
    }
#endif

    return;
}

/**
 * @return int
 */
int ExynosDisplay::checkLayerFps() {
    mLowFpsLayerInfo.initializeInfos();

    if (mDisplayControl.handleLowFpsLayers == false)
        return NO_ERROR;

    Mutex::Autolock lock(mDRMutex);

    for (size_t i=0; i < mLayers.size(); i++) {
        if ((mLayers[i]->mOverlayPriority < ePriorityHigh) &&
            (mLayers[i]->getFps() < LOW_FPS_THRESHOLD)) {
            mLowFpsLayerInfo.addLowFpsLayer(i);
        } else if (mLowFpsLayerInfo.mHasLowFpsLayer == true) {
            break;
        }
    }
    /* There is only one low fps layer, Overlay is better in this case */
    if ((mLowFpsLayerInfo.mHasLowFpsLayer == true) &&
        (mLowFpsLayerInfo.mFirstIndex == mLowFpsLayerInfo.mLastIndex))
        mLowFpsLayerInfo.initializeInfos();

    return NO_ERROR;
}

int ExynosDisplay::switchDynamicReCompMode(dynamic_recomp_mode mode) {
    if (mDynamicReCompMode == mode) return NO_MODE_SWITCH;

    ATRACE_INT("Force client composition by DR", (mode == DEVICE_2_CLIENT));
    mDynamicReCompMode = mode;
    setGeometryChanged(GEOMETRY_DISPLAY_DYNAMIC_RECOMPOSITION);
    return mode;
}

/**
 * @return int
 */
int ExynosDisplay::checkDynamicReCompMode() {
    ATRACE_CALL();
    Mutex::Autolock lock(mDRMutex);

    if (!exynosHWCControl.useDynamicRecomp) {
        mLastModeSwitchTimeStamp = 0;
        return switchDynamicReCompMode(CLIENT_2_DEVICE);
    }

    /* initialize the Timestamps */
    if (!mLastModeSwitchTimeStamp) {
        mLastModeSwitchTimeStamp = mLastUpdateTimeStamp;
        return switchDynamicReCompMode(CLIENT_2_DEVICE);
    }

    /* Avoid to use DEVICE_2_CLIENT if there's a layer with priority >= ePriorityHigh such as:
     * front buffer, video layer, HDR, DRM layer, etc.
     */
    for (size_t i = 0; i < mLayers.size(); i++) {
        if ((mLayers[i]->mOverlayPriority >= ePriorityHigh) ||
            mLayers[i]->mPreprocessedInfo.preProcessed) {
            auto ret = switchDynamicReCompMode(CLIENT_2_DEVICE);
            if (ret) {
                mUpdateCallCnt = 0;
                mLastModeSwitchTimeStamp = mLastUpdateTimeStamp;
                DISPLAY_LOGD(eDebugDynamicRecomp, "[DYNAMIC_RECOMP] GLES_2_HWC by video layer");
            }
            return ret;
        }
    }

    unsigned int incomingPixels = 0;
    hwc_rect_t dispRect = {INT_MAX, INT_MAX, 0, 0};
    for (size_t i = 0; i < mLayers.size(); i++) {
        auto& r = mLayers[i]->mPreprocessedInfo.displayFrame;
        if (r.top < dispRect.top) dispRect.top = r.top;
        if (r.left < dispRect.left) dispRect.left = r.left;
        if (r.bottom > dispRect.bottom) dispRect.bottom = r.bottom;
        if (r.right > dispRect.right) dispRect.right = r.right;
        auto w = WIDTH(r);
        auto h = HEIGHT(r);
        incomingPixels += w * h;
    }

    /* Mode Switch is not required if total pixels are not more than the threshold */
    unsigned int mergedDisplayFrameSize = WIDTH(dispRect) * HEIGHT(dispRect);
    if (incomingPixels <= mergedDisplayFrameSize) {
        auto ret = switchDynamicReCompMode(CLIENT_2_DEVICE);
        if (ret) {
            mUpdateCallCnt = 0;
            mLastModeSwitchTimeStamp = mLastUpdateTimeStamp;
            DISPLAY_LOGD(eDebugDynamicRecomp, "[DYNAMIC_RECOMP] GLES_2_HWC by BW check");
        }
        return ret;
    }

    /*
     * There will be at least one composition call per one minute (because of time update)
     * To minimize the analysis overhead, just analyze it once in 5 second
     */
    auto timeStampDiff = systemTime(SYSTEM_TIME_MONOTONIC) - mLastModeSwitchTimeStamp;

    /*
     * previous CompModeSwitch was CLIENT_2_DEVICE: check fps after 5s from mLastModeSwitchTimeStamp
     * previous CompModeSwitch was DEVICE_2_CLIENT: check immediately
     */
    if ((mDynamicReCompMode != DEVICE_2_CLIENT) && (timeStampDiff < kLayerFpsStableTimeNs))
        return 0;

    mLastModeSwitchTimeStamp = mLastUpdateTimeStamp;
    float updateFps = 0;
    if ((mUpdateEventCnt != 1) &&
        (mDynamicReCompMode == DEVICE_2_CLIENT) && (mUpdateCallCnt == 1)) {
        DISPLAY_LOGD(eDebugDynamicRecomp, "[DYNAMIC_RECOMP] first frame after DEVICE_2_CLIENT");
        updateFps = kDynamicRecompFpsThreshold + 1;
    } else {
        float maxFps = 0;
        for (uint32_t i = 0; i < mLayers.size(); i++) {
            float layerFps = mLayers[i]->checkFps(/* increaseCount */ false);
            if (maxFps < layerFps) maxFps = layerFps;
        }
        updateFps = maxFps;
    }
    mUpdateCallCnt = 0;

    /*
     * FPS estimation.
     * If FPS is lower than kDynamicRecompFpsThreshold, try to switch the mode to GLES
     */
    if (updateFps < kDynamicRecompFpsThreshold) {
        auto ret = switchDynamicReCompMode(DEVICE_2_CLIENT);
        if (ret) {
            DISPLAY_LOGD(eDebugDynamicRecomp, "[DYNAMIC_RECOMP] DEVICE_2_CLIENT by low FPS(%.2f)",
                         updateFps);
        }
        return ret;
    } else {
        auto ret = switchDynamicReCompMode(CLIENT_2_DEVICE);
        if (ret) {
            DISPLAY_LOGD(eDebugDynamicRecomp, "[DYNAMIC_RECOMP] CLIENT_2_HWC by high FPS((%.2f)",
                         updateFps);
        }
        return ret;
    }

    return 0;
}

/**
 * @return int
 */
int ExynosDisplay::handleDynamicReCompMode() {
    return 0;
}

/**
 * @param changedBit
 * @return int
 */
void ExynosDisplay::setGeometryChanged(uint64_t changedBit) {
    mGeometryChanged |= changedBit;
    mDevice->setGeometryChanged(changedBit);
}

void ExynosDisplay::clearGeometryChanged()
{
    mGeometryChanged = 0;
    mBufferUpdates = 0;
    for (size_t i=0; i < mLayers.size(); i++) {
        mLayers[i]->clearGeometryChanged();
    }
}

int ExynosDisplay::handleStaticLayers(ExynosCompositionInfo& compositionInfo)
{
    if (compositionInfo.mType != COMPOSITION_CLIENT)
        return -EINVAL;

    if (mType == HWC_DISPLAY_VIRTUAL)
        return NO_ERROR;

    if (compositionInfo.mHasCompositionLayer == false) {
        DISPLAY_LOGD(eDebugSkipStaicLayer, "there is no client composition");
        return NO_ERROR;
    }
    if ((compositionInfo.mWindowIndex < 0) ||
        (compositionInfo.mWindowIndex >= (int32_t)mDpuData.configs.size()))
    {
        DISPLAY_LOGE("invalid mWindowIndex(%d)", compositionInfo.mWindowIndex);
        return -EINVAL;
    }

    exynos_win_config_data &config = mDpuData.configs[compositionInfo.mWindowIndex];

    /* Store configuration of client target configuration */
    if (compositionInfo.mSkipFlag == false) {
        compositionInfo.mLastWinConfigData = config;
        DISPLAY_LOGD(eDebugSkipStaicLayer, "config[%d] is stored",
                compositionInfo.mWindowIndex);
    } else {
        for (size_t i = (size_t)compositionInfo.mFirstIndex; i <= (size_t)compositionInfo.mLastIndex; i++) {
            if ((mLayers[i]->mExynosCompositionType == HWC2_COMPOSITION_CLIENT) &&
                (mLayers[i]->mAcquireFence >= 0))
                fence_close(mLayers[i]->mAcquireFence, this, FENCE_TYPE_SRC_ACQUIRE, FENCE_IP_ALL);
            mLayers[i]->mAcquireFence = -1;
            mLayers[i]->mReleaseFence = -1;
        }

        if (compositionInfo.mTargetBuffer == NULL) {
            fence_close(config.acq_fence, this,
                    FENCE_TYPE_SRC_ACQUIRE, FENCE_IP_ALL);

            config = compositionInfo.mLastWinConfigData;
            /* Assigned otfMPP for client target can be changed */
            config.assignedMPP = compositionInfo.mOtfMPP;
            /* acq_fence was closed by DPU driver in the previous frame */
            config.acq_fence = -1;
        } else {
            /* Check target buffer is same with previous frame */
            if (!std::equal(config.fd_idma, config.fd_idma+3, compositionInfo.mLastWinConfigData.fd_idma)) {
                DISPLAY_LOGE("Current config [%d][%d, %d, %d]",
                        compositionInfo.mWindowIndex,
                        config.fd_idma[0], config.fd_idma[1], config.fd_idma[2]);
                DISPLAY_LOGE("=============================  dump last win configs  ===================================");
                for (size_t i = 0; i < mLastDpuData.configs.size(); i++) {
                    android::String8 result;
                    result.appendFormat("config[%zu]\n", i);
                    dumpConfig(result, mLastDpuData.configs[i]);
                    DISPLAY_LOGE("%s", result.c_str());
                }
                DISPLAY_LOGE("compositionInfo.mLastWinConfigData config [%d, %d, %d]",
                        compositionInfo.mLastWinConfigData.fd_idma[0],
                        compositionInfo.mLastWinConfigData.fd_idma[1],
                        compositionInfo.mLastWinConfigData.fd_idma[2]);
                return -EINVAL;
            }
        }

        DISPLAY_LOGD(eDebugSkipStaicLayer, "skipStaticLayer config[%d]", compositionInfo.mWindowIndex);
        dumpConfig(config);
    }

    return NO_ERROR;
}

bool ExynosDisplay::skipStaticLayerChanged(ExynosCompositionInfo& compositionInfo)
{
    if ((int)compositionInfo.mSkipSrcInfo.srcNum !=
            (compositionInfo.mLastIndex - compositionInfo.mFirstIndex + 1)) {
        DISPLAY_LOGD(eDebugSkipStaicLayer, "Client composition number is changed (%d -> %d)",
                compositionInfo.mSkipSrcInfo.srcNum,
                compositionInfo.mLastIndex - compositionInfo.mFirstIndex + 1);
        return true;
    }

    bool isChanged = false;
    for (size_t i = (size_t)compositionInfo.mFirstIndex; i <= (size_t)compositionInfo.mLastIndex; i++) {
        ExynosLayer *layer = mLayers[i];
        size_t index = i - compositionInfo.mFirstIndex;
        if ((layer->mLayerBuffer == NULL) ||
            (compositionInfo.mSkipSrcInfo.srcInfo[index].bufferHandle != layer->mLayerBuffer))
        {
            isChanged = true;
            DISPLAY_LOGD(eDebugSkipStaicLayer, "layer[%zu] handle is changed"\
                    " handle(%p -> %p), layerFlag(0x%8x)",
                    i, compositionInfo.mSkipSrcInfo.srcInfo[index].bufferHandle,
                    layer->mLayerBuffer, layer->mLayerFlag);
            break;
        } else if ((compositionInfo.mSkipSrcInfo.srcInfo[index].x != layer->mSrcImg.x) ||
                (compositionInfo.mSkipSrcInfo.srcInfo[index].y != layer->mSrcImg.y) ||
                (compositionInfo.mSkipSrcInfo.srcInfo[index].w != layer->mSrcImg.w) ||
                (compositionInfo.mSkipSrcInfo.srcInfo[index].h != layer->mSrcImg.h) ||
                (compositionInfo.mSkipSrcInfo.srcInfo[index].dataSpace != layer->mSrcImg.dataSpace) ||
                (compositionInfo.mSkipSrcInfo.srcInfo[index].blending != layer->mSrcImg.blending) ||
                (compositionInfo.mSkipSrcInfo.srcInfo[index].transform != layer->mSrcImg.transform) ||
                (compositionInfo.mSkipSrcInfo.srcInfo[index].planeAlpha != layer->mSrcImg.planeAlpha))
        {
            isChanged = true;
            DISPLAY_LOGD(eDebugSkipStaicLayer, "layer[%zu] source info is changed, "\
                    "x(%d->%d), y(%d->%d), w(%d->%d), h(%d->%d), dataSpace(%d->%d), "\
                    "blending(%d->%d), transform(%d->%d), planeAlpha(%3.1f->%3.1f)", i,
                    compositionInfo.mSkipSrcInfo.srcInfo[index].x, layer->mSrcImg.x,
                    compositionInfo.mSkipSrcInfo.srcInfo[index].y, layer->mSrcImg.y,
                    compositionInfo.mSkipSrcInfo.srcInfo[index].w,  layer->mSrcImg.w,
                    compositionInfo.mSkipSrcInfo.srcInfo[index].h,  layer->mSrcImg.h,
                    compositionInfo.mSkipSrcInfo.srcInfo[index].dataSpace, layer->mSrcImg.dataSpace,
                    compositionInfo.mSkipSrcInfo.srcInfo[index].blending, layer->mSrcImg.blending,
                    compositionInfo.mSkipSrcInfo.srcInfo[index].transform, layer->mSrcImg.transform,
                    compositionInfo.mSkipSrcInfo.srcInfo[index].planeAlpha, layer->mSrcImg.planeAlpha);
            break;
        } else if ((compositionInfo.mSkipSrcInfo.dstInfo[index].x != layer->mDstImg.x) ||
                (compositionInfo.mSkipSrcInfo.dstInfo[index].y != layer->mDstImg.y) ||
                (compositionInfo.mSkipSrcInfo.dstInfo[index].w != layer->mDstImg.w) ||
                (compositionInfo.mSkipSrcInfo.dstInfo[index].h != layer->mDstImg.h))
        {
            isChanged = true;
            DISPLAY_LOGD(eDebugSkipStaicLayer, "layer[%zu] dst info is changed, "\
                    "x(%d->%d), y(%d->%d), w(%d->%d), h(%d->%d)", i,
                    compositionInfo.mSkipSrcInfo.dstInfo[index].x, layer->mDstImg.x,
                    compositionInfo.mSkipSrcInfo.dstInfo[index].y, layer->mDstImg.y,
                    compositionInfo.mSkipSrcInfo.dstInfo[index].w, layer->mDstImg.w,
                    compositionInfo.mSkipSrcInfo.dstInfo[index].h, layer->mDstImg.h);
            break;
        }
    }
    return isChanged;
}

void ExynosDisplay::requestLhbm(bool on) {
    mDevice->onRefresh(mDisplayId);
    if (mBrightnessController) {
        mBrightnessController->processLocalHbm(on);
    }
}

/**
 * @param compositionType
 * @return int
 */
int ExynosDisplay::skipStaticLayers(ExynosCompositionInfo& compositionInfo)
{
    compositionInfo.mSkipFlag = false;

    if (compositionInfo.mType != COMPOSITION_CLIENT)
        return -EINVAL;

    if ((exynosHWCControl.skipStaticLayers == 0) ||
        (compositionInfo.mEnableSkipStatic == false)) {
        DISPLAY_LOGD(eDebugSkipStaicLayer, "skipStaticLayers(%d), mEnableSkipStatic(%d)",
                exynosHWCControl.skipStaticLayers, compositionInfo.mEnableSkipStatic);
        compositionInfo.mSkipStaticInitFlag = false;
        return NO_ERROR;
    }

    if ((compositionInfo.mHasCompositionLayer == false) ||
        (compositionInfo.mFirstIndex < 0) ||
        (compositionInfo.mLastIndex < 0) ||
        ((compositionInfo.mLastIndex - compositionInfo.mFirstIndex + 1) > NUM_SKIP_STATIC_LAYER)) {
        DISPLAY_LOGD(eDebugSkipStaicLayer, "mHasCompositionLayer(%d), mFirstIndex(%d), mLastIndex(%d)",
                compositionInfo.mHasCompositionLayer,
                compositionInfo.mFirstIndex, compositionInfo.mLastIndex);
        compositionInfo.mSkipStaticInitFlag = false;
        return NO_ERROR;
    }

    if (compositionInfo.mSkipStaticInitFlag) {
        bool isChanged = skipStaticLayerChanged(compositionInfo);
        if (isChanged == true) {
            compositionInfo.mSkipStaticInitFlag = false;
            return NO_ERROR;
        }

        for (size_t i = (size_t)compositionInfo.mFirstIndex; i <= (size_t)compositionInfo.mLastIndex; i++) {
            ExynosLayer *layer = mLayers[i];
            if (layer->mValidateCompositionType == COMPOSITION_CLIENT) {
                layer->mOverlayInfo |= eSkipStaticLayer;
            } else {
                compositionInfo.mSkipStaticInitFlag = false;
                if (layer->mOverlayPriority < ePriorityHigh) {
                    DISPLAY_LOGE("[%zu] Invalid layer type: %d",
                            i, layer->mValidateCompositionType);
                    return -EINVAL;
                } else {
                    return NO_ERROR;
                }
            }
        }

        compositionInfo.mSkipFlag = true;
        DISPLAY_LOGD(eDebugSkipStaicLayer, "SkipStaicLayer is enabled");
        return NO_ERROR;
    }

    compositionInfo.mSkipStaticInitFlag = true;
    memset(&compositionInfo.mSkipSrcInfo, 0, sizeof(compositionInfo.mSkipSrcInfo));
    for (int i = 0; i < NUM_SKIP_STATIC_LAYER; i++) {
        compositionInfo.mSkipSrcInfo.srcInfo[i].acquireFenceFd = -1;
        compositionInfo.mSkipSrcInfo.srcInfo[i].releaseFenceFd = -1;
        compositionInfo.mSkipSrcInfo.dstInfo[i].acquireFenceFd = -1;
        compositionInfo.mSkipSrcInfo.dstInfo[i].releaseFenceFd = -1;
    }

    for (size_t i = (size_t)compositionInfo.mFirstIndex; i <= (size_t)compositionInfo.mLastIndex; i++) {
        ExynosLayer *layer = mLayers[i];
        size_t index = i - compositionInfo.mFirstIndex;
        compositionInfo.mSkipSrcInfo.srcInfo[index] = layer->mSrcImg;
        compositionInfo.mSkipSrcInfo.dstInfo[index] = layer->mDstImg;
        DISPLAY_LOGD(eDebugSkipStaicLayer, "mSkipSrcInfo.srcInfo[%zu] is initialized, %p",
                index, layer->mSrcImg.bufferHandle);
    }
    compositionInfo.mSkipSrcInfo.srcNum = (compositionInfo.mLastIndex - compositionInfo.mFirstIndex + 1);
    return NO_ERROR;
}

bool ExynosDisplay::shouldSignalNonIdle(void) {
    // Some cases such that we can skip calling mPowerHalHint.signalNonIdle():
    // 1. Updating source crop or buffer for video layer
    // 2. Updating refresh rate indicator layer
    uint64_t exclude = GEOMETRY_LAYER_SOURCECROP_CHANGED;
    if ((mGeometryChanged & ~exclude) != 0) {
        return true;
    }
    for (size_t i = 0; i < mLayers.size(); i++) {
        // Frame update for refresh rate overlay indicator layer can be ignored
        if (mLayers[i]->mRequestedCompositionType == HWC2_COMPOSITION_REFRESH_RATE_INDICATOR)
            continue;
        // Frame update for video layer can be ignored
        if (mLayers[i]->isLayerFormatYuv()) continue;
        if (mLayers[i]->mLastLayerBuffer != mLayers[i]->mLayerBuffer ||
            mLayers[i]->mGeometryChanged != 0) {
            return true;
        }
    }
    return false;
}

/**
 * @return int
 */
int ExynosDisplay::doPostProcessing() {

    for (size_t i=0; i < mLayers.size(); i++) {
        /* Layer handle back-up */
        mLayers[i]->mLastLayerBuffer = mLayers[i]->mLayerBuffer;
    }
    clearGeometryChanged();

    return 0;
}

bool ExynosDisplay::validateExynosCompositionLayer()
{
    bool isValid = true;
    ExynosMPP *m2mMpp = mExynosCompositionInfo.mM2mMPP;

    int sourceSize = (int)m2mMpp->mAssignedSources.size();
    if ((mExynosCompositionInfo.mFirstIndex >= 0) &&
        (mExynosCompositionInfo.mLastIndex >= 0)) {
        sourceSize = mExynosCompositionInfo.mLastIndex - mExynosCompositionInfo.mFirstIndex + 1;

        if (!mUseDpu && mClientCompositionInfo.mHasCompositionLayer)
            sourceSize++;
    }

    if (m2mMpp->mAssignedSources.size() == 0) {
        DISPLAY_LOGE("No source images");
        isValid = false;
    } else if (mUseDpu && (((mExynosCompositionInfo.mFirstIndex < 0) ||
               (mExynosCompositionInfo.mLastIndex < 0)) ||
               (sourceSize != (int)m2mMpp->mAssignedSources.size()))) {
        DISPLAY_LOGE("Invalid index (%d, %d), size(%zu), sourceSize(%d)",
                mExynosCompositionInfo.mFirstIndex,
                mExynosCompositionInfo.mLastIndex,
                m2mMpp->mAssignedSources.size(),
                sourceSize);
        isValid = false;
    }
    if (isValid == false) {
        for (int32_t i = mExynosCompositionInfo.mFirstIndex; i <= mExynosCompositionInfo.mLastIndex; i++) {
            /* break when only framebuffer target is assigned on ExynosCompositor */
            if (i == -1)
                break;

            if (mLayers[i]->mAcquireFence >= 0)
                fence_close(mLayers[i]->mAcquireFence, this,
                        FENCE_TYPE_SRC_ACQUIRE, FENCE_IP_ALL);
            mLayers[i]->mAcquireFence = -1;
        }
        mExynosCompositionInfo.mM2mMPP->requestHWStateChange(MPP_HW_STATE_IDLE);
    }
    return isValid;
}

/**
 * @return int
 */
int ExynosDisplay::doExynosComposition() {
    int ret = NO_ERROR;
    exynos_image src_img;
    exynos_image dst_img;

    if (mExynosCompositionInfo.mHasCompositionLayer) {
        if (mExynosCompositionInfo.mM2mMPP == NULL) {
            DISPLAY_LOGE("mExynosCompositionInfo.mM2mMPP is NULL");
            return -EINVAL;
        }
        mExynosCompositionInfo.mM2mMPP->requestHWStateChange(MPP_HW_STATE_RUNNING);
        /* mAcquireFence is updated, Update image info */
        for (int32_t i = mExynosCompositionInfo.mFirstIndex; i <= mExynosCompositionInfo.mLastIndex; i++) {
            /* break when only framebuffer target is assigned on ExynosCompositor */
            if (i == -1)
                break;

            struct exynos_image srcImg, dstImg;
            mLayers[i]->setSrcExynosImage(&srcImg);
            dumpExynosImage(eDebugFence, srcImg);
            mLayers[i]->setDstExynosImage(&dstImg);
            mLayers[i]->setExynosImage(srcImg, dstImg);
        }

        /* For debugging */
        if (validateExynosCompositionLayer() == false) {
            DISPLAY_LOGE("mExynosCompositionInfo is not valid");
            return -EINVAL;
        }

        if ((ret = mExynosCompositionInfo.mM2mMPP->doPostProcessing(
                     mExynosCompositionInfo.mDstImg)) != NO_ERROR) {
            DISPLAY_LOGE("exynosComposition doPostProcessing fail ret(%d)", ret);
            return ret;
        }

        for (int32_t i = mExynosCompositionInfo.mFirstIndex; i <= mExynosCompositionInfo.mLastIndex; i++) {
            /* break when only framebuffer target is assigned on ExynosCompositor */
            if (i == -1)
                break;
            /* This should be closed by resource lib (libmpp or libacryl) */
            mLayers[i]->mAcquireFence = -1;
        }

        exynos_image outImage;
        if ((ret = mExynosCompositionInfo.mM2mMPP->getDstImageInfo(&outImage)) != NO_ERROR) {
            DISPLAY_LOGE("exynosComposition getDstImageInfo fail ret(%d)", ret);
            return ret;
        }

        android_dataspace dataspace = HAL_DATASPACE_UNKNOWN;
        if (mColorMode != HAL_COLOR_MODE_NATIVE)
            dataspace = colorModeToDataspace(mColorMode);
        mExynosCompositionInfo.setTargetBuffer(this, outImage.bufferHandle,
                outImage.releaseFenceFd, dataspace);
        /*
         * buffer handle, dataspace can be changed by setTargetBuffer()
         * ExynosImage should be set again according to changed handle and dataspace
         */
        setCompositionTargetExynosImage(COMPOSITION_EXYNOS, &src_img, &dst_img);
        mExynosCompositionInfo.setExynosImage(src_img, dst_img);

        DISPLAY_LOGD(eDebugFence, "mExynosCompositionInfo acquireFencefd(%d)",
                     mExynosCompositionInfo.mAcquireFence);

        if ((ret =  mExynosCompositionInfo.mM2mMPP->resetDstReleaseFence()) != NO_ERROR)
        {
            DISPLAY_LOGE("exynosComposition resetDstReleaseFence fail ret(%d)", ret);
            return ret;
        }
    }

    return ret;
}

bool ExynosDisplay::getHDRException(ExynosLayer* __unused layer)
{
    return false;
}

int32_t ExynosDisplay::configureHandle(ExynosLayer &layer, int fence_fd, exynos_win_config_data &cfg)
{
    /* TODO : this is hardcoded */
    int32_t ret = NO_ERROR;
    buffer_handle_t handle = NULL;
    int32_t blending = 0x0100;
    uint32_t x = 0, y = 0;
    uint32_t w = WIDTH(layer.mPreprocessedInfo.displayFrame);
    uint32_t h = HEIGHT(layer.mPreprocessedInfo.displayFrame);
    ExynosMPP* otfMPP = NULL;
    ExynosMPP* m2mMPP = NULL;
    unsigned int luminanceMin = 0;
    unsigned int luminanceMax = 0;

    blending = layer.mBlending;
    otfMPP = layer.mOtfMPP;
    m2mMPP = layer.mM2mMPP;

    cfg.compressionInfo = layer.mCompressionInfo;
    if (layer.mCompressionInfo.type == COMP_TYPE_AFBC) {
        cfg.comp_src = DPP_COMP_SRC_GPU;
    }
    if (otfMPP == nullptr && layer.mExynosCompositionType != HWC2_COMPOSITION_DISPLAY_DECORATION) {
        HWC_LOGE(this, "%s:: otfMPP is NULL", __func__);
        return -EINVAL;
    }
    if (m2mMPP != NULL)
        handle = m2mMPP->mDstImgs[m2mMPP->mCurrentDstBuf].bufferHandle;
    else
        handle = layer.mLayerBuffer;

    if ((!layer.isDimLayer()) && handle == NULL) {
        HWC_LOGE(this, "%s:: invalid handle", __func__);
        return -EINVAL;
    }

    if (layer.mPreprocessedInfo.displayFrame.left < 0) {
        unsigned int crop = -layer.mPreprocessedInfo.displayFrame.left;
        DISPLAY_LOGD(eDebugWinConfig, "layer off left side of screen; cropping %u pixels from left edge",
                crop);
        x = 0;
        w -= crop;
    } else {
        x = layer.mPreprocessedInfo.displayFrame.left;
    }

    if (layer.mPreprocessedInfo.displayFrame.right > (int)mXres) {
        unsigned int crop = layer.mPreprocessedInfo.displayFrame.right - mXres;
        DISPLAY_LOGD(eDebugWinConfig, "layer off right side of screen; cropping %u pixels from right edge",
                crop);
        w -= crop;
    }

    if (layer.mPreprocessedInfo.displayFrame.top < 0) {
        unsigned int crop = -layer.mPreprocessedInfo.displayFrame.top;
        DISPLAY_LOGD(eDebugWinConfig, "layer off top side of screen; cropping %u pixels from top edge",
                crop);
        y = 0;
        h -= crop;
    } else {
        y = layer.mPreprocessedInfo.displayFrame.top;
    }

    if (layer.mPreprocessedInfo.displayFrame.bottom > (int)mYres) {
        int crop = layer.mPreprocessedInfo.displayFrame.bottom - mYres;
        DISPLAY_LOGD(eDebugWinConfig, "layer off bottom side of screen; cropping %u pixels from bottom edge",
                crop);
        h -= crop;
    }

    cfg.layer = &layer;
    if ((layer.mExynosCompositionType == HWC2_COMPOSITION_DEVICE) &&
        (layer.mCompositionType == HWC2_COMPOSITION_CURSOR)) {
        cfg.state = cfg.WIN_STATE_CURSOR;
    } else if (layer.mExynosCompositionType == HWC2_COMPOSITION_DISPLAY_DECORATION) {
        cfg.state = cfg.WIN_STATE_RCD;
        assign(cfg.block_area, layer.mBlockingRect.left, layer.mBlockingRect.top,
               layer.mBlockingRect.right - layer.mBlockingRect.left,
               layer.mBlockingRect.bottom - layer.mBlockingRect.top);
    } else {
        cfg.state = cfg.WIN_STATE_BUFFER;
    }

    cfg.dst.x = x;
    cfg.dst.y = y;
    cfg.dst.w = w;
    cfg.dst.h = h;
    cfg.dst.f_w = mXres;
    cfg.dst.f_h = mYres;

    cfg.plane_alpha = layer.mPlaneAlpha;
    cfg.blending = blending;
    cfg.assignedMPP = otfMPP;

    if (layer.isDimLayer()) {
        if (fence_fd >= 0) {
            fence_fd = fence_close(fence_fd, this, FENCE_TYPE_SRC_ACQUIRE, FENCE_IP_ALL);
        }
        cfg.state = cfg.WIN_STATE_COLOR;
        hwc_color_t color = layer.mColor;
        cfg.color = (color.a << 24) | (color.r << 16) | (color.g << 8) | color.b;
        DISPLAY_LOGD(eDebugWinConfig, "HWC2: DIM layer is enabled, color: %d, alpha : %f",
                cfg.color, cfg.plane_alpha);
        return ret;
    }

    VendorGraphicBufferMeta gmeta(handle);

    if (!layer.mPreprocessedInfo.mUsePrivateFormat)
        cfg.format = gmeta.format;
    else
        cfg.format = layer.mPreprocessedInfo.mPrivateFormat;

    cfg.buffer_id = gmeta.unique_id;
    cfg.fd_idma[0] = gmeta.fd;
    cfg.fd_idma[1] = gmeta.fd1;
    cfg.fd_idma[2] = gmeta.fd2;
    cfg.protection = (getDrmMode(gmeta.producer_usage) == SECURE_DRM) ? 1 : 0;

    exynos_image src_img = layer.mSrcImg;

    if (m2mMPP != NULL)
    {
        DISPLAY_LOGD(eDebugWinConfig, "\tUse m2mMPP, bufIndex: %d", m2mMPP->mCurrentDstBuf);
        dumpExynosImage(eDebugWinConfig, m2mMPP->mAssignedSources[0]->mMidImg);
        exynos_image mpp_dst_img;
        if (m2mMPP->getDstImageInfo(&mpp_dst_img) == NO_ERROR) {
            dumpExynosImage(eDebugWinConfig, mpp_dst_img);
            cfg.compressionInfo = mpp_dst_img.compressionInfo;
            cfg.src.f_w = mpp_dst_img.fullWidth;
            cfg.src.f_h = mpp_dst_img.fullHeight;
            cfg.src.x = mpp_dst_img.x;
            cfg.src.y = mpp_dst_img.y;
            cfg.src.w = mpp_dst_img.w;
            cfg.src.h = mpp_dst_img.h;
            cfg.format = mpp_dst_img.format;
            cfg.acq_fence =
                hwcCheckFenceDebug(this, FENCE_TYPE_SRC_ACQUIRE, FENCE_IP_DPP, mpp_dst_img.releaseFenceFd);

            if (m2mMPP->mPhysicalType == MPP_MSC) {
                setFenceName(cfg.acq_fence, FENCE_DPP_SRC_MSC);
            } else if (m2mMPP->mPhysicalType == MPP_G2D) {
                setFenceName(cfg.acq_fence, FENCE_DPP_SRC_G2D);
            } else {
                setFenceName(cfg.acq_fence, FENCE_DPP_SRC_MPP);
            }
            m2mMPP->resetDstReleaseFence();
        } else {
            HWC_LOGE(this, "%s:: Failed to get dst info of m2mMPP", __func__);
        }
        cfg.dataspace = mpp_dst_img.dataSpace;

        cfg.transform = 0;

        if (hasHdrInfo(layer.mMidImg)) {
            bool hdr_exception = getHDRException(&layer);
            uint32_t parcelFdIndex =
                    getBufferNumOfFormat(layer.mMidImg.format,
                                         getCompressionType(layer.mMidImg.bufferHandle));
            if (parcelFdIndex == 0) {
                DISPLAY_LOGE("%s:: failed to get parcelFdIndex for midImg with format: %d",
                             __func__, layer.mMidImg.format);
                return -EINVAL;
            }
            if (layer.mBufferHasMetaParcel) {
                VendorGraphicBufferMeta layer_buffer_gmeta(layer.mLayerBuffer);
                if (layer_buffer_gmeta.flags & VendorGraphicBufferMeta::PRIV_FLAGS_USES_2PRIVATE_DATA)
                    cfg.fd_idma[parcelFdIndex] = layer_buffer_gmeta.fd1;
                else if (layer_buffer_gmeta.flags & VendorGraphicBufferMeta::PRIV_FLAGS_USES_3PRIVATE_DATA)
                    cfg.fd_idma[parcelFdIndex] = layer_buffer_gmeta.fd2;
            } else {
                cfg.fd_idma[parcelFdIndex] = layer.mMetaParcelFd;
            }

            if (!hdr_exception)
                cfg.hdr_enable = true;
            else
                cfg.hdr_enable = false;

            /* Min/Max luminance should be set as M2M MPP's HDR operations
             * If HDR is not processed by M2M MPP, M2M's dst image should have source's min/max luminance
             * */
            dstMetaInfo_t metaInfo = m2mMPP->getDstMetaInfo(mpp_dst_img.dataSpace);
            luminanceMin = metaInfo.minLuminance;
            luminanceMax = metaInfo.maxLuminance;
            DISPLAY_LOGD(eDebugMPP, "HWC2: DPP luminance min %d, max %d", luminanceMin, luminanceMax);
        } else {
            cfg.hdr_enable = true;
        }

        src_img = layer.mMidImg;
    } else {
        cfg.src.f_w = src_img.fullWidth;
        cfg.src.f_h = src_img.fullHeight;
        cfg.src.x = layer.mPreprocessedInfo.sourceCrop.left;
        cfg.src.y = layer.mPreprocessedInfo.sourceCrop.top;
        cfg.src.w = WIDTH(layer.mPreprocessedInfo.sourceCrop) - (cfg.src.x - (uint32_t)layer.mPreprocessedInfo.sourceCrop.left);
        cfg.src.h = HEIGHT(layer.mPreprocessedInfo.sourceCrop) - (cfg.src.y - (uint32_t)layer.mPreprocessedInfo.sourceCrop.top);
        cfg.acq_fence = hwcCheckFenceDebug(this, FENCE_TYPE_SRC_ACQUIRE, FENCE_IP_DPP, fence_fd);
        setFenceName(cfg.acq_fence, FENCE_DPP_SRC_LAYER);

        cfg.dataspace = src_img.dataSpace;
        cfg.transform = src_img.transform;

        if (hasHdrInfo(src_img)) {
            bool hdr_exception = getHDRException(&layer);
            if (!hdr_exception)
                cfg.hdr_enable = true;
            else
                cfg.hdr_enable = false;

            if (layer.mBufferHasMetaParcel == false) {
                uint32_t parcelFdIndex =
                        getBufferNumOfFormat(gmeta.format, getCompressionType(handle));
                if (parcelFdIndex == 0) {
                    DISPLAY_LOGE("%s:: failed to get parcelFdIndex for srcImg with format: %d",
                                 __func__, gmeta.format);
                    return -EINVAL;
                }

                cfg.fd_idma[parcelFdIndex] = layer.mMetaParcelFd;
            }

            /*
             * Static info uses 0.0001nit unit for luminace
             * Display uses 1nit unit for max luminance
             * and uses 0.0001nit unit for min luminance
             * Conversion is required
             */
            luminanceMin = src_img.metaParcel.sHdrStaticInfo.sType1.mMinDisplayLuminance;
            luminanceMax = src_img.metaParcel.sHdrStaticInfo.sType1.mMaxDisplayLuminance/10000;
            DISPLAY_LOGD(eDebugMPP, "HWC2: DPP luminance min %d, max %d", luminanceMin, luminanceMax);
        } else {
            cfg.hdr_enable = true;
        }
    }

    cfg.min_luminance = luminanceMin;
    cfg.max_luminance = luminanceMax;
    cfg.needColorTransform = src_img.needColorTransform;

    /* Adjust configuration */
    uint32_t srcMaxWidth, srcMaxHeight, srcWidthAlign, srcHeightAlign = 0;
    uint32_t srcXAlign, srcYAlign, srcMaxCropWidth, srcMaxCropHeight, srcCropWidthAlign, srcCropHeightAlign = 0;

    if (otfMPP != nullptr) {
        srcMaxWidth = otfMPP->getSrcMaxWidth(src_img);
        srcMaxHeight = otfMPP->getSrcMaxHeight(src_img);
        srcWidthAlign = otfMPP->getSrcWidthAlign(src_img);
        srcHeightAlign = otfMPP->getSrcHeightAlign(src_img);
        srcXAlign = otfMPP->getSrcXOffsetAlign(src_img);
        srcYAlign = otfMPP->getSrcYOffsetAlign(src_img);
        srcMaxCropWidth = otfMPP->getSrcMaxCropWidth(src_img);
        srcMaxCropHeight = otfMPP->getSrcMaxCropHeight(src_img);
        srcCropWidthAlign = otfMPP->getSrcCropWidthAlign(src_img);
        srcCropHeightAlign = otfMPP->getSrcCropHeightAlign(src_img);
    }

    if (cfg.src.x < 0)
        cfg.src.x = 0;
    if (cfg.src.y < 0)
        cfg.src.y = 0;

    if (otfMPP != NULL) {
        if (cfg.src.f_w > srcMaxWidth)
            cfg.src.f_w = srcMaxWidth;
        if (cfg.src.f_h > srcMaxHeight)
            cfg.src.f_h = srcMaxHeight;
        cfg.src.f_w = pixel_align_down(cfg.src.f_w, srcWidthAlign);
        cfg.src.f_h = pixel_align_down(cfg.src.f_h, srcHeightAlign);

        cfg.src.x = pixel_align(cfg.src.x, srcXAlign);
        cfg.src.y = pixel_align(cfg.src.y, srcYAlign);
    }

    if (cfg.src.x + cfg.src.w > cfg.src.f_w)
        cfg.src.w = cfg.src.f_w - cfg.src.x;
    if (cfg.src.y + cfg.src.h > cfg.src.f_h)
        cfg.src.h = cfg.src.f_h - cfg.src.y;

    if (otfMPP != NULL) {
        if (cfg.src.w > srcMaxCropWidth)
            cfg.src.w = srcMaxCropWidth;
        if (cfg.src.h > srcMaxCropHeight)
            cfg.src.h = srcMaxCropHeight;
        cfg.src.w = pixel_align_down(cfg.src.w, srcCropWidthAlign);
        cfg.src.h = pixel_align_down(cfg.src.h, srcCropHeightAlign);
    }

    uint64_t bufSize = gmeta.size * formatToBpp(gmeta.format);
    uint64_t srcSize = cfg.src.f_w * cfg.src.f_h * formatToBpp(cfg.format);

    if (!isFormatLossy(gmeta.format) && (bufSize < srcSize)) {
        DISPLAY_LOGE("%s:: buffer size is smaller than source size, buf(size: %d, format: %d), src(w: %d, h: %d, format: %d)",
                __func__, gmeta.size, gmeta.format, cfg.src.f_w, cfg.src.f_h, cfg.format);
        return -EINVAL;
    }

    return ret;
}


int32_t ExynosDisplay::configureOverlay(ExynosLayer *layer, exynos_win_config_data &cfg)
{
    int32_t ret = NO_ERROR;
    if(layer != NULL) {
        if ((ret = configureHandle(*layer, layer->mAcquireFence, cfg)) != NO_ERROR)
            return ret;

        /* This will be closed by setReleaseFences() using config.acq_fence */
        layer->mAcquireFence = -1;
    }
    return ret;
}
int32_t ExynosDisplay::configureOverlay(ExynosCompositionInfo &compositionInfo)
{
    int32_t windowIndex = compositionInfo.mWindowIndex;
    buffer_handle_t handle = compositionInfo.mTargetBuffer;
    VendorGraphicBufferMeta gmeta(compositionInfo.mTargetBuffer);

    if ((windowIndex < 0) || (windowIndex >= (int32_t)mDpuData.configs.size()))
    {
        HWC_LOGE(this, "%s:: ExynosCompositionInfo(%d) has invalid data, windowIndex(%d)",
                __func__, compositionInfo.mType, windowIndex);
        return -EINVAL;
    }

    exynos_win_config_data &config = mDpuData.configs[windowIndex];

    if (handle == NULL) {
        /* config will be set by handleStaticLayers */
        if (compositionInfo.mSkipFlag)
            return NO_ERROR;

        if (compositionInfo.mType == COMPOSITION_CLIENT) {
            ALOGW("%s:: ExynosCompositionInfo(%d) has invalid data, handle(%p)",
                    __func__, compositionInfo.mType, handle);
            if (compositionInfo.mAcquireFence >= 0) {
                compositionInfo.mAcquireFence = fence_close(compositionInfo.mAcquireFence, this,
                        FENCE_TYPE_SRC_ACQUIRE, FENCE_IP_FB);
            }
            config.state = config.WIN_STATE_DISABLED;
            return NO_ERROR;
        } else {
            HWC_LOGE(this, "%s:: ExynosCompositionInfo(%d) has invalid data, handle(%p)",
                    __func__, compositionInfo.mType, handle);
            return -EINVAL;
        }
    }

    config.buffer_id = gmeta.unique_id;
    config.fd_idma[0] = gmeta.fd;
    config.fd_idma[1] = gmeta.fd1;
    config.fd_idma[2] = gmeta.fd2;
    config.protection = (getDrmMode(gmeta.producer_usage) == SECURE_DRM) ? 1 : 0;
    config.state = config.WIN_STATE_BUFFER;

    config.assignedMPP = compositionInfo.mOtfMPP;

    config.dst.f_w = mXres;
    config.dst.f_h = mYres;
    config.format = gmeta.format;
    if (compositionInfo.mType == COMPOSITION_EXYNOS) {
        config.src.f_w = pixel_align(mXres, G2D_JUSTIFIED_DST_ALIGN);
        config.src.f_h = pixel_align(mYres, G2D_JUSTIFIED_DST_ALIGN);
    } else {
        config.src.f_w = gmeta.stride;
        config.src.f_h = gmeta.vstride;
    }
    config.compressionInfo = compositionInfo.mCompressionInfo;
    if (compositionInfo.mCompressionInfo.type == COMP_TYPE_AFBC) {
        if (compositionInfo.mType == COMPOSITION_EXYNOS)
            config.comp_src = DPP_COMP_SRC_G2D;
        else if (compositionInfo.mType == COMPOSITION_CLIENT)
            config.comp_src = DPP_COMP_SRC_GPU;
        else
            HWC_LOGE(this, "unknown composition type: %d", compositionInfo.mType);
    }

    bool useCompositionCrop = true;
    if ((mDisplayControl.enableCompositionCrop) &&
        (compositionInfo.mHasCompositionLayer) &&
        (compositionInfo.mFirstIndex >= 0) &&
        (compositionInfo.mLastIndex >= 0)) {
        hwc_rect merged_rect, src_rect;
        merged_rect.left = mXres;
        merged_rect.top = mYres;
        merged_rect.right = 0;
        merged_rect.bottom = 0;

        for (int i = compositionInfo.mFirstIndex; i <= compositionInfo.mLastIndex; i++) {
            ExynosLayer *layer = mLayers[i];
            src_rect.left = layer->mDisplayFrame.left;
            src_rect.top = layer->mDisplayFrame.top;
            src_rect.right = layer->mDisplayFrame.right;
            src_rect.bottom = layer->mDisplayFrame.bottom;
            merged_rect = expand(merged_rect, src_rect);
            DISPLAY_LOGD(eDebugWinConfig, "[%d] layer type: [%d, %d] dispFrame [l: %d, t: %d, r: %d, b: %d], mergedRect [l: %d, t: %d, r: %d, b: %d]",
                    i,
                    layer->mCompositionType,
                    layer->mExynosCompositionType,
                    layer->mDisplayFrame.left,
                    layer->mDisplayFrame.top,
                    layer->mDisplayFrame.right,
                    layer->mDisplayFrame.bottom,
                    merged_rect.left,
                    merged_rect.top,
                    merged_rect.right,
                    merged_rect.bottom);
        }

        config.src.x = merged_rect.left;
        config.src.y = merged_rect.top;
        config.src.w = merged_rect.right - merged_rect.left;
        config.src.h = merged_rect.bottom - merged_rect.top;

        ExynosMPP* exynosMPP = config.assignedMPP;
        if (exynosMPP == NULL) {
            DISPLAY_LOGE("%s:: assignedMPP is NULL", __func__);
            useCompositionCrop = false;
        } else {
            /* Check size constraints */
            uint32_t restrictionIdx = getRestrictionIndex(config.format);
            uint32_t srcXAlign = exynosMPP->getSrcXOffsetAlign(restrictionIdx);
            uint32_t srcYAlign = exynosMPP->getSrcYOffsetAlign(restrictionIdx);
            uint32_t srcWidthAlign = exynosMPP->getSrcCropWidthAlign(restrictionIdx);
            uint32_t srcHeightAlign = exynosMPP->getSrcCropHeightAlign(restrictionIdx);
            uint32_t srcMinWidth = exynosMPP->getSrcMinWidth(restrictionIdx);
            uint32_t srcMinHeight = exynosMPP->getSrcMinHeight(restrictionIdx);

            if (config.src.w < srcMinWidth) {
                config.src.x -= (srcMinWidth - config.src.w);
                if (config.src.x < 0)
                    config.src.x = 0;
                config.src.w = srcMinWidth;
            }
            if (config.src.h < srcMinHeight) {
                config.src.y -= (srcMinHeight - config.src.h);
                if (config.src.y < 0)
                    config.src.y = 0;
                config.src.h = srcMinHeight;
            }

            int32_t alignedSrcX = pixel_align_down(config.src.x, srcXAlign);
            int32_t alignedSrcY = pixel_align_down(config.src.y, srcYAlign);
            config.src.w += (config.src.x - alignedSrcX);
            config.src.h += (config.src.y - alignedSrcY);
            config.src.x = alignedSrcX;
            config.src.y = alignedSrcY;
            config.src.w = pixel_align(config.src.w, srcWidthAlign);
            config.src.h = pixel_align(config.src.h, srcHeightAlign);
        }

        config.dst.x = config.src.x;
        config.dst.y = config.src.y;
        config.dst.w = config.src.w;
        config.dst.h = config.src.h;

        if ((config.src.x < 0) ||
            (config.src.y < 0) ||
            ((config.src.x + config.src.w) > mXres) ||
            ((config.src.y + config.src.h) > mYres)) {
            useCompositionCrop = false;
            ALOGW("Invalid composition target crop size: (%d, %d, %d, %d)",
                    config.src.x, config.src.y,
                    config.src.w, config.src.h);
        }

        DISPLAY_LOGD(eDebugWinConfig, "composition(%d) config[%d] x : %d, y : %d, w : %d, h : %d",
                compositionInfo.mType, windowIndex,
                config.dst.x, config.dst.y,
                config.dst.w, config.dst.h);
    } else {
        useCompositionCrop = false;
    }

    if (useCompositionCrop == false) {
        config.src.x = 0;
        config.src.y = 0;
        config.src.w = mXres;
        config.src.h = mYres;
        config.dst.x = 0;
        config.dst.y = 0;
        config.dst.w = mXres;
        config.dst.h = mYres;
    }

    config.blending = HWC2_BLEND_MODE_PREMULTIPLIED;

    config.acq_fence =
        hwcCheckFenceDebug(this, FENCE_TYPE_SRC_ACQUIRE, FENCE_IP_DPP, compositionInfo.mAcquireFence);
    config.plane_alpha = 1;
    config.dataspace = compositionInfo.mSrcImg.dataSpace;
    config.hdr_enable = true;

    /* This will be closed by setReleaseFences() using config.acq_fence */
    compositionInfo.mAcquireFence = -1;
    DISPLAY_LOGD(eDebugSkipStaicLayer, "Configure composition target[%d], config[%d]!!!!",
            compositionInfo.mType, windowIndex);
    dumpConfig(config);

    uint64_t bufSize = gmeta.size * formatToBpp(gmeta.format);
    uint64_t srcSize = config.src.f_w * config.src.f_h * formatToBpp(config.format);
    if (!isFormatLossy(gmeta.format) && (bufSize < srcSize)) {
        DISPLAY_LOGE("%s:: buffer size is smaller than source size, buf(size: %d, format: %d), src(w: %d, h: %d, format: %d)",
                __func__, gmeta.size, gmeta.format, config.src.f_w, config.src.f_h, config.format);
        return -EINVAL;
    }

    return NO_ERROR;
}

/**
 * @return int
 */
int ExynosDisplay::setWinConfigData() {
    int ret = NO_ERROR;
    mDpuData.reset();

    if (mClientCompositionInfo.mHasCompositionLayer) {
        if ((ret = configureOverlay(mClientCompositionInfo)) != NO_ERROR)
            return ret;
    }
    if (mExynosCompositionInfo.mHasCompositionLayer) {
        if ((ret = configureOverlay(mExynosCompositionInfo)) != NO_ERROR) {
            /* TEST */
            //return ret;
            HWC_LOGE(this, "configureOverlay(ExynosCompositionInfo) is failed");
        }
    }

    /* TODO loop for number of layers */
    for (size_t i = 0; i < mLayers.size(); i++) {
        if ((mLayers[i]->mExynosCompositionType == HWC2_COMPOSITION_EXYNOS) ||
                (mLayers[i]->mExynosCompositionType == HWC2_COMPOSITION_CLIENT))
            continue;
        if (mLayers[i]->mExynosCompositionType == HWC2_COMPOSITION_DISPLAY_DECORATION) {
            if (CC_UNLIKELY(mDpuData.rcdConfigs.size() == 0)) {
                DISPLAY_LOGE("%s:: %zu layer has invalid COMPOSITION_TYPE(%d)", __func__, i,
                             mLayers[i]->mExynosCompositionType);
                return -EINVAL;
            }

            if ((ret = configureOverlay(mLayers[i], mDpuData.rcdConfigs[0])) != NO_ERROR)
                return ret;
            continue;
        }
        int32_t windowIndex =  mLayers[i]->mWindowIndex;
        if ((windowIndex < 0) || (windowIndex >= (int32_t)mDpuData.configs.size())) {
            DISPLAY_LOGE("%s:: %zu layer has invalid windowIndex(%d)",
                    __func__, i, windowIndex);
            return -EINVAL;
        }
        DISPLAY_LOGD(eDebugWinConfig, "%zu layer, config[%d]", i, windowIndex);
        if ((ret = configureOverlay(mLayers[i], mDpuData.configs[windowIndex])) != NO_ERROR)
            return ret;
    }

    return 0;
}

void ExynosDisplay::printDebugInfos(String8 &reason) {
    struct timeval tv;
    gettimeofday(&tv, NULL);
    reason.appendFormat("errFrameNumber: %" PRId64 " time:%s\n", mErrorFrameCount,
                        getLocalTimeStr(tv).c_str());
    ALOGD("%s", reason.c_str());

    bool fileOpened = mDebugDumpFileWriter.chooseOpenedFile();
    mDebugDumpFileWriter.write(reason);
    mErrorFrameCount++;

    android::String8 result;
    result.appendFormat("Device mGeometryChanged(%" PRIx64 "), mGeometryChanged(%" PRIx64 "), mRenderingState(%d)\n",
            mDevice->mGeometryChanged, mGeometryChanged, mRenderingState);
    result.appendFormat("=======================  dump composition infos  ================================\n");
    const ExynosCompositionInfo& clientCompInfo = mClientCompositionInfo;
    const ExynosCompositionInfo& exynosCompInfo = mExynosCompositionInfo;
    clientCompInfo.dump(result);
    exynosCompInfo.dump(result);
    ALOGD("%s", result.c_str());
    mDebugDumpFileWriter.write(result);
    result.clear();

    result.appendFormat("=======================  dump exynos layers (%zu)  ================================\n",
            mLayers.size());
    ALOGD("%s", result.c_str());
    mDebugDumpFileWriter.write(result);
    result.clear();
    for (uint32_t i = 0; i < mLayers.size(); i++) {
        ExynosLayer *layer = mLayers[i];
        layer->printLayer();
        if (fileOpened) {
            layer->dump(result);
            mDebugDumpFileWriter.write(result);
            result.clear();
        }
    }

    if (mIgnoreLayers.size()) {
        result.appendFormat("=======================  dump ignore layers (%zu)  ================================\n",
                            mIgnoreLayers.size());
        ALOGD("%s", result.c_str());
        mDebugDumpFileWriter.write(result);
        result.clear();
        for (uint32_t i = 0; i < mIgnoreLayers.size(); i++) {
            ExynosLayer *layer = mIgnoreLayers[i];
            layer->printLayer();
            if (fileOpened) {
                layer->dump(result);
                mDebugDumpFileWriter.write(result);
                result.clear();
            }
        }
    }

    result.appendFormat("=============================  dump win configs  ===================================\n");
    ALOGD("%s", result.c_str());
    mDebugDumpFileWriter.write(result);
    result.clear();
    for (size_t i = 0; i < mDpuData.configs.size(); i++) {
        ALOGD("config[%zu]", i);
        printConfig(mDpuData.configs[i]);
        if (fileOpened) {
            result.appendFormat("config[%zu]\n", i);
            dumpConfig(result, mDpuData.configs[i]);
            mDebugDumpFileWriter.write(result);
            result.clear();
        }
    }
    mDebugDumpFileWriter.flush();
}

int32_t ExynosDisplay::validateWinConfigData()
{
    bool flagValidConfig = true;
    int bufferStateCnt = 0;

    for (size_t i = 0; i < mDpuData.configs.size(); i++) {
        exynos_win_config_data &config = mDpuData.configs[i];
        if (config.state == config.WIN_STATE_BUFFER) {
            bool configInvalid = false;
            /* multiple dma mapping */
            for (size_t j = (i+1); j < mDpuData.configs.size(); j++) {
                exynos_win_config_data &compare_config = mDpuData.configs[j];
                if ((config.state == config.WIN_STATE_BUFFER) &&
                    (compare_config.state == compare_config.WIN_STATE_BUFFER)) {
                    if ((config.assignedMPP != NULL) &&
                        (config.assignedMPP == compare_config.assignedMPP)) {
                        DISPLAY_LOGE("WIN_CONFIG error: duplicated assignedMPP(%s) between win%zu, win%zu",
                                config.assignedMPP->mName.c_str(), i, j);
                        compare_config.state = compare_config.WIN_STATE_DISABLED;
                        flagValidConfig = false;
                        continue;
                    }
                }
            }
            if ((config.src.x < 0) || (config.src.y < 0)||
                (config.dst.x < 0) || (config.dst.y < 0)||
                (config.src.w <= 0) || (config.src.h <= 0)||
                (config.dst.w <= 0) || (config.dst.h <= 0)||
                (config.dst.x + config.dst.w > (uint32_t)mXres) ||
                (config.dst.y + config.dst.h > (uint32_t)mYres)) {
                DISPLAY_LOGE("WIN_CONFIG error: invalid pos or size win%zu", i);
                configInvalid = true;
            }

            if ((config.src.w > config.src.f_w) ||
                (config.src.h > config.src.f_h)) {
                DISPLAY_LOGE("WIN_CONFIG error: invalid size %zu, %d, %d, %d, %d", i,
                        config.src.w, config.src.f_w, config.src.h, config.src.f_h);
                configInvalid = true;
            }

            /* Source alignment check */
            ExynosMPP* exynosMPP = config.assignedMPP;
            if (exynosMPP == NULL) {
                DISPLAY_LOGE("WIN_CONFIG error: config %zu assigendMPP is NULL", i);
                configInvalid = true;
            } else {
                uint32_t restrictionIdx = getRestrictionIndex(config.format);
                uint32_t srcXAlign = exynosMPP->getSrcXOffsetAlign(restrictionIdx);
                uint32_t srcYAlign = exynosMPP->getSrcYOffsetAlign(restrictionIdx);
                uint32_t srcWidthAlign = exynosMPP->getSrcCropWidthAlign(restrictionIdx);
                uint32_t srcHeightAlign = exynosMPP->getSrcCropHeightAlign(restrictionIdx);
                if ((config.src.x % srcXAlign != 0) ||
                    (config.src.y % srcYAlign != 0) ||
                    (config.src.w % srcWidthAlign != 0) ||
                    (config.src.h % srcHeightAlign != 0))
                {
                    DISPLAY_LOGE("WIN_CONFIG error: invalid src alignment : %zu, "\
                            "assignedMPP: %s, mppType:%d, format(%d), s_x: %d(%d), s_y: %d(%d), s_w : %d(%d), s_h : %d(%d)", i,
                            config.assignedMPP->mName.c_str(), exynosMPP->mLogicalType, config.format, config.src.x, srcXAlign,
                            config.src.y, srcYAlign, config.src.w, srcWidthAlign, config.src.h, srcHeightAlign);
                    configInvalid = true;
                }
            }

            if (configInvalid) {
                config.state = config.WIN_STATE_DISABLED;
                flagValidConfig = false;
            }

            bufferStateCnt++;
        }

        if ((config.state == config.WIN_STATE_COLOR) ||
            (config.state == config.WIN_STATE_CURSOR))
            bufferStateCnt++;
    }

    if (bufferStateCnt == 0) {
        DISPLAY_LOGE("WIN_CONFIG error: has no buffer window");
        flagValidConfig = false;
    }

    if (flagValidConfig)
        return NO_ERROR;
    else
        return -EINVAL;
}

/**
 * @return int
 */
int ExynosDisplay::setDisplayWinConfigData() {
    return 0;
}

bool ExynosDisplay::checkConfigChanged(const exynos_dpu_data &lastConfigsData, const exynos_dpu_data &newConfigsData)
{
    if (exynosHWCControl.skipWinConfig == 0)
        return true;

    /* HWC doesn't skip WIN_CONFIG if other display is connected */
    if ((mDevice->checkNonInternalConnection()) && (mType == HWC_DISPLAY_PRIMARY))
        return true;

    for (size_t i = 0; i < lastConfigsData.configs.size(); i++) {
        if ((lastConfigsData.configs[i].state != newConfigsData.configs[i].state) ||
            (lastConfigsData.configs[i].fd_idma[0] != newConfigsData.configs[i].fd_idma[0]) ||
            (lastConfigsData.configs[i].fd_idma[1] != newConfigsData.configs[i].fd_idma[1]) ||
            (lastConfigsData.configs[i].fd_idma[2] != newConfigsData.configs[i].fd_idma[2]) ||
            (lastConfigsData.configs[i].dst.x != newConfigsData.configs[i].dst.x) ||
            (lastConfigsData.configs[i].dst.y != newConfigsData.configs[i].dst.y) ||
            (lastConfigsData.configs[i].dst.w != newConfigsData.configs[i].dst.w) ||
            (lastConfigsData.configs[i].dst.h != newConfigsData.configs[i].dst.h) ||
            (lastConfigsData.configs[i].src.x != newConfigsData.configs[i].src.x) ||
            (lastConfigsData.configs[i].src.y != newConfigsData.configs[i].src.y) ||
            (lastConfigsData.configs[i].src.w != newConfigsData.configs[i].src.w) ||
            (lastConfigsData.configs[i].src.h != newConfigsData.configs[i].src.h) ||
            (lastConfigsData.configs[i].format != newConfigsData.configs[i].format) ||
            (lastConfigsData.configs[i].blending != newConfigsData.configs[i].blending) ||
            (lastConfigsData.configs[i].plane_alpha != newConfigsData.configs[i].plane_alpha))
            return true;
    }

    /* To cover buffer payload changed case */
    for (size_t i = 0; i < mLayers.size(); i++) {
        if(mLayers[i]->mLastLayerBuffer != mLayers[i]->mLayerBuffer)
            return true;
    }

    return false;
}

int ExynosDisplay::checkConfigDstChanged(const exynos_dpu_data &lastConfigsData, const exynos_dpu_data &newConfigsData, uint32_t index)
{
    if ((lastConfigsData.configs[index].state != newConfigsData.configs[index].state) ||
        (lastConfigsData.configs[index].fd_idma[0] != newConfigsData.configs[index].fd_idma[0]) ||
        (lastConfigsData.configs[index].fd_idma[1] != newConfigsData.configs[index].fd_idma[1]) ||
        (lastConfigsData.configs[index].fd_idma[2] != newConfigsData.configs[index].fd_idma[2]) ||
        (lastConfigsData.configs[index].format != newConfigsData.configs[index].format) ||
        (lastConfigsData.configs[index].blending != newConfigsData.configs[index].blending) ||
        (lastConfigsData.configs[index].plane_alpha != newConfigsData.configs[index].plane_alpha)) {
        DISPLAY_LOGD(eDebugWindowUpdate, "damage region is skip, but other configuration except dst was changed");
        DISPLAY_LOGD(eDebugWindowUpdate, "\tstate[%d, %d], fd[%d, %d], format[0x%8x, 0x%8x], blending[%d, %d], plane_alpha[%f, %f]",
                lastConfigsData.configs[index].state, newConfigsData.configs[index].state,
                lastConfigsData.configs[index].fd_idma[0], newConfigsData.configs[index].fd_idma[0],
                lastConfigsData.configs[index].format, newConfigsData.configs[index].format,
                lastConfigsData.configs[index].blending, newConfigsData.configs[index].blending,
                lastConfigsData.configs[index].plane_alpha, newConfigsData.configs[index].plane_alpha);
        return -1;
    }
    if ((lastConfigsData.configs[index].dst.x != newConfigsData.configs[index].dst.x) ||
        (lastConfigsData.configs[index].dst.y != newConfigsData.configs[index].dst.y) ||
        (lastConfigsData.configs[index].dst.w != newConfigsData.configs[index].dst.w) ||
        (lastConfigsData.configs[index].dst.h != newConfigsData.configs[index].dst.h) ||
        (lastConfigsData.configs[index].src.x != newConfigsData.configs[index].src.x) ||
        (lastConfigsData.configs[index].src.y != newConfigsData.configs[index].src.y) ||
        (lastConfigsData.configs[index].src.w != newConfigsData.configs[index].src.w) ||
        (lastConfigsData.configs[index].src.h != newConfigsData.configs[index].src.h))
        return 1;

    else
        return 0;
}

/**
 * @return int
 */
int ExynosDisplay::deliverWinConfigData() {

    ATRACE_CALL();
    String8 errString;
    int ret = NO_ERROR;
    struct timeval tv_s, tv_e;
    long timediff;

    ret = validateWinConfigData();
    if (ret != NO_ERROR) {
        errString.appendFormat("Invalid WIN_CONFIG\n");
        goto err;
    }

    for (size_t i = 0; i < mDpuData.configs.size(); i++) {
        DISPLAY_LOGD(eDebugWinConfig|eDebugSkipStaicLayer, "deliver config[%zu]", i);
        dumpConfig(mDpuData.configs[i]);
    }

    if (checkConfigChanged(mDpuData, mLastDpuData) == false) {
        DISPLAY_LOGD(eDebugWinConfig, "Winconfig : same");
#ifndef DISABLE_FENCE
        if (mLastRetireFence > 0) {
            mDpuData.retire_fence =
                hwcCheckFenceDebug(this, FENCE_TYPE_RETIRE, FENCE_IP_DPP,
                        hwc_dup(mLastRetireFence, this,  FENCE_TYPE_RETIRE, FENCE_IP_DPP));
        } else
            mDpuData.retire_fence = -1;
#endif
        ret = 0;
    } else {
        /* wait for 5 vsync */
        int32_t waitTime = mVsyncPeriod / 1000000 * 5;
        gettimeofday(&tv_s, NULL);
        if (mUsePowerHints) {
            mRetireFenceWaitTime = systemTime();
        }
        if (fence_valid(mLastRetireFence)) {
            ATRACE_NAME("waitLastRetireFence");
            if (sync_wait(mLastRetireFence, waitTime) < 0) {
                DISPLAY_LOGE("%s:: mLastRetireFence(%d) is not released during (%d ms)",
                        __func__, mLastRetireFence, waitTime);
                if (sync_wait(mLastRetireFence, 1000 - waitTime) < 0) {
                    DISPLAY_LOGE("%s:: mLastRetireFence sync wait error (%d)", __func__, mLastRetireFence);
                }
                else {
                    gettimeofday(&tv_e, NULL);
                    tv_e.tv_usec += (tv_e.tv_sec - tv_s.tv_sec) * 1000000;
                    timediff = tv_e.tv_usec - tv_s.tv_usec;
                    DISPLAY_LOGE("%s:: winconfig is delayed over 5 vysnc (fence:%d)(time:%ld)",
                            __func__, mLastRetireFence, timediff);
                }
            }
        }
        if (mUsePowerHints) {
            mRetireFenceAcquireTime = systemTime();
        }
        for (size_t i = 0; i < mDpuData.configs.size(); i++) {
            setFenceInfo(mDpuData.configs[i].acq_fence, this, FENCE_TYPE_SRC_ACQUIRE, FENCE_IP_DPP,
                         HwcFenceDirection::TO);
        }

        if ((ret = mDisplayInterface->deliverWinConfigData()) < 0) {
            errString.appendFormat("interface's deliverWinConfigData() failed: %s ret(%d)\n", strerror(errno), ret);
            goto err;
        } else {
            mLastDpuData = mDpuData;
        }

        for (size_t i = 0; i < mDpuData.configs.size(); i++) {
            setFenceInfo(mDpuData.configs[i].rel_fence, this, FENCE_TYPE_SRC_RELEASE, FENCE_IP_DPP,
                         HwcFenceDirection::FROM);
        }
        setFenceInfo(mDpuData.retire_fence, this, FENCE_TYPE_RETIRE, FENCE_IP_DPP,
                     HwcFenceDirection::FROM);
    }

    return ret;
err:
    printDebugInfos(errString);
    closeFences();
    clearDisplay();
    mDisplayInterface->setForcePanic();

    return ret;
}

/**
 * @return int
 */
int ExynosDisplay::setReleaseFences() {

    int release_fd = -1;
    String8 errString;

    /*
     * Close release fence for client target buffer
     * SurfaceFlinger doesn't get release fence for client target buffer
     */
    if ((mClientCompositionInfo.mHasCompositionLayer) &&
        (mClientCompositionInfo.mWindowIndex >= 0) &&
        (mClientCompositionInfo.mWindowIndex < (int32_t)mDpuData.configs.size())) {

        exynos_win_config_data &config = mDpuData.configs[mClientCompositionInfo.mWindowIndex];

        for (int i = mClientCompositionInfo.mFirstIndex; i <= mClientCompositionInfo.mLastIndex; i++) {
            if (mLayers[i]->mExynosCompositionType != HWC2_COMPOSITION_CLIENT) {
                if(mLayers[i]->mOverlayPriority < ePriorityHigh) {
                    errString.appendFormat("%d layer compositionType is not client(%d)\n", i, mLayers[i]->mExynosCompositionType);
                    goto err;
                } else {
                    continue;
                }
            }
            if (mType == HWC_DISPLAY_VIRTUAL)
                mLayers[i]->mReleaseFence = -1;
            else
                mLayers[i]->mReleaseFence =
                    hwcCheckFenceDebug(this, FENCE_TYPE_SRC_RELEASE, FENCE_IP_DPP,
                            hwc_dup(config.rel_fence, this,
                                FENCE_TYPE_SRC_RELEASE, FENCE_IP_DPP));
        }
        config.rel_fence = fence_close(config.rel_fence, this,
                   FENCE_TYPE_SRC_RELEASE, FENCE_IP_FB);
    }

    // DPU doesn't close acq_fence, HWC should close it.
    for (auto &config : mDpuData.configs) {
        if (config.acq_fence != -1)
            fence_close(config.acq_fence, this, FENCE_TYPE_SRC_ACQUIRE, FENCE_IP_DPP);
        config.acq_fence = -1;
    }
    for (auto &config : mDpuData.rcdConfigs) {
        if (config.acq_fence != -1)
            fence_close(config.acq_fence, this, FENCE_TYPE_SRC_ACQUIRE, FENCE_IP_DPP);
        config.acq_fence = -1;
    }
    // DPU doesn't close rel_fence of readback buffer, HWC should close it
    if (mDpuData.readback_info.rel_fence >= 0) {
        mDpuData.readback_info.rel_fence =
            fence_close(mDpuData.readback_info.rel_fence, this,
                FENCE_TYPE_READBACK_RELEASE, FENCE_IP_FB);
    }

    for (size_t i = 0; i < mLayers.size(); i++) {
        if ((mLayers[i]->mExynosCompositionType == HWC2_COMPOSITION_CLIENT) ||
            (mLayers[i]->mExynosCompositionType == HWC2_COMPOSITION_EXYNOS) ||
            (mLayers[i]->mExynosCompositionType == HWC2_COMPOSITION_DISPLAY_DECORATION))
            continue;
        if ((mLayers[i]->mWindowIndex < 0) ||
            (mLayers[i]->mWindowIndex >= mDpuData.configs.size())) {
            errString.appendFormat("%s:: layer[%zu] has invalid window index(%d)\n",
                    __func__, i, mLayers[i]->mWindowIndex);
            goto err;
        }
        exynos_win_config_data &config = mDpuData.configs[mLayers[i]->mWindowIndex];
        if (mLayers[i]->mOtfMPP != NULL) {
            mLayers[i]->mOtfMPP->setHWStateFence(-1);
        }
        if (mLayers[i]->mM2mMPP != NULL) {
            if (mLayers[i]->mM2mMPP->mUseM2MSrcFence)
                mLayers[i]->mReleaseFence = mLayers[i]->mM2mMPP->getSrcReleaseFence(0);
            else {
                mLayers[i]->mReleaseFence = hwcCheckFenceDebug(this, FENCE_TYPE_SRC_RELEASE, FENCE_IP_DPP,
                    hwc_dup(config.rel_fence, this,
                        FENCE_TYPE_SRC_RELEASE, FENCE_IP_LAYER));
            }

            mLayers[i]->mM2mMPP->resetSrcReleaseFence();
#ifdef DISABLE_FENCE
            mLayers[i]->mM2mMPP->setDstAcquireFence(-1);
#else
            DISPLAY_LOGD(eDebugFence, "m2m : win_index(%d), releaseFencefd(%d)",
                    mLayers[i]->mWindowIndex, config.rel_fence);
            if (config.rel_fence > 0) {
                release_fd = config.rel_fence;
                if (release_fd >= 0) {
                    setFenceInfo(release_fd, this, FENCE_TYPE_DST_ACQUIRE, FENCE_IP_DPP,
                                 HwcFenceDirection::UPDATE, true);
                    mLayers[i]->mM2mMPP->setDstAcquireFence(release_fd);
                } else {
                    DISPLAY_LOGE("fail to dup, ret(%d, %s)", errno, strerror(errno));
                    mLayers[i]->mM2mMPP->setDstAcquireFence(-1);
                }
            } else {
                mLayers[i]->mM2mMPP->setDstAcquireFence(-1);
            }
            DISPLAY_LOGD(eDebugFence, "mM2mMPP is used, layer[%zu].releaseFencefd(%d)",
                    i, mLayers[i]->mReleaseFence);
#endif
        } else {
#ifdef DISABLE_FENCE
            mLayers[i]->mReleaseFence = -1;
#else
            DISPLAY_LOGD(eDebugFence, "other : win_index(%d), releaseFencefd(%d)",
                    mLayers[i]->mWindowIndex, config.rel_fence);
            if (config.rel_fence > 0) {
                release_fd = hwcCheckFenceDebug(this, FENCE_TYPE_SRC_RELEASE, FENCE_IP_DPP, config.rel_fence);
                if (release_fd >= 0)
                    mLayers[i]->mReleaseFence = release_fd;
                else {
                    DISPLAY_LOGE("fail to dup, ret(%d, %s)", errno, strerror(errno));
                    mLayers[i]->mReleaseFence = -1;
                }
            } else {
                mLayers[i]->mReleaseFence = -1;
            }
            DISPLAY_LOGD(eDebugFence, "Direct overlay layer[%zu].releaseFencefd(%d)",
                i, mLayers[i]->mReleaseFence);
#endif
        }
    }

    if (mExynosCompositionInfo.mHasCompositionLayer) {
        if (mExynosCompositionInfo.mM2mMPP == NULL)
        {
            errString.appendFormat("There is exynos composition, but m2mMPP is NULL\n");
            goto err;
        }
        if (mUseDpu &&
            ((mExynosCompositionInfo.mWindowIndex < 0) ||
             (mExynosCompositionInfo.mWindowIndex >= (int32_t)mDpuData.configs.size()))) {
            errString.appendFormat("%s:: exynosComposition has invalid window index(%d)\n",
                    __func__, mExynosCompositionInfo.mWindowIndex);
            goto err;
        }
        exynos_win_config_data &config = mDpuData.configs[mExynosCompositionInfo.mWindowIndex];
        for (int i = mExynosCompositionInfo.mFirstIndex; i <= mExynosCompositionInfo.mLastIndex; i++) {
            /* break when only framebuffer target is assigned on ExynosCompositor */
            if (i == -1)
                break;

            if (mLayers[i]->mExynosCompositionType != HWC2_COMPOSITION_EXYNOS) {
                errString.appendFormat("%d layer compositionType is not exynos(%d)\n", i, mLayers[i]->mExynosCompositionType);
                goto err;
            }

            if (mExynosCompositionInfo.mM2mMPP->mUseM2MSrcFence)
                mLayers[i]->mReleaseFence =
                    mExynosCompositionInfo.mM2mMPP->getSrcReleaseFence(i-mExynosCompositionInfo.mFirstIndex);
            else {
                mLayers[i]->mReleaseFence =
                    hwc_dup(config.rel_fence,
                            this, FENCE_TYPE_SRC_RELEASE, FENCE_IP_LAYER);
            }

            DISPLAY_LOGD(eDebugFence, "exynos composition layer[%d].releaseFencefd(%d)",
                    i, mLayers[i]->mReleaseFence);
        }
        mExynosCompositionInfo.mM2mMPP->resetSrcReleaseFence();
        if(mUseDpu) {
#ifdef DISABLE_FENCE
            mExynosCompositionInfo.mM2mMPP->setDstAcquireFence(-1);
#else
            if (config.rel_fence > 0) {
                setFenceInfo(config.rel_fence, this, FENCE_TYPE_DST_ACQUIRE, FENCE_IP_DPP,
                             HwcFenceDirection::UPDATE, true);
                mExynosCompositionInfo.mM2mMPP->setDstAcquireFence(config.rel_fence);
            } else {
                mExynosCompositionInfo.mM2mMPP->setDstAcquireFence(-1);
            }
#endif
        }
    }
    return 0;

err:
    printDebugInfos(errString);
    closeFences();
    mDisplayInterface->setForcePanic();
    return -EINVAL;
}

/**
 * If display uses outbuf and outbuf is invalid, this function return false.
 * Otherwise, this function return true.
 * If outbuf is invalid, display should handle fence of layers.
 */
bool ExynosDisplay::checkFrameValidation() {
    return true;
}

int32_t ExynosDisplay::acceptDisplayChanges() {
    int32_t type = 0;
    if (mDropFrameDuringResSwitch) {
        return HWC2_ERROR_NONE;
    }
    if (mRenderingState != RENDERING_STATE_VALIDATED) {
        DISPLAY_LOGE("%s:: display is not validated : %d", __func__, mRenderingState);
        return HWC2_ERROR_NOT_VALIDATED;
    }

    for (size_t i = 0; i < mLayers.size(); i++) {
        if (mLayers[i] != NULL) {
            HDEBUGLOGD(eDebugDefault, "%s, Layer %zu : %d, %d", __func__, i,
                    mLayers[i]->mExynosCompositionType, mLayers[i]->mValidateCompositionType);
            type = getLayerCompositionTypeForValidationType(i);

            /* update compositionType
             * SF updates their state and doesn't call back into HWC HAL
             */
            mLayers[i]->mCompositionType = type;
            mLayers[i]->mExynosCompositionType = mLayers[i]->mValidateCompositionType;
        }
        else {
            HWC_LOGE(this, "Layer %zu is NULL", i);
        }
    }
    mRenderingState = RENDERING_STATE_ACCEPTED_CHANGE;

    return HWC2_ERROR_NONE;
}

int32_t ExynosDisplay::createLayer(hwc2_layer_t* outLayer) {

    Mutex::Autolock lock(mDRMutex);
    if (mPlugState == false) {
        DISPLAY_LOGI("%s : skip createLayer. Display is already disconnected", __func__);
        return HWC2_ERROR_BAD_DISPLAY;
    }

    /* TODO : Implementation here */
    ExynosLayer *layer = new ExynosLayer(this);

    /* TODO : Sort sequence should be added to somewhere */
    mLayers.add((ExynosLayer*)layer);

    /* TODO : Set z-order to max, check outLayer address? */
    layer->setLayerZOrder(1000);

    *outLayer = (hwc2_layer_t)layer;
    setGeometryChanged(GEOMETRY_DISPLAY_LAYER_ADDED);

    return HWC2_ERROR_NONE;
}

int32_t ExynosDisplay::getActiveConfig(hwc2_config_t* outConfig)
{
    Mutex::Autolock lock(mDisplayMutex);
    return getActiveConfigInternal(outConfig);
}

int32_t ExynosDisplay::getActiveConfigInternal(hwc2_config_t* outConfig)
{
    *outConfig = mActiveConfig;

    return HWC2_ERROR_NONE;
}

int32_t ExynosDisplay::getLayerCompositionTypeForValidationType(uint32_t layerIndex)
{
    int32_t type = -1;

    if (layerIndex >= mLayers.size())
    {
        DISPLAY_LOGE("invalid layer index (%d)", layerIndex);
        return type;
    }
    if ((mLayers[layerIndex]->mValidateCompositionType == HWC2_COMPOSITION_CLIENT) &&
        (mClientCompositionInfo.mSkipFlag) &&
        (mClientCompositionInfo.mFirstIndex <= (int32_t)layerIndex) &&
        ((int32_t)layerIndex <= mClientCompositionInfo.mLastIndex)) {
        type = HWC2_COMPOSITION_DEVICE;
    } else if (mLayers[layerIndex]->mValidateCompositionType == HWC2_COMPOSITION_EXYNOS) {
        type = HWC2_COMPOSITION_DEVICE;
    } else if ((mLayers[layerIndex]->mCompositionType == HWC2_COMPOSITION_CURSOR) &&
               (mLayers[layerIndex]->mValidateCompositionType == HWC2_COMPOSITION_DEVICE)) {
        if (mDisplayControl.cursorSupport == true)
            type = HWC2_COMPOSITION_CURSOR;
        else
            type = HWC2_COMPOSITION_DEVICE;
    } else if ((mLayers[layerIndex]->mCompositionType == HWC2_COMPOSITION_SOLID_COLOR) &&
               (mLayers[layerIndex]->mValidateCompositionType == HWC2_COMPOSITION_DEVICE)) {
        type = HWC2_COMPOSITION_SOLID_COLOR;
    } else if ((mLayers[layerIndex]->mCompositionType == HWC2_COMPOSITION_REFRESH_RATE_INDICATOR) &&
               (mLayers[layerIndex]->mValidateCompositionType == HWC2_COMPOSITION_DEVICE)) {
        type = HWC2_COMPOSITION_REFRESH_RATE_INDICATOR;
    } else {
        type = mLayers[layerIndex]->mValidateCompositionType;
    }

    return type;
}

int32_t ExynosDisplay::getChangedCompositionTypes(
        uint32_t* outNumElements, hwc2_layer_t* outLayers,
        int32_t* /*hwc2_composition_t*/ outTypes) {
    if (mDropFrameDuringResSwitch) {
        if ((outLayers == NULL) || (outTypes == NULL)) {
            *outNumElements = 0;
        }
        return HWC2_ERROR_NONE;
    }

    if (mRenderingState != RENDERING_STATE_VALIDATED) {
        DISPLAY_LOGE("%s:: display is not validated : %d", __func__, mRenderingState);
        return HWC2_ERROR_NOT_VALIDATED;
    }

    uint32_t count = 0;

    auto set_out_param = [](ExynosLayer *layer, int32_t type, uint32_t &count, uint32_t num,
                            hwc2_layer_t *out_layers, int32_t *out_types) -> int32_t {
        if (type == layer->mCompositionType) {
            return 0;
        }
        if (out_layers == NULL || out_types == NULL) {
            count++;
        } else {
            if (count < num) {
                out_layers[count] = (hwc2_layer_t)layer;
                out_types[count] = type;
                count++;
            } else {
                return -1;
            }
        }
        return 0;
    };

    int32_t ret = 0;
    for (size_t i = 0; i < mLayers.size(); i++) {
        DISPLAY_LOGD(eDebugHWC, "[%zu] layer: mCompositionType(%d), mValidateCompositionType(%d), mExynosCompositionType(%d), skipFlag(%d)",
                i, mLayers[i]->mCompositionType, mLayers[i]->mValidateCompositionType,
                mLayers[i]->mExynosCompositionType, mClientCompositionInfo.mSkipFlag);
        if ((ret = set_out_param(mLayers[i], getLayerCompositionTypeForValidationType(i), count,
                                 *outNumElements, outLayers, outTypes)) < 0) {
            break;
        }
    }
    if (ret == 0) {
        for (size_t i = 0; i < mIgnoreLayers.size(); i++) {
            DISPLAY_LOGD(eDebugHWC,
                         "[%zu] ignore layer: mCompositionType(%d), mValidateCompositionType(%d)",
                         i, mIgnoreLayers[i]->mCompositionType,
                         mIgnoreLayers[i]->mValidateCompositionType);
            if ((ret = set_out_param(mIgnoreLayers[i], mIgnoreLayers[i]->mValidateCompositionType,
                                     count, *outNumElements, outLayers, outTypes)) < 0) {
                break;
            }
        }
    }
    if (ret < 0) {
        DISPLAY_LOGE("array size is not valid (%d, %d)", count, *outNumElements);
        String8 errString;
        errString.appendFormat("array size is not valid (%d, %d)", count, *outNumElements);
        printDebugInfos(errString);
        return ret;
    }

    if ((outLayers == NULL) || (outTypes == NULL))
        *outNumElements = count;

    return HWC2_ERROR_NONE;
}

int32_t ExynosDisplay::getClientTargetSupport(uint32_t width, uint32_t height,
                                              int32_t /*android_pixel_format_t*/ format,
                                              int32_t /*android_dataspace_t*/ dataspace)
{
    if (width != mXres)
        return HWC2_ERROR_UNSUPPORTED;
    if (height != mYres)
        return HWC2_ERROR_UNSUPPORTED;
    if (format != HAL_PIXEL_FORMAT_RGBA_8888)
        return HWC2_ERROR_UNSUPPORTED;
    if ((dataspace != HAL_DATASPACE_UNKNOWN) &&
        (!mDisplayInterface->supportDataspace(dataspace)))
        return HWC2_ERROR_UNSUPPORTED;

    return HWC2_ERROR_NONE;
}

int32_t ExynosDisplay::getColorModes(uint32_t *outNumModes, int32_t * /*android_color_mode_t*/ outModes)
{
    return mDisplayInterface->getColorModes(outNumModes, outModes);
}

int32_t ExynosDisplay::getDisplayAttribute(
        hwc2_config_t config,
        int32_t /*hwc2_attribute_t*/ attribute, int32_t* outValue) {

    switch (attribute) {
    case HWC2_ATTRIBUTE_VSYNC_PERIOD:
        *outValue = mDisplayConfigs[config].vsyncPeriod;
        break;

    case HWC2_ATTRIBUTE_WIDTH:
        *outValue = mDisplayConfigs[config].width;
        break;

    case HWC2_ATTRIBUTE_HEIGHT:
        *outValue = mDisplayConfigs[config].height;
        break;

    case HWC2_ATTRIBUTE_DPI_X:
        *outValue = mDisplayConfigs[config].Xdpi;
        break;

    case HWC2_ATTRIBUTE_DPI_Y:
        *outValue = mDisplayConfigs[config].Ydpi;
        break;

    case HWC2_ATTRIBUTE_CONFIG_GROUP:
        *outValue = mDisplayConfigs[config].groupId;
        break;

    default:
        ALOGE("unknown display attribute %u", attribute);
        return HWC2_ERROR_BAD_CONFIG;
    }

    return HWC2_ERROR_NONE;
}

int32_t ExynosDisplay::getDisplayConfigs(
        uint32_t* outNumConfigs,
        hwc2_config_t* outConfigs) {
    return mDisplayInterface->getDisplayConfigs(outNumConfigs, outConfigs);
}

int32_t ExynosDisplay::getDisplayName(uint32_t* outSize, char* outName)
{
    if (outName == NULL) {
        *outSize = mDisplayName.size();
        return HWC2_ERROR_NONE;
    }

    uint32_t strSize = mDisplayName.size();
    if (*outSize < strSize) {
        DISPLAY_LOGE("Invalide outSize(%d), mDisplayName.size(%d)",
                *outSize, strSize);
        strSize = *outSize;
    }
    std::strncpy(outName, mDisplayName.c_str(), strSize);
    *outSize = strSize;

    return HWC2_ERROR_NONE;
}

int32_t ExynosDisplay::getDisplayRequests(
        int32_t* /*hwc2_display_request_t*/ outDisplayRequests,
        uint32_t* outNumElements, hwc2_layer_t* outLayers,
        int32_t* /*hwc2_layer_request_t*/ outLayerRequests) {
    /*
     * This function doesn't check mRenderingState
     * This can be called in the below rendering state
     * RENDERING_STATE_PRESENTED: when it is called by canSkipValidate()
     * RENDERING_STATE_ACCEPTED_CHANGE: when it is called by SF
     * RENDERING_STATE_VALIDATED:  when it is called by validateDisplay()
     */

    String8 errString;
    *outDisplayRequests = 0;

    if (mDropFrameDuringResSwitch) {
        if ((outLayers == NULL) || (outLayerRequests == NULL)) {
            *outNumElements = 0;
        }
        return HWC2_ERROR_NONE;
    }

    uint32_t requestNum = 0;
    if (mClientCompositionInfo.mHasCompositionLayer == true) {
        if ((mClientCompositionInfo.mFirstIndex < 0) ||
            (mClientCompositionInfo.mFirstIndex >= (int)mLayers.size()) ||
            (mClientCompositionInfo.mLastIndex < 0) ||
            (mClientCompositionInfo.mLastIndex >= (int)mLayers.size())) {
            errString.appendFormat("%s:: mClientCompositionInfo.mHasCompositionLayer is true "
                    "but index is not valid (firstIndex: %d, lastIndex: %d)\n",
                    __func__, mClientCompositionInfo.mFirstIndex,
                    mClientCompositionInfo.mLastIndex);
            goto err;
        }

        for (int32_t i = mClientCompositionInfo.mFirstIndex; i < mClientCompositionInfo.mLastIndex; i++) {
            ExynosLayer *layer = mLayers[i];
            if (layer->needClearClientTarget()) {
                if ((outLayers != NULL) && (outLayerRequests != NULL)) {
                    if (requestNum >= *outNumElements)
                        return -1;
                    outLayers[requestNum] = (hwc2_layer_t)layer;
                    outLayerRequests[requestNum] = HWC2_LAYER_REQUEST_CLEAR_CLIENT_TARGET;
                }
                requestNum++;
            }
        }
    }
    if ((outLayers == NULL) || (outLayerRequests == NULL))
        *outNumElements = requestNum;

    return HWC2_ERROR_NONE;

err:
    printDebugInfos(errString);
    *outNumElements = 0;
    mDisplayInterface->setForcePanic();
    return -EINVAL;
}

int32_t ExynosDisplay::getDisplayType(
        int32_t* /*hwc2_display_type_t*/ outType) {
    switch (mType) {
    case HWC_DISPLAY_PRIMARY:
    case HWC_DISPLAY_EXTERNAL:
        *outType = HWC2_DISPLAY_TYPE_PHYSICAL;
        return HWC2_ERROR_NONE;
    case HWC_DISPLAY_VIRTUAL:
        *outType = HWC2_DISPLAY_TYPE_VIRTUAL;
        return HWC2_ERROR_NONE;
    default:
        DISPLAY_LOGE("Invalid display type(%d)", mType);
        *outType = HWC2_DISPLAY_TYPE_INVALID;
        return HWC2_ERROR_NONE;
    }
}

int32_t ExynosDisplay::getDozeSupport(
        int32_t* outSupport) {
    if (mDisplayInterface->isDozeModeAvailable()) {
        *outSupport = 1;
    } else {
        *outSupport = 0;
    }

    return 0;
}

int32_t ExynosDisplay::getReleaseFences(
        uint32_t* outNumElements,
        hwc2_layer_t* outLayers, int32_t* outFences) {

    if (outNumElements == NULL) {
        return HWC2_ERROR_BAD_PARAMETER;
    }

    Mutex::Autolock lock(mDisplayMutex);
    uint32_t deviceLayerNum = 0;
    if (outLayers != NULL && outFences != NULL) {
        // second pass call
        for (size_t i = 0; i < mLayers.size(); i++) {
            if (mLayers[i]->mReleaseFence >= 0) {
                if (deviceLayerNum < *outNumElements) {
                    // transfer fence ownership to the caller
                    setFenceName(mLayers[i]->mReleaseFence, FENCE_LAYER_RELEASE_DPP);
                    outLayers[deviceLayerNum] = (hwc2_layer_t)mLayers[i];
                    outFences[deviceLayerNum] = mLayers[i]->mReleaseFence;
                    mLayers[i]->mReleaseFence = -1;

                    DISPLAY_LOGD(eDebugHWC, "[%zu] layer deviceLayerNum(%d), release fence: %d", i,
                                 deviceLayerNum, outFences[deviceLayerNum]);
                } else {
                    // *outNumElements is not from the first pass call.
                    DISPLAY_LOGE("%s: outNumElements %d too small", __func__, *outNumElements);
                    return HWC2_ERROR_BAD_PARAMETER;
                }
                deviceLayerNum++;
            }
        }
    } else {
        // first pass call
        for (size_t i = 0; i < mLayers.size(); i++) {
            if (mLayers[i]->mReleaseFence >= 0) {
                deviceLayerNum++;
            }
        }
    }
    *outNumElements = deviceLayerNum;

    return 0;
}

int32_t ExynosDisplay::canSkipValidate() {
    if (exynosHWCControl.skipResourceAssign == 0)
        return SKIP_ERR_CONFIG_DISABLED;

    /* This is first frame. validateDisplay can't be skipped */
    if (mRenderingState == RENDERING_STATE_NONE)
        return SKIP_ERR_FIRST_FRAME;

    if (mDevice->mGeometryChanged != 0) {
        /* validateDisplay() should be called */
        return SKIP_ERR_GEOMETRY_CHAGNED;
    } else {
        for (uint32_t i = 0; i < mLayers.size(); i++) {
            if (getLayerCompositionTypeForValidationType(i) ==
                    HWC2_COMPOSITION_CLIENT) {
                return SKIP_ERR_HAS_CLIENT_COMP;
            }
        }

        if ((mClientCompositionInfo.mSkipStaticInitFlag == true) &&
            (mClientCompositionInfo.mSkipFlag == true)) {
            if (skipStaticLayerChanged(mClientCompositionInfo) == true)
                return SKIP_ERR_SKIP_STATIC_CHANGED;
        }

        if (mClientCompositionInfo.mHasCompositionLayer &&
            mClientCompositionInfo.mTargetBuffer == NULL) {
            return SKIP_ERR_INVALID_CLIENT_TARGET_BUFFER;
        }

        /*
         * If there is hwc2_layer_request_t
         * validateDisplay() can't be skipped
         */
        int32_t displayRequests = 0;
        uint32_t outNumRequests = 0;
        if ((getDisplayRequests(&displayRequests, &outNumRequests, NULL, NULL) != NO_ERROR) ||
            (outNumRequests != 0))
            return SKIP_ERR_HAS_REQUEST;
    }
    return NO_ERROR;
}

bool ExynosDisplay::isFullScreenComposition() {
    hwc_rect_t dispRect = { INT_MAX, INT_MAX, 0, 0 };
    for (auto layer : mLayers) {
        auto &r = layer->mDisplayFrame;

        if (r.top < dispRect.top)
            dispRect.top = r.top;
        if (r.left < dispRect.left)
            dispRect.left = r.left;
        if (r.bottom > dispRect.bottom)
            dispRect.bottom = r.bottom;
        if (r.right > dispRect.right)
            dispRect.right = r.right;
    }

    if ((dispRect.right != mXres) || (dispRect.bottom != mYres)) {
        ALOGD("invalid displayFrame disp=[%d %d %d %d] expected=%dx%d",
                dispRect.left, dispRect.top, dispRect.right, dispRect.bottom,
                mXres, mYres);
        return false;
    }

    return true;
}

void dumpBuffer(const String8& prefix, const exynos_image& image, std::ofstream& configFile) {
    ATRACE_NAME(prefix.c_str());
    if (image.bufferHandle == nullptr) {
        ALOGE("%s: Buffer handle for %s is NULL", __func__, prefix.c_str());
        return;
    }
    ALOGI("%s: dumping buffer for %s", __func__, prefix.c_str());

    bool failFenceSync = false;
    String8 infoDump;
    if (image.acquireFenceFd > 0 && sync_wait(image.acquireFenceFd, 1000) < 0) {
        infoDump.appendFormat("Failed to sync acquire fence\n");
        ALOGE("%s: Failed to wait acquire fence %d, errno=(%d, %s)", __func__, image.acquireFenceFd,
              errno, strerror(errno));
        failFenceSync = true;
    }
    if (image.releaseFenceFd > 0 && sync_wait(image.releaseFenceFd, 1000) < 0) {
        infoDump.appendFormat("Failed to sync release fence\n");

        ALOGE("%s: Failed to wait release fence %d, errno=(%d, %s)", __func__, image.releaseFenceFd,
              errno, strerror(errno));
        failFenceSync = true;
    }

    VendorGraphicBufferMeta gmeta(image.bufferHandle);
    String8 infoPath = String8::format("%s/%s-info.txt", kBufferDumpPath, prefix.c_str());
    std::ofstream infoFile(infoPath.c_str());
    if (!infoFile) {
        ALOGE("%s: failed to open file %s", __func__, infoPath.c_str());
        return;
    }

    // TODO(b/261232489): Fix fence sync errors
    // We currently ignore the fence errors and just dump the buffers

    // dump buffer info
    dumpExynosImage(infoDump, image);
    infoDump.appendFormat("\nfd[%d, %d, %d] size[%d, %d, %d]\n", gmeta.fd, gmeta.fd1, gmeta.fd2,
                          gmeta.size, gmeta.size1, gmeta.size2);
    infoDump.appendFormat(" offset[%d, %d, %d] format:%d framework_format:%d\n", gmeta.offset,
                          gmeta.offset1, gmeta.offset2, gmeta.format, gmeta.frameworkFormat);
    infoDump.appendFormat(" width:%d height:%d stride:%d vstride:%d\n", gmeta.width, gmeta.height,
                          gmeta.stride, gmeta.vstride);
    infoDump.appendFormat(" producer: 0x%" PRIx64 " consumer: 0x%" PRIx64 " flags: 0x%" PRIx32 "\n",
                          gmeta.producer_usage, gmeta.consumer_usage, gmeta.flags);
    infoFile << infoDump << std::endl;

    String8 bufferPath =
            String8::format("%s/%s-%s.raw", kBufferDumpPath, prefix.c_str(),
                            getFormatStr(image.format, image.compressionInfo.type).c_str());
    std::ofstream bufferFile(bufferPath.c_str(), std::ios::binary);
    if (!bufferFile) {
        ALOGE("%s: failed to open file %s", __func__, bufferPath.c_str());
        return;
    }

    // dump info that can be loaded by hwc-tester
    configFile << "buffers {\n";
    configFile << "    key: \"" << prefix << "\"\n";
    configFile << "    format: " << getFormatStr(image.format, image.compressionInfo.type) << "\n";
    configFile << "    width: " << gmeta.width << "\n";
    configFile << "    height: " << gmeta.height << "\n";
    auto usage = gmeta.producer_usage | gmeta.consumer_usage;
    configFile << "    usage: 0x" << std::hex << usage << std::dec << "\n";
    configFile << "    filepath: \"" << bufferPath << "\"\n";
    configFile << "}\n" << std::endl;

    int bufferNumber = getBufferNumOfFormat(image.format, image.compressionInfo.type);
    for (int i = 0; i < bufferNumber; ++i) {
        if (gmeta.fds[i] <= 0) {
            ALOGE("%s: gmeta.fds[%d]=%d is invalid", __func__, i, gmeta.fds[i]);
            continue;
        }
        if (gmeta.sizes[i] <= 0) {
            ALOGE("%s: gmeta.sizes[%d]=%d is invalid", __func__, i, gmeta.sizes[i]);
            continue;
        }
        auto addr = mmap(0, gmeta.sizes[i], PROT_READ | PROT_WRITE, MAP_SHARED, gmeta.fds[i], 0);
        if (addr != MAP_FAILED && addr != NULL) {
            bufferFile.write(static_cast<char*>(addr), gmeta.sizes[i]);
            munmap(addr, gmeta.sizes[i]);
        } else {
            ALOGE("%s: failed to mmap fds[%d]:%d for %s", __func__, i, gmeta.fds[i]);
        }
    }
}

void ExynosDisplay::dumpAllBuffers() {
    ATRACE_CALL();
    // dump layers info
    String8 infoPath = String8::format("%s/%03d-display-info.txt", kBufferDumpPath, mBufferDumpNum);
    std::ofstream infoFile(infoPath.c_str());
    if (!infoFile) {
        DISPLAY_LOGE("%s: failed to open file %s", __func__, infoPath.c_str());
        ++mBufferDumpNum;
        return;
    }
    String8 displayDump;
    dumpLocked(displayDump);
    infoFile << displayDump << std::endl;

    // dump buffer contents & infos
    std::vector<String8> allLayerKeys;
    String8 testerConfigPath =
            String8::format("%s/%03d-hwc-tester-config.textproto", kBufferDumpPath, mBufferDumpNum);
    std::ofstream configFile(testerConfigPath.c_str());
    configFile << std::string(15, '#')
               << " You can load this config file using hwc-tester to reproduce this frame "
               << std::string(15, '#') << std::endl;
    {
        std::scoped_lock lock(mDRMutex);
        for (int i = 0; i < mLayers.size(); ++i) {
            String8 prefix = String8::format("%03d-%d-src", mBufferDumpNum, i);
            dumpBuffer(prefix, mLayers[i]->mSrcImg, configFile);
            if (mLayers[i]->mM2mMPP != nullptr) {
                String8 midPrefix = String8::format("%03d-%d-mid", mBufferDumpNum, i);
                exynos_image image = mLayers[i]->mMidImg;
                mLayers[i]->mM2mMPP->getDstImageInfo(&image);
                dumpBuffer(midPrefix, image, configFile);
            }
            configFile << "layers {\n";
            configFile << "    key: \"" << prefix << "\"\n";
            configFile << "    composition: "
                       << AidlComposer3::toString(static_cast<AidlComposer3::Composition>(
                                  mLayers[i]->mRequestedCompositionType))
                       << "\n";
            configFile << "    source_crop: {\n";
            configFile << "        left: " << mLayers[i]->mPreprocessedInfo.sourceCrop.left << "\n";
            configFile << "        top: " << mLayers[i]->mPreprocessedInfo.sourceCrop.top << "\n";
            configFile << "        right: " << mLayers[i]->mPreprocessedInfo.sourceCrop.right
                       << "\n";
            configFile << "        bottom: " << mLayers[i]->mPreprocessedInfo.sourceCrop.bottom
                       << "\n";
            configFile << "    }\n";
            configFile << "    display_frame: {\n";
            configFile << "        left: " << mLayers[i]->mPreprocessedInfo.displayFrame.left
                       << "\n";
            configFile << "        top: " << mLayers[i]->mPreprocessedInfo.displayFrame.top << "\n";
            configFile << "        right: " << mLayers[i]->mPreprocessedInfo.displayFrame.right
                       << "\n";
            configFile << "        bottom: " << mLayers[i]->mPreprocessedInfo.displayFrame.bottom
                       << "\n";
            configFile << "    }\n";
            configFile << "    dataspace: "
                       << AidlCommon::toString(
                                  static_cast<AidlCommon::Dataspace>(mLayers[i]->mDataSpace))
                       << "\n";
            configFile << "    blend: "
                       << AidlCommon::toString(
                                  static_cast<AidlCommon::BlendMode>(mLayers[i]->mBlending))
                       << "\n";
            configFile << "    transform: "
                       << AidlCommon::toString(
                                  static_cast<AidlCommon::Transform>(mLayers[i]->mTransform))
                       << "\n";
            configFile << "    plane_alpha: " << mLayers[i]->mPlaneAlpha << "\n";
            configFile << "    z_order: " << mLayers[i]->mZOrder << "\n";
            if (mLayers[i]->mRequestedCompositionType == HWC2_COMPOSITION_SOLID_COLOR) {
                configFile << "    color: {\n";
                configFile << "        r: " << mLayers[i]->mColor.r << "\n";
                configFile << "        g: " << mLayers[i]->mColor.g << "\n";
                configFile << "        b: " << mLayers[i]->mColor.b << "\n";
                configFile << "        a: " << mLayers[i]->mColor.a << "\n";
                configFile << "    }\n";
            } else if (mLayers[i]->mSrcImg.bufferHandle != nullptr) {
                configFile << "    buffer_key: \"" << prefix << "\"\n";
            }
            configFile << "}\n" << std::endl;
            allLayerKeys.push_back(prefix);
        }
    }

    if (mClientCompositionInfo.mHasCompositionLayer) {
        String8 prefix = String8::format("%03d-client-target", mBufferDumpNum);
        exynos_image src, dst;
        setCompositionTargetExynosImage(COMPOSITION_CLIENT, &src, &dst);
        dumpBuffer(prefix, src, configFile);
    }

    configFile << "timelines {\n";
    configFile << "    display_id: " << mDisplayId << "\n";
    configFile << "    width: " << mXres << "\n";
    configFile << "    height: " << mYres << "\n";
    configFile << "    color_mode: "
               << AidlComposer3::toString(static_cast<AidlComposer3::ColorMode>(mColorMode))
               << "\n";
    configFile << std::endl;
    for (auto& layerKey : allLayerKeys) {
        configFile << "    layers: {\n";
        configFile << "        layer_key: \"" << layerKey << "\"\n";
        configFile << "    }\n";
    }
    configFile << "}" << std::endl;

    ++mBufferDumpNum;
}

int32_t ExynosDisplay::presentDisplay(int32_t* outRetireFence) {
    DISPLAY_ATRACE_CALL();
    gettimeofday(&updateTimeInfo.lastPresentTime, NULL);

    const bool mixedComposition = isMixedComposition();
    // store this once here for the whole frame so it's consistent
    mUsePowerHints = usePowerHintSession();
    if (mUsePowerHints) {
        // adds + removes the tid for adpf tracking
        mPowerHalHint.trackThisThread();
        mPresentStartTime = systemTime(SYSTEM_TIME_MONOTONIC);
        if (!mValidateStartTime.has_value()) {
            mValidationDuration = std::nullopt;
            // load target time here if validation was skipped
            mExpectedPresentTime = getExpectedPresentTime(mPresentStartTime);
            auto target = min(mExpectedPresentTime - mPresentStartTime,
                              static_cast<nsecs_t>(mVsyncPeriod));
            mPowerHalHint.signalTargetWorkDuration(target);
            // if we did not validate (have not sent hint yet) and have data for this case
            std::optional<nsecs_t> predictedDuration = getPredictedDuration(false);
            if (predictedDuration.has_value()) {
                mPowerHalHint.signalActualWorkDuration(*predictedDuration);
            }
        }
        mRetireFenceWaitTime = std::nullopt;
        mValidateStartTime = std::nullopt;
    }

    int ret = HWC2_ERROR_NONE;
    String8 errString;
    thread_local bool setTaskProfileDone = false;

    if (setTaskProfileDone == false) {
        if (!SetTaskProfiles(gettid(), {"SFMainPolicy"})) {
            ALOGW("Failed to add `%d` into SFMainPolicy", gettid());
        }
        setTaskProfileDone = true;
    }

    Mutex::Autolock lock(mDisplayMutex);

    if (!mHpdStatus) {
        ALOGD("presentDisplay: drop frame: mHpdStatus == false");
    }

    mDropFrameDuringResSwitch =
            (mGeometryChanged & GEOMETRY_DISPLAY_RESOLUTION_CHANGED) && !isFullScreenComposition();
    if (mDropFrameDuringResSwitch) {
        ALOGD("presentDisplay: drop invalid frame during resolution switch");
    }

    if (!mHpdStatus || mDropFrameDuringResSwitch || mPauseDisplay || mDevice->isInTUI()) {
        closeFencesForSkipFrame(RENDERING_STATE_PRESENTED);
        *outRetireFence = -1;
        mRenderingState = RENDERING_STATE_PRESENTED;
        applyExpectedPresentTime();
        return ret;
    }

    /*
     * buffer handle, dataspace were set by setClientTarget() after validateDisplay
     * ExynosImage should be set again according to changed handle and dataspace
     */
    exynos_image src_img;
    exynos_image dst_img;
    setCompositionTargetExynosImage(COMPOSITION_CLIENT, &src_img, &dst_img);
    mClientCompositionInfo.setExynosImage(src_img, dst_img);
    mClientCompositionInfo.setExynosMidImage(dst_img);

    funcReturnCallback presentRetCallback([&]() {
        if (ret != HWC2_ERROR_NOT_VALIDATED)
            presentPostProcessing();
    });

    if (mSkipFrame) {
        ALOGI("[%d] presentDisplay is skipped by mSkipFrame", mDisplayId);
        closeFencesForSkipFrame(RENDERING_STATE_PRESENTED);
        setGeometryChanged(GEOMETRY_DISPLAY_FORCE_VALIDATE);
        *outRetireFence = -1;
        for (size_t i=0; i < mLayers.size(); i++) {
            mLayers[i]->mReleaseFence = -1;
        }
        if (mRenderingState == RENDERING_STATE_NONE) {
            ALOGD("\tThis is the first frame after power on");
            ret = HWC2_ERROR_NONE;
        } else {
            ALOGD("\tThis is the second frame after power on");
            ret = HWC2_ERROR_NOT_VALIDATED;
        }
        mRenderingState = RENDERING_STATE_PRESENTED;
        mDevice->onRefresh(mDisplayId);
        return ret;
    }

    tryUpdateBtsFromOperationRate(true);

    if (mRenderingState != RENDERING_STATE_ACCEPTED_CHANGE) {
        /*
         * presentDisplay() can be called before validateDisplay()
         * when HWC2_CAPABILITY_SKIP_VALIDATE is supported
         */
#ifdef HWC_NO_SUPPORT_SKIP_VALIDATE
        DISPLAY_LOGE("%s:: Skip validate is not supported. Invalid rendering state : %d", __func__, mRenderingState);
        goto err;
#endif
        if ((mRenderingState != RENDERING_STATE_NONE) &&
            (mRenderingState != RENDERING_STATE_PRESENTED)) {
            DISPLAY_LOGE("%s:: invalid rendering state : %d", __func__, mRenderingState);
            goto err;
        }

        if (mDevice->canSkipValidate() == false)
            goto not_validated;
        else {
            for (size_t i=0; i < mLayers.size(); i++) {
                // Layer's acquire fence from SF
                mLayers[i]->setSrcAcquireFence();
            }
            DISPLAY_LOGD(eDebugSkipValidate, "validate is skipped");
        }

        if (updateColorConversionInfo() != NO_ERROR) {
            ALOGE("%s:: updateColorConversionInfo() fail, ret(%d)",
                    __func__, ret);
        }
        if (mDisplayControl.earlyStartMPP == true) {
            /*
             * HWC should update performanceInfo when validate is skipped
             * HWC excludes the layer from performance calculation
             * if there is no buffer update. (using ExynosMPP::canSkipProcessing())
             * Therefore performanceInfo should be calculated again if the buffer is updated.
             */
            if ((ret = mDevice->mResourceManager->deliverPerformanceInfo()) != NO_ERROR) {
                DISPLAY_LOGE("deliverPerformanceInfo() error (%d) in validateSkip case", ret);
            }
            startPostProcessing();
        }
    }
    mRetireFenceAcquireTime = std::nullopt;
    mDpuData.reset();

    if (mConfigRequestState == hwc_request_state_t::SET_CONFIG_STATE_PENDING) {
        if ((ret = doDisplayConfigPostProcess(mDevice)) != NO_ERROR) {
            DISPLAY_LOGE("doDisplayConfigPostProcess error (%d)", ret);
        }
    }

    if (updatePresentColorConversionInfo() != NO_ERROR) {
        ALOGE("%s:: updatePresentColorConversionInfo() fail, ret(%d)",
                __func__, ret);
    }

    if ((mLayers.size() == 0) &&
        (mType != HWC_DISPLAY_VIRTUAL)) {
        ALOGI("%s:: layer size is 0", __func__);
        clearDisplay();
        *outRetireFence = -1;
        mLastRetireFence = fence_close(mLastRetireFence, this,
                FENCE_TYPE_RETIRE, FENCE_IP_DPP);
        mRenderingState = RENDERING_STATE_PRESENTED;
        ret = 0;
        return ret;
    }

    if (!checkFrameValidation()) {
        ALOGW("%s: checkFrameValidation fail", __func__);
        clearDisplay();
        *outRetireFence = -1;
        mLastRetireFence = fence_close(mLastRetireFence, this,
                FENCE_TYPE_RETIRE, FENCE_IP_DPP);
        mRenderingState = RENDERING_STATE_PRESENTED;
        return ret;
    }

    if ((mDisplayControl.earlyStartMPP == false) &&
        ((ret = doExynosComposition()) != NO_ERROR)) {
        errString.appendFormat("exynosComposition fail (%d)\n", ret);
        goto err;
    }

    // loop for all layer
    for (size_t i=0; i < mLayers.size(); i++) {
        /* mAcquireFence is updated, Update image info */
        struct exynos_image srcImg, dstImg, midImg;
        mLayers[i]->setSrcExynosImage(&srcImg);
        mLayers[i]->setDstExynosImage(&dstImg);
        mLayers[i]->setExynosImage(srcImg, dstImg);

        if (mLayers[i]->mExynosCompositionType == HWC2_COMPOSITION_CLIENT) {
            mLayers[i]->mReleaseFence = -1;
            mLayers[i]->mAcquireFence =
                fence_close(mLayers[i]->mAcquireFence, this,
                        FENCE_TYPE_SRC_ACQUIRE, FENCE_IP_LAYER);
        } else if (mLayers[i]->mExynosCompositionType == HWC2_COMPOSITION_EXYNOS) {
            continue;
        } else {
            if (mLayers[i]->mOtfMPP != NULL) {
                mLayers[i]->mOtfMPP->requestHWStateChange(MPP_HW_STATE_RUNNING);
            }

            if ((mDisplayControl.earlyStartMPP == false) &&
                (mLayers[i]->mExynosCompositionType == HWC2_COMPOSITION_DEVICE) &&
                (mLayers[i]->mM2mMPP != NULL)) {
                ExynosMPP* m2mMpp = mLayers[i]->mM2mMPP;
                midImg = mLayers[i]->mMidImg;
                m2mMpp->requestHWStateChange(MPP_HW_STATE_RUNNING);
                if ((ret = m2mMpp->doPostProcessing(midImg)) != NO_ERROR) {
                    HWC_LOGE(this, "%s:: doPostProcessing() failed, layer(%zu), ret(%d)",
                            __func__, i, ret);
                    errString.appendFormat("%s:: doPostProcessing() failed, layer(%zu), ret(%d)\n",
                            __func__, i, ret);
                    goto err;
                } else {
                    /* This should be closed by lib for each resource */
                    mLayers[i]->mAcquireFence = -1;
                }
            }
        }
    }

    if ((ret = setWinConfigData()) != NO_ERROR) {
        errString.appendFormat("setWinConfigData fail (%d)\n", ret);
        goto err;
    }

    if ((ret = handleStaticLayers(mClientCompositionInfo)) != NO_ERROR) {
        mClientCompositionInfo.mSkipStaticInitFlag = false;
        errString.appendFormat("handleStaticLayers error\n");
        goto err;
    }

    if (shouldSignalNonIdle()) {
        mPowerHalHint.signalNonIdle();
    }

    if (mRefreshRateIndicatorHandler && needUpdateRRIndicator()) {
        mRefreshRateIndicatorHandler->checkOnPresentDisplay();
    }

    handleWindowUpdate();

    setDisplayWinConfigData();

    if ((ret = deliverWinConfigData()) != NO_ERROR) {
        HWC_LOGE(this, "%s:: fail to deliver win_config (%d)", __func__, ret);
        if (mDpuData.retire_fence > 0)
            fence_close(mDpuData.retire_fence, this, FENCE_TYPE_RETIRE, FENCE_IP_DPP);
        mDpuData.retire_fence = -1;
    }

    setReleaseFences();

    if (mBufferDumpNum < mBufferDumpCount) {
        dumpAllBuffers();
    }

    if (mDpuData.retire_fence != -1) {
#ifdef DISABLE_FENCE
        if (mDpuData.retire_fence >= 0)
            fence_close(mDpuData.retire_fence, this, FENCE_TYPE_RETIRE, FENCE_IP_DPP);
        *outRetireFence = -1;
#else
        *outRetireFence =
            hwcCheckFenceDebug(this, FENCE_TYPE_RETIRE, FENCE_IP_DPP, mDpuData.retire_fence);
#endif
        setFenceInfo(mDpuData.retire_fence, this, FENCE_TYPE_RETIRE, FENCE_IP_LAYER,
                     HwcFenceDirection::TO);
    } else
        *outRetireFence = -1;

    /* Update last retire fence */
    mLastRetireFence = fence_close(mLastRetireFence, this, FENCE_TYPE_RETIRE, FENCE_IP_DPP);
    mLastRetireFence = hwc_dup((*outRetireFence), this, FENCE_TYPE_RETIRE, FENCE_IP_DPP, true);
    setFenceName(mLastRetireFence, FENCE_RETIRE);

    increaseMPPDstBufIndex();

    /* Check all of acquireFence are closed */
    for (size_t i=0; i < mLayers.size(); i++) {
        if (mLayers[i]->mAcquireFence != -1) {
            DISPLAY_LOGE("layer[%zu] fence(%d) type(%d, %d, %d) is not closed",
                    i, mLayers[i]->mAcquireFence,
                    mLayers[i]->mCompositionType,
                    mLayers[i]->mExynosCompositionType,
                    mLayers[i]->mValidateCompositionType);
            if (mLayers[i]->mM2mMPP != NULL)
                DISPLAY_LOGE("\t%s is assigned", mLayers[i]->mM2mMPP->mName.c_str());
            if (mLayers[i]->mAcquireFence > 0)
                fence_close(mLayers[i]->mAcquireFence, this,
                        FENCE_TYPE_SRC_ACQUIRE, FENCE_IP_LAYER);
            mLayers[i]->mAcquireFence = -1;
        }
    }
    if (mExynosCompositionInfo.mAcquireFence >= 0) {
        DISPLAY_LOGE("mExynosCompositionInfo mAcquireFence(%d) is not initialized", mExynosCompositionInfo.mAcquireFence);
        fence_close(mExynosCompositionInfo.mAcquireFence, this, FENCE_TYPE_SRC_ACQUIRE, FENCE_IP_G2D);
        mExynosCompositionInfo.mAcquireFence = -1;
    }
    if (mClientCompositionInfo.mAcquireFence >= 0) {
        DISPLAY_LOGE("mClientCompositionInfo mAcquireFence(%d) is not initialized", mClientCompositionInfo.mAcquireFence);
        fence_close(mClientCompositionInfo.mAcquireFence, this, FENCE_TYPE_SRC_ACQUIRE, FENCE_IP_FB);
        mClientCompositionInfo.mAcquireFence = -1;
    }

    /* All of release fences are tranferred */
    for (size_t i=0; i < mLayers.size(); i++) {
        setFenceInfo(mLayers[i]->mReleaseFence, this, FENCE_TYPE_SRC_ACQUIRE, FENCE_IP_LAYER,
                     HwcFenceDirection::TO);
    }

    doPostProcessing();

    if (!mDevice->validateFences(this)) {
        ALOGE("%s:: validate fence failed.", __func__);
    }

    mDpuData.reset();

    mRenderingState = RENDERING_STATE_PRESENTED;

    if (mConfigRequestState == hwc_request_state_t::SET_CONFIG_STATE_REQUESTED) {
        /* Do not update mVsyncPeriod */
        updateInternalDisplayConfigVariables(mDesiredConfig, false);
    }

    if (mUsePowerHints) {
        // update the "last present" now that we know for sure when this frame is due
        mLastExpectedPresentTime = mExpectedPresentTime;

        // we add an offset here to keep the flinger and HWC error terms roughly the same
        static const constexpr std::chrono::nanoseconds kFlingerOffset = 300us;
        nsecs_t now = systemTime() + kFlingerOffset.count();

        updateAverages(now);
        nsecs_t duration = now - mPresentStartTime;
        if (mRetireFenceWaitTime.has_value() && mRetireFenceAcquireTime.has_value()) {
            duration = now - *mRetireFenceAcquireTime + *mRetireFenceWaitTime - mPresentStartTime;
        }
        mPowerHalHint.signalActualWorkDuration(duration + mValidationDuration.value_or(0));
    }

    mPriorFrameMixedComposition = mixedComposition;

    tryUpdateBtsFromOperationRate(false);

    return ret;
err:
    printDebugInfos(errString);
    closeFences();
    *outRetireFence = -1;
    mLastRetireFence = -1;
    mRenderingState = RENDERING_STATE_PRESENTED;
    setGeometryChanged(GEOMETRY_ERROR_CASE);

    mLastDpuData.reset();

    mClientCompositionInfo.mSkipStaticInitFlag = false;
    mExynosCompositionInfo.mSkipStaticInitFlag = false;

    mDpuData.reset();

    if (!mDevice->validateFences(this)) {
        ALOGE("%s:: validate fence failed.", __func__);
    }
    mDisplayInterface->setForcePanic();

    ret = -EINVAL;
    return ret;

not_validated:
    DISPLAY_LOGD(eDebugSkipValidate, "display need validate");
    mRenderingState = RENDERING_STATE_NONE;
    ret = HWC2_ERROR_NOT_VALIDATED;
    return ret;
}

int32_t ExynosDisplay::presentPostProcessing()
{
    setReadbackBufferInternal(NULL, -1, false);
    if (mDpuData.enable_readback)
        mDevice->signalReadbackDone();
    mDpuData.enable_readback = false;

    for (auto it : mIgnoreLayers) {
        /*
         * Directly close without counting down
         * because it was not counted by validate
         */
        if (it->mAcquireFence > 0) {
            close(it->mAcquireFence);
        }
        it->mAcquireFence = -1;
    }
    return NO_ERROR;
}

int32_t ExynosDisplay::setActiveConfig(hwc2_config_t config)
{
    Mutex::Autolock lock(mDisplayMutex);
    DISPLAY_LOGD(eDebugDisplayConfig, "%s:: config(%d)", __func__, config);
    return setActiveConfigInternal(config, false);
}

int32_t ExynosDisplay::setActiveConfigInternal(hwc2_config_t config, bool force) {
    if (isBadConfig(config)) return HWC2_ERROR_BAD_CONFIG;

    if (!force && needNotChangeConfig(config)) {
        ALOGI("skip same config %d (force %d)", config, force);
        return HWC2_ERROR_NONE;
    }

    DISPLAY_LOGD(eDebugDisplayConfig, "(current %d) : %dx%d, %dms, %d Xdpi, %d Ydpi", mActiveConfig,
            mXres, mYres, mVsyncPeriod, mXdpi, mYdpi);
    DISPLAY_LOGD(eDebugDisplayConfig, "(requested %d) : %dx%d, %dms, %d Xdpi, %d Ydpi", config,
            mDisplayConfigs[config].width, mDisplayConfigs[config].height, mDisplayConfigs[config].vsyncPeriod,
            mDisplayConfigs[config].Xdpi, mDisplayConfigs[config].Ydpi);

    if (mDisplayInterface->setActiveConfig(config) < 0) {
        ALOGE("%s bad config request", __func__);
        return HWC2_ERROR_BAD_CONFIG;
    }

    if ((mXres != mDisplayConfigs[config].width) ||
        (mYres != mDisplayConfigs[config].height)) {
        mRenderingState = RENDERING_STATE_NONE;
        setGeometryChanged(GEOMETRY_DISPLAY_RESOLUTION_CHANGED);
    }

    updateInternalDisplayConfigVariables(config);
    return HWC2_ERROR_NONE;
}

int32_t ExynosDisplay::setClientTarget(
        buffer_handle_t target,
        int32_t acquireFence, int32_t /*android_dataspace_t*/ dataspace) {
    buffer_handle_t handle = NULL;
    if (target != NULL)
        handle = target;

#ifdef DISABLE_FENCE
    if (acquireFence >= 0)
        fence_close(acquireFence, this, FENCE_TYPE_SRC_ACQUIRE, FENCE_IP_FB);
    acquireFence = -1;
#endif
    acquireFence = hwcCheckFenceDebug(this, FENCE_TYPE_SRC_ACQUIRE, FENCE_IP_FB, acquireFence);
    if (handle == NULL) {
        DISPLAY_LOGD(eDebugOverlaySupported, "ClientTarget is NULL, skipStaic (%d)",
                mClientCompositionInfo.mSkipFlag);
        if (mClientCompositionInfo.mSkipFlag == false) {
            DISPLAY_LOGE("ClientTarget is NULL");
            DISPLAY_LOGE("\t%s:: mRenderingState(%d)",__func__, mRenderingState);
        }
    } else {
        VendorGraphicBufferMeta gmeta(handle);

        DISPLAY_LOGD(eDebugOverlaySupported, "ClientTarget handle: %p [fd: %d, %d, %d]",
                handle, gmeta.fd, gmeta.fd1, gmeta.fd2);
        if ((mClientCompositionInfo.mSkipFlag == true) &&
                ((mClientCompositionInfo.mLastWinConfigData.fd_idma[0] != gmeta.fd) ||
                 (mClientCompositionInfo.mLastWinConfigData.fd_idma[1] != gmeta.fd1) ||
                 (mClientCompositionInfo.mLastWinConfigData.fd_idma[2] != gmeta.fd2))) {
            String8 errString;
            DISPLAY_LOGE("skip flag is enabled but buffer is updated lastConfig[%d, %d, %d], handle[%d, %d, %d]\n",
                    mClientCompositionInfo.mLastWinConfigData.fd_idma[0],
                    mClientCompositionInfo.mLastWinConfigData.fd_idma[1],
                    mClientCompositionInfo.mLastWinConfigData.fd_idma[2],
                    gmeta.fd, gmeta.fd1, gmeta.fd2);
            DISPLAY_LOGE("last win config");
            for (size_t i = 0; i < mLastDpuData.configs.size(); i++) {
                errString.appendFormat("config[%zu]\n", i);
                dumpConfig(errString, mLastDpuData.configs[i]);
                DISPLAY_LOGE("\t%s", errString.c_str());
                errString.clear();
            }
            errString.appendFormat("%s:: skip flag is enabled but buffer is updated\n",
                    __func__);
            printDebugInfos(errString);
        }
    }
    mClientCompositionInfo.setTargetBuffer(this, handle, acquireFence, (android_dataspace)dataspace);
    setFenceInfo(acquireFence, this, FENCE_TYPE_SRC_RELEASE, FENCE_IP_FB, HwcFenceDirection::FROM);

    if (handle) {
        mClientCompositionInfo.mCompressionInfo = getCompressionInfo(handle);
        mExynosCompositionInfo.mCompressionInfo = getCompressionInfo(handle);
    }

    return 0;
}

int32_t ExynosDisplay::setColorTransform(
        const float* matrix,
        int32_t /*android_color_transform_t*/ hint) {
    if ((hint < HAL_COLOR_TRANSFORM_IDENTITY) ||
        (hint > HAL_COLOR_TRANSFORM_CORRECT_TRITANOPIA))
        return HWC2_ERROR_BAD_PARAMETER;
    ALOGI("%s:: %d, %d", __func__, mColorTransformHint, hint);
    if (mColorTransformHint != hint)
        setGeometryChanged(GEOMETRY_DISPLAY_COLOR_TRANSFORM_CHANGED);
    mColorTransformHint = hint;
#ifdef HWC_SUPPORT_COLOR_TRANSFORM
    int ret = mDisplayInterface->setColorTransform(matrix, hint);
    if (ret < 0)
        mColorTransformHint = ret;
    return ret;
#else
    return HWC2_ERROR_NONE;
#endif
}

int32_t ExynosDisplay::setColorMode(int32_t /*android_color_mode_t*/ mode)
{
    if (mDisplayInterface->setColorMode(mode) < 0) {
        if (mode == HAL_COLOR_MODE_NATIVE)
            return HWC2_ERROR_NONE;

        ALOGE("%s:: is not supported", __func__);
        return HWC2_ERROR_UNSUPPORTED;
    }

    ALOGI("%s:: %d, %d", __func__, mColorMode, mode);
    if (mColorMode != mode)
        setGeometryChanged(GEOMETRY_DISPLAY_COLOR_MODE_CHANGED);
    mColorMode = (android_color_mode_t)mode;
    return HWC2_ERROR_NONE;
}

int32_t ExynosDisplay::getRenderIntents(int32_t mode, uint32_t* outNumIntents,
        int32_t* /*android_render_intent_v1_1_t*/ outIntents)
{
    ALOGI("%s:: mode(%d), outNum(%d), outIntents(%p)",
            __func__, mode, *outNumIntents, outIntents);

    return mDisplayInterface->getRenderIntents(mode, outNumIntents, outIntents);
}

int32_t ExynosDisplay::setColorModeWithRenderIntent(int32_t /*android_color_mode_t*/ mode,
        int32_t /*android_render_intent_v1_1_t */ intent)
{
    ALOGI("%s:: mode(%d), intent(%d)", __func__, mode, intent);

    return mDisplayInterface->setColorModeWithRenderIntent(mode, intent);
}

int32_t ExynosDisplay::getDisplayIdentificationData(uint8_t* outPort,
        uint32_t* outDataSize, uint8_t* outData)
{
    return mDisplayInterface->getDisplayIdentificationData(outPort, outDataSize, outData);
}

int32_t ExynosDisplay::getDisplayCapabilities(uint32_t* outNumCapabilities,
        uint32_t* outCapabilities)
{
    /* If each display has their own capabilities,
     * this should be described in display module codes */

    uint32_t capabilityNum = 0;
    bool isBrightnessSupported = false;
    int32_t isDozeSupported = 0;

    auto ret = getDisplayBrightnessSupport(&isBrightnessSupported);
    if (ret != HWC2_ERROR_NONE) {
        ALOGE("%s: failed to getDisplayBrightnessSupport: %d", __func__, ret);
        return ret;
    }
    if (isBrightnessSupported) {
        capabilityNum++;
    }

    ret = getDozeSupport(&isDozeSupported);
    if (ret != HWC2_ERROR_NONE) {
        ALOGE("%s: failed to getDozeSupport: %d", __func__, ret);
        return ret;
    }
    if (isDozeSupported) {
        capabilityNum++;
    }

#ifdef HWC_SUPPORT_COLOR_TRANSFORM
    capabilityNum++;
#endif

    if (outCapabilities == NULL) {
        *outNumCapabilities = capabilityNum;
        return HWC2_ERROR_NONE;
    }
    if (capabilityNum != *outNumCapabilities) {
        ALOGE("%s:: invalid outNumCapabilities(%d), should be(%d)", __func__, *outNumCapabilities, capabilityNum);
        return HWC2_ERROR_BAD_PARAMETER;
    }

    uint32_t index = 0;
    if (isBrightnessSupported) {
        outCapabilities[index++] = HWC2_DISPLAY_CAPABILITY_BRIGHTNESS;
    }

    if (isDozeSupported) {
        outCapabilities[index++] = HWC2_DISPLAY_CAPABILITY_DOZE;
    }

#ifdef HWC_SUPPORT_COLOR_TRANSFORM
    outCapabilities[index++] = HWC2_DISPLAY_CAPABILITY_SKIP_CLIENT_COLOR_TRANSFORM;
#endif

    return HWC2_ERROR_NONE;
}

int32_t ExynosDisplay::getDisplayBrightnessSupport(bool* outSupport)
{
    if (!mBrightnessController || !mBrightnessController->isSupported()) {
        *outSupport = false;
    } else {
        *outSupport = true;
    }

    return HWC2_ERROR_NONE;
}

void ExynosDisplay::handleTargetOperationRate() {
    int32_t targetOpRate = mOperationRateManager->getTargetOperationRate();
    if (targetOpRate == mBrightnessController->getOperationRate()) return;

    mDevice->onRefresh(mDisplayId);
    mBrightnessController->processOperationRate(targetOpRate);
}

int32_t ExynosDisplay::setDisplayBrightness(float brightness, bool waitPresent)
{
    if (mBrightnessController) {
        int32_t ret;

        ret = mBrightnessController->processDisplayBrightness(brightness, mVsyncPeriod,
                                                              waitPresent);
        if (ret == NO_ERROR) {
            setMinIdleRefreshRate(0, RrThrottleRequester::BRIGHTNESS);
            if (mOperationRateManager) {
                mOperationRateManager->onBrightness(mBrightnessController->getBrightnessLevel());
                handleTargetOperationRate();
            }
        }
        return ret;
    }

    return HWC2_ERROR_UNSUPPORTED;
}

int32_t ExynosDisplay::ignoreBrightnessUpdateRequests(bool ignore) {
    if (mBrightnessController)
        return mBrightnessController->ignoreBrightnessUpdateRequests(ignore);

    return HWC2_ERROR_UNSUPPORTED;
}

int32_t ExynosDisplay::setBrightnessNits(const float nits)
{
    if (mBrightnessController) {
        int32_t ret = mBrightnessController->setBrightnessNits(nits, mVsyncPeriod);

        if (ret == NO_ERROR) {
            setMinIdleRefreshRate(0, RrThrottleRequester::BRIGHTNESS);
            if (mOperationRateManager)
                mOperationRateManager->onBrightness(mBrightnessController->getBrightnessLevel());
        }

        return ret;
    }

    return HWC2_ERROR_UNSUPPORTED;
}

int32_t ExynosDisplay::setBrightnessDbv(const uint32_t dbv) {
    if (mBrightnessController) {
        int32_t ret = mBrightnessController->setBrightnessDbv(dbv, mVsyncPeriod);

        if (ret == NO_ERROR) {
            setMinIdleRefreshRate(0, RrThrottleRequester::BRIGHTNESS);
            if (mOperationRateManager) {
                mOperationRateManager->onBrightness(mBrightnessController->getBrightnessLevel());
            }
        }

        return ret;
    }

    return HWC2_ERROR_UNSUPPORTED;
}

int32_t ExynosDisplay::getDisplayConnectionType(uint32_t* outType)
{
    if (mType == HWC_DISPLAY_PRIMARY)
        *outType = HWC2_DISPLAY_CONNECTION_TYPE_INTERNAL;
    else if (mType == HWC_DISPLAY_EXTERNAL)
        *outType = HWC2_DISPLAY_CONNECTION_TYPE_EXTERNAL;
    else
        return HWC2_ERROR_BAD_DISPLAY;

    return HWC2_ERROR_NONE;
}

int32_t ExynosDisplay::getDisplayVsyncPeriod(hwc2_vsync_period_t* __unused outVsyncPeriod)
{
    Mutex::Autolock lock(mDisplayMutex);
    return getDisplayVsyncPeriodInternal(outVsyncPeriod);
}

int32_t ExynosDisplay::getConfigAppliedTime(const uint64_t desiredTime,
        const uint64_t actualChangeTime,
        int64_t &appliedTime, int64_t &refreshTime)
{
    uint32_t transientDuration = mDisplayInterface->getConfigChangeDuration();
    appliedTime = actualChangeTime;

    if (desiredTime > appliedTime) {
        const int64_t originalAppliedTime = appliedTime;
        const int64_t diff = desiredTime - appliedTime;
        appliedTime += (diff + mVsyncPeriod - 1) / mVsyncPeriod * mVsyncPeriod;
        DISPLAY_LOGD(eDebugDisplayConfig,
                     "desired time(%" PRId64 "), applied time(%" PRId64 "->%" PRId64 ")",
                     desiredTime, originalAppliedTime, appliedTime);
    } else {
        DISPLAY_LOGD(eDebugDisplayConfig, "desired time(%" PRId64 "), applied time(%" PRId64 ")",
                     desiredTime, appliedTime);
    }

    refreshTime = appliedTime - (transientDuration * mVsyncPeriod);

    return NO_ERROR;
}

void ExynosDisplay::calculateTimelineLocked(
        hwc2_config_t config, hwc_vsync_period_change_constraints_t* vsyncPeriodChangeConstraints,
        hwc_vsync_period_change_timeline_t* outTimeline) {
    int64_t actualChangeTime = 0;
    /* actualChangeTime includes transient duration */
    mDisplayInterface->getVsyncAppliedTime(config, &actualChangeTime);

    outTimeline->refreshRequired = true;
    getConfigAppliedTime(mVsyncPeriodChangeConstraints.desiredTimeNanos, actualChangeTime,
                         outTimeline->newVsyncAppliedTimeNanos, outTimeline->refreshTimeNanos);

    DISPLAY_LOGD(eDebugDisplayConfig,
                 "requested config : %d(%d)->%d(%d), "
                 "desired %" PRId64 ", newVsyncAppliedTimeNanos : %" PRId64 "",
                 mActiveConfig, mDisplayConfigs[mActiveConfig].vsyncPeriod, config,
                 mDisplayConfigs[config].vsyncPeriod,
                 mVsyncPeriodChangeConstraints.desiredTimeNanos,
                 outTimeline->newVsyncAppliedTimeNanos);
}

int32_t ExynosDisplay::setActiveConfigWithConstraints(hwc2_config_t config,
        hwc_vsync_period_change_constraints_t* vsyncPeriodChangeConstraints,
        hwc_vsync_period_change_timeline_t* outTimeline)
{
    DISPLAY_ATRACE_CALL();
    Mutex::Autolock lock(mDisplayMutex);
    const nsecs_t current = systemTime(SYSTEM_TIME_MONOTONIC);
    const nsecs_t diffMs = ns2ms(vsyncPeriodChangeConstraints->desiredTimeNanos - current);
    DISPLAY_LOGD(eDebugDisplayConfig, "config(%d->%d), seamless(%d), diff(%" PRId64 ")",
                 mActiveConfig, config, vsyncPeriodChangeConstraints->seamlessRequired, diffMs);

    if (CC_UNLIKELY(ATRACE_ENABLED())) ATRACE_NAME(("diff:" + std::to_string(diffMs)).c_str());

    if (isBadConfig(config)) return HWC2_ERROR_BAD_CONFIG;

    if (!isConfigSettingEnabled()) {
        mPendingConfig = config;
        DISPLAY_LOGI("%s: config setting disabled, set pending config=%d", __func__, config);
        return HWC2_ERROR_NONE;
    }

    if (mDisplayConfigs[mActiveConfig].groupId != mDisplayConfigs[config].groupId) {
        if (vsyncPeriodChangeConstraints->seamlessRequired) {
            DISPLAY_LOGD(eDebugDisplayConfig, "Case : Seamless is not allowed");
            return HWC2_ERROR_SEAMLESS_NOT_ALLOWED;
        }

        outTimeline->newVsyncAppliedTimeNanos = vsyncPeriodChangeConstraints->desiredTimeNanos;
        outTimeline->refreshRequired = true;
    }

    if (needNotChangeConfig(config)) {
        outTimeline->refreshRequired = false;
        outTimeline->newVsyncAppliedTimeNanos = vsyncPeriodChangeConstraints->desiredTimeNanos;
        return HWC2_ERROR_NONE;
    }

    if ((mXres != mDisplayConfigs[config].width) || (mYres != mDisplayConfigs[config].height)) {
        if ((mDisplayInterface->setActiveConfigWithConstraints(config, true)) != NO_ERROR) {
            ALOGW("Mode change not possible");
            return HWC2_ERROR_BAD_CONFIG;
        }
        mRenderingState = RENDERING_STATE_NONE;
        setGeometryChanged(GEOMETRY_DISPLAY_RESOLUTION_CHANGED);
        updateInternalDisplayConfigVariables(config, false);
    } else if (vsyncPeriodChangeConstraints->seamlessRequired) {
        if ((mDisplayInterface->setActiveConfigWithConstraints(config, true)) != NO_ERROR) {
            DISPLAY_LOGD(eDebugDisplayConfig, "Case : Seamless is not possible");
            return HWC2_ERROR_SEAMLESS_NOT_POSSIBLE;
        }
    }

    DISPLAY_LOGD(eDebugDisplayConfig, "%s : %dx%d, %dms, %d Xdpi, %d Ydpi", __func__,
            mXres, mYres, mVsyncPeriod, mXdpi, mYdpi);

    if (mConfigRequestState == hwc_request_state_t::SET_CONFIG_STATE_REQUESTED) {
        DISPLAY_LOGI("%s, previous request config is processing (desird %d, new request %d)",
                     __func__, mDesiredConfig, config);
    }
    /* Config would be requested on present time */
    mConfigRequestState = hwc_request_state_t::SET_CONFIG_STATE_PENDING;
    mVsyncPeriodChangeConstraints = *vsyncPeriodChangeConstraints;
    mDesiredConfig = config;
    DISPLAY_ATRACE_INT("Pending ActiveConfig", mDesiredConfig);

    calculateTimelineLocked(config, vsyncPeriodChangeConstraints, outTimeline);

    /* mActiveConfig should be changed immediately for internal status */
    mActiveConfig = config;
    mVsyncAppliedTimeLine = *outTimeline;
    updateBtsFrameScanoutPeriod(getDisplayFrameScanoutPeriodFromConfig(config));

    bool earlyWakeupNeeded = checkRrCompensationEnabled();
    if (earlyWakeupNeeded) {
        setEarlyWakeupDisplay();
    }
    if (mRefreshRateIndicatorHandler) {
        mRefreshRateIndicatorHandler->checkOnSetActiveConfig(mDisplayConfigs[config].refreshRate);
    }

    return HWC2_ERROR_NONE;
}

int32_t ExynosDisplay::setBootDisplayConfig(int32_t config) {
    return HWC2_ERROR_UNSUPPORTED;
}

int32_t ExynosDisplay::clearBootDisplayConfig() {
    return HWC2_ERROR_UNSUPPORTED;
}

int32_t ExynosDisplay::getPreferredBootDisplayConfig(int32_t *outConfig) {
    return getPreferredDisplayConfigInternal(outConfig);
}

int32_t ExynosDisplay::getPreferredDisplayConfigInternal(int32_t *outConfig) {
    return HWC2_ERROR_UNSUPPORTED;
}

int32_t ExynosDisplay::setAutoLowLatencyMode(bool __unused on)
{
    return HWC2_ERROR_UNSUPPORTED;
}

int32_t ExynosDisplay::getSupportedContentTypes(uint32_t* __unused outNumSupportedContentTypes,
        uint32_t* __unused outSupportedContentTypes)
{
    if (outSupportedContentTypes == NULL)
        outNumSupportedContentTypes = 0;
    return HWC2_ERROR_NONE;
}

int32_t ExynosDisplay::setContentType(int32_t /* hwc2_content_type_t */ contentType)
{
    if (contentType == HWC2_CONTENT_TYPE_NONE)
        return HWC2_ERROR_NONE;

    return HWC2_ERROR_UNSUPPORTED;
}

int32_t ExynosDisplay::getClientTargetProperty(
                        hwc_client_target_property_t *outClientTargetProperty,
                        HwcDimmingStage *outDimmingStage) {
    outClientTargetProperty->pixelFormat = HAL_PIXEL_FORMAT_RGBA_8888;
    outClientTargetProperty->dataspace = HAL_DATASPACE_UNKNOWN;
    if (outDimmingStage != nullptr)
        *outDimmingStage = HwcDimmingStage::DIMMING_NONE;

    return HWC2_ERROR_NONE;
}

bool ExynosDisplay::isBadConfig(hwc2_config_t config)
{
    /* Check invalid config */
    const auto its = mDisplayConfigs.find(config);
    if (its == mDisplayConfigs.end()) {
        DISPLAY_LOGE("%s, invalid config : %d", __func__, config);
        return true;
    }

    return false;
}

bool ExynosDisplay::needNotChangeConfig(hwc2_config_t config)
{
    /* getting current config and compare */
    /* If same value, return */
    if (mActiveConfig == config) {
        DISPLAY_LOGI("%s, Same config change requested : %d", __func__, config);
        return true;
    }

    return false;
}

int32_t ExynosDisplay::updateInternalDisplayConfigVariables(
        hwc2_config_t config, bool updateVsync)
{
    mActiveConfig = config;

    /* Update internal variables */
    getDisplayAttribute(mActiveConfig, HWC2_ATTRIBUTE_WIDTH, (int32_t*)&mXres);
    getDisplayAttribute(mActiveConfig, HWC2_ATTRIBUTE_HEIGHT, (int32_t*)&mYres);
    getDisplayAttribute(mActiveConfig, HWC2_ATTRIBUTE_DPI_X, (int32_t*)&mXdpi);
    getDisplayAttribute(mActiveConfig, HWC2_ATTRIBUTE_DPI_Y, (int32_t*)&mYdpi);
    mHdrFullScrenAreaThreshold = mXres * mYres * kHdrFullScreen;
    if (updateVsync) {
        resetConfigRequestStateLocked(config);
    }
    if (mRefreshRateIndicatorHandler) {
        mRefreshRateIndicatorHandler->checkOnSetActiveConfig(mDisplayConfigs[config].refreshRate);
    }

    return NO_ERROR;
}

void ExynosDisplay::updateBtsFrameScanoutPeriod(int32_t frameScanoutPeriod, bool configApplied) {
    if (mBtsFrameScanoutPeriod == frameScanoutPeriod) {
        return;
    }

    if (configApplied || frameScanoutPeriod < mBtsFrameScanoutPeriod) {
        checkBtsReassignResource(frameScanoutPeriod, mBtsFrameScanoutPeriod);
        mBtsFrameScanoutPeriod = frameScanoutPeriod;
        ATRACE_INT("btsFrameScanoutPeriod", mBtsFrameScanoutPeriod);
    }
}

void ExynosDisplay::tryUpdateBtsFromOperationRate(bool beforeValidateDisplay) {
    if (mOperationRateManager == nullptr || mBrightnessController == nullptr ||
        mActiveConfig == UINT_MAX) {
        return;
    }

    if (!mDisplayConfigs[mActiveConfig].isOperationRateToBts) {
        return;
    }

    if (beforeValidateDisplay && mBrightnessController->isOperationRatePending()) {
        uint32_t opRate = mBrightnessController->getOperationRate();
        if (opRate) {
            int32_t operationRatePeriod = nsecsPerSec / opRate;
            if (operationRatePeriod < mBtsFrameScanoutPeriod) {
                updateBtsFrameScanoutPeriod(opRate);
                mBtsPendingOperationRatePeriod = 0;
            } else if (operationRatePeriod != mBtsFrameScanoutPeriod) {
                mBtsPendingOperationRatePeriod = operationRatePeriod;
            }
        }
    }

    if (!beforeValidateDisplay && mBtsPendingOperationRatePeriod &&
        !mBrightnessController->isOperationRatePending()) {
        /* Do not update during rr transition, it will be updated after setting config done */
        if (mConfigRequestState != hwc_request_state_t::SET_CONFIG_STATE_REQUESTED) {
            updateBtsFrameScanoutPeriod(mBtsPendingOperationRatePeriod, true);
        }
        mBtsPendingOperationRatePeriod = 0;
    }
}

inline int32_t ExynosDisplay::getDisplayFrameScanoutPeriodFromConfig(hwc2_config_t config) {
    int32_t frameScanoutPeriodNs;
    auto vrrConfig = getVrrConfigs(config);
    if (vrrConfig.has_value() && vrrConfig->isFullySupported) {
        frameScanoutPeriodNs = vrrConfig->minFrameIntervalNs;
    } else {
        getDisplayAttribute(config, HWC2_ATTRIBUTE_VSYNC_PERIOD, &frameScanoutPeriodNs);
        if (mOperationRateManager && mBrightnessController &&
            mDisplayConfigs[config].isOperationRateToBts) {
            uint32_t opRate = mBrightnessController->getOperationRate();
            if (opRate) {
                uint32_t opPeriodNs = nsecsPerSec / opRate;
                frameScanoutPeriodNs =
                        (frameScanoutPeriodNs <= opPeriodNs) ? frameScanoutPeriodNs : opPeriodNs;
            }
        }
    }

    assert(frameScanoutPeriodNs > 0);
    return frameScanoutPeriodNs;
}

uint32_t ExynosDisplay::getBtsRefreshRate() const {
    return static_cast<uint32_t>(round(nsecsPerSec / mBtsFrameScanoutPeriod * 0.1f) * 10);
}

void ExynosDisplay::updateRefreshRateHint() {
    if (mRefreshRate) {
        mPowerHalHint.signalRefreshRate(mPowerModeState.value_or(HWC2_POWER_MODE_OFF),
                                        mRefreshRate);
    }
}

/* This function must be called within a mDisplayMutex protection */
int32_t ExynosDisplay::resetConfigRequestStateLocked(hwc2_config_t config) {
    ATRACE_CALL();

    assert(isBadConfig(config) == false);

    mRefreshRate = mDisplayConfigs[config].refreshRate;
    mVsyncPeriod = getDisplayVsyncPeriodFromConfig(config);
    updateBtsFrameScanoutPeriod(getDisplayFrameScanoutPeriodFromConfig(config), true);
    DISPLAY_LOGD(eDebugDisplayConfig, "Update mVsyncPeriod %d by config(%d)", mVsyncPeriod, config);

    updateRefreshRateHint();

    if (mConfigRequestState != hwc_request_state_t::SET_CONFIG_STATE_REQUESTED) {
        DISPLAY_LOGI("%s: mConfigRequestState (%d) is not REQUESTED", __func__,
                     mConfigRequestState);
    } else {
        DISPLAY_LOGD(eDebugDisplayInterfaceConfig, "%s: Change mConfigRequestState (%d) to DONE",
                     __func__, mConfigRequestState);
        mConfigRequestState = hwc_request_state_t::SET_CONFIG_STATE_DONE;
        updateAppliedActiveConfig(mActiveConfig, systemTime(SYSTEM_TIME_MONOTONIC));
    }
    return NO_ERROR;
}

int32_t ExynosDisplay::updateConfigRequestAppliedTime()
{
    if (mConfigRequestState != hwc_request_state_t::SET_CONFIG_STATE_REQUESTED) {
        DISPLAY_LOGI("%s: mConfigRequestState (%d) is not REQUESTED", __func__,
                     mConfigRequestState);
        return NO_ERROR;
    }

    /*
     * config change was requested but
     * it is not applied until newVsyncAppliedTimeNanos
     * Update time information
     */
    int64_t actualChangeTime = 0;
    mDisplayInterface->getVsyncAppliedTime(mDesiredConfig, &actualChangeTime);
    return updateVsyncAppliedTimeLine(actualChangeTime);
}

int32_t ExynosDisplay::updateVsyncAppliedTimeLine(int64_t actualChangeTime)
{
    ExynosDevice *dev = mDevice;

    DISPLAY_LOGD(eDebugDisplayConfig,"Vsync applied time is changed (%" PRId64 "-> %" PRId64 ")",
            mVsyncAppliedTimeLine.newVsyncAppliedTimeNanos,
            actualChangeTime);
    getConfigAppliedTime(mVsyncPeriodChangeConstraints.desiredTimeNanos,
            actualChangeTime,
            mVsyncAppliedTimeLine.newVsyncAppliedTimeNanos,
            mVsyncAppliedTimeLine.refreshTimeNanos);
    if (mConfigRequestState ==
            hwc_request_state_t::SET_CONFIG_STATE_REQUESTED) {
        mVsyncAppliedTimeLine.refreshRequired = false;
    } else {
        mVsyncAppliedTimeLine.refreshRequired = true;
    }

    DISPLAY_LOGD(eDebugDisplayConfig,"refresh required(%d), newVsyncAppliedTimeNanos (%" PRId64 ")",
            mVsyncAppliedTimeLine.refreshRequired,
            mVsyncAppliedTimeLine.newVsyncAppliedTimeNanos);

    dev->onVsyncPeriodTimingChanged(getId(), &mVsyncAppliedTimeLine);

    return NO_ERROR;
}

int32_t ExynosDisplay::getDisplayVsyncPeriodInternal(hwc2_vsync_period_t* outVsyncPeriod)
{
    /* Getting actual config from DPU */
    if (mDisplayInterface->getDisplayVsyncPeriod(outVsyncPeriod) == HWC2_ERROR_NONE) {
        DISPLAY_LOGD(eDebugDisplayInterfaceConfig, "period : %ld",
                (long)*outVsyncPeriod);
    } else {
        *outVsyncPeriod = mVsyncPeriod;
        DISPLAY_LOGD(eDebugDisplayInterfaceConfig, "period is mVsyncPeriod: %d",
                mVsyncPeriod);
    }
    return HWC2_ERROR_NONE;
}

int32_t ExynosDisplay::doDisplayConfigInternal(hwc2_config_t config) {
    return mDisplayInterface->setActiveConfigWithConstraints(config);
}

int32_t ExynosDisplay::doDisplayConfigPostProcess(ExynosDevice *dev)
{
    ATRACE_CALL();
    uint64_t current = systemTime(SYSTEM_TIME_MONOTONIC);

    int64_t actualChangeTime = 0;
    mDisplayInterface->getVsyncAppliedTime(mDesiredConfig, &actualChangeTime);
    bool needSetActiveConfig = false;

    DISPLAY_LOGD(eDebugDisplayConfig,
            "Check time for setActiveConfig (curr: %" PRId64
            ", actualChangeTime: %" PRId64 ", desiredTime: %" PRId64 "",
            current, actualChangeTime,
            mVsyncPeriodChangeConstraints.desiredTimeNanos);
    if (actualChangeTime >= mVsyncPeriodChangeConstraints.desiredTimeNanos) {
        DISPLAY_LOGD(eDebugDisplayConfig, "Request setActiveConfig %d", mDesiredConfig);
        needSetActiveConfig = true;
        DISPLAY_ATRACE_INT("Pending ActiveConfig", 0);
        DISPLAY_ATRACE_INT64("TimeToApplyConfig", 0);
    } else {
        DISPLAY_LOGD(eDebugDisplayConfig, "setActiveConfig still pending (mDesiredConfig %d)",
                     mDesiredConfig);
        DISPLAY_ATRACE_INT("Pending ActiveConfig", mDesiredConfig);
        DISPLAY_ATRACE_INT64("TimeToApplyConfig",
                             ns2ms(mVsyncPeriodChangeConstraints.desiredTimeNanos - current));
    }

    if (needSetActiveConfig) {
        int32_t ret = NO_ERROR;
        if ((ret = doDisplayConfigInternal(mDesiredConfig)) != NO_ERROR) {
            return ret;
        }

        mConfigRequestState = hwc_request_state_t::SET_CONFIG_STATE_REQUESTED;
    }

    return updateVsyncAppliedTimeLine(actualChangeTime);
}

int32_t ExynosDisplay::setOutputBuffer( buffer_handle_t __unused buffer, int32_t __unused releaseFence)
{
    return HWC2_ERROR_NONE;
}

int ExynosDisplay::clearDisplay(bool needModeClear) {

    const int ret = mDisplayInterface->clearDisplay(needModeClear);
    if (ret)
        DISPLAY_LOGE("fail to clear display");

    mClientCompositionInfo.mSkipStaticInitFlag = false;
    mClientCompositionInfo.mSkipFlag = false;

    mLastDpuData.reset();

    /* Update last retire fence */
    mLastRetireFence = fence_close(mLastRetireFence, this, FENCE_TYPE_RETIRE, FENCE_IP_DPP);

    if (mBrightnessController) {
        mBrightnessController->onClearDisplay(needModeClear);
    }
    return ret;
}

int32_t ExynosDisplay::setPowerMode(
        int32_t /*hwc2_power_mode_t*/ mode) {
    Mutex::Autolock lock(mDisplayMutex);

    if (!mDisplayInterface->isDozeModeAvailable() &&
        (mode == HWC2_POWER_MODE_DOZE || mode == HWC2_POWER_MODE_DOZE_SUSPEND)) {
        return HWC2_ERROR_UNSUPPORTED;
    }

    if (mode == HWC_POWER_MODE_OFF) {
        mDevice->mPrimaryBlank = true;
        clearDisplay(true);
        ALOGV("HWC2: Clear display (power off)");
    } else {
        mDevice->mPrimaryBlank = false;
    }

    if (mode == HWC_POWER_MODE_OFF)
        mDREnable = false;
    else
        mDREnable = mDRDefault;

    // check the dynamic recomposition thread by following display power status;
    mDevice->checkDynamicRecompositionThread();


    /* TODO: Call display interface */
    mDisplayInterface->setPowerMode(mode);

    ALOGD("%s:: mode(%d))", __func__, mode);

    mPowerModeState = (hwc2_power_mode_t)mode;

    if (mode == HWC_POWER_MODE_OFF) {
        /* It should be called from validate() when the screen is on */
        mSkipFrame = true;
        setGeometryChanged(GEOMETRY_DISPLAY_POWER_OFF);
        if ((mRenderingState >= RENDERING_STATE_VALIDATED) &&
            (mRenderingState < RENDERING_STATE_PRESENTED))
            closeFencesForSkipFrame(RENDERING_STATE_VALIDATED);
        mRenderingState = RENDERING_STATE_NONE;
    } else {
        setGeometryChanged(GEOMETRY_DISPLAY_POWER_ON);
    }

    return HWC2_ERROR_NONE;
}

int32_t ExynosDisplay::setVsyncEnabled(
        int32_t /*hwc2_vsync_t*/ enabled) {
    Mutex::Autolock lock(mDisplayMutex);
    return setVsyncEnabledInternal(enabled);
}

int32_t ExynosDisplay::setVsyncEnabledInternal(
        int32_t enabled) {

    uint32_t val = 0;

    if (enabled < 0 || enabled > HWC2_VSYNC_DISABLE)
        return HWC2_ERROR_BAD_PARAMETER;


    if (enabled == HWC2_VSYNC_ENABLE) {
        gettimeofday(&updateTimeInfo.lastEnableVsyncTime, NULL);
        val = 1;
        if (mVsyncState != HWC2_VSYNC_ENABLE) {
            /* TODO: remove it once driver can handle on its own */
            setEarlyWakeupDisplay();
        }
    } else {
        gettimeofday(&updateTimeInfo.lastDisableVsyncTime, NULL);
    }

    if (mDisplayInterface->setVsyncEnabled(val) < 0) {
        HWC_LOGE(this, "vsync ioctl failed errno : %d", errno);
        return HWC2_ERROR_BAD_DISPLAY;
    }

    mVsyncState = (hwc2_vsync_t)enabled;

    return HWC2_ERROR_NONE;
}

int32_t ExynosDisplay::validateDisplay(
        uint32_t* outNumTypes, uint32_t* outNumRequests) {
    DISPLAY_ATRACE_CALL();
    gettimeofday(&updateTimeInfo.lastValidateTime, NULL);
    Mutex::Autolock lock(mDisplayMutex);

    if (!mHpdStatus) {
        ALOGD("validateDisplay: drop frame: mHpdStatus == false");
        return HWC2_ERROR_NONE;
    }

    if (mPauseDisplay) return HWC2_ERROR_NONE;

    mDropFrameDuringResSwitch =
            (mGeometryChanged & GEOMETRY_DISPLAY_RESOLUTION_CHANGED) && !isFullScreenComposition();
    if (mDropFrameDuringResSwitch) {
        ALOGD("validateDisplay: drop invalid frame during resolution switch");
        *outNumTypes = 0;
        *outNumRequests = 0;
        return HWC2_ERROR_NONE;
    }

    int ret = NO_ERROR;
    bool validateError = false;
    mUpdateEventCnt++;
    mUpdateCallCnt++;
    mLastUpdateTimeStamp = systemTime(SYSTEM_TIME_MONOTONIC);

    if (usePowerHintSession()) {
        mValidateStartTime = mLastUpdateTimeStamp;
        mExpectedPresentTime = getExpectedPresentTime(*mValidateStartTime);
        auto target =
                min(mExpectedPresentTime - *mValidateStartTime, static_cast<nsecs_t>(mVsyncPeriod));
        mPowerHalHint.signalTargetWorkDuration(target);
        std::optional<nsecs_t> predictedDuration = getPredictedDuration(true);
        if (predictedDuration.has_value()) {
            mPowerHalHint.signalActualWorkDuration(*predictedDuration);
        }
    }

    checkIgnoreLayers();
    if (mLayers.size() == 0)
        DISPLAY_LOGI("%s:: validateDisplay layer size is 0", __func__);
    else
        mLayers.vector_sort();

    for (size_t i = 0; i < mLayers.size(); i++) mLayers[i]->setSrcAcquireFence();

    tryUpdateBtsFromOperationRate(true);
    doPreProcessing();
    checkLayerFps();
    if (exynosHWCControl.useDynamicRecomp == true && mDREnable) {
        checkDynamicReCompMode();
        if (mDevice->isDynamicRecompositionThreadAlive() == false &&
            mDevice->mDRLoopStatus == false)
            mDevice->dynamicRecompositionThreadCreate();
    }

    if ((ret = mResourceManager->assignResource(this)) != NO_ERROR) {
        validateError = true;
        HWC_LOGE(this, "%s:: assignResource() fail, display(%d), ret(%d)", __func__, mDisplayId, ret);
        String8 errString;
        errString.appendFormat("%s:: assignResource() fail, display(%d), ret(%d)\n",
                __func__, mDisplayId, ret);
        printDebugInfos(errString);
        mDisplayInterface->setForcePanic();
    }

    if ((ret = skipStaticLayers(mClientCompositionInfo)) != NO_ERROR) {
        validateError = true;
        HWC_LOGE(this, "%s:: skipStaticLayers() fail, display(%d), ret(%d)", __func__, mDisplayId, ret);
    } else {
        if ((mClientCompositionInfo.mHasCompositionLayer) &&
            (mClientCompositionInfo.mSkipFlag == false)) {
            /* Initialize compositionType */
            for (size_t i = (size_t)mClientCompositionInfo.mFirstIndex; i <= (size_t)mClientCompositionInfo.mLastIndex; i++) {
                if (mLayers[i]->mOverlayPriority >= ePriorityHigh)
                    continue;
                mLayers[i]->mValidateCompositionType = HWC2_COMPOSITION_CLIENT;
            }
        }
    }

    mRenderingState = RENDERING_STATE_VALIDATED;

    /*
     * HWC should update performanceInfo even if assignResource is skipped
     * HWC excludes the layer from performance calculation
     * if there is no buffer update. (using ExynosMPP::canSkipProcessing())
     * Therefore performanceInfo should be calculated again if only the buffer is updated.
     */
    if ((ret = mDevice->mResourceManager->deliverPerformanceInfo()) != NO_ERROR) {
        HWC_LOGE(NULL,"%s:: deliverPerformanceInfo() error (%d)",
                __func__, ret);
    }

    if ((validateError == false) && (mDisplayControl.earlyStartMPP == true)) {
        if ((ret = startPostProcessing()) != NO_ERROR)
            validateError = true;
    }

    if (validateError) {
        setGeometryChanged(GEOMETRY_ERROR_CASE);
        mClientCompositionInfo.mSkipStaticInitFlag = false;
        mExynosCompositionInfo.mSkipStaticInitFlag = false;
        mResourceManager->resetAssignedResources(this, true);
        mClientCompositionInfo.initializeInfos(this);
        mExynosCompositionInfo.initializeInfos(this);
        for (uint32_t i = 0; i < mLayers.size(); i++) {
            ExynosLayer *layer = mLayers[i];
            layer->mOverlayInfo |= eResourceAssignFail;
            layer->mValidateCompositionType = HWC2_COMPOSITION_CLIENT;
            addClientCompositionLayer(i);
        }
        mResourceManager->assignCompositionTarget(this, COMPOSITION_CLIENT);
        mResourceManager->assignWindow(this);
    }

    resetColorMappingInfoForClientComp();
    storePrevValidateCompositionType();

    int32_t displayRequests = 0;
    if ((ret = getChangedCompositionTypes(outNumTypes, NULL, NULL)) != NO_ERROR) {
        HWC_LOGE(this, "%s:: getChangedCompositionTypes() fail, display(%d), ret(%d)", __func__, mDisplayId, ret);
        setGeometryChanged(GEOMETRY_ERROR_CASE);
    }
    if ((ret = getDisplayRequests(&displayRequests, outNumRequests, NULL, NULL)) != NO_ERROR) {
        HWC_LOGE(this, "%s:: getDisplayRequests() fail, display(%d), ret(%d)", __func__, mDisplayId, ret);
        setGeometryChanged(GEOMETRY_ERROR_CASE);
    }

    mSkipFrame = false;

    if ((*outNumTypes == 0) && (*outNumRequests == 0))
        return HWC2_ERROR_NONE;

    if (usePowerHintSession()) {
        mValidationDuration = systemTime(SYSTEM_TIME_MONOTONIC) - *mValidateStartTime;
    }

    return HWC2_ERROR_HAS_CHANGES;
}

int32_t ExynosDisplay::startPostProcessing()
{
    ATRACE_CALL();
    int ret = NO_ERROR;
    String8 errString;

    float assignedCapacity = mResourceManager->getAssignedCapacity(MPP_G2D);

    if (assignedCapacity > (mResourceManager->getM2MCapa(MPP_G2D) * MPP_CAPA_OVER_THRESHOLD)) {
        errString.appendFormat("Assigned capacity for exynos composition is over restriction (%f)",
                assignedCapacity);
        goto err;
    }

    if ((ret = doExynosComposition()) != NO_ERROR) {
        errString.appendFormat("exynosComposition fail (%d)\n", ret);
        goto err;
    }

    // loop for all layer
    for (size_t i=0; i < mLayers.size(); i++) {
        if((mLayers[i]->mValidateCompositionType == HWC2_COMPOSITION_DEVICE) &&
           (mLayers[i]->mM2mMPP != NULL)) {
            /* mAcquireFence is updated, Update image info */
            struct exynos_image srcImg, dstImg, midImg;
            mLayers[i]->setSrcExynosImage(&srcImg);
            mLayers[i]->setDstExynosImage(&dstImg);
            mLayers[i]->setExynosImage(srcImg, dstImg);
            ExynosMPP* m2mMpp = mLayers[i]->mM2mMPP;
            midImg = mLayers[i]->mMidImg;
            m2mMpp->requestHWStateChange(MPP_HW_STATE_RUNNING);
            if ((ret = m2mMpp->doPostProcessing(midImg)) != NO_ERROR) {
                DISPLAY_LOGE("%s:: doPostProcessing() failed, layer(%zu), ret(%d)",
                        __func__, i, ret);
                errString.appendFormat("%s:: doPostProcessing() failed, layer(%zu), ret(%d)\n",
                        __func__, i, ret);
                goto err;
            } else {
                /* This should be closed by lib for each resource */
                mLayers[i]->mAcquireFence = -1;
            }
        }
    }
    return ret;
err:
    printDebugInfos(errString);
    closeFences();
    mDisplayInterface->setForcePanic();
    return -EINVAL;
}

int32_t ExynosDisplay::setCursorPositionAsync(uint32_t x_pos, uint32_t y_pos) {
    mDisplayInterface->setCursorPositionAsync(x_pos, y_pos);
    return HWC2_ERROR_NONE;
}

// clang-format off
void ExynosDisplay::dumpConfig(const exynos_win_config_data &c)
{
    DISPLAY_LOGD(eDebugWinConfig|eDebugSkipStaicLayer, "\tstate = %u", c.state);
    if (c.state == c.WIN_STATE_COLOR) {
        DISPLAY_LOGD(eDebugWinConfig|eDebugSkipStaicLayer,
                "\t\tx = %d, y = %d, width = %d, height = %d, color = %u, alpha = %f\n",
                c.dst.x, c.dst.y, c.dst.w, c.dst.h, c.color, c.plane_alpha);
    } else/* if (c.state != c.WIN_STATE_DISABLED) */{
        DISPLAY_LOGD(eDebugWinConfig|eDebugSkipStaicLayer, "\t\tfd = (%d, %d, %d), acq_fence = %d, rel_fence = %d "
                "src_f_w = %u, src_f_h = %u, src_x = %d, src_y = %d, src_w = %u, src_h = %u, "
                "dst_f_w = %u, dst_f_h = %u, dst_x = %d, dst_y = %d, dst_w = %u, dst_h = %u, "
                "format = %u, pa = %f, transform = %d, dataspace = 0x%8x, hdr_enable = %d, blending = %u, "
                "protection = %u, compression = %s, compression_src = %d, transparent(x:%d, y:%d, w:%u, h:%u), "
                "block(x:%d, y:%d, w:%u, h:%u), opaque(x:%d, y:%d, w:%u, h:%u)",
                c.fd_idma[0], c.fd_idma[1], c.fd_idma[2],
                c.acq_fence, c.rel_fence,
                c.src.f_w, c.src.f_h, c.src.x, c.src.y, c.src.w, c.src.h,
                c.dst.f_w, c.dst.f_h, c.dst.x, c.dst.y, c.dst.w, c.dst.h,
                c.format, c.plane_alpha, c.transform, c.dataspace, c.hdr_enable,
                c.blending, c.protection, getCompressionStr(c.compressionInfo).c_str(), c.comp_src,
                c.transparent_area.x, c.transparent_area.y, c.transparent_area.w, c.transparent_area.h,
                c.block_area.x, c.block_area.y, c.block_area.w, c.block_area.h,
                c.opaque_area.x, c.opaque_area.y, c.opaque_area.w, c.opaque_area.h);
    }
}

void ExynosDisplay::dump(String8& result) {
    Mutex::Autolock lock(mDisplayMutex);
    dumpLocked(result);
}

void ExynosDisplay::dumpLocked(String8& result) {
    result.appendFormat("[%s] display information size: %d x %d, vsyncState: %d, colorMode: %d, "
                        "colorTransformHint: %d, orientation %d\n",
                        mDisplayName.c_str(), mXres, mYres, mVsyncState, mColorMode,
                        mColorTransformHint, mMountOrientation);
    mClientCompositionInfo.dump(result);
    mExynosCompositionInfo.dump(result);

    result.appendFormat("PanelGammaSource (%d)\n\n", GetCurrentPanelGammaSource());

    {
        Mutex::Autolock lock(mDRMutex);
        if (mLayers.size()) {
            result.appendFormat("============================== dump layers ===========================================\n");
            for (uint32_t i = 0; i < mLayers.size(); i++) {
                ExynosLayer *layer = mLayers[i];
                layer->dump(result);
            }
        }
        if (mIgnoreLayers.size()) {
            result.appendFormat("\n============================== dump ignore layers ===========================================\n");
            for (uint32_t i = 0; i < mIgnoreLayers.size(); i++) {
                ExynosLayer *layer = mIgnoreLayers[i];
                layer->dump(result);
            }
        }
    }
    result.appendFormat("\n");
    if (mBrightnessController) {
        mBrightnessController->dump(result);
    }
    if (mHistogramController) {
        mHistogramController->dump(result);
    }
}

void ExynosDisplay::dumpConfig(String8 &result, const exynos_win_config_data &c)
{
    result.appendFormat("\tstate = %u\n", c.state);
    if (c.state == c.WIN_STATE_COLOR) {
        result.appendFormat("\t\tx = %d, y = %d, width = %d, height = %d, color = %u, alpha = %f\n",
                c.dst.x, c.dst.y, c.dst.w, c.dst.h, c.color, c.plane_alpha);
    } else/* if (c.state != c.WIN_STATE_DISABLED) */{
        result.appendFormat("\t\tfd = (%d, %d, %d), acq_fence = %d, rel_fence = %d "
                "src_f_w = %u, src_f_h = %u, src_x = %d, src_y = %d, src_w = %u, src_h = %u, "
                "dst_f_w = %u, dst_f_h = %u, dst_x = %d, dst_y = %d, dst_w = %u, dst_h = %u, "
                "format = %u, pa = %f, transform = %d, dataspace = 0x%8x, hdr_enable = %d, blending = %u, "
                "protection = %u, compression = %s, compression_src = %d, transparent(x:%d, y:%d, w:%u, h:%u), "
                "block(x:%d, y:%d, w:%u, h:%u), opaque(x:%d, y:%d, w:%u, h:%u)\n",
                c.fd_idma[0], c.fd_idma[1], c.fd_idma[2],
                c.acq_fence, c.rel_fence,
                c.src.f_w, c.src.f_h, c.src.x, c.src.y, c.src.w, c.src.h,
                c.dst.f_w, c.dst.f_h, c.dst.x, c.dst.y, c.dst.w, c.dst.h,
                c.format, c.plane_alpha, c.transform, c.dataspace, c.hdr_enable, c.blending, c.protection,
                getCompressionStr(c.compressionInfo).c_str(), c.comp_src,
                c.transparent_area.x, c.transparent_area.y, c.transparent_area.w, c.transparent_area.h,
                c.block_area.x, c.block_area.y, c.block_area.w, c.block_area.h,
                c.opaque_area.x, c.opaque_area.y, c.opaque_area.w, c.opaque_area.h);
    }
}

void ExynosDisplay::printConfig(exynos_win_config_data &c)
{
    ALOGD("\tstate = %u", c.state);
    if (c.state == c.WIN_STATE_COLOR) {
        ALOGD("\t\tx = %d, y = %d, width = %d, height = %d, color = %u, alpha = %f\n",
                c.dst.x, c.dst.y, c.dst.w, c.dst.h, c.color, c.plane_alpha);
    } else/* if (c.state != c.WIN_STATE_DISABLED) */{
        ALOGD("\t\tfd = (%d, %d, %d), acq_fence = %d, rel_fence = %d "
                "src_f_w = %u, src_f_h = %u, src_x = %d, src_y = %d, src_w = %u, src_h = %u, "
                "dst_f_w = %u, dst_f_h = %u, dst_x = %d, dst_y = %d, dst_w = %u, dst_h = %u, "
                "format = %u, pa = %f, transform = %d, dataspace = 0x%8x, hdr_enable = %d, blending = %u, "
                "protection = %u, compression = %s, compression_src = %d, transparent(x:%d, y:%d, w:%u, h:%u), "
                "block(x:%d, y:%d, w:%u, h:%u), opaque(x:%d, y:%d, w:%u, h:%u)",
                c.fd_idma[0], c.fd_idma[1], c.fd_idma[2],
                c.acq_fence, c.rel_fence,
                c.src.f_w, c.src.f_h, c.src.x, c.src.y, c.src.w, c.src.h,
                c.dst.f_w, c.dst.f_h, c.dst.x, c.dst.y, c.dst.w, c.dst.h,
                c.format, c.plane_alpha, c.transform, c.dataspace, c.hdr_enable, c.blending, c.protection,
                getCompressionStr(c.compressionInfo).c_str(), c.comp_src,
                c.transparent_area.x, c.transparent_area.y, c.transparent_area.w, c.transparent_area.h,
                c.block_area.x, c.block_area.y, c.block_area.w, c.block_area.h,
                c.opaque_area.x, c.opaque_area.y, c.opaque_area.w, c.opaque_area.h);
    }
}
// clang-format on

int32_t ExynosDisplay::setCompositionTargetExynosImage(uint32_t targetType, exynos_image *src_img, exynos_image *dst_img)
{
    if (targetType != COMPOSITION_CLIENT && targetType != COMPOSITION_EXYNOS) {
        return -EINVAL;
    }
    const ExynosCompositionInfo& compositionInfo =
            (targetType == COMPOSITION_CLIENT) ? mClientCompositionInfo : mExynosCompositionInfo;

    src_img->fullWidth = mXres;
    src_img->fullHeight = mYres;
    /* To do */
    /* Fb crop should be set hear */
    src_img->x = 0;
    src_img->y = 0;
    src_img->w = mXres;
    src_img->h = mYres;

    if (compositionInfo.mTargetBuffer != NULL) {
        src_img->bufferHandle = compositionInfo.mTargetBuffer;

        VendorGraphicBufferMeta gmeta(compositionInfo.mTargetBuffer);
        src_img->format = gmeta.format;
        src_img->usageFlags = gmeta.producer_usage;
    } else {
        src_img->bufferHandle = NULL;
        src_img->format = HAL_PIXEL_FORMAT_RGBA_8888;
        src_img->usageFlags = 0;
    }
    src_img->layerFlags = 0x0;
    src_img->acquireFenceFd = compositionInfo.mAcquireFence;
    src_img->releaseFenceFd = -1;
    src_img->dataSpace = compositionInfo.mDataSpace;
    src_img->blending = HWC2_BLEND_MODE_PREMULTIPLIED;
    src_img->transform = 0;
    src_img->compressionInfo = compositionInfo.mCompressionInfo;
    src_img->planeAlpha = 1;
    src_img->zOrder = 0;
    if ((targetType == COMPOSITION_CLIENT) && (mType == HWC_DISPLAY_VIRTUAL)) {
        if (compositionInfo.mLastIndex < mExynosCompositionInfo.mLastIndex)
            src_img->zOrder = 0;
        else
            src_img->zOrder = 1000;
    }
    src_img->needPreblending = compositionInfo.mNeedPreblending;

    dst_img->fullWidth = mXres;
    dst_img->fullHeight = mYres;
    /* To do */
    /* Fb crop should be set hear */
    dst_img->x = 0;
    dst_img->y = 0;
    dst_img->w = mXres;
    dst_img->h = mYres;

    dst_img->bufferHandle = NULL;
    dst_img->format = HAL_PIXEL_FORMAT_RGBA_8888;
    dst_img->usageFlags = 0;

    dst_img->layerFlags = 0x0;
    dst_img->acquireFenceFd = -1;
    dst_img->releaseFenceFd = -1;
    dst_img->dataSpace = src_img->dataSpace;
    if (mColorMode != HAL_COLOR_MODE_NATIVE)
        dst_img->dataSpace = colorModeToDataspace(mColorMode);
    dst_img->blending = HWC2_BLEND_MODE_NONE;
    dst_img->transform = 0;
    dst_img->compressionInfo = compositionInfo.mCompressionInfo;
    dst_img->planeAlpha = 1;
    dst_img->zOrder = src_img->zOrder;

    return NO_ERROR;
}

int32_t ExynosDisplay::initializeValidateInfos()
{
    mCursorIndex = -1;
    for (uint32_t i = 0; i < mLayers.size(); i++) {
        ExynosLayer *layer = mLayers[i];
        layer->mValidateCompositionType = HWC2_COMPOSITION_INVALID;
        layer->mOverlayInfo = 0;
        if ((mDisplayControl.cursorSupport == true) &&
            (mLayers[i]->mCompositionType == HWC2_COMPOSITION_CURSOR))
            mCursorIndex = i;
    }

    exynos_image src_img;
    exynos_image dst_img;

    mClientCompositionInfo.initializeInfos(this);
    setCompositionTargetExynosImage(COMPOSITION_CLIENT, &src_img, &dst_img);
    mClientCompositionInfo.setExynosImage(src_img, dst_img);

    mExynosCompositionInfo.initializeInfos(this);
    setCompositionTargetExynosImage(COMPOSITION_EXYNOS, &src_img, &dst_img);
    mExynosCompositionInfo.setExynosImage(src_img, dst_img);

    return NO_ERROR;
}

int32_t ExynosDisplay::addClientCompositionLayer(uint32_t layerIndex)
{
    bool exynosCompositionChanged = false;
    int32_t ret = NO_ERROR;

    DISPLAY_LOGD(eDebugResourceManager, "[%d] layer is added to client composition", layerIndex);

    if (mClientCompositionInfo.mHasCompositionLayer == false) {
        mClientCompositionInfo.mFirstIndex = layerIndex;
        mClientCompositionInfo.mLastIndex = layerIndex;
        mClientCompositionInfo.mHasCompositionLayer = true;
        return EXYNOS_ERROR_CHANGED;
    } else {
        mClientCompositionInfo.mFirstIndex = min(mClientCompositionInfo.mFirstIndex, (int32_t)layerIndex);
        mClientCompositionInfo.mLastIndex = max(mClientCompositionInfo.mLastIndex, (int32_t)layerIndex);
    }
    DISPLAY_LOGD(eDebugResourceManager, "\tClient composition range [%d] - [%d]",
            mClientCompositionInfo.mFirstIndex, mClientCompositionInfo.mLastIndex);

    if ((mClientCompositionInfo.mFirstIndex < 0) || (mClientCompositionInfo.mLastIndex < 0))
    {
        HWC_LOGE(this, "%s:: mClientCompositionInfo.mHasCompositionLayer is true "
                "but index is not valid (firstIndex: %d, lastIndex: %d)",
                __func__, mClientCompositionInfo.mFirstIndex,
                mClientCompositionInfo.mLastIndex);
        return -EINVAL;
    }

    /* handle sandwiched layers */
    for (uint32_t i = (uint32_t)mClientCompositionInfo.mFirstIndex + 1; i < (uint32_t)mClientCompositionInfo.mLastIndex; i++) {
        ExynosLayer *layer = mLayers[i];
        if (layer->needClearClientTarget()) {
            DISPLAY_LOGD(eDebugResourceManager,
                         "\t[%d] layer is opaque and has high or max priority (%d)", i,
                         layer->mOverlayPriority);
            continue;
        }
        if (layer->mValidateCompositionType != HWC2_COMPOSITION_CLIENT)
        {
            DISPLAY_LOGD(eDebugResourceManager, "\t[%d] layer changed", i);
            if (layer->mValidateCompositionType == HWC2_COMPOSITION_EXYNOS)
                exynosCompositionChanged = true;
            else {
                if (layer->mValidateCompositionType == HWC2_COMPOSITION_DEVICE) mWindowNumUsed--;
            }
            layer->resetAssignedResource();
            layer->mValidateCompositionType = HWC2_COMPOSITION_CLIENT;
            layer->mOverlayInfo |= eSandwitchedBetweenGLES;
        }
    }

    /* Check Exynos Composition info is changed */
    if (exynosCompositionChanged) {
        DISPLAY_LOGD(eDebugResourceManager, "exynos composition [%d] - [%d] is changed",
                mExynosCompositionInfo.mFirstIndex, mExynosCompositionInfo.mLastIndex);
        uint32_t newFirstIndex = ~0;
        int32_t newLastIndex = -1;

        if ((mExynosCompositionInfo.mFirstIndex < 0) || (mExynosCompositionInfo.mLastIndex < 0))
        {
            HWC_LOGE(this, "%s:: mExynosCompositionInfo.mHasCompositionLayer should be true(%d) "
                    "but index is not valid (firstIndex: %d, lastIndex: %d)",
                    __func__, mExynosCompositionInfo.mHasCompositionLayer,
                    mExynosCompositionInfo.mFirstIndex,
                    mExynosCompositionInfo.mLastIndex);
            return -EINVAL;
        }

        for (uint32_t i = 0; i < mLayers.size(); i++)
        {
            ExynosLayer *exynosLayer = mLayers[i];
            if (exynosLayer->mValidateCompositionType == HWC2_COMPOSITION_EXYNOS) {
                newFirstIndex = min(newFirstIndex, i);
                newLastIndex = max(newLastIndex, (int32_t)i);
            }
        }

        DISPLAY_LOGD(eDebugResourceManager, "changed exynos composition [%d] - [%d]",
                newFirstIndex, newLastIndex);

        /* There is no exynos composition layer */
        if (newFirstIndex == (uint32_t)~0)
        {
            mExynosCompositionInfo.initializeInfos(this);
            ret = EXYNOS_ERROR_CHANGED;
        } else {
            mExynosCompositionInfo.mFirstIndex = newFirstIndex;
            mExynosCompositionInfo.mLastIndex = newLastIndex;
        }
    }

    DISPLAY_LOGD(eDebugResourceManager, "\tresult changeFlag(0x%8x)", ret);
    DISPLAY_LOGD(eDebugResourceManager, "\tClient composition(%d) range [%d] - [%d]",
            mClientCompositionInfo.mHasCompositionLayer,
            mClientCompositionInfo.mFirstIndex, mClientCompositionInfo.mLastIndex);
    DISPLAY_LOGD(eDebugResourceManager, "\tExynos composition(%d) range [%d] - [%d]",
            mExynosCompositionInfo.mHasCompositionLayer,
            mExynosCompositionInfo.mFirstIndex, mExynosCompositionInfo.mLastIndex);

    return ret;
}
int32_t ExynosDisplay::removeClientCompositionLayer(uint32_t layerIndex)
{
    int32_t ret = NO_ERROR;

    DISPLAY_LOGD(eDebugResourceManager, "[%d] - [%d] [%d] layer is removed from client composition",
            mClientCompositionInfo.mFirstIndex, mClientCompositionInfo.mLastIndex,
            layerIndex);

    /* Only first layer or last layer can be removed */
    if ((mClientCompositionInfo.mHasCompositionLayer == false) ||
        ((mClientCompositionInfo.mFirstIndex != (int32_t)layerIndex) &&
         (mClientCompositionInfo.mLastIndex != (int32_t)layerIndex))) {
        DISPLAY_LOGE("removeClientCompositionLayer() error, [%d] - [%d], layer[%d]",
                mClientCompositionInfo.mFirstIndex, mClientCompositionInfo.mLastIndex,
                layerIndex);
        return -EINVAL;
    }

    if (mClientCompositionInfo.mFirstIndex == mClientCompositionInfo.mLastIndex) {
        ExynosMPP *otfMPP = mClientCompositionInfo.mOtfMPP;
        if (otfMPP != NULL)
            otfMPP->resetAssignedState();
        else {
            DISPLAY_LOGE("mClientCompositionInfo.mOtfMPP is NULL");
            return -EINVAL;
        }
        mClientCompositionInfo.initializeInfos(this);
        mWindowNumUsed--;
    } else if ((int32_t)layerIndex == mClientCompositionInfo.mFirstIndex)
        mClientCompositionInfo.mFirstIndex++;
    else
        mClientCompositionInfo.mLastIndex--;

    DISPLAY_LOGD(eDebugResourceManager, "\tClient composition(%d) range [%d] - [%d]",
            mClientCompositionInfo.mHasCompositionLayer,
            mClientCompositionInfo.mFirstIndex, mClientCompositionInfo.mLastIndex);

    return ret;
}

bool ExynosDisplay::hasClientComposition() {
    return mClientCompositionInfo.mHasCompositionLayer;
}

int32_t ExynosDisplay::addExynosCompositionLayer(uint32_t layerIndex, float totalUsedCapa) {
    bool invalidFlag = false;
    int32_t changeFlag = NO_ERROR;
    int ret = 0;
    int32_t startIndex;
    int32_t endIndex;

    DISPLAY_LOGD(eDebugResourceManager, "[%d] layer is added to exynos composition", layerIndex);

    if (mExynosCompositionInfo.mHasCompositionLayer == false) {
        mExynosCompositionInfo.mFirstIndex = layerIndex;
        mExynosCompositionInfo.mLastIndex = layerIndex;
        mExynosCompositionInfo.mHasCompositionLayer = true;
        return EXYNOS_ERROR_CHANGED;
    } else {
        mExynosCompositionInfo.mFirstIndex = min(mExynosCompositionInfo.mFirstIndex, (int32_t)layerIndex);
        mExynosCompositionInfo.mLastIndex = max(mExynosCompositionInfo.mLastIndex, (int32_t)layerIndex);
    }

    DISPLAY_LOGD(eDebugResourceManager, "\tExynos composition range [%d] - [%d]",
            mExynosCompositionInfo.mFirstIndex, mExynosCompositionInfo.mLastIndex);

    ExynosMPP *m2mMPP = mExynosCompositionInfo.mM2mMPP;

    if (m2mMPP == NULL) {
        DISPLAY_LOGE("exynosComposition m2mMPP is NULL");
        return -EINVAL;
    }

    startIndex = mExynosCompositionInfo.mFirstIndex;
    endIndex = mExynosCompositionInfo.mLastIndex;

    if ((startIndex < 0) || (endIndex < 0) ||
        (startIndex >= (int32_t)mLayers.size()) || (endIndex >= (int32_t)mLayers.size())) {
        DISPLAY_LOGE("exynosComposition invalid index (%d), (%d)", startIndex, endIndex);
        return -EINVAL;
    }

    int32_t maxPriorityIndex = -1;

    uint32_t highPriorityIndex = 0;
    uint32_t highPriorityNum = 0;
    int32_t highPriorityCheck = 0;
    std::vector<int32_t> highPriority;
    highPriority.assign(mLayers.size(), -1);
    /* handle sandwiched layers */
    for (int32_t i = startIndex; i <= endIndex; i++) {
        ExynosLayer *layer = mLayers[i];
        if (layer == NULL) {
            DISPLAY_LOGE("layer[%d] layer is null", i);
            continue;
        }

        if (layer->mOverlayPriority == ePriorityMax &&
                m2mMPP->mLogicalType == MPP_LOGICAL_G2D_COMBO) {
            DISPLAY_LOGD(eDebugResourceManager, "\tG2D will be assgined for only [%d] layer", i);
            invalidFlag = true;
            maxPriorityIndex = i;
            continue;
        }

        if (layer->mOverlayPriority >= ePriorityHigh)
        {
            DISPLAY_LOGD(eDebugResourceManager, "\t[%d] layer has high priority", i);
            highPriority[highPriorityIndex++] = i;
            highPriorityNum++;
            continue;
        }

        if (layer->mValidateCompositionType == HWC2_COMPOSITION_EXYNOS)
            continue;

        exynos_image src_img;
        exynos_image dst_img;
        layer->setSrcExynosImage(&src_img);
        layer->setDstExynosImage(&dst_img);
        layer->setExynosMidImage(dst_img);
        bool isAssignable = false;
        if ((layer->mSupportedMPPFlag & m2mMPP->mLogicalType) != 0)
            isAssignable = m2mMPP->isAssignable(this, src_img, dst_img, totalUsedCapa);

        if (layer->mValidateCompositionType == HWC2_COMPOSITION_CLIENT)
        {
            DISPLAY_LOGD(eDebugResourceManager, "\t[%d] layer is client composition", i);
            invalidFlag = true;
        } else if (((layer->mSupportedMPPFlag & m2mMPP->mLogicalType) == 0) ||
                   (isAssignable == false))
        {
            DISPLAY_LOGD(eDebugResourceManager, "\t[%d] layer is not supported by G2D", i);
            invalidFlag = true;
            layer->resetAssignedResource();
            layer->mValidateCompositionType = HWC2_COMPOSITION_CLIENT;
            if ((ret = addClientCompositionLayer(i)) < 0)
                return ret;
            changeFlag |= ret;
        } else if ((layer->mValidateCompositionType == HWC2_COMPOSITION_DEVICE) ||
                   (layer->mValidateCompositionType == HWC2_COMPOSITION_INVALID)) {
            DISPLAY_LOGD(eDebugResourceManager, "\t[%d] layer changed", i);
            layer->mOverlayInfo |= eSandwitchedBetweenEXYNOS;
            layer->resetAssignedResource();
            if ((ret = m2mMPP->assignMPP(this, layer)) != NO_ERROR)
            {
                HWC_LOGE(this, "%s:: %s MPP assignMPP() error (%d)",
                        __func__, m2mMPP->mName.c_str(), ret);
                return ret;
            }
            if (layer->mValidateCompositionType == HWC2_COMPOSITION_DEVICE) mWindowNumUsed--;
            layer->mValidateCompositionType = HWC2_COMPOSITION_EXYNOS;
            mExynosCompositionInfo.mFirstIndex = min(mExynosCompositionInfo.mFirstIndex, (int32_t)i);
            mExynosCompositionInfo.mLastIndex = max(mExynosCompositionInfo.mLastIndex, (int32_t)i);
        } else {
            DISPLAY_LOGD(eDebugResourceManager, "\t[%d] layer has known type (%d)", i,
                         layer->mValidateCompositionType);
        }
    }

    if (invalidFlag) {
        DISPLAY_LOGD(eDebugResourceManager, "\tClient composition range [%d] - [%d]",
                mClientCompositionInfo.mFirstIndex, mClientCompositionInfo.mLastIndex);
        DISPLAY_LOGD(eDebugResourceManager, "\tExynos composition range [%d] - [%d], highPriorityNum[%d]",
                mExynosCompositionInfo.mFirstIndex, mExynosCompositionInfo.mLastIndex, highPriorityNum);

        if (m2mMPP->mLogicalType == MPP_LOGICAL_G2D_COMBO && maxPriorityIndex >= 0) {
            startIndex = mExynosCompositionInfo.mFirstIndex;
            endIndex = mExynosCompositionInfo.mLastIndex;

            for (int32_t i = startIndex; i <= endIndex; i++) {
                if (mLayers[i]->mOverlayPriority == ePriorityMax ||
                        mLayers[i]->mValidateCompositionType == HWC2_COMPOSITION_CLIENT)
                    continue;
                mLayers[i]->resetAssignedResource();
                mLayers[i]->mValidateCompositionType = HWC2_COMPOSITION_CLIENT;
                if ((ret = addClientCompositionLayer(i)) < 0)
                    return ret;
                changeFlag |= ret;
            }

            if (mLayers[maxPriorityIndex]->mValidateCompositionType
                    != HWC2_COMPOSITION_EXYNOS) {
                mLayers[maxPriorityIndex]->mValidateCompositionType = HWC2_COMPOSITION_EXYNOS;
                mLayers[maxPriorityIndex]->resetAssignedResource();
                if ((ret = m2mMPP->assignMPP(this, mLayers[maxPriorityIndex])) != NO_ERROR)
                {
                    ALOGE("%s:: %s MPP assignMPP() error (%d)",
                            __func__, m2mMPP->mName.c_str(), ret);
                    return ret;
                }
            }

            mExynosCompositionInfo.mFirstIndex = maxPriorityIndex;
            mExynosCompositionInfo.mLastIndex = maxPriorityIndex;
        }

        /* Check if exynos comosition nests GLES composition */
        if ((mClientCompositionInfo.mHasCompositionLayer) &&
            (mExynosCompositionInfo.mFirstIndex < mClientCompositionInfo.mFirstIndex) &&
            (mClientCompositionInfo.mFirstIndex < mExynosCompositionInfo.mLastIndex) &&
            (mExynosCompositionInfo.mFirstIndex < mClientCompositionInfo.mLastIndex) &&
            (mClientCompositionInfo.mLastIndex < mExynosCompositionInfo.mLastIndex)) {

            if ((mClientCompositionInfo.mFirstIndex - mExynosCompositionInfo.mFirstIndex) <
                (mExynosCompositionInfo.mLastIndex - mClientCompositionInfo.mLastIndex)) {
                mLayers[mExynosCompositionInfo.mFirstIndex]->resetAssignedResource();
                mLayers[mExynosCompositionInfo.mFirstIndex]->mValidateCompositionType = HWC2_COMPOSITION_CLIENT;
                if ((ret = addClientCompositionLayer(mExynosCompositionInfo.mFirstIndex)) < 0)
                    return ret;
                mExynosCompositionInfo.mFirstIndex = mClientCompositionInfo.mLastIndex + 1;
                changeFlag |= ret;
            } else {
                mLayers[mExynosCompositionInfo.mLastIndex]->resetAssignedResource();
                mLayers[mExynosCompositionInfo.mLastIndex]->mValidateCompositionType = HWC2_COMPOSITION_CLIENT;
                if ((ret = addClientCompositionLayer(mExynosCompositionInfo.mLastIndex)) < 0)
                    return ret;
                mExynosCompositionInfo.mLastIndex = (mClientCompositionInfo.mFirstIndex - 1);
                changeFlag |= ret;
            }
        }
    }

    if (highPriorityNum > 0 && (m2mMPP->mLogicalType != MPP_LOGICAL_G2D_COMBO)) {
        for (uint32_t i = 0; i < highPriorityNum; i++) {
            if ((int32_t)highPriority[i] == mExynosCompositionInfo.mFirstIndex)
                mExynosCompositionInfo.mFirstIndex++;
            else if ((int32_t)highPriority[i] == mExynosCompositionInfo.mLastIndex)
                mExynosCompositionInfo.mLastIndex--;
        }
    }

    if ((mExynosCompositionInfo.mFirstIndex < 0) ||
        (mExynosCompositionInfo.mFirstIndex >= (int)mLayers.size()) ||
        (mExynosCompositionInfo.mLastIndex < 0) ||
        (mExynosCompositionInfo.mLastIndex >= (int)mLayers.size()) ||
        (mExynosCompositionInfo.mFirstIndex > mExynosCompositionInfo.mLastIndex))
    {
        DISPLAY_LOGD(eDebugResourceManager, "\texynos composition is disabled, because of invalid index (%d, %d), size(%zu)",
                mExynosCompositionInfo.mFirstIndex, mExynosCompositionInfo.mLastIndex, mLayers.size());
        mExynosCompositionInfo.initializeInfos(this);
        changeFlag = EXYNOS_ERROR_CHANGED;
    }

    for (uint32_t i = 0; i < highPriorityNum; i++) {
        if ((mExynosCompositionInfo.mFirstIndex < (int32_t)highPriority[i]) &&
            ((int32_t)highPriority[i] < mExynosCompositionInfo.mLastIndex)) {
            highPriorityCheck = 1;
            break;
        }
    }


    if (highPriorityCheck && (m2mMPP->mLogicalType != MPP_LOGICAL_G2D_COMBO)) {
        startIndex = mExynosCompositionInfo.mFirstIndex;
        endIndex = mExynosCompositionInfo.mLastIndex;
        DISPLAY_LOGD(eDebugResourceManager, "\texynos composition is disabled because of sandwitched max priority layer (%d, %d)",
                mExynosCompositionInfo.mFirstIndex, mExynosCompositionInfo.mLastIndex);
        for (int32_t i = startIndex; i <= endIndex; i++) {
            int32_t checkPri = 0;
            for (uint32_t j = 0; j < highPriorityNum; j++) {
                if (i == (int32_t)highPriority[j]) {
                    checkPri = 1;
                    break;
                }
            }

            if (checkPri)
                continue;

            mLayers[i]->resetAssignedResource();
            mLayers[i]->mValidateCompositionType = HWC2_COMPOSITION_CLIENT;
            if ((ret = addClientCompositionLayer(i)) < 0)
                HWC_LOGE(this, "%d layer: addClientCompositionLayer() fail", i);
        }
        mExynosCompositionInfo.initializeInfos(this);
        changeFlag = EXYNOS_ERROR_CHANGED;
    }

    DISPLAY_LOGD(eDebugResourceManager, "\tresult changeFlag(0x%8x)", changeFlag);
    DISPLAY_LOGD(eDebugResourceManager, "\tClient composition range [%d] - [%d]",
            mClientCompositionInfo.mFirstIndex, mClientCompositionInfo.mLastIndex);
    DISPLAY_LOGD(eDebugResourceManager, "\tExynos composition range [%d] - [%d]",
            mExynosCompositionInfo.mFirstIndex, mExynosCompositionInfo.mLastIndex);

    return changeFlag;
}

bool ExynosDisplay::isPowerModeOff() const {
    ATRACE_CALL();
    Mutex::Autolock lock(mDisplayMutex);
    return mPowerModeState.has_value() && mPowerModeState.value() == HWC2_POWER_MODE_OFF;
}

bool ExynosDisplay::isSecureContentPresenting() const {
    ATRACE_CALL();
    Mutex::Autolock lock(mDRMutex);
    for (uint32_t i = 0; i < mLayers.size(); i++) {
        ExynosLayer *layer = mLayers[i];
        if (layer != NULL && layer->isDrm()) { /* there is some DRM layer */
            return true;
        }
    }
    return false;
}

bool ExynosDisplay::windowUpdateExceptions()
{

    if (mExynosCompositionInfo.mHasCompositionLayer) {
        DISPLAY_LOGD(eDebugWindowUpdate, "has exynos composition");
        return true;
    }
    if (mClientCompositionInfo.mHasCompositionLayer) {
        DISPLAY_LOGD(eDebugWindowUpdate, "has client composition");
        return true;
    }

    for (size_t i = 0; i < mLayers.size(); i++) {
        if (mLayers[i]->mM2mMPP != NULL) return true;
        if (mLayers[i]->mLayerBuffer == NULL) return true;
        if (mLayers[i]->mTransform != 0) return true;
    }

    for (size_t i = 0; i < mDpuData.configs.size(); i++) {
        exynos_win_config_data &config = mDpuData.configs[i];
        if (config.state == config.WIN_STATE_BUFFER) {
            if (config.src.w/config.dst.w != 1 || config.src.h/config.dst.h != 1) {
                DISPLAY_LOGD(eDebugWindowUpdate, "Skip reason : scaled");
                return true;
            }
        }
    }

    return false;
}

int ExynosDisplay::handleWindowUpdate()
{
    int ret = NO_ERROR;
    // TODO will be implemented
    unsigned int excp;

    mDpuData.enable_win_update = false;
    /* Init with full size */
    mDpuData.win_update_region.x = 0;
    mDpuData.win_update_region.w = mXres;
    mDpuData.win_update_region.y = 0;
    mDpuData.win_update_region.h = mYres;

    if (exynosHWCControl.windowUpdate != 1) return 0;

    if (mGeometryChanged != 0) {
        DISPLAY_LOGD(eDebugWindowUpdate, "GEOMETRY chnaged 0x%"  PRIx64 "",
                mGeometryChanged);
        return 0;
    }

    if ((mCursorIndex >= 0) && (mCursorIndex < (int32_t)mLayers.size())) {
        ExynosLayer *layer = mLayers[mCursorIndex];
        /* Cursor layer is enabled */
        if (layer->mExynosCompositionType == HWC2_COMPOSITION_DEVICE) {
            return 0;
        }
    }

    /* exceptions */
    if (windowUpdateExceptions())
        return 0;

    hwc_rect mergedRect = {(int)mXres, (int)mYres, 0, 0};
    hwc_rect damageRect = {(int)mXres, (int)mYres, 0, 0};

    for (size_t i = 0; i < mLayers.size(); i++) {
        if (mLayers[i]->mExynosCompositionType == HWC2_COMPOSITION_DISPLAY_DECORATION) {
            continue;
        }
        excp = getLayerRegion(mLayers[i], &damageRect, eDamageRegionByDamage);
        if (excp == eDamageRegionPartial) {
            DISPLAY_LOGD(eDebugWindowUpdate, "layer(%zu) partial : %d, %d, %d, %d", i,
                    damageRect.left, damageRect.top, damageRect.right, damageRect.bottom);
            mergedRect = expand(mergedRect, damageRect);
        }
        else if (excp == eDamageRegionSkip) {
            int32_t windowIndex = mLayers[i]->mWindowIndex;
            if ((ret = checkConfigDstChanged(mDpuData, mLastDpuData, windowIndex)) < 0) {
                return 0;
            } else if (ret > 0) {
                damageRect.left = mLayers[i]->mDisplayFrame.left;
                damageRect.right = mLayers[i]->mDisplayFrame.right;
                damageRect.top = mLayers[i]->mDisplayFrame.top;
                damageRect.bottom = mLayers[i]->mDisplayFrame.bottom;
                DISPLAY_LOGD(eDebugWindowUpdate, "Skip layer (origin) : %d, %d, %d, %d",
                        damageRect.left, damageRect.top, damageRect.right, damageRect.bottom);
                mergedRect = expand(mergedRect, damageRect);
                hwc_rect prevDst = {mLastDpuData.configs[i].dst.x, mLastDpuData.configs[i].dst.y,
                    mLastDpuData.configs[i].dst.x + (int)mLastDpuData.configs[i].dst.w,
                    mLastDpuData.configs[i].dst.y + (int)mLastDpuData.configs[i].dst.h};
                mergedRect = expand(mergedRect, prevDst);
            } else {
                DISPLAY_LOGD(eDebugWindowUpdate, "layer(%zu) skip", i);
                continue;
            }
        }
        else if (excp == eDamageRegionFull) {
            damageRect.left = mLayers[i]->mDisplayFrame.left;
            damageRect.top = mLayers[i]->mDisplayFrame.top;
            damageRect.right = mLayers[i]->mDisplayFrame.right;
            damageRect.bottom = mLayers[i]->mDisplayFrame.bottom;
            DISPLAY_LOGD(eDebugWindowUpdate, "Full layer update : %d, %d, %d, %d", mLayers[i]->mDisplayFrame.left,
                    mLayers[i]->mDisplayFrame.top, mLayers[i]->mDisplayFrame.right, mLayers[i]->mDisplayFrame.bottom);
            mergedRect = expand(mergedRect, damageRect);
        }
        else {
            DISPLAY_LOGD(eDebugWindowUpdate, "Partial canceled, Skip reason (layer %zu) : %d", i, excp);
            return 0;
        }
    }

    if (mergedRect.left == (int32_t)mXres && mergedRect.right == 0 &&
        mergedRect.top == (int32_t)mYres && mergedRect.bottom == 0) {
        DISPLAY_LOGD(eDebugWindowUpdate, "Partial canceled, All layer skiped" );
        return 0;
    }

    DISPLAY_LOGD(eDebugWindowUpdate, "Partial(origin) : %d, %d, %d, %d",
            mergedRect.left, mergedRect.top, mergedRect.right, mergedRect.bottom);

    if (mergedRect.left < 0) mergedRect.left = 0;
    if (mergedRect.right > (int32_t)mXres) mergedRect.right = mXres;
    if (mergedRect.top < 0) mergedRect.top = 0;
    if (mergedRect.bottom > (int32_t)mYres) mergedRect.bottom = mYres;

    if (mergedRect.left == 0 && mergedRect.right == (int32_t)mXres &&
        mergedRect.top == 0 && mergedRect.bottom == (int32_t)mYres) {
        DISPLAY_LOGD(eDebugWindowUpdate, "Partial : Full size");
        mDpuData.enable_win_update = true;
        mDpuData.win_update_region.x = 0;
        mDpuData.win_update_region.w = mXres;
        mDpuData.win_update_region.y = 0;
        mDpuData.win_update_region.h = mYres;
        DISPLAY_LOGD(eDebugWindowUpdate, "window update end ------------------");
        return 0;
    }

    mDpuData.enable_win_update = true;
    mDpuData.win_update_region.x = mergedRect.left;
    mDpuData.win_update_region.w = WIDTH(mergedRect);
    mDpuData.win_update_region.y = mergedRect.top;
    mDpuData.win_update_region.h = HEIGHT(mergedRect);

    DISPLAY_LOGD(eDebugWindowUpdate, "window update end ------------------");
    return 0;
}

unsigned int ExynosDisplay::getLayerRegion(ExynosLayer *layer, hwc_rect *rect_area, uint32_t regionType) {

    android::Vector <hwc_rect_t> hwcRects;
    size_t numRects = 0;

    rect_area->left = INT_MAX;
    rect_area->top = INT_MAX;
    rect_area->right = rect_area->bottom = 0;

    hwcRects = layer->mDamageRects;
    numRects = layer->mDamageNum;

    if ((numRects == 0) || (hwcRects.size() == 0))
        return eDamageRegionFull;

    if ((numRects == 1) && (hwcRects[0].left == 0) && (hwcRects[0].top == 0) &&
            (hwcRects[0].right == 0) && (hwcRects[0].bottom == 0))
        return eDamageRegionSkip;

    switch (regionType) {
    case eDamageRegionByDamage:
        for (size_t j = 0; j < hwcRects.size(); j++) {
            hwc_rect_t rect;

            if ((hwcRects[j].left < 0) || (hwcRects[j].top < 0) ||
                    (hwcRects[j].right < 0) || (hwcRects[j].bottom < 0) ||
                    (hwcRects[j].left >= hwcRects[j].right) || (hwcRects[j].top >= hwcRects[j].bottom) ||
                    (hwcRects[j].right - hwcRects[j].left > WIDTH(layer->mSourceCrop)) ||
                    (hwcRects[j].bottom - hwcRects[j].top > HEIGHT(layer->mSourceCrop))) {
                rect_area->left = INT_MAX;
                rect_area->top = INT_MAX;
                rect_area->right = rect_area->bottom = 0;
                return eDamageRegionFull;
            }

            rect.left = layer->mDisplayFrame.left + hwcRects[j].left - layer->mSourceCrop.left;
            rect.top = layer->mDisplayFrame.top + hwcRects[j].top - layer->mSourceCrop.top;
            rect.right = layer->mDisplayFrame.left + hwcRects[j].right - layer->mSourceCrop.left;
            rect.bottom = layer->mDisplayFrame.top + hwcRects[j].bottom - layer->mSourceCrop.top;
            DISPLAY_LOGD(eDebugWindowUpdate, "Display frame : %d, %d, %d, %d", layer->mDisplayFrame.left,
                    layer->mDisplayFrame.top, layer->mDisplayFrame.right, layer->mDisplayFrame.bottom);
            DISPLAY_LOGD(eDebugWindowUpdate, "hwcRects : %d, %d, %d, %d", hwcRects[j].left,
                    hwcRects[j].top, hwcRects[j].right, hwcRects[j].bottom);
            adjustRect(rect, INT_MAX, INT_MAX);
            /* Get sums of rects */
            *rect_area = expand(*rect_area, rect);
        }
        return eDamageRegionPartial;
        break;
    case eDamageRegionByLayer:
        if (layer->mLastLayerBuffer != layer->mLayerBuffer)
            return eDamageRegionFull;
        else
            return eDamageRegionSkip;
        break;
    default:
        HWC_LOGE(this, "%s:: Invalid regionType (%d)", __func__, regionType);
        return eDamageRegionError;
        break;
    }

    return eDamageRegionFull;
}

uint32_t ExynosDisplay::getRestrictionIndex(int halFormat)
{
    if (isFormatRgb(halFormat))
        return RESTRICTION_RGB;
    else
        return RESTRICTION_YUV;
}

void ExynosDisplay::closeFencesForSkipFrame(rendering_state renderingState)
{
    for (size_t i=0; i < mLayers.size(); i++) {
        if (mLayers[i]->mAcquireFence != -1) {
            mLayers[i]->mAcquireFence = fence_close(mLayers[i]->mAcquireFence, this,
                    FENCE_TYPE_SRC_ACQUIRE, FENCE_IP_LAYER);
        }
    }

    if (mDpuData.readback_info.rel_fence >= 0) {
        mDpuData.readback_info.rel_fence =
            fence_close(mDpuData.readback_info.rel_fence, this,
                    FENCE_TYPE_READBACK_RELEASE, FENCE_IP_FB);
    }
    if (mDpuData.readback_info.acq_fence >= 0) {
        mDpuData.readback_info.acq_fence =
            fence_close(mDpuData.readback_info.acq_fence, this,
                    FENCE_TYPE_READBACK_ACQUIRE, FENCE_IP_DPP);
    }

    if (renderingState >= RENDERING_STATE_VALIDATED) {
        if (mDisplayControl.earlyStartMPP == true) {
            if (mExynosCompositionInfo.mHasCompositionLayer) {
                /*
                 * m2mMPP's release fence for dst buffer was set to
                 * mExynosCompositionInfo.mAcquireFence by startPostProcessing()
                 * in validate time.
                 * This fence should be passed to display driver
                 * but it wont't because this frame will not be presented.
                 * So fence should be closed.
                 */
                mExynosCompositionInfo.mAcquireFence = fence_close(mExynosCompositionInfo.mAcquireFence,
                        this, FENCE_TYPE_DST_RELEASE, FENCE_IP_G2D);
            }

            for (size_t i = 0; i < mLayers.size(); i++) {
                exynos_image outImage;
                ExynosMPP* m2mMPP = mLayers[i]->mM2mMPP;
                if ((mLayers[i]->mValidateCompositionType == HWC2_COMPOSITION_DEVICE) &&
                    (m2mMPP != NULL) &&
                    (m2mMPP->mAssignedDisplay == this) &&
                    (m2mMPP->getDstImageInfo(&outImage) == NO_ERROR)) {
                    if (m2mMPP->mPhysicalType == MPP_MSC) {
                        fence_close(outImage.releaseFenceFd, this, FENCE_TYPE_DST_RELEASE, FENCE_IP_MSC);
                    } else if (m2mMPP->mPhysicalType == MPP_G2D) {
                        ALOGD("close(%d)", outImage.releaseFenceFd);
                        fence_close(outImage.releaseFenceFd, this, FENCE_TYPE_DST_RELEASE, FENCE_IP_G2D);
                    } else {
                        DISPLAY_LOGE("[%zu] layer has invalid mppType(%d)", i, m2mMPP->mPhysicalType);
                        fence_close(outImage.releaseFenceFd, this, FENCE_TYPE_DST_RELEASE, FENCE_IP_ALL);
                    }
                    m2mMPP->resetDstReleaseFence();
                    ALOGD("reset buf[%d], %d", m2mMPP->mCurrentDstBuf,
                            m2mMPP->mDstImgs[m2mMPP->mCurrentDstBuf].acrylicReleaseFenceFd);
                }
            }
        }
    }

    if (renderingState >= RENDERING_STATE_PRESENTED) {
        /* mAcquireFence is set after validate */
        mClientCompositionInfo.mAcquireFence = fence_close(mClientCompositionInfo.mAcquireFence, this,
                FENCE_TYPE_SRC_ACQUIRE, FENCE_IP_FB);
    }
}
void ExynosDisplay::closeFences()
{
    for (auto &config : mDpuData.configs) {
        if (config.acq_fence != -1)
            fence_close(config.acq_fence, this, FENCE_TYPE_SRC_ACQUIRE, FENCE_IP_DPP);
        config.acq_fence = -1;
        if (config.rel_fence >= 0)
            fence_close(config.rel_fence, this, FENCE_TYPE_SRC_RELEASE, FENCE_IP_DPP);
        config.rel_fence = -1;
    }
    for (auto &config : mDpuData.rcdConfigs) {
        if (config.acq_fence != -1)
            fence_close(config.acq_fence, this, FENCE_TYPE_SRC_ACQUIRE, FENCE_IP_DPP);
        config.acq_fence = -1;
        if (config.rel_fence >= 0)
            fence_close(config.rel_fence, this, FENCE_TYPE_SRC_RELEASE, FENCE_IP_DPP);
        config.rel_fence = -1;
    }
    for (size_t i = 0; i < mLayers.size(); i++) {
        if (mLayers[i]->mReleaseFence > 0) {
            fence_close(mLayers[i]->mReleaseFence, this,
                    FENCE_TYPE_SRC_RELEASE, FENCE_IP_LAYER);
            mLayers[i]->mReleaseFence = -1;
        }
        if ((mLayers[i]->mExynosCompositionType == HWC2_COMPOSITION_DEVICE) &&
            (mLayers[i]->mM2mMPP != NULL)) {
            mLayers[i]->mM2mMPP->closeFences();
        }
    }
    if (mExynosCompositionInfo.mHasCompositionLayer) {
        if (mExynosCompositionInfo.mM2mMPP == NULL)
        {
            DISPLAY_LOGE("There is exynos composition, but m2mMPP is NULL");
            return;
        }
        mExynosCompositionInfo.mM2mMPP->closeFences();
    }

    for (size_t i=0; i < mLayers.size(); i++) {
        if (mLayers[i]->mAcquireFence != -1) {
            mLayers[i]->mAcquireFence = fence_close(mLayers[i]->mAcquireFence, this,
                    FENCE_TYPE_SRC_ACQUIRE, FENCE_IP_LAYER);
        }
    }

    mExynosCompositionInfo.mAcquireFence = fence_close(mExynosCompositionInfo.mAcquireFence, this,
            FENCE_TYPE_SRC_ACQUIRE, FENCE_IP_G2D);
    mClientCompositionInfo.mAcquireFence = fence_close(mClientCompositionInfo.mAcquireFence, this,
            FENCE_TYPE_SRC_ACQUIRE, FENCE_IP_FB);

    if (mDpuData.retire_fence > 0)
        fence_close(mDpuData.retire_fence, this, FENCE_TYPE_RETIRE, FENCE_IP_DPP);
    mDpuData.retire_fence = -1;

    mLastRetireFence = fence_close(mLastRetireFence, this,  FENCE_TYPE_RETIRE, FENCE_IP_DPP);

    if (mDpuData.readback_info.rel_fence >= 0) {
        mDpuData.readback_info.rel_fence =
            fence_close(mDpuData.readback_info.rel_fence, this,
                    FENCE_TYPE_READBACK_RELEASE, FENCE_IP_FB);
    }
    if (mDpuData.readback_info.acq_fence >= 0) {
        mDpuData.readback_info.acq_fence =
            fence_close(mDpuData.readback_info.acq_fence, this,
                    FENCE_TYPE_READBACK_ACQUIRE, FENCE_IP_DPP);
    }
}

void ExynosDisplay::setHWCControl(uint32_t ctrl, int32_t val)
{
    switch (ctrl) {
        case HWC_CTL_ENABLE_COMPOSITION_CROP:
            mDisplayControl.enableCompositionCrop = (unsigned int)val;
            break;
        case HWC_CTL_ENABLE_EXYNOSCOMPOSITION_OPT:
            mDisplayControl.enableExynosCompositionOptimization = (unsigned int)val;
            break;
        case HWC_CTL_ENABLE_CLIENTCOMPOSITION_OPT:
            mDisplayControl.enableClientCompositionOptimization = (unsigned int)val;
            break;
        case HWC_CTL_USE_MAX_G2D_SRC:
            mDisplayControl.useMaxG2DSrc = (unsigned int)val;
            break;
        case HWC_CTL_ENABLE_HANDLE_LOW_FPS:
            mDisplayControl.handleLowFpsLayers = (unsigned int)val;
            break;
        case HWC_CTL_ENABLE_EARLY_START_MPP:
            mDisplayControl.earlyStartMPP = (unsigned int)val;
            break;
        default:
            ALOGE("%s: unsupported HWC_CTL (%d)", __func__, ctrl);
            break;
    }
}

int32_t ExynosDisplay::getHdrCapabilities(uint32_t* outNumTypes,
        int32_t* outTypes, float* outMaxLuminance,
        float* outMaxAverageLuminance, float* outMinLuminance)
{
    DISPLAY_LOGD(eDebugHWC, "HWC2: %s, %d", __func__, __LINE__);

    if (outNumTypes == NULL || outMaxLuminance == NULL ||
            outMaxAverageLuminance == NULL || outMinLuminance == NULL) {
        return HWC2_ERROR_BAD_PARAMETER;
    }

    if (outTypes == NULL) {
        /*
         * This function is always called twice.
         * outTypes is NULL in the first call and
         * outType is valid pointer in the second call.
         * Get information only in the first call.
         * Use saved information in the second call.
         */
        if (mDisplayInterface->updateHdrCapabilities() != NO_ERROR)
            return HWC2_ERROR_BAD_CONFIG;
    }

    *outMaxLuminance = mMaxLuminance;
    *outMaxAverageLuminance = mMaxAverageLuminance;
    *outMinLuminance = mMinLuminance;

    if (outTypes == NULL) {
        *outNumTypes = mHdrTypes.size();
    } else {
        if (*outNumTypes != mHdrTypes.size()) {
            ALOGE("%s:: Invalid parameter (outNumTypes: %d, mHdrTypes size: %zu",
                    __func__, *outNumTypes, mHdrTypes.size());
            return HWC2_ERROR_BAD_PARAMETER;
        }
        for(uint32_t i = 0; i < *outNumTypes; i++) {
            outTypes[i] = mHdrTypes[i];
        }
    }
    return HWC2_ERROR_NONE;
}

int32_t ExynosDisplay::getMountOrientation(HwcMountOrientation *orientation)
{
    if (!orientation)
        return HWC2_ERROR_BAD_PARAMETER;

    *orientation = mMountOrientation;
    return HWC2_ERROR_NONE;
}

std::optional<VrrConfig_t> ExynosDisplay::getVrrConfigs(hwc2_config_t config) {
    if (isBadConfig(config)) {
        return std::nullopt;
    }
    return mDisplayConfigs[config].vrrConfig;
}

// Support DDI scalser
void ExynosDisplay::setDDIScalerEnable(int __unused width, int __unused height) {
}

int ExynosDisplay::getDDIScalerMode(int __unused width, int __unused height) {
    return 1; // WQHD
}

void ExynosDisplay::increaseMPPDstBufIndex() {
    for (size_t i=0; i < mLayers.size(); i++) {
        if((mLayers[i]->mExynosCompositionType == HWC2_COMPOSITION_DEVICE) &&
           (mLayers[i]->mM2mMPP != NULL)) {
            mLayers[i]->mM2mMPP->increaseDstBuffIndex();
        }
    }

    if ((mExynosCompositionInfo.mHasCompositionLayer) &&
        (mExynosCompositionInfo.mM2mMPP != NULL)) {
        mExynosCompositionInfo.mM2mMPP->increaseDstBuffIndex();
    }
}

int32_t ExynosDisplay::getReadbackBufferAttributes(int32_t* /*android_pixel_format_t*/ outFormat,
        int32_t* /*android_dataspace_t*/ outDataspace)
{
    int32_t ret = mDisplayInterface->getReadbackBufferAttributes(outFormat, outDataspace);
    if (ret == NO_ERROR) {
        /* Interface didn't specific set dataspace */
        if (*outDataspace == HAL_DATASPACE_UNKNOWN)
            *outDataspace = colorModeToDataspace(mColorMode);
        /* Set default value */
        if (*outDataspace == HAL_DATASPACE_UNKNOWN)
            *outDataspace = HAL_DATASPACE_V0_SRGB;

        mDisplayControl.readbackSupport = true;
        ALOGI("readback info: format(0x%8x), dataspace(0x%8x)", *outFormat, *outDataspace);
    } else {
        mDisplayControl.readbackSupport = false;
        ALOGI("readback is not supported, ret(%d)", ret);
        ret = HWC2_ERROR_UNSUPPORTED;
    }
    return ret;
}

int32_t ExynosDisplay::setReadbackBuffer(buffer_handle_t buffer,
        int32_t releaseFence, bool requestedService)
{
    Mutex::Autolock lock(mDisplayMutex);
    int32_t ret = NO_ERROR;

    if (buffer == nullptr)
        return HWC2_ERROR_BAD_PARAMETER;

    if (mDisplayControl.readbackSupport) {
        mDpuData.enable_readback = true;
    } else {
        DISPLAY_LOGE("readback is not supported but setReadbackBuffer is called, buffer(%p), releaseFence(%d)",
                buffer, releaseFence);
        if (releaseFence >= 0)
            releaseFence = fence_close(releaseFence, this,
                    FENCE_TYPE_READBACK_RELEASE, FENCE_IP_FB);
        mDpuData.enable_readback = false;
        ret = HWC2_ERROR_UNSUPPORTED;
    }
    setReadbackBufferInternal(buffer, releaseFence, requestedService);
    return ret;
}

void ExynosDisplay::setReadbackBufferInternal(buffer_handle_t buffer,
        int32_t releaseFence, bool requestedService)
{
    if (mDpuData.readback_info.rel_fence >= 0) {
        mDpuData.readback_info.rel_fence =
            fence_close(mDpuData.readback_info.rel_fence, this,
                    FENCE_TYPE_READBACK_RELEASE, FENCE_IP_FB);
        DISPLAY_LOGE("previous readback release fence is not delivered to display device");
    }
    if (releaseFence >= 0) {
        setFenceInfo(releaseFence, this, FENCE_TYPE_READBACK_RELEASE, FENCE_IP_FB,
                     HwcFenceDirection::FROM);
    }
    mDpuData.readback_info.rel_fence = releaseFence;

    if (buffer != NULL)
        mDpuData.readback_info.handle = buffer;

    mDpuData.readback_info.requested_from_service = requestedService;
}

int32_t ExynosDisplay::getReadbackBufferFence(int32_t* outFence)
{
    /*
     * acq_fence was not set or
     * it was already closed by error or frame skip
     */
    if (mDpuData.readback_info.acq_fence < 0) {
        *outFence = -1;
        return HWC2_ERROR_UNSUPPORTED;
    }

    *outFence = mDpuData.readback_info.acq_fence;
    /* Fence will be closed by caller of this function */
    mDpuData.readback_info.acq_fence = -1;
    return NO_ERROR;
}

int32_t ExynosDisplay::setReadbackBufferAcqFence(int32_t acqFence) {
    if (mDpuData.readback_info.acq_fence >= 0) {
        mDpuData.readback_info.acq_fence =
            fence_close(mDpuData.readback_info.acq_fence, this,
                    FENCE_TYPE_READBACK_ACQUIRE, FENCE_IP_DPP);
        DISPLAY_LOGE("previous readback out fence is not delivered to framework");
    }
    mDpuData.readback_info.acq_fence = acqFence;
    if (acqFence >= 0) {
        /*
         * Requtester of readback will get acqFence after presentDisplay
         * so validateFences should not check this fence
         * in presentDisplay so this function sets pendingAllowed parameter.
         */
        setFenceInfo(acqFence, this, FENCE_TYPE_READBACK_ACQUIRE, FENCE_IP_DPP,
                     HwcFenceDirection::FROM, true);
    }

    return NO_ERROR;
}

void ExynosDisplay::initDisplayInterface(uint32_t __unused interfaceType)
{
    mDisplayInterface = std::make_unique<ExynosDisplayInterface>();
    mDisplayInterface->init(this);
}

void ExynosDisplay::traceLayerTypes() {
    size_t g2d_count = 0;
    size_t dpu_count = 0;
    size_t gpu_count = 0;
    size_t skip_count = 0;
    size_t rcd_count = 0;
    for(auto const& layer: mLayers) {
        switch (layer->mExynosCompositionType) {
            case HWC2_COMPOSITION_EXYNOS:
                g2d_count++;
                break;
            case HWC2_COMPOSITION_CLIENT:
                if (layer->mCompositionType == HWC2_COMPOSITION_DEVICE) {
                    skip_count++;
                } else {
                    gpu_count++;
                }
                break;
            case HWC2_COMPOSITION_DEVICE:
                dpu_count++;
                break;
            case HWC2_COMPOSITION_DISPLAY_DECORATION:
                ++rcd_count;
                break;
            default:
                ALOGW("%s: Unknown layer composition type: %d", __func__,
                      layer->mExynosCompositionType);
                break;
        }
    }
    DISPLAY_ATRACE_INT("HWComposer: DPU Layer", dpu_count);
    DISPLAY_ATRACE_INT("HWComposer: G2D Layer", g2d_count);
    DISPLAY_ATRACE_INT("HWComposer: GPU Layer", gpu_count);
    DISPLAY_ATRACE_INT("HWComposer: RCD Layer", rcd_count);
    DISPLAY_ATRACE_INT("HWComposer: DPU Cached Layer", skip_count);
    DISPLAY_ATRACE_INT("HWComposer: SF Cached Layer", mIgnoreLayers.size());
    DISPLAY_ATRACE_INT("HWComposer: Total Layer", mLayers.size() + mIgnoreLayers.size());
}

void ExynosDisplay::updateBrightnessState() {
    static constexpr float kMaxCll = 10000.0;
    bool clientRgbHdr = false;
    bool instantHbm = false;
    bool sdrDim = false;
    BrightnessController::HdrLayerState hdrState = BrightnessController::HdrLayerState::kHdrNone;

    for (size_t i = 0; i < mLayers.size(); i++) {
        if (mLayers[i]->mIsHdrLayer) {
            // TODO(longling): Below code block for RGB HDR is obsolete also
            // need some fix (mExynosCompositionType is invalid at this time)
            if (mLayers[i]->isLayerFormatRgb()) {
                auto meta = mLayers[i]->getMetaParcel();
                if ((meta != nullptr) && (meta->eType & VIDEO_INFO_TYPE_HDR_STATIC) &&
                    meta->sHdrStaticInfo.sType1.mMaxContentLightLevel >= kMaxCll) {
                    // if there are one or more such layers and any one of them
                    // is composed by GPU, we won't dim sdr layers
                    if (mLayers[i]->mExynosCompositionType == HWC2_COMPOSITION_CLIENT) {
                        clientRgbHdr = true;
                    } else {
                        instantHbm = true;
                    }
                }
            }

            // If any HDR layer is large, keep the state as kHdrLarge
            if (hdrState != BrightnessController::HdrLayerState::kHdrLarge
                && mLayers[i]->getDisplayFrameArea() >= mHdrFullScrenAreaThreshold) {
                hdrState = BrightnessController::HdrLayerState::kHdrLarge;
            } else if (hdrState == BrightnessController::HdrLayerState::kHdrNone) {
                hdrState = BrightnessController::HdrLayerState::kHdrSmall;
            } // else keep the state (kHdrLarge or kHdrSmall) unchanged.
        }
        // SDR layers could be kept dimmed for a while after HDR is gone (DM
        // will animate the display brightness from HDR brightess to SDR brightness).
        if (mLayers[i]->mBrightness < 1.0) {
            sdrDim = true;
        }
    }

    if (mBrightnessController) {
        mBrightnessController->updateFrameStates(hdrState, sdrDim);
        mBrightnessController->processInstantHbm(instantHbm && !clientRgbHdr);
        mBrightnessController->updateCabcMode();
    }
}

void ExynosDisplay::cleanupAfterClientDeath() {
    // Invalidate the client target buffer because it will be freed when the client dies
    mClientCompositionInfo.mTargetBuffer = NULL;
    // Invalidate the skip static flag so that we have to get a new target buffer first
    // before we can skip the static layers
    mClientCompositionInfo.mSkipStaticInitFlag = false;
    mClientCompositionInfo.mSkipFlag = false;
}

int32_t ExynosDisplay::flushDisplayBrightnessChange() {
    if (mBrightnessController) {
        setMinIdleRefreshRate(0, RrThrottleRequester::BRIGHTNESS);
        if (mOperationRateManager) {
            mOperationRateManager->onBrightness(mBrightnessController->getBrightnessLevel());
            handleTargetOperationRate();
        }
        return mBrightnessController->applyPendingChangeViaSysfs(mVsyncPeriod);
    }
    return NO_ERROR;
}

// we can cache the value once it is known to avoid the lock after boot
// we also only actually check once per frame to keep the value internally consistent
bool ExynosDisplay::usePowerHintSession() {
    if (!mUsePowerHintSession.has_value() && mPowerHalHint.checkPowerHintSessionReady()) {
        mUsePowerHintSession = mPowerHalHint.usePowerHintSession();
    }
    return mUsePowerHintSession.value_or(false);
}

nsecs_t ExynosDisplay::getExpectedPresentTime(nsecs_t startTime) {
    ExynosDisplay *primaryDisplay = mDevice->getDisplay(HWC_DISPLAY_PRIMARY);
    if (primaryDisplay) {
        nsecs_t out = primaryDisplay->getPendingExpectedPresentTime();
        if (out != 0) {
            return out;
        }
    }
    return getPredictedPresentTime(startTime);
}

nsecs_t ExynosDisplay::getPredictedPresentTime(nsecs_t startTime) {
    auto lastRetireFenceSignalTime = getSignalTime(mLastRetireFence);
    auto expectedPresentTime = startTime - 1;
    if (lastRetireFenceSignalTime != SIGNAL_TIME_INVALID &&
        lastRetireFenceSignalTime != SIGNAL_TIME_PENDING) {
        expectedPresentTime = lastRetireFenceSignalTime + mVsyncPeriod;
        mRetireFencePreviousSignalTime = lastRetireFenceSignalTime;
    } else if (sync_wait(mLastRetireFence, 0) < 0) {
        // if last retire fence was not signaled, then try previous signal time
        if (mRetireFencePreviousSignalTime.has_value()) {
            expectedPresentTime = *mRetireFencePreviousSignalTime + 2 * mVsyncPeriod;
        }
        // the last retire fence acquire time can be useful where the previous fence signal time may
        // not be recorded yet, but it's only accurate when there is fence wait time
        if (mRetireFenceAcquireTime.has_value()) {
            expectedPresentTime =
                    max(expectedPresentTime, *mRetireFenceAcquireTime + 2 * mVsyncPeriod);
        }
    }
    if (expectedPresentTime < startTime) {
        ALOGV("Could not predict expected present time, fall back on target of one vsync");
        expectedPresentTime = startTime + mVsyncPeriod;
    }
    return expectedPresentTime;
}

nsecs_t ExynosDisplay::getSignalTime(int32_t fd) const {
    if (fd == -1) {
        return SIGNAL_TIME_INVALID;
    }

    struct sync_file_info *finfo = sync_file_info(fd);
    if (finfo == nullptr) {
        return SIGNAL_TIME_INVALID;
    }

    if (finfo->status != 1) {
        const auto status = finfo->status;
        sync_file_info_free(finfo);
        return status < 0 ? SIGNAL_TIME_INVALID : SIGNAL_TIME_PENDING;
    }

    uint64_t timestamp = 0;
    struct sync_fence_info *pinfo = sync_get_fence_info(finfo);
    for (size_t i = 0; i < finfo->num_fences; i++) {
        if (pinfo[i].timestamp_ns > timestamp) {
            timestamp = pinfo[i].timestamp_ns;
        }
    }

    sync_file_info_free(finfo);
    return nsecs_t(timestamp);
}

std::optional<nsecs_t> ExynosDisplay::getPredictedDuration(bool duringValidation) {
    AveragesKey beforeFenceKey(mLayers.size(), duringValidation, true);
    AveragesKey afterFenceKey(mLayers.size(), duringValidation, false);
    if (mRollingAverages.count(beforeFenceKey) == 0 || mRollingAverages.count(afterFenceKey) == 0) {
        return std::nullopt;
    }
    nsecs_t beforeReleaseFence = mRollingAverages[beforeFenceKey].average;
    nsecs_t afterReleaseFence = mRollingAverages[afterFenceKey].average;
    return std::make_optional(afterReleaseFence + beforeReleaseFence);
}

void ExynosDisplay::updateAverages(nsecs_t endTime) {
    if (!mRetireFenceWaitTime.has_value() || !mRetireFenceAcquireTime.has_value()) {
        return;
    }
    nsecs_t beforeFenceTime =
            mValidationDuration.value_or(0) + (*mRetireFenceWaitTime - mPresentStartTime);
    nsecs_t afterFenceTime = endTime - *mRetireFenceAcquireTime;
    mRollingAverages[AveragesKey(mLayers.size(), mValidationDuration.has_value(), true)].insert(
            beforeFenceTime);
    mRollingAverages[AveragesKey(mLayers.size(), mValidationDuration.has_value(), false)].insert(
            afterFenceTime);
}

int32_t ExynosDisplay::getRCDLayerSupport(bool &outSupport) const {
    outSupport = mDebugRCDLayerEnabled && mDpuData.rcdConfigs.size() > 0;
    return NO_ERROR;
}

int32_t ExynosDisplay::setDebugRCDLayerEnabled(bool enable) {
    mDebugRCDLayerEnabled = enable;
    return NO_ERROR;
}

int32_t ExynosDisplay::getDisplayIdleTimerSupport(bool &outSupport) {
    return mDisplayInterface->getDisplayIdleTimerSupport(outSupport);
}

int32_t ExynosDisplay::getDisplayMultiThreadedPresentSupport(bool &outSupport) {
    outSupport = mDisplayControl.multiThreadedPresent;
    return NO_ERROR;
}

bool ExynosDisplay::isMixedComposition() {
    for (size_t i = 0; i < mLayers.size(); i++) {
        if (mLayers[i]->mBrightness < 1.0) {
            return true;
        }
    }
    return false;
}

int ExynosDisplay::lookupDisplayConfigs(const int32_t &width,
                                        const int32_t &height,
                                        const int32_t &fps,
                                        const int32_t &vsyncRate,
                                        int32_t *outConfig) {
    if (!fps || !vsyncRate)
        return HWC2_ERROR_BAD_CONFIG;

    constexpr auto nsecsPerMs = std::chrono::nanoseconds(1ms).count();
    const auto vsyncPeriod = nsecsPerSec / vsyncRate;

    for (auto const& [config, mode] : mDisplayConfigs) {
        long delta = abs(vsyncPeriod - mode.vsyncPeriod);
        if ((width == 0 || width == mode.width) && (height == 0 || height == mode.height) &&
            (delta < nsecsPerMs) && (fps == mode.refreshRate)) {
            ALOGD("%s: found display config for mode: %dx%d@%d:%d config=%d",
                 __func__, width, height, fps, vsyncRate, config);
            *outConfig = config;
            return HWC2_ERROR_NONE;
        }
    }

    return HWC2_ERROR_BAD_CONFIG;
}

int ExynosDisplay::lookupDisplayConfigsRelaxed(const int32_t &width,
                                               const int32_t &height,
                                               const int32_t &fps,
                                               int32_t *outConfig) {
    if (fps <= 1)
        return HWC2_ERROR_BAD_CONFIG;

    const auto vsyncPeriod = nsecsPerSec / fps;
    const auto vsyncPeriodMin = nsecsPerSec / (fps + 1);
    const auto vsyncPeriodMax = nsecsPerSec / (fps - 1);

    // Search for exact match in resolution and vsync
    for (auto const& [config, mode] : mDisplayConfigs) {
        if (mode.width == width && mode.height == height && mode.vsyncPeriod == vsyncPeriod) {
            ALOGD("%s: found exact match for mode %dx%d@%d -> config=%d",
                 __func__, width, height, fps, config);
            *outConfig = config;
            return HWC2_ERROR_NONE;
        }
    }

    // Search for exact match in resolution, allow small variance in vsync
    for (auto const& [config, mode] : mDisplayConfigs) {
        if (mode.width == width && mode.height == height &&
            mode.vsyncPeriod >= vsyncPeriodMin && mode.vsyncPeriod <= vsyncPeriodMax) {
            ALOGD("%s: found close match for mode %dx%d@%d -> config=%d",
                 __func__, width, height, fps, config);
            *outConfig = config;
            return HWC2_ERROR_NONE;
        }
    }

    // Search for smaller resolution, allow small variance in vsync
    // mDisplayConfigs is sorted, so this will give the largest available option
    for (auto const& [config, mode] : mDisplayConfigs) {
        if (mode.width <= width && mode.height <= height &&
            mode.vsyncPeriod >= vsyncPeriodMin && mode.vsyncPeriod <= vsyncPeriodMax) {
            ALOGD("%s: found relaxed match for mode %dx%d@%d -> config=%d",
                 __func__, width, height, fps, config);
            *outConfig = config;
            return HWC2_ERROR_NONE;
        }
    }

    return HWC2_ERROR_BAD_CONFIG;
}

FILE *ExynosDisplay::RotatingLogFileWriter::openLogFile(const std::string &filename,
                                                        const std::string &mode) {
    FILE *file = nullptr;
    auto fullpath = std::string(ERROR_LOG_PATH0) + "/" + filename;
    file = fopen(fullpath.c_str(), mode.c_str());
    if (file != nullptr) {
        return file;
    }
    ALOGE("Fail to open file %s, error: %s", fullpath.c_str(), strerror(errno));
    fullpath = std::string(ERROR_LOG_PATH1) + "/" + filename;
    file = fopen(fullpath.c_str(), mode.c_str());
    if (file == nullptr) {
        ALOGE("Fail to open file %s, error: %s", fullpath.c_str(), strerror(errno));
    }
    return file;
}

std::optional<nsecs_t> ExynosDisplay::RotatingLogFileWriter::getLastModifiedTimestamp(
        const std::string &filename) {
    struct stat fileStat;
    auto fullpath = std::string(ERROR_LOG_PATH0) + "/" + filename;
    if (stat(fullpath.c_str(), &fileStat) == 0) {
        return fileStat.st_mtim.tv_sec * nsecsPerSec + fileStat.st_mtim.tv_nsec;
    }
    fullpath = std::string(ERROR_LOG_PATH1) + "/" + filename;
    if (stat(fullpath.c_str(), &fileStat) == 0) {
        return fileStat.st_mtim.tv_sec * nsecsPerSec + fileStat.st_mtim.tv_nsec;
    }
    return std::nullopt;
}

bool ExynosDisplay::RotatingLogFileWriter::chooseOpenedFile() {
    if (mLastFileIndex < 0) {
        // HWC could be restarted, so choose to open new file or continue the last modified file
        int chosenIndex = 0;
        nsecs_t lastModifTimestamp = 0;
        for (int i = 0; i < mMaxFileCount; ++i) {
            auto timestamp = getLastModifiedTimestamp(mPrefixName + std::to_string(i) + mExtension);
            if (!timestamp.has_value()) {
                chosenIndex = i;
                break;
            }
            if (i == 0 || lastModifTimestamp < *timestamp) {
                chosenIndex = i;
                lastModifTimestamp = *timestamp;
            }
        }
        auto filename = mPrefixName + std::to_string(chosenIndex) + mExtension;
        mFile = openLogFile(filename, "ab");
        if (mFile == nullptr) {
            ALOGE("Unable to open log file for %s", filename.c_str());
            return false;
        }
        mLastFileIndex = chosenIndex;
    }

    // Choose to use the same last file or move on to the next file
    for (int i = 0; i < 2; ++i) {
        if (mFile == nullptr) {
            mFile = openLogFile(mPrefixName + std::to_string(mLastFileIndex) + mExtension,
                                (i == 0) ? "ab" : "wb");
        }
        if (mFile != nullptr) {
            auto fileSize = ftell(mFile);
            if (fileSize < mThresholdSizePerFile) return true;
            fclose(mFile);
            mFile = nullptr;
        }
        mLastFileIndex = (mLastFileIndex + 1) % mMaxFileCount;
    }
    return false;
}

void ExynosDisplay::invalidate() {
    mDevice->onRefresh(mDisplayId);
}

bool ExynosDisplay::checkHotplugEventUpdated(bool &hpdStatus) {
    if (mDisplayInterface == nullptr) {
        ALOGW("%s: mDisplayInterface == nullptr", __func__);
        return false;
    }

    hpdStatus = mDisplayInterface->readHotplugStatus();

    int hotplugErrorCode = mDisplayInterface->readHotplugErrorCode();

    DISPLAY_LOGI("[%s] mDisplayId(%d), mIndex(%d), HPD Status(previous :%d, current : %d), "
                 "hotplugErrorCode=%d",
                 __func__, mDisplayId, mIndex, mHpdStatus, hpdStatus, hotplugErrorCode);

    return (mHpdStatus != hpdStatus) || (hotplugErrorCode != 0);
}

void ExynosDisplay::handleHotplugEvent(bool hpdStatus) {
    mHpdStatus = hpdStatus;
}

void ExynosDisplay::hotplug() {
    int hotplugErrorCode = mDisplayInterface->readHotplugErrorCode();
    mDisplayInterface->resetHotplugErrorCode();
    mDevice->onHotPlug(mDisplayId, mHpdStatus, hotplugErrorCode);
    ALOGI("HPD callback(%s, mDisplayId %d, hotplugErrorCode=%d) was called",
          mHpdStatus ? "connection" : "disconnection", mDisplayId, hotplugErrorCode);
}

void ExynosDisplay::contentProtectionUpdated(HdcpLevels hdcpLevels) {
    mDevice->onContentProtectionUpdated(mDisplayId, hdcpLevels);
}

ExynosDisplay::SysfsBasedRRIHandler::SysfsBasedRRIHandler(ExynosDisplay* display)
      : mDisplay(display),
        mLastRefreshRate(0),
        mLastCallbackTime(0),
        mIgnoringLastUpdate(false),
        mCanIgnoreIncreaseUpdate(false) {}

int32_t ExynosDisplay::SysfsBasedRRIHandler::init() {
    auto path = String8::format(kRefreshRateStatePathFormat, mDisplay->mIndex);
    mFd.Set(open(path.c_str(), O_RDONLY));
    if (mFd.get() < 0) {
        ALOGE("Failed to open sysfs(%s) for refresh rate debug event: %s", path.c_str(),
              strerror(errno));
        return -errno;
    }
    auto ret = mDisplay->mDevice->mDeviceInterface->registerSysfsEventHandler(shared_from_this());
    if (ret != NO_ERROR) {
        ALOGE("%s: Failed to register sysfs event handler: %d", __func__, ret);
        return ret;
    }
    setAllowWakeup(true);
    // Call the callback immediately
    handleSysfsEvent();
    return NO_ERROR;
}

int32_t ExynosDisplay::SysfsBasedRRIHandler::disable() {
    setAllowWakeup(false);
    return mDisplay->mDevice->mDeviceInterface->unregisterSysfsEventHandler(getFd());
}

void ExynosDisplay::SysfsBasedRRIHandler::updateRefreshRateLocked(int refreshRate) {
    ATRACE_CALL();
    ATRACE_INT("Refresh rate indicator event", refreshRate);
    // Ignore refresh rate increase that is caused by refresh rate indicator update but there's
    // no update for the other layers
    if (mCanIgnoreIncreaseUpdate && refreshRate > mLastRefreshRate && mLastRefreshRate > 0 &&
        mDisplay->getLastLayerUpdateTime() < mLastCallbackTime) {
        mIgnoringLastUpdate = true;
        mCanIgnoreIncreaseUpdate = false;
        return;
    }
    mIgnoringLastUpdate = false;
    if (refreshRate == mLastRefreshRate) {
        return;
    }
    mLastRefreshRate = refreshRate;
    mLastCallbackTime = systemTime(CLOCK_MONOTONIC);
    ATRACE_INT("Refresh rate indicator callback", mLastRefreshRate);
    mDisplay->mDevice->onRefreshRateChangedDebug(mDisplay->mDisplayId, s2ns(1) / mLastRefreshRate);
    mCanIgnoreIncreaseUpdate = true;
}

void ExynosDisplay::SysfsBasedRRIHandler::handleSysfsEvent() {
    ATRACE_CALL();
    std::scoped_lock lock(mMutex);

    char buffer[1024];
    lseek(mFd.get(), 0, SEEK_SET);
    int ret = read(mFd.get(), &buffer, sizeof(buffer));
    if (ret < 0) {
        ALOGE("%s: Failed to read refresh rate from fd %d: %s", __func__, mFd.get(),
              strerror(errno));
        return;
    }
    std::string_view bufferView(buffer);
    auto pos = bufferView.find('@');
    if (pos == std::string::npos) {
        ALOGE("%s: Failed to parse refresh rate event (invalid format)", __func__);
        return;
    }
    int refreshRate = 0;
    std::from_chars(bufferView.data() + pos + 1, bufferView.data() + bufferView.size() - 1,
                    refreshRate);
    updateRefreshRateLocked(refreshRate);
}

void ExynosDisplay::SysfsBasedRRIHandler::updateRefreshRate(int refreshRate) {
    std::scoped_lock lock(mMutex);
    updateRefreshRateLocked(refreshRate);
}

void ExynosDisplay::SysfsBasedRRIHandler::setAllowWakeup(const bool enabled) {
    auto path = String8::format(kRefreshRateAllowWakeupStateChangePathFormat, mDisplay->mIndex);
    std::ofstream ofs(path);
    if (ofs.is_open()) {
        ofs << enabled;
        if (ofs.fail()) {
            ALOGW("%s: Failed to write %d to allow wakeup node: %d", __func__, enabled, errno);
        }
    } else {
        ALOGW("%s: Failed to open allow wakeup node: %d", __func__, errno);
    }
}

int32_t ExynosDisplay::setRefreshRateChangedCallbackDebugEnabled(bool enabled) {
    if ((!!mRefreshRateIndicatorHandler) == enabled) {
        ALOGW("%s: RefreshRateChangedCallbackDebug is already %s", __func__,
              enabled ? "enabled" : "disabled");
        return NO_ERROR;
    }
    int32_t ret = NO_ERROR;
    if (enabled) {
        if (mType == HWC_DISPLAY_PRIMARY) {
            mRefreshRateIndicatorHandler = std::make_shared<SysfsBasedRRIHandler>(this);
        } else {
            mRefreshRateIndicatorHandler = std::make_shared<ActiveConfigBasedRRIHandler>(this);
        }
        if (!mRefreshRateIndicatorHandler) {
            ALOGE("%s: Failed to create refresh rate debug handler", __func__);
            return -ENOMEM;
        }
        ret = mRefreshRateIndicatorHandler->init();
        if (ret != NO_ERROR) {
            ALOGE("%s: Failed to initialize refresh rate debug handler: %d", __func__, ret);
            mRefreshRateIndicatorHandler.reset();
            return ret;
        }
    } else {
        ret = mRefreshRateIndicatorHandler->disable();
        mRefreshRateIndicatorHandler.reset();
    }
    return ret;
}

nsecs_t ExynosDisplay::getLastLayerUpdateTime() {
    Mutex::Autolock lock(mDRMutex);
    nsecs_t time = 0;
    for (size_t i = 0; i < mLayers.size(); ++i) {
        // The update from refresh rate indicator layer should be ignored
        if (mLayers[i]->mRequestedCompositionType == HWC2_COMPOSITION_REFRESH_RATE_INDICATOR)
            continue;
        time = max(time, mLayers[i]->mLastUpdateTime);
    }
    return time;
}

void ExynosDisplay::SysfsBasedRRIHandler::checkOnPresentDisplay() {
    // Update refresh rate indicator if the last update event is ignored to make sure that
    // the refresh rate caused by the current frame update will be applied immediately since
    // we may not receive the sysfs event if the refresh rate is the same as the last ignored one.
    if (!mIgnoringLastUpdate) {
        return;
    }
    handleSysfsEvent();
}

ExynosDisplay::ActiveConfigBasedRRIHandler::ActiveConfigBasedRRIHandler(ExynosDisplay* display)
      : mDisplay(display), mLastRefreshRate(0) {}

int32_t ExynosDisplay::ActiveConfigBasedRRIHandler::init() {
    updateRefreshRate(mDisplay->mRefreshRate);
    return NO_ERROR;
}

void ExynosDisplay::ActiveConfigBasedRRIHandler::updateRefreshRate(int refreshRate) {
    if (mLastRefreshRate == refreshRate) {
        return;
    }
    mLastRefreshRate = refreshRate;
    mDisplay->mDevice->onRefreshRateChangedDebug(mDisplay->mDisplayId, s2ns(1) / refreshRate);
}

void ExynosDisplay::ActiveConfigBasedRRIHandler::checkOnSetActiveConfig(int refreshRate) {
    updateRefreshRate(refreshRate);
}

bool ExynosDisplay::needUpdateRRIndicator() {
    uint64_t exclude = GEOMETRY_LAYER_TYPE_CHANGED;
    return (mGeometryChanged & ~exclude) > 0 || mBufferUpdates > 0;
}

uint32_t ExynosDisplay::getPeakRefreshRate() {
    uint32_t opRate = mBrightnessController->getOperationRate();
    return opRate ?: mPeakRefreshRate;
}

VsyncPeriodNanos ExynosDisplay::getVsyncPeriod(const int32_t config) {
    const auto& it = mDisplayConfigs.find(config);
    if (it == mDisplayConfigs.end()) return 0;
    return it->second.vsyncPeriod;
}

uint32_t ExynosDisplay::getRefreshRate(const int32_t config) {
    const auto& it = mDisplayConfigs.find(config);
    if (it == mDisplayConfigs.end()) return 0;
    return it->second.refreshRate;
}

uint32_t ExynosDisplay::getConfigId(const int32_t refreshRate, const int32_t width,
                                    const int32_t height) {
    for (auto entry : mDisplayConfigs) {
        auto config = entry.first;
        auto displayCfg = entry.second;
        if (getRefreshRate(config) == refreshRate && displayCfg.width == width &&
            displayCfg.height == height) {
            return config;
        }
    }
    return UINT_MAX;
}

void ExynosDisplay::resetColorMappingInfoForClientComp() {
    if (mType != HWC_DISPLAY_PRIMARY) return;

    int32_t ret = NO_ERROR;
    for (uint32_t i = 0; i < mLayers.size(); i++) {
        ExynosLayer *layer = mLayers[i];
        if (layer->mPrevValidateCompositionType != HWC2_COMPOSITION_CLIENT &&
            layer->mValidateCompositionType == HWC2_COMPOSITION_CLIENT) {
            if ((ret = resetColorMappingInfo(layer)) != NO_ERROR) {
                DISPLAY_LOGE("%s:: resetColorMappingInfo() idx=%d error(%d)", __func__, i, ret);
            }
        }
    }

    // when no GPU composition, resets the mapping info of client composition info
    if (mClientCompositionInfo.mPrevHasCompositionLayer &&
        !mClientCompositionInfo.mHasCompositionLayer) {
        if ((ret = resetColorMappingInfo(&mClientCompositionInfo)) != NO_ERROR) {
            DISPLAY_LOGE("%s:: resetColorMappingInfo() for client target error(%d)", __func__, ret);
        }
    }
}

void ExynosDisplay::storePrevValidateCompositionType() {
    for (uint32_t i = 0; i < mIgnoreLayers.size(); i++) {
        ExynosLayer *layer = mIgnoreLayers[i];
        layer->mPrevValidateCompositionType = layer->mValidateCompositionType;
    }

    for (uint32_t i = 0; i < mLayers.size(); i++) {
        ExynosLayer *layer = mLayers[i];
        layer->mPrevValidateCompositionType = layer->mValidateCompositionType;
    }
    mClientCompositionInfo.mPrevHasCompositionLayer = mClientCompositionInfo.mHasCompositionLayer;
}

displaycolor::DisplayType ExynosDisplay::getDcDisplayType() const {
    switch (mType) {
        case HWC_DISPLAY_PRIMARY:
            return mIndex == 0 ? displaycolor::DisplayType::DISPLAY_PRIMARY
                               : displaycolor::DisplayType::DISPLAY_SECONDARY;
        case HWC_DISPLAY_EXTERNAL:
            return displaycolor::DisplayType::DISPLAY_EXTERNAL;
        case HWC_DISPLAY_VIRTUAL:
        default:
            DISPLAY_LOGE("%s: Unsupported display type(%d)", __func__, mType);
            assert(false);
            return displaycolor::DisplayType::DISPLAY_PRIMARY;
    }
}
