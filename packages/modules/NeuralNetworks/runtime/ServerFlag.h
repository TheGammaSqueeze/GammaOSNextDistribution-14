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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_SERVER_FLAG_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_SERVER_FLAG_H

#include <nnapi/Types.h>
#include <stdint.h>

#include <functional>
#include <string>

#include "NeuralNetworks.h"

namespace android::nn {

// Keep these values consistent with server side configuration in
// google3/googledata/experiments/mobile/android_platform/nnapi_native/features/
//   feature_level.gcl and telemetry.gcl.
constexpr char kExprCategoryName[] = "nnapi_native";
constexpr char kCurrentFeatureLevelFlagName[] = "current_feature_level";
constexpr char kTelemetryEnableFlagName[] = "telemetry_enable";
constexpr int64_t kDefaultFeatureLevelNum = 8;
// When this value is updated, update kMinFeatureLevelCode in runtime/test/TestUpdatability.cpp with
// the corresponding ANEURALNETWORKS_FEATURE_LEVEL_* version.
constexpr int64_t kMinFeatureLevelNum = 8;
constexpr int64_t kMaxFeatureLevelNum = 8;
constexpr bool kDefaultTelemetryEnableValue = false;

#ifndef NN_COMPATIBILITY_LIBRARY_BUILD
#ifndef NN_EXPERIMENTAL_FEATURE
// Function to get server feature level flag. Note that this function should NOT be used directly.
// Instead, clients are expected to use DeviceManager::getRuntimeVersion or
// DeviceManager::getRuntimeFeatureLevel in runtime/Manager.h.
int64_t getServerFeatureLevelFlag();

// Function to get server telemetry enable flag. Note that this function should NOT be used
// directly. Instead, clients are expected to use DeviceManager::isPlatformTelemetryEnabled in
// runtime/Manager.h.
bool getServerTelemetryEnableFlag();
#endif  // NN_EXPERIMENTAL_FEATURE

// Testing-only.
using GetServerConfigurableFlagFunc =
        std::function<std::string(const std::string&, const std::string&, const std::string&)>;
int64_t getServerFeatureLevelFlag(GetServerConfigurableFlagFunc serverFunc);
bool getServerTelemetryEnableFlag(GetServerConfigurableFlagFunc serverFunc);
#endif  // NN_COMPATIBILITY_LIBRARY_BUILD

// Get the runtime version corresponding to the server feature flag value.
Version serverFeatureLevelToVersion(int64_t serverFeatureLevel);

}  // namespace android::nn

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_SERVER_FLAG_H
