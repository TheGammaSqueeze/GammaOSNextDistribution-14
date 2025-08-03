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

#pragma once

#include <memory>

#include "common/bind.h"
#include "common/init_flags.h"
#include "hci/acl_manager/acl_scheduler.h"
#include "hci/acl_manager/assembler.h"
#include "hci/acl_manager/round_robin_scheduler.h"
#include "hci/class_of_device.h"
#include "hci/controller.h"
#include "hci/event_checkers.h"
#include "hci/remote_name_request.h"
#include "os/metrics.h"
#include "security/security_manager_listener.h"
#include "security/security_module.h"

namespace bluetooth {
namespace hci {
namespace acl_manager {

struct acl_connection {
  acl_connection(AddressWithType address_with_type, AclConnection::QueueDownEnd* queue_down_end, os::Handler* handler)
      : address_with_type_(address_with_type),
        assembler_(new acl_manager::assembler(address_with_type, queue_down_end, handler)) {}
  ~acl_connection() {
    delete assembler_;
  }
  AddressWithType address_with_type_;
  struct acl_manager::assembler* assembler_;
  ConnectionManagementCallbacks* connection_management_callbacks_ = nullptr;
};

struct classic_impl : public security::ISecurityManagerListener {
  classic_impl(
      HciLayer* hci_layer,
      Controller* controller,
      os::Handler* handler,
      RoundRobinScheduler* round_robin_scheduler,
      bool crash_on_unknown_handle,
      AclScheduler* acl_scheduler,
      RemoteNameRequestModule* remote_name_request_module)
      : hci_layer_(hci_layer),
        controller_(controller),
        round_robin_scheduler_(round_robin_scheduler),
        acl_scheduler_(acl_scheduler),
        remote_name_request_module_(remote_name_request_module) {
    hci_layer_ = hci_layer;
    controller_ = controller;
    handler_ = handler;
    connections.crash_on_unknown_handle_ = crash_on_unknown_handle;
    should_accept_connection_ = common::Bind([](Address, ClassOfDevice) { return true; });
    acl_connection_interface_ = hci_layer_->GetAclConnectionInterface(
        handler_->BindOn(this, &classic_impl::on_classic_event),
        handler_->BindOn(this, &classic_impl::on_classic_disconnect),
        handler_->BindOn(this, &classic_impl::on_incoming_connection),
        handler_->BindOn(this, &classic_impl::on_read_remote_version_information));
  }

  ~classic_impl() {
    hci_layer_->PutAclConnectionInterface();
    connections.reset();
    security_manager_.reset();
  }

  void on_classic_event(EventView event_packet) {
    EventCode event_code = event_packet.GetEventCode();
    switch (event_code) {
      case EventCode::CONNECTION_COMPLETE:
        on_connection_complete(event_packet);
        break;
      case EventCode::CONNECTION_PACKET_TYPE_CHANGED:
        on_connection_packet_type_changed(event_packet);
        break;
      case EventCode::AUTHENTICATION_COMPLETE:
        on_authentication_complete(event_packet);
        break;
      case EventCode::READ_CLOCK_OFFSET_COMPLETE:
        on_read_clock_offset_complete(event_packet);
        break;
      case EventCode::MODE_CHANGE:
        on_mode_change(event_packet);
        break;
      case EventCode::SNIFF_SUBRATING:
        on_sniff_subrating(event_packet);
        break;
      case EventCode::QOS_SETUP_COMPLETE:
        on_qos_setup_complete(event_packet);
        break;
      case EventCode::ROLE_CHANGE:
        on_role_change(event_packet);
        break;
      case EventCode::FLOW_SPECIFICATION_COMPLETE:
        on_flow_specification_complete(event_packet);
        break;
      case EventCode::FLUSH_OCCURRED:
        on_flush_occurred(event_packet);
        break;
      case EventCode::READ_REMOTE_SUPPORTED_FEATURES_COMPLETE:
        on_read_remote_supported_features_complete(event_packet);
        break;
      case EventCode::READ_REMOTE_EXTENDED_FEATURES_COMPLETE:
        on_read_remote_extended_features_complete(event_packet);
        break;
      case EventCode::LINK_SUPERVISION_TIMEOUT_CHANGED:
        on_link_supervision_timeout_changed(event_packet);
        break;
      case EventCode::CENTRAL_LINK_KEY_COMPLETE:
        on_central_link_key_complete(event_packet);
        break;
      default:
        LOG_ALWAYS_FATAL("Unhandled event code %s", EventCodeText(event_code).c_str());
    }
  }

