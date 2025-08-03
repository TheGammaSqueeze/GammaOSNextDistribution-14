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

#define ATRACE_TAG (ATRACE_TAG_GRAPHICS | ATRACE_TAG_HAL)

#include <cutils/properties.h>
#include <poll.h>

#include "BrightnessController.h"
#include "ExynosHWCModule.h"

void BrightnessController::LinearBrightnessTable::Init(const struct brightness_capability* cap) {
    if (!cap) {
        return;
    }
    setBrightnessRangeFromAttribute(cap->normal, mBrightnessRanges[BrightnessMode::BM_NOMINAL]);
    setBrightnessRangeFromAttribute(cap->hbm, mBrightnessRanges[BrightnessMode::BM_HBM]);
    if (mBrightnessRanges[BrightnessMode::BM_NOMINAL].brightness_max ==
        mBrightnessRanges[BrightnessMode::BM_HBM].brightness_min) {
        mBrightnessRanges[BrightnessMode::BM_HBM].brightness_min_exclusive = true;
    }
    if (!mBrightnessRanges.at(BrightnessMode::BM_NOMINAL).IsValid()) {
        ALOGE("%s: brightness range for BM_NOMINAL is invalid!", __func__);
        return;
    }
    //  BM_HBM range is optional for some devices
    if (mBrightnessRanges.count(BrightnessMode::BM_HBM) > 0) {
        if (!mBrightnessRanges.at(BrightnessMode::BM_HBM).IsValid()) {
            ALOGE("%s: brightness range for BM_HBM is invalid!", __func__);
            return;
        }
    }
    mIsValid = true;
}

std::optional<float> BrightnessController::LinearBrightnessTable::NitsToBrightness(
        float nits) const {
    BrightnessMode mode = GetBrightnessModeForNits(nits);
    if (mode == BrightnessMode::BM_INVALID) {
        return std::nullopt;
    }

    const DisplayBrightnessRange& range = mBrightnessRanges.at(mode);
    const float brightness = LinearInterpolation(nits,
        range.nits_min, range.nits_max,
        range.brightness_min, range.brightness_max);
    if (isnan(brightness)) {
        return std::nullopt;
    }

    return brightness;
}

std::optional<float> BrightnessController::LinearBrightnessTable::DbvToBrightness(
        uint32_t dbv) const {
    BrightnessMode bm = getBrightnessModeForDbv(dbv);
    if (bm == BrightnessMode::BM_INVALID) {
        return std::nullopt;
    }

    std::optional<float> nits = DbvToNits(bm, dbv);
    if (nits == std::nullopt) {
        return std::nullopt;
    }

    return NitsToBrightness(nits.value());
}

std::optional<float> BrightnessController::LinearBrightnessTable::BrightnessToNits(
        float brightness, BrightnessMode& bm) const {
    bm = GetBrightnessMode(brightness);
    if (bm == BrightnessMode::BM_MAX) {
        return std::nullopt;
    }
    const DisplayBrightnessRange& range = mBrightnessRanges.at(bm);
    float nits = LinearInterpolation(brightness, range.brightness_min, range.brightness_max,
                                     range.nits_min, range.nits_max);
    if (isnan(nits)) {
        return std::nullopt;
    }

    return nits;
}

std::optional<uint32_t> BrightnessController::LinearBrightnessTable::NitsToDbv(BrightnessMode bm,
                                                                               float nits) const {
    if (mBrightnessRanges.count(bm) == 0) {
        return std::nullopt;
    }
    const auto& range = mBrightnessRanges.at(bm);
    float dbv = 0.0;

    dbv = LinearInterpolation(nits, range.nits_min, range.nits_max, range.dbv_min, range.dbv_max);
    if (isnan(dbv) || dbv < 0) {
        return std::nullopt;
    }
    return lround(dbv);
}

std::optional<float> BrightnessController::LinearBrightnessTable::DbvToNits(BrightnessMode bm,
                                                                            uint32_t dbv) const {
    if (mBrightnessRanges.count(bm) == 0) {
        return std::nullopt;
    }
    const auto& range = mBrightnessRanges.at(bm);
    float nits = 0.0;

    nits = LinearInterpolation(dbv, range.dbv_min, range.dbv_max, range.nits_min, range.nits_max);
    if (isnan(nits)) {
        return std::nullopt;
    }
    return nits;
}

BrightnessController::BrightnessController(int32_t panelIndex, std::function<void(void)> refresh,
                                           std::function<void(void)> updateDcLhbm)
      : mPanelIndex(panelIndex),
        mEnhanceHbmReq(false),
        mLhbmReq(false),
        mBrightnessFloatReq(-1),
        mBrightnessLevel(0),
        mGhbm(HbmMode::OFF),
        mDimming(false),
        mLhbm(false),
        mSdrDim(false),
        mPrevSdrDim(false),
        mDimBrightnessReq(false),
        mOperationRate(0),
        mFrameRefresh(refresh),
        mHdrLayerState(HdrLayerState::kHdrNone),
        mUpdateDcLhbm(updateDcLhbm) {
    initBrightnessSysfs();
    initCabcSysfs();
}

BrightnessController::~BrightnessController() {
    if (mDimmingLooper) {
        mDimmingLooper->removeMessages(mDimmingHandler);
    }
    if (mDimmingThreadRunning) {
        mDimmingLooper->sendMessage(mDimmingHandler, DimmingMsgHandler::MSG_QUIT);
        mDimmingThread.join();
    }
}

