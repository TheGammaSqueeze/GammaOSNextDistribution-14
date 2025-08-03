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

#include <type_traits>

#include "common/strings.h"
#include "hci/hci_packets.h"

// Define new enums or parsers for existing enums
namespace bluetooth {
namespace hci {

// Must be 0b00, 0b01, 0b10, and 0b11 as this is a bit mask
enum DeviceType { UNKNOWN = 0, BR_EDR = 1, LE = 2, DUAL = 3 };

// Scan mode from legacy stack, which is different from hci::ScanEnable
enum LegacyScanMode { BT_SCAN_MODE_NONE = 0, BT_SCAN_MODE_CONNECTABLE = 1, BT_SCAN_MODE_CONNECTABLE_DISCOVERABLE = 2 };

}  // namespace hci

// Must be defined in bluetooth namespace
template <typename T, typename std::enable_if<std::is_same_v<T, hci::DeviceType>, int>::type = 0>
std::optional<hci::DeviceType> FromLegacyConfigString(const std::string& str) {
  auto raw_value = common::Int64FromString(str);
  if (!raw_value) {
    return std::nullopt;
  }
  if (*raw_value < hci::DeviceType::UNKNOWN || *raw_value > hci::DeviceType::DUAL) {
    return std::nullopt;
  }
  return static_cast<hci::DeviceType>(*raw_value);
}

// Must be defined in bluetooth namespace
template <typename T, typename std::enable_if<std::is_same_v<T, hci::AddressType>, int>::type = 0>
std::optional<hci::AddressType> FromLegacyConfigString(const std::string& str) {
  auto raw_value = common::Int64FromString(str);
  if (!raw_value) {
    return std::nullopt;
  }
  if (*raw_value < static_cast<int64_t>(hci::AddressType::PUBLIC_DEVICE_ADDRESS) ||
      *raw_value > static_cast<int64_t>(hci::AddressType::RANDOM_IDENTITY_ADDRESS)) {
    return std::nullopt;
  }
  return static_cast<hci::AddressType>(*raw_value);
}

// Must be defined in bluetooth namespace
template <typename T, typename std::enable_if<std::is_same_v<T, hci::KeyType>, int>::type = 0>
std::optional<hci::KeyType> FromLegacyConfigString(const std::string& str) {
  auto raw_value = common::Int64FromString(str);
  if (!raw_value) {
    return std::nullopt;
  }
  if (*raw_value < static_cast<int64_t>(hci::KeyType::COMBINATION) ||
      *raw_value > static_cast<int64_t>(hci::KeyType::AUTHENTICATED_P256)) {
    return std::nullopt;
  }
  return static_cast<hci::KeyType>(*raw_value);
}

// Must be defined in bluetooth namespace
template <typename T, typename std::enable_if<std::is_same_v<T, hci::LegacyScanMode>, int>::type = 0>
std::optional<hci::LegacyScanMode> FromLegacyConfigString(const std::string& str) {
  auto raw_value = common::Int64FromString(str);
  if (!raw_value) {
    return std::nullopt;
  }
  if (*raw_value < static_cast<int64_t>(hci::LegacyScanMode::BT_SCAN_MODE_NONE) ||
      *raw_value > static_cast<int64_t>(hci::LegacyScanMode::BT_SCAN_MODE_CONNECTABLE_DISCOVERABLE)) {
    return std::nullopt;
  }
  return static_cast<hci::LegacyScanMode>(*raw_value);
}

}  // namespace bluetooth