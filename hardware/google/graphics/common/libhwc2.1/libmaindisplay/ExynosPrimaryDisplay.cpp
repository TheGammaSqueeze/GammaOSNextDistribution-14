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
//#define LOG_NDEBUG 0

#define ATRACE_TAG (ATRACE_TAG_GRAPHICS | ATRACE_TAG_HAL)

#include "ExynosPrimaryDisplay.h"

#include <linux/fb.h>
#include <poll.h>

#include <chrono>
#include <fstream>

#include "../libvrr/interface/Panel_def.h"
#include "BrightnessController.h"
#include "ExynosDevice.h"
#include "ExynosDisplayDrmInterface.h"
#include "ExynosDisplayDrmInterfaceModule.h"
#include "ExynosExternalDisplay.h"
#include "ExynosHWCDebug.h"
#include "ExynosHWCHelper.h"
#include "ExynosLayer.h"
#include "HistogramController.h"

extern struct exynos_hwc_control exynosHWCControl;

using namespace SOC_VERSION;

namespace {

using android::hardware::graphics::composer::kPanelRefreshCtrlFrameInsertionAutoMode;
using android::hardware::graphics::composer::kPanelRefreshCtrlIdleEnabled;
using android::hardware::graphics::composer::kPanelRefreshCtrlTeTypeChangeable;
using android::hardware::graphics::composer::kRefreshControlNodeEnabled;
using android::hardware::graphics::composer::kRefreshControlNodeName;

constexpr auto nsecsPerSec = std::chrono::nanoseconds(1s).count();

inline constexpr int kDefaultNotifyExpectedPresentConfigHeadsUpNs =
        std::chrono::nanoseconds(30ms).count();
inline constexpr int kDefaultNotifyExpectedPresentConfigTimeoutNs =
        std::chrono::nanoseconds(30ms).count();

static constexpr int kMaximumPropertyIdentifierLength = 128;

static const std::map<const DisplayType, const std::string> panelSysfsPath =
        {{DisplayType::DISPLAY_PRIMARY, "/sys/devices/platform/exynos-drm/primary-panel/"},
#ifdef USES_IDISPLAY_INTF_SEC
         {DisplayType::DISPLAY_SECONDARY, "/sys/devices/platform/exynos-drm/secondary-panel/"}
#endif

};
} // namespace

static String8 getPropertyBootModeStr(const int32_t dispId) {
    String8 str;
    if (dispId == 0) {
        str.appendFormat("persist.vendor.display.primary.boot_config");
    } else {
        str.appendFormat("persist.vendor.display.%d.primary.boot_config", dispId);
    }
    return str;
}

static std::string loadPanelGammaCalibration(const std::string &file) {
    std::ifstream ifs(file);

    if (!ifs.is_open()) {
        ALOGW("Unable to open gamma calibration '%s', error = %s", file.c_str(), strerror(errno));
        return {};
    }

    std::string raw_data, gamma;
    char ch;
    while (std::getline(ifs, raw_data, '\r')) {
        gamma.append(raw_data);
        gamma.append(1, ' ');
        ifs.get(ch);
        if (ch != '\n') {
            gamma.append(1, ch);
        }
    }
    ifs.close();

    /* eliminate space character in the last byte */
    if (!gamma.empty()) {
        gamma.pop_back();
    }

    return gamma;
}

ExynosPrimaryDisplay::ExynosPrimaryDisplay(uint32_t index, ExynosDevice* device,
                                           const std::string& displayName)
      : ExynosDisplay(HWC_DISPLAY_PRIMARY, index, device, displayName),
        mUseBlockingZoneForMinIdleRefreshRate(false),
        mMinIdleRefreshRate(0),
        mRrThrottleFps{0},
        mRrThrottleNanos{0},
        mRefreshRateDelayNanos(0),
        mLastRefreshRateAppliedNanos(0),
        mAppliedActiveConfig(0),
        mDisplayIdleTimerEnabled(false),
        mDisplayIdleTimerNanos{0},
        mDisplayIdleDelayNanos(-1),
        mDisplayNeedHandleIdleExit(false) {
    // TODO : Hard coded here
    mNumMaxPriorityAllowed = 5;

    /* Initialization */
    mFramesToReachLhbmPeakBrightness =
            property_get_int32("vendor.primarydisplay.lhbm.frames_to_reach_peak_brightness", 3);

    /* TODO(b/274705046): use drm properties instead of system properties */
    mDefaultMinIdleRefreshRate =
            property_get_int32("vendor.primarydisplay.min_idle_refresh_rate.default", 0);
    mMinIdleRefreshRateForBlockingZone =
            property_get_int32("vendor.primarydisplay.min_idle_refresh_rate.blocking_zone", 0);
    mDbvThresholdForBlockingZone =
            property_get_int32("vendor.primarydisplay.min_idle_refresh_rate.blocking_zone_dbv", 0);
    if (mDefaultMinIdleRefreshRate && mMinIdleRefreshRateForBlockingZone &&
        mDbvThresholdForBlockingZone) {
        mUseBlockingZoneForMinIdleRefreshRate = true;
        ALOGI("%s min_default=%d min_blocking_zone=%d dbv_blocking_zone=%d", __func__,
              mDefaultMinIdleRefreshRate, mMinIdleRefreshRateForBlockingZone,
              mDbvThresholdForBlockingZone);
    }

    DisplayType displayType = getDcDisplayType();
    std::string displayTypeIdentifier;
    if (displayType == DisplayType::DISPLAY_PRIMARY) {
        displayTypeIdentifier = "primarydisplay";
    } else if (displayType == DisplayType::DISPLAY_EXTERNAL) {
        displayTypeIdentifier = "externaldisplay";
    }
#ifdef USES_IDISPLAY_INTF_SEC
    else if (displayType == DisplayType::DISPLAY_SECONDARY) {
        displayTypeIdentifier = "secondarydisplay";
    }
#endif
    if (!displayTypeIdentifier.empty()) {
        char pathBuffer[kMaximumPropertyIdentifierLength];
        sprintf(pathBuffer, "ro.vendor.%s.vrr.enabled", displayTypeIdentifier.c_str());
        mVrrSettings.enabled = property_get_bool(pathBuffer, false);
        if (mVrrSettings.enabled) {
            sprintf(pathBuffer, "ro.vendor.%s.vrr.expected_present.headsup_ns",
                    displayTypeIdentifier.c_str());
            mVrrSettings.notifyExpectedPresentConfig.HeadsUpNs =
                    property_get_int32(pathBuffer, kDefaultNotifyExpectedPresentConfigHeadsUpNs);
            sprintf(pathBuffer, "ro.vendor.%s.vrr.expected_present.timeout_ns",
                    displayTypeIdentifier.c_str());
            mVrrSettings.notifyExpectedPresentConfig.TimeoutNs =
                    property_get_int32(pathBuffer, kDefaultNotifyExpectedPresentConfigTimeoutNs);
            mVrrSettings.configChangeCallback =
                    std::bind(&ExynosPrimaryDisplay::onConfigChange, this, std::placeholders::_1);
        } else {
            std::string displayFileNodePath = getPanelSysfsPath();
            if (displayFileNodePath.empty()) {
                ALOGE("%s(): cannot find file node %s of display %s", __func__,
                      displayFileNodePath.c_str(), mDisplayName.c_str());
            } else {
                FileNodeWriter fileNodeWriter(displayFileNodePath);
                auto content = fileNodeWriter.read(kRefreshControlNodeName);
                if (content.has_value() &&
                    !(content.value().compare(0, kRefreshControlNodeEnabled.length(),
                                              kRefreshControlNodeEnabled))) {
                    uint32_t cmd = kPanelRefreshCtrlFrameInsertionAutoMode |
                            kPanelRefreshCtrlIdleEnabled | kPanelRefreshCtrlTeTypeChangeable;
                    bool ret = fileNodeWriter.WriteCommandString(kRefreshControlNodeName, cmd);
                    if (!ret) {
                        ALOGE("%s(): write command to file node %s%s failed.", __func__,
                              displayFileNodePath.c_str(), kRefreshControlNodeName.c_str());
                    }
                } else {
                    ALOGI("%s(): refresh control is not supported", __func__);
                }
            }
        }
    }

    // Allow to enable dynamic recomposition after every power on
    // since it will always be disabled for every power off
    // TODO(b/268474771): to enable DR by default if video mode panel is detected
    if (property_get_int32("vendor.display.dynamic_recomposition", 0) & (1 << index)) {
        mDRDefault = true;
        mDREnable = true;
    }

    // Prepare multi resolution
    // Will be exynosHWCControl.multiResoultion
    mResolutionInfo.nNum = 1;
    mResolutionInfo.nResolution[0].w = 1440;
    mResolutionInfo.nResolution[0].h = 2960;
    mResolutionInfo.nDSCYSliceSize[0] = 40;
    mResolutionInfo.nDSCXSliceSize[0] = 1440 / 2;
    mResolutionInfo.nPanelType[0] = PANEL_DSC;
    mResolutionInfo.nResolution[1].w = 1080;
    mResolutionInfo.nResolution[1].h = 2220;
    mResolutionInfo.nDSCYSliceSize[1] = 30;
    mResolutionInfo.nDSCXSliceSize[1] = 1080 / 2;
    mResolutionInfo.nPanelType[1] = PANEL_DSC;
    mResolutionInfo.nResolution[2].w = 720;
    mResolutionInfo.nResolution[2].h = 1480;
    mResolutionInfo.nDSCYSliceSize[2] = 74;
    mResolutionInfo.nDSCXSliceSize[2] = 720;
    mResolutionInfo.nPanelType[2] = PANEL_LEGACY;

    char value[PROPERTY_VALUE_MAX];
    const char *earlyWakeupNodeBase = early_wakeup_node_0_base;
    if (getDcDisplayType() == DisplayType::DISPLAY_SECONDARY &&
        property_get("vendor.display.secondary_early_wakeup_node", value, "") > 0) {
        earlyWakeupNodeBase = value;
    }
    mEarlyWakeupDispFd = fopen(earlyWakeupNodeBase, "w");
    if (mEarlyWakeupDispFd == nullptr)
        ALOGE("open %s failed! %s", earlyWakeupNodeBase, strerror(errno));
    mBrightnessController = std::make_unique<BrightnessController>(
            mIndex, [this]() { mDevice->onRefresh(mDisplayId); },
            [this]() { updatePresentColorConversionInfo(); });
    mHistogramController = std::make_unique<HistogramController>(this);

    mDisplayControl.multiThreadedPresent = true;
}

