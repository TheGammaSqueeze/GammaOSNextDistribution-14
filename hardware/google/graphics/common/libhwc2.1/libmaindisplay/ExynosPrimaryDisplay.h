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
#ifndef EXYNOS_PRIMARY_DISPLAY_H
#define EXYNOS_PRIMARY_DISPLAY_H

#include <map>

#include "../libdevice/ExynosDisplay.h"
#include "../libvrr/VariableRefreshRateController.h"
#include "../libvrr/interface/VariableRefreshRateInterface.h"

using android::hardware::graphics::composer::PresentListener;
using android::hardware::graphics::composer::VariableRefreshRateController;
using android::hardware::graphics::composer::VsyncListener;
using namespace displaycolor;

class ExynosPrimaryDisplay : public ExynosDisplay {
    public:
        /* Methods */
        ExynosPrimaryDisplay(uint32_t index, ExynosDevice* device, const std::string& displayName);
        ~ExynosPrimaryDisplay();
        virtual void setDDIScalerEnable(int width, int height);
        virtual int getDDIScalerMode(int width, int height);
        virtual int32_t SetCurrentPanelGammaSource(const displaycolor::DisplayType type,
                                                   const PanelGammaSource& source) override;
        virtual PanelGammaSource GetCurrentPanelGammaSource() const override {
            return currentPanelGammaSource;
        }

        virtual bool isLhbmSupported();
        virtual int32_t setLhbmState(bool enabled);

        virtual bool getLhbmState();
        virtual void setEarlyWakeupDisplay();
        virtual void setExpectedPresentTime(uint64_t timestamp, int frameIntervalNs) override;
        virtual uint64_t getPendingExpectedPresentTime() override;
        virtual int getPendingFrameInterval() override;
        virtual void applyExpectedPresentTime();
        virtual int32_t setDisplayIdleTimer(const int32_t timeoutMs) override;
        virtual void handleDisplayIdleEnter(const uint32_t idleTeRefreshRate) override;

        virtual void initDisplayInterface(uint32_t interfaceType);
        virtual int32_t doDisplayConfigInternal(hwc2_config_t config) override;

        virtual int32_t setMinIdleRefreshRate(const int fps,
                                              const RrThrottleRequester requester) override;
        virtual int32_t setRefreshRateThrottleNanos(const int64_t delayNs,
                                                    const RrThrottleRequester requester) override;
        virtual bool isDbmSupported() override;
        virtual int32_t setDbmState(bool enabled) override;

        virtual void dump(String8& result) override;
        virtual void updateAppliedActiveConfig(const hwc2_config_t newConfig,
                                               const int64_t ts) override;
        virtual void checkBtsReassignResource(const int32_t vsyncPeriod,
                                              const int32_t btsVsyncPeriod) override;

        virtual int32_t setBootDisplayConfig(int32_t config) override;
        virtual int32_t clearBootDisplayConfig() override;
        virtual int32_t getPreferredDisplayConfigInternal(int32_t* outConfig) override;
        virtual bool isConfigSettingEnabled() override;
        virtual void enableConfigSetting(bool en) override;

        virtual int32_t getDisplayConfigs(uint32_t* outNumConfigs,
                                          hwc2_config_t* outConfigs) override;
        virtual int32_t presentDisplay(int32_t* outRetireFence) override;

        virtual void onVsync(int64_t timestamp) override;

        const std::string& getPanelName() final;

        int32_t notifyExpectedPresent(int64_t timestamp, int32_t frameIntervalNs) override;

        int32_t setPresentTimeoutParameters(
                int timeoutNs, const std::vector<std::pair<uint32_t, uint32_t>>& settings) override;

        int32_t setPresentTimeoutController(uint32_t controllerType) override;

    protected:
        /* setPowerMode(int32_t mode)
         * Descriptor: HWC2_FUNCTION_SET_POWER_MODE
         * Parameters:
         *   mode - hwc2_power_mode_t and ext_hwc2_power_mode_t
         *
         * Returns HWC2_ERROR_NONE or the following error:
         *   HWC2_ERROR_UNSUPPORTED when DOZE mode not support
         */
        virtual int32_t setPowerMode(int32_t mode) override;
        virtual bool getHDRException(ExynosLayer* __unused layer);
        virtual int32_t setActiveConfigInternal(hwc2_config_t config, bool force) override;
        virtual int32_t getActiveConfigInternal(hwc2_config_t* outConfig) override;

    public:
        // Prepare multi resolution
        ResolutionInfo mResolutionInfo;
        std::string getPanelSysfsPath() const override {
            return getPanelSysfsPath(getDcDisplayType());
        }
        std::string getPanelSysfsPath(const displaycolor::DisplayType& type) const;