 private:
  static constexpr uint16_t kIllegalConnectionHandle = 0xffff;
  struct {
   private:
    std::map<uint16_t, acl_connection> acl_connections_;
    mutable std::mutex acl_connections_guard_;
    ConnectionManagementCallbacks* find_callbacks(uint16_t handle) {
      auto connection = acl_connections_.find(handle);
      if (connection == acl_connections_.end()) return nullptr;
      return connection->second.connection_management_callbacks_;
    }
    ConnectionManagementCallbacks* find_callbacks(const Address& address) {
      for (auto& connection_pair : acl_connections_) {
        if (connection_pair.second.address_with_type_.GetAddress() == address) {
          return connection_pair.second.connection_management_callbacks_;
        }
      }
      return nullptr;
    }
    void remove(uint16_t handle) {
      auto connection = acl_connections_.find(handle);
      if (connection != acl_connections_.end()) {
        connection->second.connection_management_callbacks_ = nullptr;
        acl_connections_.erase(handle);
      }
    }

   public:
    bool crash_on_unknown_handle_ = false;
    bool is_empty() const {
      std::unique_lock<std::mutex> lock(acl_connections_guard_);
      return acl_connections_.empty();
    }
    void reset() {
      std::unique_lock<std::mutex> lock(acl_connections_guard_);
      acl_connections_.clear();
    }
    void invalidate(uint16_t handle) {
      std::unique_lock<std::mutex> lock(acl_connections_guard_);
      remove(handle);
    }
    void execute(
        uint16_t handle,
        std::function<void(ConnectionManagementCallbacks* callbacks)> execute,
        bool remove_afterwards = false) {
      std::unique_lock<std::mutex> lock(acl_connections_guard_);
      auto callbacks = find_callbacks(handle);
      if (callbacks != nullptr)
        execute(callbacks);
      else
        ASSERT_LOG(!crash_on_unknown_handle_, "Received command for unknown handle:0x%x", handle);
      if (remove_afterwards) remove(handle);
    }
    void execute(const Address& address, std::function<void(ConnectionManagementCallbacks* callbacks)> execute) {
      std::unique_lock<std::mutex> lock(acl_connections_guard_);
      auto callbacks = find_callbacks(address);
      if (callbacks != nullptr) execute(callbacks);
    }
    bool send_packet_upward(uint16_t handle, std::function<void(struct acl_manager::assembler* assembler)> cb) {
      std::unique_lock<std::mutex> lock(acl_connections_guard_);
      auto connection = acl_connections_.find(handle);
      if (connection != acl_connections_.end()) cb(connection->second.assembler_);
      return connection != acl_connections_.end();
    }
    void add(
        uint16_t handle,
        const AddressWithType& remote_address,
        AclConnection::QueueDownEnd* queue_end,
        os::Handler* handler,
        ConnectionManagementCallbacks* connection_management_callbacks) {
      std::unique_lock<std::mutex> lock(acl_connections_guard_);
      auto emplace_pair = acl_connections_.emplace(
          std::piecewise_construct,
          std::forward_as_tuple(handle),
          std::forward_as_tuple(remote_address, queue_end, handler));
      ASSERT(emplace_pair.second);  // Make sure the connection is unique
      emplace_pair.first->second.connection_management_callbacks_ = connection_management_callbacks;
    }
    uint16_t HACK_get_handle(const Address& address) const {
      std::unique_lock<std::mutex> lock(acl_connections_guard_);
      for (auto it = acl_connections_.begin(); it != acl_connections_.end(); it++) {
        if (it->second.address_with_type_.GetAddress() == address) {
          return it->first;
        }
      }
      return kIllegalConnectionHandle;
    }
    Address get_address(uint16_t handle) const {
      std::unique_lock<std::mutex> lock(acl_connections_guard_);
      auto connection = acl_connections_.find(handle);
      if (connection == acl_connections_.end()) {
        return Address::kEmpty;
      }
      return connection->second.address_with_type_.GetAddress();
    }
    bool is_classic_link_already_connected(const Address& address) const {
      std::unique_lock<std::mutex> lock(acl_connections_guard_);
      for (const auto& connection : acl_connections_) {
        if (connection.second.address_with_type_.GetAddress() == address) {
          return true;
        }
      }
      return false;
    }
  } connections;

