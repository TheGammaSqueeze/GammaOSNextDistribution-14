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

#include "HidlHalPropValue.h"

namespace android {
namespace frameworks {
namespace automotive {
namespace vhal {

using ::aidl::android::hardware::automotive::vehicle::VehiclePropertyStatus;
using ::android::hardware::automotive::vehicle::V2_0::VehiclePropValue;

HidlHalPropValue::HidlHalPropValue(int32_t propId) {
    mPropValue = {};
    mPropValue.prop = propId;
}

HidlHalPropValue::HidlHalPropValue(int32_t propId, int32_t areaId) {
    mPropValue = {};
    mPropValue.prop = propId;
    mPropValue.areaId = areaId;
}

HidlHalPropValue::HidlHalPropValue(VehiclePropValue&& value) {
    mPropValue = std::move(value);
}

int32_t HidlHalPropValue::getPropId() const {
    return mPropValue.prop;
}

int32_t HidlHalPropValue::getAreaId() const {
    return mPropValue.areaId;
}

int64_t HidlHalPropValue::getTimestamp() const {
    return mPropValue.timestamp;
}

VehiclePropertyStatus HidlHalPropValue::getStatus() const {
    return static_cast<VehiclePropertyStatus>(mPropValue.status);
}

void HidlHalPropValue::setInt32Values(const std::vector<int32_t>& values) {
    mPropValue.value.int32Values = values;
}

std::vector<int32_t> HidlHalPropValue::getInt32Values() const {
    return mPropValue.value.int32Values;
}

void HidlHalPropValue::setInt64Values(const std::vector<int64_t>& values) {
    mPropValue.value.int64Values = values;
}

std::vector<int64_t> HidlHalPropValue::getInt64Values() const {
    return mPropValue.value.int64Values;
}

void HidlHalPropValue::setFloatValues(const std::vector<float>& values) {
    mPropValue.value.floatValues = values;
}

std::vector<float> HidlHalPropValue::getFloatValues() const {
    return mPropValue.value.floatValues;
}

void HidlHalPropValue::setByteValues(const std::vector<uint8_t>& values) {
    mPropValue.value.bytes = values;
}

std::vector<uint8_t> HidlHalPropValue::getByteValues() const {
    return mPropValue.value.bytes;
}

void HidlHalPropValue::setStringValue(const std::string& value) {
    mPropValue.value.stringValue = value;
}

std::string HidlHalPropValue::getStringValue() const {
    return mPropValue.value.stringValue;
}

const void* HidlHalPropValue::toVehiclePropValue() const {
    return &mPropValue;
}

std::unique_ptr<IHalPropValue> HidlHalPropValue::clone() const {
    auto propValueCopy = mPropValue;
    return std::make_unique<HidlHalPropValue>(std::move(propValueCopy));
}

}  // namespace vhal
}  // namespace automotive
}  // namespace frameworks
}  // namespace android
