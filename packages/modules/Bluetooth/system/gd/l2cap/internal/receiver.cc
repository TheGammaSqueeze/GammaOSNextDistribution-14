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

#include "l2cap/internal/receiver.h"

#include "common/bidi_queue.h"
#include "l2cap/cid.h"
#include "l2cap/internal/data_pipeline_manager.h"
#include "l2cap/l2cap_packets.h"
#include "packet/packet_view.h"

namespace bluetooth {
namespace l2cap {
namespace internal {
Receiver::Receiver(
    LowerQueueUpEnd* link_queue_up_end, os::Handler* handler, DataPipelineManager* data_pipeline_manager_)
    : link_queue_up_end_(link_queue_up_end),
      handler_(handler),
      buffer_timer_(handler),
      data_pipeline_manager_(data_pipeline_manager_) {
  ASSERT(link_queue_up_end_ != nullptr && handler_ != nullptr);
  link_queue_up_end_->RegisterDequeue(handler_,
                                      common::Bind(&Receiver::link_queue_dequeue_callback, common::Unretained(this)));
}

// Invoked from external handler/thread (ModuleRegistry)
Receiver::~Receiver() {
  link_queue_up_end_->UnregisterDequeue();
}

// Invoked from external (Queue Reactable)
void Receiver::link_queue_dequeue_callback() {
  auto packet = link_queue_up_end_->TryDequeue();
  auto basic_frame_view = BasicFrameView::Create(*packet);
  if (!basic_frame_view.IsValid()) {
    LOG_WARN("Received an invalid basic frame");
    return;
  }
  Cid cid = static_cast<Cid>(basic_frame_view.GetChannelId());
  auto* data_controller = data_pipeline_manager_->GetDataController(cid);
  if (data_controller == nullptr) {
    // TODO(b/150170271): Buffer a few packets before data controller is attached
    LOG_WARN("Received a packet without data controller. cid: %d", cid);
    buffered_packets_.emplace(*packet);
    LOG_WARN("Enqueued the unexpected packet. Current queue size: %zu", buffered_packets_.size());
    buffer_timer_.Schedule(
        common::BindOnce(&Receiver::check_buffered_packets, common::Unretained(this)), std::chrono::milliseconds(500));

    return;
  }
  data_controller->OnPdu(*packet);
}

void Receiver::check_buffered_packets() {
  while (!buffered_packets_.empty()) {
    auto packet = buffered_packets_.front();
    buffered_packets_.pop();
    auto basic_frame_view = BasicFrameView::Create(packet);
    if (!basic_frame_view.IsValid()) {
      LOG_WARN("Received an invalid basic frame");
      return;
    }
    Cid cid = static_cast<Cid>(basic_frame_view.GetChannelId());
    auto* data_controller = data_pipeline_manager_->GetDataController(cid);
    if (data_controller == nullptr) {
      LOG_ERROR("Dropping a packet with invalid cid: %d", cid);
    } else {
      data_controller->OnPdu(packet);
    }
  }
}

}  // namespace internal
}  // namespace l2cap
}  // namespace bluetooth