void BrightnessController::updateBrightnessTable(std::unique_ptr<const IBrightnessTable>& table) {
    if (table && table->GetBrightnessRange(BrightnessMode::BM_NOMINAL)) {
        ALOGI("%s: apply brightness table from libdisplaycolor", __func__);
        mBrightnessTable = std::move(table);
    } else {
        ALOGW("%s: table is not valid!", __func__);
    }
    if (!mBrightnessTable) {
        ALOGE("%s: brightness table is not available!", __func__);
        return;
    }
    auto normal_range = mBrightnessTable->GetBrightnessRange(BrightnessMode::BM_NOMINAL);
    if (!normal_range) {
        ALOGE("%s: normal brightness range not available!", __func__);
        return;
    }

    // init to min before SF sets the brightness
    mDisplayWhitePointNits = normal_range.value().get().nits_min;
    mPrevDisplayWhitePointNits = mDisplayWhitePointNits;
    mBrightnessIntfSupported = true;

    String8 nodeName;
    nodeName.appendFormat(kDimBrightnessFileNode, mPanelIndex);

    std::ifstream ifsDimBrightness(nodeName.c_str());
    if (ifsDimBrightness.fail()) {
        ALOGW("%s fail to open %s", __func__, nodeName.c_str());
    } else {
        ifsDimBrightness >> mDimBrightness;
        ifsDimBrightness.close();
        if (mDimBrightness >= normal_range.value().get().dbv_min) mDimBrightness = 0;
    }
    mDbmSupported = !!mDimBrightness;
    ALOGI("%s mDimBrightness=%d, mDbmSupported=%d", __func__, mDimBrightness, mDbmSupported);
}

int BrightnessController::initDrm(const DrmDevice& drmDevice, const DrmConnector& connector) {
    initBrightnessTable(drmDevice, connector);

    initDimmingUsage();

    mLhbmSupported = connector.lhbm_on().id() != 0;
    mGhbmSupported = connector.hbm_mode().id() != 0;

    /* allow the first brightness to apply */
    mBrightnessFloatReq.set_dirty();
    return NO_ERROR;
}

void BrightnessController::initDimmingUsage() {
    String8 propName;
    propName.appendFormat(kDimmingUsagePropName, mPanelIndex);

    mBrightnessDimmingUsage =
            static_cast<BrightnessDimmingUsage>(property_get_int32(propName.c_str(), 0));

    propName.clear();
    propName.appendFormat(kDimmingHbmTimePropName, mPanelIndex);
    mHbmDimmingTimeUs = property_get_int32(propName.c_str(), kHbmDimmingTimeUs);

    if (mBrightnessDimmingUsage == BrightnessDimmingUsage::NORMAL) {
        mDimming.store(true);
    }

    if (mBrightnessDimmingUsage == BrightnessDimmingUsage::HBM) {
        mDimmingHandler = new DimmingMsgHandler(this);
        mDimmingThread = std::thread(&BrightnessController::dimmingThread, this);
    }
}

void BrightnessController::initBrightnessSysfs() {
    String8 nodeName;
    nodeName.appendFormat(BRIGHTNESS_SYSFS_NODE, mPanelIndex);
    mBrightnessOfs.open(nodeName.c_str(), std::ofstream::out);
    if (mBrightnessOfs.fail()) {
        ALOGE("%s %s fail to open", __func__, nodeName.c_str());
        return;
    }

    nodeName.clear();
    nodeName.appendFormat(MAX_BRIGHTNESS_SYSFS_NODE, mPanelIndex);

    std::ifstream ifsMaxBrightness(nodeName.c_str());
    if (ifsMaxBrightness.fail()) {
        ALOGE("%s fail to open %s", __func__, nodeName.c_str());
        return;
    }

    ifsMaxBrightness >> mMaxBrightness;
    ifsMaxBrightness.close();

    nodeName.clear();
    nodeName.appendFormat(kGlobalAclModeFileNode, mPanelIndex);
    mAclModeOfs.open(nodeName.c_str(), std::ofstream::out);
    if (mAclModeOfs.fail()) {
        ALOGI("%s %s not supported", __func__, nodeName.c_str());
    } else {
        String8 propName;
        propName.appendFormat(kAclModeDefaultPropName, mPanelIndex);

        mAclModeDefault = static_cast<AclMode>(property_get_int32(propName, 0));
        mAclMode.set_dirty();
    }
}

void BrightnessController::initCabcSysfs() {
    mCabcSupport = property_get_bool("vendor.display.cabc.supported", false);
    if (!mCabcSupport) return;

    String8 nodeName;
    nodeName.appendFormat(kLocalCabcModeFileNode, mPanelIndex);

    mCabcModeOfs.open(nodeName.c_str(), std::ofstream::out);
    if (mCabcModeOfs.fail()) {
        ALOGE("%s %s fail to open", __func__, nodeName.c_str());
        return;
    }
}

void BrightnessController::initBrightnessTable(const DrmDevice& drmDevice,
                                               const DrmConnector& connector) {
    if (connector.brightness_cap().id() == 0) {
        ALOGD("the brightness_cap is not supported");
        return;
    }

    const auto [ret, blobId] = connector.brightness_cap().value();
    if (ret) {
        ALOGE("Fail to get brightness_cap (ret = %d)", ret);
        return;
    }

    if (blobId == 0) {
        ALOGE("the brightness_cap is supported but blob is not valid");
        return;
    }

    drmModePropertyBlobPtr blob = drmModeGetPropertyBlob(drmDevice.fd(), blobId);
    if (blob == nullptr) {
        ALOGE("Fail to get brightness_cap blob");
        return;
    }

    const struct brightness_capability *cap =
            reinterpret_cast<struct brightness_capability *>(blob->data);
    mKernelBrightnessTable.Init(cap);
    if (mKernelBrightnessTable.IsValid()) {
        mBrightnessTable = std::make_unique<LinearBrightnessTable>(mKernelBrightnessTable);
    }

    parseHbmModeEnums(connector.hbm_mode());

    drmModeFreePropertyBlob(blob);
}

