/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include "ExynosPrimaryDisplayModule.h"

#include <android-base/file.h>
#include <json/reader.h>
#include <json/value.h>

#include <cmath>

#include "BrightnessController.h"
#include "ExynosDisplayDrmInterfaceModule.h"
#include "ExynosHWCDebug.h"

#ifdef FORCE_GPU_COMPOSITION
extern exynos_hwc_control exynosHWCControl;
#endif

using namespace gs101;

mpp_phycal_type_t getMPPTypeFromDPPChannel(uint32_t channel) {

    for (int i=0; i < MAX_DECON_DMA_TYPE; i++){
        if(idma_channel_map[i].channel == channel)
            return idma_channel_map[i].type;
    }

    return MPP_P_TYPE_MAX;
}

ExynosPrimaryDisplayModule::ExynosPrimaryDisplayModule(uint32_t index, ExynosDevice* device,
                                                       const std::string& displayName)
      : ExynosPrimaryDisplay(index, device, displayName), mAtcInit(false) {
#ifdef FORCE_GPU_COMPOSITION
    exynosHWCControl.forceGpu = true;
#endif
    mColorManager = std::make_unique<ColorManager>(this, static_cast<ExynosDeviceModule*>(device));
}

ExynosPrimaryDisplayModule::~ExynosPrimaryDisplayModule () {
}

void ExynosPrimaryDisplayModule::usePreDefinedWindow(bool use)
{
#ifdef FIX_BASE_WINDOW_INDEX
    /* Use fixed base window index */
    mBaseWindowIndex = FIX_BASE_WINDOW_INDEX;
    return;
#endif

    if (use) {
        mBaseWindowIndex = PRIMARY_DISP_BASE_WIN[mDevice->mDisplayMode];
        mMaxWindowNum = mDisplayInterface->getMaxWindowNum() - PRIMARY_DISP_BASE_WIN[mDevice->mDisplayMode];
    } else {
        mBaseWindowIndex = 0;
        mMaxWindowNum = mDisplayInterface->getMaxWindowNum();
    }
}

int32_t ExynosPrimaryDisplayModule::validateWinConfigData()
{
    bool flagValidConfig = true;

    if (ExynosDisplay::validateWinConfigData() != NO_ERROR)
        flagValidConfig = false;

    for (size_t i = 0; i < mDpuData.configs.size(); i++) {
        struct exynos_win_config_data &config = mDpuData.configs[i];
        if (config.state == config.WIN_STATE_BUFFER) {
            bool configInvalid = false;
            uint32_t mppType = config.assignedMPP->mPhysicalType;
            if ((config.src.w != config.dst.w) ||
                (config.src.h != config.dst.h)) {
                if ((mppType == MPP_DPP_GF) ||
                    (mppType == MPP_DPP_VG) ||
                    (mppType == MPP_DPP_VGF)) {
                    DISPLAY_LOGE("WIN_CONFIG error: invalid assign id : "
                            "%zu,  s_w : %d, d_w : %d, s_h : %d, d_h : %d, mppType : %d",
                            i, config.src.w, config.dst.w, config.src.h, config.dst.h, mppType);
                    configInvalid = true;
                }
            }
            if (configInvalid) {
                config.state = config.WIN_STATE_DISABLED;
                flagValidConfig = false;
            }
        }
    }
    if (flagValidConfig)
        return NO_ERROR;
    else
        return -EINVAL;
}

void ExynosPrimaryDisplayModule::doPreProcessing() {
    ExynosDisplay::doPreProcessing();

    if (mDevice->checkNonInternalConnection()) {
        mDisplayControl.adjustDisplayFrame = true;
    } else {
        mDisplayControl.adjustDisplayFrame = false;
    }
}

int32_t ExynosPrimaryDisplayModule::getColorModes(
        uint32_t* outNumModes, int32_t* outModes)
{
    return mColorManager->getColorModes(outNumModes, outModes);
}

int32_t ExynosPrimaryDisplayModule::setColorMode(int32_t mode)
{
    return mColorManager->setColorMode(mode);
}

