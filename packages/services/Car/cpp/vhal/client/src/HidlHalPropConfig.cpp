/*
 * Copyright (c) 2022, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "HidlHalPropConfig.h"

#include <VehicleUtils.h>

namespace android {
namespace frameworks {
namespace automotive {
namespace vhal {

using ::android::hardware::automotive::vehicle::V2_0::VehicleAreaConfig;
using ::android::hardware::automotive::vehicle::V2_0::VehiclePropConfig;

using ::android::hardware::automotive::vehicle::toInt;

HidlHalPropConfig::HidlHalPropConfig(VehiclePropConfig&& config) {
    mPropConfig = std::move(config);
    for (VehicleAreaConfig& areaConfig : mPropConfig.areaConfigs) {
        mAreaConfigs.push_back(std::make_unique<HidlHalAreaConfig>(std::move(areaConfig),
                                                                   toInt(mPropConfig.access)));
    }
}

int32_t HidlHalPropConfig::getPropId() const {
    return mPropConfig.prop;
}

int32_t HidlHalPropConfig::getAccess() const {
    return toInt(mPropConfig.access);
}

int32_t HidlHalPropConfig::getChangeMode() const {
    return toInt(mPropConfig.changeMode);
}

size_t HidlHalPropConfig::getAreaConfigSize() const {
    return mAreaConfigs.size();
}

std::vector<int32_t> HidlHalPropConfig::getConfigArray() const {
    return mPropConfig.configArray;
}

std::string HidlHalPropConfig::getConfigString() const {
    return mPropConfig.configString;
}

float HidlHalPropConfig::getMinSampleRate() const {
    return mPropConfig.minSampleRate;
}

float HidlHalPropConfig::getMaxSampleRate() const {
    return mPropConfig.maxSampleRate;
}

HidlHalAreaConfig::HidlHalAreaConfig(VehicleAreaConfig&& areaConfig, int32_t access) {
    mAreaConfig = std::move(areaConfig);
    mAccess = access;
}

int32_t HidlHalAreaConfig::getAreaId() const {
    return mAreaConfig.areaId;
}

int32_t HidlHalAreaConfig::getAccess() const {
    return mAccess;
}

int32_t HidlHalAreaConfig::getMinInt32Value() const {
    return mAreaConfig.minInt32Value;
}

int32_t HidlHalAreaConfig::getMaxInt32Value() const {
    return mAreaConfig.maxInt32Value;
}

int64_t HidlHalAreaConfig::getMinInt64Value() const {
    return mAreaConfig.minInt64Value;
}

int64_t HidlHalAreaConfig::getMaxInt64Value() const {
    return mAreaConfig.maxInt64Value;
}

float HidlHalAreaConfig::getMinFloatValue() const {
    return mAreaConfig.minFloatValue;
}

float HidlHalAreaConfig::getMaxFloatValue() const {
    return mAreaConfig.maxFloatValue;
}

// HIDL VHAL does not support VUR.
bool HidlHalAreaConfig::isVariableUpdateRateSupported() const {
    return false;
}

}  // namespace vhal
}  // namespace automotive
}  // namespace frameworks
}  // namespace android