 public:
  bool send_packet_upward(uint16_t handle, std::function<void(struct acl_manager::assembler* assembler)> cb) {
    return connections.send_packet_upward(handle, cb);
  }

  void on_incoming_connection(Address address, ClassOfDevice cod) {
    if (client_callbacks_ == nullptr) {
      LOG_ERROR("No callbacks to call");
      auto reason = RejectConnectionReason::LIMITED_RESOURCES;
      this->reject_connection(RejectConnectionRequestBuilder::Create(address, reason));
      return;
    }

    client_handler_->CallOn(
        client_callbacks_, &ConnectionCallbacks::OnConnectRequest, address, cod);

    acl_scheduler_->RegisterPendingIncomingConnection(address);

    if (is_classic_link_already_connected(address)) {
      auto reason = RejectConnectionReason::UNACCEPTABLE_BD_ADDR;
      this->reject_connection(RejectConnectionRequestBuilder::Create(address, reason));
    } else if (should_accept_connection_.Run(address, cod)) {
      this->accept_connection(address);
    } else {
      auto reason = RejectConnectionReason::LIMITED_RESOURCES;  // TODO: determine reason
      this->reject_connection(RejectConnectionRequestBuilder::Create(address, reason));
    }
  }

  bool is_classic_link_already_connected(Address address) {
    return connections.is_classic_link_already_connected(address);
  }

  void create_connection(Address address) {
    // TODO: Configure default connection parameters?
    uint16_t packet_type = 0x4408 /* DM 1,3,5 */ | 0x8810 /*DH 1,3,5 */;
    PageScanRepetitionMode page_scan_repetition_mode = PageScanRepetitionMode::R1;
    uint16_t clock_offset = 0;
    ClockOffsetValid clock_offset_valid = ClockOffsetValid::INVALID;
    CreateConnectionRoleSwitch allow_role_switch = CreateConnectionRoleSwitch::ALLOW_ROLE_SWITCH;
    ASSERT(client_callbacks_ != nullptr);
    std::unique_ptr<CreateConnectionBuilder> packet = CreateConnectionBuilder::Create(
        address, packet_type, page_scan_repetition_mode, clock_offset, clock_offset_valid, allow_role_switch);

    acl_scheduler_->EnqueueOutgoingAclConnection(
        address, handler_->BindOnceOn(this, &classic_impl::actually_create_connection, address, std::move(packet)));
  }

  void actually_create_connection(Address address, std::unique_ptr<CreateConnectionBuilder> packet) {
    if (is_classic_link_already_connected(address)) {
      LOG_WARN("already connected: %s", ADDRESS_TO_LOGGABLE_CSTR(address));
      acl_scheduler_->ReportOutgoingAclConnectionFailure();
      return;
    }
    acl_connection_interface_->EnqueueCommand(
        std::move(packet), handler_->BindOnceOn(this, &classic_impl::on_create_connection_status, address));
  }

  void on_create_connection_status(Address address, CommandStatusView status) {
    ASSERT(status.IsValid());
    ASSERT(status.GetCommandOpCode() == OpCode::CREATE_CONNECTION);
    if (status.GetStatus() != hci::ErrorCode::SUCCESS /* = pending */) {
      // something went wrong, but unblock queue and report to caller
      LOG_ERROR("Failed to create connection, reporting failure and continuing");
      ASSERT(client_callbacks_ != nullptr);
      client_handler_->Post(common::BindOnce(
          &ConnectionCallbacks::OnConnectFail,
          common::Unretained(client_callbacks_),
          address,
          status.GetStatus(),
          true /* locally initiated */));
      acl_scheduler_->ReportOutgoingAclConnectionFailure();
    } else {
      // everything is good, resume when a connection_complete event arrives
      return;
    }
  }

  enum class Initiator {
    LOCALLY_INITIATED,
    REMOTE_INITIATED,
  };

