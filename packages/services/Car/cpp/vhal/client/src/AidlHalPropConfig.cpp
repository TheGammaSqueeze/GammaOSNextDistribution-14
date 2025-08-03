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

#include "AidlHalPropConfig.h"

#include <VehicleUtils.h>

namespace android {
namespace frameworks {
namespace automotive {
namespace vhal {

using ::aidl::android::hardware::automotive::vehicle::VehicleAreaConfig;
using ::aidl::android::hardware::automotive::vehicle::VehiclePropConfig;
using ::aidl::android::hardware::automotive::vehicle::VehiclePropertyAccess;

using ::android::hardware::automotive::vehicle::toInt;

AidlHalPropConfig::AidlHalPropConfig(VehiclePropConfig&& config) {
    mPropConfig = std::move(config);
    for (VehicleAreaConfig& areaConfig : mPropConfig.areaConfigs) {
        int32_t access = (areaConfig.access == VehiclePropertyAccess::NONE)
                ? toInt(mPropConfig.access)
                : toInt(areaConfig.access);
        mAreaConfigs.push_back(std::make_unique<AidlHalAreaConfig>(std::move(areaConfig), access));
    }
}

int32_t AidlHalPropConfig::getPropId() const {
    return mPropConfig.prop;
}

int32_t AidlHalPropConfig::getAccess() const {
    return toInt(mPropConfig.access);
}

int32_t AidlHalPropConfig::getChangeMode() const {
    return toInt(mPropConfig.changeMode);
}

size_t AidlHalPropConfig::getAreaConfigSize() const {
    return mAreaConfigs.size();
}

std::vector<int32_t> AidlHalPropConfig::getConfigArray() const {
    return mPropConfig.configArray;
}

std::string AidlHalPropConfig::getConfigString() const {
    return mPropConfig.configString;
}

float AidlHalPropConfig::getMinSampleRate() const {
    return mPropConfig.minSampleRate;
}

float AidlHalPropConfig::getMaxSampleRate() const {
    return mPropConfig.maxSampleRate;
}

AidlHalAreaConfig::AidlHalAreaConfig(VehicleAreaConfig&& areaConfig, int32_t access) {
    mAreaConfig = std::move(areaConfig);
    mAccess = access;
}

int32_t AidlHalAreaConfig::getAreaId() const {
    return mAreaConfig.areaId;
}

int32_t AidlHalAreaConfig::getAccess() const {
    return mAccess;
}

int32_t AidlHalAreaConfig::getMinInt32Value() const {
    return mAreaConfig.minInt32Value;
}

int32_t AidlHalAreaConfig::getMaxInt32Value() const {
    return mAreaConfig.maxInt32Value;
}

int64_t AidlHalAreaConfig::getMinInt64Value() const {
    return mAreaConfig.minInt64Value;
}

int64_t AidlHalAreaConfig::getMaxInt64Value() const {
    return mAreaConfig.maxInt64Value;
}

float AidlHalAreaConfig::getMinFloatValue() const {
    return mAreaConfig.minFloatValue;
}

float AidlHalAreaConfig::getMaxFloatValue() const {
    return mAreaConfig.maxFloatValue;
}

bool AidlHalAreaConfig::isVariableUpdateRateSupported() const {
    return mAreaConfig.supportVariableUpdateRate;
}

}  // namespace vhal
}  // namespace automotive
}  // namespace frameworks
}  // namespace android
