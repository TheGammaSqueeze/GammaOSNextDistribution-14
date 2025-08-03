/*
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
 */
#include "security/record/security_record_storage.h"

#include "storage/classic_device.h"
#include "storage/le_device.h"
#include "storage/mutation.h"

namespace bluetooth {
namespace security {
namespace record {

namespace {
void SetClassicData(
    storage::Mutation& mutation, std::shared_ptr<record::SecurityRecord> record, storage::Device& device) {
  if (*device.GetDeviceType() == hci::DeviceType::LE) {
    return;
  }
  if (record->IsClassicLinkKeyValid()) {
    LOG_WARN("Type: %d", static_cast<int>(*device.GetDeviceType()));
    mutation.Add(device.Classic().SetLinkKey(record->GetLinkKey()));
    mutation.Add(device.Classic().SetLinkKeyType(record->GetKeyType()));
  }
}

void SetLeData(storage::Mutation& mutation, std::shared_ptr<record::SecurityRecord> record, storage::Device& device) {
  if (*device.GetDeviceType() == hci::DeviceType::BR_EDR) {
    return;
  }

  auto le_device = device.Le();

  if (record->identity_address_) {
    mutation.Add(le_device.SetAddressType(record->identity_address_->GetAddressType()));
  }

  if (record->remote_irk) {
    std::array<uint8_t, 23> peerid;
    std::copy_n(record->remote_irk->data(), record->remote_irk->size(), peerid.data());
    peerid[16] = static_cast<uint8_t>(record->identity_address_->GetAddressType());
    std::copy_n(record->identity_address_->GetAddress().data(), 6, peerid.data() + 17);

    common::ByteArray<23> byte_array(peerid);
    mutation.Add(le_device.SetPeerId(byte_array.ToString()));
  }

  if (record->pseudo_address_) {
    mutation.Add(le_device.SetLegacyPseudoAddress(record->pseudo_address_->GetAddress()));
  }

  if (record->remote_ltk) {
    std::array<uint8_t, 28> penc_keys;

    std::copy_n(record->remote_ltk->data(), record->remote_ltk->size(), penc_keys.data());
    std::copy_n(record->remote_rand->data(), record->remote_rand->size(), penc_keys.data() + 16);
    uint16_t* ediv_location = (uint16_t*)(penc_keys.data() + 24);
    *ediv_location = *record->remote_ediv;
    penc_keys[26] = record->security_level;
    penc_keys[27] = record->key_size;

    common::ByteArray<28> byte_array(penc_keys);
    mutation.Add(le_device.SetPeerEncryptionKeys(byte_array.ToString()));
  }

  if (record->remote_signature_key) {
    std::array<uint8_t, 21> psrk_keys;

    // four bytes counter, all zeros
    *psrk_keys.data() = 0;
    *(psrk_keys.data() + 1) = 0;
    *(psrk_keys.data() + 2) = 0;
    *(psrk_keys.data() + 3) = 0;
    std::copy_n(record->remote_signature_key->data(), record->remote_signature_key->size(), psrk_keys.data() + 4);
    *(psrk_keys.data() + 20) = record->security_level;

    common::ByteArray<21> byte_array(psrk_keys);
    mutation.Add(le_device.SetPeerSignatureResolvingKeys(byte_array.ToString()));
  }
}

void SetAuthenticationData(
    storage::Mutation& /* mutation */,
    std::shared_ptr<record::SecurityRecord> record,
    storage::Device& device) {
  device.SetIsAuthenticated((record->IsAuthenticated() ? 1 : 0));
  device.SetIsEncryptionRequired((record->IsEncryptionRequired() ? 1 : 0));
  device.SetRequiresMitmProtection(record->RequiresMitmProtection() ? 1 : 0);
}
}  // namespace

SecurityRecordStorage::SecurityRecordStorage(storage::StorageModule* storage_module, os::Handler* handler)
    : storage_module_(storage_module), handler_(handler) {}

void SecurityRecordStorage::SaveSecurityRecords(std::set<std::shared_ptr<record::SecurityRecord>>* records) {
  for (auto record : *records) {
    if (record->IsTemporary()) continue;
    storage::Device device = storage_module_->GetDeviceByClassicMacAddress(record->GetPseudoAddress()->GetAddress());
    auto mutation = storage_module_->Modify();

    if (record->IsClassicLinkKeyValid() && !record->identity_address_) {
      mutation.Add(device.SetDeviceType(hci::DeviceType::BR_EDR));
    } else if (record->IsClassicLinkKeyValid() && record->remote_ltk) {
      mutation.Add(device.SetDeviceType(hci::DeviceType::DUAL));
    } else if (!record->IsClassicLinkKeyValid() && record->remote_ltk) {
      mutation.Add(device.SetDeviceType(hci::DeviceType::LE));
    } else {
      mutation.Add(device.SetDeviceType(hci::DeviceType::LE));
      LOG_WARN(
          "Cannot determine device type from security record for '%s'; defaulting to LE",
          ADDRESS_TO_LOGGABLE_CSTR(*record->GetPseudoAddress()));
    }
    mutation.Commit();
    SetClassicData(mutation, record, device);
    SetLeData(mutation, record, device);
    SetAuthenticationData(mutation, record, device);
    mutation.Commit();
  }
}

void SecurityRecordStorage::LoadSecurityRecords(std::set<std::shared_ptr<record::SecurityRecord>>* records) {
  for (auto device : storage_module_->GetBondedDevices()) {
    auto address_type = (device.GetDeviceType() == hci::DeviceType::BR_EDR) ? hci::AddressType::PUBLIC_DEVICE_ADDRESS
                                                                            : device.Le().GetAddressType();
    auto address_with_type = hci::AddressWithType(device.GetAddress(), *address_type);

    auto record = std::make_shared<record::SecurityRecord>(address_with_type);
    if (device.GetDeviceType() != hci::DeviceType::LE) {
      record->SetLinkKey(device.Classic().GetLinkKey()->bytes, *device.Classic().GetLinkKeyType());
    }
    if (device.GetDeviceType() != hci::DeviceType::BR_EDR) {
      record->pseudo_address_ = std::make_optional<hci::AddressWithType>(
          *device.Le().GetLegacyPseudoAddress(), *device.Le().GetAddressType());

      if (device.Le().GetPeerId()) {
        auto peerid = common::ByteArray<23>::FromString(*device.Le().GetPeerId());
        record->remote_irk = std::make_optional<std::array<uint8_t, 16>>();
        std::copy_n(peerid->data(), record->remote_irk->size(), record->remote_irk->data());

        uint8_t idaddress_type;
        hci::Address idaddress;
        std::copy_n(peerid->data() + 16, 1, &idaddress_type);
        std::copy_n(peerid->data() + 17, 6, idaddress.data());
        record->identity_address_ =
            std::make_optional<hci::AddressWithType>(idaddress, static_cast<hci::AddressType>(idaddress_type));
      }

      if (device.Le().GetPeerEncryptionKeys()) {
        auto peer_encryption_keys = common::ByteArray<28>::FromString(*device.Le().GetPeerEncryptionKeys());
        record->remote_ltk = std::make_optional<std::array<uint8_t, 16>>();
        record->remote_rand = std::make_optional<std::array<uint8_t, 8>>();
        record->remote_ediv = std::make_optional(0);

        std::copy_n(peer_encryption_keys->data(), 16, record->remote_ltk->data());
        std::copy_n(peer_encryption_keys->data() + 16, 8, record->remote_rand->data());
        std::copy_n(peer_encryption_keys->data() + 24, 2, &(*record->remote_ediv));
        record->security_level = peer_encryption_keys->data()[26];
        record->key_size = peer_encryption_keys->data()[27];
      }

      if (device.Le().GetPeerSignatureResolvingKeys()) {
        auto peer_signature_resolving_keys =
            common::ByteArray<21>::FromString(*device.Le().GetPeerSignatureResolvingKeys());
        record->remote_signature_key = std::make_optional<std::array<uint8_t, 16>>();

        std::copy_n(peer_signature_resolving_keys->data() + 4, 16, record->remote_signature_key->data());
        record->security_level = peer_signature_resolving_keys->data()[20];
      }
    }
    record->SetIsEncryptionRequired(device.GetIsEncryptionRequired() == 1 ? true : false);
    record->SetAuthenticated(device.GetIsAuthenticated() == 1 ? true : false);
    record->SetRequiresMitmProtection(device.GetRequiresMitmProtection() == 1 ? true : false);
    records->insert(record);
  }
}

void SecurityRecordStorage::RemoveDevice(hci::AddressWithType address) {
  storage::Device device = storage_module_->GetDeviceByClassicMacAddress(address.GetAddress());
  auto mutation = storage_module_->Modify();
  mutation.Add(device.RemoveFromConfig());
  mutation.Commit();
}

}  // namespace record
}  // namespace security
}  // namespace bluetooth
