/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include <processgroup/sched_policy.h>

#include "VariableRefreshRateController.h"

#include <android-base/logging.h>
#include <sync/sync.h>
#include <utils/Trace.h>

#include "ExynosHWCHelper.h"
#include "Utils.h"
#include "drmmode.h"

#include <chrono>
#include <tuple>

#include "RefreshRateCalculator/RefreshRateCalculatorFactory.h"

#include "display/DisplayContextProviderFactory.h"

namespace android::hardware::graphics::composer {

namespace {

using android::hardware::graphics::composer::VrrControllerEventType;

}

static OperationSpeedMode getOperationSpeedModeWrapper(void* host) {
    VariableRefreshRateController* controller =
            reinterpret_cast<VariableRefreshRateController*>(host);
    return controller->getOperationSpeedMode();
}

static BrightnessMode getBrightnessModeWrapper(void* host) {
    VariableRefreshRateController* controller =
            reinterpret_cast<VariableRefreshRateController*>(host);
    return controller->getBrightnessMode();
}

static int getBrightnessNitsWrapper(void* host) {
    VariableRefreshRateController* controller =
            reinterpret_cast<VariableRefreshRateController*>(host);
    return controller->getBrightnessNits();
}

static const char* getDisplayFileNodePathWrapper(void* host) {
    VariableRefreshRateController* controller =
            reinterpret_cast<VariableRefreshRateController*>(host);
    return controller->getDisplayFileNodePath();
}

static int getEstimateVideoFrameRateWrapper(void* host) {
    VariableRefreshRateController* controller =
            reinterpret_cast<VariableRefreshRateController*>(host);
    return controller->getEstimatedVideoFrameRate();
}

static int getAmbientLightSensorOutputWrapper(void* host) {
    VariableRefreshRateController* controller =
            reinterpret_cast<VariableRefreshRateController*>(host);
    return controller->getAmbientLightSensorOutput();
}

static bool isProximityThrottlingEnabledWrapper(void* host) {
    VariableRefreshRateController* controller =
            reinterpret_cast<VariableRefreshRateController*>(host);
    return controller->isProximityThrottlingEnabled();
}

auto VariableRefreshRateController::CreateInstance(ExynosDisplay* display,
                                                   const std::string& panelName)
        -> std::shared_ptr<VariableRefreshRateController> {
    if (!display) {
        LOG(ERROR)
                << "VrrController: create VariableRefreshRateController without display handler.";
        return nullptr;
    }
    auto controller = std::shared_ptr<VariableRefreshRateController>(
            new VariableRefreshRateController(display, panelName));
    std::thread thread = std::thread(&VariableRefreshRateController::threadBody, controller.get());
    std::string threadName = "VrrCtrl_";
    threadName += display->mIndex == 0 ? "Primary" : "Second";
    int error = pthread_setname_np(thread.native_handle(), threadName.c_str());
    if (error != 0) {
        LOG(WARNING) << "VrrController: Unable to set thread name, error = " << strerror(error);
    }
    thread.detach();

    return controller;
}

VariableRefreshRateController::VariableRefreshRateController(ExynosDisplay* display,
                                                             const std::string& panelName)
      : mDisplay(display), mPanelName(panelName) {
    mState = VrrControllerState::kDisable;
    std::string displayFileNodePath = mDisplay->getPanelSysfsPath();
    if (displayFileNodePath.empty()) {
        LOG(WARNING) << "VrrController: Cannot find file node of display: "
                     << mDisplay->mDisplayName;
    } else {
        mFileNodeWritter = std::make_unique<FileNodeWriter>(displayFileNodePath);
    }

    // Initialize DisplayContextProviderInterface.
    mDisplayContextProviderInterface.getOperationSpeedMode = (&getOperationSpeedModeWrapper);
    mDisplayContextProviderInterface.getBrightnessMode = (&getBrightnessModeWrapper);
    mDisplayContextProviderInterface.getBrightnessNits = (&getBrightnessNitsWrapper);
    mDisplayContextProviderInterface.getDisplayFileNodePath = (&getDisplayFileNodePathWrapper);
    mDisplayContextProviderInterface.getEstimatedVideoFrameRate =
            (&getEstimateVideoFrameRateWrapper);
    mDisplayContextProviderInterface.getAmbientLightSensorOutput =
            (&getAmbientLightSensorOutputWrapper);
    mDisplayContextProviderInterface.isProximityThrottlingEnabled =
            (&isProximityThrottlingEnabledWrapper);

    // Flow to build refresh rate calculator.
    RefreshRateCalculatorFactory refreshRateCalculatorFactory;
    std::vector<std::unique_ptr<RefreshRateCalculator>> Calculators;

    Calculators.emplace_back(std::move(
            refreshRateCalculatorFactory
                    .BuildRefreshRateCalculator(&mEventQueue, RefreshRateCalculatorType::kAod)));
    Calculators.emplace_back(std::move(
            refreshRateCalculatorFactory
                    .BuildRefreshRateCalculator(&mEventQueue,
                                                RefreshRateCalculatorType::kVideoPlayback)));

    PeriodRefreshRateCalculatorParameters peridParams;
    peridParams.mConfidencePercentage = 0;
    Calculators.emplace_back(std::move(
            refreshRateCalculatorFactory.BuildRefreshRateCalculator(&mEventQueue, peridParams)));

    mRefreshRateCalculator = refreshRateCalculatorFactory.BuildRefreshRateCalculator(Calculators);
    mRefreshRateCalculator->registerRefreshRateChangeCallback(
            std::bind(&VariableRefreshRateController::onRefreshRateChanged, this,
                      std::placeholders::_1));

    mPowerModeListeners.push_back(mRefreshRateCalculator.get());

    DisplayContextProviderFactory displayContextProviderFactory(mDisplay, this, &mEventQueue);
    mDisplayContextProvider = displayContextProviderFactory.buildDisplayContextProvider(
            DisplayContextProviderType::kExynos);

    mPresentTimeoutEventHandlerLoader.reset(
            new ExternalEventHandlerLoader(std::string(kVendorDisplayPanelLibrary).c_str(),
                                           &mDisplayContextProviderInterface, this,
                                           mPanelName.c_str()));
    mPresentTimeoutEventHandler = mPresentTimeoutEventHandlerLoader->getEventHandler();

    mResidencyWatcher = ndk::SharedRefBase::make<DisplayStateResidencyWatcher>(display);
}

VariableRefreshRateController::~VariableRefreshRateController() {
    stopThread(true);

    const std::lock_guard<std::mutex> lock(mMutex);
    if (mLastPresentFence.has_value()) {
        if (close(mLastPresentFence.value())) {
            LOG(ERROR) << "VrrController: close fence file failed, errno = " << errno;
        }
        mLastPresentFence = std::nullopt;
    }
};

int VariableRefreshRateController::notifyExpectedPresent(int64_t timestamp,
                                                         int32_t frameIntervalNs) {
    ATRACE_CALL();
    {
        const std::lock_guard<std::mutex> lock(mMutex);
        mRecord.mNextExpectedPresentTime = {mVrrActiveConfig, timestamp, frameIntervalNs};
        // Post kNotifyExpectedPresentConfig event.
        postEvent(VrrControllerEventType::kNotifyExpectedPresentConfig, getNowNs());
    }
    mCondition.notify_all();
    return 0;
}

void VariableRefreshRateController::reset() {
    ATRACE_CALL();

    const std::lock_guard<std::mutex> lock(mMutex);
    mEventQueue.mPriorityQueue = std::priority_queue<VrrControllerEvent>();
    mRecord.clear();
    dropEventLocked();
    if (mLastPresentFence.has_value()) {
        if (close(mLastPresentFence.value())) {
            LOG(ERROR) << "VrrController: close fence file failed, errno = " << errno;
        }
        mLastPresentFence = std::nullopt;
    }
}

void VariableRefreshRateController::setActiveVrrConfiguration(hwc2_config_t config) {
    LOG(INFO) << "VrrController: Set active Vrr configuration = " << config
              << ", power mode = " << mPowerMode;
    ATRACE_CALL();
    {
        const std::lock_guard<std::mutex> lock(mMutex);
        if (mVrrConfigs.count(config) == 0) {
            LOG(ERROR) << "VrrController: Set an undefined active configuration";
            return;
        }
        mVrrActiveConfig = config;
        if (mResidencyWatcher) {
            mResidencyWatcher->setActiveConfig(config);
        }
        if (mState == VrrControllerState::kDisable) {
            return;
        }
        mState = VrrControllerState::kRendering;
        dropEventLocked(VrrControllerEventType::kSystemRenderingTimeout);

        if (mVrrConfigs[mVrrActiveConfig].isFullySupported) {
            postEvent(VrrControllerEventType::kSystemRenderingTimeout,
                      getNowNs() +
                              mVrrConfigs[mVrrActiveConfig].notifyExpectedPresentConfig->TimeoutNs);
        }
        if (mRefreshRateCalculator) {
            mRefreshRateCalculator->setMinFrameInterval(
                    mVrrConfigs[mVrrActiveConfig].minFrameIntervalNs);
        }
    }
    mCondition.notify_all();
}

void VariableRefreshRateController::setEnable(bool isEnabled) {
    ATRACE_CALL();
    {
        const std::lock_guard<std::mutex> lock(mMutex);
        if (mEnabled == isEnabled) {
            return;
        }
        mEnabled = isEnabled;
        if (mEnabled == false) {
            dropEventLocked();
        }
    }
    mCondition.notify_all();
}

void VariableRefreshRateController::setPowerMode(int32_t powerMode) {
    ATRACE_CALL();
    LOG(INFO) << "VrrController: Set power mode to " << powerMode;

    {
        const std::lock_guard<std::mutex> lock(mMutex);
        if (mPowerMode == powerMode) {
            return;
        }
        switch (powerMode) {
            case HWC_POWER_MODE_OFF:
            case HWC_POWER_MODE_DOZE:
            case HWC_POWER_MODE_DOZE_SUSPEND: {
                mState = VrrControllerState::kDisable;
                dropEventLocked(VrrControllerEventType::kGeneralEventMask);
                break;
            }
            case HWC_POWER_MODE_NORMAL: {
                // We should transition from either HWC_POWER_MODE_OFF, HWC_POWER_MODE_DOZE, or
                // HWC_POWER_MODE_DOZE_SUSPEND. At this point, there should be no pending events
                // posted.
                if (!mEventQueue.mPriorityQueue.empty()) {
                    LOG(WARNING) << "VrrController: there should be no pending event when resume "
                                    "from power mode = "
                                 << mPowerMode << " to power mode = " << powerMode;
                    LOG(INFO) << dumpEventQueueLocked();
                }
                mState = VrrControllerState::kRendering;
                const auto& vrrConfig = mVrrConfigs[mVrrActiveConfig];
                if (vrrConfig.isFullySupported) {
                    postEvent(VrrControllerEventType::kSystemRenderingTimeout,
                              getNowNs() + vrrConfig.notifyExpectedPresentConfig->TimeoutNs);
                }
                break;
            }
            default: {
                LOG(ERROR) << "VrrController: Unknown power mode = " << powerMode;
                return;
            }
        }
        if (!mPowerModeListeners.empty()) {
            for (const auto& listener : mPowerModeListeners) {
                listener->onPowerStateChange(mPowerMode, powerMode);
            }
        }
        mPowerMode = powerMode;
    }
    if (mResidencyWatcher) {
        mResidencyWatcher->setPowerMode(powerMode);
    }
    mCondition.notify_all();
}

void VariableRefreshRateController::setVrrConfigurations(
        std::unordered_map<hwc2_config_t, VrrConfig_t> configs) {
    ATRACE_CALL();

    for (const auto& it : configs) {
        LOG(INFO) << "VrrController: set Vrr configuration id = " << it.first;
        if (it.second.isFullySupported) {
            if (!it.second.notifyExpectedPresentConfig.has_value()) {
                LOG(ERROR) << "VrrController: full vrr config should have "
                              "notifyExpectedPresentConfig.";
                return;
            }
        }
    }

    const std::lock_guard<std::mutex> lock(mMutex);
    mVrrConfigs = std::move(configs);
}

int VariableRefreshRateController::getAmbientLightSensorOutput() const {
    return mDisplayContextProvider->getAmbientLightSensorOutput();
}

BrightnessMode VariableRefreshRateController::getBrightnessMode() const {
    return mDisplayContextProvider->getBrightnessMode();
}

int VariableRefreshRateController::getBrightnessNits() const {
    return mDisplayContextProvider->getBrightnessNits();
}

const char* VariableRefreshRateController::getDisplayFileNodePath() const {
    return mDisplayContextProvider->getDisplayFileNodePath();
}

int VariableRefreshRateController::getEstimatedVideoFrameRate() const {
    return mDisplayContextProvider->getEstimatedVideoFrameRate();
}

OperationSpeedMode VariableRefreshRateController::getOperationSpeedMode() const {
    return mDisplayContextProvider->getOperationSpeedMode();
}

bool VariableRefreshRateController::isProximityThrottlingEnabled() const {
    return mDisplayContextProvider->isProximityThrottlingEnabled();
}

void VariableRefreshRateController::stopThread(bool exit) {
    ATRACE_CALL();
    {
        const std::lock_guard<std::mutex> lock(mMutex);
        mThreadExit = exit;
        mEnabled = false;
        mState = VrrControllerState::kDisable;
    }
    mCondition.notify_all();
}

void VariableRefreshRateController::onPresent(int fence) {
    if (fence < 0) {
        return;
    }
    ATRACE_CALL();
    {
        const std::lock_guard<std::mutex> lock(mMutex);
        if (mState == VrrControllerState::kDisable) {
            return;
        }
        if (!mRecord.mPendingCurrentPresentTime.has_value()) {
            LOG(WARNING) << "VrrController: VrrController: Present without expected present time "
                            "information";
            return;
        } else {
            if (mRefreshRateCalculator) {
                mRefreshRateCalculator->onPresent(mRecord.mPendingCurrentPresentTime.value().mTime,
                                                  getPresentFrameFlag());
            }

            mRecord.mPresentHistory.next() = mRecord.mPendingCurrentPresentTime.value();
            mRecord.mPendingCurrentPresentTime = std::nullopt;
        }
        if (mState == VrrControllerState::kHibernate) {
            LOG(WARNING) << "VrrController: Present during hibernation without prior notification "
                            "via notifyExpectedPresent.";
            mState = VrrControllerState::kRendering;
            dropEventLocked(VrrControllerEventType::kHibernateTimeout);
        }
    }

    // Prior to pushing the most recent fence update, verify the release timestamps of all preceding
    // fences.
    // TODO(b/309873055): delegate the task of executing updateVsyncHistory to the Vrr controller's
    // loop thread in order to reduce the workload of calling thread.
    updateVsyncHistory();
    int dupFence = dup(fence);
    if (dupFence < 0) {
        LOG(ERROR) << "VrrController: duplicate fence file failed." << errno;
    }

    {
        const std::lock_guard<std::mutex> lock(mMutex);
        if (mLastPresentFence.has_value()) {
            LOG(WARNING) << "VrrController: last present fence remains open.";
        }
        mLastPresentFence = dupFence;
        // Drop the out of date timeout.
        dropEventLocked(VrrControllerEventType::kSystemRenderingTimeout);
        cancelPresentTimeoutHandlingLocked();
        // Post next rendering timeout.
        if (mVrrConfigs[mVrrActiveConfig].isFullySupported) {
            postEvent(VrrControllerEventType::kSystemRenderingTimeout,
                      getNowNs() +
                              mVrrConfigs[mVrrActiveConfig].notifyExpectedPresentConfig->TimeoutNs);
        }
        if (shouldHandleVendorRenderingTimeout()) {
            // Post next frame insertion event.
            auto vendorPresentTimeoutNs = getNowNs() +
                    (mVendorPresentTimeoutOverride
                             ? mVendorPresentTimeoutOverride.value().mTimeoutNs
                             : kDefaultVendorPresentTimeoutNs);
            postEvent(VrrControllerEventType::kVendorRenderingTimeout, vendorPresentTimeoutNs);
        }
    }
    mCondition.notify_all();
}

void VariableRefreshRateController::setExpectedPresentTime(int64_t timestampNanos,
                                                           int frameIntervalNs) {
    ATRACE_CALL();

    const std::lock_guard<std::mutex> lock(mMutex);
    mRecord.mPendingCurrentPresentTime = {mVrrActiveConfig, timestampNanos, frameIntervalNs};
}

void VariableRefreshRateController::onVsync(int64_t timestampNanos,
                                            int32_t __unused vsyncPeriodNanos) {
    const std::lock_guard<std::mutex> lock(mMutex);
    mRecord.mVsyncHistory
            .next() = {.mType = VariableRefreshRateController::VsyncEvent::Type::kVblank,
                       .mTime = timestampNanos};
}

void VariableRefreshRateController::cancelPresentTimeoutHandlingLocked() {
    dropEventLocked(VrrControllerEventType::kVendorRenderingTimeout);
    dropEventLocked(VrrControllerEventType::kHandleVendorRenderingTimeout);
}

void VariableRefreshRateController::dropEventLocked() {
    mEventQueue.mPriorityQueue = std::priority_queue<VrrControllerEvent>();
}

void VariableRefreshRateController::dropEventLocked(VrrControllerEventType eventType) {
    std::priority_queue<VrrControllerEvent> q;
    auto target = static_cast<int>(eventType);
    while (!mEventQueue.mPriorityQueue.empty()) {
        const auto& it = mEventQueue.mPriorityQueue.top();
        if ((static_cast<int>(it.mEventType) & target) != target) {
            q.push(it);
        }
        mEventQueue.mPriorityQueue.pop();
    }
    mEventQueue.mPriorityQueue = std::move(q);
}

std::string VariableRefreshRateController::dumpEventQueueLocked() {
    std::string content;
    if (mEventQueue.mPriorityQueue.empty()) {
        return content;
    }

    std::priority_queue<VrrControllerEvent> q;
    while (!mEventQueue.mPriorityQueue.empty()) {
        const auto& it = mEventQueue.mPriorityQueue.top();
        content += "VrrController: event = ";
        content += it.toString();
        content += "\n";
        q.push(it);
        mEventQueue.mPriorityQueue.pop();
    }
    mEventQueue.mPriorityQueue = std::move(q);
    return content;
}

int64_t VariableRefreshRateController::getLastFenceSignalTimeUnlocked(int fd) {
    if (fd == -1) {
        return SIGNAL_TIME_INVALID;
    }
    struct sync_file_info* finfo = sync_file_info(fd);
    if (finfo == nullptr) {
        LOG(ERROR) << "VrrController: sync_file_info returned NULL for fd " << fd;
        return SIGNAL_TIME_INVALID;
    }
    if (finfo->status != 1) {
        const auto status = finfo->status;
        if (status < 0) {
            LOG(ERROR) << "VrrController: sync_file_info contains an error: " << status;
        }
        sync_file_info_free(finfo);
        return status < 0 ? SIGNAL_TIME_INVALID : SIGNAL_TIME_PENDING;
    }
    uint64_t timestamp = 0;
    struct sync_fence_info* pinfo = sync_get_fence_info(finfo);
    if (finfo->num_fences != 1) {
        LOG(WARNING) << "VrrController:: there is more than one fence in the file descriptor = "
                     << fd;
    }
    for (size_t i = 0; i < finfo->num_fences; i++) {
        if (pinfo[i].timestamp_ns > timestamp) {
            timestamp = pinfo[i].timestamp_ns;
        }
    }
    sync_file_info_free(finfo);
    return timestamp;
}

int64_t VariableRefreshRateController::getNextEventTimeLocked() const {
    if (mEventQueue.mPriorityQueue.empty()) {
        LOG(WARNING) << "VrrController: event queue should NOT be empty.";
        return -1;
    }
    const auto& event = mEventQueue.mPriorityQueue.top();
    return event.mWhenNs;
}

std::string VariableRefreshRateController::getStateName(VrrControllerState state) const {
    switch (state) {
        case VrrControllerState::kDisable:
            return "Disable";
        case VrrControllerState::kRendering:
            return "Rendering";
        case VrrControllerState::kHibernate:
            return "Hibernate";
        default:
            return "Unknown";
    }
}

void VariableRefreshRateController::handleCadenceChange() {
    ATRACE_CALL();
    if (!mRecord.mNextExpectedPresentTime.has_value()) {
        LOG(WARNING) << "VrrController: cadence change occurs without the expected present timing "
                        "information.";
        return;
    }
    // TODO(b/305311056): handle frame rate change.
    mRecord.mNextExpectedPresentTime = std::nullopt;
}

void VariableRefreshRateController::handleResume() {
    ATRACE_CALL();
    if (!mRecord.mNextExpectedPresentTime.has_value()) {
        LOG(WARNING)
                << "VrrController: resume occurs without the expected present timing information.";
        return;
    }
    // TODO(b/305311281): handle panel resume.
    mRecord.mNextExpectedPresentTime = std::nullopt;
}

void VariableRefreshRateController::handleHibernate() {
    ATRACE_CALL();
    // TODO(b/305311206): handle entering panel hibernate.
    postEvent(VrrControllerEventType::kHibernateTimeout,
              getNowNs() + kDefaultWakeUpTimeInPowerSaving);
}

void VariableRefreshRateController::handleStayHibernate() {
    ATRACE_CALL();
    // TODO(b/305311698): handle keeping panel hibernate.
    postEvent(VrrControllerEventType::kHibernateTimeout,
              getNowNs() + kDefaultWakeUpTimeInPowerSaving);
}

void VariableRefreshRateController::handlePresentTimeout(const VrrControllerEvent& event) {
    ATRACE_CALL();

    if (mState == VrrControllerState::kDisable) {
        cancelPresentTimeoutHandlingLocked();
        return;
    }
    event.mFunctor();
}

void VariableRefreshRateController::onRefreshRateChanged(int refreshRate) {
    if (!(mDisplay) || !(mDisplay->mDevice)) {
        LOG(ERROR) << "VrrController: absence of a device or display.";
        return;
    }
    refreshRate =
            refreshRate == kDefaultInvalidRefreshRate ? kDefaultMinimumRefreshRate : refreshRate;

    if (mResidencyWatcher) {
        mResidencyWatcher->setRefreshRate(refreshRate);
    }

    if (!mDisplay->mDevice->isVrrApiSupported()) {
        // For legacy API, vsyncPeriodNanos is utilized to denote the refresh rate,
        // refreshPeriodNanos is disregarded.
        mDisplay->mDevice->onRefreshRateChangedDebug(mDisplay->mDisplayId,
                                                     freqToDurationNs(refreshRate), -1);
    } else {
        mDisplay->mDevice->onRefreshRateChangedDebug(mDisplay->mDisplayId,
                                                     mVrrConfigs[mVrrActiveConfig].vsyncPeriodNs,
                                                     freqToDurationNs(refreshRate));
    }
}

bool VariableRefreshRateController::shouldHandleVendorRenderingTimeout() const {
    return (mPresentTimeoutController == PresentTimeoutControllerType::kSoftware) &&
            ((!mVendorPresentTimeoutOverride) ||
             (mVendorPresentTimeoutOverride.value().mSchedule.size() > 0));
}

void VariableRefreshRateController::threadBody() {
    struct sched_param param = {.sched_priority = sched_get_priority_max(SCHED_FIFO)};
    if (sched_setscheduler(0, SCHED_FIFO, &param) != 0) {
        LOG(ERROR) << "VrrController: fail to set scheduler to SCHED_FIFO.";
        return;
    }
    for (;;) {
        bool stateChanged = false;
        {
            std::unique_lock<std::mutex> lock(mMutex);
            if (mThreadExit) break;
            if (!mEnabled) mCondition.wait(lock);
            if (!mEnabled) continue;

            if (mEventQueue.mPriorityQueue.empty()) {
                mCondition.wait(lock);
            }
            int64_t whenNs = getNextEventTimeLocked();
            int64_t nowNs = getNowNs();
            if (whenNs > nowNs) {
                int64_t delayNs = whenNs - nowNs;
                auto res = mCondition.wait_for(lock, std::chrono::nanoseconds(delayNs));
                if (res != std::cv_status::timeout) {
                    continue;
                }
            }

            if (mEventQueue.mPriorityQueue.empty()) {
                continue;
            }

            auto event = mEventQueue.mPriorityQueue.top();
            mEventQueue.mPriorityQueue.pop();
            if (static_cast<int>(event.mEventType) &
                static_cast<int>(VrrControllerEventType::kRefreshRateCalculatorUpdateMask)) {
                handleGeneralEventLocked(event);
                continue;
            }
            if (mState == VrrControllerState::kRendering) {
                if (event.mEventType == VrrControllerEventType::kHibernateTimeout) {
                    LOG(ERROR) << "VrrController: receiving a hibernate timeout event while in the "
                                  "rendering state.";
                }
                switch (event.mEventType) {
                    case VrrControllerEventType::kSystemRenderingTimeout: {
                        handleHibernate();
                        mState = VrrControllerState::kHibernate;
                        stateChanged = true;
                        break;
                    }
                    case VrrControllerEventType::kNotifyExpectedPresentConfig: {
                        handleCadenceChange();
                        break;
                    }
                    case VrrControllerEventType::kVendorRenderingTimeout: {
                        if (mPresentTimeoutEventHandler) {
                            // Verify whether a present timeout override exists, and if so, execute
                            // it first.
                            if (mVendorPresentTimeoutOverride) {
                                const auto& params = mVendorPresentTimeoutOverride.value();
                                TimedEvent timedEvent("VendorPresentTimeoutOverride");
                                timedEvent.mIsRelativeTime = true;
                                timedEvent.mFunctor = params.mFunctor;
                                int64_t whenFromNowNs = 0;
                                for (int i = 0; i < params.mSchedule.size(); ++i) {
                                    uint32_t intervalNs = params.mSchedule[i].second;
                                    for (int j = 0; j < params.mSchedule[i].first; ++j) {
                                        timedEvent.mWhenNs = whenFromNowNs;
                                        postEvent(VrrControllerEventType::
                                                          kHandleVendorRenderingTimeout,
                                                  timedEvent);
                                        whenFromNowNs += intervalNs;
                                    }
                                }
                            } else {
                                auto handleEvents = mPresentTimeoutEventHandler->getHandleEvents();
                                for (auto& event : handleEvents) {
                                    postEvent(VrrControllerEventType::kHandleVendorRenderingTimeout,
                                              event);
                                }
                            }
                        }
                        break;
                    }
                    case VrrControllerEventType::kHandleVendorRenderingTimeout: {
                        handlePresentTimeout(event);
                        break;
                    }
                    default: {
                        break;
                    }
                }
            } else {
                if (event.mEventType == VrrControllerEventType::kSystemRenderingTimeout) {
                    LOG(ERROR) << "VrrController: receiving a rendering timeout event while in the "
                                  "hibernate state.";
                }
                if (mState != VrrControllerState::kHibernate) {
                    LOG(ERROR) << "VrrController: expecting to be in hibernate, but instead in "
                                  "state = "
                               << getStateName(mState);
                }
                switch (event.mEventType) {
                    case VrrControllerEventType::kHibernateTimeout: {
                        handleStayHibernate();
                        break;
                    }
                    case VrrControllerEventType::kNotifyExpectedPresentConfig: {
                        handleResume();
                        mState = VrrControllerState::kRendering;
                        stateChanged = true;
                        break;
                    }
                    default: {
                        break;
                    }
                }
            }
        }
        // TODO(b/309873055): implement a handler to serialize all outer function calls to the same
        // thread owned by the VRR controller.
        if (stateChanged) {
            updateVsyncHistory();
        }
    }
}

void VariableRefreshRateController::postEvent(VrrControllerEventType type, int64_t when) {
    VrrControllerEvent event;
    event.mEventType = type;
    event.mWhenNs = when;
    mEventQueue.mPriorityQueue.emplace(event);
}

void VariableRefreshRateController::postEvent(VrrControllerEventType type, TimedEvent& timedEvent) {
    VrrControllerEvent event;
    event.mEventType = type;
    setTimedEventWithAbsoluteTime(timedEvent);
    event.mWhenNs = timedEvent.mWhenNs;
    event.mFunctor = std::move(timedEvent.mFunctor);
    mEventQueue.mPriorityQueue.emplace(event);
}

void VariableRefreshRateController::updateVsyncHistory() {
    int fence = -1;

    {
        const std::lock_guard<std::mutex> lock(mMutex);
        if (!mLastPresentFence.has_value()) {
            return;
        }
        fence = mLastPresentFence.value();
        mLastPresentFence = std::nullopt;
    }

    // Execute the following logic unlocked to enhance performance.
    int64_t lastSignalTime = getLastFenceSignalTimeUnlocked(fence);
    if (close(fence)) {
        LOG(ERROR) << "VrrController: close fence file failed, errno = " << errno;
        return;
    } else if (lastSignalTime == SIGNAL_TIME_PENDING || lastSignalTime == SIGNAL_TIME_INVALID) {
        return;
    }

    {
        // Acquire the mutex again to store the vsync record.
        const std::lock_guard<std::mutex> lock(mMutex);
        mRecord.mVsyncHistory
                .next() = {.mType = VariableRefreshRateController::VsyncEvent::Type::kReleaseFence,
                           .mTime = lastSignalTime};
    }
}

} // namespace android::hardware::graphics::composer
