/*
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

#ifndef _BRIGHTNESS_CONTROLLER_H_
#define _BRIGHTNESS_CONTROLLER_H_

#include <drm/samsung_drm.h>
#include <utils/Looper.h>
#include <utils/Mutex.h>

#include <fstream>
#include <thread>

#include "ExynosDisplayDrmInterface.h"

/**
 * Brightness change requests come from binder calls or HWC itself.
 * The request could be applied via next drm commit or immeditely via sysfs.
 *
 * To make it simple, setDisplayBrightness from SF, if not triggering a HBM on/off,
 * will be applied immediately via sysfs path. All other requests will be applied via next
 * drm commit.
 *
 * Sysfs path is faster than drm path. So if there is a pending drm commit that may
 * change brightness level, sfsfs path task should wait until it has completed.
 */
class BrightnessController {
public:
    using HdrLayerState = displaycolor::HdrLayerState;
    using DisplayBrightnessRange = displaycolor::DisplayBrightnessRange;
    using BrightnessRangeMap = displaycolor::BrightnessRangeMap;
    using IBrightnessTable = displaycolor::IBrightnessTable;
    using BrightnessMode = displaycolor::BrightnessMode;
    using ColorRenderIntent = displaycolor::hwc::RenderIntent;

    class DimmingMsgHandler : public virtual ::android::MessageHandler {
    public:
        enum {
            MSG_QUIT,
            MSG_DIMMING_OFF,
        };
        DimmingMsgHandler(BrightnessController* bc) : mBrightnessController(bc) {}
        void handleMessage(const Message& message) override;

    private:
        BrightnessController* mBrightnessController;
    };

    BrightnessController(int32_t panelIndex, std::function<void(void)> refresh,
                         std::function<void(void)> updateDcLhbm);
    ~BrightnessController();

    BrightnessController(int32_t panelIndex);
    int initDrm(const DrmDevice& drmDevice,
                const DrmConnector& connector);

    int processEnhancedHbm(bool on);
    int processDisplayBrightness(float bl, const nsecs_t vsyncNs, bool waitPresent = false);
    int ignoreBrightnessUpdateRequests(bool ignore);
    int setBrightnessNits(float nits, const nsecs_t vsyncNs);
    int setBrightnessDbv(uint32_t dbv, const nsecs_t vsyncNs);
    int processLocalHbm(bool on);
    int processDimBrightness(bool on);
    int processOperationRate(int32_t hz);
    bool isDbmSupported() { return mDbmSupported; }
    int applyPendingChangeViaSysfs(const nsecs_t vsyncNs);
    int applyAclViaSysfs();
    bool validateLayerBrightness(float brightness);

    /**
     * processInstantHbm for GHBM UDFPS
     *  - on true: turn on HBM at next frame with peak brightness
     *       false: turn off HBM at next frame and use system display brightness
     *              from processDisplayBrightness
     */
    int processInstantHbm(bool on);

    /**
     * updateFrameStates
     *  - hdrState: hdr layer size in this frame
     *  - sdrDim: whether any dimmed sdr layer in this frame
     */
    void updateFrameStates(HdrLayerState hdrState, bool sdrDim);

    /**
     * updateColorRenderIntent
     *  - intent: color render intent
     */
    void updateColorRenderIntent(int32_t intent);

    /**
     * Dim ratio to keep the sdr brightness unchange after an instant hbm on with peak brightness.
     */
    float getSdrDimRatioForInstantHbm();

    void onClearDisplay(bool needModeClear);

    /**
     * apply brightness change on drm path.
     * Note: only this path can hold the lock for a long time
     */
    int prepareFrameCommit(ExynosDisplay& display, const DrmConnector& connector,
                           ExynosDisplayDrmInterface::DrmModeAtomicReq& drmReq,
                           const bool mixedComposition, bool& ghbmSync, bool& lhbmSync,
                           bool& blSync, bool& opRateSync);

    bool isGhbmSupported() { return mGhbmSupported; }
    bool isLhbmSupported() { return mLhbmSupported; }

