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

#ifndef CPP_VHAL_CLIENT_INCLUDE_IHALPROPVALUE_H_
#define CPP_VHAL_CLIENT_INCLUDE_IHALPROPVALUE_H_

#include <aidl/android/hardware/automotive/vehicle/VehiclePropValue.h>
#include <aidl/android/hardware/automotive/vehicle/VehiclePropertyStatus.h>
#include <android/hardware/automotive/vehicle/2.0/IVehicle.h>

#include <cstdint>
#include <vector>

namespace android {
namespace frameworks {
namespace automotive {
namespace vhal {

class IHalPropValue {
public:
    virtual int32_t getPropId() const = 0;

    virtual int32_t getAreaId() const = 0;

    virtual int64_t getTimestamp() const = 0;

    virtual aidl::android::hardware::automotive::vehicle::VehiclePropertyStatus getStatus()
            const = 0;

    virtual void setInt32Values(const std::vector<int32_t>& values) = 0;

    virtual std::vector<int32_t> getInt32Values() const = 0;

    virtual void setInt64Values(const std::vector<int64_t>& values) = 0;

    virtual std::vector<int64_t> getInt64Values() const = 0;

    virtual void setFloatValues(const std::vector<float>& values) = 0;

    virtual std::vector<float> getFloatValues() const = 0;

    virtual void setByteValues(const std::vector<uint8_t>& values) = 0;

    virtual std::vector<uint8_t> getByteValues() const = 0;

    virtual void setStringValue(const std::string& value) = 0;

    virtual std::string getStringValue() const = 0;

    virtual const void* toVehiclePropValue() const = 0;

    virtual ~IHalPropValue() = default;

    IHalPropValue() = default;

    // Delete copy constructor.
    IHalPropValue(const IHalPropValue& other) = delete;

    // Clone the object. Need to return a unique_ptr since IHalPropValue is an abstract class.
    virtual std::unique_ptr<IHalPropValue> clone() const = 0;
};

}  // namespace vhal
}  // namespace automotive
}  // namespace frameworks
}  // namespace android

#endif  // CPP_VHAL_CLIENT_INCLUDE_IHALPROPVALUE_H_
