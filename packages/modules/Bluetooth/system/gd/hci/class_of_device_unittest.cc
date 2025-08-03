/******************************************************************************
 *
 *  Copyright 2019 The Android Open Source Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include "hci/class_of_device.h"

using bluetooth::hci::ClassOfDevice;

static const char* test_class = "efc-d-ab";
static const uint8_t test_bytes[]{0xab, 0xcd, 0xef};

TEST(ClassOfDeviceUnittest, test_constructor_array) {
  ClassOfDevice cod(test_bytes);

  ASSERT_EQ(test_bytes[0], cod.cod[0]);
  ASSERT_EQ(test_bytes[1], cod.cod[1]);
  ASSERT_EQ(test_bytes[2], cod.cod[2]);

  std::string ret = cod.ToString();

  ASSERT_STREQ(test_class, ret.c_str());
}

TEST(ClassOfDeviceUnittest, test_to_from_str) {
  ClassOfDevice cod;
  ClassOfDevice::FromString(test_class, cod);

  ASSERT_EQ(test_bytes[0], cod.cod[0]);
  ASSERT_EQ(test_bytes[1], cod.cod[1]);
  ASSERT_EQ(test_bytes[2], cod.cod[2]);

  std::string ret = cod.ToString();

  ASSERT_STREQ(test_class, ret.c_str());
}

TEST(ClassOfDeviceUnittest, test_from_octets) {
  ClassOfDevice cod;
  size_t expected_result = ClassOfDevice::kLength;
  ASSERT_EQ(expected_result, cod.FromOctets(test_bytes));

  ASSERT_EQ(test_bytes[0], cod.cod[0]);
  ASSERT_EQ(test_bytes[1], cod.cod[1]);
  ASSERT_EQ(test_bytes[2], cod.cod[2]);

  std::string ret = cod.ToString();

  ASSERT_STREQ(test_class, ret.c_str());
}

TEST(ClassOfDeviceTest, test_copy) {
  ClassOfDevice cod1;
  ClassOfDevice cod2;
  ClassOfDevice::FromString(test_class, cod1);
  cod2 = cod1;

  ASSERT_EQ(cod1.cod[0], cod2.cod[0]);
  ASSERT_EQ(cod1.cod[1], cod2.cod[1]);
  ASSERT_EQ(cod1.cod[2], cod2.cod[2]);
}

TEST(ClassOfDeviceTest, IsValid) {
  ASSERT_FALSE(ClassOfDevice::IsValid(""));
  ASSERT_FALSE(ClassOfDevice::IsValid("000000"));
  ASSERT_FALSE(ClassOfDevice::IsValid("00-00-00"));
  ASSERT_FALSE(ClassOfDevice::IsValid("000-0-0"));
  ASSERT_TRUE(ClassOfDevice::IsValid("000-0-00"));
  ASSERT_TRUE(ClassOfDevice::IsValid("ABc-d-00"));
  ASSERT_TRUE(ClassOfDevice::IsValid("aBc-D-eF"));
}

TEST(ClassOfDeviceTest, classOfDeviceFromString) {
  ClassOfDevice cod;

  ASSERT_TRUE(ClassOfDevice::FromString("000-0-00", cod));
  const ClassOfDevice result0 = {{0x00, 0x00, 0x00}};
  ASSERT_EQ(0, memcmp(cod.data(), result0.data(), ClassOfDevice::kLength));

  ASSERT_TRUE(ClassOfDevice::FromString("ab2-1-4C", cod));
  const ClassOfDevice result1 = {{0x4c, 0x21, 0xab}};
  ASSERT_EQ(0, memcmp(cod.data(), result1.data(), ClassOfDevice::kLength));
}

TEST(ClassOfDeviceTest, classOfDeviceFromUint32Legacy) {
  auto cod = ClassOfDevice::FromUint32Legacy(0);
  ASSERT_TRUE(cod);
  ASSERT_THAT(cod->cod, testing::ElementsAre(0x00, 0x00, 0x00));
  ASSERT_EQ(cod->ToUint32Legacy(), 0ul);

  cod = ClassOfDevice::FromUint32Legacy(0xab214c);
  ASSERT_TRUE(cod);
  ASSERT_THAT(cod->cod, testing::ElementsAre(0xab, 0x21, 0x4c));
  ASSERT_EQ(cod->ToUint32Legacy(), 0xab214cul);

  ASSERT_FALSE(ClassOfDevice::FromUint32Legacy(0x1ab214c));
}
