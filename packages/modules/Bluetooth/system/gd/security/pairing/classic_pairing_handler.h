/*
 *
 *  Copyright 2019 The Android Open Source Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License") override;
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

#include <utility>

#include "common/callback.h"
#include "security/initial_informations.h"
#include "security/pairing/pairing_handler.h"

namespace bluetooth {
namespace security {

class ISecurityManagerListener;

namespace pairing {

class ClassicPairingHandler : public PairingHandler {
 public:
  ClassicPairingHandler(
      channel::SecurityManagerChannel* security_manager_channel,
      std::shared_ptr<record::SecurityRecord> record,
      os::Handler* security_handler,
      common::OnceCallback<void(hci::Address, PairingResultOrFailure)> complete_callback,
      UI* user_interface,
      os::Handler* user_interface_handler,
      std::string device_name,
      neighbor::NameDbModule* name_db_module)
      : PairingHandler(security_manager_channel, std::move(record), name_db_module),
        security_handler_(security_handler),
        remote_io_capability_(hci::IoCapability::DISPLAY_YES_NO),
        remote_oob_present_(hci::OobDataPresent::NOT_PRESENT),
        remote_authentication_requirements_(hci::AuthenticationRequirements::DEDICATED_BONDING_MITM_PROTECTION),
        local_io_capability_(hci::IoCapability::DISPLAY_YES_NO),
        local_oob_present_(hci::OobDataPresent::NOT_PRESENT),
        local_authentication_requirements_(hci::AuthenticationRequirements::DEDICATED_BONDING_MITM_PROTECTION),
        complete_callback_(std::move(complete_callback)),
        user_interface_(user_interface),
        user_interface_handler_(user_interface_handler),
        device_name_(std::move(device_name)) {}

  ~ClassicPairingHandler() = default;

  void Initiate(
      bool locally_initiated,
      hci::IoCapability io_capability,
      hci::AuthenticationRequirements auth_requirements,
      OobData remote_p192_oob_data,
      OobData remote_p256_oob_data) override;
  void Cancel() override;

  void OnReceive(hci::ChangeConnectionLinkKeyCompleteView packet) override;
  void OnReceive(hci::CentralLinkKeyCompleteView packet) override;
  void OnReceive(hci::PinCodeRequestView packet) override;
  void OnReceive(hci::LinkKeyRequestView packet) override;
  void OnReceive(hci::LinkKeyNotificationView packet) override;
  void OnReceive(hci::IoCapabilityRequestView packet) override;
  void OnReceive(hci::IoCapabilityResponseView packet) override;
  void OnReceive(hci::SimplePairingCompleteView packet) override;
  void OnReceive(hci::ReturnLinkKeysView packet) override;
  void OnReceive(hci::EncryptionChangeView packet) override;
  void OnReceive(hci::EncryptionKeyRefreshCompleteView packet) override;
  void OnReceive(hci::RemoteOobDataRequestView packet) override;
  void OnReceive(hci::UserPasskeyNotificationView packet) override;
  void OnReceive(hci::KeypressNotificationView packet) override;
  void OnReceive(hci::UserConfirmationRequestView packet) override;
  void OnReceive(hci::UserPasskeyRequestView packet) override;

  void OnPairingPromptAccepted(const bluetooth::hci::AddressWithType& address, bool confirmed) override;
  void OnConfirmYesNo(const bluetooth::hci::AddressWithType& address, bool confirmed) override;
  void OnPasskeyEntry(const bluetooth::hci::AddressWithType& address, uint32_t passkey) override;
  void OnPinEntry(const bluetooth::hci::AddressWithType& address, std::vector<uint8_t> pin) override;

  void OnNameRequestComplete(hci::Address address, bool success);

 private:
  void OnUserInput(bool user_input);
  void OnPasskeyInput(uint32_t passkey);
  void NotifyUiDisplayYesNo(uint32_t numeric_value);
  void NotifyUiDisplayYesNo();
  void NotifyUiDisplayPasskey(uint32_t passkey);
  void NotifyUiDisplayPasskeyInput();
  void NotifyUiDisplayPinCodeInput();
  void NotifyUiDisplayCancel();
  void UserClickedYes();
  void UserClickedNo();

  os::Handler* security_handler_ __attribute__((unused));
  hci::IoCapability remote_io_capability_;
  hci::OobDataPresent remote_oob_present_ __attribute__((unused));
  hci::AuthenticationRequirements remote_authentication_requirements_ __attribute__((unused));
  hci::IoCapability local_io_capability_;
  hci::OobDataPresent local_oob_present_ __attribute__((unused));
  hci::AuthenticationRequirements local_authentication_requirements_ __attribute__((unused));
  OobData remote_p192_oob_data_;
  OobData remote_p256_oob_data_;
  common::OnceCallback<void(hci::Address, PairingResultOrFailure)> complete_callback_;
  UI* user_interface_;
  os::Handler* user_interface_handler_;
  std::string device_name_;
  bool is_cancelled_ = false;

  bool has_gotten_io_cap_response_ = false;
  bool has_gotten_name_response_ = false;
  std::optional<hci::UserConfirmationRequestView> user_confirmation_request_ = std::nullopt;
  std::optional<hci::LinkKeyNotificationView> link_key_notification_ = std::nullopt;

  hci::ErrorCode last_status_ = hci::ErrorCode::UNKNOWN_HCI_COMMAND;
  bool locally_initiated_ = false;
  uint32_t passkey_ = 0;
  bool already_link_key_replied_ = false;
  bool secure_connections_enabled_ = true;
  bool is_legacy_pin_code_ = false;
};

}  // namespace pairing
}  // namespace security
}  // namespace bluetooth
