/*
 * Copyright 2023 The Android Open Source Project
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

#include "security_event_parser.h"

#include <bluetooth/log.h>

#include <optional>
#include <string>

#include "btm_sec.h"
#include "common/metrics.h"
#include "hci/hci_packets.h"
#include "main/shim/helpers.h"
#include "stack/include/btm_sec_api_types.h"
#include "stack/include/hci_error_code.h"
#include "stack/include/sec_hci_link_interface.h"
#include "stack/include/stack_metrics_logging.h"
#include "types/raw_address.h"

using namespace bluetooth;
using namespace bluetooth::hci;
using android::bluetooth::hci::CMD_UNKNOWN;
using android::bluetooth::hci::STATUS_UNKNOWN;
using bluetooth::common::kUnknownConnectionHandle;

namespace bluetooth::stack::btm {
namespace {
static void log_address_and_status(const Address& bda, EventCode event_code,
                                   ErrorCode event_status) {
  uint32_t cmd = android::bluetooth::hci::CMD_UNKNOWN;
  uint16_t status = static_cast<uint16_t>(event_status);
  uint16_t reason = android::bluetooth::hci::STATUS_UNKNOWN;
  uint16_t handle = bluetooth::common::kUnknownConnectionHandle;
  int64_t value = 0;
  log_classic_pairing_event(ToRawAddress(bda), handle, cmd,
                            static_cast<uint16_t>(event_code), status, reason,
                            value);
}
static void log_address(const Address& bda, EventCode event_code) {
  uint32_t cmd = android::bluetooth::hci::CMD_UNKNOWN;
  uint16_t status = android::bluetooth::hci::STATUS_UNKNOWN;
  uint16_t reason = android::bluetooth::hci::STATUS_UNKNOWN;
  uint16_t handle = bluetooth::common::kUnknownConnectionHandle;
  int64_t value = 0;
  log_classic_pairing_event(ToRawAddress(bda), handle, cmd,
                            static_cast<uint16_t>(event_code), status, reason,
                            value);
}
static void parse_encryption_change(const EventView event) {
  auto change_opt = EncryptionChangeView::CreateOptional(event);
  ASSERT(change_opt.has_value());
  auto change = change_opt.value();

  ErrorCode status = change.GetStatus();
  uint16_t handle = change.GetConnectionHandle();
  EncryptionEnabled encr_enable = change.GetEncryptionEnabled();

  btm_sec_encryption_change_evt(handle, static_cast<tHCI_STATUS>(status),
                                static_cast<uint8_t>(encr_enable));
  log_classic_pairing_event(ToRawAddress(Address::kEmpty), handle,
                            android::bluetooth::hci::CMD_UNKNOWN,
                            static_cast<uint32_t>(change.GetEventCode()),
                            static_cast<uint16_t>(status),
                            android::bluetooth::hci::STATUS_UNKNOWN, 0);
}
static void parse_change_connection_link_key_complete(const EventView event) {
  auto complete_opt =
      ChangeConnectionLinkKeyCompleteView::CreateOptional(event);
  ASSERT(complete_opt.has_value());
  auto complete = complete_opt.value();

  log_classic_pairing_event(ToRawAddress(Address::kEmpty),
                            complete.GetConnectionHandle(),
                            android::bluetooth::hci::CMD_UNKNOWN,
                            static_cast<uint32_t>(complete.GetEventCode()),
                            static_cast<uint16_t>(complete.GetStatus()),
                            android::bluetooth::hci::STATUS_UNKNOWN, 0);
}
static void parse_central_link_key_complete(const EventView event) {
  auto event_opt = CentralLinkKeyCompleteView::CreateOptional(event);
  ASSERT(event_opt.has_value());
  auto complete = event_opt.value();

  log::info("Unhandled event: {}", EventCodeText(event.GetEventCode()).c_str());
}
static void parse_return_link_keys(const EventView event) {
  auto event_opt = ReturnLinkKeysView::CreateOptional(event);
  ASSERT(event_opt.has_value());
  auto view = event_opt.value();

  log::info("Unhandled event: {}", EventCodeText(event.GetEventCode()).c_str());
}
static void parse_pin_code_request(const EventView event) {
  auto event_opt = PinCodeRequestView::CreateOptional(event);
  ASSERT(event_opt.has_value());
  auto request = event_opt.value();
  btm_sec_pin_code_request(ToRawAddress(request.GetBdAddr()));
}
static void parse_link_key_request(const EventView event) {
  auto event_opt = LinkKeyRequestView::CreateOptional(event);
  ASSERT(event_opt.has_value());
  auto request = event_opt.value();

  btm_sec_link_key_request(ToRawAddress(request.GetBdAddr()));
  log_address(request.GetBdAddr(), event.GetEventCode());
}
static void parse_link_key_notification(const EventView event) {
  auto event_opt = LinkKeyNotificationView::CreateOptional(event);
  ASSERT(event_opt.has_value());
  auto notification = event_opt.value();

  btm_sec_link_key_notification(
      ToRawAddress(notification.GetBdAddr()), notification.GetLinkKey(),
      static_cast<uint8_t>(notification.GetKeyType()));
  log_address(notification.GetBdAddr(), event.GetEventCode());
}
static void parse_encryption_key_refresh_complete(const EventView event) {
  auto event_opt = EncryptionKeyRefreshCompleteView::CreateOptional(event);
  ASSERT(event_opt.has_value());
  auto refresh = event_opt.value();

  btm_sec_encryption_key_refresh_complete(
      refresh.GetConnectionHandle(),
      static_cast<tHCI_STATUS>(refresh.GetStatus()));
}
static void parse_io_capabilities_req(const EventView event) {
  auto event_opt = IoCapabilityRequestView::CreateOptional(event);
  ASSERT(event_opt.has_value());
  auto request = event_opt.value();

  RawAddress peer = ToRawAddress(request.GetBdAddr());

  btm_io_capabilities_req(peer);
  log_address(request.GetBdAddr(), event.GetEventCode());
}
static void parse_io_capabilities_rsp(const EventView event) {
  auto response_opt = IoCapabilityResponseView::CreateOptional(event);
  ASSERT(response_opt.has_value());
  auto response = response_opt.value();

  tBTM_SP_IO_RSP evt_data{
      .bd_addr = ToRawAddress(response.GetBdAddr()),
      .io_cap = static_cast<tBTM_IO_CAP>(response.GetIoCapability()),
      .oob_data = static_cast<tBTM_OOB_DATA>(response.GetOobDataPresent()),
      .auth_req =
          static_cast<tBTM_AUTH_REQ>(response.GetAuthenticationRequirements()),
  };

  btm_io_capabilities_rsp(evt_data);
  log_address(response.GetBdAddr(), event.GetEventCode());
}
static void parse_remote_oob_data_request(const EventView event) {
  auto event_opt = RemoteOobDataRequestView::CreateOptional(event);
  ASSERT(event_opt.has_value());
  auto request = event_opt.value();

  btm_rem_oob_req(ToRawAddress(request.GetBdAddr()));
  log_address(request.GetBdAddr(), event.GetEventCode());
}
static void parse_simple_pairing_complete(const EventView event) {
  auto event_opt = SimplePairingCompleteView::CreateOptional(event);
  ASSERT(event_opt.has_value());
  auto complete = event_opt.value();

  btm_simple_pair_complete(ToRawAddress(complete.GetBdAddr()),
                           static_cast<uint8_t>(complete.GetStatus()));
  log_address_and_status(complete.GetBdAddr(), event.GetEventCode(),
                         complete.GetStatus());
}
static void parse_user_passkey_notification(const EventView event) {
  auto event_opt = UserPasskeyNotificationView::CreateOptional(event);
  ASSERT(event_opt.has_value());
  auto notification = event_opt.value();

  btm_proc_sp_req_evt(BTM_SP_KEY_NOTIF_EVT,
                      ToRawAddress(notification.GetBdAddr()),
                      notification.GetPasskey());
  log_address(notification.GetBdAddr(), event.GetEventCode());
}
static void parse_keypress_notification(const EventView event) {
  auto event_opt = KeypressNotificationView::CreateOptional(event);
  ASSERT(event_opt.has_value());
  auto notification = event_opt.value();

  log::info("Unhandled event: {}", EventCodeText(event.GetEventCode()).c_str());
  log_address(notification.GetBdAddr(), event.GetEventCode());
}
static void parse_user_confirmation_request(const EventView event) {
  auto event_opt = UserConfirmationRequestView::CreateOptional(event);
  ASSERT(event_opt.has_value());
  auto request = event_opt.value();

  btm_proc_sp_req_evt(BTM_SP_CFM_REQ_EVT, ToRawAddress(request.GetBdAddr()),
                      request.GetNumericValue());
  log_address(request.GetBdAddr(), event.GetEventCode());
}
static void parse_user_passkey_request(const EventView event) {
  auto event_opt = UserPasskeyRequestView::CreateOptional(event);
  ASSERT(event_opt.has_value());
  auto request = event_opt.value();

  btm_proc_sp_req_evt(BTM_SP_KEY_REQ_EVT, ToRawAddress(request.GetBdAddr()),
                      0 /* No value needed */);
  log_address(request.GetBdAddr(), event.GetEventCode());
}
}  // namespace
}  // namespace bluetooth::stack::btm