int BrightnessController::processEnhancedHbm(bool on) {
    if (!mGhbmSupported) {
        return HWC2_ERROR_UNSUPPORTED;
    }

    std::lock_guard<std::recursive_mutex> lock(mBrightnessMutex);
    mEnhanceHbmReq.store(on);
    if (mEnhanceHbmReq.is_dirty()) {
        updateStates();
    }
    return NO_ERROR;
}

void BrightnessController::processDimmingOff() {
    std::lock_guard<std::recursive_mutex> lock(mBrightnessMutex);
    if (mHbmDimming) {
        mHbmDimming = false;
        updateStates();
        mFrameRefresh();
    }
}

int BrightnessController::updateAclMode() {
    if (!mAclModeOfs.is_open()) return HWC2_ERROR_UNSUPPORTED;

    if (mColorRenderIntent.get() == ColorRenderIntent::COLORIMETRIC) {
        mAclMode.store(AclMode::ACL_ENHANCED);
    } else {
        mAclMode.store(mAclModeDefault);
    }

    if (applyAclViaSysfs() == HWC2_ERROR_NO_RESOURCES)
        ALOGW("%s try to apply acl_mode when brightness changed", __func__);

    return NO_ERROR;
}

int BrightnessController::applyAclViaSysfs() {
    if (!mAclModeOfs.is_open()) return NO_ERROR;
    if (!mAclMode.is_dirty()) return NO_ERROR;

    mAclModeOfs.seekp(std::ios_base::beg);
    mAclModeOfs << std::to_string(static_cast<uint8_t>(mAclMode.get()));
    mAclModeOfs.flush();
    if (mAclModeOfs.fail()) {
        ALOGW("%s write acl_mode to %d error = %s", __func__, mAclMode.get(), strerror(errno));
        mAclModeOfs.clear();
        return HWC2_ERROR_NO_RESOURCES;
    }

    mAclMode.clear_dirty();
    ALOGI("%s acl_mode = %d", __func__, mAclMode.get());

    return NO_ERROR;
}

int BrightnessController::processDisplayBrightness(float brightness, const nsecs_t vsyncNs,
                                                   bool waitPresent) {
    uint32_t level;
    bool ghbm;

    if (mIgnoreBrightnessUpdateRequests) {
        ALOGI("%s: Brightness update is ignored. requested: %f, current: %f",
            __func__, brightness, mBrightnessFloatReq.get());
        return NO_ERROR;
    }

    if (brightness < -1.0f || brightness > 1.0f) {
        return HWC2_ERROR_BAD_PARAMETER;
    }

    ATRACE_CALL();

    /* update ACL */
    if (applyAclViaSysfs() == HWC2_ERROR_NO_RESOURCES)
        ALOGE("%s failed to apply acl_mode", __func__);

    if (!mBrightnessIntfSupported) {
        level = brightness < 0 ? 0 : static_cast<uint32_t>(brightness * mMaxBrightness + 0.5f);
        return applyBrightnessViaSysfs(level);
    }

    {
        std::lock_guard<std::recursive_mutex> lock(mBrightnessMutex);
        /* apply the first brightness */
        if (mBrightnessFloatReq.is_dirty()) mBrightnessLevel.set_dirty();

        mBrightnessFloatReq.store(brightness);
        if (!mBrightnessFloatReq.is_dirty()) {
            return NO_ERROR;
        }

        // check if it will go drm path for below cases.
        // case 1: hbm state will change
        // case 2: for hwc3, brightness command could apply at next present if possible
        if (queryBrightness(brightness, &ghbm, &level) == NO_ERROR) {
            // ghbm on/off always go drm path
            // check if this will cause a hbm transition
            if (mGhbmSupported && (mGhbm.get() != HbmMode::OFF) != ghbm) {
                // this brightness change will go drm path
                updateStates();
                mFrameRefresh(); // force next frame to update brightness
                return NO_ERROR;
            }
            // there will be a Present to apply this brightness change
            if (waitPresent) {
                // this brightness change will go drm path
                updateStates();
                return NO_ERROR;
            }
        } else {
            level = brightness < 0 ? 0 : static_cast<uint32_t>(brightness * mMaxBrightness + 0.5f);
        }
        // clear dirty before go sysfs path
        mBrightnessFloatReq.clear_dirty();
    }

    // Sysfs path is faster than drm path. If there is an unchecked drm path change, the sysfs
    // path should check the sysfs content.
    if (mUncheckedGbhmRequest) {
        ATRACE_NAME("check_ghbm_mode");
        checkSysfsStatus(GetPanelSysfileByIndex(kGlobalHbmModeFileNode),
                         {std::to_string(toUnderlying(mPendingGhbmStatus.load()))}, vsyncNs * 5);
        mUncheckedGbhmRequest = false;
    }

    if (mUncheckedLhbmRequest) {
        ATRACE_NAME("check_lhbm_mode");
        checkSysfsStatus(GetPanelSysfileByIndex(kLocalHbmModeFileNode),
                         {std::to_string(mPendingLhbmStatus)}, vsyncNs * 5);
        mUncheckedLhbmRequest = false;
    }

    return applyBrightnessViaSysfs(level);
}

int BrightnessController::ignoreBrightnessUpdateRequests(bool ignore) {
    mIgnoreBrightnessUpdateRequests = ignore;

    return NO_ERROR;
}

int BrightnessController::setBrightnessNits(float nits, const nsecs_t vsyncNs) {
    ALOGI("%s set brightness to %f nits", __func__,  nits);

    std::optional<float> brightness = mBrightnessTable ?
        mBrightnessTable->NitsToBrightness(nits) : std::nullopt;

    if (brightness == std::nullopt) {
        ALOGI("%s could not find brightness for %f nits", __func__, nits);
        return -EINVAL;
    }

    return processDisplayBrightness(brightness.value(), vsyncNs);
}