int32_t ExynosPrimaryDisplayModule::getRenderIntents(int32_t mode,
        uint32_t* outNumIntents, int32_t* outIntents)
{
    return mColorManager->getRenderIntents(mode, outNumIntents, outIntents);
}

int32_t ExynosPrimaryDisplayModule::setColorModeWithRenderIntent(int32_t mode,
        int32_t intent)
{
    return mColorManager->setColorModeWithRenderIntent(mode, intent);
}

int32_t ExynosPrimaryDisplayModule::setColorTransform(
        const float* matrix, int32_t hint)
{
    return mColorManager->setColorTransform(matrix, hint);
}

int32_t ExynosPrimaryDisplayModule::getClientTargetProperty(
        hwc_client_target_property_t* outClientTargetProperty,
        HwcDimmingStage *outDimmingStage) {
    GsInterfaceType* displayColorInterface = getDisplayColorInterface();
    if (displayColorInterface == nullptr) {
        ALOGI("%s dc interface not created", __func__);
        return ExynosDisplay::getClientTargetProperty(outClientTargetProperty);
    }

    const DisplayType display = getDcDisplayType();
    hwc::PixelFormat pixelFormat;
    hwc::Dataspace dataspace;
    bool dimming_linear;
    if (!displayColorInterface->GetBlendingProperty(display, pixelFormat, dataspace,
                                                    dimming_linear)) {
        outClientTargetProperty->pixelFormat = toUnderlying(pixelFormat);
        outClientTargetProperty->dataspace = toUnderlying(dataspace);
        if (outDimmingStage != nullptr)
            *outDimmingStage = dimming_linear
                              ? HwcDimmingStage::DIMMING_LINEAR
                              : HwcDimmingStage::DIMMING_OETF;

        return HWC2_ERROR_NONE;
    }

    ALOGW("%s failed to get property of blending stage", __func__);
    return ExynosDisplay::getClientTargetProperty(outClientTargetProperty);
}

int32_t ExynosPrimaryDisplayModule::updateBrightnessTable() {
    std::unique_ptr<const IBrightnessTable> table;
    auto displayColorInterface = getDisplayColorInterface();
    if (displayColorInterface == nullptr) {
        ALOGE("%s displaycolor interface not available!", __func__);
        return HWC2_ERROR_NO_RESOURCES;
    }

    auto displayType = getDcDisplayType();
    auto ret = displayColorInterface->GetBrightnessTable(displayType, table);
    if (ret != android::OK) {
        ALOGE("%s brightness table not available!", __func__);
        return HWC2_ERROR_NO_RESOURCES;
    }
    // BrightnessController is not ready until this step
    mBrightnessController->updateBrightnessTable(table);

    return HWC2_ERROR_NONE;
}

int ExynosPrimaryDisplayModule::deliverWinConfigData()
{
    int ret = 0;
    ExynosDisplayDrmInterfaceModule *moduleDisplayInterface =
        (ExynosDisplayDrmInterfaceModule*)(mDisplayInterface.get());
    GsInterfaceType* displayColorInterface = getDisplayColorInterface();

    bool forceDisplayColorSetting = false;
    if (!getDisplaySceneInfo().displaySettingDelivered || isForceColorUpdate())
        forceDisplayColorSetting = true;

    setForceColorUpdate(false);

    if (displayColorInterface != nullptr) {
        moduleDisplayInterface
                ->setColorSettingChanged(getDisplaySceneInfo().needDisplayColorSetting(),
                                         forceDisplayColorSetting);
    }

    ret = ExynosDisplay::deliverWinConfigData();

    checkAtcAnimation();

    if (mDpuData.enable_readback &&
       !mDpuData.readback_info.requested_from_service)
        getDisplaySceneInfo().displaySettingDelivered = false;
    else
        getDisplaySceneInfo().displaySettingDelivered = true;

    return ret;
}

int32_t ExynosPrimaryDisplayModule::updateColorConversionInfo()
{
    return mColorManager->updateColorConversionInfo();
}

