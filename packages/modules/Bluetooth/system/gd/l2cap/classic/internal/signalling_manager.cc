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

#include "l2cap/classic/internal/signalling_manager.h"

#include <chrono>

#include "common/bind.h"
#include "l2cap/classic/internal/channel_configuration_state.h"
#include "l2cap/classic/internal/link.h"
#include "l2cap/internal/data_pipeline_manager.h"
#include "l2cap/l2cap_packets.h"
#include "os/log.h"
#include "packet/raw_builder.h"

namespace bluetooth {
namespace l2cap {
namespace classic {
namespace internal {
static constexpr auto kTimeout = std::chrono::seconds(3);

static std::vector<ControlView> GetCommandsFromPacketView(PacketView<kLittleEndian> packet) {
  size_t curr = 0;
  size_t end = packet.size();
  std::vector<ControlView> result;
  while (curr < end) {
    auto sub_view = packet.GetLittleEndianSubview(curr, end);
    auto control = ControlView::Create(sub_view);
    if (!control.IsValid()) {
      return {};
    }
    result.push_back(control);
    curr += 1 + 1 + 2 + control.GetPayload().size();
  }
  return result;
}

ClassicSignallingManager::ClassicSignallingManager(os::Handler* handler, Link* link,
                                                   l2cap::internal::DataPipelineManager* data_pipeline_manager,
                                                   DynamicChannelServiceManagerImpl* dynamic_service_manager,
                                                   l2cap::internal::DynamicChannelAllocator* channel_allocator,
                                                   FixedChannelServiceManagerImpl* fixed_service_manager)
    : handler_(handler), link_(link), data_pipeline_manager_(data_pipeline_manager),
      dynamic_service_manager_(dynamic_service_manager), channel_allocator_(channel_allocator),
      fixed_service_manager_(fixed_service_manager), alarm_(handler) {
  ASSERT(handler_ != nullptr);
  ASSERT(link_ != nullptr);
  signalling_channel_ = link_->AllocateFixedChannel(kClassicSignallingCid);
  signalling_channel_->GetQueueUpEnd()->RegisterDequeue(
      handler_, common::Bind(&ClassicSignallingManager::on_incoming_packet, common::Unretained(this)));
  enqueue_buffer_ =
      std::make_unique<os::EnqueueBuffer<packet::BasePacketBuilder>>(signalling_channel_->GetQueueUpEnd());
}

ClassicSignallingManager::~ClassicSignallingManager() {
  alarm_.Cancel();
  signalling_channel_->GetQueueUpEnd()->UnregisterDequeue();
  signalling_channel_ = nullptr;
  enqueue_buffer_->Clear();
  enqueue_buffer_.reset();
}

void ClassicSignallingManager::OnCommandReject(CommandRejectView command_reject_view) {
  if (command_just_sent_.signal_id_ != command_reject_view.GetIdentifier()) {
    LOG_WARN("Unexpected command reject: no pending request");
    return;
  }
  if (command_just_sent_.command_code_ == CommandCode::INFORMATION_REQUEST &&
      command_just_sent_.info_type_ == InformationRequestInfoType::EXTENDED_FEATURES_SUPPORTED) {
    link_->OnRemoteExtendedFeatureReceived(false, false);
  }
  alarm_.Cancel();
  handle_send_next_command();

  LOG_INFO("Command rejected");
}

void ClassicSignallingManager::SendConnectionRequest(Psm psm, Cid local_cid) {
  dynamic_service_manager_->GetSecurityEnforcementInterface()->Enforce(
      link_->GetDevice(),
      dynamic_service_manager_->GetService(psm)->GetSecurityPolicy(),
      handler_->BindOnceOn(
          this,
          &ClassicSignallingManager::on_security_result_for_outgoing,
          SecurityEnforcementType::LINK_KEY,
          psm,
          local_cid));
}

void ClassicSignallingManager::on_security_result_for_outgoing(
    SecurityEnforcementType type, Psm psm, Cid local_cid, bool result) {
  if (enqueue_buffer_.get() == nullptr) {
    LOG_ERROR("Got security result callback after deletion");
    return;
  }
  if (!result) {
    LOG_WARN("Security requirement can't be satisfied. Dropping connection request");
    DynamicChannelManager::ConnectionResult connection_result{
        .connection_result_code = DynamicChannelManager::ConnectionResultCode::FAIL_SECURITY_BLOCK,
        .hci_error = hci::ErrorCode::SUCCESS,
        .l2cap_connection_response_result = ConnectionResponseResult::NO_RESOURCES_AVAILABLE,
    };
    link_->OnOutgoingConnectionRequestFail(local_cid, connection_result);
    return;
  }
  if (type == SecurityEnforcementType::LINK_KEY && !link_->IsAuthenticated() &&
      dynamic_service_manager_->GetService(psm)->GetSecurityPolicy() !=
          SecurityPolicy::_SDP_ONLY_NO_SECURITY_WHATSOEVER_PLAINTEXT_TRANSPORT_OK) {
    link_->Encrypt();
    // TODO(b/171253721): If we can receive ENCRYPTION_CHANGE event, we can send command after callback is received.
  }

  PendingCommand pending_command = {next_signal_id_, CommandCode::CONNECTION_REQUEST, psm, local_cid, {}, {}, {}};
  next_signal_id_++;
  pending_commands_.push(std::move(pending_command));
  if (command_just_sent_.signal_id_ == kInvalidSignalId) {
    handle_send_next_command();
  }
}

void ClassicSignallingManager::send_configuration_request(Cid remote_cid,
                                                          std::vector<std::unique_ptr<ConfigurationOption>> config) {
  PendingCommand pending_command = {next_signal_id_,  CommandCode::CONFIGURATION_REQUEST, {}, {}, remote_cid, {},
                                    std::move(config)};
  next_signal_id_++;
  pending_commands_.push(std::move(pending_command));
  if (command_just_sent_.signal_id_ == kInvalidSignalId) {
    handle_send_next_command();
  }
}

void ClassicSignallingManager::SendDisconnectionRequest(Cid local_cid, Cid remote_cid) {
  PendingCommand pending_command = {
      next_signal_id_, CommandCode::DISCONNECTION_REQUEST, {}, local_cid, remote_cid, {}, {}};
  next_signal_id_++;
  pending_commands_.push(std::move(pending_command));
  if (command_just_sent_.signal_id_ == kInvalidSignalId) {
    handle_send_next_command();
  }
}

void ClassicSignallingManager::SendInformationRequest(InformationRequestInfoType type) {
  PendingCommand pending_command = {next_signal_id_, CommandCode::INFORMATION_REQUEST, {}, {}, {}, type, {}};
  next_signal_id_++;
  pending_commands_.push(std::move(pending_command));
  if (command_just_sent_.signal_id_ == kInvalidSignalId) {
    handle_send_next_command();
  }
}

void ClassicSignallingManager::SendEchoRequest(std::unique_ptr<packet::RawBuilder> /* payload */) {
  LOG_WARN("Not supported");
}

void ClassicSignallingManager::CancelAlarm() {
  alarm_.Cancel();
}

void ClassicSignallingManager::OnConnectionRequest(SignalId signal_id, Psm psm, Cid remote_cid) {
  if (!IsPsmValid(psm)) {
    LOG_WARN("Invalid psm received from remote psm:%d remote_cid:%d", psm, remote_cid);
    send_connection_response(signal_id, remote_cid, kInvalidCid, ConnectionResponseResult::PSM_NOT_SUPPORTED,
                             ConnectionResponseStatus::NO_FURTHER_INFORMATION_AVAILABLE);
    return;
  }

  if (remote_cid == kInvalidCid) {
    LOG_WARN("Invalid remote cid received from remote psm:%d remote_cid:%d", psm, remote_cid);
    send_connection_response(signal_id, remote_cid, kInvalidCid, ConnectionResponseResult::INVALID_CID,
                             ConnectionResponseStatus::NO_FURTHER_INFORMATION_AVAILABLE);
    return;
  }
  /* TODO(zachoverflow): add back in with policy
  if (channel_allocator_->IsPsmUsed(psm)) {
    LOG_WARN("Psm already exists");
    send_connection_response(signal_id, remote_cid, kInvalidCid, ConnectionResponseResult::PSM_NOT_SUPPORTED,
                             ConnectionResponseStatus::NO_FURTHER_INFORMATION_AVAILABLE);
    return;
  }
  */

  if (!dynamic_service_manager_->IsServiceRegistered(psm)) {
    LOG_INFO("Service for this psm (%d) is not registered", psm);
    send_connection_response(signal_id, remote_cid, kInvalidCid, ConnectionResponseResult::PSM_NOT_SUPPORTED,
                             ConnectionResponseStatus::NO_FURTHER_INFORMATION_AVAILABLE);
    return;
  }

  dynamic_service_manager_->GetSecurityEnforcementInterface()->Enforce(
      link_->GetDevice(),
      dynamic_service_manager_->GetService(psm)->GetSecurityPolicy(),
      handler_->BindOnceOn(
          this, &ClassicSignallingManager::on_security_result_for_incoming, psm, remote_cid, signal_id));
}

void ClassicSignallingManager::on_security_result_for_incoming(
    Psm psm, Cid remote_cid, SignalId signal_id, bool result) {
  if (enqueue_buffer_.get() == nullptr) {
    LOG_ERROR("Got security result callback after deletion");
    return;
  }
  if (!result) {
    send_connection_response(
        signal_id,
        remote_cid,
        0,
        ConnectionResponseResult::SECURITY_BLOCK,
        ConnectionResponseStatus::NO_FURTHER_INFORMATION_AVAILABLE);
    DynamicChannelManager::ConnectionResult connection_result{
        .connection_result_code = DynamicChannelManager::ConnectionResultCode::FAIL_SECURITY_BLOCK,
        .hci_error = hci::ErrorCode::SUCCESS,
        .l2cap_connection_response_result = ConnectionResponseResult::NO_RESOURCES_AVAILABLE,
    };
    link_->OnOutgoingConnectionRequestFail(0, connection_result);
  }

  auto new_channel = link_->AllocateDynamicChannel(psm, remote_cid);
  if (new_channel == nullptr) {
    LOG_WARN("Can't allocate dynamic channel");
    return;
  }
  send_connection_response(
      signal_id,
      remote_cid,
      new_channel->GetCid(),
      ConnectionResponseResult::SUCCESS,
      ConnectionResponseStatus::NO_FURTHER_INFORMATION_AVAILABLE);

  link_->SendInitialConfigRequestOrQueue(new_channel->GetCid());
}

void ClassicSignallingManager::OnConnectionResponse(
    SignalId signal_id,
    Cid remote_cid,
    Cid cid,
    ConnectionResponseResult result,
    ConnectionResponseStatus /* status */) {
  if (command_just_sent_.signal_id_ != signal_id ||
      command_just_sent_.command_code_ != CommandCode::CONNECTION_REQUEST) {
    LOG_WARN("Unexpected response: no pending request. Expected signal id %d type %s, got %d",
             command_just_sent_.signal_id_.Value(), CommandCodeText(command_just_sent_.command_code_).data(),
             signal_id.Value());
    return;
  }
  if (command_just_sent_.source_cid_ != cid) {
    LOG_WARN("SCID doesn't match: expected %d, received %d", command_just_sent_.source_cid_, cid);
    handle_send_next_command();
    return;
  }
  if (result == ConnectionResponseResult::PENDING) {
    alarm_.Schedule(common::BindOnce(&ClassicSignallingManager::on_command_timeout, common::Unretained(this)),
                    kTimeout);
    return;
  }

  command_just_sent_.signal_id_ = kInvalidSignalId;
  alarm_.Cancel();
  if (result != ConnectionResponseResult::SUCCESS) {
    DynamicChannelManager::ConnectionResult connection_result{
        .connection_result_code = DynamicChannelManager::ConnectionResultCode::FAIL_L2CAP_ERROR,
        .hci_error = hci::ErrorCode::SUCCESS,
        .l2cap_connection_response_result = result,
    };
    link_->OnOutgoingConnectionRequestFail(cid, connection_result);
    handle_send_next_command();
    return;
  }
  Psm pending_psm = command_just_sent_.psm_;
  auto new_channel = link_->AllocateReservedDynamicChannel(cid, pending_psm, remote_cid);
  if (new_channel == nullptr) {
    LOG_WARN("Can't allocate dynamic channel");
    DynamicChannelManager::ConnectionResult connection_result{
        .connection_result_code = DynamicChannelManager::ConnectionResultCode::FAIL_L2CAP_ERROR,
        .hci_error = hci::ErrorCode::SUCCESS,
        .l2cap_connection_response_result = ConnectionResponseResult::NO_RESOURCES_AVAILABLE,
    };
    link_->OnOutgoingConnectionRequestFail(cid, connection_result);
    handle_send_next_command();
    return;
  }

  link_->SendInitialConfigRequestOrQueue(cid);
}

void ClassicSignallingManager::OnConfigurationRequest(SignalId signal_id, Cid cid, Continuation is_continuation,
                                                      std::vector<std::unique_ptr<ConfigurationOption>> options) {
  auto channel = channel_allocator_->FindChannelByCid(cid);
  if (channel == nullptr) {
    LOG_WARN("Configuration request for an unknown channel");
    return;
  }

  auto& configuration_state = channel_configuration_[cid];
  std::vector<std::unique_ptr<ConfigurationOption>> rsp_options;
  ConfigurationResponseResult result = ConfigurationResponseResult::SUCCESS;
  auto remote_rfc_mode = RetransmissionAndFlowControlModeOption::L2CAP_BASIC;

  auto initial_config_option = dynamic_service_manager_->GetService(channel->GetPsm())->GetConfigOption();

  for (auto& option : options) {
    switch (option->type_) {
      case ConfigurationOptionType::MTU: {
        auto* config = MtuConfigurationOption::Specialize(option.get());
        if (config->mtu_ < initial_config_option.minimal_remote_mtu) {
          LOG_WARN("Configuration request with unacceptable MTU");
          config->mtu_ = initial_config_option.minimal_remote_mtu;
          result = ConfigurationResponseResult::UNACCEPTABLE_PARAMETERS;
        }
        rsp_options.emplace_back(std::make_unique<MtuConfigurationOption>(*config));
        break;
      }
      case ConfigurationOptionType::FLUSH_TIMEOUT: {
        auto* config = FlushTimeoutConfigurationOption::Specialize(option.get());
        rsp_options.emplace_back(std::make_unique<FlushTimeoutConfigurationOption>(*config));
        break;
      }
      case ConfigurationOptionType::RETRANSMISSION_AND_FLOW_CONTROL: {
        auto* config = RetransmissionAndFlowControlConfigurationOption::Specialize(option.get());
        remote_rfc_mode = config->mode_;
        if (config->mode_ == RetransmissionAndFlowControlModeOption::ENHANCED_RETRANSMISSION) {
          if (config->retransmission_time_out_ == 0) {
            config->retransmission_time_out_ = 2000;
          }
          if (config->monitor_time_out_ == 0) {
            config->monitor_time_out_ = 12000;
          }
        }
        configuration_state.remote_retransmission_and_flow_control_ = *config;
        configuration_state.retransmission_and_flow_control_mode_ = config->mode_;
        rsp_options.emplace_back(std::make_unique<RetransmissionAndFlowControlConfigurationOption>(*config));
        break;
      }
      case ConfigurationOptionType::FRAME_CHECK_SEQUENCE: {
        // We determine whether to use FCS or not when we send config request
        break;
      }
      default:
        if (option->is_hint_ != ConfigurationOptionIsHint::OPTION_IS_A_HINT) {
          LOG_WARN("Received some unsupported configuration option: %d", static_cast<int>(option->type_));
          auto response =
              ConfigurationResponseBuilder::Create(signal_id.Value(), channel->GetRemoteCid(), is_continuation,
                                                   ConfigurationResponseResult::UNKNOWN_OPTIONS, {});
          enqueue_buffer_->Enqueue(std::move(response), handler_);
          return;
        }
        break;
    }
  }

  if (remote_rfc_mode == RetransmissionAndFlowControlModeOption::L2CAP_BASIC &&
      initial_config_option.channel_mode ==
          DynamicChannelConfigurationOption::RetransmissionAndFlowControlMode::ENHANCED_RETRANSMISSION) {
    LOG_WARN("ERTM mandatory not allow mode configuration, disconnect channel.");
    SendDisconnectionRequest(channel->GetCid(), channel->GetRemoteCid());
    return;
  }

  if (configuration_state.state_ == ChannelConfigurationState::State::WAIT_CONFIG_REQ) {
    std::unique_ptr<DynamicChannel> user_channel = std::make_unique<DynamicChannel>(channel, handler_);
    if (channel->local_initiated_) {
      link_->NotifyChannelCreation(cid, std::move(user_channel));
    } else {
      dynamic_service_manager_->GetService(channel->GetPsm())->NotifyChannelCreation(std::move(user_channel));
    }
    configuration_state.state_ = ChannelConfigurationState::State::CONFIGURED;
    data_pipeline_manager_->AttachChannel(cid, channel, l2cap::internal::DataPipelineManager::ChannelMode::BASIC);
    data_pipeline_manager_->UpdateClassicConfiguration(cid, configuration_state);
  } else if (configuration_state.state_ == ChannelConfigurationState::State::WAIT_CONFIG_REQ_RSP) {
    configuration_state.state_ = ChannelConfigurationState::State::WAIT_CONFIG_RSP;
  }

  auto response = ConfigurationResponseBuilder::Create(signal_id.Value(), channel->GetRemoteCid(), is_continuation,
                                                       result, std::move(rsp_options));
  enqueue_buffer_->Enqueue(std::move(response), handler_);
}

void ClassicSignallingManager::SendInitialConfigRequest(Cid local_cid) {
  auto channel = channel_allocator_->FindChannelByCid(local_cid);
  auto psm = channel->GetPsm();
  auto& configuration_state = channel_configuration_[local_cid];
  auto* service = dynamic_service_manager_->GetService(psm);
  auto initial_config = service->GetConfigOption();

  auto mtu_configuration = std::make_unique<MtuConfigurationOption>();
  mtu_configuration->mtu_ = initial_config.incoming_mtu;

  auto fcs_option = std::make_unique<FrameCheckSequenceOption>();
  fcs_option->fcs_type_ = FcsType::NO_FCS;
  configuration_state.fcs_type_ = FcsType::NO_FCS;
  if (link_->GetRemoteSupportsFcs()) {
    fcs_option->fcs_type_ = FcsType::DEFAULT;
    configuration_state.fcs_type_ = FcsType::DEFAULT;
  }

  auto retransmission_flow_control_configuration = std::make_unique<RetransmissionAndFlowControlConfigurationOption>();
  switch (initial_config.channel_mode) {
    case DynamicChannelConfigurationOption::RetransmissionAndFlowControlMode::L2CAP_BASIC:
      retransmission_flow_control_configuration->mode_ = RetransmissionAndFlowControlModeOption::L2CAP_BASIC;
      configuration_state.retransmission_and_flow_control_mode_ = RetransmissionAndFlowControlModeOption::L2CAP_BASIC;
      break;
    case DynamicChannelConfigurationOption::RetransmissionAndFlowControlMode::ENHANCED_RETRANSMISSION:
    case DynamicChannelConfigurationOption::RetransmissionAndFlowControlMode::ENHANCED_RETRANSMISSION_OPTIONAL:
      retransmission_flow_control_configuration->mode_ =
          RetransmissionAndFlowControlModeOption::ENHANCED_RETRANSMISSION;
      configuration_state.retransmission_and_flow_control_mode_ =
          RetransmissionAndFlowControlModeOption::ENHANCED_RETRANSMISSION;
      // TODO: Decide where to put initial values
      retransmission_flow_control_configuration->tx_window_size_ = 10;
      retransmission_flow_control_configuration->max_transmit_ = 20;
      retransmission_flow_control_configuration->retransmission_time_out_ = 2000;
      retransmission_flow_control_configuration->monitor_time_out_ = 12000;
      retransmission_flow_control_configuration->maximum_pdu_size_ = 1010;
      break;
  }
  configuration_state.local_retransmission_and_flow_control_ = *retransmission_flow_control_configuration;

  std::vector<std::unique_ptr<ConfigurationOption>> config;
  config.emplace_back(std::move(mtu_configuration));
  if (initial_config.channel_mode != DynamicChannelConfigurationOption::RetransmissionAndFlowControlMode::L2CAP_BASIC) {
    config.emplace_back(std::move(retransmission_flow_control_configuration));
    config.emplace_back(std::move(fcs_option));
  }
  send_configuration_request(channel->GetRemoteCid(), std::move(config));
}

void ClassicSignallingManager::negotiate_configuration(
    Cid cid, Continuation, std::vector<std::unique_ptr<ConfigurationOption>> options) {
  auto channel = channel_allocator_->FindChannelByCid(cid);
  auto& configuration_state = channel_configuration_[channel->GetCid()];
  std::vector<std::unique_ptr<ConfigurationOption>> negotiation_config;
  bool can_negotiate = false;
  for (auto& option : options) {
    switch (option->type_) {
      case ConfigurationOptionType::MTU: {
        // MTU is non-negotiable option. Use default mtu size
        auto mtu_configuration = std::make_unique<MtuConfigurationOption>();
        mtu_configuration->mtu_ = kDefaultClassicMtu;
        negotiation_config.emplace_back(std::move(mtu_configuration));
        can_negotiate = true;
        break;
      }
      case ConfigurationOptionType::FRAME_CHECK_SEQUENCE:
      case ConfigurationOptionType::FLUSH_TIMEOUT: {
        // TODO: Handle these two configuration options negotiation.
        can_negotiate = true;
        break;
      }
      case ConfigurationOptionType::RETRANSMISSION_AND_FLOW_CONTROL: {
        auto* config = RetransmissionAndFlowControlConfigurationOption::Specialize(option.get());
        if (config->mode_ == RetransmissionAndFlowControlModeOption::ENHANCED_RETRANSMISSION) {
          configuration_state.retransmission_and_flow_control_mode_ = config->mode_;
          configuration_state.local_retransmission_and_flow_control_ = *config;
          negotiation_config.emplace_back(std::make_unique<RetransmissionAndFlowControlConfigurationOption>(*config));
        } else if (config->mode_ == RetransmissionAndFlowControlModeOption::L2CAP_BASIC) {
          auto initial_config_option = dynamic_service_manager_->GetService(channel->GetPsm())->GetConfigOption();
          if (initial_config_option.channel_mode ==
              DynamicChannelConfigurationOption::RetransmissionAndFlowControlMode::ENHANCED_RETRANSMISSION) {
            // ERTM mandatory is not allow negotiating of retransmission and flow control mode, disconnect channel
            SendDisconnectionRequest(channel->GetCid(), channel->GetRemoteCid());
            return;
          } else if (initial_config_option.channel_mode ==
                     DynamicChannelConfigurationOption::RetransmissionAndFlowControlMode::
                         ENHANCED_RETRANSMISSION_OPTIONAL) {
            can_negotiate = true;
            negotiation_config.emplace_back(std::make_unique<RetransmissionAndFlowControlConfigurationOption>(*config));
          }
        } else {
          // Not support other retransmission and flow control mode, disconnect channel.
          SendDisconnectionRequest(channel->GetCid(), channel->GetRemoteCid());
          return;
        }
        break;
      }
      default:
        LOG_WARN("Received some unsupported configuration option: %d", static_cast<int>(option->type_));
        return;
    }
  }
  if (can_negotiate) {
    send_configuration_request(channel->GetRemoteCid(), std::move(negotiation_config));
  } else {
    LOG_INFO("No suggested parameter received");
  }
}

void ClassicSignallingManager::OnConfigurationResponse(SignalId signal_id, Cid cid, Continuation is_continuation,
                                                       ConfigurationResponseResult result,
                                                       std::vector<std::unique_ptr<ConfigurationOption>> options) {
  if (command_just_sent_.signal_id_ != signal_id ||
      command_just_sent_.command_code_ != CommandCode::CONFIGURATION_REQUEST) {
    LOG_WARN("Unexpected response: no pending request. Expected signal id %d type %s, got %d",
             command_just_sent_.signal_id_.Value(), CommandCodeText(command_just_sent_.command_code_).data(),
             signal_id.Value());
    return;
  }

  auto channel = channel_allocator_->FindChannelByCid(cid);
  if (channel == nullptr) {
    LOG_WARN("Configuration request for an unknown channel");
    handle_send_next_command();
    return;
  }

  switch (result) {
    default:
    case ConfigurationResponseResult::REJECTED:
    case ConfigurationResponseResult::UNKNOWN_OPTIONS:
    case ConfigurationResponseResult::FLOW_SPEC_REJECTED:
      LOG_WARN("Configuration response not SUCCESS: %s", ConfigurationResponseResultText(result).c_str());
      alarm_.Cancel();
      handle_send_next_command();
      return;

    case ConfigurationResponseResult::PENDING:
      alarm_.Schedule(common::BindOnce(&ClassicSignallingManager::on_command_timeout, common::Unretained(this)),
                      kTimeout);
      return;

    case ConfigurationResponseResult::UNACCEPTABLE_PARAMETERS:
      LOG_INFO("Configuration response with unacceptable parameters");
      alarm_.Cancel();
      negotiate_configuration(cid, is_continuation, std::move(options));
      handle_send_next_command();
      return;

    case ConfigurationResponseResult::SUCCESS:
      break;
  }
  auto& configuration_state = channel_configuration_[channel->GetCid()];

  for (auto& option : options) {
    switch (option->type_) {
      case ConfigurationOptionType::MTU: {
        // Since they accepted our MTU, no need to read the new value.
        break;
      }
      case ConfigurationOptionType::FLUSH_TIMEOUT: {
        break;
      }
      case ConfigurationOptionType::RETRANSMISSION_AND_FLOW_CONTROL: {
        auto config = RetransmissionAndFlowControlConfigurationOption::Specialize(option.get());
        if (configuration_state.retransmission_and_flow_control_mode_ != config->mode_) {
          SendDisconnectionRequest(cid, channel->GetRemoteCid());
          alarm_.Cancel();
          handle_send_next_command();
          return;
        }
        configuration_state.local_retransmission_and_flow_control_ = *config;
        break;
      }
      case ConfigurationOptionType::FRAME_CHECK_SEQUENCE: {
        configuration_state.fcs_type_ = FrameCheckSequenceOption::Specialize(option.get())->fcs_type_;
        break;
      }
      default:
        LOG_WARN("Received some unsupported configuration option: %d", static_cast<int>(option->type_));
        alarm_.Cancel();
        handle_send_next_command();
        return;
    }
  }

  if (configuration_state.state_ == ChannelConfigurationState::State::WAIT_CONFIG_RSP) {
    std::unique_ptr<DynamicChannel> user_channel = std::make_unique<DynamicChannel>(channel, handler_);
    if (channel->local_initiated_) {
      link_->NotifyChannelCreation(cid, std::move(user_channel));
    } else {
      dynamic_service_manager_->GetService(channel->GetPsm())->NotifyChannelCreation(std::move(user_channel));
    }
    configuration_state.state_ = ChannelConfigurationState::State::CONFIGURED;
    data_pipeline_manager_->AttachChannel(cid, channel, l2cap::internal::DataPipelineManager::ChannelMode::BASIC);
    data_pipeline_manager_->UpdateClassicConfiguration(cid, configuration_state);
  } else if (configuration_state.state_ == ChannelConfigurationState::State::WAIT_CONFIG_REQ_RSP) {
    configuration_state.state_ = ChannelConfigurationState::State::WAIT_CONFIG_REQ;
  }

  alarm_.Cancel();
  handle_send_next_command();
}

void ClassicSignallingManager::OnDisconnectionRequest(SignalId signal_id, Cid cid, Cid remote_cid) {
  // TODO: check cid match
  auto channel = channel_allocator_->FindChannelByCid(cid);
  if (channel == nullptr) {
    LOG_WARN("Disconnect request for an unknown channel");
    return;
  }
  auto builder = DisconnectionResponseBuilder::Create(signal_id.Value(), cid, remote_cid);
  enqueue_buffer_->Enqueue(std::move(builder), handler_);
  channel->OnClosed(hci::ErrorCode::SUCCESS);
  auto& configuration_state = channel_configuration_[channel->GetCid()];
  if (configuration_state.state_ == configuration_state.CONFIGURED) {
    data_pipeline_manager_->DetachChannel(cid);
  }
  link_->FreeDynamicChannel(cid);
  channel_configuration_.erase(cid);
}

void ClassicSignallingManager::OnDisconnectionResponse(
    SignalId signal_id, Cid /* remote_cid */, Cid cid) {
  if (command_just_sent_.signal_id_ != signal_id ||
      command_just_sent_.command_code_ != CommandCode::DISCONNECTION_REQUEST) {
    LOG_WARN("Unexpected response: no pending request. Expected signal id %d type %s, got %d",
             command_just_sent_.signal_id_.Value(), CommandCodeText(command_just_sent_.command_code_).data(),
             signal_id.Value());
    return;
  }

  alarm_.Cancel();

  auto channel = channel_allocator_->FindChannelByCid(cid);
  if (channel == nullptr) {
    LOG_WARN("Disconnect response for an unknown channel");
    handle_send_next_command();
    return;
  }

  channel->OnClosed(hci::ErrorCode::SUCCESS);
  auto& configuration_state = channel_configuration_[cid];
  if (configuration_state.state_ == configuration_state.CONFIGURED) {
    data_pipeline_manager_->DetachChannel(cid);
  }
  link_->FreeDynamicChannel(cid);
  handle_send_next_command();
  channel_configuration_.erase(cid);
}

void ClassicSignallingManager::OnEchoRequest(SignalId signal_id, const PacketView<kLittleEndian>& packet) {
  std::vector<uint8_t> packet_vector{packet.begin(), packet.end()};
  auto raw_builder = std::make_unique<packet::RawBuilder>();
  raw_builder->AddOctets(packet_vector);
  auto builder = EchoResponseBuilder::Create(signal_id.Value(), std::move(raw_builder));
  enqueue_buffer_->Enqueue(std::move(builder), handler_);
}

void ClassicSignallingManager::OnEchoResponse(
    SignalId signal_id, const PacketView<kLittleEndian>& /* packet */) {
  if (command_just_sent_.signal_id_ != signal_id || command_just_sent_.command_code_ != CommandCode::ECHO_REQUEST) {
    LOG_WARN("Unexpected response: no pending request. Expected signal id %d type %s, got %d",
             command_just_sent_.signal_id_.Value(), CommandCodeText(command_just_sent_.command_code_).data(),
             signal_id.Value());
    return;
  }
  LOG_INFO("Echo response received");
  alarm_.Cancel();
  handle_send_next_command();
}

void ClassicSignallingManager::OnInformationRequest(SignalId signal_id, InformationRequestInfoType type) {
  switch (type) {
    case InformationRequestInfoType::CONNECTIONLESS_MTU: {
      auto response = InformationResponseConnectionlessMtuBuilder::Create(
          signal_id.Value(), InformationRequestResult::SUCCESS, kDefaultClassicMtu);
      enqueue_buffer_->Enqueue(std::move(response), handler_);
      break;
    }
    case InformationRequestInfoType::EXTENDED_FEATURES_SUPPORTED: {
      auto response = InformationResponseExtendedFeaturesBuilder::Create(
          signal_id.Value(), InformationRequestResult::SUCCESS, 0, 0, 0, 1 /* ERTM */, 0 /* Streaming mode */,
          1 /* FCS */, 0, 1 /* Fixed Channels */, 0, 0, 0 /* COC */);
      enqueue_buffer_->Enqueue(std::move(response), handler_);
      break;
    }
    case InformationRequestInfoType::FIXED_CHANNELS_SUPPORTED: {
      auto response = InformationResponseFixedChannelsBuilder::Create(
          signal_id.Value(), InformationRequestResult::SUCCESS, fixed_service_manager_->GetSupportedFixedChannelMask());
      enqueue_buffer_->Enqueue(std::move(response), handler_);
      break;
    }
  }
}

void ClassicSignallingManager::OnInformationResponse(SignalId signal_id, const InformationResponseView& response) {
  if (command_just_sent_.signal_id_ != signal_id ||
      command_just_sent_.command_code_ != CommandCode::INFORMATION_REQUEST) {
    LOG_WARN("Unexpected response: no pending request. Expected signal id %d type %s, got %d",
             command_just_sent_.signal_id_.Value(), CommandCodeText(command_just_sent_.command_code_).data(),
             signal_id.Value());
    return;
  }

  auto type = response.GetInfoType();
  switch (type) {
    case InformationRequestInfoType::CONNECTIONLESS_MTU: {
      auto view = InformationResponseConnectionlessMtuView::Create(response);
      if (!view.IsValid()) {
        LOG_WARN("Invalid InformationResponseConnectionlessMtu received");
        return;
      }
      link_->SetRemoteConnectionlessMtu(view.GetConnectionlessMtu());
      break;
    }
    case InformationRequestInfoType::EXTENDED_FEATURES_SUPPORTED: {
      auto view = InformationResponseExtendedFeaturesView::Create(response);
      if (!view.IsValid()) {
        LOG_WARN("Invalid InformationResponseExtendedFeatures received");
        return;
      }
      link_->OnRemoteExtendedFeatureReceived(view.GetEnhancedRetransmissionMode(), view.GetFcsOption());
      // We don't care about other parameters
      break;
    }
    case InformationRequestInfoType::FIXED_CHANNELS_SUPPORTED: {
      auto view = InformationResponseFixedChannelsView::Create(response);
      if (!view.IsValid()) {
        LOG_WARN("Invalid InformationResponseFixedChannel received");
        return;
      }
      // We don't use fixed channels (connectionless or BR/EDR security) for now so we don't care
      break;
    }
  }

  alarm_.Cancel();
  handle_send_next_command();
}

void ClassicSignallingManager::on_incoming_packet() {
  auto packet = signalling_channel_->GetQueueUpEnd()->TryDequeue();
  auto command_list = GetCommandsFromPacketView(*packet);
  for (auto& command : command_list) {
    handle_one_command(command);
  }
}

void ClassicSignallingManager::handle_one_command(ControlView control_packet_view) {
  if (!control_packet_view.IsValid()) {
    LOG_WARN("Invalid signalling packet received");
    return;
  }
  auto code = control_packet_view.GetCode();
  switch (code) {
    case CommandCode::COMMAND_REJECT: {
      CommandRejectView command_reject_view = CommandRejectView::Create(control_packet_view);
      if (!command_reject_view.IsValid()) {
        return;
      }
      OnCommandReject(command_reject_view);
      return;
    }
    case CommandCode::CONNECTION_REQUEST: {
      ConnectionRequestView connection_request_view = ConnectionRequestView::Create(control_packet_view);
      if (!connection_request_view.IsValid()) {
        return;
      }
      OnConnectionRequest(control_packet_view.GetIdentifier(), connection_request_view.GetPsm(),
                          connection_request_view.GetSourceCid());
      return;
    }
    case CommandCode::CONNECTION_RESPONSE: {
      ConnectionResponseView connection_response_view = ConnectionResponseView::Create(control_packet_view);
      if (!connection_response_view.IsValid()) {
        return;
      }
      OnConnectionResponse(connection_response_view.GetIdentifier(), connection_response_view.GetDestinationCid(),
                           connection_response_view.GetSourceCid(), connection_response_view.GetResult(),
                           connection_response_view.GetStatus());
      return;
    }
    case CommandCode::CONFIGURATION_REQUEST: {
      ConfigurationRequestView configuration_request_view = ConfigurationRequestView::Create(control_packet_view);
      if (!configuration_request_view.IsValid()) {
        return;
      }
      OnConfigurationRequest(configuration_request_view.GetIdentifier(), configuration_request_view.GetDestinationCid(),
                             configuration_request_view.GetContinuation(), configuration_request_view.GetConfig());
      return;
    }
    case CommandCode::CONFIGURATION_RESPONSE: {
      ConfigurationResponseView configuration_response_view = ConfigurationResponseView::Create(control_packet_view);
      if (!configuration_response_view.IsValid()) {
        return;
      }
      OnConfigurationResponse(configuration_response_view.GetIdentifier(), configuration_response_view.GetSourceCid(),
                              configuration_response_view.GetContinuation(), configuration_response_view.GetResult(),
                              configuration_response_view.GetConfig());
      return;
    }
    case CommandCode::DISCONNECTION_REQUEST: {
      DisconnectionRequestView disconnection_request_view = DisconnectionRequestView::Create(control_packet_view);
      if (!disconnection_request_view.IsValid()) {
        return;
      }
      OnDisconnectionRequest(disconnection_request_view.GetIdentifier(), disconnection_request_view.GetDestinationCid(),
                             disconnection_request_view.GetSourceCid());
      return;
    }
    case CommandCode::DISCONNECTION_RESPONSE: {
      DisconnectionResponseView disconnection_response_view = DisconnectionResponseView::Create(control_packet_view);
      if (!disconnection_response_view.IsValid()) {
        return;
      }
      OnDisconnectionResponse(disconnection_response_view.GetIdentifier(),
                              disconnection_response_view.GetDestinationCid(),
                              disconnection_response_view.GetSourceCid());
      return;
    }
    case CommandCode::ECHO_REQUEST: {
      EchoRequestView echo_request_view = EchoRequestView::Create(control_packet_view);
      if (!echo_request_view.IsValid()) {
        return;
      }
      OnEchoRequest(echo_request_view.GetIdentifier(), echo_request_view.GetPayload());
      return;
    }
    case CommandCode::ECHO_RESPONSE: {
      EchoResponseView echo_response_view = EchoResponseView::Create(control_packet_view);
      if (!echo_response_view.IsValid()) {
        return;
      }
      OnEchoResponse(echo_response_view.GetIdentifier(), echo_response_view.GetPayload());
      return;
    }
    case CommandCode::INFORMATION_REQUEST: {
      InformationRequestView information_request_view = InformationRequestView::Create(control_packet_view);
      if (!information_request_view.IsValid()) {
        return;
      }
      OnInformationRequest(information_request_view.GetIdentifier(), information_request_view.GetInfoType());
      return;
    }
    case CommandCode::INFORMATION_RESPONSE: {
      InformationResponseView information_response_view = InformationResponseView::Create(control_packet_view);
      if (!information_response_view.IsValid()) {
        return;
      }
      OnInformationResponse(information_response_view.GetIdentifier(), information_response_view);
      return;
    }
    case CommandCode::CREDIT_BASED_CONNECTION_REQUEST: {
      CreditBasedConnectionRequestView request_view = CreditBasedConnectionRequestView::Create(control_packet_view);
      if (!request_view.IsValid()) {
        return;
      }
      return;
    }
    case CommandCode::CREDIT_BASED_CONNECTION_RESPONSE: {
      CreditBasedConnectionResponseView response_view = CreditBasedConnectionResponseView::Create(control_packet_view);
      if (!response_view.IsValid()) {
        return;
      }
      return;
    }
    case CommandCode::CREDIT_BASED_RECONFIGURE_REQUEST: {
      CreditBasedReconfigureRequestView request_view = CreditBasedReconfigureRequestView::Create(control_packet_view);
      if (!request_view.IsValid()) {
        return;
      }
      return;
    }
    case CommandCode::CREDIT_BASED_RECONFIGURE_RESPONSE: {
      CreditBasedReconfigureResponseView response_view =
          CreditBasedReconfigureResponseView::Create(control_packet_view);
      if (!response_view.IsValid()) {
        return;
      }
      return;
    }
    case CommandCode::FLOW_CONTROL_CREDIT: {
      FlowControlCreditView credit_view = FlowControlCreditView::Create(control_packet_view);
      if (!credit_view.IsValid()) {
        return;
      }
      return;
    }
    default:
      LOG_WARN("Unhandled event 0x%x", static_cast<int>(code));
      auto builder = CommandRejectNotUnderstoodBuilder::Create(control_packet_view.GetIdentifier());
      enqueue_buffer_->Enqueue(std::move(builder), handler_);
      return;
  }
}

void ClassicSignallingManager::send_connection_response(SignalId signal_id, Cid remote_cid, Cid local_cid,
                                                        ConnectionResponseResult result,
                                                        ConnectionResponseStatus status) {
  auto builder = ConnectionResponseBuilder::Create(signal_id.Value(), local_cid, remote_cid, result, status);
  enqueue_buffer_->Enqueue(std::move(builder), handler_);
}

void ClassicSignallingManager::on_command_timeout() {
  LOG_WARN("Response time out");
  if (command_just_sent_.signal_id_ == kInvalidSignalId) {
    LOG_ERROR("No pending command");
    return;
  }
  LOG_WARN("Response time out for %s", CommandCodeText(command_just_sent_.command_code_).c_str());
  switch (command_just_sent_.command_code_) {
    case CommandCode::CONNECTION_REQUEST: {
      DynamicChannelManager::ConnectionResult connection_result{
          .connection_result_code = DynamicChannelManager::ConnectionResultCode::FAIL_L2CAP_ERROR,
          .hci_error = hci::ErrorCode::SUCCESS,
          .l2cap_connection_response_result = ConnectionResponseResult::NO_RESOURCES_AVAILABLE,
      };
      link_->OnOutgoingConnectionRequestFail(command_just_sent_.source_cid_, connection_result);
      break;
    }
    case CommandCode::CONFIGURATION_REQUEST: {
      auto channel = channel_allocator_->FindChannelByRemoteCid(command_just_sent_.destination_cid_);
      SendDisconnectionRequest(channel->GetCid(), channel->GetRemoteCid());
      return;
    }
    case CommandCode::INFORMATION_REQUEST: {
      if (command_just_sent_.info_type_ == InformationRequestInfoType::EXTENDED_FEATURES_SUPPORTED) {
        link_->OnRemoteExtendedFeatureReceived(false, false);
      }
      break;
    }
    default:
      break;
  }
  handle_send_next_command();
}

void ClassicSignallingManager::handle_send_next_command() {
  command_just_sent_.signal_id_ = kInvalidSignalId;
  if (pending_commands_.empty()) {
    return;
  }
  command_just_sent_ = std::move(pending_commands_.front());
  pending_commands_.pop();

  auto signal_id = command_just_sent_.signal_id_;
  auto psm = command_just_sent_.psm_;
  auto source_cid = command_just_sent_.source_cid_;
  auto destination_cid = command_just_sent_.destination_cid_;
  auto info_type = command_just_sent_.info_type_;
  auto config = std::move(command_just_sent_.config_);
  switch (command_just_sent_.command_code_) {
    case CommandCode::CONNECTION_REQUEST: {
      auto builder = ConnectionRequestBuilder::Create(signal_id.Value(), psm, source_cid);
      enqueue_buffer_->Enqueue(std::move(builder), handler_);
      alarm_.Schedule(common::BindOnce(&ClassicSignallingManager::on_command_timeout, common::Unretained(this)),
                      kTimeout);
      break;
    }
    case CommandCode::CONFIGURATION_REQUEST: {
      auto builder =
          ConfigurationRequestBuilder::Create(signal_id.Value(), destination_cid, Continuation::END, std::move(config));
      enqueue_buffer_->Enqueue(std::move(builder), handler_);
      alarm_.Schedule(common::BindOnce(&ClassicSignallingManager::on_command_timeout, common::Unretained(this)),
                      kTimeout);
      break;
    }
    case CommandCode::DISCONNECTION_REQUEST: {
      auto builder = DisconnectionRequestBuilder::Create(signal_id.Value(), destination_cid, source_cid);
      enqueue_buffer_->Enqueue(std::move(builder), handler_);
      alarm_.Schedule(common::BindOnce(&ClassicSignallingManager::on_command_timeout, common::Unretained(this)),
                      kTimeout);
      break;
    }
    case CommandCode::INFORMATION_REQUEST: {
      auto builder = InformationRequestBuilder::Create(signal_id.Value(), info_type);
      enqueue_buffer_->Enqueue(std::move(builder), handler_);
      alarm_.Schedule(common::BindOnce(&ClassicSignallingManager::on_command_timeout, common::Unretained(this)),
                      kTimeout);
      break;
    }
    default:
      LOG_WARN("Unsupported command code 0x%x", static_cast<int>(command_just_sent_.command_code_));
  }
}

}  // namespace internal
}  // namespace classic
}  // namespace l2cap
}  // namespace bluetooth
