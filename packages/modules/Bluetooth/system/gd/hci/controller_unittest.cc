/*
 * Copyright 2022 The Android Open Source Project
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

#include "hci/controller.h"

#include <gtest/gtest.h>

namespace bluetooth {
namespace hci {

class ControllerUnitTest : public ::testing::Test {
 protected:
  void SetUp() override {}

  void TearDown() override {}
};

TEST_F(ControllerUnitTest, testLeEventMask) {
  LocalVersionInformation version;
  version.hci_version_ = HciVersion::V_5_3;

  // Update the function and this test when adding new bits.
  ASSERT_TRUE(Controller::kLeEventMask53 > Controller::kDefaultLeEventMask);

  ASSERT_EQ(
      Controller::MaskLeEventMask(version.hci_version_, Controller::kDefaultLeEventMask),
      Controller::kDefaultLeEventMask);
  ASSERT_LE(
      Controller::MaskLeEventMask(version.hci_version_, Controller::kDefaultLeEventMask),
      Controller::kLeEventMask53);
  version.hci_version_ = HciVersion::V_5_2;
  ASSERT_LE(
      Controller::MaskLeEventMask(version.hci_version_, Controller::kDefaultLeEventMask), Controller::kLeEventMask52);
  version.hci_version_ = HciVersion::V_5_1;
  ASSERT_LE(
      Controller::MaskLeEventMask(version.hci_version_, Controller::kDefaultLeEventMask), Controller::kLeEventMask51);
  version.hci_version_ = HciVersion::V_4_2;
  ASSERT_LE(
      Controller::MaskLeEventMask(version.hci_version_, Controller::kDefaultLeEventMask), Controller::kLeEventMask42);
  version.hci_version_ = HciVersion::V_4_1;
  ASSERT_LE(
      Controller::MaskLeEventMask(version.hci_version_, Controller::kDefaultLeEventMask), Controller::kLeEventMask41);
}

}  // namespace hci
}  // namespace bluetooth
