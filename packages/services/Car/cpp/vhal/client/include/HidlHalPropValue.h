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

#ifndef CPP_VHAL_CLIENT_INCLUDE_HIDLHALPROPVALUE_H_
#define CPP_VHAL_CLIENT_INCLUDE_HIDLHALPROPVALUE_H_

#include "IHalPropValue.h"

#include <android/hardware/automotive/vehicle/2.0/IVehicle.h>

namespace android {
namespace frameworks {
namespace automotive {
namespace vhal {

class HidlHalPropValue : public IHalPropValue {
public:
    explicit HidlHalPropValue(int32_t propId);
    explicit HidlHalPropValue(
            ::android::hardware::automotive::vehicle::V2_0::VehiclePropValue&& value);
    HidlHalPropValue(int32_t propId, int32_t areaId);

    int32_t getPropId() const override;

    int32_t getAreaId() const override;

    int64_t getTimestamp() const override;

    aidl::android::hardware::automotive::vehicle::VehiclePropertyStatus getStatus() const override;

    void setInt32Values(const std::vector<int32_t>& values) override;

    std::vector<int32_t> getInt32Values() const override;

    void setInt64Values(const std::vector<int64_t>& values) override;

    std::vector<int64_t> getInt64Values() const override;

    void setFloatValues(const std::vector<float>& values) override;

    std::vector<float> getFloatValues() const override;

    void setByteValues(const std::vector<uint8_t>& values) override;

    std::vector<uint8_t> getByteValues() const override;

    void setStringValue(const std::string& value) override;

    std::string getStringValue() const override;

    const void* toVehiclePropValue() const override;

    std::unique_ptr<IHalPropValue> clone() const override;

private:
    ::android::hardware::automotive::vehicle::V2_0::VehiclePropValue mPropValue;
};

}  // namespace vhal
}  // namespace automotive
}  // namespace frameworks
}  // namespace android

#endif  // CPP_VHAL_CLIENT_INCLUDE_HIDLHALPROPVALUE_H_
