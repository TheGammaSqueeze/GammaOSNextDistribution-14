/*
 *  Copyright 2020 The Android Open Source Project
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
 */
#include "security/record/security_record_storage.h"

#include <gtest/gtest.h>

#include "storage/storage_module.h"

namespace bluetooth {
namespace security {
namespace record {
namespace {

static const std::chrono::milliseconds kTestConfigSaveDelay = std::chrono::milliseconds(100);

using storage::StorageModule;

class DISABLED_SecurityRecordStorageTest : public ::testing::Test {
 protected:
  void SetUp() override {
    // Make storage module
    storage_module_ =
        new StorageModule("/tmp/temp_config.txt", kTestConfigSaveDelay, 100, false, false);

    // Inject
    fake_registry_.InjectTestModule(&StorageModule::Factory, storage_module_);

    // Make storage
    record_storage_ = new record::SecurityRecordStorage(storage_module_, handler_);
  }

  void TearDown() override {
    synchronize();
    fake_registry_.StopAll();
    delete record_storage_;
  }

  void synchronize() {
    fake_registry_.SynchronizeModuleHandler(&StorageModule::Factory, std::chrono::milliseconds(20));
  }

  TestModuleRegistry fake_registry_;
  os::Thread& thread_ = fake_registry_.GetTestThread();
  os::Handler* handler_ = nullptr;
  StorageModule* storage_module_;
  record::SecurityRecordStorage* record_storage_;
};

TEST_F(DISABLED_SecurityRecordStorageTest, setup_teardown) {}

TEST_F(DISABLED_SecurityRecordStorageTest, store_security_record) {
  hci::AddressWithType remote(
      hci::Address({0x01, 0x02, 0x03, 0x04, 0x05, 0x06}), hci::AddressType::PUBLIC_DEVICE_ADDRESS);
  std::array<uint8_t, 16> link_key = {
      0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0};
  std::shared_ptr<record::SecurityRecord> record = std::make_shared<record::SecurityRecord>(remote);

  record->SetLinkKey(link_key, hci::KeyType::DEBUG_COMBINATION);
  std::set<std::shared_ptr<record::SecurityRecord>> record_set;
  record_set.insert(record);
  record_storage_->SaveSecurityRecords(&record_set);

  auto device = storage_module_->GetDeviceByClassicMacAddress(remote.GetAddress());
  ASSERT_TRUE(device.GetDeviceType());
  ASSERT_EQ(device.Classic().GetLinkKeyType(), record->GetKeyType());
  int i = 0;
  for (i = 0; i < 16; ++i) {
    ASSERT_EQ(link_key[i], device.Classic().GetLinkKey()->bytes[i]);
  }
}

TEST_F(DISABLED_SecurityRecordStorageTest, store_le_security_record) {
  hci::AddressWithType identity_address(
      hci::Address({0x01, 0x02, 0x03, 0x04, 0x05, 0x06}), hci::AddressType::RANDOM_DEVICE_ADDRESS);
  std::array<uint8_t, 16> remote_ltk{
      0x07, 0x0c, 0x0e, 0x16, 0x18, 0x55, 0xc6, 0x72, 0x64, 0x5a, 0xd8, 0xb1, 0xf6, 0x93, 0x94, 0xa7};
  uint16_t remote_ediv = 0x28;
  std::array<uint8_t, 8> remote_rand{0x48, 0xac, 0x91, 0xf4, 0xef, 0x6d, 0x41, 0x10};
  std::array<uint8_t, 16> remote_irk{
      0x66, 0x90, 0x40, 0x76, 0x27, 0x69, 0x57, 0x71, 0x0d, 0x39, 0xf7, 0x80, 0x9e, 0x2f, 0x49, 0xcf};
  std::array<uint8_t, 16> remote_signature_key{
      0x08, 0x83, 0xae, 0x44, 0xd6, 0x77, 0x9e, 0x90, 0x1d, 0x25, 0xcd, 0xd7, 0xb6, 0xf4, 0x57, 0x85};
  std::shared_ptr<record::SecurityRecord> record = std::make_shared<record::SecurityRecord>(identity_address);

  record->identity_address_ = identity_address;
  record->remote_ltk = remote_ltk;
  record->key_size = 16;
  record->security_level = 2;
  record->remote_ediv = remote_ediv;
  record->remote_rand = remote_rand;
  record->remote_irk = remote_irk;
  record->remote_signature_key = remote_signature_key;

  std::set<std::shared_ptr<record::SecurityRecord>> record_set;
  record_set.insert(record);
  record_storage_->SaveSecurityRecords(&record_set);

  auto device = storage_module_->GetDeviceByClassicMacAddress(identity_address.GetAddress());
  ASSERT_EQ(hci::DeviceType::LE, device.GetDeviceType());
  ASSERT_EQ(device.Le().GetAddressType(), identity_address.GetAddressType());

  // IRK, address type, and address glued together
  ASSERT_EQ(*device.Le().GetPeerId(), "66904076276957710d39f7809e2f49cf01010203040506");

  // LTK, RAND, EDIV and sec level glued together
  ASSERT_EQ(*device.Le().GetPeerEncryptionKeys(), "070c0e161855c672645ad8b1f69394a748ac91f4ef6d411028000210");

  // Counter, signature key, and security level glued together
  ASSERT_EQ(device.Le().GetPeerSignatureResolvingKeys(), "000000000883ae44d6779e901d25cdd7b6f4578502");
}

TEST_F(DISABLED_SecurityRecordStorageTest, load_security_record) {
  hci::AddressWithType remote(
      hci::Address({0x01, 0x02, 0x03, 0x04, 0x05, 0x06}), hci::AddressType::PUBLIC_DEVICE_ADDRESS);
  std::array<uint8_t, 16> link_key = {
      0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0};
  std::shared_ptr<record::SecurityRecord> record = std::make_shared<record::SecurityRecord>(remote);

  record->SetLinkKey(link_key, hci::KeyType::DEBUG_COMBINATION);
  std::set<std::shared_ptr<record::SecurityRecord>> record_set;
  record_set.insert(record);
  record_storage_->SaveSecurityRecords(&record_set);

  auto device = storage_module_->GetDeviceByClassicMacAddress(remote.GetAddress());
  ASSERT_TRUE(device.GetDeviceType());

  ASSERT_EQ(device.Classic().GetLinkKeyType(), record->GetKeyType());
  int i = 0;
  for (i = 0; i < 16; ++i) {
    ASSERT_EQ(link_key[i], device.Classic().GetLinkKey()->bytes[i]);
  }

  record_set.clear();
  record_storage_->LoadSecurityRecords(&record_set);
  record = *record_set.begin();
  link_key = record->GetLinkKey();

  ASSERT_EQ(device.Classic().GetLinkKeyType(), record->GetKeyType());
  ASSERT_TRUE(device.GetDeviceType());
  for (i = 0; i < 16; ++i) {
    ASSERT_EQ(link_key[i], device.Classic().GetLinkKey()->bytes[i]);
  }
}

TEST_F(DISABLED_SecurityRecordStorageTest, dont_save_temporary_records) {
  hci::AddressWithType remote(
      hci::Address({0x01, 0x02, 0x03, 0x04, 0x05, 0x06}), hci::AddressType::PUBLIC_DEVICE_ADDRESS);
  std::array<uint8_t, 16> link_key = {
      0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0};
  std::shared_ptr<record::SecurityRecord> record = std::make_shared<record::SecurityRecord>(remote);

  record->SetLinkKey(link_key, hci::KeyType::DEBUG_COMBINATION);
  record->SetIsTemporary(true);
  std::set<std::shared_ptr<record::SecurityRecord>> record_set;
  record_set.insert(record);
  record_storage_->SaveSecurityRecords(&record_set);

  auto device = storage_module_->GetDeviceByClassicMacAddress(remote.GetAddress());
  ASSERT_FALSE(device.GetDeviceType());

  record_set.clear();
  record_storage_->LoadSecurityRecords(&record_set);
  ASSERT_EQ(record_set.size(), 0);
}

TEST_F(DISABLED_SecurityRecordStorageTest, test_remove) {
  hci::AddressWithType remote(
      hci::Address({0x01, 0x02, 0x03, 0x04, 0x05, 0x06}), hci::AddressType::PUBLIC_DEVICE_ADDRESS);
  std::array<uint8_t, 16> link_key = {
      0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0};
  std::shared_ptr<record::SecurityRecord> record = std::make_shared<record::SecurityRecord>(remote);

  record->SetLinkKey(link_key, hci::KeyType::DEBUG_COMBINATION);
  std::set<std::shared_ptr<record::SecurityRecord>> record_set;
  record_set.insert(record);
  record_storage_->SaveSecurityRecords(&record_set);

  auto device = storage_module_->GetDeviceByClassicMacAddress(remote.GetAddress());
  ASSERT_TRUE(device.GetDeviceType());

  ASSERT_EQ(device.Classic().GetLinkKeyType(), record->GetKeyType());
  int i = 0;
  for (i = 0; i < 16; ++i) {
    ASSERT_EQ(link_key[i], device.Classic().GetLinkKey()->bytes[i]);
  }

  record_storage_->RemoveDevice(remote);

  record_set.clear();
  record_storage_->LoadSecurityRecords(&record_set);
  ASSERT_EQ(record_set.size(), 0);
}

}  // namespace
}  // namespace record
}  // namespace security
}  // namespace bluetooth
