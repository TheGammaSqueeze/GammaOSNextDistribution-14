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

#include "get_current_player_application_setting_value.h"

namespace bluetooth {
namespace avrcp {

std::unique_ptr<GetCurrentPlayerApplicationSettingValueResponseBuilder>
GetCurrentPlayerApplicationSettingValueResponseBuilder::MakeBuilder(
    std::vector<PlayerAttribute> attributes, std::vector<uint8_t> values) {
  std::unique_ptr<GetCurrentPlayerApplicationSettingValueResponseBuilder>
      builder(new GetCurrentPlayerApplicationSettingValueResponseBuilder(
          std::move(attributes), std::move(values)));

  return builder;
}

size_t GetCurrentPlayerApplicationSettingValueResponseBuilder::size() const {
  size_t len = VendorPacket::kMinSize();
  len += sizeof(uint8_t);                       // Number of attributes size
  len += attributes_.size() * sizeof(uint8_t);  // Attributes
  len += values_.size() * sizeof(uint8_t);      // Attributes' values
  return len;
}

bool GetCurrentPlayerApplicationSettingValueResponseBuilder::Serialize(
    const std::shared_ptr<::bluetooth::Packet>& pkt) {
  ReserveSpace(pkt, size());

  PacketBuilder::PushHeader(pkt);

  VendorPacketBuilder::PushHeader(pkt, size() - VendorPacket::kMinSize());

  AddPayloadOctets1(pkt, (uint8_t)attributes_.size());
  for (size_t i = 0; i < attributes_.size(); i++) {
    AddPayloadOctets1(pkt, (uint8_t)attributes_[i]);
    AddPayloadOctets1(pkt, (uint8_t)values_[i]);
  }

  return true;
}

uint8_t
GetCurrentPlayerApplicationSettingValueRequest::GetNumberOfRequestedAttributes()
    const {
  auto it = begin() + VendorPacket::kMinSize();
  return *it;
}

std::vector<PlayerAttribute>
GetCurrentPlayerApplicationSettingValueRequest::GetRequestedAttributes() const {
  auto it = begin() + VendorPacket::kMinSize();
  uint8_t number_of_attributes = static_cast<uint8_t>(it.extract8());
  std::vector<PlayerAttribute> attribute_list;
  for (size_t i = 0; i < number_of_attributes; i++) {
    attribute_list.push_back((PlayerAttribute)it.extract8());
  }
  return attribute_list;
}

bool GetCurrentPlayerApplicationSettingValueRequest::IsValid() const {
  if (!VendorPacket::IsValid()) return false;
  if (size() < kMinSize()) return false;

  size_t num_attributes = GetNumberOfRequestedAttributes();
  auto attr_start = begin() + VendorPacket::kMinSize() + static_cast<size_t>(1);

  return (num_attributes * sizeof(uint8_t)) == (size_t)(end() - attr_start);
}

std::string GetCurrentPlayerApplicationSettingValueRequest::ToString() const {
  std::stringstream ss;
  ss << "GetCurrentPlayerApplicationSettingValueRequest: " << std::endl;
  ss << "  └ cType = " << GetCType() << std::endl;
  ss << "  └ Subunit Type = " << loghex(GetSubunitType()) << std::endl;
  ss << "  └ Subunit ID = " << loghex(GetSubunitId()) << std::endl;
  ss << "  └ OpCode = " << GetOpcode() << std::endl;
  ss << "  └ Company ID = " << loghex(GetCompanyId()) << std::endl;
  ss << "  └ Command PDU = " << GetCommandPdu() << std::endl;
  ss << "  └ PacketType = " << GetPacketType() << std::endl;
  ss << "  └ Parameter Length = " << loghex(GetParameterLength()) << std::endl;
  ss << "  └ Num Attributes = " << loghex(GetNumberOfRequestedAttributes())
     << std::endl;

  auto attr_list = GetRequestedAttributes();
  ss << "  └ Player Attribute List: Size: " << attr_list.size() << std::endl;
  for (auto it = attr_list.begin(); it != attr_list.end(); it++) {
    ss << "      └ " << static_cast<PlayerAttribute>(*it) << std::endl;
  }
  ss << std::endl;

  return ss.str();
}

}  // namespace avrcp
}  // namespace bluetooth
