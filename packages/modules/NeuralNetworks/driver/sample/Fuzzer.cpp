/*
 * Copyright (C) 2022 The Android Open Source Project
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

#define LOG_TAG "Fuzzer"

#include <aidl/android/hardware/neuralnetworks/BnDevice.h>
#include <fuzzbinder/libbinder_ndk_driver.h>
#include <fuzzer/FuzzedDataProvider.h>
#include <nnapi/hal/aidl/Adapter.h>

#include <memory>
#include <string>

#include "CanonicalDevice.h"

namespace aidl::android::hardware::neuralnetworks::fuzzer {
namespace {

std::shared_ptr<BnDevice> makeDevice() {
    const std::string name = "nnapi-sample";
    auto device = std::make_shared<::android::nn::sample::Device>(name);
    return adapter::adapt(std::move(device));
}

void limitLoggingToCrashes() {
    [[maybe_unused]] static const auto oldSeverity = ::android::base::SetMinimumLogSeverity(
            ::android::base::LogSeverity::FATAL_WITHOUT_ABORT);
}

}  // namespace
}  // namespace aidl::android::hardware::neuralnetworks::fuzzer

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
    // Limit NNAPI fuzz test logging to crashes (which is what the test cares about) to reduce the
    // noise and potentially speed up testing.
    aidl::android::hardware::neuralnetworks::fuzzer::limitLoggingToCrashes();

    // Initialize the Device under test when LLVMFuzzerTestOneInput is first called, and reuse it in
    // later calls.
    static const auto device = aidl::android::hardware::neuralnetworks::fuzzer::makeDevice();

    android::fuzzService(device->asBinder().get(), FuzzedDataProvider(data, size));
    return 0;
}