int32_t ExynosPrimaryDisplayModule::resetColorMappingInfo(ExynosMPPSource* mppSrc) {
    return mColorManager->resetColorMappingInfo(mppSrc);
}

int32_t ExynosPrimaryDisplayModule::updatePresentColorConversionInfo()
{
    int ret = NO_ERROR;
    GsInterfaceType* displayColorInterface = getDisplayColorInterface();
    if (displayColorInterface == nullptr) {
        return ret;
    }

    ExynosDisplayDrmInterfaceModule *moduleDisplayInterface =
        (ExynosDisplayDrmInterfaceModule*)(mDisplayInterface.get());
    auto refresh_rate = moduleDisplayInterface->getDesiredRefreshRate();
    if (refresh_rate > 0) {
        getDisplaySceneInfo().displayScene.refresh_rate = refresh_rate;
    }
    auto operation_rate = moduleDisplayInterface->getOperationRate();
    if (operation_rate > 0) {
        getDisplaySceneInfo().displayScene.operation_rate = static_cast<uint32_t>(operation_rate);
    }

    getDisplaySceneInfo().displayScene.lhbm_on = mBrightnessController->isLhbmOn();
    getDisplaySceneInfo().displayScene.dbv = mBrightnessController->getBrightnessLevel();
    const DisplayType display = getDcDisplayType();
    if ((ret = displayColorInterface->UpdatePresent(display, getDisplaySceneInfo().displayScene)) !=
        0) {
        DISPLAY_LOGE("Display Scene update error (%d)", ret);
        return ret;
    }

    return ret;
}

int32_t ExynosPrimaryDisplayModule::getColorAdjustedDbv(uint32_t &dbv_adj) {
    GsInterfaceType* displayColorInterface = getDisplayColorInterface();
    if (displayColorInterface == nullptr) {
        return NO_ERROR;
    }

    const DisplayType display = getDcDisplayType();
    dbv_adj = displayColorInterface->GetPipelineData(display)->Panel().GetAdjustedBrightnessLevel();
    return NO_ERROR;
}

bool ExynosPrimaryDisplayModule::parseAtcProfile() {
    Json::Value root;
    Json::CharReaderBuilder reader_builder;
    std::unique_ptr<Json::CharReader> reader(reader_builder.newCharReader());
    std::string atc_profile;

    if (!android::base::ReadFileToString(kAtcProfilePath, &atc_profile)) {
        atc_profile = kAtcJsonRaw;
        ALOGI("Use default atc profile file");
    }

    if (!reader->parse(atc_profile.c_str(), atc_profile.c_str() + atc_profile.size(), &root,
                       nullptr)) {
        ALOGE("Failed to parse atc profile file");
        return false;
    }

    ALOGI("Atc Profile version = %s", root[kAtcProfileVersionStr].asString().c_str());
    Json::Value nodes = root[kAtcProfileModesStr];
    atc_mode mode;

    for (Json::Value::ArrayIndex i = 0; i < nodes.size(); ++i) {
        std::string name = nodes[i][kAtcProfileModeNameStr].asString();

        if (nodes[i][kAtcProfileLuxMapStr].size() != nodes[i][kAtcProfileAlMapStr].size() &&
            nodes[i][kAtcProfileAlMapStr].size() != nodes[i][kAtcProfileStMapStr].size()) {
            ALOGE("Atc profile is unavailable !");
            return false;
        }

        uint32_t map_cnt = nodes[i][kAtcProfileLuxMapStr].size();

        mode.lux_map.clear();
        for (uint32_t index = 0; index < map_cnt; ++index) {
            mode.lux_map.emplace_back(atc_lux_map{nodes[i][kAtcProfileLuxMapStr][index].asUInt(),
                                                  nodes[i][kAtcProfileAlMapStr][index].asUInt(),
                                                  nodes[i][kAtcProfileStMapStr][index].asUInt()});
        }

        if (!nodes[i][kAtcProfileStUpStepStr].empty())
            mode.st_up_step = nodes[i][kAtcProfileStUpStepStr].asUInt();
        else
            mode.st_up_step = kAtcStStep;

        if (!nodes[i][kAtcProfileStDownStepStr].empty())
            mode.st_down_step = nodes[i][kAtcProfileStDownStepStr].asUInt();
        else
            mode.st_down_step = kAtcStStep;

        if (nodes[i][kAtcProfileSubSettingStr].size() != kAtcSubSetting.size()) return false;

        for (auto it = kAtcSubSetting.begin(); it != kAtcSubSetting.end(); it++) {
            mode.sub_setting[it->first.c_str()] =
                    nodes[i][kAtcProfileSubSettingStr][it->first.c_str()].asUInt();
        }
        auto ret = mAtcModeSetting.insert(std::make_pair(name.c_str(), mode));
        if (ret.second == false) {
            ALOGE("Atc mode %s is already existed!", ret.first->first.c_str());
            return false;
        }
    }

    if (mAtcModeSetting.find(kAtcModeNormalStr) == mAtcModeSetting.end()) {
        ALOGW("Failed to find atc normal mode");
        return false;
    }
    return true;
}

