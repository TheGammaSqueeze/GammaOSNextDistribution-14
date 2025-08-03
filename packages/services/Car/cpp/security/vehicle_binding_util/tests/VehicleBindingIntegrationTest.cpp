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

#include <android-base/properties.h>
#include <gtest/gtest.h>

#include <IVhalClient.h>
#include <VehicleHalTypes.h>

namespace android {
namespace automotive {
namespace security {
namespace {

using ::aidl::android::hardware::automotive::vehicle::StatusCode;
using ::aidl::android::hardware::automotive::vehicle::VehiclePropConfig;
using ::aidl::android::hardware::automotive::vehicle::VehicleProperty;
using ::android::frameworks::automotive::vhal::IVhalClient;

bool isSeedVhalPropertySupported(std::shared_ptr<IVhalClient> vehicle) {
    std::vector<int32_t> props = {
            static_cast<int32_t>(VehicleProperty::STORAGE_ENCRYPTION_BINDING_SEED)};

    auto result = vehicle->getPropConfigs(props);
    return result.ok() && result.value().size() != 0;
}

// Verify that vold got the binding seed if VHAL reports a seed
TEST(VehicleBindingIntegrationTedt, TestVehicleBindingSeedSet) {
    std::string expected_value = "1";
    auto client = IVhalClient::create();
    if (!isSeedVhalPropertySupported(client)) {
        GTEST_LOG_(INFO) << "Device does not support vehicle binding seed "
                            "(STORAGE_ENCRYPTION_BINDING_SEED).";
        expected_value = "";
    }

    ASSERT_EQ(expected_value, android::base::GetProperty("vold.storage_seed_bound", ""));
}

}  // namespace
}  // namespace security
}  // namespace automotive
}  // namespace android
