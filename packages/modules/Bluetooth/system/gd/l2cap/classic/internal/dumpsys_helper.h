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

#pragma once

#include "l2cap/classic/internal/fixed_channel_impl.h"
#include "l2cap/classic/internal/link.h"
#include "l2cap/classic/internal/link_manager.h"
#include "l2cap/internal/dynamic_channel_allocator.h"
#include "l2cap/internal/fixed_channel_allocator.h"
#include "l2cap_classic_module_generated.h"

namespace bluetooth {
namespace l2cap {
namespace classic {
namespace internal {

class DumpsysHelper {
 public:
  DumpsysHelper(const LinkManager& link_manager);

  std::vector<flatbuffers::Offset<ChannelData>> DumpActiveDynamicChannels(
      flatbuffers::FlatBufferBuilder* fb_builder,
      const l2cap::internal::DynamicChannelAllocator& channel_allocator) const;
  std::vector<flatbuffers::Offset<ChannelData>> DumpActiveFixedChannels(
      flatbuffers::FlatBufferBuilder* fb_builder,
      const l2cap::internal::FixedChannelAllocator<FixedChannelImpl, Link>& channel_allocator) const;
  std::vector<flatbuffers::Offset<LinkData>> DumpActiveLinks(flatbuffers::FlatBufferBuilder* fb_builder) const;

 private:
  const LinkManager& link_manager_;
};

}  // namespace internal
}  // namespace classic
}  // namespace l2cap
}  // namespace bluetooth
