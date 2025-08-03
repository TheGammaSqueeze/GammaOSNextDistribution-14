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

#include "list_player_application_setting_values.h"

namespace bluetooth {
namespace avrcp {

std::unique_ptr<ListPlayerApplicationSettingValuesResponseBuilder>
ListPlayerApplicationSettingValuesResponseBuilder::MakeBuilder(
    std::vector<uint8_t> values) {
  std::unique_ptr<ListPlayerApplicationSettingValuesResponseBuilder> builder(
      new ListPlayerApplicationSettingValuesResponseBuilder(std::move(values)));

  return builder;
}

size_t ListPlayerApplicationSettingValuesResponseBuilder::size() const {
  size_t len = VendorPacket::kMinSize();
  len += sizeof(uint8_t);                   // Number of values
  len += values_.size() * sizeof(uint8_t);  // Values size
  return len;
}

bool ListPlayerApplicationSettingValuesResponseBuilder::Serialize(
    const std::shared_ptr<::bluetooth::Packet>& pkt) {
  ReserveSpace(pkt, size());

  PacketBuilder::PushHeader(pkt);

  VendorPacketBuilder::PushHeader(pkt, size() - VendorPacket::kMinSize());

  AddPayloadOctets1(pkt, static_cast<uint8_t>(values_.size()));
  for (auto value : values_) {
    AddPayloadOctets1(pkt, static_cast<uint8_t>(value));
  }

  return true;
}

PlayerAttribute
ListPlayerApplicationSettingValuesRequest::GetRequestedAttribute() const {
  auto it = begin() + VendorPacket::kMinSize();
  return static_cast<PlayerAttribute>(it.extract8());
}

bool ListPlayerApplicationSettingValuesRequest::IsValid() const {
  if (!VendorPacket::IsValid()) return false;
  return size() == kMinSize();
}

std::string ListPlayerApplicationSettingValuesRequest::ToString() const {
  std::stringstream ss;
  ss << "ListPlayerApplicationSettingValuesRequest: " << std::endl;
  ss << "  └ cType = " << GetCType() << std::endl;
  ss << "  └ Subunit Type = " << loghex(GetSubunitType()) << std::endl;
  ss << "  └ Subunit ID = " << loghex(GetSubunitId()) << std::endl;
  ss << "  └ OpCode = " << GetOpcode() << std::endl;
  ss << "  └ Company ID = " << loghex(GetCompanyId()) << std::endl;
  ss << "  └ Command PDU = " << GetCommandPdu() << std::endl;
  ss << "  └ PacketType = " << GetPacketType() << std::endl;
  ss << "  └ Parameter Length = " << loghex(GetParameterLength()) << std::endl;
  ss << "  └ Player Setting Attribute = " << GetRequestedAttribute()
     << std::endl;
  ss << std::endl;

  return ss.str();
}

}  // namespace avrcp
}  // namespace bluetooth
