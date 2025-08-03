// Copyright (C) 2021 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include "src/flags/FlagProvider.h"

#include <gtest/gtest.h>

#include "tests/statsd_test_util.h"

namespace android {
namespace os {
namespace statsd {

#ifdef __ANDROID__

using namespace std;

const string TEST_FLAG = "MyFlagTest";

struct FlagParam {
    string flagValue;
    string label;
};

class FlagProviderTest_SPlus : public testing::TestWithParam<FlagParam> {
    void TearDown() override {
        FlagProvider::getInstance().resetOverrides();
    }
};

INSTANTIATE_TEST_SUITE_P(DefaultValues, FlagProviderTest_SPlus,
                         testing::ValuesIn<FlagParam>({
                                 // Default values.
                                 {FLAG_FALSE, "DefaultFalse"},
                                 {FLAG_TRUE, "DefaultTrue"},
                         }),
                         [](const testing::TestParamInfo<FlagProviderTest_SPlus::ParamType>& info) {
                             return info.param.label;
                         });

TEST_P(FlagProviderTest_SPlus, TestGetFlagBoolServerFlagTrue) {
    FlagProvider::getInstance().overrideFuncs(&isAtLeastSFuncTrue, &getServerFlagFuncTrue);
    EXPECT_TRUE(FlagProvider::getInstance().getFlagBool(TEST_FLAG, GetParam().flagValue));
}

TEST_P(FlagProviderTest_SPlus, TestGetFlagBoolServerFlagFalse) {
    FlagProvider::getInstance().overrideFuncs(&isAtLeastSFuncTrue, &getServerFlagFuncFalse);
    EXPECT_FALSE(FlagProvider::getInstance().getFlagBool(TEST_FLAG, GetParam().flagValue));
}

TEST_P(FlagProviderTest_SPlus, TestOverrideLocalFlags) {
    FlagProvider::getInstance().overrideFuncs(&isAtLeastSFuncTrue);

    FlagProvider::getInstance().overrideFlag(TEST_FLAG, FLAG_FALSE, /*isBootFlag=*/false);
    FlagProvider::getInstance().overrideFlag(TEST_FLAG, FLAG_FALSE, /*isBootFlag=*/true);
    EXPECT_FALSE(FlagProvider::getInstance().getFlagBool(TEST_FLAG, GetParam().flagValue));
    EXPECT_FALSE(FlagProvider::getInstance().getBootFlagBool(TEST_FLAG, GetParam().flagValue));

    FlagProvider::getInstance().overrideFlag(TEST_FLAG, FLAG_TRUE, /*isBootFlag=*/false);
    FlagProvider::getInstance().overrideFlag(TEST_FLAG, FLAG_TRUE, /*isBootFlag=*/true);
    EXPECT_TRUE(FlagProvider::getInstance().getFlagBool(TEST_FLAG, GetParam().flagValue));
    EXPECT_TRUE(FlagProvider::getInstance().getBootFlagBool(TEST_FLAG, GetParam().flagValue));
}

class FlagProviderTest_SPlus_RealValues : public testing::TestWithParam<FlagParam> {
    void SetUp() override {
        if (!isAtLeastS()) {
            GTEST_SKIP() << "Cannot query flags from system property on R-.";
        }
    }