ExynosPrimaryDisplay::~ExynosPrimaryDisplay()
{
    if (mEarlyWakeupDispFd) {
        fclose(mEarlyWakeupDispFd);
        mEarlyWakeupDispFd = nullptr;
    }

    if (mDisplayNeedHandleIdleExitOfs.is_open()) {
        mDisplayNeedHandleIdleExitOfs.close();
    }
}

void ExynosPrimaryDisplay::setDDIScalerEnable(int width, int height) {

    if (exynosHWCControl.setDDIScaler == false) return;

    ALOGI("DDISCALER Info : setDDIScalerEnable(w=%d,h=%d)", width, height);
    mNewScaledWidth = width;
    mNewScaledHeight = height;
    mXres = width;
    mYres = height;
}

int ExynosPrimaryDisplay::getDDIScalerMode(int width, int height) {

    if (exynosHWCControl.setDDIScaler == false) return 1;

    // Check if panel support support resolution or not.
    for (uint32_t i=0; i < mResolutionInfo.nNum; i++) {
        if (mResolutionInfo.nResolution[i].w * mResolutionInfo.nResolution[i].h ==
                static_cast<uint32_t>(width * height))
            return i + 1;
    }

    return 1; // WQHD
}

int32_t ExynosPrimaryDisplay::doDisplayConfigInternal(hwc2_config_t config) {
    if (!mPowerModeState.has_value() || (*mPowerModeState != HWC2_POWER_MODE_ON) ||
        !isConfigSettingEnabled()) {
        mPendingConfig = config;
        mConfigRequestState = hwc_request_state_t::SET_CONFIG_STATE_DONE;
        DISPLAY_LOGI("%s:: Pending desired Config: %d", __func__, config);
        return NO_ERROR;
    }
    return ExynosDisplay::doDisplayConfigInternal(config);
}

int32_t ExynosPrimaryDisplay::getActiveConfigInternal(hwc2_config_t *outConfig) {
    if (outConfig && mPendingConfig != UINT_MAX) {
        *outConfig = mPendingConfig;
        return HWC2_ERROR_NONE;
    }
    return ExynosDisplay::getActiveConfigInternal(outConfig);
}

int32_t ExynosPrimaryDisplay::setActiveConfigInternal(hwc2_config_t config, bool force) {
    hwc2_config_t cur_config;

    getActiveConfigInternal(&cur_config);
    if (cur_config == config) {
        ALOGI("%s:: Same display config is set", __func__);
        return HWC2_ERROR_NONE;
    }
    if (!mPowerModeState.has_value() || (*mPowerModeState != HWC2_POWER_MODE_ON) ||
        !isConfigSettingEnabled()) {
        mPendingConfig = config;
        return HWC2_ERROR_NONE;
    }
    return ExynosDisplay::setActiveConfigInternal(config, force);
}

// If a display is Off, applyPendingConfig() calls to setActiveConfig() that also
// power on the display by a blocking commit.
int32_t ExynosPrimaryDisplay::applyPendingConfig() {
    if (!isConfigSettingEnabled()) {
        ALOGI("%s:: config setting is disabled", __func__);
        if (mPowerModeState.has_value() && (*mPowerModeState == HWC2_POWER_MODE_ON)) {
            ALOGI("%s:: skip apply pending config", __func__);
            return HWC2_ERROR_NONE;
        }
    }

    hwc2_config_t config;
    if (mPendingConfig != UINT_MAX) {
        ALOGI("%s:: mPendingConfig: %d", __func__, mPendingConfig);
        config = mPendingConfig;
        mPendingConfig = UINT_MAX;
    } else {
        getActiveConfigInternal(&config);
    }

    return ExynosDisplay::setActiveConfigInternal(config, true);
}

int32_t ExynosPrimaryDisplay::setBootDisplayConfig(int32_t config) {
    auto hwcConfig = static_cast<hwc2_config_t>(config);

    const auto &it = mDisplayConfigs.find(hwcConfig);
    if (it == mDisplayConfigs.end()) {
        DISPLAY_LOGE("%s: invalid config %d", __func__, config);
        return HWC2_ERROR_BAD_CONFIG;
    }

    const auto &mode = it->second;
    if (mode.vsyncPeriod == 0)
        return HWC2_ERROR_BAD_CONFIG;

    char modeStr[PROPERTY_VALUE_MAX];
    int ret = snprintf(modeStr, sizeof(modeStr), "%dx%d@%d:%d", mode.width, mode.height,
                       mode.refreshRate, nanoSec2Hz(mode.vsyncPeriod));
    if (ret <= 0)
        return HWC2_ERROR_BAD_CONFIG;

    ALOGD("%s: mode=%s (%d)", __func__, modeStr, config);
    ret = property_set(getPropertyBootModeStr(mDisplayId).c_str(), modeStr);

    return !ret ? HWC2_ERROR_NONE : HWC2_ERROR_BAD_CONFIG;
}

int32_t ExynosPrimaryDisplay::clearBootDisplayConfig() {
    auto ret = property_set(getPropertyBootModeStr(mDisplayId).c_str(), nullptr);

    ALOGD("%s: clearing boot mode", __func__);
    return !ret ? HWC2_ERROR_NONE : HWC2_ERROR_BAD_CONFIG;
}