int BrightnessController::setBrightnessDbv(uint32_t dbv, const nsecs_t vsyncNs) {
    ALOGI("%s set brightness to %u dbv", __func__, dbv);

    std::optional<float> brightness =
            mBrightnessTable ? mBrightnessTable->DbvToBrightness(dbv) : std::nullopt;

    if (brightness == std::nullopt) {
        ALOGI("%s could not find brightness for %d dbv", __func__, dbv);
        return -EINVAL;
    }

    return processDisplayBrightness(brightness.value(), vsyncNs);
}

// In HWC3, brightness change could be applied via drm commit or sysfs path.
// If a brightness change command does not come with a frame update, this
// function wil be called to apply the brghtness change via sysfs path.
int BrightnessController::applyPendingChangeViaSysfs(const nsecs_t vsyncNs) {
    ATRACE_CALL();
    uint32_t level;
    {
        std::lock_guard<std::recursive_mutex> lock(mBrightnessMutex);

        if (!mBrightnessLevel.is_dirty()) {
            return NO_ERROR;
        }

        // there will be a drm commit to apply this brightness change if a GHBM change is pending.
        if (mGhbm.is_dirty()) {
            ALOGI("%s standalone brightness change will be handled by next frame update for GHBM",
                  __func__);
            return NO_ERROR;
        }

        // there will be a drm commit to apply this brightness change if a LHBM change is pending.
        if (mLhbm.is_dirty()) {
            ALOGI("%s standalone brightness change will be handled by next frame update for LHBM",
                  __func__);
            return NO_ERROR;
        }

        // there will be a drm commit to apply this brightness change if a operation rate change is
        // pending.
        if (mOperationRate.is_dirty()) {
            ALOGI("%s standalone brightness change will be handled by next frame update for "
                  "operation rate",
                  __func__);
            return NO_ERROR;
        }

        level = mBrightnessLevel.get();
    }

    if (mUncheckedBlRequest) {
        ATRACE_NAME("check_bl_value");
        checkSysfsStatus(GetPanelSysfileByIndex(BRIGHTNESS_SYSFS_NODE),
                         {std::to_string(mPendingBl)}, vsyncNs * 5);
        mUncheckedBlRequest = false;
    }

    return applyBrightnessViaSysfs(level);
}

int BrightnessController::processLocalHbm(bool on) {
    if (!mLhbmSupported) {
        return HWC2_ERROR_UNSUPPORTED;
    }

    std::lock_guard<std::recursive_mutex> lock(mBrightnessMutex);
    mLhbmReq.store(on);
    // As kernel timeout timer might disable LHBM without letting HWC know, enforce mLhbmReq and
    // mLhbm dirty to ensure the enabling request can be passed through kernel unconditionally.
    // TODO-b/260915350: move LHBM timeout mechanism from kernel to HWC for easier control and sync.
    if (on) {
        mLhbmReq.set_dirty();
        mLhbm.set_dirty();
    }
    if (mLhbmReq.is_dirty()) {
        updateStates();
    }

    return NO_ERROR;
}

void BrightnessController::updateFrameStates(HdrLayerState hdrState, bool sdrDim) {
    mHdrLayerState.store(hdrState);
    if (!mGhbmSupported) {
        return;
    }

    std::lock_guard<std::recursive_mutex> lock(mBrightnessMutex);
    mPrevSdrDim.store(mSdrDim.get());
    mSdrDim.store(sdrDim);
    if (mSdrDim.is_dirty() || mPrevSdrDim.is_dirty()) {
        updateStates();
    }
}

void BrightnessController::updateColorRenderIntent(int32_t intent) {
    mColorRenderIntent.store(static_cast<ColorRenderIntent>(intent));
    if (mColorRenderIntent.is_dirty()) {
        updateAclMode();
        ALOGI("%s Color Render Intent = %d", __func__, mColorRenderIntent.get());
        mColorRenderIntent.clear_dirty();
    }
}

int BrightnessController::processInstantHbm(bool on) {
    if (!mGhbmSupported) {
        return HWC2_ERROR_UNSUPPORTED;
    }

    std::lock_guard<std::recursive_mutex> lock(mBrightnessMutex);
    mInstantHbmReq.store(on);
    if (mInstantHbmReq.is_dirty()) {
        updateStates();
    }
    return NO_ERROR;
}

int BrightnessController::processDimBrightness(bool on) {
    std::lock_guard<std::recursive_mutex> lock(mBrightnessMutex);
    mDimBrightnessReq.store(on);
    if (mDimBrightnessReq.is_dirty()) {
        updateStates();
        ALOGI("%s request = %d", __func__, mDimBrightnessReq.get());
    }
    return NO_ERROR;
}

float BrightnessController::getSdrDimRatioForInstantHbm() {
    if (!mBrightnessIntfSupported || !mGhbmSupported) {
        return 1.0f;
    }

    std::lock_guard<std::recursive_mutex> lock(mBrightnessMutex);
    if (!mInstantHbmReq.get()) {
        return 1.0f;
    }

    float sdr = 0;
    if (queryBrightness(mBrightnessFloatReq.get(), nullptr, nullptr, &sdr) != NO_ERROR) {
        return 1.0f;
    }

    auto hbm_range = mBrightnessTable->GetBrightnessRange(BrightnessMode::BM_HBM);
    if (!hbm_range) {
        ALOGE("%s error HBM brightness range not available!", __func__);
        return 1.0f;
    }
    float peak = hbm_range.value().get().nits_max;
    if (sdr == 0 || peak == 0) {
        ALOGW("%s error luminance value sdr %f peak %f", __func__, sdr, peak);
        return 1.0f;
    }

    float ratio = sdr / peak;
    if (ratio < kGhbmMinDimRatio) {
        ALOGW("%s sdr dim ratio %f too small", __func__, ratio);
        ratio = kGhbmMinDimRatio;
    }

    return ratio;
}

