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

#include "storage/storage_module.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <chrono>
#include <cstdio>
#include <filesystem>
#include <iomanip>
#include <optional>
#include <thread>

#include "common/bind.h"
#include "module.h"
#include "os/fake_timer/fake_timerfd.h"
#include "os/files.h"
#include "storage/config_cache.h"
#include "storage/config_keys.h"
#include "storage/device.h"
#include "storage/legacy_config_file.h"

namespace testing {

using bluetooth::TestModuleRegistry;
using bluetooth::hci::Address;
using bluetooth::os::fake_timer::fake_timerfd_advance;
using bluetooth::storage::ConfigCache;
using bluetooth::storage::Device;
using bluetooth::storage::LegacyConfigFile;
using bluetooth::storage::StorageModule;

static const std::chrono::milliseconds kTestConfigSaveDelay = std::chrono::milliseconds(100);
static const size_t kTestTempDevicesCapacity = 10;

class TestStorageModule : public StorageModule {
 public:
  TestStorageModule(
      std::string config_file_path,
      std::chrono::milliseconds config_save_delay,
      bool is_restricted_mode,
      bool is_single_user_mode)
      : StorageModule(
            std::move(config_file_path),
            config_save_delay,
            kTestTempDevicesCapacity,
            is_restricted_mode,
            is_single_user_mode) {}

  ConfigCache* GetMemoryOnlyConfigCachePublic() {
    return StorageModule::GetMemoryOnlyConfigCache();
  }

  bool HasSectionPublic(const std::string& section) const {
    return StorageModule::HasSection(section);
  }
  bool HasPropertyPublic(const std::string& section, const std::string& property) const {
    return HasProperty(section, property);
  }

  std::optional<std::string> GetPropertyPublic(
      const std::string& section, const std::string& property) const {
    return GetProperty(section, property);
  }
  void SetPropertyPublic(std::string section, std::string property, std::string value) {
    return SetProperty(section, property, value);
  }

  std::vector<std::string> GetPersistentSectionsPublic() const {
    return GetPersistentSections();
  }

  bool RemovePropertyPublic(const std::string& section, const std::string& property) {
    return RemoveProperty(section, property);
  }

  void ConvertEncryptOrDecryptKeyIfNeededPublic() {
    return ConvertEncryptOrDecryptKeyIfNeeded();
  }

  void RemoveSectionWithPropertyPublic(const std::string& property) {
    return RemoveSectionWithProperty(property);
  }

  void RemoveSectionPublic(const std::string& section) {
    return RemoveSection(section);
  }
};

class StorageModuleTest : public Test {
 protected:
  void SetUp() override {
    temp_dir_ = std::filesystem::temp_directory_path();
    temp_config_ = temp_dir_ / "temp_config.txt";
    temp_backup_config_ = temp_dir_ / "temp_config.bak";
    DeleteConfigFiles();
    ASSERT_FALSE(std::filesystem::exists(temp_config_));
    ASSERT_FALSE(std::filesystem::exists(temp_backup_config_));
  }

  void TearDown() override {
    test_registry_.StopAll();
    DeleteConfigFiles();
  }

  void DeleteConfigFiles() {
    if (std::filesystem::exists(temp_config_)) {
      ASSERT_TRUE(std::filesystem::remove(temp_config_));
    }
    if (std::filesystem::exists(temp_backup_config_)) {
      ASSERT_TRUE(std::filesystem::remove(temp_backup_config_));
    }
  }

  void FakeTimerAdvance(std::chrono::milliseconds time) {
    auto handler = test_registry_.GetTestModuleHandler(&StorageModule::Factory);
    handler->Post(bluetooth::common::BindOnce(fake_timerfd_advance, time.count()));
  }

  bool WaitForReactorIdle(std::chrono::milliseconds time) {
    bool stopped =
        test_registry_.GetTestThread().GetReactor()->WaitForIdle(std::chrono::seconds(2));
    if (!stopped) {
      return false;
    }
    FakeTimerAdvance(time);
    return test_registry_.GetTestThread().GetReactor()->WaitForIdle(std::chrono::seconds(2));
  }

