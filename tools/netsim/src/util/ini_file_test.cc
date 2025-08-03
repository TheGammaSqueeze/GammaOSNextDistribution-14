// Copyright 2022 The Android Open Source Project
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

// Unit tests for IniFile class.
#include "util/ini_file.h"

#include <cstdio>
#include <fstream>
#include <string>

#include "gtest/gtest.h"

namespace netsim {
namespace testing {
namespace {

class IniFileReadTest : public ::testing::TestWithParam<std::string> {
 public:
  void SetUp() override {
    tempFileName_ = std::tmpnam(NULL);
    std::ofstream outTempFile(tempFileName_);
    ASSERT_TRUE(outTempFile.is_open());
    outTempFile << GetParam();
    outTempFile.close();
  }
  void TearDown() override {
    // Delete temp file.
    ASSERT_EQ(std::remove(tempFileName_), 0);
  }
  const char *tempFileName_;
};

TEST_P(IniFileReadTest, Read) {
  IniFile iniFile(tempFileName_);
  iniFile.Read();

  EXPECT_TRUE(iniFile.HasKey("port"));
  EXPECT_FALSE(iniFile.HasKey("unknown-key"));
  EXPECT_TRUE(iniFile.Get("port").has_value());
  EXPECT_EQ(iniFile.Get("port").value(), "123");
  EXPECT_FALSE(iniFile.Get("unknown-key").has_value());
}

INSTANTIATE_TEST_SUITE_P(IniFileParameters, IniFileReadTest,
                         ::testing::Values("port=123", "port= 123", "port =123",
                                           " port = 123 "));

TEST(IniFileTest, SetTest) {
  const char *tempFileName = tmpnam(NULL);
  IniFile iniFile(tempFileName);

  EXPECT_FALSE(iniFile.HasKey("port"));
  EXPECT_FALSE(iniFile.HasKey("unknown-key"));
  EXPECT_FALSE(iniFile.Get("port").has_value());
  EXPECT_FALSE(iniFile.Get("unknown-key").has_value());

  iniFile.Set("port", "123");
  EXPECT_TRUE(iniFile.HasKey("port"));
  EXPECT_FALSE(iniFile.HasKey("unknown-key"));
  EXPECT_EQ(iniFile.Get("port").value(), "123");
  EXPECT_FALSE(iniFile.Get("unknown-key").has_value());

  // Update the value of an existing key.
  iniFile.Set("port", "234");
  EXPECT_TRUE(iniFile.HasKey("port"));
  EXPECT_FALSE(iniFile.HasKey("unknown-key"));
  EXPECT_EQ(iniFile.Get("port").value(), "234");
  EXPECT_FALSE(iniFile.Get("unknown-key").has_value());
}

TEST(IniFileTest, WriteTest) {
  const char *tempFileName = tmpnam(NULL);
  {
    IniFile iniFile(tempFileName);

    EXPECT_FALSE(iniFile.HasKey("port"));
    EXPECT_FALSE(iniFile.HasKey("unknown-key"));
    EXPECT_FALSE(iniFile.Get("port").has_value());
    EXPECT_FALSE(iniFile.Get("unknown-key").has_value());

    iniFile.Set("port", "123");
    EXPECT_TRUE(iniFile.HasKey("port"));
    EXPECT_FALSE(iniFile.HasKey("unknown-key"));
    EXPECT_EQ(iniFile.Get("port").value(), "123");
    EXPECT_FALSE(iniFile.Get("unknown-key").has_value());

    iniFile.Write();

    std::ifstream inTempFile(tempFileName);
    std::string line;
    ASSERT_TRUE(getline(inTempFile, line));
    EXPECT_EQ(line, "port=123");
    EXPECT_FALSE(getline(inTempFile, line));
  }

  // Delete temp file.
  ASSERT_EQ(std::remove(tempFileName), 0);
}

}  // namespace
}  // namespace testing
}  // namespace netsim
