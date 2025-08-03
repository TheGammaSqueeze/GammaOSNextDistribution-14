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

#ifndef CAR_LIB_NATIVE_INCLUDE_CARPOWERPOLICY_H_
#define CAR_LIB_NATIVE_INCLUDE_CARPOWERPOLICY_H_

#include <binder/Parcelable.h>

#include <string>
#include <vector>

namespace android {
namespace car {
namespace hardware {
namespace power {

class CarPowerPolicy : public android::Parcelable {
public:
    CarPowerPolicy() = default;
    CarPowerPolicy(const std::string& policyId, const std::vector<int32_t>& enabledComponents,
                   const std::vector<int32_t>& disabledComponents);
    virtual ~CarPowerPolicy() = default;

    android::status_t writeToParcel(android::Parcel* parcel) const override;
    android::status_t readFromParcel(const android::Parcel* parcel) override;

private:
    android::String8 mPolicyId;
    std::vector<int32_t> mEnabledComponents;
    std::vector<int32_t> mDisabledComponents;
};

}  // namespace power
}  // namespace hardware
}  // namespace car
}  // namespace android

#endif  // CAR_LIB_NATIVE_INCLUDE_CARPOWERPOLICY_H_
