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

#include "tools/system_properties.h"

#include "gmock/gmock.h"
#include "gtest/gtest.h"

namespace art {
namespace tools {
namespace {

using ::testing::Return;

class MockSystemProperties : public SystemProperties {
 public:
  MOCK_METHOD(std::string, GetProperty, (const std::string& key), (const, override));
};

class SystemPropertiesTest : public testing::Test {
 protected:
  MockSystemProperties system_properties_;
};

TEST_F(SystemPropertiesTest, Get) {
  EXPECT_CALL(system_properties_, GetProperty("key_1")).WillOnce(Return("value_1"));
  EXPECT_EQ(system_properties_.Get("key_1", /*default_value=*/"default"), "value_1");
}

TEST_F(SystemPropertiesTest, GetWithFallback) {
  EXPECT_CALL(system_properties_, GetProperty("key_1")).WillOnce(Return(""));
  EXPECT_CALL(system_properties_, GetProperty("key_2")).WillOnce(Return("value_2"));
  EXPECT_CALL(system_properties_, GetProperty("key_3")).WillOnce(Return("value_3"));
  EXPECT_EQ(system_properties_.Get("key_1", "key_2", "key_3", /*default_value=*/"default"),
            "value_2");
}

TEST_F(SystemPropertiesTest, GetDefault) {
  EXPECT_CALL(system_properties_, GetProperty("key_1")).WillOnce(Return(""));
  EXPECT_EQ(system_properties_.Get("key_1", /*default_value=*/"default"), "default");
}

TEST_F(SystemPropertiesTest, GetOrEmpty) {
  EXPECT_CALL(system_properties_, GetProperty("key_1")).WillOnce(Return("value_1"));
  EXPECT_EQ(system_properties_.GetOrEmpty("key_1"), "value_1");
}

TEST_F(SystemPropertiesTest, GetOrEmptyWithFallback) {
  EXPECT_CALL(system_properties_, GetProperty("key_1")).WillOnce(Return(""));
  EXPECT_CALL(system_properties_, GetProperty("key_2")).WillOnce(Return("value_2"));
  EXPECT_CALL(system_properties_, GetProperty("key_3")).WillOnce(Return("value_3"));
  EXPECT_EQ(system_properties_.GetOrEmpty("key_1", "key_2", "key_3"), "value_2");
}

TEST_F(SystemPropertiesTest, GetOrEmptyDefault) {
  EXPECT_CALL(system_properties_, GetProperty("key_1")).WillOnce(Return(""));
  EXPECT_EQ(system_properties_.GetOrEmpty("key_1"), "");
}

TEST_F(SystemPropertiesTest, GetBoolTrue) {
  EXPECT_CALL(system_properties_, GetProperty("key_1")).WillOnce(Return("true"));
  EXPECT_EQ(system_properties_.GetBool("key_1", /*default_value=*/false), true);
}

TEST_F(SystemPropertiesTest, GetBoolFalse) {
  EXPECT_CALL(system_properties_, GetProperty("key_1")).WillOnce(Return("false"));
  EXPECT_EQ(system_properties_.GetBool("key_1", /*default_value=*/true), false);
}

TEST_F(SystemPropertiesTest, GetBoolWithFallback) {
  EXPECT_CALL(system_properties_, GetProperty("key_1")).WillOnce(Return(""));
  EXPECT_CALL(system_properties_, GetProperty("key_2")).WillOnce(Return("true"));
  EXPECT_CALL(system_properties_, GetProperty("key_3")).WillOnce(Return("false"));
  EXPECT_EQ(system_properties_.GetBool("key_1", "key_2", "key_3", /*default_value=*/false), true);
}

TEST_F(SystemPropertiesTest, GetBoolDefault) {
  EXPECT_CALL(system_properties_, GetProperty("key_1")).WillOnce(Return(""));
  EXPECT_EQ(system_properties_.GetBool("key_1", /*default_value=*/true), true);
}

}  // namespace
}  // namespace tools
}  // namespace art
