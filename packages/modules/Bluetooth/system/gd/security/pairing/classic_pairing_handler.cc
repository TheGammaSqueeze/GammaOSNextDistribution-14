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
#include "security/pairing/classic_pairing_handler.h"

#include "common/bind.h"

namespace bluetooth {
namespace security {
namespace pairing {

void ClassicPairingHandler::NotifyUiDisplayYesNo(uint32_t numeric_value) {
  ASSERT(user_interface_handler_ != nullptr);
  ConfirmationData data(*GetRecord()->GetPseudoAddress(), device_name_, numeric_value);
  data.SetRemoteIoCaps(remote_io_capability_);
  data.SetRemoteAuthReqs(remote_authentication_requirements_);
  data.SetRemoteOobDataPresent(remote_oob_present_);
  user_interface_handler_->CallOn(user_interface_, &UI::DisplayConfirmValue, data);
}

void ClassicPairingHandler::NotifyUiDisplayYesNo() {
  ASSERT(user_interface_handler_ != nullptr);
  ConfirmationData data(*GetRecord()->GetPseudoAddress(), device_name_);
  data.SetRemoteIoCaps(remote_io_capability_);
  data.SetRemoteAuthReqs(remote_authentication_requirements_);
  data.SetRemoteOobDataPresent(remote_oob_present_);
  user_interface_handler_->CallOn(user_interface_, &UI::DisplayYesNoDialog, data);
}

void ClassicPairingHandler::NotifyUiDisplayPasskey(uint32_t passkey) {
  ASSERT(user_interface_handler_ != nullptr);
  ConfirmationData data(*GetRecord()->GetPseudoAddress(), device_name_, passkey);
  data.SetRemoteIoCaps(remote_io_capability_);
  data.SetRemoteAuthReqs(remote_authentication_requirements_);
  data.SetRemoteOobDataPresent(remote_oob_present_);
  user_interface_handler_->CallOn(user_interface_, &UI::DisplayPasskey, data);
}

void ClassicPairingHandler::NotifyUiDisplayPasskeyInput() {
  ASSERT(user_interface_handler_ != nullptr);
  ConfirmationData data(*GetRecord()->GetPseudoAddress(), device_name_);
  data.SetRemoteIoCaps(remote_io_capability_);
  data.SetRemoteAuthReqs(remote_authentication_requirements_);
  data.SetRemoteOobDataPresent(remote_oob_present_);
  user_interface_handler_->CallOn(user_interface_, &UI::DisplayEnterPasskeyDialog, data);
}

void ClassicPairingHandler::NotifyUiDisplayPinCodeInput() {
  ASSERT(user_interface_handler_ != nullptr);
  ConfirmationData data(*GetRecord()->GetPseudoAddress(), device_name_);
  data.SetRemoteIoCaps(remote_io_capability_);
  data.SetRemoteAuthReqs(remote_authentication_requirements_);
  data.SetRemoteOobDataPresent(remote_oob_present_);
  user_interface_handler_->CallOn(user_interface_, &UI::DisplayEnterPinDialog, data);
}

void ClassicPairingHandler::NotifyUiDisplayCancel() {
  ASSERT(user_interface_handler_ != nullptr);
  user_interface_handler_->CallOn(user_interface_, &UI::Cancel, *GetRecord()->GetPseudoAddress());
}

void ClassicPairingHandler::OnPairingPromptAccepted(
    const bluetooth::hci::AddressWithType& /* address */, bool /* confirmed */) {
  // NOTE: This is not used by Classic, only by LE
  LOG_ALWAYS_FATAL("This is not supported by Classic Pairing Handler, only LE");
}

void ClassicPairingHandler::OnConfirmYesNo(
    const bluetooth::hci::AddressWithType& /* address */, bool confirmed) {
  if (confirmed) {
    GetChannel()->SendCommand(
        hci::UserConfirmationRequestReplyBuilder::Create(GetRecord()->GetPseudoAddress()->GetAddress()));
  } else {
    GetChannel()->SendCommand(
        hci::UserConfirmationRequestNegativeReplyBuilder::Create(GetRecord()->GetPseudoAddress()->GetAddress()));
  }
}

void ClassicPairingHandler::OnPasskeyEntry(const bluetooth::hci::AddressWithType& address, uint32_t passkey) {
  GetChannel()->SendCommand(hci::UserPasskeyRequestReplyBuilder::Create(address.GetAddress(), passkey));
}

void ClassicPairingHandler::OnPinEntry(const bluetooth::hci::AddressWithType& address, std::vector<uint8_t> pin) {
  std::array<uint8_t, 16> padded_pin;
  for (size_t i = 0; i < 16 && i < pin.size(); i++) {
    padded_pin[i] = pin[i];
  }
  LOG_INFO("%s", ADDRESS_TO_LOGGABLE_CSTR(address.GetAddress()));
  GetChannel()->SendCommand(hci::PinCodeRequestReplyBuilder::Create(address.GetAddress(), pin.size(), padded_pin));
}

void ClassicPairingHandler::Initiate(
    bool locally_initiated,
    hci::IoCapability io_capability,
    hci::AuthenticationRequirements auth_requirements,
    OobData remote_p192_oob_data,
    OobData remote_p256_oob_data) {
  LOG_INFO("Initiate");
  locally_initiated_ = locally_initiated;
  local_io_capability_ = io_capability;
  local_authentication_requirements_ = auth_requirements;
  remote_p192_oob_data_ = remote_p192_oob_data;
  remote_p256_oob_data_ = remote_p256_oob_data;
  bool has192 = remote_p192_oob_data.IsValid();
  bool has256 = remote_p256_oob_data.IsValid();
  bool has_both = has192 && has256;

  if (has_both) {
    remote_oob_present_ = hci::OobDataPresent::P_192_AND_256_PRESENT;
  } else {
    if (has192) {
      remote_oob_present_ = hci::OobDataPresent::P_192_PRESENT;
    } else if (has256) {
      remote_oob_present_ = hci::OobDataPresent::P_256_PRESENT;
    }
  }

  if (locally_initiated_) {
    GetChannel()->Connect(GetRecord()->GetPseudoAddress()->GetAddress());
  }
}

void ClassicPairingHandler::OnNameRequestComplete(hci::Address address, bool /* success */) {
  if (GetNameDbModule()->IsNameCached(address)) {
    auto remote_name = GetNameDbModule()->ReadCachedRemoteName(address);
    std::string tmp_name;
    for (uint8_t i : remote_name) {
      tmp_name += i;
    }
    device_name_ = tmp_name;
  }
  has_gotten_name_response_ = true;
  // For PIN Pairing
  if (is_legacy_pin_code_) {
    NotifyUiDisplayPinCodeInput();
  }
  // For SSP/Numeric comparison flow
  if (user_confirmation_request_) {
    this->OnReceive(*user_confirmation_request_);
  }
  // For OOB Flow; we go to link key notification and must wait for name
  if (link_key_notification_) {
    this->OnReceive(*link_key_notification_);
  }
}

void ClassicPairingHandler::Cancel() {
  if (is_cancelled_) return;
  is_cancelled_ = true;
  PairingResultOrFailure result = PairingResult();
  if (last_status_ != hci::ErrorCode::SUCCESS) {
    result = PairingFailure(hci::ErrorCodeText(last_status_));
  }
  std::move(complete_callback_).Run(GetRecord()->GetPseudoAddress()->GetAddress(), result);
}

void ClassicPairingHandler::OnReceive(hci::ChangeConnectionLinkKeyCompleteView packet) {
  ASSERT(packet.IsValid());
  LOG_INFO("Received unsupported event: %s", hci::EventCodeText(packet.GetEventCode()).c_str());
}

void ClassicPairingHandler::OnReceive(hci::CentralLinkKeyCompleteView packet) {
  ASSERT(packet.IsValid());
  LOG_INFO("Received unsupported event: %s", hci::EventCodeText(packet.GetEventCode()).c_str());
}

void ClassicPairingHandler::OnReceive(hci::PinCodeRequestView packet) {
  ASSERT(packet.IsValid());
  LOG_INFO("Received: %s", hci::EventCodeText(packet.GetEventCode()).c_str());
  ASSERT_LOG(GetRecord()->GetPseudoAddress()->GetAddress() == packet.GetBdAddr(), "Address mismatch");
  is_legacy_pin_code_ = true;
  GetNameDbModule()->ReadRemoteNameRequest(
      GetRecord()->GetPseudoAddress()->GetAddress(),
      common::BindOnce(&ClassicPairingHandler::OnNameRequestComplete, common::Unretained(this)),
      security_handler_);
}

void ClassicPairingHandler::OnReceive(hci::LinkKeyRequestView packet) {
  ASSERT(packet.IsValid());
  if (already_link_key_replied_) {
    LOG_WARN("Pairing is already in progress...");
    return;
  }
  already_link_key_replied_ = true;
  LOG_INFO("Received: %s", hci::EventCodeText(packet.GetEventCode()).c_str());
  ASSERT_LOG(GetRecord()->GetPseudoAddress()->GetAddress() == packet.GetBdAddr(), "Address mismatch");
  if (GetRecord()->IsPaired()) {
    LOG_INFO("Sending: LINK_KEY_REQUEST_REPLY");
    this->GetChannel()->SendCommand(hci::LinkKeyRequestReplyBuilder::Create(
        GetRecord()->GetPseudoAddress()->GetAddress(), GetRecord()->GetLinkKey()));
    last_status_ = hci::ErrorCode::SUCCESS;
    Cancel();
  } else {
    LOG_INFO("Sending: LINK_KEY_REQUEST_NEGATIVE_REPLY");
    this->GetChannel()->SendCommand(
        hci::LinkKeyRequestNegativeReplyBuilder::Create(GetRecord()->GetPseudoAddress()->GetAddress()));
  }
}

void ClassicPairingHandler::OnReceive(hci::LinkKeyNotificationView packet) {
  ASSERT(packet.IsValid());
  LOG_INFO("Received: %s", hci::EventCodeText(packet.GetEventCode()).c_str());
  ASSERT_LOG(GetRecord()->GetPseudoAddress()->GetAddress() == packet.GetBdAddr(), "Address mismatch");
  GetRecord()->SetLinkKey(packet.GetLinkKey(), packet.GetKeyType());
  if (!has_gotten_name_response_) {
    link_key_notification_ = std::make_optional<hci::LinkKeyNotificationView>(packet);
    return;
  }
  if (is_legacy_pin_code_) {
    last_status_ = hci::ErrorCode::SUCCESS;
  }
  Cancel();
}

void ClassicPairingHandler::OnReceive(hci::IoCapabilityRequestView packet) {
  ASSERT(packet.IsValid());
  LOG_INFO("Received: %s", hci::EventCodeText(packet.GetEventCode()).c_str());
  ASSERT_LOG(GetRecord()->GetPseudoAddress()->GetAddress() == packet.GetBdAddr(), "Address mismatch");
  hci::IoCapability io_capability = local_io_capability_;
  hci::OobDataPresent oob_present = remote_oob_present_;
  hci::AuthenticationRequirements authentication_requirements = local_authentication_requirements_;
  auto reply_packet = hci::IoCapabilityRequestReplyBuilder::Create(
      GetRecord()->GetPseudoAddress()->GetAddress(), io_capability, oob_present, authentication_requirements);
  this->GetChannel()->SendCommand(std::move(reply_packet));
  GetNameDbModule()->ReadRemoteNameRequest(
      GetRecord()->GetPseudoAddress()->GetAddress(),
      common::BindOnce(&ClassicPairingHandler::OnNameRequestComplete, common::Unretained(this)),
      security_handler_);
}

void ClassicPairingHandler::OnReceive(hci::IoCapabilityResponseView packet) {
  ASSERT(packet.IsValid());
  LOG_INFO("Received: %s", hci::EventCodeText(packet.GetEventCode()).c_str());
  ASSERT_LOG(GetRecord()->GetPseudoAddress()->GetAddress() == packet.GetBdAddr(), "Address mismatch");

  remote_io_capability_ = packet.GetIoCapability();
  remote_authentication_requirements_ = packet.GetAuthenticationRequirements();

  switch (remote_authentication_requirements_) {
    case hci::AuthenticationRequirements::NO_BONDING:
      GetRecord()->SetIsEncryptionRequired(
          local_authentication_requirements_ != hci::AuthenticationRequirements::NO_BONDING ||
          local_authentication_requirements_ != hci::AuthenticationRequirements::NO_BONDING_MITM_PROTECTION);
      GetRecord()->SetRequiresMitmProtection(
          local_authentication_requirements_ == hci::AuthenticationRequirements::DEDICATED_BONDING_MITM_PROTECTION ||
          local_authentication_requirements_ == hci::AuthenticationRequirements::GENERAL_BONDING_MITM_PROTECTION ||
          local_authentication_requirements_ == hci::AuthenticationRequirements::NO_BONDING_MITM_PROTECTION);
      // TODO(optedoblivion): check for HID device (CoD) and if HID don't make temporary
      GetRecord()->SetIsTemporary(
          local_authentication_requirements_ == hci::AuthenticationRequirements::NO_BONDING ||
          local_authentication_requirements_ == hci::AuthenticationRequirements::NO_BONDING_MITM_PROTECTION);
      break;
    case hci::AuthenticationRequirements::NO_BONDING_MITM_PROTECTION:
      GetRecord()->SetIsEncryptionRequired(
          local_authentication_requirements_ != hci::AuthenticationRequirements::NO_BONDING ||
          local_authentication_requirements_ != hci::AuthenticationRequirements::NO_BONDING_MITM_PROTECTION);
      GetRecord()->SetRequiresMitmProtection(true);
      GetRecord()->SetIsTemporary(
          local_authentication_requirements_ == hci::AuthenticationRequirements::NO_BONDING ||
          local_authentication_requirements_ == hci::AuthenticationRequirements::NO_BONDING_MITM_PROTECTION);
      break;
    case hci::AuthenticationRequirements::DEDICATED_BONDING:
      GetRecord()->SetIsEncryptionRequired(true);
      GetRecord()->SetRequiresMitmProtection(
          local_authentication_requirements_ == hci::AuthenticationRequirements::DEDICATED_BONDING_MITM_PROTECTION ||
          local_authentication_requirements_ == hci::AuthenticationRequirements::GENERAL_BONDING_MITM_PROTECTION ||
          local_authentication_requirements_ == hci::AuthenticationRequirements::NO_BONDING_MITM_PROTECTION);
      break;
    case hci::AuthenticationRequirements::DEDICATED_BONDING_MITM_PROTECTION:
      GetRecord()->SetIsEncryptionRequired(true);
      GetRecord()->SetRequiresMitmProtection(true);
      break;
    case hci::AuthenticationRequirements::GENERAL_BONDING:
      GetRecord()->SetIsEncryptionRequired(true);
      GetRecord()->SetRequiresMitmProtection(
          local_authentication_requirements_ == hci::AuthenticationRequirements::DEDICATED_BONDING_MITM_PROTECTION ||
          local_authentication_requirements_ == hci::AuthenticationRequirements::GENERAL_BONDING_MITM_PROTECTION ||
          local_authentication_requirements_ == hci::AuthenticationRequirements::NO_BONDING_MITM_PROTECTION);
      break;
    case hci::AuthenticationRequirements::GENERAL_BONDING_MITM_PROTECTION:
      GetRecord()->SetIsEncryptionRequired(true);
      GetRecord()->SetRequiresMitmProtection(true);
      break;
    default:
      GetRecord()->SetIsEncryptionRequired(true);
      GetRecord()->SetRequiresMitmProtection(true);
      break;
  }

  has_gotten_io_cap_response_ = true;
  if (user_confirmation_request_) {
    this->OnReceive(*user_confirmation_request_);
  }
}

void ClassicPairingHandler::OnReceive(hci::SimplePairingCompleteView packet) {
  ASSERT(packet.IsValid());
  LOG_INFO("Received: %s", hci::EventCodeText(packet.GetEventCode()).c_str());
  ASSERT_LOG(GetRecord()->GetPseudoAddress()->GetAddress() == packet.GetBdAddr(), "Address mismatch");
  last_status_ = packet.GetStatus();
  if (last_status_ != hci::ErrorCode::SUCCESS) {
    LOG_INFO("Failed SimplePairingComplete: %s", hci::ErrorCodeText(last_status_).c_str());
    // Cancel here since we won't get LinkKeyNotification
    Cancel();
  }
}

void ClassicPairingHandler::OnReceive(hci::ReturnLinkKeysView packet) {
  ASSERT(packet.IsValid());
  LOG_INFO("Received: %s", hci::EventCodeText(packet.GetEventCode()).c_str());
}

void ClassicPairingHandler::OnReceive(hci::EncryptionChangeView packet) {
  ASSERT(packet.IsValid());
  LOG_INFO("Received: %s", hci::EventCodeText(packet.GetEventCode()).c_str());
}

void ClassicPairingHandler::OnReceive(hci::EncryptionKeyRefreshCompleteView packet) {
  ASSERT(packet.IsValid());
  LOG_INFO("Received: %s", hci::EventCodeText(packet.GetEventCode()).c_str());
}

void ClassicPairingHandler::OnReceive(hci::RemoteOobDataRequestView packet) {
  ASSERT(packet.IsValid());
  LOG_INFO("Received: %s", hci::EventCodeText(packet.GetEventCode()).c_str());
  ASSERT_LOG(GetRecord()->GetPseudoAddress()->GetAddress() == packet.GetBdAddr(), "Address mismatch");

  // Corev5.2 V2PF
  switch (remote_oob_present_) {
    case hci::OobDataPresent::NOT_PRESENT:
      LOG_WARN("Missing remote OOB data");
      GetChannel()->SendCommand(
          hci::RemoteOobDataRequestNegativeReplyBuilder::Create(GetRecord()->GetPseudoAddress()->GetAddress()));
      break;
    case hci::OobDataPresent::P_192_PRESENT:
      LOG_INFO("P192 Present");
      // TODO(optedoblivion): Figure this out and remove
      secure_connections_enabled_ = false;
      if (secure_connections_enabled_) {
        GetChannel()->SendCommand(hci::RemoteOobExtendedDataRequestReplyBuilder::Create(
            GetRecord()->GetPseudoAddress()->GetAddress(),
            this->remote_p192_oob_data_.GetC(),
            this->remote_p192_oob_data_.GetR(),
            this->remote_p256_oob_data_.GetC(),
            this->remote_p256_oob_data_.GetR()));
      } else {
        GetChannel()->SendCommand(hci::RemoteOobDataRequestReplyBuilder::Create(
            GetRecord()->GetPseudoAddress()->GetAddress(),
            this->remote_p192_oob_data_.GetC(),
            this->remote_p192_oob_data_.GetR()));
      }
      break;
    case hci::OobDataPresent::P_256_PRESENT:
      LOG_INFO("P256 Present");
      GetChannel()->SendCommand(hci::RemoteOobExtendedDataRequestReplyBuilder::Create(
          GetRecord()->GetPseudoAddress()->GetAddress(),
          this->remote_p192_oob_data_.GetC(),
          this->remote_p192_oob_data_.GetR(),
          this->remote_p256_oob_data_.GetC(),
          this->remote_p256_oob_data_.GetR()));
      break;
    case hci::OobDataPresent::P_192_AND_256_PRESENT:
      LOG_INFO("P192 and P256 Present");
      GetChannel()->SendCommand(hci::RemoteOobExtendedDataRequestReplyBuilder::Create(
          GetRecord()->GetPseudoAddress()->GetAddress(),
          this->remote_p192_oob_data_.GetC(),
          this->remote_p192_oob_data_.GetR(),
          this->remote_p256_oob_data_.GetC(),
          this->remote_p256_oob_data_.GetR()));
      break;
  }
}

void ClassicPairingHandler::OnReceive(hci::UserPasskeyNotificationView packet) {
  ASSERT(packet.IsValid());
  LOG_INFO("Received: %s", hci::EventCodeText(packet.GetEventCode()).c_str());
  ASSERT_LOG(GetRecord()->GetPseudoAddress()->GetAddress() == packet.GetBdAddr(), "Address mismatch");
  NotifyUiDisplayPasskey(packet.GetPasskey());
}

void ClassicPairingHandler::OnReceive(hci::KeypressNotificationView packet) {
  ASSERT(packet.IsValid());
  LOG_INFO("Received: %s", hci::EventCodeText(packet.GetEventCode()).c_str());
  LOG_INFO("Notification Type: %s", hci::KeypressNotificationTypeText(packet.GetNotificationType()).c_str());
  switch (packet.GetNotificationType()) {
    case hci::KeypressNotificationType::ENTRY_STARTED:
      // Tell the UI to highlight the first digit
      break;
    case hci::KeypressNotificationType::DIGIT_ENTERED:
      // Tell the UI to move one digit to the right
      break;
    case hci::KeypressNotificationType::DIGIT_ERASED:
      // Tell the UI to move back one digit
      break;
    case hci::KeypressNotificationType::CLEARED:
      // Tell the UI to highlight the first digit again
      break;
    case hci::KeypressNotificationType::ENTRY_COMPLETED:
      // Tell the UI to hide the dialog
      break;
  }
}

/**
 * Here we decide what type of pairing authentication method we will use
 *
 * The table is on pg 2133 of the Core v5.1 spec.
 */

void ClassicPairingHandler::OnReceive(hci::UserConfirmationRequestView packet) {
  // Ensure we have io cap response otherwise checks will be wrong if it comes late
  // Ensure we have the name response otherwise we cannot show a name for the device to the user
  if (!has_gotten_io_cap_response_ || !has_gotten_name_response_) {
    user_confirmation_request_ = std::make_optional<hci::UserConfirmationRequestView>(packet);
    return;
  }
  ASSERT(packet.IsValid());
  LOG_INFO("Received: %s", hci::EventCodeText(packet.GetEventCode()).c_str());
  ASSERT_LOG(GetRecord()->GetPseudoAddress()->GetAddress() == packet.GetBdAddr(), "Address mismatch");
  // if locally_initialized, use default, otherwise us remote io caps
  hci::IoCapability initiator_io_capability = (locally_initiated_) ? local_io_capability_ : remote_io_capability_;
  hci::IoCapability responder_io_capability = (!locally_initiated_) ? local_io_capability_ : remote_io_capability_;
  switch (initiator_io_capability) {
    case hci::IoCapability::DISPLAY_ONLY:
      switch (responder_io_capability) {
        case hci::IoCapability::DISPLAY_ONLY:
          // NumericComparison, Both auto confirm
          LOG_INFO("Numeric Comparison: A and B auto confirm");
          if (!GetRecord()->RequiresMitmProtection()) {
            GetChannel()->SendCommand(
                hci::UserConfirmationRequestReplyBuilder::Create(GetRecord()->GetPseudoAddress()->GetAddress()));
            // NOTE(optedoblivion) BTA needs a callback for when auto accepting JustWorks
            // If we auto accept from the ClassicPairingHandler in GD then we won't
            // get a callback to this shim function.
            // We will have to call it anyway until we eliminate the need
            // TODO(optedoblivion): REMOVE WHEN SHIM LEAVES
            NotifyUiDisplayYesNo();
          } else {
            GetChannel()->SendCommand(hci::UserConfirmationRequestNegativeReplyBuilder::Create(
                GetRecord()->GetPseudoAddress()->GetAddress()));
          }
          // Unauthenticated
          GetRecord()->SetAuthenticated(false);
          break;
        case hci::IoCapability::DISPLAY_YES_NO:
          // NumericComparison, Initiator auto confirm, Responder display
          if (!GetRecord()->RequiresMitmProtection()) {
            GetChannel()->SendCommand(
                hci::UserConfirmationRequestReplyBuilder::Create(GetRecord()->GetPseudoAddress()->GetAddress()));
            // TODO(optedoblivion): REMOVE WHEN SHIM LEAVES
            NotifyUiDisplayYesNo();
          } else {
            GetChannel()->SendCommand(hci::UserConfirmationRequestNegativeReplyBuilder::Create(
                GetRecord()->GetPseudoAddress()->GetAddress()));
          }
          LOG_INFO("Numeric Comparison: A auto confirm");
          // Unauthenticated
          GetRecord()->SetAuthenticated(true);
          break;
        case hci::IoCapability::KEYBOARD_ONLY:
          // PassKey Entry, Initiator display, Responder input
          NotifyUiDisplayPasskey(packet.GetNumericValue());
          LOG_INFO("Passkey Entry: A display, B input");
          // Authenticated
          GetRecord()->SetAuthenticated(true);
          break;
        case hci::IoCapability::NO_INPUT_NO_OUTPUT:
          // NumericComparison, Both auto confirm
          LOG_INFO("Numeric Comparison: A and B auto confirm");
          if (!GetRecord()->RequiresMitmProtection()) {
            GetChannel()->SendCommand(
                hci::UserConfirmationRequestReplyBuilder::Create(GetRecord()->GetPseudoAddress()->GetAddress()));
            // TODO(optedoblivion): REMOVE WHEN SHIM LEAVES
            NotifyUiDisplayYesNo();
          } else {
            GetChannel()->SendCommand(hci::UserConfirmationRequestNegativeReplyBuilder::Create(
                GetRecord()->GetPseudoAddress()->GetAddress()));
          }
          // Unauthenticated
          GetRecord()->SetAuthenticated(true);
          break;
      }
      break;
    case hci::IoCapability::DISPLAY_YES_NO:
      switch (responder_io_capability) {
        case hci::IoCapability::DISPLAY_ONLY:
          // NumericComparison, Initiator display, Responder auto confirm
          LOG_INFO("Numeric Comparison: A DisplayYesNo, B auto confirm");
          NotifyUiDisplayYesNo(packet.GetNumericValue());
          // Unauthenticated
          GetRecord()->SetAuthenticated(true);
          break;
        case hci::IoCapability::DISPLAY_YES_NO:
          // NumericComparison Both Display, Both confirm
          LOG_INFO("Numeric Comparison: A and B DisplayYesNo");
          NotifyUiDisplayYesNo(packet.GetNumericValue());
          // Authenticated
          GetRecord()->SetAuthenticated(true);
          break;
        case hci::IoCapability::KEYBOARD_ONLY:
          // PassKey Entry, Initiator display, Responder input
          NotifyUiDisplayPasskey(packet.GetNumericValue());
          LOG_INFO("Passkey Entry: A display, B input");
          // Authenticated
          GetRecord()->SetAuthenticated(true);
          break;
        case hci::IoCapability::NO_INPUT_NO_OUTPUT:
          // NumericComparison, auto confirm Responder, Yes/No confirm Initiator. Don't show confirmation value
          LOG_INFO("Numeric Comparison: A DisplayYesNo, B auto confirm, no show value");
          NotifyUiDisplayYesNo();
          // Unauthenticated
          GetRecord()->SetAuthenticated(true);
          break;
      }
      break;
    case hci::IoCapability::KEYBOARD_ONLY:
      switch (responder_io_capability) {
        case hci::IoCapability::DISPLAY_ONLY:
          // PassKey Entry, Responder display, Initiator input
          NotifyUiDisplayPasskeyInput();
          LOG_INFO("Passkey Entry: A input, B display");
          // Authenticated
          GetRecord()->SetAuthenticated(true);
          break;
        case hci::IoCapability::DISPLAY_YES_NO:
          // PassKey Entry, Responder display, Initiator input
          NotifyUiDisplayPasskeyInput();
          LOG_INFO("Passkey Entry: A input, B display");
          // Authenticated
          GetRecord()->SetAuthenticated(true);
          break;
        case hci::IoCapability::KEYBOARD_ONLY:
          // PassKey Entry, both input
          NotifyUiDisplayPasskeyInput();
          LOG_INFO("Passkey Entry: A input, B input");
          // Authenticated
          GetRecord()->SetAuthenticated(true);
          break;
        case hci::IoCapability::NO_INPUT_NO_OUTPUT:
          // NumericComparison, both auto confirm
          LOG_INFO("Numeric Comparison: A and B auto confirm");
          if (!GetRecord()->RequiresMitmProtection()) {
            GetChannel()->SendCommand(
                hci::UserConfirmationRequestReplyBuilder::Create(GetRecord()->GetPseudoAddress()->GetAddress()));
            // TODO(optedoblivion): REMOVE WHEN SHIM LEAVES
            NotifyUiDisplayYesNo();
          } else {
            GetChannel()->SendCommand(hci::UserConfirmationRequestNegativeReplyBuilder::Create(
                GetRecord()->GetPseudoAddress()->GetAddress()));
          }
          // Unauthenticated
          GetRecord()->SetAuthenticated(false);
          break;
      }
      break;
    case hci::IoCapability::NO_INPUT_NO_OUTPUT:
      switch (responder_io_capability) {
        case hci::IoCapability::DISPLAY_ONLY:
          // NumericComparison, both auto confirm
          LOG_INFO("Numeric Comparison: A and B auto confirm");
          if (!GetRecord()->RequiresMitmProtection()) {
            GetChannel()->SendCommand(
                hci::UserConfirmationRequestReplyBuilder::Create(GetRecord()->GetPseudoAddress()->GetAddress()));
            // TODO(optedoblivion): REMOVE WHEN SHIM LEAVES
            NotifyUiDisplayYesNo();
          } else {
            GetChannel()->SendCommand(hci::UserConfirmationRequestNegativeReplyBuilder::Create(
                GetRecord()->GetPseudoAddress()->GetAddress()));
          }
          // Unauthenticated
          GetRecord()->SetAuthenticated(false);
          break;
        case hci::IoCapability::DISPLAY_YES_NO:
          // NumericComparison, Initiator auto confirm, Responder Yes/No confirm, no show conf val
          LOG_INFO("Numeric Comparison: A auto confirm");
          if (!GetRecord()->RequiresMitmProtection()) {
            GetChannel()->SendCommand(
                hci::UserConfirmationRequestReplyBuilder::Create(GetRecord()->GetPseudoAddress()->GetAddress()));
            // TODO(optedoblivion): REMOVE WHEN SHIM LEAVES
            NotifyUiDisplayYesNo();
          } else {
            GetChannel()->SendCommand(hci::UserConfirmationRequestNegativeReplyBuilder::Create(
                GetRecord()->GetPseudoAddress()->GetAddress()));
          }
          // Unauthenticated
          GetRecord()->SetAuthenticated(false);
          break;
        case hci::IoCapability::KEYBOARD_ONLY:
          // NumericComparison, both auto confirm
          LOG_INFO("Numeric Comparison: A and B auto confirm");
          if (!GetRecord()->RequiresMitmProtection()) {
            GetChannel()->SendCommand(
                hci::UserConfirmationRequestReplyBuilder::Create(GetRecord()->GetPseudoAddress()->GetAddress()));
            // TODO(optedoblivion): REMOVE WHEN SHIM LEAVES
            NotifyUiDisplayYesNo();
          } else {
            GetChannel()->SendCommand(hci::UserConfirmationRequestNegativeReplyBuilder::Create(
                GetRecord()->GetPseudoAddress()->GetAddress()));
          }
          // Unauthenticated
          GetRecord()->SetAuthenticated(false);
          break;
        case hci::IoCapability::NO_INPUT_NO_OUTPUT:
          // NumericComparison, both auto confirm
          LOG_INFO("Numeric Comparison: A and B auto confirm");
          if (!GetRecord()->RequiresMitmProtection()) {
            GetChannel()->SendCommand(
                hci::UserConfirmationRequestReplyBuilder::Create(GetRecord()->GetPseudoAddress()->GetAddress()));
            // TODO(optedoblivion): REMOVE WHEN SHIM LEAVES
            NotifyUiDisplayYesNo();
          } else {
            GetChannel()->SendCommand(hci::UserConfirmationRequestNegativeReplyBuilder::Create(
                GetRecord()->GetPseudoAddress()->GetAddress()));
          }
          // Unauthenticated
          GetRecord()->SetAuthenticated(false);
          break;
      }
      break;
  }
}

void ClassicPairingHandler::OnReceive(hci::UserPasskeyRequestView packet) {
  ASSERT(packet.IsValid());
  ASSERT_LOG(GetRecord()->GetPseudoAddress()->GetAddress() == packet.GetBdAddr(), "Address mismatch");
}

void ClassicPairingHandler::OnUserInput(bool user_input) {
  if (user_input) {
    UserClickedYes();
  } else {
    UserClickedNo();
  }
}

void ClassicPairingHandler::UserClickedYes() {
  GetChannel()->SendCommand(
      hci::UserConfirmationRequestReplyBuilder::Create(GetRecord()->GetPseudoAddress()->GetAddress()));
}

void ClassicPairingHandler::UserClickedNo() {
  GetChannel()->SendCommand(
      hci::UserConfirmationRequestNegativeReplyBuilder::Create(GetRecord()->GetPseudoAddress()->GetAddress()));
}

void ClassicPairingHandler::OnPasskeyInput(uint32_t passkey) {
  passkey_ = passkey;
}

}  // namespace pairing
}  // namespace security
}  // namespace bluetooth
