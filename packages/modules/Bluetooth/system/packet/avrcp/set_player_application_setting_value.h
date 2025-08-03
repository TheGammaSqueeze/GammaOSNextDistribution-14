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

#pragma once

#include <vector>

#include "vendor_packet.h"

namespace bluetooth {
namespace avrcp {

class SetPlayerApplicationSettingValueResponseBuilder
    : public VendorPacketBuilder {
 public:
  virtual ~SetPlayerApplicationSettingValueResponseBuilder() = default;

  static std::unique_ptr<SetPlayerApplicationSettingValueResponseBuilder>
  MakeBuilder();

  virtual size_t size() const override;
  virtual bool Serialize(
      const std::shared_ptr<::bluetooth::Packet>& pkt) override;

 protected:
  SetPlayerApplicationSettingValueResponseBuilder()
      : VendorPacketBuilder(CType::ACCEPTED,
                            CommandPdu::SET_PLAYER_APPLICATION_SETTING_VALUE,
                            PacketType::SINGLE){};
};

class SetPlayerApplicationSettingValueRequest : public VendorPacket {
 public:
  virtual ~SetPlayerApplicationSettingValueRequest() = default;

  /**
   *  Set Player Application Setting Value
   *   AvrcpPacket:
   *     CType c_type_;
   *     uint8_t subunit_type_ : 5;
   *     uint8_t subunit_id_ : 3;
   *     Opcode opcode_;
   *   VendorPacket:
   *     uint8_t company_id[3];
   *     uint8_t command_pdu;
   *     uint8_t packet_type;
   *     uint16_t param_length;
   *   SetPlayerApplicationSettingValueRequest:
   *     uint8_t number_of_attributes;
   *     std::vector<PlayerAttribute> attributes;
   *     std::vector<uint8_t> values;
   */
  static constexpr size_t kMinSize() {
    return VendorPacket::kMinSize() + sizeof(uint8_t);
  }

  uint8_t GetNumberOfRequestedAttributes() const;
  std::vector<PlayerAttribute> GetRequestedAttributes() const;
  std::vector<uint8_t> GetRequestedValues() const;

  virtual bool IsValid() const override;
  virtual std::string ToString() const override;

 protected:
  using VendorPacket::VendorPacket;
};

}  // namespace avrcp
}  // namespace bluetooth