    bool isGhbmOn() {
        std::lock_guard<std::recursive_mutex> lock(mBrightnessMutex);
        return mGhbm.get() != HbmMode::OFF;
    }

    bool isLhbmOn() {
        std::lock_guard<std::recursive_mutex> lock(mBrightnessMutex);
        return mLhbm.get();
    }
    int checkSysfsStatus(const std::string& file, const std::vector<std::string>& expectedValue,
                         const nsecs_t timeoutNs);
    bool fileExists(const std::string& file) {
        struct stat sb;
        return stat(file.c_str(), &sb) == 0;
    }
    void resetLhbmState();

    uint32_t getBrightnessLevel() {
        std::lock_guard<std::recursive_mutex> lock(mBrightnessMutex);
        return mBrightnessLevel.get();
    }

    std::optional<std::tuple<float, BrightnessMode>> getBrightnessNitsAndMode() {
        std::lock_guard<std::recursive_mutex> lock(mBrightnessMutex);
        BrightnessMode brightnessMode;
        if (mBrightnessTable == nullptr) {
            return std::nullopt;
        }

        auto brightness = mBrightnessTable->DbvToBrightness(mBrightnessLevel.get());
        if (brightness == std::nullopt) {
            return std::nullopt;
        }
        auto nits = mBrightnessTable->BrightnessToNits(brightness.value(), brightnessMode);
        if (nits == std::nullopt) {
            return std::nullopt;
        }
        return std::make_tuple(nits.value(), brightnessMode);
    }

    bool isDimSdr() {
        std::lock_guard<std::recursive_mutex> lock(mBrightnessMutex);
        return mInstantHbmReq.get();
    }

    HdrLayerState getHdrLayerState() {
        return mHdrLayerState.get();
    }

    uint32_t getOperationRate() {
        std::lock_guard<std::recursive_mutex> lock(mBrightnessMutex);
        return mOperationRate.get();
    }

    bool isOperationRatePending() {
        std::lock_guard<std::recursive_mutex> lock(mBrightnessMutex);
        return mOperationRate.is_dirty();
    }

    bool isSupported() {
        // valid mMaxBrightness means both brightness and max_brightness sysfs exist
        return mMaxBrightness > 0;
    }

    void dump(String8 &result);

    void setOutdoorVisibility(LbeState state);

    int updateCabcMode();

    const std::string GetPanelSysfileByIndex(const char *file_pattern) {
        String8 nodeName;
        nodeName.appendFormat(file_pattern, mPanelIndex);
        return nodeName.c_str();
    }

    const std::string GetPanelRefreshRateSysfile() {
        String8 nodeName;
        nodeName.appendFormat(kRefreshrateFileNode,
                              mPanelIndex == 0           ? "primary"
                                      : mPanelIndex == 1 ? "secondary"
                                                         : "unknown");
        return nodeName.c_str();
    }

    void updateBrightnessTable(std::unique_ptr<const IBrightnessTable>& table);
    const BrightnessRangeMap& getBrightnessRanges() const {
        return mKernelBrightnessTable.GetBrightnessRangeMap();
    }

    /*
     * WARNING: This enum is parsed by Battery Historian. Add new values, but
     *  do not modify/remove existing ones. Alternatively, consult with the
     *  Battery Historian team (b/239640926).
     */
    enum class BrightnessRange : uint32_t {
        NORMAL = 0,
        HBM = 1,
        MAX,
    };

    /*
     * WARNING: This enum is parsed by Battery Historian. Add new values, but
     *  do not modify/remove existing ones. Alternatively, consult with the
     *  Battery Historian team (b/239640926).
     */
    enum class HbmMode {
        OFF = 0,
        ON_IRC_ON = 1,
        ON_IRC_OFF = 2,
    };

    /*
     * LHBM command need take a couple of frames to become effective
     * DISABLED - finish sending disabling command to panel
     * ENABLED - panel finishes boosting brightness to the peak value
     * ENABLING - finish sending enabling command to panel (panel begins boosting brightness)
     * Note: the definition should be consistent with kernel driver
     */
    enum class LhbmMode {
        DISABLED = 0,
        ENABLED = 1,
        ENABLING = 2,
    };

