/*
 * Copyright (C) 2023 The Android Open Source Project
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
#include <android/configuration.h>

//-----------------------------------------------------------------
class AConfigurationTest : public ::testing::Test {

protected:
    /* Test setup*/
    virtual void SetUp() {
        config_ = AConfiguration_new();
        ASSERT_NE(nullptr, config_);
    }

    virtual void TearDown() {
        AConfiguration_delete(config_);
    }

    AConfiguration* config_;
};

// TODO(b/265391605): add all AConfiguration method tests.

//-------------------------------------------------------------------------------------------------

// @ApiTest = AConfiguration_new|AConfiguration_delete
TEST_F(AConfigurationTest, testNewDelete) {
    // Will be done with SetUp and TearDown.
}

// @ApiTest = AConfiguration_getGrammaticalGender|AConfiguration_setGrammaticalGender
TEST_F(AConfigurationTest, testGrammaticalGender) {
    EXPECT_EQ(ACONFIGURATION_GRAMMATICAL_GENDER_ANY, AConfiguration_getGrammaticalGender(config_));
    AConfiguration_setGrammaticalGender(config_, ACONFIGURATION_GRAMMATICAL_GENDER_NEUTER);
    EXPECT_EQ(ACONFIGURATION_GRAMMATICAL_GENDER_NEUTER, AConfiguration_getGrammaticalGender(config_));
}

