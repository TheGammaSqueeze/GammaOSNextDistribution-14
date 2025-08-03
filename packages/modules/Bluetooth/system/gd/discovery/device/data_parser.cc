/*
 * Copyright 2024 The Android Open Source Project
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

#include "discovery/device/data_parser.h"

#include "hci/hci_packets.h"
#include "packet/iterator.h"

using namespace bluetooth;

using namespace bluetooth::hci;
using namespace bluetooth::packet;

namespace bluetooth::discovery::device {

DataParser::DataParser(const std::vector<uint8_t>& data) {
  auto it = Iterator<kLittleEndian>(std::make_shared<std::vector<uint8_t>>(data));

  while (it.NumBytesRemaining()) {
    GapData gap_data;
    it = GapData::Parse(&gap_data, it);
    gap_data_.push_back(gap_data);
  }
}

size_t DataParser::GetNumGapData() const {
  return gap_data_.size();
}

std::vector<hci::GapData> DataParser::GetData() const {
  return std::vector<hci::GapData>(gap_data_);
}

std::vector<hci::GapDataType> DataParser::GetDataTypes() const {
  std::vector<hci::GapDataType> types;
  for (const auto& gap_data : gap_data_) types.push_back(gap_data.data_type_);
  return types;
}

}  // namespace bluetooth::discovery::device
