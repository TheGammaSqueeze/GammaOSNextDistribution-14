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

#include "l2cap/internal/scheduler_fifo.h"

#include "dynamic_channel_impl.h"
#include "l2cap/internal/data_pipeline_manager.h"
#include "l2cap/l2cap_packets.h"
#include "os/log.h"

namespace bluetooth {
namespace l2cap {
namespace internal {

Fifo::Fifo(DataPipelineManager* data_pipeline_manager, LowerQueueUpEnd* link_queue_up_end, os::Handler* handler)
    : data_pipeline_manager_(data_pipeline_manager), link_queue_up_end_(link_queue_up_end), handler_(handler) {
  ASSERT(link_queue_up_end_ != nullptr && handler_ != nullptr);
}

// Invoked from some external Handler context
Fifo::~Fifo() {
  // TODO(hsz): notify Sender don't send callback to me
  if (link_queue_enqueue_registered_.exchange(false)) {
    link_queue_up_end_->UnregisterEnqueue();
  }
}

// Invoked within L2CAP Handler context
void Fifo::OnPacketsReady(Cid cid, int number_packets) {
  if (number_packets == 0) {
    return;
  }
  int priority = high_priority_cids_.count(cid) != 0;
  next_to_dequeue_and_num_packets.push(std::make_pair(cid, number_packets), priority);
  try_register_link_queue_enqueue();
}

// Invoked within L2CAP Handler context
void Fifo::SetChannelTxPriority(Cid cid, bool high_priority) {
  if (high_priority) {
    high_priority_cids_.emplace(cid);
  } else {
    high_priority_cids_.erase(cid);
  }
}

void Fifo::RemoveChannel(Cid cid) {
  for (size_t i = 0; i < next_to_dequeue_and_num_packets.size(); i++) {
    auto& channel_id_and_number_packets = next_to_dequeue_and_num_packets.front();
    if (channel_id_and_number_packets.second != cid) {
      next_to_dequeue_and_num_packets.push(channel_id_and_number_packets);
    }
    next_to_dequeue_and_num_packets.pop();
  }
  if (next_to_dequeue_and_num_packets.empty() && link_queue_enqueue_registered_.exchange(false)) {
    link_queue_up_end_->UnregisterEnqueue();
  }
}

// Invoked from some external Queue Reactable context
std::unique_ptr<Fifo::UpperDequeue> Fifo::link_queue_enqueue_callback() {
  ASSERT(!next_to_dequeue_and_num_packets.empty());
  auto& channel_id_and_number_packets = next_to_dequeue_and_num_packets.front();
  auto channel_id = channel_id_and_number_packets.first;
  channel_id_and_number_packets.second--;
  if (channel_id_and_number_packets.second == 0) {
    next_to_dequeue_and_num_packets.pop();
  }
  auto packet = data_pipeline_manager_->GetDataController(channel_id)->GetNextPacket();

  data_pipeline_manager_->OnPacketSent(channel_id);
  if (next_to_dequeue_and_num_packets.empty() && link_queue_enqueue_registered_.exchange(false)) {
    link_queue_up_end_->UnregisterEnqueue();
  }
  return packet;
}

void Fifo::try_register_link_queue_enqueue() {
  if (link_queue_enqueue_registered_.exchange(true)) {
    return;
  }
  link_queue_up_end_->RegisterEnqueue(handler_,
                                      common::Bind(&Fifo::link_queue_enqueue_callback, common::Unretained(this)));
}

}  // namespace internal
}  // namespace l2cap
}  // namespace bluetooth
