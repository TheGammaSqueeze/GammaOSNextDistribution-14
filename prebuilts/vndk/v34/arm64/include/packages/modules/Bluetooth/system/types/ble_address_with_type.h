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

#include <cstdint>
#include <string>
#include "types/raw_address.h"

#define BLE_ADDR_PUBLIC 0x00
#define BLE_ADDR_RANDOM 0x01
#define BLE_ADDR_PUBLIC_ID 0x02
#define BLE_ADDR_RANDOM_ID 0x03
#define BLE_ADDR_ANONYMOUS 0xFF
typedef uint8_t tBLE_ADDR_TYPE;
#ifdef __cplusplus
inline std::string AddressTypeText(tBLE_ADDR_TYPE type) {
  switch (type) {
    case BLE_ADDR_PUBLIC:
      return std::string("public");
    case BLE_ADDR_RANDOM:
      return std::string("random");
    case BLE_ADDR_PUBLIC_ID:
      return std::string("public identity");
    case BLE_ADDR_RANDOM_ID:
      return std::string("random identity");
    case BLE_ADDR_ANONYMOUS:
      return std::string("anonymous");
    default:
      return std::string("unknown");
  }
}
#endif  // __cplusplus

inline bool is_ble_addr_type_valid(uint8_t raw_type) { return raw_type < 4; }

inline bool is_ble_addr_type_known(tBLE_ADDR_TYPE type) {
  switch (type) {
    case BLE_ADDR_PUBLIC:
    case BLE_ADDR_PUBLIC_ID:
    case BLE_ADDR_RANDOM:
    case BLE_ADDR_RANDOM_ID:
      return true;
    default:
      return false;
  }
}

inline tBLE_ADDR_TYPE to_ble_addr_type(uint8_t raw_type) {
  return (tBLE_ADDR_TYPE)raw_type;
}
inline uint8_t from_ble_addr_type(tBLE_ADDR_TYPE type) { return (uint8_t)type; }

/* BLE ADDR type ID bit */
#define BLE_ADDR_TYPE_ID_BIT 0x02
inline bool is_identity_type(const tBLE_ADDR_TYPE& type) {
  return type & BLE_ADDR_TYPE_ID_BIT;
}

#define STREAM_TO_BLE_ADDR_TYPE(type, p) \
  {                                      \
    (type) = (tBLE_ADDR_TYPE)(*(p));     \
    (p) += sizeof(tBLE_ADDR_TYPE);       \
  }
#define BLE_ADDR_TYPE_TO_STREAM(p, type) \
  { *(p)++ = (tBLE_ADDR_TYPE)(type); }

#ifdef __cplusplus
constexpr uint8_t kBleAddressPublicDevice = BLE_ADDR_PUBLIC;
constexpr uint8_t kBleAddressRandomDevice = BLE_ADDR_RANDOM;
constexpr uint8_t kBleAddressIdentityBit = BLE_ADDR_TYPE_ID_BIT;
constexpr uint8_t kBleAddressPublicIdentity =
    kBleAddressIdentityBit | kBleAddressPublicDevice;
constexpr uint8_t kBleAddressRandomIdentity =
    kBleAddressIdentityBit | kBleAddressRandomDevice;

constexpr uint8_t kResolvableAddressMask = 0xc0;
constexpr uint8_t kResolvableAddressMsb = 0x40;

struct tBLE_BD_ADDR {
  tBLE_ADDR_TYPE type;
  RawAddress bda;
  bool AddressEquals(const RawAddress& other) const { return other == bda; }
  bool IsPublicDeviceType() const { return type == kBleAddressPublicDevice; }
  bool IsRandomDeviceType() const { return type == kBleAddressRandomDevice; }
  bool IsPublicIdentityType() const {
    return type == kBleAddressPublicIdentity;
  }
  bool lsRandomIdentityType() const {
    return type == kBleAddressRandomIdentity;
  }
  bool IsAddressResolvable() const {
    return ((bda.address)[0] & kResolvableAddressMask) == kResolvableAddressMsb;
  }
  bool IsPublic() const { return !(type & 0x01); }
  bool IsResolvablePrivateAddress() const {
    return IsAddressResolvable() && IsRandomDeviceType();
  }
  bool IsIdentityType() const {
    return IsPublicIdentityType() || lsRandomIdentityType();
  }
  bool TypeWithoutIdentityEquals(const tBLE_ADDR_TYPE other) const {
    return (other & ~kBleAddressIdentityBit) ==
           (type & ~kBleAddressIdentityBit);
  }

  std::string ToString() const {
    return std::string(bda.ToString() + "[" + AddressTypeText(type) + "]");
  }

  std::string ToStringForLogging() const {
    return bda.ToStringForLogging() + "[" + AddressTypeText(type) + "]";
  }

  std::string ToRedactedStringForLogging() const {
    return bda.ToRedactedStringForLogging() + "[" + AddressTypeText(type) + "]";
  }

  bool operator==(const tBLE_BD_ADDR rhs) const {
    return rhs.type == type && rhs.bda == bda;
  }
  bool operator!=(const tBLE_BD_ADDR rhs) const { return !(*this == rhs); }
};

template <>
struct std::hash<tBLE_BD_ADDR> {
  std::size_t operator()(const tBLE_BD_ADDR& val) const {
    static_assert(sizeof(uint64_t) >=
                  (RawAddress::kLength + sizeof(tBLE_ADDR_TYPE)));
    uint64_t int_addr = 0;
    memcpy(reinterpret_cast<uint8_t*>(&int_addr), val.bda.address,
           RawAddress::kLength);
    memcpy(reinterpret_cast<uint8_t*>(&int_addr) + RawAddress::kLength,
           (const void*)&val.type, sizeof(tBLE_ADDR_TYPE));
    return std::hash<uint64_t>{}(int_addr);
  }
};

#endif