  bluetooth::os::Handler* handler_;
  TestModuleRegistry test_registry_;
  std::filesystem::path temp_dir_;
  std::filesystem::path temp_config_;
  std::filesystem::path temp_backup_config_;
};

TEST_F(StorageModuleTest, empty_config_no_op_test) {
  // Verify state before test
  ASSERT_FALSE(std::filesystem::exists(temp_config_));

  // Actual test
  auto* storage = new TestStorageModule(temp_config_.string(), kTestConfigSaveDelay, false, false);
  test_registry_.InjectTestModule(&StorageModule::Factory, storage);
  test_registry_.StopAll();

  // Verify states after test
  ASSERT_TRUE(std::filesystem::exists(temp_config_));

  // Verify config after test
  auto config = LegacyConfigFile::FromPath(temp_config_.string()).Read(kTestTempDevicesCapacity);
  ASSERT_TRUE(config);
  ASSERT_TRUE(config->HasSection(StorageModule::kInfoSection));
  ASSERT_THAT(
      config->GetProperty(StorageModule::kInfoSection, StorageModule::kFileSourceProperty),
      Optional(StrEq("Empty")));
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
    "Name = hello world\n"
    "LinkKey = fedcba0987654321fedcba0987654328\n"
    "\n";

TEST_F(StorageModuleTest, read_existing_config_test) {
  ASSERT_TRUE(bluetooth::os::WriteToFile(temp_config_.string(), kReadTestConfig));
  // Actual test

  // Set up
  auto* storage = new TestStorageModule(temp_config_.string(), kTestConfigSaveDelay, false, false);
  test_registry_.InjectTestModule(&StorageModule::Factory, storage);

  // Test
  ASSERT_TRUE(storage->HasSectionPublic("Metrics"));
  ASSERT_THAT(storage->GetPersistentSectionsPublic(), ElementsAre("01:02:03:ab:cd:ea"));
  ASSERT_THAT(
      storage->GetPropertyPublic(StorageModule::kAdapterSection, BTIF_STORAGE_KEY_ADDRESS),
      Optional(StrEq("01:02:03:ab:cd:ef")));

  // Tear down
  test_registry_.StopAll();

  // Verify states after test
  ASSERT_TRUE(std::filesystem::exists(temp_config_));

  // Verify config after test
  auto config = bluetooth::os::ReadSmallFile(temp_config_.string());
  ASSERT_TRUE(config);
  ASSERT_EQ(*config, kReadTestConfig);
}

TEST_F(StorageModuleTest, save_config_test) {
  // Prepare config file
  ASSERT_TRUE(bluetooth::os::WriteToFile(temp_config_.string(), kReadTestConfig));

  // Set up
  auto* storage = new TestStorageModule(temp_config_.string(), kTestConfigSaveDelay, false, false);
  test_registry_.InjectTestModule(&StorageModule::Factory, storage);

  // Test
  // Change a property
  ASSERT_THAT(
      storage->GetPropertyPublic("01:02:03:ab:cd:ea", BTIF_STORAGE_KEY_NAME),
      Optional(StrEq("hello world")));
  storage->SetPropertyPublic("01:02:03:ab:cd:ea", BTIF_STORAGE_KEY_NAME, "foo");
  ASSERT_THAT(
      storage->GetPropertyPublic("01:02:03:ab:cd:ea", BTIF_STORAGE_KEY_NAME),
      Optional(StrEq("foo")));
  ASSERT_TRUE(WaitForReactorIdle(kTestConfigSaveDelay));

  auto config = LegacyConfigFile::FromPath(temp_config_.string()).Read(kTestTempDevicesCapacity);
  ASSERT_TRUE(config);
  ASSERT_THAT(
      config->GetProperty("01:02:03:ab:cd:ea", BTIF_STORAGE_KEY_NAME), Optional(StrEq("foo")));

  // Remove a property
  storage->RemovePropertyPublic("01:02:03:ab:cd:ea", BTIF_STORAGE_KEY_NAME);
  ASSERT_TRUE(WaitForReactorIdle(kTestConfigSaveDelay));
  LOG_INFO("After waiting 2");
  config = LegacyConfigFile::FromPath(temp_config_.string()).Read(kTestTempDevicesCapacity);
  ASSERT_TRUE(config);
  ASSERT_FALSE(config->HasProperty("01:02:03:ab:cd:ea", BTIF_STORAGE_KEY_NAME));

  // Remove a section
  storage->RemoveSectionPublic("01:02:03:ab:cd:ea");
  ASSERT_TRUE(WaitForReactorIdle(kTestConfigSaveDelay));
  LOG_INFO("After waiting 3");
  config = LegacyConfigFile::FromPath(temp_config_.string()).Read(kTestTempDevicesCapacity);
  ASSERT_TRUE(config);
  ASSERT_FALSE(config->HasSection("01:02:03:ab:cd:ea"));

  // Tear down
  test_registry_.StopAll();

  // Verify states after test
  ASSERT_TRUE(std::filesystem::exists(temp_config_));
}

TEST_F(StorageModuleTest, get_bonded_devices_test) {
  // Prepare config file
  ASSERT_TRUE(bluetooth::os::WriteToFile(temp_config_.string(), kReadTestConfig));

  // Set up
  auto* storage = new TestStorageModule(temp_config_.string(), kTestConfigSaveDelay, false, false);
  test_registry_.InjectTestModule(&StorageModule::Factory, storage);

  ASSERT_EQ(storage->GetBondedDevices().size(), 1u);
  auto address = Address::FromString("01:02:03:ab:cd:ea");
  ASSERT_EQ(address, storage->GetBondedDevices()[0].GetAddress());

  // Tear down
  test_registry_.StopAll();
}

TEST_F(StorageModuleTest, unchanged_config_causes_no_write) {
  // Prepare config file
  ASSERT_TRUE(bluetooth::os::WriteToFile(temp_config_.string(), kReadTestConfig));

  // Set up
  auto* storage = new TestStorageModule(temp_config_.string(), kTestConfigSaveDelay, false, false);
  test_registry_.InjectTestModule(&StorageModule::Factory, storage);

  ASSERT_EQ(storage->GetBondedDevices().size(), 1u);
  auto address = Address::FromString("01:02:03:ab:cd:ea");
  ASSERT_EQ(address, storage->GetBondedDevices()[0].GetAddress());

  // Remove the file after it was read, so we can check if it was written with exists()
  DeleteConfigFiles();

  // Tear down
  test_registry_.StopAll();

  ASSERT_FALSE(std::filesystem::exists(temp_config_));
}

TEST_F(StorageModuleTest, changed_config_causes_a_write) {
  // Prepare config file
  ASSERT_TRUE(bluetooth::os::WriteToFile(temp_config_.string(), kReadTestConfig));

  // Set up
  auto* storage = new TestStorageModule(temp_config_.string(), kTestConfigSaveDelay, false, false);
  test_registry_.InjectTestModule(&StorageModule::Factory, storage);

  // Remove the file after it was read, so we can check if it was written with exists()
  DeleteConfigFiles();

  // Change a property
  storage->SetPropertyPublic("01:02:03:ab:cd:ea", BTIF_STORAGE_KEY_NAME, "foo");

  ASSERT_TRUE(WaitForReactorIdle(std::chrono::milliseconds(1)));

  // Tear down
  test_registry_.StopAll();

  ASSERT_TRUE(std::filesystem::exists(temp_config_));
}

TEST_F(StorageModuleTest, no_config_causes_a_write) {
  // Set up
  auto* storage = new TestStorageModule(temp_config_.string(), kTestConfigSaveDelay, false, false);
  test_registry_.InjectTestModule(&StorageModule::Factory, storage);

  // Tear down
  test_registry_.StopAll();

  ASSERT_TRUE(std::filesystem::exists(temp_config_));
}

}  // namespace testing