int32_t ExynosPrimaryDisplay::getPreferredDisplayConfigInternal(int32_t *outConfig) {
    char modeStr[PROPERTY_VALUE_MAX];
    auto ret = property_get(getPropertyBootModeStr(mDisplayId).c_str(), modeStr, "");

    if (ret <= 0) {
        return mDisplayInterface->getDefaultModeId(outConfig);
    }

    int width, height;
    int fps = 0, vsyncRate = 0;

    ret = sscanf(modeStr, "%dx%d@%d:%d", &width, &height, &fps, &vsyncRate);
    if (ret < 4) {
        ret = sscanf(modeStr, "%dx%d@%d", &width, &height, &fps);
        if ((ret < 3) || !fps) {
            ALOGW("%s: unable to find boot config for mode: %s", __func__, modeStr);
            return HWC2_ERROR_BAD_CONFIG;
        }
        if (lookupDisplayConfigs(width, height, fps, fps, outConfig) != HWC2_ERROR_NONE) {
            ALOGE("%s: kernel doesn't support mode: %s", __func__, modeStr);
            return HWC2_ERROR_BAD_CONFIG;
        }
        ret = setBootDisplayConfig(*outConfig);
        if (ret == HWC2_ERROR_NONE)
            ALOGI("%s: succeeded to replace %s with new format", __func__, modeStr);
        else
            ALOGE("%s: failed to replace %s with new format", __func__, modeStr);
        return ret;
    }
    if (!fps || !vsyncRate || (fps > vsyncRate)) {
        ALOGE("%s: bad boot config: %s", __func__, modeStr);
        return HWC2_ERROR_BAD_CONFIG;
    }
    return lookupDisplayConfigs(width, height, fps, vsyncRate, outConfig);
}

int32_t ExynosPrimaryDisplay::setPowerOn() {
    ATRACE_CALL();
    updateAppliedActiveConfig(0, 0);
    int ret = NO_ERROR;
    if (mDisplayId != 0 || !mFirstPowerOn) {
        if (mDevice->hasOtherDisplayOn(this)) {
            mResourceManager->prepareResources(mDisplayId);
            // TODO: This is useful for cmd mode, and b/282094671 tries to handles video mode
            mDisplayInterface->triggerClearDisplayPlanes();
        }
        ret = applyPendingConfig();
    }

    if (!mPowerModeState.has_value() || (*mPowerModeState == HWC2_POWER_MODE_OFF)) {
        // check the dynamic recomposition thread by following display
        mDevice->checkDynamicRecompositionThread();
        if (ret) {
            mDisplayInterface->setPowerMode(HWC2_POWER_MODE_ON);
        }
        setGeometryChanged(GEOMETRY_DISPLAY_POWER_ON);
    }

    {
        std::lock_guard<std::mutex> lock(mPowerModeMutex);
        mPowerModeState = HWC2_POWER_MODE_ON;
        if (mNotifyPowerOn) {
            mPowerOnCondition.notify_one();
            mNotifyPowerOn = false;
        }
    }

    if (mFirstPowerOn) {
        firstPowerOn();
    }

    return HWC2_ERROR_NONE;
}

int32_t ExynosPrimaryDisplay::setPowerOff() {
    ATRACE_CALL();

    clearDisplay(true);

    // check the dynamic recomposition thread by following display
    mDevice->checkDynamicRecompositionThread();

    mDisplayInterface->setPowerMode(HWC2_POWER_MODE_OFF);

    {
        std::lock_guard<std::mutex> lock(mPowerModeMutex);
        mPowerModeState = HWC2_POWER_MODE_OFF;
    }

    /* It should be called from validate() when the screen is on */
    mSkipFrame = true;
    setGeometryChanged(GEOMETRY_DISPLAY_POWER_OFF);
    if ((mRenderingState >= RENDERING_STATE_VALIDATED) &&
        (mRenderingState < RENDERING_STATE_PRESENTED))
        closeFencesForSkipFrame(RENDERING_STATE_VALIDATED);
    mRenderingState = RENDERING_STATE_NONE;

    // in the case user turns off screen when LHBM is on
    // TODO: b/236433238 considering a lock for mLhbmOn state
    mLhbmOn = false;
    return HWC2_ERROR_NONE;
}

int32_t ExynosPrimaryDisplay::setPowerDoze(hwc2_power_mode_t mode) {
    ATRACE_CALL();

    if (!mDisplayInterface->isDozeModeAvailable()) {
        return HWC2_ERROR_UNSUPPORTED;
    }

    if (mPowerModeState.has_value() &&
        ((*mPowerModeState == HWC2_POWER_MODE_OFF) || (*mPowerModeState == HWC2_POWER_MODE_ON))) {
        if (mDisplayInterface->setLowPowerMode()) {
            ALOGI("Not support LP mode.");
            return HWC2_ERROR_UNSUPPORTED;
        }
    }

    {
        std::lock_guard<std::mutex> lock(mPowerModeMutex);
        mPowerModeState = mode;
    }

    // LHBM will be disabled in the kernel while entering AOD mode if it's
    // already enabled. Reset the state to avoid the sync problem.
    mBrightnessController->resetLhbmState();
    mLhbmOn = false;

    ExynosDisplay::updateRefreshRateHint();

    return HWC2_ERROR_NONE;
}

int32_t ExynosPrimaryDisplay::setPowerMode(int32_t mode) {
    Mutex::Autolock lock(mDisplayMutex);

    if (mode == static_cast<int32_t>(ext_hwc2_power_mode_t::PAUSE)) {
        mode = HWC2_POWER_MODE_OFF;
        mPauseDisplay = true;
    } else if (mode == static_cast<int32_t>(ext_hwc2_power_mode_t::RESUME)) {
        mode = HWC2_POWER_MODE_ON;
        mPauseDisplay = false;
    } else if (mPauseDisplay) {
        ALOGI("Skip power mode transition due to pause display.");
        return HWC2_ERROR_NONE;
    }

    if (mPowerModeState.has_value() && (mode == static_cast<int32_t>(mPowerModeState.value()))) {
        ALOGI("Skip power mode transition due to the same power state.");
        return HWC2_ERROR_NONE;
    }

    int fb_blank = (mode != HWC2_POWER_MODE_OFF) ? FB_BLANK_UNBLANK : FB_BLANK_POWERDOWN;
    ALOGD("%s:: FBIOBLANK mode(%d), blank(%d)", __func__, mode, fb_blank);

    if (fb_blank == FB_BLANK_POWERDOWN)
        mDREnable = false;
    else
        mDREnable = mDRDefault;

    if (mOperationRateManager) {
        mOperationRateManager->onPowerMode(mode);
        mBrightnessController->processOperationRate(
                mOperationRateManager->getTargetOperationRate());
    }

    int32_t res = HWC2_ERROR_BAD_PARAMETER;
    switch (mode) {
        case HWC2_POWER_MODE_DOZE:
        case HWC2_POWER_MODE_DOZE_SUSPEND: {
            if (mode == HWC2_POWER_MODE_DOZE && mDisplayInterface->needRefreshOnLP()) {
                ALOGI("Refresh before setting power doze.");
                mDevice->onRefresh(mDisplayId);
            }
            res = setPowerDoze(static_cast<hwc2_power_mode_t>(mode));
            break;
        }
        case HWC2_POWER_MODE_OFF:
            res = setPowerOff();
            break;
        case HWC2_POWER_MODE_ON:
            res = setPowerOn();
            break;
        default:
            return res;
    }
    if (res != HWC2_ERROR_NONE) {
        return res;
    }

    ExynosDisplay::updateRefreshRateHint();
    if (mVariableRefreshRateController) {
        mVariableRefreshRateController->setPowerMode(mode);
    }
    return res;
}

void ExynosPrimaryDisplay::firstPowerOn() {
    SetCurrentPanelGammaSource(DisplayType::DISPLAY_PRIMARY, PanelGammaSource::GAMMA_CALIBRATION);
    mFirstPowerOn = false;
    getDisplayIdleTimerEnabled(mDisplayIdleTimerEnabled);
    initDisplayHandleIdleExit();
}

bool ExynosPrimaryDisplay::getHDRException(ExynosLayer* __unused layer)
{
    return false;
}

void ExynosPrimaryDisplay::initDisplayInterface(uint32_t interfaceType)
{
    if (interfaceType == INTERFACE_TYPE_DRM)
        mDisplayInterface = std::make_unique<ExynosPrimaryDisplayDrmInterfaceModule>((ExynosDisplay *)this);
    else
        LOG_ALWAYS_FATAL("%s::Unknown interface type(%d)",
                __func__, interfaceType);
    mDisplayInterface->init(this);

    if (mVrrSettings.enabled) {
        mDisplayInterface->setVrrSettings(mVrrSettings);
    }

    mDpuData.init(mMaxWindowNum, mDevice->getSpecialPlaneNum(mDisplayId));
    mLastDpuData.init(mMaxWindowNum, mDevice->getSpecialPlaneNum(mDisplayId));
    ALOGI("window configs size(%zu) rcd configs zie(%zu)", mDpuData.configs.size(),
          mDpuData.rcdConfigs.size());
}

