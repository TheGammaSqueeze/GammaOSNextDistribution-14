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

#pragma once

#include <string>
#include <vector>

#include "hci/address_with_type.h"

namespace bluetooth {
namespace security {

class ConfirmationData {
 public:
  ConfirmationData() : address_with_type_(hci::AddressWithType()), name_("No name set") {}
  ConfirmationData(bluetooth::hci::AddressWithType address_with_type, std::string name)
      : address_with_type_(address_with_type), name_(name) {}
  ConfirmationData(bluetooth::hci::AddressWithType address_with_type, std::string name, uint32_t numeric_value)
      : address_with_type_(address_with_type), name_(name), numeric_value_(numeric_value) {}

  const bluetooth::hci::AddressWithType& GetAddressWithType() {
    return address_with_type_;
  }

  std::string GetName() {
    return name_;
  }

  uint32_t GetNumericValue() {
    return numeric_value_;
  }

  hci::IoCapability GetRemoteIoCaps() const {
    return remote_io_caps_;
  }
  void SetRemoteIoCaps(hci::IoCapability remote_io_caps) {
    remote_io_caps_ = remote_io_caps;
  }

  hci::AuthenticationRequirements GetRemoteAuthReqs() const {
    return remote_auth_reqs_;
  }

  void SetRemoteAuthReqs(hci::AuthenticationRequirements remote_auth_reqs) {
    remote_auth_reqs_ = remote_auth_reqs;
  }

  hci::OobDataPresent GetRemoteOobDataPresent() const {
    return remote_oob_data_present_;
  }

  void SetRemoteOobDataPresent(hci::OobDataPresent remote_oob_data_present) {
    remote_oob_data_present_ = remote_oob_data_present;
  }

  bool IsJustWorks() const {
    return just_works_;
  }

  void SetJustWorks(bool just_works) {
    just_works_ = just_works;
  }

 private:
  bluetooth::hci::AddressWithType address_with_type_;
  std::string name_;
  // Can either be the confirmation value or the passkey
  uint32_t numeric_value_ = 0;

  // TODO(optedoblivion): Revisit after shim/BTA layer is gone
  // Extra data is a hack to get data from the module to the shim
  hci::IoCapability remote_io_caps_ = hci::IoCapability::DISPLAY_YES_NO;
  hci::AuthenticationRequirements remote_auth_reqs_ = hci::AuthenticationRequirements::DEDICATED_BONDING;
  hci::OobDataPresent remote_oob_data_present_ = hci::OobDataPresent::NOT_PRESENT;
  bool just_works_ = false;
};

// Through this interface we talk to the user, asking for confirmations/acceptance.
class UI {
 public:
  virtual ~UI() = default;

  /* Remote LE device tries to initiate pairing, ask user to confirm */
  virtual void DisplayPairingPrompt(const bluetooth::hci::AddressWithType& address, std::string name) = 0;

  /* Remove the pairing prompt from DisplayPairingPrompt, i.e. remote device disconnected, or some application requested
   * bond with this device */
  virtual void Cancel(const bluetooth::hci::AddressWithType& address) = 0;

  /* Display value for Comparison, user responds yes/no */
  virtual void DisplayConfirmValue(ConfirmationData data) = 0;

  /* Display Yes/No dialog, Classic pairing, numeric comparison with NoInputNoOutput device */
  virtual void DisplayYesNoDialog(ConfirmationData data) = 0;

  /* Display a dialog box that will let user enter the Passkey */
  virtual void DisplayEnterPasskeyDialog(ConfirmationData data) = 0;

  /* Present the passkey value to the user, user compares with other device */
  virtual void DisplayPasskey(ConfirmationData data) = 0;

  /* Ask the user to enter a PIN */
  virtual void DisplayEnterPinDialog(ConfirmationData data) = 0;
};

/* Through this interface, UI provides us with user choices. */
class UICallbacks {
 public:
  virtual ~UICallbacks() = default;

  /* User accepted pairing prompt */
  virtual void OnPairingPromptAccepted(const bluetooth::hci::AddressWithType& address, bool confirmed) = 0;

  /* User confirmed that displayed value matches the value on the other device */
  virtual void OnConfirmYesNo(const bluetooth::hci::AddressWithType& address, bool confirmed) = 0;

  /* User typed the value displayed on the other device. This is either Passkey or the Confirm value */
  virtual void OnPasskeyEntry(const bluetooth::hci::AddressWithType& address, uint32_t passkey) = 0;

  /* User typed the PIN for the other device. */
  virtual void OnPinEntry(const bluetooth::hci::AddressWithType& address, std::vector<uint8_t> pin) = 0;
};

}  // namespace security
}  // namespace bluetooth
