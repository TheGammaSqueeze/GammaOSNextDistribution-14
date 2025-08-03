/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include <vector>
#include "GnssVisibilityControl.h"

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
namespace implementation {

ndk::ScopedAStatus GnssVisibilityControl::enableNfwLocationAccess(
        const std::vector<std::string>& /*hostname*/) {
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus GnssVisibilityControl::setCallback(
        const std::shared_ptr<IGnssVisibilityControlCallback>& /*callback*/) {
    return ndk::ScopedAStatus::ok();
}

}  // namespace implementation
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