int BrightnessController::processOperationRate(int32_t hz) {
    std::lock_guard<std::recursive_mutex> lock(mBrightnessMutex);
    if (mOperationRate.get() != hz) {
        ATRACE_CALL();
        ALOGI("%s: store operation rate %d", __func__, hz);
        mOperationRate.set_dirty();
        mOperationRate.store(hz);
        updateStates();
    }

    return NO_ERROR;
}

void BrightnessController::onClearDisplay(bool needModeClear) {
    resetLhbmState();
    mInstantHbmReq.reset(false);

    if (mBrightnessLevel.is_dirty()) applyBrightnessViaSysfs(mBrightnessLevel.get());

    if (!needModeClear) return;

    std::lock_guard<std::recursive_mutex> lock(mBrightnessMutex);
    mEnhanceHbmReq.reset(false);
    mBrightnessFloatReq.reset(-1);

    mBrightnessLevel.reset(0);
    mDisplayWhitePointNits = 0;
    mPrevDisplayWhitePointNits = 0;
    mGhbm.reset(HbmMode::OFF);
    mDimming.reset(false);
    mHbmDimming = false;
    if (mBrightnessDimmingUsage == BrightnessDimmingUsage::NORMAL) {
        mDimming.store(true);
    }
    mOperationRate.reset(0);

    std::lock_guard<std::recursive_mutex> lock1(mCabcModeMutex);
    mCabcMode.reset(CabcMode::OFF);
}

int BrightnessController::prepareFrameCommit(ExynosDisplay& display, const DrmConnector& connector,
                                             ExynosDisplayDrmInterface::DrmModeAtomicReq& drmReq,
                                             const bool mixedComposition, bool& ghbmSync,
                                             bool& lhbmSync, bool& blSync, bool& opRateSync) {
    int ret;

    ghbmSync = false;
    lhbmSync = false;
    blSync = false;
    opRateSync = false;

    ATRACE_CALL();
    std::lock_guard<std::recursive_mutex> lock(mBrightnessMutex);

    bool sync = false;
    if (mixedComposition && mPrevDisplayWhitePointNits > 0 && mDisplayWhitePointNits > 0) {
        float diff = std::abs(mPrevDisplayWhitePointNits - mDisplayWhitePointNits);
        float min = std::min(mPrevDisplayWhitePointNits, mDisplayWhitePointNits);
        if (diff / min > kBrightnessSyncThreshold) {
            sync = true;
            ALOGD("%s: enable brightness sync for change from %f to %f", __func__,
                  mPrevDisplayWhitePointNits, mDisplayWhitePointNits);
        }
    }

    if (mDimming.is_dirty()) {
        if ((ret = drmReq.atomicAddProperty(connector.id(), connector.dimming_on(),
                                            mDimming.get())) < 0) {
            ALOGE("%s: Fail to set dimming_on property", __func__);
        }
        mDimming.clear_dirty();
    }

    if (mLhbm.is_dirty() && mLhbmSupported) {
        if ((ret = drmReq.atomicAddProperty(connector.id(), connector.lhbm_on(),
                                            mLhbm.get())) < 0) {
            ALOGE("%s: Fail to set lhbm_on property", __func__);
        } else {
            lhbmSync = true;
        }

        auto dbv = mBrightnessLevel.get();
        auto old_dbv = dbv;
        if (mLhbm.get()) {
            mUpdateDcLhbm();
            uint32_t dbv_adj = 0;
            if (display.getColorAdjustedDbv(dbv_adj)) {
                ALOGW("failed to get adjusted dbv");
            } else if (dbv_adj != dbv && dbv_adj != 0) {
                if (mBrightnessTable) {
                    auto normal_range =
                            mBrightnessTable->GetBrightnessRange(BrightnessMode::BM_NOMINAL);
                    if (normal_range) {
                        dbv_adj = std::clamp(dbv_adj, normal_range.value().get().dbv_min,
                                             normal_range.value().get().dbv_max);
                    }
                }
                ALOGI("lhbm: adjust dbv from %d to %d", dbv, dbv_adj);
                dbv = dbv_adj;
                mLhbmBrightnessAdj = (dbv != old_dbv);
            }
        }

        if (mLhbmBrightnessAdj) {
            // case 1: lhbm on and dbv is changed, use the new dbv
            // case 2: lhbm off and dbv was changed at lhbm on, use current dbv
            if ((ret = drmReq.atomicAddProperty(connector.id(),
                                               connector.brightness_level(), dbv)) < 0) {
                ALOGE("%s: Fail to set brightness_level property", __func__);
            } else {
                blSync = true;
                mUncheckedBlRequest = true;
                mPendingBl = dbv;
            }
        }

        // mLhbmBrightnessAdj will last from LHBM on to off
        if (!mLhbm.get() && mLhbmBrightnessAdj) {
            mLhbmBrightnessAdj = false;
        }

        mLhbm.clear_dirty();
    }

    if (mBrightnessLevel.is_dirty()) {
        // skip if lhbm has updated bl
        if (!blSync) {
            if ((ret = drmReq.atomicAddProperty(connector.id(),
                                                connector.brightness_level(),
                                                mBrightnessLevel.get())) < 0) {
                ALOGE("%s: Fail to set brightness_level property", __func__);
            } else {
                mUncheckedBlRequest = true;
                mPendingBl = mBrightnessLevel.get();
                blSync = sync;
            }
        }
        mBrightnessLevel.clear_dirty();
        mPrevDisplayWhitePointNits = mDisplayWhitePointNits;
    }

    if (mGhbm.is_dirty() && mGhbmSupported) {
        HbmMode hbmMode = mGhbm.get();
        auto [hbmEnum, ret] = DrmEnumParser::halToDrmEnum(static_cast<int32_t>(hbmMode),
                                                          mHbmModeEnums);
        if (ret < 0) {
            ALOGE("Fail to convert hbm mode(%d)", hbmMode);
            return ret;
        }

        if ((ret = drmReq.atomicAddProperty(connector.id(), connector.hbm_mode(),
                                            hbmEnum)) < 0) {
            ALOGE("%s: Fail to set hbm_mode property", __func__);
        } else {
            ghbmSync = sync;
        }
        mGhbm.clear_dirty();
    }

    mHdrLayerState.clear_dirty();

    if (mOperationRate.is_dirty()) {
        if ((ret = drmReq.atomicAddProperty(connector.id(), connector.operation_rate(),
                                            mOperationRate.get())) < 0) {
            ALOGE("%s: Fail to set operation_rate property", __func__);
        } else {
            opRateSync = sync;
        }
        mOperationRate.clear_dirty();
    }

    return NO_ERROR;
}

