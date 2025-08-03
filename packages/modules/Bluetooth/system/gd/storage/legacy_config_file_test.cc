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

#include "storage/legacy_config_file.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <filesystem>

#include "os/files.h"
#include "storage/config_keys.h"
#include "storage/device.h"

namespace testing {

using bluetooth::os::ReadSmallFile;
using bluetooth::os::WriteToFile;
using bluetooth::storage::ConfigCache;
using bluetooth::storage::Device;
using bluetooth::storage::LegacyConfigFile;

TEST(LegacyConfigFileTest, write_and_read_loop_back_test) {
  auto temp_dir = std::filesystem::temp_directory_path();
  auto temp_config = temp_dir / "temp_config.txt";

  ConfigCache config(100, Device::kLinkKeyProperties);
  config.SetProperty("A", "B", "C");
  config.SetProperty("AA:BB:CC:DD:EE:FF", "B", "C");
  config.SetProperty("AA:BB:CC:DD:EE:FF", "C", "D");
  config.SetProperty("CC:DD:EE:FF:00:11", BTIF_STORAGE_KEY_LINK_KEY, "AABBAABBCCDDEE");
  EXPECT_TRUE(config.HasProperty("CC:DD:EE:FF:00:11", BTIF_STORAGE_KEY_LINK_KEY));
  EXPECT_THAT(config.GetPersistentSections(), ElementsAre("CC:DD:EE:FF:00:11"));

  EXPECT_TRUE(LegacyConfigFile::FromPath(temp_config.string()).Write(config));
  auto config_read = LegacyConfigFile::FromPath(temp_config.string()).Read(100);
  EXPECT_TRUE(config_read);
  // Unpaired devices do not exist in persistent config file
  config.RemoveSection("AA:BB:CC:DD:EE:FF");
  EXPECT_EQ(config, *config_read);
  EXPECT_THAT(config_read->GetPersistentSections(), ElementsAre("CC:DD:EE:FF:00:11"));
  EXPECT_THAT(config_read->GetProperty("A", "B"), Optional(StrEq("C")));
  EXPECT_THAT(config_read->GetProperty("CC:DD:EE:FF:00:11", "LinkKey"), Optional(StrEq("AABBAABBCCDDEE")));

  EXPECT_TRUE(std::filesystem::remove(temp_config));
}

static const std::string kReadTestConfig =
    "[Info]\n"
    "FileSource = Empty\n"
    "TimeCreated = 2020-05-20 01:20:56\n"
    "\n"
    "[Metrics]\n"
    "Salt256Bit = 1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef\n"
    "\n"
    "[Adapter]\n"
    "Address = 01:02:03:ab:cd:ef\n"
    "LE_LOCAL_KEY_IRK = fedcba0987654321fedcba0987654321\n"
    "LE_LOCAL_KEY_IR = fedcba0987654321fedcba0987654322\n"
    "LE_LOCAL_KEY_DHK = fedcba0987654321fedcba0987654323\n"
    "LE_LOCAL_KEY_ER = fedcba0987654321fedcba0987654324\n"
    "ScanMode = 2\n"
    "DiscoveryTimeout = 120\n"
    "\n"
    "[01:02:03:ab:cd:ea]\n"
    "name = hello world\n"
    "LinkKey = fedcba0987654321fedcba0987654328\n";

TEST(LegacyConfigFileTest, read_test) {
  auto temp_dir = std::filesystem::temp_directory_path();
  auto temp_config = temp_dir / "temp_config.txt";
  EXPECT_TRUE(WriteToFile(temp_config.string(), kReadTestConfig));

  auto config_read = LegacyConfigFile::FromPath(temp_config.string()).Read(100);
  EXPECT_TRUE(config_read);
  EXPECT_THAT(config_read->GetPersistentSections(), ElementsAre("01:02:03:ab:cd:ea"));
  EXPECT_THAT(config_read->GetProperty("Info", "FileSource"), Optional(StrEq("Empty")));
  EXPECT_THAT(config_read->GetProperty("Info", "FileSource"), Optional(StrEq("Empty")));
  EXPECT_THAT(
      config_read->GetProperty("01:02:03:ab:cd:ea", "LinkKey"), Optional(StrEq("fedcba0987654321fedcba0987654328")));

  EXPECT_TRUE(std::filesystem::remove(temp_config));
}

static const std::string kWriteTestConfig =
    "[Info]\n"
    "FileSource = Empty\n"
    "TimeCreated = \n"
    "\n"
    "[Adapter]\n"
    "Address = 01:02:03:ab:cd:ef\n"
    "\n"
    "[01:02:03:ab:cd:ea]\n"
    "Name = hello world\n"
    "LinkKey = fedcba0987654321fedcba0987654328\n"
    "\n";

TEST(LegacyConfigFileTest, write_test) {
  auto temp_dir = std::filesystem::temp_directory_path();
  auto temp_config = temp_dir / "temp_config.txt";

  ConfigCache config(100, Device::kLinkKeyProperties);
  config.SetProperty("Info", "FileSource", "Empty");
  config.SetProperty("Info", "TimeCreated", "");
  config.SetProperty(BTIF_STORAGE_SECTION_ADAPTER, BTIF_STORAGE_KEY_ADDRESS, "01:02:03:ab:cd:ef");
  config.SetProperty("01:02:03:ab:cd:ea", BTIF_STORAGE_KEY_NAME, "hello world");
  config.SetProperty(
      "01:02:03:ab:cd:ea", BTIF_STORAGE_KEY_LINK_KEY, "fedcba0987654321fedcba0987654328");
  EXPECT_TRUE(LegacyConfigFile::FromPath(temp_config.string()).Write(config));

  EXPECT_THAT(ReadSmallFile(temp_config.string()), Optional(StrEq(kWriteTestConfig)));

  EXPECT_TRUE(std::filesystem::remove(temp_config));
}

static const std::string kConfigWithDuplicateSectionAndKey =
    "                                                                                \n\
first_key=value                                                                      \n\
                                                                                     \n\
# Device ID (DID) configuration                                                      \n\
[DID]                                                                                \n\
                                                                                     \n\
# Record Number: 1, 2 or 3 - maximum of 3 records                                    \n\
recordNumber = 1                                                                     \n\
                                                                                     \n\
# Primary Record - true or false (default)                                           \n\
# There can be only one primary record                                               \n\
primaryRecord = true                                                                 \n\
                                                                                     \n\
# Vendor ID '0xFFFF' indicates no Device ID Service Record is present in the device  \n\
# 0x000F = Broadcom Corporation (default)                                            \n\
#vendorId = 0x000F                                                                   \n\
                                                                                     \n\
# Vendor ID Source                                                                   \n\
# 0x0001 = Bluetooth SIG assigned Device ID Vendor ID value (default)                \n\
# 0x0002 = USB Implementer's Forum assigned Device ID Vendor ID value                \n\
#vendorIdSource = 0x0001                                                             \n\
                                                                                     \n\
# Product ID & Product Version                                                       \n\
# Per spec DID v1.3 0xJJMN for version is interpreted as JJ.M.N                      \n\
# JJ: major version number, M: minor version number, N: sub-minor version number     \n\
# For example: 1200, v14.3.6                                                         \n\
productId = 0x1200                                                                   \n\
version = 0x1111                                                                     \n\
                                                                                     \n\
# Optional attributes                                                                \n\
#clientExecutableURL =                                                               \n\
#serviceDescription =                                                                \n\
#documentationURL =                                                                  \n\
                                                                                     \n\
# Additional optional DID records. Bluedroid supports up to 3 records.               \n\
[DID]                                                                                \n\
[DID]                                                                                \n\
version = 0x1436                                                                     \n\
                                                                                     \n\
HiSyncId = 18446744073709551615                                                      \n\
HiSyncId2 = 15001900                                                                 \n\
";

TEST(LegacyConfigFileTest, duplicate_section_and_key_test) {
  auto temp_dir = std::filesystem::temp_directory_path();
  auto temp_config = temp_dir / "temp_config.txt";
  ASSERT_TRUE(WriteToFile(temp_config.string(), kConfigWithDuplicateSectionAndKey));

  auto config_read = LegacyConfigFile::FromPath(temp_config.string()).Read(100);
  ASSERT_TRUE(config_read);
  EXPECT_THAT(config_read->GetProperty(ConfigCache::kDefaultSectionName, "first_key"), Optional(StrEq("value")));
  // All sections with the same name merge into the same key-value pair
  EXPECT_THAT(config_read->GetProperty("DID", "primaryRecord"), Optional(StrEq("true")));
  // When keys are repeated, the later one wins
  EXPECT_THAT(config_read->GetProperty("DID", "version"), Optional(StrEq("0x1436")));

  EXPECT_TRUE(std::filesystem::remove(temp_config));
}

}  // namespace testing