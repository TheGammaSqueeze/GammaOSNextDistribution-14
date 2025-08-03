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
// Authors: corbin.souffrant@leviathansecurity.com
//          dylan.katz@leviathansecurity.com

#pragma once

#include <vector>

#include "fuzz/helpers.h"
#include "l2cap/classic/internal/fixed_channel_impl.h"
#include "l2cap/internal/dynamic_channel_impl.h"
#include "l2cap/l2cap_packets.h"
#include "os/fuzz/fuzz_inject_queue.h"
#include "os/handler.h"
#include "packet/packet_view.h"

namespace bluetooth {

typedef os::IQueueEnqueue<packet::PacketView<packet::kLittleEndian>> EnqueueType;
typedef os::fuzz::FuzzInjectQueue<packet::PacketView<packet::kLittleEndian>> ChannelFuzzQueueType;

class ChannelFuzzController {
 public:
  ChannelFuzzController(os::Handler* l2cap_handler, std::shared_ptr<l2cap::internal::DynamicChannelImpl> chan);

  ChannelFuzzController(os::Handler* l2cap_handler, std::shared_ptr<l2cap::classic::internal::FixedChannelImpl> chan);

  void injectPacketData(std::vector<uint8_t> data);

  void injectFrame(std::vector<uint8_t> data);

 private:
  std::shared_ptr<ChannelFuzzQueueType> channelInject_;
};
}  // namespace bluetooth