  void create_and_announce_connection(
      ConnectionCompleteView connection_complete, Role current_role, Initiator initiator) {
    auto status = connection_complete.GetStatus();
    auto address = connection_complete.GetBdAddr();
    if (client_callbacks_ == nullptr) {
      LOG_WARN("No client callbacks registered for connection");
      return;
    }
    if (status != ErrorCode::SUCCESS) {
      client_handler_->Post(common::BindOnce(
          &ConnectionCallbacks::OnConnectFail,
          common::Unretained(client_callbacks_),
          address,
          status,
          initiator == Initiator::LOCALLY_INITIATED));
      return;
    }
    uint16_t handle = connection_complete.GetConnectionHandle();
    auto queue = std::make_shared<AclConnection::Queue>(10);
    auto queue_down_end = queue->GetDownEnd();
    round_robin_scheduler_->Register(RoundRobinScheduler::ConnectionType::CLASSIC, handle, queue);
    std::unique_ptr<ClassicAclConnection> connection(
        new ClassicAclConnection(std::move(queue), acl_connection_interface_, handle, address));
    connection->locally_initiated_ = initiator == Initiator::LOCALLY_INITIATED;
    connections.add(
        handle,
        AddressWithType{address, AddressType::PUBLIC_DEVICE_ADDRESS},
        queue_down_end,
        handler_,
        connection->GetEventCallbacks([this](uint16_t handle) { this->connections.invalidate(handle); }));
    connections.execute(address, [=](ConnectionManagementCallbacks* callbacks) {
      if (delayed_role_change_ == nullptr) {
        callbacks->OnRoleChange(hci::ErrorCode::SUCCESS, current_role);
      } else if (delayed_role_change_->GetBdAddr() == address) {
        LOG_INFO("Sending delayed role change for %s",
                 ADDRESS_TO_LOGGABLE_CSTR(delayed_role_change_->GetBdAddr()));
        callbacks->OnRoleChange(delayed_role_change_->GetStatus(), delayed_role_change_->GetNewRole());
        delayed_role_change_.reset();
      }
    });
    client_handler_->Post(common::BindOnce(
        &ConnectionCallbacks::OnConnectSuccess, common::Unretained(client_callbacks_), std::move(connection)));
  }

  void on_connection_complete(EventView packet) {
    ConnectionCompleteView connection_complete = ConnectionCompleteView::Create(packet);
    ASSERT(connection_complete.IsValid());
    auto status = connection_complete.GetStatus();
    auto address = connection_complete.GetBdAddr();

    acl_scheduler_->ReportAclConnectionCompletion(
        address,
        handler_->BindOnceOn(
            this,
            &classic_impl::create_and_announce_connection,
            connection_complete,
            Role::CENTRAL,
            Initiator::LOCALLY_INITIATED),
        handler_->BindOnceOn(
            this,
            &classic_impl::create_and_announce_connection,
            connection_complete,
            Role::PERIPHERAL,
            Initiator::REMOTE_INITIATED),
        handler_->BindOnce(
            [=](RemoteNameRequestModule* remote_name_request_module,
                Address address,
                ErrorCode status,
                std::string valid_incoming_addresses) {
              ASSERT_LOG(
                  status == ErrorCode::UNKNOWN_CONNECTION,
                  "No prior connection request for %s expecting:%s",
                  ADDRESS_TO_LOGGABLE_CSTR(address),
                  valid_incoming_addresses.c_str());
              LOG_WARN(
                  "No matching connection to %s (%s)",
                  ADDRESS_TO_LOGGABLE_CSTR(address),
                  ErrorCodeText(status).c_str());
              LOG_WARN("Firmware error after RemoteNameRequestCancel?");  // see b/184239841
              remote_name_request_module->ReportRemoteNameRequestCancellation(address);
            },
            common::Unretained(remote_name_request_module_),
            address,
            status));
  }

  void cancel_connect(Address address) {
    acl_scheduler_->CancelAclConnection(
        address,
        handler_->BindOnceOn(this, &classic_impl::actually_cancel_connect, address),
        client_handler_->BindOnceOn(
            client_callbacks_,
            &ConnectionCallbacks::OnConnectFail,
            address,
            ErrorCode::UNKNOWN_CONNECTION,
            true /* locally initiated */));
  }

  void actually_cancel_connect(Address address) {
    std::unique_ptr<CreateConnectionCancelBuilder> packet = CreateConnectionCancelBuilder::Create(address);
    acl_connection_interface_->EnqueueCommand(
        std::move(packet), handler_->BindOnce(check_complete<CreateConnectionCancelCompleteView>));
  }

