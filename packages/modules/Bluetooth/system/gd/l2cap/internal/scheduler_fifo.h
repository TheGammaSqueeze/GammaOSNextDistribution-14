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

#include <atomic>
#include <string>
#include <unordered_map>
#include <unordered_set>

#include "common/bidi_queue.h"
#include "common/bind.h"
#include "common/multi_priority_queue.h"
#include "l2cap/cid.h"
#include "l2cap/internal/channel_impl.h"
#include "l2cap/internal/scheduler.h"
#include "l2cap/internal/sender.h"
#include "os/handler.h"
#include "os/queue.h"

namespace bluetooth {
namespace l2cap {
namespace internal {
class DataPipelineManager;

class Fifo : public Scheduler {
 public:
  Fifo(DataPipelineManager* data_pipeline_manager, LowerQueueUpEnd* link_queue_up_end, os::Handler* handler);
  ~Fifo();
  void OnPacketsReady(Cid cid, int number_packets) override;
  void SetChannelTxPriority(Cid cid, bool high_priority) override;
  void RemoveChannel(Cid cid) override;

 private:
  DataPipelineManager* data_pipeline_manager_;
  LowerQueueUpEnd* link_queue_up_end_;
  os::Handler* handler_;
  using ChannelAndNumPackets = std::pair<Cid, int>;
  common::MultiPriorityQueue<ChannelAndNumPackets, 2> next_to_dequeue_and_num_packets;
  std::unordered_set<Cid> high_priority_cids_;
  std::atomic_bool link_queue_enqueue_registered_ = false;

  void try_register_link_queue_enqueue();
  std::unique_ptr<LowerEnqueue> link_queue_enqueue_callback();
};

}  // namespace internal
}  // namespace l2cap
}  // namespace bluetooth