std::string ExynosPrimaryDisplay::getPanelSysfsPath(const DisplayType& type) const {
    if ((type < DisplayType::DISPLAY_PRIMARY) || (type >= DisplayType::DISPLAY_MAX)) {
        ALOGE("Invalid display panel type %d", type);
        return {};
    }

    const auto& iter = panelSysfsPath.find(type);
    if (iter == panelSysfsPath.end()) {
        return {};
    }

    return iter->second;
}

int32_t ExynosPrimaryDisplay::SetCurrentPanelGammaSource(const DisplayType type,
                                                         const PanelGammaSource &source) {
    std::string &&panel_sysfs_path = getPanelSysfsPath(type);
    if (panel_sysfs_path.empty()) {
        return HWC2_ERROR_UNSUPPORTED;
    }

    std::ifstream ifs;
    std::string &&path = panel_sysfs_path + "panel_name";
    ifs.open(path, std::ifstream::in);
    if (!ifs.is_open()) {
        ALOGW("Unable to access panel name path '%s' (%s)", path.c_str(), strerror(errno));
        return HWC2_ERROR_UNSUPPORTED;
    }
    std::string panel_name;
    std::getline(ifs, panel_name);
    ifs.close();

    path = panel_sysfs_path + "serial_number";
    ifs.open(path, std::ifstream::in);
    if (!ifs.is_open()) {
        ALOGW("Unable to access panel id path '%s' (%s)", path.c_str(), strerror(errno));
        return HWC2_ERROR_UNSUPPORTED;
    }
    std::string panel_id;
    std::getline(ifs, panel_id);
    ifs.close();

    std::string gamma_node = panel_sysfs_path + "gamma";
    if (access(gamma_node.c_str(), W_OK)) {
        ALOGW("Unable to access panel gamma calibration node '%s' (%s)", gamma_node.c_str(),
              strerror(errno));
        return HWC2_ERROR_UNSUPPORTED;
    }

    std::string &&gamma_data = "default";
    if (source == PanelGammaSource::GAMMA_CALIBRATION) {
        std::string gamma_cal_file(kDisplayCalFilePath);
        gamma_cal_file.append(kPanelGammaCalFilePrefix)
                .append(1, '_')
                .append(panel_name)
                .append(1, '_')
                .append(panel_id)
                .append(".cal");
        if (access(gamma_cal_file.c_str(), R_OK)) {
            ALOGI("Fail to access `%s` (%s), try golden gamma calibration", gamma_cal_file.c_str(),
                  strerror(errno));
            gamma_cal_file = kDisplayCalFilePath;
            gamma_cal_file.append(kPanelGammaCalFilePrefix)
                    .append(1, '_')
                    .append(panel_name)
                    .append(".cal");
        }
        gamma_data = loadPanelGammaCalibration(gamma_cal_file);
    }

    if (gamma_data.empty()) {
        return HWC2_ERROR_UNSUPPORTED;
    }

    std::ofstream ofs(gamma_node);
    if (!ofs.is_open()) {
        ALOGW("Unable to open gamma node '%s', error = %s", gamma_node.c_str(), strerror(errno));
        return HWC2_ERROR_UNSUPPORTED;
    }
    ofs.write(gamma_data.c_str(), gamma_data.size());
    ofs.close();

    currentPanelGammaSource = source;
    return HWC2_ERROR_NONE;
}

bool ExynosPrimaryDisplay::isLhbmSupported() {
    return mBrightnessController->isLhbmSupported();
}

bool ExynosPrimaryDisplay::isConfigSettingEnabled() {
    int64_t msSinceDisabled =
            (systemTime(SYSTEM_TIME_MONOTONIC) - mConfigSettingDisabledTimestamp) / 1000000;
    return !mConfigSettingDisabled || msSinceDisabled > kConfigDisablingMaxDurationMs;
}

void ExynosPrimaryDisplay::enableConfigSetting(bool en) {
    DISPLAY_ATRACE_INT("ConfigSettingDisabled", !en);
    ALOGI("%s:: mConfigSettingDisabled: %d", __func__, !en);
    if (!en) {
        mConfigSettingDisabled = true;
        mConfigSettingDisabledTimestamp = systemTime(SYSTEM_TIME_MONOTONIC);
        return;
    }

    mConfigSettingDisabled = false;
}

int32_t ExynosPrimaryDisplay::getDisplayConfigs(uint32_t* outNumConfigs,
                                                hwc2_config_t* outConfigs) {
    int32_t ret = ExynosDisplay::getDisplayConfigs(outNumConfigs, outConfigs);
    if (ret == HWC2_ERROR_NONE) {
        if (mVrrSettings.enabled && mDisplayConfigs.size()) {
            if (!mVariableRefreshRateController) {
                mVariableRefreshRateController =
                        VariableRefreshRateController::CreateInstance(this, getPanelName());
                std::unordered_map<hwc2_config_t, VrrConfig_t> vrrConfigs;
                for (const auto& it : mDisplayConfigs) {
                    if (!it.second.vrrConfig.has_value()) {
                        ALOGE("Both pseudo and full VRR modes should include VRR configurations.");
                        return HWC2_ERROR_BAD_CONFIG;
                    }
                    vrrConfigs[it.first] = it.second.vrrConfig.value();
                }
                mVariableRefreshRateController->setVrrConfigurations(std::move(vrrConfigs));
                hwc2_config_t activeConfig;
                if (ExynosDisplay::getActiveConfig(&activeConfig) == HWC2_ERROR_NONE) {
                    mVariableRefreshRateController->setActiveVrrConfiguration(activeConfig);
                    mVariableRefreshRateController->setEnable(true);
                }
            }
        }
    }
    return ret;
}

int32_t ExynosPrimaryDisplay::presentDisplay(int32_t* outRetireFence) {
    auto res = ExynosDisplay::presentDisplay(outRetireFence);
    // Forward presentDisplay if there is a listener.
    const auto presentListener = getPresentListener();
    if (res == HWC2_ERROR_NONE && presentListener) {
        presentListener->onPresent(*outRetireFence);
    }
    return res;
}

void ExynosPrimaryDisplay::onVsync(int64_t timestamp) {
    const auto vsyncListener = getVsyncListener();
    if (vsyncListener) {
        vsyncListener->onVsync(timestamp, 0);
    }
}

int32_t ExynosPrimaryDisplay::notifyExpectedPresent(int64_t timestamp, int32_t frameIntervalNs) {
    if (mVariableRefreshRateController) {
        mVariableRefreshRateController->notifyExpectedPresent(timestamp, frameIntervalNs);
    }
    return NO_ERROR;
}

int32_t ExynosPrimaryDisplay::setPresentTimeoutParameters(
        int timeoutNs, const std::vector<std::pair<uint32_t, uint32_t>>& settings) {
    if (mVariableRefreshRateController) {
        mVariableRefreshRateController->setPresentTimeoutParameters(timeoutNs, settings);
        return NO_ERROR;
    }
    return HWC2_ERROR_UNSUPPORTED;
}

int32_t ExynosPrimaryDisplay::setPresentTimeoutController(uint32_t controllerType) {
    if (mVariableRefreshRateController) {
        mVariableRefreshRateController->setPresentTimeoutController(controllerType);
        return NO_ERROR;
    }
    return HWC2_ERROR_UNSUPPORTED;
}

int32_t ExynosPrimaryDisplay::setLhbmDisplayConfigLocked(uint32_t peakRate) {
    auto hwConfig = mDisplayInterface->getActiveModeId();
    auto config = getConfigId(peakRate, mDisplayConfigs[hwConfig].width,
                              mDisplayConfigs[hwConfig].height);
    if (config == UINT_MAX) {
        DISPLAY_LOGE("%s: failed to get config for rate=%d", __func__, peakRate);
        return -EINVAL;
    }

    if (mPendingConfig == UINT_MAX && mActiveConfig != config) mPendingConfig = mActiveConfig;
    if (config != hwConfig) {
        if (ExynosDisplay::setActiveConfigInternal(config, true) == HWC2_ERROR_NONE) {
            DISPLAY_LOGI("%s: succeeded to set config=%d rate=%d", __func__, config, peakRate);
        } else {
            DISPLAY_LOGW("%s: failed to set config=%d rate=%d", __func__, config, peakRate);
        }
    } else {
        DISPLAY_LOGI("%s: keep config=%d rate=%d", __func__, config, peakRate);
    }
    enableConfigSetting(false);
    return OK;
}