  static constexpr bool kRemoveConnectionAfterwards = true;
  void on_classic_disconnect(uint16_t handle, ErrorCode reason) {
    bool event_also_routes_to_other_receivers = connections.crash_on_unknown_handle_;
    bluetooth::os::LogMetricBluetoothDisconnectionReasonReported(
        static_cast<uint32_t>(reason), connections.get_address(handle), handle);
    connections.crash_on_unknown_handle_ = false;
    connections.execute(
        handle,
        [=](ConnectionManagementCallbacks* callbacks) {
          round_robin_scheduler_->Unregister(handle);
          callbacks->OnDisconnection(reason);
        },
        kRemoveConnectionAfterwards);
    connections.crash_on_unknown_handle_ = event_also_routes_to_other_receivers;
  }

  void on_connection_packet_type_changed(EventView packet) {
    ConnectionPacketTypeChangedView packet_type_changed = ConnectionPacketTypeChangedView::Create(packet);
    if (!packet_type_changed.IsValid()) {
      LOG_ERROR("Received on_connection_packet_type_changed with invalid packet");
      return;
    } else if (packet_type_changed.GetStatus() != ErrorCode::SUCCESS) {
      auto status = packet_type_changed.GetStatus();
      std::string error_code = ErrorCodeText(status);
      LOG_ERROR("Received on_connection_packet_type_changed with error code %s", error_code.c_str());
      return;
    }
    uint16_t handle = packet_type_changed.GetConnectionHandle();
    connections.execute(handle, [=](ConnectionManagementCallbacks* /* callbacks */) {
      // We don't handle this event; we didn't do this in legacy stack either.
    });
  }

  void on_central_link_key_complete(EventView packet) {
    CentralLinkKeyCompleteView complete_view = CentralLinkKeyCompleteView::Create(packet);
    if (!complete_view.IsValid()) {
      LOG_ERROR("Received on_central_link_key_complete with invalid packet");
      return;
    } else if (complete_view.GetStatus() != ErrorCode::SUCCESS) {
      auto status = complete_view.GetStatus();
      std::string error_code = ErrorCodeText(status);
      LOG_ERROR("Received on_central_link_key_complete with error code %s", error_code.c_str());
      return;
    }
    uint16_t handle = complete_view.GetConnectionHandle();
    connections.execute(handle, [=](ConnectionManagementCallbacks* callbacks) {
      KeyFlag key_flag = complete_view.GetKeyFlag();
      callbacks->OnCentralLinkKeyComplete(key_flag);
    });
  }

  void on_authentication_complete(EventView packet) {
    AuthenticationCompleteView authentication_complete = AuthenticationCompleteView::Create(packet);
    if (!authentication_complete.IsValid()) {
      LOG_ERROR("Received on_authentication_complete with invalid packet");
      return;
    }
    uint16_t handle = authentication_complete.GetConnectionHandle();
    connections.execute(handle, [=](ConnectionManagementCallbacks* callbacks) {
      callbacks->OnAuthenticationComplete(authentication_complete.GetStatus());
    });
  }

  void on_change_connection_link_key_complete(EventView packet) {
    ChangeConnectionLinkKeyCompleteView complete_view = ChangeConnectionLinkKeyCompleteView::Create(packet);
    if (!complete_view.IsValid()) {
      LOG_ERROR("Received on_change_connection_link_key_complete with invalid packet");
      return;
    } else if (complete_view.GetStatus() != ErrorCode::SUCCESS) {
      auto status = complete_view.GetStatus();
      std::string error_code = ErrorCodeText(status);
      LOG_ERROR("Received on_change_connection_link_key_complete with error code %s", error_code.c_str());
      return;
    }
    uint16_t handle = complete_view.GetConnectionHandle();
    connections.execute(
        handle, [=](ConnectionManagementCallbacks* callbacks) { callbacks->OnChangeConnectionLinkKeyComplete(); });
  }

  void on_read_clock_offset_complete(EventView packet) {
    ReadClockOffsetCompleteView complete_view = ReadClockOffsetCompleteView::Create(packet);
    if (!complete_view.IsValid()) {
      LOG_ERROR("Received on_read_clock_offset_complete with invalid packet");
      return;
    } else if (complete_view.GetStatus() != ErrorCode::SUCCESS) {
      auto status = complete_view.GetStatus();
      std::string error_code = ErrorCodeText(status);
      LOG_ERROR("Received on_read_clock_offset_complete with error code %s", error_code.c_str());
      return;
    }
    uint16_t handle = complete_view.GetConnectionHandle();
    connections.execute(handle, [=](ConnectionManagementCallbacks* callbacks) {
      uint16_t clock_offset = complete_view.GetClockOffset();
      callbacks->OnReadClockOffsetComplete(clock_offset);
    });
  }

