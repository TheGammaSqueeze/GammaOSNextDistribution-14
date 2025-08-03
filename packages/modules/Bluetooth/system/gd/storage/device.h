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

#include <functional>
#include <limits>
#include <optional>
#include <string>
#include <type_traits>
#include <unordered_set>
#include <utility>

#include "hci/address.h"
#include "hci/address_with_type.h"
#include "hci/class_of_device.h"
#include "hci/enum_helper.h"
#include "storage/config_cache.h"
#include "storage/config_cache_helper.h"
#include "storage/config_keys.h"
#include "storage/mutation_entry.h"
#include "storage/serializable.h"

namespace bluetooth {
namespace storage {

class LeDevice;
class ClassicDevice;

// Make sure our macro is used
#ifdef GENERATE_PROPERTY_GETTER_SETTER_REMOVER
static_assert(false, "GENERATE_PROPERTY_GETTER_SETTER_REMOVER() must be uniquely defined once in this file");
#endif

#define GENERATE_PROPERTY_GETTER_SETTER_REMOVER(NAME, RETURN_TYPE, PROPERTY_KEY)                                \
 public:                                                                                                        \
  std::optional<RETURN_TYPE> Get##NAME() const {                                                                \
    return ConfigCacheHelper(*config_).Get<RETURN_TYPE>(section_, PROPERTY_KEY);                                \
  }                                                                                                             \
  MutationEntry Set##NAME(const RETURN_TYPE& value) {                                                           \
    return MutationEntry::Set<RETURN_TYPE>(MutationEntry::PropertyType::NORMAL, section_, PROPERTY_KEY, value); \
  }                                                                                                             \
  MutationEntry Remove##NAME() {                                                                                \
    return MutationEntry::Remove(MutationEntry::PropertyType::NORMAL, section_, PROPERTY_KEY);                  \
  }

// Make sure our macro is used
#ifdef GENERATE_PROPERTY_GETTER_SETTER_REMOVER_WITH_CUSTOM_SETTER
static_assert(
    false, "GENERATE_PROPERTY_GETTER_SETTER_REMOVER_WITH_CUSTOM_SETTER() must be uniquely defined once in this file");
#endif

// FUNC is bracketed function definition that takes a const RETURN_TYPE& value and return RETURN_TYPE
// e.g. { return value + 1; }
#define GENERATE_PROPERTY_GETTER_SETTER_REMOVER_WITH_CUSTOM_SETTER(NAME, RETURN_TYPE, PROPERTY_KEY, FUNC)           \
 public:                                                                                                            \
  std::optional<RETURN_TYPE> Get##NAME() const {                                                                    \
    return ConfigCacheHelper(*config_).Get<RETURN_TYPE>(section_, PROPERTY_KEY);                                    \
  }                                                                                                                 \
  MutationEntry Set##NAME(const RETURN_TYPE& value) {                                                               \
    auto new_value = [this](const RETURN_TYPE& value) -> RETURN_TYPE FUNC(value);                                   \
    return MutationEntry::Set<RETURN_TYPE>(MutationEntry::PropertyType::NORMAL, section_, PROPERTY_KEY, new_value); \
  }                                                                                                                 \
  MutationEntry Remove##NAME() {                                                                                    \
    return MutationEntry::Remove(MutationEntry::PropertyType::NORMAL, section_, PROPERTY_KEY);                      \
  }

// Make sure our macro is used
#ifdef GENERATE_TEMP_PROPERTY_GETTER_SETTER_REMOVER
static_assert(false, "GENERATE_TEMP_PROPERTY_GETTER_SETTER_REMOVER() must be uniquely defined once in this file");
#endif

// Macro to generate tempoarary property that exists in memory only
// It is subjected to a limit of 10,000 devices
// It will be cleared when the stack is restarted
#define GENERATE_TEMP_PROPERTY_GETTER_SETTER_REMOVER(NAME, RETURN_TYPE, PROPERTY_KEY)                                \
 public:                                                                                                             \
  std::optional<RETURN_TYPE> GetTemp##NAME() const {                                                                 \
    return ConfigCacheHelper(*memory_only_config_).Get<RETURN_TYPE>(section_, PROPERTY_KEY);                         \
  }                                                                                                                  \
  MutationEntry SetTemp##NAME(const RETURN_TYPE& value) {                                                            \
    return MutationEntry::Set<RETURN_TYPE>(MutationEntry::PropertyType::MEMORY_ONLY, section_, PROPERTY_KEY, value); \
  }                                                                                                                  \
  MutationEntry RemoveTemp##NAME() {                                                                                 \
    return MutationEntry::Remove(MutationEntry::PropertyType::MEMORY_ONLY, section_, PROPERTY_KEY);                  \
  }

// A think wrapper of device in ConfigCache, allowing easy access to various predefined properties of a Bluetooth device
//
// Device, LeDevice, and Classic device objects are fully copyable, comparable hashable
//
// A newly created device does not have any DeviceType information and user can only read or write the values in this
// common Device abstraction layer.
//
// As soon as a user determines the type of device, they should call SetDeviceType() to assign device to a type
// After that, Classic() or Le() will return interfaces that allows access to deeper layer properties
class Device {
 public:
  enum ConfigKeyAddressType { LEGACY_KEY_ADDRESS, CLASSIC_ADDRESS, LE_IDENTITY_ADDRESS, LE_LEGACY_PSEUDO_ADDRESS };

  Device(
      ConfigCache* config,
      ConfigCache* memory_only_config,
      const hci::Address& key_address,
      ConfigKeyAddressType key_address_type);
  Device(ConfigCache* config, ConfigCache* memory_only_config, std::string section);