bool ExynosPrimaryDisplayModule::isLbeSupported() {
    return mLbeSupported;
}

void ExynosPrimaryDisplayModule::initLbe() {
    if (!parseAtcProfile()) {
        ALOGD("Failed to parseAtcMode");
        mAtcInit = false;
        return;
    }

    mAtcInit = true;
    mAtcAmbientLight.node = String8::format(ATC_AMBIENT_LIGHT_FILE_NAME, mIndex);
    mAtcAmbientLight.value.set_dirty();
    mAtcStrength.node = String8::format(ATC_ST_FILE_NAME, mIndex);
    mAtcStrength.value.set_dirty();
    mAtcEnable.node = String8::format(ATC_ENABLE_FILE_NAME, mIndex);
    mAtcEnable.value.set_dirty();

    for (auto it = kAtcSubSetting.begin(); it != kAtcSubSetting.end(); it++) {
        mAtcSubSetting[it->first.c_str()].node = String8::format(it->second.c_str(), mIndex);
        mAtcSubSetting[it->first.c_str()].value.set_dirty();
    }
    mLbeSupported = true;
}

uint32_t ExynosPrimaryDisplayModule::getAtcLuxMapIndex(std::vector<atc_lux_map> map, uint32_t lux) {
    uint32_t index = 0;
    for (uint32_t i = 0; i < map.size(); i++) {
        if (lux < map[i].lux) {
            break;
        }
        index = i;
    }

    return index;
}

int32_t ExynosPrimaryDisplayModule::setAtcStrength(uint32_t strength) {
    mAtcStrength.value.store(strength);
    if (mAtcStrength.value.is_dirty()) {
        if (writeIntToFile(mAtcStrength.node.c_str(), mAtcStrength.value.get()) != NO_ERROR) {
            return -EPERM;
        }
        mAtcStrength.value.clear_dirty();
    }
    return NO_ERROR;
}

int32_t ExynosPrimaryDisplayModule::setAtcAmbientLight(uint32_t ambient_light) {
    mAtcAmbientLight.value.store(ambient_light);
    if (mAtcAmbientLight.value.is_dirty()) {
        if (writeIntToFile(mAtcAmbientLight.node.c_str(), mAtcAmbientLight.value.get()) != NO_ERROR)
            return -EPERM;
        mAtcAmbientLight.value.clear_dirty();
    }

    return NO_ERROR;
}