void ExynosPrimaryDisplay::restoreLhbmDisplayConfigLocked() {
    enableConfigSetting(true);
    hwc2_config_t pendingConfig = mPendingConfig;
    auto hwConfig = mDisplayInterface->getActiveModeId();
    if (pendingConfig != UINT_MAX && pendingConfig != hwConfig) {
        if (applyPendingConfig() == HWC2_ERROR_NONE) {
            DISPLAY_LOGI("%s: succeeded to set config=%d rate=%d", __func__, pendingConfig,
                         getRefreshRate(pendingConfig));
        } else {
            DISPLAY_LOGE("%s: failed to set config=%d rate=%d", __func__, pendingConfig,
                         getRefreshRate(pendingConfig));
        }
    } else {
        mPendingConfig = UINT_MAX;
        DISPLAY_LOGI("%s: keep config=%d rate=%d", __func__, hwConfig, getRefreshRate(hwConfig));
    }
}

// This function should be called by other threads (e.g. sensor HAL).
// HWCService can call this function but it should be for test purpose only.
int32_t ExynosPrimaryDisplay::setLhbmState(bool enabled) {
    int ret = OK;
    // NOTE: mLhbmOn could be set to false at any time by setPowerOff in another
    // thread. Make sure no side effect if that happens. Or add lock if we have
    // to when new code is added.
    DISPLAY_ATRACE_CALL();
    DISPLAY_LOGI("%s: enabled=%d", __func__, enabled);
    {
        ATRACE_NAME("wait_for_power_on");
        std::unique_lock<std::mutex> lock(mPowerModeMutex);
        if (!mPowerModeState.has_value() || (*mPowerModeState != HWC2_POWER_MODE_ON)) {
            mNotifyPowerOn = true;
            if (!mPowerOnCondition.wait_for(lock, std::chrono::milliseconds(2000), [this]() {
                    return (mPowerModeState.has_value() &&
                            (*mPowerModeState == HWC2_POWER_MODE_ON));
                })) {
                DISPLAY_LOGW("%s: wait for power mode on timeout !", __func__);
                return TIMED_OUT;
            }
        }
    }

    auto lhbmSysfs = mBrightnessController->GetPanelSysfileByIndex(
            BrightnessController::kLocalHbmModeFileNode);
    ret = mBrightnessController->checkSysfsStatus(lhbmSysfs,
                                         {std::to_string(static_cast<int>(
                                                 BrightnessController::LhbmMode::DISABLED))},
                                         0);
    bool wasDisabled = ret == OK;
    if (!enabled && wasDisabled) {
        DISPLAY_LOGW("%s: lhbm is at DISABLED state, skip disabling", __func__);
        return NO_ERROR;
    } else if (enabled && !wasDisabled) {
        requestLhbm(true);
        DISPLAY_LOGI("%s: lhbm is at ENABLING or ENABLED state, re-enable to reset timeout timer",
                     __func__);
        return NO_ERROR;
    }

    std::vector<std::string> checkingValue;
    if (!enabled) {
        ATRACE_NAME("disable_lhbm");
        requestLhbm(false);
        {
            ATRACE_NAME("wait_for_lhbm_off_cmd");
            checkingValue = {
                    std::to_string(static_cast<int>(BrightnessController::LhbmMode::DISABLED))};
            ret = mBrightnessController->checkSysfsStatus(lhbmSysfs, checkingValue,
                                                          ms2ns(kSysfsCheckTimeoutMs));
            if (ret != OK) {
                DISPLAY_LOGW("%s: failed to send lhbm-off cmd", __func__);
            }
        }
        {
            Mutex::Autolock lock(mDisplayMutex);
            restoreLhbmDisplayConfigLocked();
        }
        setLHBMRefreshRateThrottle(0);
        mLhbmOn = false;
        return NO_ERROR;
    }

    ATRACE_NAME("enable_lhbm");
    int64_t lhbmWaitForRrNanos, lhbmEnablingNanos, lhbmEnablingDoneNanos;
    bool enablingStateSupported = !mFramesToReachLhbmPeakBrightness;
    uint32_t peakRate = 0;
    auto rrSysfs = mBrightnessController->GetPanelRefreshRateSysfile();
    lhbmWaitForRrNanos = systemTime(SYSTEM_TIME_MONOTONIC);
    {
        Mutex::Autolock lock(mDisplayMutex);
        peakRate = getPeakRefreshRate();
        if (peakRate < 60) {
            DISPLAY_LOGE("%s: invalid peak rate=%d", __func__, peakRate);
            return -EINVAL;
        }
        ret = setLhbmDisplayConfigLocked(peakRate);
        if (ret != OK) return ret;
    }

    if (mBrightnessController->fileExists(rrSysfs)) {
        ATRACE_NAME("wait_for_peak_rate_cmd");
        ret = mBrightnessController->checkSysfsStatus(rrSysfs, {std::to_string(peakRate)},
                                                      ms2ns(kLhbmWaitForPeakRefreshRateMs));
        if (ret != OK) {
            DISPLAY_LOGW("%s: failed to poll peak refresh rate=%d, ret=%d", __func__, peakRate,
                         ret);
        }
    } else {
        ATRACE_NAME("wait_for_peak_rate_blindly");
        DISPLAY_LOGW("%s: missing refresh rate path: %s", __func__, rrSysfs.c_str());
        // blindly wait for (3 full frames + 1 frame uncertainty) to ensure DM finishes
        // switching refresh rate
        for (int32_t i = 0; i < 4; i++) {
            if (mDisplayInterface->waitVBlank()) {
                DISPLAY_LOGE("%s: failed to blindly wait for peak refresh rate=%d, i=%d", __func__,
                             peakRate, i);
                ret = -ENODEV;
                goto enable_err;
            }
        }
    }

    setLHBMRefreshRateThrottle(kLhbmRefreshRateThrottleMs);
    checkingValue = {std::to_string(static_cast<int>(BrightnessController::LhbmMode::ENABLING)),
                     std::to_string(static_cast<int>(BrightnessController::LhbmMode::ENABLED))};
    lhbmEnablingNanos = systemTime(SYSTEM_TIME_MONOTONIC);
    requestLhbm(true);
    {
        ATRACE_NAME("wait_for_lhbm_on_cmd");
        ret = mBrightnessController->checkSysfsStatus(lhbmSysfs, checkingValue,
                                                      ms2ns(kSysfsCheckTimeoutMs));
        if (ret != OK) {
            DISPLAY_LOGE("%s: failed to enable lhbm", __func__);
            setLHBMRefreshRateThrottle(0);
            goto enable_err;
        }
    }

    lhbmEnablingDoneNanos = systemTime(SYSTEM_TIME_MONOTONIC);
    {
        ATRACE_NAME("wait_for_peak_brightness");
        if (enablingStateSupported) {
            ret = mBrightnessController->checkSysfsStatus(lhbmSysfs,
                                            {std::to_string(static_cast<int>(
                                                    BrightnessController::LhbmMode::ENABLED))},
                                            ms2ns(kSysfsCheckTimeoutMs));
            if (ret != OK) {
                DISPLAY_LOGE("%s: failed to wait for lhbm becoming effective", __func__);
                goto enable_err;
            }
        } else {
            // lhbm takes effect at next vblank
            for (int32_t i = mFramesToReachLhbmPeakBrightness + 1; i > 0; i--) {
                ret = mDisplayInterface->waitVBlank();
                if (ret) {
                    DISPLAY_LOGE("%s: failed to wait vblank for peak brightness, %d", __func__, i);
                    goto enable_err;
                }
            }
        }
    }
    DISPLAY_LOGI("%s: latency: %04d = %03d|rr@%03d + %03d|en + %03d|boost@%s", __func__,
                 getTimestampDeltaMs(0, lhbmWaitForRrNanos),
                 getTimestampDeltaMs(lhbmEnablingNanos, lhbmWaitForRrNanos), peakRate,
                 getTimestampDeltaMs(lhbmEnablingDoneNanos, lhbmEnablingNanos),
                 getTimestampDeltaMs(0, lhbmEnablingDoneNanos),
                 enablingStateSupported ? "polling" : "fixed");

    mLhbmOn = true;
    if (!mPowerModeState.has_value() || (*mPowerModeState == HWC2_POWER_MODE_OFF && mLhbmOn)) {
        mLhbmOn = false;
        DISPLAY_LOGE("%s: power off during request lhbm on", __func__);
        return -EINVAL;
    }
    return NO_ERROR;
enable_err:
    {
        // We may receive LHBM request during the power off sequence due to the
        // race condition between display and sensor. If the failure happens
        // after requestLhbm(), we will get a wrong LHBM state in the 1st commit
        // after power on. We should reset the state in this case.
        std::unique_lock<std::mutex> lock(mPowerModeMutex);
        if (!mPowerModeState.has_value() || (*mPowerModeState == HWC2_POWER_MODE_OFF)) {
            DISPLAY_LOGW("%s: request lhbm during power off sequence, reset the state", __func__);
            mBrightnessController->resetLhbmState();
        }
    }

    Mutex::Autolock lock(mDisplayMutex);
    restoreLhbmDisplayConfigLocked();
    return ret;
}