  // for move
  Device(Device&& other) noexcept = default;
  Device& operator=(Device&& other) noexcept = default;

  // for copy
  Device(const Device& other) noexcept = default;
  Device& operator=(const Device& other) noexcept = default;

  // operators
  bool operator==(const Device& other) const {
    return config_ == other.config_ && memory_only_config_ == other.memory_only_config_ && section_ == other.section_;
  }
  bool operator!=(const Device& other) const {
    return !(*this == other);
  }
  bool operator<(const Device& other) const {
    if (config_ != other.config_) {
      return config_ < other.config_;
    }
    if (memory_only_config_ != other.memory_only_config_) {
      return memory_only_config_ < other.memory_only_config_;
    }
    return section_ < other.section_;
  }
  bool operator>(const Device& rhs) const {
    return (rhs < *this);
  }
  bool operator<=(const Device& rhs) const {
    return !(*this > rhs);
  }
  bool operator>=(const Device& rhs) const {
    return !(*this < rhs);
  }

  // A newly created Device object may not be backed by any properties in the ConfigCache, where Exists() will return
  // false. As soon as a property value is added to the device. Exists() will become true.
  bool Exists();

  // Remove device and all its properties from config and memory-only temp config
  MutationEntry RemoveFromConfig();
  // Remove device and all its properties from memory-only temp config, but keep items in normal config
  MutationEntry RemoveFromTempConfig();

  // Only works when GetDeviceType() returns BR_EDR or DUAL, will crash otherwise
  // For first time use, please SetDeviceType() to the right value
  ClassicDevice Classic();

  // Only works when GetDeviceType() returns LE or DUAL, will crash otherwise
  // For first time use, please SetDeviceType() to the right value
  LeDevice Le();

  // For logging purpose only, you can't get a Device object from parsing a std::string
  std::string ToLogString() const;

  hci::Address GetAddress() const;

  // Property names that correspond to a link key used in Bluetooth Classic and LE device
  static const std::unordered_set<std::string_view> kLinkKeyProperties;

 private:
  ConfigCache* config_;
  ConfigCache* memory_only_config_;
  std::string section_;
  friend std::hash<Device>;

 public:
  // Macro generate getters, setters and removers
  GENERATE_PROPERTY_GETTER_SETTER_REMOVER(Name, std::string, BTIF_STORAGE_KEY_NAME);
  GENERATE_PROPERTY_GETTER_SETTER_REMOVER(
      ClassOfDevice, hci::ClassOfDevice, BTIF_STORAGE_KEY_DEV_CLASS);
  GENERATE_PROPERTY_GETTER_SETTER_REMOVER_WITH_CUSTOM_SETTER(
      DeviceType, hci::DeviceType, BTIF_STORAGE_KEY_DEV_TYPE, {
        return static_cast<hci::DeviceType>(
            value | GetDeviceType().value_or(hci::DeviceType::UNKNOWN));
      });
  GENERATE_PROPERTY_GETTER_SETTER_REMOVER(ManufacturerCode, uint16_t, "Manufacturer");
  GENERATE_PROPERTY_GETTER_SETTER_REMOVER(LmpVersion, uint8_t, "LmpVer");
  GENERATE_PROPERTY_GETTER_SETTER_REMOVER(LmpSubVersion, uint16_t, "LmpSubVer");
  GENERATE_PROPERTY_GETTER_SETTER_REMOVER(SdpDiManufacturer, uint16_t, "SdpDiManufacturer");
  GENERATE_PROPERTY_GETTER_SETTER_REMOVER(SdpDiModel, uint16_t, "SdpDiModel");
  GENERATE_PROPERTY_GETTER_SETTER_REMOVER(SdpDiHardwareVersion, uint16_t, "SdpDiHardwareVersion");
  GENERATE_PROPERTY_GETTER_SETTER_REMOVER(SdpDiVendorIdSource, uint16_t, "SdpDiVendorIdSource");

  GENERATE_PROPERTY_GETTER_SETTER_REMOVER(MetricsId, int, "MetricsId");
  GENERATE_PROPERTY_GETTER_SETTER_REMOVER(PinLength, int, "PinLength");
  // unix timestamp in seconds from epoch
  GENERATE_PROPERTY_GETTER_SETTER_REMOVER(CreationUnixTimestamp, int, BTIF_STORAGE_KEY_DEV_CLASS);
  GENERATE_PROPERTY_GETTER_SETTER_REMOVER(IsAuthenticated, int, "IsAuthenticated");
  GENERATE_PROPERTY_GETTER_SETTER_REMOVER(RequiresMitmProtection, int, "RequiresMitmProtection");
  GENERATE_PROPERTY_GETTER_SETTER_REMOVER(IsEncryptionRequired, int, "IsEncryptionRequired");
};

}  // namespace storage
}  // namespace bluetooth

namespace std {
template <>
struct hash<bluetooth::storage::Device> {
  std::size_t operator()(const bluetooth::storage::Device& val) const noexcept {
    std::size_t pointer_hash_1 = std::hash<bluetooth::storage::ConfigCache*>{}(val.config_);
    std::size_t pointer_hash_2 = std::hash<bluetooth::storage::ConfigCache*>{}(val.config_);
    std::size_t addr_hash = std::hash<std::string>{}(val.section_);
    return addr_hash ^ (pointer_hash_1 << 1) ^ (pointer_hash_2 << 2);
  }
};
}  // namespace std