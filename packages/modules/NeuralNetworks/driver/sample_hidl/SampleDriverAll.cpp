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

#define LOG_TAG "SampleDriverAll"

#include <hidl/LegacySupport.h>

#include <string>

#include "SampleDriverFull.h"
#include "SampleDriverUtils.h"

using android::sp;
using android::hardware::neuralnetworks::V1_0::PerformanceInfo;
using android::nn::sample_driver::SampleDriverFull;

int main() {
    const std::string name = "nnapi-sample_all";
    const auto perf = PerformanceInfo{.execTime = 1.1f, .powerUsage = 1.1f};
    const auto driver = sp<SampleDriverFull>::make(name.c_str(), perf);
    return run(driver, name);
}