    void TearDown() override {
        writeFlag(TEST_FLAG, FLAG_EMPTY);
        writeBootFlag(TEST_FLAG, FLAG_EMPTY);
        FlagProvider::getInstance().initBootFlags({TEST_FLAG});
    }
};

INSTANTIATE_TEST_SUITE_P(DefaultValues, FlagProviderTest_SPlus_RealValues,
                         testing::ValuesIn<FlagParam>({
                                 // Default values.
                                 {FLAG_FALSE, "DefaultFalse"},
                                 {FLAG_TRUE, "DefaultTrue"},
                         }),
                         [](const testing::TestParamInfo<FlagProviderTest_SPlus::ParamType>& info) {
                             return info.param.label;
                         });

TEST_P(FlagProviderTest_SPlus_RealValues, TestGetFlagBoolServerFlagTrue) {
    writeFlag(TEST_FLAG, FLAG_TRUE);
    EXPECT_TRUE(FlagProvider::getInstance().getFlagBool(TEST_FLAG, GetParam().flagValue));
}

TEST_P(FlagProviderTest_SPlus_RealValues, TestGetFlagBoolServerFlagFalse) {
    writeFlag(TEST_FLAG, FLAG_FALSE);
    EXPECT_FALSE(FlagProvider::getInstance().getFlagBool(TEST_FLAG, GetParam().flagValue));
}

TEST_F(FlagProviderTest_SPlus_RealValues, TestGetFlagBoolServerFlagEmptyDefaultFalse) {
    writeFlag(TEST_FLAG, FLAG_EMPTY);
    EXPECT_FALSE(FlagProvider::getInstance().getFlagBool(TEST_FLAG, FLAG_FALSE));
}

TEST_F(FlagProviderTest_SPlus_RealValues, TestGetFlagBoolServerFlagEmptyDefaultTrue) {
    writeFlag(TEST_FLAG, FLAG_EMPTY);
    EXPECT_TRUE(FlagProvider::getInstance().getFlagBool(TEST_FLAG, FLAG_TRUE));
}

TEST_P(FlagProviderTest_SPlus_RealValues, TestGetBootFlagBoolServerFlagTrue) {
    writeBootFlag(TEST_FLAG, FLAG_TRUE);
    FlagProvider::getInstance().initBootFlags({TEST_FLAG});
    EXPECT_TRUE(FlagProvider::getInstance().getBootFlagBool(TEST_FLAG, GetParam().flagValue));
}

TEST_P(FlagProviderTest_SPlus_RealValues, TestGetBootFlagBoolServerFlagFalse) {
    writeBootFlag(TEST_FLAG, FLAG_FALSE);
    FlagProvider::getInstance().initBootFlags({TEST_FLAG});
    EXPECT_FALSE(FlagProvider::getInstance().getBootFlagBool(TEST_FLAG, GetParam().flagValue));
}

TEST_P(FlagProviderTest_SPlus_RealValues, TestGetBootFlagBoolServerFlagUpdated) {
    writeBootFlag(TEST_FLAG, FLAG_FALSE);
    FlagProvider::getInstance().initBootFlags({TEST_FLAG});
    writeBootFlag(TEST_FLAG, FLAG_TRUE);
    EXPECT_FALSE(FlagProvider::getInstance().getBootFlagBool(TEST_FLAG, GetParam().flagValue));
}

TEST_F(FlagProviderTest_SPlus_RealValues, TestGetFlagBoolNoInitServerFlagEmptyDefaultFalse) {
    writeBootFlag(TEST_FLAG, FLAG_EMPTY);
    EXPECT_FALSE(FlagProvider::getInstance().getFlagBool(TEST_FLAG, FLAG_FALSE));
}

TEST_F(FlagProviderTest_SPlus_RealValues, TestGetFlagBoolNoInitServerFlagEmptyDefaultTrue) {
    writeBootFlag(TEST_FLAG, FLAG_EMPTY);
    EXPECT_TRUE(FlagProvider::getInstance().getFlagBool(TEST_FLAG, FLAG_TRUE));
}

class FlagProviderTest_RMinus : public testing::TestWithParam<FlagParam> {
    void SetUp() override {
        writeFlag(TEST_FLAG, GetParam().flagValue);
        FlagProvider::getInstance().overrideFuncs(&isAtLeastSFuncFalse);
    }

    void TearDown() override {
        FlagProvider::getInstance().resetOverrides();
        writeFlag(TEST_FLAG, FLAG_EMPTY);
    }
};

INSTANTIATE_TEST_SUITE_P(
        ServerFlagValues, FlagProviderTest_RMinus,
        testing::ValuesIn<FlagParam>({
                // Server flag values.
                {FLAG_TRUE, "ServerFlagTrue"},
                {FLAG_FALSE, "ServerFlagFalse"},
                {FLAG_EMPTY, "ServerFlagEmpty"},
        }),
        [](const testing::TestParamInfo<FlagProviderTest_RMinus::ParamType>& info) {
            return info.param.label;
        });

TEST_P(FlagProviderTest_RMinus, TestGetFlagBoolDefaultValueFalse) {
    EXPECT_FALSE(FlagProvider::getInstance().getFlagBool(TEST_FLAG, FLAG_FALSE));
}

TEST_P(FlagProviderTest_RMinus, TestGetFlagBoolDefaultValueTrue) {
    EXPECT_TRUE(FlagProvider::getInstance().getFlagBool(TEST_FLAG, FLAG_TRUE));
}

#else
GTEST_LOG_(INFO) << "This test does nothing.\n";
#endif

}  // namespace statsd
}  // namespace os
}  // namespace android
