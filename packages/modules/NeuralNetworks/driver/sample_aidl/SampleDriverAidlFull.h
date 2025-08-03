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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_DRIVER_SAMPLE_AIDL_SAMPLE_DRIVER_AIDL_FULL_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_DRIVER_SAMPLE_AIDL_SAMPLE_DRIVER_AIDL_FULL_H

#include <vector>

#include "SampleDriverAidl.h"

namespace android {
namespace nn {
namespace sample_driver_aidl {

class SampleDriverFull : public SampleDriver {
   public:
    SampleDriverFull(const char* name, aidl_hal::PerformanceInfo perf)
        : SampleDriver(name), mPerf(perf) {}
    ndk::ScopedAStatus getCapabilities(aidl_hal::Capabilities* capabilities) override;
    ndk::ScopedAStatus getSupportedOperations(const aidl_hal::Model& model,
                                              std::vector<bool>* supportedOperations) override;

   private:
    aidl_hal::PerformanceInfo mPerf;
};

}  // namespace sample_driver_aidl
}  // namespace nn
}  // namespace android

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_DRIVER_SAMPLE_AIDL_SAMPLE_DRIVER_AIDL_FULL_H