  void on_mode_change(EventView packet) {
    ModeChangeView mode_change_view = ModeChangeView::Create(packet);
    if (!mode_change_view.IsValid()) {
      LOG_ERROR("Received on_mode_change with invalid packet");
      return;
    }
    uint16_t handle = mode_change_view.GetConnectionHandle();
    connections.execute(handle, [=](ConnectionManagementCallbacks* callbacks) {
      callbacks->OnModeChange(
          mode_change_view.GetStatus(), mode_change_view.GetCurrentMode(), mode_change_view.GetInterval());
    });
  }

  void on_sniff_subrating(EventView packet) {
    SniffSubratingEventView sniff_subrating_view = SniffSubratingEventView::Create(packet);
    if (!sniff_subrating_view.IsValid()) {
      LOG_ERROR("Received on_sniff_subrating with invalid packet");
      return;
    }
    uint16_t handle = sniff_subrating_view.GetConnectionHandle();
    connections.execute(handle, [=](ConnectionManagementCallbacks* callbacks) {
      callbacks->OnSniffSubrating(
          sniff_subrating_view.GetStatus(),
          sniff_subrating_view.GetMaximumTransmitLatency(),
          sniff_subrating_view.GetMaximumReceiveLatency(),
          sniff_subrating_view.GetMinimumRemoteTimeout(),
          sniff_subrating_view.GetMinimumLocalTimeout());
    });
  }

  void on_qos_setup_complete(EventView packet) {
    QosSetupCompleteView complete_view = QosSetupCompleteView::Create(packet);
    if (!complete_view.IsValid()) {
      LOG_ERROR("Received on_qos_setup_complete with invalid packet");
      return;
    } else if (complete_view.GetStatus() != ErrorCode::SUCCESS) {
      auto status = complete_view.GetStatus();
      std::string error_code = ErrorCodeText(status);
      LOG_ERROR("Received on_qos_setup_complete with error code %s", error_code.c_str());
      return;
    }
    uint16_t handle = complete_view.GetConnectionHandle();
    connections.execute(handle, [=](ConnectionManagementCallbacks* callbacks) {
      ServiceType service_type = complete_view.GetServiceType();
      uint32_t token_rate = complete_view.GetTokenRate();
      uint32_t peak_bandwidth = complete_view.GetPeakBandwidth();
      uint32_t latency = complete_view.GetLatency();
      uint32_t delay_variation = complete_view.GetDelayVariation();
      callbacks->OnQosSetupComplete(service_type, token_rate, peak_bandwidth, latency, delay_variation);
    });
  }

  void on_flow_specification_complete(EventView packet) {
    FlowSpecificationCompleteView complete_view = FlowSpecificationCompleteView::Create(packet);
    if (!complete_view.IsValid()) {
      LOG_ERROR("Received on_flow_specification_complete with invalid packet");
      return;
    } else if (complete_view.GetStatus() != ErrorCode::SUCCESS) {
      auto status = complete_view.GetStatus();
      std::string error_code = ErrorCodeText(status);
      LOG_ERROR("Received on_flow_specification_complete with error code %s", error_code.c_str());
      return;
    }
    uint16_t handle = complete_view.GetConnectionHandle();
    connections.execute(handle, [=](ConnectionManagementCallbacks* callbacks) {
      FlowDirection flow_direction = complete_view.GetFlowDirection();
      ServiceType service_type = complete_view.GetServiceType();
      uint32_t token_rate = complete_view.GetTokenRate();
      uint32_t token_bucket_size = complete_view.GetTokenBucketSize();
      uint32_t peak_bandwidth = complete_view.GetPeakBandwidth();
      uint32_t access_latency = complete_view.GetAccessLatency();
      callbacks->OnFlowSpecificationComplete(
          flow_direction, service_type, token_rate, token_bucket_size, peak_bandwidth, access_latency);
    });
  }

