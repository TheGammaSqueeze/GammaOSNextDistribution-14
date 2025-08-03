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

#include "AidlHalPropValue.h"

namespace android {
namespace frameworks {
namespace automotive {
namespace vhal {

using ::aidl::android::hardware::automotive::vehicle::VehiclePropertyStatus;
using ::aidl::android::hardware::automotive::vehicle::VehiclePropValue;

AidlHalPropValue::AidlHalPropValue(int32_t propId) : IHalPropValue() {
    mPropValue = {};
    mPropValue.prop = propId;
}

AidlHalPropValue::AidlHalPropValue(int32_t propId, int32_t areaId) : IHalPropValue() {
    mPropValue = {};
    mPropValue.prop = propId;
    mPropValue.areaId = areaId;
}

AidlHalPropValue::AidlHalPropValue(VehiclePropValue&& value) : IHalPropValue() {
    mPropValue = std::move(value);
}

int32_t AidlHalPropValue::getPropId() const {
    return mPropValue.prop;
}

int32_t AidlHalPropValue::getAreaId() const {
    return mPropValue.areaId;
}

int64_t AidlHalPropValue::getTimestamp() const {
    return mPropValue.timestamp;
}

VehiclePropertyStatus AidlHalPropValue::getStatus() const {
    return mPropValue.status;
}

void AidlHalPropValue::setInt32Values(const std::vector<int32_t>& values) {
    mPropValue.value.int32Values = values;
}

std::vector<int32_t> AidlHalPropValue::getInt32Values() const {
    return mPropValue.value.int32Values;
}

void AidlHalPropValue::setInt64Values(const std::vector<int64_t>& values) {
    mPropValue.value.int64Values = values;
}

std::vector<int64_t> AidlHalPropValue::getInt64Values() const {
    return mPropValue.value.int64Values;
}

void AidlHalPropValue::setFloatValues(const std::vector<float>& values) {
    mPropValue.value.floatValues = values;
}

std::vector<float> AidlHalPropValue::getFloatValues() const {
    return mPropValue.value.floatValues;
}

void AidlHalPropValue::setByteValues(const std::vector<uint8_t>& values) {
    mPropValue.value.byteValues = values;
}

std::vector<uint8_t> AidlHalPropValue::getByteValues() const {
    return mPropValue.value.byteValues;
}

void AidlHalPropValue::setStringValue(const std::string& value) {
    mPropValue.value.stringValue = value;
}

std::string AidlHalPropValue::getStringValue() const {
    return mPropValue.value.stringValue;
}

const void* AidlHalPropValue::toVehiclePropValue() const {
    return &mPropValue;
}

std::unique_ptr<IHalPropValue> AidlHalPropValue::clone() const {
    auto propValueCopy = mPropValue;
    return std::make_unique<AidlHalPropValue>(std::move(propValueCopy));
}

}  // namespace vhal
}  // namespace automotive
}  // namespace frameworks
}  // namespace android
