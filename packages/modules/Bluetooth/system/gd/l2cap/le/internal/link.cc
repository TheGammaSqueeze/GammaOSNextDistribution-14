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

#include "l2cap/le/internal/link.h"

#include <chrono>
#include <memory>

#include "hci/acl_manager/le_acl_connection.h"
#include "l2cap/internal/dynamic_channel_impl.h"
#include "l2cap/internal/parameter_provider.h"
#include "l2cap/le/dynamic_channel_manager.h"
#include "l2cap/le/internal/fixed_channel_impl.h"
#include "l2cap/le/internal/link_manager.h"
#include "os/alarm.h"

namespace bluetooth {
namespace l2cap {
namespace le {
namespace internal {

static constexpr uint16_t kDefaultMinimumCeLength = 0x0002;
static constexpr uint16_t kDefaultMaximumCeLength = 0x0C00;

Link::Link(
    os::Handler* l2cap_handler,
    std::unique_ptr<hci::acl_manager::LeAclConnection> acl_connection,
    l2cap::internal::ParameterProvider* parameter_provider,
    DynamicChannelServiceManagerImpl* dynamic_service_manager,
    FixedChannelServiceManagerImpl* /* fixed_service_manager */,
    LinkManager* link_manager)
    : l2cap_handler_(l2cap_handler),
      acl_connection_(std::move(acl_connection)),
      data_pipeline_manager_(l2cap_handler, this, acl_connection_->GetAclQueueEnd()),
      parameter_provider_(parameter_provider),
      dynamic_service_manager_(dynamic_service_manager),
      signalling_manager_(
          l2cap_handler_,
          this,
          &data_pipeline_manager_,
          dynamic_service_manager_,
          &dynamic_channel_allocator_),
      link_manager_(link_manager) {
  ASSERT(l2cap_handler_ != nullptr);
  ASSERT(acl_connection_ != nullptr);
  ASSERT(parameter_provider_ != nullptr);
  link_idle_disconnect_alarm_.Schedule(common::BindOnce(&Link::Disconnect, common::Unretained(this)),
                                       parameter_provider_->GetLeLinkIdleDisconnectTimeout());
  acl_connection_->RegisterCallbacks(this, l2cap_handler_);
}

void Link::OnAclDisconnected(hci::ErrorCode reason) {
  fixed_channel_allocator_.OnAclDisconnected(static_cast<hci::ErrorCode>(reason));
  dynamic_channel_allocator_.OnAclDisconnected(static_cast<hci::ErrorCode>(reason));
}

void Link::OnDisconnection(hci::ErrorCode status) {
  OnAclDisconnected(status);

  link_manager_->OnDisconnect(GetAclConnection()->GetRemoteAddress());
}

void Link::OnConnectionUpdate(
    hci::ErrorCode /* hci_status */,
    uint16_t connection_interval,
    uint16_t connection_latency,
    uint16_t supervision_timeout) {
  LOG_INFO(
      "interval %hx latency %hx supervision_timeout %hx", connection_interval, connection_latency, supervision_timeout);
  if (update_request_signal_id_ != kInvalidSignalId) {
    hci::ErrorCode result = hci::ErrorCode::SUCCESS;
    if (connection_interval > update_request_interval_max_ || connection_interval < update_request_interval_min_ ||
        connection_latency != update_request_latency_ || supervision_timeout != update_request_supervision_timeout_) {
      LOG_INFO("Received connection update complete with different parameters that provided by the Host");
    }

    if (!CheckConnectionParameters(connection_interval, connection_interval, connection_latency, supervision_timeout)) {
      result = hci::ErrorCode::UNSPECIFIED_ERROR;
    }

    on_connection_update_complete(update_request_signal_id_, result);
    update_request_signal_id_ = kInvalidSignalId;
  }
}

void Link::OnDataLengthChange(uint16_t tx_octets, uint16_t tx_time, uint16_t rx_octets, uint16_t rx_time) {
  LOG_INFO("tx_octets %hx tx_time %hx rx_octets %hx rx_time %hx", tx_octets, tx_time, rx_octets, rx_time);
}

void Link::OnReadRemoteVersionInformationComplete(
    hci::ErrorCode hci_status, uint8_t lmp_version, uint16_t manufacturer_name, uint16_t sub_version) {
  LOG_INFO("lmp_version:%hhu manufacturer_name:%hu sub_version:%hu", lmp_version, manufacturer_name, sub_version);
  link_manager_->OnReadRemoteVersionInformationComplete(
      hci_status, GetDevice(), lmp_version, manufacturer_name, sub_version);
}

void Link::OnLeReadRemoteFeaturesComplete(hci::ErrorCode /* hci_status*/, uint64_t /* features */) {
}

void Link::OnPhyUpdate(
    hci::ErrorCode /* hci_status */, uint8_t /* tx_phy */, uint8_t /* rx_phy */) {}

void Link::OnLeSubrateChange(
    hci::ErrorCode hci_status,
    uint16_t subrate_factor,
    uint16_t peripheral_latency,
    uint16_t continuation_number,
    uint16_t supervision_timeout) {
  LOG_INFO(
      "hci_status: %s, subrate_factor: %#hx, peripheral_latency: %#hx, continuation_number: %#hx, "
      "supervision_timeout: %#hx",
      ErrorCodeText(hci_status).c_str(),
      subrate_factor,
      peripheral_latency,
      continuation_number,
      supervision_timeout);
}

void Link::Disconnect() {
  acl_connection_->Disconnect(hci::DisconnectReason::REMOTE_USER_TERMINATED_CONNECTION);
}

void Link::UpdateConnectionParameterFromRemote(SignalId signal_id, uint16_t conn_interval_min,
                                               uint16_t conn_interval_max, uint16_t conn_latency,
                                               uint16_t supervision_timeout) {
  acl_connection_->LeConnectionUpdate(conn_interval_min, conn_interval_max, conn_latency, supervision_timeout,
                                      kDefaultMinimumCeLength, kDefaultMaximumCeLength);
  update_request_signal_id_ = signal_id;
  update_request_interval_min_ = conn_interval_min;
  update_request_interval_max_ = conn_interval_max;
  update_request_latency_ = conn_latency;
  update_request_supervision_timeout_ = supervision_timeout;
}

bool Link::CheckConnectionParameters(
    uint16_t conn_interval_min, uint16_t conn_interval_max, uint16_t conn_latency, uint16_t supervision_timeout) {
  if (conn_interval_min < 0x0006 || conn_interval_min > 0x0C80 || conn_interval_max < 0x0006 ||
      conn_interval_max > 0x0C80 || conn_latency > 0x01F3 || supervision_timeout < 0x000A ||
      supervision_timeout > 0x0C80) {
    LOG_ERROR("Invalid parameter");
    return false;
  }

  // The Maximum interval in milliseconds will be conn_interval_max * 1.25 ms
  // The Timeout in milliseconds will be expected_supervision_timeout * 10 ms
  // The Timeout in milliseconds shall be larger than (1 + Latency) * Interval_Max * 2, where Interval_Max is given in
  // milliseconds.
  uint32_t supervision_timeout_min = (uint32_t)(1 + conn_latency) * conn_interval_max * 2 + 1;
  if (supervision_timeout * 8 < supervision_timeout_min || conn_interval_max < conn_interval_min) {
    LOG_ERROR("Invalid parameter");
    return false;
  }

  return true;
}

void Link::SendConnectionParameterUpdate(uint16_t conn_interval_min, uint16_t conn_interval_max, uint16_t conn_latency,
                                         uint16_t supervision_timeout, uint16_t min_ce_length, uint16_t max_ce_length) {
  if (acl_connection_->GetRole() == hci::Role::PERIPHERAL) {
    // TODO: If both LL central and peripheral support 4.1, use HCI command directly
    signalling_manager_.SendConnectionParameterUpdateRequest(conn_interval_min, conn_interval_max, conn_latency,
                                                             supervision_timeout);
    return;
  }
  acl_connection_->LeConnectionUpdate(conn_interval_min, conn_interval_max, conn_latency, supervision_timeout,
                                      min_ce_length, max_ce_length);
  update_request_signal_id_ = kInvalidSignalId;
}

std::shared_ptr<FixedChannelImpl> Link::AllocateFixedChannel(
    Cid cid, SecurityPolicy /* security_policy */) {
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

void Link::SendConnectionRequest(Psm psm, PendingDynamicChannelConnection pending_dynamic_channel_connection) {
  if (dynamic_channel_allocator_.IsPsmUsed(psm)) {
    LOG_INFO("Psm %d is already connected", psm);
    return;
  }
  auto reserved_cid = ReserveDynamicChannel();
  auto mtu = pending_dynamic_channel_connection.configuration_.mtu;
  local_cid_to_pending_dynamic_channel_connection_map_[reserved_cid] = std::move(pending_dynamic_channel_connection);
  signalling_manager_.SendConnectionRequest(psm, reserved_cid, mtu);
}

void Link::SendDisconnectionRequest(Cid local_cid, Cid remote_cid) {
  auto channel = dynamic_channel_allocator_.FindChannelByCid(local_cid);
  if (channel == nullptr || channel->GetRemoteCid() != remote_cid) {
    LOG_ERROR("Invalid cid");
  }
  signalling_manager_.SendDisconnectRequest(local_cid, remote_cid);
}

void Link::OnOutgoingConnectionRequestFail(Cid local_cid, LeCreditBasedConnectionResponseResult response_result) {
  if (local_cid_to_pending_dynamic_channel_connection_map_.find(local_cid) !=
      local_cid_to_pending_dynamic_channel_connection_map_.end()) {
    // TODO(hsz): Currently we only notify the client when the remote didn't send connection response SUCCESS.
    //  Should we notify the client when the link failed to establish?
    DynamicChannelManager::ConnectionResult result{
        .connection_result_code = DynamicChannelManager::ConnectionResultCode::FAIL_L2CAP_ERROR,
        .hci_error = hci::ErrorCode::SUCCESS,
        .l2cap_connection_response_result = response_result,
    };
    NotifyChannelFail(local_cid, result);
  }
  dynamic_channel_allocator_.FreeChannel(local_cid);
}

std::shared_ptr<l2cap::internal::DynamicChannelImpl> Link::AllocateDynamicChannel(Psm psm, Cid remote_cid) {
  auto channel = dynamic_channel_allocator_.AllocateChannel(psm, remote_cid);
  if (channel != nullptr) {
    data_pipeline_manager_.AttachChannel(channel->GetCid(), channel,
                                         l2cap::internal::DataPipelineManager::ChannelMode::LE_CREDIT_BASED);
    RefreshRefCount();
    channel->local_initiated_ = false;
  }
  return channel;
}

std::shared_ptr<l2cap::internal::DynamicChannelImpl> Link::AllocateReservedDynamicChannel(Cid reserved_cid, Psm psm,
                                                                                          Cid remote_cid) {
  auto channel = dynamic_channel_allocator_.AllocateReservedChannel(reserved_cid, psm, remote_cid);
  if (channel != nullptr) {
    data_pipeline_manager_.AttachChannel(channel->GetCid(), channel,
                                         l2cap::internal::DataPipelineManager::ChannelMode::LE_CREDIT_BASED);
    RefreshRefCount();
    channel->local_initiated_ = true;
  }
  return channel;
}

void Link::FreeDynamicChannel(Cid cid) {
  if (dynamic_channel_allocator_.FindChannelByCid(cid) == nullptr) {
    return;
  }
  data_pipeline_manager_.DetachChannel(cid);
  dynamic_channel_allocator_.FreeChannel(cid);
  RefreshRefCount();
}

void Link::RefreshRefCount() {
  int ref_count = 0;
  ref_count += fixed_channel_allocator_.GetRefCount();
  ref_count += dynamic_channel_allocator_.NumberOfChannels();
  ASSERT_LOG(ref_count >= 0, "ref_count %d is less than 0", ref_count);
  if (ref_count > 0) {
    link_idle_disconnect_alarm_.Cancel();
  } else {
    link_idle_disconnect_alarm_.Schedule(common::BindOnce(&Link::Disconnect, common::Unretained(this)),
                                         parameter_provider_->GetLeLinkIdleDisconnectTimeout());
  }
}

void Link::NotifyChannelCreation(Cid cid, std::unique_ptr<DynamicChannel> user_channel) {
  ASSERT(local_cid_to_pending_dynamic_channel_connection_map_.find(cid) !=
         local_cid_to_pending_dynamic_channel_connection_map_.end());
  auto& pending_dynamic_channel_connection = local_cid_to_pending_dynamic_channel_connection_map_[cid];
  pending_dynamic_channel_connection.handler_->Post(
      common::BindOnce(std::move(pending_dynamic_channel_connection.on_open_callback_), std::move(user_channel)));
  local_cid_to_pending_dynamic_channel_connection_map_.erase(cid);
}

void Link::NotifyChannelFail(Cid cid, DynamicChannelManager::ConnectionResult result) {
  ASSERT(local_cid_to_pending_dynamic_channel_connection_map_.find(cid) !=
         local_cid_to_pending_dynamic_channel_connection_map_.end());
  auto& pending_dynamic_channel_connection = local_cid_to_pending_dynamic_channel_connection_map_[cid];
  // TODO(cmanton) Pass proper connection falure result to user
  pending_dynamic_channel_connection.handler_->Post(
      common::BindOnce(std::move(pending_dynamic_channel_connection.on_fail_callback_), result));
  local_cid_to_pending_dynamic_channel_connection_map_.erase(cid);
}

uint16_t Link::GetMps() const {
  return parameter_provider_->GetLeMps();
}

uint16_t Link::GetInitialCredit() const {
  return parameter_provider_->GetLeInitialCredit();
}

void Link::SendLeCredit(Cid local_cid, uint16_t credit) {
  signalling_manager_.SendCredit(local_cid, credit);
}

void Link::ReadRemoteVersionInformation() {
  acl_connection_->ReadRemoteVersionInformation();
}

void Link::on_connection_update_complete(SignalId signal_id, hci::ErrorCode error_code) {
  if (!signal_id.IsValid()) {
    LOG_INFO("Invalid signal_id");
    return;
  }
  ConnectionParameterUpdateResponseResult result = (error_code == hci::ErrorCode::SUCCESS)
                                                       ? ConnectionParameterUpdateResponseResult::ACCEPTED
                                                       : ConnectionParameterUpdateResponseResult::REJECTED;
  signalling_manager_.SendConnectionParameterUpdateResponse(SignalId(), result);
}

void Link::OnPendingPacketChange(Cid /* local_cid */, bool has_packet) {
  if (has_packet) {
    remaining_packets_to_be_sent_++;
  } else {
    remaining_packets_to_be_sent_--;
  }
  link_manager_->OnPendingPacketChange(GetDevice(), remaining_packets_to_be_sent_);
}

}  // namespace internal
}  // namespace le
}  // namespace l2cap
}  // namespace bluetooth
