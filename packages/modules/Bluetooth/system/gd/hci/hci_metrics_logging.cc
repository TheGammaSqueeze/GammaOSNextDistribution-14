/*
 * Copyright 2021 The Android Open Source Project
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
#include "hci/hci_metrics_logging.h"

#include <frameworks/proto_logging/stats/enums/bluetooth/hci/enums.pb.h>

#include "common/audit_log.h"
#include "common/strings.h"
#include "os/metrics.h"
#include "storage/device.h"

namespace bluetooth {
namespace hci {

void log_hci_event(
    std::unique_ptr<CommandView>& command_view, EventView event_view, storage::StorageModule* storage_module) {
  ASSERT(event_view.IsValid());
  EventCode event_code = event_view.GetEventCode();
  switch (event_code) {
    case EventCode::COMMAND_COMPLETE: {
      CommandCompleteView complete_view = CommandCompleteView::Create(event_view);
      ASSERT(complete_view.IsValid());
      if (complete_view.GetCommandOpCode() == OpCode::NONE) {
        return;
      }
      ASSERT(command_view->IsValid());
      log_link_layer_connection_command_complete(event_view, command_view);
      log_classic_pairing_command_complete(event_view, command_view);
      break;
    }
    case EventCode::COMMAND_STATUS: {
      CommandStatusView response_view = CommandStatusView::Create(event_view);
      ASSERT(response_view.IsValid());
      if (response_view.GetCommandOpCode() == OpCode::NONE) {
        return;
      }
      ASSERT(command_view->IsValid());
      log_link_layer_connection_command_status(command_view, response_view.GetStatus());
      log_classic_pairing_command_status(command_view, response_view.GetStatus());
      break;
    }
    case EventCode::LE_META_EVENT: {
      LeMetaEventView le_meta_event_view = LeMetaEventView::Create(event_view);
      ASSERT(le_meta_event_view.IsValid());
      log_link_layer_connection_event_le_meta(le_meta_event_view);
      break;
    }
    default:
      log_link_layer_connection_other_hci_event(event_view, storage_module);
      log_classic_pairing_other_hci_event(event_view);
  }
}

void log_link_layer_connection_command(std::unique_ptr<CommandView>& command_view) {
  // get op_code
  ASSERT(command_view->IsValid());
  OpCode op_code = command_view->GetOpCode();

  // init parameters to log
  Address address = Address::kEmpty;
  uint32_t connection_handle = bluetooth::os::kUnknownConnectionHandle;
  uint16_t reason = static_cast<uint16_t>(ErrorCode::UNKNOWN_HCI_COMMAND);
  static uint16_t kUnknownBleEvt = android::bluetooth::hci::BLE_EVT_UNKNOWN;
  uint16_t event_code = android::bluetooth::hci::EVT_UNKNOWN;
  android::bluetooth::DirectionEnum direction = android::bluetooth::DIRECTION_UNKNOWN;
  uint16_t link_type = android::bluetooth::LINK_TYPE_UNKNOWN;
  uint16_t status = static_cast<uint16_t>(ErrorCode::STATUS_UNKNOWN);

  // get ConnectionManagementCommandView
  ConnectionManagementCommandView connection_management_command_view =
      ConnectionManagementCommandView::Create(AclCommandView::Create(*command_view));
  ASSERT(connection_management_command_view.IsValid());
  switch (op_code) {
    case OpCode::CREATE_CONNECTION: {
      auto create_connection_view = CreateConnectionView::Create(std::move(connection_management_command_view));
      ASSERT(create_connection_view.IsValid());
      address = create_connection_view.GetBdAddr();
      direction = android::bluetooth::DIRECTION_OUTGOING;
      link_type = android::bluetooth::LINK_TYPE_ACL;
      break;
    }
    case OpCode::CREATE_CONNECTION_CANCEL: {
      auto create_connection_cancel_view =
          CreateConnectionCancelView::Create(std::move(connection_management_command_view));
      ASSERT(create_connection_cancel_view.IsValid());
      address = create_connection_cancel_view.GetBdAddr();
      direction = android::bluetooth::DIRECTION_OUTGOING;
      link_type = android::bluetooth::LINK_TYPE_ACL;
      break;
    }
    case OpCode::DISCONNECT: {
      auto disconnect_view = DisconnectView::Create(std::move(connection_management_command_view));
      ASSERT(disconnect_view.IsValid());
      connection_handle = disconnect_view.GetConnectionHandle();
      reason = static_cast<uint16_t>(disconnect_view.GetReason());
      break;
    }
    case OpCode::SETUP_SYNCHRONOUS_CONNECTION: {
      auto setup_synchronous_connection_view = SetupSynchronousConnectionView::Create(
          ScoConnectionCommandView::Create(std::move(connection_management_command_view)));
      ASSERT(setup_synchronous_connection_view.IsValid());
      connection_handle = setup_synchronous_connection_view.GetConnectionHandle();
      direction = android::bluetooth::DIRECTION_OUTGOING;
      break;
    }
    case OpCode::ENHANCED_SETUP_SYNCHRONOUS_CONNECTION: {
      auto enhanced_setup_synchronous_connection_view = EnhancedSetupSynchronousConnectionView::Create(
          ScoConnectionCommandView::Create(std::move(connection_management_command_view)));
      ASSERT(enhanced_setup_synchronous_connection_view.IsValid());
      connection_handle = enhanced_setup_synchronous_connection_view.GetConnectionHandle();
      direction = android::bluetooth::DIRECTION_OUTGOING;
      break;
    }
    case OpCode::ACCEPT_CONNECTION_REQUEST: {
      auto accept_connection_request_view =
          AcceptConnectionRequestView::Create(std::move(connection_management_command_view));
      ASSERT(accept_connection_request_view.IsValid());
      address = accept_connection_request_view.GetBdAddr();
      direction = android::bluetooth::DIRECTION_INCOMING;
      break;
    }
    case OpCode::ACCEPT_SYNCHRONOUS_CONNECTION: {
      auto accept_synchronous_connection_view = AcceptSynchronousConnectionView::Create(
          ScoConnectionCommandView::Create(std::move(connection_management_command_view)));
      ASSERT(accept_synchronous_connection_view.IsValid());
      address = accept_synchronous_connection_view.GetBdAddr();
      direction = android::bluetooth::DIRECTION_INCOMING;
      break;
    }
    case OpCode::ENHANCED_ACCEPT_SYNCHRONOUS_CONNECTION: {
      auto enhanced_accept_synchronous_connection_view = EnhancedAcceptSynchronousConnectionView::Create(
          ScoConnectionCommandView::Create(std::move(connection_management_command_view)));
      ASSERT(enhanced_accept_synchronous_connection_view.IsValid());
      address = enhanced_accept_synchronous_connection_view.GetBdAddr();
      direction = android::bluetooth::DIRECTION_INCOMING;
      break;
    }
    case OpCode::REJECT_CONNECTION_REQUEST: {
      auto reject_connection_request_view =
          RejectConnectionRequestView::Create(std::move(connection_management_command_view));
      ASSERT(reject_connection_request_view.IsValid());
      address = reject_connection_request_view.GetBdAddr();
      reason = static_cast<uint16_t>(reject_connection_request_view.GetReason());
      direction = android::bluetooth::DIRECTION_INCOMING;
      break;
    }
    case OpCode::REJECT_SYNCHRONOUS_CONNECTION: {
      auto reject_synchronous_connection_view = RejectSynchronousConnectionView::Create(
          ScoConnectionCommandView::Create(std::move(connection_management_command_view)));
      ASSERT(reject_synchronous_connection_view.IsValid());
      address = reject_synchronous_connection_view.GetBdAddr();
      reason = static_cast<uint16_t>(reject_synchronous_connection_view.GetReason());
      direction = android::bluetooth::DIRECTION_INCOMING;
      break;
    }
    case OpCode::LE_CREATE_CONNECTION: {
      auto le_create_connection_view = LeCreateConnectionView::Create(
          LeConnectionManagementCommandView::Create(std::move(connection_management_command_view)));
      ASSERT(le_create_connection_view.IsValid());
      address = le_create_connection_view.GetPeerAddress();
      direction = android::bluetooth::DIRECTION_INCOMING;
      link_type = android::bluetooth::LINK_TYPE_ACL;
      break;
    }
    case OpCode::LE_EXTENDED_CREATE_CONNECTION: {
      auto le_extended_create_connection_view = LeExtendedCreateConnectionView::Create(
          LeConnectionManagementCommandView::Create(std::move(connection_management_command_view)));
      ASSERT(le_extended_create_connection_view.IsValid());
      address = le_extended_create_connection_view.GetPeerAddress();
      direction = android::bluetooth::DIRECTION_OUTGOING;
      link_type = android::bluetooth::LINK_TYPE_ACL;
      break;
    }
    case OpCode::LE_CREATE_CONNECTION_CANCEL: {
      auto le_create_connection_cancel_view = LeCreateConnectionCancelView::Create(
          LeConnectionManagementCommandView::Create(std::move(connection_management_command_view)));
      ASSERT(le_create_connection_cancel_view.IsValid());
      direction = android::bluetooth::DIRECTION_OUTGOING;
      link_type = android::bluetooth::LINK_TYPE_ACL;
      break;
    }
    case OpCode::LE_CLEAR_FILTER_ACCEPT_LIST: {
      auto le_clear_filter_accept_list_view = LeClearFilterAcceptListView::Create(
          LeConnectionManagementCommandView::Create(std::move(connection_management_command_view)));
      ASSERT(le_clear_filter_accept_list_view.IsValid());
      direction = android::bluetooth::DIRECTION_INCOMING;
      link_type = android::bluetooth::LINK_TYPE_ACL;
      break;
    }
    case OpCode::LE_ADD_DEVICE_TO_FILTER_ACCEPT_LIST: {
      auto le_add_device_to_accept_list_view = LeAddDeviceToFilterAcceptListView::Create(
          LeConnectionManagementCommandView::Create(std::move(connection_management_command_view)));
      ASSERT(le_add_device_to_accept_list_view.IsValid());
      address = le_add_device_to_accept_list_view.GetAddress();
      direction = android::bluetooth::DIRECTION_INCOMING;
      link_type = android::bluetooth::LINK_TYPE_ACL;
      break;
    }
    case OpCode::LE_REMOVE_DEVICE_FROM_FILTER_ACCEPT_LIST: {
      auto le_remove_device_from_accept_list_view = LeRemoveDeviceFromFilterAcceptListView::Create(
          LeConnectionManagementCommandView::Create(std::move(connection_management_command_view)));
      ASSERT(le_remove_device_from_accept_list_view.IsValid());
      address = le_remove_device_from_accept_list_view.GetAddress();
      direction = android::bluetooth::DIRECTION_INCOMING;
      link_type = android::bluetooth::LINK_TYPE_ACL;
      break;
    }
    default:
      return;
  }
  os::LogMetricLinkLayerConnectionEvent(
      &address,
      connection_handle,
      direction,
      link_type,
      static_cast<uint32_t>(op_code),
      static_cast<uint16_t>(event_code),
      kUnknownBleEvt,
      status,
      static_cast<uint16_t>(reason));
}

void log_link_layer_connection_command_status(std::unique_ptr<CommandView>& command_view, ErrorCode status) {
  // get op_code
  ASSERT(command_view->IsValid());
  OpCode op_code = command_view->GetOpCode();

  // init parameters to log
  Address address = Address::kEmpty;
  uint32_t connection_handle = bluetooth::os::kUnknownConnectionHandle;
  uint16_t reason = static_cast<uint16_t>(ErrorCode::UNKNOWN_HCI_COMMAND);
  static uint16_t kUnknownBleEvt = android::bluetooth::hci::BLE_EVT_UNKNOWN;
  uint16_t event_code = android::bluetooth::hci::EVT_COMMAND_STATUS;
  android::bluetooth::DirectionEnum direction = android::bluetooth::DIRECTION_UNKNOWN;
  uint16_t link_type = android::bluetooth::LINK_TYPE_UNKNOWN;

  // get ConnectionManagementCommandView
  ConnectionManagementCommandView connection_management_command_view =
      ConnectionManagementCommandView::Create(AclCommandView::Create(*command_view));
  ASSERT(connection_management_command_view.IsValid());
  switch (op_code) {
    case OpCode::CREATE_CONNECTION: {
      auto create_connection_view = CreateConnectionView::Create(std::move(connection_management_command_view));
      ASSERT(create_connection_view.IsValid());
      address = create_connection_view.GetBdAddr();
      direction = android::bluetooth::DIRECTION_OUTGOING;
      link_type = android::bluetooth::LINK_TYPE_ACL;
      break;
    }
    case OpCode::CREATE_CONNECTION_CANCEL: {
      auto create_connection_cancel_view =
          CreateConnectionCancelView::Create(std::move(connection_management_command_view));
      ASSERT(create_connection_cancel_view.IsValid());
      address = create_connection_cancel_view.GetBdAddr();
      direction = android::bluetooth::DIRECTION_OUTGOING;
      link_type = android::bluetooth::LINK_TYPE_ACL;
      break;
    }
    case OpCode::DISCONNECT: {
      auto disconnect_view = DisconnectView::Create(std::move(connection_management_command_view));
      ASSERT(disconnect_view.IsValid());
      connection_handle = disconnect_view.GetConnectionHandle();
      reason = static_cast<uint16_t>(disconnect_view.GetReason());
      break;
    }
    case OpCode::SETUP_SYNCHRONOUS_CONNECTION: {
      auto setup_synchronous_connection_view = SetupSynchronousConnectionView::Create(
          ScoConnectionCommandView::Create(std::move(connection_management_command_view)));
      ASSERT(setup_synchronous_connection_view.IsValid());
      connection_handle = setup_synchronous_connection_view.GetConnectionHandle();
      direction = android::bluetooth::DIRECTION_OUTGOING;
      break;
    }
    case OpCode::ENHANCED_SETUP_SYNCHRONOUS_CONNECTION: {
      auto enhanced_setup_synchronous_connection_view = EnhancedSetupSynchronousConnectionView::Create(
          ScoConnectionCommandView::Create(std::move(connection_management_command_view)));
      ASSERT(enhanced_setup_synchronous_connection_view.IsValid());
      connection_handle = enhanced_setup_synchronous_connection_view.GetConnectionHandle();
      direction = android::bluetooth::DIRECTION_OUTGOING;
      break;
    }
    case OpCode::ACCEPT_CONNECTION_REQUEST: {
      auto accept_connection_request_view =
          AcceptConnectionRequestView::Create(std::move(connection_management_command_view));
      ASSERT(accept_connection_request_view.IsValid());
      address = accept_connection_request_view.GetBdAddr();
      direction = android::bluetooth::DIRECTION_INCOMING;
      break;
    }
    case OpCode::ACCEPT_SYNCHRONOUS_CONNECTION: {
      auto accept_synchronous_connection_view = AcceptSynchronousConnectionView::Create(
          ScoConnectionCommandView::Create(std::move(connection_management_command_view)));
      ASSERT(accept_synchronous_connection_view.IsValid());
      address = accept_synchronous_connection_view.GetBdAddr();
      direction = android::bluetooth::DIRECTION_INCOMING;
      break;
    }
    case OpCode::ENHANCED_ACCEPT_SYNCHRONOUS_CONNECTION: {
      auto enhanced_accept_synchronous_connection_view = EnhancedAcceptSynchronousConnectionView::Create(
          ScoConnectionCommandView::Create(std::move(connection_management_command_view)));
      ASSERT(enhanced_accept_synchronous_connection_view.IsValid());
      address = enhanced_accept_synchronous_connection_view.GetBdAddr();
      direction = android::bluetooth::DIRECTION_INCOMING;
      break;
    }
    case OpCode::REJECT_CONNECTION_REQUEST: {
      auto reject_connection_request_view =
          RejectConnectionRequestView::Create(std::move(connection_management_command_view));
      ASSERT(reject_connection_request_view.IsValid());
      address = reject_connection_request_view.GetBdAddr();
      reason = static_cast<uint16_t>(reject_connection_request_view.GetReason());
      direction = android::bluetooth::DIRECTION_INCOMING;
      break;
    }
    case OpCode::REJECT_SYNCHRONOUS_CONNECTION: {
      auto reject_synchronous_connection_view = RejectSynchronousConnectionView::Create(
          ScoConnectionCommandView::Create(std::move(connection_management_command_view)));
      ASSERT(reject_synchronous_connection_view.IsValid());
      address = reject_synchronous_connection_view.GetBdAddr();
      reason = static_cast<uint16_t>(reject_synchronous_connection_view.GetReason());
      direction = android::bluetooth::DIRECTION_INCOMING;
      break;
    }
    case OpCode::LE_CREATE_CONNECTION: {
      auto le_create_connection_view = LeCreateConnectionView::Create(
          LeConnectionManagementCommandView::Create(std::move(connection_management_command_view)));
      ASSERT(le_create_connection_view.IsValid());
      uint8_t initiator_filter_policy = static_cast<uint8_t>(le_create_connection_view.GetInitiatorFilterPolicy());
      if (initiator_filter_policy != 0x00 && status == ErrorCode::SUCCESS) {
        return;
      }
      address = le_create_connection_view.GetPeerAddress();
      direction = android::bluetooth::DIRECTION_INCOMING;
      link_type = android::bluetooth::LINK_TYPE_ACL;
      break;
    }
    case OpCode::LE_EXTENDED_CREATE_CONNECTION: {
      auto le_extended_create_connection_view = LeExtendedCreateConnectionView::Create(
          LeConnectionManagementCommandView::Create(std::move(connection_management_command_view)));
      ASSERT(le_extended_create_connection_view.IsValid());
      uint8_t initiator_filter_policy =
          static_cast<uint8_t>(le_extended_create_connection_view.GetInitiatorFilterPolicy());
      if (initiator_filter_policy != 0x00 && status == ErrorCode::SUCCESS) {
        return;
      }
      address = le_extended_create_connection_view.GetPeerAddress();
      direction = android::bluetooth::DIRECTION_OUTGOING;
      link_type = android::bluetooth::LINK_TYPE_ACL;
      break;
    }
    case OpCode::LE_CREATE_CONNECTION_CANCEL: {
      auto le_create_connection_cancel_view = LeCreateConnectionCancelView::Create(
          LeConnectionManagementCommandView::Create(std::move(connection_management_command_view)));
      ASSERT(le_create_connection_cancel_view.IsValid());
      if (status == ErrorCode::SUCCESS) {
        return;
      }
      direction = android::bluetooth::DIRECTION_OUTGOING;
      link_type = android::bluetooth::LINK_TYPE_ACL;
      break;
    }
    case OpCode::LE_CLEAR_FILTER_ACCEPT_LIST: {
      auto le_clear_filter_accept_list_view = LeClearFilterAcceptListView::Create(
          LeConnectionManagementCommandView::Create(std::move(connection_management_command_view)));
      ASSERT(le_clear_filter_accept_list_view.IsValid());
      direction = android::bluetooth::DIRECTION_INCOMING;
      link_type = android::bluetooth::LINK_TYPE_ACL;
      break;
    }
    case OpCode::LE_ADD_DEVICE_TO_FILTER_ACCEPT_LIST: {
      auto le_add_device_to_accept_list_view = LeAddDeviceToFilterAcceptListView::Create(
          LeConnectionManagementCommandView::Create(std::move(connection_management_command_view)));
      ASSERT(le_add_device_to_accept_list_view.IsValid());
      address = le_add_device_to_accept_list_view.GetAddress();
      direction = android::bluetooth::DIRECTION_INCOMING;
      link_type = android::bluetooth::LINK_TYPE_ACL;
      break;
    }
    case OpCode::LE_REMOVE_DEVICE_FROM_FILTER_ACCEPT_LIST: {
      auto le_remove_device_from_accept_list_view = LeRemoveDeviceFromFilterAcceptListView::Create(
          LeConnectionManagementCommandView::Create(std::move(connection_management_command_view)));
      ASSERT(le_remove_device_from_accept_list_view.IsValid());
      address = le_remove_device_from_accept_list_view.GetAddress();
      direction = android::bluetooth::DIRECTION_INCOMING;
      link_type = android::bluetooth::LINK_TYPE_ACL;
      break;
    }
    default:
      return;
  }
  os::LogMetricLinkLayerConnectionEvent(
      &address,
      connection_handle,
      direction,
      link_type,
      static_cast<uint32_t>(op_code),
      static_cast<uint16_t>(event_code),
      kUnknownBleEvt,
      static_cast<uint16_t>(status),
      static_cast<uint16_t>(reason));
}

void log_link_layer_connection_command_complete(EventView event_view, std::unique_ptr<CommandView>& command_view) {
  CommandCompleteView command_complete_view = CommandCompleteView::Create(std::move(event_view));
  ASSERT(command_complete_view.IsValid());
  OpCode op_code = command_complete_view.GetCommandOpCode();

  // init parameters to log
  Address address = Address::kEmpty;
  uint32_t connection_handle = bluetooth::os::kUnknownConnectionHandle;
  ErrorCode status = ErrorCode::UNKNOWN_HCI_COMMAND;
  ErrorCode reason = ErrorCode::UNKNOWN_HCI_COMMAND;
  static uint16_t kUnknownBleEvt = android::bluetooth::hci::BLE_EVT_UNKNOWN;
  uint16_t event_code = android::bluetooth::hci::EVT_COMMAND_COMPLETE;
  android::bluetooth::DirectionEnum direction = android::bluetooth::DIRECTION_UNKNOWN;
  uint16_t link_type = android::bluetooth::LINK_TYPE_UNKNOWN;

  // get ConnectionManagementCommandView
  ConnectionManagementCommandView connection_management_command_view =
      ConnectionManagementCommandView::Create(AclCommandView::Create(*command_view));
  ASSERT(connection_management_command_view.IsValid());

  switch (op_code) {
    case OpCode::LE_CLEAR_FILTER_ACCEPT_LIST: {
      auto le_clear_filter_accept_list_view = LeClearFilterAcceptListView::Create(
          LeConnectionManagementCommandView::Create(std::move(connection_management_command_view)));
      ASSERT(le_clear_filter_accept_list_view.IsValid());
      direction = android::bluetooth::DIRECTION_INCOMING;
      link_type = android::bluetooth::LINK_TYPE_ACL;
      break;
    }
    case OpCode::LE_ADD_DEVICE_TO_FILTER_ACCEPT_LIST: {
      auto le_add_device_to_accept_list_view = LeAddDeviceToFilterAcceptListView::Create(
          LeConnectionManagementCommandView::Create(std::move(connection_management_command_view)));
      ASSERT(le_add_device_to_accept_list_view.IsValid());
      address = le_add_device_to_accept_list_view.GetAddress();
      direction = android::bluetooth::DIRECTION_INCOMING;
      link_type = android::bluetooth::LINK_TYPE_ACL;
      break;
    }
    case OpCode::LE_REMOVE_DEVICE_FROM_FILTER_ACCEPT_LIST: {
      auto le_remove_device_from_accept_list_view = LeRemoveDeviceFromFilterAcceptListView::Create(
          LeConnectionManagementCommandView::Create(std::move(connection_management_command_view)));
      ASSERT(le_remove_device_from_accept_list_view.IsValid());
      address = le_remove_device_from_accept_list_view.GetAddress();
      direction = android::bluetooth::DIRECTION_INCOMING;
      link_type = android::bluetooth::LINK_TYPE_ACL;
      break;
    }
    case OpCode::CREATE_CONNECTION_CANCEL: {
      auto create_connection_cancel_complete_view =
          CreateConnectionCancelCompleteView::Create(std::move(command_complete_view));
      ASSERT(create_connection_cancel_complete_view.IsValid());
      address = create_connection_cancel_complete_view.GetBdAddr();
      direction = android::bluetooth::DIRECTION_OUTGOING;
      link_type = android::bluetooth::LINK_TYPE_ACL;
      status = create_connection_cancel_complete_view.GetStatus();
      break;
    }
    case OpCode::LE_CREATE_CONNECTION_CANCEL: {
      auto le_create_connection_cancel_complete_view =
          LeCreateConnectionCancelCompleteView::Create(std::move(command_complete_view));
      ASSERT(le_create_connection_cancel_complete_view.IsValid());
      direction = android::bluetooth::DIRECTION_OUTGOING;
      link_type = android::bluetooth::LINK_TYPE_ACL;
      status = le_create_connection_cancel_complete_view.GetStatus();
      break;
    }
    default:
      return;
  }
  os::LogMetricLinkLayerConnectionEvent(
      &address,
      connection_handle,
      direction,
      link_type,
      static_cast<uint32_t>(op_code),
      static_cast<uint16_t>(event_code),
      kUnknownBleEvt,
      static_cast<uint16_t>(status),
      static_cast<uint16_t>(reason));
}

void log_link_layer_connection_other_hci_event(EventView packet, storage::StorageModule* storage_module) {
  EventCode event_code = packet.GetEventCode();
  Address address = Address::kEmpty;
  uint32_t connection_handle = bluetooth::os::kUnknownConnectionHandle;
  android::bluetooth::DirectionEnum direction = android::bluetooth::DIRECTION_UNKNOWN;
  uint16_t link_type = android::bluetooth::LINK_TYPE_UNKNOWN;
  ErrorCode status = ErrorCode::UNKNOWN_HCI_COMMAND;
  ErrorCode reason = ErrorCode::UNKNOWN_HCI_COMMAND;
  uint32_t cmd = android::bluetooth::hci::CMD_UNKNOWN;
  switch (event_code) {
    case EventCode::CONNECTION_COMPLETE: {
      auto connection_complete_view = ConnectionCompleteView::Create(std::move(packet));
      ASSERT(connection_complete_view.IsValid());
      address = connection_complete_view.GetBdAddr();
      connection_handle = connection_complete_view.GetConnectionHandle();
      link_type = static_cast<uint16_t>(connection_complete_view.GetLinkType());
      status = connection_complete_view.GetStatus();

      // besides log link layer connection events, also log remote device manufacturer info
      log_remote_device_information(
          address,
          android::bluetooth::ADDRESS_TYPE_PUBLIC,
          connection_handle,
          status,
          storage_module);

      if (status != ErrorCode::SUCCESS) {
        common::LogConnectionAdminAuditEvent("Connecting", address, status);
      }
      break;
    }
    case EventCode::CONNECTION_REQUEST: {
      auto connection_request_view = ConnectionRequestView::Create(std::move(packet));
      ASSERT(connection_request_view.IsValid());
      address = connection_request_view.GetBdAddr();
      link_type = static_cast<uint16_t>(connection_request_view.GetLinkType());
      direction = android::bluetooth::DIRECTION_INCOMING;
      break;
    }
    case EventCode::DISCONNECTION_COMPLETE: {
      auto disconnection_complete_view = DisconnectionCompleteView::Create(std::move(packet));
      ASSERT(disconnection_complete_view.IsValid());
      status = disconnection_complete_view.GetStatus();
      connection_handle = disconnection_complete_view.GetConnectionHandle();
      reason = disconnection_complete_view.GetReason();
      break;
    }
    case EventCode::SYNCHRONOUS_CONNECTION_COMPLETE: {
      auto synchronous_connection_complete_view = SynchronousConnectionCompleteView::Create(std::move(packet));
      ASSERT(synchronous_connection_complete_view.IsValid());
      connection_handle = synchronous_connection_complete_view.GetConnectionHandle();
      address = synchronous_connection_complete_view.GetBdAddr();
      link_type = static_cast<uint16_t>(synchronous_connection_complete_view.GetLinkType());
      status = synchronous_connection_complete_view.GetStatus();
      break;
    }
    case EventCode::SYNCHRONOUS_CONNECTION_CHANGED: {
      auto synchronous_connection_changed_view = SynchronousConnectionChangedView::Create(std::move(packet));
      ASSERT(synchronous_connection_changed_view.IsValid());
      status = synchronous_connection_changed_view.GetStatus();
      connection_handle = synchronous_connection_changed_view.GetConnectionHandle();
      break;
    }
    default:
      return;
  }
  os::LogMetricLinkLayerConnectionEvent(
      &address,
      connection_handle,
      direction,
      link_type,
      static_cast<uint32_t>(cmd),
      static_cast<uint16_t>(event_code),
      android::bluetooth::hci::BLE_EVT_UNKNOWN,
      static_cast<uint16_t>(status),
      static_cast<uint16_t>(reason));
}

void log_link_layer_connection_event_le_meta(LeMetaEventView le_meta_event_view) {
  SubeventCode leEvt = le_meta_event_view.GetSubeventCode();
  if (leEvt != SubeventCode::ENHANCED_CONNECTION_COMPLETE && leEvt != SubeventCode::CONNECTION_COMPLETE) {
    // function is called for all le meta events. Only need to process le connection complete.
    return;
  }

  EventCode event_code = EventCode::LE_META_EVENT;
  Address address;
  uint32_t connection_handle;
  android::bluetooth::DirectionEnum direction = android::bluetooth::DIRECTION_UNKNOWN;
  uint16_t link_type = android::bluetooth::LINK_TYPE_ACL;
  ErrorCode status;
  ErrorCode reason = ErrorCode::UNKNOWN_HCI_COMMAND;
  uint32_t cmd = android::bluetooth::hci::CMD_UNKNOWN;

  if (leEvt == SubeventCode::CONNECTION_COMPLETE) {
    auto le_connection_complete_view = LeConnectionCompleteView::Create(std::move(le_meta_event_view));
    ASSERT(le_connection_complete_view.IsValid());
    address = le_connection_complete_view.GetPeerAddress();
    connection_handle = le_connection_complete_view.GetConnectionHandle();
    status = le_connection_complete_view.GetStatus();
  } else if (leEvt == SubeventCode::ENHANCED_CONNECTION_COMPLETE) {
    auto le_enhanced_connection_complete_view = LeEnhancedConnectionCompleteView::Create(std::move(le_meta_event_view));
    ASSERT(le_enhanced_connection_complete_view.IsValid());
    address = le_enhanced_connection_complete_view.GetPeerAddress();
    connection_handle = le_enhanced_connection_complete_view.GetConnectionHandle();
    status = le_enhanced_connection_complete_view.GetStatus();
  } else {
    LOG_ALWAYS_FATAL("WTF");
  }

  os::LogMetricLinkLayerConnectionEvent(
      &address,
      connection_handle,
      direction,
      link_type,
      static_cast<uint32_t>(cmd),
      static_cast<uint16_t>(event_code),
      static_cast<uint16_t>(leEvt),
      static_cast<uint16_t>(status),
      static_cast<uint16_t>(reason));

  if (status != ErrorCode::SUCCESS && status != ErrorCode::UNKNOWN_CONNECTION) {
    // ERROR CODE 0x02, unknown connection identifier, means connection attempt was cancelled by host, so probably no
    // need to log it.
    common::LogConnectionAdminAuditEvent("Connecting", address, status);
  }
}

void log_classic_pairing_other_hci_event(EventView packet) {
  EventCode event_code = packet.GetEventCode();
  Address address = Address::kEmpty;
  uint32_t cmd = android::bluetooth::hci::CMD_UNKNOWN;
  ErrorCode status = ErrorCode::UNKNOWN_HCI_COMMAND;
  ErrorCode reason = ErrorCode::UNKNOWN_HCI_COMMAND;
  uint32_t connection_handle = bluetooth::os::kUnknownConnectionHandle;
  int64_t value = 0;

  switch (event_code) {
    case EventCode::IO_CAPABILITY_REQUEST: {
      IoCapabilityRequestView io_capability_request_view = IoCapabilityRequestView::Create(std::move(packet));
      ASSERT(io_capability_request_view.IsValid());
      address = io_capability_request_view.GetBdAddr();
      break;
    }
    case EventCode::IO_CAPABILITY_RESPONSE: {
      IoCapabilityResponseView io_capability_response_view = IoCapabilityResponseView::Create(std::move(packet));
      ASSERT(io_capability_response_view.IsValid());
      address = io_capability_response_view.GetBdAddr();
      break;
    }
    case EventCode::LINK_KEY_REQUEST: {
      LinkKeyRequestView link_key_request_view = LinkKeyRequestView::Create(std::move(packet));
      ASSERT(link_key_request_view.IsValid());
      address = link_key_request_view.GetBdAddr();
      break;
    }
    case EventCode::LINK_KEY_NOTIFICATION: {
      LinkKeyNotificationView link_key_notification_view = LinkKeyNotificationView::Create(std::move(packet));
      ASSERT(link_key_notification_view.IsValid());
      address = link_key_notification_view.GetBdAddr();
      break;
    }
    case EventCode::USER_PASSKEY_REQUEST: {
      UserPasskeyRequestView user_passkey_request_view = UserPasskeyRequestView::Create(std::move(packet));
      ASSERT(user_passkey_request_view.IsValid());
      address = user_passkey_request_view.GetBdAddr();
      break;
    }
    case EventCode::USER_PASSKEY_NOTIFICATION: {
      UserPasskeyNotificationView user_passkey_notification_view = UserPasskeyNotificationView::Create(std::move(packet));
      ASSERT(user_passkey_notification_view.IsValid());
      address = user_passkey_notification_view.GetBdAddr();
      break;
    }
    case EventCode::USER_CONFIRMATION_REQUEST: {
      UserConfirmationRequestView user_confirmation_request_view = UserConfirmationRequestView::Create(std::move(packet));
      ASSERT(user_confirmation_request_view.IsValid());
      address = user_confirmation_request_view.GetBdAddr();
      break;
    }
    case EventCode::KEYPRESS_NOTIFICATION: {
      KeypressNotificationView keypress_notification_view = KeypressNotificationView::Create(std::move(packet));
      ASSERT(keypress_notification_view.IsValid());
      address = keypress_notification_view.GetBdAddr();
      break;
    }
    case EventCode::REMOTE_OOB_DATA_REQUEST: {
      RemoteOobDataRequestView remote_oob_data_request_view = RemoteOobDataRequestView::Create(std::move(packet));
      if (!remote_oob_data_request_view.IsValid()) {
        LOG_WARN("remote_oob_data_request_view not valid");
        return;
      }
      address = remote_oob_data_request_view.GetBdAddr();
      break;
    }
    case EventCode::SIMPLE_PAIRING_COMPLETE: {
      SimplePairingCompleteView simple_pairing_complete_view = SimplePairingCompleteView::Create(std::move(packet));
      ASSERT(simple_pairing_complete_view.IsValid());
      address = simple_pairing_complete_view.GetBdAddr();
      status = simple_pairing_complete_view.GetStatus();
      break;
    }
    case EventCode::REMOTE_NAME_REQUEST_COMPLETE: {
      RemoteNameRequestCompleteView remote_name_request_complete_view = RemoteNameRequestCompleteView::Create(std::move(packet));
      if (!remote_name_request_complete_view.IsValid()) {
        LOG_WARN("remote_name_request_complete_view not valid");
        return;
      }
      address = remote_name_request_complete_view.GetBdAddr();
      status = remote_name_request_complete_view.GetStatus();
      break;
    }
    case EventCode::AUTHENTICATION_COMPLETE: {
      AuthenticationCompleteView authentication_complete_view = AuthenticationCompleteView::Create(std::move(packet));
      ASSERT(authentication_complete_view.IsValid());
      status = authentication_complete_view.GetStatus();
      connection_handle = authentication_complete_view.GetConnectionHandle();
      break;
    }
    case EventCode::ENCRYPTION_CHANGE: {
      EncryptionChangeView encryption_change_view = EncryptionChangeView::Create(std::move(packet));
      ASSERT(encryption_change_view.IsValid());
      status = encryption_change_view.GetStatus();
      connection_handle = encryption_change_view.GetConnectionHandle();
      value = static_cast<int64_t>(encryption_change_view.GetEncryptionEnabled());
      break;
    }
    default:
      return;
  }
  os::LogMetricClassicPairingEvent(
      address,
      connection_handle,
      static_cast<uint32_t>(cmd),
      static_cast<uint16_t>(event_code),
      static_cast<uint16_t>(status),
      static_cast<uint16_t>(reason),
      value);
}

void log_classic_pairing_command_status(std::unique_ptr<CommandView>& command_view, ErrorCode status) {
  // get op_code
  ASSERT(command_view->IsValid());
  OpCode op_code = command_view->GetOpCode();

  // init parameters
  Address address = Address::kEmpty;
  ErrorCode reason = ErrorCode::UNKNOWN_HCI_COMMAND;
  uint32_t connection_handle = bluetooth::os::kUnknownConnectionHandle;
  int64_t value = 0;
  uint16_t event_code = android::bluetooth::hci::EVT_COMMAND_STATUS;

  // create SecurityCommandView
  SecurityCommandView security_command_view = SecurityCommandView::Create(*command_view);
  ASSERT(security_command_view.IsValid());

  // create ConnectionManagementCommandView
  ConnectionManagementCommandView connection_management_command_view =
      ConnectionManagementCommandView::Create(AclCommandView::Create(*command_view));
  ASSERT(connection_management_command_view.IsValid());

  // create DiscoveryCommandView
  DiscoveryCommandView discovery_command_view = DiscoveryCommandView::Create(*command_view);
  ASSERT(discovery_command_view.IsValid());

  switch (op_code) {
    case OpCode::READ_LOCAL_OOB_DATA: {
      ReadLocalOobDataView read_local_oob_data_view = ReadLocalOobDataView::Create(std::move(security_command_view));
      ASSERT(read_local_oob_data_view.IsValid());
      break;
    }
    case OpCode::WRITE_SIMPLE_PAIRING_MODE: {
      WriteSimplePairingModeView write_simple_pairing_mode_view
      = WriteSimplePairingModeView::Create(std::move(security_command_view));
      ASSERT(write_simple_pairing_mode_view.IsValid());
      value = static_cast<int64_t>(write_simple_pairing_mode_view.GetSimplePairingMode());
      break;
    }
    case OpCode::WRITE_SECURE_CONNECTIONS_HOST_SUPPORT: {
      WriteSecureConnectionsHostSupportView write_secure_connections_host_support_view
      = WriteSecureConnectionsHostSupportView::Create(std::move(security_command_view));
      ASSERT(write_secure_connections_host_support_view.IsValid());
      value = static_cast<int64_t>(write_secure_connections_host_support_view.GetSecureConnectionsHostSupport());
      break;
    }
    case OpCode::AUTHENTICATION_REQUESTED: {
      AuthenticationRequestedView authentication_requested_view
      = AuthenticationRequestedView::Create(std::move(connection_management_command_view));
      ASSERT(authentication_requested_view.IsValid());
      connection_handle = authentication_requested_view.GetConnectionHandle();
      break;
    }
    case OpCode::SET_CONNECTION_ENCRYPTION: {
      SetConnectionEncryptionView set_connection_encryption_view
      = SetConnectionEncryptionView::Create(std::move(connection_management_command_view));
      ASSERT(set_connection_encryption_view.IsValid());
      connection_handle = set_connection_encryption_view.GetConnectionHandle();
      value = static_cast<int64_t>(set_connection_encryption_view.GetEncryptionEnable());
      break;
    }
    case OpCode::REMOTE_NAME_REQUEST: {
      RemoteNameRequestView remote_name_request_view = RemoteNameRequestView::Create(std::move(discovery_command_view));
      ASSERT(remote_name_request_view.IsValid());
      address = remote_name_request_view.GetBdAddr();
      break;
    }
    case OpCode::REMOTE_NAME_REQUEST_CANCEL: {
      RemoteNameRequestCancelView remote_name_request_cancel_view
      = RemoteNameRequestCancelView::Create(std::move(discovery_command_view));
      ASSERT(remote_name_request_cancel_view.IsValid());
      address = remote_name_request_cancel_view.GetBdAddr();
      break;
    }
    case OpCode::LINK_KEY_REQUEST_REPLY: {
      LinkKeyRequestReplyView link_key_request_reply_view
      = LinkKeyRequestReplyView::Create(std::move(security_command_view));
      ASSERT(link_key_request_reply_view.IsValid());
      address = link_key_request_reply_view.GetBdAddr();
      break;
    }
    case OpCode::LINK_KEY_REQUEST_NEGATIVE_REPLY: {
      LinkKeyRequestNegativeReplyView link_key_request_negative_reply_view
      = LinkKeyRequestNegativeReplyView::Create(std::move(security_command_view));
      ASSERT(link_key_request_negative_reply_view.IsValid());
      address = link_key_request_negative_reply_view.GetBdAddr();
      break;
    }
    case OpCode::IO_CAPABILITY_REQUEST_REPLY: {
      IoCapabilityRequestReplyView io_capability_request_reply_view
      = IoCapabilityRequestReplyView::Create(std::move(security_command_view));
      ASSERT(io_capability_request_reply_view.IsValid());
      address = io_capability_request_reply_view.GetBdAddr();
      break;
    }
    case OpCode::USER_CONFIRMATION_REQUEST_REPLY: {
      UserConfirmationRequestReplyView user_confirmation_request_reply
      = UserConfirmationRequestReplyView::Create(std::move(security_command_view));
      ASSERT(user_confirmation_request_reply.IsValid());
      address = user_confirmation_request_reply.GetBdAddr();
      break;
    }
    case OpCode::USER_CONFIRMATION_REQUEST_NEGATIVE_REPLY: {
      UserConfirmationRequestNegativeReplyView user_confirmation_request_negative_reply
      = UserConfirmationRequestNegativeReplyView::Create(std::move(security_command_view));
      ASSERT(user_confirmation_request_negative_reply.IsValid());
      address = user_confirmation_request_negative_reply.GetBdAddr();
      break;
    }
    case OpCode::USER_PASSKEY_REQUEST_REPLY: {
      UserPasskeyRequestReplyView user_passkey_request_reply
      = UserPasskeyRequestReplyView::Create(std::move(security_command_view));
      ASSERT(user_passkey_request_reply.IsValid());
      address = user_passkey_request_reply.GetBdAddr();
      break;
    }
    case OpCode::USER_PASSKEY_REQUEST_NEGATIVE_REPLY: {
      UserPasskeyRequestNegativeReplyView user_passkey_request_negative_reply
      = UserPasskeyRequestNegativeReplyView::Create(std::move(security_command_view));
      ASSERT(user_passkey_request_negative_reply.IsValid());
      address = user_passkey_request_negative_reply.GetBdAddr();
      break;
    }
    case OpCode::REMOTE_OOB_DATA_REQUEST_REPLY: {
      RemoteOobDataRequestReplyView remote_oob_data_request_reply_view
      = RemoteOobDataRequestReplyView::Create(std::move(security_command_view));
      if (!remote_oob_data_request_reply_view.IsValid()) {
        LOG_WARN("remote_oob_data_request_reply_view is not valid.");
        return;
      }
      address = remote_oob_data_request_reply_view.GetBdAddr();
      break;
    }
    case OpCode::REMOTE_OOB_DATA_REQUEST_NEGATIVE_REPLY: {
      RemoteOobDataRequestNegativeReplyView remote_oob_data_request_negative_reply_view
      = RemoteOobDataRequestNegativeReplyView::Create(std::move(security_command_view));
      if (!remote_oob_data_request_negative_reply_view.IsValid()) {
        LOG_WARN("remote_oob_data_request_negative_reply_view is not valid.");
        return;
      }
      address = remote_oob_data_request_negative_reply_view.GetBdAddr();
      break;
    }
    case OpCode::IO_CAPABILITY_REQUEST_NEGATIVE_REPLY: {
      IoCapabilityRequestNegativeReplyView io_capability_request_negative_reply_view
      = IoCapabilityRequestNegativeReplyView::Create(std::move(security_command_view));
      ASSERT(io_capability_request_negative_reply_view.IsValid());
      address = io_capability_request_negative_reply_view.GetBdAddr();
      reason = io_capability_request_negative_reply_view.GetReason();
      break;
    }
    default:
      return;
  }
  os::LogMetricClassicPairingEvent(
      address,
      connection_handle,
      static_cast<uint32_t>(op_code),
      static_cast<uint16_t>(event_code),
      static_cast<uint16_t>(status),
      static_cast<uint16_t>(reason),
      value);
}

void log_classic_pairing_command_complete(EventView event_view, std::unique_ptr<CommandView>& command_view) {

  // get op_code
  CommandCompleteView command_complete_view = CommandCompleteView::Create(std::move(event_view));
  ASSERT(command_complete_view.IsValid());
  OpCode op_code = command_complete_view.GetCommandOpCode();

  // init parameters
  Address address = Address::kEmpty;
  ErrorCode status = ErrorCode::UNKNOWN_HCI_COMMAND;
  ErrorCode reason = ErrorCode::UNKNOWN_HCI_COMMAND;
  uint32_t connection_handle = bluetooth::os::kUnknownConnectionHandle;
  int64_t value = 0;
  EventCode event_code = EventCode::COMMAND_COMPLETE;

  // get ConnectionManagementCommandView
  ConnectionManagementCommandView connection_management_command_view =
      ConnectionManagementCommandView::Create(AclCommandView::Create(*command_view));
  ASSERT(connection_management_command_view.IsValid());

  // create SecurityCommandView
  SecurityCommandView security_command_view = SecurityCommandView::Create(*command_view);
  ASSERT(security_command_view.IsValid());

  switch (op_code) {
    case OpCode::READ_LOCAL_OOB_DATA: {
      auto read_local_oob_data_complete_view = ReadLocalOobDataCompleteView::Create(std::move(command_complete_view));
      ASSERT(read_local_oob_data_complete_view.IsValid());
      status = read_local_oob_data_complete_view.GetStatus();
      break;
    }
    case OpCode::WRITE_SIMPLE_PAIRING_MODE: {
      auto write_simple_pairing_mode_complete_view = WriteSimplePairingModeCompleteView::Create(std::move(command_complete_view));
      ASSERT(write_simple_pairing_mode_complete_view.IsValid());
      status = write_simple_pairing_mode_complete_view.GetStatus();
      break;
    }
    case OpCode::WRITE_SECURE_CONNECTIONS_HOST_SUPPORT: {
      auto write_secure_connections_host_support_complete_view = WriteSecureConnectionsHostSupportCompleteView::Create(std::move(command_complete_view));
      ASSERT(write_secure_connections_host_support_complete_view.IsValid());
      status = write_secure_connections_host_support_complete_view.GetStatus();
      break;
    }
    case OpCode::READ_ENCRYPTION_KEY_SIZE: {
      auto read_encryption_key_size_complete_view = ReadEncryptionKeySizeCompleteView::Create(std::move(command_complete_view));
      ASSERT(read_encryption_key_size_complete_view.IsValid());
      status = read_encryption_key_size_complete_view.GetStatus();
      connection_handle = read_encryption_key_size_complete_view.GetConnectionHandle();
      value = read_encryption_key_size_complete_view.GetKeySize();
      break;
    }
    case OpCode::LINK_KEY_REQUEST_REPLY: {
      auto link_key_request_reply_complete_view = LinkKeyRequestReplyCompleteView::Create(std::move(command_complete_view));
      ASSERT(link_key_request_reply_complete_view.IsValid());
      status = link_key_request_reply_complete_view.GetStatus();
      auto link_key_request_reply_view = LinkKeyRequestReplyView::Create(std::move(security_command_view));
      ASSERT(link_key_request_reply_view.IsValid());
      address = link_key_request_reply_view.GetBdAddr();
      break;
    }
    case OpCode::LINK_KEY_REQUEST_NEGATIVE_REPLY: {
      auto link_key_request_negative_reply_complete_view = LinkKeyRequestNegativeReplyCompleteView::Create(std::move(command_complete_view));
      ASSERT(link_key_request_negative_reply_complete_view.IsValid());
      status = link_key_request_negative_reply_complete_view.GetStatus();
      auto link_key_request_negative_reply_view = LinkKeyRequestNegativeReplyView::Create(std::move(security_command_view));
      ASSERT(link_key_request_negative_reply_view.IsValid());
      address = link_key_request_negative_reply_view.GetBdAddr();
      break;
    }
    case OpCode::IO_CAPABILITY_REQUEST_REPLY: {
      auto io_capability_request_reply_complete_view = IoCapabilityRequestReplyCompleteView::Create(std::move(command_complete_view));
      ASSERT(io_capability_request_reply_complete_view.IsValid());
      status = io_capability_request_reply_complete_view.GetStatus();
      auto io_capability_request_reply_view = IoCapabilityRequestReplyView::Create(std::move(security_command_view));
      ASSERT(io_capability_request_reply_view.IsValid());
      address = io_capability_request_reply_view.GetBdAddr();
      break;
    }
    case OpCode::IO_CAPABILITY_REQUEST_NEGATIVE_REPLY: {
      auto io_capability_request_negative_reply_complete_view = IoCapabilityRequestNegativeReplyCompleteView::Create(std::move(command_complete_view));
      ASSERT(io_capability_request_negative_reply_complete_view.IsValid());
      status = io_capability_request_negative_reply_complete_view.GetStatus();
      auto io_capability_request_negative_reply_view = IoCapabilityRequestNegativeReplyView::Create(std::move(security_command_view));
      ASSERT(io_capability_request_negative_reply_view.IsValid());
      address = io_capability_request_negative_reply_view.GetBdAddr();
      break;
    }
    case OpCode::USER_CONFIRMATION_REQUEST_REPLY: {
      auto user_confirmation_request_reply_complete_view = UserConfirmationRequestReplyCompleteView::Create(std::move(command_complete_view));
      ASSERT(user_confirmation_request_reply_complete_view.IsValid());
      status = user_confirmation_request_reply_complete_view.GetStatus();
      auto user_confirmation_request_reply_view = UserConfirmationRequestReplyView::Create(std::move(security_command_view));
      ASSERT(user_confirmation_request_reply_view.IsValid());
      address = user_confirmation_request_reply_view.GetBdAddr();
      break;
    }
    case OpCode::USER_CONFIRMATION_REQUEST_NEGATIVE_REPLY: {
      auto user_confirmation_request_negative_reply_complete_view = UserConfirmationRequestNegativeReplyCompleteView::Create(std::move(command_complete_view));
      ASSERT(user_confirmation_request_negative_reply_complete_view.IsValid());
      status = user_confirmation_request_negative_reply_complete_view.GetStatus();
      auto user_confirmation_request_negative_reply_view = UserConfirmationRequestNegativeReplyView::Create(std::move(security_command_view));
      ASSERT(user_confirmation_request_negative_reply_view.IsValid());
      address = user_confirmation_request_negative_reply_view.GetBdAddr();
      break;
    }
    case OpCode::USER_PASSKEY_REQUEST_REPLY: {
      auto user_passkey_request_reply_complete_view = UserPasskeyRequestReplyCompleteView::Create(std::move(command_complete_view));
      ASSERT(user_passkey_request_reply_complete_view.IsValid());
      status = user_passkey_request_reply_complete_view.GetStatus();
      auto user_passkey_request_reply_view = UserPasskeyRequestReplyView::Create(std::move(security_command_view));
      ASSERT(user_passkey_request_reply_view.IsValid());
      address = user_passkey_request_reply_view.GetBdAddr();
      break;
    }
    case OpCode::USER_PASSKEY_REQUEST_NEGATIVE_REPLY: {
      auto user_passkey_request_negative_reply_complete_view = UserPasskeyRequestNegativeReplyCompleteView::Create(std::move(command_complete_view));
      ASSERT(user_passkey_request_negative_reply_complete_view.IsValid());
      status = user_passkey_request_negative_reply_complete_view.GetStatus();
      auto user_passkey_request_negative_reply_view = UserPasskeyRequestNegativeReplyView::Create(std::move(security_command_view));
      ASSERT(user_passkey_request_negative_reply_view.IsValid());
      address = user_passkey_request_negative_reply_view.GetBdAddr();
      break;
    }
    case OpCode::REMOTE_OOB_DATA_REQUEST_REPLY: {
      auto remote_oob_data_request_reply_complete_view = RemoteOobDataRequestReplyCompleteView::Create(std::move(command_complete_view));
      if (!remote_oob_data_request_reply_complete_view.IsValid()) {
        LOG_WARN("remote_oob_data_request_reply_complete_view is not valid.");
        return;
      }
      status = remote_oob_data_request_reply_complete_view.GetStatus();
      auto remote_oob_data_request_reply_view = RemoteOobDataRequestReplyView::Create(std::move(security_command_view));
      if (!remote_oob_data_request_reply_view.IsValid()) {
        LOG_WARN("remote_oob_data_request_reply_view is not valid.");
        return;
      }
      address = remote_oob_data_request_reply_view.GetBdAddr();
      break;
    }
    case OpCode::REMOTE_OOB_DATA_REQUEST_NEGATIVE_REPLY: {
      auto remote_oob_data_request_negative_reply_complete_view = RemoteOobDataRequestNegativeReplyCompleteView::Create(std::move(command_complete_view));
      if (!remote_oob_data_request_negative_reply_complete_view.IsValid()) {
        LOG_WARN("remote_oob_data_request_negative_reply_complete_view is not valid.");
        return;
      }
      status = remote_oob_data_request_negative_reply_complete_view.GetStatus();
      auto remote_oob_data_request_negative_reply_view = RemoteOobDataRequestNegativeReplyView::Create(std::move(security_command_view));
      if (!remote_oob_data_request_negative_reply_view.IsValid()) {
        LOG_WARN("remote_oob_data_request_negative_reply_view is not valid.");
        return;
      }
      address = remote_oob_data_request_negative_reply_view.GetBdAddr();
      break;
    }
    default:
      return;
  }
  os::LogMetricClassicPairingEvent(
      address,
      connection_handle,
      static_cast<uint32_t>(op_code),
      static_cast<uint16_t>(event_code),
      static_cast<uint16_t>(status),
      static_cast<uint16_t>(reason),
      value);
}

void log_remote_device_information(
    const Address& address,
    android::bluetooth::AddressTypeEnum address_type,
    uint32_t connection_handle,
    ErrorCode status,
    storage::StorageModule* storage_module) {
  if (address.IsEmpty()) {
    return;
  }
  const storage::Device device = storage_module->GetDeviceByLegacyKey(address);
  // log ManufacturerInfo
  std::stringstream sdp_di_vendor_id_source;
  // [N - native]::SDP::[DIP - Device ID Profile]
  sdp_di_vendor_id_source << "N:SDP::DIP::" << common::ToHexString(device.GetSdpDiVendorIdSource().value_or(0)).c_str();
  os::LogMetricManufacturerInfo(
      address,
      address_type,
      android::bluetooth::DeviceInfoSrcEnum::DEVICE_INFO_INTERNAL,
      sdp_di_vendor_id_source.str(),
      common::ToHexString(device.GetSdpDiManufacturer().value_or(0)).c_str(),
      common::ToHexString(device.GetSdpDiModel().value_or(0)).c_str(),
      common::ToHexString(device.GetSdpDiHardwareVersion().value_or(0)).c_str(),
      "");

  // log RemoteVersionInfo
  os::LogMetricRemoteVersionInfo(
      connection_handle,
      static_cast<uint16_t>(status),
      device.GetLmpVersion().value_or(-1),
      device.GetManufacturerCode().value_or(-1),
      device.GetLmpSubVersion().value_or(-1));
}

}  // namespace hci
}  // namespace bluetooth
