/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "AgnssRil.h"

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
namespace implementation {

ndk::ScopedAStatus AGnssRil::setCallback(const std::shared_ptr<IAGnssRilCallback>& /*callback*/) {
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus AGnssRil::setRefLocation(const AGnssRefLocation& /*agnssReflocation*/) {
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus AGnssRil::setSetId(SetIdType /*type*/, const std::string& /*setid*/) {
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus AGnssRil::updateNetworkState(const NetworkAttributes& /*attributes*/) {
    return ndk::ScopedAStatus::ok();
}

}  // namespace implementation
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace
