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

#include <array>
#include <optional>
#include <string>
#include <unordered_set>

#include "hci/link_key.h"
#include "hci/uuid.h"
#include "storage/config_cache.h"
#include "storage/config_cache_helper.h"
#include "storage/device.h"

namespace bluetooth {
namespace storage {

class ClassicDevice {
 public:
  ClassicDevice(ConfigCache* config, ConfigCache* memory_only_config, std::string section);

  // for move
  ClassicDevice(ClassicDevice&& other) noexcept = default;
  ClassicDevice& operator=(ClassicDevice&& other) noexcept = default;

  // for copy
  ClassicDevice(const ClassicDevice& other) noexcept = default;
  ClassicDevice& operator=(const ClassicDevice& other) noexcept = default;

  // operators
  bool operator==(const ClassicDevice& other) const {
    return config_ == other.config_ && memory_only_config_ == other.memory_only_config_ && section_ == other.section_;
  }
  bool operator!=(const ClassicDevice& other) const {
    return !(*this == other);
  }
  bool operator<(const ClassicDevice& other) const {
    if (config_ != other.config_) {
      return config_ < other.config_;
    }
    if (memory_only_config_ != other.memory_only_config_) {
      return memory_only_config_ < other.memory_only_config_;
    }
    return section_ < other.section_;
  }
  bool operator>(const ClassicDevice& rhs) const {
    return (rhs < *this);
  }
  bool operator<=(const ClassicDevice& rhs) const {
    return !(*this > rhs);
  }
  bool operator>=(const ClassicDevice& rhs) const {
    return !(*this < rhs);
  }

  // Get the parent device
  Device Parent();

  // For logging purpose only, you can't get a ClassicDevice object from parsing a std::string
  std::string ToLogString() const;

  // Get address of this classic device, it must exist
  hci::Address GetAddress() const;

  // Return true if device has a link key in one of |kLinkKeyProperties|
  bool IsPaired() const;

  // Property names that correspond to a link key used in Bluetooth classic device
  static const std::unordered_set<std::string_view> kLinkKeyProperties;

 private:
  ConfigCache* config_;
  ConfigCache* memory_only_config_;
  std::string section_;
  friend std::hash<ClassicDevice>;

 public:
  GENERATE_PROPERTY_GETTER_SETTER_REMOVER(LinkKey, hci::LinkKey, "LinkKey");
  GENERATE_PROPERTY_GETTER_SETTER_REMOVER(LinkKeyType, hci::KeyType, "LinkKeyType");
  GENERATE_PROPERTY_GETTER_SETTER_REMOVER(ServiceUuids, std::vector<hci::Uuid>, "Service");
  GENERATE_PROPERTY_GETTER_SETTER_REMOVER(SdpDiManufacturer, uint16_t, "SdpDiManufacturer");
  GENERATE_PROPERTY_GETTER_SETTER_REMOVER(SdpDiModel, uint16_t, "SdpDiModel");
  GENERATE_PROPERTY_GETTER_SETTER_REMOVER(SdpDiHardwareVersion, uint16_t, "SdpDiHardwareVersion");
  GENERATE_PROPERTY_GETTER_SETTER_REMOVER(SdpDiVendorIdSource, uint16_t, "SdpDiVendorIdSource");
};

}  // namespace storage
}  // namespace bluetooth

namespace std {
template <>
struct hash<bluetooth::storage::ClassicDevice> {
  std::size_t operator()(const bluetooth::storage::ClassicDevice& val) const noexcept {
    std::size_t pointer_hash_1 = std::hash<bluetooth::storage::ConfigCache*>{}(val.config_);
    std::size_t pointer_hash_2 = std::hash<bluetooth::storage::ConfigCache*>{}(val.config_);
    std::size_t addr_hash = std::hash<std::string>{}(val.section_);
    return addr_hash ^ (pointer_hash_1 << 1) ^ (pointer_hash_2 << 2);
  }
};
}  // namespace std