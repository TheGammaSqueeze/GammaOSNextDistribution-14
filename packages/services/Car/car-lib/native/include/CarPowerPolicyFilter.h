/*
 * Copyright (C) 2020 The Android Open Source Project
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

#ifndef CAR_LIB_NATIVE_INCLUDE_CARPOWERPOLICYFILTER_H_
#define CAR_LIB_NATIVE_INCLUDE_CARPOWERPOLICYFILTER_H_

#include <binder/Parcelable.h>

#include <vector>

namespace android {
namespace car {
namespace hardware {
namespace power {

class CarPowerPolicyFilter : public android::Parcelable {
public:
    CarPowerPolicyFilter() = default;
    explicit CarPowerPolicyFilter(const std::vector<int32_t>& components);
    virtual ~CarPowerPolicyFilter() = default;

    android::status_t writeToParcel(android::Parcel* parcel) const override;
    android::status_t readFromParcel(const android::Parcel* parcel) override;

private:
    std::vector<int32_t> mComponents;
};

}  // namespace power
}  // namespace hardware
}  // namespace car
}  // namespace android

#endif  // CAR_LIB_NATIVE_INCLUDE_CARPOWERPOLICYFILTER_H_
