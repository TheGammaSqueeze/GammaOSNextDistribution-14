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

#define LOG_TAG "SampleDriverAidlAll"

#include <android/binder_interface_utils.h>

#include <memory>

#include "SampleDriverAidlFull.h"
#include "SampleDriverAidlUtils.h"

using aidl::android::hardware::neuralnetworks::PerformanceInfo;
using android::nn::sample_driver_aidl::SampleDriverFull;

int main() {
    const std::string name = "nnapi-sample_all";
    const PerformanceInfo performance{.execTime = 1.1f, .powerUsage = 1.1f};
    const std::shared_ptr<SampleDriverFull> driver =
            ndk::SharedRefBase::make<SampleDriverFull>(name.c_str(), performance);
    return run(driver, name);
}
