/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include "ExynosPrimaryDisplayModule.h"

#include <cutils/properties.h>

#include "ExynosHWCHelper.h"

#define OP_MANAGER_LOGD(msg, ...)                                                         \
    ALOGD("[%s] OperationRateManager::%s:" msg, mDisplay->mDisplayName.c_str(), __func__, \
          ##__VA_ARGS__)
#define OP_MANAGER_LOGI(msg, ...)                                                         \
    ALOGI("[%s] OperationRateManager::%s:" msg, mDisplay->mDisplayName.c_str(), __func__, \
          ##__VA_ARGS__)
#define OP_MANAGER_LOGE(msg, ...)                                                         \
    ALOGE("[%s] OperationRateManager::%s:" msg, mDisplay->mDisplayName.c_str(), __func__, \
          ##__VA_ARGS__)

using namespace zuma;

ExynosPrimaryDisplayModule::ExynosPrimaryDisplayModule(uint32_t index, ExynosDevice* device,
                                                       const std::string& displayName)
      : gs201::ExynosPrimaryDisplayModule(index, device, displayName) {
    int32_t hs_hz = property_get_int32("vendor.primarydisplay.op.hs_hz", 0);
    int32_t ns_hz = property_get_int32("vendor.primarydisplay.op.ns_hz", 0);

    if (hs_hz && ns_hz) {
        mOperationRateManager = std::make_unique<OperationRateManager>(this, hs_hz, ns_hz);
    }
}

ExynosPrimaryDisplayModule::~ExynosPrimaryDisplayModule ()
{

}

int32_t ExynosPrimaryDisplayModule::validateWinConfigData()
{
    return ExynosDisplay::validateWinConfigData();
}

int32_t ExynosPrimaryDisplayModule::OperationRateManager::getTargetOperationRate() const {
    if (mDisplayPowerMode == HWC2_POWER_MODE_DOZE ||
        mDisplayPowerMode == HWC2_POWER_MODE_DOZE_SUSPEND) {
        return LP_OP_RATE;
    } else {
        return mDisplayTargetOperationRate;
    }
}

int32_t ExynosPrimaryDisplayModule::OperationRateManager::setTargetOperationRate(
        const int32_t rate) {
    if (mDisplayTargetOperationRate == rate) return NO_ERROR;

    OP_MANAGER_LOGI("set target operation rate %d", rate);
    mDisplayTargetOperationRate = rate;

    return NO_ERROR;
}

ExynosPrimaryDisplayModule::OperationRateManager::OperationRateManager(
        ExynosPrimaryDisplay* display, int32_t hsHz, int32_t nsHz)
      : gs201::ExynosPrimaryDisplayModule::OperationRateManager(),
        mDisplay(display),
        mDisplayHsOperationRate(hsHz),
        mDisplayNsOperationRate(nsHz),
        mDisplayPeakRefreshRate(0),
        mDisplayRefreshRate(0),
        mDisplayLastDbv(0),
        mDisplayDbv(0),
        mDisplayPowerMode(HWC2_POWER_MODE_ON),
        mDisplayLowBatteryModeEnabled(false) {
    mDisplayNsMinDbv = property_get_int32("vendor.primarydisplay.op.ns_min_dbv", 0);
    mDisplayTargetOperationRate = mDisplayHsOperationRate;
    OP_MANAGER_LOGI("Op Rate: NS=%d HS=%d NsMinDbv=%d", mDisplayNsOperationRate,
                    mDisplayHsOperationRate, mDisplayNsMinDbv);
}

ExynosPrimaryDisplayModule::OperationRateManager::~OperationRateManager() {}

int32_t ExynosPrimaryDisplayModule::OperationRateManager::onPeakRefreshRate(uint32_t rate) {
    Mutex::Autolock lock(mLock);
    char rateStr[PROP_VALUE_MAX];
    std::sprintf(rateStr, "%d", rate);

    OP_MANAGER_LOGD("rate=%d", rate);
    mDisplayPeakRefreshRate = rate;
    if (property_set("persist.vendor.primarydisplay.op.peak_refresh_rate", rateStr) < 0) {
        OP_MANAGER_LOGE("failed to set property persist.primarydisplay.op.peak_refresh_rate");
    }
    return 0;
}

int32_t ExynosPrimaryDisplayModule::OperationRateManager::onLowPowerMode(bool enabled) {
    Mutex::Autolock lock(mLock);
    OP_MANAGER_LOGD("enabled=%d", enabled);
    mDisplayLowBatteryModeEnabled = enabled;
    return 0;
}

int32_t ExynosPrimaryDisplayModule::OperationRateManager::onConfig(hwc2_config_t cfg) {
    Mutex::Autolock lock(mLock);
    mDisplayRefreshRate = mDisplay->getRefreshRate(cfg);
    OP_MANAGER_LOGD("rate=%d", mDisplayRefreshRate);
    updateOperationRateLocked(DispOpCondition::SET_CONFIG);
    return 0;
}

int32_t ExynosPrimaryDisplayModule::OperationRateManager::onBrightness(uint32_t dbv) {
    Mutex::Autolock lock(mLock);
    if (dbv == 0 || mDisplayLastDbv == dbv) return 0;
    OP_MANAGER_LOGD("dbv=%d", dbv);
    mDisplayDbv = dbv;

    /*
        Update peak_refresh_rate from persist/vendor prop after a brightness change.
        1. Otherwise there will be NS-HS-NS switch during the onPowerMode.
        2. When constructor is called, persist property is not ready yet and returns 0.
    */
    if (!mDisplayPeakRefreshRate) {
        char rateStr[PROP_VALUE_MAX];
        int32_t vendorPeakRefreshRate = 0, persistPeakRefreshRate = 0;
        if (property_get("persist.vendor.primarydisplay.op.peak_refresh_rate", rateStr, "0") >= 0 &&
            atoi(rateStr) > 0) {
            persistPeakRefreshRate = atoi(rateStr);
            mDisplayPeakRefreshRate = persistPeakRefreshRate;
        } else {
            vendorPeakRefreshRate =
                    property_get_int32("vendor.primarydisplay.op.peak_refresh_rate", 0);
            mDisplayPeakRefreshRate = vendorPeakRefreshRate;
        }

        OP_MANAGER_LOGD("peak_refresh_rate=%d[vendor: %d|persist %d]", mDisplayPeakRefreshRate,
                        vendorPeakRefreshRate, persistPeakRefreshRate);
    }

    return updateOperationRateLocked(DispOpCondition::SET_DBV);
}

int32_t ExynosPrimaryDisplayModule::OperationRateManager::onPowerMode(int32_t mode) {
    std::string modeName = "Unknown";
    if (mode == HWC2_POWER_MODE_ON) {
        modeName = "On";
    } else if (mode == HWC2_POWER_MODE_OFF) {
        modeName = "Off";
    } else if (mode == HWC2_POWER_MODE_DOZE || mode == HWC2_POWER_MODE_DOZE_SUSPEND) {
        modeName = "LP";
    }

    Mutex::Autolock lock(mLock);
    OP_MANAGER_LOGD("mode=%s", modeName.c_str());
    mDisplayPowerMode = static_cast<hwc2_power_mode_t>(mode);
    return updateOperationRateLocked(DispOpCondition::PANEL_SET_POWER);
}

int32_t ExynosPrimaryDisplayModule::OperationRateManager::updateOperationRateLocked(
        const DispOpCondition cond) {
    int32_t ret = HWC2_ERROR_NONE, dbv;

    ATRACE_CALL();
    if (cond == DispOpCondition::SET_DBV) {
        dbv = mDisplayDbv;
    } else {
        dbv = mDisplayLastDbv;
    }

    int32_t desiredOpRate = mDisplayHsOperationRate;
    int32_t curRefreshRate = mDisplay->getRefreshRate(mDisplay->mActiveConfig);
    bool isSteadyLowRefreshRate =
            (mDisplayPeakRefreshRate && mDisplayPeakRefreshRate <= mDisplayNsOperationRate) ||
            mDisplayLowBatteryModeEnabled;
    int32_t effectiveOpRate = 0;

    // check minimal operation rate needed
    if (isSteadyLowRefreshRate && curRefreshRate <= mDisplayNsOperationRate) {
        desiredOpRate = mDisplayNsOperationRate;
    }
    // check blocking zone
    if (dbv < mDisplayNsMinDbv) {
        desiredOpRate = mDisplayHsOperationRate;
    }

    if (mDisplayPowerMode == HWC2_POWER_MODE_DOZE ||
        mDisplayPowerMode == HWC2_POWER_MODE_DOZE_SUSPEND) {
        mDisplayTargetOperationRate = LP_OP_RATE;
        desiredOpRate = mDisplayTargetOperationRate;
        effectiveOpRate = desiredOpRate;
    } else if (mDisplayPowerMode != HWC2_POWER_MODE_ON) {
        return ret;
    }

    if (cond == DispOpCondition::SET_CONFIG) {
        curRefreshRate = mDisplayRefreshRate;
        if ((curRefreshRate > mDisplayNsOperationRate) &&
            (curRefreshRate <= mDisplayHsOperationRate))
            effectiveOpRate = mDisplayHsOperationRate;
    } else if (cond == DispOpCondition::PANEL_SET_POWER) {
        if (mDisplayPowerMode == HWC2_POWER_MODE_ON) {
            mDisplayTargetOperationRate = getTargetOperationRate();
        }
        effectiveOpRate = desiredOpRate;
    } else if (cond == DispOpCondition::SET_DBV) {
        // TODO: tune brightness delta for different brightness curve and values
        int32_t delta = abs(dbv - mDisplayLastDbv);
        if ((desiredOpRate == mDisplayHsOperationRate) || (delta > BRIGHTNESS_DELTA_THRESHOLD)) {
            effectiveOpRate = desiredOpRate;
        }
        mDisplayLastDbv = dbv;
        if (effectiveOpRate > LP_OP_RATE && (effectiveOpRate != mDisplayTargetOperationRate)) {
            OP_MANAGER_LOGD("brightness delta=%d", delta);
        } else {
            return ret;
        }
    }

    if (!mDisplay->isConfigSettingEnabled() && effectiveOpRate == mDisplayNsOperationRate) {
        OP_MANAGER_LOGI("rate switching is disabled, skip NS op rate update");
        return ret;
    } else if (effectiveOpRate > LP_OP_RATE) {
        ret = setTargetOperationRate(effectiveOpRate);
    }

    OP_MANAGER_LOGI("Target@%d(desired:%d) | Refresh@%d(peak:%d), Battery:%s, DBV:%d(NsMin:%d)",
                    mDisplayTargetOperationRate, desiredOpRate, curRefreshRate,
                    mDisplayPeakRefreshRate, mDisplayLowBatteryModeEnabled ? "Low" : "OK",
                    mDisplayLastDbv, mDisplayNsMinDbv);
    return ret;
}

void ExynosPrimaryDisplayModule::checkPreblendingRequirement() {
    if (!hasDisplayColor()) {
        DISPLAY_LOGD(eDebugTDM, "%s is skipped because of no displaycolor", __func__);
        return;
    }

    String8 log;
    int count = 0;

    auto checkPreblending = [&](const int idx, ExynosMPPSource* mppSrc) -> int {
        auto* colorManager = getColorManager();
        if (!colorManager) return false;
        auto& dpp = colorManager->getDppForLayer(mppSrc);
        mppSrc->mNeedPreblending =
                dpp.EotfLut().enable | dpp.Gm().enable | dpp.Dtm().enable | dpp.OetfLut().enable;
        if (hwcCheckDebugMessages(eDebugTDM)) {
            log.appendFormat(" i=%d,pb(%d-%d,%d,%d,%d)", idx, mppSrc->mNeedPreblending,
                             dpp.EotfLut().enable, dpp.Gm().enable, dpp.Dtm().enable,
                             dpp.OetfLut().enable);
        }
        return mppSrc->mNeedPreblending;
    };

    // for client target
    count += checkPreblending(-1, &mClientCompositionInfo);

    // for normal layers
    for (size_t i = 0; i < mLayers.size(); ++i) {
        count += checkPreblending(i, mLayers[i]);
    }
    DISPLAY_LOGD(eDebugTDM, "disp(%d),cnt=%d%s", mDisplayId, count, log.c_str());
}