  void on_flush_occurred(EventView packet) {
    FlushOccurredView flush_occurred_view = FlushOccurredView::Create(packet);
    if (!flush_occurred_view.IsValid()) {
      LOG_ERROR("Received on_flush_occurred with invalid packet");
      return;
    }
    uint16_t handle = flush_occurred_view.GetConnectionHandle();
    connections.execute(handle, [=](ConnectionManagementCallbacks* callbacks) { callbacks->OnFlushOccurred(); });
  }

  void on_read_remote_version_information(
      hci::ErrorCode hci_status, uint16_t handle, uint8_t version, uint16_t manufacturer_name, uint16_t sub_version) {
    connections.execute(handle, [=](ConnectionManagementCallbacks* callbacks) {
      callbacks->OnReadRemoteVersionInformationComplete(hci_status, version, manufacturer_name, sub_version);
    });
  }

  void on_read_remote_supported_features_complete(EventView packet) {
    auto view = ReadRemoteSupportedFeaturesCompleteView::Create(packet);
    ASSERT_LOG(view.IsValid(), "Read remote supported features packet invalid");
    uint16_t handle = view.GetConnectionHandle();
    bluetooth::os::LogMetricBluetoothRemoteSupportedFeatures(
        connections.get_address(handle), 0, view.GetLmpFeatures(), handle);
    connections.execute(handle, [=](ConnectionManagementCallbacks* callbacks) {
      callbacks->OnReadRemoteSupportedFeaturesComplete(view.GetLmpFeatures());
    });
  }

  void on_read_remote_extended_features_complete(EventView packet) {
    auto view = ReadRemoteExtendedFeaturesCompleteView::Create(packet);
    ASSERT_LOG(view.IsValid(), "Read remote extended features packet invalid");
    uint16_t handle = view.GetConnectionHandle();
    bluetooth::os::LogMetricBluetoothRemoteSupportedFeatures(
        connections.get_address(handle), view.GetPageNumber(), view.GetExtendedLmpFeatures(), handle);
    connections.execute(handle, [=](ConnectionManagementCallbacks* callbacks) {
      callbacks->OnReadRemoteExtendedFeaturesComplete(
          view.GetPageNumber(), view.GetMaximumPageNumber(), view.GetExtendedLmpFeatures());
    });
  }

  void OnEncryptionStateChanged(EncryptionChangeView encryption_change_view) override {
    if (!encryption_change_view.IsValid()) {
      LOG_ERROR("Invalid packet");
      return;
    } else if (encryption_change_view.GetStatus() != ErrorCode::SUCCESS) {
      auto status = encryption_change_view.GetStatus();
      std::string error_code = ErrorCodeText(status);
      LOG_ERROR("error_code %s", error_code.c_str());
      return;
    }
    uint16_t handle = encryption_change_view.GetConnectionHandle();
    connections.execute(handle, [=](ConnectionManagementCallbacks* callbacks) {
      EncryptionEnabled enabled = encryption_change_view.GetEncryptionEnabled();
      callbacks->OnEncryptionChange(enabled);
    });
  }

  void on_role_change(EventView packet) {
    RoleChangeView role_change_view = RoleChangeView::Create(packet);
    if (!role_change_view.IsValid()) {
      LOG_ERROR("Received on_role_change with invalid packet");
      return;
    }
    auto hci_status = role_change_view.GetStatus();
    Address bd_addr = role_change_view.GetBdAddr();
    Role new_role = role_change_view.GetNewRole();
    bool sent = false;
    connections.execute(bd_addr, [=, &sent](ConnectionManagementCallbacks* callbacks) {
      if (callbacks != nullptr) {
        callbacks->OnRoleChange(hci_status, new_role);
        sent = true;
      }
    });
    if (!sent) {
      if (delayed_role_change_ != nullptr) {
        LOG_WARN(
            "Second delayed role change (@%s dropped)",
            ADDRESS_TO_LOGGABLE_CSTR(delayed_role_change_->GetBdAddr()));
      }
      LOG_INFO(
          "Role change for %s with no matching connection (new role: %s)",
          ADDRESS_TO_LOGGABLE_CSTR(role_change_view.GetBdAddr()),
          RoleText(role_change_view.GetNewRole()).c_str());
      delayed_role_change_ = std::make_unique<RoleChangeView>(role_change_view);
    }
  }

