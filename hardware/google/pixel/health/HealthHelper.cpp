/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include <android-base/logging.h>
#include <android/hardware/health/translate-ndk.h>
#include <hal_conversion.h>
#include <pixelhealth/HealthHelper.h>

using HidlHealthInfo = android::hardware::health::V2_0::HealthInfo;
using aidl::android::hardware::health::HealthInfo;
using android::h2a::translate;
using android::hardware::health::V1_0::hal_conversion::convertToHealthInfo;

namespace hardware {
namespace google {
namespace pixel {
namespace health {

HealthInfo ToHealthInfo(const struct android::BatteryProperties *props) {
    HidlHealthInfo hidl_health_info;
    convertToHealthInfo(props, hidl_health_info.legacy);
    HealthInfo aidl_health_info;
    CHECK(translate(hidl_health_info, &aidl_health_info));
    return aidl_health_info;
}

}  // namespace health
}  // namespace pixel
}  // namespace google
}  // namespace hardware
