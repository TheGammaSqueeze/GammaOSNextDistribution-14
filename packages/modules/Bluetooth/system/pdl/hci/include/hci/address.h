/******************************************************************************
 *
 *  Copyright 2019 The Android Open Source Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

#pragma once

#include <array>
#include <cstring>
#include <initializer_list>
#include <optional>
#include <ostream>
#include <string>

#include "common/interfaces/ILoggable.h"
#include "packet/custom_field_fixed_size_interface.h"
#include "storage/serializable.h"

namespace bluetooth {
namespace hci {

class Address final : public packet::CustomFieldFixedSizeInterface<Address>,
                      public storage::Serializable<Address>,
                      public bluetooth::common::IRedactableLoggable {
 public:
  static constexpr size_t kLength = 6;

  // Bluetooth MAC address bytes saved in little endian format.
  // The address MSB is address[5], the address LSB is address[0].
  // Note that the textual representation follows the big endian format,
  // ie. Address{0, 1, 2, 3, 4, 5} is represented as 05:04:03:02:01:00.
  std::array<uint8_t, kLength> address = {};

  Address() = default;
  Address(const uint8_t (&addr)[kLength]);
  Address(std::initializer_list<uint8_t> l);

  // CustomFieldFixedSizeInterface methods
  inline uint8_t* data() override { return address.data(); }
  inline const uint8_t* data() const override { return address.data(); }

  // storage::Serializable methods
  std::string ToString() const override;
  std::string ToColonSepHexString() const;
  std::string ToStringForLogging() const override;
  std::string ToRedactedStringForLogging() const override;

  static std::optional<Address> FromString(const std::string& from);
  std::string ToLegacyConfigString() const override;
  static std::optional<Address> FromLegacyConfigString(const std::string& str);

  bool operator<(const Address& rhs) const { return address < rhs.address; }
  bool operator==(const Address& rhs) const { return address == rhs.address; }
  bool operator>(const Address& rhs) const { return (rhs < *this); }
  bool operator<=(const Address& rhs) const { return !(*this > rhs); }
  bool operator>=(const Address& rhs) const { return !(*this < rhs); }
  bool operator!=(const Address& rhs) const { return !(*this == rhs); }

  bool IsEmpty() const { return *this == kEmpty; }

  // Converts |string| to Address and places it in |to|. If |from| does
  // not represent a Bluetooth address, |to| is not modified and this function
  // returns false. Otherwise, it returns true.
  static bool FromString(const std::string& from, Address& to);

  // Copies |from| raw Bluetooth address octets to the local object.
  // Returns the number of copied octets - should be always Address::kLength
  size_t FromOctets(const uint8_t* from);

  static bool IsValidAddress(const std::string& address);

  static const Address kEmpty;  // 00:00:00:00:00:00
  static const Address kAny;    // FF:FF:FF:FF:FF:FF
 private:
  std::string _ToMaskedColonSepHexString(int bytes_to_mask) const;
};

// TODO: to fine-tune this.
// we need an interface between the logger and ILoggable
inline std::ostream& operator<<(std::ostream& os, const Address& a) {
  os << a.ToString();
  return os;
}

}  // namespace hci
}  // namespace bluetooth

namespace std {
template <>
struct hash<bluetooth::hci::Address> {
  std::size_t operator()(const bluetooth::hci::Address& val) const {
    static_assert(sizeof(uint64_t) >= bluetooth::hci::Address::kLength);
    uint64_t int_addr = 0;
    memcpy(reinterpret_cast<uint8_t*>(&int_addr), val.data(),
           bluetooth::hci::Address::kLength);
    return std::hash<uint64_t>{}(int_addr);
  }
};
}  // namespace std