        uint32_t mRcdId = -1;

    private:
        static constexpr const char* kDisplayCalFilePath = "/mnt/vendor/persist/display/";
        static constexpr const char* kPanelGammaCalFilePrefix = "gamma_calib_data";
        enum PanelGammaSource currentPanelGammaSource = PanelGammaSource::GAMMA_DEFAULT;

        bool checkLhbmMode(bool status, nsecs_t timoutNs);
        void setLHBMRefreshRateThrottle(const uint32_t delayMs);

        bool mFirstPowerOn = true;
        bool mNotifyPowerOn = false;
        std::mutex mPowerModeMutex;
        std::condition_variable mPowerOnCondition;

        int32_t applyPendingConfig();
        int32_t setPowerOn();
        int32_t setPowerOff();
        int32_t setPowerDoze(hwc2_power_mode_t mode);
        void firstPowerOn();
        int32_t setDisplayIdleTimerEnabled(const bool enabled);
        int32_t getDisplayIdleTimerEnabled(bool& enabled);
        void setDisplayNeedHandleIdleExit(const bool needed, const bool force);
        int32_t setDisplayIdleDelayNanos(int32_t delayNanos,
                                         const DispIdleTimerRequester requester);
        void initDisplayHandleIdleExit();
        int32_t setLhbmDisplayConfigLocked(uint32_t peakRate);
        void restoreLhbmDisplayConfigLocked();

        void onConfigChange(int configId);

        // LHBM
        FILE* mLhbmFd;
        std::atomic<bool> mLhbmOn;
        int32_t mFramesToReachLhbmPeakBrightness;
        bool mConfigSettingDisabled = false;
        int64_t mConfigSettingDisabledTimestamp = 0;
        // timeout value of waiting for peak refresh rate
        static constexpr uint32_t kLhbmWaitForPeakRefreshRateMs = 100U;
        static constexpr uint32_t kLhbmRefreshRateThrottleMs = 1000U;
        static constexpr uint32_t kConfigDisablingMaxDurationMs = 1000U;
        static constexpr uint32_t kSysfsCheckTimeoutMs = 500U;

        int32_t getTimestampDeltaMs(int64_t endNs, int64_t beginNs) {
            if (endNs == 0) endNs = systemTime(SYSTEM_TIME_MONOTONIC);
            return (endNs - beginNs) / 1000000;
        }

        FILE* mEarlyWakeupDispFd;
        static constexpr const char* kWakeupDispFilePath =
                "/sys/devices/platform/1c300000.drmdecon/early_wakeup";

        CtrlValue<std::tuple<int64_t, int>> mExpectedPresentTimeAndInterval;

        virtual void calculateTimelineLocked(
                hwc2_config_t config,
                hwc_vsync_period_change_constraints_t* vsyncPeriodChangeConstraints,
                hwc_vsync_period_change_timeline_t* outTimeline) override;
        void recalculateTimelineLocked(int64_t refreshRateDelayNanos);

        // min idle refresh rate
        int mDefaultMinIdleRefreshRate;
        // the min refresh rate in the blocking zone, e.g. 10 means 10Hz in the zone
        int mMinIdleRefreshRateForBlockingZone;
        // blocking zone threshold, e.g. 492 means entering the zone if DBV < 492
        uint32_t mDbvThresholdForBlockingZone;
        bool mUseBlockingZoneForMinIdleRefreshRate;
        int mMinIdleRefreshRate;
        int mRrThrottleFps[toUnderlying(RrThrottleRequester::MAX)];
        std::mutex mMinIdleRefreshRateMutex;

        std::mutex mIdleRefreshRateThrottleMutex;
        int64_t mRrThrottleNanos[toUnderlying(RrThrottleRequester::MAX)];
        int64_t mRefreshRateDelayNanos;
        int64_t mRrUseDelayNanos;
        int64_t mLastRefreshRateAppliedNanos;
        bool mIsRrNeedCheckDelay;
        hwc2_config_t mAppliedActiveConfig;

        std::mutex mDisplayIdleDelayMutex;
        bool mDisplayIdleTimerEnabled;
        int64_t mDisplayIdleTimerNanos[toUnderlying(DispIdleTimerRequester::MAX)];
        std::ofstream mDisplayNeedHandleIdleExitOfs;
        int64_t mDisplayIdleDelayNanos;
        bool mDisplayNeedHandleIdleExit;

        // Function and variables related to Vrr.
        PresentListener* getPresentListener();
        VsyncListener* getVsyncListener();

        VrrSettings_t mVrrSettings;
        std::shared_ptr<VariableRefreshRateController> mVariableRefreshRateController;
};

#endif