void BrightnessController::DimmingMsgHandler::handleMessage(const ::android::Message& message) {
    ALOGI("%s %d", __func__, message.what);

    switch (message.what) {
        case MSG_DIMMING_OFF:
            mBrightnessController->processDimmingOff();
            break;

        case MSG_QUIT:
            mBrightnessController->mDimmingThreadRunning = false;
            break;
    }
}

void BrightnessController::dimmingThread() {
    mDimmingLooper = new Looper(false);
    Looper::setForThread(mDimmingLooper);
    mDimmingThreadRunning = true;
    while (mDimmingThreadRunning.load(std::memory_order_relaxed)) {
        mDimmingLooper->pollOnce(-1);
    }
}

// Process all requests to update states for next commit
int BrightnessController::updateStates() {
    bool ghbm;
    uint32_t level;
    float brightness = mInstantHbmReq.get() ? 1.0f : mBrightnessFloatReq.get();
    if (queryBrightness(brightness, &ghbm, &level, &mDisplayWhitePointNits)) {
        ALOGW("%s failed to convert brightness %f", __func__, mBrightnessFloatReq.get());
        return HWC2_ERROR_UNSUPPORTED;
    }

    mBrightnessLevel.store(level);
    mLhbm.store(mLhbmReq.get());

    // turn off irc for sun light visibility
    bool irc = !mEnhanceHbmReq.get();
    if (ghbm) {
        mGhbm.store(irc ? HbmMode::ON_IRC_ON : HbmMode::ON_IRC_OFF);
    } else {
        mGhbm.store(HbmMode::OFF);
    }

    if (mLhbm.is_dirty()) {
        // Next sysfs path should verify this change has been applied.
        mUncheckedLhbmRequest = true;
        mPendingLhbmStatus = mLhbm.get();
    }
    if (mGhbm.is_dirty()) {
        // Next sysfs path should verify this change has been applied.
        mUncheckedGbhmRequest = true;
        mPendingGhbmStatus = mGhbm.get();
    }

    // no dimming for instant hbm
    // no dimming if current or previous frame is mixed composition
    //  - frame N-1: no HDR, HBM off, no sdr dim
    //  - frame N: HDR visible HBM on, sdr dim is enabled
    //  - frame N+1, HDR gone, HBM off, no sdr dim.
    //  We don't need panel dimming for HBM on at frame N and HBM off at frame N+1
    bool dimming = !mInstantHbmReq.get() && !mSdrDim.get() && !mPrevSdrDim.get();
    switch (mBrightnessDimmingUsage) {
        case BrightnessDimmingUsage::HBM:
            // turn on dimming at HBM on/off
            // turn off dimming after mHbmDimmingTimeUs or there is an instant hbm on/off
            if (mGhbm.is_dirty() && dimming) {
                mHbmDimming = true;
                if (mDimmingLooper) {
                    mDimmingLooper->removeMessages(mDimmingHandler,
                                                   DimmingMsgHandler::MSG_DIMMING_OFF);
                    mDimmingLooper->sendMessageDelayed(us2ns(mHbmDimmingTimeUs), mDimmingHandler,
                                                       DimmingMsgHandler::MSG_DIMMING_OFF);
                }
            }

            dimming = dimming && (mHbmDimming);
            break;

        case BrightnessDimmingUsage::NONE:
            dimming = false;
            break;

        default:
            break;
    }
    mDimming.store(dimming);

    mEnhanceHbmReq.clear_dirty();
    mLhbmReq.clear_dirty();
    mBrightnessFloatReq.clear_dirty();
    mInstantHbmReq.clear_dirty();
    mSdrDim.clear_dirty();
    mPrevSdrDim.clear_dirty();
    mDimBrightnessReq.clear_dirty();

    if (mBrightnessLevel.is_dirty() || mDimming.is_dirty() || mGhbm.is_dirty() ||
        mLhbm.is_dirty()) {
        printBrightnessStates("drm");
    }
    return NO_ERROR;
}