    /*
     * BrightnessDimmingUsage:
     * NORMAL- enable dimming
     * HBM-    enable dimming only for hbm transition
     * NONE-   disable dimming
     *
     * WARNING: This enum is parsed by Battery Historian. Add new values, but
     *  do not modify/remove existing ones. Alternatively, consult with the
     *  Battery Historian team (b/239640926).
     */
    enum class BrightnessDimmingUsage {
        NORMAL = 0,
        HBM = 1,
        NONE,
    };

    static constexpr const char *kLocalHbmModeFileNode =
                "/sys/class/backlight/panel%d-backlight/local_hbm_mode";
    static constexpr const char* kDimBrightnessFileNode =
            "/sys/class/backlight/panel%d-backlight/dim_brightness";
    static constexpr const char* kRefreshrateFileNode =
            "/sys/devices/platform/exynos-drm/%s-panel/refresh_rate";

private:
    // This is a backup implementation of brightness table. It would be applied only when the system
    // failed to initiate libdisplaycolor. The complete implementation is class
    // DisplayData::BrightnessTable
    class LinearBrightnessTable : public IBrightnessTable {
    public:
        LinearBrightnessTable() : mIsValid(false) {}
        void Init(const struct brightness_capability* cap);
        bool IsValid() const { return mIsValid; }
        const BrightnessRangeMap& GetBrightnessRangeMap() const { return mBrightnessRanges; }

        /* IBrightnessTable functions */
        std::optional<std::reference_wrapper<const DisplayBrightnessRange>> GetBrightnessRange(
                BrightnessMode bm) const override {
            if (mBrightnessRanges.count(bm) == 0) {
                return std::nullopt;
            }
            return mBrightnessRanges.at(bm);
        }
        std::optional<float> BrightnessToNits(float brightness, BrightnessMode& bm) const override;
        std::optional<float> NitsToBrightness(float nits) const override;
        std::optional<float> DbvToBrightness(uint32_t dbv) const override;
        std::optional<uint32_t> NitsToDbv(BrightnessMode bm, float nits) const override;
        std::optional<float> DbvToNits(BrightnessMode bm, uint32_t dbv) const override;

        BrightnessMode GetBrightnessMode(float brightness) const {
            for (const auto& [mode, range] : mBrightnessRanges) {
                if (((!range.brightness_min_exclusive && brightness == range.brightness_min) ||
                     brightness > range.brightness_min) &&
                    brightness <= range.brightness_max) {
                    return mode;
                }
            }
            // return BM_MAX if there is no matching range
            return BrightnessMode::BM_MAX;
        }

        BrightnessMode GetBrightnessModeForNits(float nits) const {
            for (const auto& [mode, range] : mBrightnessRanges) {
                if (nits >= range.nits_min && nits <= range.nits_max) {
                    return mode;
                }
            }
            // return BM_INVALID if there is no matching range
            return BrightnessMode::BM_INVALID;
        }

        BrightnessMode getBrightnessModeForDbv(uint32_t dbv) const {
            for (const auto& [mode, range] : mBrightnessRanges) {
                if (dbv >= range.dbv_min && dbv <= range.dbv_max) {
                    return mode;
                }
            }
            // return BM_INVALID if there is no matching range
            return BrightnessMode::BM_INVALID;
        }

