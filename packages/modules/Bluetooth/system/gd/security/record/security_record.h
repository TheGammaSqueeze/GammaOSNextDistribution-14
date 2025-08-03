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

#pragma once

#include "hci/address_with_type.h"
#include "hci/octets.h"

namespace bluetooth {
namespace security {
namespace record {

class SecurityRecord {
 public:
  explicit SecurityRecord(hci::AddressWithType address) : pseudo_address_(address) {}

  SecurityRecord& operator=(const SecurityRecord& other) = default;

  /**
   * Returns true if a device is currently pairing to another device
   */
  bool IsPairing() const {
    return pairing_;
  }

  /* Link key has been exchanged, but not stored */
  bool IsPaired() const {
    return IsClassicLinkKeyValid();
  }

  void SetLinkKey(std::array<uint8_t, 16> link_key, hci::KeyType key_type) {
    link_key_ = link_key;
    key_type_ = key_type;
    CancelPairing();
  }

  void CancelPairing() {
    pairing_ = false;
  }

  std::array<uint8_t, 16> GetLinkKey() {
    ASSERT(IsClassicLinkKeyValid());
    return link_key_;
  }

  hci::KeyType GetKeyType() {
    ASSERT(IsClassicLinkKeyValid());
    return key_type_;
  }

  std::optional<hci::AddressWithType> GetPseudoAddress() {
    return pseudo_address_;
  }

  void SetAuthenticated(bool is_authenticated) {
    this->is_authenticated_ = is_authenticated;
  }

  bool IsAuthenticated() {
    return this->is_authenticated_;
  }

  void SetRequiresMitmProtection(bool requires_mitm_protection) {
    this->requires_mitm_protection_ = requires_mitm_protection;
  }

  bool RequiresMitmProtection() {
    return this->requires_mitm_protection_;
  }

  void SetIsEncryptionRequired(bool is_encryption_required) {
    this->is_encryption_required_ = is_encryption_required;
  }

  bool IsEncryptionRequired() {
    return this->is_encryption_required_;
  }

  bool IsClassicLinkKeyValid() const {
    return !std::all_of(link_key_.begin(), link_key_.end(), [](uint8_t b) { return b == 0; });
  }

  void SetIsTemporary(bool is_temp) {
    this->is_temporary_ = is_temp;
  }

  bool IsTemporary() {
    return this->is_temporary_;
  }

 private:

  std::array<uint8_t, 16> link_key_ = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
  hci::KeyType key_type_ = hci::KeyType::DEBUG_COMBINATION;

  bool is_temporary_ = false;
  bool pairing_ = false;
  bool is_authenticated_ = false;
  bool requires_mitm_protection_ = false;
  bool is_encryption_required_ = false;

 public:
  /* First address we have ever seen this device with, that we used to create bond */
  std::optional<hci::AddressWithType> pseudo_address_;

  /* Identity Address */
  std::optional<hci::AddressWithType> identity_address_;

  std::optional<hci::Octet16> remote_ltk;
  uint8_t key_size;
  uint8_t security_level;
  std::optional<uint16_t> remote_ediv;
  std::optional<std::array<uint8_t, 8>> remote_rand;
  std::optional<hci::Octet16> remote_irk;
  std::optional<hci::Octet16> remote_signature_key;

  std::optional<hci::Octet16> local_ltk;
  std::optional<uint16_t> local_ediv;
  std::optional<std::array<uint8_t, 8>> local_rand;
};

}  // namespace record
}  // namespace security
}  // namespace bluetooth