int32_t ExynosPrimaryDisplayModule::setAtcMode(std::string mode_name) {
    auto mode_data = mAtcModeSetting.find(mode_name);
    uint32_t ambient_light = 0;
    uint32_t strength = 0;
    bool enable = (!mode_name.empty()) && (mode_data != mAtcModeSetting.end());

    if (enable) {
        atc_mode mode = mode_data->second;
        for (auto it = kAtcSubSetting.begin(); it != kAtcSubSetting.end(); it++) {
            mAtcSubSetting[it->first.c_str()].value.store(mode.sub_setting[it->first.c_str()]);
            if (mAtcSubSetting[it->first.c_str()].value.is_dirty()) {
                if (writeIntToFile(mAtcSubSetting[it->first.c_str()].node.c_str(),
                                   mAtcSubSetting[it->first.c_str()].value.get()) != NO_ERROR)
                    return -EPERM;
                mAtcSubSetting[it->first.c_str()].value.clear_dirty();
            }
        }
        mAtcStUpStep = mode.st_up_step;
        mAtcStDownStep = mode.st_down_step;

        uint32_t index = getAtcLuxMapIndex(mode.lux_map, mCurrentLux);
        ambient_light = mode.lux_map[index].al;
        strength = mode.lux_map[index].st;
    }

    if (setAtcAmbientLight(ambient_light) != NO_ERROR) {
        ALOGE("Fail to set atc ambient light for %s mode", mode_name.c_str());
        return -EPERM;
    }

    if (setAtcStDimming(strength) != NO_ERROR) {
        ALOGE("Fail to set atc st dimming for %s mode", mode_name.c_str());
        return -EPERM;
    }

    if (!enable && isInAtcAnimation()) {
        mPendingAtcOff = true;
    } else {
        if (setAtcEnable(enable) != NO_ERROR) {
            ALOGE("Fail to set atc enable = %d", enable);
            return -EPERM;
        }
        mPendingAtcOff = false;
    }

    mCurrentAtcModeName = enable ? mode_name : "NULL";
    ALOGI("atc enable=%d (mode=%s, pending off=%s)", enable, mCurrentAtcModeName.c_str(),
          mPendingAtcOff ? "true" : "false");
    return NO_ERROR;
}
void ExynosPrimaryDisplayModule::setLbeState(LbeState state) {
    if (!mAtcInit) return;
    std::string modeStr;
    bool enhanced_hbm = false;
    switch (state) {
        case LbeState::OFF:
            mCurrentLux = 0;
            break;
        case LbeState::NORMAL:
            modeStr = kAtcModeNormalStr;
            break;
        case LbeState::HIGH_BRIGHTNESS:
            modeStr = kAtcModeHbmStr;
            break;
        case LbeState::POWER_SAVE:
            modeStr = kAtcModePowerSaveStr;
            break;
        case LbeState::HIGH_BRIGHTNESS_ENHANCE:
            modeStr = kAtcModeHbmStr;
            enhanced_hbm = true;
            break;
        default:
            ALOGE("Lbe state not support");
            return;
    }

    if (setAtcMode(modeStr) != NO_ERROR) return;

    mBrightnessController->processEnhancedHbm(enhanced_hbm);
    mBrightnessController->setOutdoorVisibility(state);

    if (mCurrentLbeState != state) {
        mCurrentLbeState = state;
        mDevice->onRefresh(mDisplayId);
    }
    ALOGI("Lbe state %hhd", mCurrentLbeState);
}

void ExynosPrimaryDisplayModule::setLbeAmbientLight(int value) {
    if (!mAtcInit) return;

    auto it = mAtcModeSetting.find(mCurrentAtcModeName);
    if (it == mAtcModeSetting.end()) {
        ALOGE("Atc mode not found");
        return;
    }
    atc_mode mode = it->second;

    uint32_t index = getAtcLuxMapIndex(mode.lux_map, value);
    if (setAtcAmbientLight(mode.lux_map[index].al) != NO_ERROR) {
        ALOGE("Failed to set atc ambient light");
        return;
    }

    if (setAtcStDimming(mode.lux_map[index].st) != NO_ERROR) {
        ALOGE("Failed to set atc st dimming");
        return;
    }

    if (mAtcLuxMapIndex != index) {
        mAtcLuxMapIndex = index;
        mDevice->onRefresh(mDisplayId);
    }
    mCurrentLux = value;
}

LbeState ExynosPrimaryDisplayModule::getLbeState() {
    return mCurrentLbeState;
}