    private:
        static void setBrightnessRangeFromAttribute(const struct brightness_attribute& attr,
                                                    displaycolor::DisplayBrightnessRange& range) {
            range.nits_min = attr.nits.min;
            range.nits_max = attr.nits.max;
            range.dbv_min = attr.level.min;
            range.dbv_max = attr.level.max;
            range.brightness_min_exclusive = false;
            range.brightness_min = static_cast<float>(attr.percentage.min) / 100.0f;
            range.brightness_max = static_cast<float>(attr.percentage.max) / 100.0f;
        }
        /**
         * Implement linear interpolation/extrapolation formula:
         *  y = y1+(y2-y1)*(x-x1)/(x2-x1)
         * Return NAN for following cases:
         *  - Attempt to do extrapolation when x1==x2
         *  - Undefined output when (x2 == x1) and (y2 != y1)
         */
        static inline float LinearInterpolation(float x, float x1, float x2, float y1, float y2) {
            if (x2 == x1) {
                if (x != x1) {
                    ALOGE("%s: attempt to do extrapolation when x1==x2", __func__);
                    return NAN;
                }
                if (y2 == y1) {
                    // This is considered a normal case. (interpolation between a single point)
                    return y1;
                } else {
                    // The output is undefined when (y1!=y2)
                    ALOGE("%s: undefined output when (x2 == x1) and (y2 != y1)", __func__);
                    return NAN;
                }
            }
            float t = (x - x1) / (x2 - x1);
            return y1 + (y2 - y1) * t;
        }
        inline bool SupportHBM() const {
            return mBrightnessRanges.count(BrightnessMode::BM_HBM) > 0;
        }
        bool mIsValid;
        BrightnessRangeMap mBrightnessRanges;
    };
    // sync brightness change for mixed composition when there is more than 50% luminance change.
    // The percentage is calculated as:
    //        (big_lumi - small_lumi) / small_lumi
    // For mixed composition, if remove brightness animations, the minimum brightness jump is
    // between nbm peak and hbm peak. 50% will cover known panels
    static constexpr float kBrightnessSyncThreshold = 0.5f;
    // Worst case for panel with brightness range 2 nits to 1000 nits.
    static constexpr float kGhbmMinDimRatio = 0.002;
    static constexpr int32_t kHbmDimmingTimeUs = 5000000;
    static constexpr const char *kGlobalHbmModeFileNode =
                "/sys/class/backlight/panel%d-backlight/hbm_mode";
    static constexpr const char* kDimmingUsagePropName =
            "vendor.display.%d.brightness.dimming.usage";
    static constexpr const char* kDimmingHbmTimePropName =
            "vendor.display.%d.brightness.dimming.hbm_time";
    static constexpr const char* kGlobalAclModeFileNode =
            "/sys/class/backlight/panel%d-backlight/acl_mode";
    static constexpr const char* kAclModeDefaultPropName =
            "vendor.display.%d.brightness.acl.default";

    int queryBrightness(float brightness, bool* ghbm = nullptr, uint32_t* level = nullptr,
                        float *nits = nullptr);
    void initBrightnessTable(const DrmDevice& device, const DrmConnector& connector);
    void initBrightnessSysfs();
    void initCabcSysfs();
    void initDimmingUsage();
    int applyBrightnessViaSysfs(uint32_t level);
    int applyCabcModeViaSysfs(uint8_t mode);
    int updateStates(); // REQUIRES(mBrightnessMutex)
    void dimmingThread();
    void processDimmingOff();
    int updateAclMode();

    void parseHbmModeEnums(const DrmProperty& property);

    void printBrightnessStates(const char* path); // REQUIRES(mBrightnessMutex)

    bool mLhbmSupported = false;
    bool mGhbmSupported = false;
    bool mDbmSupported = false;
    bool mBrightnessIntfSupported = false;
    LinearBrightnessTable mKernelBrightnessTable;
    // External object from libdisplaycolor
    std::unique_ptr<const IBrightnessTable> mBrightnessTable;

    int32_t mPanelIndex;
    DrmEnumParser::MapHal2DrmEnum mHbmModeEnums;