int BrightnessController::queryBrightness(float brightness, bool *ghbm, uint32_t *level,
                                               float *nits) {
    if (!mBrightnessIntfSupported) {
        return HWC2_ERROR_UNSUPPORTED;
    }

    if (mBrightnessTable == nullptr) {
        ALOGE("%s: brightness table is empty!", __func__);
        return HWC2_ERROR_UNSUPPORTED;
    }

    auto normal_range = mBrightnessTable->GetBrightnessRange(BrightnessMode::BM_NOMINAL);
    if (!normal_range) {
        ALOGE("%s: normal brightness range not available!", __func__);
        return HWC2_ERROR_UNSUPPORTED;
    }

    if (brightness < 0) {
        // screen off
        if (ghbm) {
            *ghbm = false;
        }
        if (level) {
            *level = 0;
        }
        if (nits) {
            *nits = 0;
        }
        return NO_ERROR;
    }

    BrightnessMode bm = BrightnessMode::BM_MAX;
    std::optional<float> nits_value = mBrightnessTable->BrightnessToNits(brightness, bm);
    if (!nits_value) {
        return -EINVAL;
    }
    if (ghbm) {
        *ghbm = (bm == BrightnessMode::BM_HBM);
    }
    std::optional<uint32_t> dbv_value = mBrightnessTable->NitsToDbv(bm, nits_value.value());
    if (!dbv_value) {
        return -EINVAL;
    }

    if (level) {
        if ((bm == BrightnessMode::BM_NOMINAL) && mDbmSupported &&
            (mDimBrightnessReq.get() == true) &&
            (dbv_value == normal_range.value().get().dbv_min)) {
            *level = mDimBrightness;
        } else {
            *level = dbv_value.value();
        }
    }
    if (nits) {
        *nits = nits_value.value();
    }

    return NO_ERROR;
}

// Return immediately if it's already in the status. Otherwise poll the status
int BrightnessController::checkSysfsStatus(const std::string& file,
                                           const std::vector<std::string>& expectedValue,
                                           const nsecs_t timeoutNs) {
    ATRACE_CALL();

    if (expectedValue.size() == 0) {
      return -EINVAL;
    }

    char buf[16];
    UniqueFd fd = open(file.c_str(), O_RDONLY);
    if (fd.get() < 0) {
        ALOGE("%s failed to open sysfs %s: %s", __func__, file.c_str(), strerror(errno));
        return -ENOENT;
    }

    int size = read(fd.get(), buf, sizeof(buf));
    if (size <= 0) {
        ALOGE("%s failed to read from %s: %s", __func__, file.c_str(), strerror(errno));
        return -EIO;
    }

    // '- 1' to remove trailing '\n'
    std::string val = std::string(buf, size - 1);
    if (std::find(expectedValue.begin(), expectedValue.end(), val) != expectedValue.end()) {
        return OK;
    } else if (timeoutNs == 0) {
        // not get the expected value and no intention to wait
        return -EINVAL;
    }

    struct pollfd pfd;
    int ret = -EINVAL;

    auto startTime = systemTime(SYSTEM_TIME_MONOTONIC);
    pfd.fd = fd.get();
    pfd.events = POLLPRI;
    while (true) {
        auto currentTime = systemTime(SYSTEM_TIME_MONOTONIC);
        // int64_t for nsecs_t
        auto remainTimeNs = timeoutNs - (currentTime - startTime);
        if (remainTimeNs <= 0) {
            remainTimeNs = ms2ns(1);
        }
        int pollRet = poll(&pfd, 1, ns2ms(remainTimeNs));
        if (pollRet == 0) {
            ALOGW("%s poll %s timeout", __func__, file.c_str());
            // time out
            ret = -ETIMEDOUT;
            break;
        } else if (pollRet > 0) {
            if (!(pfd.revents & POLLPRI)) {
                continue;
            }

            lseek(fd.get(), 0, SEEK_SET);
            size = read(fd.get(), buf, sizeof(buf));
            if (size > 0) {
                val = std::string(buf, size - 1);
                if (std::find(expectedValue.begin(), expectedValue.end(), val) !=
                    expectedValue.end()) {
                    ret = OK;
                    break;
                } else {
                    std::string values;
                    for (auto& s : expectedValue) {
                        values += s + std::string(" ");
                    }
                    if (values.size() > 0) {
                        values.resize(values.size() - 1);
                    }
                    ALOGW("%s read %s expected %s after notified on file %s", __func__, val.c_str(),
                          values.c_str(), file.c_str());
                }
            } else {
                ret = -EIO;
                ALOGE("%s failed to read after notified %d on file %s", __func__, errno,
                      file.c_str());
                break;
            }
        } else {
            if (errno == EAGAIN || errno == EINTR) {
                continue;
            }

            ALOGE("%s poll failed %d on file %s", __func__, errno, file.c_str());
            ret = -errno;
            break;
        }
    };

    return ret;
}

void BrightnessController::resetLhbmState() {
    std::lock_guard<std::recursive_mutex> lock(mBrightnessMutex);
    mLhbmReq.reset(false);
    mLhbm.reset(false);
    mLhbmBrightnessAdj = false;
}

void BrightnessController::setOutdoorVisibility(LbeState state) {
    std::lock_guard<std::recursive_mutex> lock(mCabcModeMutex);
    mOutdoorVisibility = (state != LbeState::OFF);
}

int BrightnessController::updateCabcMode() {
    if (!mCabcSupport || mCabcModeOfs.fail()) return HWC2_ERROR_UNSUPPORTED;

    std::lock_guard<std::recursive_mutex> lock(mCabcModeMutex);
    CabcMode mode;
    if (mOutdoorVisibility)
        mode = CabcMode::OFF;
    else
        mode = isHdrLayerOn() ? CabcMode::CABC_MOVIE_MODE : CabcMode::CABC_UI_MODE;
    mCabcMode.store(mode);

    if (mCabcMode.is_dirty()) {
        applyCabcModeViaSysfs(static_cast<uint8_t>(mode));
        ALOGD("%s, isHdrLayerOn: %d, mOutdoorVisibility: %d.", __func__, isHdrLayerOn(),
              mOutdoorVisibility);
        mCabcMode.clear_dirty();
    }
    return NO_ERROR;
}

