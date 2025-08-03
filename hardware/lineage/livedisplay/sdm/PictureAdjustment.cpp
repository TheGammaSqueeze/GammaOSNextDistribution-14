/*
 * Copyright (C) 2019-2020 The LineageOS Project
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

#ifdef LIVES_IN_SYSTEM
#define LOG_TAG "lineage.livedisplay@2.0-impl-sdm"
#else
#define LOG_TAG "vendor.lineage.livedisplay@2.0-impl-sdm"
#endif

#include "livedisplay/sdm/PictureAdjustment.h"

#include <android-base/logging.h>

#include "livedisplay/sdm/Utils.h"

namespace vendor {
namespace lineage {
namespace livedisplay {
namespace V2_0 {
namespace sdm {

using ::android::OK;
using ::android::hardware::Void;

PictureAdjustment::PictureAdjustment(std::shared_ptr<SDMController> controller)
    : controller_(std::move(controller)) {
    if (!isReady()) {
        LOG(FATAL) << "PictureAdjustment backend not ready, exiting.";
    }
}

bool PictureAdjustment::isReady() {
    static int supported = -1;

    if (supported >= 0) {
        return supported;
    }

    if (utils::CheckFeatureVersion(controller_, utils::FEATURE_VER_SW_PA_API) != OK) {
        supported = 0;
        return false;
    }

    HsicRanges r{};
    if (controller_->getGlobalPaRange(&r) != OK) {
        supported = 0;
        return false;
    }

    supported = r.hue.max != 0 && r.hue.min != 0 && r.saturation.max != 0.f &&
                r.saturation.min != 0.f && r.intensity.max != 0.f && r.intensity.min != 0.f &&
                r.contrast.max != 0.f && r.contrast.min != 0.f;

    return supported;
}

HSIC PictureAdjustment::getPictureAdjustmentInternal() {
    HsicConfig config{};

    if (controller_->getGlobalPaConfig(&config) == OK) {
        return HSIC{static_cast<float>(config.data.hue), config.data.saturation,
                    config.data.intensity, config.data.contrast, config.data.saturation_threshold};
    }

    return HSIC{};
}

void PictureAdjustment::updateDefaultPictureAdjustment() {
    default_pa_ = getPictureAdjustmentInternal();
}

// Methods from ::vendor::lineage::livedisplay::V2_0::IPictureAdjustment follow.
Return<void> PictureAdjustment::getHueRange(getHueRange_cb _hidl_cb) {
    FloatRange range{};
    HsicRanges r{};

    if (controller_->getGlobalPaRange(&r) == OK) {
        range.max = r.hue.max;
        range.min = r.hue.min;
        range.step = r.hue.step;
    }

    _hidl_cb(range);
    return Void();
}

Return<void> PictureAdjustment::getSaturationRange(getSaturationRange_cb _hidl_cb) {
    FloatRange range{};
    HsicRanges r{};

    if (controller_->getGlobalPaRange(&r) == OK) {
        range.max = r.saturation.max;
        range.min = r.saturation.min;
        range.step = r.saturation.step;
    }

    _hidl_cb(range);
    return Void();
}

Return<void> PictureAdjustment::getIntensityRange(getIntensityRange_cb _hidl_cb) {
    FloatRange range{};
    HsicRanges r{};

    if (controller_->getGlobalPaRange(&r) == OK) {
        range.max = r.intensity.max;
        range.min = r.intensity.min;
        range.step = r.intensity.step;
    }

    _hidl_cb(range);
    return Void();
}

Return<void> PictureAdjustment::getContrastRange(getContrastRange_cb _hidl_cb) {
    FloatRange range{};
    HsicRanges r{};

    if (controller_->getGlobalPaRange(&r) == OK) {
        range.max = r.contrast.max;
        range.min = r.contrast.min;
        range.step = r.contrast.step;
    }

    _hidl_cb(range);
    return Void();
}

Return<void> PictureAdjustment::getSaturationThresholdRange(
        getSaturationThresholdRange_cb _hidl_cb) {
    FloatRange range{};
    HsicRanges r{};

    if (controller_->getGlobalPaRange(&r) == OK) {
        range.max = r.saturation_threshold.max;
        range.min = r.saturation_threshold.min;
        range.step = r.saturation_threshold.step;
    }

    _hidl_cb(range);
    return Void();
}

Return<void> PictureAdjustment::getPictureAdjustment(getPictureAdjustment_cb _hidl_cb) {
    _hidl_cb(getPictureAdjustmentInternal());
    return Void();
}

Return<void> PictureAdjustment::getDefaultPictureAdjustment(
        getDefaultPictureAdjustment_cb _hidl_cb) {
    _hidl_cb(default_pa_);
    return Void();
}

Return<bool> PictureAdjustment::setPictureAdjustment(
        const ::vendor::lineage::livedisplay::V2_0::HSIC& hsic) {
    HsicConfig config = {0,
                         {static_cast<int32_t>(hsic.hue), hsic.saturation, hsic.intensity,
                          hsic.contrast, hsic.saturationThreshold}};

    return controller_->setGlobalPaConfig(&config) == OK;
}

}  // namespace sdm
}  // namespace V2_0
}  // namespace livedisplay
}  // namespace lineage
}  // namespace vendor