PanelCalibrationStatus ExynosPrimaryDisplayModule::getPanelCalibrationStatus() {
    auto displayColorInterface = getDisplayColorInterface();
    if (displayColorInterface == nullptr) {
        return PanelCalibrationStatus::UNCALIBRATED;
    }

    auto displayType = getDcDisplayType();
    auto calibrationInfo = displayColorInterface->GetCalibrationInfo(displayType);

    if (calibrationInfo.factory_cal_loaded) {
        return PanelCalibrationStatus::ORIGINAL;
    } else if (calibrationInfo.golden_cal_loaded) {
        return PanelCalibrationStatus::GOLDEN;
    } else {
        return PanelCalibrationStatus::UNCALIBRATED;
    }
}

int32_t ExynosPrimaryDisplayModule::setAtcStDimming(uint32_t value) {
    Mutex::Autolock lock(mAtcStMutex);
    int32_t strength = mAtcStrength.value.get();
    if (mAtcStTarget != value) {
        mAtcStTarget = value;
        uint32_t step = mAtcStTarget > strength ? mAtcStUpStep : mAtcStDownStep;

        int diff = value - strength;
        uint32_t count = (std::abs(diff) + step - 1) / step;
        mAtcStStepCount = count;
        ALOGI("setup atc st dimming=%d, count=%d, step=%d", value, count, step);
    }

    if (mAtcStStepCount == 0 && !mAtcStrength.value.is_dirty()) return NO_ERROR;

    if ((strength + mAtcStUpStep) < mAtcStTarget) {
        strength = strength + mAtcStUpStep;
    } else if (strength > (mAtcStTarget + mAtcStDownStep)) {
        strength = strength - mAtcStDownStep;
    } else {
        strength = mAtcStTarget;
    }

    if (setAtcStrength(strength) != NO_ERROR) {
        ALOGE("Failed to set atc st");
        return -EPERM;
    }

    if (mAtcStStepCount > 0) mAtcStStepCount--;
    return NO_ERROR;
}

int32_t ExynosPrimaryDisplayModule::setAtcEnable(bool enable) {
    mAtcEnable.value.store(enable);
    if (mAtcEnable.value.is_dirty()) {
        if (writeIntToFile(mAtcEnable.node.c_str(), enable) != NO_ERROR) return -EPERM;
        mAtcEnable.value.clear_dirty();
    }
    return NO_ERROR;
}

void ExynosPrimaryDisplayModule::checkAtcAnimation() {
    if (!isInAtcAnimation()) return;

    if (setAtcStDimming(mAtcStTarget) != NO_ERROR) {
        ALOGE("Failed to set atc st dimming");
        return;
    }

    if (mPendingAtcOff && mAtcStStepCount == 0) {
        if (setAtcEnable(false) != NO_ERROR) {
            ALOGE("Failed to set atc enable to off");
            return;
        }
        mPendingAtcOff = false;
        ALOGI("atc enable is off (pending off=false)");
    }

    mDevice->onRefresh(mDisplayId);
}

int32_t ExynosPrimaryDisplayModule::setPowerMode(int32_t mode) {
    hwc2_power_mode_t prevPowerModeState = mPowerModeState.value_or(HWC2_POWER_MODE_OFF);
    int32_t ret;

    ret = ExynosPrimaryDisplay::setPowerMode(mode);

    if ((ret == HWC2_ERROR_NONE) && isDisplaySwitched(mode, prevPowerModeState)) {
        ExynosDeviceModule* device = static_cast<ExynosDeviceModule*>(mDevice);

        device->setActiveDisplay(mIndex);
        setForceColorUpdate(true);
    }
    return ret;
}

bool ExynosPrimaryDisplayModule::isDisplaySwitched(int32_t mode, int32_t prevMode) {
    ExynosDeviceModule* device = static_cast<ExynosDeviceModule*>(mDevice);

    return (device->getActiveDisplay() != mIndex) && (prevMode == HWC_POWER_MODE_OFF) &&
            (mode != HWC_POWER_MODE_OFF);
}
