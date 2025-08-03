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

// Adapted from get_folder_items_packet_test

#include <fuzzer/FuzzedDataProvider.h>

#include "avrcp_test_packets.h"
#include "get_folder_items.h"
#include "packet_test_helper.h"

namespace bluetooth {
namespace avrcp {

using TestGetFolderItemsReqPacket = TestPacketType<GetFolderItemsRequest>;

extern "C" int LLVMFuzzerTestOneInput(uint8_t* data, size_t size) {
  auto builder = GetFolderItemsResponseBuilder::MakeNowPlayingBuilder(
      Status::NO_ERROR, 0x0000, 0xFFFF);
  std::set<AttributeEntry> attributes;
  FuzzedDataProvider data_provider(data, size);
  std::string s = data_provider.ConsumeRemainingBytesAsString();
  attributes.insert(AttributeEntry(Attribute::TITLE, s));
  auto song = MediaElementItem(0x02, s, attributes);
  builder->AddSong(song);

  auto test_packet = TestGetFolderItemsReqPacket::Make();
  builder->Serialize(test_packet);
  test_packet->GetData();

  // Second test with the same data.
  auto builder2 = GetFolderItemsResponseBuilder::MakeVFSBuilder(
      Status::NO_ERROR, 0x0000, 0xFFFF);
  auto folder = FolderItem(0x0000000000000001, 0x00, true, s);
  builder2->AddFolder(folder);

  test_packet = TestGetFolderItemsReqPacket::Make();
  builder2->Serialize(test_packet);
  test_packet->GetData();

  // Third test with the same data.
  MediaPlayerItem player1(1, s, true);

  // Browsing Header + Status field + UID Counter field + Number of Items
  // field
  auto packet_size = BrowsePacket::kMinSize() + 5;
  packet_size += player1.size();

  auto builder3 = GetFolderItemsResponseBuilder::MakePlayerListBuilder(
      Status::NO_ERROR, 0x0000, packet_size);

  builder3->AddMediaPlayer(player1);

  return 0;
}

}  // namespace avrcp
}  // namespace bluetooth