    // brightness state
    std::recursive_mutex mBrightnessMutex;
    // requests
    CtrlValue<bool> mEnhanceHbmReq;       // GUARDED_BY(mBrightnessMutex)
    CtrlValue<bool> mLhbmReq;             // GUARDED_BY(mBrightnessMutex)
    CtrlValue<float> mBrightnessFloatReq; // GUARDED_BY(mBrightnessMutex)
    CtrlValue<bool> mInstantHbmReq;       // GUARDED_BY(mBrightnessMutex)
    // states to drm after updateStates call
    CtrlValue<uint32_t> mBrightnessLevel; // GUARDED_BY(mBrightnessMutex)
    CtrlValue<HbmMode> mGhbm;             // GUARDED_BY(mBrightnessMutex)
    CtrlValue<bool> mDimming;             // GUARDED_BY(mBrightnessMutex)
    CtrlValue<bool> mLhbm;                // GUARDED_BY(mBrightnessMutex)
    CtrlValue<bool> mSdrDim;              // GUARDED_BY(mBrightnessMutex)
    CtrlValue<bool> mPrevSdrDim;          // GUARDED_BY(mBrightnessMutex)
    CtrlValue<bool> mDimBrightnessReq;    // GUARDED_BY(mBrightnessMutex)
    CtrlValue<uint32_t> mOperationRate;   // GUARDED_BY(mBrightnessMutex)

    // Indicating if the last LHBM on has changed the brightness level
    bool mLhbmBrightnessAdj = false;

    // Indicating if brightness updates are ignored
    bool mIgnoreBrightnessUpdateRequests = false;

    std::function<void(void)> mFrameRefresh;
    CtrlValue<HdrLayerState> mHdrLayerState;
    CtrlValue<ColorRenderIntent> mColorRenderIntent;

    // these are used by sysfs path to wait drm path bl change task
    // indicationg an unchecked LHBM change in drm path
    std::atomic<bool> mUncheckedLhbmRequest = false;
    std::atomic<bool> mPendingLhbmStatus = false;
    // indicationg an unchecked GHBM change in drm path
    std::atomic<bool> mUncheckedGbhmRequest = false;
    std::atomic<HbmMode> mPendingGhbmStatus = HbmMode::OFF;
    // indicating an unchecked brightness change in drm path
    std::atomic<bool> mUncheckedBlRequest = false;
    std::atomic<uint32_t> mPendingBl = 0;

    // these are dimming related
    BrightnessDimmingUsage mBrightnessDimmingUsage = BrightnessDimmingUsage::NORMAL;
    bool mHbmDimming = false; // GUARDED_BY(mBrightnessMutex)
    int32_t mHbmDimmingTimeUs = 0;
    std::thread mDimmingThread;
    std::atomic<bool> mDimmingThreadRunning;
    ::android::sp<::android::Looper> mDimmingLooper;
    ::android::sp<DimmingMsgHandler> mDimmingHandler;

    // sysfs path
    std::ofstream mBrightnessOfs;
    uint32_t mMaxBrightness = 0; // read from sysfs
    std::ofstream mCabcModeOfs;
    bool mCabcSupport = false;
    uint32_t mDimBrightness = 0;

    // Note IRC or dimming is not in consideration for now.
    float mDisplayWhitePointNits = 0;
    float mPrevDisplayWhitePointNits = 0;

    std::function<void(void)> mUpdateDcLhbm;

    // state for control ACL state
    enum class AclMode {
        ACL_OFF = 0,
        ACL_NORMAL,
        ACL_ENHANCED,
    };

    std::ofstream mAclModeOfs;
    CtrlValue<AclMode> mAclMode;
    AclMode mAclModeDefault = AclMode::ACL_OFF;

    // state for control CABC state
    enum class CabcMode {
        OFF = 0,
        CABC_UI_MODE,
        CABC_STILL_MODE,
        CABC_MOVIE_MODE,
    };

    static constexpr const char* kLocalCabcModeFileNode =
            "/sys/class/backlight/panel%d-backlight/cabc_mode";
    std::recursive_mutex mCabcModeMutex;
    bool mOutdoorVisibility = false; // GUARDED_BY(mCabcModeMutex)
    bool isHdrLayerOn() { return mHdrLayerState.get() == HdrLayerState::kHdrLarge; }
    CtrlValue<CabcMode> mCabcMode; // GUARDED_BY(mCabcModeMutex)
};

#endif // _BRIGHTNESS_CONTROLLER_H_
