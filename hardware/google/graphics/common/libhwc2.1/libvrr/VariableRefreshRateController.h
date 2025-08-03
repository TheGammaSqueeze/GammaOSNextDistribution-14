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

#pragma once

#include <utils/Mutex.h>
#include <condition_variable>
#include <list>
#include <map>
#include <optional>
#include <queue>
#include <thread>

#include "../libdevice/ExynosDisplay.h"
#include "../libdevice/ExynosLayer.h"
#include "DisplayStateResidencyWatcher.h"
#include "EventQueue.h"
#include "ExternalEventHandlerLoader.h"
#include "RefreshRateCalculator/RefreshRateCalculator.h"
#include "RingBuffer.h"
#include "Utils.h"
#include "display/common/DisplayConfigurationOwner.h"
#include "interface/DisplayContextProvider.h"
#include "interface/VariableRefreshRateInterface.h"

namespace android::hardware::graphics::composer {

class VariableRefreshRateController : public VsyncListener,
                                      public PresentListener,
                                      public DisplayContextProvider,
                                      public DisplayConfigurationsOwner {
public:
    ~VariableRefreshRateController();

    auto static CreateInstance(ExynosDisplay* display, const std::string& panelName)
            -> std::shared_ptr<VariableRefreshRateController>;

    const VrrConfig_t* getCurrentDisplayConfiguration() const override {
        const auto& it = mVrrConfigs.find(mVrrActiveConfig);
        if (it == mVrrConfigs.end()) {
            return nullptr;
        }
        return &(it->second);
    }

    int notifyExpectedPresent(int64_t timestamp, int32_t frameIntervalNs);

    // Clear historical record data.
    void reset();

    // After setting the active Vrr configuration, we will automatically transition into the
    // rendering state and post the timeout event.
    void setActiveVrrConfiguration(hwc2_config_t config);

    void setEnable(bool isEnabled);

    void setPowerMode(int32_t mode);

    void setVrrConfigurations(std::unordered_map<hwc2_config_t, VrrConfig_t> configs);

    // Inherit from DisplayContextProvider.
    int getAmbientLightSensorOutput() const override;
    BrightnessMode getBrightnessMode() const override;
    int getBrightnessNits() const override;
    const char* getDisplayFileNodePath() const override;
    int getEstimatedVideoFrameRate() const override;
    OperationSpeedMode getOperationSpeedMode() const override;
    bool isProximityThrottlingEnabled() const override;

    const DisplayContextProviderInterface* getDisplayContextProviderInterface() const {
        return &mDisplayContextProviderInterface;
    }

    void setPresentTimeoutParameters(int timeoutNs,
                                     const std::vector<std::pair<uint32_t, uint32_t>>& settings) {
        const std::lock_guard<std::mutex> lock(mMutex);

        if (!mPresentTimeoutEventHandler) {
            return;
        }
        if ((timeoutNs >= 0) && (!settings.empty())) {
            auto functor = mPresentTimeoutEventHandler->getHandleFunction();
            mVendorPresentTimeoutOverride = std::make_optional<PresentTimeoutSettingsNew>();
            mVendorPresentTimeoutOverride.value().mTimeoutNs = timeoutNs;
            mVendorPresentTimeoutOverride.value().mFunctor = std::move(functor);
            for (const auto& setting : settings) {
                mVendorPresentTimeoutOverride.value().mSchedule.emplace_back(setting);
            }
        } else {
            mVendorPresentTimeoutOverride = std::nullopt;
        }
    }

    void setPresentTimeoutController(uint32_t controllerType) {
        const std::lock_guard<std::mutex> lock(mMutex);

        PresentTimeoutControllerType newControllerType =
                static_cast<PresentTimeoutControllerType>(controllerType);
        if (newControllerType != mPresentTimeoutController) {
            if (mPresentTimeoutController == PresentTimeoutControllerType::kSoftware) {
                dropEventLocked(VrrControllerEventType::kVendorRenderingTimeout);
            }
            if (mPresentTimeoutEventHandler) {
                // When |controllerType| is kNone, we select software to control present timeout,
                // but without handling.
                mPresentTimeoutEventHandler->setPanelFrameInsertionMode(
                        newControllerType == PresentTimeoutControllerType::kHardware);
            }
        }
        mPresentTimeoutController = newControllerType;
    }

private:
    static constexpr int kDefaultRingBufferCapacity = 128;
    static constexpr int64_t kDefaultWakeUpTimeInPowerSaving =
            500 * (std::nano::den / std::milli::den); // 500 ms
    static constexpr int64_t SIGNAL_TIME_PENDING = INT64_MAX;
    static constexpr int64_t SIGNAL_TIME_INVALID = -1;

    static constexpr int64_t kDefaultVendorPresentTimeoutNs =
            33 * (std::nano::den / std::milli::den); // 33 ms

    static constexpr std::string_view kVendorDisplayPanelLibrary = "libdisplaypanel.so";

    enum class VrrControllerState {
        kDisable = 0,
        kRendering,
        kHibernate,
    };

    typedef struct PresentEvent {
        hwc2_config_t config;
        int64_t mTime;
        int mDuration;
    } PresentEvent;

    // The |PresentTimeoutSettings| will override the settings of the present timeout handler. Once
    // it is set, the present timeout will be directly set by these parameters.
    typedef struct PresentTimeoutSettings {
        int mNumOfWorks;
        int mTimeoutNs;
        int mIntervalNs;
        std::function<int()> mFunctor;
    } PresentTimeoutSettings;

    typedef struct PresentTimeoutSettingsNew {
        PresentTimeoutSettingsNew() = default;
        int mTimeoutNs = 0;
        std::vector<std::pair<uint32_t, uint32_t>> mSchedule;
        std::function<int()> mFunctor;
    } PresentTimeoutSettingsNew;

    enum class PresentTimeoutControllerType {
        kNone = 0,
        kHardware,
        kSoftware,
    };

    typedef struct VsyncEvent {
        enum class Type {
            kVblank,
            kReleaseFence,
        };
        Type mType;
        int64_t mTime;
    } VsyncEvent;

    typedef struct VrrRecord {
        static constexpr int kDefaultRingBufferCapacity = 128;

        void clear() {
            mNextExpectedPresentTime = std::nullopt;
            mPendingCurrentPresentTime = std::nullopt;
            mPresentHistory.clear();
            mVsyncHistory.clear();
        }

        std::optional<PresentEvent> mNextExpectedPresentTime = std::nullopt;
        std::optional<PresentEvent> mPendingCurrentPresentTime = std::nullopt;

        typedef RingBuffer<PresentEvent, kDefaultRingBufferCapacity> PresentTimeRecord;
        typedef RingBuffer<VsyncEvent, kDefaultRingBufferCapacity> VsyncRecord;
        PresentTimeRecord mPresentHistory;
        VsyncRecord mVsyncHistory;
    } VrrRecord;

    VariableRefreshRateController(ExynosDisplay* display, const std::string& panelName);

    // Implement interface PresentListener.
    virtual void onPresent(int32_t fence) override;
    virtual void setExpectedPresentTime(int64_t timestampNanos, int frameIntervalNs) override;

    // Implement interface VsyncListener.
    virtual void onVsync(int64_t timestamp, int32_t vsyncPeriodNanos) override;

    void cancelPresentTimeoutHandlingLocked();

    void dropEventLocked();
    void dropEventLocked(VrrControllerEventType eventType);

    std::string dumpEventQueueLocked();

    int64_t getLastFenceSignalTimeUnlocked(int fd);

    int64_t getNextEventTimeLocked() const;

    int getPresentFrameFlag() const {
        int flag = 0;
        // Is Yuv.
        for (size_t i = 0; i < mDisplay->mLayers.size(); i++) {
            auto layer = mDisplay->mLayers[i];
            if (layer->isLayerFormatYuv()) {
                flag |= static_cast<int>(PresentFrameFlag::kIsYuv);
            }
            if (layer->mRequestedCompositionType == HWC2_COMPOSITION_REFRESH_RATE_INDICATOR) {
                flag |= static_cast<int>(PresentFrameFlag::kHasRefreshRateIndicatorLayer);
            }
        }
        // Present when doze.
        if ((mPowerMode == HWC_POWER_MODE_DOZE) || (mPowerMode == HWC_POWER_MODE_DOZE_SUSPEND)) {
            flag |= static_cast<int>(PresentFrameFlag::kPresentingWhenDoze);
        }
        return flag;
    }

    std::string getStateName(VrrControllerState state) const;

    // Functions responsible for state machine transitions.
    void handleCadenceChange();
    void handleResume();
    void handleHibernate();
    void handleStayHibernate();

    void handleGeneralEventLocked(VrrControllerEvent& event) {
        if (event.mFunctor) {
            event.mFunctor();
        }
    }

    void handlePresentTimeout(const VrrControllerEvent& event);

    void onRefreshRateChanged(int refreshRate);

    void postEvent(VrrControllerEventType type, TimedEvent& timedEvent);
    void postEvent(VrrControllerEventType type, int64_t when);

    bool shouldHandleVendorRenderingTimeout() const;

    void stopThread(bool exit);

    // The core function of the VRR controller thread.
    void threadBody();

    void updateVsyncHistory();

    ExynosDisplay* mDisplay;

    // The subsequent variables must be guarded by mMutex when accessed.
    EventQueue mEventQueue;
    VrrRecord mRecord;

    int32_t mPowerMode = -1;
    std::vector<PowerModeListener*> mPowerModeListeners;

    VrrControllerState mState;
    hwc2_config_t mVrrActiveConfig = -1;
    std::unordered_map<hwc2_config_t, VrrConfig_t> mVrrConfigs;
    std::optional<int> mLastPresentFence;

    std::unique_ptr<FileNodeWriter> mFileNodeWritter;

    DisplayContextProviderInterface mDisplayContextProviderInterface;
    std::unique_ptr<ExternalEventHandlerLoader> mPresentTimeoutEventHandlerLoader;
    ExternalEventHandler* mPresentTimeoutEventHandler = nullptr;
    std::optional<PresentTimeoutSettings> mVendorPresentTimeoutOverrideOld;
    std::optional<PresentTimeoutSettingsNew> mVendorPresentTimeoutOverride;
    PresentTimeoutControllerType mPresentTimeoutController =
            PresentTimeoutControllerType::kSoftware;

    std::string mPanelName;

    std::unique_ptr<RefreshRateCalculator> mRefreshRateCalculator;
    std::shared_ptr<DisplayStateResidencyWatcher> mResidencyWatcher;

    std::unique_ptr<DisplayContextProvider> mDisplayContextProvider;

    bool mEnabled = false;
    bool mThreadExit = false;

    std::mutex mMutex;
    std::condition_variable mCondition;
};

} // namespace android::hardware::graphics::composer
