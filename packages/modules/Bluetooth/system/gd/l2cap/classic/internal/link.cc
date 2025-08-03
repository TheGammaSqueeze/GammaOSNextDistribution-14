/*
 * Copyright 2019 The Android Open Source Project
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

#include "l2cap/classic/internal/link.h"

#include <chrono>
#include <memory>

#include "common/bind.h"
#include "hci/acl_manager/classic_acl_connection.h"
#include "l2cap/classic/dynamic_channel_manager.h"
#include "l2cap/classic/internal/fixed_channel_impl.h"
#include "l2cap/classic/internal/link_manager.h"
#include "l2cap/internal/parameter_provider.h"
#include "os/alarm.h"

namespace bluetooth {
namespace l2cap {
namespace classic {
namespace internal {

using RetransmissionAndFlowControlMode = DynamicChannelConfigurationOption::RetransmissionAndFlowControlMode;
using ConnectionResult = DynamicChannelManager::ConnectionResult;
using ConnectionResultCode = DynamicChannelManager::ConnectionResultCode;

Link::Link(
    os::Handler* l2cap_handler,
    std::unique_ptr<hci::acl_manager::ClassicAclConnection> acl_connection,
    l2cap::internal::ParameterProvider* parameter_provider,
    DynamicChannelServiceManagerImpl* dynamic_service_manager,
    FixedChannelServiceManagerImpl* fixed_service_manager,
    LinkManager* link_manager)
    : l2cap_handler_(l2cap_handler),
      acl_connection_(std::move(acl_connection)),
      data_pipeline_manager_(l2cap_handler, this, acl_connection_->GetAclQueueEnd()),
      parameter_provider_(parameter_provider),
      dynamic_service_manager_(dynamic_service_manager),
      fixed_service_manager_(fixed_service_manager),
      link_manager_(link_manager),
      signalling_manager_(
          l2cap_handler_,
          this,
          &data_pipeline_manager_,
          dynamic_service_manager_,
          &dynamic_channel_allocator_,
          fixed_service_manager_),
      acl_handle_(acl_connection_->GetHandle()) {
  ASSERT(l2cap_handler_ != nullptr);
  ASSERT(acl_connection_ != nullptr);
  ASSERT(parameter_provider_ != nullptr);
  link_idle_disconnect_alarm_.Schedule(common::BindOnce(&Link::Disconnect, common::Unretained(this)),
                                       parameter_provider_->GetClassicLinkIdleDisconnectTimeout());
  acl_connection_->RegisterCallbacks(this, l2cap_handler_);
}

void Link::OnAclDisconnected(hci::ErrorCode status) {
  signalling_manager_.CancelAlarm();
  fixed_channel_allocator_.OnAclDisconnected(status);
  dynamic_channel_allocator_.OnAclDisconnected(status);
  ConnectionResult result{
      .connection_result_code = ConnectionResultCode::FAIL_HCI_ERROR,
      .hci_error = status,
      .l2cap_connection_response_result = ConnectionResponseResult::SUCCESS,
  };
  while (!local_cid_to_pending_dynamic_channel_connection_map_.empty()) {
    auto entry = local_cid_to_pending_dynamic_channel_connection_map_.begin();
    NotifyChannelFail(entry->first, result);
  }
}

void Link::Disconnect() {
  acl_connection_->Disconnect(hci::DisconnectReason::REMOTE_USER_TERMINATED_CONNECTION);
}

void Link::Encrypt() {
  if (encryption_enabled_ == hci::EncryptionEnabled::OFF) {
    acl_connection_->SetConnectionEncryption(hci::Enable::ENABLED);
  }
}

void Link::Authenticate() {
  if (!IsAuthenticated() && !has_requested_authentication_) {
    has_requested_authentication_ = true;
    acl_connection_->AuthenticationRequested();
  }
}

bool Link::IsAuthenticated() const {
  return encryption_enabled_ != hci::EncryptionEnabled::OFF;
}

void Link::ReadRemoteVersionInformation() {
  acl_connection_->ReadRemoteVersionInformation();
}

void Link::ReadRemoteSupportedFeatures() {
  acl_connection_->ReadRemoteSupportedFeatures();
}

void Link::ReadRemoteExtendedFeatures(uint8_t page_number) {
  acl_connection_->ReadRemoteExtendedFeatures(page_number);
}

void Link::ReadClockOffset() {
  acl_connection_->ReadClockOffset();
}

void Link::AcquireSecurityHold() {
  used_by_security_module_ = true;
  RefreshRefCount();
}
void Link::ReleaseSecurityHold() {
  used_by_security_module_ = false;
  RefreshRefCount();
}

std::shared_ptr<FixedChannelImpl> Link::AllocateFixedChannel(Cid cid) {
  auto channel = fixed_channel_allocator_.AllocateChannel(cid);
  data_pipeline_manager_.AttachChannel(cid, channel, l2cap::internal::DataPipelineManager::ChannelMode::BASIC);
  return channel;
}

bool Link::IsFixedChannelAllocated(Cid cid) {
  return fixed_channel_allocator_.IsChannelAllocated(cid);
}

Cid Link::ReserveDynamicChannel() {
  return dynamic_channel_allocator_.ReserveChannel();
}

void Link::SendConnectionRequest(Psm psm, Cid local_cid) {
  signalling_manager_.SendConnectionRequest(psm, local_cid);
}

void Link::SendConnectionRequest(Psm psm, Cid local_cid,
                                 PendingDynamicChannelConnection pending_dynamic_channel_connection) {
  if (pending_dynamic_channel_connection.configuration_.channel_mode ==
          RetransmissionAndFlowControlMode::ENHANCED_RETRANSMISSION &&
      !remote_extended_feature_received_) {
    pending_dynamic_psm_list_.push_back(psm);
    pending_dynamic_channel_callback_list_.push_back(std::move(pending_dynamic_channel_connection));
    LOG_INFO("Will connect after information response ERTM feature support is received");
    dynamic_channel_allocator_.FreeChannel(local_cid);
    return;
  } else if (pending_dynamic_channel_connection.configuration_.channel_mode ==
                 RetransmissionAndFlowControlMode::ENHANCED_RETRANSMISSION &&
             !GetRemoteSupportsErtm()) {
    LOG_WARN("Remote doesn't support ERTM. Dropping connection request");
    ConnectionResult result{
        .connection_result_code = ConnectionResultCode::FAIL_REMOTE_NOT_SUPPORT,
    };
    pending_dynamic_channel_connection.on_fail_callback_.Invoke(result);
    dynamic_channel_allocator_.FreeChannel(local_cid);
    return;
  } else {
    local_cid_to_pending_dynamic_channel_connection_map_[local_cid] = std::move(pending_dynamic_channel_connection);
    signalling_manager_.SendConnectionRequest(psm, local_cid);
  }
}

void Link::SetChannelTxPriority(Cid local_cid, bool high_priority) {
  data_pipeline_manager_.SetChannelTxPriority(local_cid, high_priority);
}

void Link::SetPendingDynamicChannels(std::list<Psm> psm_list,
                                     std::list<Link::PendingDynamicChannelConnection> callback_list) {
  ASSERT(psm_list.size() == callback_list.size());
  pending_dynamic_psm_list_ = std::move(psm_list);
  pending_dynamic_channel_callback_list_ = std::move(callback_list);
}

void Link::connect_to_pending_dynamic_channels() {
  auto psm = pending_dynamic_psm_list_.begin();
  auto callback = pending_dynamic_channel_callback_list_.begin();
  while (psm != pending_dynamic_psm_list_.end()) {
    SendConnectionRequest(*psm, ReserveDynamicChannel(), std::move(*callback));
    psm++;
    callback++;
  }
}

void Link::send_pending_configuration_requests() {
  for (auto local_cid : pending_outgoing_configuration_request_list_) {
    signalling_manager_.SendInitialConfigRequest(local_cid);
  }
  pending_outgoing_configuration_request_list_.clear();
}

void Link::OnOutgoingConnectionRequestFail(Cid local_cid, ConnectionResult result) {
  if (local_cid_to_pending_dynamic_channel_connection_map_.find(local_cid) !=
      local_cid_to_pending_dynamic_channel_connection_map_.end()) {
    NotifyChannelFail(local_cid, result);
  }
  dynamic_channel_allocator_.FreeChannel(local_cid);
}

void Link::SendInitialConfigRequestOrQueue(Cid local_cid) {
  if (remote_extended_feature_received_) {
    signalling_manager_.SendInitialConfigRequest(local_cid);
  } else {
    pending_outgoing_configuration_request_list_.push_back(local_cid);
  }
}

void Link::SendDisconnectionRequest(Cid local_cid, Cid remote_cid) {
  signalling_manager_.SendDisconnectionRequest(local_cid, remote_cid);
}

void Link::SendInformationRequest(InformationRequestInfoType type) {
  signalling_manager_.SendInformationRequest(type);
}

std::shared_ptr<l2cap::internal::DynamicChannelImpl> Link::AllocateDynamicChannel(Psm psm, Cid remote_cid) {
  auto channel = dynamic_channel_allocator_.AllocateChannel(psm, remote_cid);
  if (channel != nullptr) {
    RefreshRefCount();
    channel->local_initiated_ = false;
  }
  return channel;
}

std::shared_ptr<l2cap::internal::DynamicChannelImpl> Link::AllocateReservedDynamicChannel(Cid reserved_cid, Psm psm,
                                                                                          Cid remote_cid) {
  auto channel = dynamic_channel_allocator_.AllocateReservedChannel(reserved_cid, psm, remote_cid);
  if (channel != nullptr) {
    RefreshRefCount();
  }
  channel->local_initiated_ = true;
  return channel;
}

classic::DynamicChannelConfigurationOption Link::GetConfigurationForInitialConfiguration(Cid cid) {
  ASSERT(local_cid_to_pending_dynamic_channel_connection_map_.find(cid) !=
         local_cid_to_pending_dynamic_channel_connection_map_.end());
  return local_cid_to_pending_dynamic_channel_connection_map_[cid].configuration_;
}

void Link::FreeDynamicChannel(Cid cid) {
  if (dynamic_channel_allocator_.FindChannelByCid(cid) == nullptr) {
    return;
  }
  dynamic_channel_allocator_.FreeChannel(cid);
  RefreshRefCount();
}

void Link::RefreshRefCount() {
  int ref_count = 0;
  ref_count += fixed_channel_allocator_.GetRefCount();
  ref_count += dynamic_channel_allocator_.NumberOfChannels();
  if (used_by_security_module_) {
    ref_count += 1;
  }
  ASSERT_LOG(ref_count >= 0, "ref_count %d is less than 0", ref_count);
  if (ref_count > 0) {
    link_idle_disconnect_alarm_.Cancel();
  } else {
    link_idle_disconnect_alarm_.Schedule(common::BindOnce(&Link::Disconnect, common::Unretained(this)),
                                         parameter_provider_->GetClassicLinkIdleDisconnectTimeout());
  }
}

void Link::NotifyChannelCreation(Cid cid, std::unique_ptr<DynamicChannel> user_channel) {
  ASSERT(local_cid_to_pending_dynamic_channel_connection_map_.find(cid) !=
         local_cid_to_pending_dynamic_channel_connection_map_.end());
  auto& pending_dynamic_channel_connection = local_cid_to_pending_dynamic_channel_connection_map_[cid];
  pending_dynamic_channel_connection.on_open_callback_.Invoke(std::move(user_channel));
  local_cid_to_pending_dynamic_channel_connection_map_.erase(cid);
}

void Link::NotifyChannelFail(Cid cid, ConnectionResult result) {
  ASSERT(local_cid_to_pending_dynamic_channel_connection_map_.find(cid) !=
         local_cid_to_pending_dynamic_channel_connection_map_.end());
  auto& pending_dynamic_channel_connection = local_cid_to_pending_dynamic_channel_connection_map_[cid];
  pending_dynamic_channel_connection.on_fail_callback_.Invoke(result);
  local_cid_to_pending_dynamic_channel_connection_map_.erase(cid);
}

void Link::SetRemoteConnectionlessMtu(Mtu mtu) {
  remote_connectionless_mtu_ = mtu;
}

Mtu Link::GetRemoteConnectionlessMtu() const {
  return remote_connectionless_mtu_;
}

bool Link::GetRemoteSupportsErtm() const {
  return remote_supports_ertm_;
}

bool Link::GetRemoteSupportsFcs() const {
  return remote_supports_fcs_;
}

void Link::OnRemoteExtendedFeatureReceived(bool ertm_supported, bool fcs_supported) {
  remote_supports_ertm_ = ertm_supported;
  remote_supports_fcs_ = fcs_supported;
  remote_extended_feature_received_ = true;
  connect_to_pending_dynamic_channels();
  send_pending_configuration_requests();
}

void Link::OnConnectionPacketTypeChanged(uint16_t packet_type) {
  LOG_INFO("UNIMPLEMENTED %s packet_type:%x", __func__, packet_type);
}

void Link::OnAuthenticationComplete(hci::ErrorCode hci_status) {
  link_manager_->OnAuthenticationComplete(hci_status, GetDevice().GetAddress());
}

void Link::OnEncryptionChange(hci::EncryptionEnabled enabled) {
  encryption_enabled_ = enabled;
  link_manager_->OnEncryptionChange(GetDevice().GetAddress(), enabled);
  for (auto& listener : encryption_change_listener_) {
    signalling_manager_.on_security_result_for_outgoing(
        ClassicSignallingManager::SecurityEnforcementType::ENCRYPTION,
        listener.psm,
        listener.cid,
        enabled != hci::EncryptionEnabled::OFF);
  }
}

void Link::OnChangeConnectionLinkKeyComplete() {
  LOG_INFO("UNIMPLEMENTED %s", __func__);
}

void Link::OnReadClockOffsetComplete(uint16_t clock_offset) {
  link_manager_->OnReadClockOffset(GetDevice().GetAddress(), clock_offset);
}

void Link::OnModeChange(hci::ErrorCode status, hci::Mode current_mode, uint16_t interval) {
  link_manager_->OnModeChange(status, GetDevice().GetAddress(), current_mode, interval);
}

void Link::OnSniffSubrating(
    hci::ErrorCode hci_status,
    uint16_t maximum_transmit_latency,
    uint16_t maximum_receive_latency,
    uint16_t minimum_remote_timeout,
    uint16_t minimum_local_timeout) {
  link_manager_->OnSniffSubrating(
      hci_status,
      GetDevice().GetAddress(),
      maximum_transmit_latency,
      maximum_receive_latency,
      minimum_remote_timeout,
      minimum_local_timeout);
}

void Link::OnQosSetupComplete(hci::ServiceType service_type, uint32_t token_rate, uint32_t peak_bandwidth,
                              uint32_t latency, uint32_t delay_variation) {
  LOG_INFO(
      "UNIMPLEMENTED %s service_type:%s token_rate:%d peak_bandwidth:%d latency:%d delay_varitation:%d",
      __func__,
      hci::ServiceTypeText(service_type).c_str(),
      token_rate,
      peak_bandwidth,
      latency,
      delay_variation);
}
void Link::OnFlowSpecificationComplete(hci::FlowDirection flow_direction, hci::ServiceType service_type,
                                       uint32_t token_rate, uint32_t token_bucket_size, uint32_t peak_bandwidth,
                                       uint32_t access_latency) {
  LOG_INFO(
      "UNIMPLEMENTED %s flow_direction:%s service_type:%s token_rate:%d token_bucket_size:%d peak_bandwidth:%d "
      "access_latency:%d",
      __func__,
      hci::FlowDirectionText(flow_direction).c_str(),
      hci::ServiceTypeText(service_type).c_str(),
      token_rate,
      token_bucket_size,
      peak_bandwidth,
      access_latency);
}
void Link::OnFlushOccurred() {
  LOG_INFO("UNIMPLEMENTED %s", __func__);
}
void Link::OnRoleDiscoveryComplete(hci::Role current_role) {
  role_ = current_role;
}
void Link::OnReadLinkPolicySettingsComplete(uint16_t link_policy_settings) {
  LOG_INFO("UNIMPLEMENTED %s link_policy_settings:0x%x", __func__, link_policy_settings);
}
void Link::OnReadAutomaticFlushTimeoutComplete(uint16_t flush_timeout) {
  LOG_INFO("UNIMPLEMENTED %s flush_timeout:%d", __func__, flush_timeout);
}
void Link::OnReadTransmitPowerLevelComplete(uint8_t transmit_power_level) {
  LOG_INFO("UNIMPLEMENTED %s transmit_power_level:%d", __func__, transmit_power_level);
}
void Link::OnReadLinkSupervisionTimeoutComplete(uint16_t link_supervision_timeout) {
  LOG_INFO("UNIMPLEMENTED %s link_supervision_timeout:%d", __func__, link_supervision_timeout);
}
void Link::OnReadFailedContactCounterComplete(uint16_t failed_contact_counter) {
  LOG_INFO("UNIMPLEMENTED %sfailed_contact_counter:%hu", __func__, failed_contact_counter);
}
void Link::OnReadLinkQualityComplete(uint8_t link_quality) {
  LOG_INFO("UNIMPLEMENTED %s link_quality:%hhu", __func__, link_quality);
}
void Link::OnReadAfhChannelMapComplete(
    hci::AfhMode afh_mode, std::array<uint8_t, 10> /* afh_channel_map */) {
  LOG_INFO("UNIMPLEMENTED %s afh_mode:%s", __func__, hci::AfhModeText(afh_mode).c_str());
}
void Link::OnReadRssiComplete(uint8_t rssi) {
  LOG_INFO("UNIMPLEMENTED %s rssi:%hhd", __func__, rssi);
}
void Link::OnReadClockComplete(uint32_t clock, uint16_t accuracy) {
  LOG_INFO("UNIMPLEMENTED %s clock:%u accuracy:%hu", __func__, clock, accuracy);
}
void Link::OnCentralLinkKeyComplete(hci::KeyFlag key_flag) {
  LOG_INFO("UNIMPLEMENTED key_flag:%s", hci::KeyFlagText(key_flag).c_str());
}
void Link::OnRoleChange(hci::ErrorCode hci_status, hci::Role new_role) {
  role_ = new_role;
  link_manager_->OnRoleChange(hci_status, GetDevice().GetAddress(), new_role);
}
void Link::OnDisconnection(hci::ErrorCode reason) {
  OnAclDisconnected(reason);
  link_manager_->OnDisconnect(GetDevice().GetAddress(), reason);
}
void Link::OnReadRemoteVersionInformationComplete(
    hci::ErrorCode hci_status, uint8_t lmp_version, uint16_t manufacturer_name, uint16_t sub_version) {
  LOG_INFO(
      "UNIMPLEMENTED hci_status:%s lmp_version:%hhu manufacturer_name:%hu sub_version:%hu",
      ErrorCodeText(hci_status).c_str(),
      lmp_version,
      manufacturer_name,
      sub_version);
  link_manager_->OnReadRemoteVersionInformation(
      hci_status, GetDevice().GetAddress(), lmp_version, manufacturer_name, sub_version);
}
void Link::OnReadRemoteSupportedFeaturesComplete(uint64_t features) {
  LOG_INFO("page_number:%hhu features:0x%lx", static_cast<uint8_t>(0), static_cast<unsigned long>(features));
  link_manager_->OnReadRemoteSupportedFeatures(GetDevice().GetAddress(), features);
}

void Link::OnReadRemoteExtendedFeaturesComplete(uint8_t page_number, uint8_t max_page_number, uint64_t features) {
  LOG_INFO(
      "page_number:%hhu max_page_number:%hhu features:0x%lx",
      page_number,
      max_page_number,
      static_cast<unsigned long>(features));
  link_manager_->OnReadRemoteExtendedFeatures(GetDevice().GetAddress(), page_number, max_page_number, features);
}

void Link::AddEncryptionChangeListener(EncryptionChangeListener listener) {
  encryption_change_listener_.push_back(listener);
}

void Link::OnPendingPacketChange(Cid /* local_cid */, bool has_packet) {
  if (has_packet) {
    remaining_packets_to_be_sent_++;
  } else {
    remaining_packets_to_be_sent_--;
  }
  if (link_manager_ != nullptr) {
    link_manager_->OnPendingPacketChange(GetDevice().GetAddress(), remaining_packets_to_be_sent_);
  }
}

}  // namespace internal
}  // namespace classic
}  // namespace l2cap
}  // namespace bluetooth