bool ExynosPrimaryDisplay::getLhbmState() {
    return mLhbmOn;
}

void ExynosPrimaryDisplay::setLHBMRefreshRateThrottle(const uint32_t delayMs) {
    ATRACE_CALL();

    if (delayMs) {
        // make new throttle take effect
        mLastRefreshRateAppliedNanos = systemTime(SYSTEM_TIME_MONOTONIC);
        DISPLAY_ATRACE_INT64("LastRefreshRateAppliedMs", ns2ms(mLastRefreshRateAppliedNanos));
    }

    setRefreshRateThrottleNanos(std::chrono::duration_cast<std::chrono::nanoseconds>(
                                        std::chrono::milliseconds(delayMs))
                                        .count(),
                                RrThrottleRequester::LHBM);
}

void ExynosPrimaryDisplay::setEarlyWakeupDisplay() {
    if (mEarlyWakeupDispFd) {
        writeFileNode(mEarlyWakeupDispFd, 1);
    }
}

void ExynosPrimaryDisplay::setExpectedPresentTime(uint64_t timestamp, int frameIntervalNs) {
    mExpectedPresentTimeAndInterval.store(std::make_tuple(timestamp, frameIntervalNs));
    // Forward presentDisplay if there is a listener.
    const auto presentListener = getPresentListener();
    if (presentListener) {
        presentListener->setExpectedPresentTime(timestamp, frameIntervalNs);
    }
}

uint64_t ExynosPrimaryDisplay::getPendingExpectedPresentTime() {
    if (mExpectedPresentTimeAndInterval.is_dirty()) {
        return std::get<0>(mExpectedPresentTimeAndInterval.get());
    }

    return 0;
}

int ExynosPrimaryDisplay::getPendingFrameInterval() {
    if (mExpectedPresentTimeAndInterval.is_dirty()) {
        return std::get<1>(mExpectedPresentTimeAndInterval.get());
    }

    return 0;
}

void ExynosPrimaryDisplay::applyExpectedPresentTime() {
    mExpectedPresentTimeAndInterval.clear_dirty();
}

int32_t ExynosPrimaryDisplay::setDisplayIdleTimer(const int32_t timeoutMs) {
    bool support = false;
    if (getDisplayIdleTimerSupport(support) || support == false) {
        return HWC2_ERROR_UNSUPPORTED;
    }

    if (timeoutMs < 0) {
        return HWC2_ERROR_BAD_PARAMETER;
    }

    if (timeoutMs > 0) {
        setDisplayIdleDelayNanos(std::chrono::duration_cast<std::chrono::nanoseconds>(
                                         std::chrono::milliseconds(timeoutMs))
                                         .count(),
                                 DispIdleTimerRequester::SF);
    }

    bool enabled = (timeoutMs > 0);
    if (enabled != mDisplayIdleTimerEnabled) {
        if (setDisplayIdleTimerEnabled(enabled) == NO_ERROR) {
            mDisplayIdleTimerEnabled = enabled;
        }
    }

    return HWC2_ERROR_NONE;
}

int32_t ExynosPrimaryDisplay::getDisplayIdleTimerEnabled(bool &enabled) {
    bool support = false;
    if (getDisplayIdleTimerSupport(support) || support == false) {
        return HWC2_ERROR_UNSUPPORTED;
    }

    const std::string path = getPanelSysfsPath() + "panel_idle";
    std::ifstream ifs(path);
    if (!ifs.is_open()) {
        ALOGW("%s() unable to open node '%s', error = %s", __func__, path.c_str(), strerror(errno));
        return errno;
    } else {
        std::string panel_idle;
        std::getline(ifs, panel_idle);
        ifs.close();
        enabled = (panel_idle == "1");
        ALOGI("%s() get panel_idle(%d) from the sysfs node", __func__, enabled);
    }
    return NO_ERROR;
}

int32_t ExynosPrimaryDisplay::setDisplayIdleTimerEnabled(const bool enabled) {
    const std::string path = getPanelSysfsPath() + "panel_idle";
    std::ofstream ofs(path);
    if (!ofs.is_open()) {
        ALOGW("%s() unable to open node '%s', error = %s", __func__, path.c_str(), strerror(errno));
        return errno;
    } else {
        ofs << enabled;
        ofs.close();
        ALOGI("%s() writes panel_idle(%d) to the sysfs node", __func__, enabled);
    }
    return NO_ERROR;
}

int32_t ExynosPrimaryDisplay::setDisplayIdleDelayNanos(const int32_t delayNanos,
                                                       const DispIdleTimerRequester requester) {
    std::lock_guard<std::mutex> lock(mDisplayIdleDelayMutex);

    int64_t maxDelayNanos = 0;
    mDisplayIdleTimerNanos[toUnderlying(requester)] = delayNanos;
    for (uint32_t i = 0; i < toUnderlying(DispIdleTimerRequester::MAX); i++) {
        if (mDisplayIdleTimerNanos[i] > maxDelayNanos) {
            maxDelayNanos = mDisplayIdleTimerNanos[i];
        }
    }

    if (mDisplayIdleDelayNanos == maxDelayNanos) {
        return NO_ERROR;
    }

    mDisplayIdleDelayNanos = maxDelayNanos;

    const int32_t displayIdleDelayMs = std::chrono::duration_cast<std::chrono::milliseconds>(
                                               std::chrono::nanoseconds(mDisplayIdleDelayNanos))
                                               .count();
    const std::string path = getPanelSysfsPath() + "idle_delay_ms";
    std::ofstream ofs(path);
    if (!ofs.is_open()) {
        ALOGW("%s() unable to open node '%s', error = %s", __func__, path.c_str(), strerror(errno));
        return errno;
    } else {
        ofs << displayIdleDelayMs;
        ALOGI("%s() writes idle_delay_ms(%d) to the sysfs node (0x%x)", __func__,
              displayIdleDelayMs, ofs.rdstate());
        ofs.close();
    }
    return NO_ERROR;
}

void ExynosPrimaryDisplay::initDisplayHandleIdleExit() {
    if (bool support; getDisplayIdleTimerSupport(support) || support == false) {
        return;
    }

    const std::string path = getPanelSysfsPath() + "panel_need_handle_idle_exit";
    mDisplayNeedHandleIdleExitOfs.open(path, std::ofstream::out);
    if (!mDisplayNeedHandleIdleExitOfs.is_open()) {
        ALOGI("%s() '%s' doesn't exist(%s)", __func__, path.c_str(), strerror(errno));
    }

    setDisplayNeedHandleIdleExit(false, true);
}

