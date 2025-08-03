/*
 * Copyright (C) 2019 The LineageOS Project
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

#include "PictureAdjustment.h"

#include <dlfcn.h>

#include "Constants.h"
#include "Types.h"

namespace vendor {
namespace lineage {
namespace livedisplay {
namespace V2_0 {
namespace legacymm {

static sp<PictureAdjustment> sInstance;

PictureAdjustment::PictureAdjustment(void* libHandle) {
    sInstance = this;

    mLibHandle = libHandle;
    disp_api_supported =
            reinterpret_cast<int (*)(int32_t, int32_t)>(dlsym(mLibHandle, "disp_api_supported"));
    disp_api_get_pa_range =
            reinterpret_cast<int (*)(int32_t, void*)>(dlsym(mLibHandle, "disp_api_get_pa_range"));
    disp_api_get_pa_config =
            reinterpret_cast<int (*)(int32_t, void*)>(dlsym(mLibHandle, "disp_api_get_pa_config"));
    disp_api_set_pa_config =
            reinterpret_cast<int (*)(int32_t, void*)>(dlsym(mLibHandle, "disp_api_set_pa_config"));
    memset(&mDefaultPictureAdjustment, 0, sizeof(HSIC));
}

bool PictureAdjustment::isSupported() {
    mm_pa_range r{};

    if (disp_api_supported == nullptr || disp_api_supported(0, PICTURE_ADJUSTMENT_FEATURE) == 0) {
        return false;
    }

    if (disp_api_get_pa_range == nullptr || disp_api_get_pa_range(0, &r) != 0) {
        return false;
    }

    return r.max.hue != 0 && r.min.hue != 0 && r.max.saturation != 0.f && r.min.saturation != 0.f &&
           r.max.intensity != 0.f && r.min.intensity != 0.f && r.max.contrast != 0.f &&
           r.min.contrast != 0.f;
}

HSIC PictureAdjustment::getPictureAdjustmentInternal() {
    mm_pa_config config{};

    if (disp_api_get_pa_config != nullptr) {
        if (disp_api_get_pa_config(0, &config) == 0) {
            return HSIC{static_cast<float>(config.data.hue),
                        static_cast<float>(config.data.saturation),
                        static_cast<float>(config.data.intensity),
                        static_cast<float>(config.data.contrast),
                        static_cast<float>(config.data.saturationThreshold)};
        }
    }

    return HSIC{};
}

void PictureAdjustment::updateDefaultPictureAdjustment() {
    if (sInstance != nullptr) {
        sInstance->mDefaultPictureAdjustment = sInstance->getPictureAdjustmentInternal();
    }
}

// Methods from ::vendor::lineage::livedisplay::V2_0::IPictureAdjustment follow.
Return<void> PictureAdjustment::getHueRange(getHueRange_cb _hidl_cb) {
    FloatRange range{};
    mm_pa_range r{};

    if (disp_api_get_pa_range != nullptr) {
        if (disp_api_get_pa_range(0, &r) == 0) {
            range.max = r.max.hue;
            range.min = r.min.hue;
            range.step = 1;
        }
    }

    _hidl_cb(range);
    return Void();
}

Return<void> PictureAdjustment::getSaturationRange(getSaturationRange_cb _hidl_cb) {
    FloatRange range{};
    mm_pa_range r{};

    if (disp_api_get_pa_range != nullptr) {
        if (disp_api_get_pa_range(0, &r) == 0) {
            range.max = r.max.saturation;
            range.min = r.min.saturation;
            range.step = 1;
        }
    }

    _hidl_cb(range);
    return Void();
}

Return<void> PictureAdjustment::getIntensityRange(getIntensityRange_cb _hidl_cb) {
    FloatRange range{};
    mm_pa_range r{};

    if (disp_api_get_pa_range != nullptr) {
        if (disp_api_get_pa_range(0, &r) == 0) {
            range.max = r.max.intensity;
            range.min = r.min.intensity;
            range.step = 1;
        }
    }

    _hidl_cb(range);
    return Void();
}

Return<void> PictureAdjustment::getContrastRange(getContrastRange_cb _hidl_cb) {
    FloatRange range{};
    mm_pa_range r{};

    if (disp_api_get_pa_range != nullptr) {
        if (disp_api_get_pa_range(0, &r) == 0) {
            range.max = r.max.contrast;
            range.min = r.min.contrast;
            range.step = 1;
        }
    }

    _hidl_cb(range);
    return Void();
}

Return<void> PictureAdjustment::getSaturationThresholdRange(
        getSaturationThresholdRange_cb _hidl_cb) {
    FloatRange range{};
    mm_pa_range r{};

    if (disp_api_get_pa_range != nullptr) {
        if (disp_api_get_pa_range(0, &r) == 0) {
            range.max = r.max.saturationThreshold;
            range.min = r.min.saturationThreshold;
            range.step = 1;
        }
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
    _hidl_cb(mDefaultPictureAdjustment);
    return Void();
}

Return<bool> PictureAdjustment::setPictureAdjustment(
        const ::vendor::lineage::livedisplay::V2_0::HSIC& hsic) {
    mm_pa_config config = {0xF,
                           {static_cast<int>(hsic.hue), static_cast<int>(hsic.saturation),
                            static_cast<int>(hsic.intensity), static_cast<int>(hsic.contrast),
                            static_cast<int>(hsic.saturationThreshold)}};

    if (disp_api_set_pa_config != nullptr) {
        return disp_api_set_pa_config(0, &config) == 0;
    }

    return false;
}

}  // namespace legacymm
}  // namespace V2_0
}  // namespace livedisplay
}  // namespace lineage
}  // namespace vendor