namespace bluetooth::stack::btm {

void SecurityEventParser::OnSecurityEvent(bluetooth::hci::EventView event) {
  switch (event.GetEventCode()) {
    case EventCode::ENCRYPTION_CHANGE:
      parse_encryption_change(event);
      break;
    case EventCode::CHANGE_CONNECTION_LINK_KEY_COMPLETE:
      parse_change_connection_link_key_complete(event);
      break;
    case EventCode::CENTRAL_LINK_KEY_COMPLETE:
      parse_central_link_key_complete(event);
      break;
    case EventCode::RETURN_LINK_KEYS:
      parse_return_link_keys(event);
      break;
    case EventCode::PIN_CODE_REQUEST:
      parse_pin_code_request(event);
      break;
    case EventCode::LINK_KEY_REQUEST:
      parse_link_key_request(event);
      break;
    case EventCode::LINK_KEY_NOTIFICATION:
      parse_link_key_notification(event);
      break;
    case EventCode::ENCRYPTION_KEY_REFRESH_COMPLETE:
      parse_encryption_key_refresh_complete(event);
      break;
    case EventCode::IO_CAPABILITY_REQUEST:
      parse_io_capabilities_req(event);
      break;
    case EventCode::IO_CAPABILITY_RESPONSE:
      parse_io_capabilities_rsp(event);
      break;
    case EventCode::REMOTE_OOB_DATA_REQUEST:
      parse_remote_oob_data_request(event);
      break;
    case EventCode::SIMPLE_PAIRING_COMPLETE:
      parse_simple_pairing_complete(event);
      break;
    case EventCode::USER_PASSKEY_NOTIFICATION:
      parse_user_passkey_notification(event);
      break;
    case EventCode::KEYPRESS_NOTIFICATION:
      parse_keypress_notification(event);
      break;
    case EventCode::USER_CONFIRMATION_REQUEST:
      parse_user_confirmation_request(event);
      break;
    case EventCode::USER_PASSKEY_REQUEST:
      parse_user_passkey_request(event);
      break;
    default:
      log::error("Unhandled event {}",
                 EventCodeText(event.GetEventCode()).c_str());
  }
}
}  // namespace bluetooth::stack::btm
