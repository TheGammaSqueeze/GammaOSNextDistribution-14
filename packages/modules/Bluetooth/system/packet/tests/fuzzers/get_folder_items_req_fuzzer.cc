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

#include "avrcp_test_packets.h"
#include "get_folder_items.h"
#include "packet_test_helper.h"

namespace bluetooth {
namespace avrcp {

using TestGetFolderItemsReqPacket = TestPacketType<GetFolderItemsRequest>;

extern "C" int LLVMFuzzerTestOneInput(const char* data, size_t size) {
  std::vector<uint8_t> get_folder_items_request_vfs;

  // Starts large to avoid throwing exceptions
  if (size >= 34) {
    for (size_t x = 0; x < size; x++) {
      get_folder_items_request_vfs.push_back(data[x]);
    }

    auto test_packet =
        TestGetFolderItemsReqPacket::Make(get_folder_items_request_vfs);

    test_packet->GetScope();
    test_packet->GetStartItem();
    test_packet->GetEndItem();
    test_packet->GetNumAttributes();
    test_packet->IsValid();
    test_packet->ToString();
  }

  return 0;
}

}  // namespace avrcp
}  // namespace bluetooth
