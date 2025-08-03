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

#include <gtest/gtest.h>

#include "NeuralNetworks.h"

class UpdatabilityTest : public ::testing::Test {};

// Keep this value in sync with the corresponding value of kMinFeatureLevelNum in
// runtime/ServerFlag.h.
constexpr int64_t kMinFeatureLevelCode = ANEURALNETWORKS_FEATURE_LEVEL_8;

TEST_F(UpdatabilityTest, GetFeatureLevel) {
    if (__builtin_available(android __NNAPI_FL5_MIN_ANDROID_API__, *)) {
        // Ensure that the feature level returned is never less than what is allowed by the server
        // feature level flag.
        EXPECT_GE(ANeuralNetworks_getRuntimeFeatureLevel(), kMinFeatureLevelCode);
    } else {
        GTEST_SKIP();
    }
}

TEST_F(UpdatabilityTest, DeviceFeatureLevelLowerOrEqualToRuntimeFeatureLevel) {
    if (__builtin_available(android __NNAPI_FL5_MIN_ANDROID_API__, *)) {
        const int64_t runtimeFeatureLevel = ANeuralNetworks_getRuntimeFeatureLevel();
        uint32_t numDevices = 0;
        EXPECT_EQ(ANeuralNetworks_getDeviceCount(&numDevices), ANEURALNETWORKS_NO_ERROR);
        for (uint32_t i = 0; i < numDevices; i++) {
            SCOPED_TRACE(i);
            int64_t featureLevel;
            ANeuralNetworksDevice* device;
            EXPECT_EQ(ANeuralNetworks_getDevice(i, &device), ANEURALNETWORKS_NO_ERROR);
            EXPECT_EQ(ANeuralNetworksDevice_getFeatureLevel(device, &featureLevel),
                      ANEURALNETWORKS_NO_ERROR);

            EXPECT_LE(featureLevel, runtimeFeatureLevel);
        }
    } else {
        GTEST_SKIP();
    }
}