void ExynosPrimaryDisplay::setDisplayNeedHandleIdleExit(const bool needed, const bool force) {
    if (!mDisplayNeedHandleIdleExitOfs.is_open()) {
        return;
    }

    if (needed == mDisplayNeedHandleIdleExit && !force) {
        return;
    }

    mDisplayNeedHandleIdleExitOfs << needed;
    if (mDisplayNeedHandleIdleExitOfs.fail()) {
        ALOGW("%s() failed to write panel_need_handle_idle_exit(%d) to sysfs node %s", __func__,
              needed, strerror(errno));
        return;
    }

    mDisplayNeedHandleIdleExitOfs.flush();
    if (mDisplayNeedHandleIdleExitOfs.fail()) {
        ALOGW("%s() failed to flush panel_need_handle_idle_exit(%d) to sysfs node %s", __func__,
              needed, strerror(errno));
        return;
    }

    ALOGI("%s() writes panel_need_handle_idle_exit(%d) to sysfs node", __func__, needed);
    mDisplayNeedHandleIdleExit = needed;
}

void ExynosPrimaryDisplay::handleDisplayIdleEnter(const uint32_t idleTeRefreshRate) {
    bool needed = false;
    {
        Mutex::Autolock lock1(mDisplayMutex);
        uint32_t btsRefreshRate = getBtsRefreshRate();
        if (idleTeRefreshRate <= btsRefreshRate) {
            return;
        }
        Mutex::Autolock lock2(mDRMutex);
        for (size_t i = 0; i < mLayers.size(); i++) {
            if (mLayers[i]->mOtfMPP && mLayers[i]->mM2mMPP == nullptr &&
                !mLayers[i]->checkBtsCap(idleTeRefreshRate)) {
                needed = true;
                break;
            }
        }
    }

    setDisplayNeedHandleIdleExit(needed, false);
}

int32_t ExynosPrimaryDisplay::setMinIdleRefreshRate(const int targetFps,
                                                    const RrThrottleRequester requester) {
    int fps = (targetFps <= 0) ? mDefaultMinIdleRefreshRate : targetFps;
    if (requester == RrThrottleRequester::BRIGHTNESS && mUseBlockingZoneForMinIdleRefreshRate) {
        uint32_t level = mBrightnessController->getBrightnessLevel();
        fps = (level < mDbvThresholdForBlockingZone) ? mMinIdleRefreshRateForBlockingZone
                                                     : mDefaultMinIdleRefreshRate;
    }

    std::lock_guard<std::mutex> lock(mMinIdleRefreshRateMutex);
    if (fps == mRrThrottleFps[toUnderlying(requester)]) return NO_ERROR;

    ALOGD("%s requester %u, fps %d", __func__, toUnderlying(requester), fps);
    mRrThrottleFps[toUnderlying(requester)] = fps;
    int maxMinIdleFps = 0;
    for (uint32_t i = 0; i < toUnderlying(RrThrottleRequester::MAX); i++) {
        if (mRrThrottleFps[i] > maxMinIdleFps) {
            maxMinIdleFps = mRrThrottleFps[i];
        }
    }
    if (maxMinIdleFps == mMinIdleRefreshRate) return NO_ERROR;

    const std::string path = getPanelSysfsPath() + "min_vrefresh";
    std::ofstream ofs(path);
    if (!ofs.is_open()) {
        ALOGW("%s Unable to open node '%s', error = %s", __func__, path.c_str(), strerror(errno));
        return errno;
    } else {
        ofs << maxMinIdleFps;
        ofs.close();
        ALOGI("ExynosPrimaryDisplay::%s() writes min_vrefresh(%d) to the sysfs node", __func__,
              maxMinIdleFps);
    }
    mMinIdleRefreshRate = maxMinIdleFps;
    return NO_ERROR;
}

int32_t ExynosPrimaryDisplay::setRefreshRateThrottleNanos(const int64_t delayNanos,
                                                          const RrThrottleRequester requester) {
    ATRACE_CALL();
    if (delayNanos < 0) {
        ALOGW("%s() set invalid delay(%" PRId64 ")", __func__, delayNanos);
        return BAD_VALUE;
    }

    int32_t ret = NO_ERROR;
    int64_t maxDelayNanos = 0;
    {
        std::lock_guard<std::mutex> lock(mIdleRefreshRateThrottleMutex);
        if (delayNanos == mRrThrottleNanos[toUnderlying(requester)]) return NO_ERROR;

        ALOGI("%s() requester(%u) set delay to %" PRId64 "ns", __func__, toUnderlying(requester),
              delayNanos);
        mRrThrottleNanos[toUnderlying(requester)] = delayNanos;
        uint32_t maxDelayRequester = toUnderlying(RrThrottleRequester::MAX);
        for (uint32_t i = 0; i < toUnderlying(RrThrottleRequester::MAX); i++) {
            if (mRrThrottleNanos[i] > maxDelayNanos) {
                maxDelayRequester = i;
                maxDelayNanos = mRrThrottleNanos[i];
            }
        }

        DISPLAY_ATRACE_INT("RefreshRateDelayRequester", maxDelayRequester);
        DISPLAY_ATRACE_INT64("RefreshRateDelay", ns2ms(maxDelayNanos));
        if (mRefreshRateDelayNanos == maxDelayNanos) {
            return NO_ERROR;
        }

        ret = setDisplayIdleDelayNanos(maxDelayNanos, DispIdleTimerRequester::RR_THROTTLE);
        if (ret == NO_ERROR) {
            mRefreshRateDelayNanos = maxDelayNanos;
        }
    }

    Mutex::Autolock lock(mDisplayMutex);
    if (ret == NO_ERROR) {
        recalculateTimelineLocked(maxDelayNanos);
    }

    return ret;
}

void ExynosPrimaryDisplay::dump(String8 &result) {
    ExynosDisplay::dump(result);
    result.appendFormat("Display idle timer: %s\n",
                        (mDisplayIdleTimerEnabled) ? "enabled" : "disabled");
    for (uint32_t i = 0; i < toUnderlying(DispIdleTimerRequester::MAX); i++) {
        result.appendFormat("\t[%u] vote to %" PRId64 " ns\n", i, mDisplayIdleTimerNanos[i]);
    }

    result.appendFormat("Min idle refresh rate: %d, default: %d", mMinIdleRefreshRate,
                        mDefaultMinIdleRefreshRate);
    if (mUseBlockingZoneForMinIdleRefreshRate) {
        result.appendFormat(", blocking zone level: %d, min refresh rate: %d\n",
                            mDbvThresholdForBlockingZone, mMinIdleRefreshRateForBlockingZone);
    } else {
        result.appendFormat("\n");
    }

    for (uint32_t i = 0; i < toUnderlying(RrThrottleRequester::MAX); i++) {
        result.appendFormat("\t[%u] vote to %d hz\n", i, mRrThrottleFps[i]);
    }

    result.appendFormat("Refresh rate delay: %" PRId64 " ns\n", mRefreshRateDelayNanos);
    for (uint32_t i = 0; i < toUnderlying(RrThrottleRequester::MAX); i++) {
        result.appendFormat("\t[%u] vote to %" PRId64 " ns\n", i, mRrThrottleNanos[i]);
    }
    result.appendFormat("\n");
}

