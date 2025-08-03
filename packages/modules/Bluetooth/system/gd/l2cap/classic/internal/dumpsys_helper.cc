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

#include <string>

#include "l2cap/classic/internal/dumpsys_helper.h"
#include "l2cap/classic/internal/fixed_channel_impl.h"
#include "l2cap/classic/internal/link.h"
#include "l2cap/classic/internal/link_manager.h"
#include "l2cap/internal/dynamic_channel_impl.h"
#include "l2cap_classic_module_generated.h"
#include "os/log.h"

bluetooth::l2cap::classic::internal::DumpsysHelper::DumpsysHelper(const LinkManager& link_manager)
    : link_manager_(link_manager) {}

std::vector<flatbuffers::Offset<bluetooth::l2cap::classic::ChannelData>>
bluetooth::l2cap::classic::internal::DumpsysHelper::DumpActiveDynamicChannels(
    flatbuffers::FlatBufferBuilder* fb_builder,
    const l2cap::internal::DynamicChannelAllocator& channel_allocator) const {
  std::vector<flatbuffers::Offset<bluetooth::l2cap::classic::ChannelData>> channel_offsets;

  for (auto it = channel_allocator.channels_.cbegin(); it != channel_allocator.channels_.cend(); ++it) {
    ChannelDataBuilder builder(*fb_builder);
    builder.add_cid(it->first);
    channel_offsets.push_back(builder.Finish());
  }
  return channel_offsets;
}

std::vector<flatbuffers::Offset<::bluetooth::l2cap::classic::ChannelData>>
bluetooth::l2cap::classic::internal::DumpsysHelper::DumpActiveFixedChannels(
    flatbuffers::FlatBufferBuilder* fb_builder,
    const bluetooth::l2cap::internal::FixedChannelAllocator<
        bluetooth::l2cap::classic::internal::FixedChannelImpl,
        bluetooth::l2cap::classic::internal::Link>& channel_allocator) const {
  std::vector<flatbuffers::Offset<bluetooth::l2cap::classic::ChannelData>> channel_offsets;

  for (auto it = channel_allocator.channels_.cbegin(); it != channel_allocator.channels_.cend(); ++it) {
    ChannelDataBuilder builder(*fb_builder);
    builder.add_cid(it->first);
    channel_offsets.push_back(builder.Finish());
  }
  return channel_offsets;
}

std::vector<flatbuffers::Offset<bluetooth::l2cap::classic::LinkData>>
bluetooth::l2cap::classic::internal::DumpsysHelper::DumpActiveLinks(flatbuffers::FlatBufferBuilder* fb_builder) const {
  const std::unordered_map<hci::Address, Link>* links = &link_manager_.links_;

  std::vector<flatbuffers::Offset<LinkData>> link_offsets;

  for (auto it = links->cbegin(); it != links->cend(); ++it) {
    auto link_address = fb_builder->CreateString(it->second.ToString());
    auto dynamic_channel_offsets = DumpActiveDynamicChannels(fb_builder, it->second.dynamic_channel_allocator_);
    auto dynamic_channels = fb_builder->CreateVector(dynamic_channel_offsets);

    auto fixed_channel_offsets = DumpActiveFixedChannels(fb_builder, it->second.fixed_channel_allocator_);
    auto fixed_channels = fb_builder->CreateVector(fixed_channel_offsets);

    LinkDataBuilder builder(*fb_builder);
    builder.add_address(link_address);
    builder.add_dynamic_channels(dynamic_channels);
    builder.add_fixed_channels(fixed_channels);
    link_offsets.push_back(builder.Finish());
  }
  return link_offsets;
}
