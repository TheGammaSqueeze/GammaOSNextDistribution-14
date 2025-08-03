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

#include "hci/acl_manager/classic_acl_connection.h"

#include "hci/address.h"
#include "hci/event_checkers.h"
#include "os/metrics.h"

using bluetooth::hci::Address;

namespace bluetooth {
namespace hci {
namespace acl_manager {

class AclConnectionTracker : public ConnectionManagementCallbacks {
 public:
  AclConnectionTracker(
      AclConnectionInterface* acl_connection_interface, const Address& address, uint16_t connection_handle)
      : acl_connection_interface_(acl_connection_interface), address_(address), connection_handle_(connection_handle) {}
  ~AclConnectionTracker() {
    // If callbacks were registered, they should have been delivered.
    ASSERT(client_callbacks_ == nullptr || queued_callbacks_.empty());
  }
  void RegisterCallbacks(ConnectionManagementCallbacks* callbacks, os::Handler* handler) {
    client_handler_ = handler;
    client_callbacks_ = callbacks;
    while (!queued_callbacks_.empty()) {
      auto iter = queued_callbacks_.begin();
      handler->Post(std::move(*iter));
      queued_callbacks_.erase(iter);
    }
  }

#define SAVE_OR_CALL(f, ...)                                                                                        \
  if (client_handler_ == nullptr) {                                                                                 \
    queued_callbacks_.emplace_back(                                                                                 \
        common::BindOnce(&ConnectionManagementCallbacks::f, common::Unretained(this), ##__VA_ARGS__));              \
  } else {                                                                                                          \
    client_handler_->Post(                                                                                          \
        common::BindOnce(&ConnectionManagementCallbacks::f, common::Unretained(client_callbacks_), ##__VA_ARGS__)); \
  }

  void OnConnectionPacketTypeChanged(uint16_t packet_type) override {
    SAVE_OR_CALL(OnConnectionPacketTypeChanged, packet_type)
  }
  void OnAuthenticationComplete(hci::ErrorCode hci_status) override {
    SAVE_OR_CALL(OnAuthenticationComplete, hci_status)
  }
  void OnEncryptionChange(EncryptionEnabled enabled) override {
    SAVE_OR_CALL(OnEncryptionChange, enabled)
  }
  void OnChangeConnectionLinkKeyComplete() override {
    SAVE_OR_CALL(OnChangeConnectionLinkKeyComplete)
  }
  void OnReadClockOffsetComplete(uint16_t clock_offset) override {
    SAVE_OR_CALL(OnReadClockOffsetComplete, clock_offset)
  }
  void OnModeChange(ErrorCode status, Mode current_mode, uint16_t interval) override {
    SAVE_OR_CALL(OnModeChange, status, current_mode, interval)
  }
  void OnSniffSubrating(
      hci::ErrorCode hci_status,
      uint16_t maximum_transmit_latency,
      uint16_t maximum_receive_latency,
      uint16_t minimum_remote_timeout,
      uint16_t minimum_local_timeout) override {
    SAVE_OR_CALL(
        OnSniffSubrating,
        hci_status,
        maximum_transmit_latency,
        maximum_receive_latency,
        minimum_remote_timeout,
        minimum_local_timeout);
  }
  void OnQosSetupComplete(ServiceType service_type, uint32_t token_rate, uint32_t peak_bandwidth, uint32_t latency,
                          uint32_t delay_variation) override {
    SAVE_OR_CALL(OnQosSetupComplete, service_type, token_rate, peak_bandwidth, latency, delay_variation)
  }
  void OnFlowSpecificationComplete(FlowDirection flow_direction, ServiceType service_type, uint32_t token_rate,
                                   uint32_t token_bucket_size, uint32_t peak_bandwidth,
                                   uint32_t access_latency) override {
    SAVE_OR_CALL(OnFlowSpecificationComplete, flow_direction, service_type, token_rate, token_bucket_size,
                 peak_bandwidth, access_latency)
  }
  void OnFlushOccurred() override {
    SAVE_OR_CALL(OnFlushOccurred)
  }
  void OnRoleDiscoveryComplete(Role current_role) override {
    SAVE_OR_CALL(OnRoleDiscoveryComplete, current_role)
  }
  void OnReadLinkPolicySettingsComplete(uint16_t link_policy_settings) override {
    SAVE_OR_CALL(OnReadLinkPolicySettingsComplete, link_policy_settings)
  }
  void OnReadAutomaticFlushTimeoutComplete(uint16_t flush_timeout) override {
    SAVE_OR_CALL(OnReadAutomaticFlushTimeoutComplete, flush_timeout)
  }
  void OnReadTransmitPowerLevelComplete(uint8_t transmit_power_level) override {
    bluetooth::os::LogMetricReadTxPowerLevelResult(
        address_, connection_handle_, static_cast<uint8_t>(ErrorCode::SUCCESS), transmit_power_level);
    SAVE_OR_CALL(OnReadTransmitPowerLevelComplete, transmit_power_level)
  }
  void OnReadLinkSupervisionTimeoutComplete(uint16_t link_supervision_timeout) override {
    SAVE_OR_CALL(OnReadLinkSupervisionTimeoutComplete, link_supervision_timeout)
  }
  void OnReadFailedContactCounterComplete(uint16_t failed_contact_counter) override {
    bluetooth::os::LogMetricReadFailedContactCounterResult(
        address_, connection_handle_, static_cast<uint8_t>(ErrorCode::SUCCESS), failed_contact_counter);
    SAVE_OR_CALL(OnReadFailedContactCounterComplete, failed_contact_counter);
  }
  void OnReadLinkQualityComplete(uint8_t link_quality) override {
    SAVE_OR_CALL(OnReadLinkQualityComplete, link_quality)
  }
  void OnReadAfhChannelMapComplete(AfhMode afh_mode, std::array<uint8_t, 10> afh_channel_map) override {
    SAVE_OR_CALL(OnReadAfhChannelMapComplete, afh_mode, afh_channel_map)
  }
  void OnReadRssiComplete(uint8_t rssi) override {
    bluetooth::os::LogMetricReadRssiResult(
        address_, connection_handle_, static_cast<uint8_t>(ErrorCode::SUCCESS), rssi);
    SAVE_OR_CALL(OnReadRssiComplete, rssi);
  }
  void OnReadClockComplete(uint32_t clock, uint16_t accuracy) override {
    SAVE_OR_CALL(OnReadClockComplete, clock, accuracy)
  }
  void OnCentralLinkKeyComplete(KeyFlag key_flag) override {
    SAVE_OR_CALL(OnCentralLinkKeyComplete, key_flag)
  }
  void OnRoleChange(hci::ErrorCode hci_status, Role new_role) override {
    SAVE_OR_CALL(OnRoleChange, hci_status, new_role)
  }
  void OnReadRemoteVersionInformationComplete(
      hci::ErrorCode hci_status, uint8_t lmp_version, uint16_t manufacturer_name, uint16_t sub_version) override {
    bluetooth::os::LogMetricRemoteVersionInfo(
        connection_handle_, static_cast<uint8_t>(hci_status), lmp_version, manufacturer_name, sub_version);
    SAVE_OR_CALL(OnReadRemoteVersionInformationComplete, hci_status, lmp_version, manufacturer_name, sub_version);
  }
  void OnReadRemoteSupportedFeaturesComplete(uint64_t features) override {
    SAVE_OR_CALL(OnReadRemoteSupportedFeaturesComplete, features);
  }
  void OnReadRemoteExtendedFeaturesComplete(uint8_t page_number, uint8_t max_page_number, uint64_t features) override {
    SAVE_OR_CALL(OnReadRemoteExtendedFeaturesComplete, page_number, max_page_number, features);
  }
  void OnDisconnection(ErrorCode reason) {
    SAVE_OR_CALL(OnDisconnection, reason);
  }

#undef SAVE_OR_CALL

  void on_role_discovery_complete(CommandCompleteView view) {
    auto complete_view = RoleDiscoveryCompleteView::Create(view);
    if (!complete_view.IsValid()) {
      LOG_ERROR("Received on_role_discovery_complete with invalid packet");
      return;
    } else if (complete_view.GetStatus() != ErrorCode::SUCCESS) {
      auto status = complete_view.GetStatus();
      std::string error_code = ErrorCodeText(status);
      LOG_ERROR("Received on_role_discovery_complete with error code %s", error_code.c_str());
      return;
    }
    OnRoleDiscoveryComplete(complete_view.GetCurrentRole());
  }

  void on_read_link_policy_settings_complete(CommandCompleteView view) {
    auto complete_view = ReadLinkPolicySettingsCompleteView::Create(view);
    if (!complete_view.IsValid()) {
      LOG_ERROR("Received on_read_link_policy_settings_complete with invalid packet");
      return;
    } else if (complete_view.GetStatus() != ErrorCode::SUCCESS) {
      auto status = complete_view.GetStatus();
      std::string error_code = ErrorCodeText(status);
      LOG_ERROR("Received on_read_link_policy_settings_complete with error code %s", error_code.c_str());
      return;
    }
    OnReadLinkPolicySettingsComplete(complete_view.GetLinkPolicySettings());
  }

  void on_read_automatic_flush_timeout_complete(CommandCompleteView view) {
    auto complete_view = ReadAutomaticFlushTimeoutCompleteView::Create(view);
    if (!complete_view.IsValid()) {
      LOG_ERROR("Received on_read_automatic_flush_timeout_complete with invalid packet");
      return;
    } else if (complete_view.GetStatus() != ErrorCode::SUCCESS) {
      auto status = complete_view.GetStatus();
      std::string error_code = ErrorCodeText(status);
      LOG_ERROR("Received on_read_automatic_flush_timeout_complete with error code %s", error_code.c_str());
      return;
    }
    OnReadAutomaticFlushTimeoutComplete(complete_view.GetFlushTimeout());
  }

  void on_read_transmit_power_level_complete(CommandCompleteView view) {
    auto complete_view = ReadTransmitPowerLevelCompleteView::Create(view);
    if (!complete_view.IsValid()) {
      LOG_ERROR("Received on_read_transmit_power_level_complete with invalid packet");
      return;
    } else if (complete_view.GetStatus() != ErrorCode::SUCCESS) {
      auto status = complete_view.GetStatus();
      std::string error_code = ErrorCodeText(status);
      LOG_ERROR("Received on_read_transmit_power_level_complete with error code %s", error_code.c_str());
      return;
    }
    OnReadTransmitPowerLevelComplete(complete_view.GetTransmitPowerLevel());
  }

  void on_read_link_supervision_timeout_complete(CommandCompleteView view) {
    auto complete_view = ReadLinkSupervisionTimeoutCompleteView::Create(view);
    if (!complete_view.IsValid()) {
      LOG_ERROR("Received on_read_link_supervision_timeout_complete with invalid packet");
      return;
    } else if (complete_view.GetStatus() != ErrorCode::SUCCESS) {
      auto status = complete_view.GetStatus();
      std::string error_code = ErrorCodeText(status);
      LOG_ERROR("Received on_read_link_supervision_timeout_complete with error code %s", error_code.c_str());
      return;
    }
    OnReadLinkSupervisionTimeoutComplete(complete_view.GetLinkSupervisionTimeout());
  }

  void on_read_failed_contact_counter_complete(CommandCompleteView view) {
    auto complete_view = ReadFailedContactCounterCompleteView::Create(view);
    if (!complete_view.IsValid()) {
      LOG_ERROR("Received on_read_failed_contact_counter_complete with invalid packet");
      return;
    } else if (complete_view.GetStatus() != ErrorCode::SUCCESS) {
      auto status = complete_view.GetStatus();
      std::string error_code = ErrorCodeText(status);
      LOG_ERROR("Received on_read_failed_contact_counter_complete with error code %s", error_code.c_str());
      return;
    }
    OnReadFailedContactCounterComplete(complete_view.GetFailedContactCounter());
  }

  void on_read_link_quality_complete(CommandCompleteView view) {
    auto complete_view = ReadLinkQualityCompleteView::Create(view);
    if (!complete_view.IsValid()) {
      LOG_ERROR("Received on_read_link_quality_complete with invalid packet");
      return;
    } else if (complete_view.GetStatus() != ErrorCode::SUCCESS) {
      auto status = complete_view.GetStatus();
      std::string error_code = ErrorCodeText(status);
      LOG_ERROR("Received on_read_link_quality_complete with error code %s", error_code.c_str());
      return;
    }
    OnReadLinkQualityComplete(complete_view.GetLinkQuality());
  }

  void on_read_afh_channel_map_complete(CommandCompleteView view) {
    auto complete_view = ReadAfhChannelMapCompleteView::Create(view);
    if (!complete_view.IsValid()) {
      LOG_ERROR("Received on_read_afh_channel_map_complete with invalid packet");
      return;
    } else if (complete_view.GetStatus() != ErrorCode::SUCCESS) {
      auto status = complete_view.GetStatus();
      std::string error_code = ErrorCodeText(status);
      LOG_ERROR("Received on_read_afh_channel_map_complete with error code %s", error_code.c_str());
      return;
    }
    OnReadAfhChannelMapComplete(complete_view.GetAfhMode(), complete_view.GetAfhChannelMap());
  }

  void on_read_rssi_complete(CommandCompleteView view) {
    auto complete_view = ReadRssiCompleteView::Create(view);
    if (!complete_view.IsValid()) {
      LOG_ERROR("Received on_read_rssi_complete with invalid packet");
      return;
    } else if (complete_view.GetStatus() != ErrorCode::SUCCESS) {
      auto status = complete_view.GetStatus();
      std::string error_code = ErrorCodeText(status);
      LOG_ERROR("Received on_read_rssi_complete with error code %s", error_code.c_str());
      return;
    }
    OnReadRssiComplete(complete_view.GetRssi());
  }

  void on_read_remote_version_information_status(CommandStatusView view) {
    ASSERT_LOG(view.IsValid(), "Bad status packet!");
  }

  void on_read_remote_supported_features_status(CommandStatusView view) {
    ASSERT_LOG(view.IsValid(), "Bad status packet!");
  }

  void on_read_remote_extended_features_status(CommandStatusView view) {
    ASSERT_LOG(view.IsValid(), "Bad status packet!");
  }

  void on_read_clock_complete(CommandCompleteView view) {
    auto complete_view = ReadClockCompleteView::Create(view);
    if (!complete_view.IsValid()) {
      LOG_ERROR("Received on_read_clock_complete with invalid packet");
      return;
    } else if (complete_view.GetStatus() != ErrorCode::SUCCESS) {
      auto status = complete_view.GetStatus();
      std::string error_code = ErrorCodeText(status);
      LOG_ERROR("Received on_read_clock_complete with error code %s", error_code.c_str());
      return;
    }
    uint32_t clock = complete_view.GetClock();
    uint16_t accuracy = complete_view.GetAccuracy();
    OnReadClockComplete(clock, accuracy);
  }

  AclConnectionInterface* acl_connection_interface_;
  os::Handler* client_handler_ = nullptr;
  ConnectionManagementCallbacks* client_callbacks_ = nullptr;
  std::list<common::OnceClosure> queued_callbacks_;
  Address address_;
  uint16_t connection_handle_;
};

struct ClassicAclConnection::impl {
  impl(
      AclConnectionInterface* acl_connection_interface,
      std::shared_ptr<Queue> queue,
      const Address& address,
      uint16_t connection_handle)
      : tracker(acl_connection_interface, address, connection_handle), queue_(std::move(queue)) {}
  ConnectionManagementCallbacks* GetEventCallbacks(std::function<void(uint16_t)> invalidate_callbacks) {
    ASSERT_LOG(!invalidate_callbacks_, "Already returned event callbacks for this connection");
    invalidate_callbacks_ = std::move(invalidate_callbacks);
    return &tracker;
  }
  void PutEventCallbacks() {
    if (invalidate_callbacks_) invalidate_callbacks_(tracker.connection_handle_);
    invalidate_callbacks_ = {};
  }

  AclConnectionTracker tracker;
  std::shared_ptr<Queue> queue_;
  std::function<void(uint16_t)> invalidate_callbacks_;
};

ClassicAclConnection::ClassicAclConnection()
    : AclConnection(), acl_connection_interface_(nullptr), address_(Address::kEmpty) {}

ClassicAclConnection::ClassicAclConnection(std::shared_ptr<Queue> queue,
                                           AclConnectionInterface* acl_connection_interface, uint16_t handle,
                                           Address address)
    : AclConnection(queue->GetUpEnd(), handle), acl_connection_interface_(acl_connection_interface), address_(address) {
  pimpl_ = new ClassicAclConnection::impl(acl_connection_interface, std::move(queue), address, handle);
}

ClassicAclConnection::~ClassicAclConnection() {
  if (pimpl_) pimpl_->PutEventCallbacks();
  delete pimpl_;
}

ConnectionManagementCallbacks* ClassicAclConnection::GetEventCallbacks(
    std::function<void(uint16_t)> invalidate_callbacks) {
  return pimpl_->GetEventCallbacks(std::move(invalidate_callbacks));
}

void ClassicAclConnection::RegisterCallbacks(ConnectionManagementCallbacks* callbacks, os::Handler* handler) {
  return pimpl_->tracker.RegisterCallbacks(callbacks, handler);
}

bool ClassicAclConnection::Disconnect(DisconnectReason reason) {
  acl_connection_interface_->EnqueueCommand(
      DisconnectBuilder::Create(handle_, reason),
      pimpl_->tracker.client_handler_->BindOnce(check_status<DisconnectStatusView>));
  return true;
}

bool ClassicAclConnection::ChangeConnectionPacketType(uint16_t packet_type) {
  acl_connection_interface_->EnqueueCommand(
      ChangeConnectionPacketTypeBuilder::Create(handle_, packet_type),
      pimpl_->tracker.client_handler_->BindOnce(
          check_status<ChangeConnectionPacketTypeStatusView>));
  return true;
}

bool ClassicAclConnection::AuthenticationRequested() {
  acl_connection_interface_->EnqueueCommand(
      AuthenticationRequestedBuilder::Create(handle_),
      pimpl_->tracker.client_handler_->BindOnce(check_status<AuthenticationRequestedStatusView>));
  return true;
}

bool ClassicAclConnection::SetConnectionEncryption(Enable enable) {
  acl_connection_interface_->EnqueueCommand(
      SetConnectionEncryptionBuilder::Create(handle_, enable),
      pimpl_->tracker.client_handler_->BindOnce(check_status<SetConnectionEncryptionStatusView>));
  return true;
}

bool ClassicAclConnection::ChangeConnectionLinkKey() {
  acl_connection_interface_->EnqueueCommand(
      ChangeConnectionLinkKeyBuilder::Create(handle_),
      pimpl_->tracker.client_handler_->BindOnce(check_status<ChangeConnectionLinkKeyStatusView>));
  return true;
}

bool ClassicAclConnection::ReadClockOffset() {
  acl_connection_interface_->EnqueueCommand(
      ReadClockOffsetBuilder::Create(handle_),
      pimpl_->tracker.client_handler_->BindOnce(check_status<ReadClockOffsetStatusView>));
  return true;
}

bool ClassicAclConnection::HoldMode(uint16_t max_interval, uint16_t min_interval) {
  acl_connection_interface_->EnqueueCommand(
      HoldModeBuilder::Create(handle_, max_interval, min_interval),
      pimpl_->tracker.client_handler_->BindOnce(check_status<HoldModeStatusView>));
  return true;
}

bool ClassicAclConnection::SniffMode(uint16_t max_interval, uint16_t min_interval, uint16_t attempt, uint16_t timeout) {
  acl_connection_interface_->EnqueueCommand(
      SniffModeBuilder::Create(handle_, max_interval, min_interval, attempt, timeout),
      pimpl_->tracker.client_handler_->BindOnce(check_status<SniffModeStatusView>));
  return true;
}

bool ClassicAclConnection::ExitSniffMode() {
  acl_connection_interface_->EnqueueCommand(
      ExitSniffModeBuilder::Create(handle_),
      pimpl_->tracker.client_handler_->BindOnce(check_status<ExitSniffModeStatusView>));
  return true;
}

bool ClassicAclConnection::QosSetup(ServiceType service_type, uint32_t token_rate, uint32_t peak_bandwidth,
                                    uint32_t latency, uint32_t delay_variation) {
  acl_connection_interface_->EnqueueCommand(
      QosSetupBuilder::Create(
          handle_, service_type, token_rate, peak_bandwidth, latency, delay_variation),
      pimpl_->tracker.client_handler_->BindOnce(check_status<QosSetupStatusView>));
  return true;
}

bool ClassicAclConnection::RoleDiscovery() {
  acl_connection_interface_->EnqueueCommand(
      RoleDiscoveryBuilder::Create(handle_),
      pimpl_->tracker.client_handler_->BindOnceOn(&pimpl_->tracker, &AclConnectionTracker::on_role_discovery_complete));
  return true;
}

bool ClassicAclConnection::ReadLinkPolicySettings() {
  acl_connection_interface_->EnqueueCommand(
      ReadLinkPolicySettingsBuilder::Create(handle_),
      pimpl_->tracker.client_handler_->BindOnceOn(&pimpl_->tracker,
                                                  &AclConnectionTracker::on_read_link_policy_settings_complete));
  return true;
}

bool ClassicAclConnection::WriteLinkPolicySettings(uint16_t link_policy_settings) {
  acl_connection_interface_->EnqueueCommand(
      WriteLinkPolicySettingsBuilder::Create(handle_, link_policy_settings),
      pimpl_->tracker.client_handler_->BindOnce(
          check_complete<WriteLinkPolicySettingsCompleteView>));
  return true;
}

bool ClassicAclConnection::FlowSpecification(FlowDirection flow_direction, ServiceType service_type,
                                             uint32_t token_rate, uint32_t token_bucket_size, uint32_t peak_bandwidth,
                                             uint32_t access_latency) {
  acl_connection_interface_->EnqueueCommand(
      FlowSpecificationBuilder::Create(
          handle_,
          flow_direction,
          service_type,
          token_rate,
          token_bucket_size,
          peak_bandwidth,
          access_latency),
      pimpl_->tracker.client_handler_->BindOnce(check_status<FlowSpecificationStatusView>));
  return true;
}

bool ClassicAclConnection::SniffSubrating(uint16_t maximum_latency, uint16_t minimum_remote_timeout,
                                          uint16_t minimum_local_timeout) {
  acl_connection_interface_->EnqueueCommand(
      SniffSubratingBuilder::Create(
          handle_, maximum_latency, minimum_remote_timeout, minimum_local_timeout),
      pimpl_->tracker.client_handler_->BindOnce(check_complete<SniffSubratingCompleteView>));
  return true;
}

bool ClassicAclConnection::Flush() {
  acl_connection_interface_->EnqueueCommand(
      FlushBuilder::Create(handle_),
      pimpl_->tracker.client_handler_->BindOnce(check_complete<FlushCompleteView>));
  return true;
}

bool ClassicAclConnection::ReadAutomaticFlushTimeout() {
  acl_connection_interface_->EnqueueCommand(
      ReadAutomaticFlushTimeoutBuilder::Create(handle_),
      pimpl_->tracker.client_handler_->BindOnceOn(&pimpl_->tracker,
                                                  &AclConnectionTracker::on_read_automatic_flush_timeout_complete));
  return true;
}

bool ClassicAclConnection::WriteAutomaticFlushTimeout(uint16_t flush_timeout) {
  acl_connection_interface_->EnqueueCommand(
      WriteAutomaticFlushTimeoutBuilder::Create(handle_, flush_timeout),
      pimpl_->tracker.client_handler_->BindOnce(
          check_complete<WriteAutomaticFlushTimeoutCompleteView>));
  return true;
}

bool ClassicAclConnection::ReadTransmitPowerLevel(TransmitPowerLevelType type) {
  acl_connection_interface_->EnqueueCommand(
      ReadTransmitPowerLevelBuilder::Create(handle_, type),
      pimpl_->tracker.client_handler_->BindOnceOn(&pimpl_->tracker,
                                                  &AclConnectionTracker::on_read_transmit_power_level_complete));
  return true;
}

bool ClassicAclConnection::ReadLinkSupervisionTimeout() {
  acl_connection_interface_->EnqueueCommand(
      ReadLinkSupervisionTimeoutBuilder::Create(handle_),
      pimpl_->tracker.client_handler_->BindOnceOn(&pimpl_->tracker,
                                                  &AclConnectionTracker::on_read_link_supervision_timeout_complete));
  return true;
}

bool ClassicAclConnection::WriteLinkSupervisionTimeout(uint16_t link_supervision_timeout) {
  acl_connection_interface_->EnqueueCommand(
      WriteLinkSupervisionTimeoutBuilder::Create(handle_, link_supervision_timeout),
      pimpl_->tracker.client_handler_->BindOnce(
          check_complete<WriteLinkSupervisionTimeoutCompleteView>));
  return true;
}

bool ClassicAclConnection::ReadFailedContactCounter() {
  acl_connection_interface_->EnqueueCommand(
      ReadFailedContactCounterBuilder::Create(handle_),
      pimpl_->tracker.client_handler_->BindOnceOn(&pimpl_->tracker,
                                                  &AclConnectionTracker::on_read_failed_contact_counter_complete));
  return true;
}

bool ClassicAclConnection::ResetFailedContactCounter() {
  acl_connection_interface_->EnqueueCommand(
      ResetFailedContactCounterBuilder::Create(handle_),
      pimpl_->tracker.client_handler_->BindOnce(
          check_complete<ResetFailedContactCounterCompleteView>));
  return true;
}

bool ClassicAclConnection::ReadLinkQuality() {
  acl_connection_interface_->EnqueueCommand(
      ReadLinkQualityBuilder::Create(handle_),
      pimpl_->tracker.client_handler_->BindOnceOn(&pimpl_->tracker,
                                                  &AclConnectionTracker::on_read_link_quality_complete));
  return true;
}

bool ClassicAclConnection::ReadAfhChannelMap() {
  acl_connection_interface_->EnqueueCommand(
      ReadAfhChannelMapBuilder::Create(handle_),
      pimpl_->tracker.client_handler_->BindOnceOn(&pimpl_->tracker,
                                                  &AclConnectionTracker::on_read_afh_channel_map_complete));
  return true;
}

bool ClassicAclConnection::ReadRssi() {
  acl_connection_interface_->EnqueueCommand(
      ReadRssiBuilder::Create(handle_),
      pimpl_->tracker.client_handler_->BindOnceOn(&pimpl_->tracker, &AclConnectionTracker::on_read_rssi_complete));
  return true;
}

bool ClassicAclConnection::ReadRemoteVersionInformation() {
  acl_connection_interface_->EnqueueCommand(
      ReadRemoteVersionInformationBuilder::Create(handle_),
      pimpl_->tracker.client_handler_->BindOnceOn(&pimpl_->tracker,
                                                  &AclConnectionTracker::on_read_remote_version_information_status));
  return true;
}

bool ClassicAclConnection::ReadRemoteSupportedFeatures() {
  acl_connection_interface_->EnqueueCommand(
      ReadRemoteSupportedFeaturesBuilder::Create(handle_),
      pimpl_->tracker.client_handler_->BindOnceOn(&pimpl_->tracker,
                                                  &AclConnectionTracker::on_read_remote_supported_features_status));
  return true;
}

bool ClassicAclConnection::ReadRemoteExtendedFeatures(uint8_t page_number) {
  acl_connection_interface_->EnqueueCommand(
      ReadRemoteExtendedFeaturesBuilder::Create(handle_, page_number),
      pimpl_->tracker.client_handler_->BindOnceOn(
          &pimpl_->tracker, &AclConnectionTracker::on_read_remote_extended_features_status));
  return true;
}

bool ClassicAclConnection::ReadClock(WhichClock which_clock) {
  pimpl_->tracker.acl_connection_interface_->EnqueueCommand(
      ReadClockBuilder::Create(handle_, which_clock),
      pimpl_->tracker.client_handler_->BindOnceOn(&pimpl_->tracker, &AclConnectionTracker::on_read_clock_complete));
  return true;
}

}  // namespace acl_manager
}  // namespace hci
}  // namespace bluetooth
