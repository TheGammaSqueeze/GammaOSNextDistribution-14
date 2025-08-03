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

#include "set_player_application_setting_value.h"

namespace bluetooth {
namespace avrcp {

std::unique_ptr<SetPlayerApplicationSettingValueResponseBuilder>
SetPlayerApplicationSettingValueResponseBuilder::MakeBuilder() {
  std::unique_ptr<SetPlayerApplicationSettingValueResponseBuilder> builder(
      new SetPlayerApplicationSettingValueResponseBuilder());

  return builder;
}

size_t SetPlayerApplicationSettingValueResponseBuilder::size() const {
  return VendorPacket::kMinSize();
}

bool SetPlayerApplicationSettingValueResponseBuilder::Serialize(
    const std::shared_ptr<::bluetooth::Packet>& pkt) {
  ReserveSpace(pkt, size());

  PacketBuilder::PushHeader(pkt);

  VendorPacketBuilder::PushHeader(pkt, 0);

  return true;
}

uint8_t
SetPlayerApplicationSettingValueRequest::GetNumberOfRequestedAttributes()
    const {
  auto it = begin() + VendorPacket::kMinSize();
  return *it;
}

std::vector<PlayerAttribute>
SetPlayerApplicationSettingValueRequest::GetRequestedAttributes() const {
  auto it = begin() + VendorPacket::kMinSize() +
            static_cast<size_t>(1);  // Point to the first attribute
  std::vector<PlayerAttribute> attribute_list;

  for (; it < end(); it++) {
    attribute_list.push_back(static_cast<PlayerAttribute>(*it));
    it++;  // Skip value
  }

  return attribute_list;
}

std::vector<uint8_t>
SetPlayerApplicationSettingValueRequest::GetRequestedValues() const {
  auto it = begin() + VendorPacket::kMinSize() +
            static_cast<size_t>(1);  // Point to the first attribute
  std::vector<uint8_t> values_list;

  for (; it < end(); it++) {
    it++;  // Skip attribute
    values_list.push_back(static_cast<uint8_t>(*it));
  }

  return values_list;
}

bool SetPlayerApplicationSettingValueRequest::IsValid() const {
  if (!VendorPacket::IsValid()) return false;
  if (size() < kMinSize()) return false;

  size_t num_of_attrs = GetNumberOfRequestedAttributes();
  auto attr_start = begin() + VendorPacket::kMinSize() + static_cast<size_t>(1);

  return (num_of_attrs * 2 * sizeof(uint8_t)) == (size_t)(end() - attr_start);
}

std::string SetPlayerApplicationSettingValueRequest::ToString() const {
  std::stringstream ss;
  ss << "SetPlayerApplicationSettingValueRequest: " << std::endl;
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

  auto attribute_list_ = GetRequestedAttributes();
  auto values_list_ = GetRequestedValues();
  ss << "  └ Player Attributes and Values List: Size: "
     << attribute_list_.size() << std::endl;
  for (size_t i = 0; i < attribute_list_.size(); i++) {
    ss << "      └ " << static_cast<PlayerAttribute>(attribute_list_.at(i))
       << ": " << std::to_string(values_list_.at(i)) << std::endl;
  }
  ss << std::endl;

  return ss.str();
}

}  // namespace avrcp
}  // namespace bluetooth
