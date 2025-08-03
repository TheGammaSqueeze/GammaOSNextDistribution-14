/*
 * Copyright (C) 2017 The Android Open Source Project
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

#define LOG_TAG "SampleDriverLimited"

#include <android-base/logging.h>
#include <android/hardware/neuralnetworks/1.3/IDevice.h>
#include <hidl/HidlTransportSupport.h>
#include <nnapi/Types.h>
#include <nnapi/hal/Adapter.h>

#include <algorithm>
#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "CanonicalDevice.h"
#include "LimitedSupportDevice.h"

namespace android::nn::sample {
namespace {

int main() {
    constexpr size_t kNumberOfThreads = 4;
    hardware::configureRpcThreadpool(kNumberOfThreads, true);

    // Get the canonical interface objects. When developing the SL, you may want to make this
    // "getDevices" instead.
    const auto devices = getExampleLimitedDevices();

    // Adapt all canonical interface objects to HIDL interface objects.
    std::vector<sp<hardware::neuralnetworks::V1_3::IDevice>> hidlDevices;
    hidlDevices.reserve(devices.size());
    std::transform(
            devices.begin(), devices.end(), std::back_inserter(hidlDevices),
            [](const auto& device) { return hardware::neuralnetworks::adapter::adapt(device); });

    // Register all HIDL interface objects.
    CHECK_EQ(devices.size(), hidlDevices.size());
    for (size_t i = 0; i < hidlDevices.size(); ++i) {
        if (hidlDevices[i]->registerAsService(devices[i]->getName()) != android::OK) {
            LOG(ERROR) << "Could not register service " << devices[i]->getName();
            return 1;
        }
    }

    hardware::joinRpcThreadpool();
    LOG(ERROR) << "Service exited!";
    return 1;
}

}  // namespace
}  // namespace android::nn::sample

int main() {
    return android::nn::sample::main();
}