int BrightnessController::applyBrightnessViaSysfs(uint32_t level) {
    if (mBrightnessOfs.is_open()) {
        ATRACE_NAME("write_bl_sysfs");
        mBrightnessOfs.seekp(std::ios_base::beg);
        mBrightnessOfs << std::to_string(level);
        mBrightnessOfs.flush();
        if (mBrightnessOfs.fail()) {
            ALOGE("%s fail to write brightness %d", __func__, level);
            mBrightnessOfs.clear();
            return HWC2_ERROR_NO_RESOURCES;
        }

        {
            std::lock_guard<std::recursive_mutex> lock(mBrightnessMutex);
            mBrightnessLevel.reset(level);
            mPrevDisplayWhitePointNits = mDisplayWhitePointNits;
            printBrightnessStates("sysfs");
        }

        return NO_ERROR;
    }

    return HWC2_ERROR_UNSUPPORTED;
}

int BrightnessController::applyCabcModeViaSysfs(uint8_t mode) {
    if (!mCabcModeOfs.is_open()) return HWC2_ERROR_UNSUPPORTED;

    ATRACE_NAME("write_cabc_mode_sysfs");
    mCabcModeOfs.seekp(std::ios_base::beg);
    mCabcModeOfs << std::to_string(mode);
    mCabcModeOfs.flush();
    if (mCabcModeOfs.fail()) {
        ALOGE("%s fail to write CabcMode %d", __func__, mode);
        mCabcModeOfs.clear();
        return HWC2_ERROR_NO_RESOURCES;
    }
    ALOGI("%s Cabc_Mode=%d", __func__, mode);
    return NO_ERROR;
}

// brightness is normalized to current display brightness
bool BrightnessController::validateLayerBrightness(float brightness) {
    std::lock_guard<std::recursive_mutex> lock(mBrightnessMutex);
    if (!std::isfinite(brightness)) {
        ALOGW("%s layer brightness %f is not a valid floating value", __func__, brightness);
        return false;
    }

    if (brightness > 1.f || brightness < 0.f) {
        ALOGW("%s Brightness is out of [0, 1] range: %f", __func__, brightness);
        return false;
    }

    return true;
}

void BrightnessController::parseHbmModeEnums(const DrmProperty& property) {
    const std::vector<std::pair<uint32_t, const char *>> modeEnums = {
            {static_cast<uint32_t>(HbmMode::OFF), "Off"},
            {static_cast<uint32_t>(HbmMode::ON_IRC_ON), "On IRC On"},
            {static_cast<uint32_t>(HbmMode::ON_IRC_OFF), "On IRC Off"},
    };

    DrmEnumParser::parseEnums(property, modeEnums, mHbmModeEnums);
    for (auto &e : mHbmModeEnums) {
        ALOGD("hbm mode [hal: %d, drm: %" PRId64 ", %s]", e.first, e.second,
              modeEnums[e.first].second);
    }
}

/*
 * WARNING: This print is parsed by Battery Historian. Consult with the Battery
 *   Historian team before modifying (b/239640926).
 */
void BrightnessController::printBrightnessStates(const char* path) {
    ALOGI("path=%s, id=%d, level=%d, nits=%f, brightness=%f, DimmingOn=%d, Hbm=%d, LhbmOn=%d, "
          "OpRate=%d",
          path ?: "unknown", mPanelIndex, mBrightnessLevel.get(), mDisplayWhitePointNits,
          mBrightnessFloatReq.get(), mDimming.get(), mGhbm.get(), mLhbm.get(),
          mOperationRate.get());
}

void BrightnessController::dump(String8& result) {
    std::lock_guard<std::recursive_mutex> lock(mBrightnessMutex);

    result.appendFormat("BrightnessController:\n");
    result.appendFormat("\tsysfs support %d, max %d, valid brightness table %d, "
                        "lhbm supported %d, ghbm supported %d\n", mBrightnessOfs.is_open(),
                        mMaxBrightness, mBrightnessIntfSupported, mLhbmSupported, mGhbmSupported);
    result.appendFormat("\trequests: enhance hbm %d, lhbm %d, "
                        "brightness %f, instant hbm %d, DimBrightness %d\n",
                        mEnhanceHbmReq.get(), mLhbmReq.get(), mBrightnessFloatReq.get(),
                        mInstantHbmReq.get(), mDimBrightnessReq.get());
    result.appendFormat("\tstates: brighntess level %d, ghbm %d, dimming %d, lhbm %d",
                        mBrightnessLevel.get(), mGhbm.get(), mDimming.get(), mLhbm.get());
    result.appendFormat("\thdr layer state %d, unchecked lhbm request %d(%d), "
                        "unchecked ghbm request %d(%d)\n",
                        mHdrLayerState.get(), mUncheckedLhbmRequest.load(),
                        mPendingLhbmStatus.load(), mUncheckedGbhmRequest.load(),
                        mPendingGhbmStatus.load());
    result.appendFormat("\tdimming usage %d, hbm dimming %d, time us %d\n", mBrightnessDimmingUsage,
                        mHbmDimming, mHbmDimmingTimeUs);
    result.appendFormat("\twhite point nits current %f, previous %f\n", mDisplayWhitePointNits,
                        mPrevDisplayWhitePointNits);
    result.appendFormat("\tcabc supported %d, cabcMode %d\n", mCabcModeOfs.is_open(),
                        mCabcMode.get());
    result.appendFormat("\tignore brightness update request %d\n", mIgnoreBrightnessUpdateRequests);
    result.appendFormat("\tacl mode supported %d, acl mode %d\n", mAclModeOfs.is_open(),
                        mAclMode.get());
    result.appendFormat("\toperation rate %d\n", mOperationRate.get());

    result.appendFormat("\n");
}
