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

#include "l2cap/le/internal/signalling_manager.h"

#include <chrono>

#include "common/bind.h"
#include "l2cap/internal/data_pipeline_manager.h"
#include "l2cap/internal/dynamic_channel_impl.h"
#include "l2cap/internal/le_credit_based_channel_data_controller.h"
#include "l2cap/l2cap_packets.h"
#include "l2cap/le/internal/link.h"
#include "os/log.h"
#include "packet/raw_builder.h"

namespace bluetooth {
namespace l2cap {
namespace le {
namespace internal {

static constexpr auto kTimeout = std::chrono::seconds(3);

LeSignallingManager::LeSignallingManager(os::Handler* handler, Link* link,
                                         l2cap::internal::DataPipelineManager* data_pipeline_manager,
                                         DynamicChannelServiceManagerImpl* dynamic_service_manager,
                                         l2cap::internal::DynamicChannelAllocator* channel_allocator)
    : handler_(handler), link_(link), data_pipeline_manager_(data_pipeline_manager),
      dynamic_service_manager_(dynamic_service_manager), channel_allocator_(channel_allocator), alarm_(handler) {
  ASSERT(handler_ != nullptr);
  ASSERT(link_ != nullptr);
  signalling_channel_ =
      link_->AllocateFixedChannel(kLeSignallingCid, SecurityPolicy::NO_SECURITY_WHATSOEVER_PLAINTEXT_TRANSPORT_OK);
  signalling_channel_->GetQueueUpEnd()->RegisterDequeue(
      handler_, common::Bind(&LeSignallingManager::on_incoming_packet, common::Unretained(this)));
  enqueue_buffer_ =
      std::make_unique<os::EnqueueBuffer<packet::BasePacketBuilder>>(signalling_channel_->GetQueueUpEnd());
}

LeSignallingManager::~LeSignallingManager() {
  enqueue_buffer_.reset();
  signalling_channel_->GetQueueUpEnd()->UnregisterDequeue();
  signalling_channel_ = nullptr;
}

void LeSignallingManager::SendConnectionRequest(Psm psm, Cid local_cid, Mtu mtu) {
  dynamic_service_manager_->GetSecurityEnforcementInterface()->Enforce(
      link_->GetDevice(),
      dynamic_service_manager_->GetService(psm)->GetSecurityPolicy(),
      handler_->BindOnceOn(this, &LeSignallingManager::on_security_result_for_outgoing, psm, local_cid, mtu));
}

void LeSignallingManager::on_security_result_for_outgoing(Psm psm, Cid local_cid, Mtu mtu, bool result) {
  if (!result) {
    LOG_WARN("Security requirement can't be satisfied. Dropping connection request");
    return;
  }

  PendingCommand pending_command = PendingCommand::CreditBasedConnectionRequest(
      next_signal_id_, psm, local_cid, mtu, link_->GetMps(), link_->GetInitialCredit());
  next_signal_id_++;
  pending_commands_.push(pending_command);
  if (pending_commands_.size() == 1) {
    handle_send_next_command();
  }
}

void LeSignallingManager::SendDisconnectRequest(Cid scid, Cid dcid) {
  PendingCommand pending_command = PendingCommand::DisconnectionRequest(next_signal_id_, scid, dcid);
  next_signal_id_++;
  pending_commands_.push(pending_command);
  if (pending_commands_.size() == 1) {
    handle_send_next_command();
  }
}

void LeSignallingManager::SendConnectionParameterUpdateRequest(
    uint16_t interval_min, uint16_t interval_max, uint16_t peripheral_latency, uint16_t timeout_multiplier) {
  PendingCommand pending_command = PendingCommand::ConnectionParameterUpdate(
      next_signal_id_, interval_min, interval_max, peripheral_latency, timeout_multiplier);
  next_signal_id_++;
  pending_commands_.push(pending_command);
  if (pending_commands_.size() == 1) {
    handle_send_next_command();
  }
}

void LeSignallingManager::SendConnectionParameterUpdateResponse(SignalId signal_id,
                                                                ConnectionParameterUpdateResponseResult result) {
  auto builder = ConnectionParameterUpdateResponseBuilder::Create(signal_id.Value(), result);
  enqueue_buffer_->Enqueue(std::move(builder), handler_);
}

void LeSignallingManager::SendCredit(Cid local_cid, uint16_t credits) {
  auto builder = LeFlowControlCreditBuilder::Create(next_signal_id_.Value(), local_cid, credits);
  next_signal_id_++;
  enqueue_buffer_->Enqueue(std::move(builder), handler_);
}

void LeSignallingManager::SendEnhancedConnectionRequest(
    Psm /* psm */, std::vector<Cid> /* local_cid */, Mtu /* mtu */) {}

void LeSignallingManager::SendEnhancedReconfigureRequest(
    std::vector<Cid> /* local_cid */, Mtu /* mtu */) {}

void LeSignallingManager::CancelAlarm() {
  alarm_.Cancel();
}

void LeSignallingManager::OnCommandReject(LeCommandRejectView command_reject_view) {
  auto signal_id = command_reject_view.GetIdentifier();
  if (signal_id != command_just_sent_.signal_id_) {
    LOG_WARN("Unexpected response: no pending request");
    return;
  }
  alarm_.Cancel();
  if (command_just_sent_.command_code_ == LeCommandCode::LE_CREDIT_BASED_CONNECTION_REQUEST) {
    link_->OnOutgoingConnectionRequestFail(command_just_sent_.source_cid_,
                                           LeCreditBasedConnectionResponseResult::NO_RESOURCES_AVAILABLE);
  }
  handle_send_next_command();

  LOG_WARN("Command rejected");
}

void LeSignallingManager::OnConnectionParameterUpdateRequest(
    SignalId signal_id,
    uint16_t interval_min,
    uint16_t interval_max,
    uint16_t peripheral_latency,
    uint16_t timeout_multiplier) {
  if (link_->GetRole() == hci::Role::PERIPHERAL) {
    LOG_WARN("Received request from LL central");
    auto builder = LeCommandRejectNotUnderstoodBuilder::Create(signal_id.Value());
    enqueue_buffer_->Enqueue(std::move(builder), handler_);
    return;
  }

  if (!link_->CheckConnectionParameters(interval_min, interval_max, peripheral_latency, timeout_multiplier)) {
    LOG_WARN("Received invalid connection parameter update request from LL central");
    auto builder = ConnectionParameterUpdateResponseBuilder::Create(signal_id.Value(),
                                                                    ConnectionParameterUpdateResponseResult::REJECTED);
    enqueue_buffer_->Enqueue(std::move(builder), handler_);
    return;
  }
  link_->UpdateConnectionParameterFromRemote(
      signal_id, interval_min, interval_max, peripheral_latency, timeout_multiplier);
}

void LeSignallingManager::OnConnectionParameterUpdateResponse(SignalId signal_id,
                                                              ConnectionParameterUpdateResponseResult result) {
  if (signal_id != command_just_sent_.signal_id_) {
    LOG_WARN("Unexpected response: no pending request");
    return;
  }
  if (command_just_sent_.command_code_ != LeCommandCode::CONNECTION_PARAMETER_UPDATE_REQUEST) {
    LOG_WARN("Unexpected response: no pending request");
    return;
  }
  alarm_.Cancel();
  command_just_sent_.signal_id_ = kInitialSignalId;
  if (result != ConnectionParameterUpdateResponseResult::ACCEPTED) {
    LOG_ERROR("Connection parameter update is not accepted");
  }
}

void LeSignallingManager::OnConnectionRequest(SignalId signal_id, Psm psm, Cid remote_cid, Mtu mtu, uint16_t mps,
                                              uint16_t initial_credits) {
  if (remote_cid == kInvalidCid) {
    LOG_WARN("Invalid remote cid received from remote psm:%d remote_cid:%d", psm, remote_cid);
    send_connection_response(signal_id, kInvalidCid, 0, 0, 0,
                             LeCreditBasedConnectionResponseResult::INVALID_SOURCE_CID);
    return;
  }

  if (channel_allocator_->IsPsmUsed(psm)) {
    LOG_WARN("Psm already exists");
    send_connection_response(signal_id, kInvalidCid, 0, 0, 0,
                             LeCreditBasedConnectionResponseResult::LE_PSM_NOT_SUPPORTED);
    return;
  }

  if (!dynamic_service_manager_->IsServiceRegistered(psm)) {
    LOG_INFO("Service for this psm (%d) is not registered", psm);
    send_connection_response(signal_id, kInvalidCid, 0, 0, 0,
                             LeCreditBasedConnectionResponseResult::LE_PSM_NOT_SUPPORTED);
    return;
  }

  PendingConnection pending{
      .remote_cid = remote_cid,
      .mtu = mtu,
      .max_pdu_size = mps,
      .initial_credits = initial_credits,
      .incoming_signal_id = signal_id,
  };
  dynamic_service_manager_->GetSecurityEnforcementInterface()->Enforce(
      link_->GetDevice(),
      dynamic_service_manager_->GetService(psm)->GetSecurityPolicy(),
      handler_->BindOnceOn(this, &LeSignallingManager::on_security_result_for_incoming, psm, pending));
}

void LeSignallingManager::on_security_result_for_incoming(Psm psm, PendingConnection request, bool result) {
  auto signal_id = request.incoming_signal_id;
  auto* service = dynamic_service_manager_->GetService(psm);
  if (!result) {
    auto security_policy = service->GetSecurityPolicy();
    switch (security_policy) {
      case SecurityPolicy::NO_SECURITY_WHATSOEVER_PLAINTEXT_TRANSPORT_OK:
        LOG_ERROR("If no security requirement, we should never fail");
        break;
      case SecurityPolicy::ENCRYPTED_TRANSPORT:
        send_connection_response(signal_id, kInvalidCid, 0, 0, 0,
                                 LeCreditBasedConnectionResponseResult::INSUFFICIENT_AUTHENTICATION);
        return;
      case SecurityPolicy::AUTHENTICATED_ENCRYPTED_TRANSPORT:
      case SecurityPolicy::BEST:
        send_connection_response(signal_id, kInvalidCid, 0, 0, 0,
                                 LeCreditBasedConnectionResponseResult::INSUFFICIENT_AUTHENTICATION);
        return;
      case SecurityPolicy::_NOT_FOR_YOU__AUTHENTICATED_PAIRING_WITH_128_BIT_KEY:
        send_connection_response(signal_id, kInvalidCid, 0, 0, 0,
                                 LeCreditBasedConnectionResponseResult::INSUFFICIENT_ENCRYPTION_KEY_SIZE);
        return;
      case SecurityPolicy::_NOT_FOR_YOU__AUTHORIZATION:
        send_connection_response(signal_id, kInvalidCid, 0, 0, 0,
                                 LeCreditBasedConnectionResponseResult::INSUFFICIENT_AUTHORIZATION);
        return;
    }
  }
  auto config = service->GetConfigOption();
  auto local_mtu = config.mtu;
  auto local_mps = link_->GetMps();

  auto new_channel = link_->AllocateDynamicChannel(psm, request.remote_cid);
  if (new_channel == nullptr) {
    LOG_WARN("Can't allocate dynamic channel");
    // TODO: We need to respond with the correct reason
    send_connection_response(signal_id, kInvalidCid, 0, 0, 0,
                             LeCreditBasedConnectionResponseResult::SOURCE_CID_ALREADY_ALLOCATED);
    return;
  }

  send_connection_response(signal_id, new_channel->GetCid(), local_mtu, local_mps, link_->GetInitialCredit(),
                           LeCreditBasedConnectionResponseResult::SUCCESS);
  auto* data_controller = reinterpret_cast<l2cap::internal::LeCreditBasedDataController*>(
      data_pipeline_manager_->GetDataController(new_channel->GetCid()));
  auto actual_mtu = std::min(request.mtu, local_mtu);
  data_controller->SetMtu(actual_mtu);
  data_controller->SetMps(std::min(request.max_pdu_size, local_mps));
  data_controller->OnCredit(request.initial_credits);
  auto user_channel = std::make_unique<DynamicChannel>(new_channel, handler_, link_, actual_mtu);
  dynamic_service_manager_->GetService(psm)->NotifyChannelCreation(std::move(user_channel));
}

void LeSignallingManager::OnConnectionResponse(SignalId signal_id, Cid remote_cid, Mtu mtu, uint16_t mps,
                                               uint16_t initial_credits, LeCreditBasedConnectionResponseResult result) {
  if (signal_id != command_just_sent_.signal_id_) {
    LOG_WARN("Unexpected response: no pending request");
    return;
  }
  if (command_just_sent_.command_code_ != LeCommandCode::LE_CREDIT_BASED_CONNECTION_REQUEST) {
    LOG_WARN("Unexpected response: no pending request");
    return;
  }
  alarm_.Cancel();
  command_just_sent_.signal_id_ = kInitialSignalId;
  if (result != LeCreditBasedConnectionResponseResult::SUCCESS) {
    LOG_WARN("Connection failed: %s", LeCreditBasedConnectionResponseResultText(result).data());
    link_->OnOutgoingConnectionRequestFail(command_just_sent_.source_cid_, result);
    handle_send_next_command();
    return;
  }
  auto new_channel =
      link_->AllocateReservedDynamicChannel(command_just_sent_.source_cid_, command_just_sent_.psm_, remote_cid);
  if (new_channel == nullptr) {
    LOG_WARN("Can't allocate dynamic channel");
    link_->OnOutgoingConnectionRequestFail(command_just_sent_.source_cid_,
                                           LeCreditBasedConnectionResponseResult::NO_RESOURCES_AVAILABLE);
    handle_send_next_command();
    return;
  }
  auto* data_controller = reinterpret_cast<l2cap::internal::LeCreditBasedDataController*>(
      data_pipeline_manager_->GetDataController(new_channel->GetCid()));
  auto actual_mtu = std::min(mtu, command_just_sent_.mtu_);
  data_controller->SetMtu(actual_mtu);
  data_controller->SetMps(std::min(mps, command_just_sent_.mps_));
  data_controller->OnCredit(initial_credits);
  std::unique_ptr<DynamicChannel> user_channel =
      std::make_unique<DynamicChannel>(new_channel, handler_, link_, actual_mtu);
  link_->NotifyChannelCreation(new_channel->GetCid(), std::move(user_channel));
}

void LeSignallingManager::OnDisconnectionRequest(SignalId signal_id, Cid cid, Cid remote_cid) {
  auto channel = channel_allocator_->FindChannelByCid(cid);
  if (channel == nullptr) {
    LOG_WARN("Disconnect request for an unknown channel");
    return;
  }
  if (channel->GetRemoteCid() != remote_cid) {
    LOG_WARN("Disconnect request for an unmatching channel");
    return;
  }
  auto builder = LeDisconnectionResponseBuilder::Create(signal_id.Value(), cid, remote_cid);
  enqueue_buffer_->Enqueue(std::move(builder), handler_);
  channel->OnClosed(hci::ErrorCode::SUCCESS);
  link_->FreeDynamicChannel(cid);
}

void LeSignallingManager::OnDisconnectionResponse(SignalId signal_id, Cid remote_cid, Cid cid) {
  if (signal_id != command_just_sent_.signal_id_ ||
      command_just_sent_.command_code_ != LeCommandCode::DISCONNECTION_REQUEST) {
    LOG_WARN("Unexpected response: no pending request");
    return;
  }
  if (command_just_sent_.source_cid_ != cid || command_just_sent_.destination_cid_ != remote_cid) {
    LOG_WARN("Unexpected response: cid doesn't match. Expected scid %d dcid %d, got scid %d dcid %d",
             command_just_sent_.source_cid_, command_just_sent_.destination_cid_, cid, remote_cid);
    handle_send_next_command();
    return;
  }
  alarm_.Cancel();
  command_just_sent_.signal_id_ = kInitialSignalId;
  auto channel = channel_allocator_->FindChannelByCid(cid);
  if (channel == nullptr) {
    LOG_WARN("Disconnect response for an unknown channel");
    handle_send_next_command();
    return;
  }

  channel->OnClosed(hci::ErrorCode::SUCCESS);
  link_->FreeDynamicChannel(cid);
  handle_send_next_command();
}

void LeSignallingManager::OnCredit(Cid remote_cid, uint16_t credits) {
  auto channel = channel_allocator_->FindChannelByRemoteCid(remote_cid);
  if (channel == nullptr) {
    LOG_WARN("Received credit for invalid cid %d", channel->GetCid());
    return;
  }
  auto* data_controller = reinterpret_cast<l2cap::internal::LeCreditBasedDataController*>(
      data_pipeline_manager_->GetDataController(channel->GetCid()));
  data_controller->OnCredit(credits);
}

void LeSignallingManager::on_incoming_packet() {
  auto packet = signalling_channel_->GetQueueUpEnd()->TryDequeue();
  LeControlView control_packet_view = LeControlView::Create(*packet);
  if (!control_packet_view.IsValid()) {
    LOG_WARN("Invalid signalling packet received");
    return;
  }
  auto code = control_packet_view.GetCode();
  switch (code) {
    case LeCommandCode::COMMAND_REJECT: {
      LeCommandRejectView command_reject_view = LeCommandRejectView::Create(control_packet_view);
      if (!command_reject_view.IsValid()) {
        return;
      }
      OnCommandReject(command_reject_view);
      return;
    }

    case LeCommandCode::CONNECTION_PARAMETER_UPDATE_REQUEST: {
      ConnectionParameterUpdateRequestView parameter_update_req_view =
          ConnectionParameterUpdateRequestView::Create(control_packet_view);
      if (!parameter_update_req_view.IsValid()) {
        return;
      }
      OnConnectionParameterUpdateRequest(
          parameter_update_req_view.GetIdentifier(),
          parameter_update_req_view.GetIntervalMin(),
          parameter_update_req_view.GetIntervalMax(),
          parameter_update_req_view.GetPeripheralLatency(),
          parameter_update_req_view.GetTimeoutMultiplier());
      return;
    }
    case LeCommandCode::CONNECTION_PARAMETER_UPDATE_RESPONSE: {
      ConnectionParameterUpdateResponseView parameter_update_rsp_view =
          ConnectionParameterUpdateResponseView::Create(control_packet_view);
      if (!parameter_update_rsp_view.IsValid()) {
        return;
      }
      OnConnectionParameterUpdateResponse(parameter_update_rsp_view.GetIdentifier(),
                                          parameter_update_rsp_view.GetResult());
      return;
    }
    case LeCommandCode::LE_CREDIT_BASED_CONNECTION_REQUEST: {
      LeCreditBasedConnectionRequestView connection_request_view =
          LeCreditBasedConnectionRequestView::Create(control_packet_view);
      if (!connection_request_view.IsValid()) {
        return;
      }
      OnConnectionRequest(connection_request_view.GetIdentifier(), connection_request_view.GetLePsm(),
                          connection_request_view.GetSourceCid(), connection_request_view.GetMtu(),
                          connection_request_view.GetMps(), connection_request_view.GetInitialCredits());
      return;
    }
    case LeCommandCode::LE_CREDIT_BASED_CONNECTION_RESPONSE: {
      LeCreditBasedConnectionResponseView connection_response_view =
          LeCreditBasedConnectionResponseView::Create(control_packet_view);
      if (!connection_response_view.IsValid()) {
        return;
      }
      OnConnectionResponse(connection_response_view.GetIdentifier(), connection_response_view.GetDestinationCid(),
                           connection_response_view.GetMtu(), connection_response_view.GetMps(),
                           connection_response_view.GetInitialCredits(), connection_response_view.GetResult());
      return;
    }
    case LeCommandCode::LE_FLOW_CONTROL_CREDIT: {
      LeFlowControlCreditView credit_view = LeFlowControlCreditView::Create(control_packet_view);
      if (!credit_view.IsValid()) {
        return;
      }
      OnCredit(credit_view.GetCid(), credit_view.GetCredits());
      return;
    }
    case LeCommandCode::DISCONNECTION_REQUEST: {
      LeDisconnectionRequestView disconnection_request_view = LeDisconnectionRequestView::Create(control_packet_view);
      if (!disconnection_request_view.IsValid()) {
        return;
      }
      OnDisconnectionRequest(disconnection_request_view.GetIdentifier(), disconnection_request_view.GetDestinationCid(),
                             disconnection_request_view.GetSourceCid());
      return;
    }
    case LeCommandCode::DISCONNECTION_RESPONSE: {
      LeDisconnectionResponseView disconnection_response_view =
          LeDisconnectionResponseView::Create(control_packet_view);
      if (!disconnection_response_view.IsValid()) {
        return;
      }
      OnDisconnectionResponse(disconnection_response_view.GetIdentifier(),
                              disconnection_response_view.GetDestinationCid(),
                              disconnection_response_view.GetSourceCid());
      return;
    }
    case LeCommandCode::CREDIT_BASED_CONNECTION_REQUEST: {
      LeEnhancedCreditBasedConnectionRequestView request_view =
          LeEnhancedCreditBasedConnectionRequestView::Create(control_packet_view);
      if (!request_view.IsValid()) {
        return;
      }
      return;
    }
    case LeCommandCode::CREDIT_BASED_CONNECTION_RESPONSE: {
      LeEnhancedCreditBasedConnectionResponseView response_view =
          LeEnhancedCreditBasedConnectionResponseView::Create(control_packet_view);
      if (!response_view.IsValid()) {
        return;
      }
      return;
    }
    case LeCommandCode::CREDIT_BASED_RECONFIGURE_REQUEST: {
      LeEnhancedCreditBasedReconfigureRequestView request_view =
          LeEnhancedCreditBasedReconfigureRequestView::Create(control_packet_view);
      if (!request_view.IsValid()) {
        return;
      }
      return;
    }
    case LeCommandCode::CREDIT_BASED_RECONFIGURE_RESPONSE: {
      LeEnhancedCreditBasedReconfigureResponseView response_view =
          LeEnhancedCreditBasedReconfigureResponseView::Create(control_packet_view);
      if (!response_view.IsValid()) {
        return;
      }
      return;
    }
    default:
      LOG_WARN("Unhandled event 0x%x", static_cast<int>(code));
      auto builder = LeCommandRejectNotUnderstoodBuilder::Create(control_packet_view.GetIdentifier());
      enqueue_buffer_->Enqueue(std::move(builder), handler_);
      return;
  }
}

void LeSignallingManager::send_connection_response(SignalId signal_id, Cid local_cid, Mtu mtu, uint16_t mps,
                                                   uint16_t initial_credit,
                                                   LeCreditBasedConnectionResponseResult result) {
  auto builder =
      LeCreditBasedConnectionResponseBuilder::Create(signal_id.Value(), local_cid, mtu, mps, initial_credit, result);
  enqueue_buffer_->Enqueue(std::move(builder), handler_);
}

void LeSignallingManager::on_command_timeout() {
  LOG_WARN("Response time out");
  if (command_just_sent_.signal_id_ == kInvalidSignalId) {
    LOG_ERROR("No pending command");
    return;
  }
  switch (command_just_sent_.command_code_) {
    case LeCommandCode::CONNECTION_PARAMETER_UPDATE_REQUEST: {
      link_->OnOutgoingConnectionRequestFail(command_just_sent_.source_cid_,
                                             LeCreditBasedConnectionResponseResult::NO_RESOURCES_AVAILABLE);
      break;
    }
    default:
      break;
  }
  handle_send_next_command();
}

void LeSignallingManager::handle_send_next_command() {
  command_just_sent_.signal_id_ = kInvalidSignalId;
  if (pending_commands_.empty()) {
    return;
  }

  command_just_sent_ = pending_commands_.front();
  pending_commands_.pop();
  switch (command_just_sent_.command_code_) {
    case LeCommandCode::LE_CREDIT_BASED_CONNECTION_REQUEST: {
      auto builder = LeCreditBasedConnectionRequestBuilder::Create(
          command_just_sent_.signal_id_.Value(), command_just_sent_.psm_, command_just_sent_.source_cid_,
          command_just_sent_.mtu_, command_just_sent_.mps_, command_just_sent_.credits_);
      enqueue_buffer_->Enqueue(std::move(builder), handler_);
      alarm_.Schedule(common::BindOnce(&LeSignallingManager::on_command_timeout, common::Unretained(this)), kTimeout);
      break;
    }
    case LeCommandCode::DISCONNECTION_REQUEST: {
      auto builder = LeDisconnectionRequestBuilder::Create(
          command_just_sent_.signal_id_.Value(), command_just_sent_.destination_cid_, command_just_sent_.source_cid_);
      enqueue_buffer_->Enqueue(std::move(builder), handler_);
      alarm_.Schedule(common::BindOnce(&LeSignallingManager::on_command_timeout, common::Unretained(this)), kTimeout);
      break;
    }
    case LeCommandCode::CONNECTION_PARAMETER_UPDATE_REQUEST: {
      auto builder = ConnectionParameterUpdateRequestBuilder::Create(
          command_just_sent_.signal_id_.Value(),
          command_just_sent_.interval_min_,
          command_just_sent_.interval_max_,
          command_just_sent_.peripheral_latency_,
          command_just_sent_.timeout_multiplier_);
      enqueue_buffer_->Enqueue(std::move(builder), handler_);
      alarm_.Schedule(common::BindOnce(&LeSignallingManager::on_command_timeout, common::Unretained(this)), kTimeout);
      break;
    }
    default: {
      LOG_WARN("Unsupported command code 0x%x", static_cast<int>(command_just_sent_.command_code_));
    }
  }
}
}  // namespace internal
}  // namespace le
}  // namespace l2cap
}  // namespace bluetooth