  void on_link_supervision_timeout_changed(EventView packet) {
    auto view = LinkSupervisionTimeoutChangedView::Create(packet);
    ASSERT_LOG(view.IsValid(), "Link supervision timeout changed packet invalid");
    LOG_INFO("UNIMPLEMENTED called");
  }

  void on_accept_connection_status(Address address, CommandStatusView status) {
    auto accept_status = AcceptConnectionRequestStatusView::Create(status);
    ASSERT(accept_status.IsValid());
    if (status.GetStatus() != ErrorCode::SUCCESS) {
      cancel_connect(address);
    }
  }

  void central_link_key(KeyFlag key_flag) {
    std::unique_ptr<CentralLinkKeyBuilder> packet = CentralLinkKeyBuilder::Create(key_flag);
    acl_connection_interface_->EnqueueCommand(
        std::move(packet), handler_->BindOnce(check_status<CentralLinkKeyStatusView>));
  }

  void switch_role(Address address, Role role) {
    std::unique_ptr<SwitchRoleBuilder> packet = SwitchRoleBuilder::Create(address, role);
    acl_connection_interface_->EnqueueCommand(
        std::move(packet), handler_->BindOnce(check_status<SwitchRoleStatusView>));
  }

  void write_default_link_policy_settings(uint16_t default_link_policy_settings) {
    std::unique_ptr<WriteDefaultLinkPolicySettingsBuilder> packet =
        WriteDefaultLinkPolicySettingsBuilder::Create(default_link_policy_settings);
    acl_connection_interface_->EnqueueCommand(
        std::move(packet),
        handler_->BindOnce(check_complete<WriteDefaultLinkPolicySettingsCompleteView>));
  }

  void accept_connection(Address address) {
    auto role = AcceptConnectionRequestRole::BECOME_CENTRAL;  // We prefer to be central
    acl_connection_interface_->EnqueueCommand(
        AcceptConnectionRequestBuilder::Create(address, role),
        handler_->BindOnceOn(this, &classic_impl::on_accept_connection_status, address));
  }

  void reject_connection(std::unique_ptr<RejectConnectionRequestBuilder> builder) {
    acl_connection_interface_->EnqueueCommand(
        std::move(builder), handler_->BindOnce(check_status<RejectConnectionRequestStatusView>));
  }

  void OnDeviceBonded(bluetooth::hci::AddressWithType /* device */) override {}
  void OnDeviceUnbonded(bluetooth::hci::AddressWithType /* device */) override {}
  void OnDeviceBondFailed(
      bluetooth::hci::AddressWithType /* device */,
      security::PairingFailure /* status */) override {}

  void set_security_module(security::SecurityModule* security_module) {
    security_manager_ = security_module->GetSecurityManager();
    security_manager_->RegisterCallbackListener(this, handler_);
  }

  uint16_t HACK_get_handle(Address address) {
    return connections.HACK_get_handle(address);
  }

  void handle_register_callbacks(ConnectionCallbacks* callbacks, os::Handler* handler) {
    ASSERT(client_callbacks_ == nullptr);
    ASSERT(client_handler_ == nullptr);
    client_callbacks_ = callbacks;
    client_handler_ = handler;
  }

  void handle_unregister_callbacks(ConnectionCallbacks* callbacks, std::promise<void> promise) {
    ASSERT_LOG(client_callbacks_ == callbacks, "Registered callback entity is different then unregister request");
    client_callbacks_ = nullptr;
    client_handler_ = nullptr;
    promise.set_value();
  }

  HciLayer* hci_layer_ = nullptr;
  Controller* controller_ = nullptr;
  RoundRobinScheduler* round_robin_scheduler_ = nullptr;
  AclScheduler* acl_scheduler_ = nullptr;
  RemoteNameRequestModule* remote_name_request_module_ = nullptr;
  AclConnectionInterface* acl_connection_interface_ = nullptr;
  os::Handler* handler_ = nullptr;
  ConnectionCallbacks* client_callbacks_ = nullptr;
  os::Handler* client_handler_ = nullptr;

  common::Callback<bool(Address, ClassOfDevice)> should_accept_connection_;
  std::unique_ptr<RoleChangeView> delayed_role_change_ = nullptr;

  std::unique_ptr<security::SecurityManager> security_manager_;
};

}  // namespace acl_manager
}  // namespace hci
}  // namespace bluetooth
