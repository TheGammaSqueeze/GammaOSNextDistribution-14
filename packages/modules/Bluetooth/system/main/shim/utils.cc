/*
 * Copyright 2023 The Android Open Source Project
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

#define LOG_TAG "shim"

#include "utils.h"

#include "os/log.h"

namespace bluetooth {
namespace shim {
void parse_gap_data(const std::vector<uint8_t> &raw_data,
                    std::vector<hci::GapData> &output) {
    size_t offset = 0;
    while (offset < raw_data.size()) {
      hci::GapData gap_data;
      uint8_t len = raw_data[offset];

      if (offset + len + 1 > raw_data.size()) {
        LOG_WARN("GAP data out of bound");
        break;
      }

      auto begin = raw_data.begin() + offset;
      auto end = begin + len + 1;  // 1 byte for len
      auto data_copy = std::make_shared<std::vector<uint8_t>>(begin, end);
      bluetooth::packet::PacketView<bluetooth::packet::kLittleEndian> packet(
          data_copy);
      hci::GapData::Parse(&gap_data, packet.begin());
      output.push_back(gap_data);
      offset += len + 1;  // 1 byte for len
    }
}

}  // namespace shim
}  // namespace bluetooth
