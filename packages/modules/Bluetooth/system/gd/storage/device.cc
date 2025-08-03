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

#include "storage/device.h"

#include <algorithm>
#include <limits>

#include "os/log.h"
#include "storage/classic_device.h"
#include "storage/config_cache_helper.h"
#include "storage/le_device.h"

namespace bluetooth {
namespace storage {

using hci::DeviceType;

namespace {
// TODO(siyuanh): also defined in storage/le_device.cc
const std::string kLeIdentityAddressKey = "LeIdentityAddr";
const std::string kLeLegacyPseudoAddr = "LeLegacyPseudoAddr";

std::string GetConfigSection(
    ConfigCache* config, const hci::Address& key_address, Device::ConfigKeyAddressType key_address_type) {
  ASSERT_LOG(config != nullptr, "config cannot be null");
  ASSERT_LOG(!key_address.IsEmpty(), "key_address cannot be empty");
  // assume lower case
  auto key_address_string = key_address.ToString();
  switch (key_address_type) {
    case Device::ConfigKeyAddressType::LEGACY_KEY_ADDRESS:
    case Device::ConfigKeyAddressType::CLASSIC_ADDRESS:
      return key_address_string;
    case Device::ConfigKeyAddressType::LE_IDENTITY_ADDRESS:
      for (const auto& section_and_property : config->GetSectionNamesWithProperty(kLeIdentityAddressKey)) {
        if (section_and_property.property == key_address_string) {
          return section_and_property.section;
        }
      }
      return key_address_string;
    case Device::ConfigKeyAddressType::LE_LEGACY_PSEUDO_ADDRESS:
      for (const auto& section_and_property : config->GetSectionNamesWithProperty(kLeLegacyPseudoAddr)) {
        if (section_and_property.property == key_address_string) {
          return section_and_property.section;
        }
      }
      // One cannot create a new device just using LE legacy pseudo address
      [[fallthrough]];
    default:
      LOG_ALWAYS_FATAL("Unknown key_address_type %d", static_cast<int>(key_address_type));
      return "";
  }
}

}  // namespace

const std::unordered_set<std::string_view> Device::kLinkKeyProperties = {
    "LinkKey", "LE_KEY_PENC", "LE_KEY_PID", "LE_KEY_PCSRK", "LE_KEY_LENC", "LE_KEY_LCSRK"};

Device::Device(
    ConfigCache* config,
    ConfigCache* memory_only_config,
    const hci::Address& key_address,
    ConfigKeyAddressType key_address_type)
    : Device(config, memory_only_config, GetConfigSection(config, key_address, key_address_type)) {}

Device::Device(ConfigCache* config, ConfigCache* memory_only_config, std::string section)
    : config_(config), memory_only_config_(memory_only_config), section_(std::move(section)) {}

bool Device::Exists() {
  return config_->HasSection(section_);
}

MutationEntry Device::RemoveFromConfig() {
  return MutationEntry::Remove(MutationEntry::PropertyType::NORMAL, section_);
}

MutationEntry Device::RemoveFromTempConfig() {
  return MutationEntry::Remove(MutationEntry::PropertyType::MEMORY_ONLY, section_);
}

LeDevice Device::Le() {
  auto device_type = GetDeviceType();
  ASSERT(device_type);
  ASSERT(device_type == DeviceType::LE || device_type == DeviceType::DUAL);
  return LeDevice(config_, memory_only_config_, section_);
}

ClassicDevice Device::Classic() {
  auto device_type = GetDeviceType();
  ASSERT(device_type);
  ASSERT(device_type == DeviceType::BR_EDR || device_type == DeviceType::DUAL);
  return ClassicDevice(config_, memory_only_config_, section_);
}

hci::Address Device::GetAddress() const {
  // section name of a device is its address
  auto addr = hci::Address::FromString(section_);
  ASSERT(addr.has_value());
  return addr.value();
}

std::string Device::ToLogString() const {
  return section_;
}

}  // namespace storage
}  // namespace bluetooth
