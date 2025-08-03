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

#include <gtest/gtest.h>
#include <nnapi/TypeUtils.h>
#include <nnapi/Types.h>

#include "ServerFlag.h"

using android::nn::GetServerConfigurableFlagFunc;
using android::nn::getServerFeatureLevelFlag;
using android::nn::getServerTelemetryEnableFlag;
using android::nn::kDefaultFeatureLevelNum;
using android::nn::kDefaultTelemetryEnableValue;
using android::nn::kMaxFeatureLevelNum;
using android::nn::kMinFeatureLevelNum;
using android::nn::kVersionFeatureLevel5;
using android::nn::kVersionFeatureLevel6;
using android::nn::kVersionFeatureLevel7;
using android::nn::kVersionFeatureLevel8;
using android::nn::serverFeatureLevelToVersion;

static std::string fakeServerFuncDefault(const std::string& /*categoryName*/,
                                         const std::string& /*flagName*/,
                                         const std::string& /*defaultValue*/) {
    return std::to_string(kDefaultFeatureLevelNum);
}

static std::string fakeServerFuncMax(const std::string& /*categoryName*/,
                                     const std::string& /*flagName*/,
                                     const std::string& /*defaultValue*/) {
    return std::to_string(kMaxFeatureLevelNum);
}

static std::string fakeServerFuncMin(const std::string& /*categoryName*/,
                                     const std::string& /*flagName*/,
                                     const std::string& /*defaultValue*/) {
    return std::to_string(kMinFeatureLevelNum);
}

static std::string fakeServerFuncLarge(const std::string& /*categoryName*/,
                                       const std::string& /*flagName*/,
                                       const std::string& /*defaultValue*/) {
    return std::to_string(kMaxFeatureLevelNum + 1);
}

static std::string fakeServerFuncSmall(const std::string& /*categoryName*/,
                                       const std::string& /*flagName*/,
                                       const std::string& /*defaultValue*/) {
    return std::to_string(kMinFeatureLevelNum - 1);
}

static std::string fakeServerFuncNull(const std::string& /*categoryName*/,
                                      const std::string& /*flagName*/,
                                      const std::string& /*defaultValue*/) {
    return "null";
}

static std::string fakeServerTelemetryFuncDefault(const std::string& /*categoryName*/,
                                                  const std::string& /*flagName*/,
                                                  const std::string& /*defaultValue*/) {
    return std::to_string(kDefaultTelemetryEnableValue);
}

static std::string fakeServerTelemetryFuncInvalid(const std::string& /*categoryName*/,
                                                  const std::string& /*flagName*/,
                                                  const std::string& /*defaultValue*/) {
    return "not_a_bool";
}

static std::string fakeServerTelemetryFuncNull(const std::string& /*categoryName*/,
                                               const std::string& /*flagName*/,
                                               const std::string& /*defaultValue*/) {
    return "null";
}

TEST(ServerFlagTest, ServerFeatureLevelFlag) {
    // Tests android::nn::getServerFeatureLevelFlag directly because
    // feature level is stored as static variable in runtime so that the value does not change if
    // uses client APIs.

    // Tests correct value is returned if the flag is set legally.
    EXPECT_EQ(getServerFeatureLevelFlag(fakeServerFuncDefault), kDefaultFeatureLevelNum);
    EXPECT_EQ(getServerFeatureLevelFlag(fakeServerFuncMax), kMaxFeatureLevelNum);
    EXPECT_EQ(getServerFeatureLevelFlag(fakeServerFuncMin), kMinFeatureLevelNum);

    // Tests default value is returned if the flag is unset or illegal.
    EXPECT_EQ(getServerFeatureLevelFlag(fakeServerFuncLarge), kDefaultFeatureLevelNum);
    EXPECT_EQ(getServerFeatureLevelFlag(fakeServerFuncSmall), kDefaultFeatureLevelNum);
    EXPECT_EQ(getServerFeatureLevelFlag(fakeServerFuncNull), kDefaultFeatureLevelNum);
}

TEST(ServerFlagTest, ServerFeatureLevelToVersion) {
    EXPECT_EQ(serverFeatureLevelToVersion(5), kVersionFeatureLevel5);
    EXPECT_EQ(serverFeatureLevelToVersion(6), kVersionFeatureLevel6);
    EXPECT_EQ(serverFeatureLevelToVersion(7), kVersionFeatureLevel7);
    EXPECT_EQ(serverFeatureLevelToVersion(8), kVersionFeatureLevel8);

    EXPECT_EQ(serverFeatureLevelToVersion(kMinFeatureLevelNum), kVersionFeatureLevel8);
    EXPECT_EQ(serverFeatureLevelToVersion(kDefaultFeatureLevelNum), kVersionFeatureLevel8);
    EXPECT_EQ(serverFeatureLevelToVersion(kMaxFeatureLevelNum), kVersionFeatureLevel8);
}

static GetServerConfigurableFlagFunc makeFuncWithReturn(std::string ret) {
    return [ret = std::move(ret)](const std::string&, const std::string&,
                                  const std::string&) -> std::string { return ret; };
}

TEST(ServerFlagTest, ServerTelemetryEnableFlag) {
    // Tests android::nn::getServerTelemetryEnableFlag directly because whether or not telemetry is
    // enabled is stored as static variable in runtime so that the value does not change if uses
    // client APIs.

    // Tests correct value is returned if the flag is set legally.
    EXPECT_EQ(getServerTelemetryEnableFlag(fakeServerTelemetryFuncDefault),
              kDefaultTelemetryEnableValue);

    const std::vector<std::string> kPossibleTrueStrings = {"1", "on", "true", "y", "yes"};
    for (const auto& trueString : kPossibleTrueStrings) {
        GetServerConfigurableFlagFunc fn = makeFuncWithReturn(trueString);
        EXPECT_EQ(getServerTelemetryEnableFlag(fn), true);
    }

    const std::vector<std::string> kPossibleFalseStrings = {"0", "false", "n", "no", "off"};
    for (const auto& falseString : kPossibleFalseStrings) {
        GetServerConfigurableFlagFunc fn = makeFuncWithReturn(falseString);
        EXPECT_EQ(getServerTelemetryEnableFlag(fn), false);
    }

    // Tests default value is returned if the flag is unset or illegal.
    EXPECT_EQ(getServerTelemetryEnableFlag(fakeServerTelemetryFuncInvalid),
              kDefaultTelemetryEnableValue);
    EXPECT_EQ(getServerTelemetryEnableFlag(fakeServerTelemetryFuncNull),
              kDefaultTelemetryEnableValue);
}
