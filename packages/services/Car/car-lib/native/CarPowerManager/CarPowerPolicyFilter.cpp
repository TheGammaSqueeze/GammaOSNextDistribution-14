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

#include "CarPowerPolicyFilter.h"

#include <binder/Parcel.h>

namespace android {
namespace car {
namespace hardware {
namespace power {

using android::Parcel;
using android::status_t;

CarPowerPolicyFilter::CarPowerPolicyFilter(const std::vector<int32_t>& components) :
      mComponents(components) {}

status_t CarPowerPolicyFilter::writeToParcel(Parcel* parcel) const {
    return parcel->writeInt32Vector(mComponents);
}

status_t CarPowerPolicyFilter::readFromParcel(const Parcel* parcel) {
    return parcel->readInt32Vector(&mComponents);
}

}  // namespace power
}  // namespace hardware
}  // namespace car
}  // namespace android