void ExynosPrimaryDisplay::calculateTimelineLocked(
        hwc2_config_t config, hwc_vsync_period_change_constraints_t* vsyncPeriodChangeConstraints,
        hwc_vsync_period_change_timeline_t* outTimeline) {
    ATRACE_CALL();
    int64_t desiredUpdateTimeNanos = vsyncPeriodChangeConstraints->desiredTimeNanos;
    const int64_t origDesiredUpdateTimeNanos = desiredUpdateTimeNanos;
    int64_t threshold = 0;
    int64_t lastUpdateDelta = 0;
    int64_t actualChangeTime = 0;
    bool isDelayed = false;

    /* actualChangeTime includes transient duration */
    mDisplayInterface->getVsyncAppliedTime(config, &actualChangeTime);

    outTimeline->refreshRequired = true;

    /* When the refresh rate changes from high to low, check if RR throttle is needed */
    {
        std::lock_guard<std::mutex> lock(mIdleRefreshRateThrottleMutex);
        threshold = mRefreshRateDelayNanos;
        mRrUseDelayNanos = 0;
        mIsRrNeedCheckDelay =
                mDisplayConfigs[mActiveConfig].vsyncPeriod < mDisplayConfigs[config].vsyncPeriod;
        if (threshold != 0 && mLastRefreshRateAppliedNanos != 0 && mIsRrNeedCheckDelay) {
            lastUpdateDelta = desiredUpdateTimeNanos - mLastRefreshRateAppliedNanos;
            if (lastUpdateDelta < threshold) {
                /* in this case, the active config change needs to be delayed */
                isDelayed = true;
                desiredUpdateTimeNanos += threshold - lastUpdateDelta;
                mRrUseDelayNanos = threshold;
            }
        }
    }

    mVsyncPeriodChangeConstraints.desiredTimeNanos = desiredUpdateTimeNanos;

    getConfigAppliedTime(mVsyncPeriodChangeConstraints.desiredTimeNanos, actualChangeTime,
                         outTimeline->newVsyncAppliedTimeNanos, outTimeline->refreshTimeNanos);

    const nsecs_t now = systemTime(SYSTEM_TIME_MONOTONIC);
    DISPLAY_LOGD_AND_ATRACE_NAME(eDebugDisplayConfig,
                                 "requested config : %d(%d)->%d(%d), isDelay:%d,"
                                 " delta %" PRId64 ", delay %" PRId64 ", threshold %" PRId64 ", "
                                 "now:%" PRId64 ", desired %" PRId64 "->%" PRId64
                                 ", newVsyncAppliedTimeNanos : %" PRId64
                                 ", refreshTimeNanos:%" PRId64
                                 ", mLastRefreshRateAppliedNanos:%" PRId64,
                                 mActiveConfig, mDisplayConfigs[mActiveConfig].vsyncPeriod, config,
                                 mDisplayConfigs[config].vsyncPeriod, isDelayed,
                                 ns2ms(lastUpdateDelta), ns2ms(threshold - lastUpdateDelta),
                                 ns2ms(threshold), ns2ms(now), ns2ms(origDesiredUpdateTimeNanos),
                                 ns2ms(mVsyncPeriodChangeConstraints.desiredTimeNanos),
                                 ns2ms(outTimeline->newVsyncAppliedTimeNanos),
                                 ns2ms(outTimeline->refreshTimeNanos),
                                 ns2ms(mLastRefreshRateAppliedNanos));

    DISPLAY_ATRACE_INT64("TimeToApplyConfig",
                         ns2ms(mVsyncPeriodChangeConstraints.desiredTimeNanos - now));
}

void ExynosPrimaryDisplay::recalculateTimelineLocked(int64_t refreshRateDelayNanos) {
    ATRACE_CALL();

    if (mConfigRequestState != hwc_request_state_t::SET_CONFIG_STATE_PENDING) {
        return;
    }

    std::lock_guard<std::mutex> lock(mIdleRefreshRateThrottleMutex);
    if (!mIsRrNeedCheckDelay) {
        return;
    }

    int64_t desiredUpdateTimeNanos = mVsyncPeriodChangeConstraints.desiredTimeNanos;
    if (mRrUseDelayNanos) {
        desiredUpdateTimeNanos += (refreshRateDelayNanos - mRrUseDelayNanos);
    } else {
        int64_t lastUpdateDelta =
                mVsyncPeriodChangeConstraints.desiredTimeNanos - mLastRefreshRateAppliedNanos;
        if (lastUpdateDelta < refreshRateDelayNanos) {
            desiredUpdateTimeNanos += (refreshRateDelayNanos - lastUpdateDelta);
        }
    }
    mRrUseDelayNanos = refreshRateDelayNanos;

    const nsecs_t now = systemTime(SYSTEM_TIME_MONOTONIC);
    if (desiredUpdateTimeNanos > now) {
        mVsyncPeriodChangeConstraints.desiredTimeNanos = desiredUpdateTimeNanos;
    } else {
        mVsyncPeriodChangeConstraints.desiredTimeNanos = now;
    }

    DISPLAY_ATRACE_INT64("TimeToApplyConfig",
                         ns2ms(mVsyncPeriodChangeConstraints.desiredTimeNanos - now));
}

void ExynosPrimaryDisplay::updateAppliedActiveConfig(const hwc2_config_t newConfig,
                                                     const int64_t ts) {
    ATRACE_CALL();
    if (mAppliedActiveConfig == 0 ||
        getDisplayVsyncPeriodFromConfig(mAppliedActiveConfig) !=
                getDisplayVsyncPeriodFromConfig(newConfig)) {
        DISPLAY_LOGD(eDebugDisplayConfig,
                     "%s mAppliedActiveConfig(%d->%d), mLastRefreshRateAppliedNanos(%" PRIu64
                     " -> %" PRIu64 ")",
                     __func__, mAppliedActiveConfig, newConfig, mLastRefreshRateAppliedNanos, ts);
        mLastRefreshRateAppliedNanos = ts;
        DISPLAY_ATRACE_INT64("LastRefreshRateAppliedMs", ns2ms(mLastRefreshRateAppliedNanos));
    }

    mAppliedActiveConfig = newConfig;
}

void ExynosPrimaryDisplay::checkBtsReassignResource(const int32_t vsyncPeriod,
                                                    const int32_t btsVsyncPeriod) {
    ATRACE_CALL();
    uint32_t refreshRate = static_cast<uint32_t>(round(nsecsPerSec / vsyncPeriod * 0.1f) * 10);

    Mutex::Autolock lock(mDRMutex);
    if (vsyncPeriod < btsVsyncPeriod) {
        for (size_t i = 0; i < mLayers.size(); i++) {
            if (mLayers[i]->mOtfMPP && mLayers[i]->mM2mMPP == nullptr &&
                !mLayers[i]->checkBtsCap(refreshRate)) {
                mLayers[i]->setGeometryChanged(GEOMETRY_DEVICE_CONFIG_CHANGED);
                break;
            }
        }
    } else if (vsyncPeriod > btsVsyncPeriod) {
        for (size_t i = 0; i < mLayers.size(); i++) {
            if (mLayers[i]->mOtfMPP && mLayers[i]->mM2mMPP) {
                float srcWidth = mLayers[i]->mSourceCrop.right - mLayers[i]->mSourceCrop.left;
                float srcHeight = mLayers[i]->mSourceCrop.bottom - mLayers[i]->mSourceCrop.top;
                float resolution = srcWidth * srcHeight * refreshRate / 1000;
                float ratioVertical = static_cast<float>(mLayers[i]->mDisplayFrame.bottom -
                                                         mLayers[i]->mDisplayFrame.top) /
                        mYres;

                if (mLayers[i]->mOtfMPP->checkDownscaleCap(resolution, ratioVertical)) {
                    mLayers[i]->setGeometryChanged(GEOMETRY_DEVICE_CONFIG_CHANGED);
                    break;
                }
            }
        }
    }
}

bool ExynosPrimaryDisplay::isDbmSupported() {
    return mBrightnessController->isDbmSupported();
}

int32_t ExynosPrimaryDisplay::setDbmState(bool enabled) {
    mBrightnessController->processDimBrightness(enabled);
    return NO_ERROR;
}

PresentListener* ExynosPrimaryDisplay::getPresentListener() {
    if (mVariableRefreshRateController) {
        return mVariableRefreshRateController.get();
    }
    return nullptr;
}

VsyncListener* ExynosPrimaryDisplay::getVsyncListener() {
    if (mVariableRefreshRateController) {
        return mVariableRefreshRateController.get();
    }
    return nullptr;
}

void ExynosPrimaryDisplay::onConfigChange(int configId) {
    if (mVariableRefreshRateController) {
        return mVariableRefreshRateController->setActiveVrrConfiguration(configId);
    }
}

const std::string& ExynosPrimaryDisplay::getPanelName() {
    if (!mPanelName.empty()) {
        return mPanelName;
    }

    const std::string& sysfs = getPanelSysfsPath();
    if (!sysfs.empty()) {
        std::string sysfs_rel("panel_name");
        if (readLineFromFile(sysfs + "/" + sysfs_rel, mPanelName, '\n') != OK) {
            ALOGE("failed reading %s/%s", sysfs.c_str(), sysfs_rel.c_str());
        }
    }
    return mPanelName;
}
