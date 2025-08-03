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

#pragma once

#include <cstdint>
#include <queue>
#include <vector>

#include "l2cap/cid.h"
#include "l2cap/internal/data_pipeline_manager.h"
#include "l2cap/internal/dynamic_channel_allocator.h"
#include "l2cap/l2cap_packets.h"
#include "l2cap/le/internal/dynamic_channel_service_manager_impl.h"
#include "l2cap/le/internal/fixed_channel_impl.h"
#include "l2cap/le/internal/fixed_channel_service_manager_impl.h"
#include "l2cap/mtu.h"
#include "l2cap/psm.h"
#include "l2cap/signal_id.h"
#include "os/alarm.h"
#include "os/handler.h"
#include "os/queue.h"
#include "packet/raw_builder.h"

namespace bluetooth {
namespace l2cap {
namespace le {
namespace internal {

struct PendingCommand {
  SignalId signal_id_ = kInvalidSignalId;
  LeCommandCode command_code_;
  Psm psm_;
  Cid source_cid_;
  Cid destination_cid_;
  Mtu mtu_;
  uint16_t mps_;
  uint16_t credits_;
  uint16_t interval_min_;
  uint16_t interval_max_;
  uint16_t peripheral_latency_;
  uint16_t timeout_multiplier_;

  static PendingCommand CreditBasedConnectionRequest(SignalId signal_id, Psm psm, Cid scid, Mtu mtu, uint16_t mps,
                                                     uint16_t initial_credits) {
    PendingCommand pending_command;
    pending_command.signal_id_ = signal_id;
    pending_command.command_code_ = LeCommandCode::LE_CREDIT_BASED_CONNECTION_REQUEST;
    pending_command.psm_ = psm;
    pending_command.source_cid_ = scid;
    pending_command.mtu_ = mtu;
    pending_command.mps_ = mps;
    pending_command.credits_ = initial_credits;
    return pending_command;
  }

  static PendingCommand DisconnectionRequest(SignalId signal_id, Cid scid, Cid dcid) {
    PendingCommand pending_command;
    pending_command.signal_id_ = signal_id;
    pending_command.command_code_ = LeCommandCode::DISCONNECTION_REQUEST;
    pending_command.source_cid_ = scid;
    pending_command.destination_cid_ = dcid;
    return pending_command;
  }

  static PendingCommand ConnectionParameterUpdate(
      SignalId signal_id,
      uint16_t interval_min,
      uint16_t interval_max,
      uint16_t peripheral_latency,
      uint16_t timeout_multiplier) {
    PendingCommand pending_command;
    pending_command.signal_id_ = signal_id;
    pending_command.command_code_ = LeCommandCode::CONNECTION_PARAMETER_UPDATE_REQUEST;
    pending_command.interval_min_ = interval_min;
    pending_command.interval_max_ = interval_max;
    pending_command.peripheral_latency_ = peripheral_latency;
    pending_command.timeout_multiplier_ = timeout_multiplier;
    return pending_command;
  }
};

class Link;

class LeSignallingManager {
 public:
  LeSignallingManager(os::Handler* handler, Link* link, l2cap::internal::DataPipelineManager* data_pipeline_manager,
                      DynamicChannelServiceManagerImpl* dynamic_service_manager,
                      l2cap::internal::DynamicChannelAllocator* channel_allocator);

  virtual ~LeSignallingManager();

  void SendConnectionRequest(Psm psm, Cid local_cid, Mtu mtu);

  void SendDisconnectRequest(Cid local_cid, Cid remote_cid);

  // Note: Since Core 4.1, LL peripheral can send this through HCI command.
  void SendConnectionParameterUpdateRequest(
      uint16_t interval_min, uint16_t interval_max, uint16_t peripheral_latency, uint16_t timeout_multiplier);

  void SendConnectionParameterUpdateResponse(SignalId signal_id, ConnectionParameterUpdateResponseResult result);

  void SendCredit(Cid local_cid, uint16_t credits);

  void SendEnhancedConnectionRequest(Psm psm, std::vector<Cid> local_cid, Mtu mtu);

  void SendEnhancedReconfigureRequest(std::vector<Cid> local_cid, Mtu mtu);

  void CancelAlarm();

  void OnCommandReject(LeCommandRejectView command_reject_view);

  void OnConnectionParameterUpdateRequest(
      SignalId signal_id,
      uint16_t interval_min,
      uint16_t interval_max,
      uint16_t peripheral_latency,
      uint16_t timeout_multiplier);
  void OnConnectionParameterUpdateResponse(SignalId signal_id, ConnectionParameterUpdateResponseResult result);

  void OnConnectionRequest(SignalId signal_id, Psm psm, Cid remote_cid, Mtu mtu, uint16_t mps,
                           uint16_t initial_credits);

  void OnConnectionResponse(SignalId signal_id, Cid remote_cid, Mtu mtu, uint16_t mps, uint16_t initial_credits,
                            LeCreditBasedConnectionResponseResult result);

  void OnDisconnectionRequest(SignalId signal_id, Cid cid, Cid remote_cid);

  void OnDisconnectionResponse(SignalId signal_id, Cid cid, Cid remote_cid);

  void OnCredit(Cid remote_cid, uint16_t credits);

 private:
  struct PendingConnection {
    Cid remote_cid;
    Mtu mtu;
    uint16_t max_pdu_size;
    uint16_t initial_credits;
    SignalId incoming_signal_id;
  };

  void on_incoming_packet();
  void send_connection_response(SignalId signal_id, Cid local_cid, Mtu mtu, uint16_t mps, uint16_t initial_credit,
                                LeCreditBasedConnectionResponseResult result);
  void on_command_timeout();
  void handle_send_next_command();
  void on_security_result_for_incoming(Psm psm, PendingConnection request, bool result);
  void on_security_result_for_outgoing(Psm psm, Cid local_cid, Mtu mtu, bool result);

  os::Handler* handler_;
  Link* link_;
  l2cap::internal::DataPipelineManager* data_pipeline_manager_;
  std::shared_ptr<le::internal::FixedChannelImpl> signalling_channel_;
  DynamicChannelServiceManagerImpl* dynamic_service_manager_;
  l2cap::internal::DynamicChannelAllocator* channel_allocator_;
  std::unique_ptr<os::EnqueueBuffer<packet::BasePacketBuilder>> enqueue_buffer_;
  std::queue<PendingCommand> pending_commands_;
  PendingCommand command_just_sent_;
  os::Alarm alarm_;
  SignalId next_signal_id_ = kInitialSignalId;
};

}  // namespace internal
}  // namespace le
}  // namespace l2cap
}  // namespace bluetooth
