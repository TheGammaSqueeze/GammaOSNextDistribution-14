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

#define LOG_TAG "SampleDriverAidlLimited"

#include <aidl/android/hardware/neuralnetworks/BnDevice.h>
#include <aidl/android/hardware/neuralnetworks/IDevice.h>
#include <android-base/logging.h>
#include <android/binder_interface_utils.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <nnapi/Types.h>
#include <nnapi/hal/aidl/Adapter.h>

#include <algorithm>
#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "CanonicalDevice.h"
#include "LimitedSupportDevice.h"

namespace android::nn::sample {
namespace {

using AidlBnDevice = ::aidl::android::hardware::neuralnetworks::BnDevice;
using AidlIDevice = ::aidl::android::hardware::neuralnetworks::IDevice;
using ::aidl::android::hardware::neuralnetworks::adapter::adapt;

int main() {
    constexpr size_t kNumberOfThreads = 4;
    ABinderProcess_setThreadPoolMaxThreadCount(kNumberOfThreads);

    // Get the canonical interface objects. When developing the SL, you may want to make this
    // "getDevices" instead.
    const auto devices = getExampleLimitedDevices();

    // Adapt all canonical interface objects to AIDL interface objects.
    std::vector<std::shared_ptr<AidlBnDevice>> aidlDevices;
    aidlDevices.reserve(devices.size());
    std::transform(devices.begin(), devices.end(), std::back_inserter(aidlDevices),
                   [](const auto& device) { return adapt(device); });

    // Register all AIDL interface objects.
    CHECK_EQ(devices.size(), aidlDevices.size());
    for (size_t i = 0; i < aidlDevices.size(); ++i) {
        const std::string name = devices[i]->getName();
        if (name != "nnapi-sample_quant") {
            continue;
        }

        const std::string fqName = std::string(AidlIDevice::descriptor) + "/" + name;
        const binder_status_t status =
                AServiceManager_addService(aidlDevices[i]->asBinder().get(), fqName.c_str());
        if (status != STATUS_OK) {
            LOG(ERROR) << "Could not register service " << name;
            return 1;
        }
    }

    ABinderProcess_joinThreadPool();
    LOG(ERROR) << "Service exited!";
    return 1;
}

}  // namespace
}  // namespace android::nn::sample

int main() {
    return android::nn::sample::main();
}
