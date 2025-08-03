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

#include "CarPowerPolicy.h"

#include <binder/Parcel.h>

namespace android {
namespace car {
namespace hardware {
namespace power {

using android::Parcel;
using android::status_t;

CarPowerPolicy::CarPowerPolicy(const std::string& policyId,
                               const std::vector<int32_t>& enabledComponents,
                               const std::vector<int32_t>& disabledComponents) :
      mPolicyId(policyId.c_str(), policyId.length()),
      mEnabledComponents(enabledComponents),
      mDisabledComponents(disabledComponents) {}

status_t CarPowerPolicy::writeToParcel(Parcel* parcel) const {
    status_t status = parcel->writeString8(mPolicyId);
    if (status != OK) {
        return status;
    }
    status = parcel->writeInt32Vector(mEnabledComponents);
    if (status != OK) {
        return status;
    }
    return parcel->writeInt32Vector(mDisabledComponents);
}

status_t CarPowerPolicy::readFromParcel(const Parcel* parcel) {
    status_t status = parcel->readString8(&mPolicyId);
    if (status != OK) {
        return status;
    }
    status = parcel->readInt32Vector(&mEnabledComponents);
    if (status != OK) {
        return status;
    }
    return parcel->readInt32Vector(&mDisabledComponents);
}

}  // namespace power
}  // namespace hardware
}  // namespace car
}  // namespace android
