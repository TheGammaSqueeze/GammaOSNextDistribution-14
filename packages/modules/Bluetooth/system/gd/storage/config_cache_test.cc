/*
 * Copyright 2020 The Android Open Source Project
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

#include "storage/config_cache.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <cstdio>

#include "hci/enum_helper.h"
#include "storage/config_keys.h"
#include "storage/device.h"

namespace testing {

namespace {
std::string GetTestAddress(int i) {
  std::string res = "00:00:00:00:00:00";
  res.reserve(res.size() + 1);
  std::snprintf(res.data(), res.capacity(), "AA:BB:CC:DD:EE:%02d", i);
  return res;
}
}  // namespace

using bluetooth::storage::ConfigCache;
using bluetooth::storage::Device;
using SectionAndPropertyValue = bluetooth::storage::ConfigCache::SectionAndPropertyValue;

TEST(ConfigCacheTest, simple_set_get_test) {
  ConfigCache config(100, Device::kLinkKeyProperties);
  config.SetProperty("A", "B", "C");
  auto value = config.GetProperty("A", "B");
  ASSERT_TRUE(value);
  ASSERT_EQ(*value, "C");
}

TEST(ConfigCacheTest, empty_values_test) {
  ConfigCache config(100, Device::kLinkKeyProperties);
  ASSERT_DEATH({ config.SetProperty("", "B", "C"); }, "Empty section name not allowed");
  ASSERT_DEATH({ config.SetProperty("A", "", "C"); }, "Empty property name not allowed");
  // empty value is allowed
  config.SetProperty("A", "B", "");
  auto value = config.GetProperty("A", "B");
  ASSERT_TRUE(value);
  ASSERT_EQ(*value, "");
}

TEST(ConfigCacheTest, insert_boundary_device_with_linkkey_test) {
  ConfigCache config(2, Device::kLinkKeyProperties);
  config.SetProperty("A", "B", "C");
  config.SetProperty("CC:DD:EE:FF:00:10", BTIF_STORAGE_KEY_NAME, "Hello");
  config.SetProperty("CC:DD:EE:FF:00:09", BTIF_STORAGE_KEY_NAME, "Hello 2");
  config.SetProperty("CC:DD:EE:FF:00:11", BTIF_STORAGE_KEY_LINK_KEY, "AABBAABBCCDDEE");
  ASSERT_TRUE(config.GetProperty("CC:DD:EE:FF:00:10", BTIF_STORAGE_KEY_NAME));
}

TEST(ConfigCacheTest, comparison_test) {
  ConfigCache config_1(2, Device::kLinkKeyProperties);
  config_1.SetProperty("A", "B", "C");
  config_1.SetProperty("CC:DD:EE:FF:00:10", BTIF_STORAGE_KEY_NAME, "Hello");
  config_1.SetProperty("CC:DD:EE:FF:00:09", BTIF_STORAGE_KEY_NAME, "Hello 2");
  config_1.SetProperty("CC:DD:EE:FF:00:11", BTIF_STORAGE_KEY_LINK_KEY, "AABBAABBCCDDEE");
  ConfigCache config_2(2, Device::kLinkKeyProperties);
  config_2.SetProperty("A", "B", "C");
  config_2.SetProperty("CC:DD:EE:FF:00:10", BTIF_STORAGE_KEY_NAME, "Hello");
  config_2.SetProperty("CC:DD:EE:FF:00:09", BTIF_STORAGE_KEY_NAME, "Hello 2");
  config_2.SetProperty("CC:DD:EE:FF:00:11", BTIF_STORAGE_KEY_LINK_KEY, "AABBAABBCCDDEE");
  ASSERT_EQ(config_1, config_2);
  // Config with different temp device order should not be equal
  ASSERT_TRUE(config_2.GetProperty("CC:DD:EE:FF:00:10", BTIF_STORAGE_KEY_NAME));
  ASSERT_NE(config_1, config_2);
  ASSERT_TRUE(config_1.GetProperty("CC:DD:EE:FF:00:10", BTIF_STORAGE_KEY_NAME));
  ASSERT_EQ(config_1, config_2);
  // Config with different persistent device order should not be equal
  config_1.SetProperty("CC:DD:EE:FF:00:12", BTIF_STORAGE_KEY_LINK_KEY, "AABBAABBCCDDEE");
  config_2.RemoveSection("CC:DD:EE:FF:00:11");
  config_2.SetProperty("CC:DD:EE:FF:00:12", BTIF_STORAGE_KEY_LINK_KEY, "AABBAABBCCDDEE");
  config_2.SetProperty("CC:DD:EE:FF:00:11", BTIF_STORAGE_KEY_LINK_KEY, "AABBAABBCCDDEE");
  ASSERT_NE(config_1, config_2);
  // Config with different capacity should not be equal
  ConfigCache config_3(3, Device::kLinkKeyProperties);
  config_3.SetProperty("A", "B", "C");
  config_3.SetProperty("CC:DD:EE:FF:00:10", BTIF_STORAGE_KEY_NAME, "Hello");
  config_3.SetProperty("CC:DD:EE:FF:00:09", BTIF_STORAGE_KEY_NAME, "Hello 2");
  config_3.SetProperty("CC:DD:EE:FF:00:11", BTIF_STORAGE_KEY_LINK_KEY, "AABBAABBCCDDEE");
  config_3.SetProperty("CC:DD:EE:FF:00:12", BTIF_STORAGE_KEY_LINK_KEY, "AABBAABBCCDDEE");
  ASSERT_NE(config_1, config_3);
  // Empty config should not be equal to non-empty ones
  ConfigCache config_4(2, Device::kLinkKeyProperties);
  ASSERT_NE(config_1, config_4);
  // Empty configs should be equal
  ConfigCache config_5(2, Device::kLinkKeyProperties);
  ASSERT_EQ(config_4, config_5);
  // Empty configs with different capacity should not be equal
  ConfigCache config_6(3, Device::kLinkKeyProperties);
  ASSERT_NE(config_4, config_6);
}

TEST(ConfigCacheTest, empty_string_test) {
  ConfigCache config(100, Device::kLinkKeyProperties);
  config.SetProperty("A", "B", "");
  auto value = config.GetProperty("A", "B");
  ASSERT_TRUE(value);
  ASSERT_EQ(*value, "");
}

TEST(ConfigCacheTest, mac_address_set_get_test) {
  ConfigCache config(100, Device::kLinkKeyProperties);
  config.SetProperty("A", "B", "C");
  config.SetProperty("AA:BB:CC:DD:EE:FF", "B", "C");
  auto value = config.GetProperty("A", "B");
  ASSERT_TRUE(value);
  ASSERT_EQ(*value, "C");
  value = config.GetProperty("AA:BB:CC:DD:EE:FF", "B");
  ASSERT_TRUE(value);
  ASSERT_EQ(*value, "C");
  ASSERT_FALSE(config.GetProperty("A", "BC"));
  ASSERT_FALSE(config.GetProperty("ABC", "B"));
}

TEST(ConfigCacheTest, has_section_and_property_test) {
  ConfigCache config(100, Device::kLinkKeyProperties);
  config.SetProperty("A", "B", "C");
  config.SetProperty("AA:BB:CC:DD:EE:FF", "B", "C");
  config.SetProperty("AA:BB:CC:DD:EE:FF", "C", "D");
  ASSERT_TRUE(config.HasSection("A"));
  ASSERT_TRUE(config.HasSection("AA:BB:CC:DD:EE:FF"));
  ASSERT_TRUE(config.HasProperty("A", "B"));
  ASSERT_TRUE(config.HasProperty("AA:BB:CC:DD:EE:FF", "B"));
  config.SetProperty("AA:BB:CC:DD:EE:FF", "C", "D");
  auto value = config.GetProperty("AA:BB:CC:DD:EE:FF", "C");
  ASSERT_TRUE(value);
  ASSERT_EQ(*value, "D");
  value = config.GetProperty("AA:BB:CC:DD:EE:FF", "B");
  ASSERT_TRUE(value);
  ASSERT_EQ(*value, "C");
  config.SetProperty("AA:BB:CC:DD:EE:FF", "B", "E");
  value = config.GetProperty("AA:BB:CC:DD:EE:FF", "B");
  ASSERT_TRUE(value);
  ASSERT_THAT(value, Optional(StrEq("E")));
  ASSERT_FALSE(config.HasSection("Ab"));
  ASSERT_FALSE(config.HasSection("AA:11:CC:DD:EE:FF"));
  ASSERT_FALSE(config.HasProperty("A", "bB"));
  ASSERT_FALSE(config.HasProperty("AA:BB:11:DD:EE:FF", "B"));
}

TEST(ConfigCacheTest, remove_section_test) {
  ConfigCache config(100, Device::kLinkKeyProperties);
  config.SetProperty("A", "B", "C");
  config.SetProperty("AA:BB:CC:DD:EE:FF", "B", "C");
  config.SetProperty("AA:BB:CC:DD:EE:FF", "C", "D");
  ASSERT_TRUE(config.HasSection("A"));
  ASSERT_TRUE(config.HasSection("AA:BB:CC:DD:EE:FF"));
  ASSERT_TRUE(config.HasProperty("A", "B"));
  ASSERT_TRUE(config.HasProperty("AA:BB:CC:DD:EE:FF", "B"));
  ASSERT_TRUE(config.RemoveSection("AA:BB:CC:DD:EE:FF"));
  ASSERT_TRUE(config.RemoveSection("A"));
  ASSERT_FALSE(config.HasProperty("A", "B"));
  ASSERT_FALSE(config.HasProperty("AA:BB:CC:DD:EE:FF", "B"));
}

TEST(ConfigCacheTest, remove_property_test) {
  ConfigCache config(100, Device::kLinkKeyProperties);
  config.SetProperty("A", "B", "C");
  config.SetProperty("AA:BB:CC:DD:EE:FF", "B", "C");
  config.SetProperty("AA:BB:CC:DD:EE:FF", "C", "D");
  ASSERT_TRUE(config.HasSection("A"));
  ASSERT_TRUE(config.HasSection("AA:BB:CC:DD:EE:FF"));
  ASSERT_TRUE(config.HasProperty("A", "B"));
  ASSERT_TRUE(config.HasProperty("AA:BB:CC:DD:EE:FF", "B"));
  ASSERT_TRUE(config.HasProperty("AA:BB:CC:DD:EE:FF", "C"));
  ASSERT_TRUE(config.RemoveProperty("AA:BB:CC:DD:EE:FF", "B"));
  ASSERT_FALSE(config.HasProperty("AA:BB:CC:DD:EE:FF", "B"));
  ASSERT_FALSE(config.GetProperty("AA:BB:CC:DD:EE:FF", "B"));
}

TEST(ConfigCacheTest, remove_all_properties_from_section_test) {
  ConfigCache config(100, Device::kLinkKeyProperties);
  config.SetProperty("A", "B", "C");
  config.SetProperty("AA:BB:CC:DD:EE:FF", "B", "C");
  config.SetProperty("AA:BB:CC:DD:EE:FF", "C", "D");
  ASSERT_TRUE(config.HasSection("A"));
  ASSERT_TRUE(config.HasSection("AA:BB:CC:DD:EE:FF"));
  ASSERT_TRUE(config.HasProperty("A", "B"));
  ASSERT_TRUE(config.HasProperty("AA:BB:CC:DD:EE:FF", "B"));
  ASSERT_TRUE(config.HasProperty("AA:BB:CC:DD:EE:FF", "C"));
  ASSERT_TRUE(config.RemoveSection("AA:BB:CC:DD:EE:FF"));
  ASSERT_FALSE(config.HasSection("AA:BB:CC:DD:EE:FF"));
  ASSERT_FALSE(config.HasProperty("AA:BB:CC:DD:EE:FF", "B"));
  ASSERT_FALSE(config.GetProperty("AA:BB:CC:DD:EE:FF", "C"));
}

TEST(ConfigCacheTest, get_persistent_devices_test) {
  ConfigCache config(100, Device::kLinkKeyProperties);
  config.SetProperty("A", "B", "C");
  config.SetProperty("AA:BB:CC:DD:EE:FF", "B", "C");
  config.SetProperty("AA:BB:CC:DD:EE:FF", "C", "D");
  config.SetProperty("CC:DD:EE:FF:00:11", BTIF_STORAGE_KEY_LINK_KEY, "AABBAABBCCDDEE");
  ASSERT_TRUE(config.HasProperty("CC:DD:EE:FF:00:11", BTIF_STORAGE_KEY_LINK_KEY));
  ASSERT_THAT(config.GetPersistentSections(), ElementsAre("CC:DD:EE:FF:00:11"));
  config.SetProperty("AA:BB:CC:DD:EE:FF", BTIF_STORAGE_KEY_LINK_KEY, "DEERDEERDEER");
  ASSERT_THAT(config.GetPersistentSections(), ElementsAre("CC:DD:EE:FF:00:11", "AA:BB:CC:DD:EE:FF"));
  ASSERT_TRUE(config.RemoveProperty("CC:DD:EE:FF:00:11", BTIF_STORAGE_KEY_LINK_KEY));
  ASSERT_THAT(config.GetPersistentSections(), ElementsAre("AA:BB:CC:DD:EE:FF"));
}

TEST(ConfigCacheTest, appoaching_temporary_config_limit_test) {
  ConfigCache config(2, Device::kLinkKeyProperties);
  for (int i = 0; i < 10; ++i) {
    config.SetProperty(GetTestAddress(i), BTIF_STORAGE_KEY_NAME, "Hello" + std::to_string(i));
    if (i % 2 == 0) {
      config.SetProperty(GetTestAddress(i), BTIF_STORAGE_KEY_LINK_KEY, "Key" + std::to_string(i));
    }
  }
  for (int i = 0; i < 10; ++i) {
    if (i % 2 == 0) {
      ASSERT_TRUE(config.HasSection(GetTestAddress(i)));
      ASSERT_TRUE(config.HasProperty(GetTestAddress(i), BTIF_STORAGE_KEY_LINK_KEY));
      ASSERT_THAT(
          config.GetProperty(GetTestAddress(i), BTIF_STORAGE_KEY_NAME),
          Optional(StrEq("Hello" + std::to_string(i))));
    } else if (i >= 7) {
      ASSERT_TRUE(config.HasSection(GetTestAddress(i)));
      ASSERT_THAT(
          config.GetProperty(GetTestAddress(i), BTIF_STORAGE_KEY_NAME),
          Optional(StrEq("Hello" + std::to_string(i))));
    } else {
      ASSERT_FALSE(config.HasSection(GetTestAddress(i)));
    }
  }
  ASSERT_THAT(
      config.GetPersistentSections(),
      ElementsAre(GetTestAddress(0), GetTestAddress(2), GetTestAddress(4), GetTestAddress(6), GetTestAddress(8)));
}

TEST(ConfigCacheTest, remove_section_with_property_test) {
  ConfigCache config(100, Device::kLinkKeyProperties);
  config.SetProperty("A", "B", "C");
  config.SetProperty("AA:BB:CC:DD:EE:FF", "B", "C");
  config.SetProperty("AA:BB:CC:DD:EE:FF", "C", "D");
  config.SetProperty("CC:DD:EE:FF:00:11", "B", "AABBAABBCCDDEE");
  config.SetProperty("CC:DD:EE:FF:00:11", BTIF_STORAGE_KEY_LINK_KEY, "AABBAABBCCDDEE");
  config.RemoveSectionWithProperty("B");
  ASSERT_FALSE(config.HasSection("A"));
  ASSERT_FALSE(config.HasSection("AA:BB:CC:DD:EE:FF"));
  ASSERT_FALSE(config.HasSection("CC:DD:EE:FF:00:11"));
}

TEST(ConfigCacheTest, persistent_config_changed_callback_test) {
  ConfigCache config(100, Device::kLinkKeyProperties);
  int num_change = 0;
  config.SetPersistentConfigChangedCallback([&num_change] { num_change++; });
  config.SetProperty("A", "B", "C");
  ASSERT_EQ(num_change, 1);
  config.SetProperty("AA:BB:CC:DD:EE:FF", "B", "C");
  ASSERT_EQ(num_change, 1);
  config.SetProperty("AA:BB:CC:DD:EE:FF", "C", "D");
  ASSERT_EQ(num_change, 1);
  config.SetProperty("CC:DD:EE:FF:00:11", "B", "AABBAABBCCDDEE");
  ASSERT_EQ(num_change, 1);
  config.SetProperty("CC:DD:EE:FF:00:11", BTIF_STORAGE_KEY_LINK_KEY, "AABBAABBCCDDEE");
  ASSERT_EQ(num_change, 2);
  config.RemoveProperty("CC:DD:EE:FF:00:11", BTIF_STORAGE_KEY_LINK_KEY);
  ASSERT_EQ(num_change, 3);
  config.RemoveSectionWithProperty("B");
  ASSERT_EQ(num_change, 4);
}

TEST(ConfigCacheTest, fix_device_type_inconsistency_missing_devtype_no_keys_test) {
  ConfigCache config(100, Device::kLinkKeyProperties);
  config.SetProperty("A", "B", "C");
  config.SetProperty("AA:BB:CC:DD:EE:FF", "B", "C");
  config.SetProperty("AA:BB:CC:DD:EE:FF", "C", "D");

  auto hadInconsistencies = config.FixDeviceTypeInconsistencies();

  ASSERT_TRUE(hadInconsistencies);
  ASSERT_THAT(
      config.GetProperty("AA:BB:CC:DD:EE:FF", BTIF_STORAGE_KEY_DEV_TYPE),
      Optional(StrEq(std::to_string(bluetooth::hci::DeviceType::BR_EDR))));
}

TEST(ConfigCacheTest, fix_device_type_inconsistency_consistent_devtype_test) {
  // arrange
  ConfigCache config(100, Device::kLinkKeyProperties);
  config.SetProperty("A", "B", "C");
  config.SetProperty("AA:BB:CC:DD:EE:FF", "B", "C");
  config.SetProperty("AA:BB:CC:DD:EE:FF", "C", "D");
  config.SetProperty(
      "AA:BB:CC:DD:EE:FF",
      BTIF_STORAGE_KEY_DEV_TYPE,
      std::to_string(bluetooth::hci::DeviceType::BR_EDR));

  config.SetProperty("CC:DD:EE:FF:00:11", "B", "AABBAABBCCDDEE");
  config.SetProperty(
      "CC:DD:EE:FF:00:11",
      BTIF_STORAGE_KEY_DEV_TYPE,
      std::to_string(bluetooth::hci::DeviceType::BR_EDR));
  config.SetProperty("CC:DD:EE:FF:00:11", BTIF_STORAGE_KEY_LINK_KEY, "AABBAABBCCDDEE");

  // act
  auto hadInconsistencies = config.FixDeviceTypeInconsistencies();

  // assert
  ASSERT_FALSE(hadInconsistencies);
  ASSERT_THAT(
      config.GetProperty("CC:DD:EE:FF:00:11", BTIF_STORAGE_KEY_DEV_TYPE),
      Optional(StrEq(std::to_string(bluetooth::hci::DeviceType::BR_EDR))));
}

TEST(ConfigCacheTest, fix_device_type_inconsistency_devtype_should_be_dual_test) {
  // arrange
  ConfigCache config(100, Device::kLinkKeyProperties);
  config.SetProperty("A", "B", "C");
  config.SetProperty("AA:BB:CC:DD:EE:FF", "B", "C");
  config.SetProperty("AA:BB:CC:DD:EE:FF", "C", "D");
  config.SetProperty(
      "AA:BB:CC:DD:EE:FF",
      BTIF_STORAGE_KEY_DEV_TYPE,
      std::to_string(bluetooth::hci::DeviceType::BR_EDR));

  config.SetProperty("CC:DD:EE:FF:00:11", "B", "AABBAABBCCDDEE");
  config.SetProperty(
      "CC:DD:EE:FF:00:11",
      BTIF_STORAGE_KEY_DEV_TYPE,
      std::to_string(bluetooth::hci::DeviceType::BR_EDR));
  config.SetProperty("CC:DD:EE:FF:00:11", BTIF_STORAGE_KEY_LINK_KEY, "AABBAABBCCDDEE");
  config.SetProperty("CC:DD:EE:FF:00:11", BTIF_STORAGE_KEY_LE_KEY_PENC, "AABBAABBCCDDEE");

  // act
  auto hadInconsistencies = config.FixDeviceTypeInconsistencies();

  // assert
  ASSERT_TRUE(hadInconsistencies);
  ASSERT_THAT(
      config.GetProperty("CC:DD:EE:FF:00:11", BTIF_STORAGE_KEY_DEV_TYPE),
      Optional(StrEq(std::to_string(bluetooth::hci::DeviceType::DUAL))));
}

TEST(ConfigCacheTest, fix_device_type_inconsistency_devtype_should_be_le_not_classic_test) {
  // arrange
  ConfigCache config(100, Device::kLinkKeyProperties);
  config.SetProperty("A", "B", "C");
  config.SetProperty("AA:BB:CC:DD:EE:FF", "B", "C");
  config.SetProperty("AA:BB:CC:DD:EE:FF", "C", "D");
  config.SetProperty(
      "AA:BB:CC:DD:EE:FF",
      BTIF_STORAGE_KEY_DEV_TYPE,
      std::to_string(bluetooth::hci::DeviceType::BR_EDR));

  config.SetProperty("CC:DD:EE:FF:00:11", "B", "AABBAABBCCDDEE");
  config.SetProperty(
      "CC:DD:EE:FF:00:11",
      BTIF_STORAGE_KEY_DEV_TYPE,
      std::to_string(bluetooth::hci::DeviceType::BR_EDR));
  config.SetProperty("CC:DD:EE:FF:00:11", BTIF_STORAGE_KEY_LE_KEY_PENC, "AABBAABBCCDDEE");

  // act
  auto hadInconsistencies = config.FixDeviceTypeInconsistencies();

  // assert
  ASSERT_TRUE(hadInconsistencies);
  ASSERT_THAT(
      config.GetProperty("CC:DD:EE:FF:00:11", BTIF_STORAGE_KEY_DEV_TYPE),
      Optional(StrEq(std::to_string(bluetooth::hci::DeviceType::LE))));
}

TEST(ConfigCacheTest, fix_device_type_inconsistency_devtype_dont_override_dual_test) {
  // arrange
  ConfigCache config(100, Device::kLinkKeyProperties);
  config.SetProperty("A", "B", "C");
  config.SetProperty("AA:BB:CC:DD:EE:FF", "B", "C");
  config.SetProperty("AA:BB:CC:DD:EE:FF", "C", "D");
  config.SetProperty(
      "AA:BB:CC:DD:EE:FF",
      BTIF_STORAGE_KEY_DEV_TYPE,
      std::to_string(bluetooth::hci::DeviceType::BR_EDR));

  config.SetProperty("CC:DD:EE:FF:00:11", "B", "AABBAABBCCDDEE");
  config.SetProperty(
      "CC:DD:EE:FF:00:11",
      BTIF_STORAGE_KEY_DEV_TYPE,
      std::to_string(bluetooth::hci::DeviceType::DUAL));
  config.SetProperty("CC:DD:EE:FF:00:11", BTIF_STORAGE_KEY_LINK_KEY, "AABBAABBCCDDEE");
  config.SetProperty("CC:DD:EE:FF:00:11", BTIF_STORAGE_KEY_LE_KEY_PENC, "AABBAABBCCDDEE");

  // act
  auto hadInconsistencies = config.FixDeviceTypeInconsistencies();

  // assert
  ASSERT_FALSE(hadInconsistencies);
  ASSERT_THAT(
      config.GetProperty("CC:DD:EE:FF:00:11", BTIF_STORAGE_KEY_DEV_TYPE),
      Optional(StrEq(std::to_string(bluetooth::hci::DeviceType::DUAL))));
}

TEST(ConfigCacheTest, test_get_section_with_property) {
  ConfigCache config(100, Device::kLinkKeyProperties);
  config.SetProperty("A", "B", "C");
  config.SetProperty("AA:BB:CC:DD:EE:FF", "B", "C");
  config.SetProperty("AA:BB:CC:DD:EE:EF", "C", "D");
  ASSERT_THAT(
      config.GetSectionNamesWithProperty("B"),
      ElementsAre(
          SectionAndPropertyValue{.section = "A", .property = "C"},
          SectionAndPropertyValue{.section = "AA:BB:CC:DD:EE:FF", .property = "C"}));
}

TEST(ConfigCacheTest, test_get_sections_matching_at_least_one_property) {
  ConfigCache config(100, Device::kLinkKeyProperties);
  config.SetProperty("A", "B", "C");
  config.SetProperty("AA:BB:CC:DD:EE:FF", "B", "C");
  config.SetProperty("AA:BB:CC:DD:EE:EF", "C", "D");
  ASSERT_TRUE(config.HasAtLeastOneMatchingPropertiesInSection("AA:BB:CC:DD:EE:FF", {"B", "C", "D"}));
  ASSERT_TRUE(config.HasAtLeastOneMatchingPropertiesInSection("A", {"B", "C", "D"}));
  ASSERT_FALSE(config.HasAtLeastOneMatchingPropertiesInSection("AA:BB:CC:DD:EE:FF", {"BC", "D"}));
}

TEST(ConfigCacheTest, test_empty_persistent_properties) {
  ConfigCache config(100, {});
  config.SetProperty("A", "B", "C");
  config.SetProperty("AA:BB:CC:DD:EE:FF", "B", "C");
  config.SetProperty("AA:BB:CC:DD:EE:EF", "C", "D");
  config.SetProperty("AA:BB:CC:DD:EE:EF", BTIF_STORAGE_KEY_LINK_KEY, "D");
  ASSERT_TRUE(config.HasAtLeastOneMatchingPropertiesInSection("AA:BB:CC:DD:EE:FF", {"B", "C", "D"}));
  ASSERT_TRUE(config.HasAtLeastOneMatchingPropertiesInSection("A", {"B", "C", "D"}));
  ASSERT_FALSE(config.HasAtLeastOneMatchingPropertiesInSection("AA:BB:CC:DD:EE:FF", {"BC", "D"}));
  ASSERT_THAT(config.GetPersistentSections(), ElementsAre());
}

}  // namespace testing